package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkNetworkRecoveryTest {
    @Test public void initialAndRepeatedValidationDoNotInterruptAnActiveRequest() {
        var state = new ArtworkNetworkRecovery();
        assertFalse(state.update("wifi", true));
        assertFalse(state.update("wifi", true));
    }
    @Test public void restoredNetworkAndChangedValidatedNetworkEachRecoverOnce() {
        var state = new ArtworkNetworkRecovery(); state.update("wifi", true);
        assertFalse(state.update(null, false));
        assertTrue(state.update("wifi", true));
        assertFalse(state.update("wifi", true));
        assertTrue(state.update("cell", true));
        assertFalse(state.update("cell", true));
    }
    @Test public void connectionWithoutInternetValidationCannotWakeDownloads() {
        var state = new ArtworkNetworkRecovery(); state.update(null, false);
        assertFalse(state.update("wifi", false)); assertTrue(state.update("wifi", true));
    }
    @Test public void rateLimitsAndCatalogAmbiguityAreNotTransportRecoveryFailures() {
        assertTrue(ArtworkNetworkRecovery.transport("network_io"));
        assertTrue(ArtworkNetworkRecovery.transport("network_headers_timeout"));
        assertTrue(ArtworkNetworkRecovery.transport("network_policy"));
        assertFalse(ArtworkNetworkRecovery.transport("upstream_rate_limit"));
        assertFalse(ArtworkNetworkRecovery.transport("multiple_catalog_matches"));
    }
}
