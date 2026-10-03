package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Only geometry gaps of the same attached, playing song may retain a decoder. */
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
}
