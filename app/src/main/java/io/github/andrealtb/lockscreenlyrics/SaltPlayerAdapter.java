package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Constructor;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

final class SaltPlayerAdapter implements PlayerAdapter {
    private static final String PACKAGE_NAME = "com.salt.music";
    private static final String HOOK_ID_PREFIX = "salt-player-lyric-result-";
    private static final String[] LYRIC_RESULT_CLASS_CANDIDATES = {
            "androidx.obf.ow0",
            "androidx.obf.qw0"
    };

    @Override
    public String packageName() {
        return PACKAGE_NAME;
    }

    @Override
    public String displayName() {
        return "Salt Player";
    }

    @Override
    public void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader) {
        int hookCount = 0;
        Throwable lastFailure = null;
        for (String className : LYRIC_RESULT_CLASS_CANDIDATES) {
            try {
                hookCount += hookLyricResultConstructors(module, classLoader, className);
            } catch (Throwable t) {
                lastFailure = t;
            }
        }

        if (hookCount > 0) {
            module.info("Hooked " + displayName() + " lyric result constructors, count=" + hookCount);
            return;
        }

        if (lastFailure != null) {
            module.error("Failed to hook " + displayName() + " lyric result constructors", lastFailure);
        } else {
            module.error("Failed to hook " + displayName() + " lyric result constructors",
                    new IllegalStateException("No matching lyric result constructor candidates"));
        }
    }

    private static int hookLyricResultConstructors(
            LockscreenLyricsModule module,
            ClassLoader classLoader,
            String resultClassName) throws ClassNotFoundException {
        Class<?> lyricResultClass = classLoader.loadClass(resultClassName);
        int count = 0;
        for (Constructor<?> constructor : lyricResultClass.getDeclaredConstructors()) {
            String kind = lyricConstructorKind(constructor);
            if (kind == null) {
                continue;
            }
            constructor.setAccessible(true);
            module.hook(constructor)
                    .setId(HOOK_ID_PREFIX + simpleClassName(resultClassName) + "-" + kind)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(module::onPlayerLyricResultConstructed);
            count++;
        }
        return count;
    }

    private static String lyricConstructorKind(Constructor<?> constructor) {
        Class<?>[] types = constructor.getParameterTypes();
        if (types.length != 3
                || types[1] != String.class
                || !types[0].getName().startsWith("androidx.obf.")) {
            return null;
        }
        if (types[2] == String.class) {
            return "primary";
        }
        if (types[2] == int.class) {
            return "synthetic";
        }
        return null;
    }

    private static String simpleClassName(String className) {
        int index = className.lastIndexOf('.');
        return (index >= 0 ? className.substring(index + 1) : className).toLowerCase(Locale.ROOT);
    }
}
