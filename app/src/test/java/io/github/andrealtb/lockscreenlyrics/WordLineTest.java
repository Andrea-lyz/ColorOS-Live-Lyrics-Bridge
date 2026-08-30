package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.andrealtb.lockscreenlyrics.render.LyricTimingMode;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unit tests for the {@link WordLine} lifecycle helpers that were promoted
 * out of {@code LockscreenLyricsModule} in step 3.1. These guard the timing
 * heuristics the renderer and the AOD path rely on.
 */
public class WordLineTest {

    @Test
    public void inferredEndMillis_defaultsToSixHundredMs() {
        WordLine line = new WordLine(1000L, "hello", null);
        assertEquals(1600L, line.endTimeMillis);
        assertEquals(LyricTimingMode.LINE_TIMED, line.timingMode);
    }

    @Test
    public void inferredEndMillis_keepsTrailingWordInBounds() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(1000L, 0, 5),
                new WordRange(2000L, 5, 10),
                new WordRange(3000L, 10, 15)));
        WordLine line = new WordLine(900L, "abc def ghi", words);
        assertEquals(3520L, line.endTimeMillis);
        assertEquals(LyricTimingMode.WORD_TIMED, line.timingMode);
    }

    @Test
    public void normalizedText_stripsLrcTimeTagsAndCollapsesWhitespace() {
        WordLine line = new WordLine(0L, "[00:12.34]  hello   world", null);
        assertEquals("hello world", line.normalizedText);
        assertEquals("helloworld", line.textMatchKey);
    }

    @Test
    public void findWordIndex_returnsLastWordAtOrBeforePosition() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(1000L, 0, 5),
                new WordRange(2000L, 5, 10),
                new WordRange(3000L, 10, 15)));
        WordLine line = new WordLine(0L, "abc def ghi", words);
        assertEquals(-1, line.findWordIndex(0L));
        assertEquals(-1, line.findWordIndex(999L));
        assertEquals(0, line.findWordIndex(1000L));
        assertEquals(0, line.findWordIndex(1500L));
        assertEquals(1, line.findWordIndex(2000L));
        // 2500L sits between word[1]=2000L and word[2]=3000L; the last word
        // whose time is <= position is index 1.
        assertEquals(1, line.findWordIndex(2500L));
        assertEquals(2, line.findWordIndex(3000L));
        assertEquals(2, line.findWordIndex(5000L));
    }

    @Test
    public void firstProgressStartMillis_usesFirstWordForWordTimedLines() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(9405L, 0, 3),
                new WordRange(9623L, 4, 7)));
        WordLine line = new WordLine(9405L, "But now", words);
        assertEquals(9405L, line.firstProgressStartMillis());
        WordLine untimed = new WordLine(1000L, "plain", null);
        assertEquals(1000L, untimed.firstProgressStartMillis());
    }

    @Test
    public void shouldHoldWordTimedReveal_untilFirstWord_evenWhenLineBeginIsEarlier() {
        // Love Story / QRC: line.begin 00:30.362, first word 00:32.362.
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(32_362L, 0, 3),
                new WordRange(32_700L, 4, 10)));
        WordLine line = new WordLine(30_362L, "See the lights", words);
        assertTrue(WordLyricRenderSupport.shouldHoldWordTimedReveal(line, 30_362L));
        assertTrue(WordLyricRenderSupport.shouldHoldWordTimedReveal(line, 32_361L));
        assertFalse(WordLyricRenderSupport.shouldHoldWordTimedReveal(line, 32_362L));
        assertFalse(WordLyricRenderSupport.shouldHoldWordTimedReveal(line, 33_000L));
    }

    @Test
    public void shouldHoldWordTimedReveal_ignoresLineTimedAndEmptyWords() {
        WordLine lineTimed = new WordLine(1_000L, "plain", null);
        assertFalse(WordLyricRenderSupport.shouldHoldWordTimedReveal(lineTimed, 1_200L));
        assertFalse(WordLyricRenderSupport.shouldHoldWordTimedReveal(null, 1_000L));
    }

    @Test
    public void lastWordReveal_matchesInLinePaceInsteadOfTrailingEndTag() {
        // Cruel Summer: <00:39.714> is the next line's first word, not "summer".
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(36_867L, 0, 4),
                new WordRange(37_049L, 5, 6),
                new WordRange(37_217L, 7, 12),
                new WordRange(38_105L, 13, 19)));
        WordLine line = new WordLine(36_409L, "It's a cruel summer", words, 39_714L);
        assertEquals(37_049L, line.wordEndMillis(0));
        assertEquals(38_105L, line.wordEndMillis(2));
        long lastEnd = line.wordEndMillis(3);
        assertEquals(38_287L, lastEnd);
        assertEquals(1f, line.wordProgress(3, lastEnd), 0.0001f);
        assertEquals(1f, line.wordProgress(3, 39_522L), 0.0001f);
        assertTrue(line.wordProgress(3, 38_200L) > 0.4f);
        assertTrue(line.wordProgress(3, 38_200L) < 1f);
    }

    @Test
    public void lastWordReveal_twoWordLineDoesNotStretchToTrailingTag() {
        // "With you": <00:53.996> is the next line, not the duration of "you".
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(50_937L, 0, 4),
                new WordRange(51_121L, 5, 8)));
        WordLine line = new WordLine(50_667L, "With you", words, 53_996L);
        assertEquals(51_121L, line.wordEndMillis(0));
        assertEquals(51_305L, line.wordEndMillis(1));
        assertEquals(1f, line.wordProgress(1, 51_996L), 0.0001f);
    }

    @Test
    public void lastWordReveal_clipsToNextLineBeginSoTheRowFinishesBeforeHandoff() {
        ArrayList<WordRange> currentWords = new ArrayList<>(Arrays.asList(
                new WordRange(33_783L, 0, 3),
                new WordRange(33_983L, 4, 8),
                new WordRange(34_295L, 9, 12),
                new WordRange(35_496L, 13, 17),
                new WordRange(36_359L, 18, 20)));
        WordLine current = new WordLine(33_514L, "And it's ooh whoa oh", currentWords, 36_867L);
        WordLine next = new WordLine(36_409L, "It's a cruel summer", null, 39_714L);
        WordLyricModel model = new WordLyricModel();
        model.lines.add(current);
        model.lines.add(next);

        assertEquals(36_409L, WordLyricRenderSupport.wordRevealEndMillis(model, current, 4));
        assertEquals(
                1f,
                WordLyricRenderSupport.wordRevealProgress(model, current, 4, 36_409L),
                0.0001f);
        assertTrue(
                WordLyricRenderSupport.wordRevealProgress(model, current, 4, 36_380L) < 1f);
    }

    @Test
    public void ultraShortSingleWordInterjectionUsesTimestampHighlight() {
        ArrayList<WordRange> ayyWords = new ArrayList<>(Arrays.asList(
                new WordRange(25_830L, 0, 3)));
        WordLine ayy = new WordLine(
                25_830L,
                "Ayy",
                ayyWords,
                25_910L,
                LyricTimingMode.WORD_TIMED);
        WordLine newMoney = new WordLine(
                25_890L,
                "New money, suit and tie",
                null,
                28_350L);
        WordLyricModel model = new WordLyricModel();
        model.lines.add(ayy);
        model.lines.add(newMoney);

        assertEquals(
                25_890L,
                WordLyricRenderSupport.wordRevealEndMillis(model, ayy, 0));
        assertTrue(WordLyricRenderSupport.shouldUseTimestampHighlight(model, ayy));
        assertFalse(WordLyricRenderSupport.shouldUseTimestampHighlight(model, newMoney));
        assertTrue(
                WordLyricRenderSupport.wordRevealProgress(model, ayy, 0, 25_831L) < 0.1f);
    }

    @Test
    public void wordProgress_clampsToZeroAndOne() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(1000L, 0, 5),
                new WordRange(2000L, 5, 10)));
        WordLine line = new WordLine(0L, "ab cd", words);
        assertEquals(0f, line.wordProgress(0, 0L), 0.0001f);
        assertEquals(0f, line.wordProgress(0, 1000L), 0.0001f);
        assertEquals(1f, line.wordProgress(0, 2000L), 0.0001f);
        assertEquals(1f, line.wordProgress(0, 5000L), 0.0001f);
    }

    @Test
    public void wordProgress_outOfRange_returnsZero() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(1000L, 0, 5)));
        WordLine line = new WordLine(0L, "ab", words);
        assertEquals(0f, line.wordProgress(-1, 1200L), 0.0001f);
        assertEquals(0f, line.wordProgress(5, 1200L), 0.0001f);
    }

    @Test
    public void delayToNextWordMillis_usesWordGap() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(1000L, 0, 5),
                new WordRange(2000L, 5, 10),
                new WordRange(3000L, 10, 15)));
        WordLine line = new WordLine(0L, "abc def ghi", words);
        long delay = line.delayToNextWordMillis(1100L);
        // 2000 - 1100 = 900, plus 16ms padding, then coerced to >= 40ms.
        assertEquals(916L, delay);
    }

    @Test
    public void delayToNextWordMillis_returnsFallbackAtEnd() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(1000L, 0, 5)));
        WordLine line = new WordLine(0L, "ab", words);
        assertEquals(220L, line.delayToNextWordMillis(5000L));
    }

    @Test
    public void normalizedDisplayText_cachesUntilSourceChanges() {
        WordLine line = new WordLine(0L, "hello", null);
        line.displayText = "hello   world";
        String first = line.normalizedDisplayText();
        String second = line.normalizedDisplayText();
        assertEquals(first, second);
        assertEquals("hello world", first);
        // Updating the source must invalidate the cache.
        line.displayText = "HELLO   WORLD";
        assertEquals("HELLO WORLD", line.normalizedDisplayText());
    }

    @Test
    public void normalizedTranslation_handlesNullAndEmpty() {
        WordLine line = new WordLine(0L, "hello", null);
        assertEquals("", line.normalizedTranslation());
        line.translation = "  你好  ";
        assertEquals("你好", line.normalizedTranslation());
    }

    @Test
    public void rendererLayoutCapacity_growsAndCopies() {
        WordLine line = new WordLine(0L, "hello", null);
        assertEquals(4, line.rendererLayoutStarts.length);
        line.ensureRendererLayoutCapacity(10);
        assertEquals(16, line.rendererLayoutStarts.length);
        line.rendererLayoutStarts[0] = 42;
        line.ensureRendererLayoutCapacity(20);
        assertEquals(32, line.rendererLayoutStarts.length);
        assertEquals(42, line.rendererLayoutStarts[0]);
    }

    @Test
    public void matchesWordLineText_matchesExactAndPunctuation() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(1000L, 0, 5)));
        WordLine line = new WordLine(0L, "hello world", words);
        String normalized = WordLyricRenderSupport.normalizeLine("hello world");
        assertTrue(WordLyricRenderSupport.matchesWordLineText(line, normalized));
        // Empty / null inputs should never match.
        assertFalse(WordLyricRenderSupport.matchesWordLineText(line, ""));
        assertFalse(WordLyricRenderSupport.matchesWordLineText(null, normalized));
    }

    @Test
    public void sameWordLine_identityCheck() {
        WordLine line = new WordLine(0L, "hello", null);
        assertTrue(WordLyricRenderSupport.sameWordLine(line, line));
        assertFalse(WordLyricRenderSupport.sameWordLine(line, null));
        assertFalse(WordLyricRenderSupport.sameWordLine(null, line));
        assertFalse(WordLyricRenderSupport.sameWordLine(line, new WordLine(0L, "hello", null)));
    }

    @Test
    public void findWord_returnsWordByPosition() {
        ArrayList<WordRange> words = new ArrayList<>(Arrays.asList(
                new WordRange(1000L, 0, 5),
                new WordRange(3000L, 5, 10)));
        WordLine line = new WordLine(0L, "ab cd", words);
        WordRange word = line.findWord(2500L);
        assertNotNull(word);
        assertEquals(1000L, word.timeMillis);
        assertNull(line.findWord(0L));
        assertNull(line.findWord(999L));
    }

    @Test
    public void findWord_returnsNullWhenWordsEmpty() {
        WordLine line = new WordLine(0L, "ab", null);
        assertNull(line.findWord(1500L));
    }
}
