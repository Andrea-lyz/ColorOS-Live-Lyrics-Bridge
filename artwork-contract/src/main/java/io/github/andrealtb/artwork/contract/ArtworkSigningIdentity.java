package io.github.andrealtb.artwork.contract;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact current signer set. A rotated/different identity requires explicit reselection. */
public final class ArtworkSigningIdentity {
    private ArtworkSigningIdentity() {}

    public static String read(PackageManager manager, String packageName) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo info = manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) throw new SecurityException("missing_signature");
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures;
        }
        if (signatures == null || signatures.length == 0) throw new SecurityException("missing_signature");
        List<String> digests = new ArrayList<>();
        for (Signature signature : signatures) {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            digests.add(hex.toString());
        }
        Collections.sort(digests);
        return String.join(":", digests);
    }
}
