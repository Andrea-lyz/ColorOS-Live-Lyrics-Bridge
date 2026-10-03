package io.github.andrealtb.artwork.am;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * Notices this process being stopped while work is in flight: a short sleep that returns seconds
 * late. Device logs showed CPU-only steps taking 8-10 s and resuming exactly when SystemUI called
 * in again, which is an OEM background freezer rather than the network. Monotonic time does not
 * advance in device suspend, so only a stopped process on an awake device counts.
 */
final class AmPauseDetector {
    static final long TICK_MS = 250;
    static final long PAUSE_MS = 1_500;
    private static final Object LOCK = new Object();
    private static volatile LongConsumer listener = gap -> {};
    private static final AtomicLong PAUSES = new AtomicLong();
    private static int active;
    private static Thread thread;

    private AmPauseDetector() {}

    static void listen(LongConsumer onPause) { listener = onPause; }

    /** Process-wide count; a change during a request means its timeouts measured a stopped process. */
    static long pauses() { return PAUSES.get(); }

    static void begin() {
        synchronized (LOCK) {
            if (active++ > 0) return;
            thread = new Thread(AmPauseDetector::watch, "artwork-am-pause");
            thread.setDaemon(true);
            thread.start();
        }
    }

    static void end() {
        synchronized (LOCK) {
            if (active == 0 || --active > 0) return;
            if (thread != null) thread.interrupt();
            thread = null;
        }
    }

    private static void watch() {
        long last = System.nanoTime();
        while (!Thread.currentThread().isInterrupted()) {
            try { Thread.sleep(TICK_MS); }
            catch (InterruptedException stopped) { return; }
            long now = System.nanoTime();
            long late = TimeUnit.NANOSECONDS.toMillis(now - last) - TICK_MS;
            last = now;
            if (late >= PAUSE_MS) {
                PAUSES.incrementAndGet();
                try { listener.accept(late); } catch (RuntimeException ignored) { /* diagnostics only */ }
            }
        }
    }
}
