package io.github.andrealtb.lockscreenlyrics;

final class LyricUiColors {
    private static final int OPAQUE_ALPHA = 0xFF;
    private static final int PLAYED_ALPHA = 0xF0;
    private static final int GLOW_SHADOW_ALPHA = 0xBA;
    private static final int GLOW_FILL_ALPHA = 0x32;
    private static final int FOCUSED_OPACITY_BONUS = Math.round(255f * 0.15f);

    private LyricUiColors() {
    }

    static int inactive(LyricUiConfig config) {
        return withAlpha(config.primaryColor, percentAlpha(config.inactiveOpacityPercent));
    }

    static int focusedInactive(LyricUiConfig config) {
        int alpha = Math.min(OPAQUE_ALPHA,
                percentAlpha(config.inactiveOpacityPercent) + FOCUSED_OPACITY_BONUS);
        return withAlpha(config.primaryColor, alpha);
    }

    static int active(LyricUiConfig config) {
        return withAlpha(config.primaryColor, OPAQUE_ALPHA);
    }

    static int played(LyricUiConfig config) {
        return withAlpha(config.primaryColor, PLAYED_ALPHA);
    }

    static int translationBase(
            LyricUiConfig config, boolean activeInAod, float focusAmount) {
        if (activeInAod) {
            return active(config);
        }
        return blend(inactive(config), focusedInactive(config), focusAmount);
    }

    static int glowShadow(LyricUiConfig config) {
        int alpha = config.glowEnabled
                ? Math.round(GLOW_SHADOW_ALPHA * config.glowIntensityPercent / 100f)
                : 0;
        return withAlpha(config.glowColor, alpha);
    }

    static int glowFill(LyricUiConfig config) {
        int alpha = config.glowEnabled
                ? Math.round(GLOW_FILL_ALPHA * config.glowIntensityPercent / 100f)
                : 0;
        return withAlpha(config.primaryColor, alpha);
    }

    private static int percentAlpha(int percent) {
        return Math.round(OPAQUE_ALPHA * percent / 100f);
    }

    private static int withAlpha(String rgb, int alpha) {
        int value = Integer.parseInt(rgb.substring(1), 16);
        return (alpha << 24) | value;
    }

    private static int blend(int fromColor, int toColor, float amount) {
        float progress = Math.max(0f, Math.min(1f, amount));
        int fromA = (fromColor >>> 24) & 0xFF;
        int fromR = (fromColor >>> 16) & 0xFF;
        int fromG = (fromColor >>> 8) & 0xFF;
        int fromB = fromColor & 0xFF;
        int toA = (toColor >>> 24) & 0xFF;
        int toR = (toColor >>> 16) & 0xFF;
        int toG = (toColor >>> 8) & 0xFF;
        int toB = toColor & 0xFF;
        int a = Math.round(fromA + (toA - fromA) * progress);
        int r = Math.round(fromR + (toR - fromR) * progress);
        int g = Math.round(fromG + (toG - fromG) * progress);
        int b = Math.round(fromB + (toB - fromB) * progress);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
