package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Exercises the {@link TimedLyricDocument} merge / search / serialization helpers
 * that the Bridge relies on for translation pairing, word-timing promotion, and
 * round-trip LRC emit. The constructor of {@link TimedLyricDocument} is
 * package-private, so the document must be built through the {@link
 * TimedLyricDocument#TimedLyricDocument(List)} form on the same JVM classpath.
 */
public final class TimedLyricDocumentTest {

    private static TimedLyricDocument.Line line(
            long startMillis,
            long endMillis,
            String text,
            String translation,
            TimedLyricDocument.Word... words) {
        return new TimedLyricDocument.Line(
                startMillis,
                endMillis,
                text,
                translation,
                Arrays.asList(words));
    }

    private static TimedLyricDocument.Word word(
            long startMillis,
            long endMillis,
            int start,
            int end) {
        return new TimedLyricDocument.Word(startMillis, endMillis, start, end);
    }

    private static List<TimedLyricDocument.Line> lines(
            TimedLyricDocument.Line... values) {
        ArrayList<TimedLyricDocument.Line> list = new ArrayList<>();
        Collections.addAll(list, values);
        return list;
    }

    @Test
    public void constructorSortsLinesByStartMillisAndDropsEmpty() {
        TimedLyricDocument.Line second = line(3_000L, 4_000L, "hello", "");
        TimedLyricDocument.Line first = line(1_000L, 2_000L, "first", "");
        TimedLyricDocument.Line empty = line(2_000L, 2_500L, "   ", "");

        TimedLyricDocument document = new TimedLyricDocument(
                lines(second, first, empty));

        assertEquals(2, document.lineCount());
        assertEquals("first", document.lines().get(0).text);
        assertEquals("hello", document.lines().get(1).text);
    }

    @Test
    public void constructorNormalisesNegativeStartAndEndMillis() {
        TimedLyricDocument.Line bounded = new TimedLyricDocument.Line(
                -123L,
                -50L,
                "hello",
                "",
                Collections.<TimedLyricDocument.Word>emptyList());

        assertEquals(0L, bounded.startMillis);
        assertEquals(0L, bounded.endMillis);
    }

    @Test
    public void hasWordTimingOnlyCountsLinesWithMultipleWords() {
        TimedLyricDocument document = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello", "",
                        word(0L, 500L, 0, 1),
                        word(500L, 1_000L, 1, 2)),
                line(1_000L, 2_000L, "world", ""),
                line(2_000L, 3_000L, "skip", "",
                        word(2_000L, 3_000L, 0, 1))));

        assertTrue(document.hasWordTiming());
        assertEquals(1, document.wordTimedLineCount());
    }

    @Test
    public void emptyDocumentHasNoLinesAndNoWordTiming() {
        assertTrue(TimedLyricDocument.EMPTY.isEmpty());
        assertEquals(0, TimedLyricDocument.EMPTY.lineCount());
        assertEquals(0, TimedLyricDocument.EMPTY.translationCount());
        assertFalse(TimedLyricDocument.EMPTY.hasWordTiming());
    }

    @Test
    public void withTranslationsFromKeepsExistingTranslationsWhenNoMatch() {
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello", "你好"),
                line(1_000L, 2_000L, "world", "世界")));
        TimedLyricDocument empty = TimedLyricDocument.EMPTY;

        TimedLyricDocument merged = primary.withTranslationsFrom(empty, 1_000L);

        assertEquals(2, merged.lineCount());
        assertEquals("你好", merged.lines().get(0).translation);
        assertEquals("世界", merged.lines().get(1).translation);
    }

    @Test
    public void withTranslationsFromPairsNearestTranslationWithinTolerance() {
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello", ""),
                line(1_000L, 2_000L, "world", "")));
        TimedLyricDocument translations = new TimedLyricDocument(lines(
                line(2L, 10L, "你好", ""),
                line(1_010L, 1_500L, "世界", "")));

        TimedLyricDocument merged = primary.withTranslationsFrom(translations, 50L);

        assertEquals("你好", merged.lines().get(0).translation);
        assertEquals("世界", merged.lines().get(1).translation);
    }

    @Test
    public void withTranslationsFromReturnsOriginalWhenOutsideTolerance() {
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello", "你好-stays")));
        TimedLyricDocument translations = new TimedLyricDocument(lines(
                line(2_000L, 3_000L, "out of reach", "")));

        TimedLyricDocument merged = primary.withTranslationsFrom(translations, 100L);

        assertEquals("你好-stays", merged.lines().get(0).translation);
    }

    @Test
    public void withUsableTranslationsFromRejectsSameTextOrLatinBackingVocal() {
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "Put your lips close to mine", "")));
        TimedLyricDocument translations = new TimedLyricDocument(lines(
                line(0L, 1_000L, "Put your lips close to mine (close to mine)", "")));

        TimedLyricDocument merged = primary.withUsableTranslationsFrom(translations, 50L);

        assertEquals("", merged.lines().get(0).translation);
    }

    @Test
    public void withWordTimingFromPromotesPlainLineWhenCandidateMatchesText() {
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello world", "")));
        TimedLyricDocument wordTimed = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello world", "",
                        word(0L, 500L, 0, 5),
                        word(500L, 1_000L, 6, 11))));

        TimedLyricDocument merged = primary.withWordTimingFrom(wordTimed, 50L);

        TimedLyricDocument.Line promoted = merged.lines().get(0);
        assertEquals(2, promoted.words.size());
        assertTrue(merged.hasWordTiming());
        assertEquals(1_000L, promoted.endMillis);
    }

    @Test
    public void withWordTimingFromKeepsLineWhenTextDoesNotMatchCandidate() {
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "primary", "")));
        TimedLyricDocument wordTimed = new TimedLyricDocument(lines(
                line(0L, 1_000L, "different text", "",
                        word(0L, 500L, 0, 5),
                        word(500L, 1_000L, 6, 11))));

        TimedLyricDocument merged = primary.withWordTimingFrom(wordTimed, 50L);

        assertFalse(merged.hasWordTiming());
        assertEquals(0, merged.lines().get(0).words.size());
    }

    @Test
    public void withWordTimingFromDoesNotDemoteAlreadyWordTimedLine() {
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello", "",
                        word(0L, 400L, 0, 1),
                        word(400L, 800L, 1, 2),
                        word(800L, 1_000L, 2, 3))));
        TimedLyricDocument wordTimed = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello", "",
                        word(0L, 500L, 0, 1),
                        word(500L, 1_000L, 1, 2))));

        TimedLyricDocument merged = primary.withWordTimingFrom(wordTimed, 50L);

        assertEquals(3, merged.lines().get(0).words.size());
    }

    @Test
    public void usableTranslationMatchCountTreatsSameLanguageAsNotUsable() {
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "Hello world", ""),
                line(1_000L, 2_000L, "Another line", "")));
        TimedLyricDocument translations = new TimedLyricDocument(lines(
                line(0L, 1_000L, "Hello world (close to mine)", ""),
                line(1_000L, 2_000L, "请翻译我", "")));

        int matches = primary.usableTranslationMatchCount(translations, 50L);

        assertEquals(1, matches);
    }

    @Test
    public void toPlainLrcEmitsSortedTextWithoutTranslation() {
        TimedLyricDocument document = new TimedLyricDocument(lines(
                line(1_000L, 2_000L, "hello", "ignored"),
                line(0L, 500L, "first", "")));

        String plain = document.toPlainLrc();

        assertEquals("[00:00.000]first\n[00:01.000]hello\n", plain);
    }

    @Test
    public void toEnhancedLrcIncludesPerWordTimingWhenPresent() {
        TimedLyricDocument document = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello world", "",
                        word(0L, 400L, 0, 5),
                        word(400L, 1_000L, 6, 11)),
                line(1_000L, 2_000L, "trailing line", "")));

        String enhanced = document.toEnhancedLrc();

        // The cursor advances by the per-word text slice; an unmatched gap
        // between word end (5) and word start (6) is emitted as a literal
        // space.
        assertEquals(
                "[00:00.000]<00:00.000>hello <00:00.400>world<00:01.000>\n"
                        + "[00:01.000]trailing line\n",
                enhanced);
    }

    @Test
    public void toEnhancedLrcEmitTranslationBelowOriginalLine() {
        TimedLyricDocument document = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello", "你好")));

        String enhanced = document.toEnhancedLrc();

        assertEquals("[00:00.000]hello\n[00:00.000]你好\n", enhanced);
    }

    @Test
    public void sanitizeSuspiciousWordTimingKeepsHealthyWordTiming() {
        TimedLyricDocument.Line candidate = line(
                0L, 1_000L, "hello",
                "",
                word(0L, 500L, 0, 1),
                word(500L, 1_000L, 1, 2));

        TimedLyricDocument document = new TimedLyricDocument(lines(candidate));

        assertEquals(2, document.lines().get(0).words.size());
    }

    @Test
    public void sanitizeSuspiciousWordTimingLeavesSingleWordAlone() {
        TimedLyricDocument.Line candidate = line(
                0L, 1_000L, "hello",
                "",
                word(0L, 1_000L, 0, 1));

        TimedLyricDocument document = new TimedLyricDocument(lines(candidate));

        assertEquals(1, document.lines().get(0).words.size());
    }

    @Test
    public void fromRawLrcReturnsEmptyOnPlainTextWithoutTimedTags() {
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc("plain text without timestamps");

        assertTrue(document.isEmpty());
    }

    @Test
    public void fromRawLrcParsesSingleTimestampedLine() {
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc("[00:01.000]hello");

        assertEquals(1, document.lineCount());
        assertEquals("hello", document.lines().get(0).text);
        assertEquals(1_000L, document.lines().get(0).startMillis);
    }

    @Test
    public void fromRawLrcParsesWordTimedEnhancedLrc() {
        String enhanced = "[00:01.000]<00:01.000>hel<00:01.500>lo<00:02.000>";
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(enhanced);

        assertEquals(1, document.lineCount());
        TimedLyricDocument.Line parsed = document.lines().get(0);
        assertNotNull(parsed);
        assertEquals("hello", parsed.text);
        // The trailing <00:02.000> on the line is captured as the line-end
        // tag, not the last word's end time.
        assertEquals(2_000L, parsed.endMillis);
    }

    @Test
    public void fromRawLrcDowngradesSuspiciousNonMonotonicWordTiming() {
        // Two words whose start times are out of order must collapse to a
        // single line-timed word. The AutoParser wires start times from the
        // <mm:ss.xxx> tags verbatim, so we can craft the exact ordering here.
        String enhanced = "[00:08.000]<00:08.000>out<00:01.000>of<00:08.500>";
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(enhanced);

        assertEquals(1, document.lineCount());
        TimedLyricDocument.Line parsed = document.lines().get(0);
        assertNotNull(parsed);
        assertEquals("non-monotonic word timing should be downgraded to a single "
                        + "line-timed word, but found " + parsed.words.size(),
                1, parsed.words.size());
        assertEquals(0, parsed.words.get(0).start);
        assertEquals("outof".length(), parsed.words.get(0).end);
    }

    @Test
    public void withTranslationsFromNullSnapshotReturnsOriginalTranslations() {
        // Passing the constant TimedLyricDocument.EMPTY as the translation
        // source behaves like a snapshot with no rows: the primary lines
        // keep their existing translations untouched.
        TimedLyricDocument primary = new TimedLyricDocument(lines(
                line(0L, 1_000L, "hello", "你好")));

        TimedLyricDocument merged = primary.withTranslationsFrom(
                TimedLyricDocument.EMPTY, 1_000L);

        assertEquals("你好", merged.lines().get(0).translation);
    }
}
