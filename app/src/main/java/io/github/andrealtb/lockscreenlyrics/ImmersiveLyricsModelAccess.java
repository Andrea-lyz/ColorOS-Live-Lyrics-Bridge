package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Reflection access to the ColorOS 17 immersive lyric model, identified by shape only.
 *
 * <p>SystemUIPlugin 17.000.002 passes {@code ImmersiveLyricsModel(List lines, int position)} to
 * {@code LyricsRecyclerView.o(model, animate)}. Each line holds one {@code long} start time and its
 * text. Names are obfuscated, so the model is any class with exactly one {@code List} field, exactly
 * one {@code int} field and a {@code (List, int)} constructor.</p>
 */
final class ImmersiveLyricsModelAccess {
    // Single-entry caches: holding one shape at a time never pins an old plugin ClassLoader for
    // longer than the next call with the new generation's class.
    private static volatile ModelShape cachedModelShape;
    private static volatile LineShape cachedLineShape;

    private ImmersiveLyricsModelAccess() {
    }

    static final class Snapshot {
        final List<?> lines;
        final int index;

        Snapshot(List<?> lines, int index) {
            this.lines = lines;
            this.index = index;
        }

        int lineCount() {
            return lines == null ? 0 : lines.size();
        }
    }

    static boolean isModelClass(Class<?> type) {
        return modelShape(type) != null;
    }

    /** Returns the model's lines and position, or null when the object is not such a model. */
    static Snapshot read(Object model) {
        ModelShape shape = model == null ? null : modelShape(model.getClass());
        if (shape == null) {
            return null;
        }
        try {
            Object lines = shape.linesField.get(model);
            return new Snapshot(lines instanceof List ? (List<?>) lines : null,
                    shape.indexField.getInt(model));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A new model of the same class with the same lines and another position, or null. */
    static Object withIndex(Object model, List<?> lines, int index) {
        ModelShape shape = model == null ? null : modelShape(model.getClass());
        if (shape == null) {
            return null;
        }
        try {
            return shape.constructor.newInstance(lines, index);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Line start times in list order, or null when any line lacks a single long field. */
    static long[] startTimes(List<?> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        long[] times = new long[lines.size()];
        for (int i = 0; i < times.length; i++) {
            Object line = lines.get(i);
            Field time = line == null ? null : lineTimeField(line.getClass());
            if (time == null) {
                return null;
            }
            try {
                times[i] = time.getLong(line);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return times;
    }

    private static ModelShape modelShape(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isInterface() || type.isArray()) {
            return null;
        }
        ModelShape cached = cachedModelShape;
        if (cached != null && cached.type == type) {
            return cached;
        }
        Field linesField = null;
        Field indexField = null;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (List.class.isAssignableFrom(field.getType())) {
                if (linesField != null) {
                    return null;
                }
                linesField = field;
            } else if (field.getType() == int.class) {
                if (indexField != null) {
                    return null;
                }
                indexField = field;
            }
        }
        if (linesField == null || indexField == null) {
            return null;
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(List.class, int.class);
            linesField.setAccessible(true);
            indexField.setAccessible(true);
            constructor.setAccessible(true);
            ModelShape shape = new ModelShape(type, linesField, indexField, constructor);
            cachedModelShape = shape;
            return shape;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field lineTimeField(Class<?> type) {
        LineShape cached = cachedLineShape;
        if (cached != null && cached.type == type) {
            return cached.timeField;
        }
        Field timeField = null;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != long.class) {
                continue;
            }
            if (timeField != null) {
                return null;
            }
            timeField = field;
        }
        if (timeField == null) {
            return null;
        }
        try {
            timeField.setAccessible(true);
        } catch (Throwable ignored) {
            return null;
        }
        cachedLineShape = new LineShape(type, timeField);
        return timeField;
    }

    private static final class ModelShape {
        final Class<?> type;
        final Field linesField;
        final Field indexField;
        final Constructor<?> constructor;

        ModelShape(Class<?> type, Field linesField, Field indexField, Constructor<?> constructor) {
            this.type = type;
            this.linesField = linesField;
            this.indexField = indexField;
            this.constructor = constructor;
        }
    }

    private static final class LineShape {
        final Class<?> type;
        final Field timeField;

        LineShape(Class<?> type, Field timeField) {
            this.type = type;
            this.timeField = timeField;
        }
    }
}
