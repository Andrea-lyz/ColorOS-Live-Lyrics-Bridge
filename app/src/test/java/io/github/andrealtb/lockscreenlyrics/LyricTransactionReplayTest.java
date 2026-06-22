package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricTransactionReplayTest {
    @Test
    public void requestIdKeepsLateResultBoundToOriginalTrack() {
        LyricSessionReducer reducer = reducer();
        LyricSessionReducer.TrackSnapshot first = track("First", "Artist", "media-1");
        LyricSessionReducer.TrackSnapshot second = track("Second", "Artist", "media-2");
        reducer.observeTrack(first, LyricSessionReducer.ObservationKind.STABLE_METADATA, 1_000L);
        reducer.acceptSourceEvent(LyricSourceEvent.lookupStarted(
                "generic-player",
                "request-1",
                "",
                "",
                "",
                1_050L,
                LyricProviderCapabilities.PASSIVE_PARSER));

        reducer.observeTrack(second, LyricSessionReducer.ObservationKind.STABLE_METADATA, 1_100L);
        LyricSessionReducer.EventUpdate late = reducer.acceptSourceEvent(
                resolved("generic-player", "request-1", "", "[00:01]first", 1_200L));

        assertFalse(late.boundToCurrentTrack);
        assertNull(reducer.documentForTrack(second.key, 1_250L));

        LyricSessionReducer.TrackUpdate replay =
                reducer.observeTrack(
                        first,
                        LyricSessionReducer.ObservationKind.STABLE_METADATA,
                        1_300L);
        assertSame(late.document, replay.document);
        assertEquals(first.key, replay.document.boundTrackKey);
    }

    @Test
    public void strongIdentityAllowsMultipleOutOfOrderPreloads() {
        LyricSessionReducer reducer = reducer();
        LyricSessionReducer.TrackSnapshot first = track("First", "Artist", "media-1");
        LyricSessionReducer.TrackSnapshot second = track("Second", "Artist", "media-2");
        LyricSessionReducer.TrackSnapshot third = track("Third", "Artist", "media-3");
        reducer.observeTrack(first, LyricSessionReducer.ObservationKind.STABLE_METADATA, 1_000L);

        LyricSessionReducer.EventUpdate thirdLyric = reducer.acceptSourceEvent(
                resolved("generic-player", "", third.key, "[00:03]third", 1_100L));
        LyricSessionReducer.EventUpdate secondLyric = reducer.acceptSourceEvent(
                resolved("generic-player", "", second.key, "[00:02]second", 1_200L));

        assertSame(secondLyric.document, reducer.observeTrack(
                second,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_300L).document);
        assertSame(thirdLyric.document, reducer.observeTrack(
                third,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_400L).document);
    }

    @Test
    public void sourceMissLeavesCurrentTransactionOpen() {
        LyricSessionReducer reducer = reducer();
        LyricSessionReducer.TrackSnapshot current = track("Current", "Artist", "media-1");
        reducer.observeTrack(current, LyricSessionReducer.ObservationKind.STABLE_METADATA, 1_000L);

        LyricSessionReducer.EventUpdate miss = reducer.acceptSourceEvent(
                LyricSourceEvent.terminal(
                        LyricSourceEvent.Outcome.SOURCE_MISS,
                        "embedded-provider",
                        "",
                        "",
                        "",
                        "",
                        "",
                        1_100L,
                        LyricProviderCapabilities.PASSIVE_PARSER));
        assertFalse(miss.queued);

        LyricSessionReducer.EventUpdate resolved = reducer.acceptSourceEvent(
                LyricSourceEvent.resolved(
                        "external-provider",
                        "",
                        "",
                        "",
                        "",
                        "[00:01]current",
                        "[00:01]current",
                        1_200L,
                        LyricProviderCapabilities.CURRENT_TRACK_SOURCE));
        assertTrue(resolved.boundToCurrentTrack);
        assertSame(resolved.document, reducer.documentForTrack(current.key, 1_250L));
    }

    @Test
    public void startupPlaceholderCannotPreemptFollowingRealLyric() {
        LyricSessionReducer reducer = reducer();
        LyricSessionReducer.EventUpdate placeholder = reducer.acceptSourceEvent(
                LyricSourceEvent.terminal(
                        LyricResultClassifier.classifyEmptyResult(
                                "UNKNOWN"),
                        "UNKNOWN",
                        "",
                        "",
                        "",
                        "",
                        "UNKNOWN",
                        1_000L,
                        LyricProviderCapabilities.PASSIVE_PARSER));
        assertFalse(placeholder.queued);

        LyricSessionReducer.EventUpdate lyric = reducer.acceptSourceEvent(
                resolved(
                        "EMBEDDED",
                        "",
                        "",
                        "[00:01]real next track lyric",
                        1_100L));
        LyricSessionReducer.TrackSnapshot next =
                track("Next", "Artist", "media-next");
        LyricSessionReducer.TrackUpdate update = reducer.observeTrack(
                next,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L);

        assertSame(lyric.document, update.document);
        assertNull(update.terminalOutcome);
        assertEquals(next.key, update.document.boundTrackKey);
    }

    @Test
    public void duplicateTerminalConstructorsAreConsumedOnlyOnce() {
        LyricSessionReducer reducer = reducer();
        LyricSourceEvent firstNotFound = LyricSourceEvent.terminal(
                LyricSourceEvent.Outcome.NO_LYRIC,
                "NOT_FOUND",
                "",
                "",
                "",
                "",
                "",
                1_000L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        LyricSourceEvent duplicateNotFound = LyricSourceEvent.terminal(
                LyricSourceEvent.Outcome.NO_LYRIC,
                "NOT_FOUND",
                "",
                "",
                "",
                "",
                "",
                1_001L,
                LyricProviderCapabilities.PASSIVE_PARSER);
        reducer.acceptSourceEvent(firstNotFound);
        reducer.acceptSourceEvent(duplicateNotFound);

        LyricSessionReducer.TrackSnapshot noLyric =
                track("Instrumental", "Artist", "media-1");
        LyricSessionReducer.TrackUpdate firstUpdate = reducer.observeTrack(
                noLyric,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_100L);
        assertEquals(LyricSourceEvent.Outcome.NO_LYRIC, firstUpdate.terminalOutcome);

        LyricSessionReducer.EventUpdate realLyric = reducer.acceptSourceEvent(
                resolved("EMBEDDED", "", "", "[00:01]next song", 1_200L));
        LyricSessionReducer.TrackSnapshot next =
                track("Next", "Artist", "media-2");
        LyricSessionReducer.TrackUpdate nextUpdate = reducer.observeTrack(
                next,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_300L);
        assertSame(realLyric.document, nextUpdate.document);
        assertNull(nextUpdate.terminalOutcome);
    }

    @Test
    public void passiveResultWaitsForNextStableObservationInsteadOfCurrentTrack() {
        LyricSessionReducer reducer = reducer();
        LyricSessionReducer.TrackSnapshot previous = track("Previous", "Artist", "media-1");
        LyricSessionReducer.TrackSnapshot next = track("Next", "Artist", "media-2");
        reducer.observeTrack(previous, LyricSessionReducer.ObservationKind.STABLE_METADATA, 1_000L);

        LyricSessionReducer.EventUpdate result = reducer.acceptSourceEvent(
                resolved("passive-parser", "", "", "[00:01]next", 1_100L));

        assertFalse(result.boundToCurrentTrack);
        assertNull(reducer.documentForTrack(previous.key, 1_150L));
        assertSame(result.document, reducer.observeTrack(
                next,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L).document);
    }

    @Test
    public void repeatedStableMetadataCanCompletePassiveTransaction() {
        LyricSessionReducer reducer = reducer();
        LyricSessionReducer.TrackSnapshot current = track("Current", "Artist", "media-1");
        reducer.observeTrack(current, LyricSessionReducer.ObservationKind.STABLE_METADATA, 1_000L);

        LyricSessionReducer.EventUpdate result = reducer.acceptSourceEvent(
                resolved("passive-parser", "", "", "[00:01]current", 1_100L));
        assertFalse(result.boundToCurrentTrack);

        LyricSessionReducer.TrackUpdate repeated = reducer.observeTrack(
                current,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L);
        assertSame(result.document, repeated.document);
        assertEquals(current.key, result.document.boundTrackKey);
    }

    @Test
    public void exactIdentityDropsOlderWeakEventsAsSynchronizationBarrier() {
        LyricSessionReducer reducer = reducer();
        LyricSessionReducer.TrackSnapshot previous = track("Previous", "Artist", "media-1");
        LyricSessionReducer.TrackSnapshot boulevard =
                track("Boulevard of Broken Dreams（碎梦大道）", "Green Day", "media-2");
        LyricSessionReducer.TrackSnapshot instrumental =
                track("Instrumental", "Artist", "media-3");
        reducer.observeTrack(previous, LyricSessionReducer.ObservationKind.STABLE_METADATA, 1_000L);

        reducer.acceptSourceEvent(
                resolved("passive-parser", "", "", "[00:01]stale weak lyric", 1_100L));
        LyricSessionReducer.EventUpdate exact = reducer.acceptSourceEvent(
                resolved(
                        "passive-parser",
                        "",
                        TrackIdentity.buildKey("Boulevard of Broken Dreams", "Green Day"),
                        "[00:01]I walk a lonely road",
                        1_200L));

        assertSame(exact.document, reducer.observeTrack(
                boulevard,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_300L).document);
        assertNull(reducer.observeTrack(
                instrumental,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_400L).document);
    }

    @Test
    public void parseFailureEndsOnlyItsExplicitTargetTransaction() {
        LyricSessionReducer reducer = reducer();
        LyricSessionReducer.TrackSnapshot first = track("First", "Artist", "media-1");
        LyricSessionReducer.TrackSnapshot broken = track("Broken", "Artist", "media-2");
        LyricSessionReducer.TrackSnapshot next = track("Next", "Artist", "media-3");
        reducer.observeTrack(first, LyricSessionReducer.ObservationKind.STABLE_METADATA, 1_000L);

        reducer.acceptSourceEvent(LyricSourceEvent.terminal(
                LyricSourceEvent.Outcome.PARSE_FAILED,
                "generic-player",
                "",
                "media-2",
                "",
                broken.key,
                "[ti:Broken]\nmalformed",
                1_100L,
                LyricProviderCapabilities.ACTIVE_INTEGRATION));
        LyricSessionReducer.TrackUpdate brokenUpdate = reducer.observeTrack(
                broken,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_200L);
        assertEquals(LyricSourceEvent.Outcome.PARSE_FAILED, brokenUpdate.terminalOutcome);
        assertNull(brokenUpdate.document);

        LyricSessionReducer.EventUpdate nextLyric = reducer.acceptSourceEvent(
                resolved("generic-player", "", "", "[00:01]next", 1_300L));
        assertFalse(nextLyric.boundToCurrentTrack);
        assertSame(nextLyric.document, reducer.observeTrack(
                next,
                LyricSessionReducer.ObservationKind.STABLE_METADATA,
                1_400L).document);
    }

    private static LyricSessionReducer reducer() {
        return new LyricSessionReducer(300_000L, 24);
    }

    private static LyricSessionReducer.TrackSnapshot track(
            String title,
            String artist,
            String mediaId) {
        return new LyricSessionReducer.TrackSnapshot(title, artist, 180_000L, mediaId);
    }

    private static LyricSourceEvent resolved(
            String source,
            String requestId,
            String trackHintKey,
            String lyric,
            long timestamp) {
        return LyricSourceEvent.resolved(
                source,
                requestId,
                "",
                "",
                trackHintKey,
                lyric,
                lyric,
                timestamp,
                LyricProviderCapabilities.PASSIVE_PARSER);
    }
}
