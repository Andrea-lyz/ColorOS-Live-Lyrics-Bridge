package io.github.andrealtb.lockscreenlyrics;

import android.annotation.SuppressLint;

import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;

abstract class FirstBatchMediaSessionAdapter implements PlayerAdapter {
    private final String packageName;
    private final String displayName;
    private final String sourceName;
    private final ExecutorService executor;
    private final AtomicReference<String> currentRequestId = new AtomicReference<>("");

    FirstBatchMediaSessionAdapter(String packageName, String displayName, String sourceName) {
        this.packageName = packageName;
        this.displayName = displayName;
        this.sourceName = sourceName;
        this.executor = Executors.newSingleThreadExecutor(new AdapterThreadFactory(displayName));
    }

    @Override
    public final String packageName() {
        return packageName;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public LyricProviderCapabilities lyricCapabilities() {
        return LyricProviderCapabilities.ACTIVE_INTEGRATION;
    }

    @Override
    public final boolean allowsModuleToReplaceUntrustedLyricInfo() {
        return true;
    }

    @Override
    public final boolean publishesCapturedLyricToMediaSession() {
        return false;
    }

    @Override
    public final boolean rewritesPlayerLyricInfoMetadata() {
        return false;
    }

    @Override
    public void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader) {
        module.installInjectedTranslationToggleActionHook(packageName);
        try {
            Method setMetadata =
                    MediaSession.class.getDeclaredMethod("setMetadata", MediaMetadata.class);
            module.hook(setMetadata)
                    .setId("first-batch-media-session-" + packageName)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> onSetMetadata(module, chain));
            module.info("Hooked " + displayName + " MediaSession metadata for first-batch lyrics");
        } catch (Throwable t) {
            module.error("Failed to hook " + displayName + " MediaSession metadata", t);
        }
    }

    private Object onSetMetadata(
            LockscreenLyricsModule module,
            XposedInterface.Chain chain) throws Throwable {
        Object metadataArg = chain.getArg(0);
        Object result = chain.proceed();
        if (metadataArg instanceof MediaMetadata) {
            onMetadata(module, (MediaMetadata) metadataArg);
        }
        return result;
    }

    @SuppressLint("WrongConstant")
    private void onMetadata(LockscreenLyricsModule module, MediaMetadata metadata) {
        TrackMetadata track = TrackMetadata.from(metadata);
        if (!track.hasUsableTitle()) {
            return;
        }
        onObservedTrackMetadata(module, track, metadata);
        String requestId = sourceName + ':' + track.identityKey();
        if (requestId.equals(currentRequestId.getAndSet(requestId))) {
            return;
        }

        String existingLyricInfo = metadata.getString(LyricInfoContract.METADATA_KEY);
        LyricInfoContract.Payload officialPayload = LyricInfoContract.parse(existingLyricInfo);
        if (officialPayload != null && !officialPayload.hasWordTiming()) {
            module.info("Detected simple official lyricInfo from " + displayName
                    + "; scheduling enhanced lookup for title=" + track.title);
        }

        if (!shouldLookupLyrics(track)) {
            onLookupNotAvailable(module, track);
            return;
        }

        module.reportLyricSourceEvent(LyricSourceEvent.lookupStarted(
                sourceName,
                requestId,
                track.mediaId,
                track.mediaUri,
                track.trackHintKey(),
                System.currentTimeMillis(),
                lyricCapabilities()));
        executor.execute(() -> resolveLyrics(module, track, requestId));
    }

    private void resolveLyrics(
            LockscreenLyricsModule module,
            TrackMetadata track,
            String requestId) {
        try {
            TimedLyricDocument document = loadLyrics(track);
            if (document == null || document.isEmpty()) {
                module.reportLyricSourceEvent(LyricSourceEvent.terminal(
                        LyricSourceEvent.Outcome.NO_LYRIC,
                        sourceName,
                        requestId,
                        track.mediaId,
                        track.mediaUri,
                        track.trackHintKey(),
                        "",
                        System.currentTimeMillis(),
                        lyricCapabilities()));
                return;
            }
            String rawLyric = document.hasWordTiming()
                    ? document.toEnhancedLrc()
                    : document.toPlainLrc();
            module.reportLyricSourceEvent(LyricSourceEvent.resolved(
                    sourceName,
                    requestId,
                    track.mediaId,
                    track.mediaUri,
                    track.trackHintKey(),
                    document.toPlainLrc(),
                    rawLyric,
                    System.currentTimeMillis(),
                    lyricCapabilities()));
            module.info("Resolved " + displayName + " lyric document"
                    + ", lines=" + document.lineCount()
                    + ", wordTiming=" + document.hasWordTiming()
                    + ", title=" + track.title);
        } catch (NoLyricException e) {
            module.reportLyricSourceEvent(LyricSourceEvent.terminal(
                    LyricSourceEvent.Outcome.NO_LYRIC,
                    sourceName,
                    requestId,
                    track.mediaId,
                    track.mediaUri,
                    track.trackHintKey(),
                    "",
                    System.currentTimeMillis(),
                    lyricCapabilities()));
            module.info(displayName + " reported no lyric for title=" + track.title);
        } catch (Throwable t) {
            module.reportLyricSourceEvent(LyricSourceEvent.terminal(
                    LyricSourceEvent.Outcome.PARSE_FAILED,
                    sourceName,
                    requestId,
                    track.mediaId,
                    track.mediaUri,
                    track.trackHintKey(),
                    "",
                    System.currentTimeMillis(),
                    lyricCapabilities()));
            module.error("Failed to resolve " + displayName + " lyrics for title="
                    + track.title, t);
        }
    }

    protected boolean shouldLookupLyrics(TrackMetadata track) {
        return false;
    }

    protected TimedLyricDocument loadLyrics(TrackMetadata track) throws Exception {
        return TimedLyricDocument.EMPTY;
    }

    protected void onLookupNotAvailable(LockscreenLyricsModule module, TrackMetadata track) {
        module.info(displayName + " first-batch adapter observed track metadata; "
                + "enhanced lyric resolver is not wired yet, title=" + track.title);
    }

    protected void onObservedTrackMetadata(
            LockscreenLyricsModule module,
            TrackMetadata track,
            MediaMetadata metadata) {
    }

    static final class TrackMetadata {
        final String title;
        final String artist;
        final long durationMillis;
        final String mediaId;
        final String mediaUri;

        private TrackMetadata(
                String title,
                String artist,
                long durationMillis,
                String mediaId,
                String mediaUri) {
            this.title = nullToEmpty(title);
            this.artist = nullToEmpty(artist);
            this.durationMillis = durationMillis;
            this.mediaId = nullToEmpty(mediaId);
            this.mediaUri = LyricSourceEvent.normalizeUri(mediaUri);
        }

        static TrackMetadata from(MediaMetadata metadata) {
            return new TrackMetadata(
                    firstNonEmpty(
                            text(metadata, MediaMetadata.METADATA_KEY_TITLE),
                            text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)),
                    firstNonEmpty(
                            text(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                            text(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                            text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)),
                    metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI));
        }

        boolean hasUsableTitle() {
            return !TextUtils.isEmpty(title);
        }

        String trackHintKey() {
            return LockscreenLyricsModule.buildTrackKey(title, artist);
        }

        String identityKey() {
            return firstNonEmpty(mediaId, mediaUri, trackHintKey());
        }

        private static String text(MediaMetadata metadata, String key) {
            CharSequence value = metadata.getText(key);
            return value == null
                    ? ""
                    : LyricTextSanitizer.removeIgnorableCharacters(value.toString()).trim();
        }
    }

    static final class NoLyricException extends Exception {
        NoLyricException(String message) {
            super(message);
        }
    }

    private static final class AdapterThreadFactory implements ThreadFactory {
        private final String name;

        AdapterThreadFactory(String displayName) {
            this.name = "LockscreenLyrics-"
                    + displayName.replaceAll("[^A-Za-z0-9]+", "-").toLowerCase(Locale.ROOT);
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }

    static String firstNonEmpty(String... values) {
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
}
