package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;
import io.github.andrealtb.lockscreenlyrics.diagnostics.SensitiveFieldRedactor;

import java.util.ArrayList;
import java.util.List;

/**
 * SystemUI MediaController binding (E-5a, plan §15.3): active-session discovery for the current
 * player, the single bound playback callback with its epoch and session-token checks, and the
 * binding lock. Moved verbatim from {@code LockscreenLyricsModule}; no method body changed.
 *
 * <p>Playback-state ingestion (the clock, the track-reset guard, position-jump observation) stays
 * in the entry class for now: it also drives the official-lyric handoff, renderer targets and the
 * recycler, which later E-5 steps and E-6/E-7 move.
 */
final class SystemUiPlaybackController {
    /** What the controller hands to, or asks of, the rest of the SystemUI runtime. */
    interface Host {
        void onBoundPlaybackStateChanged(PlaybackControllerBinding source, PlaybackState state);

        void rememberSystemUiPlaybackState(PlaybackState playbackState);

        void rememberMediaControllerPlaybackState(String packageName, Object controllerObject);

        void info(BridgeDebugArea area, String event, String message);

        void error(String message, Throwable throwable);
    }

    private final Host host;
    private final Handler mainHandler;
    final Object systemUiPlaybackControllerLock = new Object();
    PlaybackControllerBinding systemUiPlaybackControllerBinding;
    private long nextSystemUiPlaybackControllerEpoch;
    private volatile long lastPlaybackControllerDiscoveryElapsedMs = -1L;
    private volatile String lastPlaybackControllerDiscoveryPackage = "";

    SystemUiPlaybackController(Host host, Handler mainHandler) {
        this.host = host;
        this.mainHandler = mainHandler;
    }

    private void onBoundPlaybackStateChanged(PlaybackControllerBinding source, PlaybackState state) {
        host.onBoundPlaybackStateChanged(source, state);
    }

    private void rememberSystemUiPlaybackState(PlaybackState playbackState) {
        host.rememberSystemUiPlaybackState(playbackState);
    }

    private void rememberMediaControllerPlaybackState(String packageName, Object controllerObject) {
        host.rememberMediaControllerPlaybackState(packageName, controllerObject);
    }

    private void info(BridgeDebugArea area, String event, String message) {
        host.info(area, event, message);
    }

    private void error(String message, Throwable throwable) {
        host.error(message, throwable);
    }

    final class PlaybackControllerBinding {
        final MediaController controller;
        final Object token;
        final String packageName;
        final long epoch;
        final MediaController.Callback callback;

        PlaybackControllerBinding(
                MediaController controller,
                Object token,
                String packageName,
                long epoch) {
            this.controller = controller;
            this.token = token;
            this.packageName = packageName;
            this.epoch = epoch;
            this.callback = new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    onBoundPlaybackStateChanged(
                            PlaybackControllerBinding.this,
                            state);
                }

                @Override
                public void onSessionDestroyed() {
                    onBoundPlaybackSessionDestroyed(PlaybackControllerBinding.this);
                }
            };
        }

        boolean matches(MediaController candidate, Object candidateToken, String candidatePackage) {
            return packageName.equals(candidatePackage)
                    && (controller == candidate || sameSessionToken(token, candidateToken));
        }
    }

    private void onBoundPlaybackSessionDestroyed(PlaybackControllerBinding source) {
        synchronized (systemUiPlaybackControllerLock) {
            if (systemUiPlaybackControllerBinding == source) {
                systemUiPlaybackControllerBinding = null;
            }
        }
    }

    boolean isCurrentPlaybackControllerBinding(PlaybackControllerBinding source) {
        synchronized (systemUiPlaybackControllerLock) {
            return source != null
                    && systemUiPlaybackControllerBinding == source
                    && LockscreenIntegrationPolicy.isCurrentPlaybackControllerEvent(
                    source.epoch,
                    nextSystemUiPlaybackControllerEpoch,
                    source.packageName,
                    systemUiPlaybackControllerBinding == null
                            ? ""
                            : systemUiPlaybackControllerBinding.packageName,
                    systemUiPlaybackControllerBinding != null
                            && sameSessionToken(
                            source.token,
                            systemUiPlaybackControllerBinding.token));
        }
    }

    private static boolean sameSessionToken(Object left, Object right) {
        return left == right || (left != null && left.equals(right));
    }

    void scheduleSystemUiPlaybackControllerDiscovery(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        PlaybackControllerBinding existing;
        synchronized (systemUiPlaybackControllerLock) {
            existing = systemUiPlaybackControllerBinding;
        }
        if (existing != null && packageName.equals(existing.packageName)) {
            rememberSystemUiPlaybackState(existing.controller.getPlaybackState());
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (packageName.equals(lastPlaybackControllerDiscoveryPackage)
                && lastPlaybackControllerDiscoveryElapsedMs >= 0L
                && now - lastPlaybackControllerDiscoveryElapsedMs < 750L) {
            return;
        }
        lastPlaybackControllerDiscoveryPackage = packageName;
        lastPlaybackControllerDiscoveryElapsedMs = now;
        mainHandler.post(() -> discoverSystemUiPlaybackController(packageName));
    }

    @SuppressLint("MissingPermission")
    private void discoverSystemUiPlaybackController(String packageName) {
        try {
            PlaybackControllerBinding existing;
            synchronized (systemUiPlaybackControllerLock) {
                existing = systemUiPlaybackControllerBinding;
            }
            if (existing != null && packageName.equals(existing.packageName)) {
                rememberSystemUiPlaybackState(existing.controller.getPlaybackState());
                return;
            }
            Context context = currentApplicationContext();
            if (context == null) {
                return;
            }
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(
                    Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return;
            }
            List<MediaController> controllers = manager.getActiveSessions(null);
            ArrayList<String> packages = new ArrayList<>(controllers.size());
            for (MediaController controller : controllers) {
                packages.add(controller == null ? "" : controller.getPackageName());
            }
            int selectedIndex = LockscreenIntegrationPolicy.uniquePackageMatchIndex(
                    packages,
                    packageName);
            if (selectedIndex < 0 || selectedIndex >= controllers.size()) {
                info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL,
                        "Skipped playback controller discovery for package="
                                + packageName
                                + ", candidates=" + packages.size());
                return;
            }
            rememberMediaControllerPlaybackState(packageName, controllers.get(selectedIndex));
        } catch (Throwable t) {
            error("Failed to discover active MediaController for " + packageName, t);
        }
    }

    void ensureSystemUiPlaybackControllerCallback(
            String packageName,
            MediaController controller) {
        if (TextUtils.isEmpty(packageName) || controller == null) {
            return;
        }
        Object token;
        try {
            token = controller.getSessionToken();
        } catch (Throwable t) {
            error("Failed to read MediaController session token", t);
            return;
        }
        PlaybackControllerBinding previous;
        PlaybackControllerBinding candidate;
        synchronized (systemUiPlaybackControllerLock) {
            previous = systemUiPlaybackControllerBinding;
            if (previous != null && previous.matches(controller, token, packageName)) {
                return;
            }
            candidate = new PlaybackControllerBinding(
                    controller,
                    token,
                    packageName,
                    ++nextSystemUiPlaybackControllerEpoch);
            systemUiPlaybackControllerBinding = candidate;
        }

        if (previous != null) {
            try {
                previous.controller.unregisterCallback(previous.callback);
            } catch (Throwable ignored) {
                // A released/remote session may already have removed the callback.
            }
        }
        try {
            controller.registerCallback(candidate.callback, mainHandler);
        } catch (Throwable t) {
            synchronized (systemUiPlaybackControllerLock) {
                if (systemUiPlaybackControllerBinding == candidate) {
                    systemUiPlaybackControllerBinding = null;
                }
            }
            error("Failed to register MediaController playback callback", t);
            return;
        }
        if (!isCurrentPlaybackControllerBinding(candidate)) {
            try {
                controller.unregisterCallback(candidate.callback);
            } catch (Throwable ignored) {
                // A newer binding already owns playback events.
            }
            return;
        }
        info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED,
                "Registered MediaController playback callback for package=" + packageName
                        + ", epoch=" + candidate.epoch
                        + ", token=" + SensitiveFieldRedactor.trackHash(String.valueOf(token)));
    }

    void clearSystemUiPlaybackControllerBinding() {
        PlaybackControllerBinding previous;
        synchronized (systemUiPlaybackControllerLock) {
            previous = systemUiPlaybackControllerBinding;
            if (previous == null) {
                return;
            }
            systemUiPlaybackControllerBinding = null;
            nextSystemUiPlaybackControllerEpoch++;
        }
        try {
            previous.controller.unregisterCallback(previous.callback);
        } catch (Throwable ignored) {
            // Released sessions may already have removed the callback.
        }
    }
}
