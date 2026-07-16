package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricTextSanitizerTest {
    @Test
    public void removesInvisibleFormattingCharacters() {
        assertEquals(
                "main translation",
                LyricTextSanitizer.removeIgnorableCharacters(
                        "" + LyricTextSanitizer.codePointString(LyricTextSanitizer.BYTE_ORDER_MARK_CODE_POINT) + "main" + LyricTextSanitizer.ZERO_WIDTH_SPACE + " translation" + LyricTextSanitizer.codePointString(LyricTextSanitizer.WORD_JOINER_CODE_POINT) + ""));
    }

    @Test
    public void returnsOriginalStringWhenNoCleanupIsNeeded() {
        String lyric = "And all of the foes and all of the friends";

        assertTrue(lyric == LyricTextSanitizer.removeIgnorableCharacters(lyric));
    }
}
