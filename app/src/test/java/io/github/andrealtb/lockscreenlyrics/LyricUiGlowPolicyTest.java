package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricUiGlowPolicyTest {
    @Test
    public void legacyDisabledStateCanonicalizesOntoZeroIntensity() {
        assertEquals(0, LyricUiGlowPolicy.canonicalIntensityPercent(false, 100));
        assertFalse(LyricUiGlowPolicy.enabledForIntensity(0));
    }

    @Test
    public void visibleIntensityIsTheCanonicalEnableOwner() {
        assertEquals(65, LyricUiGlowPolicy.canonicalIntensityPercent(true, 65));
        assertTrue(LyricUiGlowPolicy.enabledForIntensity(65));
    }
}
