package io.github.andrealtb.lockscreenlyrics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class PlayerTranslationSettingsActivity extends SettingsBaseActivity {
    private static final String TAG = "LockscreenLyrics";
    private static final int[] PLAYER_ACCENTS = {
            0xFF4A90D9,
            0xFFE87C3E,
            0xFF31C27C,
            0xFFC62F2F,
            0xFFFC3C44,
            0xFF6C5CE7,
            0xFFF39C12,
            0xFF1DB954,
            0xFFFF6B81,
            0xFF2E86DE
    };
    private final ArrayList<EntryView> entryViews = new ArrayList<>();
    private final LinkedHashSet<String> clearRequestedPackages = new LinkedHashSet<>();
    private SharedPreferences preferences;
    private MaterialSwitch fallbackDefault;
    private long pendingSettingsRevision = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE);
        setTitle(R.string.player_translation_settings_title);
        setContentView(createContent());
    }

    private View createContent() {
        LyricUiConfig config = LyricUiConfigRepository.load(preferences);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int screenPadding = settingsScreenPadding();
        content.setPadding(screenPadding, screenPadding, screenPadding,
                settingsScreenBottomPadding() + dp(72));
        content.setBackgroundColor(settingsBackgroundColor());
        content.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        installSettingsInsets(content);

        TextView description = text(
                getString(R.string.sub_trans_desc),
                14,
                0xFF5F6368);
        description.setPadding(dp(4), dp(4), dp(4), dp(14));
        content.addView(description, matchWrap());

        LinearLayout fallbackCard = card();
        fallbackDefault = toggle(
                getString(R.string.sub_trans_fallback),
                config.defaultTranslationEnabled);
        fallbackCard.addView(fallbackDefault, matchWrap());
        content.addView(fallbackCard, marginBottom(dp(12)));

        int entryIndex = 0;
        for (PlayerTranslationSettings.Entry entry : PlayerTranslationSettings.entries()) {
            String playerName = getString(entry.labelRes);
            LinearLayout card = paddedCard();

            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = text(
                    playerName.isEmpty()
                            ? "?"
                            : playerName.substring(0, playerName.offsetByCodePoints(0, 1)),
                    14,
                    Color.WHITE);
            icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);
            GradientDrawable iconBackground = new GradientDrawable();
            iconBackground.setColor(PLAYER_ACCENTS[
                    Math.min(entryIndex, PLAYER_ACCENTS.length - 1)]);
            iconBackground.setCornerRadius(dp(10));
            icon.setBackground(iconBackground);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            iconParams.rightMargin = dp(10);
            header.addView(icon, iconParams);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(playerName, 13.5f, settingsTextColor());
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            labels.addView(title, matchWrap());
            String statusText;
            if (!entry.supportsTranslation) {
                statusText = getString(R.string.sub_trans_status_unsupported);
            } else {
                statusText = getString(R.string.sub_trans_status_configurable);
            }
            TextView status = text(statusText, 10.5f, 0xFF5F6368);
            status.setPadding(0, dp(2), 0, 0);
            labels.addView(status, matchWrap());
            header.addView(labels, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f));
            card.addView(header, matchWrap());
            MaterialSwitch defaultMaterialSwitch = null;
            MaterialSwitch buttonMaterialSwitch = null;
            if (entry.supportsTranslation) {
                boolean defaultEnabled = preferences.getBoolean(
                        LyricUiSettings.translationDefaultKeyForPackage(
                                entry.playerPackages[0]),
                        config.defaultTranslationEnabled);
                defaultMaterialSwitch = toggle(
                        getString(R.string.sub_trans_default),
                        defaultEnabled);
                defaultMaterialSwitch.setPadding(0, 0, 0, 0);
                boolean buttonEnabled = preferences.getBoolean(
                        LyricUiSettings.translationButtonKeyForPackage(
                                entry.playerPackages[0]),
                        true);
                buttonMaterialSwitch = toggle(
                        getString(R.string.sub_trans_button),
                        buttonEnabled);
                buttonMaterialSwitch.setPadding(0, 0, 0, 0);
                Button clear = button(getString(R.string.sub_trans_clear));
                clear.setOnClickListener(view -> {
                    for (String packageName : entry.playerPackages) {
                        clearRequestedPackages.add(packageName);
                    }
                    Toast.makeText(this,
                            getString(R.string.sub_trans_clear_toast, playerName),
                            Toast.LENGTH_SHORT).show();
                });
                card.addView(defaultMaterialSwitch, matchWrap());
                card.addView(buttonMaterialSwitch, matchWrap());
                card.addView(clear, matchWrap());
            }
            entryViews.add(new EntryView(
                    entry,
                    defaultMaterialSwitch,
                    buttonMaterialSwitch));
            content.addView(card, marginBottom(dp(12)));
            entryIndex++;
        }

        Button save = button(getString(R.string.sub_trans_save));
        styleGoldButton(save);
        removeButtonShadow(save);
        save.setOnClickListener(view -> save());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);

        FrameLayout stage = new FrameLayout(this);
        stage.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(65),
                Gravity.BOTTOM);
        stage.addView(settingsBottomAction(save), bottomParams);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(settingsBackgroundColor());
        page.addView(settingsAppBar(
                getString(R.string.player_translation_settings_title),
                null,
                this::finish), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                settingsActionBarHeight()));
        page.addView(stage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return page;
    }

    private void save() {
        boolean globalDefaultEnabled = fallbackDefault.isChecked();
        LyricUiConfig config = LyricUiSettings.withGlobalTranslationDefault(
                LyricUiConfigRepository.load(preferences),
                globalDefaultEnabled);
        LyricUiConfigRepository.save(preferences, config);

        ArrayList<String> packages = new ArrayList<>();
        ArrayList<Boolean> defaults = new ArrayList<>();
        ArrayList<String> buttonPackages = new ArrayList<>();
        ArrayList<Boolean> buttonValues = new ArrayList<>();
        SharedPreferences.Editor editor = preferences.edit();
        for (EntryView entryView : entryViews) {
            if (entryView.defaultMaterialSwitch == null) continue;
            boolean enabled = entryView.defaultMaterialSwitch.isChecked();
            boolean buttonEnabled = entryView.buttonMaterialSwitch.isChecked();
            for (String packageName : entryView.entry.playerPackages) {
                packages.add(packageName);
                defaults.add(enabled);
                editor.putBoolean(
                        LyricUiSettings.translationDefaultKeyForPackage(packageName),
                        enabled);
                buttonPackages.add(packageName);
                buttonValues.add(buttonEnabled);
                editor.putBoolean(
                        LyricUiSettings.translationButtonKeyForPackage(packageName),
                        buttonEnabled);
            }
        }
        editor.apply();

        boolean[] defaultValues = new boolean[defaults.size()];
        for (int i = 0; i < defaults.size(); i++) defaultValues[i] = defaults.get(i);
        boolean[] buttonValueArray = new boolean[buttonValues.size()];
        for (int i = 0; i < buttonValues.size(); i++) {
            buttonValueArray[i] = buttonValues.get(i);
        }
        long revision = LyricUiSettings.newSettingsRevision();
        pendingSettingsRevision = revision;
        Intent intent = new Intent(LyricUiSettings.ACTION_PLAYER_TRANSLATION_SETTINGS_CHANGED)
                .setPackage("com.android.systemui")
                .putExtra(
                        LyricUiSettings.EXTRA_DEFAULT_TRANSLATION_ENABLED,
                        globalDefaultEnabled)
                .putExtra(
                        LyricUiSettings.EXTRA_PLAYER_TRANSLATION_PACKAGES,
                        packages.toArray(new String[0]))
                .putExtra(
                        LyricUiSettings.EXTRA_PLAYER_TRANSLATION_DEFAULTS,
                        defaultValues)
                .putExtra(
                        LyricUiSettings.EXTRA_TRANSLATION_BUTTON_PACKAGES,
                        buttonPackages.toArray(new String[0]))
                .putExtra(
                        LyricUiSettings.EXTRA_TRANSLATION_BUTTON_VALUES,
                        buttonValueArray)
                .putExtra(
                        LyricUiSettings.EXTRA_CLEAR_TRANSLATION_PACKAGES,
                        clearRequestedPackages.toArray(new String[0]))
                .putExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, revision)
                .putExtra(
                        LyricUiSettings.EXTRA_SETTINGS_SOURCE,
                        LyricUiSettings.SOURCE_PLAYER_TRANSLATION)
                .putExtra(
                        LyricUiSettings.EXTRA_RESULT_RECEIVER,
                        createApplyResultReceiver(revision));
        sendBroadcast(intent);
        clearRequestedPackages.clear();
        logSettingsEvent(
                "settings-send",
                "Sent player translation settings"
                        + " | source=" + LyricUiSettings.SOURCE_PLAYER_TRANSLATION
                        + ", revision=" + revision
                        + ", alignment=" + config.alignment
                        + ", globalDefault=" + globalDefaultEnabled
                        + ", players=" + packages.size());
        Toast.makeText(this, getString(R.string.sub_trans_saved), Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(() -> {
            if (pendingSettingsRevision != revision) return;
            pendingSettingsRevision = -1L;
            Toast.makeText(
                    this,
                    getString(R.string.sub_trans_saved_pending),
                    Toast.LENGTH_LONG).show();
        }, 2_500L);
    }

    private ResultReceiver createApplyResultReceiver(long revision) {
        return new PlayerTranslationApplyResultReceiver(revision);
    }

    /** Named ResultReceiver: anonymous numbering shifts across builds and crashes SystemUI. */
    private final class PlayerTranslationApplyResultReceiver extends ResultReceiver {
        private final long revision;

        PlayerTranslationApplyResultReceiver(long revision) {
            super(new Handler(getMainLooper()));
            this.revision = revision;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (pendingSettingsRevision != revision || resultData == null) return;
            if (resultData.getLong(LyricUiSettings.RESULT_CONFIG_REVISION, -1L)
                    != revision) {
                return;
            }
            pendingSettingsRevision = -1L;
            boolean applied = resultCode == LyricUiSettings.RESULT_SETTINGS_APPLIED
                    && resultData.getBoolean(LyricUiSettings.RESULT_APPLIED, false);
            String process = resultData.getString(LyricUiSettings.RESULT_PROCESS, "unknown");
            String reason = resultData.getString(LyricUiSettings.RESULT_REASON, "");
            logSettingsEvent(
                    applied ? "settings-ack" : "settings-rejected",
                    "Received SystemUI settings acknowledgement"
                            + " | source=" + LyricUiSettings.SOURCE_PLAYER_TRANSLATION
                            + ", revision=" + revision
                            + ", process=" + process
                            + ", applied=" + applied
                            + ", reason=" + reason);
            Toast.makeText(
                    PlayerTranslationSettingsActivity.this,
                    applied
                            ? getString(R.string.sub_trans_applied)
                            : getString(R.string.sub_trans_rejected, reason),
                    applied ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        }
    }

    private void logSettingsEvent(String event, String message) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        Log.i(TAG, LyricLogFormatter.format(
                getPackageName(),
                LyricLogFormatter.Area.SETTINGS,
                event,
                message));
    }

    @SuppressWarnings("deprecation")
    private static final class EntryView {
        final PlayerTranslationSettings.Entry entry;
        final MaterialSwitch defaultMaterialSwitch;
        final MaterialSwitch buttonMaterialSwitch;
        EntryView(
                PlayerTranslationSettings.Entry entry,
                MaterialSwitch defaultMaterialSwitch,
                MaterialSwitch buttonMaterialSwitch) {
            this.entry = entry;
            this.defaultMaterialSwitch = defaultMaterialSwitch;
            this.buttonMaterialSwitch = buttonMaterialSwitch;
        }
    }
}
