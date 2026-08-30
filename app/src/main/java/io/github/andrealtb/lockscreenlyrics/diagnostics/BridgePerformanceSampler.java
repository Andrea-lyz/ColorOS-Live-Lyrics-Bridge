package io.github.andrealtb.lockscreenlyrics.diagnostics;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Low-overhead aggregate timing for Bridge hot paths.
 *
 * <p>When the performance debug area is disabled, {@link #begin()} performs only the area-gate
 * check and returns zero. Enabled samples are accumulated in fixed arrays and emitted as one
 * structured summary per reporting window; no per-frame log line or collection allocation is
 * produced.</p>
 */
public final class BridgePerformanceSampler {
    static final long DEFAULT_REPORT_INTERVAL_NANOS = 5_000_000_000L;
    static final long NO_SAMPLE = Long.MIN_VALUE;

    public enum Metric {
        TEXT_VIEW_DRAW("text-view-draw"),
        FRAME_RESOLVE("frame-resolve"),
        RENDERER_DRAW("renderer-draw"),
        ACTIVE_REFRESH("active-refresh"),
        MODEL_PARSE("model-parse");

        final String key;

        Metric(String key) {
            this.key = key;
        }
    }

    private final BooleanSupplier enabled;
    private final LongSupplier nanoClock;
    private final Consumer<String> reportSink;
    private final long reportIntervalNanos;
    private final long[] counts = new long[Metric.values().length];
    private final long[] totalNanos = new long[Metric.values().length];
    private final long[] maxNanos = new long[Metric.values().length];
    private long windowStartedAtNanos;

    public BridgePerformanceSampler() {
        this(
                () -> StructuredBridgeLog.isAreaEnabled(BridgeDebugArea.PERFORMANCE),
                System::nanoTime,
                message -> StructuredBridgeLog.info(
                        BridgeDebugArea.PERFORMANCE,
                        BridgeEvents.PERF_SAMPLE,
                        message),
                DEFAULT_REPORT_INTERVAL_NANOS);
    }

    BridgePerformanceSampler(
            BooleanSupplier enabled,
            LongSupplier nanoClock,
            Consumer<String> reportSink,
            long reportIntervalNanos) {
        this.enabled = enabled;
        this.nanoClock = nanoClock;
        this.reportSink = reportSink;
        this.reportIntervalNanos = Math.max(1L, reportIntervalNanos);
    }

    public long begin() {
        return enabled.getAsBoolean() ? nanoClock.getAsLong() : NO_SAMPLE;
    }

    public void end(Metric metric, long startedAtNanos) {
        if (metric == null || startedAtNanos == NO_SAMPLE) {
            return;
        }
        long endedAtNanos = nanoClock.getAsLong();
        long elapsedNanos = Math.max(0L, endedAtNanos - startedAtNanos);
        String report = record(metric, elapsedNanos, endedAtNanos);
        if (report != null && !report.isEmpty()) {
            reportSink.accept(report);
        }
    }

    private synchronized String record(Metric metric, long elapsedNanos, long nowNanos) {
        if (windowStartedAtNanos == 0L) {
            windowStartedAtNanos = nowNanos;
        }
        int index = metric.ordinal();
        counts[index]++;
        totalNanos[index] += elapsedNanos;
        maxNanos[index] = Math.max(maxNanos[index], elapsedNanos);
        if (nowNanos - windowStartedAtNanos < reportIntervalNanos) {
            return null;
        }

        String report = buildReport(nowNanos - windowStartedAtNanos);
        clear(nowNanos);
        return report;
    }

    private String buildReport(long windowNanos) {
        StringBuilder report = new StringBuilder(256);
        report.append("Bridge performance aggregate, windowMs=")
                .append(nanosToMillis(windowNanos));
        Metric[] metrics = Metric.values();
        for (int index = 0; index < metrics.length; index++) {
            long count = counts[index];
            if (count <= 0L) {
                continue;
            }
            report.append(", ")
                    .append(metrics[index].key)
                    .append("={count=")
                    .append(count)
                    .append(",avgUs=")
                    .append(nanosToMicros(totalNanos[index] / count))
                    .append(",maxUs=")
                    .append(nanosToMicros(maxNanos[index]))
                    .append('}');
        }
        return report.toString();
    }

    private void clear(long nextWindowStartedAtNanos) {
        for (int index = 0; index < counts.length; index++) {
            counts[index] = 0L;
            totalNanos[index] = 0L;
            maxNanos[index] = 0L;
        }
        windowStartedAtNanos = nextWindowStartedAtNanos;
    }

    private static long nanosToMicros(long nanos) {
        return nanos / 1_000L;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
