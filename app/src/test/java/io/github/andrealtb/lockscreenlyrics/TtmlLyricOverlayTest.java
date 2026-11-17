package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.github.andrealtb.lockscreenlyrics.render.LyricSide;
import io.github.andrealtb.lockscreenlyrics.render.LyricTimingMode;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;

/** Covers the optional {@code lyricInfo.ttml} lane: contract parsing and model annotation. */
public final class TtmlLyricOverlayTest {
    private static final String DUET_DISPLAY = "[00:10.000]Hello there\n"
            + "[00:12.500]Hi back\n"
            + "[00:16.000]Together\n"
            + "[00:19.000]Again me\n";
    private static final String DUET_RAW = "[00:10.000]<00:10.000>Hel<00:10.400>lo <00:11.000>there<00:12.000>\n"
            + "[00:12.500]<00:12.500>Hi <00:13.000>back<00:14.000>\n"
            + "[00:16.000]<00:16.000>To<00:17.000>gether<00:18.000>\n"
            + "[00:19.000]<00:19.000>Again <00:20.000>me<00:21.000>\n";

    private static final LyricParseTraceSink SILENT = new LyricParseTraceSink() {
        @Override
        public boolean traceEnabled() {
            return false;
        }

        @Override
        public void trace(String message) {
        }

        @Override
        public void onCoreParseFailure(boolean primarySource, Throwable failure) {
        }
    };

    @Test
    public void payloadKeepsItsOwnLanesWhenTtmlAccompaniesThem() throws Exception {
        JSONObject object = new JSONObject();
        object.put("lyric", DUET_DISPLAY);
        object.put("rawLyric", DUET_RAW);
        object.put("translationLyric", "[00:10.000]自带翻译");
        object.put("ttml", read("apple-duet-word"));

        LyricInfoContract.Payload payload = LyricInfoContract.parse(object.toString());

        assertEquals(DUET_RAW, payload.rawLyric);
        assertEquals("[00:10.000]自带翻译", payload.translationLyric);
        assertEquals(read("apple-duet-word"), payload.ttml);
    }

    @Test
    public void ttmlSuppliesWordTimingAndTranslationWhenTheLanesAreMissing() throws Exception {
        JSONObject object = new JSONObject();
        object.put("lyric", DUET_DISPLAY);
        object.put("ttml", read("apple-duet-word"));

        LyricInfoContract.Payload payload = LyricInfoContract.parse(object.toString());

        assertEquals(DUET_RAW, payload.rawLyric);
        assertTrue(payload.hasWordTiming());
        assertEquals("[00:10.000]第一行翻译\n[00:12.500]第二行翻译\n", payload.translationLyric);
    }

    @Test
    public void unusableTtmlIsDroppedWithoutAffectingThePayload() throws Exception {
        JSONObject broken = new JSONObject();
        broken.put("lyric", DUET_DISPLAY);
        broken.put("ttml", "<tt><body><p begin=\"abc\">x</p></body></tt>");
        LyricInfoContract.Payload brokenPayload = LyricInfoContract.parse(broken.toString());
        assertNotNull(brokenPayload);
        assertEquals("", brokenPayload.ttml);
        assertEquals("", brokenPayload.rawLyric);

        JSONObject sentenceWindow = new JSONObject();
        sentenceWindow.put("displayMode", "sentence-window-v1");
        sentenceWindow.put("currentLine", 1);
        sentenceWindow.put("lyric", "[00:00.000]One\n[00:01.000]Two");
        sentenceWindow.put("ttml", read("apple-duet-word"));
        LyricInfoContract.Payload windowPayload =
                LyricInfoContract.parse(sentenceWindow.toString());
        assertEquals(1_000L, windowPayload.snapshotPositionMillis);
        assertEquals("", windowPayload.ttml);
        assertEquals("", windowPayload.rawLyric);
    }

    @Test
    public void duetSidesAndBackgroundVocalsAnnotateTheAssembledLines() throws Exception {
        WordLyricModel model = assemble(DUET_RAW, DUET_DISPLAY, read("apple-duet-word"));

        assertEquals(4, model.lines.size());
        assertEquals(4, model.ttmlAnnotatedLines);
        assertTrue(model.hasDuet);
        assertEquals(LyricSide.PRIMARY, model.lines.get(0).side);
        assertEquals(LyricSide.SECONDARY, model.lines.get(1).side);
        assertEquals(LyricSide.PRIMARY, model.lines.get(2).side);
        assertEquals(LyricSide.PRIMARY, model.lines.get(3).side);

        // Timing and text still come from the LRC lanes.
        assertEquals(LyricTimingMode.WORD_TIMED, model.lines.get(1).timingMode);
        assertEquals("Hi back", model.lines.get(1).text);
        assertNull(model.lines.get(0).accompaniment);
        assertEquals(1, model.lines.get(1).accompaniment.size());
        WordLine background = model.lines.get(1).accompaniment.get(0);
        assertEquals("ooh yeah", background.text);
        assertEquals(14_000L, background.timeMillis);
        assertEquals(LyricTimingMode.WORD_TIMED, background.timingMode);
    }

    @Test
    public void linesRemovedFromTheLanesLeaveTheRestAnnotated() throws Exception {
        // Opening cleanup drops rows from lyric/rawLyric but leaves the document untouched.
        String raw = DUET_RAW.substring(DUET_RAW.indexOf('\n') + 1);
        String display = DUET_DISPLAY.substring(DUET_DISPLAY.indexOf('\n') + 1);

        WordLyricModel model = assemble(raw, display, read("apple-duet-word"));

        assertEquals(3, model.lines.size());
        assertEquals(3, model.ttmlAnnotatedLines);
        assertTrue(model.hasDuet);
        assertEquals(LyricSide.SECONDARY, model.lines.get(0).side);
        assertEquals(LyricSide.PRIMARY, model.lines.get(1).side);
    }

    @Test
    public void aDuetReducedToOneSideIsNotLaidOutAsADuet() throws Exception {
        // Only the second singer's line survives, so nothing must be pushed to one side.
        WordLyricModel model = assemble(
                "[00:12.500]<00:12.500>Hi <00:13.000>back<00:14.000>\n",
                "[00:12.500]Hi back\n",
                read("apple-duet-word"));

        assertEquals(1, model.ttmlAnnotatedLines);
        assertFalse(model.hasDuet);
        assertEquals(LyricSide.UNSPECIFIED, model.lines.get(0).side);
        assertEquals(1, model.lines.get(0).accompaniment.size());
    }

    @Test
    public void singleSingerAndAbsentDocumentsLeaveLinesUnspecified() throws Exception {
        String raw = "[00:05.200]First line only\n[01:02.500]Second & line\n[01:10.250]Third line\n";

        WordLyricModel single = assemble(raw, raw, read("line-timed"));
        assertEquals(3, single.ttmlAnnotatedLines);
        assertFalse(single.hasDuet);

        WordLyricModel none = assemble(DUET_RAW, DUET_DISPLAY, "");
        assertEquals(0, none.ttmlAnnotatedLines);
        assertFalse(none.hasDuet);
        for (WordLine line : none.lines) {
            assertEquals(LyricSide.UNSPECIFIED, line.side);
            assertNull(line.accompaniment);
        }
    }

    @Test
    public void lineTimedDuetWrappedInOneSpanStaysLineTimed() throws Exception {
        String ttml = "<tt xmlns=\"http://www.w3.org/ns/ttml\""
                + " xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\"><head><metadata>"
                + "<ttm:agent type=\"person\" xml:id=\"v1\"/>"
                + "<ttm:agent type=\"person\" xml:id=\"v2\"/></metadata></head><body><div>"
                + "<p begin=\"1.000\" end=\"2.000\" ttm:agent=\"v1\">"
                + "<span begin=\"1.000\" end=\"2.000\">Whole line one</span></p>"
                + "<p begin=\"3.000\" end=\"4.000\" ttm:agent=\"v2\">"
                + "<span begin=\"3.000\" end=\"4.000\">Whole line two</span></p>"
                + "</div></body></tt>";
        JSONObject object = new JSONObject();
        object.put("lyric", "[00:01.000]Whole line one\n[00:03.000]Whole line two\n");
        object.put("ttml", ttml);
        LyricInfoContract.Payload payload = LyricInfoContract.parse(object.toString());

        // A one-span line must not turn into a fabricated word timeline.
        assertEquals("[00:01.000]Whole line one\n[00:03.000]Whole line two\n", payload.rawLyric);
        WordLyricModel model = assemble(payload.rawLyric, payload.lyric, payload.ttml);
        assertTrue(model.hasDuet);
        assertEquals(LyricSide.SECONDARY, model.lines.get(1).side);
        assertEquals(LyricTimingMode.LINE_TIMED, model.lines.get(1).timingMode);
    }

    @Test
    public void modelSignatureChangesOnlyWhenTtmlIsPresent() throws Exception {
        JSONObject object = new JSONObject();
        object.put("lyric", DUET_DISPLAY);
        object.put("rawLyric", DUET_RAW);
        object.put("trackKey", "track");
        LyricInfoContract.Payload plain = LyricInfoContract.parse(object.toString());
        object.put("ttml", read("apple-duet-word"));
        LyricInfoContract.Payload withTtml = LyricInfoContract.parse(object.toString());

        String plainSignature = LyricMetadataSupport.buildWordLyricModelSignature(
                plain, plain.rawLyric, plain.translationLyric);
        assertEquals(3, plainSignature.chars().filter(c -> c == '|').count());
        assertNotEquals(plainSignature, LyricMetadataSupport.buildWordLyricModelSignature(
                withTtml, withTtml.rawLyric, plain.translationLyric));
    }

    /**
     * Runs a real-world document through the whole lane. Lyrics are copyrighted, so the file
     * stays outside the repository: set {@code CLL_TTML_TEST_FILE} to its path.
     */
    @Test
    public void externalDocumentAnnotatesEveryLineItProduces() throws Exception {
        String path = System.getenv("CLL_TTML_TEST_FILE");
        assumeTrue("CLL_TTML_TEST_FILE was not supplied", path != null && !path.isEmpty());
        String ttml = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        JSONObject object = new JSONObject();
        object.put("ttml", ttml);

        LyricInfoContract.Payload payload = LyricInfoContract.parse(object.toString());

        assertNotNull(payload);
        TtmlLyricOverlay overlay = TtmlLyricOverlay.of(ttml);
        WordLyricModel model = new NativeLyricModelAssembler(SILENT).assemble(
                payload.rawLyric, true, payload.lyric, true,
                payload.translationLyric, payload.ttml).model;
        System.out.println("external-ttml chars=" + ttml.length()
                + " overlayLines=" + overlay.lines.size()
                + " duet=" + overlay.hasDuet
                + " accompaniment=" + overlay.accompanimentCount
                + " modelLines=" + model.lines.size()
                + " annotated=" + model.ttmlAnnotatedLines
                + " translations=" + model.translationCount()
                + " rawChars=" + payload.rawLyric.length());
        assertEquals(model.lines.size(), model.ttmlAnnotatedLines);
        assertEquals(overlay.hasDuet, model.hasDuet);
        assertEquals(
                LyricInfoContract.containsTimedLrc(overlay.translationLrc()),
                model.translationCount() > 0);
    }

    private static WordLyricModel assemble(String raw, String display, String ttml) {
        return new NativeLyricModelAssembler(SILENT).assemble(
                raw,
                LyricInfoContract.containsTimedLrc(raw),
                display,
                LyricInfoContract.containsTimedLrc(display),
                "",
                ttml).model;
    }

    private static String read(String name) throws Exception {
        try (InputStream in = TtmlLyricOverlayTest.class
                .getResourceAsStream("/fixtures/ttml/" + name + ".ttml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
