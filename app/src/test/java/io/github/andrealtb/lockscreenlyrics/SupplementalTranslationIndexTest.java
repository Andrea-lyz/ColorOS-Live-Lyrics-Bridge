package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;

/**
 * Differential fuzz pinning the indexed supplemental-translation checks to
 * the historical full-list {@link SupplementalTranslationPolicy} scans.
 * Phase 6 slice 5.
 */
public final class SupplementalTranslationIndexTest {
    @Test
    public void indexMatchesPolicyAcrossRandomizedModels() {
        Random random = new Random(20260829L);
        String[] texts = {
                "Alpha line",
                "Beta 歌词",
                "Gamma",
                "Delta 翻译",
                "Alpha line",
                "Beta 歌词",
        };
        for (int iteration = 0; iteration < 400; iteration++) {
            int size = 1 + random.nextInt(24);
            List<WordLine> lines = new ArrayList<>();
            long time = random.nextInt(400);
            for (int i = 0; i < size; i++) {
                time += random.nextInt(500);
                lines.add(new WordLine(time, texts[random.nextInt(texts.length)], null));
            }
            SupplementalTranslationIndex index = new SupplementalTranslationIndex(lines);
            for (WordLine current : lines) {
                long supplementalTime = current.timeMillis - 300L + random.nextInt(600);
                assertEquals(
                        SupplementalTranslationPolicy.isNearestPrimaryLineForTimestamp(
                                lines, current, supplementalTime, 120L),
                        index.isNearestPrimaryLineForTimestamp(current, supplementalTime, 120L));
                for (String candidateText : texts) {
                    assertEquals(
                            SupplementalTranslationPolicy.matchesNearbyPrimaryLine(
                                    lines, current, candidateText, 120L),
                            index.matchesNearbyPrimaryLine(current, candidateText, 120L));
                }
            }
        }
    }

    @Test
    public void nearestCheckHonorsDistanceTiesAndWindow() {
        List<WordLine> lines = new ArrayList<>();
        WordLine first = new WordLine(1_000L, "First", null);
        WordLine second = new WordLine(1_060L, "Second", null);
        lines.add(first);
        lines.add(second);
        SupplementalTranslationIndex index = new SupplementalTranslationIndex(lines);

        // Equidistant tie: neither line is strictly closer, both stay nearest.
        assertEquals(true, index.isNearestPrimaryLineForTimestamp(first, 1_030L, 120L));
        assertEquals(true, index.isNearestPrimaryLineForTimestamp(second, 1_030L, 120L));
        // The other line is strictly closer to 1_055.
        assertEquals(false, index.isNearestPrimaryLineForTimestamp(first, 1_055L, 120L));
        assertEquals(true, index.isNearestPrimaryLineForTimestamp(second, 1_055L, 120L));
        // Outside the max distance window.
        assertEquals(false, index.isNearestPrimaryLineForTimestamp(first, 1_500L, 120L));
    }

    @Test
    public void nearbyTextCheckUsesTimeWindowBuckets() {
        List<WordLine> lines = new ArrayList<>();
        WordLine current = new WordLine(2_000L, "Repeated elsewhere", null);
        lines.add(new WordLine(1_000L, "Primary", null));
        lines.add(current);
        lines.add(new WordLine(2_080L, "Repeated elsewhere", null));
        lines.add(new WordLine(9_000L, "Repeated elsewhere", null));
        SupplementalTranslationIndex index = new SupplementalTranslationIndex(lines);

        assertEquals(true, index.matchesNearbyPrimaryLine(current, "Repeated elsewhere", 120L));
        assertEquals(false, index.matchesNearbyPrimaryLine(current, "Primary", 120L));
        assertEquals(false, index.matchesNearbyPrimaryLine(current, "", 120L));
        // Only the far duplicate remains when the window shrinks.
        SupplementalTranslationIndex narrow =
                new SupplementalTranslationIndex(java.util.Arrays.asList(
                        current, new WordLine(9_000L, "Repeated elsewhere", null)));
        assertEquals(false, narrow.matchesNearbyPrimaryLine(current, "Repeated elsewhere", 120L));
    }

    @Test
    public void lowerBoundFindsFirstAtOrAfter() {
        long[] times = {1L, 3L, 3L, 7L};
        assertEquals(0, SupplementalTranslationIndex.lowerBound(times, 0L));
        assertEquals(0, SupplementalTranslationIndex.lowerBound(times, 1L));
        assertEquals(1, SupplementalTranslationIndex.lowerBound(times, 3L));
        assertEquals(3, SupplementalTranslationIndex.lowerBound(times, 4L));
        assertEquals(4, SupplementalTranslationIndex.lowerBound(times, 8L));
    }
}
