package io.github.andrealtb.lockscreenlyrics;

/** Fixed settings-page color swatches; custom colors remain backed by the existing hex fields. */
final class SettingsColorPalette {
    static final String[] PRIMARY = {
            "#FFFFFF",
            "#F9EFE2",
            "#FFD68A",
            "#8FE3FF"
    };
    static final String[] GLOW = {
            "#FFD68A",
            "#FF9D5C",
            "#4FC4E8",
            "#FF5D73"
    };

    static String customSeed(String current, String[] palette, String fallback) {
        String normalized = LyricUiConfig.sanitizeColor(current, fallback);
        if (palette != null) {
            for (String presetColor : palette) {
                if (presetColor.equalsIgnoreCase(normalized)) {
                    return fallback;
                }
            }
        }
        return normalized;
    }

    private SettingsColorPalette() {
    }
}
