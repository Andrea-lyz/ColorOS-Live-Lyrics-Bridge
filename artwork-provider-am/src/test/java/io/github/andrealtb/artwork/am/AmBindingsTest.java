package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

public class AmBindingsTest {
    private static ArtworkQuery query(String title, String artist, String album) {
        return new ArtworkQuery(title, artist, album, 231000, "", 288, 288, 1080, 1080, 20 * 1024 * 1024);
    }
    private static AmBindings.Binding binding(String localAlbum, String localArtist, String albumId) {
        return new AmBindings.Binding(localAlbum, localArtist, "us", albumId, "1989 (Deluxe Edition)", "Taylor Swift");
    }

    @Test public void localAlbumNameBindsEveryTrackWhateverTheTagsSay() {
        var bindings = List.of(binding("1989 [Deluxe]", "", "10"));
        // Normalized like every other identity field: case and punctuation do not matter.
        assertEquals("10", AmBindings.find(bindings, query("Style", "泰勒·斯威夫特", "1989 (deluxe)")).albumId());
        assertEquals("10", AmBindings.find(bindings, query("Wildest Dreams", "", "1989 Deluxe")).albumId());
        assertNull(AmBindings.find(bindings, query("Style", "Taylor Swift", "1989")));
        assertNull(AmBindings.find(bindings, query("Style", "Taylor Swift", "")));
    }

    @Test public void artistLimitedBindingWinsAndKeepsOtherArtistsOut() {
        var any = binding("Greatest Hits", "", "10");
        var queen = binding("Greatest Hits", "Queen", "20");
        var bindings = List.of(any, queen);
        assertEquals("20", AmBindings.find(bindings, query("Bohemian Rhapsody", "Queen", "Greatest Hits")).albumId());
        // A collaboration still credits the limited artist.
        assertEquals("20", AmBindings.find(bindings, query("Under Pressure", "Queen/David Bowie", "Greatest Hits")).albumId());
        assertEquals("10", AmBindings.find(bindings, query("Song", "ABBA", "Greatest Hits")).albumId());
        assertNull(AmBindings.find(List.of(queen), query("Song", "ABBA", "Greatest Hits")));
        // An artist name containing a separator matches as written.
        assertNotNull(AmBindings.find(List.of(binding("Bridge", "Simon & Garfunkel", "30")),
                query("The Boxer", "Simon & Garfunkel", "Bridge")));
    }

    @Test public void rebindingTheSameLocalAlbumReplacesAndDeleteRemovesOnlyThatOne() {
        var bindings = AmBindings.put(List.of(), binding("1989", "", "10"));
        bindings = AmBindings.put(bindings, binding("Lover", "", "40"));
        bindings = AmBindings.put(bindings, binding("1989 ", "", "11"));
        assertEquals(2, bindings.size());
        assertEquals("11", bindings.get(0).albumId());
        bindings = AmBindings.put(bindings, binding("1989", "Taylor Swift", "12"));
        assertEquals(3, bindings.size());
        bindings = AmBindings.remove(bindings, binding("1989", "", "ignored"));
        assertEquals(List.of("12", "40"), bindings.stream().map(AmBindings.Binding::albumId).toList());
    }

    @Test public void storedBindingsRoundTripAndDamagedEntriesAreDroppedIndividually() {
        var bindings = List.of(binding("1989", "Taylor Swift", "10"), binding("Lover", "", "40"));
        assertEquals(bindings, AmBindings.decode(AmBindings.encode(bindings)));
        String stored = "[{\"localAlbum\":\"1989\",\"localArtist\":\"\",\"country\":\"us\",\"albumId\":\"10\",\"title\":\"t\",\"artist\":\"a\"},"
                + "{\"localAlbum\":\"x\",\"localArtist\":\"\",\"country\":\"usa\",\"albumId\":\"10\",\"title\":\"t\",\"artist\":\"a\"},"
                + "{\"localAlbum\":\"y\",\"localArtist\":\"\",\"country\":\"us\",\"albumId\":\"../10\",\"title\":\"t\",\"artist\":\"a\"},"
                + "{\"localAlbum\":\"  \",\"localArtist\":\"\",\"country\":\"us\",\"albumId\":\"10\",\"title\":\"t\",\"artist\":\"a\"},7]";
        assertEquals(1, AmBindings.decode(stored).size());
        assertTrue(AmBindings.decode("not json").isEmpty());
    }

    @Test public void bindingChangesTheFailureKeyButNotTheSharedVideoKey() {
        var query = query("Style", "Taylor Swift", "1989");
        assertNotEquals(AmCache.boundKey(query, "us", "10"), AmCache.boundKey(query, "us", "11"));
        assertNotEquals(AmCache.boundKey(query, "us", "10"), AmCache.key(query, "us"));
        assertNotEquals(AmCache.boundKey(query, "us", "10"), AmCache.albumAssetKey(query, "us", "10"));
        // One answer per bound album and size, shared by every track.
        assertEquals(AmCache.boundKey(query, "us", "10"), AmCache.boundKey(query("Blank Space", "x", "1989"), "us", "10"));
    }

    @Test public void recentAlbumsKeepOneEntryPerAlbumAndOnlyAlbumLevelOutcomes() {
        var matched = new AmRecentAlbums.Entry("1989", "Taylor Swift", AmRecentAlbums.Outcome.MATCHED);
        var entries = AmRecentAlbums.note(List.of(), matched);
        assertSame(entries, AmRecentAlbums.note(entries, matched));
        entries = AmRecentAlbums.note(entries, new AmRecentAlbums.Entry("Lover", "Taylor Swift", AmRecentAlbums.Outcome.UNMATCHED));
        entries = AmRecentAlbums.note(entries, new AmRecentAlbums.Entry("1989", "taylor swift", AmRecentAlbums.Outcome.BOUND));
        assertEquals(2, entries.size());
        assertEquals(AmRecentAlbums.Outcome.BOUND, entries.get(0).outcome());
        assertSame(entries, AmRecentAlbums.note(entries, new AmRecentAlbums.Entry(" ", "x", AmRecentAlbums.Outcome.MATCHED)));
        for (int i = 0; i < 40; i++) entries = AmRecentAlbums.note(entries, new AmRecentAlbums.Entry("a" + i, "", AmRecentAlbums.Outcome.MATCHED));
        assertEquals(AmRecentAlbums.MAX, entries.size());
        assertEquals(entries, AmRecentAlbums.decode(AmRecentAlbums.encode(entries)));

        assertEquals(AmRecentAlbums.Outcome.UNMATCHED, AmRecentAlbums.outcome(new AmFailure(Status.RETRY_LATER, "catalog_match_unconfirmed", 60_000)));
        assertEquals(AmRecentAlbums.Outcome.UNMATCHED, AmRecentAlbums.outcome(new AmFailure(Status.AMBIGUOUS, "multiple_catalog_matches")));
        assertEquals(AmRecentAlbums.Outcome.NO_MOTION, AmRecentAlbums.outcome(new AmFailure(Status.NO_MOTION, "confirmed_album_no_motion")));
        // Device log 040311: a page that could not be parsed must still list the album for binding.
        assertEquals(AmRecentAlbums.Outcome.FAILED, AmRecentAlbums.outcome(new AmFailure(Status.RETRY_LATER, "web_schema_changed", 300_000)));
        assertEquals(AmRecentAlbums.Outcome.FAILED, AmRecentAlbums.outcome(new AmFailure(Status.RETRY_LATER, "network_stage_timeout", 30_000)));
        assertEquals(AmRecentAlbums.Outcome.FAILED, AmRecentAlbums.outcome(new AmFailure(Status.UNSUPPORTED, "motion_asset_unrecognized")));
        assertNull(AmRecentAlbums.outcome(new AmFailure(Status.ERROR, "cancelled")));
    }

    @Test public void albumSearchOffersEveryAlbumWithItsEditionDetails() throws Exception {
        var hits = AmPage.albumHits("{\"results\":[{\"wrapperType\":\"collection\",\"collectionType\":\"Album\",\"collectionId\":10,"
                + "\"collectionName\":\"1989 (Deluxe Edition)\",\"artistName\":\"Taylor Swift\",\"releaseDate\":\"2014-10-27T07:00:00Z\","
                + "\"trackCount\":19,\"collectionExplicitness\":\"notExplicit\"},"
                + "{\"wrapperType\":\"collection\",\"collectionType\":\"Compilation\",\"collectionId\":11,\"collectionName\":\"x\",\"artistName\":\"y\"},"
                + "{\"wrapperType\":\"track\",\"kind\":\"song\",\"trackId\":1}]}");
        assertEquals(1, hits.size());
        assertEquals(new AmPage.AlbumHit("10", "1989 (Deluxe Edition)", "Taylor Swift", "2014-10-27", 19, false), hits.get(0));
        try { AmPage.albumHits("{\"results\":[{\"wrapperType\":\"collection\",\"collectionType\":\"Album\",\"collectionId\":\"1x\"}]}"); fail(); }
        catch (AmFailure expected) { assertEquals("catalog_schema_changed", expected.reason); }
    }

    @Test public void leadCreditKeepsTheNameAsWritten() {
        assertEquals("Taylor Swift", AmIdentity.leadCredit(" Taylor Swift/Ed Sheeran/Future"));
        assertEquals("taylor swift", AmIdentity.primaryArtist("Taylor Swift feat. Future"));
        assertEquals("", AmIdentity.leadCredit(" / "));
    }
}
