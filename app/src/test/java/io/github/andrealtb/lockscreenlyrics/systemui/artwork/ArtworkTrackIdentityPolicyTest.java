package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkTrackIdentityPolicyTest {
    private final ArtworkTrackIdentityPolicy policy = new ArtworkTrackIdentityPolicy();
    private ArtworkCardIdentity card(String title) { return new ArtworkCardIdentity("player-key", "pkg", title, "Artist"); }
    private ArtworkTrackIdentityPolicy.Metadata meta(String id, String title, String album, long duration) {
        return new ArtworkTrackIdentityPolicy.Metadata(id, title, "Artist", album, duration);
    }

    @Test public void hostsBoundToOneSessionNumberTheSameSongAlike() {
        // Device log: the long-lived card host reached generation 2 for a song while the large cover,
        // attached anew for that song, started at 1, so the decoder was never handed between them.
        var songs = new ArtworkTrackIdentityPolicy.SessionSongs();
        var cardHost = new ArtworkTrackIdentityPolicy();
        var coverHost = new ArtworkTrackIdentityPolicy();
        var one = meta("id-1", "One", "Album", 1000);
        var two = meta("id-2", "Two", "Album", 1000);
        assertTrue(cardHost.observe(card("One"), one, 0).ready());
        assertEquals(1, songs.generation(one));
        assertEquals(2, cardHost.observe(card("Two"), two, 1).generation());
        long cardGeneration = songs.generation(two);
        assertEquals(1, coverHost.observe(card("Two"), two, 2).generation());
        long coverGeneration = songs.generation(two);
        assertEquals(cardGeneration, coverGeneration);
        assertTrue(ArtworkResolveLifetime.sameSong(
                new ArtworkRequestStamp(0, 0, 1, 1, 1, cardGeneration, 3, 0),
                new ArtworkRequestStamp(0, 0, 1, 5, 1, coverGeneration, 1, 0)));
        assertEquals("replaying an earlier song is a new generation", 3, songs.generation(one));
    }

    @Test public void sessionSongKeyFollowsObserveSongIdentity() {
        var songs = new ArtworkTrackIdentityPolicy.SessionSongs();
        long initial = songs.generation(meta("", "Song", "", 1000));
        assertEquals(initial, songs.generation(meta("", "Song", "Album", 1000)));
        assertNotEquals(initial, songs.generation(meta("id", "Song", "Album", 1000)));
    }

    @Test public void sameAlbumDifferentMediaIdsAreRealTrackChanges() {
        assertEquals(1, policy.observe(card("One"), meta("id-1", "One", "Album", 1000), 0).generation());
        var next = policy.observe(card("Two"), meta("id-2", "Two", "Album", 1000), 1);
        assertEquals(2, next.generation());
        assertTrue(next.ready());
    }

    @Test public void albumAndDurationCompletionOnlyAdvanceRequestRevision() {
        var initial = policy.observe(card("Song"), meta("id", "Song", "", 0), 0);
        var enriched = policy.observe(card("Song"), meta("id", "Song", "Album", 1234), 1);
        assertEquals(initial.generation(), enriched.generation());
        assertTrue(enriched.revision() > initial.revision());
        assertFalse(policy.observe(card("Song"), meta("id", "Song", "Album", 1234), 2).changed());
    }

    @Test public void tornMetadataImmediatelyInvalidatesOldResultsAndNeverFillsFromOldSong() {
        var initial = policy.observe(card("One"), meta("one", "One", "Album", 1000), 0);
        var torn = policy.observe(card("Two"), meta("one", "One", "Album", 1000), 1);
        assertFalse(torn.ready());
        assertTrue(torn.revision() > initial.revision());
        assertNull(policy.accepted());
        assertTrue(policy.observe(card("Two"), meta("two", "Two", "", 0), 2).ready());
        assertEquals("", policy.accepted().album());
    }

    @Test public void stableIdTitleProjectionDoesNotCreateANewSong() {
        policy.observe(card("Song"), meta("id", "Song", "Album", 1000), 0);
        var projection = policy.observe(card("Lyric line"), meta("id", "Lyric line", "Album", 1000), 1000);
        assertFalse(projection.ready());
        assertEquals(1, projection.generation());
    }

    @Test public void fallbackNeedsCompleteTimedStableObservations() {
        var policy = new ArtworkTrackIdentityPolicy(true);
        assertFalse(policy.observe(card("Song"), meta("", "Song", "Album", 0), 0).ready());
        assertFalse(policy.observe(card("Song"), meta("", "Song", "Album", 1000), 10).ready());
        assertFalse(policy.observe(card("Song"), meta("", "Song", "Album", 1000), 100).ready());
        assertTrue(policy.observe(card("Song"), meta("", "Song", "Album", 1000), 510).ready());
        assertFalse(policy.observe(card("Next"), meta("", "Next", "Album", 10_000), 520).ready());
        assertTrue(policy.observe(card("Next"), meta("", "Next", "Album", 10_000), 1020).ready());
        assertEquals(2, policy.observe(card("Next"), meta("", "Next", "Album", 10_000), 1030).generation());
    }

    @Test public void defaultDoesNotTreatStableLyricTitleProjectionAsVerifiedIdentity() {
        assertFalse(policy.observe(card("Lyric line"), meta("", "Lyric line", "Album", 1000), 0).ready());
        assertFalse(policy.observe(card("Lyric line"), meta("", "Lyric line", "Album", 1000), 10_000).ready());
    }

    @Test public void separateSessionPoliciesDoNotShareMediaIdNamespace() {
        policy.observe(card("One"), meta("7", "One", "Album", 1000), 0);
        var other = new ArtworkTrackIdentityPolicy();
        assertEquals(1, other.observe(card("Two"), meta("7", "Two", "Album", 1000), 1).generation());
        assertEquals("One", policy.accepted().title());
    }

    @Test public void diagnosticReasonSeparatesMissingIdFromTornTextWithoutChangingState() {
        var initial = policy.observe(card("Song"), meta("id", "Song", "Album", 1000), 0);
        assertEquals("media_id_missing_fallback_disabled", policy.diagnosticReason(card("Song"), meta("", "Song", "Album", 1000), 1));
        assertEquals("card_title_mismatch", policy.diagnosticReason(card("Other"), meta("id", "Song", "Album", 1000), 1));
        assertEquals("same_id_text_changed", policy.diagnosticReason(card("Projected"), meta("id", "Projected", "Album", 1000), 1));
        var unchanged = policy.observe(card("Song"), meta("id", "Song", "Album", 1000), 2);
        assertEquals(initial.generation(), unchanged.generation());
        assertEquals(initial.revision(), unchanged.revision());
        assertFalse(unchanged.changed());
    }

    @Test public void idlessTitleProjectionWithSameArtistAndDurationRemainsStatic() {
        var policy = new ArtworkTrackIdentityPolicy(true);
        policy.observe(card("Song"), meta("", "Song", "Album", 100_000), 0);
        assertTrue(policy.observe(card("Song"), meta("", "Song", "Album", 100_000), 500).ready());
        assertFalse(policy.observe(card("Lyric line"), meta("", "Lyric line", "Album", 100_000), 1000).ready());
        assertFalse(policy.observe(card("Lyric line"), meta("", "Lyric line", "Album", 100_000), 10_000).ready());
        assertEquals("fallback_title_change_ambiguous", policy.diagnosticReason(card("Lyric line"), meta("", "Lyric line", "Album", 100_000), 10_000));
        assertNull(policy.accepted());
    }

    @Test public void conflictingDisplayFieldsRejectInitialProjectedTitle() {
        var policy = new ArtworkTrackIdentityPolicy(true);
        var projected = new ArtworkTrackIdentityPolicy.Metadata("", "Lyric line", "Artist", "Album", 100_000, "Song", "Artist");
        assertFalse(policy.observe(card("Lyric line"), projected, 0).ready());
        assertFalse(policy.observe(card("Lyric line"), projected, 1000).ready());
        assertEquals("display_metadata_conflict", policy.diagnosticReason(card("Lyric line"), projected, 1000));
    }

    @Test public void missingPreviouslyReliableIdCannotChangeNamespace() {
        var policy = new ArtworkTrackIdentityPolicy(true);
        policy.observe(card("Song"), meta("id", "Song", "Album", 1000), 0);
        assertFalse(policy.observe(card("Song"), meta("", "Song", "Album", 1000), 1000).ready());
        assertFalse(policy.observe(card("Song"), meta("", "Song", "Album", 1000), 2000).ready());
        assertEquals(1, policy.observe(card("Song"), meta("id", "Song", "Album", 1000), 3000).generation());
    }

    @Test public void supplementalFieldsDoNotCreateAnIdlessTrackGeneration() {
        var policy = new ArtworkTrackIdentityPolicy(true);
        policy.observe(card("Song"), meta("", "Song", "", 100_000), 0);
        var initial = policy.observe(card("Song"), meta("", "Song", "", 100_000), 500);
        assertFalse(policy.observe(card("Song"), meta("", "Song", "Album", 100_100), 600).ready());
        var complete = policy.observe(card("Song"), meta("", "Song", "Album", 100_100), 1100);
        assertEquals(initial.generation(), complete.generation());
        assertTrue(complete.revision() > initial.revision());
    }

    @Test public void idlessTransitionWaitsForSourceCardAndControllerInsteadOfInventingANewSession() {
        var policy = new ArtworkTrackIdentityPolicy(true);
        var first = meta("", "One", "Album", 100_000);
        policy.observe(card("One"), first, 0);
        assertTrue(policy.observe(card("One"), first, 500).ready());
        var source = new ArtworkSessionAssociation.Entry<>("player-key", "pkg", 0, "same-token", "Two", "Artist", 120_000, true);
        assertSame(source, ArtworkSessionAssociation.matchSession(card("One"), java.util.List.of(source)));
        assertFalse(ArtworkSessionAssociation.matchesSong(card("One"), source));
        policy.invalidate();
        assertFalse(policy.observe(card("Two"), first, 1000).ready());
        var second = meta("", "Two", "Album", 120_000);
        assertFalse(policy.observe(card("Two"), second, 1100).ready());
        var next = policy.observe(card("Two"), second, 1650);
        assertTrue(next.ready());
        assertEquals(2, next.generation());
        assertSame(source, ArtworkSessionAssociation.matchSession(card("Two"), java.util.List.of(source)));
    }
}
