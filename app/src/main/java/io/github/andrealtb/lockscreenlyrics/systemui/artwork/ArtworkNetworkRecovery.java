package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Initial capability delivery is not a recovery; repeated callbacks must not restart playback. */
public final class ArtworkNetworkRecovery {
    private boolean initialized;
    private Object network;
    private boolean validated;
    public boolean update(Object next, boolean ready) {
        boolean recovered = initialized && ready && (!validated || !java.util.Objects.equals(network, next));
        initialized = true; network = next; validated = ready;
        return recovered;
    }
    public static boolean transport(String reason) {
        return reason.equals("network_policy") || reason.equals("network_io") || reason.equals("network_deadline")
                || reason.equals("network_connect_timeout") || reason.equals("network_headers_timeout")
                || reason.equals("network_read_timeout") || reason.equals("network_stage_timeout");
    }
}
