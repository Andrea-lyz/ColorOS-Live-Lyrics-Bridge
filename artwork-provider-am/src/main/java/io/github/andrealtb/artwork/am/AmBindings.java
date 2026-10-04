package io.github.andrealtb.artwork.am;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.andrealtb.artwork.contract.ArtworkQuery;

/**
 * Album choices the user made in the plugin for local files whose tags differ from the catalog.
 * The player's metadata stays as it is: a local album name, optionally narrowed to one artist,
 * names an Apple Music album, and that album's motion cover is used without a track check.
 */
final class AmBindings {
    static final int MAX = 200;
    private static final String PREFERENCES = "am_bindings";
    private static final String KEY = "bindings";

    record Binding(String localAlbum, String localArtist, String country, String albumId, String title, String artist) {
        boolean sameLocal(Binding other) {
            return AmIdentity.normalize(localAlbum).equals(AmIdentity.normalize(other.localAlbum))
                    && AmIdentity.normalize(localArtist).equals(AmIdentity.normalize(other.localArtist));
        }
    }

    private AmBindings() {}

    /** The narrowest binding for this local album: one limited to the playing artist wins over one for any artist. */
    static Binding find(List<Binding> bindings, ArtworkQuery query) {
        String album = AmIdentity.normalize(query.album);
        if (album.isEmpty()) return null;
        Binding anyArtist = null;
        for (Binding binding : bindings) {
            if (!album.equals(AmIdentity.normalize(binding.localAlbum()))) continue;
            String artist = AmIdentity.normalize(binding.localArtist());
            if (artist.isEmpty()) { if (anyArtist == null) anyArtist = binding; continue; }
            if (artist.equals(AmIdentity.normalize(query.artist))
                    || AmIdentity.credits(query.artist, query.title).contains(artist)) return binding;
        }
        return anyArtist;
    }

    /** Newest first; a binding for the same local album and artist is replaced. */
    static List<Binding> put(List<Binding> bindings, Binding binding) {
        List<Binding> next = new ArrayList<>();
        next.add(binding);
        for (Binding old : bindings) if (!old.sameLocal(binding) && next.size() < MAX) next.add(old);
        return next;
    }

    static List<Binding> remove(List<Binding> bindings, Binding binding) {
        List<Binding> next = new ArrayList<>(bindings);
        next.removeIf(old -> old.sameLocal(binding));
        return next;
    }

    static String encode(List<Binding> bindings) {
        try {
            JSONArray items = new JSONArray();
            for (Binding binding : bindings) items.put(new JSONObject().put("localAlbum", binding.localAlbum())
                    .put("localArtist", binding.localArtist()).put("country", binding.country()).put("albumId", binding.albumId())
                    .put("title", binding.title()).put("artist", binding.artist()));
            return items.toString();
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    /** Entries that fail validation are dropped one by one; a damaged entry never disables the others. */
    static List<Binding> decode(String value) {
        List<Binding> bindings = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(value);
            for (int i = 0; i < items.length() && bindings.size() < MAX; i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                Binding binding = new Binding(item.optString("localAlbum"), item.optString("localArtist"),
                        item.optString("country"), item.optString("albumId"), item.optString("title"), item.optString("artist"));
                if (valid(binding)) bindings.add(binding);
            }
        } catch (Exception ignored) { /* unreadable store: no bindings */ }
        return bindings;
    }

    static boolean valid(Binding binding) {
        return !AmIdentity.normalize(binding.localAlbum()).isEmpty() && binding.country().matches("[a-z]{2}")
                && binding.albumId().matches("[0-9]{1,20}") && binding.localAlbum().length() <= 512
                && binding.localArtist().length() <= 512 && binding.title().length() <= 512 && binding.artist().length() <= 512;
    }

    static List<Binding> load(Context context) {
        return decode(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, "[]"));
    }

    static void save(Context context, List<Binding> bindings) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, encode(bindings)).apply();
    }
}
