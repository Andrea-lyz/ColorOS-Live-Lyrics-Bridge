package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.andrealtb.lockscreenlyrics.render.LyricSide;

public final class DuetAlignmentPolicyTest {
    private static final int START = LyricUiConfig.ALIGN_START;
    private static final int CENTER = LyricUiConfig.ALIGN_CENTER;
    private static final int END = LyricUiConfig.ALIGN_END;

    @Test
    public void leadSideKeepsTheConfiguredEdgeAndTheOtherSideMirrorsIt() {
        assertEquals(START, align(START, LyricSide.PRIMARY));
        assertEquals(END, align(START, LyricSide.SECONDARY));
        assertEquals(END, align(END, LyricSide.PRIMARY));
        assertEquals(START, align(END, LyricSide.SECONDARY));
    }

    @Test
    public void centeredLayoutSplitsDuetRowsToBothEdges() {
        assertEquals(START, align(CENTER, LyricSide.PRIMARY));
        assertEquals(END, align(CENTER, LyricSide.SECONDARY));
    }

    @Test
    public void rowsWithoutASideOrWithTheSwitchOffKeepTheConfiguredAlignment() {
        for (int configured : new int[]{START, CENTER, END}) {
            assertEquals(configured, align(configured, LyricSide.UNSPECIFIED));
            assertEquals(configured, align(configured, null));
            for (LyricSide side : LyricSide.values()) {
                assertEquals(configured,
                        LyricUiLayoutPolicy.effectiveAlignment(configured, false, side));
            }
        }
    }

    @Test
    public void duetSplitDefaultsOnAndSurvivesOlderStoredConfigs() {
        assertEquals(true, LyricUiConfig.defaults().duetSplitEnabled);
        java.util.Map<String, Object> stored =
                LyricUiConfigCodec.encode(LyricUiConfig.defaults());
        stored.remove(LyricUiConfigCodec.DUET_SPLIT_ENABLED);
        assertEquals(true,
                LyricUiConfigCodec.decode(stored, LyricUiConfig.defaults(), false).duetSplitEnabled);
    }

    private static int align(int configured, LyricSide side) {
        return LyricUiLayoutPolicy.effectiveAlignment(configured, true, side);
    }
}
