package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

public class AmPageTest {
    @Test public void actualPublicDeluxeSnapshotKeepsAllTracksAndRightMaster() throws Exception {
        String html;
        try (var stream = getClass().getResourceAsStream("/apple-1989-deluxe-public.html")) {
            assertNotNull(stream);
            html = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var album = AmPage.album(html, "1713845538");
        assertEquals(22, album.tracks().size());
        assertTrue(album.master().getPath().endsWith("/P637795736_default.m3u8"));
        assertEquals("1713845746", album.tracks().get(2).songId());
        assertEquals(231000, album.tracks().get(2).durationMs());
    }
    static String page(String video) {
        return "<script type=\"application/json\" id=\"serialized-server-data\">{\"data\":[{\"data\":{\"sections\":["
                + "{\"id\":\"recommendation\",\"items\":[]},"
                + "{\"id\":\"track-list - 10\",\"items\":[{\"title\":\"Style\",\"artistName\":\"Taylor Swift\",\"duration\":231000,"
                + "\"contentDescriptor\":{\"kind\":\"song\",\"identifiers\":{\"storeAdamID\":\"1\"},\"url\":\"https://music.apple.com/us/album/style/10?i=1\"}}]},"
                + "{\"id\":\"album-detail-header-section - 10\",\"items\":[{\"title\":\"1989\",\"contentDescriptor\":{\"kind\":\"album\","
                + "\"identifiers\":{\"storeAdamID\":\"10\"}},\"videoArtwork\":" + video + "}]}]}}]}</script>";
    }
    private static String track(String title, String songId, String url, boolean withDuration) {
        return "{\"title\":\"" + title + "\",\"artistName\":\"Taylor Swift\"" + (withDuration ? ",\"duration\":231000" : "")
                + ",\"contentDescriptor\":{\"kind\":\"song\",\"identifiers\":{\"storeAdamID\":\"" + songId + "\"},\"url\":\"" + url + "\"}}";
    }
    private static String pageWithTracks(String tracks, String video) {
        String original = page(video);
        int start = original.indexOf("{\"id\":\"track-list - 10\"");
        int end = original.indexOf("{\"id\":\"album-detail-header-section - 10\"");
        return original.substring(0, start) + "{\"id\":\"track-list - 10\",\"items\":[" + tracks + "]}," + original.substring(end);
    }
    @Test public void multiDiscAlbumMergesEveryDiscTrackList() throws Exception {
        // Device log 042351: "The Life of a Showgirl: The Encore" lists its discs in separate sections.
        String html;
        try (var stream = getClass().getResourceAsStream("/showgirl-encore-two-disc-public.html")) {
            assertNotNull(stream);
            html = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var album = AmPage.album(html, "6814997249");
        assertEquals(16, album.tracks().size());
        assertEquals(0, album.skippedTracks());
        assertEquals("6814997425", album.tracks().get(12).songId());
        assertNotNull(album.master());
        var query = new io.github.andrealtb.artwork.contract.ArtworkQuery("The Fate of Ophelia", "Taylor Swift",
                "The Life of a Showgirl: The Encore", 226000, "", 288, 288, 1080, 1080, 20 * 1024 * 1024);
        assertEquals("6814997402", AmIdentity.unique(album.tracks(), query, "", album.id()).songId());
    }
    @Test public void discSectionsMustBelongToTheExpectedAlbum() {
        assertTrue(AmPage.trackListSection("track-list - 10", "10"));
        assertTrue(AmPage.trackListSection("track-list - 10 - 2", "10"));
        assertFalse(AmPage.trackListSection("track-list - 100", "10"));
        assertFalse(AmPage.trackListSection("track-list - 10 - x", "10"));
        assertFalse(AmPage.trackListSection("track-list - 10 - 1 - 2", "10"));
        assertFalse(AmPage.trackListSection("track-list-section - 10", "10"));
    }
    @Test public void oneOddTrackIsSkippedInsteadOfDiscardingTheAlbum() throws Exception {
        String tracks = track("Style", "1", "https://music.apple.com/us/album/style/10?i=1", true) + ","
                + track("Elsewhere", "2", "https://music.apple.com/us/album/other/99?i=2", true) + ","
                + track("No duration", "3", "https://music.apple.com/us/album/x/10?i=3", false) + ","
                + track("Song link", "4", "https://music.apple.com/us/song/x/4", true);
        var album = AmPage.album(pageWithTracks(tracks, "null"), "10");
        assertEquals(1, album.tracks().size());
        assertEquals("1", album.tracks().get(0).songId());
        assertEquals(3, album.skippedTracks());
    }
    @Test public void unreadablePagesNameTheStepNotTheContent() {
        try { AmPage.album(pageWithTracks(track("Elsewhere", "2", "https://music.apple.com/us/album/other/99?i=2", true), "null"), "10"); fail(); }
        catch (AmFailure expected) {
            assertEquals("web_schema_changed", expected.reason);
            assertTrue(expected.detail, expected.detail.startsWith("no_tracks/"));
        }
        try { AmPage.album(page("null").replace("album-detail-header-section - 10", "album-detail-header-section - 20"), "10"); fail(); }
        catch (AmFailure expected) { assertTrue(expected.detail, expected.detail.startsWith("header_missing/")); }
        try { AmPage.album("<html>challenge</html>", "10"); fail(); }
        catch (AmFailure expected) { assertTrue(expected.detail, expected.detail.startsWith("server_data/")); }
    }
    @Test public void unknownMotionHostIsAnUnsupportedAssetNotABrokenPage() {
        try { AmPage.album(page("{\"dictionary\":{\"motionDetailSquare\":{\"video\":\"https://cdn.example.com/master.m3u8\"}}}"), "10"); fail(); }
        catch (AmFailure expected) {
            assertEquals(Status.UNSUPPORTED, expected.status);
            assertEquals("motion_asset_unrecognized", expected.reason);
            assertEquals("video_host=cdn.example.com", expected.detail);
        }
    }
    @Test public void findsOwnedAlbumAndTrackSectionsInsteadOfFixedPositions() throws Exception {
        var album = AmPage.album(page("{\"dictionary\":{\"motionDetailSquare\":{\"video\":\"https://mvod.itunes.apple.com/master.m3u8\"}}}"), "10");
        assertEquals("10", album.id()); assertEquals(231000, album.tracks().get(0).durationMs()); assertNotNull(album.master());
    }
    @Test public void completeMatchingAlbumCanHaveNoMotion() throws Exception { assertNull(AmPage.album(page("null"), "10").master()); }
    @Test public void missingServerDataAndWrongAlbumAreSchemaFailuresNotNoMotion() throws Exception {
        for (String body : new String[] { "<html>challenge</html>", page("null").replace("album-detail-header-section - 10", "album-detail-header-section - 20") }) {
            try { AmPage.album(body, "10"); fail(); } catch (AmFailure expected) { assertEquals(Status.RETRY_LATER, expected.status); }
        }
    }
    @Test public void cnLookupCollectionOnlyIsNotAPlayableSong() throws Exception {
        assertTrue(AmPage.itunes("{\"results\":[{\"wrapperType\":\"collection\",\"collectionId\":10}]}").isEmpty());
    }
    @Test public void retainsRawVersionAndMillisecondsFromItunes() throws Exception {
        var tracks = AmPage.itunes("{\"results\":[{\"wrapperType\":\"track\",\"kind\":\"song\",\"trackId\":1,\"collectionId\":10,"
                + "\"trackName\":\"Style (Taylor's Version)\",\"artistName\":\"Taylor Swift\",\"collectionName\":\"1989 [Deluxe]\",\"trackTimeMillis\":231000}]}");
        assertEquals("Style (Taylor's Version)", tracks.get(0).title()); assertEquals("1989 [Deluxe]", tracks.get(0).album());
        assertEquals(231000, tracks.get(0).durationMs());
    }
    @Test public void unknownMotionDictionaryIsNotNegativeCachedAsNoMotion() throws Exception {
        try { AmPage.album(page("{\"dictionary\":{\"newMotionField\":{}}}"), "10"); fail(); }
        catch (AmFailure expected) { assertEquals(Status.UNSUPPORTED, expected.status); }
    }
    @Test public void retryAfterIsBounded() {
        assertEquals(1000, AmNetwork.retryAfter("0")); assertEquals(86400000, AmNetwork.retryAfter("999999999"));
        assertEquals(60000, AmNetwork.retryAfter(null)); assertEquals(60000, AmNetwork.retryAfter("bad"));
    }
}
