package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

public class ArtworkScreenAwakeTest {
    /** Manual clock: tasks run only when the test advances time. */
    private static final class Clock implements ArtworkRetryWakeup.Scheduler {
        private record Task(Runnable task, long at) {}
        final List<Task> tasks = new ArrayList<>();
        long now;
        public void post(Runnable task, long delayMs) { tasks.add(new Task(task, now + delayMs)); }
        public void remove(Runnable task) { tasks.removeIf(entry -> entry.task() == task); }
        void advance(long ms) {
            long end = now + ms;
            while (true) {
                Task next = null;
                for (Task entry : tasks) if (entry.at() <= end && (next == null || entry.at() < next.at())) next = entry;
                if (next == null) break;
                tasks.remove(next);
                now = next.at();
                next.task().run();
            }
            now = end;
        }
    }
    private static final class Power implements ArtworkScreenAwake.Power {
        boolean held;
        int holds, releases, pulses;
        long lease;
        public void hold(long leaseMs) { held = true; holds++; lease = leaseMs; }
        public void release() { held = false; releases++; }
        public void pulse() { pulses++; }
    }
    private final Clock clock = new Clock();
    private final Power power = new Power();
    private final List<String> trace = new ArrayList<>();
    private String state = "no_video";
    private final ArtworkScreenAwake awake = new ArtworkScreenAwake(power, clock, () -> state, trace::add);

    @Test public void staysOffUntilEnabledAndTheLargeCoverIsActuallyPlaying() {
        state = ArtworkScreenAwake.PLAYING;
        awake.update();
        assertFalse(power.held);
        awake.setEnabled(true);
        assertTrue(power.held);
        assertEquals(ArtworkScreenAwake.LEASE_MS, power.lease);
        assertEquals(1, power.pulses);
    }

    @Test public void leaseAndUserActivityAreRenewedWhilePlaying() {
        awake.setEnabled(true);
        state = ArtworkScreenAwake.PLAYING;
        awake.update();
        clock.advance(ArtworkScreenAwake.RENEW_MS * 3);
        assertEquals(4, power.holds);
        assertEquals(4, power.pulses);
        assertTrue(power.held);
        // Repeated observations while held do not stack locks or timers.
        for (int i = 0; i < 10; i++) awake.update();
        assertEquals(4, power.holds);
        assertEquals(1, clock.tasks.size());
    }

    @Test public void pauseReleasesAfterTheGraceAndNamesWhy() {
        awake.setEnabled(true);
        state = ArtworkScreenAwake.PLAYING;
        awake.update();
        state = "paused";
        awake.update();
        assertTrue(power.held);
        clock.advance(ArtworkScreenAwake.RELEASE_GRACE_MS);
        assertFalse(power.held);
        assertEquals("state=released reason=paused", trace.get(trace.size() - 1));
        clock.advance(60_000);
        assertEquals(1, power.holds);
        assertTrue(clock.tasks.isEmpty());
    }

    @Test public void aOneFrameGapDoesNotFlapTheLock() {
        awake.setEnabled(true);
        state = ArtworkScreenAwake.PLAYING;
        awake.update();
        state = "geometry_transition";
        awake.update();
        clock.advance(100);
        state = ArtworkScreenAwake.PLAYING;
        awake.update();
        clock.advance(ArtworkScreenAwake.RELEASE_GRACE_MS * 2);
        assertTrue(power.held);
        assertEquals(0, power.releases);
    }

    @Test public void playingAgainWithoutAnObservationStillCancelsTheRelease() {
        awake.setEnabled(true);
        state = ArtworkScreenAwake.PLAYING;
        awake.update();
        state = "surface_switch";
        awake.update();
        state = ArtworkScreenAwake.PLAYING;
        clock.advance(ArtworkScreenAwake.RELEASE_GRACE_MS + ArtworkScreenAwake.RENEW_MS);
        assertTrue(power.held);
        assertEquals(0, power.releases);
    }

    @Test public void renewalNoticesAStopNobodyReported() {
        awake.setEnabled(true);
        state = ArtworkScreenAwake.PLAYING;
        awake.update();
        state = "no_video";
        clock.advance(ArtworkScreenAwake.RENEW_MS + ArtworkScreenAwake.RELEASE_GRACE_MS);
        assertFalse(power.held);
    }

    @Test public void screenOffDisableAndCloseReleaseAtOnce() {
        awake.setEnabled(true);
        state = ArtworkScreenAwake.PLAYING;
        awake.update();
        awake.releaseNow("screen_off");
        assertFalse(power.held);
        assertEquals("state=released reason=screen_off", trace.get(trace.size() - 1));
        assertTrue(clock.tasks.isEmpty());

        awake.update();
        assertTrue(power.held);
        awake.setEnabled(false);
        assertFalse(power.held);
        awake.update();
        assertFalse(power.held);

        awake.setEnabled(true);
        assertTrue(power.held);
        awake.close();
        assertFalse(power.held);
        awake.setEnabled(true);
        awake.update();
        assertFalse(power.held);
        assertTrue(clock.tasks.isEmpty());
    }
}
