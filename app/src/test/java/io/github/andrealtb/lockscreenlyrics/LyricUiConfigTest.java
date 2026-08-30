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

        assertEquals(3, config.schemaVersion);
        assertEquals(100, config.activeOpacityPercent);
        assertEquals(50, config.currentUnrevealedOpacityPercent);
        assertEquals(60, config.activeTranslationOpacityPercent);
        assertEquals(80, config.activeTranslationProgressOpacityPercent);
        assertEquals(44, config.inactiveOpacityPercent);
        assertTrue(config.inactiveTranslationFollowsMain);
        assertEquals(44, config.inactiveTranslationOpacityPercent);
        assertTrue(config.verticalFadeEnabled);
        assertEquals(519, config.verticalFadeLengthTenthsDp);
        assertFalse(config.inactiveRowFadeEnabled);
        assertEquals(90, config.inactiveRowFadePercent);
        assertFalse(config.blurEnabled);
        assertEquals(40, config.blurRadiusTenthsPx);
        assertTrue(config.glowEnabled);
        assertEquals("#FFFFFF", config.primaryColor);
        assertEquals("#FFD68A", config.glowColor);
        assertEquals(220, config.mainFontTenthsSp);
        assertEquals(66, config.translationFontRatioPercent);
        assertEquals(0, config.lineSpacingTenthsDp);
        assertEquals(0, config.wrappedLineSpacingTenthsDp);
    }

    @Test
    public void clampsInvalidValuesAndColors() {
        LyricUiConfig config = new LyricUiConfig.Builder()
                .activeOpacityPercent(30)
                .currentUnrevealedOpacityPercent(99)
                .activeTranslationOpacityPercent(80)
                .activeTranslationProgressOpacityPercent(120)
                .inactiveOpacityPercent(2)
                .inactiveTranslationOpacityPercent(2)
                .verticalFadeLengthTenthsDp(1201)
                .inactiveRowFadePercent(2)
                .blurRadiusTenthsPx(83)
                .inactiveScalePercent(120)
                .glowRadiusPercent(2)
                .primaryColor("#11223344")
                .glowColor("#abc123")
                .mainFontTenthsSp(999)
                .translationFontRatioPercent(3)
                .lineSpacingTenthsDp(203)
                .wrappedLineSpacingTenthsDp(83)
                .build();

        assertEquals(50, config.activeOpacityPercent);
        assertEquals(50, config.currentUnrevealedOpacityPercent);
        assertEquals(50, config.activeTranslationOpacityPercent);
        assertEquals(50, config.activeTranslationProgressOpacityPercent);
        assertEquals(30, config.inactiveOpacityPercent);
        assertEquals(20, config.inactiveTranslationOpacityPercent);
        assertEquals(1200, config.verticalFadeLengthTenthsDp);
        assertEquals(50, config.inactiveRowFadePercent);
        assertEquals(80, config.blurRadiusTenthsPx);
        assertEquals(100, config.inactiveScalePercent);
        assertEquals(10, config.glowRadiusPercent);
        assertEquals("#FFFFFF", config.primaryColor);
        assertEquals("#ABC123", config.glowColor);
        assertEquals(280, config.mainFontTenthsSp);
        assertEquals(55, config.translationFontRatioPercent);
        assertEquals(200, config.lineSpacingTenthsDp);
        assertEquals(80, config.wrappedLineSpacingTenthsDp);

        LyricUiConfig compact = new LyricUiConfig.Builder()
                .inactiveScalePercent(-999)
                .verticalFadeLengthTenthsDp(-999)
                .lineSpacingTenthsDp(-999)
                .wrappedLineSpacingTenthsDp(-999)
                .build();
        assertEquals(75, compact.inactiveScalePercent);
        assertEquals(0, compact.verticalFadeLengthTenthsDp);
        assertEquals(-50, compact.lineSpacingTenthsDp);
        assertEquals(-10, compact.wrappedLineSpacingTenthsDp);
    }

    @Test
    public void partialDecodeKeepsMissingFields() {
        LyricUiConfig baseline = new LyricUiConfig.Builder()
                .inactiveOpacityPercent(70)
                .glowEnabled(false)
                .wrappedLineSpacingTenthsDp(30)
                .build();
        Map<String, Object> values = new HashMap<>();
        values.put(LyricUiConfigCodec.SCHEMA, 3);
        values.put(LyricUiConfigCodec.BLUR_ENABLED, true);

        LyricUiConfig decoded = LyricUiConfigCodec.decode(values, baseline, false);

        assertTrue(decoded.blurEnabled);
        assertEquals(70, decoded.inactiveOpacityPercent);
        assertFalse(decoded.glowEnabled);
        assertEquals(30, decoded.wrappedLineSpacingTenthsDp);
    }

    @Test
    public void activeLaneConstraintsKeepTheBrightnessHierarchy() {
        LyricUiConfig config = LyricUiConfig.defaults().buildUpon()
                .activeOpacityPercent(72)
                .currentUnrevealedOpacityPercent(88)
                .activeTranslationProgressOpacityPercent(68)
                .activeTranslationOpacityPercent(91)
                .build();

        assertEquals(72, config.activeOpacityPercent);
        assertEquals(72, config.currentUnrevealedOpacityPercent);
        assertEquals(68, config.activeTranslationProgressOpacityPercent);
        assertEquals(68, config.activeTranslationOpacityPercent);
    }

    @Test
    public void codecRoundTripPreservesLineSpacing() {
        LyricUiConfig source = LyricUiConfig.defaults().buildUpon()
                .lineSpacingTenthsDp(65)
                .wrappedLineSpacingTenthsDp(25)
                .alignment(LyricUiConfig.ALIGN_CENTER)
                .build();

        LyricUiConfig decoded = LyricUiConfigCodec.decode(
                LyricUiConfigCodec.encode(source),
                LyricUiConfig.defaults(),
                false);

        assertEquals(65, decoded.lineSpacingTenthsDp);
        assertEquals(25, decoded.wrappedLineSpacingTenthsDp);
        assertEquals(LyricUiConfig.ALIGN_CENTER, decoded.alignment);
    }

    @Test
    public void playerTranslationUpdatePreservesTypographyAndAppearance() {
        LyricUiConfig source = LyricUiConfig.defaults().buildUpon()
                .activeOpacityPercent(88)
                .currentUnrevealedOpacityPercent(41)
                .activeTranslationOpacityPercent(57)
                .activeTranslationProgressOpacityPercent(76)
                .alignment(LyricUiConfig.ALIGN_CENTER)
                .mainFontTenthsSp(260)
                .lineSpacingTenthsDp(-35)
                .wrappedLineSpacingTenthsDp(30)
                .inactiveOpacityPercent(73)
                .inactiveTranslationFollowsMain(false)
                .inactiveTranslationOpacityPercent(38)
                .verticalFadeEnabled(false)
                .verticalFadeLengthTenthsDp(640)
                .inactiveRowFadeEnabled(true)
                .inactiveRowFadePercent(82)
                .glowEnabled(false)
                .defaultTranslationEnabled(true)
                .build();

        LyricUiConfig updated = LyricUiSettings.withGlobalTranslationDefault(source, false);

        assertFalse(updated.defaultTranslationEnabled);
        assertEquals(88, updated.activeOpacityPercent);
        assertEquals(41, updated.currentUnrevealedOpacityPercent);
        assertEquals(57, updated.activeTranslationOpacityPercent);
        assertEquals(76, updated.activeTranslationProgressOpacityPercent);
        assertEquals(LyricUiConfig.ALIGN_CENTER, updated.alignment);
        assertEquals(260, updated.mainFontTenthsSp);
        assertEquals(-35, updated.lineSpacingTenthsDp);
        assertEquals(30, updated.wrappedLineSpacingTenthsDp);
        assertEquals(73, updated.inactiveOpacityPercent);
        assertFalse(updated.inactiveTranslationFollowsMain);
        assertEquals(38, updated.inactiveTranslationOpacityPercent);
        assertFalse(updated.verticalFadeEnabled);
        assertEquals(640, updated.verticalFadeLengthTenthsDp);
        assertTrue(updated.inactiveRowFadeEnabled);
        assertEquals(82, updated.inactiveRowFadePercent);
        assertFalse(updated.glowEnabled);
    }

    @Test
    public void settingsRevisionIsStrictlyMonotonic() {
        long first = LyricUiSettings.newSettingsRevision();
        long second = LyricUiSettings.newSettingsRevision();

        assertTrue(second > first);
    }

    @Test
    public void migratesLegacyKeysAndRejectsUnknownSchema() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put(LyricUiConfigCodec.SCALE_ENABLED, true);
        legacy.put(LyricUiConfigCodec.INACTIVE_OPACITY, 63);
        LyricUiConfig migrated = LyricUiConfigCodec.decode(
                legacy, LyricUiConfig.defaults(), true);
        assertTrue(migrated.scaleEnabled);
        assertTrue(migrated.inactiveRowFadeEnabled);
        assertEquals(63, migrated.inactiveTranslationOpacityPercent);
        assertEquals(130, migrated.lineSpacingTenthsDp);

        Map<String, Object> future = new HashMap<>();
        future.put(LyricUiConfigCodec.SCHEMA, 99);
        assertNull(LyricUiConfigCodec.decode(
                future, LyricUiConfig.defaults(), false));
    }

    @Test
    public void resetAppearancePreservesPolicyFields() {
        LyricUiConfig config = new LyricUiConfig.Builder()
                .activeOpacityPercent(77)
                .currentUnrevealedOpacityPercent(45)
                .activeTranslationOpacityPercent(43)
                .activeTranslationProgressOpacityPercent(65)
                .inactiveOpacityPercent(80)
                .inactiveTranslationFollowsMain(false)
                .inactiveTranslationOpacityPercent(52)
                .verticalFadeEnabled(false)
                .verticalFadeLengthTenthsDp(700)
                .inactiveRowFadeEnabled(true)
                .inactiveRowFadePercent(75)
                .wrappedLineSpacingTenthsDp(30)
                .screenTimeoutSeconds(30)
                .defaultTranslationEnabled(false)
                .maxRefreshRateHz(90)
                .build()
                .resetAppearance();

        assertEquals(100, config.activeOpacityPercent);
        assertEquals(50, config.currentUnrevealedOpacityPercent);
        assertEquals(60, config.activeTranslationOpacityPercent);
        assertEquals(80, config.activeTranslationProgressOpacityPercent);
        assertEquals(44, config.inactiveOpacityPercent);
        assertTrue(config.inactiveTranslationFollowsMain);
        assertEquals(44, config.inactiveTranslationOpacityPercent);
        assertTrue(config.verticalFadeEnabled);
        assertEquals(519, config.verticalFadeLengthTenthsDp);
        assertFalse(config.inactiveRowFadeEnabled);
        assertEquals(90, config.inactiveRowFadePercent);
        assertEquals(0, config.wrappedLineSpacingTenthsDp);
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
        assertEquals(61, migrated.inactiveTranslationOpacityPercent);
        assertFalse(migrated.inactiveRowFadeEnabled);
        assertEquals(3, migrated.schemaVersion);
        assertNull(LyricUiConfigCodec.decode(
                schemaOne,
                LyricUiConfig.defaults(),
                false));
    }

    @Test
    public void schemaTwoMigratesRowFadeAndInactiveTranslationWithoutVisualDrift() {
        Map<String, Object> schemaTwo = new HashMap<>();
        schemaTwo.put(LyricUiConfigCodec.SCHEMA, 2);
        schemaTwo.put(LyricUiConfigCodec.INACTIVE_OPACITY, 72);
        schemaTwo.put(LyricUiConfigCodec.BLUR_ENABLED, true);
        schemaTwo.put(LyricUiConfigCodec.SCALE_ENABLED, false);

        LyricUiConfig migrated = LyricUiConfigCodec.decode(
                schemaTwo,
                LyricUiConfig.defaults(),
                true);

        assertEquals(3, migrated.schemaVersion);
        assertEquals(72, migrated.inactiveOpacityPercent);
        assertEquals(72, migrated.inactiveTranslationOpacityPercent);
        assertTrue(migrated.inactiveTranslationFollowsMain);
        assertTrue(migrated.inactiveRowFadeEnabled);
        assertEquals(90, migrated.inactiveRowFadePercent);
        assertEquals(166, LyricVisualAlphaPolicy.steadyInactiveMainAlpha(migrated));
    }

    @Test
    public void schemaThreePartialDecodeKeepsDormantVisualValues() {
        LyricUiConfig baseline = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(39)
                .inactiveTranslationFollowsMain(true)
                .inactiveTranslationOpacityPercent(83)
                .inactiveRowFadeEnabled(false)
                .inactiveRowFadePercent(74)
                .verticalFadeEnabled(false)
                .verticalFadeLengthTenthsDp(777)
                .build();
        Map<String, Object> partial = new HashMap<>();
        partial.put(LyricUiConfigCodec.SCHEMA, 3);
        partial.put(LyricUiConfigCodec.SCALE_ENABLED, true);

        LyricUiConfig decoded = LyricUiConfigCodec.decode(partial, baseline, false);

        assertTrue(decoded.scaleEnabled);
        assertFalse(decoded.inactiveRowFadeEnabled);
        assertEquals(74, decoded.inactiveRowFadePercent);
        assertEquals(83, decoded.inactiveTranslationOpacityPercent);
        assertFalse(decoded.verticalFadeEnabled);
        assertEquals(777, decoded.verticalFadeLengthTenthsDp);
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
