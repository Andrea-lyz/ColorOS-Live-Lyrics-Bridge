package io.github.andrealtb.lockscreenlyrics;

import android.media.MediaMetadata;
import android.text.TextUtils;

import java.util.ArrayDeque;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.ParametersMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

final class QqMusicAdapter extends FirstBatchMediaSessionAdapter {
    private static final String PACKAGE_NAME = "com.tencent.qqmusic";
    private static final String SOURCE_NAME = "QQ Music internal lyric";
    private static final String LEGACY_SOURCE_NAME = "QQ Music QRC";
    private static final String SONG_INFO_CLASS =
            "com.tencent.qqmusicplayerprocess.songinfo.SongInfo";
    private static final String LYRIC_ENGINE_CLASS = "com.lyricengine.base.k";
    private static final String MEDIA_SESSION_UPDATE_CLASS =
            "com.tencent.qqmusicplayerprocess.servicenew.mediasession.s";
    private static final String QRC_DECRYPT_CLASS =
            "com.tencent.qqmusic.business.lyricnew.QRCDesDecrypt";
    private static final String QRC_DECRYPT_METHOD = "doDecryptionLyric";
    private static final String BUILDER_CLASS_SUFFIX = "MediaMetadataCompat$Builder";
    private static final long RECENT_MEDIA_SESSION_METADATA_MS = 30_000L;
    private static final long RECENT_DECRYPTED_LYRIC_MS = 45_000L;
    private static final long INTERNAL_TRANSLATION_MATCH_MS = 1_500L;
    private static final long INTERNAL_WORD_TIMING_MATCH_MS = 300L;
    private static final long CANDIDATE_BEFORE_CONTEXT_GRACE_MS = 1_500L;
    private static final int MAX_PROBE_LOGS_PER_PROCESS = 16;
    private static final int MAX_DECRYPTED_CANDIDATES = 12;
    private static final Object DEXKIT_LOAD_LOCK = new Object();

    private static volatile boolean dexKitLoaded;

    private final AtomicReference<String> currentInternalRequestId = new AtomicReference<>("");
    private final AtomicReference<String> currentTranslatedBaseRequestId =
            new AtomicReference<>("");
    private final AtomicReference<String> currentProbeSignature = new AtomicReference<>("");
    private final AtomicReference<String> currentDecryptedLyricSignature =
            new AtomicReference<>("");
    private final AtomicReference<InternalLyricContext> currentInternalContext =
            new AtomicReference<>();
    private final AtomicInteger probeLogCount = new AtomicInteger();
    private final Object decryptedLyricsLock = new Object();
    private final ArrayDeque<DecryptedLyricCandidate> decryptedLyricCandidates =
            new ArrayDeque<>();
    private final AtomicReference<ObservedTrackMetadata> observedTrackMetadata =
            new AtomicReference<>();
    private volatile boolean internalLyricHookInstalled;

    QqMusicAdapter() {
        super(PACKAGE_NAME, "QQ Music", LEGACY_SOURCE_NAME);
    }

    @Override
    public void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader) {
        super.installLyricSourceHooks(module, classLoader);
        try {
            ensureDexKitLoaded();
        } catch (Throwable t) {
            module.error("Failed to load DexKit for QQ Music internal lyric hook", t);
            return;
        }
        try (DexKitBridge bridge = DexKitBridge.create(classLoader, true)) {
            Method mediaSessionUpdate = resolveMediaSessionUpdateMethod(module, bridge, classLoader);
            mediaSessionUpdate.setAccessible(true);
            module.hook(mediaSessionUpdate)
                    .setId("qq-music-media-session-update-lyric")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> onMediaSessionUpdate(module, chain));
            internalLyricHookInstalled = true;
            module.info("Hooked QQ Music MediaSessionUpdateController lyric object via DexKit: "
                    + mediaSessionUpdate);
        } catch (Throwable t) {
            module.error("Failed to hook QQ Music internal lyric object via DexKit", t);
        }
        installQrcDecryptHook(module, classLoader);
    }

    @Override
    protected boolean shouldLookupLyrics(TrackMetadata track) {
        return !internalLyricHookInstalled && !TextUtils.isEmpty(track.mediaId);
    }

    @Override
    protected TimedLyricDocument loadLyrics(TrackMetadata track) throws Exception {
        return QqMusicLyricClient.download(track.mediaId);
    }

    @Override
    protected void onObservedTrackMetadata(
            LockscreenLyricsModule module,
            TrackMetadata track,
            MediaMetadata metadata) {
        long now = System.currentTimeMillis();
        ObservedTrackMetadata previous =
                observedTrackMetadata.getAndSet(new ObservedTrackMetadata(track, now));
        if (previous != null
                && !TrackIdentity.matchesHintKey(
                previous.track.trackHintKey(),
                track.trackHintKey())) {
            currentInternalContext.set(null);
            currentTranslatedBaseRequestId.set("");
            currentInternalRequestId.set("");
            module.info("Reset QQ Music internal lyric context after track metadata changed"
                    + ", title=" + track.title
                    + ", artist=" + track.artist);
        }
    }

    @Override
    protected void onLookupNotAvailable(LockscreenLyricsModule module, TrackMetadata track) {
        if (internalLyricHookInstalled) {
            module.info("QQ Music metadata observed; waiting for internal lyric object"
                    + " for title=" + track.title);
            return;
        }
        super.onLookupNotAvailable(module, track);
    }

    private Object onMediaSessionUpdate(
            LockscreenLyricsModule module,
            XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            Object songInfo = chain.getArg(1);
            Object lyric = chain.getArg(2);
            publishInternalLyric(module, songInfo, lyric);
        } catch (Throwable t) {
            module.error("Failed while decoding QQ Music internal lyric object", t);
        }
        return result;
    }

    private void publishInternalLyric(
            LockscreenLyricsModule module,
            Object songInfo,
            Object lyric) {
        QqMusicInternalLyricExtractor.SongMetadata metadata =
                QqMusicInternalLyricExtractor.readSongMetadata(songInfo);
        TrackMetadata observedTrack = recentObservedTrack();
        String title = firstUsableTrackText(
                observedTrack == null ? "" : observedTrack.title,
                metadata.title);
        String artist = firstUsableTrackText(
                observedTrack == null ? "" : observedTrack.artist,
                metadata.artist);
        String mediaId = observedTrack == null ? "" : observedTrack.mediaId;
        String mediaUri = observedTrack == null ? "" : observedTrack.mediaUri;
        String trackHintKey = TextUtils.isEmpty(title)
                ? metadata.trackHintKey()
                : LockscreenLyricsModule.buildTrackKey(title, artist);
        TimedLyricDocument baseDocument = QqMusicInternalLyricExtractor.extract(lyric);
        String identity = firstNonEmptyValue(mediaId, metadata.songId);
        String baseRawLyric = baseDocument.toEnhancedLrc();
        String baseRequestId = buildInternalRequestId(
                identity,
                title,
                trackHintKey,
                baseRawLyric,
                baseDocument.lineCount());
        InternalLyricContext context = new InternalLyricContext(
                identity,
                title,
                artist,
                mediaId,
                mediaUri,
                trackHintKey,
                baseDocument,
                baseRequestId,
                System.currentTimeMillis());
        TimedLyricDocument document = mergeRecentDecryptedLyrics(context);
        maybeLogProbe(
                module,
                probeReason(trackHintKey, document),
                metadata,
                observedTrack,
                songInfo,
                lyric,
                document);
        if (TextUtils.isEmpty(trackHintKey)) {
            module.info("Skip QQ Music internal lyric because SongInfo has no title");
            return;
        }

        if (document.isEmpty()) {
            module.info("QQ Music internal lyric object is empty for title=" + metadata.title);
            return;
        }
        if (!document.hasWordTiming()) {
            module.info("QQ Music internal lyric object has no word timing for title="
                    + metadata.title + ", lines=" + document.lineCount());
            return;
        }

        currentInternalContext.set(context);
        publishResolvedInternalLyric(module, context, document);
        if (document.translationCount() == 0) {
            module.info("QQ Music internal word lyric has no usable internal translation yet"
                    + ", waiting for QRCDesDecrypt candidate, title=" + title);
        }
    }

    private Object onQrcDecrypt(
            LockscreenLyricsModule module,
            XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (result instanceof String) {
                recordDecryptedLyric(module, (String) result);
            }
        } catch (Throwable t) {
            module.error("Failed while probing QQ Music decrypted QRC lyric", t);
        }
        return result;
    }

    private void recordDecryptedLyric(LockscreenLyricsModule module, String rawLyric) {
        if (TextUtils.isEmpty(rawLyric)) {
            return;
        }
        TimedLyricDocument rawDocument = QqMusicLyricClient.parseQrcOrLrc(rawLyric);
        if (rawDocument.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        DecryptedLyricCandidate candidate = new DecryptedLyricCandidate(
                rawLyric,
                rawDocument,
                now);
        if (candidate.document.isEmpty()) {
            return;
        }
        storeDecryptedCandidate(candidate, now);

        InternalLyricContext context = currentInternalContext.get();
        boolean compatible = canApplyCandidateToContext(context, candidate, now);
        TimedLyricDocument enrichedBase = compatible
                ? context.baseDocument.withWordTimingFrom(
                candidate.document,
                INTERNAL_WORD_TIMING_MATCH_MS)
                : TimedLyricDocument.EMPTY;
        int usableMatches = !compatible
                ? 0
                : enrichedBase.usableTranslationMatchCount(
                candidate.document,
                INTERNAL_TRANSLATION_MATCH_MS);
        String logSignature = candidate.signature + '|' + usableMatches;
        if (!logSignature.equals(currentDecryptedLyricSignature.getAndSet(logSignature))) {
            QqMusicLyricProbe.logDecryptedLyric(
                    module,
                    candidate.document.hasWordTiming() ? "word-or-primary" : "line-candidate",
                    rawLyric,
                    candidate.document,
                    candidate.trackHintKey,
                    compatible,
                    usableMatches);
        }

        if (!compatible) {
            return;
        }
        TimedLyricDocument merged = enrichedBase.withUsableTranslationsFrom(
                candidate.document,
                INTERNAL_TRANSLATION_MATCH_MS);
        if (merged.translationCount() > context.baseDocument.translationCount()
                || merged.wordTimedLineCount() > context.baseDocument.wordTimedLineCount()) {
            module.info("Merged QQ Music internal decrypted translation"
                    + ", title=" + context.title
                    + ", matches=" + usableMatches
                    + ", translations=" + merged.translationCount()
                    + ", wordTimedLines=" + merged.wordTimedLineCount());
            publishResolvedInternalLyric(module, context, merged);
        }
    }

    private void storeDecryptedCandidate(DecryptedLyricCandidate candidate, long now) {
        synchronized (decryptedLyricsLock) {
            pruneDecryptedCandidatesLocked(now);
            DecryptedLyricCandidate last = decryptedLyricCandidates.peekLast();
            if (last != null && last.signature.equals(candidate.signature)) {
                return;
            }
            decryptedLyricCandidates.addLast(candidate);
            while (decryptedLyricCandidates.size() > MAX_DECRYPTED_CANDIDATES) {
                decryptedLyricCandidates.removeFirst();
            }
        }
    }

    private TimedLyricDocument mergeRecentDecryptedLyrics(InternalLyricContext context) {
        TimedLyricDocument baseDocument = context == null
                ? TimedLyricDocument.EMPTY
                : context.baseDocument;
        if (baseDocument == null || baseDocument.isEmpty()) {
            return baseDocument == null ? TimedLyricDocument.EMPTY : baseDocument;
        }
        long now = System.currentTimeMillis();
        TimedLyricDocument bestDocument = baseDocument;
        int bestTranslations = baseDocument.translationCount();
        int bestWordTimedLines = baseDocument.wordTimedLineCount();
        int bestMatches = 0;
        synchronized (decryptedLyricsLock) {
            pruneDecryptedCandidatesLocked(now);
            for (DecryptedLyricCandidate candidate : decryptedLyricCandidates) {
                if (!canApplyCandidateToContext(context, candidate, now)) {
                    continue;
                }
                TimedLyricDocument wordEnriched = baseDocument.withWordTimingFrom(
                        candidate.document,
                        INTERNAL_WORD_TIMING_MATCH_MS);
                int matches = wordEnriched.usableTranslationMatchCount(
                        candidate.document,
                        INTERNAL_TRANSLATION_MATCH_MS);
                TimedLyricDocument merged = wordEnriched.withUsableTranslationsFrom(
                        candidate.document,
                        INTERNAL_TRANSLATION_MATCH_MS);
                int translations = merged.translationCount();
                int wordTimedLines = merged.wordTimedLineCount();
                if (translations > bestTranslations
                        || wordTimedLines > bestWordTimedLines
                        || (translations == bestTranslations
                        && wordTimedLines == bestWordTimedLines
                        && matches > bestMatches)) {
                    bestDocument = merged;
                    bestTranslations = translations;
                    bestWordTimedLines = wordTimedLines;
                    bestMatches = matches;
                }
            }
        }
        return bestDocument;
    }

    private boolean canApplyCandidateToContext(
            InternalLyricContext context,
            DecryptedLyricCandidate candidate,
            long now) {
        if (context == null || candidate == null || context.baseDocument.isEmpty()) {
            return false;
        }
        if (!contextMatchesObservedTrack(context, now)) {
            return false;
        }
        if (!TextUtils.isEmpty(candidate.trackHintKey)
                && !TextUtils.isEmpty(context.trackHintKey)) {
            return TrackIdentity.matchesHintKey(candidate.trackHintKey, context.trackHintKey);
        }
        return candidate.observedAtMillis + CANDIDATE_BEFORE_CONTEXT_GRACE_MS
                >= context.createdAtMillis;
    }

    private boolean contextMatchesObservedTrack(InternalLyricContext context, long now) {
        ObservedTrackMetadata observed = observedTrackMetadata.get();
        if (observed == null
                || now - observed.observedAtMillis > RECENT_MEDIA_SESSION_METADATA_MS
                || TextUtils.isEmpty(observed.track.trackHintKey())
                || TextUtils.isEmpty(context.trackHintKey)) {
            return true;
        }
        return TrackIdentity.matchesHintKey(context.trackHintKey, observed.track.trackHintKey());
    }

    private void pruneDecryptedCandidatesLocked(long now) {
        while (!decryptedLyricCandidates.isEmpty()) {
            DecryptedLyricCandidate first = decryptedLyricCandidates.peekFirst();
            if (first == null
                    || now - first.observedAtMillis <= RECENT_DECRYPTED_LYRIC_MS) {
                return;
            }
            decryptedLyricCandidates.removeFirst();
        }
    }

    private void publishResolvedInternalLyric(
            LockscreenLyricsModule module,
            InternalLyricContext context,
            TimedLyricDocument document) {
        if (context == null || document == null || document.isEmpty()) {
            return;
        }
        if (document.translationCount() == 0
                && context.baseRequestId.equals(currentTranslatedBaseRequestId.get())) {
            module.info("Skip QQ Music no-translation internal lyric rebroadcast"
                    + " because translated internal lyric is active, title=" + context.title);
            return;
        }

        String rawLyric = document.toEnhancedLrc();
        String requestId = buildInternalRequestId(
                context.identity,
                context.title,
                context.trackHintKey,
                rawLyric,
                document.lineCount());
        if (requestId.equals(currentInternalRequestId.getAndSet(requestId))) {
            return;
        }
        if (document.translationCount() > 0) {
            currentTranslatedBaseRequestId.set(context.baseRequestId);
        }
        module.reportLyricSourceEvent(LyricSourceEvent.resolved(
                SOURCE_NAME,
                requestId,
                context.mediaId,
                context.mediaUri,
                context.trackHintKey,
                document.toPlainLrc(),
                rawLyric,
                System.currentTimeMillis(),
                lyricCapabilities()));
        module.info("Resolved QQ Music internal word lyric"
                + ", title=" + context.title
                + ", artist=" + context.artist
                + ", lines=" + document.lineCount()
                + ", translations=" + document.translationCount()
                + ", internalTranslation="
                + (document.translationCount() > context.baseDocument.translationCount()));
    }

    private static String inferDocumentTrackHintKey(TimedLyricDocument document) {
        if (document == null || document.isEmpty()) {
            return "";
        }
        int inspected = 0;
        for (TimedLyricDocument.Line line : document.lines()) {
            if (line == null || line.startMillis > 15_000L || inspected >= 4) {
                break;
            }
            inspected++;
            String key = trackHintKeyFromCreditLine(line.text);
            if (!TextUtils.isEmpty(key)) {
                return key;
            }
        }
        return "";
    }

    private static String trackHintKeyFromCreditLine(String text) {
        String value = nullToEmpty(text).trim();
        if (value.length() < 5 || value.length() > 128) {
            return "";
        }
        int separator = findCreditSeparator(value);
        if (separator <= 0 || separator + 1 >= value.length()) {
            return "";
        }
        String title = value.substring(0, separator).trim();
        String artist = value.substring(separator + 1).trim();
        if (artist.startsWith("-")) {
            artist = artist.substring(1).trim();
        }
        if (title.length() < 1 || artist.length() < 1) {
            return "";
        }
        return TrackIdentity.buildKey(title, artist);
    }

    private static int findCreditSeparator(String value) {
        String[] separators = {
                " - ",
                "- ",
                " -",
                "\u2013",
                "\u2014"
        };
        int best = -1;
        for (String separator : separators) {
            int index = value.lastIndexOf(separator);
            if (index > 0 && index + separator.length() < value.length()) {
                best = Math.max(best, index);
            }
        }
        if (best >= 0) {
            return best;
        }
        int plainHyphen = value.lastIndexOf('-');
        return plainHyphen > 0 && plainHyphen + 1 < value.length() ? plainHyphen : -1;
    }

    private void maybeLogProbe(
            LockscreenLyricsModule module,
            String reason,
            QqMusicInternalLyricExtractor.SongMetadata metadata,
            TrackMetadata observedTrack,
            Object songInfo,
            Object lyric,
            TimedLyricDocument document) {
        String signature = QqMusicLyricProbe.buildSignature(
                reason,
                metadata,
                observedTrack,
                lyric,
                document);
        if (signature.equals(currentProbeSignature.getAndSet(signature))) {
            return;
        }
        int count = probeLogCount.get();
        while (count < MAX_PROBE_LOGS_PER_PROCESS) {
            if (probeLogCount.compareAndSet(count, count + 1)) {
                QqMusicLyricProbe.logInternalLyric(
                        module,
                        reason,
                        metadata,
                        observedTrack,
                        songInfo,
                        lyric,
                        document);
                return;
            }
            count = probeLogCount.get();
        }
    }

    private static String probeReason(String trackHintKey, TimedLyricDocument document) {
        if (TextUtils.isEmpty(trackHintKey)) {
            return "missing-track";
        }
        if (document == null || document.isEmpty()) {
            return "empty";
        }
        if (!document.hasWordTiming()) {
            return "no-word-timing";
        }
        if (document.translationCount() == 0) {
            return "resolved-no-translation";
        }
        return "resolved";
    }

    private void installQrcDecryptHook(LockscreenLyricsModule module, ClassLoader classLoader) {
        try {
            Class<?> decryptClass = classLoader.loadClass(QRC_DECRYPT_CLASS);
            Method method = decryptClass.getDeclaredMethod(QRC_DECRYPT_METHOD, String.class);
            method.setAccessible(true);
            module.hook(method)
                    .setId("qq-music-qrc-decrypt-lyric")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> onQrcDecrypt(module, chain));
            module.info("Hooked QQ Music QRC decrypted lyric probe: "
                    + QqMusicLyricProbe.describeMethod(method));
        } catch (Throwable t) {
            module.error("Failed to hook QQ Music QRC decrypted lyric probe", t);
        }
    }

    private TrackMetadata recentObservedTrack() {
        ObservedTrackMetadata observed = observedTrackMetadata.get();
        if (observed == null
                || System.currentTimeMillis() - observed.observedAtMillis
                > RECENT_MEDIA_SESSION_METADATA_MS) {
            return null;
        }
        return observed.track;
    }

    private static String buildInternalRequestId(
            String mediaId,
            String title,
            String trackHintKey,
            String rawLyric,
            int lineCount) {
        String identity = firstNonEmptyValue(
                mediaId,
                title,
                trackHintKey);
        String payload = rawLyric == null ? "" : rawLyric;
        return "qqmusic-internal:"
                + identity
                + ':' + lineCount
                + ':' + payload.length()
                + ':' + Integer.toHexString(payload.hashCode());
    }

    private static Method resolveMediaSessionUpdateMethod(
            LockscreenLyricsModule module,
            DexKitBridge bridge,
            ClassLoader classLoader) throws ReflectiveOperationException {
        MethodDataList strictMethods = findMediaSessionUpdateMethods(bridge, true);
        logDexKitCandidates(module, "strict-name", strictMethods, classLoader);
        Method method = firstVerifiedMethod(
                strictMethods,
                classLoader);
        if (method != null) {
            module.info("QQ Lyric Probe resolved hook via strict DexKit: "
                    + QqMusicLyricProbe.describeMethod(method));
            return method;
        }
        MethodDataList looseMethods = findMediaSessionUpdateMethods(bridge, false);
        logDexKitCandidates(module, "loose-signature", looseMethods, classLoader);
        method = firstVerifiedMethod(
                looseMethods,
                classLoader);
        if (method != null) {
            module.info("QQ Lyric Probe resolved hook via loose DexKit: "
                    + QqMusicLyricProbe.describeMethod(method));
            return method;
        }
        method = findLegacyMediaSessionUpdateMethod(classLoader);
        module.info("QQ Lyric Probe resolved hook via legacy scan: "
                + QqMusicLyricProbe.describeMethod(method));
        return method;
    }

    private static void logDexKitCandidates(
            LockscreenLyricsModule module,
            String label,
            MethodDataList methods,
            ClassLoader classLoader) {
        if (module == null) {
            return;
        }
        int total = 0;
        int verified = 0;
        StringBuilder samples = new StringBuilder();
        if (methods != null) {
            for (MethodData methodData : methods) {
                total++;
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    boolean valid = isMediaSessionUpdateMethod(method);
                    if (valid) {
                        verified++;
                    }
                    if (samples.length() < 700) {
                        if (samples.length() > 0) {
                            samples.append(" | ");
                        }
                        samples.append(valid ? "ok:" : "skip:")
                                .append(QqMusicLyricProbe.describeMethod(method));
                    }
                } catch (Throwable t) {
                    if (samples.length() < 700) {
                        if (samples.length() > 0) {
                            samples.append(" | ");
                        }
                        samples.append("error:")
                                .append(t.getClass().getSimpleName());
                    }
                }
            }
        }
        module.info("QQ Lyric Probe DexKit " + label
                + " candidates total=" + total
                + ", verified=" + verified
                + ", sample=" + (samples.length() == 0 ? "none" : samples));
    }

    private static MethodDataList findMediaSessionUpdateMethods(
            DexKitBridge bridge,
            boolean requireMethodName) {
        MethodMatcher matcher = MethodMatcher.create()
                .params(ParametersMatcher.create()
                        .add(BUILDER_CLASS_SUFFIX, StringMatchType.EndsWith)
                        .add(SONG_INFO_CLASS, StringMatchType.Equals)
                        .add(LYRIC_ENGINE_CLASS, StringMatchType.Equals));
        if (requireMethodName) {
            matcher.name("h");
        }
        return bridge.findMethod(FindMethod.create()
                .searchPackages(
                        "com.tencent.qqmusicplayerprocess",
                        "com.tencent.qqmusic")
                .matcher(matcher));
    }

    private static Method firstVerifiedMethod(MethodDataList methods, ClassLoader classLoader) {
        if (methods == null) {
            return null;
        }
        for (MethodData methodData : methods) {
            try {
                Method method = methodData.getMethodInstance(classLoader);
                if (isMediaSessionUpdateMethod(method)) {
                    return method;
                }
            } catch (Throwable ignored) {
                // Keep scanning DexKit candidates; the verified Java signature is authoritative.
            }
        }
        return null;
    }

    private static Method findLegacyMediaSessionUpdateMethod(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> controllerClass = classLoader.loadClass(MEDIA_SESSION_UPDATE_CLASS);
        Method fallback = null;
        for (Method method : controllerClass.getDeclaredMethods()) {
            if (isMediaSessionUpdateMethod(method)) {
                return method;
            }
            if (fallback == null
                    && "h".equals(method.getName())
                    && method.getParameterCount() == 3) {
                fallback = method;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new NoSuchMethodException(
                MEDIA_SESSION_UPDATE_CLASS + "#h(builder, SongInfo, lyric)");
    }

    private static boolean isMediaSessionUpdateMethod(Method method) {
        if (method == null || method.getParameterCount() != 3) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes[0].getName().endsWith(BUILDER_CLASS_SUFFIX)
                && SONG_INFO_CLASS.equals(parameterTypes[1].getName())
                && LYRIC_ENGINE_CLASS.equals(parameterTypes[2].getName());
    }

    private static void ensureDexKitLoaded() {
        if (dexKitLoaded) {
            return;
        }
        synchronized (DEXKIT_LOAD_LOCK) {
            if (dexKitLoaded) {
                return;
            }
            System.loadLibrary("dexkit");
            dexKitLoaded = true;
        }
    }

    private static String firstUsableTrackText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !isSongInfoPlaceholder(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isSongInfoPlaceholder(String value) {
        String normalized = value == null ? "" : value.trim();
        return "0".equals(normalized) || "1".equals(normalized);
    }

    private static final class ObservedTrackMetadata {
        final TrackMetadata track;
        final long observedAtMillis;

        ObservedTrackMetadata(TrackMetadata track, long observedAtMillis) {
            this.track = track;
            this.observedAtMillis = observedAtMillis;
        }
    }

    private static String firstNonEmptyValue(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class InternalLyricContext {
        final String identity;
        final String title;
        final String artist;
        final String mediaId;
        final String mediaUri;
        final String trackHintKey;
        final TimedLyricDocument baseDocument;
        final String baseRequestId;
        final long createdAtMillis;

        InternalLyricContext(
                String identity,
                String title,
                String artist,
                String mediaId,
                String mediaUri,
                String trackHintKey,
                TimedLyricDocument baseDocument,
                String baseRequestId,
                long createdAtMillis) {
            this.identity = firstNonEmptyValue(identity, title, trackHintKey);
            this.title = nullToEmpty(title);
            this.artist = nullToEmpty(artist);
            this.mediaId = nullToEmpty(mediaId);
            this.mediaUri = nullToEmpty(mediaUri);
            this.trackHintKey = nullToEmpty(trackHintKey);
            this.baseDocument = baseDocument == null ? TimedLyricDocument.EMPTY : baseDocument;
            this.baseRequestId = nullToEmpty(baseRequestId);
            this.createdAtMillis = Math.max(0L, createdAtMillis);
        }
    }

    private static final class DecryptedLyricCandidate {
        final TimedLyricDocument document;
        final long observedAtMillis;
        final String trackHintKey;
        final String signature;

        DecryptedLyricCandidate(
                String rawLyric,
                TimedLyricDocument document,
                long observedAtMillis) {
            String raw = nullToEmpty(rawLyric);
            TimedLyricDocument rawDocument =
                    document == null ? TimedLyricDocument.EMPTY : document;
            this.document = QqMusicInternalLyricExtractor.withoutLikelyMetadataLines(rawDocument);
            this.observedAtMillis = observedAtMillis;
            this.trackHintKey = inferDocumentTrackHintKey(rawDocument);
            this.signature = raw.length()
                    + ":"
                    + Integer.toHexString(raw.hashCode())
                    + ":"
                    + this.document.lineCount()
                    + ":"
                    + this.trackHintKey
                    + ":"
                    + this.document.hasWordTiming()
                    + ":"
                    + this.document.translationCount();
        }
    }
}
