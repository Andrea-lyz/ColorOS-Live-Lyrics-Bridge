package io.github.andrealtb.artwork.am;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

/**
 * Local album names the plugin was recently asked for, kept only in its private storage and never
 * logged, so the binding page can offer the exact name the player reports.
 */
final class AmRecentAlbums {
    enum Outcome { MATCHED, BOUND, NO_MOTION, UNMATCHED, FAILED }
    record Entry(String album, String artist, Outcome outcome) {
        boolean sameAlbum(Entry other) {
            return AmIdentity.normalize(album).equals(AmIdentity.normalize(other.album))
                    && AmIdentity.normalize(artist).equals(AmIdentity.normalize(other.artist));
        }
    }
    static final int MAX = 30;
    private static final String PREFERENCES = "am_recent_albums";
    private static final String KEY = "albums";
    private static final Object LOCK = new Object();

    private AmRecentAlbums() {}

    /**
     * Every requested album is listed so it can be bound, whatever went wrong; only a cancelled
     * request, which says nothing about the album, is left out.
     */
    static Outcome outcome(AmFailure failure) {
        if (failure.status == Status.ERROR && failure.reason.equals("cancelled")) return null;
        return switch (failure.status) {
            case NO_MOTION -> Outcome.NO_MOTION;
            case UNSUPPORTED -> failure.reason.equals("no_square_motion_asset") ? Outcome.NO_MOTION : Outcome.FAILED;
            case NO_MATCH, AMBIGUOUS -> Outcome.UNMATCHED;
            case RETRY_LATER -> failure.reason.equals("catalog_match_unconfirmed")
                    || failure.reason.equals("catalog_album_unconfirmed") ? Outcome.UNMATCHED : Outcome.FAILED;
            default -> Outcome.FAILED;
        };
    }

    /** Most recent first; returns the same list when nothing changed so callers can skip the write. */
    static List<Entry> note(List<Entry> entries, Entry entry) {
        if (AmIdentity.normalize(entry.album()).isEmpty()) return entries;
        if (!entries.isEmpty() && entries.get(0).equals(entry)) return entries;
        List<Entry> next = new ArrayList<>();
        next.add(entry);
        for (Entry old : entries) if (!old.sameAlbum(entry) && next.size() < MAX) next.add(old);
        return next;
    }

    static void note(Context context, ArtworkQuery query, Outcome outcome) {
        Entry entry = new Entry(clip(query.album), clip(query.artist), outcome);
        synchronized (LOCK) {
            List<Entry> entries = load(context), next = note(entries, entry);
            if (next != entries) prefs(context).edit().putString(KEY, encode(next)).apply();
        }
    }

    static List<Entry> load(Context context) { return decode(prefs(context).getString(KEY, "[]")); }

    static void clear(Context context) { synchronized (LOCK) { prefs(context).edit().clear().apply(); } }

    static String encode(List<Entry> entries) {
        try {
            JSONArray items = new JSONArray();
            for (Entry entry : entries) items.put(new JSONObject().put("album", entry.album()).put("artist", entry.artist())
                    .put("outcome", entry.outcome().name()));
            return items.toString();
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    static List<Entry> decode(String value) {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(value);
            for (int i = 0; i < items.length() && entries.size() < MAX; i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                try {
                    Entry entry = new Entry(clip(item.optString("album")), clip(item.optString("artist")),
                            Outcome.valueOf(item.optString("outcome")));
                    if (!AmIdentity.normalize(entry.album()).isEmpty()) entries.add(entry);
                } catch (IllegalArgumentException ignored) { /* unknown outcome from another version */ }
            }
        } catch (Exception ignored) { /* unreadable store: empty history */ }
        return entries;
    }

    private static String clip(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 512 ? text.substring(0, 512) : text;
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
