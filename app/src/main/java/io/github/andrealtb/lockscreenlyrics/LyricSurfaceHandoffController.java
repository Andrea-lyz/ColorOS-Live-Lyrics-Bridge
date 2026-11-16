package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;

/**
 * Official-lyric track handoff state (E-7a, plan §15.8.12): the handoff generation, start time,
 * release-retry generation, the official-draw suppression deadline, the pending late-takeover fade,
 * and the bright-row geometry commit (its own lock, generation and pending model). This class is
 * the only writer of that state; the entry class reads it and calls the transition methods below
 * in the order it used to assign the fields inline.
 *
 * <p>The View work around a transition (suppress / restore / fade / invalidate the remembered
 * lyric views, the visibility watchdog) stays in the entry class until E-6c moves the recycler
 * controller. {@code lyricModelReplacementInProgress} belongs to the model store (E-5d).
 */
final class LyricSurfaceHandoffController {
    /** The only runtime state the controller reads. */
    interface Host {
        WordLyricModel currentWordLyricModel();
    }

    private final Host host;
    volatile long officialLyricDrawSuppressedUntilElapsedMs;
    volatile long officialLyricHandoffStartedAtElapsedMs;
    private volatile int officialLyricHandoffReleaseRetryGeneration = -1;
    volatile int officialLyricHandoffGeneration;
    private final Object brightLyricGeometryCommitLock = new Object();
    volatile WordLyricModel pendingBrightLyricGeometryModel;
    volatile int brightLyricGeometryCommitGeneration;
    volatile boolean pendingCustomLyricTakeoverFade;

    LyricSurfaceHandoffController(Host host) {
        this.host = host;
    }

    /** Starts a track handoff at {@code now}; returns its generation. */
    int beginTrackHandoff(long now) {
        officialLyricHandoffStartedAtElapsedMs = now;
        officialLyricHandoffReleaseRetryGeneration = -1;
        officialLyricDrawSuppressedUntilElapsedMs =
                now + SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS;
        pendingCustomLyricTakeoverFade = true;
        return ++officialLyricHandoffGeneration;
    }

    /** Releases the suppression and the pending fade (finish and timeout paths). */
    void clearTrackHandoff() {
        officialLyricDrawSuppressedUntilElapsedMs = 0L;
        officialLyricHandoffStartedAtElapsedMs = 0L;
        officialLyricHandoffReleaseRetryGeneration = -1;
        pendingCustomLyricTakeoverFade = false;
    }

    int nextHandoffGeneration() {
        return ++officialLyricHandoffGeneration;
    }

    /** Claims the single release retry of {@code generation}; false when one is already queued. */
    boolean markReleaseRetry(int generation) {
        if (officialLyricHandoffReleaseRetryGeneration == generation) {
            return false;
        }
        officialLyricHandoffReleaseRetryGeneration = generation;
        return true;
    }

    void clearReleaseRetry() {
        officialLyricHandoffReleaseRetryGeneration = -1;
    }

    /** Expired suppression deadline: clears it without touching the pending fade. */
    void clearDrawSuppression() {
        officialLyricDrawSuppressedUntilElapsedMs = 0L;
        officialLyricHandoffStartedAtElapsedMs = 0L;
        officialLyricHandoffReleaseRetryGeneration = -1;
    }

    void clearTakeoverFade() {
        pendingCustomLyricTakeoverFade = false;
    }

    /** Consumes the pending geometry commit of {@code model}; false when it is stale. */
    boolean claimBrightLyricGeometryCommit(WordLyricModel model, int generation) {
        synchronized (brightLyricGeometryCommitLock) {
            if (generation != brightLyricGeometryCommitGeneration
                    || pendingBrightLyricGeometryModel != model
                    || host.currentWordLyricModel() != model) {
                return false;
            }
            pendingBrightLyricGeometryModel = null;
        }
        return true;
    }

    int beginBrightLyricGeometryCommit(WordLyricModel model) {
        synchronized (brightLyricGeometryCommitLock) {
            brightLyricGeometryCommitGeneration = brightLyricGeometryCommitGeneration
                    == Integer.MAX_VALUE ? 1 : brightLyricGeometryCommitGeneration + 1;
            pendingBrightLyricGeometryModel = model;
            return brightLyricGeometryCommitGeneration;
        }
    }

    void cancelBrightLyricGeometryCommit() {
        synchronized (brightLyricGeometryCommitLock) {
            brightLyricGeometryCommitGeneration = brightLyricGeometryCommitGeneration
                    == Integer.MAX_VALUE ? 1 : brightLyricGeometryCommitGeneration + 1;
            pendingBrightLyricGeometryModel = null;
        }
    }

    boolean isCurrentBrightLyricGeometryCommit(
            WordLyricModel model,
            int generation) {
        synchronized (brightLyricGeometryCommitLock) {
            return generation == brightLyricGeometryCommitGeneration
                    && pendingBrightLyricGeometryModel == model
                    && host.currentWordLyricModel() == model;
        }
    }
}
