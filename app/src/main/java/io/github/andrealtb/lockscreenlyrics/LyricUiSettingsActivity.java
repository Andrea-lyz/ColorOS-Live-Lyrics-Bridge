package io.github.andrealtb.lockscreenlyrics;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.ResultReceiver;
import android.text.InputType;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LyricUiSettingsActivity extends Activity {
    private static final String TAG = "LockscreenLyrics";
    private static final String STATE_DRAFT_CONFIG = "draft_config";
    private static final int BACKGROUND = 0xFFF6F6F8;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF111111;
    private static final long PREVIEW_SCROLL_LAYER_RELEASE_DELAY_MS = 160L;
    private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9A-Fa-f]{6}");

    private SharedPreferences preferences;
    private boolean binding;
    private LyricUiConfig draft;
    private Spinner presetSpinner;
    private LightweightSlider opacity;
    private Switch blurEnabled;
    private LightweightSlider blurRadius;
    private Switch scaleEnabled;
    private LightweightSlider inactiveScale;
    private Switch glowEnabled;
    private LightweightSlider glowIntensity;
    private LightweightSlider glowRadius;
    private EditText primaryColor;
    private EditText glowColor;
    private Spinner motionMode;
    private Switch passiveVerticalPan;
    private Switch translationMarquee;
    private Spinner refreshRate;
    private int[] refreshRateValues;
    private Switch lineTimedProgress;
    private Switch translationProgress;
    private Switch screenTimeout;
    private EditText screenTimeoutSeconds;
    private LightweightSlider mainFontSize;
    private LightweightSlider translationFontRatio;
    private Spinner fontWeight;
    private Spinner alignment;
    private LightweightSlider lineSpacing;
    private TextView previewMain;
    private LinearLayout previewActiveSlot;
    private TextView previewTranslation;
    private LinearLayout previewSecondarySlotOne;
    private TextView previewSecondaryOne;
    private TextView previewSecondaryTranslationOne;
    private LinearLayout previewSecondarySlotTwo;
    private TextView previewSecondaryTwo;
    private TextView previewSecondaryTranslationTwo;
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
    private int ignoredPresetSelection = -1;
    private long pendingSettingsRevision = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        preferences = getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, MODE_PRIVATE);
        draft = LyricUiConfigRepository.load(preferences);
        if (savedInstanceState != null) {
            LyricUiConfig restored = LyricUiConfigRepository.decodeSnapshot(
                    savedInstanceState.getBundle(STATE_DRAFT_CONFIG),
                    draft);
            if (restored != null) draft = restored;
        }
        View content = createContent();
        setContentView(content);
        bind(draft);
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
        releaseScrollCachedPreviewLayer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releaseScrollCachedPreviewLayer();
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        configureSettingsWindowRefreshRate(window);
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
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        content.setBackgroundColor(BACKGROUND);
        disableAutofill(content);
        installInsets(content);

        TextView notice = text("修改期间仅更新本页预览；点击保存后一次校验并应用。", 14, 0xFF7A4C00);
        notice.setPadding(dp(14), dp(12), dp(14), dp(12));
        notice.setBackgroundColor(0xFFFFF4DE);
        content.addView(notice, marginBottom(dp(18)));

        content.addView(section("外观预设"));
        LinearLayout presetCard = card();
        presetSpinner = spinner(new String[]{"默认", "柔和", "醒目", "极简", "自定义"});
        presetCard.addView(row("风格预设", presetSpinner));
        Button reset = button("恢复默认外观");
        reset.setOnClickListener(view -> bind(readDraft().resetAppearance()));
        presetCard.addView(reset, matchWrap());
        content.addView(presetCard, marginBottom(dp(16)));

        content.addView(section("预览"));
        LinearLayout preview = card();
        previewAnchor = new FrameLayout(this);
        previewActiveSlot = new LinearLayout(this);
        previewActiveSlot.setOrientation(LinearLayout.VERTICAL);
        previewActiveSlot.setGravity(Gravity.CENTER_VERTICAL);
        previewSecondarySlotOne = new LinearLayout(this);
        previewSecondarySlotOne.setOrientation(LinearLayout.VERTICAL);
        previewSecondarySlotOne.setGravity(Gravity.CENTER_VERTICAL);
        previewSecondarySlotTwo = new LinearLayout(this);
        previewSecondarySlotTwo.setOrientation(LinearLayout.VERTICAL);
        previewSecondarySlotTwo.setGravity(Gravity.CENTER_VERTICAL);
        previewMain = text("正在播放的主歌词", 22, Color.WHITE);
        previewTranslation = text("Current translation preview", 15, Color.WHITE);
        previewSecondaryOne = text("下一行歌词预览", 22, Color.WHITE);
        previewSecondaryTranslationOne = text("Next translation preview", 15, Color.WHITE);
        previewSecondaryTwo = text("再下一行歌词预览", 22, Color.WHITE);
        previewSecondaryTranslationTwo = text("Following translation preview", 15, Color.WHITE);
        previewMain.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        preview.setBackgroundColor(0xFF202124);
        previewActiveSlot.addView(previewMain, matchWrap());
        previewActiveSlot.addView(previewTranslation, matchWrap());
        preview.addView(previewActiveSlot, matchWrap());
        previewSecondarySlotOne.addView(previewSecondaryOne, matchWrap());
        previewSecondarySlotOne.addView(previewSecondaryTranslationOne, matchWrap());
        preview.addView(previewSecondarySlotOne, matchWrap());
        previewSecondarySlotTwo.addView(previewSecondaryTwo, matchWrap());
        previewSecondarySlotTwo.addView(previewSecondaryTranslationTwo, matchWrap());
        preview.addView(previewSecondarySlotTwo, matchWrap());
        previewAnchor.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        content.addView(previewAnchor, marginBottom(dp(16)));

        content.addView(section("绘制与动效"));
        LinearLayout appearance = card();
        opacity = seek(30, 100);
        appearance.addView(labeledSeek("非活动歌词不透明度", opacity, "%", 30));
        blurEnabled = toggle("模糊", false);
        appearance.addView(blurEnabled);
        blurRadius = seek(0, 16);
        appearance.addView(labeledSeek("模糊半径", blurRadius, " × 0.5px", 0));
        scaleEnabled = toggle("滚动缩放", false);
        appearance.addView(scaleEnabled);
        inactiveScale = seek(75, 100);
        appearance.addView(labeledSeek("非活动缩放", inactiveScale, "%", 75));
        glowEnabled = toggle("光晕", true);
        appearance.addView(glowEnabled);
        glowIntensity = seek(0, 100);
        appearance.addView(labeledSeek("光晕强度", glowIntensity, "%", 0));
        glowRadius = seek(10, 24);
        appearance.addView(labeledSeek("光晕半径（字号比例）", glowRadius, "%", 10));
        primaryColor = colorInput("歌词主色 #RRGGBB");
        glowColor = colorInput("光晕色 #RRGGBB");
        appearance.addView(primaryColor, matchWrap());
        appearance.addView(glowColor, matchWrap());
        motionMode = spinner(new String[]{"标准", "减少动态", "关闭"});
        appearance.addView(row("动效", motionMode));
        passiveVerticalPan = toggle("长歌词自动纵向浏览", true);
        translationMarquee = toggle("长翻译自动横向滚动", true);
        appearance.addView(passiveVerticalPan);
        appearance.addView(translationMarquee);
        content.addView(appearance, marginBottom(dp(16)));

        content.addView(section("刷新策略"));
        LinearLayout policy = card();
        buildRefreshRateOptions();
        refreshRate = spinner(refreshRateLabels());
        policy.addView(row("歌词刷新上限", refreshRate));
        TextView refreshRateHint = text(
                "仅限制歌词内容重绘，不会强制屏幕升频；系统降至 60 Hz 时歌词也会随之降至 60 帧。",
                12,
                0x99000000);
        refreshRateHint.setPadding(dp(4), 0, dp(4), dp(6));
        policy.addView(refreshRateHint, matchWrap());
        content.addView(policy, marginBottom(dp(16)));

        content.addView(section("兼容与屏幕"));
        LinearLayout compatibility = card();
        Button playerTranslationSettings = button("播放器翻译设置");
        playerTranslationSettings.setOnClickListener(view -> startActivity(
                new Intent(this, PlayerTranslationSettingsActivity.class)));
        Button openingCleanupSettings = button("歌词开头信息清理");
        openingCleanupSettings.setOnClickListener(view -> startActivity(
                new Intent(this, LyricOpeningCleanupSettingsActivity.class)));
        lineTimedProgress = toggle("普通逐行歌词进度", false);
        translationProgress = toggle("翻译进度", false);
        screenTimeout = toggle("歌词显示时保持屏幕点亮", true);
        screenTimeoutSeconds = numberInput("自定义秒数（留空则保持常亮）");
        compatibility.addView(playerTranslationSettings, matchWrap());
        compatibility.addView(openingCleanupSettings, matchWrap());
        compatibility.addView(lineTimedProgress);
        compatibility.addView(translationProgress);
        compatibility.addView(screenTimeout);
        compatibility.addView(screenTimeoutSeconds, matchWrap());
        content.addView(compatibility, marginBottom(dp(16)));

        content.addView(section("高级排版"));
        LinearLayout typography = card();
        mainFontSize = seek(18, 26);
        typography.addView(labeledSeek("主歌词字号", mainFontSize, " sp", 18));
        translationFontRatio = seek(55, 75);
        typography.addView(labeledSeek("翻译字号比例", translationFontRatio, "%", 55));
        fontWeight = spinner(new String[]{"跟随系统", "常规", "中等", "粗体"});
        typography.addView(row("字重", fontWeight));
        alignment = spinner(new String[]{"左对齐", "居中", "右对齐"});
        typography.addView(row("对齐", alignment));
        lineSpacing = seek(-10, 40);
        typography.addView(labeledHalfDpSeek("歌词行间距", lineSpacing));
        content.addView(typography, marginBottom(dp(16)));

        Button save = button("保存并应用");
        save.setTextColor(Color.WHITE);
        save.setBackgroundColor(TEXT);
        save.setOnClickListener(view -> save());
        content.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        installKeyboardAvoidance(scroll);
        installKeyboardFocusRecovery(scroll, screenTimeoutSeconds);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        installFloatingPreview(root, scroll, previewAnchor, preview);
        return root;
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
            params.leftMargin = dp(20);
            params.rightMargin = dp(20);
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

        int actionBarHeight = 0;
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBarHeight = actionBar.getHeight();
        }
        if (actionBarHeight <= 0) {
            TypedValue actionBarSize = new TypedValue();
            if (getTheme().resolveAttribute(
                    android.R.attr.actionBarSize,
                    actionBarSize,
                    true)) {
                actionBarHeight = TypedValue.complexToDimensionPixelSize(
                        actionBarSize.data,
                        getResources().getDisplayMetrics());
            }
        }
        Rect visibleFrame = new Rect();
        decor.getWindowVisibleDisplayFrame(visibleFrame);
        return new TopUiBoundary(
                Math.max(rootTopOnScreen, visibleFrame.top) + actionBarHeight,
                "actionbar-fallback");
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
        for (Switch toggle : new Switch[]{blurEnabled, scaleEnabled, glowEnabled,
                passiveVerticalPan, translationMarquee,
                lineTimedProgress, translationProgress, screenTimeout}) {
            toggle.setOnClickListener(changed);
        }
        for (LightweightSlider slider : valueSeekBars()) {
            slider.setOnProgressChangedListener((view, progress, fromUser) -> {
                Object tag = slider.getTag();
                if (tag instanceof SeekValueLabel) ((SeekValueLabel) tag).update();
                onDraftChanged();
            });
        }
        motionMode.setOnItemSelectedListener(new SimpleItemSelectedListener(p -> onDraftChanged()));
        refreshRate.setOnItemSelectedListener(new SimpleItemSelectedListener(p -> onDraftChanged()));
        fontWeight.setOnItemSelectedListener(new SimpleItemSelectedListener(p -> onDraftChanged()));
        alignment.setOnItemSelectedListener(new SimpleItemSelectedListener(p -> onDraftChanged()));
        primaryColor.addTextChangedListener(new SimpleTextWatcher(this::onDraftChanged));
        glowColor.addTextChangedListener(new SimpleTextWatcher(this::onDraftChanged));
    }

    private void onDraftChanged() {
        if (!binding) bindPreview(readDraft());
    }

    private LyricUiConfig readDraft() {
        int refreshIndex = refreshRate == null ? 0 : refreshRate.getSelectedItemPosition();
        int refresh = refreshIndex >= 0 && refreshIndex < refreshRateValues.length
                ? refreshRateValues[refreshIndex] : 0;
        return draft.buildUpon()
                .inactiveOpacityPercent(opacity.getProgress())
                .blurEnabled(blurEnabled.isChecked())
                .blurRadiusTenthsPx(blurRadius.getProgress() * 5)
                .scaleEnabled(scaleEnabled.isChecked())
                .inactiveScalePercent(inactiveScale.getProgress())
                .glowEnabled(glowEnabled.isChecked())
                .glowIntensityPercent(glowIntensity.getProgress())
                .glowRadiusPercent(glowRadius.getProgress())
                .primaryColor(primaryColor.getText().toString())
                .glowColor(glowColor.getText().toString())
                .motionMode(motionMode.getSelectedItemPosition())
                .passiveVerticalPanEnabled(passiveVerticalPan.isChecked())
                .translationMarqueeEnabled(translationMarquee.isChecked())
                .maxRefreshRateHz(refresh)
                .defaultTranslationEnabled(LyricUiConfigRepository.load(
                        preferences).defaultTranslationEnabled)
                .lineTimedProgressEnabled(lineTimedProgress.isChecked())
                .translationProgressEnabled(translationProgress.isChecked())
                .screenTimeoutEnabled(screenTimeout.isChecked())
                .screenTimeoutSeconds(readInt(screenTimeoutSeconds))
                .mainFontTenthsSp(mainFontSize.getProgress() * 10)
                .translationFontRatioPercent(translationFontRatio.getProgress())
                .fontWeight(fontWeight.getSelectedItemPosition())
                .alignment(alignment.getSelectedItemPosition())
                .lineSpacingTenthsDp(lineSpacing.getProgress() * 5)
                .build();
    }

    private void bind(LyricUiConfig config) {
        draft = config;
        binding = true;
        opacity.setProgress(config.inactiveOpacityPercent);
        blurEnabled.setChecked(config.blurEnabled);
        blurRadius.setProgress(config.blurRadiusTenthsPx / 5);
        scaleEnabled.setChecked(config.scaleEnabled);
        inactiveScale.setProgress(config.inactiveScalePercent);
        glowEnabled.setChecked(config.glowEnabled);
        glowIntensity.setProgress(config.glowIntensityPercent);
        glowRadius.setProgress(config.glowRadiusPercent);
        primaryColor.setText(config.primaryColor);
        glowColor.setText(config.glowColor);
        motionMode.setSelection(config.motionMode);
        passiveVerticalPan.setChecked(config.passiveVerticalPanEnabled);
        translationMarquee.setChecked(config.translationMarqueeEnabled);
        refreshRate.setSelection(indexOfRefresh(config.maxRefreshRateHz));
        lineTimedProgress.setChecked(config.lineTimedProgressEnabled);
        translationProgress.setChecked(config.translationProgressEnabled);
        screenTimeout.setChecked(config.screenTimeoutEnabled);
        screenTimeoutSeconds.setText(config.screenTimeoutSeconds <= 0
                ? "" : Integer.toString(config.screenTimeoutSeconds));
        mainFontSize.setProgress(config.mainFontTenthsSp / 10);
        translationFontRatio.setProgress(config.translationFontRatioPercent);
        fontWeight.setSelection(config.fontWeight);
        alignment.setSelection(config.alignment);
        lineSpacing.setProgress(config.lineSpacingTenthsDp / 5);
        updateSeekValueLabels();
        ignoredPresetSelection = LyricUiPreset.detect(config).ordinal();
        presetSpinner.setSelection(ignoredPresetSelection);
        binding = false;
        bindPreview(config);
    }

    private void updateSeekValueLabels() {
        for (LightweightSlider slider : valueSeekBars()) {
            Object tag = slider.getTag();
            if (tag instanceof SeekValueLabel) {
                ((SeekValueLabel) tag).update();
            }
        }
    }

    private LightweightSlider[] valueSeekBars() {
        return new LightweightSlider[]{opacity, blurRadius, inactiveScale,
                glowIntensity, glowRadius, mainFontSize, translationFontRatio,
                lineSpacing};
    }

    private void bindPreview(LyricUiConfig config) {
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
        float groupHeight = LyricUiLayoutPolicy.fontOuterHeight(
                mainMetrics.top,
                mainMetrics.bottom)
                + dp(2f)
                + LyricUiLayoutPolicy.fontOuterHeight(
                translationMetrics.top,
                translationMetrics.bottom);
        return previewSlotHeight(config, groupHeight, main.getPaint().getTextSize());
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

    private void save() {
        String primary = primaryColor.getText().toString().trim();
        String glow = glowColor.getText().toString().trim();
        if (!COLOR_PATTERN.matcher(primary).matches() || !COLOR_PATTERN.matcher(glow).matches()) {
            Toast.makeText(this, "颜色必须为 #RRGGBB", Toast.LENGTH_SHORT).show();
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
        logSettingsEvent(
                "settings-send",
                "Sent lyric UI settings"
                        + " | source=" + LyricUiSettings.SOURCE_MAIN_SETTINGS
                        + ", revision=" + revision
                        + ", alignment=" + config.alignment
                        + ", fontSp10=" + config.mainFontTenthsSp
                        + ", lineSpacingDp10=" + config.lineSpacingTenthsDp);
        Toast.makeText(this, "已保存，等待 SystemUI 确认", Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(() -> {
            if (pendingSettingsRevision != revision) return;
            pendingSettingsRevision = -1L;
            Toast.makeText(
                    this,
                    "已保存，但 SystemUI 未确认应用，请检查模块是否已启用",
                    Toast.LENGTH_LONG).show();
        }, 2_500L);
    }

    private ResultReceiver createApplyResultReceiver(long revision) {
        return new ResultReceiver(new Handler(getMainLooper())) {
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
                Toast.makeText(
                        LyricUiSettingsActivity.this,
                        applied ? "已保存并应用" : "SystemUI 拒绝了设置：" + reason,
                        applied ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            }
        };
    }

    private void logSettingsEvent(String event, String message) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        Log.i(TAG, LyricLogFormatter.format(
                getPackageName(),
                LyricLogFormatter.Area.SETTINGS,
                event,
                message));
    }

    private Typeface resolvePreviewTypeface(int weight) {
        if (weight == LyricUiConfig.WEIGHT_BOLD) return Typeface.DEFAULT_BOLD;
        if (weight == LyricUiConfig.WEIGHT_MEDIUM && Build.VERSION.SDK_INT >= 28) {
            return Typeface.create(Typeface.DEFAULT, 500, false);
        }
        return Typeface.DEFAULT;
    }

    private static void applyPreviewBlur(View view, boolean enabled, float radiusPx) {
        if (Build.VERSION.SDK_INT < 31 || view == null) return;
        if (enabled && radiusPx > 0f) {
            view.setRenderEffect(RenderEffect.createBlurEffect(
                    radiusPx,
                    radiusPx,
                    Shader.TileMode.CLAMP));
        } else {
            view.setRenderEffect(null);
        }
    }

    private static void setBottomMargin(View view, int margin) {
        if (view == null || !(view.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        if (params.bottomMargin == margin) return;
        params.bottomMargin = margin;
        view.setLayoutParams(params);
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
            labels[i] = refreshRateValues[i] == 0 ? "跟随屏幕" : refreshRateValues[i] + " Hz";
        }
        return labels;
    }

    private int indexOfRefresh(int value) {
        for (int i = 0; i < refreshRateValues.length; i++) if (refreshRateValues[i] == value) return i;
        return 0;
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(14), dp(10), dp(14), dp(12));
        view.setBackgroundColor(CARD);
        return view;
    }

    private TextView section(String title) {
        TextView view = text(title, 13, 0x99000000);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(4), dp(4), 0, dp(8));
        return view;
    }

    private LinearLayout row(String label, View control) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = text(label, 16, TEXT);
        text.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        text.setIncludeFontPadding(false);
        row.addView(text, new LinearLayout.LayoutParams(0, dp(54), 1f));
        row.addView(control, new LinearLayout.LayoutParams(dp(150), dp(54)));
        return row;
    }

    private View labeledSeek(
            String label,
            LightweightSlider slider,
            String suffix,
            int base) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(label, 15, TEXT);
        TextView value = text("", 13, 0x99000000);
        group.addView(title);
        group.addView(value);
        slider.setContentDescription(label);
        group.addView(slider, matchWrap());
        Runnable update = () -> value.setText(String.format(
                Locale.ROOT, "%d%s", slider.getProgress(), suffix));
        slider.setTag(new SeekValueLabel(update));
        update.run();
        return group;
    }

    private View labeledHalfDpSeek(String label, LightweightSlider slider) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(label, 15, TEXT);
        TextView value = text("", 13, 0x99000000);
        group.addView(title);
        group.addView(value);
        slider.setContentDescription(label);
        group.addView(slider, matchWrap());
        Runnable update = () -> value.setText(String.format(
                Locale.ROOT,
                "%.1f dp",
                slider.getProgress() * 0.5f));
        slider.setTag(new SeekValueLabel(update));
        update.run();
        return group;
    }

    private LightweightSlider seek(int min, int max) {
        LightweightSlider slider = new LightweightSlider(this);
        slider.setMin(min);
        slider.setMax(max);
        return slider;
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

    private EditText colorInput(String hint) {
        EditText input = new EditText(this);
        disableAutofill(input);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        return input;
    }

    private EditText numberInput(String hint) {
        EditText input = new EditText(this);
        disableAutofill(input);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
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

    private int readInt(EditText input) {
        try { return Integer.parseInt(input.getText().toString().trim()); }
        catch (RuntimeException ignored) { return 0; }
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

    private static void disableAutofill(View view) {
        if (view == null) return;
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if (view instanceof EditText) {
            ((EditText) view).setAutofillHints((String[]) null);
        }
    }

    @SuppressWarnings("deprecation")
    private void installInsets(View content) {
        int base = content.getPaddingTop();
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(view.getPaddingLeft(), base + insets.getSystemWindowInsetTop(),
                    view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
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

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable changed;
        SimpleTextWatcher(Runnable changed) { this.changed = changed; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
        @Override public void afterTextChanged(android.text.Editable editable) { }
    }
}
