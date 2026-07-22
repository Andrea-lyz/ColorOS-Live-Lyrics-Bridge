package io.github.andrealtb.lockscreenlyrics;

import android.os.SystemClock;

/**
 * Poweramp-only external-lyric compatibility state.
 *
 * <p>Poweramp can publish a track-generation event ahead of SystemUI metadata. This component
 * owns the generation/key authority window, stale-document rejection, and its narrowly scoped
 * SystemUI handoff actions. No other Provider may reuse this state machine.</p>
 */
final class PowerampExternalLyricAdaptation {
    private static final long NATIVE_POSITION_AUTHORITY_MS = 3_000L;
    private static final long LOG_INTERVAL_MS = 1_500L;

    interface Host {
        boolean currentLyricPayloadMatchesTrack(String title, String artist);

        boolean currentExternalWordLyricModelMatchesTrack(String source, String trackKey);

        void bindCurrentLyricProviderPackage(String packageName, String reason);

        void clearSystemUiLyricModelForTrackChange(String title, String artist);

        void resetSystemUiPlaybackPositionForTrackChange(
                String title,
                String artist,
                String reason);

        void markExternalTrackAsCurrentSystemUiTrack(String title, String artist, long observedAt);

        boolean isLyricLayoutDiagnosticsEnabled();

        String shortenForLog(String value);

        void info(String message);
    }

    private final Host host;
    private volatile long trackGeneration;
    private volatile String trackKey = "";
    private volatile long nativePositionAuthorityUntilElapsedMs;
    private volatile long lastStaleGenerationLogAt;
    private volatile long lastDeferredDocumentLogAt;

    PowerampExternalLyricAdaptation(Host host) {
        this.host = host;
    }

    boolean handlesProviderSource(String source) {
        return ExternalLyricProviderSpecialCases.isPowerampProviderSource(source);
    }

    boolean handlePowerampProviderTrackEvent(
            String source,
            boolean trackChanged,
            long incomingGeneration,
            String incomingTrackHintKey,
            String title,
            String artist) {
        if (!ExternalLyricProviderSpecialCases.isPowerampProviderSource(source)
                || incomingGeneration <= 0L) {
            return false;
        }
        if (incomingGeneration < trackGeneration) {
            return false;
        }

        String incomingKey = firstNonEmpty(incomingTrackHintKey, TrackIdentity.buildKey(title, artist));
        boolean sameGeneration = incomingGeneration == trackGeneration;
        boolean sameTrack = isEmpty(trackKey)
                || isEmpty(incomingKey)
                || TrackIdentity.matchesHintKey(incomingKey, trackKey);
        if (sameGeneration && sameTrack) {
            return false;
        }
        if (sameGeneration && !sameTrack && !trackChanged) {
            return false;
        }

        String previousKey = trackKey;
        trackGeneration = incomingGeneration;
        trackKey = incomingKey;
        long now = SystemClock.elapsedRealtime();
        nativePositionAuthorityUntilElapsedMs = now + NATIVE_POSITION_AUTHORITY_MS;
        host.bindCurrentLyricProviderPackage(
                ExternalLyricProviderSpecialCases.powerampProviderPackage(),
                "Poweramp external track event");

        boolean previousExternalTrackKnown = !isEmpty(previousKey);
        boolean incomingMatchesPreviousTrack = previousExternalTrackKnown
                && TrackIdentity.matchesHintKey(incomingKey, previousKey);
        boolean payloadMatchesIncomingTrack = host.currentLyricPayloadMatchesTrack(title, artist);
        boolean powerampModelMatchesIncomingTrack =
                host.currentExternalWordLyricModelMatchesTrack(source, incomingKey);
        boolean preserveSameTrackPosition =
                LockscreenIntegrationPolicy.shouldPreservePowerampPositionForSameTrackReattach(
                        previousExternalTrackKnown,
                        incomingMatchesPreviousTrack,
                        payloadMatchesIncomingTrack,
                        powerampModelMatchesIncomingTrack);
        if (preserveSameTrackPosition && host.isLyricLayoutDiagnosticsEnabled()) {
            host.info("Preserved native Poweramp playback position across same-track reattach"
                    + ", title=" + host.shortenForLog(title));
        }
        if (!isEmpty(title)
                && !preserveSameTrackPosition
                && (!incomingMatchesPreviousTrack || !payloadMatchesIncomingTrack)) {
            host.clearSystemUiLyricModelForTrackChange(title, artist);
        } else if (!isEmpty(title) && !preserveSameTrackPosition) {
            host.resetSystemUiPlaybackPositionForTrackChange(
                    title,
                    artist,
                    "Poweramp external track event");
        }
        if (!isEmpty(title)) {
            host.markExternalTrackAsCurrentSystemUiTrack(title, artist, now);
        }
        host.info("Accepted Poweramp external track event"
                + ", event=" + (trackChanged ? "trackChanged" : "lyricReady")
                + ", generation=" + incomingGeneration
                + ", title=" + host.shortenForLog(title)
                + ", artist=" + host.shortenForLog(artist));
        return true;
    }

    boolean shouldDiscardStalePowerampProviderLyric(
            String source,
            long incomingGeneration,
            String incomingTrackHintKey,
            String title,
            String artist) {
        if (!ExternalLyricProviderSpecialCases.isPowerampProviderSource(source)
                || incomingGeneration <= 0L) {
            return false;
        }
        if (incomingGeneration != trackGeneration) {
            return true;
        }
        String incomingKey = firstNonEmpty(incomingTrackHintKey, TrackIdentity.buildKey(title, artist));
        return !isEmpty(trackKey)
                && !isEmpty(incomingKey)
                && !TrackIdentity.matchesHintKey(incomingKey, trackKey);
    }

    void logRejectedStalePowerampProviderLyric(
            long incomingGeneration,
            String incomingTrackHintKey,
            String title,
            String artist) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastStaleGenerationLogAt < LOG_INTERVAL_MS) {
            return;
        }
        lastStaleGenerationLogAt = now;
        host.info("Ignored stale Poweramp external lyric"
                + ", incomingGeneration=" + incomingGeneration
                + ", currentGeneration=" + trackGeneration
                + ", incomingKey=" + host.shortenForLog(incomingTrackHintKey)
                + ", currentKey=" + host.shortenForLog(trackKey)
                + ", incomingTitle=" + host.shortenForLog(title)
                + ", incomingArtist=" + host.shortenForLog(artist));
    }

    boolean isCurrentPowerampProviderDocument(
            String source,
            long documentGeneration,
            String documentTrackHintKey,
            String title,
            String artist) {
        if (!ExternalLyricProviderSpecialCases.isPowerampProviderSource(source)
                || documentGeneration <= 0L
                || documentGeneration != trackGeneration) {
            return false;
        }
        String documentKey = firstNonEmpty(documentTrackHintKey, TrackIdentity.buildKey(title, artist));
        return isEmpty(trackKey)
                || isEmpty(documentKey)
                || TrackIdentity.matchesHintKey(documentKey, trackKey);
    }

    boolean shouldDeferPowerampProviderDocumentForRecentSystemUiTrack(
            String source,
            long documentGeneration,
            String documentTrackHintKey,
            String documentTitle,
            String documentArtist,
            String systemUiTitle,
            String systemUiArtist,
            long systemUiTrackIdentityChangedAtElapsedMs) {
        if (!ExternalLyricProviderSpecialCases.isPowerampProviderSource(source)
                || isEmpty(systemUiTitle)
                || systemUiTrackIdentityChangedAtElapsedMs <= 0L) {
            return false;
        }
        if (isCurrentPowerampProviderDocument(
                source,
                documentGeneration,
                documentTrackHintKey,
                documentTitle,
                documentArtist)) {
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - systemUiTrackIdentityChangedAtElapsedMs
                > ExternalLyricProviderSpecialCases.POWERAMP_SYSTEMUI_TRACK_AUTHORITY_MS) {
            return false;
        }
        return !TrackIdentity.matchesHintKey(
                firstNonEmpty(documentTrackHintKey, TrackIdentity.buildKey(documentTitle, documentArtist)),
                TrackIdentity.buildKey(systemUiTitle, systemUiArtist));
    }

    void logDeferredPowerampProviderDocument(
            String documentTitle,
            String documentArtist,
            String systemUiTitle,
            String systemUiArtist,
            String reason) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastDeferredDocumentLogAt < LOG_INTERVAL_MS) {
            return;
        }
        lastDeferredDocumentLogAt = now;
        host.info("Deferred Poweramp external lyric document after " + empty(reason)
                + ", documentTitle=" + host.shortenForLog(documentTitle)
                + ", documentArtist=" + host.shortenForLog(documentArtist)
                + ", systemUiTitle=" + host.shortenForLog(systemUiTitle)
                + ", systemUiArtist=" + host.shortenForLog(systemUiArtist));
    }

    boolean hasNativePlaybackPositionAuthority(long now) {
        return LockscreenIntegrationPolicy.shouldTrustPowerampNativePosition(
                now,
                nativePositionAuthorityUntilElapsedMs);
    }

    boolean shouldIgnoreStalePowerampSeedlingMediaBundle(
            String packageName,
            String incomingTitle,
            String incomingArtist,
            String currentSystemUiTitle,
            String currentSystemUiArtist,
            long trackPositionResetGuardUntilElapsedMs,
            long now) {
        if (isEmpty(incomingTitle)
                || isEmpty(currentSystemUiTitle)
                || now > trackPositionResetGuardUntilElapsedMs
                || !ExternalLyricProviderSpecialCases.isPowerampProviderPackage(packageName)) {
            return false;
        }
        return !TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey(incomingTitle, incomingArtist),
                TrackIdentity.buildKey(currentSystemUiTitle, currentSystemUiArtist));
    }

    private static String firstNonEmpty(String first, String second) {
        return isEmpty(first) ? empty(second) : first;
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
