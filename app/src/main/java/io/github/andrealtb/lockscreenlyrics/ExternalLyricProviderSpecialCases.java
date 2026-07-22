package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;

/**
 * Named compatibility decisions for providers whose runtime behaviour differs from the generic
 * external-lyric pipeline.
 *
 * <p>Do not add source/package conditionals to {@link LockscreenLyricsModule}. A new Provider
 * either uses the generic protocol/profile path, or adds one explicitly named method here (and a
 * focused test) before a runtime strategy is introduced.</p>
 */
final class ExternalLyricProviderSpecialCases {
    static final long POWERAMP_SYSTEMUI_TRACK_AUTHORITY_MS = 12_000L;

    private ExternalLyricProviderSpecialCases() {
    }

    static boolean isProviderBackedPlayerPackage(String packageName) {
        return ExternalLyricProviderRegistry.isTrustedProviderHostPackage(packageName);
    }

    static String[] providerBackedPlayerPackagesForOplusWhitelist() {
        return ExternalLyricProviderRegistry.trustedProviderHostPackages();
    }

    static boolean supportsRegisteredProviderTranslationToggle(String packageName) {
        return ExternalLyricProviderRegistry
                .registeredProviderMayOverrideFavoriteActionWithTranslation(packageName);
    }

    static boolean shouldSuppressKuGouOfficialLyricInfo(
            String packageName,
            LyricInfoContract.Payload payload,
            boolean alreadyHasExternalDocument) {
        if (!ExternalLyricProviderRegistry.KUGOU_MUSIC_PLAYER_PACKAGE.equals(packageName)
                || payload == null
                || alreadyHasExternalDocument
                || payload.isModuleEnvelope()) {
            return false;
        }
        return !isKuGouProviderPayload(payload.provider)
                && (isEmpty(payload.provider) || !payload.hasWordTiming());
    }

    static boolean shouldPreserveQiShuiOrKuGouConceptExternalLyricSurface(String source) {
        return ExternalLyricProviderRegistry.QISHUI_MUSIC_SOURCE.equals(source)
                || ExternalLyricProviderRegistry.KUGOU_CONCEPT_MUSIC_SOURCE.equals(source);
    }

    static boolean isSpotifyExternalLyricContext(String playerPackage, String source) {
        return ExternalLyricProviderRegistry.SPOTIFY_PLAYER_PACKAGE.equals(playerPackage)
                || ExternalLyricProviderRegistry.SPOTIFY_SOURCE.equals(source);
    }

    static boolean isSpotifyPlayerPackage(String packageName) {
        return ExternalLyricProviderRegistry.SPOTIFY_PLAYER_PACKAGE.equals(packageName);
    }

    static String spotifyProviderSource() {
        return ExternalLyricProviderRegistry.SPOTIFY_SOURCE;
    }

    static boolean isSpotifyAdvertisementOrSponsoredMediaItem(String title, String artist) {
        String metadata = (empty(title) + ' ' + empty(artist)).toLowerCase(Locale.ROOT);
        return metadata.contains("广告")
                || metadata.contains("骞垮憡")
                || metadata.contains("advertisement")
                || metadata.contains("sponsored");
    }

    static boolean shouldApplyOfficialDisplayTextAliasesForProvider(String source) {
        // KuGou Lite removes its one SystemUI-filtered timed promotional row before publishing.
        // Its remaining display and raw lanes therefore stay index-compatible and require the
        // official aliases for RecyclerView current-line/word-timing alignment.
        return !ExternalLyricProviderRegistry.APPLE_MUSIC_SOURCE.equals(source)
                && !ExternalLyricProviderRegistry.KUGOU_MUSIC_SOURCE.equals(source);
    }

    static boolean shouldInvalidateAppleMusicSystemUiCommitAfterTrackChanged(String playerPackage) {
        return ExternalLyricProviderRegistry.APPLE_MUSIC_PLAYER_PACKAGE.equals(playerPackage);
    }

    static boolean shouldReplaySystemUiAfterAppleMusicLyricReady(
            String source,
            String playerPackage) {
        return ExternalLyricProviderRegistry.APPLE_MUSIC_SOURCE.equals(source)
                && ExternalLyricProviderRegistry.APPLE_MUSIC_PLAYER_PACKAGE.equals(playerPackage);
    }

    static boolean shouldAllowAppleMusicGenerationScopedPromotion(
            String source,
            String playerPackage) {
        return ExternalLyricProviderRegistry.APPLE_MUSIC_SOURCE.equals(source)
                && ExternalLyricProviderRegistry.APPLE_MUSIC_PLAYER_PACKAGE.equals(playerPackage);
    }

    /**
     * LX uses the MediaSession title as a Bluetooth lyric lane.  Its real track identity is moved
     * into the artist field as {@code <song> - <artist>}, and the title can briefly be empty while
     * a seek is committed.  SystemUI must see the stable identity while it loads its lyric view;
     * the player-side metadata is deliberately left untouched so Bluetooth lyric receivers still
     * receive the live lyric line.
     */
    static boolean shouldNormalizeLxBluetoothLyricMetadataForSystemUi(
            String source,
            String playerPackage,
            String incomingTitle,
            String incomingArtist,
            String stableTitle,
            String stableArtist) {
        if (!isLxSourceBoundToPlayer(source, playerPackage)) {
            return false;
        }

        String normalizedStableTitle = empty(stableTitle).trim();
        String normalizedStableArtist = empty(stableArtist).trim();
        String normalizedIncomingTitle = empty(incomingTitle).trim();
        String normalizedIncomingArtist = empty(incomingArtist).trim();
        if (isEmpty(normalizedStableTitle) || isEmpty(normalizedIncomingArtist)) {
            return false;
        }
        if (normalizedStableTitle.equals(normalizedIncomingTitle)
                && normalizedStableArtist.equals(normalizedIncomingArtist)) {
            return false;
        }
        if (isEmpty(normalizedStableArtist)) {
            return normalizedStableTitle.equals(normalizedIncomingArtist);
        }

        String titlePrefix = normalizedStableTitle + " - ";
        if (!normalizedIncomingArtist.startsWith(titlePrefix)) {
            return false;
        }
        String projectedArtist = normalizedIncomingArtist.substring(titlePrefix.length());
        return normalizedStableArtist.equals(projectedArtist)
                || normalizedStableArtist.startsWith(projectedArtist + " - ")
                || projectedArtist.startsWith(normalizedStableArtist + " - ");
    }

    static boolean isPowerampProviderSource(String source) {
        return ExternalLyricProviderRegistry.POWERAMP_SOURCE.equals(source);
    }

    static boolean isPowerampProviderPackage(String packageName) {
        return ExternalLyricProviderRegistry.POWERAMP_PLAYER_PACKAGE.equals(packageName);
    }

    static String powerampProviderPackage() {
        return ExternalLyricProviderRegistry.POWERAMP_PLAYER_PACKAGE;
    }

    static boolean isKuGouProviderPayload(String provider) {
        return ExternalLyricProviderRegistry.KUGOU_MUSIC_SOURCE.equals(provider)
                || ExternalLyricProviderRegistry.KUGOU_CONCEPT_MUSIC_SOURCE.equals(provider);
    }

    static String sourceForKuGouProviderPayload(LyricInfoContract.Payload payload) {
        if (payload == null || payload.isModuleEnvelope()) {
            return "";
        }
        return isKuGouProviderPayload(payload.provider) ? payload.provider : "";
    }

    static boolean isKuGouExternalWordTimingAuthoritative(
            boolean hasExternalWordLyricModel,
            String externalSource,
            boolean hasFreshExternalDocument,
            LyricInfoContract.Payload currentPayload,
            String currentProviderPackage,
            String currentTrackKey) {
        if (!hasExternalWordLyricModel
                || !ExternalLyricProviderRegistry.KUGOU_MUSIC_SOURCE.equals(externalSource)) {
            return false;
        }
        if (hasFreshExternalDocument) {
            return true;
        }
        if (currentPayload == null
                || !currentPayload.hasWordTiming()
                || !ExternalLyricProviderRegistry.KUGOU_MUSIC_SOURCE.equals(
                        sourceForKuGouProviderPayload(currentPayload))) {
            return false;
        }
        if (!isEmpty(currentProviderPackage)
                && !ExternalLyricProviderRegistry.KUGOU_MUSIC_PLAYER_PACKAGE.equals(
                        currentProviderPackage)) {
            return false;
        }
        String payloadKey = payloadTrackKey(currentPayload);
        return isEmpty(payloadKey)
                || isEmpty(currentTrackKey)
                || payloadKey.equals(currentTrackKey)
                || TrackIdentity.matchesHintKey(payloadKey, currentTrackKey);
    }

    private static String payloadTrackKey(LyricInfoContract.Payload payload) {
        if (payload == null) {
            return "";
        }
        return empty(payload.trackKey);
    }

    private static boolean isLxSourceBoundToPlayer(String source, String playerPackage) {
        return (ExternalLyricProviderRegistry.LX_MUSIC_SOURCE.equals(source)
                && ExternalLyricProviderRegistry.LX_MUSIC_PLAYER_PACKAGE.equals(playerPackage))
                || (ExternalLyricProviderRegistry.LX_WALNUT_MUSIC_SOURCE.equals(source)
                && ExternalLyricProviderRegistry.LX_WALNUT_MUSIC_PLAYER_PACKAGE.equals(
                playerPackage));
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
