package io.github.andrealtb.artwork.am;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

/**
 * Binds an Apple Music album to a local album name. Searching sends only the terms the user typed;
 * a binding is saved only after the album page confirms a square motion cover exists.
 */
public final class AmBindingActivity extends Activity {
    private record Choice(String country, String id, String title, String artist, String detail, String artwork, boolean explicit,
            AmPage.Album page) {}

    private enum Tone { NEUTRAL, ERROR, SUCCESS }

    /** The trailing control of one search result: bind button, progress, then a confirmation. */
    private final class Action {
        final FrameLayout root = new FrameLayout(AmBindingActivity.this);
        final TextView button = AmUi.tonalButton(AmBindingActivity.this, getString(R.string.binding_action), color(R.color.am_accent));
        final ProgressBar progress = new ProgressBar(AmBindingActivity.this, null, android.R.attr.progressBarStyleSmall);
        final TextView done = AmUi.chip(AmBindingActivity.this, getString(R.string.binding_done), color(R.color.am_good));

        Action() {
            progress.setIndeterminateTintList(ColorStateList.valueOf(color(R.color.am_accent)));
            Drawable check = getDrawable(R.drawable.ic_am_check);
            if (check != null) {
                check = check.mutate();
                check.setTint(color(R.color.am_good));
                check.setBounds(0, 0, dp(14), dp(14));
                done.setCompoundDrawablesRelative(check, null, null, null);
                done.setCompoundDrawablePadding(dp(3));
            }
            done.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(button, new FrameLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP, Gravity.CENTER));
            root.addView(progress, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            root.addView(done, new FrameLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP, Gravity.CENTER));
            idle();
        }

        void idle() { show(button); }

        void busy() { show(progress); }

        void bound() { show(done); }

        private void show(View visible) {
            for (View view : new View[] { button, progress, done }) view.setVisibility(view == visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "artwork-am-binding");
        thread.setDaemon(true);
        return thread;
    });
    private AmNetwork network;
    /** UI thread only: the lookup whose answer the page still wants. */
    private AmNetwork.Task task;
    /** UI thread only: the result row waiting for {@link #task}. */
    private Action pending;
    private ScrollView scroll;
    private EditText album;
    private EditText artist;
    private EditText terms;
    private View statusRow;
    private ProgressBar busy;
    private TextView status;
    private LinearLayout results;
    private LinearLayout bindings;
    private LinearLayout recent;
    private TextView bindingCount;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        Context app = getApplicationContext();
        network = new AmNetwork(() -> AmSettings.connected(app), reason -> AmSettings.trace(app, "ARTWORK_AM_BINDING_HTTP", reason));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.am_bg));
        LinearLayout bar = topBar();
        root.addView(bar, AmUi.matchWrap());
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(AmUi.MATCH, 0, 1f));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
            // The keyboard shrinks the scroller itself, so it keeps the focused field in view.
            view.setPadding(0, 0, 0, ime);
            bar.setPadding(dp(6) + bars.left, bars.top + dp(4), dp(16) + bars.right, dp(6));
            content.setPadding(dp(16) + bars.left, dp(4), dp(16) + bars.right, (ime > 0 ? 0 : bars.bottom) + dp(24));
            return insets;
        });

        content.addView(intro(), AmUi.marginTop(this, 4));

        LinearLayout local = AmUi.card(this);
        local.addView(step(1, R.string.binding_step_local));
        album = labelled(local, R.string.binding_local_album, R.string.binding_local_album_hint);
        artist = labelled(local, R.string.binding_local_artist, R.string.binding_local_artist_hint);
        content.addView(local, AmUi.marginTop(this, 14));

        LinearLayout find = AmUi.card(this);
        find.addView(step(2, R.string.binding_step_search));
        terms = AmUi.field(this, R.string.binding_terms);
        terms.setFilters(new InputFilter[] { new InputFilter.LengthFilter(512) });
        Drawable search = getDrawable(R.drawable.ic_am_search);
        if (search != null) {
            search = search.mutate();
            search.setTint(color(R.color.am_text_tertiary));
            search.setBounds(0, 0, dp(20), dp(20));
            terms.setCompoundDrawablesRelative(search, null, null, null);
            terms.setCompoundDrawablePadding(dp(10));
        }
        terms.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        terms.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            search();
            return true;
        });
        find.addView(terms, AmUi.marginTop(this, 14));
        TextView searchButton = AmUi.primaryButton(this, getString(R.string.binding_search));
        searchButton.setOnClickListener(view -> search());
        find.addView(searchButton, AmUi.marginTop(this, 12));
        LinearLayout statusLine = new LinearLayout(this);
        statusLine.setGravity(Gravity.CENTER_VERTICAL);
        busy = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        busy.setIndeterminateTintList(ColorStateList.valueOf(color(R.color.am_accent)));
        LinearLayout.LayoutParams busyParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        busyParams.setMarginEnd(dp(8));
        statusLine.addView(busy, busyParams);
        status = AmUi.text(this, "", 13, color(R.color.am_text_secondary), false);
        statusLine.addView(status, new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f));
        statusRow = statusLine;
        statusRow.setVisibility(View.GONE);
        find.addView(statusLine, AmUi.marginTop(this, 12));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        find.addView(results, AmUi.marginTop(this, 4));
        content.addView(find, AmUi.marginTop(this, 14));

        bindingCount = AmUi.chip(this, "0", color(R.color.am_good));
        content.addView(header(R.string.binding_existing, bindingCount, null), AmUi.marginTop(this, 22));
        bindings = listCard();
        content.addView(bindings, AmUi.marginTop(this, 10));

        TextView clear = AmUi.tonalButton(this, getString(R.string.binding_recent_clear), color(R.color.am_text_secondary));
        clear.setOnClickListener(view -> { AmRecentAlbums.clear(this); showRecent(); });
        content.addView(header(R.string.binding_recent, null, clear), AmUi.marginTop(this, 22));
        TextView recentHint = AmUi.text(this, getString(R.string.binding_recent_hint), 12.5f, color(R.color.am_text_tertiary), false);
        LinearLayout.LayoutParams hintParams = AmUi.marginTop(this, 2);
        hintParams.setMarginStart(dp(4));
        content.addView(recentHint, hintParams);
        recent = listCard();
        content.addView(recent, AmUi.marginTop(this, 10));

        setContentView(root);
        showBindings();
    }

    @Override protected void onResume() {
        super.onResume();
        showRecent();
    }

    @Override protected void onDestroy() {
        if (task != null) task.cancel();
        io.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = AmUi.icon(this, R.drawable.ic_am_back, color(R.color.am_text));
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(color(R.color.am_ripple)), null, null));
        back.setContentDescription(getString(R.string.binding_back));
        back.setOnClickListener(view -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = AmUi.single(AmUi.text(this, getString(R.string.binding_title), 20, color(R.color.am_text), true));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        titleParams.setMarginStart(dp(4));
        bar.addView(title, titleParams);
        return bar;
    }

    private View intro() {
        LinearLayout intro = new LinearLayout(this);
        intro.setBackground(AmUi.round(AmUi.alpha(color(R.color.am_accent), 0x14), dp(18)));
        intro.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.topMargin = dp(1);
        intro.addView(AmUi.icon(this, R.drawable.ic_am_info, color(R.color.am_accent)), iconParams);
        TextView text = AmUi.text(this, getString(R.string.binding_description), 12.5f, color(R.color.am_text_secondary), false);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        textParams.setMarginStart(dp(10));
        intro.addView(text, textParams);
        return intro;
    }

    private View step(int number, int title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = AmUi.text(this, String.valueOf(number), 13, 0xFFFFFFFF, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, AmUi.GRADIENT);
        circle.setShape(GradientDrawable.OVAL);
        badge.setBackground(circle);
        row.addView(badge, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = AmUi.single(AmUi.text(this, getString(title), 16.5f, color(R.color.am_text), true));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        labelParams.setMarginStart(dp(10));
        row.addView(label, labelParams);
        return row;
    }

    private EditText labelled(LinearLayout parent, int label, int hint) {
        TextView name = AmUi.text(this, getString(label), 12.5f, color(R.color.am_text_secondary), true);
        LinearLayout.LayoutParams nameParams = AmUi.marginTop(this, 14);
        nameParams.setMarginStart(dp(4));
        parent.addView(name, nameParams);
        EditText field = AmUi.field(this, hint);
        field.setFilters(new InputFilter[] { new InputFilter.LengthFilter(512) });
        parent.addView(field, AmUi.marginTop(this, 6));
        return field;
    }

    private View header(int title, View afterTitle, View atEnd) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, 0, 0);
        row.addView(AmUi.text(this, getString(title), 17, color(R.color.am_text), true));
        if (afterTitle != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP);
            params.setMarginStart(dp(8));
            row.addView(afterTitle, params);
        }
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (atEnd != null) row.addView(atEnd);
        return row;
    }

    private LinearLayout listCard() {
        LinearLayout card = AmUi.card(this);
        card.setPadding(dp(14), dp(4), dp(14), dp(4));
        return card;
    }

    private void search() {
        String text = terms.getText().toString().trim();
        if (text.isEmpty()) text = album.getText().toString().trim();
        if (text.isEmpty()) { setStatus(getString(R.string.binding_need_terms), Tone.ERROR, false); return; }
        String query = text, market = AmSettings.country(this);
        AmNetwork.Task current = restart();
        results.removeAllViews();
        setStatus(getString(R.string.binding_searching), Tone.NEUTRAL, true);
        if (getWindow().getInsetsController() != null) getWindow().getInsetsController().hide(WindowInsets.Type.ime());
        io.execute(() -> {
            try {
                List<Choice> choices = new ArrayList<>();
                if (query.regionMatches(true, 0, "https://", 0, 8)) {
                    // A pasted album link names the album and its market exactly.
                    AmIdentity.AppleLink link = AmIdentity.link(query);
                    if (link.albumId().isEmpty()) throw new AmFailure(Status.UNSUPPORTED, "song_link");
                    AmPage.Album page = AmPage.album(network.text(AmCatalog.pageUri(link.country(), link.albumId()), 3 * 1024 * 1024, current),
                            link.albumId());
                    choices.add(linked(link, page, current));
                } else {
                    for (AmPage.AlbumHit hit : AmPage.albumHits(network.text(AmCatalog.albumSearchUri(query, market), 2 * 1024 * 1024, current))) {
                        choices.add(new Choice(market, hit.id(), hit.title(), hit.artist(), detail(hit), hit.artwork(), hit.explicit(), null));
                    }
                }
                ui(current, () -> showResults(choices));
            } catch (AmFailure failure) { ui(current, () -> setStatus(message(failure), Tone.ERROR, false)); }
            catch (RuntimeException error) { ui(current, () -> setStatus(getString(R.string.binding_failed, "unexpected"), Tone.ERROR, false)); }
        });
    }

    /** Display details for a pasted link come from a catalog lookup when it answers; the page alone is enough. */
    private Choice linked(AmIdentity.AppleLink link, AmPage.Album page, AmNetwork.Task current) {
        String market = link.country().toUpperCase(Locale.ROOT);
        try {
            for (AmPage.AlbumHit hit : AmPage.albumHits(network.text(AmCatalog.albumLookupUri(link.country(), page.id()), 512 * 1024, current))) {
                if (hit.id().equals(page.id())) {
                    return new Choice(link.country(), page.id(), hit.title(), hit.artist(), detail(hit) + " · " + market, hit.artwork(),
                            hit.explicit(), page);
                }
            }
        } catch (AmFailure ignored) {
            // Lookup is cosmetic; the verified page decides.
        }
        AmIdentity.Track first = page.tracks().get(0);
        return new Choice(link.country(), page.id(), first.album(), first.artist(), market, "", false, page);
    }

    private void bind(Choice choice, Action action) {
        String local = album.getText().toString().trim(), only = artist.getText().toString().trim();
        if (AmIdentity.normalize(local).isEmpty()) {
            setStatus(getString(R.string.binding_need_album), Tone.ERROR, false);
            scroll.smoothScrollTo(0, 0);
            album.requestFocus();
            return;
        }
        AmBindings.Binding binding = new AmBindings.Binding(local, only, choice.country(), choice.id(), choice.title(), choice.artist());
        if (!AmBindings.valid(binding)) { setStatus(getString(R.string.binding_failed, "invalid_binding"), Tone.ERROR, false); return; }
        AmNetwork.Task current = restart();
        pending = action;
        action.busy();
        setStatus(getString(R.string.binding_checking), Tone.NEUTRAL, true);
        io.execute(() -> {
            try {
                AmPage.Album page = choice.page() != null ? choice.page()
                        : AmPage.album(network.text(AmCatalog.pageUri(choice.country(), choice.id()), 3 * 1024 * 1024, current), choice.id());
                if (page.master() == null) throw new AmFailure(Status.NO_MOTION, "confirmed_album_no_motion");
                ui(current, () -> {
                    List<AmBindings.Binding> before = AmBindings.load(this), after = AmBindings.put(before, binding);
                    AmBindings.save(this, after);
                    AmThumbnails.keep(this, choice.artwork(), choice.country(), choice.id());
                    forgetUnused(before, after);
                    pending = null;
                    action.bound();
                    showBindings();
                    setStatus(getString(R.string.binding_saved, local, choice.title()), Tone.SUCCESS, false);
                });
            } catch (AmFailure failure) { ui(current, () -> fail(action, message(failure))); }
            catch (RuntimeException error) { ui(current, () -> fail(action, getString(R.string.binding_failed, "unexpected"))); }
        });
    }

    private void fail(Action action, String text) {
        pending = null;
        action.idle();
        setStatus(text, Tone.ERROR, false);
    }

    private void showResults(List<Choice> choices) {
        results.removeAllViews();
        setStatus(getString(choices.isEmpty() ? R.string.binding_no_results : R.string.binding_pick), Tone.NEUTRAL, false);
        for (int i = 0; i < choices.size(); i++) {
            if (i > 0) results.addView(rowDivider(72));
            results.addView(resultRow(choices.get(i)));
        }
    }

    private View resultRow(Choice choice) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(AmUi.ripple(this, null, dp(14)));
        CoverTile tile = new CoverTile(this, dp(12));
        tile.setPlaceholder(choice.id(), choice.title());
        AmThumbnails.album(this, tile, choice.artwork(), dp(60));
        row.addView(tile, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = AmUi.text(this, choice.title(), 15, color(R.color.am_text), true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(AmUi.single(AmUi.text(this, choice.artist(), 13, color(R.color.am_text_secondary), false)), AmUi.marginTop(this, 2));
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        if (choice.explicit()) {
            TextView explicit = AmUi.chip(this, getString(R.string.binding_explicit), color(R.color.am_text_secondary));
            LinearLayout.LayoutParams explicitParams = new LinearLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP);
            explicitParams.setMarginEnd(dp(6));
            meta.addView(explicit, explicitParams);
        }
        meta.addView(AmUi.single(AmUi.text(this, choice.detail(), 12, color(R.color.am_text_tertiary), false)));
        texts.addView(meta, AmUi.marginTop(this, 4));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        textParams.setMarginStart(dp(12));
        textParams.setMarginEnd(dp(8));
        row.addView(texts, textParams);
        Action action = new Action();
        row.addView(action.root);
        View.OnClickListener click = view -> bind(choice, action);
        row.setOnClickListener(click);
        action.button.setOnClickListener(click);
        return row;
    }

    private void showBindings() {
        bindings.removeAllViews();
        List<AmBindings.Binding> all = AmBindings.load(this);
        bindingCount.setText(String.valueOf(all.size()));
        if (all.isEmpty()) { bindings.addView(empty(R.string.binding_none)); return; }
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) bindings.addView(rowDivider(64));
            bindings.addView(bindingRow(all.get(i)));
        }
    }

    private View bindingRow(AmBindings.Binding binding) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        CoverTile tile = new CoverTile(this, dp(12));
        tile.setPlaceholder(binding.albumId(), binding.title());
        AmThumbnails.bound(this, tile, binding.country(), binding.albumId(), dp(52));
        row.addView(tile, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView local = AmUi.text(this, binding.localAlbum(), 15, color(R.color.am_text), true);
        local.setMaxLines(2);
        local.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(local);
        if (!binding.localArtist().isEmpty()) {
            LinearLayout.LayoutParams onlyParams = new LinearLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP);
            onlyParams.topMargin = dp(3);
            texts.addView(AmUi.chip(this, getString(R.string.binding_only_artist, binding.localArtist()), color(R.color.am_info)), onlyParams);
        }
        LinearLayout target = new LinearLayout(this);
        target.setGravity(Gravity.CENTER_VERTICAL);
        target.addView(AmUi.icon(this, R.drawable.ic_am_forward, color(R.color.am_accent)), new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView catalog = AmUi.single(AmUi.text(this, binding.title() + " — " + binding.artist(), 12.5f, color(R.color.am_text_secondary), false));
        LinearLayout.LayoutParams catalogParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        catalogParams.setMarginStart(dp(5));
        target.addView(catalog, catalogParams);
        LinearLayout.LayoutParams marketParams = new LinearLayout.LayoutParams(AmUi.WRAP, AmUi.WRAP);
        marketParams.setMarginStart(dp(6));
        target.addView(AmUi.chip(this, binding.country().toUpperCase(Locale.ROOT), color(R.color.am_purple)), marketParams);
        texts.addView(target, AmUi.marginTop(this, 4));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        textParams.setMarginStart(dp(12));
        textParams.setMarginEnd(dp(4));
        row.addView(texts, textParams);
        ImageView delete = AmUi.icon(this, R.drawable.ic_am_delete, color(R.color.am_bad));
        delete.setPadding(dp(9), dp(9), dp(9), dp(9));
        delete.setBackground(new RippleDrawable(ColorStateList.valueOf(color(R.color.am_ripple)), null, null));
        delete.setContentDescription(getString(R.string.binding_delete));
        delete.setOnClickListener(view -> confirmDelete(binding));
        row.addView(delete, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return row;
    }

    private void confirmDelete(AmBindings.Binding binding) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.binding_delete_title)
                .setMessage(getString(R.string.binding_delete_message, binding.localAlbum()))
                .setPositiveButton(R.string.binding_delete, (dialog, which) -> {
                    List<AmBindings.Binding> before = AmBindings.load(this), after = AmBindings.remove(before, binding);
                    AmBindings.save(this, after);
                    forgetUnused(before, after);
                    showBindings();
                    setStatus(getString(R.string.binding_deleted), Tone.NEUTRAL, false);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Drops the saved art of albums no binding refers to any more. */
    private void forgetUnused(List<AmBindings.Binding> before, List<AmBindings.Binding> after) {
        for (AmBindings.Binding old : before) {
            boolean used = false;
            for (AmBindings.Binding kept : after) {
                if (kept.country().equals(old.country()) && kept.albumId().equals(old.albumId())) { used = true; break; }
            }
            if (!used) AmThumbnails.forget(this, old.country(), old.albumId());
        }
    }

    private void showRecent() {
        recent.removeAllViews();
        List<AmRecentAlbums.Entry> entries = AmRecentAlbums.load(this);
        if (entries.isEmpty()) { recent.addView(empty(R.string.binding_recent_none)); return; }
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) recent.addView(rowDivider(52));
            recent.addView(recentRow(entries.get(i)));
        }
    }

    private View recentRow(AmRecentAlbums.Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        row.setBackground(AmUi.ripple(this, null, dp(14)));
        CoverTile tile = new CoverTile(this, dp(10));
        tile.setPlaceholder(entry.album(), entry.album());
        row.addView(tile, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(AmUi.single(AmUi.text(this, entry.album(), 14.5f, color(R.color.am_text), true)));
        if (!entry.artist().isEmpty()) {
            texts.addView(AmUi.single(AmUi.text(this, entry.artist(), 12.5f, color(R.color.am_text_secondary), false)), AmUi.marginTop(this, 1));
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, AmUi.WRAP, 1f);
        textParams.setMarginStart(dp(12));
        textParams.setMarginEnd(dp(8));
        row.addView(texts, textParams);
        row.addView(AmUi.chip(this, getString(AmArtworkActivity.outcomeLabel(entry.outcome())),
                color(AmArtworkActivity.outcomeColor(entry.outcome()))));
        row.setOnClickListener(view -> {
            album.setText(entry.album());
            String lead = AmIdentity.leadCredit(entry.artist());
            terms.setText(lead.isEmpty() ? entry.album() : lead + " " + entry.album());
            terms.setSelection(terms.length());
            setStatus(getString(R.string.binding_recent_picked), Tone.NEUTRAL, false);
            scroll.smoothScrollTo(0, 0);
        });
        return row;
    }

    private View empty(int text) {
        TextView view = AmUi.text(this, getString(text), 13, color(R.color.am_text_secondary), false);
        view.setPadding(dp(2), dp(12), dp(2), dp(12));
        return view;
    }

    private View rowDivider(int insetDp) {
        View divider = new View(this);
        divider.setBackgroundColor(color(R.color.am_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AmUi.MATCH, Math.max(1, dp(0.7f)));
        params.setMarginStart(dp(insetDp));
        divider.setLayoutParams(params);
        return divider;
    }

    private void setStatus(String text, Tone tone, boolean working) {
        statusRow.setVisibility(text.isEmpty() && !working ? View.GONE : View.VISIBLE);
        busy.setVisibility(working ? View.VISIBLE : View.GONE);
        status.setText(text);
        status.setTextColor(color(switch (tone) {
            case ERROR -> R.color.am_bad;
            case SUCCESS -> R.color.am_good;
            case NEUTRAL -> R.color.am_text_secondary;
        }));
    }

    private String detail(AmPage.AlbumHit hit) {
        String year = hit.releaseDay().length() >= 4 ? hit.releaseDay().substring(0, 4) : "—";
        return year + " · " + getResources().getQuantityString(R.plurals.binding_tracks, hit.trackCount(), hit.trackCount());
    }

    private String message(AmFailure failure) {
        return switch (failure.reason) {
            case "network_policy" -> getString(R.string.binding_network);
            case "confirmed_album_no_motion", "no_square_motion_asset" -> getString(R.string.binding_no_motion);
            case "motion_asset_unrecognized" -> getString(R.string.binding_motion_unrecognized);
            case "web_schema_changed" -> getString(R.string.binding_page_unreadable);
            case "song_link" -> getString(R.string.binding_song_link);
            case "invalid_apple_link" -> getString(R.string.binding_bad_link);
            case "cancelled" -> "";
            default -> getString(R.string.binding_failed, failure.reason);
        };
    }

    private AmNetwork.Task restart() {
        if (task != null) task.cancel();
        if (pending != null) {
            pending.idle();
            pending = null;
        }
        task = new AmNetwork.Task();
        return task;
    }

    private void ui(AmNetwork.Task owner, Runnable action) {
        runOnUiThread(() -> { if (!isDestroyed() && task == owner) action.run(); });
    }

    private int dp(float value) { return AmUi.dp(this, value); }

    private int color(int res) { return getColor(res); }
}
