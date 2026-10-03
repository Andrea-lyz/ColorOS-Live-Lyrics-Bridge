package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkTransitionStateTest {
    @Test public void invalidatingDrawsWaitUntilTheActualTerminalDraw() {
        ArtworkTransitionState state = new ArtworkTransitionState();
        state.start();
        for (int frame = 0; frame < 100; frame++) {
            long revision = state.beginDraw();
            state.invalidate();
            state.endDraw(revision);
            assertFalse(state.complete());
        }
        long last = state.beginDraw();
        assertFalse(state.complete());
        state.endDraw(last);
        assertTrue(state.complete());
    }
    @Test public void preexistingResetAndReverseTransitionsCannotCommitTheNextCover() {
        ArtworkTransitionState state = new ArtworkTransitionState();
        state.endDraw(state.beginDraw());
        assertFalse(state.complete());
        state.start();
        state.reverseOrReset();
        state.endDraw(state.beginDraw());
        assertFalse(state.complete());
    }
    @Test public void interruptedDrawCannotCommitAReplacementTransition() {
        ArtworkTransitionState state = new ArtworkTransitionState();
        state.start();
        long old = state.beginDraw();
        state.start();
        state.endDraw(old);
        assertFalse(state.complete());
        state.endDraw(state.beginDraw());
        assertTrue(state.complete());
        state.invalidate();
        assertFalse(state.complete());
    }
}
