package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** C16 single-slot only. Keeps the native image and touch owner in place. */
public final class ArtworkImmersiveMount implements ArtworkVideoMount {
    public static final class Unsupported extends IllegalArgumentException {
        public final String reason;
        Unsupported(String reason) { super(reason); this.reason = reason; }
    }
    private final ImageView image;
    private final FrameLayout parent;
    private final ClipLayer layer;
    public final TextureView texture;
    private boolean closed;
    private int width = -1;
    private int height = -1;
    private float x;
    private float y;
    private ViewOutlineProvider outlineProvider;
    private android.graphics.drawable.Drawable drawable;

    public ArtworkImmersiveMount(ImageView image) throws ReflectiveOperationException {
        this.image = image;
        if (image.getClass().getName().equals("com.oplus.systemui.plugins.shared.template.component.media.view.SmartShapeableImageView")
                && image.getParent() instanceof FrameLayout frame) parent = frame;
        else throw new Unsupported("single_slot_parent");
        validateShape(image);
        layer = new ClipLayer(image.getContext());
        texture = new TextureView(image.getContext());
        layer.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        layer.setClickable(false);
        layer.setFocusable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        layer.setVisibility(View.INVISIBLE);
        // Ask the actual native view for its current outline, including OPlus smooth-round metadata.
        layer.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                ViewOutlineProvider provider = image.getOutlineProvider();
                if (provider != null) provider.getOutline(image, outline);
                else outline.setEmpty();
            }
        });
        layer.setClipToOutline(true);
        parent.addView(layer, parent.indexOfChild(image) + 1, new FrameLayout.LayoutParams(1, 1));
        try { sync(); }
        catch (ReflectiveOperationException | RuntimeException error) { close(); throw error; }
    }

    public void sync() throws ReflectiveOperationException {
        if (closed || image.getParent() != parent) throw new IllegalStateException("mount_parent_changed");
        ArtworkVideoMount.syncMotion(layer, image);
        if (image.getWidth() <= 0 || image.getHeight() <= 0) return;
        if (width == image.getWidth() && height == image.getHeight() && x == image.getX() && y == image.getY()
                && outlineProvider == image.getOutlineProvider() && drawable == image.getDrawable()) return;
        validateShape(image);
        Path path = materialPath(image);
        layer.path.set(path);
        FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) layer.getLayoutParams();
        if (bounds.width != image.getWidth() || bounds.height != image.getHeight()) {
            bounds.width = image.getWidth();
            bounds.height = image.getHeight();
            layer.setLayoutParams(bounds);
        }
        layer.setX(image.getX());
        layer.setY(image.getY());
        layer.setPivotX(image.getPivotX());
        layer.setPivotY(image.getPivotY());
        layer.setTranslationZ(image.getTranslationZ());
        layer.setElevation(image.getElevation());
        layer.invalidateOutline();
        layer.invalidate();
        width = image.getWidth();
        height = image.getHeight();
        x = image.getX();
        y = image.getY();
        outlineProvider = image.getOutlineProvider();
        drawable = image.getDrawable();
    }

    public boolean settled() {
        return !closed && image.isAttachedToWindow() && image.isShown()
                && image.getWindowVisibility() == View.VISIBLE && image.getWidth() > 0 && image.getHeight() > 0
                && image.getAlpha() == 1f && image.getScaleX() == 1f && image.getScaleY() == 1f
                && image.getRotation() == 0f && image.getRotationX() == 0f && image.getRotationY() == 0f
                && image.getTranslationX() == 0f && image.getTranslationY() == 0f;
    }

    public void show(boolean visible) { if (!closed) layer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE); }

    @Override public TextureView texture() { return texture; }
    @Override public boolean fitXY() { return true; }

    private static void validateShape(ImageView image) throws ReflectiveOperationException {
        if (!image.getClipToOutline() || image.getOutlineProvider() == null || image.getForeground() != null
                || image.getImageTintList() != null || image.getColorFilter() != null || image.getImageAlpha() != 255
                || image.getPaddingLeft() != 0 || image.getPaddingTop() != 0
                || image.getPaddingRight() != 0 || image.getPaddingBottom() != 0
                || image.getScaleType() != ImageView.ScaleType.FIT_XY) throw new Unsupported("mount_shape_unsupported");
        Class<?> material = image.getClass().getSuperclass();
        if (!material.getName().equals("com.google.android.material.imageview.ShapeableImageView")) {
            throw new Unsupported("material_parent_changed");
        }
        Method stroke = material.getMethod("getStrokeWidth");
        if (stroke.getReturnType() != float.class || ((Number) stroke.invoke(image)).floatValue() != 0f) {
            throw new Unsupported("material_stroke_unsupported");
        }
        for (String side : new String[]{"Left", "Top", "Right", "Bottom"}) {
            if (((Number) material.getMethod("getContentPadding" + side).invoke(image)).intValue() != 0) {
                throw new Unsupported("material_content_padding");
            }
        }
        Outline outline = new Outline();
        image.getOutlineProvider().getOutline(image, outline);
        if (outline.isEmpty() || !outline.canClip()) throw new Unsupported("outline_not_clippable");
    }

    // Material keeps an interior convex path and an exterior subtraction mask. Never use the mask as a clip.
    private static Path materialPath(ImageView image) throws ReflectiveOperationException {
        Path selected = null;
        Field[] fields = image.getClass().getSuperclass().getDeclaredFields();
        if (fields.length > 64) throw new Unsupported("material_field_budget");
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != Path.class) continue;
            field.setAccessible(true);
            Path path = (Path) field.get(image);
            if (path == null || path.isEmpty() || !path.isConvex()) continue;
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            if (bounds.left < 0 || bounds.top < 0 || bounds.right > image.getWidth() || bounds.bottom > image.getHeight()
                    || bounds.width() <= 0 || bounds.height() <= 0) continue;
            if (selected != null) throw new Unsupported("material_path_ambiguous");
            selected = path;
        }
        if (selected == null) throw new Unsupported("material_path_missing");
        return selected;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        layer.setVisibility(View.INVISIBLE);
        parent.removeView(layer);
    }

    private static final class ClipLayer extends FrameLayout {
        final Path path = new Path();
        ClipLayer(Context context) { super(context); }
        @Override protected void dispatchDraw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(path);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }
}
