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
    public void findOfficialAliasLine_prefersChorusTimestampOverNearbyIndex() {
        // Cruel Summer: official adapter 18 is the second chorus at 00:47.744,
        // but model index 18 is "It's cool..." and radius 2 from there hits
        // the first chorus at model 17 (00:36.409). Exact begin can miss when
        // the official list is 1ms off the word model.
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(33_514L, "And it's ooh whoa oh"));
        WordLine firstChorus = wordLine(
                36_409L,
                "It's a cruel summer",
                new long[] {36_867L, 37_049L, 37_217L, 38_105L});
        model.lines.add(firstChorus);
        model.lines.add(line(39_522L, "It's cool that's what I tell'em"));
        model.lines.add(line(42_303L, "No rules in breakable heaven"));
        model.lines.add(line(44_757L, "But ooh whoa oh"));
        WordLine secondChorus = wordLine(
                47_744L,
                "It's a cruel summer",
                new long[] {48_032L, 48_201L, 48_369L, 49_425L});
        model.lines.add(secondChorus);

        assertEquals(
                firstChorus,
                model.findLineByTextNearIndex("It's a cruel summer", 2, 2, false));

        WordLine alias = model.findOfficialAliasLine(
                47_745L,
                "It's a cruel summer",
                1,
                2);
        assertNotNull(alias);
        assertEquals(47_744L, alias.timeMillis);
        assertEquals(secondChorus, alias);
    }

    @Test
    public void findOfficialAliasLine_keepsExactBeginMatch() {
        WordLyricModel model = new WordLyricModel();
        WordLine firstChorus = line(36_409L, "It's a cruel summer");
        WordLine secondChorus = line(47_744L, "It's a cruel summer");
        model.lines.add(firstChorus);
        model.lines.add(line(39_522L, "It's cool that's what I tell'em"));
        model.lines.add(secondChorus);

        assertEquals(
                firstChorus,
                model.findOfficialAliasLine(36_409L, "It's a cruel summer", 0, 0));
        assertEquals(
                secondChorus,
                model.findOfficialAliasLine(47_744L, "It's a cruel summer", 1, 2));
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
    public void propagateNearbyTranslationsRepairsRepeatedMainTextBeforeDraw() {
        WordLyricModel model = new WordLyricModel();
        WordLine untranslated = line(1_000L, "same chorus");
        WordLine translated = line(5_000L, "same chorus");
        translated.translation = "同一段副歌";
        model.lines.add(untranslated);
        model.lines.add(line(3_000L, "middle"));
        model.lines.add(translated);

        assertEquals(1, model.propagateNearbyTranslations(6));
        assertEquals("同一段副歌", untranslated.translation);
        assertEquals(0, model.propagateNearbyTranslations(6));
    }

    @Test
    public void propagateNearbyTranslationsUsesOfficialDisplayAlias() {
        WordLyricModel model = new WordLyricModel();
        WordLine untranslated = line(1_000L, "source one");
        untranslated.displayText = "shared display";
        WordLine translated = line(2_000L, "source two");
        translated.displayText = "shared display";
        translated.translation = "共享翻译";
        model.lines.add(untranslated);
        model.lines.add(translated);

        assertEquals(1, model.propagateNearbyTranslations(2));
        assertEquals("共享翻译", untranslated.translation);
    }

    @Test
    public void propagateNearbyTranslationsRespectsRadius() {
        WordLyricModel model = new WordLyricModel();
        WordLine untranslated = line(1_000L, "repeat");
        model.lines.add(untranslated);
        for (int index = 0; index < 7; index++) {
            model.lines.add(line(2_000L + index, "middle-" + index));
        }
        WordLine translated = line(9_000L, "repeat");
        translated.translation = "翻译";
        model.lines.add(translated);

        assertEquals(0, model.propagateNearbyTranslations(6));
        assertEquals("", untranslated.translation);
    }

    @Test
    public void propagateNearbyTranslationsDoesNotChainPastRadius() {
        WordLyricModel model = new WordLyricModel();
        WordLine source = line(1_000L, "repeat");
        source.translation = "翻译";
        model.lines.add(source);
        for (int index = 1; index < 6; index++) {
            model.lines.add(line(1_000L + index, "middle-a-" + index));
        }
        WordLine near = line(2_000L, "repeat");
        model.lines.add(near);
        for (int index = 7; index < 12; index++) {
            model.lines.add(line(2_000L + index, "middle-b-" + index));
        }
        WordLine beyond = line(3_000L, "repeat");
        model.lines.add(beyond);

        assertEquals(1, model.propagateNearbyTranslations(6));
        assertEquals("翻译", near.translation);
        assertEquals("", beyond.translation);
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
    public void isBeforeFirstProgressStart_usesFirstWordNotOfficialZeroAlias() {
        WordLyricModel model = new WordLyricModel();
        WordLine first = wordLine(9_405L, "But now you're going", new long[] {9_405L, 9_623L});
        model.lines.add(first);
        model.officialLines.add(first);
        assertEquals(9_405L, model.firstProgressStartMillis());
        assertTrue(model.isBeforeFirstProgressStart(136L));
        assertTrue(model.isBeforeFirstProgressStart(4_282L));
        assertFalse(model.isBeforeFirstProgressStart(9_405L));
        assertFalse(model.isBeforeFirstProgressStart(10_284L));
        assertFalse(model.isBeforeFirstProgressStart(-1L));
    }

    @Test
    public void isBeforeFirstProgressStart_usesLineTimeWhenUntimed() {
        WordLyricModel model = new WordLyricModel();
        model.lines.add(line(1_000L, "untimed"));
        assertTrue(model.isBeforeFirstProgressStart(0L));
        assertFalse(model.isBeforeFirstProgressStart(1_000L));
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
