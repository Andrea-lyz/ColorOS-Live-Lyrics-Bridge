package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import java.util.LinkedHashMap;

/**
 * Insertion-order LRU of last-good KuWo covers. Peek does not refresh recency;
 * remember removes then inserts so a later real track change still misses the
 * snapshot and keeps native behavior.
 */
public final class KuWoArtworkSnapshotStore<T> {
    private final Object lock = new Object();
    private final LinkedHashMap<String, T> snapshots = new LinkedHashMap<>();
    private final int limit;

    public KuWoArtworkSnapshotStore() {
        this(KuWoCoverPolicy.SNAPSHOT_LIMIT);
    }

    public KuWoArtworkSnapshotStore(int limit) {
        this.limit = Math.max(1, limit);
    }

    public void remember(String trackKey, T value) {
        synchronized (lock) {
            snapshots.remove(trackKey);
            snapshots.put(trackKey, value);
            while (snapshots.size() > limit) {
                String eldest = snapshots.keySet().iterator().next();
                snapshots.remove(eldest);
            }
        }
    }

    public void rememberKeys(String snapshotKey, String titleArtistKey, T value) {
        if (value == null) {
            return;
        }
        remember(snapshotKey, value);
        if (titleArtistKey != null && !titleArtistKey.isEmpty()) {
            remember(titleArtistKey, value);
        }
    }

    public T peek(String trackKey) {
        synchronized (lock) {
            return snapshots.get(trackKey);
        }
    }
}
