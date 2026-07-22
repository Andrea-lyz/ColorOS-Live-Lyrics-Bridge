package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.content.Intent;

import io.github.andrealtb.lockscreenlyrics.protocol.ExternalLyricProtocol;

/** Sends a Bridge-module player capture directly to the SystemUI v4 ingress. */
final class ExternalLyricDirectSender {
    private ExternalLyricDirectSender() {
    }

    static void sendModuleCapture(Context context, Intent capture) {
        if (context == null || capture == null) {
            throw new IllegalArgumentException("Direct broadcast context and capture are required");
        }
        String playerPackage = context.getPackageName();
        capture.setAction(LyricInfoContract.ACTION_DIRECT_EXTERNAL_LYRIC_CAPTURED);
        capture.setPackage(ExternalLyricProtocol.SYSTEM_UI_PACKAGE);
        capture.putExtra(
                LyricInfoContract.EXTRA_EXTERNAL_PROTOCOL_VERSION,
                LyricInfoContract.EXTERNAL_PROTOCOL_VERSION_CURRENT);
        capture.putExtra(LyricInfoContract.EXTRA_EXTERNAL_PLAYER_PACKAGE, playerPackage);
        capture.putExtra(LyricInfoContract.EXTRA_EXTERNAL_SENDER_PACKAGE, playerPackage);
        capture.putExtra(
                LyricInfoContract.EXTRA_EXTERNAL_SENDER_KIND,
                LyricInfoContract.EXTERNAL_SENDER_KIND_MODULE);
        context.sendBroadcast(capture);
    }
}
