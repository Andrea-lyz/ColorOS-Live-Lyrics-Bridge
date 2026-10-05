package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricLineBreakPolicyTest {
    private static final LyricLineBreakPolicy.WidthMeasurer CODE_UNIT_WIDTH =
            (text, start, end) -> end - start;

    @Test public void timedLyricsWithNbspKeepDoorAndYourWhole() {
        for (String plain : new String[]{"I walked through the door with you", "You taught me about your past"}) {
            String word = plain.contains("door") ? "door" : "your";
            for (char space : new char[]{' ', '\u00a0', '\u202f', '\u2007'}) {
                String text = plain.replace(' ', space);
                int wordStart = text.indexOf(word);
                int boundary = LyricLineBreakPolicy.chooseWrapEnd(text, 0, text.length(), wordStart + 2, CODE_UNIT_WIDTH);
                assertEquals("must move the whole word to the next row", wordStart, boundary);
                assertTrue(LyricLineBreakPolicy.shouldBalanceUntranslatedText(text, 0, text.length(), wordStart + 2, CODE_UNIT_WIDTH));
            }
        }
    }

    @Test public void aWordWiderThanTheWholeRowStillMakesProgress() {
        String text = "supercalifragilistic";
        assertEquals(5, LyricLineBreakPolicy.chooseWrapEnd(text, 0, text.length(), 5, CODE_UNIT_WIDTH));
    }

    @Test
    public void japaneseSentenceWithoutSpacesWrapsAtCharacterBoundary() {
        String text = "聞いて、私さ、"
                + "この前自転車に"
                + "ぶつかりそうになったの。";

        int end = LyricLineBreakPolicy.chooseWrapEnd(
                text,
                0,
                text.length(),
                12f,
                CODE_UNIT_WIDTH);

        assertTrue(end > 0);
        assertTrue(end < text.length());
    }

    @Test
    public void englishTextStillPrefersWordBoundary() {
        String text = "walk this empty street";

        int end = LyricLineBreakPolicy.chooseWrapEnd(
                text,
                0,
                text.length(),
                10f,
                CODE_UNIT_WIDTH);

        assertEquals("walk this ", text.substring(0, end));
    }

    @Test
    public void englishTextUsesTheWholeRemainingLineWhenItFits() {
        String text = "merlot on his mouth";

        int end = LyricLineBreakPolicy.chooseWrapEnd(
                text,
                0,
                text.length(),
                text.length(),
                CODE_UNIT_WIDTH);

        assertEquals(text.length(), end);
    }

    @Test
    public void fittingUntranslatedEnglishTextDoesNotBalanceAcrossTwoLines() {
        String text = "Forgive me, Peter";

        assertFalse(LyricLineBreakPolicy.shouldBalanceUntranslatedText(
                text,
                0,
                text.length(),
                text.length(),
                CODE_UNIT_WIDTH));
    }

    @Test
    public void overflowingUntranslatedEnglishTextCanBalanceAcrossTwoLines() {
        String text = "Preserved from when we were just kids";

        assertTrue(LyricLineBreakPolicy.shouldBalanceUntranslatedText(
                text,
                0,
                text.length(),
                20f,
                CODE_UNIT_WIDTH));
    }

    @Test
    public void cjkClosingPunctuationDoesNotStartNextLine() {
        String text = "聞いて私さ、この前";

        int end = LyricLineBreakPolicy.chooseWrapEnd(
                text,
                0,
                text.length(),
                5f,
                CODE_UNIT_WIDTH);

        assertEquals("聞いて私", text.substring(0, end));
        assertFalse(text.substring(end).startsWith("、"));
    }

    @Test
    public void surrogatePairIsNeverSplit() {
        String text = "生きてる🌸だけで";

        int end = LyricLineBreakPolicy.chooseWrapEnd(
                text,
                0,
                text.length(),
                6f,
                CODE_UNIT_WIDTH);

        assertFalse(Character.isHighSurrogate(text.charAt(end - 1)));
        assertFalse(end < text.length() && Character.isLowSurrogate(text.charAt(end)));
    }
}
