package io.github.andrealtb.lockscreenlyrics;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkDisplaySettings;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkProviderDirectory;

/** Saved dynamic artwork settings. Every change applies at once; SystemUI reports what it runs. */
public final class ArtworkSettingsActivity extends SettingsBaseActivity {
    private static final long STATUS_TIMEOUT_MS = 2_500L;
    private static final long STATUS_REFRESH_MS = 3_000L;
    private static final int COLOR_READY = 0xFF2E7D32;
    private static final int COLOR_BUSY = 0xFFB7791F;
    private static final int COLOR_PROBLEM = 0xFFC62828;

    private final ExecutorService discovery = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(android.os.Looper.getMainLooper());
    private final Runnable statusRefresh = this::requestStatus;
    private ArtworkDisplaySettings settings;
    private ArtworkSettingsRepository.Selection selection;
    private MaterialSwitch master;
    private MaterialSwitch card;
    private MaterialSwitch immersive;
    private MaterialSwitch keepAwake;
    private TextView providerName;
    private TextView providerState;
    private Button providerSettings;
    private View statusDot;
    private TextView statusTitle;
    private TextView statusDetail;
    private boolean binding;
    private boolean resumed;
    private long statusRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.artwork_settings_title);
        setContentView(createContent());
        settings = ArtworkSettingsRepository.load(this);
        bind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        settings = ArtworkSettingsRepository.load(this);
        bind();
        refreshSelection();
        requestStatus();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(statusRefresh);
        ++statusRequest;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        discovery.shutdownNow();
        super.onDestroy();
    }

    private View createContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int screenPadding = settingsScreenPadding();
        content.setPadding(screenPadding, screenPadding, screenPadding, settingsScreenBottomPadding());
        content.setBackgroundColor(settingsBackgroundColor());
        installSettingsInsets(content);

        TextView description = text(getString(R.string.artwork_settings_desc), 14, 0xFF5F6368);
        description.setLineSpacing(0f, 1.2f);
        description.setPadding(dp(4), dp(4), dp(4), dp(14));
        content.addView(description, matchWrap());

        LinearLayout statusCard = paddedCard();
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(getColor(R.color.settings_text_muted));
        statusDot.setBackground(dot);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotParams.rightMargin = dp(10);
        statusRow.addView(statusDot, dotParams);
        statusTitle = text(getString(R.string.artwork_status_querying), 14, settingsTextColor());
        statusTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusRow.addView(statusTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusCard.addView(statusRow, matchWrap());
        statusDetail = text("", 12, getColor(R.color.settings_text_secondary));
        statusDetail.setLineSpacing(0f, 1.2f);
        statusDetail.setPadding(dp(19), dp(6), 0, 0);
        statusCard.addView(statusDetail, matchWrap());
        content.addView(statusCard, marginBottom(dp(12)));

        LinearLayout display = card();
        display.addView(section(R.drawable.ic_sec_artwork, getString(R.string.artwork_settings_section_display), "DISPLAY"));
        master = toggle(getString(R.string.artwork_settings_enable), false);
        master.setOnCheckedChangeListener((view, checked) -> { if (!binding) onMasterChanged(checked); });
        display.addView(master, matchWrap());
        addCardDivider(display);
        card = toggle(getString(R.string.artwork_settings_card), true);
        card.setOnCheckedChangeListener((view, checked) -> { if (!binding) save(settings.withCard(checked)); });
        display.addView(card, matchWrap());
        addCardDivider(display);
        immersive = toggle(getString(R.string.artwork_settings_large_cover), true);
        immersive.setOnCheckedChangeListener((view, checked) -> { if (!binding) save(settings.withImmersive(checked)); });
        display.addView(immersive, matchWrap());
        content.addView(display, marginBottom(dp(12)));

        LinearLayout source = card();
        source.addView(section(R.drawable.ic_sec_compat, getString(R.string.artwork_settings_section_source), "SOURCE"));
        LinearLayout providerBlock = new LinearLayout(this);
        providerBlock.setOrientation(LinearLayout.VERTICAL);
        providerBlock.setPadding(dp(17), dp(6), dp(17), dp(4));
        providerName = text(getString(R.string.artwork_settings_provider_none), 13.5f, settingsTextColor());
        providerName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        providerBlock.addView(providerName, matchWrap());
        providerState = text("", 11.5f, getColor(R.color.settings_text_secondary));
        providerState.setPadding(0, dp(3), 0, 0);
        providerBlock.addView(providerState, matchWrap());
        source.addView(providerBlock, matchWrap());
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(13), dp(4), dp(13), dp(4));
        Button choose = button(getString(R.string.artwork_settings_choose));
        choose.setOnClickListener(view -> chooseProvider(false));
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        chooseParams.rightMargin = dp(8);
        actions.addView(choose, chooseParams);
        providerSettings = button(getString(R.string.artwork_settings_provider_settings));
        providerSettings.setOnClickListener(view -> openProviderSettings());
        actions.addView(providerSettings, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        source.addView(actions, matchWrap());
        TextView sourceHint = text(getString(R.string.artwork_settings_source_hint), 11.5f, getColor(R.color.settings_text_secondary));
        sourceHint.setLineSpacing(0f, 1.2f);
        sourceHint.setPadding(dp(17), dp(4), dp(17), dp(14));
        source.addView(sourceHint, matchWrap());
        content.addView(source, marginBottom(dp(12)));

        LinearLayout screen = card();
        screen.addView(section(R.drawable.ic_sec_motion, getString(R.string.artwork_settings_section_screen), "SCREEN"));
        keepAwake = toggle(getString(R.string.artwork_settings_keep_awake), false);
        keepAwake.setOnCheckedChangeListener((view, checked) -> { if (!binding) save(settings.withKeepAwake(checked)); });
        screen.addView(keepAwake, matchWrap());
        TextView keepAwakeHint = text(getString(R.string.artwork_settings_keep_awake_hint), 11.5f,
                getColor(R.color.settings_text_secondary));
        keepAwakeHint.setLineSpacing(0f, 1.2f);
        keepAwakeHint.setPadding(dp(17), 0, dp(17), dp(14));
        screen.addView(keepAwakeHint, matchWrap());
        content.addView(screen, marginBottom(dp(12)));

        TextView footer = text(getString(R.string.artwork_settings_footer), 11.5f, getColor(R.color.settings_text_muted));
        footer.setLineSpacing(0f, 1.2f);
        footer.setPadding(dp(4), dp(2), dp(4), dp(8));
        content.addView(footer, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(settingsBackgroundColor());
        page.addView(settingsAppBar(getString(R.string.artwork_settings_title), null, this::finish),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, settingsActionBarHeight()));
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private void bind() {
        binding = true;
        master.setChecked(settings.enabled());
        card.setChecked(settings.cardEnabled());
        immersive.setChecked(settings.immersiveEnabled());
        keepAwake.setChecked(settings.keepAwake());
        binding = false;
        bindSelection();
    }

    /** Turning the display on names what leaves the device and needs a provider first. */
    private void onMasterChanged(boolean checked) {
        if (!checked) { save(settings.withEnabled(false)); return; }
        revertMaster();
        if (!settings.selected()) { chooseProvider(true); return; }
        confirmEnable();
    }

    private void confirmEnable() {
        showSettingsConfirmDialog(
                R.string.artwork_settings_disclosure_title,
                R.string.artwork_settings_disclosure_message,
                R.string.artwork_settings_disclosure_confirm,
                false,
                () -> save(settings.withEnabled(true)));
    }

    private void revertMaster() {
        binding = true;
        master.setChecked(settings.enabled());
        binding = false;
    }

    private void save(ArtworkDisplaySettings next) {
        try {
            settings = ArtworkSettingsRepository.apply(this, next);
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.artwork_settings_save_failed, Toast.LENGTH_LONG).show();
            settings = ArtworkSettingsRepository.load(this);
        }
        bind();
        refreshSelection();
        // Give SystemUI a moment to apply before asking what it runs.
        handler.removeCallbacks(statusRefresh);
        handler.postDelayed(statusRefresh, 600L);
    }

    private void refreshSelection() {
        ArtworkDisplaySettings checked = settings;
        discovery.execute(() -> {
            ArtworkSettingsRepository.Selection result = ArtworkSettingsRepository.check(this, checked);
            runOnUiThread(() -> {
                if (isDestroyed() || checked != settings) return;
                selection = result;
                bindSelection();
            });
        });
    }

    private void bindSelection() {
        ArtworkSettingsRepository.Selection current = selection;
        boolean selected = settings.selected();
        ArtworkSettingsRepository.ProviderState state = !selected ? ArtworkSettingsRepository.ProviderState.NONE
                : current == null ? null : current.state();
        ArtworkProviderDirectory.Provider provider = current == null ? null : current.provider();
        providerName.setText(provider != null ? provider.label()
                : selected ? settings.component().substring(0, settings.component().indexOf('/'))
                : getString(R.string.artwork_settings_provider_none));
        if (state == null) providerState.setText("");
        else switch (state) {
            case NONE -> providerState.setText(R.string.artwork_settings_provider_state_none);
            case READY -> providerState.setText(getString(R.string.artwork_settings_provider_state_ready,
                    provider.component().getPackageName()));
            case MISSING -> providerState.setText(R.string.artwork_settings_provider_state_missing);
            case CHANGED -> providerState.setText(R.string.artwork_settings_provider_state_changed);
        }
        providerSettings.setEnabled(state == ArtworkSettingsRepository.ProviderState.READY
                && provider != null && provider.settingsActivity() != null);
    }

    private void chooseProvider(boolean enableAfter) {
        discovery.execute(() -> {
            List<ArtworkProviderDirectory.Provider> providers = ArtworkProviderDirectory.discover(this);
            runOnUiThread(() -> {
                if (isDestroyed() || !resumed) return;
                if (providers.isEmpty()) {
                    Toast.makeText(this, R.string.artwork_settings_no_providers, Toast.LENGTH_LONG).show();
                    return;
                }
                String[] labels = new String[providers.size()];
                for (int index = 0; index < labels.length; index++) {
                    ArtworkProviderDirectory.Provider provider = providers.get(index);
                    String signer = provider.signingIdentity();
                    labels[index] = provider.label() + "\n" + provider.component().getPackageName()
                            + "\n" + getString(R.string.artwork_settings_signer, signer.substring(0, Math.min(16, signer.length())));
                }
                new AlertDialog.Builder(this)
                        .setTitle(R.string.artwork_settings_choose)
                        .setItems(labels, (dialog, index) -> {
                            ArtworkProviderDirectory.Provider provider = providers.get(index);
                            selection = new ArtworkSettingsRepository.Selection(ArtworkSettingsRepository.ProviderState.READY, provider);
                            save(settings.withSelection(provider.component().flattenToString(), provider.signingIdentity()));
                            if (enableAfter) confirmEnable();
                        })
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .show();
            });
        });
    }

    private void openProviderSettings() {
        ArtworkSettingsRepository.Selection current = selection;
        if (current == null || current.provider() == null || current.provider().settingsActivity() == null) return;
        try {
            startActivity(new Intent().setComponent(current.provider().settingsActivity()));
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.artwork_settings_provider_state_missing, Toast.LENGTH_LONG).show();
        }
    }

    private void requestStatus() {
        handler.removeCallbacks(statusRefresh);
        if (!resumed) return;
        long request = ++statusRequest;
        try {
            Intent intent = new Intent(ArtworkDisplaySettings.ACTION_REQUEST_STATUS)
                    .setPackage("com.android.systemui")
                    .putExtra(ArtworkDisplaySettings.EXTRA_RESULT_RECEIVER, new ArtworkStatusReceiver(request));
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
            sendBroadcast(intent);
        } catch (RuntimeException error) {
            showStatus(request, null);
            return;
        }
        handler.postDelayed(() -> showStatus(request, null), STATUS_TIMEOUT_MS);
    }

    private void showStatus(long request, Bundle result) {
        if (request != statusRequest || !resumed) return;
        ++statusRequest;
        handler.removeCallbacks(statusRefresh);
        handler.postDelayed(statusRefresh, STATUS_REFRESH_MS);
        if (result == null) {
            setStatus(getColor(R.color.settings_text_muted), getString(R.string.artwork_status_no_systemui),
                    getString(R.string.artwork_status_no_systemui_detail));
            return;
        }
        long revision = result.getLong(ArtworkDisplaySettings.STATUS_REVISION, -1L);
        String source = result.getString(ArtworkDisplaySettings.STATUS_SOURCE, "off");
        String state = result.getString(ArtworkDisplaySettings.STATUS_STATE, "off");
        long ageMs = result.getLong(ArtworkDisplaySettings.STATUS_AGE_MS, -1L);
        boolean hooks = result.getBoolean(ArtworkDisplaySettings.STATUS_HOOKS, false);
        if ("debug_test".equals(source)) {
            setStatus(COLOR_BUSY, getString(R.string.artwork_status_debug_test), getString(R.string.artwork_status_debug_test_detail));
            return;
        }
        if (revision != settings.revision()) {
            setStatus(COLOR_BUSY, getString(R.string.artwork_status_pending), getString(R.string.artwork_status_pending_detail));
            return;
        }
        if (!settings.enabled()) {
            setStatus(getColor(R.color.settings_text_muted), getString(R.string.artwork_status_disabled), "");
            return;
        }
        if (!settings.cardEnabled() && !settings.immersiveEnabled()) {
            setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_no_surface), "");
            return;
        }
        if (!hooks) {
            setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_unsupported), getString(R.string.artwork_status_unsupported_detail));
            return;
        }
        int seconds = (int) Math.min(Integer.MAX_VALUE, Math.max(1, ageMs / 1000));
        String age = ageMs < 0 ? "" : getResources().getQuantityString(R.plurals.artwork_status_age, seconds, seconds);
        switch (ArtworkSettingsStatus.classify(state)) {
            case PLAYING -> setStatus(COLOR_READY, getString(R.string.artwork_status_playing), "");
            case RESOLVING -> setStatus(COLOR_BUSY, getString(R.string.artwork_status_resolving), "");
            case PLAYED -> setStatus(COLOR_READY, getString(R.string.artwork_status_played), getString(R.string.artwork_status_idle_detail));
            case WAITING, OFF -> setStatus(COLOR_READY, getString(R.string.artwork_status_waiting), getString(R.string.artwork_status_idle_detail));
            case NO_MOTION -> setStatus(COLOR_BUSY, getString(R.string.artwork_status_no_motion), age);
            case MOTION_UNSUPPORTED -> setStatus(COLOR_BUSY, getString(R.string.artwork_status_motion_unsupported), join(code(state), age));
            case SOURCE_UNREADABLE -> setStatus(COLOR_BUSY, getString(R.string.artwork_status_source_unreadable),
                    join(getString(R.string.artwork_status_source_unreadable_detail), age));
            case UNMATCHED -> setStatus(COLOR_BUSY, getString(R.string.artwork_status_unmatched), join(getString(R.string.artwork_status_unmatched_detail), age));
            case SOURCE_DISABLED -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_source_disabled), getString(R.string.artwork_status_source_disabled_detail));
            case NETWORK -> setStatus(COLOR_BUSY, getString(R.string.artwork_status_network), join(getString(R.string.artwork_status_network_detail), age));
            case TEST_ONLY -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_test_only), "");
            case PROVIDER_REJECTED -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_rejected), getString(R.string.artwork_status_rejected_detail));
            case PROVIDER_UNAVAILABLE -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_provider_unavailable), "");
            case PROVIDER_FAILED -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_provider_failed), join(code(state), age));
            case MEDIA_UNREADABLE -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_media_unreadable),
                    join(getString(R.string.artwork_status_provider_log_hint), join(code(state), age)));
            case MEDIA_TOO_LARGE -> setStatus(COLOR_BUSY, getString(R.string.artwork_status_media_too_large), join(code(state), age));
            case MEDIA_PREPARATION_FAILED -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_media_preparation_failed),
                    join(getString(R.string.artwork_status_provider_log_hint), join(code(state), age)));
            case LAYOUT_UNSUPPORTED -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_layout_unsupported), join(code(state), age));
            case PLAYBACK_FAILED -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_playback_failed), join(code(state), age));
            case FAILED -> setStatus(COLOR_PROBLEM, getString(R.string.artwork_status_failed), join(code(state), age));
        }
    }

    private String code(String state) {
        return getString(R.string.artwork_status_code, state);
    }

    private static String join(String first, String second) {
        if (first.isEmpty()) return second;
        return second.isEmpty() ? first : first + "\n" + second;
    }

    private void setStatus(int color, String title, String detail) {
        if (statusDot.getBackground() instanceof GradientDrawable dot) dot.setColor(color);
        statusTitle.setText(title);
        statusDetail.setText(detail);
        statusDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Named, not anonymous: its class name is parceled to SystemUI and must stay stable across updates. */
    private final class ArtworkStatusReceiver extends ResultReceiver {
        private final long request;

        ArtworkStatusReceiver(long request) {
            super(handler);
            this.request = request;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            showStatus(request, resultData == null ? new Bundle() : resultData);
        }
    }
}
