package io.github.andrealtb.lockscreenlyrics;

/** Keeps player-process hook selection out of the SystemUI runtime bootstrap. */
final class PlayerRuntimeBootstrap {
    interface Host {
        PlayerAdapter findPlayerAdapter(String packageName);

        void bindHookedPlayerAdapter(PlayerAdapter adapter);

        void installMediaMetadataHook();

        void installPlayerLyricSourceHooks(PlayerAdapter adapter, ClassLoader classLoader);
    }

    private final Host host;

    PlayerRuntimeBootstrap(Host host) {
        this.host = host;
    }

    void initialize(String packageName, ClassLoader classLoader) {
        PlayerAdapter adapter = host.findPlayerAdapter(packageName);
        if (adapter == null) {
            return;
        }
        host.bindHookedPlayerAdapter(adapter);
        host.installMediaMetadataHook();
        host.installPlayerLyricSourceHooks(adapter, classLoader);
    }
}
