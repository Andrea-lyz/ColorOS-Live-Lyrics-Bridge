package io.github.andrealtb.lockscreenlyrics;

import java.util.List;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;

/** Guards supplemental translation matching from consuming a nearby primary lyric line. */
final class SupplementalTranslationPolicy {
    private SupplementalTranslationPolicy() {
    }

    static boolean matchesNearbyPrimaryLine(
            List<WordLine> primaryLines,
            WordLine currentLine,
            String candidateText,
            long maxDistanceMillis) {
        if (primaryLines == null || primaryLines.isEmpty() || currentLine == null) {
            return false;
        }
        String normalizedCandidate = WordLyricRenderSupport.normalizeLine(candidateText);
        if (normalizedCandidate.isEmpty()) {
            return false;
        }
        long maxDistance = Math.max(0L, maxDistanceMillis);
        for (WordLine primaryLine : primaryLines) {
            if (primaryLine == null || primaryLine == currentLine) {
                continue;
            }
            if (Math.abs(primaryLine.timeMillis - currentLine.timeMillis) > maxDistance) {
                continue;
            }
            if (normalizedCandidate.equals(
                    WordLyricRenderSupport.normalizeLine(primaryLine.text))) {
                return true;
            }
        }
        return false;
    }

    static boolean isNearestPrimaryLineForTimestamp(
            List<WordLine> primaryLines,
            WordLine currentLine,
            long supplementalTimeMillis,
            long maxDistanceMillis) {
        if (primaryLines == null || primaryLines.isEmpty() || currentLine == null) {
            return false;
        }
        long maxDistance = Math.max(0L, maxDistanceMillis);
        long currentDistance = Math.abs(currentLine.timeMillis - supplementalTimeMillis);
        if (currentDistance > maxDistance) {
            return false;
        }
        for (WordLine primaryLine : primaryLines) {
            if (primaryLine == null || primaryLine == currentLine) {
                continue;
            }
            long distance = Math.abs(primaryLine.timeMillis - supplementalTimeMillis);
            if (distance < currentDistance) {
                return false;
            }
        }
        return true;
    }
}
