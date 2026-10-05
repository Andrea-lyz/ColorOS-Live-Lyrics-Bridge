package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.os.Build;

/** Settings visibility only; runtime color reads additionally require the verified split model. */
final class C17GlowSupport {
    private static volatile Boolean cached;
    private C17GlowSupport() {}

    static boolean matches(int sdk, String systemUiVersion) {
        return sdk >= 37 && systemUiVersion != null && systemUiVersion.startsWith("17.");
    }

    static boolean available(Context context) {
        if (Build.VERSION.SDK_INT < 37) return false;
        Boolean known = cached;
        if (known != null) return known;
        try {
            boolean supported = matches(Build.VERSION.SDK_INT,
                    context.getPackageManager().getPackageInfo("com.android.systemui", 0).versionName);
            cached = supported;
            return supported;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException error) {
            return false;
        }
    }
}
