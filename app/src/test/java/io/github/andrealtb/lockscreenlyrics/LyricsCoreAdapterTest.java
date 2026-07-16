package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class LyricsCoreAdapterTest {
    @Test
    public void parsesYrcWordTimingRegression() {
        String yrc = "[1200,2400](1200,600,0)逐(1800,600,0)字"
                + "(2400,600,0)歌(3000,600,0)词\n"
                + "[4200,1200](4200,600,0)安(4800,600,0)全";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(yrc);

        assertEquals(2, parsed.lines.size());
        LyricsCoreAdapter.ParsedLine first = parsed.lines.get(0);
        assertEquals(1_200L, first.startMillis);
        assertEquals(3_600L, first.endMillis);
        assertEquals("逐字歌词", first.text);
        assertEquals(4, first.syllables.size());
        assertEquals(1_200L, first.syllables.get(0).startMillis);
        assertEquals(1_800L, first.syllables.get(0).endMillis);
        assertEquals(0, first.syllables.get(0).start);
        assertEquals(1, first.syllables.get(0).end);
        assertEquals(3_000L, first.syllables.get(3).startMillis);
        assertEquals(3_600L, first.syllables.get(3).endMillis);
        assertEquals(3, first.syllables.get(3).start);
        assertEquals(4, first.syllables.get(3).end);
    }

    @Test
    public void parsesSameTimestampBilingualLrc() {
        String lrc = "[00:00.00]One two（ワン　ツー）\n"
                + "[00:12.90]エマージェンシー　0時　奴らは\n"
                + "[00:12.90]紧急状况  零点 他们在\n"
                + "[00:16.00]クレイジー・インザ・タウン　家に篭って\n"
                + "[00:16.00]crazy in the town  窝在家里";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertEquals(3, parsed.lines.size());
        assertEquals(12_900L, parsed.lines.get(1).startMillis);
        assertEquals("エマージェンシー　0時　奴らは", parsed.lines.get(1).text);
        assertEquals("紧急状况  零点 他们在", parsed.lines.get(1).translation);
        assertEquals("crazy in the town  窝在家里", parsed.lines.get(2).translation);
    }

    @Test
    public void parsesGoodbyeDeclarationRegressionFile() throws Exception {
        String testFile = System.getProperty("lyrics.test.file", "");
        assumeTrue("lyrics.test.file was not supplied", !testFile.isEmpty());
        String lrc = new String(
                Files.readAllBytes(Paths.get(testFile)),
                StandardCharsets.UTF_8);

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertTrue("expected the complete song rather than a Latin-only subset",
                parsed.lines.size() >= 20);
        LyricsCoreAdapter.ParsedLine line = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 12_900L)
                .findFirst()
                .orElseThrow();
        assertEquals("エマージェンシー　0時　奴らは", line.text);
        assertEquals("紧急状况  零点 他们在", line.translation);
        long translations = parsed.lines.stream()
                .filter(candidate -> !candidate.translation.isEmpty())
                .count();
        assertTrue("expected same-timestamp Chinese lines to become translations",
                translations >= 20);
    }

    @Test
    public void parsesDorotheaWordLrcMainLines() throws Exception {
        String testFile = System.getProperty("lyrics.dorothea.file", "");
        assumeTrue("lyrics.dorothea.file was not supplied", !testFile.isEmpty());
        String lrc = new String(
                Files.readAllBytes(Paths.get(testFile)),
                StandardCharsets.UTF_8);

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertTrue("expected Dorothea to parse into the main lyric lines",
                parsed.lines.stream().anyMatch(line ->
                        line.text.contains("Hey Dorothea do you ever stop and think about me")));
        assertTrue("expected Dorothea same-timestamp Chinese lines to become translations",
                parsed.lines.stream().filter(line -> !line.translation.isEmpty()).count() >= 10);
    }

    @Test
    public void plainLrcFallbackPairsSameTimestampTraditionalChineseTranslation() {
        String lrc = "[00:18.67]Kitsune maison\n"
                + "[00:20.13]I'ma grow my hair\n"
                + "[00:20.13]我要留長頭髮\n"
                + "[00:22.98]Put the money in the bag\n"
                + "[00:22.98]把錢裝進口袋裡";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);

        assertEquals(3, parsed.lines.size());
        assertEquals("I'ma grow my hair", parsed.lines.get(1).text);
        assertEquals("我要留長頭髮", parsed.lines.get(1).translation);
        assertEquals("把錢裝進口袋裡", parsed.lines.get(2).translation);
    }

    @Test
    public void plainLrcFallbackSkipsLyricTranslationProviderCredit() {
        String lrc = "[00:01.00]He did it\n"
                + "[00:01.00]他背叛了我\n"
                + "[00:01.00]以下歌词翻译由 Salt Player 提供\n"
                + "[00:04.00]No, no body, no crime";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);

        assertEquals(2, parsed.lines.size());
        assertEquals("He did it", parsed.lines.get(0).text);
        assertEquals("他背叛了我", parsed.lines.get(0).translation);
        assertEquals("No, no body, no crime", parsed.lines.get(1).text);
        assertEquals("", parsed.lines.get(1).translation);
    }

    @Test
    public void plainLrcFallbackIgnoresJapaneseRomajiVariant() {
        String lrc = "[00:30.436]こんな私の未熟なうたを\n"
                + "[00:30.436]感谢你愿意聆听\n"
                + "[00:30.436]ko n na wa ta shi no mi ju ku na u ta wo";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);

        assertEquals(1, parsed.lines.size());
        assertEquals("こんな私の未熟なうたを", parsed.lines.get(0).text);
        assertEquals("感谢你愿意聆听", parsed.lines.get(0).translation);
    }

    @Test
    public void plainLrcFallbackIgnoresCantonesePhoneticVariant() {
        String lrc = "[00:03.909]缠[00:04.085]绵[00:04.261]的"
                + "[00:04.429]晚[00:04.669]风 [00:06.189]吹"
                + "[00:06.413]熄[00:06.749]爱[00:06.957]的"
                + "[00:07.317]梦[00:07.685]\n"
                + "[00:03.909]cin min di man fong  cui si oi di mong \n"
                + "[00:08.637]为[00:08.821]何[00:09.005]love "
                + "[00:09.237]is [00:09.405]gone [00:10.013]gone "
                + "[00:10.557]gone[00:10.853]\n"
                + "[00:08.637]wai ho love is gone gone gone";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);

        assertEquals(2, parsed.lines.size());
        assertEquals("缠绵的晚风 吹熄爱的梦",
                parsed.lines.get(0).text);
        assertEquals("", parsed.lines.get(0).translation);
        assertEquals("为何love is gone gone gone", parsed.lines.get(1).text);
        assertEquals("", parsed.lines.get(1).translation);
    }

    @Test
    public void keepsJapaneseMainLineWhenRomajiIsMissingForOneTimestamp() {
        String lrc = "[00:00.850]あの一等星のさんざめく光で\n"
                + "[00:00.850]a no i tto u se i no sa n za me ku hi ka ri de\n"
                + "[00:00.850]在那一等星的喧嚣光芒之下\n"
                + "[00:07.230]我が太陽系の鼓動に合わせて\n"
                + "[00:07.230]让我们来伴着 太阳系的脉动";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        LyricsCoreAdapter.ParsedLine line = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 7_230L)
                .findFirst()
                .orElseThrow();
        assertEquals("我が太陽系の鼓動に合わせて", line.text);
        assertEquals("让我们来伴着 太阳系的脉动",
                line.translation);
    }

    @Test
    public void prefersChineseTranslationOverJapaneseRomajiLane() {
        String lrc = "[00:37.447]磊[00:37.825]々[00:38.223]落"
                + "[00:38.600]々[00:39.023] [00:39.023]反"
                + "[00:39.415]戦[00:39.799]国[00:40.151]家"
                + "[00:40.351]\n"
                + "[00:37.447]ra i ra i ra ku ra ku   ha n se n ko 'k ka \n"
                + "[00:37.447]光明磊落反战国家";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertEquals(1, parsed.lines.size());
        assertEquals("磊々落々 反戦国家",
                parsed.lines.get(0).text);
        assertEquals("光明磊落反战国家",
                parsed.lines.get(0).translation);
    }

    @Test
    public void keepsKanjiMainLineWhenRomajiLaneSharesTrailingLatinAcronym() {
        String lrc = "[00:43.688]悪[00:44.088]霊[00:44.423]退"
                + "[00:44.871]散[00:45.111] [00:45.231]ICBM"
                + "[00:46.584]\n"
                + "[00:43.688]a ku ryo u ta i sa n ICBM \n"
                + "[00:43.688]恶灵退散 ICBM";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertEquals(1, parsed.lines.size());
        assertEquals("悪霊退散 ICBM", parsed.lines.get(0).text);
        assertEquals("恶灵退散 ICBM", parsed.lines.get(0).translation);
    }

    @Test
    public void parsesAdoSenbonzakuraRegressionFileWhenSupplied() throws Exception {
        String testFile = System.getProperty("lyrics.ado.file", "");
        assumeTrue("lyrics.ado.file was not supplied", !testFile.isEmpty());
        String lrc = new String(
                Files.readAllBytes(Paths.get(testFile)),
                StandardCharsets.UTF_8);

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        LyricsCoreAdapter.ParsedLine line = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 43_688L)
                .findFirst()
                .orElseThrow();
        assertEquals("悪霊退散 ICBM", line.text);
        assertEquals("恶灵退散 ICBM", line.translation);
    }

    @Test
    public void ignoresZeroWidthSpacerBeforeBilingualLine() {
        String lrc = "[00:30.00]Before\n"
                + "[00:38.13]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "\n"
                + "[00:38.15]And all of the foes and all of the friends\n"
                + "[00:38.15]所有的对手 "
                + "所有的朋友" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertTrue(parsed.lines.stream().noneMatch(line -> line.startMillis == 38_130L));
        LyricsCoreAdapter.ParsedLine line = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 38_150L)
                .findFirst()
                .orElseThrow();
        assertEquals("And all of the foes and all of the friends", line.text);
        assertEquals("所有的对手 所有的朋友",
                line.translation);
    }

    @Test
    public void parsesKitsuneRegressionFileWhenSupplied() throws Exception {
        String testFile = System.getProperty("lyrics.kitsune.file", "");
        assumeTrue("lyrics.kitsune.file was not supplied", !testFile.isEmpty());
        String lrc = new String(
                Files.readAllBytes(Paths.get(testFile)),
                StandardCharsets.UTF_8);

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertTrue("expected the complete Kitsune lyric", parsed.lines.size() >= 60);
        assertTrue("expected same-timestamp translations",
                parsed.lines.stream().filter(line -> !line.translation.isEmpty()).count() >= 40);
        LyricsCoreAdapter.ParsedLine line = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 28_830L)
                .findFirst()
                .orElseThrow();
        assertEquals("I'ma grow my hair", line.text);
        assertEquals("我要留長頭髮", line.translation);
    }

    @Test
    public void parsesActuallyRomanticLongLinesWhenSupplied() throws Exception {
        String testFile = System.getenv("LYRICS_ACTUALLY_FILE");
        assumeTrue("LYRICS_ACTUALLY_FILE was not supplied",
                testFile != null && !testFile.isEmpty());
        String lrc = new String(
                Files.readAllBytes(Paths.get(testFile)),
                StandardCharsets.UTF_8);

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        LyricsCoreAdapter.ParsedLine first = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 6_790L)
                .findFirst()
                .orElseThrow();
        assertEquals(
                "I heard you call me \"Boring Barbie\" when the coke's got you brave",
                first.text);
        LyricsCoreAdapter.ParsedLine third = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 18_070L)
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Wrote me a song saying it makes you sick to see my face",
                third.text);
    }

    @Test
    public void keepsEnglishEnhancedLineAsPrimaryWhenChineseTranslationSharesTimestamp() {
        assertEnhancedBilingualLine(
                "[00:43.734] <00:43.734>Treat <00:43.931>me "
                        + "<00:44.115>like <00:44.307>a <00:44.480>lady "
                        + "<00:45.636>all <00:45.838>that <00:46.035>I "
                        + "<00:46.226>can <00:46.395>say <00:46.835>is<00:47.387>\n"
                        + "[00:43.734]你对我绅士礼貌 "
                        + "对此我只能说",
                43_734L,
                "Treat me like a lady all that I can say is",
                "你对我绅士礼貌 对此我只能说");
    }

    @Test
    public void keepsGoldRushEnhancedLineAsPrimaryWhenChineseTranslationSharesTimestamp() {
        assertEnhancedBilingualLine(
                "[01:24.350] <01:24.350>Had <01:24.569>never "
                        + "<01:24.936>seen <01:25.343>a <01:25.555>love "
                        + "<01:26.011>as <01:26.167>pure <01:26.555>as "
                        + "<01:26.770>it<01:27.604>\n"
                        + "[01:24.350]未曾感受过"
                        + "如此真切的爱",
                84_350L,
                "Had never seen a love as pure as it",
                "未曾感受过如此真切的爱");
    }

    @Test
    public void keepsBeautyAndABeatEnhancedLineAsPrimaryWhenChineseTranslationSharesTimestamp() {
        assertEnhancedBilingualLine(
                "[00:50.093] <00:50.093>Is <00:50.317>a "
                        + "<00:50.501>beauty <00:51.461>and <00:51.973>a "
                        + "<00:52.181>beat<00:53.917>\n"
                        + "[00:50.093]就是一个美人"
                        + "和一首带感的歌",
                50_093L,
                "Is a beauty and a beat",
                "就是一个美人和一首带感的歌");
    }

    @Test
    public void keepsAugustBracketInlineLineAsPrimaryWhenChineseTranslationSharesTimestamp() {
        assertEnhancedBilingualLine(
                "[00:26.609]But [00:26.793]I [00:27.041]can "
                        + "[00:27.345]see [00:27.689]us[00:27.995]\n"
                        + "[00:26.609]但我看见我们",
                26_609L,
                "But I can see us",
                "但我看见我们");
    }

    @Test
    public void userReportedNoBodyNoCrimeShortEnhancedLineKeepsPrimaryAndTranslation() {
        String lrc = "[00:06.490]He [00:06.850]did [00:07.390]it[00:07.630]\n"
                + "[00:06.490]他背叛了我\n"
                + "[00:09.400]He [00:09.760]did [00:25.060]it[00:25.540]\n"
                + "[00:09.400]他背叛了我";

        assertEnhancedBilingualLine(
                lrc,
                6_490L,
                "He did it",
                "他背叛了我");
        assertParsedLine(
                LyricsCoreAdapter.parse(lrc),
                9_400L,
                "He did it",
                "他背叛了我");
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);
        assertDocumentLine(document, 9_400L, "He did it", "他背叛了我");
        assertDocumentWordCount(document, 9_400L, 1);
    }

    @Test
    public void userReportedDeathByAThousandCutsPreservesCreditsForLaterCleanup() {
        String lrc = "[by:Trap_Girl]\n"
                + "[00:00.00]作词 : Taylor Swift/Jack Antonoff\n"
                + "[00:00.09]作曲 : Taylor Swift/Jack Antonoff\n"
                + "[00:00.18]My, my, my, my\n"
                + "[00:00.18]只属于我\n";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        String official = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertParsedLine(parsed, 180L, "My, my, my, my", "只属于我");
        assertTrue("official=" + official, official.contains("My, my, my, my"));
        assertTrue(official.contains("作词"));
        assertTrue(official.contains("作曲"));
        assertFalse(official.contains("只属于我"));
    }

    @Test
    public void userReportedEnhancedBilingualFilesKeepPrimaryLinesWhenSupplied() throws Exception {
        String fixtureDir = System.getProperty("lyrics.swap.fixture.dir", "");
        assumeTrue("lyrics.swap.fixture.dir was not supplied", !fixtureDir.isEmpty());

        assertFileLine(
                fixtureDir,
                "01. Taylor Swift - All Of The Girls You Loved Before.lrc",
                43_734L,
                "Treat me like a lady all that I can say is",
                "你对我绅士礼貌 对此我只能说");
        assertFileLine(
                fixtureDir,
                "Taylor Swift - gold rush.lrc",
                84_350L,
                "Had never seen a love as pure as it",
                "未曾感受过如此真切的爱");
        assertFileLine(
                fixtureDir,
                "Justin Bieber&Nicki Minaj - Beauty And A Beat.lrc",
                50_093L,
                "Is a beauty and a beat",
                "就是一个美人和一首带感的歌");
        assertFileLine(
                fixtureDir,
                "08 - Taylor Swift - august.lrc",
                26_609L,
                "But I can see us",
                "但我看见我们");
    }

    private static void assertEnhancedBilingualLine(
            String lrc,
            long startMillis,
            String expectedText,
            String expectedTranslation) {
        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);
        LyricsCoreAdapter.ParsedLyrics fallback = LyricsCoreAdapter.parsePlainLrc(lrc);
        TimedLyricDocument document = TimedLyricDocument.fromRawLrc(lrc);

        assertParsedLine(parsed, startMillis, expectedText, expectedTranslation);
        assertParsedLine(fallback, startMillis, expectedText, expectedTranslation);
        assertDocumentLine(document, startMillis, expectedText, expectedTranslation);
    }

    private static void assertFileLine(
            String fixtureDir,
            String fileName,
            long startMillis,
            String expectedText,
            String expectedTranslation) throws Exception {
        String lrc = new String(
                Files.readAllBytes(Paths.get(fixtureDir, fileName)),
                StandardCharsets.UTF_8);
        assertParsedLine(
                LyricsCoreAdapter.parse(lrc),
                startMillis,
                expectedText,
                expectedTranslation);
        assertParsedLine(
                LyricsCoreAdapter.parsePlainLrc(lrc),
                startMillis,
                expectedText,
                expectedTranslation);
        assertDocumentLine(
                TimedLyricDocument.fromRawLrc(lrc),
                startMillis,
                expectedText,
                expectedTranslation);
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

    private static void assertDocumentLine(
            TimedLyricDocument document,
            long startMillis,
            String expectedText,
            String expectedTranslation) {
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == startMillis)
                .findFirst()
                .orElseThrow();
        assertEquals(expectedText, line.text);
        assertEquals(expectedTranslation, line.translation);
    }

    private static void assertDocumentWordCount(
            TimedLyricDocument document,
            long startMillis,
            int expectedWordCount) {
        TimedLyricDocument.Line line = document.lines().stream()
                .filter(candidate -> candidate.startMillis == startMillis)
                .findFirst()
                .orElseThrow();
        assertEquals(expectedWordCount, line.words.size());
    }
}
