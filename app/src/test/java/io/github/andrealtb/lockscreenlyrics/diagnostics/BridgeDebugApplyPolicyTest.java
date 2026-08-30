package io.github.andrealtb.lockscreenlyrics.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BridgeDebugApplyPolicyTest {
    @Test
    public void firstLoadAnnouncesEvenWhenStillDisabled() {
        BridgeDebugConfig disabled = BridgeDebugConfig.disabled();
        assertFalse(BridgeDebugApplyPolicy.shouldReconfigure(disabled, disabled));
        assertTrue(BridgeDebugApplyPolicy.shouldAnnounce(
                disabled, disabled, false, "systemui-preferences"));
        assertFalse(BridgeDebugApplyPolicy.shouldAnnounce(
                disabled, disabled, true, "systemui-preferences"));
    }

    @Test
    public void settingsBroadcastAlwaysAnnounces() {
        BridgeDebugConfig enabled = BridgeDebugConfig.enabledAll(9L);
        assertTrue(BridgeDebugApplyPolicy.shouldAnnounce(
                enabled, enabled, true, BridgeDebugApplyPolicy.SOURCE_SETTINGS_BROADCAST));
    }

    @Test
    public void masterChangeReconfiguresAndAnnounces() {
        BridgeDebugConfig off = BridgeDebugConfig.disabled();
        BridgeDebugConfig on = BridgeDebugConfig.enabledAll(1L);
        assertTrue(BridgeDebugApplyPolicy.shouldReconfigure(off, on));
        assertTrue(BridgeDebugApplyPolicy.shouldAnnounce(off, on, true, "systemui-preferences"));
    }
}
