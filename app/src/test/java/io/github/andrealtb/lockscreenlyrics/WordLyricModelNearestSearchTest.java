package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;

/**
 * Differential fuzz proving the Phase 6 slice 5 time-ordered outward walk
 * in {@link WordLyricModel#findLineByText(String, long)} and
 * {@link WordLyricModel#findLineByTranslation(String, long)} returns exactly
 * the line the historical full-list nearest scan kept, including distance
 * ties and same-timestamp clusters.
 */
public final class WordLyricModelNearestSearchTest {
    @Test
    public void textAndTranslationSearchMatchHistoricalScan() {
        Random random = new Random(20260829L);
        String[] texts = {
                "Hello world line",
                "Cruel summer chorus",
                "你好世界",
                "Hello world line",
                "Verse 歌词语句",
                "Middle passage",
                "Cruel summer chorus",
        };
        String[] translations = {"翻译甲", "翻译乙", "Translation C", "翻译甲"};

        for (int iteration = 0; iteration < 400; iteration++) {
            int size = 1 + random.nextInt(40);
            WordLyricModel model = new WordLyricModel();
            long time = random.nextInt(300);
            for (int i = 0; i < size; i++) {
                time += random.nextInt(400);
                WordLine line = new WordLine(time, texts[random.nextInt(texts.length)], null);
                if (random.nextInt(10) < 3) {
                    line.translation = translations[random.nextInt(translations.length)];
                }
                if (random.nextInt(10) < 2) {
                    line.displayText = texts[random.nextInt(texts.length)];
                }
                model.lines.add(line);
            }

            long maxTime = time;
            int queries = 30;
            for (int q = 0; q < queries; q++) {
                WordLine sample = model.lines.get(random.nextInt(model.lines.size()));
                String queryText = pickQueryText(random, sample);
                long position = random.nextInt(5) == 0
                        ? sample.timeMillis
                        : -500L + random.nextInt((int) maxTime + 1000);

                assertSame(
                        "findLineByText mismatch text=" + queryText + " position=" + position,
                        naiveNearestByText(model, queryText, position),
                        model.findLineByText(queryText, position));
                assertSame(
                        "findLineByTranslation mismatch text=" + queryText + " position=" + position,
                        naiveNearestByTranslation(model, queryText, position),
                        model.findLineByTranslation(queryText, position));
            }
        }
    }

    @Test
    public void negativePositionKeepsFirstMatchOrder() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(new WordLine(1_000L, "shared text", null));
        model.lines.add(new WordLine(9_000L, "shared text", null));
        WordLine first = model.findLineByText(
                WordLyricRenderSupport.normalizeLine("shared text"), -1L);
        assertSame(model.lines.get(0), first);
        WordLine translationFirst = model.findLineByTranslation("", -1L);
        assertEquals(null, translationFirst);
    }

    private static String pickQueryText(Random random, WordLine sample) {
        int choice = random.nextInt(4);
        switch (choice) {
            case 0:
                return sample.normalizedText;
            case 1:
                // Whitespace-stripped variant exercises the lyricMatchKey prefix path.
                return sample.normalizedText.replace(" ", "");
            case 2:
                return WordLyricRenderSupport.normalizeLine(sample.translation);
            default:
                return WordLyricRenderSupport.normalizeLine(sample.displayText);
        }
    }

    private static WordLine naiveNearestByText(WordLyricModel model, String normalizedText, long position) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return null;
        }
        if (position < 0) {
            for (WordLine line : model.lines) {
                if (WordLyricRenderSupport.matchesWordLineText(line, normalizedText)) {
                    return line;
                }
            }
            return null;
        }
        WordLine best = null;
        long bestDistance = Long.MAX_VALUE;
        for (WordLine line : model.lines) {
            if (WordLyricRenderSupport.matchesWordLineText(line, normalizedText)) {
                long distance = Math.abs(line.timeMillis - position);
                if (best == null || distance < bestDistance) {
                    best = line;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static WordLine naiveNearestByTranslation(
            WordLyricModel model, String normalizedText, long position) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return null;
        }
        if (position < 0) {
            for (WordLine line : model.lines) {
                if (line.normalizedTranslation().equals(normalizedText)) {
                    return line;
                }
            }
            return null;
        }
        WordLine best = null;
        long bestDistance = Long.MAX_VALUE;
        for (WordLine line : model.lines) {
            if (line.normalizedTranslation().equals(normalizedText)) {
                long distance = Math.abs(line.timeMillis - position);
                if (best == null || distance < bestDistance) {
                    best = line;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
