package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugConfig;

/** Replays restored app-side settings into the live SystemUI configuration domains. */
final class BridgeConfigRuntimeSync {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private BridgeConfigRuntimeSync() {
    }

    static void applyAll(Context context) {
        SharedPreferences main = context.getSharedPreferences(
                LyricUiSettings.PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        LyricUiConfig config = LyricUiConfigRepository.load(main);
        long revision = LyricUiSettings.newSettingsRevision();
        sendStyle(context, config, revision);
        sendPlayerTranslation(context, main, config, revision);
        sendCleanup(context, main);
        sendDebug(context, revision);
    }

    private static void sendStyle(Context context, LyricUiConfig config, long revision) {
        Intent intent = LyricUiConfigRepository.putSnapshot(
                new Intent(LyricUiSettings.ACTION_STYLE_CHANGED)
                        .setPackage(SYSTEM_UI_PACKAGE)
                        .putExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, revision)
                        .putExtra(
                                LyricUiSettings.EXTRA_SETTINGS_SOURCE,
                                LyricUiSettings.SOURCE_CONFIG_BACKUP),
                config);
        context.sendBroadcast(intent);
    }

    private static void sendPlayerTranslation(
            Context context,
            SharedPreferences preferences,
            LyricUiConfig config,
            long revision) {
        ArrayList<String> packages = new ArrayList<>();
        ArrayList<Boolean> defaults = new ArrayList<>();
        ArrayList<Boolean> buttons = new ArrayList<>();
        for (PlayerTranslationSettings.Entry entry : PlayerTranslationSettings.entries()) {
            if (!entry.supportsTranslation) continue;
            for (String packageName : entry.playerPackages) {
                packages.add(packageName);
                defaults.add(preferences.getBoolean(
                        LyricUiSettings.translationDefaultKeyForPackage(packageName),
                        config.defaultTranslationEnabled));
                buttons.add(preferences.getBoolean(
                        LyricUiSettings.translationButtonKeyForPackage(packageName),
                        true));
            }
        }
        boolean[] defaultValues = new boolean[defaults.size()];
        boolean[] buttonValues = new boolean[buttons.size()];
        for (int index = 0; index < defaults.size(); index++) {
            defaultValues[index] = defaults.get(index);
            buttonValues[index] = buttons.get(index);
        }
        String[] packageValues = packages.toArray(new String[0]);
        Intent intent = new Intent(LyricUiSettings.ACTION_PLAYER_TRANSLATION_SETTINGS_CHANGED)
                .setPackage(SYSTEM_UI_PACKAGE)
                .putExtra(
                        LyricUiSettings.EXTRA_DEFAULT_TRANSLATION_ENABLED,
                        config.defaultTranslationEnabled)
                .putExtra(LyricUiSettings.EXTRA_PLAYER_TRANSLATION_PACKAGES, packageValues)
                .putExtra(LyricUiSettings.EXTRA_PLAYER_TRANSLATION_DEFAULTS, defaultValues)
                .putExtra(LyricUiSettings.EXTRA_TRANSLATION_BUTTON_PACKAGES, packageValues)
                .putExtra(LyricUiSettings.EXTRA_TRANSLATION_BUTTON_VALUES, buttonValues)
                .putExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, revision)
                .putExtra(
                        LyricUiSettings.EXTRA_SETTINGS_SOURCE,
                        LyricUiSettings.SOURCE_CONFIG_BACKUP);
        context.sendBroadcast(intent);
    }

    private static void sendCleanup(Context context, SharedPreferences preferences) {
        LyricContentCleanupConfig config = LyricContentCleanupRepository.load(preferences);
        String encoded = config.encode();
        Intent intent = new Intent(LyricUiSettings.ACTION_CONTENT_CLEANUP_CHANGED)
                .setPackage(LyricContentCleanupConfigTransfer.SYSTEM_UI_PACKAGE);
        if (encoded.length() <= LyricContentCleanupConfigTransfer.LEGACY_INLINE_MAX_CHARS) {
            intent.putExtra(LyricUiSettings.EXTRA_CONTENT_CLEANUP_CONFIG, encoded);
        }
        if (!LyricContentCleanupConfigTransfer.grantSystemUiReadAccess(context)) {
            throw new IllegalStateException("Could not grant SystemUI cleanup-config access");
        }
        LyricContentCleanupConfigTransfer.attachConfigUri(intent);
        context.sendBroadcast(intent);
    }

    private static void sendDebug(Context context, long revision) {
        SharedPreferences preferences = context.getSharedPreferences(
                BridgeDebugConfig.PREFS_NAME,
                Context.MODE_PRIVATE);
        BridgeDebugConfig config = BridgeDebugConfig.load(preferences).withRevision(revision);
        BridgeDebugConfig.save(preferences, config);
        Intent intent = config.putExtras(
                new Intent(LyricUiSettings.ACTION_DEBUG_SETTINGS_CHANGED)
                        .setPackage(SYSTEM_UI_PACKAGE)
                        .putExtra(LyricUiSettings.EXTRA_CONFIG_REVISION, revision)
                        .putExtra(
                                LyricUiSettings.EXTRA_SETTINGS_SOURCE,
                                LyricUiSettings.SOURCE_CONFIG_BACKUP));
        context.sendBroadcast(intent);
    }
}
