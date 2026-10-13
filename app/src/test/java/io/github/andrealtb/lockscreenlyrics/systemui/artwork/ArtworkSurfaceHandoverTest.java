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

    @Test public void onlyASettledPauseOnTheSameHostMayFadeBackToStatic() {
        assertTrue(ArtworkSurfaceHandover.mayFadeToStatic(LARGE, LARGE, "paused", true));
        assertFalse(ArtworkSurfaceHandover.mayFadeToStatic(LARGE, LARGE, "paused", false));
        assertFalse(ArtworkSurfaceHandover.mayFadeToStatic(LARGE, CARD, "paused", true));
        assertFalse(ArtworkSurfaceHandover.mayFadeToStatic(LARGE, null, "paused", true));
        for (String reason : new String[] {"eligible", "screen_off", "display_off", "keyguard_open",
                "bridge_off", "detached", "binding_not_ready", "geometry_transition", "native_transition"}) {
            assertFalse(reason, ArtworkSurfaceHandover.mayFadeToStatic(LARGE, LARGE, reason, true));
        }
    }

    @Test public void changedSongSessionPluginProviderOrConfigurationCannotKeepThePausedFrame() {
        for (ArtworkRequestStamp changed : new ArtworkRequestStamp[] {
                new ArtworkRequestStamp(3, 1, 1, 2, 1, 2, 1, 1),
                new ArtworkRequestStamp(3, 1, 1, 2, 2, 1, 1, 1),
                new ArtworkRequestStamp(3, 1, 2, 2, 1, 1, 1, 1),
                new ArtworkRequestStamp(3, 2, 1, 2, 1, 1, 1, 1),
                new ArtworkRequestStamp(3, 1, 1, 2, 1, 1, 1, 2)}) {
            assertFalse(ArtworkSurfaceHandover.mayFadeToStatic(LARGE, changed, "paused", true));
        }
    }

    @Test public void revokedFadeLeaseCannotReviveAfterRapidSongChanges() {
        ArtworkRequestStamp[] live = { LARGE };
        ArtworkRenderGuard fade = new ArtworkRenderGuard(LARGE, () -> live[0],
                () -> ArtworkSurfaceHandover.mayFadeToStatic(LARGE, live[0], "paused", true));
        assertTrue(fade.permits());
        live[0] = new ArtworkRequestStamp(3, 1, 1, 2, 1, 2, 1, 1);
        assertFalse(fade.permits());
        live[0] = LARGE;
        assertFalse(fade.permits());
    }
}
