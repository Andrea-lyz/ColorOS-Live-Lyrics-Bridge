package io.github.andrealtb.lockscreenlyrics.render;

/**
 * A timed inline tag matched inside an un-timed lyric line (e.g. YRC and QRC
 * inline timing segments). Promoted from {@code LockscreenLyricsModule} in
 * step 3.1.
 */
public final class TagMatch {

    public final int start;
    public final int end;
    public final long timeMillis;

    public TagMatch(int start, int end, long timeMillis) {
        this.start = start;
        this.end = end;
        this.timeMillis = timeMillis;
    }
}
