package io.github.andrealtb.lockscreenlyrics;

/** Canonicalizes the legacy glow enable flag onto the visible intensity control. */
final class LyricUiGlowPolicy {
    private LyricUiGlowPolicy() {
    }

    static int canonicalIntensityPercent(boolean legacyEnabled, int intensityPercent) {
        if (!legacyEnabled) return 0;
        return Math.max(0, Math.min(100, intensityPercent));
    }

    static boolean enabledForIntensity(int intensityPercent) {
        return intensityPercent > 0;
    }
}
