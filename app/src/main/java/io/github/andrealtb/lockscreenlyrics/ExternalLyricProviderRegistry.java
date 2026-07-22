package io.github.andrealtb.lockscreenlyrics;

/**
 * Static source-to-player admission registry for direct LyricProvider broadcasts.
 *
 * <p>A new Provider must register its stable source-to-player-package binding here and must not
 * teach {@link LockscreenLyricsModule} about its package or source id.</p>
 */
final class ExternalLyricProviderRegistry {
    static final String QQ_MUSIC_PLAYER_PACKAGE = "com.tencent.qqmusic";
    static final String QQ_MUSIC_HD_PLAYER_PACKAGE = "com.tencent.qqmusicpad";
    static final String QQ_MUSIC_SOURCE = "lyricprovider/qq-music";
    static final String NETEASE_MUSIC_PLAYER_PACKAGE = "com.netease.cloudmusic";
    static final String NETEASE_MUSIC_HONOR_PLAYER_PACKAGE = "com.hihonor.cloudmusic";
    static final String NETEASE_MUSIC_SOURCE = "lyricprovider/netease-cloud-music";
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
    static final String APPLE_MUSIC_PLAYER_PACKAGE = "com.apple.android.music";
    static final String APPLE_MUSIC_SOURCE = "lyricprovider/apple-music";
    static final String LX_MUSIC_PLAYER_PACKAGE = "cn.toside.music.mobile";
    static final String LX_WALNUT_MUSIC_PLAYER_PACKAGE = "com.lxwalnut.music.mobile";
    static final String LX_MUSIC_SOURCE = "lyricprovider/lx-music";
    static final String LX_WALNUT_MUSIC_SOURCE = "lyricprovider/lx-walnut-music";

    private static final Source[] EXTERNAL_SOURCES = {
            new Source(APPLE_MUSIC_SOURCE, APPLE_MUSIC_PLAYER_PACKAGE, false, false, false),
            new Source(LX_MUSIC_SOURCE, LX_MUSIC_PLAYER_PACKAGE, false, false, false),
            new Source(
                    LX_WALNUT_MUSIC_SOURCE,
                    LX_WALNUT_MUSIC_PLAYER_PACKAGE,
                    false,
                    false,
                    false),
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
            LX_MUSIC_PLAYER_PACKAGE,
            LX_WALNUT_MUSIC_PLAYER_PACKAGE,
            POWERAMP_PLAYER_PACKAGE,
            SPOTIFY_PLAYER_PACKAGE,
            QISHUI_MUSIC_PLAYER_PACKAGE,
            KUGOU_MUSIC_PLAYER_PACKAGE,
            KUGOU_CONCEPT_MUSIC_PLAYER_PACKAGE
    };

    private ExternalLyricProviderRegistry() {
    }

    static boolean isTrustedProviderHostPackage(String packageName) {
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

    static String[] trustedProviderHostPackages() {
        return BRIDGE_PLAYER_PACKAGES.clone();
    }

    static String trustedHostPackageForSource(String source) {
        Source externalSource = findBySource(source);
        return externalSource == null ? "" : externalSource.playerPackage;
    }

    /**
     * Provider code is injected into the music-player process. Direct v4 broadcasts therefore
     * carry a declared source and this static whitelist binds it to its only allowed host.
     */
    static boolean isTrustedSourceBoundToHostPackage(String source, String packageName) {
        if (isEmpty(source) || isEmpty(packageName)) {
            return false;
        }
        if (QQ_MUSIC_SOURCE.equals(source)) {
            return QQ_MUSIC_PLAYER_PACKAGE.equals(packageName)
                    || QQ_MUSIC_HD_PLAYER_PACKAGE.equals(packageName);
        }
        if (NETEASE_MUSIC_SOURCE.equals(source)) {
            return NETEASE_MUSIC_PLAYER_PACKAGE.equals(packageName)
                    || NETEASE_MUSIC_HONOR_PLAYER_PACKAGE.equals(packageName);
        }
        Source externalSource = findBySource(source);
        return externalSource != null && externalSource.playerPackage.equals(packageName);
    }

    static boolean registeredProviderSupportsPlaybackState(String source) {
        Source externalSource = findBySource(source);
        return externalSource != null && externalSource.supportsPlaybackState;
    }

    static boolean registeredProviderCanPromoteAsAuthoritative(String source, String packageName) {
        Source externalSource = findBySource(source);
        return externalSource != null
                && externalSource.canPromoteAsAuthoritative
                && externalSource.playerPackage.equals(packageName);
    }

    static boolean registeredProviderAllowsTitleOnlyFallbackMatch(String source) {
        Source externalSource = findBySource(source);
        return externalSource != null && externalSource.allowsTitleOnlyFallbackMatch;
    }

    static boolean registeredProviderMayOverrideFavoriteActionWithTranslation(String packageName) {
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
