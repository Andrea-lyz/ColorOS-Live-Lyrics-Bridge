package io.github.andrealtb.lockscreenlyrics;

import java.util.LinkedHashMap;
import java.util.Map;

final class LyricUiConfigCodec {
    static final String SCHEMA = "lyric_ui_schema";
    static final String INACTIVE_OPACITY = "inactive_opacity_percent";
    static final String BLUR_ENABLED = "lyric_ui_inactive_blur_enabled";
    static final String BLUR_RADIUS = "blur_radius_tenths_px";
    static final String SCALE_ENABLED = "lyric_ui_scroll_scale_enabled";
    static final String INACTIVE_SCALE = "inactive_scale_percent";
    static final String GLOW_ENABLED = "glow_enabled";
    static final String GLOW_INTENSITY = "glow_intensity_percent";
    static final String GLOW_RADIUS = "glow_radius_percent";
    static final String PRIMARY_COLOR = "primary_color";
    static final String GLOW_COLOR = "glow_color";
    static final String MOTION_MODE = "motion_mode";
    static final String PASSIVE_VERTICAL_PAN = "passive_vertical_pan_enabled";
    static final String TRANSLATION_MARQUEE = "translation_marquee_enabled";
    static final String MAX_REFRESH_RATE = "max_refresh_rate_hz";
    static final String DEFAULT_TRANSLATION = "default_translation_enabled";
    static final String LINE_TIMED_PROGRESS = "lyric_ui_line_timed_progress_enabled";
    static final String TRANSLATION_PROGRESS = "lyric_ui_translation_progress_enabled";
    static final String SCREEN_TIMEOUT_ENABLED = "screen_timeout_enabled";
    static final String SCREEN_TIMEOUT_SECONDS = "screen_timeout_seconds";
    static final String MAIN_FONT_SIZE = "main_font_tenths_sp";
    static final String TRANSLATION_FONT_RATIO = "translation_font_ratio_percent";
    static final String FONT_WEIGHT = "font_weight";
    static final String ALIGNMENT = "alignment";
    static final String LINE_SPACING = "line_spacing_tenths_dp";
    // Schema 1 only. Kept so migration can explicitly discard the former metadata DSL.
    static final String LEGACY_METADATA_CLEANUP_RULES = "metadata_cleanup_rules";

    private LyricUiConfigCodec() {
    }

    static Map<String, Object> encode(LyricUiConfig config) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(SCHEMA, LyricUiConfig.SCHEMA_VERSION);
        values.put(INACTIVE_OPACITY, config.inactiveOpacityPercent);
        values.put(BLUR_ENABLED, config.blurEnabled);
        values.put(BLUR_RADIUS, config.blurRadiusTenthsPx);
        values.put(SCALE_ENABLED, config.scaleEnabled);
        values.put(INACTIVE_SCALE, config.inactiveScalePercent);
        values.put(GLOW_ENABLED, config.glowEnabled);
        values.put(GLOW_INTENSITY, config.glowIntensityPercent);
        values.put(GLOW_RADIUS, config.glowRadiusPercent);
        values.put(PRIMARY_COLOR, config.primaryColor);
        values.put(GLOW_COLOR, config.glowColor);
        values.put(MOTION_MODE, config.motionMode);
        values.put(PASSIVE_VERTICAL_PAN, config.passiveVerticalPanEnabled);
        values.put(TRANSLATION_MARQUEE, config.translationMarqueeEnabled);
        values.put(MAX_REFRESH_RATE, config.maxRefreshRateHz);
        values.put(DEFAULT_TRANSLATION, config.defaultTranslationEnabled);
        values.put(LINE_TIMED_PROGRESS, config.lineTimedProgressEnabled);
        values.put(TRANSLATION_PROGRESS, config.translationProgressEnabled);
        values.put(SCREEN_TIMEOUT_ENABLED, config.screenTimeoutEnabled);
        values.put(SCREEN_TIMEOUT_SECONDS, config.screenTimeoutSeconds);
        values.put(MAIN_FONT_SIZE, config.mainFontTenthsSp);
        values.put(TRANSLATION_FONT_RATIO, config.translationFontRatioPercent);
        values.put(FONT_WEIGHT, config.fontWeight);
        values.put(ALIGNMENT, config.alignment);
        values.put(LINE_SPACING, config.lineSpacingTenthsDp);
        return values;
    }

    static LyricUiConfig decode(
            Map<String, ?> values, LyricUiConfig baseline, boolean allowLegacy) {
        LyricUiConfig base = baseline == null ? LyricUiConfig.defaults() : baseline;
        if (values == null || values.isEmpty()) return base;
        if (values.containsKey(SCHEMA)) {
            int schema = integer(values.get(SCHEMA), -1);
            if (schema != LyricUiConfig.SCHEMA_VERSION && !(allowLegacy && schema == 1)) {
                return null;
            }
        } else if (!allowLegacy) {
            return null;
        }
        LyricUiConfig.Builder builder = base.buildUpon();
        if (values.containsKey(INACTIVE_OPACITY)) builder.inactiveOpacityPercent(integer(values.get(INACTIVE_OPACITY), base.inactiveOpacityPercent));
        if (values.containsKey(BLUR_ENABLED)) builder.blurEnabled(bool(values.get(BLUR_ENABLED), base.blurEnabled));
        if (values.containsKey(BLUR_RADIUS)) builder.blurRadiusTenthsPx(integer(values.get(BLUR_RADIUS), base.blurRadiusTenthsPx));
        if (values.containsKey(SCALE_ENABLED)) builder.scaleEnabled(bool(values.get(SCALE_ENABLED), base.scaleEnabled));
        if (values.containsKey(INACTIVE_SCALE)) builder.inactiveScalePercent(integer(values.get(INACTIVE_SCALE), base.inactiveScalePercent));
        if (values.containsKey(GLOW_ENABLED)) builder.glowEnabled(bool(values.get(GLOW_ENABLED), base.glowEnabled));
        if (values.containsKey(GLOW_INTENSITY)) builder.glowIntensityPercent(integer(values.get(GLOW_INTENSITY), base.glowIntensityPercent));
        if (values.containsKey(GLOW_RADIUS)) builder.glowRadiusPercent(integer(values.get(GLOW_RADIUS), base.glowRadiusPercent));
        if (values.containsKey(PRIMARY_COLOR)) builder.primaryColor(string(values.get(PRIMARY_COLOR), base.primaryColor));
        if (values.containsKey(GLOW_COLOR)) builder.glowColor(string(values.get(GLOW_COLOR), base.glowColor));
        if (values.containsKey(MOTION_MODE)) builder.motionMode(integer(values.get(MOTION_MODE), base.motionMode));
        if (values.containsKey(PASSIVE_VERTICAL_PAN)) builder.passiveVerticalPanEnabled(bool(values.get(PASSIVE_VERTICAL_PAN), base.passiveVerticalPanEnabled));
        if (values.containsKey(TRANSLATION_MARQUEE)) builder.translationMarqueeEnabled(bool(values.get(TRANSLATION_MARQUEE), base.translationMarqueeEnabled));
        if (values.containsKey(MAX_REFRESH_RATE)) builder.maxRefreshRateHz(integer(values.get(MAX_REFRESH_RATE), base.maxRefreshRateHz));
        if (values.containsKey(DEFAULT_TRANSLATION)) builder.defaultTranslationEnabled(bool(values.get(DEFAULT_TRANSLATION), base.defaultTranslationEnabled));
        if (values.containsKey(LINE_TIMED_PROGRESS)) builder.lineTimedProgressEnabled(bool(values.get(LINE_TIMED_PROGRESS), base.lineTimedProgressEnabled));
        if (values.containsKey(TRANSLATION_PROGRESS)) builder.translationProgressEnabled(bool(values.get(TRANSLATION_PROGRESS), base.translationProgressEnabled));
        if (values.containsKey(SCREEN_TIMEOUT_ENABLED)) builder.screenTimeoutEnabled(bool(values.get(SCREEN_TIMEOUT_ENABLED), base.screenTimeoutEnabled));
        if (values.containsKey(SCREEN_TIMEOUT_SECONDS)) builder.screenTimeoutSeconds(integer(values.get(SCREEN_TIMEOUT_SECONDS), base.screenTimeoutSeconds));
        if (values.containsKey(MAIN_FONT_SIZE)) builder.mainFontTenthsSp(integer(values.get(MAIN_FONT_SIZE), base.mainFontTenthsSp));
        if (values.containsKey(TRANSLATION_FONT_RATIO)) builder.translationFontRatioPercent(integer(values.get(TRANSLATION_FONT_RATIO), base.translationFontRatioPercent));
        if (values.containsKey(FONT_WEIGHT)) builder.fontWeight(integer(values.get(FONT_WEIGHT), base.fontWeight));
        if (values.containsKey(ALIGNMENT)) builder.alignment(integer(values.get(ALIGNMENT), base.alignment));
        if (values.containsKey(LINE_SPACING)) builder.lineSpacingTenthsDp(integer(values.get(LINE_SPACING), base.lineSpacingTenthsDp));
        LyricUiConfig decoded = builder.build();
        if (allowLegacy && !values.containsKey(LINE_SPACING)) {
            decoded = decoded.buildUpon()
                    .lineSpacingTenthsDp(LyricUiLayoutPolicy.legacyLineSpacingTenthsDp(decoded))
                    .build();
        }
        return decoded;
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        return fallback;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
