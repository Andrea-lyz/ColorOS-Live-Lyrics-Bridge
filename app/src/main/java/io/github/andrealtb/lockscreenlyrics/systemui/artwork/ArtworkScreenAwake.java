package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps the screen on while a motion cover visibly plays on the large cover. It owns a lock of its
 * own, separate from the lyric keep-awake: the screen stays on while either holds, and neither
 * releases the other. Same mechanism as the lyric keep-awake, proven on ColorOS since v1.7.1: a
 * short SCREEN_BRIGHT lease renewed together with a user-activity pulse, because a lock alone still
 * let the lock screen dim into AOD. A short grace keeps a one-frame geometry check from flapping it;
 * ON_AFTER_RELEASE lets the normal timeout start from the release.
 */
public final class ArtworkScreenAwake implements AutoCloseable {
    public interface Power {
        /** Acquires, or renews the lease of, the screen lock. */
        void hold(long leaseMs);
        void release();
        /** User activity that keeps the timeout from expiring without changing the lights. */
        void pulse();
    }
    public static final String PLAYING = "playing";
    static final long LEASE_MS = 15_000;
    static final long RENEW_MS = 8_000;
    static final long RELEASE_GRACE_MS = 1_000;
    private final Power power;
    private final ArtworkRetryWakeup.Scheduler scheduler;
    /** {@link #PLAYING}, or why the large cover is not playing a motion cover right now. */
    private final Supplier<String> state;
    private final Consumer<String> trace;
    private final Runnable renew = this::renew;
    private final Runnable graceElapsed = this::graceElapsed;
    private boolean enabled;
    private boolean held;
    private boolean releasing;
    private boolean closed;

    public ArtworkScreenAwake(Power power, ArtworkRetryWakeup.Scheduler scheduler, Supplier<String> state, Consumer<String> trace) {
        this.power = power;
        this.scheduler = scheduler;
        this.state = state;
        this.trace = trace;
    }

    public void setEnabled(boolean value) {
        if (closed || enabled == value) return;
        enabled = value;
        trace.accept("enabled=" + value);
        if (value) update();
        else release("disabled");
    }

    /** Cheap enough for every observation; acquires at once, releases only after the grace. */
    public void update() {
        if (closed || !enabled) return;
        String reason = state.get();
        if (PLAYING.equals(reason)) {
            cancelGrace();
            if (held) return;
            held = true;
            power.hold(LEASE_MS);
            power.pulse();
            scheduler.post(renew, RENEW_MS);
            trace.accept("state=held");
        } else if (held) {
            beginGrace();
        }
    }

    /** Screen off, unlock or teardown: no grace, the surface is already gone. */
    public void releaseNow(String reason) {
        if (!closed) release(reason);
    }

    @Override public void close() {
        if (closed) return;
        release("closed");
        closed = true;
    }

    private void renew() {
        if (closed || !held) return;
        if (enabled && PLAYING.equals(state.get())) {
            power.hold(LEASE_MS);
            power.pulse();
        } else {
            beginGrace();
        }
        scheduler.post(renew, RENEW_MS);
    }

    private void beginGrace() {
        if (releasing) return;
        releasing = true;
        scheduler.post(graceElapsed, RELEASE_GRACE_MS);
    }

    private void cancelGrace() {
        if (!releasing) return;
        releasing = false;
        scheduler.remove(graceElapsed);
    }

    private void graceElapsed() {
        releasing = false;
        if (closed || !held) return;
        String reason = state.get();
        if (enabled && PLAYING.equals(reason)) return;
        release(enabled ? reason : "disabled");
    }

    private void release(String reason) {
        scheduler.remove(renew);
        cancelGrace();
        if (!held) return;
        held = false;
        power.release();
        trace.accept("state=released reason=" + reason);
    }

    /** The device lock; any failure leaves the screen on its normal timeout rather than breaking playback. */
    public static Power device(Context context, Consumer<String> failure) {
        return new Power() {
            private PowerManager manager;
            private PowerManager.WakeLock lock;
            private boolean pulseFailed;

            @SuppressWarnings("deprecation")
            @Override public void hold(long leaseMs) {
                try {
                    if (lock == null) {
                        manager = context.getSystemService(PowerManager.class);
                        if (manager == null) return;
                        lock = manager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                                "LockscreenLyrics:ArtworkKeepAwake");
                        lock.setReferenceCounted(false);
                    }
                    lock.acquire(leaseMs);
                } catch (RuntimeException error) {
                    failure.accept("hold_failed errorType=" + ArtworkTrace.errorType(error));
                }
            }

            @Override public void release() {
                try { if (lock != null && lock.isHeld()) lock.release(); }
                catch (RuntimeException error) { failure.accept("release_failed errorType=" + ArtworkTrace.errorType(error)); }
            }

            @SuppressLint("DiscouragedPrivateApi")
            @Override public void pulse() {
                if (manager == null || pulseFailed) return;
                long now = SystemClock.uptimeMillis();
                try {
                    // userActivity(time, USER_ACTIVITY_EVENT_OTHER, USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS)
                    Method method = PowerManager.class.getDeclaredMethod("userActivity", long.class, int.class, int.class);
                    method.setAccessible(true);
                    method.invoke(manager, now, 0, 1);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    try {
                        Method legacy = PowerManager.class.getDeclaredMethod("userActivity", long.class, boolean.class);
                        legacy.setAccessible(true);
                        legacy.invoke(manager, now, true);
                    } catch (ReflectiveOperationException | RuntimeException fallback) {
                        pulseFailed = true;
                        failure.accept("pulse_failed errorType=" + ArtworkTrace.errorType(fallback));
                    }
                }
            }
        };
    }
}
