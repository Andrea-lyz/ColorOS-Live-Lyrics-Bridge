package io.github.andrealtb.lockscreenlyrics.bootstrap;

import android.content.Context;

import io.github.andrealtb.lockscreenlyrics.SystemUiDexKitAdapter;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;

/** Keeps the SystemUI process bootstrap separate from player-process hook installation. */
public final class SystemUiRuntimeBootstrap {
    public interface Host {
        void installAodMediaSupportHooks(ClassLoader classLoader);

        SystemUiDexKitAdapter.Targets resolveSystemUiTargets(ClassLoader classLoader);

        void installOplusMediaPolicyBypassHooks(SystemUiDexKitAdapter.Targets targets);

        void installSystemUiWordLyricHooks(
                ClassLoader classLoader,
                SystemUiDexKitAdapter.Targets targets);

        void installSystemUiTranslationToggleActionHook(SystemUiDexKitAdapter.Targets targets);

        Context currentApplicationContext();

        void ensureLyricUiSettingsReceiver(Context context);

        void loadLyricUiStyleSettings(Context context, boolean force);

        void post(Runnable runnable);

        void postDelayed(Runnable runnable, long delayMillis);

        void infoAlways(BridgeDebugArea area, String event, String message);
    }

    private final Host host;
    private final String renderPipelineRevision;
    private final boolean drawFrameReuseEnabled;
    private final boolean slotAliasReuseEnabled;

    public SystemUiRuntimeBootstrap(
            Host host,
            String renderPipelineRevision,
            boolean drawFrameReuseEnabled,
            boolean slotAliasReuseEnabled) {
        this.host = host;
        this.renderPipelineRevision = renderPipelineRevision;
        this.drawFrameReuseEnabled = drawFrameReuseEnabled;
        this.slotAliasReuseEnabled = slotAliasReuseEnabled;
    }

    public void initialize(ClassLoader classLoader) {
        host.installAodMediaSupportHooks(classLoader);
        SystemUiDexKitAdapter.Targets targets = host.resolveSystemUiTargets(classLoader);
        if (targets == null) {
            return;
        }
        host.installOplusMediaPolicyBypassHooks(targets);
        host.installSystemUiWordLyricHooks(classLoader, targets);
        host.installSystemUiTranslationToggleActionHook(targets);
        host.infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.SYSTEMUI_BOOTSTRAP, "Official lyric render pipeline"
                + " | revision=" + renderPipelineRevision
                + ", drawFrameReuse=" + drawFrameReuseEnabled
                + ", slotAliasReuse=" + slotAliasReuseEnabled);
        Runnable registerReceivers = () -> {
            Context context = host.currentApplicationContext();
            host.ensureLyricUiSettingsReceiver(context);
            host.loadLyricUiStyleSettings(context, true);
        };
        host.post(registerReceivers);
        host.postDelayed(registerReceivers, 1_500L);
    }
}
