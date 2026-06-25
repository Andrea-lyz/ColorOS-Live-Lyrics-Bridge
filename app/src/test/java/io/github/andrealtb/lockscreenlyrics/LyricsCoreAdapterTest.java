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
}
