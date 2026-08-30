package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class KuWoArtworkSnapshotStoreTest {
    @Test
    public void rememberEvictsEldestWhenOverLimit() {
        KuWoArtworkSnapshotStore<String> store = new KuWoArtworkSnapshotStore<>(2);
        store.remember("a", "A");
        store.remember("b", "B");
        store.remember("c", "C");
        assertNull(store.peek("a"));
        assertEquals("B", store.peek("b"));
        assertEquals("C", store.peek("c"));
    }

    @Test
    public void rememberRefreshesRecencyWithoutPeekDoingSo() {
        KuWoArtworkSnapshotStore<String> store = new KuWoArtworkSnapshotStore<>(2);
        store.remember("a", "A");
        store.remember("b", "B");
        store.peek("a");
        store.remember("c", "C");
        assertNull(store.peek("a"));
        store.remember("b", "B2");
        store.remember("d", "D");
        assertEquals("B2", store.peek("b"));
        assertNull(store.peek("c"));
        assertEquals("D", store.peek("d"));
    }

    @Test
    public void rememberKeysSkipsNullValueAndEmptyTitleArtistKey() {
        KuWoArtworkSnapshotStore<String> store = new KuWoArtworkSnapshotStore<>(4);
        store.rememberKeys("id:1", "song|artist", null);
        assertNull(store.peek("id:1"));
        store.rememberKeys("id:1", "", "icon");
        assertEquals("icon", store.peek("id:1"));
        assertNull(store.peek(""));
        store.rememberKeys("id:1", "song|artist", "icon2");
        assertEquals("icon2", store.peek("id:1"));
        assertEquals("icon2", store.peek("song|artist"));
    }

    @Test
    public void nonPositiveLimitKeepsTheLatestEntry() {
        KuWoArtworkSnapshotStore<String> store = new KuWoArtworkSnapshotStore<>(0);
        store.remember("a", "A");
        assertEquals("A", store.peek("a"));
        store.remember("b", "B");
        assertNull(store.peek("a"));
        assertEquals("B", store.peek("b"));
    }
}
