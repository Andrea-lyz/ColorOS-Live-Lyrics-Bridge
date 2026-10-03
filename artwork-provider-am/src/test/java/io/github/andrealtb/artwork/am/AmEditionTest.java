package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

public class AmEditionTest {
    @Test public void actualShowgirlCatalogContinuesFromTwinCandidatesToOwnedWebAlbum() throws Exception {
        String songs = resource("/showgirl-explicit-clean-public.json"), html = resource("/showgirl-explicit-public.html");
        var resolver = new AmCatalog((uri, limit) -> {
            if (uri.getHost().equals("itunes.apple.com")) return songs;
            assertTrue(uri.getPath().endsWith("/1838810949")); return html;
        }, (stage, tracks) -> {});
        var album = resolver.resolve(query(), null, "us", null);
        assertEquals("1838810949", album.id()); assertEquals(12, album.tracks().size());
        assertNotNull(album.master()); assertTrue(album.master().getPath().endsWith("P1189220687_default.m3u8"));
    }
    @Test public void actualShowgirlUsesItsNativeAvc360SingleFileVariant() throws Exception {
        var base = java.net.URI.create("https://mvod.itunes.apple.com/master.m3u8");
        var variant = AmHls.variants(base, resource("/showgirl-explicit-square-master.m3u8"), query()).get(0);
        assertEquals(360, variant.width());
        var plan = AmHls.filePlan(variant.uri(), resource("/showgirl-avc360-child.m3u8"), query().maxFileBytes);
        assertEquals(450783, plan.bytes()); assertEquals(14.93158, plan.durationSeconds(), 0.00001);
    }
    private static String resource(String name) throws Exception {
        try (var stream = AmEditionTest.class.getResourceAsStream(name)) {
            assertNotNull(stream); return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    static ArtworkQuery query() { return new ArtworkQuery("The Fate of Ophelia", "Taylor Swift", "The Life of a Showgirl", 226074, "", 288, 288, 1080, 1080, 20 * 1024 * 1024); }
    static AmIdentity.Track track(String id, String rating, String day, int count) {
        return new AmIdentity.Track(id + "1", id, "The Fate of Ophelia", "Taylor Swift", "The Life of a Showgirl", 226074,
                new AmEdition.Info(rating, day, count));
    }
    static final AmIdentity.Track EXPLICIT = track("1838810949", "explicit", "2025-10-03", 12);
    static final AmIdentity.Track CLEAN = track("1842897453", "cleaned", "2025-10-03", 12);
    @Test public void explicitCleanPairUsesStableExplicitCoverRegardlessOfOrder() throws Exception {
        assertSame(EXPLICIT, AmIdentity.unique(List.of(CLEAN, EXPLICIT), query(), "", ""));
        assertSame(EXPLICIT, AmIdentity.unique(List.of(EXPLICIT, CLEAN), query(), "", ""));
        assertTrue(AmIdentity.diagnostics(List.of(CLEAN, EXPLICIT), query()).contains("explicitCleanEquivalent=true"));
    }
    @Test public void exactCleanAlbumOrSongRemainsAuthoritative() throws Exception {
        assertSame(CLEAN, AmIdentity.unique(List.of(EXPLICIT, CLEAN), query(), "", CLEAN.albumId()));
        assertSame(CLEAN, AmIdentity.unique(List.of(EXPLICIT, CLEAN), query(), CLEAN.songId(), ""));
    }
    @Test public void unknownRatingAndOrdinaryNotExplicitDoNotMeanClean() throws Exception {
        for (String rating : List.of("", "notExplicit", "explicit")) {
            expectAmbiguous(List.of(EXPLICIT, track("20", rating, "2025-10-03", 12)));
        }
    }
    @Test public void differentReleaseOrTrackCountDoesNotCollapseOtherEditions() throws Exception {
        expectAmbiguous(List.of(EXPLICIT, track("20", "cleaned", "2024-10-03", 12)));
        expectAmbiguous(List.of(EXPLICIT, track("20", "cleaned", "2025-10-03", 16)));
        expectAmbiguous(List.of(EXPLICIT, track("20", "cleaned", "", 12)));
    }
    @Test public void extraUnclassifiedCandidateRemainsAmbiguous() throws Exception {
        expectAmbiguous(List.of(EXPLICIT, CLEAN, track("30", "", "2025-10-03", 12)));
    }
    @Test public void albumSearchUsesSameNarrowRule() throws Exception {
        String base = "{\"wrapperType\":\"collection\",\"collectionType\":\"Album\",\"artistName\":\"Taylor Swift\",\"collectionName\":\"The Life of a Showgirl\",\"trackCount\":12,\"releaseDate\":\"2025-10-03T07:00:00Z\",\"collectionId\":%s,\"collectionExplicitness\":\"%s\"}";
        String response = "{\"results\":[" + String.format(base, "1842897453", "cleaned") + "," + String.format(base, "1838810949", "explicit") + "]}";
        assertEquals(List.of("1838810949"), AmPage.albumIds(response, query().album, query().artist));
    }
    @Test public void actualPublicTracksUseAlbumRatingEvenWhenTrackIsNotExplicit() throws Exception {
        String response;
        try (var stream = getClass().getResourceAsStream("/showgirl-explicit-clean-public.json")) {
            assertNotNull(stream); response = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var tracks = AmPage.itunes(response);
        var chosen = AmIdentity.unique(tracks, query(), "", "");
        assertEquals("1838810949", chosen.albumId()); assertEquals("1838810951", chosen.songId());
        assertEquals("explicit", chosen.edition().rating());
        ArtworkQuery elizabeth = new ArtworkQuery("Elizabeth Taylor", query().artist, query().album, 208292, "", 288, 288, 1080, 1080, query().maxFileBytes);
        assertEquals("1838810952", AmIdentity.unique(tracks, elizabeth, "", "").songId());
    }
    @Test public void sameAlbumIdDifferentSongIdsAreNotEditionPair() throws Exception {
        var second = new AmIdentity.Track("other-song", EXPLICIT.albumId(), EXPLICIT.title(), EXPLICIT.artist(), EXPLICIT.album(), EXPLICIT.durationMs(), CLEAN.edition());
        expectAmbiguous(List.of(EXPLICIT, second));
    }
    private static void expectAmbiguous(List<AmIdentity.Track> tracks) throws Exception {
        try { AmIdentity.unique(tracks, query(), "", ""); fail(); }
        catch (AmFailure failure) { assertEquals(Status.AMBIGUOUS, failure.status); }
    }
}
