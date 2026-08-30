package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricUiPresetTest {
    @Test
    public void presetsApplyDocumentedStyleWithoutChangingPolicyFields() {
        LyricUiConfig source = new LyricUiConfig.Builder()
                .screenTimeoutSeconds(45)
                .defaultTranslationEnabled(false)
                .build();
        LyricUiConfig soft = LyricUiPreset.SOFT.apply(source);

        assertEquals(36, soft.inactiveOpacityPercent);
        assertEquals(100, soft.activeOpacityPercent);
        assertEquals(50, soft.currentUnrevealedOpacityPercent);
        assertEquals(60, soft.activeTranslationOpacityPercent);
        assertEquals(80, soft.activeTranslationProgressOpacityPercent);
        assertTrue(soft.inactiveTranslationFollowsMain);
        assertEquals(36, soft.inactiveTranslationOpacityPercent);
        assertTrue(soft.verticalFadeEnabled);
        assertEquals(519, soft.verticalFadeLengthTenthsDp);
        assertTrue(soft.inactiveRowFadeEnabled);
        assertEquals(90, soft.inactiveRowFadePercent);
        assertTrue(soft.blurEnabled);
        assertEquals(20, soft.blurRadiusTenthsPx);
        assertEquals(96, soft.inactiveScalePercent);
        assertEquals(60, soft.glowIntensityPercent);
        assertEquals(LyricUiConfig.MOTION_REDUCED, soft.motionMode);
        assertEquals(45, soft.screenTimeoutSeconds);
        assertFalse(soft.defaultTranslationEnabled);
    }

    @Test
    public void manualAppearanceChangeBecomesCustom() {
        LyricUiConfig vivid = LyricUiPreset.VIVID.apply(LyricUiConfig.defaults());
        assertEquals(LyricUiPreset.VIVID, LyricUiPreset.detect(vivid));
        assertEquals(
                LyricUiPreset.CUSTOM,
                LyricUiPreset.detect(vivid.buildUpon().glowRadiusPercent(21).build()));
        assertEquals(
                LyricUiPreset.CUSTOM,
                LyricUiPreset.detect(vivid.buildUpon().primaryColor("#123456").build()));
        assertEquals(
                LyricUiPreset.CUSTOM,
                LyricUiPreset.detect(vivid.buildUpon().glowColor("#654321").build()));
    }

    @Test
    public void everyPresetOwnsVisualLayersAndAnyDeviationBecomesCustom() {
        LyricUiPreset[] presets = {
                LyricUiPreset.DEFAULT,
                LyricUiPreset.SOFT,
                LyricUiPreset.VIVID,
                LyricUiPreset.MINIMAL
        };
        int[] inactiveOpacity = {44, 36, 44, 55};
        boolean[] rowFadeEnabled = {false, true, true, false};
        for (int index = 0; index < presets.length; index++) {
            LyricUiConfig config = presets[index].apply(LyricUiConfig.defaults().buildUpon()
                    .activeOpacityPercent(72)
                    .currentUnrevealedOpacityPercent(35)
                    .activeTranslationOpacityPercent(42)
                    .activeTranslationProgressOpacityPercent(61)
                    .inactiveTranslationFollowsMain(false)
                    .inactiveTranslationOpacityPercent(25)
                    .verticalFadeEnabled(false)
                    .verticalFadeLengthTenthsDp(700)
                    .inactiveRowFadeEnabled(!rowFadeEnabled[index])
                    .inactiveRowFadePercent(73)
                    .build());

            assertEquals(100, config.activeOpacityPercent);
            assertEquals(50, config.currentUnrevealedOpacityPercent);
            assertEquals(60, config.activeTranslationOpacityPercent);
            assertEquals(80, config.activeTranslationProgressOpacityPercent);
            assertEquals(inactiveOpacity[index], config.inactiveOpacityPercent);
            assertTrue(config.inactiveTranslationFollowsMain);
            assertEquals(inactiveOpacity[index], config.inactiveTranslationOpacityPercent);
            assertTrue(config.verticalFadeEnabled);
            assertEquals(519, config.verticalFadeLengthTenthsDp);
            assertEquals(rowFadeEnabled[index], config.inactiveRowFadeEnabled);
            assertEquals(90, config.inactiveRowFadePercent);
            assertEquals(presets[index], LyricUiPreset.detect(config));
        }

        LyricUiConfig vivid = LyricUiPreset.VIVID.apply(LyricUiConfig.defaults());
        assertCustom(vivid.buildUpon().activeOpacityPercent(99).build());
        assertCustom(vivid.buildUpon().currentUnrevealedOpacityPercent(49).build());
        assertCustom(vivid.buildUpon().activeTranslationOpacityPercent(59).build());
        assertCustom(vivid.buildUpon().activeTranslationProgressOpacityPercent(79).build());
        assertCustom(vivid.buildUpon().inactiveOpacityPercent(43).build());
        assertCustom(vivid.buildUpon().inactiveTranslationFollowsMain(false).build());
        assertCustom(vivid.buildUpon().inactiveTranslationOpacityPercent(43).build());
        assertCustom(vivid.buildUpon().verticalFadeEnabled(false).build());
        assertCustom(vivid.buildUpon().verticalFadeLengthTenthsDp(520).build());
        assertCustom(vivid.buildUpon().inactiveRowFadeEnabled(false).build());
        assertCustom(vivid.buildUpon().inactiveRowFadePercent(89).build());
    }

    @Test
    public void minimalPresetRepresentsDisabledGlowAsZeroIntensity() {
        LyricUiConfig minimal = LyricUiPreset.MINIMAL.apply(LyricUiConfig.defaults());

        assertFalse(minimal.glowEnabled);
        assertEquals(0, minimal.glowIntensityPercent);
        assertEquals(LyricUiPreset.MINIMAL, LyricUiPreset.detect(minimal));
        assertEquals(
                LyricUiPreset.MINIMAL,
                LyricUiPreset.detect(minimal.buildUpon().glowIntensityPercent(100).build()));
    }

    @Test
    public void everyPresetOwnsDefaultTypographyAndManualTypographyBecomesCustom() {
        LyricUiConfig customTypography = LyricUiConfig.defaults().buildUpon()
                .mainFontTenthsSp(260)
                .translationFontRatioPercent(75)
                .fontWeight(LyricUiConfig.WEIGHT_BOLD)
                .alignment(LyricUiConfig.ALIGN_CENTER)
                .lineSpacingTenthsDp(40)
                .wrappedLineSpacingTenthsDp(25)
                .build();

        LyricUiConfig defaults = LyricUiPreset.DEFAULT.apply(customTypography);
        assertEquals(220, defaults.mainFontTenthsSp);
        assertEquals(66, defaults.translationFontRatioPercent);
        assertEquals(LyricUiConfig.WEIGHT_SYSTEM, defaults.fontWeight);
        assertEquals(LyricUiConfig.ALIGN_START, defaults.alignment);
        assertEquals(0, defaults.lineSpacingTenthsDp);
        assertEquals(0, defaults.wrappedLineSpacingTenthsDp);
        assertEquals(LyricUiPreset.CUSTOM, LyricUiPreset.detect(customTypography));
        assertEquals(LyricUiPreset.DEFAULT, LyricUiPreset.detect(defaults));

        for (LyricUiPreset preset : new LyricUiPreset[]{
                LyricUiPreset.SOFT, LyricUiPreset.VIVID, LyricUiPreset.MINIMAL}) {
            LyricUiConfig applied = preset.apply(customTypography);
            assertEquals(220, applied.mainFontTenthsSp);
            assertEquals(66, applied.translationFontRatioPercent);
            assertEquals(LyricUiConfig.WEIGHT_SYSTEM, applied.fontWeight);
            assertEquals(LyricUiConfig.ALIGN_START, applied.alignment);
            assertEquals(0, applied.lineSpacingTenthsDp);
            assertEquals(0, applied.wrappedLineSpacingTenthsDp);
            assertEquals(preset, LyricUiPreset.detect(applied));
            assertEquals(
                    LyricUiPreset.CUSTOM,
                    LyricUiPreset.detect(applied.buildUpon().mainFontTenthsSp(230).build()));
            assertEquals(
                    LyricUiPreset.CUSTOM,
                    LyricUiPreset.detect(applied.buildUpon().lineSpacingTenthsDp(125).build()));
            assertEquals(
                    LyricUiPreset.CUSTOM,
                    LyricUiPreset.detect(
                            applied.buildUpon().wrappedLineSpacingTenthsDp(25).build()));
        }
    }

    @Test
    public void legacyPresetSpacingIsRecognizedWithoutMatchingCustomSpacing() {
        LyricUiConfig legacyDefault = LyricUiPreset.DEFAULT.apply(LyricUiConfig.defaults())
                .buildUpon()
                .lineSpacingTenthsDp(110)
                .build();
        LyricUiConfig legacySoft = LyricUiPreset.SOFT.apply(LyricUiConfig.defaults())
                .buildUpon()
                .lineSpacingTenthsDp(130)
                .build();

        assertTrue(LyricUiPreset.hasLegacyPresetSpacing(legacyDefault));
        assertTrue(LyricUiPreset.hasLegacyPresetSpacing(legacySoft));
        assertFalse(LyricUiPreset.hasLegacyPresetSpacing(
                legacySoft.buildUpon().lineSpacingTenthsDp(125).build()));
    }

    private static void assertCustom(LyricUiConfig config) {
        assertEquals(LyricUiPreset.CUSTOM, LyricUiPreset.detect(config));
    }
}
