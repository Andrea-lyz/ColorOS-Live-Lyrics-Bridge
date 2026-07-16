package io.github.andrealtb.lockscreenlyrics;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class PlayerTranslationSettingsActivity extends Activity {
    private static final int BACKGROUND = 0xFFF6F6F8;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF111111;
    private final ArrayList<EntryView> entryViews = new ArrayList<>();
    private final LinkedHashSet<String> clearRequestedPackages = new LinkedHashSet<>();
    private SharedPreferences preferences;
    private Switch fallbackDefault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        preferences = getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE);
        setTitle("播放器翻译设置");
        setContentView(createContent());
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private View createContent() {
        LyricUiConfig config = LyricUiConfigRepository.load(preferences);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        content.setBackgroundColor(BACKGROUND);
        content.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        installInsets(content);

        TextView description = text(
                "播放器自己的翻译按钮选择会优先于这里的默认值；可按播放器单独清除该记忆。",
                14,
                0xFF5F6368);
        description.setPadding(dp(4), dp(4), dp(4), dp(14));
        content.addView(description, matchWrap());

        LinearLayout fallbackCard = card();
        fallbackDefault = toggle("其他播放器默认显示翻译", config.defaultTranslationEnabled);
        fallbackCard.addView(fallbackDefault, matchWrap());
        content.addView(fallbackCard, marginBottom(dp(16)));

        for (PlayerTranslationSettings.Entry entry : PlayerTranslationSettings.entries()) {
            boolean available = entry.isBuiltIn() || isPackageInstalled(entry.providerPackage);
            LinearLayout card = card();
            TextView title = text(entry.label, 17, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(title, matchWrap());
            TextView status = text(
                    entry.isBuiltIn()
                            ? "内置适配"
                            : available ? "已检测到 LyricProvider" : "未安装对应 LyricProvider",
                    13,
                    available ? 0xFF5F6368 : 0xFF9AA0A6);
            status.setPadding(0, dp(2), 0, dp(4));
            card.addView(status, matchWrap());
            boolean defaultEnabled = preferences.getBoolean(
                    LyricUiSettings.translationDefaultKeyForPackage(entry.playerPackages[0]),
                    config.defaultTranslationEnabled);
            Switch defaultSwitch = toggle("默认显示翻译", defaultEnabled);
            Button clear = button("清除该播放器的翻译记忆");
            clear.setOnClickListener(view -> {
                for (String packageName : entry.playerPackages) {
                    clearRequestedPackages.add(packageName);
                }
                Toast.makeText(this, "将在保存时清除 " + entry.label + " 的记忆",
                        Toast.LENGTH_SHORT).show();
            });
            defaultSwitch.setEnabled(available);
            clear.setEnabled(available);
            card.setAlpha(available ? 1f : 0.48f);
            card.addView(defaultSwitch, matchWrap());
            card.addView(clear, matchWrap());
            entryViews.add(new EntryView(entry, defaultSwitch, available));
            content.addView(card, marginBottom(dp(12)));
        }

        Button save = button("保存并应用");
        save.setTextColor(Color.WHITE);
        save.setBackgroundColor(TEXT);
        save.setOnClickListener(view -> save());
        content.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        return scroll;
    }

    private void save() {
        LyricUiConfig config = LyricUiConfigRepository.load(preferences)
                .buildUpon()
                .defaultTranslationEnabled(fallbackDefault.isChecked())
                .build();
        LyricUiConfigRepository.save(preferences, config);

        ArrayList<String> packages = new ArrayList<>();
        ArrayList<Boolean> defaults = new ArrayList<>();
        SharedPreferences.Editor editor = preferences.edit();
        for (EntryView entryView : entryViews) {
            if (!entryView.available) continue;
            boolean enabled = entryView.defaultSwitch.isChecked();
            for (String packageName : entryView.entry.playerPackages) {
                packages.add(packageName);
                defaults.add(enabled);
                editor.putBoolean(
                        LyricUiSettings.translationDefaultKeyForPackage(packageName),
                        enabled);
            }
        }
        editor.apply();

        boolean[] defaultValues = new boolean[defaults.size()];
        for (int i = 0; i < defaults.size(); i++) defaultValues[i] = defaults.get(i);
        Intent intent = LyricUiConfigRepository.putSnapshot(
                new Intent(LyricUiSettings.ACTION_PLAYER_TRANSLATION_SETTINGS_CHANGED)
                        .setPackage("com.android.systemui")
                        .putExtra(
                                LyricUiSettings.EXTRA_PLAYER_TRANSLATION_PACKAGES,
                                packages.toArray(new String[0]))
                        .putExtra(
                                LyricUiSettings.EXTRA_PLAYER_TRANSLATION_DEFAULTS,
                                defaultValues)
                        .putExtra(
                                LyricUiSettings.EXTRA_CLEAR_TRANSLATION_PACKAGES,
                                clearRequestedPackages.toArray(new String[0])),
                config);
        sendBroadcast(intent);
        clearRequestedPackages.clear();
        Toast.makeText(this, "播放器翻译设置已应用", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    private boolean isPackageInstalled(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        try {
            getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(14), dp(10), dp(14), dp(12));
        view.setBackgroundColor(CARD);
        return view;
    }

    private Switch toggle(String label, boolean checked) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextSize(16);
        view.setTextColor(TEXT);
        view.setChecked(checked);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setMinHeight(dp(54));
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams marginBottom(int margin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = margin;
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    private void installInsets(View content) {
        int base = content.getPaddingTop();
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    view.getPaddingLeft(),
                    base + insets.getSystemWindowInsetTop(),
                    view.getPaddingRight(),
                    view.getPaddingBottom());
            return insets;
        });
    }

    private static final class EntryView {
        final PlayerTranslationSettings.Entry entry;
        final Switch defaultSwitch;
        final boolean available;

        EntryView(
                PlayerTranslationSettings.Entry entry,
                Switch defaultSwitch,
                boolean available) {
            this.entry = entry;
            this.defaultSwitch = defaultSwitch;
            this.available = available;
        }
    }
}
