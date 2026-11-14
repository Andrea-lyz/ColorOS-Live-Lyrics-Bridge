package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Applies the persisted settings-page locale (default: Chinese). Shared by the Views settings
 * pages and the Compose management pages so both follow one language preference.
 */
public final class SettingsLocaleContext {
    private SettingsLocaleContext() {
    }

    public static Context wrap(Context base) {
        String tag = base.getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(LyricUiSettings.PREF_SETTINGS_LOCALE, null);
        if (tag == null || tag.isEmpty()) return base;
        Locale locale = Locale.forLanguageTag(tag);
        if (locale == null || locale.getLanguage().isEmpty()) return base;
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        return base.createConfigurationContext(configuration);
    }
}
