package io.github.andrealtb.artwork.am;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Stand-in "living" cover shown until a cached motion cover plays: colour fields drift under a
 * passing sheen. Redraws only while running and visible; turning the source off fades it to grey.
 */
final class LiveCoverView extends View {
    private static final int[] BLOBS = { 0xFFFF375F, 0xFFBF5AF2, 0xFF5E5CE6, 0xFFFF9F0A };
    private static final long FRAME_MS = 16;
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient[] shaders = new RadialGradient[BLOBS.length];
    private final Matrix matrix = new Matrix();
    private final long origin = SystemClock.uptimeMillis();
    private LinearGradient sheenShader;
    private ValueAnimator fade;
    private float saturation = 1f;
    private boolean running;
    private boolean shown;

    LiveCoverView(Context context) {
        super(context);
        blob.setBlendMode(BlendMode.SCREEN);
    }

    void setRunning(boolean running) {
        this.running = running;
        if (running) invalidate();
    }

    void setActive(boolean active, boolean animate) {
        float target = active ? 1f : 0.12f;
        if (fade != null) fade.cancel();
        if (!animate) { saturate(target); return; }
        fade = ValueAnimator.ofFloat(saturation, target).setDuration(450);
        fade.addUpdateListener(animation -> saturate((float) animation.getAnimatedValue()));
        fade.start();
    }

    private void saturate(float value) {
        saturation = value;
        ColorMatrix colors = new ColorMatrix();
        colors.setSaturation(value);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colors);
        base.setColorFilter(filter);
        blob.setColorFilter(filter);
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float radius = Math.max(width, height) * 0.7f;
        for (int i = 0; i < BLOBS.length; i++) {
            shaders[i] = new RadialGradient(0, 0, radius, new int[] { BLOBS[i], AmUi.alpha(BLOBS[i], 0x55), AmUi.alpha(BLOBS[i], 0) },
                    new float[] { 0f, 0.45f, 1f }, Shader.TileMode.CLAMP);
        }
        base.setShader(new LinearGradient(0, 0, width, height, 0xFF2B1446, 0xFF101031, Shader.TileMode.CLAMP));
        sheenShader = new LinearGradient(0, 0, width * 0.3f, height * 0.3f, new int[] { 0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF },
                null, Shader.TileMode.CLAMP);
        sheen.setShader(sheenShader);
    }

    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        if (width == 0 || sheenShader == null) return;
        float seconds = (SystemClock.uptimeMillis() - origin) / 1000f;
        canvas.drawRect(0, 0, width, height, base);
        for (int i = 0; i < shaders.length; i++) {
            float phase = i * 1.7f;
            float x = width * (0.5f + 0.4f * (float) Math.sin(seconds * (0.23f + i * 0.05f) + phase));
            float y = height * (0.5f + 0.4f * (float) Math.cos(seconds * (0.19f + i * 0.04f) + phase * 1.3f));
            matrix.setTranslate(x, y);
            shaders[i].setLocalMatrix(matrix);
            blob.setShader(shaders[i]);
            canvas.drawRect(0, 0, width, height, blob);
        }
        float cycle = (seconds % 7f) / 7f;
        if (cycle < 0.4f && saturation > 0.5f) {
            float offset = -0.35f + cycle / 0.4f * 1.45f;
            matrix.setTranslate(offset * width, offset * height);
            sheenShader.setLocalMatrix(matrix);
            canvas.drawRect(0, 0, width, height, sheen);
        }
        if (running && shown) postInvalidateDelayed(FRAME_MS);
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        shown = visible;
        if (visible && running) invalidate();
    }
}
