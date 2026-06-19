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
}
