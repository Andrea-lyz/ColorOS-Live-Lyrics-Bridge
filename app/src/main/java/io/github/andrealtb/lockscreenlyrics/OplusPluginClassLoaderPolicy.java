package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Decides when the OPlus SystemUI plugin ClassLoader can load plugin classes.
 *
 * <p>ColorOS 16 assigns {@code mBase} and {@code mPackages} inside the
 * {@code OPlusPluginClassLoader(String, String, ClassLoader, String...)} constructor. ColorOS 17
 * (SystemUI 17.99.02) has no constructor at all: {@code PluginInstance$PluginFactory
 * .createClassLoader()} calls {@code PathClassLoader.<init>} directly and assigns both fields
 * afterwards. {@code loadClass} iterates {@code mPackages}, so the loader must not be used before
 * that field is set.</p>
 */
final class OplusPluginClassLoaderPolicy {
    static final String PACKAGE_FILTER_FIELD = "mPackages";

    private OplusPluginClassLoaderPolicy() {
    }

    /** The factory entry: an instance method without parameters that returns a ClassLoader. */
    static boolean isClassLoaderFactory(Method method) {
        return method != null
                && !Modifier.isStatic(method.getModifiers())
                && method.getParameterCount() == 0
                && ClassLoader.class.isAssignableFrom(method.getReturnType());
    }

    static boolean isPluginClassLoader(Object loader, String pluginClassLoaderClassName) {
        if (loader == null || pluginClassLoaderClassName == null) {
            return false;
        }
        for (Class<?> current = loader.getClass(); current != null; current = current.getSuperclass()) {
            if (pluginClassLoaderClassName.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True once the package filter is assigned. A loader without the known field keeps the
     * historical behavior (ready after construction).
     */
    static boolean isReady(Object loader) {
        if (loader == null) {
            return false;
        }
        for (Class<?> current = loader.getClass(); current != null; current = current.getSuperclass()) {
            Field field;
            try {
                field = current.getDeclaredField(PACKAGE_FILTER_FIELD);
            } catch (NoSuchFieldException e) {
                continue;
            } catch (Throwable t) {
                return true;
            }
            try {
                field.setAccessible(true);
                return field.get(loader) != null;
            } catch (Throwable t) {
                return true;
            }
        }
        return true;
    }
}
