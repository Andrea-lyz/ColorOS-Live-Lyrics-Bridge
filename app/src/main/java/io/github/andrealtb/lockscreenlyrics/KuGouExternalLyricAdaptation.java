package io.github.andrealtb.lockscreenlyrics;

import android.os.SystemClock;

/**
 * KuGou-only protection for Provider word-timed lyrics when the official payload is incomplete.
 */
final class KuGouExternalLyricAdaptation {
    private static final long LOG_INTERVAL_MS = 1_500L;

    interface Host {
        void bindCurrentLyricProviderPackage(String packageName, String reason);

        boolean shouldProtectProviderWordLyricSurface();

        void restoreProviderWordLyricSurfaceAfterOfficialSuppression(String reason);

        String shortenForLog(String value);

        void info(String message);
    }

    private final Host host;
    private volatile long lastOfficialLyricInfoSuppressionLogAt;

    KuGouExternalLyricAdaptation(Host host) {
        this.host = host;
    }

    boolean shouldSuppressKuGouOfficialLyricInfo(
            String packageName,
            LyricInfoContract.Payload payload,
            boolean alreadyHasExternalDocument) {
        return ExternalLyricProviderSpecialCases.shouldSuppressKuGouOfficialLyricInfo(
                packageName,
                payload,
                alreadyHasExternalDocument);
    }

    void retainKuGouProviderWordLyricAfterOfficialSuppression(
            String packageName,
            String title,
            String artist,
            LyricInfoContract.Payload payload) {
        if (packageName != null && !packageName.isEmpty()) {
            host.bindCurrentLyricProviderPackage(packageName, "suppressed official lyricInfo");
        }
        if (host.shouldProtectProviderWordLyricSurface()) {
            host.restoreProviderWordLyricSurfaceAfterOfficialSuppression(
                    "suppressed-kugou-official-lyricInfo");
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastOfficialLyricInfoSuppressionLogAt < LOG_INTERVAL_MS) {
            return;
        }
        lastOfficialLyricInfoSuppressionLogAt = now;
        host.info("Suppressed KuGou official lyricInfo while Provider model is authoritative"
                + ", title=" + host.shortenForLog(title)
                + ", artist=" + host.shortenForLog(artist)
                + ", provider=" + (payload == null ? "" : empty(payload.provider))
                + ", lyricChars=" + (payload == null || payload.lyric == null
                ? 0
                : payload.lyric.length())
                + ", rawChars=" + (payload == null || payload.rawLyric == null
                ? 0
                : payload.rawLyric.length()));
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
