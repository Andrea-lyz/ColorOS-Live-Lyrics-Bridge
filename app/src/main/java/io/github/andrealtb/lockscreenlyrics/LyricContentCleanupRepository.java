package io.github.andrealtb.lockscreenlyrics;

import android.content.SharedPreferences;

final class LyricContentCleanupRepository {
    static final String PREFERENCE_KEY = "lyric_content_cleanup_config";

    private LyricContentCleanupRepository() {
    }

    static LyricContentCleanupConfig load(SharedPreferences preferences) {
        if (preferences == null) return LyricContentCleanupConfig.defaults();
        LyricContentCleanupConfig decoded = LyricContentCleanupConfig.decode(
                preferences.getString(PREFERENCE_KEY, ""));
        return decoded == null ? LyricContentCleanupConfig.defaults() : decoded;
    }

    static void save(
            SharedPreferences preferences,
            LyricContentCleanupConfig config) {
        if (preferences == null || config == null) return;
        preferences.edit().putString(PREFERENCE_KEY, config.encode()).apply();
    }
}
