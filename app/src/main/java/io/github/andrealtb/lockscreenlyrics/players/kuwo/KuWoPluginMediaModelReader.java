package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import java.lang.reflect.Field;

/** Reads KuWo plugin media-model package and labeled toString fields. */
public final class KuWoPluginMediaModelReader {
    private static final String LYRIC_SUPPORTED_LABEL = ", isLyricSupported=";

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

    /**
     * Reads the model's {@code isLyricSupported=} flag. The last label wins: song and lyric text
     * print before it, and only numeric or boolean fields follow it (ColorOS 16 ends there;
     * ColorOS 17 appends primaryColor and artworkFullBgEnable).
     *
     * @return the flag, or {@code null} when the label is missing or not followed by a boolean
     */
    public static Boolean readLyricSupported(String description) {
        if (description == null) {
            return null;
        }
        int start = description.lastIndexOf(LYRIC_SUPPORTED_LABEL);
        if (start < 0) {
            return null;
        }
        start += LYRIC_SUPPORTED_LABEL.length();
        if (description.startsWith("true", start)) {
            return Boolean.TRUE;
        }
        if (description.startsWith("false", start)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
