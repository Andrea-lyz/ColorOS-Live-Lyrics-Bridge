package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Player-independent transaction reducer for track observations and lyric source events.
 *
 * <p>Strong identities are matched directly. Legacy callbacks without request or media identity
 * are placed in a bounded ordered queue and only use timing as a final fallback. This keeps
 * player-specific callback shapes outside the association state machine.</p>
 */
final class LyricSessionReducer {
    private static final long DUPLICATE_EVENT_COALESCE_WINDOW_MS = 300L;
    private static final long MAX_PENDING_EVENT_AGE_MS = 30_000L;

    enum ObservationKind {
        STABLE_METADATA,
        RELAY_METADATA
    }

    static final class TrackSnapshot {
        final String title;
        final String artist;
        final long durationMillis;
        final String songId;
        final String mediaUri;
        final String key;

        TrackSnapshot(String title, String artist, long durationMillis, String songId) {
            this(title, artist, durationMillis, songId, "");
        }

        TrackSnapshot(
                String title,
                String artist,
                long durationMillis,
                String songId,
                String mediaUri) {
            this.title = nullToEmpty(title);
            this.artist = nullToEmpty(artist);
            this.durationMillis = durationMillis;
            this.songId = nullToEmpty(songId);
            this.mediaUri = LyricSourceEvent.normalizeUri(mediaUri);
            this.key = TrackIdentity.buildKey(this.title, this.artist);
        }
    }

    static final class LyricDocument {
        final String source;
        final String requestId;
        final String mediaId;
        final String mediaUri;
        final String lyric;
        final String rawLyric;
        final String trackHintKey;
        final long capturedAtMillis;
        final LyricProviderCapabilities capabilities;
        final long baselineEpoch;
        String boundTrackKey = "";

        LyricDocument(
                PendingEvent event) {
            this.source = event.source;
            this.requestId = event.requestId;
            this.mediaId = event.mediaId;
            this.mediaUri = event.mediaUri;
            this.lyric = event.lyric;
            this.rawLyric = event.rawLyric;
            this.trackHintKey = event.trackHintKey;
            this.capturedAtMillis = event.occurredAtMillis;
            this.capabilities = event.capabilities;
            this.baselineEpoch = event.baselineEpoch;
        }
    }

    static final class CaptureUpdate {
        final LyricDocument document;
        final boolean boundToCurrentTrack;

        CaptureUpdate(LyricDocument document, boolean boundToCurrentTrack) {
            this.document = document;
            this.boundToCurrentTrack = boundToCurrentTrack;
        }
    }

    static final class EventUpdate {
        final LyricSourceEvent.Outcome outcome;
        final LyricDocument document;
        final boolean boundToCurrentTrack;
        final boolean queued;

        EventUpdate(
                LyricSourceEvent.Outcome outcome,
                LyricDocument document,
                boolean boundToCurrentTrack,
                boolean queued) {
            this.outcome = outcome;
            this.document = document;
            this.boundToCurrentTrack = boundToCurrentTrack;
            this.queued = queued;
        }
    }

    static final class TrackUpdate {
        final TrackSnapshot track;
        final LyricDocument document;
        final long generation;
        final boolean trackChanged;
        final boolean noDocumentConfirmed;
        final LyricSourceEvent.Outcome terminalOutcome;

        TrackUpdate(
                TrackSnapshot track,
                LyricDocument document,
                long generation,
                boolean trackChanged,
                LyricSourceEvent.Outcome terminalOutcome) {
            this.track = track;
            this.document = document;
            this.generation = generation;
            this.trackChanged = trackChanged;
            this.terminalOutcome = terminalOutcome;
            this.noDocumentConfirmed = terminalOutcome == LyricSourceEvent.Outcome.NO_LYRIC
                    || terminalOutcome == LyricSourceEvent.Outcome.PARSE_FAILED;
        }
    }

    private static final class RequestContext {
        final String mediaId;
        final String mediaUri;
        final String trackHintKey;
        final long baselineEpoch;
        final long startedAtMillis;

        RequestContext(
                String mediaId,
                String mediaUri,
                String trackHintKey,
                long baselineEpoch,
                long startedAtMillis) {
            this.mediaId = mediaId;
            this.mediaUri = mediaUri;
            this.trackHintKey = trackHintKey;
            this.baselineEpoch = baselineEpoch;
            this.startedAtMillis = startedAtMillis;
        }
    }

    private static final class PendingEvent {
        final LyricSourceEvent.Outcome outcome;
        final String source;
        final String requestId;
        final String mediaId;
        final String mediaUri;
        final String trackHintKey;
        final String lyric;
        final String rawLyric;
        final long occurredAtMillis;
        final LyricProviderCapabilities capabilities;
        final long baselineEpoch;
        LyricDocument document;

        PendingEvent(
                LyricSourceEvent event,
                RequestContext request,
                long currentEpoch) {
            this.outcome = event.outcome;
            this.source = event.source;
            this.requestId = event.requestId;
            this.mediaId = firstNonEmpty(
                    event.mediaId,
                    request == null ? "" : request.mediaId);
            this.mediaUri = firstNonEmpty(
                    event.mediaUri,
                    request == null ? "" : request.mediaUri);
            this.trackHintKey = firstNonEmpty(
                    event.trackHintKey,
                    request == null ? "" : request.trackHintKey);
            this.lyric = event.lyric;
            this.rawLyric = event.rawLyric;
            this.occurredAtMillis = event.occurredAtMillis;
            this.capabilities = event.capabilities;
            this.baselineEpoch = request == null ? currentEpoch : request.baselineEpoch;
        }

        boolean isResolved() {
            return outcome == LyricSourceEvent.Outcome.RESOLVED;
        }

        boolean isTerminal() {
            return outcome == LyricSourceEvent.Outcome.NO_LYRIC
                    || outcome == LyricSourceEvent.Outcome.PARSE_FAILED;
        }
    }

    private final long documentMaxAgeMillis;
    private final int cacheMaxEntries;
    private final int pendingMaxEntries;
    private final LinkedHashMap<String, LyricDocument> documentsByTrack =
            new LinkedHashMap<>(16, 0.75f, true);
    private final ArrayDeque<PendingEvent> pendingEvents = new ArrayDeque<>();
    private final LinkedHashMap<String, RequestContext> requestsById =
            new LinkedHashMap<>(16, 0.75f, true);

    private TrackSnapshot currentTrack;
    private LyricDocument mostRecentDocument;
    private long generation;

    LyricSessionReducer(long documentMaxAgeMillis, int cacheMaxEntries) {
        this.documentMaxAgeMillis = documentMaxAgeMillis;
        this.cacheMaxEntries = Math.max(1, cacheMaxEntries);
        this.pendingMaxEntries = Math.max(8, this.cacheMaxEntries * 2);
    }

    synchronized CaptureUpdate capture(
            String source,
            String lyric,
            String rawLyric,
            String trackHintKey,
            long capturedAtMillis,
            LyricProviderCapabilities capabilities) {
        EventUpdate update = acceptSourceEvent(LyricSourceEvent.resolved(
                source,
                "",
                "",
                "",
                trackHintKey,
                lyric,
                rawLyric,
                capturedAtMillis,
                capabilities));
        return new CaptureUpdate(update.document, update.boundToCurrentTrack);
    }

    synchronized EventUpdate acceptSourceEvent(LyricSourceEvent event) {
        if (event == null) {
            return new EventUpdate(
                    LyricSourceEvent.Outcome.SOURCE_MISS,
                    null,
                    false,
                    false);
        }
        pruneExpired(event.occurredAtMillis);
        if (event.outcome == LyricSourceEvent.Outcome.LOOKUP_STARTED) {
            registerRequest(event);
            return new EventUpdate(event.outcome, null, false, false);
        }
        if (event.outcome == LyricSourceEvent.Outcome.SOURCE_MISS) {
            return new EventUpdate(event.outcome, null, false, false);
        }

        RequestContext request = event.requestId.isEmpty()
                ? null
                : requestsById.get(event.requestId);
        PendingEvent transaction = new PendingEvent(
                event,
                request,
                generation);

        if (transaction.isResolved()) {
            LyricDocument duplicate = findEquivalentCapture(transaction);
            if (duplicate != null) {
                finishRequest(event.requestId);
                mostRecentDocument = duplicate;
                return new EventUpdate(
                        event.outcome,
                        duplicate,
                        currentTrack != null
                                && currentTrack.key.equals(duplicate.boundTrackKey),
                        duplicate.boundTrackKey.isEmpty());
            }
            transaction.document = new LyricDocument(transaction);
            LyricDocument replayedCurrent = findCurrentTrackReplay(transaction.document);
            if (replayedCurrent != null) {
                finishRequest(event.requestId);
                mostRecentDocument = replayedCurrent;
                return new EventUpdate(event.outcome, replayedCurrent, true, false);
            }
            mostRecentDocument = transaction.document;
        } else if (findEquivalentTerminal(transaction)) {
            finishRequest(event.requestId);
            return new EventUpdate(event.outcome, null, false, true);
        }

        if (canApplyToCurrentTrack(transaction)) {
            if (matchesTrack(transaction, currentTrack)) {
                discardUnidentifiedEventsAtOrBefore(transaction.occurredAtMillis);
            }
            applyToTrack(transaction, currentTrack);
            finishRequest(event.requestId);
            return new EventUpdate(
                    event.outcome,
                    transaction.document,
                    transaction.isResolved(),
                    false);
        }

        enqueue(transaction);
        finishRequest(event.requestId);
        return new EventUpdate(event.outcome, transaction.document, false, true);
    }

    synchronized TrackUpdate observeTrack(
            TrackSnapshot nextTrack,
            ObservationKind observationKind,
            long observedAtMillis) {
        pruneExpired(observedAtMillis);
        if (nextTrack == null || nextTrack.key.isEmpty()) {
            return new TrackUpdate(currentTrack, null, generation, false, null);
        }

        boolean firstTrackObservation = currentTrack == null;
        boolean trackChanged = firstTrackObservation || !currentTrack.key.equals(nextTrack.key);
        currentTrack = nextTrack;
        if (trackChanged) {
            generation++;
        }

        PendingEvent pending = selectPendingEvent(
                nextTrack,
                observationKind,
                firstTrackObservation,
                observedAtMillis);
        LyricDocument selected = null;
        LyricSourceEvent.Outcome terminalOutcome = null;
        if (pending != null) {
            if (matchesTrack(pending, nextTrack)) {
                discardUnidentifiedEventsBefore(pending);
            }
            pendingEvents.remove(pending);
            if (pending.isResolved()) {
                bind(pending.document, nextTrack.key);
                selected = pending.document;
            } else if (pending.isTerminal()) {
                invalidateTrack(nextTrack.key);
                terminalOutcome = pending.outcome;
            }
        } else {
            selected = findDocumentForTrack(nextTrack.key);
        }

        return new TrackUpdate(
                nextTrack,
                selected,
                generation,
                trackChanged,
                terminalOutcome);
    }

    synchronized void recordPendingNoDocument(long observedAtMillis) {
        acceptSourceEvent(LyricSourceEvent.terminal(
                LyricSourceEvent.Outcome.NO_LYRIC,
                "legacy-no-lyric",
                "",
                "",
                "",
                "",
                "",
                observedAtMillis,
                LyricProviderCapabilities.PASSIVE_PARSER));
    }

    synchronized void markCurrentTrackHasNoDocument(long observedAtMillis) {
        acceptSourceEvent(LyricSourceEvent.terminal(
                LyricSourceEvent.Outcome.NO_LYRIC,
                "explicit-current-no-lyric",
                "",
                "",
                "",
                "",
                "",
                observedAtMillis,
                LyricProviderCapabilities.CURRENT_TRACK_SOURCE));
    }

    synchronized TrackSnapshot currentTrack() {
        return currentTrack;
    }

    synchronized long generation() {
        return generation;
    }

    synchronized LyricDocument recentDocument(long nowMillis) {
        pruneExpired(nowMillis);
        return isFresh(mostRecentDocument, nowMillis) ? mostRecentDocument : null;
    }

    synchronized LyricDocument documentForTrack(String trackKey, long nowMillis) {
        pruneExpired(nowMillis);
        return findDocumentForTrack(trackKey);
    }

    private void registerRequest(LyricSourceEvent event) {
        if (event.requestId.isEmpty()) {
            return;
        }
        String trackHintKey = event.trackHintKey;
        String mediaId = event.mediaId;
        String mediaUri = event.mediaUri;
        if (currentTrack != null) {
            if (trackHintKey.isEmpty()) {
                trackHintKey = currentTrack.key;
            }
            if (mediaId.isEmpty()) {
                mediaId = currentTrack.songId;
            }
            if (mediaUri.isEmpty()) {
                mediaUri = currentTrack.mediaUri;
            }
        }
        requestsById.put(event.requestId, new RequestContext(
                mediaId,
                mediaUri,
                trackHintKey,
                generation,
                event.occurredAtMillis));
        trimRequests();
    }

    private void finishRequest(String requestId) {
        if (!nullToEmpty(requestId).isEmpty()) {
            requestsById.remove(requestId);
        }
    }

    private boolean canApplyToCurrentTrack(PendingEvent event) {
        if (event == null || currentTrack == null) {
            return false;
        }
        if (matchesTrack(event, currentTrack)) {
            return true;
        }
        LyricProviderCapabilities.AssociationStrategy strategy =
                event.capabilities.associationFor(event.outcome);
        return strategy == LyricProviderCapabilities.AssociationStrategy.CURRENT_TRACK;
    }

    private PendingEvent selectPendingEvent(
            TrackSnapshot nextTrack,
            ObservationKind observationKind,
            boolean firstTrackObservation,
            long observedAtMillis) {
        PendingEvent exact = null;
        for (PendingEvent candidate : pendingEvents) {
            if (matchesTrack(candidate, nextTrack)) {
                exact = candidate;
            }
        }
        if (exact != null) {
            return exact;
        }

        boolean canConsumeOrderedFallback =
                observationKind == ObservationKind.STABLE_METADATA || firstTrackObservation;
        if (!canConsumeOrderedFallback) {
            return null;
        }

        for (PendingEvent candidate : pendingEvents) {
            if (hasAuthoritativeIdentity(candidate)) {
                continue;
            }
            if (candidate.isResolved()
                    && isKnownUnhintedReplayForAnotherTrack(
                    candidate.document,
                    nextTrack.key,
                    observedAtMillis)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private boolean matchesTrack(PendingEvent event, TrackSnapshot track) {
        if (event == null || track == null) {
            return false;
        }
        if (!event.mediaId.isEmpty() && !track.songId.isEmpty()) {
            return event.mediaId.equals(track.songId);
        }
        if (!event.mediaUri.isEmpty() && !track.mediaUri.isEmpty()) {
            return event.mediaUri.equals(track.mediaUri);
        }
        return !event.trackHintKey.isEmpty()
                && TrackIdentity.matchesHintKey(event.trackHintKey, track.key);
    }

    private static boolean hasAuthoritativeIdentity(PendingEvent event) {
        return event != null
                && (!event.mediaId.isEmpty()
                || !event.mediaUri.isEmpty()
                || event.capabilities.stableTrackIdentity
                || hasCompleteTrackHint(event.trackHintKey));
    }

    private void applyToTrack(PendingEvent event, TrackSnapshot track) {
        if (event == null || track == null) {
            return;
        }
        if (event.isResolved()) {
            bind(event.document, track.key);
        } else if (event.isTerminal()) {
            invalidateTrack(track.key);
        }
    }

    private void discardUnidentifiedEventsBefore(PendingEvent boundary) {
        Iterator<PendingEvent> iterator = pendingEvents.iterator();
        while (iterator.hasNext()) {
            PendingEvent candidate = iterator.next();
            if (candidate == boundary) {
                return;
            }
            if (!hasAuthoritativeIdentity(candidate)) {
                iterator.remove();
            }
        }
    }

    private void discardUnidentifiedEventsAtOrBefore(long occurredAtMillis) {
        Iterator<PendingEvent> iterator = pendingEvents.iterator();
        while (iterator.hasNext()) {
            PendingEvent candidate = iterator.next();
            if (candidate.occurredAtMillis <= occurredAtMillis
                    && !hasAuthoritativeIdentity(candidate)) {
                iterator.remove();
            }
        }
    }

    private void enqueue(PendingEvent event) {
        pendingEvents.addLast(event);
        while (pendingEvents.size() > pendingMaxEntries) {
            pendingEvents.removeFirst();
        }
    }

    private LyricDocument findEquivalentCapture(PendingEvent event) {
        LyricDocument recent = mostRecentDocument;
        if (recent == null
                || !recent.source.equals(event.source)
                || !recent.lyric.equals(event.lyric)
                || !recent.rawLyric.equals(event.rawLyric)
                || !recent.trackHintKey.equals(event.trackHintKey)
                || !recent.mediaId.equals(event.mediaId)
                || !recent.mediaUri.equals(event.mediaUri)) {
            return null;
        }
        long age = event.occurredAtMillis - recent.capturedAtMillis;
        if (age < 0L || age > DUPLICATE_EVENT_COALESCE_WINDOW_MS) {
            return null;
        }
        String currentKey = currentTrack == null ? "" : currentTrack.key;
        if (!recent.boundTrackKey.isEmpty()) {
            return recent.boundTrackKey.equals(currentKey) ? recent : null;
        }
        return recent.baselineEpoch == event.baselineEpoch ? recent : null;
    }

    private boolean findEquivalentTerminal(PendingEvent event) {
        PendingEvent recent = pendingEvents.peekLast();
        if (recent == null
                || recent.outcome != event.outcome
                || !recent.source.equals(event.source)
                || !recent.requestId.equals(event.requestId)
                || !recent.mediaId.equals(event.mediaId)
                || !recent.mediaUri.equals(event.mediaUri)
                || !recent.trackHintKey.equals(event.trackHintKey)
                || recent.baselineEpoch != event.baselineEpoch) {
            return false;
        }
        long age = event.occurredAtMillis - recent.occurredAtMillis;
        return age >= 0L && age <= DUPLICATE_EVENT_COALESCE_WINDOW_MS;
    }

    private LyricDocument findCurrentTrackReplay(LyricDocument document) {
        if (document == null
                || currentTrack == null
                || !isUnidentifiedPassive(document)) {
            return null;
        }
        LyricDocument current = findDocumentForTrack(currentTrack.key);
        if (current == null
                || !currentTrack.key.equals(current.boundTrackKey)
                || !current.source.equals(document.source)) {
            return null;
        }
        return sameLyricContent(current, document) ? current : null;
    }

    private boolean isKnownUnhintedReplayForAnotherTrack(
            LyricDocument document,
            String nextTrackKey,
            long nowMillis) {
        if (!isUnidentifiedPassive(document) || nullToEmpty(nextTrackKey).isEmpty()) {
            return false;
        }

        boolean matchesDifferentTrack = false;
        for (LyricDocument candidate : documentsByTrack.values()) {
            if (candidate == null
                    || candidate == document
                    || !isFresh(candidate, nowMillis)
                    || candidate.boundTrackKey.isEmpty()
                    || !candidate.source.equals(document.source)
                    || !sameLyricContent(candidate, document)) {
                continue;
            }
            if (candidate.boundTrackKey.equals(nextTrackKey)) {
                return false;
            }
            matchesDifferentTrack = true;
        }
        return matchesDifferentTrack;
    }

    private static boolean isUnidentifiedPassive(LyricDocument document) {
        return document != null
                && document.requestId.isEmpty()
                && document.mediaId.isEmpty()
                && document.mediaUri.isEmpty()
                && document.trackHintKey.isEmpty()
                && document.capabilities.associationStrategy
                == LyricProviderCapabilities.AssociationStrategy.NEXT_TRACK_OBSERVATION;
    }

    private LyricDocument findDocumentForTrack(String trackKey) {
        if (nullToEmpty(trackKey).isEmpty()) {
            return null;
        }
        LyricDocument exact = documentsByTrack.get(trackKey);
        return exact != null && trackKey.equals(exact.boundTrackKey) ? exact : null;
    }

    private void bind(LyricDocument document, String trackKey) {
        if (document == null || nullToEmpty(trackKey).isEmpty()) {
            return;
        }
        document.boundTrackKey = trackKey;
        documentsByTrack.put(trackKey, document);
        trimCache();
    }

    private void invalidateTrack(String trackKey) {
        LyricDocument removed = documentsByTrack.remove(nullToEmpty(trackKey));
        if (removed != null && removed == mostRecentDocument) {
            mostRecentDocument = null;
        }
    }

    private void pruneExpired(long nowMillis) {
        Iterator<PendingEvent> pendingIterator = pendingEvents.iterator();
        while (pendingIterator.hasNext()) {
            PendingEvent event = pendingIterator.next();
            long age = nowMillis - event.occurredAtMillis;
            if (age < 0L || age > MAX_PENDING_EVENT_AGE_MS) {
                pendingIterator.remove();
            }
        }

        Iterator<Map.Entry<String, RequestContext>> requestIterator =
                requestsById.entrySet().iterator();
        while (requestIterator.hasNext()) {
            RequestContext request = requestIterator.next().getValue();
            long age = nowMillis - request.startedAtMillis;
            if (age < 0L || age > MAX_PENDING_EVENT_AGE_MS) {
                requestIterator.remove();
            }
        }

        if (mostRecentDocument != null && !isFresh(mostRecentDocument, nowMillis)) {
            mostRecentDocument = null;
        }
        documentsByTrack.entrySet().removeIf(
                entry -> !isFresh(entry.getValue(), nowMillis));
    }

    private boolean isFresh(LyricDocument document, long nowMillis) {
        if (document == null) {
            return false;
        }
        long age = nowMillis - document.capturedAtMillis;
        return age >= 0L && age <= documentMaxAgeMillis;
    }

    private void trimCache() {
        while (documentsByTrack.size() > cacheMaxEntries) {
            String eldest = documentsByTrack.keySet().iterator().next();
            documentsByTrack.remove(eldest);
        }
    }

    private void trimRequests() {
        while (requestsById.size() > pendingMaxEntries) {
            String eldest = requestsById.keySet().iterator().next();
            requestsById.remove(eldest);
        }
    }

    private static boolean hasCompleteTrackHint(String trackHintKey) {
        int separator = nullToEmpty(trackHintKey).indexOf('|');
        return separator > 0 && separator + 1 < trackHintKey.length();
    }

    private static boolean sameLyricContent(LyricDocument first, LyricDocument second) {
        if (first == null || second == null) {
            return false;
        }
        if (!first.rawLyric.isEmpty() && first.rawLyric.equals(second.rawLyric)) {
            return true;
        }
        return !first.lyric.isEmpty() && first.lyric.equals(second.lyric);
    }

    private static String firstNonEmpty(String first, String second) {
        return nullToEmpty(first).isEmpty() ? nullToEmpty(second) : first;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
