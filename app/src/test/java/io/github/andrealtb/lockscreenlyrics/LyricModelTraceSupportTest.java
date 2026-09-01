package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricModelTraceSupportTest {
    @Test
    public void traceKeepsStructureWithoutLyricContent() {
        String raw = "raw-split#4 [00:01.000]private lyric token=secret1234567890123456";
        String safe = LyricModelTraceSupport.sanitizeTraceMessage(raw);

        assertTrue(safe.startsWith("raw-split#4"));
        assertTrue(safe.contains("chars="));
        assertTrue(safe.contains("hash="));
        assertFalse(safe.contains("private lyric"));
        assertFalse(safe.contains("secret1234567890123456"));
    }
}
