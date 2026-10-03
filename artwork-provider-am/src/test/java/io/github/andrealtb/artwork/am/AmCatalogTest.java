package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

public class AmCatalogTest {
    @Test public void emptySongSearchFallsBackToExactAlbumAndStillVerifiesSongTable() throws Exception {
        var calls = new AtomicInteger();
        var resolver = new AmCatalog((uri, limit) -> {
            calls.incrementAndGet();
            if (uri.getHost().equals("music.apple.com")) return AmPageTest.page("null");
            if (uri.getQuery().contains("entity=album")) return "{\"results\":[{\"wrapperType\":\"collection\",\"collectionType\":\"Album\",\"collectionId\":10,\"collectionName\":\"1989\",\"artistName\":\"Taylor Swift\"}]}";
            return "{\"results\":[]}";
        }, (stage, tracks) -> {});
        assertEquals("10", resolver.resolve(AmIdentityTest.query("Style", "1989", 288), null, "us", null).id());
        assertEquals(3, calls.get());
    }
    @Test public void confirmedAlbumTableMatchesNewSongWithoutAnyNetworkSearch() throws Exception {
        var album = AmPage.album(AmPageTest.page("null"), "10");
        var resolver = new AmCatalog((uri, limit) -> { fail("cached album should not fetch"); return ""; }, (stage, tracks) -> {});
        assertSame(album, resolver.resolve(AmIdentityTest.query("Style", "1989", 288), null, "us", album));
    }
    @Test public void albumFallbackCannotAcceptOriginalForRerecordOrUnrelatedSong() throws Exception {
        var resolver = new AmCatalog((uri, limit) -> {
            if (uri.getHost().equals("music.apple.com")) return AmPageTest.page("null");
            if (uri.getQuery().contains("entity=album")) return "{\"results\":[{\"wrapperType\":\"collection\",\"collectionType\":\"Album\",\"collectionId\":10,\"collectionName\":\"1989\",\"artistName\":\"Taylor Swift\"}]}";
            return "{\"results\":[]}";
        }, (stage, tracks) -> {});
        try { resolver.resolve(AmIdentityTest.query("Style (Taylor's Version)", "1989", 288), null, "us", null); fail(); }
        catch (AmFailure failure) { assertEquals(Status.RETRY_LATER, failure.status); }
    }
    @Test public void sameAlbumNameMultipleIdsRemainsAmbiguous() throws Exception {
        String row = "{\"wrapperType\":\"collection\",\"collectionType\":\"Album\",\"collectionId\":%s,\"collectionName\":\"1989\",\"artistName\":\"Taylor Swift\"}";
        var resolver = new AmCatalog((uri, limit) -> uri.getQuery().contains("entity=album")
                ? "{\"results\":[" + String.format(row, "10") + "," + String.format(row, "20") + "]}" : "{\"results\":[]}", (stage, tracks) -> {});
        try { resolver.resolve(AmIdentityTest.query("Style", "1989", 288), null, "us", null); fail(); }
        catch (AmFailure failure) { assertEquals(Status.AMBIGUOUS, failure.status); }
    }
    @Test public void matchDiagnosticsRevealFieldsOnlyNotPrivateTextOrIdentity() {
        String value = AmIdentity.diagnostics(List.of(new AmIdentity.Track("private-id", "private-album", "Style", "Taylor Swift", "1989", 231000)),
                AmIdentityTest.query("Style", "1989", 288));
        assertTrue(value.contains("completeMatches=1")); assertFalse(value.contains("Taylor")); assertFalse(value.contains("private"));
    }
}
