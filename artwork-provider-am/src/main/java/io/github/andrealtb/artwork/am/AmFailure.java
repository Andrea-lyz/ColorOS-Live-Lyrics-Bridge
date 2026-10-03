package io.github.andrealtb.artwork.am;

import io.github.andrealtb.artwork.contract.ArtworkResult;

final class AmFailure extends Exception {
    final ArtworkResult.Status status;
    final String reason;
    final long retryMs;
    AmFailure(ArtworkResult.Status status, String reason) { this(status, reason, 0); }
    AmFailure(ArtworkResult.Status status, String reason, long retryMs) {
        super(reason);
        this.status = status;
        this.reason = reason;
        this.retryMs = retryMs;
    }
    ArtworkResult result() { return new ArtworkResult(status, null, retryMs, reason); }
}
