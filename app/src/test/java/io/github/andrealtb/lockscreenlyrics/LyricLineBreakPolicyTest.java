package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricLineBreakPolicyTest {
    private static final LyricLineBreakPolicy.WidthMeasurer CODE_UNIT_WIDTH =
            (text, start, end) -> end - start;

    @Test
    public void japaneseSentenceWithoutSpacesWrapsAtCharacterBoundary() {
        String text = "\u805e\u3044\u3066\u3001\u79c1\u3055\u3001"
                + "\u3053\u306e\u524d\u81ea\u8ee2\u8eca\u306b"
                + "\u3076\u3064\u304b\u308a\u305d\u3046\u306b\u306a\u3063\u305f\u306e\u3002";

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
    public void cjkClosingPunctuationDoesNotStartNextLine() {
        String text = "\u805e\u3044\u3066\u79c1\u3055\u3001\u3053\u306e\u524d";

        int end = LyricLineBreakPolicy.chooseWrapEnd(
                text,
                0,
                text.length(),
                5f,
                CODE_UNIT_WIDTH);

        assertEquals("\u805e\u3044\u3066\u79c1", text.substring(0, end));
        assertFalse(text.substring(end).startsWith("\u3001"));
    }

    @Test
    public void surrogatePairIsNeverSplit() {
        String text = "\u751f\u304d\u3066\u308b\ud83c\udf38\u3060\u3051\u3067";

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
