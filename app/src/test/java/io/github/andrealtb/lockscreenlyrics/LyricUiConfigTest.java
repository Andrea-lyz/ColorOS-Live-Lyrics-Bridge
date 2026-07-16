package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public final class LyricUiConfigTest {
    @Test
    public void defaultsMatchRendererBaseline() {
        LyricUiConfig config = LyricUiConfig.defaults();

        assertEquals(2, config.schemaVersion);
        assertEquals(44, config.inactiveOpacityPercent);
        assertFalse(config.blurEnabled);
        assertEquals(40, config.blurRadiusTenthsPx);
        assertTrue(config.glowEnabled);
        assertEquals("#FFFFFF", config.primaryColor);
        assertEquals("#FFD68A", config.glowColor);
        assertEquals(220, config.mainFontTenthsSp);
        assertEquals(66, config.translationFontRatioPercent);
        assertEquals(0, config.lineSpacingTenthsDp);
    }

    @Test
    public void clampsInvalidValuesAndColors() {
        LyricUiConfig config = new LyricUiConfig.Builder()
                .inactiveOpacityPercent(2)
                .blurRadiusTenthsPx(83)
                .inactiveScalePercent(120)
                .glowRadiusPercent(2)
                .primaryColor("#11223344")
                .glowColor("#abc123")
                .mainFontTenthsSp(999)
                .translationFontRatioPercent(3)
                .lineSpacingTenthsDp(203)
                .build();

        assertEquals(30, config.inactiveOpacityPercent);
        assertEquals(80, config.blurRadiusTenthsPx);
        assertEquals(100, config.inactiveScalePercent);
        assertEquals(10, config.glowRadiusPercent);
        assertEquals("#FFFFFF", config.primaryColor);
        assertEquals("#ABC123", config.glowColor);
        assertEquals(260, config.mainFontTenthsSp);
        assertEquals(55, config.translationFontRatioPercent);
        assertEquals(200, config.lineSpacingTenthsDp);

        LyricUiConfig compact = new LyricUiConfig.Builder()
                .inactiveScalePercent(-999)
                .lineSpacingTenthsDp(-999)
                .build();
        assertEquals(75, compact.inactiveScalePercent);
        assertEquals(-50, compact.lineSpacingTenthsDp);
    }

    @Test
    public void partialDecodeKeepsMissingFields() {
        LyricUiConfig baseline = new LyricUiConfig.Builder()
                .inactiveOpacityPercent(70)
                .glowEnabled(false)
                .build();
        Map<String, Object> values = new HashMap<>();
        values.put(LyricUiConfigCodec.SCHEMA, 2);
        values.put(LyricUiConfigCodec.BLUR_ENABLED, true);

        LyricUiConfig decoded = LyricUiConfigCodec.decode(values, baseline, false);

        assertTrue(decoded.blurEnabled);
        assertEquals(70, decoded.inactiveOpacityPercent);
        assertFalse(decoded.glowEnabled);
    }

    @Test
    public void codecRoundTripPreservesLineSpacing() {
        LyricUiConfig source = LyricUiConfig.defaults().buildUpon()
                .lineSpacingTenthsDp(65)
                .build();

        LyricUiConfig decoded = LyricUiConfigCodec.decode(
                LyricUiConfigCodec.encode(source),
                LyricUiConfig.defaults(),
                false);

        assertEquals(65, decoded.lineSpacingTenthsDp);
    }

    @Test
    public void migratesLegacyKeysAndRejectsUnknownSchema() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put(LyricUiConfigCodec.SCALE_ENABLED, true);
        LyricUiConfig migrated = LyricUiConfigCodec.decode(
                legacy, LyricUiConfig.defaults(), true);
        assertTrue(migrated.scaleEnabled);
        assertEquals(130, migrated.lineSpacingTenthsDp);

        Map<String, Object> future = new HashMap<>();
        future.put(LyricUiConfigCodec.SCHEMA, 99);
        assertNull(LyricUiConfigCodec.decode(
                future, LyricUiConfig.defaults(), false));
    }

    @Test
    public void resetAppearancePreservesPolicyFields() {
        LyricUiConfig config = new LyricUiConfig.Builder()
                .inactiveOpacityPercent(80)
                .screenTimeoutSeconds(30)
                .defaultTranslationEnabled(false)
                .maxRefreshRateHz(90)
                .build()
                .resetAppearance();

        assertEquals(44, config.inactiveOpacityPercent);
        assertEquals(30, config.screenTimeoutSeconds);
        assertFalse(config.defaultTranslationEnabled);
        assertEquals(90, config.maxRefreshRateHz);
    }

    @Test
    public void schemaOneMigratesAndDiscardsFormerMetadataDsl() {
        Map<String, Object> schemaOne = new HashMap<>();
        schemaOne.put(LyricUiConfigCodec.SCHEMA, 1);
        schemaOne.put(LyricUiConfigCodec.INACTIVE_OPACITY, 61);
        schemaOne.put(LyricUiConfigCodec.LEGACY_METADATA_CLEANUP_RULES, "title|trim");

        LyricUiConfig migrated = LyricUiConfigCodec.decode(
                schemaOne,
                LyricUiConfig.defaults(),
                true);

        assertEquals(61, migrated.inactiveOpacityPercent);
        assertEquals(2, migrated.schemaVersion);
        assertNull(LyricUiConfigCodec.decode(
                schemaOne,
                LyricUiConfig.defaults(),
                false));
    }

    @Test
    public void refreshRateAcceptsOnlySupportedFixedCaps() {
        assertEquals(0, LyricUiConfig.sanitizeRefreshRate(0));
        assertEquals(60, LyricUiConfig.sanitizeRefreshRate(60));
        assertEquals(90, LyricUiConfig.sanitizeRefreshRate(90));
        assertEquals(120, LyricUiConfig.sanitizeRefreshRate(120));
        assertEquals(0, LyricUiConfig.sanitizeRefreshRate(75));
    }

    @Test
    public void translationResetRecognizesGlobalAndPackageOverridesOnly() {
        assertTrue(LyricUiSettings.isTranslationOverrideKey(
                "lyric_info_translation_enabled"));
        assertTrue(LyricUiSettings.isTranslationOverrideKey(
                "lyric_info_translation_enabled.com.salt.music"));
        assertFalse(LyricUiSettings.isTranslationOverrideKey(
                "default_translation_enabled"));
        assertFalse(LyricUiSettings.isTranslationOverrideKey(
                "lyric_info_translation_default.com.salt.music"));
        assertFalse(LyricUiSettings.isTranslationOverrideKey(null));
    }
}
