package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LyricUiColorsTest {
    @Test
    public void derivesDefaultPaletteFromPrimaryAndGlowColors() {
        LyricUiConfig config = LyricUiConfig.defaults();

        assertEquals(0x70FFFFFF, LyricUiColors.inactive(config));
        assertEquals(0x96FFFFFF, LyricUiColors.focusedInactive(config));
        assertEquals(0xFFFFFFFF, LyricUiColors.active(config));
        assertEquals(0xF0FFFFFF, LyricUiColors.played(config));
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
    public void aodActiveTranslationUsesSameOpaqueColorAsMainLine() {
        LyricUiConfig config = LyricUiConfig.defaults().buildUpon()
                .primaryColor("#123456")
                .build();

        assertEquals(0xFF123456, LyricUiColors.translationBase(config, true, 0f));
        assertEquals(0x70123456, LyricUiColors.translationBase(config, false, 0f));
        assertEquals(0x96123456, LyricUiColors.translationBase(config, false, 1f));
    }
}
