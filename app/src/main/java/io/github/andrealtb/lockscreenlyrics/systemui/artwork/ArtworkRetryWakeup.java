package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.util.function.BooleanSupplier;

/** A single cancellable wake-up. A superseded timer cannot revive an old song/surface. */
public final class ArtworkRetryWakeup implements AutoCloseable {
    public interface Scheduler {
        void post(Runnable task, long delayMs);
        void remove(Runnable task);
    }
    private final Scheduler scheduler;
    private long revision;
    private Runnable pending;
    private boolean closed;
    public ArtworkRetryWakeup(Scheduler scheduler) { this.scheduler = scheduler; }
    public void arm(long delayMs, BooleanSupplier stillCurrent, Runnable refresh) {
        cancel();
        if (closed || delayMs < 1 || delayMs > 86_400_000) return;
        long expected = revision;
        pending = () -> {
            if (closed || revision != expected) return;
            pending = null;
            ++revision;
            if (stillCurrent.getAsBoolean()) refresh.run();
        };
        scheduler.post(pending, delayMs);
    }
    public void cancel() {
        ++revision;
        if (pending != null) scheduler.remove(pending);
        pending = null;
    }
    @Override public void close() { cancel(); closed = true; }
}
