package io.github.andrealtb.lockscreenlyrics.render;

import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class LineTimedRevealPolicyTest {
    private static WordLine line(long start, long end) {
        return new WordLine(start, "ordinary line", new ArrayList<>(), end, LyricTimingMode.LINE_TIMED);
    }

    @Test public void precedingLineFinishesBeforeNextLineBegins() {
        assertEquals(4880, LineTimedRevealPolicy.revealEnd(1000, 5000));
        assertEquals(1f, LineTimedRevealPolicy.progress(1000, 5000, 4900), 0f);
        assertEquals(1f, LineTimedRevealPolicy.progress(1000, 5000, 5000), 0f);
        assertEquals(0f, LineTimedRevealPolicy.progress(5000, 9000, 4999), 0f);
        assertEquals(0f, LineTimedRevealPolicy.progress(5000, 9000, 5000), 0f);
    }

    @Test public void shortLinesKeepAtLeastNinetyPercentOfTheirSweepTime() {
        assertEquals(1180, LineTimedRevealPolicy.revealEnd(1000, 1200));
        assertEquals(1001, LineTimedRevealPolicy.revealEnd(1000, 1001));
        assertEquals(1001, LineTimedRevealPolicy.revealEnd(1000, 1000));
    }

    @Test public void repeatedFramesAndForwardPlaybackNeverRewindButSeekStillWorks() {
        float previous = 0;
        for (int p = 0; p <= 5100; p += 16) {
            float progress = LineTimedRevealPolicy.progress(1000, 5000, p);
            assertTrue(progress >= previous);
            assertEquals(progress, LineTimedRevealPolicy.progress(1000, 5000, p), 0);
            previous = progress;
        }
        assertEquals(0f, LineTimedRevealPolicy.progress(1000, 5000, 900), 0f);
    }

    @Test public void earlyOfficialSelectionCannotActivateFutureRowOrDropCurrentRow() {
        WordLyricModel model = new WordLyricModel();
        WordLine first = line(1000, 5000), next = line(5000, 9000);
        model.lines.add(first); model.lines.add(next);
        assertFalse(LineTimedRevealPolicy.active(model, first, 999));
        assertTrue(LineTimedRevealPolicy.active(model, first, 4900));
        assertFalse(LineTimedRevealPolicy.active(model, next, 4900));
        assertFalse(LineTimedRevealPolicy.active(model, first, 5000));
        assertTrue(LineTimedRevealPolicy.active(model, next, 5000));
        assertTrue(LineTimedRevealPolicy.active(model, first, 2000)); // real backward seek
    }

    @Test public void wrappedRowsShareOneContinuousFrontIndependentOfWordRanges() {
        assertEquals(100f, LineTimedRevealPolicy.segmentReveal(200, 0, 100, .75f), 0f);
        assertEquals(50f, LineTimedRevealPolicy.segmentReveal(200, 100, 100, .75f), 0f);
        assertEquals(0f, LineTimedRevealPolicy.segmentReveal(200, 100, 100, .25f), 0f);
        assertEquals(100f, LineTimedRevealPolicy.segmentReveal(200, 100, 100, 1f), 0f);
    }
}
