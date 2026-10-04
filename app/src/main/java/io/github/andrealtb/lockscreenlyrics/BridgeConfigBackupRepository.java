package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugConfig;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkDisplaySettings;

/** Reads, validates, restores and clears every Bridge-owned preference namespace. */
final class BridgeConfigBackupRepository {
    private static final String[] PREFERENCE_NAMES = {
            LyricUiSettings.PREFERENCES_NAME,
            BridgeDebugConfig.PREFS_NAME,
            ArtworkDisplaySettings.PREFERENCES
    };
    /** Required in every backup; the dynamic artwork domain is newer and optional. */
    private static final String[] REQUIRED_NAMES = {
            LyricUiSettings.PREFERENCES_NAME,
            BridgeDebugConfig.PREFS_NAME
    };

    private BridgeConfigBackupRepository() {
    }

    static String exportAll(Context context) {
        Map<String, Map<String, ?>> namespaces = snapshot(context);
        // Untouched artwork settings stay out, so such a backup still restores on older builds.
        if (namespaces.get(ArtworkDisplaySettings.PREFERENCES).isEmpty()) {
            namespaces.remove(ArtworkDisplaySettings.PREFERENCES);
        }
        return BridgeConfigBackupCodec.encode(namespaces);
    }

    static void restoreAll(Context context, String encoded) {
        BridgeConfigBackupCodec.Backup backup = BridgeConfigBackupCodec.decode(encoded);
        if (!acceptsNamespaces(backup.namespaceNames())) {
            throw new IllegalArgumentException("Backup does not contain every Bridge config domain");
        }
        Map<String, Map<String, ?>> previous = snapshot(context);
        try {
            for (String name : PREFERENCE_NAMES) {
                if (!write(context, name, backup.namespace(name))) {
                    throw new IllegalStateException("Could not restore Bridge preferences");
                }
            }
        } catch (RuntimeException error) {
            for (String name : PREFERENCE_NAMES) write(context, name, previous.get(name));
            throw error;
        }
        // A restored provider reference is only a reference: verify it again before it can run.
        ArtworkSettingsRepository.revalidate(context);
    }

    /** Every required domain, nothing unknown; older backups without the artwork domain still restore. */
    static boolean acceptsNamespaces(Set<String> names) {
        return names.containsAll(Arrays.asList(REQUIRED_NAMES))
                && new LinkedHashSet<>(Arrays.asList(PREFERENCE_NAMES)).containsAll(names);
    }

    static void clearAll(Context context) {
        Map<String, Map<String, ?>> previous = snapshot(context);
        try {
            for (String name : PREFERENCE_NAMES) {
                if (!context.getSharedPreferences(name, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .commit()) {
                    throw new IllegalStateException("Could not reset Bridge preferences");
                }
            }
        } catch (RuntimeException error) {
            for (String name : PREFERENCE_NAMES) write(context, name, previous.get(name));
            throw error;
        }
    }

    private static Map<String, Map<String, ?>> snapshot(Context context) {
        LinkedHashMap<String, Map<String, ?>> namespaces = new LinkedHashMap<>();
        for (String name : PREFERENCE_NAMES) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : context.getSharedPreferences(
                    name,
                    Context.MODE_PRIVATE).getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Set) {
                    value = new LinkedHashSet<>((Set<?>) value);
                }
                values.put(entry.getKey(), value);
            }
            namespaces.put(name, values);
        }
        return namespaces;
    }

    @SuppressWarnings("unchecked")
    private static boolean write(Context context, String name, Map<String, ?> values) {
        SharedPreferences.Editor editor = context.getSharedPreferences(
                name,
                Context.MODE_PRIVATE).edit().clear();
        if (values != null) {
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    editor.putBoolean(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(entry.getKey(), (Float) value);
                } else if (value instanceof String) {
                    editor.putString(entry.getKey(), (String) value);
                } else if (value instanceof Set) {
                    editor.putStringSet(
                            entry.getKey(),
                            new LinkedHashSet<>((Set<String>) value));
                } else {
                    throw new IllegalArgumentException("Unsupported Bridge preference type");
                }
            }
        }
        return editor.commit();
    }
}
