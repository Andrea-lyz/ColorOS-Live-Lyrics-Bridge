package io.github.andrealtb.lockscreenlyrics;

final class SettingsWindowRefreshRatePolicy {
    static final float MAX_PREFERRED_RATE_HZ = 120f;
    private static final float RATE_TOLERANCE_HZ = 0.5f;

    private SettingsWindowRefreshRatePolicy() {}

    static float choosePreferredRate(float[] supportedRates) {
        if (supportedRates == null) return 0f;
        float selected = 0f;
        for (float rate : supportedRates) {
            if (!Float.isFinite(rate) || rate <= 0f) continue;
            if (rate > MAX_PREFERRED_RATE_HZ + RATE_TOLERANCE_HZ) continue;
            if (rate > selected) selected = rate;
        }
        return selected;
    }
}
