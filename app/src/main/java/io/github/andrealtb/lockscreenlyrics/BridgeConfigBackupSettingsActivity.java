package io.github.andrealtb.lockscreenlyrics;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Global Bridge configuration backup, restore and reset page. */
public final class BridgeConfigBackupSettingsActivity extends SettingsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.config_backup_title);
        setContentView(createContent());
    }

    private View createContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int screenPadding = settingsScreenPadding();
        content.setPadding(
                screenPadding,
                screenPadding,
                screenPadding,
                settingsScreenBottomPadding());
        content.setBackgroundColor(settingsBackgroundColor());
        installSettingsInsets(content);

        TextView description = text(
                getString(R.string.config_backup_description),
                13,
                getColor(R.color.settings_text_secondary));
        description.setLineSpacing(0f, 1.2f);
        description.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(description, matchWrap());

        LinearLayout scopeCard = card();
        scopeCard.addView(section(
                R.drawable.ic_sec_compat,
                getString(R.string.config_backup_scope_title),
                "ALL"));
        TextView scope = text(
                getString(R.string.config_backup_scope_body),
                11.5f,
                getColor(R.color.settings_text_secondary));
        scope.setLineSpacing(0f, 1.25f);
        scope.setPadding(dp(17), dp(7), dp(17), dp(14));
        scopeCard.addView(scope, matchWrap());
        content.addView(scopeCard, marginBottom(dp(12)));

        LinearLayout actionCard = card();
        actionCard.addView(section(
                R.drawable.ic_sec_compat,
                getString(R.string.config_backup_actions_title),
                "BACKUP"));
        Button copy = button(getString(R.string.config_backup_copy));
        copy.setOnClickListener(view -> copyBackup());
        actionCard.addView(actionRow(copy), matchWrap());
        addCardDivider(actionCard);
        Button restore = button(getString(R.string.config_backup_restore));
        restore.setOnClickListener(view -> confirmRestore());
        actionCard.addView(actionRow(restore), matchWrap());
        addCardDivider(actionCard);
        Button reset = button(getString(R.string.config_backup_reset));
        reset.setTextColor(getColor(R.color.settings_error));
        reset.setOnClickListener(view -> showSettingsConfirmDialog(
                R.string.config_backup_reset_confirm_title,
                R.string.config_backup_reset_confirm_message,
                R.string.config_backup_reset_confirm,
                true,
                this::performReset));
        actionCard.addView(actionRow(reset), matchWrap());
        content.addView(actionCard, marginBottom(dp(12)));

        TextView warning = text(
                getString(R.string.config_backup_downgrade_warning),
                10.5f,
                getColor(R.color.settings_text_muted));
        warning.setLineSpacing(0f, 1.2f);
        warning.setPadding(dp(5), dp(2), dp(5), dp(10));
        content.addView(warning, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(settingsBackgroundColor());
        page.addView(settingsAppBar(
                getString(R.string.config_backup_title),
                null,
                this::finish), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                settingsActionBarHeight()));
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return page;
    }

    private View actionRow(Button button) {
        FrameLayout row = new FrameLayout(this);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(46),
                Gravity.CENTER);
        row.addView(button, params);
        return row;
    }

    private void copyBackup() {
        ClipboardManager clipboard = clipboard();
        if (clipboard == null) {
            Toast.makeText(this, R.string.config_backup_clipboard_unavailable, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.config_backup_clip_label),
                BridgeConfigBackupRepository.exportAll(this)));
        Toast.makeText(this, R.string.config_backup_copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmRestore() {
        ClipboardManager clipboard = clipboard();
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence text = clip == null || clip.getItemCount() == 0
                ? null
                : clip.getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.config_backup_clipboard_empty, Toast.LENGTH_LONG).show();
            return;
        }
        String encoded = text.toString();
        showSettingsConfirmDialog(
                R.string.config_backup_restore_confirm_title,
                R.string.config_backup_restore_confirm_message,
                R.string.config_backup_restore_confirm,
                false,
                () -> performRestore(encoded));
    }

    private void performRestore(String encoded) {
        try {
            BridgeConfigBackupRepository.restoreAll(this, encoded);
        } catch (RuntimeException error) {
            Toast.makeText(
                    this,
                    getString(R.string.config_backup_restore_failed, safeMessage(error)),
                    Toast.LENGTH_LONG).show();
            return;
        }
        finishAfterRuntimeSync(
                R.string.config_backup_restored,
                R.string.config_backup_restored_restart);
    }

    private void performReset() {
        try {
            BridgeConfigBackupRepository.clearAll(this);
        } catch (RuntimeException error) {
            Toast.makeText(
                    this,
                    getString(R.string.config_backup_reset_failed, safeMessage(error)),
                    Toast.LENGTH_LONG).show();
            return;
        }
        finishAfterRuntimeSync(
                R.string.config_backup_reset_done,
                R.string.config_backup_reset_restart);
    }

    private void finishAfterRuntimeSync(int successMessage, int restartMessage) {
        try {
            BridgeConfigRuntimeSync.applyAll(this);
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, restartMessage, Toast.LENGTH_LONG).show();
        }
        setResult(RESULT_OK);
        finish();
    }

    private ClipboardManager clipboard() {
        return (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private static String safeMessage(RuntimeException error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "unknown" : message;
    }
}
