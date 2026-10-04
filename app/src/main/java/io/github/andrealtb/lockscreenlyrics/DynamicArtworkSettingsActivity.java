package io.github.andrealtb.lockscreenlyrics;

import android.content.Intent;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import io.github.andrealtb.artwork.contract.ArtworkContract;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkProviderClient;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkProviderDirectory;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkRequestStamp;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkImmersiveTestConfig;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.DynamicArtworkRenderer;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Debug fixture preview and ephemeral single-slot SystemUI test. */
public final class DynamicArtworkSettingsActivity extends SettingsBaseActivity {
    private static final String PREFERENCES = "dynamic_artwork";
    /** Survives clearing the provider selection; sent with every test enable and on each change. */
    private static final String KEEP_AWAKE = "keep_awake";
    private final ExecutorService discovery = Executors.newSingleThreadExecutor();
    private ArtworkProviderClient client;
    private DynamicArtworkRenderer renderer;
    private ArtworkProviderDirectory.Provider selected;
    private TextView selection;
    private TextView status;
    private Button preview;
    private boolean resumed;
    private long discoveryEpoch;
    private long previewEpoch;
    private EditText queryTitle, queryArtist, queryAlbum, queryDuration, queryUrl;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        client = new ArtworkProviderClient(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(settingsBackgroundColor());
        page.addView(settingsAppBar(getString(R.string.artwork_title), null,
                () -> getOnBackPressedDispatcher().onBackPressed()),
                new LinearLayout.LayoutParams(-1, settingsActionBarHeight()));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(settingsScreenPadding(), dp(12), settingsScreenPadding(), settingsScreenBottomPadding());
        installSettingsInsets(content);
        LinearLayout settings = paddedCard();
        settings.addView(text(getString(R.string.artwork_stage_hint), 13, settingsTextColor()), matchWrap());
        selection = text(getString(R.string.artwork_none), 12, settingsTextColor());
        selection.setPadding(0, dp(16), 0, dp(10));
        settings.addView(selection, matchWrap());
        Button select = button(getString(R.string.artwork_select));
        select.setOnClickListener(view -> discover(true));
        settings.addView(select, matchWrap());
        Button openSettings = button(getString(R.string.artwork_provider_settings));
        openSettings.setOnClickListener(view -> openProviderSettings());
        settings.addView(openSettings, matchWrap());
        Button enableImmersive = button(getString(R.string.artwork_immersive_test_enable));
        enableImmersive.setOnClickListener(view -> sendImmersiveTest(true));
        settings.addView(enableImmersive, matchWrap());
        Button enableLive = button(getString(R.string.artwork_live_test_enable));
        enableLive.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setMessage(R.string.artwork_live_disclosure)
                .setPositiveButton(R.string.artwork_live_test_enable, (dialog, which) -> sendImmersiveTest(true, false))
                .setNegativeButton(R.string.dialog_cancel, null).show());
        settings.addView(enableLive, matchWrap());
        com.google.android.material.materialswitch.MaterialSwitch keepAwake = toggle(getString(R.string.artwork_keep_awake),
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(KEEP_AWAKE, false));
        keepAwake.setOnCheckedChangeListener((view, checked) -> {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEEP_AWAKE, checked).apply();
            if (BuildConfig.DEBUG) sendBroadcast(new Intent(ArtworkImmersiveTestConfig.ACTION_KEEP_AWAKE)
                    .setPackage("com.android.systemui").putExtra("keepAwake", checked));
        });
        settings.addView(keepAwake, matchWrap());
        TextView keepAwakeHint = text(getString(R.string.artwork_keep_awake_hint), 12, settingsTextColor());
        keepAwakeHint.setPadding(dp(17), 0, dp(13), dp(10));
        settings.addView(keepAwakeHint, matchWrap());
        Button disableImmersive = button(getString(R.string.artwork_immersive_test_disable));
        disableImmersive.setOnClickListener(view -> sendImmersiveTest(false));
        settings.addView(disableImmersive, matchWrap());
        Button clear = button(getString(R.string.artwork_clear_selection));
        clear.setOnClickListener(view -> {
            sendImmersiveTest(false);
            ++discoveryEpoch;
            stop();
            selected = null;
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().remove("component").remove("signer")
                    .remove("protocolMajor").remove("enabled").apply();
            selection.setText(R.string.artwork_none);
            preview.setEnabled(false);
        });
        settings.addView(clear, matchWrap());
        content.addView(settings, marginBottom(dp(12)));
        FrameLayout video = new FrameLayout(this);
        video.setBackgroundColor(0xff202124);
        TextureView texture = new TextureView(this);
        video.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        content.addView(video, new LinearLayout.LayoutParams(-1, dp(260)));
        status = text(getString(R.string.artwork_stopped), 12, settingsTextColor());
        status.setPadding(0, dp(12), 0, dp(12));
        content.addView(status, matchWrap());
        queryTitle = queryInput(content, R.string.artwork_query_title);
        queryArtist = queryInput(content, R.string.artwork_query_artist);
        queryAlbum = queryInput(content, R.string.artwork_query_album);
        queryDuration = queryInput(content, R.string.artwork_query_duration);
        queryDuration.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        queryUrl = queryInput(content, R.string.artwork_query_url);
        renderer = new DynamicArtworkRenderer(texture, this::showState);
        preview = button(getString(R.string.artwork_preview));
        preview.setEnabled(false);
        preview.setOnClickListener(view -> startPreview());
        content.addView(preview, matchWrap());
        Button livePreview = button(getString(R.string.artwork_live_preview));
        livePreview.setOnClickListener(view -> startLivePreview());
        content.addView(livePreview, matchWrap());
        Button stop = button(getString(R.string.artwork_stop));
        stop.setOnClickListener(view -> stop());
        content.addView(stop, matchWrap());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        discover(false);
    }

    private void discover(boolean choose) {
        long epoch = ++discoveryEpoch;
        discovery.execute(() -> {
            List<ArtworkProviderDirectory.Provider> providers = ArtworkProviderDirectory.discover(this);
            runOnUiThread(() -> {
                if (!resumed || isDestroyed() || epoch != discoveryEpoch) return;
                if (choose) {
                    if (providers.isEmpty()) { status.setText(R.string.artwork_no_providers); return; }
                    String[] labels = new String[providers.size()];
                    for (int index = 0; index < labels.length; index++) {
                        ArtworkProviderDirectory.Provider provider = providers.get(index);
                        labels[index] = provider.label() + "\n" + provider.component().flattenToShortString()
                                + "\nSHA-256: " + provider.signingIdentity();
                    }
                    new AlertDialog.Builder(this).setTitle(R.string.artwork_select)
                            .setItems(labels, (dialog, index) -> select(providers.get(index)))
                            .setNegativeButton(R.string.dialog_cancel, null).show();
                } else {
                    selected = null;
                    String savedComponent = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString("component", "");
                    String savedSigner = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString("signer", "");
                    for (ArtworkProviderDirectory.Provider provider : providers) {
                        if (provider.component().flattenToString().equals(savedComponent)
                                && provider.signingIdentity().equals(savedSigner)) selected = provider;
                    }
                    selection.setText(selected == null ? getString(R.string.artwork_none) : selected.label());
                    preview.setEnabled(selected != null);
                    if (selected == null && savedComponent != null && !savedComponent.isEmpty()) {
                        stop();
                        status.setText(R.string.artwork_selection_invalid);
                    }
                }
            });
        });
    }

    private void select(ArtworkProviderDirectory.Provider provider) {
        sendImmersiveTest(false);
        stop();
        selected = provider;
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putString("component", provider.component().flattenToString())
                .putString("signer", provider.signingIdentity())
                .putInt("protocolMajor", ArtworkContract.MAJOR)
                .putBoolean("enabled", false).apply();
        selection.setText(provider.label());
        preview.setEnabled(true);
    }

    private void openProviderSettings() {
        ArtworkProviderDirectory.Provider provider = selected;
        if (provider == null || provider.settingsActivity() == null) return;
        discovery.execute(() -> {
            try {
                ArtworkProviderDirectory.Provider verified = ArtworkProviderDirectory.verify(this, provider);
                runOnUiThread(() -> {
                    if (!resumed || selected != provider || verified.settingsActivity() == null) return;
                    try { startActivity(new Intent().setComponent(verified.settingsActivity())); }
                    catch (RuntimeException error) { status.setText(R.string.artwork_selection_invalid); }
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (resumed) status.setText(R.string.artwork_selection_invalid); });
            }
        });
    }

    private void startPreview() {
        if (selected == null || !resumed) return;
        long requestEpoch = ++previewEpoch;
        ArtworkProviderDirectory.Provider provider = selected;
        ArtworkRequestStamp stamp = previewStamp(requestEpoch);
        renderer.stop();
        ArtworkQuery query = new ArtworkQuery("Local artwork protocol test", "", "", 0,
                "", 512, 512, ArtworkContract.MAX_RESOLUTION, ArtworkContract.MAX_RESOLUTION,
                ArtworkContract.MAX_FILE_BYTES);
        client.resolve(selected, query, true, new ArtworkProviderClient.Listener() {
            @Override public void onState(String reason) {
                if (!"connecting".equals(reason) && !"resolving".equals(reason)) renderer.stop();
                showState(reason);
            }
            @Override public void onAsset(ArtworkProviderClient.OpenedAsset asset) {
                if (!resumed) { asset.close(); return; }
                renderer.play(asset, stamp, () -> previewStamp(previewEpoch),
                        () -> resumed && selected == provider);
            }
        });
    }

    private void showState(String reason) {
        int string = switch (reason) {
            case "connecting" -> R.string.artwork_connecting;
            case "resolving" -> R.string.artwork_resolving;
            case "preparing" -> R.string.artwork_preparing;
            case "waiting_first_frame" -> R.string.artwork_waiting_frame;
            case "playing" -> R.string.artwork_playing;
            case "no_motion" -> R.string.artwork_no_video;
            case "ambiguous" -> R.string.artwork_ambiguous;
            case "network_blocked" -> R.string.artwork_network_blocked;
            case "retry_later" -> R.string.artwork_retry_later;
            case "request_timeout", "first_frame_timeout" -> R.string.artwork_timeout;
            default -> R.string.artwork_failed;
        };
        status.setText(string);
    }

    private void stop() {
        ++previewEpoch;
        client.close();
        renderer.stop();
        status.setText(R.string.artwork_stopped);
    }

    private void sendImmersiveTest(boolean enabled) {
        sendImmersiveTest(enabled, true);
    }

    private void sendImmersiveTest(boolean enabled, boolean localFixture) {
        if (!BuildConfig.DEBUG) return;
        if (enabled && selected == null) { status.setText(R.string.artwork_none); return; }
        Intent intent = new Intent(ArtworkImmersiveTestConfig.ACTION).setPackage("com.android.systemui")
                .putExtra("enabled", enabled).putExtra("revision", System.currentTimeMillis()).putExtra("localFixture", localFixture)
                .putExtra("keepAwake", getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(KEEP_AWAKE, false));
        if (enabled) intent.putExtra("component", selected.component().flattenToString())
                .putExtra("signer", selected.signingIdentity());
        sendBroadcast(intent);
        status.setText(enabled ? (localFixture ? R.string.artwork_immersive_test_sent : R.string.artwork_live_test_sent) : R.string.artwork_immersive_test_disabled);
    }

    private EditText queryInput(LinearLayout parent, int hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextColor(settingsTextColor());
        input.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(2048) });
        parent.addView(input, matchWrap());
        return input;
    }

    private void startLivePreview() {
        if (selected == null || !resumed) return;
        ArtworkQuery query;
        try {
            query = new ArtworkQuery(queryTitle.getText().toString().trim(), queryArtist.getText().toString().trim(),
                    queryAlbum.getText().toString().trim(), Long.parseLong(queryDuration.getText().toString()),
                    queryUrl.getText().toString().trim(), 288, 288, ArtworkContract.MAX_RESOLUTION,
                    ArtworkContract.MAX_RESOLUTION, ArtworkContract.MAX_FILE_BYTES);
            if (query.artist.isEmpty() || query.durationMs <= 0) throw new IllegalArgumentException();
        } catch (RuntimeException error) { status.setText(R.string.artwork_query_invalid); return; }
        long requestEpoch = ++previewEpoch;
        ArtworkProviderDirectory.Provider provider = selected;
        ArtworkRequestStamp stamp = previewStamp(requestEpoch);
        renderer.stop();
        client.resolve(provider, query, false, new ArtworkProviderClient.Listener() {
            @Override public void onState(String reason) {
                if (requestEpoch != previewEpoch) return;
                if (!reason.equals("connecting") && !reason.equals("resolving")) renderer.stop();
                showState(reason);
            }
            @Override public void onAsset(ArtworkProviderClient.OpenedAsset asset) {
                if (!resumed || requestEpoch != previewEpoch || selected != provider) { asset.close(); return; }
                renderer.play(asset, stamp, () -> previewStamp(previewEpoch), () -> resumed && selected == provider);
            }
        });
    }

    // App preview has no SystemUI plugin, host or song epochs. Those domains remain zero here.
    private static ArtworkRequestStamp previewStamp(long epoch) {
        return new ArtworkRequestStamp(epoch, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override protected void onStop() {
        resumed = false;
        ++discoveryEpoch;
        stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        client.close();
        renderer.close();
        discovery.shutdownNow();
        super.onDestroy();
    }
}
