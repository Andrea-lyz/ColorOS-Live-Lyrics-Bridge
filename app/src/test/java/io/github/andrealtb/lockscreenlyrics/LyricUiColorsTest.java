package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LyricUiColorsTest {
    @Test
    public void derivesDefaultPaletteFromPrimaryAndGlowColors() {
        LyricUiConfig config = LyricUiConfig.defaults();

        assertEquals(0x70FFFFFF, LyricUiColors.inactive(config));
        assertEquals(0x80FFFFFF, LyricUiColors.focusedInactive(config));
        assertEquals(0xFFFFFFFF, LyricUiColors.active(config));
        assertEquals(0xF0FFFFFF, LyricUiColors.played(config));
        assertEquals(0x99FFFFFF, LyricUiColors.activeTranslation(config));
        assertEquals(0xCCFFFFFF, LyricUiColors.activeTranslationProgress(config));
        assertEquals(0xBAFFD68A, LyricUiColors.glowShadow(config));
        assertEquals(0x32FFFFFF, LyricUiColors.glowFill(config));
    }

    @Test
    public void scalesGlowIntensityAndDisablesGlowWithoutChangingRgb() {
        LyricUiConfig half = LyricUiConfig.defaults().buildUpon()
                .primaryColor("#123456")
                .glowColor("#ABCDEF")
                .glowIntensityPercent(50)
                .build();
        assertEquals(0x5DABCDEF, LyricUiColors.glowShadow(half));
        assertEquals(0x19123456, LyricUiColors.glowFill(half));

        LyricUiConfig off = half.buildUpon().glowEnabled(false).build();
        assertEquals(0x00ABCDEF, LyricUiColors.glowShadow(off));
        assertEquals(0x00123456, LyricUiColors.glowFill(off));
    }

    @Test
    public void customPrimaryAffectsActiveLayersButNotBaseLyrics() {
        LyricUiConfig config = LyricUiConfig.defaults().buildUpon()
                .primaryColor("#123456")
                .build();

        assertEquals(0x70FFFFFF, LyricUiColors.inactive(config));
        assertEquals(0x80FFFFFF, LyricUiColors.focusedInactive(config));
        assertEquals(0xFF123456, LyricUiColors.active(config));
        assertEquals(0xF0123456, LyricUiColors.played(config));
        assertEquals(0x99FFFFFF, LyricUiColors.translationBase(config, true));
        assertEquals(0xCC123456, LyricUiColors.activeTranslationProgress(config));
        assertEquals(0x70FFFFFF, LyricUiColors.translationBase(config, false));
        assertArrayEquals(
                new int[]{
                        0xFF123456,
                        0xF6123456,
                        0xD9123456,
                        0x9A123456,
                        0x48123456,
                        0x10123456,
                        0x00123456
                },
                LyricUiColors.activeFeatherColors(config));
    }

    @Test
    public void focusedUnrevealedUsesItsOwnOpacityInsteadOfInactiveMain() {
        LyricUiConfig lowUnrevealed = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(30)
                .currentUnrevealedOpacityPercent(35)
                .build();
        LyricUiConfig highUnrevealed = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(80)
                .currentUnrevealedOpacityPercent(65)
                .build();

        assertEquals(0x59FFFFFF, LyricUiColors.focusedInactive(lowUnrevealed));
        assertEquals(0xA6FFFFFF, LyricUiColors.focusedInactive(highUnrevealed));
    }

    @Test
    public void inactiveTranslationSupportsFollowAndIndependentOpacity() {
        LyricUiConfig follows = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(73)
                .inactiveTranslationFollowsMain(true)
                .inactiveTranslationOpacityPercent(31)
                .build();
        LyricUiConfig independent = follows.buildUpon()
                .inactiveTranslationFollowsMain(false)
                .build();

        assertEquals(LyricUiColors.inactive(follows), LyricUiColors.translationBase(follows, false));
        assertEquals(0x4FFFFFFF, LyricUiColors.translationBase(independent, false));
        assertEquals(0x99FFFFFF, LyricUiColors.translationBase(independent, true));
    }

    @Test
    public void configurableActiveLayersKeepOneLaneAlphaOutsideFeatherCurve() {
        LyricUiConfig config = LyricUiConfig.defaults().buildUpon()
                .primaryColor("#123456")
                .activeOpacityPercent(80)
                .currentUnrevealedOpacityPercent(40)
                .activeTranslationOpacityPercent(45)
                .activeTranslationProgressOpacityPercent(70)
                .build();

        assertEquals(0xCC123456, LyricUiColors.active(config));
        assertEquals(0x66FFFFFF, LyricUiColors.focusedInactive(config));
        assertEquals(0x73FFFFFF, LyricUiColors.activeTranslation(config));
        assertEquals(0xB3123456, LyricUiColors.activeTranslationProgress(config));
        assertArrayEquals(
                new int[]{
                        0xFF123456,
                        0xF6123456,
                        0xD9123456,
                        0x9A123456,
                        0x48123456,
                        0x10123456,
                        0x00123456
                },
                LyricUiColors.activeFeatherColors(config));
    }
}
