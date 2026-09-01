package io.github.andrealtb.lockscreenlyrics.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;

public final class OfficialLyricFrameResolverTest {
    private final OfficialLyricFrameResolver resolver = new OfficialLyricFrameResolver();
    private final OfficialLyricFrameResolver.Selection selection =
            new OfficialLyricFrameResolver.Selection();

    @Test
    public void indexedMainLineWins() {
        WordLyricModel model = model(line(1_000L, "first"), line(2_000L, "second"));

        resolver.resolveInto(
                model,
                "second",
                1,
                1,
                model.lines.get(1),
                model.lines.get(0),
                1_100L,
                false,
                "",
                -1L,
                selection);

        assertSame(model.lines.get(1), selection.line);
        assertEquals("indexed-main", selection.matchReason);
        assertNull(selection.translationLine);
    }

    @Test
    public void duplicateTextUsesOccurrenceNearestPlayback() {
        WordLine first = line(1_000L, "again");
        WordLine second = line(5_000L, "again");
        WordLyricModel model = model(first, line(3_000L, "middle"), second);

        resolver.resolveInto(
                model,
                "again",
                -1,
                -1,
                null,
                second,
                4_800L,
                true,
                "",
                -1L,
                selection);

        assertSame(second, selection.line);
        assertEquals("timed-duplicate-main", selection.matchReason);
        assertTrue(selection.duplicateText);
    }

    @Test
    public void translationOnlyMatchStaysSeparateFromMainLine() {
        WordLine line = line(1_000L, "hello");
        line.translation = "你好";
        WordLyricModel model = model(line);

        resolver.resolveInto(
                model,
                "你好",
                0,
                0,
                line,
                line,
                1_000L,
                false,
                "",
                -1L,
                selection);

        assertNull(selection.line);
        assertSame(line, selection.translationLine);
        assertEquals("indexed-translation", selection.matchReason);
    }

    @Test
    public void rememberedActiveLineIsLastUniqueFallback() {
        WordLine first = line(1_000L, "first");
        WordLyricModel model = model(first);

        resolver.resolveInto(
                model,
                "remembered",
                -1,
                -1,
                null,
                null,
                -1L,
                false,
                "remembered",
                1_000L,
                selection);

        assertSame(first, selection.line);
        assertEquals("remembered-active-line", selection.matchReason);
    }

    @Test
    public void explicitOpeningAliasMappingDrawsRealLineInsteadOfBlankCreditSlot() {
        WordLine credit = line(290L, "Lyrics by");
        WordLine firstLyric = line(670L, "first lyric");
        WordLyricModel model = model(credit, firstLyric);
        model.officialLines.clear();
        model.officialLines.add(firstLyric);
        model.officialLines.add(null);

        resolver.resolveInto(
                model,
                "song title - artist",
                0,
                0,
                firstLyric,
                firstLyric,
                700L,
                false,
                "",
                -1L,
                selection);

        assertSame(firstLyric, selection.line);
        assertEquals("mapped-official-alias", selection.matchReason);
    }

    @Test
    public void activeIndexedLineFillsOnlyItsEmptyOfficialSlot() {
        WordLine credit = line(290L, "Lyrics by");
        WordLine firstLyric = line(670L, "first lyric");
        WordLyricModel model = model(credit, firstLyric);
        model.officialLines.clear();
        model.officialLines.add(firstLyric);
        model.officialLines.add(credit);

        resolver.resolveInto(
                model,
                "",
                1,
                0,
                firstLyric,
                firstLyric,
                700L,
                false,
                "",
                -1L,
                selection);

        assertSame(firstLyric, selection.line);
        assertEquals("active-empty-slot", selection.matchReason);

        resolver.resolveInto(
                model,
                "",
                0,
                0,
                credit,
                firstLyric,
                700L,
                false,
                "",
                -1L,
                selection);
        assertNull(selection.line);
        assertEquals("none", selection.matchReason);
    }

    @Test
    public void reusableSelectionIsClearedBetweenCalls() {
        WordLine first = line(1_000L, "first");
        WordLyricModel model = model(first);
        resolver.resolveInto(
                model, "first", 0, 0, first, first, 1_000L, false, "", -1L, selection);
        resolver.resolveInto(
                model, "missing", -1, -1, null, null, 1_000L, false, "", -1L, selection);

        assertNull(selection.line);
        assertNull(selection.translationLine);
        assertEquals("none", selection.matchReason);
    }

    private static WordLyricModel model(WordLine... lines) {
        WordLyricModel model = new WordLyricModel();
        for (WordLine line : lines) {
            model.lines.add(line);
            model.officialLines.add(line);
        }
        return model;
    }

    private static WordLine line(long timeMillis, String text) {
        ArrayList<WordRange> words = new ArrayList<>();
        words.add(new WordRange(timeMillis, 0, text.length()));
        return new WordLine(timeMillis, text, words, timeMillis + 800L);
    }
}
