package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/** Passive, event-driven geometry survey. Observations never grant display capability. */
public final class ArtworkHostProbe implements AutoCloseable {
    private final WeakReference<ImageView> image;
    private final WeakReference<ViewGroup> root;
    private final ArtworkTrace trace;
    private final ArtworkPlaybackPolicy.Surface surface;
    private final View.OnLayoutChangeListener layout = (view, l, t, r, b, ol, ot, or, ob) -> sample();
    private boolean active;

    public ArtworkHostProbe(ImageView image, ViewGroup root, ArtworkTrace trace,
            ArtworkPlaybackPolicy.Surface surface) {
        this.image = new WeakReference<>(image);
        this.root = new WeakReference<>(root);
        this.trace = trace;
        this.surface = surface;
    }

    public void start() {
        ImageView target = image.get();
        ViewGroup host = root.get();
        if (active || target == null || host == null) return;
        active = true;
        target.addOnLayoutChangeListener(layout);
        host.addOnLayoutChangeListener(layout);
        sample();
    }

    public void sample() {
        if (!active || !ArtworkTrace.enabled()) return;
        ImageView target = image.get();
        ViewGroup host = root.get();
        if (target == null || host == null) return;
        trace.state("ARTWORK_HOST_GEOMETRY", () -> describe(target, host));
    }

    private String describe(ImageView target, ViewGroup host) {
        boolean rotatable = hasType(target, "com.oplus.systemui.plugins.shared.template.component.media.view.RotatableImageView");
        boolean custom = hasType(target, "com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView");
        boolean material = hasType(target, "com.google.android.material.imageview.ShapeableImageView");
        int nextId = host.getResources().getIdentifier("img_large_album_art_next", "id",
                target.getResources().getResourcePackageName(target.getId()));
        View next = nextId == 0 ? null : host.findViewById(nextId);
        boolean dual = next instanceof ImageView && next != target;
        Outline outline = new Outline();
        boolean outlineReadable = false;
        if (target.getOutlineProvider() != null && target.getWidth() > 0 && target.getHeight() > 0) {
            target.getOutlineProvider().getOutline(target, outline);
            outlineReadable = !outline.isEmpty();
        }
        Rect bounds = new Rect();
        boolean rectOutline = outlineReadable && outline.getRect(bounds);
        String transition = dual ? "dual_image_unverified" : target.getDrawable() instanceof TransitionDrawable
                ? "drawable_crossfade_unverified" : "no_transition_observed";
        return "surface=" + surface + " imageType=" + target.getClass().getName()
                + " parentType=" + (target.getParent() == null ? "none" : target.getParent().getClass().getName())
                + " width=" + target.getWidth() + " height=" + target.getHeight()
                + " left=" + target.getLeft() + " top=" + target.getTop()
                + " shown=" + target.isShown() + " alpha=" + target.getAlpha()
                + " scaleX=" + target.getScaleX() + " scaleY=" + target.getScaleY() + " rotation=" + target.getRotation()
                + " translationX=" + target.getTranslationX() + " translationY=" + target.getTranslationY()
                + " scaleType=" + target.getScaleType()
                + " padded=" + (target.getPaddingLeft() != 0 || target.getPaddingTop() != 0
                    || target.getPaddingRight() != 0 || target.getPaddingBottom() != 0)
                + " foreground=" + (target.getForeground() != null) + " tinted=" + (target.getImageTintList() != null)
                + " clipToOutline=" + target.getClipToOutline() + " outlineReadable=" + outlineReadable
                + " outlineCanClip=" + (outlineReadable && outline.canClip()) + " roundRectOutline=" + rectOutline
                + " outlineRadius=" + (rectOutline ? outline.getRadius() : -1f)
                + " shape=" + (rotatable ? "rotatable_effect_unverified" : custom ? "custom_unverified"
                    : material ? "material_unverified" : "unsupported")
                + " transition=" + transition + " nextShown=" + (next != null && next.isShown())
                + " display=disabled";
    }

    private static boolean hasType(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(name)) return true;
        }
        return false;
    }

    @Override public void close() {
        if (!active) return;
        active = false;
        ImageView target = image.get();
        ViewGroup host = root.get();
        if (target != null) target.removeOnLayoutChangeListener(layout);
        if (host != null) host.removeOnLayoutChangeListener(layout);
        trace.state("ARTWORK_HOST_PROBE_RELEASED", () -> "surface=" + surface + " listenerCount=0");
    }
}
