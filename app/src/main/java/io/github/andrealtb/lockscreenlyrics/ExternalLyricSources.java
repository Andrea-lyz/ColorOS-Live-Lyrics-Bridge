package io.github.andrealtb.lockscreenlyrics;

final class ExternalLyricSources {
    static final String SPOTIFY_PLAYER_PACKAGE = "com.spotify.music";
    static final String SPOTIFY_SOURCE = "lyricprovider/spotify-music";
    static final String POWERAMP_PLAYER_PACKAGE = "com.maxmpz.audioplayer";
    static final String POWERAMP_SOURCE = "lyricprovider/poweramp-music";
    static final long POWERAMP_SYSTEMUI_TRACK_AUTHORITY_MS = 12_000L;

    private static final Source[] EXTERNAL_SOURCES = {
            new Source(SPOTIFY_SOURCE, SPOTIFY_PLAYER_PACKAGE, true, false, false),
            new Source(POWERAMP_SOURCE, POWERAMP_PLAYER_PACKAGE, false, true, true)
    };

    private static final String[] BRIDGE_PLAYER_PACKAGES = {
            "com.tencent.qqmusic",
            "com.tencent.qqmusicpad",
            "com.netease.cloudmusic",
            "com.hihonor.cloudmusic",
            "com.apple.android.music",
            POWERAMP_PLAYER_PACKAGE,
            SPOTIFY_PLAYER_PACKAGE
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
