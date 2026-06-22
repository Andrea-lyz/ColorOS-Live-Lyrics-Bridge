package io.github.andrealtb.lockscreenlyrics;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.lang.reflect.Constructor;
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
        return LyricProviderCapabilities.PASSIVE_PARSER;
    }

    @Override
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
            int hookCount = hookLyricResultConstructors(
                    module,
                    lyricResultClass,
                    sourceEnumClass,
                    lyricCapabilities());
            if (hookCount == 0) {
                throw new IllegalStateException(
                        "No matching lyric result constructors in " + lyricResult.getName());
            }

            module.info("Hooked " + displayName()
                    + " lyric result constructors via DexKit: result=" + lyricResult.getName()
                    + ", source=" + sourceEnum.getName()
                    + ", scroll=" + scrollEnum.getName()
                    + ", count=" + hookCount);
        } catch (Throwable t) {
            module.error("Failed to hook " + displayName()
                    + " lyric result constructors via DexKit", t);
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

    private static ClassData requireSingleClass(String description, ClassDataList classes) {
        if (classes.size() == 1) {
            return classes.get(0);
        }
        throw new IllegalStateException(
                "Expected one Salt Player " + description + ", found " + classes.size()
                        + ": " + classes);
    }

    private static int hookLyricResultConstructors(
            LockscreenLyricsModule module,
            Class<?> lyricResultClass,
            Class<?> sourceEnumClass,
            LyricProviderCapabilities capabilities) {
        int count = 0;
        for (Constructor<?> constructor : lyricResultClass.getDeclaredConstructors()) {
            String kind = lyricConstructorKind(constructor, sourceEnumClass);
            if (kind == null) {
                continue;
            }
            constructor.setAccessible(true);
            module.hook(constructor)
                    .setId(HOOK_ID_PREFIX
                            + simpleClassName(lyricResultClass.getName()) + "-" + kind)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> module.onPlayerLyricResultConstructed(
                            chain,
                            capabilities));
            count++;
        }
        return count;
    }

    private static String lyricConstructorKind(
            Constructor<?> constructor,
            Class<?> sourceEnumClass) {
        Class<?>[] types = constructor.getParameterTypes();
        if (types.length != 3
                || types[1] != String.class
                || types[0] != sourceEnumClass) {
            return null;
        }
        if (types[2] == String.class) {
            return "primary";
        }
        if (types[2] == int.class) {
            return "synthetic";
        }
        return null;
    }

    private static String simpleClassName(String className) {
        int index = className.lastIndexOf('.');
        return (index >= 0 ? className.substring(index + 1) : className).toLowerCase(Locale.ROOT);
    }
}
