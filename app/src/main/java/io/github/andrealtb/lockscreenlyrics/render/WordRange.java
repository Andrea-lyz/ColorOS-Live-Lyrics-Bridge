package io.github.andrealtb.lockscreenlyrics.render;

/**
 * A timed range within a {@link WordLine}. {@code timeMillis} is the absolute
 * playback position at which the substring {@code [start, end)} should light
 * up as active. Promoted from {@code LockscreenLyricsModule} in step 3.1.
 */
public final class WordRange {

    public final long timeMillis;
    public final int start;
    public final int end;

    public WordRange(long timeMillis, int start, int end) {
        this.timeMillis = timeMillis;
        this.start = start;
        this.end = end;
    }
}
