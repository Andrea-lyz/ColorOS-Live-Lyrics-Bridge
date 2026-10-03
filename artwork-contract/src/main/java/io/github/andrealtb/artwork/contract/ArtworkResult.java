package io.github.andrealtb.artwork.contract;

public final class ArtworkResult {
    public enum Status {
        READY, NO_MATCH, NO_MOTION, AMBIGUOUS, RETRY_LATER, NETWORK_BLOCKED, UNSUPPORTED, ERROR
    }

    public final Status status;
    public final ArtworkAsset asset;
    public final long retryAfterMs;
    public final String reason;

    public ArtworkResult(Status status, ArtworkAsset asset, long retryAfterMs, String reason) {
        if (status == null || (status == Status.READY) != (asset != null)
                || retryAfterMs < 0 || retryAfterMs > 24L * 60 * 60 * 1000
                || (retryAfterMs != 0 && status != Status.RETRY_LATER)) {
            throw new IllegalArgumentException("invalid_result");
        }
        this.status = status;
        this.asset = asset;
        this.retryAfterMs = retryAfterMs;
        this.reason = ArtworkContract.text(reason, 64, false);
        if (!this.reason.matches("[a-z0-9_]*")) {
            throw new IllegalArgumentException("invalid_reason");
        }
    }

    public static ArtworkResult failure(Status status, String reason) {
        return new ArtworkResult(status, null, 0, reason);
    }
}
