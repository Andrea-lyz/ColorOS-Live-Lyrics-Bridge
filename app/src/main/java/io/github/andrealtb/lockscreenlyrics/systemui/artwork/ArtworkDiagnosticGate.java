package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Each owner deduplicates its own events. Changed states have a bounded burst, not a 3s blackout. */
public final class ArtworkDiagnosticGate {
    private static final int MAX_EVENTS = 32;
    private static final int MAX_PER_SECOND = 24;
    private final LinkedHashMap<String, String> emitted = new LinkedHashMap<>();
    private long windowStart = -1;
    private int used;
    private int limited;

    public synchronized boolean accept(String event, String state, long now) {
        if (Objects.equals(emitted.get(event), state)) return false;
        if (windowStart < 0 || now < windowStart || now - windowStart >= 1000) {
            windowStart = now;
            used = 0;
        }
        if (used >= MAX_PER_SECOND) {
            if (limited < Integer.MAX_VALUE) limited++;
            return false;
        }
        used++;
        if (!emitted.containsKey(event) && emitted.size() >= MAX_EVENTS) {
            emitted.remove(emitted.keySet().iterator().next());
        }
        emitted.put(event, state);
        return true;
    }

    public synchronized int takeLimited() {
        int count = limited;
        limited = 0;
        return count;
    }
}
