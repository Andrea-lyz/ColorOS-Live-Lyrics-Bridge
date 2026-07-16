package io.github.andrealtb.lockscreenlyrics;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.enums.MatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;

final class SaltPlayerAdapter implements PlayerAdapter {
    private static final String PACKAGE_NAME = "com.salt.music";
    private static final String HOOK_ID_PREFIX = "salt-player-lyric-result-";
    private static final String HOOK_ID_MEDIA_BUTTON_RECEIVER =
            "salt-player-media-button-receiver";
    private static final String MEDIA_BUTTON_RECEIVER_CLASS =
            "com.salt.music.service.MediaButtonIntentReceiver";
    private static final String MUSIC_SERVICE_CLASS =
            "com.salt.music.service.MusicService";
    private static final String SALT_SONG_CLASS =
            "com.salt.music.data.entry.Song";
    private static final String ACTION_PLAY_OR_PAUSE = "com.salt.music.play_or_pause";
    private static final String OBFUSCATED_PACKAGE = "androidx.obf";
    private static final String SOURCE_ENUM_MARKER_EMBEDDED = "EMBEDDED";
    private static final String SOURCE_ENUM_MARKER_LYRICS3 = "TAG_LYRICS3_V2";
    private static final String SCROLL_ENUM_MARKER_CAN_SCROLL = "CAN_SCROLL";
    private static final String SCROLL_ENUM_MARKER_NOT_SCROLL = "NOT_SCROLL";
    private static final long MEDIA_BUTTON_DEBOUNCE_MS = 1_200L;
    private static final long PLAY_AFTER_SERVICE_START_DELAY_MS = 600L;
    private static final Object DEXKIT_LOAD_LOCK = new Object();
    private static final Object MEDIA_BUTTON_START_LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static volatile boolean dexKitLoaded;
    private static volatile boolean mediaButtonReceiverHookInstalled;
    private static long lastMediaButtonStartElapsedRealtime;

    @Override
    public String packageName() {
        return PACKAGE_NAME;
    }

    @Override
    public String displayName() {
        return "Salt Player";
    }

    @Override
    public LyricProviderCapabilities lyricCapabilities() {
        return LyricProviderCapabilities.ACTIVE_INTEGRATION;
    }

    @Override
    public boolean supportsLyricRelayMetadata() {
        return true;
    }

    @Override
    public boolean mayRetainStaleLyricInfo() {
        return true;
    }

    @Override
    public boolean allowsModuleToReplaceUntrustedLyricInfo() {
        return true;
    }

    @Override
    @SuppressLint("DuplicateCreateDexKit") // One bridge for the one-time Salt resolver transaction.
    public void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader) {
        // Reuse an existing custom-action channel for the public translation toggle when present.
        // Do not create a new custom-action bucket for builds that publish none.
        module.installInjectedTranslationToggleActionHook(PACKAGE_NAME);
        installMediaButtonReceiverHook(module, classLoader);

        try {
            ensureDexKitLoaded();
        } catch (Throwable t) {
            module.error("Failed to load DexKit for " + displayName(), t);
            return;
        }

        // This adapter resolves once when the player process becomes ready, then closes the
        // bridge after all related queries.
        //noinspection DuplicateCreateDexKit -- the bridge is closed after this one-time resolver.
        try (DexKitBridge bridge = DexKitBridge.create(classLoader, true)) {
            ClassData sourceEnum = findSingleClassUsingStrings(
                    bridge,
                    "lyric source enum",
                    SOURCE_ENUM_MARKER_EMBEDDED,
                    SOURCE_ENUM_MARKER_LYRICS3);
            ClassData scrollEnum = findSingleClassUsingStrings(
                    bridge,
                    "lyric scroll enum",
                    SCROLL_ENUM_MARKER_CAN_SCROLL,
                    SCROLL_ENUM_MARKER_NOT_SCROLL);
            ClassData lyricResult = findLyricResultClass(bridge, sourceEnum, scrollEnum);

            Class<?> sourceEnumClass = sourceEnum.getInstance(classLoader);
            Class<?> lyricResultClass = lyricResult.getInstance(classLoader);
            installFinalLyricPublicationHook(
                    module,
                    bridge,
                    lyricResult,
                    lyricResultClass,
                    sourceEnumClass);
            module.info("Hooked " + displayName()
                    + " final lyric publication via DexKit: result=" + lyricResult.getName()
                    + ", source=" + sourceEnum.getName()
                    + ", scroll=" + scrollEnum.getName());
        } catch (Throwable t) {
            module.error("Failed to hook " + displayName()
                    + " final lyric publication via DexKit", t);
        }
    }

    private static void installMediaButtonReceiverHook(
            LockscreenLyricsModule module,
            ClassLoader classLoader) {
        if (mediaButtonReceiverHookInstalled) {
            return;
        }
        synchronized (SaltPlayerAdapter.class) {
            if (mediaButtonReceiverHookInstalled) {
                return;
            }
            try {
                Class<?> receiverClass = classLoader.loadClass(MEDIA_BUTTON_RECEIVER_CLASS);
                Method onReceive = receiverClass.getDeclaredMethod(
                        "onReceive",
                        Context.class,
                        Intent.class);
                onReceive.setAccessible(true);
                module.hook(onReceive)
                        .setId(HOOK_ID_MEDIA_BUTTON_RECEIVER)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> onMediaButtonReceive(module, chain));
                mediaButtonReceiverHookInstalled = true;
                module.info("Hooked Salt Player media button receiver");
            } catch (ClassNotFoundException e) {
                module.info("Salt Player media button receiver is not present in this build");
            } catch (Throwable t) {
                module.error("Failed to hook Salt Player media button receiver", t);
            }
        }
    }

    private static Object onMediaButtonReceive(
            LockscreenLyricsModule module,
            XposedInterface.Chain chain) throws Throwable {
        Object contextArg = chain.getArg(0);
        Object intentArg = chain.getArg(1);
        if (!(contextArg instanceof Context) || !(intentArg instanceof Intent)) {
            return chain.proceed();
        }

        Context context = (Context) contextArg;
        Intent intent = (Intent) intentArg;
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())
                || !isPlayMediaButtonIntent(intent)) {
            return chain.proceed();
        }
        if (!tryAcceptMediaButtonStart()) {
            return null;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        startSaltMusicService(module, appContext, null);
        Context delayedContext = appContext;
        MAIN_HANDLER.postDelayed(
                () -> startSaltMusicService(module, delayedContext, ACTION_PLAY_OR_PAUSE),
                PLAY_AFTER_SERVICE_START_DELAY_MS);
        module.info("Handled Salt Player media button play request");
        return null;
    }

    private static boolean tryAcceptMediaButtonStart() {
        long now = SystemClock.elapsedRealtime();
        synchronized (MEDIA_BUTTON_START_LOCK) {
            if (!LockscreenIntegrationPolicy.shouldAcceptDebouncedEvent(
                    now,
                    lastMediaButtonStartElapsedRealtime,
                    MEDIA_BUTTON_DEBOUNCE_MS)) {
                return false;
            }
            lastMediaButtonStartElapsedRealtime = now;
            return true;
        }
    }

    private static boolean isPlayMediaButtonIntent(Intent intent) {
        KeyEvent event = mediaButtonKeyEvent(intent);
        if (event == null) {
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK;
    }

    @SuppressWarnings("deprecation")
    private static KeyEvent mediaButtonKeyEvent(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class);
        }
        Object extra = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        return extra instanceof KeyEvent ? (KeyEvent) extra : null;
    }

    private static void startSaltMusicService(
            LockscreenLyricsModule module,
            Context context,
            String action) {
        Intent serviceIntent = new Intent();
        serviceIntent.setComponent(new ComponentName(PACKAGE_NAME, MUSIC_SERVICE_CLASS));
        if (action != null) {
            serviceIntent.setAction(action);
        }
        try {
            context.startForegroundService(serviceIntent);
        } catch (Throwable t) {
            module.error("Failed to start Salt Player MusicService"
                    + (action == null ? "" : " with action " + action), t);
        }
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

    private static ClassData findSingleClassUsingStrings(
            DexKitBridge bridge,
            String description,
            String... strings) {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .searchPackages(OBFUSCATED_PACKAGE)
                .matcher(ClassMatcher.create().usingEqStrings(strings)));
        return requireSingleClass(description, classes);
    }

    private static ClassData findLyricResultClass(
            DexKitBridge bridge,
            ClassData sourceEnum,
            ClassData scrollEnum) {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .searchPackages(OBFUSCATED_PACKAGE)
                .matcher(ClassMatcher.create()
                        .fields(FieldsMatcher.create()
                                .addForType(sourceEnum.getName())
                                .addForType(scrollEnum.getName())
                                .matchType(MatchType.Contains))));
        return requireSingleClass("lyric result class", classes);
    }

    /**
     * Finds Salt's coroutine which publishes one {@code (Song, LyricResult)} pair only after it
     * re-checks that the asynchronous result still belongs to the current {@code Song.id}.
     *
     * <p>Salt has already discarded stale asynchronous results at this point. We preserve the
     * originating {@code Song}'s title/artist as explicit identity instead of guessing from LRC
     * tags or binding an identity-less parser callback to whichever MediaSession is current.</p>
     */
    private static void installFinalLyricPublicationHook(
            LockscreenLyricsModule module,
            DexKitBridge bridge,
            ClassData lyricResult,
            Class<?> lyricResultClass,
            Class<?> sourceEnumClass) throws Throwable {
        ClassData publisher = findFinalLyricPublisherClass(bridge, lyricResult);
        Class<?> publisherClass = publisher.getInstance(lyricResultClass.getClassLoader());
        Method invokeSuspend = findInvokeSuspendMethod(publisherClass);
        invokeSuspend.setAccessible(true);
        module.hook(invokeSuspend)
                .setId(HOOK_ID_PREFIX
                        + simpleClassName(publisherClass.getName()) + "-final-publication")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> onFinalLyricPublication(
                        module,
                        chain,
                        lyricResultClass,
                        sourceEnumClass));
        module.info("Using Salt Player final lyric publisher: " + publisher.getName());
    }

    private static ClassData findFinalLyricPublisherClass(
            DexKitBridge bridge,
            ClassData lyricResult) {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .searchPackages(OBFUSCATED_PACKAGE)
                .matcher(ClassMatcher.create()
                        .fields(FieldsMatcher.create()
                                .addForType(SALT_SONG_CLASS)
                                .addForType(lyricResult.getName())
                                .matchType(MatchType.Contains))));
        return requireSingleClass("final lyric publisher class", classes);
    }

    private static ClassData requireSingleClass(String description, ClassDataList classes) {
        if (classes.size() == 1) {
            return classes.get(0);
        }
        throw new IllegalStateException(
                "Expected one Salt Player " + description + ", found " + classes.size()
                        + ": " + classes);
    }

    private static Method findInvokeSuspendMethod(Class<?> publisherClass)
            throws NoSuchMethodException {
        for (Method method : publisherClass.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if ("invokeSuspend".equals(method.getName())
                    && parameterTypes.length == 1
                    && parameterTypes[0] == Object.class) {
                return method;
            }
        }
        throw new NoSuchMethodException(publisherClass.getName() + "#invokeSuspend(Object)");
    }

    private static Object onFinalLyricPublication(
            LockscreenLyricsModule module,
            XposedInterface.Chain chain,
            Class<?> lyricResultClass,
            Class<?> sourceEnumClass) throws Throwable {
        Object result = chain.proceed();
        try {
            Object publisher = chain.getThisObject();
            Object song = findFieldValueOfType(publisher, SALT_SONG_CLASS);
            Object lyricResult = findFieldValueOfType(publisher, lyricResultClass);
            if (song == null || lyricResult == null) {
                return result;
            }

            String songId = readStringProperty(song, "getId");
            String title = readStringProperty(song, "getTitle");
            String artist = readStringProperty(song, "getArtist");
            String source = readEnumField(lyricResult, sourceEnumClass);
            String timedLyric = findTimedLyricField(lyricResult);
            String rawCandidate = findRawStringField(lyricResult);
            LyricSourceEvent event = buildFinalLyricEvent(
                    songId,
                    title,
                    artist,
                    source,
                    timedLyric,
                    rawCandidate,
                    System.currentTimeMillis());
            if (event != null) {
                module.reportLyricSourceEvent(event);
            }
        } catch (Throwable t) {
            module.error("Failed while decoding Salt final lyric publication", t);
        }
        return result;
    }

    private static LyricSourceEvent buildFinalLyricEvent(
            String songId,
            String title,
            String artist,
            String source,
            String timedLyric,
            String rawCandidate,
            long nowMillis) {
        String trackHintKey = title == null || title.trim().isEmpty()
                ? ""
                : TrackIdentity.buildKey(title, artist);
        if (trackHintKey.isEmpty()) {
            return null;
        }
        String resolvedSource = "SALT_FINAL_" + (source == null ? "UNKNOWN" : source);
        String rawLyric = timedLyric.isEmpty() ? rawCandidate : timedLyric;
        String requestId = buildFinalRequestId(songId, source, rawLyric);
        if (!timedLyric.isEmpty()) {
            return LyricSourceEvent.resolved(
                    resolvedSource,
                    requestId,
                    "",
                    "",
                    trackHintKey,
                    "",
                    rawLyric,
                    nowMillis,
                    LyricProviderCapabilities.ACTIVE_INTEGRATION);
        }
        LyricSourceEvent.Outcome outcome = LyricResultClassifier.classifyEmptyResult(source);
        return outcome == LyricSourceEvent.Outcome.SOURCE_MISS
                ? null
                : LyricSourceEvent.terminal(
                        outcome,
                        resolvedSource,
                        requestId,
                        "",
                        "",
                        trackHintKey,
                        rawLyric,
                        nowMillis,
                        LyricProviderCapabilities.ACTIVE_INTEGRATION);
    }

    private static String buildFinalRequestId(String songId, String source, String rawLyric) {
        String normalizedSongId = songId == null ? "" : songId.trim();
        String normalizedSource = source == null ? "UNKNOWN" : source;
        String payload = rawLyric == null ? "" : rawLyric;
        return "salt-final:"
                + normalizedSongId
                + ':' + normalizedSource
                + ':' + payload.length()
                + ':' + Integer.toHexString(payload.hashCode());
    }

    private static Object findFieldValueOfType(Object instance, Class<?> type)
            throws IllegalAccessException {
        if (instance == null || type == null) {
            return null;
        }
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (!type.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object findFieldValueOfType(Object instance, String typeName)
            throws IllegalAccessException {
        if (instance == null || typeName == null || typeName.isEmpty()) {
            return null;
        }
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (!typeName.equals(field.getType().getName())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String readStringProperty(Object instance, String methodName) {
        if (instance == null || methodName == null || methodName.isEmpty()) {
            return "";
        }
        try {
            Method method = instance.getClass().getMethod(methodName);
            Object value = method.invoke(instance);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readEnumField(Object instance, Class<?> enumClass)
            throws IllegalAccessException {
        Object value = findFieldValueOfType(instance, enumClass);
        return value == null ? "UNKNOWN" : String.valueOf(value);
    }

    private static String findTimedLyricField(Object instance) throws IllegalAccessException {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getType() != String.class) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value instanceof String
                    && LyricInfoContract.containsTimedLrc((String) value)) {
                return (String) value;
            }
        }
        return "";
    }

    private static String findRawStringField(Object instance) throws IllegalAccessException {
        String candidate = "";
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getType() != String.class) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value instanceof String
                    && ((String) value).trim().length() > candidate.trim().length()) {
                candidate = (String) value;
            }
        }
        return candidate;
    }

    private static String simpleClassName(String className) {
        int index = className.lastIndexOf('.');
        return (index >= 0 ? className.substring(index + 1) : className).toLowerCase(Locale.ROOT);
    }
}
