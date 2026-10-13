package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArtworkFramePacerTest {
    private static final long SOURCE_FRAME_NS = 33_333_333L;

    private static int presentedOf(ArtworkFramePacer pacer, int sourceFrames, long jitterNs) {
        int presented = 0;
        for (int i = 0; i < sourceFrames; i++) {
            long jitter = (i % 2 == 0 ? 1 : -1) * jitterNs;
            if (pacer.accept(i * SOURCE_FRAME_NS + jitter)) presented++;
        }
        return presented;
    }

    @Test public void uncappedPresentsEveryFrame() {
        ArtworkFramePacer pacer = new ArtworkFramePacer();
        pacer.setLimit(0);
        assertEquals(300, presentedOf(pacer, 300, 0));
    }

    @Test public void capsAThirtyFpsSourceToTheChosenAverageDespiteJitter() {
        for (int fps : new int[] {10, 15, 20, 25}) {
            ArtworkFramePacer pacer = new ArtworkFramePacer();
            pacer.setLimit(fps);
            int presented = presentedOf(pacer, 300, 2_000_000L);
            assertTrue(fps + "fps presented " + presented, Math.abs(presented - fps * 10) <= 1);
        }
    }

    @Test public void halvingAlternatesFramesEvenly() {
        ArtworkFramePacer pacer = new ArtworkFramePacer();
        pacer.setLimit(15);
        for (int i = 0; i < 60; i++) {
            assertEquals("frame " + i, i % 2 == 0, pacer.accept(i * SOURCE_FRAME_NS));
        }
    }

    @Test public void aLoopRestartPresentsItsFirstFrame() {
        ArtworkFramePacer pacer = new ArtworkFramePacer();
        pacer.setLimit(15);
        presentedOf(pacer, 90, 0);
        assertTrue(pacer.accept(0L));
        assertTrue(!pacer.accept(SOURCE_FRAME_NS));
    }

    @Test public void aStallRestartsTheCadenceInsteadOfBursting() {
        ArtworkFramePacer pacer = new ArtworkFramePacer();
        pacer.setLimit(10);
        assertTrue(pacer.accept(0L));
        assertTrue(pacer.accept(5_000_000_000L));
        assertTrue(!pacer.accept(5_000_000_000L + SOURCE_FRAME_NS));
    }
}
