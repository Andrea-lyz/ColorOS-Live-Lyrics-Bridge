package io.github.andrealtb.lockscreenlyrics;

/** Immutable parsed renderer colors for one {@link LyricUiConfig} snapshot. */
final class LyricUiPalette {
    final int inactive;
    final int focusedInactive;
    final int active;
    final int played;
    final int activeTranslation;
    final int inactiveTranslation;
    final int activeTranslationProgress;
    final int glowShadow;
    final int glowFill;
    final int[] activeFeatherColors;

    private LyricUiPalette(
            int inactive,
            int focusedInactive,
            int active,
            int played,
            int activeTranslation,
            int inactiveTranslation,
            int activeTranslationProgress,
            int glowShadow,
            int glowFill,
            int[] activeFeatherColors) {
        this.inactive = inactive;
        this.focusedInactive = focusedInactive;
        this.active = active;
        this.played = played;
        this.activeTranslation = activeTranslation;
        this.inactiveTranslation = inactiveTranslation;
        this.activeTranslationProgress = activeTranslationProgress;
        this.glowShadow = glowShadow;
        this.glowFill = glowFill;
        this.activeFeatherColors = activeFeatherColors;
    }

    static LyricUiPalette from(LyricUiConfig config) {
        LyricUiConfig safeConfig = config == null ? LyricUiConfig.defaults() : config;
        return new LyricUiPalette(
                LyricUiColors.inactive(safeConfig),
                LyricUiColors.focusedInactive(safeConfig),
                LyricUiColors.active(safeConfig),
                LyricUiColors.played(safeConfig),
                LyricUiColors.activeTranslation(safeConfig),
                LyricUiColors.translationBase(safeConfig, false),
                LyricUiColors.activeTranslationProgress(safeConfig),
                LyricUiColors.glowShadow(safeConfig),
                LyricUiColors.glowFill(safeConfig),
                LyricUiColors.activeFeatherColors(safeConfig));
    }

    int translationBase(boolean activeLine) {
        return activeLine ? activeTranslation : inactiveTranslation;
    }
}
