package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.drawable.Drawable;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** C17 current-slot outline/stroke or same-frame fullscreen mirrors below the vendor's blur/masks. */
@androidx.annotation.RequiresApi(33)
public final class ArtworkC17Mount implements ArtworkVideoMount {
    static final String MIRRORS = """
            uniform shader video;
            uniform float2 outputSize;
            uniform float3 square;
            half4 main(float2 p) {
                float u = (p.x - square.x) / square.z;
                float v = (p.y - square.y) / square.z;
                if (u < 0.0 || u > 1.0 || v < -1.0 || v > 2.0) return half4(0.0);
                v = v < 0.0 ? -v : (v > 1.0 ? 2.0 - v : v);
                float2 pixel = clamp(float2(u, v) * outputSize, float2(0.5), outputSize - 0.5);
                return video.eval(pixel);
            }
            """;
    private final ImageView image;
    private final FrameLayout parent;
    private final FrameLayout layer;
    private final TextureView texture;
    private final boolean background;
    private final RuntimeShader mirrors;
    private final ImageView top, bottom;
    private boolean closed;
    private int width, height, left, topEdge, side;

    public ArtworkC17Mount(ImageView image, boolean background, Runnable detached) throws ReflectiveOperationException {
        this.image = image;
        this.background = background;
        if (!(image.getParent() instanceof FrameLayout frame)) throw unsupported("c17_parent");
        parent = frame;
        texture = new TextureView(image.getContext());
        if (background) {
            if (!parent.getClass().getName().equals(
                    "com.oplus.systemui.plugins.shared.template.component.media.view.MediaFullScreenContentView")) {
                throw unsupported("c17_background_parent");
            }
            top = asImage(ArtworkC17Access.resource(parent, "topMirror"));
            bottom = asImage(ArtworkC17Access.resource(parent, "bottomMirror"));
            mirrors = new RuntimeShader(MIRRORS);
            texture.setRenderEffect(RenderEffect.createRuntimeShaderEffect(mirrors, "video"));
            layer = new FrameLayout(image.getContext());
        } else {
            top = bottom = null;
            mirrors = null;
            layer = new ForegroundLayer(image.getContext(), image);
            layer.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    if (image.getOutlineProvider() == null) outline.setEmpty();
                    else image.getOutlineProvider().getOutline(image, outline);
                }
            });
            layer.setClipToOutline(true);
        }
        layer.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        layer.setClickable(false);
        layer.setFocusable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        layer.setVisibility(View.INVISIBLE);
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        layer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) {
                // A background renderer may detach/re-attach while the section itself remains alive.
                // Do not leave a stopped decoder's attempted stamp blocking the next request.
                main.post(() -> { if (!closed) detached.run(); });
            }
        });
        parent.addView(layer, background ? parent.getChildCount() : parent.indexOfChild(image) + 1,
                new FrameLayout.LayoutParams(1, 1));
        try { sync(); }
        catch (RuntimeException error) { close(); throw error; }
    }

    private static ImageView asImage(View view) {
        if (!(view instanceof ImageView image)) throw unsupported("c17_mirror_missing");
        return image;
    }

    @Override public void sync() {
        if (closed || image.getParent() != parent || layer.getParent() != parent) throw unsupported("c17_parent_changed");
        if (image.getImageTintList() != null || image.getColorFilter() != null || image.getImageAlpha() != 255
                || image.getPaddingLeft() != 0 || image.getPaddingTop() != 0
                || image.getPaddingRight() != 0 || image.getPaddingBottom() != 0
                || image.getScaleType() != ImageView.ScaleType.CENTER_CROP) throw unsupported("c17_image_effect");
        int w = background ? parent.getWidth() : image.getWidth();
        int h = background ? parent.getHeight() : image.getHeight();
        if (w <= 0 || h <= 0) throw unsupported("c17_bounds");
        if (background) {
            validateMirrors();
            if (w != width || h != height || left != image.getLeft() || topEdge != image.getTop() || side != image.getWidth()) {
                mirrors.setFloatUniform("outputSize", w, h);
                mirrors.setFloatUniform("square", image.getLeft(), image.getTop(), image.getWidth());
                // RenderEffect snapshots shader state: replace only when native geometry changed.
                texture.setRenderEffect(RenderEffect.createRuntimeShaderEffect(mirrors, "video"));
                left = image.getLeft(); topEdge = image.getTop(); side = image.getWidth();
            }
            // Native onLayout deliberately lays out only its three ImageViews. Lay out our own child;
            // keeping it inside contentContainer also preserves H4.i()'s whole-content lyric blur.
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layer.getLayoutParams();
            if (params.width != w || params.height != h) {
                params.width = w; params.height = h; layer.setLayoutParams(params);
            }
            if (layer.getMeasuredWidth() != w || layer.getMeasuredHeight() != h) {
                layer.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            }
            layer.layout(0, 0, w, h);
        } else {
            validateOutline();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layer.getLayoutParams();
            if (params.width != w || params.height != h) {
                params.width = w; params.height = h; layer.setLayoutParams(params);
            }
            layer.setX(image.getX()); layer.setY(image.getY());
            layer.setElevation(image.getElevation()); layer.setTranslationZ(image.getTranslationZ());
            layer.invalidateOutline();
        }
        width = w; height = h;
    }

    private void validateOutline() {
        if (!image.getClipToOutline() || image.getOutlineProvider() == null
                || !(image.getForeground() instanceof android.graphics.drawable.GradientDrawable)) throw unsupported("c17_outline_profile");
        Outline outline = new Outline();
        image.getOutlineProvider().getOutline(image, outline);
        if (outline.isEmpty() || !outline.canClip()) throw unsupported("c17_outline_unclippable");
    }

    private void validateMirrors() {
        int size = image.getWidth();
        if (!ArtworkC17State.mirrorsMatch(bounds(image), bounds(top), bounds(bottom))
                || image.getForeground() != null) throw unsupported("c17_square_geometry");
        for (ImageView mirror : new ImageView[]{top, bottom}) {
            if (mirror.getParent() != parent || !mirror.isShown() || mirror.getWidth() != size || mirror.getHeight() != size
                    || mirror.getLeft() != image.getLeft() || mirror.getScaleY() != -1f || mirror.getScaleX() != 1f
                    || mirror.getAlpha() != 1f || mirror.getImageAlpha() != 255 || mirror.getTranslationX() != 0
                    || mirror.getTranslationY() != 0 || mirror.getRotation() != 0
                    || mirror.getScaleType() != ImageView.ScaleType.CENTER_CROP) throw unsupported("c17_mirror_geometry");
        }
    }

    private static ArtworkC17State.Bounds bounds(ImageView view) {
        return new ArtworkC17State.Bounds(view.getLeft(), view.getTop(), view.getWidth(), view.getHeight());
    }

    @Override public TextureView texture() { return texture; }
    @Override public boolean fitXY() { return background; }
    @Override public void show(boolean visible) { if (!closed) layer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        layer.setVisibility(View.INVISIBLE);
        parent.removeView(layer);
        texture.setRenderEffect(null);
    }

    private static ArtworkImmersiveMount.Unsupported unsupported(String reason) {
        return new ArtworkImmersiveMount.Unsupported(reason);
    }

    private static final class ForegroundLayer extends FrameLayout {
        private final ImageView image;
        ForegroundLayer(Context context, ImageView image) { super(context); this.image = image; }
        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            // Draw the native stroke without stealing its callback, state, alpha or bounds.
            Drawable stroke = image.getForeground();
            if (stroke != null) stroke.draw(canvas);
        }
    }
}
