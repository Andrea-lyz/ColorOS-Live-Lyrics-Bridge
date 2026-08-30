package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class LyricRefreshRateOptionsPolicyTest {
    @Test
    public void unsupportedSavedRateIsPreservedButMarkedUnavailable() {
        LyricRefreshRateOptionsPolicy.Options options =
                LyricRefreshRateOptionsPolicy.build(true, false, true, 90);

        assertArrayEquals(new int[]{0, 60, 90, 120}, options.values);
        assertArrayEquals(new boolean[]{true, true, false, true}, options.available);
    }

    @Test
    public void unsupportedUnsavedRatesAreNotOffered() {
        LyricRefreshRateOptionsPolicy.Options options =
                LyricRefreshRateOptionsPolicy.build(true, false, true, 0);

        assertArrayEquals(new int[]{0, 60, 120}, options.values);
        assertArrayEquals(new boolean[]{true, true, true}, options.available);
    }
}
