package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;

import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkDisplaySettings;

/**
 * Read-only copy of the saved appearance settings for the management UI's Appearance tab (plan
 * §8.4 U-04, §14.18). The tab only shows these values; every edit still goes through
 * {@link LyricUiSettingsActivity} and its draft / save / discard flow, so the configuration keeps
 * a single source of truth. Plain public values because the config types are package-private.
 */
public final class AppearanceSnapshot {
    public enum Preset { DEFAULT, SOFT, VIVID, MINIMAL, CUSTOM }

    public enum Motion { STANDARD, REDUCED, OFF }

    public enum Weight { SYSTEM, REGULAR, MEDIUM, BOLD }

    public enum Alignment { START, CENTER, END }

    /** Dynamic artwork provider state, as the old page's artwork row shows it. */
    public enum Artwork { OFF, READY, PROBLEM }

    public final Preset preset;
    public final String primaryColor;
    public final String glowColor;
    public final boolean glowOn;
    public final boolean glowFollowsCover;
    public final boolean blurOn;
    public final boolean scaleOn;
    public final int mainFontTenthsSp;
    public final int translationFontRatioPercent;
    public final Weight weight;
    public final Alignment alignment;
    public final Motion motion;
    /** 0 means no limit. */
    public final int maxRefreshRateHz;
    public final boolean lineProgressOn;
    public final boolean translationProgressOn;
    public final boolean charLiftOn;
    public final boolean screenTimeoutOn;
    /** 0 keeps the screen on for as long as lyrics show. */
    public final int screenTimeoutSeconds;
    public final Artwork artwork;
    /** Provider label when {@link #artwork} is READY, otherwise null. */
    public final String artworkProvider;

    AppearanceSnapshot(LyricUiConfig config, Artwork artwork, String artworkProvider) {
        LyricUiConfig c = config == null ? LyricUiConfig.defaults() : config;
        preset = Preset.valueOf(LyricUiPreset.detect(c).name());
        primaryColor = c.primaryColor;
        glowColor = c.glowColor;
        glowOn = c.glowEnabled && c.glowIntensityPercent > 0;
        glowFollowsCover = c.glowFollowsCover;
        blurOn = c.blurEnabled;
        scaleOn = c.scaleEnabled;
        mainFontTenthsSp = c.mainFontTenthsSp;
        translationFontRatioPercent = c.translationFontRatioPercent;
        weight = Weight.values()[c.fontWeight];
        alignment = Alignment.values()[c.alignment];
        motion = Motion.values()[c.motionMode];
        maxRefreshRateHz = c.maxRefreshRateHz;
        lineProgressOn = c.lineTimedProgressEnabled;
        translationProgressOn = c.translationProgressEnabled;
        charLiftOn = c.charLiftEnabled;
        screenTimeoutOn = c.screenTimeoutEnabled;
        screenTimeoutSeconds = c.screenTimeoutSeconds;
        this.artwork = artwork == null ? Artwork.OFF : artwork;
        this.artworkProvider = this.artwork == Artwork.READY ? artworkProvider : null;
    }

    /** Reads the saved settings (not any open draft). Call off the main thread when possible. */
    public static AppearanceSnapshot load(Context context) {
        LyricUiConfig config = LyricUiConfigRepository.load(
                context.getSharedPreferences(LyricUiSettings.PREFERENCES_NAME, Context.MODE_PRIVATE));
        ArtworkDisplaySettings settings = ArtworkSettingsRepository.load(context);
        if (!settings.enabled()) return new AppearanceSnapshot(config, Artwork.OFF, null);
        ArtworkSettingsRepository.Selection selection = ArtworkSettingsRepository.check(context, settings);
        boolean ready = settings.active()
                && selection.state() == ArtworkSettingsRepository.ProviderState.READY
                && selection.provider() != null;
        return ready
                ? new AppearanceSnapshot(config, Artwork.READY, selection.provider().label())
                : new AppearanceSnapshot(config, Artwork.PROBLEM, null);
    }
}
