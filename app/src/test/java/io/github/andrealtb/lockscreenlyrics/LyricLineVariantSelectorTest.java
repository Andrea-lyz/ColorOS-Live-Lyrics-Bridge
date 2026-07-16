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
                "悪霊退散 ICBM",
                "a ku ryo u ta i sa n ICBM",
                "恶灵退散 ICBM");

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
                "悪霊退散",
                "a ku ryo u ta i sa n ICBM",
                "恶灵退散 ICBM");

        String suffix = LyricLineVariantSelector.findSharedTrailingLatinToken(texts, 0);

        assertEquals("ICBM", suffix);
        assertEquals("悪霊退散 ICBM",
                LyricLineVariantSelector.appendLatinSuffix(texts.get(0), suffix));
    }

    @Test
    public void keepsJapaneseMainLineWhenRomajiLaneIsMissing() {
        List<String> texts = Arrays.asList(
                "我が太陽系の鼓動に合わせて",
                "让我们来伴着 太阳系的脉动");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
    }

    @Test
    public void shortEnglishLyricLineDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "Is a beauty and a beat",
                "就是一个美人和一首带感的歌");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertFalse(LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(texts.get(0)));
    }

    @Test
    public void shortEnglishAllSmallWordsDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "But I can see us",
                "但我看见我们");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertFalse(LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(texts.get(0)));
    }

    @Test
    public void repeatedEnglishPhraseDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "We are, we are, we are",
                "我们只是");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
    }

    @Test
    public void repeatedEnglishPossessiveDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "My, my, my, my",
                "只属于我");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
    }

    @Test
    public void repeatedYouLyricDoesNotBecomeChineseTranslationPhoneticText() {
        List<String> texts = Arrays.asList(
                "You you",
                "你 你");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertFalse(LyricLineVariantSelector.isLikelyCjkPhoneticVariant(
                texts,
                1,
                texts.get(0)));
    }

    @Test
    public void repeatedEnglishQuestionDoesNotLoseToChineseTranslationAsRomaji() {
        List<String> texts = Arrays.asList(
                "Is it? Is it?",
                "是吗？是吗？");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
    }

    @Test
    public void romajiOnlyLaneStillYieldsCjkTextWhenNoJapaneseSourceIsPresent() {
        List<String> texts = Arrays.asList(
                "ra i ra i ra ku ra ku ha n se n ko k ka",
                "光明磊落反战国家");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(1, primaryIndex);
        assertTrue(LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(texts.get(0)));
    }

    @Test
    public void cantonesePhoneticLaneDoesNotWinOverChineseMainLine() {
        List<String> texts = Arrays.asList(
                "缠绵的晚风 吹熄爱的梦",
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
                "为何love is gone gone gone",
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
                "但我看见我们");

        int primaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(texts);

        assertEquals(0, primaryIndex);
        assertFalse(LyricLineVariantSelector.isLikelyCjkPhoneticVariant(
                texts,
                1,
                texts.get(0)));
    }
}
