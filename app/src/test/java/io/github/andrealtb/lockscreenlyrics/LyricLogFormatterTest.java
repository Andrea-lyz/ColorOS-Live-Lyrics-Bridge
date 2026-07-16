package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class LyricLogFormatterTest {
    @Test
    public void formatsStructuredPrefixAndFields() {
        String formatted = LyricLogFormatter.format(
                "com.android.systemui",
                LyricLogFormatter.Area.RECYCLER,
                "attachment",
                "Observed LyricsRecyclerView attachment, alpha=0, size=0x0");

        assertEquals(
                "[com.android.systemui][Recycler][attachment] "
                        + "Observed LyricsRecyclerView attachment | alpha=0, size=0x0",
                formatted);
    }

    @Test
    public void escapesNewlinesTabsAndControlCharacters() {
        String formatted = LyricLogFormatter.format(
                "player",
                LyricLogFormatter.Area.PARSER,
                "parse",
                "line1\nline2\t" + (char) 1);

        assertTrue(formatted.endsWith("line1\\nline2\\t<U+0001>"));
    }

    @Test
    public void truncatesLongValues() {
        String value = "x".repeat(LyricLogFormatter.MAX_MESSAGE_CHARS + 200);
        String formatted = LyricLogFormatter.format(
                "systemui", LyricLogFormatter.Area.RENDER, "draw", value);

        assertTrue(formatted.endsWith("..."));
        assertTrue(formatted.length() < value.length());
    }

    @Test
    public void chunksUseOneBasedIndexAndTotal() {
        List<String> chunks = LyricLogFormatter.chunks("abcdefghij", 4);

        assertEquals(3, chunks.size());
        assertEquals("chunk=1/3 abcd", chunks.get(0));
        assertEquals("chunk=2/3 efgh", chunks.get(1));
        assertEquals("chunk=3/3 ij", chunks.get(2));
    }
}
