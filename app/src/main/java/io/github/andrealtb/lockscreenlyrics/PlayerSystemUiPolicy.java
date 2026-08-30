package io.github.andrealtb.lockscreenlyrics;

/**
 * Package-only SystemUI compatibility policy.
 *
 * <p>This class intentionally knows only player packages. It does not identify Provider APKs,
 * accept Provider source ids, or define a lyric transport. Player-process lyric differences
 * belong in the independent Provider modules.</p>
 */
final class PlayerSystemUiPolicy {
    static final String QQ_MUSIC = "com.tencent.qqmusic";
    static final String NETEASE_MUSIC = "com.netease.cloudmusic";
    static final String NETEASE_HONOR = "com.hihonor.cloudmusic";
    static final String APPLE_MUSIC = "com.apple.android.music";
    static final String LX_MUSIC = "cn.toside.music.mobile";
    static final String LX_WALNUT = "com.lxwalnut.music.mobile";
    static final String POWERAMP = "com.maxmpz.audioplayer";
    static final String SPOTIFY = "com.spotify.music";
    static final String QISHUI = "com.luna.music";
    static final String KUGOU = "com.kugou.android";
    static final String KUGOU_LITE = "com.kugou.android.lite";
    static final String METROLIST = "com.metrolist.music";
    static final String KUWO = "cn.kuwo.player";
    static final String SALT = "com.salt.music";
    static final String CONE = "ink.trantor.coneplayer";
    static final String CONE_GP = "ink.trantor.coneplayer.gp";
    static final String HALCYON = "com.ella.music";
    static final String FLAMINGO = "yos.music.player";
    static final String QZ_MUSIC = "love.qz.music";
    static final String PRISM_MUSIC = "com.lg.sllocalmusic";

    private static final String[] OPLUS_HISTORY_PACKAGES = {
            QQ_MUSIC,
            NETEASE_MUSIC,
            NETEASE_HONOR,
            APPLE_MUSIC,
            LX_MUSIC,
            LX_WALNUT,
            POWERAMP,
            SPOTIFY,
            QISHUI,
            KUGOU,
            KUGOU_LITE,
            METROLIST,
            KUWO,
            SALT,
            CONE,
            CONE_GP,
            HALCYON,
            FLAMINGO,
            QZ_MUSIC,
            PRISM_MUSIC
    };

    private PlayerSystemUiPolicy() {
    }

    static String[] oplusHistoryPackages() {
        return OPLUS_HISTORY_PACKAGES.clone();
    }

    static boolean supportsFavoriteTranslationOverride(String packageName) {
        return QQ_MUSIC.equals(packageName)
                || NETEASE_MUSIC.equals(packageName)
                || NETEASE_HONOR.equals(packageName)
                || APPLE_MUSIC.equals(packageName)
                || QISHUI.equals(packageName)
                || KUGOU.equals(packageName)
                || KUGOU_LITE.equals(packageName)
                || KUWO.equals(packageName)
                || SALT.equals(packageName)
                || CONE.equals(packageName)
                || CONE_GP.equals(packageName)
                || LX_MUSIC.equals(packageName)
                || LX_WALNUT.equals(packageName)
                || POWERAMP.equals(packageName);
    }

    static boolean isPoweramp(String packageName) {
        return POWERAMP.equals(packageName);
    }
}
