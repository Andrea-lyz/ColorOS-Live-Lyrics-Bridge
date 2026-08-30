package io.github.andrealtb.lockscreenlyrics;

/** Pure alpha resolution shared by the settings preview and the SystemUI renderer. */
final class LyricVisualAlphaPolicy {
    private static final int OPAQUE_ALPHA = 0xFF;

    private LyricVisualAlphaPolicy() {
    }

    static int activeAlpha(LyricUiConfig config) {
        return percentAlpha(safe(config).activeOpacityPercent);
    }

    static int currentUnrevealedAlpha(LyricUiConfig config) {
        return percentAlpha(safe(config).currentUnrevealedOpacityPercent);
    }

    static int activeTranslationAlpha(LyricUiConfig config) {
        return percentAlpha(safe(config).activeTranslationOpacityPercent);
    }

    static int activeTranslationProgressAlpha(LyricUiConfig config) {
        return percentAlpha(safe(config).activeTranslationProgressOpacityPercent);
    }

    static int inactiveMainAlpha(LyricUiConfig config) {
        return percentAlpha(safe(config).inactiveOpacityPercent);
    }

    static int inactiveTranslationAlpha(LyricUiConfig config) {
        LyricUiConfig safeConfig = safe(config);
        int percent = safeConfig.inactiveTranslationFollowsMain
                ? safeConfig.inactiveOpacityPercent
                : safeConfig.inactiveTranslationOpacityPercent;
        return percentAlpha(percent);
    }

    static int steadyInactiveMainAlpha(LyricUiConfig config) {
        return applyInactiveRowFade(config, inactiveMainAlpha(config));
    }

    static int steadyInactiveTranslationAlpha(LyricUiConfig config) {
        return applyInactiveRowFade(config, inactiveTranslationAlpha(config));
    }

    static float steadyInactiveMainPercent(LyricUiConfig config) {
        return alphaPercent(steadyInactiveMainAlpha(config));
    }

    static float steadyInactiveTranslationPercent(LyricUiConfig config) {
        return alphaPercent(steadyInactiveTranslationAlpha(config));
    }

    static float inactiveRowFadeMultiplier(LyricUiConfig config) {
        LyricUiConfig safeConfig = safe(config);
        return safeConfig.inactiveRowFadeEnabled
                ? safeConfig.inactiveRowFadePercent / 100f
                : 1f;
    }

    static int applyInactiveRowFade(LyricUiConfig config, int baseAlpha) {
        int boundedBase = clamp(baseAlpha, 0, OPAQUE_ALPHA);
        return Math.round(boundedBase * inactiveRowFadeMultiplier(config));
    }

    static int percentAlpha(int percent) {
        return Math.round(OPAQUE_ALPHA * clamp(percent, 0, 100) / 100f);
    }

    private static float alphaPercent(int alpha) {
        return clamp(alpha, 0, OPAQUE_ALPHA) * 100f / OPAQUE_ALPHA;
    }

    private static LyricUiConfig safe(LyricUiConfig config) {
        return config == null ? LyricUiConfig.defaults() : config;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
