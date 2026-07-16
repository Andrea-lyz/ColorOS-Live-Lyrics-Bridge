package io.github.andrealtb.lockscreenlyrics;

final class LyricRefreshRatePolicy {
    static final long UNSET_DEADLINE_NANOS = Long.MIN_VALUE;
    private static final long VSYNC_TIMESTAMP_TOLERANCE_NANOS = 500_000L;

    private LyricRefreshRatePolicy() {
    }

    static long framePeriodNanos(int maxRefreshRateHz) {
        if (maxRefreshRateHz <= 0) return 0L;
        return Math.max(1L, Math.round(1_000_000_000d / maxRefreshRateHz));
    }

    static boolean isFrameDue(
            long frameTimeNanos,
            long nextDeadlineNanos,
            int maxRefreshRateHz) {
        return maxRefreshRateHz <= 0
                || nextDeadlineNanos == UNSET_DEADLINE_NANOS
                || frameTimeNanos + VSYNC_TIMESTAMP_TOLERANCE_NANOS >= nextDeadlineNanos;
    }

    static long advanceDeadline(
            long frameTimeNanos,
            long previousDeadlineNanos,
            int maxRefreshRateHz) {
        long periodNanos = framePeriodNanos(maxRefreshRateHz);
        if (periodNanos <= 0L) return UNSET_DEADLINE_NANOS;
        long deadline = previousDeadlineNanos == UNSET_DEADLINE_NANOS
                ? frameTimeNanos + periodNanos
                : previousDeadlineNanos + periodNanos;
        while (deadline <= frameTimeNanos) {
            deadline += periodNanos;
        }
        return deadline;
    }
}
