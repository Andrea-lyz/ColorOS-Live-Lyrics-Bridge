package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public final class NativeLyricClockPolicyTest {
    private static final long BAND = 600L;

    @Test
    public void controllerUsesModuleClockOnlyInsideTheBand() {
        assertEquals(10_000L, NativeLyricClockPolicy.controllerPosition(10_200L, 10_000L, BAND));
        assertEquals(10_000L, NativeLyricClockPolicy.controllerPosition(9_800L, 10_000L, BAND));
        // Seeks, restarts and unrelated sessions keep the vendor value.
        assertEquals(15_000L, NativeLyricClockPolicy.controllerPosition(15_000L, 10_000L, BAND));
        assertEquals(2_000L, NativeLyricClockPolicy.controllerPosition(2_000L, 10_000L, BAND));
        assertEquals(10_200L, NativeLyricClockPolicy.controllerPosition(10_200L, -1L, BAND));
        assertEquals(-1L, NativeLyricClockPolicy.controllerPosition(-1L, 10_000L, BAND));
    }

    @Test
    public void timedCallHoldsSubBandStepsBackButFollowsSeeksAndProgress() {
        assertEquals(10_000L, NativeLyricClockPolicy.timedLyricPosition(9_800L, 10_000L, BAND));
        assertEquals(10_300L, NativeLyricClockPolicy.timedLyricPosition(10_300L, 10_000L, BAND));
        // A boundary-timer value just ahead of the held clock passes unchanged.
        assertEquals(12_250L, NativeLyricClockPolicy.timedLyricPosition(12_250L, 12_249L, BAND));
        assertEquals(5_000L, NativeLyricClockPolicy.timedLyricPosition(5_000L, 10_000L, BAND));
        assertEquals(9_800L, NativeLyricClockPolicy.timedLyricPosition(9_800L, -1L, BAND));
    }

    @Test
    public void heldPositionAdvancesWithPlaybackSpeed() {
        assertEquals(11_000L, NativeLyricClockPolicy.heldPosition(10_000L, 500L, 1_500L, 1f));
        assertEquals(10_500L, NativeLyricClockPolicy.heldPosition(10_000L, 500L, 1_500L, 0.5f));
        assertEquals(10_000L, NativeLyricClockPolicy.heldPosition(10_000L, 500L, 1_500L, 0f));
        assertEquals(-1L, NativeLyricClockPolicy.heldPosition(-1L, 500L, 1_500L, 1f));
        assertEquals(-1L, NativeLyricClockPolicy.heldPosition(10_000L, 2_000L, 1_500L, 1f));
    }

    @Test
    public void unmodifiedVendorLoopFlipsBackOnCoarseAnchors() {
        VendorLyricListSimulation stock = VendorLyricListSimulation.run(false, false, 1f);
        assertTrue("the simulation must reproduce the flip-back", stock.flipBacks() > 0);
    }

    @Test
    public void guardAloneRemovesFlipBacksWithoutDelayingRowChanges() {
        VendorLyricListSimulation stock = VendorLyricListSimulation.run(false, false, 1f);
        VendorLyricListSimulation guarded = VendorLyricListSimulation.run(true, false, 1f);
        assertEquals(0, guarded.flipBacks());
        for (int line = 1; line < VendorLyricListSimulation.LINE_COUNT; line++) {
            long stockAt = stock.firstReached(line);
            if (stockAt >= 0L) {
                long guardedAt = guarded.firstReached(line);
                assertTrue("line " + line + " reached late", guardedAt >= 0L && guardedAt <= stockAt);
            }
        }
    }

    @Test
    public void alignedControllerChangesRowsWhenTheModuleClockReachesThem() {
        VendorLyricListSimulation aligned = VendorLyricListSimulation.run(true, true, 1f);
        assertEquals(0, aligned.flipBacks());
        assertTrue(aligned.changes.size() > 10);
        for (long[] change : aligned.changes) {
            long lateness = aligned.moduleClock(change[0])
                    - VendorLyricListSimulation.lineStart((int) change[1]);
            assertTrue("row " + change[1] + " changed early by " + -lateness, lateness >= 0L);
            // One anchor interval (1003 ms) exceeds the vendor's 1 s timer horizon by 3 ms.
            assertTrue("row " + change[1] + " changed late by " + lateness, lateness <= 5L);
        }
    }

    @Test
    public void slowPlaybackKeepsTheVendorBoundaryTimerOnTime() {
        // The vendor posts its boundary timer with the media-time delay, so below 1x it fires
        // before the clock reaches the row. The timed call must keep that exact row time.
        VendorLyricListSimulation stock = VendorLyricListSimulation.run(false, false, 0.75f);
        VendorLyricListSimulation aligned = VendorLyricListSimulation.run(true, true, 0.75f);
        assertTrue(stock.flipBacks() > 0);
        assertEquals(0, aligned.flipBacks());
        assertTrue(aligned.changes.size() > 10);
        for (long[] change : aligned.changes) {
            long lateness = aligned.moduleClock(change[0])
                    - VendorLyricListSimulation.lineStart((int) change[1]);
            assertTrue("row " + change[1] + " changed late by " + lateness, lateness <= 5L);
        }
    }

    /**
     * The vendor immersive loop (SystemUIPlugin 16.001.002 {@code u(Long, boolean)} and
     * {@code LyricsRecyclerView} timed method): each position update selects the row by binary
     * search and posts a timer for the next row when it is under one second away. Anchors model a
     * player that republishes 400 ms position blocks every 1003 ms; the module clock applies the
     * existing same-state small-correction hold.
     */
    private static final class VendorLyricListSimulation {
        static final int LINE_COUNT = 24;
        static final long LINE_SPACING_MS = 2_450L;
        private static final long FIRST_ANCHOR_AT = 100_000L;
        private static final long ANCHOR_INTERVAL_MS = 1_003L;
        private static final long BLOCK_MS = 400L;
        private static final long DECODER_START_MS = 137L;

        final List<long[]> changes = new ArrayList<>();
        private final boolean policy;
        private final boolean aligned;
        private final float speed;
        private long moduleBase = -1L;
        private long moduleAt = -1L;
        private int index = -1;
        private long guardPosition = -1L;
        private long guardAt = -1L;
        private long timerAt = -1L;
        private long timerPosition = -1L;

        private VendorLyricListSimulation(boolean policy, boolean aligned, float speed) {
            this.policy = policy;
            this.aligned = aligned;
            this.speed = speed;
        }

        static VendorLyricListSimulation run(boolean policy, boolean aligned, float speed) {
            VendorLyricListSimulation sim = new VendorLyricListSimulation(policy, aligned, speed);
            for (long anchorAt = FIRST_ANCHOR_AT; ; anchorAt += ANCHOR_INTERVAL_MS) {
                long decoder = DECODER_START_MS + (long) ((anchorAt - FIRST_ANCHOR_AT) * speed);
                if (decoder >= lineStart(LINE_COUNT - 1)) {
                    return sim;
                }
                while (sim.timerAt >= 0L && sim.timerAt <= anchorAt) {
                    long fireAt = sim.timerAt;
                    sim.timerAt = -1L;
                    sim.timedCall(fireAt, sim.timerPosition);
                }
                long anchor = decoder / BLOCK_MS * BLOCK_MS;
                sim.observeModuleAnchor(anchorAt, anchor);
                sim.controllerUpdate(anchorAt, anchor);
            }
        }

        static long lineStart(int line) {
            return line * LINE_SPACING_MS;
        }

        long moduleClock(long now) {
            return moduleBase < 0L ? -1L : moduleBase + (long) ((now - moduleAt) * speed);
        }

        int flipBacks() {
            int count = 0;
            for (int i = 1; i < changes.size(); i++) {
                if (changes.get(i)[1] < changes.get(i - 1)[1]) count++;
            }
            return count;
        }

        long firstReached(int line) {
            for (long[] change : changes) {
                if (change[1] >= line) return change[0];
            }
            return -1L;
        }

        private void observeModuleAnchor(long now, long anchor) {
            long previous = moduleClock(now);
            long accepted = LockscreenIntegrationPolicy.smoothPlayingPosition(
                    previous < 0L ? -1 : 3, 3, previous, anchor, BAND);
            moduleBase = accepted;
            moduleAt = now;
        }

        private void controllerUpdate(long now, long vendorPosition) {
            long position = policy && aligned
                    ? NativeLyricClockPolicy.controllerPosition(vendorPosition, moduleClock(now), BAND)
                    : vendorPosition;
            timedCall(now, position);
            timerAt = -1L;
            long next = nextLineStartAfter(position);
            long delay = next - position;
            if (next >= 0L && delay >= 1L && delay < 1_000L) {
                timerAt = now + delay;
                timerPosition = next;
            }
        }

        private void timedCall(long now, long vendorPosition) {
            long position = vendorPosition;
            if (policy) {
                long held = NativeLyricClockPolicy.heldPosition(guardPosition, guardAt, now, speed);
                position = NativeLyricClockPolicy.timedLyricPosition(vendorPosition, held, BAND);
                guardPosition = position;
                guardAt = now;
            }
            int row = lineAt(position);
            if (row >= 0 && row != index) {
                changes.add(new long[] {now, row});
                index = row;
            }
        }

        private static int lineAt(long position) {
            int row = -1;
            for (int line = 0; line < LINE_COUNT; line++) {
                if (lineStart(line) <= position) row = line;
            }
            return row;
        }

        private static long nextLineStartAfter(long position) {
            for (int line = 0; line < LINE_COUNT; line++) {
                if (lineStart(line) > position) return lineStart(line);
            }
            return -1L;
        }
    }
}
