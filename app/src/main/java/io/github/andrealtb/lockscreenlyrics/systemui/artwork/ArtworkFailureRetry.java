package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/**
 * Retry delays for failures that are not a provider answer. Provider results carry their own policy
 * (RETRY_LATER, NETWORK_BLOCKED, NO_MOTION); these are transport, process and decoder failures.
 *
 * Device logs showed the provider process stopped by the OEM background cleaner ({@code o-stop}) and
 * repeatedly frozen while SystemUI waited. Without a retry the same song stayed static on that surface
 * until the screen turned off, the song changed or the host view was recreated.
 */
public final class ArtworkFailureRetry {
    /** Per song: a reproducible failure (for example a file the decoder rejects) must not loop. */
    static final int MAX_PER_SONG = 3;
    static final long NONE = Long.MAX_VALUE;
    private ArtworkRequestStamp song;
    private int used;

    /** The provider process went away or never answered the handshake: a restarted process answers quickly. */
    static long delayMs(String reason) {
        return switch (reason) {
            case "provider_died", "provider_disconnected", "binding_died", "null_binding", "handshake_timeout",
                    "decode_failed", "prepare_failed", "first_frame_timeout", "surface_handover_failed",
                    "pause_failed", "geometry_changed", "consumer_failed" -> 5_000;
            case "request_timeout", "bind_failed", "selection_timeout", "selection_failed",
                    "ipc_budget_exhausted", "provider_or_asset_failed" -> 30_000;
            // Identity/mode rejections, unsupported hosts and incomplete metadata do not heal by waiting.
            default -> NONE;
        };
    }

    /** {@link #NONE} when the reason is not retryable or this song has used its budget. */
    long next(String reason, ArtworkRequestStamp failedSong) {
        long delay = delayMs(reason);
        if (delay == NONE) return NONE;
        if (!ArtworkResolveLifetime.sameSong(song, failedSong)) { song = failedSong; used = 0; }
        if (used >= MAX_PER_SONG) return NONE;
        used++;
        return delay;
    }

    /** A frame reached the screen: later failures of this song start a fresh budget. */
    void played() { song = null; used = 0; }
}
