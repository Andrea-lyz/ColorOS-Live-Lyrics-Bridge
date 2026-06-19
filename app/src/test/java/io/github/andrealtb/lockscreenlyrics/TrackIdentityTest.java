package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TrackIdentityTest {
    @Test
    public void explicitSuffixDoesNotChangeTrackIdentity() {
        assertEquals(
                TrackIdentity.buildKey("Modern Girl", "Bleachers"),
                TrackIdentity.buildKey(
                        "Modern Girl [Explicit]",
                        "Bleachers"));
        assertEquals(
                TrackIdentity.buildKey("Jesus Is Dead", "Bleachers"),
                TrackIdentity.buildKey(
                        "Jesus Is Dead (Explicit)",
                        "Bleachers"));
    }

    @Test
    public void ordinaryBracketedTitleTextIsPreserved() {
        assertEquals(
                "song [live]|artist",
                TrackIdentity.buildKey("Song [Live]", "Artist"));
    }

    @Test
    public void missingLrcArtistStillMatchesTheSameTitle() {
        assertTrue(TrackIdentity.matchesHintKey(
                "alma mater|",
                TrackIdentity.buildKey("Alma Mater [Explicit]", "Bleachers")));
        assertFalse(TrackIdentity.matchesHintKey(
                "alma mater|",
                TrackIdentity.buildKey("Tiny Moves", "Bleachers")));
    }

    @Test
    public void transitionFallbackAllowsSmallPreMetadataLead() {
        assertTrue(TrackIdentity.isTransitionResult(-23L, 250L, 800L));
        assertTrue(TrackIdentity.isTransitionResult(800L, 250L, 800L));
        assertFalse(TrackIdentity.isTransitionResult(-251L, 250L, 800L));
        assertFalse(TrackIdentity.isTransitionResult(801L, 250L, 800L));
    }
}
