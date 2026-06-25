package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public final class LyricLineVariantSelectorTest {
    @Test
    public void keepsKanjiLineBeforeRomajiWhenBothContainTrailingAcronym() {
        List<String> texts = Arrays.asList(
                "\u60aa\u970a\u9000\u6563 ICBM",
                "a ku ryo u ta i sa n ICBM",
                "\u6076\u7075\u9000\u6563 ICBM");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertTrue(LyricLineVariantSelector.isLikelyJapaneseRomanizationVariant(
                texts,
                primaryIndex,
                texts.get(1)));
    }

    @Test
    public void restoresSharedTrailingAcronymWhenPrimaryLineDroppedIt() {
        List<String> texts = Arrays.asList(
                "\u60aa\u970a\u9000\u6563",
                "a ku ryo u ta i sa n ICBM",
                "\u6076\u7075\u9000\u6563 ICBM");

        String suffix = LyricLineVariantSelector.findSharedTrailingLatinToken(texts, 0);

        assertEquals("ICBM", suffix);
        assertEquals("\u60aa\u970a\u9000\u6563 ICBM",
                LyricLineVariantSelector.appendLatinSuffix(texts.get(0), suffix));
    }
}
