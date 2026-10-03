package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Local-FD renderer. Mounting, geometry, shape and official transitions belong to verified hosts. */
public final class DynamicArtworkRenderer implements AutoCloseable, TextureView.SurfaceTextureListener {
    private static final Handler MEDIA;
    private static DynamicArtworkRenderer owner;
    static {
        HandlerThread thread = new HandlerThread("bridge-artwork-media");
        thread.start();
        MEDIA = new Handler(thread.getLooper());
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextureView view;
    private final Consumer<String> status;
    private boolean fitXY;
    private Session session;
    private boolean closed;
    private TextureView handoverFrom;
    private Runnable pendingDetach;

    public DynamicArtworkRenderer(TextureView view, Consumer<String> status) {
        this(view, status, false);
    }

    public DynamicArtworkRenderer(TextureView view, Consumer<String> status, boolean fitXY) {
        this.view = view;
        this.status = status;
        this.fitXY = fitXY;
        adopt(view);
    }

    private void adopt(TextureView target) {
        target.setSurfaceTextureListener(this);
        target.setOpaque(false);
        target.setAlpha(0f);
        target.setClickable(false);
        target.setFocusable(false);
        target.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public boolean isPlaying() {
        requireMain();
        Session active = session;
        return !closed && active != null && active.started && active.player != null;
    }

    /** Replace a surface lease with a bounded same-song transition lease; the decoder keeps running. */
    public boolean retain(ArtworkRenderGuard guard) {
        requireMain();
        Session active = session;
        if (!isPlaying() || !guard.permits()) { guard.close(); return false; }
        active.guard.close();
        active.guard = guard;
        return true;
    }

    /**
     * Hands the running decoder to another view of the same song instead of rebuilding it, so the
     * new surface fades in on the next frame rather than after a fresh prepare. {@code onDetached}
     * runs once the player no longer uses the previous view.
     */
    public boolean handOver(TextureView next, boolean nextFitXY, ArtworkRenderGuard guard, Runnable onDetached) {
        requireMain();
        Session active = session;
        if (closed || active == null || next == null || next == view || pendingDetach != null) { guard.close(); return false; }
        if (!guard.permits() || active.player == null) { guard.close(); return false; }
        active.guard.close();
        active.guard = guard;
        active.visible = false;
        active.updated = false;
        main.removeCallbacks(active.timeout);
        main.postDelayed(active.timeout, ArtworkSurfaceHandover.MAX_HOLD_MS);
        view.setSurfaceTextureListener(null);
        handoverFrom = view;
        pendingDetach = onDetached;
        view = next;
        this.fitXY = nextFitXY;
        adopt(next);
        // The previous view is released only once the player has actually moved off it.
        if (next.isAvailable()) attach(active, next.getSurfaceTexture());
        return true;
    }

    private void attach(Session target, SurfaceTexture texture) {
        MEDIA.post(() -> {
            Surface stale = null;
            Surface next = null;
            boolean attached = false;
            try {
                MediaPlayer player = target.player;
                if (player == null || !target.active.get()) throw new IllegalStateException("handover_lost");
                next = new Surface(texture);
                player.setSurface(next);
                stale = target.surface;
                target.surface = next;
                attached = true;
            } catch (RuntimeException error) {
                if (next != null && !attached) next.release();
                main.post(() -> fail(target, "surface_handover_failed"));
            }
            Surface release = stale;
            boolean moved = attached;
            main.post(() -> {
                TextureView previous = handoverFrom;
                handoverFrom = null;
                Runnable done = pendingDetach;
                pendingDetach = null;
                if (previous != null) previous.setSurfaceTextureListener(null);
                if (release != null) release.release();
                if (done != null) done.run();
                if (moved && current(target)) crop(target);
            });
        });
    }

    /** The caller supplies all ownership epochs and live display eligibility. Always takes FD ownership. */
    public void play(ArtworkProviderClient.OpenedAsset asset, ArtworkRequestStamp stamp,
            Supplier<ArtworkRequestStamp> currentStamp, BooleanSupplier eligible) {
        ArtworkRenderGuard guard;
        try { guard = new ArtworkRenderGuard(stamp, currentStamp, eligible); }
        catch (RuntimeException error) { asset.close(); throw error; }
        play(asset, guard);
    }

    private void play(ArtworkProviderClient.OpenedAsset asset, ArtworkRenderGuard guard) {
        requireMain();
        if (closed) { guard.close(); asset.close(); return; }
        stop();
        if (!guard.permits()) { guard.close(); asset.close(); return; }
        if (owner != null && owner != this) owner.stop();
        owner = this;
        Session next = new Session(asset, guard);
        session = next;
        status.accept("preparing");
        main.postDelayed(next.timeout, 10_000);
        if (view.isAvailable()) prepare(next, view.getSurfaceTexture());
    }

    private void prepare(Session target, SurfaceTexture texture) {
        if (!current(target) || texture == null || target.preparing) return;
        target.preparing = true;
        crop(target);
        MEDIA.post(() -> {
            if (!target.active.get()) { target.asset.close(); return; }
            try {
                target.surface = new Surface(texture);
                target.player = new MediaPlayer();
                MediaPlayer player = target.player;
                player.setSurface(target.surface);
                player.setVolume(0f, 0f);
                player.setLooping(true);
                player.setOnPreparedListener(prepared -> {
                    main.post(() -> {
                        if (!current(target)) return;
                        MEDIA.post(() -> {
                            if (!target.active.get()) return;
                            try { prepared.start(); }
                            catch (RuntimeException error) {
                                main.post(() -> fail(target, "decode_failed"));
                                return;
                            }
                            main.post(() -> {
                                if (!current(target)) return;
                                target.started = true;
                                status.accept("waiting_first_frame");
                                firstFrame(target);
                            });
                        });
                    });
                });
                player.setOnInfoListener((prepared, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        main.post(() -> {
                            if (current(target)) {
                                target.rendering = true;
                                target.updated = false;
                            }
                        });
                    }
                    return false;
                });
                player.setOnErrorListener((prepared, what, extra) -> {
                    main.post(() -> fail(target, "decode_failed"));
                    return true;
                });
                try {
                    player.setDataSource(target.asset.descriptor.getFileDescriptor(), 0, target.asset.metadata.fileBytes);
                } finally {
                    // MediaPlayer duplicates the descriptor. No Java FD remains for the playback lifetime.
                    target.asset.close();
                }
                if (target.active.get()) player.prepareAsync();
            } catch (Exception error) {
                main.post(() -> fail(target, "prepare_failed"));
            }
        });
    }

    private void firstFrame(Session target) {
        if (!current(target) || target.visible || !target.started || !target.rendering || !target.updated) return;
        target.visible = true;
        main.removeCallbacks(target.timeout);
        // A handover lands on a settled surface showing the native static cover, so it fades in too.
        view.animate().cancel();
        view.animate().alpha(1f).setDuration(200).start();
        status.accept("playing");
    }

    private void crop(Session target) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
        float videoAspect = (float) target.asset.metadata.width / target.asset.metadata.height;
        float viewAspect = (float) view.getWidth() / view.getHeight();
        Matrix transform = new Matrix();
        if (!fitXY) transform.setScale(Math.max(1f, videoAspect / viewAspect), Math.max(1f, viewAspect / videoAspect),
                view.getWidth() / 2f, view.getHeight() / 2f);
        view.setTransform(transform);
    }

    private boolean current(Session target) {
        if (closed || session != target || !target.active.get() || owner != this) return false;
        if (target.guard.permits()) return true;
        stop();
        status.accept("stale_render");
        return false;
    }

    private void fail(Session target, String reason) {
        if (!current(target)) return;
        stop();
        status.accept(reason);
    }

    public void stop() {
        requireMain();
        // A pending handover must still release the previous view, otherwise its layer is orphaned.
        Runnable pending = pendingDetach;
        pendingDetach = null;
        handoverFrom = null;
        if (pending != null) pending.run();
        Session previous = session;
        session = null;
        view.animate().cancel();
        view.setAlpha(0f);
        if (owner == this) owner = null;
        if (previous == null) return;
        previous.active.set(false);
        previous.guard.close();
        main.removeCallbacks(previous.timeout);
        MEDIA.post(() -> {
            previous.asset.close();
            if (previous.player != null) {
                try { previous.player.release(); } catch (RuntimeException ignored) {}
                previous.player = null;
            }
            if (previous.surface != null) {
                previous.surface.release();
                previous.surface = null;
            }
        });
    }

    @Override public void close() {
        requireMain();
        stop();
        closed = true;
        view.setSurfaceTextureListener(null);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (session == null) return;
        if (session.player == null) prepare(session, texture);
        else if (pendingDetach != null) attach(session, texture);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (session != null) crop(session);
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        stop();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        if (session != null && current(session) && session.rendering) {
            session.updated = true;
            firstFrame(session);
        }
    }

    private static void requireMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("owner_thread");
    }

    private final class Session {
        final ArtworkProviderClient.OpenedAsset asset;
        ArtworkRenderGuard guard;
        final AtomicBoolean active = new AtomicBoolean(true);
        final Runnable timeout = () -> fail(this, "first_frame_timeout");
        boolean preparing;
        boolean started;
        boolean rendering;
        boolean updated;
        boolean visible;
        // Media lane only; UI invalidates the session before enqueuing its release.
        MediaPlayer player;
        Surface surface;

        Session(ArtworkProviderClient.OpenedAsset asset, ArtworkRenderGuard guard) {
            this.asset = asset;
            this.guard = guard;
        }
    }
}
