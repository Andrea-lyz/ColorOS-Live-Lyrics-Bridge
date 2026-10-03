package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkSurfaceHandoverTest {
    private static final ArtworkRequestStamp LARGE = new ArtworkRequestStamp(3, 1, 1, 2, 1, 1, 1, 1);
    private static final ArtworkRequestStamp CARD = new ArtworkRequestStamp(3, 1, 1, 4, 1, 1, 2, 1);

    @Test public void sameSongSurvivesBothGeometryGapsAndTheNewSurfaceEpoch() {
        for (String reason : new String[] {"geometry_transition", "hidden", "no_bounds", "eligible"}) {
            assertTrue(reason, ArtworkSurfaceHandover.mayRetain(LARGE, CARD, reason, 1100));
            assertTrue(reason, ArtworkSurfaceHandover.mayRetain(CARD, LARGE, reason, 430));
        }
    }

    @Test public void realLifecycleChangesAndUnverifiedEffectsNeverRetainPlayback() {
        for (String reason : new String[] {"paused", "screen_off", "display_off", "keyguard_open",
                "bridge_off", "detached", "binding_not_ready", "native_transition", "shape_profile", "image_effect"}) {
            assertFalse(reason, ArtworkSurfaceHandover.mayRetain(LARGE, CARD, reason, 100));
        }
    }

    @Test public void songSessionPluginChangeAndExpiredHoldRejectLateFrames() {
        assertFalse(ArtworkSurfaceHandover.mayRetain(LARGE, null, "hidden", 100));
        assertFalse(ArtworkSurfaceHandover.mayRetain(LARGE,
                new ArtworkRequestStamp(3, 1, 1, 4, 1, 2, 2, 1), "hidden", 100));
        assertFalse(ArtworkSurfaceHandover.mayRetain(LARGE,
                new ArtworkRequestStamp(3, 1, 1, 4, 2, 1, 2, 1), "hidden", 100));
        assertFalse(ArtworkSurfaceHandover.mayRetain(LARGE,
                new ArtworkRequestStamp(3, 1, 2, 4, 1, 1, 2, 1), "hidden", 100));
        assertFalse(ArtworkSurfaceHandover.mayRetain(LARGE, CARD, "hidden", -1));
        assertFalse(ArtworkSurfaceHandover.mayRetain(LARGE, CARD, "hidden", ArtworkSurfaceHandover.MAX_HOLD_MS));
    }

    @Test public void transitionUsesANewLeaseWithoutRevivingTheRejectedDisplayLease() {
        ArtworkRenderGuard display = new ArtworkRenderGuard(LARGE, () -> null, () -> false);
        assertFalse(display.permits());
        ArtworkRenderGuard hold = new ArtworkRenderGuard(LARGE, () -> LARGE,
                () -> ArtworkSurfaceHandover.mayRetain(LARGE, CARD, "geometry_transition", 26));
        assertTrue(hold.permits());
        assertFalse(display.permits());
        hold.close();
        assertFalse(hold.permits());
    }
}
