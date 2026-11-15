package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * LyricsRecyclerView position and animation state (F-1b, plan §15.8.19): the module's current
 * recycler index, the primed row, the adaptive line-timed follow and its lag window, the settle
 * window, the fade-in generation and deadline, the force-align generation, the row-scale animation
 * suppression, the track row-rebind window and the last official-index observation. This class is
 * the only writer of that state; the entry class reads it and calls the transition methods below
 * where it used to assign the fields inline.
 *
 * <p>The recycler logic itself (Hook callbacks, alignment, scrolling, row styling) is orchestration
 * across the renderer, model, handoff, refresh and mode owners, so it stays in the entry class.
 */
final class LyricsRecyclerState {
    volatile int lastLyricsRecyclerIndex = -1;
    WeakReference<View> lastPrimedLyricsRecyclerView = new WeakReference<>(null);
    int lastPrimedLyricsRecyclerIndex = -1;
    volatile boolean adaptiveLineTimedRecyclerFollowActive;
    volatile int lastAdaptiveLineTimedRecyclerAlignedIndex = -1;
    private volatile int adaptiveLineTimedLagCandidateIndex = -1;
    volatile long adaptiveLineTimedLagObservedAtElapsedMs = -1L;
    volatile long lyricRecyclerSettleUntilElapsedMs;
    volatile int lyricRecyclerSettleOfficialIndex = -1;
    volatile long lyricRecyclerFadeInUntilElapsedMs;
    private volatile int lyricRecyclerFadeGeneration;
    volatile int lyricRecyclerForceAlignGeneration;
    volatile long officialRowScaleAnimationSuppressUntilElapsedMs;
    private volatile long lyricTrackRowRebindEligibleUntilElapsedMs;
    private volatile long lastOfficialLyricIndexObservedAtElapsedMs = -1L;

    /** Track change: back to the first row, adaptive follow off, nothing primed. */
    void resetForTrackChange() {
        lastLyricsRecyclerIndex = 0;
        resetAdaptiveLineTimedRecyclerFollow();
        lastPrimedLyricsRecyclerView = new WeakReference<>(null);
        lastPrimedLyricsRecyclerIndex = -1;
    }

    void clearOfficialIndexObservation() {
        lastOfficialLyricIndexObservedAtElapsedMs = -1L;
    }

    void noteOfficialIndexObserved(long now) {
        lastOfficialLyricIndexObservedAtElapsedMs = now;
    }

    void setCurrentIndex(int targetIndex) {
        lastLyricsRecyclerIndex = targetIndex;
    }

    void markPrimed(View recycler, int targetIndex) {
        lastPrimedLyricsRecyclerView = new WeakReference<>(recycler);
        lastPrimedLyricsRecyclerIndex = targetIndex;
        lastLyricsRecyclerIndex = targetIndex;
    }

    int nextForceAlignGeneration() {
        return ++lyricRecyclerForceAlignGeneration;
    }

    /** Tracks how long the official row has lagged {@code targetIndex} by two or more rows. */
    void observeAdaptiveLag(boolean knownOfficialLag, int targetIndex, long now) {
        if (!knownOfficialLag) {
            adaptiveLineTimedLagCandidateIndex = -1;
            adaptiveLineTimedLagObservedAtElapsedMs = -1L;
        } else if (adaptiveLineTimedLagCandidateIndex != targetIndex) {
            adaptiveLineTimedLagCandidateIndex = targetIndex;
            adaptiveLineTimedLagObservedAtElapsedMs = now;
        }
    }

    void markAdaptiveFollowActive() {
        adaptiveLineTimedRecyclerFollowActive = true;
    }

    void markAdaptiveAligned(int targetIndex) {
        lastAdaptiveLineTimedRecyclerAlignedIndex = targetIndex;
        lastLyricsRecyclerIndex = targetIndex;
    }

    void armTrackRowRebind(long now) {
        lyricTrackRowRebindEligibleUntilElapsedMs =
                now + SYSTEMUI_LYRIC_ROW_REBIND_WINDOW_MS;
    }

    void restartFadeIn() {
        lyricRecyclerFadeGeneration++;
        lyricRecyclerFadeInUntilElapsedMs = 0L;
    }

    void beginLyricRecyclerSettleWindow(int officialIndex, long durationMs) {
        long now = SystemClock.elapsedRealtime();
        lyricRecyclerSettleUntilElapsedMs = Math.max(
                lyricRecyclerSettleUntilElapsedMs,
                now + Math.max(ACTIVE_LYRIC_RETRY_DELAY_MS, durationMs));
        if (officialIndex >= 0) {
            lyricRecyclerSettleOfficialIndex = officialIndex;
        }
    }

    boolean isLyricRecyclerSettleWindowActive() {
        return SystemClock.elapsedRealtime() < lyricRecyclerSettleUntilElapsedMs;
    }

    void suppressOfficialRowScaleAnimations(long durationMs) {
        long now = SystemClock.elapsedRealtime();
        officialRowScaleAnimationSuppressUntilElapsedMs = Math.max(
                officialRowScaleAnimationSuppressUntilElapsedMs,
                now + Math.max(0L, durationMs));
    }

    void resetAdaptiveLineTimedRecyclerFollow() {
        adaptiveLineTimedRecyclerFollowActive = false;
        lastAdaptiveLineTimedRecyclerAlignedIndex = -1;
        adaptiveLineTimedLagCandidateIndex = -1;
        adaptiveLineTimedLagObservedAtElapsedMs = -1L;
    }
}
