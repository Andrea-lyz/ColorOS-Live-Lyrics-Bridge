package io.github.andrealtb.artwork.am;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Rounded usage bar filled with the brand gradient. */
final class UsageBarView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float shown;
    private ValueAnimator animator;

    UsageBarView(Context context) {
        super(context);
        track.setColor(context.getColor(R.color.am_field));
    }

    void setFraction(float fraction, boolean animate) {
        float target = Math.max(0f, Math.min(1f, fraction));
        if (animator != null) animator.cancel();
        if (!animate) { shown = target; invalidate(); return; }
        animator = ValueAnimator.ofFloat(shown, target).setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(animation -> { shown = (float) animation.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        fill.setShader(new LinearGradient(0, 0, width, 0, AmUi.GRADIENT, null, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        float width = getWidth(), height = getHeight(), radius = height / 2f;
        rect.set(0, 0, width, height);
        canvas.drawRoundRect(rect, radius, radius, track);
        if (shown <= 0f) return;
        rect.right = Math.max(height, width * shown);
        canvas.drawRoundRect(rect, radius, radius, fill);
    }
}
