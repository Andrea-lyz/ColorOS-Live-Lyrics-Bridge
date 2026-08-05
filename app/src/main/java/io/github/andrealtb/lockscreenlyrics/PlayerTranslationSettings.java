package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class PlayerTranslationSettings {
    static final class Entry {
        final int labelRes;
        final String providerPackage;
        final String[] playerPackages;

        Entry(int labelRes, String providerPackage, String... playerPackages) {
            this.labelRes = labelRes;
            this.providerPackage = providerPackage;
            this.playerPackages = playerPackages.clone();
        }

        boolean isBuiltIn() {
            return providerPackage.isEmpty();
        }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry(R.string.player_salt, "", "com.salt.music"),
            new Entry(R.string.player_cone, "", "ink.trantor.coneplayer", "ink.trantor.coneplayer.gp"),
            new Entry(R.string.player_qq, "io.github.proify.lyricon.qmprovider",
                    "com.tencent.qqmusic", "com.tencent.qqmusicpad"),
            new Entry(R.string.player_netease, "io.github.proify.lyricon.cmprovider",
                    "com.netease.cloudmusic", "com.hihonor.cloudmusic"),
            new Entry(R.string.player_apple, "io.github.proify.lyricon.amprovider",
                    "com.apple.android.music"),
            new Entry(R.string.player_lx, "io.github.proify.lyricon.lxprovider",
                    "cn.toside.music.mobile", "com.lxwalnut.music.mobile"),
            new Entry(R.string.player_poweramp, "io.github.proify.lyricon.paprovider",
                    "com.maxmpz.audioplayer"),
            new Entry(R.string.player_spotify, "io.github.proify.lyricon.spotifyprovider",
                    "com.spotify.music"),
            new Entry(R.string.player_qishui, "io.github.proify.lyricon.qishuiprovider",
                    "com.luna.music"),
            new Entry(R.string.player_kugou, "io.github.proify.lyricon.kgprovider",
                    "com.kugou.android", "com.kugou.android.lite")
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

    static String[] providerPackages() {
        ArrayList<String> packages = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (!entry.isBuiltIn()) packages.add(entry.providerPackage);
        }
        return packages.toArray(new String[0]);
    }
}
