package io.github.andrealtb.lockscreenlyrics.systemui.lyrics;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Caches verified private-field access used by the official lyrics Recycler hot path. */
public final class LyricsRecyclerFieldAccessor {
    private static final String CURRENT_INDEX_FIELD = "n";

    private final Object lock = new Object();
    private final WeakHashMap<Class<?>, Field> currentIndexFields = new WeakHashMap<>();
    private final Set<Class<?>> missingCurrentIndexFields =
            Collections.newSetFromMap(new WeakHashMap<>());

    public int readCurrentIndex(Object recycler, int fallback) {
        if (recycler == null) {
            return fallback;
        }
        Field field = resolveCurrentIndexField(recycler.getClass());
        if (field == null) {
            return fallback;
        }
        try {
            return field.getType() == int.class
                    ? field.getInt(recycler)
                    : ((Number) field.get(recycler)).intValue();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private Field resolveCurrentIndexField(Class<?> recyclerClass) {
        synchronized (lock) {
            Field cached = currentIndexFields.get(recyclerClass);
            if (cached != null) {
                return cached;
            }
            if (missingCurrentIndexFields.contains(recyclerClass)) {
                return null;
            }
        }

        Field resolved = findCurrentIndexField(recyclerClass);
        synchronized (lock) {
            if (resolved == null) {
                missingCurrentIndexFields.add(recyclerClass);
            } else {
                currentIndexFields.put(recyclerClass, resolved);
            }
        }
        return resolved;
    }

    private static Field findCurrentIndexField(Class<?> recyclerClass) {
        Class<?> current = recyclerClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(CURRENT_INDEX_FIELD);
                Class<?> fieldType = field.getType();
                if (fieldType != int.class && fieldType != Integer.class) {
                    return null;
                }
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
