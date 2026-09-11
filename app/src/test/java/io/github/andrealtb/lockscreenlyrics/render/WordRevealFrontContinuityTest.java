package io.github.andrealtb.lockscreenlyrics.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;

/**
 * Pins the reveal-front timing the character float-up depends on.
 *
 * <p>Each character's height is a pure function of its distance to the front,
 * so the front's motion is the animation. A non-final word's reveal end is the
 * next word's begin ({@link WordLine#wordEndMillis}), which means a pause
 * between two words stretches the current word's sweep instead of stalling the
 * front, and the handoff introduces no jump. If that ever changes, characters
 * would freeze mid-rise through every pause and then snap, so these assertions
 * are load-bearing.
 */
public final class WordRevealFrontContinuityTest {

    @Test
    public void aPauseBetweenWordsStretchesTheSweepInsteadOfStallingIt() {
        WordLine line = twoWordLine(0L, 1_000L, 2_000L);

        assertEquals(0.5f, WordLyricRenderSupport.wordRevealProgress(null, line, 0, 500L), 1e-4f);
        assertTrue(
                "the front must still be moving right up to the next word",
                WordLyricRenderSupport.wordRevealProgress(null, line, 0, 999L) < 1f);
        assertEquals(0, line.findWordIndex(999L));
    }

    @Test
    public void aLongPauseStretchesTheSweepJustAsFar() {
        WordLine line = twoWordLine(0L, 3_000L, 6_000L);

        assertEquals(0.25f, WordLyricRenderSupport.wordRevealProgress(null, line, 0, 750L), 1e-4f);
        assertEquals(0.5f, WordLyricRenderSupport.wordRevealProgress(null, line, 0, 1_500L), 1e-4f);
        assertTrue(WordLyricRenderSupport.wordRevealProgress(null, line, 0, 2_999L) < 1f);
    }

    @Test
    public void theFrontIsContinuousAcrossTheWordHandoff() {
        WordLine line = twoWordLine(0L, 1_000L, 2_000L);

        // The last frame of word 0 has revealed nearly all of word 0's text; the
        // first frame of word 1 has revealed all of word 0 and none of word 1,
        // so the front does not jump at the boundary.
        assertTrue(WordLyricRenderSupport.wordRevealProgress(null, line, 0, 999L) > 0.99f);
        assertEquals(1, line.findWordIndex(1_000L));
        assertEquals(0f, WordLyricRenderSupport.wordRevealProgress(null, line, 1, 1_000L), 1e-6f);
    }

    @Test
    public void onlyTheFinalWordLeavesTheFrontStanding() {
        WordLine line = twoWordLine(0L, 1_000L, 2_000L);

        long end = WordLyricRenderSupport.wordRevealEndMillis(null, line, 1);
        assertTrue("the last word must settle before the row does", end < line.endTimeMillis);
        assertEquals(1f, WordLyricRenderSupport.wordRevealProgress(null, line, 1, end), 1e-6f);
        assertEquals(
                "this is the one stall the lift has to decay out of",
                1f,
                WordLyricRenderSupport.wordRevealProgress(null, line, 1, line.endTimeMillis),
                1e-6f);
    }

    /** Two single-character words, the second beginning at {@code secondBegin}. */
    private static WordLine twoWordLine(long firstBegin, long secondBegin, long lineEnd) {
        ArrayList<WordRange> words = new ArrayList<>();
        words.add(new WordRange(firstBegin, 0, 1));
        words.add(new WordRange(secondBegin, 1, 2));
        return new WordLine(firstBegin, "ab", words, lineEnd);
    }
}
