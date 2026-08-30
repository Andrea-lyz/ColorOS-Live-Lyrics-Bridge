package io.github.andrealtb.lockscreenlyrics.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BridgeDebugConfigTest {
    @Test
    public void defaultsAreOffWithAllAreasReady() {
        BridgeDebugConfig config = BridgeDebugConfig.disabled();
        assertFalse(config.masterEnabled);
        assertEquals("off", config.enabledAreasLabel());
        assertFalse(config.isAreaEnabled(BridgeDebugArea.AOD));
        assertTrue(config.aod);
    }

    @Test
    public void masterEnablesDeclaredAreasOnly() {
        BridgeDebugConfig config = new BridgeDebugConfig(
                BridgeDebugConfig.SCHEMA_VERSION,
                9L,
                true,
                true,
                false,
                true,
                false,
                true,
                false,
                true);
        assertTrue(config.isAreaEnabled(BridgeDebugArea.BOOTSTRAP));
        assertFalse(config.isAreaEnabled(BridgeDebugArea.MEDIA));
        assertTrue(config.isAreaEnabled(BridgeDebugArea.LYRIC));
        assertFalse(config.isAreaEnabled(BridgeDebugArea.RENDERER));
        assertTrue(config.isAreaEnabled(BridgeDebugArea.AOD));
        assertFalse(config.isAreaEnabled(BridgeDebugArea.PLAYER_SPECIAL));
        assertTrue(config.isAreaEnabled(BridgeDebugArea.PERFORMANCE));
        assertEquals("bootstrap,lyric,aod,performance", config.enabledAreasLabel());
    }
}
