package io.github.andrealtb.lockscreenlyrics.render;

import java.util.ArrayList;

/**
 * Carries the resolved timing / text information for a lyric line that has
 * inline (per-word or per-segment) timing. Promoted from
 * {@code LockscreenLyricsModule} in step 3.1.
 */
public final class InlineTimedLyricLine {

    public final long timeMillis;
    public final long endTimeMillis;
    public final String text;
    public final ArrayList<WordRange> words;
    public final boolean inlineTiming;
    public final int sourceTimedSegmentCount;
    public final int order;

    public InlineTimedLyricLine(
            long timeMillis,
            long endTimeMillis,
            String text,
            ArrayList<WordRange> words,
            boolean inlineTiming,
            int sourceTimedSegmentCount,
            int order) {
        this.timeMillis = timeMillis;
        this.endTimeMillis = Math.max(timeMillis, endTimeMillis);
        this.text = text;
        this.words = words;
        this.inlineTiming = inlineTiming;
        this.sourceTimedSegmentCount = Math.max(0, sourceTimedSegmentCount);
        this.order = order;
    }
}
