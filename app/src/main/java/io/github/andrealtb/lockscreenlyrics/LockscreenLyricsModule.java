package io.github.andrealtb.lockscreenlyrics;

import android.app.KeyguardManager;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Binder;
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
import android.widget.ImageView;
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
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class LockscreenLyricsModule extends XposedModule {
    private static final String TAG = "LockscreenLyrics";
    private static final String MODULE_PACKAGE = "io.github.andrealtb.lockscreenlyrics";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String SALT_PLAYER_PACKAGE = "com.salt.music";
    private static final String OPLUS_MEDIA_CONTROL_SERVICE_CLASS =
            "com.android.server.media.OplusMediaControlService";
    private static final String OPLUS_HISTORY_WHITELIST_METHOD =
            "isInHistoryPlayInfoWhiteList";
    private static final String LYRICS_RECYCLER_VIEW_CLASS =
            "com.oplus.systemui.plugins.shared.template.component.media.view.LyricsRecyclerView";
    private static final String OPLUS_LYRIC_INFO_KEY = LyricInfoContract.METADATA_KEY;
    private static final String OPLUS_RAW_LYRIC_INFO_KEY = LyricInfoContract.JSON_RAW_LYRIC;
    private static final String HOOK_ID_SET_METADATA = "lockscreen-lyrics-set-metadata";
    private static final String HOOK_ID_SET_PLAYBACK_STATE_TRANSLATION_ACTION =
            "lockscreen-lyrics-set-playback-state-translation-action";
    private static final String HOOK_ID_SYSTEMUI_LOAD_LYRIC = "oplus-word-load-lyric";
    private static final String HOOK_ID_SEEDLING_MEDIA_BUNDLE = "oplus-word-seedling-media-bundle";
    private static final String HOOK_ID_TEXTVIEW_ON_DRAW = "oplus-word-textview-on-draw";
    private static final String HOOK_ID_VIEW_ON_ATTACHED = "oplus-word-view-on-attached";
    private static final String HOOK_ID_VIEW_ON_DETACHED = "oplus-word-view-on-detached";
    private static final String HOOK_ID_VIEW_SET_CONTENT_DESCRIPTION =
            "oplus-translation-button-content-description";
    private static final String HOOK_ID_VIEW_SET_VISIBILITY =
            "oplus-translation-button-visibility";
    private static final String HOOK_ID_IMAGE_VIEW_SET_IMAGE_DRAWABLE =
            "oplus-translation-button-image-drawable";
    private static final String HOOK_ID_IMAGE_VIEW_SET_IMAGE_BITMAP =
            "oplus-translation-button-image-bitmap";
    private static final String HOOK_ID_CLASS_LOADER_LOAD_CLASS = "oplus-word-classloader-load-class";
    private static final String HOOK_ID_LYRICS_RECYCLER = "oplus-word-lyrics-recycler";
    private static final String HOOK_ID_SYSTEMUI_LOG_I = "oplus-lyric-ui-mode-log-i";
    private static final String HOOK_ID_SYSTEMUI_LOG_PRINTLN = "oplus-lyric-ui-mode-log-println";
    private static final String HOOK_ID_RUS_DEAL_END_TAG = "oplus-media-rus-deal-end-tag";
    private static final String HOOK_ID_RUS_SAVE_LIST_TO_SP = "oplus-media-rus-save-list";
    private static final String HOOK_ID_RUS_GET_WHITE_LIST = "oplus-media-rus-get-white-list";
    private static final String HOOK_ID_GET_LYRIC_ENTRANCE = "oplus-media-get-lyric-entrance";
    private static final String HOOK_ID_UPDATE_PKG_ACTIONS_RULE = "oplus-media-update-pkg-actions-rule";
    private static final String HOOK_ID_TRANSLATION_TOGGLE_ACTION =
            "oplus-media-translation-toggle-action";
    private static final String HOOK_ID_OPLUS_HISTORY_WHITELIST =
            "oplus-media-history-whitelist-salt";
    private static final String OPLUS_MEDIA_RUS_TAG_WHITELIST = "whitelist";
    private static final String SALT_DESKTOP_LYRIC_ACTION = "com.salt.music.desktop_lyrics";
    private static final String TRANSLATION_TOGGLE_ACTION =
            LyricInfoContract.ACTION_TOGGLE_TRANSLATION;
    private static final String TRANSLATION_ICON_RESOURCE_NAME = "ic_translation";
    private static final String TRANSLATION_PREFERENCES_NAME = "lockscreen_lyrics";
    private static final String TRANSLATION_PREFERENCE_KEY = "lyric_info_translation_enabled";
    private static final String TRANSLATION_ACTION_DESCRIPTION_PREFIX =
            "\u7ffb\u8bd1\uff1a";
    private static final String TRANSLATION_ACTION_NAME = "\u7ffb\u8bd1";
    private static final int TRANSLATION_ICON_FINGERPRINT_SIZE = 48;
    private static final int OPLUS_LYRIC_ENTRANCE_ALL = 52;
    private static final long LYRIC_CACHE_MAX_AGE_MS = 5 * 60 * 1000L;
    private static final int TRACK_LYRIC_CACHE_MAX_ENTRIES = 24;
    private static final long SALT_STALE_FALLBACK_CONFIRM_WINDOW_MS = 8_000L;
    private static final long SCREEN_TIMEOUT_USER_ACTIVITY_INTERVAL_MS = 8_000L;
    private static final long SCREEN_TIMEOUT_WAKE_LOCK_LEASE_MS = 15_000L;
    private static final long SCREEN_TIMEOUT_VISIBLE_LYRIC_VIEW_MAX_AGE_MS = 12_000L;
    private static final long SCREEN_TIMEOUT_MODEL_EVIDENCE_GRACE_MS = 3_000L;
    private static final long SCREEN_TIMEOUT_USER_PRESENT_RECHECK_DELAY_MS = 500L;
    private static final long LYRIC_UI_TRANSITION_GLOW_FREEZE_MS = 900L;
    private static final long SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS = 1_400L;
    private static final float SYSTEMUI_LYRIC_HANDOFF_HIDDEN_ALPHA = 0.001f;
    private static final float SYSTEMUI_LYRIC_VISIBLE_ALPHA = 1f;
    private static final long SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS = 420L;
    private static final long SYSTEMUI_LYRIC_ROW_REBIND_WINDOW_MS = 1_800L;
    private static final long ACTIVE_LYRIC_FRAME_DELAY_MS = 16L;
    private static final long ACTIVE_LYRIC_RETRY_DELAY_MS = 48L;
    private static final long LYRIC_VISIBILITY_RECOVERY_FIRST_DELAY_MS = 96L;
    private static final long LYRIC_VISIBILITY_RECOVERY_SECOND_DELAY_MS = 240L;
    private static final long LYRIC_VISIBILITY_RECOVERY_FINAL_DELAY_MS = 520L;
    private static final float LYRIC_SLOT_HEIGHT_DP = 80f;
    private static final float OFFICIAL_LYRIC_LINE_SPACING_DP = 6f;
    private static final float ACTIVE_LYRIC_CENTER_OFFSET_DP = 48f;
    private static final String OFFICIAL_LYRIC_LINE_SPACING_FIELD = "u";
    private static final Pattern LRC_TIME_TAG = Pattern.compile("\\[[0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?\\]");
    private static final Pattern ANY_LRC_TIME_TAG = Pattern.compile("[\\[<]([0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?)[\\]>]");
    private static final PlayerAdapter[] PLAYER_ADAPTERS = {
            new SaltPlayerAdapter(),
            new ConePlayerAdapter("ink.trantor.coneplayer"),
            new ConePlayerAdapter("ink.trantor.coneplayer.gp")
    };

    private final LyricSessionReducer playerLyricSession =
            new LyricSessionReducer(LYRIC_CACHE_MAX_AGE_MS, TRACK_LYRIC_CACHE_MAX_ENTRIES);
    private volatile MediaSession lastSession;
    private volatile MediaMetadata lastMetadata;
    private volatile PlayerAdapter hookedPlayerAdapter;
    private volatile long lastLyricRelayLogAt;
    private volatile String pendingSaltFallbackClearTrackKey = "";
    private volatile long pendingSaltFallbackClearAtMillis = -1L;
    private volatile WordLyricModel currentWordLyricModel;
    private volatile String currentWordLyricModelSignature = "";
    private volatile long lastTextViewSpanLogAt;
    private volatile long lastTextViewDrawLogAt;
    private volatile long lastRecyclerLogAt;
    private volatile long lastRecyclerScrollStabilizeLogAt;
    private volatile long lastOfficialLyricPayloadLogAt;
    private volatile long lastLyricLayoutDiagnosticsLogAt;
    private volatile long lastActiveRefreshLogAt;
    private volatile long lastSeedlingPlaybackStateLogAt;
    private volatile long lastComputedPositionMs = -1L;
    private volatile long lastComputedPositionElapsedMs = -1L;
    private volatile long lastSeedlingActiveLineTimeMs = -1L;
    private volatile long lastSeedlingActiveLineObservedAtMs = -1L;
    private volatile boolean lastSystemUiPackageSupported;
    private volatile String currentLyricProviderPackage = "";
    private volatile LyricInfoContract.Payload currentLyricProviderPayload;
    private volatile boolean lastPlaybackIsPlaying = true;
    private volatile float lastPlaybackSpeed = 1f;
    private volatile int lastSystemUiPlaybackState = -1;
    private volatile int lastLoggedSystemUiPlaybackState = -100;
    private volatile String lastSystemUiSongName = "";
    private volatile String lastSystemUiArtistName = "";
    private volatile boolean systemUiHasOfficialLyric;
    private volatile SystemUiDexKitAdapter.Targets systemUiDexKitTargets;
    private volatile boolean oplusMediaPolicyHooksInstalled;
    private volatile boolean oplusHistoryWhitelistHookInstalled;
    private final Set<String> loggedOplusHistoryIntegrationPackages =
            ConcurrentHashMap.newKeySet();
    private final Set<String> loggedOplusHistoryManifestFailures =
            ConcurrentHashMap.newKeySet();
    private volatile boolean translationToggleActionHookInstalled;
    private volatile boolean injectedTranslationToggleActionHookInstalled;
    private volatile boolean injectedTranslationToggleActionLogged;
    private volatile boolean injectedTranslationToggleActionFailureLogged;
    private volatile Object oplusMediaActionPrioritySelector;
    private volatile Method oplusUpdatePkgActionsRuleMethod;
    private volatile Object[] lastOplusPkgActionsRuleArgs;
    private final Set<String> translationToggleRule0Packages =
            ConcurrentHashMap.newKeySet();
    private final Set<String> pendingTranslationToggleRule0Packages =
            ConcurrentHashMap.newKeySet();
    private final Set<String> refreshedTranslationToggleRule0Packages =
            ConcurrentHashMap.newKeySet();
    private volatile boolean translationPreferenceLoaded;
    private volatile boolean lyricInfoTranslationEnabled = true;
    private volatile boolean screenTimeoutReceiverRegistered;
    private volatile boolean systemUiLyricModeEnabled;
    private volatile boolean systemUiLyricModeKeepAwakeActive;
    private volatile long lyricUiTransitionGlowFrozenUntilElapsedMs;
    private volatile long lyricUiTransitionFrozenGlowPositionMs = -1L;
    private volatile int lyricModeRebindGeneration;
    private volatile long officialLyricDrawSuppressedUntilElapsedMs;
    private volatile int officialLyricHandoffGeneration;
    private volatile boolean lyricModelReplacementInProgress;
    private volatile boolean pendingCustomLyricTakeoverFade;
    private volatile long lyricTrackRowRebindEligibleUntilElapsedMs;
    private volatile long lyricRecyclerFadeInUntilElapsedMs;
    private volatile int lyricRecyclerFadeGeneration;
    private final Object suppressedLyricsRecyclerAlphasLock = new Object();
    private final WeakHashMap<View, Float> suppressedLyricsRecyclerAlphas =
            new WeakHashMap<>();
    private volatile long lastSystemUiLyricModeLogAt;
    private volatile long lastSystemUiLyricModeStateLogAt;
    private volatile long lastScreenTimeoutLogAt;
    private volatile long lastVisibleOfficialLyricTextViewAt;
    private volatile boolean screenTimeoutUserActivityPulsePosted;
    private volatile boolean screenTimeoutUserPresentRecheckPosted;
    private volatile boolean screenTimeoutUserActivityFailureLogged;
    private volatile boolean screenTimeoutPausedByScreenOff;
    private volatile boolean screenTimeoutPausedByUserPresent;
    private volatile long screenTimeoutLyricEvidenceGraceUntilElapsedMs;
    private BroadcastReceiver screenTimeoutReceiver;
    private PowerManager.WakeLock screenTimeoutWakeLock;
    private PowerManager screenTimeoutPowerManager;
    private volatile int lastLyricsRecyclerIndex = -1;
    private volatile boolean lyricsRecyclerHookInstalled;
    private volatile boolean lyricsRecyclerSetCurrentUnavailable;
    private final Object lyricsRecyclerViewsLock = new Object();
    private final ArrayList<WeakReference<View>> lyricsRecyclerViews = new ArrayList<>();
    private WeakReference<View> lastPrimedLyricsRecyclerView = new WeakReference<>(null);
    private int lastPrimedLyricsRecyclerIndex = -1;
    private final Object activeLyricTextViewsLock = new Object();
    private final ArrayList<WeakReference<TextView>> activeLyricTextViews = new ArrayList<>();
    private final ArrayList<TextView> activeLyricRefreshCandidates = new ArrayList<>(8);
    private final WeakHashMap<TextView, NormalizedTextSnapshot> normalizedLyricTextCache =
            new WeakHashMap<>();
    private WeakReference<TextView> activeRendererTextView = new WeakReference<>(null);
    private WordLine activeRendererWordLine;
    private final Object lyricRootViewsLock = new Object();
    private final ArrayList<WeakReference<View>> lyricRootViews = new ArrayList<>();
    private final Object translationActionViewsLock = new Object();
    private final WeakHashMap<View, Boolean> translationActionViews = new WeakHashMap<>();
    private final WeakHashMap<Bitmap, Boolean> translationBitmapMatchCache = new WeakHashMap<>();
    private final WeakHashMap<Drawable, Boolean> translationDrawableMatchCache =
            new WeakHashMap<>();
    private final WeakHashMap<View, Long> translationRootLastScanAt = new WeakHashMap<>();
    private static final Object VIEW_VISUAL_EFFECT_CACHE_LOCK = new Object();
    private static final WeakHashMap<Class<?>, Method[]> VIEW_BLUR_METHOD_CACHE =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> VIEW_BLUR_DISABLED = new WeakHashMap<>();
    private static final Object RECYCLER_POSITION_METHOD_CACHE_LOCK = new Object();
    private static final WeakHashMap<Class<?>, Method> RECYCLER_POSITION_METHOD_CACHE =
            new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Boolean> RECYCLER_POSITION_METHOD_MISSING =
            new WeakHashMap<>();
    private static final ThreadLocal<Rect> VIEW_VISIBLE_RECT =
            ThreadLocal.withInitial(Rect::new);
    private volatile byte[] translationIconAlphaFingerprint;
    private volatile String activeLyricLine = "";
    private volatile long activeLyricLineTimeMs = -1L;
    private volatile boolean activeLyricUpdatePosted;
    private volatile boolean lyricVisibilityRecoveryPosted;
    private volatile long lastLyricVisibilityRecoveryLogAt;
    private final ThreadLocal<Boolean> suppressLyricsRecyclerHook = new ThreadLocal<>();
    private final OfficialLyricTextRenderer officialLyricTextRenderer = new OfficialLyricTextRenderer();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable activeLyricRefreshRunnable = () -> {
        activeLyricUpdatePosted = false;
        refreshActiveLyricTextView();
    };
    private final Runnable screenTimeoutUserActivityPulse = new Runnable() {
        @Override
        public void run() {
            screenTimeoutUserActivityPulsePosted = false;
            if (!shouldHoldScreenTimeoutWakeLock(currentApplicationContext())) {
                releaseScreenTimeoutWakeLock("conditions changed");
                return;
            }
            PowerManager powerManager = screenTimeoutPowerManager;
            if (powerManager != null) {
                renewScreenTimeoutWakeLockLease(powerManager);
                pulseScreenTimeoutUserActivity(powerManager, false);
            }
            scheduleScreenTimeoutUserActivityPulse();
        }
    };

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        String processName = param.getProcessName();
        if (param.isSystemServer()) {
            info("Loaded in system_server, API " + getApiVersion());
            return;
        }
        if (processName == null || !isSupportedProcess(processName)) {
            info("Skip process " + processName);
            detach();
            return;
        }
        info("Loaded in " + processName + ", API " + getApiVersion());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        installOplusHistoryWhitelistHook(param.getClassLoader());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (SYSTEMUI_PACKAGE.equals(packageName)) {
            ClassLoader classLoader = param.getClassLoader();
            SystemUiDexKitAdapter.Targets targets = resolveSystemUiTargets(classLoader);
            if (targets == null) {
                return;
            }
            installOplusMediaPolicyBypassHooks(targets);
            installSystemUiWordLyricHooks(classLoader, targets);
            installSystemUiTranslationToggleActionHook(targets);
            return;
        }

        PlayerAdapter adapter = findPlayerAdapter(packageName);
        if (adapter == null) {
            return;
        }
        hookedPlayerAdapter = adapter;
        installMediaMetadataHook();
        adapter.installLyricSourceHooks(this, param.getClassLoader());
    }

    private SystemUiDexKitAdapter.Targets resolveSystemUiTargets(ClassLoader classLoader) {
        SystemUiDexKitAdapter.Targets cached = systemUiDexKitTargets;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = systemUiDexKitTargets;
            if (cached != null) {
                return cached;
            }
            try {
                cached = SystemUiDexKitAdapter.resolve(classLoader);
                systemUiDexKitTargets = cached;
                info("Resolved SystemUI private hooks via DexKit");
                return cached;
            } catch (Throwable dexKitFailure) {
                error("Failed to resolve SystemUI private hooks via DexKit; trying legacy names",
                        dexKitFailure);
            }
            try {
                cached = SystemUiDexKitAdapter.resolveLegacy(classLoader);
                systemUiDexKitTargets = cached;
                info("Resolved SystemUI private hooks via legacy-name fallback");
                return cached;
            } catch (Throwable fallbackFailure) {
                error("Failed to resolve SystemUI private hook targets", fallbackFailure);
                return null;
            }
        }
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

    private static boolean isBuiltInPlayerPackage(String packageName) {
        return findPlayerAdapter(packageName) != null;
    }

    private void installOplusHistoryWhitelistHook(ClassLoader classLoader) {
        if (oplusHistoryWhitelistHookInstalled) {
            return;
        }
        synchronized (this) {
            if (oplusHistoryWhitelistHookInstalled) {
                return;
            }
            try {
                Class<?> serviceClass = classLoader.loadClass(
                        OPLUS_MEDIA_CONTROL_SERVICE_CLASS);
                Method whitelistMethod = serviceClass.getDeclaredMethod(
                        OPLUS_HISTORY_WHITELIST_METHOD,
                        String.class);
                whitelistMethod.setAccessible(true);
                hook(whitelistMethod)
                        .setId(HOOK_ID_OPLUS_HISTORY_WHITELIST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusHistoryWhitelistLookup);
                oplusHistoryWhitelistHookInstalled = true;
                info("Hooked OPlus media history integration");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                info("OPlus media history whitelist hook is unavailable on this system");
            } catch (Throwable t) {
                error("Failed to hook OPlus media history whitelist", t);
            }
        }
    }

    private Object onOplusHistoryWhitelistLookup(
            XposedInterface.Chain chain) throws Throwable {
        Object originalResult = chain.proceed();
        boolean alreadyWhitelisted = Boolean.TRUE.equals(originalResult);
        if (alreadyWhitelisted) {
            return originalResult;
        }
        Object packageNameArg = chain.getArg(0);
        if (!(packageNameArg instanceof String)
                || TextUtils.isEmpty((String) packageNameArg)) {
            return originalResult;
        }

        String packageName = (String) packageNameArg;
        boolean builtInAdapter = isBuiltInPlayerPackage(packageName);
        boolean manifestOptIn = !builtInAdapter
                && isOplusHistoryIntegrationDeclared(
                        chain.getThisObject(),
                        packageName);
        if (!LockscreenIntegrationPolicy.shouldEnableOplusHistoryIntegration(
                false,
                builtInAdapter,
                manifestOptIn)) {
            return originalResult;
        }
        if (loggedOplusHistoryIntegrationPackages.add(packageName)) {
            info("Accepted " + packageName + " into OPlus media history"
                    + (builtInAdapter ? " via built-in adapter" : " via manifest opt-in"));
        }
        return true;
    }

    private boolean isOplusHistoryIntegrationDeclared(
            Object service,
            String packageName) {
        Object contextValue = readFieldValue(service, "mContext");
        if (!(contextValue instanceof Context)) {
            return false;
        }

        Context context = (Context) contextValue;
        long identity = Binder.clearCallingIdentity();
        try {
            ApplicationInfo applicationInfo = context.getPackageManager()
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            Bundle metadata = applicationInfo.metaData;
            return metadata != null
                    && metadata.getBoolean(
                            LyricInfoContract.MANIFEST_METADATA_OPLUS_MEDIA_HISTORY,
                            false);
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            if (loggedOplusHistoryManifestFailures.add(packageName)) {
                error("Failed to read OPlus media history opt-in for " + packageName, t);
            }
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean isCurrentLyricProviderPackage(String packageName) {
        return isBuiltInPlayerPackage(packageName)
                || (!TextUtils.isEmpty(packageName) && packageName.equals(currentLyricProviderPackage));
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

    private boolean activeAdapterSupportsLyricRelayMetadata() {
        PlayerAdapter adapter = hookedPlayerAdapter;
        return adapter != null && adapter.supportsLyricRelayMetadata();
    }

    private boolean activeAdapterMayRetainStaleLyricInfo() {
        PlayerAdapter adapter = hookedPlayerAdapter;
        return adapter != null && adapter.mayRetainStaleLyricInfo();
    }

    private boolean activeAdapterAllowsModuleToReplaceUntrustedLyricInfo() {
        PlayerAdapter adapter = hookedPlayerAdapter;
        return adapter != null && adapter.allowsModuleToReplaceUntrustedLyricInfo();
    }

    private static String findPlayerPackageInLog(String message) {
        if (TextUtils.isEmpty(message)) {
            return "";
        }
        int start = message.indexOf("pkg:");
        if (start < 0) {
            return "";
        }
        start += 4;
        while (start < message.length() && Character.isWhitespace(message.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < message.length()) {
            char value = message.charAt(end);
            if (!(Character.isLetterOrDigit(value) || value == '.' || value == '_')) {
                break;
            }
            end++;
        }
        String packageName = message.substring(start, end);
        return looksLikePackageName(packageName) ? packageName : "";
    }

    private void installOplusMediaPolicyBypassHooks(SystemUiDexKitAdapter.Targets targets) {
        if (oplusMediaPolicyHooksInstalled) {
            return;
        }
        synchronized (this) {
            if (oplusMediaPolicyHooksInstalled) {
                return;
            }
            try {
                Method dealEndTag = targets.dealEndTag;
                dealEndTag.setAccessible(true);
                hook(dealEndTag)
                        .setId(HOOK_ID_RUS_DEAL_END_TAG)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaRusDealEndTag);

                Method saveListToSp = targets.saveListToSp;
                saveListToSp.setAccessible(true);
                hook(saveListToSp)
                        .setId(HOOK_ID_RUS_SAVE_LIST_TO_SP)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaRusSaveListToSp);

                Method getRusWhiteList = targets.getRusWhiteList;
                getRusWhiteList.setAccessible(true);
                hook(getRusWhiteList)
                        .setId(HOOK_ID_RUS_GET_WHITE_LIST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaRusGetWhiteList);

                Method getLyricEntrance = targets.getLyricEntrance;
                getLyricEntrance.setAccessible(true);
                hook(getLyricEntrance)
                        .setId(HOOK_ID_GET_LYRIC_ENTRANCE)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaGetLyricEntrance);

                Method updatePkgActionsRule = targets.updatePkgActionsRule;
                updatePkgActionsRule.setAccessible(true);
                hook(updatePkgActionsRule)
                        .setId(HOOK_ID_UPDATE_PKG_ACTIONS_RULE)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaUpdatePkgActionsRule);
                oplusUpdatePkgActionsRuleMethod = updatePkgActionsRule;

                oplusMediaPolicyHooksInstalled = true;
                info("Hooked OPlus media policy bypass");
            } catch (Throwable t) {
                error("Failed to hook OPlus media policy bypass", t);
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

    private Object onOplusMediaGetLyricEntrance(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        int original = result instanceof Number ? ((Number) result).intValue() : 0;
        if (original != 0) {
            return result;
        }

        Object packageName = chain.getArg(0);
        if (packageName instanceof String && !TextUtils.isEmpty((String) packageName)) {
            return OPLUS_LYRIC_ENTRANCE_ALL;
        }
        return 0;
    }

    private Object onOplusMediaUpdatePkgActionsRule(XposedInterface.Chain chain) throws Throwable {
        oplusMediaActionPrioritySelector = chain.getThisObject();
        List<Object> args = chain.getArgs();
        if (args.isEmpty() || !(args.get(0) instanceof Map)) {
            return chain.proceed();
        }

        ArrayList<String> knownTranslationPackages =
                new ArrayList<>(translationToggleRule0Packages);
        LinkedHashMap<Object, Object> actionPriority = copyActionPriorityWithRule0Packages(
                (Map<?, ?>) args.get(0),
                knownTranslationPackages);

        Object[] patchedArgs = args.toArray(new Object[0]);
        patchedArgs[0] = actionPriority;
        lastOplusPkgActionsRuleArgs = patchedArgs.clone();
        Object result = chain.proceed(patchedArgs);
        markTranslationToggleRule0PackagesRefreshed(knownTranslationPackages);
        return result;
    }

    private void installSystemUiTranslationToggleActionHook(
            SystemUiDexKitAdapter.Targets targets) {
        if (translationToggleActionHookInstalled) {
            return;
        }
        synchronized (this) {
            if (translationToggleActionHookInstalled) {
                return;
            }
            try {
                ensureTranslationPreferenceLoaded();
                Method createActionsFromState = targets.createActionsFromState;
                createActionsFromState.setAccessible(true);
                hook(createActionsFromState)
                        .setId(HOOK_ID_TRANSLATION_TOGGLE_ACTION)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaCreateActionsFromState);
                translationToggleActionHookInstalled = true;
                info("Hooked OPlus media translation toggle action");
            } catch (Throwable t) {
                error("Failed to hook OPlus media translation toggle action", t);
            }
        }
    }

    private Object onOplusMediaCreateActionsFromState(XposedInterface.Chain chain) throws Throwable {
        Object packageNameArg = chain.getArg(0);
        Object controllerArg = chain.getArg(2);
        String packageName = packageNameArg instanceof String ? (String) packageNameArg : "";
        boolean hasTranslationAction = !TextUtils.isEmpty(packageName)
                && controllerHasTranslationAction(controllerArg);
        if (hasTranslationAction) {
            ensureTranslationToggleRule0(packageName);
        }

        Object result = chain.proceed();
        try {
            if (!TextUtils.isEmpty(packageName)) {
                rememberMediaControllerPlaybackState(
                        packageName,
                        controllerArg);
                if (result != null) {
                    replaceTranslationToggleAction(packageName, result);
                }
            }
        } catch (Throwable t) {
            error("Failed to configure lyric translation toggle action", t);
        }
        return result;
    }

    private static boolean controllerHasTranslationAction(Object controllerObject) {
        if (!(controllerObject instanceof MediaController)) {
            return false;
        }
        PlaybackState state = ((MediaController) controllerObject).getPlaybackState();
        return hasCustomAction(state, TRANSLATION_TOGGLE_ACTION);
    }

    private void ensureTranslationToggleRule0(String packageName) {
        if (TextUtils.isEmpty(packageName)
                || isBuiltInPlayerPackage(packageName)) {
            return;
        }
        translationToggleRule0Packages.add(packageName);
        if (!refreshedTranslationToggleRule0Packages.contains(packageName)) {
            pendingTranslationToggleRule0Packages.add(packageName);
        }
        refreshPendingTranslationToggleRule0Packages();
    }

    private void refreshPendingTranslationToggleRule0Packages() {
        ArrayList<String> pendingPackages = new ArrayList<>();
        for (String packageName : pendingTranslationToggleRule0Packages) {
            if (refreshedTranslationToggleRule0Packages.contains(packageName)) {
                pendingTranslationToggleRule0Packages.remove(packageName);
            } else {
                pendingPackages.add(packageName);
            }
        }
        if (pendingPackages.isEmpty()) {
            return;
        }

        Object selector = oplusMediaActionPrioritySelector;
        Method updateMethod = oplusUpdatePkgActionsRuleMethod;
        Object[] cachedArgs = lastOplusPkgActionsRuleArgs;
        if (selector == null
                || updateMethod == null
                || cachedArgs == null) {
            return;
        }

        try {
            ArrayList<String> knownTranslationPackages = new ArrayList<>();
            synchronized (selector) {
                Object[] refreshArgs = cachedArgs.clone();
                if (!(refreshArgs[0] instanceof Map)) {
                    return;
                }
                knownTranslationPackages.addAll(translationToggleRule0Packages);
                LinkedHashMap<Object, Object> actionPriority =
                        copyActionPriorityWithRule0Packages(
                                (Map<?, ?>) refreshArgs[0],
                                knownTranslationPackages);
                refreshArgs[0] = actionPriority;
                updateMethod.invoke(selector, refreshArgs);
                lastOplusPkgActionsRuleArgs = refreshArgs.clone();
            }
            markTranslationToggleRule0PackagesRefreshed(knownTranslationPackages);
            info("Enabled OPlus Rule0 through updatePkgActionsRule for translation providers "
                    + pendingPackages);
        } catch (Throwable t) {
            error("Failed to enable OPlus Rule0 through updatePkgActionsRule for "
                    + pendingPackages, t);
        }
    }

    private static LinkedHashMap<Object, Object> copyActionPriorityWithRule0Packages(
            Map<?, ?> source,
            Iterable<String> translationPackages) {
        LinkedHashMap<Object, Object> actionPriority = new LinkedHashMap<>();
        actionPriority.putAll(source);
        for (PlayerAdapter adapter : PLAYER_ADAPTERS) {
            actionPriority.put(adapter.packageName(), "0");
        }
        for (String packageName : translationPackages) {
            if (!TextUtils.isEmpty(packageName)) {
                actionPriority.put(packageName, "0");
            }
        }
        return actionPriority;
    }

    private void markTranslationToggleRule0PackagesRefreshed(List<String> packageNames) {
        for (String packageName : packageNames) {
            if (TextUtils.isEmpty(packageName)) {
                continue;
            }
            refreshedTranslationToggleRule0Packages.add(packageName);
            pendingTranslationToggleRule0Packages.remove(packageName);
        }
    }

    private void replaceTranslationToggleAction(
            String packageName, Object mediaButton) {
        if (TextUtils.isEmpty(packageName) || mediaButton == null) {
            return;
        }

        Object mediaButtonEx = invokeNoArgByName(mediaButton, "getMediaButtonEx");
        Object actions = invokeNoArgByName(mediaButtonEx, "getRule0CustomActions");
        if (!(actions instanceof List)) {
            return;
        }

        for (Object mediaAction : (List<?>) actions) {
            Object runnable = invokeNoArgByName(mediaAction, "getAction");
            PlaybackState.CustomAction customAction = findPlaybackStateCustomAction(runnable);
            if (customAction == null) {
                continue;
            }

            String actionId = customAction.getAction();
            boolean integrationAction = TRANSLATION_TOGGLE_ACTION.equals(actionId);
            boolean legacySaltAction = isBuiltInPlayerPackage(packageName)
                    && SALT_DESKTOP_LYRIC_ACTION.equals(actionId);
            if (!integrationAction && !legacySaltAction) {
                continue;
            }

            if (integrationAction) {
                promoteTranslationToggleAction(mediaButtonEx, (List<?>) actions, mediaAction);
                if (!replaceMediaActionIcon(mediaAction, packageName)) {
                    rememberCurrentMediaActionIcon(mediaAction);
                }
            } else {
                replaceMediaActionIcon(mediaAction, packageName);
            }

            updateTranslationActionPresentation(mediaAction);
            tryInvokeOneArgByName(mediaAction, "setAction", (Runnable) () -> {
                toggleLyricInfoTranslation();
                updateTranslationActionPresentation(mediaAction);
            });
            info("Configured lyricInfo translation toggle for " + packageName
                    + ", protocol=" + (integrationAction ? "public" : "salt-legacy"));
            return;
        }
    }

    private void promoteTranslationToggleAction(
            Object mediaButtonEx, List<?> actions, Object translationAction) {
        if (actions.isEmpty() || actions.get(0) == translationAction) {
            return;
        }
        ArrayList<Object> ordered = new ArrayList<>(actions.size());
        ordered.add(translationAction);
        for (Object action : actions) {
            if (action != translationAction) {
                ordered.add(action);
            }
        }

        tryInvokeOneArgByName(mediaButtonEx, "setRule0CustomActions", ordered);
        Object applied = invokeNoArgByName(mediaButtonEx, "getRule0CustomActions");
        if (!(applied instanceof List)
                || ((List<?>) applied).isEmpty()
                || ((List<?>) applied).get(0) != translationAction) {
            writeFieldValue(mediaButtonEx, "rule0CustomActions", ordered);
        }
        info("Promoted lyricInfo translation toggle within OPlus Rule0 custom actions");
    }

    private void rememberCurrentMediaActionIcon(Object mediaAction) {
        Object icon = invokeNoArgByName(mediaAction, "getIcon");
        if (icon instanceof Drawable) {
            rememberTranslationIconFingerprint((Drawable) icon);
        }
    }

    private static PlaybackState.CustomAction findPlaybackStateCustomAction(Object runnable) {
        if (runnable == null) {
            return null;
        }
        Class<?> current = runnable.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(runnable);
                    if (value instanceof PlaybackState.CustomAction) {
                        return (PlaybackState.CustomAction) value;
                    }
                } catch (Throwable ignored) {
                    // Continue through synthetic fields and superclass fields.
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean replaceMediaActionIcon(Object mediaAction, String packageName) {
        Context context = currentApplicationContext();
        if (context == null || mediaAction == null || TextUtils.isEmpty(packageName)) {
            return false;
        }
        try {
            Icon icon = findTranslationIcon(context, packageName);
            if (icon == null) {
                return false;
            }
            Drawable drawable = icon.loadDrawable(context);
            if (drawable != null) {
                drawable = drawable.mutate();
                rememberTranslationIconFingerprint(drawable);
                tryInvokeOneArgByName(mediaAction, "setIcon", drawable);
            }
            Object mediaActionEx = invokeNoArgByName(mediaAction, "getMediaActionEx");
            writeFieldValue(mediaActionEx, "icon", icon);
            return true;
        } catch (Throwable t) {
            error("Failed to load lyric translation icon", t);
            return false;
        }
    }

    private static Icon findTranslationIcon(Context context, String providerPackage) {
        Icon providerIcon = findTranslationIconInPackage(context, providerPackage);
        return providerIcon != null
                ? providerIcon
                : findTranslationIconInPackage(context, MODULE_PACKAGE);
    }

    private static Icon findTranslationIconInPackage(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            Context packageContext = context.createPackageContext(
                    packageName,
                    Context.CONTEXT_IGNORE_SECURITY);
            int resourceId = packageContext.getResources().getIdentifier(
                    TRANSLATION_ICON_RESOURCE_NAME,
                    "drawable",
                    packageName);
            return resourceId == 0 ? null : Icon.createWithResource(packageName, resourceId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void updateTranslationActionPresentation(Object mediaAction) {
        boolean enabled = isLyricInfoTranslationEnabled();
        tryInvokeOneArgByName(
                mediaAction,
                "setContentDescription",
                translationActionDescription(enabled));
        Object icon = invokeNoArgByName(mediaAction, "getIcon");
        if (icon instanceof Drawable) {
            ((Drawable) icon).setAlpha(enabled ? 255 : 135);
            ((Drawable) icon).invalidateSelf();
        }
    }

    private void toggleLyricInfoTranslation() {
        setLyricInfoTranslationEnabled(!isLyricInfoTranslationEnabled());
    }

    private boolean isLyricInfoTranslationEnabled() {
        ensureTranslationPreferenceLoaded();
        return lyricInfoTranslationEnabled;
    }

    private void ensureTranslationPreferenceLoaded() {
        if (translationPreferenceLoaded) {
            return;
        }
        synchronized (this) {
            if (translationPreferenceLoaded) {
                return;
            }
            Context context = currentApplicationContext();
            if (context == null) {
                officialLyricTextRenderer.setTranslationEnabledImmediately(
                        lyricInfoTranslationEnabled);
                return;
            }
            SharedPreferences preferences = context.getSharedPreferences(
                    TRANSLATION_PREFERENCES_NAME,
                    Context.MODE_PRIVATE);
            lyricInfoTranslationEnabled = preferences.getBoolean(
                    TRANSLATION_PREFERENCE_KEY,
                    true);
            officialLyricTextRenderer.setTranslationEnabledImmediately(
                    lyricInfoTranslationEnabled);
            translationPreferenceLoaded = true;
        }
    }

    private void setLyricInfoTranslationEnabled(boolean enabled) {
        ensureTranslationPreferenceLoaded();
        if (lyricInfoTranslationEnabled == enabled) {
            return;
        }

        lyricInfoTranslationEnabled = enabled;
        officialLyricTextRenderer.setTranslationEnabled(enabled);
        Context context = currentApplicationContext();
        if (context != null) {
            context.getSharedPreferences(TRANSLATION_PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(TRANSLATION_PREFERENCE_KEY, enabled)
                    .apply();
        }

        mainHandler.post(() -> refreshLyricViewsAfterTranslationToggle(enabled));
        info("lyricInfo translation " + (enabled ? "enabled" : "disabled"));
    }

    private void refreshLyricViewsAfterTranslationToggle(boolean enabled) {
        ArrayList<TextView> textViews = snapshotActiveTextViews();
        for (TextView textView : textViews) {
            textView.invalidate();
            textView.postInvalidateOnAnimation();
        }
        primeRememberedLyricsRecyclerViews(
                enabled ? "translation-enabled" : "translation-disabled");
        for (View root : snapshotLyricRootViews()) {
            root.invalidate();
            root.postInvalidateOnAnimation();
        }
    }

    private static String translationActionDescription(boolean enabled) {
        return TRANSLATION_ACTION_DESCRIPTION_PREFIX
                + (enabled ? "\u5f00\u542f" : "\u5173\u95ed");
    }

    private void installSystemUiWordLyricHooks(
            ClassLoader classLoader,
            SystemUiDexKitAdapter.Targets targets) {
        try {
            Method loadLyricInBg = targets.loadLyricInBg;
            loadLyricInBg.setAccessible(true);
            hook(loadLyricInBg)
                    .setId(HOOK_ID_SYSTEMUI_LOAD_LYRIC)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiLoadLyricInBg);

            Method mediaDataToBundle = targets.mediaDataToBundle;
            mediaDataToBundle.setAccessible(true);
            hook(mediaDataToBundle)
                    .setId(HOOK_ID_SEEDLING_MEDIA_BUNDLE)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSeedlingMediaBundle);

            Method onDraw = TextView.class.getDeclaredMethod("onDraw", Canvas.class);
            onDraw.setAccessible(true);
            hook(onDraw)
                    .setId(HOOK_ID_TEXTVIEW_ON_DRAW)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onTextViewOnDraw);

            Method onAttachedToWindow = View.class.getDeclaredMethod("onAttachedToWindow");
            onAttachedToWindow.setAccessible(true);
            hook(onAttachedToWindow)
                    .setId(HOOK_ID_VIEW_ON_ATTACHED)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onViewAttachedToWindow);

            Method onDetachedFromWindow = View.class.getDeclaredMethod("onDetachedFromWindow");
            onDetachedFromWindow.setAccessible(true);
            hook(onDetachedFromWindow)
                    .setId(HOOK_ID_VIEW_ON_DETACHED)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onViewDetachedFromWindow);

            Method setContentDescription = View.class.getDeclaredMethod(
                    "setContentDescription",
                    CharSequence.class);
            hook(setContentDescription)
                    .setId(HOOK_ID_VIEW_SET_CONTENT_DESCRIPTION)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onViewSetContentDescription);

            Method setVisibility = View.class.getDeclaredMethod("setVisibility", int.class);
            hook(setVisibility)
                    .setId(HOOK_ID_VIEW_SET_VISIBILITY)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onViewSetVisibility);

            Method setImageDrawable = ImageView.class.getDeclaredMethod(
                    "setImageDrawable",
                    Drawable.class);
            hook(setImageDrawable)
                    .setId(HOOK_ID_IMAGE_VIEW_SET_IMAGE_DRAWABLE)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onImageViewSetImage);

            Method setImageBitmap = ImageView.class.getDeclaredMethod(
                    "setImageBitmap",
                    Bitmap.class);
            hook(setImageBitmap)
                    .setId(HOOK_ID_IMAGE_VIEW_SET_IMAGE_BITMAP)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onImageViewSetImage);

            installSystemUiLyricModeLogHooks();

            Method loadClass = ClassLoader.class.getDeclaredMethod("loadClass", String.class, boolean.class);
            loadClass.setAccessible(true);
            hook(loadClass)
                    .setId(HOOK_ID_CLASS_LOADER_LOAD_CLASS)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onClassLoaderLoadClass);

            tryInstallLyricsRecyclerViewHook(classLoader);
            ensureScreenTimeoutReceiver(currentApplicationContext());
            info("Hooked SystemUI official lyric hooks"
                    + (targets.resolvedByDexKit ? " via DexKit" : " via legacy fallback"));
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
            if (first instanceof String) {
                tag = (String) first;
            } else {
                tag = charSequenceToString((CharSequence) chain.getArg(1));
            }
            if (!"PluginSeedling--Template".equals(tag)) {
                return chain.proceed();
            }
            Object messageArg = first instanceof String ? chain.getArg(1) : chain.getArg(2);
            String message = messageArg instanceof CharSequence
                    ? charSequenceToString((CharSequence) messageArg)
                    : "";
            observeSystemUiLyricModeLog(tag, message);
        } catch (Throwable ignored) {
            // Logging hooks must stay invisible to SystemUI.
        }
        return chain.proceed();
    }

    private void observeSystemUiLyricModeLog(String tag, String message) {
        if (!"PluginSeedling--Template".equals(tag)
                || TextUtils.isEmpty(message)) {
            return;
        }
        if (message.contains("LyricsRecyclerView-->setCurrentLyric")) {
            int targetIndex = LockscreenIntegrationPolicy.parseTaggedNonNegativeInt(
                    message,
                    "p:");
            if (targetIndex >= 0) {
                rememberOfficialCurrentLyricIndex(targetIndex);
            }
        }
        if (message.contains("LyricsRecyclerView-->setCurrentLyric p:0, c:-1")) {
            long now = SystemClock.elapsedRealtime();
            boolean activeTrackHandoff = now < officialLyricDrawSuppressedUntilElapsedMs;
            if (now <= lyricTrackRowRebindEligibleUntilElapsedMs
                    && (systemUiLyricModeKeepAwakeActive || activeTrackHandoff)) {
                lyricTrackRowRebindEligibleUntilElapsedMs = 0L;
                releaseOfficialLyricTrackHandoffForRowRebind();
            }
        }
        if (message.contains("[onPreChange]")
                && message.contains("method=immersiveStateChange")
                && message.contains("animate=true")) {
            freezeLyricGlowForUiTransition();
        }

        // Seedling does not emit lyricUiMode=false when the immersive card collapses.
        // The state transition itself is the earliest reliable signal that the action
        // now belongs to the compact lock-screen card and must be hidden there.
        if (isImmersiveMediaExitLog(message)) {
            deactivateSystemUiLyricModeAfterImmersiveExit();
            return;
        }
        if (isImmersiveMediaEnterLog(message)) {
            cancelLyricTrackHandoffForImmersiveEntry();
            activateSystemUiLyricModeAfterImmersiveEntry();
            return;
        }
        if (!message.contains("lyricUiMode=")) {
            return;
        }

        String playerPackage = findPlayerPackageInLog(message);
        if (TextUtils.isEmpty(playerPackage)) {
            return;
        }
        if (!isCurrentLyricProviderPackage(playerPackage)
                && currentLyricProviderPayload != null
                && TextUtils.isEmpty(currentLyricProviderPackage)) {
            bindCurrentLyricProviderPackage(playerPackage, "lyric UI log");
        }
        if (!isCurrentLyricProviderPackage(playerPackage)) {
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
            mainHandler.post(this::refreshTranslationActionViewVisibility);
            refreshLyricRenderingAfterModeChange(active);
        }
        updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private void rememberOfficialCurrentLyricIndex(int targetIndex) {
        WordLyricModel model = currentWordLyricModel;
        if (model == null || targetIndex < 0) {
            return;
        }
        WordLine line = model.lineAtOfficialIndex(targetIndex);
        if (line == null) {
            line = model.lineAt(targetIndex);
        }
        if (line == null) {
            return;
        }

        lastLyricsRecyclerIndex = targetIndex;
        activeLyricLine = line.normalizedText;
        activeLyricLineTimeMs = line.timeMillis;
        long now = SystemClock.elapsedRealtime();
        lastSeedlingActiveLineTimeMs = line.timeMillis;
        lastSeedlingActiveLineObservedAtMs = now;
        TextView activeView = firstActiveLyricTextView();
        if (activeView != null) {
            scheduleActiveLyricRefresh(activeView, ACTIVE_LYRIC_FRAME_DELAY_MS);
        } else {
            scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS);
        }
    }

    private void refreshLyricRenderingAfterModeChange(boolean active) {
        int generation = ++lyricModeRebindGeneration;
        mainHandler.removeCallbacks(activeLyricRefreshRunnable);
        activeLyricUpdatePosted = false;

        Runnable refresh = () -> {
            if (generation != lyricModeRebindGeneration) {
                return;
            }
            for (View recycler : snapshotLyricsRecyclerViews()) {
                recycler.postInvalidateOnAnimation();
            }
            for (TextView textView : snapshotActiveTextViews()) {
                textView.postInvalidateOnAnimation();
            }
            for (View root : snapshotLyricRootViews()) {
                root.postInvalidateOnAnimation();
            }
            if (active) {
                scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS);
            }
        };
        mainHandler.post(refresh);
        mainHandler.postDelayed(refresh, 48L);
        mainHandler.postDelayed(refresh, 120L);
        mainHandler.postDelayed(refresh, 240L);
    }

    private static boolean isImmersiveMediaExitLog(String message) {
        return message.contains("method=immersiveStateChange")
                && message.contains("fromState=2")
                && message.contains("toState=0");
    }

    private static boolean isImmersiveMediaEnterLog(String message) {
        return message.contains("method=immersiveStateChange")
                && message.contains("toState=2")
                && (message.contains("fromState=0")
                || message.contains("fromState=1"));
    }

    private void freezeLyricGlowForUiTransition() {
        long now = SystemClock.elapsedRealtime();
        if (now >= lyricUiTransitionGlowFrozenUntilElapsedMs
                || lyricUiTransitionFrozenGlowPositionMs < 0L) {
            lyricUiTransitionFrozenGlowPositionMs = estimatePlaybackPositionMillis();
        }
        lyricUiTransitionGlowFrozenUntilElapsedMs =
                now + LYRIC_UI_TRANSITION_GLOW_FREEZE_MS;
    }

    private long resolveLyricGlowPosition(long position) {
        long now = SystemClock.elapsedRealtime();
        if (now < lyricUiTransitionGlowFrozenUntilElapsedMs
                && lyricUiTransitionFrozenGlowPositionMs >= 0L) {
            return lyricUiTransitionFrozenGlowPositionMs;
        }
        lyricUiTransitionFrozenGlowPositionMs = -1L;
        return position;
    }

    private void activateSystemUiLyricModeAfterImmersiveEntry() {
        if (!systemUiLyricModeEnabled || !systemUiHasOfficialLyric) {
            return;
        }
        boolean changed = !systemUiLyricModeKeepAwakeActive;
        systemUiLyricModeKeepAwakeActive = true;
        screenTimeoutPausedByUserPresent = false;
        lastSystemUiLyricModeLogAt = SystemClock.elapsedRealtime();
        if (changed) {
            info("Observed immersive media entry with lyric mode active");
            maybeLogLyricModeKeepAwake(true);
        }
        Runnable refresh = () -> {
            refreshTranslationActionViewVisibility();
        };
        mainHandler.post(refresh);
        scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS);
        mainHandler.postDelayed(() -> {
            discoverTranslationActionViewsNearRememberedLyrics();
            refreshTranslationActionViewVisibility();
        }, 960L);
        updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private void deactivateSystemUiLyricModeAfterImmersiveExit() {
        boolean changed = systemUiLyricModeKeepAwakeActive;
        // Keep lyricUiMode as the user's selected media-card mode. Seedling does not
        // emit it again when a compact capsule is expanded back to state 2.
        systemUiLyricModeKeepAwakeActive = false;
        lastSystemUiLyricModeLogAt = SystemClock.elapsedRealtime();
        clearScreenTimeoutLyricEvidence();
        if (changed) {
            info("Observed immersive media exit; hiding translation action");
            mainHandler.post(this::refreshTranslationActionViewVisibility);
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
        Object[] normalizedArgs = null;
        try {
            Object metadataArg = chain.getArg(1);
            if (metadataArg instanceof MediaMetadata) {
                String lyricInfo = ((MediaMetadata) metadataArg).getString(OPLUS_LYRIC_INFO_KEY);
                LyricInfoContract.NormalizedPayload normalizedPayload =
                        LyricInfoContract.normalizeOfficialLyricInfo(lyricInfo);
                LyricInfoContract.Payload payload = normalizedPayload.payload;
                if (payload != null) {
                    if (normalizedPayload.changed) {
                        MediaMetadata normalizedMetadata = new MediaMetadata.Builder(
                                (MediaMetadata) metadataArg)
                                .putString(OPLUS_LYRIC_INFO_KEY, normalizedPayload.lyricInfo)
                                .build();
                        normalizedArgs = chain.getArgs().toArray(new Object[0]);
                        normalizedArgs[1] = normalizedMetadata;
                    }
                    acceptCurrentLyricProvider(chain, payload);
                    maybeLogOfficialLyricPayload(payload, normalizedPayload.changed);
                    cacheSystemUiLyricModel(payload);
                } else {
                    clearCurrentLyricProvider();
                }
            }
        } catch (Throwable t) {
            error("Failed to read SystemUI lyricInfo", t);
        }
        return normalizedArgs == null ? chain.proceed() : chain.proceed(normalizedArgs);
    }

    private void acceptCurrentLyricProvider(
            XposedInterface.Chain chain, LyricInfoContract.Payload payload) {
        currentLyricProviderPayload = payload;
        String packageName = findPlayerPackageInArgs(chain.getArgs());
        currentLyricProviderPackage = "";
        if (!TextUtils.isEmpty(packageName)) {
            bindCurrentLyricProviderPackage(packageName, "lyricInfo metadata");
        }
    }

    private static String findPlayerPackageInArgs(List<Object> args) {
        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i);
            if (arg instanceof String && looksLikePackageName((String) arg)) {
                return (String) arg;
            }
        }
        return "";
    }

    private static boolean looksLikePackageName(String value) {
        if (TextUtils.isEmpty(value) || !value.equals(value.toLowerCase(Locale.ROOT))) {
            return false;
        }
        int dots = 0;
        boolean segmentHasCharacter = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '.') {
                if (!segmentHasCharacter) {
                    return false;
                }
                dots++;
                segmentHasCharacter = false;
            } else if (Character.isLetterOrDigit(character) || character == '_') {
                segmentHasCharacter = true;
            } else {
                return false;
            }
        }
        return dots >= 1 && segmentHasCharacter;
    }

    private void bindCurrentLyricProviderPackage(String packageName, String source) {
        if (TextUtils.isEmpty(packageName) || packageName.equals(currentLyricProviderPackage)) {
            return;
        }
        currentLyricProviderPackage = packageName;
        lastSystemUiPackageSupported = true;
        info("Accepted " + (isBuiltInPlayerPackage(packageName) ? "built-in" : "external")
                + " lyricInfo provider " + packageName + " from " + source);
    }

    private boolean tryBindCurrentLyricProvider(String packageName, String songName) {
        LyricInfoContract.Payload payload = currentLyricProviderPayload;
        if (payload == null || TextUtils.isEmpty(packageName)) {
            return false;
        }
        if (!TextUtils.isEmpty(currentLyricProviderPackage)) {
            return packageName.equals(currentLyricProviderPackage);
        }
        if (TextUtils.isEmpty(payload.songName)
                || !normalizeLine(payload.songName).equals(normalizeLine(songName))) {
            return false;
        }
        bindCurrentLyricProviderPackage(packageName, "matching SystemUI media data");
        return true;
    }

    private void clearCurrentLyricProvider() {
        if (currentWordLyricModel != null && hasAttachedLyricsRecyclerView()) {
            beginOfficialLyricTrackHandoff("lyricInfo temporarily unavailable");
        }
        currentLyricProviderPayload = null;
        currentLyricProviderPackage = "";
        lastSystemUiPackageSupported = false;
        systemUiHasOfficialLyric = false;
        currentWordLyricModel = null;
        currentWordLyricModelSignature = "";
        clearSeedlingActiveLyricHint();
        systemUiLyricModeKeepAwakeActive = false;
        clearScreenTimeoutLyricEvidence();
        mainHandler.post(this::refreshTranslationActionViewVisibility);
        updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private Object onSeedlingMediaBundle(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (!(result instanceof Bundle)) {
            return result;
        }
        try {
            Bundle root = (Bundle) result;
            ArrayList<?> mediaList = root.getParcelableArrayList("mediaList");
            if (mediaList == null) {
                readSeedlingMediaBundle(root);
            } else {
                for (Object item : mediaList) {
                    if (item instanceof Bundle && readSeedlingMediaBundle((Bundle) item)) {
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            error("Failed to read Seedling media bundle playback state", t);
        }
        return result;
    }

    private boolean readSeedlingMediaBundle(Bundle mediaData) {
        String packageName = mediaData.getString("packageName", "");
        String songName = charSequenceToString(mediaData.getCharSequence("songName"));
        if (!isCurrentLyricProviderPackage(packageName)
                && !tryBindCurrentLyricProvider(packageName, songName)) {
            return false;
        }

        String artistName = charSequenceToString(mediaData.getCharSequence("artist"));
        MetadataTrackIdentity relayIdentity = SALT_PLAYER_PACKAGE.equals(packageName)
                ? resolveSaltRelayPayloadIdentity(
                currentLyricProviderPayload,
                songName,
                artistName)
                : null;
        if (relayIdentity != null) {
            songName = relayIdentity.title;
            artistName = relayIdentity.artist;
        }
        int state = mediaData.getInt("state", -1);
        long storedPosition = mediaData.getLong("position", -1L);
        long lastPositionUpdateTime =
                mediaData.getLong("lastPositionUpdateTime", -1L);
        float speed = mediaData.getFloat("playbackSpeed", 1f);
        long computedPosition = computeSeedlingPosition(
                state,
                storedPosition,
                lastPositionUpdateTime,
                speed);
        rememberSeedlingActiveLyric(
                charSequenceToString(mediaData.getCharSequence("currentLyric")),
                computedPosition);

        String previousTrackKey = buildTrackKey(
                lastSystemUiSongName,
                lastSystemUiArtistName);
        String nextTrackKey = buildTrackKey(songName, artistName);
        boolean systemUiTrackChanged = lastSystemUiPackageSupported
                && !TextUtils.isEmpty(lastSystemUiSongName)
                && !previousTrackKey.equals(nextTrackKey);
        if (systemUiTrackChanged
                && !payloadMatchesTrack(currentLyricProviderPayload, songName, artistName)) {
            clearSystemUiLyricModelForTrackChange(songName, artistName);
        }
        lastSystemUiPackageSupported = true;
        lastSystemUiSongName = songName;
        lastSystemUiArtistName = artistName;

        rememberSystemUiPlaybackState(state, storedPosition, computedPosition, speed);
        return true;
    }

    private void rememberMediaControllerPlaybackState(
            String packageName,
            Object controllerObject) {
        if (!(controllerObject instanceof MediaController)
                || TextUtils.isEmpty(packageName)) {
            return;
        }
        if (!TextUtils.isEmpty(currentLyricProviderPackage)) {
            if (!packageName.equals(currentLyricProviderPackage)) {
                return;
            }
        } else if (!isBuiltInPlayerPackage(packageName)) {
            return;
        }

        PlaybackState playbackState = ((MediaController) controllerObject).getPlaybackState();
        if (playbackState == null) {
            return;
        }
        int state = playbackState.getState();
        long storedPosition = playbackState.getPosition();
        float speed = playbackState.getPlaybackSpeed();
        long computedPosition = LockscreenIntegrationPolicy.extrapolatePlaybackPosition(
                isPlaybackStateInMotion(state),
                storedPosition,
                playbackState.getLastPositionUpdateTime(),
                speed,
                SystemClock.elapsedRealtime());
        rememberSystemUiPlaybackState(state, storedPosition, computedPosition, speed);
    }

    private void rememberSystemUiPlaybackState(
            int state,
            long storedPosition,
            long computedPosition,
            float speed) {
        long previousPosition = estimatePlaybackPositionMillis();
        if (isPlaybackStateInMotion(state)
                && LockscreenIntegrationPolicy.isLikelyPlaybackTrackRestart(
                previousPosition,
                computedPosition)) {
            // Salt publishes the new PlaybackState a few milliseconds before the replacement
            // lyricInfo. Hide the old model during that gap instead of drawing one stale frame.
            clearSeedlingActiveLyricHint();
            activeRendererTextView = new WeakReference<>(null);
            activeRendererWordLine = null;
            beginOfficialLyricTrackHandoff("playback position reset");
        }
        long nextPosition = storedPosition >= 0 ? storedPosition : computedPosition;
        if (isPlaybackStateInMotion(state) && speed > 0f && computedPosition >= 0) {
            lastPlaybackIsPlaying = true;
            lastPlaybackSpeed = speed;
            lastComputedPositionMs = computedPosition;
            lastComputedPositionElapsedMs = SystemClock.elapsedRealtime();
        } else if (state >= 0) {
            lastPlaybackIsPlaying = false;
            lastPlaybackSpeed = speed;
            lastComputedPositionMs = nextPosition;
            lastComputedPositionElapsedMs = -1L;
        } else if (computedPosition >= 0) {
            lastComputedPositionMs = computedPosition;
            lastComputedPositionElapsedMs = SystemClock.elapsedRealtime();
        }
        lastSystemUiPlaybackState = state;
        maybeLogSeedlingPlaybackState(state, nextPosition, computedPosition, speed);
        updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private static boolean isPlaybackStateInMotion(int state) {
        return state == 3 || state == 4 || state == 5;
    }

    private static long computeSeedlingPosition(
            int state,
            long storedPosition,
            long lastPositionUpdateTime,
            float speed) {
        return LockscreenIntegrationPolicy.extrapolatePlaybackPosition(
                isPlaybackStateInMotion(state),
                storedPosition,
                lastPositionUpdateTime,
                speed,
                SystemClock.elapsedRealtime());
    }

    private void rememberSeedlingActiveLyric(String currentLyric, long position) {
        WordLyricModel model = currentWordLyricModel;
        String normalized = normalizeLine(currentLyric);
        if (model == null || TextUtils.isEmpty(normalized)) {
            return;
        }

        WordLine line = model.findLineByText(normalized, position);
        if (line == null) {
            line = model.findLineByTranslation(normalized, position);
        }
        if (line == null) {
            return;
        }
        lastSeedlingActiveLineTimeMs = line.timeMillis;
        lastSeedlingActiveLineObservedAtMs = SystemClock.elapsedRealtime();
    }

    private void clearSeedlingActiveLyricHint() {
        lastSeedlingActiveLineTimeMs = -1L;
        lastSeedlingActiveLineObservedAtMs = -1L;
    }

    private Object onTextViewOnDraw(XposedInterface.Chain chain) throws Throwable {
        Object thisObject = chain.getThisObject();
        Object canvasArg = chain.getArg(0);
        if (!(thisObject instanceof TextView) || !(canvasArg instanceof Canvas)) {
            return chain.proceed();
        }

        TextView textView = (TextView) thisObject;
        if (findContainingLyricsRecyclerView(textView) == null) {
            return chain.proceed();
        }
        noteVisibleLockscreenLyricTextView(textView);
        boolean suppressingTrackHandoff = shouldSuppressOfficialLyricForTrackHandoff();
        boolean recyclerFadeInProgress =
                SystemClock.elapsedRealtime() < lyricRecyclerFadeInUntilElapsedMs;
        if (currentWordLyricModel == null) {
            return suppressingTrackHandoff || recyclerFadeInProgress
                    ? null
                    : chain.proceed();
        }
        if (!suppressingTrackHandoff
                && !recyclerFadeInProgress
                && !isEffectivelyVisible(textView)) {
            // Never cache the official renderer while the lyric container is hidden or moving
            // through a cross-fade. Lightweight mode-change invalidations above make the row
            // record our renderer as soon as it becomes visible.
            return null;
        }
        try {
            DrawFrame frame = findOfficialLyricDrawFrame(textView);
            if (frame == null) {
                return suppressingTrackHandoff || recyclerFadeInProgress
                        ? null
                        : chain.proceed();
            }

            prepareOfficialLyricTextView(textView);
            officialLyricTextRenderer.draw((Canvas) canvasArg, textView, frame);
            maybeLogTextViewDraw(frame, textView);
            if (suppressingTrackHandoff && !lyricModelReplacementInProgress) {
                finishOfficialLyricTrackHandoff();
            } else if (!suppressingTrackHandoff && !lyricModelReplacementInProgress) {
                fadeInLateCustomLyricTakeover(textView);
            }
            return null;
        } catch (Throwable t) {
            error("Failed to custom-draw official lyric TextView", t);
            return suppressingTrackHandoff ? null : chain.proceed();
        }
    }

    private Object onViewAttachedToWindow(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object thisObject = chain.getThisObject();
        if (!(thisObject instanceof View)) {
            return result;
        }

        View view = (View) thisObject;
        if (isLyricsRecyclerView(view)) {
            View recyclerView = view;
            configureOfficialLyricLineSpacing(recyclerView);
            tryInstallLyricsRecyclerViewHook(recyclerView.getClass());
            officialLyricTextRenderer.armEntranceReveal();
            rememberLyricsRecyclerView(recyclerView);
            scheduleLyricsRecyclerPrime(recyclerView);
            scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS);
            mainHandler.postDelayed(
                    () -> {
                        discoverTranslationActionViewsNear(recyclerView);
                        refreshTranslationActionViewVisibility();
                    },
                    960L);
            mainHandler.post(this::refreshTranslationActionViewVisibility);
        }
        if (isTranslationActionDescription(view.getContentDescription())) {
            rememberTranslationActionView(view);
        } else if (view instanceof ImageView) {
            detectTranslationActionImageView((ImageView) view, "view attachment");
        }
        return result;
    }

    private static boolean payloadMatchesTrack(
            LyricInfoContract.Payload payload, String title, String artist) {
        return payload != null
                && !TextUtils.isEmpty(payload.songName)
                && buildTrackKey(payload.songName, payload.artist)
                .equals(buildTrackKey(title, artist));
    }

    private void clearSystemUiLyricModelForTrackChange(String title, String artist) {
        beginOfficialLyricTrackHandoff("SystemUI track changed");
        currentLyricProviderPayload = null;
        systemUiHasOfficialLyric = false;
        currentWordLyricModel = null;
        currentWordLyricModelSignature = "";
        clearSeedlingActiveLyricHint();
        activeRendererTextView = new WeakReference<>(null);
        activeRendererWordLine = null;
        officialLyricTextRenderer.clearGlowCache();
        activeLyricLine = "";
        activeLyricLineTimeMs = -1L;
        clearScreenTimeoutLyricEvidence();
        mainHandler.post(() -> {
            refreshTranslationActionViewVisibility();
            for (TextView textView : snapshotActiveTextViews()) {
                textView.requestLayout();
                textView.invalidate();
            }
        });
        info("Cleared previous lyric model after track change to title="
                + title + ", artist=" + nullToEmpty(artist));
        updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private Object onViewDetachedFromWindow(XposedInterface.Chain chain) throws Throwable {
        Object thisObject = chain.getThisObject();
        boolean lyricsRecycler = thisObject instanceof View
                && isLyricsRecyclerView((View) thisObject);
        if (thisObject instanceof View && isRememberedTranslationActionView((View) thisObject)) {
            forgetTranslationActionView((View) thisObject);
        }
        Object result = chain.proceed();
        if (lyricsRecycler) {
            mainHandler.post(this::refreshTranslationActionViewVisibility);
        }
        return result;
    }

    private Object onViewSetContentDescription(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object thisObject = chain.getThisObject();
        if (!(thisObject instanceof View)) {
            return result;
        }

        View view = (View) thisObject;
        Object descriptionArg = chain.getArg(0);
        if (descriptionArg instanceof CharSequence
                && isTranslationActionDescription((CharSequence) descriptionArg)) {
            rememberTranslationActionView(view, false);
        } else if (!isIconMatchedTranslationActionView(view)) {
            forgetTranslationActionView(view);
        }
        return result;
    }

    private Object onViewSetVisibility(XposedInterface.Chain chain) throws Throwable {
        Object thisObject = chain.getThisObject();
        Object visibilityArg = chain.getArg(0);
        if (!(thisObject instanceof View) || !(visibilityArg instanceof Number)) {
            return chain.proceed();
        }

        View view = (View) thisObject;
        Object result;
        if (((Number) visibilityArg).intValue() == View.VISIBLE
                && isRememberedTranslationActionView(view)
                && !shouldShowTranslationActionView()) {
            Object[] args = chain.getArgs().toArray();
            // Keep the action slot measured so previous/play/next remain centered.
            args[0] = View.INVISIBLE;
            result = chain.proceed(args);
        } else {
            result = chain.proceed();
        }
        if (isLyricsRecyclerView(view)) {
            mainHandler.post(this::refreshTranslationActionViewVisibility);
            if (((Number) visibilityArg).intValue() == View.VISIBLE) {
                scheduleLyricsRecyclerPrime(view);
                mainHandler.postDelayed(
                        () -> refreshLyricRenderingAfterModeChange(
                                systemUiLyricModeKeepAwakeActive),
                        32L);
            }
        }
        return result;
    }

    private Object onImageViewSetImage(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object thisObject = chain.getThisObject();
        if (thisObject instanceof ImageView) {
            detectTranslationActionImageView((ImageView) thisObject, "image binding");
        }
        return result;
    }

    private void detectTranslationActionImageView(ImageView imageView, String reason) {
        if (imageView == null
                || translationIconAlphaFingerprint == null
                || (!isRememberedTranslationActionView(imageView)
                && !isNearRememberedLyricsRecyclerView(imageView))) {
            return;
        }
        if (!looksLikeTranslationIcon(imageView.getDrawable())) {
            return;
        }
        boolean newlyDetected = !isRememberedTranslationActionView(imageView);
        rememberTranslationActionView(imageView, true);
        if (newlyDetected) {
            info("Detected translation action view from " + reason);
        }
    }

    private boolean isNearRememberedLyricsRecyclerView(View candidate) {
        if (candidate == null) {
            return false;
        }
        ArrayList<View> candidateAncestors = new ArrayList<>(9);
        View current = candidate;
        for (int depth = 0; current != null && depth < 9; depth++) {
            candidateAncestors.add(current);
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                    continue;
                }
                current = recycler;
                for (int depth = 0; current != null && depth < 9; depth++) {
                    if (candidateAncestors.contains(current)) {
                        return true;
                    }
                    Object parent = current.getParent();
                    current = parent instanceof View ? (View) parent : null;
                }
            }
        }
        return false;
    }

    private void rememberTranslationActionView(View view) {
        rememberTranslationActionView(view, false);
    }

    private void rememberTranslationActionView(View view, boolean iconMatched) {
        if (view == null) {
            return;
        }
        boolean added;
        synchronized (translationActionViewsLock) {
            Boolean previous = translationActionViews.get(view);
            added = previous == null;
            translationActionViews.put(view, iconMatched || Boolean.TRUE.equals(previous));
        }
        applyTranslationActionViewVisibility(view);
        if (!added) {
            return;
        }
        info("Tracked translation action view, iconMatched=" + iconMatched
                + ", attached=" + view.isAttachedToWindow()
                + ", visibility=" + view.getVisibility());
        view.post(() -> applyTranslationActionViewVisibility(view));
        mainHandler.postDelayed(() -> applyTranslationActionViewVisibility(view), 64L);
        mainHandler.postDelayed(() -> applyTranslationActionViewVisibility(view), 240L);
    }

    private void forgetTranslationActionView(View view) {
        if (view == null) {
            return;
        }
        synchronized (translationActionViewsLock) {
            translationActionViews.remove(view);
        }
    }

    private boolean isRememberedTranslationActionView(View view) {
        synchronized (translationActionViewsLock) {
            return translationActionViews.containsKey(view);
        }
    }

    private boolean isIconMatchedTranslationActionView(View view) {
        synchronized (translationActionViewsLock) {
            return Boolean.TRUE.equals(translationActionViews.get(view));
        }
    }

    private void refreshTranslationActionViewVisibility() {
        ArrayList<View> views;
        synchronized (translationActionViewsLock) {
            views = new ArrayList<>(translationActionViews.keySet());
        }
        for (View view : views) {
            applyTranslationActionViewVisibility(view);
        }
    }

    private void applyTranslationActionViewVisibility(View view) {
        if (view == null || !isRememberedTranslationActionView(view)) {
            return;
        }
        int targetVisibility = shouldShowTranslationActionView()
                ? View.VISIBLE
                : View.INVISIBLE;
        if (view.getVisibility() != targetVisibility) {
            view.setVisibility(targetVisibility);
        }
    }

    private boolean shouldShowTranslationActionView() {
        WordLyricModel model = currentWordLyricModel;
        return systemUiLyricModeKeepAwakeActive
                && hasShownLyricsRecyclerView()
                && model != null
                && model.translationCount() > 0;
    }

    private boolean hasShownLyricsRecyclerView() {
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler.isAttachedToWindow() && recycler.isShown()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTranslationActionDescription(CharSequence description) {
        return description != null
                && description.toString().startsWith(
                TRANSLATION_ACTION_DESCRIPTION_PREFIX);
    }

    private void rememberTranslationIconFingerprint(Drawable drawable) {
        if (translationIconAlphaFingerprint != null) {
            return;
        }
        byte[] fingerprint = renderDrawableAlphaFingerprint(drawable);
        if (fingerprint == null) {
            return;
        }
        translationIconAlphaFingerprint = fingerprint;
        mainHandler.post(() -> {
            discoverTranslationActionViewsNearRememberedLyrics();
            refreshTranslationActionViewVisibility();
        });
    }

    private void discoverTranslationActionViewsNearRememberedLyrics() {
        ArrayList<View> recyclers = new ArrayList<>();
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler.isAttachedToWindow()) {
                    recyclers.add(recycler);
                }
            }
        }
        for (View recycler : recyclers) {
            discoverTranslationActionViewsNear(recycler);
        }
    }

    private void discoverTranslationActionViewsNear(View anchor) {
        if (anchor == null || translationIconAlphaFingerprint == null) {
            return;
        }
        View root = anchor;
        for (int i = 0; i < 7; i++) {
            Object parent = root.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            root = (View) parent;
        }
        long now = SystemClock.elapsedRealtime();
        synchronized (translationRootLastScanAt) {
            Long lastScanAt = translationRootLastScanAt.get(root);
            if (lastScanAt != null && now - lastScanAt < 50L) {
                return;
            }
            translationRootLastScanAt.put(root, now);
        }

        ArrayList<View> pending = new ArrayList<>();
        pending.add(root);
        int visited = 0;
        while (!pending.isEmpty() && visited < 6_000) {
            View view = pending.remove(pending.size() - 1);
            visited++;
            if (view instanceof ImageView
                    && looksLikeTranslationIcon(((ImageView) view).getDrawable())) {
                rememberTranslationActionView(view, true);
            }
            if (!(view instanceof ViewGroup)) {
                continue;
            }
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child != null) {
                    pending.add(child);
                }
            }
        }
    }

    private boolean looksLikeTranslationIcon(Drawable drawable) {
        byte[] expected = translationIconAlphaFingerprint;
        if (drawable == null || expected == null) {
            return false;
        }
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                return false;
            }
            synchronized (translationBitmapMatchCache) {
                Boolean cached = translationBitmapMatchCache.get(bitmap);
                if (cached != null) {
                    return cached;
                }
            }
            boolean matches = alphaFingerprintsMatch(
                    expected,
                    renderBitmapAlphaFingerprint(bitmap));
            synchronized (translationBitmapMatchCache) {
                translationBitmapMatchCache.put(bitmap, matches);
            }
            return matches;
        }

        String drawableClassName = drawable.getClass().getName();
        if (!drawableClassName.contains("VectorDrawable")) {
            return false;
        }
        synchronized (translationDrawableMatchCache) {
            Boolean cached = translationDrawableMatchCache.get(drawable);
            if (cached != null) {
                return cached;
            }
        }
        boolean matches = alphaFingerprintsMatch(
                expected,
                renderDrawableAlphaFingerprint(drawable));
        synchronized (translationDrawableMatchCache) {
            translationDrawableMatchCache.put(drawable, matches);
        }
        return matches;
    }

    private static boolean alphaFingerprintsMatch(byte[] expected, byte[] actual) {
        if (expected == null || actual == null || expected.length != actual.length) {
            return false;
        }

        int expectedMax = 0;
        int actualMax = 0;
        for (int i = 0; i < expected.length; i++) {
            expectedMax = Math.max(expectedMax, expected[i] & 0xff);
            actualMax = Math.max(actualMax, actual[i] & 0xff);
        }
        if (expectedMax == 0 || actualMax == 0) {
            return false;
        }

        int expectedThreshold = Math.max(8, expectedMax / 8);
        int actualThreshold = Math.max(8, actualMax / 8);
        int intersection = 0;
        int union = 0;
        int expectedPixels = 0;
        int actualPixels = 0;
        for (int i = 0; i < expected.length; i++) {
            boolean expectedOn = (expected[i] & 0xff) >= expectedThreshold;
            boolean actualOn = (actual[i] & 0xff) >= actualThreshold;
            if (expectedOn) {
                expectedPixels++;
            }
            if (actualOn) {
                actualPixels++;
            }
            if (expectedOn && actualOn) {
                intersection++;
            }
            if (expectedOn || actualOn) {
                union++;
            }
        }
        if (union == 0 || expectedPixels == 0 || actualPixels == 0) {
            return false;
        }
        float areaRatio = actualPixels / (float) expectedPixels;
        float overlap = intersection / (float) union;
        return areaRatio >= 0.72f && areaRatio <= 1.38f && overlap >= 0.68f;
    }

    private static byte[] renderBitmapAlphaFingerprint(Bitmap source) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        Bitmap software = null;
        Bitmap scaled = null;
        try {
            software = source.getConfig() == Bitmap.Config.HARDWARE
                    ? source.copy(Bitmap.Config.ARGB_8888, false)
                    : source;
            if (software == null || software.isRecycled()) {
                return null;
            }
            scaled = Bitmap.createScaledBitmap(
                    software,
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    true);
            byte[] alpha = new byte[
                    TRANSLATION_ICON_FINGERPRINT_SIZE * TRANSLATION_ICON_FINGERPRINT_SIZE];
            int index = 0;
            for (int y = 0; y < TRANSLATION_ICON_FINGERPRINT_SIZE; y++) {
                for (int x = 0; x < TRANSLATION_ICON_FINGERPRINT_SIZE; x++) {
                    alpha[index++] = (byte) (scaled.getPixel(x, y) >>> 24);
                }
            }
            return alpha;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (scaled != null && scaled != software && scaled != source) {
                scaled.recycle();
            }
            if (software != null && software != source) {
                software.recycle();
            }
        }
    }

    private static byte[] renderDrawableAlphaFingerprint(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            android.graphics.Rect oldBounds = new android.graphics.Rect(drawable.getBounds());
            int oldAlpha = drawable.getAlpha();
            drawable.setBounds(
                    0,
                    0,
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    TRANSLATION_ICON_FINGERPRINT_SIZE);
            drawable.setAlpha(255);
            drawable.draw(canvas);
            drawable.setAlpha(oldAlpha);
            drawable.setBounds(oldBounds);

            byte[] alpha = new byte[
                    TRANSLATION_ICON_FINGERPRINT_SIZE * TRANSLATION_ICON_FINGERPRINT_SIZE];
            int index = 0;
            for (int y = 0; y < TRANSLATION_ICON_FINGERPRINT_SIZE; y++) {
                for (int x = 0; x < TRANSLATION_ICON_FINGERPRINT_SIZE; x++) {
                    alpha[index++] = (byte) (bitmap.getPixel(x, y) >>> 24);
                }
            }
            return alpha;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private static String charSequenceToString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private String normalizedTextOf(TextView textView) {
        if (textView == null) {
            return "";
        }
        CharSequence value = textView.getText();
        if (value == null) {
            return "";
        }
        int contentHash = charSequenceContentHash(value);
        NormalizedTextSnapshot cached = normalizedLyricTextCache.get(textView);
        if (cached != null
                && cached.source == value
                && cached.length == value.length()
                && cached.contentHash == contentHash) {
            return cached.normalized;
        }
        String text = value instanceof String ? (String) value : value.toString();
        String normalized = normalizeLine(text);
        normalizedLyricTextCache.put(
                textView,
                new NormalizedTextSnapshot(value, value.length(), contentHash, normalized));
        return normalized;
    }

    private static int charSequenceContentHash(CharSequence value) {
        int hash = 0;
        for (int i = 0; i < value.length(); i++) {
            hash = 31 * hash + value.charAt(i);
        }
        return hash;
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
        if (Boolean.TRUE.equals(suppressLyricsRecyclerHook.get())) {
            return chain.proceed();
        }
        Object recycler = chain.getThisObject();
        int targetIndex = -1;
        LyricsRecyclerGeometry beforeGeometry = null;
        try {
            Object index = chain.getArg(0);
            if (index instanceof Integer) {
                targetIndex = (Integer) index;
                lastLyricsRecyclerIndex = targetIndex;
                WordLyricModel model = currentWordLyricModel;
                if (model != null) {
                    WordLine line = model.lineAtOfficialIndex(lastLyricsRecyclerIndex);
                    if (line == null) {
                        line = model.lineAt(lastLyricsRecyclerIndex);
                    }
                    if (line != null) {
                        activeLyricLine = line.normalizedText;
                        activeLyricLineTimeMs = line.timeMillis;
                        TextView candidate = firstActiveLyricTextView();
                        if (candidate != null) {
                            scheduleActiveLyricRefresh(candidate, 33L);
                        }
                    }
                }
                maybeLogRecyclerIndex(lastLyricsRecyclerIndex);
            }
            if (recycler instanceof View) {
                View recyclerView = (View) recycler;
                beforeGeometry = captureLyricsRecyclerGeometry(recyclerView, targetIndex);
                stopLyricsRecyclerScroll(recyclerView);
                stabilizeLyricsRecyclerScroll(recyclerView, "before-setCurrentLyric");
                applyVisibleLyricBlockHeights(recyclerView);
            }
        } catch (Throwable t) {
            error("Failed while reading LyricsRecyclerView#setCurrentLyric", t);
        }
        Object result = chain.proceed(disableLyricsRecyclerAnimation(chain));
        if (recycler instanceof View) {
            View recyclerView = (View) recycler;
            stopLyricsRecyclerScroll(recyclerView);
            stabilizeLyricsRecyclerScroll(recyclerView, "after-setCurrentLyric");
            applyVisibleLyricBlockHeights(recyclerView);
            offsetLyricsRecyclerCurrentLine(recyclerView, targetIndex);
            maybeLogLyricsRecyclerSetCurrentGeometry(
                    targetIndex,
                    beforeGeometry,
                    captureLyricsRecyclerGeometry(recyclerView, targetIndex));
            final int postedTargetIndex = targetIndex;
            recyclerView.post(() -> {
                offsetLyricsRecyclerCurrentLine(recyclerView, postedTargetIndex);
                stopLyricsRecyclerScroll(recyclerView);
            });
            if (targetIndex <= 0) {
                prewarmVisibleLyricBlockHeights(recyclerView);
            }
        }
        return result;
    }

    private static Object[] disableLyricsRecyclerAnimation(XposedInterface.Chain chain) {
        Object[] args = chain.getArgs().toArray();
        Class<?>[] parameterTypes = chain.getExecutable().getParameterTypes();
        for (int i = 1; i < parameterTypes.length && i < args.length; i++) {
            if (parameterTypes[i] == boolean.class || parameterTypes[i] == Boolean.class) {
                args[i] = false;
                break;
            }
        }
        return args;
    }

    private static void stopLyricsRecyclerScroll(View recycler) {
        tryInvokeNoArgByName(recycler, "stopScroll");
    }

    private void prewarmVisibleLyricBlockHeights(View recycler) {
        if (recycler == null) {
            return;
        }
        recycler.post(() -> {
            stabilizeLyricsRecyclerScroll(recycler, "post-bind");
            applyVisibleLyricBlockHeights(recycler);
        });
        mainHandler.postDelayed(() -> applyVisibleLyricBlockHeights(recycler), 32L);
        mainHandler.postDelayed(() -> applyVisibleLyricBlockHeights(recycler), 96L);
    }

    private void rememberLyricsRecyclerView(View recycler) {
        if (recycler == null) {
            return;
        }
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View existing = lyricsRecyclerViews.get(i).get();
                if (existing == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (existing == recycler) {
                    return;
                }
            }
            if (lyricsRecyclerViews.size() >= 4) {
                lyricsRecyclerViews.remove(0);
            }
            lyricsRecyclerViews.add(new WeakReference<>(recycler));
        }
        info("Observed LyricsRecyclerView attachment");
    }

    private void scheduleLyricsRecyclerPrime(View recycler) {
        if (recycler == null) {
            return;
        }
        recycler.post(() -> {
            primeLyricsRecyclerView(recycler, "attached");
            if (!hasBoundLyricsRecyclerChildren(recycler)) {
                mainHandler.postDelayed(
                        () -> primeLyricsRecyclerView(recycler, "attached-await-children"),
                        180L);
            }
        });
    }

    private void primeRememberedLyricsRecyclerViews(String reason) {
        ArrayList<View> recyclers = new ArrayList<>();
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else {
                    recyclers.add(recycler);
                }
            }
        }
        for (View recycler : recyclers) {
            primeLyricsRecyclerView(recycler, reason);
        }
    }

    private void primeLyricsRecyclerView(View recycler, String reason) {
        WordLyricModel model = currentWordLyricModel;
        if (recycler == null || model == null || model.lines.isEmpty() || !recycler.isAttachedToWindow()) {
            return;
        }
        int targetIndex = model.displayIndexAt(estimatePlaybackPositionMillis());
        stabilizeLyricsRecyclerScroll(recycler, "prime-" + reason);
        boolean alreadyPrimed = lastPrimedLyricsRecyclerView.get() == recycler
                && lastPrimedLyricsRecyclerIndex == targetIndex
                && hasBoundLyricsRecyclerChildren(recycler);
        boolean positioned = alreadyPrimed || invokeLyricsRecyclerSetCurrentLyric(recycler, targetIndex);
        applyVisibleLyricBlockHeights(recycler);
        if (positioned && hasBoundLyricsRecyclerChildren(recycler)) {
            lastPrimedLyricsRecyclerView = new WeakReference<>(recycler);
            lastPrimedLyricsRecyclerIndex = targetIndex;
            lastLyricsRecyclerIndex = targetIndex;
            WordLine line = model.lineAt(targetIndex);
            if (line != null) {
                activeLyricLine = line.normalizedText;
                activeLyricLineTimeMs = line.timeMillis;
            }
            if (!alreadyPrimed) {
                info("Primed LyricsRecyclerView at index=" + targetIndex + ", reason=" + reason);
            }
        }
    }

    private static boolean hasBoundLyricsRecyclerChildren(View recycler) {
        return recycler instanceof ViewGroup && ((ViewGroup) recycler).getChildCount() > 0;
    }

    private boolean invokeLyricsRecyclerSetCurrentLyric(View recycler, int targetIndex) {
        if (lyricsRecyclerSetCurrentUnavailable) {
            return false;
        }
        Class<?> current = recycler.getClass();
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!"setCurrentLyric".equals(method.getName())
                        || parameterTypes.length == 0
                        || parameterTypes[0] != int.class) {
                    continue;
                }
                Object[] args = new Object[parameterTypes.length];
                int integerIndex = 0;
                boolean compatible = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> type = parameterTypes[i];
                    if (type == int.class || type == Integer.class) {
                        args[i] = integerIndex++ == 0 ? targetIndex : -1;
                    } else if (type == boolean.class || type == Boolean.class) {
                        args[i] = false;
                    } else if (type == long.class || type == Long.class) {
                        args[i] = 0L;
                    } else if (type == float.class || type == Float.class) {
                        args[i] = 0f;
                    } else if (!type.isPrimitive()) {
                        args[i] = null;
                    } else {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    suppressLyricsRecyclerHook.set(true);
                    method.invoke(recycler, args);
                    return true;
                } catch (Throwable ignored) {
                    // The adapter may not be ready yet; attachment retries will try again.
                } finally {
                    suppressLyricsRecyclerHook.remove();
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean isLyricsRecyclerView(View view) {
        return view != null && view.getClass().getName().contains("LyricsRecyclerView");
    }

    private void stabilizeLyricsRecyclerScroll(View recycler, String reason) {
        if (recycler == null) {
            return;
        }
        stopLyricsRecyclerScroll(recycler);
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

    private void offsetLyricsRecyclerCurrentLine(View recyclerView, int targetIndex) {
        if (recyclerView == null
                || targetIndex < 0
                || !recyclerView.isAttachedToWindow()
                || recyclerView.getHeight() <= 0) {
            return;
        }
        int centerOffset = dp(recyclerView.getContext(), ACTIVE_LYRIC_CENTER_OFFSET_DP);
        if (centerOffset == 0) {
            return;
        }
        int desiredCenter = recyclerView.getHeight() / 2 + centerOffset;
        int itemHeight = dp(recyclerView.getContext(), LYRIC_SLOT_HEIGHT_DP);
        int topOffset = Math.max(0, desiredCenter - itemHeight / 2);
        if (scrollLyricsRecyclerToPositionWithOffset(recyclerView, targetIndex, topOffset)) {
            return;
        }
        LyricsRecyclerGeometry geometry = captureLyricsRecyclerGeometry(
                recyclerView,
                targetIndex);
        if (geometry.targetCenter == Integer.MIN_VALUE) {
            return;
        }
        int delta = desiredCenter - geometry.targetCenter;
        int tolerance = dp(recyclerView.getContext(), 2f);
        if (Math.abs(delta) <= tolerance) {
            return;
        }
        recyclerView.scrollBy(0, -delta);
    }

    private boolean scrollLyricsRecyclerToPositionWithOffset(
            View recyclerView,
            int targetIndex,
            int topOffset) {
        Object layoutManager = invokeNoArgByName(recyclerView, "getLayoutManager");
        if (layoutManager == null) {
            return false;
        }
        return invokeTwoIntByName(
                layoutManager,
                "scrollToPositionWithOffset",
                targetIndex,
                topOffset);
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
                        normalizedTextOf(textView),
                        estimatePlaybackPositionMillis());
                if (match.line != null) {
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
            Class<?> current = lyricsRecyclerViewClass;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!"setCurrentLyric".equals(method.getName())
                            || method.getParameterTypes().length == 0
                            || method.getParameterTypes()[0] != int.class) {
                        continue;
                    }
                    method.setAccessible(true);
                    hook(method)
                            .setId(HOOK_ID_LYRICS_RECYCLER
                                    + "-"
                                    + hooked
                                    + "-"
                                    + method.getParameterTypes().length)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(this::onLyricsRecyclerSetCurrentLyric);
                    hooked++;
                }
                current = current.getSuperclass();
            }
            if (hooked > 0) {
                info("Hooked LyricsRecyclerView#setCurrentLyric, methods=" + hooked);
            } else {
                lyricsRecyclerSetCurrentUnavailable = true;
                info("No LyricsRecyclerView#setCurrentLyric hook target found on "
                        + lyricsRecyclerViewClass.getName());
            }
            lyricsRecyclerHookInstalled = true;
        } catch (Throwable t) {
            error("Failed to hook LyricsRecyclerView#setCurrentLyric", t);
        }
    }

    private void rememberActiveLyricTextView(Object view, WordLine line) {
        if (!(view instanceof TextView) || line == null || TextUtils.isEmpty(line.text)) {
            return;
        }
        TextView textView = (TextView) view;
        activeLyricLine = line.normalizedText;
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

        int adapterPosition = findValidLyricsRecyclerAdapterPosition(textView, model);
        WordLine indexedLine = model.lineAtOfficialIndex(adapterPosition);
        if (indexedLine == null) {
            indexedLine = model.lineAt(adapterPosition);
        }
        WordLine line = null;
        WordLine translationLine = null;
        if (indexedLine != null) {
            if (matchesWordLineText(indexedLine, normalizedText)) {
                line = indexedLine;
            } else if (!TextUtils.isEmpty(indexedLine.translation)
                    && indexedLine.normalizedTranslation().equals(normalizedText)) {
                translationLine = indexedLine;
            }
            // The official list can omit credits or pre-roll its first lyric, so its adapter
            // position is only a hint. Fall through to the nearby text match when it differs.
        }
        boolean duplicateText = model.hasDuplicateRenderableText(normalizedText);
        WordLine mappedAnchor = model.lineAtOfficialIndex(
                adapterPosition >= 0 ? adapterPosition : lastLyricsRecyclerIndex);
        int mappedAnchorIndex = model.indexOfLine(mappedAnchor);
        int anchorIndex = mappedAnchorIndex >= 0
                ? mappedAnchorIndex
                : model.displayIndexAt(position);
        if (line == null
                && translationLine == null
                && anchorIndex >= 0) {
            line = model.findLineByTextNearIndex(normalizedText, anchorIndex, 2, false);
            translationLine = model.findLineByTranslationNearIndex(normalizedText, anchorIndex, 2);
        }
        if (line == null && translationLine == null && duplicateText) {
            WordLine activeLine = model.findActiveLine(position);
            if (activeLine != null) {
                if (LockscreenIntegrationPolicy.activeTextMatches(
                        normalizedText, activeLine.normalizedText)) {
                    line = activeLine;
                } else if (LockscreenIntegrationPolicy.activeTextMatches(
                        normalizedText, activeLine.normalizedTranslation())) {
                    translationLine = activeLine;
                }
            }
            if (line == null && translationLine == null) {
                return LyricTextMatch.EMPTY;
            }
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
        WordLyricModel model = currentWordLyricModel;
        if (model == null || !isReadyForLyricDraw(textView)) {
            return null;
        }
        if (!isInLyricsRecyclerView(textView)) {
            return null;
        }
        ensureScreenTimeoutReceiver(textView.getContext());

        CharSequence currentText = textView.getText();
        if (currentText == null || currentText.length() == 0 || currentText.length() > 240) {
            return null;
        }

        String normalizedText = normalizedTextOf(textView);
        if (TextUtils.isEmpty(normalizedText)) {
            return null;
        }

        long position = estimatePlaybackPositionMillis();
        WordLine activeLine = model.findActiveLine(position);
        WordLine seedlingActiveLine = recentSeedlingActiveLine(model);
        if (seedlingActiveLine != null
                && (activeLine == null
                || Math.abs(seedlingActiveLine.timeMillis - position)
                <= Math.abs(activeLine.timeMillis - position) + 1_000L)) {
            activeLine = seedlingActiveLine;
        }
        int adapterPosition = findValidLyricsRecyclerAdapterPosition(textView, model);
        WordLine indexedLine = model.lineAtOfficialIndex(adapterPosition);
        if (indexedLine == null) {
            indexedLine = model.lineAt(adapterPosition);
        }
        WordLine line = null;
        WordLine translationLine = null;
        if (indexedLine != null) {
            if (matchesWordLineText(indexedLine, normalizedText)) {
                line = indexedLine;
            } else if (!TextUtils.isEmpty(indexedLine.translation)
                    && indexedLine.normalizedTranslation().equals(normalizedText)) {
                translationLine = indexedLine;
            }
            // The official list can omit credits or pre-roll its first lyric, so its adapter
            // position is only a hint. Fall through to the nearby text match when it differs.
        }
        boolean duplicateText = model.hasDuplicateRenderableText(normalizedText);
        WordLine mappedAnchor = model.lineAtOfficialIndex(
                adapterPosition >= 0 ? adapterPosition : lastLyricsRecyclerIndex);
        int mappedAnchorIndex = model.indexOfLine(mappedAnchor);
        int anchorIndex = mappedAnchorIndex >= 0
                ? mappedAnchorIndex
                : model.displayIndexAt(position);
        if (line == null
                && translationLine == null
                && anchorIndex >= 0) {
            line = model.findLineByTextNearIndex(normalizedText, anchorIndex, 2, false);
            translationLine = model.findLineByTranslationNearIndex(normalizedText, anchorIndex, 2);
        }
        if (line == null && translationLine == null && duplicateText) {
            if (activeLine != null) {
                if (LockscreenIntegrationPolicy.activeTextMatches(
                        normalizedText, activeLine.normalizedText)) {
                    line = activeLine;
                } else if (LockscreenIntegrationPolicy.activeTextMatches(
                        normalizedText, activeLine.normalizedTranslation())) {
                    translationLine = activeLine;
                }
            }
            if (line == null && translationLine == null) {
                return null;
            }
        }
        if (line == null && translationLine == null) {
            line = activeLine != null && matchesWordLineText(activeLine, normalizedText)
                    ? activeLine
                    : model.findLineByText(normalizedText, position);
            translationLine = activeLine != null
                    && !TextUtils.isEmpty(activeLine.translation)
                    && activeLine.normalizedTranslation().equals(normalizedText)
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
            return null;
        }
        if (line == null
                && normalizedText.equals(activeLyricLine)
                && !duplicateText) {
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
                && activeLine.normalizedText.equals(line.normalizedText);
        rememberActiveTextViewReference(textView);
        if (active) {
            activeLyricLine = line.normalizedText;
            activeLyricLineTimeMs = line.timeMillis;
            activeRendererTextView = new WeakReference<>(textView);
            activeRendererWordLine = line;
            scheduleActiveLyricRefresh(
                    textView,
                    lastPlaybackIsPlaying ? ACTIVE_LYRIC_FRAME_DELAY_MS : 500L);
        }
        int lineIndex = model.indexOfLine(line);
        WordLine focusAnchor = resolveFocusAnchorLine(model, activeLine, position);
        int focusAnchorIndex = model.indexOfLine(focusAnchor);
        boolean focused = active || isFocusedLyricLine(
                focusAnchor,
                line,
                focusAnchorIndex,
                lineIndex);
        return new DrawFrame(
                model,
                line,
                lineIndex,
                focusAnchorIndex,
                position,
                resolveLyricGlowPosition(position),
                active,
                focused);
    }

    private WordLine recentSeedlingActiveLine(WordLyricModel model) {
        if (model == null
                || lastSeedlingActiveLineTimeMs < 0L
                || lastSeedlingActiveLineObservedAtMs < 0L
                || SystemClock.elapsedRealtime() - lastSeedlingActiveLineObservedAtMs > 6_000L) {
            return null;
        }
        return model.findLineAtTime(lastSeedlingActiveLineTimeMs);
    }

    private static WordLine resolveFocusAnchorLine(
            WordLyricModel model, WordLine activeLine, long position) {
        if (activeLine != null || model == null) {
            return activeLine;
        }
        return model.lineAt(model.displayIndexAt(position));
    }

    private static boolean isFocusedLyricLine(
            WordLine focusAnchor,
            WordLine line,
            int focusAnchorIndex,
            int lineIndex) {
        if (focusAnchor == null || line == null || focusAnchorIndex < 0) {
            return false;
        }
        if (lineIndex == focusAnchorIndex) {
            return true;
        }
        return shouldFocusNextLyricLine(
                focusAnchor,
                line,
                focusAnchorIndex,
                lineIndex);
    }

    private static boolean isReadyForLyricDraw(TextView textView) {
        return textView != null
                && textView.isAttachedToWindow()
                && textView.getVisibility() == View.VISIBLE
                && textView.getWidth() > 0
                && textView.getHeight() > 0;
    }

    private static boolean shouldFocusNextLyricLine(
            WordLine activeLine,
            WordLine line,
            int activeIndex,
            int lineIndex) {
        if (activeLine == null || line == null || activeIndex < 0 || lineIndex != activeIndex + 1) {
            return false;
        }
        if (!TextUtils.isEmpty(line.translation)) {
            return true;
        }
        return !isShortStandaloneLyricLine(activeLine) && !isShortStandaloneLyricLine(line);
    }

    private void noteVisibleLockscreenLyricTextView(TextView textView) {
        if (textView == null
                || !isInLyricsRecyclerView(textView)
                || !isEffectivelyVisible(textView)
                || !hasUsableLyricText(textView)) {
            return;
        }
        lastVisibleOfficialLyricTextViewAt = SystemClock.elapsedRealtime();
        if (screenTimeoutPausedByUserPresent
                && isKeyguardShowingForScreenTimeout(textView.getContext())) {
            screenTimeoutPausedByUserPresent = false;
            maybeLogScreenTimeout(
                    "Resumed screen timeout keep-awake after visible lyric confirmed keyguard",
                    true);
        }
        if (screenTimeoutPausedByScreenOff
                || screenTimeoutPausedByUserPresent
                || !hasSupportedSystemUiPlayer()
                || !lastPlaybackIsPlaying) {
            return;
        }
        if (!systemUiLyricModeKeepAwakeActive) {
            systemUiLyricModeKeepAwakeActive = true;
            lastSystemUiLyricModeLogAt = SystemClock.elapsedRealtime();
            maybeLogLyricModeKeepAwake(true);
            maybeLogScreenTimeout(
                    "Inferred lockscreen lyric UI keep-awake from visible official lyric view",
                    false);
        }
        updateScreenTimeoutWakeLock(textView.getContext());
    }

    private boolean hasUsableLyricText(TextView textView) {
        CharSequence text = textView == null ? null : textView.getText();
        if (text == null || text.length() == 0 || text.length() > 240) {
            return false;
        }
        return !TextUtils.isEmpty(normalizedTextOf(textView));
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
        Method method = resolveRecyclerChildPositionMethod(recyclerView.getClass());
        if (method == null) {
            return -1;
        }
        try {
            Object result = method.invoke(recyclerView, child);
            return result instanceof Number ? ((Number) result).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static Method resolveRecyclerChildPositionMethod(Class<?> recyclerClass) {
        synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
            Method cached = RECYCLER_POSITION_METHOD_CACHE.get(recyclerClass);
            if (cached != null) {
                return cached;
            }
            if (RECYCLER_POSITION_METHOD_MISSING.containsKey(recyclerClass)) {
                return null;
            }
        }
        Method resolved = null;
        String[] methodNames = {
                "getChildBindingAdapterPosition",
                "getChildAdapterPosition",
                "getChildLayoutPosition"
        };
        for (String methodName : methodNames) {
            try {
                resolved = findMethod(recyclerClass, methodName, View.class);
                resolved.setAccessible(true);
                break;
            } catch (Throwable ignored) {
                // Some plugin RecyclerView builds expose only one of these helpers.
            }
        }
        synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
            if (resolved != null) {
                RECYCLER_POSITION_METHOD_CACHE.put(recyclerClass, resolved);
            } else {
                RECYCLER_POSITION_METHOD_MISSING.put(recyclerClass, Boolean.TRUE);
            }
        }
        return resolved;
    }

    private static int findValidLyricsRecyclerAdapterPosition(TextView textView, WordLyricModel model) {
        int position = findLyricsRecyclerAdapterPosition(textView);
        if (model == null
                || position < 0
                || (position >= model.lines.size()
                && position >= model.officialLines.size())) {
            return -1;
        }
        return position;
    }

    private static View findContainingLyricsRecyclerView(TextView textView) {
        if (textView == null) {
            return null;
        }
        Object parent = textView.getParent();
        for (int depth = 0; depth < 12 && parent instanceof View; depth++) {
            View parentView = (View) parent;
            if (parentView.getClass().getName().contains("LyricsRecyclerView")) {
                return parentView;
            }
            parent = parentView.getParent();
        }
        return null;
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

    private void configureOfficialLyricLineSpacing(View recyclerView) {
        if (recyclerView == null) {
            return;
        }
        int requestedSpacing = dp(
                recyclerView.getContext(),
                OFFICIAL_LYRIC_LINE_SPACING_DP);
        int previousSpacing = readIntField(
                recyclerView,
                OFFICIAL_LYRIC_LINE_SPACING_FIELD,
                -1);
        if (previousSpacing < 0) {
            info("Official lyric line spacing field unavailable; keeping plugin default");
            return;
        }
        if (previousSpacing != requestedSpacing) {
            writeFieldValue(
                    recyclerView,
                    OFFICIAL_LYRIC_LINE_SPACING_FIELD,
                    requestedSpacing);
        }
        int appliedSpacing = readIntField(
                recyclerView,
                OFFICIAL_LYRIC_LINE_SPACING_FIELD,
                -1);
        if (appliedSpacing != requestedSpacing) {
            info("Could not configure official lyric line spacing; keeping plugin default");
            return;
        }
        int synchronizedChildren = synchronizeBoundLyricItemSpacing(
                recyclerView,
                previousSpacing,
                requestedSpacing);
        if (previousSpacing != requestedSpacing || synchronizedChildren > 0) {
            info("Configured official lyric line spacing, from=" + previousSpacing
                    + ", to=" + requestedSpacing
                    + ", synchronizedChildren=" + synchronizedChildren);
        }
    }

    private static int synchronizeBoundLyricItemSpacing(
            View recyclerView, int previousSpacing, int requestedSpacing) {
        if (!(recyclerView instanceof ViewGroup) || previousSpacing == requestedSpacing) {
            return 0;
        }
        ViewGroup recyclerGroup = (ViewGroup) recyclerView;
        int synchronizedChildren = 0;
        for (int i = 0; i < recyclerGroup.getChildCount(); i++) {
            View child = recyclerGroup.getChildAt(i);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (!(params instanceof ViewGroup.MarginLayoutParams)) {
                continue;
            }
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
            if (marginParams.bottomMargin != previousSpacing) {
                continue;
            }
            marginParams.bottomMargin = requestedSpacing;
            child.setLayoutParams(marginParams);
            synchronizedChildren++;
        }
        return synchronizedChildren;
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
        boolean changed = false;
        if (Math.abs(view.getAlpha() - 1f) > 0.001f) {
            view.setAlpha(1f);
            changed = true;
        }
        if (Math.abs(view.getScaleX() - 1f) > 0.001f) {
            view.setScaleX(1f);
            changed = true;
        }
        if (Math.abs(view.getScaleY() - 1f) > 0.001f) {
            view.setScaleY(1f);
            changed = true;
        }
        if (view.getLayerType() != View.LAYER_TYPE_NONE) {
            view.setLayerType(View.LAYER_TYPE_NONE, null);
            changed = true;
        }
        boolean disableVendorBlur;
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            disableVendorBlur = changed || !VIEW_BLUR_DISABLED.containsKey(view);
            if (disableVendorBlur) {
                VIEW_BLUR_DISABLED.put(view, Boolean.TRUE);
            }
        }
        if (!disableVendorBlur) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null);
        }
        for (Method method : resolveViewBlurMethods(view.getClass())) {
            try {
                method.invoke(view, false);
            } catch (Throwable ignored) {
                // The standard visual properties above are sufficient when a vendor
                // implementation rejects its optional blur setter.
            }
        }
    }

    private static Method[] resolveViewBlurMethods(Class<?> viewClass) {
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            Method[] cached = VIEW_BLUR_METHOD_CACHE.get(viewClass);
            if (cached != null) {
                return cached;
            }
        }

        ArrayList<Method> resolved = new ArrayList<>(2);
        boolean foundViewBlur = false;
        boolean foundBlur = false;
        Class<?> current = viewClass;
        while (current != null && (!foundViewBlur || !foundBlur)) {
            for (Method method : current.getDeclaredMethods()) {
                String name = method.getName();
                if ((foundViewBlur || !"setViewBlur".equals(name))
                        && (foundBlur || !"setBlur".equals(name))) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1
                        || (parameterTypes[0] != boolean.class
                        && parameterTypes[0] != Boolean.class)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    resolved.add(method);
                    if ("setViewBlur".equals(name)) {
                        foundViewBlur = true;
                    } else {
                        foundBlur = true;
                    }
                } catch (Throwable ignored) {
                    // Keep looking in the superclass.
                }
            }
            current = current.getSuperclass();
        }
        Method[] methods = resolved.toArray(new Method[0]);
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            VIEW_BLUR_METHOD_CACHE.put(viewClass, methods);
        }
        return methods;
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
        if (textView == null) {
            return;
        }
        if (activeLyricUpdatePosted || !systemUiLyricModeKeepAwakeActive) {
            return;
        }
        activeLyricUpdatePosted = true;
        if (delayMillis <= ACTIVE_LYRIC_FRAME_DELAY_MS && textView.isAttachedToWindow()) {
            textView.postOnAnimation(activeLyricRefreshRunnable);
        } else {
            mainHandler.postDelayed(
                    activeLyricRefreshRunnable,
                    Math.max(16L, Math.min(delayMillis, 600L)));
        }
    }

    private void scheduleActiveLyricRefresh(long delayMillis) {
        if (activeLyricUpdatePosted || !systemUiLyricModeKeepAwakeActive) {
            return;
        }
        activeLyricUpdatePosted = true;
        mainHandler.postDelayed(
                activeLyricRefreshRunnable,
                Math.max(16L, Math.min(delayMillis, 600L)));
    }

    private void refreshActiveLyricTextView() {
        if (!systemUiLyricModeKeepAwakeActive) {
            return;
        }
        WordLyricModel model = currentWordLyricModel;
        if (model == null) {
            updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }

        ArrayList<TextView> candidates = activeLyricRefreshCandidates;
        candidates.clear();
        collectActiveTextViews(candidates);
        boolean hasSearchRoot = hasAttachedLyricsRecyclerView();
        if (candidates.isEmpty() && !hasSearchRoot) {
            return;
        }

        long position = estimatePlaybackPositionMillis();
        WordLine line = model.findActiveLine(position);
        if (line == null && position >= 0L) {
            line = model.lineAt(model.displayIndexAt(position));
        }
        if (line == null && position < 0L) {
            line = model.findLineAtTime(activeLyricLineTimeMs);
        }
        if (line == null) {
            return;
        }
        activeLyricLine = line.normalizedText;
        activeLyricLineTimeMs = line.timeMillis;
        TextView directTarget = activeRendererTextView.get();
        if (line == activeRendererWordLine
                && directTarget != null
                && directTarget.isAttachedToWindow()
                && isEffectivelyVisible(directTarget)
                && matchesWordLineText(line, normalizedTextOf(directTarget))) {
            directTarget.postInvalidateOnAnimation();
            scheduleActiveLyricRefresh(
                    directTarget,
                    lastPlaybackIsPlaying ? ACTIVE_LYRIC_FRAME_DELAY_MS : 500L);
            return;
        }
        if (candidates.isEmpty()) {
            mergeVisibleLyricTextViewsFromRoots(candidates, model, activeLyricLine);
        }

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
            String normalized = normalizedTextOf(textView);
            if (!normalized.equals(activeLyricLine) && !model.hasRenderableText(normalized)) {
                continue;
            }
            LyricTextMatch match = findLyricTextMatch(model, textView, normalized, position);
            if (!sameWordLine(match.line, line)
                    && !sameWordLine(match.translationLine, line)) {
                continue;
            }

            textView.postInvalidateOnAnimation();
            activeRendererTextView = new WeakReference<>(textView);
            activeRendererWordLine = line;
            updated++;
        }

        if (updated == 0 && visible > 0) {
            updated = invalidateRenderableVisibleLyricTextViews(candidates, model, position);
        }
        if (visible == 0 || updated == 0) {
            scheduleLyricVisibilityRecovery(
                    visible == 0 ? "hidden-candidates" : "unmatched-candidates");
        }

        maybeLogActiveRefresh(position, line.text, candidates.size(), attached, visible, updated);
        long nextDelay = !lastPlaybackIsPlaying
                ? 500L
                : updated > 0
                ? ACTIVE_LYRIC_FRAME_DELAY_MS
                : ACTIVE_LYRIC_RETRY_DELAY_MS;
        TextView nextAnchor = firstActiveLyricTextView();
        if (nextAnchor != null) {
            scheduleActiveLyricRefresh(nextAnchor, nextDelay);
        } else if (hasSearchRoot) {
            scheduleActiveLyricRefresh(nextDelay);
        }
    }

    private int invalidateRenderableVisibleLyricTextViews(
            ArrayList<TextView> candidates, WordLyricModel model, long position) {
        if (candidates == null || model == null) {
            return 0;
        }
        int updated = 0;
        for (TextView textView : candidates) {
            if (textView == null
                    || !textView.isAttachedToWindow()
                    || !isEffectivelyVisible(textView)) {
                continue;
            }
            CharSequence currentText = textView.getText();
            if (currentText == null || currentText.length() == 0 || currentText.length() > 240) {
                continue;
            }
            String normalized = normalizedTextOf(textView);
            if (!model.hasRenderableText(normalized)) {
                continue;
            }
            LyricTextMatch match = findLyricTextMatch(model, textView, normalized, position);
            WordLine visibleLine = match.line != null ? match.line : match.translationLine;
            if (visibleLine == null) {
                continue;
            }
            textView.postInvalidateOnAnimation();
            rememberActiveTextViewReference(textView);
            if (updated == 0) {
                activeRendererTextView = new WeakReference<>(textView);
                activeRendererWordLine = visibleLine;
            }
            updated++;
        }
        return updated;
    }

    private void scheduleLyricVisibilityRecovery(String reason) {
        if (lyricVisibilityRecoveryPosted || currentWordLyricModel == null) {
            return;
        }
        lyricVisibilityRecoveryPosted = true;
        long[] delays = {
                LYRIC_VISIBILITY_RECOVERY_FIRST_DELAY_MS,
                LYRIC_VISIBILITY_RECOVERY_SECOND_DELAY_MS,
                LYRIC_VISIBILITY_RECOVERY_FINAL_DELAY_MS
        };
        for (int i = 0; i < delays.length; i++) {
            boolean finalPass = i == delays.length - 1;
            mainHandler.postDelayed(() -> {
                try {
                    recoverLyricVisibility(reason);
                } finally {
                    if (finalPass) {
                        lyricVisibilityRecoveryPosted = false;
                    }
                }
            }, delays[i]);
        }
    }

    private void recoverLyricVisibility(String reason) {
        if (currentWordLyricModel == null) {
            return;
        }
        if (officialLyricDrawSuppressedUntilElapsedMs > 0L
                && SystemClock.elapsedRealtime() >= officialLyricDrawSuppressedUntilElapsedMs) {
            officialLyricDrawSuppressedUntilElapsedMs = 0L;
            pendingCustomLyricTakeoverFade = false;
            restoreSuppressedLyricsRecyclerViews(false);
        }

        boolean touched = false;
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (recycler == null
                    || !recycler.isAttachedToWindow()
                    || recycler.getVisibility() != View.VISIBLE
                    || recycler.getWidth() <= 0
                    || recycler.getHeight() <= 0) {
                continue;
            }
            primeLyricsRecyclerView(recycler, "visibility-recovery");
            applyVisibleLyricBlockHeights(recycler);
            invalidateLyricsRecyclerDescendants(recycler);
            recycler.postInvalidateOnAnimation();
            touched = true;
        }
        for (TextView textView : snapshotActiveTextViews()) {
            if (textView == null || !textView.isAttachedToWindow()) {
                continue;
            }
            textView.requestLayout();
            textView.invalidate();
            textView.postInvalidateOnAnimation();
            touched = true;
        }
        if (!touched) {
            return;
        }
        maybeLogLyricVisibilityRecovery(reason);
        if (systemUiLyricModeKeepAwakeActive) {
            scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS);
        }
    }

    private ArrayList<TextView> snapshotActiveTextViews() {
        ArrayList<TextView> result = new ArrayList<>();
        collectActiveTextViews(result);
        return result;
    }

    private void collectActiveTextViews(ArrayList<TextView> result) {
        if (result == null) {
            return;
        }
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView textView = activeLyricTextViews.get(i).get();
                if (textView == null
                        || !textView.isAttachedToWindow()
                        || !isInLyricsRecyclerView(textView)) {
                    activeLyricTextViews.remove(i);
                } else {
                    result.add(textView);
                }
            }
        }
    }

    private boolean hasAttachedLyricsRecyclerView() {
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null || !recycler.isAttachedToWindow()) {
                    lyricsRecyclerViews.remove(i);
                } else {
                    return true;
                }
            }
        }
        return false;
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

    private ArrayList<View> snapshotLyricsRecyclerViews() {
        ArrayList<View> result = new ArrayList<>();
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null || !recycler.isAttachedToWindow()) {
                    lyricsRecyclerViews.remove(i);
                } else {
                    result.add(recycler);
                }
            }
        }
        return result;
    }

    private TextView firstActiveLyricTextView() {
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView textView = activeLyricTextViews.get(i).get();
                if (textView == null
                        || !textView.isAttachedToWindow()
                        || !isInLyricsRecyclerView(textView)) {
                    activeLyricTextViews.remove(i);
                } else if (isEffectivelyVisible(textView)) {
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
            View recycler = findContainingLyricsRecyclerView(textView);
            if (recycler != null && recycler.isAttachedToWindow() && !containsView(roots, recycler)) {
                roots.add(recycler);
            }
        }
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (recycler != null && recycler.isAttachedToWindow() && !containsView(roots, recycler)) {
                roots.add(recycler);
            }
        }

        for (View root : roots) {
            collectVisibleLyricTextViews(root, model, normalizedLine, candidates, new int[]{0});
        }
    }

    private void collectVisibleLyricTextViews(
            View view, WordLyricModel model, String normalizedLine, ArrayList<TextView> candidates, int[] visited) {
        if (view == null || visited[0]++ > 220 || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0.05f) {
            return;
        }

        if (view instanceof TextView && isEffectivelyVisible(view)) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            String normalized = text == null ? "" : normalizedTextOf(textView);
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
        Rect visibleRect = VIEW_VISIBLE_RECT.get();
        visibleRect.setEmpty();
        return view.getGlobalVisibleRect(visibleRect) && !visibleRect.isEmpty();
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

    private static void tryInvokeNoArgByName(Object target, String methodName) {
        if (target == null || TextUtils.isEmpty(methodName)) {
            return;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(target);
                return;
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }
    }

    private static Object invokeNoArgByName(Object target, String methodName) {
        if (target == null || TextUtils.isEmpty(methodName)) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean invokeTwoIntByName(
            Object target,
            String methodName,
            int first,
            int second) {
        if (target == null || TextUtils.isEmpty(methodName)) {
            return false;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            Method[] methods = current.getDeclaredMethods();
            for (Method method : methods) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!methodName.equals(method.getName()) || parameterTypes.length != 2) {
                    continue;
                }
                if (!isIntParameter(parameterTypes[0]) || !isIntParameter(parameterTypes[1])) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(target, first, second);
                    return true;
                } catch (Throwable ignored) {
                    // Try another overload or superclass implementation.
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean isIntParameter(Class<?> type) {
        return type == int.class || type == Integer.class;
    }

    private static void writeFieldValue(Object target, String fieldName, Object value) {
        if (target == null || TextUtils.isEmpty(fieldName)) {
            return;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
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

    private static Context applicationContextOf(Context context) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext();
        return appContext == null ? context : appContext;
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
                        releaseScreenTimeoutWakeLock("user present");
                        scheduleScreenTimeoutUserPresentRecheck(receiverContext);
                        maybeLogScreenTimeout(
                                "Paused screen timeout keep-awake pending keyguard recheck",
                                true);
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

    private void scheduleScreenTimeoutUserPresentRecheck(Context context) {
        if (screenTimeoutUserPresentRecheckPosted) {
            return;
        }
        screenTimeoutUserPresentRecheckPosted = true;
        Context appContext = applicationContextOf(context);
        mainHandler.postDelayed(() -> {
            screenTimeoutUserPresentRecheckPosted = false;
            recheckScreenTimeoutAfterUserPresent(appContext);
        }, SCREEN_TIMEOUT_USER_PRESENT_RECHECK_DELAY_MS);
    }

    private void recheckScreenTimeoutAfterUserPresent(Context context) {
        if (!screenTimeoutPausedByUserPresent) {
            updateScreenTimeoutWakeLock(context);
            return;
        }
        if (screenTimeoutPausedByScreenOff) {
            return;
        }
        if (isKeyguardShowingForScreenTimeout(context)) {
            screenTimeoutPausedByUserPresent = false;
            maybeLogScreenTimeout(
                    "Resumed screen timeout keep-awake after keyguard remained visible",
                    true);
            updateScreenTimeoutWakeLock(context);
            return;
        }
        systemUiLyricModeKeepAwakeActive = false;
        clearScreenTimeoutLyricEvidence();
        releaseScreenTimeoutWakeLock("keyguard dismissed");
        maybeLogScreenTimeout("Stopped screen timeout keep-awake after keyguard dismissed", true);
    }

    private void updateScreenTimeoutWakeLock(Context context) {
        if (!shouldHoldScreenTimeoutWakeLock(context)) {
            maybeLogScreenTimeoutSkip(context);
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
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                        TAG + ":ScreenTimeout");
                wakeLock.setReferenceCounted(false);
                screenTimeoutWakeLock = wakeLock;
            }
            boolean wasHeld = wakeLock.isHeld();
            if (!wasHeld) {
                wakeLock.acquire(SCREEN_TIMEOUT_WAKE_LOCK_LEASE_MS);
            }
            if (!wasHeld) {
                maybeLogScreenTimeout(
                        "Acquired bright screen timeout wake lock lease="
                                + SCREEN_TIMEOUT_WAKE_LOCK_LEASE_MS + "ms",
                        true);
                pulseScreenTimeoutUserActivity(powerManager, true);
            }
            scheduleScreenTimeoutUserActivityPulse();
        } catch (Throwable t) {
            error("Failed to update screen timeout wake lock", t);
        }
    }

    private boolean shouldHoldScreenTimeoutWakeLock(Context context) {
        return systemUiLyricModeKeepAwakeActive
                && !screenTimeoutPausedByScreenOff
                && !screenTimeoutPausedByUserPresent
                && hasSupportedSystemUiPlayer()
                && lastPlaybackIsPlaying
                && hasScreenTimeoutLyricEvidence()
                && isKeyguardShowingForScreenTimeout(context)
                && isScreenInteractiveForWakeLock();
    }

    private boolean hasSupportedSystemUiPlayer() {
        return lastSystemUiPackageSupported
                || (!TextUtils.isEmpty(currentLyricProviderPackage)
                && isCurrentLyricProviderPackage(currentLyricProviderPackage));
    }

    private boolean hasScreenTimeoutLyricEvidence() {
        return hasRecentVisibleOfficialLyricTextView()
                || hasRecentScreenTimeoutLyricModelEvidence();
    }

    private boolean hasRecentVisibleOfficialLyricTextView() {
        long lastVisibleAt = lastVisibleOfficialLyricTextViewAt;
        if (lastVisibleAt <= 0L) {
            return false;
        }
        long age = SystemClock.elapsedRealtime() - lastVisibleAt;
        return age >= 0L && age <= SCREEN_TIMEOUT_VISIBLE_LYRIC_VIEW_MAX_AGE_MS;
    }

    private boolean hasRecentScreenTimeoutLyricModelEvidence() {
        long until = screenTimeoutLyricEvidenceGraceUntilElapsedMs;
        return until > 0L && SystemClock.elapsedRealtime() <= until;
    }

    private void markScreenTimeoutLyricModelEvidence() {
        screenTimeoutLyricEvidenceGraceUntilElapsedMs =
                SystemClock.elapsedRealtime() + SCREEN_TIMEOUT_MODEL_EVIDENCE_GRACE_MS;
    }

    private void clearScreenTimeoutLyricEvidence() {
        lastVisibleOfficialLyricTextViewAt = 0L;
        screenTimeoutLyricEvidenceGraceUntilElapsedMs = 0L;
    }

    private boolean isScreenInteractiveForWakeLock() {
        PowerManager powerManager = screenTimeoutPowerManager;
        return powerManager == null || powerManager.isInteractive();
    }

    private boolean isKeyguardShowingForScreenTimeout(Context context) {
        Context appContext = applicationContextOf(context);
        if (appContext == null) {
            return false;
        }
        try {
            KeyguardManager keyguardManager =
                    (KeyguardManager) appContext.getSystemService(Context.KEYGUARD_SERVICE);
            return keyguardManager != null && keyguardManager.isKeyguardLocked();
        } catch (Throwable t) {
            maybeLogScreenTimeout("Skip screen timeout wake lock: failed to read keyguard state", false);
            return false;
        }
    }

    private void maybeLogScreenTimeoutSkip(Context context) {
        if (!systemUiLyricModeKeepAwakeActive) {
            return;
        }
        maybeLogScreenTimeout("Skip screen timeout wake lock: screenOff=" + screenTimeoutPausedByScreenOff
                + ", userPresent=" + screenTimeoutPausedByUserPresent
                + ", supportedPlayer=" + hasSupportedSystemUiPlayer()
                + ", playing=" + lastPlaybackIsPlaying
                + ", hasModel=" + (currentWordLyricModel != null)
                + ", hasOfficialLyric=" + systemUiHasOfficialLyric
                + ", recentOfficialView=" + hasRecentVisibleOfficialLyricTextView()
                + ", modelGrace=" + hasRecentScreenTimeoutLyricModelEvidence()
                + ", keyguardShowing=" + isKeyguardShowingForScreenTimeout(context)
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

    private void renewScreenTimeoutWakeLockLease(PowerManager powerManager) {
        if (powerManager == null) {
            return;
        }
        PowerManager.WakeLock wakeLock = screenTimeoutWakeLock;
        if (wakeLock == null) {
            return;
        }
        try {
            boolean wasHeld = wakeLock.isHeld();
            wakeLock.acquire(SCREEN_TIMEOUT_WAKE_LOCK_LEASE_MS);
            if (!wasHeld) {
                maybeLogScreenTimeout(
                        "Re-acquired bright screen timeout wake lock lease="
                                + SCREEN_TIMEOUT_WAKE_LOCK_LEASE_MS + "ms",
                        true);
            }
        } catch (Throwable t) {
            error("Failed to renew screen timeout wake lock lease", t);
        }
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

    void installInjectedTranslationToggleActionHook(String packageName) {
        if (injectedTranslationToggleActionHookInstalled) {
            return;
        }
        synchronized (this) {
            if (injectedTranslationToggleActionHookInstalled) {
                return;
            }
            try {
                Method setPlaybackState = MediaSession.class.getDeclaredMethod(
                        "setPlaybackState",
                        PlaybackState.class);
                hook(setPlaybackState)
                        .setId(HOOK_ID_SET_PLAYBACK_STATE_TRANSLATION_ACTION + "-" + packageName)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onSetPlaybackStateForTranslationAction);
                injectedTranslationToggleActionHookInstalled = true;
                info("Hooked MediaSession#setPlaybackState for translation action in "
                        + packageName);
            } catch (Throwable t) {
                error("Failed to hook MediaSession#setPlaybackState for translation action in "
                        + packageName, t);
            }
        }
    }

    private Object onSetPlaybackStateForTranslationAction(
            XposedInterface.Chain chain) throws Throwable {
        Object stateArg = chain.getArg(0);
        if (!(stateArg instanceof PlaybackState)) {
            return chain.proceed();
        }

        PlaybackState original = (PlaybackState) stateArg;
        if (hasCustomAction(original, TRANSLATION_TOGGLE_ACTION)) {
            return chain.proceed();
        }
        List<PlaybackState.CustomAction> customActions = original.getCustomActions();
        int originalCustomActionCount = customActions == null ? 0 : customActions.size();

        try {
            int iconResource = resolveTranslationActionPlaceholderIcon(original);
            PlaybackState.CustomAction translationAction =
                    new PlaybackState.CustomAction.Builder(
                            TRANSLATION_TOGGLE_ACTION,
                            TRANSLATION_ACTION_NAME,
                            iconResource)
                            .build();
            PlaybackState patched =
                    copyPlaybackStateAndAppendCustomAction(original, translationAction);
            Object[] patchedArgs = chain.getArgs().toArray(new Object[0]);
            patchedArgs[0] = patched;
            if (!injectedTranslationToggleActionLogged) {
                injectedTranslationToggleActionLogged = true;
                info("Injected lyricInfo translation action into player PlaybackState"
                        + ", preservedCustomActions=" + originalCustomActionCount);
            }
            return chain.proceed(patchedArgs);
        } catch (Throwable t) {
            if (!injectedTranslationToggleActionFailureLogged) {
                injectedTranslationToggleActionFailureLogged = true;
                error("Failed to inject lyricInfo translation action into PlaybackState", t);
            }
            return chain.proceed();
        }
    }

    private static boolean hasCustomAction(PlaybackState state, String actionId) {
        if (state == null || TextUtils.isEmpty(actionId)) {
            return false;
        }
        List<PlaybackState.CustomAction> actions = state.getCustomActions();
        if (actions == null) {
            return false;
        }
        for (PlaybackState.CustomAction action : actions) {
            if (action != null && actionId.equals(action.getAction())) {
                return true;
            }
        }
        return false;
    }

    private static PlaybackState copyPlaybackStateAndAppendCustomAction(
            PlaybackState original,
            PlaybackState.CustomAction appendedAction) {
        PlaybackState.Builder builder = new PlaybackState.Builder(original);
        if (appendedAction != null) {
            builder.addCustomAction(appendedAction);
        }
        return builder.build();
    }

    private int resolveTranslationActionPlaceholderIcon(PlaybackState state) {
        List<PlaybackState.CustomAction> actions =
                state == null ? null : state.getCustomActions();
        if (actions != null) {
            for (PlaybackState.CustomAction action : actions) {
                if (action != null && action.getIcon() != 0) {
                    return action.getIcon();
                }
            }
        }

        Context context = currentApplicationContext();
        if (context != null && context.getApplicationInfo() != null) {
            int applicationIcon = context.getApplicationInfo().icon;
            if (applicationIcon != 0) {
                return applicationIcon;
            }
        }
        return android.R.drawable.sym_def_app_icon;
    }

    void cacheTimedLyric(String source, String rawLyric) {
        cacheTimedLyric(source, rawLyric, LyricProviderCapabilities.PASSIVE_PARSER);
    }

    void cacheTimedLyric(
            String source,
            String rawLyric,
            LyricProviderCapabilities capabilities) {
        reportLyricSourceEvent(LyricSourceEvent.resolved(
                source,
                "",
                "",
                "",
                inferTrackHintKey(rawLyric),
                "",
                rawLyric,
                System.currentTimeMillis(),
                capabilities));
    }

    @SuppressLint("WrongConstant")
    private Object onSetMetadata(XposedInterface.Chain chain) throws Throwable {
        Object metadataArg = chain.getArg(0);
        if (!(metadataArg instanceof MediaMetadata)) {
            return chain.proceed();
        }

        MediaMetadata original = (MediaMetadata) metadataArg;
        String existingLyricInfo = original.getString(OPLUS_LYRIC_INFO_KEY);

        String title = firstNonEmpty(
                getText(original, MediaMetadata.METADATA_KEY_TITLE),
                getText(original, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        );
        String artist = firstNonEmpty(
                getText(original, MediaMetadata.METADATA_KEY_ARTIST),
                getText(original, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                getText(original, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        );

        Object thisObject = chain.getThisObject();
        rememberPlayerSession(thisObject);
        long duration = original.getLong(MediaMetadata.METADATA_KEY_DURATION);
        MediaMetadata stableMetadata = lastMetadata;
        String stableLyricInfo = stableMetadata == null
                ? ""
                : stableMetadata.getString(OPLUS_LYRIC_INFO_KEY);
        MetadataTrackIdentity trackIdentity = resolveMetadataTrackIdentity(
                original,
                title,
                artist,
                existingLyricInfo,
                stableLyricInfo,
                playerLyricSession.recentDocument(System.currentTimeMillis()));
        String trackTitle = trackIdentity.title;
        String trackArtist = trackIdentity.artist;
        if (TextUtils.isEmpty(trackTitle)) {
            return chain.proceed();
        }
        String trackKey = buildTrackKey(trackTitle, trackArtist);
        long observedAtMillis = System.currentTimeMillis();
        LyricSessionReducer.TrackUpdate sessionUpdate = playerLyricSession.observeTrack(
                new LyricSessionReducer.TrackSnapshot(
                        trackTitle,
                        trackArtist,
                        duration,
                        original.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                        original.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)),
                trackIdentity.saltRelay
                        ? LyricSessionReducer.ObservationKind.RELAY_METADATA
                        : LyricSessionReducer.ObservationKind.STABLE_METADATA,
                observedAtMillis);
        boolean trackChanged = sessionUpdate.trackChanged;
        LyricSessionReducer.LyricDocument realLyric = sessionUpdate.document != null
                ? sessionUpdate.document
                : playerLyricSession.documentForTrack(trackKey, observedAtMillis);
        if (sessionUpdate.noDocumentConfirmed) {
            info("Confirmed lyric transaction outcome=" + sessionUpdate.terminalOutcome
                    + " for title=" + trackTitle
                    + ", artist=" + nullToEmpty(trackArtist)
                    + " from pending source event");
        }

        if (shouldPreserveSaltLyricRelayMetadata(
                original,
                stableMetadata,
                stableLyricInfo,
                title,
                trackIdentity)) {
            MediaMetadata relayed = stableLyricInfo.equals(existingLyricInfo)
                    ? original
                    : new MediaMetadata.Builder(original)
                    .putString(OPLUS_LYRIC_INFO_KEY, stableLyricInfo)
                    .build();
            maybeLogPreservedSaltLyricRelay(title);
            Object[] relayedArgs = chain.getArgs().toArray(new Object[0]);
            relayedArgs[0] = relayed;
            return chain.proceed(relayedArgs);
        }

        boolean mayRetainStaleLyricInfo = activeAdapterMayRetainStaleLyricInfo();
        boolean hasExistingLyricInfo = !TextUtils.isEmpty(existingLyricInfo);
        LyricInfoContract.Payload parsedExistingPayload =
                LyricInfoContract.parse(existingLyricInfo);
        boolean invalidExistingLyricInfo = hasExistingLyricInfo
                && parsedExistingPayload == null;
        boolean mismatchedExistingLyricInfo = hasExistingLyricInfo
                && parsedExistingPayload != null
                && !lyricInfoMatchesTrack(parsedExistingPayload, trackTitle, trackArtist);
        boolean confirmingSaltFallbackClear =
                isPendingSaltFallbackClear(trackKey, observedAtMillis);
        boolean sameAsStableLyricInfo = hasExistingLyricInfo
                && !TextUtils.isEmpty(stableLyricInfo)
                && existingLyricInfo.equals(stableLyricInfo);
        boolean unsafeSaltFallbackLyricInfo = mayRetainStaleLyricInfo
                && hasExistingLyricInfo
                && parsedExistingPayload != null
                && LyricInfoTrackMatcher.shouldClearSaltPlayerFallbackLyricInfo(
                parsedExistingPayload,
                trackTitle,
                trackArtist,
                trackChanged,
                confirmingSaltFallbackClear,
                sameAsStableLyricInfo,
                realLyric != null);
        boolean clearExistingLyricInfo = invalidExistingLyricInfo
                || mismatchedExistingLyricInfo
                || unsafeSaltFallbackLyricInfo;
        if (mayRetainStaleLyricInfo
                && hasExistingLyricInfo
                && parsedExistingPayload != null
                && (mismatchedExistingLyricInfo || unsafeSaltFallbackLyricInfo)) {
            noteStaleSaltFallbackLyricInfo(
                    trackKey,
                    trackChanged,
                    confirmingSaltFallbackClear,
                    observedAtMillis,
                    trackTitle,
                    trackArtist);
        } else if (trackChanged || realLyric != null) {
            clearPendingSaltFallbackClear();
        }

        LyricInfoContract.Payload existingPayload =
                clearExistingLyricInfo ? null : parsedExistingPayload;
        boolean hasTrustedPlayerIntegrationData = existingPayload != null
                && existingPayload.hasModuleExtensionData()
                && (!activeAdapterAllowsModuleToReplaceUntrustedLyricInfo()
                || existingPayload.isModuleEnvelope());
        LockscreenIntegrationPolicy.LyricInfoSource lyricInfoSource =
                LockscreenIntegrationPolicy.chooseLyricInfoSource(
                        hasExistingLyricInfo
                                && existingPayload != null
                                && !clearExistingLyricInfo,
                        hasTrustedPlayerIntegrationData,
                        realLyric != null);
        if (lyricInfoSource != LockscreenIntegrationPolicy.LyricInfoSource.MODULE_CAPTURE) {
            if (clearExistingLyricInfo) {
                MediaMetadata cleared = new MediaMetadata.Builder(original)
                        .putString(OPLUS_LYRIC_INFO_KEY, "")
                        .build();
                Object[] clearedArgs = chain.getArgs().toArray(new Object[0]);
                clearedArgs[0] = cleared;
                rememberPlayerMetadata(thisObject, cleared);
                info("Cleared " + lyricInfoClearReason(
                        invalidExistingLyricInfo,
                        mismatchedExistingLyricInfo,
                        unsafeSaltFallbackLyricInfo)
                        + " lyricInfo for title="
                        + trackTitle + ", artist=" + nullToEmpty(trackArtist));
                return chain.proceed(clearedArgs);
            }
            if (lyricInfoSource == LockscreenIntegrationPolicy.LyricInfoSource.NONE
                    && (TextUtils.isEmpty(existingLyricInfo) || trackChanged)) {
                info("Skip lyricInfo injection because no fresh real lyric is cached for title="
                        + trackTitle + ", artist=" + nullToEmpty(trackArtist));
            }
            rememberPlayerMetadata(thisObject, original);
            return chain.proceed();
        }

        String lyricInfo = buildModuleLyricInfo(
                trackTitle,
                trackArtist,
                duration,
                realLyric.lyric,
                realLyric.rawLyric,
                realLyric.source,
                sessionUpdate.generation,
                trackKey);
        if (lyricInfo.equals(existingLyricInfo)) {
            rememberPlayerMetadata(thisObject, original);
            return chain.proceed();
        }
        MediaMetadata patched = new MediaMetadata.Builder(original)
                .putString(OPLUS_LYRIC_INFO_KEY, lyricInfo)
                .build();

        List<Object> args = chain.getArgs();
        Object[] patchedArgs = args.toArray(new Object[0]);
        patchedArgs[0] = patched;
        rememberPlayerMetadata(thisObject, patched);

        info((TextUtils.isEmpty(existingLyricInfo)
                ? "Injected"
                : "Replaced player lyricInfo with")
                + " real " + realLyric.source
                + " lyricInfo for title=" + trackTitle
                + ", artist=" + nullToEmpty(trackArtist)
                + ", generation=" + sessionUpdate.generation);
        return chain.proceed(patchedArgs);
    }

    private void rememberPlayerSession(Object thisObject) {
        if (thisObject instanceof MediaSession) {
            lastSession = (MediaSession) thisObject;
        }
    }

    private void rememberPlayerMetadata(Object thisObject, MediaMetadata metadata) {
        if (!(thisObject instanceof MediaSession) || metadata == null) {
            return;
        }
        lastSession = (MediaSession) thisObject;
        lastMetadata = metadata;
    }

    private MetadataTrackIdentity resolveMetadataTrackIdentity(
            MediaMetadata metadata,
            String incomingTitle,
            String incomingArtist,
            String existingLyricInfo,
            String stableLyricInfo,
            LyricSessionReducer.LyricDocument capturedLyric) {
        MetadataTrackIdentity originalIdentity =
                new MetadataTrackIdentity(incomingTitle, incomingArtist, false);
        if (!activeAdapterSupportsLyricRelayMetadata() || metadata == null) {
            return originalIdentity;
        }

        MetadataTrackIdentity payloadIdentity = resolveSaltRelayPayloadIdentity(
                LyricInfoContract.parse(existingLyricInfo),
                incomingTitle,
                incomingArtist);
        if (payloadIdentity == null) {
            payloadIdentity = resolveSaltRelayPayloadIdentity(
                    LyricInfoContract.parse(stableLyricInfo),
                    incomingTitle,
                    incomingArtist);
        }
        if (payloadIdentity != null) {
            return payloadIdentity;
        }

        TrackIdentity.SaltRelayIdentity parsed =
                TrackIdentity.parseSaltRelayArtist(incomingArtist);
        boolean transientRelayTitle = TextUtils.isEmpty(incomingTitle)
                || isNonLyricInfoLine(incomingTitle, 0L);
        if (parsed != null
                && buildTrackKey(incomingTitle, parsed.artist)
                .equals(buildTrackKey(parsed.title, parsed.artist))) {
            return new MetadataTrackIdentity(parsed.title, parsed.artist, true);
        }
        if (capturedLyric != null
                && parsed != null
                && (transientRelayTitle
                || TrackIdentity.relayIdentityMatchesHint(
                parsed,
                capturedLyric.trackHintKey))) {
            return new MetadataTrackIdentity(parsed.title, parsed.artist, true);
        }
        LyricSessionReducer.TrackSnapshot currentTrack = playerLyricSession.currentTrack();
        if (parsed != null
                && currentTrack != null
                && currentTrack.key.equals(buildTrackKey(parsed.title, parsed.artist))) {
            return new MetadataTrackIdentity(parsed.title, parsed.artist, true);
        }

        boolean titleMatchesCapturedLyric = capturedLyric != null
                && relayTitleMatchesLyricText(
                incomingTitle,
                firstNonEmpty(capturedLyric.rawLyric, capturedLyric.lyric));
        if (!titleMatchesCapturedLyric) {
            return originalIdentity;
        }

        String displayTitle = getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String displayArtist = getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (!TextUtils.isEmpty(displayTitle)
                && !buildTrackKey(displayTitle, displayArtist)
                .equals(buildTrackKey(incomingTitle, incomingArtist))
                && containsNormalizedText(incomingArtist, displayTitle)
                && (TextUtils.isEmpty(displayArtist)
                || containsNormalizedText(incomingArtist, displayArtist))) {
            return new MetadataTrackIdentity(displayTitle, displayArtist, true);
        }

        if (parsed == null) {
            return originalIdentity;
        }
        return new MetadataTrackIdentity(parsed.title, parsed.artist, true);
    }

    private static MetadataTrackIdentity resolveSaltRelayPayloadIdentity(
            LyricInfoContract.Payload payload,
            String incomingTitle,
            String incomingArtist) {
        if (payload == null
                || TextUtils.isEmpty(payload.songName)
                || (!TextUtils.isEmpty(incomingTitle)
                && !relayTitleMatchesLyric(incomingTitle, payload))
                || !containsNormalizedText(incomingArtist, payload.songName)
                || (!TextUtils.isEmpty(payload.artist)
                && !containsNormalizedText(incomingArtist, payload.artist))) {
            return null;
        }
        return new MetadataTrackIdentity(payload.songName, payload.artist, true);
    }

    private boolean shouldPreserveSaltLyricRelayMetadata(
            MediaMetadata incoming,
            MediaMetadata stable,
            String stableLyricInfo,
            String incomingTitle,
            MetadataTrackIdentity trackIdentity) {
        if (!activeAdapterSupportsLyricRelayMetadata()
                || incoming == null
                || stable == null
                || trackIdentity == null
                || !trackIdentity.saltRelay
                || TextUtils.isEmpty(stableLyricInfo)) {
            return false;
        }

        LyricInfoContract.Payload stablePayload = LyricInfoContract.parse(stableLyricInfo);
        if (stablePayload == null) {
            return false;
        }
        if (!buildTrackKey(stablePayload.songName, stablePayload.artist)
                .equals(buildTrackKey(trackIdentity.title, trackIdentity.artist))) {
            return false;
        }

        long incomingDuration = incoming.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long stableDuration = stable.getLong(MediaMetadata.METADATA_KEY_DURATION);
        boolean sameDuration = incomingDuration <= 0L
                || stableDuration <= 0L
                || incomingDuration == stableDuration;
        return sameDuration
                && (TextUtils.isEmpty(incomingTitle)
                || relayTitleMatchesLyric(incomingTitle, stablePayload));
    }

    private static boolean relayTitleMatchesLyric(
            String incomingTitle,
            LyricInfoContract.Payload payload) {
        if (TextUtils.isEmpty(incomingTitle) || payload == null) {
            return false;
        }
        String lyric = LyricInfoContract.containsTimedLrc(payload.rawLyric)
                ? payload.rawLyric
                : payload.lyric;
        return relayTitleMatchesLyricText(incomingTitle, lyric);
    }

    private static boolean relayTitleMatchesLyricText(
            String incomingTitle,
            String lyric) {
        if (TextUtils.isEmpty(lyric)) {
            return false;
        }
        for (String titlePart : incomingTitle.split("\\r?\\n")) {
            String normalizedTitle = cleanPlainLyricText(titlePart);
            if (TextUtils.isEmpty(normalizedTitle)) {
                continue;
            }
            for (String rawLine : splitRawLyricLines(lyric)) {
                String lyricLine = cleanPlainLyricText(rawLine);
                if (!TextUtils.isEmpty(lyricLine)
                        && LockscreenIntegrationPolicy.sameLyricVariant(
                        normalizedTitle,
                        lyricLine)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsNormalizedText(String container, String value) {
        if (TextUtils.isEmpty(container) || TextUtils.isEmpty(value)) {
            return false;
        }
        return normalizeLine(container).contains(normalizeLine(value));
    }

    private boolean isPendingSaltFallbackClear(String trackKey, long nowMillis) {
        if (TextUtils.isEmpty(trackKey)
                || TextUtils.isEmpty(pendingSaltFallbackClearTrackKey)
                || !trackKey.equals(pendingSaltFallbackClearTrackKey)
                || pendingSaltFallbackClearAtMillis < 0L) {
            return false;
        }
        long age = nowMillis - pendingSaltFallbackClearAtMillis;
        return age >= 0L && age <= SALT_STALE_FALLBACK_CONFIRM_WINDOW_MS;
    }

    private void noteStaleSaltFallbackLyricInfo(
            String trackKey,
            boolean trackChanged,
            boolean confirmingPreviousClear,
            long observedAtMillis,
            String title,
            String artist) {
        if (!trackChanged && confirmingPreviousClear) {
            playerLyricSession.markCurrentTrackHasNoDocument(observedAtMillis);
            info("Marked current track lyric unavailable from repeated stale Salt lyricInfo"
                    + " for title=" + title + ", artist=" + nullToEmpty(artist));
        }
        pendingSaltFallbackClearTrackKey = nullToEmpty(trackKey);
        pendingSaltFallbackClearAtMillis = observedAtMillis;
    }

    private void clearPendingSaltFallbackClear() {
        pendingSaltFallbackClearTrackKey = "";
        pendingSaltFallbackClearAtMillis = -1L;
    }

    private static String lyricInfoClearReason(
            boolean invalidExistingLyricInfo,
            boolean mismatchedExistingLyricInfo,
            boolean unsafeSaltFallbackLyricInfo) {
        if (invalidExistingLyricInfo) {
            return "invalid";
        }
        if (mismatchedExistingLyricInfo) {
            return "stale";
        }
        if (unsafeSaltFallbackLyricInfo) {
            return "unsafe Salt fallback";
        }
        return "stale";
    }

    private void maybeLogPreservedSaltLyricRelay(String title) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLyricRelayLogAt < 5_000L) {
            return;
        }
        lastLyricRelayLogAt = now;
        info("Preserved stable lyricInfo for Salt status-bar/car lyric relay, line="
                + shortenForLog(title));
    }

    void reportLyricSourceEvent(LyricSourceEvent sourceEvent) {
        if (sourceEvent == null) {
            return;
        }
        LyricSourceEvent event = normalizeLyricSourceEvent(sourceEvent);
        LyricSessionReducer.EventUpdate update =
                playerLyricSession.acceptSourceEvent(event);
        if (event.outcome == LyricSourceEvent.Outcome.RESOLVED) {
            info("Accepted lyric transaction from " + event.source
                    + ", rawChars=" + event.rawLyric.length()
                    + ", oplusChars=" + event.lyric.length()
                    + ", requestId=" + shortenForLog(event.requestId)
                    + ", identity=" + lyricEventIdentityForLog(event)
                    + ", association="
                    + (update.boundToCurrentTrack ? "current-track" : "pending"));
            if (update.boundToCurrentTrack) {
                publishBoundDocumentToCurrentMetadata(update.document);
            }
            return;
        }
        if (event.outcome == LyricSourceEvent.Outcome.NO_LYRIC
                || event.outcome == LyricSourceEvent.Outcome.PARSE_FAILED) {
            info("Accepted lyric transaction outcome=" + event.outcome
                    + " from " + event.source
                    + ", identity=" + lyricEventIdentityForLog(event)
                    + ", association=" + (update.queued ? "pending" : "current-track"));
        }
    }

    private LyricSourceEvent normalizeLyricSourceEvent(LyricSourceEvent event) {
        if (event.outcome != LyricSourceEvent.Outcome.RESOLVED) {
            return event;
        }
        String rawLyric = firstNonEmpty(event.rawLyric, event.lyric);
        String normalized = sanitizeForOplusLyric(rawLyric);
        if (!looksLikeTimedLrc(normalized)) {
            return LyricSourceEvent.terminal(
                    LyricSourceEvent.Outcome.PARSE_FAILED,
                    event.source,
                    event.requestId,
                    event.mediaId,
                    event.mediaUri,
                    event.trackHintKey,
                    rawLyric,
                    event.occurredAtMillis,
                    event.capabilities);
        }
        return LyricSourceEvent.resolved(
                event.source,
                event.requestId,
                event.mediaId,
                event.mediaUri,
                firstNonEmpty(event.trackHintKey, inferTrackHintKey(rawLyric)),
                normalized,
                rawLyric,
                event.occurredAtMillis,
                event.capabilities);
    }

    private static String lyricEventIdentityForLog(LyricSourceEvent event) {
        if (event == null) {
            return "none";
        }
        if (!TextUtils.isEmpty(event.requestId)) {
            return "request";
        }
        if (!TextUtils.isEmpty(event.mediaId)) {
            return "media-id";
        }
        if (!TextUtils.isEmpty(event.mediaUri)) {
            return "media-uri";
        }
        if (!TextUtils.isEmpty(event.trackHintKey)) {
            return "title-artist";
        }
        return "ordered-fallback";
    }

    @SuppressLint("WrongConstant")
    private void publishBoundDocumentToCurrentMetadata(
            LyricSessionReducer.LyricDocument lyric) {
        MediaSession session = lastSession;
        MediaMetadata metadata = lastMetadata;
        LyricSessionReducer.TrackSnapshot currentTrack = playerLyricSession.currentTrack();
        if (session == null
                || metadata == null
                || lyric == null
                || currentTrack == null
                || !currentTrack.key.equals(lyric.boundTrackKey)) {
            return;
        }

        String existingLyricInfo = metadata.getString(OPLUS_LYRIC_INFO_KEY);
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);

        try {
            LyricInfoContract.Payload existingPayload =
                    LyricInfoContract.parse(existingLyricInfo);
            if (existingPayload != null
                    && existingPayload.hasModuleExtensionData()
                    && !existingPayload.isModuleEnvelope()
                    && !activeAdapterAllowsModuleToReplaceUntrustedLyricInfo()) {
                return;
            }
            String mergedLyricInfo = buildModuleLyricInfo(
                    currentTrack.title,
                    currentTrack.artist,
                    duration,
                    lyric.lyric,
                    lyric.rawLyric,
                    lyric.source,
                    playerLyricSession.generation(),
                    currentTrack.key);
            if (mergedLyricInfo.equals(existingLyricInfo)) {
                return;
            }
            MediaMetadata patched = new MediaMetadata.Builder(metadata)
                    .putString(OPLUS_LYRIC_INFO_KEY, mergedLyricInfo)
                    .build();
            session.setMetadata(patched);
            info("Published current-track lyric document from " + lyric.source);
        } catch (Throwable t) {
            error("Failed to publish current-track lyric document", t);
        }
    }

    private static String inferTrackHintKey(String rawLyric) {
        return LyricInfoTrackMatcher.inferTrackHintKey(rawLyric);
    }

    static String buildTrackKey(String title, String artist) {
        return TrackIdentity.buildKey(title, artist);
    }

    private static boolean lyricInfoMatchesTrack(
            LyricInfoContract.Payload payload, String title, String artist) {
        return LyricInfoTrackMatcher.payloadMatchesTrack(payload, title, artist);
    }

    private static boolean looksLikeTimedLrc(String lyric) {
        return !TextUtils.isEmpty(lyric) && ANY_LRC_TIME_TAG.matcher(lyric).find();
    }

    private void cacheSystemUiLyricModel(LyricInfoContract.Payload payload) {
        String rawLyric = payload == null ? "" : payload.rawLyric;
        systemUiHasOfficialLyric = payload != null
                && (LyricInfoContract.containsTimedLrc(payload.lyric)
                || LyricInfoContract.containsTimedLrc(rawLyric));
        if (systemUiHasOfficialLyric) {
            markScreenTimeoutLyricModelEvidence();
        } else {
            screenTimeoutLyricEvidenceGraceUntilElapsedMs = 0L;
        }
        String signature = buildWordLyricModelSignature(payload);
        boolean replacingModel = currentWordLyricModel != null
                && !TextUtils.isEmpty(signature)
                && !signature.equals(currentWordLyricModelSignature);
        if (replacingModel) {
            lyricModelReplacementInProgress = true;
            beginOfficialLyricTrackHandoff("parsing replacement lyric model");
        }
        if (!LyricInfoContract.containsTimedLrc(rawLyric)) {
            currentWordLyricModel = null;
            currentWordLyricModelSignature = "";
            clearSeedlingActiveLyricHint();
            activeRendererTextView = new WeakReference<>(null);
            activeRendererWordLine = null;
            officialLyricTextRenderer.clearGlowCache();
            lyricModelReplacementInProgress = false;
            info("Accepted SystemUI timed lyric without word model");
            mainHandler.post(this::refreshTranslationActionViewVisibility);
            finishOfficialLyricTrackHandoff();
            updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }

        if (currentWordLyricModel != null
                && !TextUtils.isEmpty(signature)
                && signature.equals(currentWordLyricModelSignature)) {
            updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }

        WordLyricModel model = parseWordLyric(rawLyric, true);
        if (!model.lines.isEmpty()) {
            applyOfficialDisplayTextAliases(model, payload.lyric);
            mergeSupplementalTranslations(model, payload.lyric, rawLyric, false);
            mergeSupplementalTranslations(model, payload.translationLyric, rawLyric, true);
        }
        if (model.lines.isEmpty()) {
            currentWordLyricModel = null;
            currentWordLyricModelSignature = "";
            clearSeedlingActiveLyricHint();
            activeRendererTextView = new WeakReference<>(null);
            activeRendererWordLine = null;
            officialLyricTextRenderer.clearGlowCache();
            lyricModelReplacementInProgress = false;
            info("Lyrics Core returned no renderable model; using official SystemUI lyric renderer");
            mainHandler.post(this::refreshTranslationActionViewVisibility);
            finishOfficialLyricTrackHandoff();
            updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }
        currentWordLyricModel = model;
        currentWordLyricModelSignature = signature;
        clearSeedlingActiveLyricHint();
        lyricModelReplacementInProgress = false;
        activeRendererTextView = new WeakReference<>(null);
        activeRendererWordLine = null;
        officialLyricTextRenderer.clearGlowCache();
        info("Cached SystemUI word lyric model, parser=" + model.parserName
                + ", lines=" + model.lines.size()
                + ", translations=" + model.translationCount());
        mainHandler.post(() -> {
            refreshTranslationActionViewVisibility();
            primeRememberedLyricsRecyclerViews("model-ready");
            invalidateRememberedLyricViews();
        });
        updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private void beginOfficialLyricTrackHandoff(String reason) {
        beginOfficialLyricTrackHandoff(reason, true);
    }

    private void beginOfficialLyricTrackHandoff(String reason, boolean armRowRebind) {
        long now = SystemClock.elapsedRealtime();
        if (!hasVisibleLyricsSurfaceForTrackHandoff(now)) {
            pendingCustomLyricTakeoverFade = false;
            return;
        }
        if (armRowRebind) {
            lyricTrackRowRebindEligibleUntilElapsedMs =
                    now + SYSTEMUI_LYRIC_ROW_REBIND_WINDOW_MS;
        }
        officialLyricDrawSuppressedUntilElapsedMs =
                now + SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS;
        pendingCustomLyricTakeoverFade = true;
        int generation = ++officialLyricHandoffGeneration;
        info("Suppressing official lyric frames during track handoff, reason=" + reason);
        mainHandler.post(() -> {
            suppressRememberedLyricsRecyclerViews();
            invalidateRememberedLyricViews();
        });
        mainHandler.postDelayed(() -> {
            if (generation != officialLyricHandoffGeneration) {
                return;
            }
            officialLyricDrawSuppressedUntilElapsedMs = 0L;
            info("Fading lyric renderer in after handoff timeout");
            restoreSuppressedLyricsRecyclerViews(true);
            invalidateRememberedLyricViews();
        }, SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS);
        scheduleLyricsRecyclerVisibilityWatchdog();
    }

    private void releaseOfficialLyricTrackHandoffForRowRebind() {
        officialLyricDrawSuppressedUntilElapsedMs = 0L;
        pendingCustomLyricTakeoverFade = false;
        officialLyricHandoffGeneration++;
        info("Fading lyric renderer in after RecyclerView row rebind");
        mainHandler.post(() -> {
            restoreSuppressedLyricsRecyclerViews(true);
            invalidateRememberedLyricViews();
        });
        scheduleLyricsRecyclerVisibilityWatchdog();
    }

    private void finishOfficialLyricTrackHandoff() {
        if (officialLyricDrawSuppressedUntilElapsedMs <= 0L) {
            return;
        }
        officialLyricDrawSuppressedUntilElapsedMs = 0L;
        pendingCustomLyricTakeoverFade = false;
        int generation = ++officialLyricHandoffGeneration;
        mainHandler.post(() -> {
            invalidateRememberedLyricViews();
            View anchor = firstAttachedLyricsRecyclerView();
            Runnable restore = () -> {
                if (generation != officialLyricHandoffGeneration
                        || officialLyricDrawSuppressedUntilElapsedMs > 0L) {
                    return;
                }
                restoreSuppressedLyricsRecyclerViews(true);
                invalidateRememberedLyricViews();
            };
            if (anchor != null) {
                anchor.postOnAnimation(restore);
            } else {
                mainHandler.postDelayed(restore, ACTIVE_LYRIC_FRAME_DELAY_MS);
            }
        });
    }

    private void cancelLyricTrackHandoffForImmersiveEntry() {
        officialLyricDrawSuppressedUntilElapsedMs = 0L;
        lyricTrackRowRebindEligibleUntilElapsedMs = 0L;
        pendingCustomLyricTakeoverFade = false;
        officialLyricHandoffGeneration++;
        lyricRecyclerFadeGeneration++;
        lyricRecyclerFadeInUntilElapsedMs = 0L;
        mainHandler.post(() -> {
            restoreSuppressedLyricsRecyclerViews(false);
            invalidateRememberedLyricViews();
        });
    }

    private void scheduleLyricsRecyclerVisibilityWatchdog() {
        mainHandler.postDelayed(() -> {
            long now = SystemClock.elapsedRealtime();
            if (now < officialLyricDrawSuppressedUntilElapsedMs) {
                return;
            }
            boolean restored = false;
            for (View recycler : snapshotLyricsRecyclerViews()) {
                if (recycler == null
                        || !recycler.isAttachedToWindow()
                        || recycler.getVisibility() != View.VISIBLE
                        || recycler.getWidth() <= 0
                        || recycler.getHeight() <= 0) {
                    continue;
                }
                if (recycler.getAlpha() < 0.99f) {
                    recycler.animate().cancel();
                    recycler.setAlpha(SYSTEMUI_LYRIC_VISIBLE_ALPHA);
                    restored = true;
                }
                invalidateLyricsRecyclerDescendants(recycler);
                recycler.postInvalidateOnAnimation();
            }
            if (restored) {
                synchronized (suppressedLyricsRecyclerAlphasLock) {
                    suppressedLyricsRecyclerAlphas.clear();
                }
                pendingCustomLyricTakeoverFade = false;
                info("Visibility watchdog restored lyric RecyclerView alpha");
            }
        }, SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS
                + SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS
                + 120L);
    }

    private boolean shouldSuppressOfficialLyricForTrackHandoff() {
        long deadline = officialLyricDrawSuppressedUntilElapsedMs;
        if (deadline <= 0L) {
            return false;
        }
        if (SystemClock.elapsedRealtime() < deadline) {
            return true;
        }
        officialLyricDrawSuppressedUntilElapsedMs = 0L;
        return false;
    }

    private void invalidateRememberedLyricViews() {
        for (View recycler : snapshotLyricsRecyclerViews()) {
            recycler.postInvalidateOnAnimation();
        }
        for (TextView textView : snapshotActiveTextViews()) {
            textView.postInvalidateOnAnimation();
        }
        for (View root : snapshotLyricRootViews()) {
            root.postInvalidateOnAnimation();
        }
    }

    private void suppressRememberedLyricsRecyclerViews() {
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (recycler == null
                    || !recycler.isAttachedToWindow()
                    || recycler.getVisibility() != View.VISIBLE
                    || recycler.getWidth() <= 0
                    || recycler.getHeight() <= 0) {
                continue;
            }
            synchronized (suppressedLyricsRecyclerAlphasLock) {
                if (!suppressedLyricsRecyclerAlphas.containsKey(recycler)) {
                    // The RecyclerView is fully visible in lyric mode. Never inherit an
                    // in-flight fade value here, otherwise repeated track changes make the
                    // saved target alpha decay (1.0 -> 0.7 -> 0.4 ...).
                    suppressedLyricsRecyclerAlphas.put(
                            recycler,
                            SYSTEMUI_LYRIC_VISIBLE_ALPHA);
                }
            }
            recycler.animate().cancel();
            lyricRecyclerFadeGeneration++;
            lyricRecyclerFadeInUntilElapsedMs = 0L;
            if (recycler.getAlpha() != SYSTEMUI_LYRIC_HANDOFF_HIDDEN_ALPHA) {
                recycler.setAlpha(SYSTEMUI_LYRIC_HANDOFF_HIDDEN_ALPHA);
            }
            recycler.invalidate();
        }
    }

    private void restoreSuppressedLyricsRecyclerViews(boolean animate) {
        ArrayList<View> recyclers = new ArrayList<>();
        ArrayList<Float> alphas = new ArrayList<>();
        synchronized (suppressedLyricsRecyclerAlphasLock) {
            for (Map.Entry<View, Float> entry : suppressedLyricsRecyclerAlphas.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    recyclers.add(entry.getKey());
                    alphas.add(entry.getValue());
                }
            }
            suppressedLyricsRecyclerAlphas.clear();
        }
        for (int i = 0; i < recyclers.size(); i++) {
            View recycler = recyclers.get(i);
            float targetAlpha = alphas.get(i);
            if (animate && recycler.isAttachedToWindow()) {
                animateLyricsRecyclerFadeIn(recycler, targetAlpha);
            } else {
                recycler.animate().cancel();
                recycler.setAlpha(targetAlpha);
                invalidateLyricsRecyclerDescendants(recycler);
                recycler.postInvalidateOnAnimation();
            }
        }
    }

    private void fadeInLateCustomLyricTakeover(TextView textView) {
        if (!pendingCustomLyricTakeoverFade || textView == null) {
            return;
        }
        View recycler = findContainingLyricsRecyclerView(textView);
        if (recycler == null || !recycler.isAttachedToWindow()) {
            return;
        }
        pendingCustomLyricTakeoverFade = false;
        int handoffGeneration = officialLyricHandoffGeneration;
        recycler.postOnAnimation(() -> {
            if (handoffGeneration != officialLyricHandoffGeneration
                    || officialLyricDrawSuppressedUntilElapsedMs > 0L
                    || !recycler.isAttachedToWindow()) {
                return;
            }
            info("Fading late custom lyric takeover");
            animateLyricsRecyclerFadeIn(recycler, SYSTEMUI_LYRIC_VISIBLE_ALPHA);
        });
    }

    private void animateLyricsRecyclerFadeIn(View recycler, float targetAlpha) {
        if (recycler == null) {
            return;
        }
        float resolvedTarget = Math.max(0.01f, Math.min(1f, targetAlpha));
        int generation = ++lyricRecyclerFadeGeneration;
        lyricRecyclerFadeInUntilElapsedMs =
                SystemClock.elapsedRealtime() + SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS + 48L;
        recycler.animate().cancel();
        recycler.setAlpha(SYSTEMUI_LYRIC_HANDOFF_HIDDEN_ALPHA);
        invalidateLyricsRecyclerDescendants(recycler);
        recycler.postInvalidateOnAnimation();
        long[] redrawDelays = {48L, 120L, 240L, SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS};
        for (long delay : redrawDelays) {
            mainHandler.postDelayed(() -> {
                if (generation == lyricRecyclerFadeGeneration) {
                    invalidateLyricsRecyclerDescendants(recycler);
                }
            }, delay);
        }
        recycler.animate()
                .alpha(resolvedTarget)
                .setDuration(SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS)
                .withEndAction(() -> {
                    if (generation != lyricRecyclerFadeGeneration) {
                        return;
                    }
                    lyricRecyclerFadeInUntilElapsedMs = 0L;
                    recycler.setAlpha(resolvedTarget);
                    invalidateLyricsRecyclerDescendants(recycler);
                    recycler.postInvalidateOnAnimation();
                })
                .start();
    }

    private static void invalidateLyricsRecyclerDescendants(View root) {
        invalidateLyricsRecyclerDescendants(root, new int[]{0});
    }

    private static void invalidateLyricsRecyclerDescendants(View view, int[] visited) {
        if (view == null || visited[0]++ > 320) {
            return;
        }
        if (view instanceof TextView) {
            view.postInvalidateOnAnimation();
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            invalidateLyricsRecyclerDescendants(group.getChildAt(i), visited);
        }
    }

    private View firstAttachedLyricsRecyclerView() {
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (recycler != null && recycler.isAttachedToWindow()) {
                return recycler;
            }
        }
        return null;
    }

    private boolean hasVisibleLyricsSurfaceForTrackHandoff(long now) {
        if (now < officialLyricDrawSuppressedUntilElapsedMs) {
            return true;
        }
        if (!systemUiLyricModeKeepAwakeActive) {
            return false;
        }
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (recycler != null
                    && recycler.isAttachedToWindow()
                    && recycler.getVisibility() == View.VISIBLE
                    && recycler.getWidth() > 0
                    && recycler.getHeight() > 0
                    && recycler.getAlpha() > 0.01f) {
                return true;
            }
        }
        return false;
    }

    private static String buildWordLyricModelSignature(LyricInfoContract.Payload payload) {
        if (payload == null) {
            return "";
        }
        return payload.songId
                + '|'
                + payload.sessionGeneration
                + '|'
                + payload.trackKey
                + '|'
                + payload.songName
                + '|'
                + payload.artist
                + '|'
                + contentSignature(payload.lyric)
                + '|'
                + contentSignature(payload.rawLyric)
                + '|'
                + contentSignature(payload.translationLyric);
    }

    private static String contentSignature(String value) {
        if (value == null) {
            return "0:0";
        }
        return value.length() + ":" + Integer.toHexString(value.hashCode());
    }

    private void mergeSupplementalTranslations(
            WordLyricModel target,
            String supplemental,
            String rawLyric,
            boolean allowTextAsTranslation) {
        if (target == null
                || target.lines.isEmpty()
                || !LyricInfoContract.containsTimedLrc(supplemental)
                || supplemental.equals(rawLyric)) {
            return;
        }

        WordLyricModel supplementalModel = parseWordLyric(supplemental, false);
        if (supplementalModel.lines.isEmpty()) {
            return;
        }

        int before = target.translationCount();
        for (WordLine targetLine : target.lines) {
            if (targetLine == null || !TextUtils.isEmpty(targetLine.translation)) {
                continue;
            }

            WordLine supplementalLine = findSupplementalTranslationLine(
                    supplementalModel,
                    targetLine,
                    allowTextAsTranslation);
            if (supplementalLine == null) {
                continue;
            }

            String translation = cleanPlainLyricText(supplementalLine.translation);
            if (TextUtils.isEmpty(translation) && allowTextAsTranslation) {
                translation = cleanPlainLyricText(supplementalLine.text);
            }
            if (!TextUtils.isEmpty(translation)
                    && !normalizeLine(translation).equals(normalizeLine(targetLine.text))) {
                targetLine.translation = translation;
            }
        }

        int added = target.translationCount() - before;
        if (added > 0) {
            info("Merged supplemental lyric translations, added=" + added);
        }
    }

    private static WordLine findSupplementalTranslationLine(
            WordLyricModel supplementalModel,
            WordLine targetLine,
            boolean allowTextAsTranslation) {
        WordLine best = null;
        long bestDistance = Long.MAX_VALUE;
        String targetText = normalizeLine(targetLine.text);
        for (WordLine candidate : supplementalModel.lines) {
            if (candidate == null) {
                continue;
            }
            long distance = Math.abs(candidate.timeMillis - targetLine.timeMillis);
            if (distance > 120L || distance > bestDistance) {
                continue;
            }

            String candidateTranslation = cleanPlainLyricText(candidate.translation);
            String candidateText = normalizeLine(candidate.text);
            boolean usable = !TextUtils.isEmpty(candidateTranslation)
                    || (allowTextAsTranslation
                    && !TextUtils.isEmpty(candidate.text)
                    && !candidateText.equals(targetText));
            if (!usable) {
                continue;
            }

            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    private WordLyricModel parseWordLyric(String rawLyric, boolean primarySource) {
        WordLyricModel inlineModel = parseInlineWordLrc(rawLyric);
        if (!inlineModel.lines.isEmpty()) {
            return inlineModel;
        }

        WordLyricModel model = new WordLyricModel();
        model.parserName = "lyrics-core";
        try {
            LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(rawLyric);
            LinkedHashMap<String, WordLine> uniqueLines = new LinkedHashMap<>();
            for (LyricsCoreAdapter.ParsedLine parsedLine : parsed.lines) {
                WordLine line = toWordLine(parsedLine);
                if (line == null || line.words.isEmpty()) {
                    continue;
                }

                String key = line.timeMillis + "|" + normalizeLine(line.text);
                WordLine existing = uniqueLines.get(key);
                if (existing == null) {
                    uniqueLines.put(key, line);
                } else if (TextUtils.isEmpty(existing.translation)
                        && !TextUtils.isEmpty(line.translation)) {
                    existing.translation = line.translation;
                }
            }
            model.lines.addAll(uniqueLines.values());
            model.lines.sort((left, right) -> Long.compare(left.timeMillis, right.timeMillis));
            mergeSameTimestampLyricLines(model);
        } catch (Throwable t) {
            // Do not let a parser or dependency failure crash the injected process. An empty
            // model deliberately leaves the original ColorOS lyric renderer untouched.
            error(primarySource
                    ? "Lyrics Core parsing failed; using official SystemUI lyric renderer"
                    : "Lyrics Core parsing failed for supplemental lyric; ignoring supplemental translations",
                    t);
        }
        return model;
    }

    private WordLyricModel parseInlineWordLrc(String rawLyric) {
        WordLyricModel model = new WordLyricModel();
        model.parserName = "inline-lrc";
        if (TextUtils.isEmpty(rawLyric) || !LyricInfoContract.containsTimedLrc(rawLyric)) {
            return model;
        }

        LinkedHashMap<Long, ArrayList<InlineTimedLyricLine>> groups = new LinkedHashMap<>();
        ArrayList<InlineTimedLyricLine> orphanTranslations = new ArrayList<>();
        int order = 0;
        int inlineTimedLineCount = 0;
        for (String rawLine : splitRawLyricLines(rawLyric)) {
            for (String expandedLine : OplusLyricNormalizer.splitEmbeddedTimedLines(rawLine)) {
                InlineTimedLyricLine line = parseInlineTimedLyricLine(expandedLine, order++);
                if (line == null) {
                    continue;
                }
                if (line.inlineTiming) {
                    inlineTimedLineCount++;
                }
                ArrayList<InlineTimedLyricLine> group = groups.get(line.timeMillis);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(line.timeMillis, group);
                }
                group.add(line);
            }
        }
        if (inlineTimedLineCount <= 0 || groups.isEmpty()) {
            model.lines.clear();
            return model;
        }

        for (Map.Entry<Long, ArrayList<InlineTimedLyricLine>> entry : groups.entrySet()) {
            ArrayList<InlineTimedLyricLine> group = entry.getValue();
            InlineTimedLyricLine primary = choosePrimaryInlineTimedLyricLine(group);
            if (primary == null) {
                continue;
            }
            if (!primary.inlineTiming
                    && group.size() == 1
                    && !containsLatinLetter(primary.text)) {
                // In a mixed enhanced-LRC payload, a lone non-inline non-Latin line is almost
                // always a delayed translation for the preceding word-timed line.
                orphanTranslations.add(primary);
                continue;
            }

            WordLine wordLine = new WordLine(
                    primary.timeMillis,
                    primary.text,
                    primary.words,
                    primary.endTimeMillis,
                    primary.inlineTiming
                            ? LyricTimingMode.WORD_TIMED
                            : LyricTimingMode.LINE_TIMED);
            for (InlineTimedLyricLine candidate : group) {
                if (candidate == null || candidate == primary) {
                    continue;
                }
                String translation = cleanPlainLyricText(candidate.text);
                if (TextUtils.isEmpty(translation)
                        || LockscreenIntegrationPolicy.sameLyricVariant(
                        primary.text,
                        translation)) {
                    continue;
                }
                if (TextUtils.isEmpty(wordLine.translation)) {
                    wordLine.translation = translation;
                }
            }
            model.lines.add(wordLine);
        }

        model.lines.sort((left, right) -> Long.compare(left.timeMillis, right.timeMillis));
        attachDelayedInlineTranslations(model, orphanTranslations);
        return model;
    }

    private static void attachDelayedInlineTranslations(
            WordLyricModel model,
            ArrayList<InlineTimedLyricLine> translations) {
        if (model == null || model.lines.isEmpty() || translations == null || translations.isEmpty()) {
            return;
        }
        for (InlineTimedLyricLine candidate : translations) {
            if (candidate == null || TextUtils.isEmpty(candidate.text)) {
                continue;
            }

            WordLine previous = null;
            WordLine next = null;
            for (WordLine line : model.lines) {
                if (line.timeMillis < candidate.timeMillis) {
                    previous = line;
                    continue;
                }
                if (line.timeMillis > candidate.timeMillis) {
                    next = line;
                    break;
                }
            }
            if (previous == null || !TextUtils.isEmpty(previous.translation)) {
                continue;
            }

            boolean previousHasWordTiming = previous.words.size() > 1
                    || previous.endTimeMillis > previous.timeMillis + 600L;
            boolean candidateLooksLikeTranslation =
                    !containsLatinLetter(candidate.text)
                            && !LockscreenIntegrationPolicy.sameLyricVariant(
                            previous.text,
                            candidate.text);
            long nextTime = next == null ? -1L : next.timeMillis;
            if (LockscreenIntegrationPolicy.shouldAttachDelayedTranslation(
                    previousHasWordTiming,
                    candidateLooksLikeTranslation,
                    previous.timeMillis,
                    previous.endTimeMillis,
                    candidate.timeMillis,
                    nextTime)) {
                previous.translation = cleanPlainLyricText(candidate.text);
            }
        }
    }

    private static InlineTimedLyricLine parseInlineTimedLyricLine(String rawLine, int order) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (TextUtils.isEmpty(line)) {
            return null;
        }

        java.util.regex.Matcher matcher = ANY_LRC_TIME_TAG.matcher(line);
        ArrayList<TagMatch> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(new TagMatch(matcher.start(), matcher.end(), parseLrcTimeMillis(matcher.group(1))));
        }
        if (tags.isEmpty() || tags.get(0).start != 0) {
            return null;
        }

        StringBuilder text = new StringBuilder(line.length());
        ArrayList<WordRange> words = new ArrayList<>();
        boolean previousSegmentEndedWithSpace = false;
        long explicitEndMillis = -1L;
        for (int i = 0; i < tags.size(); i++) {
            TagMatch tag = tags.get(i);
            int segmentStart = tag.end;
            int segmentEnd = i + 1 < tags.size() ? tags.get(i + 1).start : line.length();
            String rawSegment = segmentStart < segmentEnd
                    ? line.substring(segmentStart, segmentEnd)
                    : "";
            boolean segmentStartsWithSpace = startsWithWhitespace(rawSegment);
            boolean segmentEndsWithSpace = endsWithWhitespace(rawSegment);
            String segment = cleanInlineTimedLyricSegment(rawSegment);
            if (TextUtils.isEmpty(segment)) {
                if (i == tags.size() - 1 && tags.size() > 1 && tag.timeMillis > tags.get(0).timeMillis) {
                    explicitEndMillis = tag.timeMillis;
                }
                continue;
            }

            if (shouldInsertInlineSegmentSpace(
                    text,
                    segment,
                    segmentStartsWithSpace,
                    previousSegmentEndedWithSpace)) {
                text.append(' ');
            }
            int start = text.length();
            text.append(segment);
            int end = text.length();
            if (start < end) {
                words.add(new WordRange(tag.timeMillis, start, end));
            }
            previousSegmentEndedWithSpace = segmentEndsWithSpace;
        }

        if (TextUtils.isEmpty(text.toString()) || words.isEmpty()) {
            return null;
        }

        NormalizedWordLineText normalized = normalizeTimedWordText(text.toString(), words);
        if (TextUtils.isEmpty(normalized.text)
                || normalized.words.isEmpty()
                || isNonLyricInfoLine(normalized.text, tags.get(0).timeMillis)) {
            return null;
        }

        long inferredEnd = inferWordLineEndMillis(tags.get(0).timeMillis, normalized.words);
        long endTimeMillis = explicitEndMillis > tags.get(0).timeMillis
                ? Math.max(explicitEndMillis, normalized.words.get(normalized.words.size() - 1).timeMillis + 80L)
                : inferredEnd;
        boolean inlineTiming = LockscreenIntegrationPolicy.hasProgressiveInlineTiming(
                normalized.words.size(),
                normalized.words.get(0).timeMillis,
                normalized.words.get(normalized.words.size() - 1).timeMillis,
                tags.get(0).timeMillis,
                explicitEndMillis);
        ArrayList<WordRange> renderedWords = normalized.words;
        if (!inlineTiming && normalized.words.size() > 1) {
            renderedWords = new ArrayList<>();
            renderedWords.add(new WordRange(
                    tags.get(0).timeMillis,
                    0,
                    normalized.text.length()));
        }
        return new InlineTimedLyricLine(
                tags.get(0).timeMillis,
                endTimeMillis,
                normalized.text,
                renderedWords,
                inlineTiming,
                order);
    }

    private static InlineTimedLyricLine choosePrimaryInlineTimedLyricLine(
            ArrayList<InlineTimedLyricLine> group) {
        InlineTimedLyricLine best = null;
        int bestScore = Integer.MIN_VALUE;
        if (group == null) {
            return null;
        }
        for (InlineTimedLyricLine line : group) {
            if (line == null || TextUtils.isEmpty(line.text)) {
                continue;
            }
            int score = Math.min(120, line.words == null ? 0 : line.words.size()) * 12
                    + Math.min(120, normalizeLine(line.text).length());
            if (line.inlineTiming) {
                score += 1_000;
            }
            if (containsLatinLetter(line.text)) {
                score += 500;
            }
            score -= Math.max(0, line.order);
            if (best == null || score > bestScore) {
                best = line;
                bestScore = score;
            }
        }
        return best;
    }

    private static String cleanInlineTimedLyricSegment(String segment) {
        if (TextUtils.isEmpty(segment)) {
            return "";
        }
        String cleaned = ANY_LRC_TIME_TAG.matcher(segment).replaceAll("");
        cleaned = LyricTextSanitizer.removeIgnorableCharacters(cleaned).replace('\t', ' ');
        return cleaned.trim().replaceAll(" {2,}", " ");
    }

    private static boolean shouldInsertInlineSegmentSpace(
            StringBuilder current,
            String segment,
            boolean segmentStartsWithSpace,
            boolean previousSegmentEndedWithSpace) {
        if (current == null || current.length() == 0 || TextUtils.isEmpty(segment)) {
            return false;
        }
        if (segmentStartsWithSpace || previousSegmentEndedWithSpace) {
            return true;
        }
        char previous = current.charAt(current.length() - 1);
        char first = segment.charAt(0);
        return isAsciiWordLike(previous) && isAsciiWordLike(first);
    }

    private static boolean startsWithWhitespace(String value) {
        return !TextUtils.isEmpty(value) && Character.isWhitespace(value.charAt(0));
    }

    private static boolean endsWithWhitespace(String value) {
        return !TextUtils.isEmpty(value) && Character.isWhitespace(value.charAt(value.length() - 1));
    }

    private static boolean isAsciiWordLike(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9');
    }

    private static void mergeSameTimestampLyricLines(WordLyricModel model) {
        if (model == null || model.lines.size() < 2) {
            return;
        }

        ArrayList<WordLine> merged = new ArrayList<>(model.lines.size());
        int index = 0;
        while (index < model.lines.size()) {
            WordLine first = model.lines.get(index);
            if (first == null) {
                index++;
                continue;
            }

            ArrayList<WordLine> group = new ArrayList<>();
            group.add(first);
            int next = index + 1;
            while (next < model.lines.size()) {
                WordLine candidate = model.lines.get(next);
                if (candidate == null || candidate.timeMillis != first.timeMillis) {
                    break;
                }
                group.add(candidate);
                next++;
            }

            WordLine primary = choosePrimaryWordLine(group);
            if (primary == null) {
                index = next;
                continue;
            }
            for (WordLine candidate : group) {
                if (candidate == null || candidate == primary) {
                    continue;
                }
                String translation = cleanPlainLyricText(candidate.translation);
                if (TextUtils.isEmpty(translation)) {
                    translation = cleanPlainLyricText(candidate.text);
                }
                if (TextUtils.isEmpty(translation)
                        || LockscreenIntegrationPolicy.sameLyricVariant(
                        primary.text,
                        translation)) {
                    continue;
                }
                if (TextUtils.isEmpty(primary.translation)) {
                    primary.translation = translation;
                }
            }
            merged.add(primary);
            index = next;
        }

        model.lines.clear();
        model.lines.addAll(merged);
    }

    private static WordLine choosePrimaryWordLine(ArrayList<WordLine> group) {
        WordLine best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < group.size(); i++) {
            WordLine line = group.get(i);
            if (line == null || TextUtils.isEmpty(line.text)) {
                continue;
            }
            int score = Math.min(80, line.words == null ? 0 : line.words.size()) * 4
                    + Math.min(80, normalizeLine(line.text).length());
            if (containsLatinLetter(line.text)) {
                score += 1_000;
            }
            // Earlier same-timestamp lines usually carry the source/main lyric.
            score -= i;
            if (best == null || score > bestScore) {
                best = line;
                bestScore = score;
            }
        }
        return best;
    }

    private void applyOfficialDisplayTextAliases(WordLyricModel model, String officialLyric) {
        if (model == null
                || model.lines.isEmpty()
                || !LyricInfoContract.containsTimedLrc(officialLyric)) {
            return;
        }

        int applied = 0;
        String firstAlias = "";
        LinkedHashMap<String, Integer> textOccurrences = new LinkedHashMap<>();
        model.officialLines.clear();
        model.renderableTextIndexBuilt = false;
        for (TimedLyricGroup group : parseTimedTextGroups(officialLyric)) {
            if (group == null || group.texts.isEmpty()) {
                continue;
            }
            int primaryIndex = findPrimaryTextIndex(group.texts);
            String displayText = cleanPlainLyricText(group.texts.get(primaryIndex));
            if (TextUtils.isEmpty(displayText)) {
                continue;
            }
            String normalizedDisplayText = normalizeLine(displayText);
            int occurrence = textOccurrences.containsKey(normalizedDisplayText)
                    ? textOccurrences.get(normalizedDisplayText)
                    : 0;
            textOccurrences.put(normalizedDisplayText, occurrence + 1);
            WordLine wordLine = findOfficialWordLine(
                    model,
                    group.timeMillis,
                    normalizedDisplayText,
                    occurrence);
            model.officialLines.add(wordLine);
            if (wordLine == null) {
                continue;
            }
            wordLine.displayText = displayText;
            applied++;
            if (TextUtils.isEmpty(firstAlias)) {
                firstAlias = displayText;
            }
            for (int i = 0; i < group.texts.size(); i++) {
                if (i == primaryIndex || !TextUtils.isEmpty(wordLine.translation)) {
                    continue;
                }
                String translation = cleanPlainLyricText(group.texts.get(i));
                if (!TextUtils.isEmpty(translation)
                        && !LockscreenIntegrationPolicy.sameLyricVariant(
                        displayText,
                        translation)) {
                    wordLine.translation = translation;
                }
            }
        }
        if (applied > 0) {
            info("Applied official lyric display aliases, aliases=" + applied
                    + ", first=" + shortenForLog(firstAlias));
        }
    }

    private static WordLine findOfficialWordLine(
            WordLyricModel model,
            long timeMillis,
            String normalizedDisplayText,
            int occurrence) {
        if (model == null || TextUtils.isEmpty(normalizedDisplayText)) {
            return null;
        }
        WordLine exactTime = model.findLineAtTime(timeMillis);
        if (matchesWordLineText(exactTime, normalizedDisplayText)) {
            return exactTime;
        }

        WordLine occurrenceMatch = model.findLineByTextOccurrence(
                normalizedDisplayText,
                occurrence);
        if (occurrenceMatch != null) {
            return occurrenceMatch;
        }

        WordLine timedText = model.findLineByText(normalizedDisplayText, timeMillis);
        if (timedText != null) {
            return timedText;
        }

        WordLine nearest = model.findNearestLineByTime(timeMillis, 650L);
        if (matchesWordLineText(nearest, normalizedDisplayText)) {
            return nearest;
        }
        return nearest;
    }

    private static ArrayList<TimedLyricGroup> parseTimedTextGroups(String lyric) {
        LinkedHashMap<Long, TimedLyricGroup> groups = new LinkedHashMap<>();
        if (TextUtils.isEmpty(lyric)) {
            return new ArrayList<>();
        }
        for (String rawLine : splitRawLyricLines(lyric)) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            java.util.regex.Matcher firstTag = ANY_LRC_TIME_TAG.matcher(line);
            if (!firstTag.find() || firstTag.start() != 0) {
                continue;
            }

            long timeMillis = parseLrcTimeMillis(firstTag.group(1));
            String text = line.substring(firstTag.end());
            text = ANY_LRC_TIME_TAG.matcher(text).replaceAll("");
            text = cleanPlainLyricText(text);
            if (!TextUtils.isEmpty(text) && !isNonLyricInfoLine(text, timeMillis)) {
                TimedLyricGroup group = groups.get(timeMillis);
                if (group == null) {
                    group = new TimedLyricGroup(timeMillis);
                    groups.put(timeMillis, group);
                }
                group.texts.add(text);
            }
        }
        return new ArrayList<>(groups.values());
    }

    private static WordLine toWordLine(LyricsCoreAdapter.ParsedLine parsedLine) {
        if (parsedLine == null || TextUtils.isEmpty(parsedLine.text)) {
            return null;
        }

        ArrayList<WordRange> sourceWords = new ArrayList<>();
        for (LyricsCoreAdapter.ParsedSyllable syllable : parsedLine.syllables) {
            int start = Math.max(0, Math.min(parsedLine.text.length(), syllable.start));
            int end = Math.max(start, Math.min(parsedLine.text.length(), syllable.end));
            while (start < end && Character.isWhitespace(parsedLine.text.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(parsedLine.text.charAt(end - 1))) {
                end--;
            }
            if (start < end) {
                sourceWords.add(new WordRange(syllable.startMillis, start, end));
            }
        }
        if (sourceWords.isEmpty()) {
            sourceWords.add(new WordRange(
                    parsedLine.startMillis,
                    0,
                    parsedLine.text.length()));
        }

        NormalizedWordLineText normalized = normalizeTimedWordText(parsedLine.text, sourceWords);
        if (TextUtils.isEmpty(normalized.text)
                || normalized.words.isEmpty()
                || isNonLyricInfoLine(normalized.text, parsedLine.startMillis)) {
            return null;
        }

        long inferredEnd = inferWordLineEndMillis(parsedLine.startMillis, normalized.words);
        long endTimeMillis = parsedLine.endMillis > parsedLine.startMillis
                && parsedLine.endMillis - parsedLine.startMillis <= 120_000L
                ? parsedLine.endMillis
                : inferredEnd;
        WordLine line = new WordLine(
                parsedLine.startMillis,
                normalized.text,
                normalized.words,
                endTimeMillis,
                parsedLine.syllables.size() > 1
                        ? LyricTimingMode.WORD_TIMED
                        : LyricTimingMode.LINE_TIMED);
        String translation = cleanPlainLyricText(parsedLine.translation);
        line.translation = LockscreenIntegrationPolicy.sameLyricVariant(
                normalized.text,
                translation)
                ? ""
                : translation;
        return line;
    }

    private static String cleanPlainLyricText(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        text = ANY_LRC_TIME_TAG.matcher(text).replaceAll("");
        text = LyricTextSanitizer.removeIgnorableCharacters(text).trim();
        return text.replaceAll("[ \\t]{2,}", " ");
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
            int timingTagEnd = findTimingTagEnd(text, i);
            if (timingTagEnd > i) {
                int mapped = normalized.length();
                for (int j = i; j <= timingTagEnd && j <= length; j++) {
                    boundaryMap[j] = mapped;
                }
                i = timingTagEnd - 1;
                continue;
            }
            char value = text.charAt(i);
            if (LyricTextSanitizer.isIgnorableCharacter(value)) {
                boundaryMap[i] = normalized.length();
                continue;
            }
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

    private static int findTimingTagEnd(String text, int start) {
        if (TextUtils.isEmpty(text) || start < 0 || start >= text.length()) {
            return -1;
        }
        char open = text.charAt(start);
        char close;
        if (open == '[') {
            close = ']';
        } else if (open == '<') {
            close = '>';
        } else {
            return -1;
        }

        int maxEnd = Math.min(text.length() - 1, start + 18);
        int end = -1;
        for (int i = start + 1; i <= maxEnd; i++) {
            if (text.charAt(i) == close) {
                end = i;
                break;
            }
        }
        if (end <= start) {
            return -1;
        }

        String candidate = text.substring(start, end + 1);
        return ANY_LRC_TIME_TAG.matcher(candidate).matches() ? end + 1 : -1;
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
            if (delta >= 0) {
                return Math.max(0L, base + (long) (lastPlaybackSpeed * delta));
            }
            return base;
        }

        WordLyricModel model = currentWordLyricModel;
        WordLine hintedLine = model == null
                ? null
                : model.lineAtOfficialIndex(lastLyricsRecyclerIndex);
        if (hintedLine == null && model != null) {
            hintedLine = model.lineAt(lastLyricsRecyclerIndex);
        }
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
        if (now - lastTextViewDrawLogAt < 3_000L) {
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
                + ", timing=" + (wordLine == null ? "" : wordLine.timingMode)
                + ", hasTranslation=" + (wordLine != null && !TextUtils.isEmpty(wordLine.translation))
                + ", view=" + describeViewForLog(view));
        maybeLogOfficialLyricGeometry(frame, view, "draw-main");
    }

    private void maybeLogOfficialLyricPayload(
            LyricInfoContract.Payload payload, boolean normalizedForOfficialList) {
        if (!isLyricLayoutDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastOfficialLyricPayloadLogAt < 1_500L) {
            return;
        }
        lastOfficialLyricPayloadLogAt = now;
        info("Official lyric payload, provider=" + (payload == null ? "" : payload.provider)
                + ", package=" + currentLyricProviderPackage
                + ", moduleEnvelope=" + (payload != null && payload.isModuleEnvelope())
                + ", normalizedForOfficialList=" + normalizedForOfficialList
                + ", lyricChars=" + (payload == null || payload.lyric == null
                ? 0
                : payload.lyric.length())
                + ", rawChars=" + (payload == null || payload.rawLyric == null
                ? 0
                : payload.rawLyric.length()));
    }

    private void maybeLogOfficialLyricGeometry(
            DrawFrame frame, TextView view, String role) {
        if (!isLyricLayoutDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLyricLayoutDiagnosticsLogAt < 1_000L) {
            return;
        }
        lastLyricLayoutDiagnosticsLogAt = now;
        View itemView = findLyricsRecyclerItemView(view);
        View recycler = findContainingLyricsRecyclerView(view);
        int adapterPosition = findLyricsRecyclerAdapterPosition(view);
        LyricsRecyclerGeometry geometry = captureLyricsRecyclerGeometry(
                recycler,
                adapterPosition);
        info("Official lyric geometry, role=" + role
                + ", adapterPosition=" + adapterPosition
                + ", drawIndex=" + (frame == null ? -1 : frame.lineIndex)
                + ", activeIndex=" + (frame == null ? -1 : frame.activeIndex)
                + ", itemHeight=" + (itemView == null ? -1 : itemView.getHeight())
                + ", textMeasuredHeight=" + (view == null ? -1 : view.getMeasuredHeight())
                + ", bottomMargin=" + bottomMarginOf(itemView == null ? view : itemView)
                + ", firstVisiblePosition=" + geometry.firstVisiblePosition
                + ", firstVisibleTop=" + geometry.firstVisibleTop
                + ", targetCenter=" + geometry.targetCenter
                + ", slotDp=" + LYRIC_SLOT_HEIGHT_DP
                + ", spacingDp=" + OFFICIAL_LYRIC_LINE_SPACING_DP);
    }

    private void maybeLogLyricsRecyclerSetCurrentGeometry(
            int targetIndex,
            LyricsRecyclerGeometry before,
            LyricsRecyclerGeometry after) {
        if (!isLyricLayoutDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLyricLayoutDiagnosticsLogAt < 750L) {
            return;
        }
        lastLyricLayoutDiagnosticsLogAt = now;
        int beforeCenter = before == null ? Integer.MIN_VALUE : before.targetCenter;
        int afterCenter = after == null ? Integer.MIN_VALUE : after.targetCenter;
        info("LyricsRecyclerView setCurrentLyric geometry, target=" + targetIndex
                + ", beforeFirst=" + (before == null ? -1 : before.firstVisiblePosition)
                + ", beforeTop=" + (before == null ? 0 : before.firstVisibleTop)
                + ", beforeTargetCenter=" + beforeCenter
                + ", afterFirst=" + (after == null ? -1 : after.firstVisiblePosition)
                + ", afterTop=" + (after == null ? 0 : after.firstVisibleTop)
                + ", afterTargetCenter=" + afterCenter
                + ", targetCenterDelta=" + (beforeCenter == Integer.MIN_VALUE
                || afterCenter == Integer.MIN_VALUE
                ? "unknown"
                : String.valueOf(afterCenter - beforeCenter)));
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
        if (state == lastLoggedSystemUiPlaybackState && now - lastSeedlingPlaybackStateLogAt < 5_000L) {
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
        if (now - lastActiveRefreshLogAt < 3_000L) {
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

    private void maybeLogLyricVisibilityRecovery(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastLyricVisibilityRecoveryLogAt < 1_500L) {
            return;
        }
        lastLyricVisibilityRecoveryLogAt = now;
        info("Recovered lyric renderer after visibility transition, reason=" + reason);
    }

    private void maybeLogRecyclerIndex(int index) {
        long now = System.currentTimeMillis();
        if (now - lastRecyclerLogAt < 1_000L) {
            return;
        }
        lastRecyclerLogAt = now;
        info("LyricsRecyclerView current index=" + index);
    }

    private static boolean isLyricLayoutDiagnosticsEnabled() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    private static LyricsRecyclerGeometry captureLyricsRecyclerGeometry(
            View recycler, int targetIndex) {
        if (!(recycler instanceof ViewGroup)) {
            return LyricsRecyclerGeometry.EMPTY;
        }
        ViewGroup group = (ViewGroup) recycler;
        int firstVisiblePosition = -1;
        int firstVisibleTop = 0;
        int targetCenter = Integer.MIN_VALUE;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            int position = readRecyclerChildPosition(recycler, child);
            if (position >= 0 && firstVisiblePosition < 0) {
                firstVisiblePosition = position;
                firstVisibleTop = child.getTop();
            }
            if (position == targetIndex) {
                targetCenter = child.getTop() + child.getHeight() / 2;
            }
        }
        return new LyricsRecyclerGeometry(firstVisiblePosition, firstVisibleTop, targetCenter);
    }

    private static int bottomMarginOf(View view) {
        if (view == null) {
            return 0;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof ViewGroup.MarginLayoutParams
                ? ((ViewGroup.MarginLayoutParams) params).bottomMargin
                : 0;
    }

    private static String shortenForLog(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    private static String sanitizeForOplusLyric(String rawLyric) {
        return OplusLyricNormalizer.normalizeForOfficialList(rawLyric);
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

    private static boolean isNonLyricInfoLine(String text, long timeMillis) {
        return OplusLyricNormalizer.isNonLyricInfoLine(text, timeMillis);
    }

    private static boolean containsAny(String value, String... needles) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        for (String needle : needles) {
            if (!TextUtils.isEmpty(needle) && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isShortStandaloneLyricLine(WordLine line) {
        return line != null && isShortStandaloneLyricText(line.text);
    }

    private static boolean isShortStandaloneLyricText(String text) {
        String normalized = normalizeLine(text);
        if (TextUtils.isEmpty(normalized) || normalized.length() > 18) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isWhitespace(normalized.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String buildModuleLyricInfo(
            String title,
            String artist,
            long duration,
            String lyric,
            String rawLyric,
            String source,
            long sessionGeneration,
            String trackKey) throws Exception {
        JSONObject object = new JSONObject();
        object.put(LyricInfoContract.JSON_SONG_NAME, title);
        object.put(LyricInfoContract.JSON_ARTIST, nullToEmpty(artist));
        object.put(LyricInfoContract.JSON_SONG_ID, buildSongId(title, artist, duration));
        object.put(LyricInfoContract.JSON_LYRIC, lyric);
        object.put(OPLUS_RAW_LYRIC_INFO_KEY, rawLyric);
        object.put(LyricInfoContract.JSON_PROVIDER, LyricInfoContract.MODULE_PROVIDER);
        object.put(LyricInfoContract.JSON_TRACK_KEY, nullToEmpty(trackKey));
        object.put(LyricInfoContract.JSON_SESSION_GENERATION, sessionGeneration);
        object.put("source", nullToEmpty(source));
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
        private static final int INACTIVE_COLOR = 0x70FFFFFF;
        private static final int FOCUSED_INACTIVE_COLOR = 0x96FFFFFF;
        private static final int PLAYED_COLOR = 0xF0FFFFFF;
        private static final int ACTIVE_COLOR = 0xFFFFFFFF;
        private static final int TRANSLATION_COLOR = INACTIVE_COLOR;
        private static final int FOCUSED_TRANSLATION_COLOR = FOCUSED_INACTIVE_COLOR;
        private static final int ACTIVE_GLOW_FILL_COLOR = 0x2EFFFFFF;
        private static final int ACTIVE_GLOW_SHADOW_COLOR = 0xB8FFD68A;
        private static final float ACTIVE_GLOW_RADIUS_FACTOR = 0.17f;
        private static final float ACTIVE_FEATHER_WIDTH_FACTOR = 0.42f;
        private static final float MAIN_TEXT_SIZE_SP = 22f;
        private static final float UNTRANSLATED_TEXT_SIZE_SP = 24f;
        private static final float UNTRANSLATED_LINE_ADVANCE_DP = 26f;
        private static final int MAX_DRAW_LINES = 3;
        private static final int MAX_TRANSLATED_MAIN_DRAW_LINES = 2;
        private static final long FOCUSED_REVEAL_ANIMATION_MS = 260L;
        private static final long ENTRANCE_REVEAL_ANIMATION_MS = 340L;
        private static final float ENTRANCE_REVEAL_START_SCALE = 0.975f;
        private static final long TRANSLATED_WINDOW_ANIMATION_MS = 220L;
        private static final float TRANSLATED_WINDOW_SLIDE_DP = 7f;
        private static final long TRANSLATION_TOGGLE_ANIMATION_MS = 260L;
        private static final float TRANSLATION_TOGGLE_SLIDE_DP = 5f;
        private static final float TRANSLATION_SCROLL_START_PROGRESS = 0.08f;
        private static final float TRANSLATION_SCROLL_END_PROGRESS = 0.82f;

        private final TextPaint inactivePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint playedPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint activePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint activeGlowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint activeFeatherPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint translationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final TextPaint glowRasterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint glowBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint.FontMetrics mainFontMetrics = new Paint.FontMetrics();
        private final Paint.FontMetrics translationFontMetrics = new Paint.FontMetrics();
        private final Paint.FontMetrics glowFontMetrics = new Paint.FontMetrics();
        private final Matrix activeFeatherShaderMatrix = new Matrix();
        private final ArrayList<LyricDrawLine> drawLines = new ArrayList<>(MAX_DRAW_LINES);
        private final LyricDrawLine[] drawLinePool = {
                new LyricDrawLine(),
                new LyricDrawLine(),
                new LyricDrawLine()
        };
        private final LinearGradient activeFeatherShader = new LinearGradient(
                0f,
                0f,
                1f,
                0f,
                new int[]{0xFFFFFFFF, 0x9AFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.43f, 1f},
                Shader.TileMode.CLAMP);
        private final GlowSegmentCache[] glowSegmentCaches = {
                new GlowSegmentCache(),
                new GlowSegmentCache(),
                new GlowSegmentCache()
        };
        private long glowCacheUseCounter;
        private BlurMaskFilter glowMaskFilter;
        private int glowMaskRadiusKey = -1;
        private float translationAnimationStartAmount = 1f;
        private float translationAnimationTargetAmount = 1f;
        private long translationAnimationStartedAtMs = -1L;

        private WordLine lastLine;
        private long lineChangeElapsedMs = SystemClock.elapsedRealtime();
        private boolean entranceRevealArmed;
        private long entranceRevealStartedAtMs = -1L;

        void armEntranceReveal() {
            // Seedling already animates the whole immersive surface. Starting another
            // 340 ms scale/fade only after the first bound lyric row makes that row feel late.
            entranceRevealArmed = false;
            entranceRevealStartedAtMs = -1L;
        }

        synchronized void setTranslationEnabled(boolean enabled) {
            long now = SystemClock.elapsedRealtime();
            float currentAmount = resolveTranslationAmount(now);
            translationAnimationStartAmount = currentAmount;
            translationAnimationTargetAmount = enabled ? 1f : 0f;
            translationAnimationStartedAtMs =
                    Math.abs(currentAmount - translationAnimationTargetAmount) < 0.001f
                            ? -1L
                            : now;
        }

        synchronized void setTranslationEnabledImmediately(boolean enabled) {
            translationAnimationStartAmount = enabled ? 1f : 0f;
            translationAnimationTargetAmount = translationAnimationStartAmount;
            translationAnimationStartedAtMs = -1L;
        }

        void applySlotHeight(TextView textView, WordLyricModel model, WordLine line) {
            int height = resolveSlotHeight(textView, model, line);
            if (height > 0) {
                setOfficialLyricSlotHeight(textView, height);
            }
        }

        void draw(Canvas canvas, TextView textView, DrawFrame frame) {
            WordLine line = frame.line;
            long position = frame.position;
            long glowPosition = frame.glowPosition;
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
            float availableWidth = textView.getWidth() - leftPadding - rightPadding;
            if (availableWidth <= 1f) {
                return;
            }

            int fixedSlotHeight = resolveSlotHeight(textView, frame.model, line);
            if (fixedSlotHeight <= 0) {
                return;
            }
            setOfficialLyricSlotHeight(textView, fixedSlotHeight);
            float availableHeight = fixedSlotHeight;

            float translationAmount = resolveTranslationAmount();
            boolean compactSlot = (translationAmount <= 0.001f
                    || TextUtils.isEmpty(line.translation))
                    && isCompactLyricSlot(textView, availableHeight);
            boolean untranslatedLayout = TextUtils.isEmpty(line.translation);
            configurePaints(textView, compactSlot, untranslatedLayout);
            float focusAmount = resolveFocusAmount(line, frame.active, frame.focused, frame.activeIndex);
            boolean entranceDriver = frame.lineIndex == frame.activeIndex;
            float entranceAmount = entranceDriver ? resolveEntranceRevealAmount() : 1f;
            if (frame.active && entranceAmount < 1f) {
                focusAmount = Math.min(focusAmount, entranceAmount);
            }
            clearFocusedOfficialLyricViewEffects(textView);
            if ((frame.focused && focusAmount < 1f)
                    || (entranceDriver && entranceAmount < 1f)) {
                textView.postInvalidateOnAnimation();
            }
            int entranceCanvasSave = -1;
            if (entranceAmount < 1f) {
                float scale = ENTRANCE_REVEAL_START_SCALE
                        + (1f - ENTRANCE_REVEAL_START_SCALE) * entranceAmount;
                entranceCanvasSave = canvas.save();
                canvas.scale(scale, scale, textView.getPaddingLeft(), availableHeight * 0.5f);
            }
            applyFade(1f, focusAmount);
            if (compactSlot) {
                drawCompactLine(
                        canvas,
                        textView,
                        line,
                        position,
                        glowPosition,
                        frame.active,
                        focusAmount,
                        availableWidth,
                        availableHeight);
                if (entranceCanvasSave >= 0) {
                    canvas.restoreToCount(entranceCanvasSave);
                }
                return;
            }
            drawLyricGroup(
                    canvas,
                    textView,
                    frame.model,
                    line,
                    position,
                    glowPosition,
                    frame.active,
                    availableWidth,
                    availableHeight,
                    focusAmount,
                    translationAmount);
            if (entranceCanvasSave >= 0) {
                canvas.restoreToCount(entranceCanvasSave);
            }
            if (isTranslationAnimationRunning()) {
                textView.postInvalidateOnAnimation();
            }
        }

        private synchronized float resolveTranslationAmount() {
            return resolveTranslationAmount(SystemClock.elapsedRealtime());
        }

        private float resolveTranslationAmount(long now) {
            if (translationAnimationStartedAtMs < 0L) {
                return translationAnimationTargetAmount;
            }
            float rawProgress = Math.max(
                    0f,
                    Math.min(1f, (now - translationAnimationStartedAtMs)
                            / (float) TRANSLATION_TOGGLE_ANIMATION_MS));
            if (rawProgress >= 1f) {
                translationAnimationStartAmount = translationAnimationTargetAmount;
                translationAnimationStartedAtMs = -1L;
                return translationAnimationTargetAmount;
            }
            float eased = smoothStep(rawProgress);
            return translationAnimationStartAmount
                    + (translationAnimationTargetAmount - translationAnimationStartAmount)
                    * eased;
        }

        private synchronized boolean isTranslationAnimationRunning() {
            return translationAnimationStartedAtMs >= 0L;
        }

        private float resolveEntranceRevealAmount() {
            if (!entranceRevealArmed) {
                return 1f;
            }
            long now = SystemClock.elapsedRealtime();
            if (entranceRevealStartedAtMs < 0L) {
                entranceRevealStartedAtMs = now;
                return 0f;
            }
            float rawProgress = Math.max(
                    0f,
                    Math.min(1f, (now - entranceRevealStartedAtMs)
                            / (float) ENTRANCE_REVEAL_ANIMATION_MS));
            if (rawProgress >= 1f) {
                entranceRevealArmed = false;
                entranceRevealStartedAtMs = -1L;
                return 1f;
            }
            return smoothStep(rawProgress);
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
                WordLyricModel model,
                WordLine line,
                long position,
                long glowPosition,
                boolean activeLine,
                float availableWidth,
                float availableHeight,
                float focusAmount,
                float translationAmount) {
            float originalSize = inactivePaint.getTextSize();
            boolean sourceHasTranslation = !TextUtils.isEmpty(line.translation);
            boolean hasTranslation = translationAmount > 0.001f && sourceHasTranslation;
            boolean untranslatedLayout = !sourceHasTranslation;

            String text = line.text;
            fitMainTextToMaxDrawLines(
                    textView.getContext(),
                    text,
                    availableWidth,
                    MAX_DRAW_LINES);
            buildDrawLines(line, text, availableWidth, false, untranslatedLayout);
            int wordIndex = activeLine ? line.findWordIndex(position) : -1;
            WordRange activeWord = wordIndex >= 0 && wordIndex < line.words.size() ? line.words.get(wordIndex) : null;
            int glowWordIndex = activeLine ? line.findWordIndex(glowPosition) : -1;
            WordRange glowActiveWord = glowWordIndex >= 0 && glowWordIndex < line.words.size()
                    ? line.words.get(glowWordIndex)
                    : null;
            if (drawLines.isEmpty()) {
                setTextSize(originalSize);
                return;
            }
            TranslatedLineWindow lineWindow = hasTranslation
                    ? resolveTranslatedMainLineWindow(
                    line,
                    activeWord,
                    activeLine,
                    availableWidth,
                    position)
                    : null;
            int visibleMainLineCount = lineWindow == null ? drawLines.size() : lineWindow.count;

            inactivePaint.getFontMetrics(mainFontMetrics);
            Paint.FontMetrics mainMetrics = mainFontMetrics;
            float lineHeight = mainMetrics.descent - mainMetrics.ascent;
            float lineGap = visibleMainLineCount > 1
                    ? (untranslatedLayout
                            ? dp(textView.getContext(), UNTRANSLATED_LINE_ADVANCE_DP) - lineHeight
                            : dp(textView.getContext(), 1f))
                    : 0f;
            float translationGap = hasTranslation ? dp(textView.getContext(), 2f) : 0f;
            translationPaint.getFontMetrics(translationFontMetrics);
            Paint.FontMetrics translationMetrics = translationFontMetrics;
            float mainHeight = lineHeight * visibleMainLineCount
                    + lineGap * Math.max(0, visibleMainLineCount - 1);
            float translationHeight = hasTranslation
                    ? translationMetrics.descent - translationMetrics.ascent
                    : 0f;
            float compactGroupHeight = mainHeight
                    + (hasTranslation ? translationGap + translationHeight : 0f);
            float expandedMainHeight = lineHeight * drawLines.size()
                    + lineGap * Math.max(0, drawLines.size() - 1);
            boolean translationReplacementTransition = lineWindow != null
                    && drawLines.size() > visibleMainLineCount
                    && translationAmount < 0.999f;
            float expandedAmount = 1f - translationAmount;
            float compactTop = Math.max(0f, (availableHeight - compactGroupHeight) * 0.5f);
            float expandedTop = Math.max(0f, (availableHeight - expandedMainHeight) * 0.5f);
            float groupHeight = mainHeight
                    + (hasTranslation
                    ? (translationGap + translationHeight) * translationAmount
                    : 0f);
            compactTop = clampTopWithinSlot(
                    compactTop,
                    availableHeight,
                    compactGroupHeight);
            expandedTop = clampTopWithinSlot(
                    expandedTop,
                    availableHeight,
                    expandedMainHeight);
            float top = translationReplacementTransition
                    ? compactTop * translationAmount + expandedTop * expandedAmount
                    : clampTopWithinSlot(
                    Math.max(0f, (availableHeight - groupHeight) * 0.5f),
                    availableHeight,
                    groupHeight);

            canvas.save();
            canvas.clipRect(textView.getPaddingLeft(), 0, textView.getWidth() - textView.getPaddingRight(), textView.getHeight());
            float y = top - mainMetrics.ascent;
            int replacementLineIndex = -1;
            if (translationReplacementTransition) {
                replacementLineIndex = drawTranslationReplacementMainLines(
                        canvas,
                        textView,
                        line,
                        text,
                        activeWord,
                        wordIndex,
                        glowActiveWord,
                        glowWordIndex,
                        compactTop,
                        expandedTop,
                        lineHeight,
                        lineGap,
                        availableWidth,
                        position,
                        glowPosition,
                        activeLine,
                        lineWindow.currentStart,
                        lineWindow.count,
                        focusAmount,
                        translationAmount);
                textView.postInvalidateOnAnimation();
            } else if (lineWindow != null && lineWindow.animating) {
                float slide = dp(textView.getContext(), TRANSLATED_WINDOW_SLIDE_DP);
                float direction = lineWindow.currentStart >= lineWindow.previousStart ? 1f : -1f;
                drawMainLineWindow(
                        canvas,
                        textView,
                        line,
                        text,
                        activeWord,
                        wordIndex,
                        glowActiveWord,
                        glowWordIndex,
                        y - direction * slide * lineWindow.progress,
                        lineHeight,
                        lineGap,
                        availableWidth,
                        position,
                        glowPosition,
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
                        glowActiveWord,
                        glowWordIndex,
                        y + direction * slide * (1f - lineWindow.progress),
                        lineHeight,
                        lineGap,
                        availableWidth,
                        position,
                        glowPosition,
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
                        glowActiveWord,
                        glowWordIndex,
                        y,
                        lineHeight,
                        lineGap,
                        availableWidth,
                        position,
                        glowPosition,
                        activeLine,
                        windowStart,
                        visibleMainLineCount,
                        focusAmount,
                        1f);
            }
            applyFade(1f, focusAmount);
            if (hasTranslation) {
                float translationBaseline;
                if (translationReplacementTransition && replacementLineIndex >= 0) {
                    float compactTranslationBaseline = compactTop
                            + mainHeight
                            + translationGap
                            - translationMetrics.ascent;
                    float replacementBaseline = expandedTop
                            - mainMetrics.ascent
                            + replacementLineIndex * (lineHeight + lineGap);
                    translationBaseline = compactTranslationBaseline * translationAmount
                            + replacementBaseline * expandedAmount;
                } else {
                    translationBaseline = top
                            + lineHeight * visibleMainLineCount
                            + lineGap * Math.max(0, visibleMainLineCount - 1)
                            + translationGap
                            - translationMetrics.ascent
                            + dp(textView.getContext(), TRANSLATION_TOGGLE_SLIDE_DP)
                            * (1f - translationAmount);
                }
                translationPaint.setColor(scaleAlpha(
                        blendColor(
                                TRANSLATION_COLOR,
                                FOCUSED_TRANSLATION_COLOR,
                                focusAmount),
                        translationAmount));
                float translationX = resolveTranslationTextX(
                        textView,
                        model,
                        line,
                        position,
                        activeLine,
                        availableWidth);
                canvas.drawText(line.translation, translationX, translationBaseline, translationPaint);
            }
            canvas.restore();

            if (inactivePaint.getTextSize() != originalSize) {
                setTextSize(originalSize);
            }
        }

        private static float clampTopWithinSlot(
                float top,
                float availableHeight,
                float groupHeight) {
            float maxTop = Math.max(0f, availableHeight - groupHeight);
            return Math.max(0f, Math.min(maxTop, top));
        }

        private float resolveTranslationTextX(
                TextView textView,
                WordLyricModel model,
                WordLine line,
                long position,
                boolean activeLine,
                float availableWidth) {
            float startX = textView.getPaddingLeft();
            if (!activeLine || line == null || TextUtils.isEmpty(line.translation)) {
                return startX;
            }
            float translationWidth = translationPaint.measureText(line.translation);
            float overflow = translationWidth - availableWidth;
            if (overflow <= 0.5f) {
                return startX;
            }
            long displayEndMillis = line.endTimeMillis;
            if (model != null) {
                int lineIndex = model.indexOfLine(line);
                WordLine nextLine = model.lineAt(lineIndex + 1);
                if (nextLine != null && nextLine.timeMillis > line.timeMillis) {
                    displayEndMillis = nextLine.timeMillis;
                }
            }
            long duration = Math.max(1L, displayEndMillis - line.timeMillis);
            float lineProgress = Math.max(
                    0f,
                    Math.min(1f, (position - line.timeMillis) / (float) duration));
            float scrollProgress = Math.max(
                    0f,
                    Math.min(
                            1f,
                            (lineProgress - TRANSLATION_SCROLL_START_PROGRESS)
                                    / (TRANSLATION_SCROLL_END_PROGRESS
                                    - TRANSLATION_SCROLL_START_PROGRESS)));
            float easedScrollProgress = smoothStep(scrollProgress);
            if (scrollProgress < 1f) {
                textView.postInvalidateOnAnimation();
            }
            return startX - overflow * easedScrollProgress;
        }

        private int resolveSlotHeight(TextView textView, WordLyricModel model, WordLine line) {
            if (textView == null || line == null) {
                return 0;
            }
            return dp(textView.getContext(), LYRIC_SLOT_HEIGHT_DP);
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
                float availableWidth,
                long position) {
            int totalLines = drawLines.size();
            int count = Math.min(MAX_TRANSLATED_MAIN_DRAW_LINES, totalLines);
            if (totalLines <= MAX_TRANSLATED_MAIN_DRAW_LINES) {
                resetTranslatedWindow(line, 0, availableWidth);
                return new TranslatedLineWindow(0, 0, count, 1f, 1f, false);
            }

            // RecyclerView can briefly draw two holders mapped to the same lyric while it
            // advances. An inactive holder must not reset the active holder's 2-of-3-line
            // sliding window, otherwise the final wrapped line never stays visible.
            if (!activeLine) {
                return new TranslatedLineWindow(0, 0, count, 1f, 1f, false);
            }

            int targetStart = targetTranslatedMainLineWindowStart(
                    line,
                    activeWord,
                    totalLines,
                    position);
            int widthKey = Math.max(1, Math.round(availableWidth));
            long now = SystemClock.elapsedRealtime();
            if (line.translatedWindowWidthKey != widthKey) {
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

        private int targetTranslatedMainLineWindowStart(
                WordLine line,
                WordRange activeWord,
                int totalLines,
                long position) {
            if (line != null && line.timingMode == LyricTimingMode.LINE_TIMED) {
                return LockscreenIntegrationPolicy.lineTimedSlidingWindowStart(
                        position,
                        line.timeMillis,
                        line.endTimeMillis,
                        line.rendererLayoutWidths,
                        totalLines,
                        MAX_TRANSLATED_MAIN_DRAW_LINES);
            }
            if (activeWord == null) {
                return 0;
            }
            for (int i = 0; i < drawLines.size(); i++) {
                LyricDrawLine drawLine = drawLines.get(i);
                if (activeWord.end > drawLine.start && activeWord.start < drawLine.end) {
                    return LockscreenIntegrationPolicy.clampSlidingWindowStart(
                            i,
                            totalLines,
                            MAX_TRANSLATED_MAIN_DRAW_LINES);
                }
            }
            return 0;
        }

        private int drawTranslationReplacementMainLines(
                Canvas canvas,
                TextView textView,
                WordLine line,
                String text,
                WordRange activeWord,
                int wordIndex,
                WordRange glowActiveWord,
                int glowWordIndex,
                float compactTop,
                float expandedTop,
                float lineHeight,
                float lineGap,
                float availableWidth,
                long position,
                long glowPosition,
                boolean activeLine,
                int windowStart,
                int count,
                float focusAmount,
                float translationAmount) {
            int start = Math.max(0, Math.min(windowStart, drawLines.size()));
            int end = Math.min(drawLines.size(), start + count);
            float expandedAmount = 1f - translationAmount;
            float lineAdvance = lineHeight + lineGap;
            float slide = dp(textView.getContext(), TRANSLATION_TOGGLE_SLIDE_DP);
            int replacementLineIndex = -1;
            for (int i = 0; i < drawLines.size(); i++) {
                LyricDrawLine drawLine = drawLines.get(i);
                boolean inCompactWindow = i >= start && i < end;
                float expandedY = expandedTop - mainFontMetrics.ascent + i * lineAdvance;
                float lineY;
                float alpha;
                if (inCompactWindow) {
                    float compactY = compactTop
                            - mainFontMetrics.ascent
                            + (i - start) * lineAdvance;
                    lineY = compactY * translationAmount + expandedY * expandedAmount;
                    alpha = 1f;
                } else {
                    if (replacementLineIndex < 0) {
                        replacementLineIndex = i;
                    }
                    float slideDirection = i < start ? 1f : -1f;
                    lineY = expandedY + slideDirection * slide * translationAmount;
                    alpha = expandedAmount;
                }
                if (alpha <= 0.01f) {
                    continue;
                }
                applyFade(alpha, focusAmount);
                float x = resolveTextX(textView, drawLine.width, availableWidth);
                drawSegment(
                        canvas,
                        line,
                        text,
                        drawLine,
                        activeWord,
                        wordIndex,
                        glowActiveWord,
                        glowWordIndex,
                        x,
                        lineY,
                        position,
                        glowPosition,
                        activeLine);
            }
            return replacementLineIndex;
        }

        private void drawMainLineWindow(
                Canvas canvas,
                TextView textView,
                WordLine line,
                String text,
                WordRange activeWord,
                int wordIndex,
                WordRange glowActiveWord,
                int glowWordIndex,
                float y,
                float lineHeight,
                float lineGap,
                float availableWidth,
                long position,
                long glowPosition,
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
                float segmentWidth = drawLine.width;
                float x = resolveTextX(textView, segmentWidth, availableWidth);
                drawSegment(
                        canvas,
                        line,
                        text,
                        drawLine,
                        activeWord,
                        wordIndex,
                        glowActiveWord,
                        glowWordIndex,
                        x,
                        lineY,
                        position,
                        glowPosition,
                        activeLine);
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
                long glowPosition,
                boolean activeLine,
                float focusAmount,
                float availableWidth,
                float availableHeight) {
            float originalSize = inactivePaint.getTextSize();
            fitSingleLineText(textView, line.text, availableWidth, originalSize, sp(textView.getContext(), 10f));
            applyFade(1f, focusAmount);

            inactivePaint.getFontMetrics(mainFontMetrics);
            Paint.FontMetrics metrics = mainFontMetrics;
            float lineHeight = metrics.descent - metrics.ascent;
            float y = textView.getPaddingTop() + Math.max(0f, (availableHeight - lineHeight) / 2f) - metrics.ascent;

            canvas.save();
            canvas.clipRect(textView.getPaddingLeft(), 0, textView.getWidth() - textView.getPaddingRight(), textView.getHeight());
            if (activeLine) {
                drawProgressLine(canvas, textView, line, position, glowPosition, y);
            } else {
                canvas.drawText(line.text, textView.getPaddingLeft(), y, inactivePaint);
            }
            canvas.restore();

            if (inactivePaint.getTextSize() != originalSize) {
                setTextSize(originalSize);
            }
        }

        private void drawProgressLine(
                Canvas canvas,
                TextView textView,
                WordLine line,
                long position,
                long glowPosition,
                float y) {
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
            float glowRevealWidth = revealWidth;
            if (glowPosition != position) {
                int glowWordIndex = line.findWordIndex(glowPosition);
                if (glowWordIndex >= 0 && glowWordIndex < line.words.size()) {
                    WordRange glowWord = line.words.get(glowWordIndex);
                    int glowStart = Math.max(0, Math.min(glowWord.start, line.text.length()));
                    int glowEnd = Math.max(glowStart, Math.min(glowWord.end, line.text.length()));
                    glowRevealWidth = inactivePaint.measureText(line.text, 0, glowStart)
                            + inactivePaint.measureText(line.text, glowStart, glowEnd)
                            * line.wordProgress(glowWordIndex, glowPosition);
                } else {
                    glowRevealWidth = 0f;
                }
            }
            drawProgressGlow(
                    canvas,
                    line,
                    line.text,
                    0,
                    line.text.length(),
                    textView.getPaddingLeft(),
                    y,
                    segmentWidth,
                    glowRevealWidth);
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
                WordRange glowActiveWord,
                int glowWordIndex,
                float x,
                float y,
                long position,
                long glowPosition,
                boolean activeLine) {
            canvas.drawText(text, drawLine.start, drawLine.end, x, y, inactivePaint);
            if (!activeLine) {
                return;
            }

            float segmentWidth = drawLine.width;
            if (activeWord == null) {
                return;
            }

            float revealWidth;
            if (drawLine.end <= activeWord.start) {
                revealWidth = segmentWidth;
            } else if (drawLine.start >= activeWord.end) {
                revealWidth = 0f;
            } else {
                revealWidth = resolveSegmentRevealWidth(
                        line,
                        text,
                        drawLine,
                        activeWord,
                        wordIndex,
                        position);
            }
            float glowRevealWidth = revealWidth;
            if (glowPosition != position) {
                if (glowActiveWord == null) {
                    glowRevealWidth = 0f;
                } else if (drawLine.end <= glowActiveWord.start) {
                    glowRevealWidth = segmentWidth;
                } else if (drawLine.start >= glowActiveWord.end) {
                    glowRevealWidth = 0f;
                } else {
                    glowRevealWidth = resolveSegmentRevealWidth(
                            line,
                            text,
                            drawLine,
                            glowActiveWord,
                            glowWordIndex,
                            glowPosition);
                }
            }
            if (glowRevealWidth > 0f) {
                drawProgressGlow(
                        canvas,
                        line,
                        text,
                        drawLine.start,
                        drawLine.end,
                        x,
                        y,
                        segmentWidth,
                        glowRevealWidth);
            }
            if (revealWidth > 0f) {
                drawRevealedText(canvas, text, drawLine.start, drawLine.end, x, y, segmentWidth, revealWidth);
            }
        }

        private float resolveSegmentRevealWidth(
                WordLine line,
                String text,
                LyricDrawLine drawLine,
                WordRange activeWord,
                int wordIndex,
                long position) {
            int activeStart = Math.max(0, Math.min(activeWord.start, text.length()));
            int activeEnd = Math.max(activeStart, Math.min(activeWord.end, text.length()));
            int segmentActiveStart = Math.max(drawLine.start, activeStart);
            int segmentActiveEnd = Math.min(drawLine.end, activeEnd);
            if (segmentActiveStart >= segmentActiveEnd) {
                return drawLine.end <= activeStart
                        ? inactivePaint.measureText(text, drawLine.start, drawLine.end)
                        : 0f;
            }

            float activeWidth = inactivePaint.measureText(text, segmentActiveStart, segmentActiveEnd);
            float beforeActiveInSegment = inactivePaint.measureText(
                    text,
                    drawLine.start,
                    segmentActiveStart);

            if (activeStart >= drawLine.start && activeEnd <= drawLine.end) {
                return beforeActiveInSegment + activeWidth * line.wordProgress(wordIndex, position);
            }

            float totalActiveWidth = 0f;
            float activeWidthBeforeSegment = 0f;
            for (LyricDrawLine candidate : drawLines) {
                int start = Math.max(candidate.start, activeStart);
                int end = Math.min(candidate.end, activeEnd);
                if (start >= end) {
                    continue;
                }
                float width = inactivePaint.measureText(text, start, end);
                if (candidate.end <= drawLine.start) {
                    activeWidthBeforeSegment += width;
                }
                totalActiveWidth += width;
            }
            if (totalActiveWidth <= 0f) {
                return 0f;
            }

            float revealedActiveWidth = totalActiveWidth * line.wordProgress(wordIndex, position);
            float revealedInsideSegment = Math.max(
                    0f,
                    Math.min(activeWidth, revealedActiveWidth - activeWidthBeforeSegment));
            return beforeActiveInSegment + revealedInsideSegment;
        }

        private void drawProgressGlow(
                Canvas canvas,
                WordLine line,
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
            boolean fullyVisible = visibleWidth >= segmentWidth - 0.5f;

            GlowSegmentCache glowCache = obtainGlowSegmentCache(
                    line,
                    text,
                    start,
                    end,
                    segmentWidth,
                    glowPad);
            if (glowCache == null || glowCache.bitmap == null) {
                return;
            }
            int paintAlpha = (activePaint.getColor() >>> 24) & 0xFF;
            float bitmapLeft = x - glowCache.padding;
            float bitmapTop = y - glowCache.baseline;
            if (fullyVisible) {
                drawGlowBitmapBand(
                        canvas,
                        glowCache,
                        bitmapLeft,
                        bitmapTop,
                        segmentLeft - glowPad,
                        segmentRight + glowPad,
                        paintAlpha);
                return;
            }

            // Feather the glow in several narrow bands. A single clipRect leaves a bright,
            // rectangular leading edge that looks like a box sweeping over the lyric.
            float featherWidth = Math.max(4f, activePaint.getTextSize() * 0.58f);
            float featherStart = Math.max(
                    segmentLeft - glowPad,
                    revealRight - featherWidth * 0.35f);
            float featherEnd = Math.min(
                    segmentRight + glowPad,
                    revealRight + featherWidth * 0.85f);
            if (featherStart > segmentLeft - glowPad) {
                drawGlowBitmapBand(
                        canvas,
                        glowCache,
                        bitmapLeft,
                        bitmapTop,
                        segmentLeft - glowPad,
                        featherStart,
                        paintAlpha);
            }
            final int featherBands = 4;
            for (int band = 0; band < featherBands; band++) {
                float startAmount = band / (float) featherBands;
                float endAmount = (band + 1f) / featherBands;
                float bandLeft = featherStart
                        + (featherEnd - featherStart) * startAmount;
                float bandRight = featherStart
                        + (featherEnd - featherStart) * endAmount;
                float alphaAmount = 1f - smoothStep((band + 0.5f) / featherBands);
                drawGlowBitmapBand(
                        canvas,
                        glowCache,
                        bitmapLeft,
                        bitmapTop,
                        bandLeft,
                        bandRight,
                        Math.round(paintAlpha * alphaAmount));
            }
            glowBitmapPaint.setAlpha(255);
        }

        private void drawGlowBitmapBand(
                Canvas canvas,
                GlowSegmentCache glowCache,
                float bitmapLeft,
                float bitmapTop,
                float clipLeft,
                float clipRight,
                int alpha) {
            if (canvas == null
                    || glowCache == null
                    || glowCache.bitmap == null
                    || clipRight <= clipLeft
                    || alpha <= 0) {
                return;
            }
            glowBitmapPaint.setAlpha(Math.max(0, Math.min(255, alpha)));
            int save = canvas.save();
            canvas.clipRect(clipLeft, 0f, clipRight, canvas.getHeight());
            canvas.drawBitmap(glowCache.bitmap, bitmapLeft, bitmapTop, glowBitmapPaint);
            canvas.restoreToCount(save);
        }

        private GlowSegmentCache obtainGlowSegmentCache(
                WordLine line,
                String text,
                int start,
                int end,
                float segmentWidth,
                float glowPad) {
            if (line == null || TextUtils.isEmpty(text) || start >= end) {
                return null;
            }
            int textSizeKey = Math.max(1, Math.round(activePaint.getTextSize() * 10f));
            int widthKey = Math.max(1, Math.round(segmentWidth));
            Typeface typeface = activePaint.getTypeface();
            GlowSegmentCache target = null;
            for (GlowSegmentCache cache : glowSegmentCaches) {
                if (cache.matches(line, start, end, textSizeKey, widthKey, typeface)) {
                    cache.lastUsed = ++glowCacheUseCounter;
                    return cache;
                }
                if (target == null || cache.lastUsed < target.lastUsed) {
                    target = cache;
                }
            }
            if (target == null) {
                return null;
            }

            float glowRadius = Math.max(2f, activePaint.getTextSize() * ACTIVE_GLOW_RADIUS_FACTOR);
            int radiusKey = Math.max(1, Math.round(glowRadius * 10f));
            if (glowMaskFilter == null || glowMaskRadiusKey != radiusKey) {
                glowMaskFilter = new BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL);
                glowMaskRadiusKey = radiusKey;
            }
            int padding = Math.max(2, (int) Math.ceil(Math.max(glowPad, glowRadius * 2.2f)));
            glowRasterPaint.setTypeface(typeface);
            glowRasterPaint.setTextSize(activePaint.getTextSize());
            glowRasterPaint.setStyle(Paint.Style.FILL);
            glowRasterPaint.clearShadowLayer();
            glowRasterPaint.setShader(null);
            glowRasterPaint.setColorFilter(null);
            glowRasterPaint.getFontMetrics(glowFontMetrics);
            float baseline = padding - glowFontMetrics.ascent;
            int requiredWidth = Math.max(1, (int) Math.ceil(segmentWidth) + padding * 2 + 2);
            int requiredHeight = Math.max(
                    1,
                    (int) Math.ceil(glowFontMetrics.descent - glowFontMetrics.ascent)
                            + padding * 2
                            + 2);
            target.ensureBitmap(requiredWidth, requiredHeight);
            if (target.bitmap == null || target.canvas == null) {
                return null;
            }
            target.bitmap.eraseColor(0);
            target.canvas.save();
            target.canvas.clipRect(0, 0, requiredWidth, requiredHeight);
            glowRasterPaint.setColor(ACTIVE_GLOW_SHADOW_COLOR);
            glowRasterPaint.setMaskFilter(glowMaskFilter);
            target.canvas.drawText(text, start, end, padding, baseline, glowRasterPaint);
            glowRasterPaint.setMaskFilter(null);
            glowRasterPaint.setColor(ACTIVE_GLOW_FILL_COLOR);
            target.canvas.drawText(text, start, end, padding, baseline, glowRasterPaint);
            target.canvas.restore();

            target.line = line;
            target.start = start;
            target.end = end;
            target.textSizeKey = textSizeKey;
            target.widthKey = widthKey;
            target.typeface = typeface;
            target.padding = padding;
            target.baseline = baseline;
            target.lastUsed = ++glowCacheUseCounter;
            return target;
        }

        void clearGlowCache() {
            for (GlowSegmentCache cache : glowSegmentCaches) {
                cache.clear();
            }
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
            if (featherStart > segmentLeft) {
                int solidSave = canvas.save();
                canvas.clipRect(segmentLeft, 0f, featherStart, canvas.getHeight());
                canvas.drawText(text, start, end, x, y, activePaint);
                canvas.restoreToCount(solidSave);
            }

            float featherSpan = Math.max(1f, featherEnd - featherStart);
            activeFeatherShaderMatrix.setScale(featherSpan, 1f);
            activeFeatherShaderMatrix.postTranslate(featherStart, 0f);
            activeFeatherShader.setLocalMatrix(activeFeatherShaderMatrix);
            activeFeatherPaint.setAlpha((activePaint.getColor() >>> 24) & 0xFF);
            activeFeatherPaint.setShader(activeFeatherShader);
            int featherSave = canvas.save();
            canvas.clipRect(featherStart, 0f, featherEnd, canvas.getHeight());
            canvas.drawText(text, start, end, x, y, activeFeatherPaint);
            canvas.restoreToCount(featherSave);
            activeFeatherPaint.setShader(null);
        }

        private void buildDrawLines(
                WordLine line,
                String text,
                float availableWidth,
                boolean singleLine,
                boolean balanceUntranslatedText) {
            drawLines.clear();
            if (line == null || TextUtils.isEmpty(text)) {
                return;
            }
            int widthKey = Math.max(1, Math.round(availableWidth));
            int textSizeKey = Math.max(1, Math.round(inactivePaint.getTextSize() * 10f));
            if (line.rendererLayoutWidthKey == widthKey
                    && line.rendererLayoutTextSizeKey == textSizeKey
                    && line.rendererLayoutSingleLine == singleLine) {
                for (int i = 0; i < line.rendererLayoutCount; i++) {
                    addDrawLine(
                            line.rendererLayoutStarts[i],
                            line.rendererLayoutEnds[i],
                            line.rendererLayoutWidths[i]);
                }
                return;
            }

            int textStart = firstNonSpace(text, 0, text.length());
            int textEnd = lastNonSpace(text, textStart, text.length());
            if (textStart >= textEnd) {
                cacheDrawLines(line, widthKey, textSizeKey, singleLine);
                return;
            }

            if (!singleLine
                    && balanceUntranslatedText
                    && shouldBalanceUntranslatedText(text, textStart, textEnd)) {
                int balancedSplit = chooseBalancedSplit(text, textStart, textEnd, availableWidth);
                if (balancedSplit > textStart && balancedSplit < textEnd) {
                    int leftEnd = lastNonSpace(text, textStart, balancedSplit);
                    int rightStart = firstNonSpace(text, balancedSplit, textEnd);
                    float leftWidth = inactivePaint.measureText(text, textStart, leftEnd);
                    float rightWidth = inactivePaint.measureText(text, rightStart, textEnd);
                    if (leftEnd > textStart
                            && rightStart < textEnd
                            && leftWidth <= availableWidth
                            && rightWidth <= availableWidth) {
                        addDrawLine(textStart, leftEnd, leftWidth);
                        addDrawLine(rightStart, textEnd, rightWidth);
                        cacheDrawLines(line, widthKey, textSizeKey, singleLine);
                        return;
                    }
                }
            }

            if (singleLine
                    || inactivePaint.measureText(text, textStart, textEnd) <= availableWidth) {
                addDrawLine(
                        textStart,
                        textEnd,
                        inactivePaint.measureText(text, textStart, textEnd));
                cacheDrawLines(line, widthKey, textSizeKey, singleLine);
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
                    addDrawLine(
                            lineStart,
                            cleanEnd,
                            inactivePaint.measureText(text, lineStart, cleanEnd));
                }
                lineStart = firstNonSpace(text, lineEnd, textEnd);
            }
            cacheDrawLines(line, widthKey, textSizeKey, singleLine);
        }

        private void fitMainTextToMaxDrawLines(
                Context context,
                String text,
                float availableWidth,
                int maxLines) {
            if (context == null
                    || TextUtils.isEmpty(text)
                    || availableWidth <= 1f
                    || maxLines <= 0
                    || requiredWrappedLineCount(text, availableWidth, maxLines) <= maxLines) {
                return;
            }

            float originalSize = inactivePaint.getTextSize();
            float minimumSize = Math.min(originalSize, sp(context, 12f));
            setTextSize(minimumSize);
            if (requiredWrappedLineCount(text, availableWidth, maxLines) > maxLines) {
                return;
            }

            float fittingSize = minimumSize;
            float overflowingSize = originalSize;
            for (int i = 0; i < 8; i++) {
                float candidate = (fittingSize + overflowingSize) * 0.5f;
                setTextSize(candidate);
                if (requiredWrappedLineCount(text, availableWidth, maxLines) <= maxLines) {
                    fittingSize = candidate;
                } else {
                    overflowingSize = candidate;
                }
            }
            setTextSize(fittingSize);
        }

        private int requiredWrappedLineCount(
                String text,
                float availableWidth,
                int stopAfterLines) {
            int textStart = firstNonSpace(text, 0, text.length());
            int textEnd = lastNonSpace(text, textStart, text.length());
            if (textStart >= textEnd) {
                return 0;
            }
            int count = 0;
            int lineStart = textStart;
            while (lineStart < textEnd) {
                int lineEnd = chooseWrapEnd(text, lineStart, textEnd, availableWidth);
                if (lineEnd <= lineStart) {
                    return stopAfterLines + 1;
                }
                int cleanEnd = lastNonSpace(text, lineStart, lineEnd);
                if (cleanEnd <= lineStart
                        || inactivePaint.measureText(text, lineStart, cleanEnd)
                        > availableWidth + 0.5f) {
                    return stopAfterLines + 1;
                }
                count++;
                if (count > stopAfterLines) {
                    return count;
                }
                lineStart = firstNonSpace(text, lineEnd, textEnd);
            }
            return count;
        }

        private static boolean shouldBalanceUntranslatedText(String text, int start, int end) {
            if (!textContainsSpace(text, start, end)) {
                return false;
            }
            int visibleCharacters = 0;
            for (int i = start; i < end; i++) {
                char character = text.charAt(i);
                if (character == ':' || character == '\uff1a') {
                    return false;
                }
                if (!Character.isWhitespace(character)) {
                    visibleCharacters++;
                }
            }
            return visibleCharacters >= 8;
        }

        private void addDrawLine(int start, int end, float width) {
            int index = drawLines.size();
            if (index >= drawLinePool.length) {
                return;
            }
            LyricDrawLine drawLine = drawLinePool[index];
            drawLine.start = start;
            drawLine.end = end;
            drawLine.width = width;
            drawLines.add(drawLine);
        }

        private void cacheDrawLines(
                WordLine line, int widthKey, int textSizeKey, boolean singleLine) {
            line.rendererLayoutWidthKey = widthKey;
            line.rendererLayoutTextSizeKey = textSizeKey;
            line.rendererLayoutSingleLine = singleLine;
            line.rendererLayoutCount = drawLines.size();
            for (int i = 0; i < drawLines.size(); i++) {
                LyricDrawLine drawLine = drawLines.get(i);
                line.rendererLayoutStarts[i] = drawLine.start;
                line.rendererLayoutEnds[i] = drawLine.end;
                line.rendererLayoutWidths[i] = drawLine.width;
            }
        }

        private int chooseWrapEnd(String text, int start, int end, float availableWidth) {
            return LyricLineBreakPolicy.chooseWrapEnd(
                    text,
                    start,
                    end,
                    availableWidth,
                    (value, rangeStart, rangeEnd) ->
                            inactivePaint.measureText(value, rangeStart, rangeEnd));
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
                maxWidth = Math.max(maxWidth, drawLine.width);
            }
            if (maxWidth > availableWidth) {
                fittedSize = Math.min(fittedSize, fittedSize * availableWidth / Math.max(1f, maxWidth));
            }

            inactivePaint.getFontMetrics(mainFontMetrics);
            Paint.FontMetrics metrics = mainFontMetrics;
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

        private void configurePaints(
                TextView textView, boolean compactSlot, boolean untranslatedLayout) {
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
                float textSizeSp = untranslatedLayout
                        ? UNTRANSLATED_TEXT_SIZE_SP
                        : MAIN_TEXT_SIZE_SP;
                setTextSize(sp(textView.getContext(), textSizeSp));
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
            inactivePaint.setStyle(Paint.Style.FILL);
            playedPaint.setStyle(Paint.Style.FILL);
            activePaint.setStyle(Paint.Style.FILL);
            activeGlowPaint.setStyle(Paint.Style.FILL);
            activeFeatherPaint.setStyle(Paint.Style.FILL);
            translationPaint.setStyle(Paint.Style.FILL);
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

        private static final class GlowSegmentCache {
            Bitmap bitmap;
            Canvas canvas;
            WordLine line;
            Typeface typeface;
            int start;
            int end;
            int textSizeKey;
            int widthKey;
            int padding;
            float baseline;
            long lastUsed;

            boolean matches(
                    WordLine candidateLine,
                    int candidateStart,
                    int candidateEnd,
                    int candidateTextSizeKey,
                    int candidateWidthKey,
                    Typeface candidateTypeface) {
                return bitmap != null
                        && !bitmap.isRecycled()
                        && line == candidateLine
                        && start == candidateStart
                        && end == candidateEnd
                        && textSizeKey == candidateTextSizeKey
                        && widthKey == candidateWidthKey
                        && typeface == candidateTypeface;
            }

            void ensureBitmap(int width, int height) {
                boolean replace = bitmap == null
                        || bitmap.isRecycled()
                        || bitmap.getWidth() < width
                        || bitmap.getHeight() < height;
                if (!replace) {
                    return;
                }
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                canvas = new Canvas(bitmap);
            }

            void clear() {
                line = null;
                typeface = null;
                lastUsed = 0L;
            }
        }

        private static final class LyricDrawLine {
            int start;
            int end;
            float width;
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

    private static final class MetadataTrackIdentity {
        final String title;
        final String artist;
        final boolean saltRelay;

        MetadataTrackIdentity(String title, String artist, boolean saltRelay) {
            this.title = title;
            this.artist = artist;
            this.saltRelay = saltRelay;
        }
    }

    private static final class NormalizedTextSnapshot {
        final CharSequence source;
        final int length;
        final int contentHash;
        final String normalized;

        NormalizedTextSnapshot(
                CharSequence source, int length, int contentHash, String normalized) {
            this.source = source;
            this.length = length;
            this.contentHash = contentHash;
            this.normalized = normalized;
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
        final long glowPosition;
        final boolean active;
        final boolean focused;

        DrawFrame(
                WordLyricModel model,
                WordLine line,
                int lineIndex,
                int activeIndex,
                long position,
                long glowPosition,
                boolean active,
                boolean focused) {
            this.model = model;
            this.line = line;
            this.lineIndex = lineIndex;
            this.activeIndex = activeIndex;
            this.position = position;
            this.glowPosition = glowPosition;
            this.active = active;
            this.focused = focused;
        }
    }

    private static final class LyricsRecyclerGeometry {
        static final LyricsRecyclerGeometry EMPTY =
                new LyricsRecyclerGeometry(-1, 0, Integer.MIN_VALUE);

        final int firstVisiblePosition;
        final int firstVisibleTop;
        final int targetCenter;

        LyricsRecyclerGeometry(int firstVisiblePosition, int firstVisibleTop, int targetCenter) {
            this.firstVisiblePosition = firstVisiblePosition;
            this.firstVisibleTop = firstVisibleTop;
            this.targetCenter = targetCenter;
        }
    }

    private static final class WordLyricModel {
        final ArrayList<WordLine> lines = new ArrayList<>();
        final ArrayList<WordLine> officialLines = new ArrayList<>();
        final LinkedHashMap<String, Integer> renderableTextCounts = new LinkedHashMap<>();
        boolean renderableTextIndexBuilt;
        String parserName = "lyrics-core";

        WordLine findLine(long position, String currentLine) {
            String normalizedCurrent = normalizeLine(currentLine);
            WordLine fallback = null;
            for (WordLine line : lines) {
                if (!TextUtils.isEmpty(normalizedCurrent)
                        && matchesWordLineText(line, normalizedCurrent)) {
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

        WordLine lineAtOfficialIndex(int index) {
            return index >= 0 && index < officialLines.size()
                    ? officialLines.get(index)
                    : null;
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

        int displayIndexAt(long position) {
            WordLine active = findActiveLine(position);
            int index = indexOfLine(active);
            return index >= 0 ? index : lines.isEmpty() ? -1 : 0;
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

        WordLine findNearestLineByTime(long timeMillis, long maxDistanceMillis) {
            if (timeMillis < 0 || lines.isEmpty()) {
                return null;
            }
            WordLine best = null;
            long bestDistance = Math.max(0L, maxDistanceMillis) + 1L;
            for (WordLine line : lines) {
                long distance = Math.abs(line.timeMillis - timeMillis);
                if (distance < bestDistance) {
                    best = line;
                    bestDistance = distance;
                }
                if (line.timeMillis > timeMillis && distance > bestDistance) {
                    break;
                }
            }
            return bestDistance <= Math.max(0L, maxDistanceMillis) ? best : null;
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
                if (matchesWordLineText(line, normalizedText)) {
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

        WordLine findLineByTextOccurrence(String normalizedText, int occurrence) {
            if (TextUtils.isEmpty(normalizedText) || occurrence < 0) {
                return null;
            }
            int seen = 0;
            for (WordLine line : lines) {
                if (!matchesWordLineText(line, normalizedText)) {
                    continue;
                }
                if (seen++ == occurrence) {
                    return line;
                }
            }
            return null;
        }

        boolean hasRenderableText(String normalizedText) {
            if (TextUtils.isEmpty(normalizedText)) {
                return false;
            }
            ensureRenderableTextIndex();
            if (renderableTextCounts.containsKey(normalizedText)) {
                return true;
            }
            for (WordLine line : lines) {
                if (matchesWordLineText(line, normalizedText)) {
                    return true;
                }
                if (line.normalizedTranslation().equals(normalizedText)) {
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
                if (!matchesWordLineText(line, normalizedText)) {
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
                if (line.normalizedTranslation().equals(normalizedText)) {
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
                if (!line.normalizedTranslation().equals(normalizedText)) {
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

        boolean hasDuplicateRenderableText(String normalizedText) {
            if (TextUtils.isEmpty(normalizedText)) {
                return false;
            }
            ensureRenderableTextIndex();
            Integer exactCount = renderableTextCounts.get(normalizedText);
            if (exactCount != null) {
                return exactCount > 1;
            }
            int count = 0;
            for (WordLine line : lines) {
                if (matchesWordLineText(line, normalizedText)
                        || line.normalizedTranslation().equals(normalizedText)) {
                    count++;
                    if (count > 1) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void ensureRenderableTextIndex() {
            if (renderableTextIndexBuilt) {
                return;
            }
            renderableTextCounts.clear();
            for (WordLine line : lines) {
                String primary = line.normalizedText;
                String display = line.normalizedDisplayText();
                String translation = line.normalizedTranslation();
                incrementRenderableTextCount(primary);
                if (!display.equals(primary)) {
                    incrementRenderableTextCount(display);
                }
                if (!translation.equals(primary) && !translation.equals(display)) {
                    incrementRenderableTextCount(translation);
                }
            }
            renderableTextIndexBuilt = true;
        }

        private void incrementRenderableTextCount(String normalizedText) {
            if (TextUtils.isEmpty(normalizedText)) {
                return;
            }
            Integer count = renderableTextCounts.get(normalizedText);
            renderableTextCounts.put(normalizedText, count == null ? 1 : count + 1);
        }
    }

    private enum LyricTimingMode {
        WORD_TIMED,
        LINE_TIMED
    }

    private static final class WordLine {
        final long timeMillis;
        final long endTimeMillis;
        final String text;
        final String normalizedText;
        final String textMatchKey;
        final ArrayList<WordRange> words;
        final LyricTimingMode timingMode;
        String displayText = "";
        String translation = "";
        private String normalizedDisplaySource = "";
        private String normalizedDisplayText = "";
        private String displayMatchKey = "";
        private String normalizedTranslationSource = "";
        private String normalizedTranslationText = "";
        int rendererLayoutWidthKey = -1;
        int rendererLayoutTextSizeKey = -1;
        boolean rendererLayoutSingleLine;
        int rendererLayoutCount;
        final int[] rendererLayoutStarts = new int[3];
        final int[] rendererLayoutEnds = new int[3];
        final float[] rendererLayoutWidths = new float[3];
        int translatedWindowWidthKey = -1;
        int translatedWindowStart;
        int translatedWindowPreviousStart;
        long translatedWindowChangedAtMs;
        int focusedVisualActiveIndex = Integer.MIN_VALUE;
        long focusedVisualStartElapsedMs;

        WordLine(long timeMillis, String text, ArrayList<WordRange> words) {
            this(
                    timeMillis,
                    text,
                    words,
                    inferWordLineEndMillis(timeMillis, words),
                    words != null && words.size() > 1
                            ? LyricTimingMode.WORD_TIMED
                            : LyricTimingMode.LINE_TIMED);
        }

        WordLine(long timeMillis, String text, ArrayList<WordRange> words, long endTimeMillis) {
            this(
                    timeMillis,
                    text,
                    words,
                    endTimeMillis,
                    words != null && words.size() > 1
                            ? LyricTimingMode.WORD_TIMED
                            : LyricTimingMode.LINE_TIMED);
        }

        WordLine(
                long timeMillis,
                String text,
                ArrayList<WordRange> words,
                long endTimeMillis,
                LyricTimingMode timingMode) {
            this.timeMillis = timeMillis;
            this.endTimeMillis = Math.max(timeMillis, endTimeMillis);
            this.text = text;
            this.normalizedText = normalizeLine(text);
            this.textMatchKey = lyricMatchKeyFromNormalized(normalizedText);
            this.words = words;
            this.timingMode = timingMode == null
                    ? LyricTimingMode.LINE_TIMED
                    : timingMode;
        }

        String normalizedDisplayText() {
            String source = nullToEmpty(displayText);
            if (!source.equals(normalizedDisplaySource)) {
                normalizedDisplaySource = source;
                normalizedDisplayText = normalizeLine(source);
                displayMatchKey = lyricMatchKeyFromNormalized(normalizedDisplayText);
            }
            return normalizedDisplayText;
        }

        String displayMatchKey() {
            normalizedDisplayText();
            return displayMatchKey;
        }

        String normalizedTranslation() {
            String source = nullToEmpty(translation);
            if (!source.equals(normalizedTranslationSource)) {
                normalizedTranslationSource = source;
                normalizedTranslationText = normalizeLine(source);
            }
            return normalizedTranslationText;
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

    private static final class InlineTimedLyricLine {
        final long timeMillis;
        final long endTimeMillis;
        final String text;
        final ArrayList<WordRange> words;
        final boolean inlineTiming;
        final int order;

        InlineTimedLyricLine(
                long timeMillis,
                long endTimeMillis,
                String text,
                ArrayList<WordRange> words,
                boolean inlineTiming,
                int order) {
            this.timeMillis = timeMillis;
            this.endTimeMillis = Math.max(timeMillis, endTimeMillis);
            this.text = text;
            this.words = words;
            this.inlineTiming = inlineTiming;
            this.order = order;
        }
    }

    private static boolean sameWordLine(WordLine left, WordLine right) {
        return left != null && right != null && left == right;
    }

    private static boolean matchesWordLineText(WordLine line, String normalizedText) {
        if (line == null || TextUtils.isEmpty(normalizedText)) {
            return false;
        }
        String normalizedDisplayText = line.normalizedDisplayText();
        if (line.normalizedText.equals(normalizedText)
                || normalizedDisplayText.equals(normalizedText)) {
            return true;
        }
        if (isLyricPrefixMatchCached(
                normalizedText,
                line.normalizedText,
                line.textMatchKey)) {
            return true;
        }
        return !TextUtils.isEmpty(normalizedDisplayText)
                && isLyricPrefixMatchCached(
                normalizedText,
                normalizedDisplayText,
                line.displayMatchKey());
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
        if (TextUtils.isEmpty(visibleText) || TextUtils.isEmpty(fullText)) {
            return false;
        }
        String visibleKey = lyricMatchKey(visibleText);
        String fullKey = lyricMatchKey(fullText);
        return LyricTextMatchPolicy.hasSubstantialPrefix(
                visibleText,
                fullText,
                visibleKey,
                fullKey);
    }

    private static boolean isLyricPrefixMatchCached(
            String visibleText, String fullText, String fullKey) {
        if (TextUtils.isEmpty(visibleText) || TextUtils.isEmpty(fullText)) {
            return false;
        }
        String visibleKey = lyricMatchKeyFromNormalized(visibleText);
        return LyricTextMatchPolicy.hasSubstantialPrefix(
                visibleText,
                fullText,
                visibleKey,
                fullKey);
    }

    private static String lyricMatchKey(String text) {
        return lyricMatchKeyFromNormalized(normalizeLine(text));
    }

    private static String lyricMatchKeyFromNormalized(String normalized) {
        StringBuilder key = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                key.append(Character.toLowerCase(ch));
            }
        }
        return key.toString();
    }

    private static String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        String normalized = LyricTextSanitizer.removeIgnorableCharacters(line);
        if (normalized.indexOf('[') >= 0 || normalized.indexOf('<') >= 0) {
            normalized = ANY_LRC_TIME_TAG.matcher(normalized).replaceAll("");
        }
        int length = normalized.length();
        int start = 0;
        int end = length;
        while (start < end && normalized.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && normalized.charAt(end - 1) <= ' ') {
            end--;
        }
        boolean collapseWhitespace = false;
        for (int i = start + 1; i < end; i++) {
            char previous = normalized.charAt(i - 1);
            char current = normalized.charAt(i);
            if ((previous == ' ' || previous == '\t')
                    && (current == ' ' || current == '\t')) {
                collapseWhitespace = true;
                break;
            }
        }
        if (!collapseWhitespace) {
            return start == 0 && end == length
                    ? normalized
                    : normalized.substring(start, end);
        }
        StringBuilder result = new StringBuilder(end - start);
        boolean inWhitespaceRun = false;
        for (int i = start; i < end; i++) {
            char ch = normalized.charAt(i);
            boolean whitespace = ch == ' ' || ch == '\t';
            if (whitespace) {
                if (!inWhitespaceRun) {
                    result.append(ch);
                } else if (result.charAt(result.length() - 1) != ' ') {
                    result.setCharAt(result.length() - 1, ' ');
                }
                inWhitespaceRun = true;
            } else {
                result.append(ch);
                inWhitespaceRun = false;
            }
        }
        return result.toString();
    }

}
