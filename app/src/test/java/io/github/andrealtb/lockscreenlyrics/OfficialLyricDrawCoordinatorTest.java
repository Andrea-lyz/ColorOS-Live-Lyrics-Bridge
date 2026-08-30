package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;

import io.github.andrealtb.lockscreenlyrics.render.DrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;

/**
 * Focused coverage for the Phase 6 slice 6 draw coordinator's pure
 * decisions. The full draw path needs live SystemUI views and stays on the
 * device smoke matrix.
 */
public final class OfficialLyricDrawCoordinatorTest {
    @Test
    public void shouldHandleDrawFollowsInspectOrPendingGate() {
        FakeHost host = new FakeHost();
        OfficialLyricDrawCoordinator coordinator = new OfficialLyricDrawCoordinator(host);

        host.inspect = false;
        host.reactivationPending = false;
        assertFalse(coordinator.shouldHandleDraw());

        host.inspect = true;
        host.reactivationPending = false;
        assertTrue(coordinator.shouldHandleDraw());

        host.inspect = false;
        host.reactivationPending = true;
        assertTrue(coordinator.shouldHandleDraw());

        host.inspect = true;
        host.reactivationPending = true;
        assertTrue(coordinator.shouldHandleDraw());
    }

    @Test
    public void preferRecentFrameRequiresReuseEnabledAndCalmSurface() {
        assertTrue(OfficialLyricDrawCoordinator.shouldPreferRecentDrawFrame(true, false, false, false));
        assertFalse(OfficialLyricDrawCoordinator.shouldPreferRecentDrawFrame(false, false, false, false));
        assertFalse(OfficialLyricDrawCoordinator.shouldPreferRecentDrawFrame(true, true, false, false));
        assertFalse(OfficialLyricDrawCoordinator.shouldPreferRecentDrawFrame(true, false, true, false));
        assertFalse(OfficialLyricDrawCoordinator.shouldPreferRecentDrawFrame(true, false, false, true));
        assertFalse(OfficialLyricDrawCoordinator.shouldPreferRecentDrawFrame(true, true, true, true));
    }

    private static final class FakeHost implements OfficialLyricDrawCoordinator.Host {
        boolean inspect;
        boolean reactivationPending;

        @Override public boolean shouldInspectLyricTextViewHooks() { return inspect; }
        @Override public boolean lyricSurfaceReactivationPending() { return reactivationPending; }
        @Override public long lyricRecyclerFadeInUntilElapsedMs() { return 0L; }
        @Override public WordLyricModel currentWordLyricModel() { return null; }
        @Override public WordLyricModel pendingBrightLyricGeometryModel() { return null; }
        @Override public boolean lyricModelReplacementInProgress() { return false; }
        @Override public boolean isAodLowFrameRateLyricMode() { return false; }
        @Override public boolean isLyricRecyclerSettleWindowActive() { return false; }
        @Override public boolean shouldSuppressOfficialLyricForTrackHandoff() { return false; }
        @Override public View findContainingLyricsRecyclerView(TextView textView) { return null; }
        @Override public boolean activateSystemUiLyricModeFromPendingDraw(
                TextView textView, View lyricsRecycler, String reason) { return false; }
        @Override public void officialRendererFallback(
                String reason, TextView textView, WordLyricModel model, String details) { }
        @Override public void noteVisibleLockscreenLyricTextView(
                TextView textView, View lyricsRecycler, long elapsedRealtime) { }
        @Override public void refreshLyricUiStyleSettingsIfNeeded() { }
        @Override public void setRendererAodLowFrameRateMode(boolean enabled) { }
        @Override public void setRendererForceOfficialSlotHeight(boolean force) { }
        @Override public void drawWithOfficialRenderer(Canvas canvas, TextView textView, DrawFrame frame) { }
        @Override public DrawFrame findOfficialLyricDrawFrame(TextView textView) { return null; }
        @Override public DrawFrame findRecentOfficialDrawFrame(TextView textView) { return null; }
        @Override public void rememberRecentOfficialDrawFrame(TextView textView, DrawFrame frame) { }
        @Override public void forgetRecentOfficialDrawFrame(TextView textView) { }
        @Override public void markBoundLyricFrameReady(TextView textView, DrawFrame frame) { }
        @Override public void scheduleBoundLyricFrameRetry(TextView textView, WordLyricModel model) { }
        @Override public String normalizedTextOf(TextView textView) { return ""; }
        @Override public void followAdaptiveNativeLineTimedRecycler(
                WordLyricModel model, WordLine line, TextView textView) { }
        @Override public void logTextViewDraw(DrawFrame frame, TextView textView) { }
        @Override public void finishOfficialLyricTrackHandoffAfterStableCustomFrame(TextView textView) { }
        @Override public void fadeInLateCustomLyricTakeover(TextView textView) { }
        @Override public void onCustomDrawError(TextView textView, WordLyricModel model, Throwable failure) { }
    }
}
