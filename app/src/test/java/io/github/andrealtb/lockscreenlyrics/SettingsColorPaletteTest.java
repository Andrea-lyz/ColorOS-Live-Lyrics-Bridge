package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

public final class SettingsColorPaletteTest {
    private static final Pattern RGB = Pattern.compile("#[0-9A-F]{6}");

    @Test
    public void primaryAndGlowPalettesMatchTheApprovedFiveSlotDesign() {
        assertEquals(4, SettingsColorPalette.PRIMARY.length);
        assertEquals(4, SettingsColorPalette.GLOW.length);
        assertEquals("#FF9AA8", SettingsColorPalette.customSeed(
                "#FFFFFF", SettingsColorPalette.PRIMARY, "#FF9AA8"));
        assertEquals("#12ABCD", SettingsColorPalette.customSeed(
                "#12ABCD", SettingsColorPalette.PRIMARY, "#FF9AA8"));
        assertEquals("#FF5D73", SettingsColorPalette.customSeed(
                "#FF5D73", SettingsColorPalette.GLOW, "#FF5D73"));
        assertEquals("#FF9AA8", LyricUiConfig.sanitizeColor("#FF9AA8", "#FFFFFF"));
        assertEquals("#FF5D73", LyricUiConfig.sanitizeColor("#FF5D73", "#FFFFFF"));
        for (String color : SettingsColorPalette.PRIMARY) {
            assertTrue(RGB.matcher(color).matches());
        }
        for (String color : SettingsColorPalette.GLOW) {
            assertTrue(RGB.matcher(color).matches());
        }
        assertEquals("#FFFFFF", SettingsColorPalette.PRIMARY[0]);
        assertEquals("#FFD68A", SettingsColorPalette.GLOW[0]);
        assertEquals("#8FE3FF", SettingsColorPalette.PRIMARY[3]);
        assertEquals("#FF5D73", SettingsColorPalette.GLOW[3]);
    }

    @Test
    public void customSeedRejectsInvalidInputAndKeepsFallback() {
        assertEquals("#FF9AA8", SettingsColorPalette.customSeed(
                "not-a-color", SettingsColorPalette.PRIMARY, "#FF9AA8"));
        assertEquals("#FF9AA8", SettingsColorPalette.customSeed(
                "#ffffff", SettingsColorPalette.PRIMARY, "#FF9AA8"));
    }
}
