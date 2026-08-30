package io.github.andrealtb.lockscreenlyrics.diagnostics;

import java.util.Locale;

/** Maps leftover natural-language module logs onto stable [CLL] events. */
public final class LegacyLogEventMap {
    public static final class Mapping {
        public final BridgeDebugArea area;
        public final String event;
        public final boolean alwaysOn;

        Mapping(BridgeDebugArea area, String event, boolean alwaysOn) {
            this.area = area;
            this.event = event;
            this.alwaysOn = alwaysOn;
        }
    }

    private LegacyLogEventMap() {
    }

    public static Mapping classify(String message) {
        String value = message == null ? "" : message;
        String lower = value.toLowerCase(Locale.ROOT);
        if (contains(value, "Observed LyricsRecyclerView attachment")) {
            return new Mapping(BridgeDebugArea.AOD, BridgeEvents.RECYCLER_ATTACHED, false);
        }
        if (contains(lower, "stabilized") && contains(lower, "scroll")) {
            return new Mapping(
                    BridgeDebugArea.AOD, BridgeEvents.RECYCLER_SCROLL_STABILIZED, false);
        }
        if (contains(value, "Primed LyricsRecyclerView")) {
            return new Mapping(BridgeDebugArea.AOD, BridgeEvents.RECYCLER_PRIMED, false);
        }
        if (contains(lower, "layout height changed")) {
            return new Mapping(
                    BridgeDebugArea.AOD, BridgeEvents.OFFICIAL_LAYOUT_HEIGHT_CHANGED, false);
        }
        if (contains(lower, "row scale")) {
            return new Mapping(BridgeDebugArea.AOD, BridgeEvents.OFFICIAL_ROW_SCALE, false);
        }
        if (contains(lower, "setcurrentlyric geometry")) {
            return new Mapping(
                    BridgeDebugArea.AOD, BridgeEvents.SET_CURRENT_LYRIC_GEOMETRY, false);
        }
        if (startsWith(value, "Loaded in")
                || startsWith(value, "Skip process")
                || startsWith(value, "Resolved SystemUI private hooks")
                || startsWith(value, "Official lyric render pipeline")
                || startsWith(value, "Registered protected SystemUI")) {
            return new Mapping(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SYSTEMUI_BOOTSTRAP, true);
        }
        if (startsWith(value, "Hooked ")) {
            return new Mapping(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, true);
        }
        if (startsWith(value, "Failed ")) {
            return new Mapping(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_FAILED, true);
        }
        if (contains(lower, "bridge debug logging") || contains(lower, "debug config")) {
            return new Mapping(BridgeDebugArea.BOOTSTRAP, BridgeEvents.DEBUG_CONFIG_APPLIED, true);
        }
        if (contains(lower, "lyric ui settings")
                || contains(lower, "player translation settings")
                || contains(lower, "content cleanup")) {
            return new Mapping(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SETTINGS_APPLIED, true);
        }
        if (contains(lower, "official lyric payload") || contains(lower, "native lyric")) {
            return new Mapping(BridgeDebugArea.LYRIC, BridgeEvents.NATIVE_LYRIC_RECEIVED, false);
        }
        if (contains(lower, "aod") || contains(lower, "ambient")) {
            return new Mapping(BridgeDebugArea.AOD, BridgeEvents.AOD_TRANSITION, false);
        }
        if (contains(lower, "recyclerview")
                || contains(lower, "recycler")
                || contains(lower, "prime")) {
            return new Mapping(BridgeDebugArea.AOD, BridgeEvents.SURFACE_STATE_CHANGED, false);
        }
        if (contains(lower, "draw")
                || contains(lower, "render")
                || contains(lower, "frame")
                || contains(lower, "glow")) {
            return new Mapping(BridgeDebugArea.RENDERER, BridgeEvents.RENDER_STATE_CHANGED, false);
        }
        if (contains(lower, "translation") || contains(lower, "翻译")) {
            return new Mapping(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.DETAIL, false);
        }
        if (contains(lower, "provider")) {
            return new Mapping(BridgeDebugArea.LYRIC, BridgeEvents.DETAIL, false);
        }
        if (contains(lower, "screen timeout")
                || contains(lower, "wake lock")
                || contains(lower, "screen-state")
                || contains(lower, "playback")
                || contains(lower, "mediasession")
                || contains(lower, "metadata")
                || contains(lower, "seedling")) {
            return new Mapping(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, false);
        }
        if (contains(lower, "parser")
                || contains(lower, "parse")
                || contains(lower, "lrc")
                || contains(lower, "ttml")
                || contains(lower, "yrc")) {
            return new Mapping(BridgeDebugArea.LYRIC, BridgeEvents.LYRIC_PARSED, false);
        }
        if (contains(lower, "hook")
                || contains(lower, "dexkit")
                || contains(lower, "classloader")
                || contains(lower, "setting")
                || contains(lower, "module")) {
            return new Mapping(BridgeDebugArea.BOOTSTRAP, BridgeEvents.DETAIL, false);
        }
        if (contains(lower, "durationms")
                || contains(lower, "queue")
                || contains(lower, "perf")) {
            return new Mapping(BridgeDebugArea.PERFORMANCE, BridgeEvents.PERF_SAMPLE, false);
        }
        return new Mapping(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, false);
    }

    private static boolean contains(String value, String needle) {
        return value.contains(needle);
    }

    private static boolean startsWith(String value, String prefix) {
        return value.startsWith(prefix);
    }
}
