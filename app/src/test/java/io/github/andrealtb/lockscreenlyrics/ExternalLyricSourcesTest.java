package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExternalLyricSourcesTest {
    @Test
    public void mapsKnownExternalSourcesToPlayerPackages() {
        assertEquals(
                "com.spotify.music",
                ExternalLyricSources.playerPackageForSource("lyricprovider/spotify-music"));
        assertEquals(
                "com.maxmpz.audioplayer",
                ExternalLyricSources.playerPackageForSource("lyricprovider/poweramp-music"));
        assertEquals(
                "com.apple.android.music",
                ExternalLyricSources.playerPackageForSource("lyricprovider/apple-music"));
        assertEquals("", ExternalLyricSources.playerPackageForSource("unknown"));
    }

    @Test
    public void bridgePlayerPackagesIncludeProviderBackedPlayers() {
        assertTrue(ExternalLyricSources.isBridgePlayerPackage("com.tencent.qqmusic"));
        assertTrue(ExternalLyricSources.isBridgePlayerPackage("com.tencent.qqmusicpad"));
        assertTrue(ExternalLyricSources.isBridgePlayerPackage("com.netease.cloudmusic"));
        assertTrue(ExternalLyricSources.isBridgePlayerPackage("com.hihonor.cloudmusic"));
        assertTrue(ExternalLyricSources.isBridgePlayerPackage("com.apple.android.music"));
        assertTrue(ExternalLyricSources.isBridgePlayerPackage("com.maxmpz.audioplayer"));
        assertTrue(ExternalLyricSources.isBridgePlayerPackage("com.spotify.music"));
        assertFalse(ExternalLyricSources.isBridgePlayerPackage("com.example.music"));
    }

    @Test
    public void favoriteActionOverrideIsLimitedToOfficialLyricPlayers() {
        assertTrue(ExternalLyricSources.canOverrideFavoriteActionWithTranslation(
                "com.tencent.qqmusic"));
        assertTrue(ExternalLyricSources.canOverrideFavoriteActionWithTranslation(
                "com.tencent.qqmusicpad"));
        assertTrue(ExternalLyricSources.canOverrideFavoriteActionWithTranslation(
                "com.netease.cloudmusic"));
        assertTrue(ExternalLyricSources.canOverrideFavoriteActionWithTranslation(
                "com.hihonor.cloudmusic"));
        assertTrue(ExternalLyricSources.canOverrideFavoriteActionWithTranslation(
                "com.apple.android.music"));
        assertFalse(ExternalLyricSources.canOverrideFavoriteActionWithTranslation(
                "com.maxmpz.audioplayer"));
        assertFalse(ExternalLyricSources.canOverrideFavoriteActionWithTranslation(
                "com.spotify.music"));
    }

    @Test
    public void officialDisplayAliasesAreSkippedForAppleMusicProvider() {
        assertFalse(ExternalLyricSources.shouldApplyOfficialDisplayTextAliases(
                "lyricprovider/apple-music"));
        assertTrue(ExternalLyricSources.shouldApplyOfficialDisplayTextAliases(
                "lyricprovider/spotify-music"));
        assertTrue(ExternalLyricSources.shouldApplyOfficialDisplayTextAliases(
                "lyricprovider/poweramp-music"));
        assertTrue(ExternalLyricSources.shouldApplyOfficialDisplayTextAliases(""));
    }

    @Test
    public void sourceCapabilitiesStayProviderSpecific() {
        assertTrue(ExternalLyricSources.supportsPlaybackState("lyricprovider/spotify-music"));
        assertFalse(ExternalLyricSources.supportsPlaybackState("lyricprovider/poweramp-music"));
        assertTrue(ExternalLyricSources.canPromoteAsAuthoritative(
                "lyricprovider/poweramp-music",
                "com.maxmpz.audioplayer"));
        assertFalse(ExternalLyricSources.canPromoteAsAuthoritative(
                "lyricprovider/spotify-music",
                "com.spotify.music"));
        assertTrue(ExternalLyricSources.allowsTitleOnlyFallbackMatch(
                "lyricprovider/poweramp-music"));
        assertFalse(ExternalLyricSources.allowsTitleOnlyFallbackMatch(
                "lyricprovider/spotify-music"));
        assertFalse(ExternalLyricSources.supportsPlaybackState("lyricprovider/apple-music"));
        assertFalse(ExternalLyricSources.canPromoteAsAuthoritative(
                "lyricprovider/apple-music",
                "com.apple.android.music"));
        assertFalse(ExternalLyricSources.allowsTitleOnlyFallbackMatch(
                "lyricprovider/apple-music"));
    }
}
