package io.github.andrealtb.lockscreenlyrics.diagnostics;

/** Stable Bridge log event names. Do not use natural-language sentences as event types. */
public final class BridgeEvents {
    public static final String SYSTEMUI_BOOTSTRAP = "SYSTEMUI_BOOTSTRAP";
    public static final String HOOK_INSTALLED = "HOOK_INSTALLED";
    public static final String HOOK_FAILED = "HOOK_FAILED";
    public static final String DEBUG_CONFIG_APPLIED = "DEBUG_CONFIG_APPLIED";
    public static final String SETTINGS_APPLIED = "SETTINGS_APPLIED";
    public static final String NATIVE_LYRIC_RECEIVED = "NATIVE_LYRIC_RECEIVED";
    public static final String LYRIC_PARSED = "LYRIC_PARSED";
    public static final String LYRIC_PARSE_REJECTED = "LYRIC_PARSE_REJECTED";
    public static final String SESSION_REDUCED = "SESSION_REDUCED";
    public static final String SURFACE_STATE_CHANGED = "SURFACE_STATE_CHANGED";
    public static final String RENDER_STATE_CHANGED = "RENDER_STATE_CHANGED";
    public static final String AOD_TRANSITION = "AOD_TRANSITION";
    public static final String PERF_SAMPLE = "PERF_SAMPLE";
    public static final String RECYCLER_ATTACHED = "RECYCLER_ATTACHED";
    public static final String RECYCLER_SCROLL_STABILIZED = "RECYCLER_SCROLL_STABILIZED";
    public static final String RECYCLER_PRIMED = "RECYCLER_PRIMED";
    public static final String OFFICIAL_LAYOUT_HEIGHT_CHANGED = "OFFICIAL_LAYOUT_HEIGHT_CHANGED";
    public static final String OFFICIAL_ROW_SCALE = "OFFICIAL_ROW_SCALE";
    public static final String SET_CURRENT_LYRIC_GEOMETRY = "SET_CURRENT_LYRIC_GEOMETRY";
    public static final String ARTWORK_PROBE = "ARTWORK_PROBE";
    public static final String TRANSLATION_ACTION_REBIND = "TRANSLATION_ACTION_REBIND";
    public static final String DETAIL = "DETAIL";
    public static final String FAILURE = "FAILURE";

    private BridgeEvents() {
    }
}
