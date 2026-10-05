package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkC17StateTest {
    @Test public void incomingSlotMustWaitForControllerHandoverEvenAfterAlphaAnimationStops() {
        assertFalse(ArtworkC17State.foregroundReady(false, true, false));
        assertFalse(ArtworkC17State.foregroundReady(false, false, false));
        assertTrue(ArtworkC17State.foregroundReady(true, false, false));
    }

    @Test public void rapidSkipCannotRevealIntermediatePendingCover() {
        assertFalse(ArtworkC17State.foregroundReady(true, true, true));
        assertFalse(ArtworkC17State.foregroundReady(true, false, true));
        assertFalse(ArtworkC17State.foregroundReady(true, true, false));
        assertTrue(ArtworkC17State.foregroundReady(true, false, false));
    }

    @Test public void foregroundCompletionDoesNotCompleteBackgroundTransition() {
        assertTrue(ArtworkC17State.foregroundReady(true, false, false));
        assertFalse(ArtworkC17State.backgroundReady(true, false));
        assertFalse(ArtworkC17State.backgroundReady(false, true));
        assertTrue(ArtworkC17State.backgroundReady(false, false));
    }

    @Test public void portraitMirrorsMayExtendOutsideViewportButMustMeetTheCenter() {
        var center = new ArtworkC17State.Bounds(0, 480, 1080, 1080);
        var top = new ArtworkC17State.Bounds(0, -600, 1080, 1080);
        var bottom = new ArtworkC17State.Bounds(0, 1560, 1080, 1080);
        assertTrue(ArtworkC17State.mirrorsMatch(center, top, bottom));
        assertFalse(ArtworkC17State.mirrorsMatch(center, top, new ArtworkC17State.Bounds(0, 1561, 1080, 1080)));
        assertFalse(ArtworkC17State.mirrorsMatch(center, new ArtworkC17State.Bounds(1, -600, 1080, 1080), bottom));
    }

    @Test public void landscapeAndResizedGeometryCannotReusePortraitSampling() {
        var center = new ArtworkC17State.Bounds(660, 216, 1080, 1080);
        var top = new ArtworkC17State.Bounds(660, -864, 1080, 1080);
        var bottom = new ArtworkC17State.Bounds(660, 1296, 1080, 1080);
        assertTrue(ArtworkC17State.mirrorsMatch(center, top, bottom));
        assertFalse(ArtworkC17State.mirrorsMatch(new ArtworkC17State.Bounds(660, 216, 810, 1080), top, bottom));
        var empty = new ArtworkC17State.Bounds(0, 0, 0, 0);
        assertFalse(ArtworkC17State.mirrorsMatch(empty, empty, empty));
    }
}
