package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

public class AmIdentityTest {
    static ArtworkQuery query(String title, String album, int pixels) {
        return new ArtworkQuery(title, "Taylor Swift", album, 231000, "", pixels, pixels, 1080, 1080, 20 * 1024 * 1024);
    }
    @Test public void sameDurationDoesNotMakeOriginalAndRerecordEquivalent() {
        assertFalse(AmIdentity.agrees(new AmIdentity.Track("1", "10", "Style", "Taylor Swift", "1989", 231000),
                query("Style (Taylor's Version)", "1989 (Taylor's Version)", 288)));
    }
    @Test public void playerListingBothArtistsMatchesStoreGuestCreditInTitle() {
        // Device log: "Fortnight" matched title, album and duration but never the artist.
        var store = new AmIdentity.Track("1", "10", "Fortnight (feat. Post Malone)", "Taylor Swift",
                "THE TORTURED POETS DEPARTMENT", 228966);
        for (String artist : List.of("Taylor Swift/Post Malone", "Taylor Swift; Post Malone", "Taylor Swift & Post Malone",
                "Taylor Swift\u3001Post Malone", "Taylor Swift\uFF0FPost Malone", "Taylor Swift feat. Post Malone")) {
            assertTrue(artist, AmIdentity.agrees(store, new ArtworkQuery("Fortnight (feat. Post Malone)", artist,
                    "THE TORTURED POETS DEPARTMENT", 228966, "", 288, 288, 1080, 1080, 20 * 1024 * 1024)));
        }
    }
    @Test public void creditedSetsMustBeEqualUnlessTheAlbumAnchorsTheTrack() {
        var store = new AmIdentity.Track("1", "10", "Fortnight (feat. Post Malone)", "Taylor Swift",
                "THE TORTURED POETS DEPARTMENT", 228966);
        // Without an album to anchor it, differing guest lists stay different artists.
        assertFalse(AmIdentity.agrees(store, new ArtworkQuery("Fortnight (feat. Post Malone)", "Taylor Swift/Ed Sheeran",
                "", 228966, "", 288, 288, 1080, 1080, 20 * 1024 * 1024)));
        // Inside the named album the lead artist decides; inaccurate guest tags still bind the album's cover.
        assertTrue(AmIdentity.agrees(store, new ArtworkQuery("Fortnight (feat. Post Malone)", "Taylor Swift/Ed Sheeran",
                "THE TORTURED POETS DEPARTMENT", 228966, "", 288, 288, 1080, 1080, 20 * 1024 * 1024)));
        assertFalse(AmIdentity.sameArtists("Taylor Swift", "Exile", "Taylor Swift & Bon Iver", "Exile"));
        assertFalse(AmIdentity.sameArtists("Post Malone", "Fortnight (feat. Post Malone)", "Taylor Swift",
                "Fortnight (feat. Post Malone)"));
        assertTrue(AmIdentity.sameArtists("Simon & Garfunkel", "The Boxer", "Simon & Garfunkel", "The Boxer"));
    }
    @Test public void albumTrackWithGuestsWrittenDifferentlyStillBindsItsAlbum() {
        // User case: an album mostly by one artist, one track listed as "Taylor Swift/Ed Sheeran/Future".
        var store = new AmIdentity.Track("1", "10", "End Game (feat. Ed Sheeran & Future)", "Taylor Swift", "reputation", 244821);
        for (String[] player : new String[][] {
                {"End Game", "Taylor Swift/Ed Sheeran/Future"}, {"End Game", "Taylor Swift"},
                {"End Game", "Ed Sheeran/Taylor Swift/Future"}, {"End Game (feat. Ed Sheeran & Future)", "Taylor Swift/Ed Sheeran/Future"},
                {"End Game feat. Ed Sheeran & Future", "Taylor Swift, Ed Sheeran, Future"}}) {
            assertTrue(player[0] + " / " + player[1], AmIdentity.agrees(store, new ArtworkQuery(player[0], player[1], "reputation",
                    244821, "", 288, 288, 1080, 1080, 20 * 1024 * 1024)));
        }
    }
    @Test public void albumCentricMatchKeepsAlbumVersionAndLeadArtist() {
        var store = new AmIdentity.Track("1", "10", "End Game (feat. Ed Sheeran & Future)", "Taylor Swift", "reputation", 244821);
        // No album: the album cannot anchor a looser track identity.
        assertFalse(AmIdentity.agrees(store, new ArtworkQuery("End Game", "Taylor Swift/Ed Sheeran/Future", "",
                244821, "", 288, 288, 1080, 1080, 20 * 1024 * 1024)));
        // Another album of the same name family.
        assertFalse(AmIdentity.agrees(store, new ArtworkQuery("End Game", "Taylor Swift", "Taylor Swift Karaoke: reputation",
                244821, "", 288, 288, 1080, 1080, 20 * 1024 * 1024)));
        // A guest's own listing does not take over the album.
        assertFalse(AmIdentity.agrees(store, new ArtworkQuery("End Game", "Ed Sheeran", "reputation",
                244821, "", 288, 288, 1080, 1080, 20 * 1024 * 1024)));
        // Version words stay part of the title.
        var karaoke = new AmIdentity.Track("2", "20", "End Game (feat. Ed Sheeran & Future) [Karaoke Version]", "Taylor Swift",
                "reputation", 244821);
        assertFalse(AmIdentity.agrees(karaoke, new ArtworkQuery("End Game", "Taylor Swift", "reputation",
                244821, "", 288, 288, 1080, 1080, 20 * 1024 * 1024)));
        assertEquals("style taylor s version", AmIdentity.coreTitle("Style (Taylor's Version)"));
    }
    @Test public void exactAlbumSelectsEdition() throws Exception {
        var original = new AmIdentity.Track("1", "10", "Style", "Taylor Swift", "1989", 231000);
        var deluxe = new AmIdentity.Track("2", "20", "Style", "Taylor Swift", "1989 (Deluxe Edition)", 231000);
        assertEquals(original, AmIdentity.unique(List.of(original, deluxe), query("Style", "1989", 288), "", ""));
    }
    @Test public void missingAlbumWithMultipleEditionsIsAmbiguous() throws Exception {
        try {
            AmIdentity.unique(List.of(new AmIdentity.Track("1", "10", "Style", "Taylor Swift", "1989", 231000),
                    new AmIdentity.Track("2", "20", "Style", "Taylor Swift", "1989 Deluxe", 231000)), query("Style", "", 288), "", "");
            fail();
        } catch (AmFailure failure) { assertEquals(Status.AMBIGUOUS, failure.status); }
    }
    @Test public void unicodeNamesDoNotCollapseToEmpty() {
        assertEquals("周杰伦 七里香", AmIdentity.normalize("周杰伦《七里香》"));
        assertNotEquals(AmIdentity.normalize("青花瓷"), AmIdentity.normalize("七里香"));
        assertEquals("宇多田ヒカル", AmIdentity.normalize("宇多田ヒカル"));
    }
    @Test public void albumSongParameterKeepsBothIdentitiesAndMarket() throws Exception {
        var link = AmIdentity.link("https://music.apple.com/cn/album/album/1713845538?i=1713845746&uo=4");
        assertEquals("cn", link.country()); assertEquals("1713845538", link.albumId()); assertEquals("1713845746", link.songId());
    }
    @Test public void unsafeUrlAndDuplicateIdentityAreRejected() throws Exception {
        for (String url : List.of("https://music.apple.com.evil.test/us/album/a/1", "http://music.apple.com/us/album/a/1",
                "https://user@music.apple.com/us/album/a/1", "https://music.apple.com/us/album/a/1?i=2&i=3")) {
            try { AmIdentity.link(url); fail(url); } catch (AmFailure failure) { assertEquals(Status.UNSUPPORTED, failure.status); }
        }
    }
    @Test public void emptyBoundedSearchIsRetryNotPermanentNoMatch() throws Exception {
        try { AmIdentity.unique(List.of(), query("Style", "1989", 288), "", ""); fail(); }
        catch (AmFailure failure) { assertEquals(Status.RETRY_LATER, failure.status); }
    }
    @Test public void wrongArtistOrUnknownDurationCannotMatch() {
        assertFalse(AmIdentity.agrees(new AmIdentity.Track("1", "10", "Style", "Someone else", "1989", 231000), query("Style", "1989", 288)));
        assertFalse(AmIdentity.agrees(new AmIdentity.Track("1", "10", "Style", "Taylor Swift", "1989", 0), query("Style", "1989", 288)));
    }
}
