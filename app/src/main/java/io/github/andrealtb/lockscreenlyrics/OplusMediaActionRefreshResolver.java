package io.github.andrealtb.lockscreenlyrics;

import android.media.session.PlaybackState;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolves ColorOS' synthetic playback-state refresh entry without pinning its full class name. */
final class OplusMediaActionRefreshResolver {
    private OplusMediaActionRefreshResolver() {
    }

    static Method resolve(Class<?> managerClass) {
        if (managerClass == null) return null;
        Method uniqueShape = null;
        boolean ambiguousShape = false;
        for (Method method : managerClass.getDeclaredMethods()) {
            if (!matchesShape(method, managerClass)) continue;
            if (method.getName().contains("updateMediaDataFromPlayState")) {
                method.setAccessible(true);
                return method;
            }
            if (uniqueShape == null) {
                uniqueShape = method;
            } else {
                ambiguousShape = true;
            }
        }
        if (uniqueShape == null || ambiguousShape) return null;
        uniqueShape.setAccessible(true);
        return uniqueShape;
    }

    private static boolean matchesShape(Method method, Class<?> managerClass) {
        Class<?>[] parameters = method.getParameterTypes();
        return Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == void.class
                && parameters.length == 3
                && parameters[0] == managerClass
                && parameters[1] == String.class
                && parameters[2] == PlaybackState.class;
    }
}
