package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.Locale;

/** Compact mockup cpick panel anchored to the custom swatch. */
final class SettingsColorDialog {
    interface OnColorAccepted {
        void accept(String color);
    }

    private SettingsColorDialog() {
    }

    static void show(
            Context context,
            View anchor,
            String title,
            String currentColor,
            String fallbackColor,
            OnColorAccepted accepted) {
        String initial = LyricUiConfig.sanitizeColor(currentColor, fallbackColor);
        int initialArgb = Color.parseColor(initial);
        int panelWidth = dp(context, 196);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 13), dp(context, 13), dp(context, 13), dp(context, 11));
        GradientDrawable panelBackground = rounded(
                Color.WHITE,
                dp(context, 16),
                dp(context, 1),
                0x1A1B222C);
        panel.setBackground(panelBackground);

        TextView titleView = text(context, title, 11.5f, 0xFF1B222C, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.bottomMargin = dp(context, 9);
        panel.addView(titleView, titleParams);

        LinearLayout body = new LinearLayout(context);
        body.setGravity(Gravity.CENTER_VERTICAL);
        TextView hash = text(context, "#", 13f, 0xFF8A919C, Typeface.BOLD);
        hash.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        body.addView(hash, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText hexInput = new EditText(context);
        hexInput.setSingleLine(true);
        hexInput.setTextSize(13f);
        hexInput.setTextColor(0xFF1B222C);
        hexInput.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexInput.setKeyListener(DigitsKeyListener.getInstance("0123456789ABCDEFabcdef"));
        hexInput.setFilters(new InputFilter[]{
                new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(6)
        });
        hexInput.setHint(context.getString(R.string.dialog_hex_hint));
        hexInput.setHintTextColor(0xFF9AA0A6);
        hexInput.setText(String.format(Locale.ROOT, "%06X", initialArgb & 0xFFFFFF));
        hexInput.setSelectAllOnFocus(true);
        hexInput.setPadding(dp(context, 9), 0, dp(context, 9), 0);
        LinearLayout.LayoutParams hexParams = new LinearLayout.LayoutParams(
                0,
                dp(context, 34),
                1f);
        hexParams.leftMargin = dp(context, 8);
        body.addView(hexInput, hexParams);

        TextView preview = new TextView(context);
        GradientDrawable previewBackground = rounded(
                initialArgb,
                dp(context, 9),
                dp(context, 1.5f),
                0x40344455);
        preview.setBackground(previewBackground);
        preview.setContentDescription(context.getString(R.string.cd_color_picker));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                dp(context, 30),
                dp(context, 30));
        previewParams.leftMargin = dp(context, 8);
        body.addView(preview, previewParams);
        panel.addView(body, matchWrap());

        float[] initialHsv = new float[3];
        Color.colorToHSV(initialArgb, initialHsv);
        SaturationValueView svPanel = new SaturationValueView(context);
        HueBarView hueBar = new HueBarView(context);
        svPanel.setHue(initialHsv[0]);
        svPanel.setSaturation(initialHsv[1]);
        svPanel.setValue(initialHsv[2]);
        hueBar.setHue(initialHsv[0]);

        LinearLayout hsvPanel = new LinearLayout(context);
        hsvPanel.setOrientation(LinearLayout.VERTICAL);
        hsvPanel.setPadding(0, dp(context, 9), 0, 0);
        hsvPanel.addView(svPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 96)));
        LinearLayout.LayoutParams hueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 16));
        hueParams.topMargin = dp(context, 8);
        hsvPanel.addView(hueBar, hueParams);
        hsvPanel.setVisibility(View.GONE);
        panel.addView(hsvPanel, matchWrap());

        TextView error = text(
                context,
                context.getString(R.string.color_error_format),
                9.5f,
                0xFFE5484D,
                Typeface.NORMAL);
        LinearLayout.LayoutParams errorParams = matchWrap();
        errorParams.leftMargin = dp(context, 2);
        errorParams.topMargin = dp(context, 7);
        panel.addView(error, errorParams);
        error.setVisibility(View.GONE);

        final boolean[] invalid = {false};
        final boolean[] syncing = {false};
        Runnable updateFieldBackground = () -> hexInput.setBackground(rounded(
                Color.WHITE,
                dp(context, 10),
                dp(context, 1.5f),
                invalid[0]
                        ? 0xFFE5484D
                        : hexInput.hasFocus() ? 0xFFF2C14E : 0x40344455));
        hexInput.setOnFocusChangeListener((view, hasFocus) -> updateFieldBackground.run());
        updateFieldBackground.run();
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String value = editable.toString().trim();
                invalid[0] = value.length() >= 6 && !value.matches("[0-9A-Fa-f]{6}");
                error.setVisibility(invalid[0] ? View.VISIBLE : View.GONE);
                if (value.matches("[0-9A-Fa-f]{6}")) {
                    int color = Color.parseColor("#" + value);
                    previewBackground.setColor(color);
                    if (!syncing[0]) {
                        float[] hsv = new float[3];
                        Color.colorToHSV(color, hsv);
                        hueBar.setHue(hsv[0]);
                        svPanel.setHue(hsv[0]);
                        svPanel.setSaturation(hsv[1]);
                        svPanel.setValue(hsv[2]);
                    }
                }
                updateFieldBackground.run();
            }
        });

        Runnable updateFromHsv = () -> {
            if (syncing[0]) return;
            syncing[0] = true;
            int color = Color.HSVToColor(255, new float[]{
                    hueBar.getHue(),
                    svPanel.getSaturation(),
                    svPanel.getValue()});
            String value = String.format(Locale.ROOT, "%06X", color & 0xFFFFFF);
            hexInput.setText(value);
            hexInput.setSelection(value.length());
            previewBackground.setColor(color);
            invalid[0] = false;
            error.setVisibility(View.GONE);
            updateFieldBackground.run();
            syncing[0] = false;
        };
        svPanel.setOnColorChanged((saturation, value) -> updateFromHsv.run());
        hueBar.setOnHueChanged(hue -> {
            svPanel.setHue(hue);
            updateFromHsv.run();
        });

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(context, 11);
        panel.addView(actions, actionsParams);

        TextView cancel = action(
                context,
                context.getString(R.string.dialog_cancel),
                0x121B222C,
                0xFF5C6774);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0,
                dp(context, 34),
                1f);
        cancelParams.rightMargin = dp(context, 8);
        actions.addView(cancel, cancelParams);

        TextView apply = action(
                context,
                context.getString(R.string.dialog_apply),
                0xFFF2C14E,
                0xFF6F4A0D);
        actions.addView(apply, new LinearLayout.LayoutParams(
                0,
                dp(context, 34),
                1f));

        PopupWindow popup = new PopupWindow(
                panel,
                panelWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(context, 10));
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        preview.setOnClickListener(view -> {
            boolean showHsv = hsvPanel.getVisibility() != View.VISIBLE;
            hsvPanel.setVisibility(showHsv ? View.VISIBLE : View.GONE);
            previewBackground.setStroke(
                    dp(context, showHsv ? 2f : 1.5f),
                    showHsv ? 0xFFF2C14E : 0x40344455);
            panel.requestLayout();
            panel.post(popup::update);
        });

        cancel.setOnClickListener(view -> popup.dismiss());
        apply.setOnClickListener(view -> {
            String raw = hexInput.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!raw.matches("[0-9A-F]{6}")) {
                invalid[0] = true;
                error.setVisibility(View.VISIBLE);
                updateFieldBackground.run();
                return;
            }
            accepted.accept(LyricUiConfig.sanitizeColor("#" + raw, fallbackColor));
            popup.dismiss();
        });

        panel.setAlpha(0f);
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.setTranslationY(-dp(context, 6));
        panel.setPivotX(panelWidth);
        panel.setPivotY(0f);
        popup.showAsDropDown(anchor, anchor.getWidth() - panelWidth, dp(context, 8));
        panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180L)
                .start();
    }

    private static TextView action(
            Context context,
            String label,
            int backgroundColor,
            int textColor) {
        TextView view = text(context, label, 11.5f, textColor, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(rounded(backgroundColor, dp(context, 99), 0, Color.TRANSPARENT));
        return view;
    }

    private static TextView text(
            Context context,
            String value,
            float size,
            int color,
            int typefaceStyle) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, typefaceStyle);
        view.setIncludeFontPadding(false);
        return view;
    }

    private static GradientDrawable rounded(
            int color,
            float radius,
            int strokeWidth,
            int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        if (strokeWidth > 0) background.setStroke(strokeWidth, strokeColor);
        return background;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Compact saturation/value square shown on demand from the preview swatch. */
    private static final class SaturationValueView extends View {
        interface OnColorChangedListener {
            void onColorChanged(float saturation, float value);
        }

        private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue;
        private float saturation = 1f;
        private float value = 1f;
        private OnColorChangedListener listener;

        SaturationValueView(Context context) {
            super(context);
            cursorFill.setColor(Color.WHITE);
            cursorRing.setStyle(Paint.Style.STROKE);
            cursorRing.setStrokeWidth(dp(context, 1.5f));
            cursorRing.setColor(0xFF1B222C);
            GradientDrawable background = rounded(Color.TRANSPARENT, dp(context, 9), 0, 0);
            setBackground(background);
            setClipToOutline(true);
        }

        void setHue(float hue) {
            this.hue = hue;
            updateHueShader();
            invalidate();
        }

        void setSaturation(float saturation) {
            this.saturation = Math.max(0f, Math.min(1f, saturation));
            invalidate();
        }

        void setValue(float value) {
            this.value = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        float getSaturation() {
            return saturation;
        }

        float getValue() {
            return value;
        }

        void setOnColorChanged(OnColorChangedListener listener) {
            this.listener = listener;
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            updateHueShader();
            valuePaint.setShader(new LinearGradient(
                    0f,
                    0f,
                    0f,
                    Math.max(1, height),
                    0x00000000,
                    0xFF000000,
                    Shader.TileMode.CLAMP));
        }

        private void updateHueShader() {
            int color = Color.HSVToColor(255, new float[]{hue, 1f, 1f});
            huePaint.setShader(new LinearGradient(
                    0f,
                    0f,
                    Math.max(1, getWidth()),
                    0f,
                    Color.WHITE,
                    color,
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), huePaint);
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), valuePaint);
            float x = saturation * Math.max(1, getWidth() - 1);
            float y = (1f - value) * Math.max(1, getHeight() - 1);
            canvas.drawCircle(x, y, dp(getContext(), 4.5f), cursorRing);
            canvas.drawCircle(x, y, dp(getContext(), 3f), cursorFill);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                    && event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                return super.onTouchEvent(event);
            }
            saturation = Math.max(0f, Math.min(1f, event.getX() / Math.max(1f, getWidth())));
            value = 1f - Math.max(0f, Math.min(1f, event.getY() / Math.max(1f, getHeight())));
            invalidate();
            if (listener != null) listener.onColorChanged(saturation, value);
            return true;
        }
    }

    /** Compact hue strip paired with {@link SaturationValueView}. */
    private static final class HueBarView extends View {
        interface OnHueChangedListener {
            void onHueChanged(float hue);
        }

        private static final int[] COLORS = {
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                Color.BLUE, Color.MAGENTA, Color.RED
        };
        private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue;
        private OnHueChangedListener listener;

        HueBarView(Context context) {
            super(context);
            cursorFill.setColor(Color.WHITE);
            cursorRing.setStyle(Paint.Style.STROKE);
            cursorRing.setStrokeWidth(dp(context, 1.5f));
            cursorRing.setColor(0xFF1B222C);
        }

        void setHue(float hue) {
            this.hue = Math.max(0f, Math.min(360f, hue));
            invalidate();
        }

        float getHue() {
            return hue;
        }

        void setOnHueChanged(OnHueChangedListener listener) {
            this.listener = listener;
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            huePaint.setShader(new LinearGradient(
                    0f,
                    0f,
                    Math.max(1, width),
                    0f,
                    COLORS,
                    null,
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = getHeight() / 2f;
            canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(), radius, radius, huePaint);
            float x = hue / 360f * Math.max(1, getWidth() - 1);
            canvas.drawCircle(x, radius, radius * 0.66f, cursorRing);
            canvas.drawCircle(x, radius, radius * 0.48f, cursorFill);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                    && event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                return super.onTouchEvent(event);
            }
            hue = Math.max(0f, Math.min(1f, event.getX() / Math.max(1f, getWidth()))) * 360f;
            invalidate();
            if (listener != null) listener.onHueChanged(hue);
            return true;
        }
    }
}
