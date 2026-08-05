package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A compact mockup-style visual preset selector; it changes no configuration by itself. */
final class SettingsPresetCard extends FrameLayout {
    private static final int GOLD = 0xFFF2C14E;
    private static final int GOLD_INK = 0xFF6F4A0D;
    private static final int INK = 0xFF1B222C;
    private static final int INACTIVE = 0x701B222C;

    private final LyricUiPreset preset;
    private final LinearLayout bars;
    private final TextView label;
    private final ImageView tick;

    SettingsPresetCard(Context context, LyricUiPreset preset) {
        super(context);
        this.preset = preset;
        setClickable(true);
        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(6), dp(6), dp(6), dp(6));
        addView(content, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        bars = new LinearLayout(context);
        bars.setOrientation(LinearLayout.VERTICAL);
        bars.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        content.addView(bars, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(30)));
        addBars();

        label = new TextView(context);
        label.setText(context.getString(preset.labelRes));
        label.setTextSize(10.5f);
        label.setTextColor(0xFF5C6774);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(8);
        content.addView(label, labelParams);

        tick = new ImageView(context);
        tick.setImageResource(R.drawable.ic_check);
        tick.setColorFilter(0xFF231603);
        tick.setPadding(dp(4), dp(4), dp(4), dp(4));
        tick.setVisibility(INVISIBLE);
        GradientDrawable tickBackground = new GradientDrawable();
        tickBackground.setShape(GradientDrawable.OVAL);
        tickBackground.setColor(GOLD);
        tick.setBackground(tickBackground);
        FrameLayout.LayoutParams tickParams = new FrameLayout.LayoutParams(
                dp(16),
                dp(16),
                Gravity.TOP | Gravity.END);
        tickParams.topMargin = dp(1);
        tickParams.rightMargin = dp(1);
        addView(tick, tickParams);
        setPresetSelected(false);
    }

    LyricUiPreset preset() {
        return preset;
    }

    void setPresetSelected(boolean selected) {
        setSelected(selected);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(15));
        background.setStroke(dp(selected ? 2 : 1), selected ? GOLD : 0x24344455);
        setBackground(background);
        label.setTextColor(selected ? GOLD_INK : 0xFF5C6774);
        label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tick.setVisibility(selected ? VISIBLE : INVISIBLE);
        setElevation(selected ? dp(4) : dp(1));
    }

    private void addBars() {
        int[] widths = {56, 42, 30};
        for (int index = 0; index < widths.length; index++) {
            View bar = new View(getContext());
            GradientDrawable background = new GradientDrawable();
            background.setColor(index == 0 ? INK : INACTIVE);
            background.setCornerRadius(dp(3));
            bar.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(widths[index]),
                    dp(3));
            params.topMargin = dp(3);
            bars.addView(bar, params);
            if (preset == LyricUiPreset.SOFT) {
                bar.setAlpha(index == 0 ? 0.82f : 0.36f);
            } else if (preset == LyricUiPreset.VIVID) {
                bar.setAlpha(index == 0 ? 1f : 0.44f);
                if (index > 0) bar.setScaleX(0.94f);
            } else if (preset == LyricUiPreset.MINIMAL) {
                bar.setAlpha(index == 0 ? 1f : 0.55f);
            }
            if (index == 0 && preset != LyricUiPreset.MINIMAL) {
                bar.setElevation(dp(preset == LyricUiPreset.VIVID ? 5 : 3));
            }
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
