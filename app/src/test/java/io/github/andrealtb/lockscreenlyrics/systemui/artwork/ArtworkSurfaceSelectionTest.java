package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkSurfaceSelectionTest {
    @Test public void oneLargeCoverWinsEvenWhenCardsAreAmbiguous() {
        assertEquals(ArtworkPlaybackPolicy.Surface.IMMERSIVE, ArtworkPlaybackPolicy.selectUnique(2, 1));
        assertEquals(ArtworkPlaybackPolicy.Surface.LOCKSCREEN_CARD, ArtworkPlaybackPolicy.selectUnique(1, 0));
        assertNull(ArtworkPlaybackPolicy.selectUnique(0, 0));
    }
    @Test public void ambiguityNeverSelectsAnArbitraryCardOrFallsBackFromAmbiguousLargeCovers() {
        assertNull(ArtworkPlaybackPolicy.selectUnique(2, 0));
        assertNull(ArtworkPlaybackPolicy.selectUnique(1, 2));
        assertNull(ArtworkPlaybackPolicy.selectUnique(-1, 0));
    }
}
