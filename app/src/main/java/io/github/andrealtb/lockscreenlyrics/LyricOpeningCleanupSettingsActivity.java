package io.github.andrealtb.lockscreenlyrics;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** User-facing editor for opening lyric information cleanup. No regex or free-form DSL. */
public final class LyricOpeningCleanupSettingsActivity extends SettingsBaseActivity {
    private static final int MUTED = 0x99000000;

    private SharedPreferences preferences;
    private LyricContentCleanupConfig draft;
    private MaterialSwitch copyrightNotices;
    private MaterialSwitch productionCredits;
    private MaterialSwitch titleArtistLead;
    private TextView currentSong;
    private TextView currentStatus;
    private RadioGroup lyricRows;
    private LinearLayout suggestions;
    private LinearLayout learnedRules;
    private Button clearCurrentCorrection;
    private String currentTrackKey = "";
    private String currentRawLyric = "";
    private List<LyricOpeningCleanup.Line> currentLines = new ArrayList<>();
    private int selectedFirstFormalIndex = -1;
    private final LinkedHashMap<CheckBox, LyricContentCleanupConfig.LearnedRule>
            suggestionRules = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.opening_cleanup_settings_title);
        preferences = getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE);
        draft = LyricContentCleanupRepository.load(preferences);
        setContentView(createContent());
        bindConfig();
        requestCurrentLyrics();
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    private View createContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int screenPadding = settingsScreenPadding();
        content.setPadding(screenPadding, screenPadding, screenPadding,
                settingsScreenBottomPadding() + dp(72));
        content.setBackgroundColor(settingsBackgroundColor());
        content.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        installSettingsInsets(content);

        TextView intro = text(
                getString(R.string.sub_clean_notice),
                14,
                0xFF6A4A00);
        intro.setPadding(dp(14), dp(12), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable introBackground =
                new android.graphics.drawable.GradientDrawable();
        introBackground.setColor(0xFFFFF4DE);
        introBackground.setCornerRadius(dp(12));
        intro.setBackground(introBackground);
        content.addView(intro, marginBottom(dp(12)));

        LinearLayout builtIns = card();
        builtIns.addView(section(getString(R.string.sub_clean_builtin)));
        copyrightNotices = toggle(getString(R.string.sub_clean_copyright), false);
        productionCredits = toggle(getString(R.string.sub_clean_production), false);
        titleArtistLead = toggle(getString(R.string.sub_clean_titleartist), false);
        builtIns.addView(copyrightNotices);
        addCardDivider(builtIns);
        builtIns.addView(productionCredits);
        addCardDivider(builtIns);
        builtIns.addView(titleArtistLead);
        addCardDivider(builtIns);
        Button resetBuiltIns = button(getString(R.string.sub_clean_reset));
        resetBuiltIns.setOnClickListener(view -> {
            copyrightNotices.setChecked(false);
            productionCredits.setChecked(false);
            titleArtistLead.setChecked(false);
            rebuildCurrentSong();
        });
        LinearLayout.LayoutParams resetParams = matchWrap();
        resetParams.leftMargin = dp(17);
        resetParams.rightMargin = dp(17);
        resetParams.bottomMargin = dp(12);
        builtIns.addView(resetBuiltIns, resetParams);
        content.addView(builtIns, marginBottom(dp(12)));

        LinearLayout current = card();
        current.addView(section(getString(R.string.sub_clean_current)));
        currentSong = text(getString(R.string.sub_clean_song_loading), 17, settingsTextColor());
        currentSong.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        currentSong.setPadding(dp(17), dp(10), dp(17), 0);
        current.addView(currentSong, matchWrap());
        currentStatus = text(getString(R.string.sub_clean_status_hint), 13, MUTED);
        currentStatus.setPadding(dp(17), dp(4), dp(17), dp(8));
        current.addView(currentStatus, matchWrap());
        Button refresh = button(getString(R.string.sub_clean_refresh));
        refresh.setOnClickListener(view -> requestCurrentLyrics());
        LinearLayout.LayoutParams currentButtonParams = matchWrap();
        currentButtonParams.leftMargin = dp(17);
        currentButtonParams.rightMargin = dp(17);
        current.addView(refresh, currentButtonParams);
        clearCurrentCorrection = button(getString(R.string.sub_clean_clearper));
        clearCurrentCorrection.setOnClickListener(view -> {
            if (currentTrackKey.isEmpty()) return;
            draft = draft.buildUpon().removeTrackOverride(currentTrackKey).build();
            selectedFirstFormalIndex = -1;
            rebuildCurrentSong();
        });
        LinearLayout.LayoutParams clearParams = matchWrap();
        clearParams.leftMargin = dp(17);
        clearParams.rightMargin = dp(17);
        current.addView(clearCurrentCorrection, clearParams);
        lyricRows = new RadioGroup(this);
        lyricRows.setOrientation(LinearLayout.VERTICAL);
        lyricRows.setPadding(dp(17), dp(6), dp(17), dp(12));
        current.addView(lyricRows, matchWrap());
        content.addView(current, marginBottom(dp(12)));

        suggestions = card();
        suggestions.addView(section(getString(R.string.sub_clean_suggestions)));
        TextView suggestionsHint = text(
                getString(R.string.sub_clean_suggestions_hint),
                13,
                MUTED);
        suggestionsHint.setPadding(dp(17), dp(10), dp(17), dp(14));
        suggestions.addView(suggestionsHint, matchWrap());
        content.addView(suggestions, marginBottom(dp(12)));

        learnedRules = card();
        learnedRules.addView(section(getString(R.string.sub_clean_learned)));
        content.addView(learnedRules, marginBottom(dp(12)));

        Button save = button(getString(R.string.sub_clean_save));
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
                getString(R.string.opening_cleanup_settings_title),
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

    private void bindConfig() {
        copyrightNotices.setChecked(draft.copyrightNoticesEnabled);
        productionCredits.setChecked(draft.productionCreditsEnabled);
        titleArtistLead.setChecked(draft.titleArtistLeadEnabled);
        View.OnClickListener changed = view -> rebuildCurrentSong();
        copyrightNotices.setOnClickListener(changed);
        productionCredits.setOnClickListener(changed);
        titleArtistLead.setOnClickListener(changed);
        rebuildLearnedRules();
    }

    @SuppressWarnings("deprecation")
    private void requestCurrentLyrics() {
        currentStatus.setText(R.string.cleanup_loading_current_lyrics);
        ResultReceiver receiver = new MediaSnapshotResultReceiver();
        Intent request = new Intent(LyricUiSettings.ACTION_REQUEST_MEDIA_SNAPSHOT)
                .setPackage("com.android.systemui")
                .putExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER, receiver);
        sendBroadcast(request);
    }

    /** Named ResultReceiver: anonymous numbering shifts across builds and crashes SystemUI. */
    private final class MediaSnapshotResultReceiver extends ResultReceiver {
        MediaSnapshotResultReceiver() {
            super(new Handler(getMainLooper()));
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultData == null) {
                showNoCurrentLyrics();
                return;
            }
            String title = resultData.getString(LyricUiSettings.RESULT_TITLE, "");
            String artist = resultData.getString(LyricUiSettings.RESULT_ARTIST, "");
            currentTrackKey = resultData.getString(
                    LyricUiSettings.RESULT_TRACK_KEY,
                    "");
            currentRawLyric = resultData.getString(
                    LyricUiSettings.RESULT_RAW_LYRIC,
                    "");
            currentSong.setText(title.isEmpty()
                    ? getString(R.string.sub_clean_song_current)
                    : artist.isEmpty()
                    ? title
                    : getString(R.string.cleanup_current_song_artist, title, artist));
            currentLines = LyricOpeningCleanup.parseLines(currentRawLyric);
            if (currentTrackKey.isEmpty() || currentLines.isEmpty()) {
                showNoCurrentLyrics();
                return;
            }
            selectedFirstFormalIndex = findStoredFirstFormalIndex();
            rebuildCurrentSong();
        }
    }

    private void showNoCurrentLyrics() {
        currentTrackKey = "";
        currentRawLyric = "";
        currentLines = new ArrayList<>();
        selectedFirstFormalIndex = -1;
        lyricRows.removeAllViews();
        currentSong.setText(getString(R.string.sub_clean_song_none));
        currentStatus.setText(getString(R.string.sub_clean_song_none_hint));
        clearCurrentCorrection.setEnabled(false);
        rebuildSuggestions(new ArrayList<>());
    }

    private void rebuildCurrentSong() {
        LyricContentCleanupConfig previewConfig = readDraft(false);
        List<LyricOpeningCleanup.Decision> decisions = LyricOpeningCleanup.analyze(
                currentLines,
                currentTrackKey,
                previewConfig);
        lyricRows.removeAllViews();
        if (decisions.isEmpty()) {
            if (!currentTrackKey.isEmpty()) showNoCurrentLyrics();
            return;
        }
        int hidden = 0;
        for (int index = 0; index < decisions.size(); index++) {
            LyricOpeningCleanup.Decision decision = decisions.get(index);
            if (decision.hidden) hidden++;
            RadioButton row = new RadioButton(this);
            row.setId(View.generateViewId());
            row.setTag(index);
            row.setText(formatLine(decision));
            row.setTextSize(14);
            row.setTextColor(decision.hidden ? 0xFF6B6B6B : settingsTextColor());
            row.setGravity(Gravity.TOP | Gravity.START);
            row.setPadding(0, dp(7), 0, dp(7));
            row.setChecked(index == selectedFirstFormalIndex);
            row.setOnClickListener(view -> {
                selectedFirstFormalIndex = (Integer) view.getTag();
                rebuildCurrentSong();
            });
            lyricRows.addView(row, matchWrap());
        }
        currentStatus.setText(getString(R.string.cleanup_hidden_count, hidden));
        clearCurrentCorrection.setEnabled(
                draft.firstFormalLineByTrack.containsKey(currentTrackKey)
                        || selectedFirstFormalIndex >= 0);
        rebuildSuggestions(decisions);
    }

    private void rebuildSuggestions(List<LyricOpeningCleanup.Decision> decisions) {
        while (suggestions.getChildCount() > 1) suggestions.removeViewAt(1);
        suggestionRules.clear();
        if (selectedFirstFormalIndex <= 0 || decisions == null || decisions.isEmpty()) {
            TextView empty = text(
                    getString(R.string.sub_clean_pickfirst),
                    14,
                    MUTED);
            empty.setPadding(dp(17), dp(10), dp(17), dp(14));
            suggestions.addView(empty, matchWrap());
            return;
        }
        LyricContentCleanupConfig withoutTrackOverride = readDraft(false)
                .buildUpon()
                .removeTrackOverride(currentTrackKey)
                .build();
        List<LyricOpeningCleanup.Decision> automatic = LyricOpeningCleanup.analyze(
                currentLines,
                currentTrackKey,
                withoutTrackOverride);
        for (int index = 0;
                index < selectedFirstFormalIndex && index < automatic.size();
                index++) {
            LyricOpeningCleanup.Decision decision = automatic.get(index);
            if (decision.reason != LyricOpeningCleanup.Reason.VISIBLE) {
                continue;
            }
            LyricContentCleanupConfig.LearnedRule proposed =
                    LyricOpeningCleanup.proposeLearnedRule(decision.line.text);
            if (proposed == null || draft.learnedRules.contains(proposed)
                    || suggestionRules.containsValue(proposed)) {
                continue;
            }
            CheckBox option = new CheckBox(this);
            option.setText(getString(
                    R.string.cleanup_learn_similar,
                    displayRule(proposed)));
            option.setTextSize(14);
            option.setTextColor(settingsTextColor());
            option.setPadding(dp(17), dp(6), dp(17), dp(6));
            suggestions.addView(option, matchWrap());
            suggestionRules.put(option, proposed);
        }
        if (suggestionRules.isEmpty()) {
            TextView empty = text(
                    getString(R.string.sub_clean_suggestions_none),
                    14,
                    MUTED);
            empty.setPadding(dp(17), dp(10), dp(17), dp(14));
            suggestions.addView(empty, matchWrap());
        }
    }

    private void rebuildLearnedRules() {
        while (learnedRules.getChildCount() > 1) learnedRules.removeViewAt(1);
        if (draft.learnedRules.isEmpty()) {
            TextView empty = text(
                    getString(R.string.sub_clean_learned_none),
                    14,
                    MUTED);
            empty.setPadding(dp(17), dp(10), dp(17), dp(14));
            learnedRules.addView(empty, matchWrap());
            return;
        }
        for (LyricContentCleanupConfig.LearnedRule rule : draft.learnedRules) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(17), 0, dp(17), 0);
            TextView label = text(displayRule(rule), 14, settingsTextColor());
            row.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1f));
            Button remove = button(getString(R.string.sub_clean_remove));
            remove.setOnClickListener(view -> {
                draft = draft.buildUpon().removeLearnedRule(rule).build();
                rebuildLearnedRules();
                rebuildCurrentSong();
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(90), dp(48)));
            learnedRules.addView(row, matchWrap());
        }
        Button clear = button(getString(R.string.sub_clean_learned_clear));
        clear.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(getString(R.string.sub_clean_learned_clear_title))
                .setMessage(getString(R.string.sub_clean_learned_clear_message))
                .setNegativeButton(getString(R.string.sub_clean_cancel), null)
                .setPositiveButton(getString(R.string.sub_clean_clear), (dialog, which) -> {
                    draft = draft.buildUpon().clearLearnedRules().build();
                    rebuildLearnedRules();
                    rebuildCurrentSong();
                })
                .show());
        LinearLayout.LayoutParams clearParams = matchWrap();
        clearParams.leftMargin = dp(17);
        clearParams.rightMargin = dp(17);
        clearParams.bottomMargin = dp(12);
        learnedRules.addView(clear, clearParams);
    }

    private LyricContentCleanupConfig readDraft(boolean includeSuggestions) {
        LyricContentCleanupConfig.Builder builder = draft.buildUpon()
                .copyrightNoticesEnabled(copyrightNotices.isChecked())
                .productionCreditsEnabled(productionCredits.isChecked())
                .titleArtistLeadEnabled(titleArtistLead.isChecked());
        if (!currentTrackKey.isEmpty()) {
            if (selectedFirstFormalIndex >= 0
                    && selectedFirstFormalIndex < currentLines.size()) {
                builder.firstFormalLine(
                        currentTrackKey,
                        currentLines.get(selectedFirstFormalIndex).fingerprint);
            } else if (!draft.firstFormalLineByTrack.containsKey(currentTrackKey)) {
                builder.removeTrackOverride(currentTrackKey);
            }
        }
        if (includeSuggestions) {
            for (Map.Entry<CheckBox, LyricContentCleanupConfig.LearnedRule> entry
                    : suggestionRules.entrySet()) {
                if (entry.getKey().isChecked()) builder.addLearnedRule(entry.getValue());
            }
        }
        return builder.build();
    }

    private void save() {
        LyricContentCleanupConfig config = readDraft(true);
        final String encoded;
        try {
            encoded = config.encode();
            LyricContentCleanupRepository.save(preferences, config);
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(LyricUiSettings.ACTION_CONTENT_CLEANUP_CHANGED)
                .setPackage(LyricContentCleanupConfigTransfer.SYSTEM_UI_PACKAGE);
        if (encoded.length() <= LyricContentCleanupConfigTransfer.LEGACY_INLINE_MAX_CHARS) {
            intent.putExtra(LyricUiSettings.EXTRA_CONTENT_CLEANUP_CONFIG, encoded);
        }
        if (!LyricContentCleanupConfigTransfer.grantSystemUiReadAccess(this)) {
            Toast.makeText(this, getString(R.string.sub_clean_grant_failed), Toast.LENGTH_LONG).show();
            return;
        }
        LyricContentCleanupConfigTransfer.attachConfigUri(intent);
        sendBroadcast(intent);
        draft = config;
        selectedFirstFormalIndex = findStoredFirstFormalIndex();
        rebuildLearnedRules();
        rebuildCurrentSong();
        Toast.makeText(this, getString(R.string.sub_clean_applied), Toast.LENGTH_SHORT).show();
    }

    private int findStoredFirstFormalIndex() {
        String fingerprint = draft.firstFormalLineByTrack.get(currentTrackKey);
        if (fingerprint == null) return -1;
        for (int index = 0; index < currentLines.size(); index++) {
            if (fingerprint.equals(currentLines.get(index).fingerprint)) return index;
        }
        return -1;
    }

    private String formatLine(LyricOpeningCleanup.Decision decision) {
        String time = formatTime(decision.line.timeMillis);
        String label = decision.hidden
                ? LyricOpeningCleanup.reasonLabel(decision.reason)
                : getString(R.string.sub_clean_formal);
        return time + "  " + decision.line.text + "\n" + label;
    }

    private String displayRule(LyricContentCleanupConfig.LearnedRule rule) {
        if (rule == null) return "";
        return rule.type == LyricContentCleanupConfig.LearnedType.PREFIX
                ? getString(R.string.sub_clean_rule_prefix, rule.value)
                : getString(R.string.sub_clean_rule_exact, rule.value);
    }

    private static String formatTime(long timeMillis) {
        long totalSeconds = Math.max(0L, timeMillis) / 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

}
