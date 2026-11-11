package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import java.util.LinkedHashMap;

/**
 * Insertion-order LRU of last-good KuWo covers. Peek does not refresh recency;
 * remember removes then inserts so a later real track change still misses the
 * snapshot and keeps native behavior.
 *
 * <p>A strong snapshot key is the media id ({@code id:...}). The title/artist alias is
 * retained for the Seedling path, which exposes no media id, but that alias carries the
 * strong key that produced it so a same-name track with a different media id cannot
 * borrow the previous track's cover.
 */
public final class KuWoArtworkSnapshotStore<T> {
    private final Object lock = new Object();
    private final LinkedHashMap<String, Entry<T>> snapshots = new LinkedHashMap<>();
    private final int limit;

    public KuWoArtworkSnapshotStore() {
        this(KuWoCoverPolicy.SNAPSHOT_LIMIT);
    }

    public KuWoArtworkSnapshotStore(int limit) {
        this.limit = Math.max(1, limit);
    }

    public void remember(String trackKey, T value) {
        if (trackKey == null || trackKey.isEmpty() || value == null) {
            return;
        }
        rememberEntry(trackKey, strongKeyOf(trackKey), value);
    }

    public void rememberKeys(String snapshotKey, String titleArtistKey, T value) {
        if (value == null) {
            return;
        }
        String strongKey = strongKeyOf(snapshotKey);
        if (snapshotKey != null && !snapshotKey.isEmpty()) {
            rememberEntry(snapshotKey, strongKey, value);
        }
        if (titleArtistKey != null
                && !titleArtistKey.isEmpty()
                && !titleArtistKey.equals(snapshotKey)) {
            rememberEntry(titleArtistKey, strongKey, value);
        }
    }

    public T peek(String trackKey) {
        synchronized (lock) {
            Entry<T> entry = snapshots.get(trackKey);
            return entry == null ? null : entry.value;
        }
    }

    /**
     * Returns an entry only when its capture identity matches the live identity. A legacy
     * title-only entry is never used while a strong identity is required.
     */
    public T peekIfStrongIdentity(String trackKey, String expectedStrongKey) {
        synchronized (lock) {
            Entry<T> entry = snapshots.get(trackKey);
            if (entry == null) {
                return null;
            }
            String expected = normalizeStrongKey(expectedStrongKey);
            if (expected.isEmpty()) {
                return entry.strongKey == null ? entry.value : null;
            }
            return expected.equals(entry.strongKey) ? entry.value : null;
        }
    }

    private void rememberEntry(String key, String strongKey, T value) {
        synchronized (lock) {
            snapshots.remove(key);
            snapshots.put(key, new Entry<>(value, strongKey));
            while (snapshots.size() > limit) {
                String eldest = snapshots.keySet().iterator().next();
                snapshots.remove(eldest);
            }
        }
    }

    private static String strongKeyOf(String snapshotKey) {
        return snapshotKey != null && snapshotKey.startsWith("id:") ? snapshotKey : null;
    }

    private static String normalizeStrongKey(String key) {
        return key == null ? "" : key.trim();
    }

    private static final class Entry<T> {
        final T value;
        final String strongKey;

        Entry(T value, String strongKey) {
            this.value = value;
            this.strongKey = strongKey;
        }
    }
}
