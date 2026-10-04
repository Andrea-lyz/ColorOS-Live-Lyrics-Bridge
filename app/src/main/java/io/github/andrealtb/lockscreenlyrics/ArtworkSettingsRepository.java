package io.github.andrealtb.lockscreenlyrics;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkDisplaySettings;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkProviderDirectory;

/** App-side source copy of the dynamic artwork settings and the provider check behind their status. */
final class ArtworkSettingsRepository {
    enum ProviderState { NONE, READY, MISSING, CHANGED }

    record Selection(ProviderState state, ArtworkProviderDirectory.Provider provider) {}

    private ArtworkSettingsRepository() {
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(ArtworkDisplaySettings.PREFERENCES, Context.MODE_PRIVATE);
    }

    static ArtworkDisplaySettings load(Context context) {
        return ArtworkDisplaySettings.load(preferences(context));
    }

    /** Saves with a fresh revision and replays the result to SystemUI. */
    static ArtworkDisplaySettings apply(Context context, ArtworkDisplaySettings next) {
        ArtworkDisplaySettings saved = next.withRevision(LyricUiSettings.newSettingsRevision());
        if (!saved.save(preferences(context))) throw new IllegalStateException("artwork_settings_not_saved");
        BridgeConfigRuntimeSync.sendArtwork(context, saved);
        return saved;
    }

    /** Whether the referenced provider is installed, enabled, on this protocol and still signed the same. */
    static Selection check(Context context, ArtworkDisplaySettings settings) {
        if (!settings.selected()) return new Selection(ProviderState.NONE, null);
        try {
            ArtworkProviderDirectory.Provider provider = ArtworkProviderDirectory.read(context,
                    ComponentName.unflattenFromString(settings.component()));
            return new Selection(provider.signingIdentity().equals(settings.signer())
                    ? ProviderState.READY : ProviderState.CHANGED, provider);
        } catch (Exception error) {
            return new Selection(ProviderState.MISSING, null);
        }
    }

    /** After a restore: a missing or re-signed provider keeps its reference but stays disabled. */
    static void revalidate(Context context) {
        ArtworkDisplaySettings settings = load(context);
        if (settings.enabled() && check(context, settings).state() != ProviderState.READY) {
            settings.withEnabled(false).save(preferences(context));
        }
    }
}
