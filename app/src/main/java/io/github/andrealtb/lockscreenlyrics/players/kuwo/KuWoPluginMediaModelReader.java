package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import java.lang.reflect.Field;

/** Reads KuWo plugin media-model package and labeled toString fields. */
public final class KuWoPluginMediaModelReader {
    private KuWoPluginMediaModelReader() {
    }

    public static boolean containsPlayerPackage(Object model) {
        if (model == null) {
            return false;
        }
        Class<?> current = model.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() != String.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (KuWoMediaIdentityPolicy.PLAYER_PACKAGE.equals(field.get(model))) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    /**
     * @return labeled substring when both markers are present; otherwise {@code null}
     *         so the caller can fall back to named fields.
     */
    public static String readLabeledText(String description, String label, String nextLabel) {
        if (description == null || label == null || nextLabel == null) {
            return null;
        }
        int start = description.indexOf(label);
        if (start < 0) {
            return null;
        }
        start += label.length();
        int end = description.indexOf(nextLabel, start);
        if (end >= start) {
            return description.substring(start, end);
        }
        return null;
    }
}
