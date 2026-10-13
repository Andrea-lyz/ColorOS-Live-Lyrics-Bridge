package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.ComponentName;
import android.content.Intent;
import io.github.andrealtb.lockscreenlyrics.BuildConfig;

/**
 * What the SystemUI artwork runtime runs: the saved display settings, or an ephemeral debug
 * fixture test that overrides them until it is switched off. Transport of either always requires
 * the Bridge signature permission; SystemUI still verifies the selected provider before each bind.
 */
public record ArtworkPlaybackConfig(boolean enabled, ComponentName component, String signer, long revision,
        boolean localFixture, boolean keepAwake, boolean cardEnabled, boolean immersiveEnabled, int frameRateLimit) {
    public static final ArtworkPlaybackConfig DISABLED = new ArtworkPlaybackConfig(false, null, "", 0);
    /** Debug-only fixture test; ignored by release builds. */
    public static final String ACTION_TEST = "io.github.andrealtb.lockscreenlyrics.action.ARTWORK_IMMERSIVE_TEST";

    public ArtworkPlaybackConfig(boolean enabled, ComponentName component, String signer, long revision) {
        this(enabled, component, signer, revision, true);
    }
    public ArtworkPlaybackConfig(boolean enabled, ComponentName component, String signer, long revision, boolean localFixture) {
        this(enabled, component, signer, revision, localFixture, false, true, true);
    }
    public ArtworkPlaybackConfig(boolean enabled, ComponentName component, String signer, long revision,
            boolean localFixture, boolean keepAwake, boolean cardEnabled, boolean immersiveEnabled) {
        this(enabled, component, signer, revision, localFixture, keepAwake, cardEnabled, immersiveEnabled,
                ArtworkDisplaySettings.FRAME_RATE_UNLIMITED);
    }

    /** Capped playback decodes into a pacing reader; uncapped playback writes the view directly. */
    public boolean frameRateLimited() {
        return frameRateLimit < ArtworkDisplaySettings.FRAME_RATE_UNLIMITED;
    }

    /**
     * Same provider and mode: presentation toggles and a new cap within the capped range change without
     * restarting the decoder; switching between capped and uncapped rebuilds its output path.
     */
    public boolean sameSession(ArtworkPlaybackConfig other) {
        return other != null && enabled && other.enabled && localFixture == other.localFixture
                && frameRateLimited() == other.frameRateLimited()
                && java.util.Objects.equals(component, other.component) && signer.equals(other.signer);
    }

    public static ArtworkPlaybackConfig readTest(Intent intent) {
        boolean enabled = BuildConfig.DEBUG && intent.getBooleanExtra("enabled", false);
        String component = intent.getStringExtra("component");
        String signer = intent.getStringExtra("signer");
        long revision = intent.getLongExtra("revision", 0);
        if (!enabled) return new ArtworkPlaybackConfig(false, null, "", revision);
        if (component == null || component.length() > 512 || signer == null || signer.isEmpty()
                || signer.length() > 1024 || revision <= 0) throw new IllegalArgumentException("test_config_invalid");
        ComponentName name = ComponentName.unflattenFromString(component);
        if (name == null) throw new IllegalArgumentException("test_component_invalid");
        return new ArtworkPlaybackConfig(true, name, signer, revision, intent.getBooleanExtra("localFixture", true));
    }
}
