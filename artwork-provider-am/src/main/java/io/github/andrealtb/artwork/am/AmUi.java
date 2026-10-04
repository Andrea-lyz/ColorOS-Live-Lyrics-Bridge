package io.github.andrealtb.artwork.am;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Shared look of the provider pages: rounded cards, tinted badges, pill buttons. Colours follow day/night resources. */
final class AmUi {
    static final int[] GRADIENT = { 0xFFFF375F, 0xFFBF5AF2, 0xFF5E5CE6 };
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private AmUi() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int color(Context context, int res) { return context.getColor(res); }

    static int alpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    static Drawable ripple(Context context, Drawable content, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(color(context, R.color.am_ripple)), content,
                round(0xFF000000, radius));
    }

    static Drawable cardBackground(Context context) {
        GradientDrawable card = round(color(context, R.color.am_card), dp(context, 22));
        card.setStroke(Math.max(1, dp(context, 0.6f)), color(context, R.color.am_card_stroke));
        return card;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground(context));
        card.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        return card;
    }

    static TextView text(Context context, CharSequence value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    static TextView single(TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    /** Gradient pill, the one strong action of a section. */
    static TextView primaryButton(Context context, CharSequence label) {
        TextView button = text(context, label, 15, 0xFFFFFFFF, true);
        button.setGravity(Gravity.CENTER);
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, GRADIENT);
        fill.setCornerRadius(dp(context, 24));
        button.setBackground(ripple(context, fill, dp(context, 24)));
        button.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 12));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    static TextView tonalButton(Context context, CharSequence label, int color) {
        TextView button = text(context, label, 13.5f, color, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ripple(context, round(alpha(color, 0x24), dp(context, 18)), dp(context, 18)));
        button.setPadding(dp(context, 14), dp(context, 7), dp(context, 14), dp(context, 7));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    static TextView chip(Context context, CharSequence label, int color) {
        TextView chip = text(context, label, 11.5f, color, true);
        chip.setBackground(round(alpha(color, 0x22), dp(context, 10)));
        chip.setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 3));
        chip.setSingleLine(true);
        return chip;
    }

    static View dot(Context context, int color, int sizeDp) {
        View dot = new View(context);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(color);
        dot.setBackground(oval);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return dot;
    }

    static ImageView icon(Context context, int res, int color) {
        ImageView icon = new ImageView(context);
        icon.setImageResource(res);
        icon.setImageTintList(ColorStateList.valueOf(color));
        return icon;
    }

    /** Round tinted badge holding an icon; returned so callers can recolour it later. */
    static FrameLayout badge(Context context, int iconRes, int color, int sizeDp) {
        FrameLayout badge = new FrameLayout(context);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(alpha(color, 0x26));
        badge.setBackground(oval);
        int icon = Math.round(sizeDp * 0.56f);
        badge.addView(icon(context, iconRes, color), new FrameLayout.LayoutParams(dp(context, icon), dp(context, icon), Gravity.CENTER));
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return badge;
    }

    static void recolor(FrameLayout badge, int iconRes, int color) {
        ((GradientDrawable) badge.getBackground()).setColor(alpha(color, 0x26));
        ImageView icon = (ImageView) badge.getChildAt(0);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(color));
    }

    static Switch toggle(Context context) {
        Switch toggle = new Switch(context);
        toggle.setShowText(false);
        return toggle;
    }

    /** Card heading: badge, title and an optional trailing view. */
    static LinearLayout heading(Context context, int iconRes, int color, CharSequence title, View trailing) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(badge(context, iconRes, color, 30));
        TextView label = single(text(context, title, 16.5f, color(context, R.color.am_text), true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP, 1f);
        params.setMarginStart(dp(context, 10));
        row.addView(label, params);
        if (trailing != null) row.addView(trailing);
        return row;
    }

    /** A settings row: icon badge, title, summary and a trailing control. */
    static LinearLayout row(Context context, int iconRes, int color, CharSequence title, TextView summary, View trailing) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 12), 0, dp(context, 12));
        row.addView(badge(context, iconRes, color, 34));
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(context, title, 15, color(context, R.color.am_text), true));
        if (summary != null) {
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            summary.setTextColor(color(context, R.color.am_text_secondary));
            texts.addView(summary, marginTop(context, 2));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP, 1f);
        params.setMarginStart(dp(context, 12));
        params.setMarginEnd(dp(context, 10));
        row.addView(texts, params);
        if (trailing != null) row.addView(trailing);
        return row;
    }

    static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(color(context, R.color.am_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(context, 0.7f)));
        params.setMarginStart(dp(context, 46));
        divider.setLayoutParams(params);
        return divider;
    }

    static EditText field(Context context, int hint) {
        EditText field = new EditText(context);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        field.setTextColor(color(context, R.color.am_text));
        field.setHintTextColor(color(context, R.color.am_text_tertiary));
        float radius = dp(context, 14);
        GradientDrawable idle = round(color(context, R.color.am_field), radius);
        GradientDrawable focused = round(color(context, R.color.am_field), radius);
        focused.setStroke(dp(context, 1.5f), color(context, R.color.am_accent));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] { android.R.attr.state_focused }, focused);
        background.addState(new int[0], idle);
        field.setBackground(background);
        field.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        return field;
    }

    static LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(MATCH, WRAP); }

    static LinearLayout.LayoutParams marginTop(Context context, int dp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(context, dp);
        return params;
    }
}
