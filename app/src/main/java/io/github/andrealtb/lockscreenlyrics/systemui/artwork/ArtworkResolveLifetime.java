package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** A download already triggered for a song outlives every display state; only the song or the feature ends it. */
public final class ArtworkResolveLifetime {
    /** Online providers may spend 52 seconds on source fallback and transfer; leave time for FD verification. */
    public static final long ONLINE_TIMEOUT_MS = 55_000;
    /** The same song's in-flight work remains useful across a surface switch until its request expires. */
    public static final long RETAIN_WINDOW_MS = ONLINE_TIMEOUT_MS;
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

    /** Same recording: exactly the fields a provider query is built from, so a re-created surface reuses it. */
    public static boolean sameRecording(ArtworkTrackIdentityPolicy.Metadata issued, ArtworkTrackIdentityPolicy.Metadata wanted) {
        return issued != null && wanted != null && issued.durationMs() > 0 && !issued.title().isEmpty()
                && issued.title().equals(wanted.title())
                && issued.artist().equals(wanted.artist())
                && issued.album().equals(wanted.album())
                && issued.durationMs() == wanted.durationMs();
    }

    /**
     * An issued download stays valid for the same song (session/generation) or the same recording after a
     * keyguard/session re-registration. A different recording never matches.
     */
    public static boolean sameTarget(ArtworkRequestStamp issued, ArtworkTrackIdentityPolicy.Metadata issuedMetadata,
            ArtworkRequestStamp wanted, ArtworkTrackIdentityPolicy.Metadata wantedMetadata) {
        return sameSong(issued, wanted) || sameRecording(issuedMetadata, wantedMetadata);
    }

    /** Same recording on the other surface, while the issued download is still young enough to keep. */
    public static boolean retainForSameTarget(ArtworkRequestStamp issued, ArtworkTrackIdentityPolicy.Metadata issuedMetadata,
            ArtworkRequestStamp wanted, ArtworkTrackIdentityPolicy.Metadata wantedMetadata, long inFlightMs) {
        return inFlightMs >= 0 && inFlightMs <= RETAIN_WINDOW_MS
                && sameTarget(issued, issuedMetadata, wanted, wantedMetadata);
    }
}
