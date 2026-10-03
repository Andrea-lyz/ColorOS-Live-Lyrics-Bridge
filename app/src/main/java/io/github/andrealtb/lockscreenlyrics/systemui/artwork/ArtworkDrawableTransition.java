package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;

/** Uses public draw/invalidations. Unknown pre-observation and reverse transitions stay static. */
public final class ArtworkDrawableTransition {
    private final ArtworkTransitionRegistry registry = new ArtworkTransitionRegistry();
    public void start(Drawable drawable) { registry.start(drawable); }
    public void reverseOrReset(Drawable drawable) { registry.reverseOrReset(drawable); }
    public void beginDraw(Drawable drawable) { registry.beginDraw(drawable); }
    public void invalidate(Drawable drawable) { registry.invalidate(drawable); }
    public void endDraw(Drawable drawable) { registry.endDraw(drawable); }
    public void release(Drawable drawable) { registry.release(drawable); }
    public void clear() { registry.clear(); }
    public String diagnostic(Drawable drawable) { return registry.diagnostic(drawable); }

    public boolean complete(Drawable drawable) {
        if (drawable == null) return false;
        if (!(drawable instanceof TransitionDrawable)) return true;
        if (drawable.getClass() != TransitionDrawable.class) return false;
        return registry.complete(drawable);
    }
}
