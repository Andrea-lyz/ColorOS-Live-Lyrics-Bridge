package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Firmware-independent fallback, accepted only when the signature is unique. */
final class TimedLyricMethodPolicy {
    private TimedLyricMethodPolicy() {}

    static boolean matches(Executable executable) {
        if (!(executable instanceof Method) || !shape((Method) executable)) return false;
        if ("l".equals(executable.getName())) return true;
        int candidates = 0;
        for (Method method : executable.getDeclaringClass().getDeclaredMethods()) {
            if (shape(method)) candidates++;
        }
        return candidates == 1;
    }

    private static boolean shape(Method method) {
        Class<?>[] types = method.getParameterTypes();
        return !Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                && types.length == 2 && (types[0] == boolean.class || types[0] == Boolean.class)
                && types[1] == long.class;
    }

    static long position(long snapshotPosition, long nativePosition) {
        return snapshotPosition >= 0L ? snapshotPosition : nativePosition;
    }
}
