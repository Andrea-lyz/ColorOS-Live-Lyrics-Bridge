package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

/**
 * Asks the Bridge hook inside SystemUI to restart SystemUI (same broadcast and reply as the old
 * appearance page used, now sent from the management Home, plan §14.18). The callback runs once
 * on the main thread: acknowledged, or not answered within the timeout.
 */
public final class SystemUiRestartRequest {
    public interface Listener {
        void onResult(boolean acknowledged);
    }

    private static final long ACK_TIMEOUT_MS = 2_500L;
    private static long sequence;

    private SystemUiRestartRequest() {
    }

    /** Returns false when the broadcast could not be sent (the listener is then not called). */
    public static boolean send(Context context, Listener listener) {
        Handler main = new Handler(Looper.getMainLooper());
        long requestId = ++sequence;
        boolean[] done = {false};
        Runnable timeout = () -> {
            if (done[0]) return;
            done[0] = true;
            listener.onResult(false);
        };
        ResultReceiver receiver = new ResultReceiver(main) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (done[0] || resultData == null
                        || resultData.getLong(LyricUiSettings.RESULT_SYSTEM_UI_RESTART_REQUEST_ID, -1L) != requestId) {
                    return;
                }
                if (resultCode == LyricUiSettings.RESULT_SYSTEM_UI_RESTART_ACKNOWLEDGED
                        && resultData.getBoolean(LyricUiSettings.RESULT_SYSTEM_UI_RESTART_ACCEPTED, false)) {
                    done[0] = true;
                    main.removeCallbacks(timeout);
                    listener.onResult(true);
                }
            }
        };
        try {
            Intent intent = new Intent(LyricUiSettings.ACTION_RESTART_SYSTEM_UI)
                    .setPackage("com.android.systemui")
                    .putExtra(LyricUiSettings.EXTRA_SYSTEM_UI_RESTART_REQUEST_ID, requestId)
                    .putExtra(LyricUiSettings.EXTRA_SYSTEM_UI_RESTART_RESULT_RECEIVER,
                            ResultReceiverTransport.forRemoteProcess(receiver));
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
            context.sendBroadcast(intent);
        } catch (RuntimeException error) {
            return false;
        }
        main.postDelayed(timeout, ACK_TIMEOUT_MS);
        return true;
    }
}
