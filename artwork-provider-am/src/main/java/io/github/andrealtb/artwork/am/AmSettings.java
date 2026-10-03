package io.github.andrealtb.artwork.am;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.util.Locale;

final class AmSettings {
    static SharedPreferences prefs(Context context) { return context.getSharedPreferences("am_artwork", Context.MODE_PRIVATE); }
    static boolean enabled(Context context) { return prefs(context).getBoolean("enabled", false); }
    static String country(Context context) {
        String value = prefs(context).getString("country", "us");
        return value != null && value.matches("[a-zA-Z]{2}") ? value.toLowerCase(Locale.ROOT) : "us";
    }
    static boolean online(Context context) {
        if (!enabled(context)) return false;
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                && (!manager.isActiveNetworkMetered() || prefs(context).getBoolean("metered", false));
    }
    /** Process importance at a stall: separates a stuck call from a process the system stopped running. */
    static int importance() {
        try {
            android.app.ActivityManager.RunningAppProcessInfo info = new android.app.ActivityManager.RunningAppProcessInfo();
            android.app.ActivityManager.getMyMemoryState(info);
            return info.importance;
        } catch (RuntimeException error) { return -1; }
    }
    static void trace(Context context, String event, String reason) {
        if (prefs(context).getBoolean("debug", false)) android.util.Log.i("CLL-Artwork-AM",
                "[CLL] level=INFO component=provider/artwork_am area=resource event=" + event + " reason=" + reason);
    }
    static void dimensions(Context context, String event, int width, int height, long bytes) {
        if (prefs(context).getBoolean("debug", false)) android.util.Log.i("CLL-Artwork-AM",
                "[CLL] level=INFO component=provider/artwork_am area=resource event=" + event
                        + " width=" + width + " height=" + height + " bytes=" + bytes);
    }
    static void matching(Context context, String stage, java.util.List<AmIdentity.Track> tracks,
            io.github.andrealtb.artwork.contract.ArtworkQuery query) {
        if (prefs(context).getBoolean("debug", false)) android.util.Log.i("CLL-Artwork-AM",
                "[CLL] level=INFO component=provider/artwork_am area=resource event=ARTWORK_AM_MATCH_CHECK stage="
                        + stage + " " + AmIdentity.diagnostics(tracks, query));
    }
}
