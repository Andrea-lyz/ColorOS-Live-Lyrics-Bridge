package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ArtworkSessionAssociationTest {
    private final ArtworkCardIdentity card = new ArtworkCardIdentity("player-key", "music.pkg", "Song", "Artist");

    private ArtworkSessionAssociation.Entry<String> entry(String key, String pkg, int user, String token, boolean active) {
        return new ArtworkSessionAssociation.Entry<>(key, pkg, user, token, "Song", "Artist", 240_000, active);
    }

    @Test public void resolvesByExactCardIdAcrossSeveralSessionsOfTheSamePackage() {
        var wanted = entry("player-key", "music.pkg", 10, "token-B", true);
        assertSame(wanted, ArtworkSessionAssociation.match(card, List.of(
                entry("other-key", "music.pkg", 10, "token-A", true), wanted)));
    }

    @Test public void rejectsDuplicatePlayerKeysEvenAcrossUsersOrIdenticalTokens() {
        var first = entry("player-key", "music.pkg", 0, "same", true);
        assertNull(ArtworkSessionAssociation.match(card, List.of(first,
                entry("player-key", "music.pkg", 10, "other", true))));
        assertNull(ArtworkSessionAssociation.match(card, List.of(first, first)));
    }

    @Test public void rejectsInactiveMissingTokenAndDifferentPackage() {
        assertNull(ArtworkSessionAssociation.match(card, List.of(entry("player-key", "music.pkg", 0, "token", false))));
        assertNull(ArtworkSessionAssociation.match(card, List.of(entry("player-key", "other.pkg", 0, "token", true))));
        assertNull(ArtworkSessionAssociation.match(card, List.of(entry("player-key", "music.pkg", 0, null, true))));
        assertNull(ArtworkSessionAssociation.match(card, List.of(entry("player-key", "music.pkg", -1, "token", true))));
    }

    @Test public void waitsForCardAndSeedlingTitlesToAgreeDuringFastTrackChanges() {
        var next = new ArtworkSessionAssociation.Entry<>("player-key", "music.pkg", 0, "token", "Next", "Artist", 240_000, true);
        assertNull(ArtworkSessionAssociation.match(card, List.of(next)));
        assertSame(next, ArtworkSessionAssociation.match(new ArtworkCardIdentity("player-key", "music.pkg", "Next", "Artist"), List.of(next)));
        assertNull(ArtworkSessionAssociation.match(new ArtworkCardIdentity("player-key", "music.pkg", "Next", ""), List.of(next)));
    }

    @Test public void songTransitionRetainsExactTokenUntilCardCatchesUp() {
        var next = new ArtworkSessionAssociation.Entry<>("player-key", "music.pkg", 0, "token", "Next", "Artist", 250_000, true);
        assertSame(next, ArtworkSessionAssociation.matchSession(card, List.of(next)));
        assertFalse(ArtworkSessionAssociation.matchesSong(card, next));
        var updated = new ArtworkCardIdentity("player-key", "music.pkg", "Next", "Artist");
        assertSame(next, ArtworkSessionAssociation.matchSession(updated, List.of(next)));
        assertTrue(ArtworkSessionAssociation.matchesSong(updated, next));
    }

    @Test public void incompleteSongKeepsSessionButCannotBecomeReady() {
        var session = entry("player-key", "music.pkg", 0, "token", true);
        var partial = new ArtworkCardIdentity("player-key", "music.pkg", "", "");
        assertSame(session, ArtworkSessionAssociation.matchSession(partial, List.of(session)));
        assertFalse(ArtworkSessionAssociation.matchesSong(partial, session));
        assertNull(ArtworkSessionAssociation.matchSession(partial, List.of(session, session)));
    }
}
