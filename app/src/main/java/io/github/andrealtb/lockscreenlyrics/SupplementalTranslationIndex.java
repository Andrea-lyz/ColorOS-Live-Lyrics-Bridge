package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;

/**
 * Time/text index over the primary word model used while merging
 * supplemental translations. Replaces the per-candidate full-list scans of
 * {@link SupplementalTranslationPolicy} with binary-searched time windows
 * and normalized-text buckets so the merge is O(N log N + window) instead
 * of O(N * M * N). Decisions are behavior-equivalent to the policy; the
 * differential tests pin that equivalence. Phase 6 slice 5.
 */
final class SupplementalTranslationIndex {
    private final List<WordLine> lines;
    private final long[] times;
    private final HashMap<String, ArrayList<WordLine>> textBuckets = new HashMap<>();

    SupplementalTranslationIndex(List<WordLine> primaryLines) {
        this.lines = primaryLines == null ? new ArrayList<>() : primaryLines;
        this.times = new long[this.lines.size()];
        for (int i = 0; i < this.lines.size(); i++) {
            WordLine line = this.lines.get(i);
            this.times[i] = line == null ? 0L : line.timeMillis;
            if (line != null) {
                String key = WordLyricRenderSupport.normalizeLine(line.text);
                ArrayList<WordLine> bucket = textBuckets.get(key);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    textBuckets.put(key, bucket);
                }
                bucket.add(line);
            }
        }
    }

    boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Indexed form of
     * {@link SupplementalTranslationPolicy#isNearestPrimaryLineForTimestamp}.
     * True when no other primary line sits strictly closer to
     * {@code supplementalTimeMillis} than {@code currentLine}.
     */
    boolean isNearestPrimaryLineForTimestamp(
            WordLine currentLine,
            long supplementalTimeMillis,
            long maxDistanceMillis) {
        if (lines.isEmpty() || currentLine == null) {
            return false;
        }
        long maxDistance = Math.max(0L, maxDistanceMillis);
        long currentDistance = Math.abs(currentLine.timeMillis - supplementalTimeMillis);
        if (currentDistance > maxDistance) {
            return false;
        }
        int insertion = lowerBound(times, supplementalTimeMillis);
        for (int i = insertion - 1; i >= 0; i--) {
            long distance = supplementalTimeMillis - times[i];
            if (distance >= currentDistance) {
                break;
            }
            WordLine line = lines.get(i);
            if (line != null && line != currentLine) {
                return false;
            }
        }
        for (int i = insertion; i < times.length; i++) {
            long distance = times[i] - supplementalTimeMillis;
            if (distance >= currentDistance) {
                break;
            }
            WordLine line = lines.get(i);
            if (line != null && line != currentLine) {
                return false;
            }
        }
        return true;
    }

    /**
     * Indexed form of
     * {@link SupplementalTranslationPolicy#matchesNearbyPrimaryLine}: true
     * when another primary line within {@code maxDistanceMillis} of
     * {@code currentLine} already renders the candidate text.
     */
    boolean matchesNearbyPrimaryLine(
            WordLine currentLine,
            String candidateText,
            long maxDistanceMillis) {
        if (lines.isEmpty() || currentLine == null) {
            return false;
        }
        String normalizedCandidate = WordLyricRenderSupport.normalizeLine(candidateText);
        if (normalizedCandidate.isEmpty()) {
            return false;
        }
        ArrayList<WordLine> bucket = textBuckets.get(normalizedCandidate);
        if (bucket == null) {
            return false;
        }
        long maxDistance = Math.max(0L, maxDistanceMillis);
        for (WordLine line : bucket) {
            if (line == null || line == currentLine) {
                continue;
            }
            if (Math.abs(line.timeMillis - currentLine.timeMillis) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    /** First index whose time is >= {@code value}; requires sorted times. */
    static int lowerBound(long[] times, long value) {
        int low = 0;
        int high = times.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (times[mid] < value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
