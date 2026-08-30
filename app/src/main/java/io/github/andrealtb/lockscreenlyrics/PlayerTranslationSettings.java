package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class PlayerTranslationSettings {
    static final class Entry {
        final int labelRes;
        final boolean supportsTranslation;
        final String[] playerPackages;

        Entry(
                int labelRes,
                boolean supportsTranslation,
                String... playerPackages) {
            this.labelRes = labelRes;
            this.supportsTranslation = supportsTranslation;
            this.playerPackages = playerPackages.clone();
        }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry(R.string.player_salt, true, "com.salt.music"),
            new Entry(R.string.player_cone, true,
                    "ink.trantor.coneplayer", "ink.trantor.coneplayer.gp"),
            new Entry(R.string.player_qq, true, "com.tencent.qqmusic"),
            new Entry(R.string.player_netease, true,
                    "com.netease.cloudmusic", "com.hihonor.cloudmusic"),
            new Entry(R.string.player_apple, true, "com.apple.android.music"),
            new Entry(R.string.player_lx, true,
                    "cn.toside.music.mobile",
                    "com.lxwalnut.music.mobile"),
            new Entry(R.string.player_poweramp, true, "com.maxmpz.audioplayer"),
            new Entry(R.string.player_spotify, false, "com.spotify.music"),
            new Entry(R.string.player_qishui, true, "com.luna.music"),
            new Entry(R.string.player_kugou, true,
                    "com.kugou.android", "com.kugou.android.lite"),
            new Entry(R.string.player_kuwo, true, "cn.kuwo.player"),
            new Entry(R.string.player_metrolist, false, "com.metrolist.music")
    ));

    private PlayerTranslationSettings() {
    }

    static List<Entry> entries() {
        return ENTRIES;
    }

    static boolean isSupportedPlayerPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        for (Entry entry : ENTRIES) {
            for (String candidate : entry.playerPackages) {
                if (candidate.equals(packageName)) return true;
            }
        }
        return false;
    }

    static String[] flattenPackages() {
        ArrayList<String> packages = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            Collections.addAll(packages, entry.playerPackages);
        }
        return packages.toArray(new String[0]);
    }
}
