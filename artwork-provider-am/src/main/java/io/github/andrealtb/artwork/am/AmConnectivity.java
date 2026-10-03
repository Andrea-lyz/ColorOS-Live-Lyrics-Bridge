package io.github.andrealtb.artwork.am;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** One process-wide observer. A restored validated network invalidates only transport failure caches. */
final class AmConnectivity {
    static final class Epoch {
        private final String instance = java.util.UUID.randomUUID().toString();
        private Object network;
        private boolean validated;
        private long sequence;
        synchronized String update(Object next, boolean ready) {
            if (ready && (!validated || !java.util.Objects.equals(network, next))) ++sequence;
            network = next; validated = ready;
            return instance + "/" + sequence;
        }
    }
    private static AmConnectivity instance;
    private final ConnectivityManager manager;
    private final Epoch epoch = new Epoch();
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { token(); }
        @Override public void onLost(Network network) { token(); }
    };
    static synchronized AmConnectivity get(Context context) {
        if (instance == null) instance = new AmConnectivity(context.getApplicationContext());
        return instance;
    }
    private AmConnectivity(Context context) {
        manager = context.getSystemService(ConnectivityManager.class);
        token();
        if (manager != null) try { manager.registerDefaultNetworkCallback(callback); }
        catch (RuntimeException ignored) { /* synchronous snapshots still invalidate changed networks */ }
    }
    synchronized String token() {
        try {
            Network network = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities caps = network == null ? null : manager.getNetworkCapabilities(network);
            return epoch.update(network, caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        } catch (RuntimeException error) { return epoch.update(null, false); }
    }
    static boolean transport(String reason) {
        return reason.equals("network_io") || reason.equals("network_deadline") || reason.equals("network_stage_timeout")
                || reason.equals("network_connect_timeout")
                || reason.equals("network_headers_timeout") || reason.equals("network_read_timeout");
    }
}
