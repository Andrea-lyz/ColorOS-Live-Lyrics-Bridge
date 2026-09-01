package io.github.andrealtb.lockscreenlyrics;

import java.util.concurrent.atomic.AtomicLong;

/** Monotonic ownership gate for the two SystemUI lyric-loader executor paths. */
final class SystemUiLyricPublicationGate {
    private final AtomicLong epoch = new AtomicLong();

    long beginLoad() {
        return epoch.incrementAndGet();
    }

    void invalidate() {
        epoch.incrementAndGet();
    }

    long currentEpoch() {
        return epoch.get();
    }

    boolean canCommit(long candidateEpoch) {
        return candidateEpoch == epoch.get();
    }
}
