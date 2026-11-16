package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

import android.os.Handler;
import android.os.SystemClock;
import android.view.View;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Lockscreen lyric surface mode (E-6b, plan §15.8.12): whether the lyric page is active
 * (keep-awake), the delayed surface-reactivation callbacks and their generation, the provisional
 * draw window, and the AOD low-frame-rate flag. This class is the only writer of that state; the
 * entry class reads it and calls the transition methods below in the order it used to assign the
 * fields inline.
 *
 * <p>The orchestration around a transition (renderer refresh, translation views, active refresh,
 * screen timeout) stays in the entry class, which is the composition root for those owners. The
 * mode-rebind runnable belongs to the active-refresh code (E-7b).
 */
final class LyricSurfaceModeController {
    /** Diagnostics sink; the controller has no other dependency on the runtime. */
    interface Host {
        void info(BridgeDebugArea area, String event, String message);
    }

    private final Host host;
    private final OfficialLyricTextRenderer officialLyricTextRenderer;
    private final Handler mainHandler;
    volatile boolean systemUiLyricModeKeepAwakeActive;
    volatile long lastSystemUiLyricModeLogAt;
    private volatile long lastSystemUiLyricModeStateLogAt;
    volatile boolean lyricSurfaceReactivationPending;
    volatile int lyricSurfaceReactivationGeneration;
    private final Object lyricSurfaceReactivationCallbacksLock = new Object();
    private final ArrayList<Runnable> lyricSurfaceReactivationCallbacks =
            new ArrayList<>(LYRIC_VISIBILITY_RECOVERY_DELAYS_MS.length);
    WeakReference<View> lyricSurfaceProvisionalRecyclerView = new WeakReference<>(null);
    volatile long lyricSurfaceProvisionalDrawUntilElapsedMs;
    volatile boolean aodLowFrameRateLyricMode;

    LyricSurfaceModeController(
            Host host,
            OfficialLyricTextRenderer officialLyricTextRenderer,
            Handler mainHandler) {
        this.host = host;
        this.officialLyricTextRenderer = officialLyricTextRenderer;
        this.mainHandler = mainHandler;
    }

    private void info(BridgeDebugArea area, String event, String message) {
        host.info(area, event, message);
    }

    /** Enters lyric mode; returns whether it was inactive before. */
    boolean markLyricModeActive() {
        boolean changed = !systemUiLyricModeKeepAwakeActive;
        systemUiLyricModeKeepAwakeActive = true;
        return changed;
    }

    /** Leaves lyric mode; returns whether it was active before. */
    boolean markLyricModeInactive() {
        boolean changed = systemUiLyricModeKeepAwakeActive;
        systemUiLyricModeKeepAwakeActive = false;
        return changed;
    }

    void noteLyricSurfaceSignal(long now) {
        lastSystemUiLyricModeLogAt = now;
    }

    void markProvisionalLyricSurface(View recycler, long now) {
        View provisionalRecycler = lyricSurfaceProvisionalRecyclerView.get();
        if (provisionalRecycler != recycler
                || now >= lyricSurfaceProvisionalDrawUntilElapsedMs) {
            lyricSurfaceProvisionalRecyclerView = new WeakReference<>(recycler);
            lyricSurfaceProvisionalDrawUntilElapsedMs =
                    now + LYRIC_SURFACE_PROVISIONAL_DRAW_GRACE_MS;
        }
    }

    /** Cancels pending reactivation passes and starts a new round; returns its generation. */
    int beginLyricSurfaceReactivation() {
        cancelLyricSurfaceReactivationCallbacks();
        lyricSurfaceReactivationPending = true;
        return lyricSurfaceReactivationGeneration;
    }

    void postLyricSurfaceReactivation(Runnable callback, long delayMs) {
        synchronized (lyricSurfaceReactivationCallbacksLock) {
            lyricSurfaceReactivationCallbacks.add(callback);
        }
        mainHandler.postDelayed(callback, delayMs);
    }

    void forgetLyricSurfaceReactivation(Runnable callback) {
        synchronized (lyricSurfaceReactivationCallbacksLock) {
            lyricSurfaceReactivationCallbacks.remove(callback);
        }
    }

    void finishLyricSurfaceReactivation() {
        lyricSurfaceReactivationPending = false;
        clearLyricSurfaceProvisionalDraw();
    }

    void maybeLogLyricModeKeepAwake(boolean active) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSystemUiLyricModeStateLogAt < 1_500L) {
            return;
        }
        lastSystemUiLyricModeStateLogAt = now;
        info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Lockscreen lyric UI keep-awake " + (active ? "ON" : "OFF"));
    }

    void setAodLowFrameRateLyricMode(boolean enabled) {
        aodLowFrameRateLyricMode = enabled;
        officialLyricTextRenderer.setAodLowFrameRateMode(enabled);
    }

    void cancelLyricSurfaceReactivationCallbacks() {
        lyricSurfaceReactivationGeneration++;
        synchronized (lyricSurfaceReactivationCallbacksLock) {
            for (Runnable callback : lyricSurfaceReactivationCallbacks) {
                mainHandler.removeCallbacks(callback);
            }
            lyricSurfaceReactivationCallbacks.clear();
        }
        lyricSurfaceReactivationPending = false;
        clearLyricSurfaceProvisionalDraw();
    }

    private void clearLyricSurfaceProvisionalDraw() {
        lyricSurfaceProvisionalDrawUntilElapsedMs = 0L;
        lyricSurfaceProvisionalRecyclerView = new WeakReference<>(null);
    }

    private boolean isProvisionalLyricSurfaceDraw(View recycler) {
        if (recycler == null
                || recycler != lyricSurfaceProvisionalRecyclerView.get()
                || SystemClock.elapsedRealtime() >= lyricSurfaceProvisionalDrawUntilElapsedMs) {
            return false;
        }
        return recycler.isAttachedToWindow() && recycler.getVisibility() == View.VISIBLE;
    }
}
