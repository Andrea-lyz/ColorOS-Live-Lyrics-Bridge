package io.github.andrealtb.lockscreenlyrics;

import android.widget.TextView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Recognizes the vendor LyricsRecyclerView helper that switches a lyric row between its active
 * and inactive style.
 *
 * <p>ColorOS 17 (SystemUIPlugin 17.000.002) {@code void h(AppCompatTextView, boolean active)}
 * swaps the row typeface together with its RenderEffect and shadow: the base typeface for the
 * active row and a weight-600 derivative for the others. SystemUIPlugin 16.001.002 new, new10
 * and old builds declare the same instance helper ({@code h} or {@code i}) with equal active and
 * other-line weights (700), so learning the active typeface does not change their rendering; the
 * user build uses a static three-argument {@code d(...)} that this shape does not match. Only
 * the shape is trusted, and only methods declared by the recycler class itself.</p>
 */
final class OfficialLyricRowStyleMethodPolicy {
    private OfficialLyricRowStyleMethodPolicy() {
    }

    static boolean matches(Method method, Class<?> lyricsRecyclerClass) {
        if (method == null
                || lyricsRecyclerClass == null
                || method.getDeclaringClass() != lyricsRecyclerClass
                || method.getReturnType() != void.class
                || Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == 2
                && TextView.class.isAssignableFrom(parameterTypes[0])
                && (parameterTypes[1] == boolean.class || parameterTypes[1] == Boolean.class);
    }
}
