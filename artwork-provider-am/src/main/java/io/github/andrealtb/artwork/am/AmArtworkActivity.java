package io.github.andrealtb.artwork.am;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import io.github.andrealtb.artwork.contract.ArtworkSigningIdentity;

/**
 * Provider home: the source switch, live status, how recent requests went and the motion cover
 * cache. Bridge opens this page as the provider's settings activity, so the class name is stable.
 */
public final class AmArtworkActivity extends Activity {
    private static final String[] MARKETS = { "us", "cn", "jp", "gb", "hk", "tw", "kr", "sg", "ca", "au", "de", "fr", "it", "es", "br", "mx" };
    private static final int[] CACHE_LIMIT_PRESETS = { 512, 1000, 2000, 4000, 8000 };
    private static final int GALLERY_MAX = 24;
    private static final int DOT_ON = 0xFF3DD36B;
    private static final int DOT_OFF = 0xFFB4B3BC;

    private record Tile(View root, FrameLayout badge, TextView value, TextView detail) {}

    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "artwork-am-home");
        thread.setDaemon(true);
        return thread;
    });
    private ScrollView scroll;
    private LinearLayout cacheCard;
    private GlowView glow;
    private LiveCoverView cover;
    private MotionPreview preview;
    private GradientDrawable heroDot;
    private TextView heroState;
    private TextView liveChip;
    private Switch enabled;
    private Switch metered;
    private Switch debug;
    private TextView enabledSummary;
    private Tile network;
    private Tile market;
    private Tile cache;
    private Tile bindingsTile;
    private RingChartView outcomes;
    private LinearLayout legend;
    private TextView outcomesCaption;
    private TextView attention;
    private UsageBarView usage;
    private TextView usageText;
    private TextView cacheLimitValue;
    private View galleryScroll;
    private LinearLayout gallery;
    private TextView cacheHint;
    private TextView galleryEmpty;
    private TextView marketValue;
    private TextView bridgeState;
    private TextView bridgeAction;
    private ConnectivityManager.NetworkCallback networkCallback;
    private List<File> videos = List.of();
    private boolean binding;
    private boolean resumed;
    private boolean firstShow = true;
    private int cacheGeneration;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(build());
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        glow.setRunning(true);
        cover.setRunning(preview.current() == null);
        refreshSettings(!firstShow);
        refreshOutcomes(true);
        refreshCache(!firstShow);
        listenNetwork(true);
        firstShow = false;
    }

    @Override protected void onPause() {
        resumed = false;
        listenNetwork(false);
        preview.stop();
        glow.setRunning(false);
        cover.setRunning(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private View build() {
        int pad = dp(16), bg = color(R.color.am_bg);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
                AmUi.alpha(color(R.color.am_accent), 0x2C), AmUi.alpha(color(R.color.am_violet), 0x12), bg, bg, bg, bg }));
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(pad + bars.left, bars.top + dp(4), pad + bars.right, bars.bottom + dp(28));
            return insets;
        });
        content.addView(hero());
        content.addView(tiles(), AmUi.marginTop(this, 14));
        content.addView(outcomeCard(), AmUi.marginTop(this, 14));
        cacheCard = cacheCard();
        content.addView(cacheCard, AmUi.marginTop(this, 14));
        content.addView(sourceCard(), AmUi.marginTop(this, 14));
        content.addView(advancedCard(), AmUi.marginTop(this, 14));
        content.addView(footer(), AmUi.marginTop(this, 18));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        scroll.addView(content);
        return scroll;
    }

    private View hero() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int size = Math.min(dp(236), getResources().getDisplayMetrics().widthPixels - dp(104));
        FrameLayout stage = new FrameLayout(this);
        glow = new GlowView(this, size);
        stage.addView(glow, new FrameLayout.LayoutParams(AmUi.MATCH, AmUi.MATCH));

        FrameLayout frame = new FrameLayout(this);
        float radius = dp(30);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        frame.setClipToOutline(true);
        frame.setElevation(dp(12));
        cover = new LiveCoverView(this);
        frame.addView(cover, new FrameLayout.LayoutParams(AmUi.MATCH, AmUi.MATCH));
        TextureView video = new TextureView(this);
        frame.addView(video, new FrameLayout.LayoutParams(AmUi.MATCH, AmUi.MATCH));

        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(AmUi.round(0x59000000, dp(14)));
        pill.setPadding(dp(10), dp(5), dp(11), dp(5));
        View dot = AmUi.dot(this, DOT_ON, 7);
        heroDot = (GradientDrawable) dot.getBackground();
        pill.addView(dot);
        heroState = AmUi.text(this, "", 12, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP);
        stateParams.setMarginStart(dp(6));
        pill.addView(heroState, stateParams);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP, Gravity.BOTTOM | Gravity.START);
        pillParams.setMargins(dp(12), 0, 0, dp(12));
        frame.addView(pill, pillParams);

        liveChip = AmUi.text(this, getString(R.string.hero_live), 11, 0xFFFFFFFF, true);
        liveChip.setLetterSpacing(0.08f);
        liveChip.setBackground(AmUi.round(0x59000000, dp(12)));
        liveChip.setPadding(dp(9), dp(3), dp(9), dp(4));
        liveChip.setVisibility(View.GONE);
        FrameLayout.LayoutParams liveParams = new FrameLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP, Gravity.TOP | Gravity.END);
        liveParams.setMargins(0, dp(12), dp(12), 0);
        frame.addView(liveChip, liveParams);
        frame.setContentDescription(getString(R.string.hero_cover_description));
        frame.setOnClickListener(view -> nextVideo());
        stage.addView(frame, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        column.addView(stage, new LinearLayout.LayoutParams(AmUi.MATCH, size + dp(72)));
        preview = new MotionPreview(video, new MotionPreview.Listener() {
            @Override public void onStarted() {
                liveChip.setVisibility(View.VISIBLE);
                cover.setRunning(false);
            }

            @Override public void onStopped() {
                liveChip.setVisibility(View.GONE);
                cover.setRunning(resumed);
                markGallery(null);
            }
        });

        TextView title = AmUi.text(this, getString(R.string.app_name), 26, color(R.color.am_text), true);
        title.setGravity(Gravity.CENTER);
        column.addView(title, AmUi.matchWrap());
        TextView subtitle = AmUi.text(this, getString(R.string.hero_subtitle), 13.5f, color(R.color.am_text_secondary), false);
        subtitle.setGravity(Gravity.CENTER);
        column.addView(subtitle, AmUi.marginTop(this, 4));

        LinearLayout card = AmUi.card(this);
        card.setPadding(dp(16), dp(4), dp(16), dp(4));
        enabled = AmUi.toggle(this);
        enabledSummary = new TextView(this);
        card.addView(AmUi.row(this, R.drawable.ic_am_motion, color(R.color.am_accent), getString(R.string.enable_title),
                enabledSummary, enabled));
        enabled.setOnCheckedChangeListener((view, checked) -> { if (!binding) onEnable(checked); });
        column.addView(card, AmUi.marginTop(this, 18));
        return column;
    }

    private View tiles() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        network = tile(R.drawable.ic_am_wifi, R.color.am_info, R.string.tile_network);
        market = tile(R.drawable.ic_am_globe, R.color.am_purple, R.string.tile_market);
        cache = tile(R.drawable.ic_am_storage, R.color.am_warn, R.string.tile_cache);
        bindingsTile = tile(R.drawable.ic_am_link, R.color.am_good, R.string.tile_bindings);
        grid.addView(pair(network, market));
        grid.addView(pair(cache, bindingsTile), AmUi.marginTop(this, 12));
        network.root().setOnClickListener(view -> openNetworkPanel());
        market.root().setOnClickListener(view -> pickMarket());
        cache.root().setOnClickListener(view -> scroll.smoothScrollTo(0, Math.max(0, cacheCard.getTop() - dp(12))));
        bindingsTile.root().setOnClickListener(view -> openBindings());
        return grid;
    }

    private LinearLayout pair(Tile left, Tile right) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, AmUi.MATCH, 1f);
        first.setMarginEnd(dp(6));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, AmUi.MATCH, 1f);
        second.setMarginStart(dp(6));
        row.addView(left.root(), first);
        row.addView(right.root(), second);
        return row;
    }

    private Tile tile(int icon, int colorRes, int label) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(AmUi.ripple(this, AmUi.cardBackground(this), dp(22)));
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setClickable(true);
        root.setFocusable(true);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = AmUi.badge(this, icon, color(colorRes), 30);
        top.addView(badge);
        TextView name = AmUi.single(AmUi.text(this, getString(label), 12.5f, color(R.color.am_text_secondary), false));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        nameParams.setMarginStart(dp(8));
        top.addView(name, nameParams);
        top.addView(AmUi.icon(this, R.drawable.ic_am_chevron, color(R.color.am_text_tertiary)), new LinearLayout.LayoutParams(dp(18), dp(18)));
        root.addView(top);
        TextView value = AmUi.single(AmUi.text(this, "", 19, color(R.color.am_text), true));
        root.addView(value, AmUi.marginTop(this, 10));
        TextView detail = AmUi.text(this, "", 12, color(R.color.am_text_tertiary), false);
        detail.setMaxLines(2);
        root.addView(detail, AmUi.marginTop(this, 2));
        return new Tile(root, badge, value, detail);
    }

    private View outcomeCard() {
        LinearLayout card = AmUi.card(this);
        card.addView(AmUi.heading(this, R.drawable.ic_am_chart, color(R.color.am_violet), getString(R.string.outcomes_title), null));
        outcomesCaption = AmUi.text(this, "", 12.5f, color(R.color.am_text_secondary), false);
        LinearLayout.LayoutParams captionParams = AmUi.marginTop(this, 2);
        captionParams.setMarginStart(dp(40));
        card.addView(outcomesCaption, captionParams);
        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_VERTICAL);
        outcomes = new RingChartView(this);
        body.addView(outcomes, new LinearLayout.LayoutParams(dp(124), dp(124)));
        legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams legendParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        legendParams.setMarginStart(dp(20));
        body.addView(legend, legendParams);
        card.addView(body, AmUi.marginTop(this, 14));
        attention = AmUi.text(this, "", 12.5f, color(R.color.am_warn), true);
        attention.setBackground(AmUi.round(AmUi.alpha(color(R.color.am_warn), 0x1C), dp(12)));
        attention.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.addView(attention, AmUi.marginTop(this, 14));
        TextView open = AmUi.primaryButton(this, getString(R.string.outcomes_open));
        open.setOnClickListener(view -> openBindings());
        card.addView(open, AmUi.marginTop(this, 14));
        return card;
    }

    private LinearLayout cacheCard() {
        LinearLayout card = AmUi.card(this);
        card.addView(AmUi.heading(this, R.drawable.ic_am_storage, color(R.color.am_warn), getString(R.string.cache_title), null));
        usage = new UsageBarView(this);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(AmUi.MATCH, dp(10));
        barParams.topMargin = dp(16);
        card.addView(usage, barParams);
        usageText = AmUi.text(this, "", 12.5f, color(R.color.am_text_secondary), false);
        card.addView(usageText, AmUi.marginTop(this, 6));
        card.addView(AmUi.divider(this));
        TextView limitSummary = new TextView(this);
        limitSummary.setText(R.string.cache_limit_summary);
        LinearLayout limitTrailing = new LinearLayout(this);
        limitTrailing.setGravity(Gravity.CENTER_VERTICAL);
        cacheLimitValue = AmUi.chip(this, "", color(R.color.am_warn));
        limitTrailing.addView(cacheLimitValue);
        limitTrailing.addView(AmUi.icon(this, R.drawable.ic_am_chevron, color(R.color.am_text_tertiary)),
                new LinearLayout.LayoutParams(dp(20), dp(20)));
        LinearLayout limitRow = AmUi.row(this, R.drawable.ic_am_tune, color(R.color.am_warn), getString(R.string.cache_limit_title),
                limitSummary, limitTrailing);
        limitRow.setBackground(AmUi.ripple(this, null, dp(14)));
        limitRow.setOnClickListener(view -> pickCacheLimit());
        card.addView(limitRow);
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        gallery = new LinearLayout(this);
        gallery.setPadding(0, dp(2), 0, dp(2));
        strip.addView(gallery);
        galleryScroll = strip;
        card.addView(strip, AmUi.marginTop(this, 14));
        cacheHint = AmUi.text(this, getString(R.string.cache_hint), 12, color(R.color.am_text_tertiary), false);
        card.addView(cacheHint, AmUi.marginTop(this, 8));
        galleryEmpty = AmUi.text(this, getString(R.string.cache_empty), 13, color(R.color.am_text_secondary), false);
        galleryEmpty.setBackground(AmUi.round(color(R.color.am_field), dp(14)));
        galleryEmpty.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(galleryEmpty, AmUi.marginTop(this, 14));
        TextView clear = AmUi.tonalButton(this, getString(R.string.clear), color(R.color.am_bad));
        clear.setOnClickListener(view -> clearCache());
        card.addView(clear, AmUi.marginTop(this, 14));
        return card;
    }

    private View sourceCard() {
        LinearLayout card = AmUi.card(this);
        card.addView(AmUi.heading(this, R.drawable.ic_am_tune, color(R.color.am_info), getString(R.string.section_source), null));
        metered = AmUi.toggle(this);
        TextView meteredSummary = new TextView(this);
        meteredSummary.setText(R.string.metered_summary);
        card.addView(AmUi.row(this, R.drawable.ic_am_cellular, color(R.color.am_info), getString(R.string.metered), meteredSummary, metered),
                AmUi.marginTop(this, 4));
        metered.setOnCheckedChangeListener((view, checked) -> {
            if (binding) return;
            AmSettings.prefs(this).edit().putBoolean("metered", checked).apply();
            refreshNetwork();
        });
        card.addView(AmUi.divider(this));
        LinearLayout trailing = new LinearLayout(this);
        trailing.setGravity(Gravity.CENTER_VERTICAL);
        marketValue = AmUi.chip(this, "", color(R.color.am_purple));
        trailing.addView(marketValue);
        trailing.addView(AmUi.icon(this, R.drawable.ic_am_chevron, color(R.color.am_text_tertiary)), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView marketSummary = new TextView(this);
        marketSummary.setText(R.string.market_summary);
        LinearLayout marketRow = AmUi.row(this, R.drawable.ic_am_globe, color(R.color.am_purple), getString(R.string.market_title),
                marketSummary, trailing);
        marketRow.setBackground(AmUi.ripple(this, null, dp(14)));
        marketRow.setOnClickListener(view -> pickMarket());
        card.addView(marketRow);
        return card;
    }

    private View advancedCard() {
        LinearLayout card = AmUi.card(this);
        card.addView(AmUi.heading(this, R.drawable.ic_am_info, color(R.color.am_muted), getString(R.string.section_advanced), null));
        debug = AmUi.toggle(this);
        TextView debugSummary = new TextView(this);
        debugSummary.setText(R.string.debug_summary);
        card.addView(AmUi.row(this, R.drawable.ic_am_code, color(R.color.am_muted), getString(R.string.debug), debugSummary, debug),
                AmUi.marginTop(this, 4));
        debug.setOnCheckedChangeListener((view, checked) -> {
            if (!binding) AmSettings.prefs(this).edit().putBoolean("debug", checked).apply();
        });
        card.addView(AmUi.divider(this));
        bridgeState = new TextView(this);
        bridgeAction = AmUi.tonalButton(this, "", color(R.color.am_accent));
        card.addView(AmUi.row(this, R.drawable.ic_am_shield, color(R.color.am_good), getString(R.string.bridge_title), bridgeState, bridgeAction));
        return card;
    }

    private View footer() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(6), 0, dp(6), 0);
        footer.addView(AmUi.text(this, getString(R.string.footer_privacy), 12, color(R.color.am_text_tertiary), false));
        footer.addView(AmUi.text(this, getString(R.string.footer_version, versionName()), 12, color(R.color.am_text_tertiary), false),
                AmUi.marginTop(this, 8));
        return footer;
    }

    private void refreshSettings(boolean animate) {
        SharedPreferences prefs = AmSettings.prefs(this);
        boolean on = AmSettings.enabled(this);
        binding = true;
        enabled.setChecked(on);
        metered.setChecked(prefs.getBoolean("metered", false));
        debug.setChecked(prefs.getBoolean("debug", false));
        if (!animate) {
            enabled.jumpDrawablesToCurrentState();
            metered.jumpDrawablesToCurrentState();
            debug.jumpDrawablesToCurrentState();
        }
        binding = false;
        showSourceState(on, animate);
        refreshMarket();
        refreshCacheLimit();
        refreshNetwork();
        refreshBridge();
        bindingsTile.value().setText(String.valueOf(AmBindings.load(this).size()));
        bindingsTile.detail().setText(R.string.bindings_detail);
    }

    private void showSourceState(boolean on, boolean animate) {
        heroState.setText(on ? R.string.hero_state_on : R.string.hero_state_off);
        heroDot.setColor(on ? DOT_ON : DOT_OFF);
        enabledSummary.setText(on ? R.string.enable_summary_on : R.string.enable_summary_off);
        cover.setActive(on, animate);
        glow.setActive(on, animate);
    }

    private void onEnable(boolean checked) {
        if (!checked) {
            AmSettings.prefs(this).edit().putBoolean("enabled", false).apply();
            showSourceState(false, true);
            refreshNetwork();
            return;
        }
        // Stays off until the disclosure is accepted.
        setChecked(enabled, false);
        new AlertDialog.Builder(this)
                .setTitle(R.string.enable_dialog_title)
                .setMessage(R.string.enable_dialog_message)
                .setPositiveButton(R.string.enable_dialog_confirm, (dialog, which) -> {
                    AmSettings.prefs(this).edit().putBoolean("enabled", true).apply();
                    setChecked(enabled, true);
                    showSourceState(true, true);
                    refreshNetwork();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setChecked(Switch toggle, boolean value) {
        binding = true;
        toggle.setChecked(value);
        binding = false;
    }

    private void refreshNetwork() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities = manager == null ? null : manager.getNetworkCapabilities(manager.getActiveNetwork());
        boolean connected = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean meteredNetwork = manager != null && manager.isActiveNetworkMetered();
        int icon, tint, value, detail;
        if (!connected) {
            icon = R.drawable.ic_am_offline;
            tint = R.color.am_muted;
            value = R.string.network_offline;
            detail = R.string.network_detail_offline;
        } else {
            icon = meteredNetwork ? R.drawable.ic_am_cellular : R.drawable.ic_am_wifi;
            value = meteredNetwork ? R.string.network_metered : R.string.network_unmetered;
            if (!AmSettings.enabled(this)) {
                tint = R.color.am_muted;
                detail = R.string.network_detail_off;
            } else if (meteredNetwork && !AmSettings.prefs(this).getBoolean("metered", false)) {
                tint = R.color.am_warn;
                detail = R.string.network_detail_paused;
            } else {
                tint = R.color.am_good;
                detail = R.string.network_detail_ready;
            }
        }
        AmUi.recolor(network.badge(), icon, color(tint));
        network.value().setText(value);
        network.detail().setText(detail);
    }

    private void listenNetwork(boolean listen) {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        if (manager == null) return;
        if (!listen) {
            if (networkCallback != null) {
                try { manager.unregisterNetworkCallback(networkCallback); } catch (IllegalArgumentException ignored) { /* already gone */ }
                networkCallback = null;
            }
            return;
        }
        if (networkCallback != null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network changed, NetworkCapabilities capabilities) { refreshNetwork(); }

            @Override public void onLost(Network lost) { refreshNetwork(); }
        };
        try {
            manager.registerDefaultNetworkCallback(networkCallback, new Handler(Looper.getMainLooper()));
        } catch (RuntimeException error) {
            networkCallback = null;
        }
    }

    private void refreshMarket() {
        String code = AmSettings.country(this);
        market.value().setText(code.toUpperCase(Locale.ROOT));
        market.detail().setText(countryName(code));
        marketValue.setText(code.toUpperCase(Locale.ROOT));
    }

    private static String countryName(String code) {
        try {
            return new Locale.Builder().setRegion(code.toUpperCase(Locale.ROOT)).build().getDisplayCountry();
        } catch (RuntimeException error) {
            return code.toUpperCase(Locale.ROOT);
        }
    }

    private void pickMarket() {
        String current = AmSettings.country(this);
        List<String> codes = new ArrayList<>(List.of(MARKETS));
        if (!codes.contains(current)) codes.add(0, current);
        String[] labels = new String[codes.size() + 1];
        for (int i = 0; i < codes.size(); i++) labels[i] = codes.get(i).toUpperCase(Locale.ROOT) + " · " + countryName(codes.get(i));
        labels[codes.size()] = getString(R.string.market_other);
        new AlertDialog.Builder(this)
                .setTitle(R.string.market_dialog_title)
                .setSingleChoiceItems(labels, codes.indexOf(current), (dialog, which) -> {
                    dialog.dismiss();
                    if (which == codes.size()) customMarket();
                    else saveMarket(codes.get(which));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void customMarket() {
        EditText field = AmUi.field(this, R.string.market_other_hint);
        field.setFilters(new InputFilter[] { new InputFilter.LengthFilter(2), new InputFilter.AllCaps() });
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(field);
        new AlertDialog.Builder(this)
                .setTitle(R.string.market_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String code = field.getText().toString().trim();
                    if (code.matches("[a-zA-Z]{2}")) saveMarket(code);
                    else toast(getString(R.string.country_invalid));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveMarket(String code) {
        String lower = code.toLowerCase(Locale.ROOT);
        AmSettings.prefs(this).edit().putString("country", lower).apply();
        refreshMarket();
        toast(getString(R.string.market_saved, lower.toUpperCase(Locale.ROOT)));
    }

    private void refreshCacheLimit() {
        cacheLimitValue.setText(formatLimit(AmSettings.cacheLimitMb(this)));
    }

    private String formatLimit(int megabytes) {
        return Formatter.formatShortFileSize(this, megabytes * 1_000_000L);
    }

    private void pickCacheLimit() {
        int current = AmSettings.cacheLimitMb(this);
        List<Integer> values = new ArrayList<>();
        for (int preset : CACHE_LIMIT_PRESETS) values.add(preset);
        if (!values.contains(current)) values.add(0, current);
        String[] labels = new String[values.size() + 1];
        for (int i = 0; i < values.size(); i++) labels[i] = formatLimit(values.get(i));
        labels[values.size()] = getString(R.string.cache_limit_custom);
        new AlertDialog.Builder(this)
                .setTitle(R.string.cache_limit_dialog_title)
                .setSingleChoiceItems(labels, values.indexOf(current), (dialog, which) -> {
                    dialog.dismiss();
                    if (which == values.size()) customCacheLimit();
                    else saveCacheLimit(values.get(which));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void customCacheLimit() {
        EditText field = AmUi.field(this, R.string.cache_limit_custom_hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });
        field.setText(String.valueOf(AmSettings.cacheLimitMb(this)));
        field.setSelection(field.getText().length());
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(field, new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f));
        TextView unit = AmUi.text(this, getString(R.string.cache_limit_unit), 15, color(R.color.am_text_secondary), false);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP);
        unitParams.setMarginStart(dp(10));
        box.addView(unit, unitParams);
        new AlertDialog.Builder(this)
                .setTitle(R.string.cache_limit_dialog_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int megabytes;
                    try { megabytes = Integer.parseInt(field.getText().toString().trim()); }
                    catch (NumberFormatException empty) { megabytes = -1; }
                    if (megabytes < AmSettings.MIN_CACHE_LIMIT_MB || megabytes > AmSettings.MAX_CACHE_LIMIT_MB) {
                        toast(getString(R.string.cache_limit_invalid, formatLimit(AmSettings.MIN_CACHE_LIMIT_MB),
                                formatLimit(AmSettings.MAX_CACHE_LIMIT_MB)));
                        return;
                    }
                    saveCacheLimit(megabytes);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveCacheLimit(int megabytes) {
        AmSettings.prefs(this).edit().putInt("cacheLimitMb", megabytes).apply();
        refreshCacheLimit();
        toast(getString(R.string.cache_limit_saved, formatLimit(megabytes)));
        Context app = getApplicationContext();
        try {
            io.execute(() -> {
                // Reapplies the new budget and trims right away; raising it only allows more later.
                try { AmCache.get(app).cleanup(); } catch (RuntimeException unavailable) { /* nothing to trim */ }
                runOnUiThread(() -> { if (!isDestroyed()) refreshCache(true); });
            });
        } catch (RejectedExecutionException closing) {
            // Page is closing.
        }
    }

    private void refreshBridge() {
        String pinned = getSharedPreferences(ArtworkCallerPolicy.PREFERENCES, MODE_PRIVATE).getString(ArtworkCallerPolicy.SIGNER, "");
        boolean hasPin = pinned != null && !pinned.isEmpty();
        String installed = null;
        try {
            installed = ArtworkSigningIdentity.read(getPackageManager(), ArtworkCallerPolicy.BRIDGE);
        } catch (Exception missing) {
            // Bridge not installed or not visible: nothing to authorize.
        }
        int state;
        boolean revoke;
        if (installed == null) { state = R.string.bridge_state_missing; revoke = hasPin; }
        else if (!hasPin) { state = R.string.bridge_state_none; revoke = false; }
        else if (pinned.equals(installed)) { state = R.string.bridge_state_allowed; revoke = true; }
        else { state = R.string.bridge_state_changed; revoke = false; }
        bridgeState.setText(state);
        bridgeAction.setVisibility(installed == null && !hasPin ? View.GONE : View.VISIBLE);
        bridgeAction.setText(revoke ? R.string.bridge_revoke : R.string.bridge_allow);
        bridgeAction.setOnClickListener(view -> { if (revoke) revokeBridge(); else allowBridge(); });
    }

    private void allowBridge() {
        try {
            String signer = ArtworkSigningIdentity.read(getPackageManager(), ArtworkCallerPolicy.BRIDGE);
            getSharedPreferences(ArtworkCallerPolicy.PREFERENCES, MODE_PRIVATE).edit().putString(ArtworkCallerPolicy.SIGNER, signer).apply();
            toast(getString(R.string.allowed));
        } catch (Exception error) {
            toast(getString(R.string.bridge_missing));
        }
        refreshBridge();
    }

    private void revokeBridge() {
        getSharedPreferences(ArtworkCallerPolicy.PREFERENCES, MODE_PRIVATE).edit().clear().apply();
        toast(getString(R.string.revoked));
        refreshBridge();
    }

    private void refreshOutcomes(boolean animate) {
        List<AmRecentAlbums.Entry> entries = AmRecentAlbums.load(this);
        AmRecentAlbums.Outcome[] order = AmRecentAlbums.Outcome.values();
        int[] counts = new int[order.length];
        for (AmRecentAlbums.Entry entry : entries) counts[entry.outcome().ordinal()]++;
        List<RingChartView.Segment> segments = new ArrayList<>();
        legend.removeAllViews();
        for (AmRecentAlbums.Outcome outcome : order) {
            int count = counts[outcome.ordinal()], tint = color(outcomeColor(outcome));
            segments.add(new RingChartView.Segment(count, tint));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(3), 0, dp(3));
            row.addView(AmUi.dot(this, tint, 9));
            TextView name = AmUi.single(AmUi.text(this, getString(outcomeLabel(outcome)), 13.5f, color(R.color.am_text), false));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
            nameParams.setMarginStart(dp(10));
            row.addView(name, nameParams);
            row.addView(AmUi.text(this, String.valueOf(count), 13.5f, color(R.color.am_text), true));
            row.setAlpha(count == 0 ? 0.45f : 1f);
            legend.addView(row);
        }
        int total = entries.size();
        outcomes.setData(segments, String.valueOf(total), getResources().getQuantityString(R.plurals.outcomes_center, total),
                animate && total > 0);
        outcomesCaption.setText(total == 0 ? getString(R.string.outcomes_empty)
                : getResources().getQuantityString(R.plurals.outcomes_caption, total, total));
        int open = counts[AmRecentAlbums.Outcome.UNMATCHED.ordinal()] + counts[AmRecentAlbums.Outcome.FAILED.ordinal()];
        attention.setVisibility(open > 0 ? View.VISIBLE : View.GONE);
        if (open > 0) attention.setText(getResources().getQuantityString(R.plurals.outcomes_attention, open, open));
    }

    static int outcomeColor(AmRecentAlbums.Outcome outcome) {
        return switch (outcome) {
            case MATCHED -> R.color.am_good;
            case BOUND -> R.color.am_violet;
            case NO_MOTION -> R.color.am_muted;
            case UNMATCHED -> R.color.am_warn;
            case FAILED -> R.color.am_bad;
        };
    }

    static int outcomeLabel(AmRecentAlbums.Outcome outcome) {
        return switch (outcome) {
            case MATCHED -> R.string.outcome_matched;
            case BOUND -> R.string.outcome_bound;
            case NO_MOTION -> R.string.outcome_no_motion;
            case UNMATCHED -> R.string.outcome_unmatched;
            case FAILED -> R.string.outcome_failed;
        };
    }

    private void refreshCache(boolean animate) {
        int generation = ++cacheGeneration;
        Context app = getApplicationContext();
        try {
            io.execute(() -> {
                List<File> files;
                try { files = AmCache.get(app).videos(); } catch (RuntimeException unavailable) { files = List.of(); }
                long bytes = 0;
                for (File file : files) bytes += file.length();
                long budget = AmSettings.cacheLimitBytes(app);
                List<File> found = files;
                long total = bytes;
                runOnUiThread(() -> { if (!isDestroyed() && generation == cacheGeneration) showCache(found, total, budget, animate); });
            });
        } catch (RejectedExecutionException closing) {
            // Page is closing.
        }
    }

    private void showCache(List<File> files, long bytes, long budget, boolean animate) {
        videos = files;
        String used = Formatter.formatShortFileSize(this, bytes);
        cache.value().setText(used);
        cache.detail().setText(getResources().getQuantityString(R.plurals.cache_count, files.size(), files.size()));
        usage.setFraction(budget <= 0 ? 0f : bytes / (float) budget, animate);
        usageText.setText(getString(R.string.cache_usage, used, Formatter.formatShortFileSize(this, budget)));
        boolean empty = files.isEmpty();
        galleryScroll.setVisibility(empty ? View.GONE : View.VISIBLE);
        cacheHint.setVisibility(empty ? View.GONE : View.VISIBLE);
        galleryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        gallery.removeAllViews();
        int size = dp(76);
        for (int i = 0; i < Math.min(files.size(), GALLERY_MAX); i++) {
            File file = files.get(i);
            CoverTile tile = new CoverTile(this, dp(16));
            tile.setPlaceholder(file.getName(), "");
            tile.setPlayBadge(true);
            tile.setContentDescription(getString(R.string.hero_cover_description));
            AmThumbnails.video(tile, file, size);
            tile.setOnClickListener(view -> {
                play(file);
                scroll.smoothScrollTo(0, 0);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            if (i > 0) params.setMarginStart(dp(10));
            gallery.addView(tile, params);
        }
        File current = preview.current();
        if (resumed && (current == null || !files.contains(current))) play(empty ? null : files.get(0));
        else markGallery(current);
    }

    private void play(File file) {
        if (file == null) preview.stop();
        else preview.play(file);
        markGallery(file);
    }

    private void markGallery(File file) {
        for (int i = 0; i < gallery.getChildCount() && i < videos.size(); i++) {
            ((CoverTile) gallery.getChildAt(i)).setRing(videos.get(i).equals(file));
        }
    }

    private void nextVideo() {
        if (videos.size() < 2) return;
        int index = videos.indexOf(preview.current());
        play(videos.get((index + 1) % videos.size()));
    }

    private void clearCache() {
        Context app = getApplicationContext();
        preview.stop();
        try {
            io.execute(() -> {
                try { AmCache.get(app).clear(); } catch (RuntimeException unavailable) { /* nothing to clear */ }
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    toast(getString(R.string.cleared));
                    refreshCache(true);
                });
            });
        } catch (RejectedExecutionException closing) {
            // Page is closing.
        }
    }

    private void openBindings() { startActivity(new Intent(this, AmBindingActivity.class)); }

    private void openNetworkPanel() {
        try {
            startActivity(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
        } catch (ActivityNotFoundException noPanel) {
            try { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); } catch (ActivityNotFoundException ignored) { /* no settings app */ }
        }
    }

    @SuppressWarnings("deprecation")
    private String versionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (Exception error) {
            return "";
        }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private int dp(float value) { return AmUi.dp(this, value); }

    private int color(int res) { return getColor(res); }
}
