package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.util.function.Consumer;

/** Main-thread frame coalescing. The scheduler must enqueue, never invoke inline during TextureView.draw. */
final class ArtworkFrameDispatch {
    private final Consumer<Runnable> post;
    private Runnable pending;
    private long epoch;

    ArtworkFrameDispatch(Consumer<Runnable> post) { this.post = post; }

    void offer(Runnable action) {
        boolean queued = pending != null;
        pending = action;
        if (queued) return;
        long issued = epoch;
        post.accept(() -> {
            if (epoch != issued) return;
            Runnable next = pending;
            pending = null;
            if (next != null) next.run();
        });
    }

    void clear() {
        epoch++;
        pending = null;
    }
}
