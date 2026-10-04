package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import io.github.andrealtb.artwork.contract.ArtworkContract;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Saved dynamic artwork display settings: an own preference domain and broadcast, never part of
 * LyricUiConfig. The app holds the source copy; SystemUI keeps the last applied copy so the display
 * survives a SystemUI restart. A provider selection is only a reference: SystemUI verifies the
 * component, signer and protocol again before every bind.
 */
public record ArtworkDisplaySettings(long revision, boolean enabled, String component, String signer,
        int protocolMajor, boolean cardEnabled, boolean immersiveEnabled, boolean keepAwake) {
    public static final String PREFERENCES = "lockscreen_lyrics_artwork";
    public static final String ACTION_CHANGED = "io.github.andrealtb.lockscreenlyrics.action.ARTWORK_SETTINGS_CHANGED";
    public static final String ACTION_REQUEST_STATUS = "io.github.andrealtb.lockscreenlyrics.action.REQUEST_ARTWORK_STATUS";
    public static final String EXTRA_RESULT_RECEIVER = "result_receiver";
    public static final int SCHEMA = 1;
    static final String KEY_SCHEMA = "artwork_schema";
    static final String KEY_REVISION = "artwork_revision";
    static final String KEY_ENABLED = "artwork_enabled";
    static final String KEY_COMPONENT = "artwork_component";
    static final String KEY_SIGNER = "artwork_signer";
    static final String KEY_PROTOCOL = "artwork_protocol_major";
    static final String KEY_CARD = "artwork_card_enabled";
    static final String KEY_IMMERSIVE = "artwork_immersive_enabled";
    static final String KEY_KEEP_AWAKE = "artwork_keep_awake";
    /** Status reply from SystemUI: which settings it runs and what the display last did. */
    public static final String STATUS_REVISION = "artwork_status_revision";
    public static final String STATUS_SOURCE = "artwork_status_source";
    public static final String STATUS_ACTIVE = "artwork_status_active";
    public static final String STATUS_STATE = "artwork_status_state";
    public static final String STATUS_AGE_MS = "artwork_status_age_ms";
    public static final String STATUS_HOOKS = "artwork_status_hooks";
    /** A real package name (dotted Java identifiers) and a class name; nothing path-like. */
    private static final Pattern COMPONENT = Pattern.compile(
            "(?=.{3,511}$)[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+/\\.?[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern SIGNER = Pattern.compile("[0-9a-f]{64}(:[0-9a-f]{64}){0,7}");

    public static ArtworkDisplaySettings defaults() {
        return new ArtworkDisplaySettings(0, false, "", "", ArtworkContract.MAJOR, true, true, false);
    }

    /** A complete, current-protocol provider reference; whether it is still installed is checked elsewhere. */
    public boolean selected() {
        return COMPONENT.matcher(component).matches() && SIGNER.matcher(signer).matches()
                && protocolMajor == ArtworkContract.MAJOR;
    }

    /** Whether SystemUI should run the display at all. */
    public boolean active() {
        return enabled && selected() && (cardEnabled || immersiveEnabled);
    }

    public ArtworkPlaybackConfig playback() {
        if (!active()) return ArtworkPlaybackConfig.DISABLED;
        return new ArtworkPlaybackConfig(true, ComponentName.unflattenFromString(component), signer, revision,
                false, keepAwake, cardEnabled, immersiveEnabled);
    }

    public ArtworkDisplaySettings withRevision(long value) {
        return new ArtworkDisplaySettings(value, enabled, component, signer, protocolMajor, cardEnabled, immersiveEnabled, keepAwake);
    }
    public ArtworkDisplaySettings withEnabled(boolean value) {
        return new ArtworkDisplaySettings(revision, value, component, signer, protocolMajor, cardEnabled, immersiveEnabled, keepAwake);
    }
    public ArtworkDisplaySettings withSelection(String flattenedComponent, String signingIdentity) {
        return new ArtworkDisplaySettings(revision, enabled, flattenedComponent == null ? "" : flattenedComponent,
                signingIdentity == null ? "" : signingIdentity, ArtworkContract.MAJOR, cardEnabled, immersiveEnabled, keepAwake);
    }
    public ArtworkDisplaySettings withCard(boolean value) {
        return new ArtworkDisplaySettings(revision, enabled, component, signer, protocolMajor, value, immersiveEnabled, keepAwake);
    }
    public ArtworkDisplaySettings withImmersive(boolean value) {
        return new ArtworkDisplaySettings(revision, enabled, component, signer, protocolMajor, cardEnabled, value, keepAwake);
    }
    public ArtworkDisplaySettings withKeepAwake(boolean value) {
        return new ArtworkDisplaySettings(revision, enabled, component, signer, protocolMajor, cardEnabled, immersiveEnabled, value);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(KEY_SCHEMA, SCHEMA);
        values.put(KEY_REVISION, revision);
        values.put(KEY_ENABLED, enabled);
        values.put(KEY_COMPONENT, component);
        values.put(KEY_SIGNER, signer);
        values.put(KEY_PROTOCOL, protocolMajor);
        values.put(KEY_CARD, cardEnabled);
        values.put(KEY_IMMERSIVE, immersiveEnabled);
        values.put(KEY_KEEP_AWAKE, keepAwake);
        return values;
    }

    /**
     * Lenient for stored values: a missing or mistyped field takes its default, and a malformed
     * selection is cleared, so damaged storage can only turn the display off, never point it elsewhere.
     */
    public static ArtworkDisplaySettings fromMap(Map<String, ?> values) {
        ArtworkDisplaySettings defaults = defaults();
        if (values == null) return defaults;
        String component = text(values.get(KEY_COMPONENT)), signer = text(values.get(KEY_SIGNER));
        int protocol = values.get(KEY_PROTOCOL) instanceof Integer major ? major : defaults.protocolMajor;
        if (!COMPONENT.matcher(component).matches() || !SIGNER.matcher(signer).matches()) {
            component = "";
            signer = "";
            protocol = ArtworkContract.MAJOR;
        }
        return new ArtworkDisplaySettings(
                values.get(KEY_REVISION) instanceof Long value ? Math.max(0, value) : 0,
                flag(values.get(KEY_ENABLED), defaults.enabled), component, signer, protocol,
                flag(values.get(KEY_CARD), defaults.cardEnabled), flag(values.get(KEY_IMMERSIVE), defaults.immersiveEnabled),
                flag(values.get(KEY_KEEP_AWAKE), defaults.keepAwake));
    }

    /** Strict for transport: only a complete snapshot of this schema is accepted. */
    public static ArtworkDisplaySettings fromIntent(Intent intent) {
        Bundle extras = intent == null ? null : intent.getExtras();
        if (extras == null || extras.getInt(KEY_SCHEMA, -1) != SCHEMA) throw new IllegalArgumentException("artwork_settings_schema");
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : new String[] {KEY_REVISION, KEY_ENABLED, KEY_COMPONENT, KEY_SIGNER, KEY_PROTOCOL,
                KEY_CARD, KEY_IMMERSIVE, KEY_KEEP_AWAKE}) {
            if (!extras.containsKey(key)) throw new IllegalArgumentException("artwork_settings_incomplete");
            values.put(key, extras.get(key));
        }
        return fromMap(values);
    }

    public Intent putExtras(Intent intent) {
        return intent.putExtra(KEY_SCHEMA, SCHEMA).putExtra(KEY_REVISION, revision).putExtra(KEY_ENABLED, enabled)
                .putExtra(KEY_COMPONENT, component).putExtra(KEY_SIGNER, signer).putExtra(KEY_PROTOCOL, protocolMajor)
                .putExtra(KEY_CARD, cardEnabled).putExtra(KEY_IMMERSIVE, immersiveEnabled).putExtra(KEY_KEEP_AWAKE, keepAwake);
    }

    public static ArtworkDisplaySettings load(SharedPreferences preferences) {
        return preferences == null ? defaults() : fromMap(preferences.getAll());
    }

    /** Synchronous: the settings page reports a failed write. */
    public boolean save(SharedPreferences preferences) {
        return editor(preferences).commit();
    }

    /** Asynchronous, for SystemUI's copy written on its main thread. */
    public void persist(SharedPreferences preferences) {
        editor(preferences).apply();
    }

    private SharedPreferences.Editor editor(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, Object> entry : toMap().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean flag) editor.putBoolean(entry.getKey(), flag);
            else if (value instanceof Integer number) editor.putInt(entry.getKey(), number);
            else if (value instanceof Long number) editor.putLong(entry.getKey(), number);
            else editor.putString(entry.getKey(), (String) value);
        }
        return editor;
    }

    private static String text(Object value) {
        return value instanceof String string && string.length() <= 1024 ? string : "";
    }

    private static boolean flag(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }
}
