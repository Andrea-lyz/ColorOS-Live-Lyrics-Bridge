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
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import io.github.andrealtb.lockscreenlyrics.render.LineTimedRevealPolicy;
import io.github.andrealtb.lockscreenlyrics.render.CachedDrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.CharLiftGeometry;
import io.github.andrealtb.lockscreenlyrics.render.DrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.LyricDrawLine;
import io.github.andrealtb.lockscreenlyrics.render.LyricDrawLayoutEngine;
import io.github.andrealtb.lockscreenlyrics.render.LyricTimingMode;
import io.github.andrealtb.lockscreenlyrics.render.LyricsRecyclerGeometry;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricFrameResolver;
import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricDataset;
import io.github.andrealtb.lockscreenlyrics.render.LyricRenderTransactions;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderConstants;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.github.andrealtb.lockscreenlyrics.bootstrap.SystemUiRuntimeBootstrap;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.DynamicArtworkRuntime;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkTrace;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugApplyPolicy;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugConfig;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgePerformanceSampler;
import io.github.andrealtb.lockscreenlyrics.diagnostics.ArtworkDiagnostics;
import io.github.andrealtb.lockscreenlyrics.diagnostics.SensitiveFieldRedactor;
import io.github.andrealtb.lockscreenlyrics.diagnostics.StructuredBridgeLog;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoCoverPolicy;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoMediaIdentityPolicy;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoPluginMediaModelReader;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoSameTrackLyricRetention;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoSystemUiRuntime;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.LyricsRecyclerFieldAccessor;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.LyricsRecyclerPolicy;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.SystemUiLoadLyricPolicy;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static io.github.andrealtb.lockscreenlyrics.LyricLogDescriptions.*;
import static io.github.andrealtb.lockscreenlyrics.TranslationActionController.normalizeTranslationPreferencePackage;
import static io.github.andrealtb.lockscreenlyrics.TranslationActionController.translationPreferenceKeyForPackage;
import static io.github.andrealtb.lockscreenlyrics.TranslationActionViewTracker.isTranslationActionDescription;
import static io.github.andrealtb.lockscreenlyrics.ScreenTimeoutController.SCREEN_TIMEOUT_MODEL_EVIDENCE_GRACE_MS;
import static io.github.andrealtb.lockscreenlyrics.ScreenTimeoutController.SCREEN_TIMEOUT_VISIBLE_LYRIC_NOTE_THROTTLE_MS;

public final class LockscreenLyricsModule extends XposedModule {
    static final String TAG = "LockscreenLyrics";
    private static final String LYRIC_PARSE_TRACE_TAG = "LockscreenLyricsParse";
    private static final String LYRIC_RENDER_PIPELINE_REVISION =
            "native-v5-systemui-owner-r6-track-reset";
    // Runtime debug is off by default. Enable it from the settings Debug page, or with
    // log.tag.LockscreenLyrics=DEBUG. Release and debug APKs share the same default.
    private static final boolean LYRIC_VERBOSE_DIAGNOSTICS_ENABLED = false;
    private static final boolean LYRIC_PARSE_TRACE_ENABLED = false;
    private static final int LYRIC_PARSE_TRACE_CHUNK_SIZE = 3000;
    // A cleanup change may replay the last SystemUI metadata only while it still describes the
    // current track. This bounds a stale context that survived a vendor-side load failure.
    private static final long SYSTEM_UI_METADATA_REPLAY_MAX_AGE_MS = 12L * 60L * 60L * 1000L;
    private static volatile String logProcessName = "unknown";
    private static volatile LyricUiConfig runtimeLyricUiConfig = LyricUiConfig.defaults();
    private static final String MODULE_PACKAGE = "io.github.andrealtb.lockscreenlyrics";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String OPLUS_PLUGIN_CLASS_LOADER_CLASS =
            "com.android.systemui.shared.plugins.OPlusPluginClassLoader";
    // ColorOS 17 SystemUI has no OPlusPluginClassLoader constructor: the factory builds the loader
    // and assigns its package filter afterwards, so plugin hooks start when the factory returns.
    private static final String OPLUS_PLUGIN_FACTORY_CLASS =
            "com.android.systemui.shared.plugins.PluginInstance$PluginFactory";
    private static final String OPLUS_PLUGIN_FACTORY_CREATE_CLASS_LOADER_METHOD =
            "createClassLoader";
    private static final String LYRICS_RECYCLER_VIEW_CLASS =
            "com.oplus.systemui.plugins.shared.template.component.media.view.LyricsRecyclerView";
    private static final String LYRICS_SWITCHER_VIEW_CLASS =
            "com.oplus.systemui.plugins.shared.template.component.media.view.LyricsSwitcherView";
    private static final String OPLUS_LYRIC_INFO_KEY = LyricInfoContract.METADATA_KEY;
    private static final String OPLUS_RAW_LYRIC_INFO_KEY = LyricInfoContract.JSON_RAW_LYRIC;
    private static final String HOOK_ID_SYSTEMUI_LOAD_LYRIC = "oplus-word-load-lyric";
    private static final String HOOK_ID_SYSTEMUI_ARTWORK_ICON =
            "oplus-word-artwork-icon";
    private static final String HOOK_ID_SYSTEMUI_SEEDLING_ARTWORK =
            "oplus-seedling-artwork-icon";
    private static final String HOOK_ID_SYSTEMUI_ARTWORK_LOADER_PROBE =
            "oplus-seedling-artwork-loader-probe";
    private static final String HOOK_ID_MEDIA_DATA_METADATA_CHANGED =
            "oplus-media-data-metadata-changed";
    private static final String HOOK_ID_MEDIA_DATA_LOAD_METADATA =
            "oplus-media-data-load-metadata";
    private static final String HOOK_ID_SEEDLING_MEDIA_BUNDLE = "oplus-word-seedling-media-bundle";
    private static final String HOOK_ID_TEXTVIEW_ON_DRAW = "oplus-word-textview-on-draw";
    private static final String HOOK_ID_TEXTVIEW_SET_TEXT = "oplus-word-textview-set-text";
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
    private static final String HOOK_ID_PLUGIN_CLASS_LOADER_CONSTRUCTOR =
            "oplus-word-plugin-classloader-constructor";
    private static final String HOOK_ID_PLUGIN_CLASS_LOADER_FACTORY =
            "oplus-word-plugin-classloader-factory";
    private static final String HOOK_ID_LYRICS_RECYCLER = "oplus-word-lyrics-recycler";
    private static final String HOOK_ID_LYRICS_RECYCLER_NOTIFY_GUARD =
            "oplus-word-lyrics-recycler-notify-guard";
    private static final String HOOK_ID_RECYCLER_ADAPTER_NOTIFY_CHANGED =
            "oplus-word-recycler-adapter-notify-changed";
    private static final String HOOK_ID_RUS_GET_WHITE_LIST = "oplus-media-rus-get-white-list";
    private static final String HOOK_ID_GET_LYRIC_ENTRANCE = "oplus-media-get-lyric-entrance";
    private static final String HOOK_ID_RESOLVE_LYRIC_SUPPORT = "oplus-media-resolve-lyric-support";
    private static final String HOOK_ID_UPDATE_PKG_ACTIONS_RULE = "oplus-media-update-pkg-actions-rule";
    private static final String HOOK_ID_TRANSLATION_TOGGLE_ACTION =
            "oplus-media-translation-toggle-action";
    private static final String TRANSLATION_TOGGLE_ACTION =
            LyricInfoContract.ACTION_TOGGLE_TRANSLATION;
    static final String TRANSLATION_PREFERENCES_NAME = LyricUiSettings.PREFERENCES_NAME;
    static final String TRANSLATION_PREFERENCE_KEY =
            LyricUiSettings.TRANSLATION_PREFERENCE_KEY;
    private static final String TRANSLATION_ACTION_NAME = "\u7ffb\u8bd1";
    private static final long OFFICIAL_DRAW_FRAME_TRANSIENT_MISS_GRACE_MS =
            LyricTimingTuningConstants.OfficialLyric.DRAW_FRAME_TRANSIENT_MISS_GRACE_MS;
    private static final long SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS =
            LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS;
    private static final long SYSTEMUI_LYRIC_HANDOFF_MIN_MASK_MS =
            LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_HANDOFF_MIN_MASK_MS;
    private static final float SYSTEMUI_LYRIC_HANDOFF_HIDDEN_ALPHA =
            LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_HANDOFF_HIDDEN_ALPHA;
    private static final float SYSTEMUI_LYRIC_VISIBLE_ALPHA =
            LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_VISIBLE_ALPHA;
    private static final long SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS =
            LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS;
    private static final long[] AOD_LYRIC_HANDOFF_REDRAW_DELAYS_MS =
            LyricTimingTuningConstants.Aod.HANDOFF_REDRAW_DELAYS_MS;
    private static final long SYSTEMUI_LYRIC_ROW_REBIND_WINDOW_MS =
            LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_ROW_REBIND_WINDOW_MS;
    private static final long SYSTEMUI_TRACK_RESET_POSITION_GUARD_MS =
            LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_TRACK_RESET_POSITION_GUARD_MS;
    private static final long SYSTEMUI_TRACK_RESET_STALE_POSITION_MS =
            LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_TRACK_RESET_STALE_POSITION_MS;
    // Text-only line updates do not need a 60 Hz invalidation loop. Word-timed progress and
    // active row visual transitions still retain the display-rate path below.
    private static final long ACTIVE_LYRIC_FRAME_DELAY_MS =
            LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_FRAME_DELAY_MS;
    private static final long ACTIVE_LYRIC_STATIC_FRAME_DELAY_MS =
            LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_STATIC_FRAME_DELAY_MS;
    private static final long AOD_ACTIVE_LYRIC_REFRESH_DELAY_MS =
            LyricTimingTuningConstants.Aod.ACTIVE_LYRIC_REFRESH_DELAY_MS;
    private static final long ACTIVE_LYRIC_RETRY_DELAY_MS =
            LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_RETRY_DELAY_MS;
    private static final long LYRIC_RECYCLER_SCREEN_STATE_SETTLE_MS =
            LyricTimingTuningConstants.LyricGeneral.LYRIC_RECYCLER_SCREEN_STATE_SETTLE_MS;
    private static final long LYRIC_RECYCLER_SET_CURRENT_SETTLE_MS =
            LyricTimingTuningConstants.LyricGeneral.LYRIC_RECYCLER_SET_CURRENT_SETTLE_MS;
    private static final long ADAPTIVE_LINE_TIMED_RECYCLER_MIN_LAG_MS = 650L;
    private static final long LYRIC_PLAYBACK_POSITION_JUMP_MS =
            LyricTimingTuningConstants.LyricGeneral.PLAYBACK_POSITION_JUMP_MS;
    private static final int LYRIC_BOUND_FRAME_RETRY_MAX =
            LyricTimingTuningConstants.LyricGeneral.BOUND_FRAME_RETRY_MAX;
    private static final int LYRIC_SURFACE_RENDER_PASS_MAX_FRAMES =
            LyricTimingTuningConstants.LyricGeneral.SURFACE_RENDER_PASS_MAX_FRAMES;
    private static final long LYRIC_SURFACE_RENDER_PASS_MAX_MS =
            LyricTimingTuningConstants.LyricGeneral.SURFACE_RENDER_PASS_MAX_MS;
    private static final long LYRIC_UI_STYLE_SETTINGS_RELOAD_MS =
            LyricTimingTuningConstants.LyricGeneral.UI_STYLE_SETTINGS_RELOAD_MS;
    private static final long LYRIC_VISIBILITY_RECOVERY_FIRST_DELAY_MS =
            LyricTimingTuningConstants.LyricGeneral.VISIBILITY_RECOVERY_FIRST_DELAY_MS;
    private static final long LYRIC_VISIBILITY_RECOVERY_SECOND_DELAY_MS =
            LyricTimingTuningConstants.LyricGeneral.VISIBILITY_RECOVERY_SECOND_DELAY_MS;
    private static final long LYRIC_VISIBILITY_RECOVERY_FINAL_DELAY_MS =
            LyricTimingTuningConstants.LyricGeneral.VISIBILITY_RECOVERY_FINAL_DELAY_MS;
    private static final long LYRIC_VISIBILITY_RECOVERY_LONG_DELAY_MS =
            LyricTimingTuningConstants.LyricGeneral.VISIBILITY_RECOVERY_LONG_DELAY_MS;
    private static final long LYRIC_VISIBILITY_RECOVERY_LAST_DELAY_MS =
            LyricTimingTuningConstants.LyricGeneral.VISIBILITY_RECOVERY_LAST_DELAY_MS;
    private static final long LYRIC_SURFACE_PROVISIONAL_DRAW_GRACE_MS =
            LyricTimingTuningConstants.LyricGeneral.SURFACE_PROVISIONAL_DRAW_GRACE_MS;
    private static final long[] LYRIC_VISIBILITY_RECOVERY_DELAYS_MS =
            LyricTimingTuningConstants.LyricGeneral.VISIBILITY_RECOVERY_DELAYS_MS;
    private static final long TRANSLATION_TOGGLE_LAYOUT_FRAME_MS =
            LyricTimingTuningConstants.Translation.TOGGLE_LAYOUT_FRAME_MS;
    private static final float OFFICIAL_LYRIC_INACTIVE_BLUR_RADIUS_PX =
            LyricTimingTuningConstants.OfficialLyric.INACTIVE_BLUR_RADIUS_PX;
    private static final float OFFICIAL_LYRIC_BLUR_ZERO_THRESHOLD_PX =
            LyricTimingTuningConstants.OfficialLyric.BLUR_ZERO_THRESHOLD_PX;
    private static final long OFFICIAL_LYRIC_ROW_SCALE_ATTACH_SUPPRESS_MS =
            LyricTimingTuningConstants.OfficialLyric.ROW_SCALE_ATTACH_SUPPRESS_MS;
    static final long AOD_WORD_PROGRESS_TO_LINE_ANIMATION_MS =
            LyricTimingTuningConstants.Aod.WORD_PROGRESS_TO_LINE_ANIMATION_MS;
    private static final float ACTIVE_LYRIC_CENTER_OFFSET_DP =
            LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_CENTER_OFFSET_DP;
    private static final float ACTIVE_LYRIC_POSITION_SHIFT_UP_DP =
            LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_POSITION_SHIFT_UP_DP;
    // A bound lyric row is reused by more than one immersive RecyclerView during SystemUI
    // cross-fades. Reusing a frame by TextView identity can therefore carry the old surface's
    // adapter position into the new surface. Resolve every frame from the current binding.
    private static final boolean OFFICIAL_DRAW_FRAME_REUSE_ENABLED =
            LyricTimingTuningConstants.OfficialLyric.DRAW_FRAME_REUSE_ENABLED;
    private static final long OFFICIAL_FRAME_DECISION_LOG_INTERVAL_MS =
            LyricTimingTuningConstants.OfficialLyric.FRAME_DECISION_LOG_INTERVAL_MS;
    private static final long OFFICIAL_ADAPTER_SUPPRESSION_LOG_INTERVAL_MS =
            LyricTimingTuningConstants.OfficialLyric.ADAPTER_SUPPRESSION_LOG_INTERVAL_MS;
    private static final Pattern LRC_TIME_TAG = Pattern.compile("\\[[0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?\\]");
    private volatile WordLyricModel currentWordLyricModel;
    private volatile String currentWordLyricModelSignature = "";
    private final Object wordLyricModelCacheLock = new Object();
    private volatile String currentWordLyricModelTrackKey = "";
    private volatile long lastTextViewSpanLogAt;
    private volatile long lastTextViewDrawLogAt;
    private volatile long lastRecyclerLogAt;
    private volatile long lastRecyclerScrollStabilizeLogAt;
    private volatile long lastLyricsRecyclerForceAlignLogAt;
    private volatile long lastOfficialLyricPayloadLogAt;
    private volatile long lastLyricLayoutDiagnosticsLogAt;
    private volatile long lastOfficialFrameDecisionLogAt;
    private final Map<String, Long> officialRendererFallbackLogAt =
            new ConcurrentHashMap<>();
    private int lastCustomDrawLogGeometryGeneration = -1;
    private volatile String lastOfficialFrameDecisionLogKey = "";
    private static final Map<String, Long> officialAdapterSuppressionLogTimes =
            new ConcurrentHashMap<>();
    private volatile long lastActiveRefreshLogAt;
    private volatile long lastSeedlingPlaybackStateLogAt;
    private volatile long lastComputedPositionMs = -1L;
    private volatile long lastComputedPositionElapsedMs = -1L;
    private volatile long lastSeedlingActiveLineTimeMs = -1L;
    private volatile long lastSeedlingActiveLineObservedAtMs = -1L;
    private volatile long lastLyricTrackPositionResetLogAt;
    private volatile long lyricTrackPositionResetGuardUntilElapsedMs;
    private volatile long lastSystemUiTrackIdentityChangedAtElapsedMs;
    private volatile long lastStalePlaybackPositionIgnoredLogAt;
    private volatile long lastTransientLyricInfoMissLogAt;
    private volatile boolean lastSystemUiPackageSupported;
    private volatile String currentLyricProviderPackage = "";
    private volatile LyricInfoContract.Payload currentLyricProviderPayload;
    private volatile String currentLyricObservedTrackKey = "";
    private final SystemUiLyricPublicationGate systemUiLyricPublicationGate =
            new SystemUiLyricPublicationGate();
    private volatile String playbackClockTrackKey = "";
    private volatile boolean lastPlaybackIsPlaying = true;
    private volatile float lastPlaybackSpeed = 1f;
    private volatile int lastSystemUiPlaybackState = -1;
    private volatile int lastLoggedSystemUiPlaybackState = -100;
    private volatile String lastSystemUiSongName = "";
    private volatile String lastSystemUiArtistName = "";
    private volatile String lastSystemUiAlbumName = "";
    // SystemUI media id for the current track. Seedling exposes only title/artist, so this is
    // the strong identity used to reject a same-name cover captured for another track.
    private volatile String lastSystemUiMediaId = "";
    private volatile boolean systemUiHasOfficialLyric;
    private volatile SystemUiDexKitAdapter.Targets systemUiDexKitTargets;
    private final Object officialLyricsRecyclerCompatibilityLock = new Object();
    // Strong values are scoped to the active plugin generation and explicitly cleared when a
    // replacement ClassLoader arrives; this keeps the hot path fast without pinning old loaders.
    private final Map<Class<?>, OfficialLyricsRecyclerCompatibility.Binding>
            officialLyricsRecyclerBindings = new IdentityHashMap<>();
    private final Map<Class<?>, Boolean> unavailableOfficialLyricsRecyclerBindings =
            new IdentityHashMap<>();
    private volatile SystemUiLyricLoadContext latestSystemUiLyricLoadContext;
    private volatile Method systemUiMetadataRefreshMethod;
    private volatile boolean systemUiMetadataRefreshMethodUnavailableLogged;
    private final KuWoSystemUiRuntime kuWoRuntime = new KuWoSystemUiRuntime();
    private volatile boolean oplusPluginMediaModelHookInstalled;
    private volatile OplusPluginDexKitAdapter.Targets oplusPluginKuWoTargets;
    /** Immersive controller entry resolved in the same DexKit pass as the media-model targets. */
    private volatile Method resolvedLyricPositionController;
    private volatile boolean lyricPositionControllerHooked;
    private volatile boolean shortWindowDiagnosticsInstalled;
    private volatile long lastShortWindowLambdaProbeAt;
    private volatile long lastShortWindowModelProbeAt;
    // Monotonic guard for the vendor timed-lyric method; touched only on the SystemUI main thread.
    private WeakReference<View> timedLyricGuardRecycler = new WeakReference<>(null);
    private WeakReference<WordLyricModel> timedLyricGuardModel = new WeakReference<>(null);
    private long timedLyricGuardPosition = -1L;
    private long timedLyricGuardElapsedMs = -1L;
    private final OplusMediaPolicyHooks oplusMediaPolicyHooks = new OplusMediaPolicyHooks(
            new OplusMediaPolicyHooks.Host() {
                @Override
                public XposedInterface.HookBuilder hook(java.lang.reflect.Executable origin) {
                    return LockscreenLyricsModule.this.hook(origin);
                }

                @Override
                public void info(BridgeDebugArea area, String event, String message) {
                    LockscreenLyricsModule.this.info(area, event, message);
                }

                @Override
                public void infoAlways(BridgeDebugArea area, String event, String message) {
                    LockscreenLyricsModule.this.infoAlways(area, event, message);
                }

                @Override
                public void error(String message, Throwable throwable) {
                    LockscreenLyricsModule.this.error(message, throwable);
                }
            });
    private volatile boolean oplusMediaPolicyHooksInstalled;
    private volatile boolean translationToggleActionHookInstalled;
    private volatile boolean injectedTranslationToggleActionHookInstalled;
    private volatile boolean injectedTranslationToggleActionLogged;
    private volatile boolean injectedTranslationToggleActionFailureLogged;
    private String lastTranslationModelRebindKey = "";
    private final Set<String> providerDeclaredTranslationTogglePackages =
            ConcurrentHashMap.newKeySet();
    private final Map<String, WeakReference<Object>> latestTranslationMediaButtons =
            new ConcurrentHashMap<>();
    private final TranslationToggleMediaActionBinder translationToggleActionBinder =
            new TranslationToggleMediaActionBinder(new TranslationToggleMediaActionBinder.Host() {
                @Override
                public Context currentApplicationContext() {
                    return LockscreenLyricsModule.currentApplicationContext();
                }

                @Override
                public String logProcessName() {
                    return logProcessName;
                }

                @Override
                public boolean isLyricInfoTranslationEnabled(String packageName) {
                    return translationController.isLyricInfoTranslationEnabled(packageName);
                }

                @Override
                public void rememberTranslationIconFingerprint(Drawable drawable) {
                    translationTracker.rememberTranslationIconFingerprint(drawable);
                }

                @Override
                public void onTranslationToggleClicked(String packageName) {
                    LockscreenLyricsModule.this.toggleLyricInfoTranslation(packageName);
                }
            });
    private volatile boolean screenTimeoutReceiverRegistered;
    private volatile boolean systemUiLyricModeKeepAwakeActive;
    private volatile boolean lyricUiScrollScaleEnabled;
    private volatile boolean lyricUiInactiveBlurEnabled;
    private volatile boolean lyricUiLineTimedProgressEnabled =
            LyricUiSettings.DEFAULT_LINE_TIMED_PROGRESS_ENABLED;
    private volatile boolean lyricUiTranslationProgressEnabled =
            LyricUiSettings.DEFAULT_TRANSLATION_PROGRESS_ENABLED;
    private volatile LyricUiConfig lyricUiConfig = LyricUiConfig.defaults();
    private volatile LyricContentCleanupConfig lyricContentCleanupConfig =
            LyricContentCleanupConfig.defaults();
    private volatile String currentCleanupSnapshotTrackKey = "";
    private volatile String currentCleanupSnapshotRawLyric = "";
    private volatile String currentCleanupSnapshotDisplayLyric = "";
    private volatile String currentCleanupSnapshotTitle = "";
    private volatile String currentCleanupSnapshotArtist = "";
    private volatile long lyricUiStyleSettingsLoadedAtElapsedMs = -1L;
    private boolean bridgeDebugConfigAnnounced;
    private volatile int lyricModeRebindGeneration;
    private Runnable lyricModeRebindRunnable;
    private volatile int translationLayoutGeneration;
    private volatile long officialLyricDrawSuppressedUntilElapsedMs;
    private volatile long officialLyricHandoffStartedAtElapsedMs;
    private volatile int officialLyricHandoffReleaseRetryGeneration = -1;
    private volatile int officialLyricHandoffGeneration;
    private volatile int lyricRecyclerForceAlignGeneration;
    private volatile long lastTransientLyricSurfaceMissLogAt;
    private volatile boolean lyricModelReplacementInProgress;
    private final Object brightLyricGeometryCommitLock = new Object();
    private volatile WordLyricModel pendingBrightLyricGeometryModel;
    private volatile int brightLyricGeometryCommitGeneration;
    private volatile boolean pendingCustomLyricTakeoverFade;
    private volatile boolean lyricUiSettingsReceiverRegistered;
    private BroadcastReceiver lyricUiSettingsReceiver;
    private volatile boolean universalBindingsReceiverRegistered;
    private BroadcastReceiver universalBindingsReceiver;
    private volatile long lyricTrackRowRebindEligibleUntilElapsedMs;
    private volatile long lyricRecyclerFadeInUntilElapsedMs;
    private volatile int lyricRecyclerFadeGeneration;
    private final Object suppressedLyricsRecyclerAlphasLock = new Object();
    private final WeakHashMap<View, Float> suppressedLyricsRecyclerAlphas =
            new WeakHashMap<>();
    private volatile long lastSystemUiLyricModeLogAt;
    private volatile long lastSystemUiLyricModeStateLogAt;
    private volatile boolean aodLowFrameRateLyricMode;
    private BroadcastReceiver screenTimeoutReceiver;
    private volatile boolean screenTurnedOffForLockState;
    private volatile int lastLyricsRecyclerIndex = -1;
    private volatile boolean adaptiveLineTimedRecyclerFollowActive;
    private volatile int lastAdaptiveLineTimedRecyclerAlignedIndex = -1;
    private volatile int adaptiveLineTimedLagCandidateIndex = -1;
    private volatile long adaptiveLineTimedLagObservedAtElapsedMs = -1L;
    private volatile int lyricRecyclerSettleOfficialIndex = -1;
    private volatile long lyricRecyclerSettleUntilElapsedMs;
    private volatile long lyricTrackPositionResetGuardStartedAtElapsedMs = -1L;
    private volatile long lastOfficialLyricIndexObservedAtElapsedMs = -1L;
    private volatile long officialRowScaleAnimationSuppressUntilElapsedMs;
    private volatile boolean lyricsRecyclerHookInstallAttempted;
    private volatile boolean pluginClassLoaderConstructorHookInstalled;
    private final Object pluginHookGenerationLock = new Object();
    private WeakReference<ClassLoader> activePluginClassLoader = new WeakReference<>(null);
    // Last plugin loader whose early hooks ran; the constructor and factory entries may both fire.
    private WeakReference<ClassLoader> readyPluginClassLoader = new WeakReference<>(null);
    private final ArrayList<XposedInterface.HookHandle> activePluginHookHandles =
            new ArrayList<>();
    private volatile boolean lyricsRecyclerSetCurrentUnavailable;
    private volatile boolean recyclerAdapterNotifyHookInstalled;
    private volatile boolean recyclerAdapterNotifyGuardUnavailableLogged;
    private final Object lyricsRecyclerViewsLock = new Object();
    private final ArrayList<WeakReference<View>> lyricsRecyclerViews = new ArrayList<>();
    private final Object lyricSurfaceRenderEpochsLock = new Object();
    private final WeakHashMap<View, Integer> lyricSurfaceRenderEpochs = new WeakHashMap<>();
    private final WeakHashMap<View, LyricSurfaceReadinessState> lyricSurfaceReadinessStates =
            new WeakHashMap<>();
    private final WeakHashMap<View, Integer> lyricSurfacePendingDrawGenerations =
            new WeakHashMap<>();
    private final WeakHashMap<View, Integer> lyricSurfaceVisibleSessionGenerations =
            new WeakHashMap<>();
    private final Object lyricsRecyclerAdaptersLock = new Object();
    private final ArrayList<WeakReference<Object>> lyricsRecyclerAdapters = new ArrayList<>();
    private WeakReference<View> lastPrimedLyricsRecyclerView = new WeakReference<>(null);
    private int lastPrimedLyricsRecyclerIndex = -1;
    private final Object activeLyricTextViewsLock = new Object();
    private final ArrayList<WeakReference<TextView>> activeLyricTextViews = new ArrayList<>();
    private final ArrayList<TextView> activeLyricRefreshCandidates = new ArrayList<>(8);
    private final WeakHashMap<TextView, NormalizedTextSnapshot> normalizedLyricTextCache =
            new WeakHashMap<>();
    private final WeakHashMap<TextView, CachedDrawFrame> recentOfficialDrawFrames =
            new WeakHashMap<>();
    private final Object lyricTextBindEpochsLock = new Object();
    private final WeakHashMap<TextView, Integer> lyricTextBindEpochs = new WeakHashMap<>();
    private final WeakHashMap<TextView, Integer> lyricTextBindRetryPostedEpochs =
            new WeakHashMap<>();
    private final WeakHashMap<TextView, Integer> lyricTextBindRetryCounts =
            new WeakHashMap<>();
    private final WeakHashMap<TextView, BoundLyricFrameStamp> boundLyricFrameStamps =
            new WeakHashMap<>();
    private int nextLyricTextBindEpoch;
    private WeakReference<TextView> activeRendererTextView = new WeakReference<>(null);
    private final Object activeRendererTargetsLock = new Object();
    private final WeakHashMap<View, WeakReference<TextView>> activeRendererTargetsByRecycler =
            new WeakHashMap<>();
    private WordLine activeRendererWordLine;
    private final Object lyricRootViewsLock = new Object();
    private final ArrayList<WeakReference<View>> lyricRootViews = new ArrayList<>();
    private volatile boolean translationActionViewRecoveryPosted;
    private static final Object VIEW_VISUAL_EFFECT_CACHE_LOCK = new Object();
    private static final WeakHashMap<Class<?>, WeakReference<Method[]>>
            VIEW_BLUR_METHOD_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> VIEW_BLUR_DISABLED = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> VIEW_LYRIC_RENDER_EFFECT_MODE =
            new WeakHashMap<>();
    private static final Object RECYCLER_POSITION_METHOD_CACHE_LOCK = new Object();
    private static final WeakHashMap<Class<?>, WeakReference<Method>>
            RECYCLER_POSITION_METHOD_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Boolean> RECYCLER_POSITION_METHOD_MISSING =
            new WeakHashMap<>();
    /** getAdapter lives on RecyclerView; a per-call declared-method walk throws once per subclass level. */
    private static final WeakHashMap<Class<?>, WeakReference<Method>>
            RECYCLER_ADAPTER_METHOD_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Boolean> LYRICS_RECYCLER_VIEW_CLASS_MATCH_CACHE =
            new WeakHashMap<>();
    private static final WeakHashMap<TextView, LyricsRecyclerMatch> LYRICS_RECYCLER_MATCH_CACHE =
            new WeakHashMap<>();
    private static final LyricsRecyclerFieldAccessor LYRICS_RECYCLER_FIELD_ACCESSOR =
            new LyricsRecyclerFieldAccessor();
    // ColorOS 17 has no readable index field (n). Its official row field is resolved by DexKit
    // semantics on the first attach (immersiveCurrentIndexField); until then, or when it cannot be
    // resolved, the row each recycler was last sent through its immersive entry stands in for it.
    // Weak keys: a detached recycler never outlives SystemUI.
    private static final WeakHashMap<Object, ImmersiveEntryIndex> IMMERSIVE_ENTRY_INDEXES =
            new WeakHashMap<>();
    // The vendor applies a new list only after its switch fade and an asynchronous diff, so the
    // row sent through the entry can lead the displayed one; this field is the displayed row.
    private static volatile Field immersiveCurrentIndexField;
    private static final LyricsRecyclerMatch NO_LYRICS_RECYCLER_MATCH =
            new LyricsRecyclerMatch(null, null);
    private static final ThreadLocal<Rect> VIEW_VISIBLE_RECT =
            ThreadLocal.withInitial(Rect::new);
    private volatile String activeLyricLine = "";
    private volatile long activeLyricLineTimeMs = -1L;
    private volatile boolean activeLyricUpdatePosted;
    private boolean activeLyricRefreshCadenceScheduled;
    private WordLine activeLyricRefreshCadenceLine;
    private int activeLyricRefreshCadenceHz = -1;
    private long activeLyricRefreshNextDeadlineNanos =
            LyricRefreshRatePolicy.UNSET_DEADLINE_NANOS;
    private volatile boolean lyricSurfaceReactivationPending;
    private volatile int lyricSurfaceReactivationGeneration;
    private final Object lyricSurfaceReactivationCallbacksLock = new Object();
    private final ArrayList<Runnable> lyricSurfaceReactivationCallbacks =
            new ArrayList<>(LYRIC_VISIBILITY_RECOVERY_DELAYS_MS.length);
    private volatile long lyricSurfaceProvisionalDrawUntilElapsedMs;
    private WeakReference<View> lyricSurfaceProvisionalRecyclerView = new WeakReference<>(null);
    private View activeLyricRefreshAnchor;
    private volatile long lastRecyclerAdapterNotifyGuardLogAt;
    private final ThreadLocal<Boolean> suppressLyricsRecyclerHook = new ThreadLocal<>();
    private final OfficialLyricTextRenderer officialLyricTextRenderer = new OfficialLyricTextRenderer();
    private final LyricRenderTransactions<WordLyricModel> lyricRenderTransactions =
            new LyricRenderTransactions<>();
    private final WeakHashMap<Class<?>, Boolean> lyricAdapterBindHookClasses = new WeakHashMap<>();
    private volatile boolean lyricDatasetHookAvailable;
    private final OfficialLyricFrameResolver officialLyricFrameResolver =
            new OfficialLyricFrameResolver();
    private final OfficialLyricFrameResolver.Selection officialFrameSelection =
            new OfficialLyricFrameResolver.Selection();
    private final OfficialLyricFrameResolver.Selection lyricTextMatchSelection =
            new OfficialLyricFrameResolver.Selection();
    private final BridgePerformanceSampler performanceSampler = new BridgePerformanceSampler();
    private final NativeLyricModelAssembler nativeLyricModelAssembler =
            new NativeLyricModelAssembler(new LyricParseTraceSink() {
                @Override
                public boolean traceEnabled() {
                    return isLyricParseTraceEnabled();
                }

                @Override
                public void trace(String message) {
                    traceLyricParse(message);
                }

                @Override
                public void onCoreParseFailure(boolean primarySource, Throwable failure) {
                    error(primarySource
                            ? "Lyrics Core parsing failed; using official SystemUI lyric renderer"
                            : "Lyrics Core parsing failed for supplemental lyric; ignoring supplemental translations",
                            failure);
                }
            });
    private final OfficialLyricDrawCoordinator officialLyricDrawCoordinator =
            new OfficialLyricDrawCoordinator(new OfficialLyricDrawCoordinator.Host() {
                @Override
                public boolean shouldInspectLyricTextViewHooks() {
                    return lyricRenderTransactions.currentModel() != null
                            || LockscreenLyricsModule.this.shouldSuppressOfficialLyricForTrackHandoff()
                            || LockscreenLyricsModule.this.shouldInspectLyricTextViewHooks();
                }

                @Override
                public boolean lyricSurfaceReactivationPending() {
                    return lyricSurfaceReactivationPending;
                }

                @Override
                public long lyricRecyclerFadeInUntilElapsedMs() {
                    return lyricRecyclerFadeInUntilElapsedMs;
                }

                @Override
                public WordLyricModel currentWordLyricModel() {
                    return lyricRenderTransactions.currentModel();
                }

                @Override
                public WordLyricModel pendingBrightLyricGeometryModel() {
                    return pendingBrightLyricGeometryModel;
                }

                @Override
                public boolean lyricModelReplacementInProgress() {
                    return lyricModelReplacementInProgress;
                }

                @Override
                public boolean isAodLowFrameRateLyricMode() {
                    return LockscreenLyricsModule.this.isAodLowFrameRateLyricMode();
                }

                @Override
                public boolean isLyricRecyclerSettleWindowActive() {
                    return LockscreenLyricsModule.this.isLyricRecyclerSettleWindowActive();
                }

                @Override
                public boolean shouldSuppressOfficialLyricForTrackHandoff() {
                    return LockscreenLyricsModule.this.shouldSuppressOfficialLyricForTrackHandoff();
                }

                @Override
                public LyricRenderTransactions.Status lyricDrawBindingStatus(
                        TextView textView, View recycler, WordLyricModel model) {
                    return lyricDatasetHookAvailable
                            ? lyricRenderTransactions.rowStatus(recycler, textView, model)
                            : LyricRenderTransactions.Status.UNSUPPORTED;
                }

                @Override
                public View findContainingLyricsRecyclerView(TextView textView) {
                    return LockscreenLyricsModule.this.findContainingLyricsRecyclerView(textView);
                }

                @Override
                public boolean activateSystemUiLyricModeFromPendingDraw(
                        TextView textView, View lyricsRecycler, String reason) {
                    return LockscreenLyricsModule.this.activateSystemUiLyricModeFromPendingDraw(
                            textView, lyricsRecycler, reason);
                }

                @Override
                public void officialRendererFallback(
                        String reason, TextView textView, WordLyricModel model, String details) {
                    maybeLogOfficialRendererFallback(reason, textView, model, details);
                }

                @Override
                public void noteVisibleLockscreenLyricTextView(
                        TextView textView, View lyricsRecycler, long elapsedRealtime) {
                    LockscreenLyricsModule.this.noteVisibleLockscreenLyricTextView(
                            textView, lyricsRecycler, elapsedRealtime);
                }

                @Override
                public void refreshLyricUiStyleSettingsIfNeeded() {
                    LockscreenLyricsModule.this.refreshLyricUiStyleSettingsIfNeeded();
                }

                @Override
                public void setRendererAodLowFrameRateMode(boolean enabled) {
                    diagnosticAod = Boolean.toString(enabled);
                    officialLyricTextRenderer.setAodLowFrameRateMode(enabled);
                }

                @Override
                public void setRendererForceOfficialSlotHeight(boolean force) {
                    officialLyricTextRenderer.setForceOfficialSlotHeight(force);
                }

                @Override
                public void drawWithOfficialRenderer(Canvas canvas, TextView textView, DrawFrame frame) {
                    long rendererStartedAt = performanceSampler.begin();
                    try {
                        boolean coverTraced = BridgePerfTrace.begin("CLL:lyricCoverGlowColor");
                        try {
                            Integer color = dynamicArtworkRuntime.coverColor(textView);
                            officialLyricTextRenderer.setCoverGlowColor(color);
                            CompatibilityStatus.global().palette(
                                    textView, lyricUiConfig.glowFollowsCover, color != null);
                        } finally {
                            BridgePerfTrace.end(coverTraced);
                        }
                        officialLyricTextRenderer.draw(canvas, textView, frame);
                    } finally {
                        performanceSampler.end(
                                BridgePerformanceSampler.Metric.RENDERER_DRAW,
                                rendererStartedAt);
                    }
                }

                @Override
                public DrawFrame findOfficialLyricDrawFrame(TextView textView, WordLyricModel model) {
                    return LockscreenLyricsModule.this.findOfficialLyricDrawFrame(textView, model);
                }

                @Override
                public DrawFrame findRecentOfficialDrawFrame(TextView textView, WordLyricModel model) {
                    return LockscreenLyricsModule.this.findRecentOfficialDrawFrame(textView, model);
                }

                @Override
                public void rememberRecentOfficialDrawFrame(TextView textView, DrawFrame frame) {
                    LockscreenLyricsModule.this.rememberRecentOfficialDrawFrame(textView, frame);
                }

                @Override
                public void forgetRecentOfficialDrawFrame(TextView textView) {
                    recentOfficialDrawFrames.remove(textView);
                }

                @Override
                public void markBoundLyricFrameReady(TextView textView, DrawFrame frame) {
                    LockscreenLyricsModule.this.markBoundLyricFrameReady(textView, frame);
                }

                @Override
                public void scheduleBoundLyricFrameRetry(TextView textView, WordLyricModel model) {
                    LockscreenLyricsModule.this.scheduleBoundLyricFrameRetry(textView, model);
                }

                @Override
                public String normalizedTextOf(TextView textView) {
                    return LockscreenLyricsModule.this.normalizedTextOf(textView);
                }

                @Override
                public void followAdaptiveNativeLineTimedRecycler(
                        WordLyricModel model, WordLine line, TextView textView) {
                    LockscreenLyricsModule.this.followAdaptiveNativeLineTimedRecycler(
                            model, line, textView);
                }

                @Override
                public void logTextViewDraw(DrawFrame frame, TextView textView) {
                    maybeLogTextViewDraw(frame, textView);
                }

                @Override
                public void finishOfficialLyricTrackHandoffAfterStableCustomFrame(TextView textView) {
                    LockscreenLyricsModule.this
                            .finishOfficialLyricTrackHandoffAfterStableCustomFrame(textView);
                }

                @Override
                public void fadeInLateCustomLyricTakeover(TextView textView) {
                    LockscreenLyricsModule.this.fadeInLateCustomLyricTakeover(textView);
                }

                @Override
                public void onCustomDrawError(TextView textView, WordLyricModel model, Throwable failure) {
                    error("Failed to custom-draw official lyric TextView"
                            + " | alignment=" + lyricUiConfig.alignment
                            + ", view=" + describeViewForLog(textView), failure);
                }
            });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScreenTimeoutController screenTimeout = new ScreenTimeoutController(
            new ScreenTimeoutController.Host() {
                @Override
                public boolean isSystemUiLyricModeKeepAwakeActive() {
                    return systemUiLyricModeKeepAwakeActive;
                }

                @Override
                public boolean isLastPlaybackPlaying() {
                    return lastPlaybackIsPlaying;
                }

                @Override
                public boolean hasCurrentWordLyricModel() {
                    return currentWordLyricModel != null;
                }

                @Override
                public boolean hasSystemUiOfficialLyric() {
                    return systemUiHasOfficialLyric;
                }

                @Override
                public boolean hasSupportedSystemUiPlayer() {
                    return LockscreenLyricsModule.this.hasSupportedSystemUiPlayer();
                }

                @Override
                public void deactivateSystemUiLyricModeAfterSurfaceHidden() {
                    LockscreenLyricsModule.this.deactivateSystemUiLyricModeAfterSurfaceHidden();
                }

                @Override
                public void ensureScreenTimeoutReceiver(Context context) {
                    LockscreenLyricsModule.this.ensureScreenTimeoutReceiver(context);
                }

                @Override
                public void info(BridgeDebugArea area, String event, String message) {
                    LockscreenLyricsModule.this.info(area, event, message);
                }

                @Override
                public void error(String message, Throwable throwable) {
                    LockscreenLyricsModule.this.error(message, throwable);
                }
            },
            mainHandler);
    private final TranslationActionViewTracker translationTracker = new TranslationActionViewTracker(
            new TranslationActionViewTracker.Host() {
                @Override
                public boolean shouldManageTranslationActionViewVisibility() {
                    return LockscreenLyricsModule.this.shouldManageTranslationActionViewVisibility();
                }

                @Override
                public String describeTranslationButtonState() {
                    return LockscreenLyricsModule.this.describeTranslationButtonState();
                }

                @Override
                public WordLyricModel currentWordLyricModel() {
                    return currentWordLyricModel;
                }

                @Override
                public String currentTranslationPreferencePackage() {
                    return LockscreenLyricsModule.this.currentTranslationPreferencePackage();
                }

                @Override
                public boolean userWantsTranslationButton(String packageName) {
                    return translationController.userWantsTranslationButton(packageName);
                }

                @Override
                public boolean isLyricInfoTranslationEnabled(String packageName) {
                    return translationController.isLyricInfoTranslationEnabled(packageName);
                }

                @Override
                public boolean isNearRememberedLyricsRecyclerView(View candidate) {
                    return LockscreenLyricsModule.this.isNearRememberedLyricsRecyclerView(candidate);
                }

                @Override
                public void translationButtonDebug(String message) {
                    LockscreenLyricsModule.this.translationButtonDebug(message);
                }
            },
            translationToggleActionBinder,
            mainHandler);
    private final TranslationActionController translationController = new TranslationActionController(
            new TranslationActionController.Host() {
                @Override
                public boolean isDeviceKeyguardLocked(Context context) {
                    return LockscreenLyricsModule.this.isDeviceKeyguardLocked(context);
                }

                @Override
                public String currentTranslationPreferencePackage() {
                    return LockscreenLyricsModule.this.currentTranslationPreferencePackage();
                }

                @Override
                public boolean defaultTranslationEnabled() {
                    return lyricUiConfig.defaultTranslationEnabled;
                }

                @Override
                public void translationButtonDebug(String message) {
                    LockscreenLyricsModule.this.translationButtonDebug(message);
                }

                @Override
                public void info(BridgeDebugArea area, String event, String message) {
                    LockscreenLyricsModule.this.info(area, event, message);
                }

                @Override
                public void error(String message, Throwable throwable) {
                    LockscreenLyricsModule.this.error(message, throwable);
                }
            },
            officialLyricTextRenderer);
    private final Object systemUiPlaybackControllerLock = new Object();
    private PlaybackControllerBinding systemUiPlaybackControllerBinding;
    private long nextSystemUiPlaybackControllerEpoch;
    private volatile long lastPlaybackControllerDiscoveryElapsedMs = -1L;
    private volatile String lastPlaybackControllerDiscoveryPackage = "";
    private WeakReference<Object> oplusMediaDataManager = new WeakReference<>(null);
    private volatile Method oplusPlaybackStateRefreshMethod;
    private final DynamicArtworkRuntime dynamicArtworkRuntime = new DynamicArtworkRuntime(
            (target, id, after) -> {
                XposedInterface.HookHandle handle = hook(target)
                        .setId(id)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object receiver = chain.getThisObject();
                            Object[] arguments = chain.getArgs().toArray();
                            try { after.before(receiver, arguments); }
                            catch (Throwable ignored) { ArtworkTrace.observerFailure(id, ignored); }
                            Object result = chain.proceed();
                            try {
                                after.observe(receiver, arguments, result);
                            } catch (Throwable ignored) {
                                // Artwork observation always preserves the original call's result.
                                ArtworkTrace.observerFailure(id, ignored);
                            }
                            return result;
                        });
                if (handle == null) throw new IllegalStateException("artwork_hook_unavailable");
                return handle::unhook;
            }, mainHandler, LockscreenLyricsModule::currentApplicationContext,
            LyricUiSettings.CHANGE_SETTINGS_PERMISSION);
    private final SystemUiRuntimeBootstrap systemUiRuntimeBootstrap =
            new SystemUiRuntimeBootstrap(
                    new SystemUiRuntimeBootstrap.Host() {
                        @Override
                        public void installAodMediaSupportHooks(ClassLoader classLoader) {
                            oplusMediaPolicyHooks.installAodMediaSupportHooks(classLoader);
                        }

                        @Override
                        public SystemUiDexKitAdapter.Targets resolveSystemUiTargets(
                                ClassLoader classLoader) {
                            return LockscreenLyricsModule.this.resolveSystemUiTargets(classLoader);
                        }

                        @Override
                        public void installOplusMediaPolicyBypassHooks(
                                SystemUiDexKitAdapter.Targets targets) {
                            LockscreenLyricsModule.this.installOplusMediaPolicyBypassHooks(targets);
                        }

                        @Override
                        public void installSystemUiWordLyricHooks(
                                ClassLoader classLoader,
                                SystemUiDexKitAdapter.Targets targets) {
                            LockscreenLyricsModule.this.installSystemUiWordLyricHooks(
                                    classLoader,
                                    targets);
                        }

                        @Override
                        public void installSystemUiTranslationToggleActionHook(
                                SystemUiDexKitAdapter.Targets targets) {
                            LockscreenLyricsModule.this.installSystemUiTranslationToggleActionHook(
                                    targets);
                        }

                        @Override
                        public Context currentApplicationContext() {
                            return LockscreenLyricsModule.this.currentApplicationContext();
                        }

                        @Override
                        public void ensureLyricUiSettingsReceiver(Context context) {
                            LockscreenLyricsModule.this.ensureLyricUiSettingsReceiver(context);
                        }

                        @Override
                        public void loadLyricUiStyleSettings(Context context, boolean force) {
                            LockscreenLyricsModule.this.loadLyricUiStyleSettings(context, force);
                        }

                        @Override
                        public void post(Runnable runnable) {
                            mainHandler.post(runnable);
                        }

                        @Override
                        public void postDelayed(Runnable runnable, long delayMillis) {
                            mainHandler.postDelayed(runnable, delayMillis);
                        }

                        @Override
                        public void infoAlways(BridgeDebugArea area, String event, String message) {
                            LockscreenLyricsModule.this.infoAlways(area, event, message);
                        }
                    },
                    LYRIC_RENDER_PIPELINE_REVISION,
                    OFFICIAL_DRAW_FRAME_REUSE_ENABLED,
                    WordLyricRenderConstants.OFFICIAL_SLOT_ALIAS_REUSE_ENABLED);
    private final Runnable activeLyricRefreshRunnable = () -> {
        View scheduledAnchor = activeLyricRefreshAnchor;
        boolean useDisplayCadence = activeLyricRefreshCadenceScheduled;
        WordLine cadenceLine = activeLyricRefreshCadenceLine;
        activeLyricUpdatePosted = false;
        activeLyricRefreshAnchor = null;
        activeLyricRefreshCadenceScheduled = false;
        activeLyricRefreshCadenceLine = null;
        if (useDisplayCadence && !consumeActiveLyricRefreshFrame()) {
            if (scheduledAnchor instanceof TextView
                    && scheduledAnchor.isAttachedToWindow()) {
                scheduleActiveLyricRefresh(
                        (TextView) scheduledAnchor,
                        ACTIVE_LYRIC_FRAME_DELAY_MS,
                        cadenceLine);
            } else {
                scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS, cadenceLine);
            }
            return;
        }
        refreshActiveLyricTextView();
    };

    private final String diagnosticProcessEpoch = java.util.UUID.randomUUID().toString();
    private volatile String diagnosticFramework = "unknown";
    private volatile int diagnosticApi = -1;
    private volatile long diagnosticFrameworkProperties = -1L;
    private volatile long diagnosticUiRevision = -1L;
    private volatile String diagnosticAod = "unknown";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        try {
            diagnosticFramework = getFrameworkName() + " / " + getFrameworkVersion()
                    + " (" + getFrameworkVersionCode() + ")";
            diagnosticApi = getApiVersion();
            diagnosticFrameworkProperties = getFrameworkProperties();
        } catch (RuntimeException | LinkageError ignored) {
            // Observation failure must not change module/bootstrap behavior.
        }
        String processName = param.getProcessName();
        if (param.isSystemServer()) {
            logProcessName = "system_server";
            configureBridgeDiagnostics(BridgeDebugConfig.disabled());
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SYSTEMUI_BOOTSTRAP, "Loaded in system_server, API " + getApiVersion());
            return;
        }
        logProcessName = nullToEmpty(processName);
        configureBridgeDiagnostics(BridgeDebugConfig.disabled());
        if (processName == null || !isSupportedProcess(processName)) {
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SYSTEMUI_BOOTSTRAP, "Skip process " + processName);
            detach();
            return;
        }
        infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SYSTEMUI_BOOTSTRAP, "Loaded in " + processName + ", API " + getApiVersion());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        oplusMediaPolicyHooks.installOplusHistoryWhitelistHook(param.getClassLoader());
        oplusMediaPolicyHooks.installOplusMediaBlacklistBypassHook(param.getClassLoader());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (SYSTEMUI_PACKAGE.equals(packageName)) {
            io.github.andrealtb.lockscreenlyrics.diagnostics.LoadedComponentFingerprints.global().observed("systemui", param.getClassLoader());
            dynamicArtworkRuntime.initializeSystemUi(param.getClassLoader());
            tryInstallPluginClassLoaderConstructorHook(param.getClassLoader());
            systemUiRuntimeBootstrap.initialize(param.getClassLoader());
        }
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
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SYSTEMUI_BOOTSTRAP, "Resolved SystemUI private hooks via DexKit (rusVariant=" + cached.rusVariant
                        + ", whitelistGetter=" + whitelistGetterLabel(cached) + ")");
                return cached;
            } catch (Throwable dexKitFailure) {
                error("Failed to resolve SystemUI private hooks via DexKit; trying legacy names",
                        dexKitFailure);
            }
            try {
                cached = SystemUiDexKitAdapter.resolveLegacy(classLoader);
                systemUiDexKitTargets = cached;
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SYSTEMUI_BOOTSTRAP, "Resolved SystemUI private hooks via legacy-name fallback (rusVariant="
                        + cached.rusVariant + ", whitelistGetter=" + whitelistGetterLabel(cached)
                        + ")");
                return cached;
            } catch (Throwable fallbackFailure) {
                error("Failed to resolve SystemUI private hook targets", fallbackFailure);
                return null;
            }
        }
    }

    private static String whitelistGetterLabel(SystemUiDexKitAdapter.Targets targets) {
        if (targets.getRusWhiteList != null) {
            return "getRusWhiteList";
        }
        if (targets.mediaRusConfigWhiteListGetter != null) {
            return "MediaRusConfig.getWhiteList";
        }
        return "none";
    }

    private static boolean isSupportedProcess(String processName) {
        return LockscreenIntegrationPolicy.isSupportedSystemUiProcess(processName);
    }

    static boolean isModuleManagedPlayerPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        return PlayerSystemUiPolicy.isHistoryPackage(packageName);
    }

    static String moduleManagedPlayerKind(String packageName) {
        return isModuleManagedPlayerPackage(packageName) ? "player" : "manifest";
    }

    private boolean isCurrentLyricProviderPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        if (!TextUtils.isEmpty(currentLyricProviderPackage)) {
            return packageName.equals(currentLyricProviderPackage);
        }
        return isModuleManagedPlayerPackage(packageName);
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
                Method getRusWhiteList = targets.getRusWhiteList;
                if (getRusWhiteList != null) {
                    getRusWhiteList.setAccessible(true);
                    hook(getRusWhiteList)
                            .setId(HOOK_ID_RUS_GET_WHITE_LIST)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(oplusMediaPolicyHooks::onOplusMediaRusGetWhiteList);
                } else {
                    Method mediaRusConfigWhiteListGetter = targets.mediaRusConfigWhiteListGetter;
                    if (mediaRusConfigWhiteListGetter != null) {
                        mediaRusConfigWhiteListGetter.setAccessible(true);
                        hook(mediaRusConfigWhiteListGetter)
                                .setId(HOOK_ID_RUS_GET_WHITE_LIST)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(oplusMediaPolicyHooks::onOplusMediaRusGetWhiteList);
                    } else {
                        warn(
                                LyricLogFormatter.Area.SYSTEM_UI,
                                "bypass",
                                "Skipped OPlus media whitelist bypass: no whitelist getter "
                                        + "available on this build");
                    }
                }

                Method getLyricEntrance = targets.getLyricEntrance;
                getLyricEntrance.setAccessible(true);
                hook(getLyricEntrance)
                        .setId(HOOK_ID_GET_LYRIC_ENTRANCE)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(oplusMediaPolicyHooks::onOplusMediaGetLyricEntrance);

                Method resolveLyricSupport = targets.resolveLyricSupport;
                if (resolveLyricSupport != null) {
                    hook(resolveLyricSupport)
                            .setId(HOOK_ID_RESOLVE_LYRIC_SUPPORT)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(oplusMediaPolicyHooks::onOplusMediaResolveLyricSupport);
                }

                Method updatePkgActionsRule = targets.updatePkgActionsRule;
                updatePkgActionsRule.setAccessible(true);
                hook(updatePkgActionsRule)
                        .setId(HOOK_ID_UPDATE_PKG_ACTIONS_RULE)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(translationController::onOplusMediaUpdatePkgActionsRule);
                translationController.oplusUpdatePkgActionsRuleMethod = updatePkgActionsRule;

                oplusMediaPolicyHooksInstalled = true;
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus media policy bypass"
                        + ", lyricSupportGate=" + (resolveLyricSupport != null)
                        + ", whitelistGetter=" + whitelistGetterLabel(targets));
            } catch (Throwable t) {
                error("Failed to hook OPlus media policy bypass", t);
            }
        }
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
                translationController.ensureTranslationPreferenceLoaded();
                Method createActionsFromState = targets.createActionsFromState;
                createActionsFromState.setAccessible(true);
                hook(createActionsFromState)
                        .setId(HOOK_ID_TRANSLATION_TOGGLE_ACTION)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaCreateActionsFromState);
                translationToggleActionHookInstalled = true;
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus media translation toggle action");
            } catch (Throwable t) {
                error("Failed to hook OPlus media translation toggle action", t);
            }
        }
    }

    private Object onOplusMediaCreateActionsFromState(XposedInterface.Chain chain) throws Throwable {
        Object packageNameArg = chain.getArg(0);
        Object controllerArg = chain.getArg(2);
        String packageName = packageNameArg instanceof String ? (String) packageNameArg : "";
        Context lockContext = currentApplicationContext();
        boolean keyguardLocked = isDeviceKeyguardLocked(lockContext);
        boolean isCurrentProvider = isCurrentLyricProviderPackage(packageName);
        boolean hasTranslationAction = keyguardLocked
                && isCurrentProvider
                && !TextUtils.isEmpty(packageName)
                && controllerHasTranslationAction(controllerArg);
        boolean canOverrideWithTranslation = keyguardLocked
                && isCurrentProvider
                && PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                        packageName,
                        providerDeclaredTranslationTogglePackages);
        translationButtonDebug("createActionsFromState package=" + nullToEmpty(packageName)
                + ", keyguardLocked=" + keyguardLocked
                + ", hasPublicAction=" + hasTranslationAction
                + ", canOverride=" + canOverrideWithTranslation
                + ", controller=" + (controllerArg == null
                ? "null"
                : controllerArg.getClass().getName()));
        if (hasTranslationAction || canOverrideWithTranslation) {
            if (PlayerSystemUiPolicy.preservesNativeActionRow(packageName)) {
                translationController.dropTranslationToggleRule0(packageName);
                translationButtonDebug("Rule0 ensure skipped: player keeps its native action row"
                        + ", package=" + nullToEmpty(packageName));
            } else {
                translationController.ensureTranslationToggleRule0(packageName);
            }
        } else {
            translationController.dropTranslationToggleRule0(packageName);
        }

        Object result = chain.proceed();
        try {
            if (!TextUtils.isEmpty(packageName)) {
                rememberMediaControllerPlaybackState(
                        packageName,
                        controllerArg);
                if (result != null) {
                    latestTranslationMediaButtons.put(
                            packageName,
                            new WeakReference<>(result));
                    translationButtonDebug("createActionsFromState result for "
                            + packageName
                            + ", class=" + result.getClass().getName());
                    boolean userWantsButton = keyguardLocked && translationController.userWantsTranslationButton(packageName);
                    translationToggleActionBinder.applyTranslationToggle(
                            packageName,
                            result,
                            PlayerTranslationTogglePolicy
                                    .shouldReplaceFavoriteActionWithTranslation(
                                            canOverrideWithTranslation,
                                            isCurrentLyricProviderPackage(packageName),
                                            currentWordLyricModel == null
                                                    ? -1
                                                    : currentWordLyricModel.translationCount(),
                                            currentLyricProviderPayload == null
                                                    ? null
                                                    : currentLyricProviderPayload.translationLyric)
                                    && userWantsButton,
                            userWantsButton,
                            currentLyricProviderPackage,
                            currentWordLyricModel == null
                                    ? -1
                                    : currentWordLyricModel.translationCount(),
                            currentLyricProviderPayload == null
                                    || currentLyricProviderPayload.translationLyric == null
                                    ? -1
                                    : currentLyricProviderPayload.translationLyric.length());
                } else {
                    translationButtonDebug("createActionsFromState result null for "
                            + packageName);
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

    private void toggleLyricInfoTranslation(String packageName) {
        setLyricInfoTranslationEnabled(packageName, !translationController.isLyricInfoTranslationEnabled(packageName));
    }

    private void setLyricInfoTranslationEnabled(String packageName, boolean enabled) {
        String preferencePackage = normalizeTranslationPreferencePackage(packageName);
        translationController.ensureTranslationPreferenceLoaded(preferencePackage);
        if (translationController.isLyricInfoTranslationEnabledFromCache(preferencePackage) == enabled) {
            return;
        }

        translationController.lyricInfoTranslationEnabledByPackage.put(preferencePackage, enabled);
        if (translationController.shouldApplyTranslationPreferenceToRenderer(preferencePackage)) {
            officialLyricTextRenderer.setTranslationEnabled(enabled);
        }
        Context context = currentApplicationContext();
        if (context != null) {
            context.getSharedPreferences(TRANSLATION_PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(translationPreferenceKeyForPackage(preferencePackage), enabled)
                    .apply();
        }

        int generation = ++translationLayoutGeneration;
        mainHandler.post(() -> refreshLyricViewsAfterTranslationToggle(generation));
        mainHandler.post(() -> {
            discoverTranslationActionViewsNearRememberedLyrics();
            infoAlways(
                    BridgeDebugArea.PLAYER_SPECIAL,
                    BridgeEvents.TRANSLATION_ACTION_REBIND,
                    "Scanned current translation action views"
                            + " | package=" + preferencePackage
                            + ", enabled=" + enabled
                            + ", trackedViews=" + translationTracker.rememberedTranslationActionViewCount());
            requestOplusTranslationActionRebind(preferencePackage);
            translationTracker.refreshTranslationActionViewVisibility();
        });
        info(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.DETAIL, "lyricInfo translation " + (enabled ? "enabled" : "disabled")
                + " for " + preferencePackage);
    }

    private void resolveOplusPlaybackStateRefreshMethod(Class<?> managerClass) {
        if (managerClass == null || oplusPlaybackStateRefreshMethod != null) return;
        oplusPlaybackStateRefreshMethod = OplusMediaActionRefreshResolver.resolve(managerClass);
    }

    private void rememberOplusMediaDataManager(Object manager) {
        Method refreshMethod = oplusPlaybackStateRefreshMethod;
        if (manager == null || refreshMethod == null) return;
        Class<?>[] parameters = refreshMethod.getParameterTypes();
        if (parameters.length == 3 && parameters[0].isInstance(manager)) {
            oplusMediaDataManager = new WeakReference<>(manager);
        }
    }

    private String resolveMediaEntryKey(Object manager, String packageName) {
        if (manager == null || TextUtils.isEmpty(packageName)) {
            return packageName;
        }
        try {
            Method findEntryMethod = manager.getClass().getMethod("findExistingEntry", String.class);
            findEntryMethod.setAccessible(true);
            Object result = findEntryMethod.invoke(manager, packageName);
            if (result instanceof String && !((String) result).isEmpty()) {
                return (String) result;
            }
        } catch (Throwable ignored) {
        }
        return packageName;
    }

    /**
     * Re-runs SystemUI's full media rebuild for every live entry of {@code packageName} by calling
     * the MediaDataManager state entry directly: {@code updateState(key, state)} on ColorOS 16,
     * {@code onStateUpdate(key, state)} on ColorOS 17 (see {@link MediaDataStateUpdateResolver}).
     *
     * <p>The synthetic {@code updateMediaDataFromPlayState} entry is guarded by
     * {@code mediaPlayerIndex != -1 || state == PLAYING}; notification-keyed entries fail the index
     * check and a stale controller state fails the PLAYING check, so a lock/unlock rebind silently
     * did nothing until the player itself changed state (PJZ110 2026-09-19). The direct entry
     * has no such guard: it re-runs {@code createActionsFromState} (our gate) and
     * {@code onMediaDataLoaded} (priority recompute) for the real entry key and session token.</p>
     *
     * @return the entry name when at least one media entry was rebuilt, otherwise {@code null}
     */
    private String requestDirectMediaDataUpdateState(Object exManager, String packageName) {
        if (exManager == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        Object legacyManager = invokeNoArgByName(exManager, "getMediaDataManager");
        Object entries = legacyManager == null ? null : readFieldValue(legacyManager, "mediaEntries");
        if (!(entries instanceof Map)) {
            translationButtonDebug("direct updateState unavailable, package=" + packageName
                    + ", legacyManager=" + (legacyManager == null ? "null" : legacyManager.getClass().getName())
                    + ", entries=" + (entries == null ? "null" : entries.getClass().getName()));
            return null;
        }
        Method updateState = MediaDataStateUpdateResolver.resolve(legacyManager.getClass());
        if (updateState == null) {
            translationButtonDebug("direct updateState unavailable: method missing on "
                    + legacyManager.getClass().getName());
            return null;
        }
        String entryName = updateState.getName();
        Context context = currentApplicationContext();
        if (context == null) {
            return null;
        }
        List<Map.Entry<?, ?>> snapshot;
        try {
            snapshot = new ArrayList<>(((Map<?, ?>) entries).entrySet());
        } catch (Throwable t) {
            return null;
        }
        int updated = 0;
        for (Map.Entry<?, ?> entry : snapshot) {
            Object key = entry.getKey();
            Object data = entry.getValue();
            if (!(key instanceof String) || data == null) continue;
            if (!packageName.equals(invokeNoArgByName(data, "getPackageName"))) continue;
            Object token = invokeNoArgByName(data, "getToken");
            if (!(token instanceof android.media.session.MediaSession.Token)) {
                translationButtonDebug("direct updateState skipped: token null, key=" + key);
                continue;
            }
            PlaybackState state;
            try {
                state = new MediaController(context, (android.media.session.MediaSession.Token) token)
                        .getPlaybackState();
            } catch (Throwable t) {
                state = null;
            }
            if (state == null) {
                translationButtonDebug("direct updateState skipped: state null, key=" + key);
                continue;
            }
            try {
                updateState.invoke(legacyManager, key, state);
                updated++;
                translationButtonDebug("direct " + entryName + " requested, key=" + key
                        + ", state=" + state.getState());
            } catch (Throwable t) {
                error("Failed direct MediaDataManager." + entryName + " for " + key, t);
            }
        }
        return updated > 0 ? entryName : null;
    }

    private boolean requestOplusTranslationActionRebind(String packageName) {
        String safePackage = normalizeTranslationPreferencePackage(packageName);
        Method refreshMethod = oplusPlaybackStateRefreshMethod;
        Object manager = oplusMediaDataManager.get();
        String directEntry = requestDirectMediaDataUpdateState(manager, safePackage);
        if (directEntry != null) {
            infoAlways(
                    BridgeDebugArea.PLAYER_SPECIAL,
                    BridgeEvents.TRANSLATION_ACTION_REBIND,
                    "Requested translation action MediaData rebind via " + directEntry
                            + " | package=" + safePackage
                            + ", enabled=" + translationController.isLyricInfoTranslationEnabledFromCache(safePackage)
                            + ", keyguardLocked=" + isDeviceKeyguardLocked(currentApplicationContext()));
            return true;
        }
        MediaController controller;
        String controllerPackage;
        synchronized (systemUiPlaybackControllerLock) {
            PlaybackControllerBinding binding = systemUiPlaybackControllerBinding;
            controller = binding == null ? null : binding.controller;
            controllerPackage = binding == null ? "" : binding.packageName;
        }
        PlaybackState playbackState = null;
        if (controller != null) {
            try {
                playbackState = controller.getPlaybackState();
            } catch (Throwable ignored) {
                // A released session will be rejected by the missing-state gate below.
            }
        }
        if (refreshMethod == null
                || manager == null
                || controller == null
                || playbackState == null
                || !safePackage.equals(controllerPackage)) {
            infoAlways(
                    BridgeDebugArea.PLAYER_SPECIAL,
                    BridgeEvents.TRANSLATION_ACTION_REBIND,
                    "Skipped translation action MediaData rebind"
                            + " | package=" + safePackage
                            + ", manager=" + (manager != null)
                            + ", method=" + (refreshMethod != null)
                            + ", controller=" + (controller != null)
                            + ", state=" + (playbackState != null)
                            + ", controllerPackage=" + nullToEmpty(controllerPackage));
            return false;
        }
        String resolvedKey = resolveMediaEntryKey(manager, safePackage);
        try {
            refreshMethod.invoke(null, manager, resolvedKey, playbackState);
            infoAlways(
                    BridgeDebugArea.PLAYER_SPECIAL,
                    BridgeEvents.TRANSLATION_ACTION_REBIND,
                    "Requested translation action MediaData rebind"
                            + " | package=" + safePackage
                            + ", key=" + resolvedKey
                            + ", enabled="
                            + translationController.isLyricInfoTranslationEnabledFromCache(safePackage)
                            + ", playbackState=" + playbackState.getState());
            return true;
        } catch (Throwable throwable) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "translation-action-rebind-failed",
                    "Could not request translation action MediaData rebind"
                            + " | package=" + safePackage
                            + ", error=" + throwable.getClass().getSimpleName());
            return false;
        }
    }

    private void refreshLyricUiStyleSettingsIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (lyricUiStyleSettingsLoadedAtElapsedMs >= 0L
                && now - lyricUiStyleSettingsLoadedAtElapsedMs
                < LYRIC_UI_STYLE_SETTINGS_RELOAD_MS) {
            return;
        }
        boolean traced = BridgePerfTrace.begin("CLL:lyricSettingsReload");
        try {
            loadLyricUiStyleSettings(currentApplicationContext(), false);
        } finally {
            BridgePerfTrace.end(traced);
        }
    }

    private void loadLyricUiStyleSettings(Context context, boolean force) {
        if (context == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!force
                && lyricUiStyleSettingsLoadedAtElapsedMs >= 0L
                && now - lyricUiStyleSettingsLoadedAtElapsedMs
                < LYRIC_UI_STYLE_SETTINGS_RELOAD_MS) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                LyricUiSettings.PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        LyricUiConfig previousConfig = lyricUiConfig;
        LyricUiConfig config = LyricUiConfigRepository.load(preferences);
        lyricContentCleanupConfig = LyricContentCleanupRepository.load(preferences);
        lyricUiConfig = config;
        runtimeLyricUiConfig = config;
        dynamicArtworkRuntime.setCoverColorEnabled(config.glowFollowsCover && C17GlowSupport.available(context));
        applyLyricUiStyleSettings(
                config.scaleEnabled,
                config.blurEnabled,
                config.lineTimedProgressEnabled,
                config.translationProgressEnabled,
                config.screenTimeoutEnabled,
                config.screenTimeoutSeconds,
                false);
        lyricUiStyleSettingsLoadedAtElapsedMs = now;
        if (force) {
            loadBridgeDebugSettings(context);
        }
        if (force || previousConfig == null || !config.equals(previousConfig)) {
            settingsInfo("Loaded lyric UI settings"
                    + " | source=systemui-preferences"
                    + ", alignment=" + config.alignment
                    + ", fontSp10=" + config.mainFontTenthsSp
                    + ", lineSpacingDp10=" + config.lineSpacingTenthsDp
                    + ", wrappedLineSpacingDp10="
                    + config.wrappedLineSpacingTenthsDp
                    + ", force=" + force);
        }
    }

    private void handleLyricUiStyleSettingsChanged(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        long revision = intent.getLongExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, -1L);
        String source = nullToEmpty(intent.getStringExtra(
                LyricUiSettings.EXTRA_SETTINGS_SOURCE));
        settingsInfo("Received lyric UI settings"
                + " | source=" + source
                + ", revision=" + revision
                + ", previousAlignment=" + lyricUiConfig.alignment);
        LyricUiConfig config = LyricUiConfigRepository.decodePartial(intent, lyricUiConfig);
        if (config == null) {
            warn(LyricLogFormatter.Area.SETTINGS, "schema-rejected",
                    "Rejected lyric UI settings with unknown schema");
            sendSettingsApplyResult(intent, false, lyricUiConfig, "unknown-schema");
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        LyricUiConfig previousConfig = lyricUiConfig;
        LyricUiConfigRepository.save(
                appContext.getSharedPreferences(
                        LyricUiSettings.PREFERENCES_NAME,
                        Context.MODE_PRIVATE),
                config);
        lyricUiConfig = config;
        runtimeLyricUiConfig = config;
        dynamicArtworkRuntime.setCoverColorEnabled(config.glowFollowsCover && C17GlowSupport.available(context));
        if (intent.getBooleanExtra(
                LyricUiSettings.EXTRA_CLEAR_TRANSLATION_OVERRIDES,
                false)) {
            clearTranslationPreferenceOverrides(appContext, config.defaultTranslationEnabled);
        }
        applyLyricUiStyleSettings(
                config.scaleEnabled,
                config.blurEnabled,
                config.lineTimedProgressEnabled,
                config.translationProgressEnabled,
                config.screenTimeoutEnabled,
                config.screenTimeoutSeconds,
                true);
        if (!config.equals(previousConfig)) {
            resetOfficialRowScaleState();
            boolean lineSpacingChanged = previousConfig == null
                    || config.lineSpacingTenthsDp != previousConfig.lineSpacingTenthsDp;
            boolean wrappedLineSpacingChanged = previousConfig == null
                    || config.wrappedLineSpacingTenthsDp
                    != previousConfig.wrappedLineSpacingTenthsDp;
            for (View recycler : snapshotLyricsRecyclerViews()) {
                configureOfficialLyricLineSpacing(recycler);
                if (lineSpacingChanged || wrappedLineSpacingChanged) {
                    recycler.requestLayout();
                }
            }
            redrawLyricRenderTargets(
                    wrappedLineSpacingChanged,
                    wrappedLineSpacingChanged);
        }
        lyricUiStyleSettingsLoadedAtElapsedMs = SystemClock.elapsedRealtime();
        settingsInfo("Applied lyric UI settings"
                + " | source=" + source
                + ", revision=" + revision
                + ", alignment=" + config.alignment
                + ", fontSp10=" + config.mainFontTenthsSp
                + ", lineSpacingDp10=" + config.lineSpacingTenthsDp
                + ", wrappedLineSpacingDp10=" + config.wrappedLineSpacingTenthsDp
                + ", changed=" + !config.equals(previousConfig));
        sendSettingsApplyResult(intent, true, config, "applied");
    }

    private void handleLyricContentCleanupChanged(Context context, Intent intent) {
        if (context == null || intent == null) return;
        LyricContentCleanupConfig config =
                LyricContentCleanupConfigTransfer.decodeFromIntent(context, intent);
        if (config == null) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "content-cleanup-schema-rejected",
                    "Rejected lyric opening cleanup settings with unknown or invalid schema");
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;
        LyricContentCleanupRepository.save(
                appContext.getSharedPreferences(
                        LyricUiSettings.PREFERENCES_NAME,
                        Context.MODE_PRIVATE),
                config);
        lyricContentCleanupConfig = config;
        if (!replayCurrentSystemUiLyricLoadForCleanup()) {
            rebuildCurrentWordLyricModelForCleanup();
        }
        info(BridgeDebugArea.BOOTSTRAP, BridgeEvents.DETAIL, "Applied lyric opening cleanup settings, learned="
                + config.learnedRules.size()
                + ", trackOverrides="
                + config.firstFormalLineByTrack.size());
    }

    private boolean replayCurrentSystemUiLyricLoadForCleanup() {
        SystemUiLyricLoadContext context = latestSystemUiLyricLoadContext;
        Object owner = context == null ? null : context.owner.get();
        MediaMetadata metadata = context == null ? null : context.metadata.get();
        if (context == null
                || context != latestSystemUiLyricLoadContext
                || owner == null
                || metadata == null
                || !SystemUiDexKitAdapter.isReplayableMetadataRefresh(context.refreshMethod)
                || !TextUtils.equals(context.title, lastSystemUiSongName)
                || !TextUtils.equals(context.artist, lastSystemUiArtistName)) {
            return false;
        }
        if (!TextUtils.isEmpty(context.packageName)
                && !TextUtils.isEmpty(currentLyricProviderPackage)
                && !context.packageName.equals(currentLyricProviderPackage)) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "content-cleanup-reload-deferred",
                    "SystemUI metadata context belongs to another package; keeping the local rebuild");
            return false;
        }
        String liveTrackKey = payloadTrackKey(currentLyricProviderPayload);
        if (!TextUtils.isEmpty(context.trackKey)
                && !TextUtils.isEmpty(liveTrackKey)
                && !context.trackKey.equals(liveTrackKey)) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "content-cleanup-reload-deferred",
                    "SystemUI metadata context no longer matches the live track; keeping the local rebuild");
            return false;
        }
        if (SystemClock.elapsedRealtime() - context.observedAtElapsedMillis
                > SYSTEM_UI_METADATA_REPLAY_MAX_AGE_MS) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "content-cleanup-reload-deferred",
                    "SystemUI metadata context is stale; keeping the local rebuild");
            return false;
        }
        try {
            if (java.lang.reflect.Modifier.isStatic(context.refreshMethod.getModifiers())) {
                if (!SystemUiDexKitAdapter.canInvokeMetadataScheduler(
                        owner, context.refreshMethod, context.key)) {
                    warn(
                            LyricLogFormatter.Area.SETTINGS,
                            "content-cleanup-reload-deferred",
                            "SystemUI metadata scheduler is not eligible for this entry;"
                                    + " keeping the local model rebuild");
                    return false;
                }
                // Static scheduler: the receiver argument is ignored, owner is parameter 0.
                SystemUiDexKitAdapter.invokeMetadataScheduler(
                        context.refreshMethod, owner, context.key, metadata);
                // The void scheduler only enqueues a vendor job; it does not prove that the
                // official dataset was rebuilt. Keep the local model rebuild as the fallback.
                info(
                        BridgeDebugArea.BOOTSTRAP,
                        "content-cleanup-reload-requested",
                        "Requested SystemUI metadata reload; local model rebuild continues");
                return false;
            }
            context.refreshMethod.invoke(owner, context.key, metadata);
            return true;
        } catch (Throwable error) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "content-cleanup-reload-failed",
                    "Could not reload current SystemUI lyric after cleanup change");
            return false;
        }
    }

    private void rebuildCurrentWordLyricModelForCleanup() {
        LyricInfoContract.Payload current = currentLyricProviderPayload;
        if (current == null || current.snapshotPositionMillis >= 0L
                || TextUtils.isEmpty(currentCleanupSnapshotRawLyric)) return;
        try {
            JSONObject object = new JSONObject();
            object.put(LyricInfoContract.JSON_SONG_NAME, current.songName);
            object.put(LyricInfoContract.JSON_ARTIST, current.artist);
            object.put(LyricInfoContract.JSON_ALBUM, current.album);
            object.put(LyricInfoContract.JSON_SONG_ID, current.songId);
            object.put(LyricInfoContract.JSON_LYRIC, currentCleanupSnapshotDisplayLyric);
            object.put(LyricInfoContract.JSON_RAW_LYRIC, currentCleanupSnapshotRawLyric);
            object.put(LyricInfoContract.JSON_TRANSLATION_LYRIC, current.translationLyric);
            object.put(LyricInfoContract.JSON_PROVIDER, current.provider);
            object.put(LyricInfoContract.JSON_TRACK_KEY, currentCleanupSnapshotTrackKey);
            object.put(LyricInfoContract.JSON_SESSION_GENERATION, current.sessionGeneration);
            object.put(LyricInfoContract.JSON_SOURCE, current.source);
            LyricInfoContract.NormalizedPayload source =
                    LyricInfoContract.parseLyricInfo(object.toString());
            LyricInfoContract.NormalizedPayload cleaned = applyModuleContentCleanup(
                    source,
                    lastSystemUiSongName,
                    lastSystemUiArtistName,
                    false);
            cleaned = LyricInfoContract.normalizeOfficialLyricInfo(cleaned.lyricInfo);
            if (cleaned == null || cleaned.payload == null) return;
            long rebuildEpoch = systemUiLyricPublicationGate.beginLoad();
            synchronized (wordLyricModelCacheLock) {
                currentWordLyricModelSignature = "";
                cacheSystemUiLyricModelLocked(
                        null,
                        cleaned.payload,
                        source.payload,
                        rebuildEpoch,
                        currentLyricProviderPackage,
                        lastSystemUiSongName,
                        lastSystemUiArtistName);
            }
            redrawLyricRenderTargets(false, false);
        } catch (Throwable error) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "content-cleanup-model-rebuild-failed",
                    "Could not rebuild current word lyric model after cleanup change");
        }
    }

    private void applyLyricUiStyleSettings(
            boolean scrollScale,
            boolean inactiveBlur,
            boolean lineTimedProgress,
            boolean translationProgress,
            boolean screenTimeoutEnabled,
            int screenTimeoutSeconds,
            boolean refreshViews) {
        int sanitizedScreenTimeoutSeconds =
                LyricUiSettings.sanitizeScreenTimeoutSeconds(screenTimeoutSeconds);
        boolean layoutChanged = lyricUiScrollScaleEnabled != scrollScale;
        boolean styleChanged = layoutChanged
                || lyricUiInactiveBlurEnabled != inactiveBlur
                || lyricUiLineTimedProgressEnabled != lineTimedProgress
                || lyricUiTranslationProgressEnabled != translationProgress;
        boolean screenTimeoutChanged =
                screenTimeout.screenTimeoutSmartEnabled != screenTimeoutEnabled
                        || screenTimeout.screenTimeoutCustomSeconds != sanitizedScreenTimeoutSeconds;
        boolean changed = styleChanged || screenTimeoutChanged;
        lyricUiScrollScaleEnabled = scrollScale;
        lyricUiInactiveBlurEnabled = inactiveBlur;
        lyricUiLineTimedProgressEnabled = lineTimedProgress;
        lyricUiTranslationProgressEnabled = translationProgress;
        screenTimeout.screenTimeoutSmartEnabled = screenTimeoutEnabled;
        screenTimeout.screenTimeoutCustomSeconds = sanitizedScreenTimeoutSeconds;
        officialLyricTextRenderer.setStyleOptions(
                scrollScale,
                inactiveBlur,
                lineTimedProgress,
                translationProgress);
        officialLyricTextRenderer.setConfig(lyricUiConfig);
        if (!changed) {
            return;
        }
        if (screenTimeoutChanged) {
            screenTimeout.resetScreenTimeoutKeepAwakeWindow();
            if (screenTimeout.screenTimeoutSmartEnabled) {
                screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
            } else {
                screenTimeout.releaseScreenTimeoutWakeLock("disabled by user");
            }
        }
        if (layoutChanged) {
            resetOfficialRowScaleState();
        }
        info(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.DETAIL, "Updated lyric UI style settings, scrollScale=" + scrollScale
                + ", inactiveBlur=" + inactiveBlur
                + ", lineTimedProgress=" + lineTimedProgress
                + ", translationProgress=" + translationProgress
                + ", screenTimeoutEnabled=" + screenTimeoutEnabled
                + ", screenTimeoutSeconds=" + sanitizedScreenTimeoutSeconds
                + ", alignment=" + lyricUiConfig.alignment);
        if (refreshViews && styleChanged) {
            redrawLyricRenderTargets(layoutChanged, layoutChanged);
        }
    }

    private void resetOfficialRowScaleState() {
        WordLyricModel model = currentWordLyricModel;
        if (model == null) {
            return;
        }
        for (WordLine line : model.lines) {
            line.rowVisualScaleInitialized = false;
            line.rowVisualScaleStart = 1f;
            line.rowVisualScaleTarget = 1f;
            line.rowVisualFadeStart = 1f;
            line.rowVisualFadeTarget = 1f;
            line.rowVisualBlurRadiusTarget = 0f;
            line.rowVisualScaleStartedAtMs = -1L;
            line.rowVisualScaleActiveIndex = Integer.MIN_VALUE;
        }
    }

    private void applyTranslationPreferenceForPackage(String packageName) {
        String preferencePackage = normalizeTranslationPreferencePackage(packageName);
        translationController.ensureTranslationPreferenceLoaded(preferencePackage);
        officialLyricTextRenderer.setTranslationEnabledImmediately(
                translationController.isLyricInfoTranslationEnabledFromCache(preferencePackage));
        mainHandler.post(translationTracker::refreshTranslationActionViewVisibility);
    }

    private String currentTranslationPreferencePackage() {
        return nullToEmpty(currentLyricProviderPackage);
    }

    private void handlePlayerTranslationSettingsChanged(Context context, Intent intent) {
        if (context == null || intent == null) return;
        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;
        SharedPreferences preferences = appContext.getSharedPreferences(
                TRANSLATION_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        String currentPackage = currentTranslationPreferencePackage();
        boolean previousCurrentValue = translationController.isLyricInfoTranslationEnabled(currentPackage);
        long revision = intent.getLongExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, -1L);
        String source = nullToEmpty(intent.getStringExtra(
                LyricUiSettings.EXTRA_SETTINGS_SOURCE));
        boolean globalDefaultEnabled = intent.hasExtra(
                LyricUiSettings.EXTRA_DEFAULT_TRANSLATION_ENABLED)
                ? intent.getBooleanExtra(
                        LyricUiSettings.EXTRA_DEFAULT_TRANSLATION_ENABLED,
                        lyricUiConfig.defaultTranslationEnabled)
                : intent.getBooleanExtra(
                        LyricUiConfigCodec.DEFAULT_TRANSLATION,
                        lyricUiConfig.defaultTranslationEnabled);
        if (intent.hasExtra(LyricUiSettings.EXTRA_DEFAULT_TRANSLATION_BUTTON_ENABLED)) {
            preferences.edit()
                    .putBoolean(
                            LyricUiSettings.KEY_DEFAULT_TRANSLATION_BUTTON,
                            intent.getBooleanExtra(
                                    LyricUiSettings.EXTRA_DEFAULT_TRANSLATION_BUTTON_ENABLED,
                                    true))
                    .apply();
            translationController.translationButtonEnabledByPackage.clear();
        }
        LyricUiConfig previousConfig = lyricUiConfig;
        LyricUiConfig updatedConfig = LyricUiSettings.withGlobalTranslationDefault(
                previousConfig,
                globalDefaultEnabled);
        boolean globalDefaultChanged = updatedConfig.defaultTranslationEnabled
                != previousConfig.defaultTranslationEnabled;
        LyricUiConfigRepository.save(
                appContext.getSharedPreferences(
                        LyricUiSettings.PREFERENCES_NAME,
                        Context.MODE_PRIVATE),
                updatedConfig);
        lyricUiConfig = updatedConfig;
        runtimeLyricUiConfig = updatedConfig;
        officialLyricTextRenderer.setConfig(updatedConfig);
        lyricUiStyleSettingsLoadedAtElapsedMs = SystemClock.elapsedRealtime();
        settingsInfo("Received player translation settings"
                + " | source=" + source
                + ", revision=" + revision
                + ", alignment=" + updatedConfig.alignment
                + ", previousAlignment=" + previousConfig.alignment
                + ", globalDefault=" + globalDefaultEnabled);
        LinkedHashSet<String> affectedPackages = new LinkedHashSet<>();
        SharedPreferences.Editor editor = preferences.edit();

        String[] packages = intent.getStringArrayExtra(
                LyricUiSettings.EXTRA_PLAYER_TRANSLATION_PACKAGES);
        boolean[] defaults = intent.getBooleanArrayExtra(
                LyricUiSettings.EXTRA_PLAYER_TRANSLATION_DEFAULTS);
        if (packages != null && defaults != null && packages.length == defaults.length) {
            int count = Math.min(packages.length, LyricUiSettings.MAX_PLAYER_TRANSLATION_PACKAGES);
            for (int i = 0; i < count; i++) {
                String packageName = nullToEmpty(packages[i]);
                if (!PlayerTranslationSettings.isTranslationSettingsTarget(packageName)) continue;
                editor.putBoolean(
                        LyricUiSettings.translationDefaultKeyForPackage(packageName),
                        defaults[i]);
                affectedPackages.add(packageName);
            }
        }

        String[] buttonPackages = intent.getStringArrayExtra(
                LyricUiSettings.EXTRA_TRANSLATION_BUTTON_PACKAGES);
        boolean[] buttonValues = intent.getBooleanArrayExtra(
                LyricUiSettings.EXTRA_TRANSLATION_BUTTON_VALUES);
        if (buttonPackages != null && buttonValues != null
                && buttonPackages.length == buttonValues.length) {
            int count = Math.min(buttonPackages.length, LyricUiSettings.MAX_PLAYER_TRANSLATION_PACKAGES);
            for (int i = 0; i < count; i++) {
                String packageName = nullToEmpty(buttonPackages[i]);
                if (!PlayerTranslationSettings.isTranslationSettingsTarget(packageName)) continue;
                editor.putBoolean(
                        LyricUiSettings.translationButtonKeyForPackage(packageName),
                        buttonValues[i]);
                translationController.translationButtonEnabledByPackage.put(packageName, buttonValues[i]);
            }
        }

        String[] clearPackages = intent.getStringArrayExtra(
                LyricUiSettings.EXTRA_CLEAR_TRANSLATION_PACKAGES);
        if (clearPackages != null) {
            int count = Math.min(clearPackages.length, LyricUiSettings.MAX_PLAYER_TRANSLATION_PACKAGES);
            for (int i = 0; i < count; i++) {
                String packageName = nullToEmpty(clearPackages[i]);
                if (!PlayerTranslationSettings.isTranslationSettingsTarget(packageName)) continue;
                editor.remove(translationPreferenceKeyForPackage(packageName));
                affectedPackages.add(packageName);
            }
        }
        editor.apply();
        reapplyCurrentTranslationButtonSetting();
        if (globalDefaultChanged) {
            affectedPackages.add(currentPackage);
        }
        for (String packageName : affectedPackages) {
            translationController.lyricInfoTranslationEnabledByPackage.remove(packageName);
            translationController.loadedTranslationPreferencePackages.remove(packageName);
        }

        if (affectedPackages.contains(currentPackage)) {
            boolean defaultValue = translationController.defaultTranslationPreferenceValue(
                    preferences,
                    currentPackage);
            boolean enabled = preferences.getBoolean(
                    translationPreferenceKeyForPackage(currentPackage),
                    defaultValue);
            translationController.lyricInfoTranslationEnabledByPackage.put(currentPackage, enabled);
            translationController.loadedTranslationPreferencePackages.add(currentPackage);
            if (previousCurrentValue != enabled) {
                officialLyricTextRenderer.setTranslationEnabled(enabled);
                int generation = ++translationLayoutGeneration;
                mainHandler.post(() -> refreshLyricViewsAfterTranslationToggle(generation));
            }
        }
        requestOplusTranslationActionRebind(currentTranslationPreferencePackage());
        infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SETTINGS_APPLIED, "Updated player translation settings, players=" + affectedPackages.size()
                + ", cleared=" + (clearPackages == null ? 0 : clearPackages.length)
                + " | source=" + source
                + ", revision=" + revision
                + ", alignment=" + updatedConfig.alignment);
        sendSettingsApplyResult(intent, true, updatedConfig, "applied");
    }

    private void reapplyCurrentTranslationButtonSetting() {
        String packageName = currentTranslationPreferencePackage();
        WeakReference<Object> reference = latestTranslationMediaButtons.get(packageName);
        Object mediaButton = reference == null ? null : reference.get();
        if (reference != null && mediaButton == null) {
            latestTranslationMediaButtons.remove(packageName, reference);
        }
        if (mediaButton != null) {
            boolean canOverride = PlayerTranslationTogglePolicy
                    .canOverrideFavoriteActionWithTranslation(
                            packageName,
                            providerDeclaredTranslationTogglePackages);
            translationToggleActionBinder.applyTranslationToggle(
                    packageName,
                    mediaButton,
                    PlayerTranslationTogglePolicy.shouldReplaceFavoriteActionWithTranslation(
                            canOverride,
                            isCurrentLyricProviderPackage(packageName),
                            currentWordLyricModel == null
                                    ? -1
                                    : currentWordLyricModel.translationCount(),
                            currentLyricProviderPayload == null
                                    ? null
                                    : currentLyricProviderPayload.translationLyric)
                            && translationController.userWantsTranslationButton(packageName),
                    translationController.userWantsTranslationButton(packageName),
                    currentLyricProviderPackage,
                    currentWordLyricModel == null
                            ? -1
                            : currentWordLyricModel.translationCount(),
                    currentLyricProviderPayload == null
                            || currentLyricProviderPayload.translationLyric == null
                            ? -1
                            : currentLyricProviderPayload.translationLyric.length());
        }
        mainHandler.post(() -> {
            translationTracker.refreshTranslationActionViewVisibility();
            scheduleTranslationActionViewRecovery();
        });
    }

    @SuppressWarnings("deprecation")
    private void sendSettingsApplyResult(
            Intent intent,
            boolean applied,
            LyricUiConfig config,
            String reason) {
        if (intent == null) return;
        if (applied) diagnosticUiRevision = intent.getLongExtra(
                LyricUiSettings.EXTRA_CONFIG_REVISION, -1L);
        ResultReceiver receiver = intent.getParcelableExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER);
        if (receiver == null) return;
        Bundle result = new Bundle();
        result.putBoolean(LyricUiSettings.RESULT_APPLIED, applied);
        result.putLong(
                LyricUiSettings.RESULT_CONFIG_REVISION,
                intent.getLongExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, -1L));
        result.putInt(
                LyricUiSettings.RESULT_ALIGNMENT,
                config == null ? -1 : config.alignment);
        result.putString(LyricUiSettings.RESULT_PROCESS, logProcessName);
        result.putString(LyricUiSettings.RESULT_REASON, nullToEmpty(reason));
        try {
            receiver.send(
                    applied
                            ? LyricUiSettings.RESULT_SETTINGS_APPLIED
                            : LyricUiSettings.RESULT_SETTINGS_REJECTED,
                    result);
        } catch (Throwable throwable) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "settings-ack-failed",
                    "Could not return settings acknowledgement"
                            + " | revision=" + result.getLong(
                            LyricUiSettings.RESULT_CONFIG_REVISION,
                            -1L)
                            + ", applied=" + applied
                            + ", error=" + throwable.getClass().getSimpleName());
        }
    }

    private void clearTranslationPreferenceOverrides(Context context, boolean enabled) {
        SharedPreferences preferences = context.getSharedPreferences(
                TRANSLATION_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (LyricUiSettings.isTranslationOverrideKey(key)) {
                editor.remove(key);
            }
        }
        editor.apply();
        translationController.lyricInfoTranslationEnabledByPackage.clear();
        translationController.loadedTranslationPreferencePackages.clear();
        officialLyricTextRenderer.setTranslationEnabled(enabled);
        int generation = ++translationLayoutGeneration;
        mainHandler.post(() -> refreshLyricViewsAfterTranslationToggle(generation));
        info(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.DETAIL, "Cleared player-specific translation preferences, defaultEnabled=" + enabled);
    }

    private void refreshLyricViewsAfterTranslationToggle(int generation) {
        if (generation != translationLayoutGeneration) {
            return;
        }
        boolean aod = isAodLowFrameRateLyricMode();
        if (aod) {
            officialLyricTextRenderer.finishTranslationLayoutChange();
        }
        boolean running = officialLyricTextRenderer.advanceTranslationLayoutAnimation();
        for (View recycler : snapshotLyricsRecyclerViews()) {
            int targetIndex = readLyricsRecyclerCurrentIndex(recycler);
            LyricsRecyclerGeometry before = targetIndex >= 0
                    ? captureLyricsRecyclerGeometry(recycler, targetIndex)
                    : null;
            applyVisibleLyricBlockHeights(recycler);
            invalidateLyricsRecyclerDescendants(recycler);
            if (!aod
                    && before != null
                    && before.targetCenter != Integer.MIN_VALUE) {
                scheduleTranslationToggleAnchorRestore(
                        generation,
                        recycler,
                        targetIndex,
                        before.targetCenter);
            }
        }
        if (running && !aod && generation == translationLayoutGeneration) {
            View frameHost = firstAttachedLyricsRecyclerView();
            if (frameHost != null) {
                frameHost.postOnAnimation(
                        () -> refreshLyricViewsAfterTranslationToggle(generation));
            } else {
                mainHandler.postDelayed(
                        () -> refreshLyricViewsAfterTranslationToggle(generation),
                        TRANSLATION_TOGGLE_LAYOUT_FRAME_MS);
            }
        }
    }

    private void scheduleTranslationToggleAnchorRestore(
            int generation,
            View recycler,
            int targetIndex,
            int desiredCenter) {
        ViewTreeObserver observer = recycler.getViewTreeObserver();
        if (!observer.isAlive()) {
            return;
        }
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver current = recycler.getViewTreeObserver();
                if (current.isAlive()) {
                    current.removeOnPreDrawListener(this);
                }
                restoreTranslationToggleAnchor(
                        generation,
                        recycler,
                        targetIndex,
                        desiredCenter);
                return true;
            }
        });
    }

    private void restoreTranslationToggleAnchor(
            int generation,
            View recycler,
            int targetIndex,
            int desiredCenter) {
        if (generation != translationLayoutGeneration
                || recycler == null
                || !recycler.isAttachedToWindow()
                || isAodLowFrameRateLyricMode()) {
            return;
        }
        LyricsRecyclerGeometry after = captureLyricsRecyclerGeometry(recycler, targetIndex);
        if (after.targetCenter == Integer.MIN_VALUE) {
            return;
        }
        int correction = after.targetCenter - desiredCenter;
        int tolerance = dp(recycler.getContext(), 1f);
        int maximumCorrection = dp(recycler.getContext(), 48f);
        if (Math.abs(correction) <= tolerance || Math.abs(correction) > maximumCorrection) {
            return;
        }
        recycler.scrollBy(0, correction);
    }

    private void redrawLyricRenderTargets(boolean requestLayout, boolean syncRecyclerHeights) {
        for (View recycler : snapshotLyricsRecyclerViews()) {
            configureOfficialLyricScaleOwnership(recycler);
            if (syncRecyclerHeights) {
                applyVisibleLyricBlockHeights(recycler);
            }
            if (requestLayout) {
                recycler.requestLayout();
            }
            invalidateLyricsRecyclerDescendants(recycler);
            recycler.postInvalidateOnAnimation();
        }
        for (TextView textView : snapshotActiveTextViews()) {
            if (requestLayout) {
                textView.requestLayout();
            }
            textView.invalidate();
            textView.postInvalidateOnAnimation();
        }
        for (View root : snapshotLyricRootViews()) {
            if (requestLayout) {
                root.requestLayout();
            }
            root.invalidate();
            root.postInvalidateOnAnimation();
        }
    }

    private void installSystemUiWordLyricHooks(
            ClassLoader classLoader,
            SystemUiDexKitAdapter.Targets targets) {
        try {
            Method loadLyricInBg = targets.loadLyricInBg;
            loadLyricInBg.setAccessible(true);
            resolveOplusPlaybackStateRefreshMethod(loadLyricInBg.getDeclaringClass());
            hook(loadLyricInBg)
                    .setId(HOOK_ID_SYSTEMUI_LOAD_LYRIC)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiLoadLyricInBg);
            installKuWoArtworkSnapshotHook(loadLyricInBg.getDeclaringClass());
            installKuWoMediaDataArtworkCaptureHook(loadLyricInBg.getDeclaringClass());
            installKuWoSeedlingArtworkHook(classLoader);
            installSystemUiArtworkDiagnosticsHook(classLoader);
            installKuWoCarLyricMetadataHook(loadLyricInBg.getDeclaringClass(), targets.metadataChanged);
            installKuWoCarLyricLoadMetadataHook(
                    classLoader,
                    "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl");

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

            Method setText = TextView.class.getDeclaredMethod(
                    "setText",
                    CharSequence.class,
                    TextView.BufferType.class);
            setText.setAccessible(true);
            hook(setText)
                    .setId(HOOK_ID_TEXTVIEW_SET_TEXT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onTextViewSetText);

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

            Method setVisibility = View.class.getDeclaredMethod("setVisibility", int.class);
            setVisibility.setAccessible(true);
            hook(setVisibility)
                    .setId(HOOK_ID_VIEW_SET_VISIBILITY)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onViewSetVisibility);

            Method setContentDescription = View.class.getDeclaredMethod(
                    "setContentDescription",
                    CharSequence.class);
            setContentDescription.setAccessible(true);
            hook(setContentDescription)
                    .setId(HOOK_ID_VIEW_SET_CONTENT_DESCRIPTION)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onViewSetContentDescription);

            Method setImageDrawable = ImageView.class.getDeclaredMethod(
                    "setImageDrawable",
                    Drawable.class);
            setImageDrawable.setAccessible(true);
            hook(setImageDrawable)
                    .setId(HOOK_ID_IMAGE_VIEW_SET_IMAGE_DRAWABLE)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onImageViewSetImage);

            Method setImageBitmap = ImageView.class.getDeclaredMethod(
                    "setImageBitmap",
                    Bitmap.class);
            setImageBitmap.setAccessible(true);
            hook(setImageBitmap)
                    .setId(HOOK_ID_IMAGE_VIEW_SET_IMAGE_BITMAP)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onImageViewSetImage);

            tryInstallPluginClassLoaderConstructorHook(classLoader);
            tryInstallLyricsRecyclerViewHook(classLoader);
            ensureScreenTimeoutReceiver(currentApplicationContext());
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked SystemUI official lyric hooks"
                    + (targets.resolvedByDexKit ? " via DexKit" : " via legacy fallback"));
        } catch (Throwable t) {
            error("Failed to hook SystemUI official lyric TextView draw hooks", t);
        }
    }

    /**
     * KuWo publishes its cover through an http artwork URI that ColorOS refuses to load and
     * a Bitmap lane that does not survive the MediaSession binder crossing, so every
     * metadata-driven MediaData rebuild resolves the artwork to null and the immersive card
     * falls back to the default solid-color art. Capture the last non-null same-track icon
     * seen at the common lookup point and hand it back when the metadata-derived lookup is
     * empty again. Real track changes miss the snapshot and keep native behavior.
     */
    private void installKuWoArtworkSnapshotHook(Class<?> ownerClass) {
        try {
            Method getArtwork = null;
            Class<?> current = ownerClass;
            while (current != null && getArtwork == null) {
                for (Method candidate : current.getDeclaredMethods()) {
                    if ("getArtWorkIconByMediaMetaData".equals(candidate.getName())
                            && candidate.getParameterCount() == 2
                            && candidate.getParameterTypes()[0] == MediaMetadata.class
                            && candidate.getParameterTypes()[1] == String.class) {
                        getArtwork = candidate;
                        break;
                    }
                }
                current = current.getSuperclass();
            }
            if (getArtwork == null) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "Skipped KuWo artwork snapshot hook:"
                                + " getArtWorkIconByMediaMetaData not found");
                return;
            }
            getArtwork.setAccessible(true);
            hook(getArtwork)
                    .setId(HOOK_ID_SYSTEMUI_ARTWORK_ICON)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiKuWoArtworkLookup);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked SystemUI media-metadata artwork lookup for KuWo cover snapshot: "
                    + getArtwork.getDeclaringClass().getName());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook KuWo artwork snapshot: " + t);
        }
    }

    /**
     * C17 removed the 2-argument metadata lookup. Capture the artwork that the live media-data
     * pipeline already resolved (for example from the player's MediaSession) so the same-track
     * Seedling repair keeps a valid source without rebuilding or downloading a cover.
     */
    private void installKuWoMediaDataArtworkCaptureHook(Class<?> ownerClass) {
        try {
            Method onMediaDataLoaded = null;
            for (Method candidate : ownerClass.getDeclaredMethods()) {
                if (!"onMediaDataLoaded".equals(candidate.getName())
                        || candidate.getParameterCount() != 3) {
                    continue;
                }
                Class<?>[] types = candidate.getParameterTypes();
                if (types[0] == String.class
                        && types[1] == String.class
                        && "com.android.systemui.media.controls.shared.model.MediaData"
                        .equals(types[2].getName())) {
                    onMediaDataLoaded = candidate;
                    break;
                }
            }
            if (onMediaDataLoaded == null) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "Skipped KuWo media-data artwork capture: onMediaDataLoaded not found");
                return;
            }
            onMediaDataLoaded.setAccessible(true);
            hook(onMediaDataLoaded)
                    .setId(HOOK_ID_SYSTEMUI_ARTWORK_ICON + "-media-data")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onKuWoMediaDataArtworkLoaded);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED,
                    "Hooked live media-data artwork capture for KuWo cover snapshot: "
                            + onMediaDataLoaded.getDeclaringClass().getName());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook KuWo media-data artwork capture: " + t);
        }
    }

    private Object onKuWoMediaDataArtworkLoaded(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            Object data = chain.getArg(2);
            if (data == null
                    || !KuWoMediaIdentityPolicy.isPlayerPackage(
                    invokeStringGetter(data, "getPackageName"))) {
                return result;
            }
            Object artwork = invokeNoArgByName(data, "getArtwork");
            if (!(artwork instanceof Icon) || !isPlausibleKuWoCoverIcon((Icon) artwork)) {
                return result;
            }
            Object mediaDataEx = invokeNoArgByName(data, "getMediaDataEx");
            Object metadata = mediaDataEx == null
                    ? null
                    : invokeNoArgByName(mediaDataEx, "getMetadata");
            if (metadata instanceof MediaMetadata) {
                rememberKuWoArtworkSnapshots((MediaMetadata) metadata, (Icon) artwork);
            } else {
                rememberKuWoArtworkSnapshot(
                        buildTrackKey(
                                invokeStringGetter(data, "getSong"),
                                invokeStringGetter(data, "getArtist")),
                        (Icon) artwork);
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo media-data artwork capture failed: " + t);
        }
        return result;
    }

    private void installKuWoSeedlingArtworkHook(ClassLoader classLoader) {
        try {
            Class<?> dataClass = classLoader.loadClass(
                    "com.oplus.systemui.seedlingservice.mediaControl.SeedlingMediaData");
            Method setArtwork = dataClass.getDeclaredMethod(
                    "setArtworkIcon",
                    Icon.class);
            setArtwork.setAccessible(true);
            hook(setArtwork)
                    .setId(HOOK_ID_SYSTEMUI_SEEDLING_ARTWORK)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onKuWoSeedlingArtworkChanged);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked Seedling artwork for KuWo same-track cover repair");
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook Seedling artwork: " + t);
        }
    }

    private Object onKuWoSeedlingArtworkChanged(XposedInterface.Chain chain)
            throws Throwable {
        Object data = null;
        boolean traceLxArtwork = false;
        Object[] proceedArgs = null;
        try {
            data = chain.getThisObject();
            if (data != null && isLxSeedlingData(data)) {
                Object traceData = data;
                Icon incoming = chain.getArg(0) instanceof Icon ? (Icon) chain.getArg(0) : null;
                artworkProbe("SEEDLING_SET_IN", () -> ArtworkDiagnostics.describeSeedling(traceData)
                        + " | incoming=" + ArtworkDiagnostics.describeIcon(incoming));
                traceLxArtwork = true;
            } else if (data != null && isKuwoSeedlingData(data)) {
                String title = invokeStringGetter(data, "getSong");
                String artist = invokeStringGetter(data, "getArtist");
                String trackKey = buildTrackKey(title, artist);
                Icon incoming = chain.getArg(0) instanceof Icon ? (Icon) chain.getArg(0) : null;
                Icon snapshot = peekKuWoArtworkSnapshot(trackKey, lastSystemUiMediaId);
                if (KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                        isPlausibleKuWoCoverIcon(incoming),
                        trackKey,
                        buildTrackKey(lastSystemUiSongName, lastSystemUiArtistName),
                        snapshot != null)) {
                    long now = SystemClock.elapsedRealtime();
                    if (kuWoRuntime.takeSeedlingLog(now)) {
                        info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED,
                                "Repaired Seedling album artwork from the same-track snapshot;"
                                        + " incoming=" + describeSeedlingIcon(incoming)
                                        + ", repaired=" + describeSeedlingIcon(snapshot));
                    }
                    proceedArgs = chain.getArgs().toArray(new Object[0]);
                    proceedArgs[0] = snapshot;
                }
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo Seedling artwork repair failed: " + t);
        }
        Object result = HookProceed.once(chain, proceedArgs);
        if (traceLxArtwork && data != null) {
            Object tracedData = data;
            try {
                artworkProbe("SEEDLING_SET_OUT", () -> ArtworkDiagnostics.describeSeedling(tracedData));
            } catch (Throwable ignored) {
                // Diagnostics never own the host result.
            }
        }
        return result;
    }

    private static boolean isKuwoSeedlingData(Object data) {
        try {
            return KuWoMediaIdentityPolicy.isPlayerPackage(
                    invokeStringGetter(data, "getPackageName"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLxSeedlingData(Object data) {
        return data != null && isLxPlayerPackage(invokeStringGetter(data, "getPackageName"));
    }

    private void installSystemUiArtworkDiagnosticsHook(ClassLoader classLoader) {
        try {
            Class<?> loaderClass = classLoader.loadClass(
                    "com.oplus.systemui.media.seedling.client.loader.album.artwrok.UriArtworkLoader");
            Method load = null;
            for (Method candidate : loaderClass.getDeclaredMethods()) {
                if ("load".equals(candidate.getName()) && candidate.getParameterCount() == 3) {
                    load = candidate;
                    break;
                }
            }
            if (load == null) return;
            load.setAccessible(true);
            hook(load)
                    .setId(HOOK_ID_SYSTEMUI_ARTWORK_LOADER_PROBE)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiArtworkLoaderProbe);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED,
                    "Hooked Seedling artwork loader diagnostics");
        } catch (Throwable t) {
            warn(LyricLogFormatter.Area.SYSTEM_UI, "artwork-probe",
                    "Failed to hook Seedling artwork loader diagnostics: " + t);
        }
    }

    private Object onSystemUiArtworkLoaderProbe(XposedInterface.Chain chain) throws Throwable {
        Object data = chain.getArg(2);
        String packageName = chain.getArg(0) instanceof String ? (String) chain.getArg(0) : "";
        if (!isLxPlayerPackage(packageName) && !isLxSeedlingData(data)) {
            return chain.proceed();
        }
        artworkProbe("LOADER_IN", () -> ArtworkDiagnostics.describeSeedling(data));
        Object result = chain.proceed();
        artworkProbe("LOADER_OUT", () -> ArtworkDiagnostics.describeLoaderResult(result)
                + " | " + ArtworkDiagnostics.describeSeedling(data));
        return result;
    }

    private static String invokeStringGetter(Object owner, String methodName) {
        if (owner == null) {
            return "";
        }
        try {
            Object value = owner.getClass()
                    .getMethod(methodName)
                    .invoke(owner);
            return value instanceof CharSequence ? value.toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void installKuWoCarLyricMetadataHook(Class<?> ownerClass, Method metadataChanged) {
        try {
            Method changed = metadataChanged;
            if (changed == null || SystemUiDexKitAdapter.metadataParameterIndex(changed) < 0) {
                Class<?> current = ownerClass;
                while (current != null && changed == null) {
                    changed = SystemUiDexKitAdapter.findMetadataChangedEntry(current);
                    current = current.getSuperclass();
                }
            }
            if (changed == null) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "Skipped KuWo car-lyric metadata hook: onMetaDataChanged not found");
                return;
            }
            changed.setAccessible(true);
            if (SystemUiDexKitAdapter.isReplayableMetadataChanged(changed)) {
                systemUiMetadataRefreshMethod = changed;
            }
            hook(changed)
                    .setId(HOOK_ID_MEDIA_DATA_METADATA_CHANGED)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onOplusMediaDataMetadataChanged);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus media metadata changed for KuWo car-lyric identity: "
                    + changed.getDeclaringClass().getName()
                    + "#"
                    + changed.getName()
                    + " params="
                    + changed.getParameterCount());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook KuWo car-lyric metadata identity: " + t);
        }
    }

    private Object onOplusMediaDataMetadataChanged(XposedInterface.Chain chain)
            throws Throwable {
        Object[] args = chain.getArgs().toArray(new Object[0]);
        rememberOplusMediaDataManager(metadataChangedOwner(chain, args));
        int metadataIndex = metadataArgIndex(args);
        if (metadataIndex < 0) {
            return chain.proceed();
        }
        MediaMetadata metadata = (MediaMetadata) args[metadataIndex];
        String incomingTitle = firstNonEmpty(
                getText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        String incomingArtist = firstNonEmpty(
                getText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
        if (!KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                lastSystemUiSongName,
                lastSystemUiArtistName,
                incomingTitle,
                incomingArtist)) {
            return chain.proceed();
        }
        MediaMetadata stable = buildMetadataWithStableSystemUiIdentity(
                metadata,
                lastSystemUiSongName,
                lastSystemUiArtistName);
        args[metadataIndex] = stable;
        if (kuWoRuntime.takeCarLyricNormalizedLogOnce()) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Normalized KuWo car-lyric identity before media data update");
        }
        return chain.proceed(args);
    }

    private static Object metadataChangedOwner(XposedInterface.Chain chain, Object[] args) {
        Object owner = chain.getThisObject();
        if (owner != null) {
            return owner;
        }
        int metadataIndex = metadataArgIndex(args);
        if (metadataIndex >= 2 && args[0] != null && args[metadataIndex - 1] instanceof String) {
            return args[0];
        }
        return null;
    }

    private static int metadataArgIndex(Object[] args) {
        int index = -1;
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof MediaMetadata)) {
                continue;
            }
            if (index >= 0) {
                return -1;
            }
            index = i;
        }
        if (index < 1 || !(args[index - 1] instanceof String)) {
            return -1;
        }
        return index;
    }

    private void installKuWoCarLyricLoadMetadataHook(
            ClassLoader classLoader,
            String className) {
        try {
            Context context = currentApplicationContext();
            Class<?> managerClass = classLoader.loadClass(className);
            Method entry = null;
            for (Method method : managerClass.getDeclaredMethods()) {
                if ("loadMediaDataInBg".equals(method.getName())
                        && method.getParameterCount() == 4
                        && method.getParameterTypes()[0] == String.class
                        && method.getParameterTypes()[1] == StatusBarNotification.class
                        && method.getParameterTypes()[2] == String.class
                        && method.getParameterTypes()[3] == boolean.class) {
                    entry = method;
                    break;
                }
            }
            if (entry == null) {
                // C17 loads the same notification through onNotificationAddedEx; its second
                // parameter is the same StatusBarNotification the handler already consumes.
                entry = findDeclaredMethodOrNull(
                        managerClass,
                        "onNotificationAddedEx",
                        String.class,
                        StatusBarNotification.class);
            }
            if (entry == null) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "Skipped KuWo car-lyric load hook: no notification metadata entry");
                return;
            }
            entry.setAccessible(true);
            hook(entry)
                    .setId(HOOK_ID_MEDIA_DATA_LOAD_METADATA)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onLegacyMediaDataLoad);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked notification media loader for KuWo car-lyric identity: "
                    + managerClass.getName() + "#" + entry.getName());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook legacy media data loader: " + t);
        }
    }

    private static Method findDeclaredMethodOrNull(
            Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Object onLegacyMediaDataLoad(XposedInterface.Chain chain) throws Throwable {
        Object notificationArg = chain.getArg(1);
        if (!(notificationArg instanceof android.service.notification.StatusBarNotification)) {
            return chain.proceed();
        }
        android.service.notification.StatusBarNotification notification =
                (android.service.notification.StatusBarNotification) notificationArg;
        if (!KuWoMediaIdentityPolicy.isPlayerPackage(notification.getPackageName())) {
            return chain.proceed();
        }
        Notification nativeNotification = notification.getNotification();
        Bundle extras = nativeNotification.extras;
        MediaMetadata metadata = extras == null
                ? null
                : extras.getParcelable("android.mediaSession.metadata");
        Icon notificationCover = nativeNotification.getLargeIcon();
        if (notificationCover != null && isPlausibleKuWoCoverIcon(notificationCover)) {
            rememberKuWoArtworkSnapshots(metadata, notificationCover);
        }
        String title = firstNonEmpty(
                getText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        String artist = firstNonEmpty(
                getText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
        if (!KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                lastSystemUiSongName,
                lastSystemUiArtistName,
                title,
                artist)) {
            return chain.proceed();
        }
        if (metadata == null) {
            return chain.proceed();
        }
        // Never rewrite the Notification object that the notification pipeline still owns.
        // Clone the notification, copy its extras, and pass the cloned SBN to the vendor method.
        Notification copiedNotification;
        android.os.Parcel parcel = android.os.Parcel.obtain();
        try {
            nativeNotification.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            copiedNotification = Notification.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
        Bundle copiedExtras = copiedNotification.extras == null
                ? new Bundle()
                : new Bundle(copiedNotification.extras);
        copiedExtras.putParcelable(
                "android.mediaSession.metadata",
                buildMetadataWithStableSystemUiIdentity(
                        metadata,
                        lastSystemUiSongName,
                        lastSystemUiArtistName));
        copiedNotification.extras = copiedExtras;
        // getOpPkg() and getUid() are API 29+; below it keep the posting package and an
        // unknown uid. The vendor path that reaches this hook only exists on C17.
        String opPkg = notification.getPackageName();
        int uid = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            opPkg = notification.getOpPkg();
            uid = notification.getUid();
        }
        StatusBarNotification replacement = new StatusBarNotification(
                notification.getPackageName(),
                opPkg,
                notification.getId(),
                notification.getTag(),
                uid,
                0,
                notification.getUserId(),
                copiedNotification,
                notification.getUser(),
                notification.getPostTime());
        Object[] replacementArgs = chain.getArgs().toArray(new Object[0]);
        replacementArgs[1] = replacement;
        if (kuWoRuntime.takeCarLyricNormalizedLogOnce()) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Normalized KuWo car-lyric identity before legacy media data load");
        }
        return chain.proceed(replacementArgs);
    }

    private Object onSystemUiKuWoArtworkLookup(XposedInterface.Chain chain) throws Throwable {
        boolean kuwoLookup = false;
        MediaMetadata effectiveMetadata = null;
        Object[] proceedArgs = null;
        try {
            Object packageNameArg = chain.getArg(1);
            kuwoLookup = packageNameArg instanceof String
                    && KuWoMediaIdentityPolicy.isPlayerPackage((String) packageNameArg);
            if (kuwoLookup && chain.getArg(0) instanceof MediaMetadata) {
                effectiveMetadata = (MediaMetadata) chain.getArg(0);
            }
            if (kuwoLookup && effectiveMetadata != null
                    && KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                    lastSystemUiSongName,
                    lastSystemUiArtistName,
                    firstNonEmpty(
                            getText(effectiveMetadata, MediaMetadata.METADATA_KEY_TITLE),
                            getText(effectiveMetadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)),
                    firstNonEmpty(
                            getText(effectiveMetadata, MediaMetadata.METADATA_KEY_ARTIST),
                            getText(effectiveMetadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)))) {
                effectiveMetadata = buildMetadataWithStableSystemUiIdentity(
                        effectiveMetadata,
                        lastSystemUiSongName,
                        lastSystemUiArtistName);
                proceedArgs = chain.getArgs().toArray(new Object[0]);
                proceedArgs[0] = effectiveMetadata;
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo artwork snapshot preprocessing failed: " + t);
        }

        Object result = HookProceed.once(chain, proceedArgs);
        if (!kuwoLookup) {
            return result;
        }
        try {
            String trackKey = kuWoArtworkSnapshotKey(effectiveMetadata);
            if (TextUtils.isEmpty(trackKey)) {
                return result;
            }
            if (result instanceof Icon && isPlausibleKuWoCoverIcon((Icon) result)) {
                rememberKuWoArtworkSnapshots(effectiveMetadata, (Icon) result);
                return result;
            }
            Icon snapshot = peekKuWoArtworkSnapshot(trackKey);
            if (snapshot != null) {
                if (kuWoRuntime.takeArtworkRestoreLogOnce()) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, "Restored KuWo album artwork from same-track snapshot after"
                            + (result == null ? " empty" : " implausible")
                            + " metadata lookup");
                }
                return snapshot;
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo artwork snapshot handling failed: " + t);
        }
        return result;
    }

    private static String kuWoArtworkSnapshotKey(MediaMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        return KuWoCoverPolicy.artworkSnapshotKey(
                metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                buildTrackKey(
                        firstNonEmpty(
                                getText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)),
                        firstNonEmpty(
                                getText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))));
    }

    private static boolean isPlausibleKuWoCoverIcon(Icon icon) {
        try {
            return KuWoCoverPolicy.isPlausibleCoverIconBitmap(extractIconBitmap(icon));
        } catch (Throwable ignored) {
            return true;
        }
    }

    static Bitmap extractIconBitmap(Icon icon) {
        try {
            android.content.Context context = currentApplicationContext();
            if (context != null) {
                Drawable drawable = icon.loadDrawable(context);
                if (drawable instanceof BitmapDrawable) {
                    return ((BitmapDrawable) drawable).getBitmap();
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the framework-internal accessor below.
        }
        try {
            return (Bitmap) icon.getClass().getMethod("getBitmap").invoke(icon);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void rememberKuWoArtworkSnapshots(MediaMetadata metadata, Icon icon) {
        if (metadata == null || icon == null) {
            return;
        }
        kuWoRuntime.artwork().rememberKeys(
                kuWoArtworkSnapshotKey(metadata),
                buildTrackKey(
                        firstNonEmpty(
                                getText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)),
                        firstNonEmpty(
                                getText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))),
                icon);
    }

    private void rememberKuWoArtworkSnapshot(String trackKey, Icon icon) {
        if (TextUtils.isEmpty(trackKey) || icon == null) {
            return;
        }
        kuWoRuntime.artwork().remember(trackKey, icon);
    }

    private Icon peekKuWoArtworkSnapshot(String trackKey) {
        return kuWoRuntime.artwork().peek(trackKey);
    }

    private Icon peekKuWoArtworkSnapshot(String trackKey, String expectedStrongKey) {
        return kuWoRuntime.artwork().peekIfStrongIdentity(trackKey, expectedStrongKey);
    }

    private void refreshLyricRenderingAfterModeChange(boolean active) {
        int generation = ++lyricModeRebindGeneration;
        cancelActiveLyricRefreshCallbacks(!active);
        if (lyricModeRebindRunnable != null) {
            mainHandler.removeCallbacks(lyricModeRebindRunnable);
        }

        Runnable refresh = () -> {
            if (generation != lyricModeRebindGeneration) {
                return;
            }
            // Mode changes only invalidate custom pixels. SystemUI owns the RecyclerView
            // transaction and its geometry; repeated requestLayout/height passes here used to
            // race the hidden-surface preparation performed during immersive-page cross-fades.
            for (View recycler : snapshotLyricsRecyclerViews()) {
                invalidateLyricsRecyclerDescendants(recycler);
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
        lyricModeRebindRunnable = refresh;
        mainHandler.post(refresh);
    }

    private static long resolveLyricGlowPosition(long position) {
        // The glow and revealed glyphs must use the same playback snapshot. Freezing only
        // the glow during lockscreen/AOD visibility changes makes the two layers resume
        // hundreds of milliseconds apart in both transition directions.
        return position;
    }

    private boolean activateSystemUiLyricModeFromSurface(String reason) {
        return activateSystemUiLyricModeFromSurface(reason, false, true);
    }

    private boolean activateSystemUiLyricModeFromVisibleTextView(
            TextView textView,
            View recycler,
            String reason) {
        if (textView == null
                || recycler == null
                || recycler.getVisibility() != View.VISIBLE
                || !isEffectivelyVisible(textView)) {
            return false;
        }
        rememberActiveTextViewReference(textView);
        return activateSystemUiLyricModeFromSurface(reason, true, true);
    }

    private boolean activateSystemUiLyricModeFromPendingDraw(
            TextView textView,
            View recycler,
            String reason) {
        if (!lyricSurfaceReactivationPending
                || textView == null
                || recycler == null
                || !textView.isAttachedToWindow()
                || !recycler.isAttachedToWindow()
                || recycler.getVisibility() != View.VISIBLE) {
            return false;
        }
        rememberActiveTextViewReference(textView);
        prepareLyricSurfaceFromPendingDraw(recycler, "pending-draw");
        View provisionalRecycler = lyricSurfaceProvisionalRecyclerView.get();
        long now = SystemClock.elapsedRealtime();
        if (provisionalRecycler != recycler
                || now >= lyricSurfaceProvisionalDrawUntilElapsedMs) {
            lyricSurfaceProvisionalRecyclerView = new WeakReference<>(recycler);
            lyricSurfaceProvisionalDrawUntilElapsedMs =
                    now + LYRIC_SURFACE_PROVISIONAL_DRAW_GRACE_MS;
        }
        return activateSystemUiLyricModeFromSurface(reason, true, false);
    }

    private boolean activateSystemUiLyricModeFromSurface(
            String reason,
            boolean confirmedLyricTextView,
            boolean settleSurfaceReactivation) {
        if ((currentWordLyricModel == null && !systemUiHasOfficialLyric)
                || (!confirmedLyricTextView && !hasStableActiveLyricRefreshSurface())) {
            return false;
        }
        if (settleSurfaceReactivation && lyricSurfaceReactivationPending) {
            cancelLyricSurfaceReactivationCallbacks();
        }
        boolean changed = !systemUiLyricModeKeepAwakeActive;
        systemUiLyricModeKeepAwakeActive = true;
        screenTimeout.screenTimeoutPausedByUserPresent = false;
        lastSystemUiLyricModeLogAt = SystemClock.elapsedRealtime();
        if (changed) {
            // Entering the page is not proof that every rebound row has a custom display list.
            // Keep any in-flight handoff until the bound surface is complete (or its existing
            // timeout expires) instead of exposing the first successfully drawn active row.
            maybeLogLyricModeKeepAwake(true);
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Observed active lyric surface from " + reason);
            refreshLyricRenderingAfterModeChange(true);
            mainHandler.post(translationTracker::refreshTranslationActionViewVisibility);
            mainHandler.postDelayed(() -> {
                discoverTranslationActionViewsNearRememberedLyrics();
                translationTracker.refreshTranslationActionViewVisibility();
            }, 960L);
        } else {
            scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS);
        }
        screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
        return true;
    }

    private void deactivateSystemUiLyricModeAfterSurfaceHidden() {
        boolean changed = systemUiLyricModeKeepAwakeActive;
        systemUiLyricModeKeepAwakeActive = false;
        lyricModeRebindGeneration++;
        if (lyricModeRebindRunnable != null) {
            mainHandler.removeCallbacks(lyricModeRebindRunnable);
            lyricModeRebindRunnable = null;
        }
        lastSystemUiLyricModeLogAt = SystemClock.elapsedRealtime();
        cancelActiveLyricRefreshCallbacks(true);
        if ((currentWordLyricModel == null && !systemUiHasOfficialLyric)
                || !hasAttachedLyricsRecyclerView()) {
            cancelLyricSurfaceReactivationCallbacks();
        }
        translationTracker.clearTrackedTranslationActionViews();
        screenTimeout.clearScreenTimeoutLyricEvidence();
        if (changed) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Observed hidden lyric surface; stopping active lyric refresh");
            mainHandler.post(translationTracker::refreshTranslationActionViewVisibility);
        }
        screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private boolean retainLyricModeForTransientSurfaceMiss(String reason) {
        long now = SystemClock.elapsedRealtime();
        long elapsedSinceSurfaceSignal = lastSystemUiLyricModeLogAt <= 0L
                ? -1L
                : now - lastSystemUiLyricModeLogAt;
        boolean retain = LockscreenIntegrationPolicy
                .shouldRetainLyricModeForTransientSurfaceMiss(
                        systemUiLyricModeKeepAwakeActive,
                        currentWordLyricModel != null,
                        lyricSurfaceReactivationPending,
                        elapsedSinceSurfaceSignal,
                        LYRIC_SURFACE_PROVISIONAL_DRAW_GRACE_MS);
        if (!retain) {
            return false;
        }
        scheduleActiveLyricRefresh(ACTIVE_LYRIC_RETRY_DELAY_MS);
        if (now - lastTransientLyricSurfaceMissLogAt >= 500L) {
            lastTransientLyricSurfaceMissLogAt = now;
            info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "Deferred lyric surface deactivation during transient visibility miss"
                    + ", reason=" + reason
                    + ", recyclerAttached=" + hasAttachedLyricsRecyclerView()
                    + ", reactivationPending=" + lyricSurfaceReactivationPending
                    + ", elapsedSinceSurfaceSignal=" + elapsedSinceSurfaceSignal);
        }
        return true;
    }

    private void maybeLogLyricModeKeepAwake(boolean active) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSystemUiLyricModeStateLogAt < 1_500L) {
            return;
        }
        lastSystemUiLyricModeStateLogAt = now;
        info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Lockscreen lyric UI keep-awake " + (active ? "ON" : "OFF"));
    }

    @SuppressLint("WrongConstant")
    private Object onSystemUiLoadLyricInBg(XposedInterface.Chain chain) throws Throwable {
        long loadEpoch = systemUiLyricPublicationGate.beginLoad();
        rememberOplusMediaDataManager(chain.getThisObject());
        Object[] normalizedArgs = null;
        try {
            Object metadataArg = chain.getArg(1);
            if (metadataArg instanceof MediaMetadata) {
                MediaMetadata metadata = (MediaMetadata) metadataArg;
                String title = firstNonEmpty(
                        getText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                        getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
                String artist = firstNonEmpty(
                        getText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                        getText(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                        getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
                String album = getText(metadata, MediaMetadata.METADATA_KEY_ALBUM);
                String packageName = findPlayerPackageInArgs(chain.getArgs());
                String artworkPackage = firstNonEmpty(packageName, currentLyricProviderPackage);
                if (isLxPlayerPackage(artworkPackage)) {
                    MediaMetadata artworkMetadata = metadata;
                    artworkProbe("SYSTEMUI_METADATA", () -> "package=" + artworkPackage
                            + " | " + ArtworkDiagnostics.describeMetadata(artworkMetadata));
                }
                if (SystemUiLoadLyricPolicy.shouldNormalizeKuWoCarLyricIdentity(
                        packageName,
                        lastSystemUiSongName,
                        lastSystemUiArtistName,
                        title,
                        artist)) {
                    metadata = buildMetadataWithStableSystemUiIdentity(
                            metadata,
                            lastSystemUiSongName,
                            lastSystemUiArtistName);
                    title = lastSystemUiSongName;
                    artist = lastSystemUiArtistName;
                    normalizedArgs = chain.getArgs().toArray(new Object[0]);
                    normalizedArgs[1] = metadata;
                    info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED,
                            "Normalized KuWo car-lyric metadata identity for SystemUI load");
                }
                lastSystemUiMediaId = nullToEmpty(
                        metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
                String lyricInfo = metadata.getString(OPLUS_LYRIC_INFO_KEY);
                LyricInfoContract.NormalizedPayload originalPayload =
                        LyricInfoContract.parseLyricInfo(lyricInfo);
                LyricInfoContract.NormalizedPayload normalizedPayload = applyModuleContentCleanup(
                        originalPayload,
                        title,
                        artist,
                        false);
                normalizedPayload = LyricInfoContract.normalizeOfficialLyricInfo(
                        normalizedPayload.lyricInfo);
                LyricInfoContract.Payload payload = normalizedPayload.payload;
                SystemUiLoadLyricPolicy.Action loadAction =
                        SystemUiLoadLyricPolicy.decide(payload != null);
                if (loadAction == SystemUiLoadLyricPolicy.Action.ACCEPT_PAYLOAD) {
                    // Keep the pre-cleanup metadata for the settings replay. The vendor will run
                    // this hook again, so it must see the original lyricInfo and apply the
                    // current cleanup policy instead of re-cleaning an already cleaned input.
                    MediaMetadata replayMetadata = metadata;
                    MediaMetadata effectiveMetadata = metadata;
                    if (SystemUiLoadLyricPolicy.shouldRewriteMetadataLyricInfo(
                            lyricInfo,
                            normalizedPayload.lyricInfo)) {
                        MediaMetadata normalizedMetadata =
                                buildMetadataWithLyricInfoPreservingArtwork(
                                        metadata,
                                        normalizedPayload.lyricInfo,
                                        title,
                                        artist);
                        normalizedArgs = chain.getArgs().toArray(new Object[0]);
                        normalizedArgs[1] = normalizedMetadata;
                        effectiveMetadata = normalizedMetadata;
                    }
                    boolean committed;
                    synchronized (wordLyricModelCacheLock) {
                        committed = cacheSystemUiLyricModelLocked(
                                chain,
                                payload,
                                originalPayload == null ? null : originalPayload.payload,
                                loadEpoch,
                                packageName,
                                title,
                                artist);
                        if (committed) {
                            lastSystemUiSongName = title;
                            lastSystemUiArtistName = artist;
                            lastSystemUiAlbumName = album;
                            rememberSystemUiLyricLoadContext(
                                    chain.getThisObject(),
                                    chain.getArg(0),
                                    replayMetadata,
                                    packageName,
                                    title,
                                    artist,
                                    payloadTrackKey(payload));
                        }
                    }
                    if (committed) {
                        maybeLogOfficialLyricPayload(
                                payload,
                                normalizedPayload.changed,
                                effectiveMetadata);
                    }
                } else {
                    synchronized (wordLyricModelCacheLock) {
                        if (systemUiLyricPublicationGate.canCommit(loadEpoch)) {
                            lastSystemUiSongName = title;
                            lastSystemUiArtistName = artist;
                            lastSystemUiAlbumName = album;
                            rememberSystemUiLyricLoadContext(
                                    chain.getThisObject(),
                                    chain.getArg(0),
                                    metadata,
                                    packageName,
                                    title,
                                    artist,
                                    buildTrackKey(title, artist));
                            clearCurrentLyricProvider(title, artist);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            error("Failed to read SystemUI lyricInfo", t);
        }
        return normalizedArgs == null ? chain.proceed() : chain.proceed(normalizedArgs);
    }
    private LyricInfoContract.NormalizedPayload applyModuleContentCleanup(
            LyricInfoContract.NormalizedPayload normalized,
            String fallbackTitle,
            String fallbackArtist,
            boolean rememberSnapshot) {
        return applyModuleContentCleanup(
                normalized,
                fallbackTitle,
                fallbackArtist,
                rememberSnapshot,
                lyricContentCleanupConfig);
    }

    private LyricInfoContract.NormalizedPayload applyModuleContentCleanup(
            LyricInfoContract.NormalizedPayload normalized,
            String fallbackTitle,
            String fallbackArtist,
            boolean rememberSnapshot,
            LyricContentCleanupConfig cleanupConfig) {
        if (normalized == null || normalized.payload == null
                || normalized.payload.snapshotPositionMillis >= 0L) {
            // Event-driven row indices must survive content cleanup unchanged.
            return normalized;
        }
        try {
            LyricContentCleanupConfig effectiveConfig = cleanupConfig == null
                    ? LyricContentCleanupConfig.defaults()
                    : cleanupConfig;
            LyricInfoContract.Payload payload = normalized.payload;
            String trackKey = firstNonEmpty(
                    payload.trackKey,
                    buildTrackKey(
                            firstNonEmpty(payload.songName, fallbackTitle),
                            firstNonEmpty(payload.artist, fallbackArtist)));
            String originalDisplay = payload.lyric;
            String originalRaw = payload.rawLyric;
            if (rememberSnapshot) {
                currentCleanupSnapshotTrackKey = trackKey;
                currentCleanupSnapshotDisplayLyric = originalDisplay;
                currentCleanupSnapshotRawLyric = firstNonEmpty(originalRaw, originalDisplay);
                currentCleanupSnapshotTitle = firstNonEmpty(fallbackTitle, payload.songName);
                currentCleanupSnapshotArtist = firstNonEmpty(fallbackArtist, payload.artist);
            }
            LyricOpeningCleanup.Result display = LyricOpeningCleanup.clean(
                    originalDisplay,
                    originalRaw,
                    trackKey,
                    effectiveConfig);
            LyricOpeningCleanup.Result raw = LyricInfoContract.containsTimedLrc(originalRaw)
                    ? LyricOpeningCleanup.clean(
                    originalRaw,
                    trackKey,
                    effectiveConfig)
                    : new LyricOpeningCleanup.Result(originalRaw, new ArrayList<>());
            LyricOpeningCleanup.Result translation = LyricOpeningCleanup.clean(
                    payload.translationLyric,
                    originalRaw,
                    trackKey,
                    effectiveConfig);
            if (TextUtils.equals(display.timedText, payload.lyric)
                    && TextUtils.equals(raw.timedText, payload.rawLyric)
                    && TextUtils.equals(translation.timedText, payload.translationLyric)) {
                return normalized;
            }
            JSONObject object = new JSONObject(normalized.lyricInfo);
            if (LyricInfoContract.containsTimedLrc(display.timedText)) {
                object.put(LyricInfoContract.JSON_LYRIC, display.timedText);
            }
            if (LyricInfoContract.containsTimedLrc(raw.timedText)) {
                object.put(LyricInfoContract.JSON_RAW_LYRIC, raw.timedText);
            }
            LyricInfoContract.replaceTranslationLyric(object, translation.timedText);
            return LyricInfoContract.parseLyricInfo(object.toString());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "opening-cleanup-invalid",
                    "Kept original timed lyrics because opening cleanup failed");
            return normalized;
        }
    }

    private void acceptCurrentLyricProvider(
            XposedInterface.Chain chain,
            LyricInfoContract.Payload payload,
            String observedPackage,
            String observedTitle,
            String observedArtist) {
        LyricInfoContract.Payload previousPayload = currentLyricProviderPayload;
        String nextClockTrackKey = payloadTrackKey(payload);
        boolean nativeClockTrackChanged = LockscreenIntegrationPolicy
                .shouldResetNativePlaybackClock(
                        isNativeV5Payload(payload),
                        playbackClockTrackKey,
                        nextClockTrackKey);
        if (nativeClockTrackChanged || isLyricPayloadTrackChanged(previousPayload, payload)) {
            resetSystemUiPlaybackPositionForLyricTrackChange(payload);
        }
        currentLyricProviderPayload = payload;
        if (payload.snapshotPositionMillis >= 0L) {
            info(BridgeDebugArea.LYRIC, BridgeEvents.DETAIL,
                    "Snapshot accepted; current=" + payload.snapshotPositionMillis / 1000L);
        }
        currentLyricObservedTrackKey = buildTrackKey(observedTitle, observedArtist);
        if (isNativeV5Payload(payload) && !TextUtils.isEmpty(nextClockTrackKey)) {
            playbackClockTrackKey = nextClockTrackKey;
        }
        String packageName = !TextUtils.isEmpty(observedPackage)
                ? observedPackage
                : chain == null ? "" : findPlayerPackageInArgs(chain.getArgs());
        if (!TextUtils.isEmpty(packageName)) {
            bindCurrentLyricProviderPackage(packageName, "lyricInfo metadata");
            scheduleSystemUiPlaybackControllerDiscovery(packageName);
        } else if (isLyricPayloadTrackChanged(previousPayload, payload)) {
            currentLyricProviderPackage = "";
        }
    }

    private void restoreProviderWordLyricSurfaceAfterOfficialSuppression(String reason) {
        screenTimeout.screenTimeoutLyricEvidenceGraceUntilElapsedMs =
                SystemClock.elapsedRealtime() + SCREEN_TIMEOUT_MODEL_EVIDENCE_GRACE_MS;
        mainHandler.post(() -> {
            restoreSuppressedLyricsRecyclerViews(false);
            primeRememberedLyricsRecyclerViews(reason);
            for (View recycler : snapshotLyricsRecyclerViews()) {
                scheduleLyricSurfaceRenderPass(
                        recycler,
                        reason);
            }
            invalidateRememberedLyricViews();
            translationTracker.refreshTranslationActionViewVisibility();
        });
    }

    private void beginScreenStateLyricSettleWindow() {
        suppressOfficialRowScaleAnimations(LYRIC_RECYCLER_SCREEN_STATE_SETTLE_MS);
        beginLyricRecyclerSettleWindow(
                lastLyricsRecyclerIndex,
                LYRIC_RECYCLER_SCREEN_STATE_SETTLE_MS);
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (shouldModulePositionLyricsRecycler()) {
                // Preserve the known-good one-shot AOD stabilization. On the bright lock screen,
                // the official controller owns its SmoothScroller and child transforms.
                stabilizeLyricsRecyclerScroll(recycler, "screen-state");
            }
            invalidateLyricsRecyclerDescendants(recycler);
            recycler.postInvalidateOnAnimation();
        }
        if (systemUiLyricModeKeepAwakeActive) {
            TextView activeView = firstActiveLyricTextView();
            if (activeView != null) {
                scheduleActiveLyricRefresh(activeView, ACTIVE_LYRIC_RETRY_DELAY_MS);
            } else {
                scheduleActiveLyricRefresh(ACTIVE_LYRIC_RETRY_DELAY_MS);
            }
        }
    }

    private void beginLyricRecyclerSettleWindow(int officialIndex, long durationMs) {
        long now = SystemClock.elapsedRealtime();
        lyricRecyclerSettleUntilElapsedMs = Math.max(
                lyricRecyclerSettleUntilElapsedMs,
                now + Math.max(ACTIVE_LYRIC_RETRY_DELAY_MS, durationMs));
        if (officialIndex >= 0) {
            lyricRecyclerSettleOfficialIndex = officialIndex;
        }
    }

    private boolean isLyricRecyclerSettleWindowActive() {
        return SystemClock.elapsedRealtime() < lyricRecyclerSettleUntilElapsedMs;
    }

    private void suppressOfficialRowScaleAnimations(long durationMs) {
        long now = SystemClock.elapsedRealtime();
        officialRowScaleAnimationSuppressUntilElapsedMs = Math.max(
                officialRowScaleAnimationSuppressUntilElapsedMs,
                now + Math.max(0L, durationMs));
    }

    private boolean shouldAnimateOfficialRowScale(TextView textView) {
        return textView != null
                && textView.isAttachedToWindow()
                && textView.getWindowVisibility() == View.VISIBLE
                && SystemClock.elapsedRealtime() >= officialRowScaleAnimationSuppressUntilElapsedMs
                && !isAodLowFrameRateLyricMode();
    }

    private boolean isAodLowFrameRateLyricMode() {
        if (aodLowFrameRateLyricMode || screenTimeout.screenTimeoutPausedByScreenOff) {
            return true;
        }
        PowerManager powerManager = screenTimeout.screenTimeoutPowerManager;
        return powerManager != null && !powerManager.isInteractive();
    }

    private int lyricTextLengthLimit() {
        // 1600 Unicode code points plus normalization spaces and surrogate pairs.
        return hasSentenceWindow() ? 4800 : 240;
    }

    private boolean hasSentenceWindow() {
        LyricInfoContract.Payload payload = currentLyricProviderPayload;
        return payload != null && payload.snapshotPositionMillis >= 0L;
    }

    private boolean shouldModulePositionLyricsRecycler() {
        if (hasSentenceWindow()) return true;
        return LyricsRecyclerPolicy.shouldModulePosition(
                aodLowFrameRateLyricMode,
                screenTimeout.isScreenInteractiveForWakeLock(),
                systemUiOwnsNativeLyricsRecycler());
    }

    private boolean systemUiOwnsNativeLyricsRecycler() {
        if (hasSentenceWindow()) return false;
        LyricInfoContract.Payload payload = currentLyricProviderPayload;
        if (payload != null) {
            return isNativeV5Payload(payload);
        }
        return currentWordLyricModel != null;
    }

    private static boolean isNativeV5Payload(LyricInfoContract.Payload payload) {
        return payload != null;
    }

    private void setAodLowFrameRateLyricMode(boolean enabled) {
        aodLowFrameRateLyricMode = enabled;
        officialLyricTextRenderer.setAodLowFrameRateMode(enabled);
    }

    private boolean isLyricPayloadTrackChanged(
            LyricInfoContract.Payload previous,
            LyricInfoContract.Payload next) {
        if (previous == null || next == null) {
            return false;
        }
        String previousKey = payloadTrackKey(previous);
        String nextKey = payloadTrackKey(next);
        return !TextUtils.isEmpty(previousKey)
                && !TextUtils.isEmpty(nextKey)
                && !previousKey.equals(nextKey);
    }

    private void resetSystemUiPlaybackPositionForLyricTrackChange(
            LyricInfoContract.Payload payload) {
        resetSystemUiPlaybackPositionForTrackChange(
                payload == null ? "" : payload.songName,
                payload == null ? "" : payload.artist,
                "payload track change");
    }

    private void resetSystemUiPlaybackPositionForTrackChange(
            String title,
            String artist,
            String reason) {
        // A no-lyric intermediate track has no payload that could advance the native
        // session generation. Invalidate the previous payload binding here so returning
        // to the same cached song starts a fresh visual clock instead of inheriting the
        // intermediate track's elapsed offset.
        playbackClockTrackKey = "";
        clearSeedlingActiveLyricHint();
        clearActiveRendererTargets();
        activeRendererWordLine = null;
        activeLyricLine = "";
        activeLyricLineTimeMs = -1L;
        long now = SystemClock.elapsedRealtime();
        lastComputedPositionMs = 0L;
        lastComputedPositionElapsedMs = lastPlaybackIsPlaying
                ? now
                : -1L;
        lastLyricsRecyclerIndex = 0;
        resetAdaptiveLineTimedRecyclerFollow();
        lastPrimedLyricsRecyclerView = new WeakReference<>(null);
        lastPrimedLyricsRecyclerIndex = -1;
        lyricTrackPositionResetGuardStartedAtElapsedMs = now;
        lastOfficialLyricIndexObservedAtElapsedMs = -1L;
        lyricTrackPositionResetGuardUntilElapsedMs =
                now + SYSTEMUI_TRACK_RESET_POSITION_GUARD_MS;
        if (now - lastLyricTrackPositionResetLogAt >= 1_500L) {
            lastLyricTrackPositionResetLogAt = now;
            info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED,
                    "Reset SystemUI lyric playback position after " + reason
                            + ", track=" + SensitiveFieldRedactor.trackHash(
                            buildTrackKey(title, artist)));
        }
    }

    private void extendLyricTrackPositionResetGuard() {
        long now = SystemClock.elapsedRealtime();
        if (lyricTrackPositionResetGuardStartedAtElapsedMs < 0L
                || now > lyricTrackPositionResetGuardUntilElapsedMs) {
            lyricTrackPositionResetGuardStartedAtElapsedMs = now;
        }
        lyricTrackPositionResetGuardUntilElapsedMs = Math.max(
                lyricTrackPositionResetGuardUntilElapsedMs,
                now + SYSTEMUI_TRACK_RESET_POSITION_GUARD_MS);
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
        applyTranslationPreferenceForPackage(packageName);
        info(BridgeDebugArea.LYRIC, BridgeEvents.DETAIL, "Accepted " + moduleManagedPlayerKind(packageName)
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
                || !WordLyricRenderSupport.normalizeLine(payload.songName).equals(WordLyricRenderSupport.normalizeLine(songName))) {
            return false;
        }
        bindCurrentLyricProviderPackage(packageName, "matching SystemUI media data");
        return true;
    }

    private boolean shouldRetainLyricModelDuringTransientLyricInfoMiss(
            String title,
            String artist) {
        LyricInfoContract.Payload payload = currentLyricProviderPayload;
        String boundTrackKey = currentLyricObservedTrackKey;
        if (payload == null || TextUtils.isEmpty(boundTrackKey)) {
            return false;
        }
        if (!LockscreenIntegrationPolicy.canRetainIdentityBoundPayload(
                boundTrackKey,
                TextUtils.isEmpty(title) ? "" : buildTrackKey(title, artist),
                TextUtils.isEmpty(lastSystemUiSongName)
                        ? ""
                        : buildTrackKey(lastSystemUiSongName, lastSystemUiArtistName))) {
            return false;
        }
        return currentWordLyricModel != null
                && lastSystemUiPackageSupported
                && payload != null;
    }

    private void clearCurrentLyricProvider() {
        clearCurrentLyricProvider("", "");
    }

    private void clearCurrentLyricProvider(String title, String artist) {
        if (shouldRetainLyricModelDuringTransientLyricInfoMiss(title, artist)) {
            screenTimeout.screenTimeoutLyricEvidenceGraceUntilElapsedMs =
                    SystemClock.elapsedRealtime() + SCREEN_TIMEOUT_MODEL_EVIDENCE_GRACE_MS;
            long now = SystemClock.elapsedRealtime();
            if (now - lastTransientLyricInfoMissLogAt >= 1_500L) {
                lastTransientLyricInfoMissLogAt = now;
                info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Retained current SystemUI word lyric model during transient lyricInfo miss");
            }
            screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }
        if (!TextUtils.isEmpty(title)
                && currentLyricProviderPayload != null
                && !payloadMatchesTrack(currentLyricProviderPayload, title, artist)) {
            clearSystemUiLyricModelForTrackChange(title, artist);
            currentLyricProviderPackage = "";
            lastSystemUiPackageSupported = false;
            return;
        }
        if (currentWordLyricModel != null && hasAttachedLyricsRecyclerView()) {
            beginOfficialLyricTrackHandoff("lyricInfo temporarily unavailable");
        }
        traceLyricModelState("provider-cleared", currentWordLyricModel);
        currentLyricProviderPayload = null;
        currentLyricObservedTrackKey = "";
        currentLyricProviderPackage = "";
        lastSystemUiPackageSupported = false;
        systemUiHasOfficialLyric = false;
        CompatibilityStatus.global().invalidateLyricObservations();
        currentWordLyricModel = null;
        clearCommittedLyricRenderModel();
        currentWordLyricModelSignature = "";
        currentWordLyricModelTrackKey = "";
        currentCleanupSnapshotTrackKey = "";
        currentCleanupSnapshotRawLyric = "";
        currentCleanupSnapshotDisplayLyric = "";
        currentCleanupSnapshotTitle = "";
        currentCleanupSnapshotArtist = "";
        latestSystemUiLyricLoadContext = null;
        lastSystemUiMediaId = "";
        mainHandler.post(() -> {
            if (currentWordLyricModel == null) deactivateSystemUiLyricModeAfterSurfaceHidden();
        });
        mainHandler.post(this::clearSystemUiPlaybackControllerBinding);
        mainHandler.post(translationTracker::refreshTranslationActionViewVisibility);
        screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
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
        if (isLxPlayerPackage(packageName)) {
            artworkProbe("SEEDLING_BUNDLE", () -> ArtworkDiagnostics.describeSeedlingBundle(mediaData));
        }
        String songName = charSequenceToString(mediaData.getCharSequence("songName"));
        if (!isCurrentLyricProviderPackage(packageName)
                && !tryBindCurrentLyricProvider(packageName, songName)) {
            return false;
        }

        String artistName = charSequenceToString(mediaData.getCharSequence("artist"));
        boolean hasSystemUiTrackTitle = !TextUtils.isEmpty(songName);
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
        String previousTrackKey = buildTrackKey(
                lastSystemUiSongName,
                lastSystemUiArtistName);
        String nextTrackKey = buildTrackKey(songName, artistName);
        boolean systemUiTrackIdentityChanged = hasSystemUiTrackTitle
                && !TextUtils.isEmpty(nextTrackKey)
                && !nextTrackKey.equals(previousTrackKey);
        if (systemUiTrackIdentityChanged) {
            lastSystemUiTrackIdentityChangedAtElapsedMs = SystemClock.elapsedRealtime();
        }
        boolean systemUiTrackChanged = hasSystemUiTrackTitle
                && lastSystemUiPackageSupported
                && !TextUtils.isEmpty(lastSystemUiSongName)
                && !previousTrackKey.equals(nextTrackKey);
        if (systemUiTrackChanged
                && !payloadMatchesTrack(currentLyricProviderPayload, songName, artistName)) {
            clearSystemUiLyricModelForTrackChange(songName, artistName);
        }
        if (systemUiTrackChanged) {
            resetSystemUiPlaybackPositionForTrackChange(
                    songName,
                    artistName,
                    "media track change");
            if (isPlaybackStateInMotion(state) && storedPosition > 3_000L) {
                storedPosition = 0L;
                computedPosition = 0L;
            }
        }
        rememberSeedlingActiveLyric(
                charSequenceToString(mediaData.getCharSequence("currentLyric")),
                computedPosition);
        lastSystemUiPackageSupported = true;
        if (hasSystemUiTrackTitle) {
            lastSystemUiSongName = songName;
            lastSystemUiArtistName = artistName;
        }

        rememberSystemUiPlaybackState(
                state,
                storedPosition,
                computedPosition,
                speed,
                lastPositionUpdateTime);
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
        } else if (!isModuleManagedPlayerPackage(packageName)) {
            return;
        }

        MediaController controller = (MediaController) controllerObject;
        ensureSystemUiPlaybackControllerCallback(packageName, controller);
        PlaybackState playbackState = controller.getPlaybackState();
        if (playbackState == null) {
            return;
        }
        rememberSystemUiPlaybackState(playbackState);
    }

    private final class PlaybackControllerBinding {
        final MediaController controller;
        final Object token;
        final String packageName;
        final long epoch;
        final MediaController.Callback callback;

        PlaybackControllerBinding(
                MediaController controller,
                Object token,
                String packageName,
                long epoch) {
            this.controller = controller;
            this.token = token;
            this.packageName = packageName;
            this.epoch = epoch;
            this.callback = new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    onBoundPlaybackStateChanged(
                            PlaybackControllerBinding.this,
                            state);
                }

                @Override
                public void onSessionDestroyed() {
                    onBoundPlaybackSessionDestroyed(PlaybackControllerBinding.this);
                }
            };
        }

        boolean matches(MediaController candidate, Object candidateToken, String candidatePackage) {
            return packageName.equals(candidatePackage)
                    && (controller == candidate || sameSessionToken(token, candidateToken));
        }
    }

    private void onBoundPlaybackStateChanged(
            PlaybackControllerBinding source,
            PlaybackState state) {
        if (state == null || !isCurrentPlaybackControllerBinding(source)) {
            return;
        }
        if (!TextUtils.isEmpty(currentLyricProviderPackage)
                && !source.packageName.equals(currentLyricProviderPackage)) {
            return;
        }
        rememberSystemUiPlaybackState(state);
        invalidateRememberedLyricViews();
        TextView activeView = firstActiveLyricTextView();
        if (activeView != null) {
            scheduleActiveLyricRefresh(activeView, ACTIVE_LYRIC_FRAME_DELAY_MS);
        }
    }

    private void onBoundPlaybackSessionDestroyed(PlaybackControllerBinding source) {
        synchronized (systemUiPlaybackControllerLock) {
            if (systemUiPlaybackControllerBinding == source) {
                systemUiPlaybackControllerBinding = null;
            }
        }
    }

    private boolean isCurrentPlaybackControllerBinding(PlaybackControllerBinding source) {
        synchronized (systemUiPlaybackControllerLock) {
            return source != null
                    && systemUiPlaybackControllerBinding == source
                    && LockscreenIntegrationPolicy.isCurrentPlaybackControllerEvent(
                    source.epoch,
                    nextSystemUiPlaybackControllerEpoch,
                    source.packageName,
                    systemUiPlaybackControllerBinding == null
                            ? ""
                            : systemUiPlaybackControllerBinding.packageName,
                    systemUiPlaybackControllerBinding != null
                            && sameSessionToken(
                            source.token,
                            systemUiPlaybackControllerBinding.token));
        }
    }

    private static boolean sameSessionToken(Object left, Object right) {
        return left == right || (left != null && left.equals(right));
    }

    private void scheduleSystemUiPlaybackControllerDiscovery(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        PlaybackControllerBinding existing;
        synchronized (systemUiPlaybackControllerLock) {
            existing = systemUiPlaybackControllerBinding;
        }
        if (existing != null && packageName.equals(existing.packageName)) {
            rememberSystemUiPlaybackState(existing.controller.getPlaybackState());
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (packageName.equals(lastPlaybackControllerDiscoveryPackage)
                && lastPlaybackControllerDiscoveryElapsedMs >= 0L
                && now - lastPlaybackControllerDiscoveryElapsedMs < 750L) {
            return;
        }
        lastPlaybackControllerDiscoveryPackage = packageName;
        lastPlaybackControllerDiscoveryElapsedMs = now;
        mainHandler.post(() -> discoverSystemUiPlaybackController(packageName));
    }

    @SuppressLint("MissingPermission")
    private void discoverSystemUiPlaybackController(String packageName) {
        try {
            PlaybackControllerBinding existing;
            synchronized (systemUiPlaybackControllerLock) {
                existing = systemUiPlaybackControllerBinding;
            }
            if (existing != null && packageName.equals(existing.packageName)) {
                rememberSystemUiPlaybackState(existing.controller.getPlaybackState());
                return;
            }
            Context context = currentApplicationContext();
            if (context == null) {
                return;
            }
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(
                    Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return;
            }
            List<MediaController> controllers = manager.getActiveSessions(null);
            ArrayList<String> packages = new ArrayList<>(controllers.size());
            for (MediaController controller : controllers) {
                packages.add(controller == null ? "" : controller.getPackageName());
            }
            int selectedIndex = LockscreenIntegrationPolicy.uniquePackageMatchIndex(
                    packages,
                    packageName);
            if (selectedIndex < 0 || selectedIndex >= controllers.size()) {
                info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL,
                        "Skipped playback controller discovery for package="
                                + packageName
                                + ", candidates=" + packages.size());
                return;
            }
            rememberMediaControllerPlaybackState(packageName, controllers.get(selectedIndex));
        } catch (Throwable t) {
            error("Failed to discover active MediaController for " + packageName, t);
        }
    }

    private void ensureSystemUiPlaybackControllerCallback(
            String packageName,
            MediaController controller) {
        if (TextUtils.isEmpty(packageName) || controller == null) {
            return;
        }
        Object token;
        try {
            token = controller.getSessionToken();
        } catch (Throwable t) {
            error("Failed to read MediaController session token", t);
            return;
        }
        PlaybackControllerBinding previous;
        PlaybackControllerBinding candidate;
        synchronized (systemUiPlaybackControllerLock) {
            previous = systemUiPlaybackControllerBinding;
            if (previous != null && previous.matches(controller, token, packageName)) {
                return;
            }
            candidate = new PlaybackControllerBinding(
                    controller,
                    token,
                    packageName,
                    ++nextSystemUiPlaybackControllerEpoch);
            systemUiPlaybackControllerBinding = candidate;
        }

        if (previous != null) {
            try {
                previous.controller.unregisterCallback(previous.callback);
            } catch (Throwable ignored) {
                // A released/remote session may already have removed the callback.
            }
        }
        try {
            controller.registerCallback(candidate.callback, mainHandler);
        } catch (Throwable t) {
            synchronized (systemUiPlaybackControllerLock) {
                if (systemUiPlaybackControllerBinding == candidate) {
                    systemUiPlaybackControllerBinding = null;
                }
            }
            error("Failed to register MediaController playback callback", t);
            return;
        }
        if (!isCurrentPlaybackControllerBinding(candidate)) {
            try {
                controller.unregisterCallback(candidate.callback);
            } catch (Throwable ignored) {
                // A newer binding already owns playback events.
            }
            return;
        }
        info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED,
                "Registered MediaController playback callback for package=" + packageName
                        + ", epoch=" + candidate.epoch
                        + ", token=" + SensitiveFieldRedactor.trackHash(String.valueOf(token)));
    }

    private void clearSystemUiPlaybackControllerBinding() {
        PlaybackControllerBinding previous;
        synchronized (systemUiPlaybackControllerLock) {
            previous = systemUiPlaybackControllerBinding;
            if (previous == null) {
                return;
            }
            systemUiPlaybackControllerBinding = null;
            nextSystemUiPlaybackControllerEpoch++;
        }
        try {
            previous.controller.unregisterCallback(previous.callback);
        } catch (Throwable ignored) {
            // Released sessions may already have removed the callback.
        }
    }

    private void rememberSystemUiPlaybackState(PlaybackState playbackState) {
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
        rememberSystemUiPlaybackState(
                state,
                storedPosition,
                computedPosition,
                speed,
                playbackState.getLastPositionUpdateTime());
    }

    private void rememberSystemUiPlaybackState(
            int state,
            long storedPosition,
            long computedPosition,
            float speed,
            long lastPositionUpdateTime) {
        long previousPosition = estimatePlaybackPositionMillis();
        long now = SystemClock.elapsedRealtime();
        if (shouldIgnoreStalePlaybackPositionAfterTrackReset(
                state,
                previousPosition,
                storedPosition,
                computedPosition,
                lastPositionUpdateTime,
                now)) {
            lastPlaybackIsPlaying = isPlaybackStateInMotion(state) && isFiniteNonZero(speed);
            lastPlaybackSpeed = isFiniteNonZero(speed) ? speed : 0f;
            lastComputedPositionMs = 0L;
            lastComputedPositionElapsedMs = now;
            lastSystemUiPlaybackState = state;
            maybeLogIgnoredStalePlaybackPositionAfterTrackReset(
                    storedPosition,
                    computedPosition,
                    previousPosition);
            screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }
        boolean likelyTrackRestart = isPlaybackStateInMotion(state)
                && LockscreenIntegrationPolicy.isLikelyPlaybackTrackRestart(
                previousPosition,
                computedPosition);
        if (likelyTrackRestart) {
            clearSeedlingActiveLyricHint();
            clearActiveRendererTargets();
            activeRendererWordLine = null;
            beginOfficialLyricTrackHandoff("playback position reset");
        }
        long rawComputedPosition = computedPosition;
        computedPosition = LockscreenIntegrationPolicy.smoothPlayingPosition(
                lastSystemUiPlaybackState,
                state,
                previousPosition,
                computedPosition,
                LyricTimingTuningConstants.LyricGeneral.PLAYBACK_SMALL_CORRECTION_MS);
        if (computedPosition != rawComputedPosition) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL,
                    "Smoothed small playback clock correction, previousPosition="
                            + previousPosition
                            + ", rawPosition=" + rawComputedPosition
                            + ", acceptedPosition=" + computedPosition);
        }
        long nextPosition = storedPosition >= 0 ? storedPosition : computedPosition;
        if (isPlaybackStateInMotion(state) && isFiniteNonZero(speed) && computedPosition >= 0) {
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
        long activePosition = resolvePlaybackStateActivePosition(
                state,
                nextPosition,
                computedPosition);
        boolean playbackPositionJump = shouldObserveLyricsPlaybackPositionJump(
                state,
                previousPosition,
                activePosition);
        if (playbackPositionJump) {
            observeLyricsPlaybackPositionJump(previousPosition, activePosition);
        }
        screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private static long resolvePlaybackStateActivePosition(
            int state,
            long nextPosition,
            long computedPosition) {
        if (isPlaybackStateInMotion(state) && computedPosition >= 0L) {
            return computedPosition;
        }
        return nextPosition;
    }

    private boolean shouldObserveLyricsPlaybackPositionJump(
            int state,
            long previousPosition,
            long nextPosition) {
        if (currentWordLyricModel == null
                || !systemUiLyricModeKeepAwakeActive
                || previousPosition < 0L
                || nextPosition < 0L) {
            return false;
        }
        return LockscreenIntegrationPolicy.isPlaybackPositionJump(
                state,
                previousPosition,
                nextPosition,
                LYRIC_PLAYBACK_POSITION_JUMP_MS);
    }

    private boolean shouldIgnoreStalePlaybackPositionAfterTrackReset(
            int state,
            long previousPosition,
            long storedPosition,
            long computedPosition,
            long lastPositionUpdateTime,
            long now) {
        if (!isPlaybackStateInMotion(state)
                || now > lyricTrackPositionResetGuardUntilElapsedMs
                || previousPosition < 0
                || previousPosition > 1_000L) {
            return false;
        }
        if (LockscreenIntegrationPolicy.isFreshPlaybackPositionAfterTrackReset(
                lastPositionUpdateTime,
                lyricTrackPositionResetGuardStartedAtElapsedMs)) {
            return false;
        }
        long candidatePosition = Math.max(storedPosition, computedPosition);
        return candidatePosition >= SYSTEMUI_TRACK_RESET_STALE_POSITION_MS;
    }

    private static boolean isPlaybackStateInMotion(int state) {
        return state == 3 || state == 4 || state == 5;
    }

    private static boolean isFiniteNonZero(float speed) {
        return speed != 0f && !Float.isNaN(speed) && !Float.isInfinite(speed);
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
        String normalized = WordLyricRenderSupport.normalizeLine(currentLyric);
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

    private void observeLyricsPlaybackPositionJump(
            long previousPosition,
            long requestedPosition) {
        if (!systemUiLyricModeKeepAwakeActive || requestedPosition < 0L) {
            return;
        }
        WordLyricModel model = currentWordLyricModel;
        if (model == null || model.lines.isEmpty()) {
            return;
        }
        long position = estimatePlaybackPositionMillis();
        if (position < 0L) {
            position = requestedPosition;
        }
        if (position < 0L) {
            return;
        }
        int targetLineIndex = model.displayIndexAt(position);
        WordLine line = model.lineAt(targetLineIndex);
        if (targetLineIndex < 0 || line == null) {
            return;
        }
        activeLyricLine = line.normalizedText;
        activeLyricLineTimeMs = line.timeMillis;
        clearSeedlingActiveLyricHint();
        clearActiveRendererTargets();
        activeRendererWordLine = null;
        int targetAdapterIndex = model.adapterIndexOfLine(line);
        if (targetAdapterIndex < 0) {
            targetAdapterIndex = targetLineIndex;
        }
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (recycler == null || !recycler.isAttachedToWindow()) {
                continue;
            }
            invalidateLyricsRecyclerDescendants(recycler);
            recycler.postInvalidateOnAnimation();
            scheduleLyricsRecyclerOwnershipSnapshots(
                    recycler,
                    "playback-jump-observed",
                    targetAdapterIndex);
        }
        TextView activeView = firstActiveLyricTextView();
        if (activeView != null) {
            scheduleActiveLyricRefresh(activeView, ACTIVE_LYRIC_FRAME_DELAY_MS);
        } else {
            scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS);
        }
        if (isLyricLayoutDiagnosticsEnabled()) {
            info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "Observed playback jump; SystemUI retains RecyclerView positioning"
                    + ", previousPosition=" + previousPosition
                    + ", targetPosition=" + position
                    + ", targetLineIndex=" + targetLineIndex
                    + ", targetAdapterIndex=" + targetAdapterIndex
                    + ", line=" + shortenForLog(line.normalizedText));
        }
    }

    private Object onTextViewOnDraw(XposedInterface.Chain chain) throws Throwable {
        long performanceStartedAt = performanceSampler.begin();
        try {
            return drawOfficialLyricTextView(chain);
        } finally {
            performanceSampler.end(
                    BridgePerformanceSampler.Metric.TEXT_VIEW_DRAW,
                    performanceStartedAt);
        }
    }

    private Object drawOfficialLyricTextView(XposedInterface.Chain chain) throws Throwable {
        if (!officialLyricDrawCoordinator.shouldHandleDraw()) {
            return chain.proceed();
        }
        Object thisObject = chain.getThisObject();
        Object canvasArg = chain.getArg(0);
        if (!(thisObject instanceof TextView) || !(canvasArg instanceof Canvas)) {
            return chain.proceed();
        }
        return officialLyricDrawCoordinator.draw(
                (TextView) thisObject,
                (Canvas) canvasArg,
                chain::proceed);
    }

    private Object onTextViewSetText(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object thisObject = chain.getThisObject();
        if (thisObject instanceof TextView) {
            TextView textView = (TextView) thisObject;
            // An attached non-lyric TextView keeps its negative parent-chain result until the
            // next attach. Clearing it on every setText made every SystemUI text bind rescan up
            // to twelve ancestors. onViewAttachedToWindow already invalidates the sentinel after
            // a real reparent, while pre-attach lookups are never negatively cached.
            boolean lyricRow = hasCachedLyricTextView(textView)
                    || isInLyricsRecyclerView(textView);
            if (!lyricRow) {
                return result;
            }
            // RecyclerView rebinds the same TextView without detaching it. Every text mutation is
            // therefore a new bind epoch and must discard normalized/frame/parent cache state.
            forgetLyricTextViewCaches(textView);
            beginLyricTextBindEpoch(textView);
            if (currentWordLyricModel != null) {
                scheduleOfficialLyricSlotPrebind(textView);
            }
        }
        return result;
    }

    private Object onViewAttachedToWindow(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object thisObject = chain.getThisObject();
        if (!(thisObject instanceof View)) {
            return result;
        }

        View view = (View) thisObject;
        boolean textView = view instanceof TextView;
        if (textView
                && LYRICS_RECYCLER_MATCH_CACHE.get((TextView) view)
                == NO_LYRICS_RECYCLER_MATCH) {
            LYRICS_RECYCLER_MATCH_CACHE.remove((TextView) view);
        }
        boolean lyricsRecyclerView = !textView && isLyricsRecyclerView(view);
        if (!textView && !lyricsRecyclerView) {
            return result;
        }
        if (lyricsRecyclerView) {
            View recyclerView = view;
            suppressOfficialRowScaleAnimations(OFFICIAL_LYRIC_ROW_SCALE_ATTACH_SUPPRESS_MS);
            configureOfficialLyricLineSpacing(recyclerView);
            configureOfficialLyricScaleOwnership(recyclerView);
            tryInstallLyricsRecyclerViewHook(recyclerView.getClass());
            rememberLyricsRecyclerView(recyclerView);
            scheduleLyricsRecyclerPrime(recyclerView);
            scheduleLyricSurfaceRenderPass(recyclerView, "attached");
            scheduleLyricsRecyclerOwnershipSnapshots(
                    recyclerView,
                    "attached",
                    readLyricsRecyclerCurrentIndex(recyclerView));
            if (recyclerView.getVisibility() == View.VISIBLE && recyclerView.isShown()) {
                activateSystemUiLyricModeFromSurface("LyricsRecyclerView attachment");
            }
            if (!systemUiLyricModeKeepAwakeActive) {
                scheduleLyricSurfaceReactivation("LyricsRecyclerView attachment");
            }
            scheduleActiveLyricRefresh(ACTIVE_LYRIC_FRAME_DELAY_MS);
        }
        if (textView && (hasCachedLyricTextView((TextView) view)
                || isInLyricsRecyclerView((TextView) view))) {
            TextView lyricTextView = (TextView) view;
            View ownerRecycler = findContainingLyricsRecyclerView(lyricTextView);
            if (ownerRecycler != null) {
                rememberLyricsRecyclerView(ownerRecycler);
                ensureLyricSurfaceRenderPass(ownerRecycler, "text-attached");
            }
            // Vendor binding can prepare scale pivots before the row has a laid-out width.
            // Re-apply once the attach traversal has supplied stable dimensions so centered
            // inactive rows do not scale around the logical-start edge.
            lyricTextView.post(() -> {
                if (lyricTextView.isAttachedToWindow()
                        && isInLyricsRecyclerView(lyricTextView)) {
                    applyOfficialLyricScalePivot(lyricTextView);
                }
            });
            lyricTextView.removeOnLayoutChangeListener(
                    OFFICIAL_LYRIC_SCALE_PIVOT_LAYOUT_LISTENER);
            lyricTextView.addOnLayoutChangeListener(
                    OFFICIAL_LYRIC_SCALE_PIVOT_LAYOUT_LISTENER);
            forgetLyricTextViewCaches(lyricTextView);
            ensureLyricTextBindEpoch(lyricTextView);
            if (currentWordLyricModel != null) {
                scheduleOfficialLyricSlotPrebind(lyricTextView);
            }
        }
        return result;
    }

    private static boolean payloadMatchesTrack(
            LyricInfoContract.Payload payload, String title, String artist) {
        return LyricInfoTrackMatcher.payloadMatchesTrack(payload, title, artist);
    }

    private static String payloadTrackKey(LyricInfoContract.Payload payload) {
        if (payload == null) {
            return "";
        }
        if (!TextUtils.isEmpty(payload.trackKey)) {
            return payload.trackKey;
        }
        if (TextUtils.isEmpty(payload.songName)) {
            return "";
        }
        return buildTrackKey(payload.songName, payload.artist);
    }

    private void clearSystemUiLyricModelForTrackChange(String title, String artist) {
        systemUiLyricPublicationGate.invalidate();
        resetSystemUiPlaybackPositionForTrackChange(
                title,
                artist,
                "SystemUI track changed");
        beginOfficialLyricTrackHandoff("SystemUI track changed");
        traceLyricModelState("track-cleared", currentWordLyricModel);
        currentLyricProviderPayload = null;
        currentLyricObservedTrackKey = "";
        systemUiHasOfficialLyric = false;
        CompatibilityStatus.global().invalidateLyricObservations();
        currentWordLyricModel = null;
        clearCommittedLyricRenderModel();
        currentWordLyricModelSignature = "";
        currentWordLyricModelTrackKey = "";
        latestSystemUiLyricLoadContext = null;
        lastSystemUiMediaId = "";
        screenTimeout.clearScreenTimeoutLyricEvidence();
        mainHandler.post(() -> {
            translationTracker.refreshTranslationActionViewVisibility();
            for (TextView textView : snapshotActiveTextViews()) {
                textView.requestLayout();
                textView.invalidate();
            }
        });
        info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL,
                "Cleared previous lyric model after track change, track="
                        + SensitiveFieldRedactor.trackHash(buildTrackKey(title, artist)));
        screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private Object onViewDetachedFromWindow(XposedInterface.Chain chain) throws Throwable {
        Object thisObject = chain.getThisObject();
        boolean lyricsRecyclerView = thisObject instanceof View
                && isLyricsRecyclerView((View) thisObject)
                && isRememberedLyricsRecyclerView((View) thisObject);
        if (thisObject instanceof TextView
                && hasCachedLyricTextView((TextView) thisObject)) {
            ((TextView) thisObject).removeOnLayoutChangeListener(
                    OFFICIAL_LYRIC_SCALE_PIVOT_LAYOUT_LISTENER);
            forgetLyricTextViewCaches((TextView) thisObject);
            forgetActiveRendererTarget((TextView) thisObject);
        }
        Object result = chain.proceed();
        if (lyricsRecyclerView) {
            forgetActiveRendererTarget((View) thisObject);
        }
        if (lyricsRecyclerView
                && !hasActiveLyricRefreshSurface()
                && !retainLyricModeForTransientSurfaceMiss("recycler detached")) {
            deactivateSystemUiLyricModeAfterSurfaceHidden();
        }
        if (lyricsRecyclerView && !hasAttachedLyricsRecyclerView()) {
            cancelLyricSurfaceReactivationCallbacks();
        }
        return result;
    }

    private Object onViewSetContentDescription(XposedInterface.Chain chain) throws Throwable {
        Object descriptionArg = chain.getArg(0);
        boolean translationDescription = descriptionArg instanceof CharSequence
                && isTranslationActionDescription((CharSequence) descriptionArg);
        if (!translationDescription && !translationTracker.translationActionTrackingActive) {
            return chain.proceed();
        }
        Object result = chain.proceed();
        Object thisObject = chain.getThisObject();
        if (!(thisObject instanceof View)) {
            return result;
        }

        View view = (View) thisObject;
        if (translationDescription) {
            translationButtonDebug("contentDescription matched translation action, view="
                    + describeViewForLog(view)
                    + ", description=" + descriptionArg);
            translationTracker.rememberTranslationActionView(view, false);
        } else if (translationTracker.isRememberedTranslationActionView(view)
                && translationTracker.isRedirectedTranslationActionView(view)) {
            translationButtonDebug("contentDescription changed on redirected action, rechecking after bind"
                    + ", view=" + describeViewForLog(view)
                    + ", description=" + descriptionArg);
            // The icon is already rebound when the label arrives; re-check once this bind returns.
            mainHandler.post(() -> translationTracker.forgetTranslationActionViewIfRebound(view));
        } else if (translationTracker.isRememberedTranslationActionView(view)
                && !translationTracker.isIconMatchedTranslationActionView(view)) {
            translationButtonDebug("contentDescription changed on tracked action, scheduling recheck"
                    + ", view=" + describeViewForLog(view)
                    + ", description=" + descriptionArg);
            // Binding briefly replaces the accessibility label before the matching icon/label is
            // restored. Validate after the bind settles instead of forgetting the action eagerly.
            mainHandler.postDelayed(
                    () -> translationTracker.forgetTranslationActionViewIfRebound(view),
                    LYRIC_VISIBILITY_RECOVERY_FIRST_DELAY_MS);
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
        int requestedVisibility = ((Number) visibilityArg).intValue();
        boolean lyricsRecyclerView = isLyricsRecyclerView(view);
        if (!lyricsRecyclerView && !translationTracker.translationActionTrackingActive) {
            return chain.proceed();
        }
        Object result;
        if (requestedVisibility == View.VISIBLE
                && translationTracker.isRememberedTranslationActionView(view)
                && translationTracker.stillShowsTranslationToggle(view)
                && PlayerTranslationTogglePolicy.shouldForceHideTrackedTranslationActionView(
                        shouldManageTranslationActionViewVisibility(),
                        currentWordLyricModel == null
                                ? -1
                                : currentWordLyricModel.translationCount(),
                        translationController.userWantsTranslationButton(currentTranslationPreferencePackage()))) {
            Object[] args = chain.getArgs().toArray();
            // Keep the action slot measured so previous/play/next remain centered.
            args[0] = View.INVISIBLE;
            translationButtonDebug("redirect translation action visibility VISIBLE->INVISIBLE"
                    + ", state=" + describeTranslationButtonState()
                    + ", view=" + describeViewForLog(view));
            result = chain.proceed(args);
            synchronized (translationTracker.translationActionViewsLock) {
                translationTracker.translationActionRedirectedViews.put(view, Boolean.TRUE);
            }
        } else {
            result = chain.proceed();
            // Any other visibility request, vendor or Bridge, supersedes a pending restore.
            synchronized (translationTracker.translationActionViewsLock) {
                if (!translationTracker.translationActionRedirectedViews.isEmpty()) {
                    translationTracker.translationActionRedirectedViews.remove(view);
                }
            }
        }
        if (lyricsRecyclerView) {
            mainHandler.post(translationTracker::refreshTranslationActionViewVisibility);
            if (requestedVisibility == View.VISIBLE) {
                rememberLyricsRecyclerView(view);
                configureOfficialLyricScaleOwnership(view);
                scheduleLyricsRecyclerPrime(view);
                scheduleLyricSurfaceRenderPass(view, "visible");
                scheduleLyricsRecyclerOwnershipSnapshots(
                        view,
                        "visibility-visible",
                        readLyricsRecyclerCurrentIndex(view));
                activateSystemUiLyricModeFromSurface("LyricsRecyclerView visibility");
                if (!systemUiLyricModeKeepAwakeActive) {
                    scheduleLyricSurfaceReactivation("LyricsRecyclerView visibility");
                }
            } else {
                forgetActiveRendererTarget(view);
                if (!hasActiveLyricRefreshSurface()
                        && !retainLyricModeForTransientSurfaceMiss(
                        "recycler visibility changed")) {
                    deactivateSystemUiLyricModeAfterSurfaceHidden();
                }
            }
        }
        return result;
    }

    private Object onImageViewSetImage(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object artworkView = chain.getThisObject();
        if (artworkView instanceof ImageView) {
            ImageView imageView = (ImageView) artworkView;
            synchronized (translationTracker.translationActionViewsLock) {
                if (!Boolean.TRUE.equals(
                        translationTracker.translationActionPresentationInFlight.get(imageView))) {
                    translationTracker.translationActionPresentationStates.remove(imageView);
                }
            }
        }
        if (artworkView instanceof ImageView
                && isLxPlayerPackage(currentLyricProviderPackage)
                && ArtworkDiagnostics.isLikelyArtworkView((ImageView) artworkView)) {
            ImageView imageView = (ImageView) artworkView;
            artworkProbe("FINAL_IMAGE_VIEW", () -> ArtworkDiagnostics.describeImageView(imageView));
        }
        if (translationTracker.translationIconAlphaFingerprint == null
                || (!systemUiLyricModeKeepAwakeActive && !translationTracker.translationActionTrackingActive)) {
            return result;
        }
        Object thisObject = chain.getThisObject();
        if (thisObject instanceof ImageView) {
            translationTracker.detectTranslationActionImageView((ImageView) thisObject, "image binding");
        }
        return result;
    }

    private boolean isNearRememberedLyricsRecyclerView(View candidate) {
        if (candidate == null) {
            return false;
        }
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                    continue;
                }
                View candidateAncestor = candidate;
                for (int candidateDepth = 0;
                        candidateAncestor != null && candidateDepth < 9;
                        candidateDepth++) {
                    View recyclerAncestor = recycler;
                    for (int recyclerDepth = 0;
                            recyclerAncestor != null && recyclerDepth < 9;
                            recyclerDepth++) {
                        if (candidateAncestor == recyclerAncestor) {
                            return true;
                        }
                        Object recyclerParent = recyclerAncestor.getParent();
                        recyclerAncestor = recyclerParent instanceof View
                                ? (View) recyclerParent
                                : null;
                    }
                    Object candidateParent = candidateAncestor.getParent();
                    candidateAncestor = candidateParent instanceof View
                            ? (View) candidateParent
                            : null;
                }
            }
        }
        return false;
    }

    private boolean shouldManageTranslationActionViewVisibility() {
        return systemUiLyricModeKeepAwakeActive && hasAttachedLyricsRecyclerView();
    }

    private String describeTranslationButtonState() {
        WordLyricModel model = currentWordLyricModel;
        return "package=" + nullToEmpty(currentTranslationPreferencePackage())
                + ", provider=" + nullToEmpty(currentLyricProviderPackage)
                + ", modeActive=" + systemUiLyricModeKeepAwakeActive
                + ", recyclerAttached=" + hasAttachedLyricsRecyclerView()
                + ", model=" + (model != null)
                + ", translations=" + (model == null ? -1 : model.translationCount())
                + ", trackedViews=" + translationTracker.rememberedTranslationActionViewCount()
                + ", enabled=" + translationController.isLyricInfoTranslationEnabledFromCache(
                currentTranslationPreferencePackage());
    }

    private void discoverTranslationActionViewsNearRememberedLyrics() {
        if (!systemUiLyricModeKeepAwakeActive) {
            return;
        }
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
        boolean traced = BridgePerfTrace.begin("CLL:translationActionScan");
        try {
            for (View recycler : recyclers) {
                translationTracker.discoverTranslationActionViewsNear(recycler);
            }
        } finally {
            BridgePerfTrace.end(traced);
        }
    }

    private void scheduleTranslationActionViewRecovery() {
        if (translationActionViewRecoveryPosted || !systemUiLyricModeKeepAwakeActive) {
            return;
        }
        translationActionViewRecoveryPosted = true;
        mainHandler.postDelayed(() -> {
            translationActionViewRecoveryPosted = false;
            discoverTranslationActionViewsNearRememberedLyrics();
            translationTracker.refreshTranslationActionViewVisibility();
        }, LYRIC_VISIBILITY_RECOVERY_FIRST_DELAY_MS);
    }

    private static String charSequenceToString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private String normalizedTextOf(TextView textView) {
        if (textView == null) {
            return "";
        }
        // A retained/recycled TextView can still expose another row's text while the native
        // adapter's committed dataset and real bind owner are already known. Match and measure
        // the immutable bound row, not that mutable string. No ownership is inferred from text.
        LyricRenderTransactions.RowBinding binding = readyLyricRowBinding(
                textView, currentWordLyricModel);
        CharSequence value = binding == null ? textView.getText() : binding.text();
        if (value == null) {
            return "";
        }
        NormalizedTextSnapshot cached = normalizedLyricTextCache.get(textView);
        if (binding != null && (cached == null || cached.source != value)
                && !TextUtils.equals(textView.getText(), value)) {
            StructuredBridgeLog.debugTransition(
                    BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED,
                    () -> "action=bound-row-text-recovered, adapterPosition=" + binding.position()
                            + ", timeMs=" + binding.timeMillis()
                            + ", boundChars=" + binding.text().length()
                            + ", viewChars=" + textView.length());
        }
        if (cached != null
                && cached.source == value
                && cached.length == value.length()
                && value instanceof String) {
            return cached.normalized;
        }
        int contentHash = charSequenceContentHash(value);
        if (cached != null
                && cached.source == value
                && cached.length == value.length()
                && cached.contentHash == contentHash) {
            return cached.normalized;
        }
        String text = value instanceof String ? (String) value : value.toString();
        String normalized = WordLyricRenderSupport.normalizeLine(text);
        normalizedLyricTextCache.put(
                textView,
                new NormalizedTextSnapshot(value, value.length(), contentHash, normalized));
        return normalized;
    }

    private LyricRenderTransactions.RowBinding readyLyricRowBinding(
            TextView textView, WordLyricModel model) {
        return textView == null || model == null ? null : lyricRenderTransactions.readyRowBinding(
                findContainingLyricsRecyclerView(textView), textView, model);
    }

    private WordLine indexedLineForBoundLyricRow(
            TextView textView, WordLyricModel model, int adapterPosition, String normalizedText) {
        LyricRenderTransactions.RowBinding binding = readyLyricRowBinding(textView, model);
        return binding == null
                ? model.lineAtAdapterIndexMatchingText(adapterPosition, normalizedText)
                : model.lineAtBoundRow(binding.position(), normalizedText, binding.timeMillis());
    }

    private static int charSequenceContentHash(CharSequence value) {
        if (value == null) {
            return 0;
        }
        int hash = 0;
        for (int i = 0; i < value.length(); i++) {
            hash = 31 * hash + value.charAt(i);
        }
        return hash;
    }

    private Object onLyricsRecyclerSetCurrentLyric(XposedInterface.Chain chain) throws Throwable {
        if (LyricsRecyclerPolicy.shouldSkipSetCurrentLyricHook(
                Boolean.TRUE.equals(suppressLyricsRecyclerHook.get()))) {
            return chain.proceed();
        }
        Object recycler = chain.getThisObject();
        View recyclerView = recycler instanceof View ? (View) recycler : null;
        LyricInfoContract.Payload snapshot = currentLyricProviderPayload;
        if (snapshot != null && snapshot.snapshotPositionMillis >= 0L
                && isOfficialTimedCurrentLyricExecutable(chain.getExecutable())) {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            args[1] = TimedLyricMethodPolicy.position(snapshot.snapshotPositionMillis, (Long) args[1]);
            Object result = chain.proceed(args);
            if (recyclerView != null) rememberLyricsRecyclerView(recyclerView);
            info(BridgeDebugArea.RENDERER, BridgeEvents.DETAIL,
                    "Pinned native lyric clock; current=" + snapshot.snapshotPositionMillis / 1000L);
            return result;
        }
        if (recyclerView != null) {
            rememberLyricsRecyclerView(recyclerView);
        }
        boolean officialTimedCurrentLyric =
                isOfficialTimedCurrentLyricExecutable(chain.getExecutable());
        // Position only: the animate flag and the index/scroll transaction remain the vendor's.
        Object[] guardedArgs = officialTimedCurrentLyric
                ? guardTimedLyricArgs(chain, recyclerView)
                : null;
        int requestedTargetIndex = -1;
        int previousOfficialIndex = -1;
        LyricsRecyclerGeometry beforeGeometry = null;
        try {
            requestedTargetIndex = resolveLyricsRecyclerTargetIndex(
                    chain,
                    recycler,
                    guardedArgs == null ? null : guardedArgs[1]);
            if (recyclerView != null) {
                previousOfficialIndex = readLyricsRecyclerCurrentIndex(recyclerView);
                if (recyclerView.isShown()) {
                    activateSystemUiLyricModeFromSurface("setCurrentLyric");
                }
                int geometryTarget = requestedTargetIndex >= 0
                        ? requestedTargetIndex
                        : previousOfficialIndex;
                beforeGeometry = captureLyricsRecyclerGeometry(recyclerView, geometryTarget);
            }
        } catch (Throwable t) {
            error("Failed while reading LyricsRecyclerView#setCurrentLyric", t);
        }
        // The vendor controller deliberately uses l(false, position) while the immersive page is
        // alpha=0, and l(true, position) for ordinary progression. Do not rewrite that transaction:
        // it is the single owner of n, SmoothScroller cancellation, adapter notifications and row
        // transforms. Earlier seek/re-entry patches changed the boolean and raced those four pieces.
        // Only the position may change: the controller entry aligns it, this call only guards it.
        Object result = guardedArgs != null ? chain.proceed(guardedArgs) : chain.proceed();
        if (recyclerView != null) {
            int officialIndex = readLyricsRecyclerCurrentIndex(recyclerView);
            int observedTargetIndex = officialIndex >= 0
                    ? officialIndex
                    : requestedTargetIndex;
            if (observedTargetIndex >= 0) {
                rememberLyricsRecyclerTargetIndex(observedTargetIndex);
            }
            maybeLogLyricsRecyclerSetCurrentGeometry(
                    observedTargetIndex,
                    beforeGeometry,
                    captureLyricsRecyclerGeometry(recyclerView, observedTargetIndex));
            scheduleLyricsRecyclerOwnershipSnapshots(
                    recyclerView,
                    officialTimedCurrentLyric
                            ? "official-current-timed"
                            : "official-current-indexed",
                    observedTargetIndex);
        }
        return result;
    }

    private int resolveLyricsRecyclerTargetIndex(
            XposedInterface.Chain chain,
            Object recycler,
            Object guardedPosition) {
        try {
            Class<?>[] parameterTypes = chain.getExecutable().getParameterTypes();
            if (parameterTypes.length > 0) {
                Object first = chain.getArg(0);
                if (first instanceof Integer) {
                    return (Integer) first;
                }
            }
            if (parameterTypes.length == 2
                    && isBooleanParameter(parameterTypes[0])
                    && (parameterTypes[1] == long.class || parameterTypes[1] == Long.class)) {
                Object positionArg = guardedPosition != null ? guardedPosition : chain.getArg(1);
                if (positionArg instanceof Number) {
                    WordLyricModel model = currentWordLyricModel;
                    if (model != null) {
                        return model.adapterIndexAt(((Number) positionArg).longValue());
                    }
                }
            }
            return readLyricsRecyclerCurrentIndex(recycler);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private void rememberLyricsRecyclerTargetIndex(int targetIndex) {
        if (targetIndex < 0) {
            return;
        }
        lastLyricsRecyclerIndex = targetIndex;
        beginLyricRecyclerSettleWindow(
                targetIndex,
                LYRIC_RECYCLER_SET_CURRENT_SETTLE_MS);
        WordLyricModel model = currentWordLyricModel;
        if (model != null) {
            WordLine line = model.lineAtAdapterIndex(lastLyricsRecyclerIndex);
            if (line != null) {
                activeLyricLine = line.normalizedText;
                activeLyricLineTimeMs = line.timeMillis;
                long now = SystemClock.elapsedRealtime();
                lastOfficialLyricIndexObservedAtElapsedMs = now;
                lastSeedlingActiveLineTimeMs = line.timeMillis;
                lastSeedlingActiveLineObservedAtMs = now;
                TextView candidate = firstActiveLyricTextView();
                if (candidate != null) {
                    scheduleActiveLyricRefresh(candidate, 33L);
                }
            }
        }
        maybeLogRecyclerIndex(lastLyricsRecyclerIndex);
    }

    private static void stopLyricsRecyclerScroll(View recycler) {
        tryInvokeNoArgByName(recycler, "stopScroll");
    }

    private void rememberLyricsRecyclerView(View recycler) {
        if (recycler == null) {
            return;
        }
        rememberLyricsRecyclerAdapter(recycler);
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View existing = lyricsRecyclerViews.get(i).get();
                if (existing == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (existing == recycler) {
                    return;
                }
            }
            if (lyricsRecyclerViews.size() >= 8) {
                int removableIndex = -1;
                for (int i = 0; i < lyricsRecyclerViews.size(); i++) {
                    View existing = lyricsRecyclerViews.get(i).get();
                    if (existing == null || !existing.isAttachedToWindow()) {
                        removableIndex = i;
                        break;
                    }
                }
                lyricsRecyclerViews.remove(removableIndex >= 0 ? removableIndex : 0);
            }
            lyricsRecyclerViews.add(new WeakReference<>(recycler));
        }
        info(BridgeDebugArea.AOD, BridgeEvents.RECYCLER_ATTACHED, "Observed LyricsRecyclerView attachment"
                + " | rvId=" + objectId(recycler)
                + ", class=" + recycler.getClass().getName());
    }

    private void scheduleLyricSurfaceRenderPass(View recycler, String reason) {
        if (recycler == null) {
            return;
        }
        final int epoch;
        final long startedAt = SystemClock.elapsedRealtime();
        WordLyricModel model = currentWordLyricModel;
        if (lyricRenderTransactions.surfaceStatus(recycler, model)
                != LyricRenderTransactions.Status.READY) {
            return;
        }
        synchronized (lyricSurfaceRenderEpochsLock) {
            Integer previous = lyricSurfaceRenderEpochs.get(recycler);
            epoch = previous == null
                    ? 1
                    : previous == Integer.MAX_VALUE ? 1 : previous + 1;
            lyricSurfaceRenderEpochs.put(recycler, epoch);
            lyricSurfaceReadinessStates.put(
                    recycler,
                    new LyricSurfaceReadinessState(epoch, model, startedAt));
        }
        invalidateLyricsRecyclerDescendants(recycler);
        recycler.postInvalidateOnAnimation();
        recycler.postOnAnimation(() -> continueLyricSurfaceRenderPass(
                recycler,
                model,
                nullToEmpty(reason),
                epoch,
                1,
                startedAt));
    }

    private int beginBrightLyricGeometryCommit(WordLyricModel model) {
        synchronized (brightLyricGeometryCommitLock) {
            brightLyricGeometryCommitGeneration = brightLyricGeometryCommitGeneration
                    == Integer.MAX_VALUE ? 1 : brightLyricGeometryCommitGeneration + 1;
            pendingBrightLyricGeometryModel = model;
            return brightLyricGeometryCommitGeneration;
        }
    }

    private void cancelBrightLyricGeometryCommit() {
        synchronized (brightLyricGeometryCommitLock) {
            brightLyricGeometryCommitGeneration = brightLyricGeometryCommitGeneration
                    == Integer.MAX_VALUE ? 1 : brightLyricGeometryCommitGeneration + 1;
            pendingBrightLyricGeometryModel = null;
        }
    }

    private boolean isCurrentBrightLyricGeometryCommit(
            WordLyricModel model,
            int generation) {
        synchronized (brightLyricGeometryCommitLock) {
            return generation == brightLyricGeometryCommitGeneration
                    && pendingBrightLyricGeometryModel == model
                    && currentWordLyricModel == model;
        }
    }

    private void preparePublishedLyricModelGeometry(
            WordLyricModel model,
            int generation) {
        if (!isCurrentBrightLyricGeometryCommit(model, generation)) {
            return;
        }
        if (hasActiveLyricRefreshSurface()) {
            activateSystemUiLyricModeFromSurface("lyric model ready");
        }
        translationTracker.refreshTranslationActionViewVisibility();
        ArrayList<View> recyclers = snapshotLyricsRecyclerViews();
        boolean hasObservedSurface = !recyclers.isEmpty();
        recyclers.removeIf(recycler -> lyricRenderTransactions.surfaceStatus(recycler, model)
                != LyricRenderTransactions.Status.READY);
        if (hasObservedSurface && recyclers.isEmpty()) {
            // The parser's new model is not a commit of the old adapter dataset. The real
            // setLyrics event resumes this transaction, without a timer or text-based takeover.
            invalidateRememberedLyricViews();
            return;
        }
        boolean deferBrightPixels = !isAodLowFrameRateLyricMode() && !recyclers.isEmpty();
        for (View recycler : recyclers) {
            if (recycler instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) recycler;
                int[] cohort = {0, 0, 0};
                for (int i = 0; i < group.getChildCount(); i++) {
                    View item = group.getChildAt(i);
                    observeLyricDrawCohort(recycler, item, readRecyclerChildPosition(recycler, item),
                            model, cohort);
                }
            }
            configureOfficialLyricScaleOwnership(recycler);
            applyVisibleLyricBlockHeights(recycler);
            if (!deferBrightPixels) {
                scheduleLyricSurfaceRenderPass(recycler, "model-ready");
            }
        }
        // Preserve the known-good AOD position/prime order. The bright-screen barrier controls
        // only custom pixels and never takes ownership of RecyclerView positioning.
        primeRememberedLyricsRecyclerViews("model-ready");
        if (!deferBrightPixels) {
            finishBrightLyricGeometryCommit(model, generation, false);
            invalidateRememberedLyricViews();
            return;
        }
        View anchor = recyclers.get(0);
        anchor.postOnAnimation(
                () -> finishBrightLyricGeometryCommit(model, generation, true));
        mainHandler.postDelayed(
                () -> finishBrightLyricGeometryCommit(model, generation, true),
                LYRIC_SURFACE_RENDER_PASS_MAX_MS);
    }

    private void finishBrightLyricGeometryCommit(
            WordLyricModel model,
            int generation,
            boolean scheduleRenderPass) {
        synchronized (brightLyricGeometryCommitLock) {
            if (generation != brightLyricGeometryCommitGeneration
                    || pendingBrightLyricGeometryModel != model
                    || currentWordLyricModel != model) {
                return;
            }
            pendingBrightLyricGeometryModel = null;
        }
        boolean aodLowFrameRateMode = isAodLowFrameRateLyricMode();
        boolean revealCommittedModel =
                LockscreenIntegrationPolicy.shouldRevealCommittedBrightLyricModel(
                        scheduleRenderPass,
                        aodLowFrameRateMode,
                        lyricUiConfig.motionMode);
        if (revealCommittedModel) {
            // Fade only the module-rendered lyric pixels after their new row geometry has landed.
            // RecyclerView alpha remains owned by SystemUI, so this cannot cancel or overwrite the
            // immersive-page/AOD surface transition that is running around the same handoff.
            officialLyricTextRenderer.armModelSwitchReveal();
        }
        if (scheduleRenderPass) {
            for (View recycler : snapshotLyricsRecyclerViews()) {
                scheduleLyricSurfaceRenderPass(recycler, "model-geometry-committed");
            }
        }
        invalidateRememberedLyricViews();
        if (isLyricLayoutDiagnosticsEnabled()) {
            info(BridgeDebugArea.AOD, BridgeEvents.AOD_TRANSITION, "Committed published lyric model geometry"
                    + " | generation=" + generation
                    + ", lines=" + (model == null ? 0 : model.lines.size())
                    + ", reveal=" + revealCommittedModel
                    + ", aod=" + aodLowFrameRateMode);
        }
    }

    private void ensureLyricSurfaceRenderPass(View recycler, String reason) {
        if (recycler == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        synchronized (lyricSurfaceRenderEpochsLock) {
            LyricSurfaceReadinessState state = lyricSurfaceReadinessStates.get(recycler);
            if (state != null
                    && state.model == currentWordLyricModel
                    && !state.stableReady
                    && now - state.startedAtElapsedMs <= LYRIC_SURFACE_RENDER_PASS_MAX_MS) {
                return;
            }
        }
        scheduleLyricSurfaceRenderPass(recycler, reason);
    }

    private void prepareLyricSurfaceFromPendingDraw(View recycler, String reason) {
        if (recycler == null) {
            return;
        }
        boolean prepare;
        synchronized (lyricSurfaceRenderEpochsLock) {
            Integer previousGeneration = lyricSurfacePendingDrawGenerations.get(recycler);
            prepare = previousGeneration == null
                    || previousGeneration != lyricSurfaceReactivationGeneration;
            if (prepare) {
                lyricSurfacePendingDrawGenerations.put(
                        recycler,
                        lyricSurfaceReactivationGeneration);
            }
        }
        if (!prepare) {
            return;
        }
        rememberLyricsRecyclerView(recycler);
        scheduleLyricSurfaceRenderPass(recycler, reason);
    }

    private void prepareLyricSurfaceFromVisibleSession(View recycler, String reason) {
        if (recycler == null) {
            return;
        }
        boolean prepare;
        synchronized (lyricSurfaceRenderEpochsLock) {
            Integer previousGeneration = lyricSurfaceVisibleSessionGenerations.get(recycler);
            prepare = previousGeneration == null || previousGeneration != lyricModeRebindGeneration;
            if (prepare) {
                lyricSurfaceVisibleSessionGenerations.put(recycler, lyricModeRebindGeneration);
            }
        }
        if (!prepare) {
            return;
        }
        rememberLyricsRecyclerView(recycler);
        scheduleLyricSurfaceRenderPass(recycler, reason);
    }

    private void continueLyricSurfaceRenderPass(
            View recycler,
            WordLyricModel model,
            String reason,
            int epoch,
            int frame,
            long startedAt) {
        LyricSurfaceReadinessState state;
        synchronized (lyricSurfaceRenderEpochsLock) {
            Integer currentEpoch = lyricSurfaceRenderEpochs.get(recycler);
            state = lyricSurfaceReadinessStates.get(recycler);
            if (currentEpoch == null
                    || currentEpoch != epoch
                    || state == null
                    || state.epoch != epoch
                    || state.model != model) {
                return;
            }
        }
        if (!recycler.isAttachedToWindow() || currentWordLyricModel != model) {
            return;
        }

        LyricSurfaceReadinessSnapshot snapshot = captureLyricSurfaceReadiness(
                recycler,
                model,
                epoch);
        boolean stableReady;
        synchronized (lyricSurfaceRenderEpochsLock) {
            LyricSurfaceReadinessState currentState = lyricSurfaceReadinessStates.get(recycler);
            if (currentState != state) {
                return;
            }
            stableReady = state.observe(snapshot);
        }
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        boolean exhausted = frame >= LYRIC_SURFACE_RENDER_PASS_MAX_FRAMES
                || elapsed >= LYRIC_SURFACE_RENDER_PASS_MAX_MS;
        if (stableReady || exhausted) {
            if (isLyricLayoutDiagnosticsEnabled()) {
                info(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED, "Prepared lyric surface render pass"
                        + " | reason=" + reason
                        + ", rvId=" + objectId(recycler)
                        + ", epoch=" + epoch
                        + ", frames=" + frame
                        + ", stableReady=" + stableReady
                        + ", required=" + snapshot.requiredFrames
                        + ", ready=" + snapshot.readyFrames
                        + ", children=" + snapshot.recyclerChildCount
                        + ", itemCount=" + snapshot.adapterItemCount
                        + ", elapsedMs=" + elapsed);
            }
            return;
        }

        invalidateLyricsRecyclerDescendants(recycler);
        recycler.postInvalidateOnAnimation();
        recycler.postOnAnimation(() -> continueLyricSurfaceRenderPass(
                recycler,
                model,
                reason,
                epoch,
                frame + 1,
                startedAt));
    }

    private void rememberLyricsRecyclerAdapter(View recycler) {
        Object adapter = readRecyclerAdapter(recycler);
        if (adapter == null) {
            return;
        }
        tryInstallLyricAdapterBindHook(adapter);
        lyricRenderTransactions.registerAdapter(recycler, adapter);
        synchronized (lyricsRecyclerAdaptersLock) {
            for (int i = lyricsRecyclerAdapters.size() - 1; i >= 0; i--) {
                Object existing = lyricsRecyclerAdapters.get(i).get();
                if (existing == null) {
                    lyricsRecyclerAdapters.remove(i);
                } else if (existing == adapter) {
                    return;
                }
            }
            if (lyricsRecyclerAdapters.size() >= 8) {
                lyricsRecyclerAdapters.remove(0);
            }
            lyricsRecyclerAdapters.add(new WeakReference<>(adapter));
        }
    }

    private Object onLyricsRecyclerSetLyrics(XposedInterface.Chain chain) throws Throwable {
        View recycler = (View) chain.getThisObject();
        Object adapter = readRecyclerAdapter(recycler);
        if (adapter == null) {
            // setLyrics owns a final adapter before the first setAdapter call.
            Object found = null;
            for (Class<?> type = recycler.getClass(); type != null && type != View.class;
                    type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    try {
                        field.setAccessible(true);
                        Object value = field.get(recycler);
                        if (findRecyclerAdapterBaseClass(value) != null) {
                            if (found != null && found != value) {
                                found = null;
                                break;
                            }
                            found = value;
                        }
                    } catch (Throwable ignored) {
                        // Only a uniquely observed adapter can be enrolled in the transaction.
                    }
                }
                if (found != null) break;
            }
            adapter = found;
        }
        boolean bindingAvailable = tryInstallLyricAdapterBindHook(adapter);
        OfficialLyricDataset dataset = bindingAvailable
                ? OfficialLyricDataset.fromNativeRows((List<?>) chain.getArg(0)) : null;
        LyricRenderTransactions.DatasetChange change =
                lyricRenderTransactions.beginDataset(recycler, adapter, dataset);
        Object result;
        try {
            result = chain.proceed();
        } catch (Throwable hostFailure) {
            lyricRenderTransactions.abortDataset(change);
            throw hostFailure;
        }
        lyricRenderTransactions.commitDataset(change, readRecyclerAdapter(recycler));
        rememberLyricsRecyclerView(recycler);
        WordLyricModel model = lyricRenderTransactions.currentModel();
        if (model != null && model == currentWordLyricModel
                && lyricRenderTransactions.surfaceStatus(recycler, model)
                == LyricRenderTransactions.Status.READY) {
            int generation = beginBrightLyricGeometryCommit(model);
            preparePublishedLyricModelGeometry(model, generation);
        }
        invalidateLyricsRecyclerDescendants(recycler);
        recycler.postInvalidateOnAnimation();
        StructuredBridgeLog.debugTransition(
                BridgeDebugArea.RENDERER, BridgeEvents.LYRIC_HANDOFF,
                () -> "action=dataset-committed, rows=" + (dataset == null ? -1 : dataset.size())
                        + ", modelReady=" + (lyricRenderTransactions.surfaceStatus(recycler, model)
                        == LyricRenderTransactions.Status.READY));
        return result;
    }

    private boolean tryInstallLyricAdapterBindHook(Object adapter) {
        if (adapter == null) return false;
        Class<?> adapterClass = adapter.getClass();
        synchronized (pluginHookGenerationLock) {
            Boolean installed = lyricAdapterBindHookClasses.get(adapterClass);
            if (installed != null) return installed;
            Class<?> baseClass = findRecyclerAdapterBaseClass(adapter);
            if (baseClass == null) {
                lyricAdapterBindHookClasses.put(adapterClass, false);
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_FAILED,
                        "Lyric adapter runtime contract unavailable, class=" + adapterClass.getName());
                return false;
            }
            int count = 0;
            for (Method method : LyricRecyclerAdapterContract.bindingMethods(adapter, baseClass)) {
                try {
                    method.setAccessible(true);
                    XposedInterface.HookHandle handle = hook(method)
                            .setId(HOOK_ID_LYRICS_RECYCLER + "-adapter-bind-" + count)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(this::onLyricsAdapterBindViewHolder);
                    rememberPluginHookHandle(adapterClass.getClassLoader(), handle);
                    count++;
                } catch (Throwable failure) {
                    error("Failed to observe lyric adapter binding", failure);
                }
            }
            lyricAdapterBindHookClasses.put(adapterClass, count > 0);
            if (count > 0) infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED,
                    "Hooked lyric adapter binding transactions, methods=" + count
                            + ", contract=runtime-signatures, class=" + adapterClass.getName());
            return count > 0;
        }
    }

    private Object onLyricsAdapterBindViewHolder(XposedInterface.Chain chain) throws Throwable {
        Object adapter = chain.getThisObject();
        Object owner = lyricRenderTransactions.surfaceForAdapter(adapter);
        if (owner instanceof View) {
            observeCommittedLyricAdapterData((View) owner, adapter);
        }
        LyricRenderTransactions.RowBinding binding = lyricRenderTransactions.beginRowBind(
                adapter, ((Number) chain.getArg(1)).intValue());
        Object result = chain.proceed();
        if (binding == null) return result;
        Object holder = chain.getArg(0);
        for (Class<?> type = holder.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field itemField = type.getDeclaredField("itemView");
                itemField.setAccessible(true);
                Object item = itemField.get(holder);
                if (item instanceof View) {
                    recordBoundLyricItem((View) item, binding, new int[]{0});
                }
                break;
            } catch (NoSuchFieldException ignored) {
                // itemView is declared by RecyclerView.ViewHolder, not the vendor subclass.
            } catch (Throwable failure) {
                error("Failed to associate lyric holder with dataset", failure);
                break;
            }
        }
        return result;
    }

    private void observeCommittedLyricAdapterData(View recycler, Object adapter) {
        OfficialLyricDataset dataset = tryInstallLyricAdapterBindHook(adapter)
                ? OfficialLyricDataset.fromAdapter(adapter) : null;
        if (!lyricRenderTransactions.observeAdapterDataset(adapter, dataset)) return;
        invalidateCommittedLyricRows(recycler);
        WordLyricModel model = lyricRenderTransactions.currentModel();
        mainHandler.post(() -> {
            if (model == null || currentWordLyricModel != model
                    || lyricRenderTransactions.currentModel() != model
                    || lyricRenderTransactions.surfaceStatus(recycler, model)
                    != LyricRenderTransactions.Status.READY) return;
            int generation = beginBrightLyricGeometryCommit(model);
            preparePublishedLyricModelGeometry(model, generation);
        });
    }

    private void invalidateCommittedLyricRows(View recycler) {
        for (Object row : lyricRenderTransactions.rowsForSurface(recycler)) {
            if (row instanceof TextView) ((TextView) row).invalidate();
        }
        invalidateLyricsRecyclerDescendants(recycler);
        recycler.invalidate();
    }

    private Object onLyricsRecyclerDraw(XposedInterface.Chain chain) throws Throwable {
        Object target = chain.getThisObject();
        if (!(target instanceof ViewGroup) || !isLyricsRecyclerView((View) target)
                || !(chain.getArg(0) instanceof Canvas)) return chain.proceed();
        ViewGroup recycler = (ViewGroup) target;
        Object adapter = readRecyclerAdapter(recycler);
        if (adapter != null) {
            rememberLyricsRecyclerAdapter(recycler);
            WordLyricModel candidate = lyricRenderTransactions.currentModel();
            if (lyricRenderTransactions.surfaceStatus(recycler, candidate)
                    != LyricRenderTransactions.Status.READY) {
                observeCommittedLyricAdapterData(recycler, adapter);
            }
        }
        WordLyricModel model = lyricRenderTransactions.currentModel();
        LyricRenderTransactions.Status status = lyricRenderTransactions.surfaceStatus(recycler, model);
        if (model == null && !shouldSuppressOfficialLyricForTrackHandoff()) return chain.proceed();
        if (status == LyricRenderTransactions.Status.UNSUPPORTED) return chain.proceed();
        int[] cohort = {0, 0, 0};
        if (status == LyricRenderTransactions.Status.READY) {
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View item = recycler.getChildAt(i);
                observeLyricDrawCohort(recycler, item, readRecyclerChildPosition(recycler, item),
                        model, cohort);
            }
        }
        boolean coherent = status == LyricRenderTransactions.Status.READY
                && pendingBrightLyricGeometryModel != model && cohort[0] > 0 && cohort[1] == 0;
        boolean exposed = lyricRenderTransactions.commitSurfacePixels(recycler, model, coherent);
        if (exposed) {
            // Includes retired/hidden holders no longer listed by getChildCount. Their cached
            // RenderNodes must not replay pixels from the previous dataset when the batch opens.
            invalidateCommittedLyricRows(recycler);
            StructuredBridgeLog.debugTransition(
                    BridgeDebugArea.RENDERER, BridgeEvents.LYRIC_HANDOFF,
                    () -> "action=render-transaction-committed, rows=" + cohort[0]);
        }
        return LyricSurfaceDrawTransaction.draw(
                (Canvas) chain.getArg(0), coherent, chain::proceed);
    }

    private void observeLyricDrawCohort(
            View recycler, View view, int position, WordLyricModel model, int[] counts) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (counts[2]++ > 320) {
            counts[1]++;
            return;
        }
        if (view instanceof TextView && ((TextView) view).length() > 0) {
            LyricRenderTransactions.RowStatus status = lyricRenderTransactions.observeRowPosition(
                    recycler, view, position, model);
            if (status == LyricRenderTransactions.RowStatus.CURRENT) counts[0]++;
            else if (status == LyricRenderTransactions.RowStatus.WAITING) counts[1]++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                observeLyricDrawCohort(recycler, group.getChildAt(i), position, model, counts);
            }
        }
    }

    private void recordBoundLyricItem(
            View view, LyricRenderTransactions.RowBinding binding, int[] visited) {
        if (view == null || visited[0]++ > 128) return;
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            forgetLyricTextViewCaches(textView);
            beginLyricTextBindEpoch(textView);
            lyricRenderTransactions.completeRowBind(binding, textView);
            scheduleOfficialLyricSlotPrebind(textView);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                recordBoundLyricItem(group.getChildAt(i), binding, visited);
            }
        }
    }

    private boolean isRememberedLyricsRecyclerAdapter(Object adapter) {
        if (adapter == null) {
            return false;
        }
        synchronized (lyricsRecyclerAdaptersLock) {
            for (int i = lyricsRecyclerAdapters.size() - 1; i >= 0; i--) {
                Object existing = lyricsRecyclerAdapters.get(i).get();
                if (existing == null) {
                    lyricsRecyclerAdapters.remove(i);
                } else if (existing == adapter) {
                    return true;
                }
            }
        }
        return false;
    }

    private void scheduleLyricsRecyclerPrime(View recycler) {
        if (recycler == null) {
            return;
        }
        recycler.post(() -> {
            primeLyricsRecyclerView(recycler, "attached");
            if (LyricsRecyclerPolicy.shouldScheduleFollowUpPrime(
                    hasBoundLyricsRecyclerChildren(recycler))) {
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
        if (lyricRenderTransactions.surfaceStatus(recycler, model)
                != LyricRenderTransactions.Status.READY) return;
        if (!LyricsRecyclerPolicy.hasPrimeInputs(
                recycler != null,
                model != null && !model.lines.isEmpty(),
                recycler != null && recycler.isAttachedToWindow(),
                recycler != null && recycler.getVisibility() == View.VISIBLE)
                || recycler == null
                || model == null) {
            return;
        }
        if (!shouldModulePositionLyricsRecycler()) {
            maybeLogLyricsRecyclerOwnershipSnapshot(
                    recycler,
                    "module-prime-skipped-" + nullToEmpty(reason),
                    readLyricsRecyclerCurrentIndex(recycler));
            return;
        }
        int targetIndex = resolveLyricsRecyclerPrimeTargetIndex(model);
        WordLine targetLine = model.lineAtAdapterIndex(targetIndex);
        stabilizeLyricsRecyclerScroll(recycler, "prime-" + reason);
        boolean alreadyPrimed = lastPrimedLyricsRecyclerView.get() == recycler
                && lastPrimedLyricsRecyclerIndex == targetIndex
                && hasBoundLyricsRecyclerChildren(recycler);
        boolean positioned = alreadyPrimed || invokeLyricsRecyclerSetCurrentLyric(recycler, targetIndex);
        if (!positioned && hasSentenceWindow()) {
            // Some firmware has no discoverable current-line method. Keep the same official
            // RecyclerView geometry and use the existing layout-manager positioning fallback.
            positioned = forceAlignLyricsRecyclerToIndex(recycler, targetIndex, "sentence-window");
        }
        applyVisibleLyricBlockHeights(recycler);
        if (positioned && hasBoundLyricsRecyclerChildren(recycler)) {
            lastPrimedLyricsRecyclerView = new WeakReference<>(recycler);
            lastPrimedLyricsRecyclerIndex = targetIndex;
            lastLyricsRecyclerIndex = targetIndex;
            if (targetLine != null) {
                activeLyricLine = targetLine.normalizedText;
                activeLyricLineTimeMs = targetLine.timeMillis;
            }
            if (!alreadyPrimed) {
                info(BridgeDebugArea.AOD, BridgeEvents.RECYCLER_PRIMED, "Primed LyricsRecyclerView at index=" + targetIndex + ", reason=" + reason);
            }
        }
    }

    private int resolveLyricsRecyclerPrimeTargetIndex(WordLyricModel model) {
        if (model == null || model.lines.isEmpty()) {
            return -1;
        }
        return model.adapterIndexAt(estimatePlaybackPositionMillis());
    }

    private boolean forceAlignLyricsRecyclerToIndex(
            View recycler,
            int targetIndex,
            String reason) {
        return forceAlignLyricsRecyclerToIndex(recycler, targetIndex, reason, true);
    }

    private boolean forceAlignLyricsRecyclerToIndex(
            View recycler,
            int targetIndex,
            String reason,
            boolean scheduleRetries) {
        return forceAlignLyricsRecyclerToIndex(
                recycler,
                targetIndex,
                reason,
                scheduleRetries,
                false);
    }

    private boolean forceAlignLyricsRecyclerToIndex(
            View recycler,
            int targetIndex,
            String reason,
            boolean scheduleRetries,
            boolean allowNativeAdaptiveRecovery) {
        if (recycler == null
                || targetIndex < 0
                || !recycler.isAttachedToWindow()
                || (!shouldModulePositionLyricsRecycler()
                && !allowNativeAdaptiveRecovery)) {
            return false;
        }
        int generation = ++lyricRecyclerForceAlignGeneration;
        int topOffset = computeLyricsRecyclerTopOffset(recycler, targetIndex);
        boolean immediate = scrollLyricsRecyclerToPositionWithOffset(
                recycler,
                targetIndex,
                topOffset);
        if (immediate) {
            maybeLogForcedLyricsRecyclerAlign(recycler, targetIndex, reason);
        }
        if (scheduleRetries) {
            recycler.post(() -> alignLyricsRecyclerAfterLayout(
                    recycler,
                    targetIndex,
                    generation,
                    reason,
                    allowNativeAdaptiveRecovery));
            recycler.postDelayed(
                    () -> alignLyricsRecyclerAfterLayout(
                            recycler,
                            targetIndex,
                            generation,
                            reason,
                            allowNativeAdaptiveRecovery),
                    64L);
            recycler.postDelayed(
                    () -> alignLyricsRecyclerAfterLayout(
                            recycler,
                            targetIndex,
                            generation,
                            reason,
                            allowNativeAdaptiveRecovery),
                    180L);
            recycler.postDelayed(
                    () -> alignLyricsRecyclerAfterLayout(
                            recycler,
                            targetIndex,
                            generation,
                            reason,
                            allowNativeAdaptiveRecovery),
                    320L);
        }
        return immediate;
    }

    private void alignLyricsRecyclerAfterLayout(
            View recycler,
            int targetIndex,
            int generation,
            String reason,
            boolean allowNativeAdaptiveRecovery) {
        boolean generationMatches = generation == lyricRecyclerForceAlignGeneration;
        boolean attached = recycler != null && recycler.isAttachedToWindow();
        if (!generationMatches
                || recycler == null
                || targetIndex < 0
                || !attached
                || (!shouldModulePositionLyricsRecycler()
                && !allowNativeAdaptiveRecovery)) {
            return;
        }
        stopLyricsRecyclerScroll(recycler);
        applyVisibleLyricBlockHeights(recycler);
        scrollLyricsRecyclerToPositionWithOffset(
                recycler,
                targetIndex,
                computeLyricsRecyclerTopOffset(recycler, targetIndex));
        offsetLyricsRecyclerCurrentLine(recycler, targetIndex);
    }

    private void followAdaptiveNativeLineTimedRecycler(
            WordLyricModel model,
            WordLine activeLine,
            TextView activeView) {
        if (model == null
                || activeLine == null
                || activeView == null
                || activeLine.timingMode != LyricTimingMode.LINE_TIMED
                || currentLyricProviderPayload == null) {
            return;
        }
        int targetIndex = model.adapterIndexOfLine(activeLine);
        View recycler = findContainingLyricsRecyclerView(activeView);
        if (targetIndex < 0 || recycler == null || !recycler.isAttachedToWindow()) {
            return;
        }
        boolean transitionSettled = screenTimeout.isScreenInteractiveForWakeLock()
                && !isAodLowFrameRateLyricMode()
                && recycler.getVisibility() == View.VISIBLE
                && recycler.getWidth() > 0
                && recycler.getHeight() > 0
                && recycler.getAlpha() >= 0.99f
                && Math.abs(recycler.getTranslationY()) <= 1f;
        LyricsRecyclerGeometry geometry = captureLyricsRecyclerGeometry(recycler, targetIndex);
        int desiredCenter = computeActiveLyricsRecyclerDesiredCenter(recycler);
        int officialIndex = readLyricsRecyclerCurrentIndex(recycler);
        long now = SystemClock.elapsedRealtime();
        boolean knownOfficialLag = transitionSettled
                && officialIndex >= 0
                && Math.abs(targetIndex - officialIndex) >= 2;
        if (!adaptiveLineTimedRecyclerFollowActive) {
            if (!knownOfficialLag) {
                adaptiveLineTimedLagCandidateIndex = -1;
                adaptiveLineTimedLagObservedAtElapsedMs = -1L;
            } else if (adaptiveLineTimedLagCandidateIndex != targetIndex) {
                adaptiveLineTimedLagCandidateIndex = targetIndex;
                adaptiveLineTimedLagObservedAtElapsedMs = now;
            }
        }
        long lagObservedMillis = adaptiveLineTimedLagObservedAtElapsedMs < 0L
                ? -1L
                : Math.max(0L, now - adaptiveLineTimedLagObservedAtElapsedMs);
        if (!adaptiveLineTimedRecyclerFollowActive
                && LyricsRecyclerPolicy.shouldActivateAdaptiveLineTimedFollow(
                true,
                true,
                transitionSettled,
                targetIndex,
                officialIndex,
                lagObservedMillis,
                ADAPTIVE_LINE_TIMED_RECYCLER_MIN_LAG_MS)) {
            adaptiveLineTimedRecyclerFollowActive = true;
            info(BridgeDebugArea.AOD, BridgeEvents.RECYCLER_SCROLL_STABILIZED,
                    "Activated adaptive native line-timed Recycler follow"
                            + " | targetIndex=" + targetIndex
                            + ", officialIndex=" + officialIndex
                            + ", lagObservedMs=" + lagObservedMillis
                            + ", targetCenter=" + geometry.targetCenter
                            + ", desiredCenter=" + desiredCenter
                            + ", rowHeight=" + activeView.getHeight());
        }
        if (!LyricsRecyclerPolicy.shouldFollowAdaptiveLineTimedRow(
                adaptiveLineTimedRecyclerFollowActive,
                transitionSettled,
                targetIndex,
                lastAdaptiveLineTimedRecyclerAlignedIndex)) {
            return;
        }
        if (forceAlignLyricsRecyclerToIndex(
                recycler,
                targetIndex,
                "adaptive-native-line-timed",
                true,
                true)) {
            lastAdaptiveLineTimedRecyclerAlignedIndex = targetIndex;
            lastLyricsRecyclerIndex = targetIndex;
        }
    }

    private void resetAdaptiveLineTimedRecyclerFollow() {
        adaptiveLineTimedRecyclerFollowActive = false;
        lastAdaptiveLineTimedRecyclerAlignedIndex = -1;
        adaptiveLineTimedLagCandidateIndex = -1;
        adaptiveLineTimedLagObservedAtElapsedMs = -1L;
    }

    private int computeLyricsRecyclerTopOffset(View recyclerView, int targetIndex) {
        if (recyclerView == null || recyclerView.getHeight() <= 0) {
            return 0;
        }
        int desiredCenter = computeActiveLyricsRecyclerDesiredCenter(recyclerView);
        return computeLyricsRecyclerTopOffset(recyclerView, targetIndex, desiredCenter);
    }

    private int computeActiveLyricsRecyclerDesiredCenter(View recyclerView) {
        if (recyclerView == null || recyclerView.getHeight() <= 0) {
            return 0;
        }
        float shiftedOffsetDp = ACTIVE_LYRIC_CENTER_OFFSET_DP - ACTIVE_LYRIC_POSITION_SHIFT_UP_DP;
        return recyclerView.getHeight() / 2 + dp(recyclerView.getContext(), shiftedOffsetDp);
    }

    private int computeLyricsRecyclerTopOffset(
            View recyclerView,
            int targetIndex,
            int desiredCenter) {
        if (recyclerView == null || recyclerView.getHeight() <= 0) {
            return 0;
        }
        int itemHeight = findBoundLyricsRecyclerItemHeight(recyclerView, targetIndex);
        if (itemHeight <= 0) {
            itemHeight = dp(recyclerView.getContext(), WordLyricRenderConstants.LYRIC_SLOT_HEIGHT_DP);
        }
        return Math.max(0, desiredCenter - itemHeight / 2);
    }

    private static int findBoundLyricsRecyclerItemHeight(View recyclerView, int targetIndex) {
        if (!(recyclerView instanceof ViewGroup) || targetIndex < 0) {
            return 0;
        }
        ViewGroup group = (ViewGroup) recyclerView;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (readRecyclerChildPosition(recyclerView, child) == targetIndex) {
                return child.getHeight();
            }
        }
        return 0;
    }

    private static boolean hasBoundLyricsRecyclerChildren(View recycler) {
        return recycler instanceof ViewGroup && ((ViewGroup) recycler).getChildCount() > 0;
    }

    private boolean invokeLyricsRecyclerSetCurrentLyric(View recycler, int targetIndex) {
        if (lyricsRecyclerSetCurrentUnavailable
                || targetIndex < 0
                || recycler == null
                || !recycler.isAttachedToWindow()
                || !shouldModulePositionLyricsRecycler()) {
            return false;
        }
        Class<?> current = recycler.getClass();
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!isLyricsRecyclerCurrentLyricMethod(method)) {
                    continue;
                }
                Object[] args = new Object[parameterTypes.length];
                int integerIndex = 0;
                WordLyricModel model = currentWordLyricModel;
                WordLine targetLine = model == null ? null : model.lineAtAdapterIndex(targetIndex);
                long targetTimeMillis = targetLine == null ? 0L : targetLine.timeMillis;
                if (parameterTypes.length == 2
                        && isBooleanParameter(parameterTypes[0])
                        && parameterTypes[1] == long.class
                        && targetLine == null) {
                    continue;
                }
                boolean compatible = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> type = parameterTypes[i];
                    if (type == int.class || type == Integer.class) {
                        args[i] = integerIndex++ == 0 ? targetIndex : -1;
                    } else if (isBooleanParameter(type)) {
                        args[i] = false;
                    } else if (type == long.class || type == Long.class) {
                        args[i] = targetTimeMillis;
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
                    if (isOfficialTimedCurrentLyricMethod(method)) {
                        applyVisibleLyricBlockHeights(recycler);
                    }
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
        if (view == null) {
            return false;
        }
        Class<?> viewClass = view.getClass();
        synchronized (LYRICS_RECYCLER_VIEW_CLASS_MATCH_CACHE) {
            Boolean cached = LYRICS_RECYCLER_VIEW_CLASS_MATCH_CACHE.get(viewClass);
            if (cached != null) {
                return cached;
            }
            boolean matches = hasLyricsRecyclerViewClassName(viewClass);
            LYRICS_RECYCLER_VIEW_CLASS_MATCH_CACHE.put(viewClass, matches);
            return matches;
        }
    }

    private static boolean hasLyricsRecyclerViewClassName(Class<?> viewClass) {
        Class<?> current = viewClass;
        while (current != null) {
            if (LYRICS_RECYCLER_VIEW_CLASS.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private void stabilizeLyricsRecyclerScroll(View recycler, String reason) {
        if (recycler == null || !shouldModulePositionLyricsRecycler()) {
            return;
        }
        stopLyricsRecyclerScroll(recycler);
        maybeLogRecyclerScrollStabilize(recycler, reason);
    }

    private void applyVisibleLyricBlockHeights(View root) {
        refreshLyricUiStyleSettingsIfNeeded();
        applyVisibleLyricBlockHeights(root, currentSettlingOfficialLyricIndex());
    }

    private int currentSettlingOfficialLyricIndex() {
        return lyricRecyclerSettleOfficialIndex >= 0
                && SystemClock.elapsedRealtime() < lyricRecyclerSettleUntilElapsedMs
                ? lyricRecyclerSettleOfficialIndex
                : -1;
    }

    private void applyVisibleLyricBlockHeights(View root, int activeSlotIndex) {
        WordLyricModel model = currentWordLyricModel;
        if (model == null || root == null || !root.isAttachedToWindow()) {
            return;
        }
        applyVisibleLyricBlockHeights(root, model, activeSlotIndex, new int[]{0});
    }

    private void offsetLyricsRecyclerCurrentLine(View recyclerView, int targetIndex) {
        if (recyclerView == null
                || targetIndex < 0
                || !recyclerView.isAttachedToWindow()
                || recyclerView.getHeight() <= 0
                || !shouldModulePositionLyricsRecycler()) {
            return;
        }
        int desiredCenter = computeActiveLyricsRecyclerDesiredCenter(recyclerView);
        int topOffset = computeLyricsRecyclerTopOffset(recyclerView, targetIndex);
        scrollLyricsRecyclerToPositionWithOffset(recyclerView, targetIndex, topOffset);
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

    private void applyVisibleLyricBlockHeights(
            View view,
            WordLyricModel model,
            int activeSlotIndex,
            int[] visited) {
        if (view == null || visited[0]++ > 300 || view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            if (text != null
                    && text.length() > 0
                    && text.length() <= lyricTextLengthLimit()
                    && isInLyricsRecyclerView(textView)
                    && lyricRenderTransactions.rowStatus(
                    findContainingLyricsRecyclerView(textView), textView, model)
                    == LyricRenderTransactions.Status.READY) {
                LyricTextMatch match = findLyricTextMatch(
                        model,
                        textView,
                        normalizedTextOf(textView),
                        estimatePlaybackPositionMillis());
                if (match.line != null) {
                    prepareOfficialLyricTextView(textView);
                    officialLyricTextRenderer.applySlotHeight(textView, match.line, false);
                }
            }
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            applyVisibleLyricBlockHeights(
                    viewGroup.getChildAt(i),
                    model,
                    activeSlotIndex,
                    visited);
        }
    }

    private void scheduleOfficialLyricSlotPrebind(TextView textView) {
        if (currentWordLyricModel == null
                || textView == null
                || !textView.isAttachedToWindow()) {
            return;
        }
        CharSequence text = textView.getText();
        if (text == null || text.length() == 0 || text.length() > lyricTextLengthLimit()) {
            return;
        }
        prebindOfficialLyricSlotAfterTextMutation(textView);
        textView.postInvalidateOnAnimation();
    }

    private int beginLyricTextBindEpoch(TextView textView) {
        CompatibilityStatus.global().forgetOwner(textView);
        if (textView == null) {
            return -1;
        }
        synchronized (lyricTextBindEpochsLock) {
            nextLyricTextBindEpoch = nextLyricTextBindEpoch == Integer.MAX_VALUE
                    ? 1
                    : nextLyricTextBindEpoch + 1;
            int epoch = nextLyricTextBindEpoch;
            lyricTextBindEpochs.put(textView, epoch);
            lyricTextBindRetryPostedEpochs.remove(textView);
            lyricTextBindRetryCounts.remove(textView);
            boundLyricFrameStamps.remove(textView);
            return epoch;
        }
    }

    private int ensureLyricTextBindEpoch(TextView textView) {
        if (textView == null) {
            return -1;
        }
        synchronized (lyricTextBindEpochsLock) {
            Integer epoch = lyricTextBindEpochs.get(textView);
            if (epoch != null) {
                return epoch;
            }
        }
        return beginLyricTextBindEpoch(textView);
    }

    private void scheduleBoundLyricFrameRetry(TextView textView, WordLyricModel expectedModel) {
        if (textView == null || expectedModel == null) {
            return;
        }
        int epoch = ensureLyricTextBindEpoch(textView);
        if (epoch < 0) {
            return;
        }
        synchronized (lyricTextBindEpochsLock) {
            Integer postedEpoch = lyricTextBindRetryPostedEpochs.get(textView);
            int retryCount = lyricTextBindRetryCounts.getOrDefault(textView, 0);
            if ((postedEpoch != null && postedEpoch == epoch)
                    || retryCount >= LYRIC_BOUND_FRAME_RETRY_MAX) {
                return;
            }
            lyricTextBindRetryPostedEpochs.put(textView, epoch);
            lyricTextBindRetryCounts.put(textView, retryCount + 1);
        }
        int expectedContentHash = charSequenceContentHash(textView.getText());
        View expectedRecycler = findContainingLyricsRecyclerView(textView);
        textView.postOnAnimation(() -> {
            synchronized (lyricTextBindEpochsLock) {
                Integer postedEpoch = lyricTextBindRetryPostedEpochs.get(textView);
                lyricTextBindRetryPostedEpochs.remove(textView);
                Integer currentEpoch = lyricTextBindEpochs.get(textView);
                if (postedEpoch == null
                        || postedEpoch != epoch
                        || currentEpoch == null
                        || currentEpoch != epoch) {
                    return;
                }
            }
            if (currentWordLyricModel != expectedModel
                    || !textView.isAttachedToWindow()
                    || findContainingLyricsRecyclerView(textView) != expectedRecycler
                    || charSequenceContentHash(textView.getText()) != expectedContentHash) {
                return;
            }
            prebindOfficialLyricSlotAfterTextMutation(textView);
            textView.invalidate();
            textView.postInvalidateOnAnimation();
            if (isLyricVerboseDiagnosticsEnabled()) {
                info(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED, "Retried bound lyric frame"
                        + " | tvId=" + objectId(textView)
                        + ", rvId=" + objectId(expectedRecycler)
                        + ", bindEpoch=" + epoch);
            }
        });
    }

    private void markBoundLyricFrameReady(TextView textView, DrawFrame frame) {
        WordLyricModel model = frame == null ? null : frame.model;
        if (textView == null || model == null || frame.line == null) {
            return;
        }
        View recycler = findContainingLyricsRecyclerView(textView);
        if (recycler == null) {
            return;
        }
        int bindEpoch = ensureLyricTextBindEpoch(textView);
        if (bindEpoch < 0) {
            return;
        }
        int surfaceEpoch;
        synchronized (lyricSurfaceRenderEpochsLock) {
            LyricSurfaceReadinessState state = lyricSurfaceReadinessStates.get(recycler);
            if (state == null || state.model != model || state.stableReady) {
                return;
            }
            surfaceEpoch = state.epoch;
        }
        NormalizedTextSnapshot textSnapshot = normalizedLyricTextCache.get(textView);
        int contentHash = textSnapshot == null
                ? charSequenceContentHash(textView.getText())
                : textSnapshot.contentHash;
        boolean duplicateText = model.hasDuplicateRenderableText(normalizedTextOf(textView));
        int adapterPosition = duplicateText
                ? findValidLyricsRecyclerAdapterPosition(textView, model)
                : -1;
        synchronized (lyricTextBindEpochsLock) {
            lyricTextBindRetryCounts.remove(textView);
            BoundLyricFrameStamp existing = boundLyricFrameStamps.get(textView);
            if (existing != null
                    && existing.sameIdentity(
                    model,
                    recycler,
                    bindEpoch,
                    contentHash,
                    surfaceEpoch,
                    adapterPosition,
                    frame.lineIndex)) {
                return;
            }
            boundLyricFrameStamps.put(
                    textView,
                    new BoundLyricFrameStamp(
                            model,
                            recycler,
                            bindEpoch,
                            contentHash,
                            surfaceEpoch,
                            adapterPosition,
                            frame.lineIndex));
        }
    }

    private boolean isLyricSurfaceCustomFrameReady(View recycler, WordLyricModel model) {
        if (recycler == null || model == null || !recycler.isAttachedToWindow()) {
            return false;
        }
        synchronized (lyricSurfaceRenderEpochsLock) {
            Integer epoch = lyricSurfaceRenderEpochs.get(recycler);
            LyricSurfaceReadinessState state = lyricSurfaceReadinessStates.get(recycler);
            return epoch != null
                    && state != null
                    && state.epoch == epoch
                    && state.model == model
                    && state.stableReady;
        }
    }

    private LyricSurfaceReadinessSnapshot captureLyricSurfaceReadiness(
            View recycler,
            WordLyricModel model,
            int surfaceEpoch) {
        if (model == null) {
            return new LyricSurfaceReadinessSnapshot(
                    0,
                    0,
                    0,
                    recycler instanceof ViewGroup
                            ? ((ViewGroup) recycler).getChildCount() : 0,
                    readLyricsRecyclerAdapterItemCount(recycler));
        }
        int[] counts = {0, 0, 0, 1};
        long position = estimatePlaybackPositionMillis();
        collectLyricSurfaceCustomFrameReadiness(
                recycler,
                recycler,
                model,
                surfaceEpoch,
                position,
                counts);
        int childCount = recycler instanceof ViewGroup
                ? ((ViewGroup) recycler).getChildCount()
                : 0;
        int adapterItemCount = readLyricsRecyclerAdapterItemCount(recycler);
        if (adapterItemCount < 0 && model != null) {
            adapterItemCount = !model.officialLines.isEmpty()
                    ? model.officialLines.size()
                    : model.lines.size();
        }
        return new LyricSurfaceReadinessSnapshot(
                counts[0],
                counts[1],
                counts[3],
                childCount,
                adapterItemCount);
    }

    private void collectLyricSurfaceCustomFrameReadiness(
            View view,
            View recycler,
            WordLyricModel model,
            int surfaceEpoch,
            long position,
            int[] counts) {
        if (view == null || counts[2]++ > 320) {
            return;
        }
        if (view instanceof TextView && view.isAttachedToWindow()) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            String normalizedText = text == null ? "" : normalizedTextOf(textView);
            if (text != null
                    && text.length() > 0
                    && text.length() <= lyricTextLengthLimit()
                    && model.hasRenderableText(normalizedText)
                    && findContainingLyricsRecyclerView(textView) == recycler
                    && lyricRenderTransactions.rowStatus(recycler, textView, model)
                    == LyricRenderTransactions.Status.READY) {
                counts[0]++;
                int bindEpoch = ensureLyricTextBindEpoch(textView);
                int contentHash = charSequenceContentHash(text);
                int adapterPosition = findValidLyricsRecyclerAdapterPosition(textView, model);
                boolean duplicateText = model.hasDuplicateRenderableText(normalizedText);
                int resolvedLineIndex = -1;
                if (duplicateText) {
                    LyricTextMatch match = findLyricTextMatch(
                            model,
                            textView,
                            normalizedText,
                            position);
                    WordLine resolvedLine = match.line != null
                            ? match.line
                            : match.translationLine;
                    resolvedLineIndex = model.indexOfLine(resolvedLine);
                }
                BoundLyricFrameStamp stamp;
                synchronized (lyricTextBindEpochsLock) {
                    stamp = boundLyricFrameStamps.get(textView);
                }
                if (stamp != null
                        && stamp.matches(
                        model,
                        recycler,
                        bindEpoch,
                        contentHash,
                        surfaceEpoch,
                        adapterPosition,
                        resolvedLineIndex,
                        duplicateText)) {
                    counts[1]++;
                }
                counts[3] = 31 * counts[3] + System.identityHashCode(textView);
                counts[3] = 31 * counts[3] + bindEpoch;
                counts[3] = 31 * counts[3] + contentHash;
                counts[3] = 31 * counts[3] + adapterPosition;
            }
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectLyricSurfaceCustomFrameReadiness(
                    group.getChildAt(i),
                    recycler,
                    model,
                    surfaceEpoch,
                    position,
                    counts);
        }
    }

    private static int readLyricsRecyclerAdapterItemCount(View recycler) {
        Object adapter = invokeNoArgByName(recycler, "getAdapter");
        Object itemCount = invokeNoArgByName(adapter, "getItemCount");
        return itemCount instanceof Number ? ((Number) itemCount).intValue() : -1;
    }

    private void forgetLyricTextViewCaches(TextView textView) {
        if (textView == null) {
            return;
        }
        normalizedLyricTextCache.remove(textView);
        recentOfficialDrawFrames.remove(textView);
        LYRICS_RECYCLER_MATCH_CACHE.remove(textView);
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            VIEW_LYRIC_RENDER_EFFECT_MODE.remove(textView);
            VIEW_BLUR_DISABLED.remove(textView);
        }
        synchronized (lyricTextBindEpochsLock) {
            lyricTextBindEpochs.remove(textView);
            lyricTextBindRetryPostedEpochs.remove(textView);
            lyricTextBindRetryCounts.remove(textView);
            boundLyricFrameStamps.remove(textView);
        }
    }

    private boolean shouldInspectLyricTextViewHooks() {
        // Render ownership is independent from keep-awake/visibility lifecycle. SystemUI builds
        // the next immersive lyric surface while alpha=0; the row still needs a complete custom
        // display list before the parent fade begins.
        if (currentWordLyricModel != null || systemUiLyricModeKeepAwakeActive) {
            return true;
        }
        if (officialLyricDrawSuppressedUntilElapsedMs <= 0L
                && lyricRecyclerFadeInUntilElapsedMs <= 0L
                && !pendingCustomLyricTakeoverFade) {
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        return now < officialLyricDrawSuppressedUntilElapsedMs
                || now < lyricRecyclerFadeInUntilElapsedMs
                || pendingCustomLyricTakeoverFade;
    }

    private boolean hasCachedLyricTextView(TextView textView) {
        if (textView == null) {
            return false;
        }
        LyricsRecyclerMatch match = LYRICS_RECYCLER_MATCH_CACHE.get(textView);
        return recentOfficialDrawFrames.containsKey(textView)
                || (match != null
                && match != NO_LYRICS_RECYCLER_MATCH
                && match.isUsable(textView));
    }

    private void prebindOfficialLyricSlotAfterTextMutation(TextView textView) {
        try {
            WordLyricModel model = currentWordLyricModel;
            if (model == null
                    || textView == null
                    || !textView.isAttachedToWindow()
                    || !isInLyricsRecyclerView(textView)) {
                return;
            }
            CharSequence text = textView.getText();
            if (text == null || text.length() == 0 || text.length() > lyricTextLengthLimit()) {
                return;
            }
            String normalizedText = normalizedTextOf(textView);
            if (TextUtils.isEmpty(normalizedText)) {
                return;
            }
            if (lyricRenderTransactions.rowStatus(
                    findContainingLyricsRecyclerView(textView), textView, model)
                    != LyricRenderTransactions.Status.READY) return;
            LyricTextMatch match = findLyricTextMatch(
                    model,
                    textView,
                    normalizedText,
                    estimatePlaybackPositionMillis());
            if (match.line == null) {
                return;
            }
            prepareOfficialLyricTextView(textView);
            officialLyricTextRenderer.applySlotHeight(textView, match.line);
            textView.invalidate();
            textView.postInvalidateOnAnimation();
        } catch (Throwable ignored) {
            // Text mutation happens on many SystemUI labels; never let a lyric guard affect them.
        }
    }

    private void tryInstallLyricsRecyclerViewHook(ClassLoader classLoader) {
        if (lyricsRecyclerHookInstallAttempted || classLoader == null) {
            return;
        }
        try {
            Class<?> lyricsRecyclerViewClass =
                    classLoader.loadClass(LYRICS_RECYCLER_VIEW_CLASS);
            tryInstallLyricsRecyclerViewHook(lyricsRecyclerViewClass);
        } catch (Throwable ignored) {
            // The Seedling plugin class is loaded lazily. The plugin ClassLoader constructor or
            // factory hook, or its first View attachment, installs the lyric-local hooks.
        }
    }

    @SuppressLint("PrivateApi") // LSPosed hook targets the vendor plugin class loader by name.
    private synchronized void tryInstallPluginClassLoaderConstructorHook(
            ClassLoader classLoader) {
        if (pluginClassLoaderConstructorHookInstalled || classLoader == null) {
            return;
        }
        try {
            Class<?> pluginClassLoaderClass =
                    classLoader.loadClass(OPLUS_PLUGIN_CLASS_LOADER_CLASS);
            Constructor<?>[] constructors = pluginClassLoaderClass.getDeclaredConstructors();
            int hooked = 0;
            for (Constructor<?> constructor : constructors) {
                constructor.setAccessible(true);
                hook(constructor)
                        .setId(HOOK_ID_PLUGIN_CLASS_LOADER_CONSTRUCTOR
                                + "-"
                                + hooked
                                + "-"
                                + constructor.getParameterTypes().length)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onPluginClassLoaderConstructed);
                hooked++;
            }
            int factoryHooks = tryInstallPluginClassLoaderFactoryHook(classLoader);
            pluginClassLoaderConstructorHookInstalled = hooked + factoryHooks > 0;
            if (hooked > 0) {
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus plugin ClassLoader constructors, methods=" + hooked);
            }
            if (factoryHooks > 0) {
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED,
                        "Hooked OPlus plugin ClassLoader factory, constructorHooks=" + hooked);
            }
            if (!pluginClassLoaderConstructorHookInstalled) {
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_FAILED,
                        "No OPlus plugin ClassLoader constructor or factory; only the"
                                + " LyricsRecyclerView hooks install, on first attachment");
            }
        } catch (Throwable t) {
            error("Failed to hook OPlus plugin ClassLoader constructors", t);
        }
    }

    /**
     * ColorOS 17 inlined the OPlusPluginClassLoader constructor into
     * {@code PluginInstance$PluginFactory.createClassLoader()}, which assigns the loader's base
     * and package filter only after construction. Its return value is the first point where the
     * loader can resolve plugin classes.
     */
    @SuppressLint("PrivateApi") // LSPosed hook targets the vendor plugin factory by name.
    private int tryInstallPluginClassLoaderFactoryHook(ClassLoader classLoader) {
        Method factoryMethod;
        try {
            factoryMethod = classLoader.loadClass(OPLUS_PLUGIN_FACTORY_CLASS)
                    .getDeclaredMethod(OPLUS_PLUGIN_FACTORY_CREATE_CLASS_LOADER_METHOD);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return 0;
        }
        if (!OplusPluginClassLoaderPolicy.isClassLoaderFactory(factoryMethod)) {
            return 0;
        }
        try {
            factoryMethod.setAccessible(true);
            XposedInterface.HookHandle handle = hook(factoryMethod)
                    .setId(HOOK_ID_PLUGIN_CLASS_LOADER_FACTORY)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onPluginClassLoaderCreated);
            return handle == null ? 0 : 1;
        } catch (Throwable t) {
            error("Failed to hook OPlus plugin ClassLoader factory", t);
            return 0;
        }
    }

    private Object onPluginClassLoaderConstructed(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object thisObject = chain.getThisObject();
        if (thisObject instanceof ClassLoader) {
            onPluginClassLoaderReady((ClassLoader) thisObject);
        }
        return result;
    }

    private Object onPluginClassLoaderCreated(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (result instanceof ClassLoader
                && OplusPluginClassLoaderPolicy.isPluginClassLoader(
                        result, OPLUS_PLUGIN_CLASS_LOADER_CLASS)) {
            onPluginClassLoaderReady((ClassLoader) result);
        }
        return result;
    }

    private void onPluginClassLoaderReady(ClassLoader pluginLoader) {
        // A loader whose package filter is still unassigned cannot load plugin classes yet; the
        // factory hook (ColorOS 17) or the first lyric view attachment installs the hooks then.
        if (!OplusPluginClassLoaderPolicy.isReady(pluginLoader)) {
            return;
        }
        io.github.andrealtb.lockscreenlyrics.diagnostics.LoadedComponentFingerprints.global().observed("plugin", pluginLoader);
        dynamicArtworkRuntime.onPluginClassLoader(pluginLoader);
        synchronized (pluginHookGenerationLock) {
            if (readyPluginClassLoader.get() == pluginLoader) {
                return;
            }
            readyPluginClassLoader = new WeakReference<>(pluginLoader);
        }
        activatePluginHookGeneration(pluginLoader);
        tryInstallLyricsRecyclerViewHook(pluginLoader);
        tryInstallKuWoPluginMediaModelHook(pluginLoader);
        tryInstallLyricPositionControllerHook(pluginLoader);
        tryInstallShortWindowDiagnostics(pluginLoader);
    }

    private void activatePluginHookGeneration(ClassLoader pluginLoader) {
        if (pluginLoader == null) {
            return;
        }
        ArrayList<XposedInterface.HookHandle> staleHandles;
        synchronized (pluginHookGenerationLock) {
            if (activePluginClassLoader.get() == pluginLoader) {
                return;
            }
            staleHandles = new ArrayList<>(activePluginHookHandles);
            activePluginHookHandles.clear();
            activePluginClassLoader = new WeakReference<>(pluginLoader);
            lyricsRecyclerHookInstallAttempted = false;
            lyricsRecyclerSetCurrentUnavailable = false;
            oplusPluginMediaModelHookInstalled = false;
            oplusPluginKuWoTargets = null;
            resolvedLyricPositionController = null;
            lyricPositionControllerHooked = false;
            shortWindowDiagnosticsInstalled = false;
            lyricDatasetHookAvailable = false;
            lyricAdapterBindHookClasses.clear();
            lyricRenderTransactions.clearBindings();
        }
        synchronized (officialLyricsRecyclerCompatibilityLock) {
            officialLyricsRecyclerBindings.clear();
            unavailableOfficialLyricsRecyclerBindings.clear();
            immersiveCurrentIndexField = null;
        }
        for (XposedInterface.HookHandle handle : staleHandles) {
            try {
                handle.unhook();
            } catch (Throwable ignored) {
                // The old plugin generation may already have been detached by SystemUI.
            }
        }
    }

    private void rememberPluginHookHandle(
            ClassLoader pluginLoader,
            XposedInterface.HookHandle handle) {
        if (pluginLoader == null || handle == null) {
            return;
        }
        boolean current;
        synchronized (pluginHookGenerationLock) {
            current = activePluginClassLoader.get() == pluginLoader;
            if (current) {
                activePluginHookHandles.add(handle);
            }
        }
        if (!current) {
            try {
                handle.unhook();
            } catch (Throwable ignored) {
                // A newer loader already owns the plugin hooks.
            }
        }
    }

    private void tryInstallKuWoPluginMediaModelHook(ClassLoader pluginLoader) {
        if (oplusPluginMediaModelHookInstalled || pluginLoader == null) {
            return;
        }
        try {
            OplusPluginDexKitAdapter.Targets targets;
            try {
                OplusPluginDexKitAdapter.Resolution resolution =
                        OplusPluginDexKitAdapter.resolveAll(pluginLoader, LYRICS_RECYCLER_VIEW_CLASS);
                // Captured before the media-model check so a model miss cannot drop it.
                resolvedLyricPositionController = resolution.lyricPositionController;
                if (resolution.lyricPositionController == null) {
                    // The legacy timed controller is optional. ColorOS 17 aligns through the
                    // immersive entry instead, so this is not a whole-clock failure.
                    infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.DETAIL,
                            "Legacy timed lyric controller absent; using entry/row-style alignment: "
                                    + resolution.lyricPositionControllerFailure);
                }
                targets = resolution.requireTargets();
            } catch (Throwable dexKitFailure) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "OPlus plugin DexKit resolution failed, using m6 fallback: "
                                + dexKitFailure);
                targets = OplusPluginDexKitAdapter.legacy(pluginLoader);
            }
            Class<?> modelClass = targets.mediaModelClass;
            oplusPluginKuWoTargets = targets;
            Constructor<?>[] constructors = modelClass.getDeclaredConstructors();
            int hooked = 0;
            for (Constructor<?> constructor : constructors) {
                constructor.setAccessible(true);
                XposedInterface.HookHandle handle = hook(constructor)
                        .setId(HOOK_ID_PLUGIN_CLASS_LOADER_CONSTRUCTOR
                                + "-kuwo-model-" + hooked + "-"
                                + constructor.getParameterTypes().length)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onKuWoPluginMediaModelBuilt);
                rememberPluginHookHandle(pluginLoader, handle);
                hooked++;
            }
            if (hooked > 0) {
                oplusPluginMediaModelHookInstalled = true;
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus KuWo plugin media model, constructors=" + hooked
                        + ", resolver=" + (targets.resolvedByDexKit ? "dexkit" : "legacy")
                        + ", lyricSupportedField=" + targets.lyricSupportedField.getName()
                        + ", albumArtRepair=" + (targets.artwork != null
                                ? "on"
                                : "off: " + targets.artworkFailure));
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Skipped KuWo plugin media model hook: " + t);
        }
    }

    private void tryInstallLyricPositionControllerHook(ClassLoader pluginLoader) {
        Method controller = resolvedLyricPositionController;
        if (lyricPositionControllerHooked || controller == null || pluginLoader == null) {
            return;
        }
        try {
            XposedInterface.HookHandle handle = hook(controller)
                    .setId(HOOK_ID_LYRICS_RECYCLER + "-controller-position")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onLyricControllerPositionUpdate);
            rememberPluginHookHandle(pluginLoader, handle);
            lyricPositionControllerHooked = true;
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED,
                    "Hooked lyric position controller, method="
                            + controller.getDeclaringClass().getName() + "#" + controller.getName());
        } catch (Throwable t) {
            error("Failed to hook lyric position controller", t);
        }
    }

    /**
     * Diagnostic-only F06 probe. It observes the immersive lyric transform and the model
     * constructors without changing the emitted model. Enable the renderer debug area to see
     * {@code short-window-probe}; the hook is PROTECTIVE and cannot alter the vendor result.
     */
    private void tryInstallShortWindowDiagnostics(ClassLoader pluginLoader) {
        if (shortWindowDiagnosticsInstalled || pluginLoader == null) {
            return;
        }
        try {
            Class<?> lambda = pluginLoader.loadClass(
                    "com.oplus.systemui.plugins.shared.template.section.media."
                            + "MPImmersiveSectionViewModel$immersiveLyricsModel$2");
            int hooks = 0;
            for (Method method : lambda.getDeclaredMethods()) {
                if (!"invoke".equals(method.getName()) || method.getParameterCount() != 3) {
                    continue;
                }
                method.setAccessible(true);
                XposedInterface.HookHandle handle = hook(method)
                        .setId("short-window-probe-invoke-" + hooks)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onShortWindowProbeInvoke);
                rememberPluginHookHandle(pluginLoader, handle);
                hooks++;
            }
            Class<?> modelClass = pluginLoader.loadClass(
                    "com.oplus.systemui.plugins.shared.template.section.model.media.h");
            for (Constructor<?> constructor : modelClass.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                XposedInterface.HookHandle handle = hook(constructor)
                        .setId("short-window-probe-model-" + constructor.getParameterCount())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onShortWindowProbeModelBuilt);
                rememberPluginHookHandle(pluginLoader, handle);
                hooks++;
            }
            shortWindowDiagnosticsInstalled = hooks > 0;
            if (hooks > 0) {
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED,
                        "Installed short-window diagnostics, hooks=" + hooks);
            } else {
                warn(LyricLogFormatter.Area.SYSTEM_UI, "short-window-probe",
                        "Short-window diagnostics found no hook targets");
            }
        } catch (Throwable t) {
            warn(LyricLogFormatter.Area.SYSTEM_UI, "short-window-probe",
                    "Short-window diagnostics unavailable: " + t);
        }
    }

    private Object onShortWindowProbeInvoke(XposedInterface.Chain chain) throws Throwable {
        try {
            Object triple = chain.getArg(1);
            boolean kotlinTriple = triple != null
                    && "kotlin.Triple".equals(triple.getClass().getName());
            String accessor = kotlinTriple
                    ? firstNoArgMethodName(triple, "getFirst", "a", "d")
                    : null;
            if (accessor == null) {
                maybeLogShortWindowProbe(
                        "lambda-invoke", "triple-unavailable", -1, triple, false);
            } else {
                Object first = invokeNoArgByName(triple, accessor);
                Object lyricModel = readFieldOrMissing(first, "p");
                String lines;
                if (lyricModel == PROBE_FIELD_MISSING) {
                    lines = "model-unreadable";
                } else if (lyricModel == null) {
                    lines = "model-null";
                } else {
                    lines = describeProbeLines(readFieldOrMissing(lyricModel, "a"));
                }
                maybeLogShortWindowProbe("lambda-invoke", lines, -1, first, false);
            }
        } catch (Throwable ignored) {
            // Diagnostics never own the vendor result.
        }
        return chain.proceed();
    }

    private Object onShortWindowProbeModelBuilt(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            Object model = chain.getThisObject();
            Object lines = readFieldOrMissing(model, "a");
            int index = readIntField(model, "b", -1);
            int arity = chain.getArgs().size();
            maybeLogShortWindowProbe(
                    "model-ctor/" + arity, describeProbeLines(lines), index, model, true);
        } catch (Throwable ignored) {
            // Diagnostics never own the vendor result.
        }
        return result;
    }

    /**
     * Diagnostic-only F06 probe. It observes the immersive lyric transform and the model
     * constructors without changing the emitted model. Per-stage throttling plus
     * {@link StructuredBridgeLog#debugTransition} preserves the input and output stages of one
     * transform; the shared structured-log throttle would otherwise drop the second stage.
     */
    private void maybeLogShortWindowProbe(
            String stage, String lines, int index, Object owner, boolean modelStage) {
        long now = SystemClock.elapsedRealtime();
        if (modelStage) {
            if (now - lastShortWindowModelProbeAt < 1_000L) {
                return;
            }
            lastShortWindowModelProbeAt = now;
        } else {
            if (now - lastShortWindowLambdaProbeAt < 1_000L) {
                return;
            }
            lastShortWindowLambdaProbeAt = now;
        }
        StructuredBridgeLog.debugTransition(BridgeDebugArea.RENDERER, "short-window-probe",
                () -> "stage=" + stage
                        + ", lines=" + lines
                        + ", index=" + index
                        + ", owner=" + (owner == null ? "null" : owner.getClass().getSimpleName())
                        + ", thread=" + Thread.currentThread().getName());
    }

    /**
     * Aligns the position the vendor controller forwards to the timed lyric method and uses for
     * its next row-change timer. Only the position argument changes; see
     * {@link NativeLyricClockPolicy}.
     */
    private Object onLyricControllerPositionUpdate(XposedInterface.Chain chain) throws Throwable {
        Object positionArg = chain.getArg(0);
        if (!(positionArg instanceof Long) || !isModuleLyricClockUsable()) {
            return chain.proceed();
        }
        long vendorPosition = (Long) positionArg;
        long aligned = NativeLyricClockPolicy.controllerPosition(
                vendorPosition,
                estimatePlaybackPositionMillis(),
                LyricTimingTuningConstants.LyricGeneral.PLAYBACK_SMALL_CORRECTION_MS);
        if (aligned == vendorPosition) {
            return chain.proceed();
        }
        Object[] args = chain.getArgs().toArray(new Object[0]);
        args[0] = aligned;
        logNativeLyricClockAlignment("controller", vendorPosition, aligned);
        return chain.proceed(args);
    }

    /**
     * The module clock may drive the vendor list only while it runs from a real PlaybackState
     * anchor and the module owns the rendered model; otherwise vendor timing is untouched.
     */
    private boolean isModuleLyricClockUsable() {
        return lastPlaybackIsPlaying
                && lastComputedPositionMs >= 0L
                && lastComputedPositionElapsedMs >= 0L
                && currentWordLyricModel != null
                && !hasSentenceWindow();
    }

    /**
     * Returns replacement arguments for the vendor timed-lyric call, or null to keep them. Only
     * the monotonic guard applies here; see {@link NativeLyricClockPolicy#timedLyricPosition}.
     */
    private Object[] guardTimedLyricArgs(XposedInterface.Chain chain, View recyclerView) {
        Object positionArg = chain.getArg(1);
        WordLyricModel model = currentWordLyricModel;
        if (!(positionArg instanceof Number) || recyclerView == null || !isModuleLyricClockUsable()) {
            timedLyricGuardPosition = -1L;
            return null;
        }
        long now = SystemClock.elapsedRealtime();
        long vendorPosition = ((Number) positionArg).longValue();
        long held = timedLyricGuardRecycler.get() == recyclerView && timedLyricGuardModel.get() == model
                ? NativeLyricClockPolicy.heldPosition(
                timedLyricGuardPosition, timedLyricGuardElapsedMs, now, lastPlaybackSpeed)
                : -1L;
        long guarded = NativeLyricClockPolicy.timedLyricPosition(
                vendorPosition,
                held,
                LyricTimingTuningConstants.LyricGeneral.PLAYBACK_SMALL_CORRECTION_MS);
        timedLyricGuardRecycler = new WeakReference<>(recyclerView);
        timedLyricGuardModel = new WeakReference<>(model);
        timedLyricGuardPosition = guarded;
        timedLyricGuardElapsedMs = now;
        if (guarded == vendorPosition) {
            return null;
        }
        Object[] args = chain.getArgs().toArray(new Object[0]);
        args[1] = guarded;
        logNativeLyricClockAlignment("guard", vendorPosition, guarded);
        return args;
    }

    private void logNativeLyricClockAlignment(String source, long vendorPosition, long aligned) {
        StructuredBridgeLog.debug(
                BridgeDebugArea.RENDERER,
                BridgeEvents.NATIVE_CLOCK_ALIGNED,
                () -> "Aligned vendor lyric clock, source=" + source
                        + ", vendor=" + vendorPosition
                        + ", aligned=" + aligned
                        + ", deltaMs=" + (aligned - vendorPosition)
                        + ", controllerHooked=" + lyricPositionControllerHooked);
    }

    private Object onKuWoPluginMediaModelBuilt(XposedInterface.Chain chain) throws Throwable {
        Object model = chain.proceed();
        if (model == null) {
            model = chain.getThisObject();
        }
        if (model == null || !KuWoPluginMediaModelReader.containsPlayerPackage(model)) {
            return model;
        }
        try {
            OplusPluginDexKitAdapter.Targets targets = oplusPluginKuWoTargets;
            String description = String.valueOf(model);
            String title = readKuWoMediaModelText(
                    model,
                    description,
                    "h",
                    "f6173h",
                    "songName=",
                    ", artist=");
            String artist = readKuWoMediaModelText(
                    model,
                    description,
                    "i",
                    "f6174i",
                    "artist=",
                    ", iconBeforeArtist=");
            // A missing label counts as supported so it can never trigger a restore.
            boolean lyricSupported = !Boolean.FALSE.equals(
                    KuWoPluginMediaModelReader.readLyricSupported(description));
            String trackKey = buildTrackKey(title, artist);
            Field lyricField = targets == null
                    ? findFieldOfType(model.getClass(), "s")
                    : targets.lyricModelField;
            Object lyricModel = lyricField == null ? null : readField(model, lyricField);
            int lineCount = countLyricModelLines(lyricModel);
            boolean albumArtRepaired = false;
            KuWoSameTrackLyricRetention retention = kuWoRuntime.retention();
            synchronized (retention.lock()) {
                KuWoSameTrackLyricRetention.Result result = retention.decide(
                        trackKey,
                        title,
                        artist,
                        lyricModel,
                        lineCount,
                        lyricSupported);
                if (result.repairAlbumArt) {
                    albumArtRepaired = repairKuWoPluginAlbumArt(
                            model,
                            result.repairTitle,
                            result.repairArtist);
                }
                boolean lyricSupportRestored = false;
                if (result.lyricToRestore != null) {
                    if (lyricField != null) {
                        lyricField.setAccessible(true);
                        lyricField.set(model, result.lyricToRestore);
                    }
                    setKuWoPluginLyricSupported(model, targets);
                    lineCount = countLyricModelLines(result.lyricToRestore);
                } else if (result.restoreLyricSupport) {
                    lyricSupportRestored = setKuWoPluginLyricSupported(model, targets);
                }
                // Commit even when repairAlbumArt returned false. Repair swallows
                // failures; skipping commit here would drop same-track memory.
                retention.commit(result, trackKey, title, artist, lyricModel);
                long now = SystemClock.elapsedRealtime();
                if (result.action == KuWoSameTrackLyricRetention.Action.RETAIN_CAR_LYRIC_MUTATION
                        && kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, "Ignored KuWo car-lyric metadata mutation while retaining"
                            + " same-track plugin model, lines=" + lineCount);
                } else if (result.action
                        == KuWoSameTrackLyricRetention.Action.CLEAR_FOR_TRACK_CHANGE
                        && kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Cleared KuWo plugin lyric retention for real track change: "
                            + trackKey);
                } else if (result.action
                        == KuWoSameTrackLyricRetention.Action.RESTORE_EMPTY_MODEL
                        && kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Retained same-track KuWo plugin lyric model, lines=" + lineCount);
                } else if (lyricSupportRestored && kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Restored same-track KuWo plugin lyric support, lines=" + lineCount);
                }
            }
            if (albumArtRepaired) {
                long now = SystemClock.elapsedRealtime();
                if (kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Repaired KuWo plugin album art from same-track snapshot, "
                            + "lines=" + lineCount);
                }
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo plugin media model retention failed: " + t);
        }
        return model;
    }

    private boolean repairKuWoPluginAlbumArt(
            Object model,
            String title,
            String artist) {
        OplusPluginDexKitAdapter.Targets targets = oplusPluginKuWoTargets;
        if (targets == null) {
            logKuWoPluginAlbumArtSkip(title, artist, "missing-plugin-targets");
            return false;
        }
        // The install log already reports why repair is off for this plugin build.
        OplusPluginDexKitAdapter.Artwork artwork = targets.artwork;
        if (artwork == null) {
            return false;
        }
        Field albumArtField = artwork.albumArtField;
        Object albumArt = readField(model, albumArtField);
        if (albumArt == null) {
            logKuWoPluginAlbumArtSkip(title, artist, "no-album-art");
            return false;
        }
        try {
            Object staticIcon = artwork.staticIconGetter.invoke(albumArt);
            if (staticIcon == null) {
                return false;
            }
            Object lottie = artwork.lottieIconGetter.invoke(albumArt);
            Field drawableField = artwork.staticDrawableField;
            Field bitmapField = artwork.staticBitmapField;
            Field cardModelField = artwork.cardIconModelField;
            Object cardModel = readField(staticIcon, cardModelField);
            Bitmap cardBitmap = cardModel == null
                    ? null
                    : (Bitmap) artwork.iconModelBitmapGetter.invoke(cardModel);
            Object staticBitmapValue = readField(staticIcon, bitmapField);
            Bitmap staticBitmap = staticBitmapValue instanceof Bitmap
                    ? (Bitmap) staticBitmapValue
                    : null;
            Object staticDrawableValue = readField(staticIcon, drawableField);
            Drawable staticDrawable = staticDrawableValue instanceof Drawable
                    ? (Drawable) staticDrawableValue
                    : null;
            boolean cardInvalid = !KuWoCoverPolicy.isPlausibleCoverBitmap(cardBitmap);
            boolean staticBitmapInvalid = !KuWoCoverPolicy.isPlausibleCoverBitmap(staticBitmap);
            boolean drawableInvalid = staticDrawable != null
                    && !KuWoCoverPolicy.isPlausibleCoverSize(
                            staticDrawable.getIntrinsicWidth(),
                            staticDrawable.getIntrinsicHeight());
            if (!cardInvalid && !staticBitmapInvalid && !drawableInvalid) {
                return false;
            }
            Icon snapshot = peekKuWoArtworkSnapshot(
                    buildTrackKey(title, artist), lastSystemUiMediaId);
            if (snapshot == null) {
                logKuWoPluginAlbumArtSkip(title, artist, "no-snapshot");
                return false;
            }
            Bitmap bitmap = extractIconBitmap(snapshot);
            if (!KuWoCoverPolicy.isPlausibleCoverBitmap(bitmap)) {
                logKuWoPluginAlbumArtSkip(title, artist, "bad-snapshot");
                return false;
            }
            Drawable repairedDrawable = snapshot.loadDrawable(currentApplicationContext());
            Integer primaryColor = cardModel == null
                    ? null
                    : (Integer) artwork.iconModelColorGetter.invoke(cardModel);
            Object repairedCardModel = artwork.normalIconConstructor.newInstance(
                    bitmap,
                    primaryColor);
            Object repairedMiniModel = artwork.normalIconConstructor.newInstance(
                    bitmap,
                    primaryColor);
            Object repairedStaticIcon = artwork.staticIconConstructor.newInstance(
                    snapshot,
                    repairedDrawable,
                    bitmap,
                    repairedMiniModel,
                    repairedCardModel);
            Object repairedAlbumArt = artwork.multiIconConstructor.newInstance(
                    repairedStaticIcon,
                    lottie);
            albumArtField.setAccessible(true);
            albumArtField.set(model, repairedAlbumArt);
            return true;
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to repair KuWo plugin album art: " + t);
            return false;
        }
    }

    /**
     * Sets the model's lyric-support flag and confirms it through the {@code isLyricSupported=}
     * label. A write that does not reach the label is undone, so a misidentified boolean (ColorOS
     * 17 keeps artworkFullBgEnable next to the flag) is never left changed. The model is still
     * inside its constructor hook, so no other thread can observe the probe.
     */
    private boolean setKuWoPluginLyricSupported(
            Object model,
            OplusPluginDexKitAdapter.Targets targets) throws IllegalAccessException {
        if (Boolean.TRUE.equals(KuWoPluginMediaModelReader.readLyricSupported(String.valueOf(model)))) {
            return true;
        }
        Field supportedField = targets == null
                ? findBooleanSuffixField(model.getClass(), "u")
                : targets.lyricSupportedField;
        if (supportedField == null) {
            return false;
        }
        supportedField.setAccessible(true);
        boolean previous = supportedField.getBoolean(model);
        supportedField.setBoolean(model, true);
        if (Boolean.TRUE.equals(KuWoPluginMediaModelReader.readLyricSupported(String.valueOf(model)))) {
            return true;
        }
        supportedField.setBoolean(model, previous);
        if (kuWoRuntime.takeLyricSupportMismatchLogOnce()) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo plugin lyric-support field did not match the isLyricSupported label;"
                            + " write undone, field=" + supportedField.getName());
        }
        return false;
    }

    private static String readKuWoMediaModelText(
            Object model,
            String description,
            String primaryField,
            String fallbackField,
            String label,
            String nextLabel) {
        String labeled = KuWoPluginMediaModelReader.readLabeledText(
                description,
                label,
                nextLabel);
        if (labeled != null) {
            return labeled;
        }
        return readStringFieldByName(model, primaryField, fallbackField);
    }

    private void logKuWoPluginAlbumArtSkip(String title, String artist, String reason) {
        if (!kuWoRuntime.takeMediaModelLog(SystemClock.elapsedRealtime())) {
            return;
        }
        info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Skipped KuWo plugin album art repair; reason=" + reason
                + ", track=" + buildTrackKey(title, artist));
    }

    private static String readStringFieldByName(
            Object target,
            String primaryName,
            String fallbackName) {
        Object value = readNamedFieldValue(target, primaryName);
        if (!(value instanceof String)) {
            value = readNamedFieldValue(target, fallbackName);
        }
        return value instanceof String ? (String) value : "";
    }

    private static Object readNamedFieldValue(Object target, String fieldName) {
        if (target == null || TextUtils.isEmpty(fieldName)) {
            return null;
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object target, Field field) {
        if (target == null || field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findFieldOfType(Class<?> type, String simpleName) {
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().getSimpleName().equals(simpleName)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findBooleanSuffixField(Class<?> type, String suffix) {
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == boolean.class && field.getName().endsWith(suffix)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static int countLyricModelLines(Object lyricModel) {
        if (lyricModel == null) {
            return 0;
        }
        Class<?> current = lyricModel.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() != List.class) {
                    continue;
                }
                Object value = readField(lyricModel, field);
                if (value instanceof List) {
                    return ((List<?>) value).size();
                }
            }
            current = current.getSuperclass();
        }
        return 0;
    }

    private synchronized void tryInstallLyricsRecyclerViewHook(Class<?> lyricsRecyclerViewClass) {
        if (lyricsRecyclerHookInstallAttempted || lyricsRecyclerViewClass == null) {
            return;
        }
        // A missing optional obfuscated guard method must not cause repeated hook installation.
        // Re-entering here used to duplicate every setCurrentLyric interceptor on each attach or
        // plugin ClassLoader construction when that method was absent on a firmware variant.
        ClassLoader pluginLoader = lyricsRecyclerViewClass.getClassLoader();
        activatePluginHookGeneration(pluginLoader);
        lyricsRecyclerHookInstallAttempted = true;
        try {
            int currentLyricHooks = 0;
            int scalePivotHooks = 0;
            int rowStyleHooks = 0;
            int immersiveEntryHooks = 0;
            int datasetHooks = 0;
            int drawHooks = 0;
            Class<?> current = lyricsRecyclerViewClass;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (drawHooks == 0 && "draw".equals(method.getName())
                            && method.getReturnType() == void.class
                            && method.getParameterTypes().length == 1
                            && method.getParameterTypes()[0] == Canvas.class) {
                        method.setAccessible(true);
                        XposedInterface.HookHandle handle = hook(method)
                                .setId(HOOK_ID_LYRICS_RECYCLER + "-surface-draw")
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(this::onLyricsRecyclerDraw);
                        rememberPluginHookHandle(pluginLoader, handle);
                        drawHooks++;
                    } else if ("setLyrics".equals(method.getName())
                            && method.getReturnType() == void.class
                            && method.getParameterTypes().length == 1
                            && List.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        method.setAccessible(true);
                        XposedInterface.HookHandle handle = hook(method)
                                .setId(HOOK_ID_LYRICS_RECYCLER + "-dataset-" + datasetHooks)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(this::onLyricsRecyclerSetLyrics);
                        rememberPluginHookHandle(pluginLoader, handle);
                        datasetHooks++;
                    } else if (isLyricsRecyclerCurrentLyricMethod(method)) {
                        method.setAccessible(true);
                        XposedInterface.HookHandle handle = hook(method)
                                .setId(HOOK_ID_LYRICS_RECYCLER
                                        + "-current-"
                                        + currentLyricHooks
                                        + "-"
                                        + method.getParameterTypes().length)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(this::onLyricsRecyclerSetCurrentLyric);
                        rememberPluginHookHandle(pluginLoader, handle);
                        currentLyricHooks++;
                    } else if (isLyricsRecyclerScalePivotMethod(method)) {
                        method.setAccessible(true);
                        XposedInterface.HookHandle handle = hook(method)
                                .setId(HOOK_ID_LYRICS_RECYCLER
                                        + "-pivot-"
                                        + scalePivotHooks)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(this::onLyricsRecyclerPrepareScalePivot);
                        rememberPluginHookHandle(pluginLoader, handle);
                        scalePivotHooks++;
                    } else if (OfficialLyricRowStyleMethodPolicy.matches(
                            method, lyricsRecyclerViewClass)) {
                        method.setAccessible(true);
                        XposedInterface.HookHandle handle = hook(method)
                                .setId(HOOK_ID_LYRICS_RECYCLER
                                        + "-row-style-"
                                        + rowStyleHooks)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(this::onLyricsRecyclerRowStyleApplied);
                        rememberPluginHookHandle(pluginLoader, handle);
                        rowStyleHooks++;
                    } else if (OfficialImmersiveLyricEntryPolicy.matches(
                            method, lyricsRecyclerViewClass)) {
                        method.setAccessible(true);
                        XposedInterface.HookHandle handle = hook(method)
                                .setId(HOOK_ID_LYRICS_RECYCLER
                                        + "-immersive-entry-"
                                        + immersiveEntryHooks)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(this::onLyricsRecyclerImmersiveEntry);
                        rememberPluginHookHandle(pluginLoader, handle);
                        immersiveEntryHooks++;
                    }
                }
                current = current.getSuperclass();
            }
            lyricDatasetHookAvailable = drawHooks > 0;
            if (currentLyricHooks > 0
                    || scalePivotHooks > 0
                    || rowStyleHooks > 0
                    || immersiveEntryHooks > 0 || datasetHooks > 0 || drawHooks > 0) {
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked LyricsRecyclerView runtime methods, currentMethods="
                        + currentLyricHooks
                        + ", pivotMethods=" + scalePivotHooks
                        + ", rowStyleMethods=" + rowStyleHooks
                        + ", immersiveEntryMethods=" + immersiveEntryHooks
                        + ", datasetMethods=" + datasetHooks
                        + ", surfaceDrawMethods=" + drawHooks);
            }
            if (currentLyricHooks == 0) {
                // Module-driven positioning invokes the ColorOS 16 entry only; the ColorOS 17
                // immersive entry is observed and aligned, never called by the module.
                lyricsRecyclerSetCurrentUnavailable = true;
                if (immersiveEntryHooks == 0) {
                    info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "No LyricsRecyclerView current lyric hook target found on "
                            + lyricsRecyclerViewClass.getName());
                }
            }
        } catch (Throwable t) {
            error("Failed to hook LyricsRecyclerView current lyric updates", t);
        }
    }

    private Object onLyricsRecyclerPrepareScalePivot(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        int textViewIndex = chain.getExecutable() instanceof Method
                ? OfficialLyricScalePivotMethodPolicy.textViewArgumentIndex(
                        (Method) chain.getExecutable())
                : OfficialLyricScalePivotMethodPolicy.NOT_A_PIVOT_METHOD;
        Object textView = textViewIndex >= 0 ? chain.getArg(textViewIndex) : null;
        if (textView instanceof TextView) {
            applyOfficialLyricScalePivot((TextView) textView);
        }
        return result;
    }

    private Object onLyricsRecyclerRowStyleApplied(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object textView = chain.getArg(0);
        if (textView instanceof TextView) {
            TextView row = (TextView) textView;
            if (Boolean.TRUE.equals(chain.getArg(1))) {
                rememberOfficialActiveLyricTypeface(row.getTypeface());
            }
            // The vendor just replaced the row RenderEffect (blur 0 active / 6px inactive). Drop the
            // cached Bridge effect so the next draw reapplies the configured blur over it.
            synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
                VIEW_LYRIC_RENDER_EFFECT_MODE.remove(row);
                VIEW_BLUR_DISABLED.remove(row);
            }
            row.invalidate();
        }
        return result;
    }

    /**
     * ColorOS 17 counterpart of {@link #onLyricsRecyclerSetCurrentLyric}: records the row the
     * vendor applies as the official index and keeps it on the module clock within the
     * small-correction band. Only the model position may change; see
     * {@link OfficialImmersiveLyricEntryPolicy}.
     */
    private Object onLyricsRecyclerImmersiveEntry(XposedInterface.Chain chain) throws Throwable {
        if (LyricsRecyclerPolicy.shouldSkipSetCurrentLyricHook(
                Boolean.TRUE.equals(suppressLyricsRecyclerHook.get()))) {
            return chain.proceed();
        }
        Object recycler = chain.getThisObject();
        View recyclerView = recycler instanceof View ? (View) recycler : null;
        Object model = chain.getArg(0);
        ImmersiveLyricsModelAccess.Snapshot snapshot = ImmersiveLyricsModelAccess.read(model);
        if (recyclerView == null || snapshot == null) {
            return chain.proceed();
        }
        rememberLyricsRecyclerView(recyclerView);
        long[] startTimes = ImmersiveLyricsModelAccess.startTimes(snapshot.lines);
        int vendorIndex = snapshot.index;
        int targetIndex = resolveImmersiveEntryIndex(recycler, vendorIndex, startTimes);
        Object[] args = null;
        if (targetIndex != vendorIndex) {
            Object aligned = targetIndex >= 0 && targetIndex < snapshot.lineCount()
                    ? ImmersiveLyricsModelAccess.withIndex(model, snapshot.lines, targetIndex)
                    : null;
            if (aligned != null) {
                args = chain.getArgs().toArray(new Object[0]);
                args[0] = aligned;
            } else {
                targetIndex = vendorIndex;
            }
        }
        int previousOfficialIndex = readLyricsRecyclerCurrentIndex(recyclerView);
        synchronized (IMMERSIVE_ENTRY_INDEXES) {
            IMMERSIVE_ENTRY_INDEXES.put(recycler, new ImmersiveEntryIndex(targetIndex, startTimes));
        }
        LyricsRecyclerGeometry beforeGeometry = null;
        try {
            if (recyclerView.isShown()) {
                activateSystemUiLyricModeFromSurface("immersiveEntry");
            }
            beforeGeometry = captureLyricsRecyclerGeometry(
                    recyclerView,
                    targetIndex >= 0 ? targetIndex : previousOfficialIndex);
        } catch (Throwable t) {
            error("Failed while reading LyricsRecyclerView immersive entry", t);
        }
        Object result = args != null ? chain.proceed(args) : chain.proceed();
        // Like the ColorOS 16 path, follow the row the vendor displays after the call: a new list
        // is applied only after its switch fade and diff, and a switch in progress ignores rows.
        Integer appliedIndex = readImmersiveCurrentIndexField(recyclerView);
        int observedTargetIndex = appliedIndex != null && appliedIndex >= 0
                ? appliedIndex
                : targetIndex;
        if (observedTargetIndex >= 0) {
            rememberLyricsRecyclerTargetIndex(observedTargetIndex);
        }
        maybeLogLyricsRecyclerSetCurrentGeometry(
                observedTargetIndex,
                beforeGeometry,
                captureLyricsRecyclerGeometry(recyclerView, observedTargetIndex));
        scheduleLyricsRecyclerOwnershipSnapshots(
                recyclerView,
                "official-current-immersive",
                observedTargetIndex);
        return result;
    }

    private int resolveImmersiveEntryIndex(Object recycler, int vendorIndex, long[] startTimes) {
        if (vendorIndex < 0 || startTimes == null) {
            return vendorIndex;
        }
        LyricInfoContract.Payload payload = currentLyricProviderPayload;
        if (payload != null && payload.snapshotPositionMillis >= 0L) {
            int pinned = OfficialImmersiveLyricEntryPolicy.indexAt(
                    startTimes,
                    payload.snapshotPositionMillis);
            if (pinned != vendorIndex) {
                info(BridgeDebugArea.RENDERER, BridgeEvents.DETAIL,
                        "Pinned native lyric index; current="
                                + payload.snapshotPositionMillis / 1000L);
            }
            return pinned;
        }
        if (!isModuleLyricClockUsable()) {
            return vendorIndex;
        }
        long position = estimatePlaybackPositionMillis();
        int moduleIndex = OfficialImmersiveLyricEntryPolicy.indexAt(startTimes, position);
        long intoRowMillis = moduleIndex >= 0 ? position - startTimes[moduleIndex] : -1L;
        int previousIndex;
        synchronized (IMMERSIVE_ENTRY_INDEXES) {
            ImmersiveEntryIndex previous = IMMERSIVE_ENTRY_INDEXES.get(recycler);
            previousIndex = previous != null && previous.sameLines(startTimes)
                    ? previous.index
                    : -1;
        }
        int aligned = OfficialImmersiveLyricEntryPolicy.alignedIndex(
                vendorIndex,
                previousIndex,
                moduleIndex,
                intoRowMillis,
                LyricTimingTuningConstants.LyricGeneral.PLAYBACK_SMALL_CORRECTION_MS);
        if (aligned != vendorIndex) {
            int loggedPrevious = previousIndex;
            StructuredBridgeLog.debug(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.NATIVE_CLOCK_ALIGNED,
                    () -> "Aligned vendor lyric row, source=immersive"
                            + ", vendorIndex=" + vendorIndex
                            + ", alignedIndex=" + aligned
                            + ", previousIndex=" + loggedPrevious
                            + ", moduleIntoRowMs=" + intoRowMillis);
        }
        return aligned;
    }

    /**
     * ColorOS 17 swaps the lyric row typeface with its active state (see
     * {@link OfficialLyricRowStyleMethodPolicy}). A row measured with the inactive typeface and
     * drawn with the active one wraps differently, which pushed its translation out of the slot.
     * The renderer therefore measures and draws every row with the active-row typeface; rows
     * measured before it was known are re-measured once.
     */
    private void rememberOfficialActiveLyricTypeface(Typeface typeface) {
        if (!officialLyricTextRenderer.setOfficialBaseTypeface(typeface)) {
            return;
        }
        infoAlways(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED,
                "Learned official active lyric typeface; re-measuring visible lyric rows once"
                        + " | weight="
                        + (Build.VERSION.SDK_INT >= 28 ? typeface.getWeight() : -1));
        mainHandler.post(() -> redrawLyricRenderTargets(false, true));
    }

    private static boolean isLyricsRecyclerCurrentLyricMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if ("setCurrentLyric".equals(method.getName())
                && parameterTypes.length > 0
                && parameterTypes[0] == int.class) {
            return true;
        }
        return isOfficialTimedCurrentLyricMethod(method);
    }

    private static boolean isLyricsRecyclerNotifyCrashGuardMethod(Method method) {
        return method != null
                && "e".equals(method.getName())
                && method.getReturnType() == void.class;
    }

    private static boolean isLyricsRecyclerScalePivotMethod(Method method) {
        // Method names drift across same-version SystemUIPlugin binaries (verified m/k plus the
        // current u/v/C device variant, and ColorOS 17's static d). The shapes are narrow and
        // safe: they can only operate on the lyric row, and restoring the configured pivot after
        // they return is idempotent even for style/binding helpers.
        return OfficialLyricScalePivotMethodPolicy.textViewArgumentIndex(method)
                != OfficialLyricScalePivotMethodPolicy.NOT_A_PIVOT_METHOD;
    }

    private static boolean isOfficialTimedCurrentLyricMethod(Method method) {
        return method != null
                && method.getReturnType() == void.class
                && isOfficialTimedCurrentLyricExecutable(method);
    }

    private static boolean isOfficialTimedCurrentLyricExecutable(
            java.lang.reflect.Executable executable) {
        return TimedLyricMethodPolicy.matches(executable);
    }

    private void tryInstallRecyclerAdapterNotifyHook(ClassLoader classLoader) {
        if (recyclerAdapterNotifyHookInstalled || classLoader == null) {
            return;
        }
        try {
            Class<?> adapterClass =
                    classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$Adapter");
            tryInstallRecyclerAdapterNotifyHook(adapterClass, "class-loader");
        } catch (Throwable t) {
            error("Failed to hook RecyclerView.Adapter notify guards", t);
        }
    }

    private void tryInstallRecyclerAdapterNotifyHookFromRecycler(View recycler) {
        if (recyclerAdapterNotifyHookInstalled || recycler == null) {
            return;
        }
        Object adapter = invokeNoArgByName(recycler, "getAdapter");
        Class<?> adapterClass = findRecyclerAdapterBaseClass(adapter);
        if (adapterClass != null) {
            tryInstallRecyclerAdapterNotifyHook(adapterClass, "attached-adapter");
        }
    }

    private static Class<?> findRecyclerAdapterBaseClass(Object adapter) {
        return LyricRecyclerAdapterContract.findBaseClass(adapter);
    }

    private synchronized void tryInstallRecyclerAdapterNotifyHook(
            Class<?> adapterClass,
            String source) {
        if (recyclerAdapterNotifyHookInstalled || adapterClass == null) {
            return;
        }
        int hooked = 0;
        hooked += hookRecyclerAdapterNotifyMethod(adapterClass, "notifyItemChanged", int.class);
        hooked += hookRecyclerAdapterNotifyMethod(
                adapterClass,
                "notifyItemChanged",
                int.class,
                Object.class);
        hooked += hookRecyclerAdapterNotifyMethod(
                adapterClass,
                "notifyItemRangeChanged",
                int.class,
                int.class);
        hooked += hookRecyclerAdapterNotifyMethod(
                adapterClass,
                "notifyItemRangeChanged",
                int.class,
                int.class,
                Object.class);
        recyclerAdapterNotifyHookInstalled = hooked > 0;
        if (hooked > 0) {
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked RecyclerView.Adapter notify guards, methods=" + hooked
                    + ", source=" + source);
        }
    }

    private void scheduleRecyclerAdapterNotifyHook(View recycler, ClassLoader classLoader) {
        if (recyclerAdapterNotifyHookInstalled || (recycler == null && classLoader == null)) {
            return;
        }
        mainHandler.post(() -> {
            tryInstallRecyclerAdapterNotifyHookFromRecycler(recycler);
            if (!recyclerAdapterNotifyHookInstalled) {
                tryInstallRecyclerAdapterNotifyHook(classLoader);
            }
            if (!recyclerAdapterNotifyHookInstalled) {
                mainHandler.postDelayed(
                        () -> {
                            tryInstallRecyclerAdapterNotifyHookFromRecycler(recycler);
                            if (!recyclerAdapterNotifyHookInstalled) {
                                tryInstallRecyclerAdapterNotifyHook(classLoader);
                            }
                        },
                        100L);
            }
        });
    }

    private int hookRecyclerAdapterNotifyMethod(
            Class<?> adapterClass,
            String methodName,
            Class<?>... parameterTypes) {
        try {
            Method method = adapterClass.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            hook(method)
                    .setId(HOOK_ID_RECYCLER_ADAPTER_NOTIFY_CHANGED
                            + "-"
                            + methodName
                            + "-"
                            + parameterTypes.length)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onRecyclerAdapterNotifyChanged);
            return 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    private Object onRecyclerAdapterNotifyChanged(XposedInterface.Chain chain) throws Throwable {
        if (!isRememberedLyricsRecyclerAdapter(chain.getThisObject())) {
            return chain.proceed();
        }
        try {
            return chain.proceed();
        } catch (IllegalStateException e) {
            if (!LockscreenIntegrationPolicy.isLyricsRecyclerComputingLayoutException(e)) {
                throw e;
            }
            maybeLogRecyclerAdapterNotifyGuard(e);
            return null;
        }
    }

    private Object onLyricsRecyclerNotifyCrashGuard(XposedInterface.Chain chain) throws Throwable {
        try {
            return chain.proceed();
        } catch (IllegalStateException e) {
            Object recycler = chain.getThisObject();
            if (!(recycler instanceof View)
                    || !isLyricsRecyclerView((View) recycler)
                    || !LockscreenIntegrationPolicy.isLyricsRecyclerComputingLayoutException(e)) {
                throw e;
            }
            maybeLogRecyclerAdapterNotifyGuard(e);
            return null;
        }
    }

    private void maybeLogRecyclerAdapterNotifyGuard(Throwable throwable) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRecyclerAdapterNotifyGuardLogAt < 1_500L) {
            return;
        }
        lastRecyclerAdapterNotifyGuardLogAt = now;
        info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "Suppressed RecyclerView.Adapter notify during layout, message="
                + (throwable == null ? "" : throwable.getMessage()));
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
        if (lyricRenderTransactions.rowStatus(
                findContainingLyricsRecyclerView(textView), textView, model)
                != LyricRenderTransactions.Status.READY) return LyricTextMatch.EMPTY;

        int adapterPosition = findValidLyricsRecyclerAdapterPosition(textView, model);
        WordLine indexedLine = indexedLineForBoundLyricRow(
                textView, model, adapterPosition, normalizedText);
        boolean duplicateText = model.hasDuplicateRenderableText(normalizedText);
        int officialIndex = indexedLine == null
                ? currentOfficialIndexForTextView(textView)
                : -1;
        WordLine activeLine = duplicateText
                ? resolveRecyclerVisualActiveLyricLine(model, position, textView)
                : null;
        officialLyricFrameResolver.resolveInto(
                model,
                normalizedText,
                adapterPosition,
                officialIndex,
                indexedLine,
                activeLine,
                position,
                duplicateText,
                "",
                -1L,
                lyricTextMatchSelection);
        return lyricTextMatchSelection.line == null
                && lyricTextMatchSelection.translationLine == null
                ? LyricTextMatch.EMPTY
                : new LyricTextMatch(
                        lyricTextMatchSelection.line,
                        lyricTextMatchSelection.translationLine);
    }

    private DrawFrame findOfficialLyricDrawFrame(TextView textView, WordLyricModel model) {
        long performanceStartedAt = performanceSampler.begin();
        try {
            return resolveOfficialLyricDrawFrame(textView, model);
        } finally {
            performanceSampler.end(
                    BridgePerformanceSampler.Metric.FRAME_RESOLVE,
                    performanceStartedAt);
        }
    }

    private DrawFrame resolveOfficialLyricDrawFrame(TextView textView, WordLyricModel model) {
        if (model == null || !isReadyForLyricDraw(textView)) {
            return null;
        }
        LyricsRecyclerMatch rowMatch = findLyricsRecyclerMatch(textView);
        if (rowMatch == null) {
            return null;
        }
        View recycler = rowMatch.recycler();
        View itemView = rowMatch.itemView();
        if (recycler == null || itemView == null) {
            return null;
        }
        ensureScreenTimeoutReceiver(textView.getContext());

        CharSequence currentText = textView.getText();
        if (currentText == null || currentText.length() == 0 || currentText.length() > lyricTextLengthLimit()) {
            return null;
        }

        String normalizedText = normalizedTextOf(textView);
        if (TextUtils.isEmpty(normalizedText)) {
            return null;
        }

        long position = estimatePlaybackPositionMillis();
        int officialIndex = readLyricsRecyclerCurrentIndex(recycler);
        WordLine activeLine = resolveRecyclerVisualActiveLyricLine(
                model,
                position,
                officialIndex);
        int adapterPosition = readRecyclerChildPosition(recycler, itemView);
        if (adapterPosition < 0
                || (adapterPosition >= model.lines.size()
                && adapterPosition >= model.officialLines.size())) {
            adapterPosition = -1;
        }
        WordLine indexedLine = indexedLineForBoundLyricRow(
                textView, model, adapterPosition, normalizedText);
        if (shouldSkipStrictOfficialAdapterSlot(model, adapterPosition, indexedLine, normalizedText)) {
            return null;
        }
        boolean duplicateText = model.hasDuplicateRenderableText(normalizedText);
        boolean rememberedLyricTextView = isRememberedActiveTextView(textView);
        officialLyricFrameResolver.resolveInto(
                model,
                normalizedText,
                adapterPosition,
                officialIndex,
                indexedLine,
                activeLine,
                position,
                duplicateText,
                rememberedLyricTextView ? activeLyricLine : "",
                rememberedLyricTextView ? activeLyricLineTimeMs : -1L,
                officialFrameSelection);
        WordLine line = officialFrameSelection.line;
        WordLine translationLine = officialFrameSelection.translationLine;
        String matchReason = officialFrameSelection.matchReason;
        boolean knownLyricTextView = rememberedLyricTextView
                || line != null
                || translationLine != null;
        if (!knownLyricTextView) {
            return null;
        }
        if (line == null && translationLine != null) {
            return null;
        }
        if (line == null) {
            return null;
        }
        if (shouldSuppressDuplicateOfficialAdapterDraw(
                model,
                adapterPosition,
                line,
                officialIndex)) {
            return null;
        }
        if (position < 0) {
            position = line.timeMillis;
        }
        maybeLogOfficialFrameDecision(
                "draw",
                model,
                textView,
                normalizedText,
                adapterPosition,
                indexedLine,
                model.rawOfficialLineAt(adapterPosition),
                activeLine,
                line,
                null,
                position,
                matchReason,
                duplicateText);

        boolean active = isOfficialWordProgressActive(model, activeLine, line, position);
        rememberActiveTextViewReference(textView);
        if (active) {
            activeLyricLine = line.normalizedText;
            activeLyricLineTimeMs = line.timeMillis;
            rememberActiveRendererTextView(textView);
            activeRendererWordLine = line;
            scheduleActiveLyricRefresh(
                    textView,
                    lastPlaybackIsPlaying ? ACTIVE_LYRIC_FRAME_DELAY_MS : 500L);
        }
        int lineIndex = model.indexOfLine(line);
        WordLine focusAnchor = resolveFocusAnchorLine(model, activeLine, position);
        int focusAnchorIndex = model.indexOfLine(focusAnchor);
        int scaleActiveIndex = resolveOfficialScaleActiveIndex(
                model,
                activeLine,
                position,
                officialIndex);
        boolean focused = isOfficialVisualFocusedLine(lineIndex, focusAnchorIndex, scaleActiveIndex);
        return new DrawFrame(
                model,
                line,
                lineIndex,
                focusAnchorIndex,
                scaleActiveIndex,
                position,
                resolveLyricGlowPosition(position),
                active,
                focused,
                shouldAnimateOfficialRowScale(textView));
    }

    private void rememberRecentOfficialDrawFrame(TextView textView, DrawFrame frame) {
        if (textView == null
                || frame == null
                || frame.model == null
                || frame.line == null
                || TextUtils.isEmpty(frame.line.normalizedText)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        CachedDrawFrame cached = recentOfficialDrawFrames.get(textView);
        if (cached != null
                && cached.model == frame.model
                && cached.line == frame.line
                && cached.lineTimeMillis == frame.line.timeMillis
                && cached.normalizedText.equals(frame.line.normalizedText)) {
            cached.capturedAtElapsedMs = now;
            return;
        }
        recentOfficialDrawFrames.put(
                textView,
                new CachedDrawFrame(
                        frame.model,
                        frame.line,
                        frame.line.timeMillis,
                        frame.line.normalizedText,
                        now));
    }

    private DrawFrame findRecentOfficialDrawFrame(TextView textView, WordLyricModel model) {
        if (textView == null || !isReadyForLyricDraw(textView) || !isInLyricsRecyclerView(textView)) {
            return null;
        }
        CachedDrawFrame cached = recentOfficialDrawFrames.get(textView);
        long now = SystemClock.elapsedRealtime();
        if (cached == null
                || model == null
                || cached.model != model
                || cached.line == null
                || now - cached.capturedAtElapsedMs > OFFICIAL_DRAW_FRAME_TRANSIENT_MISS_GRACE_MS) {
            return null;
        }

        String normalizedText = normalizedTextOf(textView);
        if (!isCachedDrawFrameStillCompatible(cached, normalizedText)) {
            recentOfficialDrawFrames.remove(textView);
            return null;
        }
        int adapterPosition = findValidLyricsRecyclerAdapterPosition(textView, model);
        WordLine indexedLine = indexedLineForBoundLyricRow(
                textView, model, adapterPosition, normalizedText);
        if (shouldSkipStrictOfficialAdapterSlot(model, adapterPosition, indexedLine, normalizedText)
                || shouldSuppressDuplicateOfficialAdapterDraw(
                model,
                adapterPosition,
                cached.line,
                textView)) {
            recentOfficialDrawFrames.remove(textView);
            return null;
        }

        long position = estimatePlaybackPositionMillis();
        if (position < 0L) {
            position = cached.line.timeMillis;
        }
        WordLine activeLine = resolveRecyclerVisualActiveLyricLine(model, position, textView);
        if (indexedLine != null && indexedLine != cached.line) {
            recentOfficialDrawFrames.remove(textView);
            maybeLogOfficialFrameDecision(
                    "recent-cache-indexed",
                    model,
                    textView,
                    normalizedText,
                    adapterPosition,
                    indexedLine,
                    model.rawOfficialLineAt(adapterPosition),
                    activeLine,
                    indexedLine,
                    cached.line,
                    position,
                    "cached-mismatch-indexed",
                    model.hasDuplicateRenderableText(normalizedText));
            return buildOfficialDrawFrame(model, indexedLine, activeLine, position, textView);
        }
        maybeLogOfficialFrameDecision(
                "recent-cache",
                model,
                textView,
                normalizedText,
                adapterPosition,
                indexedLine,
                model.rawOfficialLineAt(adapterPosition),
                activeLine,
                cached.line,
                cached.line,
                position,
                "cached-compatible",
                model.hasDuplicateRenderableText(normalizedText));
        return buildOfficialDrawFrame(model, cached.line, activeLine, position, textView);
    }

    private boolean isCachedDrawFrameStillCompatible(
            CachedDrawFrame cached,
            String normalizedText) {
        if (cached == null || cached.line == null || TextUtils.isEmpty(cached.normalizedText)) {
            return false;
        }
        if (TextUtils.isEmpty(normalizedText)) {
            return false;
        }
        if (normalizedText.equals(cached.normalizedText)
                || WordLyricRenderSupport.matchesWordLineText(cached.line, normalizedText)) {
            return true;
        }
        return !TextUtils.isEmpty(cached.line.translation)
                && cached.line.normalizedTranslation().equals(normalizedText);
    }

    private DrawFrame buildOfficialDrawFrame(
            WordLyricModel model,
            WordLine line,
            WordLine activeLine,
            long position,
            TextView textView) {
        if (model == null || line == null) {
            return null;
        }
        int lineIndex = model.indexOfLine(line);
        if (lineIndex < 0) {
            return null;
        }
        WordLine focusAnchor = resolveFocusAnchorLine(model, activeLine, position);
        int focusAnchorIndex = model.indexOfLine(focusAnchor);
        int scaleActiveIndex = resolveOfficialScaleActiveIndex(
                model,
                activeLine,
                position,
                textView);
        boolean active = isOfficialWordProgressActive(model, activeLine, line, position);
        boolean focused = isOfficialVisualFocusedLine(lineIndex, focusAnchorIndex, scaleActiveIndex);
        return new DrawFrame(
                model,
                line,
                lineIndex,
                focusAnchorIndex,
                scaleActiveIndex,
                position,
                resolveLyricGlowPosition(position),
                active,
                focused,
                shouldAnimateOfficialRowScale(textView));
    }

    private int resolveOfficialScaleActiveIndex(
            WordLyricModel model,
            WordLine activeLine,
            long position,
            TextView textView) {
        return resolveOfficialScaleActiveIndex(
                model,
                activeLine,
                position,
                readLyricsRecyclerCurrentIndex(
                        findContainingLyricsRecyclerView(textView)));
    }

    private int resolveOfficialScaleActiveIndex(
            WordLyricModel model,
            WordLine activeLine,
            long position,
            int officialIndex) {
        if (model == null) {
            return -1;
        }
        if (isBeforeFirstLyricScaleStart(model, position)) {
            return -1;
        }
        WordLine officialLine = model.lineAtOfficialDisplayIndex(officialIndex);
        int officialLineIndex = model.indexOfLine(officialLine);
        int activeLineIndex = model.indexOfLine(activeLine);
        officialLineIndex = SentenceWindowContract.visualIndex(
                hasSentenceWindow() ? position : -1L, activeLineIndex, officialLineIndex);
        return LockscreenIntegrationPolicy.chooseOfficialLyricVisualIndex(
                officialLineIndex,
                activeLineIndex,
                model.displayIndexAt(position));
    }

    private static boolean isOfficialVisualFocusedLine(
            int lineIndex,
            int focusAnchorIndex,
            int scaleActiveIndex) {
        if (lineIndex < 0) {
            return false;
        }
        if (lineIndex == scaleActiveIndex) {
            return true;
        }
        return scaleActiveIndex < 0 && lineIndex == focusAnchorIndex;
    }

    private static boolean isBeforeFirstLyricScaleStart(WordLyricModel model, long position) {
        return model != null && model.isBeforeFirstProgressStart(position);
    }

    private boolean isOfficialWordProgressActive(
            WordLyricModel model,
            WordLine activeLine,
            WordLine line,
            long position) {
        if (lyricUiLineTimedProgressEnabled && !isAodLowFrameRateLyricMode()
                && line != null && line.timingMode == LyricTimingMode.LINE_TIMED) {
            return LineTimedRevealPolicy.active(model, line, position);
        }
        boolean lineMatchesPlaybackActive = activeLine != null
                && line != null
                && activeLine.timeMillis == line.timeMillis
                && activeLine.normalizedText.equals(line.normalizedText);
        return LockscreenIntegrationPolicy.shouldTreatLineAsWordProgressActive(
                lineMatchesPlaybackActive,
                model != null && model.isBeforeFirstProgressStart(position));
    }

    private boolean shouldSkipStrictOfficialAdapterSlot(
            WordLyricModel model,
            int adapterPosition,
            WordLine indexedLine,
            String normalizedText) {
        // RecyclerView pre-layout can expose a new bound string with the previous adapter
        // position for one frame. A unique model string is authoritative in that window;
        // adapter position is only needed to disambiguate repeated lyric text.
        if (model != null
                && model.hasRenderableText(normalizedText)
                && !model.hasDuplicateRenderableText(normalizedText)) {
            return false;
        }
        if (!hasStrictOfficialAdapterSlot(model, adapterPosition)) {
            return false;
        }
        if (indexedLine == null) {
            maybeLogOfficialAdapterSuppression(
                    "slot-empty",
                    adapterPosition,
                    -1,
                    normalizedText,
                    null);
            return true;
        }
        boolean matchesMain = WordLyricRenderSupport.matchesWordLineText(indexedLine, normalizedText);
        boolean matchesTranslation = !TextUtils.isEmpty(indexedLine.translation)
                && indexedLine.normalizedTranslation().equals(normalizedText);
        if (matchesMain || matchesTranslation
                || model.matchesOfficialDisplayAlias(adapterPosition, normalizedText)) {
            return false;
        }
        maybeLogOfficialAdapterSuppression(
                "slot-mismatch",
                adapterPosition,
                model.indexOfLine(indexedLine),
                normalizedText,
                indexedLine);
        return true;
    }

    private static boolean hasStrictOfficialAdapterSlot(
            WordLyricModel model,
            int adapterPosition) {
        return model != null
                && adapterPosition >= 0
                && !model.officialLines.isEmpty()
                && adapterPosition < model.officialLines.size();
    }

    private boolean shouldSuppressDuplicateOfficialAdapterDraw(
            WordLyricModel model,
            int adapterPosition,
            WordLine line,
            TextView textView) {
        return shouldSuppressDuplicateOfficialAdapterDraw(
                model,
                adapterPosition,
                line,
                currentOfficialLyricsRecyclerAdapterIndex(model, textView));
    }

    private boolean shouldSuppressDuplicateOfficialAdapterDraw(
            WordLyricModel model,
            int adapterPosition,
            WordLine line,
            int currentIndex) {
        if (!WordLyricRenderConstants.OFFICIAL_SLOT_ALIAS_REUSE_ENABLED) {
            return false;
        }
        if (!hasStrictOfficialAdapterSlot(model, adapterPosition) || line == null) {
            return false;
        }
        WordLine indexedLine = model.lineAtOfficialIndex(adapterPosition);
        if (indexedLine != line) {
            return false;
        }
        int firstIndex = -1;
        int duplicateCount = 0;
        for (int i = 0; i < model.officialLines.size(); i++) {
            if (model.officialLines.get(i) != line) {
                continue;
            }
            if (firstIndex < 0) {
                firstIndex = i;
            }
            duplicateCount++;
        }
        if (duplicateCount <= 1) {
            return false;
        }
        int keepIndex = firstIndex;
        if (currentIndex >= 0
                && currentIndex < model.officialLines.size()
                && model.officialLines.get(currentIndex) == line) {
            keepIndex = currentIndex;
        }
        boolean suppress = adapterPosition != keepIndex;
        if (suppress) {
            maybeLogOfficialAdapterSuppression(
                    "duplicate-slot",
                    adapterPosition,
                    model.indexOfLine(line),
                    line.normalizedText,
                    line);
        }
        return suppress;
    }

    private int currentOfficialLyricsRecyclerAdapterIndex(
            WordLyricModel model,
            TextView textView) {
        if (model == null) {
            return -1;
        }
        int officialIndex = readLyricsRecyclerCurrentIndex(
                findContainingLyricsRecyclerView(textView));
        if (officialIndex >= 0) {
            return officialIndex;
        }
        return model.adapterIndexAt(estimatePlaybackPositionMillis());
    }

    private void maybeLogOfficialAdapterSuppression(
            String reason,
            int adapterPosition,
            int drawIndex,
            String normalizedText,
            WordLine line) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        String key = nullToEmpty(reason)
                + "|" + adapterPosition
                + "|" + drawIndex
                + "|" + shortenForLog(normalizedText)
                + "|" + (line == null ? -1L : line.timeMillis)
                + "|" + shortenForLog(line == null ? "" : line.normalizedText);
        long now = SystemClock.elapsedRealtime();
        Long previous = officialAdapterSuppressionLogTimes.get(key);
        if (previous != null && now - previous < OFFICIAL_ADAPTER_SUPPRESSION_LOG_INTERVAL_MS) {
            return;
        }
        if (officialAdapterSuppressionLogTimes.size() >= 256) {
            officialAdapterSuppressionLogTimes.clear();
        }
        officialAdapterSuppressionLogTimes.put(key, now);
        info(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED, "Suppress official lyric custom draw"
                        + ", reason=" + reason
                        + ", adapterPosition=" + adapterPosition
                        + ", drawIndex=" + drawIndex
                        + ", text=" + shortenForLog(normalizedText)
                        + ", line=" + LyricModelTraceSupport.describeWordLine(line, false));
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

    private WordLine resolvePlaybackActiveLyricLine(WordLyricModel model, long position) {
        if (model == null) {
            return null;
        }
        WordLine activeLine = position >= 0L ? model.findActiveLine(position) : null;
        if (activeLine == null) {
            activeLine = firstLineBeforeDisplayStart(model, position);
        } else {
            WordLine firstDisplayLine = firstLineBeforeDisplayStart(model, position);
            if (firstDisplayLine != null) {
                int activeLineIndex = model.indexOfLine(activeLine);
                int firstDisplayLineIndex = model.indexOfLine(firstDisplayLine);
                if (activeLineIndex < 0
                        || (firstDisplayLineIndex >= 0
                        && activeLineIndex < firstDisplayLineIndex)) {
                    activeLine = firstDisplayLine;
                }
            }
        }
        return activeLine;
    }

    private WordLine resolveRecyclerVisualActiveLyricLine(
            WordLyricModel model,
            long position,
            TextView textView) {
        return resolveRecyclerVisualActiveLyricLine(
                model,
                position,
                readLyricsRecyclerCurrentIndex(
                        findContainingLyricsRecyclerView(textView)));
    }

    private WordLine resolveRecyclerVisualActiveLyricLine(
            WordLyricModel model,
            long position,
            int officialIndex) {
        if (model == null) {
            return null;
        }
        WordLine officialLine = model.lineAtOfficialDisplayIndex(officialIndex);
        WordLine playbackLine = resolvePlaybackActiveLyricLine(model, position);
        if (officialLine != null
                && LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                systemUiOwnsNativeLyricsRecycler(),
                model.indexOfLine(officialLine),
                model.indexOfLine(playbackLine),
                model.isBeforeFirstProgressStart(position),
                SystemClock.elapsedRealtime() <= lyricTrackPositionResetGuardUntilElapsedMs)) {
            return officialLine;
        }
        return playbackLine;
    }

    private static WordLine firstLineBeforeDisplayStart(WordLyricModel model, long position) {
        if (model == null || position < 0L || model.lines.isEmpty()) {
            return null;
        }
        WordLine firstLine = model.firstDisplayLine();
        if (firstLine == null || firstLine.timeMillis <= position) {
            return null;
        }
        return firstLine;
    }

    private static WordLine resolveFocusAnchorLine(
            WordLyricModel model, WordLine activeLine, long position) {
        WordLine firstDisplayLine = firstLineBeforeDisplayStart(model, position);
        if (firstDisplayLine != null) {
            return firstDisplayLine;
        }
        if (activeLine != null || model == null) {
            return activeLine;
        }
        return model.lineAt(model.displayIndexAt(position));
    }

    private static boolean isReadyForLyricDraw(TextView textView) {
        return textView != null
                && textView.isAttachedToWindow()
                && textView.getVisibility() == View.VISIBLE
                && textView.getWidth() > 0
                && textView.getHeight() > 0;
    }

    private void noteVisibleLockscreenLyricTextView(
            TextView textView,
            View recycler,
            long now) {
        if (textView == null
                || recycler == null
                || recycler.getVisibility() != View.VISIBLE) {
            return;
        }
        if (systemUiLyricModeKeepAwakeActive
                && !screenTimeout.screenTimeoutPausedByUserPresent
                && now - screenTimeout.lastVisibleOfficialLyricTextViewAt
                < SCREEN_TIMEOUT_VISIBLE_LYRIC_NOTE_THROTTLE_MS) {
            return;
        }
        if (!isEffectivelyVisible(textView)) {
            return;
        }
        if (!hasUsableLyricText(textView)) {
            return;
        }
        rememberLyricsRecyclerView(recycler);
        screenTimeout.lastVisibleOfficialLyricTextViewAt = now;
        if (screenTimeout.screenTimeoutPausedByUserPresent
                && screenTimeout.isKeyguardShowingForScreenTimeout(textView.getContext())) {
            screenTimeout.screenTimeoutPausedByUserPresent = false;
            screenTimeout.maybeLogScreenTimeout(
                    "Resumed screen timeout keep-awake after visible lyric confirmed keyguard",
                    true);
        }
        if (screenTimeout.screenTimeoutPausedByScreenOff
                || screenTimeout.screenTimeoutPausedByUserPresent
                || !hasSupportedSystemUiPlayer()
                || !lastPlaybackIsPlaying) {
            return;
        }
        if (!systemUiLyricModeKeepAwakeActive) {
            prepareLyricSurfaceFromVisibleSession(recycler, "visible-session-draw");
            activateSystemUiLyricModeFromVisibleTextView(
                    textView,
                    recycler,
                    "visible lyric TextView");
            screenTimeout.maybeLogScreenTimeout(
                    "Inferred lockscreen lyric UI keep-awake from visible official lyric view",
                    false);
        }
        screenTimeout.updateScreenTimeoutWakeLock(textView.getContext());
    }

    private boolean hasUsableLyricText(TextView textView) {
        CharSequence text = textView == null ? null : textView.getText();
        if (text == null || text.length() == 0 || text.length() > lyricTextLengthLimit()) {
            return false;
        }
        return !TextUtils.isEmpty(normalizedTextOf(textView));
    }

    private static int findLyricsRecyclerAdapterPosition(TextView textView) {
        LyricsRecyclerMatch match = findLyricsRecyclerMatch(textView);
        return match == null
                ? -1
                : readRecyclerChildPosition(match.recycler(), match.itemView());
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
            WeakReference<Method> reference = RECYCLER_POSITION_METHOD_CACHE.get(recyclerClass);
            Method cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            if (RECYCLER_POSITION_METHOD_MISSING.containsKey(recyclerClass)) {
                return null;
            }
        }
        Method resolved = findRecyclerChildPositionMethod(recyclerClass);
        synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
            if (resolved == null) {
                RECYCLER_POSITION_METHOD_MISSING.put(recyclerClass, Boolean.TRUE);
            } else {
                RECYCLER_POSITION_METHOD_CACHE.put(
                        recyclerClass,
                        new WeakReference<>(resolved));
            }
        }
        return resolved;
    }

    private static Method findRecyclerChildPositionMethod(Class<?> recyclerClass) {
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

    private static int currentOfficialIndexForTextView(TextView textView) {
        return readLyricsRecyclerCurrentIndex(findContainingLyricsRecyclerView(textView));
    }

    static View findContainingLyricsRecyclerView(TextView textView) {
        LyricsRecyclerMatch match = findLyricsRecyclerMatch(textView);
        return match == null ? null : match.recycler();
    }

    static View findLyricsRecyclerItemView(TextView textView) {
        LyricsRecyclerMatch match = findLyricsRecyclerMatch(textView);
        if (match != null) {
            return match.itemView();
        }
        if (textView == null) {
            return null;
        }
        return textView;
    }

    private static LyricsRecyclerMatch findLyricsRecyclerMatch(TextView textView) {
        if (textView == null) {
            return null;
        }
        LyricsRecyclerMatch cached = LYRICS_RECYCLER_MATCH_CACHE.get(textView);
        if (cached == NO_LYRICS_RECYCLER_MATCH) {
            return null;
        }
        if (cached != null && cached.isUsable(textView)) {
            return cached;
        }
        if (cached != null) {
            LYRICS_RECYCLER_MATCH_CACHE.remove(textView);
        }
        View child = textView;
        Object parent = child.getParent();
        for (int depth = 0; depth < 12 && parent instanceof View; depth++) {
            View parentView = (View) parent;
            if (isLyricsRecyclerView(parentView)) {
                LyricsRecyclerMatch match = new LyricsRecyclerMatch(parentView, child);
                LYRICS_RECYCLER_MATCH_CACHE.put(textView, match);
                return match;
            }
            child = parentView;
            parent = child.getParent();
        }
        if (textView.isAttachedToWindow()) {
            LYRICS_RECYCLER_MATCH_CACHE.put(textView, NO_LYRICS_RECYCLER_MATCH);
        }
        return null;
    }

    private void configureOfficialLyricLineSpacing(View recyclerView) {
        if (recyclerView == null) {
            return;
        }
        LyricUiConfig config = runtimeLyricUiConfig == null
                ? LyricUiConfig.defaults()
                : runtimeLyricUiConfig;
        // Keep one RecyclerView-owned edge mask; disabling it preserves the configured length.
        recyclerView.setVerticalFadingEdgeEnabled(config.verticalFadeEnabled);
        recyclerView.setFadingEdgeLength(dp(
                recyclerView.getContext(),
                config.verticalFadeLengthTenthsDp / 10f));
        recyclerView.invalidate();
        OfficialLyricsRecyclerCompatibility.Binding binding =
                resolveOfficialLyricsRecyclerBinding(recyclerView);
        if (binding == null) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Official lyric line spacing layout unavailable; keeping plugin default");
            return;
        }
        float requestedSpacingDp = LyricUiLayoutPolicy.lineSpacingTenthsDp(config) / 10f;
        int requestedSpacing = dp(recyclerView.getContext(), requestedSpacingDp);
        Integer previousSpacingValue = binding.readLineSpacing(recyclerView);
        if (previousSpacingValue == null) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Official lyric line spacing field unavailable; keeping plugin default");
            return;
        }
        int previousSpacing = previousSpacingValue;
        if (previousSpacing != requestedSpacing) {
            if (!binding.writeLineSpacing(recyclerView, requestedSpacing)) {
                info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Could not configure official lyric line spacing; keeping plugin default");
                return;
            }
        }
        Integer appliedSpacing = binding.readLineSpacing(recyclerView);
        if (appliedSpacing == null || appliedSpacing != requestedSpacing) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Could not configure official lyric line spacing; keeping plugin default");
            return;
        }
        int synchronizedChildren = synchronizeBoundLyricItemSpacing(
                recyclerView,
                previousSpacing,
                requestedSpacing);
        if (previousSpacing != requestedSpacing || synchronizedChildren > 0) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Configured official lyric line spacing, from=" + previousSpacing
                    + ", to=" + requestedSpacing
                    + ", layout=" + binding.layoutName
                    + ", synchronizedChildren=" + synchronizedChildren);
        }
    }

    private void configureOfficialLyricScaleOwnership(View recyclerView) {
        if (recyclerView == null) {
            return;
        }
        OfficialLyricsRecyclerCompatibility.Binding binding =
                resolveOfficialLyricsRecyclerBinding(recyclerView);
        if (binding == null) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Official lyric inactive scale layout unavailable; keeping plugin scale");
            return;
        }
        float requestedScale = LockscreenIntegrationPolicy.officialInactiveRowScale(
                runtimeLyricUiConfig.scaleEnabled,
                runtimeLyricUiConfig.inactiveScalePercent);
        Float previousScaleValue = binding.readInactiveScale(recyclerView);
        if (previousScaleValue == null || Float.isNaN(previousScaleValue)) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Official lyric inactive scale field unavailable; keeping plugin scale");
            return;
        }
        float previousScale = previousScaleValue;
        // Keep the vendor RecyclerView as the only row-scale state machine. Its current-index
        // transaction atomically owns child attachment, animation cancellation, pivot preparation
        // and the active/inactive transform. The custom renderer only supplies pixels.
        boolean scaleChanged = Math.abs(previousScale - requestedScale) > 0.001f;
        if (scaleChanged) {
            if (!binding.writeInactiveScale(recyclerView, requestedScale)) {
                info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Could not configure official lyric inactive scale; keeping plugin default");
                return;
            }
            Float appliedScale = binding.readInactiveScale(recyclerView);
            if (appliedScale == null
                    || Float.isNaN(appliedScale)
                    || Math.abs(appliedScale - requestedScale) > 0.001f) {
                info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Could not configure official lyric inactive scale; keeping plugin default");
                return;
            }
        }
        int synchronizedChildren = scaleChanged
                ? synchronizeBoundLyricItemScales(recyclerView, requestedScale)
                : 0;
        synchronizeBoundLyricItemPivots(recyclerView);
        if (scaleChanged || synchronizedChildren > 0) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Configured official lyric scale ownership, from=" + previousScale
                    + ", to=" + requestedScale
                    + ", layout=" + binding.layoutName
                    + ", synchronizedChildren=" + synchronizedChildren);
        }
    }

    private OfficialLyricsRecyclerCompatibility.Binding resolveOfficialLyricsRecyclerBinding(
            View recyclerView) {
        if (recyclerView == null) {
            return null;
        }
        Class<?> recyclerClass = recyclerView.getClass();
        synchronized (officialLyricsRecyclerCompatibilityLock) {
            OfficialLyricsRecyclerCompatibility.Binding cached =
                    officialLyricsRecyclerBindings.get(recyclerClass);
            if (cached != null) {
                return cached;
            }
            if (unavailableOfficialLyricsRecyclerBindings.containsKey(recyclerClass)) {
                return null;
            }

            OfficialLyricsRecyclerCompatibility.Binding resolved =
                    OfficialLyricsRecyclerCompatibility.resolve(recyclerClass);
            String source = "verified fingerprint";
            if (resolved == null) {
                source = "DexKit semantics";
                try {
                    OfficialLyricsRecyclerDexKitResolver.Resolution resolution =
                            OfficialLyricsRecyclerDexKitResolver.resolve(recyclerView);
                    if (resolution != null) {
                        resolved = resolution.layout;
                        publishImmersiveCurrentIndexField(recyclerClass, resolution.currentIndex);
                    }
                } catch (Throwable t) {
                    error("Failed to resolve LyricsRecyclerView fields via DexKit", t);
                }
            }
            if (resolved == null) {
                unavailableOfficialLyricsRecyclerBindings.put(recyclerClass, Boolean.TRUE);
                info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "No unique safe LyricsRecyclerView field layout found; keeping plugin defaults");
                return null;
            }
            officialLyricsRecyclerBindings.put(recyclerClass, resolved);
            info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "Resolved LyricsRecyclerView fields via " + source
                    + ", layout=" + resolved.layoutName);
            return resolved;
        }
    }

    private void publishImmersiveCurrentIndexField(Class<?> recyclerClass, Field field) {
        if (recyclerClass == null
                || field == null
                || field.getType() != int.class
                || !field.getDeclaringClass().isAssignableFrom(recyclerClass)) {
            return;
        }
        try {
            field.setAccessible(true);
        } catch (Throwable t) {
            return;
        }
        immersiveCurrentIndexField = field;
        infoAlways(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED,
                "Resolved official lyric current row field via DexKit semantics, field="
                        + field.getName());
    }

    /** ColorOS 17 displayed official row, or null before it is resolved for this recycler. */
    private static Integer readImmersiveCurrentIndexField(Object recycler) {
        Field field = immersiveCurrentIndexField;
        if (field == null
                || recycler == null
                || !field.getDeclaringClass().isInstance(recycler)) {
            return null;
        }
        try {
            return field.getInt(recycler);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int synchronizeBoundLyricItemScales(
            View recyclerView,
            float inactiveScale) {
        if (!(recyclerView instanceof ViewGroup)) {
            return 0;
        }
        ViewGroup group = (ViewGroup) recyclerView;
        int officialIndex = readLyricsRecyclerCurrentIndex(recyclerView);
        int changed = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            TextView lyricTextView = findFirstTextView(child, new int[]{0});
            View scaleView = lyricTextView == null ? child : lyricTextView;
            int adapterPosition = readRecyclerChildPosition(recyclerView, child);
            float targetScale = adapterPosition >= 0 && adapterPosition == officialIndex
                    ? 1f
                    : inactiveScale;
            if (Math.abs(scaleView.getScaleX() - targetScale) > 0.001f) {
                scaleView.setScaleX(targetScale);
                changed++;
            }
            if (Math.abs(scaleView.getScaleY() - targetScale) > 0.001f) {
                scaleView.setScaleY(targetScale);
            }
        }
        return changed;
    }

    private static void synchronizeBoundLyricItemPivots(View recyclerView) {
        if (!(recyclerView instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) recyclerView;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView textView = findFirstTextView(group.getChildAt(i), new int[]{0});
            if (textView != null) {
                applyOfficialLyricScalePivot(textView);
            }
        }
    }

    private static TextView findFirstTextView(View view, int[] visited) {
        if (view == null || visited[0]++ > 24) {
            return null;
        }
        if (view instanceof TextView) {
            return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView match = findFirstTextView(group.getChildAt(i), visited);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static void applyOfficialLyricScalePivot(TextView textView) {
        if (textView == null) {
            return;
        }
        int resolvedWidth = LyricUiLayoutPolicy.resolvedViewDimension(
                textView.getWidth(),
                textView.getMeasuredWidth());
        int resolvedHeight = LyricUiLayoutPolicy.resolvedViewDimension(
                textView.getHeight(),
                textView.getMeasuredHeight());
        if (resolvedWidth <= 0) {
            // The vendor method already selected its safest available pivot. Do not replace it
            // with paddingLeft merely because this hook ran before the first layout.
            return;
        }
        float contentWidth = Math.max(
                0f,
                resolvedWidth - textView.getPaddingLeft() - textView.getPaddingRight());
        float pivotX = LyricUiLayoutPolicy.horizontalScalePivot(
                runtimeLyricUiConfig.alignment,
                textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL,
                textView.getPaddingLeft(),
                contentWidth);
        textView.setPivotX(pivotX);
        if (runtimeLyricUiConfig.alignment != LyricUiConfig.ALIGN_CENTER) {
            textView.setPivotY(0f);
            return;
        }
        if (resolvedHeight <= 0) {
            return;
        }
        float contentHeight = Math.max(
                0f,
                resolvedHeight - textView.getPaddingTop() - textView.getPaddingBottom());
        textView.setPivotY(textView.getPaddingTop() + contentHeight / 2f);
    }

    private static final View.OnLayoutChangeListener
            OFFICIAL_LYRIC_SCALE_PIVOT_LAYOUT_LISTENER =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (view instanceof TextView
                        && (right - left != oldRight - oldLeft
                        || bottom - top != oldBottom - oldTop)) {
                    applyOfficialLyricScalePivot((TextView) view);
                }
            };

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
        // This method is also reached from the per-frame custom draw path. TextView's
        // height setters request a new layout even when the requested value is unchanged,
        // so keep this preparation idempotent to avoid remeasuring the whole shade on
        // every lyric frame.
        if (textView.getIncludeFontPadding()) {
            textView.setIncludeFontPadding(false);
        }
        if (textView.getMinHeight() != 0) {
            textView.setMinHeight(0);
        }
        if (textView.getMinimumHeight() != 0) {
            textView.setMinimumHeight(0);
        }
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null
                && itemView != textView
                && itemView.getMinimumHeight() != 0) {
            itemView.setMinimumHeight(0);
        }
    }

    private static void clearFocusedOfficialLyricViewEffects(TextView textView) {
        applyFocusedOfficialLyricViewEffects(textView, false, false);
    }

    private static void applyFocusedOfficialLyricViewEffects(
            TextView textView,
            boolean inactiveBlur) {
        applyFocusedOfficialLyricViewEffects(textView, inactiveBlur, inactiveBlur);
    }

    private static void applyFocusedOfficialLyricViewEffects(
            TextView textView,
            boolean inactiveBlur,
            boolean keepZeroBlurEffect) {
        applyFocusedOfficialLyricViewEffects(
                textView,
                inactiveBlur ? runtimeLyricUiConfig.blurRadiusTenthsPx / 10f : 0f,
                keepZeroBlurEffect);
    }

    static void applyFocusedOfficialLyricViewEffects(
            TextView textView,
            float inactiveBlurRadius,
            boolean keepZeroBlurEffect) {
        if (textView == null) {
            return;
        }
        applyViewVisualEffects(textView, inactiveBlurRadius, keepZeroBlurEffect);
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null && itemView != textView) {
            applyViewVisualEffects(itemView, false, false);
        }
    }

    private static void clearViewVisualEffects(View view) {
        applyViewVisualEffects(view, false, false);
    }

    private static void applyViewVisualEffects(View view, boolean inactiveBlur) {
        applyViewVisualEffects(view, inactiveBlur, inactiveBlur);
    }

    private static void applyViewVisualEffects(
            View view,
            boolean inactiveBlur,
            boolean keepZeroBlurEffect) {
        applyViewVisualEffects(
                view,
                inactiveBlur ? runtimeLyricUiConfig.blurRadiusTenthsPx / 10f : 0f,
                keepZeroBlurEffect);
    }

    private static void applyViewVisualEffects(
            View view,
            float inactiveBlurRadius,
            boolean keepZeroBlurEffect) {
        if (view == null) {
            return;
        }
        float blurRadius = Math.max(0f, inactiveBlurRadius);
        if (blurRadius < OFFICIAL_LYRIC_BLUR_ZERO_THRESHOLD_PX) {
            blurRadius = 0f;
        }
        // Do not reset alpha/scale/layer type from onDraw. Those physical View properties belong
        // to SystemUI's RecyclerView/page transaction; overwriting them each frame caused the
        // official row animator and the custom renderer to pull the same item in opposite ways.
        boolean updateBlur;
        int effectMode = blurRadius > 0f
                ? 1000 + Math.max(1, Math.round(blurRadius * 10f))
                : keepZeroBlurEffect ? 1 : 0;
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            Integer previousEffectMode = VIEW_LYRIC_RENDER_EFFECT_MODE.get(view);
            updateBlur = previousEffectMode == null
                    || previousEffectMode != effectMode
                    || !VIEW_BLUR_DISABLED.containsKey(view);
            if (effectMode != 0) {
                VIEW_LYRIC_RENDER_EFFECT_MODE.put(view, effectMode);
            } else {
                VIEW_LYRIC_RENDER_EFFECT_MODE.remove(view);
            }
            if (updateBlur) {
                VIEW_BLUR_DISABLED.put(view, Boolean.TRUE);
            }
        }
        if (!updateBlur) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect effect = null;
            if (effectMode > 1) {
                float radius = (effectMode - 1000) / 10f;
                effect = RenderEffect.createBlurEffect(
                        radius,
                        radius,
                        Shader.TileMode.CLAMP);
            } else if (effectMode == 1) {
                effect = RenderEffect.createBlurEffect(0f, 0f, Shader.TileMode.CLAMP);
            }
            view.setRenderEffect(effect);
        }
        if (effectMode == 0) {
            for (Method method : resolveViewBlurMethods(view.getClass())) {
                try {
                    method.invoke(view, false);
                } catch (Throwable ignored) {
                    // The standard visual properties above are sufficient when a vendor
                    // implementation rejects its optional blur setter.
                }
            }
        }
    }

    private static Method[] resolveViewBlurMethods(Class<?> viewClass) {
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            WeakReference<Method[]> reference = VIEW_BLUR_METHOD_CACHE.get(viewClass);
            Method[] cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
        }
        Method[] resolved = findViewBlurMethods(viewClass);
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            VIEW_BLUR_METHOD_CACHE.put(viewClass, new WeakReference<>(resolved));
        }
        return resolved;
    }

    private static Method[] findViewBlurMethods(Class<?> viewClass) {
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
        return resolved.toArray(new Method[0]);
    }

    static void setOfficialLyricSlotHeight(TextView textView, int height) {
        int safeHeight = Math.max(1, height);
        setViewHeight(textView, safeHeight);
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null && itemView != textView) {
            if (itemView.getMinimumHeight() != 0) {
                itemView.setMinimumHeight(0);
            }
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
        int oldHeight = params.height;
        boolean diagnosticsEnabled = isLyricLayoutDiagnosticsEnabled();
        String before = diagnosticsEnabled ? describeViewForLog(view) : "";
        params.height = height;
        view.setLayoutParams(params);
        if (diagnosticsEnabled) {
            StructuredBridgeLog.info(
                    BridgeDebugArea.AOD,
                    BridgeEvents.OFFICIAL_LAYOUT_HEIGHT_CHANGED,
                    "Official lyric layout height changed"
                    + ", oldLayoutHeight=" + oldHeight
                    + ", newLayoutHeight=" + height
                    + ", before=" + before
                    + ", after=" + describeViewForLog(view));
        }
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

    private void rememberActiveRendererTextView(TextView textView) {
        if (textView == null) {
            return;
        }
        if (activeRendererTextView.get() != textView) {
            activeRendererTextView = new WeakReference<>(textView);
        }
        View recycler = findContainingLyricsRecyclerView(textView);
        if (recycler != null) {
            synchronized (activeRendererTargetsLock) {
                activeRendererTargetsByRecycler.put(recycler, new WeakReference<>(textView));
            }
        }
    }

    private void forgetActiveRendererTarget(TextView textView) {
        if (textView == null) {
            return;
        }
        if (activeRendererTextView.get() == textView) {
            activeRendererTextView = new WeakReference<>(null);
        }
        synchronized (activeRendererTargetsLock) {
            java.util.Iterator<Map.Entry<View, WeakReference<TextView>>> iterator =
                    activeRendererTargetsByRecycler.entrySet().iterator();
            while (iterator.hasNext()) {
                WeakReference<TextView> reference = iterator.next().getValue();
                if (reference == null || reference.get() == null || reference.get() == textView) {
                    iterator.remove();
                }
            }
        }
    }

    private void forgetActiveRendererTarget(View recycler) {
        if (recycler == null) {
            return;
        }
        TextView removed = null;
        synchronized (activeRendererTargetsLock) {
            WeakReference<TextView> reference = activeRendererTargetsByRecycler.remove(recycler);
            removed = reference == null ? null : reference.get();
        }
        if (removed != null && activeRendererTextView.get() == removed) {
            activeRendererTextView = new WeakReference<>(null);
        }
    }

    private ArrayList<TextView> snapshotActiveRendererTargets() {
        ArrayList<TextView> targets = new ArrayList<>(4);
        synchronized (activeRendererTargetsLock) {
            java.util.Iterator<Map.Entry<View, WeakReference<TextView>>> iterator =
                    activeRendererTargetsByRecycler.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<View, WeakReference<TextView>> entry = iterator.next();
                View recycler = entry.getKey();
                WeakReference<TextView> reference = entry.getValue();
                TextView textView = reference == null ? null : reference.get();
                if (recycler == null
                        || textView == null
                        || !recycler.isAttachedToWindow()
                        || !textView.isAttachedToWindow()
                        || findContainingLyricsRecyclerView(textView) != recycler) {
                    iterator.remove();
                    continue;
                }
                targets.add(textView);
            }
        }
        return targets;
    }

    private void clearActiveRendererTargets() {
        activeRendererTextView = new WeakReference<>(null);
        synchronized (activeRendererTargetsLock) {
            activeRendererTargetsByRecycler.clear();
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
        scheduleActiveLyricRefresh(textView, delayMillis, null);
    }

    private void scheduleActiveLyricRefresh(
            TextView textView,
            long delayMillis,
            WordLine currentLine) {
        if (textView == null) {
            return;
        }
        if (activeLyricUpdatePosted || !systemUiLyricModeKeepAwakeActive) {
            return;
        }
        activeLyricUpdatePosted = true;
        long resolvedDelayMillis = resolveActiveLyricRefreshDelay(delayMillis, currentLine);
        activeLyricRefreshCadenceScheduled = shouldUseActiveLyricDisplayCadence(
                resolvedDelayMillis,
                currentLine);
        activeLyricRefreshCadenceLine = currentLine;
        if (resolvedDelayMillis <= ACTIVE_LYRIC_FRAME_DELAY_MS
                && !isAodLowFrameRateLyricMode()
                && textView.isAttachedToWindow()) {
            activeLyricRefreshAnchor = textView;
            textView.postOnAnimation(activeLyricRefreshRunnable);
        } else {
            activeLyricRefreshAnchor = null;
            mainHandler.postDelayed(
                    activeLyricRefreshRunnable,
                    Math.max(16L, Math.min(resolvedDelayMillis, 600L)));
        }
    }

    private void scheduleActiveLyricRefresh(long delayMillis) {
        scheduleActiveLyricRefresh(delayMillis, null);
    }

    private void scheduleActiveLyricRefresh(long delayMillis, WordLine currentLine) {
        if (activeLyricUpdatePosted || !systemUiLyricModeKeepAwakeActive) {
            return;
        }
        activeLyricUpdatePosted = true;
        activeLyricRefreshAnchor = null;
        long resolvedDelayMillis = resolveActiveLyricRefreshDelay(delayMillis, currentLine);
        activeLyricRefreshCadenceScheduled = shouldUseActiveLyricDisplayCadence(
                resolvedDelayMillis,
                currentLine);
        activeLyricRefreshCadenceLine = currentLine;
        mainHandler.postDelayed(
                activeLyricRefreshRunnable,
                Math.max(16L, Math.min(resolvedDelayMillis, 600L)));
    }

    private void cancelActiveLyricRefreshCallbacks(boolean ignoredCancelVisibilityRecovery) {
        mainHandler.removeCallbacks(activeLyricRefreshRunnable);
        View anchor = activeLyricRefreshAnchor;
        if (anchor != null) {
            anchor.removeCallbacks(activeLyricRefreshRunnable);
        }
        activeLyricRefreshAnchor = null;
        activeLyricUpdatePosted = false;
        activeLyricRefreshCadenceScheduled = false;
        activeLyricRefreshCadenceLine = null;
        resetActiveLyricRefreshCadence();
    }

    private void scheduleLyricSurfaceReactivation(String reason) {
        if (systemUiLyricModeKeepAwakeActive
                || (currentWordLyricModel == null && !systemUiHasOfficialLyric)) {
            return;
        }
        cancelLyricSurfaceReactivationCallbacks();
        lyricSurfaceReactivationPending = true;
        int generation = lyricSurfaceReactivationGeneration;
        for (int i = 0; i < LYRIC_VISIBILITY_RECOVERY_DELAYS_MS.length; i++) {
            Runnable callback = new LyricSurfaceReactivationTask(
                    generation,
                    reason,
                    i == LYRIC_VISIBILITY_RECOVERY_DELAYS_MS.length - 1);
            synchronized (lyricSurfaceReactivationCallbacksLock) {
                lyricSurfaceReactivationCallbacks.add(callback);
            }
            mainHandler.postDelayed(callback, LYRIC_VISIBILITY_RECOVERY_DELAYS_MS[i]);
        }
    }

    private void cancelLyricSurfaceReactivationCallbacks() {
        lyricSurfaceReactivationGeneration++;
        synchronized (lyricSurfaceReactivationCallbacksLock) {
            for (Runnable callback : lyricSurfaceReactivationCallbacks) {
                mainHandler.removeCallbacks(callback);
            }
            lyricSurfaceReactivationCallbacks.clear();
        }
        lyricSurfaceReactivationPending = false;
        clearLyricSurfaceProvisionalDraw();
    }

    private void clearLyricSurfaceProvisionalDraw() {
        lyricSurfaceProvisionalDrawUntilElapsedMs = 0L;
        lyricSurfaceProvisionalRecyclerView = new WeakReference<>(null);
    }

    private boolean isProvisionalLyricSurfaceDraw(View recycler) {
        if (recycler == null
                || recycler != lyricSurfaceProvisionalRecyclerView.get()
                || SystemClock.elapsedRealtime() >= lyricSurfaceProvisionalDrawUntilElapsedMs) {
            return false;
        }
        return recycler.isAttachedToWindow() && recycler.getVisibility() == View.VISIBLE;
    }

    private final class LyricSurfaceReactivationTask implements Runnable {
        private final int generation;
        private final String reason;
        private final boolean finalPass;

        LyricSurfaceReactivationTask(int generation, String reason, boolean finalPass) {
            this.generation = generation;
            this.reason = reason;
            this.finalPass = finalPass;
        }

        @Override
        public void run() {
            synchronized (lyricSurfaceReactivationCallbacksLock) {
                lyricSurfaceReactivationCallbacks.remove(this);
            }
            if (generation != lyricSurfaceReactivationGeneration) {
                return;
            }
            if (hasStableActiveLyricRefreshSurface()) {
                activateSystemUiLyricModeFromSurface(reason + " delayed");
                return;
            }
            TextView visibleLyricTextView = findVisibleLyricTextViewForSurfaceReactivation();
            View recycler = findContainingLyricsRecyclerView(visibleLyricTextView);
            if (activateSystemUiLyricModeFromVisibleTextView(
                    visibleLyricTextView,
                    recycler,
                    reason + " delayed visible lyric TextView")) {
                return;
            }
            if (finalPass && generation == lyricSurfaceReactivationGeneration) {
                lyricSurfaceReactivationPending = false;
                clearLyricSurfaceProvisionalDraw();
            }
        }
    }

    private TextView findVisibleLyricTextViewForSurfaceReactivation() {
        WordLyricModel model = currentWordLyricModel;
        if (model == null) {
            return null;
        }
        ArrayList<TextView> candidates = new ArrayList<>(4);
        int[] visited = {0};
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (recycler == null
                    || recycler.getVisibility() != View.VISIBLE
                    || recycler.getAlpha() <= 0.05f) {
                continue;
            }
            collectVisibleLyricTextViews(recycler, model, "", candidates, visited);
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
        return null;
    }

    private long resolveActiveLyricRefreshDelay(long delayMillis, WordLine currentLine) {
        long boundedDelayMillis = Math.max(16L, Math.min(delayMillis, 600L));
        if (lastPlaybackIsPlaying && isAodLowFrameRateLyricMode()) {
            return Math.max(boundedDelayMillis, AOD_ACTIVE_LYRIC_REFRESH_DELAY_MS);
        }
        if (lastPlaybackIsPlaying
                && boundedDelayMillis <= ACTIVE_LYRIC_FRAME_DELAY_MS
                && !requiresDisplayRateActiveLyricRefresh(currentLine)) {
            return ACTIVE_LYRIC_STATIC_FRAME_DELAY_MS;
        }
        return boundedDelayMillis;
    }

    private boolean shouldUseActiveLyricDisplayCadence(
            long resolvedDelayMillis,
            WordLine currentLine) {
        return lyricUiConfig.maxRefreshRateHz > 0
                && lastPlaybackIsPlaying
                && !isAodLowFrameRateLyricMode()
                && resolvedDelayMillis <= ACTIVE_LYRIC_FRAME_DELAY_MS
                && requiresDisplayRateActiveLyricRefresh(currentLine);
    }

    private boolean consumeActiveLyricRefreshFrame() {
        int maxRefreshRateHz = lyricUiConfig.maxRefreshRateHz;
        if (maxRefreshRateHz <= 0) {
            resetActiveLyricRefreshCadence();
            return true;
        }
        if (activeLyricRefreshCadenceHz != maxRefreshRateHz) {
            activeLyricRefreshCadenceHz = maxRefreshRateHz;
            activeLyricRefreshNextDeadlineNanos =
                    LyricRefreshRatePolicy.UNSET_DEADLINE_NANOS;
        }
        long nowNanos = System.nanoTime();
        if (!LyricRefreshRatePolicy.isFrameDue(
                nowNanos,
                activeLyricRefreshNextDeadlineNanos,
                maxRefreshRateHz)) {
            return false;
        }
        activeLyricRefreshNextDeadlineNanos = LyricRefreshRatePolicy.advanceDeadline(
                nowNanos,
                activeLyricRefreshNextDeadlineNanos,
                maxRefreshRateHz);
        return true;
    }

    private void resetActiveLyricRefreshCadence() {
        activeLyricRefreshCadenceHz = -1;
        activeLyricRefreshNextDeadlineNanos =
                LyricRefreshRatePolicy.UNSET_DEADLINE_NANOS;
    }

    private boolean requiresDisplayRateActiveLyricRefresh(WordLine currentLine) {
        WordLine line = currentLine;
        if (line == null) {
            WordLyricModel model = currentWordLyricModel;
            if (model == null) {
                return false;
            }
            long position = estimatePlaybackPositionMillis();
            line = resolvePlaybackActiveLyricLine(model, position);
            if (line == null && position >= 0L) {
                line = model.lineAt(model.displayIndexAt(position));
            }
            if (line == null && position < 0L) {
                line = model.findLineAtTime(activeLyricLineTimeMs);
            }
        }
        if (line == null) {
            return false;
        }
        if (line.timingMode != LyricTimingMode.LINE_TIMED
                || lyricUiLineTimedProgressEnabled) {
            return true;
        }
        return line.passiveLinePanEligible
                || lyricUiInactiveBlurEnabled;
    }

    private void refreshActiveLyricTextView() {
        long performanceStartedAt = performanceSampler.begin();
        try {
            runActiveLyricRefresh();
        } finally {
            performanceSampler.end(
                    BridgePerformanceSampler.Metric.ACTIVE_REFRESH,
                    performanceStartedAt);
        }
    }

    private void runActiveLyricRefresh() {
        if (!systemUiLyricModeKeepAwakeActive) {
            return;
        }
        if (!hasActiveLyricRefreshSurface()) {
            if (!retainLyricModeForTransientSurfaceMiss("active refresh surface miss")) {
                deactivateSystemUiLyricModeAfterSurfaceHidden();
            }
            return;
        }
        WordLyricModel model = currentWordLyricModel;
        if (model == null) {
            screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
            return;
        }

        long position = estimatePlaybackPositionMillis();
        WordLine line = resolvePlaybackActiveLyricLine(model, position);
        if (line == null && position >= 0L) {
            line = model.lineAt(model.displayIndexAt(position));
        }
        if (line == null && position < 0L) {
            line = model.findLineAtTime(activeLyricLineTimeMs);
        }
        if (line == null) {
            return;
        }
        TextView directTarget = null;
        WordLine directLine = null;
        int directTargetsUpdated = 0;
        for (TextView target : snapshotActiveRendererTargets()) {
            if (!isEffectivelyVisible(target)) {
                continue;
            }
            WordLine targetLine = resolveRecyclerVisualActiveLyricLine(model, position, target);
            if (targetLine == null
                    || !WordLyricRenderSupport.matchesWordLineText(targetLine, normalizedTextOf(target))) {
                continue;
            }
            target.postInvalidateOnAnimation();
            if (directTarget == null) {
                directTarget = target;
                directLine = targetLine;
            }
            directTargetsUpdated++;
        }
        if (directTargetsUpdated > 0 && directTarget != null) {
            if (directLine != null) {
                line = directLine;
            }
            activeLyricLine = line.normalizedText;
            activeLyricLineTimeMs = line.timeMillis;
            activeRendererWordLine = line;
            scheduleActiveLyricRefresh(
                    directTarget,
                    lastPlaybackIsPlaying ? ACTIVE_LYRIC_FRAME_DELAY_MS : 500L,
                    line);
            return;
        }

        activeLyricLine = line.normalizedText;
        activeLyricLineTimeMs = line.timeMillis;

        ArrayList<TextView> candidates = activeLyricRefreshCandidates;
        candidates.clear();
        collectActiveTextViews(candidates);
        boolean hasSearchRoot = hasActiveLyricRefreshSurface();
        int rememberedCandidateCount = candidates.size();
        int effectivelyVisibleCandidateCount = countEffectivelyVisibleLyricCandidates(candidates);
        if (LockscreenIntegrationPolicy.shouldSearchAttachedRecyclerForLyricCandidates(
                rememberedCandidateCount,
                effectivelyVisibleCandidateCount)
                && hasSearchRoot) {
            // During SystemUI cross-fades the old row views can stay attached while their
            // ancestor is transparent. A non-empty weak-reference cache must not prevent us
            // from discovering the replacement RecyclerView's already-bound rows.
            mergeVisibleLyricTextViewsFromRoots(candidates, model, activeLyricLine);
            if (candidates.size() > rememberedCandidateCount
                    && isLyricLayoutDiagnosticsEnabled()) {
                info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "Recovered lyric candidates from attached RecyclerView"
                        + " | remembered=" + rememberedCandidateCount
                        + ", visibleRemembered=" + effectivelyVisibleCandidateCount
                        + ", discovered=" + (candidates.size() - rememberedCandidateCount));
            }
        }
        if (candidates.isEmpty() && !hasSearchRoot) {
            return;
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
            if (!WordLyricRenderSupport.sameWordLine(match.line, line)
                    && !WordLyricRenderSupport.sameWordLine(match.translationLine, line)) {
                continue;
            }

            textView.postInvalidateOnAnimation();
            rememberActiveRendererTextView(textView);
            activeRendererWordLine = line;
            updated++;
        }

        boolean settleWindowActive = isLyricRecyclerSettleWindowActive();
        if (updated == 0 && visible > 0 && !settleWindowActive) {
            updated = invalidateRenderableVisibleLyricTextViews(candidates, model, position);
        }
        maybeLogActiveRefresh(position, line.text, candidates.size(), attached, visible, updated);
        long nextDelay = !lastPlaybackIsPlaying
                ? 500L
                : updated > 0
                ? ACTIVE_LYRIC_FRAME_DELAY_MS
                : ACTIVE_LYRIC_RETRY_DELAY_MS;
        TextView nextAnchor = activeRendererTextView.get();
        if (nextAnchor == null
                || !nextAnchor.isAttachedToWindow()
                || !isEffectivelyVisible(nextAnchor)) {
            nextAnchor = firstActiveLyricTextView();
        }
        if (nextAnchor != null) {
            scheduleActiveLyricRefresh(nextAnchor, nextDelay, line);
        } else if (hasSearchRoot) {
            scheduleActiveLyricRefresh(nextDelay, line);
        }
    }

    private static int countEffectivelyVisibleLyricCandidates(
            ArrayList<TextView> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int visible = 0;
        for (TextView candidate : candidates) {
            if (isEffectivelyVisible(candidate)) {
                visible++;
            }
        }
        return visible;
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
            if (currentText == null || currentText.length() == 0 || currentText.length() > lyricTextLengthLimit()) {
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
                rememberActiveRendererTextView(textView);
                activeRendererWordLine = visibleLine;
            }
            updated++;
        }
        return updated;
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
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler.isAttachedToWindow()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasStableActiveLyricRefreshSurface() {
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler.isAttachedToWindow()
                        && recycler.getVisibility() == View.VISIBLE
                        && recycler.isShown()) {
                    // Alpha, translation, and size are intentionally ignored here. An attached
                    // AOD lyric surface is valid before its first visible frame is committed.
                    return true;
                }
            }
        }
        // Some island transitions render the replacement lyric child before the old container's
        // visibility state has settled. A confirmed visible lyric row is a lyric-local fallback.
        return firstActiveLyricTextView() != null;
    }

    private boolean hasActiveLyricRefreshSurface() {
        if (hasStableActiveLyricRefreshSurface()) {
            return true;
        }
        View recycler = lyricSurfaceProvisionalRecyclerView.get();
        return recycler != null
                && recycler.isAttachedToWindow()
                && recycler.getVisibility() == View.VISIBLE
                && SystemClock.elapsedRealtime() < lyricSurfaceProvisionalDrawUntilElapsedMs;
    }

    private boolean isRememberedLyricsRecyclerView(View candidate) {
        if (candidate == null) {
            return false;
        }
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler == candidate) {
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
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler.isAttachedToWindow()) {
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
                    && text.length() <= lyricTextLengthLimit()
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
        return findLyricsRecyclerMatch(textView) != null;
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

    private static Object readRecyclerAdapter(View recycler) {
        if (recycler == null) {
            return null;
        }
        Class<?> recyclerClass = recycler.getClass();
        Method method;
        synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
            WeakReference<Method> reference = RECYCLER_ADAPTER_METHOD_CACHE.get(recyclerClass);
            method = reference == null ? null : reference.get();
        }
        if (method == null) {
            for (Class<?> current = recyclerClass; current != null && method == null;
                    current = current.getSuperclass()) {
                try {
                    method = current.getDeclaredMethod("getAdapter");
                    method.setAccessible(true);
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
            if (method == null) {
                return null;
            }
            synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
                RECYCLER_ADAPTER_METHOD_CACHE.put(recyclerClass, new WeakReference<>(method));
            }
        }
        try {
            return method.invoke(recycler);
        } catch (Throwable ignored) {
            return null;
        }
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

    private static boolean isBooleanParameter(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
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

    private static int readLyricsRecyclerCurrentIndex(Object recycler) {
        int fieldIndex = LYRICS_RECYCLER_FIELD_ACCESSOR.readCurrentIndex(recycler, Integer.MIN_VALUE);
        if (fieldIndex != Integer.MIN_VALUE) {
            return fieldIndex;
        }
        if (recycler == null) {
            return -1;
        }
        Integer immersiveIndex = readImmersiveCurrentIndexField(recycler);
        if (immersiveIndex != null) {
            return immersiveIndex;
        }
        synchronized (IMMERSIVE_ENTRY_INDEXES) {
            ImmersiveEntryIndex entry = IMMERSIVE_ENTRY_INDEXES.get(recycler);
            return entry == null ? -1 : entry.index;
        }
    }

    private static long readLongField(Object target, String fieldName, long defaultValue) {
        Object value = readFieldValue(target, fieldName);
        return value instanceof Number ? ((Number) value).longValue() : defaultValue;
    }

    private static float readFloatField(Object target, String fieldName, float defaultValue) {
        Object value = readFieldValue(target, fieldName);
        return value instanceof Number ? ((Number) value).floatValue() : defaultValue;
    }

    private static boolean readBooleanField(Object target, String fieldName, boolean defaultValue) {
        Object value = readFieldValue(target, fieldName);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : defaultValue;
    }

    static Object readFieldValue(Object target, String fieldName) {
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

    static final Object PROBE_FIELD_MISSING = new Object();

    /** Distinguishes a genuinely null field from a missing or unreadable field. */
    private static Object readFieldOrMissing(Object target, String fieldName) {
        if (target == null || TextUtils.isEmpty(fieldName)) {
            return PROBE_FIELD_MISSING;
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
                return PROBE_FIELD_MISSING;
            }
        }
        return PROBE_FIELD_MISSING;
    }

    /** Returns the first existing zero-argument accessor without invoking it. */
    private static String firstNoArgMethodName(Object target, String... names) {
        if (target == null || names == null) {
            return null;
        }
        for (String name : names) {
            if (hasNoArgMethod(target, name)) {
                return name;
            }
        }
        return null;
    }

    private static boolean hasNoArgMethod(Object target, String methodName) {
        if (target == null || TextUtils.isEmpty(methodName)) {
            return false;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                current.getDeclaredMethod(methodName);
                return true;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    static Context currentApplicationContext() {
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

    static Context applicationContextOf(Context context) {
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
                        setAodLowFrameRateLyricMode(true);
                        beginScreenStateLyricSettleWindow();
                        screenTimeout.invalidateScreenTimeoutKeyguardCache();
                        screenTimeout.resetScreenTimeoutKeepAwakeWindow();
                        screenTimeout.screenTimeoutPausedByScreenOff = true;
                        screenTimeout.screenTimeoutPausedByUserPresent = false;
                        screenTimeout.releaseScreenTimeoutWakeLock("screen off");
                        screenTimeout.maybeLogScreenTimeout("Paused screen timeout keep-awake after screen off", true);
                        screenTurnedOffForLockState = true;
                        infoAlways(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.TRANSLATION_ACTION_REBIND,
                                "Keyguard locked screen off, requesting rebind for " + currentTranslationPreferencePackage());
                        requestOplusTranslationActionRebind(currentTranslationPreferencePackage());
                        // Keyguard may become locked slightly after SCREEN_OFF; re-run once so the
                        // gate is evaluated against the settled lock state.
                        mainHandler.postDelayed(() -> {
                            if (isDeviceKeyguardLocked(receiverContext)) {
                                requestOplusTranslationActionRebind(currentTranslationPreferencePackage());
                            }
                        }, 600L);
                        return;
                    }
                    if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        setAodLowFrameRateLyricMode(false);
                        beginScreenStateLyricSettleWindow();
                        screenTimeout.invalidateScreenTimeoutKeyguardCache();
                        if (screenTimeout.screenTimeoutPausedByScreenOff) {
                            screenTimeout.maybeLogScreenTimeout("Resumed screen timeout keep-awake after screen on", true);
                        }
                        screenTimeout.screenTimeoutPausedByScreenOff = false;
                        screenTimeout.screenTimeoutPausedByUserPresent = false;
                        screenTimeout.updateScreenTimeoutWakeLock(receiverContext);
                        // Check if device is actually locked (handles delay-lock window where screen was turned on before keyguard locked)
                        if (!isDeviceKeyguardLocked(receiverContext)) {
                            screenTurnedOffForLockState = false;
                            translationController.restoreOriginalPkgActionsRules();
                            requestOplusTranslationActionRebind(currentTranslationPreferencePackage());
                        }
                        return;
                    }
                    if (Intent.ACTION_USER_PRESENT.equals(action)) {
                        setAodLowFrameRateLyricMode(false);
                        screenTimeout.invalidateScreenTimeoutKeyguardCache();
                        screenTimeout.resetScreenTimeoutKeepAwakeWindow();
                        screenTimeout.screenTimeoutPausedByUserPresent = true;
                        screenTimeout.releaseScreenTimeoutWakeLock("user present");
                        screenTimeout.scheduleScreenTimeoutUserPresentRecheck(receiverContext);
                        screenTimeout.maybeLogScreenTimeout(
                                "Paused screen timeout keep-awake pending keyguard recheck",
                                true);
                        screenTurnedOffForLockState = false;
                        translationController.restoreOriginalPkgActionsRules();
                        infoAlways(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.TRANSLATION_ACTION_REBIND,
                                "Keyguard unlocked user present, requesting rebind for " + currentTranslationPreferencePackage());
                        requestOplusTranslationActionRebind(currentTranslationPreferencePackage());
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
                info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, "Registered SystemUI screen timeout receiver");
            } catch (Throwable t) {
                error("Failed to register SystemUI screen timeout receiver", t);
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void ensureLyricUiSettingsReceiver(Context context) {
        if (context == null) return;
        ensureUniversalPlayerBindingsReceiver(context);
        if (lyricUiSettingsReceiverRegistered) return;
        synchronized (this) {
            if (lyricUiSettingsReceiverRegistered) return;
            Context appContext = context.getApplicationContext();
            if (appContext == null) appContext = context;
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    if (intent == null) return;
                    try {
                        if (LyricUiSettings.ACTION_STYLE_CHANGED.equals(intent.getAction())) {
                            handleLyricUiStyleSettingsChanged(receiverContext, intent);
                        } else if (LyricUiSettings.ACTION_PLAYER_TRANSLATION_SETTINGS_CHANGED.equals(
                                intent.getAction())) {
                            handlePlayerTranslationSettingsChanged(receiverContext, intent);
                        } else if (LyricUiSettings.ACTION_REQUEST_MEDIA_SNAPSHOT.equals(
                                intent.getAction())) {
                            sendMediaSnapshotResult(intent);
                        } else if (LyricUiSettings.ACTION_REQUEST_MODULE_STATUS.equals(
                                intent.getAction())) {
                            handleModuleStatusRequest(intent);
                        } else if (LyricUiSettings.isDiagnosticAction(intent.getAction())) {
                            handleShortDiagnostics(intent);
                        } else if (LyricUiSettings.ACTION_CONTENT_CLEANUP_CHANGED.equals(
                                intent.getAction())) {
                            handleLyricContentCleanupChanged(receiverContext, intent);
                        } else if (LyricUiSettings.ACTION_DEBUG_SETTINGS_CHANGED.equals(
                                intent.getAction())) {
                            handleBridgeDebugSettingsChanged(receiverContext, intent);
                        } else if (LyricUiSettings.ACTION_RESTART_SYSTEM_UI.equals(
                                intent.getAction())) {
                            handleSystemUiRestartRequest(intent);
                        }
                    } catch (RuntimeException failure) {
                        // An unreadable callback or stale request must not kill the host process.
                        warn(
                                LyricLogFormatter.Area.SETTINGS,
                                "settings-request-failed",
                                "Could not handle settings request"
                                        + " | action=" + intent.getAction()
                                        + ", error=" + failure.getClass().getSimpleName());
                    }
                }
            };
            try {
                IntentFilter filter = new IntentFilter(LyricUiSettings.ACTION_STYLE_CHANGED);
                filter.addAction(LyricUiSettings.ACTION_PLAYER_TRANSLATION_SETTINGS_CHANGED);
                filter.addAction(LyricUiSettings.ACTION_REQUEST_MEDIA_SNAPSHOT);
                filter.addAction(LyricUiSettings.ACTION_REQUEST_MODULE_STATUS);
                filter.addAction(LyricUiSettings.ACTION_DIAGNOSTIC_START);
                filter.addAction(LyricUiSettings.ACTION_DIAGNOSTIC_MARK);
                filter.addAction(LyricUiSettings.ACTION_DIAGNOSTIC_STOP);
                filter.addAction(LyricUiSettings.ACTION_DIAGNOSTIC_SNAPSHOT);
                filter.addAction(LyricUiSettings.ACTION_CONTENT_CLEANUP_CHANGED);
                filter.addAction(LyricUiSettings.ACTION_DEBUG_SETTINGS_CHANGED);
                filter.addAction(LyricUiSettings.ACTION_RESTART_SYSTEM_UI);
                if (Build.VERSION.SDK_INT >= 33) {
                    appContext.registerReceiver(
                            receiver,
                            filter,
                            LyricUiSettings.CHANGE_SETTINGS_PERMISSION,
                            mainHandler,
                            Context.RECEIVER_EXPORTED);
                } else {
                    appContext.registerReceiver(
                            receiver,
                            filter,
                            LyricUiSettings.CHANGE_SETTINGS_PERMISSION,
                            mainHandler);
                }
                lyricUiSettingsReceiver = receiver;
                lyricUiSettingsReceiverRegistered = true;
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SYSTEMUI_BOOTSTRAP, "Registered protected SystemUI lyric settings receiver");
                ensureUniversalPlayerBindingsReceiver(appContext);
            } catch (Throwable t) {
                error("Failed to register protected SystemUI lyric settings receiver", t);
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void ensureUniversalPlayerBindingsReceiver(Context context) {
        if (context == null || universalBindingsReceiverRegistered) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        synchronized (this) {
            if (universalBindingsReceiverRegistered) {
                return;
            }
        UniversalPlayerBridgeContract.restoreFrom(
                appContext.getSharedPreferences(
                        TRANSLATION_PREFERENCES_NAME,
                        Context.MODE_PRIVATE));
        syncDeclaredTranslationTogglePackages();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                handleUniversalPlayerBindingsChanged(receiverContext, intent);
            }
        };
        try {
            IntentFilter filter = new IntentFilter(
                    UniversalPlayerBridgeContract.ACTION_PLAYER_BINDINGS_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(
                        receiver,
                        filter,
                        Context.RECEIVER_EXPORTED);
            } else {
                appContext.registerReceiver(receiver, filter);
            }
            universalBindingsReceiver = receiver;
            universalBindingsReceiverRegistered = true;
            infoAlways(
                    BridgeDebugArea.BOOTSTRAP,
                    BridgeEvents.SYSTEMUI_BOOTSTRAP,
                    "Registered Universal Player Provider bindings receiver");
        } catch (Throwable t) {
            error("Failed to register Universal Player Provider bindings receiver", t);
        }
        }
    }

    private void handleUniversalPlayerBindingsChanged(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!UniversalPlayerBridgeContract.ACTION_PLAYER_BINDINGS_CHANGED.equals(intent.getAction())) {
            return;
        }
        if (!UniversalPlayerBridgeContract.isAcceptedIdentity(
                intent.getStringExtra(UniversalPlayerBridgeContract.EXTRA_PROVIDER_IDENTITY))) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    BridgeEvents.DETAIL,
                    "Rejected Universal Player Provider bindings | identity mismatch");
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        SharedPreferences preferences = appContext.getSharedPreferences(
                TRANSLATION_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        java.util.List<String> bound = UniversalPlayerBridgeContract.sanitizeBoundPackages(
                intent.getStringArrayListExtra(UniversalPlayerBridgeContract.EXTRA_BOUND_PACKAGES));
        UniversalPlayerBridgeContract.persist(preferences, true, bound);
        boolean groupDefault = preferences.getBoolean(
                LyricUiSettings.translationDefaultKeyForPackage(
                        UniversalPlayerBridgeContract.TRANSLATION_GROUP_PACKAGE),
                lyricUiConfig.defaultTranslationEnabled);
        boolean groupButton = preferences.getBoolean(
                LyricUiSettings.translationButtonKeyForPackage(
                        UniversalPlayerBridgeContract.TRANSLATION_GROUP_PACKAGE),
                translationController.defaultTranslationButtonEnabled());
        SharedPreferences.Editor editor = preferences.edit();
        for (String packageName : bound) {
            String defaultKey = LyricUiSettings.translationDefaultKeyForPackage(packageName);
            if (!preferences.contains(defaultKey)) {
                editor.putBoolean(defaultKey, groupDefault);
            }
            String buttonKey = LyricUiSettings.translationButtonKeyForPackage(packageName);
            if (!preferences.contains(buttonKey)) {
                editor.putBoolean(buttonKey, groupButton);
                translationController.translationButtonEnabledByPackage.put(packageName, groupButton);
            }
        }
        editor.apply();
        syncDeclaredTranslationTogglePackages();
        reapplyCurrentTranslationButtonSetting();
        infoAlways(
                BridgeDebugArea.BOOTSTRAP,
                BridgeEvents.SETTINGS_APPLIED,
                "Applied Universal Player Provider bindings | present=true count=" + bound.size());
    }

    private void syncDeclaredTranslationTogglePackages() {
        providerDeclaredTranslationTogglePackages.clear();
        providerDeclaredTranslationTogglePackages.addAll(
                UniversalPlayerBridgeContract.extraHistoryPackages());
    }

    /**
     * Restarts only the main SystemUI process after the protected settings broadcast has been
     * delivered.  The settings app cannot kill another UID directly, so the request is handled
     * here while running inside SystemUI.  Sub-processes keep running; the persistent main
     * process is the one that owns the lock-screen surface and will be restarted by the system.
     */
    @SuppressWarnings("deprecation")
    private void handleSystemUiRestartRequest(Intent intent) {
        if (!SYSTEMUI_PACKAGE.equals(logProcessName)) {
            return;
        }
        ResultReceiver receiver = intent == null
                ? null
                : intent.getParcelableExtra(
                LyricUiSettings.EXTRA_SYSTEM_UI_RESTART_RESULT_RECEIVER);
        if (receiver != null) {
            Bundle result = new Bundle();
            result.putLong(
                    LyricUiSettings.RESULT_SYSTEM_UI_RESTART_REQUEST_ID,
                    intent.getLongExtra(
                            LyricUiSettings.EXTRA_SYSTEM_UI_RESTART_REQUEST_ID,
                            -1L));
            result.putBoolean(LyricUiSettings.RESULT_SYSTEM_UI_RESTART_ACCEPTED, true);
            try {
                receiver.send(
                        LyricUiSettings.RESULT_SYSTEM_UI_RESTART_ACKNOWLEDGED,
                        result);
            } catch (Throwable throwable) {
                warn(
                        LyricLogFormatter.Area.SETTINGS,
                        "systemui-restart-ack-failed",
                        "Could not acknowledge SystemUI restart request"
                                + " | error=" + throwable.getClass().getSimpleName());
            }
        }
        mainHandler.postDelayed(() -> {
            try {
                android.os.Process.killProcess(android.os.Process.myPid());
            } catch (Throwable throwable) {
                error("Failed to restart SystemUI process", throwable);
            }
        }, 120L);
    }

    @SuppressWarnings("deprecation")
    private void handleModuleStatusRequest(Intent intent) {
        if (!SYSTEMUI_PACKAGE.equals(logProcessName)) {
            return;
        }
        ResultReceiver receiver = intent == null
                ? null
                : intent.getParcelableExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER);
        if (receiver == null) return;
        Bundle result = new Bundle();
        result.putInt(LyricUiSettings.RESULT_MODULE_VERSION_CODE, BuildConfig.VERSION_CODE);
        result.putString(LyricUiSettings.RESULT_PROCESS, logProcessName);
        result.putLong(LyricUiSettings.RESULT_MODULE_TIMESTAMP, System.currentTimeMillis());
        StructuredBridgeLog.config().putStatus(result);
        io.github.andrealtb.lockscreenlyrics.diagnostics.BuildIdentity.putRuntimeStatus(
                result, logProcessName, diagnosticProcessEpoch, diagnosticFramework,
                diagnosticApi, diagnosticFrameworkProperties, diagnosticUiRevision);
        result.putString("cll_observed_aod_low_frame_rate", diagnosticAod);
        io.github.andrealtb.lockscreenlyrics.diagnostics.AodModeObservation.global().putStatus(result);
        io.github.andrealtb.lockscreenlyrics.diagnostics.LoadedComponentFingerprints.global().putStatus(result);
        CompatibilityStatus.global().putStatus(result);
        try {
            receiver.send(0, result);
        } catch (Throwable throwable) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "module-status-reply-failed",
                    "Could not answer module status request"
                            + " | error=" + throwable.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("deprecation")
    private void handleShortDiagnostics(Intent intent) {
        if (intent == null || !LyricUiSettings.isDiagnosticAction(intent.getAction())) return;
        ResultReceiver receiver = intent.getParcelableExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER);
        if (receiver == null) return;
        io.github.andrealtb.lockscreenlyrics.diagnostics.ShortDiagnosticSession session =
                io.github.andrealtb.lockscreenlyrics.diagnostics.ShortDiagnosticSession.global();
        String status = "SNAPSHOT";
        String expected = intent.getStringExtra(LyricUiSettings.EXTRA_DIAGNOSTIC_SESSION);
        if (LyricUiSettings.ACTION_DIAGNOSTIC_START.equals(intent.getAction())) {
            session.start(); status = "STARTED_OR_ALREADY_ACTIVE";
            io.github.andrealtb.lockscreenlyrics.diagnostics.LoadedComponentFingerprints.global().collect();
        } else if (LyricUiSettings.ACTION_DIAGNOSTIC_MARK.equals(intent.getAction())) {
            status = session.mark(expected);
        } else if (LyricUiSettings.ACTION_DIAGNOSTIC_STOP.equals(intent.getAction())) {
            status = session.stop(expected);
        }
        Bundle runtime = new Bundle();
        io.github.andrealtb.lockscreenlyrics.diagnostics.BuildIdentity.putRuntimeStatus(
                runtime, logProcessName, diagnosticProcessEpoch, diagnosticFramework,
                diagnosticApi, diagnosticFrameworkProperties, diagnosticUiRevision);
        runtime.putString("cll_observed_aod_low_frame_rate", diagnosticAod);
        io.github.andrealtb.lockscreenlyrics.diagnostics.AodModeObservation.global().putStatus(runtime);
        StructuredBridgeLog.config().putStatus(runtime);
        CompatibilityStatus.global().putStatus(runtime);
        io.github.andrealtb.lockscreenlyrics.diagnostics.LoadedComponentFingerprints.global().putStatus(runtime);
        LyricUiConfig config = lyricUiConfig;
        runtime.putBoolean("diag_config_lineProgress", config.lineTimedProgressEnabled);
        runtime.putBoolean("diag_config_translationProgress", config.translationProgressEnabled);
        runtime.putBoolean("diag_config_charLift", config.charLiftEnabled);
        runtime.putBoolean("diag_config_coverGlow", config.glowFollowsCover);
        Bundle result = new Bundle(); result.putInt("schema", 1);
        result.putString("sessionId", session.sessionId()); result.putString("status", status);
        result.putString("diagnostic_json", session.summaryJson(runtime));
        try { receiver.send(0, result); } catch (RuntimeException ignored) { }
    }

    @SuppressWarnings("deprecation")
    private void sendMediaSnapshotResult(Intent intent) {
        ResultReceiver receiver = intent.getParcelableExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER);
        if (receiver == null) return;
        Bundle result = new Bundle();
        result.putString(LyricUiSettings.RESULT_TITLE, lastSystemUiSongName);
        result.putString(LyricUiSettings.RESULT_ARTIST, lastSystemUiArtistName);
        result.putString(LyricUiSettings.RESULT_ALBUM, lastSystemUiAlbumName);
        boolean snapshotMatchesCurrent = TextUtils.equals(
                WordLyricRenderSupport.normalizeLine(currentCleanupSnapshotTitle),
                WordLyricRenderSupport.normalizeLine(lastSystemUiSongName))
                && (TextUtils.isEmpty(currentCleanupSnapshotArtist)
                || TextUtils.isEmpty(lastSystemUiArtistName)
                || TextUtils.equals(
                WordLyricRenderSupport.normalizeLine(currentCleanupSnapshotArtist),
                WordLyricRenderSupport.normalizeLine(lastSystemUiArtistName)));
        result.putString(
                LyricUiSettings.RESULT_TRACK_KEY,
                snapshotMatchesCurrent ? currentCleanupSnapshotTrackKey : "");
        result.putString(
                LyricUiSettings.RESULT_RAW_LYRIC,
                snapshotMatchesCurrent
                        ? LyricOpeningCleanup.previewTimedText(currentCleanupSnapshotRawLyric)
                        : "");
        receiver.send(0, result);
    }

    private boolean hasSupportedSystemUiPlayer() {
        return lastSystemUiPackageSupported
                || (!TextUtils.isEmpty(currentLyricProviderPackage)
                && isCurrentLyricProviderPackage(currentLyricProviderPackage));
    }

    private boolean isDeviceKeyguardLocked(Context context) {
        Context appContext = applicationContextOf(context);
        if (appContext != null) {
            try {
                KeyguardManager keyguardManager = screenTimeout.screenTimeoutKeyguardManager;
                if (keyguardManager == null) {
                    keyguardManager =
                            (KeyguardManager) appContext.getSystemService(Context.KEYGUARD_SERVICE);
                    screenTimeout.screenTimeoutKeyguardManager = keyguardManager;
                }
                if (keyguardManager != null) {
                    return keyguardManager.isKeyguardLocked();
                }
            } catch (Throwable ignored) {
            }
        }
        return screenTurnedOffForLockState;
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

    @SuppressLint("WrongConstant")
    private static MediaMetadata buildMetadataWithLyricInfoPreservingArtwork(
            MediaMetadata base,
            String lyricInfo,
            String trackTitle,
            String trackArtist) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder(base);
        copyMediaMetadataArtwork(base, builder);
        return builder
                .putString(OPLUS_LYRIC_INFO_KEY, nullToEmpty(lyricInfo))
                .build();
    }

    private static void copyMediaMetadataArtwork(
            MediaMetadata source,
            MediaMetadata.Builder target) {
        if (source == null || target == null) {
            return;
        }
        boolean hasArtworkUri = false;
        String[] uriKeys = {
                MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
        };
        for (String key : uriKeys) {
            if (!TextUtils.isEmpty(source.getString(key))) {
                hasArtworkUri = true;
                break;
            }
        }
        String[] bitmapKeys = {
                MediaMetadata.METADATA_KEY_ART,
                MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON
        };
        for (String key : bitmapKeys) {
            Bitmap bitmap = source.getBitmap(key);
            if (bitmap != null) {
                if (hasArtworkUri && bitmap.getWidth() <= 1 && bitmap.getHeight() <= 1) {
                    target.putBitmap(key, null);
                } else {
                    target.putBitmap(key, bitmap);
                }
            }
        }
        for (String key : uriKeys) {
            String uri = source.getString(key);
            if (!TextUtils.isEmpty(uri)) {
                target.putString(key, uri);
            }
        }
    }

    @SuppressLint("WrongConstant")
    private static MediaMetadata buildMetadataWithStableSystemUiIdentity(
            MediaMetadata base,
            String title,
            String artist) {
        return new MediaMetadata.Builder(base)
                .putString(MediaMetadata.METADATA_KEY_TITLE, nullToEmpty(title))
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, nullToEmpty(title))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, nullToEmpty(artist))
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, nullToEmpty(artist))
                .build();
    }

    static Bitmap firstNonNullBitmap(Bitmap... bitmaps) {
        if (bitmaps == null) return null;
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null) return bitmap;
        }
        return null;
    }

    static String buildTrackKey(String title, String artist) {
        return TrackIdentity.buildKey(title, artist);
    }

    private boolean cacheSystemUiLyricModelLocked(
            XposedInterface.Chain chain,
            LyricInfoContract.Payload payload,
            LyricInfoContract.Payload originalPayload,
            long loadEpoch,
            String packageName,
            String observedTitle,
            String observedArtist) {
        if (payload == null) {
            return false;
        }
        String displayLyric = payload.lyric;
        String rawLyric = SentenceWindowContract.renderSource(payload);
        String translationLyric = payload.translationLyric;
        boolean displayTimed = LyricInfoContract.containsTimedLrc(displayLyric);
        boolean rawTimed = LyricInfoContract.containsTimedLrc(rawLyric);
        String payloadKey = payloadTrackKey(payload);
        String signature = buildWordLyricModelSignature(payload, rawLyric, translationLyric);
        if (!rawTimed) {
            String observedKey = buildTrackKey(observedTitle, observedArtist);
            if (TextUtils.isEmpty(payloadKey)) payloadKey = observedKey;
            signature = "native-display|" + observedKey + '|' + signature;
        }
        final boolean hadModel = currentWordLyricModel != null;
        final LyricInfoTrackMatcher.NativeOnlyModelRetention retention =
                !rawTimed && hadModel && lastSystemUiPackageSupported
                        ? LyricInfoTrackMatcher.nativeOnlyModelRetention(
                                currentLyricProviderPayload,
                                currentLyricObservedTrackKey,
                                currentLyricProviderPackage,
                                payload,
                                packageName,
                                observedTitle,
                                observedArtist)
                        : null;
        NativeLyricModelAssembler.AssemblyResult assembled = null;
        if (retention != LyricInfoTrackMatcher.NativeOnlyModelRetention.RETAIN
                && !(currentWordLyricModel != null
                && !TextUtils.isEmpty(signature)
                && signature.equals(currentWordLyricModelSignature))) {
            long parseStartedAt = performanceSampler.begin();
            try {
                assembled = nativeLyricModelAssembler.assemble(
                        rawLyric,
                        rawTimed,
                        displayLyric,
                        displayTimed,
                        translationLyric);
            } finally {
                performanceSampler.end(
                        BridgePerformanceSampler.Metric.MODEL_PARSE,
                        parseStartedAt);
            }
        }

        if (!systemUiLyricPublicationGate.canCommit(loadEpoch)) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED,
                    "Dropped stale SystemUI lyric parse, epoch=" + loadEpoch
                            + ", currentEpoch=" + systemUiLyricPublicationGate.currentEpoch()
                            + ", track=" + SensitiveFieldRedactor.trackHash(
                            buildTrackKey(observedTitle, observedArtist)));
            return false;
        }

        if (retention == LyricInfoTrackMatcher.NativeOnlyModelRetention.RETAIN) {
            // Keep the complete payload and cleanup/translation lanes along with its model.
            // The caller still forwards this native update and remembers the new load context.
            screenTimeout.markScreenTimeoutLyricModelEvidence();
            publishLyricRenderBinding(currentWordLyricModel,
                    nativeLyricModelAssembler.displayDatasetFor(displayLyric), loadEpoch);
            info(BridgeDebugArea.RENDERER, BridgeEvents.LYRIC_MODEL_RETAINED,
                    "Retained identity-bound custom model during native-only refresh"
                            + " | epoch=" + loadEpoch
                            + ", track=" + SensitiveFieldRedactor.trackHash(
                                    currentLyricObservedTrackKey));
            screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
            return true;
        }

        rememberCurrentCleanupSnapshot(originalPayload, observedTitle, observedArtist);
        acceptCurrentLyricProvider(
                chain,
                payload,
                packageName,
                observedTitle,
                observedArtist);
        systemUiHasOfficialLyric = displayTimed || rawTimed;
        if (systemUiHasOfficialLyric) {
            screenTimeout.markScreenTimeoutLyricModelEvidence();
        } else {
            screenTimeout.screenTimeoutLyricEvidenceGraceUntilElapsedMs = 0L;
        }
        if (assembled == null) {
            if (currentWordLyricModel != null) {
                publishLyricRenderBinding(currentWordLyricModel,
                        nativeLyricModelAssembler.displayDatasetFor(displayLyric), loadEpoch);
            }
            screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
            return true;
        }

        boolean replacingModel = currentWordLyricModel != null
                && !TextUtils.isEmpty(signature)
                && !signature.equals(currentWordLyricModelSignature);
        if (replacingModel) {
            lyricModelReplacementInProgress = true;
            beginOfficialLyricTrackHandoff("publishing replacement lyric model");
            extendLyricTrackPositionResetGuard();
        }
        if (assembled.aliasesApplied > 0) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL,
                    "Applied official lyric display aliases, aliases=" + assembled.aliasesApplied
                            + ", first="
                            + LyricModelTraceSupport.describeSensitiveText(assembled.firstAlias));
        }
        if (assembled.supplementalDisplayAdded > 0) {
            info(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.DETAIL,
                    "Merged supplemental lyric translations, added="
                            + assembled.supplementalDisplayAdded);
        }
        if (assembled.supplementalTranslationAdded > 0) {
            info(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.DETAIL,
                    "Merged supplemental lyric translations, added="
                            + assembled.supplementalTranslationAdded);
        }
        if (assembled.propagatedTranslations > 0) {
            info(BridgeDebugArea.LYRIC, BridgeEvents.DETAIL,
                    "Propagated nearby translations during model assembly, copied="
                            + assembled.propagatedTranslations);
        }

        WordLyricModel model = assembled.model;
        if (payload.snapshotPositionMillis >= 0L) {
            for (WordLine line : model.lines) line.sentenceWindowExpanded = true;
        }
        if (model.lines.isEmpty()) {
            clearWordLyricModelState(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.RENDER_STATE_CHANGED,
                    "Lyrics Core returned no renderable model; using official SystemUI lyric renderer");
            return true;
        }
        if (!rawTimed) {
            StructuredBridgeLog.debugTransition(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.LYRIC_MODEL_STATE,
                    () -> "action=native-display-model"
                            + ", epoch=" + loadEpoch
                            + ", previousModel=" + hadModel
                            + ", retention=" + (retention != null
                                    ? retention.name()
                                    : hadModel ? "player-inactive" : "model-missing")
                            + ", lines=" + model.lines.size()
                            + ", parser=" + model.parserName
                            + ", track=" + SensitiveFieldRedactor.trackHash(
                                    buildTrackKey(observedTitle, observedArtist)));
        }
        publishWordLyricModel(model, signature, payloadKey, loadEpoch);
        return true;
    }

    private void rememberCurrentCleanupSnapshot(
            LyricInfoContract.Payload originalPayload,
            String fallbackTitle,
            String fallbackArtist) {
        if (originalPayload == null) {
            return;
        }
        currentCleanupSnapshotTrackKey = firstNonEmpty(
                originalPayload.trackKey,
                buildTrackKey(
                        firstNonEmpty(originalPayload.songName, fallbackTitle),
                        firstNonEmpty(originalPayload.artist, fallbackArtist)));
        currentCleanupSnapshotDisplayLyric = originalPayload.lyric;
        currentCleanupSnapshotRawLyric = firstNonEmpty(
                originalPayload.rawLyric,
                originalPayload.lyric);
        currentCleanupSnapshotTitle = firstNonEmpty(fallbackTitle, originalPayload.songName);
        currentCleanupSnapshotArtist = firstNonEmpty(fallbackArtist, originalPayload.artist);
    }

    private void clearWordLyricModelState(BridgeDebugArea area, String event, String message) {
        traceLyricModelState("cleared", currentWordLyricModel);
        CompatibilityStatus.global().invalidateLyricObservations();
        currentWordLyricModel = null;
        clearCommittedLyricRenderModel();
        currentWordLyricModelSignature = "";
        currentWordLyricModelTrackKey = "";
        lyricModelReplacementInProgress = false;
        info(area, event, message);
        mainHandler.post(translationTracker::refreshTranslationActionViewVisibility);
        finishOfficialLyricTrackHandoff();
        screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private void publishWordLyricModel(
            WordLyricModel model, String signature, String payloadKey, long loadEpoch) {
        // Parsing/cache state may advance on the metadata worker. It is not render ownership.
        if (currentWordLyricModel != model) CompatibilityStatus.global().invalidateLyricObservations();
        currentWordLyricModel = model;
        currentWordLyricModelSignature = signature;
        currentWordLyricModelTrackKey = payloadKey;
        traceLyricModelState("parsed", model);
        info(BridgeDebugArea.LYRIC, BridgeEvents.DETAIL,
                "Cached native SystemUI word lyric model, parser=" + model.parserName
                        + ", lines=" + model.lines.size()
                        + ", translations=" + model.translationCount());
        maybeLogWordLyricModelSlotIntegrity(model, "systemui-cache");
        publishLyricRenderBinding(model, model.officialDataset, loadEpoch);
        String translationPackage = currentLyricProviderPackage;
        mainHandler.post(() -> {
            if (currentWordLyricModel != model
                    || !systemUiLyricPublicationGate.canCommit(loadEpoch)
                    || !translationPackage.equals(currentLyricProviderPackage)) return;
            String rebindKey = translationPackage + "|" + payloadKey
                    + "|" + (model.translationCount() > 0);
            if (!rebindKey.equals(lastTranslationModelRebindKey)
                    && requestOplusTranslationActionRebind(translationPackage)) {
                lastTranslationModelRebindKey = rebindKey;
            }
        });
        screenTimeout.updateScreenTimeoutWakeLock(currentApplicationContext());
    }

    private void publishLyricRenderBinding(
            WordLyricModel model, OfficialLyricDataset dataset, long loadEpoch) {
        mainHandler.post(() -> {
            if (model == null || currentWordLyricModel != model
                    || !systemUiLyricPublicationGate.canCommit(loadEpoch)) return;
            if (lyricRenderTransactions.currentModel() == model
                    && dataset.equals(lyricRenderTransactions.currentDataset())) return;
            // Model, dataset expectation and mutable Canvas caches commit between UI traversals.
            lyricRenderTransactions.publish(model, dataset);
            traceLyricModelState("published", model);
            int generation = beginBrightLyricGeometryCommit(model);
            resetCommittedLyricRendererState();
            lyricModelReplacementInProgress = false;
            preparePublishedLyricModelGeometry(model, generation);
        });
    }

    private void resetCommittedLyricRendererState() {
        resetAdaptiveLineTimedRecyclerFollow();
        clearSeedlingActiveLyricHint();
        clearActiveRendererTargets();
        activeRendererWordLine = null;
        activeLyricLine = "";
        activeLyricLineTimeMs = -1L;
        officialLyricTextRenderer.clearGlowCache();
        officialLyricTextRenderer.cancelModelSwitchReveal();
    }

    private void clearCommittedLyricRenderModel() {
        mainHandler.post(() -> {
            if (currentWordLyricModel != null) return;
            lyricRenderTransactions.publish(null, null);
            cancelBrightLyricGeometryCommit();
            resetCommittedLyricRendererState();
            invalidateRememberedLyricViews();
        });
    }

    private void traceLyricModelState(String action, WordLyricModel model) {
        StructuredBridgeLog.debugTransition(
                BridgeDebugArea.RENDERER,
                BridgeEvents.LYRIC_MODEL_STATE,
                () -> "action=" + action
                        + ", elapsedMs=" + SystemClock.elapsedRealtime()
                        + ", modelId=" + (model == null
                                ? "none" : Integer.toHexString(System.identityHashCode(model)))
                        + ", lines=" + (model == null ? 0 : model.lines.size())
                        + ", translations=" + (model == null ? 0 : model.translationCount())
                        + ", geometryGeneration=" + brightLyricGeometryCommitGeneration
                        + ", track=" + SensitiveFieldRedactor.trackHash(
                                currentLyricObservedTrackKey));
    }

    private void beginOfficialLyricTrackHandoff(String reason) {
        beginOfficialLyricTrackHandoff(reason, true);
    }

    private void beginOfficialLyricTrackHandoff(String reason, boolean armRowRebind) {
        long now = SystemClock.elapsedRealtime();
        if (!shouldStartOfficialLyricTrackHandoff(now)) {
            pendingCustomLyricTakeoverFade = false;
            return;
        }
        if (armRowRebind) {
            lyricTrackRowRebindEligibleUntilElapsedMs =
                    now + SYSTEMUI_LYRIC_ROW_REBIND_WINDOW_MS;
        }
        officialLyricHandoffStartedAtElapsedMs = now;
        officialLyricHandoffReleaseRetryGeneration = -1;
        officialLyricDrawSuppressedUntilElapsedMs =
                now + SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS;
        pendingCustomLyricTakeoverFade = true;
        int generation = ++officialLyricHandoffGeneration;
        StructuredBridgeLog.debugTransition(
                BridgeDebugArea.RENDERER,
                BridgeEvents.LYRIC_HANDOFF,
                () -> "action=begin, generation=" + generation
                        + ", reason=" + reason
                        + ", elapsedMs=" + now
                        + ", model=" + (currentWordLyricModel != null)
                        + ", limitMs=" + SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS);
        mainHandler.post(() -> {
            suppressRememberedLyricsRecyclerViews();
            invalidateRememberedLyricViews();
        });
        mainHandler.postDelayed(() -> {
            if (generation != officialLyricHandoffGeneration) {
                return;
            }
            officialLyricDrawSuppressedUntilElapsedMs = 0L;
            officialLyricHandoffStartedAtElapsedMs = 0L;
            officialLyricHandoffReleaseRetryGeneration = -1;
            pendingCustomLyricTakeoverFade = false;
            StructuredBridgeLog.debugTransition(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.LYRIC_HANDOFF,
                    () -> "action=timeout, generation=" + generation
                            + ", elapsedMs=" + SystemClock.elapsedRealtime()
                            + ", model=" + (currentWordLyricModel != null));
            restoreSuppressedLyricsRecyclerViews(true);
            invalidateRememberedLyricViews();
        }, SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS);
        scheduleLyricsRecyclerVisibilityWatchdog();
    }

    private void finishOfficialLyricTrackHandoff() {
        if (officialLyricDrawSuppressedUntilElapsedMs <= 0L
                && !pendingCustomLyricTakeoverFade) {
            return;
        }
        int finishedGeneration = officialLyricHandoffGeneration;
        long startedAt = officialLyricHandoffStartedAtElapsedMs;
        officialLyricDrawSuppressedUntilElapsedMs = 0L;
        officialLyricHandoffStartedAtElapsedMs = 0L;
        officialLyricHandoffReleaseRetryGeneration = -1;
        pendingCustomLyricTakeoverFade = false;
        int generation = ++officialLyricHandoffGeneration;
        StructuredBridgeLog.debugTransition(
                BridgeDebugArea.RENDERER,
                BridgeEvents.LYRIC_HANDOFF,
                () -> "action=finish, generation=" + finishedGeneration
                        + ", elapsedMs=" + SystemClock.elapsedRealtime()
                        + ", heldMs=" + (startedAt > 0L
                                ? SystemClock.elapsedRealtime() - startedAt : -1L)
                        + ", model=" + (currentWordLyricModel != null));
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

    private void finishOfficialLyricTrackHandoffAfterStableCustomFrame(TextView textView) {
        if (officialLyricDrawSuppressedUntilElapsedMs <= 0L) {
            return;
        }
        if (delayOfficialLyricTrackHandoffReleaseIfNeeded()) {
            return;
        }
        View recycler = textView == null ? null : findContainingLyricsRecyclerView(textView);
        if (recycler == null
                || !recycler.isAttachedToWindow()
                || !hasBoundLyricsRecyclerChildren(recycler)
                || !isLyricSurfaceCustomFrameReady(recycler, currentWordLyricModel)) {
            scheduleOfficialLyricHandoffReleaseRetry(96L);
            return;
        }
        finishOfficialLyricTrackHandoff();
    }

    private boolean delayOfficialLyricTrackHandoffReleaseIfNeeded() {
        long startedAt = officialLyricHandoffStartedAtElapsedMs;
        if (startedAt <= 0L) {
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        long releaseAt = startedAt + SYSTEMUI_LYRIC_HANDOFF_MIN_MASK_MS;
        if (now >= releaseAt) {
            return false;
        }
        scheduleOfficialLyricHandoffReleaseRetry(releaseAt - now + ACTIVE_LYRIC_FRAME_DELAY_MS);
        return true;
    }

    private void scheduleOfficialLyricHandoffReleaseRetry(long delayMs) {
        int generation = officialLyricHandoffGeneration;
        if (officialLyricHandoffReleaseRetryGeneration == generation) {
            return;
        }
        officialLyricHandoffReleaseRetryGeneration = generation;
        mainHandler.postDelayed(() -> {
            if (generation != officialLyricHandoffGeneration
                    || officialLyricDrawSuppressedUntilElapsedMs <= 0L) {
                return;
            }
            officialLyricHandoffReleaseRetryGeneration = -1;
            invalidateRememberedLyricViews();
        }, Math.max(32L, Math.min(delayMs, 260L)));
    }

    private void scheduleLyricsRecyclerVisibilityWatchdog() {
        scheduleLyricsRecyclerVisibilityWatchdog(
                SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS
                        + SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS
                        + 120L);
    }

    private void scheduleLyricsRecyclerVisibilityWatchdog(long delayMs) {
        mainHandler.postDelayed(() -> {
            long now = SystemClock.elapsedRealtime();
            long holdUntil = currentLyricsRecyclerVisibilityHoldUntil(now);
            if (holdUntil > now) {
                scheduleLyricsRecyclerVisibilityWatchdog(
                        holdUntil - now + ACTIVE_LYRIC_FRAME_DELAY_MS);
                return;
            }
            if (hasSuppressedLyricsRecyclerAlphas()) {
                restoreSuppressedLyricsRecyclerViews(true);
                invalidateRememberedLyricViews();
                pendingCustomLyricTakeoverFade = false;
                if (isLyricLayoutDiagnosticsEnabled()) {
                    info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "Visibility watchdog restored module-suppressed lyric RecyclerView alpha");
                }
            }
        }, Math.max(32L, delayMs));
    }

    private long currentLyricsRecyclerVisibilityHoldUntil(long now) {
        long holdUntil = Math.max(
                officialLyricDrawSuppressedUntilElapsedMs,
                lyricRecyclerFadeInUntilElapsedMs);
        return holdUntil > now ? holdUntil : 0L;
    }

    private boolean hasSuppressedLyricsRecyclerAlphas() {
        synchronized (suppressedLyricsRecyclerAlphasLock) {
            return !suppressedLyricsRecyclerAlphas.isEmpty();
        }
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
        officialLyricHandoffStartedAtElapsedMs = 0L;
        officialLyricHandoffReleaseRetryGeneration = -1;
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
            invalidateLyricsRecyclerDescendants(recycler);
            recycler.postInvalidateOnAnimation();
        }
    }

    private void restoreSuppressedLyricsRecyclerViews(boolean animate) {
        synchronized (suppressedLyricsRecyclerAlphasLock) {
            suppressedLyricsRecyclerAlphas.clear();
        }
        for (View recycler : snapshotLyricsRecyclerViews()) {
            invalidateLyricsRecyclerDescendants(recycler);
            recycler.postInvalidateOnAnimation();
        }
    }

    private void fadeInLateCustomLyricTakeover(TextView textView) {
        if (!pendingCustomLyricTakeoverFade
                || textView == null) {
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
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Fading late custom lyric takeover");
            animateLyricsRecyclerFadeIn(recycler, SYSTEMUI_LYRIC_VISIBLE_ALPHA);
        });
    }

    private void animateLyricsRecyclerFadeIn(View recycler, float targetAlpha) {
        if (recycler == null) {
            return;
        }
        lyricRecyclerFadeGeneration++;
        lyricRecyclerFadeInUntilElapsedMs = 0L;
        invalidateLyricsRecyclerDescendants(recycler);
        recycler.postInvalidateOnAnimation();
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

    private boolean shouldStartOfficialLyricTrackHandoff(long now) {
        if (hasVisibleLyricsSurfaceForTrackHandoff(now)
                || now < officialLyricDrawSuppressedUntilElapsedMs
                || now <= lyricRecyclerFadeInUntilElapsedMs) {
            return true;
        }
        if (!systemUiLyricModeKeepAwakeActive) {
            return false;
        }
        return currentWordLyricModel != null || hasAttachedLyricsRecyclerView();
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

    private static String buildWordLyricModelSignature(
            LyricInfoContract.Payload payload,
            String rawLyric,
            String translationLyric) {
        if (payload == null) {
            return "";
        }
        return firstNonEmpty(payload.trackKey, payload.songId)
                + '|'
                + contentSignature(payload.lyric)
                + '|'
                + contentSignature(rawLyric)
                + '|'
                + contentSignature(translationLyric);
    }

    private static String contentSignature(String value) {
        if (value == null) {
            return "0:0";
        }
        return value.length() + ":" + Integer.toHexString(value.hashCode());
    }

    private long estimatePlaybackPositionMillis() {
        LyricInfoContract.Payload snapshot = currentLyricProviderPayload;
        if (snapshot != null && snapshot.snapshotPositionMillis >= 0L) {
            return snapshot.snapshotPositionMillis;
        }
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
                : model.lineAtAdapterIndex(lastLyricsRecyclerIndex);
        if (hintedLine != null) {
            return hintedLine.timeMillis;
        }
        return -1L;
    }

    private void maybeLogOfficialFrameDecision(
            String source,
            WordLyricModel model,
            TextView view,
            String normalizedText,
            int adapterPosition,
            WordLine indexedLine,
            WordLine officialLine,
            WordLine activeLine,
            WordLine selectedLine,
            WordLine cachedLine,
            long position,
            String matchReason,
            boolean duplicateText) {
        if (!isLyricVerboseDiagnosticsEnabled() || model == null) {
            return;
        }
        WordLine slotLine = model.lineAt(adapterPosition);
        int selectedIndex = model.indexOfLine(selectedLine);
        int slotFirstIndex = model.indexOfLine(slotLine);
        int officialIndex = model.indexOfLine(officialLine);
        int activeIndex = model.indexOfLine(activeLine);
        boolean adapterMismatch =
                adapterPosition >= 0 && selectedIndex >= 0 && selectedIndex != adapterPosition;
        boolean slotMismatch = slotLine != null && selectedLine != null && slotLine != selectedLine;
        boolean officialMismatch =
                officialLine != null && selectedLine != null && officialLine != selectedLine;
        boolean duplicateRenderable = duplicateText
                || (selectedLine != null
                && model.hasDuplicateRenderableText(selectedLine.normalizedText));
        if (!adapterMismatch && !slotMismatch && !officialMismatch && !duplicateRenderable) {
            return;
        }

        String key = nullToEmpty(source)
                + "|" + adapterPosition
                + "|" + selectedIndex
                + "|" + activeIndex
                + "|" + objectId(slotLine)
                + "|" + objectId(officialLine)
                + "|" + objectId(selectedLine)
                + "|" + shortenForLog(normalizedText);
        long now = SystemClock.elapsedRealtime();
        if (key.equals(lastOfficialFrameDecisionLogKey)
                && now - lastOfficialFrameDecisionLogAt < 1_000L) {
            return;
        }
        if (!adapterMismatch
                && !slotMismatch
                && now - lastOfficialFrameDecisionLogAt < OFFICIAL_FRAME_DECISION_LOG_INTERVAL_MS) {
            return;
        }
        lastOfficialFrameDecisionLogKey = key;
        lastOfficialFrameDecisionLogAt = now;

        View itemView = findLyricsRecyclerItemView(view);
        View recycler = findContainingLyricsRecyclerView(view);
        LyricsRecyclerGeometry geometry = captureLyricsRecyclerGeometry(recycler, adapterPosition);
        info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "Official frame decision"
                + ", source=" + source
                + ", reason=" + matchReason
                + ", position=" + position
                + ", adapterPosition=" + adapterPosition
                + ", selectedIndex=" + selectedIndex
                + ", slotFirstIndex=" + slotFirstIndex
                + ", officialIndex=" + officialIndex
                + ", activeIndex=" + activeIndex
                + ", recyclerIndex=" + lastLyricsRecyclerIndex
                + ", settleIndex=" + lyricRecyclerSettleOfficialIndex
                + ", duplicateText=" + duplicateText
                + ", duplicateRenderable=" + duplicateRenderable
                + ", adapterMismatch=" + adapterMismatch
                + ", slotMismatch=" + slotMismatch
                + ", officialMismatch=" + officialMismatch
                + ", firstVisiblePosition=" + geometry.firstVisiblePosition
                + ", firstVisibleTop=" + geometry.firstVisibleTop
                + ", itemTop=" + (itemView == null ? -1 : itemView.getTop())
                + ", itemHeight=" + (itemView == null ? -1 : itemView.getHeight())
                + ", viewId=" + objectId(view)
                + ", text=" + shortenForLog(normalizedText)
                + ", slot=" + describeDecisionLine(model, slotLine)
                + ", indexed=" + describeDecisionLine(model, indexedLine)
                + ", official=" + describeDecisionLine(model, officialLine)
                + ", cached=" + describeDecisionLine(model, cachedLine)
                + ", selected=" + describeDecisionLine(model, selectedLine)
                + ", active=" + describeDecisionLine(model, activeLine)
                + ", window=" + describeModelWindow(model, adapterPosition, 3));
    }

    private void maybeLogWordLyricModelSlotIntegrity(
            WordLyricModel model,
            String reason) {
        if (!isLyricVerboseDiagnosticsEnabled() || model == null) {
            return;
        }
        IdentityHashMap<WordLine, Integer> firstSlots = new IdentityHashMap<>();
        int duplicateObjects = 0;
        StringBuilder duplicateSamples = new StringBuilder();
        for (int i = 0; i < model.lines.size(); i++) {
            WordLine line = model.lines.get(i);
            if (line == null) {
                continue;
            }
            Integer firstSlot = firstSlots.get(line);
            if (firstSlot == null) {
                firstSlots.put(line, i);
                continue;
            }
            duplicateObjects++;
            if (duplicateSamples.length() < 900) {
                if (duplicateSamples.length() > 0) {
                    duplicateSamples.append(" | ");
                }
                duplicateSamples.append(firstSlot)
                        .append("->")
                        .append(i)
                        .append("#")
                        .append(objectId(line))
                        .append(" ")
                        .append(shortenForLog(line.text));
            }
        }

        int officialAliasMismatches = 0;
        StringBuilder aliasSamples = new StringBuilder();
        for (int i = 0; i < model.officialLines.size(); i++) {
            WordLine officialLine = model.rawOfficialLineAt(i);
            if (officialLine == null) {
                continue;
            }
            int mappedIndex = model.indexOfLine(officialLine);
            if (mappedIndex == i) {
                continue;
            }
            officialAliasMismatches++;
            if (aliasSamples.length() < 900) {
                if (aliasSamples.length() > 0) {
                    aliasSamples.append(" | ");
                }
                aliasSamples.append(i)
                        .append("->")
                        .append(mappedIndex)
                        .append("#")
                        .append(objectId(officialLine))
                        .append(" ")
                        .append(shortenForLog(officialLine.text));
            }
        }
        int focusIndex = model.lines.size() > 108 ? 105 : Math.max(0, model.lines.size() - 1);
        info(BridgeDebugArea.LYRIC, BridgeEvents.LYRIC_PARSED, "Word lyric model slot integrity"
                + ", reason=" + reason
                + ", parser=" + model.parserName
                + ", lines=" + model.lines.size()
                + ", officialLines=" + model.officialLines.size()
                + ", duplicateObjects=" + duplicateObjects
                + ", duplicateSamples=" + duplicateSamples
                + ", officialAliasMismatches=" + officialAliasMismatches
                + ", aliasSamples=" + aliasSamples
                + ", window=" + describeModelWindow(model, focusIndex, 5));
    }

    static String objectId(Object value) {
        return value == null ? "null" : Integer.toHexString(System.identityHashCode(value));
    }

    private void maybeLogTextViewSpan(long position, String line, Object view) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastTextViewSpanLogAt < 1_000L) {
            return;
        }
        lastTextViewSpanLogAt = now;
        info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Observed active lyric TextView at position=" + position
                + ", line=" + line
                + ", view=" + (view == null ? "null" : view.getClass().getName()));
    }

    private void maybeLogTextViewDraw(DrawFrame frame, TextView view) {
        if (frame != null && view != null
                && StructuredBridgeLog.isAreaEnabled(BridgeDebugArea.RENDERER)
                && lastCustomDrawLogGeometryGeneration != brightLyricGeometryCommitGeneration) {
            int geometryGeneration = brightLyricGeometryCommitGeneration;
            lastCustomDrawLogGeometryGeneration = geometryGeneration;
            StructuredBridgeLog.debugTransition(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.LYRIC_CUSTOM_FRAME,
                    () -> "elapsedMs=" + SystemClock.elapsedRealtime()
                            + ", geometryGeneration=" + geometryGeneration
                            + ", modelId=" + Integer.toHexString(
                                    System.identityHashCode(frame.model))
                            + ", viewId=" + Integer.toHexString(System.identityHashCode(view))
                            + ", lineIndex=" + frame.lineIndex
                            + ", active=" + frame.active
                            + ", shown=" + view.isShown()
                            + ", viewAlpha=" + view.getAlpha());
        }
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastTextViewDrawLogAt < 3_000L) {
            return;
        }
        lastTextViewDrawLogAt = now;
        WordLine wordLine = frame == null ? null : frame.line;
        info(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.DETAIL, "Custom-drew official lyric TextView at position=" + (frame == null ? -1L : frame.position)
                + ", playing=" + lastPlaybackIsPlaying
                + ", index=" + (frame == null ? -1 : frame.lineIndex)
                + ", active=" + (frame != null && frame.active)
                + ", focused=" + (frame != null && frame.focused)
                + ", activeIndex=" + (frame == null ? -1 : frame.activeIndex)
                + ", scaleActiveIndex=" + (frame == null ? -1 : frame.scaleActiveIndex)
                + ", line=" + (wordLine == null ? "" : wordLine.text)
                + ", timing=" + (wordLine == null ? "" : wordLine.timingMode)
                + ", hasTranslation=" + (wordLine != null && !TextUtils.isEmpty(wordLine.translation))
                + ", view=" + describeViewForLog(view));
        maybeLogOfficialLyricGeometry(frame, view, "draw-main");
    }

    private void maybeLogOfficialRendererFallback(
            String reason,
            TextView view,
            WordLyricModel model,
            String detail) {
        if (!StructuredBridgeLog.isAreaEnabled(BridgeDebugArea.RENDERER) || view == null
                || !isInLyricsRecyclerView(view)) {
            return;
        }
        String safeReason = TextUtils.isEmpty(reason) ? "unknown" : reason;
        long now = SystemClock.elapsedRealtime();
        Long last = officialRendererFallbackLogAt.get(safeReason);
        if (last != null && now - last < 3_000L) return;
        officialRendererFallbackLogAt.put(safeReason, now);
        StructuredBridgeLog.debugTransition(
                BridgeDebugArea.RENDERER,
                BridgeEvents.LYRIC_RENDER_FALLBACK,
                () -> "reason=" + safeReason
                        + ", elapsedMs=" + now
                        + ", modelId=" + (model == null
                                ? "none" : Integer.toHexString(System.identityHashCode(model)))
                        + ", viewId=" + Integer.toHexString(System.identityHashCode(view))
                        + ", handoffRemainingMs=" + Math.max(
                                0L, officialLyricDrawSuppressedUntilElapsedMs - now)
                        + ", fadeRemainingMs=" + Math.max(
                                0L, lyricRecyclerFadeInUntilElapsedMs - now));
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        info(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED, "Official lyric renderer fallback"
                + " | reason=" + safeReason
                + ", alignment=" + lyricUiConfig.alignment
                + ", model=" + (model == null ? "null" : "ready")
                + ", source=systemui"
                + ", detail=" + shortenForLog(detail)
                + ", view=" + describeViewForLog(view));
    }

    private void maybeLogOfficialLyricPayload(
            LyricInfoContract.Payload payload,
            boolean normalizedForOfficialList,
            MediaMetadata metadata) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastOfficialLyricPayloadLogAt < 1_500L) {
            return;
        }
        lastOfficialLyricPayloadLogAt = now;
        info(BridgeDebugArea.LYRIC, BridgeEvents.NATIVE_LYRIC_RECEIVED, "Official lyric payload, provider=" + (payload == null ? "" : payload.provider)
                + ", package=" + currentLyricProviderPackage
                + ", normalizedForOfficialList=" + normalizedForOfficialList
                + ", lyricChars=" + (payload == null || payload.lyric == null
                ? 0
                : payload.lyric.length())
                + ", rawChars=" + (payload == null || payload.rawLyric == null
                ? 0
                : payload.rawLyric.length())
                + ", translationChars=" + (payload == null || payload.translationLyric == null
                ? 0
                : payload.translationLyric.length())
                + ", artwork=" + describeMetadataArtwork(metadata));
    }

    private void maybeLogOfficialLyricGeometry(
            DrawFrame frame, TextView view, String role) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastLyricLayoutDiagnosticsLogAt < 1_000L) {
            return;
        }
        lastLyricLayoutDiagnosticsLogAt = now;
        View itemView = findLyricsRecyclerItemView(view);
        View recycler = findContainingLyricsRecyclerView(view);
        int adapterPosition = findLyricsRecyclerAdapterPosition(view);
        LyricsRecyclerGeometry geometry = captureLyricsRecyclerGeometry(
                recycler,
                adapterPosition);
        info(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED, "Official lyric geometry, role=" + role
                + ", adapterPosition=" + adapterPosition
                + ", drawIndex=" + (frame == null ? -1 : frame.lineIndex)
                + ", activeIndex=" + (frame == null ? -1 : frame.activeIndex)
                + ", scaleActiveIndex=" + (frame == null ? -1 : frame.scaleActiveIndex)
                + ", itemHeight=" + (itemView == null ? -1 : itemView.getHeight())
                + ", textMeasuredHeight=" + (view == null ? -1 : view.getMeasuredHeight())
                + ", bottomMargin=" + bottomMarginOf(itemView == null ? view : itemView)
                + ", firstVisiblePosition=" + geometry.firstVisiblePosition
                + ", firstVisibleTop=" + geometry.firstVisibleTop
                + ", targetCenter=" + geometry.targetCenter
                + ", fallbackSlotDp=" + WordLyricRenderConstants.LYRIC_SLOT_HEIGHT_DP
                + ", spacingDp="
                + LyricUiLayoutPolicy.lineSpacingTenthsDp(runtimeLyricUiConfig) / 10f
                + ", wrappedSpacingDp="
                + LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(
                runtimeLyricUiConfig) / 10f);
    }

    private void maybeLogLyricsRecyclerSetCurrentGeometry(
            int targetIndex,
            LyricsRecyclerGeometry before,
            LyricsRecyclerGeometry after) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastLyricLayoutDiagnosticsLogAt < 750L) {
            return;
        }
        lastLyricLayoutDiagnosticsLogAt = now;
        int beforeCenter = before == null ? Integer.MIN_VALUE : before.targetCenter;
        int afterCenter = after == null ? Integer.MIN_VALUE : after.targetCenter;
        info(BridgeDebugArea.AOD, BridgeEvents.SET_CURRENT_LYRIC_GEOMETRY, "LyricsRecyclerView setCurrentLyric geometry, target=" + targetIndex
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

    private void scheduleLyricsRecyclerOwnershipSnapshots(
            View recycler,
            String event,
            int targetIndex) {
        if (recycler == null || !isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        maybeLogLyricsRecyclerOwnershipSnapshot(
                recycler,
                nullToEmpty(event) + "-immediate",
                targetIndex);
        recycler.postOnAnimation(() -> maybeLogLyricsRecyclerOwnershipSnapshot(
                recycler,
                nullToEmpty(event) + "-frame",
                targetIndex));
        recycler.postDelayed(() -> maybeLogLyricsRecyclerOwnershipSnapshot(
                recycler,
                nullToEmpty(event) + "-64ms",
                targetIndex), 64L);
    }

    private void maybeLogLyricsRecyclerOwnershipSnapshot(
            View recycler,
            String event,
            int targetIndex) {
        if (recycler == null || !isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        LyricsRecyclerGeometry geometry = captureLyricsRecyclerGeometry(recycler, targetIndex);
        Object scrollState = invokeNoArgByName(recycler, "getScrollState");
        boolean modulePositionOwner = shouldModulePositionLyricsRecycler();
        info(BridgeDebugArea.AOD, BridgeEvents.AOD_TRANSITION, "LyricsRecyclerView ownership snapshot"
                + " | event=" + shortenForLog(event)
                + ", rvId=" + objectId(recycler)
                + ", owner=" + (modulePositionOwner ? "module-aod-fallback" : "systemui")
                + ", officialIndex=" + readLyricsRecyclerCurrentIndex(recycler)
                + ", targetIndex=" + targetIndex
                + ", children=" + geometry.childCount
                + ", first=" + geometry.firstVisiblePosition
                + ", last=" + geometry.lastVisiblePosition
                + ", firstTop=" + geometry.firstVisibleTop
                + ", targetCenter=" + geometry.targetCenter
                + ", officialOffset=" + readIntField(recycler, "B", Integer.MIN_VALUE)
                + ", scrollState=" + String.valueOf(scrollState)
                + ", attached=" + recycler.isAttachedToWindow()
                + ", shown=" + recycler.isShown()
                + ", visibility=" + recycler.getVisibility()
                + ", size=" + recycler.getWidth() + "x" + recycler.getHeight()
                + ", alpha=" + recycler.getAlpha()
                + ", aod=" + isAodLowFrameRateLyricMode()
                + ", interactive=" + screenTimeout.isScreenInteractiveForWakeLock());
    }

    private void maybeLogRecyclerScrollStabilize(View recycler, String reason) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastRecyclerScrollStabilizeLogAt < 1_500L) {
            return;
        }
        lastRecyclerScrollStabilizeLogAt = now;
        info(BridgeDebugArea.AOD, BridgeEvents.RECYCLER_SCROLL_STABILIZED, "Stabilized LyricsRecyclerView scroll, reason=" + reason
                + ", index=" + lastLyricsRecyclerIndex
                + ", recycler=" + describeViewForLog(recycler));
    }

    private void maybeLogForcedLyricsRecyclerAlign(
            View recycler,
            int targetIndex,
            String reason) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastLyricsRecyclerForceAlignLogAt < 1_500L) {
            return;
        }
        lastLyricsRecyclerForceAlignLogAt = now;
        info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "Force-aligned LyricsRecyclerView, reason=" + reason
                + ", targetIndex=" + targetIndex
                + ", recycler=" + describeViewForLog(recycler));
    }

    private void maybeLogSeedlingPlaybackState(int state, long storedPosition, long computedPosition, float speed) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED
                && state == lastLoggedSystemUiPlaybackState
                && now - lastSeedlingPlaybackStateLogAt < 5_000L) {
            return;
        }
        lastLoggedSystemUiPlaybackState = state;
        lastSeedlingPlaybackStateLogAt = now;
        info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, "Seedling playback state=" + state
                + ", playing=" + lastPlaybackIsPlaying
                + ", storedPosition=" + storedPosition
                + ", computedPosition=" + computedPosition
                + ", speed=" + speed);
    }

    private void maybeLogIgnoredStalePlaybackPositionAfterTrackReset(
            long storedPosition,
            long computedPosition,
            long previousPosition) {
        if (!isLyricLayoutDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastStalePlaybackPositionIgnoredLogAt < 1_500L) {
            return;
        }
        lastStalePlaybackPositionIgnoredLogAt = now;
        info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, "Ignored stale SystemUI playback position after track reset, previousPosition="
                + previousPosition
                + ", storedPosition=" + storedPosition
                + ", computedPosition=" + computedPosition);
    }

    private void maybeLogActiveRefresh(
            long position, String line, int candidates, int attached, int visible, int updated) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastActiveRefreshLogAt < 3_000L) {
            return;
        }
        lastActiveRefreshLogAt = now;
        info(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED, "Refreshed active lyric renderer at position=" + position
                + ", candidates=" + candidates
                + ", attached=" + attached
                + ", visible=" + visible
                + ", updated=" + updated
                + ", line=" + line);
    }

    private void maybeLogRecyclerIndex(int index) {
        if (!isLyricVerboseDiagnosticsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!LYRIC_VERBOSE_DIAGNOSTICS_ENABLED && now - lastRecyclerLogAt < 1_000L) {
            return;
        }
        lastRecyclerLogAt = now;
        info(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, "LyricsRecyclerView current index=" + index);
    }

    static boolean isLyricLayoutDiagnosticsEnabled() {
        return StructuredBridgeLog.isMasterEnabled()
                || LYRIC_VERBOSE_DIAGNOSTICS_ENABLED
                || Log.isLoggable(TAG, Log.DEBUG);
    }

    static boolean isLyricVerboseDiagnosticsEnabled() {
        return LYRIC_VERBOSE_DIAGNOSTICS_ENABLED || Log.isLoggable(TAG, Log.VERBOSE);
    }

    private static boolean isLyricParseTraceEnabled() {
        return LYRIC_PARSE_TRACE_ENABLED || Log.isLoggable(LYRIC_PARSE_TRACE_TAG, Log.VERBOSE);
    }

    private static LyricsRecyclerGeometry captureLyricsRecyclerGeometry(
            View recycler, int targetIndex) {
        if (!(recycler instanceof ViewGroup)) {
            return LyricsRecyclerGeometry.EMPTY;
        }
        ViewGroup group = (ViewGroup) recycler;
        int childCount = group.getChildCount();
        int firstVisiblePosition = Integer.MAX_VALUE;
        int lastVisiblePosition = -1;
        int firstVisibleTop = 0;
        int targetCenter = Integer.MIN_VALUE;
        for (int i = 0; i < childCount; i++) {
            View child = group.getChildAt(i);
            int position = readRecyclerChildPosition(recycler, child);
            if (position >= 0 && position < firstVisiblePosition) {
                firstVisiblePosition = position;
                firstVisibleTop = child.getTop();
            }
            if (position > lastVisiblePosition) {
                lastVisiblePosition = position;
            }
            if (position == targetIndex) {
                targetCenter = child.getTop() + child.getHeight() / 2;
            }
        }
        if (firstVisiblePosition == Integer.MAX_VALUE) {
            firstVisiblePosition = -1;
        }
        return new LyricsRecyclerGeometry(
                firstVisiblePosition,
                lastVisiblePosition,
                firstVisibleTop,
                targetCenter,
                childCount);
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

    private static String sanitizeForOplusLyric(String rawLyric) {
        return OplusLyricNormalizer.normalizeForOfficialList(rawLyric);
    }

    private static String getText(MediaMetadata metadata, String key) {
        CharSequence value = metadata.getText(key);
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static String normalizeLooseMetadataText(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char character = trimmed.charAt(i);
            if (Character.isLetterOrDigit(character) || character > 0x7F) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static void traceLyricParse(String message) {
        if (!isLyricParseTraceEnabled()) {
            return;
        }
        String value = LyricModelTraceSupport.sanitizeTraceMessage(message);
        for (String chunk : LyricLogFormatter.chunks(value, LYRIC_PARSE_TRACE_CHUNK_SIZE)) {
            Log.i(
                    LYRIC_PARSE_TRACE_TAG,
                    LyricLogFormatter.format(
                            logProcessName,
                            LyricLogFormatter.Area.PARSER,
                            "trace",
                            chunk));
        }
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
        String normalized = WordLyricRenderSupport.normalizeLine(text);
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

    private void rememberSystemUiLyricLoadContext(
            Object owner,
            Object keyArg,
            MediaMetadata metadata,
            String packageName,
            String title,
            String artist,
            String trackKey) {
        if (owner == null
                || !(keyArg instanceof String)
                || metadata == null) {
            return;
        }
        Method refreshMethod = resolveSystemUiMetadataRefreshMethod(owner);
        if (refreshMethod == null) {
            return;
        }
        latestSystemUiLyricLoadContext = new SystemUiLyricLoadContext(
                owner,
                refreshMethod,
                (String) keyArg,
                metadata,
                packageName,
                title,
                artist,
                trackKey,
                SystemClock.elapsedRealtime());
    }

    private Method resolveSystemUiMetadataRefreshMethod(Object owner) {
        Method cached = systemUiMetadataRefreshMethod;
        if (cached != null && cached.getDeclaringClass().isInstance(owner)) {
            return cached;
        }
        Class<?> ownerClass = owner.getClass();
        Method candidate = null;
        for (Method method : ownerClass.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != void.class
                    || parameterTypes.length != 2
                    || parameterTypes[0] != String.class
                    || parameterTypes[1] != MediaMetadata.class) {
                continue;
            }
            if ("onMetaDataChanged".equals(method.getName())) {
                candidate = method;
                break;
            }
            if (candidate == null) {
                candidate = method;
            }
        }
        if (candidate == null) {
            candidate = SystemUiDexKitAdapter.findMetadataSchedulerEntry(ownerClass);
        }
        if (candidate == null) {
            if (!systemUiMetadataRefreshMethodUnavailableLogged) {
                systemUiMetadataRefreshMethodUnavailableLogged = true;
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "metadata-refresh-unavailable",
                        "No SystemUI metadata refresh method for lyric reload");
            }
            return null;
        }
        candidate.setAccessible(true);
        systemUiMetadataRefreshMethod = candidate;
        return candidate;
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static float sp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().scaledDensity;
    }

    void info(BridgeDebugArea area, String event, String message) {
        StructuredBridgeLog.info(area, event, message);
    }

    private void artworkProbe(String stage, Supplier<String> details) {
        StructuredBridgeLog.debug(
                BridgeDebugArea.MEDIA,
                BridgeEvents.ARTWORK_PROBE + '_' + nullToEmpty(stage),
                () -> "stage=" + nullToEmpty(stage) + " | "
                        + (details == null ? "" : nullToEmpty(details.get())));
    }

    private static boolean isLxPlayerPackage(String packageName) {
        return PlayerSystemUiPolicy.LX_MUSIC.equals(packageName)
                || PlayerSystemUiPolicy.LX_WALNUT.equals(packageName);
    }

    void infoAlways(BridgeDebugArea area, String event, String message) {
        StructuredBridgeLog.infoAlways(area, event, message);
    }

    private void settingsInfo(String message) {
        StructuredBridgeLog.infoAlways(
                BridgeDebugArea.BOOTSTRAP,
                BridgeEvents.SETTINGS_APPLIED,
                message);
    }

    private void translationButtonDebug(String message) {
        StructuredBridgeLog.debug(
                BridgeDebugArea.PLAYER_SPECIAL,
                BridgeEvents.DETAIL,
                () -> message);
    }

    private void warn(LyricLogFormatter.Area area, String event, String message) {
        StructuredBridgeLog.warn(
                mapLogArea(area),
                event == null || event.isEmpty() ? BridgeEvents.DETAIL : event,
                message);
    }

    void error(String message, Throwable throwable) {
        StructuredBridgeLog.error(message, throwable);
    }

    private void configureBridgeDiagnostics(BridgeDebugConfig nextConfig) {
        StructuredBridgeLog.setLogTagForcesDebug(Log.isLoggable(TAG, Log.DEBUG));
        StructuredBridgeLog.configure(
                nextConfig,
                logProcessName,
                this::writeLogcat,
                this::writeFrameworkLog);
        dynamicArtworkRuntime.onDiagnosticsChanged();
    }

    private void loadBridgeDebugSettings(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        applyBridgeDebugConfig(
                BridgeDebugConfig.load(appContext.getSharedPreferences(
                        BridgeDebugConfig.PREFS_NAME,
                        Context.MODE_PRIVATE)),
                "systemui-preferences");
    }

    private void handleBridgeDebugSettingsChanged(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        BridgeDebugConfig config = BridgeDebugConfig.fromIntent(
                intent,
                StructuredBridgeLog.config());
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        BridgeDebugConfig.save(
                appContext.getSharedPreferences(
                        BridgeDebugConfig.PREFS_NAME,
                        Context.MODE_PRIVATE),
                config);
        applyBridgeDebugConfig(config, "settings-broadcast");
        sendDebugSettingsApplyResult(intent, true, config, "applied");
    }

    private void applyBridgeDebugConfig(BridgeDebugConfig config, String source) {
        BridgeDebugConfig next = config == null ? BridgeDebugConfig.disabled() : config;
        BridgeDebugConfig previous = StructuredBridgeLog.config();
        boolean reconfigure = BridgeDebugApplyPolicy.shouldReconfigure(previous, next);
        boolean announce = BridgeDebugApplyPolicy.shouldAnnounce(
                previous, next, bridgeDebugConfigAnnounced, source);
        if (reconfigure || !bridgeDebugConfigAnnounced) {
            configureBridgeDiagnostics(next);
        } else {
            StructuredBridgeLog.setLogTagForcesDebug(Log.isLoggable(TAG, Log.DEBUG));
        }
        if (!announce) {
            return;
        }
        bridgeDebugConfigAnnounced = true;
        StructuredBridgeLog.infoAlways(
                BridgeDebugArea.BOOTSTRAP,
                BridgeEvents.DEBUG_CONFIG_APPLIED,
                "Applied bridge debug logging"
                        + " | source=" + nullToEmpty(source)
                        + ", revision=" + next.revision
                        + ", master=" + next.masterEnabled
                        + ", areas=" + next.enabledAreasLabel());
    }

    @SuppressWarnings("deprecation")
    private void sendDebugSettingsApplyResult(
            Intent intent,
            boolean applied,
            BridgeDebugConfig config,
            String reason) {
        if (intent == null) {
            return;
        }
        ResultReceiver receiver = intent.getParcelableExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER);
        if (receiver == null) {
            return;
        }
        Bundle result = new Bundle();
        result.putBoolean(LyricUiSettings.RESULT_APPLIED, applied);
        result.putLong(
                LyricUiSettings.RESULT_CONFIG_REVISION,
                intent.getLongExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, -1L));
        result.putString(LyricUiSettings.RESULT_PROCESS, logProcessName);
        result.putString(LyricUiSettings.RESULT_REASON, nullToEmpty(reason));
        if (config != null) {
            config.putStatus(result);
        }
        try {
            receiver.send(
                    applied
                            ? LyricUiSettings.RESULT_SETTINGS_APPLIED
                            : LyricUiSettings.RESULT_SETTINGS_REJECTED,
                    result);
        } catch (Throwable throwable) {
            warn(
                    LyricLogFormatter.Area.SETTINGS,
                    "debug-settings-ack-failed",
                    "Could not acknowledge debug settings"
                            + " | error=" + throwable.getClass().getSimpleName());
        }
    }

    private void writeLogcat(int level, String tag, String message, Throwable throwable) {
        if (throwable != null) {
            Log.println(level, tag, message);
            Log.e(tag, message, throwable);
        } else {
            Log.println(level, tag, message);
        }
    }

    private void writeFrameworkLog(int level, String tag, String message, Throwable throwable) {
        if (throwable != null) {
            log(level, tag, message, throwable);
        } else {
            log(level, tag, message);
        }
    }

    private static BridgeDebugArea mapLogArea(LyricLogFormatter.Area area) {
        if (area == null) {
            return BridgeDebugArea.MEDIA;
        }
        switch (area) {
            case LIFECYCLE:
            case HOOK:
            case SETTINGS:
                return BridgeDebugArea.BOOTSTRAP;
            case PLAYER:
            case TRANSACTION:
            case SYSTEM_UI:
            case SCREEN:
                return BridgeDebugArea.MEDIA;
            case PROVIDER:
            case PARSER:
                return BridgeDebugArea.LYRIC;
            case RENDER:
            case RECYCLER:
                return BridgeDebugArea.RENDERER;
            case AOD:
                return BridgeDebugArea.AOD;
            case TRANSLATION:
                return BridgeDebugArea.PLAYER_SPECIAL;
            default:
                return BridgeDebugArea.MEDIA;
        }
    }

}
