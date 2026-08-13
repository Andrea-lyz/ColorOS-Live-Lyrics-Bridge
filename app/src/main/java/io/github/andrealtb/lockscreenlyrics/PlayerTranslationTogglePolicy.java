package io.github.andrealtb.lockscreenlyrics;

import java.util.Set;

/**
 * Decides whether a player's favorite media-card slot may be replaced with the lyric translation
 * toggle. Pure decision logic; every SystemUI media-model operation lives in
 * {@link TranslationToggleMediaActionBinder}.
 */
final class PlayerTranslationTogglePolicy {

    private PlayerTranslationTogglePolicy() {
    }

    static boolean canOverrideFavoriteActionWithTranslation(
            String packageName,
            Set<String> providerDeclaredTranslationTogglePackages) {
        return ExternalLyricProviderSpecialCases
                .supportsRegisteredProviderTranslationToggle(packageName)
                || (providerDeclaredTranslationTogglePackages != null
                && providerDeclaredTranslationTogglePackages.contains(packageName));
    }

    static boolean shouldReplaceFavoriteActionWithTranslation(
            boolean canOverrideFavoriteAction,
            boolean isCurrentLyricProviderPackage,
            int modelTranslationCount,
            String payloadTranslationLyric) {
        if (!canOverrideFavoriteAction || !isCurrentLyricProviderPackage) {
            return false;
        }
        if (modelTranslationCount > 0) {
            return true;
        }
        return payloadTranslationLyric != null
                && LyricInfoContract.containsTimedLrc(payloadTranslationLyric);
    }
}
