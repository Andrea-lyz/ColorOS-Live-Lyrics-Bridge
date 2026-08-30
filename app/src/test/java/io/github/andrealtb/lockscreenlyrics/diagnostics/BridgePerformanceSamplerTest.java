package io.github.andrealtb.lockscreenlyrics.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;

public final class BridgePerformanceSamplerTest {
    @Test
    public void disabledSamplerDoesNotReadClockOrEmit() {
        int[] clockReads = {0};
        ArrayList<String> reports = new ArrayList<>();
        BridgePerformanceSampler sampler = new BridgePerformanceSampler(
                () -> false,
                () -> {
                    clockReads[0]++;
                    return 10L;
                },
                reports::add,
                100L);

        long startedAt = sampler.begin();
        sampler.end(BridgePerformanceSampler.Metric.TEXT_VIEW_DRAW, startedAt);

        assertEquals(BridgePerformanceSampler.NO_SAMPLE, startedAt);
        assertEquals(0, clockReads[0]);
        assertTrue(reports.isEmpty());
    }

    @Test
    public void enabledSamplerAggregatesOneReportPerWindow() {
        long[] now = {1_000L};
        ArrayList<String> reports = new ArrayList<>();
        BridgePerformanceSampler sampler = new BridgePerformanceSampler(
                () -> true,
                () -> now[0],
                reports::add,
                60L);

        long drawStart = sampler.begin();
        now[0] += 40L;
        sampler.end(BridgePerformanceSampler.Metric.TEXT_VIEW_DRAW, drawStart);

        long frameStart = sampler.begin();
        now[0] += 70L;
        sampler.end(BridgePerformanceSampler.Metric.FRAME_RESOLVE, frameStart);

        assertEquals(1, reports.size());
        String report = reports.get(0);
        assertTrue(report.contains("text-view-draw={count=1,avgUs=0,maxUs=0}"));
        assertTrue(report.contains("frame-resolve={count=1,avgUs=0,maxUs=0}"));
    }

    @Test
    public void enabledSamplerReportsAverageAndMaximumMicros() {
        long[] now = {1_000_000L};
        ArrayList<String> reports = new ArrayList<>();
        BridgePerformanceSampler sampler = new BridgePerformanceSampler(
                () -> true,
                () -> now[0],
                reports::add,
                2_000_000L);

        long first = sampler.begin();
        now[0] += 1_000_000L;
        sampler.end(BridgePerformanceSampler.Metric.MODEL_PARSE, first);

        long second = sampler.begin();
        now[0] += 2_000_000L;
        sampler.end(BridgePerformanceSampler.Metric.MODEL_PARSE, second);

        assertEquals(1, reports.size());
        assertTrue(reports.get(0).contains(
                "model-parse={count=2,avgUs=1500,maxUs=2000}"));
    }
}
