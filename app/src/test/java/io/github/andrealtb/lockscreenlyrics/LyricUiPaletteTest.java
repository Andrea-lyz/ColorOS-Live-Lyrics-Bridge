package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LyricUiPaletteTest {
    @Test
    public void resolvesSameColorsAsLegacyHelpers() {
        LyricUiConfig config = LyricUiConfig.defaults();
        LyricUiPalette palette = LyricUiPalette.from(config);

        assertEquals(LyricUiColors.inactive(config), palette.inactive);
        assertEquals(LyricUiColors.focusedInactive(config), palette.focusedInactive);
        assertEquals(LyricUiColors.active(config), palette.active);
        assertEquals(LyricUiColors.played(config), palette.played);
        assertEquals(LyricUiColors.activeTranslation(config), palette.activeTranslation);
        assertEquals(
                LyricUiColors.translationBase(config, false),
                palette.inactiveTranslation);
        assertEquals(
                LyricUiColors.activeTranslationProgress(config),
                palette.activeTranslationProgress);
        assertEquals(LyricUiColors.glowShadow(config), palette.glowShadow);
        assertEquals(LyricUiColors.glowFill(config), palette.glowFill);
        assertArrayEquals(
                LyricUiColors.activeFeatherColors(config),
                palette.activeFeatherColors);
    }

    @Test
    public void translationBaseSeparatesActiveAndInactiveRows() {
        LyricUiConfig config = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(70)
                .inactiveTranslationFollowsMain(false)
                .inactiveTranslationOpacityPercent(35)
                .build();
        LyricUiPalette palette = LyricUiPalette.from(config);
        assertEquals(palette.activeTranslation, palette.translationBase(true));
        assertEquals(palette.inactiveTranslation, palette.translationBase(false));
        assertEquals(0x59FFFFFF, palette.inactiveTranslation);
        assertEquals(0xB3FFFFFF, palette.inactive);
    }
}
