package io.github.andrealtb.lockscreenlyrics;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Resolves the host adapter's own types; no module AndroidX class identity crosses loaders. */
final class LyricRecyclerAdapterContract {
    private LyricRecyclerAdapterContract() { }

    static Class<?> findBaseClass(Object adapter) {
        for (Class<?> type = adapter == null ? null : adapter.getClass();
                type != null && type != Object.class; type = type.getSuperclass()) {
            Method[] methods = type.getDeclaredMethods();
            for (Method bind : methods) {
                Class<?>[] parameters = bind.getParameterTypes();
                if (!isAbstractPublic(bind) || bind.getReturnType() != void.class
                        || parameters.length != 2 || parameters[1] != int.class
                        || !hasItemView(parameters[0])) continue;
                Class<?> holder = parameters[0];
                boolean count = false;
                boolean create = false;
                for (Method method : methods) {
                    if (!isAbstractPublic(method)) continue;
                    Class<?>[] args = method.getParameterTypes();
                    count |= args.length == 0 && method.getReturnType() == int.class;
                    create |= args.length == 2 && args[0] == ViewGroup.class
                            && args[1] == int.class && method.getReturnType() == holder;
                }
                if (count && create) return type;
            }
        }
        return null;
    }

    static List<Method> bindingMethods(Object adapter, Class<?> baseClass) {
        ArrayList<Method> result = new ArrayList<>();
        if (adapter == null || baseClass == null) return result;
        for (Method nativeMethod : baseClass.getDeclaredMethods()) {
            Class<?>[] parameters = nativeMethod.getParameterTypes();
            if (!Modifier.isPublic(nativeMethod.getModifiers())
                    || Modifier.isStatic(nativeMethod.getModifiers())
                    || nativeMethod.getReturnType() != void.class
                    || (parameters.length != 2 && parameters.length != 3)
                    || parameters[1] != int.class || !hasItemView(parameters[0])
                    || (parameters.length == 3 && parameters[2] != List.class)) continue;
            try {
                Method bound = adapter.getClass().getMethod(nativeMethod.getName(), parameters);
                if (bound.getDeclaringClass() != baseClass
                        && !Modifier.isAbstract(bound.getModifiers())) result.add(bound);
            } catch (NoSuchMethodException ignored) {
                // A missing override is not permission to hook every adapter's base wrapper.
            }
        }
        return result;
    }

    private static boolean isAbstractPublic(Method method) {
        int modifiers = method.getModifiers();
        return Modifier.isAbstract(modifiers) && Modifier.isPublic(modifiers)
                && !Modifier.isStatic(modifiers);
    }

    private static boolean hasItemView(Class<?> holder) {
        for (Class<?> type = holder; type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
                        && Modifier.isFinal(modifiers) && field.getType() == View.class) return true;
            }
        }
        return false;
    }
}
