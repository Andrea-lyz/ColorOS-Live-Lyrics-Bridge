package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;

import java.lang.reflect.Method;

/**
 * Smart screen timeout on the lockscreen lyric page (E-3, plan §15.3): the bright WakeLock lease,
 * the user-activity pulse, the custom-timeout window, the keyguard-state cache and the lyric
 * evidence window. Moved verbatim from {@code LockscreenLyricsModule}; the only body edits are
 * entry-class field reads that now go through {@link Host}.
 *
 * <p>The SCREEN_OFF / SCREEN_ON / USER_PRESENT receiver stays in the entry class: it also drives
 * AOD frame-rate mode, the screen-state settle window and the translation-action rebind, which are
 * not this controller's state. It calls into this controller in the same order as before.
 */
final class ScreenTimeoutController {
    /** What the controller reads from, or asks of, the rest of the SystemUI runtime. */
    interface Host {
        boolean isSystemUiLyricModeKeepAwakeActive();

        boolean isLastPlaybackPlaying();

        boolean hasCurrentWordLyricModel();

        boolean hasSystemUiOfficialLyric();

        boolean hasSupportedSystemUiPlayer();

        void deactivateSystemUiLyricModeAfterSurfaceHidden();

        void ensureScreenTimeoutReceiver(Context context);

        void info(BridgeDebugArea area, String event, String message);

        void error(String message, Throwable throwable);
    }

    private static final long SCREEN_TIMEOUT_USER_ACTIVITY_INTERVAL_MS =
            LyricTimingTuningConstants.ScreenTimeout.USER_ACTIVITY_INTERVAL_MS;
    private static final long SCREEN_TIMEOUT_WAKE_LOCK_LEASE_MS =
            LyricTimingTuningConstants.ScreenTimeout.WAKE_LOCK_LEASE_MS;
    private static final long SCREEN_TIMEOUT_VISIBLE_LYRIC_VIEW_MAX_AGE_MS =
            LyricTimingTuningConstants.ScreenTimeout.VISIBLE_LYRIC_VIEW_MAX_AGE_MS;
    static final long SCREEN_TIMEOUT_MODEL_EVIDENCE_GRACE_MS =
            LyricTimingTuningConstants.ScreenTimeout.MODEL_EVIDENCE_GRACE_MS;
    private static final long SCREEN_TIMEOUT_USER_PRESENT_RECHECK_DELAY_MS =
            LyricTimingTuningConstants.ScreenTimeout.USER_PRESENT_RECHECK_DELAY_MS;
    private static final long SCREEN_TIMEOUT_KEYGUARD_STATE_CACHE_MS =
            LyricTimingTuningConstants.ScreenTimeout.KEYGUARD_STATE_CACHE_MS;
    static final long SCREEN_TIMEOUT_VISIBLE_LYRIC_NOTE_THROTTLE_MS =
            LyricTimingTuningConstants.ScreenTimeout.VISIBLE_LYRIC_NOTE_THROTTLE_MS;

    private final Host host;
    private final Handler mainHandler;
    volatile boolean screenTimeoutSmartEnabled =
            LyricUiSettings.DEFAULT_SCREEN_TIMEOUT_ENABLED;
    volatile int screenTimeoutCustomSeconds =
            LyricUiSettings.DEFAULT_SCREEN_TIMEOUT_SECONDS;
    private volatile long lastScreenTimeoutLogAt;
    volatile long lastVisibleOfficialLyricTextViewAt;
    private volatile boolean screenTimeoutUserActivityPulsePosted;
    private volatile boolean screenTimeoutUserPresentRecheckPosted;
    private volatile boolean screenTimeoutUserActivityFailureLogged;
    volatile boolean screenTimeoutPausedByScreenOff;
    volatile boolean screenTimeoutPausedByUserPresent;
    volatile long screenTimeoutLyricEvidenceGraceUntilElapsedMs;
    private volatile long screenTimeoutKeepAwakeUntilElapsedMs;
    private PowerManager.WakeLock screenTimeoutWakeLock;
    PowerManager screenTimeoutPowerManager;
    KeyguardManager screenTimeoutKeyguardManager;
    private volatile boolean screenTimeoutKeyguardLockedCached;
    private volatile long screenTimeoutKeyguardCacheUntilElapsedMs;
    private final Runnable screenTimeoutUserActivityPulse = new Runnable() {
        @Override
        public void run() {
            screenTimeoutUserActivityPulsePosted = false;
            Context context = currentApplicationContext();
            if (!hasScreenTimeoutWakeLockBaseConditions(context)) {
                resetScreenTimeoutKeepAwakeWindow();
                releaseScreenTimeoutWakeLock("conditions changed");
                return;
            }
            if (!isScreenTimeoutKeepAwakeWindowActive()) {
                releaseScreenTimeoutWakeLock("custom timeout elapsed");
                maybeLogScreenTimeout("Stopped screen timeout keep-awake after custom timeout", true);
                return;
            }
            PowerManager powerManager = screenTimeoutPowerManager;
            if (powerManager != null) {
                renewScreenTimeoutWakeLockLease(powerManager);
                pulseScreenTimeoutUserActivity(powerManager, false);
            }
            scheduleScreenTimeoutUserActivityPulse();
        }
    };

    ScreenTimeoutController(Host host, Handler mainHandler) {
        this.host = host;
        this.mainHandler = mainHandler;
    }

    private boolean hasSupportedSystemUiPlayer() {
        return host.hasSupportedSystemUiPlayer();
    }

    private void deactivateSystemUiLyricModeAfterSurfaceHidden() {
        host.deactivateSystemUiLyricModeAfterSurfaceHidden();
    }

    private void ensureScreenTimeoutReceiver(Context context) {
        host.ensureScreenTimeoutReceiver(context);
    }

    private void info(BridgeDebugArea area, String event, String message) {
        host.info(area, event, message);
    }

    private void error(String message, Throwable throwable) {
        host.error(message, throwable);
    }

    void scheduleScreenTimeoutUserPresentRecheck(Context context) {
        if (screenTimeoutUserPresentRecheckPosted) {
            return;
        }
        screenTimeoutUserPresentRecheckPosted = true;
        Context appContext = applicationContextOf(context);
        mainHandler.postDelayed(() -> {
            screenTimeoutUserPresentRecheckPosted = false;
            recheckScreenTimeoutAfterUserPresent(appContext);
        }, SCREEN_TIMEOUT_USER_PRESENT_RECHECK_DELAY_MS);
    }

    private void recheckScreenTimeoutAfterUserPresent(Context context) {
        if (!screenTimeoutPausedByUserPresent) {
            updateScreenTimeoutWakeLock(context);
            return;
        }
        if (screenTimeoutPausedByScreenOff) {
            return;
        }
        if (isKeyguardShowingForScreenTimeout(context)) {
            screenTimeoutPausedByUserPresent = false;
            maybeLogScreenTimeout(
                    "Resumed screen timeout keep-awake after keyguard remained visible",
                    true);
            updateScreenTimeoutWakeLock(context);
            return;
        }
        deactivateSystemUiLyricModeAfterSurfaceHidden();
        releaseScreenTimeoutWakeLock("keyguard dismissed");
        maybeLogScreenTimeout("Stopped screen timeout keep-awake after keyguard dismissed", true);
    }

    void updateScreenTimeoutWakeLock(Context context) {
        if (!hasScreenTimeoutWakeLockBaseConditions(context)) {
            resetScreenTimeoutKeepAwakeWindow();
            maybeLogScreenTimeoutSkip(context);
            releaseScreenTimeoutWakeLock("conditions changed");
            return;
        }
        if (!ensureScreenTimeoutKeepAwakeWindowActive()) {
            maybeLogScreenTimeout("Skip screen timeout wake lock: custom timeout elapsed", false);
            releaseScreenTimeoutWakeLock("custom timeout elapsed");
            return;
        }
        if (context == null) {
            return;
        }
        ensureScreenTimeoutReceiver(context);
        try {
            Context appContext = context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            PowerManager powerManager = screenTimeoutPowerManager;
            if (powerManager == null) {
                powerManager =
                        (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
                if (powerManager == null) {
                    return;
                }
                screenTimeoutPowerManager = powerManager;
            }
            if (!powerManager.isInteractive()) {
                releaseScreenTimeoutWakeLock("screen not interactive");
                return;
            }
            PowerManager.WakeLock wakeLock = screenTimeoutWakeLock;
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                        TAG + ":ScreenTimeout");
                wakeLock.setReferenceCounted(false);
                screenTimeoutWakeLock = wakeLock;
            }
            boolean wasHeld = wakeLock.isHeld();
            long leaseMs = screenTimeoutWakeLockLeaseMs();
            if (!wasHeld) {
                wakeLock.acquire(leaseMs);
            }
            if (!wasHeld) {
                maybeLogScreenTimeout(
                        "Acquired bright screen timeout wake lock lease="
                                + leaseMs + "ms",
                        true);
                pulseScreenTimeoutUserActivity(powerManager, true);
            }
            scheduleScreenTimeoutUserActivityPulse();
        } catch (Throwable t) {
            error("Failed to update screen timeout wake lock", t);
        }
    }

    private boolean shouldHoldScreenTimeoutWakeLock(Context context) {
        return hasScreenTimeoutWakeLockBaseConditions(context)
                && isScreenTimeoutKeepAwakeWindowActive();
    }

    private boolean hasScreenTimeoutWakeLockBaseConditions(Context context) {
        return screenTimeoutSmartEnabled
                && host.isSystemUiLyricModeKeepAwakeActive()
                && !screenTimeoutPausedByScreenOff
                && !screenTimeoutPausedByUserPresent
                && hasSupportedSystemUiPlayer()
                && host.isLastPlaybackPlaying()
                && hasScreenTimeoutLyricEvidence()
                && isKeyguardShowingForScreenTimeout(context)
                && isScreenInteractiveForWakeLock();
    }

    private boolean ensureScreenTimeoutKeepAwakeWindowActive() {
        long customDurationMs = screenTimeoutCustomDurationMs();
        if (customDurationMs <= 0L) {
            resetScreenTimeoutKeepAwakeWindow();
            return true;
        }
        long now = SystemClock.elapsedRealtime();
        long until = screenTimeoutKeepAwakeUntilElapsedMs;
        if (until <= 0L) {
            screenTimeoutKeepAwakeUntilElapsedMs = now + customDurationMs;
            maybeLogScreenTimeout(
                    "Started custom screen timeout window duration=" + customDurationMs + "ms",
                    true);
            return true;
        }
        return now <= until;
    }

    private boolean isScreenTimeoutKeepAwakeWindowActive() {
        if (screenTimeoutCustomDurationMs() <= 0L) {
            return true;
        }
        long until = screenTimeoutKeepAwakeUntilElapsedMs;
        return until > 0L && SystemClock.elapsedRealtime() <= until;
    }

    private long screenTimeoutCustomDurationMs() {
        int seconds = LyricUiSettings.sanitizeScreenTimeoutSeconds(screenTimeoutCustomSeconds);
        return seconds <= 0 ? 0L : seconds * 1000L;
    }

    private long screenTimeoutWakeLockLeaseMs() {
        long customDurationMs = screenTimeoutCustomDurationMs();
        if (customDurationMs <= 0L) {
            return SCREEN_TIMEOUT_WAKE_LOCK_LEASE_MS;
        }
        long remainingMs = screenTimeoutKeepAwakeUntilElapsedMs - SystemClock.elapsedRealtime();
        if (remainingMs <= 0L) {
            return 1L;
        }
        return Math.min(SCREEN_TIMEOUT_WAKE_LOCK_LEASE_MS, Math.max(1L, remainingMs));
    }

    void resetScreenTimeoutKeepAwakeWindow() {
        screenTimeoutKeepAwakeUntilElapsedMs = 0L;
    }

    private boolean hasScreenTimeoutLyricEvidence() {
        return hasRecentVisibleOfficialLyricTextView()
                || hasRecentScreenTimeoutLyricModelEvidence();
    }

    private boolean hasRecentVisibleOfficialLyricTextView() {
        long lastVisibleAt = lastVisibleOfficialLyricTextViewAt;
        if (lastVisibleAt <= 0L) {
            return false;
        }
        long age = SystemClock.elapsedRealtime() - lastVisibleAt;
        return age >= 0L && age <= SCREEN_TIMEOUT_VISIBLE_LYRIC_VIEW_MAX_AGE_MS;
    }

    private boolean hasRecentScreenTimeoutLyricModelEvidence() {
        long until = screenTimeoutLyricEvidenceGraceUntilElapsedMs;
        return until > 0L && SystemClock.elapsedRealtime() <= until;
    }

    void markScreenTimeoutLyricModelEvidence() {
        screenTimeoutLyricEvidenceGraceUntilElapsedMs =
                SystemClock.elapsedRealtime() + SCREEN_TIMEOUT_MODEL_EVIDENCE_GRACE_MS;
    }

    void clearScreenTimeoutLyricEvidence() {
        lastVisibleOfficialLyricTextViewAt = 0L;
        screenTimeoutLyricEvidenceGraceUntilElapsedMs = 0L;
        resetScreenTimeoutKeepAwakeWindow();
    }

    void invalidateScreenTimeoutKeyguardCache() {
        screenTimeoutKeyguardCacheUntilElapsedMs = 0L;
    }

    boolean isScreenInteractiveForWakeLock() {
        PowerManager powerManager = screenTimeoutPowerManager;
        return powerManager == null || powerManager.isInteractive();
    }

    boolean isKeyguardShowingForScreenTimeout(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now <= screenTimeoutKeyguardCacheUntilElapsedMs) {
            return screenTimeoutKeyguardLockedCached;
        }
        Context appContext = applicationContextOf(context);
        if (appContext == null) {
            return false;
        }
        try {
            KeyguardManager keyguardManager = screenTimeoutKeyguardManager;
            if (keyguardManager == null) {
                keyguardManager =
                        (KeyguardManager) appContext.getSystemService(Context.KEYGUARD_SERVICE);
                screenTimeoutKeyguardManager = keyguardManager;
            }
            boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
            screenTimeoutKeyguardLockedCached = locked;
            screenTimeoutKeyguardCacheUntilElapsedMs =
                    now + SCREEN_TIMEOUT_KEYGUARD_STATE_CACHE_MS;
            return locked;
        } catch (Throwable t) {
            invalidateScreenTimeoutKeyguardCache();
            maybeLogScreenTimeout("Skip screen timeout wake lock: failed to read keyguard state", false);
            return false;
        }
    }

    private void maybeLogScreenTimeoutSkip(Context context) {
        if (!host.isSystemUiLyricModeKeepAwakeActive()) {
            return;
        }
        maybeLogScreenTimeout("Skip screen timeout wake lock: screenOff=" + screenTimeoutPausedByScreenOff
                + ", enabled=" + screenTimeoutSmartEnabled
                + ", userPresent=" + screenTimeoutPausedByUserPresent
                + ", supportedPlayer=" + hasSupportedSystemUiPlayer()
                + ", playing=" + host.isLastPlaybackPlaying()
                + ", hasModel=" + host.hasCurrentWordLyricModel()
                + ", hasOfficialLyric=" + host.hasSystemUiOfficialLyric()
                + ", recentOfficialView=" + hasRecentVisibleOfficialLyricTextView()
                + ", modelGrace=" + hasRecentScreenTimeoutLyricModelEvidence()
                + ", customSeconds=" + screenTimeoutCustomSeconds
                + ", customWindowActive=" + isScreenTimeoutKeepAwakeWindowActive()
                + ", keyguardShowing=" + isKeyguardShowingForScreenTimeout(context)
                + ", interactive=" + isScreenInteractiveForWakeLock(), false);
    }

    void releaseScreenTimeoutWakeLock(String reason) {
        stopScreenTimeoutUserActivityPulse();
        PowerManager.WakeLock wakeLock = screenTimeoutWakeLock;
        if (wakeLock == null) {
            return;
        }
        try {
            if (wakeLock.isHeld()) {
                wakeLock.release();
                maybeLogScreenTimeout("Released screen timeout wake lock: " + reason, true);
            }
        } catch (Throwable t) {
            error("Failed to release screen timeout wake lock", t);
        }
    }

    private void scheduleScreenTimeoutUserActivityPulse() {
        if (screenTimeoutUserActivityPulsePosted) {
            return;
        }
        screenTimeoutUserActivityPulsePosted = true;
        mainHandler.postDelayed(
                screenTimeoutUserActivityPulse,
                nextScreenTimeoutUserActivityPulseDelayMs());
    }

    private void stopScreenTimeoutUserActivityPulse() {
        if (!screenTimeoutUserActivityPulsePosted) {
            return;
        }
        mainHandler.removeCallbacks(screenTimeoutUserActivityPulse);
        screenTimeoutUserActivityPulsePosted = false;
    }

    private void renewScreenTimeoutWakeLockLease(PowerManager powerManager) {
        if (powerManager == null) {
            return;
        }
        PowerManager.WakeLock wakeLock = screenTimeoutWakeLock;
        if (wakeLock == null) {
            return;
        }
        try {
            boolean wasHeld = wakeLock.isHeld();
            long leaseMs = screenTimeoutWakeLockLeaseMs();
            wakeLock.acquire(leaseMs);
            if (!wasHeld) {
                maybeLogScreenTimeout(
                        "Re-acquired bright screen timeout wake lock lease="
                                + leaseMs + "ms",
                        true);
            }
        } catch (Throwable t) {
            error("Failed to renew screen timeout wake lock lease", t);
        }
    }

    private long nextScreenTimeoutUserActivityPulseDelayMs() {
        long customDurationMs = screenTimeoutCustomDurationMs();
        if (customDurationMs <= 0L) {
            return SCREEN_TIMEOUT_USER_ACTIVITY_INTERVAL_MS;
        }
        long remainingMs = screenTimeoutKeepAwakeUntilElapsedMs - SystemClock.elapsedRealtime();
        if (remainingMs <= 0L) {
            return 1L;
        }
        return Math.min(SCREEN_TIMEOUT_USER_ACTIVITY_INTERVAL_MS, Math.max(1L, remainingMs));
    }

    @SuppressLint("DiscouragedPrivateApi")
    private void pulseScreenTimeoutUserActivity(PowerManager powerManager, boolean forceLog) {
        if (powerManager == null) {
            return;
        }
        long uptime = SystemClock.uptimeMillis();
        Throwable firstFailure = null;
        try {
            Method userActivity = PowerManager.class.getDeclaredMethod(
                    "userActivity",
                    long.class,
                    int.class,
                    int.class);
            userActivity.setAccessible(true);
            userActivity.invoke(powerManager, uptime, 0, 1);
            maybeLogScreenTimeout("Pulsed screen timeout user activity without changing lights", forceLog);
            return;
        } catch (NoSuchMethodException ignored) {
            // Fall through to older framework signature.
        } catch (Throwable t) {
            firstFailure = t;
        }
        try {
            Method userActivity = PowerManager.class.getDeclaredMethod(
                    "userActivity",
                    long.class,
                    boolean.class);
            userActivity.setAccessible(true);
            userActivity.invoke(powerManager, uptime, true);
            maybeLogScreenTimeout("Pulsed screen timeout user activity without changing lights", forceLog);
        } catch (Throwable t) {
            if (!screenTimeoutUserActivityFailureLogged) {
                screenTimeoutUserActivityFailureLogged = true;
                if (firstFailure != null) {
                    error("Failed to pulse screen timeout user activity via int signature", firstFailure);
                }
                error("Failed to pulse screen timeout user activity", t);
            }
        }
    }

    void maybeLogScreenTimeout(String message, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastScreenTimeoutLogAt < 2_000L) {
            return;
        }
        lastScreenTimeoutLogAt = now;
        info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, message);
    }
}
