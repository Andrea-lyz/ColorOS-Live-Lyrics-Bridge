package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricParsingRegressionMatrixTest {
    @Test
    public void noBodyNoCrimeBrokenWordTimingKeepsCompleteBilingualLine() {
        String lrc = "[00:06.490]He [00:06.850]did [00:07.390]it[00:07.630]\n"
                + "[00:06.490]\u4ed6\u80cc\u53db\u4e86\u6211\n"
                + "[00:09.400]He [00:09.760]did [00:25.060]it[00:25.540]\n"
                + "[00:09.400]\u4ed6\u80cc\u53db\u4e86\u6211\n"
                + "[00:25.060]I [00:25.430]think [00:25.820]he "
                + "[00:26.120]did [00:26.430]it[00:26.880]\n"
                + "[00:25.060]\u6211\u89c9\u5f97\u662f\u4ed6\u5e72\u7684";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);

        assertParsedLine(parsed, 9_400L, "He did it", "\u4ed6\u80cc\u53db\u4e86\u6211");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 9_400L)
                .findFirst()
                .orElseThrow();
        assertEquals("He did it", line.text);
        assertEquals("\u4ed6\u80cc\u53db\u4e86\u6211", line.translation);
        assertEquals(1, line.words.size());
    }

    @Test
    public void deathByAThousandCutsOpeningKeepsEnglishMainAndDropsCredits() {
        String lrc = "[by:Trap_Girl]\n"
                + "[00:00.00]\u4f5c\u8bcd : Taylor Swift/Jack Antonoff\n"
                + "[00:00.09]\u4f5c\u66f2 : Taylor Swift/Jack Antonoff\n"
                + "[00:00.18]My, my, my, my\n"
                + "[00:00.18]\u53ea\u5c5e\u4e8e\u6211\n";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        String official = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertParsedLine(parsed, 180L, "My, my, my, my", "\u53ea\u5c5e\u4e8e\u6211");
        assertTrue(official.contains("My, my, my, my"));
        assertFalse(official.contains("\u4f5c\u8bcd"));
        assertFalse(official.contains("\u4f5c\u66f2"));
        assertFalse(official.contains("\u53ea\u5c5e\u4e8e\u6211"));
    }

    @Test
    public void providerCreditAndMissingTranslationDoNotShiftLanes() {
        String lrc = "[00:01.00]First line\n"
                + "[00:01.00]\u7b2c\u4e00\u884c\n"
                + "[00:02.00]\u4ee5\u4e0b\u6b4c\u8bcd\u7ffb\u8bd1\u7531 Someone \u63d0\u4f9b\n"
                + "[00:03.00]Missing translation line\n"
                + "[00:05.00]Final line\n"
                + "[00:05.00]\u6700\u540e\u4e00\u884c\n";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);

        assertEquals(3, parsed.lines.size());
        assertParsedLine(parsed, 1_000L, "First line", "\u7b2c\u4e00\u884c");
        assertParsedLine(parsed, 3_000L, "Missing translation line", "");
        assertParsedLine(parsed, 5_000L, "Final line", "\u6700\u540e\u4e00\u884c");
    }

    @Test
    public void bejeweledEnhancedLineKeepsEnglishMainLine() {
        String lrc = "[01:15.691] <01:15.691>Have <01:15.838>you "
                + "<01:16.008>heard<01:16.401>\n"
                + "[01:15.691]\u4f60\u6709\u6ca1\u6709\u7528\u5fc3\u503e\u542c";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);
        String official = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertParsedLine(
                parsed,
                75_691L,
                "Have you heard",
                "\u4f60\u6709\u6ca1\u6709\u7528\u5fc3\u503e\u542c");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 75_691L)
                .findFirst()
                .orElseThrow();
        assertEquals("Have you heard", line.text);
        assertEquals("\u4f60\u6709\u6ca1\u6709\u7528\u5fc3\u503e\u542c", line.translation);
        assertTrue(line.words.size() > 1);
        assertEquals("[00:00.000]Have you heard\n[01:23.691]\u200B", official);
    }

    @Test
    public void coneyIslandEnhancedLineKeepsEnglishMainLine() {
        String lrc = "[01:10.060] <01:10.060>Over <01:10.810>and "
                + "<01:11.080>over<01:12.880>\n"
                + "[01:10.060]\u4e00\u6b21\u53c8\u4e00\u6b21";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);
        String official = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertParsedLine(
                parsed,
                70_060L,
                "Over and over",
                "\u4e00\u6b21\u53c8\u4e00\u6b21");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 70_060L)
                .findFirst()
                .orElseThrow();
        assertEquals("Over and over", line.text);
        assertEquals("\u4e00\u6b21\u53c8\u4e00\u6b21", line.translation);
        assertTrue(line.words.size() > 1);
        assertEquals("[00:00.000]Over and over\n[01:18.060]\u200B", official);
    }

    @Test
    public void neteaseBracketWordTimedLineKeepsTrueWordTiming() {
        String lrc = "[00:46.230]Girl [00:46.470]tell [00:46.770]me "
                + "[00:47.010]what [00:47.280]you [00:47.460]want"
                + "[00:48.120], [00:48.120]what [00:48.690]you "
                + "[00:48.870]want[00:50.340]\n"
                + "[00:46.230]\u5b9d\u8d1d\u544a\u8bc9\u6211\u4f60\u60f3\u8981\u4ec0\u4e48";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);

        assertParsedLine(
                parsed,
                46_230L,
                "Girl tell me what you want, what you want",
                "\u5b9d\u8d1d\u544a\u8bc9\u6211\u4f60\u60f3\u8981\u4ec0\u4e48");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 46_230L)
                .findFirst()
                .orElseThrow();
        assertEquals("Girl tell me what you want, what you want", line.text);
        assertEquals("\u5b9d\u8d1d\u544a\u8bc9\u6211\u4f60\u60f3\u8981\u4ec0\u4e48", line.translation);
        assertTrue(line.words.size() > 1);
        assertEquals(46_230L, line.words.get(0).startMillis);
        assertEquals(48_870L, line.words.get(line.words.size() - 1).startMillis);
    }

    @Test
    public void neteaseEnhancedWordTimedLineKeepsTrueWordTiming() {
        String lrc = "[00:46.230] <00:46.230>Girl <00:46.470>tell "
                + "<00:46.770>me <00:47.010>what <00:47.280>you "
                + "<00:47.460>want<00:48.120>, <00:48.120>what "
                + "<00:48.690>you <00:48.870>want<00:50.340>\n"
                + "[00:46.230]\u5b9d\u8d1d\u544a\u8bc9\u6211\u4f60\u60f3\u8981\u4ec0\u4e48";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);

        assertParsedLine(
                parsed,
                46_230L,
                "Girl tell me what you want, what you want",
                "\u5b9d\u8d1d\u544a\u8bc9\u6211\u4f60\u60f3\u8981\u4ec0\u4e48");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 46_230L)
                .findFirst()
                .orElseThrow();
        assertEquals("Girl tell me what you want, what you want", line.text);
        assertEquals("\u5b9d\u8d1d\u544a\u8bc9\u6211\u4f60\u60f3\u8981\u4ec0\u4e48", line.translation);
        assertTrue(line.words.size() > 1);
        assertEquals(46_230L, line.words.get(0).startMillis);
        assertEquals(48_870L, line.words.get(line.words.size() - 1).startMillis);
    }

    @Test
    public void shortEnglishLineIsNotMistakenForRomajiWhenTranslationComesFirst() {
        String translation = "\u6211\u66fe\u770b\u5230\u5982\u4eca\u9677\u5165\u8ff7\u5931";
        String lrc = "[01:58.780]" + translation + "\n"
                + "[01:58.780]I saw, now I am blind";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);
        String official = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals(1, parsed.lines.size());
        assertParsedLine(parsed, 118_780L, "I saw, now I am blind", translation);
        assertTrue(official.contains("I saw, now I am blind"));
        assertFalse(official.contains(translation));
    }

    private static void assertParsedLine(
            LyricsCoreAdapter.ParsedLyrics parsed,
            long startMillis,
            String expectedText,
            String expectedTranslation) {
        LyricsCoreAdapter.ParsedLine line = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == startMillis)
                .findFirst()
                .orElseThrow();
        assertEquals(expectedText, line.text);
        assertEquals(expectedTranslation, line.translation);
    }
}
