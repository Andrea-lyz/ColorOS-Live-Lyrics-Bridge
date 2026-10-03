package io.github.andrealtb.artwork.am;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

/** Unicode preserving equality. Version words are never removed. */
final class AmIdentity {
    record Track(String songId, String albumId, String title, String artist, String album, long durationMs, AmEdition.Info edition) {
        Track(String songId, String albumId, String title, String artist, String album, long durationMs) {
            this(songId, albumId, title, artist, album, durationMs, AmEdition.Info.UNKNOWN);
        }
    }
    record AppleLink(String country, String albumId, String songId) {}

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** A title's guest credit, e.g. "Fortnight (feat. Post Malone)". */
    private static final Pattern FEATURED = Pattern.compile("[(\\[]\\s*(?:feat\\.?|ft\\.?|featuring|with)\\s+([^)\\]]+)[)\\]]",
            Pattern.CASE_INSENSITIVE);
    /** Artist-list separators players and stores use; full-width forms are folded by NFKC first. */
    private static final Pattern CREDIT_SEPARATOR = Pattern.compile("\\s*(?:[/;,\u3001&]|\\b(?:feat|ft)\\b\\.?|\\bfeaturing\\b)\\s*",
            Pattern.CASE_INSENSITIVE);

    /** Only a guest credit, bracketed or trailing; version words such as "Taylor's Version" stay in the title. */
    private static final Pattern GUEST_CLAUSE = Pattern.compile(
            "\\s*[(\\[]\\s*(?:feat\\.?|ft\\.?|featuring)\\s+[^)\\]]+[)\\]]|\\s+(?:feat\\.|ft\\.|featuring)\\s+[^()\\[\\]]+$",
            Pattern.CASE_INSENSITIVE);

    /** Title without its guest credit, e.g. "End Game (feat. Ed Sheeran & Future)" and "End Game" agree. */
    static String coreTitle(String title) {
        return normalize(GUEST_CLAUSE.matcher(Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKC)).replaceAll(""));
    }

    /** Credited artists: the artist field split into names, plus the guests named in the title. */
    static Set<String> credits(String artist, String title) {
        Set<String> names = new TreeSet<>();
        addCredits(artist, names);
        Matcher featured = FEATURED.matcher(Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKC));
        while (featured.find()) addCredits(featured.group(1), names);
        return names;
    }
    private static void addCredits(String value, Set<String> names) {
        for (String part : CREDIT_SEPARATOR.split(Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC))) {
            String name = normalize(part);
            if (!name.isEmpty()) names.add(name);
        }
    }
    /**
     * Same artists: identical fields, or identical credited sets. Device log: a player reporting both
     * artists of "Fortnight (feat. Post Malone)" never matched the store's single "Taylor Swift".
     * Sets must be equal, so a lone primary artist never matches a duet credited to two.
     */
    static boolean sameArtists(String artist, String title, String otherArtist, String otherTitle) {
        String a = normalize(artist), b = normalize(otherArtist);
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b) || credits(artist, title).equals(credits(otherArtist, otherTitle));
    }
    /** First credited name; album-level search only, before the track table is verified. */
    static String primaryArtist(String artist) {
        for (String part : CREDIT_SEPARATOR.split(Normalizer.normalize(artist == null ? "" : artist, Normalizer.Form.NFKC))) {
            String name = normalize(part);
            if (!name.isEmpty()) return name;
        }
        return "";
    }

    static AppleLink link(String value) throws AmFailure {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"music.apple.com".equalsIgnoreCase(uri.getHost())
                    || uri.getPort() != -1 || uri.getRawUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            String[] parts = uri.getPath().split("/");
            if (parts.length < 4 || !parts[1].matches("[a-zA-Z]{2}")
                    || !(parts[2].equals("album") || parts[2].equals("song"))) throw new IllegalArgumentException();
            String id = parts[parts.length - 1];
            if (!id.matches("[0-9]{1,20}")) throw new IllegalArgumentException();
            String song = parts[2].equals("song") ? id : "";
            if (uri.getRawQuery() != null) for (String arg : uri.getRawQuery().split("&")) {
                String[] pair = arg.split("=", 2);
                if (URLDecoder.decode(pair[0], "UTF-8").equals("i")) {
                    if (pair.length != 2 || !pair[1].matches("[0-9]{1,20}") || !song.isEmpty()) throw new IllegalArgumentException();
                    song = pair[1];
                }
            }
            return new AppleLink(parts[1].toLowerCase(Locale.ROOT), parts[2].equals("album") ? id : "", song);
        } catch (Exception error) { throw new AmFailure(Status.UNSUPPORTED, "invalid_apple_link"); }
    }

    static boolean agrees(Track track, ArtworkQuery query) {
        String title = normalize(query.title), artist = normalize(query.artist), album = normalize(query.album);
        if (title.isEmpty() || artist.isEmpty() || query.durationMs <= 0 || track.durationMs() <= 0
                || Math.abs(query.durationMs - track.durationMs()) > 3000) return false;
        if (!album.isEmpty() && !album.equals(normalize(track.album()))) return false;
        if (title.equals(normalize(track.title())) && sameArtists(query.artist, query.title, track.artist(), track.title())) return true;
        return !album.isEmpty() && sameAlbumTrack(track, query);
    }

    /**
     * The motion cover belongs to the album. Inside the named album, a track whose guests are written
     * differently (an artist list such as "Taylor Swift/Ed Sheeran/Future" versus a title credit) is the
     * same track when the guest-free titles agree and each side's lead artist is credited by the other.
     * A guest's own listing or another artist never takes over the album.
     */
    static boolean sameAlbumTrack(Track track, ArtworkQuery query) {
        String core = coreTitle(query.title);
        if (core.isEmpty() || !core.equals(coreTitle(track.title()))) return false;
        String lead = primaryArtist(query.artist), storeLead = primaryArtist(track.artist());
        return !lead.isEmpty() && !storeLead.isEmpty()
                && credits(track.artist(), track.title()).contains(lead)
                && credits(query.artist, query.title).contains(storeLead);
    }

    static Track unique(List<Track> tracks, ArtworkQuery query, String songId, String albumId) throws AmFailure {
        TreeMap<String, Track> matches = new TreeMap<>();
        for (Track track : tracks) {
            if (!songId.isEmpty() && !songId.equals(track.songId())) continue;
            if (!albumId.isEmpty() && !albumId.equals(track.albumId())) continue;
            if (agrees(track, query)) matches.put(track.albumId() + "/" + track.songId(), track);
        }
        if (matches.size() > 1) {
            Track equivalent = query.album.isEmpty() ? null : explicitCleanTrack(List.copyOf(matches.values()));
            if (equivalent != null) return equivalent;
            throw new AmFailure(Status.AMBIGUOUS, "multiple_catalog_matches");
        }
        // Search is bounded and may be incomplete: an empty candidate list is not a permanent miss.
        if (matches.isEmpty()) throw new AmFailure(Status.RETRY_LATER, "catalog_match_unconfirmed", 60_000);
        return matches.firstEntry().getValue();
    }
    private static Track explicitCleanTrack(List<Track> matches) {
        if (matches.size() != 2) return null;
        Track a = matches.get(0), b = matches.get(1);
        if (a.albumId().equals(b.albumId()) || !normalize(a.title()).equals(normalize(b.title()))
                || a.durationMs() <= 0 || b.durationMs() <= 0 || Math.abs(a.durationMs() - b.durationMs()) > 3000) return null;
        java.util.ArrayList<AmEdition.Candidate> editions = new java.util.ArrayList<>();
        for (Track track : matches) editions.add(new AmEdition.Candidate(track.albumId(), track.artist(), track.album(), track.edition()));
        String chosen = AmEdition.explicitCleanChoice(editions);
        if (chosen == null) return null;
        return a.albumId().equals(chosen) ? a : b;
    }
    static String diagnostics(List<Track> tracks, ArtworkQuery query) {
        int titles = 0, artists = 0, albums = 0, durations = 0, complete = 0;
        java.util.Map<String, Track> matched = new TreeMap<>();
        for (Track track : tracks) {
            if (normalize(query.title).equals(normalize(track.title()))
                    || !query.album.isEmpty() && coreTitle(query.title).equals(coreTitle(track.title()))) titles++;
            if (sameArtists(query.artist, query.title, track.artist(), track.title())
                    || !query.album.isEmpty() && sameAlbumTrack(track, query)) artists++;
            if (query.album.isEmpty() || normalize(query.album).equals(normalize(track.album()))) albums++;
            if (query.durationMs > 0 && track.durationMs() > 0 && Math.abs(query.durationMs - track.durationMs()) <= 3000) durations++;
            if (agrees(track, query)) { complete++; matched.put(track.albumId() + "/" + track.songId(), track); }
        }
        return "candidates=" + tracks.size() + " titleMatches=" + titles + " artistMatches=" + artists
                + " albumMatches=" + albums + " durationMatches=" + durations + " completeMatches=" + complete
                + " explicitCleanEquivalent=" + (!query.album.isEmpty() && explicitCleanTrack(List.copyOf(matched.values())) != null);
    }
}
