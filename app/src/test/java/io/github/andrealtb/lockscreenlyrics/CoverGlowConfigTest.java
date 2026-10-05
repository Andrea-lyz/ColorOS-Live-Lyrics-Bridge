package io.github.andrealtb.lockscreenlyrics;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class CoverGlowConfigTest {
    @Test public void optionalSettingDefaultsOffAndRoundTripsWithManualFallback() {
        LyricUiConfig old = LyricUiConfigCodec.decode(Map.of(), LyricUiConfig.defaults(), false);
        assertFalse(old.glowFollowsCover);
        var config = old.buildUpon().glowColor("#123456").glowFollowsCover(true).build();
        var copy = LyricUiConfigCodec.decode(LyricUiConfigCodec.encode(config), old, false);
        assertEquals(config, copy);
        assertEquals(config.hashCode(), copy.hashCode());
        assertEquals("#123456", copy.glowColor);
        assertNotEquals(old, copy);
        assertTrue(copy.buildUpon().build().glowFollowsCover);
    }

    @Test public void coverChangesOnlyGlowRgbAndKeepsConfiguredIntensity() {
        var manual = LyricUiConfig.defaults().buildUpon().glowColor("#123456").glowIntensityPercent(50).build();
        var follow = manual.buildUpon().glowFollowsCover(true).build();
        int color = LyricUiColors.glowShadow(follow, 0xFFABCDEF);
        assertEquals(0xABCDEF, color & 0xFFFFFF);
        assertEquals(LyricUiColors.glowShadow(manual) >>> 24, color >>> 24);
        assertEquals(LyricUiColors.active(manual), LyricUiColors.active(follow));
        assertEquals(LyricUiColors.glowFill(manual), LyricUiColors.glowFill(follow));
        assertEquals(LyricUiColors.glowShadow(manual), LyricUiColors.glowShadow(manual, 0xFFABCDEF));
    }

    @Test public void missingOrTransparentArtworkReturnsManualColorAndZeroIntensityStaysOff() {
        var config = LyricUiConfig.defaults().buildUpon().glowFollowsCover(true).build();
        int fallback = LyricUiColors.glowShadow(config);
        assertEquals(fallback, LyricUiColors.glowShadow(config, null));
        assertEquals(fallback, LyricUiColors.glowShadow(config, 0));
        assertEquals(0, LyricUiColors.glowShadow(config.buildUpon().glowIntensityPercent(0).build(), 0xFFABCDEF) >>> 24);
    }

    @Test public void c16AndUnknownVersionsDoNotExposeTheOption() {
        assertTrue(C17GlowSupport.matches(37, "17.99.02"));
        assertFalse(C17GlowSupport.matches(36, "16.0.1"));
        assertFalse(C17GlowSupport.matches(37, "16.0.1"));
        assertFalse(C17GlowSupport.matches(37, null));
    }
}
