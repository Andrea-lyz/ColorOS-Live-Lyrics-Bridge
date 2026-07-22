package io.github.andrealtb.lockscreenlyrics;

import android.media.MediaMetadata;
import android.os.SystemClock;

/**
 * Apple Music-only recovery for a lyric-ready event that arrives after SystemUI loaded metadata.
 */
final class AppleMusicExternalLyricAdaptation {
    private static final long SYSTEM_UI_LYRIC_LOAD_CONTEXT_MAX_AGE_MS = 15_000L;
    private static final long[] SYSTEM_UI_LYRIC_COMMIT_RETRY_DELAYS_MS = {
            0L,
            160L,
            520L,
            1_000L
    };

    interface Host {
        void executeExternalProviderTask(Runnable runnable);

        void postToMain(Runnable runnable);

        void postToMainDelayed(Runnable runnable, long delayMs);

        LockscreenLyricsModule.SystemUiLyricLoadContext latestSystemUiLyricLoadContext();

        boolean isCurrentGeneratedExternalDocument(
                LockscreenLyricsModule.ExternalLyricDocument document);

        boolean externalDocumentMatchesSystemUiTrack(
                LockscreenLyricsModule.ExternalLyricDocument document,
                String title,
                String artist);

        boolean isLyricLayoutDiagnosticsEnabled();

        String shortenForLog(String value);

        void info(String message);

        void reportExternalLyricFailure(String message, Throwable throwable);
    }

    private final Host host;
    private volatile int commitGeneration;
    private volatile String lastCommittedDocumentKey = "";

    AppleMusicExternalLyricAdaptation(Host host) {
        this.host = host;
    }

    void invalidatePendingAppleMusicCommitAfterTrackChanged(String playerPackage) {
        if (ExternalLyricProviderSpecialCases
                .shouldInvalidateAppleMusicSystemUiCommitAfterTrackChanged(playerPackage)) {
            commitGeneration++;
        }
    }

    void scheduleAppleMusicSystemUiCommitAfterLyricPromotion(
            LockscreenLyricsModule.ExternalLyricDocument document) {
        if (document == null
                || !ExternalLyricProviderSpecialCases.shouldReplaySystemUiAfterAppleMusicLyricReady(
                document.source,
                document.sourceInfo.playerPackage)) {
            return;
        }
        int scheduleGeneration = ++commitGeneration;
        for (long delayMs : SYSTEM_UI_LYRIC_COMMIT_RETRY_DELAYS_MS) {
            Runnable schedule = () -> host.executeExternalProviderTask(
                    () -> replayAppleMusicSystemUiLyricLoadAfterLyricReady(
                            document,
                            scheduleGeneration));
            if (delayMs == 0L) {
                host.postToMain(schedule);
            } else {
                host.postToMainDelayed(schedule, delayMs);
            }
        }
    }

    private void replayAppleMusicSystemUiLyricLoadAfterLyricReady(
            LockscreenLyricsModule.ExternalLyricDocument document,
            int scheduleGeneration) {
        if (scheduleGeneration != commitGeneration) {
            return;
        }
        LockscreenLyricsModule.SystemUiLyricLoadContext context =
                host.latestSystemUiLyricLoadContext();
        Object owner = context == null ? null : context.owner.get();
        MediaMetadata metadata = context == null ? null : context.metadata.get();
        String documentKey = appleMusicCommitKey(document);
        long contextAgeMillis = context == null
                ? -1L
                : SystemClock.elapsedRealtime() - context.observedAtElapsedMillis;
        boolean shouldReplay = context != null
                && owner != null
                && metadata != null
                && LockscreenIntegrationPolicy.shouldReplaySystemUiLyricLoadAfterExternalPromotion(
                ExternalLyricProviderSpecialCases.shouldReplaySystemUiAfterAppleMusicLyricReady(
                        document.source,
                        document.sourceInfo.playerPackage),
                host.isCurrentGeneratedExternalDocument(document),
                document.sourceInfo.playerPackage.equals(context.packageName),
                host.externalDocumentMatchesSystemUiTrack(document, context.title, context.artist),
                documentKey.equals(lastCommittedDocumentKey),
                contextAgeMillis,
                SYSTEM_UI_LYRIC_LOAD_CONTEXT_MAX_AGE_MS);
        if (!shouldReplay) {
            return;
        }
        try {
            context.refreshMethod.invoke(owner, context.key, metadata);
            if (scheduleGeneration != commitGeneration
                    || !host.isCurrentGeneratedExternalDocument(document)) {
                return;
            }
            lastCommittedDocumentKey = documentKey;
            if (host.isLyricLayoutDiagnosticsEnabled()) {
                host.info("Replayed SystemUI lyric load after delayed Apple Music lyric ready"
                        + ", generation=" + document.trackGeneration
                        + ", title=" + host.shortenForLog(document.title));
            }
        } catch (Throwable throwable) {
            host.reportExternalLyricFailure(
                    "Failed to replay SystemUI lyric load after Apple Music lyric ready",
                    throwable);
        }
    }

    private static String appleMusicCommitKey(LockscreenLyricsModule.ExternalLyricDocument document) {
        return document.source
                + '|'
                + document.trackGeneration
                + '|'
                + firstNonEmpty(
                document.trackHintKey,
                TrackIdentity.buildKey(document.title, document.artist));
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? second == null ? "" : second : first;
    }
}
