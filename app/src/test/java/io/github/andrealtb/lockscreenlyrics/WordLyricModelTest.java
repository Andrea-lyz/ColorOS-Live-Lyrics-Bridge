package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unit tests for {@link WordLyricModel}. Covers the lookup helpers that the
 * official SystemUI recycler binder consumes. Promoted from
 * {@code LockscreenLyricsModule} in step 3.1.
 */
public class WordLyricModelTest {

    private static WordLine line(long time, String text) {
        return new WordLine(time, text, null);
    }

    private static WordLine wordLine(long time, String text, long[] wordTimes) {
        ArrayList<WordRange> words = new ArrayList<>(wordTimes.length);
        for (int i = 0; i < wordTimes.length; i++) {
            words.add(new WordRange(wordTimes[i], 0, 0));
        }
        return new WordLine(time, text, words);
    }

    @Test
    public void findActiveLine_returnsLatestLineAtOrBeforePosition() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "first"));
        model.lines.add(line(2000L, "second"));
        model.lines.add(line(3000L, "third"));
        // first line boundary is inclusive.
        assertEquals("first", model.findActiveLine(1000L).text);
        assertEquals("first", model.findActiveLine(1500L).text);
        assertEquals("second", model.findActiveLine(2000L).text);
        assertEquals("third", model.findActiveLine(9999L).text);
    }

    @Test
    public void findActiveLine_returnsNullWhenPositionUnderflows() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "first"));
        assertNull(model.findActiveLine(0L));
    }

    @Test
    public void displayIndexAt_returnsMinusOneWhenModelEmpty() {
        WordLyricModel model = new WordLyricModel();
        assertEquals(-1, model.displayIndexAt(5000L));
    }

    @Test
    public void displayIndexAt_returnsZeroWhenModelOnlyHasActiveLine() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "alpha"));
        assertEquals(0, model.displayIndexAt(5000L));
    }

    @Test
    public void findLine_prefersTextualMatchBeforePositionFallback() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "hello"));
        model.lines.add(line(2000L, "world"));
        WordLine match = model.findLine(9999L, "world");
        assertNotNull(match);
        assertEquals(2000L, match.timeMillis);
    }

    @Test
    public void findLineByTextOccurrence_returnsNthMatch() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "hello"));
        model.lines.add(line(2000L, "hello again"));
        model.lines.add(line(3000L, "hello"));
        // Use the full text so the prefix matcher doesn't fold "hello again"
        // into the first match. Occurrence 0 is the leading "hello" at index
        // 0, and occurrence 1 is the matching "hello" line at index 2.
        assertEquals("hello", model.findLineByTextOccurrence("hello", 0).text);
        assertEquals("hello", model.findLineByTextOccurrence("hello", 1).text);
        assertNull(model.findLineByTextOccurrence("hello", 2));
    }

    @Test
    public void findLineByTextOccurrence_matchesFullText() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "hello"));
        model.lines.add(line(2000L, "hello again"));
        model.lines.add(line(3000L, "hello world"));
        // Using the unique full text ("hello again") matches a single line.
        assertEquals("hello again", model.findLineByTextOccurrence("hello again", 0).text);
        assertNull(model.findLineByTextOccurrence("hello again", 1));
    }

    @Test
    public void findLineByTextOccurrence_rejectsNegative() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "hello"));
        assertNull(model.findLineByTextOccurrence("hello", -1));
    }

    @Test
    public void findLineByTranslation_matchesNormalizedTranslation() {
        WordLyricModel model = new WordLyricModel();
        WordLine line = line(1000L, "Hello");
        line.translation = "你好";
        model.lines.add(line);
        WordLine match = model.findLineByTranslation("你好");
        assertNotNull(match);
        assertEquals(1000L, match.timeMillis);
    }

    @Test
    public void findLineByTextNearest_respectsPosition() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "shared"));
        model.lines.add(line(5000L, "shared"));
        WordLine match = model.findLineByText("shared", 5100L);
        assertNotNull(match);
        assertEquals(5000L, match.timeMillis);
    }

    @Test
    public void findLineByText_nearest_respectsMaxDistance() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "unique"));
        model.lines.add(line(10_000L, "unique"));
        assertNotNull(model.findNearestLineByTime(9500L, 1000L));
        assertNull(model.findNearestLineByTime(9500L, 100L));
    }

    @Test
    public void findLineByTextNearIndex_searchesRadius() {
        WordLyricModel model = new WordLyricModel();
        for (int i = 0; i < 10; i++) {
            model.lines.add(line(1000L * i, "line" + i));
        }
        WordLine pick = model.findLineByTextNearIndex("line5", 0, 10, false);
        assertNotNull(pick);
        assertEquals("line5", pick.text);
        assertNull(model.findLineByTextNearIndex("line5", 0, 4, false));
    }

    @Test
    public void findLineByTextNearIndex_requiresTranslationWhenAsked() {
        WordLyricModel model = new WordLyricModel();
        WordLine anchor = line(1000L, "anchor");
        model.lines.add(anchor);
        WordLine target = line(2000L, "target");
        model.lines.add(target);
        assertNull(model.findLineByTextNearIndex("target", 0, 5, true));
        target.translation = "翻译";
        WordLine match = model.findLineByTextNearIndex("target", 0, 5, true);
        assertNotNull(match);
        assertEquals("target", match.text);
    }

    @Test
    public void translationCount_excludesEmpty() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "a"));
        model.lines.add(line(2000L, "b"));
        WordLine withTranslation = line(3000L, "c");
        withTranslation.translation = "翻译";
        model.lines.add(withTranslation);
        assertEquals(1, model.translationCount());
    }

    @Test
    public void hasDuplicateRenderableText_flagsRepeated() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "hello"));
        model.lines.add(line(2000L, "world"));
        model.lines.add(line(3000L, "hello"));
        assertTrue(model.hasDuplicateRenderableText("hello"));
        assertFalse(model.hasDuplicateRenderableText("world"));
    }

    @Test
    public void hasRenderableText_returnsFalseOnEmpty() {
        WordLyricModel model = new WordLyricModel();
        assertFalse(model.hasRenderableText(""));
        model.lines.add(line(1000L, "hello"));
        assertTrue(model.hasRenderableText("hello"));
        assertFalse(model.hasRenderableText("not-present"));
    }

    @Test
    public void ensureRenderableTextIndex_isIdempotent() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "hello"));
        model.lines.add(line(2000L, "hello"));
        assertTrue(model.hasDuplicateRenderableText("hello"));
        // Second call must not invalidate the cached index.
        assertTrue(model.hasDuplicateRenderableText("hello"));
    }

    @Test
    public void indexOfLine_usesIdentity() {
        WordLyricModel model = new WordLyricModel();
        WordLine first = line(1000L, "alpha");
        WordLine second = line(2000L, "alpha");
        model.lines.add(first);
        model.lines.add(second);
        assertEquals(0, model.indexOfLine(first));
        assertEquals(1, model.indexOfLine(second));
        assertEquals(-1, model.indexOfLine(line(3000L, "alpha")));
    }

    @Test
    public void firstDisplayLine_skipsEmptyLines() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(0L, ""));
        model.lines.add(line(1000L, "first text"));
        assertEquals("first text", model.firstDisplayLine().text);
    }

    @Test
    public void adapterIndexOfLine_fallsBackToOfficialLines() {
        WordLyricModel model = new WordLyricModel();
        WordLine slots = line(1000L, "slot");
        WordLine official = line(1000L, "official");
        model.officialLines.add(slots);
        model.officialLines.add(official);
        assertEquals(0, model.adapterIndexOfLine(slots));
        assertEquals(1, model.adapterIndexOfLine(official));
    }

    @Test
    public void findLineAtTime_returnsExactMatch() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "alpha"));
        model.lines.add(line(2000L, "beta"));
        model.lines.add(line(3000L, "gamma"));
        assertEquals("alpha", model.findLineAtTime(1000L).text);
        assertEquals("beta", model.findLineAtTime(2000L).text);
        assertEquals("gamma", model.findLineAtTime(3000L).text);
        assertNull(model.findLineAtTime(2500L));
        assertNull(model.findLineAtTime(-1L));
    }

    @Test
    public void lineAt_adapterIndex_clampsAndPrefersOfficial() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "main"));
        model.officialLines.add(line(2000L, "official"));
        assertNull(model.lineAtAdapterIndex(-1));
        // Adapter index prefers `lines` when both views are populated and the
        // requested slot is in range.
        assertEquals("main", model.lineAtAdapterIndex(0).text);
        // Index 1 is past `lines` (size 1) and out of `officialLines` (size 1)
        // so the adapter path must report null.
        assertNull(model.lineAtAdapterIndex(1));
        assertNull(model.lineAtAdapterIndex(5));
    }

    @Test
    public void lineAt_adapterIndex_fallsBackToOfficialWhenLinesEmpty() {
        WordLyricModel model = new WordLyricModel();
        model.officialLines.add(line(2000L, "official"));
        // No `lines` are registered, so the adapter path falls straight to
        // `officialLines` for index 0.
        assertEquals("official", model.lineAtAdapterIndex(0).text);
    }

    @Test
    public void lineAt_adapterIndex_officialLinesShadowByLength() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "main-0"));
        model.lines.add(line(2000L, "main-1"));
        model.officialLines.add(line(3000L, "official-0"));
        // Both views cover index 0; `lines` wins.
        assertEquals("main-0", model.lineAtAdapterIndex(0).text);
        // Index 1 is in range for `lines` only.
        assertEquals("main-1", model.lineAtAdapterIndex(1).text);
    }

    @Test
    public void lineAtOfficialDisplayIndex_fallsBackToAdapter() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1000L, "main"));
        assertEquals("main", model.lineAtOfficialDisplayIndex(0).text);
    }

    @Test
    public void wordLineInMatchesRenderableText_matchesTranslation() {
        WordLyricModel model = new WordLyricModel();
        WordLine withTranslation = line(1000L, "hello");
        withTranslation.translation = "你好";
        model.lines.add(withTranslation);
        assertTrue(model.hasRenderableText("hello"));
        assertTrue(model.hasRenderableText("你好"));
    }
}
