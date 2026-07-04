package io.github.andrealtb.lockscreenlyrics;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

public final class LyricUiSettingsActivity extends Activity {
    private static final int COLOR_BACKGROUND = 0xFFF6F6F8;
    private static final int COLOR_CARD = 0xFFFFFFFF;
    private static final int COLOR_TEXT_PRIMARY = 0xFF111111;
    private static final int COLOR_TEXT_SECONDARY = 0x99000000;
    private static final int COLOR_TEXT_TERTIARY = 0x66000000;
    private static final int COLOR_DIVIDER = 0x14000000;
    private static final int COLOR_NOTICE_BACKGROUND = 0xFFFFF4DE;
    private static final int COLOR_NOTICE_TITLE = 0xFF7A4C00;
    private static final int COLOR_NOTICE_SUMMARY = 0xB27A4C00;

    private SharedPreferences preferences;
    private Switch defaultSwitch;
    private Switch scrollScaleSwitch;
    private Switch inactiveBlurSwitch;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        preferences = getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE);
        setContentView(createContentView());
        bindSwitchesFromPreferences();
        broadcastCurrentSettings();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private View createContentView() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(34), dp(24), dp(28));
        content.setBackgroundColor(COLOR_BACKGROUND);

        TextView title = new TextView(this);
        title.setText(getString(R.string.lyric_ui_settings_title));
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(30f);
        title.setGravity(Gravity.START);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        content.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.lyric_ui_settings_subtitle));
        subtitle.setTextColor(COLOR_TEXT_SECONDARY);
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, dp(10), 0, dp(20));
        content.addView(subtitle, matchWrap());

        content.addView(createNoticeCard(), matchWrapWithBottomMargin(dp(18)));

        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(getString(R.string.lyric_ui_effects_section));
        sectionTitle.setTextColor(COLOR_TEXT_TERTIARY);
        sectionTitle.setTextSize(13f);
        sectionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sectionTitle.setPadding(dp(4), 0, 0, dp(8));
        content.addView(sectionTitle, matchWrap());

        LinearLayout card = createCard();
        defaultSwitch = addSwitchPreference(
                card,
                getString(R.string.lyric_ui_default_style),
                getString(R.string.lyric_ui_default_style_summary),
                true);
        scrollScaleSwitch = addSwitchPreference(
                card,
                getString(R.string.lyric_ui_scroll_scale),
                getString(R.string.lyric_ui_scroll_scale_summary),
                true);
        inactiveBlurSwitch = addSwitchPreference(
                card,
                getString(R.string.lyric_ui_inactive_blur),
                getString(R.string.lyric_ui_inactive_blur_summary),
                false);
        content.addView(card, matchWrap());

        CompoundButton.OnCheckedChangeListener listener = this::onSwitchChanged;
        defaultSwitch.setOnCheckedChangeListener(listener);
        scrollScaleSwitch.setOnCheckedChangeListener(listener);
        inactiveBlurSwitch.setOnCheckedChangeListener(listener);

        Space bottomSpace = new Space(this);
        content.addView(bottomSpace, new LinearLayout.LayoutParams(1, dp(28)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);
        scrollView.addView(content);
        return scrollView;
    }

    private LinearLayout createNoticeCard() {
        LinearLayout card = createCard();
        card.setBackground(roundedDrawable(COLOR_NOTICE_BACKGROUND, 18));
        card.setPadding(dp(18), dp(15), dp(18), dp(15));

        TextView title = new TextView(this);
        title.setText(getString(R.string.lyric_ui_restart_notice_title));
        title.setTextColor(COLOR_NOTICE_TITLE);
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        card.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText(getString(R.string.lyric_ui_restart_notice_summary));
        summary.setTextColor(COLOR_NOTICE_SUMMARY);
        summary.setTextSize(13f);
        summary.setLineSpacing(dp(2), 1f);
        summary.setPadding(0, dp(7), 0, 0);
        card.addView(summary, matchWrap());
        return card;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedDrawable(COLOR_CARD, 22));
        card.setClipToOutline(false);
        return card;
    }

    private Switch addSwitchPreference(
            LinearLayout card,
            String title,
            String summary,
            boolean withDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(78));
        row.setPadding(dp(18), dp(12), dp(14), dp(12));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTextSize(16f);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setIncludeFontPadding(false);
        labels.addView(titleView, matchWrap());

        TextView summaryView = new TextView(this);
        summaryView.setText(summary);
        summaryView.setTextColor(COLOR_TEXT_SECONDARY);
        summaryView.setTextSize(13f);
        summaryView.setLineSpacing(dp(2), 1f);
        summaryView.setPadding(0, dp(7), dp(16), 0);
        labels.addView(summaryView, matchWrap());

        Switch switchView = new Switch(this);
        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        row.addView(switchView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked()));

        card.addView(row, matchWrap());
        if (withDivider) {
            View divider = new View(this);
            divider.setBackgroundColor(COLOR_DIVIDER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.max(1, dp(0.5f)));
            params.leftMargin = dp(18);
            card.addView(divider, params);
        }
        return switchView;
    }

    private void onSwitchChanged(CompoundButton button, boolean checked) {
        if (binding) {
            return;
        }
        boolean scrollScale = scrollScaleSwitch.isChecked();
        boolean inactiveBlur = inactiveBlurSwitch.isChecked();
        if (button == defaultSwitch && checked) {
            scrollScale = false;
            inactiveBlur = false;
        }
        if (button == scrollScaleSwitch) {
            scrollScale = checked;
        } else if (button == inactiveBlurSwitch) {
            inactiveBlur = checked;
        }
        saveAndBroadcast(scrollScale, inactiveBlur);
    }

    private void bindSwitchesFromPreferences() {
        boolean scrollScale = preferences.getBoolean(
                LyricUiSettings.KEY_SCROLL_SCALE_ENABLED,
                false);
        boolean inactiveBlur = preferences.getBoolean(
                LyricUiSettings.KEY_INACTIVE_BLUR_ENABLED,
                false);
        bindSwitches(scrollScale, inactiveBlur);
    }

    private void saveAndBroadcast(boolean scrollScale, boolean inactiveBlur) {
        preferences.edit()
                .putBoolean(LyricUiSettings.KEY_SCROLL_SCALE_ENABLED, scrollScale)
                .putBoolean(LyricUiSettings.KEY_INACTIVE_BLUR_ENABLED, inactiveBlur)
                .apply();
        bindSwitches(scrollScale, inactiveBlur);
        broadcastSettings(scrollScale, inactiveBlur);
    }

    private void bindSwitches(boolean scrollScale, boolean inactiveBlur) {
        binding = true;
        defaultSwitch.setChecked(!scrollScale && !inactiveBlur);
        scrollScaleSwitch.setChecked(scrollScale);
        inactiveBlurSwitch.setChecked(inactiveBlur);
        binding = false;
    }

    private void broadcastCurrentSettings() {
        boolean scrollScale = preferences.getBoolean(
                LyricUiSettings.KEY_SCROLL_SCALE_ENABLED,
                false);
        boolean inactiveBlur = preferences.getBoolean(
                LyricUiSettings.KEY_INACTIVE_BLUR_ENABLED,
                false);
        broadcastSettings(scrollScale, inactiveBlur);
    }

    private void broadcastSettings(boolean scrollScale, boolean inactiveBlur) {
        Intent intent = new Intent(LyricUiSettings.ACTION_STYLE_CHANGED)
                .setPackage("com.android.systemui")
                .putExtra(LyricUiSettings.EXTRA_SCROLL_SCALE_ENABLED, scrollScale)
                .putExtra(LyricUiSettings.EXTRA_INACTIVE_BLUR_ENABLED, inactiveBlur);
        sendBroadcast(intent);
    }

    private GradientDrawable roundedDrawable(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithBottomMargin(int marginBottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = marginBottom;
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
