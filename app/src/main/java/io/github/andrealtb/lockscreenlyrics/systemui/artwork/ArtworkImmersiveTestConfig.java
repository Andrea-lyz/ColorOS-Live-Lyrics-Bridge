package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.ComponentName;
import android.content.Intent;
import io.github.andrealtb.lockscreenlyrics.BuildConfig;

/** Ephemeral, debug-only test settings. Transport must require the Bridge signature permission. */
public record ArtworkImmersiveTestConfig(boolean enabled, ComponentName component, String signer, long revision,
        boolean localFixture, boolean keepAwake) {
    public ArtworkImmersiveTestConfig(boolean enabled, ComponentName component, String signer, long revision) {
        this(enabled, component, signer, revision, true);
    }
    public ArtworkImmersiveTestConfig(boolean enabled, ComponentName component, String signer, long revision, boolean localFixture) {
        this(enabled, component, signer, revision, localFixture, false);
    }
    public static final String ACTION = "io.github.andrealtb.lockscreenlyrics.action.ARTWORK_IMMERSIVE_TEST";
    /** Changes only the keep-awake choice, without restarting the running test. */
    public static final String ACTION_KEEP_AWAKE = "io.github.andrealtb.lockscreenlyrics.action.ARTWORK_KEEP_AWAKE";
    public static ArtworkImmersiveTestConfig read(Intent intent) {
        boolean enabled = BuildConfig.DEBUG && intent.getBooleanExtra("enabled", false);
        String component = intent.getStringExtra("component");
        String signer = intent.getStringExtra("signer");
        long revision = intent.getLongExtra("revision", 0);
        if (!enabled) return new ArtworkImmersiveTestConfig(false, null, "", revision);
        if (component == null || component.length() > 512 || signer == null || signer.isEmpty()
                || signer.length() > 1024 || revision <= 0) throw new IllegalArgumentException("test_config_invalid");
        ComponentName name = ComponentName.unflattenFromString(component);
        if (name == null) throw new IllegalArgumentException("test_component_invalid");
        return new ArtworkImmersiveTestConfig(true, name, signer, revision, intent.getBooleanExtra("localFixture", true),
                readKeepAwake(intent));
    }
    public static boolean readKeepAwake(Intent intent) {
        return BuildConfig.DEBUG && intent.getBooleanExtra("keepAwake", false);
    }
}
