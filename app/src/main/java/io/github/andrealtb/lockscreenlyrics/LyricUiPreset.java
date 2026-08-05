package io.github.andrealtb.lockscreenlyrics;

enum LyricUiPreset {
    DEFAULT(R.string.preset_default),
    SOFT(R.string.preset_soft),
    VIVID(R.string.preset_vivid),
    MINIMAL(R.string.preset_minimal),
    CUSTOM(R.string.preset_custom);

    final int labelRes;

    LyricUiPreset(int labelRes) {
        this.labelRes = labelRes;
    }

    LyricUiConfig apply(LyricUiConfig source) {
        if (this == CUSTOM) return source;
        LyricUiConfig defaults = LyricUiConfig.defaults();
        LyricUiConfig.Builder builder = source.buildUpon()
                .mainFontTenthsSp(defaults.mainFontTenthsSp)
                .translationFontRatioPercent(defaults.translationFontRatioPercent)
                .fontWeight(defaults.fontWeight)
                .alignment(defaults.alignment)
                .lineSpacingTenthsDp(defaults.lineSpacingTenthsDp)
                .wrappedLineSpacingTenthsDp(defaults.wrappedLineSpacingTenthsDp);
        switch (this) {
            case SOFT:
                // All presets keep the default white lyric color with the gold glow; they
                // only differ in effects (2026-08-05 device decision).
                return builder.primaryColor("#FFFFFF").glowColor("#FFD68A")
                        .inactiveOpacityPercent(36)
                        .blurEnabled(true).blurRadiusTenthsPx(20)
                        .scaleEnabled(true).inactiveScalePercent(96)
                        .glowEnabled(true).glowIntensityPercent(60).glowRadiusPercent(14)
                        .motionMode(LyricUiConfig.MOTION_REDUCED).build();
            case VIVID:
                return builder.primaryColor("#FFFFFF").glowColor("#FFD68A")
                        .inactiveOpacityPercent(44)
                        .blurEnabled(true).blurRadiusTenthsPx(40)
                        .scaleEnabled(true).inactiveScalePercent(90)
                        .glowEnabled(true).glowIntensityPercent(100).glowRadiusPercent(22)
                        .motionMode(LyricUiConfig.MOTION_STANDARD).build();
            case MINIMAL:
                return builder.primaryColor("#FFFFFF").glowColor("#FFD68A")
                        .inactiveOpacityPercent(55)
                        .blurEnabled(false).blurRadiusTenthsPx(40)
                        .scaleEnabled(false).inactiveScalePercent(90)
                        .glowEnabled(false).glowIntensityPercent(0).glowRadiusPercent(18)
                        .motionMode(LyricUiConfig.MOTION_OFF).build();
            case DEFAULT:
            default:
                return builder.primaryColor("#FFFFFF").glowColor("#FFD68A")
                        .inactiveOpacityPercent(44)
                        .blurEnabled(false).blurRadiusTenthsPx(40)
                        .scaleEnabled(false).inactiveScalePercent(90)
                        .glowEnabled(true).glowIntensityPercent(100).glowRadiusPercent(18)
                        .motionMode(LyricUiConfig.MOTION_STANDARD)
                        .build();
        }
    }

    static LyricUiPreset detect(LyricUiConfig config) {
        for (LyricUiPreset preset : new LyricUiPreset[]{DEFAULT, SOFT, VIVID, MINIMAL}) {
            LyricUiConfig candidate = preset.apply(config);
            if (sameAppearance(candidate, config) && sameTypography(candidate, config)) {
                return preset;
            }
        }
        return CUSTOM;
    }

    static boolean hasDefaultAppearance(LyricUiConfig config) {
        return config != null && sameAppearance(DEFAULT.apply(config), config);
    }

    static boolean hasLegacyPresetSpacing(LyricUiConfig config) {
        if (config == null
                || config.mainFontTenthsSp != 220
                || config.translationFontRatioPercent != 66
                || config.fontWeight != LyricUiConfig.WEIGHT_SYSTEM
                || config.alignment != LyricUiConfig.ALIGN_START) {
            return false;
        }
        for (LyricUiPreset preset : new LyricUiPreset[]{DEFAULT, SOFT, VIVID, MINIMAL}) {
            LyricUiConfig candidate = preset.apply(config);
            int legacySpacing = preset == DEFAULT ? 110 : 130;
            if (sameAppearance(candidate, config)
                    && config.lineSpacingTenthsDp == legacySpacing) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameAppearance(LyricUiConfig left, LyricUiConfig right) {
        return left.inactiveOpacityPercent == right.inactiveOpacityPercent
                && left.blurEnabled == right.blurEnabled
                && left.blurRadiusTenthsPx == right.blurRadiusTenthsPx
                && left.scaleEnabled == right.scaleEnabled
                && left.inactiveScalePercent == right.inactiveScalePercent
                && left.glowEnabled == right.glowEnabled
                && (!left.glowEnabled
                        || left.glowIntensityPercent == right.glowIntensityPercent)
                && left.glowRadiusPercent == right.glowRadiusPercent
                && left.motionMode == right.motionMode
                && left.primaryColor.equals(right.primaryColor)
                && left.glowColor.equals(right.glowColor);
    }

    private static boolean sameTypography(LyricUiConfig left, LyricUiConfig right) {
        return left.mainFontTenthsSp == right.mainFontTenthsSp
                && left.translationFontRatioPercent == right.translationFontRatioPercent
                && left.fontWeight == right.fontWeight
                && left.alignment == right.alignment
                && left.lineSpacingTenthsDp == right.lineSpacingTenthsDp
                && left.wrappedLineSpacingTenthsDp == right.wrappedLineSpacingTenthsDp;
    }
}
