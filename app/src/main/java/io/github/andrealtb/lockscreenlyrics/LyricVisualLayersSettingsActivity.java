package io.github.andrealtb.lockscreenlyrics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

/** Independent owner for lyric brightness levels and RecyclerView edge fading. */
public final class LyricVisualLayersSettingsActivity extends SettingsBaseActivity {
    private static final String TAG = "LockscreenLyrics";
    private static final String STATE_DRAFT_CONFIG = "visual_layers_draft_config";
    private static final long APPLY_ACK_TIMEOUT_MS = 2_500L;

    private SharedPreferences preferences;
    private LyricUiConfig draft;
    private LyricUiConfig savedConfig;
    private boolean binding;
    private boolean verticalFadeLengthTouched;
    private long pendingSettingsRevision = -1L;

    private SliderControl activeOpacity;
    private SliderControl currentUnrevealedOpacity;
    private SliderControl activeTranslationOpacity;
    private SliderControl activeTranslationProgressOpacity;
    private SliderControl inactiveOpacity;
    private MaterialSwitch inactiveTranslationFollowsMain;
    private SliderControl inactiveTranslationOpacity;
    private View inactiveTranslationOpacityRow;
    private MaterialSwitch verticalFadeEnabled;
    private SliderControl verticalFadeLength;
    private View verticalFadeLengthRow;
    private MaterialSwitch inactiveRowFadeEnabled;
    private SliderControl inactiveRowFadeOpacity;
    private View inactiveRowFadeOpacityRow;
    private LyricVisualLayersPreviewView preview;
    private TextView dirtyStatus;
    private Button saveButton;
    private OnBackPressedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE);
        savedConfig = LyricUiConfigRepository.load(preferences);
        draft = savedConfig;
        if (savedInstanceState != null) {
            LyricUiConfig restored = LyricUiConfigRepository.decodeSnapshot(
                    savedInstanceState.getBundle(STATE_DRAFT_CONFIG),
                    draft);
            if (restored != null) draft = restored;
        }

        setTitle(R.string.visual_layers_settings_title);
        setContentView(createContent());
        bind(draft);
        installListeners();

        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscardAndExit();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        updateDirtyState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (outState != null && activeOpacity != null) {
            draft = readDraft();
            outState.putBundle(
                    STATE_DRAFT_CONFIG,
                    LyricUiConfigRepository.putSnapshot(new Bundle(), draft));
        }
        super.onSaveInstanceState(outState);
    }

    private View createContent() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(settingsBackgroundColor());
        page.addView(settingsAppBar(
                getString(R.string.visual_layers_settings_title),
                null,
                this::confirmDiscardAndExit), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                settingsActionBarHeight()));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int screenPadding = settingsScreenPadding();
        body.setPadding(0, screenPadding, 0, 0);
        body.setBackgroundColor(settingsBackgroundColor());
        body.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);

        LinearLayout previewCard = card();
        previewCard.addView(section(
                R.drawable.ic_sec_color,
                getString(R.string.visual_preview_title),
                "LIVE"));
        preview = new LyricVisualLayersPreviewView(this);
        preview.setContentDescription(getString(R.string.visual_preview_content_description));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(196));
        previewParams.leftMargin = dp(8);
        previewParams.rightMargin = dp(8);
        previewParams.bottomMargin = dp(8);
        previewCard.addView(preview, previewParams);
        LinearLayout.LayoutParams previewCardParams = marginBottom(dp(12));
        previewCardParams.leftMargin = screenPadding;
        previewCardParams.rightMargin = screenPadding;
        body.addView(previewCard, previewCardParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(
                screenPadding,
                0,
                screenPadding,
                settingsScreenBottomPadding() + dp(72));
        installSideInsets(controls);

        TextView description = text(
                getString(R.string.visual_layers_description),
                12.5f,
                getColor(R.color.settings_text_muted));
        description.setPadding(dp(4), 0, dp(4), dp(10));
        controls.addView(description, matchWrap());

        LinearLayout brightnessCard = card();
        brightnessCard.addView(section(
                R.drawable.ic_sec_color,
                getString(R.string.visual_section_brightness),
                "ALPHA"));
        activeOpacity = sliderControl(
                getString(R.string.visual_active_opacity), 50f, 100f, 1f);
        brightnessCard.addView(activeOpacity.row, matchWrap());
        addCardDivider(brightnessCard);
        currentUnrevealedOpacity = sliderControl(
                getString(R.string.visual_current_unrevealed_opacity), 20f, 100f, 1f);
        brightnessCard.addView(currentUnrevealedOpacity.row, matchWrap());
        addCardDivider(brightnessCard);
        activeTranslationOpacity = sliderControl(
                getString(R.string.visual_active_translation_opacity), 20f, 100f, 1f);
        brightnessCard.addView(activeTranslationOpacity.row, matchWrap());
        addCardDivider(brightnessCard);
        activeTranslationProgressOpacity = sliderControl(
                getString(R.string.visual_active_translation_progress_opacity),
                20f,
                100f,
                1f);
        brightnessCard.addView(activeTranslationProgressOpacity.row, matchWrap());
        addCardDivider(brightnessCard);
        inactiveOpacity = sliderControl(
                getString(R.string.visual_inactive_main_opacity), 30f, 100f, 1f);
        brightnessCard.addView(inactiveOpacity.row, matchWrap());
        addCardDivider(brightnessCard);
        inactiveTranslationFollowsMain = toggle(
                getString(R.string.visual_inactive_translation_follows_main),
                true);
        brightnessCard.addView(inactiveTranslationFollowsMain, matchWrap());
        inactiveTranslationOpacity = sliderControl(
                getString(R.string.visual_inactive_translation_opacity),
                20f,
                100f,
                1f);
        inactiveTranslationOpacityRow = conditionalRow(inactiveTranslationOpacity.row);
        brightnessCard.addView(inactiveTranslationOpacityRow, matchWrap());
        controls.addView(brightnessCard, marginBottom(dp(12)));

        LinearLayout edgeCard = card();
        edgeCard.addView(section(
                R.drawable.ic_sec_color,
                getString(R.string.visual_section_edges),
                "EDGE"));
        verticalFadeEnabled = toggle(getString(R.string.visual_vertical_fade_enabled), true);
        edgeCard.addView(verticalFadeEnabled, matchWrap());
        verticalFadeLength = sliderControl(
                getString(R.string.visual_vertical_fade_length), 0f, 120f, 1f);
        verticalFadeLengthRow = conditionalRow(verticalFadeLength.row);
        edgeCard.addView(verticalFadeLengthRow, matchWrap());
        addCardDivider(edgeCard);
        inactiveRowFadeEnabled = toggle(
                getString(R.string.visual_inactive_row_fade_enabled),
                false);
        edgeCard.addView(inactiveRowFadeEnabled, matchWrap());
        inactiveRowFadeOpacity = sliderControl(
                getString(R.string.visual_inactive_row_fade_opacity),
                50f,
                100f,
                1f);
        inactiveRowFadeOpacityRow = conditionalRow(inactiveRowFadeOpacity.row);
        edgeCard.addView(inactiveRowFadeOpacityRow, matchWrap());
        TextView edgeHelp = text(
                getString(R.string.visual_edge_help),
                10.5f,
                getColor(R.color.settings_text_muted));
        edgeHelp.setPadding(dp(17), dp(5), dp(17), dp(12));
        edgeCard.addView(edgeHelp, matchWrap());
        controls.addView(edgeCard, marginBottom(dp(12)));

        dirtyStatus = text("", 10.5f, getColor(R.color.settings_text_muted));
        dirtyStatus.setGravity(Gravity.CENTER);
        dirtyStatus.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        dirtyStatus.setPadding(dp(8), dp(4), dp(8), dp(8));
        controls.addView(dirtyStatus, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(controls);

        saveButton = button(getString(R.string.action_apply_save));
        styleGoldButton(saveButton);
        removeButtonShadow(saveButton);
        saveButton.setOnClickListener(view -> save());

        FrameLayout controlsStage = new FrameLayout(this);
        controlsStage.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(65),
                Gravity.BOTTOM);
        controlsStage.addView(settingsBottomAction(saveButton), bottomParams);
        body.addView(controlsStage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        page.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return page;
    }

    private void installSideInsets(View body) {
        int baseLeft = body.getPaddingLeft();
        int baseTop = body.getPaddingTop();
        int baseRight = body.getPaddingRight();
        int baseBottom = body.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(body, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    baseLeft + insets.left,
                    baseTop,
                    baseRight + insets.right,
                    baseBottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(body);
    }

    private SliderControl sliderControl(String label, float min, float max, float step) {
        Slider slider = new Slider(this);
        slider.setValueTo(max);
        slider.setValueFrom(min);
        slider.setStepSize(step);
        slider.setValue(min);
        slider.setTickVisible(false);
        slider.setLabelBehavior(LabelFormatter.LABEL_GONE);
        slider.setTrackHeight(dp(4));
        slider.setTrackActiveTintList(ColorStateList.valueOf(getColor(R.color.settings_primary)));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(0x21344455));
        slider.setThumbRadius(dp(8));
        slider.setThumbTintList(ColorStateList.valueOf(getColor(R.color.settings_primary)));
        slider.setThumbStrokeColor(ColorStateList.valueOf(Color.WHITE));
        slider.setThumbStrokeWidth(dp(2.5f));
        slider.setHaloRadius(dp(16));
        slider.setHaloTintList(ColorStateList.valueOf(0x3DF2C14E));
        slider.setContentDescription(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(17), dp(7), dp(12), dp(3));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 12.5f, settingsTextColor());
        title.setIncludeFontPadding(false);
        TextView value = text("", 10.5f, 0xFF9A6A12);
        value.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        header.addView(value, new LinearLayout.LayoutParams(dp(100), dp(28)));
        row.addView(header, matchWrap());
        row.addView(slider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)));
        return new SliderControl(row, slider, value);
    }

    private View conditionalRow(View row) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        addCardDivider(container);
        container.addView(row, matchWrap());
        return container;
    }

    private void installListeners() {
        SliderControl[] controls = {
                activeOpacity,
                currentUnrevealedOpacity,
                activeTranslationOpacity,
                activeTranslationProgressOpacity,
                inactiveOpacity,
                inactiveTranslationOpacity,
                inactiveRowFadeOpacity
        };
        for (SliderControl control : controls) {
            control.slider.addOnChangeListener((slider, value, fromUser) -> onDraftChanged());
        }
        verticalFadeLength.slider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) verticalFadeLengthTouched = true;
            onDraftChanged();
        });
        inactiveTranslationFollowsMain.setOnCheckedChangeListener(
                (buttonView, checked) -> onDraftChanged());
        verticalFadeEnabled.setOnCheckedChangeListener(
                (buttonView, checked) -> onDraftChanged());
        inactiveRowFadeEnabled.setOnCheckedChangeListener(
                (buttonView, checked) -> onDraftChanged());
    }

    private void onDraftChanged() {
        if (binding) return;
        draft = readDraft();
        // Rebind immediately so active > progress > translation and unrevealed <= active are
        // visible constraints, not silent save-time corrections.
        bind(draft);
    }

    private LyricUiConfig readDraft() {
        return draft.buildUpon()
                .activeOpacityPercent(Math.round(activeOpacity.slider.getValue()))
                .currentUnrevealedOpacityPercent(
                        Math.round(currentUnrevealedOpacity.slider.getValue()))
                .activeTranslationOpacityPercent(
                        Math.round(activeTranslationOpacity.slider.getValue()))
                .activeTranslationProgressOpacityPercent(
                        Math.round(activeTranslationProgressOpacity.slider.getValue()))
                .inactiveOpacityPercent(Math.round(inactiveOpacity.slider.getValue()))
                .inactiveTranslationFollowsMain(inactiveTranslationFollowsMain.isChecked())
                .inactiveTranslationOpacityPercent(
                        Math.round(inactiveTranslationOpacity.slider.getValue()))
                .verticalFadeEnabled(verticalFadeEnabled.isChecked())
                .verticalFadeLengthTenthsDp(verticalFadeLengthTouched
                        ? Math.round(verticalFadeLength.slider.getValue()) * 10
                        : draft.verticalFadeLengthTenthsDp)
                .inactiveRowFadeEnabled(inactiveRowFadeEnabled.isChecked())
                .inactiveRowFadePercent(Math.round(inactiveRowFadeOpacity.slider.getValue()))
                .build();
    }

    private void bind(LyricUiConfig config) {
        draft = config == null ? LyricUiConfig.defaults() : config;
        binding = true;
        setSliderValue(activeOpacity.slider, draft.activeOpacityPercent);
        setSliderValue(currentUnrevealedOpacity.slider, draft.currentUnrevealedOpacityPercent);
        setSliderValue(activeTranslationOpacity.slider, draft.activeTranslationOpacityPercent);
        setSliderValue(
                activeTranslationProgressOpacity.slider,
                draft.activeTranslationProgressOpacityPercent);
        setSliderValue(inactiveOpacity.slider, draft.inactiveOpacityPercent);
        inactiveTranslationFollowsMain.setChecked(draft.inactiveTranslationFollowsMain);
        setSliderValue(
                inactiveTranslationOpacity.slider,
                draft.inactiveTranslationOpacityPercent);
        verticalFadeEnabled.setChecked(draft.verticalFadeEnabled);
        setSliderValue(
                verticalFadeLength.slider,
                Math.round(draft.verticalFadeLengthTenthsDp / 10f));
        inactiveRowFadeEnabled.setChecked(draft.inactiveRowFadeEnabled);
        setSliderValue(inactiveRowFadeOpacity.slider, draft.inactiveRowFadePercent);
        binding = false;
        updateConditionalRows();
        updateValueLabels();
        preview.bind(draft);
        updateDirtyState();
    }

    private void setSliderValue(Slider slider, float value) {
        float bounded = Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), value));
        if (Math.abs(slider.getValue() - bounded) > 0.0001f) {
            slider.setValue(bounded);
        }
    }

    private void updateConditionalRows() {
        inactiveTranslationOpacityRow.setVisibility(
                inactiveTranslationFollowsMain.isChecked() ? View.GONE : View.VISIBLE);
        verticalFadeLengthRow.setVisibility(
                verticalFadeEnabled.isChecked() ? View.VISIBLE : View.GONE);
        inactiveRowFadeOpacityRow.setVisibility(
                inactiveRowFadeEnabled.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void updateValueLabels() {
        activeOpacity.value.setText(percent(activeOpacity.slider.getValue()));
        currentUnrevealedOpacity.value.setText(percent(currentUnrevealedOpacity.slider.getValue()));
        activeTranslationOpacity.value.setText(percent(activeTranslationOpacity.slider.getValue()));
        activeTranslationProgressOpacity.value.setText(
                percent(activeTranslationProgressOpacity.slider.getValue()));
        inactiveOpacity.value.setText(getString(
                R.string.visual_value_effective,
                Math.round(inactiveOpacity.slider.getValue()),
                LyricVisualAlphaPolicy.steadyInactiveMainPercent(draft)));
        inactiveTranslationOpacity.value.setText(getString(
                R.string.visual_value_effective,
                Math.round(inactiveTranslationOpacity.slider.getValue()),
                LyricVisualAlphaPolicy.steadyInactiveTranslationPercent(draft)));
        verticalFadeLength.value.setText(getString(
                R.string.visual_value_dp,
                Math.round(verticalFadeLength.slider.getValue())));
        inactiveRowFadeOpacity.value.setText(percent(inactiveRowFadeOpacity.slider.getValue()));
    }

    private String percent(float value) {
        return getString(R.string.visual_value_percent, Math.round(value));
    }

    private void updateDirtyState() {
        boolean dirty = savedConfig == null || !draft.equals(savedConfig);
        if (dirtyStatus != null) {
            dirtyStatus.setText(getString(
                    dirty ? R.string.visual_status_unsaved : R.string.visual_status_saved));
            dirtyStatus.setTextColor(dirty
                    ? 0xFF9A6A12
                    : getColor(R.color.settings_text_muted));
        }
        if (saveButton != null) saveButton.setEnabled(dirty);
        if (backCallback != null) backCallback.setEnabled(true);
    }

    private void confirmDiscardAndExit() {
        draft = readDraft();
        if (savedConfig != null && draft.equals(savedConfig)) {
            finish();
            return;
        }
        showSettingsDiscardDialog(this::finish);
    }

    private void save() {
        LyricUiConfig config = readDraft();
        LyricUiConfigRepository.save(preferences, config);
        long revision = LyricUiSettings.newSettingsRevision();
        pendingSettingsRevision = revision;
        Intent intent = LyricUiConfigRepository.putSnapshot(
                new Intent(LyricUiSettings.ACTION_STYLE_CHANGED)
                        .setPackage("com.android.systemui")
                        .putExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, revision)
                        .putExtra(
                                LyricUiSettings.EXTRA_SETTINGS_SOURCE,
                                LyricUiSettings.SOURCE_VISUAL_LAYERS)
                        .putExtra(
                                LyricUiSettings.EXTRA_RESULT_RECEIVER,
                                new VisualLayersApplyResultReceiver(revision)),
                config);
        sendBroadcast(intent);
        savedConfig = config;
        verticalFadeLengthTouched = false;
        bind(config);
        logSettingsEvent(
                "settings-send",
                "Sent lyric visual layer settings"
                        + " | source=" + LyricUiSettings.SOURCE_VISUAL_LAYERS
                        + ", revision=" + revision
                        + ", activeOpacity=" + config.activeOpacityPercent
                        + ", inactiveOpacity=" + config.inactiveOpacityPercent
                        + ", verticalFade=" + config.verticalFadeEnabled
                        + ", verticalFadeDp10=" + config.verticalFadeLengthTenthsDp
                        + ", rowFade=" + config.inactiveRowFadeEnabled);
        Toast.makeText(this, R.string.snack_save_sent, Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(() -> {
            if (pendingSettingsRevision != revision) return;
            pendingSettingsRevision = -1L;
            Toast.makeText(this, R.string.snack_save_pending, Toast.LENGTH_LONG).show();
        }, APPLY_ACK_TIMEOUT_MS);
    }

    /** Named ResultReceiver: anonymous numbering shifts across builds and crashes SystemUI. */
    private final class VisualLayersApplyResultReceiver extends ResultReceiver {
        private final long revision;

        VisualLayersApplyResultReceiver(long revision) {
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
                    "Received SystemUI visual layer settings acknowledgement"
                            + " | source=" + LyricUiSettings.SOURCE_VISUAL_LAYERS
                            + ", revision=" + revision
                            + ", process=" + process
                            + ", applied=" + applied
                            + ", reason=" + reason);
            Toast.makeText(
                    LyricVisualLayersSettingsActivity.this,
                    applied
                            ? getString(R.string.snack_save_applied)
                            : getString(R.string.snack_save_rejected, reason),
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

    private static final class SliderControl {
        final View row;
        final Slider slider;
        final TextView value;

        SliderControl(View row, Slider slider, TextView value) {
            this.row = row;
            this.slider = slider;
            this.value = value;
        }
    }
}
