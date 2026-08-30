package io.github.andrealtb.lockscreenlyrics.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class LyricDrawLayoutEngineTest {
    @Test
    public void wrapsAtWhitespaceAndTrimsSegments() {
        LyricDrawLayoutEngine engine = new LyricDrawLayoutEngine(
                (text, start, end) -> end - start);
        WordLine line = line("hello world");

        engine.build(line, line.text, 6f, false, false, 100, null);

        List<LyricDrawLine> lines = engine.lines();
        assertEquals(2, lines.size());
        assertEquals("hello", line.text.substring(lines.get(0).start, lines.get(0).end));
        assertEquals("world", line.text.substring(lines.get(1).start, lines.get(1).end));
    }

    @Test
    public void matchingWordLineCacheAvoidsRemeasureAndReusesPool() {
        int[] measurements = {0};
        LyricDrawLayoutEngine engine = new LyricDrawLayoutEngine(
                (text, start, end) -> {
                    measurements[0]++;
                    return end - start;
                });
        WordLine line = line("cached line");

        engine.build(line, line.text, 40f, false, false, 100, null);
        LyricDrawLine first = engine.lines().get(0);
        int initialMeasurements = measurements[0];
        engine.build(line, line.text, 40f, false, false, 100, null);

        assertEquals(initialMeasurements, measurements[0]);
        assertSame(first, engine.lines().get(0));
    }

    @Test
    public void textSizeOrWidthChangeInvalidatesCache() {
        int[] measurements = {0};
        LyricDrawLayoutEngine engine = new LyricDrawLayoutEngine(
                (text, start, end) -> {
                    measurements[0]++;
                    return end - start;
                });
        WordLine line = line("invalidate me");

        engine.build(line, line.text, 40f, false, false, 100, null);
        int afterInitial = measurements[0];
        engine.build(line, line.text, 30f, false, false, 100, null);
        int afterWidth = measurements[0];
        engine.build(line, line.text, 30f, false, false, 120, null);

        assertTrue(afterWidth > afterInitial);
        assertTrue(measurements[0] > afterWidth);
    }

    @Test
    public void singleLineKeepsFullTextEvenWhenItOverflows() {
        LyricDrawLayoutEngine engine = new LyricDrawLayoutEngine(
                (text, start, end) -> end - start);
        WordLine line = line("one very long line");

        engine.build(line, line.text, 4f, true, false, 100, null);

        assertEquals(1, engine.lines().size());
        LyricDrawLine drawLine = engine.lines().get(0);
        assertEquals(line.text, line.text.substring(drawLine.start, drawLine.end));
    }

    private static WordLine line(String text) {
        ArrayList<WordRange> words = new ArrayList<>();
        words.add(new WordRange(0L, 0, text.length()));
        return new WordLine(0L, text, words, 1_000L);
    }
}
