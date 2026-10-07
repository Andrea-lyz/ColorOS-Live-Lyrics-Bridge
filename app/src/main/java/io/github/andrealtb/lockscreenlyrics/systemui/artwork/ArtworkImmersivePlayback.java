package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;
import android.widget.ImageView;
import io.github.andrealtb.artwork.contract.ArtworkContract;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import java.util.List;

/** One decoder shared by verified card/immersive hosts; explicit debug fixture or live-query integration. */
public final class ArtworkImmersivePlayback implements AutoCloseable {
    public record Candidate(ImageView image, ArtworkSessionRegistry.Binding binding,
            ArtworkPlaybackPolicy.Surface surface, ArtworkCardEffectAccess cardEffects,
            ArtworkC17Access.Bound c17, boolean background) {
        public Candidate(ImageView image, ArtworkSessionRegistry.Binding binding,
                ArtworkPlaybackPolicy.Surface surface, ArtworkCardEffectAccess cardEffects) {
            this(image, binding, surface, cardEffects, null, false);
        }
    }
    private final Context context;
    private final ArtworkProviderClient client;
    private final ArtworkDrawableTransition transition;
    private final ArtworkTrace trace = new ArtworkTrace("immersive_test");
    private ArtworkPlaybackConfig config;
    /** Last display outcome for the settings page: status codes only, never song text. */
    private String outcome = "waiting";
    private long outcomeAt = android.os.SystemClock.elapsedRealtime();
    private Candidate current;
    private ArtworkRequestStamp attempted;
    private ImageView attemptedImage;
    private ArtworkVideoMount mount;
    private DynamicArtworkRenderer renderer;
    private Candidate renderCandidate;
    private List<Candidate> latestCandidates = List.of();
    private long surfaceHoldSince = -1;
    private final Runnable holdTimeout = this::expireHold;
    /** A two-source cold request can legitimately exceed ten seconds; share the IPC request's bounded budget. */
    static final long RESOLVE_STALL_MS = ArtworkResolveLifetime.ONLINE_TIMEOUT_MS;
    private static final int MAX_STALL_RESTARTS = 2;
    private final Runnable resolveStall = this::resolveStalled;
    private ArtworkRequestStamp stallSong;
    private int stallRestarts;
    /** Host size the playing asset was requested for; travels with the decoder across handovers. */
    private int playingForPx;
    private long clientEpoch;
    private boolean screenBlocked;
    private boolean closed;
    private long retryAt = Long.MAX_VALUE;
    private long retryDelay = Long.MAX_VALUE;
    private final ArtworkRetryWakeup wakeup;
    private final ArtworkScreenAwake keepAwake;
    private final Runnable refreshDisplay;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final ArtworkNetworkRecovery networkRecovery = new ArtworkNetworkRecovery();
    private android.net.ConnectivityManager connectivity;
    private boolean networkRegistered;
    private boolean awaitingNetwork;
    private boolean resolvingResource;
    private Candidate resolvingCandidate;
    private long resolvingSince;
    private boolean networkRecoveryPending;
    private final Runnable networkPulse = this::networkRecovered;
    private void networkRecovered() {
        if (closed || config.localFixture() || current == null || (!awaitingNetwork && !resolvingResource)) return;
        if (!contextEligible()) return;
        trace.state("ARTWORK_NETWORK_RECOVERED", () -> "retryCurrent=true");
        wakeup.cancel(); stop(); attempted = null; attemptedImage = null; retryAt = Long.MAX_VALUE; awaitingNetwork = false; networkRecoveryPending = false;
        refreshDisplay.run();
    }
    private final android.net.ConnectivityManager.NetworkCallback networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
        @Override public void onCapabilitiesChanged(android.net.Network network, android.net.NetworkCapabilities caps) { readNetwork(); }
        @Override public void onLost(android.net.Network network) { readNetwork(); }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            screenBlocked = !Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            if (!screenBlocked) return;
            keepAwake.releaseNow(Intent.ACTION_USER_PRESENT.equals(intent.getAction()) ? "unlocked" : "screen_off");
            // Screen-off hides the layer and drops a finished player; an issued download keeps running.
            wakeup.cancel(); suspendVisual(); awaitingNetwork = false; networkRecoveryPending = false;
            if (!resolvingResource) { stop(); attempted = null; attemptedImage = null; }
        }
    };

    public ArtworkImmersivePlayback(Context context, ArtworkDrawableTransition transition,
            ArtworkPlaybackConfig config, Runnable refreshDisplay) {
        Context application = context.getApplicationContext();
        this.context = application == null ? context : application;
        this.transition = transition;
        this.config = config;
        this.refreshDisplay = refreshDisplay;
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        ArtworkRetryWakeup.Scheduler scheduler = new ArtworkRetryWakeup.Scheduler() {
            @Override public void post(Runnable task, long delayMs) { handler.postDelayed(task, delayMs); }
            @Override public void remove(Runnable task) { handler.removeCallbacks(task); }
        };
        wakeup = new ArtworkRetryWakeup(scheduler);
        keepAwake = new ArtworkScreenAwake(ArtworkScreenAwake.device(this.context,
                failure -> trace.state("ARTWORK_KEEP_AWAKE", () -> failure)), scheduler,
                this::keepAwakeState, state -> trace.state("ARTWORK_KEEP_AWAKE", () -> state));
        keepAwake.setEnabled(config.keepAwake());
        client = new ArtworkProviderClient(this.context);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) this.context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else this.context.registerReceiver(screen, filter);
        if (!config.localFixture()) {
            connectivity = this.context.getSystemService(android.net.ConnectivityManager.class);
            if (connectivity != null && this.context.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) try {
                readNetwork();
                connectivity.registerDefaultNetworkCallback(networkCallback, main);
                networkRegistered = true;
            } catch (RuntimeException error) {
                trace.state("ARTWORK_NETWORK_WATCH", () -> "available=false errorType=" + ArtworkTrace.errorType(error));
            }
        }
    }

    public void observe(List<Candidate> candidates) {
        try { select(candidates); }
        finally { keepAwake.update(); }
    }

    /** Debug keep-awake choice, changed without restarting the running test. */
    public void setKeepAwake(boolean enabled) {
        if (!closed) keepAwake.setEnabled(enabled);
    }

    /** {@link ArtworkScreenAwake#PLAYING} only while the motion cover is visibly playing on the large cover. */
    private String keepAwakeState() {
        if (closed) return "closed";
        if (renderer == null || !renderer.isShowing()) return "no_video";
        if (renderCandidate == null || renderCandidate.surface() != ArtworkPlaybackPolicy.Surface.IMMERSIVE) return "card_surface";
        if (!ArtworkScreenAwake.coverSurface(true, renderCandidate.background(),
                renderCandidate.c17() != null && renderCandidate.c17().coverMode())) return "lyric_background_or_unknown";
        if (surfaceHoldSince >= 0) return "surface_switch";
        if (current == null || current.image() != renderCandidate.image()) return "not_current";
        String reason = eligibilityReason(current);
        return reason.equals("eligible") ? ArtworkScreenAwake.PLAYING : reason;
    }

    private void select(List<Candidate> candidates) {
        if (closed) return;
        latestCandidates = List.copyOf(candidates);
        Candidate selected = null;
        int cardCount = 0;
        int immersiveCount = 0;
        // Aggregated per surface: per-candidate reasons alternate every frame, which would
        // defeat de-duplication and starve the shared diagnostic budget.
        java.util.Map<String, Integer> immersiveReasons = new java.util.TreeMap<>();
        java.util.Map<String, Integer> cardReasons = new java.util.TreeMap<>();
        for (Candidate candidate : candidates) {
            String reason = eligibilityReason(candidate);
            (candidate.surface() == ArtworkPlaybackPolicy.Surface.IMMERSIVE ? immersiveReasons : cardReasons)
                    .merge(reason, 1, Integer::sum);
            if (!reason.equals("eligible")) continue;
            if (candidate.surface() == ArtworkPlaybackPolicy.Surface.IMMERSIVE) immersiveCount++;
            else cardCount++;
        }
        gateSummary("IMMERSIVE", immersiveReasons);
        gateSummary("LOCKSCREEN_CARD", cardReasons);
        ArtworkPlaybackPolicy.Surface preferred = ArtworkPlaybackPolicy.selectUnique(cardCount, immersiveCount);
        int eligibleCards = cardCount;
        int eligibleLargeCovers = immersiveCount;
        trace.state("ARTWORK_TEST_SELECTION", () -> "eligibleCards=" + eligibleCards + " eligibleLargeCovers="
                + eligibleLargeCovers + " selected=" + (preferred == null ? "none" : preferred));
        if (preferred != null) for (Candidate candidate : candidates) {
            if (candidate.surface() == preferred && eligible(candidate)) { selected = candidate; break; }
        }
        Candidate previous = current;
        current = selected;
        trace.state("ARTWORK_TEST_GATE", () -> "candidateCount=" + candidates.size() + " eligible=" + (current != null)
                + (candidates.size() != 1 ? "" : " ready=" + candidates.get(0).binding().ready()
                    + " playing=" + candidates.get(0).binding().playing()
                    + " surface=" + candidates.get(0).surface()
                    + " transitionComplete=" + transitionReady(candidates.get(0))
                    + " width=" + candidates.get(0).image().getWidth() + " height=" + candidates.get(0).image().getHeight()
                    + " alpha=" + candidates.get(0).image().getAlpha() + " scaleX=" + candidates.get(0).image().getScaleX()));
        if (selected == null) {
            if (holdSurface()) return;
            // An issued download is not tied to the current display state: it finishes and reaches the
            // cache, and is shown again once the surface returns. Only a gone surface or song abandons it.
            if (resolvingResource) {
                boolean targetPresent = resolveTargetPresent(candidates);
                // A keyguard transition can momentarily detach every host view. The bounded request keeps
                // running, lands in the provider cache and is re-delivered when a surface shows it again.
                boolean surfaceAbsent = candidates.isEmpty() && android.os.SystemClock.elapsedRealtime() - resolvingSince
                        < ArtworkResolveLifetime.ONLINE_TIMEOUT_MS;
                if (targetPresent || surfaceAbsent) {
                    if (renderer != null || mount != null) suspendVisual();
                    trace.state("ARTWORK_RESOURCE_WAIT", () -> "sameRecording=" + targetPresent
                            + " surfaceAbsent=" + surfaceAbsent + " visualSuspended=true");
                    return;
                }
            }
            boolean retain = retryAt != Long.MAX_VALUE && contextEligible() && candidates.stream().anyMatch(candidate ->
                    candidate.image() == attemptedImage && candidate.image().isAttachedToWindow() && candidate.binding().ready()
                            && candidate.binding().playing() && candidate.binding().stamp().equals(attempted));
            if (!retain) {
                wakeup.cancel(); awaitingNetwork = false; networkRecoveryPending = false; attempted = null; attemptedImage = null;
            }
            trace.state("ARTWORK_RETRY_CONTEXT", () -> "retained=" + retain + " candidateCount=" + candidates.size());
            stop(); return;
        }
        if (networkRecoveryPending && awaitingNetwork) {
            trace.state("ARTWORK_NETWORK_RECOVERED", () -> "retryCurrent=true phase=eligible_again");
            wakeup.cancel(); attempted = null; attemptedImage = null; retryAt = Long.MAX_VALUE; networkRecoveryPending = false;
        }
        if (surfaceHoldSince >= 0 && renderer != null && renderer.isPlaying() && mount != null
                && renderCandidate != null && selected.image() == renderCandidate.image()
                && ArtworkResolveLifetime.sameSong(selected.binding().stamp(), attempted)) {
            Candidate target = selected;
            if (renderer.retain(displayGuard(target))) {
                clearHold();
                renderCandidate = target;
                attempted = target.binding().stamp(); attemptedImage = target.image();
                try { mount.sync(); mount.show(true); } catch (Exception error) { failure("geometry_changed"); }
                return;
            }
        }
        if (mount != null && previous != null && selected.image() == previous.image()
                && selected.binding().stamp().equals(previous.binding().stamp())) {
            try { mount.sync(); }
            catch (Exception error) { failure("geometry_changed"); }
            return;
        }
        if ((renderer == null || !renderer.isPlaying())
                && selected.image() == attemptedImage && selected.binding().stamp().equals(attempted)
                && android.os.SystemClock.elapsedRealtime() < retryAt) return;
        wakeup.cancel();
        // The song is already playing here: move the warm decoder onto the settled new surface instead
        // of resolving and preparing again. The native static cover covers the transition itself.
        if (renderer != null && renderer.isPlaying() && attempted != null
                && !ArtworkResolveLifetime.sameSong(selected.binding().stamp(), attempted)) {
            Candidate target = selected;
            trace.state("ARTWORK_SURFACE_HANDOVER", () -> "surface=" + target.surface() + " skipped=different_song");
        }
        // Device log: a video fetched for the small card, handed to the large cover, played upscaled and
        // blurry. A larger host than the asset was requested for gets its own request instead.
        boolean upgrade = renderer != null && renderer.isPlaying() && attempted != null
                && ArtworkResolveLifetime.sameSong(selected.binding().stamp(), attempted)
                && requestPx(selected.image()) > playingForPx;
        if (upgrade) {
            Candidate target = selected;
            int playing = playingForPx;
            trace.state("ARTWORK_SURFACE_HANDOVER", () -> "surface=" + target.surface() + " skipped=resolution_upgrade"
                    + " playingForPx=" + playing + " neededPx=" + requestPx(target.image()));
        }
        if (!upgrade && renderer != null && renderer.isPlaying() && attempted != null
                && ArtworkResolveLifetime.sameSong(selected.binding().stamp(), attempted)) {
            Candidate target = selected;
            ArtworkVideoMount held = mount;
            ArtworkVideoMount next = null;
            try {
                next = createMount(target);
                next.show(true);
                ArtworkRenderGuard guard = displayGuard(target);
                if (renderer.handOver(next.texture(), next.fitXY(), guard,
                        () -> { if (held != null) { try { held.close(); } catch (RuntimeException ignored) { } } })) {
                    mount = next;
                    clearHold();
                    renderCandidate = target;
                    attempted = target.binding().stamp();
                    attemptedImage = target.image();
                    trace.state("ARTWORK_SURFACE_HANDOVER", () -> "surface=" + target.surface() + " warmDecoder=true");
                    return;
                }
            } catch (Exception error) {
                trace.state("ARTWORK_SURFACE_HANDOVER", () -> "fallback=true errorType=" + ArtworkTrace.errorType(error));
            }
            if (next != null) { try { next.close(); } catch (RuntimeException ignored) { } }
        }
        if (resolvingResource && resolvingCandidate != null
                && ArtworkResolveLifetime.retainForSameTarget(resolvingCandidate.binding().stamp(), resolvingCandidate.binding().metadata(),
                        selected.binding().stamp(), selected.binding().metadata(), android.os.SystemClock.elapsedRealtime() - resolvingSince)) {
            // Same recording on the other surface: work already triggered must still reach the cache.
            suspendVisual();
            trace.state("ARTWORK_REQUEST_RETAINED", () -> "sameRecording=true surfaceSwitched=true");
            return;
        }
        stop();
        attempted = selected.binding().stamp();
        attemptedImage = selected.image();
        retryDelay = Long.MAX_VALUE;
        retryAt = Long.MAX_VALUE;
        awaitingNetwork = false;
        networkRecoveryPending = false;
        long requestEpoch = ++clientEpoch;
        Candidate request = selected;
        try {
            mountCandidate(request, requestEpoch);
            // The fixed fixture deliberately receives no real song or media/session identity.
            ArtworkQuery query = config.localFixture() ? new ArtworkQuery("Local artwork mounting test", "", "", 0, "",
                    Math.min(selected.image().getWidth(), ArtworkContract.MAX_RESOLUTION),
                    Math.min(selected.image().getHeight(), ArtworkContract.MAX_RESOLUTION),
                    ArtworkContract.MAX_RESOLUTION, ArtworkContract.MAX_RESOLUTION, ArtworkContract.MAX_FILE_BYTES)
                    : ArtworkLiveQuery.from(selected.binding().metadata(), selected.image().getWidth(), selected.image().getHeight());
            int requestedPx = Math.max(query.displayWidthPx, query.displayHeightPx);
            resolvingResource = true;
            resolvingCandidate = request;
            resolvingSince = android.os.SystemClock.elapsedRealtime();
            main.removeCallbacks(resolveStall);
            if (!config.localFixture()) main.postDelayed(resolveStall, RESOLVE_STALL_MS);
            client.resolveSelected(config.component(), config.signer(), query, config.localFixture(), new ArtworkProviderClient.Listener() {
                @Override public void onResult(io.github.andrealtb.artwork.contract.ArtworkResult.Status status, String reason, long retryAfterMs) {
                    if (requestEpoch != clientEpoch) return;
                    resolvingResource = false;
                    main.removeCallbacks(resolveStall);
                    awaitingNetwork = ArtworkNetworkRecovery.transport(reason);
                    trace.state("ARTWORK_PROVIDER_RESULT", () -> "status=" + status + " reason=" + reason + " retryAfterMs=" + retryAfterMs);
                    outcome(status.name().toLowerCase(java.util.Locale.ROOT) + ":" + reason);
                    if (!config.localFixture()) retryDelay = switch (status) {
                        case RETRY_LATER -> Math.max(1000, retryAfterMs);
                        case NETWORK_BLOCKED -> 30_000;
                        case NO_MOTION -> 86_400_000;
                        default -> Long.MAX_VALUE;
                    };
                }
                @Override public void onState(String reason) {
                    if (requestEpoch != clientEpoch) return;
                    trace.state("ARTWORK_TEST_RESOURCE_STATE", () -> "surface=" + request.surface() + " state=" + reason + " clientEpoch=" + requestEpoch);
                    if (!reason.equals("selecting") && !reason.equals("connecting") && !reason.equals("resolving")) failure(reason);
                }
                @Override public void onAsset(ArtworkProviderClient.OpenedAsset asset) {
                    // A keyguard remount replaces the host view while the same recording keeps playing; the
                    // finished asset is handed to the surface that exists now instead of being discarded.
                    Candidate target = request;
                    if (!matches(request, requestEpoch)) {
                        Candidate replacement = current;
                        boolean sameRecording = replacement != null && replacement.binding().stamp() != null
                                && !ArtworkResolveLifetime.abandoned(eligibilityReason(replacement))
                                && ArtworkResolveLifetime.sameTarget(request.binding().stamp(), request.binding().metadata(),
                                        replacement.binding().stamp(), replacement.binding().metadata());
                        if (!sameRecording) {
                            asset.close();
                            trace.state("ARTWORK_ASSET_DROPPED", () -> "phase=mount_gate currentEpoch=" + (requestEpoch == clientEpoch));
                            if (requestEpoch == clientEpoch) { retryDelay = 1000; failure("asset_gate_changed"); }
                            return;
                        }
                        target = replacement;
                        Candidate retarget = target;
                        trace.state("ARTWORK_ASSET_RETARGETED", () -> "surface=" + retarget.surface() + " remount=true");
                    }
                    resolvingResource = false; resolvingCandidate = null; awaitingNetwork = false; networkRecoveryPending = false;
                    main.removeCallbacks(resolveStall); stallRestarts = 0;
                    try {
                        if (renderer == null || renderCandidate != target) {
                            if (renderer != null || mount != null) suspendVisual();
                            mountCandidate(target, requestEpoch);
                        }
                    }
                    catch (Exception error) { asset.close(); failure("mount_unsupported"); return; }
                    ArtworkRequestStamp stamp = stamp(target, requestEpoch);
                    playingForPx = requestedPx;
                    renderer.play(asset, stamp, () -> current == null ? null : stamp(current, clientEpoch),
                            () -> current != null && eligible(current));
                }
            });
            trace.state("ARTWORK_TEST_MOUNTED", () -> "surface=" + request.surface() + " clientEpoch=" + requestEpoch
                    + " shape=" + (request.surface() == ArtworkPlaybackPolicy.Surface.IMMERSIVE
                        ? "native_path_and_outline" : "native_shader_alpha") + " mode=" + (config.localFixture() ? "local_fixture" : "live_query"));
        } catch (Exception error) {
            trace.state("ARTWORK_TEST_MOUNT_REJECTED", () -> "reason="
                    + (error instanceof ArtworkImmersiveMount.Unsupported unsupported ? unsupported.reason : "mount_unsupported")
                    + " errorType=" + ArtworkTrace.errorType(error));
            failure("mount_unsupported");
        }
    }

    public void invalidate(ImageView image) {
        boolean touched = current != null && current.image() == image || resolvingCandidate != null && resolvingCandidate.image() == image
                || renderCandidate != null && renderCandidate.image() == image;
        if (!touched) return;
        if (resolvingResource && resolvingCandidate != null) {
            // The host view is gone, but the recording did not change: the bounded download keeps running
            // and is handed to the next surface that shows the same recording.
            suspendVisual();
            trace.state("ARTWORK_REQUEST_ORPHANED", () -> "invalidate=true downloadContinues=true");
            attempted = null;
            attemptedImage = null;
            return;
        }
        wakeup.cancel();
        stop();
        attempted = null;
        attemptedImage = null;
    }

    void invalidateC17(ArtworkC17Access.Bound host) {
        if (current != null && current.c17() == host) invalidate(current.image());
        else if (renderCandidate != null && renderCandidate.c17() == host) invalidate(renderCandidate.image());
        else if (resolvingCandidate != null && resolvingCandidate.c17() == host) invalidate(resolvingCandidate.image());
    }

    private boolean matches(Candidate request, long epoch) {
        return !closed && epoch == clientEpoch && current != null
                && request.image() == current.image() && request.binding().stamp().equals(current.binding().stamp())
                && eligible(current);
    }
    /**
     * The recording an issued download belongs to is still shown by some surface, even after a keyguard
     * remount re-created the host view or the media session. Only a different recording abandons it.
     */
    private boolean resolveTargetPresent(List<Candidate> candidates) {
        if (closed || !config.enabled() || resolvingCandidate == null) return false;
        for (Candidate candidate : candidates) {
            if (!ArtworkResolveLifetime.sameTarget(resolvingCandidate.binding().stamp(), resolvingCandidate.binding().metadata(),
                    candidate.binding().stamp(), candidate.binding().metadata())) continue;
            if (ArtworkResolveLifetime.abandoned(eligibilityReason(candidate))) continue;
            return true;
        }
        return false;
    }
    private void gateSummary(String surface, java.util.Map<String, Integer> reasons) {
        trace.state("ARTWORK_GATE_" + surface, () -> {
            int total = 0;
            StringBuilder text = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> entry : reasons.entrySet()) {
                total += entry.getValue();
                text.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
            return "total=" + total + text;
        });
    }
    private void mountCandidate(Candidate request, long epoch) throws Exception {
        renderCandidate = request;
        mount = createMount(request);
        renderer = new DynamicArtworkRenderer(mount.texture(), reason -> {
            if (epoch != clientEpoch) return;
            trace.state("ARTWORK_TEST_RENDER_STATE", () -> "surface="
                    + (renderCandidate == null ? request.surface() : renderCandidate.surface())
                    + " state=" + reason + " clientEpoch=" + epoch);
            if (reason.equals("stale_render") && surfaceHoldSince >= 0) { attempted = null; attemptedImage = null; }
            if (!reason.equals("preparing") && !reason.equals("waiting_first_frame") && !reason.equals("playing")) failure(reason);
            if (reason.equals("playing")) outcome("played");
            keepAwake.update();
        }, mount.fitXY());
        mount.show(true);
    }
    private ArtworkVideoMount createMount(Candidate request) throws Exception {
        if (request.c17() != null) {
            if (Build.VERSION.SDK_INT < 33) throw new ArtworkImmersiveMount.Unsupported("c17_shader_platform");
            return new ArtworkC17Mount(request.image(), request.background(), () -> {
                invalidate(request.image());
                refreshDisplay.run();
            });
        }
        if (request.surface() == ArtworkPlaybackPolicy.Surface.IMMERSIVE) return new ArtworkImmersiveMount(request.image());
        if (Build.VERSION.SDK_INT < 33) throw new ArtworkImmersiveMount.Unsupported("card_shader_platform");
        return new ArtworkCardMount(request.image(), request.cardEffects());
    }

    private ArtworkRenderGuard displayGuard(Candidate target) {
        return new ArtworkRenderGuard(stamp(target, clientEpoch),
                () -> current == null ? null : stamp(current, clientEpoch),
                () -> current != null && eligible(current));
    }

    private boolean holdEligible() {
        if (!contextEligible() || renderCandidate == null || surfaceHoldSince < 0) return false;
        long elapsed = android.os.SystemClock.elapsedRealtime() - surfaceHoldSince;
        return latestCandidates.stream().anyMatch(candidate -> ArtworkSurfaceHandover.mayRetain(
                renderCandidate.binding().stamp(), candidate.binding().stamp(), eligibilityReason(candidate), elapsed));
    }

    private boolean holdSurface() {
        if (renderer == null || !renderer.isPlaying() || renderCandidate == null) return false;
        boolean starting = surfaceHoldSince < 0;
        if (starting) surfaceHoldSince = android.os.SystemClock.elapsedRealtime();
        if (!holdEligible()) { if (starting) surfaceHoldSince = -1; return false; }
        if (starting) {
            ArtworkRequestStamp heldStamp = stamp(renderCandidate, clientEpoch);
            ArtworkRenderGuard lease = new ArtworkRenderGuard(heldStamp,
                    () -> holdEligible() && clientEpoch == heldStamp.clientEpoch()
                            && client.serviceEpoch() == heldStamp.serviceEpoch()
                            && config.revision() == heldStamp.configRevision() ? heldStamp : null,
                    this::holdEligible);
            if (!renderer.retain(lease)) { surfaceHoldSince = -1; return false; }
            // Device feedback: layers that chased the native animation and a still preview on the
            // incoming host looked jittery. The native static cover alone animates the switch; the
            // decoder keeps running unseen and appears again once a surface has settled.
            if (mount != null) mount.show(false);
            main.postDelayed(holdTimeout, ArtworkSurfaceHandover.MAX_HOLD_MS);
            ArtworkPlaybackPolicy.Surface heldSurface = renderCandidate.surface();
            trace.state("ARTWORK_SURFACE_HELD", () -> "surface=" + heldSurface + " sameSong=true decoderKept=true layerHidden=true");
        }
        return true;
    }

    private void clearHold() {
        main.removeCallbacks(holdTimeout);
        surfaceHoldSince = -1;
    }

    /**
     * A resolve without an answer is restarted with a fresh binding. Device logs: after a lock-screen
     * cycle a provider request stayed silent until a surface switch rebound it, which then answered
     * within about a second. An issued download keeps reaching the cache on the provider side.
     */
    private void resolveStalled() {
        if (closed || !resolvingResource || resolvingCandidate == null) return;
        // Only a surface that could show the result justifies a new request; until then the old one runs on.
        if (!contextEligible() || current == null) { main.postDelayed(resolveStall, 2_000); return; }
        ArtworkRequestStamp song = resolvingCandidate.binding().stamp();
        if (!ArtworkResolveLifetime.sameSong(stallSong, song)) { stallSong = song; stallRestarts = 0; }
        long elapsed = android.os.SystemClock.elapsedRealtime() - resolvingSince;
        int restarts = stallRestarts;
        boolean restart = restarts < MAX_STALL_RESTARTS;
        trace.state("ARTWORK_RESOLVE_STALLED", () -> "elapsedMs=" + elapsed + " restarts=" + restarts + " restart=" + restart);
        if (!restart) return;
        stallRestarts++;
        wakeup.cancel(); stop(); attempted = null; attemptedImage = null; retryAt = Long.MAX_VALUE;
        refreshDisplay.run();
    }

    private void expireHold() {
        if (surfaceHoldSince < 0) return;
        trace.state("ARTWORK_SURFACE_HOLD_EXPIRED", () -> "decoderReleased=true");
        attempted = null; attemptedImage = null;
        stop();
        refreshDisplay.run();
    }
    private void suspendVisual() {
        clearHold();
        renderCandidate = null;
        playingForPx = 0;
        if (renderer != null) { renderer.close(); renderer = null; }
        if (mount != null) { mount.close(); mount = null; }
        keepAwake.update();
    }

    /** Same size rule as the provider query: the larger host side, capped at the contract resolution. */
    private static int requestPx(ImageView image) {
        return Math.max(1, Math.min(Math.max(image.getWidth(), image.getHeight()), ArtworkContract.MAX_RESOLUTION));
    }

    private ArtworkRequestStamp stamp(Candidate candidate, long epoch) {
        ArtworkRequestStamp base = candidate.binding().stamp();
        return new ArtworkRequestStamp(epoch, client.serviceEpoch(), base.pluginEpoch(), base.surfaceEpoch(),
                base.sessionEpoch(), base.trackGeneration(), base.requestRevision(), config.revision());
    }

    private boolean eligible(Candidate candidate) {
        return eligibilityReason(candidate).equals("eligible");
    }
    private boolean contextEligible() {
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return config.enabled() && !closed && !screenBlocked && power != null && power.isInteractive()
                && keyguard != null && keyguard.isKeyguardLocked();
    }
    private String eligibilityReason(Candidate candidate) {
        ImageView image = candidate.image();
        if (closed || !config.enabled()) return "bridge_off";
        if (candidate.binding() == null || !candidate.binding().ready() || candidate.binding().stamp() == null) return "binding_not_ready";
        if (!image.isAttachedToWindow()) return "detached";
        if (!candidate.binding().playing()) return "paused";
        if (screenBlocked) return "screen_off";
        PowerManager power = context.getSystemService(PowerManager.class);
        if (power == null || !power.isInteractive()) return "screen_off";
        if (image.getDisplay() == null || image.getDisplay().getState() != Display.STATE_ON) return "display_off";
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        if (keyguard == null || !keyguard.isKeyguardLocked()) return "keyguard_open";
        if (!image.isShown() || image.getWindowVisibility() != android.view.View.VISIBLE) return "hidden";
        if (image.getWidth() <= 0 || image.getHeight() <= 0) return "no_bounds";
        if ((image.getForeground() != null && (candidate.c17() == null || candidate.background()))
                || image.getImageTintList() != null || image.getColorFilter() != null) return "image_effect";
        if (candidate.background() ? !image.isHardwareAccelerated() : !(candidate.surface() == ArtworkPlaybackPolicy.Surface.IMMERSIVE
                ? image.getClipToOutline() : !image.getClipToOutline() && image.isHardwareAccelerated())) return "shape_profile";
        if (image.getImageAlpha() != 255 || image.getAlpha() != 1f || image.getScaleX() != 1f || image.getScaleY() != 1f
                || image.getTranslationX() != 0f || image.getTranslationY() != 0f || image.getRotation() != 0f
                || image.getRotationX() != 0f || image.getRotationY() != 0f) return "geometry_transition";
        if (candidate.c17() != null && !c17AncestorsSettled(image)) return "geometry_transition";
        return transitionReady(candidate) ? "eligible" : "native_transition";
    }

    private static boolean c17AncestorsSettled(android.view.View view) {
        for (android.view.ViewParent parent = view.getParent(); parent instanceof android.view.View ancestor; parent = ancestor.getParent()) {
            if (ancestor.getAlpha() != 1f || ancestor.getScaleX() != 1f || ancestor.getScaleY() != 1f
                    || ancestor.getRotation() != 0f || ancestor.getRotationX() != 0f || ancestor.getRotationY() != 0f) return false;
        }
        return true;
    }
    private void readNetwork() {
        if (closed || connectivity == null) return;
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            android.net.Network network = connectivity.getActiveNetwork();
            android.net.NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
            boolean validated = caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            boolean recovered = networkRecovery.update(network, validated);
            if (!validated || recovered) main.removeCallbacks(networkPulse);
            if (!validated) networkRecoveryPending = false;
            if (recovered) {
                networkRecoveryPending = awaitingNetwork || resolvingResource;
                main.postDelayed(networkPulse, 500);
            }
        } catch (RuntimeException ignored) { /* timed retry remains the bounded fallback */ }
    }

    private boolean transitionReady(Candidate candidate) {
        if (candidate.c17() != null) return candidate.c17().ready(candidate.image(), candidate.background());
        return candidate.surface() == ArtworkPlaybackPolicy.Surface.IMMERSIVE
                ? transition.complete(candidate.image().getDrawable())
                : Build.VERSION.SDK_INT >= 33 && candidate.cardEffects() != null && candidate.cardEffects().settled(candidate.image());
    }

    /**
     * Live display state first, then the last outcome: "playing" only while frames are on screen,
     * "resolving" while a request is out, otherwise e.g. "no_motion:confirmed_album_no_motion".
     */
    public String status() {
        if (closed) return "off";
        if (renderer != null && renderer.isShowing()) return "playing";
        if (resolvingResource) return "resolving";
        return outcome;
    }

    public long statusAgeMs() {
        return android.os.SystemClock.elapsedRealtime() - outcomeAt;
    }

    private void outcome(String value) {
        // A provider answer is followed by its generic failure state; keep the detailed form.
        if (value.equals(outcome) || outcome.startsWith(value + ":")) return;
        outcome = value;
        outcomeAt = android.os.SystemClock.elapsedRealtime();
    }

    private void failure(String reason) {
        // Superseded requests and renders are ordinary churn, not an outcome worth reporting.
        if (!reason.equals("stale_render") && !reason.equals("asset_gate_changed")) outcome(reason);
        if (!config.localFixture() && (reason.equals("request_timeout") || reason.equals("bind_failed") || reason.equals("connection_lost"))) retryDelay = 30_000;
        retryAt = retryDelay == Long.MAX_VALUE ? Long.MAX_VALUE : android.os.SystemClock.elapsedRealtime() + retryDelay;
        trace.state("ARTWORK_TEST_FALLBACK", () -> "reason=" + reason);
        Candidate failed = current != null ? current : resolvingCandidate;
        stop();
        long epoch = clientEpoch;
        if (retryAt != Long.MAX_VALUE && failed != null && !closed) {
            armRetry(failed, epoch);
        }
    }
    private void armRetry(Candidate failed, long epoch) {
        long delayMs = Math.max(1, retryAt - android.os.SystemClock.elapsedRealtime());
        trace.state("ARTWORK_RETRY_SCHEDULED", () -> "delayMs=" + delayMs + " clientEpoch=" + epoch);
        wakeup.arm(delayMs, () -> !closed && contextEligible() && clientEpoch == epoch
                && attemptedImage == failed.image() && failed.binding().stamp().equals(attempted), () -> {
            if (android.os.SystemClock.elapsedRealtime() < retryAt) { armRetry(failed, epoch); return; }
            trace.state("ARTWORK_RETRY_FIRED", () -> "clientEpoch=" + epoch);
            refreshDisplay.run();
        });
    }

    private void stop() {
        resolvingResource = false;
        main.removeCallbacks(resolveStall);
        boolean pending = resolvingCandidate != null;
        resolvingCandidate = null;
        if (renderer == null && mount == null && !pending) return;
        ++clientEpoch;
        client.close();
        suspendVisual();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        main.removeCallbacks(networkPulse);
        if (networkRegistered) try { connectivity.unregisterNetworkCallback(networkCallback); }
        catch (RuntimeException ignored) { /* service teardown */ }
        keepAwake.close();
        wakeup.close();
        stop();
        context.unregisterReceiver(screen);
        current = null;
        attempted = null;
        attemptedImage = null;
    }
}
