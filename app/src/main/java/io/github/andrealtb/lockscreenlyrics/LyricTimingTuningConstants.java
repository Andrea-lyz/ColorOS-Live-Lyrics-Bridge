package io.github.andrealtb.lockscreenlyrics;

/**
 * Tuning constants extracted from {@link LockscreenLyricsModule}. Every value here has an
 * associated device-log origin; do not retune without comparing the change against the
 * AOD / external-lyric / official-row reference logs called out in
 * <code>AGENTS.md</code>.
 *
 * <p>Naming follows the legacy prefix carried over from the host class so call sites can move
 * to {@code LyricTimingTuningConstants.<GROUP>_<NAME>} without renaming the symbol itself.
 * Groups are split by the SystemUI sub-system they govern:</p>
 *
 * <ul>
 *     <li>{@link Aod} — always-on display row refresh and animation pacing.</li>
 *     <li>{@link OfficialLyric} — official {@code LyricsRecyclerView} row scale, blur,
 *         draw-frame reuse, and SystemUI-driven handoff masks.</li>
 *     <li>{@link ExternalLyric} — external Provider ingest, soft handoff, model readiness,
 *         playback-reset guards, and Provider retry escalations.</li>
 *     <li>{@link LyricGeneral} — cross-cutting renderer scheduling, lyric cache, visibility
 *         recovery, and provisional-draw grace windows.</li>
 *     <li>{@link ScreenTimeout} — lock-screen / keyguard probe intervals and wake-lock
 *         leasing windows.</li>
 *     <li>{@link Translation} — translation-button throttle and layout-frame pacing.</li>
 * </ul>
 *
 * <p>All values are package-private because they are tuning parameters for the hook module,
 * not part of any external API contract. Mirror values that participate in the v4 broadcast
 * contract (size limits, protocol version) live in
 * {@link io.github.andrealtb.lockscreenlyrics.protocol.ExternalLyricProtocol} instead.</p>
 */
final class LyricTimingTuningConstants {

    private LyricTimingTuningConstants() {
    }

    /** Always-on display row refresh pacing. */
    static final class Aod {
        /**
         * Refresh interval for AOD word-progress while the device is in always-on mode.
         * Tuned at ~5 Hz: high enough to look smooth, low enough to avoid waking the
         * display driver more than necessary.
         */
        static final long ACTIVE_LYRIC_REFRESH_DELAY_MS = 180L;

        /**
         * Word-progress → line-progress crossfade animation duration when AOD switches
         * between sub-word timing and full-line timing.
         */
        static final long WORD_PROGRESS_TO_LINE_ANIMATION_MS = 240L;

        /** Redraw probes issued right after the AOD ↔ SystemUI handoff. */
        static final long[] HANDOFF_REDRAW_DELAYS_MS = {
                48L, 120L, 240L, OfficialLyric.SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS
        };

        private Aod() {
        }
    }

    /** Official {@code LyricsRecyclerView} row scale, blur, and draw-frame reuse. */
    static final class OfficialLyric {
        /**
         * Suppresses official row scale animation for this many ms after
         * {@code LyricsRecyclerView} first attaches. Was previously 800 ms and 0 ms;
         * 180 ms is the known-good value that lets the first frame settle without
         * desynchronizing module state from official SystemUI state. Do not change
         * without comparing against
         * <code>Observed LyricsRecyclerView attachment</code> /
         * <code>Stabilized LyricsRecyclerView scroll</code> logs.
         */
        static final long ROW_SCALE_ATTACH_SUPPRESS_MS = 180L;

        /** Blur radius for inactive rows when official blur is enabled. */
        static final float INACTIVE_BLUR_RADIUS_PX = 4f;

        /** Alpha threshold below which the blur contribution is skipped. */
        static final float BLUR_ZERO_THRESHOLD_PX = 0.55f;

        /**
         * Reusing a draw frame by {@code TextView} identity can carry the old surface's
         * adapter position into the new surface during SystemUI cross-fades. Resolve
         * every frame from the current binding.
         */
        static final boolean DRAW_FRAME_REUSE_ENABLED = false;

        /** Grace window that lets a transient missing draw frame recover before logging. */
        static final long DRAW_FRAME_TRANSIENT_MISS_GRACE_MS = 1_200L;

        /** Throttle for per-frame decision logs. */
        static final long FRAME_DECISION_LOG_INTERVAL_MS = 120L;

        /** Throttle for adapter suppression diagnostic logs. */
        static final long ADAPTER_SUPPRESSION_LOG_INTERVAL_MS = 5_000L;

        /** Upper bound for SystemUI model handoff freshness; past this the model is dropped. */
        static final long SYSTEMUI_LYRIC_MODEL_HANDOFF_MAX_MS = 1_400L;

        /**
         * Minimum mask window during the SystemUI handoff. Smaller values risk showing the
         * pre-handoff frame for a single paint cycle.
         */
        static final long SYSTEMUI_LYRIC_HANDOFF_MIN_MASK_MS = 320L;

        /** Alpha used to hide the official row while the SystemUI handoff is in flight. */
        static final float SYSTEMUI_LYRIC_HANDOFF_HIDDEN_ALPHA = 0.001f;

        /** Alpha used when the official row is fully visible after the handoff. */
        static final float SYSTEMUI_LYRIC_VISIBLE_ALPHA = 1f;

        /** Cross-fade duration from hidden → visible after the SystemUI handoff. */
        static final long SYSTEMUI_LYRIC_HANDOFF_FADE_IN_MS = 420L;

        /** Window during which a row rebind can still be treated as the same logical row. */
        static final long SYSTEMUI_LYRIC_ROW_REBIND_WINDOW_MS = 1_800L;

        /** Position-delta guard that suppresses a track reset when the delta is too small. */
        static final long SYSTEMUI_TRACK_RESET_POSITION_GUARD_MS = 1_800L;

        /** Stale position window after which a track reset is considered authoritative. */
        static final long SYSTEMUI_TRACK_RESET_STALE_POSITION_MS = 3_000L;

        private OfficialLyric() {
        }
    }

    /** External Provider ingest, soft handoff, model readiness, and retry escalations. */
    static final class ExternalLyric {
        /** Delay before rebroadcasting an external lyric update to SystemUI. */
        static final long REBROADCAST_DELAY_MS = 2_000L;

        /**
         * Soft-handoff mask window: external lyric updates arriving within this window
         * after a SystemUI transition are merged rather than rejected.
         */
        static final long SOFT_HANDOFF_MASK_MS = 2_200L;

        /** Minimum mask to keep the external model considered "ready" during handoff. */
        static final long MODEL_READY_MASK_MS = 1_200L;

        /** Mask for {@code LyricsRecyclerView} state checks during external ingest. */
        static final long RECYCLER_MASK_MS = 680L;

        /**
         * Minimum mask that lets a custom external draw frame survive the recycler
         * recycle pass.
         */
        static final long CUSTOM_FRAME_MIN_MASK_MS = 220L;

        /**
         * Recovery delay after the external lyric mode is forced back to the module
         * source. After this delay the module re-attempts the official row state.
         */
        static final long MODE_RECOVERY_MS = 3_000L;

        /** Soft-handoff refresh escalation: probes spaced for retry convergence. */
        static final long[] SOFT_HANDOFF_REFRESH_DELAYS_MS = {
                16L, 80L, 180L, 360L, 720L, 1_200L, 1_800L, 2_120L
        };

        /** Mode-recovery escalation: faster than soft handoff to recover from a forced reset. */
        static final long[] MODE_RECOVERY_REFRESH_DELAYS_MS = {
                48L, 160L, 360L, 760L, 1_240L, 1_840L, 2_480L
        };

        /** Settle window after the external row scale animation begins. */
        static final long ROW_SCALE_SETTLE_MS = 900L;

        /** Grace window for a handoff restart to converge before declaring it stale. */
        static final long HANDOFF_RESTART_GRACE_MS = 260L;

        /**
         * Minimum playback position offset to treat an external "reset" as a real
         * position jump rather than a transient flapping event.
         */
        static final long PLAYBACK_RESET_MIN_POSITION_MS = 1_000L;

        /** Retry interval while waiting for the external model to surface. */
        static final long MODEL_WAIT_RETRY_MS = 120L;

        /** Maximum number of consecutive track-generation resets before forced fallback. */
        static final long TRACK_GENERATION_RESET_MAX = 2L;

        /** Retry escalation when promoting an external lyric to official state. */
        static final long[] PROMOTION_RETRY_DELAYS_MS = {
                120L,
                360L,
                900L
        };

        /** Maximum age for SystemUI's external-lyric load context before being discarded. */
        static final long SYSTEMUI_LOAD_CONTEXT_MAX_AGE_MS = 15_000L;

        /** Maximum age for SystemUI's external-playback handoff context. */
        static final long SYSTEMUI_PLAYBACK_HANDOFF_CONTEXT_MAX_AGE_MS = 3_000L;

        private ExternalLyric() {
        }
    }

    /** Renderer scheduling, cache TTL, visibility recovery, and surface draw passes. */
    static final class LyricGeneral {
        /** TTL for the in-memory lyric cache; older entries are evicted on the next read. */
        static final long CACHE_MAX_AGE_MS = 5 * 60 * 1000L;

        /** Maximum number of tracks kept in the lyric cache before LRU eviction. */
        static final int CACHE_MAX_ENTRIES = 24;

        /**
         * Confirmation window in which a stale Salt fallback is still considered valid
         * before the module is forced to refresh.
         */
        static final long SALT_STALE_FALLBACK_CONFIRM_WINDOW_MS = 8_000L;

        /** Delay between detecting a player metadata change and publishing the lyric. */
        static final long PLAYER_METADATA_LYRIC_PUBLICATION_DELAY_MS = 500L;

        /** Display-rate invalidation interval for word-timed progress. */
        static final long ACTIVE_LYRIC_FRAME_DELAY_MS = 16L;

        /** Slower invalidation interval when only the static text needs refresh. */
        static final long ACTIVE_LYRIC_STATIC_FRAME_DELAY_MS = 96L;

        /** Retry interval for the active lyric render pass. */
        static final long ACTIVE_LYRIC_RETRY_DELAY_MS = 48L;

        /** Vertical offset (dp) used to nudge the active lyric to the centre line. */
        static final float ACTIVE_LYRIC_CENTER_OFFSET_DP = 48f;

        /** Vertical offset (dp) used to raise the active lyric above its resting position. */
        static final float ACTIVE_LYRIC_POSITION_SHIFT_UP_DP = 20f;

        /** Settle window for {@code screen-state} after the recycler first attaches. */
        static final long LYRIC_RECYCLER_SCREEN_STATE_SETTLE_MS = 900L;

        /** Settle window for {@code setCurrentLyric} after the recycler first attaches. */
        static final long LYRIC_RECYCLER_SET_CURRENT_SETTLE_MS = 360L;

        /** Position-delta threshold that counts as a "jump" for lyric playback. */
        static final long PLAYBACK_POSITION_JUMP_MS = 1_500L;

        /** Maximum number of retry passes for the bound-frame reconciliation loop. */
        static final int BOUND_FRAME_RETRY_MAX = 4;

        /** Maximum frames per render pass before the surface is force-committed. */
        static final int SURFACE_RENDER_PASS_MAX_FRAMES = 6;

        /** Maximum wall-clock per render pass. */
        static final long SURFACE_RENDER_PASS_MAX_MS = 120L;

        /** Grace window during which a provisional draw frame is allowed to survive. */
        static final long SURFACE_PROVISIONAL_DRAW_GRACE_MS = 1_200L;

        /** Cooldown after reloading the lyric UI style settings file. */
        static final long UI_STYLE_SETTINGS_RELOAD_MS = 1_500L;

        /** First probe in the visibility-recovery escalation. */
        static final long VISIBILITY_RECOVERY_FIRST_DELAY_MS = 96L;

        /** Second probe in the visibility-recovery escalation. */
        static final long VISIBILITY_RECOVERY_SECOND_DELAY_MS = 240L;

        /** Third probe in the visibility-recovery escalation. */
        static final long VISIBILITY_RECOVERY_FINAL_DELAY_MS = 520L;

        /** Fourth probe in the visibility-recovery escalation. */
        static final long VISIBILITY_RECOVERY_LONG_DELAY_MS = 1_200L;

        /** Fifth probe in the visibility-recovery escalation. */
        static final long VISIBILITY_RECOVERY_LAST_DELAY_MS = 2_400L;

        /** Ordered visibility-recovery escalation (first → last). */
        static final long[] VISIBILITY_RECOVERY_DELAYS_MS = {
                VISIBILITY_RECOVERY_FIRST_DELAY_MS,
                VISIBILITY_RECOVERY_SECOND_DELAY_MS,
                VISIBILITY_RECOVERY_FINAL_DELAY_MS,
                VISIBILITY_RECOVERY_LONG_DELAY_MS,
                VISIBILITY_RECOVERY_LAST_DELAY_MS
        };

        private LyricGeneral() {
        }
    }

    /** Lock-screen / keyguard probe intervals and wake-lock leasing windows. */
    static final class ScreenTimeout {
        /** User-activity polling interval while the screen timeout watcher is active. */
        static final long USER_ACTIVITY_INTERVAL_MS = 8_000L;

        /** Wake-lock lease duration for the screen-timeout listener. */
        static final long WAKE_LOCK_LEASE_MS = 15_000L;

        /** Maximum age for a visible lyric view before it is treated as stale. */
        static final long VISIBLE_LYRIC_VIEW_MAX_AGE_MS = 12_000L;

        /** Grace window after which model evidence is required for a lyric commit. */
        static final long MODEL_EVIDENCE_GRACE_MS = 3_000L;

        /** Re-check delay after the user is observed present. */
        static final long USER_PRESENT_RECHECK_DELAY_MS = 500L;

        /** Cache lifetime for the keyguard-state snapshot. */
        static final long KEYGUARD_STATE_CACHE_MS = 250L;

        /** Throttle for "visible lyric" diagnostic notes. */
        static final long VISIBLE_LYRIC_NOTE_THROTTLE_MS = 250L;

        private ScreenTimeout() {
        }
    }

    /** Translation-button diagnostic throttle and layout-frame pacing. */
    static final class Translation {
        /** Throttle for translation-toggle config-change logs. */
        static final long TOGGLE_CONFIG_LOG_THROTTLE_MS = 5_000L;

        /** Layout-frame interval after the translation toggle is invoked. */
        static final long TOGGLE_LAYOUT_FRAME_MS = 16L;

        private Translation() {
        }
    }
}
