package io.github.andrealtb.lockscreenlyrics;

import android.media.MediaMetadata;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Spotify-only bounded wait for a lyric-ready event following a track-change event.
 */
final class SpotifyExternalLyricAdaptation {
    private static final long SPOTIFY_EXTERNAL_LYRIC_WAIT_MS = 360L;
    private static final long SPOTIFY_TRACK_CHANGE_WAIT_FRESHNESS_MS = 1_500L;

    interface Host {
        LockscreenLyricsModule.ExternalTrackGenerationState latestExternalTrackGeneration(
                String source);

        Object externalLyricDocumentArrivalLock();

        LockscreenLyricsModule.ExternalLyricDocument findExternalLyricDocumentForMetadata(
                MediaMetadata metadata,
                String title,
                String artist,
                long nowMillis);

        boolean isCurrentGeneratedExternalDocument(
                LockscreenLyricsModule.ExternalLyricDocument document);

        void info(String message);

        String shortenForLog(String value);
    }

    private final Host host;

    SpotifyExternalLyricAdaptation(Host host) {
        this.host = host;
    }

    LockscreenLyricsModule.ExternalLyricDocument
            awaitSpotifyLyricReadyBeforeSystemUiFallback(
            MediaMetadata metadata,
            String packageName,
            String title,
            String artist) {
        if (!ExternalLyricProviderSpecialCases.isSpotifyPlayerPackage(packageName)
                || Looper.myLooper() == Looper.getMainLooper()) {
            return null;
        }
        LockscreenLyricsModule.ExternalTrackGenerationState trackState =
                host.latestExternalTrackGeneration(
                        ExternalLyricProviderSpecialCases.spotifyProviderSource());
        long now = SystemClock.elapsedRealtime();
        if (trackState == null
                || now - trackState.observedAtElapsedMs < 0L
                || now - trackState.observedAtElapsedMs
                > SPOTIFY_TRACK_CHANGE_WAIT_FRESHNESS_MS) {
            return null;
        }
        String metadataKey = TrackIdentity.buildKey(title, artist);
        if (!isEmpty(metadataKey)
                && !isEmpty(trackState.trackKey)
                && !TrackIdentity.matchesHintKey(metadataKey, trackState.trackKey)) {
            return null;
        }

        long startedAt = now;
        long deadline = startedAt + SPOTIFY_EXTERNAL_LYRIC_WAIT_MS;
        Object arrivalLock = host.externalLyricDocumentArrivalLock();
        while (true) {
            synchronized (arrivalLock) {
                LockscreenLyricsModule.ExternalTrackGenerationState latestTrackState =
                        host.latestExternalTrackGeneration(
                                ExternalLyricProviderSpecialCases.spotifyProviderSource());
                if (latestTrackState == null
                        || latestTrackState.generation != trackState.generation) {
                    return null;
                }
                LockscreenLyricsModule.ExternalLyricDocument document =
                        host.findExternalLyricDocumentForMetadata(
                                metadata,
                                title,
                                artist,
                                System.currentTimeMillis());
                if (document != null
                        && document.trackGeneration == trackState.generation
                        && host.isCurrentGeneratedExternalDocument(document)) {
                    host.info("Waited " + (SystemClock.elapsedRealtime() - startedAt)
                            + "ms for Spotify lyricReady before SystemUI fallback"
                            + ", generation=" + trackState.generation
                            + ", title=" + host.shortenForLog(title));
                    return document;
                }
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) {
                    return null;
                }
                try {
                    arrivalLock.wait(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
