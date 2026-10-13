package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/**
 * Picks which decoded frames reach the screen under the user's frame-rate cap. Every presented
 * frame redraws the lockscreen window and its blur, so presenting fewer frames is what saves power;
 * decoding itself is cheap. Timestamps are the decoder's presentation times in nanoseconds.
 * The limit may change from any thread; {@link #accept} runs on the media thread only.
 */
final class ArtworkFramePacer {
    /** Absorbs decoder jitter so a 30fps source halves evenly to 15fps. */
    static final long TOLERANCE_NS = 4_000_000L;
    private volatile long intervalNs;
    private long nextNs = Long.MIN_VALUE;

    /** {@code fps <= 0} or at least the source rate presents every frame. */
    void setLimit(int fps) {
        intervalNs = fps <= 0 ? 0L : 1_000_000_000L / fps;
    }

    boolean accept(long timestampNs) {
        long step = intervalNs;
        if (step <= 0L) {
            nextNs = Long.MIN_VALUE;
            return true;
        }
        // First frame, a loop restart (time went backwards) or a stall restarts the cadence.
        if (nextNs == Long.MIN_VALUE || timestampNs < nextNs - step || timestampNs > nextNs + 2 * step) {
            nextNs = timestampNs + step;
            return true;
        }
        if (timestampNs < nextNs - TOLERANCE_NS) return false;
        // Advance by whole intervals, not to this frame, so 20/25fps average out exactly.
        nextNs += step;
        return true;
    }
}
