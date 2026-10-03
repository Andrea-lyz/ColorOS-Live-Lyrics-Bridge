package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Forward completion from real draw/invalidations, never from elapsed-time guesses. */
public final class ArtworkTransitionState {
    private long revision;
    private boolean forward;
    private boolean drawing;
    private boolean invalidated;
    private boolean settled;
    public void start() { ++revision; forward = true; settled = false; }
    public void reverseOrReset() { ++revision; forward = false; settled = false; }
    public long beginDraw() { drawing = true; invalidated = false; settled = false; return revision; }
    public void invalidate() { settled = false; if (drawing) invalidated = true; }
    public void endDraw(long drawRevision) {
        drawing = false;
        settled = drawRevision == revision && forward && !invalidated;
    }
    public boolean complete() { return forward && settled && !drawing; }
    public String diagnostic() {
        return "forwardKnown=" + forward + " drawing=" + drawing + " drawInvalidated=" + invalidated
                + " complete=" + complete();
    }
}
