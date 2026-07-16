package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * A small settings-only slider that deliberately does not register a system gesture exclusion
 * rectangle. {@code AbsSeekBar} follows its thumb with such a rectangle; moving several native
 * seek bars inside a long {@code ScrollView} makes ColorOS recompute window exclusion and
 * keep-clear rectangles for every frame of a vertical scroll.
 */
final class LightweightSlider extends View {
    interface OnProgressChangedListener {
        void onProgressChanged(LightweightSlider slider, int progress, boolean fromUser);
    }

    private static final int DEFAULT_ACTIVE_COLOR = 0xFF0B63CE;
    private static final int INACTIVE_COLOR = 0x33000000;
    private static final int DISABLED_ACTIVE_COLOR = 0x660B63CE;
    private static final int DISABLED_INACTIVE_COLOR = 0x1F000000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int desiredHeight;
    private final float trackHeight;
    private final float thumbRadius;
    private final float pressedThumbRadius;
    private final int touchSlop;
    private final int activeColor;

    private int min;
    private int max = 100;
    private int progress;
    private float downX;
    private float downY;
    private boolean trackingTouch;
    private OnProgressChangedListener listener;

    LightweightSlider(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        desiredHeight = Math.round(48f * density);
        trackHeight = Math.max(2f, 3f * density);
        thumbRadius = 8f * density;
        pressedThumbRadius = 10f * density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        activeColor = resolveAccentColor(context);
        int horizontalPadding = Math.round(8f * density);
        setPadding(horizontalPadding, 0, horizontalPadding, 0);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    int getProgress() {
        return progress;
    }

    void setProgress(int value) {
        setProgressInternal(value, false);
    }

    void setMin(int value) {
        if (min == value) return;
        min = value;
        if (max < min) max = min;
        setProgressInternal(progress, false);
        invalidate();
    }

    void setMax(int value) {
        int sanitized = Math.max(min, value);
        if (max == sanitized) return;
        max = sanitized;
        setProgressInternal(progress, false);
        invalidate();
    }

    void setOnProgressChangedListener(OnProgressChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = resolveSize(Math.max(desiredHeight, getSuggestedMinimumHeight()),
                heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerY = getHeight() * 0.5f;
        float radius = isPressed() ? pressedThumbRadius : thumbRadius;
        float start = getPaddingLeft() + pressedThumbRadius;
        float end = getWidth() - getPaddingRight() - pressedThumbRadius;
        if (end < start) end = start;
        float fraction = max == min ? 0f : (progress - min) / (float) (max - min);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float thumbX = rtl
                ? end - (end - start) * fraction
                : start + (end - start) * fraction;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(trackHeight);
        paint.setColor(isEnabled() ? INACTIVE_COLOR : DISABLED_INACTIVE_COLOR);
        canvas.drawLine(start, centerY, end, centerY, paint);
        paint.setColor(isEnabled() ? activeColor : DISABLED_ACTIVE_COLOR);
        if (rtl) {
            canvas.drawLine(end, centerY, thumbX, centerY, paint);
        } else {
            canvas.drawLine(start, centerY, thumbX, centerY, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(thumbX, centerY, radius, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                trackingTouch = false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (!trackingTouch && dx > touchSlop && dx >= dy) {
                    trackingTouch = true;
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                }
                if (trackingTouch) updateProgressFromX(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
                if (trackingTouch
                        || (Math.abs(event.getX() - downX) <= touchSlop
                        && Math.abs(event.getY() - downY) <= touchSlop)) {
                    updateProgressFromX(event.getX());
                    performClick();
                }
                finishTouch();
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishTouch();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            direction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1 : -1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            direction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -1 : 1;
        } else {
            return super.onKeyDown(keyCode, event);
        }
        setProgressInternal(progress + direction, true);
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                min,
                max,
                progress));
        if (!isEnabled()) return;
        if (progress < max) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        if (progress > min) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName("android.widget.SeekBar");
        event.setItemCount(max - min);
        event.setCurrentItemIndex(progress - min);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) return true;
        if (!isEnabled()) return false;
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            return setProgressInternal(progress + 1, true);
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            return setProgressInternal(progress - 1, true);
        }
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()
                && arguments != null) {
            float requested = arguments.getFloat(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
                    progress);
            return setProgressInternal(Math.round(requested), true);
        }
        return false;
    }

    private void updateProgressFromX(float x) {
        float start = getPaddingLeft() + pressedThumbRadius;
        float end = getWidth() - getPaddingRight() - pressedThumbRadius;
        float fraction = end <= start ? 0f : (x - start) / (end - start);
        fraction = Math.max(0f, Math.min(1f, fraction));
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) fraction = 1f - fraction;
        setProgressInternal(min + Math.round(fraction * (max - min)), true);
    }

    private boolean setProgressInternal(int value, boolean fromUser) {
        int sanitized = Math.max(min, Math.min(max, value));
        if (progress == sanitized) return false;
        progress = sanitized;
        invalidate();
        OnProgressChangedListener currentListener = listener;
        if (currentListener != null) {
            currentListener.onProgressChanged(this, progress, fromUser);
        }
        if (fromUser) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        }
        return true;
    }

    private void finishTouch() {
        trackingTouch = false;
        setPressed(false);
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
    }

    private static int resolveAccentColor(Context context) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true)) {
            return DEFAULT_ACTIVE_COLOR;
        }
        if (value.resourceId != 0) {
            try {
                return context.getColor(value.resourceId);
            } catch (RuntimeException ignored) {
                return DEFAULT_ACTIVE_COLOR;
            }
        }
        return value.data != 0 ? value.data : DEFAULT_ACTIVE_COLOR;
    }
}
