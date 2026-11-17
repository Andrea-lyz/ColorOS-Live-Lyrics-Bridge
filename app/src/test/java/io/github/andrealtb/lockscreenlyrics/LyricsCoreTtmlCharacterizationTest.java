package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine;
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment;
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine;
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable;
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine;
import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Pins the behaviour of lyrics-core's TTML parser that the TTML / duet plan
 * (docs/TTML-DUET-ADAPTATION-PLAN.zh-CN.md, phase 0) depends on. The fixtures are hand-written
 * representative documents, not captured player payloads. A failure here after a lyrics-core
 * upgrade means the plan's transport rules must be re-checked, not that the test is wrong.
 */
public final class LyricsCoreTtmlCharacterizationTest {
    private static final String TT = "<tt xmlns=\"http://www.w3.org/ns/ttml\">";

    @Test
    public void twoSingerWordTimedDocumentKeepsSidesTranslationsAndBackgroundVocals()
            throws Exception {
        List<ISyncedLine> lines = parse(read("apple-duet-word"));

        assertAlignments(lines,
                KaraokeAlignment.Start, KaraokeAlignment.End,
                KaraokeAlignment.Start, KaraokeAlignment.Start);

        KaraokeLine.MainKaraokeLine first = main(lines.get(0));
        assertEquals(10_000, first.getStart());
        assertEquals(12_000, first.getEnd());
        assertEquals("第一行翻译", first.getTranslation());
        assertEquals(3, first.getSyllables().size());
        // Inter-span spaces are folded into the preceding syllable.
        assertEquals("lo ", first.getSyllables().get(1).getContent());
        assertEquals(10_400, first.getSyllables().get(1).getStart());
        assertEquals(11_000, first.getSyllables().get(1).getEnd());
        // No background vocal is reported as null, not as an empty list.
        assertNull(first.getAccompanimentLines());

        KaraokeLine.MainKaraokeLine second = main(lines.get(1));
        assertEquals("第二行翻译", second.getTranslation());
        assertEquals("Hi back", text(second));
        assertEquals(1, second.getAccompanimentLines().size());
        KaraokeLine.AccompanimentKaraokeLine background = second.getAccompanimentLines().get(0);
        assertEquals(KaraokeAlignment.End, background.getAlignment());
        assertEquals(14_000, background.getStart());
        assertEquals(15_000, background.getEnd());
        // Wrapping parentheses are stripped from background vocals.
        assertEquals("ooh yeah", text(background));

        // A group agent always renders on the start side.
        assertEquals("Together", text(main(lines.get(2))));
    }

    @Test
    public void inlineRolesAreRoutedOutOfTheMainText() throws Exception {
        List<ISyncedLine> lines = parse(read("amll-inline"));

        assertEquals(2, lines.size());
        KaraokeLine.MainKaraokeLine first = main(lines.get(0));
        assertEquals("君の", text(first));
        assertEquals("你的", first.getTranslation());
        assertEquals("kimi no", first.getPhonetic());

        KaraokeLine.MainKaraokeLine second = main(lines.get(1));
        assertEquals("名前", text(second));
        assertEquals("名字", second.getTranslation());
        assertEquals(KaraokeAlignment.End, second.getAlignment());
        KaraokeLine.AccompanimentKaraokeLine background = second.getAccompanimentLines().get(0);
        assertEquals("ah", text(background));
        assertEquals("啊", background.getTranslation());
    }

    @Test
    public void sidesAlternateOnSingerChangeInTimeOrder() throws Exception {
        List<ISyncedLine> lines = parse(read("three-agents"));

        // Output is sorted by start time even though the document is not.
        int[] starts = {1_000, 3_000, 5_000, 6_500, 7_000, 9_000};
        assertEquals(starts.length, lines.size());
        for (int i = 0; i < starts.length; i++) {
            assertEquals(starts[i], lines.get(i).getStart());
        }
        // v2, v1, v3, v2, group, no agent. The first singer's side comes from the parity of its
        // id (even -> End); each later singer change flips the side, so v2 is End at 1.0s and
        // Start at 6.5s. Sides are therefore only stable per singer in two-singer songs. A group
        // line is always Start and a line without an agent keeps the current side.
        assertAlignments(lines,
                KaraokeAlignment.End, KaraokeAlignment.Start, KaraokeAlignment.End,
                KaraokeAlignment.Start, KaraokeAlignment.Start, KaraokeAlignment.Start);
    }

    @Test
    public void lineTimedParagraphsLoseTheirSingerUnlessWrappedInOneSpan() throws Exception {
        List<ISyncedLine> lineTimed = parse(read("line-timed"));

        assertEquals(3, lineTimed.size());
        int[] starts = {5_200, 62_500, 70_250};
        for (int i = 0; i < starts.length; i++) {
            // SyncedLine has no alignment, so a span-less duet cannot carry sides.
            assertTrue(lineTimed.get(i) instanceof SyncedLine);
            assertEquals(starts[i], lineTimed.get(i).getStart());
        }
        assertEquals("Second & line", ((SyncedLine) lineTimed.get(1)).getContent());

        List<ISyncedLine> wrapped = parse("<tt xmlns=\"http://www.w3.org/ns/ttml\""
                + " xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\"><head><metadata>"
                + "<ttm:agent type=\"person\" xml:id=\"v1\"/>"
                + "<ttm:agent type=\"person\" xml:id=\"v2\"/></metadata></head><body><div>"
                + "<p begin=\"1.000\" end=\"2.000\" ttm:agent=\"v1\">"
                + "<span begin=\"1.000\" end=\"2.000\">Whole line one</span></p>"
                + "<p begin=\"3.000\" end=\"4.000\" ttm:agent=\"v2\">"
                + "<span begin=\"3.000\" end=\"4.000\">Whole line two</span></p>"
                + "</div></body></tt>");
        assertAlignments(wrapped, KaraokeAlignment.Start, KaraokeAlignment.End);
        assertEquals(1, main(wrapped.get(1)).getSyllables().size());
        assertEquals("Whole line two", text(main(wrapped.get(1))));
    }

    @Test
    public void lyricOffsetIsNotApplied() throws Exception {
        List<ISyncedLine> lines = parse(read("lyric-offset"));

        assertEquals(1, lines.size());
        assertEquals(10_000, lines.get(0).getStart());
    }

    @Test
    public void formattingWhitespaceBetweenSpansIsDropped() {
        List<ISyncedLine> lines = parse("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + TT
                + "\n  <body>\n    <div>\n      <p begin=\"00:01.000\" end=\"00:03.000\">\n"
                + "        <span begin=\"00:01.000\" end=\"00:02.000\">Hello</span>\n"
                + "        <span begin=\"00:02.000\" end=\"00:03.000\">world</span>\n"
                + "      </p>\n    </div>\n  </body>\n</tt>");

        assertEquals(1, lines.size());
        // A pretty-printed document loses its word spacing; a single inline space keeps it.
        assertEquals("Helloworld", text(main(lines.get(0))));
    }

    @Test
    public void unsupportedTimeAndEntityFormsAreNotNormalized() {
        List<ISyncedLine> lines = parse(TT + "<body><div>"
                + "<p begin=\"1s\" end=\"2s\">&a; x &lt;y&gt; &#x4E2D;</p></div></body></tt>");

        assertEquals(1, lines.size());
        // Offset-time syntax ("1s") is read as zero.
        assertEquals(0, lines.get(0).getStart());
        assertEquals(0, lines.get(0).getEnd());
        // Predefined entities decode; numeric and unknown references pass through verbatim.
        assertEquals("&a; x <y> &#x4E2D;", ((SyncedLine) lines.get(0)).getContent());
    }

    @Test
    public void malformedDocumentsDegradeWithoutThrowing() throws Exception {
        String duet = read("apple-duet-word");

        assertTrue(parse(duet.substring(0, duet.length() / 2)).isEmpty());
        assertTrue(parse(TT + "<body></body></tt>").isEmpty());
        assertFalse(new TTMLParser().canParse(
                "<tt><body><p begin=\"abc\">x</p><p>no time</p></body></tt>"));
    }

    private static List<ISyncedLine> parse(String ttml) {
        return new TTMLParser().parse(ttml).getLines();
    }

    private static KaraokeLine.MainKaraokeLine main(ISyncedLine line) {
        return (KaraokeLine.MainKaraokeLine) line;
    }

    private static String text(KaraokeLine line) {
        StringBuilder text = new StringBuilder();
        for (KaraokeSyllable syllable : line.getSyllables()) {
            text.append(syllable.getContent());
        }
        return text.toString();
    }

    private static void assertAlignments(List<ISyncedLine> lines, KaraokeAlignment... expected) {
        assertEquals(expected.length, lines.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("line " + i, expected[i], ((KaraokeLine) lines.get(i)).getAlignment());
        }
    }

    private static String read(String name) throws Exception {
        try (InputStream in = LyricsCoreTtmlCharacterizationTest.class
                .getResourceAsStream("/fixtures/ttml/" + name + ".ttml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
