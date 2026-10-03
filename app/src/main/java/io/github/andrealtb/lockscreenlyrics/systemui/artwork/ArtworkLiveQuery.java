package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import io.github.andrealtb.artwork.contract.ArtworkContract;
import io.github.andrealtb.artwork.contract.ArtworkQuery;

/** Exact accepted session snapshot; media ID and host epochs never cross Binder. */
public final class ArtworkLiveQuery {
    private ArtworkLiveQuery() {}
    public static ArtworkQuery from(ArtworkTrackIdentityPolicy.Metadata metadata, int width, int height) {
        if (metadata == null || metadata.title().isEmpty() || metadata.artist().isEmpty() || metadata.durationMs() <= 0) {
            throw new IllegalArgumentException("live_identity_incomplete");
        }
        // Catalog URLs are not inferred from arbitrary player media IDs or artwork URLs.
        return new ArtworkQuery(metadata.title(), metadata.artist(), metadata.album(), metadata.durationMs(), "",
                Math.max(1, Math.min(width, ArtworkContract.MAX_RESOLUTION)),
                Math.max(1, Math.min(height, ArtworkContract.MAX_RESOLUTION)),
                ArtworkContract.MAX_RESOLUTION, ArtworkContract.MAX_RESOLUTION, ArtworkContract.MAX_FILE_BYTES);
    }
}
