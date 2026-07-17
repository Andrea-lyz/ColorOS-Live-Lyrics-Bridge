package io.github.andrealtb.lockscreenlyrics;

final class LyricUiLayoutPolicy {
    private LyricUiLayoutPolicy() {}

    static int lineSpacingTenthsDp(LyricUiConfig config) {
        return config == null
                ? LyricUiConfig.defaults().lineSpacingTenthsDp
                : config.lineSpacingTenthsDp;
    }

    static int legacyLineSpacingTenthsDp(LyricUiConfig config) {
        if (config == null || !LyricUiPreset.hasDefaultAppearance(config)) {
            return 130;
        }
        return Math.round(130f * config.mainFontTenthsSp / 260f);
    }

    static float outwardFontTop(float fontTop) {
        return (float) Math.floor(fontTop);
    }

    static int visibleMainLineCount(int totalLineCount, int maximumVisibleLines) {
        return Math.max(0, Math.min(totalLineCount, Math.max(0, maximumVisibleLines)));
    }

    static float fontOuterHeight(float fontTop, float fontBottom) {
        return Math.max(
                0f,
                (float) Math.ceil(fontBottom) - outwardFontTop(fontTop));
    }

    static float translatedGroupHeight(
            float mainHeight,
            float translationContribution,
            float translationAmount) {
        float amount = Math.max(0f, Math.min(1f, translationAmount));
        return Math.max(0f, mainHeight)
                + Math.max(0f, translationContribution) * amount;
    }

    static float mainTextBlockHeight(
            float fontTop,
            float fontAscent,
            float fontDescent,
            float fontBottom,
            int visibleLineCount,
            float lineGap) {
        if (visibleLineCount <= 0) {
            return 0f;
        }
        float lineAdvance = Math.max(0f, fontDescent - fontAscent + lineGap);
        return fontOuterHeight(fontTop, fontBottom)
                + lineAdvance * (visibleLineCount - 1);
    }

    static int requiredSlotHeight(
            float groupHeight,
            int verticalPadding,
            int bottomSafety,
            int minimumHeight) {
        int contentHeight = (int) Math.ceil(Math.max(0f, groupHeight));
        return Math.max(
                Math.max(0, minimumHeight),
                contentHeight + Math.max(0, verticalPadding) + Math.max(0, bottomSafety));
    }

    static float horizontalScalePivot(
            int alignment,
            boolean rtl,
            float contentLeft,
            float contentWidth) {
        float width = Math.max(0f, contentWidth);
        if (alignment == LyricUiConfig.ALIGN_CENTER) {
            return contentLeft + width / 2f;
        }
        if (alignment == LyricUiConfig.ALIGN_END) {
            return rtl ? contentLeft : contentLeft + width;
        }
        return rtl ? contentLeft + width : contentLeft;
    }

    static int resolvedViewDimension(int laidOutDimension, int measuredDimension) {
        if (laidOutDimension > 0) {
            return laidOutDimension;
        }
        return Math.max(0, measuredDimension);
    }

    static float opticallyCenteredBaselineX(
            float contentLeft,
            float contentWidth,
            float inkLeft,
            float inkRight) {
        return contentLeft + Math.max(0f, contentWidth) / 2f
                - (inkLeft + inkRight) / 2f;
    }

    static float floatingPreviewTopInRoot(
            float visibleUiBottomOnScreen,
            float rootTopOnScreen,
            float minimumGap) {
        float gap = Math.max(0f, minimumGap);
        return Math.max(gap, visibleUiBottomOnScreen - rootTopOnScreen + gap);
    }

}
