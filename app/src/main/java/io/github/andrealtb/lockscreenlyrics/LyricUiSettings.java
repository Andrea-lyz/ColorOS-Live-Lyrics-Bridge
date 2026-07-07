package io.github.andrealtb.lockscreenlyrics;

final class LyricUiSettings {
    static final String PREFERENCES_NAME = "lockscreen_lyrics";
    static final String ACTION_STYLE_CHANGED =
            "io.github.andrealtb.lockscreenlyrics.action.LYRIC_UI_STYLE_CHANGED";
    static final String EXTRA_SCROLL_SCALE_ENABLED = "scroll_scale_enabled";
    static final String EXTRA_INACTIVE_BLUR_ENABLED = "inactive_blur_enabled";
    static final String EXTRA_LINE_TIMED_PROGRESS_ENABLED = "line_timed_progress_enabled";
    static final String EXTRA_TRANSLATION_PROGRESS_ENABLED = "translation_progress_enabled";
    static final String EXTRA_SCREEN_TIMEOUT_ENABLED = "screen_timeout_enabled";
    static final String EXTRA_SCREEN_TIMEOUT_SECONDS = "screen_timeout_seconds";
    static final String KEY_SCROLL_SCALE_ENABLED = "lyric_ui_scroll_scale_enabled";
    static final String KEY_INACTIVE_BLUR_ENABLED = "lyric_ui_inactive_blur_enabled";
    static final String KEY_LINE_TIMED_PROGRESS_ENABLED = "lyric_ui_line_timed_progress_enabled";
    static final String KEY_TRANSLATION_PROGRESS_ENABLED = "lyric_ui_translation_progress_enabled";
    static final String KEY_SCREEN_TIMEOUT_ENABLED = "screen_timeout_enabled";
    static final String KEY_SCREEN_TIMEOUT_SECONDS = "screen_timeout_seconds";
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

    private LyricUiSettings() {
    }
}
