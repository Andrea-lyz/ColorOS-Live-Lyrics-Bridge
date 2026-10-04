package io.github.andrealtb.artwork.am;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

final class AmPage {
    record Album(String id, List<AmIdentity.Track> tracks, URI master) {}
    /** One album search result offered on the binding page; nothing here is trusted for matching. */
    record AlbumHit(String id, String title, String artist, String releaseDay, int trackCount, boolean explicit) {}
    private static final Pattern SERVER_DATA = Pattern.compile("<script\\b(?=[^>]*\\bid=[\"']serialized-server-data[\"'])[^>]*>(.*?)</script>", Pattern.DOTALL);

    static List<AmIdentity.Track> itunes(String json) throws AmFailure {
        try {
            JSONArray results = new JSONObject(json).getJSONArray("results");
            if (results.length() > 250) throw new IllegalArgumentException();
            List<AmIdentity.Track> tracks = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                if (!"track".equals(item.optString("wrapperType")) || !"song".equals(item.optString("kind"))) continue;
                tracks.add(new AmIdentity.Track(id(item, "trackId"), id(item, "collectionId"), item.getString("trackName"),
                        item.getString("artistName"), item.getString("collectionName"), item.optLong("trackTimeMillis", 0), edition(item)));
            }
            return tracks;
        } catch (Exception error) { throw new AmFailure(Status.RETRY_LATER, "catalog_schema_changed", 300_000); }
    }

    static Album album(String html, String expectedId) throws AmFailure {
        try {
            Matcher matcher = SERVER_DATA.matcher(html);
            if (!matcher.find()) throw new IllegalArgumentException();
            JSONArray sections = new JSONObject(matcher.group(1)).getJSONArray("data").getJSONObject(0)
                    .getJSONObject("data").getJSONArray("sections");
            JSONObject header = null;
            JSONArray trackItems = null;
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.getJSONObject(i);
                String sectionId = section.optString("id");
                if (sectionId.equals("album-detail-header-section - " + expectedId)) header = section.getJSONArray("items").getJSONObject(0);
                if (sectionId.equals("track-list - " + expectedId)) trackItems = section.getJSONArray("items");
            }
            if (header == null || trackItems == null || trackItems.length() > 250) throw new IllegalArgumentException();
            JSONObject descriptor = header.getJSONObject("contentDescriptor");
            if (!"album".equals(descriptor.getString("kind"))
                    || !expectedId.equals(id(descriptor.getJSONObject("identifiers"), "storeAdamID"))) throw new IllegalArgumentException();
            String albumName = header.getString("title");
            List<AmIdentity.Track> tracks = new ArrayList<>();
            for (int i = 0; i < trackItems.length(); i++) {
                JSONObject item = trackItems.getJSONObject(i), content = item.getJSONObject("contentDescriptor");
                if (!"song".equals(content.optString("kind"))) continue;
                String songId = id(content.getJSONObject("identifiers"), "storeAdamID");
                AmIdentity.AppleLink link = AmIdentity.link(content.getString("url"));
                if (!expectedId.equals(link.albumId()) || !songId.equals(link.songId())) throw new IllegalArgumentException();
                tracks.add(new AmIdentity.Track(songId, expectedId, item.getString("title"), item.getString("artistName"),
                        albumName, item.getLong("duration")));
            }
            if (tracks.isEmpty()) throw new IllegalArgumentException();
            URI master = null;
            if (header.has("videoArtwork") && !header.isNull("videoArtwork")) {
                JSONObject dictionary = header.getJSONObject("videoArtwork").getJSONObject("dictionary");
                for (String key : List.of("motionDetailSquare", "motionSquareVideo1x1", "motionDetailRaw")) {
                    JSONObject video = dictionary.optJSONObject(key);
                    if (video != null) { master = AmHls.mediaUri(URI.create(video.getString("video"))); break; }
                }
                if (master == null) throw new AmFailure(Status.UNSUPPORTED, "no_square_motion_asset");
            }
            return new Album(expectedId, List.copyOf(tracks), master);
        } catch (AmFailure failure) { throw failure; }
        catch (Exception error) { throw new AmFailure(Status.RETRY_LATER, "web_schema_changed", 300_000); }
    }
    private static String id(JSONObject object, String name) throws Exception {
        String id = object.get(name).toString();
        if (!id.matches("[0-9]{1,20}")) throw new IllegalArgumentException();
        return id;
    }
    static List<String> albumIds(String json, String album, String artist) throws AmFailure {
        try {
            JSONArray results = new JSONObject(json).getJSONArray("results");
            if (results.length() > 250) throw new IllegalArgumentException();
            java.util.Set<String> matches = new java.util.TreeSet<>();
            List<AmEdition.Candidate> editions = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                if ("collection".equals(item.optString("wrapperType")) && "Album".equals(item.optString("collectionType"))
                        && !AmIdentity.normalize(album).isEmpty() && AmIdentity.normalize(album).equals(AmIdentity.normalize(item.optString("collectionName")))
                        && (AmIdentity.sameArtists(artist, "", item.optString("artistName"), "")
                            || !AmIdentity.primaryArtist(artist).isEmpty()
                                && AmIdentity.primaryArtist(artist).equals(AmIdentity.primaryArtist(item.optString("artistName"))))) {
                    String albumId = id(item, "collectionId");
                    matches.add(albumId);
                    editions.add(new AmEdition.Candidate(albumId, item.getString("artistName"), item.getString("collectionName"), edition(item)));
                }
            }
            String preferred = AmEdition.explicitCleanChoice(editions);
            return preferred == null ? List.copyOf(matches) : List.of(preferred);
        } catch (Exception error) { throw new AmFailure(Status.RETRY_LATER, "catalog_schema_changed", 300_000); }
    }
    static List<AlbumHit> albumHits(String json) throws AmFailure {
        try {
            JSONArray results = new JSONObject(json).getJSONArray("results");
            if (results.length() > 250) throw new IllegalArgumentException();
            List<AlbumHit> hits = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                if (!"collection".equals(item.optString("wrapperType")) || !"Album".equals(item.optString("collectionType"))) continue;
                AmEdition.Info info = edition(item);
                hits.add(new AlbumHit(id(item, "collectionId"), item.getString("collectionName"), item.getString("artistName"),
                        info.releaseDay(), info.trackCount(), "explicit".equals(info.rating())));
            }
            return hits;
        } catch (Exception error) { throw new AmFailure(Status.RETRY_LATER, "catalog_schema_changed", 300_000); }
    }
    private static AmEdition.Info edition(JSONObject item) {
        // Album-level rating is essential: individual non-explicit tracks can exist on an Explicit album.
        return new AmEdition.Info(item.optString("collectionExplicitness"), item.optString("releaseDate"), item.optInt("trackCount", 0));
    }
    static JSONObject snapshot(Album album) throws Exception {
        JSONArray tracks = new JSONArray();
        for (AmIdentity.Track track : album.tracks()) tracks.put(new JSONObject().put("song", track.songId())
                .put("title", track.title()).put("artist", track.artist()).put("album", track.album()).put("duration", track.durationMs()));
        return new JSONObject().put("schema", 2).put("id", album.id()).put("master", album.master() == null ? JSONObject.NULL : album.master().toString()).put("tracks", tracks);
    }
    static Album snapshot(JSONObject value) throws Exception {
        if (value.getInt("schema") != 2) throw new IllegalArgumentException();
        String albumId = id(value, "id");
        URI master = value.isNull("master") ? null : AmHls.mediaUri(URI.create(value.getString("master")));
        JSONArray items = value.getJSONArray("tracks");
        if (items.length() == 0 || items.length() > 250) throw new IllegalArgumentException();
        List<AmIdentity.Track> tracks = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String title = item.getString("title"), artist = item.getString("artist"), album = item.getString("album");
            long duration = item.getLong("duration");
            if (title.isEmpty() || artist.isEmpty() || album.isEmpty() || title.length() > 512 || artist.length() > 512
                    || album.length() > 512 || duration <= 0 || duration > 7L * 86400 * 1000) throw new IllegalArgumentException();
            tracks.add(new AmIdentity.Track(id(item, "song"), albumId, title, artist, album, duration));
        }
        return new Album(albumId, List.copyOf(tracks), master);
    }
}
