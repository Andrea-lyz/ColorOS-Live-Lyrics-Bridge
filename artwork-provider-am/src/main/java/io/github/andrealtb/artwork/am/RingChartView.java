package io.github.andrealtb.artwork.am;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.List;

/** Donut chart with a number in the middle; segments sweep in when the data changes. */
final class RingChartView extends View {
    record Segment(float value, int color) {}

    private static final float GAP_DEGREES = 3f;
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF oval = new RectF();
    private List<Segment> segments = List.of();
    private String center = "";
    private String caption = "";
    private float progress = 1f;
    private ValueAnimator animator;

    RingChartView(Context context) {
        super(context);
        arc.setStyle(Paint.Style.STROKE);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(context.getColor(R.color.am_field));
        number.setColor(context.getColor(R.color.am_text));
        number.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        number.setTextAlign(Paint.Align.CENTER);
        label.setColor(context.getColor(R.color.am_text_secondary));
        label.setTextAlign(Paint.Align.CENTER);
    }

    void setData(List<Segment> segments, String center, String caption, boolean animate) {
        this.segments = List.copyOf(segments);
        this.center = center;
        this.caption = caption;
        if (animator != null) animator.cancel();
        if (!animate) { progress = 1f; invalidate(); return; }
        animator = ValueAnimator.ofFloat(0f, 1f).setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator(1.8f));
        animator.addUpdateListener(animation -> { progress = (float) animation.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float size = Math.min(width, height), stroke = size * 0.13f;
        arc.setStrokeWidth(stroke);
        track.setStrokeWidth(stroke);
        float left = (width - size) / 2f + stroke / 2f, top = (height - size) / 2f + stroke / 2f;
        oval.set(left, top, left + size - stroke, top + size - stroke);
        number.setTextSize(size * 0.25f);
        label.setTextSize(size * 0.1f);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawArc(oval, 0, 360, false, track);
        float total = 0;
        int parts = 0;
        for (Segment segment : segments) if (segment.value() > 0) { total += segment.value(); parts++; }
        if (total > 0) {
            float gap = parts > 1 ? GAP_DEGREES : 0f, start = -90f, end = -90f + 360f * progress;
            for (Segment segment : segments) {
                if (segment.value() <= 0) continue;
                float sweep = segment.value() / total * 360f;
                float visible = Math.min(start + sweep - gap, end) - start;
                if (visible > 0) {
                    arc.setColor(segment.color());
                    canvas.drawArc(oval, start, visible, false, arc);
                }
                start += sweep;
            }
        }
        float x = oval.centerX(), y = oval.centerY();
        float lift = caption.isEmpty() ? 0f : label.getTextSize() * 0.55f;
        canvas.drawText(center, x, y + number.getTextSize() * 0.35f - lift, number);
        if (!caption.isEmpty()) canvas.drawText(caption, x, y + number.getTextSize() * 0.35f + label.getTextSize() * 1.15f - lift, label);
    }
}
