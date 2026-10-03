package io.github.andrealtb.artwork.am;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;

import io.github.andrealtb.artwork.contract.ArtworkSigningIdentity;

/** Checked on every transaction. Shared UID authorization is a UID boundary, not package isolation. */
final class ArtworkCallerPolicy {
    static final String BRIDGE = "io.github.andrealtb.lockscreenlyrics";
    static final String PREFERENCES = "authorized_client";
    static final String SIGNER = "bridge_signer";

    static int requireAllowed(Context context) {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return uid;
        PackageManager manager = context.getPackageManager();
        try {
            if (uid == manager.getApplicationInfo("com.android.systemui", 0).uid
                    && manager.checkSignatures("android", "com.android.systemui") == PackageManager.SIGNATURE_MATCH) {
                return uid;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Ordinary AOSP or non-platform signed hosts are not implicitly trusted.
        }
        try {
            String pinned = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(SIGNER, "");
            if (pinned != null && !pinned.isEmpty()
                    && uid == manager.getApplicationInfo(BRIDGE, 0).uid
                    && pinned.equals(ArtworkSigningIdentity.read(manager, BRIDGE))) return uid;
        } catch (Exception ignored) {
            // Missing package, user mismatch or changed signer: deny the transaction.
        }
        throw new SecurityException("artwork_caller_denied");
    }
}
