package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;

/**
 * Resolved, trusted Provider capabilities for one external lyric source.
 *
 * <p>Version-4 direct payloads may declare capabilities only after their source-to-player binding
 * has passed {@link ExternalLyricProviderRegistry}.</p>
 */
final class ExternalLyricSourceProfile {
    final String source;
    final String playerPackage;
    final boolean supportsPlaybackState;
    final boolean supportsTrackGeneration;
    final boolean canPromoteAsAuthoritative;
    final boolean allowsTitleOnlyFallbackMatch;
    final boolean canOverrideFavoriteActionWithTranslation;

    private ExternalLyricSourceProfile(
            String source,
            String playerPackage,
            boolean supportsPlaybackState,
            boolean supportsTrackGeneration,
            boolean canPromoteAsAuthoritative,
            boolean allowsTitleOnlyFallbackMatch,
            boolean canOverrideFavoriteActionWithTranslation) {
        this.source = empty(source);
        this.playerPackage = empty(playerPackage);
        this.supportsPlaybackState = supportsPlaybackState;
        this.supportsTrackGeneration = supportsTrackGeneration;
        this.canPromoteAsAuthoritative = canPromoteAsAuthoritative;
        this.allowsTitleOnlyFallbackMatch = allowsTitleOnlyFallbackMatch;
        this.canOverrideFavoriteActionWithTranslation =
                canOverrideFavoriteActionWithTranslation;
    }

    static ExternalLyricSourceProfile registeredProviderDefaults(String source) {
        String normalizedSource = empty(source);
        String playerPackage =
                ExternalLyricProviderRegistry.trustedHostPackageForSource(normalizedSource);
        boolean supportsPlaybackState =
                ExternalLyricProviderRegistry.registeredProviderSupportsPlaybackState(normalizedSource);
        return new ExternalLyricSourceProfile(
                normalizedSource,
                playerPackage,
                supportsPlaybackState,
                supportsPlaybackState,
                ExternalLyricProviderRegistry.registeredProviderCanPromoteAsAuthoritative(
                        normalizedSource,
                        playerPackage),
                ExternalLyricProviderRegistry.registeredProviderAllowsTitleOnlyFallbackMatch(
                        normalizedSource),
                ExternalLyricProviderRegistry.registeredProviderMayOverrideFavoriteActionWithTranslation(
                        playerPackage));
    }

    static ExternalLyricSourceProfile moduleDirect(String source, String playerPackage) {
        String normalizedSource = empty(source);
        String normalizedPackage = empty(playerPackage);
        return new ExternalLyricSourceProfile(
                normalizedSource,
                normalizedPackage,
                false,
                false,
                false,
                false,
                ExternalLyricProviderRegistry.registeredProviderMayOverrideFavoriteActionWithTranslation(
                        normalizedPackage));
    }

    static ExternalLyricSourceProfile version4ProviderDeclaration(
            String source,
            String playerPackage,
            String capabilities,
            String matchPolicy,
            String identityConfidence) {
        String normalizedSource = empty(source);
        String normalizedPackage = empty(playerPackage);
        if (isEmpty(normalizedSource)
                || isEmpty(normalizedPackage)
                || !ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                        normalizedSource,
                        normalizedPackage)) {
            return new ExternalLyricSourceProfile(
                    normalizedSource,
                    "",
                    false,
                    false,
                    false,
                    false,
                    false);
        }

        boolean playbackState = containsToken(
                capabilities,
                LyricInfoContract.CAPABILITY_EXTERNAL_PLAYBACK_STATE);
        boolean trackGeneration = playbackState
                || containsToken(
                        capabilities,
                        LyricInfoContract.CAPABILITY_EXTERNAL_TRACK_GENERATION);
        boolean currentTrackAuthority = containsToken(
                capabilities,
                LyricInfoContract.CAPABILITY_EXTERNAL_CURRENT_TRACK_AUTHORITY)
                || containsToken(
                        identityConfidence,
                        LyricInfoContract.IDENTITY_CONFIDENCE_EXTERNAL_CURRENT_TRACK);
        boolean titleOnlyFallback = containsToken(
                capabilities,
                LyricInfoContract.CAPABILITY_EXTERNAL_TITLE_ONLY_FALLBACK)
                || containsToken(
                        matchPolicy,
                        LyricInfoContract.MATCH_POLICY_EXTERNAL_TITLE_ONLY);
        boolean translationToggle = containsToken(
                capabilities,
                LyricInfoContract.CAPABILITY_EXTERNAL_TRANSLATION_TOGGLE);
        return new ExternalLyricSourceProfile(
                normalizedSource,
                normalizedPackage,
                playbackState,
                trackGeneration,
                currentTrackAuthority,
                titleOnlyFallback,
                translationToggle);
    }

    private static boolean containsToken(String tokenList, String expected) {
        if (isEmpty(tokenList) || isEmpty(expected)) {
            return false;
        }
        String normalizedExpected = expected.trim().toLowerCase(Locale.ROOT);
        String[] tokens = tokenList.split("[,;|\\s]+");
        for (String token : tokens) {
            if (normalizedExpected.equals(token.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
