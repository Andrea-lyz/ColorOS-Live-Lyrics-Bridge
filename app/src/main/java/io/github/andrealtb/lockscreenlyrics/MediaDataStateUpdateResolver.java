package io.github.andrealtb.lockscreenlyrics;

import android.media.session.PlaybackState;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Resolves the MediaDataManager entry that rebuilds one media entry from a PlaybackState without
 * the {@code mediaPlayerIndex != -1 || PLAYING} guard of the OPlus refresh wrapper.
 *
 * <p>ColorOS 16 names it {@code updateState(String, PlaybackState)}. ColorOS 17 (SystemUI
 * 17.99.02) renamed the interface method to {@code onStateUpdate}, which launches the same
 * createActionsFromState and onMediaDataLoaded rebuild on the application scope; its suspend
 * helper {@code access$updateState(impl, key, state, continuation)} is static and never
 * matches.</p>
 */
final class MediaDataStateUpdateResolver {
    private static final String[] ENTRY_NAMES = {"onStateUpdate", "updateState"};

    private MediaDataStateUpdateResolver() {
    }

    static Method resolve(Class<?> managerClass) {
        if (managerClass == null) return null;
        for (String name : ENTRY_NAMES) {
            for (Class<?> current = managerClass; current != null; current = current.getSuperclass()) {
                Method method;
                try {
                    method = current.getDeclaredMethod(name, String.class, PlaybackState.class);
                } catch (NoSuchMethodException ignored) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }
}
