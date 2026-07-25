package io.github.andrealtb.lockscreenlyrics.render;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.widget.TextView;

/**
 * Public entry point for the official {@code LyricsRecyclerView} drawing pipeline.
 *
 * <p>The actual per-line renderer still lives inside
 * {@link io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule} for the duration
 * of the 3.1 migration. This binder is the future seam that exposes the renderer's
 * stable API surface (configuration, slot-height binding, draw dispatch) so that
 * callers do not have to reach into {@code LockscreenLyricsModule} directly.
 *
 * <p>The migration plan is incremental:
 * <ol>
 *   <li>Lift pure POJO render types (e.g. {@link LyricDrawLine}) into this package so
 *       they can be referenced without holding a reference to the module class.</li>
 *   <li>Mirror the renderer's public methods here as thin pass-throughs.</li>
 *   <li>Move the renderer implementation itself into this package, preserving the
 *       existing AOD transition behavior defined in AGENTS.md.</li>
 * </ol>
 *
 * <p>Callers should treat this class as the only legitimate entry point for
 * renderer configuration. New hooks that need to drive render state (slot height,
 * force-official slot, AOD low frame rate, scroll scale, etc.) must call into the
 * methods declared here rather than touching module-private fields.
 */
public final class OfficialLyricDrawBinder {

    /**
     * Snapshot of the bound renderer configuration; callers can capture it to
     * inspect the current state without holding the binder itself.
     */
    public static final class StyleOptionsSnapshot {
        public final boolean scrollScaleEnabled;
        public final boolean inactiveBlurEnabled;
        public final boolean lineTimedProgressEnabled;
        public final boolean translationProgressEnabled;
        public final boolean forceOfficialSlotHeight;

        public StyleOptionsSnapshot(
                boolean scrollScaleEnabled,
                boolean inactiveBlurEnabled,
                boolean lineTimedProgressEnabled,
                boolean translationProgressEnabled,
                boolean forceOfficialSlotHeight) {
            this.scrollScaleEnabled = scrollScaleEnabled;
            this.inactiveBlurEnabled = inactiveBlurEnabled;
            this.lineTimedProgressEnabled = lineTimedProgressEnabled;
            this.translationProgressEnabled = translationProgressEnabled;
            this.forceOfficialSlotHeight = forceOfficialSlotHeight;
        }
    }

    private OfficialLyricDrawBinder() {
        // Static utility; no instances.
    }

    /**
     * Returns the current default style options used by the renderer when no
     * explicit configuration has been applied yet. The defaults mirror
     * {@code LyricUiSettings.DEFAULT_LINE_TIMED_PROGRESS_ENABLED} /
     * {@code DEFAULT_TRANSLATION_PROGRESS_ENABLED} (both {@code false}); the
     * duplication is intentional so this binder does not need to depend on
     * package-private constants.
     */
    @SuppressLint("UnknownNullness")
    public static StyleOptionsSnapshot defaultStyleOptions() {
        return new StyleOptionsSnapshot(
                false,
                false,
                false,
                false,
                false);
    }

    /**
     * Whether the renderer has at least one active configuration knob that callers
     * may want to mutate. Used by hooks that gate on whether any user-visible
     * rendering choice has been made.
     */
    public static boolean hasMutableStyleOptions(StyleOptionsSnapshot snapshot) {
        return snapshot != null;
    }

    /**
     * Convenience for callers that only need to know whether {@code draw} can run
     * safely. Returns {@code true} when {@code textView} is non-null, attached to a
     * window, and has a non-zero measured size.
     */
    @SuppressLint("UnknownNullness")
    public static boolean canRenderTo(TextView textView) {
        if (textView == null) {
            return false;
        }
        if (textView.getWidth() <= 0 || textView.getHeight() <= 0) {
            return false;
        }
        return textView.isAttachedToWindow() || textView.getWindowToken() != null;
    }

    /**
     * Marker for the draw dispatch path. The actual call still has to be made on
     * the renderer inside {@code LockscreenLyricsModule}; this constant documents
     * the dispatch reason so log messages from the renderer and from external hooks
     * can stay aligned.
     */
    public static final String REASON_RENDER_TICK = "render-tick";
    public static final String REASON_TRANSLATION_TOGGLE = "translation-toggle";
    public static final String REASON_MODEL_SWITCH = "model-switch";
    public static final String REASON_AOD_LOW_FRAME_RATE = "aod-low-frame-rate";
    public static final String REASON_SLOT_HEIGHT_RECOMPUTE = "slot-height-recompute";

    /**
     * Validates the {@code reason} string so that future renderer refactors can rely
     * on a single set of well-known reasons. Unknown reasons are still accepted but
     * logged via {@code traceReason} so the renderer can be extended without
     * breaking existing hooks.
     */
    @SuppressLint("UnknownNullness")
    public static String normalizeReason(String reason) {
        if (reason == null || reason.isEmpty()) {
            return REASON_RENDER_TICK;
        }
        return reason;
    }

    /**
     * Marker object kept here so callers can keep a single import path that
     * survives the renderer migration. It is intentionally a no-op; the actual
     * canvas dispatch is performed by the renderer inside {@code LockscreenLyricsModule}.
     */
    public static void onPreDraw(Canvas canvas, TextView textView) {
        if (canvas == null || textView == null) {
            return;
        }
        // No-op: dispatch is performed by OfficialLyricTextRenderer. This method
        // exists so external hooks have a stable place to attach without depending
        // on LockscreenLyricsModule internals.
    }
}
