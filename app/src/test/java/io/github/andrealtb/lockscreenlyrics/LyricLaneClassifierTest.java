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
                        "\u4ed6\u80cc\u53db\u4e86\u6211",
                        "\u4ee5\u4e0b\u6b4c\u8bcd\u7ffb\u8bd1\u7531 Salt Player \u63d0\u4f9b"),
                6_490L);

        assertEquals(0, lanes.primaryIndex());
        assertEquals(LyricLaneClassifier.Lane.MAIN, lanes.laneAt(0));
        assertEquals(LyricLaneClassifier.Lane.TRANSLATION, lanes.laneAt(1));
        assertEquals(LyricLaneClassifier.Lane.CREDIT, lanes.laneAt(2));
        assertEquals("\u4ed6\u80cc\u53db\u4e86\u6211", lanes.firstTranslation());
    }

    @Test
    public void shortEnglishQuestionWinsOverChineseTranslation() {
        LyricLaneClassifier.Result lanes = LyricLaneClassifier.classify(
                Arrays.asList(
                        "Have you heard",
                        "\u4f60\u6709\u6ca1\u6709\u7528\u5fc3\u503e\u542c"),
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
                        "\u4e00\u6b21\u53c8\u4e00\u6b21"),
                70_060L);

        assertEquals(0, lanes.primaryIndex());
        assertEquals(LyricLaneClassifier.Lane.MAIN, lanes.laneAt(0));
        assertEquals(LyricLaneClassifier.Lane.TRANSLATION, lanes.laneAt(1));
    }

    @Test
    public void pinyinVariantDoesNotWinOverChineseMainLine() {
        LyricLaneClassifier.Result lanes = LyricLaneClassifier.classify(
                Arrays.asList(
                        "\u4f60\u6709\u6ca1\u6709",
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
                        "\u3053\u3093\u306a\u79c1\u306e\u672a\u719f\u306a\u3046\u305f\u3092",
                        "\u611f\u8c22\u4f60\u613f\u610f\u8046\u542c",
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
