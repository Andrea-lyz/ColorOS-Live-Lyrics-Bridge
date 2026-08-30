package io.github.andrealtb.lockscreenlyrics;

import java.util.LinkedHashMap;
import java.util.Map;

final class LyricUiConfigCodec {
    static final String SCHEMA = "lyric_ui_schema";
    static final String ACTIVE_OPACITY = "active_opacity_percent";
    static final String CURRENT_UNREVEALED_OPACITY =
            "current_unrevealed_opacity_percent";
    static final String ACTIVE_TRANSLATION_OPACITY =
            "active_translation_opacity_percent";
    static final String ACTIVE_TRANSLATION_PROGRESS_OPACITY =
            "active_translation_progress_opacity_percent";
    static final String INACTIVE_OPACITY = "inactive_opacity_percent";
    static final String INACTIVE_TRANSLATION_FOLLOWS_MAIN =
            "inactive_translation_follows_main";
    static final String INACTIVE_TRANSLATION_OPACITY =
            "inactive_translation_opacity_percent";
    static final String VERTICAL_FADE_ENABLED = "vertical_fade_enabled";
    static final String VERTICAL_FADE_LENGTH = "vertical_fade_length_tenths_dp";
    static final String INACTIVE_ROW_FADE_ENABLED = "inactive_row_fade_enabled";
    static final String INACTIVE_ROW_FADE = "inactive_row_fade_percent";
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
    static final String WRAPPED_LINE_SPACING = "wrapped_line_spacing_tenths_dp";
    // Schema 1 only. Kept so migration can explicitly discard the former metadata DSL.
    static final String LEGACY_METADATA_CLEANUP_RULES = "metadata_cleanup_rules";

    private LyricUiConfigCodec() {
    }

    static Map<String, Object> encode(LyricUiConfig config) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(SCHEMA, LyricUiConfig.SCHEMA_VERSION);
        values.put(ACTIVE_OPACITY, config.activeOpacityPercent);
        values.put(CURRENT_UNREVEALED_OPACITY, config.currentUnrevealedOpacityPercent);
        values.put(ACTIVE_TRANSLATION_OPACITY, config.activeTranslationOpacityPercent);
        values.put(
                ACTIVE_TRANSLATION_PROGRESS_OPACITY,
                config.activeTranslationProgressOpacityPercent);
        values.put(INACTIVE_OPACITY, config.inactiveOpacityPercent);
        values.put(
                INACTIVE_TRANSLATION_FOLLOWS_MAIN,
                config.inactiveTranslationFollowsMain);
        values.put(INACTIVE_TRANSLATION_OPACITY, config.inactiveTranslationOpacityPercent);
        values.put(VERTICAL_FADE_ENABLED, config.verticalFadeEnabled);
        values.put(VERTICAL_FADE_LENGTH, config.verticalFadeLengthTenthsDp);
        values.put(INACTIVE_ROW_FADE_ENABLED, config.inactiveRowFadeEnabled);
        values.put(INACTIVE_ROW_FADE, config.inactiveRowFadePercent);
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
        values.put(WRAPPED_LINE_SPACING, config.wrappedLineSpacingTenthsDp);
        return values;
    }

    static LyricUiConfig decode(
            Map<String, ?> values, LyricUiConfig baseline, boolean allowLegacy) {
        LyricUiConfig base = baseline == null ? LyricUiConfig.defaults() : baseline;
        if (values == null || values.isEmpty()) return base;
        int schema = values.containsKey(SCHEMA)
                ? integer(values.get(SCHEMA), -1)
                : 1;
        if (values.containsKey(SCHEMA)) {
            if (schema != LyricUiConfig.SCHEMA_VERSION
                    && !(allowLegacy && (schema == 1 || schema == 2))) {
                return null;
            }
        } else if (!allowLegacy) {
            return null;
        }
        boolean migrateVisualLayers = allowLegacy && (schema == 1 || schema == 2);
        LyricUiConfig.Builder builder = base.buildUpon();
        if (values.containsKey(ACTIVE_OPACITY)) builder.activeOpacityPercent(integer(values.get(ACTIVE_OPACITY), base.activeOpacityPercent));
        if (values.containsKey(CURRENT_UNREVEALED_OPACITY)) builder.currentUnrevealedOpacityPercent(integer(values.get(CURRENT_UNREVEALED_OPACITY), base.currentUnrevealedOpacityPercent));
        if (values.containsKey(ACTIVE_TRANSLATION_OPACITY)) builder.activeTranslationOpacityPercent(integer(values.get(ACTIVE_TRANSLATION_OPACITY), base.activeTranslationOpacityPercent));
        if (values.containsKey(ACTIVE_TRANSLATION_PROGRESS_OPACITY)) builder.activeTranslationProgressOpacityPercent(integer(values.get(ACTIVE_TRANSLATION_PROGRESS_OPACITY), base.activeTranslationProgressOpacityPercent));
        if (values.containsKey(INACTIVE_OPACITY)) builder.inactiveOpacityPercent(integer(values.get(INACTIVE_OPACITY), base.inactiveOpacityPercent));
        if (values.containsKey(INACTIVE_TRANSLATION_FOLLOWS_MAIN)) builder.inactiveTranslationFollowsMain(bool(values.get(INACTIVE_TRANSLATION_FOLLOWS_MAIN), base.inactiveTranslationFollowsMain));
        if (values.containsKey(INACTIVE_TRANSLATION_OPACITY)) builder.inactiveTranslationOpacityPercent(integer(values.get(INACTIVE_TRANSLATION_OPACITY), base.inactiveTranslationOpacityPercent));
        if (values.containsKey(VERTICAL_FADE_ENABLED)) builder.verticalFadeEnabled(bool(values.get(VERTICAL_FADE_ENABLED), base.verticalFadeEnabled));
        if (values.containsKey(VERTICAL_FADE_LENGTH)) builder.verticalFadeLengthTenthsDp(integer(values.get(VERTICAL_FADE_LENGTH), base.verticalFadeLengthTenthsDp));
        if (values.containsKey(INACTIVE_ROW_FADE_ENABLED)) builder.inactiveRowFadeEnabled(bool(values.get(INACTIVE_ROW_FADE_ENABLED), base.inactiveRowFadeEnabled));
        if (values.containsKey(INACTIVE_ROW_FADE)) builder.inactiveRowFadePercent(integer(values.get(INACTIVE_ROW_FADE), base.inactiveRowFadePercent));
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
        if (values.containsKey(WRAPPED_LINE_SPACING)) builder.wrappedLineSpacingTenthsDp(integer(values.get(WRAPPED_LINE_SPACING), base.wrappedLineSpacingTenthsDp));
        LyricUiConfig decoded = builder.build();
        if (migrateVisualLayers) {
            decoded = decoded.buildUpon()
                    .inactiveTranslationOpacityPercent(decoded.inactiveOpacityPercent)
                    .inactiveRowFadeEnabled(decoded.scaleEnabled || decoded.blurEnabled)
                    .build();
        }
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
