package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.autofill.AutofillManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;

/**
 * Shared window, inset and layout policy for the settings activities.
 *
 * <p>The layout helpers below (dp/text/card/section/toggle/button/matchWrap/marginBottom) used
 * to be copied verbatim into every settings screen; they are hoisted here so sub-pages keep the
 * same visual language without duplicating the code.</p>
 */
abstract class SettingsBaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(applySettingsLocale(newBase));
    }

    /** Applies the persisted settings-page locale (default: Chinese) to this activity. */
    private static Context applySettingsLocale(Context base) {
        String tag = base.getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE)
                .getString(LyricUiSettings.PREF_SETTINGS_LOCALE, null);
        if (tag == null || tag.isEmpty()) return base;
        Locale locale = Locale.forLanguageTag(tag);
        if (locale == null || locale.getLanguage().isEmpty()) return base;
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        return base.createConfigurationContext(configuration);
    }

    /** Toggles the settings-page language between Chinese and English and rebuilds the page. */
    protected final void toggleSettingsLocale() {
        String current = getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE)
                .getString(LyricUiSettings.PREF_SETTINGS_LOCALE, null);
        String next = current != null && current.startsWith("en") ? "zh-CN" : "en";
        getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE)
                .edit()
                .putString(LyricUiSettings.PREF_SETTINGS_LOCALE, next)
                .apply();
        recreate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSettingsWindow();
        cancelAutofillSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A previous visit can leave a one-shot Autofill session alive even after the
        // hierarchy has been marked ineligible. Cancel it again when this page returns.
        cancelAutofillSession();
    }

    protected final void cancelAutofillSession() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return;
        AutofillManager manager = getSystemService(AutofillManager.class);
        if (manager != null) manager.cancel();
    }

    protected final void configureSettingsWindow() {
        Window window = getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // This is the activity-level boundary: neither the decor nor any settings
        // descendant should participate in Autofill discovery or save flows.
        window.getDecorView().setImportantForAutofill(
                View.IMPORTANT_FOR_AUTOFILL_NO);
        // Official edge-to-edge contract: gesture navigation stays transparent while app
        // surfaces draw behind it; tappable content is moved out with WindowInsets below.
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // This only affects three-button navigation; gesture navigation remains transparent.
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }

    /**
     * Insets the scrollable content away from side cutouts and the fixed bottom action surface.
     * The background still draws through the transparent navigation bar.
     */
    protected final void installSettingsInsets(View content) {
        final int baseLeft = content.getPaddingLeft();
        final int baseTop = content.getPaddingTop();
        final int baseRight = content.getPaddingRight();
        final int baseBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    baseLeft + insets.left,
                    baseTop,
                    baseRight + insets.right,
                    baseBottom + insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    /** Adds only safe-area padding; the surface itself continues behind the navigation bar. */
    protected final void installBottomSurfaceInsets(View surface) {
        final int baseLeft = surface.getPaddingLeft();
        final int baseTop = surface.getPaddingTop();
        final int baseRight = surface.getPaddingRight();
        final int baseBottom = surface.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(surface, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    baseLeft + insets.left,
                    baseTop,
                    baseRight + insets.right,
                    baseBottom + insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(surface);
    }

    /** Keeps a floating element above the expanded bottom action surface. */
    protected final void installNavigationBarBottomMargin(
            View view,
            int baseBottomMargin) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.LayoutParams rawParams = target.getLayoutParams();
            if (rawParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) rawParams;
                int desiredBottom = baseBottomMargin + insets.bottom;
                if (params.bottomMargin != desiredBottom) {
                    params.bottomMargin = desiredBottom;
                    target.setLayoutParams(params);
                }
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    @ColorInt
    protected int settingsBackgroundColor() {
        return getColor(R.color.settings_background);
    }

    protected final int settingsSurfaceColor() {
        return getColor(R.color.settings_surface);
    }

    protected final int settingsTextColor() {
        return getColor(R.color.settings_text);
    }

    protected final int settingsScreenPadding() {
        return getResources().getDimensionPixelSize(R.dimen.settings_screen_padding);
    }

    protected final int settingsScreenBottomPadding() {
        return getResources().getDimensionPixelSize(R.dimen.settings_screen_bottom_padding);
    }

    protected final int settingsActionBarHeight() {
        return getResources().getDimensionPixelSize(R.dimen.settings_action_bar_height);
    }

    protected final int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected final LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    protected final LinearLayout.LayoutParams marginBottom(int margin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = margin;
        return params;
    }

    protected final TextView text(String value, float size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    protected final LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(settingsSurfaceColor());
        background.setCornerRadius(dp(22));
        background.setStroke(dp(1), 0x141B222C);
        view.setBackground(background);
        view.setClipToOutline(true);
        view.setElevation(dp(1));
        return view;
    }

    protected final LinearLayout paddedCard() {
        LinearLayout view = card();
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        return view;
    }

    protected final void addCardDivider(LinearLayout card) {
        View divider = new View(this);
        divider.setBackgroundColor(0x141B222C);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(0.5f)));
        params.leftMargin = dp(17);
        params.rightMargin = dp(17);
        card.addView(divider, params);
    }

    protected final View section(String title) {
        return section(0, title);
    }

    /** Section header with an optional gold icon chip and mockup corner label. */
    protected final View section(int iconRes, String title) {
        return section(iconRes, title, null);
    }

    /** Section header with an optional gold icon chip and mockup corner label. */
    protected final View section(int iconRes, String title, String cornerLabel) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(17), dp(15), dp(17), dp(4));
        if (iconRes != 0) {
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable chipBackground =
                    new android.graphics.drawable.GradientDrawable();
            chipBackground.setColor(0x29F2C14E);
            chipBackground.setCornerRadius(dp(9));
            chip.setBackground(chipBackground);
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    0xFF6F4A0D,
                    android.graphics.PorterDuff.Mode.SRC_IN));
            chip.addView(icon, new LinearLayout.LayoutParams(dp(15), dp(15)));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    dp(26),
                    dp(26));
            chipParams.rightMargin = dp(9);
            header.addView(chip, chipParams);
        }
        TextView view = text(title, 13.5f, settingsTextColor());
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(view, matchWrap());
        if (cornerLabel != null && !cornerLabel.isEmpty()) {
            TextView label = new TextView(this);
            label.setText(cornerLabel);
            label.setTextSize(8f);
            label.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
            label.setLetterSpacing(0.24f);
            label.setTextColor(getColor(R.color.settings_text_muted));
            label.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            label.setPadding(dp(8), 0, dp(2), 0);
            header.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f));
        }
        return header;
    }

    protected final MaterialSwitch toggle(String label, boolean checked) {
        MaterialSwitch view = new MaterialSwitch(this);
        view.setText(label);
        view.setTextSize(13);
        view.setTextColor(settingsTextColor());
        view.setChecked(checked);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setMinHeight(dp(48));
        view.setPadding(dp(17), 0, dp(13), 0);
        return view;
    }

    protected final Button button(String label) {
        MaterialButton button = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12.5f);
        button.setTextColor(settingsTextColor());
        button.setCornerRadius(dp(18));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setStrokeColor(ColorStateList.valueOf(0x24344455));
        button.setStrokeWidth(dp(1));
        button.setMinHeight(dp(42));
        return button;
    }

    protected final void styleGoldButton(Button button) {
        button.setTextColor(0xFF231603);
        if (button instanceof MaterialButton) {
            MaterialButton materialButton = (MaterialButton) button;
            materialButton.setBackgroundTintList(ColorStateList.valueOf(
                    getColor(R.color.settings_primary_deep)));
            materialButton.setStrokeWidth(0);
            materialButton.setCornerRadius(dp(24));
        } else {
            button.setBackgroundColor(getColor(R.color.settings_primary_deep));
        }
        button.setElevation(dp(3));
    }

    protected final void removeButtonShadow(Button button) {
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setTranslationZ(0f);
    }

    /** Settings-themed unsaved-changes dialog shared by independent sub-pages. */
    protected final void showSettingsDiscardDialog(Runnable onDiscard) {
        showSettingsConfirmDialog(
                R.string.back_discard_title,
                R.string.back_discard_message,
                R.string.back_discard,
                true,
                onDiscard);
    }

    /** Settings-themed confirmation dialog for restore/reset operations. */
    protected final void showSettingsConfirmDialog(
            int titleRes,
            int messageRes,
            int positiveRes,
            boolean destructive,
            Runnable onConfirm) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.WHITE);
        panelBackground.setCornerRadius(dp(20));
        panelBackground.setStroke(dp(1), 0x1A1B222C);
        panel.setBackground(panelBackground);

        TextView title = text(
                getString(titleRes),
                17,
                settingsTextColor());
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        panel.addView(title, matchWrap());

        TextView message = text(
                getString(messageRes),
                13,
                getColor(R.color.settings_text_secondary));
        message.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(9);
        messageParams.bottomMargin = dp(18);
        panel.addView(message, messageParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = settingsPillButton(
                getString(R.string.dialog_cancel),
                0x121B222C,
                0xFF5C6774,
                Typeface.NORMAL);
        cancel.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        cancelParams.rightMargin = dp(9);
        actions.addView(cancel, cancelParams);

        Button discard = settingsPillButton(
                getString(positiveRes),
                destructive ? 0x24C04A3A : 0x29F2C14E,
                destructive ? getColor(R.color.settings_error) : 0xFF6F4A0D,
                Typeface.BOLD);
        discard.setOnClickListener(view -> {
            dialog.dismiss();
            onConfirm.run();
        });
        actions.addView(discard, new LinearLayout.LayoutParams(0, dp(42), 1f));
        panel.addView(actions, matchWrap());

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = dialogWindow.getAttributes();
            attributes.dimAmount = 0.28f;
            dialogWindow.setAttributes(attributes);
            dialogWindow.setGravity(Gravity.CENTER);
        }
        dialog.show();
        if (dialogWindow != null) {
            int width = Math.min(
                    dp(320),
                    getResources().getDisplayMetrics().widthPixels - dp(28));
            dialogWindow.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private Button settingsPillButton(
            String label,
            int background,
            int textColor,
            int style) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(12.5f);
        button.setTypeface(Typeface.DEFAULT, style);
        button.setGravity(Gravity.CENTER);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setColor(background);
        backgroundDrawable.setCornerRadius(dp(23));
        button.setBackground(backgroundDrawable);
        removeButtonShadow(button);
        return button;
    }

    /** Fixed save/action surface used by the two settings sub-pages. */
    protected final View settingsBottomAction(Button action) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(14), 0, dp(14), dp(8));
        bar.setBackgroundColor(settingsSurfaceColor());
        bar.setElevation(dp(10));

        View divider = new View(this);
        divider.setBackgroundColor(0x1A1B222C);
        bar.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(0.5f))));

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        actionParams.topMargin = dp(8);
        bar.addView(action, actionParams);
        final int baseHeight = dp(65);
        final int baseLeft = bar.getPaddingLeft();
        final int baseTop = bar.getPaddingTop();
        final int baseRight = bar.getPaddingRight();
        final int baseBottom = bar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(bar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    baseLeft + insets.left,
                    baseTop,
                    baseRight + insets.right,
                    baseBottom + insets.bottom);
            ViewGroup.LayoutParams params = view.getLayoutParams();
            int desiredHeight = baseHeight + insets.bottom;
            if (params != null && params.height != desiredHeight) {
                params.height = desiredHeight;
                view.setLayoutParams(params);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(bar);
        return bar;
    }

    /** Mockup app bar shared by the main screen and both independent sub-pages. */
    protected final View settingsAppBar(
            String title,
            String version,
            Runnable onBack) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(5), dp(16), dp(5));
        bar.setBackgroundColor(settingsBackgroundColor());
        final int appBarBaseHeight = settingsActionBarHeight();
        final int appBarBaseLeft = bar.getPaddingLeft();
        final int appBarBaseTop = bar.getPaddingTop();
        final int appBarBaseRight = bar.getPaddingRight();
        final int appBarBaseBottom = bar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(bar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    appBarBaseLeft + insets.left,
                    appBarBaseTop + insets.top,
                    appBarBaseRight + insets.right,
                    appBarBaseBottom);
            ViewGroup.LayoutParams params = view.getLayoutParams();
            int desiredHeight = appBarBaseHeight + insets.top;
            if (params != null && params.height != desiredHeight) {
                params.height = desiredHeight;
                view.setLayoutParams(params);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(bar);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(settingsTextColor());
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        back.setContentDescription(getString(android.R.string.cancel));
        GradientDrawable rippleMask = new GradientDrawable();
        rippleMask.setShape(GradientDrawable.OVAL);
        rippleMask.setColor(Color.WHITE);
        back.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x141B222C),
                null,
                rippleMask));
        back.setOnClickListener(view -> onBack.run());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        backParams.rightMargin = dp(6);
        bar.addView(back, backParams);

        TextView titleView = text(title, 19, settingsTextColor());
        titleView.setTypeface(Typeface.create(Typeface.DEFAULT, 500));
        titleView.setSingleLine(true);
        bar.addView(titleView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        if (version != null && !version.isEmpty()) {
            TextView versionPill = text(version, 9.5f, 0xFF9A6A12);
            versionPill.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
            versionPill.setGravity(Gravity.CENTER);
            versionPill.setLetterSpacing(0.12f);
            versionPill.setPadding(dp(10), 0, dp(10), 0);
            GradientDrawable pillBackground = new GradientDrawable();
            pillBackground.setColor(0x2EF2C14E);
            pillBackground.setStroke(dp(1), 0x80F2C14E);
            pillBackground.setCornerRadius(dp(99));
            versionPill.setBackground(pillBackground);
            bar.addView(versionPill, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(26)));
        }
        return bar;
    }
}
