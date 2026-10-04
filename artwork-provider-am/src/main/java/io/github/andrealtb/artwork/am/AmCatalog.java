package io.github.andrealtb.artwork.am;

import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.function.BiConsumer;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

/** Candidate discovery may fall back to an exact album search; every path verifies the actual track table. */
final class AmCatalog {
    interface Fetch { String text(URI uri, int limit) throws AmFailure; }
    private final Fetch fetch;
    private final BiConsumer<String, List<AmIdentity.Track>> diagnostic;
    AmCatalog(Fetch fetch, BiConsumer<String, List<AmIdentity.Track>> diagnostic) { this.fetch = fetch; this.diagnostic = diagnostic; }
    AmPage.Album resolve(ArtworkQuery query, AmIdentity.AppleLink link, String country, AmPage.Album known) throws AmFailure {
        if (known != null && (link == null || link.albumId().isEmpty() || link.albumId().equals(known.id()))) {
            return verify(known, query, link == null ? "" : link.songId(), "catalog_cache");
        }
        if (link != null && !link.albumId().isEmpty()) return verify(page(link.albumId(), country), query, link.songId(), "web_link");
        String uri = link != null ? "https://itunes.apple.com/lookup?id=" + link.songId() + "&entity=song&country=" + country
                : "https://itunes.apple.com/search?term=" + encode(query.artist + " " + query.title + " " + query.album)
                    + "&media=music&entity=musicTrack&country=" + country + "&limit=100";
        List<AmIdentity.Track> tracks = AmPage.itunes(fetch.text(URI.create(uri), 2 * 1024 * 1024));
        diagnostic.accept("itunes_song", tracks);
        AmIdentity.Track selected;
        try { selected = AmIdentity.unique(tracks, query, link == null ? "" : link.songId(), ""); }
        catch (AmFailure failure) {
            if (failure.status != Status.RETRY_LATER || query.album.isEmpty()) throw failure;
            List<String> albums = albumSearch(query.artist, query, country);
            // An album is credited to its lead artist; a full guest list can keep the album out of the results.
            String lead = AmIdentity.primaryArtist(query.artist);
            if (albums.isEmpty() && !lead.isEmpty() && !lead.equals(AmIdentity.normalize(query.artist))) {
                albums = albumSearch(lead, query, country);
            }
            if (albums.size() > 1) throw new AmFailure(Status.AMBIGUOUS, "multiple_album_matches");
            if (albums.isEmpty()) throw new AmFailure(Status.RETRY_LATER, "catalog_album_unconfirmed", 30_000);
            return verify(page(albums.get(0), country), query, link == null ? "" : link.songId(), "web_album_fallback");
        }
        return verify(page(selected.albumId(), country), query, selected.songId(), "web_song_album");
    }
    private List<String> albumSearch(String artist, ArtworkQuery query, String country) throws AmFailure {
        return AmPage.albumIds(fetch.text(albumSearchUri(artist + " " + query.album, country), 2 * 1024 * 1024), query.album, query.artist);
    }
    static URI albumSearchUri(String term, String country) {
        return URI.create("https://itunes.apple.com/search?term=" + encode(term) + "&media=music&entity=album&country=" + country + "&limit=25");
    }
    static URI pageUri(String country, String id) { return URI.create("https://music.apple.com/" + country + "/album/-/" + id); }
    private AmPage.Album verify(AmPage.Album album, ArtworkQuery query, String song, String stage) throws AmFailure {
        diagnostic.accept(stage, album.tracks());
        AmIdentity.unique(album.tracks(), query, song, album.id());
        return album;
    }
    private AmPage.Album page(String id, String country) throws AmFailure {
        return AmPage.album(fetch.text(pageUri(country, id), 3 * 1024 * 1024), id);
    }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
}
