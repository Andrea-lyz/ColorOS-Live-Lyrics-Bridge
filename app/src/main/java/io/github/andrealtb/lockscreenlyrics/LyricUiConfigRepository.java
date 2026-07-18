package io.github.andrealtb.lockscreenlyrics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.LinkedHashMap;
import java.util.Map;

final class LyricUiConfigRepository {
    private static final String ZERO_SPACING_DEFAULT_MIGRATED =
            "line_spacing_zero_default_migrated";

    private LyricUiConfigRepository() {
    }

    static LyricUiConfig load(SharedPreferences preferences) {
        LyricUiConfig decoded = LyricUiConfigCodec.decode(
                preferences == null ? null : preferences.getAll(),
                LyricUiConfig.defaults(),
                true);
        LyricUiConfig config = decoded == null ? LyricUiConfig.defaults() : decoded;
        if (preferences != null
                && !preferences.getBoolean(ZERO_SPACING_DEFAULT_MIGRATED, false)) {
            if (LyricUiPreset.hasLegacyPresetSpacing(config)) {
                config = config.buildUpon().lineSpacingTenthsDp(0).build();
            }
            preferences.edit().putBoolean(ZERO_SPACING_DEFAULT_MIGRATED, true).apply();
        }
        if (preferences != null
                && (!preferences.contains(LyricUiConfigCodec.SCHEMA)
                || preferences.getInt(LyricUiConfigCodec.SCHEMA, -1)
                != LyricUiConfig.SCHEMA_VERSION
                || !preferences.contains(LyricUiConfigCodec.LINE_SPACING)
                || !preferences.contains(LyricUiConfigCodec.WRAPPED_LINE_SPACING))) {
            save(preferences, config);
            preferences.edit().remove(
                    LyricUiConfigCodec.LEGACY_METADATA_CLEANUP_RULES).apply();
        }
        return config;
    }

    static void save(SharedPreferences preferences, LyricUiConfig config) {
        if (preferences == null || config == null) return;
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : LyricUiConfigCodec.encode(config).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else editor.putString(entry.getKey(), String.valueOf(value));
        }
        editor.apply();
    }

    static Intent putSnapshot(Intent intent, LyricUiConfig config) {
        for (Map.Entry<String, Object> entry : LyricUiConfigCodec.encode(config).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) intent.putExtra(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) intent.putExtra(entry.getKey(), (Integer) value);
            else intent.putExtra(entry.getKey(), String.valueOf(value));
        }
        return intent;
    }

    static Bundle putSnapshot(Bundle bundle, LyricUiConfig config) {
        Bundle target = bundle == null ? new Bundle() : bundle;
        for (Map.Entry<String, Object> entry : LyricUiConfigCodec.encode(config).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) target.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) target.putInt(entry.getKey(), (Integer) value);
            else target.putString(entry.getKey(), String.valueOf(value));
        }
        return target;
    }

    static LyricUiConfig decodePartial(Intent intent, LyricUiConfig baseline) {
        if (intent == null) return baseline;
        Bundle extras = intent.getExtras();
        if (extras == null) return baseline;
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (String key : LyricUiConfigCodec.encode(baseline).keySet()) {
            if (extras.containsKey(key)) values.put(key, extras.get(key));
        }
        return LyricUiConfigCodec.decode(values, baseline, false);
    }

    static LyricUiConfig decodeSnapshot(Bundle extras, LyricUiConfig baseline) {
        if (extras == null) return baseline;
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (String key : LyricUiConfigCodec.encode(baseline).keySet()) {
            if (extras.containsKey(key)) values.put(key, extras.get(key));
        }
        return LyricUiConfigCodec.decode(values, baseline, false);
    }
}
