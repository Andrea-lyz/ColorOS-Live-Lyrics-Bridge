package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.view.TextureView;

/** The active host alone owns its temporary layer. Native artwork is never moved or replaced. */
public interface ArtworkVideoMount extends AutoCloseable {
    TextureView texture();
    boolean fitXY();
    void sync() throws ReflectiveOperationException;
    void show(boolean visible);
    @Override void close();

    static void syncMotion(android.view.View layer, android.widget.ImageView image) {
        layer.setVisibility(image.isShown() && image.getWidth() > 0 && image.getHeight() > 0
                ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        layer.setAlpha(image.getAlpha());
        layer.setPivotX(image.getPivotX());
        layer.setPivotY(image.getPivotY());
        layer.setScaleX(image.getScaleX());
        layer.setScaleY(image.getScaleY());
        layer.setRotation(image.getRotation());
        layer.setRotationX(image.getRotationX());
        layer.setRotationY(image.getRotationY());
    }
}
