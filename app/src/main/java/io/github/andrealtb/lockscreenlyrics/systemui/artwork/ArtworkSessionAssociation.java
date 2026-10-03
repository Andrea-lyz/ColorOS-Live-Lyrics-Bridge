package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.util.List;

/** Exact matching across the entire current active set, including duplicate IDs/users. */
public final class ArtworkSessionAssociation {
    public record Entry<T>(String playerId, String packageName, int userId, T token,
            String title, String artist, long durationMs, boolean active) {}

    private ArtworkSessionAssociation() {}

    public static <T> Entry<T> match(ArtworkCardIdentity card, List<Entry<T>> entries) {
        Entry<T> selected = matchSession(card, entries);
        return matchesSong(card, selected) ? selected : null;
    }

    public static <T> Entry<T> matchSession(ArtworkCardIdentity card, List<Entry<T>> entries) {
        if (card == null || card.playerId().isEmpty() || card.packageName().isEmpty()
                || entries == null || entries.size() > 32) return null;
        Entry<T> selected = null;
        for (Entry<T> entry : entries) {
            if (!entry.active() || !card.playerId().equals(entry.playerId())
                    || !card.packageName().equals(entry.packageName())) continue;
            // Even duplicate copies of the same token are ambiguous source data; never choose the first.
            if (selected != null) return null;
            selected = entry;
        }
        if (selected == null || selected.token() == null || selected.userId() < 0) return null;
        return selected;
    }

    public static boolean matchesSong(ArtworkCardIdentity card, Entry<?> entry) {
        return card != null && card.complete() && entry != null
                && card.title().equals(ArtworkCardIdentity.clean(entry.title()))
                && card.artist().equals(ArtworkCardIdentity.clean(entry.artist()));
    }
}
