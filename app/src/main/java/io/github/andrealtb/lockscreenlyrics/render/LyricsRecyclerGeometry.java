package io.github.andrealtb.lockscreenlyrics.render;

/**
 * Snapshot of the {@code LyricsRecyclerView} layout geometry at a single
 * frame. {@link #EMPTY} represents an unattached / not-yet-laid-out state.
 * Promoted from {@code LockscreenLyricsModule} in step 3.1.
 */
public final class LyricsRecyclerGeometry {

    public static final LyricsRecyclerGeometry EMPTY =
            new LyricsRecyclerGeometry(-1, -1, 0, Integer.MIN_VALUE, 0);

    public final int firstVisiblePosition;
    public final int lastVisiblePosition;
    public final int firstVisibleTop;
    public final int targetCenter;
    public final int childCount;

    public LyricsRecyclerGeometry(
            int firstVisiblePosition,
            int lastVisiblePosition,
            int firstVisibleTop,
            int targetCenter,
            int childCount) {
        this.firstVisiblePosition = firstVisiblePosition;
        this.lastVisiblePosition = lastVisiblePosition;
        this.firstVisibleTop = firstVisibleTop;
        this.targetCenter = targetCenter;
        this.childCount = childCount;
    }
}
