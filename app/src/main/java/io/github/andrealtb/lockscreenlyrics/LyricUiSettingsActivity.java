package io.github.andrealtb.lockscreenlyrics;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
    private static final float CONTENT_TOP_GAP_DP = 16f;

    private SharedPreferences preferences;
    private Switch defaultSwitch;
    private Switch lineTimedProgressSwitch;
    private Switch translationProgressSwitch;
    private Switch scrollScaleSwitch;
    private Switch inactiveBlurSwitch;
    private Switch screenTimeoutSwitch;
    private EditText screenTimeoutSecondsInput;
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        content.setPadding(dp(24), getTopContentPadding(), dp(24), dp(28));
        content.setBackgroundColor(COLOR_BACKGROUND);

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
        lineTimedProgressSwitch = addSwitchPreference(
                card,
                getString(R.string.lyric_ui_line_timed_progress),
                getString(R.string.lyric_ui_line_timed_progress_summary),
                true);
        translationProgressSwitch = addSwitchPreference(
                card,
                getString(R.string.lyric_ui_translation_progress),
                getString(R.string.lyric_ui_translation_progress_summary),
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
        content.addView(card, matchWrapWithBottomMargin(dp(18)));

        TextView screenSectionTitle = new TextView(this);
        screenSectionTitle.setText(getString(R.string.lyric_ui_screen_section));
        screenSectionTitle.setTextColor(COLOR_TEXT_TERTIARY);
        screenSectionTitle.setTextSize(13f);
        screenSectionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        screenSectionTitle.setPadding(dp(4), 0, 0, dp(8));
        content.addView(screenSectionTitle, matchWrap());

        LinearLayout screenCard = createCard();
        screenTimeoutSwitch = addSwitchPreference(
                screenCard,
                getString(R.string.lyric_ui_screen_timeout),
                getString(R.string.lyric_ui_screen_timeout_summary),
                true);
        screenTimeoutSecondsInput = addNumberPreference(
                screenCard,
                getString(R.string.lyric_ui_screen_timeout_seconds),
                getString(R.string.lyric_ui_screen_timeout_seconds_summary),
                getString(R.string.lyric_ui_screen_timeout_seconds_hint),
                getString(R.string.lyric_ui_seconds_unit));
        content.addView(screenCard, matchWrapWithBottomMargin(dp(18)));

        Button saveButton = new Button(this);
        saveButton.setText(getString(R.string.lyric_ui_save));
        saveButton.setAllCaps(false);
        saveButton.setTextSize(16f);
        saveButton.setTextColor(Color.WHITE);
        saveButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        saveButton.setBackground(roundedDrawable(COLOR_TEXT_PRIMARY, 18));
        saveButton.setMinHeight(dp(52));
        saveButton.setOnClickListener(v -> saveCurrentSettings());
        content.addView(saveButton, matchWrap());

        CompoundButton.OnCheckedChangeListener listener = this::onSwitchChanged;
        defaultSwitch.setOnCheckedChangeListener(listener);
        lineTimedProgressSwitch.setOnCheckedChangeListener(listener);
        translationProgressSwitch.setOnCheckedChangeListener(listener);
        scrollScaleSwitch.setOnCheckedChangeListener(listener);
        inactiveBlurSwitch.setOnCheckedChangeListener(listener);
        screenTimeoutSwitch.setOnCheckedChangeListener(listener);

        Space bottomSpace = new Space(this);
        content.addView(bottomSpace, new LinearLayout.LayoutParams(1, dp(28)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);
        scrollView.addView(content);
        installKeyboardAvoidance(scrollView);
        screenTimeoutSecondsInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollView.postDelayed(() -> scrollFocusedInputIntoView(scrollView), 120L);
                scrollView.postDelayed(() -> scrollFocusedInputIntoView(scrollView), 320L);
            }
        });
        return scrollView;
    }

    private void installKeyboardAvoidance(ScrollView scrollView) {
        View root = getWindow().getDecorView();
        Rect visibleFrame = new Rect();
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        root.getWindowVisibleDisplayFrame(visibleFrame);
                        int rootHeight = root.getRootView().getHeight();
                        int keyboardHeight = Math.max(0, rootHeight - visibleFrame.bottom);
                        boolean keyboardVisible = keyboardHeight > dp(120);
                        int bottomPadding = keyboardVisible ? keyboardHeight + dp(24) : 0;
                        if (scrollView.getPaddingBottom() != bottomPadding) {
                            scrollView.setPadding(
                                    scrollView.getPaddingLeft(),
                                    scrollView.getPaddingTop(),
                                    scrollView.getPaddingRight(),
                                    bottomPadding);
                        }
                        if (keyboardVisible && screenTimeoutSecondsInput.hasFocus()) {
                            scrollView.postDelayed(
                                    () -> scrollFocusedInputIntoView(scrollView),
                                    80L);
                        }
                    }
                });
    }

    private void scrollFocusedInputIntoView(ScrollView scrollView) {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        int[] focusedLocation = new int[2];
        int[] scrollLocation = new int[2];
        focused.getLocationOnScreen(focusedLocation);
        scrollView.getLocationOnScreen(scrollLocation);
        int focusedTop = focusedLocation[1] - scrollLocation[1];
        int focusedBottom = focusedTop + focused.getHeight();
        int visibleBottom = scrollView.getHeight() - scrollView.getPaddingBottom() - dp(24);
        int visibleTop = dp(24);
        if (focusedBottom > visibleBottom) {
            scrollView.smoothScrollBy(0, focusedBottom - visibleBottom);
        } else if (focusedTop < visibleTop) {
            scrollView.smoothScrollBy(0, focusedTop - visibleTop);
        }
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

    private EditText addNumberPreference(
            LinearLayout card,
            String title,
            String summary,
            String hint,
            String unit) {
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

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(hint);
        input.setTextColor(COLOR_TEXT_PRIMARY);
        input.setHintTextColor(COLOR_TEXT_TERTIARY);
        input.setTextSize(16f);
        input.setGravity(Gravity.CENTER);
        input.setSelectAllOnFocus(true);

        TextView unitView = new TextView(this);
        unitView.setText(unit);
        unitView.setTextColor(COLOR_TEXT_PRIMARY);
        unitView.setTextSize(15f);
        unitView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        unitView.setPadding(dp(8), 0, 0, 0);

        LinearLayout inputGroup = new LinearLayout(this);
        inputGroup.setOrientation(LinearLayout.HORIZONTAL);
        inputGroup.setGravity(Gravity.CENTER_VERTICAL);
        inputGroup.addView(input, new LinearLayout.LayoutParams(
                dp(82),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        inputGroup.addView(unitView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        row.addView(inputGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row, matchWrap());
        return input;
    }

    private void onSwitchChanged(CompoundButton button, boolean checked) {
        if (binding) {
            return;
        }
        boolean scrollScale = scrollScaleSwitch.isChecked();
        boolean inactiveBlur = inactiveBlurSwitch.isChecked();
        boolean lineTimedProgress = lineTimedProgressSwitch.isChecked();
        boolean translationProgress = translationProgressSwitch.isChecked();
        boolean screenTimeoutEnabled = screenTimeoutSwitch.isChecked();
        int screenTimeoutSeconds = readScreenTimeoutSecondsFromInput();
        if (button == defaultSwitch && checked) {
            scrollScale = false;
            inactiveBlur = false;
            lineTimedProgress = LyricUiSettings.DEFAULT_LINE_TIMED_PROGRESS_ENABLED;
            translationProgress = LyricUiSettings.DEFAULT_TRANSLATION_PROGRESS_ENABLED;
        }
        if (button == scrollScaleSwitch) {
            scrollScale = checked;
        } else if (button == inactiveBlurSwitch) {
            inactiveBlur = checked;
        } else if (button == lineTimedProgressSwitch) {
            lineTimedProgress = checked;
        } else if (button == translationProgressSwitch) {
            translationProgress = checked;
        } else if (button == screenTimeoutSwitch) {
            screenTimeoutEnabled = checked;
        }
        saveAndBroadcast(
                scrollScale,
                inactiveBlur,
                lineTimedProgress,
                translationProgress,
                screenTimeoutEnabled,
                screenTimeoutSeconds);
    }

    private void saveCurrentSettings() {
        saveAndBroadcast(
                scrollScaleSwitch.isChecked(),
                inactiveBlurSwitch.isChecked(),
                lineTimedProgressSwitch.isChecked(),
                translationProgressSwitch.isChecked(),
                screenTimeoutSwitch.isChecked(),
                readScreenTimeoutSecondsFromInput());
        Toast.makeText(
                this,
                getString(R.string.lyric_ui_saved_toast),
                Toast.LENGTH_SHORT).show();
    }

    private void bindSwitchesFromPreferences() {
        boolean scrollScale = preferences.getBoolean(
                LyricUiSettings.KEY_SCROLL_SCALE_ENABLED,
                false);
        boolean inactiveBlur = preferences.getBoolean(
                LyricUiSettings.KEY_INACTIVE_BLUR_ENABLED,
                false);
        boolean lineTimedProgress = preferences.getBoolean(
                LyricUiSettings.KEY_LINE_TIMED_PROGRESS_ENABLED,
                LyricUiSettings.DEFAULT_LINE_TIMED_PROGRESS_ENABLED);
        boolean translationProgress = preferences.getBoolean(
                LyricUiSettings.KEY_TRANSLATION_PROGRESS_ENABLED,
                LyricUiSettings.DEFAULT_TRANSLATION_PROGRESS_ENABLED);
        boolean screenTimeoutEnabled = preferences.getBoolean(
                LyricUiSettings.KEY_SCREEN_TIMEOUT_ENABLED,
                LyricUiSettings.DEFAULT_SCREEN_TIMEOUT_ENABLED);
        int screenTimeoutSeconds = preferences.getInt(
                LyricUiSettings.KEY_SCREEN_TIMEOUT_SECONDS,
                LyricUiSettings.DEFAULT_SCREEN_TIMEOUT_SECONDS);
        bindSwitches(
                scrollScale,
                inactiveBlur,
                lineTimedProgress,
                translationProgress,
                screenTimeoutEnabled,
                screenTimeoutSeconds);
    }

    private void saveAndBroadcast(
            boolean scrollScale,
            boolean inactiveBlur,
            boolean lineTimedProgress,
            boolean translationProgress,
            boolean screenTimeoutEnabled,
            int screenTimeoutSeconds) {
        int sanitizedScreenTimeoutSeconds =
                LyricUiSettings.sanitizeScreenTimeoutSeconds(screenTimeoutSeconds);
        preferences.edit()
                .putBoolean(LyricUiSettings.KEY_SCROLL_SCALE_ENABLED, scrollScale)
                .putBoolean(LyricUiSettings.KEY_INACTIVE_BLUR_ENABLED, inactiveBlur)
                .putBoolean(LyricUiSettings.KEY_LINE_TIMED_PROGRESS_ENABLED, lineTimedProgress)
                .putBoolean(LyricUiSettings.KEY_TRANSLATION_PROGRESS_ENABLED, translationProgress)
                .putBoolean(LyricUiSettings.KEY_SCREEN_TIMEOUT_ENABLED, screenTimeoutEnabled)
                .putInt(
                        LyricUiSettings.KEY_SCREEN_TIMEOUT_SECONDS,
                        sanitizedScreenTimeoutSeconds)
                .apply();
        bindSwitches(
                scrollScale,
                inactiveBlur,
                lineTimedProgress,
                translationProgress,
                screenTimeoutEnabled,
                sanitizedScreenTimeoutSeconds);
        broadcastSettings(
                scrollScale,
                inactiveBlur,
                lineTimedProgress,
                translationProgress,
                screenTimeoutEnabled,
                sanitizedScreenTimeoutSeconds);
    }

    private void bindSwitches(
            boolean scrollScale,
            boolean inactiveBlur,
            boolean lineTimedProgress,
            boolean translationProgress,
            boolean screenTimeoutEnabled,
            int screenTimeoutSeconds) {
        int sanitizedScreenTimeoutSeconds =
                LyricUiSettings.sanitizeScreenTimeoutSeconds(screenTimeoutSeconds);
        binding = true;
        defaultSwitch.setChecked(!scrollScale
                && !inactiveBlur
                && lineTimedProgress == LyricUiSettings.DEFAULT_LINE_TIMED_PROGRESS_ENABLED
                && translationProgress == LyricUiSettings.DEFAULT_TRANSLATION_PROGRESS_ENABLED);
        lineTimedProgressSwitch.setChecked(lineTimedProgress);
        translationProgressSwitch.setChecked(translationProgress);
        scrollScaleSwitch.setChecked(scrollScale);
        inactiveBlurSwitch.setChecked(inactiveBlur);
        screenTimeoutSwitch.setChecked(screenTimeoutEnabled);
        String secondsText = sanitizedScreenTimeoutSeconds <= 0
                ? ""
                : Integer.toString(sanitizedScreenTimeoutSeconds);
        if (!screenTimeoutSecondsInput.getText().toString().equals(secondsText)) {
            screenTimeoutSecondsInput.setText(secondsText);
            screenTimeoutSecondsInput.setSelection(screenTimeoutSecondsInput.getText().length());
        }
        screenTimeoutSecondsInput.setEnabled(screenTimeoutEnabled);
        screenTimeoutSecondsInput.setAlpha(screenTimeoutEnabled ? 1f : 0.45f);
        binding = false;
    }

    private void broadcastCurrentSettings() {
        boolean scrollScale = preferences.getBoolean(
                LyricUiSettings.KEY_SCROLL_SCALE_ENABLED,
                false);
        boolean inactiveBlur = preferences.getBoolean(
                LyricUiSettings.KEY_INACTIVE_BLUR_ENABLED,
                false);
        boolean lineTimedProgress = preferences.getBoolean(
                LyricUiSettings.KEY_LINE_TIMED_PROGRESS_ENABLED,
                LyricUiSettings.DEFAULT_LINE_TIMED_PROGRESS_ENABLED);
        boolean translationProgress = preferences.getBoolean(
                LyricUiSettings.KEY_TRANSLATION_PROGRESS_ENABLED,
                LyricUiSettings.DEFAULT_TRANSLATION_PROGRESS_ENABLED);
        boolean screenTimeoutEnabled = preferences.getBoolean(
                LyricUiSettings.KEY_SCREEN_TIMEOUT_ENABLED,
                LyricUiSettings.DEFAULT_SCREEN_TIMEOUT_ENABLED);
        int screenTimeoutSeconds = preferences.getInt(
                LyricUiSettings.KEY_SCREEN_TIMEOUT_SECONDS,
                LyricUiSettings.DEFAULT_SCREEN_TIMEOUT_SECONDS);
        broadcastSettings(
                scrollScale,
                inactiveBlur,
                lineTimedProgress,
                translationProgress,
                screenTimeoutEnabled,
                LyricUiSettings.sanitizeScreenTimeoutSeconds(screenTimeoutSeconds));
    }

    private void broadcastSettings(
            boolean scrollScale,
            boolean inactiveBlur,
            boolean lineTimedProgress,
            boolean translationProgress,
            boolean screenTimeoutEnabled,
            int screenTimeoutSeconds) {
        Intent intent = new Intent(LyricUiSettings.ACTION_STYLE_CHANGED)
                .setPackage("com.android.systemui")
                .putExtra(LyricUiSettings.EXTRA_SCROLL_SCALE_ENABLED, scrollScale)
                .putExtra(LyricUiSettings.EXTRA_INACTIVE_BLUR_ENABLED, inactiveBlur)
                .putExtra(
                        LyricUiSettings.EXTRA_LINE_TIMED_PROGRESS_ENABLED,
                        lineTimedProgress)
                .putExtra(
                        LyricUiSettings.EXTRA_TRANSLATION_PROGRESS_ENABLED,
                        translationProgress)
                .putExtra(
                        LyricUiSettings.EXTRA_SCREEN_TIMEOUT_ENABLED,
                        screenTimeoutEnabled)
                .putExtra(
                        LyricUiSettings.EXTRA_SCREEN_TIMEOUT_SECONDS,
                        LyricUiSettings.sanitizeScreenTimeoutSeconds(screenTimeoutSeconds));
        sendBroadcast(intent);
    }

    private int readScreenTimeoutSecondsFromInput() {
        String value = screenTimeoutSecondsInput.getText().toString().trim();
        if (value.isEmpty()) {
            return LyricUiSettings.DEFAULT_SCREEN_TIMEOUT_SECONDS;
        }
        try {
            return LyricUiSettings.sanitizeScreenTimeoutSeconds(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return LyricUiSettings.DEFAULT_SCREEN_TIMEOUT_SECONDS;
        }
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

    private int getTopContentPadding() {
        return getStatusBarHeight() + getActionBarHeight() + dp(CONTENT_TOP_GAP_DP);
    }

    private int getActionBarHeight() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null && actionBar.getHeight() > 0) {
            return actionBar.getHeight();
        }
        TypedValue actionBarSize = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, actionBarSize, true)) {
            return TypedValue.complexToDimensionPixelSize(
                    actionBarSize.data,
                    getResources().getDisplayMetrics());
        }
        return dp(56);
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
