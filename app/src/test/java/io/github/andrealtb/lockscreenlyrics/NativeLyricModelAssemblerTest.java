package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;

/**
 * Behavior coverage for the Phase 6 slice 5 native lyric payload assembler:
 * inline word-timed parsing, official display aliases, indexed supplemental
 * translation merging, nearby-translation propagation, and the lyrics-core
 * fallback for line-timed payloads.
 */
public final class NativeLyricModelAssemblerTest {
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

    private static NativeLyricModelAssembler assembler() {
        return new NativeLyricModelAssembler(SILENT);
    }

    @Test
    public void assemblesWordTimedPayloadWithAliasesAndSupplementalTranslations() {
        String raw = "[00:01.000] <00:01.000>Hel <00:01.300>lo<00:01.800>\n"
                + "[00:01.000]你好\n"
                + "[00:05.000] <00:05.000>Wor<00:05.400>ld<00:05.900>\n"
                + "[00:05.000]世界\n"
                + "[00:09.000] <00:09.000>Wie <00:09.200>der <00:09.400>sehen<00:09.900>\n";
        String display = "[00:01.000]Hello\n"
                + "[00:01.000]你好\n"
                + "[00:05.000]World\n"
                + "[00:05.000]世界\n";
        String translation = "[00:09.030]再次见到你\n";

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                LyricInfoContract.containsTimedLrc(raw),
                display,
                LyricInfoContract.containsTimedLrc(display),
                translation);

        WordLyricModel model = result.model;
        assertEquals(3, model.lines.size());
        assertEquals("inline-lrc", model.parserName);

        WordLine hello = model.lines.get(0);
        assertEquals(1_000L, hello.timeMillis);
        assertEquals("Hello", hello.displayText);
        assertEquals("你好", hello.translation);
        assertEquals("Hel lo", hello.text);

        WordLine world = model.lines.get(1);
        assertEquals(5_000L, world.timeMillis);
        assertEquals("World", world.displayText);
        assertEquals("世界", world.translation);
        assertEquals("Wor ld", world.text);

        WordLine wiedersehen = model.lines.get(2);
        assertEquals(9_000L, wiedersehen.timeMillis);
        assertEquals("再次见到你", wiedersehen.translation);

        assertEquals(2, result.aliasesApplied);
        assertEquals("Hello", result.firstAlias);
        assertEquals(0, result.supplementalDisplayAdded);
        assertEquals(1, result.supplementalTranslationAdded);

        assertEquals(2, model.officialLines.size());
        assertEquals(hello, model.officialLines.get(0));
        assertEquals(world, model.officialLines.get(1));
    }

    @Test
    public void propagatesNearbyTranslationsAcrossRepeatedLines() {
        String raw = "[00:10.000] <00:10.000>Re <00:10.200>peat<00:10.800>\n"
                + "[00:10.000]重复\n"
                + "[00:20.000] <00:20.000>Mid <00:20.200>dle<00:20.800>\n"
                + "[00:40.000] <00:40.000>Re <00:40.200>peat<00:40.800>\n";

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                LyricInfoContract.containsTimedLrc(raw),
                raw,
                LyricInfoContract.containsTimedLrc(raw),
                "");

        assertEquals(3, result.model.lines.size());
        assertEquals("重复", result.model.lines.get(0).translation);
        assertEquals("重复", result.model.lines.get(2).translation);
        assertTrue(result.propagatedTranslations >= 1);
    }

    @Test
    public void fallsBackToLyricsCoreForLineTimedPayload() {
        String raw = "[00:01.00]Hello\n[00:01.00]你好\n[00:05.00]World\n";

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                LyricInfoContract.containsTimedLrc(raw),
                "",
                false,
                "");

        assertEquals("lyrics-core", result.model.parserName);
        assertTrue(result.model.lines.size() >= 2);
        WordLine hello = result.model.lines.get(0);
        assertEquals(1_000L, hello.timeMillis);
        assertEquals("你好", hello.translation);
        assertEquals(0, result.aliasesApplied);
        assertEquals(0, result.supplementalDisplayAdded);
        assertEquals(0, result.supplementalTranslationAdded);
    }

    @Test
    public void explicitTranslationLaneIsNotFilteredByBridgeContentText() {
        String raw = "[00:01.000]Hello\n[00:05.000]World\n";
        String translation = "[00:01.000]以下歌词翻译由 Salt Player 提供\n";

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                LyricInfoContract.containsTimedLrc(raw),
                raw,
                LyricInfoContract.containsTimedLrc(raw),
                translation);

        assertEquals(2, result.model.lines.size());
        assertEquals("以下歌词翻译由 Salt Player 提供",
                result.model.lines.get(0).translation);
    }

    @Test
    public void untimedPayloadProducesEmptyModel() {
        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                "plain text without timing",
                false,
                "",
                false,
                "");
        assertNotNull(result.model);
        assertTrue(result.model.lines.isEmpty());
        assertEquals(0, result.aliasesApplied);
        assertEquals(0, result.supplementalDisplayAdded);
        assertEquals(0, result.supplementalTranslationAdded);
        assertEquals(0, result.propagatedTranslations);
        assertNull(result.model.findActiveLine(1_000L));
    }

    @Test
    public void skipsSupplementalMergeWhenDisplayEqualsRaw() {
        String raw = "[00:01.000] <00:01.000>Hel <00:01.300>lo<00:01.800>\n";
        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                true,
                raw,
                true,
                "");
        assertEquals(1, result.model.lines.size());
        assertEquals(0, result.supplementalDisplayAdded);
    }

    @Test
    public void qqFrenchWordTimedLinesKeepDelayedFirstWordInsideTheLine() {
        String raw = "[00:07.228]<00:07.668>L'océan<00:08.084>sera"
                + "<00:08.508>mon<00:08.832>cavalier<00:09.592>\n"
                + "[00:11.209]<00:11.713>Couronnée<00:12.656>de"
                + "<00:12.976>lumière<00:13.488>\n"
                + "[01:08.208]<01:08.664>Condamnée<01:09.263>douce-"
                + "<01:10.023>amère<01:10.656>\n"
                + "[01:40.737]<01:41.272>Même<01:41.472>après"
                + "<01:41.760>la<01:41.959>fin<01:42.199>du"
                + "<01:42.415>spectacle<01:42.776>\n"
                + "[01:50.938]<01:51.482>Décidée<01:51.947>et"
                + "<01:52.362>sincère<01:53.274>";

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                true,
                raw,
                true,
                "");

        assertEquals(5, result.model.lines.size());
        assertEquals("L'océan sera mon cavalier", result.model.lines.get(0).text);
        assertEquals(4, result.model.lines.get(0).words.size());
        assertEquals("Couronnée de lumière", result.model.lines.get(1).text);
        assertEquals("Condamnée douce-amère", result.model.lines.get(2).text);
        assertEquals("Même après la fin du spectacle", result.model.lines.get(3).text);
        assertEquals("Décidée et sincère", result.model.lines.get(4).text);
    }

    @Test
    public void kugouTitleAliasDoesNotBecomeFirstLyricTranslation() {
        String raw = "[ti:回家的路 The Long Way Home]\n"
                + "[00:00.670]<00:00.670>当<00:00.903>你<00:01.150>走"
                + "<00:01.723>上<00:02.074>回<00:02.425>家"
                + "<00:02.776>的<00:03.127>路<00:03.479>";
        String display = "[00:00.700]回家的路 The Long Way Home - HOYO-MiX\n"
                + "[00:00.670]当你走上回家的路\n"
                + "[00:08.670]" + LyricTextSanitizer.ZERO_WIDTH_SPACE;

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                true,
                display,
                true,
                "");

        assertEquals(1, result.model.lines.size());
        WordLine firstLyric = result.model.lines.get(0);
        assertEquals("当你走上回家的路", firstLyric.text);
        assertEquals("", firstLyric.translation);
        assertEquals(3, result.model.officialLines.size());
        assertNull(result.model.officialLines.get(0));
        assertEquals(firstLyric, result.model.officialLines.get(1));
        assertNull(result.model.officialLines.get(2));
    }

    @Test
    public void kugouOpeningTitleSlotReusesDelayedRealFirstLineWithoutTranslation() {
        String raw = "[00:00.290]<00:00.290>Lyrics by<00:00.377>\n"
                + "[00:00.300]<00:00.300>Composed by<00:00.399>\n"
                + "[00:00.670]<00:00.670>当<00:00.903>你<00:01.150>走"
                + "<00:01.723>上<00:02.074>回<00:02.425>家"
                + "<00:02.776>的<00:03.127>路<00:03.479>";
        String display = "[00:00.700]回家的路 The Long Way Home - HOYO-MiX\n"
                + "[00:00.290]Lyrics by\n"
                + "[00:00.300]Composed by\n"
                + "[00:00.670]当你走上回家的路\n"
                + "[00:08.670]" + LyricTextSanitizer.ZERO_WIDTH_SPACE;

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw, true, display, true, "");
        WordLine firstLyric = result.model.lines.get(2);

        assertEquals("当你走上回家的路", firstLyric.text);
        assertEquals("", firstLyric.translation);
        assertEquals(firstLyric, result.model.officialLines.get(0));
        assertNull(result.model.officialLines.get(3));
        assertEquals(0, result.model.adapterIndexOfLine(firstLyric));
    }

    @Test
    public void ordinaryOfficialTranslationAliasStillAttaches() {
        String raw = "[00:01.000]<00:01.000>Hello<00:01.800>";
        String display = "[00:01.030]你好";

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                true,
                display,
                true,
                "");

        assertEquals(1, result.model.lines.size());
        assertEquals("你好", result.model.lines.get(0).translation);
    }

    @Test
    public void zeroWidthTailPlaceholderKeepsOfficialSlotAlignment() {
        String raw = "[01:53.274]<01:53.458>La<01:53.658>ballerine"
                + "<01:53.970>solitaire<01:54.434>sera<01:55.250>la"
                + "<01:55.530>clef<01:57.346>";
        String display = "[01:53.270]La ballerine solitaire sera la clef\n"
                + "[02:01.270]" + LyricTextSanitizer.ZERO_WIDTH_SPACE;

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw,
                true,
                display,
                true,
                "");

        assertEquals(1, result.model.lines.size());
        assertEquals(2, result.model.officialLines.size());
        assertEquals(result.model.lines.get(0), result.model.officialLines.get(0));
        assertNull(result.model.officialLines.get(1));
    }

    @Test
    public void nbspTailPlaceholderKeepsSlotWithoutCreatingTranslation() {
        String raw = "[00:01.000]<00:01.000>Hello<00:01.800>";
        String display = "[00:01.000]Hello\n[00:02.000]\u00A0";

        NativeLyricModelAssembler.AssemblyResult result = assembler().assemble(
                raw, true, display, true, "");

        assertEquals(1, result.model.lines.size());
        assertEquals(0, result.model.translationCount());
        assertEquals(2, result.model.officialLines.size());
        assertEquals(result.model.lines.get(0), result.model.officialLines.get(0));
        assertNull(result.model.officialLines.get(1));
    }
}
