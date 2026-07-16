package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class LyricUiConfig {
    static final int SCHEMA_VERSION = 2;
    static final int MOTION_STANDARD = 0;
    static final int MOTION_REDUCED = 1;
    static final int MOTION_OFF = 2;
    static final int WEIGHT_SYSTEM = 0;
    static final int WEIGHT_REGULAR = 1;
    static final int WEIGHT_MEDIUM = 2;
    static final int WEIGHT_BOLD = 3;
    static final int ALIGN_START = 0;
    static final int ALIGN_CENTER = 1;
    static final int ALIGN_END = 2;
    private static final Pattern OPAQUE_RGB = Pattern.compile("#[0-9A-Fa-f]{6}");

    final int schemaVersion;
    final int inactiveOpacityPercent;
    final boolean blurEnabled;
    final int blurRadiusTenthsPx;
    final boolean scaleEnabled;
    final int inactiveScalePercent;
    final boolean glowEnabled;
    final int glowIntensityPercent;
    final int glowRadiusPercent;
    final String primaryColor;
    final String glowColor;
    final int motionMode;
    final boolean passiveVerticalPanEnabled;
    final boolean translationMarqueeEnabled;
    final int maxRefreshRateHz;
    final boolean defaultTranslationEnabled;
    final boolean lineTimedProgressEnabled;
    final boolean translationProgressEnabled;
    final boolean screenTimeoutEnabled;
    final int screenTimeoutSeconds;
    final int mainFontTenthsSp;
    final int translationFontRatioPercent;
    final int fontWeight;
    final int alignment;
    final int lineSpacingTenthsDp;

    private LyricUiConfig(Builder builder) {
        schemaVersion = SCHEMA_VERSION;
        inactiveOpacityPercent = clamp(builder.inactiveOpacityPercent, 30, 100);
        blurEnabled = builder.blurEnabled;
        blurRadiusTenthsPx = roundStep(clamp(builder.blurRadiusTenthsPx, 0, 80), 5);
        scaleEnabled = builder.scaleEnabled;
        inactiveScalePercent = clamp(builder.inactiveScalePercent, 75, 100);
        glowEnabled = builder.glowEnabled;
        glowIntensityPercent = clamp(builder.glowIntensityPercent, 0, 100);
        glowRadiusPercent = clamp(builder.glowRadiusPercent, 10, 24);
        primaryColor = sanitizeColor(builder.primaryColor, "#FFFFFF");
        glowColor = sanitizeColor(builder.glowColor, "#FFD68A");
        motionMode = clamp(builder.motionMode, MOTION_STANDARD, MOTION_OFF);
        passiveVerticalPanEnabled = builder.passiveVerticalPanEnabled;
        translationMarqueeEnabled = builder.translationMarqueeEnabled;
        maxRefreshRateHz = sanitizeRefreshRate(builder.maxRefreshRateHz);
        defaultTranslationEnabled = builder.defaultTranslationEnabled;
        lineTimedProgressEnabled = builder.lineTimedProgressEnabled;
        translationProgressEnabled = builder.translationProgressEnabled;
        screenTimeoutEnabled = builder.screenTimeoutEnabled;
        screenTimeoutSeconds = LyricUiSettings.sanitizeScreenTimeoutSeconds(
                builder.screenTimeoutSeconds);
        mainFontTenthsSp = clamp(builder.mainFontTenthsSp, 180, 260);
        translationFontRatioPercent = clamp(builder.translationFontRatioPercent, 55, 75);
        fontWeight = clamp(builder.fontWeight, WEIGHT_SYSTEM, WEIGHT_BOLD);
        alignment = clamp(builder.alignment, ALIGN_START, ALIGN_END);
        lineSpacingTenthsDp = roundStep(clamp(builder.lineSpacingTenthsDp, -50, 200), 5);
    }

    static LyricUiConfig defaults() {
        return new Builder().build();
    }

    Builder buildUpon() {
        return new Builder(this);
    }

    LyricUiConfig resetAppearance() {
        LyricUiConfig defaults = defaults();
        return buildUpon()
                .inactiveOpacityPercent(defaults.inactiveOpacityPercent)
                .blurEnabled(defaults.blurEnabled)
                .blurRadiusTenthsPx(defaults.blurRadiusTenthsPx)
                .scaleEnabled(defaults.scaleEnabled)
                .inactiveScalePercent(defaults.inactiveScalePercent)
                .glowEnabled(defaults.glowEnabled)
                .glowIntensityPercent(defaults.glowIntensityPercent)
                .glowRadiusPercent(defaults.glowRadiusPercent)
                .primaryColor(defaults.primaryColor)
                .glowColor(defaults.glowColor)
                .motionMode(defaults.motionMode)
                .mainFontTenthsSp(defaults.mainFontTenthsSp)
                .translationFontRatioPercent(defaults.translationFontRatioPercent)
                .fontWeight(defaults.fontWeight)
                .alignment(defaults.alignment)
                .lineSpacingTenthsDp(defaults.lineSpacingTenthsDp)
                .build();
    }

    static int sanitizeRefreshRate(int value) {
        return value == 60 || value == 90 || value == 120 ? value : 0;
    }

    static String sanitizeColor(String value, String fallback) {
        if (value == null || !OPAQUE_RGB.matcher(value.trim()).matches()) return fallback;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int roundStep(int value, int step) {
        return Math.round(value / (float) step) * step;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof LyricUiConfig)) return false;
        LyricUiConfig other = (LyricUiConfig) object;
        return inactiveOpacityPercent == other.inactiveOpacityPercent
                && blurEnabled == other.blurEnabled
                && blurRadiusTenthsPx == other.blurRadiusTenthsPx
                && scaleEnabled == other.scaleEnabled
                && inactiveScalePercent == other.inactiveScalePercent
                && glowEnabled == other.glowEnabled
                && glowIntensityPercent == other.glowIntensityPercent
                && glowRadiusPercent == other.glowRadiusPercent
                && motionMode == other.motionMode
                && passiveVerticalPanEnabled == other.passiveVerticalPanEnabled
                && translationMarqueeEnabled == other.translationMarqueeEnabled
                && maxRefreshRateHz == other.maxRefreshRateHz
                && defaultTranslationEnabled == other.defaultTranslationEnabled
                && lineTimedProgressEnabled == other.lineTimedProgressEnabled
                && translationProgressEnabled == other.translationProgressEnabled
                && screenTimeoutEnabled == other.screenTimeoutEnabled
                && screenTimeoutSeconds == other.screenTimeoutSeconds
                && mainFontTenthsSp == other.mainFontTenthsSp
                && translationFontRatioPercent == other.translationFontRatioPercent
                && fontWeight == other.fontWeight
                && alignment == other.alignment
                && lineSpacingTenthsDp == other.lineSpacingTenthsDp
                && primaryColor.equals(other.primaryColor)
                && glowColor.equals(other.glowColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                inactiveOpacityPercent, blurEnabled, blurRadiusTenthsPx,
                scaleEnabled, inactiveScalePercent, glowEnabled, glowIntensityPercent,
                glowRadiusPercent, primaryColor, glowColor, motionMode,
                passiveVerticalPanEnabled, translationMarqueeEnabled, maxRefreshRateHz,
                defaultTranslationEnabled, lineTimedProgressEnabled,
                translationProgressEnabled, screenTimeoutEnabled, screenTimeoutSeconds,
                mainFontTenthsSp, translationFontRatioPercent, fontWeight, alignment,
                lineSpacingTenthsDp);
    }

    static final class Builder {
        private int inactiveOpacityPercent = 44;
        private boolean blurEnabled;
        private int blurRadiusTenthsPx = 40;
        private boolean scaleEnabled;
        private int inactiveScalePercent = 90;
        private boolean glowEnabled = true;
        private int glowIntensityPercent = 100;
        private int glowRadiusPercent = 18;
        private String primaryColor = "#FFFFFF";
        private String glowColor = "#FFD68A";
        private int motionMode = MOTION_STANDARD;
        private boolean passiveVerticalPanEnabled = true;
        private boolean translationMarqueeEnabled = true;
        private int maxRefreshRateHz;
        private boolean defaultTranslationEnabled = true;
        private boolean lineTimedProgressEnabled;
        private boolean translationProgressEnabled;
        private boolean screenTimeoutEnabled = true;
        private int screenTimeoutSeconds;
        private int mainFontTenthsSp = 220;
        private int translationFontRatioPercent = 66;
        private int fontWeight = WEIGHT_SYSTEM;
        private int alignment = ALIGN_START;
        private int lineSpacingTenthsDp;

        Builder() {
        }

        Builder(LyricUiConfig source) {
            inactiveOpacityPercent = source.inactiveOpacityPercent;
            blurEnabled = source.blurEnabled;
            blurRadiusTenthsPx = source.blurRadiusTenthsPx;
            scaleEnabled = source.scaleEnabled;
            inactiveScalePercent = source.inactiveScalePercent;
            glowEnabled = source.glowEnabled;
            glowIntensityPercent = source.glowIntensityPercent;
            glowRadiusPercent = source.glowRadiusPercent;
            primaryColor = source.primaryColor;
            glowColor = source.glowColor;
            motionMode = source.motionMode;
            passiveVerticalPanEnabled = source.passiveVerticalPanEnabled;
            translationMarqueeEnabled = source.translationMarqueeEnabled;
            maxRefreshRateHz = source.maxRefreshRateHz;
            defaultTranslationEnabled = source.defaultTranslationEnabled;
            lineTimedProgressEnabled = source.lineTimedProgressEnabled;
            translationProgressEnabled = source.translationProgressEnabled;
            screenTimeoutEnabled = source.screenTimeoutEnabled;
            screenTimeoutSeconds = source.screenTimeoutSeconds;
            mainFontTenthsSp = source.mainFontTenthsSp;
            translationFontRatioPercent = source.translationFontRatioPercent;
            fontWeight = source.fontWeight;
            alignment = source.alignment;
            lineSpacingTenthsDp = source.lineSpacingTenthsDp;
        }

        Builder inactiveOpacityPercent(int v) { inactiveOpacityPercent = v; return this; }
        Builder blurEnabled(boolean v) { blurEnabled = v; return this; }
        Builder blurRadiusTenthsPx(int v) { blurRadiusTenthsPx = v; return this; }
        Builder scaleEnabled(boolean v) { scaleEnabled = v; return this; }
        Builder inactiveScalePercent(int v) { inactiveScalePercent = v; return this; }
        Builder glowEnabled(boolean v) { glowEnabled = v; return this; }
        Builder glowIntensityPercent(int v) { glowIntensityPercent = v; return this; }
        Builder glowRadiusPercent(int v) { glowRadiusPercent = v; return this; }
        Builder primaryColor(String v) { primaryColor = v; return this; }
        Builder glowColor(String v) { glowColor = v; return this; }
        Builder motionMode(int v) { motionMode = v; return this; }
        Builder passiveVerticalPanEnabled(boolean v) { passiveVerticalPanEnabled = v; return this; }
        Builder translationMarqueeEnabled(boolean v) { translationMarqueeEnabled = v; return this; }
        Builder maxRefreshRateHz(int v) { maxRefreshRateHz = v; return this; }
        Builder defaultTranslationEnabled(boolean v) { defaultTranslationEnabled = v; return this; }
        Builder lineTimedProgressEnabled(boolean v) { lineTimedProgressEnabled = v; return this; }
        Builder translationProgressEnabled(boolean v) { translationProgressEnabled = v; return this; }
        Builder screenTimeoutEnabled(boolean v) { screenTimeoutEnabled = v; return this; }
        Builder screenTimeoutSeconds(int v) { screenTimeoutSeconds = v; return this; }
        Builder mainFontTenthsSp(int v) { mainFontTenthsSp = v; return this; }
        Builder translationFontRatioPercent(int v) { translationFontRatioPercent = v; return this; }
        Builder fontWeight(int v) { fontWeight = v; return this; }
        Builder alignment(int v) { alignment = v; return this; }
        Builder lineSpacingTenthsDp(int v) { lineSpacingTenthsDp = v; return this; }
        LyricUiConfig build() { return new LyricUiConfig(this); }
    }
}
