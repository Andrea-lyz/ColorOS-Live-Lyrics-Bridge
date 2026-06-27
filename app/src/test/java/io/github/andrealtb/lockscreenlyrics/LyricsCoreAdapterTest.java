package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class LyricsCoreAdapterTest {
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
    public void plainLrcFallbackIgnoresJapaneseRomajiVariant() {
        String lrc = "[00:30.436]\u3053\u3093\u306a\u79c1\u306e\u672a\u719f\u306a\u3046\u305f\u3092\n"
                + "[00:30.436]\u611f\u8c22\u4f60\u613f\u610f\u8046\u542c\n"
                + "[00:30.436]ko n na wa ta shi no mi ju ku na u ta wo";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);

        assertEquals(1, parsed.lines.size());
        assertEquals("\u3053\u3093\u306a\u79c1\u306e\u672a\u719f\u306a\u3046\u305f\u3092", parsed.lines.get(0).text);
        assertEquals("\u611f\u8c22\u4f60\u613f\u610f\u8046\u542c", parsed.lines.get(0).translation);
    }

    @Test
    public void plainLrcFallbackIgnoresCantonesePhoneticVariant() {
        String lrc = "[00:03.909]\u7f20[00:04.085]\u7ef5[00:04.261]\u7684"
                + "[00:04.429]\u665a[00:04.669]\u98ce [00:06.189]\u5439"
                + "[00:06.413]\u7184[00:06.749]\u7231[00:06.957]\u7684"
                + "[00:07.317]\u68a6[00:07.685]\n"
                + "[00:03.909]cin min di man fong  cui si oi di mong \n"
                + "[00:08.637]\u4e3a[00:08.821]\u4f55[00:09.005]love "
                + "[00:09.237]is [00:09.405]gone [00:10.013]gone "
                + "[00:10.557]gone[00:10.853]\n"
                + "[00:08.637]wai ho love is gone gone gone";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parsePlainLrc(lrc);

        assertEquals(2, parsed.lines.size());
        assertEquals("\u7f20\u7ef5\u7684\u665a\u98ce \u5439\u7184\u7231\u7684\u68a6",
                parsed.lines.get(0).text);
        assertEquals("", parsed.lines.get(0).translation);
        assertEquals("\u4e3a\u4f55love is gone gone gone", parsed.lines.get(1).text);
        assertEquals("", parsed.lines.get(1).translation);
    }

    @Test
    public void keepsJapaneseMainLineWhenRomajiIsMissingForOneTimestamp() {
        String lrc = "[00:00.850]\u3042\u306e\u4e00\u7b49\u661f\u306e\u3055\u3093\u3056\u3081\u304f\u5149\u3067\n"
                + "[00:00.850]a no i tto u se i no sa n za me ku hi ka ri de\n"
                + "[00:00.850]\u5728\u90a3\u4e00\u7b49\u661f\u7684\u55a7\u56a3\u5149\u8292\u4e4b\u4e0b\n"
                + "[00:07.230]\u6211\u304c\u592a\u967d\u7cfb\u306e\u9f13\u52d5\u306b\u5408\u308f\u305b\u3066\n"
                + "[00:07.230]\u8ba9\u6211\u4eec\u6765\u4f34\u7740 \u592a\u9633\u7cfb\u7684\u8109\u52a8";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        LyricsCoreAdapter.ParsedLine line = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 7_230L)
                .findFirst()
                .orElseThrow();
        assertEquals("\u6211\u304c\u592a\u967d\u7cfb\u306e\u9f13\u52d5\u306b\u5408\u308f\u305b\u3066", line.text);
        assertEquals("\u8ba9\u6211\u4eec\u6765\u4f34\u7740 \u592a\u9633\u7cfb\u7684\u8109\u52a8",
                line.translation);
    }

    @Test
    public void prefersChineseTranslationOverJapaneseRomajiLane() {
        String lrc = "[00:37.447]\u78ca[00:37.825]\u3005[00:38.223]\u843d"
                + "[00:38.600]\u3005[00:39.023] [00:39.023]\u53cd"
                + "[00:39.415]\u6226[00:39.799]\u56fd[00:40.151]\u5bb6"
                + "[00:40.351]\n"
                + "[00:37.447]ra i ra i ra ku ra ku   ha n se n ko 'k ka \n"
                + "[00:37.447]\u5149\u660e\u78ca\u843d\u53cd\u6218\u56fd\u5bb6";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertEquals(1, parsed.lines.size());
        assertEquals("\u78ca\u3005\u843d\u3005 \u53cd\u6226\u56fd\u5bb6",
                parsed.lines.get(0).text);
        assertEquals("\u5149\u660e\u78ca\u843d\u53cd\u6218\u56fd\u5bb6",
                parsed.lines.get(0).translation);
    }

    @Test
    public void keepsKanjiMainLineWhenRomajiLaneSharesTrailingLatinAcronym() {
        String lrc = "[00:43.688]\u60aa[00:44.088]\u970a[00:44.423]\u9000"
                + "[00:44.871]\u6563[00:45.111] [00:45.231]ICBM"
                + "[00:46.584]\n"
                + "[00:43.688]a ku ryo u ta i sa n ICBM \n"
                + "[00:43.688]\u6076\u7075\u9000\u6563 ICBM";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertEquals(1, parsed.lines.size());
        assertEquals("\u60aa\u970a\u9000\u6563 ICBM", parsed.lines.get(0).text);
        assertEquals("\u6076\u7075\u9000\u6563 ICBM", parsed.lines.get(0).translation);
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
        assertEquals("\u60aa\u970a\u9000\u6563 ICBM", line.text);
        assertEquals("\u6076\u7075\u9000\u6563 ICBM", line.translation);
    }

    @Test
    public void ignoresZeroWidthSpacerBeforeBilingualLine() {
        String lrc = "[00:30.00]Before\n"
                + "[00:38.13]\u200B\n"
                + "[00:38.15]And all of the foes and all of the friends\n"
                + "[00:38.15]\u6240\u6709\u7684\u5bf9\u624b "
                + "\u6240\u6709\u7684\u670b\u53cb\u200B";

        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lrc);

        assertTrue(parsed.lines.stream().noneMatch(line -> line.startMillis == 38_130L));
        LyricsCoreAdapter.ParsedLine line = parsed.lines.stream()
                .filter(candidate -> candidate.startMillis == 38_150L)
                .findFirst()
                .orElseThrow();
        assertEquals("And all of the foes and all of the friends", line.text);
        assertEquals("\u6240\u6709\u7684\u5bf9\u624b \u6240\u6709\u7684\u670b\u53cb",
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
                        + "[00:43.734]\u4f60\u5bf9\u6211\u7ec5\u58eb\u793c\u8c8c "
                        + "\u5bf9\u6b64\u6211\u53ea\u80fd\u8bf4",
                43_734L,
                "Treat me like a lady all that I can say is",
                "\u4f60\u5bf9\u6211\u7ec5\u58eb\u793c\u8c8c \u5bf9\u6b64\u6211\u53ea\u80fd\u8bf4");
    }

    @Test
    public void keepsGoldRushEnhancedLineAsPrimaryWhenChineseTranslationSharesTimestamp() {
        assertEnhancedBilingualLine(
                "[01:24.350] <01:24.350>Had <01:24.569>never "
                        + "<01:24.936>seen <01:25.343>a <01:25.555>love "
                        + "<01:26.011>as <01:26.167>pure <01:26.555>as "
                        + "<01:26.770>it<01:27.604>\n"
                        + "[01:24.350]\u672a\u66fe\u611f\u53d7\u8fc7"
                        + "\u5982\u6b64\u771f\u5207\u7684\u7231",
                84_350L,
                "Had never seen a love as pure as it",
                "\u672a\u66fe\u611f\u53d7\u8fc7\u5982\u6b64\u771f\u5207\u7684\u7231");
    }

    @Test
    public void keepsBeautyAndABeatEnhancedLineAsPrimaryWhenChineseTranslationSharesTimestamp() {
        assertEnhancedBilingualLine(
                "[00:50.093] <00:50.093>Is <00:50.317>a "
                        + "<00:50.501>beauty <00:51.461>and <00:51.973>a "
                        + "<00:52.181>beat<00:53.917>\n"
                        + "[00:50.093]\u5c31\u662f\u4e00\u4e2a\u7f8e\u4eba"
                        + "\u548c\u4e00\u9996\u5e26\u611f\u7684\u6b4c",
                50_093L,
                "Is a beauty and a beat",
                "\u5c31\u662f\u4e00\u4e2a\u7f8e\u4eba\u548c\u4e00\u9996\u5e26\u611f\u7684\u6b4c");
    }

    @Test
    public void keepsAugustBracketInlineLineAsPrimaryWhenChineseTranslationSharesTimestamp() {
        assertEnhancedBilingualLine(
                "[00:26.609]But [00:26.793]I [00:27.041]can "
                        + "[00:27.345]see [00:27.689]us[00:27.995]\n"
                        + "[00:26.609]\u4f46\u6211\u770b\u89c1\u6211\u4eec",
                26_609L,
                "But I can see us",
                "\u4f46\u6211\u770b\u89c1\u6211\u4eec");
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
                "\u4f60\u5bf9\u6211\u7ec5\u58eb\u793c\u8c8c \u5bf9\u6b64\u6211\u53ea\u80fd\u8bf4");
        assertFileLine(
                fixtureDir,
                "Taylor Swift - gold rush.lrc",
                84_350L,
                "Had never seen a love as pure as it",
                "\u672a\u66fe\u611f\u53d7\u8fc7\u5982\u6b64\u771f\u5207\u7684\u7231");
        assertFileLine(
                fixtureDir,
                "Justin Bieber&Nicki Minaj - Beauty And A Beat.lrc",
                50_093L,
                "Is a beauty and a beat",
                "\u5c31\u662f\u4e00\u4e2a\u7f8e\u4eba\u548c\u4e00\u9996\u5e26\u611f\u7684\u6b4c");
        assertFileLine(
                fixtureDir,
                "08 - Taylor Swift - august.lrc",
                26_609L,
                "But I can see us",
                "\u4f46\u6211\u770b\u89c1\u6211\u4eec");
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
}
