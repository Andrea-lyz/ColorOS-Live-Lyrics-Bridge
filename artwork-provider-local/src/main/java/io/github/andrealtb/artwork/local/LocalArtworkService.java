package io.github.andrealtb.artwork.local;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;

import io.github.andrealtb.artwork.contract.ArtworkAsset;
import io.github.andrealtb.artwork.contract.ArtworkBundleCodec;
import io.github.andrealtb.artwork.contract.ArtworkContract;
import io.github.andrealtb.artwork.contract.ArtworkFileVerifier;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult;
import io.github.andrealtb.artwork.contract.IArtworkCallback;
import io.github.andrealtb.artwork.contract.IArtworkProvider;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Offline fixture. Intentionally returns one local video for all queries; no song matching. */
public final class LocalArtworkService extends Service {
    private final RequestLeaseTable<Request> leases = new RequestLeaseTable<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), runnable -> new Thread(runnable, "artwork-local-resolve"));
    private final ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor();
    private LocalArtworkStore store;

    private final IArtworkProvider.Stub binder = new IArtworkProvider.Stub() {
        @Override public Bundle getCapabilities() {
            ArtworkCallerPolicy.requireAllowed(LocalArtworkService.this);
            return ArtworkBundleCodec.capabilities(true);
        }

        @Override public void resolve(String id, Bundle bundle, IArtworkCallback callback) {
            int uid = ArtworkCallerPolicy.requireAllowed(LocalArtworkService.this);
            ArtworkContract.opaqueId(id);
            if (callback == null) return;
            ArtworkQuery query;
            try {
                query = ArtworkBundleCodec.decodeQuery(bundle);
            } catch (RuntimeException error) {
                reply(callback, id, ArtworkResult.failure(ArtworkResult.Status.UNSUPPORTED, "invalid_query"));
                return;
            }
            Request request = new Request(uid, id, callback, query);
            if (!leases.add(uid, id, request, request.createdAt, ArtworkContract.LEASE_MS)) {
                reply(callback, id, new ArtworkResult(ArtworkResult.Status.RETRY_LATER, null, 1000, "request_budget"));
                return;
            }
            try {
                callback.asBinder().linkToDeath(request.death, 0);
                if (!current(request)) { dispose(request); return; }
                worker.execute(() -> resolveLocal(request));
            } catch (RemoteException | RejectedExecutionException error) {
                remove(request);
                reply(callback, id, ArtworkResult.failure(ArtworkResult.Status.ERROR, "client_unavailable"));
            }
        }

        @Override public void cancel(String id) {
            int uid = ArtworkCallerPolicy.requireAllowed(LocalArtworkService.this);
            ArtworkContract.opaqueId(id);
            Request request = leases.remove(uid, id);
            if (request != null) dispose(request);
        }

        @Override public ParcelFileDescriptor openAsset(String id, String assetId) {
            int uid = ArtworkCallerPolicy.requireAllowed(LocalArtworkService.this);
            ArtworkContract.opaqueId(id);
            ArtworkContract.opaqueId(assetId);
            Request request = leases.get(uid, id, SystemClock.elapsedRealtime());
            if (request == null) return null;
            synchronized (request) {
                if (!current(request) || request.asset == null || request.opened
                        || !request.asset.assetId.equals(assetId) || request.fileId.isEmpty()) return null;
                try {
                    ParcelFileDescriptor fd = ParcelFileDescriptor.open(store.file(request.fileId),
                            ParcelFileDescriptor.MODE_READ_ONLY);
                    request.opened = true;
                    return fd;
                } catch (Exception error) {
                    return null;
                }
            }
        }

        @Override public void releaseAsset(String id, String assetId) {
            int uid = ArtworkCallerPolicy.requireAllowed(LocalArtworkService.this);
            ArtworkContract.opaqueId(id);
            ArtworkContract.opaqueId(assetId);
            Request request = leases.get(uid, id, SystemClock.elapsedRealtime());
            if (request != null) {
                synchronized (request) {
                    if (request.asset != null && request.asset.assetId.equals(assetId)) remove(request);
                }
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        store = LocalArtworkStore.get(this);
        reaper.scheduleWithFixedDelay(() -> {
            for (Request request : leases.reap(SystemClock.elapsedRealtime())) dispose(request);
            store.cleanup();
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override public IBinder onBind(Intent intent) {
        return intent != null && ArtworkContract.ACTION_BIND.equals(intent.getAction()) ? binder : null;
    }

    @Override public void onDestroy() {
        reaper.shutdownNow();
        worker.shutdownNow();
        for (Request request : leases.clear()) dispose(request);
        super.onDestroy();
    }

    private void resolveLocal(Request request) {
        String fileId;
        synchronized (request) {
            if (!current(request)) return;
            request.fileId = store.pinCurrent();
            fileId = request.fileId;
        }
        if (fileId.isEmpty()) {
            fail(request, ArtworkResult.Status.NO_MOTION, "no_local_video");
            return;
        }
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(store.file(fileId), ParcelFileDescriptor.MODE_READ_ONLY)) {
            ArtworkAsset asset = ArtworkFileVerifier.inspect(fd, UUID.randomUUID().toString(), fileId,
                    request.query.maxFileBytes);
            if (!asset.fits(request.query)) {
                fail(request, ArtworkResult.Status.UNSUPPORTED, "resource_limits");
                return;
            }
            synchronized (request) {
                if (!current(request)) return;
                long remainingMs = request.createdAt + ArtworkContract.LEASE_MS - SystemClock.elapsedRealtime();
                if (remainingMs <= 0) return;
                request.asset = new ArtworkAsset(asset.assetId, asset.version, asset.codec, asset.width,
                        asset.height, asset.durationMs, asset.fileBytes, remainingMs);
                reply(request.callback, request.id, new ArtworkResult(ArtworkResult.Status.READY,
                        request.asset, 0, "local_fixture"));
            }
        } catch (Exception error) {
            fail(request, ArtworkResult.Status.ERROR, "local_validation_failed");
        }
    }

    private boolean current(Request request) {
        return leases.get(request.uid, request.id, SystemClock.elapsedRealtime()) == request;
    }

    private void fail(Request request, ArtworkResult.Status status, String reason) {
        if (current(request)) reply(request.callback, request.id, ArtworkResult.failure(status, reason));
        remove(request);
    }

    private void remove(Request request) {
        if (leases.removeIfSame(request.uid, request.id, request)) dispose(request);
    }

    private void dispose(Request request) {
        synchronized (request) {
            try { request.callback.asBinder().unlinkToDeath(request.death, 0); }
            catch (java.util.NoSuchElementException ignored) { /* cancelled before linkToDeath */ }
            if (!request.fileId.isEmpty()) {
                store.unpin(request.fileId);
                request.fileId = "";
            }
        }
    }

    private static void reply(IArtworkCallback callback, String id, ArtworkResult result) {
        try { callback.onResult(id, ArtworkBundleCodec.encodeResult(result)); }
        catch (RemoteException | RuntimeException ignored) { /* death/expiry also reclaim the lease */ }
    }

    private final class Request {
        final int uid;
        final long createdAt = SystemClock.elapsedRealtime();
        final String id;
        final IArtworkCallback callback;
        final ArtworkQuery query;
        final IBinder.DeathRecipient death;
        String fileId = "";
        ArtworkAsset asset;
        boolean opened;

        Request(int uid, String id, IArtworkCallback callback, ArtworkQuery query) {
            this.uid = uid;
            this.id = id;
            this.callback = callback;
            this.query = query;
            death = () -> remove(this);
        }
    }
}
