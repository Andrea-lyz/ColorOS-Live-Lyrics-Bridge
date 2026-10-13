package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Bounds same-song surface holds and the paused frame's brief return to native static artwork. */
public final class ArtworkSurfaceHandover {
    public static final long MAX_HOLD_MS = 3_000;
    private ArtworkSurfaceHandover() {}

    public static boolean mayRetain(ArtworkRequestStamp issued, ArtworkRequestStamp candidate,
            String reason, long elapsedMs) {
        return elapsedMs >= 0 && elapsedMs < MAX_HOLD_MS
                && ArtworkResolveLifetime.sameSong(issued, candidate)
                && issued.pluginEpoch() == candidate.pluginEpoch()
                && ("eligible".equals(reason) || "geometry_transition".equals(reason)
                    || "no_bounds".equals(reason) || "hidden".equals(reason));
    }

    public static boolean mayFadeToStatic(ArtworkRequestStamp issued, ArtworkRequestStamp candidate,
            String reason, boolean settled) {
        return settled && "paused".equals(reason) && ArtworkResolveLifetime.sameSong(issued, candidate)
                && issued.pluginEpoch() == candidate.pluginEpoch()
                && issued.surfaceEpoch() == candidate.surfaceEpoch()
                && issued.serviceEpoch() == candidate.serviceEpoch()
                && issued.configRevision() == candidate.configRevision();
    }
}
