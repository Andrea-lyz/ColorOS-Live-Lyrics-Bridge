package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Foreground and background animations have separate completion/pending ownership. No timer guesses. */
public final class ArtworkC17State {
    private ArtworkC17State() {}

    static boolean foregroundReady(boolean currentSlot, boolean animating, boolean pending) {
        return currentSlot && !animating && !pending;
    }

    static boolean backgroundReady(boolean animating, boolean pending) {
        return !animating && !pending;
    }

    record Bounds(int left, int top, int width, int height) {}

    /** Layout bounds before native scaleY=-1: the three equal squares must meet without gaps. */
    static boolean mirrorsMatch(Bounds center, Bounds top, Bounds bottom) {
        int side = center.width();
        return side > 0 && center.height() == side && top.width() == side && top.height() == side
                && bottom.width() == side && bottom.height() == side
                && top.left() == center.left() && bottom.left() == center.left()
                && (long) top.top() + side == center.top() && (long) center.top() + side == bottom.top();
    }
}
