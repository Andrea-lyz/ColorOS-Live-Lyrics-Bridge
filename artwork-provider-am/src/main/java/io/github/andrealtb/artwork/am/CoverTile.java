package io.github.andrealtb.artwork.am;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Rounded album tile: the artwork once loaded, a gradient monogram until then (or when there is
 * none). Optionally marks motion content with a small play badge.
 */
@SuppressLint("ViewConstructor") // Built in code only; never inflated from XML.
final class CoverTile extends View {
    private static final int[][] PALETTES = {
            { 0xFFFF375F, 0xFFBF5AF2 }, { 0xFF5E5CE6, 0xFF64D2FF }, { 0xFFFF9F0A, 0xFFFF375F },
            { 0xFF30D158, 0xFF0A84FF }, { 0xFFBF5AF2, 0xFF5E5CE6 }, { 0xFFFF6482, 0xFFFFB340 } };
    /** Which image an asynchronous load is for; a recycled tile ignores answers for its old key. */
    String loadKey;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final Path play = new Path();
    private final float radius;
    private int[] palette = PALETTES[0];
    private String letter = "";
    private Bitmap bitmap;
    private float imageAlpha = 1f;
    private boolean playBadge;
    private boolean selectedRing;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

    CoverTile(Context context, float radius) {
        super(context);
        this.radius = radius;
        letterPaint.setColor(0xF2FFFFFF);
        letterPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        letterPaint.setTextAlign(Paint.Align.CENTER);
        scrim.setColor(0x73000000);
        glyph.setColor(0xFFFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(context.getColor(R.color.am_accent));
        ring.setStrokeWidth(AmUi.dp(context, 2.5f));
    }

    void setPlaceholder(String seed, String label) {
        palette = PALETTES[Math.floorMod(seed.hashCode(), PALETTES.length)];
        String trimmed = label == null ? "" : label.trim();
        letter = trimmed.isEmpty() ? "" : new String(Character.toChars(Character.toUpperCase(trimmed.codePointAt(0))));
        shade();
        invalidate();
    }

    void setBitmap(Bitmap value, boolean fadeIn) {
        bitmap = value;
        shade();
        if (value != null && fadeIn) {
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f).setDuration(260);
            animator.addUpdateListener(animation -> { imageAlpha = (float) animation.getAnimatedValue(); invalidate(); });
            animator.start();
        } else {
            imageAlpha = 1f;
            invalidate();
        }
    }

    void setPlayBadge(boolean show) { playBadge = show; invalidate(); }

    void setRing(boolean show) { selectedRing = show; invalidate(); }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        shade();
        letterPaint.setTextSize(height * 0.42f);
        float size = Math.min(width, height) * 0.3f, cx = width - size * 0.75f, cy = height - size * 0.75f;
        play.reset();
        play.moveTo(cx - size * 0.16f, cy - size * 0.24f);
        play.lineTo(cx + size * 0.26f, cy);
        play.lineTo(cx - size * 0.16f, cy + size * 0.24f);
        play.close();
    }

    private void shade() {
        int width = getWidth(), height = getHeight();
        if (width == 0 || height == 0) return;
        fill.setShader(new LinearGradient(0, 0, width, height, palette[0], palette[1], Shader.TileMode.CLAMP));
        if (bitmap == null) { image.setShader(null); return; }
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
        matrix.setScale(scale, scale);
        matrix.postTranslate((width - bitmap.getWidth() * scale) / 2f, (height - bitmap.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        image.setShader(shader);
    }

    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        rect.set(0, 0, width, height);
        if (bitmap == null || imageAlpha < 1f) {
            canvas.drawRoundRect(rect, radius, radius, fill);
            if (!letter.isEmpty()) {
                canvas.drawText(letter, width / 2f, height / 2f - (letterPaint.descent() + letterPaint.ascent()) / 2f, letterPaint);
            }
        }
        if (bitmap != null && image.getShader() != null) {
            image.setAlpha(Math.round(255 * imageAlpha));
            canvas.drawRoundRect(rect, radius, radius, image);
        }
        if (playBadge) {
            float size = Math.min(width, height) * 0.3f;
            canvas.drawCircle(width - size * 0.75f, height - size * 0.75f, size / 2f, scrim);
            canvas.drawPath(play, glyph);
        }
        if (selectedRing) {
            float inset = ring.getStrokeWidth() / 2f;
            rect.inset(inset, inset);
            canvas.drawRoundRect(rect, radius - inset, radius - inset, ring);
        }
    }
}
