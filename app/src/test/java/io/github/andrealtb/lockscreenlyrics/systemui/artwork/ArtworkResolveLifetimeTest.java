package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkResolveLifetimeTest {
    @Test public void displayAndPlaybackStatesKeepAnIssuedDownloadAlive() {
        for (String reason : new String[] { "screen_off", "display_off", "keyguard_open", "paused", "hidden",
                "no_bounds", "geometry_transition", "native_transition", "image_effect", "shape_profile", "eligible" }) {
            assertFalse(reason, ArtworkResolveLifetime.abandoned(reason));
        }
    }
    @Test public void onlyAGoneSurfaceOrSongAbandonsTheDownload() {
        assertTrue(ArtworkResolveLifetime.abandoned("detached"));
        assertTrue(ArtworkResolveLifetime.abandoned("binding_not_ready"));
        assertTrue(ArtworkResolveLifetime.abandoned("bridge_off"));
    }

    private static ArtworkRequestStamp stamp(long surface, long session, long generation) {
        return new ArtworkRequestStamp(1, 1, 1, surface, session, generation, 1, 1);
    }

    @Test public void otherSurfaceOfTheSameSongKeepsARecentlyIssuedDownload() {
        // Same session/generation, different surface epoch: the card took over from the large cover.
        assertTrue(ArtworkResolveLifetime.retainForSameSong(stamp(1, 7, 3), stamp(2, 7, 3), 1_200));
        assertTrue(ArtworkResolveLifetime.retainForSameSong(stamp(2, 7, 3), stamp(1, 7, 3), 0));
    }

    @Test public void aNewSongIsNeverRetained() {
        assertFalse(ArtworkResolveLifetime.retainForSameSong(stamp(1, 7, 3), stamp(1, 7, 4), 500));
        assertFalse(ArtworkResolveLifetime.retainForSameSong(stamp(1, 7, 3), stamp(1, 8, 1), 500));
    }

    @Test public void twoSourceColdResolveSurvivesPastTheOldTenSecondWatchdog() {
        // Device fix1: AM exhausted discovery, NetEase retried a socket read, then cached READY at 16.151 s.
        assertTrue(ArtworkImmersivePlayback.RESOLVE_STALL_MS > 16_151);
        assertTrue(ArtworkResolveLifetime.ONLINE_TIMEOUT_MS > 52_000);
        assertTrue(ArtworkResolveLifetime.ONLINE_TIMEOUT_MS < io.github.andrealtb.artwork.contract.ArtworkContract.LEASE_MS);
        assertEquals(ArtworkResolveLifetime.ONLINE_TIMEOUT_MS, ArtworkImmersivePlayback.RESOLVE_STALL_MS);
        assertTrue(ArtworkResolveLifetime.retainForSameSong(stamp(1, 7, 3), stamp(2, 7, 3), 16_151));
        assertFalse(ArtworkResolveLifetime.retainForSameSong(stamp(1, 7, 3), stamp(2, 7, 4), 16_151));
    }

    @Test public void aStuckRequestIsSupersededInsteadOfBlockingTheOtherSurface() {
        long window = ArtworkResolveLifetime.RETAIN_WINDOW_MS;
        assertTrue(ArtworkResolveLifetime.retainForSameSong(stamp(1, 7, 3), stamp(2, 7, 3), window));
        assertFalse(ArtworkResolveLifetime.retainForSameSong(stamp(1, 7, 3), stamp(2, 7, 3), window + 1));
    }

    @Test public void handoverIdentityIgnoresTheSurfaceEpochButNotTheSong() {
        // A different surface epoch and client epoch still describe the same song; the generation does not.
        var card = new ArtworkRequestStamp(9, 4, 1, 1, 7, 3, 5, 1);
        var largeCover = new ArtworkRequestStamp(2, 4, 1, 8, 7, 3, 5, 1);
        assertTrue(ArtworkResolveLifetime.sameSong(card, largeCover));
        assertFalse(ArtworkResolveLifetime.sameSong(card, new ArtworkRequestStamp(9, 4, 1, 1, 7, 4, 5, 1)));
        assertFalse(ArtworkResolveLifetime.sameSong(card, new ArtworkRequestStamp(9, 4, 1, 1, 9, 3, 5, 1)));
        assertFalse(ArtworkResolveLifetime.sameSong(null, largeCover));
    }

    private static ArtworkTrackIdentityPolicy.Metadata metadata(String title, String artist, long durationMs) {
        return new ArtworkTrackIdentityPolicy.Metadata("private-host-id", title, artist, "Album", durationMs);
    }

    @Test public void sameRecordingKeepsTheProviderQueryFieldsOnly() {
        var published = metadata("Something Just Like This", "The Chainsmokers/Coldplay", 247626);
        assertTrue(ArtworkResolveLifetime.sameRecording(published, metadata("Something Just Like This", "The Chainsmokers/Coldplay", 247626)));
        assertFalse(ArtworkResolveLifetime.sameRecording(published, metadata("Something Just Like This (Live)", "The Chainsmokers/Coldplay", 247626)));
        assertFalse(ArtworkResolveLifetime.sameRecording(published, metadata("Something Just Like This", "Cover Band", 247626)));
        assertFalse(ArtworkResolveLifetime.sameRecording(published, metadata("Something Just Like This", "The Chainsmokers/Coldplay", 259064)));
        assertFalse(ArtworkResolveLifetime.sameRecording(published, null));
        assertFalse(ArtworkResolveLifetime.sameRecording(new ArtworkTrackIdentityPolicy.Metadata("id", "", "Artist", "Album", 1000), metadata("x", "Artist", 1000)));
        assertFalse(ArtworkResolveLifetime.sameRecording(new ArtworkTrackIdentityPolicy.Metadata("id", "Song", "Artist", "Album", 0), metadata("Song", "Artist", 0)));
    }

    @Test public void aReRegisteredSessionStillKeepsTheSameRecordingDownload() {
        var song = metadata("Something Just Like This", "The Chainsmokers/Coldplay", 247626);
        // Keyguard remount: the same song arrives with a new surface epoch, or a fully re-registered session.
        assertTrue(ArtworkResolveLifetime.sameTarget(stamp(1, 7, 3), song, stamp(2, 7, 3), song));
        assertTrue(ArtworkResolveLifetime.sameTarget(stamp(1, 7, 3), song, stamp(2, 9, 1), song));
        assertTrue(ArtworkResolveLifetime.retainForSameTarget(stamp(1, 7, 3), song, stamp(2, 9, 1), song, 16_151));
        assertFalse(ArtworkResolveLifetime.retainForSameTarget(stamp(1, 7, 3), song, stamp(2, 9, 1), song, ArtworkResolveLifetime.RETAIN_WINDOW_MS + 1));
        assertFalse(ArtworkResolveLifetime.sameTarget(stamp(1, 7, 3), song, stamp(2, 9, 1), metadata("Other Song", "The Chainsmokers/Coldplay", 247626)));
        assertFalse(ArtworkResolveLifetime.sameTarget(stamp(1, 7, 3), song, stamp(2, 9, 1), metadata("Something Just Like This", "Cover Band", 247626)));
    }
}
