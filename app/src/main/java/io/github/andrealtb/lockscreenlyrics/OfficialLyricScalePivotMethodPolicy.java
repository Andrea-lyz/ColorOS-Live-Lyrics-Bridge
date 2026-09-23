package io.github.andrealtb.lockscreenlyrics;

import android.widget.TextView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Recognizes the vendor LyricsRecyclerView helper that resets a lyric row's scale pivot.
 *
 * <p>Method names drift across same-version SystemUIPlugin binaries, so only the shape is
 * trusted. ColorOS 16 builds use an instance {@code void m/k(TextView)}. ColorOS 17
 * (SystemUIPlugin 17.000.002) made it {@code static void d(LyricsRecyclerView,
 * AppCompatTextView)} and calls it from a per-row layout listener on every layout pass, so a
 * Bridge pivot that is not reapplied after this helper is lost on the next layout.</p>
 */
final class OfficialLyricScalePivotMethodPolicy {
    static final int NOT_A_PIVOT_METHOD = -1;

    private OfficialLyricScalePivotMethodPolicy() {
    }

    /** Returns the lyric TextView argument index, or {@link #NOT_A_PIVOT_METHOD}. */
    static int textViewArgumentIndex(Method method) {
        if (method == null || method.getReturnType() != void.class) {
            return NOT_A_PIVOT_METHOD;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        if (!isStatic
                && parameterTypes.length == 1
                && TextView.class.isAssignableFrom(parameterTypes[0])) {
            return 0;
        }
        if (isStatic
                && parameterTypes.length == 2
                && parameterTypes[0] == method.getDeclaringClass()
                && TextView.class.isAssignableFrom(parameterTypes[1])) {
            return 1;
        }
        return NOT_A_PIVOT_METHOD;
    }
}
