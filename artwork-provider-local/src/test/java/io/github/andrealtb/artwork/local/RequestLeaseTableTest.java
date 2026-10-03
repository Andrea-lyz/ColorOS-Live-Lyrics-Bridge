package io.github.andrealtb.artwork.local;

import org.junit.Test;

import static org.junit.Assert.*;

public class RequestLeaseTableTest {
    @Test public void guessedIdFromAnotherUidCannotReadOrCancelLease() {
        RequestLeaseTable<Object> table = new RequestLeaseTable<>();
        Object asset = new Object();
        assertTrue(table.add(20001, "same-id", asset, 0, 60_000));
        assertNull(table.get(20002, "same-id", 1));
        assertNull(table.remove(20002, "same-id"));
        assertSame(asset, table.get(20001, "same-id", 1));
    }

    @Test public void duplicateAndPerUidRequestFloodsAreBounded() {
        RequestLeaseTable<Object> table = new RequestLeaseTable<>();
        Object asset = new Object();
        assertTrue(table.add(1, "id", asset, 0, 100));
        assertFalse(table.add(1, "id", new Object(), 0, 100));
        for (int i = 1; i < 4; i++) assertTrue(table.add(1, "id" + i, new Object(), 0, 100));
        assertFalse(table.add(1, "fifth", new Object(), 0, 100));
        assertTrue(table.add(2, "id", new Object(), 0, 100));
        assertSame(asset, table.get(1, "id", 1));
    }

    @Test public void totalBudgetAndExpiryDoNotPermanentlyPinAssets() {
        RequestLeaseTable<Object> table = new RequestLeaseTable<>();
        for (int i = 0; i < 16; i++) assertTrue(table.add(i, "id", new Object(), 50, 100));
        assertFalse(table.add(17, "id", new Object(), 50, 100));
        assertNotNull(table.get(1, "id", 149));
        assertNull(table.get(1, "id", 150));
        assertEquals(16, table.reap(150).size());
        assertTrue(table.values().isEmpty());
        assertTrue(table.add(17, "id", new Object(), 150, 100));
    }

    @Test public void oldWorkerOrDeathCallbackCannotRemoveReplacement() {
        RequestLeaseTable<Object> table = new RequestLeaseTable<>();
        Object old = new Object();
        Object next = new Object();
        table.add(1, "id", old, 0, 100);
        assertSame(old, table.remove(1, "id"));
        table.add(1, "id", next, 1, 100);
        assertFalse(table.removeIfSame(1, "id", old));
        assertSame(next, table.get(1, "id", 2));
        assertTrue(table.removeIfSame(1, "id", next));
        assertNull(table.get(1, "id", 2));
    }

    @Test public void serviceDestructionReclaimsEveryLease() {
        RequestLeaseTable<Object> table = new RequestLeaseTable<>();
        table.add(1, "a", new Object(), 0, 100);
        table.add(2, "b", new Object(), 0, 100);
        assertEquals(2, table.clear().size());
        assertTrue(table.clear().isEmpty());
        assertFalse(table.add(1, "c", new Object(), 0, 0));
        assertFalse(table.add(1, "c", new Object(), 0, 60_001));
    }
}
