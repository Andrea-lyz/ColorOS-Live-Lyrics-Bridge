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
                LyricProviderCapabilities.PASSIVE_PARSER);
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
    public void stableTrackCanWaitForUnlabelledLyricThatArrivesAfterMetadata() {
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

        assertTrue(lyric.boundToCurrentTrack);
        assertEquals(alma.key, lyric.document.boundTrackKey);
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
        assertTrue(duplicate.boundToCurrentTrack);
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
        assertTrue(first.boundToCurrentTrack);

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
        assertTrue(previous.boundToCurrentTrack);

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
        assertTrue(first.boundToCurrentTrack);

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
}
