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
}
