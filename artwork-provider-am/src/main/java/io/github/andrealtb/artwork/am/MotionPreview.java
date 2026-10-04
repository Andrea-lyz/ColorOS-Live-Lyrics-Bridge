package io.github.andrealtb.artwork.am;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import java.io.File;
import java.io.IOException;

/**
 * Muted, looping in-app playback of one cached motion cover. The view stays transparent until the
 * first frame renders, so the animated placeholder underneath never flashes black.
 */
final class MotionPreview implements TextureView.SurfaceTextureListener {
    interface Listener {
        void onStarted();
        void onStopped();
    }

    private final TextureView view;
    private final Listener listener;
    private MediaPlayer player;
    private Surface surface;
    private File file;

    MotionPreview(TextureView view, Listener listener) {
        this.view = view;
        this.listener = listener;
        view.setAlpha(0f);
        view.setOpaque(false);
        view.setSurfaceTextureListener(this);
    }

    /** The file being shown, or null. */
    File current() { return file; }

    void play(File next) {
        release();
        file = next;
        view.animate().cancel();
        view.setAlpha(0f);
        if (next != null && view.isAvailable()) start(view.getSurfaceTexture());
    }

    void stop() {
        boolean was = file != null;
        release();
        file = null;
        view.animate().cancel();
        view.setAlpha(0f);
        if (was) listener.onStopped();
    }

    private void start(SurfaceTexture texture) {
        if (file == null || texture == null) return;
        surface = new Surface(texture);
        MediaPlayer next = new MediaPlayer();
        player = next;
        next.setOnPreparedListener(MediaPlayer::start);
        next.setOnInfoListener((source, what, extra) -> {
            if (source == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                view.animate().alpha(1f).setDuration(380).start();
                listener.onStarted();
            }
            return false;
        });
        next.setOnErrorListener((source, what, extra) -> {
            if (source == player) stop();
            return true;
        });
        try {
            next.setDataSource(file.getPath());
            next.setSurface(surface);
            next.setLooping(true);
            next.setVolume(0f, 0f);
            next.prepareAsync();
        } catch (IOException | RuntimeException error) {
            // The cache can drop an unpinned file at any time; the placeholder stays.
            stop();
        }
    }

    private void release() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (file != null && player == null) start(texture);
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        return true;
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {}

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
