package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ArtworkRetryWakeupTest {
    private static final class Scheduler implements ArtworkRetryWakeup.Scheduler {
        final ArrayList<Runnable> tasks = new ArrayList<>();
        long delay;
        public void post(Runnable task, long ms) { tasks.add(task); delay = ms; }
        public void remove(Runnable task) { tasks.remove(task); }
    }
    @Test public void dueTimerWakesOnceWithoutPreDrawOrPause() {
        var scheduler = new Scheduler(); var wakeup = new ArtworkRetryWakeup(scheduler); var calls = new AtomicInteger();
        wakeup.arm(30000, () -> true, calls::incrementAndGet);
        assertEquals(30000, scheduler.delay);
        var job = scheduler.tasks.get(0); job.run(); job.run(); assertEquals(1, calls.get());
    }
    @Test public void cancellationAndCloseDropEvenAlreadyDequeuedCallbacks() {
        var scheduler = new Scheduler(); var wakeup = new ArtworkRetryWakeup(scheduler); var calls = new AtomicInteger();
        wakeup.arm(1000, () -> true, calls::incrementAndGet); var old = scheduler.tasks.get(0);
        wakeup.cancel(); old.run(); assertEquals(0, calls.get());
        wakeup.arm(1000, () -> true, calls::incrementAndGet); var closing = scheduler.tasks.get(0);
        wakeup.close(); closing.run(); assertEquals(0, calls.get());
    }
    @Test public void supersededSongOrIneligibleSurfaceCannotBeRevived() {
        var scheduler = new Scheduler(); var wakeup = new ArtworkRetryWakeup(scheduler); var calls = new AtomicInteger();
        wakeup.arm(1000, () -> true, calls::incrementAndGet); var old = scheduler.tasks.get(0);
        wakeup.arm(2000, () -> false, calls::incrementAndGet); var latest = scheduler.tasks.get(0);
        old.run(); latest.run(); assertEquals(0, calls.get());
    }
    @Test public void unboundedRetryHintDoesNotSchedule() {
        var scheduler = new Scheduler(); var wakeup = new ArtworkRetryWakeup(scheduler);
        wakeup.arm(Long.MAX_VALUE, () -> true, () -> fail()); assertTrue(scheduler.tasks.isEmpty());
    }
}
