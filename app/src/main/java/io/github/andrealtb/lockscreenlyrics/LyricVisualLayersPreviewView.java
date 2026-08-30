package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** Sticky visual contract preview for brightness lanes, row fade and edge masking. */
final class LyricVisualLayersPreviewView extends View {
    private static final float ACTIVE_REVEAL_FRACTION = 0.58f;
    private static final float TRANSLATION_REVEAL_FRACTION = 0.46f;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint translationPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF contentBounds = new RectF();
    private LyricUiConfig config = LyricUiConfig.defaults();
    private LyricUiPalette palette = LyricUiPalette.from(config);

    LyricVisualLayersPreviewView(Context context) {
        this(context, null);
    }

    LyricVisualLayersPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        backgroundPaint.setColor(0xFF18202A);
        mainPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        translationPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
    }

    void bind(LyricUiConfig next) {
        config = next == null ? LyricUiConfig.defaults() : next;
        palette = LyricUiPalette.from(config);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(dp(320f));
        int desiredHeight = Math.round(dp(196f));
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        contentBounds.set(0f, 0f, getWidth(), getHeight());
        canvas.drawRoundRect(contentBounds, dp(18f), dp(18f), backgroundPaint);

        int contentLayer = canvas.saveLayer(contentBounds, null);
        float x = dp(18f);
        // Center the complete three-group block optically: the previous-line top and the
        // next-line translation bottom keep roughly equal distance from the preview edges.
        float top = dp(19f);
        float availableWidth = Math.max(1f, getWidth() - dp(36f));

        mainPaint.setTextSize(sp(16.5f));
        translationPaint.setTextSize(sp(11.5f));
        drawInactiveGroup(
                canvas,
                getResources().getString(R.string.visual_preview_inactive_one_main),
                getResources().getString(R.string.visual_preview_inactive_one_translation),
                x,
                top + dp(19f),
                top + dp(38f));

        mainPaint.setTextSize(sp(20f));
        translationPaint.setTextSize(sp(12.5f));
        drawActiveGroup(
                canvas,
                getResources().getString(R.string.visual_preview_active_main),
                getResources().getString(R.string.visual_preview_active_translation),
                x,
                top + dp(75f),
                top + dp(96f),
                availableWidth);

        mainPaint.setTextSize(sp(16.5f));
        translationPaint.setTextSize(sp(11.5f));
        drawInactiveGroup(
                canvas,
                getResources().getString(R.string.visual_preview_inactive_two_main),
                getResources().getString(R.string.visual_preview_inactive_two_translation),
                x,
                top + dp(133f),
                top + dp(152f));

        applyVerticalFadeMask(canvas);
        canvas.restoreToCount(contentLayer);
    }

    private void drawActiveGroup(
            Canvas canvas,
            String main,
            String translation,
            float x,
            float mainBaseline,
            float translationBaseline,
            float availableWidth) {
        mainPaint.setColor(palette.focusedInactive);
        canvas.drawText(main, x, mainBaseline, mainPaint);
        float mainWidth = Math.min(availableWidth, mainPaint.measureText(main));
        int mainSave = canvas.save();
        canvas.clipRect(
                x,
                0f,
                x + mainWidth * ACTIVE_REVEAL_FRACTION,
                getHeight());
        mainPaint.setColor(palette.active);
        canvas.drawText(main, x, mainBaseline, mainPaint);
        canvas.restoreToCount(mainSave);

        translationPaint.setColor(palette.activeTranslation);
        canvas.drawText(translation, x, translationBaseline, translationPaint);
        // Always render a sample progress segment so this page's progress-opacity slider remains
        // observable. Runtime enable/disable ownership stays on the existing main-page switch.
        float translationWidth = Math.min(
                availableWidth,
                translationPaint.measureText(translation));
        int translationSave = canvas.save();
        canvas.clipRect(
                x,
                0f,
                x + translationWidth * TRANSLATION_REVEAL_FRACTION,
                getHeight());
        translationPaint.setColor(palette.activeTranslationProgress);
        canvas.drawText(translation, x, translationBaseline, translationPaint);
        canvas.restoreToCount(translationSave);
    }

    private void drawInactiveGroup(
            Canvas canvas,
            String main,
            String translation,
            float x,
            float mainBaseline,
            float translationBaseline) {
        mainPaint.setColor(withAlpha(
                palette.inactive,
                LyricVisualAlphaPolicy.steadyInactiveMainAlpha(config)));
        canvas.drawText(main, x, mainBaseline, mainPaint);
        translationPaint.setColor(withAlpha(
                palette.inactiveTranslation,
                LyricVisualAlphaPolicy.steadyInactiveTranslationAlpha(config)));
        canvas.drawText(translation, x, translationBaseline, translationPaint);
    }

    private void applyVerticalFadeMask(Canvas canvas) {
        if (!config.verticalFadeEnabled || config.verticalFadeLengthTenthsDp <= 0) return;
        float height = Math.max(1f, contentBounds.height());
        float fadePx = dp(config.verticalFadeLengthTenthsDp / 10f);
        float fadeFraction = Math.max(0f, Math.min(0.49f, fadePx / height));
        if (fadeFraction <= 0f) return;
        LinearGradient mask = new LinearGradient(
                0f,
                contentBounds.top,
                0f,
                contentBounds.bottom,
                new int[]{Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT},
                new float[]{0f, fadeFraction, 1f - fadeFraction, 1f},
                Shader.TileMode.CLAMP);
        maskPaint.setShader(mask);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(contentBounds, maskPaint);
        maskPaint.setXfermode(null);
        maskPaint.setShader(null);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
