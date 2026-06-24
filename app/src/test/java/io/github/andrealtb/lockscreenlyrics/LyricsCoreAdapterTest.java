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
