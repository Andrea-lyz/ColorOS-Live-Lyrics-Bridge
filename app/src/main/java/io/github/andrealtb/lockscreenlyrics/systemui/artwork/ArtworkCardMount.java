package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.lang.reflect.Field;

/** Local card profile. Read native shader alpha without modifying its uniforms or bitmap inputs. */
@androidx.annotation.RequiresApi(33)
public final class ArtworkCardMount implements ArtworkVideoMount {
    private final ImageView image;
    private final ViewGroup parent;
    private final ArtworkCardEffectAccess effects;
    private final MaskLayer layer;
    private final TextureView texture;
    private final Field leftToLeft;
    private final Field topToTop;
    private boolean closed;

    public ArtworkCardMount(ImageView image, ArtworkCardEffectAccess effects) throws ReflectiveOperationException {
        this.image = image;
        this.effects = effects;
        if (!(image.getParent() instanceof ViewGroup group)
                || !group.getClass().getName().equals("com.oplus.systemui.plugins.shared.view.template.media.MediaPlayerCardPageRootView")) {
            throw new ArtworkImmersiveMount.Unsupported("card_parent_contract");
        }
        parent = group;
        validate();
        Class<?> layoutType = image.getLayoutParams().getClass();
        // Actual C16 user DEX renames ConstraintLayout.LayoutParams to r.d. Do not mix loaders' params.
        if (!layoutType.getName().equals("r.d")) {
            throw new ArtworkImmersiveMount.Unsupported("card_layout_contract");
        }
        leftToLeft = layoutType.getField("d");
        topToTop = layoutType.getField("h");
        if (leftToLeft.getType() != int.class || topToTop.getType() != int.class) {
            throw new ArtworkImmersiveMount.Unsupported("card_constraint_contract");
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) layoutType.getConstructor(int.class, int.class)
                .newInstance(image.getWidth(), image.getHeight());
        leftToLeft.setInt(params, 0);
        topToTop.setInt(params, 0);
        params.leftMargin = image.getLeft();
        params.topMargin = image.getTop();
        layer = new MaskLayer(image.getContext());
        layer.setId(View.generateViewId());
        texture = new TextureView(image.getContext());
        layer.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        layer.setClickable(false);
        layer.setFocusable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        layer.setVisibility(View.INVISIBLE);
        layer.mask.setShader(effects.mask(image));
        parent.addView(layer, parent.indexOfChild(image) + 1, params);
        try { sync(); }
        catch (ReflectiveOperationException | RuntimeException error) { close(); throw error; }
    }

    private void validate() {
        if (!effects.settled(image) || image.getForeground() != null || image.getImageTintList() != null
                || image.getColorFilter() != null || image.getImageAlpha() != 255 || image.getClipToOutline()
                || image.getScaleType() != ImageView.ScaleType.CENTER_CROP
                || image.getPaddingLeft() != 0 || image.getPaddingTop() != 0
                || image.getPaddingRight() != 0 || image.getPaddingBottom() != 0) {
            throw new ArtworkImmersiveMount.Unsupported("card_draw_unsupported");
        }
    }

    @Override public void sync() throws ReflectiveOperationException {
        if (closed || image.getParent() != parent) throw new IllegalStateException("card_parent_changed");
        validate();
        follow();
    }
    private void follow() {
        if (closed || image.getParent() != parent) throw new IllegalStateException("card_parent_changed");
        ArtworkVideoMount.syncMotion(layer, image);
        layer.setTranslationX(image.getTranslationX());
        layer.setTranslationY(image.getTranslationY());
        if (image.getWidth() <= 0 || image.getHeight() <= 0) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) layer.getLayoutParams();
        if (params.width != image.getWidth() || params.height != image.getHeight()
                || params.leftMargin != image.getLeft() || params.topMargin != image.getTop()) {
            params.width = image.getWidth();
            params.height = image.getHeight();
            params.leftMargin = image.getLeft();
            params.topMargin = image.getTop();
            layer.setLayoutParams(params);
        }
        layer.setElevation(image.getElevation());
        layer.setTranslationZ(image.getTranslationZ());
    }
    @Override public TextureView texture() { return texture; }
    @Override public boolean fitXY() { return false; }
    @Override public void show(boolean visible) { if (!closed) layer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        layer.setVisibility(View.INVISIBLE);
        parent.removeView(layer);
        layer.mask.setShader(null);
    }
    private static final class MaskLayer extends FrameLayout {
        final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        MaskLayer(Context context) { super(context); mask.setBlendMode(BlendMode.DST_IN); }
        @Override protected void dispatchDraw(Canvas canvas) {
            if (!canvas.isHardwareAccelerated()) return;
            int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            super.dispatchDraw(canvas);
            canvas.drawPaint(mask);
            canvas.restoreToCount(save);
        }
    }
}
