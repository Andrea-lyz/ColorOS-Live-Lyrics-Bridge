package io.github.andrealtb.artwork.am;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private record Choice(String country, String id, String title, String artist, String detail, AmPage.Album page) {}
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "artwork-am-binding");
        thread.setDaemon(true);
        return thread;
    });
    private AmNetwork network;
    /** UI thread only: the lookup whose answer the page still wants. */
    private AmNetwork.Task task;
    private EditText album, artist, terms;
    private TextView status;
    private LinearLayout results, bindings, recent;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Context app = getApplicationContext();
        network = new AmNetwork(() -> AmSettings.connected(app), reason -> AmSettings.trace(app, "ARTWORK_AM_BINDING_HTTP", reason));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                    pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom()); return insets;
        });
        text(content, getString(R.string.binding_description));
        album = field(content, R.string.binding_local_album);
        artist = field(content, R.string.binding_local_artist);
        terms = field(content, R.string.binding_terms);
        terms.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        terms.setOnEditorActionListener((view, action, event) -> { if (action != EditorInfo.IME_ACTION_SEARCH) return false; search(); return true; });
        button(content, getString(R.string.binding_search)).setOnClickListener(view -> search());
        status = text(content, "");
        results = section(content);
        header(content, R.string.binding_existing);
        bindings = section(content);
        header(content, R.string.binding_recent);
        recent = section(content);
        button(content, getString(R.string.binding_recent_clear)).setOnClickListener(view -> { AmRecentAlbums.clear(this); showRecent(); });
        ScrollView scroll = new ScrollView(this); scroll.addView(content); setContentView(scroll);
        showBindings();
    }

    @Override protected void onResume() { super.onResume(); showRecent(); }

    @Override protected void onDestroy() {
        if (task != null) task.cancel();
        io.shutdownNow();
        super.onDestroy();
    }

    private void search() {
        String text = terms.getText().toString().trim();
        if (text.isEmpty()) text = album.getText().toString().trim();
        if (text.isEmpty()) { status.setText(R.string.binding_need_terms); return; }
        String query = text, market = AmSettings.country(this);
        AmNetwork.Task current = restart();
        results.removeAllViews();
        status.setText(R.string.binding_searching);
        io.execute(() -> {
            try {
                List<Choice> choices = new ArrayList<>();
                if (query.regionMatches(true, 0, "https://", 0, 8)) {
                    // A pasted album link names the album and its market exactly.
                    AmIdentity.AppleLink link = AmIdentity.link(query);
                    if (link.albumId().isEmpty()) throw new AmFailure(Status.UNSUPPORTED, "song_link");
                    AmPage.Album page = AmPage.album(network.text(AmCatalog.pageUri(link.country(), link.albumId()), 3 * 1024 * 1024, current),
                            link.albumId());
                    AmIdentity.Track first = page.tracks().get(0);
                    choices.add(new Choice(link.country(), page.id(), first.album(), first.artist(), link.country().toUpperCase(Locale.ROOT), page));
                } else {
                    for (AmPage.AlbumHit hit : AmPage.albumHits(network.text(AmCatalog.albumSearchUri(query, market), 2 * 1024 * 1024, current))) {
                        choices.add(new Choice(market, hit.id(), hit.title(), hit.artist(), detail(hit), null));
                    }
                }
                ui(current, () -> showResults(choices));
            } catch (AmFailure failure) { ui(current, () -> status.setText(message(failure))); }
            catch (RuntimeException error) { ui(current, () -> status.setText(getString(R.string.binding_failed, "unexpected"))); }
        });
    }

    private void bind(Choice choice) {
        String local = album.getText().toString().trim(), only = artist.getText().toString().trim();
        if (AmIdentity.normalize(local).isEmpty()) { status.setText(R.string.binding_need_album); album.requestFocus(); return; }
        AmBindings.Binding binding = new AmBindings.Binding(local, only, choice.country(), choice.id(), choice.title(), choice.artist());
        if (!AmBindings.valid(binding)) { status.setText(getString(R.string.binding_failed, "invalid_binding")); return; }
        AmNetwork.Task current = restart();
        status.setText(R.string.binding_checking);
        io.execute(() -> {
            try {
                AmPage.Album page = choice.page() != null ? choice.page()
                        : AmPage.album(network.text(AmCatalog.pageUri(choice.country(), choice.id()), 3 * 1024 * 1024, current), choice.id());
                if (page.master() == null) throw new AmFailure(Status.NO_MOTION, "confirmed_album_no_motion");
                ui(current, () -> {
                    AmBindings.save(this, AmBindings.put(AmBindings.load(this), binding));
                    showBindings();
                    status.setText(getString(R.string.binding_saved, local, choice.title()));
                });
            } catch (AmFailure failure) { ui(current, () -> status.setText(message(failure))); }
            catch (RuntimeException error) { ui(current, () -> status.setText(getString(R.string.binding_failed, "unexpected"))); }
        });
    }

    private void showResults(List<Choice> choices) {
        results.removeAllViews();
        status.setText(choices.isEmpty() ? R.string.binding_no_results : R.string.binding_pick);
        for (Choice choice : choices) {
            button(results, choice.title() + " — " + choice.artist() + "\n" + choice.detail()).setOnClickListener(view -> bind(choice));
        }
    }

    private void showBindings() {
        bindings.removeAllViews();
        List<AmBindings.Binding> all = AmBindings.load(this);
        if (all.isEmpty()) { text(bindings, getString(R.string.binding_none)); return; }
        for (AmBindings.Binding binding : all) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            String market = binding.country().toUpperCase(Locale.ROOT);
            TextView label = new TextView(this);
            label.setText(binding.localArtist().isEmpty()
                    ? getString(R.string.binding_row, binding.localAlbum(), binding.title(), binding.artist(), market)
                    : getString(R.string.binding_row_artist, binding.localAlbum(), binding.localArtist(), binding.title(), binding.artist(), market));
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button delete = new Button(this);
            delete.setAllCaps(false);
            delete.setText(R.string.binding_delete);
            delete.setOnClickListener(view -> {
                AmBindings.save(this, AmBindings.remove(AmBindings.load(this), binding));
                showBindings();
                status.setText(R.string.binding_deleted);
            });
            row.addView(delete);
            bindings.addView(row);
        }
    }

    private void showRecent() {
        recent.removeAllViews();
        List<AmRecentAlbums.Entry> entries = AmRecentAlbums.load(this);
        if (entries.isEmpty()) { text(recent, getString(R.string.binding_recent_none)); return; }
        for (AmRecentAlbums.Entry entry : entries) {
            String label = entry.album() + (entry.artist().isEmpty() ? "" : " — " + entry.artist()) + " · " + getString(switch (entry.outcome()) {
                case MATCHED -> R.string.outcome_matched;
                case BOUND -> R.string.outcome_bound;
                case NO_MOTION -> R.string.outcome_no_motion;
                case UNMATCHED -> R.string.outcome_unmatched;
            });
            button(recent, label).setOnClickListener(view -> {
                album.setText(entry.album());
                String lead = AmIdentity.leadCredit(entry.artist());
                terms.setText(lead.isEmpty() ? entry.album() : lead + " " + entry.album());
                status.setText(R.string.binding_recent_picked);
            });
        }
    }

    private String detail(AmPage.AlbumHit hit) {
        String year = hit.releaseDay().isEmpty() ? "—" : hit.releaseDay().substring(0, 4);
        return year + " · " + getResources().getQuantityString(R.plurals.binding_tracks, hit.trackCount(), hit.trackCount())
                + (hit.explicit() ? " · Explicit" : "");
    }

    private String message(AmFailure failure) {
        return switch (failure.reason) {
            case "network_policy" -> getString(R.string.binding_network);
            case "confirmed_album_no_motion", "no_square_motion_asset" -> getString(R.string.binding_no_motion);
            case "song_link" -> getString(R.string.binding_song_link);
            case "invalid_apple_link" -> getString(R.string.binding_bad_link);
            case "cancelled" -> "";
            default -> getString(R.string.binding_failed, failure.reason);
        };
    }

    private AmNetwork.Task restart() {
        if (task != null) task.cancel();
        task = new AmNetwork.Task();
        return task;
    }

    private void ui(AmNetwork.Task owner, Runnable action) {
        runOnUiThread(() -> { if (!isDestroyed() && task == owner) action.run(); });
    }

    private EditText field(LinearLayout parent, int hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setFilters(new InputFilter[] { new InputFilter.LengthFilter(512) });
        parent.addView(field);
        return field;
    }

    private Button button(LinearLayout parent, String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        parent.addView(button);
        return button;
    }

    private TextView text(LinearLayout parent, String value) {
        TextView view = new TextView(this);
        view.setText(value);
        parent.addView(view);
        return view;
    }

    private void header(LinearLayout parent, int title) {
        TextView view = text(parent, getString(title));
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, (int) (16 * getResources().getDisplayMetrics().density), 0, 0);
    }

    private LinearLayout section(LinearLayout parent) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        parent.addView(section);
        return section;
    }
}
