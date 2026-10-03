package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** A download already triggered for a song outlives every display state; only the song or the feature ends it. */
public final class ArtworkResolveLifetime {
    /** Past this, a request that produced nothing is treated as stuck rather than merely slow. */
    public static final long RETAIN_WINDOW_MS = 6_000;
    private ArtworkResolveLifetime() {}

    /**
     * Screen state, keyguard, playback state, view geometry and transitions are deliberately absent:
     * they decide whether the video is shown, not whether the asset is fetched and cached.
     */
    public static boolean abandoned(String reason) {
        return reason.equals("bridge_off") || reason.equals("detached") || reason.equals("binding_not_ready");
    }

    /** Same song on the other surface, while the issued download is still young enough to keep. */
    public static boolean retainForSameSong(ArtworkRequestStamp issued, ArtworkRequestStamp wanted, long inFlightMs) {
        return inFlightMs >= 0 && inFlightMs <= RETAIN_WINDOW_MS && sameSong(issued, wanted);
    }

    /** Stable song identity: the surface epoch and frame epochs differ between the two views. */
    public static boolean sameSong(ArtworkRequestStamp issued, ArtworkRequestStamp wanted) {
        return issued != null && wanted != null
                && issued.sessionEpoch() == wanted.sessionEpoch()
                && issued.trackGeneration() == wanted.trackGeneration();
    }
}
