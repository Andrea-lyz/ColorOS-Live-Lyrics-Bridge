package io.github.andrealtb.lockscreenlyrics;

import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import io.github.andrealtb.lockscreenlyrics.render.DrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;

/**
 * Orchestrates one official lyric {@code TextView#onDraw} interception:
 * hook eligibility, surface reactivation, track-handoff / fade suppression,
 * draw-frame resolution, renderer dispatch, active-line follow-up and
 * handoff commit. Promoted out of {@code LockscreenLyricsModule} in
 * Phase 6 slice 6; the module hook keeps only argument reading and
 * delegation. All module state and renderer ownership stays behind the
 * {@link Host} seam so render internals keep their current boundaries.
 */
final class OfficialLyricDrawCoordinator {
    /** Chain continuation supplied by the Xposed hook. */
    interface Proceed {
        Object proceed() throws Throwable;
    }

    /** Module state and renderer operations consumed by the draw path. */
    interface Host {
        boolean shouldInspectLyricTextViewHooks();

        boolean lyricSurfaceReactivationPending();

        long lyricRecyclerFadeInUntilElapsedMs();

        WordLyricModel currentWordLyricModel();

        WordLyricModel pendingBrightLyricGeometryModel();

        boolean lyricModelReplacementInProgress();

        boolean isAodLowFrameRateLyricMode();

        boolean isLyricRecyclerSettleWindowActive();

        boolean shouldSuppressOfficialLyricForTrackHandoff();

        View findContainingLyricsRecyclerView(TextView textView);

        boolean activateSystemUiLyricModeFromPendingDraw(
                TextView textView, View lyricsRecycler, String reason);

        void officialRendererFallback(
                String reason, TextView textView, WordLyricModel model, String details);

        void noteVisibleLockscreenLyricTextView(
                TextView textView, View lyricsRecycler, long elapsedRealtime);

        void refreshLyricUiStyleSettingsIfNeeded();

        void setRendererAodLowFrameRateMode(boolean enabled);

        void setRendererForceOfficialSlotHeight(boolean force);

        void drawWithOfficialRenderer(Canvas canvas, TextView textView, DrawFrame frame);

        DrawFrame findOfficialLyricDrawFrame(TextView textView);

        DrawFrame findRecentOfficialDrawFrame(TextView textView);

        void rememberRecentOfficialDrawFrame(TextView textView, DrawFrame frame);

        void forgetRecentOfficialDrawFrame(TextView textView);

        void markBoundLyricFrameReady(TextView textView, DrawFrame frame);

        void scheduleBoundLyricFrameRetry(TextView textView, WordLyricModel model);

        String normalizedTextOf(TextView textView);

        void followAdaptiveNativeLineTimedRecycler(
                WordLyricModel model, WordLine line, TextView textView);

        void logTextViewDraw(DrawFrame frame, TextView textView);

        void finishOfficialLyricTrackHandoffAfterStableCustomFrame(TextView textView);

        void fadeInLateCustomLyricTakeover(TextView textView);

        void onCustomDrawError(TextView textView, WordLyricModel model, Throwable failure);
    }

    private final Host host;

    OfficialLyricDrawCoordinator(Host host) {
        this.host = host;
    }

    /** Fast-path gate evaluated before reading hook arguments. */
    boolean shouldHandleDraw() {
        return host.shouldInspectLyricTextViewHooks()
                || host.lyricSurfaceReactivationPending();
    }

    static boolean shouldPreferRecentDrawFrame(
            boolean frameReuseEnabled,
            boolean recyclerSettleWindowActive,
            boolean aodLowFrameRateMode,
            boolean recyclerFadeInProgress) {
        return frameReuseEnabled
                && !recyclerSettleWindowActive
                && !aodLowFrameRateMode
                && !recyclerFadeInProgress;
    }

    Object draw(TextView textView, Canvas canvas, Proceed proceed) throws Throwable {
        boolean inspectLyricTextView = host.shouldInspectLyricTextViewHooks();
        if (!inspectLyricTextView && !host.lyricSurfaceReactivationPending()) {
            return proceed.proceed();
        }

        View lyricsRecycler = null;
        if (!inspectLyricTextView) {
            lyricsRecycler = host.findContainingLyricsRecyclerView(textView);
            if (lyricsRecycler == null || lyricsRecycler.getVisibility() != View.VISIBLE) {
                return proceed.proceed();
            }
            if (!host.activateSystemUiLyricModeFromPendingDraw(
                    textView,
                    lyricsRecycler,
                    "visible lyric draw after surface transition")) {
                host.officialRendererFallback(
                        "hook-inactive",
                        textView,
                        host.currentWordLyricModel(),
                        "pending=" + host.lyricSurfaceReactivationPending());
                return proceed.proceed();
            }
            inspectLyricTextView = host.shouldInspectLyricTextViewHooks();
            if (!inspectLyricTextView) {
                return proceed.proceed();
            }
        }
        long drawElapsedRealtime = SystemClock.elapsedRealtime();
        boolean suppressingTrackHandoff = host.shouldSuppressOfficialLyricForTrackHandoff();
        boolean recyclerFadeInProgress =
                drawElapsedRealtime < host.lyricRecyclerFadeInUntilElapsedMs();
        WordLyricModel model = host.currentWordLyricModel();
        if (model == null && !suppressingTrackHandoff && !recyclerFadeInProgress) {
            if (lyricsRecycler == null) {
                lyricsRecycler = host.findContainingLyricsRecyclerView(textView);
            }
            if (lyricsRecycler != null) {
                host.officialRendererFallback(
                        "model-null",
                        textView,
                        null,
                        "handoff=false, recyclerFade=false");
            }
            return proceed.proceed();
        }
        if (lyricsRecycler == null) {
            lyricsRecycler = host.findContainingLyricsRecyclerView(textView);
        }
        if (lyricsRecycler == null) {
            return proceed.proceed();
        }
        if (!suppressingTrackHandoff
                && !recyclerFadeInProgress
                && lyricsRecycler.getVisibility() != View.VISIBLE) {
            host.forgetRecentOfficialDrawFrame(textView);
            return null;
        }
        host.noteVisibleLockscreenLyricTextView(textView, lyricsRecycler, drawElapsedRealtime);
        if (model == null) {
            host.officialRendererFallback(
                    "model-null",
                    textView,
                    null,
                    "handoff=" + suppressingTrackHandoff
                            + ", recyclerFade=" + recyclerFadeInProgress);
            return suppressingTrackHandoff || recyclerFadeInProgress
                    ? null
                    : proceed.proceed();
        }
        boolean aodLowFrameRateMode = host.isAodLowFrameRateLyricMode();
        if (LockscreenIntegrationPolicy.shouldDeferBrightLyricPixelsForGeometryCommit(
                host.pendingBrightLyricGeometryModel() == model,
                aodLowFrameRateMode)) {
            // A replacement model is published before its content-driven row heights can be
            // committed on the main thread. Drawing into the previous song's holder geometry for
            // one frame makes translated or wrapped songs visibly jump when the next layout lands.
            // Keep that frame blank; the model-ready transaction invalidates every row immediately
            // after the next traversal has consumed the new LayoutParams.
            return null;
        }
        host.setRendererAodLowFrameRateMode(aodLowFrameRateMode);
        host.refreshLyricUiStyleSettingsIfNeeded();
        // SystemUI deliberately binds and draws the replacement lyric list while its RecyclerView
        // alpha is zero, then fades the already-prepared surface in. Drawing a blank display list
        // here leaves non-active rows blank until an unrelated later invalidation. Custom rendering
        // therefore follows structural ownership (a bound lyric item), not effective alpha.
        try {
            boolean preferRecentFrame = shouldPreferRecentDrawFrame(
                    LyricTimingTuningConstants.OfficialLyric.DRAW_FRAME_REUSE_ENABLED,
                    host.isLyricRecyclerSettleWindowActive(),
                    aodLowFrameRateMode,
                    recyclerFadeInProgress);
            DrawFrame frame = preferRecentFrame
                    ? host.findRecentOfficialDrawFrame(textView)
                    : null;
            if (frame == null) {
                frame = host.findOfficialLyricDrawFrame(textView);
            }
            if (frame == null
                    && LyricTimingTuningConstants.OfficialLyric.DRAW_FRAME_REUSE_ENABLED
                    && !preferRecentFrame) {
                frame = host.findRecentOfficialDrawFrame(textView);
            }
            if (frame == null) {
                host.officialRendererFallback(
                        "frame-miss-suppressed",
                        textView,
                        model,
                        "text=" + shortenForLog(host.normalizedTextOf(textView)));
                // Once this bound item belongs to the authoritative custom model, never alternate
                // between custom and official pixels from one frame to the next. A transient bind
                // mismatch is safer as one blank frame than a large official row flashing through.
                // The bind-scoped retry below guarantees that the blank display list is rebuilt on
                // the next frame without creating a persistent refresh loop for inactive rows.
                host.scheduleBoundLyricFrameRetry(textView, model);
                return null;
            }

            host.setRendererForceOfficialSlotHeight(false);
            host.drawWithOfficialRenderer(canvas, textView, frame);
            host.markBoundLyricFrameReady(textView, frame);
            if (LyricTimingTuningConstants.OfficialLyric.DRAW_FRAME_REUSE_ENABLED) {
                host.rememberRecentOfficialDrawFrame(textView, frame);
            }
            if (frame.active) {
                WordLyricModel activeFrameModel = frame.model;
                WordLine activeFrameLine = frame.line;
                textView.post(() -> {
                    if (host.currentWordLyricModel() == activeFrameModel
                            && textView.isAttachedToWindow()
                            && WordLyricRenderSupport.matchesWordLineText(
                                    activeFrameLine,
                                    host.normalizedTextOf(textView))) {
                        host.followAdaptiveNativeLineTimedRecycler(
                                activeFrameModel,
                                activeFrameLine,
                                textView);
                    }
                });
            }
            host.logTextViewDraw(frame, textView);
            if (suppressingTrackHandoff && !host.lyricModelReplacementInProgress()) {
                host.finishOfficialLyricTrackHandoffAfterStableCustomFrame(textView);
            } else if (!suppressingTrackHandoff
                    && !host.lyricModelReplacementInProgress()) {
                host.fadeInLateCustomLyricTakeover(textView);
            }
            return null;
        } catch (Throwable t) {
            host.officialRendererFallback(
                    "draw-error",
                    textView,
                    model,
                    t.getClass().getSimpleName());
            host.onCustomDrawError(textView, model, t);
            // Keep render ownership deterministic for the lifetime of this bound lyric item.
            host.scheduleBoundLyricFrameRetry(textView, model);
            return null;
        }
    }

    private static String shortenForLog(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }
}
