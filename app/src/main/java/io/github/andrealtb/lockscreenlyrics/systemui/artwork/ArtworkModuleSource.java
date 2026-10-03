package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Binds the existing section input; never invokes the factory that creates a WhileSubscribed flow. */
public final class ArtworkModuleSource {
    public record Input(Object flow, Method getter, Method moduleMap, Class<?> payloadType) {
        public Object payload(Object section) throws ReflectiveOperationException {
            if (section == null) return null;
            if (!moduleMap.getDeclaringClass().isInstance(section)) throw new IllegalArgumentException("section_type_changed");
            Object result = moduleMap.invoke(section);
            if (!(result instanceof Map<?, ?> map) || map.size() > 64) throw new IllegalArgumentException("module_map_shape");
            Object value = map.get("mpModule");
            if (value != null && value.getClass() != payloadType) throw new IllegalArgumentException("module_payload_changed");
            return value;
        }
    }

    private ArtworkModuleSource() {}

    public static Input bind(Object viewModel, Class<?> payloadType) throws ReflectiveOperationException {
        if (viewModel == null || payloadType == null) throw new NoSuchMethodException("module_source_missing");
        List<Object> owners = new ArrayList<>();
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        owners.add(viewModel);
        List<Input> matches = new ArrayList<>();
        IdentityHashMap<Object, Boolean> seenFlows = new IdentityHashMap<>();
        for (int index = 0; index < owners.size(); index++) {
            if (index >= 4) throw new NoSuchMethodException("module_owner_budget");
            Object owner = owners.get(index);
            if (visited.put(owner, true) != null) continue;
            Method snapshot = snapshotAccessor(owner.getClass(), payloadType);
            snapshot.setAccessible(true);
            Object expected = snapshot.invoke(owner, payloadType, "mpModule");
            if (expected != null && expected.getClass() != payloadType) throw new IllegalArgumentException("module_snapshot_type");
            Field[] fields = owner.getClass().getDeclaredFields();
            if (fields.length > 96) throw new NoSuchMethodException("module_field_budget");
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value == null) continue;
                try {
                    Method getter = stateGetter(value);
                    Object section = getter.invoke(value);
                    Method map = matchingMap(section, expected, payloadType);
                    if (map != null && seenFlows.put(value, true) == null) {
                        matches.add(new Input(value, getter, map, payloadType));
                    }
                } catch (NoSuchMethodException ignored) { /* not the section input */ }
                try {
                    snapshotAccessor(field.getType(), payloadType);
                    if (value != owner && !visited.containsKey(value) && !containsIdentity(owners, value)) owners.add(value);
                } catch (NoSuchMethodException ignored) { /* not a scoped VM delegate */ }
            }
            // The deepest owner can read the underlying section input; a delegating VM has no match.
            if (!matches.isEmpty()) break;
        }
        if (matches.size() != 1) throw new NoSuchMethodException("module_source_ambiguous");
        return matches.get(0);
    }

    private static boolean containsIdentity(List<Object> values, Object target) {
        for (Object value : values) if (value == target) return true;
        return false;
    }

    private static Method snapshotAccessor(Class<?> type, Class<?> payload) throws NoSuchMethodException {
        Method selected = null;
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                    || parameters.length != 2 || parameters[0] != Class.class || parameters[1] != String.class
                    || method.getReturnType() == Object.class || !method.getReturnType().isAssignableFrom(payload)) continue;
            if (selected != null) throw new NoSuchMethodException("snapshot_accessor_ambiguous");
            selected = method;
        }
        if (selected == null) throw new NoSuchMethodException("snapshot_accessor_missing");
        return selected;
    }

    private static Method matchingMap(Object section, Object expected, Class<?> payload) throws ReflectiveOperationException {
        if (section == null) return null;
        Method selected = null;
        for (Method method : section.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                    || method.getParameterCount() != 0 || !Map.class.isAssignableFrom(method.getReturnType())) continue;
            method.setAccessible(true);
            Object value = method.invoke(section);
            if (!(value instanceof Map<?, ?> map) || map.size() > 64 || !map.containsKey("mpModule")) continue;
            Object module = map.get("mpModule");
            if (module != expected || (module != null && module.getClass() != payload)) continue;
            if (selected != null) throw new NoSuchMethodException("module_map_ambiguous");
            selected = method;
        }
        return selected;
    }

    public static Method stateGetter(Object flow) throws NoSuchMethodException {
        Method getter = flow.getClass().getMethod("getValue");
        if (getter.getReturnType() != Object.class || Modifier.isStatic(getter.getModifiers())) {
            throw new NoSuchMethodException("state_getter_shape");
        }
        for (Method method : flow.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!Modifier.isStatic(method.getModifiers()) && parameters.length == 2
                    && method.getReturnType() == Object.class
                    && parameters[1].getName().equals("kotlin.coroutines.Continuation")) {
                getter.setAccessible(true);
                return getter;
            }
        }
        throw new NoSuchMethodException("state_collector_missing");
    }
}
