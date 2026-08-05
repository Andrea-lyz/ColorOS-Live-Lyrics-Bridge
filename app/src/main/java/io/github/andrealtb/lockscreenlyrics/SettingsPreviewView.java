package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Static miniature lyric renderer used only inside the settings preview card.
 *
 * <p>The parent card remains the existing floating-preview container. This view owns only the
 * content pixels, so changing the preview design cannot change sticky geometry or scroll-layer
 * behavior.</p>
 */
final class SettingsPreviewView extends View {
    private static final int MAX_VISIBLE_GROUPS = 3;
    private static final float GROUP_MIN_HEIGHT_DP = 52f;
    private static final float GROUP_VERTICAL_PADDING_DP = 8f;
    private static final float MAIN_TRANSLATION_GAP_DP = 2f;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LyricUiConfig config = LyricUiConfig.defaults();
    private GroupLayout[] groups = new GroupLayout[MAX_VISIBLE_GROUPS];
    private float density;
    private float scaledDensity;
    private int contentWidth;
    private int contentHeight;

    SettingsPreviewView(Context context) {
        this(context, null);
    }

    SettingsPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setCornerRadius(dp(17f));
        setBackground(outline);
        setClipToOutline(true);
        // Do not force a layer type here. The existing parent preview layer is temporarily
        // cached as hardware during scroll; this content view must remain neutral to that policy.
        setContentDescription(getContext().getString(R.string.cd_lyric_preview));
    }

    void bind(LyricUiConfig nextConfig) {
        if (nextConfig == null) {
            nextConfig = LyricUiConfig.defaults();
        }
        LyricUiConfig safe = safeConfig(nextConfig);
        config = safe;
        // GroupLayout caches paints/colors/scale built from the config; presets differ mostly
        // in color/glow/blur fields, so the layouts must be rebuilt on every bind or the
        // preview keeps drawing the previous colors.
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = dp(300f);
        }
        contentWidth = Math.max(dp(1f), width - getPaddingLeft() - getPaddingRight());
        rebuildLayouts(contentWidth);
        int desiredHeight = contentHeight + getPaddingTop() + getPaddingBottom();
        int resolvedHeight = resolveMeasuredHeight(heightMeasureSpec, desiredHeight);
        setMeasuredDimension(width, resolvedHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        backgroundPaint.setShader(new RadialGradient(
                width * 0.52f,
                height * 0.46f,
                Math.max(width, height) * 0.74f,
                new int[]{0xFF3D4650, 0xFF1B2129, 0xFF0A0D12},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height, backgroundPaint);
        backgroundPaint.setShader(null);

        if (groups[0] == null || contentWidth != width - getPaddingLeft() - getPaddingRight()) {
            contentWidth = Math.max(dp(1f), width - getPaddingLeft() - getPaddingRight());
            rebuildLayouts(contentWidth);
        }
        float y = getPaddingTop();
        float lineGap = dp(configuredLineSpacingDp());
        for (int index = 0; index < MAX_VISIBLE_GROUPS; index++) {
            GroupLayout group = groups[index];
            if (group == null) continue;
            drawGroup(canvas, group, getPaddingLeft(), y);
            y += group.height;
            if (index < MAX_VISIBLE_GROUPS - 1) y += lineGap;
        }
    }

    private void rebuildLayouts(int width) {
        float mainSizePx = config.mainFontTenthsSp / 10f * scaledDensity;
        float translationSizePx = mainSizePx * config.translationFontRatioPercent / 100f;
        Typeface typeface = resolveTypeface(config.fontWeight);
        Layout.Alignment alignment = resolveAlignment(config.alignment);
        float wrappedGap = dp(1f + LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(config) / 10f);
        String[] mainTexts = getResources().getStringArray(R.array.preview_main_lines);
        String[] translations = getResources().getStringArray(R.array.preview_translation_lines);
        groups = new GroupLayout[MAX_VISIBLE_GROUPS];
        contentHeight = 0;
        for (int index = 0; index < MAX_VISIBLE_GROUPS; index++) {
            boolean active = index == 0;
            int mainColor = active
                    ? LyricUiColors.active(config)
                    : LyricUiColors.inactive(config);
            int translationColor = active
                    ? LyricUiColors.translationBase(config, false, 1f)
                    : LyricUiColors.translationBase(config, false, 0f);
            TextPaint mainPaint = textPaint(
                    mainSizePx,
                    typeface,
                    mainColor,
                    !active && config.blurEnabled,
                    !active && config.blurEnabled ? config.blurRadiusTenthsPx / 10f : 0f);
            if (active && config.glowEnabled && config.glowIntensityPercent > 0) {
                mainPaint.setShadowLayer(
                        Math.max(1f, mainSizePx * config.glowRadiusPercent / 100f),
                        0f,
                        0f,
                        LyricUiColors.glowShadow(config));
            }
            TextPaint translationPaint = textPaint(
                    translationSizePx,
                    Typeface.DEFAULT,
                    translationColor,
                    !active && config.blurEnabled,
                    !active && config.blurEnabled ? config.blurRadiusTenthsPx / 10f : 0f);
            StaticLayout main = layout(mainTexts[index], mainPaint, width, alignment, wrappedGap);
            StaticLayout translation = layout(
                    translations[index],
                    translationPaint,
                    width,
                    alignment,
                    0f);
            float groupContentHeight = main.getHeight()
                    + dp(MAIN_TRANSLATION_GAP_DP)
                    + translation.getHeight();
            int groupHeight = Math.max(
                    dp(GROUP_MIN_HEIGHT_DP),
                    (int) Math.ceil(groupContentHeight + dp(GROUP_VERTICAL_PADDING_DP)));
            groups[index] = new GroupLayout(
                    main,
                    translation,
                    groupHeight,
                    config.scaleEnabled && !active
                            ? config.inactiveScalePercent / 100f
                            : 1f,
                    active);
            contentHeight += groupHeight;
            if (index < MAX_VISIBLE_GROUPS - 1) {
                contentHeight += dp(configuredLineSpacingDp());
            }
        }
    }

    private void drawGroup(Canvas canvas, GroupLayout group, int left, float top) {
        float scale = group.scale;
        float pivotX = scalePivot(config.alignment, left, contentWidth);
        float pivotY = top + group.height / 2f;
        canvas.save();
        canvas.scale(scale, scale, pivotX, pivotY);
        float y = top + dp(GROUP_VERTICAL_PADDING_DP / 2f);
        canvas.save();
        canvas.translate(left, y);
        group.main.draw(canvas);
        canvas.restore();
        y += group.main.getHeight() + dp(MAIN_TRANSLATION_GAP_DP);
        canvas.save();
        canvas.translate(left, y);
        group.translation.draw(canvas);
        canvas.restore();
        canvas.restore();
    }

    private TextPaint textPaint(
            float textSizePx,
            Typeface typeface,
            int color,
            boolean blur,
            float blurRadiusPx) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(Math.max(1f, textSizePx));
        paint.setTypeface(typeface);
        paint.setColor(color);
        if (blur && blurRadiusPx > 0f) {
            paint.setMaskFilter(new BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL));
        } else {
            paint.setMaskFilter(null);
        }
        return paint;
    }

    private StaticLayout layout(
            String text,
            TextPaint paint,
            int width,
            Layout.Alignment alignment,
            float lineSpacingAdd) {
        return StaticLayout.Builder.obtain(
                        text,
                        0,
                        text.length(),
                        paint,
                        Math.max(dp(1f), width))
                .setAlignment(alignment)
                .setIncludePad(false)
                .setLineSpacing(Math.max(0f, lineSpacingAdd), 1f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .build();
    }

    private Typeface resolveTypeface(int weight) {
        if (weight == LyricUiConfig.WEIGHT_BOLD) return Typeface.DEFAULT_BOLD;
        if (weight == LyricUiConfig.WEIGHT_MEDIUM && android.os.Build.VERSION.SDK_INT >= 28) {
            return Typeface.create(Typeface.DEFAULT, 500, false);
        }
        return Typeface.DEFAULT;
    }

    private Layout.Alignment resolveAlignment(int alignment) {
        if (alignment == LyricUiConfig.ALIGN_CENTER) return Layout.Alignment.ALIGN_CENTER;
        if (alignment == LyricUiConfig.ALIGN_END) return Layout.Alignment.ALIGN_OPPOSITE;
        return Layout.Alignment.ALIGN_NORMAL;
    }

    private float scalePivot(int alignment, float left, float width) {
        if (alignment == LyricUiConfig.ALIGN_CENTER) return left + width / 2f;
        if (alignment == LyricUiConfig.ALIGN_END) return left + width;
        return left;
    }

    private float configuredLineSpacingDp() {
        return LyricUiLayoutPolicy.lineSpacingTenthsDp(config) / 10f;
    }

    private LyricUiConfig safeConfig(LyricUiConfig source) {
        String primary = LyricUiConfig.sanitizeColor(source.primaryColor, "#FFFFFF");
        String glow = LyricUiConfig.sanitizeColor(source.glowColor, "#FFD68A");
        if (primary.equals(source.primaryColor) && glow.equals(source.glowColor)) {
            return source;
        }
        return source.buildUpon().primaryColor(primary).glowColor(glow).build();
    }

    private int resolveMeasuredHeight(int heightMeasureSpec, int desiredHeight) {
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        if (mode == MeasureSpec.EXACTLY) return size;
        if (mode == MeasureSpec.AT_MOST) return Math.min(size, desiredHeight);
        return desiredHeight;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private static final class GroupLayout {
        final StaticLayout main;
        final StaticLayout translation;
        final int height;
        final float scale;
        final boolean active;

        GroupLayout(
                StaticLayout main,
                StaticLayout translation,
                int height,
                float scale,
                boolean active) {
            this.main = main;
            this.translation = translation;
            this.height = height;
            this.scale = scale;
            this.active = active;
        }
    }
}
