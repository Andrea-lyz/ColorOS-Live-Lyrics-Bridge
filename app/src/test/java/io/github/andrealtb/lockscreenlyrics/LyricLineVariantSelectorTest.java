package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void keepsJapaneseMainLineWhenRomajiLaneIsMissing() {
        List<String> texts = Arrays.asList(
                "\u6211\u304c\u592a\u967d\u7cfb\u306e\u9f13\u52d5\u306b\u5408\u308f\u305b\u3066",
                "\u8ba9\u6211\u4eec\u6765\u4f34\u7740 \u592a\u9633\u7cfb\u7684\u8109\u52a8");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
    }

    @Test
    public void shortEnglishLyricLineDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "Is a beauty and a beat",
                "\u5c31\u662f\u4e00\u4e2a\u7f8e\u4eba\u548c\u4e00\u9996\u5e26\u611f\u7684\u6b4c");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertFalse(LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(texts.get(0)));
    }

    @Test
    public void shortEnglishAllSmallWordsDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "But I can see us",
                "\u4f46\u6211\u770b\u89c1\u6211\u4eec");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertFalse(LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(texts.get(0)));
    }

    @Test
    public void repeatedEnglishPhraseDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "We are, we are, we are",
                "\u6211\u4eec\u53ea\u662f");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
    }

    @Test
    public void repeatedEnglishQuestionDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "Is it? Is it?",
                "\u662f\u5417\uff1f\u662f\u5417\uff1f");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
    }

    @Test
    public void romajiOnlyLaneStillYieldsCjkTextWhenNoJapaneseSourceIsPresent() {
        List<String> texts = Arrays.asList(
                "ra i ra i ra ku ra ku ha n se n ko k ka",
                "\u5149\u660e\u78ca\u843d\u53cd\u6218\u56fd\u5bb6");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(1, primaryIndex);
        assertTrue(LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(texts.get(0)));
    }

    @Test
    public void cantonesePhoneticLaneDoesNotWinOverChineseMainLine() {
        List<String> texts = Arrays.asList(
                "\u7f20\u7ef5\u7684\u665a\u98ce \u5439\u7184\u7231\u7684\u68a6",
                "cin min di man fong  cui si oi di mong");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertTrue(LyricLineVariantSelector.isLikelyCjkPhoneticVariant(
                texts,
                primaryIndex,
                texts.get(1)));
    }

    @Test
    public void cantonesePhoneticLaneMayCopyEnglishWordsFromChineseMainLine() {
        List<String> texts = Arrays.asList(
                "\u4e3a\u4f55love is gone gone gone",
                "wai ho love is gone gone gone");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertTrue(LyricLineVariantSelector.isLikelyPhoneticVariant(
                texts,
                primaryIndex,
                texts.get(1)));
    }

    @Test
    public void shortEnglishLyricDoesNotLookLikeCantonesePhoneticLane() {
        List<String> texts = Arrays.asList(
                "But I can see us",
                "\u4f46\u6211\u770b\u89c1\u6211\u4eec");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertFalse(LyricLineVariantSelector.isLikelyCjkPhoneticVariant(
                texts,
                1,
                texts.get(0)));
    }
}
