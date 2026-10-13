package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import io.github.andrealtb.artwork.contract.ArtworkContract;

public class ArtworkDisplaySettingsTest {
    private static final String COMPONENT = "io.github.andrealtb.artwork.am/io.github.andrealtb.artwork.am.AmArtworkService";
    private static final String SIGNER = "a".repeat(64);

    @Test public void defaultsAreOffWithBothSurfacesAndNoKeepAwake() {
        ArtworkDisplaySettings settings = ArtworkDisplaySettings.defaults();
        assertFalse(settings.enabled());
        assertFalse(settings.selected());
        assertFalse(settings.active());
        assertTrue(settings.cardEnabled());
        assertTrue(settings.immersiveEnabled());
        assertFalse(settings.keepAwake());
        assertSame(ArtworkPlaybackConfig.DISABLED, settings.playback());
    }

    @Test public void activeNeedsSwitchProviderAndAtLeastOneSurface() {
        ArtworkDisplaySettings selected = ArtworkDisplaySettings.defaults().withSelection(COMPONENT, SIGNER);
        assertTrue(selected.selected());
        assertFalse(selected.active());
        assertTrue(selected.withEnabled(true).active());
        assertTrue(selected.withEnabled(true).withCard(false).active());
        assertFalse(selected.withEnabled(true).withCard(false).withImmersive(false).active());
        assertFalse(ArtworkDisplaySettings.defaults().withEnabled(true).active());
        ArtworkPlaybackConfig playback = selected.withEnabled(true).withCard(false).withKeepAwake(true).withRevision(7).playback();
        assertTrue(playback.enabled());
        assertFalse(playback.localFixture());
        assertFalse(playback.cardEnabled());
        assertTrue(playback.immersiveEnabled());
        assertTrue(playback.keepAwake());
        assertEquals(7, playback.revision());
        assertEquals(SIGNER, playback.signer());
    }

    @Test public void storedValuesRoundTrip() {
        ArtworkDisplaySettings settings = ArtworkDisplaySettings.defaults().withSelection(COMPONENT, SIGNER + ":" + "b".repeat(64))
                .withEnabled(true).withImmersive(false).withKeepAwake(true).withRevision(42);
        assertEquals(settings, ArtworkDisplaySettings.fromMap(settings.toMap()));
    }

    @Test public void damagedStorageCanOnlyTurnTheDisplayOffNeverRedirectIt() {
        Map<String, Object> values = new HashMap<>(ArtworkDisplaySettings.defaults().withSelection(COMPONENT, SIGNER)
                .withEnabled(true).toMap());
        values.put(ArtworkDisplaySettings.KEY_SIGNER, "not-a-signer");
        ArtworkDisplaySettings broken = ArtworkDisplaySettings.fromMap(values);
        assertEquals("", broken.component());
        assertFalse(broken.active());

        values.put(ArtworkDisplaySettings.KEY_SIGNER, SIGNER);
        values.put(ArtworkDisplaySettings.KEY_COMPONENT, "../evil");
        assertFalse(ArtworkDisplaySettings.fromMap(values).selected());

        values.put(ArtworkDisplaySettings.KEY_COMPONENT, COMPONENT);
        values.put(ArtworkDisplaySettings.KEY_ENABLED, "true");
        values.put(ArtworkDisplaySettings.KEY_REVISION, 5);
        ArtworkDisplaySettings mistyped = ArtworkDisplaySettings.fromMap(values);
        assertFalse(mistyped.enabled());
        assertEquals(0, mistyped.revision());

        values.put(ArtworkDisplaySettings.KEY_ENABLED, true);
        values.put(ArtworkDisplaySettings.KEY_PROTOCOL, ArtworkContract.MAJOR + 1);
        assertFalse("a provider reference from another protocol is not a selection",
                ArtworkDisplaySettings.fromMap(values).active());
        assertEquals(ArtworkDisplaySettings.defaults(), ArtworkDisplaySettings.fromMap(null));
    }

    @Test public void choosingAProviderRecordsTheCurrentProtocol() {
        Map<String, Object> values = new HashMap<>(ArtworkDisplaySettings.defaults().toMap());
        values.put(ArtworkDisplaySettings.KEY_PROTOCOL, ArtworkContract.MAJOR + 1);
        ArtworkDisplaySettings old = ArtworkDisplaySettings.fromMap(values);
        assertEquals(ArtworkContract.MAJOR, old.withSelection(COMPONENT, SIGNER).protocolMajor());
    }

    @Test public void providerRejectionsKeepTheirNamesForSettings() {
        assertEquals("provider_mode_mismatch", ArtworkProviderClient.failureReason(new SecurityException("provider_mode_mismatch")));
        assertEquals("artwork_caller_denied", ArtworkProviderClient.failureReason(new SecurityException("artwork_caller_denied")));
        assertEquals("provider_or_asset_failed", ArtworkProviderClient.failureReason(new SecurityException("anything else")));
        assertEquals("provider_or_asset_failed", ArtworkProviderClient.failureReason(new java.io.IOException("provider_mode_mismatch")));
    }

    @Test public void presentationOnlyChangesKeepTheSession() {
        ArtworkDisplaySettings on = ArtworkDisplaySettings.defaults().withSelection(COMPONENT, SIGNER).withEnabled(true);
        ArtworkPlaybackConfig base = new ArtworkPlaybackConfig(true, null, SIGNER, 1, false, false, true, true);
        assertTrue(base.sameSession(new ArtworkPlaybackConfig(true, null, SIGNER, 2, false, true, false, true)));
        assertFalse(base.sameSession(new ArtworkPlaybackConfig(true, null, "b".repeat(64), 2, false, false, true, true)));
        assertFalse(base.sameSession(new ArtworkPlaybackConfig(true, null, SIGNER, 2, true, false, true, true)));
        assertFalse(base.sameSession(ArtworkPlaybackConfig.DISABLED));
        assertFalse(ArtworkPlaybackConfig.DISABLED.sameSession(base));
        assertTrue(on.active());
    }

    @Test public void frameRateDefaultsToOriginalAndIsClampedAndCarried() {
        ArtworkDisplaySettings defaults = ArtworkDisplaySettings.defaults();
        assertEquals(ArtworkDisplaySettings.FRAME_RATE_UNLIMITED, defaults.frameRateLimit());
        assertEquals(10, defaults.withFrameRateLimit(3).frameRateLimit());
        assertEquals(30, defaults.withFrameRateLimit(60).frameRateLimit());
        ArtworkDisplaySettings capped = defaults.withSelection(COMPONENT, SIGNER).withEnabled(true).withFrameRateLimit(15);
        assertEquals(capped, ArtworkDisplaySettings.fromMap(capped.toMap()));
        assertEquals(15, capped.playback().frameRateLimit());
        assertTrue(capped.playback().frameRateLimited());
        assertFalse(capped.withFrameRateLimit(30).playback().frameRateLimited());

        Map<String, Object> legacy = new HashMap<>(capped.toMap());
        legacy.remove(ArtworkDisplaySettings.KEY_FRAME_RATE);
        assertEquals(30, ArtworkDisplaySettings.fromMap(legacy).frameRateLimit());
        legacy.put(ArtworkDisplaySettings.KEY_FRAME_RATE, 1);
        assertEquals(10, ArtworkDisplaySettings.fromMap(legacy).frameRateLimit());
    }

    @Test public void onlyCrossingTheOriginalRateRebuildsTheDecoder() {
        ArtworkDisplaySettings on = ArtworkDisplaySettings.defaults().withSelection(COMPONENT, SIGNER).withEnabled(true);
        ArtworkPlaybackConfig fifteen = on.withFrameRateLimit(15).playback();
        assertTrue(fifteen.sameSession(on.withFrameRateLimit(20).playback()));
        assertFalse(fifteen.sameSession(on.withFrameRateLimit(30).playback()));
        assertTrue(on.playback().sameSession(on.withKeepAwake(true).playback()));
    }
}
