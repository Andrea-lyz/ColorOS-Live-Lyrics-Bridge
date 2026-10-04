package io.github.andrealtb.artwork.am;

import io.github.andrealtb.artwork.contract.ArtworkResult;

final class AmFailure extends Exception {
    final ArtworkResult.Status status;
    final String reason;
    final long retryMs;
    /** Diagnostic only (a parsing step or host), never sent to the client and never page content. */
    final String detail;
    AmFailure(ArtworkResult.Status status, String reason) { this(status, reason, 0); }
    AmFailure(ArtworkResult.Status status, String reason, long retryMs) { this(status, reason, retryMs, null); }
    AmFailure(ArtworkResult.Status status, String reason, long retryMs, String detail) {
        super(reason);
        this.status = status;
        this.reason = reason;
        this.retryMs = retryMs;
        this.detail = detail;
    }
    ArtworkResult result() { return new ArtworkResult(status, null, retryMs, reason); }
}
