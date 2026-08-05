package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

/** Compact mockup-style color swatch with a selection ring that never scales out of bounds. */
final class SettingsColorSwatchView extends View {
    private static final int[] CUSTOM_COLORS = {
            0xFFF44336,
            0xFFFF9800,
            0xFFFFD54F,
            0xFF66BB6A,
            0xFF29B6F6,
            0xFFAB47BC,
            0xFFF44336
    };

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean custom;
    private int color;
    private boolean selectedState;

    SettingsColorSwatchView(Context context, String colorValue, boolean custom) {
        super(context);
        this.custom = custom;
        color = Color.parseColor(colorValue);
        setClickable(true);
        setFocusable(true);

        whiteBorderPaint.setStyle(Paint.Style.STROKE);
        whiteBorderPaint.setStrokeWidth(dp(2f));
        whiteBorderPaint.setColor(Color.WHITE);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(2f));
        selectionPaint.setColor(0xFF9A6A12);
        plusPaint.setStyle(Paint.Style.STROKE);
        plusPaint.setStrokeCap(Paint.Cap.ROUND);
        plusPaint.setStrokeWidth(dp(2f));
        plusPaint.setColor(0xFF1B222C);
    }

    void setSelectedState(boolean selected) {
        if (selectedState == selected) return;
        selectedState = selected;
        setSelected(selected);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (custom) {
            fillPaint.setShader(new SweepGradient(
                    width / 2f,
                    height / 2f,
                    CUSTOM_COLORS,
                    null));
        } else {
            fillPaint.setShader(null);
            fillPaint.setColor(color);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float fillRadius = dp(custom ? 12.5f : 11.5f);
        if (selectedState) {
            canvas.drawCircle(cx, cy, dp(15f), selectionPaint);
        }
        canvas.drawCircle(cx, cy, fillRadius, fillPaint);
        canvas.drawCircle(cx, cy, fillRadius, whiteBorderPaint);
        if (custom && !selectedState) {
            Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            centerPaint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, dp(6f), centerPaint);
            canvas.drawLine(cx - dp(3f), cy, cx + dp(3f), cy, plusPaint);
            canvas.drawLine(cx, cy - dp(3f), cx, cy + dp(3f), plusPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
