package io.github.andrealtb.lockscreenlyrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.util.Log;

import java.util.ArrayList;

public final class UniversalPlayerBindingsReceiver extends BroadcastReceiver {
    private static final String TAG = "LockscreenLyrics";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!UniversalPlayerBridgeContract.ACTION_PLAYER_BINDINGS_CHANGED.equals(intent.getAction())) {
            return;
        }
        if (!UniversalPlayerBridgeContract.isAcceptedIdentity(
                intent.getStringExtra(UniversalPlayerBridgeContract.EXTRA_PROVIDER_IDENTITY))) {
            Log.w(TAG, LyricLogFormatter.format(
                    context.getPackageName(),
                    LyricLogFormatter.Area.SETTINGS,
                    "bindings-rejected",
                    "Rejected Universal Player Provider bindings | identity mismatch"));
            return;
        }
        ArrayList<String> packages = intent.getStringArrayListExtra(
                UniversalPlayerBridgeContract.EXTRA_BOUND_PACKAGES);
        UniversalPlayerBridgeContract.persist(
                context.getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, Context.MODE_PRIVATE),
                true,
                packages);
        Log.i(TAG, LyricLogFormatter.format(
                context.getPackageName(),
                LyricLogFormatter.Area.SETTINGS,
                "bindings-applied",
                "Stored Universal Player Provider bindings | count="
                        + UniversalPlayerBridgeContract.boundPackages(
                                context.getSharedPreferences(
                                        LyricUiSettings.PREFERENCES_NAME,
                                        Context.MODE_PRIVATE)).size()));
    }
}
