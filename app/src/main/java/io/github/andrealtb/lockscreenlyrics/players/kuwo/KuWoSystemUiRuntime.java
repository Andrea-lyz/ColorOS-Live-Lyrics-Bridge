package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import android.graphics.drawable.Icon;

/**
 * Owns KuWo SystemUI runtime state. Hook installation stays in the composition
 * root; this object does not change Renderer/AOD timing.
 */
public final class KuWoSystemUiRuntime {
    public static final long LOG_THROTTLE_MS = 1_500L;

    private final KuWoArtworkSnapshotStore<Icon> artwork = new KuWoArtworkSnapshotStore<>();
    private final KuWoSameTrackLyricRetention retention = new KuWoSameTrackLyricRetention();
    private volatile boolean artworkRestoreLogged;
    private volatile long sameIdentityArtworkRestoreLoggedAt;
    private volatile boolean artworkFetchFailureLogged;
    private volatile boolean carLyricIdentityNormalizedLogged;
    private volatile long seedlingArtworkRepairLoggedAt;
    private long mediaModelRetainLoggedAt;

    public KuWoArtworkSnapshotStore<Icon> artwork() {
        return artwork;
    }

    public KuWoSameTrackLyricRetention retention() {
        return retention;
    }

    public boolean takeMediaModelLog(long nowElapsedRealtime) {
        if (nowElapsedRealtime - mediaModelRetainLoggedAt < LOG_THROTTLE_MS) {
            return false;
        }
        mediaModelRetainLoggedAt = nowElapsedRealtime;
        return true;
    }

    public boolean takeSeedlingLog(long nowElapsedRealtime) {
        if (nowElapsedRealtime - seedlingArtworkRepairLoggedAt < LOG_THROTTLE_MS) {
            return false;
        }
        seedlingArtworkRepairLoggedAt = nowElapsedRealtime;
        return true;
    }

    public boolean takeSameIdentityArtworkRestoreLog(long nowElapsedRealtime) {
        if (nowElapsedRealtime - sameIdentityArtworkRestoreLoggedAt < LOG_THROTTLE_MS) {
            return false;
        }
        sameIdentityArtworkRestoreLoggedAt = nowElapsedRealtime;
        return true;
    }

    public boolean takeArtworkRestoreLogOnce() {
        if (artworkRestoreLogged) {
            return false;
        }
        artworkRestoreLogged = true;
        return true;
    }

    public boolean takeArtworkFetchFailureLogOnce() {
        if (artworkFetchFailureLogged) {
            return false;
        }
        artworkFetchFailureLogged = true;
        return true;
    }

    public boolean takeCarLyricNormalizedLogOnce() {
        if (carLyricIdentityNormalizedLogged) {
            return false;
        }
        carLyricIdentityNormalizedLogged = true;
        return true;
    }
}
