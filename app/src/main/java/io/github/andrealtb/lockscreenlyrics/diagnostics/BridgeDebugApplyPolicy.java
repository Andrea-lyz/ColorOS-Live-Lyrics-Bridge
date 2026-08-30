package io.github.andrealtb.lockscreenlyrics.diagnostics;

/** Decides when SystemUI should reinstall debug sinks or announce DEBUG_CONFIG_APPLIED. */
public final class BridgeDebugApplyPolicy {
    public static final String SOURCE_SETTINGS_BROADCAST = "settings-broadcast";

    private BridgeDebugApplyPolicy() {
    }

    public static boolean shouldReconfigure(
            BridgeDebugConfig previous,
            BridgeDebugConfig next) {
        BridgeDebugConfig left = previous == null ? BridgeDebugConfig.disabled() : previous;
        BridgeDebugConfig right = next == null ? BridgeDebugConfig.disabled() : next;
        return !left.equals(right);
    }

    public static boolean shouldAnnounce(
            BridgeDebugConfig previous,
            BridgeDebugConfig next,
            boolean alreadyAnnounced,
            String source) {
        if (SOURCE_SETTINGS_BROADCAST.equals(source)) {
            return true;
        }
        if (!alreadyAnnounced) {
            return true;
        }
        return shouldReconfigure(previous, next);
    }
}
