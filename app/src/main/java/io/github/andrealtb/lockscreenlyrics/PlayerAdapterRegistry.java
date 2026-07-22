package io.github.andrealtb.lockscreenlyrics;

/**
 * Registry for in-process player hooks.
 *
 * <p>Adding a directly hooked player belongs here, not in {@link LockscreenLyricsModule}. External
 * LyricProvider integrations use {@link ExternalLyricProviderRegistry} instead.</p>
 */
final class PlayerAdapterRegistry {
    private static final String[] BUILT_IN_PLAYER_PACKAGES = {
            "com.salt.music",
            "com.tencent.qqmusic",
            "com.netease.cloudmusic",
            "com.apple.android.music",
            "com.maxmpz.audioplayer",
            "ink.trantor.coneplayer",
            "ink.trantor.coneplayer.gp"
    };

    private PlayerAdapterRegistry() {
    }

    static PlayerAdapter findBuiltInPlayerAdapter(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        for (PlayerAdapter adapter : AdaptersHolder.BUILT_IN_PLAYER_ADAPTERS) {
            if (adapter.packageName().equals(packageName)) {
                return adapter;
            }
        }
        return null;
    }

    static boolean isBuiltInPlayerPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        for (String builtInPackage : BUILT_IN_PLAYER_PACKAGES) {
            if (builtInPackage.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    static PlayerAdapter[] builtInPlayerAdapters() {
        return AdaptersHolder.BUILT_IN_PLAYER_ADAPTERS.clone();
    }

    static String[] builtInPlayerPackages() {
        return BUILT_IN_PLAYER_PACKAGES.clone();
    }

    private static final class AdaptersHolder {
        static final PlayerAdapter[] BUILT_IN_PLAYER_ADAPTERS = {
                new SaltPlayerAdapter(),
                new QqMusicAdapter(),
                new NeteaseMusicAdapter(),
                new AppleMusicAdapter(),
                new PowerampLocalAdapter(),
                new ConePlayerAdapter("ink.trantor.coneplayer"),
                new ConePlayerAdapter("ink.trantor.coneplayer.gp")
        };

        private AdaptersHolder() {
        }
    }
}
