package io.github.andrealtb.lockscreenlyrics;

import android.view.View;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;

/**
 * Active-lyric refresh scheduling state (F-1c, plan §15.8.19): whether a refresh is posted, its
 * frame anchor, whether it runs on the display cadence and for which line, the cadence rate and
 * its next frame deadline, and the lyric-mode rebind generation and runnable. This class is the
 * only writer of that state; the entry class reads it and calls the transition methods below where
 * it used to assign the fields inline.
 *
 * <p>The refresh runnable and the refresh pass itself stay in the entry class: they drive the
 * renderer, the recycler and the active-lyric targets.
 */
final class ActiveLyricRefreshSchedule {
    volatile boolean activeLyricUpdatePosted;
    View activeLyricRefreshAnchor;
    boolean activeLyricRefreshCadenceScheduled;
    WordLine activeLyricRefreshCadenceLine;
    private int activeLyricRefreshCadenceHz = -1;
    private long activeLyricRefreshNextDeadlineNanos =
            LyricRefreshRatePolicy.UNSET_DEADLINE_NANOS;
    volatile int lyricModeRebindGeneration;
    Runnable lyricModeRebindRunnable;

    void markPosted() {
        activeLyricUpdatePosted = true;
    }

    void setAnchor(View anchor) {
        activeLyricRefreshAnchor = anchor;
    }

    void setCadence(boolean displayCadence, WordLine cadenceLine) {
        activeLyricRefreshCadenceScheduled = displayCadence;
        activeLyricRefreshCadenceLine = cadenceLine;
    }

    /** The posted refresh is running: clears posted, anchor, cadence flag and cadence line. */
    void clearPostedRefresh() {
        activeLyricUpdatePosted = false;
        activeLyricRefreshAnchor = null;
        activeLyricRefreshCadenceScheduled = false;
        activeLyricRefreshCadenceLine = null;
    }

    /** The posted refresh was cancelled: clears it and restarts the cadence. */
    void cancelPostedRefresh() {
        activeLyricRefreshAnchor = null;
        activeLyricUpdatePosted = false;
        activeLyricRefreshCadenceScheduled = false;
        activeLyricRefreshCadenceLine = null;
        resetActiveLyricRefreshCadence();
    }

    /** Display-cadence frame gate for {@code maxRefreshRateHz} (0 = no cap). */
    boolean consumeFrame(int maxRefreshRateHz) {
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

    int nextModeRebindGeneration() {
        return ++lyricModeRebindGeneration;
    }

    void setModeRebindRunnable(Runnable runnable) {
        lyricModeRebindRunnable = runnable;
    }

    private void resetActiveLyricRefreshCadence() {
        activeLyricRefreshCadenceHz = -1;
        activeLyricRefreshNextDeadlineNanos =
                LyricRefreshRatePolicy.UNSET_DEADLINE_NANOS;
    }
}
