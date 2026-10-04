package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import io.github.andrealtb.artwork.contract.ArtworkAsset;
import io.github.andrealtb.artwork.contract.ArtworkBundleCodec;
import io.github.andrealtb.artwork.contract.ArtworkContract;
import io.github.andrealtb.artwork.contract.ArtworkFileVerifier;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult;
import io.github.andrealtb.artwork.contract.IArtworkCallback;
import io.github.andrealtb.artwork.contract.IArtworkProvider;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Main-thread ownership, process-wide bounded IPC lane. All remote work/FD inspection is off-main. */
public final class ArtworkProviderClient implements AutoCloseable {
    // A hung remote transaction consumes this single lane; recreating a page cannot spawn more lanes.
    private static final ThreadPoolExecutor IPC = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), runnable -> new Thread(runnable, "bridge-artwork-ipc"));
    /** A bind/handshake that never reaches the provider retries quickly instead of waiting out the whole lease. */
    private static final long HANDSHAKE_TIMEOUT_MS = 8_000;
    /**
     * Hidden {@code Context.BIND_FOREGROUND_SERVICE}: while SystemUI waits for a result the provider ranks
     * as a bound foreground service. Device logs showed the provider stopped by the OEM background freezer
     * mid-request although SystemUI held the binding. Falls back to a plain binding if refused.
     */
    private static final int BIND_FOREGROUND_SERVICE = 0x04000000;
    /** The same logs show each incoming transaction letting the provider run for about two seconds. */
    private static final long KEEPALIVE_MS = 1_000;
    private static final ThreadPoolExecutor KEEPALIVE = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS,
            new java.util.concurrent.SynchronousQueue<>(), runnable -> new Thread(runnable, "bridge-artwork-keepalive"));
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Request current;
    private boolean bound;
    private long epoch;
    private long serviceEpoch;

    public interface Listener {
        void onState(String reason);
        default void onConnected(long serviceEpoch) {}
        /** Only normalized reason codes and bounded retry hints; no provider metadata or URL. */
        default void onResult(ArtworkResult.Status status, String reason, long retryAfterMs) {}
        /** Receives FD ownership. Close even if the host has become ineligible. */
        void onAsset(OpenedAsset asset);
    }

    public static final class OpenedAsset implements AutoCloseable {
        public final ArtworkAsset metadata;
        public final ParcelFileDescriptor descriptor;

        OpenedAsset(ArtworkAsset metadata, ParcelFileDescriptor descriptor) {
            this.metadata = metadata;
            this.descriptor = descriptor;
        }

        @Override public void close() {
            try { descriptor.close(); } catch (IOException ignored) {}
        }
    }

    public ArtworkProviderClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public long serviceEpoch() { return serviceEpoch; }

    /** Selection inspection shares the existing bounded IPC lane; no package-manager transaction on UI. */
    public void resolveSelected(ComponentName component, String signer, ArtworkQuery query, Listener listener) {
        resolveSelected(component, signer, query, true, listener);
    }

    public void resolveSelected(ComponentName component, String signer, ArtworkQuery query, boolean localFixture, Listener listener) {
        requireMain();
        close();
        long selectionEpoch = ++epoch;
        listener.onState("selecting");
        main.postDelayed(() -> {
            if (epoch == selectionEpoch) { ++epoch; listener.onState("selection_timeout"); }
        }, 12_000);
        try {
            IPC.execute(() -> {
                try {
                    ArtworkProviderDirectory.Provider provider = ArtworkProviderDirectory.read(context, component);
                    if (!provider.signingIdentity().equals(signer)) throw new SecurityException("selection_changed");
                    main.post(() -> {
                        if (epoch == selectionEpoch) resolve(provider, query, localFixture, listener);
                    });
                } catch (Exception error) {
                    main.post(() -> { if (epoch == selectionEpoch) listener.onState("selection_failed"); });
                }
            });
        } catch (RejectedExecutionException error) { listener.onState("ipc_budget_exhausted"); }
    }

    /** Local preview only accepts fixtures; the future SystemUI runtime must pass false. */
    public void resolve(ArtworkProviderDirectory.Provider provider, ArtworkQuery query,
            boolean allowLocalFixture, Listener listener) {
        requireMain();
        close();
        Request request = new Request(++epoch, provider, query, allowLocalFixture, listener);
        current = request;
        listener.onState("connecting");
        main.postDelayed(request.timeout, allowLocalFixture ? 12_000 : 45_000);
        main.postDelayed(request.handshake, HANDSHAKE_TIMEOUT_MS);
        execute(request, () -> {
            ArtworkProviderDirectory.verify(context, provider);
            main.post(() -> bind(request));
        });
    }

    private void bind(Request request) {
        if (!isCurrent(request)) return;
        try {
            Intent intent = new Intent(ArtworkContract.ACTION_BIND).setComponent(request.provider.component());
            int flags = Context.BIND_AUTO_CREATE | (request.allowLocalFixture ? 0 : BIND_FOREGROUND_SERVICE);
            try { bound = context.bindService(intent, request.connection, flags); }
            catch (SecurityException refused) { bound = context.bindService(intent, request.connection, Context.BIND_AUTO_CREATE); }
            if (!bound) fail(request, "bind_failed");
        } catch (RuntimeException error) {
            fail(request, "bind_failed");
        }
    }

    private void connected(Request request, IBinder binder) {
        if (!isCurrent(request)) return;
        ++serviceEpoch;
        request.listener.onConnected(serviceEpoch);
        request.remote = IArtworkProvider.Stub.asInterface(binder);
        request.binder = binder;
        execute(request, () -> {
            ArtworkProviderDirectory.verify(context, request.provider);
            binder.linkToDeath(request.death, 0);
            if (!request.active.get()) { binder.unlinkToDeath(request.death, 0); return; }
            boolean localFixture = ArtworkBundleCodec.readCapabilities(request.remote.getCapabilities());
            if (localFixture != request.allowLocalFixture) throw new SecurityException("provider_mode_mismatch");
            if (!request.active.get()) return;
            main.removeCallbacks(request.handshake);
            request.remote.resolve(request.id, ArtworkBundleCodec.encodeQuery(request.query), request.callback);
            main.post(() -> {
                state(request, "resolving");
                if (!request.allowLocalFixture && isCurrent(request)) main.postDelayed(request.keepAlive, KEEPALIVE_MS);
            });
        });
    }

    /** A light incoming transaction per second until the answer arrives; a busy previous ping is skipped. */
    private void keepAlive(Request request) {
        if (!isCurrent(request) || request.received.get() || request.remote == null) return;
        IArtworkProvider remote = request.remote;
        try {
            KEEPALIVE.execute(() -> {
                if (!request.active.get() || request.received.get()) return;
                try { remote.getCapabilities(); } catch (Exception ignored) { /* death and timeouts have their own paths */ }
            });
        } catch (RejectedExecutionException busy) { /* the previous ping is still in flight */ }
        main.postDelayed(request.keepAlive, KEEPALIVE_MS);
    }

    private void receive(Request request, String id, Bundle result, int callingUid) {
        if (callingUid != request.provider.uid() || !request.id.equals(id)
                || !request.active.get() || !request.received.compareAndSet(false, true)) return;
        execute(request, () -> {
            ArtworkResult decoded = ArtworkBundleCodec.decodeResult(result);
            if (decoded.status != ArtworkResult.Status.READY) {
                main.post(() -> {
                    if (!isCurrent(request)) return;
                    request.listener.onResult(decoded.status, decoded.reason, decoded.retryAfterMs);
                    fail(request, decoded.status.name().toLowerCase(java.util.Locale.ROOT));
                });
                return;
            }
            ArtworkAsset asset = decoded.asset;
            if (!asset.fits(request.query) || !request.active.get()) throw new IOException("resource_limits");
            ArtworkProviderDirectory.verify(context, request.provider);
            ParcelFileDescriptor descriptor = request.remote.openAsset(request.id, asset.assetId);
            if (descriptor == null) throw new IOException("missing_asset");
            OpenedAsset opened = new OpenedAsset(asset, descriptor);
            boolean handedOff = false;
            try {
                ArtworkFileVerifier.verify(descriptor, asset, request.query);
                if (!request.active.get()) return;
                main.post(() -> deliver(request, opened));
                handedOff = true;
            } finally {
                if (!handedOff) opened.close();
                // MediaPlayer duplicates the FD; no cache lease is needed once a consumer owns this FD.
                request.remote.releaseAsset(request.id, asset.assetId);
            }
        });
    }

    private void deliver(Request request, OpenedAsset asset) {
        if (!isCurrent(request)) { asset.close(); return; }
        Listener listener = request.listener;
        main.removeCallbacks(request.timeout);
        // Keep binding/death observation until the surface stops, even though its asset lease is released.
        try { listener.onAsset(asset); }
        catch (RuntimeException error) { asset.close(); fail(request, "consumer_failed"); }
    }

    private void state(Request request, String reason) {
        if (isCurrent(request)) request.listener.onState(reason);
    }

    private void fail(Request request, String reason) {
        if (!isCurrent(request)) return;
        Listener listener = request.listener;
        finish(request);
        listener.onState(reason);
    }

    private boolean isCurrent(Request request) {
        return current == request && request.epoch == epoch && request.active.get();
    }

    private void finish(Request request) {
        request.active.set(false);
        request.listener = null;
        main.removeCallbacks(request.timeout);
        main.removeCallbacks(request.handshake);
        main.removeCallbacks(request.keepAlive);
        if (request.binder != null) {
            ++serviceEpoch;
            try { request.binder.unlinkToDeath(request.death, 0); }
            catch (java.util.NoSuchElementException ignored) { /* handshake may not have linked yet */ }
        }
        if (bound && current == request) {
            try { context.unbindService(request.connection); } catch (IllegalArgumentException ignored) {}
            bound = false;
        }
        if (current == request) current = null;
        if (request.remote != null) {
            try { IPC.execute(() -> {
                try { request.remote.cancel(request.id); } catch (Exception ignored) {}
            }); } catch (RejectedExecutionException ignored) { /* service TTL/death reclaims lease */ }
        }
    }

    @Override public void close() {
        requireMain();
        ++epoch;
        if (current != null) finish(current);
    }

    private void execute(Request request, IpcWork work) {
        try {
            IPC.execute(() -> {
                if (!request.active.get()) return;
                try { work.run(); }
                catch (Exception error) { main.post(() -> fail(request, failureReason(error))); }
            });
        } catch (RejectedExecutionException error) {
            main.post(() -> fail(request, "ipc_budget_exhausted"));
        }
    }

    /** Identity and mode rejections are named so settings can explain them; everything else stays generic. */
    static String failureReason(Exception error) {
        String message = error instanceof SecurityException ? error.getMessage() : null;
        return "provider_mode_mismatch".equals(message) || "provider_identity_changed".equals(message)
                || "provider_unavailable".equals(message) || "artwork_caller_denied".equals(message)
                ? message : "provider_or_asset_failed";
    }

    private static void requireMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("owner_thread");
    }

    private interface IpcWork { void run() throws Exception; }

    private final class Request {
        final long epoch;
        final String id = UUID.randomUUID().toString();
        final ArtworkProviderDirectory.Provider provider;
        final ArtworkQuery query;
        final boolean allowLocalFixture;
        final AtomicBoolean active = new AtomicBoolean(true);
        final AtomicBoolean received = new AtomicBoolean();
        final Runnable timeout = () -> fail(this, "request_timeout");
        final Runnable handshake = () -> fail(this, "handshake_timeout");
        final Runnable keepAlive = () -> keepAlive(this);
        final IBinder.DeathRecipient death = () -> main.post(() -> fail(this, "provider_died"));
        final ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) { connected(Request.this, binder); }
            @Override public void onServiceDisconnected(ComponentName name) { fail(Request.this, "provider_disconnected"); }
            @Override public void onBindingDied(ComponentName name) { fail(Request.this, "binding_died"); }
            @Override public void onNullBinding(ComponentName name) { fail(Request.this, "null_binding"); }
        };
        final IArtworkCallback.Stub callback = new IArtworkCallback.Stub() {
            @Override public void onResult(String id, Bundle result) {
                receive(Request.this, id, result, Binder.getCallingUid());
            }
        };
        volatile IArtworkProvider remote;
        volatile IBinder binder;
        Listener listener;

        Request(long epoch, ArtworkProviderDirectory.Provider provider, ArtworkQuery query,
                boolean allowLocalFixture, Listener listener) {
            this.epoch = epoch;
            this.provider = provider;
            this.query = query;
            this.allowLocalFixture = allowLocalFixture;
            this.listener = listener;
        }
    }
}
