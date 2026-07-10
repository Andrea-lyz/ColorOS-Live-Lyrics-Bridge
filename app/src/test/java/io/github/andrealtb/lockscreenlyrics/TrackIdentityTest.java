package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TrackIdentityTest {
    @Test
    public void lrcTitleTagCanContainCompositeTitleAndArtist() {
        assertEquals(
                TrackIdentity.buildKey("Alma Mater [Explicit]", "Bleachers"),
                TrackIdentity.buildLrcHintKey(
                        "Alma Mater (Explicit) - Bleachers",
                        ""));
    }

    @Test
    public void saltRelayArtistRestoresStableTrackIdentity() {
        TrackIdentity.SaltRelayIdentity identity =
                TrackIdentity.parseSaltRelayArtist(
                        "William Black/Fairlane - Broken");

        assertEquals("Broken", identity.title);
        assertEquals("William Black/Fairlane", identity.artist);
    }

    @Test
    public void saltRelayArtistKeepsAdditionalTitleSeparators() {
        TrackIdentity.SaltRelayIdentity identity =
                TrackIdentity.parseSaltRelayArtist(
                        "Porter Robinson - Kitsune Maison Freestyle - Live");

        assertEquals("Kitsune Maison Freestyle - Live", identity.title);
        assertEquals("Porter Robinson", identity.artist);
    }

    @Test
    public void saltRelayIdentityMatchesExplicitLrcHintDuringMetadataHandoff() {
        TrackIdentity.SaltRelayIdentity identity =
                TrackIdentity.parseSaltRelayArtist("Adele - All I Ask");

        assertTrue(TrackIdentity.relayIdentityMatchesHint(
                identity,
                TrackIdentity.buildKey("All I Ask", "Adele")));
        assertFalse(TrackIdentity.relayIdentityMatchesHint(
                identity,
                TrackIdentity.buildKey("Actually Romantic", "Taylor Swift")));
    }

    @Test
    public void explicitSuffixDoesNotChangeTrackIdentity() {
        assertEquals(
                TrackIdentity.buildKey("Modern Girl", "Bleachers"),
                TrackIdentity.buildKey(
                        "Modern Girl [Explicit]",
                        "Bleachers"));
        assertEquals(
                TrackIdentity.buildKey("Jesus Is Dead", "Bleachers"),
                TrackIdentity.buildKey(
                        "Jesus Is Dead (Explicit)",
                        "Bleachers"));
    }

    @Test
    public void ordinaryBracketedTitleTextIsPreserved() {
        assertEquals(
                "song [live]|artist",
                TrackIdentity.buildKey("Song [Live]", "Artist"));
    }

    @Test
    public void missingLrcArtistStillMatchesTheSameTitle() {
        assertTrue(TrackIdentity.matchesHintKey(
                "alma mater|",
                TrackIdentity.buildKey("Alma Mater [Explicit]", "Bleachers")));
        assertFalse(TrackIdentity.matchesHintKey(
                "alma mater|",
                TrackIdentity.buildKey("Tiny Moves", "Bleachers")));
    }

    @Test
    public void featuredTitleAndArtistSeparatorsStillMatchMetadata() {
        assertTrue(TrackIdentity.matchesHintKey(
                TrackIdentity.buildLrcHintKey(
                        "2step (feat. Lil Baby)",
                        "Ed Sheeran,Lil Baby"),
                TrackIdentity.buildKey(
                        "2step",
                        "Ed Sheeran/Lil Baby")));
    }

    @Test
    public void qishuiBulletSeparatedArtistsMatchProviderArtists() {
        assertTrue(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey(
                        "NO BATID\u00C3O",
                        "PROPHECY/\u68CD\u5723"),
                TrackIdentity.buildKey(
                        "NO BATID\u00C3O",
                        "PROPHECY\u2022 \u68CD\u5723")));
        assertTrue(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey(
                        "Angel",
                        "\uC724\uBBF8\uB798/Bizzy/Tiger JK"),
                TrackIdentity.buildKey(
                        "Angel",
                        "\uC724\uBBF8\uB798\u2022 Bizzy\u2022 Tiger JK")));
    }

    @Test
    public void saltMetadataMatchesTimeStyleTitleAndOmittedFeaturedArtist() {
        assertTrue(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey(
                        "8:22am (feat. La Force)",
                        "Big Red Machine/La Force"),
                TrackIdentity.buildKey(
                        "8 22am (feat. La Force)",
                        "Big Red Machine")));
    }

    @Test
    public void omittedArtistDoesNotMatchWithoutAnExplicitFeaturedCredit() {
        assertFalse(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey("A Duet", "Singer/Guest"),
                TrackIdentity.buildKey("A Duet", "Singer")));
    }

    @Test
    public void featureNormalizationDoesNotMergeDifferentBaseTitles() {
        assertFalse(TrackIdentity.matchesHintKey(
                TrackIdentity.buildLrcHintKey(
                        "2step (feat. Lil Baby)",
                        "Ed Sheeran,Lil Baby"),
                TrackIdentity.buildKey(
                        "Shivers",
                        "Ed Sheeran/Lil Baby")));
    }

    @Test
    public void artistNormalizationStillRejectsDifferentCollaborators() {
        assertFalse(TrackIdentity.matchesHintKey(
                TrackIdentity.buildLrcHintKey(
                        "2step (feat. Lil Baby)",
                        "Ed Sheeran,Lil Baby"),
                TrackIdentity.buildKey(
                        "2step",
                        "Ed Sheeran/Stormzy")));
    }

    @Test
    public void translatedTitleSuffixMatchesPlainLrcTitle() {
        assertTrue(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey("Boulevard of Broken Dreams", "Green Day"),
                TrackIdentity.buildKey(
                        "Boulevard of Broken Dreams（碎梦大道）",
                        "Green Day")));
    }

    @Test
    public void japaneseTitleMatchesChineseTranslatedSuffixFromSaltMetadata() {
        assertTrue(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey("17\u3055\u3044\u306e\u3046\u305f\u3002", "\u300e\u30e6\u30a4\u30ab\u300f"),
                TrackIdentity.buildKey(
                        "17\u3055\u3044\u306e\u3046\u305f\u3002 (17\u5c81\u7684\u6b4c\u3002)",
                        "\u300e\u30e6\u30a4\u30ab\u300f")));
    }

    @Test
    public void pureChineseTitleDoesNotTreatBracketedTextAsAnAlias() {
        assertFalse(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey("\u6211\u7684\u6b4c", "\u6b4c\u624b"),
                TrackIdentity.buildKey("\u6211\u7684\u6b4c\uff08\u53e6\u4e00\u4e2a\u540d\u5b57\uff09", "\u6b4c\u624b")));
    }

    @Test
    public void versionSuffixIsNotTreatedAsTranslationAlias() {
        assertFalse(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey("Sunny Boy", "Artist"),
                TrackIdentity.buildKey("Sunny Boy（日语翻唱）", "Artist")));
        assertFalse(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey("Song", "Artist"),
                TrackIdentity.buildKey("Song (Live)", "Artist")));
    }

    @Test
    public void saltHintMatchesCurlyApostropheVaultEditionMetadata() {
        assertTrue(TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey("You're Losing Me", "Taylor Swift"),
                TrackIdentity.buildKey(
                        "You\u2019re Losing Me (From The Vault)",
                        "Taylor Swift")));
    }

}
