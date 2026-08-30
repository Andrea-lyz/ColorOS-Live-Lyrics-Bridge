package io.github.andrealtb.lockscreenlyrics.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LegacyLogEventMapTest {
    @Test
    public void mapsKnownGoodAodPhrases() {
        assertEquals(
                BridgeEvents.RECYCLER_ATTACHED,
                LegacyLogEventMap.classify(
                        "Observed LyricsRecyclerView attachment, alpha=0, size=0x0").event);
        assertEquals(
                BridgeEvents.RECYCLER_SCROLL_STABILIZED,
                LegacyLogEventMap.classify("Stabilized LyricsRecyclerView scroll").event);
        assertEquals(
                BridgeEvents.RECYCLER_PRIMED,
                LegacyLogEventMap.classify("Primed LyricsRecyclerView").event);
        assertEquals(
                BridgeEvents.OFFICIAL_LAYOUT_HEIGHT_CHANGED,
                LegacyLogEventMap.classify("Official lyric layout height changed, oldLayoutHeight=1")
                        .event);
        assertEquals(
                BridgeEvents.OFFICIAL_ROW_SCALE,
                LegacyLogEventMap.classify("Official lyric row scale, lineIndex=2").event);
        assertEquals(
                BridgeEvents.SET_CURRENT_LYRIC_GEOMETRY,
                LegacyLogEventMap.classify("LyricsRecyclerView setCurrentLyric geometry, index=3")
                        .event);
        assertEquals(
                BridgeDebugArea.AOD,
                LegacyLogEventMap.classify("Observed LyricsRecyclerView attachment").area);
        assertFalse(LegacyLogEventMap.classify("Primed LyricsRecyclerView").alwaysOn);
    }

    @Test
    public void bootstrapSummariesStayAlwaysOn() {
        LegacyLogEventMap.Mapping loaded = LegacyLogEventMap.classify(
                "Loaded in com.android.systemui, API 102");
        assertEquals(BridgeEvents.SYSTEMUI_BOOTSTRAP, loaded.event);
        assertTrue(loaded.alwaysOn);
        assertTrue(LegacyLogEventMap.classify("Hooked OPlus media history integration").alwaysOn);
    }
}
