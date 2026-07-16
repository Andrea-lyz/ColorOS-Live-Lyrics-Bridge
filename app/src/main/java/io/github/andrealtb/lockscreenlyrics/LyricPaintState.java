package io.github.andrealtb.lockscreenlyrics;

final class LyricPaintState {
    private static final float TEXT_SIZE_TOLERANCE_PX = 0.01f;

    private LyricPaintState() {}

    static boolean needsTextSizeSync(
            float mainTextSize,
            float translationRatio,
            float inactiveTextSize,
            float playedTextSize,
            float activeTextSize,
            float activeGlowTextSize,
            float activeFeatherTextSize,
            float translationTextSize,
            float translationActiveTextSize,
            float translationFeatherTextSize) {
        float translationTextSizeTarget = mainTextSize * translationRatio;
        return differs(inactiveTextSize, mainTextSize)
                || differs(playedTextSize, mainTextSize)
                || differs(activeTextSize, mainTextSize)
                || differs(activeGlowTextSize, mainTextSize)
                || differs(activeFeatherTextSize, mainTextSize)
                || differs(translationTextSize, translationTextSizeTarget)
                || differs(translationActiveTextSize, translationTextSizeTarget)
                || differs(translationFeatherTextSize, translationTextSizeTarget);
    }

    private static boolean differs(float actual, float expected) {
        return !Float.isFinite(actual)
                || !Float.isFinite(expected)
                || Math.abs(actual - expected) > TEXT_SIZE_TOLERANCE_PX;
    }
}
