package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.andrealtb.lockscreenlyrics.render.LyricTimingMode;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
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
        assertEquals(0, line.findWordIndex(0L));
        assertEquals(0, line.findWordIndex(1500L));
        assertEquals(1, line.findWordIndex(2000L));
        // 2500L sits between word[1]=2000L and word[2]=3000L; the last word
        // whose time is <= position is index 1.
        assertEquals(1, line.findWordIndex(2500L));
        assertEquals(2, line.findWordIndex(3000L));
        assertEquals(2, line.findWordIndex(5000L));
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
        // Before the first word, the helper still resolves to the first word so
        // the renderer can show the leading char highlight.
        WordRange earliest = line.findWord(0L);
        assertNotNull(earliest);
        assertEquals(1000L, earliest.timeMillis);
    }

    @Test
    public void findWord_returnsNullWhenWordsEmpty() {
        WordLine line = new WordLine(0L, "ab", null);
        assertNull(line.findWord(1500L));
    }
}
