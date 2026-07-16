package io.github.andrealtb.lockscreenlyrics;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** User-facing editor for opening lyric information cleanup. No regex or free-form DSL. */
public final class LyricOpeningCleanupSettingsActivity extends Activity {
    private static final int BACKGROUND = 0xFFF6F6F8;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF111111;
    private static final int MUTED = 0x99000000;

    private SharedPreferences preferences;
    private LyricContentCleanupConfig draft;
    private Switch copyrightNotices;
    private Switch productionCredits;
    private Switch titleArtistLead;
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
        configureWindow();
        ActionBar actionBar = getActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);
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

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private View createContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        content.setBackgroundColor(BACKGROUND);
        content.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        installInsets(content);

        TextView intro = text(
                "模块只清理歌词开头的制作、版权等说明，不修改时间轴、逐字效果或歌曲信息。识别不完整时，选择当前歌曲第一句正式歌词即可。",
                14,
                0xFF6A4A00);
        intro.setPadding(dp(14), dp(12), dp(14), dp(12));
        intro.setBackgroundColor(0xFFFFF4DE);
        content.addView(intro, marginBottom(dp(18)));

        content.addView(section("内置清理规则"));
        LinearLayout builtIns = card();
        copyrightNotices = toggle("版权与权利声明", true);
        productionCredits = toggle("制作人员与乐器信息", true);
        titleArtistLead = toggle("开头的“歌名 - 歌手”", true);
        builtIns.addView(copyrightNotices);
        builtIns.addView(productionCredits);
        builtIns.addView(titleArtistLead);
        TextView protectedRule = text(
                "翻译来源占位等会影响主歌词/翻译识别的规则属于解析保护，始终启用。",
                13,
                MUTED);
        protectedRule.setPadding(0, dp(8), 0, dp(4));
        builtIns.addView(protectedRule, matchWrap());
        Button resetBuiltIns = button("恢复内置规则默认值");
        resetBuiltIns.setOnClickListener(view -> {
            copyrightNotices.setChecked(true);
            productionCredits.setChecked(true);
            titleArtistLead.setChecked(true);
            rebuildCurrentSong();
        });
        builtIns.addView(resetBuiltIns, matchWrap());
        content.addView(builtIns, marginBottom(dp(16)));

        content.addView(section("检查并修正当前歌曲"));
        LinearLayout current = card();
        currentSong = text("正在读取当前歌曲…", 17, TEXT);
        currentSong.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        current.addView(currentSong, matchWrap());
        currentStatus = text("请保持歌曲播放并确保锁屏歌词已加载。", 13, MUTED);
        currentStatus.setPadding(0, dp(4), 0, dp(8));
        current.addView(currentStatus, matchWrap());
        Button refresh = button("重新读取当前歌词");
        refresh.setOnClickListener(view -> requestCurrentLyrics());
        current.addView(refresh, matchWrap());
        clearCurrentCorrection = button("清除这首歌的手动修正");
        clearCurrentCorrection.setOnClickListener(view -> {
            if (currentTrackKey.isEmpty()) return;
            draft = draft.buildUpon().removeTrackOverride(currentTrackKey).build();
            selectedFirstFormalIndex = -1;
            rebuildCurrentSong();
        });
        current.addView(clearCurrentCorrection, matchWrap());
        lyricRows = new RadioGroup(this);
        lyricRows.setOrientation(LinearLayout.VERTICAL);
        current.addView(lyricRows, matchWrap());
        content.addView(current, marginBottom(dp(16)));

        content.addView(section("可学习的格式"));
        suggestions = card();
        suggestions.addView(text(
                "选择第一句正式歌词后，模块会从前面的未识别行中提取安全格式。勾选后会用于其他歌曲；过长或不稳定的内容只修正当前歌曲。",
                13,
                MUTED), matchWrap());
        content.addView(suggestions, marginBottom(dp(16)));

        content.addView(section("已学习内容"));
        learnedRules = card();
        content.addView(learnedRules, marginBottom(dp(16)));

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
        ResultReceiver receiver = new ResultReceiver(new Handler()) {
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
                        ? "当前歌曲"
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
        };
        Intent request = new Intent(LyricUiSettings.ACTION_REQUEST_MEDIA_SNAPSHOT)
                .setPackage("com.android.systemui")
                .putExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER, receiver);
        sendBroadcast(request);
    }

    private void showNoCurrentLyrics() {
        currentTrackKey = "";
        currentRawLyric = "";
        currentLines = new ArrayList<>();
        selectedFirstFormalIndex = -1;
        lyricRows.removeAllViews();
        currentSong.setText("未读取到可检查的歌词");
        currentStatus.setText("请播放一首有逐行或逐字歌词的歌曲，等待锁屏歌词出现后重试。");
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
            row.setTextColor(decision.hidden ? 0xFF6B6B6B : TEXT);
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
            suggestions.addView(text("先在上方选择第一句正式歌词。", 14, MUTED), matchWrap());
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
            option.setTextColor(TEXT);
            option.setPadding(0, dp(6), 0, dp(6));
            suggestions.addView(option, matchWrap());
            suggestionRules.put(option, proposed);
        }
        if (suggestionRules.isEmpty()) {
            suggestions.addView(text(
                    "没有可安全复用的格式；保存后仍会只修正当前歌曲。",
                    14,
                    MUTED), matchWrap());
        }
    }

    private void rebuildLearnedRules() {
        learnedRules.removeAllViews();
        if (draft.learnedRules.isEmpty()) {
            learnedRules.addView(text("尚未学习任何额外格式。", 14, MUTED), matchWrap());
            return;
        }
        for (LyricContentCleanupConfig.LearnedRule rule : draft.learnedRules) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(displayRule(rule), 14, TEXT);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1f));
            Button remove = button("删除");
            remove.setOnClickListener(view -> {
                draft = draft.buildUpon().removeLearnedRule(rule).build();
                rebuildLearnedRules();
                rebuildCurrentSong();
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(90), dp(48)));
            learnedRules.addView(row, matchWrap());
        }
        Button clear = button("清除全部已学习格式");
        clear.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("清除已学习格式？")
                .setMessage("逐曲手动修正不会被清除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> {
                    draft = draft.buildUpon().clearLearnedRules().build();
                    rebuildLearnedRules();
                    rebuildCurrentSong();
                })
                .show());
        learnedRules.addView(clear, matchWrap());
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
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        LyricContentCleanupRepository.save(preferences, config);
        Intent intent = new Intent(LyricUiSettings.ACTION_CONTENT_CLEANUP_CHANGED)
                .setPackage("com.android.systemui")
                .putExtra(LyricUiSettings.EXTRA_CONTENT_CLEANUP_CONFIG, encoded);
        sendBroadcast(intent);
        draft = config;
        selectedFirstFormalIndex = findStoredFirstFormalIndex();
        rebuildLearnedRules();
        rebuildCurrentSong();
        Toast.makeText(this, "歌词开头信息清理已应用", Toast.LENGTH_SHORT).show();
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
                : "正式歌词候选";
        return time + "  " + decision.line.text + "\n" + label;
    }

    private static String displayRule(LyricContentCleanupConfig.LearnedRule rule) {
        if (rule == null) return "";
        return (rule.type == LyricContentCleanupConfig.LearnedType.PREFIX
                ? "以“" + rule.value + "”开头"
                : "整行为“" + rule.value + "”");
    }

    private static String formatTime(long timeMillis) {
        long totalSeconds = Math.max(0L, timeMillis) / 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(14), dp(10), dp(14), dp(12));
        view.setBackgroundColor(CARD);
        return view;
    }

    private TextView section(String title) {
        TextView view = text(title, 13, MUTED);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(dp(4), dp(4), 0, dp(8));
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
}
