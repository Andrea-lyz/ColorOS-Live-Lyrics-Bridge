package io.github.andrealtb.artwork.local;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded request ownership by actual UID and unguessable client request ID. Monotonic clock. */
final class RequestLeaseTable<T> {
    private static final int MAX_TOTAL = 16;
    private static final int MAX_PER_UID = 4;
    private final Map<String, Lease<T>> leases = new HashMap<>();

    synchronized boolean add(int uid, String id, T value, long now, long ttl) {
        String key = key(uid, id);
        if (ttl <= 0 || ttl > 60_000 || leases.containsKey(key) || leases.size() >= MAX_TOTAL) return false;
        int count = 0;
        for (Lease<T> lease : leases.values()) if (lease.uid == uid) count++;
        if (count >= MAX_PER_UID) return false;
        leases.put(key, new Lease<>(uid, value, now + ttl));
        return true;
    }

    synchronized T get(int uid, String id, long now) {
        Lease<T> lease = leases.get(key(uid, id));
        return lease != null && now < lease.deadline ? lease.value : null;
    }

    synchronized T remove(int uid, String id) {
        Lease<T> lease = leases.remove(key(uid, id));
        return lease == null ? null : lease.value;
    }

    synchronized boolean removeIfSame(int uid, String id, T expected) {
        Lease<T> lease = leases.get(key(uid, id));
        if (lease == null || lease.value != expected) return false;
        leases.remove(key(uid, id));
        return true;
    }

    synchronized List<T> reap(long now) {
        List<T> expired = new ArrayList<>();
        leases.values().removeIf(lease -> {
            if (now < lease.deadline) return false;
            expired.add(lease.value);
            return true;
        });
        return expired;
    }

    synchronized List<T> values() {
        List<T> values = new ArrayList<>();
        for (Lease<T> lease : leases.values()) values.add(lease.value);
        return values;
    }

    synchronized List<T> clear() {
        List<T> values = values();
        leases.clear();
        return values;
    }

    private static String key(int uid, String id) { return uid + "/" + id; }

    private static final class Lease<T> {
        final int uid;
        final T value;
        final long deadline;

        Lease(int uid, T value, long deadline) {
            this.uid = uid;
            this.value = value;
            this.deadline = deadline;
        }
    }
}
