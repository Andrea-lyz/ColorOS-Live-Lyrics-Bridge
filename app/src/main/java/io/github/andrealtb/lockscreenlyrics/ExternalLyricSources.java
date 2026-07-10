package io.github.andrealtb.lockscreenlyrics;

final class ExternalLyricSources {
    static final String QQ_MUSIC_PLAYER_PACKAGE = "com.tencent.qqmusic";
    static final String QQ_MUSIC_HD_PLAYER_PACKAGE = "com.tencent.qqmusicpad";
    static final String NETEASE_MUSIC_PLAYER_PACKAGE = "com.netease.cloudmusic";
    static final String NETEASE_MUSIC_HONOR_PLAYER_PACKAGE = "com.hihonor.cloudmusic";
    static final String SPOTIFY_PLAYER_PACKAGE = "com.spotify.music";
    static final String SPOTIFY_SOURCE = "lyricprovider/spotify-music";
    static final String QISHUI_MUSIC_PLAYER_PACKAGE = "com.luna.music";
    static final String QISHUI_MUSIC_SOURCE = "lyricprovider/qishui-music";
    static final String KUGOU_MUSIC_PLAYER_PACKAGE = "com.kugou.android";
    static final String KUGOU_MUSIC_SOURCE = "lyricprovider/kugou-music";
    static final String KUGOU_CONCEPT_MUSIC_PLAYER_PACKAGE = "com.kugou.android.lite";
    static final String KUGOU_CONCEPT_MUSIC_SOURCE = "lyricprovider/kugou-concept-music";
    static final String POWERAMP_PLAYER_PACKAGE = "com.maxmpz.audioplayer";
    static final String POWERAMP_SOURCE = "lyricprovider/poweramp-music";
    static final long POWERAMP_SYSTEMUI_TRACK_AUTHORITY_MS = 12_000L;
    private static final String APPLE_MUSIC_PLAYER_PACKAGE = "com.apple.android.music";
    private static final String APPLE_MUSIC_SOURCE = "lyricprovider/apple-music";

    private static final Source[] EXTERNAL_SOURCES = {
            new Source(APPLE_MUSIC_SOURCE, APPLE_MUSIC_PLAYER_PACKAGE, false, false, false),
            new Source(SPOTIFY_SOURCE, SPOTIFY_PLAYER_PACKAGE, true, false, false),
            new Source(QISHUI_MUSIC_SOURCE, QISHUI_MUSIC_PLAYER_PACKAGE, true, false, false),
            new Source(KUGOU_MUSIC_SOURCE, KUGOU_MUSIC_PLAYER_PACKAGE, true, true, false),
            new Source(KUGOU_CONCEPT_MUSIC_SOURCE,
                    KUGOU_CONCEPT_MUSIC_PLAYER_PACKAGE,
                    true,
                    true,
                    false),
            new Source(POWERAMP_SOURCE, POWERAMP_PLAYER_PACKAGE, false, true, true)
    };

    private static final String[] BRIDGE_PLAYER_PACKAGES = {
            QQ_MUSIC_PLAYER_PACKAGE,
            QQ_MUSIC_HD_PLAYER_PACKAGE,
            NETEASE_MUSIC_PLAYER_PACKAGE,
            NETEASE_MUSIC_HONOR_PLAYER_PACKAGE,
            APPLE_MUSIC_PLAYER_PACKAGE,
            POWERAMP_PLAYER_PACKAGE,
            SPOTIFY_PLAYER_PACKAGE,
            QISHUI_MUSIC_PLAYER_PACKAGE,
            KUGOU_MUSIC_PLAYER_PACKAGE,
            KUGOU_CONCEPT_MUSIC_PLAYER_PACKAGE
    };

    private ExternalLyricSources() {
    }

    static boolean isBridgePlayerPackage(String packageName) {
        if (isEmpty(packageName)) {
            return false;
        }
        for (String bridgePackage : BRIDGE_PLAYER_PACKAGES) {
            if (bridgePackage.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    static String[] bridgePlayerPackages() {
        return BRIDGE_PLAYER_PACKAGES.clone();
    }

    static String playerPackageForSource(String source) {
        Source externalSource = findBySource(source);
        return externalSource == null ? "" : externalSource.playerPackage;
    }

    static boolean supportsPlaybackState(String source) {
        Source externalSource = findBySource(source);
        return externalSource != null && externalSource.supportsPlaybackState;
    }

    static boolean canPromoteAsAuthoritative(String source, String packageName) {
        Source externalSource = findBySource(source);
        return externalSource != null
                && externalSource.canPromoteAsAuthoritative
                && externalSource.playerPackage.equals(packageName);
    }

    static boolean canPromoteLatestGeneratedTrackForActivePlayer(
            String source,
            String packageName) {
        return APPLE_MUSIC_SOURCE.equals(source)
                && APPLE_MUSIC_PLAYER_PACKAGE.equals(packageName);
    }

    static boolean requiresSystemUiLyricReadyRefresh(String source, String packageName) {
        return APPLE_MUSIC_SOURCE.equals(source)
                && APPLE_MUSIC_PLAYER_PACKAGE.equals(packageName);
    }

    static boolean mayRequireSystemUiLyricReadyRefresh(String packageName) {
        return APPLE_MUSIC_PLAYER_PACKAGE.equals(packageName);
    }

    static boolean allowsTitleOnlyFallbackMatch(String source) {
        Source externalSource = findBySource(source);
        return externalSource != null && externalSource.allowsTitleOnlyFallbackMatch;
    }

    static boolean isPowerampSource(String source) {
        return POWERAMP_SOURCE.equals(source);
    }

    static boolean isPowerampPackage(String packageName) {
        return POWERAMP_PLAYER_PACKAGE.equals(packageName);
    }

    static boolean isSpotifyContext(String packageName, String source) {
        return SPOTIFY_PLAYER_PACKAGE.equals(packageName) || SPOTIFY_SOURCE.equals(source);
    }

    static boolean shouldApplyOfficialDisplayTextAliases(String source) {
        return !APPLE_MUSIC_SOURCE.equals(source)
                && !KUGOU_MUSIC_SOURCE.equals(source);
    }

    static boolean canOverrideFavoriteActionWithTranslation(String packageName) {
        return QQ_MUSIC_PLAYER_PACKAGE.equals(packageName)
                || QQ_MUSIC_HD_PLAYER_PACKAGE.equals(packageName)
                || NETEASE_MUSIC_PLAYER_PACKAGE.equals(packageName)
                || NETEASE_MUSIC_HONOR_PLAYER_PACKAGE.equals(packageName)
                || APPLE_MUSIC_PLAYER_PACKAGE.equals(packageName)
                || QISHUI_MUSIC_PLAYER_PACKAGE.equals(packageName)
                || KUGOU_MUSIC_PLAYER_PACKAGE.equals(packageName)
                || KUGOU_CONCEPT_MUSIC_PLAYER_PACKAGE.equals(packageName);
    }

    private static Source findBySource(String source) {
        if (isEmpty(source)) {
            return null;
        }
        for (Source externalSource : EXTERNAL_SOURCES) {
            if (externalSource.source.equals(source)) {
                return externalSource;
            }
        }
        return null;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static final class Source {
        final String source;
        final String playerPackage;
        final boolean supportsPlaybackState;
        final boolean canPromoteAsAuthoritative;
        final boolean allowsTitleOnlyFallbackMatch;

        Source(
                String source,
                String playerPackage,
                boolean supportsPlaybackState,
                boolean canPromoteAsAuthoritative,
                boolean allowsTitleOnlyFallbackMatch) {
            this.source = source;
            this.playerPackage = playerPackage;
            this.supportsPlaybackState = supportsPlaybackState;
            this.canPromoteAsAuthoritative = canPromoteAsAuthoritative;
            this.allowsTitleOnlyFallbackMatch = allowsTitleOnlyFallbackMatch;
        }
    }
}
