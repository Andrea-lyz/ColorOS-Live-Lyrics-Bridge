package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkSourceReplacementTest {
    @Test public void durationEnrichmentKeepsWitnessButRequiresNewStableSnapshot() {
        var policy = seeded(); policy.observeSource("Two", "Artist", 231000);
        policy.observe(card("Two"), meta("Two"), 1000);
        policy.observeSource("Two", "Artist", 231100);
        var enriched = new ArtworkTrackIdentityPolicy.Metadata("", "Two", "Artist", "Album", 231100);
        policy.observe(card("Two"), enriched, 1200);
        policy.confirmFreshMetadata(meta("Two"), 1750);
        assertFalse(policy.observe(card("Two"), enriched, 1750).ready());
        policy.confirmFreshMetadata(enriched, 1800);
        assertTrue(policy.observe(card("Two"), enriched, 1800).ready());
    }
    @Test public void oldTimerReadingBeforeNewCandidateSettlesCannotConfirmIt() {
        var policy = seeded(); policy.observeSource("Two", "Artist", 231000);
        policy.observe(card("Two"), meta("Two"), 1000);
        policy.confirmFreshMetadata(meta("Two"), 1100);
        assertFalse(policy.observe(card("Two"), meta("Two"), 1500).ready());
        policy.confirmFreshMetadata(meta("Two"), 1550);
        assertTrue(policy.observe(card("Two"), meta("Two"), 1550).ready());
    }
    private ArtworkCardIdentity card(String title) { return new ArtworkCardIdentity("key", "pkg", title, "Artist"); }
    private ArtworkTrackIdentityPolicy.Metadata meta(String title) { return new ArtworkTrackIdentityPolicy.Metadata("", title, "Artist", "Album", 231000); }
    private ArtworkTrackIdentityPolicy seeded() {
        var policy = new ArtworkTrackIdentityPolicy(true);
        policy.observeSource("One", "Artist", 231000);
        policy.observe(card("One"), meta("One"), 0);
        assertTrue(policy.observe(card("One"), meta("One"), 500).ready());
        return policy;
    }
    @Test public void equalDurationReplacementNeedsWitnessStabilityAndFreshControllerRead() {
        var policy = seeded();
        assertTrue(policy.observeSource("Two", "Artist", 231000));
        assertFalse(policy.observe(card("Two"), meta("Two"), 1000).ready());
        assertFalse(policy.observe(card("Two"), meta("Two"), 1499).ready());
        assertFalse(policy.observe(card("Two"), meta("Two"), 1500).ready());
        assertEquals("fallback_source_recheck_required", policy.diagnosticReason(card("Two"), meta("Two"), 1500));
        policy.confirmFreshMetadata(meta("Two"), 1550);
        var accepted = policy.observe(card("Two"), meta("Two"), 1550);
        assertTrue(accepted.ready()); assertEquals(2, accepted.generation());
        assertEquals(2, policy.observe(card("Two"), meta("Two"), 1600).generation());
    }
    @Test public void changedMetadataWithoutIndependentSourceStillCannotBecomeTrack() {
        var policy = seeded();
        assertFalse(policy.observe(card("Line"), meta("Line"), 1000).ready());
        policy.confirmFreshMetadata(meta("Line"), 10000);
        assertFalse(policy.observe(card("Line"), meta("Line"), 10000).ready());
        assertEquals("fallback_title_change_ambiguous", policy.diagnosticReason(card("Line"), meta("Line"), 10000));
    }
    @Test public void delayedReadOfOldSongCannotConfirmNewTrack() {
        var policy = seeded(); policy.observeSource("Two", "Artist", 231000);
        policy.observe(card("Two"), meta("Two"), 1000);
        policy.confirmFreshMetadata(meta("One"), 1550);
        assertFalse(policy.observe(card("Two"), meta("Two"), 1550).ready());
    }
    @Test public void rapidAToBToCSupersedesPendingWitnessAndOldRead() {
        var policy = seeded(); policy.observeSource("Two", "Artist", 231000);
        policy.observe(card("Two"), meta("Two"), 1000);
        policy.observeSource("Three", "Artist", 231000);
        policy.observe(card("Three"), meta("Three"), 1200);
        policy.confirmFreshMetadata(meta("Two"), 1550);
        assertFalse(policy.observe(card("Three"), meta("Three"), 1750).ready());
        policy.confirmFreshMetadata(meta("Three"), 1800);
        assertTrue(policy.observe(card("Three"), meta("Three"), 1800).ready());
        assertEquals("Three", policy.accepted().title());
    }
    @Test public void displayConflictRemainsRejectedWithSourceWitness() {
        var policy = seeded(); policy.observeSource("Two", "Artist", 231000);
        var projected = new ArtworkTrackIdentityPolicy.Metadata("", "Two", "Artist", "Album", 231000, "One", "Artist");
        assertFalse(policy.observe(card("Two"), projected, 1000).ready());
        policy.confirmFreshMetadata(projected, 2000);
        assertFalse(policy.observe(card("Two"), projected, 2000).ready());
    }
    @Test public void reliableIdTemporarilyMissingNeverFallsIntoNewWitnessNamespace() {
        var policy = new ArtworkTrackIdentityPolicy(true);
        policy.observeSource("One", "Artist", 231000);
        policy.observe(card("One"), new ArtworkTrackIdentityPolicy.Metadata("id", "One", "Artist", "Album", 231000), 0);
        assertFalse(policy.observeSource("Two", "Artist", 231000));
        assertFalse(policy.observe(card("Two"), meta("Two"), 1000).ready());
        policy.confirmFreshMetadata(meta("Two"), 1550);
        assertFalse(policy.observe(card("Two"), meta("Two"), 2000).ready());
    }
    @Test public void sourceChangeBackToOriginalDoesNotUsePendingReplacementRead() {
        var policy = seeded(); policy.observeSource("Two", "Artist", 231000);
        policy.observe(card("Two"), meta("Two"), 1000);
        policy.observeSource("One", "Artist", 231000);
        policy.confirmFreshMetadata(meta("Two"), 1550);
        assertFalse(policy.observe(card("Two"), meta("Two"), 2000).ready());
    }
}
