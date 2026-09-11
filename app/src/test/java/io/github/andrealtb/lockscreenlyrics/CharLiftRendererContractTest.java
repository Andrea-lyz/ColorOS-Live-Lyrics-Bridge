package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Source contract for the per-character lift draw path. These invariants live in
 * {@code OfficialLyricTextRenderer}, which needs a real Canvas and cannot be
 * exercised by the JVM suite, so they are pinned here the same way
 * {@link LyricUiConfigContractTest} pins renderer config consumption.
 */
public final class CharLiftRendererContractTest {

    @Test
    public void liftIsGatedOffUnderAodLowFrameRate() throws Exception {
        String module = module();
        int gate = module.indexOf("private void beginCharLiftLine(");
        assertTrue("beginCharLiftLine is missing", gate >= 0);
        String body = module.substring(gate, gate + 3_000);
        assertTrue("lift must bail out on AOD low frame rate", body.contains("aodLowFrameRateMode"));
        assertTrue("lift must bail out without the toggle", body.contains("!charLiftEnabled"));
        assertTrue(
                "lift must bail out for a timestamp-highlighted short row",
                body.contains("shouldUseTimestampHighlight"));
        assertTrue(
                "WORD_TIMED lift must remain allowed",
                body.contains("line.timingMode != LyricTimingMode.WORD_TIMED"));
        assertTrue(
                "LINE_TIMED lift must remain behind the line-progress setting",
                body.contains("line.timingMode != LyricTimingMode.LINE_TIMED"));
        assertTrue(
                "LINE_TIMED lift must stay off when line progress is disabled",
                body.contains("!lineTimedProgressEnabled"));
    }

    @Test
    public void lineTimedLiftUsesTheLineProgressDisplayEnd() throws Exception {
        String module = module();
        int start = module.indexOf("private long resolveCharLiftLineRevealEnd(");
        assertTrue("resolveCharLiftLineRevealEnd is missing", start >= 0);
        String body = module.substring(start, start + 1_000);
        assertTrue(
                "line-timed rows must select their display-end anchor",
                body.contains("line.timingMode == LyricTimingMode.LINE_TIMED"));
        assertTrue(
                "line-timed rows must end where their linear reveal ends",
                body.contains("return resolveLineDisplayEndMillis(model, line);"));
        assertTrue(
                "word-timed rows must retain their word-reveal anchor",
                body.contains("WordLyricRenderSupport.wordRevealEndMillis("));
    }

    @Test
    public void liftedDrawingNeverEscapesToTheCoordinator() throws Exception {
        String module = module();
        int start = module.indexOf("private void drawLiftedGraphemes(");
        assertTrue("drawLiftedGraphemes is missing", start >= 0);
        int end = module.indexOf("private float[] resolveGraphemePrefixWidths(", start);
        assertTrue("drawLiftedGraphemes body not found", end > start);
        String body = module.substring(start, end);
        assertTrue(
                "a lifted draw failure must be swallowed, not raised",
                body.contains("catch (RuntimeException"));
        assertTrue(
                "a lifted draw failure must stop further lifting",
                body.contains("charLiftUnavailable = true"));
        assertTrue(
                "the lifted pass must restore by count, never canvas.restore()",
                body.contains("canvas.restoreToCount("));
    }

    @Test
    public void sungCharactersAreLeftStandingWithNothingToDecay() throws Exception {
        String module = module();
        assertFalse(
                "the bell model's settle decay has no place in a step model",
                module.contains("revealDecay"));
        assertFalse(module.contains("CHAR_LIFT_SETTLE_TAIL_MS"));
        assertTrue(
                "the only animation this effect owns is the sink-in ramp",
                module.contains("CharLiftGeometry.sinkRamp("));
        assertTrue(
                "the ramp must be anchored on the row's own start",
                module.contains("WordLyricRenderConstants.CHAR_LIFT_SINK_IN_MS"));
    }

    @Test
    public void theRowSinksBeforeItsFirstWordRatherThanOnIt() throws Exception {
        String module = module();
        int gate = module.indexOf("private void beginCharLiftLine(");
        assertTrue("beginCharLiftLine is missing", gate >= 0);
        String body = module.substring(gate, gate + 3_000);
        assertFalse(
                "a row with no active word yet is still unsung and must sink",
                body.contains("|| activeWord == null"));
        assertTrue(
                "the front rests at the start of the text during the pre-roll",
                body.contains("activeWord == null"));
    }

    @Test
    public void graphemeGeometryIsResolvedOnlyThroughTheCache() throws Exception {
        String module = module();
        assertEquals(
                "grapheme boundaries must be walked on a cache miss and nowhere else",
                1,
                countOf(module, "CharLiftGeometry.graphemeBoundaries("));
        assertTrue(module.contains("private final GraphemeLayoutCache[] graphemeLayoutCaches"));
    }

    @Test
    public void compactSingleLineDrawingStaysOutOfScope() throws Exception {
        String module = module();
        int compact = module.indexOf("private void drawCompactLine(");
        int segment = module.indexOf("private void drawSegment(");
        assertTrue("drawCompactLine is missing", compact >= 0);
        assertTrue("drawSegment must follow drawCompactLine", segment > compact);
        assertTrue(
                "the compact single-line variant is out of scope for the lift",
                !module.substring(compact, segment).contains("charLift"));
    }

    @Test
    public void clearingTheGlowCacheAlsoClearsLiftState() throws Exception {
        String module = module();
        int start = module.indexOf("void clearGlowCache() {");
        assertTrue("clearGlowCache is missing", start >= 0);
        String body = module.substring(start, start + 500);
        assertTrue(body.contains("for (GraphemeLayoutCache cache : graphemeLayoutCaches)"));
        assertTrue(body.contains("charLift.reset()"));
        assertTrue(
                "a rebind must give the effect another chance",
                body.contains("charLiftUnavailable = false"));
    }

    private static int countOf(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    private static String module() throws Exception {
        return readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
