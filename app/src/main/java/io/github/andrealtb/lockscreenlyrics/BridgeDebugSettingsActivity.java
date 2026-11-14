package io.github.andrealtb.lockscreenlyrics;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugConfig;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BuildIdentity;

public final class BridgeDebugSettingsActivity extends SettingsBaseActivity {
    private static final long APPLY_TIMEOUT_MS = 2_500L;
    private static final long MODULE_STATUS_TIMEOUT_MS = 2_500L;

    private SharedPreferences preferences;
    private MaterialSwitch master;
    private MaterialSwitch bootstrap;
    private MaterialSwitch media;
    private MaterialSwitch lyric;
    private MaterialSwitch renderer;
    private MaterialSwitch aod;
    private MaterialSwitch playerSpecial;
    private MaterialSwitch performance;
    private TextView statusView;
    private TextView identityView;
    private TextView runtimeIdentityView;
    private java.util.concurrent.ExecutorService identityWorker;
    private java.util.concurrent.Future<?> identityTask;
    private String installedBuildId = BuildIdentity.UNKNOWN;
    private Bundle runtimeStatus;
    private final java.util.Map<String, String> environmentFacts = new java.util.LinkedHashMap<>();
    private io.github.andrealtb.lockscreenlyrics.diagnostics.AppEnvironmentObserver environmentObserver;
    private ShortDiagnosticsControls shortDiagnostics;
    private Bundle shortDiagnosticsRestore;
    private boolean binding;
    private boolean moduleStatusQueryPending;
    private long pendingSettingsRevision = -1L;
    private long appliedRevision = -1L;
    private boolean appliedMaster;
    private String appliedAreas = "unknown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(BridgeDebugConfig.PREFS_NAME, MODE_PRIVATE);
        shortDiagnosticsRestore = savedInstanceState;
        shortDiagnostics = new ShortDiagnosticsControls(this, () -> new java.util.LinkedHashMap<>(environmentFacts));
        setTitle(R.string.debug_settings_title);
        setContentView(createContent());
        bind(BridgeDebugConfig.load(preferences));
        queryModuleStatus();
        environmentObserver = new io.github.andrealtb.lockscreenlyrics.diagnostics.AppEnvironmentObserver(this, facts -> {
            environmentFacts.putAll(facts);
            identityView.setText(BuildIdentity.format(new BuildIdentity.Snapshot(environmentFacts)));
        });
        identityWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
        Context identityContext = getApplicationContext();
        identityTask = identityWorker.submit(() -> {
            BuildIdentity.Snapshot snapshot = BuildIdentity.capture(identityContext);
            String text = BuildIdentity.format(snapshot);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                environmentFacts.putAll(snapshot.facts);
                identityView.setText(BuildIdentity.format(new BuildIdentity.Snapshot(environmentFacts)));
                installedBuildId = snapshot.facts.getOrDefault("installed_build", BuildIdentity.UNKNOWN);
                updateRuntimeIdentity();
            });
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        if (shortDiagnostics != null) shortDiagnostics.save(out);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (shortDiagnostics != null) shortDiagnostics.activityResult(request, result, data);
    }

    @Override
    protected void onStart() { super.onStart(); if (environmentObserver != null) environmentObserver.start(); }

    @Override
    protected void onStop() { if (environmentObserver != null) environmentObserver.stop(); super.onStop(); }

    @Override
    protected void onDestroy() {
        if (environmentObserver != null) environmentObserver.close();
        if (shortDiagnostics != null) shortDiagnostics.close();
        if (identityTask != null) identityTask.cancel(true);
        if (identityWorker != null) identityWorker.shutdownNow();
        super.onDestroy();
    }

    private android.view.View createContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int screenPadding = settingsScreenPadding();
        content.setPadding(screenPadding, screenPadding, screenPadding,
                settingsScreenBottomPadding() + dp(72));
        content.setBackgroundColor(settingsBackgroundColor());
        installSettingsInsets(content);

        TextView description = text(getString(R.string.debug_settings_desc), 14, 0xFF5F6368);
        description.setPadding(dp(4), dp(4), dp(4), dp(14));
        content.addView(description, matchWrap());
        content.addView(shortDiagnostics.create(shortDiagnosticsRestore), marginBottom(dp(12)));

        LinearLayout masterCard = card();
        master = toggle(getString(R.string.debug_master), false);
        master.setOnCheckedChangeListener((view, checked) -> {
            if (binding) {
                return;
            }
            setAreaEnabled(checked);
        });
        masterCard.addView(master, matchWrap());
        content.addView(masterCard, marginBottom(dp(12)));

        LinearLayout areas = card();
        areas.addView(section(getString(R.string.debug_areas_title)));
        bootstrap = areaToggle(R.string.debug_area_bootstrap);
        media = areaToggle(R.string.debug_area_media);
        lyric = areaToggle(R.string.debug_area_lyric);
        renderer = areaToggle(R.string.debug_area_renderer);
        aod = areaToggle(R.string.debug_area_aod);
        playerSpecial = areaToggle(R.string.debug_area_player_special);
        performance = areaToggle(R.string.debug_area_performance);
        areas.addView(bootstrap, matchWrap());
        areas.addView(media, matchWrap());
        areas.addView(lyric, matchWrap());
        areas.addView(renderer, matchWrap());
        areas.addView(aod, matchWrap());
        areas.addView(playerSpecial, matchWrap());
        areas.addView(performance, matchWrap());
        content.addView(areas, marginBottom(dp(12)));

        LinearLayout statusCard = paddedCard();
        identityView = text("构建/环境快照：后台采集中…", 12, 0xFF5F6368);
        identityView.setTextIsSelectable(true);
        identityView.setPadding(0, 0, 0, dp(8));
        statusCard.addView(identityView, matchWrap());
        runtimeIdentityView = text(BuildIdentity.runtimeReport(null), 12, 0xFF5F6368);
        runtimeIdentityView.setTextIsSelectable(true);
        statusCard.addView(runtimeIdentityView, matchWrap());
        statusView = text(getString(R.string.debug_status_unknown), 13, 0xFF5F6368);
        statusCard.addView(statusView, matchWrap());
        TextView restartHint = text(getString(R.string.debug_restart_hint), 13, 0xFF5F6368);
        restartHint.setPadding(0, dp(8), 0, 0);
        statusCard.addView(restartHint, matchWrap());
        content.addView(statusCard, marginBottom(dp(12)));

        Button save = button(getString(R.string.debug_save));
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
                getString(R.string.debug_settings_title),
                null,
                this::finish), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                settingsActionBarHeight()));
        page.addView(stage, new LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return page;
    }

    private MaterialSwitch areaToggle(int labelRes) {
        MaterialSwitch view = toggle(getString(labelRes), true);
        view.setPadding(0, 0, 0, 0);
        return view;
    }

    private void bind(BridgeDebugConfig config) {
        binding = true;
        master.setChecked(config.masterEnabled);
        bootstrap.setChecked(config.bootstrap);
        media.setChecked(config.media);
        lyric.setChecked(config.lyric);
        renderer.setChecked(config.renderer);
        aod.setChecked(config.aod);
        playerSpecial.setChecked(config.playerSpecial);
        performance.setChecked(config.performance);
        setAreaEnabled(config.masterEnabled);
        binding = false;
        updateStatus();
    }

    private void setAreaEnabled(boolean enabled) {
        bootstrap.setEnabled(enabled);
        media.setEnabled(enabled);
        lyric.setEnabled(enabled);
        renderer.setEnabled(enabled);
        aod.setEnabled(enabled);
        playerSpecial.setEnabled(enabled);
        performance.setEnabled(enabled);
    }

    private BridgeDebugConfig readDraft(long revision) {
        return new BridgeDebugConfig(
                BridgeDebugConfig.SCHEMA_VERSION,
                revision,
                master.isChecked(),
                bootstrap.isChecked(),
                media.isChecked(),
                lyric.isChecked(),
                renderer.isChecked(),
                aod.isChecked(),
                playerSpecial.isChecked(),
                performance.isChecked());
    }

    private void save() {
        long revision = LyricUiSettings.newSettingsRevision();
        BridgeDebugConfig config = readDraft(revision);
        BridgeDebugConfig.save(preferences, config);
        pendingSettingsRevision = revision;
        Intent intent = config.putExtras(
                new Intent(LyricUiSettings.ACTION_DEBUG_SETTINGS_CHANGED)
                        .setPackage("com.android.systemui")
                        .putExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, revision)
                        .putExtra(
                                LyricUiSettings.EXTRA_SETTINGS_SOURCE,
                                LyricUiSettings.SOURCE_DEBUG_SETTINGS)
                        .putExtra(
                                LyricUiSettings.EXTRA_RESULT_RECEIVER,
                                ResultReceiverTransport.forRemoteProcess(
                                        new DebugApplyResultReceiver(revision))));
        sendBroadcast(intent);
        Toast.makeText(this, getString(R.string.debug_saved), Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(() -> {
            if (pendingSettingsRevision != revision) {
                return;
            }
            pendingSettingsRevision = -1L;
            Toast.makeText(this, getString(R.string.debug_saved_pending), Toast.LENGTH_LONG).show();
        }, APPLY_TIMEOUT_MS);
    }

    private void queryModuleStatus() {
        moduleStatusQueryPending = true;
        try {
            Intent intent = new Intent(LyricUiSettings.ACTION_REQUEST_MODULE_STATUS)
                    .setPackage("com.android.systemui")
                    .putExtra(
                            LyricUiSettings.EXTRA_RESULT_RECEIVER,
                            ResultReceiverTransport.forRemoteProcess(
                                    new DebugModuleStatusReceiver()));
            intent.addFlags(
                    Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
            sendBroadcast(intent);
            getWindow().getDecorView().postDelayed(() -> {
                if (!moduleStatusQueryPending) {
                    return;
                }
                moduleStatusQueryPending = false;
                appliedRevision = -1L;
                updateStatus();
            }, MODULE_STATUS_TIMEOUT_MS);
        } catch (RuntimeException ignored) {
            moduleStatusQueryPending = false;
            appliedRevision = -1L;
            updateStatus();
        }
    }

    private void updateRuntimeIdentity() {
        if (isDestroyed() || runtimeIdentityView == null) return;
        try {
            runtimeIdentityView.setText(BuildIdentity.runtimeReport(runtimeStatus, installedBuildId) + "\n"
                    + io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.formatStatus(runtimeStatus)
                    + "\n" + loadedComponentReport(runtimeStatus));
        } catch (RuntimeException incompatibleReply) {
            runtimeIdentityView.setText("注入进程/能力状态：unknown（回包不兼容）");
        }
    }

    @SuppressWarnings("deprecation")
    private String loadedComponentReport(Bundle data) {
        if (data == null) return "已观测加载器：unknown";
        java.util.ArrayList<Bundle> rows = data.getParcelableArrayList(
                io.github.andrealtb.lockscreenlyrics.diagnostics.LoadedComponentFingerprints.KEY);
        if (rows == null) return "已观测加载器：unknown（旧回包）";
        StringBuilder text = new StringBuilder("已观测加载器背后文件（不是指令内存认证）：");
        for (Bundle row : rows) {
            String kind = row.getString("kind", "unknown");
            String hash = row.getString("contentSetSha256", "unknown");
            String installed = environmentFacts.getOrDefault(kind, "unknown");
            String compare = hash.equals("unknown") || !installed.contains("content_set_sha256=") ? "unknown"
                    : installed.contains("content_set_sha256=" + hash) ? "SAME_CONTENT_SET" : "DIFFERENT_OR_DIFFERENT_CONTAINER_SET";
            text.append('\n').append(kind).append(" epoch=").append(row.getLong("loaderEpoch", -1))
                    .append(" state=").append(row.getString("state", "unknown"))
                    .append(" sha256=").append(hash).append(" comparison=").append(compare)
                    .append(" measuredAt=").append(row.getLong("fingerprintedAt", -1));
        }
        return text.toString();
    }

    private void updateStatus() {
        if (statusView == null) {
            return;
        }
        if (appliedRevision < 0L) {
            statusView.setText(getString(R.string.debug_status_unknown));
            return;
        }
        statusView.setText(getString(
                R.string.debug_status_applied,
                appliedRevision,
                appliedMaster
                        ? getString(R.string.debug_status_on)
                        : getString(R.string.debug_status_off),
                appliedAreas));
    }

    private final class DebugApplyResultReceiver extends ResultReceiver {
        private final long revision;

        DebugApplyResultReceiver(long revision) {
            super(new Handler(getMainLooper()));
            this.revision = revision;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (pendingSettingsRevision != revision || resultData == null) {
                return;
            }
            if (resultData.getLong(LyricUiSettings.RESULT_CONFIG_REVISION, -1L) != revision) {
                return;
            }
            pendingSettingsRevision = -1L;
            boolean applied = resultCode == LyricUiSettings.RESULT_SETTINGS_APPLIED
                    && resultData.getBoolean(LyricUiSettings.RESULT_APPLIED, false);
            appliedRevision = resultData.getLong(BridgeDebugConfig.KEY_REVISION, revision);
            appliedMaster = resultData.getBoolean(BridgeDebugConfig.KEY_MASTER, false);
            appliedAreas = resultData.getString("debug_areas", "unknown");
            updateStatus();
            Toast.makeText(
                    BridgeDebugSettingsActivity.this,
                    applied
                            ? getString(R.string.debug_applied)
                            : getString(
                                    R.string.debug_rejected,
                                    resultData.getString(LyricUiSettings.RESULT_REASON, "")),
                    applied ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        }
    }

    private final class DebugModuleStatusReceiver extends ResultReceiver {
        DebugModuleStatusReceiver() {
            super(new Handler(getMainLooper()));
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (!moduleStatusQueryPending || resultData == null) {
                return;
            }
            moduleStatusQueryPending = false;
            runtimeStatus = new Bundle(resultData);
            updateRuntimeIdentity();
            appliedRevision = resultData.getLong(BridgeDebugConfig.KEY_REVISION, -1L);
            appliedMaster = resultData.getBoolean(BridgeDebugConfig.KEY_MASTER, false);
            appliedAreas = resultData.getString("debug_areas", "unknown");
            updateStatus();
        }
    }
}
