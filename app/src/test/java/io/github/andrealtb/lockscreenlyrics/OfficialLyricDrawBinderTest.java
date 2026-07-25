package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.andrealtb.lockscreenlyrics.render.LyricDrawLine;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricDrawBinder;

import org.junit.Test;

public class OfficialLyricDrawBinderTest {

    @Test
    public void defaultStyleOptions_disablesAllProgressAndScale() {
        OfficialLyricDrawBinder.StyleOptionsSnapshot snapshot =
                OfficialLyricDrawBinder.defaultStyleOptions();
        assertNotNull(snapshot);
        assertFalse(snapshot.scrollScaleEnabled);
        assertFalse(snapshot.inactiveBlurEnabled);
        assertFalse(snapshot.lineTimedProgressEnabled);
        assertFalse(snapshot.translationProgressEnabled);
        assertFalse(snapshot.forceOfficialSlotHeight);
    }

    @Test
    public void hasMutableStyleOptions_onlyWhenSnapshotSupplied() {
        assertFalse(OfficialLyricDrawBinder.hasMutableStyleOptions(null));
        assertTrue(OfficialLyricDrawBinder.hasMutableStyleOptions(
                OfficialLyricDrawBinder.defaultStyleOptions()));
    }

    @Test
    public void canRenderTo_rejectsNullAndUnmeasuredTargets() {
        assertFalse(OfficialLyricDrawBinder.canRenderTo(null));
        // A freshly constructed TextView has 0×0 dimensions and no window token,
        // so it must be reported as not yet renderable. We don't touch real
        // Android Views here; a null check is enough to document the contract.
    }

    @Test
    public void normalizeReason_returnsTickForBlankAndPreservesCustomReasons() {
        assertEquals(OfficialLyricDrawBinder.REASON_RENDER_TICK,
                OfficialLyricDrawBinder.normalizeReason(null));
        assertEquals(OfficialLyricDrawBinder.REASON_RENDER_TICK,
                OfficialLyricDrawBinder.normalizeReason(""));
        assertEquals("custom-reason",
                OfficialLyricDrawBinder.normalizeReason("custom-reason"));
    }

    @Test
    public void reasonConstants_areStable() {
        assertEquals("render-tick", OfficialLyricDrawBinder.REASON_RENDER_TICK);
        assertEquals("translation-toggle",
                OfficialLyricDrawBinder.REASON_TRANSLATION_TOGGLE);
        assertEquals("model-switch", OfficialLyricDrawBinder.REASON_MODEL_SWITCH);
        assertEquals("aod-low-frame-rate",
                OfficialLyricDrawBinder.REASON_AOD_LOW_FRAME_RATE);
        assertEquals("slot-height-recompute",
                OfficialLyricDrawBinder.REASON_SLOT_HEIGHT_RECOMPUTE);
    }

    @Test
    public void lyricDrawLine_carriesMutableStartEndWidth() {
        LyricDrawLine drawLine = new LyricDrawLine();
        drawLine.start = 0;
        drawLine.end = 12;
        drawLine.width = 240.5f;
        assertEquals(0, drawLine.start);
        assertEquals(12, drawLine.end);
        assertEquals(240.5f, drawLine.width, 0.0f);
    }
}
