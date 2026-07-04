package io.github.andrealtb.lockscreenlyrics;

/** Shared heuristics for deciding when word-level timing should be downgraded to line timing. */
final class LyricTimingRepair {
    private static final long SUSPICIOUS_INLINE_GAP_MS = 8_000L;

    private LyricTimingRepair() {
    }

    static boolean shouldDowngradeWordTiming(
            int timedSegmentCount,
            long firstSegmentStartMillis,
            long lastSegmentStartMillis,
            long maxAdjacentStartGapMillis,
            boolean strictlyIncreasing) {
        if (timedSegmentCount < 2) {
            return false;
        }
        if (!strictlyIncreasing) {
            return true;
        }
        return hasSuspiciousInlineTimingGap(
                timedSegmentCount,
                firstSegmentStartMillis,
                lastSegmentStartMillis,
                maxAdjacentStartGapMillis);
    }

    static boolean hasSuspiciousInlineTimingGap(
            int timedSegmentCount,
            long firstSegmentStartMillis,
            long lastSegmentStartMillis,
            long maxAdjacentStartGapMillis) {
        if (timedSegmentCount < 2
                || firstSegmentStartMillis < 0L
                || lastSegmentStartMillis <= firstSegmentStartMillis
                || maxAdjacentStartGapMillis <= 0L) {
            return false;
        }
        long span = lastSegmentStartMillis - firstSegmentStartMillis;
        return maxAdjacentStartGapMillis >= SUSPICIOUS_INLINE_GAP_MS
                && (timedSegmentCount <= 4
                || maxAdjacentStartGapMillis * 3L >= span * 2L);
    }
}
