package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public final class LyricLaneClassifierTest {
    @Test
    public void englishMainChineseTranslationAndProviderCreditUseSeparateLanes() {
        LyricLaneClassifier.Result lanes = LyricLaneClassifier.classify(
                Arrays.asList(
                        "He did it",
                        "他背叛了我",
                        "以下歌词翻译由 Salt Player 提供"),
                6_490L);

        assertEquals(0, lanes.primaryIndex());
        assertEquals(LyricLaneClassifier.Lane.MAIN, lanes.laneAt(0));
        assertEquals(LyricLaneClassifier.Lane.TRANSLATION, lanes.laneAt(1));
        assertEquals(LyricLaneClassifier.Lane.CREDIT, lanes.laneAt(2));
        assertEquals("他背叛了我", lanes.firstTranslation());
    }

    @Test
    public void shortEnglishQuestionWinsOverChineseTranslation() {
        LyricLaneClassifier.Result lanes = LyricLaneClassifier.classify(
                Arrays.asList(
                        "Have you heard",
                        "你有没有用心倾听"),
                75_691L);

        assertEquals(0, lanes.primaryIndex());
        assertEquals(LyricLaneClassifier.Lane.MAIN, lanes.laneAt(0));
        assertEquals(LyricLaneClassifier.Lane.TRANSLATION, lanes.laneAt(1));
    }

    @Test
    public void repeatedShortEnglishPhraseWinsOverChineseTranslation() {
        LyricLaneClassifier.Result lanes = LyricLaneClassifier.classify(
                Arrays.asList(
                        "Over and over",
                        "一次又一次"),
                70_060L);

        assertEquals(0, lanes.primaryIndex());
        assertEquals(LyricLaneClassifier.Lane.MAIN, lanes.laneAt(0));
        assertEquals(LyricLaneClassifier.Lane.TRANSLATION, lanes.laneAt(1));
    }

    @Test
    public void pinyinVariantDoesNotWinOverChineseMainLine() {
        LyricLaneClassifier.Result lanes = LyricLaneClassifier.classify(
                Arrays.asList(
                        "你有没有",
                        "you mei you"),
                12_000L);

        assertEquals(0, lanes.primaryIndex());
        assertEquals(LyricLaneClassifier.Lane.MAIN, lanes.laneAt(0));
        assertEquals(LyricLaneClassifier.Lane.ROMANIZATION, lanes.laneAt(1));
    }

    @Test
    public void japaneseMainChineseTranslationAndRomajiAreSeparated() {
        LyricLaneClassifier.Result lanes = LyricLaneClassifier.classify(
                Arrays.asList(
                        "こんな私の未熟なうたを",
                        "感谢你愿意聆听",
                        "ko n na wa ta shi no mi ju ku na u ta wo"),
                30_436L);

        assertEquals(0, lanes.primaryIndex());
        assertEquals(LyricLaneClassifier.Lane.MAIN, lanes.laneAt(0));
        assertEquals(LyricLaneClassifier.Lane.TRANSLATION, lanes.laneAt(1));
        assertEquals(LyricLaneClassifier.Lane.ROMANIZATION, lanes.laneAt(2));
    }

    @Test
    public void parentheticalSourceVariantIsNotTranslation() {
        LyricLaneClassifier.Result lanes = LyricLaneClassifier.classify(
                Arrays.asList(
                        "Put your lips close to mine",
                        "Put your lips close to mine (close to mine)"),
                1_200L);

        assertEquals(0, lanes.primaryIndex());
        assertEquals(LyricLaneClassifier.Lane.MAIN, lanes.laneAt(0));
        assertEquals(LyricLaneClassifier.Lane.SOURCE_VARIANT, lanes.laneAt(1));
        assertEquals("", lanes.firstTranslation());
    }
}
