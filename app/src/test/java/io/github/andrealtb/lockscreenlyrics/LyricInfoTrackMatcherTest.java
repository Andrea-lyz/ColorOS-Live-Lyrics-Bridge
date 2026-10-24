package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricInfoTrackMatcherTest {
    @Test
    public void rawTrackHintMismatchMakesPayloadStale() throws Exception {
        LyricInfoContract.Payload payload = payload(
                "Instrumental Gap",
                "Artist B",
                "",
                "[ti:Previous Song]\n[ar:Artist A]\n[00:00]old line");

        assertFalse(LyricInfoTrackMatcher.payloadMatchesTrack(
                payload,
                "Instrumental Gap",
                "Artist B"));
    }

    @Test
    public void nativePayloadUsesOriginalTrackKeyAfterDisplayCleanup() {
        String originalKey = TrackIdentity.buildKey("Song (Live)", "Artist feat. Guest");
        LyricInfoContract.Payload payload = new LyricInfoContract.Payload(
                "Song",
                "Artist",
                "Album",
                "id",
                "[00:00]line",
                "[00:00]line",
                "",
                "native-test",
                originalKey,
                1L,
                "test");

        assertTrue(LyricInfoTrackMatcher.payloadMatchesTrack(
                payload,
                "Song (Live)",
                "Artist feat. Guest"));
    }

    @Test
    public void providerCoreStableTrackKeyMatchesItsSystemUiTitleAndArtist() {
        LyricInfoContract.Payload payload = new LyricInfoContract.Payload(
                "I Knew It, I Knew You (Piano Version)",
                "Taylor Swift",
                "",
                "",
                "[00:00]line",
                "[00:00]line",
                "",
                "com.lxwalnut.music.mobile",
                "noid|i knew it, i knew you (piano version)|taylor swift|190",
                4L,
                "com.lxwalnut.music.mobile-v5");

        assertTrue(LyricInfoTrackMatcher.payloadMatchesTrack(
                payload,
                "I Knew It, I Knew You (Piano Version)",
                "Taylor Swift"));
        assertFalse(LyricInfoTrackMatcher.payloadMatchesTrack(
                payload,
                "I Knew It, I Knew You (Acoustic Version)",
                "Taylor Swift"));
    }

    @Test
    public void matchingStableKeyWinsOverDashedFirstLyric() {
        LyricInfoContract.Payload payload = new LyricInfoContract.Payload(
                "Song", "Artist", "", "", "[00:01]A - B", "[00:01]A - B", "",
                "provider", "id|song|artist|180", 7L, "provider-v5");

        assertTrue(LyricInfoTrackMatcher.payloadMatchesTrack(payload, "Song", "Artist"));
    }

    @Test
    public void identitylessPayloadDoesNotMatchArbitraryTrack() {
        LyricInfoContract.Payload payload = payload("", "", "", "[00:01]old line");

        assertFalse(LyricInfoTrackMatcher.payloadMatchesTrack(payload, "New Song", "Artist"));
    }

    @Test
    public void samePlayerNativeOnlyRefreshCanKeepTheCompleteIdentityBoundPayload() {
        LyricInfoContract.Payload complete = payload(
                "Song", "Artist", "player", "[00:01]<00:01>Hello<00:02>\n");
        LyricInfoContract.Payload nativeOnly = nativeOnly("Song", "Artist");

        assertTrue(LyricInfoContract.containsTimedLrc(nativeOnly.lyric));
        assertFalse(nativeOnly.hasWordTiming());
        assertTrue(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, TrackIdentity.buildKey("Song", "Artist"), "player",
                nativeOnly, "player", "Song", "Artist"));
    }

    @Test
    public void trackChangesAndPlayerChangesCannotReuseThePreviousModel() {
        LyricInfoContract.Payload complete = payload(
                "Song", "Artist", "player", "[00:01]Hello\n");
        String bound = TrackIdentity.buildKey("Song", "Artist");

        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly("Next Song", "Artist"),
                "player", "Next Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly("Song", "Other Artist"),
                "player", "Song", "Other Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly("Song", "Artist"),
                "other-player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly("Song", "Artist"),
                "", "Song", "Artist"));
    }

    @Test
    public void nativePacketMustAgreeWithTheObservedIdentity() {
        LyricInfoContract.Payload complete = payload(
                "Song", "Artist", "player", "[00:01]Hello\n");
        String bound = TrackIdentity.buildKey("Song", "Artist");

        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly("Wrong Song", "Artist"),
                "player", "Song", "Artist"));
    }

    @Test
    public void completeAndSentenceWindowUpdatesStillUseTheNormalPublishPath() throws Exception {
        LyricInfoContract.Payload complete = payload(
                "Song", "Artist", "player", "[00:01]Hello\n");
        String bound = TrackIdentity.buildKey("Song", "Artist");
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", complete, "player", "Song", "Artist"));

        LyricInfoContract.Payload snapshot = LyricInfoContract.parse(
                new org.json.JSONObject()
                        .put("songName", "Song").put("artist", "Artist")
                        .put("lyric", "[00:00.000]Hello\n[00:01.000]World\n")
                        .put("displayMode", "sentence-window-v1").put("currentLine", 1)
                        .toString());
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", snapshot, "player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                snapshot, bound, "player", nativeOnly("Song", "Artist"),
                "player", "Song", "Artist"));
    }

    @Test
    public void conflictingExplicitMediaIdOrGenerationRejectsSameTitleReuse() {
        String lrc = "[00:01]Hello\n";
        String bound = TrackIdentity.buildKey("Song", "Artist");
        LyricInfoContract.Payload complete = new LyricInfoContract.Payload(
                "Song", "Artist", "", "host-id", lrc, lrc, "", "player",
                "host-id|song|artist|180", 7L, "player-v5");
        LyricInfoContract.Payload sameId = new LyricInfoContract.Payload(
                "Song", "Artist", "", "host-id", lrc, "", "", "", "", 0L, "");
        LyricInfoContract.Payload differentId = new LyricInfoContract.Payload(
                "Song", "Artist", "", "other-id", lrc, "", "", "", "", 0L, "");
        LyricInfoContract.Payload nextGeneration = new LyricInfoContract.Payload(
                "Song", "Artist", "", "host-id", lrc, "", "", "", "", 8L, "");

        assertTrue(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", sameId, "player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", differentId, "player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nextGeneration, "player", "Song", "Artist"));
    }

    @Test
    public void missingCompletePayloadOrObservedBindingCannotAuthorizeRetention() {
        LyricInfoContract.Payload complete = payload(
                "Song", "Artist", "player", "[00:01]Hello\n");
        LyricInfoContract.Payload nativeOnly = nativeOnly("Song", "Artist");
        String bound = TrackIdentity.buildKey("Song", "Artist");

        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                null, bound, "player", nativeOnly, "player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                nativeOnly, bound, "player", nativeOnly, "player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, "", "player", nativeOnly, "player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly, "player", " ", "Artist"));
    }

    @Test
    public void nativeIdentityCanFallBackToTheAlreadyBoundObservation() {
        LyricInfoContract.Payload complete = payload(
                "Song", "Artist", "player", "[00:01]Hello\n");
        String bound = TrackIdentity.buildKey("Song", "Artist");
        LyricInfoContract.Payload nativeOnly = nativeOnly("", "");

        // UI7 keeps the observed song/artist when the native JSON omits those fields.
        // The packet still cannot establish a binding or match an arbitrary new track.
        assertFalse(LyricInfoTrackMatcher.payloadMatchesTrack(nativeOnly, "Song", "Artist"));
        assertTrue(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly, "player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly, "player", "Next Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, "", "player", nativeOnly, "player", "Song", "Artist"));
    }

    @Test
    public void nativeLyricHeaderCannotOverrideAnEstablishedMetadataBinding() {
        LyricInfoContract.Payload complete = payload(
                "Song", "Artist", "player", "[00:01]Hello\n");
        String bound = TrackIdentity.buildKey("Song", "Artist");
        String display = "[00:00]Long Display Title - Artist\n[00:01]Hello\n";
        LyricInfoContract.Payload nativeOnly = new LyricInfoContract.Payload(
                "", "", "", "", display, "", "", "", "", 0L, "");
        LyricInfoContract.Payload namedNative = new LyricInfoContract.Payload(
                "Song", "Artist", "", "", display, "", "", "", "", 0L, "");

        // Native title banners are display text, whereas the accepted model is already bound.
        assertFalse(LyricInfoTrackMatcher.payloadMatchesTrack(nativeOnly, "Song", "Artist"));
        assertTrue(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", nativeOnly, "player", "Song", "Artist"));
        assertTrue(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", namedNative, "player", "Song", "Artist"));
    }

    @Test
    public void nativeExplicitIdentityConflictsCannotUseMetadataFallback() {
        LyricInfoContract.Payload complete = payload(
                "Song", "Artist", "player", "[00:01]Hello\n");
        String bound = TrackIdentity.buildKey("Song", "Artist");
        LyricInfoContract.Payload differentArtist = nativeOnly("", "Other Artist");
        LyricInfoContract.Payload differentKey = new LyricInfoContract.Payload(
                "", "", "", "", "[00:01]Hello\n", "", "", "",
                TrackIdentity.buildKey("Next Song", "Artist"), 0L, "");

        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", differentArtist, "player", "Song", "Artist"));
        assertFalse(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, bound, "player", differentKey, "player", "Song", "Artist"));
    }

    @Test
    public void officialJsonWithoutSongAndArtistKeepsTheMatchingCompleteModel() throws Exception {
        String display = "[00:00]Long Display Title - Artist\n[00:01]Hello\n";
        LyricInfoContract.Payload nativeOnly = LyricInfoContract.parse(
                new org.json.JSONObject()
                        .put("id", 0).put("songId", "host-id").put("lyricType", 0)
                        .put("lyric", display).put("noLyric", false)
                        .toString());
        LyricInfoContract.Payload complete = new LyricInfoContract.Payload(
                "Song", "Artist", "", "host-id", "[00:01]Hello\n", "[00:01]Hello\n",
                "", "player", TrackIdentity.buildKey("Song", "Artist"), 7L, "player-v5");

        assertTrue(LyricInfoTrackMatcher.canRetainModelForNativeOnlyUpdate(
                complete, TrackIdentity.buildKey("Song", "Artist"), "player",
                nativeOnly, "player", "Song", "Artist"));
    }

    private static LyricInfoContract.Payload nativeOnly(String title, String artist) {
        return new LyricInfoContract.Payload(
                title, artist, "", "", "[00:01]Hello\n", "", "", "", "", 0L, "");
    }

    private static LyricInfoContract.Payload payload(
            String title,
            String artist,
            String provider,
            String rawLyric) {
        return new LyricInfoContract.Payload(
                title,
                artist,
                "",
                "",
                rawLyric,
                rawLyric,
                "",
                provider,
                "",
                0L,
                "");
    }
}
