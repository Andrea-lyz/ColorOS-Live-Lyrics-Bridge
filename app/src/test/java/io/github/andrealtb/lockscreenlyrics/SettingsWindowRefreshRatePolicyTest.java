package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SettingsWindowRefreshRatePolicyTest {
    @Test
    public void prefers120WhenPanelAlsoExposesLowerRates() {
        assertEquals(
                120f,
                SettingsWindowRefreshRatePolicy.choosePreferredRate(
                        new float[]{1f, 60f, 90f, 120f}),
                0.001f);
    }

    @Test
    public void doesNotSelectRateAboveSettingsCap() {
        assertEquals(
                90f,
                SettingsWindowRefreshRatePolicy.choosePreferredRate(
                        new float[]{60f, 90f, 144f}),
                0.001f);
    }

    @Test
    public void preservesExactSupportedRateNear120() {
        assertEquals(
                119.88f,
                SettingsWindowRefreshRatePolicy.choosePreferredRate(
                        new float[]{59.94f, 119.88f}),
                0.001f);
    }

    @Test
    public void ignoresInvalidRates() {
        assertEquals(
                60f,
                SettingsWindowRefreshRatePolicy.choosePreferredRate(
                        new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1f, 60f}),
                0.001f);
        assertEquals(
                0f,
                SettingsWindowRefreshRatePolicy.choosePreferredRate(null),
                0.001f);
    }
}
