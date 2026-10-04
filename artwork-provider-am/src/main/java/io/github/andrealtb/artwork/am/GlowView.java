package io.github.andrealtb.artwork.am;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Soft coloured light behind the hero cover, breathing slowly; dims while the source is off. */
@SuppressLint("ViewConstructor") // Built in code only; never inflated from XML.
final class GlowView extends View {
    private static final int[] COLORS = { 0xFFFF375F, 0xFF5E5CE6, 0xFFBF5AF2 };
    private static final long FRAME_MS = 33;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient[] shaders = new RadialGradient[COLORS.length];
    private final Matrix matrix = new Matrix();
    private final long origin = SystemClock.uptimeMillis();
    private final int cover;
    private ValueAnimator fade;
    private float strength = 1f;
    private boolean running;
    private boolean shown;

    GlowView(Context context, int coverSize) {
        super(context);
        cover = coverSize;
    }

    void setRunning(boolean running) {
        this.running = running;
        if (running) invalidate();
    }

    void setActive(boolean active, boolean animate) {
        float target = active ? 1f : 0.3f;
        if (fade != null) fade.cancel();
        if (!animate) { strength = target; invalidate(); return; }
        fade = ValueAnimator.ofFloat(strength, target).setDuration(450);
        fade.addUpdateListener(animation -> { strength = (float) animation.getAnimatedValue(); invalidate(); });
        fade.start();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float radius = cover * 0.78f;
        for (int i = 0; i < COLORS.length; i++) {
            shaders[i] = new RadialGradient(0, 0, radius, new int[] { AmUi.alpha(COLORS[i], 0x70), AmUi.alpha(COLORS[i], 0x26),
                    AmUi.alpha(COLORS[i], 0) }, new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (shaders[0] == null) return;
        float seconds = (SystemClock.uptimeMillis() - origin) / 1000f;
        float centerX = getWidth() / 2f, centerY = getHeight() / 2f + cover * 0.06f;
        paint.setAlpha(Math.round(255 * strength));
        for (int i = 0; i < shaders.length; i++) {
            float side = i == 0 ? -1f : i == 1 ? 1f : 0f;
            float x = centerX + side * cover * 0.24f + cover * 0.07f * (float) Math.sin(seconds * 0.55f + i * 2.1f);
            float y = centerY + cover * 0.06f * (float) Math.cos(seconds * 0.45f + i * 1.3f) + (i == 2 ? cover * 0.18f : 0f);
            float scale = 1f + 0.07f * (float) Math.sin(seconds * 0.8f + i);
            matrix.setScale(scale, scale);
            matrix.postTranslate(x, y);
            shaders[i].setLocalMatrix(matrix);
            paint.setShader(shaders[i]);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
        if (running && shown) postInvalidateDelayed(FRAME_MS);
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        shown = visible;
        if (visible && running) invalidate();
    }
}
