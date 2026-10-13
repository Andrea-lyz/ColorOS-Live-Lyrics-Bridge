package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Local-FD renderer. Mounting, geometry, shape and official transitions belong to verified hosts. */
public final class DynamicArtworkRenderer implements AutoCloseable, TextureView.SurfaceTextureListener {
    private static final long FADE_IN_MS = 320;
    private static final long FADE_OUT_MS = 220;
    /**
     * Power experiment only, set with {@code adb shell setprop}: decode into an offscreen reader
     * and discard frames, leaving the native static cover on screen. Read once per session.
     */
    private static final String DECODE_ONLY_PROPERTY = "debug.cll.artwork.decode_only";
    private static final Handler MEDIA;
    private static DynamicArtworkRenderer owner;
    static {
        HandlerThread thread = new HandlerThread("bridge-artwork-media");
        thread.start();
        MEDIA = new Handler(thread.getLooper());
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArtworkFrameDispatch frames = new ArtworkFrameDispatch(task -> main.post(task));
    private TextureView view;
    private final Consumer<String> status;
    private boolean fitXY;
    private Session session;
    private boolean closed;
    private TextureView handoverFrom;
    private Runnable pendingDetach;
    private ValueAnimator opacity;
    /** User cap in fps; {@link ArtworkDisplaySettings#FRAME_RATE_UNLIMITED} keeps the direct surface. */
    private volatile int frameRateLimit = ArtworkDisplaySettings.FRAME_RATE_UNLIMITED;

    public DynamicArtworkRenderer(TextureView view, Consumer<String> status) {
        this(view, status, false);
    }

    public DynamicArtworkRenderer(TextureView view, Consumer<String> status, boolean fitXY) {
        this.view = view;
        this.status = status;
        this.fitXY = fitXY;
        adopt(view);
    }

    /**
     * Applies to the running decoder when it already paces frames; switching between capped and
     * uncapped takes effect from the next playback session.
     */
    public void setFrameRateLimit(int fps) {
        frameRateLimit = fps;
        Session active = session;
        if (active != null) active.pacer.setLimit(paceFor(fps));
    }

    private static int paceFor(int fps) {
        return fps >= ArtworkDisplaySettings.FRAME_RATE_UNLIMITED ? 0 : fps;
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
        return !closed && active != null && active.started && !active.retiring && active.hasPlayer();
    }

    /** The first frame is on screen: false while preparing, between a handover and its next frame, and after stop. */
    public boolean isShowing() {
        return isPlaying() && session.visible;
    }

    public boolean isFadingToStatic() {
        requireMain();
        return !closed && session != null && session.retiring;
    }

    /** A hidden same-song host returns through a fresh frame, without restarting its decoder. */
    public void reveal() {
        requireMain();
        Session active = session;
        if (!isPlaying() || !current(active)) return;
        frames.clear();
        cancelOpacity();
        view.setAlpha(0f);
        active.visible = false;
        active.updated = false;
        main.removeCallbacks(active.timeout);
        if (!active.decodeOnly) main.postDelayed(active.timeout, ArtworkSurfaceHandover.MAX_HOLD_MS);
    }

    /** Only a settled, still-current paused song may keep its last frame while returning to static. */
    public boolean fadeToStatic(ArtworkRenderGuard guard, Runnable completed) {
        requireMain();
        Session active = session;
        if (!isShowing() || pendingDetach != null || view.getAlpha() <= 0f || !guard.permits()) {
            guard.close();
            return false;
        }
        active.guard.close();
        active.guard = guard;
        active.retiring = true;
        main.removeCallbacks(active.timeout);
        MEDIA.post(() -> {
            if (!active.active.get() || !active.hasPlayer()) return;
            try {
                if (active.paced != null) active.paced.pause();
                else active.player.pause();
            }
            catch (RuntimeException error) { main.post(() -> fail(active, "pause_failed")); }
        });
        animateOpacity(active, 0f, FADE_OUT_MS, completed);
        return true;
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
        if (!guard.permits() || !active.hasPlayer()) { guard.close(); return false; }
        frames.clear();
        cancelOpacity();
        active.guard.close();
        active.guard = guard;
        active.visible = false;
        active.updated = false;
        main.removeCallbacks(active.timeout);
        if (!active.decodeOnly) main.postDelayed(active.timeout, ArtworkSurfaceHandover.MAX_HOLD_MS);
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
                if (!target.hasPlayer() || !target.active.get()) throw new IllegalStateException("handover_lost");
                if (target.reader == null) {
                    next = new Surface(texture);
                    if (target.paced != null) target.paced.setSurface(next);
                    else target.player.setSurface(next);
                    stale = target.surface;
                    target.surface = next;
                    attached = true;
                }
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
                Surface decoderSink;
                if (decodeOnlyExperiment()) {
                    target.decodeOnly = true;
                    target.reader = ImageReader.newInstance(Math.max(1, target.asset.metadata.width),
                            Math.max(1, target.asset.metadata.height), ImageFormat.PRIVATE, 3);
                    target.reader.setOnImageAvailableListener(reader -> {
                        Image frame = reader.acquireLatestImage();
                        if (frame != null) frame.close();
                    }, MEDIA);
                    decoderSink = target.reader.getSurface();
                    main.post(() -> main.removeCallbacks(target.timeout));
                } else {
                    target.surface = new Surface(texture);
                    decoderSink = target.surface;
                    if (paceFor(target.limitAtStart) > 0) {
                        startPaced(target);
                        return;
                    }
                }
                target.player = new MediaPlayer();
                MediaPlayer player = target.player;
                player.setSurface(decoderSink);
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
        if (!current(target) || target.visible || target.retiring || !target.started || !target.rendering || !target.updated) return;
        target.visible = true;
        main.removeCallbacks(target.timeout);
        // A handover lands on a settled surface showing the native static cover, so it fades in too.
        animateOpacity(target, 1f, FADE_IN_MS, null);
        status.accept("playing");
    }

    private void animateOpacity(Session target, float alpha, long durationMs, Runnable completed) {
        cancelOpacity();
        ValueAnimator next = ValueAnimator.ofFloat(view.getAlpha(), alpha);
        opacity = next;
        // An interrupted fade continues from the current opacity rather than flashing back to an endpoint.
        next.setDuration(Math.max(1, Math.round(durationMs * Math.abs(alpha - view.getAlpha()))));
        next.setInterpolator(new AccelerateDecelerateInterpolator());
        next.addUpdateListener(animation -> {
            if (opacity == next && current(target)) view.setAlpha((float) animation.getAnimatedValue());
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                // cancel() also calls onAnimationEnd; a retired animation cannot remove a newer mount.
                if (opacity != next) return;
                opacity = null;
                if (completed != null && current(target)) completed.run();
            }
        });
        next.start();
    }

    private void cancelOpacity() {
        ValueAnimator previous = opacity;
        opacity = null;
        if (previous != null) previous.cancel();
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
        frames.clear();
        // A pending handover must still release the previous view, otherwise its layer is orphaned.
        Runnable pending = pendingDetach;
        pendingDetach = null;
        handoverFrom = null;
        if (pending != null) pending.run();
        Session previous = session;
        session = null;
        cancelOpacity();
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
            if (previous.paced != null) {
                previous.paced.release();
                previous.paced = null;
            }
            if (previous.reader != null) {
                previous.reader.close();
                previous.reader = null;
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
        Session target = session;
        TextureView source = view;
        // getTextureLayer() can invoke this from draw too. Preparation's failure/status callbacks
        // may remove the mount, so they must not run inside that framework stack.
        main.post(() -> {
            if (target == null || session != target || closed || view != source || source.getSurfaceTexture() != texture) return;
            if (!target.hasPlayer()) prepare(target, texture);
            else if (pendingDetach != null && current(target)) attach(target, texture);
        });
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (session != null) crop(session);
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Session target = session;
        TextureView source = view;
        if (target != null) { target.guard.close(); target.visible = false; }
        frames.clear();
        main.post(() -> {
            if (target != null && session == target && view == source) stop();
        });
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        Session target = session;
        TextureView source = view;
        if (target == null || closed || owner != this || !target.active.get()) return;
        // TextureView.draw -> applyUpdate -> this callback -> draw still uses mLayer afterward.
        // Revoke immediately, but never call current()/status/mount.close() in this stack.
        if (!target.guard.permits()) {
            target.visible = false;
            cancelOpacity();
            source.setAlpha(0f);
        }
        frames.offer(() -> {
            if (session != target || closed || view != source || source.getSurfaceTexture() != texture) return;
            if (current(target) && target.rendering) {
                target.updated = true;
                firstFrame(target);
            }
        });
    }

    /**
     * Media lane. Capped playback decodes straight to the view and decides per frame whether its
     * release renders, so skipped frames never trigger a window redraw.
     */
    private void startPaced(Session target) throws java.io.IOException {
        target.pacer.setLimit(paceFor(target.limitAtStart));
        ArtworkPacedPlayer paced = new ArtworkPacedPlayer(MEDIA, target.pacer, new ArtworkPacedPlayer.Listener() {
            @Override public void onRenderingStart() {
                main.post(() -> {
                    if (current(target)) {
                        target.rendering = true;
                        target.updated = false;
                    }
                });
            }

            @Override public void onError() {
                main.post(() -> fail(target, "decode_failed"));
            }
        });
        target.paced = paced;
        try {
            paced.start(target.asset.descriptor.getFileDescriptor(), target.asset.metadata.fileBytes, target.surface);
        } finally {
            // The extractor duplicates the descriptor. No Java FD remains for the playback lifetime.
            target.asset.close();
        }
        main.post(() -> {
            if (!current(target)) return;
            target.started = true;
            status.accept("waiting_first_frame");
            firstFrame(target);
        });
    }

    private static boolean decodeOnlyExperiment() {
        return "1".equals(experimentProperty(DECODE_ONLY_PROPERTY));
    }

    private static String experimentProperty(String name) {
        try {
            Object value = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class)
                    .invoke(null, name);
            return value instanceof String text ? text : "";
        } catch (ReflectiveOperationException | RuntimeException error) {
            return "";
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
        boolean retiring;
        // Media lane only; UI invalidates the session before enqueuing its release.
        MediaPlayer player;
        /** The TextureView surface the decoder renders to. */
        Surface surface;
        /** Decoder sink for the decode-only power experiment. */
        ImageReader reader;
        /** Decoder under a frame-rate cap, used instead of {@link #player}. */
        ArtworkPacedPlayer paced;
        boolean decodeOnly;
        final ArtworkFramePacer pacer = new ArtworkFramePacer();
        final int limitAtStart = frameRateLimit;

        boolean hasPlayer() {
            return player != null || paced != null;
        }

        Session(ArtworkProviderClient.OpenedAsset asset, ArtworkRenderGuard guard) {
            this.asset = asset;
            this.guard = guard;
        }
    }
}
