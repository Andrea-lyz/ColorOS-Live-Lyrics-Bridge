package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExternalLyricProviderRegistryTest {
    @Test
    public void mapsEveryAdmittedProviderSourceToItsHostPlayer() {
        assertEquals("com.spotify.music", ExternalLyricProviderRegistry.trustedHostPackageForSource(
                "lyricprovider/spotify-music"));
        assertEquals("com.maxmpz.audioplayer", ExternalLyricProviderRegistry.trustedHostPackageForSource(
                "lyricprovider/poweramp-music"));
        assertEquals("com.apple.android.music", ExternalLyricProviderRegistry.trustedHostPackageForSource(
                "lyricprovider/apple-music"));
        assertEquals("com.luna.music", ExternalLyricProviderRegistry.trustedHostPackageForSource(
                "lyricprovider/qishui-music"));
        assertEquals("com.kugou.android", ExternalLyricProviderRegistry.trustedHostPackageForSource(
                "lyricprovider/kugou-music"));
        assertEquals("com.kugou.android.lite", ExternalLyricProviderRegistry.trustedHostPackageForSource(
                "lyricprovider/kugou-concept-music"));
        assertEquals("cn.toside.music.mobile",
                ExternalLyricProviderRegistry.trustedHostPackageForSource(
                        "lyricprovider/lx-music"));
        assertEquals("com.lxwalnut.music.mobile",
                ExternalLyricProviderRegistry.trustedHostPackageForSource(
                        "lyricprovider/lx-walnut-music"));
        assertEquals("com.metrolist.music",
                ExternalLyricProviderRegistry.trustedHostPackageForSource(
                        "lyricprovider/metrolist-music"));
        assertEquals("com.ella.music",
                ExternalLyricProviderRegistry.trustedHostPackageForSource(
                        "lyricprovider/halcyon"));
        assertEquals("", ExternalLyricProviderRegistry.trustedHostPackageForSource("unknown"));
    }

    @Test
    public void admitsOnlyTheExpectedPlayerPackageForEveryKnownSource() {
        assertTrue(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/qq-music", "com.tencent.qqmusic"));
        assertTrue(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/qq-music", "com.tencent.qqmusicpad"));
        assertTrue(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/netease-cloud-music", "com.hihonor.cloudmusic"));
        assertTrue(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/spotify-music", "com.spotify.music"));
        assertTrue(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/lx-music", "cn.toside.music.mobile"));
        assertTrue(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/lx-walnut-music", "com.lxwalnut.music.mobile"));
        assertTrue(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/metrolist-music", "com.metrolist.music"));
        assertTrue(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/halcyon", "com.ella.music"));
        assertFalse(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/spotify-music", "com.tencent.qqmusic"));
        assertFalse(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/lx-music", "com.lxwalnut.music.mobile"));
        assertFalse(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/metrolist-music", "com.spotify.music"));
        assertFalse(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "lyricprovider/unknown", "com.example.music"));
    }

    @Test
    public void returnsProviderBackedPlayerPackagesWithoutExposingMutableRegistryState() {
        String[] packages = ExternalLyricProviderRegistry.trustedProviderHostPackages();
        assertTrue(packages.length >= 13);
        boolean hasKuWoPlayer = false;
        for (String packageName : packages) {
            hasKuWoPlayer |= ExternalLyricProviderRegistry.KUWO_MUSIC_PLAYER_PACKAGE
                    .equals(packageName);
        }
        assertTrue(hasKuWoPlayer);
        packages[0] = "com.example.music";
        assertFalse("com.example.music".equals(
                ExternalLyricProviderRegistry.trustedProviderHostPackages()[0]));
    }

    @Test
    public void nativeKuWoLyricInfoHasNoDirectBroadcastSourceBinding() {
        assertEquals("", ExternalLyricProviderRegistry.trustedHostPackageForSource(
                "kuwo-internal"));
        assertFalse(ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                "kuwo-internal", ExternalLyricProviderRegistry.KUWO_MUSIC_PLAYER_PACKAGE));
    }

    @Test
    public void registeredDefaultsPreserveKnownProviderCapabilities() {
        ExternalLyricSourceProfile poweramp =
                ExternalLyricSourceProfile.registeredProviderDefaults(
                        "lyricprovider/poweramp-music");
        assertTrue(poweramp.canPromoteAsAuthoritative);
        assertTrue(poweramp.allowsTitleOnlyFallbackMatch);
        assertFalse(poweramp.supportsPlaybackState);

        ExternalLyricSourceProfile spotify =
                ExternalLyricSourceProfile.registeredProviderDefaults(
                        "lyricprovider/spotify-music");
        assertTrue(spotify.supportsPlaybackState);
        assertFalse(spotify.canPromoteAsAuthoritative);

        ExternalLyricSourceProfile metrolist =
                ExternalLyricSourceProfile.registeredProviderDefaults(
                        "lyricprovider/metrolist-music");
        assertTrue(metrolist.supportsPlaybackState);
        assertTrue(metrolist.supportsTrackGeneration);
        assertTrue(metrolist.canPromoteAsAuthoritative);
        assertFalse(metrolist.allowsTitleOnlyFallbackMatch);

        ExternalLyricSourceProfile halcyon =
                ExternalLyricSourceProfile.registeredProviderDefaults(
                        "lyricprovider/halcyon");
        assertTrue(halcyon.canPromoteAsAuthoritative);
        assertTrue(halcyon.allowsTitleOnlyFallbackMatch);
        assertFalse(halcyon.supportsPlaybackState);
    }

    @Test
    public void version4ProviderDeclarationCannotBypassTrustedSourceBinding() {
        ExternalLyricSourceProfile trusted =
                ExternalLyricSourceProfile.version4ProviderDeclaration(
                        "lyricprovider/spotify-music",
                        "com.spotify.music",
                        "playbackState trackGeneration",
                        "",
                        "");
        assertTrue(trusted.supportsPlaybackState);
        assertTrue(trusted.supportsTrackGeneration);
        assertEquals("com.spotify.music", trusted.playerPackage);

        ExternalLyricSourceProfile metrolist =
                ExternalLyricSourceProfile.version4ProviderDeclaration(
                        "lyricprovider/metrolist-music",
                        "com.metrolist.music",
                        "playbackState,trackGeneration,currentTrackAuthority",
                        "mediaId,trackKey,titleArtist",
                        "");
        assertTrue(metrolist.supportsPlaybackState);
        assertTrue(metrolist.supportsTrackGeneration);
        assertTrue(metrolist.canPromoteAsAuthoritative);

        ExternalLyricSourceProfile metrolistWrongHost =
                ExternalLyricSourceProfile.version4ProviderDeclaration(
                        "lyricprovider/metrolist-music",
                        "com.spotify.music",
                        "playbackState,trackGeneration",
                        "mediaId,trackKey,titleArtist",
                        "");
        assertEquals("", metrolistWrongHost.playerPackage);
        assertFalse(metrolistWrongHost.supportsPlaybackState);

        ExternalLyricSourceProfile lx = ExternalLyricSourceProfile.version4ProviderDeclaration(
                "lyricprovider/lx-music",
                "cn.toside.music.mobile",
                "trackGeneration translationToggle",
                "mediaId,trackKey,titleArtist",
                "");
        assertTrue(lx.supportsTrackGeneration);
        assertTrue(lx.canOverrideFavoriteActionWithTranslation);
        assertFalse(lx.canPromoteAsAuthoritative);

        ExternalLyricSourceProfile lxWalnut =
                ExternalLyricSourceProfile.version4ProviderDeclaration(
                        "lyricprovider/lx-walnut-music",
                        "com.lxwalnut.music.mobile",
                        "trackGeneration translationToggle",
                        "mediaId,trackKey,titleArtist",
                        "");
        assertTrue(lxWalnut.supportsTrackGeneration);
        assertTrue(lxWalnut.canOverrideFavoriteActionWithTranslation);
        assertFalse(lxWalnut.canPromoteAsAuthoritative);

        ExternalLyricSourceProfile rejected =
                ExternalLyricSourceProfile.version4ProviderDeclaration(
                        "lyricprovider/unknown",
                        "com.example.music",
                        "playbackState currentTrackAuthority",
                        "titleOnly",
                        "currentTrack");
        assertEquals("", rejected.playerPackage);
        assertFalse(rejected.supportsPlaybackState);
        assertFalse(rejected.canPromoteAsAuthoritative);
    }
}
