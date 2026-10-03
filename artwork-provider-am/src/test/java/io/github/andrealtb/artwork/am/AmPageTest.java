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
