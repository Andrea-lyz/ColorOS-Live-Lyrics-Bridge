package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricSessionReducerTest {
    @Test
    public void passiveLyricWaitsForNextTrackInsteadOfBindingPreviousTrack() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot previous =
                new LyricSessionReducer.TrackSnapshot("Alma Mater", "Bleachers", 210_132L, "");
        reducer.observeTrack(
                previous,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);
        LyricSessionReducer.CaptureUpdate previousLyric = reducer.capture(
                "passive",
                "[00:00]old song",
                "[00:00]old song",
                "",
                1_050L,
                LyricProviderCapabilities.CURRENT_TRACK_SOURCE);
        assertTrue(previousLyric.boundToCurrentTrack);

        LyricSessionReducer.CaptureUpdate capture = reducer.capture(
                "passive",
                "[00:00]new song",
                "[00:00]new song",
                "",
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(capture.boundToCurrentTrack);

        LyricSessionReducer.TrackUpdate relay = reducer.observeTrack(
                previous,
                LyricSessionReducer.ObservationKind.RELAY_METADATA,
                1_150L);
        assertSame(previousLyric.document, relay.document);

        LyricSessionReducer.TrackSnapshot next =
                new LyricSessionReducer.TrackSnapshot(
                        "Bad Blood",
                        "Taylor Swift",
                        211_104L,
                        "");
        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                next,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_250L);

        assertTrue(update.trackChanged);
        assertSame(capture.document, update.document);
        assertEquals(next.key, update.document.boundTrackKey);
    }

    @Test
    public void explicitHintCanBindCurrentTrackImmediately() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot current =
                new LyricSessionReducer.TrackSnapshot("All I Ask", "Adele", 271_800L, "");
        reducer.observeTrack(
                current,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        LyricSessionReducer.CaptureUpdate capture = reducer.capture(
                "tagged",
                "[00:00]hello",
                "[ti:All I Ask]\n[ar:Adele]\n[00:00]hello",
                TrackIdentity.buildKey("All I Ask", "Adele"),
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);

        assertTrue(capture.boundToCurrentTrack);
        assertSame(capture.document, reducer.documentForTrack(current.key, 1_200L));
    }

    @Test
    public void firstTrackCanConsumePendingPassiveLyric() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.CaptureUpdate capture = reducer.capture(
                "passive",
                "[00:00]hello",
                "[00:00]hello",
                "",
                1_000L,
                LyricProviderCapabilities.PASSIVE_PARSER);

        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                new LyricSessionReducer.TrackSnapshot("First Song", "Artist", 10_000L, ""),
                LyricSessionReducer.ObservationKind.RELAY_METADATA,
                1_100L);

        assertSame(capture.document, update.document);
        assertEquals(1L, update.generation);
    }

    @Test
    public void passiveLyricAfterMetadataWaitsForRepeatedStableObservation() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        reducer.observeTrack(
                new LyricSessionReducer.TrackSnapshot(
                        "All You Had To Do Was Stay",
                        "Taylor Swift",
                        193_289L,
                        ""),
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        LyricSessionReducer.TrackSnapshot alma =
                new LyricSessionReducer.TrackSnapshot(
                        "Alma Mater [Explicit]",
                        "Bleachers",
                        210_132L,
                        "");
        LyricSessionReducer.TrackUpdate metadata = reducer.observeTrack(
                alma,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_000L);
        assertNull(metadata.document);

        LyricSessionReducer.CaptureUpdate lyric = reducer.capture(
                "EMBEDDED",
                "[00:00]Baby I want ya",
                "[00:00]Baby I want ya",
                "",
                2_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);

        assertFalse(lyric.boundToCurrentTrack);
        LyricSessionReducer.TrackUpdate repeated = reducer.observeTrack(
                alma,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_200L);
        assertSame(lyric.document, repeated.document);
        assertEquals(alma.key, repeated.document.boundTrackKey);
    }

    @Test
    public void slowPassiveLyricWaitsForRepeatedStableObservation() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot slowTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "Slow Local Lookup",
                        "Artist",
                        210_132L,
                        "");
        reducer.observeTrack(
                slowTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_000L);

        LyricSessionReducer.CaptureUpdate lyric = reducer.capture(
                "EMBEDDED",
                "[00:00]late current line",
                "[00:00]late current line",
                "",
                4_800L,
                LyricProviderCapabilities.PASSIVE_PARSER);

        assertFalse(lyric.boundToCurrentTrack);
        LyricSessionReducer.TrackUpdate repeated = reducer.observeTrack(
                slowTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                4_900L);
        assertSame(lyric.document, repeated.document);
        assertEquals(slowTrack.key, repeated.document.boundTrackKey);
    }

    @Test
    public void advisoryHintCannotBlockNextStableTrackTransaction() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        reducer.observeTrack(
                new LyricSessionReducer.TrackSnapshot(
                        "All You Had To Do Was Stay",
                        "Taylor Swift",
                        193_289L,
                        ""),
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        LyricSessionReducer.CaptureUpdate lyric = reducer.capture(
                "EMBEDDED",
                "[00:00]Baby I want ya",
                "[ti:Lyrics by Jack Antonoff]\n[00:00]Baby I want ya",
                TrackIdentity.buildKey("Lyrics by Jack Antonoff", ""),
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(lyric.boundToCurrentTrack);

        LyricSessionReducer.TrackSnapshot alma =
                new LyricSessionReducer.TrackSnapshot(
                        "Alma Mater [Explicit]",
                        "Bleachers",
                        210_132L,
                        "");
        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                alma,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L);

        assertSame(lyric.document, update.document);
        assertEquals(alma.key, lyric.document.boundTrackKey);
    }

    @Test
    public void specificHintDoesNotBindDifferentNoLyricTrack() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot current =
                new LyricSessionReducer.TrackSnapshot(
                        "22 (Taylor's Version)",
                        "Taylor Swift",
                        230_954L,
                        "");
        reducer.observeTrack(
                current,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        LyricSessionReducer.TrackSnapshot hintedTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "a thousand years",
                        "Christina Perri",
                        285_120L,
                        "");
        LyricSessionReducer.CaptureUpdate lyric = reducer.capture(
                "EMBEDDED",
                "[00:00]Heart beats fast",
                "[00:00]a thousand years - Christina Perri\n[00:01]Heart beats fast",
                hintedTrack.key,
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(lyric.boundToCurrentTrack);

        LyricSessionReducer.TrackSnapshot noLyricTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "Strangers By Nature",
                        "Adele",
                        182_163L,
                        "");
        LyricSessionReducer.TrackUpdate noLyricUpdate = reducer.observeTrack(
                noLyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L);
        assertNull(noLyricUpdate.document);
        assertNull(reducer.documentForTrack(noLyricTrack.key, 1_200L));

        LyricSessionReducer.TrackUpdate hintedUpdate = reducer.observeTrack(
                hintedTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_300L);
        assertSame(lyric.document, hintedUpdate.document);
        assertEquals(hintedTrack.key, hintedUpdate.document.boundTrackKey);
    }

    @Test
    public void advisoryHintDoesNotExposeUnboundDocumentThroughCache() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        reducer.capture(
                "EMBEDDED",
                "[00:00]Baby I want ya",
                "[ti:Wrong Song]\n[00:00]Baby I want ya",
                TrackIdentity.buildKey("Wrong Song", ""),
                1_000L,
                LyricProviderCapabilities.PASSIVE_PARSER);

        assertNull(reducer.documentForTrack(
                TrackIdentity.buildKey("Wrong Song", ""),
                1_100L));
    }

    @Test
    public void duplicateConstructorCaptureKeepsExistingBinding() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot alma =
                new LyricSessionReducer.TrackSnapshot(
                        "Alma Mater [Explicit]",
                        "Bleachers",
                        210_132L,
                        "");
        reducer.observeTrack(
                alma,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        LyricSessionReducer.CaptureUpdate first = reducer.capture(
                "EMBEDDED",
                "[00:00]Baby I want ya",
                "[00:00]Baby I want ya",
                "",
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        LyricSessionReducer.CaptureUpdate duplicate = reducer.capture(
                "EMBEDDED",
                "[00:00]Baby I want ya",
                "[00:00]Baby I want ya",
                "",
                1_101L,
                LyricProviderCapabilities.PASSIVE_PARSER);

        assertSame(first.document, duplicate.document);
        assertFalse(duplicate.boundToCurrentTrack);
        assertSame(first.document, reducer.observeTrack(
                alma,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_150L).document);
        assertSame(first.document, reducer.documentForTrack(alma.key, 1_200L));
    }

    @Test
    public void unhintedReplayAfterCoalesceWindowKeepsCurrentBinding() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot firstTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "World Song A",
                        "Artist",
                        244_278L,
                        "");
        reducer.observeTrack(
                firstTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        LyricSessionReducer.CaptureUpdate first = reducer.capture(
                "EMBEDDED",
                "[00:00]shared line",
                "[00:00]shared line",
                "",
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        LyricSessionReducer.CaptureUpdate constructorDuplicate = reducer.capture(
                "EMBEDDED",
                "[00:00]shared line",
                "[00:00]shared line",
                "",
                1_101L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertSame(first.document, constructorDuplicate.document);
        assertSame(first.document, reducer.observeTrack(
                firstTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L).document);

        LyricSessionReducer.CaptureUpdate replay = reducer.capture(
                "EMBEDDED",
                "[00:00]shared line",
                "[00:00]shared line",
                "",
                1_600L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertSame(first.document, replay.document);
        assertTrue(replay.boundToCurrentTrack);

        LyricSessionReducer.TrackSnapshot nextTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "World Song B",
                        "Artist",
                        244_278L,
                        "");
        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                nextTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_700L);

        assertNull(update.document);
        assertNull(reducer.documentForTrack(nextTrack.key, 1_800L));
        assertSame(first.document, reducer.documentForTrack(firstTrack.key, 1_800L));
    }

    @Test
    public void knownUnhintedReplayDoesNotBindDifferentNoLyricTrack() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot lyricTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "World Song C",
                        "Artist",
                        244_278L,
                        "");
        reducer.observeTrack(
                lyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);
        LyricSessionReducer.CaptureUpdate first = reducer.capture(
                "EMBEDDED",
                "[00:00]known c line",
                "[00:00]known c line",
                "",
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(first.boundToCurrentTrack);
        assertSame(first.document, reducer.observeTrack(
                lyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L).document);

        LyricSessionReducer.TrackSnapshot previousTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "World Song A",
                        "Artist",
                        244_278L,
                        "");
        reducer.observeTrack(
                previousTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_500L);
        LyricSessionReducer.CaptureUpdate previous = reducer.capture(
                "EMBEDDED",
                "[00:00]known a line",
                "[00:00]known a line",
                "",
                1_600L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(previous.boundToCurrentTrack);
        assertSame(previous.document, reducer.observeTrack(
                previousTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_700L).document);

        reducer.observeTrack(
                previousTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_000L);
        LyricSessionReducer.CaptureUpdate replay = reducer.capture(
                "EMBEDDED",
                "[00:00]known c line",
                "[00:00]known c line",
                "",
                2_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(replay.boundToCurrentTrack);

        LyricSessionReducer.TrackSnapshot noLyricTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "World Song B",
                        "Artist",
                        244_278L,
                        "");
        LyricSessionReducer.TrackUpdate noLyricUpdate = reducer.observeTrack(
                noLyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_200L);
        assertNull(noLyricUpdate.document);
        assertNull(reducer.documentForTrack(noLyricTrack.key, 2_200L));

        LyricSessionReducer.TrackUpdate lyricUpdate = reducer.observeTrack(
                lyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_300L);
        assertSame(replay.document, lyricUpdate.document);
        assertEquals(lyricTrack.key, lyricUpdate.document.boundTrackKey);
    }

    @Test
    public void noLyricResultStopsCurrentTrackFromConsumingNextUnhintedLyric() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot noLyricTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "Sensory Overload",
                        "Artist A",
                        180_000L,
                        "");
        reducer.observeTrack(
                noLyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_000L);
        reducer.markCurrentTrackHasNoDocument(2_050L);

        LyricSessionReducer.CaptureUpdate nextLyric = reducer.capture(
                "EMBEDDED",
                "[00:00]next track line",
                "[00:00]next track line",
                "",
                2_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);

        assertFalse(nextLyric.boundToCurrentTrack);
        assertNull(reducer.documentForTrack(noLyricTrack.key, 2_100L));

        LyricSessionReducer.TrackSnapshot nextTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "Next Track",
                        "Artist B",
                        210_000L,
                        "");
        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                nextTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_200L);

        assertSame(nextLyric.document, update.document);
        assertEquals(nextTrack.key, update.document.boundTrackKey);
    }

    @Test
    public void noLyricResultBeforeMetadataInvalidatesCachedTargetDocument() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot noLyricTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "Strangers By Nature",
                        "Adele",
                        182_163L,
                        "");
        reducer.observeTrack(
                noLyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);
        LyricSessionReducer.CaptureUpdate stale = reducer.capture(
                "EMBEDDED",
                "[00:00]stale lyric from another song",
                "[00:00]stale lyric from another song",
                "",
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(stale.boundToCurrentTrack);
        assertSame(stale.document, reducer.observeTrack(
                noLyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L).document);

        LyricSessionReducer.TrackSnapshot previous =
                new LyricSessionReducer.TrackSnapshot(
                        "My Little Love",
                        "Adele",
                        389_107L,
                        "");
        reducer.observeTrack(
                previous,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_000L);

        reducer.recordPendingNoDocument(2_500L);
        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                noLyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_550L);

        assertTrue(update.trackChanged);
        assertNull(update.document);
        assertNull(reducer.documentForTrack(noLyricTrack.key, 2_600L));
    }

    @Test
    public void consecutiveNoLyricResultsDoNotShiftFollowingLyric() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot first =
                new LyricSessionReducer.TrackSnapshot("Song A", "Artist", 180_000L, "");
        LyricSessionReducer.TrackSnapshot second =
                new LyricSessionReducer.TrackSnapshot("Song B", "Artist", 180_000L, "");
        LyricSessionReducer.TrackSnapshot third =
                new LyricSessionReducer.TrackSnapshot("Song C", "Artist", 180_000L, "");
        LyricSessionReducer.TrackSnapshot fourth =
                new LyricSessionReducer.TrackSnapshot("Song D", "Artist", 180_000L, "");
        reducer.observeTrack(
                first,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        reducer.recordPendingNoDocument(1_100L);
        assertNull(reducer.observeTrack(
                second,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_150L).document);

        reducer.recordPendingNoDocument(1_200L);
        assertNull(reducer.observeTrack(
                third,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_250L).document);

        LyricSessionReducer.CaptureUpdate lyric = reducer.capture(
                "EMBEDDED",
                "[00:00]song d line",
                "[00:00]song d line",
                "",
                1_300L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(lyric.boundToCurrentTrack);

        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                fourth,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_350L);
        assertSame(lyric.document, update.document);
        assertEquals(fourth.key, update.document.boundTrackKey);
    }

    @Test
    public void expiredCurrentTrackWaitDoesNotConsumeNextUnhintedLyric() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot noLyricTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "Instrumental Gap",
                        "Artist A",
                        180_000L,
                        "");
        reducer.observeTrack(
                noLyricTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                2_000L);

        LyricSessionReducer.CaptureUpdate nextLyric = reducer.capture(
                "EMBEDDED",
                "[00:00]preloaded next line",
                "[00:00]preloaded next line",
                "",
                8_000L,
                LyricProviderCapabilities.PASSIVE_PARSER);

        assertFalse(nextLyric.boundToCurrentTrack);
        assertNull(reducer.documentForTrack(noLyricTrack.key, 8_000L));

        LyricSessionReducer.TrackSnapshot nextTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "Song After Gap",
                        "Artist B",
                        210_000L,
                        "");
        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                nextTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                8_100L);

        assertSame(nextLyric.document, update.document);
        assertEquals(nextTrack.key, update.document.boundTrackKey);
    }

    @Test
    public void taggedIdenticalLyricAfterCoalesceWindowCanBindNextTrack() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot firstTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "World Song A",
                        "Artist",
                        244_278L,
                        "");
        reducer.observeTrack(
                firstTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        LyricSessionReducer.CaptureUpdate first = reducer.capture(
                "EMBEDDED",
                "[00:00]shared line",
                "[00:00]shared line",
                "",
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(first.boundToCurrentTrack);
        assertSame(first.document, reducer.observeTrack(
                firstTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L).document);

        LyricSessionReducer.TrackSnapshot nextTrack =
                new LyricSessionReducer.TrackSnapshot(
                        "World Song B",
                        "Artist",
                        244_278L,
                        "");
        LyricSessionReducer.CaptureUpdate nextLyric = reducer.capture(
                "EMBEDDED",
                "[00:00]shared line",
                "[ti:World Song B]\n[ar:Artist]\n[00:00]shared line",
                nextTrack.key,
                1_600L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertNotSame(first.document, nextLyric.document);
        assertFalse(nextLyric.boundToCurrentTrack);

        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                nextTrack,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_700L);

        assertSame(nextLyric.document, update.document);
        assertEquals(nextTrack.key, update.document.boundTrackKey);
    }

    @Test
    public void featuredTwoStepHintBindsBeforeUnhintedTwentyTwoLyricArrives() {
        LyricSessionReducer reducer = new LyricSessionReducer(300_000L, 24);
        LyricSessionReducer.TrackSnapshot previous =
                new LyricSessionReducer.TrackSnapshot(
                        "Strangers By Nature",
                        "Adele",
                        182_163L,
                        "");
        reducer.observeTrack(
                previous,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_000L);

        String twoStepHint = TrackIdentity.buildLrcHintKey(
                "2step (feat. Lil Baby)",
                "Ed Sheeran,Lil Baby");
        LyricSessionReducer.CaptureUpdate twoStepLyric = reducer.capture(
                "EMBEDDED",
                "[00:10.39]I had a bad week",
                "[ti:2step (feat. Lil Baby)]\n"
                        + "[ar:Ed Sheeran,Lil Baby]\n"
                        + "[00:10.39]I had a bad week",
                twoStepHint,
                1_100L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(twoStepLyric.boundToCurrentTrack);

        LyricSessionReducer.TrackSnapshot twoStep =
                new LyricSessionReducer.TrackSnapshot(
                        "2step",
                        "Ed Sheeran/Lil Baby",
                        163_450L,
                        "");
        LyricSessionReducer.TrackUpdate twoStepUpdate = reducer.observeTrack(
                twoStep,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L);
        assertSame(twoStepLyric.document, twoStepUpdate.document);
        assertEquals(twoStep.key, twoStepUpdate.document.boundTrackKey);

        LyricSessionReducer.CaptureUpdate twentyTwoLyric = reducer.capture(
                "EMBEDDED",
                "[00:00.00]It feels like a perfect night",
                "[00:00.00]It feels like a perfect night",
                "",
                1_300L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        assertFalse(twentyTwoLyric.boundToCurrentTrack);
        assertSame(twoStepLyric.document, reducer.documentForTrack(twoStep.key, 1_350L));

        LyricSessionReducer.TrackSnapshot twentyTwo =
                new LyricSessionReducer.TrackSnapshot(
                        "22 (Taylor's Version)",
                        "Taylor Swift",
                        230_954L,
                        "");
        LyricSessionReducer.TrackUpdate twentyTwoUpdate = reducer.observeTrack(
                twentyTwo,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_400L);
        assertSame(twentyTwoLyric.document, twentyTwoUpdate.document);
        assertEquals(twentyTwo.key, twentyTwoUpdate.document.boundTrackKey);
    }
}
