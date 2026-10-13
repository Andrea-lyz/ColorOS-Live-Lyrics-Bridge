package io.github.andrealtb.lockscreenlyrics;

import android.os.Build;
import android.os.Trace;

/**
 * Atrace sections for Perfetto analysis of SystemUI main-thread cost. Sections are emitted only
 * while app tracing is enabled for SystemUI; otherwise each call is a single enabled-check.
 */
public final class BridgePerfTrace {
    private BridgePerfTrace() {
    }

    /** Returns whether a section was opened; pass the result to {@link #end(boolean)}. */
    public static boolean begin(String section) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !Trace.isEnabled()) {
            return false;
        }
        Trace.beginSection(section);
        return true;
    }

    public static void end(boolean begun) {
        if (begun) {
            Trace.endSection();
        }
    }
}
