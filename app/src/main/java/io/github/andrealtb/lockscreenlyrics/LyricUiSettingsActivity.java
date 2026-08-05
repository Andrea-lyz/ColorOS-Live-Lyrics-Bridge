package io.github.andrealtb.lockscreenlyrics;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.ResultReceiver;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LyricUiSettingsActivity extends SettingsBaseActivity {
    private static final String TAG = "LockscreenLyrics";
    private static final String STATE_DRAFT_CONFIG = "draft_config";
    private static final long PREVIEW_SCROLL_LAYER_RELEASE_DELAY_MS = 160L;
    private static final int MATERIAL_SLIDER_LABEL_BEHAVIOR_GONE = 2;
    private static final long SYSTEM_UI_RESTART_ACK_TIMEOUT_MS = 2_500L;
    private static final long MODULE_STATUS_QUERY_TIMEOUT_MS = 2_500L;
    private static final long SNACK_DURATION_SHORT_MS = 2_000L;
    private static final long SNACK_DURATION_LONG_MS = 3_500L;
    private static final int BOTTOM_ACTION_BAR_CLEARANCE_DP = 84;
    private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9A-Fa-f]{6}");

    private SharedPreferences preferences;
    private boolean binding;
    private LyricUiConfig draft;
    private Spinner presetSpinner;
    private final List<SettingsPresetCard> presetCards = new ArrayList<>();
    private View activationDot;
    private TextView activationText;
    private ObjectAnimator activationPulse;
    private boolean moduleStatusQueryPending;
    private View bottomActionBar;
    private TextView unsavedBadge;
    private LyricUiConfig savedConfig;
    private boolean dirtyState;
    private OnBackPressedCallback backCallback;
    private final List<PaletteRow> paletteRows = new ArrayList<>();
    private FrameLayout contentRoot;
    private View snackView;
    private TextView snackMessage;
    private String pendingSnackMessage;
    private long pendingSnackDuration;
    private final Runnable snackDismiss = () -> {
        if (snackView != null) {
            snackView.animate().cancel();
            snackView.animate()
                    .alpha(0f)
                    .translationY(dp(14))
                    .setDuration(220L)
                    .withEndAction(() -> {
                        snackView.setVisibility(View.GONE);
                        if (pendingSnackMessage != null) {
                            String message = pendingSnackMessage;
                            long duration = pendingSnackDuration;
                            pendingSnackMessage = null;
                            displaySnack(message, duration);
                        }
                    })
                    .start();
        }
    };
    private boolean keyboardVisible;
    private boolean keyboardCloseShowScheduled;
    private int keyboardLastContentHeight = -1;
    private int keyboardSettledFrames;
    private final Runnable showBottomBarAfterKeyboardClose = () -> {
        keyboardCloseShowScheduled = false;
        if (bottomActionBar == null || keyboardVisible) return;
        bottomActionBar.setVisibility(View.VISIBLE);
    };
    private TextView customPresetLabel;
    private Slider opacity;
    private MaterialSwitch blurEnabled;
    private Slider blurRadius;
    private View blurRadiusRow;
    private MaterialSwitch scaleEnabled;
    private Slider inactiveScale;
    private View inactiveScaleRow;
    private MaterialSwitch glowEnabled;
    private Slider glowIntensity;
    private Slider glowRadius;
    private final PaletteTarget primaryColor = new PaletteTarget("#FF9AA8");
    private final PaletteTarget glowColor = new PaletteTarget("#FF5D73");
    private SegmentedControl motionMode;
    private MaterialSwitch passiveVerticalPan;
    private MaterialSwitch translationMarquee;
    private MaterialButton refreshRate;
    private int refreshRateSelection;
    private int[] refreshRateValues;
    private MaterialSwitch lineTimedProgress;
    private MaterialSwitch translationProgress;
    private MaterialSwitch screenTimeout;
    private EditText screenTimeoutSeconds;
    private View screenTimeoutSecondsRow;
    private Slider mainFontSize;
    private Slider translationFontRatio;
    private SegmentedControl fontWeight;
    private SegmentedControl alignment;
    private Slider lineSpacing;
    private Slider wrappedLineSpacing;
    private SettingsPreviewView previewView;
    private FrameLayout previewAnchor;
    private boolean floatingPreviewUpdatePosted;
    private View scrollCachedPreview;
    private final Runnable releaseScrollCachedPreview = this::releaseScrollCachedPreviewLayer;
    private final int[] floatingPreviewRootLocation = new int[2];
    private final int[] floatingPreviewAnchorLocation = new int[2];
    private TopUiBoundary cachedTopUiBoundary;
    private int cachedTopUiBoundaryRootTop = Integer.MIN_VALUE;
    private View actionBarBoundaryView;
    private String lastFloatingPreviewGeometryLog = "";
    private boolean draftListenersReady;
    private ValueAnimator presetAnimator;
    private boolean presetTransitionActive;
    private boolean manualSliderDragActive;
    private LyricUiPreset displayedPreset;
    private int ignoredPresetSelection = -1;
    private long pendingSettingsRevision = -1L;
    private long systemUiRestartRequestSequence;
    private long pendingSystemUiRestartRequestId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSettingsWindowRefreshRate(getWindow());
        preferences = getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE);
        draft = LyricUiConfigRepository.load(preferences);
        savedConfig = draft;
        if (savedInstanceState != null) {
            LyricUiConfig restored = LyricUiConfigRepository.decodeSnapshot(
                    savedInstanceState.getBundle(STATE_DRAFT_CONFIG),
                    draft);
            if (restored != null) draft = restored;
        }
        View content = createContent();
        setContentView(content);
        bind(draft);
        requestModuleStatus();
        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscardAndExit();
            }
        };
        backCallback.setEnabled(false);
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        getWindow().getDecorView().post(() -> {
            installDraftListeners();
            getWindow().getDecorView().post(() -> draftListenersReady = true);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (outState != null && presetSpinner != null) {
            draft = readDraft();
            outState.putBundle(
                    STATE_DRAFT_CONFIG,
                    LyricUiConfigRepository.putSnapshot(new Bundle(), draft));
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        // A drag that is interrupted by pausing never fires onStopTrackingTouch; reset the
        // flag so preset re-detection cannot stay suppressed after the activity returns.
        manualSliderDragActive = false;
        releaseScrollCachedPreviewLayer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (presetAnimator != null) {
            presetAnimator.cancel();
            presetAnimator = null;
        }
        if (activationPulse != null) {
            activationPulse.cancel();
            activationPulse = null;
        }
        pendingSystemUiRestartRequestId = -1L;
        releaseScrollCachedPreviewLayer();
        super.onDestroy();
    }

    private void configureSettingsWindowRefreshRate(Window window) {
        @SuppressWarnings("deprecation")
        Display display = getWindowManager().getDefaultDisplay();
        Display.Mode[] modes = display == null ? null : display.getSupportedModes();
        float[] supportedRates = modes == null ? new float[0] : new float[modes.length];
        for (int i = 0; i < supportedRates.length; i++) {
            supportedRates[i] = modes[i].getRefreshRate();
        }
        float preferredRate = SettingsWindowRefreshRatePolicy.choosePreferredRate(supportedRates);
        if (preferredRate <= 0f) return;

        WindowManager.LayoutParams params = window.getAttributes();
        // Request the panel's 120 Hz mode without pinning a display mode. On API 35+, the
        // balanced hint lets LTPO panels lower their physical scan rate while content is idle,
        // and disabling touch boost avoids a separate 60/120 Hz vote during a gesture.
        params.preferredDisplayModeId = 0;
        params.preferredRefreshRate = preferredRate;
        if (Build.VERSION.SDK_INT >= 35) {
            params.setFrameRateBoostOnTouchEnabled(false);
            params.setFrameRatePowerSavingsBalanced(true);
        }
        window.setAttributes(params);
    }

    private View createContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int screenPadding = settingsScreenPadding();
        content.setPadding(screenPadding, screenPadding, screenPadding,
                settingsScreenBottomPadding() + dp(BOTTOM_ACTION_BAR_CLEARANCE_DP));
        content.setBackgroundColor(settingsBackgroundColor());
        disableAutofill(content);
        installSettingsInsets(content);

        content.addView(statusHeader(), marginBottom(dp(12)));
        LinearLayout preview = card();
        preview.setPadding(dp(12), dp(6), dp(12), dp(6));
        previewView = new SettingsPreviewView(this);
        previewView.setPadding(dp(16), dp(8), dp(16), dp(8));
        previewView.setElevation(dp(4));
        preview.addView(previewView, matchWrap());
        previewAnchor = new FrameLayout(this);
        previewAnchor.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        content.addView(previewAnchor, marginBottom(dp(12)));

        LinearLayout presetCard = card();
        presetCard.addView(section(
                R.drawable.ic_sec_presets,
                getString(R.string.settings_section_presets),
                "PRESET"));
        LinearLayout presetGrid = new LinearLayout(this);
        presetGrid.setOrientation(LinearLayout.HORIZONTAL);
        presetGrid.setGravity(Gravity.CENTER_VERTICAL);
        presetGrid.setClipChildren(false);
        presetGrid.setClipToPadding(false);
        presetGrid.setPadding(dp(12), dp(10), dp(12), dp(13));
        for (LyricUiPreset preset : new LyricUiPreset[]{
                LyricUiPreset.DEFAULT,
                LyricUiPreset.SOFT,
                LyricUiPreset.VIVID,
                LyricUiPreset.MINIMAL}) {
            SettingsPresetCard card = new SettingsPresetCard(this, preset);
            card.setOnClickListener(view -> {
                if (!binding && draftListenersReady) {
                    animatePresetSelection(preset);
                }
            });
            presetCards.add(card);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, dp(82), 1f);
            if (preset != LyricUiPreset.MINIMAL) cardParams.rightMargin = dp(8);
            presetGrid.addView(card, cardParams);
        }
        presetCard.addView(presetGrid, matchWrap());
        customPresetLabel = text(
                getString(R.string.preset_custom_label),
                11,
                0xFF177286);
        customPresetLabel.setVisibility(View.GONE);
        customPresetLabel.setGravity(Gravity.CENTER);
        customPresetLabel.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        customPresetLabel.setLetterSpacing(0.1f);
        customPresetLabel.setPadding(dp(14), dp(5), dp(14), dp(5));
        GradientDrawable customBackground = new GradientDrawable();
        customBackground.setColor(0x17177286);
        customBackground.setCornerRadius(dp(99));
        customBackground.setStroke(dp(1), 0x73177286, dp(4), dp(3));
        customPresetLabel.setBackground(customBackground);
        LinearLayout.LayoutParams customParams = matchWrap();
        customParams.leftMargin = dp(12);
        customParams.rightMargin = dp(12);
        customParams.bottomMargin = dp(12);
        presetCard.addView(customPresetLabel, customParams);
        presetSpinner = spinner(new String[]{
                getString(R.string.preset_default),
                getString(R.string.preset_soft),
                getString(R.string.preset_vivid),
                getString(R.string.preset_minimal),
                getString(R.string.preset_custom)});
        presetSpinner.setVisibility(View.GONE);
        presetCard.addView(presetSpinner, new LinearLayout.LayoutParams(1, 1));
        content.addView(presetCard, marginBottom(dp(12)));

        LinearLayout colorCard = card();
        colorCard.addView(section(
                R.drawable.ic_sec_color,
                getString(R.string.settings_section_color),
                "COLOR"));
        colorCard.addView(colorPaletteRow(
                getString(R.string.setting_primary_color),
                SettingsColorPalette.PRIMARY,
                primaryColor,
                "#FF9AA8"));
        addCardDivider(colorCard);
        colorCard.addView(colorPaletteRow(
                getString(R.string.setting_glow_color),
                SettingsColorPalette.GLOW,
                glowColor,
                "#FF5D73"));
        addCardDivider(colorCard);
        glowEnabled = toggle(getString(R.string.setting_glow), true);
        glowEnabled.setVisibility(View.GONE);
        glowIntensity = materialSeek(0, 100);
        colorCard.addView(labeledMaterialSeek(
                getString(R.string.setting_glow_intensity),
                glowIntensity,
                "%"));
        addCardDivider(colorCard);
        glowRadius = materialSeek(10, 24);
        colorCard.addView(labeledMaterialSeek(
                getString(R.string.setting_glow_radius),
                glowRadius,
                "%"));
        addCardDivider(colorCard);
        blurEnabled = toggle(getString(R.string.setting_blur), false);
        colorCard.addView(blurEnabled);
        blurRadius = materialSeek(0, 16);
        blurRadiusRow = conditionalCardRow(labeledMaterialSeek(
                getString(R.string.setting_blur_radius),
                blurRadius,
                " × 0.5px"));
        colorCard.addView(blurRadiusRow);
        addCardDivider(colorCard);
        opacity = materialSeek(30, 100);
        colorCard.addView(labeledMaterialSeek(
                getString(R.string.setting_inactive_opacity),
                opacity,
                "%"));
        addCardDivider(colorCard);
        scaleEnabled = toggle(getString(R.string.setting_scroll_scale), false);
        colorCard.addView(scaleEnabled);
        inactiveScale = materialSeek(75, 100);
        inactiveScaleRow = conditionalCardRow(labeledMaterialSeek(
                getString(R.string.setting_inactive_scale),
                inactiveScale,
                "%"));
        colorCard.addView(inactiveScaleRow);
        content.addView(colorCard, marginBottom(dp(12)));

        LinearLayout typography = card();
        typography.addView(section(
                R.drawable.ic_sec_typography,
                getString(R.string.settings_section_typography),
                "TYPE"));
        mainFontSize = materialSeek(18, 28);
        typography.addView(labeledMaterialSeek(
                getString(R.string.setting_font_size),
                mainFontSize,
                " sp"));
        addCardDivider(typography);
        TextView mainFontSizeHint = text(
                getString(R.string.font_size_hint),
                10.5f,
                0x99000000);
        mainFontSizeHint.setPadding(dp(17), 0, dp(17), dp(8));
        typography.addView(mainFontSizeHint, matchWrap());
        addCardDivider(typography);
        translationFontRatio = materialSeek(55, 75);
        typography.addView(labeledMaterialSeek(
                getString(R.string.setting_translation_ratio),
                translationFontRatio,
                "%"));
        addCardDivider(typography);
        fontWeight = new SegmentedControl(new String[]{
                getString(R.string.weight_system),
                getString(R.string.weight_regular),
                getString(R.string.weight_medium),
                getString(R.string.weight_bold)});
        typography.addView(labeledSegmented(
                getString(R.string.setting_font_weight),
                fontWeight));
        addCardDivider(typography);
        alignment = new SegmentedControl(
                new String[]{
                        getString(R.string.align_start),
                        getString(R.string.align_center),
                        getString(R.string.align_end)},
                new int[]{
                        R.drawable.ic_align_start,
                        R.drawable.ic_align_center,
                        R.drawable.ic_align_end});
        typography.addView(labeledSegmented(
                getString(R.string.setting_alignment),
                alignment));
        addCardDivider(typography);
        lineSpacing = materialSeek(-10, 40);
        typography.addView(labeledMaterialHalfDpSeek(
                getString(R.string.setting_line_spacing), lineSpacing));
        addCardDivider(typography);
        wrappedLineSpacing = materialSeek(-2, 16);
        typography.addView(labeledMaterialHalfDpSeek(
                getString(R.string.setting_wrapped_line_spacing), wrappedLineSpacing));
        content.addView(typography, marginBottom(dp(12)));

        LinearLayout motion = card();
        motion.addView(section(
                R.drawable.ic_sec_motion,
                getString(R.string.settings_section_motion_refresh),
                "MOTION"));
        motionMode = new SegmentedControl(new String[]{
                getString(R.string.motion_standard),
                getString(R.string.motion_reduced),
                getString(R.string.motion_off)});
        motion.addView(labeledSegmented(
                getString(R.string.setting_motion),
                motionMode));
        addCardDivider(motion);
        passiveVerticalPan = toggle(getString(R.string.setting_passive_pan), true);
        translationMarquee = toggle(getString(R.string.setting_translation_marquee), true);
        motion.addView(passiveVerticalPan);
        addCardDivider(motion);
        motion.addView(translationMarquee);
        addCardDivider(motion);
        buildRefreshRateOptions();
        refreshRate = refreshDropdownButton();
        motion.addView(refreshLimitRow(refreshRate), matchWrap());
        content.addView(motion, marginBottom(dp(12)));

        LinearLayout compatibility = card();
        compatibility.addView(section(
                R.drawable.ic_sec_compat,
                getString(R.string.settings_section_compat),
                "COMPAT"));
        compatibility.addView(linkRow(
                R.drawable.ic_translation,
                getString(R.string.link_player_translation),
                getString(R.string.link_player_translation_sub),
                () -> startActivity(new Intent(this, PlayerTranslationSettingsActivity.class))));
        addCardDivider(compatibility);
        compatibility.addView(linkRow(
                R.drawable.ic_cleanup,
                getString(R.string.link_opening_cleanup),
                getString(R.string.link_opening_cleanup_sub),
                () -> startActivity(new Intent(this, LyricOpeningCleanupSettingsActivity.class))));
        addCardDivider(compatibility);
        lineTimedProgress = toggle(getString(R.string.setting_line_progress), false);
        translationProgress = toggle(getString(R.string.setting_translation_progress), false);
        screenTimeout = toggle(getString(R.string.setting_screen_timeout), true);
        screenTimeoutSeconds = numberInput(getString(R.string.setting_screen_timeout_seconds_hint));
        compatibility.addView(lineTimedProgress);
        addCardDivider(compatibility);
        compatibility.addView(translationProgress);
        addCardDivider(compatibility);
        compatibility.addView(screenTimeout);
        screenTimeoutSecondsRow = conditionalCardRow(numberInputRow(
                getString(R.string.setting_screen_timeout_seconds_label),
                screenTimeoutSeconds));
        compatibility.addView(screenTimeoutSecondsRow, matchWrap());
        content.addView(compatibility, marginBottom(dp(12)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        installKeyboardFocusRecovery(scroll, screenTimeoutSeconds);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(settingsBackgroundColor());
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        bottomActionBar = createBottomActionBar();
        root.addView(bottomActionBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));
        installKeyboardAvoidance(scroll, root, previewAnchor, preview);
        installFloatingPreview(root, scroll, previewAnchor, preview);
        contentRoot = root;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(settingsBackgroundColor());
        View appBar = settingsAppBar(
                getString(R.string.lyric_ui_settings_title),
                getString(R.string.settings_appbar_version, BuildConfig.VERSION_NAME),
                () -> getOnBackPressedDispatcher().onBackPressed());
        actionBarBoundaryView = appBar;
        page.addView(appBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                settingsActionBarHeight()));
        page.addView(root, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return page;
    }

    /** Mockup dark snack above the fixed bottom bar; replaces Toast on the main page. */
    private void showSnack(String message, long durationMs) {
        if (contentRoot == null) return;
        if (snackView != null && snackView.getVisibility() == View.VISIBLE) {
            // Toast-like sequencing: SystemUI usually acknowledges within a few hundred ms;
            // queue the follow-up so the "sent" message stays visible for its full duration.
            pendingSnackMessage = message;
            pendingSnackDuration = durationMs;
            return;
        }
        displaySnack(message, durationMs);
    }

    private void displaySnack(String message, long durationMs) {
        if (snackView == null) {
            LinearLayout snackContent = new LinearLayout(this);
            snackContent.setOrientation(LinearLayout.HORIZONTAL);
            snackContent.setGravity(Gravity.CENTER_VERTICAL);
            snackContent.setPadding(dp(16), dp(12), dp(16), dp(12));
            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check);
            check.setColorFilter(0xFFEEF2F6);
            snackContent.addView(check, new LinearLayout.LayoutParams(dp(14), dp(14)));
            snackMessage = new TextView(this);
            snackMessage.setTextSize(12f);
            snackMessage.setTextColor(0xFFEEF2F6);
            LinearLayout.LayoutParams messageParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            messageParams.leftMargin = dp(8);
            snackContent.addView(snackMessage, messageParams);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xFF232B36);
            background.setCornerRadius(dp(13));
            snackContent.setBackground(background);
            snackContent.setElevation(dp(8));
            snackView = snackContent;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            params.leftMargin = dp(16);
            params.rightMargin = dp(16);
            params.bottomMargin = dp(82);
            contentRoot.addView(snackView, params);
            installNavigationBarBottomMargin(snackView, dp(82));
            snackView.setAlpha(0f);
            snackView.setTranslationY(dp(14));
            snackView.setVisibility(View.GONE);
        }
        contentRoot.removeCallbacks(snackDismiss);
        snackMessage.setText(message);
        snackView.setVisibility(View.VISIBLE);
        snackView.animate().cancel();
        snackView.setAlpha(0f);
        snackView.setTranslationY(dp(14));
        snackView.animate().alpha(1f).translationY(0f).setDuration(250L).start();
        contentRoot.postDelayed(snackDismiss, durationMs);
    }

    /**
     * Mockup bottom bar: tonal restart + filled save, pinned below the ScrollView, with the
     * UNSAVED badge floating over its top-right corner while the draft differs from saved.
     */
    private View createBottomActionBar() {
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setClipChildren(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(8));
        GradientDrawable barBackground = new GradientDrawable();
        barBackground.setColor(0xFFF2F4F7);
        barBackground.setStroke(dp(1), 0x141B222C);
        bar.setBackground(barBackground);
        bar.setElevation(dp(8));
        installBottomSurfaceInsets(bar);

        Button restartSystemUi = pillButton(
                getString(R.string.action_restart_system_ui),
                0x33F2C14E,
                0xFF9A6A12,
                Typeface.NORMAL);
        restartSystemUi.setOnClickListener(view -> requestSystemUiRestart());
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(
                0,
                dp(46),
                0.9f);
        restartParams.rightMargin = dp(9);
        bar.addView(restartSystemUi, restartParams);

        Button save = pillButton(
                getString(R.string.action_apply_save),
                0xFFF2C14E,
                0xFF231603,
                Typeface.BOLD);
        save.setElevation(dp(4));
        save.setOnClickListener(view -> save());
        bar.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1.25f));
        // The bar sits 8dp below the wrapper top so the UNSAVED badge can float above its
        // top edge while staying fully inside the wrapper (no negative margins / clipping).
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = dp(8);
        wrapper.addView(bar, barParams);

        unsavedBadge = text(getString(R.string.bar_unsaved), 8f, Color.WHITE);
        unsavedBadge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        unsavedBadge.setLetterSpacing(0.18f);
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(getColor(R.color.settings_error));
        badgeBackground.setCornerRadius(dp(99));
        unsavedBadge.setBackground(badgeBackground);
        unsavedBadge.setPadding(dp(9), dp(3), dp(9), dp(3));
        // The action bar itself is elevated; keep the badge above that surface or the lower
        // half is covered by the bar and looks clipped/translucent.
        unsavedBadge.setElevation(dp(12));
        // Elevation is needed only for drawing order. A null outline suppresses its shadow.
        unsavedBadge.setOutlineProvider(null);
        unsavedBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        badgeParams.rightMargin = dp(16);
        wrapper.addView(unsavedBadge, badgeParams);
        return wrapper;
    }

    private Button pillButton(String label, int background, int textColor, int style) {
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
        return button;
    }

    private void installFloatingPreview(
            FrameLayout root,
            ScrollView scroll,
            FrameLayout anchor,
            View preview) {
        Runnable update = () -> scheduleFloatingPreviewUpdate(root, anchor, preview);
        preview.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            if (height <= 0) return;
            root.post(() -> {
                ViewGroup.LayoutParams params = anchor.getLayoutParams();
                if (params != null && params.height != height) {
                    params.height = height;
                    anchor.setLayoutParams(params);
                }
                update.run();
            });
        });
        root.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            cachedTopUiBoundary = null;
            cachedTopUiBoundaryRootTop = Integer.MIN_VALUE;
            update.run();
        });
        scroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY != oldScrollY) {
                holdPreviewHardwareLayerDuringScroll(preview);
            }
            update.run();
        });
        update.run();
    }

    private void holdPreviewHardwareLayerDuringScroll(View preview) {
        if (preview == null) return;
        View previous = scrollCachedPreview;
        if (previous != null && previous != preview) {
            previous.removeCallbacks(releaseScrollCachedPreview);
            if (previous.getLayerType() == View.LAYER_TYPE_HARDWARE) {
                previous.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        }
        scrollCachedPreview = preview;
        preview.removeCallbacks(releaseScrollCachedPreview);
        if (preview.getLayerType() != View.LAYER_TYPE_HARDWARE) {
            preview.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        preview.postDelayed(
                releaseScrollCachedPreview,
                PREVIEW_SCROLL_LAYER_RELEASE_DELAY_MS);
    }

    private void releaseScrollCachedPreviewLayer() {
        View preview = scrollCachedPreview;
        scrollCachedPreview = null;
        if (preview == null) return;
        preview.removeCallbacks(releaseScrollCachedPreview);
        if (preview.getLayerType() == View.LAYER_TYPE_HARDWARE) {
            preview.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private void scheduleFloatingPreviewUpdate(
            FrameLayout root,
            FrameLayout anchor,
            View preview) {
        if (floatingPreviewUpdatePosted) {
            return;
        }
        floatingPreviewUpdatePosted = true;
        root.postOnAnimation(() -> {
            floatingPreviewUpdatePosted = false;
            if (root.isInLayout() || anchor.isInLayout()) {
                scheduleFloatingPreviewUpdate(root, anchor, preview);
                return;
            }
            updateFloatingPreviewPosition(root, anchor, preview);
        });
    }

    private void updateFloatingPreviewPosition(
            FrameLayout root,
            FrameLayout anchor,
            View preview) {
        if (root.getHeight() <= 0 || anchor.getHeight() <= 0 || preview.getHeight() <= 0) {
            return;
        }
        root.getLocationOnScreen(floatingPreviewRootLocation);
        anchor.getLocationOnScreen(floatingPreviewAnchorLocation);
        float naturalTop = floatingPreviewAnchorLocation[1] - floatingPreviewRootLocation[1];
        TopUiBoundary topUiBoundary = resolveCachedTopUiBoundaryOnScreen(
                floatingPreviewRootLocation[1]);
        float stickyTop = LyricUiLayoutPolicy.floatingPreviewTopInRoot(
                topUiBoundary.bottomOnScreen,
                floatingPreviewRootLocation[1],
                5f);
        boolean floating = naturalTop <= stickyTop;
        logFloatingPreviewGeometry(
                topUiBoundary,
                floatingPreviewRootLocation[1],
                Math.round(stickyTop),
                floating);
        ViewParent previewParent = preview.getParent();
        // Keep the preview inside the ScrollView until it actually becomes sticky. This lets
        // Android's overscroll stretch and rebound transform the preview together with the page.
        if (floating && previewParent != root) {
            if (previewParent instanceof ViewGroup) {
                ((ViewGroup) previewParent).removeView(preview);
            }
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            int horizontalMargin = settingsScreenPadding();
            params.leftMargin = horizontalMargin;
            params.rightMargin = horizontalMargin;
            setTranslationYIfChanged(preview, stickyTop);
            root.addView(preview, params);
        } else if (!floating && previewParent != anchor) {
            if (previewParent instanceof ViewGroup) {
                ((ViewGroup) previewParent).removeView(preview);
            }
            setTranslationYIfChanged(preview, 0f);
            anchor.addView(preview, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
        } else {
            setTranslationYIfChanged(preview, floating ? stickyTop : 0f);
        }
        float transitionDistance = dp(32);
        float floatingAmount = 1f - Math.max(
                0f,
                Math.min(1f, (naturalTop - stickyTop) / transitionDistance));
        setElevationIfChanged(preview, dp(8) * floatingAmount);
        if (preview.getVisibility() != View.VISIBLE) {
            preview.setVisibility(View.VISIBLE);
        }
    }

    private TopUiBoundary resolveCachedTopUiBoundaryOnScreen(int rootTopOnScreen) {
        if (cachedTopUiBoundary == null
                || cachedTopUiBoundaryRootTop != rootTopOnScreen) {
            cachedTopUiBoundary = resolveTopUiBoundaryOnScreen(rootTopOnScreen);
            cachedTopUiBoundaryRootTop = rootTopOnScreen;
        }
        return cachedTopUiBoundary;
    }

    private static void setTranslationYIfChanged(View view, float translationY) {
        if (Math.abs(view.getTranslationY() - translationY) > 0.25f) {
            view.setTranslationY(translationY);
        }
    }

    private static void setElevationIfChanged(View view, float elevation) {
        if (Math.abs(view.getElevation() - elevation) > 0.25f) {
            view.setElevation(elevation);
        }
    }

    private TopUiBoundary resolveTopUiBoundaryOnScreen(int rootTopOnScreen) {
        View decor = getWindow().getDecorView();
        View boundaryView = resolveActionBarBoundaryView(decor);
        if (isUsableTopBoundary(boundaryView)) {
            int[] location = new int[2];
            boundaryView.getLocationOnScreen(location);
            return new TopUiBoundary(
                    location[1] + boundaryView.getHeight(),
                    resourceEntryName(boundaryView));
        }

        View content = decor.findViewById(android.R.id.content);
        if (content != null && content.getVisibility() == View.VISIBLE) {
            int[] location = new int[2];
            content.getLocationOnScreen(location);
            if (location[1] > rootTopOnScreen) {
                return new TopUiBoundary(location[1], "android-content");
            }
        }

        Rect visibleFrame = new Rect();
        decor.getWindowVisibleDisplayFrame(visibleFrame);
        return new TopUiBoundary(
                Math.max(rootTopOnScreen, visibleFrame.top),
                "custom-appbar-fallback");
    }

    private View resolveActionBarBoundaryView(View decor) {
        if (isUsableTopBoundary(actionBarBoundaryView)) {
            return actionBarBoundaryView;
        }
        int systemId = getResources().getIdentifier(
                "action_bar_container",
                "id",
                "android");
        if (systemId != 0) {
            View candidate = decor.findViewById(systemId);
            if (candidate != null) {
                actionBarBoundaryView = candidate;
                return candidate;
            }
        }
        View candidate = findViewByResourceEntryName(decor, "action_bar_container");
        if (candidate == null) {
            candidate = findViewByResourceEntryName(decor, "action_bar");
        }
        if (candidate != null) {
            actionBarBoundaryView = candidate;
        }
        return candidate;
    }

    private View findViewByResourceEntryName(View view, String expectedName) {
        if (view == null) return null;
        if (expectedName.equals(resourceEntryName(view))) {
            return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View match = findViewByResourceEntryName(group.getChildAt(index), expectedName);
            if (match != null) return match;
        }
        return null;
    }

    private String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return "unknown";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return "unknown";
        }
    }

    private static boolean isUsableTopBoundary(View view) {
        return view != null
                && view.getVisibility() == View.VISIBLE
                && view.getHeight() > 0
                && view.isAttachedToWindow();
    }

    private void logFloatingPreviewGeometry(
            TopUiBoundary boundary,
            int rootTop,
            int stickyTop,
            boolean floating) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        String signature = boundary.source
                + ':' + boundary.bottomOnScreen
                + ':' + rootTop
                + ':' + stickyTop
                + ':' + floating;
        if (signature.equals(lastFloatingPreviewGeometryLog)) return;
        lastFloatingPreviewGeometryLog = signature;
        Log.d(TAG, LyricLogFormatter.format(
                getPackageName(),
                LyricLogFormatter.Area.SETTINGS,
                "preview-geometry",
                "Floating preview geometry, source=" + boundary.source
                        + ", boundaryBottom=" + boundary.bottomOnScreen
                        + ", rootTop=" + rootTop
                        + ", stickyTop=" + stickyTop
                        + ", floating=" + floating));
    }

    private void installDraftListeners() {
        presetSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (binding || !draftListenersReady
                    || position != presetSpinner.getSelectedItemPosition()) {
                return;
            }
            if (position == ignoredPresetSelection) {
                ignoredPresetSelection = -1;
                return;
            }
            if (position >= LyricUiPreset.CUSTOM.ordinal()) return;
            bind(LyricUiPreset.values()[position].apply(readDraft()));
        }));
        View.OnClickListener changed = view -> onDraftChanged();
        blurEnabled.setOnClickListener(view -> {
            markManualAppearanceChanged();
            updateConditionalRows();
            changed.onClick(view);
        });
        scaleEnabled.setOnClickListener(view -> {
            markManualAppearanceChanged();
            updateConditionalRows();
            changed.onClick(view);
        });
        glowEnabled.setOnClickListener(view -> {
            markManualAppearanceChanged();
            changed.onClick(view);
        });
        for (MaterialSwitch toggle : new MaterialSwitch[]{
                passiveVerticalPan, translationMarquee,
                lineTimedProgress, translationProgress}) {
            toggle.setOnClickListener(changed);
        }
        screenTimeout.setOnClickListener(view -> {
            updateConditionalRows();
            changed.onClick(view);
        });
        for (Slider slider : materialValueSliders()) {
            slider.addOnChangeListener((view, value, fromUser) -> {
                Object tag = slider.getTag();
                if (tag instanceof SeekValueLabel) ((SeekValueLabel) tag).update();
                if (fromUser) {
                    if (slider == glowIntensity) {
                        glowEnabled.setChecked(value > 0f);
                    }
                    markManualAppearanceChanged();
                    if (presetTransitionActive && presetAnimator != null) {
                        presetAnimator.cancel();
                    }
                    onDraftChanged();
                }
            });
            // While a finger is on the thumb, defer preset re-detection: sliding back and
            // forth across a preset's exact values must not flip the selection/label state
            // on every callback. The final value is judged once when the drag ends.
            slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(Slider touched) {
                    manualSliderDragActive = true;
                }

                @Override
                public void onStopTrackingTouch(Slider touched) {
                    manualSliderDragActive = false;
                    if (!binding && !presetTransitionActive) {
                        updatePresetCards(LyricUiPreset.detect(readDraft()));
                    }
                }
            });
        }
        refreshRate.setOnClickListener(view -> showRefreshRateMenu());
    }

    private void onDraftChanged() {
        if (!binding && !presetTransitionActive) {
            LyricUiConfig config = readDraft();
            bindPreview(config);
            // Preset selection is change-only cached, so manual dragging updates the state
            // without restarting card/label animations on every slider callback. While a
            // thumb is being dragged the state stays on whatever the drag first marked
            // (usually CUSTOM); the final value is re-detected on touch up.
            if (!manualSliderDragActive) {
                updatePresetCards(LyricUiPreset.detect(config));
            }
            updatePaletteSelections();
            updateDirtyState();
        }
    }

    private void updateDirtyState() {
        updateDirtyState(null);
    }

    /** Re-evaluates the UNSAVED badge and back interception against the given config. */
    private void updateDirtyState(LyricUiConfig config) {
        boolean dirty = savedConfig != null
                && !(config != null ? config : readDraft()).equals(savedConfig);
        if (dirty == dirtyState) return;
        dirtyState = dirty;
        if (unsavedBadge != null) {
            if (dirty) {
                unsavedBadge.setAlpha(0f);
                unsavedBadge.setVisibility(View.VISIBLE);
                unsavedBadge.animate().alpha(1f).setDuration(220L).start();
            } else {
                unsavedBadge.animate().cancel();
                unsavedBadge.setVisibility(View.GONE);
            }
        }
        if (backCallback != null) {
            backCallback.setEnabled(dirty);
        }
    }

    private void confirmDiscardAndExit() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.WHITE);
        panelBackground.setCornerRadius(dp(20));
        panelBackground.setStroke(dp(1), 0x1A1B222C);
        panel.setBackground(panelBackground);

        TextView title = text(
                getString(R.string.back_discard_title),
                17,
                settingsTextColor());
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        panel.addView(title, matchWrap());

        TextView message = text(
                getString(R.string.back_discard_message),
                13,
                getColor(R.color.settings_text_secondary));
        message.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(9);
        messageParams.bottomMargin = dp(18);
        panel.addView(message, messageParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = pillButton(
                getString(R.string.dialog_cancel),
                0x121B222C,
                0xFF5C6774,
                Typeface.NORMAL);
        removeButtonShadow(cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        cancelParams.rightMargin = dp(9);
        actions.addView(cancel, cancelParams);

        Button discard = pillButton(
                getString(R.string.back_discard),
                0x24C04A3A,
                getColor(R.color.settings_error),
                Typeface.BOLD);
        removeButtonShadow(discard);
        discard.setOnClickListener(view -> {
            dialog.dismiss();
            finish();
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

    private void markManualAppearanceChanged() {
        if (binding || presetTransitionActive) return;
        updatePresetCards(LyricUiPreset.CUSTOM);
    }

    private void updatePresetCards(LyricUiPreset selected) {
        if (selected == displayedPreset) return;
        displayedPreset = selected;
        for (SettingsPresetCard card : presetCards) {
            card.setPresetSelected(card.preset() == selected);
        }
        if (presetTransitionActive) {
            hideCustomPresetLabelImmediately();
            return;
        }
        if (customPresetLabel == null) return;
        boolean show = selected == LyricUiPreset.CUSTOM;
        if (show) {
            customPresetLabel.animate().cancel();
            customPresetLabel.setVisibility(View.VISIBLE);
            customPresetLabel.setAlpha(0f);
            customPresetLabel.setScaleY(0.82f);
            customPresetLabel.animate()
                    .alpha(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else if (customPresetLabel.getVisibility() == View.VISIBLE) {
            customPresetLabel.animate().cancel();
            customPresetLabel.animate()
                    .alpha(0f)
                    .scaleY(0.82f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        customPresetLabel.setVisibility(View.GONE);
                        customPresetLabel.setAlpha(1f);
                        customPresetLabel.setScaleY(1f);
                    })
                    .start();
        }
    }

    private void hideCustomPresetLabelImmediately() {
        if (customPresetLabel == null) return;
        customPresetLabel.animate().cancel();
        customPresetLabel.setVisibility(View.GONE);
        customPresetLabel.setAlpha(1f);
        customPresetLabel.setScaleY(1f);
    }

    private void animatePresetSelection(LyricUiPreset preset) {
        LyricUiConfig start = readDraft();
        LyricUiConfig target = preset.apply(start);
        if (start.equals(target)) {
            bind(target);
            return;
        }
        if (presetAnimator != null) {
            presetAnimator.cancel();
        }
        // Commit the target exactly once. The old animation rebuilt several StaticLayouts and
        // moved every Slider up to 30 times, which was visibly janky even on a 120 Hz panel.
        // The remaining transition is compositor-only (alpha + a subtle scale) and therefore
        // does not trigger measure/layout work on animation frames.
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        presetAnimator = animator;
        presetTransitionActive = true;
        hideCustomPresetLabelImmediately();
        updatePresetCards(preset);
        bind(target);
        if (previewView != null) {
            previewView.animate().cancel();
            previewView.setAlpha(0.76f);
            previewView.setScaleX(0.992f);
            previewView.setScaleY(0.992f);
        }
        animator.setDuration(190L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (presetAnimator != animator) return;
            float fraction = valueAnimator.getAnimatedFraction();
            if (previewView != null) {
                previewView.setAlpha(0.76f + 0.24f * fraction);
                float scale = 0.992f + 0.008f * fraction;
                previewView.setScaleX(scale);
                previewView.setScaleY(scale);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
                if (presetAnimator == animator) {
                    presetAnimator = null;
                    presetTransitionActive = false;
                    resetPresetPreviewTransform();
                    updatePresetCards(LyricUiPreset.detect(readDraft()));
                    updateDirtyState();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled && presetAnimator == animator) {
                    presetAnimator = null;
                    presetTransitionActive = false;
                    resetPresetPreviewTransform();
                    updatePresetCards(LyricUiPreset.detect(readDraft()));
                    updateDirtyState();
                }
            }
        });
        animator.start();
    }

    private void resetPresetPreviewTransform() {
        if (previewView == null) return;
        previewView.setAlpha(1f);
        previewView.setScaleX(1f);
        previewView.setScaleY(1f);
    }

    private LyricUiConfig readDraft() {
        int refresh = refreshRateValues.length == 0
                ? 0
                : refreshRateValues[Math.max(0, Math.min(
                        refreshRateSelection,
                        refreshRateValues.length - 1))];
        return draft.buildUpon()
                .inactiveOpacityPercent(materialProgress(opacity))
                .blurEnabled(blurEnabled.isChecked())
                .blurRadiusTenthsPx(materialProgress(blurRadius) * 5)
                .scaleEnabled(scaleEnabled.isChecked())
                .inactiveScalePercent(materialProgress(inactiveScale))
                .glowEnabled(glowEnabled.isChecked())
                .glowIntensityPercent(materialProgress(glowIntensity))
                .glowRadiusPercent(materialProgress(glowRadius))
                .primaryColor(primaryColor.get())
                .glowColor(glowColor.get())
                .motionMode(checkedIndex(motionMode))
                .passiveVerticalPanEnabled(passiveVerticalPan.isChecked())
                .translationMarqueeEnabled(translationMarquee.isChecked())
                .maxRefreshRateHz(refresh)
                .defaultTranslationEnabled(LyricUiConfigRepository.load(
                        preferences).defaultTranslationEnabled)
                .lineTimedProgressEnabled(lineTimedProgress.isChecked())
                .translationProgressEnabled(translationProgress.isChecked())
                .screenTimeoutEnabled(screenTimeout.isChecked())
                .screenTimeoutSeconds(readInt(screenTimeoutSeconds))
                .mainFontTenthsSp(materialProgress(mainFontSize) * 10)
                .translationFontRatioPercent(materialProgress(translationFontRatio))
                .fontWeight(checkedIndex(fontWeight))
                .alignment(checkedIndex(alignment))
                .lineSpacingTenthsDp(materialProgress(lineSpacing) * 5)
                .wrappedLineSpacingTenthsDp(materialProgress(wrappedLineSpacing) * 5)
                .build();
    }

    private void bind(LyricUiConfig config) {
        bind(config, true);
    }

    private void bind(LyricUiConfig config, boolean updatePresetSelection) {
        draft = config;
        binding = true;
        setSliderValueSafely(opacity, config.inactiveOpacityPercent);
        blurEnabled.setChecked(config.blurEnabled);
        setSliderValueSafely(blurRadius, config.blurRadiusTenthsPx / 5f);
        scaleEnabled.setChecked(config.scaleEnabled);
        setSliderValueSafely(inactiveScale, config.inactiveScalePercent);
        glowEnabled.setChecked(config.glowEnabled);
        setSliderValueSafely(glowIntensity, config.glowEnabled
                ? config.glowIntensityPercent
                : 0);
        setSliderValueSafely(glowRadius, config.glowRadiusPercent);
        primaryColor.set(config.primaryColor);
        glowColor.set(config.glowColor);
        updatePaletteSelections();
        checkIndex(motionMode, config.motionMode);
        checkIndex(fontWeight, config.fontWeight);
        checkIndex(alignment, config.alignment);
        passiveVerticalPan.setChecked(config.passiveVerticalPanEnabled);
        translationMarquee.setChecked(config.translationMarqueeEnabled);
        refreshRateSelection = indexOfRefresh(config.maxRefreshRateHz);
        if (refreshRate != null) {
            refreshRate.setText(refreshRateLabels()[refreshRateSelection]);
        }
        lineTimedProgress.setChecked(config.lineTimedProgressEnabled);
        translationProgress.setChecked(config.translationProgressEnabled);
        screenTimeout.setChecked(config.screenTimeoutEnabled);
        screenTimeoutSeconds.setText(config.screenTimeoutSeconds <= 0
                ? "" : Integer.toString(config.screenTimeoutSeconds));
        setSliderValueSafely(mainFontSize, config.mainFontTenthsSp / 10f);
        setSliderValueSafely(translationFontRatio, config.translationFontRatioPercent);
        setSliderValueSafely(lineSpacing, config.lineSpacingTenthsDp / 5f);
        setSliderValueSafely(wrappedLineSpacing, config.wrappedLineSpacingTenthsDp / 5f);
        updateSeekValueLabels();
        updateConditionalRows();
        if (updatePresetSelection) {
            LyricUiPreset selected = LyricUiPreset.detect(config);
            ignoredPresetSelection = selected.ordinal();
            presetSpinner.setSelection(ignoredPresetSelection);
            updatePresetCards(selected);
        }
        binding = false;
        bindPreview(config);
        updateDirtyState();
    }

    private void updateConditionalRows() {
        if (blurRadiusRow != null) {
            blurRadiusRow.setVisibility(blurEnabled.isChecked() ? View.VISIBLE : View.GONE);
        }
        if (inactiveScaleRow != null) {
            inactiveScaleRow.setVisibility(scaleEnabled.isChecked() ? View.VISIBLE : View.GONE);
        }
        if (screenTimeoutSecondsRow != null) {
            screenTimeoutSecondsRow.setVisibility(
                    screenTimeout.isChecked() ? View.VISIBLE : View.GONE);
        }
    }

    private static void setSliderValueSafely(Slider slider, float value) {
        if (slider == null) return;
        if (Float.isNaN(value) || Float.isInfinite(value)) value = slider.getValueFrom();
        slider.setValue(snapSliderValue(slider, value));
    }

    private static float snapSliderValue(Slider slider, float value) {
        float min = slider.getValueFrom();
        float max = slider.getValueTo();
        float clamped = Math.max(min, Math.min(max, value));
        float step = slider.getStepSize();
        float snapped = step > 0f
                ? min + Math.round((clamped - min) / step) * step
                : clamped;
        return Math.max(min, Math.min(max, snapped));
    }

    private void updateSeekValueLabels() {
        for (Slider slider : materialValueSliders()) {
            Object tag = slider.getTag();
            if (tag instanceof SeekValueLabel) {
                ((SeekValueLabel) tag).update();
            }
        }
    }

    private Slider[] materialValueSliders() {
        return new Slider[]{opacity, blurRadius, inactiveScale, glowIntensity, glowRadius,
                mainFontSize, translationFontRatio, lineSpacing, wrappedLineSpacing};
    }

    private int materialProgress(Slider slider) {
        return Math.round(slider.getValue());
    }

    private void bindPreview(LyricUiConfig config) {
        previewView.bind(config);
    }

    /* private void bindPreviewLegacy(LyricUiConfig config) {
        int color = Color.parseColor(LyricUiConfig.sanitizeColor(config.primaryColor, "#FFFFFF"));
        previewMain.setTextColor(color);
        previewMain.setAlpha(1f);
        previewTranslation.setTextColor(LyricUiColors.translationBase(config, false, 1f));
        previewTranslation.setAlpha(1f);
        previewSecondaryOne.setTextColor(color);
        previewSecondaryTwo.setTextColor(color);
        previewSecondaryOne.setAlpha(config.inactiveOpacityPercent / 100f);
        previewSecondaryTwo.setAlpha(config.inactiveOpacityPercent / 100f);
        int inactiveTranslationColor = LyricUiColors.translationBase(config, false, 0f);
        previewSecondaryTranslationOne.setTextColor(inactiveTranslationColor);
        previewSecondaryTranslationTwo.setTextColor(inactiveTranslationColor);
        previewSecondaryTranslationOne.setAlpha(1f);
        previewSecondaryTranslationTwo.setAlpha(1f);
        float mainTextSizeSp = config.mainFontTenthsSp / 10f;
        previewMain.setTextSize(mainTextSizeSp);
        previewTranslation.setTextSize(
                mainTextSizeSp * config.translationFontRatioPercent / 100f);
        float translationTextSizeSp =
                mainTextSizeSp * config.translationFontRatioPercent / 100f;
        previewSecondaryOne.setTextSize(mainTextSizeSp);
        previewSecondaryTranslationOne.setTextSize(translationTextSizeSp);
        previewSecondaryTwo.setTextSize(mainTextSizeSp);
        previewSecondaryTranslationTwo.setTextSize(translationTextSizeSp);
        previewMain.setTypeface(resolvePreviewTypeface(config.fontWeight));
        previewTranslation.setTypeface(resolvePreviewTypeface(config.fontWeight));
        previewSecondaryOne.setTypeface(resolvePreviewTypeface(config.fontWeight));
        previewSecondaryTranslationOne.setTypeface(resolvePreviewTypeface(config.fontWeight));
        previewSecondaryTwo.setTypeface(resolvePreviewTypeface(config.fontWeight));
        previewSecondaryTranslationTwo.setTypeface(resolvePreviewTypeface(config.fontWeight));
        float inactiveScale = config.scaleEnabled
                ? config.inactiveScalePercent / 100f
                : 1f;
        for (LinearLayout secondary : new LinearLayout[]{
                previewSecondarySlotOne,
                previewSecondarySlotTwo}) {
            secondary.setScaleX(inactiveScale);
            secondary.setScaleY(inactiveScale);
            updatePreviewScalePivot(secondary, config.alignment);
            applyPreviewBlur(
                    secondary,
                    config.blurEnabled,
                    config.blurRadiusTenthsPx / 10f);
        }
        if (config.glowEnabled && config.glowIntensityPercent > 0) {
            float mainTextSizePx = mainTextSizeSp
                    * getResources().getDisplayMetrics().scaledDensity;
            previewMain.setShadowLayer(
                    Math.max(1f, mainTextSizePx * config.glowRadiusPercent / 100f),
                    0f,
                    0f,
                    LyricUiColors.glowShadow(config));
        } else {
            previewMain.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        }
        int gravity = config.alignment == LyricUiConfig.ALIGN_CENTER
                ? Gravity.CENTER_HORIZONTAL
                : config.alignment == LyricUiConfig.ALIGN_END ? Gravity.END : Gravity.START;
        previewMain.setGravity(gravity);
        previewTranslation.setGravity(gravity);
        previewSecondaryOne.setGravity(gravity);
        previewSecondaryTranslationOne.setGravity(gravity);
        previewSecondaryTwo.setGravity(gravity);
        previewSecondaryTranslationTwo.setGravity(gravity);
        for (TextView line : new TextView[]{
                previewMain,
                previewTranslation,
                previewSecondaryOne,
                previewSecondaryTranslationOne,
                previewSecondaryTwo,
                previewSecondaryTranslationTwo}) {
            updatePreviewOpticalCenter(line, config.alignment);
        }
        int previewSpacing = dp(
                LyricUiLayoutPolicy.lineSpacingTenthsDp(config) / 10f);
        previewMain.setLineSpacing(
                dp(1f + LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(config) / 10f),
                1f);
        previewMain.setIncludeFontPadding(false);
        previewTranslation.setIncludeFontPadding(false);
        previewSecondaryOne.setIncludeFontPadding(false);
        previewSecondaryTranslationOne.setIncludeFontPadding(false);
        previewSecondaryTwo.setIncludeFontPadding(false);
        previewSecondaryTranslationTwo.setIncludeFontPadding(false);
        setBottomMargin(previewMain, dp(2f));
        setBottomMargin(previewTranslation, 0);
        setBottomMargin(previewSecondaryOne, dp(2f));
        setBottomMargin(previewSecondaryTranslationOne, 0);
        setBottomMargin(previewSecondaryTwo, dp(2f));
        setBottomMargin(previewSecondaryTranslationTwo, 0);
        setPreviewSlotHeight(
                previewActiveSlot,
                previewTranslatedSlotHeight(config, previewMain, previewTranslation));
        setPreviewSlotHeight(
                previewSecondarySlotOne,
                previewTranslatedSlotHeight(
                        config,
                        previewSecondaryOne,
                        previewSecondaryTranslationOne));
        setPreviewSlotHeight(
                previewSecondarySlotTwo,
                previewTranslatedSlotHeight(
                        config,
                        previewSecondaryTwo,
                        previewSecondaryTranslationTwo));
        setBottomMargin(previewActiveSlot, previewSpacing);
        setBottomMargin(previewSecondarySlotOne, previewSpacing);
        setBottomMargin(previewSecondarySlotTwo, 0);
        LyricUiPreset preset = LyricUiPreset.detect(config);
        if (!binding) {
            binding = true;
            presetSpinner.setSelection(preset.ordinal());
            binding = false;
        }
    }

    private void updatePreviewOpticalCenter(TextView view, int alignmentMode) {
        if (alignmentMode != LyricUiConfig.ALIGN_CENTER || view == null) {
            if (view != null) view.setTranslationX(0f);
            return;
        }
        String text = view.getText() == null ? "" : view.getText().toString();
        if (text.isEmpty()) {
            view.setTranslationX(0f);
            return;
        }
        if (text.indexOf('\n') >= 0) {
            // A single translation cannot optically center multiple lines with different
            // ink bounds. Keep TextView's per-line gravity instead of shifting the whole block.
            view.setTranslationX(0f);
            return;
        }
        Rect bounds = new Rect();
        view.getPaint().getTextBounds(text, 0, text.length(), bounds);
        if (bounds.isEmpty()) {
            view.setTranslationX(0f);
            return;
        }
        float advanceWidth = view.getPaint().measureText(text);
        view.setTranslationX(LyricUiLayoutPolicy.opticallyCenteredBaselineX(
                0f,
                advanceWidth,
                bounds.left,
                bounds.right));
    }

    private void updatePreviewScalePivot(View view, int alignmentMode) {
        Runnable update = () -> {
            int width = view.getWidth();
            if (width <= 0) return;
            boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            view.setPivotX(LyricUiLayoutPolicy.horizontalScalePivot(
                    alignmentMode,
                    rtl,
                    0f,
                    width));
            view.setPivotY(view.getHeight() / 2f);
        };
        if (view.getWidth() > 0) {
            update.run();
        } else {
            view.post(update);
        }
    }

    private int previewTranslatedSlotHeight(
            LyricUiConfig config,
            TextView main,
            TextView translation) {
        android.graphics.Paint.FontMetrics mainMetrics = main.getPaint().getFontMetrics();
        android.graphics.Paint.FontMetrics translationMetrics =
                translation.getPaint().getFontMetrics();
        float mainHeight = LyricUiLayoutPolicy.mainTextBlockHeight(
                mainMetrics.top,
                mainMetrics.ascent,
                mainMetrics.descent,
                mainMetrics.bottom,
                previewMainLineCount(main),
                dp(1f + LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(config) / 10f));
        float groupHeight = mainHeight
                + dp(2f)
                + LyricUiLayoutPolicy.fontOuterHeight(
                translationMetrics.top,
                translationMetrics.bottom);
        return previewSlotHeight(config, groupHeight, main.getPaint().getTextSize());
    }

    private static int previewMainLineCount(TextView view) {
        if (view == null || view.getText() == null || view.getText().length() == 0) {
            return 0;
        }
        int count = 1;
        CharSequence text = view.getText();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return LyricUiLayoutPolicy.visibleMainLineCount(count, 2);
    }

    private int previewSlotHeight(
            LyricUiConfig config,
            float groupHeight,
            float mainTextSizePx) {
        int verticalPadding = dp(12f);
        if (config.glowEnabled) {
            verticalPadding = Math.max(
                    verticalPadding,
                    Math.round(mainTextSizePx * config.glowRadiusPercent / 50f) + dp(2f));
        }
        return LyricUiLayoutPolicy.requiredSlotHeight(
                groupHeight,
                verticalPadding,
                dp(1f),
                dp(56f));
    }

    private static void setPreviewSlotHeight(View view, int height) {
        if (view == null || !(view.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        if (params.height == height) return;
        params.height = height;
        view.setLayoutParams(params);
    }

    */

    private void requestModuleStatus() {
        if (moduleStatusQueryPending) return;
        moduleStatusQueryPending = true;
        try {
            Intent intent = new Intent(LyricUiSettings.ACTION_REQUEST_MODULE_STATUS)
                    .setPackage("com.android.systemui")
                    .putExtra(
                            LyricUiSettings.EXTRA_RESULT_RECEIVER,
                            createModuleStatusResultReceiver());
            intent.addFlags(
                    Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
            sendBroadcast(intent);
            getWindow().getDecorView().postDelayed(() -> {
                if (!moduleStatusQueryPending) return;
                moduleStatusQueryPending = false;
                showModuleStatusInactive();
            }, MODULE_STATUS_QUERY_TIMEOUT_MS);
        } catch (RuntimeException error) {
            moduleStatusQueryPending = false;
            Log.w(TAG, "Could not request module status", error);
            showModuleStatusInactive();
        }
    }

    private ResultReceiver createModuleStatusResultReceiver() {
        return new ModuleStatusResultReceiver();
    }

    private void showModuleStatusInactive() {
        setActivationStatus(
                getColor(R.color.settings_text_muted),
                getString(R.string.settings_module_status_inactive));
    }

    private void setActivationStatus(int dotColor, String label) {
        if (activationDot == null || activationText == null) return;
        if (activationDot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) activationDot.getBackground()).setColor(dotColor);
        }
        activationText.setText(label);
    }

    /**
     * Named (not anonymous) ResultReceivers: the class name is parceled to SystemUI, and
     * anonymous numbering shifts whenever the activity gains new anonymous classes, which
     * crashes SystemUI with BadParcelableException after an APK update. Keep names stable.
     */
    private final class SystemUiRestartResultReceiver extends ResultReceiver {
        private final long requestId;

        SystemUiRestartResultReceiver(long requestId) {
            super(new Handler(getMainLooper()));
            this.requestId = requestId;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (pendingSystemUiRestartRequestId != requestId
                    || resultData == null
                    || resultData.getLong(
                    LyricUiSettings.RESULT_SYSTEM_UI_RESTART_REQUEST_ID,
                    -1L) != requestId) {
                return;
            }
            pendingSystemUiRestartRequestId = -1L;
            if (resultCode == LyricUiSettings.RESULT_SYSTEM_UI_RESTART_ACKNOWLEDGED
                    && resultData.getBoolean(
                    LyricUiSettings.RESULT_SYSTEM_UI_RESTART_ACCEPTED,
                    false)) {
                showSnack(
                        getString(R.string.snack_restart_acknowledged),
                        SNACK_DURATION_SHORT_MS);
            }
        }
    }

    private final class SettingsApplyResultReceiver extends ResultReceiver {
        private final long revision;

        SettingsApplyResultReceiver(long revision) {
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
            int appliedAlignment = resultData.getInt(
                    LyricUiSettings.RESULT_ALIGNMENT,
                    -1);
            String process = resultData.getString(LyricUiSettings.RESULT_PROCESS, "unknown");
            String reason = resultData.getString(LyricUiSettings.RESULT_REASON, "");
            logSettingsEvent(
                    applied ? "settings-ack" : "settings-rejected",
                    "Received SystemUI settings acknowledgement"
                            + " | source=" + LyricUiSettings.SOURCE_MAIN_SETTINGS
                            + ", revision=" + revision
                            + ", process=" + process
                            + ", alignment=" + appliedAlignment
                            + ", applied=" + applied
                            + ", reason=" + reason);
            showSnack(
                    applied
                            ? getString(R.string.snack_save_applied)
                            : getString(R.string.snack_save_rejected, reason),
                    applied ? SNACK_DURATION_SHORT_MS : SNACK_DURATION_LONG_MS);
        }
    }

    private final class ModuleStatusResultReceiver extends ResultReceiver {
        ModuleStatusResultReceiver() {
            super(new Handler(getMainLooper()));
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (!moduleStatusQueryPending || resultData == null) return;
            moduleStatusQueryPending = false;
            int loadedVersionCode = resultData.getInt(
                    LyricUiSettings.RESULT_MODULE_VERSION_CODE,
                    -1);
            if (loadedVersionCode > 0 && loadedVersionCode < BuildConfig.VERSION_CODE) {
                setActivationStatus(
                        getColor(R.color.settings_error),
                        getString(R.string.settings_module_status_active_restart));
            } else {
                setActivationStatus(
                        getColor(R.color.settings_success),
                        getString(R.string.settings_module_status_active));
            }
        }
    }

    /** Mockup-style pill segmented control; single selection by index, fully custom drawing. */
    private final class SegmentedControl extends LinearLayout {
        private final View[] segments;
        private int selectedIndex;

        SegmentedControl(String[] labels) {
            this(labels, null);
        }

        SegmentedControl(String[] labels, int[] iconRes) {
            super(LyricUiSettingsActivity.this);
            selectedIndex = -1;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable container = new GradientDrawable();
            container.setColor(0x0F1B222C);
            container.setCornerRadius(dp(11));
            setBackground(container);
            setPadding(dp(2.5f), dp(2.5f), dp(2.5f), dp(2.5f));
            segments = new View[labels.length];
            for (int index = 0; index < labels.length; index++) {
                View segment;
                if (iconRes != null && iconRes[index] != 0) {
                    ImageView iconSegment = new ImageView(LyricUiSettingsActivity.this);
                    iconSegment.setImageResource(iconRes[index]);
                    iconSegment.setContentDescription(labels[index]);
                    iconSegment.setScaleType(ImageView.ScaleType.CENTER);
                    iconSegment.setImageTintList(segmentTextColor());
                    segment = iconSegment;
                } else {
                    TextView textSegment = new TextView(LyricUiSettingsActivity.this);
                    textSegment.setText(labels[index]);
                    textSegment.setTextSize(10.5f);
                    textSegment.setGravity(Gravity.CENTER);
                    textSegment.setIncludeFontPadding(false);
                    textSegment.setSingleLine(true);
                    textSegment.setPadding(dp(2), 0, dp(2), 0);
                    textSegment.setTextColor(segmentTextColor());
                    segment = textSegment;
                }
                segment.setBackground(segmentBackground());
                segment.setClickable(true);
                final int position = index;
                segment.setOnClickListener(view -> select(position, true));
                segments[index] = segment;
                addView(segment, new LinearLayout.LayoutParams(0, dp(34), 1f));
            }
            select(0, false);
        }

        private StateListDrawable segmentBackground() {
            StateListDrawable states = new StateListDrawable();
            GradientDrawable checked = new GradientDrawable();
            checked.setColor(Color.WHITE);
            checked.setCornerRadius(dp(8.5f));
            GradientDrawable unchecked = new GradientDrawable();
            unchecked.setColor(Color.TRANSPARENT);
            states.addState(new int[]{android.R.attr.state_selected}, checked);
            states.addState(new int[]{}, unchecked);
            return states;
        }

        private ColorStateList segmentTextColor() {
            return new ColorStateList(
                    new int[][]{{android.R.attr.state_selected}, {}},
                    new int[]{0xFF9A6A12, 0x992B3440});
        }

        void select(int index, boolean user) {
            if (segments.length == 0) return;
            if (index < 0 || index >= segments.length) index = 0;
            if (selectedIndex == index) return;
            selectedIndex = index;
            for (int i = 0; i < segments.length; i++) {
                segments[i].setSelected(i == index);
            }
            if (user && !binding && !presetTransitionActive) {
                markManualAppearanceChanged();
                onDraftChanged();
            }
        }

        int selectedIndex() {
            return selectedIndex;
        }
    }

    private void requestSystemUiRestart() {
        long requestId = ++systemUiRestartRequestSequence;
        pendingSystemUiRestartRequestId = requestId;
        try {
            Intent intent = new Intent(LyricUiSettings.ACTION_RESTART_SYSTEM_UI)
                    .setPackage("com.android.systemui")
                    .putExtra(
                            LyricUiSettings.EXTRA_SYSTEM_UI_RESTART_REQUEST_ID,
                            requestId)
                    .putExtra(
                            LyricUiSettings.EXTRA_SYSTEM_UI_RESTART_RESULT_RECEIVER,
                            createSystemUiRestartResultReceiver(requestId));
            intent.addFlags(
                    Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
            sendBroadcast(intent);
            showSnack(
                    getString(R.string.snack_restart_requesting),
                    SNACK_DURATION_SHORT_MS);
            getWindow().getDecorView().postDelayed(() -> {
                if (pendingSystemUiRestartRequestId != requestId) return;
                pendingSystemUiRestartRequestId = -1L;
                showSnack(
                        getString(R.string.snack_restart_no_response),
                        SNACK_DURATION_LONG_MS);
            }, SYSTEM_UI_RESTART_ACK_TIMEOUT_MS);
        } catch (RuntimeException error) {
            if (pendingSystemUiRestartRequestId == requestId) {
                pendingSystemUiRestartRequestId = -1L;
            }
            Log.w(TAG, "Could not request SystemUI restart", error);
            showSnack(
                    getString(R.string.snack_restart_failed),
                    SNACK_DURATION_LONG_MS);
        }
    }

    private ResultReceiver createSystemUiRestartResultReceiver(long requestId) {
        return new SystemUiRestartResultReceiver(requestId);
    }

    private void save() {
        String primary = primaryColor.get().trim();
        String glow = glowColor.get().trim();
        if (!COLOR_PATTERN.matcher(primary).matches() || !COLOR_PATTERN.matcher(glow).matches()) {
            showSnack(getString(R.string.color_error_format), SNACK_DURATION_SHORT_MS);
            return;
        }
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
                                LyricUiSettings.SOURCE_MAIN_SETTINGS)
                        .putExtra(
                                LyricUiSettings.EXTRA_RESULT_RECEIVER,
                                createApplyResultReceiver(revision)),
                config);
        // SystemUI registers its dynamic receiver with CHANGE_SETTINGS_PERMISSION, which
        // authenticates this sender. Passing the same value as receiverPermission would
        // instead require SystemUI to hold our signature permission and drop the broadcast.
        sendBroadcast(intent);
        draft = config;
        savedConfig = config;
        updateDirtyState();
        logSettingsEvent(
                "settings-send",
                "Sent lyric UI settings"
                        + " | source=" + LyricUiSettings.SOURCE_MAIN_SETTINGS
                        + ", revision=" + revision
                        + ", alignment=" + config.alignment
                        + ", fontSp10=" + config.mainFontTenthsSp
                        + ", lineSpacingDp10=" + config.lineSpacingTenthsDp
                        + ", wrappedLineSpacingDp10="
                        + config.wrappedLineSpacingTenthsDp);
        showSnack(getString(R.string.snack_save_sent), SNACK_DURATION_SHORT_MS);
        getWindow().getDecorView().postDelayed(() -> {
            if (pendingSettingsRevision != revision) return;
            pendingSettingsRevision = -1L;
            showSnack(
                    getString(R.string.snack_save_pending),
                    SNACK_DURATION_LONG_MS);
        }, 2_500L);
    }

    private ResultReceiver createApplyResultReceiver(long revision) {
        return new SettingsApplyResultReceiver(revision);
    }

    private void logSettingsEvent(String event, String message) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        Log.i(TAG, LyricLogFormatter.format(
                getPackageName(),
                LyricLogFormatter.Area.SETTINGS,
                event,
                message));
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private void buildRefreshRateOptions() {
        boolean has60 = false, has90 = false, has120 = false;
        @SuppressWarnings("deprecation")
        Display display = getWindowManager().getDefaultDisplay();
        if (display != null) {
            for (Display.Mode mode : display.getSupportedModes()) {
                int rate = Math.round(mode.getRefreshRate());
                has60 |= Math.abs(rate - 60) <= 1;
                has90 |= Math.abs(rate - 90) <= 1;
                has120 |= Math.abs(rate - 120) <= 1;
            }
        }
        List<Integer> rates = new ArrayList<>();
        rates.add(0);
        if (has60) rates.add(60);
        if (has90) rates.add(90);
        if (has120) rates.add(120);
        refreshRateValues = new int[rates.size()];
        for (int i = 0; i < rates.size(); i++) refreshRateValues[i] = rates.get(i);
    }

    private String[] refreshRateLabels() {
        String[] labels = new String[refreshRateValues.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = refreshRateValues[i] == 0
                    ? getString(R.string.refresh_follow_screen)
                    : getString(R.string.refresh_hz_format, refreshRateValues[i]);
        }
        return labels;
    }

    private int indexOfRefresh(int value) {
        for (int i = 0; i < refreshRateValues.length; i++) if (refreshRateValues[i] == value) return i;
        return 0;
    }

    private View statusHeader() {
        LinearLayout header = card();
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(12), dp(14));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setContentDescription(getString(R.string.settings_module_icon_description));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(getColor(R.color.ic_launcher_background));
        iconBackground.setCornerRadius(dp(10));
        icon.setBackground(iconBackground);
        icon.setClipToOutline(true);
        icon.setElevation(dp(2));
        header.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, dp(8), 0);
        TextView name = text(getString(R.string.settings_status_name), 13.5f, settingsTextColor());
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(name, matchWrap());

        LinearLayout activation = new LinearLayout(this);
        activation.setGravity(Gravity.CENTER_VERTICAL);
        activationDot = new View(this);
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(getColor(R.color.settings_text_muted));
        activationDot.setBackground(dotBackground);
        activationDot.setElevation(dp(2));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.rightMargin = dp(6);
        activation.addView(activationDot, dotParams);
        activationText = text(
                getString(R.string.settings_module_status_checking),
                12,
                getColor(R.color.settings_text_secondary));
        activation.addView(activationText, matchWrap());
        details.addView(activation, matchWrap());

        activationPulse = ObjectAnimator.ofFloat(activationDot, View.ALPHA, 1f, 0.35f, 1f);
        activationPulse.setDuration(2_400L);
        activationPulse.setRepeatCount(ValueAnimator.INFINITE);
        activationPulse.start();

        header.addView(details, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        LinearLayout language = new LinearLayout(this);
        language.setGravity(Gravity.CENTER_VERTICAL);
        language.setContentDescription(getString(R.string.settings_language_description));
        language.setPadding(dp(10), dp(5), dp(10), dp(5));
        ImageView globe = new ImageView(this);
        globe.setImageResource(R.drawable.ic_language);
        globe.setColorFilter(0xFF9A6A12);
        language.addView(globe, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView languageText = text(
                getString(R.string.settings_language_current),
                11,
                getColor(R.color.settings_secondary));
        LinearLayout.LayoutParams languageTextParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        languageTextParams.leftMargin = dp(4);
        language.addView(languageText, languageTextParams);
        GradientDrawable languageBackground = new GradientDrawable();
        languageBackground.setColor(Color.TRANSPARENT);
        languageBackground.setStroke(dp(1), getColor(R.color.settings_secondary));
        languageBackground.setCornerRadius(dp(99));
        language.setBackground(languageBackground);
        language.setOnClickListener(view -> toggleSettingsLocale());
        header.addView(language, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)));
        return header;
    }

    private LinearLayout row(String label, View control) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = text(label, 16, settingsTextColor());
        text.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        text.setIncludeFontPadding(false);
        row.addView(text, new LinearLayout.LayoutParams(0, dp(54), 1f));
        row.addView(control, new LinearLayout.LayoutParams(dp(150), dp(54)));
        return row;
    }

    private View colorPaletteRow(
            String label,
            String[] colors,
            PaletteTarget target,
            String customFallback) {
        LinearLayout group = new LinearLayout(this);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setPadding(dp(17), dp(5), dp(13), dp(5));
        group.setMinimumHeight(dp(48));
        TextView labelView = text(label, 13, settingsTextColor());
        labelView.setIncludeFontPadding(false);
        group.addView(labelView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        LinearLayout swatches = new LinearLayout(this);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        swatches.setClipChildren(false);
        swatches.setClipToPadding(false);
        SettingsColorSwatchView[] swatchButtons = new SettingsColorSwatchView[colors.length];
        for (int index = 0; index < colors.length; index++) {
            String color = colors[index];
            SettingsColorSwatchView swatch = new SettingsColorSwatchView(
                    this,
                    color,
                    false);
            swatch.setContentDescription(label + " " + color);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(32));
            params.rightMargin = dp(1);
            swatches.addView(swatch, params);
            swatchButtons[index] = swatch;
            swatch.setOnClickListener(view -> {
                target.set(color);
                onDraftChanged();
            });
        }
        SettingsColorSwatchView custom = new SettingsColorSwatchView(
                this,
                customFallback,
                true);
        custom.setContentDescription(getString(R.string.cd_custom_color, label));
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        swatches.addView(custom, customParams);
        custom.setOnClickListener(view -> SettingsColorDialog.show(
                this,
                custom,
                getString(R.string.dialog_custom_color_panel_title),
                SettingsColorPalette.customSeed(
                        target.get(),
                        colors,
                        customFallback),
                customFallback,
                color -> {
                    target.set(color);
                    onDraftChanged();
                }));
        paletteRows.add(new PaletteRow(swatchButtons, custom, target, colors));
        updatePaletteSelections();
        group.addView(swatches, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return group;
    }

    /** Mockup palette selection ring: the active swatch gets a gold ring + slight scale-up. */
    private void updatePaletteSelections() {
        for (PaletteRow row : paletteRows) {
            String value = LyricUiConfig.sanitizeColor(
                    row.target.get(),
                    "#FFFFFF").toUpperCase(Locale.ROOT);
            boolean customSelected = true;
            for (int index = 0; index < row.swatches.length; index++) {
                boolean selected = row.colors[index].equalsIgnoreCase(value);
                if (selected) customSelected = false;
                setSwatchSelected(row.swatches[index], selected);
            }
            setSwatchSelected(row.custom, customSelected);
        }
    }

    private void setSwatchSelected(SettingsColorSwatchView swatch, boolean selected) {
        swatch.setSelectedState(selected);
    }

    private View labeledMaterialSeek(String label, Slider slider, String suffix) {
        LinearLayout group = new LinearLayout(this);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setPadding(dp(17), dp(2), dp(12), dp(2));
        group.setMinimumHeight(dp(48));
        TextView title = text(label, 12.5f, settingsTextColor());
        title.setIncludeFontPadding(false);
        TextView value = text("", 10.5f, 0xFF9A6A12);
        value.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        group.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.15f));
        slider.setContentDescription(label);
        group.addView(slider, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1.1f));
        group.addView(value, new LinearLayout.LayoutParams(dp(56), dp(48)));
        Runnable update = () -> value.setText(String.format(
                Locale.ROOT, "%d%s", materialProgress(slider), suffix));
        slider.setTag(new SeekValueLabel(update));
        update.run();
        return group;
    }

    private View labeledMaterialHalfDpSeek(String label, Slider slider) {
        LinearLayout group = new LinearLayout(this);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setPadding(dp(17), dp(2), dp(12), dp(2));
        group.setMinimumHeight(dp(48));
        TextView title = text(label, 12.5f, settingsTextColor());
        title.setIncludeFontPadding(false);
        TextView value = text("", 10.5f, 0xFF9A6A12);
        value.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        group.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.15f));
        slider.setContentDescription(label);
        group.addView(slider, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1.1f));
        group.addView(value, new LinearLayout.LayoutParams(dp(56), dp(48)));
        Runnable update = () -> value.setText(String.format(
                Locale.ROOT,
                "%.1f dp",
                materialProgress(slider) * 0.5f));
        slider.setTag(new SeekValueLabel(update));
        update.run();
        return group;
    }

    private Slider materialSeek(int min, int max) {
        Slider slider = new Slider(this);
        slider.setValueTo(max);
        slider.setValueFrom(min);
        slider.setStepSize(1f);
        slider.setValue(min);
        slider.setTickVisible(false);
        slider.setLabelBehavior(MATERIAL_SLIDER_LABEL_BEHAVIOR_GONE);
        slider.setTrackHeight(dp(4));
        slider.setTrackActiveTintList(ColorStateList.valueOf(getColor(R.color.settings_primary)));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(0x21344455));
        slider.setThumbRadius(dp(8));
        slider.setThumbTintList(ColorStateList.valueOf(getColor(R.color.settings_primary)));
        slider.setThumbStrokeColor(ColorStateList.valueOf(Color.WHITE));
        slider.setThumbStrokeWidth(dp(2.5f));
        slider.setHaloRadius(dp(16));
        slider.setHaloTintList(ColorStateList.valueOf(0x3DF2C14E));
        slider.setContentDescription(getString(R.string.cd_slider));
        return slider;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        return spinner;
    }

    private View labeledSegmented(String label, SegmentedControl group) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setPadding(dp(17), dp(7), dp(12), dp(7));
        wrapper.setMinimumHeight(dp(48));
        TextView title = text(label, 13, settingsTextColor());
        title.setIncludeFontPadding(false);
        wrapper.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.8f));
        wrapper.addView(group, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.7f));
        return wrapper;
    }

    private MaterialButton refreshDropdownButton() {
        MaterialButton button = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setTextSize(12f);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(12));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        button.setStrokeColor(ColorStateList.valueOf(0x24344455));
        button.setStrokeWidth(dp(1));
        refreshRateSelection = 0;
        button.setText(refreshRateLabels()[0]);
        button.setIcon(androidx.core.content.ContextCompat.getDrawable(
                this,
                R.drawable.ic_expand_more));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_END);
        button.setIconTint(ColorStateList.valueOf(0xFF9A6A12));
        button.setTextColor(ColorStateList.valueOf(0xFF2B3440));
        return button;
    }

    private void showRefreshRateMenu() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable menuBackground = new GradientDrawable();
        menuBackground.setColor(Color.WHITE);
        menuBackground.setCornerRadius(dp(14));
        menuBackground.setStroke(dp(1), 0x1A1B222C);
        menu.setBackground(menuBackground);

        PopupWindow popup = new PopupWindow(
                menu,
                dp(138),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));

        String[] labels = refreshRateLabels();
        for (int index = 0; index < labels.length; index++) {
            final int selectedIndex = index;
            boolean selected = index == refreshRateSelection;
            TextView item = text(
                    labels[index],
                    12,
                    selected ? 0xFF9A6A12 : 0xFF5C6774);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            item.setPadding(dp(14), 0, dp(14), 0);
            GradientDrawable itemBackground = new GradientDrawable();
            itemBackground.setColor(selected ? 0x2EF2C14E : Color.TRANSPARENT);
            itemBackground.setCornerRadius(dp(10));
            item.setBackground(itemBackground);
            item.setOnClickListener(view -> {
                if (selectedIndex != refreshRateSelection) {
                    refreshRateSelection = selectedIndex;
                    refreshRate.setText(refreshRateLabels()[selectedIndex]);
                    onDraftChanged();
                }
                popup.dismiss();
            });
            menu.addView(item, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(40)));
        }
        popup.showAsDropDown(refreshRate, refreshRate.getWidth() - dp(138), dp(6));
    }

    /** Mockup refresh row: label with a small hint above the dropdown control. */
    private View refreshLimitRow(MaterialButton control) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(17), dp(4), dp(12), dp(4));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(
                getString(R.string.setting_refresh_limit),
                13,
                settingsTextColor());
        title.setIncludeFontPadding(false);
        labels.addView(title, matchWrap());
        TextView sub = text(
                getString(R.string.refresh_hint_short),
                9.5f,
                0x99000000);
        sub.setIncludeFontPadding(false);
        labels.addView(sub, matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        row.addView(control, new LinearLayout.LayoutParams(dp(132), dp(42)));
        return row;
    }

    private int checkedIndex(SegmentedControl control) {
        return control == null ? 0 : control.selectedIndex();
    }

    private void checkIndex(SegmentedControl control, int index) {
        if (control != null) control.select(index, false);
    }

    private EditText numberInput(String hint) {
        EditText input = new EditText(this);
        disableAutofill(input);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(11.5f);
        input.setTextColor(settingsTextColor());
        input.setHintTextColor(getColor(R.color.settings_text_muted));
        input.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), 0x42344455);
        input.setBackground(background);
        return input;
    }

    private View conditionalCardRow(View row) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        addCardDivider(container);
        container.addView(row, matchWrap());
        return container;
    }

    private View numberInputRow(String label, EditText input) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(17), dp(6), dp(13), dp(6));
        TextView labelView = text(label, 13, settingsTextColor());
        labelView.setIncludeFontPadding(false);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        row.addView(input, new LinearLayout.LayoutParams(dp(150), dp(40)));
        return row;
    }

    private int readInt(EditText input) {
        try { return Integer.parseInt(input.getText().toString().trim()); }
        catch (RuntimeException ignored) { return 0; }
    }

    private View linkRow(int iconRes, String title, String subtitle, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(17), dp(10), dp(13), dp(10));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(12));
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x14000000),
                null,
                mask));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getColor(R.color.settings_secondary));
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(0x19177286);
        iconBackground.setCornerRadius(dp(10));
        icon.setBackground(iconBackground);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        iconParams.rightMargin = dp(12);
        row.addView(icon, iconParams);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 12.5f, settingsTextColor());
        titleView.setTypeface(Typeface.create(Typeface.DEFAULT, 500));
        column.addView(titleView, matchWrap());
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = text(subtitle, 9.5f, 0xFF8A919C);
            subtitleView.setPadding(0, dp(2), 0, 0);
            column.addView(subtitleView, matchWrap());
        }
        row.addView(column, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(0xFF8A919C);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        chevronParams.leftMargin = dp(8);
        row.addView(chevron, chevronParams);
        row.setOnClickListener(view -> onClick.run());
        return row;
    }

    private void installKeyboardAvoidance(
            ScrollView scrollView,
            FrameLayout root,
            FrameLayout previewAnchor,
            View preview) {
        View decorRoot = getWindow().getDecorView();
        Rect visibleFrame = new Rect();
        decorRoot.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        decorRoot.getWindowVisibleDisplayFrame(visibleFrame);
                        int rootHeight = decorRoot.getRootView().getHeight();
                        int keyboardHeight = Math.max(0, rootHeight - visibleFrame.bottom);
                        keyboardVisible = keyboardHeight > dp(120);
                        // Keep the fixed action bar at the screen bottom instead of lifting
                        // it above the keyboard, where it would cover the focused input.
                        if (bottomActionBar != null) {
                            if (keyboardVisible) {
                                keyboardCloseShowScheduled = false;
                                decorRoot.removeCallbacks(showBottomBarAfterKeyboardClose);
                                keyboardLastContentHeight = root.getHeight();
                                keyboardSettledFrames = 0;
                                bottomActionBar.setVisibility(View.GONE);
                            } else if (keyboardLastContentHeight >= 0
                                    && Math.abs(root.getHeight() - keyboardLastContentHeight)
                                    <= dp(2)) {
                                if (!keyboardCloseShowScheduled) {
                                    keyboardCloseShowScheduled = true;
                                    decorRoot.postDelayed(
                                            showBottomBarAfterKeyboardClose,
                                            320L);
                                }
                                // Content height stopped changing (keyboard closed or settled).
                                keyboardSettledFrames++;
                                keyboardLastContentHeight = root.getHeight();
                                if (keyboardSettledFrames >= 2) {
                                    keyboardCloseShowScheduled = false;
                                    decorRoot.removeCallbacks(showBottomBarAfterKeyboardClose);
                                    bottomActionBar.setVisibility(View.VISIBLE);
                                }
                            } else {
                                if (!keyboardCloseShowScheduled) {
                                    keyboardCloseShowScheduled = true;
                                    decorRoot.postDelayed(
                                            showBottomBarAfterKeyboardClose,
                                            320L);
                                }
                                // The content area is still animating (keyboard closing):
                                // keep the bar hidden so it cannot flash mid-screen.
                                keyboardLastContentHeight = root.getHeight();
                                keyboardSettledFrames = 0;
                            }
                        }
                        int bottomPadding = keyboardVisible ? keyboardHeight + dp(24) : 0;
                        if (scrollView.getPaddingBottom() != bottomPadding) {
                            scrollView.setPadding(
                                    scrollView.getPaddingLeft(),
                                    scrollView.getPaddingTop(),
                                    scrollView.getPaddingRight(),
                                    bottomPadding);
                        }
                        View focused = getCurrentFocus();
                        if (keyboardVisible && focused == screenTimeoutSeconds) {
                            scrollView.postDelayed(
                                    () -> scrollFocusedInputIntoView(scrollView),
                                    80L);
                        }
                    }
                });
    }

    private void installKeyboardFocusRecovery(ScrollView scrollView, View input) {
        if (input == null) return;
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) return;
            cancelAutofillSession();
            scrollView.postDelayed(() -> scrollFocusedInputIntoView(scrollView), 120L);
            scrollView.postDelayed(() -> scrollFocusedInputIntoView(scrollView), 320L);
        });
    }

    private void scrollFocusedInputIntoView(ScrollView scrollView) {
        View focused = getCurrentFocus();
        if (focused == null) return;
        int[] focusedLocation = new int[2];
        int[] scrollLocation = new int[2];
        focused.getLocationOnScreen(focusedLocation);
        scrollView.getLocationOnScreen(scrollLocation);
        int focusedTop = focusedLocation[1];
        int focusedBottom = focusedTop + focused.getHeight();
        Rect visibleFrame = new Rect();
        scrollView.getWindowVisibleDisplayFrame(visibleFrame);
        int visibleTop = Math.max(visibleFrame.top, scrollLocation[1] + dp(24));
        int visibleBottom = visibleFrame.bottom - dp(24);
        if (visibleBottom <= visibleTop) return;
        if (focusedBottom > visibleBottom) {
            scrollView.smoothScrollBy(0, focusedBottom - visibleBottom);
        } else if (focusedTop < visibleTop) {
            scrollView.smoothScrollBy(0, focusedTop - visibleTop);
        }
    }

    private void disableAutofill(View view) {
        if (view == null) return;
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if (view instanceof EditText) {
            EditText input = (EditText) view;
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            input.setAutofillHints((String[]) null);
            input.setSaveEnabled(false);
            input.setOnFocusChangeListener((focusedView, hasFocus) -> {
                if (hasFocus) cancelAutofillSession();
            });
        }
    }

    private static final class PaletteRow {
        final SettingsColorSwatchView[] swatches;
        final SettingsColorSwatchView custom;
        final PaletteTarget target;
        final String[] colors;

        PaletteRow(
                SettingsColorSwatchView[] swatches,
                SettingsColorSwatchView custom,
                PaletteTarget target,
                String[] colors) {
            this.swatches = swatches;
            this.custom = custom;
            this.target = target;
            this.colors = colors;
        }
    }

    /** Mutable color value holder; the inline hex EditText now lives inside the picker. */
    private static final class PaletteTarget {
        private String value;

        PaletteTarget(String value) {
            this.value = value;
        }

        String get() {
            return value;
        }

        void set(String value) {
            this.value = value;
        }
    }

    private static final class TopUiBoundary {
        final int bottomOnScreen;
        final String source;

        TopUiBoundary(int bottomOnScreen, String source) {
            this.bottomOnScreen = Math.max(0, bottomOnScreen);
            this.source = source == null || source.isEmpty() ? "unknown" : source;
        }
    }

    private static final class SeekValueLabel {
        private final Runnable updater;
        SeekValueLabel(Runnable updater) { this.updater = updater; }
        void update() { updater.run(); }
    }

    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        private final java.util.function.IntConsumer selected;
        SimpleItemSelectedListener(java.util.function.IntConsumer selected) { this.selected = selected; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { selected.accept(position); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }

}
