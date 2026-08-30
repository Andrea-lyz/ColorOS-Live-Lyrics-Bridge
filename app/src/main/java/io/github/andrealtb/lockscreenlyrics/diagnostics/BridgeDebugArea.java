package io.github.andrealtb.lockscreenlyrics.diagnostics;

/** Settings-facing debug areas. Names match the 4.0 plan and log `area=` field. */
public enum BridgeDebugArea {
    BOOTSTRAP("bootstrap"),
    MEDIA("media"),
    LYRIC("lyric"),
    RENDERER("renderer"),
    AOD("aod"),
    PLAYER_SPECIAL("player-special"),
    PERFORMANCE("performance");

    public final String key;

    BridgeDebugArea(String key) {
        this.key = key;
    }

    public static BridgeDebugArea fromKey(String key) {
        if (key == null || key.isEmpty()) {
            return BOOTSTRAP;
        }
        for (BridgeDebugArea area : values()) {
            if (area.key.equals(key)) {
                return area;
            }
        }
        return BOOTSTRAP;
    }
}
