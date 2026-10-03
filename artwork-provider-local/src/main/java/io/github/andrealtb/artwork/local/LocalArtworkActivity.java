package io.github.andrealtb.artwork.local;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.andrealtb.artwork.contract.ArtworkSigningIdentity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalArtworkActivity extends Activity {
    private static final int IMPORT_VIDEO = 1;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button importButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(),
                    padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(),
                    padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView description = new TextView(this);
        description.setText(R.string.description);
        content.addView(description);
        status = new TextView(this);
        status.setPadding(0, padding, 0, padding);
        status.setText(LocalArtworkStore.get(this).selected().isEmpty() ? R.string.empty : R.string.ready);
        content.addView(status);
        importButton = new Button(this);
        importButton.setText(R.string.import_video);
        importButton.setOnClickListener(view -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/mp4")
                        .addCategory(Intent.CATEGORY_OPENABLE), IMPORT_VIDEO));
        content.addView(importButton);
        Button allow = new Button(this);
        allow.setText(R.string.allow_bridge);
        allow.setOnClickListener(view -> {
            try {
                String identity = ArtworkSigningIdentity.read(getPackageManager(), ArtworkCallerPolicy.BRIDGE);
                getSharedPreferences(ArtworkCallerPolicy.PREFERENCES, MODE_PRIVATE).edit()
                        .putString(ArtworkCallerPolicy.SIGNER, identity).apply();
                status.setText(R.string.allowed);
            } catch (Exception error) {
                status.setText(R.string.missing_bridge);
            }
        });
        content.addView(allow);
        Button revoke = new Button(this);
        revoke.setText(R.string.revoke_bridge);
        revoke.setOnClickListener(view -> {
            getSharedPreferences(ArtworkCallerPolicy.PREFERENCES, MODE_PRIVATE).edit().clear().apply();
            status.setText(R.string.revoked);
        });
        content.addView(revoke);
        setContentView(content);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_VIDEO || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null || !"content".equals(uri.getScheme())) return;
        importButton.setEnabled(false);
        status.setText(R.string.importing);
        worker.execute(() -> {
            int result;
            try {
                LocalArtworkStore.get(this).importVideo(uri);
                result = R.string.ready;
            } catch (Exception error) {
                result = R.string.failed;
            }
            int finalResult = result;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                importButton.setEnabled(true);
                status.setText(finalResult);
            });
        });
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
