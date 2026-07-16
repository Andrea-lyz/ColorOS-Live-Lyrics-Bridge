package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricParsingRegressionMatrixTest {
    @Test
    public void noBodyNoCrimeBrokenWordTimingKeepsCompleteBilingualLine() {
        String lrc = "[00:06.490]He [00:06.850]did [00:07.390]it[00:07.630]\n"
                + "[00:06.490]他背叛了我\n"
                + "[00:09.400]He [00:09.760]did [00:25.060]it[00:25.540]\n"
                + "[00:09.400]他背叛了我\n"
                + "[00:25.060]I [00:25.430]think [00:25.820]he "
                + "[00:26.120]did [00:26.430]it[00:26.880]\n"
                + "[00:25.060]我觉得是他干的";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);

        assertParsedLine(parsed, 9_400L, "He did it", "他背叛了我");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 9_400L)
                .findFirst()
                .orElseThrow();
        assertEquals("He did it", line.text);
        assertEquals("他背叛了我", line.translation);
        assertEquals(1, line.words.size());
    }

    @Test
    public void deathByAThousandCutsOpeningKeepsCreditsAsCleanupCandidates() {
        String lrc = "[by:Trap_Girl]\n"
                + "[00:00.00]作词 : Taylor Swift/Jack Antonoff\n"
                + "[00:00.09]作曲 : Taylor Swift/Jack Antonoff\n"
                + "[00:00.18]My, my, my, my\n"
                + "[00:00.18]只属于我\n";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        String official = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertParsedLine(parsed, 180L, "My, my, my, my", "只属于我");
        assertTrue(official.contains("My, my, my, my"));
        assertTrue(official.contains("作词"));
        assertTrue(official.contains("作曲"));
        assertFalse(official.contains("只属于我"));
    }

    @Test
    public void providerCreditAndMissingTranslationDoNotShiftLanes() {
        String lrc = "[00:01.00]First line\n"
                + "[00:01.00]第一行\n"
                + "[00:02.00]以下歌词翻译由 Someone 提供\n"
                + "[00:03.00]Missing translation line\n"
                + "[00:05.00]Final line\n"
                + "[00:05.00]最后一行\n";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);

        assertEquals(3, parsed.lines.size());
        assertParsedLine(parsed, 1_000L, "First line", "第一行");
        assertParsedLine(parsed, 3_000L, "Missing translation line", "");
        assertParsedLine(parsed, 5_000L, "Final line", "最后一行");
    }

    @Test
    public void bejeweledEnhancedLineKeepsEnglishMainLine() {
        String lrc = "[01:15.691] <01:15.691>Have <01:15.838>you "
                + "<01:16.008>heard<01:16.401>\n"
                + "[01:15.691]你有没有用心倾听";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);
        String official = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertParsedLine(
                parsed,
                75_691L,
                "Have you heard",
                "你有没有用心倾听");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 75_691L)
                .findFirst()
                .orElseThrow();
        assertEquals("Have you heard", line.text);
        assertEquals("你有没有用心倾听", line.translation);
        assertTrue(line.words.size() > 1);
        assertEquals("[00:00.000]Have you heard\n[01:23.691]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", official);
    }

    @Test
    public void coneyIslandEnhancedLineKeepsEnglishMainLine() {
        String lrc = "[01:10.060] <01:10.060>Over <01:10.810>and "
                + "<01:11.080>over<01:12.880>\n"
                + "[01:10.060]一次又一次";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);
        String official = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertParsedLine(
                parsed,
                70_060L,
                "Over and over",
                "一次又一次");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 70_060L)
                .findFirst()
                .orElseThrow();
        assertEquals("Over and over", line.text);
        assertEquals("一次又一次", line.translation);
        assertTrue(line.words.size() > 1);
        assertEquals("[00:00.000]Over and over\n[01:18.060]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", official);
    }

    @Test
    public void neteaseBracketWordTimedLineKeepsTrueWordTiming() {
        String lrc = "[00:46.230]Girl [00:46.470]tell [00:46.770]me "
                + "[00:47.010]what [00:47.280]you [00:47.460]want"
                + "[00:48.120], [00:48.120]what [00:48.690]you "
                + "[00:48.870]want[00:50.340]\n"
                + "[00:46.230]宝贝告诉我你想要什么";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);

        assertParsedLine(
                parsed,
                46_230L,
                "Girl tell me what you want, what you want",
                "宝贝告诉我你想要什么");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 46_230L)
                .findFirst()
                .orElseThrow();
        assertEquals("Girl tell me what you want, what you want", line.text);
        assertEquals("宝贝告诉我你想要什么", line.translation);
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
                + "[00:46.230]宝贝告诉我你想要什么";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);

        assertParsedLine(
                parsed,
                46_230L,
                "Girl tell me what you want, what you want",
                "宝贝告诉我你想要什么");
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == 46_230L)
                .findFirst()
                .orElseThrow();
        assertEquals("Girl tell me what you want, what you want", line.text);
        assertEquals("宝贝告诉我你想要什么", line.translation);
        assertTrue(line.words.size() > 1);
        assertEquals(46_230L, line.words.get(0).startMillis);
        assertEquals(48_870L, line.words.get(line.words.size() - 1).startMillis);
    }

    @Test
    public void shortEnglishLineIsNotMistakenForRomajiWhenTranslationComesFirst() {
        String translation = "我曾看到如今陷入迷失";
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
