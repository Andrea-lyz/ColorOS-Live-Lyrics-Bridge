package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BuildIdentity;

/**
 * App-side, read-only status request to the Bridge running inside SystemUI. Uses the same
 * protected broadcast as the debug page; SystemUI only answers with its own observations and
 * nothing is changed. A missing reply means "unknown", never "disabled".
 */
public final class ModuleStatusQuery {
    public static final long TIMEOUT_MS = 2_500L;

    public interface Listener {
        /** Called once on the given handler when SystemUI replies. */
        void onReply(String injectedBuildId, long appliedUiRevision);
    }

    private ModuleStatusQuery() {
    }

    /** Returns false when the request could not be sent; the caller then reports no reply. */
    public static boolean send(Context context, Handler handler, Listener listener) {
        try {
            ResultReceiver receiver = new ResultReceiver(handler) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle data) {
                    if (data == null
                            || !"com.android.systemui".equals(data.getString(LyricUiSettings.RESULT_PROCESS))) {
                        return;
                    }
                    listener.onReply(
                            data.getString(BuildIdentity.RUNTIME_BUILD_ID, BuildIdentity.UNKNOWN),
                            data.getLong(BuildIdentity.UI_CONFIG_REVISION, -1L));
                }
            };
            Intent intent = new Intent(LyricUiSettings.ACTION_REQUEST_MODULE_STATUS)
                    .setPackage("com.android.systemui")
                    .putExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER,
                            ResultReceiverTransport.forRemoteProcess(receiver));
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
            context.sendBroadcast(intent);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** The build this App process is running, which is the installed APK. */
    public static String installedBuildId() {
        return BuildConfig.BUILD_ID;
    }
}
