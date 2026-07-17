package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Resolves the obfuscated private fields used by verified OPlus SystemUIPlugin builds.
 *
 * <p>OPlus has shipped multiple binaries with the same package version. Field names alone are
 * therefore not a safe compatibility contract: in one verified build {@code u} is line spacing,
 * while in another it is the animation duration. A binding is returned only when the complete
 * field-type fingerprint matches a layout we have inspected.</p>
 */
final class OfficialLyricsRecyclerCompatibility {
    private OfficialLyricsRecyclerCompatibility() {}

    static Binding resolve(Class<?> recyclerClass) {
        if (recyclerClass == null) {
            return null;
        }

        // SystemUIPlugin 16.001.002 old/new: u=int spacing, v=long duration, C=float scale.
        Binding uvC = bindingIfExact(recyclerClass, "u", "v", "C", "u/v/C");
        if (uvC != null) {
            return uvC;
        }

        // SystemUIPlugin 16.001.002 user: t=int spacing, u=long duration, A=float scale.
        return bindingIfExact(recyclerClass, "t", "u", "A", "t/u/A");
    }

    static Binding fromResolvedFields(
            Class<?> recyclerClass,
            Field lineSpacing,
            Field animationDuration,
            Field inactiveScale,
            String layoutName) {
        if (!isCompatibleInstanceField(recyclerClass, lineSpacing, int.class)
                || !isCompatibleInstanceField(recyclerClass, animationDuration, long.class)
                || !isCompatibleInstanceField(recyclerClass, inactiveScale, float.class)
                || lineSpacing.equals(animationDuration)
                || lineSpacing.equals(inactiveScale)
                || animationDuration.equals(inactiveScale)) {
            return null;
        }
        try {
            lineSpacing.setAccessible(true);
            animationDuration.setAccessible(true);
            inactiveScale.setAccessible(true);
            return new Binding(
                    lineSpacing,
                    animationDuration,
                    inactiveScale,
                    layoutName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isCompatibleInstanceField(
            Class<?> recyclerClass,
            Field field,
            Class<?> expectedType) {
        return recyclerClass != null
                && field != null
                && field.getType() == expectedType
                && !Modifier.isStatic(field.getModifiers())
                && field.getDeclaringClass().isAssignableFrom(recyclerClass);
    }

    private static Binding bindingIfExact(
            Class<?> recyclerClass,
            String lineSpacingName,
            String animationDurationName,
            String inactiveScaleName,
            String layoutName) {
        Field lineSpacing = findExactField(recyclerClass, lineSpacingName, int.class);
        Field animationDuration = findExactField(recyclerClass, animationDurationName, long.class);
        Field inactiveScale = findExactField(recyclerClass, inactiveScaleName, float.class);
        if (lineSpacing == null || animationDuration == null || inactiveScale == null) {
            return null;
        }
        return fromResolvedFields(
                recyclerClass,
                lineSpacing,
                animationDuration,
                inactiveScale,
                layoutName);
    }

    private static Field findExactField(Class<?> type, String name, Class<?> expectedType) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                if (field.getType() != expectedType) {
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

    static final class Binding {
        private final Field lineSpacing;
        @SuppressWarnings("unused")
        private final Field animationDuration;
        private final Field inactiveScale;
        final String layoutName;

        private Binding(
                Field lineSpacing,
                Field animationDuration,
                Field inactiveScale,
                String layoutName) {
            this.lineSpacing = lineSpacing;
            this.animationDuration = animationDuration;
            this.inactiveScale = inactiveScale;
            this.layoutName = layoutName;
        }

        Integer readLineSpacing(Object recycler) {
            try {
                return lineSpacing.getInt(recycler);
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean writeLineSpacing(Object recycler, int value) {
            try {
                // setInt is intentional: unlike Field#set it can never widen this value into a
                // long animation-duration field if a future obfuscation layout changes again.
                lineSpacing.setInt(recycler, value);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        Float readInactiveScale(Object recycler) {
            try {
                return inactiveScale.getFloat(recycler);
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean writeInactiveScale(Object recycler, float value) {
            try {
                inactiveScale.setFloat(recycler, value);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
