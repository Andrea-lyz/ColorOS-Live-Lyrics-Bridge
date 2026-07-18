package io.github.andrealtb.lockscreenlyrics;

final class LyricUiSettings {
    private static long lastSettingsRevision;
    static final String PREFERENCES_NAME = "lockscreen_lyrics";
    static final String ACTION_STYLE_CHANGED =
            "io.github.andrealtb.lockscreenlyrics.action.LYRIC_UI_STYLE_CHANGED";
    static final String ACTION_REQUEST_MEDIA_SNAPSHOT =
            "io.github.andrealtb.lockscreenlyrics.action.REQUEST_MEDIA_SNAPSHOT";
    static final String ACTION_PLAYER_TRANSLATION_SETTINGS_CHANGED =
            "io.github.andrealtb.lockscreenlyrics.action.PLAYER_TRANSLATION_SETTINGS_CHANGED";
    static final String ACTION_CONTENT_CLEANUP_CHANGED =
            "io.github.andrealtb.lockscreenlyrics.action.CONTENT_CLEANUP_CHANGED";
    /** Requests a restart of the main SystemUI process through the protected settings receiver. */
    static final String ACTION_RESTART_SYSTEM_UI =
            "io.github.andrealtb.lockscreenlyrics.action.RESTART_SYSTEM_UI";
    static final String EXTRA_SYSTEM_UI_RESTART_RESULT_RECEIVER =
            "system_ui_restart_result_receiver";
    static final String EXTRA_SYSTEM_UI_RESTART_REQUEST_ID =
            "system_ui_restart_request_id";
    static final String RESULT_SYSTEM_UI_RESTART_REQUEST_ID =
            "system_ui_restart_request_id";
    static final String RESULT_SYSTEM_UI_RESTART_ACCEPTED =
            "system_ui_restart_accepted";
    static final int RESULT_SYSTEM_UI_RESTART_ACKNOWLEDGED = 1;
    static final String CHANGE_SETTINGS_PERMISSION =
            "io.github.andrealtb.lockscreenlyrics.permission.CHANGE_LYRIC_UI_SETTINGS";
    static final String EXTRA_CLEAR_TRANSLATION_OVERRIDES =
            "clear_translation_overrides";
    static final String EXTRA_PLAYER_TRANSLATION_PACKAGES =
            "player_translation_packages";
    static final String EXTRA_PLAYER_TRANSLATION_DEFAULTS =
            "player_translation_defaults";
    static final String EXTRA_CLEAR_TRANSLATION_PACKAGES =
            "clear_translation_packages";
    static final String EXTRA_DEFAULT_TRANSLATION_ENABLED =
            "default_translation_enabled";
    static final String EXTRA_RESULT_RECEIVER = "result_receiver";
    static final String EXTRA_CONFIG_REVISION = "config_revision";
    static final String EXTRA_SETTINGS_SOURCE = "settings_source";
    static final String EXTRA_CONTENT_CLEANUP_CONFIG = "content_cleanup_config";
    static final String RESULT_TITLE = "title";
    static final String RESULT_ARTIST = "artist";
    static final String RESULT_ALBUM = "album";
    static final String RESULT_TRACK_KEY = "track_key";
    static final String RESULT_RAW_LYRIC = "raw_lyric";
    static final String RESULT_APPLIED = "applied";
    static final String RESULT_CONFIG_REVISION = "config_revision";
    static final String RESULT_ALIGNMENT = "alignment";
    static final String RESULT_PROCESS = "process";
    static final String RESULT_REASON = "reason";
    static final int RESULT_SETTINGS_APPLIED = 1;
    static final int RESULT_SETTINGS_REJECTED = 2;
    static final String SOURCE_MAIN_SETTINGS = "main-settings";
    static final String SOURCE_PLAYER_TRANSLATION = "player-translation";
    static final String TRANSLATION_PREFERENCE_KEY = "lyric_info_translation_enabled";
    private static final String TRANSLATION_DEFAULT_KEY = "lyric_info_translation_default";
    static final String EXTRA_SCROLL_SCALE_ENABLED = "scroll_scale_enabled";
    static final String EXTRA_INACTIVE_BLUR_ENABLED = "inactive_blur_enabled";
    static final String EXTRA_LINE_TIMED_PROGRESS_ENABLED = "line_timed_progress_enabled";
    static final String EXTRA_TRANSLATION_PROGRESS_ENABLED = "translation_progress_enabled";
    static final String EXTRA_SCREEN_TIMEOUT_ENABLED = "screen_timeout_enabled";
    static final String EXTRA_SCREEN_TIMEOUT_SECONDS = "screen_timeout_seconds";
    static final String KEY_SCROLL_SCALE_ENABLED = LyricUiConfigCodec.SCALE_ENABLED;
    static final String KEY_INACTIVE_BLUR_ENABLED = LyricUiConfigCodec.BLUR_ENABLED;
    static final String KEY_LINE_TIMED_PROGRESS_ENABLED = LyricUiConfigCodec.LINE_TIMED_PROGRESS;
    static final String KEY_TRANSLATION_PROGRESS_ENABLED = LyricUiConfigCodec.TRANSLATION_PROGRESS;
    static final String KEY_SCREEN_TIMEOUT_ENABLED = LyricUiConfigCodec.SCREEN_TIMEOUT_ENABLED;
    static final String KEY_SCREEN_TIMEOUT_SECONDS = LyricUiConfigCodec.SCREEN_TIMEOUT_SECONDS;
    static final boolean DEFAULT_LINE_TIMED_PROGRESS_ENABLED = false;
    static final boolean DEFAULT_TRANSLATION_PROGRESS_ENABLED = false;
    static final boolean DEFAULT_SCREEN_TIMEOUT_ENABLED = true;
    static final int DEFAULT_SCREEN_TIMEOUT_SECONDS = 0;
    static final int MIN_SCREEN_TIMEOUT_SECONDS = 1;
    static final int MAX_SCREEN_TIMEOUT_SECONDS = 86_400;

    static int sanitizeScreenTimeoutSeconds(int seconds) {
        if (seconds <= 0) {
            return DEFAULT_SCREEN_TIMEOUT_SECONDS;
        }
        return Math.min(MAX_SCREEN_TIMEOUT_SECONDS, Math.max(MIN_SCREEN_TIMEOUT_SECONDS, seconds));
    }

    static boolean isTranslationOverrideKey(String key) {
        return key != null && (key.equals(TRANSLATION_PREFERENCE_KEY)
                || key.startsWith(TRANSLATION_PREFERENCE_KEY + "."));
    }

    static String translationDefaultKeyForPackage(String packageName) {
        return TRANSLATION_DEFAULT_KEY + "." + packageName;
    }

    static LyricUiConfig withGlobalTranslationDefault(
            LyricUiConfig baseline,
            boolean enabled) {
        LyricUiConfig base = baseline == null ? LyricUiConfig.defaults() : baseline;
        return base.buildUpon().defaultTranslationEnabled(enabled).build();
    }

    static synchronized long newSettingsRevision() {
        long now = System.currentTimeMillis();
        lastSettingsRevision = Math.max(now, lastSettingsRevision + 1L);
        return lastSettingsRevision;
    }

    private LyricUiSettings() {
    }
}
