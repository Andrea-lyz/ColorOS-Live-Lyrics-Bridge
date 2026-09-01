package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KuWoSystemUiRuntimeTest {
    @Test
    public void mediaModelLogUsesSharedThrottleWindow() {
        KuWoSystemUiRuntime runtime = new KuWoSystemUiRuntime();
        assertFalse(runtime.takeMediaModelLog(0L));
        assertTrue(runtime.takeMediaModelLog(1_500L));
        assertFalse(runtime.takeMediaModelLog(2_999L));
        assertTrue(runtime.takeMediaModelLog(3_000L));
    }

    @Test
    public void carLyricNormalizedAndArtworkRestoreLogOnce() {
        KuWoSystemUiRuntime runtime = new KuWoSystemUiRuntime();
        assertTrue(runtime.takeCarLyricNormalizedLogOnce());
        assertFalse(runtime.takeCarLyricNormalizedLogOnce());
        assertTrue(runtime.takeArtworkRestoreLogOnce());
        assertFalse(runtime.takeArtworkRestoreLogOnce());
    }
}
