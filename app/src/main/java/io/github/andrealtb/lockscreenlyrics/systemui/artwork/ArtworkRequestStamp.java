package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Local-only asynchronous ownership. None of these epochs are sent to a resource provider. */
public record ArtworkRequestStamp(long clientEpoch, long serviceEpoch, long pluginEpoch,
        long surfaceEpoch, long sessionEpoch, long trackGeneration,
        long requestRevision, long configRevision) {
    public boolean isCurrent(ArtworkRequestStamp current) {
        return equals(current);
    }
}
