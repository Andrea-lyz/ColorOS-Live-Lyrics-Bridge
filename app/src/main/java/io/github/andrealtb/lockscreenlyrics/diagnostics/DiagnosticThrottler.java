package io.github.andrealtb.lockscreenlyrics.diagnostics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class DiagnosticThrottler {
    private final long windowMillis;
    private final ConcurrentHashMap<String, Long> lastLoggedTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> suppressedCounts =
            new ConcurrentHashMap<>();

    DiagnosticThrottler(long windowMillis) {
        this.windowMillis = Math.max(0L, windowMillis);
    }

    boolean shouldLog(String key, long nowMillis) {
        if (key == null || key.isEmpty()) {
            return true;
        }
        Long lastTime = lastLoggedTimes.get(key);
        if (lastTime == null || nowMillis - lastTime >= windowMillis) {
            lastLoggedTimes.put(key, nowMillis);
            return true;
        }
        suppressedCounts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        return false;
    }

    int takeSuppressed(String key) {
        AtomicInteger count = suppressedCounts.remove(key);
        return count == null ? 0 : count.get();
    }

    void clear() {
        lastLoggedTimes.clear();
        suppressedCounts.clear();
    }
}
