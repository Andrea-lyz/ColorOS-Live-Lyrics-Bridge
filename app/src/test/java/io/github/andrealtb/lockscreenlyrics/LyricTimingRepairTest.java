package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricTimingRepairTest {
    @Test
    public void dominantGapDowngradesShortWordTimedLine() {
        assertTrue(LyricTimingRepair.shouldDowngradeWordTiming(
                3,
                9_400L,
                25_060L,
                15_300L,
                true));
    }

    @Test
    public void nonMonotonicWordTimingDowngrades() {
        assertTrue(LyricTimingRepair.shouldDowngradeWordTiming(
                4,
                1_000L,
                3_000L,
                1_000L,
                false));
    }

    @Test
    public void evenlySpacedLongLineKeepsWordTiming() {
        assertFalse(LyricTimingRepair.shouldDowngradeWordTiming(
                8,
                0L,
                28_000L,
                4_000L,
                true));
    }
}
