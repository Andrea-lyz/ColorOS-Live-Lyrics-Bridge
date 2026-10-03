package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;

import static org.junit.Assert.*;

public class ArtworkOwnershipPolicyTest {
    @Test public void everyBindingEpochInvalidatesLateResultsIncludingFieldCompletion() {
        ArtworkRequestStamp current = new ArtworkRequestStamp(1, 2, 3, 4, 5, 6, 7, 8);
        assertTrue(current.isCurrent(new ArtworkRequestStamp(1, 2, 3, 4, 5, 6, 7, 8)));
        for (int i = 0; i < 8; i++) {
            long[] values = {1, 2, 3, 4, 5, 6, 7, 8};
            values[i]++;
            assertFalse("epoch " + i, current.isCurrent(new ArtworkRequestStamp(values[0], values[1],
                    values[2], values[3], values[4], values[5], values[6], values[7])));
        }
        assertFalse(current.isCurrent(null));
    }

    @Test public void noDecoderWithoutEveryHostSessionPlaybackAndVisibilityGate() {
        assertTrue(eligible(new boolean[]{true, true, true, true, true, false, true, true, true, true}));
        for (int i = 0; i < 10; i++) {
            boolean[] gates = {true, true, true, true, true, false, true, true, true, true};
            gates[i] = !gates[i];
            assertFalse("gate " + i, eligible(gates));
        }
    }

    @Test public void onlyOneSurfaceWinsAndImmersiveHasPriority() {
        assertNull(ArtworkPlaybackPolicy.select(false, false));
        assertEquals(ArtworkPlaybackPolicy.Surface.LOCKSCREEN_CARD, ArtworkPlaybackPolicy.select(true, false));
        assertEquals(ArtworkPlaybackPolicy.Surface.IMMERSIVE, ArtworkPlaybackPolicy.select(false, true));
        assertEquals(ArtworkPlaybackPolicy.Surface.IMMERSIVE, ArtworkPlaybackPolicy.select(true, true));
    }

    private static boolean eligible(boolean[] gates) {
        return ArtworkPlaybackPolicy.mayPlay(gates[0], gates[1], gates[2], gates[3], gates[4],
                gates[5], gates[6], gates[7], gates[8], gates[9]);
    }
}
