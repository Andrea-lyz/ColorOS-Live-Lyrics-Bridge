package io.github.andrealtb.artwork.am;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import io.github.andrealtb.artwork.contract.*;

/** Actual-UID authorization, bounded worker, callback death/cancel and single-open resource leases. */
public final class AmArtworkService extends Service {
    /** One finishing download may outlive its client so the work still reaches the cache. */
    private static final int MAX_DETACHED = 1;
    private final RequestLeaseTable<Request> leases = new RequestLeaseTable<>();
    private final java.util.Set<Request> detached = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    // Two lanes so a finishing detached download cannot delay the song the client is waiting on.
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), runnable -> new Thread(runnable, "artwork-am-resolve"));
    private final ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor();
    private AmResolver resolver;
    private AmCache cache;
    private final IArtworkProvider.Stub binder = new IArtworkProvider.Stub() {
        @Override public Bundle getCapabilities() {
            ArtworkCallerPolicy.requireAllowed(AmArtworkService.this);
            return ArtworkBundleCodec.capabilities(false);
        }
        @Override public void resolve(String id, Bundle bundle, IArtworkCallback callback) {
            int uid = ArtworkCallerPolicy.requireAllowed(AmArtworkService.this);
            ArtworkContract.opaqueId(id);
            if (callback == null) return;
            ArtworkQuery query;
            try { query = ArtworkBundleCodec.decodeQuery(bundle); }
            catch (RuntimeException error) { reply(callback, id, ArtworkResult.failure(ArtworkResult.Status.UNSUPPORTED, "invalid_query")); return; }
            Request request = new Request(uid, id, callback, query);
            if (!leases.add(uid, id, request, request.created, ArtworkContract.LEASE_MS)) {
                reply(callback, id, new ArtworkResult(ArtworkResult.Status.RETRY_LATER, null, 1000, "request_budget")); return;
            }
            try {
                callback.asBinder().linkToDeath(request.death, 0);
                if (!current(request)) { dispose(request); return; }
                worker.execute(request.work);
            } catch (RemoteException | RejectedExecutionException error) {
                remove(request);
                reply(callback, id, new ArtworkResult(ArtworkResult.Status.RETRY_LATER, null, 1000, "worker_budget"));
            }
        }
        @Override public void cancel(String id) {
            int uid = ArtworkCallerPolicy.requireAllowed(AmArtworkService.this);
            ArtworkContract.opaqueId(id);
            Request request = leases.remove(uid, id);
            if (request != null) dispose(request);
        }
        @Override public ParcelFileDescriptor openAsset(String id, String assetId) {
            int uid = ArtworkCallerPolicy.requireAllowed(AmArtworkService.this);
            ArtworkContract.opaqueId(id); ArtworkContract.opaqueId(assetId);
            Request request = leases.get(uid, id, SystemClock.elapsedRealtime());
            if (request == null || !AmSettings.enabled(AmArtworkService.this)) return null;
            synchronized (request) {
                if (!current(request) || request.asset == null || request.file == null || request.opened
                        || !request.asset.assetId.equals(assetId)) return null;
                try {
                    ParcelFileDescriptor fd = ParcelFileDescriptor.open(request.file, ParcelFileDescriptor.MODE_READ_ONLY);
                    request.opened = true;
                    return fd;
                } catch (Exception error) { return null; }
            }
        }
        @Override public void releaseAsset(String id, String assetId) {
            int uid = ArtworkCallerPolicy.requireAllowed(AmArtworkService.this);
            ArtworkContract.opaqueId(id); ArtworkContract.opaqueId(assetId);
            Request request = leases.get(uid, id, SystemClock.elapsedRealtime());
            if (request != null) synchronized (request) {
                if (request.asset != null && request.asset.assetId.equals(assetId)) remove(request);
            }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        cache = AmCache.get(this); resolver = new AmResolver(this);
        AmPauseDetector.listen(gap -> AmSettings.trace(this, "ARTWORK_AM_PROCESS_PAUSED", "gapMs=" + gap));
        reaper.scheduleWithFixedDelay(() -> {
            for (Request request : leases.reap(SystemClock.elapsedRealtime())) dispose(request);
            if (!AmSettings.enabled(this)) for (Request request : leases.clear()) dispose(request);
            cache.cleanup();
        }, 2, 2, TimeUnit.SECONDS);
    }
    @Override public IBinder onBind(Intent intent) { return intent != null && ArtworkContract.ACTION_BIND.equals(intent.getAction()) ? binder : null; }
    @Override public void onDestroy() {
        reaper.shutdownNow(); worker.shutdownNow();
        for (Request request : leases.clear()) dispose(request);
        super.onDestroy();
    }
    private void perform(Request request) {
        if (!current(request)) return;
        request.started = true;
        AmSettings.trace(this, "ARTWORK_AM_RESOLVE", "started");
        AmPauseDetector.begin();
        try {
            AmResolver.Resolved result = resolver.resolve(request.query, request.task);
            synchronized (request) {
                if (!current(request) || request.task.cancelled.get() || !AmSettings.enabled(this)) {
                    AmSettings.trace(this, "ARTWORK_AM_DETACHED_RESULT", "ready_cached");
                    cache.unpin(result.file()); return;
                }
                request.file = result.file();
                ArtworkAsset asset = result.asset();
                long remaining = request.created + ArtworkContract.LEASE_MS - SystemClock.elapsedRealtime();
                if (remaining <= 0) { remove(request); return; }
                request.asset = new ArtworkAsset(asset.assetId, asset.version, asset.codec, asset.width,
                        asset.height, asset.durationMs, asset.fileBytes, remaining);
                request.finished = true;
                reply(request.callback, request.id, new ArtworkResult(ArtworkResult.Status.READY, request.asset, 0, "am_web_verified"));
            }
        } catch (AmFailure failure) { fail(request, failure.result()); }
        catch (Exception error) { fail(request, ArtworkResult.failure(ArtworkResult.Status.ERROR, "resolver_failed")); }
        finally { detached.remove(request); AmPauseDetector.end(); }
    }
    private boolean current(Request request) { return leases.get(request.uid, request.id, SystemClock.elapsedRealtime()) == request; }
    private void fail(Request request, ArtworkResult failure) {
        request.finished = true;
        if (current(request) && !request.task.cancelled.get()) reply(request.callback, request.id, failure);
        else AmSettings.trace(this, "ARTWORK_AM_DETACHED_RESULT", failure.status.name().toLowerCase(java.util.Locale.ROOT) + "_" + failure.reason);
        remove(request);
    }
    private void remove(Request request) { if (leases.removeIfSame(request.uid, request.id, request)) dispose(request); }
    private void dispose(Request request) {
        // Already resolving: the client no longer waits, but the asset still lands in cache.
        boolean keepDownloading = AmDownloadPolicy.keepDownloading(request.started, request.finished,
                request.task.cancelled.get(), detached.size(), MAX_DETACHED) && detached.add(request);
        if (!keepDownloading) { request.task.cancel(); worker.remove(request.work); }
        synchronized (request) {
            try { request.callback.asBinder().unlinkToDeath(request.death, 0); }
            catch (java.util.NoSuchElementException ignored) { /* cancelled before link */ }
            if (request.file != null && !keepDownloading) { cache.unpin(request.file); request.file = null; }
        }
        if (keepDownloading) AmSettings.trace(this, "ARTWORK_AM_DETACHED", "started_download_completes");
    }
    private void reply(IArtworkCallback callback, String id, ArtworkResult result) {
        AmSettings.trace(this, "ARTWORK_AM_RESULT", result.status.name().toLowerCase(java.util.Locale.ROOT) + "_" + result.reason);
        try { callback.onResult(id, ArtworkBundleCodec.encodeResult(result)); }
        catch (RemoteException | RuntimeException ignored) { /* expiry/death reclaims pinned file */ }
    }
    private final class Request {
        final int uid;
        final String id;
        final IArtworkCallback callback;
        final ArtworkQuery query;
        final long created = SystemClock.elapsedRealtime();
        final AmNetwork.Task task = new AmNetwork.Task();
        final IBinder.DeathRecipient death;
        final Runnable work;
        File file;
        ArtworkAsset asset;
        boolean opened;
        volatile boolean started;
        volatile boolean finished;
        Request(int uid, String id, IArtworkCallback callback, ArtworkQuery query) {
            this.uid = uid; this.id = id; this.callback = callback; this.query = query;
            death = () -> remove(this); work = () -> perform(this);
        }
    }
}
