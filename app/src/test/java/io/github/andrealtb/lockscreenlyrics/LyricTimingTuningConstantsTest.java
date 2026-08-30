package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * Locks the tuning constants extracted from {@link LockscreenLyricsModule} so that
 * any future retune shows up as a deliberate test change rather than a silent
 * drift. The known-good values come from the AOD / external-lyric / official-row
 * reference logs documented in <code>AGENTS.md</code>; update both the constant
 * and this assertion in the same commit.
 */
public final class LyricTimingTuningConstantsTest {

    @Test
    public void aodRefreshPacingMatchesKnownGoodBaseline() {
        assertEquals(180L, LyricTimingTuningConstants.Aod.ACTIVE_LYRIC_REFRESH_DELAY_MS);
        assertEquals(240L, LyricTimingTuningConstants.Aod.WORD_PROGRESS_TO_LINE_ANIMATION_MS);
        assertArrayEquals(
                new long[]{48L, 120L, 240L, 420L},
                LyricTimingTuningConstants.Aod.HANDOFF_REDRAW_DELAYS_MS);
    }

    @Test
    public void officialLyricRowScaleSuppressMatchesKnownGoodBaseline() {
        // 180 ms is the known-good suppress window. Do not change without
        // comparing against Observed LyricsRecyclerView attachment /
        // Stabilized LyricsRecyclerView scroll logs.
        assertEquals(180L,
                LyricTimingTuningConstants.OfficialLyric.ROW_SCALE_ATTACH_SUPPRESS_MS);
        assertEquals(4f,
                LyricTimingTuningConstants.OfficialLyric.INACTIVE_BLUR_RADIUS_PX, 0f);
        assertEquals(0.55f,
                LyricTimingTuningConstants.OfficialLyric.BLUR_ZERO_THRESHOLD_PX, 0f);
        assertFalse(LyricTimingTuningConstants.OfficialLyric.DRAW_FRAME_REUSE_ENABLED);
        assertEquals(1_200L,
                LyricTimingTuningConstants.OfficialLyric.DRAW_FRAME_TRANSIENT_MISS_GRACE_MS);
        assertEquals(120L,
                LyricTimingTuningConstants.OfficialLyric.FRAME_DECISION_LOG_INTERVAL_MS);
        assertEquals(5_000L,
                LyricTimingTuningConstants.OfficialLyric.ADAPTER_SUPPRESSION_LOG_INTERVAL_MS);
        assertEquals(1_400L,
                LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS);
        assertEquals(320L,
                LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_HANDOFF_MIN_MASK_MS);
        assertEquals(0.001f,
                LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_HANDOFF_HIDDEN_ALPHA, 0f);
        assertEquals(1f,
                LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_VISIBLE_ALPHA, 0f);
        assertEquals(420L,
                LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS);
        assertEquals(1_800L,
                LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_LYRIC_ROW_REBIND_WINDOW_MS);
        assertEquals(1_800L,
                LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_TRACK_RESET_POSITION_GUARD_MS);
        assertEquals(3_000L,
                LyricTimingTuningConstants.OfficialLyric.SYSTEMUI_TRACK_RESET_STALE_POSITION_MS);
    }

    @Test
    public void lyricGeneralRendererSchedulingMatchesKnownGoodBaseline() {
        assertEquals(16L,
                LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_FRAME_DELAY_MS);
        assertEquals(96L,
                LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_STATIC_FRAME_DELAY_MS);
        assertEquals(48L,
                LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_RETRY_DELAY_MS);
        assertEquals(48f,
                LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_CENTER_OFFSET_DP, 0f);
        assertEquals(20f,
                LyricTimingTuningConstants.LyricGeneral.ACTIVE_LYRIC_POSITION_SHIFT_UP_DP, 0f);
        assertEquals(900L,
                LyricTimingTuningConstants.LyricGeneral.LYRIC_RECYCLER_SCREEN_STATE_SETTLE_MS);
        assertEquals(360L,
                LyricTimingTuningConstants.LyricGeneral.LYRIC_RECYCLER_SET_CURRENT_SETTLE_MS);
        assertEquals(1_500L,
                LyricTimingTuningConstants.LyricGeneral.PLAYBACK_POSITION_JUMP_MS);
        assertEquals(4,
                LyricTimingTuningConstants.LyricGeneral.BOUND_FRAME_RETRY_MAX);
        assertEquals(6,
                LyricTimingTuningConstants.LyricGeneral.SURFACE_RENDER_PASS_MAX_FRAMES);
        assertEquals(120L,
                LyricTimingTuningConstants.LyricGeneral.SURFACE_RENDER_PASS_MAX_MS);
        assertEquals(1_200L,
                LyricTimingTuningConstants.LyricGeneral.SURFACE_PROVISIONAL_DRAW_GRACE_MS);
        assertEquals(1_500L,
                LyricTimingTuningConstants.LyricGeneral.UI_STYLE_SETTINGS_RELOAD_MS);
        assertArrayEquals(
                new long[]{96L, 240L, 520L, 1_200L, 2_400L},
                LyricTimingTuningConstants.LyricGeneral.VISIBILITY_RECOVERY_DELAYS_MS);
    }

    @Test
    public void screenTimeoutWatcherIntervalsMatchKnownGoodBaseline() {
        assertEquals(8_000L,
                LyricTimingTuningConstants.ScreenTimeout.USER_ACTIVITY_INTERVAL_MS);
        assertEquals(15_000L,
                LyricTimingTuningConstants.ScreenTimeout.WAKE_LOCK_LEASE_MS);
        assertEquals(12_000L,
                LyricTimingTuningConstants.ScreenTimeout.VISIBLE_LYRIC_VIEW_MAX_AGE_MS);
        assertEquals(3_000L,
                LyricTimingTuningConstants.ScreenTimeout.MODEL_EVIDENCE_GRACE_MS);
        assertEquals(500L,
                LyricTimingTuningConstants.ScreenTimeout.USER_PRESENT_RECHECK_DELAY_MS);
        assertEquals(250L,
                LyricTimingTuningConstants.ScreenTimeout.KEYGUARD_STATE_CACHE_MS);
        assertEquals(250L,
                LyricTimingTuningConstants.ScreenTimeout.VISIBLE_LYRIC_NOTE_THROTTLE_MS);
    }

    @Test
    public void translationThrottleAndFrameIntervalsMatchKnownGoodBaseline() {
        assertEquals(5_000L,
                LyricTimingTuningConstants.Translation.TOGGLE_CONFIG_LOG_THROTTLE_MS);
        assertEquals(16L,
                LyricTimingTuningConstants.Translation.TOGGLE_LAYOUT_FRAME_MS);
    }
}
