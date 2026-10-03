package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Exact-token controllers shared by current attached hosts, independently of lyric availability. */
public final class ArtworkSessionRegistry implements AutoCloseable {
    private static final ThreadPoolExecutor SESSIONS = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), runnable -> new Thread(runnable, "bridge-artwork-sessions"));
    public record Binding(ArtworkRequestStamp stamp, ArtworkTrackIdentityPolicy.Metadata metadata,
            boolean ready, boolean playing, String reason) {}
    private record Key(int userId, String packageName, MediaSession.Token token) {}
    private final Handler main;
    private final ArtworkTrace trace = new ArtworkTrace("registry");
    private final Supplier<Context> context;
    private final Map<Object, Host> hosts = new IdentityHashMap<>();
    private final Map<Key, Session> sessions = new HashMap<>();
    private final Map<Key, Long> retryAfter = new HashMap<>();
    private List<ArtworkSessionAssociation.Entry<MediaSession.Token>> entries = List.of();
    private long sessionSequence;
    private long sourceSequence;
    private long sourceTime;
    private Supplier<List<ArtworkSessionAssociation.Entry<MediaSession.Token>>> sourceReader;

    public ArtworkSessionRegistry(Handler main, Supplier<Context> context) {
        this.main = main;
        this.context = context;
    }

    public void setSourceReader(Supplier<List<ArtworkSessionAssociation.Entry<MediaSession.Token>>> reader) {
        requireMain();
        sourceReader = reader;
    }

    public void updateSource(long sequence, List<ArtworkSessionAssociation.Entry<MediaSession.Token>> next) {
        requireMain();
        if (sequence <= sourceSequence) return;
        sourceSequence = sequence;
        sourceTime = SystemClock.elapsedRealtime();
        entries = next.size() <= 32 ? List.copyOf(next) : List.of();
        for (Host host : new ArrayList<>(hosts.values())) reconcile(host);
    }

    public void observeHost(Object owner, long pluginEpoch, long surfaceEpoch,
            ArtworkCardIdentity card, Consumer<Binding> listener) {
        Host known = hosts.get(owner);
        observeHost(owner, pluginEpoch, surfaceEpoch, card,
                known == null ? new ArtworkTrace("registry_host") : known.trace, listener);
    }

    public void observeHost(Object owner, long pluginEpoch, long surfaceEpoch,
            ArtworkCardIdentity card, ArtworkTrace diagnostic, Consumer<Binding> listener) {
        requireMain();
        Host host = hosts.get(owner);
        if (host == null) {
            if (hosts.size() >= 8) { listener.accept(new Binding(null, null, false, false, "host_budget")); return; }
            host = new Host(owner, pluginEpoch, surfaceEpoch, diagnostic, listener);
            hosts.put(owner, host);
        }
        host.card = card;
        reconcile(host);
    }

    public void detachHost(Object owner) {
        requireMain();
        Host host = hosts.remove(owner);
        if (host == null) return;
        unbind(host, "host_detached");
        main.removeCallbacks(host.stabilize);
        publish(host, "host_detached", null, false);
    }

    private void reconcile(Host host) {
        if (SystemClock.elapsedRealtime() - sourceTime > 8000 && sourceReader != null) {
            try {
                List<ArtworkSessionAssociation.Entry<MediaSession.Token>> fresh = sourceReader.get();
                if (fresh != null && fresh.size() <= 32) {
                    entries = List.copyOf(fresh);
                    sourceTime = SystemClock.elapsedRealtime();
                }
            } catch (RuntimeException error) { entries = List.of(); }
        }
        ArtworkSessionAssociation.Entry<MediaSession.Token> entry =
                SystemClock.elapsedRealtime() - sourceTime <= 10_000
                    ? ArtworkSessionAssociation.matchSession(host.card, entries) : null;
        host.trace.state("ARTWORK_ASSOCIATION_CHECK", () -> ArtworkAssociationDiagnostics.describe(host.card, entries)
                + " sourceFresh=" + (SystemClock.elapsedRealtime() - sourceTime <= 10_000));
        if (entry == null) {
            unbind(host, "association_unavailable");
            host.policy.invalidate();
            publish(host, "association_unavailable", null, false);
            return;
        }
        Key key = new Key(entry.userId(), entry.packageName(), entry.token());
        if (host.session == null || !host.session.key.equals(key)) {
            unbind(host, "session_key_changed");
            Session session = sessions.get(key);
            boolean reused = session != null;
            if (session == null) {
                if (retryAfter.getOrDefault(key, 0L) > SystemClock.elapsedRealtime()) {
                    host.policy.invalidate();
                    publish(host, "session_backoff", null, false);
                    return;
                }
                session = new Session(key, ++sessionSequence);
                sessions.put(key, session);
                start(session);
            }
            host.session = session;
            long boundEpoch = session.epoch;
            host.trace.state("ARTWORK_CONTROLLER_BOUND", () -> "sessionEpoch=" + boundEpoch
                    + " tokenRef=" + ArtworkTrace.tokenRef(key.token()) + " reused=" + reused
                    + " policyReset=true");
            host.policy = new ArtworkTrackIdentityPolicy(true);
        }
        Session session = host.session;
        if (host.policy.observeSource(entry.title(), entry.artist(), entry.durationMs())) {
            host.trace.state("ARTWORK_SOURCE_REPLACEMENT", () -> "sessionEpoch=" + session.epoch
                    + " sourceChanged=true freshConfirmationRequired=true");
        }
        if (!ArtworkSessionAssociation.matchesSong(host.card, entry)) {
            host.policy.invalidate();
            cancelStabilize(host);
            host.trace.state("ARTWORK_METADATA_CHECK", () -> "sessionEpoch=" + session.epoch
                    + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token()) + " reason=source_card_transition controllerRetained=true");
            publish(host, "source_card_transition", null, false);
            return;
        }
        ArtworkTrackIdentityPolicy.Metadata metadata = session.metadata;
        if (!session.active.get() || metadata == null
                || (entry.durationMs() > 0 && metadata.durationMs() > 0
                    && Math.abs(entry.durationMs() - metadata.durationMs()) > 3000)) {
            host.trace.state("ARTWORK_METADATA_CHECK", () -> "sessionEpoch=" + session.epoch
                    + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token())
                    + " reason=" + (!session.active.get() ? "session_inactive" : metadata == null ? "metadata_missing" : "duration_mismatch")
                    + " sourceDurationMs=" + entry.durationMs()
                    + " metadataDurationMs=" + (metadata == null ? 0 : metadata.durationMs()));
            host.policy.invalidate();
            publish(host, "metadata_unavailable", null, false);
            return;
        }
        host.trace.state("ARTWORK_METADATA_CHECK", () -> "sessionEpoch=" + session.epoch
                + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token())
                + " reason=" + host.policy.diagnosticReason(host.card, metadata, SystemClock.elapsedRealtime())
                + " mediaIdPresent=" + !metadata.mediaId().isEmpty()
                + " titleMatches=" + (host.card != null && host.card.title().equals(metadata.title()))
                + " artistMatches=" + (host.card != null && host.card.artist().equals(metadata.artist()))
                + " sourceDurationMs=" + entry.durationMs() + " metadataDurationMs=" + metadata.durationMs()
                + " fallbackAllowed=" + host.policy.stableFallbackAllowed());
        ArtworkTrackIdentityPolicy.Update update = host.policy.observe(host.card, metadata, SystemClock.elapsedRealtime());
        if (!update.ready()) {
            publish(host, "metadata_unstable", null, false);
            if (metadata.mediaId().isEmpty() && host.policy.stableFallbackAllowed()) {
                String pendingReason = host.policy.diagnosticReason(host.card, metadata, SystemClock.elapsedRealtime());
                if (pendingReason.equals("fallback_stabilizing") || pendingReason.equals("fallback_candidate_changed")
                        || pendingReason.equals("fallback_source_recheck_required")) {
                    if (!host.stabilizeScheduled && !host.recheckInFlight) {
                        host.stabilizeScheduled = true;
                        main.postDelayed(host.stabilize, 550);
                    }
                } else {
                    cancelStabilize(host);
                }
            }
            return;
        }
        cancelStabilize(host);
        boolean playing = session.state == PlaybackState.STATE_PLAYING;
        publish(host, "session_bound", new ArtworkRequestStamp(0, 0, host.pluginEpoch, host.surfaceEpoch,
                session.epoch, session.songs.generation(metadata), update.revision(), 0), playing);
    }

    private void start(Session session) {
        session.trace.state("ARTWORK_SESSION_CREATED", () -> "sessionEpoch=" + session.epoch
                + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token()));
        main.postDelayed(session.timeout, 5000);
        submit(() -> {
            if (!session.active.get()) return;
            try {
                Context application = context.get();
                if (application == null) throw new IllegalStateException("context_unavailable");
                MediaController controller = new MediaController(application, session.key.token());
                if (!session.key.packageName().equals(controller.getPackageName())) {
                    throw new SecurityException("controller_package_mismatch");
                }
                session.controller = controller;
                controller.registerCallback(session.callback, main);
                session.registered = true;
                session.trace.state("ARTWORK_CONTROLLER_REGISTERED", () -> "sessionEpoch=" + session.epoch
                        + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token()));
                MediaMetadata metadata = controller.getMetadata();
                PlaybackState playback = controller.getPlaybackState();
                if (!session.active.get()) {
                    controller.unregisterCallback(session.callback);
                    session.registered = false;
                    session.trace.state("ARTWORK_CONTROLLER_UNREGISTERED", () -> "sessionEpoch=" + session.epoch + " reason=late_initialization");
                    return;
                }
                ArtworkTrackIdentityPolicy.Metadata initial = metadata(metadata);
                main.post(() -> {
                    if (!current(session)) return;
                    main.removeCallbacks(session.timeout);
                    // A callback can arrive before this initial IPC snapshot. Never overwrite newer data.
                    if (!session.metadataObserved) session.metadata = initial;
                    if (!session.playbackObserved) session.state = playback == null ? PlaybackState.STATE_NONE : playback.getState();
                    session.trace.state("ARTWORK_CONTROLLER_INITIALIZED", () -> "sessionEpoch=" + session.epoch
                            + " metadataPresent=" + (session.metadata != null) + " playbackState=" + session.state
                            + " metadataCallbackFirst=" + session.metadataObserved + " playbackCallbackFirst=" + session.playbackObserved);
                    refresh(session);
                });
            } catch (Exception error) {
                session.trace.state("ARTWORK_CONTROLLER_ERROR", () -> "sessionEpoch=" + session.epoch
                        + " reason=initialization_failed errorType=" + ArtworkTrace.errorType(error));
                main.post(() -> unavailable(session));
            }
        }, () -> {
            session.trace.state("ARTWORK_CONTROLLER_ERROR", () -> "sessionEpoch=" + session.epoch + " reason=queue_budget");
            unavailable(session);
        });
    }

    /** A fallback's timed confirmation reads current controller metadata off-main, not cached values. */
    private void refreshStableMetadata(Host host) {
        if (hosts.get(host.owner) != host || host.session == null || !current(host.session)) return;
        Session session = host.session;
        MediaController controller = session.controller;
        if (controller == null) return;
        long version = session.metadataVersion;
        ArtworkCardIdentity card = host.card;
        ArtworkTrackIdentityPolicy policy = host.policy;
        host.recheckInFlight = true;
        long recheckEpoch = ++host.recheckEpoch;
        submit(() -> {
            if (!session.active.get()) return;
            try {
                long readAt = SystemClock.elapsedRealtime();
                ArtworkTrackIdentityPolicy.Metadata latest = metadata(controller.getMetadata());
                main.post(() -> {
                    if (recheckEpoch != host.recheckEpoch) return;
                    host.recheckInFlight = false;
                    if (!current(session) || hosts.get(host.owner) != host || host.session != session
                            || host.policy != policy) return;
                    if (!java.util.Objects.equals(card, host.card)) { reconcile(host); return; }
                    if (session.metadataVersion == version) {
                        session.metadata = latest;
                        session.metadataVersion++;
                    }
                    if (java.util.Objects.equals(latest, session.metadata)) policy.confirmFreshMetadata(latest, readAt);
                    host.trace.state("ARTWORK_FALLBACK_RECHECK", () -> "sessionEpoch=" + session.epoch
                            + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token()) + " metadataPresent=" + (session.metadata != null));
                    reconcile(host);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (recheckEpoch != host.recheckEpoch) return;
                    host.recheckInFlight = false;
                    if (!current(session) || hosts.get(host.owner) != host || host.session != session) return;
                    host.policy.invalidate();
                    host.trace.state("ARTWORK_CONTROLLER_ERROR", () -> "sessionEpoch=" + session.epoch
                            + " reason=fallback_recheck_failed errorType=" + ArtworkTrace.errorType(error));
                    publish(host, "metadata_unavailable", null, false);
                });
            }
        }, () -> {
            if (recheckEpoch != host.recheckEpoch) return;
            host.recheckInFlight = false;
            if (hosts.get(host.owner) == host && host.session == session) {
                host.policy.invalidate();
                publish(host, "session_backoff", null, false);
            }
        });
    }

    private void refresh(Session session) {
        for (Host host : new ArrayList<>(hosts.values())) if (host.session == session) reconcile(host);
    }

    private boolean current(Session session) {
        return session.active.get() && sessions.get(session.key) == session;
    }

    private void unavailable(Session session) {
        if (!current(session)) return;
        session.trace.state("ARTWORK_SESSION_UNAVAILABLE", () -> "sessionEpoch=" + session.epoch
                + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token()) + " registered=" + session.registered);
        retryAfter.entrySet().removeIf(entry -> entry.getValue() <= SystemClock.elapsedRealtime());
        if (retryAfter.size() < 32) retryAfter.put(session.key, SystemClock.elapsedRealtime() + 30_000);
        for (Host host : hosts.values()) {
            if (host.session != session) continue;
            host.session = null;
            host.recheckInFlight = false;
            ++host.recheckEpoch;
            cancelStabilize(host);
            host.policy.invalidate();
            publish(host, "session_unavailable", null, false);
        }
        stop(session, "session_unavailable");
    }

    private void unbind(Host host, String reason) {
        Session session = host.session;
        host.session = null;
        host.recheckInFlight = false;
        ++host.recheckEpoch;
        cancelStabilize(host);
        if (session == null) return;
        host.trace.state("ARTWORK_CONTROLLER_UNBOUND", () -> "sessionEpoch=" + session.epoch
                + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token()) + " reason=" + reason);
        for (Host other : hosts.values()) if (other.session == session) return;
        stop(session, reason);
    }

    private void stop(Session session, String reason) {
        if (!session.active.getAndSet(false)) return;
        session.trace.state("ARTWORK_SESSION_RELEASED", () -> "sessionEpoch=" + session.epoch
                + " tokenRef=" + ArtworkTrace.tokenRef(session.key.token()) + " reason=" + reason
                + " registered=" + session.registered);
        main.removeCallbacks(session.timeout);
        sessions.remove(session.key, session);
        submit(() -> {
            if (session.controller != null && session.registered) {
                try { session.controller.unregisterCallback(session.callback); } catch (RuntimeException ignored) {}
                session.registered = false;
                session.trace.state("ARTWORK_CONTROLLER_UNREGISTERED", () -> "sessionEpoch=" + session.epoch + " reason=" + reason);
            }
        }, () -> { /* No replacement lane: a blocked controller lane remains bounded. */ });
    }

    private void publish(Host host, String reason, ArtworkRequestStamp stamp, boolean playing) {
        Binding value = new Binding(stamp, stamp == null ? null : host.policy.accepted(),
                stamp != null, playing, reason);
        if (value.equals(host.last)) return;
        host.last = value;
        host.listener.accept(value);
    }

    @Override public void close() {
        requireMain();
        trace.state("ARTWORK_REGISTRY_CLOSED", () -> "hostCount=" + hosts.size() + " sessionCount=" + sessions.size());
        for (Object owner : new ArrayList<>(hosts.keySet())) detachHost(owner);
        for (Session session : new ArrayList<>(sessions.values())) stop(session, "registry_closed");
        entries = List.of();
        sourceTime = 0;
        retryAfter.clear();
    }

    private void submit(Runnable work, Runnable rejected) {
        try { SESSIONS.execute(work); }
        catch (java.util.concurrent.RejectedExecutionException error) { main.post(rejected); }
    }

    private static ArtworkTrackIdentityPolicy.Metadata metadata(MediaMetadata value) {
        if (value == null) return null;
        return new ArtworkTrackIdentityPolicy.Metadata(value.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                value.getString(MediaMetadata.METADATA_KEY_TITLE), value.getString(MediaMetadata.METADATA_KEY_ARTIST),
                value.getString(MediaMetadata.METADATA_KEY_ALBUM), value.getLong(MediaMetadata.METADATA_KEY_DURATION),
                value.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE), value.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
    }

    private static void requireMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("owner_thread");
    }

    private final class Host {
        final Object owner;
        final long pluginEpoch;
        final long surfaceEpoch;
        final Consumer<Binding> listener;
        final ArtworkTrace trace;
        final Runnable stabilize;
        ArtworkCardIdentity card;
        ArtworkTrackIdentityPolicy policy = new ArtworkTrackIdentityPolicy();
        Session session;
        Binding last;
        boolean stabilizeScheduled;
        boolean recheckInFlight;
        long recheckEpoch;

        Host(Object owner, long pluginEpoch, long surfaceEpoch, ArtworkTrace trace, Consumer<Binding> listener) {
            this.owner = owner;
            this.pluginEpoch = pluginEpoch;
            this.surfaceEpoch = surfaceEpoch;
            this.listener = listener;
            this.trace = trace;
            stabilize = () -> { stabilizeScheduled = false; refreshStableMetadata(this); };
        }
    }

    private void cancelStabilize(Host host) {
        main.removeCallbacks(host.stabilize);
        host.stabilizeScheduled = false;
    }

    private final class Session {
        final Key key;
        final long epoch;
        final AtomicBoolean active = new AtomicBoolean(true);
        final ArtworkTrackIdentityPolicy.SessionSongs songs = new ArtworkTrackIdentityPolicy.SessionSongs();
        final ArtworkTrace trace = new ArtworkTrace("session");
        final Runnable timeout;
        volatile MediaController controller;
        volatile boolean registered;
        ArtworkTrackIdentityPolicy.Metadata metadata;
        int state;
        boolean metadataObserved;
        boolean playbackObserved;
        long metadataVersion;
        final MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata next) {
                if (!current(Session.this)) {
                    trace.state("ARTWORK_STALE_DROPPED", () -> "sessionEpoch=" + epoch + " phase=metadata_callback");
                    return;
                }
                metadataObserved = true;
                metadataVersion++;
                try { metadata = metadata(next); }
                catch (RuntimeException error) {
                    metadata = null;
                    trace.state("ARTWORK_CONTROLLER_ERROR", () -> "sessionEpoch=" + epoch + " phase=metadata_decode errorType=" + ArtworkTrace.errorType(error));
                }
                trace.state("ARTWORK_METADATA_CALLBACK", () -> "sessionEpoch=" + epoch + " metadataPresent="
                        + (metadata != null) + " mediaIdPresent=" + (metadata != null && !metadata.mediaId().isEmpty()));
                refresh(Session.this);
            }
            @Override public void onPlaybackStateChanged(PlaybackState next) {
                if (!current(Session.this)) {
                    trace.state("ARTWORK_STALE_DROPPED", () -> "sessionEpoch=" + epoch + " phase=playback_callback");
                    return;
                }
                playbackObserved = true;
                state = next == null ? PlaybackState.STATE_NONE : next.getState();
                trace.state("ARTWORK_PLAYBACK_CALLBACK", () -> "sessionEpoch=" + epoch + " playbackState=" + state);
                refresh(Session.this);
            }
            @Override public void onSessionDestroyed() {
                trace.state("ARTWORK_SESSION_DESTROYED", () -> "sessionEpoch=" + epoch + " tokenRef=" + ArtworkTrace.tokenRef(key.token()));
                unavailable(Session.this);
            }
        };

        Session(Key key, long epoch) {
            this.key = key;
            this.epoch = epoch;
            timeout = () -> {
                trace.state("ARTWORK_SESSION_TIMEOUT", () -> "sessionEpoch=" + this.epoch + " registered=" + registered);
                unavailable(this);
            };
        }
    }
}
