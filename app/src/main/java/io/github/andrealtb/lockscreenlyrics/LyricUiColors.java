package io.github.andrealtb.lockscreenlyrics;

final class LyricUiColors {
    private static final String BASE_LYRIC_COLOR = "#FFFFFF";
    private static final int PLAYED_ALPHA = 0xF0;
    private static final int GLOW_SHADOW_ALPHA = 0xBA;
    private static final int GLOW_FILL_ALPHA = 0x32;

    private LyricUiColors() {
    }

    static int inactive(LyricUiConfig config) {
        return withAlpha(BASE_LYRIC_COLOR, LyricVisualAlphaPolicy.inactiveMainAlpha(config));
    }

    static int focusedInactive(LyricUiConfig config) {
        return withAlpha(
                BASE_LYRIC_COLOR,
                LyricVisualAlphaPolicy.currentUnrevealedAlpha(config));
    }

    static int active(LyricUiConfig config) {
        return withAlpha(config.primaryColor, LyricVisualAlphaPolicy.activeAlpha(config));
    }

    static int played(LyricUiConfig config) {
        return withAlpha(config.primaryColor, PLAYED_ALPHA);
    }

    static int[] activeFeatherColors(LyricUiConfig config) {
        return new int[]{
                withAlpha(config.primaryColor, 0xFF),
                withAlpha(config.primaryColor, 0xF6),
                withAlpha(config.primaryColor, 0xD9),
                withAlpha(config.primaryColor, 0x9A),
                withAlpha(config.primaryColor, 0x48),
                withAlpha(config.primaryColor, 0x10),
                withAlpha(config.primaryColor, 0x00)
        };
    }

    static int activeTranslation(LyricUiConfig config) {
        return withAlpha(
                BASE_LYRIC_COLOR,
                LyricVisualAlphaPolicy.activeTranslationAlpha(config));
    }

    static int activeTranslationProgress(LyricUiConfig config) {
        return withAlpha(
                config.primaryColor,
                LyricVisualAlphaPolicy.activeTranslationProgressAlpha(config));
    }

    static int translationBase(LyricUiConfig config, boolean activeLine) {
        return activeLine
                ? activeTranslation(config)
                : withAlpha(
                        BASE_LYRIC_COLOR,
                        LyricVisualAlphaPolicy.inactiveTranslationAlpha(config));
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

    private static int withAlpha(String rgb, int alpha) {
        int value = Integer.parseInt(rgb.substring(1), 16);
        return (alpha << 24) | value;
    }

    static int blend(int fromColor, int toColor, float amount) {
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
