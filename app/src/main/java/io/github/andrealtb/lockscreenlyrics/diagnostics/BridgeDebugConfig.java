package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Independent of {@code LyricUiConfig}; visual schema stays untouched. */
public final class BridgeDebugConfig {
    public static final String PREFS_NAME = "lockscreen_lyrics_debug";
    public static final int SCHEMA_VERSION = 1;
    public static final String KEY_SCHEMA = "debug_schema";
    public static final String KEY_REVISION = "debug_revision";
    public static final String KEY_MASTER = "debug_logging_enabled";
    public static final String KEY_BOOTSTRAP = "debug_area_bootstrap";
    public static final String KEY_MEDIA = "debug_area_media";
    public static final String KEY_LYRIC = "debug_area_lyric";
    public static final String KEY_RENDERER = "debug_area_renderer";
    public static final String KEY_AOD = "debug_area_aod";
    public static final String KEY_PLAYER_SPECIAL = "debug_area_player_special";
    public static final String KEY_PERFORMANCE = "debug_area_performance";

    public final int schemaVersion;
    public final long revision;
    public final boolean masterEnabled;
    public final boolean bootstrap;
    public final boolean media;
    public final boolean lyric;
    public final boolean renderer;
    public final boolean aod;
    public final boolean playerSpecial;
    public final boolean performance;

    public BridgeDebugConfig(
            int schemaVersion,
            long revision,
            boolean masterEnabled,
            boolean bootstrap,
            boolean media,
            boolean lyric,
            boolean renderer,
            boolean aod,
            boolean playerSpecial,
            boolean performance) {
        this.schemaVersion = schemaVersion <= 0 ? SCHEMA_VERSION : schemaVersion;
        this.revision = Math.max(0L, revision);
        this.masterEnabled = masterEnabled;
        this.bootstrap = bootstrap;
        this.media = media;
        this.lyric = lyric;
        this.renderer = renderer;
        this.aod = aod;
        this.playerSpecial = playerSpecial;
        this.performance = performance;
    }

    public static BridgeDebugConfig disabled() {
        return new BridgeDebugConfig(
                SCHEMA_VERSION, 0L, false, true, true, true, true, true, true, true);
    }

    public static BridgeDebugConfig enabledAll(long revision) {
        return new BridgeDebugConfig(
                SCHEMA_VERSION, revision, true, true, true, true, true, true, true, true);
    }

    public boolean isAreaEnabled(BridgeDebugArea area) {
        if (!masterEnabled || area == null) {
            return false;
        }
        switch (area) {
            case BOOTSTRAP:
                return bootstrap;
            case MEDIA:
                return media;
            case LYRIC:
                return lyric;
            case RENDERER:
                return renderer;
            case AOD:
                return aod;
            case PLAYER_SPECIAL:
                return playerSpecial;
            case PERFORMANCE:
                return performance;
            default:
                return false;
        }
    }

    public String enabledAreasLabel() {
        if (!masterEnabled) {
            return "off";
        }
        List<String> areas = new ArrayList<>();
        for (BridgeDebugArea area : BridgeDebugArea.values()) {
            if (isAreaEnabled(area)) {
                areas.add(area.key);
            }
        }
        return areas.isEmpty() ? "none" : String.join(",", areas);
    }

    public static BridgeDebugConfig load(SharedPreferences preferences) {
        if (preferences == null) {
            return disabled();
        }
        return new BridgeDebugConfig(
                preferences.getInt(KEY_SCHEMA, SCHEMA_VERSION),
                preferences.getLong(KEY_REVISION, 0L),
                preferences.getBoolean(KEY_MASTER, false),
                preferences.getBoolean(KEY_BOOTSTRAP, true),
                preferences.getBoolean(KEY_MEDIA, true),
                preferences.getBoolean(KEY_LYRIC, true),
                preferences.getBoolean(KEY_RENDERER, true),
                preferences.getBoolean(KEY_AOD, true),
                preferences.getBoolean(KEY_PLAYER_SPECIAL, true),
                preferences.getBoolean(KEY_PERFORMANCE, true));
    }

    public static void save(SharedPreferences preferences, BridgeDebugConfig config) {
        if (preferences == null || config == null) {
            return;
        }
        preferences.edit()
                .putInt(KEY_SCHEMA, SCHEMA_VERSION)
                .putLong(KEY_REVISION, config.revision)
                .putBoolean(KEY_MASTER, config.masterEnabled)
                .putBoolean(KEY_BOOTSTRAP, config.bootstrap)
                .putBoolean(KEY_MEDIA, config.media)
                .putBoolean(KEY_LYRIC, config.lyric)
                .putBoolean(KEY_RENDERER, config.renderer)
                .putBoolean(KEY_AOD, config.aod)
                .putBoolean(KEY_PLAYER_SPECIAL, config.playerSpecial)
                .putBoolean(KEY_PERFORMANCE, config.performance)
                .apply();
    }

    public Intent putExtras(Intent intent) {
        if (intent == null) {
            return null;
        }
        intent.putExtra(KEY_SCHEMA, SCHEMA_VERSION);
        intent.putExtra(KEY_REVISION, revision);
        intent.putExtra(KEY_MASTER, masterEnabled);
        intent.putExtra(KEY_BOOTSTRAP, bootstrap);
        intent.putExtra(KEY_MEDIA, media);
        intent.putExtra(KEY_LYRIC, lyric);
        intent.putExtra(KEY_RENDERER, renderer);
        intent.putExtra(KEY_AOD, aod);
        intent.putExtra(KEY_PLAYER_SPECIAL, playerSpecial);
        intent.putExtra(KEY_PERFORMANCE, performance);
        return intent;
    }

    public static BridgeDebugConfig fromIntent(Intent intent, BridgeDebugConfig fallback) {
        BridgeDebugConfig baseline = fallback == null ? disabled() : fallback;
        if (intent == null) {
            return baseline;
        }
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(KEY_MASTER)) {
            return baseline;
        }
        return new BridgeDebugConfig(
                extras.getInt(KEY_SCHEMA, SCHEMA_VERSION),
                extras.getLong(KEY_REVISION, baseline.revision),
                extras.getBoolean(KEY_MASTER, false),
                extras.getBoolean(KEY_BOOTSTRAP, true),
                extras.getBoolean(KEY_MEDIA, true),
                extras.getBoolean(KEY_LYRIC, true),
                extras.getBoolean(KEY_RENDERER, true),
                extras.getBoolean(KEY_AOD, true),
                extras.getBoolean(KEY_PLAYER_SPECIAL, true),
                extras.getBoolean(KEY_PERFORMANCE, true));
    }

    public void putStatus(Bundle result) {
        if (result == null) {
            return;
        }
        result.putLong(KEY_REVISION, revision);
        result.putBoolean(KEY_MASTER, masterEnabled);
        result.putString("debug_areas", enabledAreasLabel());
    }

    public BridgeDebugConfig withRevision(long nextRevision) {
        return new BridgeDebugConfig(
                SCHEMA_VERSION,
                nextRevision,
                masterEnabled,
                bootstrap,
                media,
                lyric,
                renderer,
                aod,
                playerSpecial,
                performance);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BridgeDebugConfig)) {
            return false;
        }
        BridgeDebugConfig that = (BridgeDebugConfig) other;
        return schemaVersion == that.schemaVersion
                && revision == that.revision
                && masterEnabled == that.masterEnabled
                && bootstrap == that.bootstrap
                && media == that.media
                && lyric == that.lyric
                && renderer == that.renderer
                && aod == that.aod
                && playerSpecial == that.playerSpecial
                && performance == that.performance;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                schemaVersion,
                revision,
                masterEnabled,
                bootstrap,
                media,
                lyric,
                renderer,
                aod,
                playerSpecial,
                performance);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "BridgeDebugConfig{rev=%d, master=%s, areas=%s}",
                revision,
                masterEnabled,
                enabledAreasLabel());
    }
}
