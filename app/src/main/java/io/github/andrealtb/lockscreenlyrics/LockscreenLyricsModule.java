package io.github.andrealtb.lockscreenlyrics;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class LockscreenLyricsModule extends XposedModule {
    private static final String TAG = "LockscreenLyrics";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String LYRICS_RECYCLER_VIEW_CLASS =
            "com.oplus.systemui.plugins.shared.template.component.media.view.LyricsRecyclerView";
    private static final String OPLUS_LYRIC_INFO_KEY = "lyricInfo";
    private static final String OPLUS_RAW_LYRIC_INFO_KEY = "rawLyric";
    private static final String HOOK_ID_SET_METADATA = "lockscreen-lyrics-set-metadata";
    private static final String HOOK_ID_SYSTEMUI_LOAD_LYRIC = "oplus-word-load-lyric";
    private static final String HOOK_ID_SYSTEMUI_CURRENT_LYRIC = "oplus-word-current-lyric";
    private static final String HOOK_ID_BUNDLE_CURRENT_LYRIC = "oplus-word-bundle-current-lyric";
    private static final String HOOK_ID_SEEDLING_COMPUTER_POSITION = "oplus-word-seedling-position";
    private static final String HOOK_ID_TEXTVIEW_SET_TEXT_TWO_ARG = "oplus-word-textview-set-text-two-arg";
    private static final String HOOK_ID_TEXTVIEW_SET_TEXT_ONE_ARG = "oplus-word-textview-set-text-one-arg";
    private static final String HOOK_ID_TEXTVIEW_ON_DRAW = "oplus-word-textview-on-draw";
    private static final String HOOK_ID_CLASS_LOADER_LOAD_CLASS = "oplus-word-classloader-load-class";
    private static final String HOOK_ID_LYRICS_RECYCLER = "oplus-word-lyrics-recycler";
    private static final String HOOK_ID_SYSTEMUI_LOG_I = "oplus-lyric-ui-mode-log-i";
    private static final String HOOK_ID_SYSTEMUI_LOG_PRINTLN = "oplus-lyric-ui-mode-log-println";
    private static final String HOOK_ID_RUS_DEAL_END_TAG = "oplus-media-rus-deal-end-tag";
    private static final String HOOK_ID_RUS_SAVE_LIST_TO_SP = "oplus-media-rus-save-list";
    private static final String HOOK_ID_RUS_GET_WHITE_LIST = "oplus-media-rus-get-white-list";
    private static final String OPLUS_MEDIA_RUS_TAG_WHITELIST = "whitelist";
    private static final long LYRIC_CACHE_MAX_AGE_MS = 5 * 60 * 1000L;
    private static final long OPLUS_TAIL_SPACER_DELAY_MS = 8_000L;
    private static final long SCREEN_TIMEOUT_USER_ACTIVITY_INTERVAL_MS = 8_000L;
    private static final long SCREEN_TIMEOUT_VISIBLE_LYRIC_VIEW_MAX_AGE_MS = 30_000L;
    private static final Pattern LRC_TIME_TAG = Pattern.compile("\\[[0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?\\]");
    private static final Pattern ANY_LRC_TIME_TAG = Pattern.compile("[\\[<]([0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?)[\\]>]");
    private static final PlayerAdapter[] PLAYER_ADAPTERS = {
            new SaltPlayerAdapter()
    };

    private volatile CachedLyric cachedLyric;
    private volatile MediaSession lastSession;
    private volatile MediaMetadata lastMetadata;
    private volatile WordLyricModel currentWordLyricModel;
    private final ThreadLocal<CurrentLyricFrame> currentLyricFrame = new ThreadLocal<>();
    private volatile long lastTextViewSpanLogAt;
    private volatile long lastTextViewDrawLogAt;
    private volatile long lastRecyclerLogAt;
    private volatile long lastRecyclerScrollStabilizeLogAt;
    private volatile long lastActiveRefreshLogAt;
    private volatile long lastSeedlingPlaybackStateLogAt;
    private volatile long lastComputedPositionMs = -1L;
    private volatile long lastComputedPositionElapsedMs = -1L;
    private volatile boolean lastSystemUiPackageSupported;
    private volatile boolean lastPlaybackIsPlaying = true;
    private volatile int lastSystemUiPlaybackState = -1;
    private volatile int lastLoggedSystemUiPlaybackState = -100;
    private volatile String lastSystemUiSongName = "";
    private volatile String lastSystemUiArtistName = "";
    private volatile boolean systemUiHasOfficialLyric;
    private volatile boolean oplusMediaWhitelistHooksInstalled;
    private volatile boolean screenTimeoutReceiverRegistered;
    private volatile boolean systemUiLyricModeEnabled;
    private volatile boolean systemUiLyricModeKeepAwakeActive;
    private volatile long lastSystemUiLyricModeLogAt;
    private volatile long lastSystemUiLyricModeStateLogAt;
    private volatile long lastScreenTimeoutLogAt;
    private volatile long lastVisibleOfficialLyricTextViewAt;
    private volatile boolean screenTimeoutUserActivityPulsePosted;
    private volatile boolean screenTimeoutUserActivityFailureLogged;
    private volatile boolean screenTimeoutPausedByScreenOff;
    private volatile boolean screenTimeoutPausedByUserPresent;
    private BroadcastReceiver screenTimeoutReceiver;
    private PowerManager.WakeLock screenTimeoutWakeLock;
    private PowerManager screenTimeoutPowerManager;
    private volatile int lastLyricsRecyclerIndex = -1;
    private volatile boolean lyricsRecyclerHookInstalled;
    private final Object activeLyricTextViewsLock = new Object();
    private final ArrayList<WeakReference<TextView>> activeLyricTextViews = new ArrayList<>();
    private final Object lyricRootViewsLock = new Object();
    private final ArrayList<WeakReference<View>> lyricRootViews = new ArrayList<>();
    private volatile String activeLyricLine = "";
    private volatile long activeLyricLineTimeMs = -1L;
    private volatile boolean activeLyricUpdatePosted;
    private final ThreadLocal<Boolean> suppressTextViewHook = new ThreadLocal<>();
    private final OfficialLyricTextRenderer officialLyricTextRenderer = new OfficialLyricTextRenderer();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable screenTimeoutUserActivityPulse = new Runnable() {
        @Override
        public void run() {
            screenTimeoutUserActivityPulsePosted = false;
            if (!shouldHoldScreenTimeoutWakeLock()) {
                releaseScreenTimeoutWakeLock("conditions changed");
                return;
            }
            PowerManager powerManager = screenTimeoutPowerManager;
            if (powerManager != null) {
                pulseScreenTimeoutUserActivity(powerManager, false);
            }
            scheduleScreenTimeoutUserActivityPulse();
        }
    };

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        String processName = param.getProcessName();
        if (processName == null
                || param.isSystemServer()
                || !isSupportedProcess(processName)) {
            info("Skip process " + processName);
            detach();
            return;
        }
        info("Loaded in " + processName + ", API " + getApiVersion());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (SYSTEMUI_PACKAGE.equals(packageName)) {
            ClassLoader classLoader = param.getClassLoader();
            installOplusMediaWhitelistBypassHooks(classLoader);
            installSystemUiWordLyricHooks(classLoader);
            return;
        }

        PlayerAdapter adapter = findPlayerAdapter(packageName);
        if (adapter == null) {
            return;
        }
        installMediaMetadataHook();
        adapter.installLyricSourceHooks(this, param.getClassLoader());
    }

    private static boolean isSupportedProcess(String processName) {
        if (processName == null) {
            return false;
        }
        if (processName.startsWith(SYSTEMUI_PACKAGE)) {
            return true;
        }
        for (PlayerAdapter adapter : PLAYER_ADAPTERS) {
            if (processName.startsWith(adapter.packageName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSupportedPlayerPackage(String packageName) {
        return findPlayerAdapter(packageName) != null;
    }

    private static PlayerAdapter findPlayerAdapter(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        for (PlayerAdapter adapter : PLAYER_ADAPTERS) {
            if (adapter.packageName().equals(packageName)) {
                return adapter;
            }
        }
        return null;
    }

    private static String findSupportedPlayerPackageInLog(String message) {
        if (TextUtils.isEmpty(message)) {
            return "";
        }
        for (PlayerAdapter adapter : PLAYER_ADAPTERS) {
            String packageName = adapter.packageName();
            if (message.contains("pkg: " + packageName)) {
                return packageName;
            }
        }
        return "";
    }

    private void installOplusMediaWhitelistBypassHooks(ClassLoader classLoader) {
        if (oplusMediaWhitelistHooksInstalled) {
            return;
        }
        synchronized (this) {
            if (oplusMediaWhitelistHooksInstalled) {
                return;
            }
            try {
                Class<?> rusManagerClass =
                        classLoader.loadClass("com.oplus.systemui.media.seedling.rus.OplusMediaRusUpdateManager");

                Method dealEndTag = rusManagerClass.getDeclaredMethod(
                        "dealEndTag",
                        String.class,
                        Set.class,
                        Set.class,
                        List.class,
                        List.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class
                );
                dealEndTag.setAccessible(true);
                hook(dealEndTag)
                        .setId(HOOK_ID_RUS_DEAL_END_TAG)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaRusDealEndTag);

                Method saveListToSp = rusManagerClass.getDeclaredMethod(
                        "saveListToSP",
                        Context.class,
                        Set.class,
                        Set.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class
                );
                saveListToSp.setAccessible(true);
                hook(saveListToSp)
                        .setId(HOOK_ID_RUS_SAVE_LIST_TO_SP)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaRusSaveListToSp);

                Method getRusWhiteList = rusManagerClass.getDeclaredMethod("getRusWhiteList");
                getRusWhiteList.setAccessible(true);
                hook(getRusWhiteList)
                        .setId(HOOK_ID_RUS_GET_WHITE_LIST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaRusGetWhiteList);

                oplusMediaWhitelistHooksInstalled = true;
                info("Hooked OPlus media RUS whitelist bypass");
            } catch (Throwable t) {
                error("Failed to hook OPlus media RUS whitelist bypass", t);
            }
        }
    }

    private Object onOplusMediaRusDealEndTag(XposedInterface.Chain chain) throws Throwable {
        Object tag = chain.getArg(0);
        if (OPLUS_MEDIA_RUS_TAG_WHITELIST.equals(tag)) {
            info("Ignored OPlus media RUS whitelist update");
            return null;
        }
        return chain.proceed();
    }

    private Object onOplusMediaRusSaveListToSp(XposedInterface.Chain chain) throws Throwable {
        List<Object> args = chain.getArgs();
        if (args.size() < 2 || !(args.get(1) instanceof Set)) {
            return chain.proceed();
        }

        Object[] patchedArgs = args.toArray(new Object[0]);
        patchedArgs[1] = Collections.emptySet();
        return chain.proceed(patchedArgs);
    }

    private Object onOplusMediaRusGetWhiteList(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (result instanceof OplusMediaWhitelistBypassList) {
            return result;
        }
        if (result instanceof List) {
            return new OplusMediaWhitelistBypassList((List<?>) result);
        }
        return result;
    }

    private void installSystemUiWordLyricHooks(ClassLoader classLoader) {
        try {
            Class<?> managerClass = classLoader.loadClass("com.oplus.systemui.media.controls.pipeline.OplusMediaDataManagerExImpl");
            Class<?> lyricDataClass = classLoader.loadClass("com.android.systemui.media.controls.models.player.OplusMediaLyricData");
            Method loadLyricInBg = managerClass.getDeclaredMethod(
                    "loadLyricInBg",
                    String.class,
                    MediaMetadata.class,
                    String.class,
                    String.class,
                    String.class,
                    lyricDataClass
            );
            loadLyricInBg.setAccessible(true);
            hook(loadLyricInBg)
                    .setId(HOOK_ID_SYSTEMUI_LOAD_LYRIC)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiLoadLyricInBg);

            Class<?> seedlingLyricDataClass = classLoader.loadClass("com.oplus.systemui.seedlingservice.mediaControl.LyricData");
            Method getCurrentLyric = seedlingLyricDataClass.getDeclaredMethod("getCurrentLyric", long.class);
            getCurrentLyric.setAccessible(true);
            hook(getCurrentLyric)
                    .setId(HOOK_ID_SYSTEMUI_CURRENT_LYRIC)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiGetCurrentLyric);

            Method putCharSequence = Bundle.class.getDeclaredMethod("putCharSequence", String.class, CharSequence.class);
            hook(putCharSequence)
                    .setId(HOOK_ID_BUNDLE_CURRENT_LYRIC)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onBundlePutCharSequence);

            Class<?> seedlingMediaDataClass = classLoader.loadClass("com.oplus.systemui.seedlingservice.mediaControl.SeedlingMediaData");
            Method computerPosition = seedlingMediaDataClass.getDeclaredMethod("computerPosition");
            computerPosition.setAccessible(true);
            hook(computerPosition)
                    .setId(HOOK_ID_SEEDLING_COMPUTER_POSITION)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSeedlingComputerPosition);

            Method setTextTwoArg = TextView.class.getDeclaredMethod("setText", CharSequence.class, TextView.BufferType.class);
            hook(setTextTwoArg)
                    .setId(HOOK_ID_TEXTVIEW_SET_TEXT_TWO_ARG)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onTextViewSetText);

            Method setTextOneArg = TextView.class.getDeclaredMethod("setText", CharSequence.class);
            hook(setTextOneArg)
                    .setId(HOOK_ID_TEXTVIEW_SET_TEXT_ONE_ARG)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onTextViewSetText);

            Method onDraw = TextView.class.getDeclaredMethod("onDraw", Canvas.class);
            onDraw.setAccessible(true);
            hook(onDraw)
                    .setId(HOOK_ID_TEXTVIEW_ON_DRAW)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onTextViewOnDraw);

            installSystemUiLyricModeLogHooks();

            Method loadClass = ClassLoader.class.getDeclaredMethod("loadClass", String.class, boolean.class);
            loadClass.setAccessible(true);
            hook(loadClass)
                    .setId(HOOK_ID_CLASS_LOADER_LOAD_CLASS)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onClassLoaderLoadClass);

            tryInstallLyricsRecyclerViewHook(classLoader);
            ensureScreenTimeoutReceiver(currentApplicationContext());
            info("Hooked SystemUI official lyric TextView draw hooks");
        } catch (Throwable t) {
            error("Failed to hook SystemUI official lyric TextView draw hooks", t);
        }
    }

    private void installSystemUiLyricModeLogHooks() {
        try {
            Method logI = Log.class.getDeclaredMethod("i", String.class, String.class);
            hook(logI)
                    .setId(HOOK_ID_SYSTEMUI_LOG_I)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiLogMessage);
        } catch (Throwable t) {
            error("Failed to hook Log.i for lyricUiMode", t);
        }
        try {
            Method println = Log.class.getDeclaredMethod("println", int.class, String.class, String.class);
            hook(println)
                    .setId(HOOK_ID_SYSTEMUI_LOG_PRINTLN)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiLogMessage);
        } catch (Throwable t) {
            error("Failed to hook Log.println for lyricUiMode", t);
        }
    }

    private Object onSystemUiLogMessage(XposedInterface.Chain chain) throws Throwable {
        try {
            Object first = chain.getArg(0);
            String tag;
            String message;
            if (first instanceof String) {
                tag = (String) first;
                message = charSequenceToString((CharSequence) chain.getArg(1));
            } else {
                tag = charSequenceToString((CharSequence) chain.getArg(1));
                message = charSequenceToString((CharSequence) chain.getArg(2));
            }
            observeSystemUiLyricModeLog(tag, message);
        } catch (Throwable ignored) {
            // Logging hooks must stay invisible to SystemUI.
        }
        return chain.proceed();
    }

    private void observeSystemUiLyricModeLog(String tag, String message) {
        String playerPackage = findSupportedPlayerPackageInLog(message);
        if (!"PluginSeedling--Template".equals(tag)
                || TextUtils.isEmpty(message)
                || TextUtils.isEmpty(playerPackage)
                || !message.contains("lyricUiMode=")) {
            return;
        }

        boolean lyricModeOn = message.contains("lyricUiMode=true");
        boolean lyricModeOff = message.contains("lyricUiMode=false");
        if (!lyricModeOn && !lyricModeOff) {
            return;
        }
        if (!message.contains("lockImmersiveMode:")) {
            return;
        }

        systemUiLyricModeEnabled = lyricModeOn;
        boolean active = lyricModeOn
                && message.contains("lockImmersiveMode: true")
                && message.contains("containerView.isShown=true")
                && !message.contains("hasLyric=false");
        if (active) {
            screenTimeoutPausedByUserPresent = false;
        }
        boolean changed = systemUiLyricModeKeepAwakeActive != active;
        systemUiLyricModeKeepAwakeActive = active;
        lastSystemUiLyricModeLogAt = SystemClock.elapsedRealtime();
        if (changed) {
            maybeLogLyricModeKeepAwake(active);
        }
        updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private void maybeLogLyricModeKeepAwake(boolean active) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSystemUiLyricModeStateLogAt < 1_500L) {
            return;
        }
        lastSystemUiLyricModeStateLogAt = now;
        info("Lockscreen lyric UI keep-awake " + (active ? "ON" : "OFF"));
    }

    @SuppressLint("WrongConstant")
    private Object onSystemUiLoadLyricInBg(XposedInterface.Chain chain) throws Throwable {
        try {
            Object metadataArg = chain.getArg(1);
            if (metadataArg instanceof MediaMetadata) {
                String lyricInfo = ((MediaMetadata) metadataArg).getString(OPLUS_LYRIC_INFO_KEY);
                if (shouldAcceptSystemUiLyricInfo(chain, lyricInfo)) {
                    cacheSystemUiWordLyricModel(lyricInfo);
                }
            }
        } catch (Throwable t) {
            error("Failed to read rawLyric from SystemUI lyricInfo", t);
        }
        return chain.proceed();
    }

    private boolean shouldAcceptSystemUiLyricInfo(XposedInterface.Chain chain, String lyricInfo) {
        if (!TextUtils.isEmpty(lyricInfo) && lyricInfo.contains("\"" + OPLUS_RAW_LYRIC_INFO_KEY + "\"")) {
            return true;
        }
        if (lastSystemUiPackageSupported) {
            return true;
        }
        List<Object> args = chain.getArgs();
        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i);
            if (arg instanceof String && isSupportedPlayerPackage((String) arg)) {
                return true;
            }
        }
        return false;
    }

    private Object onSystemUiGetCurrentLyric(XposedInterface.Chain chain) throws Throwable {
        long position = (Long) chain.getArg(0);
        Object result = chain.proceed();
        if (result instanceof String && currentWordLyricModel != null) {
            currentLyricFrame.set(new CurrentLyricFrame(position, (String) result));
        }
        return result;
    }

    private Object onBundlePutCharSequence(XposedInterface.Chain chain) throws Throwable {
        Object key = chain.getArg(0);
        if (!"currentLyric".equals(key)) {
            return chain.proceed();
        }
        currentLyricFrame.remove();
        return chain.proceed();
    }

    private Object onSeedlingComputerPosition(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        long computedPosition = result instanceof Long ? (Long) result : -1L;
        try {
            Object mediaData = chain.getThisObject();
            String packageName = readStringField(mediaData, "packageName", "");
            if (!isSupportedPlayerPackage(packageName)) {
                lastSystemUiPackageSupported = false;
                lastSystemUiSongName = "";
                lastSystemUiArtistName = "";
                systemUiHasOfficialLyric = false;
                systemUiLyricModeKeepAwakeActive = false;
                lastVisibleOfficialLyricTextViewAt = 0L;
                if (computedPosition >= 0) {
                    lastComputedPositionMs = computedPosition;
                    lastComputedPositionElapsedMs = SystemClock.elapsedRealtime();
                }
                updateScreenTimeoutWakeLock(currentApplicationContext());
                return result;
            }
            lastSystemUiPackageSupported = true;
            lastSystemUiSongName = readCharSequenceField(mediaData, "song", "");
            lastSystemUiArtistName = readCharSequenceField(mediaData, "artist", "");

            int state = readIntField(mediaData, "state", -1);
            long storedPosition = readLongField(mediaData, "position", computedPosition);
            float speed = readFloatField(mediaData, "playbackSpeed", 1f);
            long nextPosition = storedPosition >= 0 ? storedPosition : computedPosition;
            if (state == 3 && speed > 0f && computedPosition >= 0) {
                lastPlaybackIsPlaying = true;
                lastComputedPositionMs = computedPosition;
                lastComputedPositionElapsedMs = SystemClock.elapsedRealtime();
            } else if (state >= 0) {
                lastPlaybackIsPlaying = false;
                lastComputedPositionMs = nextPosition;
                lastComputedPositionElapsedMs = -1L;
            } else if (computedPosition >= 0) {
                lastComputedPositionMs = computedPosition;
                lastComputedPositionElapsedMs = SystemClock.elapsedRealtime();
            }
            lastSystemUiPlaybackState = state;
            maybeLogSeedlingPlaybackState(state, nextPosition, computedPosition, speed);
            updateScreenTimeoutWakeLock(currentApplicationContext());
        } catch (Throwable t) {
            error("Failed to read SeedlingMediaData playback state", t);
            if (computedPosition >= 0) {
                lastComputedPositionMs = computedPosition;
                lastComputedPositionElapsedMs = SystemClock.elapsedRealtime();
            }
        }
        return result;
    }

    private Object onTextViewOnDraw(XposedInterface.Chain chain) throws Throwable {
        Object thisObject = chain.getThisObject();
        Object canvasArg = chain.getArg(0);
        if (!(thisObject instanceof TextView) || !(canvasArg instanceof Canvas)) {
            return chain.proceed();
        }

        TextView textView = (TextView) thisObject;
        try {
            DrawFrame frame = findOfficialLyricDrawFrame(textView);
            if (frame == null) {
                return chain.proceed();
            }
            if (frame.skipOriginal) {
                collapseSkippedOfficialLyricTextView(textView);
                return null;
            }

            prepareOfficialLyricTextView(textView);
            officialLyricTextRenderer.draw((Canvas) canvasArg, textView, frame);
            maybeLogTextViewDraw(frame, textView);
            return null;
        } catch (Throwable t) {
            error("Failed to custom-draw official lyric TextView", t);
            return chain.proceed();
        }
    }


    private static String charSequenceToString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private Object onTextViewSetText(XposedInterface.Chain chain) throws Throwable {
        if (Boolean.TRUE.equals(suppressTextViewHook.get())) {
            return chain.proceed();
        }

        Object textArg = chain.getArg(0);
        if (!(textArg instanceof CharSequence)) {
            return chain.proceed();
        }

        Object result = null;
        boolean proceeded = false;
        try {
            WordLyricModel model = currentWordLyricModel;
            if (model == null) {
                return chain.proceed();
            }

            String text = textArg.toString();
            if (TextUtils.isEmpty(text) || text.length() > 240) {
                return chain.proceed();
            }

            Object view = chain.getThisObject();
            if (!(view instanceof TextView) || !isInLyricsRecyclerView((TextView) view)) {
                return chain.proceed();
            }
            TextView textView = (TextView) view;
            String normalizedText = normalizeLine(text);
            long position = estimatePlaybackPositionMillis();
            LyricTextMatch match = findLyricTextMatch(model, textView, normalizedText, position);

            if (match.translationLine != null && match.line == null) {
                collapseSkippedOfficialLyricTextView(textView);
                result = chain.proceed();
                proceeded = true;
                return result;
            }

            WordLine line = match.line;
            if (line != null) {
                prepareOfficialLyricTextView(textView);
                officialLyricTextRenderer.applySlotHeight(textView, model, line);
            }

            result = chain.proceed();
            proceeded = true;

            if (line != null) {
                WordLine activeLine = model.findActiveLine(position);
                if (activeLine != null
                        && activeLine.timeMillis == line.timeMillis
                        && normalizeLine(activeLine.text).equals(normalizeLine(line.text))) {
                    rememberActiveLyricTextView(view, line);
                    maybeLogTextViewSpan(position, line.text, view);
                }
            }
            return result;
        } catch (Throwable t) {
            error("Failed to observe active lyric TextView", t);
            if (proceeded) {
                return result;
            }
            return chain.proceed();
        }
    }

    private Object onClassLoaderLoadClass(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object name = chain.getArg(0);
        if (LYRICS_RECYCLER_VIEW_CLASS.equals(name) && result instanceof Class<?>) {
            tryInstallLyricsRecyclerViewHook((Class<?>) result);
        }
        return result;
    }

    private Object onLyricsRecyclerSetCurrentLyric(XposedInterface.Chain chain) throws Throwable {
        Object recycler = chain.getThisObject();
        int targetIndex = -1;
        try {
            Object index = chain.getArg(0);
            if (index instanceof Integer) {
                targetIndex = (Integer) index;
                lastLyricsRecyclerIndex = targetIndex;
                WordLyricModel model = currentWordLyricModel;
                if (model != null) {
                    WordLine line = model.lineAt(lastLyricsRecyclerIndex);
                    if (line != null) {
                        activeLyricLine = normalizeLine(line.text);
                        activeLyricLineTimeMs = line.timeMillis;
                        if (lastComputedPositionMs < 0) {
                            lastComputedPositionMs = line.timeMillis;
                            lastComputedPositionElapsedMs = SystemClock.elapsedRealtime();
                        }
                        TextView candidate = firstActiveLyricTextView();
                        if (candidate != null) {
                            scheduleActiveLyricRefresh(candidate, 33L);
                        }
                    }
                }
                maybeLogRecyclerIndex(lastLyricsRecyclerIndex);
            }
            if (recycler instanceof View) {
                stabilizeLyricsRecyclerScroll((View) recycler, "before-setCurrentLyric");
                applyVisibleLyricBlockHeights((View) recycler);
            }
        } catch (Throwable t) {
            error("Failed while reading LyricsRecyclerView#setCurrentLyric", t);
        }
        Object result = chain.proceed();
        if (recycler instanceof View) {
            stabilizeLyricsRecyclerScroll((View) recycler, "after-setCurrentLyric");
        }
        return result;
    }

    private void stabilizeLyricsRecyclerScroll(View recycler, String reason) {
        if (recycler == null) {
            return;
        }
        tryInvokeOneArgByName(recycler, "setItemAnimator", null);
        maybeLogRecyclerScrollStabilize(recycler, reason);
    }

    private void applyVisibleLyricBlockHeights(View root) {
        WordLyricModel model = currentWordLyricModel;
        if (model == null || root == null || !root.isAttachedToWindow()) {
            return;
        }
        applyVisibleLyricBlockHeights(root, model, new int[]{0});
    }

    private void applyVisibleLyricBlockHeights(View view, WordLyricModel model, int[] visited) {
        if (view == null || visited[0]++ > 300 || view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            if (text != null && text.length() > 0 && text.length() <= 240 && isInLyricsRecyclerView(textView)) {
                LyricTextMatch match = findLyricTextMatch(
                        model,
                        textView,
                        normalizeLine(text.toString()),
                        estimatePlaybackPositionMillis());
                if (match.translationLine != null && match.line == null) {
                    collapseSkippedOfficialLyricTextView(textView);
                } else if (match.line != null) {
                    prepareOfficialLyricTextView(textView);
                    officialLyricTextRenderer.applySlotHeight(textView, model, match.line);
                }
            }
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            applyVisibleLyricBlockHeights(viewGroup.getChildAt(i), model, visited);
        }
    }

    private void tryInstallLyricsRecyclerViewHook(ClassLoader classLoader) {
        if (lyricsRecyclerHookInstalled) {
            return;
        }
        try {
            tryInstallLyricsRecyclerViewHook(classLoader.loadClass(LYRICS_RECYCLER_VIEW_CLASS));
        } catch (Throwable ignored) {
            // The Seedling plugin class is loaded lazily; ClassLoader.loadClass hook will catch it later.
        }
    }

    private synchronized void tryInstallLyricsRecyclerViewHook(Class<?> lyricsRecyclerViewClass) {
        if (lyricsRecyclerHookInstalled || lyricsRecyclerViewClass == null) {
            return;
        }
        try {
            int hooked = 0;
            for (Method method : lyricsRecyclerViewClass.getDeclaredMethods()) {
                if (!"setCurrentLyric".equals(method.getName())
                        || method.getParameterTypes().length == 0
                        || method.getParameterTypes()[0] != int.class) {
                    continue;
                }
                method.setAccessible(true);
                hook(method)
                        .setId(HOOK_ID_LYRICS_RECYCLER + "-" + method.getParameterTypes().length)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onLyricsRecyclerSetCurrentLyric);
                hooked++;
            }
            if (hooked > 0) {
                lyricsRecyclerHookInstalled = true;
                info("Hooked LyricsRecyclerView#setCurrentLyric, methods=" + hooked);
            }
        } catch (Throwable t) {
            error("Failed to hook LyricsRecyclerView#setCurrentLyric", t);
        }
    }

    private void rememberActiveLyricTextView(Object view, WordLine line) {
        if (!(view instanceof TextView) || line == null || TextUtils.isEmpty(line.text)) {
            return;
        }
        TextView textView = (TextView) view;
        activeLyricLine = normalizeLine(line.text);
        activeLyricLineTimeMs = line.timeMillis;
        rememberActiveTextViewReference(textView);
        scheduleActiveLyricRefresh(textView, 80L);
    }

    private LyricTextMatch findLyricTextMatch(
            WordLyricModel model,
            TextView textView,
            String normalizedText,
            long position) {
        if (model == null || textView == null || TextUtils.isEmpty(normalizedText)) {
            return LyricTextMatch.EMPTY;
        }

        int adapterPosition = findLyricsRecyclerAdapterPosition(textView);
        int anchorIndex = adapterPosition >= 0
                ? adapterPosition
                : Math.min(lastLyricsRecyclerIndex, model.lines.size() - 1);
        WordLine indexedLine = model.lineAt(adapterPosition);
        WordLine line = null;
        WordLine translationLine = null;
        if (indexedLine != null) {
            if (matchesLyricText(indexedLine.text, normalizedText)) {
                line = indexedLine;
            } else if (!TextUtils.isEmpty(indexedLine.translation)
                    && normalizeLine(indexedLine.translation).equals(normalizedText)) {
                translationLine = indexedLine;
            }
        }
        if (line == null && translationLine == null && anchorIndex >= 0) {
            line = model.findLineByTextNearIndex(normalizedText, anchorIndex, 4, false);
            translationLine = model.findLineByTranslationNearIndex(normalizedText, anchorIndex, 4);
        }
        if (line == null && translationLine == null) {
            line = model.findLineByText(normalizedText, position);
            translationLine = model.findLineByTranslation(normalizedText, position);
        }
        return line == null && translationLine == null
                ? LyricTextMatch.EMPTY
                : new LyricTextMatch(line, translationLine);
    }

    private DrawFrame findOfficialLyricDrawFrame(TextView textView) {
        if (textView != null) {
            ensureScreenTimeoutReceiver(textView.getContext());
            noteVisibleLockscreenLyricTextView(textView);
        }
        WordLyricModel model = currentWordLyricModel;
        if (model == null || textView == null || !isEffectivelyVisible(textView)) {
            return null;
        }
        if (!isInLyricsRecyclerView(textView)) {
            return null;
        }

        CharSequence currentText = textView.getText();
        if (currentText == null || currentText.length() == 0 || currentText.length() > 240) {
            return null;
        }

        String normalizedText = normalizeLine(currentText.toString());
        if (TextUtils.isEmpty(normalizedText)) {
            return null;
        }

        long position = estimatePlaybackPositionMillis();
        WordLine activeLine = model.findActiveLine(position);
        int adapterPosition = findLyricsRecyclerAdapterPosition(textView);
        int anchorIndex = adapterPosition >= 0
                ? adapterPosition
                : Math.min(lastLyricsRecyclerIndex, model.lines.size() - 1);
        WordLine indexedLine = model.lineAt(adapterPosition);
        WordLine line = null;
        WordLine translationLine = null;
        if (indexedLine != null) {
            if (matchesLyricText(indexedLine.text, normalizedText)) {
                line = indexedLine;
            } else if (!TextUtils.isEmpty(indexedLine.translation)
                    && normalizeLine(indexedLine.translation).equals(normalizedText)) {
                translationLine = indexedLine;
            }
        }
        if (line == null && translationLine == null && anchorIndex >= 0) {
            line = model.findLineByTextNearIndex(normalizedText, anchorIndex, 4, false);
            translationLine = model.findLineByTranslationNearIndex(normalizedText, anchorIndex, 4);
        }
        if (line == null && translationLine == null) {
            line = activeLine != null && matchesLyricText(activeLine.text, normalizedText)
                    ? activeLine
                    : model.findLineByText(normalizedText, position);
            translationLine = activeLine != null
                    && !TextUtils.isEmpty(activeLine.translation)
                    && normalizeLine(activeLine.translation).equals(normalizedText)
                    ? activeLine
                    : model.findLineByTranslation(normalizedText, position);
        }
        boolean knownLyricTextView = isRememberedActiveTextView(textView)
                || line != null
                || translationLine != null;
        if (!knownLyricTextView) {
            return null;
        }
        if (line == null && translationLine != null) {
            int lineIndex = model.indexOfLine(translationLine);
            int activeIndex = activeLine == null ? -1 : model.indexOfLine(activeLine);
            boolean focused = activeIndex >= 0 && lineIndex == activeIndex + 1;
            return new DrawFrame(model, translationLine, lineIndex, activeIndex, position, false, focused, true);
        }
        if (line == null && normalizedText.equals(activeLyricLine)) {
            line = model.findLineAtTime(activeLyricLineTimeMs);
        }
        if (line == null) {
            return null;
        }
        if (TextUtils.isEmpty(line.translation)) {
            int lineIndex = model.indexOfLine(line);
            WordLine translatedLine = model.findLineByTextNearIndex(
                    normalizedText,
                    lineIndex >= 0 ? lineIndex : anchorIndex,
                    6,
                    true);
            if (translatedLine != null) {
                line = translatedLine;
            }
        }
        if (position < 0) {
            position = line.timeMillis;
        }

        boolean active = activeLine != null
                && activeLine.timeMillis == line.timeMillis
                && normalizeLine(activeLine.text).equals(normalizeLine(line.text));
        rememberActiveTextViewReference(textView);
        if (active) {
            activeLyricLine = normalizeLine(line.text);
            activeLyricLineTimeMs = line.timeMillis;
            scheduleActiveLyricRefresh(textView, lastPlaybackIsPlaying ? 33L : 500L);
        }
        noteVisibleLockscreenLyricTextView(textView);
        int lineIndex = model.indexOfLine(line);
        int activeIndex = activeLine == null ? -1 : model.indexOfLine(activeLine);
        boolean focused = active || (activeIndex >= 0 && lineIndex == activeIndex + 1);
        return new DrawFrame(model, line, lineIndex, activeIndex, position, active, focused, false);
    }

    private void noteVisibleLockscreenLyricTextView(TextView textView) {
        if (textView == null
                || !isInLyricsRecyclerView(textView)
                || !isEffectivelyVisible(textView)
                || !hasUsableLyricText(textView)) {
            return;
        }
        lastVisibleOfficialLyricTextViewAt = SystemClock.elapsedRealtime();
        if (screenTimeoutPausedByScreenOff
                || screenTimeoutPausedByUserPresent
                || !lastSystemUiPackageSupported
                || !lastPlaybackIsPlaying
                || !systemUiLyricModeEnabled) {
            return;
        }
        if (!systemUiLyricModeKeepAwakeActive) {
            systemUiLyricModeKeepAwakeActive = true;
            lastSystemUiLyricModeLogAt = SystemClock.elapsedRealtime();
            maybeLogLyricModeKeepAwake(true);
            maybeLogScreenTimeout("Inferred lockscreen lyric UI keep-awake from visible lyric view", false);
        }
        updateScreenTimeoutWakeLock(textView.getContext());
    }

    private static boolean hasUsableLyricText(TextView textView) {
        CharSequence text = textView == null ? null : textView.getText();
        if (text == null || text.length() == 0 || text.length() > 240) {
            return false;
        }
        return !TextUtils.isEmpty(normalizeLine(text.toString()));
    }

    private static int findLyricsRecyclerAdapterPosition(TextView textView) {
        if (textView == null) {
            return -1;
        }
        View child = textView;
        Object parent = child.getParent();
        for (int depth = 0; depth < 12 && parent instanceof View; depth++) {
            View parentView = (View) parent;
            if (parentView.getClass().getName().contains("LyricsRecyclerView")) {
                return readRecyclerChildPosition(parentView, child);
            }
            child = parentView;
            parent = child.getParent();
        }
        return -1;
    }

    private static int readRecyclerChildPosition(View recyclerView, View child) {
        if (recyclerView == null || child == null) {
            return -1;
        }
        for (String methodName : new String[]{
                "getChildBindingAdapterPosition",
                "getChildAdapterPosition",
                "getChildLayoutPosition"
        }) {
            try {
                Method method = findMethod(recyclerView.getClass(), methodName, View.class);
                Object result = method.invoke(recyclerView, child);
                if (result instanceof Number) {
                    int position = ((Number) result).intValue();
                    if (position >= 0) {
                        return position;
                    }
                }
            } catch (Throwable ignored) {
                // Some plugin RecyclerView builds expose only one of these helpers.
            }
        }
        return -1;
    }

    private static View findLyricsRecyclerItemView(TextView textView) {
        if (textView == null) {
            return null;
        }
        View child = textView;
        Object parent = child.getParent();
        for (int depth = 0; depth < 12 && parent instanceof View; depth++) {
            View parentView = (View) parent;
            if (parentView.getClass().getName().contains("LyricsRecyclerView")) {
                return child;
            }
            child = parentView;
            parent = child.getParent();
        }
        return textView;
    }

    private static void collapseSkippedOfficialLyricTextView(TextView textView) {
        if (textView == null) {
            return;
        }
        textView.setIncludeFontPadding(false);
        textView.setMinHeight(0);
        textView.setMinimumHeight(0);
        setOfficialLyricSlotHeight(textView, 1);
    }

    private static void prepareOfficialLyricTextView(TextView textView) {
        if (textView == null) {
            return;
        }
        textView.setIncludeFontPadding(false);
        textView.setMinHeight(0);
        textView.setMinimumHeight(0);
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null && itemView != textView) {
            itemView.setMinimumHeight(0);
        }
    }

    private static void clearFocusedOfficialLyricViewEffects(TextView textView) {
        if (textView == null) {
            return;
        }
        clearViewVisualEffects(textView);
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null && itemView != textView) {
            clearViewVisualEffects(itemView);
        }
    }

    private static void clearViewVisualEffects(View view) {
        if (view == null) {
            return;
        }
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setLayerType(View.LAYER_TYPE_NONE, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null);
        }
        tryInvokeOneArgByName(view, "setViewBlur", false);
        tryInvokeOneArgByName(view, "setBlur", false);
    }

    private static void setOfficialLyricSlotHeight(TextView textView, int height) {
        int safeHeight = Math.max(1, height);
        setViewHeight(textView, safeHeight);
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null && itemView != textView) {
            itemView.setMinimumHeight(0);
            setViewHeight(itemView, safeHeight);
        }
    }

    private static void setViewHeight(View view, int height) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height == height) {
            return;
        }
        params.height = height;
        view.setLayoutParams(params);
        view.requestLayout();
    }

    private boolean isRememberedActiveTextView(TextView target) {
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView textView = activeLyricTextViews.get(i).get();
                if (textView == null) {
                    activeLyricTextViews.remove(i);
                } else if (textView == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private void rememberActiveTextViewReference(TextView textView) {
        rememberLyricRootView(textView);
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView existing = activeLyricTextViews.get(i).get();
                if (existing == null) {
                    activeLyricTextViews.remove(i);
                } else if (existing == textView) {
                    return;
                }
            }
            if (activeLyricTextViews.size() >= 8) {
                activeLyricTextViews.remove(0);
            }
            activeLyricTextViews.add(new WeakReference<>(textView));
        }
    }

    private void rememberLyricRootView(TextView textView) {
        if (textView == null) {
            return;
        }
        View root = textView.getRootView();
        if (root == null) {
            return;
        }
        synchronized (lyricRootViewsLock) {
            for (int i = lyricRootViews.size() - 1; i >= 0; i--) {
                View existing = lyricRootViews.get(i).get();
                if (existing == null) {
                    lyricRootViews.remove(i);
                } else if (existing == root) {
                    return;
                }
            }
            if (lyricRootViews.size() >= 4) {
                lyricRootViews.remove(0);
            }
            lyricRootViews.add(new WeakReference<>(root));
        }
    }

    private void scheduleActiveLyricRefresh(TextView textView, long delayMillis) {
        if (activeLyricUpdatePosted || textView == null) {
            return;
        }
        activeLyricUpdatePosted = true;
        mainHandler.postDelayed(() -> {
            activeLyricUpdatePosted = false;
            refreshActiveLyricTextView();
        }, Math.max(16L, Math.min(delayMillis, 600L)));
    }

    private void refreshActiveLyricTextView() {
        WordLyricModel model = currentWordLyricModel;
        if (model == null) {
            updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }

        ArrayList<TextView> candidates = snapshotActiveTextViews();
        boolean hasSearchRoot = !snapshotLyricRootViews().isEmpty();
        if (candidates.isEmpty() && !hasSearchRoot) {
            return;
        }

        long position = estimatePlaybackPositionMillis();
        WordLine line = model.findActiveLine(position);
        if (line == null) {
            line = model.findLineAtTime(activeLyricLineTimeMs);
        }
        if (line == null) {
            return;
        }
        activeLyricLine = normalizeLine(line.text);
        activeLyricLineTimeMs = line.timeMillis;
        mergeVisibleLyricTextViewsFromRoots(candidates, model, activeLyricLine);

        int attached = 0;
        int visible = 0;
        int updated = 0;
        for (TextView textView : candidates) {
            if (!textView.isAttachedToWindow()) {
                continue;
            }
            attached++;
            if (!isEffectivelyVisible(textView)) {
                continue;
            }
            visible++;
            CharSequence currentText = textView.getText();
            if (currentText == null) {
                continue;
            }
            String normalized = normalizeLine(currentText.toString());
            if (!normalized.equals(activeLyricLine) && !model.hasRenderableText(normalized)) {
                continue;
            }

            textView.invalidate();
            textView.postInvalidateOnAnimation();
            updated++;
        }

        maybeLogActiveRefresh(position, line.text, candidates.size(), attached, visible, updated);
        long nextDelay = lastPlaybackIsPlaying ? 33L : 500L;
        TextView nextAnchor = candidates.isEmpty() ? firstActiveLyricTextView() : candidates.get(0);
        if (nextAnchor != null) {
            scheduleActiveLyricRefresh(nextAnchor, nextDelay);
        }
    }

    private ArrayList<TextView> snapshotActiveTextViews() {
        ArrayList<TextView> result = new ArrayList<>();
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView textView = activeLyricTextViews.get(i).get();
                if (textView == null) {
                    activeLyricTextViews.remove(i);
                } else {
                    result.add(textView);
                }
            }
        }
        return result;
    }

    private ArrayList<View> snapshotLyricRootViews() {
        ArrayList<View> result = new ArrayList<>();
        synchronized (lyricRootViewsLock) {
            for (int i = lyricRootViews.size() - 1; i >= 0; i--) {
                View root = lyricRootViews.get(i).get();
                if (root == null) {
                    lyricRootViews.remove(i);
                } else {
                    result.add(root);
                }
            }
        }
        return result;
    }

    private TextView firstActiveLyricTextView() {
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView textView = activeLyricTextViews.get(i).get();
                if (textView == null) {
                    activeLyricTextViews.remove(i);
                } else if (isInLyricsRecyclerView(textView)) {
                    return textView;
                }
            }
        }
        return null;
    }

    private void mergeVisibleLyricTextViewsFromRoots(
            ArrayList<TextView> candidates, WordLyricModel model, String normalizedLine) {
        if (model == null) {
            return;
        }

        ArrayList<View> roots = new ArrayList<>();
        for (TextView textView : candidates) {
            View root = textView.getRootView();
            if (root != null && root.isAttachedToWindow() && !containsView(roots, root)) {
                roots.add(root);
            }
        }
        for (View root : snapshotLyricRootViews()) {
            if (root != null && root.isAttachedToWindow() && !containsView(roots, root)) {
                roots.add(root);
            }
        }

        for (View root : roots) {
            collectVisibleLyricTextViews(root, model, normalizedLine, candidates, new int[]{0});
        }
    }

    private void collectVisibleLyricTextViews(
            View view, WordLyricModel model, String normalizedLine, ArrayList<TextView> candidates, int[] visited) {
        if (view == null || visited[0]++ > 1200 || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0.05f) {
            return;
        }

        if (view instanceof TextView && isEffectivelyVisible(view)) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            String normalized = text == null ? "" : normalizeLine(text.toString());
            if (text != null
                    && text.length() <= 240
                    && isInLyricsRecyclerView(textView)
                    && (normalized.equals(normalizedLine) || model.hasRenderableText(normalized))
                    && !containsTextView(candidates, textView)) {
                candidates.add(textView);
                rememberActiveTextViewReference(textView);
            }
        }

        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            collectVisibleLyricTextViews(viewGroup.getChildAt(i), model, normalizedLine, candidates, visited);
        }
    }

    private static boolean isEffectivelyVisible(View view) {
        if (view == null
                || !view.isAttachedToWindow()
                || view.getVisibility() != View.VISIBLE
                || view.getAlpha() <= 0.01f
                || view.getWidth() <= 0
                || view.getHeight() <= 0) {
            return false;
        }

        Object parent = view.getParent();
        for (int depth = 0; depth < 16 && parent instanceof View; depth++) {
            View parentView = (View) parent;
            if (parentView.getVisibility() != View.VISIBLE
                    || parentView.getAlpha() <= 0.01f
                    || parentView.getWidth() <= 0
                    || parentView.getHeight() <= 0) {
                return false;
            }
            parent = parentView.getParent();
        }
        return true;
    }

    private static boolean isInLyricsRecyclerView(TextView textView) {
        if (textView == null) {
            return false;
        }
        Object parent = textView.getParent();
        for (int depth = 0; depth < 12 && parent instanceof View; depth++) {
            View parentView = (View) parent;
            String className = parentView.getClass().getName();
            if (className.contains("LyricsRecyclerView")) {
                return true;
            }
            parent = parentView.getParent();
        }
        return false;
    }

    private static boolean containsTextView(ArrayList<TextView> textViews, TextView target) {
        for (TextView textView : textViews) {
            if (textView == target) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsView(ArrayList<View> views, View target) {
        for (View view : views) {
            if (view == target) {
                return true;
            }
        }
        return false;
    }

    private static String describeViewForLog(View view) {
        if (view == null) {
            return "null";
        }
        return view.getClass().getName()
                + "{attached=" + view.isAttachedToWindow()
                + ", visibility=" + view.getVisibility()
                + ", alpha=" + view.getAlpha()
                + ", size=" + view.getWidth() + "x" + view.getHeight()
                + ", pos=" + view.getX() + "," + view.getY()
                + "}";
    }

    private static void tryInvokeOneArgByName(Object target, String methodName, Object arg) {
        if (target == null || TextUtils.isEmpty(methodName)) {
            return;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            Method[] methods = current.getDeclaredMethods();
            for (Method method : methods) {
                if (!methodName.equals(method.getName()) || method.getParameterTypes().length != 1) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(target, arg);
                    return;
                } catch (Throwable ignored) {
                    // Try another overload or superclass implementation.
                }
            }
            current = current.getSuperclass();
        }
    }

    private static Method findMethod(
            Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private static String readStringField(Object target, String fieldName, String defaultValue) {
        Object value = readFieldValue(target, fieldName);
        return value instanceof String ? (String) value : defaultValue;
    }

    private static String readCharSequenceField(Object target, String fieldName, String defaultValue) {
        Object value = readFieldValue(target, fieldName);
        return value instanceof CharSequence ? value.toString() : defaultValue;
    }

    private static int readIntField(Object target, String fieldName, int defaultValue) {
        Object value = readFieldValue(target, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static long readLongField(Object target, String fieldName, long defaultValue) {
        Object value = readFieldValue(target, fieldName);
        return value instanceof Number ? ((Number) value).longValue() : defaultValue;
    }

    private static float readFloatField(Object target, String fieldName, float defaultValue) {
        Object value = readFieldValue(target, fieldName);
        return value instanceof Number ? ((Number) value).floatValue() : defaultValue;
    }

    private static Object readFieldValue(Object target, String fieldName) {
        if (target == null || TextUtils.isEmpty(fieldName)) {
            return null;
        }

        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Context currentApplicationContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThreadClass.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            if (application instanceof Context) {
                Context context = ((Context) application).getApplicationContext();
                return context == null ? (Context) application : context;
            }
        } catch (Throwable ignored) {
            // Some early package-ready callbacks run before ActivityThread exposes the app context.
        }
        return null;
    }

    private void ensureScreenTimeoutReceiver(Context context) {
        if (context == null || screenTimeoutReceiverRegistered) {
            return;
        }
        synchronized (this) {
            if (screenTimeoutReceiverRegistered) {
                return;
            }
            Context appContext = context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    String action = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        screenTimeoutPausedByScreenOff = true;
                        screenTimeoutPausedByUserPresent = false;
                        releaseScreenTimeoutWakeLock("screen off");
                        maybeLogScreenTimeout("Paused screen timeout keep-awake after screen off", true);
                        return;
                    }
                    if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        if (screenTimeoutPausedByScreenOff) {
                            maybeLogScreenTimeout("Resumed screen timeout keep-awake after screen on", true);
                        }
                        screenTimeoutPausedByScreenOff = false;
                        screenTimeoutPausedByUserPresent = false;
                        updateScreenTimeoutWakeLock(receiverContext);
                        return;
                    }
                    if (Intent.ACTION_USER_PRESENT.equals(action)) {
                        screenTimeoutPausedByUserPresent = true;
                        systemUiLyricModeKeepAwakeActive = false;
                        releaseScreenTimeoutWakeLock("user present");
                        maybeLogScreenTimeout("Paused screen timeout keep-awake after user present", true);
                        return;
                    }
                }
            };
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_USER_PRESENT);
                if (Build.VERSION.SDK_INT >= 33) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    appContext.registerReceiver(receiver, filter);
                }
                screenTimeoutReceiver = receiver;
                screenTimeoutReceiverRegistered = true;
                info("Registered SystemUI screen timeout receiver");
            } catch (Throwable t) {
                error("Failed to register SystemUI screen timeout receiver", t);
            }
        }
    }

    private void updateScreenTimeoutWakeLock(Context context) {
        if (!shouldHoldScreenTimeoutWakeLock()) {
            maybeLogScreenTimeoutSkip();
            releaseScreenTimeoutWakeLock("conditions changed");
            return;
        }
        if (context == null) {
            return;
        }
        ensureScreenTimeoutReceiver(context);
        try {
            Context appContext = context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            PowerManager powerManager = screenTimeoutPowerManager;
            if (powerManager == null) {
                powerManager =
                        (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
                if (powerManager == null) {
                    return;
                }
                screenTimeoutPowerManager = powerManager;
            }
            if (!powerManager.isInteractive()) {
                releaseScreenTimeoutWakeLock("screen not interactive");
                return;
            }
            PowerManager.WakeLock wakeLock = screenTimeoutWakeLock;
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_DIM_WAKE_LOCK,
                        TAG + ":ScreenTimeout");
                wakeLock.setReferenceCounted(false);
                screenTimeoutWakeLock = wakeLock;
            }
            boolean wasHeld = wakeLock.isHeld();
            if (!wasHeld) {
                wakeLock.acquire();
            }
            if (!wasHeld) {
                maybeLogScreenTimeout("Acquired screen timeout wake lock without timeout", true);
                pulseScreenTimeoutUserActivity(powerManager, true);
            }
            scheduleScreenTimeoutUserActivityPulse();
        } catch (Throwable t) {
            error("Failed to update screen timeout wake lock", t);
        }
    }

    private boolean shouldHoldScreenTimeoutWakeLock() {
        return systemUiLyricModeKeepAwakeActive
                && !screenTimeoutPausedByScreenOff
                && !screenTimeoutPausedByUserPresent
                && lastSystemUiPackageSupported
                && lastPlaybackIsPlaying
                && hasScreenTimeoutLyricEvidence()
                && isScreenInteractiveForWakeLock();
    }

    private boolean hasScreenTimeoutLyricEvidence() {
        return currentWordLyricModel != null
                || systemUiHasOfficialLyric
                || hasRecentVisibleOfficialLyricTextView();
    }

    private boolean hasRecentVisibleOfficialLyricTextView() {
        long lastVisibleAt = lastVisibleOfficialLyricTextViewAt;
        if (lastVisibleAt <= 0L) {
            return false;
        }
        long age = SystemClock.elapsedRealtime() - lastVisibleAt;
        return age >= 0L && age <= SCREEN_TIMEOUT_VISIBLE_LYRIC_VIEW_MAX_AGE_MS;
    }

    private boolean isScreenInteractiveForWakeLock() {
        PowerManager powerManager = screenTimeoutPowerManager;
        return powerManager == null || powerManager.isInteractive();
    }

    private void maybeLogScreenTimeoutSkip() {
        if (!systemUiLyricModeKeepAwakeActive) {
            return;
        }
        maybeLogScreenTimeout("Skip screen timeout wake lock: screenOff=" + screenTimeoutPausedByScreenOff
                + ", userPresent=" + screenTimeoutPausedByUserPresent
                + ", supportedPlayer=" + lastSystemUiPackageSupported
                + ", playing=" + lastPlaybackIsPlaying
                + ", hasModel=" + (currentWordLyricModel != null)
                + ", hasOfficialLyric=" + systemUiHasOfficialLyric
                + ", recentOfficialView=" + hasRecentVisibleOfficialLyricTextView()
                + ", interactive=" + isScreenInteractiveForWakeLock(), false);
    }

    private void releaseScreenTimeoutWakeLock(String reason) {
        stopScreenTimeoutUserActivityPulse();
        PowerManager.WakeLock wakeLock = screenTimeoutWakeLock;
        if (wakeLock == null) {
            return;
        }
        try {
            if (wakeLock.isHeld()) {
                wakeLock.release();
                maybeLogScreenTimeout("Released screen timeout wake lock: " + reason, true);
            }
        } catch (Throwable t) {
            error("Failed to release screen timeout wake lock", t);
        }
    }

    private void scheduleScreenTimeoutUserActivityPulse() {
        if (screenTimeoutUserActivityPulsePosted) {
            return;
        }
        screenTimeoutUserActivityPulsePosted = true;
        mainHandler.postDelayed(
                screenTimeoutUserActivityPulse,
                SCREEN_TIMEOUT_USER_ACTIVITY_INTERVAL_MS);
    }

    private void stopScreenTimeoutUserActivityPulse() {
        if (!screenTimeoutUserActivityPulsePosted) {
            return;
        }
        mainHandler.removeCallbacks(screenTimeoutUserActivityPulse);
        screenTimeoutUserActivityPulsePosted = false;
    }

    private void pulseScreenTimeoutUserActivity(PowerManager powerManager, boolean forceLog) {
        if (powerManager == null) {
            return;
        }
        long uptime = SystemClock.uptimeMillis();
        Throwable firstFailure = null;
        try {
            Method userActivity = PowerManager.class.getDeclaredMethod(
                    "userActivity",
                    long.class,
                    int.class,
                    int.class);
            userActivity.setAccessible(true);
            userActivity.invoke(powerManager, uptime, 0, 1);
            maybeLogScreenTimeout("Pulsed screen timeout user activity without changing lights", forceLog);
            return;
        } catch (NoSuchMethodException ignored) {
            // Fall through to older framework signature.
        } catch (Throwable t) {
            firstFailure = t;
        }
        try {
            Method userActivity = PowerManager.class.getDeclaredMethod(
                    "userActivity",
                    long.class,
                    boolean.class);
            userActivity.setAccessible(true);
            userActivity.invoke(powerManager, uptime, true);
            maybeLogScreenTimeout("Pulsed screen timeout user activity without changing lights", forceLog);
        } catch (Throwable t) {
            if (!screenTimeoutUserActivityFailureLogged) {
                screenTimeoutUserActivityFailureLogged = true;
                if (firstFailure != null) {
                    error("Failed to pulse screen timeout user activity via int signature", firstFailure);
                }
                error("Failed to pulse screen timeout user activity", t);
            }
        }
    }

    private void maybeLogScreenTimeout(String message, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastScreenTimeoutLogAt < 2_000L) {
            return;
        }
        lastScreenTimeoutLogAt = now;
        info(message);
    }

    private void installMediaMetadataHook() {
        try {
            Method setMetadata = MediaSession.class.getDeclaredMethod("setMetadata", MediaMetadata.class);
            hook(setMetadata)
                    .setId(HOOK_ID_SET_METADATA)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSetMetadata);
            info("Hooked MediaSession#setMetadata");
        } catch (Throwable t) {
            error("Failed to hook MediaSession#setMetadata", t);
        }

    }

    Object onPlayerLyricResultConstructed(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            Object sourceArg = chain.getArg(0);
            Object secondArg = chain.getArg(1);
            Object thirdArg = chain.getArgs().size() > 2 ? chain.getArg(2) : null;

            String source = String.valueOf(sourceArg);
            String rawLyric = thirdArg instanceof String ? (String) thirdArg : secondArg instanceof String ? (String) secondArg : "";
            maybeCacheRealLyric(source, rawLyric);
        } catch (Throwable t) {
            error("Failed while reading player lyric result", t);
        }
        return result;
    }

    void cacheTimedLyric(String source, String rawLyric) {
        maybeCacheRealLyric(source, rawLyric);
    }

    @SuppressLint("WrongConstant")
    private Object onSetMetadata(XposedInterface.Chain chain) throws Throwable {
        Object metadataArg = chain.getArg(0);
        if (!(metadataArg instanceof MediaMetadata)) {
            return chain.proceed();
        }

        MediaMetadata original = (MediaMetadata) metadataArg;
        String existingLyricInfo = original.getString(OPLUS_LYRIC_INFO_KEY);
        if (!TextUtils.isEmpty(existingLyricInfo)) {
            return chain.proceed();
        }

        String title = firstNonEmpty(
                getText(original, MediaMetadata.METADATA_KEY_TITLE),
                getText(original, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        );
        String artist = firstNonEmpty(
                getText(original, MediaMetadata.METADATA_KEY_ARTIST),
                getText(original, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                getText(original, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        );

        if (TextUtils.isEmpty(title)) {
            return chain.proceed();
        }

        Object thisObject = chain.getThisObject();
        if (thisObject instanceof MediaSession) {
            lastSession = (MediaSession) thisObject;
            lastMetadata = original;
        }

        long duration = original.getLong(MediaMetadata.METADATA_KEY_DURATION);
        CachedLyric realLyric = getFreshCachedLyric();
        if (realLyric == null) {
            info("Skip lyricInfo injection because no fresh real lyric is cached for title="
                    + title + ", artist=" + nullToEmpty(artist));
            return chain.proceed();
        }

        String lyricInfo = buildLyricInfo(title, artist, duration, realLyric.lyric, realLyric.rawLyric);
        MediaMetadata patched = new MediaMetadata.Builder(original)
                .putString(OPLUS_LYRIC_INFO_KEY, lyricInfo)
                .build();

        List<Object> args = chain.getArgs();
        Object[] patchedArgs = args.toArray(new Object[0]);
        patchedArgs[0] = patched;

        info("Injected real " + realLyric.source
                + " lyricInfo for title=" + title + ", artist=" + nullToEmpty(artist));
        return chain.proceed(patchedArgs);
    }

    private void maybeCacheRealLyric(String source, String rawLyric) {
        if (TextUtils.isEmpty(rawLyric)) {
            return;
        }
        String normalized = sanitizeForOplusLyric(rawLyric);
        if (!looksLikeTimedLrc(normalized)) {
            return;
        }

        CachedLyric previous = cachedLyric;
        if (previous != null && normalized.equals(previous.lyric)) {
            return;
        }

        CachedLyric next = new CachedLyric(source, normalized, rawLyric, System.currentTimeMillis());
        cachedLyric = next;
        info("Cached real timed lyric from " + source
                + ", rawChars=" + rawLyric.length()
                + ", oplusChars=" + normalized.length());
        refreshLastMetadataWith(next);
    }

    @SuppressLint("WrongConstant")
    private void refreshLastMetadataWith(CachedLyric lyric) {
        MediaSession session = lastSession;
        MediaMetadata metadata = lastMetadata;
        if (session == null || metadata == null) {
            return;
        }

        String title = firstNonEmpty(
                getText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        );
        if (TextUtils.isEmpty(title)) {
            return;
        }
        String artist = firstNonEmpty(
                getText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                getText(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        );
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);

        try {
            MediaMetadata patched = new MediaMetadata.Builder(metadata)
                    .putString(OPLUS_LYRIC_INFO_KEY, buildLyricInfo(title, artist, duration, lyric.lyric, lyric.rawLyric))
                    .build();
            session.setMetadata(patched);
            info("Refreshed MediaSession metadata with cached real lyric from " + lyric.source);
        } catch (Throwable t) {
            error("Failed to refresh MediaSession metadata with real lyric", t);
        }
    }

    private CachedLyric getFreshCachedLyric() {
        CachedLyric lyric = cachedLyric;
        if (lyric == null) {
            return null;
        }
        long age = System.currentTimeMillis() - lyric.createdAtMillis;
        return age >= 0 && age <= LYRIC_CACHE_MAX_AGE_MS ? lyric : null;
    }

    private static boolean looksLikeTimedLrc(String lyric) {
        return !TextUtils.isEmpty(lyric) && ANY_LRC_TIME_TAG.matcher(lyric).find();
    }

    private void cacheSystemUiWordLyricModel(String lyricInfo) throws Exception {
        if (TextUtils.isEmpty(lyricInfo)) {
            systemUiHasOfficialLyric = false;
            currentWordLyricModel = null;
            lastVisibleOfficialLyricTextViewAt = 0L;
            updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }
        JSONObject object = new JSONObject(lyricInfo);
        String rawLyric = object.optString(OPLUS_RAW_LYRIC_INFO_KEY, "");
        if (TextUtils.isEmpty(rawLyric)) {
            systemUiHasOfficialLyric = false;
            currentWordLyricModel = null;
            lastVisibleOfficialLyricTextViewAt = 0L;
            updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }

        systemUiHasOfficialLyric = looksLikeTimedLrc(rawLyric);
        WordLyricModel model = parseWordLyric(rawLyric);
        if (model.lines.isEmpty()) {
            currentWordLyricModel = null;
            info("Cached SystemUI official lyric without word model");
            updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }
        currentWordLyricModel = model;
        info("Cached SystemUI word lyric model, lines=" + model.lines.size()
                + ", translations=" + model.translationCount());
        updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private static WordLyricModel parseWordLyric(String rawLyric) {
        WordLyricModel model = new WordLyricModel();
        LinkedHashMap<Long, ArrayList<String>> translations = new LinkedHashMap<>();
        ArrayList<PlainLyricLine> translationLines = new ArrayList<>();
        for (String rawLine : splitRawLyricLines(rawLyric)) {
            collectPlainLyricLines(rawLine, translations, translationLines);

            WordLine line = parseWordLine(rawLine);
            if (line != null && !line.words.isEmpty()) {
                model.lines.add(line);
            }
        }
        model.lines.sort((left, right) -> Long.compare(left.timeMillis, right.timeMillis));
        completeRepeatedPlainWordLines(model, translationLines);
        model.lines.sort((left, right) -> Long.compare(left.timeMillis, right.timeMillis));
        for (WordLine line : model.lines) {
            ArrayList<String> texts = translations.get(line.timeMillis);
            if (texts == null) {
                texts = findNearbyPlainLyricTexts(translationLines, line.timeMillis);
            }
            if (texts == null) {
                continue;
            }
            for (String text : texts) {
                if (!normalizeLine(text).equals(normalizeLine(line.text))) {
                    line.translation = text;
                    break;
                }
            }
        }
        return model;
    }

    private static void collectPlainLyricLines(
            String rawLine,
            LinkedHashMap<Long, ArrayList<String>> translations,
            ArrayList<PlainLyricLine> translationLines) {
        if (TextUtils.isEmpty(rawLine)) {
            return;
        }

        java.util.regex.Matcher matcher = ANY_LRC_TIME_TAG.matcher(rawLine);
        ArrayList<TagMatch> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(new TagMatch(matcher.start(), matcher.end(), parseLrcTimeMillis(matcher.group(1))));
        }
        if (tags.isEmpty() || tags.get(0).start != 0) {
            return;
        }

        boolean singleTagLine = tags.size() == 1;
        boolean wordTimedLine = !singleTagLine && hasTimedLyricContentSegments(rawLine, tags);
        if (wordTimedLine) {
            return;
        }
        for (int i = 0; i < tags.size(); i++) {
            TagMatch tag = tags.get(i);
            int segmentStart = tag.end;
            int segmentEnd = i + 1 < tags.size() ? tags.get(i + 1).start : rawLine.length();
            if (segmentStart >= segmentEnd) {
                continue;
            }

            String text = cleanPlainLyricText(rawLine.substring(segmentStart, segmentEnd));
            if (TextUtils.isEmpty(text)) {
                continue;
            }

            ArrayList<String> texts = translations.get(tag.timeMillis);
            if (texts == null) {
                texts = new ArrayList<>();
                translations.put(tag.timeMillis, texts);
            }
            texts.add(text);
            translationLines.add(new PlainLyricLine(tag.timeMillis, text));
        }
    }

    private static ArrayList<String> findNearbyPlainLyricTexts(ArrayList<PlainLyricLine> lines, long timeMillis) {
        ArrayList<String> texts = null;
        long bestDistance = Long.MAX_VALUE;
        for (PlainLyricLine line : lines) {
            long distance = Math.abs(line.timeMillis - timeMillis);
            if (distance > 80L || distance > bestDistance) {
                continue;
            }
            if (distance < bestDistance) {
                texts = new ArrayList<>();
                bestDistance = distance;
            }
            texts.add(line.text);
        }
        return texts;
    }

    private static void completeRepeatedPlainWordLines(
            WordLyricModel model, ArrayList<PlainLyricLine> plainLines) {
        if (model == null || plainLines.isEmpty()) {
            return;
        }

        for (PlainLyricLine plainLine : plainLines) {
            if (!containsLatinLetter(plainLine.text)
                    || hasWordLineNearTime(model, plainLine.timeMillis)) {
                continue;
            }

            WordLine template = findRepeatedWordTemplate(model, plainLine.text, plainLine.timeMillis);
            WordLine completedLine = cloneWordLineAt(template, plainLine);
            if (completedLine != null) {
                model.lines.add(completedLine);
            }
        }
    }

    private static boolean hasWordLineNearTime(WordLyricModel model, long timeMillis) {
        for (WordLine line : model.lines) {
            long distance = Math.abs(line.timeMillis - timeMillis);
            if (distance <= 80L) {
                return true;
            }
        }
        return false;
    }

    private static WordLine findRepeatedWordTemplate(
            WordLyricModel model, String text, long timeMillis) {
        String normalizedText = normalizeLine(text);
        if (TextUtils.isEmpty(normalizedText)) {
            return null;
        }

        WordLine best = null;
        long bestDistance = Long.MAX_VALUE;
        for (WordLine line : model.lines) {
            if (!normalizeLine(line.text).equals(normalizedText)) {
                continue;
            }
            long distance = Math.abs(line.timeMillis - timeMillis);
            if (distance <= 80L) {
                continue;
            }
            if (best == null || distance < bestDistance) {
                best = line;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static WordLine cloneWordLineAt(WordLine template, PlainLyricLine plainLine) {
        if (template == null || plainLine == null || template.words.isEmpty()) {
            return null;
        }

        long delta = plainLine.timeMillis - template.timeMillis;
        ArrayList<WordRange> shiftedWords = new ArrayList<>(template.words.size());
        for (WordRange word : template.words) {
            shiftedWords.add(new WordRange(Math.max(plainLine.timeMillis, word.timeMillis + delta), word.start, word.end));
        }

        ArrayList<WordRange> rebuiltWords = rebuildWordRanges(plainLine.text, shiftedWords);
        if (rebuiltWords.isEmpty()) {
            return null;
        }
        long endTimeMillis = template.endTimeMillis > template.timeMillis
                ? plainLine.timeMillis + (template.endTimeMillis - template.timeMillis)
                : inferWordLineEndMillis(plainLine.timeMillis, rebuiltWords);
        return new WordLine(plainLine.timeMillis, plainLine.text, rebuiltWords, endTimeMillis);
    }

    private static String cleanPlainLyricText(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        text = ANY_LRC_TIME_TAG.matcher(text).replaceAll("");
        text = text.replace('\uFEFF', ' ').trim();
        return text.replaceAll("[ \\t]{2,}", " ");
    }

    private static PlainLyricLine parsePlainLyricLine(String rawLine) {
        if (TextUtils.isEmpty(rawLine)) {
            return null;
        }
        java.util.regex.Matcher firstTag = ANY_LRC_TIME_TAG.matcher(rawLine);
        if (!firstTag.find() || firstTag.start() != 0) {
            return null;
        }
        String text = rawLine.substring(firstTag.end());
        text = cleanPlainLyricText(text);
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        return new PlainLyricLine(parseLrcTimeMillis(firstTag.group(1)), text);
    }

    private static WordLine parseWordLine(String rawLine) {
        if (TextUtils.isEmpty(rawLine)) {
            return null;
        }
        java.util.regex.Matcher matcher = ANY_LRC_TIME_TAG.matcher(rawLine);
        ArrayList<TagMatch> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(new TagMatch(matcher.start(), matcher.end(), parseLrcTimeMillis(matcher.group(1))));
        }
        if (tags.size() < 2 || tags.get(0).start != 0) {
            return null;
        }

        StringBuilder text = new StringBuilder(rawLine.length());
        ArrayList<WordRange> words = new ArrayList<>();
        long lineTimeMillis = -1L;
        long lineEndTimeMillis = -1L;
        for (int i = 0; i < tags.size(); i++) {
            TagMatch tag = tags.get(i);
            int segmentStartInRaw = tag.end;
            int segmentEndInRaw = i + 1 < tags.size() ? tags.get(i + 1).start : rawLine.length();
            if (segmentStartInRaw >= segmentEndInRaw) {
                if (i == tags.size() - 1) {
                    lineEndTimeMillis = tag.timeMillis;
                }
                continue;
            }

            String segment = rawLine.substring(segmentStartInRaw, segmentEndInRaw);
            if (TextUtils.isEmpty(segment)) {
                if (i == tags.size() - 1 && TextUtils.isEmpty(segment.trim())) {
                    lineEndTimeMillis = tag.timeMillis;
                }
                continue;
            }
            int base = text.length();
            text.append(segment);

            int start = base;
            int end = base + segment.length();
            while (start < end && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            if (start < end && isTimedWordSegment(text.substring(start, end))) {
                if (lineTimeMillis < 0) {
                    lineTimeMillis = tag.timeMillis;
                }
                words.add(new WordRange(tag.timeMillis, start, end));
            }
        }

        String cleanText = text.toString().trim().replaceAll("[ \\t]{2,}", " ");
        if (cleanText.isEmpty() || words.isEmpty()) {
            return null;
        }

        NormalizedWordLineText normalized = normalizeTimedWordText(text.toString(), words);
        if (TextUtils.isEmpty(normalized.text) || normalized.words.isEmpty()) {
            return null;
        }
        long startTimeMillis = lineTimeMillis >= 0 ? lineTimeMillis : tags.get(0).timeMillis;
        long endTimeMillis = lineEndTimeMillis > startTimeMillis
                ? lineEndTimeMillis
                : inferWordLineEndMillis(startTimeMillis, normalized.words);
        return new WordLine(startTimeMillis, normalized.text, normalized.words, endTimeMillis);
    }

    private static NormalizedWordLineText normalizeTimedWordText(String text, ArrayList<WordRange> words) {
        if (TextUtils.isEmpty(text) || words.isEmpty()) {
            return new NormalizedWordLineText("", new ArrayList<>());
        }

        int length = text.length();
        int[] boundaryMap = new int[length + 1];
        StringBuilder normalized = new StringBuilder(length);
        boolean emittedText = false;
        boolean pendingSpace = false;
        for (int i = 0; i < length; i++) {
            char value = text.charAt(i);
            if (value == ' ' || value == '\t') {
                boundaryMap[i] = normalized.length();
                if (emittedText) {
                    pendingSpace = true;
                }
                continue;
            }
            if (pendingSpace && normalized.length() > 0) {
                normalized.append(' ');
                pendingSpace = false;
            }
            boundaryMap[i] = normalized.length();
            normalized.append(value);
            emittedText = true;
        }
        boundaryMap[length] = normalized.length();

        ArrayList<WordRange> normalizedWords = new ArrayList<>(words.size());
        for (WordRange word : words) {
            int start = word.start >= 0 && word.start <= length ? boundaryMap[word.start] : normalized.length();
            int end = word.end >= 0 && word.end <= length ? boundaryMap[word.end] : normalized.length();
            if (start < end) {
                normalizedWords.add(new WordRange(word.timeMillis, start, end));
            }
        }
        return new NormalizedWordLineText(normalized.toString(), normalizedWords);
    }

    private static ArrayList<WordRange> rebuildWordRanges(String cleanText, ArrayList<WordRange> originalWords) {
        ArrayList<WordRange> rebuilt = new ArrayList<>();
        int searchFrom = 0;
        for (WordRange ignored : originalWords) {
            while (searchFrom < cleanText.length() && Character.isWhitespace(cleanText.charAt(searchFrom))) {
                searchFrom++;
            }
            int start = searchFrom;
            while (searchFrom < cleanText.length() && !Character.isWhitespace(cleanText.charAt(searchFrom))) {
                searchFrom++;
            }
            int end = searchFrom;
            if (start < end) {
                rebuilt.add(new WordRange(ignored.timeMillis, start, end));
            }
        }
        return rebuilt;
    }

    private static long inferWordLineEndMillis(long timeMillis, ArrayList<WordRange> words) {
        if (words == null || words.isEmpty()) {
            return timeMillis + 600L;
        }
        return Math.max(timeMillis + 600L, words.get(words.size() - 1).timeMillis + 520L);
    }

    private long estimatePlaybackPositionMillis() {
        long base = lastComputedPositionMs;
        long elapsed = lastComputedPositionElapsedMs;
        if (base >= 0 && (!lastPlaybackIsPlaying || elapsed < 0)) {
            return base;
        }
        if (base >= 0 && elapsed >= 0) {
            long delta = SystemClock.elapsedRealtime() - elapsed;
            if (delta >= 0 && delta < 30_000L) {
                return base + delta;
            }
            return base;
        }

        WordLyricModel model = currentWordLyricModel;
        WordLine hintedLine = model == null ? null : model.lineAt(lastLyricsRecyclerIndex);
        if (hintedLine != null) {
            return hintedLine.timeMillis;
        }
        return -1L;
    }

    private void maybeLogTextViewSpan(long position, String line, Object view) {
        long now = System.currentTimeMillis();
        if (now - lastTextViewSpanLogAt < 1_000L) {
            return;
        }
        lastTextViewSpanLogAt = now;
        info("Observed active lyric TextView at position=" + position
                + ", line=" + line
                + ", view=" + (view == null ? "null" : view.getClass().getName()));
    }

    private void maybeLogTextViewDraw(DrawFrame frame, TextView view) {
        long now = System.currentTimeMillis();
        if (now - lastTextViewDrawLogAt < 1_000L) {
            return;
        }
        lastTextViewDrawLogAt = now;
        WordLine wordLine = frame == null ? null : frame.line;
        info("Custom-drew official lyric TextView at position=" + (frame == null ? -1L : frame.position)
                + ", playing=" + lastPlaybackIsPlaying
                + ", index=" + (frame == null ? -1 : frame.lineIndex)
                + ", active=" + (frame != null && frame.active)
                + ", focused=" + (frame != null && frame.focused)
                + ", line=" + (wordLine == null ? "" : wordLine.text)
                + ", hasTranslation=" + (wordLine != null && !TextUtils.isEmpty(wordLine.translation))
                + ", view=" + describeViewForLog(view));
    }

    private void maybeLogRecyclerScrollStabilize(View recycler, String reason) {
        long now = System.currentTimeMillis();
        if (now - lastRecyclerScrollStabilizeLogAt < 1_500L) {
            return;
        }
        lastRecyclerScrollStabilizeLogAt = now;
        info("Stabilized LyricsRecyclerView scroll, reason=" + reason
                + ", index=" + lastLyricsRecyclerIndex
                + ", recycler=" + describeViewForLog(recycler));
    }

    private void maybeLogSeedlingPlaybackState(int state, long storedPosition, long computedPosition, float speed) {
        long now = System.currentTimeMillis();
        if (state == lastLoggedSystemUiPlaybackState && now - lastSeedlingPlaybackStateLogAt < 2_000L) {
            return;
        }
        lastLoggedSystemUiPlaybackState = state;
        lastSeedlingPlaybackStateLogAt = now;
        info("Seedling playback state=" + state
                + ", playing=" + lastPlaybackIsPlaying
                + ", storedPosition=" + storedPosition
                + ", computedPosition=" + computedPosition
                + ", speed=" + speed);
    }

    private void maybeLogActiveRefresh(
            long position, String line, int candidates, int attached, int visible, int updated) {
        long now = System.currentTimeMillis();
        if (now - lastActiveRefreshLogAt < 1_000L) {
            return;
        }
        lastActiveRefreshLogAt = now;
        info("Refreshed active lyric renderer at position=" + position
                + ", candidates=" + candidates
                + ", attached=" + attached
                + ", visible=" + visible
                + ", updated=" + updated
                + ", line=" + line);
    }

    private void maybeLogRecyclerIndex(int index) {
        long now = System.currentTimeMillis();
        if (now - lastRecyclerLogAt < 1_000L) {
            return;
        }
        lastRecyclerLogAt = now;
        info("LyricsRecyclerView current index=" + index);
    }

    private static String sanitizeForOplusLyric(String rawLyric) {
        if (TextUtils.isEmpty(rawLyric)) {
            return "";
        }

        LinkedHashMap<Long, TimedLyricGroup> groups = new LinkedHashMap<>();
        for (String rawLine : splitRawLyricLines(rawLyric)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            java.util.regex.Matcher firstTag = ANY_LRC_TIME_TAG.matcher(line);
            if (!firstTag.find() || firstTag.start() != 0) {
                continue;
            }

            String time = firstTag.group(1);
            String text = line.substring(firstTag.end());
            text = ANY_LRC_TIME_TAG.matcher(text).replaceAll("");
            text = text.replace('\uFEFF', ' ').trim();
            text = text.replaceAll("[ \\t]{2,}", " ");
            if (text.isEmpty()) {
                continue;
            }

            long timeMillis = parseLrcTimeMillis(time);
            TimedLyricGroup group = groups.get(timeMillis);
            if (group == null) {
                group = new TimedLyricGroup(timeMillis);
                groups.put(timeMillis, group);
            }
            group.texts.add(text);
        }

        StringBuilder out = new StringBuilder(rawLyric.length());
        ArrayList<TimedLyricGroup> groupedLines = new ArrayList<>(groups.values());
        for (TimedLyricGroup group : groupedLines) {
            appendGroupedLyricLines(out, group);
        }
        appendTailSpacerLyricLine(out, groupedLines);
        return out.toString();
    }

    private static void appendGroupedLyricLines(StringBuilder out, TimedLyricGroup group) {
        if (group.texts.isEmpty()) {
            return;
        }

        int primaryIndex = findPrimaryTextIndex(group.texts);
        appendLyricLine(out, group.timeMillis, group.texts.get(primaryIndex));
    }

    private static void appendLyricLine(StringBuilder out, long timeMillis, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append('[').append(formatLrcTime(timeMillis)).append(']').append(text);
    }

    private static void appendTailSpacerLyricLine(StringBuilder out, ArrayList<TimedLyricGroup> groups) {
        if (groups.isEmpty()) {
            return;
        }
        TimedLyricGroup last = groups.get(groups.size() - 1);
        if (last == null) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append('[')
                .append(formatLrcTime(last.timeMillis + OPLUS_TAIL_SPACER_DELAY_MS))
                .append(']')
                .append((char) 0x200B);
    }

    private static int findPrimaryTextIndex(List<String> texts) {
        for (int i = 0; i < texts.size(); i++) {
            if (containsLatinLetter(texts.get(i))) {
                return i;
            }
        }
        return 0;
    }

    private static boolean containsLatinLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNonAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static long parseLrcTimeMillis(String time) {
        String[] minuteAndRest = time.split(":", 2);
        if (minuteAndRest.length != 2) {
            return 0L;
        }
        long minutes = safeParseLong(minuteAndRest[0]);
        String rest = minuteAndRest[1].replace(':', '.');
        String[] secondAndFraction = rest.split("\\.", 2);
        long seconds = safeParseLong(secondAndFraction[0]);
        long millis = 0L;
        if (secondAndFraction.length == 2) {
            String fraction = secondAndFraction[1];
            if (fraction.length() > 3) {
                fraction = fraction.substring(0, 3);
            }
            while (fraction.length() < 3) {
                fraction = fraction + "0";
            }
            millis = safeParseLong(fraction);
        }
        return minutes * 60_000L + seconds * 1_000L + millis;
    }

    private static long safeParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String formatLrcTime(long timeMillis) {
        long minutes = timeMillis / 60_000L;
        long seconds = (timeMillis % 60_000L) / 1_000L;
        long millis = timeMillis % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    private static final class TimedLyricGroup {
        final long timeMillis;
        final ArrayList<String> texts = new ArrayList<>();

        TimedLyricGroup(long timeMillis) {
            this.timeMillis = timeMillis;
        }
    }

    private static String getText(MediaMetadata metadata, String key) {
        CharSequence value = metadata.getText(key);
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isLikelyWordSegment(String segment) {
        if (TextUtils.isEmpty(segment)) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char ch = segment.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTimedLyricContentSegments(String rawLine, ArrayList<TagMatch> tags) {
        if (TextUtils.isEmpty(rawLine) || tags == null || tags.size() < 2) {
            return false;
        }
        for (int i = 0; i < tags.size(); i++) {
            int segmentStart = tags.get(i).end;
            int segmentEnd = i + 1 < tags.size() ? tags.get(i + 1).start : rawLine.length();
            if (segmentStart >= segmentEnd) {
                continue;
            }
            if (isLikelyWordSegment(rawLine.substring(segmentStart, segmentEnd))) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<String> splitRawLyricLines(String rawLyric) {
        ArrayList<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(rawLyric)) {
            return result;
        }

        String[] lines = rawLyric.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String rawLine : lines) {
            appendSplitRawLyricLine(result, rawLine == null ? "" : rawLine.trim());
        }
        return result;
    }

    private static void appendSplitRawLyricLine(ArrayList<String> out, String rawLine) {
        if (TextUtils.isEmpty(rawLine)) {
            return;
        }
        String[] split = splitMixedTranslationAndWordLine(rawLine);
        if (split == null) {
            out.add(rawLine);
            return;
        }
        appendSplitRawLyricLine(out, split[0]);
        appendSplitRawLyricLine(out, split[1]);
    }

    private static String[] splitMixedTranslationAndWordLine(String rawLine) {
        java.util.regex.Matcher matcher = ANY_LRC_TIME_TAG.matcher(rawLine);
        ArrayList<TagMatch> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(new TagMatch(matcher.start(), matcher.end(), parseLrcTimeMillis(matcher.group(1))));
        }
        if (tags.size() < 2 || tags.get(0).start != 0) {
            return null;
        }

        for (int i = 1; i < tags.size(); i++) {
            TagMatch splitTag = tags.get(i);
            String prefixText = cleanPlainLyricText(rawLine.substring(tags.get(0).end, splitTag.start));
            if (TextUtils.isEmpty(prefixText)
                    || prefixText.length() < 4
                    || !containsNonAscii(prefixText)) {
                continue;
            }
            if (containsLatinLetter(prefixText) && containsLyricLeadSeparator(prefixText)) {
                continue;
            }

            int segmentStart = splitTag.end;
            int segmentEnd = i + 1 < tags.size() ? tags.get(i + 1).start : rawLine.length();
            if (segmentStart >= segmentEnd) {
                continue;
            }
            if (!containsLatinLetter(rawLine.substring(segmentStart, segmentEnd))) {
                continue;
            }

            String firstLine = "[" + formatLrcTime(tags.get(0).timeMillis) + "]" + prefixText;
            String secondLine = rawLine.substring(splitTag.start).trim();
            if (!TextUtils.isEmpty(secondLine)) {
                return new String[]{firstLine, secondLine};
            }
        }
        return null;
    }

    private static boolean containsLyricLeadSeparator(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        String normalized = normalizeLine(text);
        return normalized.indexOf(':') >= 0 || normalized.indexOf('\uFF1A') >= 0;
    }

    private static boolean isTimedWordSegment(String segment) {
        if (TextUtils.isEmpty(segment)) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isWhitespace(segment.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String buildLyricInfo(String title, String artist, long duration, String lyric, String rawLyric) throws Exception {
        JSONObject object = new JSONObject();
        object.put("songName", title);
        object.put("artist", nullToEmpty(artist));
        object.put("songId", buildSongId(title, artist, duration));
        object.put("lyric", lyric);
        object.put(OPLUS_RAW_LYRIC_INFO_KEY, rawLyric);
        return object.toString();
    }

    private static String buildSongId(String title, String artist, long duration) {
        String raw = title + "|" + nullToEmpty(artist) + "|" + duration;
        return "lockscreen-lyrics-" + Integer.toHexString(raw.hashCode()).toLowerCase(Locale.ROOT);
    }

    private static String buildDemoLrc(String title, String artist) {
        String displayArtist = TextUtils.isEmpty(artist) ? "Unknown artist" : artist;
        return "[00:00.00]" + title + "\n"
                + "[00:04.00]" + displayArtist + "\n"
                + "[00:08.00]Lock-screen lyricInfo demo\n"
                + "[00:12.00]If this line appears, the OPlus path works\n"
                + "[00:16.00]Next step is wiring real LRC data\n"
                + "[00:20.00]Injected by LSPosed API 102";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static float sp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().scaledDensity;
    }

    void info(String message) {
        Log.i(TAG, message);
        log(Log.INFO, TAG, message);
    }

    void error(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
        log(Log.ERROR, TAG, message, throwable);
    }

    private static final class OfficialLyricTextRenderer {
        private static final int INACTIVE_COLOR = 0x82FFFFFF;
        private static final int FOCUSED_INACTIVE_COLOR = 0xD8FFFFFF;
        private static final int PLAYED_COLOR = 0xF0FFFFFF;
        private static final int ACTIVE_COLOR = 0xFFFFFFFF;
        private static final int TRANSLATION_COLOR = 0x9CFFFFFF;
        private static final int FOCUSED_TRANSLATION_COLOR = 0xC8FFFFFF;
        private static final int ACTIVE_BACKGROUND_COLOR = 0x24FFFFFF;
        private static final int PROGRESS_BACKGROUND_COLOR = 0x42FFFFFF;
        private static final int ACTIVE_GLOW_FILL_COLOR = 0x2EFFFFFF;
        private static final int ACTIVE_GLOW_SHADOW_COLOR = 0xB8FFD68A;
        private static final float ACTIVE_GLOW_RADIUS_FACTOR = 0.17f;
        private static final float ACTIVE_FEATHER_WIDTH_FACTOR = 0.42f;
        private static final float FIXED_SLOT_HEIGHT_DP = 82f;
        private static final float MAIN_TEXT_SIZE_SP = 22f;
        private static final float GROUP_TOP_INSET_DP = 4f;
        private static final int MAX_DRAW_LINES = 3;
        private static final int MAX_TRANSLATED_MAIN_DRAW_LINES = 2;
        private static final long FOCUSED_REVEAL_ANIMATION_MS = 260L;
        private static final long TRANSLATED_WINDOW_ANIMATION_MS = 220L;
        private static final float TRANSLATED_WINDOW_SLIDE_DP = 7f;

        private final TextPaint inactivePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint playedPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint activePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint activeGlowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint activeFeatherPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint translationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint activeBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activeGlowMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final PorterDuffXfermode dstInXfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
        private final RectF activeBackgroundRect = new RectF();
        private final ArrayList<LyricDrawLine> drawLines = new ArrayList<>(MAX_DRAW_LINES);

        private WordLine lastLine;
        private long lineChangeElapsedMs = SystemClock.elapsedRealtime();

        void applySlotHeight(TextView textView, WordLyricModel model, WordLine line) {
            int height = resolveSlotHeight(textView, model, line);
            if (height > 0) {
                setOfficialLyricSlotHeight(textView, height);
            }
        }

        void draw(Canvas canvas, TextView textView, DrawFrame frame) {
            WordLine line = frame.line;
            long position = frame.position;
            if (canvas == null || textView == null || line == null || TextUtils.isEmpty(line.text)) {
                return;
            }
            if (textView.getWidth() <= 0 || textView.getHeight() <= 0) {
                return;
            }
            if (lastLine != line) {
                lastLine = line;
                lineChangeElapsedMs = SystemClock.elapsedRealtime();
            }

            String text = line.text;
            int leftPadding = textView.getPaddingLeft();
            int rightPadding = textView.getPaddingRight();
            int topPadding = textView.getPaddingTop();
            int bottomPadding = textView.getPaddingBottom();
            float availableWidth = textView.getWidth() - leftPadding - rightPadding;
            float availableHeight = textView.getHeight() - topPadding - bottomPadding;
            if (availableWidth <= 1f) {
                return;
            }

            int fixedSlotHeight = resolveSlotHeight(textView, frame.model, line);
            if (fixedSlotHeight <= 0) {
                return;
            }
            setOfficialLyricSlotHeight(textView, fixedSlotHeight);
            availableHeight = Math.max(1f, fixedSlotHeight - topPadding - bottomPadding);

            boolean compactSlot = TextUtils.isEmpty(line.translation) && isCompactLyricSlot(textView, availableHeight);
            configurePaints(textView, compactSlot);
            float focusAmount = resolveFocusAmount(line, frame.active, frame.focused, frame.activeIndex);
            if (frame.focused) {
                clearFocusedOfficialLyricViewEffects(textView);
                if (focusAmount < 1f) {
                    textView.postInvalidateOnAnimation();
                }
            }
            applyFade(1f, focusAmount);
            if (compactSlot) {
                drawCompactLine(canvas, textView, line, position, frame.active, focusAmount, availableWidth, availableHeight);
                return;
            }
            boolean finalLine = frame.model != null && frame.lineIndex >= frame.model.lines.size() - 1;
            drawLyricGroup(
                    canvas,
                    textView,
                    line,
                    position,
                    frame.active,
                    finalLine,
                    availableWidth,
                    availableHeight,
                    focusAmount);
        }

        private float resolveFocusAmount(WordLine line, boolean activeLine, boolean focusedLine, int activeIndex) {
            if (line == null || !focusedLine) {
                return 0f;
            }
            if (activeLine) {
                line.focusedVisualActiveIndex = activeIndex;
                line.focusedVisualStartElapsedMs = 0L;
                return 1f;
            }

            long now = SystemClock.elapsedRealtime();
            if (line.focusedVisualActiveIndex != activeIndex || line.focusedVisualStartElapsedMs <= 0L) {
                line.focusedVisualActiveIndex = activeIndex;
                line.focusedVisualStartElapsedMs = now;
                return 0f;
            }
            float progress = (now - line.focusedVisualStartElapsedMs) / (float) FOCUSED_REVEAL_ANIMATION_MS;
            return smoothStep(progress);
        }

        private void drawLyricGroup(
                Canvas canvas,
                TextView textView,
                WordLine line,
                long position,
                boolean activeLine,
                boolean finalLine,
                float availableWidth,
                float availableHeight,
                float focusAmount) {
            float originalSize = inactivePaint.getTextSize();
            boolean finalTranslatedLine = finalLine && !TextUtils.isEmpty(line.translation);
            boolean hasTranslation = !TextUtils.isEmpty(line.translation);

            String text = line.text;
            buildDrawLines(text, availableWidth, false);
            int wordIndex = activeLine ? line.findWordIndex(position) : -1;
            WordRange activeWord = wordIndex >= 0 && wordIndex < line.words.size() ? line.words.get(wordIndex) : null;
            if (drawLines.isEmpty()) {
                setTextSize(originalSize);
                return;
            }
            TranslatedLineWindow lineWindow = hasTranslation
                    ? resolveTranslatedMainLineWindow(line, activeWord, activeLine, availableWidth)
                    : null;
            int visibleMainLineCount = lineWindow == null ? drawLines.size() : lineWindow.count;

            Paint.FontMetrics mainMetrics = inactivePaint.getFontMetrics();
            float lineHeight = mainMetrics.descent - mainMetrics.ascent;
            float lineGap = visibleMainLineCount > 1 ? dp(textView.getContext(), 1f) : 0f;
            float translationGap = hasTranslation ? dp(textView.getContext(), 2f) : 0f;
            Paint.FontMetrics translationMetrics = translationPaint.getFontMetrics();
            float top = textView.getPaddingTop() + dp(textView.getContext(), GROUP_TOP_INSET_DP);
            if (hasTranslation && visibleMainLineCount == 1) {
                float translationHeight = translationMetrics.descent - translationMetrics.ascent;
                float groupHeight = lineHeight + translationGap + translationHeight;
                top = textView.getPaddingTop() + Math.max(0f, (availableHeight - groupHeight) * 0.5f);
            } else if (finalTranslatedLine) {
                float topInset = dp(textView.getContext(), 6f);
                top = textView.getPaddingTop() + Math.min(Math.max(0f, top - textView.getPaddingTop()), topInset);
            }

            canvas.save();
            canvas.clipRect(textView.getPaddingLeft(), 0, textView.getWidth() - textView.getPaddingRight(), textView.getHeight());
            float y = top - mainMetrics.ascent;
            if (lineWindow != null && lineWindow.animating) {
                float slide = dp(textView.getContext(), TRANSLATED_WINDOW_SLIDE_DP);
                float direction = lineWindow.currentStart >= lineWindow.previousStart ? 1f : -1f;
                drawMainLineWindow(
                        canvas,
                        textView,
                        line,
                        text,
                        activeWord,
                        wordIndex,
                        y - direction * slide * lineWindow.progress,
                        lineHeight,
                        lineGap,
                        availableWidth,
                        position,
                        activeLine,
                        lineWindow.previousStart,
                        lineWindow.count,
                        focusAmount,
                        1f - lineWindow.alphaProgress);
                drawMainLineWindow(
                        canvas,
                        textView,
                        line,
                        text,
                        activeWord,
                        wordIndex,
                        y + direction * slide * (1f - lineWindow.progress),
                        lineHeight,
                        lineGap,
                        availableWidth,
                        position,
                        activeLine,
                        lineWindow.currentStart,
                        lineWindow.count,
                        focusAmount,
                        lineWindow.alphaProgress);
                textView.postInvalidateOnAnimation();
            } else {
                int windowStart = lineWindow == null ? 0 : lineWindow.currentStart;
                drawMainLineWindow(
                        canvas,
                        textView,
                        line,
                        text,
                        activeWord,
                        wordIndex,
                        y,
                        lineHeight,
                        lineGap,
                        availableWidth,
                        position,
                        activeLine,
                        windowStart,
                        visibleMainLineCount,
                        focusAmount,
                        1f);
            }
            applyFade(1f, focusAmount);
            if (hasTranslation) {
                float translationBaseline = top
                        + lineHeight * visibleMainLineCount
                        + lineGap * Math.max(0, visibleMainLineCount - 1)
                        + translationGap
                        - translationMetrics.ascent;
                canvas.drawText(line.translation, textView.getPaddingLeft(), translationBaseline, translationPaint);
            }
            canvas.restore();

            if (inactivePaint.getTextSize() != originalSize) {
                setTextSize(originalSize);
            }
        }

        private int resolveSlotHeight(TextView textView, WordLyricModel model, WordLine line) {
            if (textView == null || line == null) {
                return 0;
            }
            return dp(textView.getContext(), FIXED_SLOT_HEIGHT_DP);
        }

        private float resolveAvailableWidth(TextView textView) {
            if (textView == null) {
                return 0f;
            }
            int leftPadding = textView.getPaddingLeft();
            int rightPadding = textView.getPaddingRight();
            float availableWidth = textView.getWidth() - leftPadding - rightPadding;
            if (availableWidth <= 1f) {
                View itemView = findLyricsRecyclerItemView(textView);
                if (itemView != null && itemView != textView) {
                    availableWidth = itemView.getWidth() - leftPadding - rightPadding;
                }
            }
            return availableWidth;
        }

        private TranslatedLineWindow resolveTranslatedMainLineWindow(
                WordLine line,
                WordRange activeWord,
                boolean activeLine,
                float availableWidth) {
            int totalLines = drawLines.size();
            int count = Math.min(MAX_TRANSLATED_MAIN_DRAW_LINES, totalLines);
            if (totalLines <= MAX_TRANSLATED_MAIN_DRAW_LINES) {
                resetTranslatedWindow(line, 0, availableWidth);
                return new TranslatedLineWindow(0, 0, count, 1f, 1f, false);
            }

            int targetStart = activeLine
                    ? targetTranslatedMainLineWindowStart(activeWord, totalLines)
                    : 0;
            int widthKey = Math.max(1, Math.round(availableWidth));
            long now = SystemClock.elapsedRealtime();
            if (line.translatedWindowWidthKey != widthKey || !activeLine) {
                line.translatedWindowWidthKey = widthKey;
                line.translatedWindowPreviousStart = targetStart;
                line.translatedWindowStart = targetStart;
                line.translatedWindowChangedAtMs = now;
                return new TranslatedLineWindow(targetStart, targetStart, count, 1f, 1f, false);
            }

            if (line.translatedWindowStart != targetStart) {
                line.translatedWindowPreviousStart = line.translatedWindowStart;
                line.translatedWindowStart = targetStart;
                line.translatedWindowChangedAtMs = now;
            }

            float rawProgress = Math.min(
                    1f,
                    Math.max(0f, (now - line.translatedWindowChangedAtMs) / (float) TRANSLATED_WINDOW_ANIMATION_MS));
            float eased = smoothStep(rawProgress);
            boolean animating = rawProgress < 1f && line.translatedWindowPreviousStart != line.translatedWindowStart;
            if (!animating) {
                line.translatedWindowPreviousStart = line.translatedWindowStart;
            }
            return new TranslatedLineWindow(
                    line.translatedWindowStart,
                    line.translatedWindowPreviousStart,
                    count,
                    eased,
                    eased,
                    animating);
        }

        private void resetTranslatedWindow(WordLine line, int start, float availableWidth) {
            if (line == null) {
                return;
            }
            line.translatedWindowWidthKey = Math.max(1, Math.round(availableWidth));
            line.translatedWindowPreviousStart = start;
            line.translatedWindowStart = start;
            line.translatedWindowChangedAtMs = SystemClock.elapsedRealtime();
        }

        private int targetTranslatedMainLineWindowStart(WordRange activeWord, int totalLines) {
            if (activeWord == null) {
                return 0;
            }
            for (int i = 0; i < drawLines.size(); i++) {
                LyricDrawLine drawLine = drawLines.get(i);
                if (activeWord.end > drawLine.start && activeWord.start < drawLine.end) {
                    return Math.max(0, Math.min(i, totalLines - MAX_TRANSLATED_MAIN_DRAW_LINES));
                }
            }
            return 0;
        }

        private void drawMainLineWindow(
                Canvas canvas,
                TextView textView,
                WordLine line,
                String text,
                WordRange activeWord,
                int wordIndex,
                float y,
                float lineHeight,
                float lineGap,
                float availableWidth,
                long position,
                boolean activeLine,
                int windowStart,
                int count,
                float focusAmount,
                float alpha) {
            if (alpha <= 0.01f || count <= 0) {
                return;
            }
            applyFade(alpha, focusAmount);
            int start = Math.max(0, Math.min(windowStart, drawLines.size()));
            int end = Math.min(drawLines.size(), start + count);
            float lineY = y;
            for (int i = start; i < end; i++) {
                LyricDrawLine drawLine = drawLines.get(i);
                float segmentWidth = inactivePaint.measureText(text, drawLine.start, drawLine.end);
                float x = resolveTextX(textView, segmentWidth, availableWidth);
                drawSegment(canvas, line, text, drawLine, activeWord, wordIndex, x, lineY, position, activeLine);
                lineY += lineHeight + lineGap;
            }
        }

        private static float smoothStep(float progress) {
            float value = Math.max(0f, Math.min(1f, progress));
            return value * value * (3f - 2f * value);
        }

        private void drawCompactLine(
                Canvas canvas,
                TextView textView,
                WordLine line,
                long position,
                boolean activeLine,
                float focusAmount,
                float availableWidth,
                float availableHeight) {
            float originalSize = inactivePaint.getTextSize();
            fitSingleLineText(textView, line.text, availableWidth, originalSize, sp(textView.getContext(), 10f));
            applyFade(1f, focusAmount);

            Paint.FontMetrics metrics = inactivePaint.getFontMetrics();
            float lineHeight = metrics.descent - metrics.ascent;
            float y = textView.getPaddingTop() + Math.max(0f, (availableHeight - lineHeight) / 2f) - metrics.ascent;

            canvas.save();
            canvas.clipRect(textView.getPaddingLeft(), 0, textView.getWidth() - textView.getPaddingRight(), textView.getHeight());
            if (activeLine) {
                drawProgressLine(canvas, textView, line, position, y);
            } else {
                canvas.drawText(line.text, textView.getPaddingLeft(), y, inactivePaint);
            }
            canvas.restore();

            if (inactivePaint.getTextSize() != originalSize) {
                setTextSize(originalSize);
            }
        }

        private void drawProgressLine(Canvas canvas, TextView textView, WordLine line, long position, float y) {
            canvas.drawText(line.text, textView.getPaddingLeft(), y, inactivePaint);
            int wordIndex = line.findWordIndex(position);
            if (wordIndex < 0 || wordIndex >= line.words.size()) {
                return;
            }
            WordRange activeWord = line.words.get(wordIndex);
            float revealWidth = inactivePaint.measureText(line.text, 0, Math.max(0, Math.min(activeWord.start, line.text.length())));
            int start = Math.max(0, Math.min(activeWord.start, line.text.length()));
            int end = Math.max(start, Math.min(activeWord.end, line.text.length()));
            revealWidth += inactivePaint.measureText(line.text, start, end) * line.wordProgress(wordIndex, position);
            if (revealWidth <= 0f) {
                return;
            }
            float segmentWidth = inactivePaint.measureText(line.text);
            drawProgressGlow(canvas, line.text, 0, line.text.length(), textView.getPaddingLeft(), y, segmentWidth, revealWidth);
            drawRevealedText(canvas, line.text, 0, line.text.length(), textView.getPaddingLeft(), y, segmentWidth, revealWidth);
        }

        private void fitSingleLineText(TextView textView, String text, float availableWidth, float originalSize, float minimumSize) {
            float width = inactivePaint.measureText(text);
            if (width > availableWidth) {
                float fitted = Math.max(minimumSize, originalSize * availableWidth / Math.max(1f, width));
                inactivePaint.setTextSize(fitted);
                playedPaint.setTextSize(fitted);
                activePaint.setTextSize(fitted);
                activeGlowPaint.setTextSize(fitted);
                activeFeatherPaint.setTextSize(fitted);
                translationPaint.setTextSize(fitted * 0.70f);
            }
        }

        private void drawSegment(
                Canvas canvas,
                WordLine line,
                String text,
                LyricDrawLine drawLine,
                WordRange activeWord,
                int wordIndex,
                float x,
                float y,
                long position,
                boolean activeLine) {
            canvas.drawText(text, drawLine.start, drawLine.end, x, y, inactivePaint);
            if (!activeLine) {
                return;
            }

            float segmentWidth = inactivePaint.measureText(text, drawLine.start, drawLine.end);
            if (activeWord == null) {
                return;
            }

            float revealWidth;
            if (drawLine.end <= activeWord.start) {
                revealWidth = segmentWidth;
            } else if (drawLine.start >= activeWord.end) {
                revealWidth = 0f;
            } else {
                int wordStart = Math.max(drawLine.start, Math.min(activeWord.start, drawLine.end));
                int wordEnd = Math.max(wordStart, Math.min(activeWord.end, drawLine.end));
                float beforeWord = inactivePaint.measureText(text, drawLine.start, wordStart);
                float wordWidth = inactivePaint.measureText(text, wordStart, wordEnd);
                revealWidth = beforeWord + wordWidth * line.wordProgress(wordIndex, position);
            }
            if (revealWidth > 0f) {
                drawProgressGlow(canvas, text, drawLine.start, drawLine.end, x, y, segmentWidth, revealWidth);
                drawRevealedText(canvas, text, drawLine.start, drawLine.end, x, y, segmentWidth, revealWidth);
            }
        }

        private void drawProgressGlow(
                Canvas canvas,
                String text,
                int start,
                int end,
                float x,
                float y,
                float segmentWidth,
                float revealWidth) {
            if (TextUtils.isEmpty(text) || start >= end || segmentWidth <= 0f || revealWidth <= 0f) {
                return;
            }

            float visibleWidth = Math.max(0f, Math.min(segmentWidth, revealWidth));
            if (visibleWidth <= 0f) {
                return;
            }

            float segmentLeft = x;
            float segmentRight = x + segmentWidth;
            float revealRight = x + visibleWidth;
            float glowPad = Math.max(1f, activePaint.getTextSize() * 0.22f);
            float glowFeatherWidth = Math.max(4f, activePaint.getTextSize() * 0.95f);
            boolean fullyVisible = visibleWidth >= segmentWidth - 0.5f;
            float maskRight = fullyVisible
                    ? segmentRight + glowPad
                    : Math.min(segmentRight + glowPad, revealRight + glowFeatherWidth * 0.75f);
            float glowTextWidth = fullyVisible
                    ? segmentWidth
                    : Math.min(segmentWidth, visibleWidth + glowFeatherWidth * 0.40f);
            int glowEnd = textOffsetForWidth(text, start, end, glowTextWidth);
            if (glowEnd <= start && visibleWidth > 0f) {
                glowEnd = nextTextOffset(text, start, end);
            } else if (!fullyVisible && glowEnd < end) {
                glowEnd = nextTextOffset(text, glowEnd, end);
            }
            if (glowEnd <= start || maskRight <= segmentLeft - glowPad) {
                return;
            }

            int layer = canvas.saveLayer(
                    segmentLeft - glowPad,
                    0f,
                    maskRight,
                    canvas.getHeight(),
                    null);
            canvas.drawText(text, start, glowEnd, x, y, activeGlowPaint);

            activeGlowMaskPaint.setXfermode(dstInXfermode);
            if (fullyVisible) {
                activeGlowMaskPaint.setShader(null);
                activeGlowMaskPaint.setColor(0xFFFFFFFF);
            } else {
                float maskWidth = Math.max(1f, maskRight - segmentLeft);
                float settledRight = Math.max(segmentLeft, revealRight - glowFeatherWidth * 0.45f);
                float settledStop = Math.max(0f, Math.min(0.90f, (settledRight - segmentLeft) / maskWidth));
                float progressStop = Math.max(settledStop + 0.001f,
                        Math.min(0.985f, (revealRight - segmentLeft) / maskWidth));
                Shader alphaMaskShader = new LinearGradient(
                        segmentLeft,
                        0f,
                        maskRight,
                        0f,
                        new int[]{0xFF000000, 0xFF000000, 0xD8000000, 0x00000000},
                        new float[]{0f, settledStop, progressStop, 1f},
                        Shader.TileMode.CLAMP);
                activeGlowMaskPaint.setShader(alphaMaskShader);
            }
            canvas.drawRect(segmentLeft - glowPad, 0f, maskRight, canvas.getHeight(), activeGlowMaskPaint);
            activeGlowMaskPaint.setShader(null);
            activeGlowMaskPaint.setXfermode(null);
            canvas.restoreToCount(layer);
        }

        private int textOffsetForWidth(String text, int start, int end, float width) {
            if (width <= 0f) {
                return start;
            }
            if (inactivePaint.measureText(text, start, end) <= width) {
                return end;
            }
            int low = start;
            int high = end;
            while (low < high) {
                int mid = (low + high + 1) / 2;
                if (inactivePaint.measureText(text, start, mid) <= width) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            return Math.max(start, Math.min(end, low));
        }

        private static int nextTextOffset(String text, int start, int end) {
            if (start >= end) {
                return end;
            }
            if (start + 1 < end
                    && Character.isHighSurrogate(text.charAt(start))
                    && Character.isLowSurrogate(text.charAt(start + 1))) {
                return start + 2;
            }
            return start + 1;
        }

        private void drawRevealedText(
                Canvas canvas,
                String text,
                int start,
                int end,
                float x,
                float y,
                float segmentWidth,
                float revealWidth) {
            if (TextUtils.isEmpty(text) || start >= end || segmentWidth <= 0f || revealWidth <= 0f) {
                return;
            }

            float visibleWidth = Math.max(0f, Math.min(segmentWidth, revealWidth));
            if (visibleWidth <= 0f) {
                return;
            }

            float segmentLeft = x;
            float segmentRight = x + segmentWidth;
            float revealRight = x + visibleWidth;

            if (visibleWidth >= segmentWidth - 0.5f) {
                canvas.save();
                canvas.clipRect(segmentLeft, 0f, segmentRight, canvas.getHeight());
                canvas.drawText(text, start, end, x, y, activePaint);
                canvas.restore();
                return;
            }

            float featherWidth = Math.max(1f, activePaint.getTextSize() * ACTIVE_FEATHER_WIDTH_FACTOR);
            float featherStart = Math.max(segmentLeft, revealRight - featherWidth * 0.65f);
            float featherEnd = Math.min(segmentRight, revealRight + featherWidth * 0.85f);
            if (featherEnd <= segmentLeft) {
                return;
            }
            float maskWidth = Math.max(1f, featherEnd - segmentLeft);
            float solidStop = Math.max(0f, Math.min(0.96f, (featherStart - segmentLeft) / maskWidth));
            float progressStop = Math.max(solidStop + 0.001f,
                    Math.min(0.985f, (revealRight - segmentLeft) / maskWidth));
            Shader alphaMaskShader;
            if (solidStop <= 0.001f) {
                alphaMaskShader = new LinearGradient(
                        segmentLeft,
                        0f,
                        featherEnd,
                        0f,
                        new int[]{0xFF000000, 0x9A000000, 0x00000000},
                        new float[]{0f, progressStop, 1f},
                        Shader.TileMode.CLAMP);
            } else {
                alphaMaskShader = new LinearGradient(
                        segmentLeft,
                        0f,
                        featherEnd,
                        0f,
                        new int[]{0xFF000000, 0xFF000000, 0x9A000000, 0x00000000},
                        new float[]{0f, solidStop, progressStop, 1f},
                        Shader.TileMode.CLAMP);
            }

            Shader baseShader = new LinearGradient(
                    segmentLeft,
                    0f,
                    segmentRight,
                    0f,
                    activePaint.getColor(),
                    activePaint.getColor(),
                    Shader.TileMode.CLAMP);
            activeFeatherPaint.setShader(new ComposeShader(baseShader, alphaMaskShader, PorterDuff.Mode.DST_IN));
            canvas.save();
            canvas.clipRect(segmentLeft, 0f, featherEnd, canvas.getHeight());
            canvas.drawText(text, start, end, x, y, activeFeatherPaint);
            canvas.restore();
            activeFeatherPaint.setShader(null);
        }

        private void buildDrawLines(String text, float availableWidth, boolean singleLine) {
            drawLines.clear();
            if (TextUtils.isEmpty(text)) {
                return;
            }

            int textStart = firstNonSpace(text, 0, text.length());
            int textEnd = lastNonSpace(text, textStart, text.length());
            if (textStart >= textEnd) {
                return;
            }

            if (singleLine
                    || inactivePaint.measureText(text, textStart, textEnd) <= availableWidth
                    || !textContainsSpace(text, textStart, textEnd)) {
                drawLines.add(new LyricDrawLine(textStart, textEnd));
                return;
            }

            int lineStart = textStart;
            while (lineStart < textEnd && drawLines.size() < MAX_DRAW_LINES) {
                int lineEnd = chooseWrapEnd(text, lineStart, textEnd, availableWidth);
                if (drawLines.size() == MAX_DRAW_LINES - 1 || lineEnd <= lineStart) {
                    lineEnd = textEnd;
                }
                int cleanEnd = lastNonSpace(text, lineStart, lineEnd);
                if (lineStart < cleanEnd) {
                    drawLines.add(new LyricDrawLine(lineStart, cleanEnd));
                }
                lineStart = firstNonSpace(text, lineEnd, textEnd);
            }
        }

        private int chooseWrapEnd(String text, int start, int end, float availableWidth) {
            int bestEnd = -1;
            int index = start;
            while (index < end) {
                while (index < end && !Character.isWhitespace(text.charAt(index))) {
                    index++;
                }
                int candidateEnd = lastNonSpace(text, start, index);
                if (candidateEnd > start) {
                    float width = inactivePaint.measureText(text, start, candidateEnd);
                    if (width <= availableWidth) {
                        bestEnd = candidateEnd;
                    } else {
                        break;
                    }
                }
                index = firstNonSpace(text, index, end);
            }
            return bestEnd > start ? bestEnd : end;
        }

        private int chooseBalancedSplit(String text, int start, int end, float availableWidth) {
            float bestScore = Float.MAX_VALUE;
            int bestSplit = -1;
            for (int i = start + 1; i < end - 1; i++) {
                if (!Character.isWhitespace(text.charAt(i))) {
                    continue;
                }
                int leftEnd = lastNonSpace(text, start, i);
                int rightStart = firstNonSpace(text, i + 1, end);
                if (leftEnd <= start || rightStart >= end) {
                    continue;
                }
                float leftWidth = inactivePaint.measureText(text, start, leftEnd);
                float rightWidth = inactivePaint.measureText(text, rightStart, end);
                float maxWidth = Math.max(leftWidth, rightWidth);
                float overflowPenalty = Math.max(0f, maxWidth - availableWidth) * 4f;
                float balancePenalty = Math.abs(leftWidth - rightWidth) * 0.7f;
                float score = overflowPenalty + balancePenalty + maxWidth;
                if (score < bestScore) {
                    bestScore = score;
                    bestSplit = i + 1;
                }
            }
            return bestSplit;
        }

        private void fitTextSizeForBounds(
                TextView textView, String text, float availableWidth, float availableHeight, float originalSize) {
            float fittedSize = inactivePaint.getTextSize();
            float maxWidth = 0f;
            for (LyricDrawLine drawLine : drawLines) {
                maxWidth = Math.max(maxWidth, inactivePaint.measureText(text, drawLine.start, drawLine.end));
            }
            if (maxWidth > availableWidth) {
                fittedSize = Math.min(fittedSize, fittedSize * availableWidth / Math.max(1f, maxWidth));
            }

            Paint.FontMetrics metrics = inactivePaint.getFontMetrics();
            float lineHeight = metrics.descent - metrics.ascent;
            float lineGap = drawLines.size() > 1 ? dp(textView.getContext(), 3f) : 0f;
            float totalHeight = lineHeight * drawLines.size() + lineGap * (drawLines.size() - 1);
            if (totalHeight > availableHeight) {
                fittedSize = Math.min(fittedSize, fittedSize * availableHeight / Math.max(1f, totalHeight));
            }

            fittedSize = Math.max(sp(textView.getContext(), 10f), Math.min(originalSize, fittedSize));
            if (Math.abs(fittedSize - inactivePaint.getTextSize()) > 0.2f) {
                setTextSize(fittedSize);
            }
        }

        private static boolean textContainsSpace(String text, int start, int end) {
            for (int i = start; i < end; i++) {
                if (Character.isWhitespace(text.charAt(i))) {
                    return true;
                }
            }
            return false;
        }

        private static int firstNonSpace(String text, int start, int end) {
            int index = Math.max(0, start);
            int limit = Math.min(text.length(), end);
            while (index < limit && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            return index;
        }

        private static int lastNonSpace(String text, int start, int end) {
            int index = Math.min(text.length(), end);
            int limit = Math.max(0, start);
            while (index > limit && Character.isWhitespace(text.charAt(index - 1))) {
                index--;
            }
            return index;
        }

        private static boolean isCompactLyricSlot(TextView textView, float availableHeight) {
            return availableHeight <= dp(textView.getContext(), 52f);
        }

        private void configurePaints(TextView textView, boolean compactSlot) {
            Typeface typeface = textView.getTypeface();
            inactivePaint.setTypeface(typeface);
            playedPaint.setTypeface(typeface);
            activePaint.setTypeface(typeface);
            activeGlowPaint.setTypeface(typeface);
            activeFeatherPaint.setTypeface(typeface);
            translationPaint.setTypeface(typeface);
            if (compactSlot) {
                setTextSize(Math.max(sp(textView.getContext(), 10f), textView.getTextSize()));
            } else {
                setTextSize(sp(textView.getContext(), MAIN_TEXT_SIZE_SP));
            }
            setPaintEffects(textView.getContext());
        }

        private void setTextSize(float size) {
            inactivePaint.setTextSize(size);
            playedPaint.setTextSize(size);
            activePaint.setTextSize(size);
            activeGlowPaint.setTextSize(size);
            activeFeatherPaint.setTextSize(size);
            translationPaint.setTextSize(size * 0.66f);
        }

        private void setPaintEffects(Context context) {
            clearPaintEffects(inactivePaint);
            clearPaintEffects(playedPaint);
            clearPaintEffects(activePaint);
            clearPaintEffects(activeGlowPaint);
            clearPaintEffects(activeFeatherPaint);
            clearPaintEffects(translationPaint);

            float glowRadius = Math.max(dp(context, 2f),
                    Math.min(dp(context, 5f), activePaint.getTextSize() * ACTIVE_GLOW_RADIUS_FACTOR));
            activeGlowPaint.setShadowLayer(glowRadius, 0f, 0f, ACTIVE_GLOW_SHADOW_COLOR);
        }

        private static void clearPaintEffects(Paint paint) {
            paint.clearShadowLayer();
            paint.setShader(null);
            paint.setMaskFilter(null);
            paint.setColorFilter(null);
        }

        private float resolveTextX(TextView textView, float measuredWidth, float availableWidth) {
            return textView.getPaddingLeft();
        }

        private void applyFade(float fade) {
            applyFade(fade, 0f);
        }

        private void applyFade(float fade, float focusAmount) {
            float amount = Math.max(0f, Math.min(1f, focusAmount));
            inactivePaint.setColor(scaleAlpha(blendColor(INACTIVE_COLOR, FOCUSED_INACTIVE_COLOR, amount), fade));
            playedPaint.setColor(scaleAlpha(PLAYED_COLOR, fade));
            activePaint.setColor(scaleAlpha(ACTIVE_COLOR, fade));
            activeGlowPaint.setColor(scaleAlpha(ACTIVE_GLOW_FILL_COLOR, fade));
            activeFeatherPaint.setColor(scaleAlpha(ACTIVE_COLOR, fade));
            translationPaint.setColor(scaleAlpha(blendColor(TRANSLATION_COLOR, FOCUSED_TRANSLATION_COLOR, amount), fade));
            activeBackgroundPaint.setColor(scaleAlpha(ACTIVE_BACKGROUND_COLOR, fade));
            progressBackgroundPaint.setColor(scaleAlpha(PROGRESS_BACKGROUND_COLOR, fade));
        }

        private static int blendColor(int fromColor, int toColor, float amount) {
            float progress = Math.max(0f, Math.min(1f, amount));
            int fromA = (fromColor >>> 24) & 0xFF;
            int fromR = (fromColor >>> 16) & 0xFF;
            int fromG = (fromColor >>> 8) & 0xFF;
            int fromB = fromColor & 0xFF;
            int toA = (toColor >>> 24) & 0xFF;
            int toR = (toColor >>> 16) & 0xFF;
            int toG = (toColor >>> 8) & 0xFF;
            int toB = toColor & 0xFF;
            int a = Math.round(fromA + (toA - fromA) * progress);
            int r = Math.round(fromR + (toR - fromR) * progress);
            int g = Math.round(fromG + (toG - fromG) * progress);
            int b = Math.round(fromB + (toB - fromB) * progress);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static int scaleAlpha(int color, float scale) {
            int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, scale)));
            return (color & 0x00FFFFFF) | (alpha << 24);
        }

        private static final class LyricDrawLine {
            final int start;
            final int end;

            LyricDrawLine(int start, int end) {
                this.start = start;
                this.end = end;
            }
        }

        private static final class TranslatedLineWindow {
            final int currentStart;
            final int previousStart;
            final int count;
            final float progress;
            final float alphaProgress;
            final boolean animating;

            TranslatedLineWindow(
                    int currentStart,
                    int previousStart,
                    int count,
                    float progress,
                    float alphaProgress,
                    boolean animating) {
                this.currentStart = currentStart;
                this.previousStart = previousStart;
                this.count = count;
                this.progress = progress;
                this.alphaProgress = alphaProgress;
                this.animating = animating;
            }
        }

    }

    private static final class OplusMediaWhitelistBypassList extends AbstractList<String> {
        private final List<?> delegate;

        OplusMediaWhitelistBypassList(List<?> delegate) {
            this.delegate = delegate == null ? Collections.emptyList() : delegate;
        }

        @Override
        public String get(int index) {
            if (delegate.isEmpty() && index == 0) {
                return "*";
            }
            Object value = delegate.get(index);
            return value == null ? null : value.toString();
        }

        @Override
        public int size() {
            return delegate.isEmpty() ? 1 : delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(Object object) {
            if (object instanceof String && !TextUtils.isEmpty((String) object)) {
                return true;
            }
            return delegate.contains(object);
        }

        @Override
        public int indexOf(Object object) {
            int index = delegate.indexOf(object);
            if (index >= 0) {
                return index;
            }
            return contains(object) ? 0 : -1;
        }

        @Override
        public String toString() {
            return delegate + " + whitelistBypass";
        }
    }

    private static final class CachedLyric {
        final String source;
        final String lyric;
        final String rawLyric;
        final long createdAtMillis;

        CachedLyric(String source, String lyric, String rawLyric, long createdAtMillis) {
            this.source = source;
            this.lyric = lyric;
            this.rawLyric = rawLyric;
            this.createdAtMillis = createdAtMillis;
        }
    }

    private static final class CurrentLyricFrame {
        final long position;
        final String line;

        CurrentLyricFrame(long position, String line) {
            this.position = position;
            this.line = line;
        }
    }

    private static final class LyricTextMatch {
        static final LyricTextMatch EMPTY = new LyricTextMatch(null, null);

        final WordLine line;
        final WordLine translationLine;

        LyricTextMatch(WordLine line, WordLine translationLine) {
            this.line = line;
            this.translationLine = translationLine;
        }
    }

    private static final class DrawFrame {
        final WordLyricModel model;
        final WordLine line;
        final int lineIndex;
        final int activeIndex;
        final long position;
        final boolean active;
        final boolean focused;
        final boolean skipOriginal;

        DrawFrame(
                WordLyricModel model,
                WordLine line,
                int lineIndex,
                int activeIndex,
                long position,
                boolean active,
                boolean focused,
                boolean skipOriginal) {
            this.model = model;
            this.line = line;
            this.lineIndex = lineIndex;
            this.activeIndex = activeIndex;
            this.position = position;
            this.active = active;
            this.focused = focused;
            this.skipOriginal = skipOriginal;
        }
    }

    private static final class WordLyricModel {
        final ArrayList<WordLine> lines = new ArrayList<>();

        WordLine findLine(long position, String currentLine) {
            String normalizedCurrent = normalizeLine(currentLine);
            WordLine fallback = null;
            for (WordLine line : lines) {
                if (!TextUtils.isEmpty(normalizedCurrent)
                        && matchesLyricText(line.text, normalizedCurrent)) {
                    return line;
                }
                if (line.timeMillis <= position) {
                    fallback = line;
                }
            }
            return fallback;
        }

        WordLine findActiveLine(long position) {
            WordLine fallback = null;
            for (WordLine line : lines) {
                if (line.timeMillis <= position) {
                    fallback = line;
                } else {
                    break;
                }
            }
            return fallback;
        }

        WordLine lineAt(int index) {
            return index >= 0 && index < lines.size() ? lines.get(index) : null;
        }

        int indexOfLine(WordLine target) {
            if (target == null) {
                return -1;
            }
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i) == target) {
                    return i;
                }
            }
            return -1;
        }

        WordLine findLineAtTime(long timeMillis) {
            if (timeMillis < 0) {
                return null;
            }
            for (WordLine line : lines) {
                if (line.timeMillis == timeMillis) {
                    return line;
                }
            }
            return null;
        }

        WordLine findLineByText(String normalizedText) {
            return findLineByText(normalizedText, -1L);
        }

        WordLine findLineByText(String normalizedText, long position) {
            if (TextUtils.isEmpty(normalizedText)) {
                return null;
            }
            WordLine best = null;
            long bestDistance = Long.MAX_VALUE;
            for (WordLine line : lines) {
                if (matchesLyricText(line.text, normalizedText)) {
                    if (position < 0) {
                        return line;
                    }
                    long distance = Math.abs(line.timeMillis - position);
                    if (best == null || distance < bestDistance) {
                        best = line;
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        boolean hasRenderableText(String normalizedText) {
            if (TextUtils.isEmpty(normalizedText)) {
                return false;
            }
            for (WordLine line : lines) {
                if (matchesLyricText(line.text, normalizedText)) {
                    return true;
                }
                if (!TextUtils.isEmpty(line.translation)
                        && normalizeLine(line.translation).equals(normalizedText)) {
                    return true;
                }
            }
            return false;
        }

        WordLine findLineByTextNearIndex(
                String normalizedText, int index, int radius, boolean requireTranslation) {
            if (TextUtils.isEmpty(normalizedText) || index < 0 || lines.isEmpty()) {
                return null;
            }
            int anchor = Math.max(0, Math.min(index, lines.size() - 1));
            int start = Math.max(0, anchor - Math.max(0, radius));
            int end = Math.min(lines.size() - 1, anchor + Math.max(0, radius));
            WordLine best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = start; i <= end; i++) {
                WordLine line = lines.get(i);
                if (!matchesLyricText(line.text, normalizedText)) {
                    continue;
                }
                if (requireTranslation && TextUtils.isEmpty(line.translation)) {
                    continue;
                }
                int distance = Math.abs(i - anchor);
                if (best == null || distance < bestDistance) {
                    best = line;
                    bestDistance = distance;
                }
            }
            return best;
        }

        WordLine findLineByTranslation(String normalizedText) {
            return findLineByTranslation(normalizedText, -1L);
        }

        WordLine findLineByTranslation(String normalizedText, long position) {
            if (TextUtils.isEmpty(normalizedText)) {
                return null;
            }
            WordLine best = null;
            long bestDistance = Long.MAX_VALUE;
            for (WordLine line : lines) {
                if (!TextUtils.isEmpty(line.translation)
                        && normalizeLine(line.translation).equals(normalizedText)) {
                    if (position < 0) {
                        return line;
                    }
                    long distance = Math.abs(line.timeMillis - position);
                    if (best == null || distance < bestDistance) {
                        best = line;
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        WordLine findLineByTranslationNearIndex(String normalizedText, int index, int radius) {
            if (TextUtils.isEmpty(normalizedText) || index < 0 || lines.isEmpty()) {
                return null;
            }
            int anchor = Math.max(0, Math.min(index, lines.size() - 1));
            int start = Math.max(0, anchor - Math.max(0, radius));
            int end = Math.min(lines.size() - 1, anchor + Math.max(0, radius));
            WordLine best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = start; i <= end; i++) {
                WordLine line = lines.get(i);
                if (TextUtils.isEmpty(line.translation)
                        || !normalizeLine(line.translation).equals(normalizedText)) {
                    continue;
                }
                int distance = Math.abs(i - anchor);
                if (best == null || distance < bestDistance) {
                    best = line;
                    bestDistance = distance;
                }
            }
            return best;
        }

        int translationCount() {
            int count = 0;
            for (WordLine line : lines) {
                if (!TextUtils.isEmpty(line.translation)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class WordLine {
        final long timeMillis;
        final long endTimeMillis;
        final String text;
        final ArrayList<WordRange> words;
        String translation = "";
        int translatedWindowWidthKey = -1;
        int translatedWindowStart;
        int translatedWindowPreviousStart;
        long translatedWindowChangedAtMs;
        int focusedVisualActiveIndex = Integer.MIN_VALUE;
        long focusedVisualStartElapsedMs;

        WordLine(long timeMillis, String text, ArrayList<WordRange> words) {
            this(timeMillis, text, words, inferWordLineEndMillis(timeMillis, words));
        }

        WordLine(long timeMillis, String text, ArrayList<WordRange> words, long endTimeMillis) {
            this.timeMillis = timeMillis;
            this.endTimeMillis = Math.max(timeMillis, endTimeMillis);
            this.text = text;
            this.words = words;
        }

        WordRange findWord(long position) {
            int index = findWordIndex(position);
            return index >= 0 ? words.get(index) : null;
        }

        int findWordIndex(long position) {
            int fallback = -1;
            for (int i = 0; i < words.size(); i++) {
                WordRange word = words.get(i);
                if (word.timeMillis <= position) {
                    fallback = i;
                } else {
                    break;
                }
            }
            return fallback >= 0 ? fallback : words.isEmpty() ? -1 : 0;
        }

        long delayToNextWordMillis(long position) {
            for (WordRange word : words) {
                if (word.timeMillis > position) {
                    return Math.max(40L, word.timeMillis - position + 16L);
                }
            }
            return 220L;
        }

        long wordEndMillis(int index) {
            if (index < 0 || index >= words.size()) {
                return timeMillis + 600L;
            }
            long begin = words.get(index).timeMillis;
            if (index + 1 < words.size()) {
                return Math.max(begin + 80L, words.get(index + 1).timeMillis);
            }
            return Math.max(begin + 80L, endTimeMillis);
        }

        float wordProgress(int index, long position) {
            if (index < 0 || index >= words.size()) {
                return 0f;
            }
            long begin = words.get(index).timeMillis;
            long end = wordEndMillis(index);
            if (position <= begin) {
                return 0f;
            }
            if (position >= end) {
                return 1f;
            }
            return (float) (position - begin) / (float) Math.max(1L, end - begin);
        }
    }

    private static final class WordRange {
        final long timeMillis;
        final int start;
        final int end;

        WordRange(long timeMillis, int start, int end) {
            this.timeMillis = timeMillis;
            this.start = start;
            this.end = end;
        }
    }

    private static final class NormalizedWordLineText {
        final String text;
        final ArrayList<WordRange> words;

        NormalizedWordLineText(String text, ArrayList<WordRange> words) {
            this.text = text;
            this.words = words;
        }
    }

    private static final class TagMatch {
        final int start;
        final int end;
        final long timeMillis;

        TagMatch(int start, int end, long timeMillis) {
            this.start = start;
            this.end = end;
            this.timeMillis = timeMillis;
        }
    }

    private static final class PlainLyricLine {
        final long timeMillis;
        final String text;

        PlainLyricLine(long timeMillis, String text) {
            this.timeMillis = timeMillis;
            this.text = text;
        }
    }

    private static boolean matchesLyricText(String fullText, String normalizedText) {
        if (TextUtils.isEmpty(normalizedText)) {
            return false;
        }
        String normalizedFullText = normalizeLine(fullText);
        if (normalizedFullText.equals(normalizedText)) {
            return true;
        }
        return isLyricPrefixMatch(normalizedText, normalizedFullText);
    }

    private static boolean isLyricPrefixMatch(String visibleText, String fullText) {
        if (TextUtils.isEmpty(visibleText) || TextUtils.isEmpty(fullText) || visibleText.length() < 5) {
            return false;
        }
        if (fullText.startsWith(visibleText)) {
            return true;
        }

        String visibleKey = lyricMatchKey(visibleText);
        String fullKey = lyricMatchKey(fullText);
        return visibleKey.length() >= 5 && fullKey.startsWith(visibleKey);
    }

    private static String lyricMatchKey(String text) {
        String normalized = normalizeLine(text).toLowerCase(Locale.ROOT);
        StringBuilder key = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                key.append(ch);
            }
        }
        return key.toString();
    }

    private static String normalizeLine(String line) {
        return line == null ? "" : line.trim().replaceAll("[ \\t]{2,}", " ");
    }
}
