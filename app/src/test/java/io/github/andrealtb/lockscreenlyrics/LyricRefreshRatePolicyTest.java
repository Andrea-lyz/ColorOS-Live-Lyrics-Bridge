package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LyricRefreshRatePolicyTest {
    private static final long VSYNC_120_HZ_NANOS = 8_333_333L;

    @Test
    public void followScreenRendersEveryVsync() {
        assertEquals(120, renderedFramesAt120Hz(0, 120));
    }

    @Test
    public void fixedCapsUseDeadlinePacingInsteadOfMillisecondDelayRounding() {
        assertEquals(60, renderedFramesAt120Hz(60, 120));
        assertEquals(90, renderedFramesAt120Hz(90, 120));
        assertEquals(120, renderedFramesAt120Hz(120, 120));
    }

    @Test
    public void capDoesNotInventFramesWhenDisplayRunsSlower() {
        long deadline = LyricRefreshRatePolicy.UNSET_DEADLINE_NANOS;
        int rendered = 0;
        for (int frame = 0; frame < 60; frame++) {
            long now = frame * 16_666_667L;
            if (!LyricRefreshRatePolicy.isFrameDue(now, deadline, 120)) continue;
            rendered++;
            deadline = LyricRefreshRatePolicy.advanceDeadline(now, deadline, 120);
        }
        assertEquals(60, rendered);
    }

    private static int renderedFramesAt120Hz(int capHz, int vsyncCount) {
        long deadline = LyricRefreshRatePolicy.UNSET_DEADLINE_NANOS;
        int rendered = 0;
        for (int frame = 0; frame < vsyncCount; frame++) {
            long now = frame * VSYNC_120_HZ_NANOS;
            if (!LyricRefreshRatePolicy.isFrameDue(now, deadline, capHz)) continue;
            rendered++;
            deadline = LyricRefreshRatePolicy.advanceDeadline(now, deadline, capHz);
        }
        return rendered;
    }
}
