package io.github.andrealtb.artwork.am;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.andrealtb.artwork.contract.ArtworkSigningIdentity;

public final class AmArtworkActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                    pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom()); return insets;
        });
        TextView description = new TextView(this); description.setText(R.string.description); content.addView(description);
        CheckBox enabled = new CheckBox(this); enabled.setText(R.string.enable);
        enabled.setChecked(AmSettings.enabled(this)); content.addView(enabled);
        CheckBox metered = new CheckBox(this); metered.setText(R.string.metered);
        metered.setChecked(AmSettings.prefs(this).getBoolean("metered", false)); content.addView(metered);
        CheckBox debug = new CheckBox(this); debug.setText(R.string.debug);
        debug.setChecked(AmSettings.prefs(this).getBoolean("debug", false)); content.addView(debug);
        EditText country = new EditText(this); country.setSingleLine(true); country.setHint(R.string.country);
        country.setText(AmSettings.country(this)); content.addView(country);
        TextView status = new TextView(this); content.addView(status);
        Button save = new Button(this); save.setText(R.string.save); content.addView(save);
        save.setOnClickListener(view -> {
            String code = country.getText().toString().trim();
            if (!code.matches("[a-zA-Z]{2}")) { status.setText(R.string.country_invalid); return; }
            AmSettings.prefs(this).edit().putBoolean("enabled", enabled.isChecked()).putBoolean("metered", metered.isChecked())
                    .putBoolean("debug", debug.isChecked()).putString("country", code.toLowerCase(java.util.Locale.ROOT)).apply();
            status.setText(R.string.saved);
        });
        Button allow = new Button(this); allow.setText(R.string.allow_bridge); content.addView(allow);
        allow.setOnClickListener(view -> {
            try {
                String signer = ArtworkSigningIdentity.read(getPackageManager(), ArtworkCallerPolicy.BRIDGE);
                getSharedPreferences(ArtworkCallerPolicy.PREFERENCES, MODE_PRIVATE).edit().putString(ArtworkCallerPolicy.SIGNER, signer).apply();
                status.setText(R.string.allowed);
            } catch (Exception error) { status.setText(R.string.bridge_missing); }
        });
        Button revoke = new Button(this); revoke.setText(R.string.revoke); content.addView(revoke);
        revoke.setOnClickListener(view -> {
            AmSettings.prefs(this).edit().putBoolean("enabled", false).apply(); enabled.setChecked(false);
            getSharedPreferences(ArtworkCallerPolicy.PREFERENCES, MODE_PRIVATE).edit().clear().apply(); status.setText(R.string.revoked);
        });
        Button clear = new Button(this); clear.setText(R.string.clear); content.addView(clear);
        clear.setOnClickListener(view -> { AmCache.get(this).clear(); status.setText(R.string.cleared); });
        Button binding = new Button(this); binding.setText(R.string.binding_open); content.addView(binding);
        binding.setOnClickListener(view -> startActivity(new android.content.Intent(this, AmBindingActivity.class)));
        ScrollView scroll = new ScrollView(this); scroll.addView(content); setContentView(scroll);
    }
}
