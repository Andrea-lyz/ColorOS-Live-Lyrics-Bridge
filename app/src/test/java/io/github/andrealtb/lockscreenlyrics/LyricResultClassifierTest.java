package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LyricResultClassifierTest {
    @Test
    public void sourceMissIsNotTreatedAsWholeTrackNoLyric() {
        assertEquals(
                LyricSourceEvent.Outcome.SOURCE_MISS,
                LyricResultClassifier.classifyEmptyResult("EMBEDDED"));
    }

    @Test
    public void finalNotFoundIsTerminalNoLyric() {
        assertEquals(
                LyricSourceEvent.Outcome.NO_LYRIC,
                LyricResultClassifier.classifyEmptyResult("NOT_FOUND"));
    }

    @Test
    public void nonTerminalSourceNameIsNotInferredAsParseFailure() {
        assertEquals(
                LyricSourceEvent.Outcome.SOURCE_MISS,
                LyricResultClassifier.classifyEmptyResult("EMBEDDED"));
    }
}
