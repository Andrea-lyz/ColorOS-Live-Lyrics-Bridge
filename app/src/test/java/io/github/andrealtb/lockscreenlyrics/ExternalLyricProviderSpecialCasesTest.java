package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExternalLyricProviderSpecialCasesTest {
    @Test
    public void providerSpecificMethodsDescribeTheAffectedProviderAndBehaviour() {
        assertTrue(ExternalLyricProviderSpecialCases
                .shouldPreserveQiShuiOrKuGouConceptExternalLyricSurface(
                        "lyricprovider/qishui-music"));
        assertTrue(ExternalLyricProviderSpecialCases
                .shouldPreserveQiShuiOrKuGouConceptExternalLyricSurface(
                        "lyricprovider/kugou-concept-music"));
        assertFalse(ExternalLyricProviderSpecialCases
                .shouldPreserveQiShuiOrKuGouConceptExternalLyricSurface(
                        "lyricprovider/spotify-music"));
        assertFalse(ExternalLyricProviderSpecialCases
                .shouldApplyOfficialDisplayTextAliasesForProvider("lyricprovider/apple-music"));
        assertFalse(ExternalLyricProviderSpecialCases
                .shouldApplyOfficialDisplayTextAliasesForProvider("lyricprovider/kugou-music"));
        assertTrue(ExternalLyricProviderSpecialCases
                .shouldApplyOfficialDisplayTextAliasesForProvider(
                        "lyricprovider/kugou-concept-music"));
        assertTrue(ExternalLyricProviderSpecialCases
                .shouldApplyOfficialDisplayTextAliasesForProvider("lyricprovider/spotify-music"));
    }

    @Test
    public void appleMusicAndSpotifySpecialCasesStayNarrowlyScoped() {
        assertTrue(ExternalLyricProviderSpecialCases
                .shouldReplaySystemUiAfterAppleMusicLyricReady(
                        "lyricprovider/apple-music", "com.apple.android.music"));
        assertFalse(ExternalLyricProviderSpecialCases
                .shouldReplaySystemUiAfterAppleMusicLyricReady(
                        "lyricprovider/apple-music", "com.spotify.music"));
        assertTrue(ExternalLyricProviderSpecialCases.isSpotifyExternalLyricContext(
                "com.spotify.music", ""));
        assertFalse(ExternalLyricProviderSpecialCases.isSpotifyExternalLyricContext(
                "com.example.music", "lyricprovider/qishui-music"));
    }

    @Test
    public void lxBluetoothMetadataNormalizationRequiresTheTrustedProjectionShape() {
        assertTrue(ExternalLyricProviderSpecialCases
                .shouldNormalizeLxBluetoothLyricMetadataForSystemUi(
                        "lyricprovider/lx-walnut-music",
                        "com.lxwalnut.music.mobile",
                        "",
                        "Cruel Summer - Taylor Swift",
                        "Cruel Summer",
                        "Taylor Swift"));
        assertTrue(ExternalLyricProviderSpecialCases
                .shouldNormalizeLxBluetoothLyricMetadataForSystemUi(
                        "lyricprovider/lx-music",
                        "cn.toside.music.mobile",
                        "But ooh whoa oh",
                        "Cruel Summer - Taylor Swift",
                        "Cruel Summer",
                        "Taylor Swift"));
        assertFalse(ExternalLyricProviderSpecialCases
                .shouldNormalizeLxBluetoothLyricMetadataForSystemUi(
                        "lyricprovider/lx-walnut-music",
                        "com.lxwalnut.music.mobile",
                        "Cruel Summer",
                        "Taylor Swift",
                        "Cruel Summer",
                        "Taylor Swift"));
        assertFalse(ExternalLyricProviderSpecialCases
                .shouldNormalizeLxBluetoothLyricMetadataForSystemUi(
                        "lyricprovider/lx-walnut-music",
                        "cn.toside.music.mobile",
                        "",
                        "Cruel Summer - Taylor Swift",
                        "Cruel Summer",
                        "Taylor Swift"));
        assertFalse(ExternalLyricProviderSpecialCases
                .shouldNormalizeLxBluetoothLyricMetadataForSystemUi(
                        "lyricprovider/spotify-music",
                        "com.spotify.music",
                        "",
                        "Cruel Summer - Taylor Swift",
                        "Cruel Summer",
                        "Taylor Swift"));
    }

    @Test
    public void kuGouSuppressionOnlyTargetsIncompleteOfficialPayloads() {
        LyricInfoContract.Payload incompleteOfficialPayload = new LyricInfoContract.Payload(
                "title",
                "artist",
                "",
                "id",
                "plain lyric",
                "",
                "",
                "",
                "",
                0L,
                "");
        assertTrue(ExternalLyricProviderSpecialCases.shouldSuppressKuGouOfficialLyricInfo(
                "com.kugou.android",
                incompleteOfficialPayload,
                false));
        assertFalse(ExternalLyricProviderSpecialCases.shouldSuppressKuGouOfficialLyricInfo(
                "com.spotify.music",
                incompleteOfficialPayload,
                false));
    }
}
