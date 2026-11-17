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

// Members of the entry class the renderer used as a nested class. A pure move (E-1) keeps the
// bodies unchanged; narrowing this to an explicit host is a separate change (plan §15.8.2).
import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;
import static io.github.andrealtb.lockscreenlyrics.LyricsRecyclerViews.*;
import static io.github.andrealtb.lockscreenlyrics.LyricLogDescriptions.*;

/**
 * Draws the official lyric rows (main line, translation, word progress, char lift, glow).
 * Moved verbatim out of {@code LockscreenLyricsModule} in E-1 (plan §15.3); still a
 * package-private class in the same package so its package-private collaborators stay visible.
 */
final class OfficialLyricTextRenderer {
    // The leading word-progress edge is intentionally broad. It should read as a soft
    // illuminated wash, rather than a narrow rectangular scanner moving across the text.
    private static final float ACTIVE_FEATHER_WIDTH_FACTOR = 0.58f;
    private static final float GLOW_FEATHER_WIDTH_FACTOR = 0.74f;
    private static final int SOFTWARE_GLOW_FEATHER_BANDS = 12;
    // Keep the configured bloom warm and expressive; it is intentionally allowed to hug
    // the glyph a little more than the ultra-diffuse experiment did.
    private static final float SETTLED_GLOW_RADIUS_FACTOR = 1.22f;
    private static final float SETTLED_GLOW_ALPHA_FACTOR = 0.88f;
    private static final float WRAPPED_LINE_BASE_GAP_DP = 1f;
    // Sub-pixel lift is invisible; treat it as landed so the extra animation
    // frames stop instead of running forever behind a settled row.
    private static final float CHAR_LIFT_FRAME_EPSILON = 0.01f;
    private static int visibleMainDrawLines(WordLine line) {
        return line == null ? 2 : line.visibleWrappedLineLimit();
    }
    private static final long FOCUSED_REVEAL_ANIMATION_MS = 260L;
    private static final long MAIN_LINE_WINDOW_ANIMATION_MS = 220L;
    private static final float MAIN_LINE_WINDOW_SLIDE_DP = 7f;
    private static final long TRANSLATION_TOGGLE_ANIMATION_MS = 320L;
    private static final float TRANSLATION_TOGGLE_SLIDE_DP = 3f;
    private static final long TRANSLATION_TRANSIENT_MISS_GRACE_MS = 2_000L;
    private static final float TRANSLATION_SCROLL_START_PROGRESS = 0.08f;
    private static final float TRANSLATION_SCROLL_END_PROGRESS = 0.82f;
    private static final long MODEL_SWITCH_REVEAL_ANIMATION_MS = 260L;
    private static final float MODEL_SWITCH_REVEAL_START_ALPHA = 0.76f;
    private final TextPaint inactivePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint playedPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint activePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint activeGlowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint activeFeatherPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint translationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint translationActivePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint translationFeatherPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint glowRasterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint glowBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowFeatherMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint.FontMetrics mainFontMetrics = new Paint.FontMetrics();
    private final Paint.FontMetrics translationFontMetrics = new Paint.FontMetrics();
    private final Paint.FontMetrics glowFontMetrics = new Paint.FontMetrics();
    private final Rect opticalTextBounds = new Rect();
    private final Matrix activeFeatherShaderMatrix = new Matrix();
    private final Matrix glowFeatherMaskShaderMatrix = new Matrix();
    private final LyricDrawLayoutEngine drawLayoutEngine =
            new LyricDrawLayoutEngine(
                    (text, start, end) -> inactivePaint.measureText(text, start, end));
    private final List<LyricDrawLine> drawLines = drawLayoutEngine.lines();
    private LinearGradient activeFeatherShader;
    private String activeFeatherPrimaryColor;
    // A continuous alpha mask keeps the cached glyph glow soft at the progress boundary.
    private final LinearGradient glowFeatherMaskShader = new LinearGradient(
            0f,
            0f,
            1f,
            0f,
            new int[]{
                    0xFFFFFFFF,
                    0xFAFFFFFF,
                    0xE1FFFFFF,
                    0xA0FFFFFF,
                    0x52FFFFFF,
                    0x15FFFFFF,
                    0x00FFFFFF
            },
            new float[]{0f, 0.13f, 0.32f, 0.55f, 0.76f, 0.92f, 1f},
            Shader.TileMode.CLAMP);
    private final PorterDuffXfermode glowFeatherMaskXfermode =
            new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final GlowSegmentCache[] glowSegmentCaches = {
            new GlowSegmentCache(),
            new GlowSegmentCache(),
            new GlowSegmentCache(),
            new GlowSegmentCache(),
            new GlowSegmentCache(),
            new GlowSegmentCache()
    };
    private long glowCacheUseCounter;
    private static final int GLYPH_RUN_NEIGHBOURS = 2;
    private static final float GLYPH_RUN_SAFE_EPSILON = 0.05f;
    private GraphemeLayoutCache lastGraphemeLayout;
    private int glyphRunStart = -1;
    private int glyphRunEnd;
    private int glyphRunContextStart;
    private int glyphRunContextEnd;
    private float glyphRunX;
    private final GraphemeLayoutCache[] graphemeLayoutCaches = {
            new GraphemeLayoutCache(),
            new GraphemeLayoutCache(),
            new GraphemeLayoutCache(),
            new GraphemeLayoutCache()
    };
    private long graphemeCacheUseCounter;
    private final CharLiftState charLift = new CharLiftState();
    /**
     * Canvas y of the translation row's glyph top for the group being drawn,
     * or 0 when the row has no translation. Sunk main text has to stay above
     * it: the translation does not sink with the main line.
     */
    private float charLiftFloorY;
    /**
     * Sticky after a lifted draw throws. A draw error reaching the
     * coordinator blacklists the binding and falls back to native rendering
     * for good, so this effect gives itself up instead.
     */
    private boolean charLiftUnavailable;
    private BlurMaskFilter glowMaskFilter;
    private int glowMaskRadiusKey = -1;
    private float translationAnimationStartAmount = 1f;
    private float translationAnimationTargetAmount = 1f;
    private float translationLayoutAmount = 1f;
    private long translationAnimationStartedAtMs = -1L;
    private volatile boolean scrollScaleEnabled;
    private volatile boolean inactiveBlurEnabled;
    private volatile boolean lineTimedProgressEnabled =
            LyricUiSettings.DEFAULT_LINE_TIMED_PROGRESS_ENABLED;
    private volatile boolean translationProgressEnabled =
            LyricUiSettings.DEFAULT_TRANSLATION_PROGRESS_ENABLED;
    private volatile boolean charLiftEnabled =
            LyricUiSettings.DEFAULT_CHAR_LIFT_ENABLED;
    private volatile int charLiftStrengthPercent =
            LyricUiSettings.DEFAULT_CHAR_LIFT_STRENGTH_PERCENT;
    private volatile LyricUiConfig uiConfig = LyricUiConfig.defaults();
    private volatile LyricUiPalette palette = LyricUiPalette.from(uiConfig);
    private volatile boolean aodLowFrameRateMode;
    private long lastOverflowStartLogAt;

    private WordLine lastLine;
    private long lineChangeElapsedMs = SystemClock.elapsedRealtime();
    private long lastRenderableTranslationLineTimeMillis = Long.MIN_VALUE;
    private String lastRenderableTranslationNormalizedText = "";
    private String lastRenderableTranslationText = "";
    private long lastRenderableTranslationAtElapsedMs = -1L;
    private final MainLineWindow mainLineWindow = new MainLineWindow();
    private Typeface configuredTypefaceSource;
    private Typeface configuredTypeface;
    private int configuredTypefaceWeight = Integer.MIN_VALUE;
    private boolean configuredTypefaceCached;
    // Official active-row typeface, learned from the vendor row style helper that swaps it per
    // row state (ColorOS 17: 700 active / 600 inactive). Some ColorOS 16 plugin builds have
    // the same helper with equal weights. null keeps each row's own TextView typeface.
    private volatile Typeface officialBaseTypeface;
    // Part of the row slot-height cache key: rows measured before the typeface was learned,
    // or with an earlier one, must be measured again.
    private volatile int officialBaseTypefaceGeneration;
    private final LyricGroupDrawContext lyricGroupDrawContext =
            new LyricGroupDrawContext();
    private long modelSwitchRevealStartedAtMs = -1L;
    private long aodLineFillTransitionStartedAtMs = -1L;
    private float aodLineFillTransitionStartAmount = 0f;
    private float aodLineFillTransitionTargetAmount = 0f;
    private float drawBaseFade = 1f;
    private float rowVisualFade = 1f;
    private float rowVisualBlurRadius = 0f;
    private boolean rowVisualAnimationRunning;
    private volatile boolean forceOfficialSlotHeight;

    synchronized void armModelSwitchReveal() {
        modelSwitchRevealStartedAtMs = SystemClock.elapsedRealtime();
    }

    synchronized void cancelModelSwitchReveal() {
        modelSwitchRevealStartedAtMs = -1L;
    }

    synchronized void setTranslationEnabled(boolean enabled) {
        long now = SystemClock.elapsedRealtime();
        float currentAmount = resolveTranslationAmount(now);
        translationAnimationStartAmount = currentAmount;
        translationLayoutAmount = currentAmount;
        translationAnimationTargetAmount = enabled ? 1f : 0f;
        translationAnimationStartedAtMs =
                Math.abs(currentAmount - translationAnimationTargetAmount) < 0.001f
                        ? -1L
                        : now;
    }

    synchronized void setTranslationEnabledImmediately(boolean enabled) {
        translationAnimationStartAmount = enabled ? 1f : 0f;
        translationAnimationTargetAmount = translationAnimationStartAmount;
        translationLayoutAmount = translationAnimationStartAmount;
        translationAnimationStartedAtMs = -1L;
    }

    void setStyleOptions(
            boolean scrollScaleEnabled,
            boolean inactiveBlurEnabled,
            boolean lineTimedProgressEnabled,
            boolean translationProgressEnabled) {
        this.scrollScaleEnabled = scrollScaleEnabled;
        this.inactiveBlurEnabled = inactiveBlurEnabled;
        this.lineTimedProgressEnabled = lineTimedProgressEnabled;
        this.translationProgressEnabled = translationProgressEnabled;
    }

    void setConfig(LyricUiConfig config) {
        if (config == null) return;
        CompatibilityStatus.global().requested(CompatibilityStatus.Feature.COVER_GLOW, config.glowFollowsCover);
        CompatibilityStatus.global().requested(CompatibilityStatus.Feature.CHAR_LIFT, config.charLiftEnabled);
        CompatibilityStatus.global().requested(CompatibilityStatus.Feature.LINE_PROGRESS, config.lineTimedProgressEnabled);
        CompatibilityStatus.global().requested(CompatibilityStatus.Feature.TRANSLATION_PROGRESS, config.translationProgressEnabled);
        if (config.equals(uiConfig)) return;
        // C-04: a real settings change (not the periodic reload of unchanged values) is an
        // explicit event that gives paused lyric features another, limited attempt.
        FeatureDegradation.global().onSettingsReapplied(CompatibilityStatus.Feature.CUSTOM_DRAW,
                CompatibilityStatus.Feature.CHAR_LIFT, CompatibilityStatus.Feature.COVER_GLOW,
                CompatibilityStatus.Feature.TRANSLATION_BUTTON);
        LyricUiConfig previous = uiConfig;
        uiConfig = config;
        palette = LyricUiPalette.from(config);
        if (!config.primaryColor.equals(previous.primaryColor)) {
            activeFeatherShader = null;
            activeFeatherPrimaryColor = null;
        }
        scrollScaleEnabled = config.scaleEnabled;
        inactiveBlurEnabled = config.blurEnabled;
        lineTimedProgressEnabled = config.lineTimedProgressEnabled;
        translationProgressEnabled = config.translationProgressEnabled;
        charLiftEnabled = config.charLiftEnabled;
        charLiftStrengthPercent = config.charLiftStrengthPercent;
        clearGlowCache();
        if (isLyricLayoutDiagnosticsEnabled()) {
            StructuredBridgeLog.info(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.RENDER_STATE_CHANGED,
                    "Updated lyric renderer config"
                    + " | previousAlignment=" + previous.alignment
                    + ", alignment=" + config.alignment
                    + ", fontSp10=" + config.mainFontTenthsSp
                    + ", lineSpacingDp10=" + config.lineSpacingTenthsDp
                    + ", wrappedLineSpacingDp10="
                    + config.wrappedLineSpacingTenthsDp);
        }
    }

    void setCoverGlowColor(Integer color) {
        int resolved = LyricUiColors.glowShadow(uiConfig, color);
        if (resolved == palette.glowShadow) return;
        palette = palette.withGlowShadow(resolved);
        clearGlowCache();
    }

    void setForceOfficialSlotHeight(boolean forceOfficialSlotHeight) {
        this.forceOfficialSlotHeight = forceOfficialSlotHeight;
    }

    void setAodLowFrameRateMode(boolean aodLowFrameRateMode) {
        if (this.aodLowFrameRateMode == aodLowFrameRateMode) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        float currentAmount = currentAodLineFillTransitionAmount(now);
        this.aodLowFrameRateMode = aodLowFrameRateMode;
        try {
            io.github.andrealtb.lockscreenlyrics.diagnostics.AodModeObservation.global()
                    .changed(aodLowFrameRateMode, now);
        } catch (RuntimeException ignored) {
            // Optional observation; never affects the renderer's mode switch.
        }
        aodLineFillTransitionStartAmount = currentAmount;
        aodLineFillTransitionTargetAmount = aodLowFrameRateMode ? 1f : 0f;
        aodLineFillTransitionStartedAtMs =
                Math.abs(currentAmount - aodLineFillTransitionTargetAmount) < 0.001f
                        ? -1L
                        : now;
    }

    synchronized void finishTranslationLayoutChange() {
        translationAnimationStartAmount = translationAnimationTargetAmount;
        translationLayoutAmount = translationAnimationTargetAmount;
        translationAnimationStartedAtMs = -1L;
    }

    synchronized boolean advanceTranslationLayoutAnimation() {
        translationLayoutAmount = resolveTranslationAmount(SystemClock.elapsedRealtime());
        return translationAnimationStartedAtMs >= 0L;
    }

    private synchronized float currentTranslationLayoutAmount() {
        return translationLayoutAmount;
    }

    void applySlotHeight(TextView textView, WordLine line) {
        applySlotHeight(textView, line, false);
    }

    void applySlotHeight(TextView textView, WordLine line, boolean forceOfficialSlotHeight) {
        syncLineAlignment(textView, line);
        int height = resolveSlotHeight(
                textView,
                line,
                resolveRenderableTranslation(line),
                forceOfficialSlotHeight);
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
        lineAlignment = syncLineAlignment(textView, line);
        int activeRowIndex = Math.max(frame.activeIndex, frame.scaleActiveIndex);
        rowUpcoming = !frame.active && activeRowIndex >= 0 && frame.lineIndex > activeRowIndex;

        String text = line.text;
        int leftPadding = textView.getPaddingLeft();
        int rightPadding = textView.getPaddingRight();
        float availableWidth = textView.getWidth() - leftPadding - rightPadding;
        if (availableWidth <= 1f) {
            return;
        }

        boolean rowPrepTraced = BridgePerfTrace.begin("CLL:lyricRowPrepare");
        String renderableTranslation;
        int fixedSlotHeight;
        try {
            renderableTranslation = resolveRenderableTranslation(line);
            boolean forceSlotHeight = forceOfficialSlotHeight && (frame.active || frame.focused);
            fixedSlotHeight = resolveSlotHeight(
                    textView,
                    line,
                    renderableTranslation,
                    forceSlotHeight);
        } finally {
            BridgePerfTrace.end(rowPrepTraced);
        }
        if (fixedSlotHeight <= 0) {
            return;
        }
        // Binding/configuration and the shared translation-layout pass own row height.
        // Mutating LayoutParams from onDraw can make RecyclerView relayout while the
        // official SmoothScroller is settling, so drawing only consumes measured geometry.
        float availableHeight = textView.getHeight();
        if (availableHeight <= 0f) {
            return;
        }
        boolean effectiveScrollScaleEnabled = scrollScaleEnabled;
        // Advance custom opacity/blur state, but leave physical row scaling to the vendor
        // LyricsRecyclerView. Applying a second Canvas scale made the active row disagree with
        // the RecyclerView's n/animation state during page cross-fades and long seeks.
        resolveOfficialRowVisualState(line, frame);
        drawBaseFade = resolveModelSwitchRevealAmount() * rowVisualFade;
        boolean visualActiveLine = aodLowFrameRateMode ? frame.focused : frame.active;
        float aodLineFillAmount = visualActiveLine
                ? resolveAodProgressToLineAmount(line)
                : 1f;

        float translationAmount = TextUtils.isEmpty(renderableTranslation)
                ? 0f
                : resolveTranslationAmount();
        float translationLayoutAmount = TextUtils.isEmpty(renderableTranslation)
                ? 0f
                : currentTranslationLayoutAmount();
        boolean compactSlot = !scrollScaleEnabled
                && (translationAmount <= 0.001f
                || TextUtils.isEmpty(renderableTranslation))
                && isCompactLyricSlot(textView, availableHeight);
        boolean untranslatedLayout = TextUtils.isEmpty(renderableTranslation);
        configurePaints(textView, compactSlot, untranslatedLayout);
        float focusAmount = resolveFocusAmount(
                line,
                visualActiveLine,
                frame.focused,
                frame.activeIndex,
                effectiveScrollScaleEnabled);
        boolean effectsTraced = BridgePerfTrace.begin("CLL:lyricViewEffects");
        try {
            float appliedBlurRadius = resolveAppliedOfficialRowBlurRadius(textView, frame);
            applyFocusedOfficialLyricViewEffects(
                    textView,
                    appliedBlurRadius,
                    inactiveBlurEnabled && appliedBlurRadius > 0f);
        } finally {
            BridgePerfTrace.end(effectsTraced);
        }
        if ((frame.focused && focusAmount < 1f)
                || (visualActiveLine && isAodLineFillTransitionRunning())
                || (!aodLowFrameRateMode && rowVisualAnimationRunning)) {
            textView.postInvalidateOnAnimation();
        }
        applyFade(1f, focusAmount);
        if (compactSlot) {
            boolean compactTraced = BridgePerfTrace.begin("CLL:lyricCompactLine");
            try {
                drawCompactLine(
                        canvas,
                        textView,
                        frame.model,
                        line,
                        position,
                        glowPosition,
                        visualActiveLine,
                        focusAmount,
                        aodLineFillAmount,
                        availableWidth,
                        availableHeight);
            } finally {
                BridgePerfTrace.end(compactTraced);
            }
            drawBaseFade = 1f;
            return;
        }
        drawLyricGroup(
                canvas,
                textView,
                frame.model,
                line,
                position,
                glowPosition,
                visualActiveLine,
                availableWidth,
                availableHeight,
                focusAmount,
                aodLineFillAmount,
                translationAmount,
                translationLayoutAmount,
                renderableTranslation);
        drawBaseFade = 1f;
        if (isTranslationAnimationRunning()
                || isModelSwitchRevealRunning()
                || (!aodLowFrameRateMode && rowVisualAnimationRunning)) {
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
        float eased = smootherStep(rawProgress);
        return translationAnimationStartAmount
                + (translationAnimationTargetAmount - translationAnimationStartAmount)
                * eased;
    }

    private synchronized String resolveRenderableTranslation(WordLine line) {
        if (line == null) {
            return "";
        }
        String translation = nullToEmpty(line.translation);
        long now = SystemClock.elapsedRealtime();
        if (!TextUtils.isEmpty(translation)) {
            lastRenderableTranslationLineTimeMillis = line.timeMillis;
            lastRenderableTranslationNormalizedText = line.normalizedText;
            lastRenderableTranslationText = translation;
            lastRenderableTranslationAtElapsedMs = now;
            return translation;
        }
        if (!TextUtils.isEmpty(line.normalizedText)
                && line.timeMillis == lastRenderableTranslationLineTimeMillis
                && line.normalizedText.equals(lastRenderableTranslationNormalizedText)
                && !TextUtils.isEmpty(lastRenderableTranslationText)
                && lastRenderableTranslationAtElapsedMs > 0L
                && now - lastRenderableTranslationAtElapsedMs
                <= TRANSLATION_TRANSIENT_MISS_GRACE_MS) {
            return lastRenderableTranslationText;
        }
        return "";
    }

    private synchronized void clearRenderableTranslationCache() {
        lastRenderableTranslationLineTimeMillis = Long.MIN_VALUE;
        lastRenderableTranslationNormalizedText = "";
        lastRenderableTranslationText = "";
        lastRenderableTranslationAtElapsedMs = -1L;
    }

    private synchronized boolean isTranslationAnimationRunning() {
        return translationAnimationStartedAtMs >= 0L;
    }

    private synchronized float resolveModelSwitchRevealAmount() {
        if (modelSwitchRevealStartedAtMs < 0L) {
            return 1f;
        }
        long now = SystemClock.elapsedRealtime();
        float rawProgress = Math.max(
                0f,
                Math.min(1f, (now - modelSwitchRevealStartedAtMs)
                        / (float) MODEL_SWITCH_REVEAL_ANIMATION_MS));
        if (rawProgress >= 1f) {
            modelSwitchRevealStartedAtMs = -1L;
            return 1f;
        }
        return MODEL_SWITCH_REVEAL_START_ALPHA
                + (1f - MODEL_SWITCH_REVEAL_START_ALPHA) * smoothStep(rawProgress);
    }

    private synchronized boolean isModelSwitchRevealRunning() {
        return modelSwitchRevealStartedAtMs >= 0L;
    }

    private float resolveFocusAmount(
            WordLine line,
            boolean activeLine,
            boolean focusedLine,
            int activeIndex,
            boolean effectiveScrollScaleEnabled) {
        if (line == null || !focusedLine) {
            return 0f;
        }
        if (activeLine
                || effectiveScrollScaleEnabled
                || inactiveBlurEnabled
                || aodLowFrameRateMode) {
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

    private float resolveOfficialRowFadeTarget(DrawFrame frame) {
        if (!uiConfig.inactiveRowFadeEnabled) {
            return 1f;
        }
        int distance = resolveOfficialRowDistance(frame);
        if (distance <= 0) {
            return 1f;
        }
        return LyricVisualAlphaPolicy.inactiveRowFadeMultiplier(uiConfig);
    }

    private float resolveOfficialRowBlurRadiusTarget(DrawFrame frame) {
        if (!inactiveBlurEnabled) {
            return 0f;
        }
        // The frame's active state follows this row's owning RecyclerView n, keeping blur and
        // the vendor scale animation on the same official transition transaction.
        if (frame != null && frame.active) {
            return 0f;
        }
        return uiConfig.blurRadiusTenthsPx / 10f;
    }

    private float resolveOfficialRowScaleTarget(DrawFrame frame) {
        return 1f;
    }

    private static int resolveOfficialRowDistance(DrawFrame frame) {
        if (frame == null || frame.lineIndex < 0) {
            return 0;
        }
        if (frame.scaleActiveIndex < 0) {
            return frame.focused ? 0 : 1;
        }
        return Math.abs(frame.lineIndex - frame.scaleActiveIndex);
    }

    private float resolveOfficialRowVisualState(WordLine line, DrawFrame frame) {
        rowVisualFade = 1f;
        rowVisualBlurRadius = 0f;
        rowVisualAnimationRunning = false;
        long now = SystemClock.elapsedRealtime();
        boolean lowFrameRateMode = aodLowFrameRateMode;
        if (!scrollScaleEnabled && !inactiveBlurEnabled) {
            return 1f;
        }
        if (line == null || frame == null) {
            return WordLyricRenderConstants.OFFICIAL_LYRIC_ACTIVE_ROW_SCALE;
        }
        boolean scaleActive = frame.lineIndex == frame.scaleActiveIndex
                || (frame.scaleActiveIndex < 0 && frame.focused);
        float baseScale = scrollScaleEnabled
                ? resolveOfficialRowScaleTarget(frame)
                : 1f;
        float targetScale = baseScale;
        float targetFade = resolveOfficialRowFadeTarget(frame);
        float targetBlurRadius = resolveOfficialRowBlurRadiusTarget(frame);
        float currentScale = line.rowVisualScaleInitialized
                ? currentOfficialRowScale(line, now)
                : targetScale;
        float currentFade = line.rowVisualScaleInitialized
                ? currentOfficialRowFade(line, now)
                : targetFade;
        int scaleActiveIndex = frame.scaleActiveIndex;
        boolean targetChanged = !line.rowVisualScaleInitialized
                || Math.abs(line.rowVisualScaleTarget - targetScale) > 0.001f
                || Math.abs(line.rowVisualFadeTarget - targetFade) > 0.001f
                || Math.abs(line.rowVisualBlurRadiusTarget - targetBlurRadius) > 0.001f
                || line.rowVisualScaleActiveIndex != scaleActiveIndex;
        if (targetChanged) {
            boolean continuousAdvance = line.rowVisualScaleInitialized
                    && line.rowVisualScaleActiveIndex >= 0
                    && scaleActiveIndex >= 0
                    && Math.abs(scaleActiveIndex - line.rowVisualScaleActiveIndex) <= 1;
            boolean firstLyricStartAdvance = line.rowVisualScaleInitialized
                    && line.rowVisualScaleActiveIndex < 0
                    && scaleActiveIndex == frame.model.firstDisplayLineIndex();
            boolean animate = !lowFrameRateMode
                    && uiConfig.motionMode == LyricUiConfig.MOTION_STANDARD
                    && frame.rowScaleAnimationAllowed
                    && (continuousAdvance || firstLyricStartAdvance)
                    && hasOfficialRowVisualDelta(
                    currentScale,
                    targetScale,
                    currentFade,
                    targetFade);
            maybeLogOfficialRowScale(
                    line,
                    frame,
                    currentScale,
                    targetScale,
                    scaleActive,
                    true,
                    animate);
            line.rowVisualScaleInitialized = true;
            line.rowVisualScaleStart = animate ? currentScale : targetScale;
            line.rowVisualScaleTarget = targetScale;
            line.rowVisualFadeStart = animate ? currentFade : targetFade;
            line.rowVisualFadeTarget = targetFade;
            line.rowVisualBlurRadiusTarget = targetBlurRadius;
            line.rowVisualScaleStartedAtMs = animate ? now : -1L;
            line.rowVisualScaleActiveIndex = scaleActiveIndex;
            rowVisualFade = animate ? currentFade : targetFade;
            rowVisualBlurRadius = targetBlurRadius;
            rowVisualAnimationRunning = animate;
            return animate ? currentScale : targetScale;
        }
        if (isOfficialRowScaleAnimationRunning(line)) {
            rowVisualAnimationRunning = true;
        }
        rowVisualFade = currentFade;
        rowVisualBlurRadius = targetBlurRadius;
        return currentScale;
    }

    private float resolveAppliedOfficialRowBlurRadius(TextView textView, DrawFrame frame) {
        if (rowVisualBlurRadius <= 0f) {
            return 0f;
        }
        return rowVisualBlurRadius;
    }

    private static boolean hasOfficialRowVisualDelta(
            float currentScale,
            float targetScale,
            float currentFade,
            float targetFade) {
        return Math.abs(currentScale - targetScale) > 0.001f
                || Math.abs(currentFade - targetFade) > 0.001f;
    }

    private static void maybeLogOfficialRowScale(
            WordLine line,
            DrawFrame frame,
            float currentScale,
            float targetScale,
            boolean scaleActive,
            boolean targetChanged,
            boolean animate) {
        if (!isLyricLayoutDiagnosticsEnabled()) {
            return;
        }
        StructuredBridgeLog.info(
                BridgeDebugArea.AOD,
                BridgeEvents.OFFICIAL_ROW_SCALE,
                "Official lyric row scale"
                + ", lineIndex=" + (frame == null ? -1 : frame.lineIndex)
                + ", activeIndex=" + (frame == null ? -1 : frame.activeIndex)
                + ", scaleActiveIndex=" + (frame == null ? -1 : frame.scaleActiveIndex)
                + ", active=" + (frame != null && frame.active)
                + ", scaleActive=" + scaleActive
                + ", currentScale=" + currentScale
                + ", targetScale=" + targetScale
                + ", previousTarget=" + (line == null ? -1f : line.rowVisualScaleTarget)
                + ", targetChanged=" + targetChanged
                + ", animate=" + animate
                + ", allowed=" + (frame != null && frame.rowScaleAnimationAllowed)
                + ", startedAt=" + (line == null ? -1L : line.rowVisualScaleStartedAtMs)
                + ", line=" + shortenForLog(line == null ? "" : line.normalizedText));
    }

    private static float currentOfficialRowScale(WordLine line, long now) {
        if (line == null || line.rowVisualScaleStartedAtMs < 0L) {
            return line == null ? WordLyricRenderConstants.OFFICIAL_LYRIC_ACTIVE_ROW_SCALE : line.rowVisualScaleTarget;
        }
        float rawProgress = Math.max(
                0f,
                Math.min(1f, (now - line.rowVisualScaleStartedAtMs)
                        / (float) WordLyricRenderConstants.OFFICIAL_LYRIC_ROW_SCALE_ANIMATION_MS));
        if (rawProgress >= 1f) {
            finishOfficialRowVisualAnimation(line);
            return line.rowVisualScaleTarget;
        }
        float eased = officialRowScaleEase(rawProgress);
        return line.rowVisualScaleStart
                + (line.rowVisualScaleTarget - line.rowVisualScaleStart) * eased;
    }

    private static float currentOfficialRowFade(WordLine line, long now) {
        if (line == null || line.rowVisualScaleStartedAtMs < 0L) {
            return line == null ? 1f : line.rowVisualFadeTarget;
        }
        float rawProgress = Math.max(
                0f,
                Math.min(1f, (now - line.rowVisualScaleStartedAtMs)
                        / (float) WordLyricRenderConstants.OFFICIAL_LYRIC_ROW_SCALE_ANIMATION_MS));
        if (rawProgress >= 1f) {
            return line.rowVisualFadeTarget;
        }
        float eased = officialRowScaleEase(rawProgress);
        return line.rowVisualFadeStart
                + (line.rowVisualFadeTarget - line.rowVisualFadeStart) * eased;
    }

    private static void finishOfficialRowVisualAnimation(WordLine line) {
        line.rowVisualScaleStart = line.rowVisualScaleTarget;
        line.rowVisualFadeStart = line.rowVisualFadeTarget;
        line.rowVisualScaleStartedAtMs = -1L;
    }

    private static boolean isOfficialRowScaleAnimationRunning(WordLine line) {
        return line != null && line.rowVisualScaleStartedAtMs >= 0L;
    }

    private static float officialRowScaleEase(float progress) {
        return cubicBezierYForX(
                Math.max(0f, Math.min(1f, progress)),
                WordLyricRenderConstants.OFFICIAL_LYRIC_ROW_EASE_X1,
                WordLyricRenderConstants.OFFICIAL_LYRIC_ROW_EASE_Y1,
                WordLyricRenderConstants.OFFICIAL_LYRIC_ROW_EASE_X2,
                WordLyricRenderConstants.OFFICIAL_LYRIC_ROW_EASE_Y2);
    }

    private static float cubicBezierYForX(
            float x,
            float x1,
            float y1,
            float x2,
            float y2) {
        float t = x;
        for (int i = 0; i < 5; i++) {
            float currentX = cubicBezierCoordinate(t, x1, x2) - x;
            float derivative = cubicBezierDerivative(t, x1, x2);
            if (Math.abs(currentX) < 0.0005f || Math.abs(derivative) < 0.00001f) {
                break;
            }
            t = Math.max(0f, Math.min(1f, t - currentX / derivative));
        }
        return cubicBezierCoordinate(t, y1, y2);
    }

    private static float cubicBezierCoordinate(float t, float p1, float p2) {
        float inverse = 1f - t;
        return 3f * inverse * inverse * t * p1
                + 3f * inverse * t * t * p2
                + t * t * t;
    }

    private static float cubicBezierDerivative(float t, float p1, float p2) {
        float inverse = 1f - t;
        return 3f * inverse * inverse * p1
                + 6f * inverse * t * (p2 - p1)
                + 3f * t * t * (1f - p2);
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
            float aodLineFillAmount,
            float translationAmount,
            float translationLayoutAmount,
            String renderableTranslation) {
        LyricGroupDrawContext state = lyricGroupDrawContext;
        boolean prepareTraced = BridgePerfTrace.begin("CLL:lyricGroupPrepare");
        boolean prepared;
        try {
            prepared = prepareLyricGroupDraw(
                    textView,
                    model,
                    line,
                    position,
                    glowPosition,
                    activeLine,
                    availableWidth,
                    availableHeight,
                    focusAmount,
                    aodLineFillAmount,
                    translationAmount,
                    translationLayoutAmount,
                    renderableTranslation,
                    state);
        } finally {
            BridgePerfTrace.end(prepareTraced);
        }
        if (!prepared) {
            return;
        }

        int save = canvas.save();
        try {
            canvas.clipRect(
                    textView.getPaddingLeft(),
                    0f,
                    textView.getWidth() - textView.getPaddingRight(),
                    textView.getHeight());
            boolean mainTraced = BridgePerfTrace.begin(activeLine
                    ? "CLL:lyricMainPassActive"
                    : "CLL:lyricMainPassInactive");
            try {
                drawMainLyricPass(canvas, textView, state);
            } finally {
                BridgePerfTrace.end(mainTraced);
            }
            applyFade(1f, state.focusAmount);
            boolean translationTraced = BridgePerfTrace.begin("CLL:lyricTranslationPass");
            try {
                drawTranslationPass(canvas, textView, state);
            } finally {
                BridgePerfTrace.end(translationTraced);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private boolean prepareLyricGroupDraw(
            TextView textView,
            WordLyricModel model,
            WordLine line,
            long position,
            long glowPosition,
            boolean activeLine,
            float availableWidth,
            float availableHeight,
            float focusAmount,
            float aodLineFillAmount,
            float translationAmount,
            float translationLayoutAmount,
            String renderableTranslation,
            LyricGroupDrawContext state) {
        boolean sourceHasTranslation = !TextUtils.isEmpty(renderableTranslation);
        boolean untranslatedLayout = !sourceHasTranslation;
        String text = line.text;
        buildDrawLines(line, text, availableWidth, false, untranslatedLayout);
        if (drawLines.isEmpty()) {
            return false;
        }

        boolean timestampHighlight = WordLyricRenderSupport.shouldUseTimestampHighlight(
                model,
                line);
        boolean drawProgress = activeLine
                && !timestampHighlight
                && shouldDrawWordProgressForVisual(line, aodLineFillAmount);
        recordWordProgress(line, activeLine, timestampHighlight, drawProgress);
        float fullLineOverlayAmount = aodLowFrameRateMode && drawProgress
                ? aodLineFillAmount
                : 0f;
        int wordIndex = drawProgress ? line.findWordIndex(position) : -1;
        WordRange activeWord = wordIndex >= 0 && wordIndex < line.words.size()
                ? line.words.get(wordIndex)
                : null;
        int glowWordIndex = drawProgress ? line.findWordIndex(glowPosition) : -1;
        WordRange glowActiveWord =
                glowWordIndex >= 0 && glowWordIndex < line.words.size()
                        ? line.words.get(glowWordIndex)
                        : null;

        boolean passiveLineWindow = shouldUsePassiveLineWindow(line, drawLines.size());
        line.passiveLinePanEligible = passiveLineWindow;
        MainLineWindow lineWindow = !passiveLineWindow
                ? resolveMainLineWindow(
                        model,
                        line,
                        activeWord,
                        activeLine,
                        availableWidth,
                        position)
                : null;
        int visibleMainLineCount = LyricUiLayoutPolicy.visibleMainLineCount(
                drawLines.size(),
                visibleMainDrawLines(line));

        inactivePaint.getFontMetrics(mainFontMetrics);
        Paint.FontMetrics mainMetrics = mainFontMetrics;
        float lineHeight = mainMetrics.descent - mainMetrics.ascent;
        float lineGap = visibleMainLineCount > 1
                ? LyricUiLayoutPolicy.addWrappedLineSpacing(
                        dp(textView.getContext(), WRAPPED_LINE_BASE_GAP_DP),
                        dp(
                                textView.getContext(),
                                LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(uiConfig)
                                        / 10f))
                : 0f;
        float translationGap = sourceHasTranslation
                ? dp(textView.getContext(), 2f)
                : 0f;
        translationPaint.getFontMetrics(translationFontMetrics);
        Paint.FontMetrics translationMetrics = translationFontMetrics;
        float mainHeight = LyricUiLayoutPolicy.mainTextBlockHeight(
                mainMetrics.top,
                mainMetrics.ascent,
                mainMetrics.descent,
                mainMetrics.bottom,
                visibleMainLineCount,
                lineGap);
        float translationHeight = sourceHasTranslation
                ? LyricUiLayoutPolicy.fontOuterHeight(
                        translationMetrics.top,
                        translationMetrics.bottom)
                : 0f;
        float groupHeight = LyricUiLayoutPolicy.translatedGroupHeight(
                mainHeight,
                sourceHasTranslation ? translationGap + translationHeight : 0f,
                translationLayoutAmount);
        float top = clampTopWithinSlot(
                Math.max(0f, (availableHeight - groupHeight) * 0.5f),
                availableHeight,
                groupHeight);

        state.model = model;
        state.line = line;
        state.text = text;
        state.renderableTranslation = renderableTranslation;
        state.activeWord = activeWord;
        state.glowActiveWord = glowActiveWord;
        state.lineWindow = lineWindow;
        state.position = position;
        state.glowPosition = glowPosition;
        state.activeLine = activeLine;
        state.drawProgress = drawProgress;
        state.passiveLineWindow = passiveLineWindow;
        state.hasTranslation = translationAmount > 0.001f && sourceHasTranslation;
        state.wordIndex = wordIndex;
        state.glowWordIndex = glowWordIndex;
        state.visibleMainLineCount = visibleMainLineCount;
        state.availableWidth = availableWidth;
        state.focusAmount = focusAmount;
        state.fullLineOverlayAmount = fullLineOverlayAmount;
        state.top = top;
        state.mainHeight = mainHeight;
        state.y = top - LyricUiLayoutPolicy.outwardFontTop(mainMetrics.top);
        state.lineHeight = lineHeight;
        state.lineGap = lineGap;
        state.translationGap = translationGap;
        state.translationMetricsTop = translationMetrics.top;
        state.translationAmount = translationAmount;
        // Only a visible translation constrains the lift. Follow the same slide
        // as drawTranslationPass; a hidden translation must not reserve a phantom floor.
        charLiftFloorY = CharLiftGeometry.translationFloorY(
                sourceHasTranslation, top + mainHeight, translationGap, translationAmount,
                dp(textView.getContext(), TRANSLATION_TOGGLE_SLIDE_DP));
        return true;
    }

    private void drawMainLyricPass(
            Canvas canvas,
            TextView textView,
            LyricGroupDrawContext state) {
        if (state.passiveLineWindow && state.activeLine) {
            float lineProgress = resolveLineElapsedProgress(
                    state.model,
                    state.line,
                    state.position);
            float panProgress =
                    LockscreenIntegrationPolicy.passiveLinePanProgress(lineProgress);
            float lineAdvance = state.lineHeight + state.lineGap;
            float maxPan = Math.max(
                    0f,
                    (drawLines.size() - state.visibleMainLineCount) * lineAdvance);
            int passiveClipSave = canvas.save();
            try {
                canvas.clipRect(
                        textView.getPaddingLeft(),
                        state.top,
                        textView.getWidth() - textView.getPaddingRight(),
                        state.top + state.mainHeight);
                drawMainLineWindow(
                    canvas,
                    textView,
                    state.model,
                    state.line,
                    state.text,
                    state.activeWord,
                    state.wordIndex,
                    state.glowActiveWord,
                    state.glowWordIndex,
                    state.y - maxPan * panProgress,
                    state.lineHeight,
                    state.lineGap,
                    state.availableWidth,
                    state.position,
                    state.glowPosition,
                    state.activeLine,
                    state.drawProgress,
                    state.fullLineOverlayAmount,
                    0,
                    drawLines.size(),
                    state.focusAmount,
                        1f);
            } finally {
                canvas.restoreToCount(passiveClipSave);
            }
            return;
        }

        MainLineWindow lineWindow = state.lineWindow;
        if (lineWindow != null && lineWindow.animating) {
            float slide = dp(textView.getContext(), MAIN_LINE_WINDOW_SLIDE_DP);
            float direction =
                    lineWindow.currentStart >= lineWindow.previousStart ? 1f : -1f;
            drawMainLineWindow(
                    canvas,
                    textView,
                    state.model,
                    state.line,
                    state.text,
                    state.activeWord,
                    state.wordIndex,
                    state.glowActiveWord,
                    state.glowWordIndex,
                    state.y - direction * slide * lineWindow.progress,
                    state.lineHeight,
                    state.lineGap,
                    state.availableWidth,
                    state.position,
                    state.glowPosition,
                    state.activeLine,
                    state.drawProgress,
                    state.fullLineOverlayAmount,
                    lineWindow.previousStart,
                    lineWindow.count,
                    state.focusAmount,
                    1f - lineWindow.alphaProgress);
            drawMainLineWindow(
                    canvas,
                    textView,
                    state.model,
                    state.line,
                    state.text,
                    state.activeWord,
                    state.wordIndex,
                    state.glowActiveWord,
                    state.glowWordIndex,
                    state.y + direction * slide * (1f - lineWindow.progress),
                    state.lineHeight,
                    state.lineGap,
                    state.availableWidth,
                    state.position,
                    state.glowPosition,
                    state.activeLine,
                    state.drawProgress,
                    state.fullLineOverlayAmount,
                    lineWindow.currentStart,
                    lineWindow.count,
                    state.focusAmount,
                    lineWindow.alphaProgress);
            textView.postInvalidateOnAnimation();
            return;
        }

        int windowStart = lineWindow == null ? 0 : lineWindow.currentStart;
        drawMainLineWindow(
                canvas,
                textView,
                state.model,
                state.line,
                state.text,
                state.activeWord,
                state.wordIndex,
                state.glowActiveWord,
                state.glowWordIndex,
                state.y,
                state.lineHeight,
                state.lineGap,
                state.availableWidth,
                state.position,
                state.glowPosition,
                state.activeLine,
                state.drawProgress,
                state.fullLineOverlayAmount,
                windowStart,
                state.visibleMainLineCount,
                state.focusAmount,
                1f);
    }

    private void drawTranslationPass(
            Canvas canvas,
            TextView textView,
            LyricGroupDrawContext state) {
        if (!state.hasTranslation) {
            return;
        }
        float translationBaseline = state.top
                + state.mainHeight
                + state.translationGap
                - LyricUiLayoutPolicy.outwardFontTop(state.translationMetricsTop)
                + dp(textView.getContext(), TRANSLATION_TOGGLE_SLIDE_DP)
                * (1f - state.translationAmount);
        translationPaint.setColor(scaleAlpha(
                palette.translationBase(state.activeLine),
                state.translationAmount * drawBaseFade));
        translationActivePaint.setColor(scaleAlpha(
                palette.activeTranslationProgress,
                state.translationAmount * drawBaseFade));
        translationFeatherPaint.setColor(translationActivePaint.getColor());
        float translationX = resolveTranslationTextX(
                textView,
                state.model,
                state.line,
                state.renderableTranslation,
                state.position,
                state.activeLine,
                state.availableWidth);
        drawTranslationLine(
                canvas,
                textView,
                state.model,
                state.line,
                state.renderableTranslation,
                translationX,
                translationBaseline,
                state.position,
                state.activeLine);
    }

    private boolean shouldUsePassiveLineWindow(WordLine line, int totalLines) {
        return line != null
                && uiConfig.passiveVerticalPanEnabled
                && line.timingMode == LyricTimingMode.LINE_TIMED
                && !lineTimedProgressEnabled
                && !aodLowFrameRateMode
                && totalLines > visibleMainDrawLines(line);
    }

    private static float clampTopWithinSlot(
            float top,
            float availableHeight,
            float groupHeight) {
        float maxTop = Math.max(0f, availableHeight - groupHeight);
        return Math.max(0f, Math.min(maxTop, top));
    }

    private void drawTranslationLine(
            Canvas canvas,
            TextView textView,
            WordLyricModel model,
            WordLine line,
            String text,
            float x,
            float y,
            long position,
            boolean activeLine) {
        canvas.drawText(text, x, y, translationPaint);
        if (!activeLine
                || !shouldDrawTranslationProgress(line)
                || line == null
                || TextUtils.isEmpty(text)) {
            return;
        }
        float width = translationPaint.measureText(text);
        if (width <= 0f) {
            return;
        }
        float progress = resolveTranslationRevealProgress(model, line, position);
        float revealWidth = width * progress;
        if (revealWidth <= 0f) {
            if (progress < 1f) {
                textView.postInvalidateOnAnimation();
            }
            return;
        }
        drawProgressGlow(
                canvas,
                line,
                text,
                0,
                text.length(),
                x,
                y,
                width,
                revealWidth,
                translationActivePaint);
        drawRevealedText(
                canvas,
                text,
                0,
                text.length(),
                x,
                y,
                width,
                revealWidth,
                translationActivePaint,
                translationFeatherPaint);
        if (progress < 1f) {
            textView.postInvalidateOnAnimation();
        }
    }

    private boolean shouldDrawTranslationProgress(WordLine line) {
        boolean eligible = !aodLowFrameRateMode
                && translationProgressEnabled
                && line != null
                && (line.timingMode != LyricTimingMode.LINE_TIMED
                || lineTimedProgressEnabled);
        CompatibilityStatus.global().progress(line, CompatibilityStatus.Feature.TRANSLATION_PROGRESS,
                translationProgressEnabled, eligible, aodLowFrameRateMode,
                line != null && line.timingMode == LyricTimingMode.LINE_TIMED && !lineTimedProgressEnabled);
        return eligible;
    }

    private float resolveTranslationRevealProgress(
            WordLyricModel model,
            WordLine line,
            long position) {
        if (line == null) {
            return 0f;
        }
        if (line.timingMode == LyricTimingMode.LINE_TIMED && lineTimedProgressEnabled) {
            return resolveLineTimedRevealProgress(model, line, position);
        }
        if (line.words == null || line.words.isEmpty() || TextUtils.isEmpty(line.text)) {
            return resolveLineElapsedProgress(model, line, position);
        }
        if (WordLyricRenderSupport.shouldHoldWordTimedReveal(line, position)) {
            return 0f;
        }
        int wordIndex = line.findWordIndex(position);
        if (wordIndex < 0 || wordIndex >= line.words.size()) {
            return resolveLineElapsedProgress(model, line, position);
        }
        if (wordIndex == line.words.size() - 1
                && position >= WordLyricRenderSupport.wordRevealEndMillis(model, line, wordIndex)) {
            return 1f;
        }
        WordRange activeWord = line.words.get(wordIndex);
        int activeStart = Math.max(0, Math.min(activeWord.start, line.text.length()));
        int activeEnd = Math.max(activeStart, Math.min(activeWord.end, line.text.length()));
        float fullWidth = inactivePaint.measureText(line.text);
        if (fullWidth <= 0f || activeStart >= activeEnd) {
            return resolveLineElapsedProgress(model, line, position);
        }
        float revealWidth = inactivePaint.measureText(line.text, 0, activeStart)
                + inactivePaint.measureText(line.text, activeStart, activeEnd)
                * WordLyricRenderSupport.wordRevealProgress(model, line, wordIndex, position);
        return Math.max(0f, Math.min(1f, revealWidth / fullWidth));
    }

    private boolean isLineTimedProgress(WordLine line) {
        boolean eligible = line != null && line.timingMode == LyricTimingMode.LINE_TIMED && lineTimedProgressEnabled;
        CompatibilityStatus.global().progress(line, CompatibilityStatus.Feature.LINE_PROGRESS,
                lineTimedProgressEnabled, eligible, false, false);
        return eligible;
    }

    private float resolveLineTimedRevealProgress(WordLyricModel model, WordLine line, long position) {
        return LineTimedRevealPolicy.progress(line.timeMillis, resolveLineDisplayEndMillis(model, line), position);
    }

    private float resolveLineElapsedProgress(
            WordLyricModel model,
            WordLine line,
            long position) {
        if (line == null) {
            return 0f;
        }
        long displayEndMillis = resolveLineDisplayEndMillis(model, line);
        long duration = Math.max(1L, displayEndMillis - line.timeMillis);
        return Math.max(
                0f,
                Math.min(1f, (position - line.timeMillis) / (float) duration));
    }

    private long resolveLineDisplayEndMillis(WordLyricModel model, WordLine line) {
        long displayEndMillis = line.endTimeMillis;
        if (model != null) {
            int lineIndex = model.indexOfLine(line);
            WordLine nextLine = model.lineAt(lineIndex + 1);
            if (nextLine != null && nextLine.timeMillis > line.timeMillis) {
                displayEndMillis = nextLine.timeMillis;
            } else if (lineIndex >= 0
                    && line.timingMode == LyricTimingMode.LINE_TIMED
                    && displayEndMillis - line.timeMillis <= 1_000L) {
                WordLine previousLine = model.lineAt(lineIndex - 1);
                long previousLineIntervalMillis = previousLine == null
                        ? -1L
                        : line.timeMillis - previousLine.timeMillis;
                displayEndMillis = line.timeMillis
                        + LockscreenIntegrationPolicy.estimateFinalLineTimedDurationMillis(
                        previousLineIntervalMillis,
                        line.text);
            }
        }
        return Math.max(line.timeMillis + 1L, displayEndMillis);
    }

    private float resolveTranslationTextX(
            TextView textView,
            WordLyricModel model,
            WordLine line,
            String renderableTranslation,
            long position,
            boolean activeLine,
            float availableWidth) {
        float translationWidth = translationPaint.measureText(renderableTranslation);
        float startX = resolveTextX(
                textView,
                renderableTranslation,
                0,
                renderableTranslation.length(),
                translationPaint,
                translationWidth,
                availableWidth);
        if (aodLowFrameRateMode
                || !uiConfig.translationMarqueeEnabled
                || !activeLine
                || line == null
                || TextUtils.isEmpty(renderableTranslation)) {
            return startX;
        }
        float overflow = translationWidth - availableWidth;
        if (overflow <= 0.5f) {
            return startX;
        }
        float lineProgress = resolveLineElapsedProgress(model, line, position);
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
        float direction = textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? 1f : -1f;
        return startX + direction * overflow * easedScrollProgress;
    }

    private int resolveSlotHeight(
            TextView textView,
            WordLine line,
            String renderableTranslation,
            boolean forceOfficialSlotHeight) {
        if (textView == null || line == null) {
            return 0;
        }
        Context context = textView.getContext();
        int fallbackHeight = dp(context, WordLyricRenderConstants.LYRIC_SLOT_HEIGHT_DP);
        if (scrollScaleEnabled && forceOfficialSlotHeight) {
            return fallbackHeight;
        }
        float availableWidth = resolveAvailableWidth(textView);
        if (availableWidth <= 1f || TextUtils.isEmpty(line.text)) {
            return fallbackHeight;
        }

        boolean sourceHasTranslation = !TextUtils.isEmpty(renderableTranslation);
        boolean untranslatedLayout = !sourceHasTranslation;
        configureGeometryPaints(textView, untranslatedLayout);
        int widthKey = Math.max(1, Math.round(availableWidth));
        int baseTextSizeKey = Math.max(1, Math.round(inactivePaint.getTextSize() * 10f));
        // The enclosing module advances one shared layout amount before each frame,
        // then updates every bound row together. This avoids holder draw order making
        // neighboring rows use different heights during the same translation frame.
        float translationAmount = sourceHasTranslation
                ? currentTranslationLayoutAmount()
                : 0f;
        int translationAmountKey = Math.round(translationAmount * 1000f);
        int translationHash = sourceHasTranslation ? renderableTranslation.hashCode() : 0;
        int typographyKey = 31 * Math.round(translationPaint.getTextSize() * 10f)
                + uiConfig.fontWeight;
        typographyKey = 31 * typographyKey
                + (uiConfig.glowEnabled ? uiConfig.glowRadiusPercent : 0);
        typographyKey = 31 * typographyKey + uiConfig.wrappedLineSpacingTenthsDp;
        typographyKey = 31 * typographyKey + officialBaseTypefaceGeneration;
        if (line.slotHeightCollapsedValue > 0
                && line.slotHeightExpandedValue > 0
                && line.slotHeightWidthKey == widthKey
                && line.slotHeightBaseTextSizeKey == baseTextSizeKey
                && line.slotHeightTypographyKey == typographyKey
                && line.slotHeightTranslationHash == translationHash
                && line.slotHeightTranslationLength == renderableTranslation.length()
                && line.slotHeightScrollScaleEnabled == scrollScaleEnabled
                && line.slotHeightForceOfficial == forceOfficialSlotHeight) {
            int cachedHeight = Math.round(
                    line.slotHeightCollapsedValue
                            + (line.slotHeightExpandedValue
                            - line.slotHeightCollapsedValue) * translationAmount);
            line.slotHeightTranslationAmountKey = translationAmountKey;
            line.slotHeightValue = cachedHeight;
            return cachedHeight;
        }
        buildDrawLines(line, line.text, availableWidth, false, untranslatedLayout);
        if (drawLines.isEmpty()) {
            return fallbackHeight;
        }

        int fullMainLineCount = drawLines.size();
        int visibleMainLineCount = LyricUiLayoutPolicy.visibleMainLineCount(
                fullMainLineCount,
                visibleMainDrawLines(line));

        inactivePaint.getFontMetrics(mainFontMetrics);
        Paint.FontMetrics mainMetrics = mainFontMetrics;
        float lineHeight = mainMetrics.descent - mainMetrics.ascent;
        float lineGap = visibleMainLineCount > 1
                ? LyricUiLayoutPolicy.addWrappedLineSpacing(
                        dp(context, WRAPPED_LINE_BASE_GAP_DP),
                        dp(
                                context,
                                LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(uiConfig)
                                        / 10f))
                : 0f;
        float mainHeight = LyricUiLayoutPolicy.mainTextBlockHeight(
                mainMetrics.top,
                mainMetrics.ascent,
                mainMetrics.descent,
                mainMetrics.bottom,
                visibleMainLineCount,
                lineGap);

        float expandedGroupHeight = mainHeight;
        if (sourceHasTranslation) {
            translationPaint.getFontMetrics(translationFontMetrics);
            Paint.FontMetrics translationMetrics = translationFontMetrics;
            float translationHeight = LyricUiLayoutPolicy.fontOuterHeight(
                    translationMetrics.top,
                    translationMetrics.bottom);
            expandedGroupHeight = LyricUiLayoutPolicy.translatedGroupHeight(
                    mainHeight,
                    dp(context, 2f) + translationHeight,
                    1f);
        }

        // Variable-height rows are safe as long as a row's height is content/config
        // driven and does not change when focus, progress, AOD, blur, or scale animates.
        // Keep the same padding for scale presets so bold glyphs and glow are not
        // clipped at the TextView/item boundary.
        int minHeight = dp(context, WordLyricRenderConstants.LYRIC_SLOT_MIN_HEIGHT_DP);
        int verticalPadding = dp(context, WordLyricRenderConstants.LYRIC_SLOT_VERTICAL_PADDING_DP);
        if (uiConfig.glowEnabled) {
            int glowDrawingRoom = Math.round(
                    inactivePaint.getTextSize()
                            * uiConfig.glowRadiusPercent
                            / 50f)
                    + dp(context, 2f);
            verticalPadding = Math.max(verticalPadding, glowDrawingRoom);
        }
        int collapsedHeight = LyricUiLayoutPolicy.requiredSlotHeight(
                mainHeight,
                verticalPadding,
                dp(context, WordLyricRenderConstants.LYRIC_SLOT_BOTTOM_SAFETY_DP),
                minHeight);
        int expandedHeight = LyricUiLayoutPolicy.requiredSlotHeight(
                expandedGroupHeight,
                verticalPadding,
                dp(context, WordLyricRenderConstants.LYRIC_SLOT_BOTTOM_SAFETY_DP),
                minHeight);
        int resolvedHeight = Math.round(
                collapsedHeight + (expandedHeight - collapsedHeight) * translationAmount);
        line.slotHeightWidthKey = widthKey;
        line.slotHeightBaseTextSizeKey = baseTextSizeKey;
        line.slotHeightTypographyKey = typographyKey;
        line.slotHeightTranslationHash = translationHash;
        line.slotHeightTranslationLength = renderableTranslation.length();
        line.slotHeightTranslationAmountKey = translationAmountKey;
        line.slotHeightScrollScaleEnabled = scrollScaleEnabled;
        line.slotHeightForceOfficial = forceOfficialSlotHeight;
        line.slotHeightCollapsedValue = collapsedHeight;
        line.slotHeightExpandedValue = expandedHeight;
        line.slotHeightValue = resolvedHeight;
        return resolvedHeight;
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
        if (availableWidth <= 1f) {
            View recycler = findContainingLyricsRecyclerView(textView);
            if (recycler != null && recycler.getWidth() > 0) {
                int recyclerContentWidth = recycler.getWidth()
                        - recycler.getPaddingLeft()
                        - recycler.getPaddingRight();
                availableWidth = recyclerContentWidth - leftPadding - rightPadding;
            }
        }
        return availableWidth;
    }

    private MainLineWindow resolveMainLineWindow(
            WordLyricModel model,
            WordLine line,
            WordRange activeWord,
            boolean activeLine,
            float availableWidth,
            long position) {
        int totalLines = drawLines.size();
        int count = LyricUiLayoutPolicy.visibleMainLineCount(
                totalLines,
                visibleMainDrawLines(line));
        if (totalLines <= visibleMainDrawLines(line)) {
            resetMainLineWindow(line, 0, availableWidth);
            return mainLineWindow.set(0, 0, count, 1f, 1f, false);
        }

        // RecyclerView can briefly draw two holders mapped to the same lyric while it
        // advances. An inactive holder must not reset the active holder's two-line
        // sliding window, otherwise later wrapped lines never stay visible.
        if (!activeLine) {
            return mainLineWindow.set(0, 0, count, 1f, 1f, false);
        }

        int targetStart = targetMainLineWindowStart(
                model,
                line,
                activeWord,
                totalLines,
                position);
        int layoutKey = mainLineWindowLayoutKey(line, availableWidth);
        long now = SystemClock.elapsedRealtime();
        if (line.mainLineWindowLayoutKey != layoutKey) {
            line.mainLineWindowLayoutKey = layoutKey;
            line.mainLineWindowPreviousStart = targetStart;
            line.mainLineWindowStart = targetStart;
            line.mainLineWindowChangedAtMs = now;
            return mainLineWindow.set(
                    targetStart,
                    targetStart,
                    count,
                    1f,
                    1f,
                    false);
        }

        if (line.mainLineWindowStart != targetStart) {
            if (aodLowFrameRateMode) {
                line.mainLineWindowPreviousStart = targetStart;
                line.mainLineWindowStart = targetStart;
                line.mainLineWindowChangedAtMs = now;
                return mainLineWindow.set(
                        targetStart,
                        targetStart,
                        count,
                        1f,
                        1f,
                        false);
            }
            line.mainLineWindowPreviousStart = line.mainLineWindowStart;
            line.mainLineWindowStart = targetStart;
            line.mainLineWindowChangedAtMs = now;
        }

        float rawProgress = Math.min(
                1f,
                Math.max(0f, (now - line.mainLineWindowChangedAtMs)
                        / (float) MAIN_LINE_WINDOW_ANIMATION_MS));
        float eased = smoothStep(rawProgress);
        boolean animating = rawProgress < 1f
                && line.mainLineWindowPreviousStart != line.mainLineWindowStart;
        if (!animating) {
            line.mainLineWindowPreviousStart = line.mainLineWindowStart;
        }
        return mainLineWindow.set(
                line.mainLineWindowStart,
                line.mainLineWindowPreviousStart,
                count,
                eased,
                eased,
                animating);
    }

    private void resetMainLineWindow(WordLine line, int start, float availableWidth) {
        if (line == null) {
            return;
        }
        line.mainLineWindowLayoutKey = mainLineWindowLayoutKey(line, availableWidth);
        line.mainLineWindowPreviousStart = start;
        line.mainLineWindowStart = start;
        line.mainLineWindowChangedAtMs = SystemClock.elapsedRealtime();
    }

    private static int mainLineWindowLayoutKey(WordLine line, float availableWidth) {
        int key = Math.max(1, Math.round(availableWidth));
        key = 31 * key + (line == null ? 0 : line.rendererLayoutTextSizeKey);
        key = 31 * key + (line == null ? 0 : line.rendererLayoutCount);
        key = 31 * key + (line == null
                ? 0
                : System.identityHashCode(line.rendererLayoutTypeface));
        return key;
    }

    private int targetMainLineWindowStart(
            WordLyricModel model,
            WordLine line,
            WordRange activeWord,
            int totalLines,
            long position) {
        if (line != null && line.timingMode == LyricTimingMode.LINE_TIMED) {
            if (!lineTimedProgressEnabled && !aodLowFrameRateMode) {
                return 0;
            }
            return LockscreenIntegrationPolicy.lineTimedSlidingWindowStart(
                    position,
                    line.timeMillis,
                    resolveLineDisplayEndMillis(model, line),
                    line.rendererLayoutWidths,
                    totalLines,
                    visibleMainDrawLines(line));
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
                        visibleMainDrawLines(line));
            }
        }
        return 0;
    }

    private float resolveAodProgressToLineAmount(WordLine line) {
        if (!canDrawWordProgress(line)) {
            return aodLowFrameRateMode ? 1f : 0f;
        }
        return currentAodLineFillTransitionAmount(SystemClock.elapsedRealtime());
    }

    private float currentAodLineFillTransitionAmount(long now) {
        long startedAt = aodLineFillTransitionStartedAtMs;
        if (startedAt < 0L) {
            return aodLineFillTransitionTargetAmount;
        }
        float rawProgress = Math.max(
                0f,
                Math.min(1f, (now - startedAt) / (float) AOD_WORD_PROGRESS_TO_LINE_ANIMATION_MS));
        if (rawProgress >= 1f) {
            aodLineFillTransitionStartedAtMs = -1L;
            aodLineFillTransitionStartAmount = aodLineFillTransitionTargetAmount;
            return aodLineFillTransitionTargetAmount;
        }
        float eased = smoothStep(rawProgress);
        return aodLineFillTransitionStartAmount
                + (aodLineFillTransitionTargetAmount - aodLineFillTransitionStartAmount) * eased;
    }

    private boolean isAodLineFillTransitionRunning() {
        return aodLineFillTransitionStartedAtMs >= 0L;
    }

    /**
     * Capability record for word-timed fill (diagnostics only; the draw decision is already
     * made). Line-timed lines and the whole-line highlight of very short single-word lines
     * are not word fill and are not recorded.
     */
    private void recordWordProgress(WordLine line, boolean activeLine, boolean timestampHighlight, boolean drawProgress) {
        if (activeLine && !timestampHighlight && line != null && line.timingMode == LyricTimingMode.WORD_TIMED) {
            CompatibilityStatus.global().wordProgress(line, drawProgress, aodLowFrameRateMode);
        }
    }

    private boolean shouldDrawWordProgressForVisual(WordLine line, float aodLineFillAmount) {
        return shouldDrawWordProgress(line)
                || (aodLineFillAmount < 0.999f
                && canDrawWordProgress(line));
    }

    private boolean shouldDrawWordProgress(WordLine line) {
        return !aodLowFrameRateMode
                && line != null
                && (line.timingMode != LyricTimingMode.LINE_TIMED
                || lineTimedProgressEnabled);
    }

    private boolean canDrawWordProgress(WordLine line) {
        return line != null
                && line.words != null
                && !line.words.isEmpty()
                && (line.timingMode != LyricTimingMode.LINE_TIMED
                || lineTimedProgressEnabled);
    }

    private void drawMainLineWindow(
            Canvas canvas,
            TextView textView,
            WordLyricModel model,
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
            boolean drawProgress,
            float fullLineOverlayAmount,
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
        beginCharLiftLine(
                model,
                line,
                text,
                activeWord,
                wordIndex,
                position,
                activeLine,
                drawProgress,
                fullLineOverlayAmount,
                start,
                canvas.getHeight(),
                dp(
                        textView.getContext(),
                        WordLyricRenderConstants.CHAR_LIFT_MIN_CLEARANCE_DP));
        float lineY = y;
        for (int i = start; i < end; i++) {
            LyricDrawLine drawLine = drawLines.get(i);
            float segmentWidth = drawLine.width;
            float x = resolveTextX(
                    textView,
                    text,
                    drawLine.start,
                    drawLine.end,
                    inactivePaint,
                    segmentWidth,
                    availableWidth);
            drawSegment(
                    canvas,
                    model,
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
                    activeLine,
                    drawProgress,
                    fullLineOverlayAmount);
            charLift.flowSegmentStart += drawLine.width;
            lineY += lineHeight + lineGap;
        }
        // Upcoming rows already hold their unsung geometry and need no
        // animation loop. Only the current row follows the reveal and its
        // bounded finish overshoot.
        if (charLift.lineActive && charLift.finishing) {
            textView.postInvalidateOnAnimation();
        }
    }

    /**
     * Line-level lift state for one {@link #drawMainLineWindow} pass. The
     * reveal front is resolved once per line in flow coordinates (advance
     * width accumulated across wrapped segments) because a per-segment front
     * pins itself to the right edge of any segment that sits entirely before
     * the active word, which would park that segment's trailing grapheme at
     * the envelope peak for the rest of the row.
     */
    private void beginCharLiftLine(
            WordLyricModel model,
            WordLine line,
            String text,
            WordRange activeWord,
            int wordIndex,
            long position,
            boolean activeLine,
            boolean drawProgress,
            float fullLineOverlayAmount,
            int windowStart,
            float canvasHeight,
            float minClearance) {
        charLift.reset();
        boolean liftDegraded = !FeatureDegradation.global().allowed(CompatibilityStatus.Feature.CHAR_LIFT);
        if (!charLiftEnabled
                || charLiftUnavailable
                || liftDegraded
                // drawProgress alone does not rule out AOD: the low frame
                // rate path still draws word progress while the fill
                // transition runs, and an animation there costs battery for
                // motion nobody is watching.
                || aodLowFrameRateMode
                || line == null
                || (line.timingMode != LyricTimingMode.WORD_TIMED
                && (line.timingMode != LyricTimingMode.LINE_TIMED
                || !lineTimedProgressEnabled))
                || line.words == null
                || line.words.isEmpty()
                || !CharLiftGeometry.shouldPrepareLine(
                activeLine, drawProgress, position,
                Math.max(line.timeMillis, line.words.get(0).timeMillis), rowUpcoming)
                || TextUtils.isEmpty(text)
                // The cross-fade overlay repaints the whole row undisplaced,
                // so sinking underneath it would only ghost.
                || fullLineOverlayAmount > 0.001f
                || WordLyricRenderSupport.shouldUseTimestampHighlight(model, line)) {
            if (liftDegraded && charLiftEnabled) {
                CompatibilityStatus.global().degraded(CompatibilityStatus.Feature.CHAR_LIFT, line,
                        CompatibilityStatus.Path.CUSTOM, CompatibilityStatus.Scope.CHAR_LIFT_GATE);
                return;
            }
            CompatibilityStatus.global().liftBlocked(line, charLiftEnabled, charLiftUnavailable,
                    aodLowFrameRateMode, line == null || TextUtils.isEmpty(text),
                    line != null && line.timingMode == LyricTimingMode.LINE_TIMED && !lineTimedProgressEnabled);
            return;
        }
        try {
            float textSize = inactivePaint.getTextSize();
            float maxSink = textSize
                    * WordLyricRenderConstants.CHAR_LIFT_MAX_FACTOR
                    * charLiftStrengthPercent
                    / 100f;
            if (maxSink <= CHAR_LIFT_FRAME_EPSILON) {
                return;
            }
            float waveWidth =
                    textSize * WordLyricRenderConstants.CHAR_LIFT_WAVE_WIDTH_FACTOR;
            // Upcoming rows and the active row's pre-roll share the same
            // zero-progress geometry. Activation must not sink the row.
            float revealFront = activeWord == null
                    ? 0f
                    : resolveLineFlowFront(model, line, text, activeWord, wordIndex, position);
            long lineRevealEndMillis = resolveCharLiftLineRevealEnd(model, line);
            charLift.flowFront = revealFront
                    + CharLiftGeometry.finishOvershoot(
                    position,
                    lineRevealEndMillis,
                    WordLyricRenderConstants.CHAR_LIFT_FINISH_MS,
                    waveWidth);
            charLift.finishing = activeLine && position < lineRevealEndMillis
                    + WordLyricRenderConstants.CHAR_LIFT_FINISH_MS;
            charLift.flowSegmentStart = flowOffsetBeforeSegment(windowStart);
            charLift.waveWidth = waveWidth;
            charLift.maxSink = maxSink;
            charLift.floorY = charLiftFloorY > 0f ? charLiftFloorY : canvasHeight;
            charLift.minClearance = minClearance;
            charLift.lineActive = true;
            CompatibilityStatus.global().liftPrepared(line);
        } catch (RuntimeException error) {
            StructuredBridgeLog.warn(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.CHAR_LIFT_UNAVAILABLE,
                    "Character lift disabled until the next cache reset: " + error);
            charLiftUnavailable = true;
            FeatureDegradation.global().failed(CompatibilityStatus.Feature.CHAR_LIFT, error.getClass().getSimpleName());
            charLift.reset();
        }
    }

    /**
     * Resolves the endpoint of the reveal that the lift front follows. A
     * line-timed row has one synthetic whole-row range, but its linear
     * reveal finishes just before the display handoff; it never uses an
     * inferred last-word duration.
     */
    private long resolveCharLiftLineRevealEnd(WordLyricModel model, WordLine line) {
        if (line.timingMode == LyricTimingMode.LINE_TIMED) {
            return lineTimedProgressEnabled
                    ? LineTimedRevealPolicy.revealEnd(line.timeMillis, resolveLineDisplayEndMillis(model, line))
                    : resolveLineDisplayEndMillis(model, line);
        }
        return WordLyricRenderSupport.wordRevealEndMillis(
                model,
                line,
                line.words.size() - 1);
    }

    /**
     * Flow coordinate of the reveal front: the total revealed advance across
     * the wrapped segments. A segment before the active word reports its
     * full width, the segment holding the front reports a partial width, and
     * later segments report zero, so the sum is the front itself and it
     * stays monotonic across a wrap.
     */
    private float resolveLineFlowFront(
            WordLyricModel model,
            WordLine line,
            String text,
            WordRange activeWord,
            int wordIndex,
            long position) {
        // Sum, never max: a segment after the front reports zero reveal,
        // and its flow offset alone would otherwise pin the front to the end
        // of every segment before it (observed on device: the wave parked
        // at the end of the first wrapped line for the whole line).
        float front = 0f;
        for (int i = 0; i < drawLines.size(); i++) {
            LyricDrawLine candidate = drawLines.get(i);
            front = CharLiftGeometry.accumulateFlowFront(
                    front,
                    resolveWordRevealWidthForSegment(
                            model,
                            line,
                            text,
                            candidate,
                            activeWord,
                            wordIndex,
                            position,
                            candidate.width),
                    candidate.width);
        }
        return front;
    }

    private float flowOffsetBeforeSegment(int windowStart) {
        float flow = 0f;
        int limit = Math.max(0, Math.min(windowStart, drawLines.size()));
        for (int i = 0; i < limit; i++) {
            flow += drawLines.get(i).width;
        }
        return flow;
    }

    private static float smoothStep(float progress) {
        float value = Math.max(0f, Math.min(1f, progress));
        return value * value * (3f - 2f * value);
    }

    private static float interpolate(float from, float to, float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        return from + (to - from) * clamped;
    }

    private static float smootherStep(float progress) {
        float value = Math.max(0f, Math.min(1f, progress));
        return value * value * value * (value * (value * 6f - 15f) + 10f);
    }

    private void drawCompactLine(
            Canvas canvas,
            TextView textView,
            WordLyricModel model,
            WordLine line,
            long position,
            long glowPosition,
            boolean activeLine,
            float focusAmount,
            float aodLineFillAmount,
            float availableWidth,
            float availableHeight) {
        float originalSize = inactivePaint.getTextSize();
        fitSingleLineText(textView, line.text, availableWidth, originalSize, sp(textView.getContext(), 10f));
        applyFade(1f, focusAmount);

        inactivePaint.getFontMetrics(mainFontMetrics);
        Paint.FontMetrics metrics = mainFontMetrics;
        float outerHeight = LyricUiLayoutPolicy.fontOuterHeight(
                metrics.top,
                metrics.bottom);
        float y = textView.getPaddingTop()
                + Math.max(0f, (availableHeight - outerHeight) / 2f)
                - LyricUiLayoutPolicy.outwardFontTop(metrics.top);
        float compactWidth = inactivePaint.measureText(line.text);
        float x = resolveTextX(
                textView,
                line.text,
                0,
                line.text.length(),
                inactivePaint,
                compactWidth,
                availableWidth);

        int compactSave = canvas.save();
        try {
            canvas.clipRect(
                    textView.getPaddingLeft(),
                    0f,
                    textView.getWidth() - textView.getPaddingRight(),
                    textView.getHeight());
            boolean timestampHighlight = WordLyricRenderSupport.shouldUseTimestampHighlight(
                model,
                line);
        boolean drawProgress = activeLine && !timestampHighlight && shouldDrawWordProgressForVisual(
                line,
                aodLineFillAmount);
        recordWordProgress(line, activeLine, timestampHighlight, drawProgress);
            if (drawProgress) {
                drawProgressLine(canvas, model, line, position, glowPosition, x, y);
                drawFullLineOverlayIfNeeded(
                    canvas,
                    line.text,
                    0,
                    line.text.length(),
                    x,
                    y,
                    aodLineFillAmount);
            } else if (activeLine && timestampHighlight) {
                canvas.drawText(line.text, x, y, inactivePaint);
                drawProgressGlow(
                    canvas,
                    line,
                    line.text,
                    0,
                    line.text.length(),
                    x,
                    y,
                    compactWidth,
                    compactWidth);
                canvas.drawText(line.text, x, y, activePaint);
            } else {
            // The active line keeps full-opacity highlight even when it has no word
            // timing to draw a reveal for. In AOD low frame rate this branch is only
            // reached once the fill transition completes or for wordless lines, which
            // preserves the AOD fill behavior.
                canvas.drawText(
                        line.text,
                        x,
                        y,
                        activeLine ? activePaint : inactivePaint);
            }
        } finally {
            canvas.restoreToCount(compactSave);
            if (inactivePaint.getTextSize() != originalSize) {
                setTextSize(originalSize);
            }
        }
    }

    private void drawProgressLine(
            Canvas canvas,
            WordLyricModel model,
            WordLine line,
            long position,
            long glowPosition,
            float x,
            float y) {
        canvas.drawText(line.text, x, y, inactivePaint);
        if (isLineTimedProgress(line)) {
            float width = inactivePaint.measureText(line.text);
            float reveal = width * resolveLineTimedRevealProgress(model, line, position);
            drawProgressGlow(canvas, line, line.text, 0, line.text.length(), x, y, width, reveal);
            drawRevealedText(canvas, line.text, 0, line.text.length(), x, y, width, reveal);
            return;
        }
        int wordIndex = line.findWordIndex(position);
        if (wordIndex < 0 || wordIndex >= line.words.size()) {
            // Word-timed intro: keep the inactive base until the first word
            // actually starts. Filling the whole line here made karaoke snap
            // backwards from a full highlight to word 0.
            if (line.timingMode != LyricTimingMode.WORD_TIMED) {
                canvas.drawText(line.text, x, y, activePaint);
            }
            return;
        }
        WordRange activeWord = line.words.get(wordIndex);
        float revealWidth = inactivePaint.measureText(line.text, 0, Math.max(0, Math.min(activeWord.start, line.text.length())));
        int start = Math.max(0, Math.min(activeWord.start, line.text.length()));
        int end = Math.max(start, Math.min(activeWord.end, line.text.length()));
        revealWidth += inactivePaint.measureText(line.text, start, end)
                * resolveWordRevealProgress(model, line, wordIndex, position);
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
                        * resolveWordRevealProgress(model, line, glowWordIndex, glowPosition);
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
                x,
                y,
                segmentWidth,
                glowRevealWidth);
        drawRevealedText(
                canvas,
                line.text,
                0,
                line.text.length(),
                x,
                y,
                segmentWidth,
                revealWidth);
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
            WordLyricModel model,
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
            boolean activeLine,
            boolean drawProgress,
            float fullLineOverlayAmount) {
        boolean timestampHighlight = activeLine
                && !drawProgress
                && WordLyricRenderSupport.shouldUseTimestampHighlight(model, line);
        TextPaint basePaint = timestampHighlight
                ? inactivePaint
                : activeLine && !drawProgress ? activePaint : inactivePaint;
        float segmentWidth = drawLine.width;
        float revealWidth;
        boolean lifted;
        boolean segmentPrepTraced = BridgePerfTrace.begin("CLL:lyricSegmentPrepare");
        try {
            revealWidth = drawProgress && (activeWord != null || isLineTimedProgress(line))
                    ? resolveWordRevealWidthForSegment(
                    model,
                    line,
                    text,
                    drawLine,
                    activeWord,
                    wordIndex,
                    position,
                    segmentWidth)
                    : 0f;
            lifted = charLift.lineActive
                    && prepareCharLiftZone(line, text, drawLine, x, segmentWidth);
        } finally {
            BridgePerfTrace.end(segmentPrepTraced);
        }
        boolean baseTraced = BridgePerfTrace.begin(lifted
                ? "CLL:lyricBaseLifted"
                : "CLL:lyricBasePlain");
        try {
            if (lifted) {
                drawTextOutsideCharLiftZone(canvas, text, drawLine, x, y, basePaint);
                drawLiftedGraphemes(
                        canvas,
                        text,
                        drawLine,
                        x,
                        y,
                        basePaint,
                        segmentWidth,
                        revealWidth,
                        false);
                CompatibilityStatus.global().liftDrawn(line, charLiftUnavailable);
            } else {
                canvas.drawText(text, drawLine.start, drawLine.end, x, y, basePaint);
            }
        } finally {
            BridgePerfTrace.end(baseTraced);
        }
        if (!drawProgress) {
            if (timestampHighlight) {
                drawProgressGlow(
                        canvas,
                        line,
                        text,
                        drawLine.start,
                        drawLine.end,
                        x,
                        y,
                        drawLine.width,
                        drawLine.width);
                canvas.drawText(
                        text,
                        drawLine.start,
                        drawLine.end,
                        x,
                        y,
                        activePaint);
            }
            return;
        }

        if (activeWord == null && !isLineTimedProgress(line)) {
            if (activeLine && line.timingMode != LyricTimingMode.WORD_TIMED) {
                if (fullLineOverlayAmount > 0.001f) {
                    drawFullLineOverlayIfNeeded(
                            canvas,
                            text,
                            drawLine.start,
                            drawLine.end,
                            x,
                            y,
                            fullLineOverlayAmount);
                } else {
                    canvas.drawText(text, drawLine.start, drawLine.end, x, y, activePaint);
                }
            }
            return;
        }

        float glowRevealWidth = revealWidth;
        if (glowPosition != position) {
            glowRevealWidth = resolveWordRevealWidthForSegment(
                    model,
                    line,
                    text,
                    drawLine,
                    glowActiveWord,
                    glowWordIndex,
                    glowPosition,
                    segmentWidth);
        }
        if (glowRevealWidth > 0f) {
            boolean glowTraced = BridgePerfTrace.begin("CLL:lyricProgressGlow");
            try {
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
            } finally {
                BridgePerfTrace.end(glowTraced);
            }
        }
        if (revealWidth > 0f) {
            if (lifted) {
                boolean revealLiftTraced = BridgePerfTrace.begin("CLL:lyricRevealLifted");
                try {
                    drawRevealedTextOutsideCharLiftZone(
                            canvas,
                            text,
                            drawLine,
                            x,
                            y,
                            segmentWidth,
                            revealWidth);
                    drawLiftedGraphemes(
                            canvas,
                            text,
                            drawLine,
                            x,
                            y,
                            basePaint,
                            segmentWidth,
                            revealWidth,
                            true);
                } finally {
                    BridgePerfTrace.end(revealLiftTraced);
                }
            } else {
                boolean revealTraced = BridgePerfTrace.begin("CLL:lyricRevealedText");
                try {
                    drawRevealedText(
                            canvas,
                            text,
                            drawLine.start,
                            drawLine.end,
                            x,
                            y,
                            segmentWidth,
                            revealWidth);
                } finally {
                    BridgePerfTrace.end(revealTraced);
                }
            }
        }
        if (activeLine) {
            drawFullLineOverlayIfNeeded(
                    canvas,
                    text,
                    drawLine.start,
                    drawLine.end,
                    x,
                    y,
                    fullLineOverlayAmount);
        }
    }

    private void drawFullLineOverlayIfNeeded(
            Canvas canvas,
            String text,
            int start,
            int end,
            float x,
            float y,
            float amount) {
        if (amount <= 0.001f
                || TextUtils.isEmpty(text)
                || start >= end) {
            return;
        }
        int originalColor = activePaint.getColor();
        activePaint.setColor(scaleAlpha(originalColor, amount));
        canvas.drawText(text, start, end, x, y, activePaint);
        activePaint.setColor(originalColor);
    }

    /**
     * Revealed width of {@code drawLine} for one word, including the cheap
     * fully-before / fully-after answers. Shared by the draw path and by
     * {@link #resolveLineFlowFront} so the lift and the clip reveal can
     * never disagree about where the front is.
     */
    private float resolveWordRevealWidthForSegment(
            WordLyricModel model,
            WordLine line,
            String text,
            LyricDrawLine drawLine,
            WordRange word,
            int wordIndex,
            long position,
            float segmentWidth) {
        if (isLineTimedProgress(line)) {
            float total = 0f;
            float preceding = 0f;
            for (LyricDrawLine row : drawLines) {
                total += row.width;
                if (row.end <= drawLine.start) preceding += row.width;
            }
            return LineTimedRevealPolicy.segmentReveal(total, preceding, segmentWidth,
                    resolveLineTimedRevealProgress(model, line, position));
        }
        if (word == null) {
            return 0f;
        }
        if (drawLine.end <= word.start) {
            return segmentWidth;
        }
        if (drawLine.start >= word.end) {
            return 0f;
        }
        return resolveSegmentRevealWidth(
                model,
                line,
                text,
                drawLine,
                word,
                wordIndex,
                position);
    }

    /**
     * Splits this segment into the sung head, the transition the wave is
     * crossing, and the unsung tail, in canvas x. Returns false when the
     * whole segment is already sung, in which case the caller keeps the
     * untouched whole-segment draw — which is also what makes a settled row
     * pixel-identical to the effect being off.
     */
    private boolean prepareCharLiftZone(
            WordLine line,
            String text,
            LyricDrawLine drawLine,
            float x,
            float segmentWidth) {
        if (segmentWidth <= 0f || drawLine.start >= drawLine.end) {
            return false;
        }
        try {
            float flowSegmentStart = charLift.flowSegmentStart;
            float flowSegmentEnd = flowSegmentStart + segmentWidth;
            float zoneStart = CharLiftGeometry.waveZoneStart(
                    charLift.flowFront,
                    charLift.waveWidth,
                    flowSegmentStart,
                    flowSegmentEnd);
            float zoneEnd = CharLiftGeometry.waveZoneEnd(
                    charLift.flowFront,
                    charLift.waveWidth,
                    flowSegmentStart,
                    flowSegmentEnd);
            charLift.segmentLeft = x;
            charLift.segmentRight = x + segmentWidth;
            charLift.prefixWidths = null;
            charLift.runBoundaries = null;
            charLift.runAdvances = null;
            charLift.runSafe = null;
            charLift.firstGrapheme = 0;
            charLift.lastGrapheme = 0;
            if (zoneEnd <= zoneStart) {
                // No transition here: the segment is entirely sung or
                // entirely unsung. Only the latter still needs drawing work.
                float edge = x + (zoneEnd - flowSegmentStart);
                charLift.zoneLeft = edge;
                charLift.zoneRight = edge;
                return charLift.hasTail();
            }
            float[] prefixWidths = resolveGraphemePrefixWidths(line, text, drawLine);
            if (prefixWidths == null || prefixWidths.length < 2) {
                return false;
            }
            float localStart = zoneStart - flowSegmentStart;
            float localEnd = zoneEnd - flowSegmentStart;
            int first = -1;
            int last = -1;
            for (int i = 0; i + 1 < prefixWidths.length; i++) {
                if (prefixWidths[i + 1] <= localStart) {
                    continue;
                }
                if (prefixWidths[i] >= localEnd) {
                    break;
                }
                if (first < 0) {
                    first = i;
                }
                last = i + 1;
            }
            if (first < 0 || last <= first) {
                return false;
            }
            charLift.prefixWidths = prefixWidths;
            GraphemeLayoutCache layout = lastGraphemeLayout;
            if (layout != null && layout.prefixWidths == prefixWidths && layout.ltrOnly) {
                charLift.runBoundaries = layout.boundaries;
                charLift.runAdvances = layout.runAdvances;
                charLift.runSafe = layout.runSafe;
            }
            charLift.firstGrapheme = first;
            charLift.lastGrapheme = last;
            charLift.zoneLeft = x + prefixWidths[first];
            charLift.zoneRight = x + prefixWidths[last];
            return true;
        } catch (RuntimeException error) {
            StructuredBridgeLog.warn(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.CHAR_LIFT_UNAVAILABLE,
                    "Character lift disabled until the next cache reset: " + error);
            charLiftUnavailable = true;
            FeatureDegradation.global().failed(CompatibilityStatus.Feature.CHAR_LIFT, error.getClass().getSimpleName());
            return false;
        }
    }

    /** Sink shared by every grapheme past the wave, clamped to the floor. */
    private float resolveTailSink(float y) {
        return CharLiftGeometry.clampSink(
                charLift.maxSink,
                y + inactivePaint.descent(),
                charLift.floorY,
                charLift.minClearance);
    }

    /**
     * Draws the sung head at the normal baseline and the unsung tail
     * uniformly sunk. The tail is one clipped draw rather than a per-grapheme
     * walk: past the wave every grapheme carries the same displacement.
     */
    private void drawTextOutsideCharLiftZone(
            Canvas canvas,
            String text,
            LyricDrawLine drawLine,
            float x,
            float y,
            TextPaint paint) {
        float canvasWidth = canvas.getWidth();
        float canvasHeight = canvas.getHeight();
        if (charLift.hasHead()) {
            int save = canvas.save();
            try {
                canvas.clipRect(0f, 0f, charLift.zoneLeft, canvasHeight);
                canvas.drawText(text, drawLine.start, drawLine.end, x, y, paint);
            } finally {
                canvas.restoreToCount(save);
            }
        }
        if (charLift.hasTail()) {
            float sink = resolveTailSink(y);
            int save = canvas.save();
            try {
                canvas.clipRect(charLift.zoneRight, 0f, canvasWidth, canvasHeight);
                if (sink > 0f) {
                    canvas.translate(0f, sink);
                }
                canvas.drawText(text, drawLine.start, drawLine.end, x, y, paint);
            } finally {
                canvas.restoreToCount(save);
            }
        }
    }

    /**
     * The revealed layer only ever reaches the sung head: the reveal front is
     * the centre of the wave, so nothing past the wave has been revealed.
     */
    private void drawRevealedTextOutsideCharLiftZone(
            Canvas canvas,
            String text,
            LyricDrawLine drawLine,
            float x,
            float y,
            float segmentWidth,
            float revealWidth) {
        if (!charLift.hasHead()) {
            return;
        }
        int save = canvas.save();
        try {
            canvas.clipRect(0f, 0f, charLift.zoneLeft, canvas.getHeight());
            drawRevealedText(
                    canvas,
                    text,
                    drawLine.start,
                    drawLine.end,
                    x,
                    y,
                    segmentWidth,
                    revealWidth);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /**
     * Repaints the transition one grapheme at a time. Every pass draws the
     * whole segment string at its original x and lets the clip box select
     * one grapheme, so shaping and kerning stay identical to the whole
     * segment draw and the glyph is only displaced.
     *
     * <p>The clip boxes tile exactly, without the bleed the plan first
     * proposed: overlapping boxes would blend the same partly transparent
     * ink twice and darken the seam, which is worse than the hairline it
     * would hide.
     *
     * <p>{@code revealedLayer} selects the layer being repainted. Base and
     * revealed text are two separate passes so the progress glow keeps its
     * original place between them.
     */
    /**
     * Restricts the next segment draws to the graphemes around {@code grapheme}. The caller clips
     * to that grapheme, so farther glyphs are fully clipped; the window keeps two neighbours on
     * each side for overhang and shapes with the whole segment as context, so the visible glyphs
     * and their positions match a full-segment draw.
     */
    private void beginGlyphRunWindow(LyricDrawLine drawLine, float x, int grapheme) {
        int[] boundaries = charLift.runBoundaries;
        float[] advances = charLift.runAdvances;
        boolean[] safe = charLift.runSafe;
        if (boundaries == null || advances == null || safe == null
                || boundaries.length != advances.length || boundaries.length != safe.length) {
            glyphRunStart = -1;
            return;
        }
        int startIndex = Math.max(0, grapheme - GLYPH_RUN_NEIGHBOURS);
        while (startIndex > 0 && !safe[startIndex]) {
            startIndex--;
        }
        int endIndex = Math.min(boundaries.length - 1, grapheme + 1 + GLYPH_RUN_NEIGHBOURS);
        while (endIndex < boundaries.length - 1 && !safe[endIndex]) {
            endIndex++;
        }
        glyphRunContextStart = drawLine.start;
        glyphRunContextEnd = drawLine.end;
        glyphRunEnd = boundaries[endIndex];
        glyphRunX = x + advances[startIndex];
        glyphRunStart = boundaries[startIndex];
    }

    private void drawSegmentText(
            Canvas canvas, String text, int start, int end, float x, float y, Paint paint) {
        if (glyphRunStart >= 0
                && start == glyphRunContextStart
                && end == glyphRunContextEnd
                && glyphRunStart < glyphRunEnd) {
            canvas.drawTextRun(text, glyphRunStart, glyphRunEnd, start, end, glyphRunX, y, false, paint);
            return;
        }
        canvas.drawText(text, start, end, x, y, paint);
    }

    private void drawLiftedGraphemes(
            Canvas canvas,
            String text,
            LyricDrawLine drawLine,
            float x,
            float y,
            TextPaint basePaint,
            float segmentWidth,
            float revealWidth,
            boolean revealedLayer) {
        float canvasHeight = canvas.getHeight();
        float glyphBottomY = y + inactivePaint.descent();
        float[] prefixWidths = charLift.prefixWidths;
        if (prefixWidths == null) {
            return;
        }
        try {
            for (int i = charLift.firstGrapheme; i < charLift.lastGrapheme; i++) {
                float left = x + prefixWidths[i];
                float right = x + prefixWidths[i + 1];
                if (right <= left) {
                    continue;
                }
                float flowCenter = charLift.flowSegmentStart
                        + (prefixWidths[i] + prefixWidths[i + 1]) * 0.5f;
                float sink = CharLiftGeometry.clampSink(
                        CharLiftGeometry.sinkFor(
                                charLift.flowFront,
                                flowCenter,
                                charLift.waveWidth,
                                charLift.maxSink),
                        glyphBottomY,
                        charLift.floorY,
                        charLift.minClearance);
                int save = canvas.save();
                try {
                    beginGlyphRunWindow(drawLine, x, i);
                    canvas.clipRect(left, 0f, right, canvasHeight);
                    if (sink > 0f) {
                        canvas.translate(0f, sink);
                        // Mid-transition only: defeat Skia's whole-pixel
                        // baseline snapping so a slow rise (held note) is
                        // continuous instead of one-pixel steps. Fully sunk
                        // graphemes are static and keep the crisp path.
                        if (sink < charLift.maxSink - CHAR_LIFT_FRAME_EPSILON) {
                            canvas.rotate(
                                    WordLyricRenderConstants.CHAR_LIFT_SUBPIXEL_TILT_DEGREES,
                                    left,
                                    y);
                        }
                    }
                    if (revealedLayer) {
                        drawRevealedText(
                                canvas,
                                text,
                                drawLine.start,
                                drawLine.end,
                                x,
                                y,
                                segmentWidth,
                                revealWidth);
                    } else {
                        drawSegmentText(canvas, text, drawLine.start, drawLine.end, x, y, basePaint);
                    }
                } finally {
                    glyphRunStart = -1;
                    canvas.restoreToCount(save);
                }
            }
        } catch (RuntimeException error) {
            glyphRunStart = -1;
            // Never let this reach the coordinator: one draw error there
            // blacklists the binding and drops the row back to native
            // rendering permanently. Repaint the layer whole so the frame
            // stays complete, and stop lifting until the next cache reset.
            StructuredBridgeLog.warn(
                    BridgeDebugArea.RENDERER,
                    BridgeEvents.CHAR_LIFT_UNAVAILABLE,
                    "Character lift disabled until the next cache reset: " + error);
            charLiftUnavailable = true;
            FeatureDegradation.global().failed(CompatibilityStatus.Feature.CHAR_LIFT, error.getClass().getSimpleName());
            int save = canvas.save();
            try {
                if (revealedLayer) {
                    drawRevealedText(
                            canvas,
                            text,
                            drawLine.start,
                            drawLine.end,
                            x,
                            y,
                            segmentWidth,
                            revealWidth);
                } else {
                    canvas.drawText(text, drawLine.start, drawLine.end, x, y, basePaint);
                }
            } finally {
                canvas.restoreToCount(save);
            }
        }
    }

    /**
     * Advance width from {@code drawLine.start} to every grapheme boundary
     * in the segment, measured with the same paint the reveal width uses.
     * Cached per segment: the boundary walk and the prefix measurements are
     * far too expensive to repeat every frame.
     */
    private float[] resolveGraphemePrefixWidths(
            WordLine line,
            String text,
            LyricDrawLine drawLine) {
        if (line == null || TextUtils.isEmpty(text) || drawLine.start >= drawLine.end) {
            return null;
        }
        int textSizeKey = Math.max(1, Math.round(inactivePaint.getTextSize() * 10f));
        Typeface typeface = inactivePaint.getTypeface();
        GraphemeLayoutCache target = null;
        for (GraphemeLayoutCache cache : graphemeLayoutCaches) {
            if (cache.matches(
                    line, text, drawLine.start, drawLine.end, textSizeKey, typeface)) {
                cache.lastUsed = ++graphemeCacheUseCounter;
                lastGraphemeLayout = cache;
                return cache.prefixWidths;
            }
            if (target == null || cache.lastUsed < target.lastUsed) {
                target = cache;
            }
        }
        if (target == null) {
            return null;
        }
        int[] boundaries = CharLiftGeometry.graphemeBoundaries(
                text, drawLine.start, drawLine.end);
        if (boundaries.length < 2) {
            return null;
        }
        float[] prefixWidths = new float[boundaries.length];
        for (int i = 1; i < boundaries.length; i++) {
            prefixWidths[i] = inactivePaint.measureText(text, drawLine.start, boundaries[i]);
        }
        boolean ltrOnly = !java.text.Bidi.requiresBidi(
                text.toCharArray(), drawLine.start, drawLine.end);
        float[] runAdvances = null;
        boolean[] runSafe = null;
        if (ltrOnly) {
            int last = boundaries.length - 1;
            runAdvances = new float[boundaries.length];
            for (int i = 1; i < boundaries.length; i++) {
                runAdvances[i] = inactivePaint.getRunAdvance(
                        text, drawLine.start, drawLine.end,
                        drawLine.start, drawLine.end, false, boundaries[i]);
            }
            // A run starting inside a ligature (e.g. the "i" of "fi") is shaped differently
            // from the full segment and shifts every later glyph; only cut where the tail
            // shaped on its own spans exactly the remaining full-segment advance.
            runSafe = new boolean[boundaries.length];
            runSafe[0] = true;
            runSafe[last] = true;
            float total = runAdvances[last];
            for (int i = 1; i < last; i++) {
                float tail = inactivePaint.getRunAdvance(
                        text, boundaries[i], drawLine.end,
                        drawLine.start, drawLine.end, false, drawLine.end);
                runSafe[i] = Math.abs(runAdvances[i] + tail - total) < GLYPH_RUN_SAFE_EPSILON;
            }
        }
        target.boundaries = boundaries;
        target.runAdvances = runAdvances;
        target.runSafe = runSafe;
        target.ltrOnly = ltrOnly;
        lastGraphemeLayout = target;
        target.line = line;
        target.text = text;
        target.start = drawLine.start;
        target.end = drawLine.end;
        target.textSizeKey = textSizeKey;
        target.typeface = typeface;
        target.prefixWidths = prefixWidths;
        target.lastUsed = ++graphemeCacheUseCounter;
        return prefixWidths;
    }

    private float resolveSegmentRevealWidth(
            WordLyricModel model,
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
            return beforeActiveInSegment
                    + activeWidth * resolveWordRevealProgress(model, line, wordIndex, position);
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

        float revealedActiveWidth =
                totalActiveWidth * resolveWordRevealProgress(model, line, wordIndex, position);
        float revealedInsideSegment = Math.max(
                0f,
                Math.min(activeWidth, revealedActiveWidth - activeWidthBeforeSegment));
        return beforeActiveInSegment + revealedInsideSegment;
    }

    private float resolveWordRevealProgress(
            WordLyricModel model,
            WordLine line,
            int wordIndex,
            long position) {
        if (line == null) {
            return 0f;
        }
        if (line.timingMode == LyricTimingMode.LINE_TIMED && lineTimedProgressEnabled) {
            return resolveLineTimedRevealProgress(model, line, position);
        }
        return WordLyricRenderSupport.wordRevealProgress(model, line, wordIndex, position);
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
        drawProgressGlow(
                canvas,
                line,
                text,
                start,
                end,
                x,
                y,
                segmentWidth,
                revealWidth,
                activePaint);
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
            float revealWidth,
            TextPaint progressPaint) {
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
        float glowPad = Math.max(1f, progressPaint.getTextSize() * 0.22f);
        boolean fullyVisible = visibleWidth >= segmentWidth - 0.5f;
        int paintAlpha = (progressPaint.getColor() >>> 24) & 0xFF;

        GlowSegmentCache glowCache = obtainGlowSegmentCache(
                line,
                text,
                start,
                end,
                segmentWidth,
                glowPad,
                progressPaint);
        if (glowCache == null || glowCache.bitmap == null) {
            return;
        }
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

        // Keep the bloom a little behind and in front of the actual reveal point. The
        // foreground text uses the same wide, soft transition, so this reads as one wet
        // highlight instead of two independently moving vertical edges.
        float featherWidth = Math.max(
                6f,
                progressPaint.getTextSize() * GLOW_FEATHER_WIDTH_FACTOR);
        float featherStart = Math.max(
                segmentLeft - glowPad,
                revealRight - featherWidth * 0.58f);
        float featherEnd = Math.min(
                segmentRight + glowPad,
                revealRight + featherWidth * 0.78f);
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
        if (canvas.isHardwareAccelerated()) {
            drawContinuousGlowFeather(
                    canvas,
                    glowCache,
                    bitmapLeft,
                    bitmapTop,
                    featherStart,
                    featherEnd,
                    paintAlpha);
        } else {
            // Software canvases cannot reliably compose DST_IN through a saveLayer. Keep a
            // denser, eased fallback so they retain the same visual intent without seams.
            drawSoftwareGlowFeather(
                    canvas,
                    glowCache,
                    bitmapLeft,
                    bitmapTop,
                    featherStart,
                    featherEnd,
                    paintAlpha);
        }
        glowBitmapPaint.setAlpha(255);
    }

    private void drawContinuousGlowFeather(
            Canvas canvas,
            GlowSegmentCache glowCache,
            float bitmapLeft,
            float bitmapTop,
            float featherStart,
            float featherEnd,
            int paintAlpha) {
        if (featherEnd <= featherStart || paintAlpha <= 0) {
            return;
        }
        int layerSave = canvas.saveLayer(
                featherStart,
                0f,
                featherEnd,
                canvas.getHeight(),
                null);
        try {
            drawGlowBitmapBand(
                    canvas,
                    glowCache,
                    bitmapLeft,
                    bitmapTop,
                    featherStart,
                    featherEnd,
                    paintAlpha);
            float featherSpan = Math.max(1f, featherEnd - featherStart);
            glowFeatherMaskShaderMatrix.setScale(featherSpan, 1f);
            glowFeatherMaskShaderMatrix.postTranslate(featherStart, 0f);
            glowFeatherMaskShader.setLocalMatrix(glowFeatherMaskShaderMatrix);
            glowFeatherMaskPaint.setShader(glowFeatherMaskShader);
            glowFeatherMaskPaint.setXfermode(glowFeatherMaskXfermode);
            canvas.drawRect(
                    featherStart,
                    0f,
                    featherEnd,
                    canvas.getHeight(),
                    glowFeatherMaskPaint);
        } finally {
            glowFeatherMaskPaint.setXfermode(null);
            glowFeatherMaskPaint.setShader(null);
            canvas.restoreToCount(layerSave);
        }
    }

    private void drawSoftwareGlowFeather(
            Canvas canvas,
            GlowSegmentCache glowCache,
            float bitmapLeft,
            float bitmapTop,
            float featherStart,
            float featherEnd,
            int paintAlpha) {
        for (int band = 0; band < SOFTWARE_GLOW_FEATHER_BANDS; band++) {
            float startAmount = band / (float) SOFTWARE_GLOW_FEATHER_BANDS;
            float endAmount = (band + 1f) / SOFTWARE_GLOW_FEATHER_BANDS;
            float bandLeft = featherStart
                    + (featherEnd - featherStart) * startAmount;
            float bandRight = featherStart
                    + (featherEnd - featherStart) * endAmount;
            float alphaAmount = 1f - smootherStep(
                    (band + 0.5f) / SOFTWARE_GLOW_FEATHER_BANDS);
            drawGlowBitmapBand(
                    canvas,
                    glowCache,
                    bitmapLeft,
                    bitmapTop,
                    bandLeft,
                    bandRight,
                    Math.round(paintAlpha * alphaAmount));
        }
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
        try {
            canvas.clipRect(clipLeft, 0f, clipRight, canvas.getHeight());
            canvas.drawBitmap(glowCache.bitmap, bitmapLeft, bitmapTop, glowBitmapPaint);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private GlowSegmentCache obtainGlowSegmentCache(
            WordLine line,
            String text,
            int start,
            int end,
            float segmentWidth,
            float glowPad,
            TextPaint progressPaint) {
        if (line == null || TextUtils.isEmpty(text) || start >= end) {
            return null;
        }
        int textSizeKey = Math.max(1, Math.round(progressPaint.getTextSize() * 10f));
        int widthKey = Math.max(1, Math.round(segmentWidth));
        Typeface typeface = progressPaint.getTypeface();
        GlowSegmentCache target = null;
        for (GlowSegmentCache cache : glowSegmentCaches) {
            if (cache.matches(line, text, start, end, textSizeKey, widthKey, typeface)) {
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

        if (!uiConfig.glowEnabled || uiConfig.glowIntensityPercent <= 0) {
            return null;
        }
        float glowRadius = Math.max(
                2f,
                progressPaint.getTextSize()
                        * uiConfig.glowRadiusPercent
                        / 100f
                        * SETTLED_GLOW_RADIUS_FACTOR);
        int radiusKey = Math.max(1, Math.round(glowRadius * 10f));
        if (glowMaskFilter == null || glowMaskRadiusKey != radiusKey) {
            glowMaskFilter = new BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL);
            glowMaskRadiusKey = radiusKey;
        }
        int padding = Math.max(2, (int) Math.ceil(Math.max(glowPad, glowRadius * 2.2f)));
        glowRasterPaint.setTypeface(typeface);
        glowRasterPaint.setTextSize(progressPaint.getTextSize());
        glowRasterPaint.setStyle(Paint.Style.FILL);
        glowRasterPaint.clearShadowLayer();
        glowRasterPaint.setShader(null);
        glowRasterPaint.setColorFilter(null);
        glowRasterPaint.getFontMetrics(glowFontMetrics);
        float baseline = padding
                - LyricUiLayoutPolicy.outwardFontTop(glowFontMetrics.top);
        int requiredWidth = Math.max(1, (int) Math.ceil(segmentWidth) + padding * 2 + 2);
        int requiredHeight = Math.max(
                1,
                (int) LyricUiLayoutPolicy.fontOuterHeight(
                        glowFontMetrics.top,
                        glowFontMetrics.bottom)
                        + padding * 2
                        + 2);
        boolean glowTraced = BridgePerfTrace.begin("CLL:lyricGlowRaster");
        try {
            target.ensureBitmap(requiredWidth, requiredHeight);
            if (target.bitmap == null || target.canvas == null) {
                return null;
            }
            target.bitmap.eraseColor(0);
            int rasterSave = target.canvas.save();
            try {
                target.canvas.clipRect(0, 0, requiredWidth, requiredHeight);
                glowRasterPaint.setColor(scaleAlpha(
                        palette.glowShadow,
                        SETTLED_GLOW_ALPHA_FACTOR));
                glowRasterPaint.setMaskFilter(glowMaskFilter);
                target.canvas.drawText(text, start, end, padding, baseline, glowRasterPaint);
            } finally {
                glowRasterPaint.setMaskFilter(null);
                target.canvas.restoreToCount(rasterSave);
            }
        } finally {
            BridgePerfTrace.end(glowTraced);
        }
        // The opaque active glyph is drawn immediately after this cached bloom. Do not add
        // a second unblurred fill underneath it: its anti-aliased edge is what made the
        // settled highlight look like it had a visible outline.

        target.line = line;
        target.text = text;
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
        for (GraphemeLayoutCache cache : graphemeLayoutCaches) {
            cache.clear();
        }
        charLift.reset();
        lastGraphemeLayout = null;
        glyphRunStart = -1;
        charLiftUnavailable = false;
        clearRenderableTranslationCache();
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
        drawRevealedText(
                canvas,
                text,
                start,
                end,
                x,
                y,
                segmentWidth,
                revealWidth,
                activePaint,
                activeFeatherPaint);
    }

    private LinearGradient resolveActiveFeatherShader() {
        String primaryColor = uiConfig.primaryColor;
        if (activeFeatherShader == null
                || !primaryColor.equals(activeFeatherPrimaryColor)) {
            activeFeatherShader = new LinearGradient(
                    0f,
                    0f,
                    1f,
                    0f,
                    palette.activeFeatherColors,
                    new float[]{0f, 0.13f, 0.32f, 0.55f, 0.76f, 0.92f, 1f},
                    Shader.TileMode.CLAMP);
            activeFeatherPrimaryColor = primaryColor;
        }
        return activeFeatherShader;
    }

    private void drawRevealedText(
            Canvas canvas,
            String text,
            int start,
            int end,
            float x,
            float y,
            float segmentWidth,
            float revealWidth,
            TextPaint revealPaint,
            TextPaint featherPaint) {
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
            int fullSave = canvas.save();
            try {
                canvas.clipRect(segmentLeft, 0f, segmentRight, canvas.getHeight());
                drawSegmentText(canvas, text, start, end, x, y, revealPaint);
            } finally {
                canvas.restoreToCount(fullSave);
            }
            return;
        }

        float featherWidth = Math.max(1f, revealPaint.getTextSize() * ACTIVE_FEATHER_WIDTH_FACTOR);
        float featherStart = Math.max(segmentLeft, revealRight - featherWidth * 0.65f);
        float featherEnd = Math.min(segmentRight, revealRight + featherWidth * 0.85f);
        if (featherEnd <= segmentLeft) {
            return;
        }
        if (featherStart > segmentLeft) {
            int solidSave = canvas.save();
            try {
                canvas.clipRect(segmentLeft, 0f, featherStart, canvas.getHeight());
                drawSegmentText(canvas, text, start, end, x, y, revealPaint);
            } finally {
                canvas.restoreToCount(solidSave);
            }
        }

        float featherSpan = Math.max(1f, featherEnd - featherStart);
        activeFeatherShaderMatrix.setScale(featherSpan, 1f);
        activeFeatherShaderMatrix.postTranslate(featherStart, 0f);
        LinearGradient featherShader = resolveActiveFeatherShader();
        featherShader.setLocalMatrix(activeFeatherShaderMatrix);
        featherPaint.setAlpha((revealPaint.getColor() >>> 24) & 0xFF);
        featherPaint.setShader(featherShader);
        int featherSave = canvas.save();
        try {
            canvas.clipRect(featherStart, 0f, featherEnd, canvas.getHeight());
            drawSegmentText(canvas, text, start, end, x, y, featherPaint);
        } finally {
            canvas.restoreToCount(featherSave);
            featherPaint.setShader(null);
        }
    }

    private void buildDrawLines(
            WordLine line,
            String text,
            float availableWidth,
            boolean singleLine,
            boolean balanceUntranslatedText) {
        int textSizeKey = Math.max(1, Math.round(inactivePaint.getTextSize() * 10f));
        boolean traced = BridgePerfTrace.begin("CLL:lyricLayoutBuild");
        try {
            drawLayoutEngine.build(
                    line,
                    text,
                    availableWidth,
                    singleLine,
                    balanceUntranslatedText,
                    textSizeKey,
                    inactivePaint.getTypeface());
        } finally {
            BridgePerfTrace.end(traced);
        }
    }

    private static boolean isCompactLyricSlot(TextView textView, float availableHeight) {
        return availableHeight <= dp(textView.getContext(), 52f);
    }

    /** Returns true when the typeface changed and measured rows must be refreshed. */
    boolean setOfficialBaseTypeface(Typeface typeface) {
        if (typeface == null || typeface == officialBaseTypeface) {
            return false;
        }
        officialBaseTypeface = typeface;
        officialBaseTypefaceGeneration++;
        return true;
    }

    // Measurement and drawing must share one typeface, or a row wraps differently when the
    // vendor swaps its typeface on activation.
    private Typeface officialTypefaceOf(TextView textView) {
        Typeface base = officialBaseTypeface;
        return base != null ? base : textView.getTypeface();
    }

    private void configurePaints(
            TextView textView, boolean compactSlot, boolean untranslatedLayout) {
        Typeface typeface = resolveConfiguredTypeface(officialTypefaceOf(textView));
        if (inactivePaint.getTypeface() != typeface
                || playedPaint.getTypeface() != typeface
                || activePaint.getTypeface() != typeface
                || activeGlowPaint.getTypeface() != typeface
                || activeFeatherPaint.getTypeface() != typeface
                || translationPaint.getTypeface() != typeface
                || translationActivePaint.getTypeface() != typeface
                || translationFeatherPaint.getTypeface() != typeface) {
            inactivePaint.setTypeface(typeface);
            playedPaint.setTypeface(typeface);
            activePaint.setTypeface(typeface);
            activeGlowPaint.setTypeface(typeface);
            activeFeatherPaint.setTypeface(typeface);
            translationPaint.setTypeface(typeface);
            translationActivePaint.setTypeface(typeface);
            translationFeatherPaint.setTypeface(typeface);
        }
        float textSize;
        if (compactSlot) {
            textSize = Math.max(sp(textView.getContext(), 10f), textView.getTextSize());
        } else {
            float textSizeSp = LyricUiLayoutPolicy.mainTextSizeSp(
                    uiConfig,
                    untranslatedLayout);
            textSize = sp(textView.getContext(), textSizeSp);
        }
        float translationRatio = uiConfig.translationFontRatioPercent / 100f;
        if (LyricPaintState.needsTextSizeSync(
                textSize,
                translationRatio,
                inactivePaint.getTextSize(),
                playedPaint.getTextSize(),
                activePaint.getTextSize(),
                activeGlowPaint.getTextSize(),
                activeFeatherPaint.getTextSize(),
                translationPaint.getTextSize(),
                translationActivePaint.getTextSize(),
                translationFeatherPaint.getTextSize())) {
            setTextSize(textSize);
        }
        // Reveal drawing clears these after use. Reset only the two paints that can carry
        // a transient shader so an interrupted draw cannot leak it into the next frame.
        activeFeatherPaint.setShader(null);
        translationFeatherPaint.setShader(null);
    }

    private void setTextSize(float size) {
        inactivePaint.setTextSize(size);
        playedPaint.setTextSize(size);
        activePaint.setTextSize(size);
        activeGlowPaint.setTextSize(size);
        activeFeatherPaint.setTextSize(size);
        float translationRatio = uiConfig.translationFontRatioPercent / 100f;
        translationPaint.setTextSize(size * translationRatio);
        translationActivePaint.setTextSize(size * translationRatio);
        translationFeatherPaint.setTextSize(size * translationRatio);
    }

    /** Alignment of the row being drawn: the configured one unless its duet side moves it. */
    private int lineAlignment;
    /** Whether the row being drawn sits after the active row and so has not been sung yet. */
    private boolean rowUpcoming;

    /**
     * Resolves the row's alignment and keeps its scale pivot on the matching edge. Called when
     * a row is bound as well as when it is drawn, so a recycled view never plays its first
     * scale animation around the previous line's edge.
     */
    private int syncLineAlignment(TextView textView, WordLine line) {
        int alignment = LyricUiLayoutPolicy.effectiveAlignment(
                uiConfig.alignment, uiConfig.duetSplitEnabled, line == null ? null : line.side);
        LyricsRecyclerViews.updateLineAlignmentOverride(
                textView,
                alignment == uiConfig.alignment
                        ? LyricsRecyclerViews.NO_ALIGNMENT_OVERRIDE
                        : alignment);
        return alignment;
    }

    private float resolveTextX(TextView textView, float measuredWidth, float availableWidth) {
        float left = textView.getPaddingLeft();
        if (measuredWidth > availableWidth) {
            long now = SystemClock.elapsedRealtime();
            if (isLyricVerboseDiagnosticsEnabled()
                    && now - lastOverflowStartLogAt >= 3_000L) {
                lastOverflowStartLogAt = now;
                StructuredBridgeLog.info(
                        BridgeDebugArea.RENDERER,
                        BridgeEvents.RENDER_STATE_CHANGED,
                        "Wide lyric uses logical-start overflow policy"
                                + " | alignment=" + uiConfig.alignment
                                + ", measuredWidth=" + measuredWidth
                                + ", availableWidth=" + availableWidth
                                + ", layoutDirection=" + textView.getLayoutDirection());
            }
            return textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? left + availableWidth - measuredWidth
                    : left;
        }
        boolean rtl = textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (lineAlignment == LyricUiConfig.ALIGN_CENTER) {
            return left + (availableWidth - measuredWidth) / 2f;
        }
        if (lineAlignment == LyricUiConfig.ALIGN_END) {
            return rtl ? left : left + availableWidth - measuredWidth;
        }
        return rtl ? left + availableWidth - measuredWidth : left;
    }

    private float resolveTextX(
            TextView textView,
            String text,
            int start,
            int end,
            TextPaint paint,
            float measuredWidth,
            float availableWidth) {
        float mathematicalX = resolveTextX(textView, measuredWidth, availableWidth);
        if (lineAlignment != LyricUiConfig.ALIGN_CENTER
                || measuredWidth > availableWidth
                || paint == null
                || TextUtils.isEmpty(text)) {
            return mathematicalX;
        }
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        if (safeStart >= safeEnd) {
            return mathematicalX;
        }
        opticalTextBounds.setEmpty();
        paint.getTextBounds(text, safeStart, safeEnd, opticalTextBounds);
        if (opticalTextBounds.isEmpty()) {
            return mathematicalX;
        }
        return LyricUiLayoutPolicy.opticallyCenteredBaselineX(
                textView.getPaddingLeft(),
                availableWidth,
                opticalTextBounds.left,
                opticalTextBounds.right);
    }

    private void configureGeometryPaints(TextView textView, boolean untranslatedLayout) {
        Typeface typeface = resolveConfiguredTypeface(officialTypefaceOf(textView));
        inactivePaint.setTypeface(typeface);
        translationPaint.setTypeface(typeface);
        float mainSize = sp(
                textView.getContext(),
                LyricUiLayoutPolicy.mainTextSizeSp(uiConfig, untranslatedLayout));
        inactivePaint.setTextSize(mainSize);
        translationPaint.setTextSize(
                mainSize * uiConfig.translationFontRatioPercent / 100f);
    }

    private Typeface resolveConfiguredTypeface(Typeface officialTypeface) {
        int weight = uiConfig.fontWeight;
        if (configuredTypefaceCached
                && configuredTypefaceSource == officialTypeface
                && configuredTypefaceWeight == weight) {
            return configuredTypeface;
        }
        Typeface resolved;
        if (weight == LyricUiConfig.WEIGHT_SYSTEM) {
            resolved = officialTypeface;
        } else if (weight == LyricUiConfig.WEIGHT_BOLD) {
            resolved = Typeface.create(officialTypeface, Typeface.BOLD);
        } else if (weight == LyricUiConfig.WEIGHT_MEDIUM && Build.VERSION.SDK_INT >= 28) {
            resolved = Typeface.create(officialTypeface, 500, false);
        } else {
            resolved = Typeface.create(officialTypeface, Typeface.NORMAL);
        }
        configuredTypefaceSource = officialTypeface;
        configuredTypefaceWeight = weight;
        configuredTypeface = resolved;
        configuredTypefaceCached = true;
        return resolved;
    }

    private void applyFade(float fade) {
        applyFade(fade, 0f);
    }

    private void applyFade(float fade, float focusAmount) {
        float resolvedFade = fade * drawBaseFade;
        float amount = Math.max(0f, Math.min(1f, focusAmount));
        int inactiveColor = palette.inactive;
        int focusedColor = palette.focusedInactive;
        int activeColor = palette.active;
        int playedColor = palette.played;
        int glowFill = palette.glowFill;
        inactivePaint.setColor(scaleAlpha(blendColor(inactiveColor, focusedColor, amount), resolvedFade));
        playedPaint.setColor(scaleAlpha(playedColor, resolvedFade));
        activePaint.setColor(scaleAlpha(activeColor, resolvedFade));
        activeGlowPaint.setColor(scaleAlpha(glowFill, resolvedFade));
        activeFeatherPaint.setColor(scaleAlpha(activeColor, resolvedFade));
        translationPaint.setColor(scaleAlpha(blendColor(inactiveColor, focusedColor, amount), resolvedFade));
        translationActivePaint.setColor(scaleAlpha(activeColor, resolvedFade));
        translationFeatherPaint.setColor(scaleAlpha(activeColor, resolvedFade));
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
        String text;
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
                String candidateText,
                int candidateStart,
                int candidateEnd,
                int candidateTextSizeKey,
                int candidateWidthKey,
                Typeface candidateTypeface) {
            return bitmap != null
                    && !bitmap.isRecycled()
                    && line == candidateLine
                    && TextUtils.equals(text, candidateText)
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
            text = null;
            typeface = null;
            lastUsed = 0L;
        }
    }

    /**
     * Reusable per-line scratch for the character lift. Flow coordinates are
     * accumulated advance width from the start of the line's text across
     * wrapped segments, which is the only frame of reference that stays
     * comparable across a wrap.
     */
    private static final class CharLiftState {
        boolean lineActive;
        float flowFront;
        float flowSegmentStart;
        float waveWidth;
        float maxSink;
        /** True while the wave is still running off the end of the text. */
        boolean finishing;
        /** Canvas y the sunk glyph bottoms may not cross. */
        float floorY;
        /** Clearance kept above {@link #floorY}, in pixels. */
        float minClearance;
        float zoneLeft;
        float zoneRight;
        float segmentLeft;
        float segmentRight;
        int firstGrapheme;
        int lastGrapheme;
        float[] prefixWidths;
        /** Grapheme char offsets and contextual advances; null when the segment needs bidi. */
        int[] runBoundaries;
        float[] runAdvances;
        boolean[] runSafe;

        /** True when part of this segment is already sung and sits on the baseline. */
        boolean hasHead() {
            return zoneLeft > segmentLeft;
        }

        /** True when part of this segment is still unsung and sits uniformly low. */
        boolean hasTail() {
            return zoneRight < segmentRight;
        }

        void reset() {
            lineActive = false;
            flowFront = 0f;
            flowSegmentStart = 0f;
            waveWidth = 0f;
            maxSink = 0f;
            finishing = false;
            floorY = 0f;
            minClearance = 0f;
            zoneLeft = 0f;
            zoneRight = 0f;
            segmentLeft = 0f;
            segmentRight = 0f;
            firstGrapheme = 0;
            lastGrapheme = 0;
            prefixWidths = null;
            runBoundaries = null;
            runAdvances = null;
            runSafe = null;
        }
    }

    private static final class GraphemeLayoutCache {
        WordLine line;
        String text;
        Typeface typeface;
        int start;
        int end;
        int textSizeKey;
        float[] prefixWidths;
        int[] boundaries;
        /** Advance from the segment start to each boundary, shaped with the whole segment as context. */
        float[] runAdvances;
        /** Boundaries where a run can start or end without breaking a ligature or cluster. */
        boolean[] runSafe;
        boolean ltrOnly;
        long lastUsed;

        boolean matches(
                WordLine candidateLine,
                String candidateText,
                int candidateStart,
                int candidateEnd,
                int candidateTextSizeKey,
                Typeface candidateTypeface) {
            return prefixWidths != null
                    && line == candidateLine
                    && TextUtils.equals(text, candidateText)
                    && start == candidateStart
                    && end == candidateEnd
                    && textSizeKey == candidateTextSizeKey
                    && typeface == candidateTypeface;
        }

        void clear() {
            line = null;
            text = null;
            typeface = null;
            prefixWidths = null;
            boundaries = null;
            runAdvances = null;
            runSafe = null;
            ltrOnly = false;
            lastUsed = 0L;
        }
    }

    private static final class LyricGroupDrawContext {
        WordLyricModel model;
        WordLine line;
        String text;
        String renderableTranslation;
        WordRange activeWord;
        WordRange glowActiveWord;
        MainLineWindow lineWindow;
        long position;
        long glowPosition;
        boolean activeLine;
        boolean drawProgress;
        boolean passiveLineWindow;
        boolean hasTranslation;
        int wordIndex;
        int glowWordIndex;
        int visibleMainLineCount;
        float availableWidth;
        float focusAmount;
        float fullLineOverlayAmount;
        float top;
        float mainHeight;
        float y;
        float lineHeight;
        float lineGap;
        float translationGap;
        float translationMetricsTop;
        float translationAmount;
    }

    private static final class MainLineWindow {
        int currentStart;
        int previousStart;
        int count;
        float progress;
        float alphaProgress;
        boolean animating;

        MainLineWindow set(
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
            return this;
        }
    }

}
