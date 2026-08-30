package io.github.andrealtb.lockscreenlyrics;

/** Defines which {@link LyricUiConfig} fields are owned by settings sub-pages. */
final class LyricUiConfigOwnerPolicy {
    private LyricUiConfigOwnerPolicy() {
    }

    /**
     * Sub-pages own the global translation default and visual-layer controls. Merge only those
     * external fields so returning to the main page cannot overwrite an unsaved main-page draft.
     */
    static LyricUiConfig mergeExternalFields(
            LyricUiConfig target,
            LyricUiConfig persisted) {
        LyricUiConfig safeTarget = target == null ? LyricUiConfig.defaults() : target;
        LyricUiConfig safePersisted = persisted == null ? safeTarget : persisted;
        return safeTarget.buildUpon()
                .activeOpacityPercent(safePersisted.activeOpacityPercent)
                .currentUnrevealedOpacityPercent(
                        safePersisted.currentUnrevealedOpacityPercent)
                .activeTranslationOpacityPercent(
                        safePersisted.activeTranslationOpacityPercent)
                .activeTranslationProgressOpacityPercent(
                        safePersisted.activeTranslationProgressOpacityPercent)
                .inactiveOpacityPercent(safePersisted.inactiveOpacityPercent)
                .inactiveTranslationFollowsMain(
                        safePersisted.inactiveTranslationFollowsMain)
                .inactiveTranslationOpacityPercent(
                        safePersisted.inactiveTranslationOpacityPercent)
                .verticalFadeEnabled(safePersisted.verticalFadeEnabled)
                .verticalFadeLengthTenthsDp(safePersisted.verticalFadeLengthTenthsDp)
                .inactiveRowFadeEnabled(safePersisted.inactiveRowFadeEnabled)
                .inactiveRowFadePercent(safePersisted.inactiveRowFadePercent)
                .defaultTranslationEnabled(safePersisted.defaultTranslationEnabled)
                .build();
    }
}
