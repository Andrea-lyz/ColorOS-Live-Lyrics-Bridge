package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class PlayerTranslationSettings {
    static final class Entry {
        final String label;
        final String providerPackage;
        final String[] playerPackages;

        Entry(String label, String providerPackage, String... playerPackages) {
            this.label = label;
            this.providerPackage = providerPackage;
            this.playerPackages = playerPackages.clone();
        }

        boolean isBuiltIn() {
            return providerPackage.isEmpty();
        }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("Salt Player", "", "com.salt.music"),
            new Entry("ConePlayer", "", "ink.trantor.coneplayer", "ink.trantor.coneplayer.gp"),
            new Entry("QQ 音乐", "io.github.proify.lyricon.qmprovider",
                    "com.tencent.qqmusic", "com.tencent.qqmusicpad"),
            new Entry("网易云音乐", "io.github.proify.lyricon.cmprovider",
                    "com.netease.cloudmusic", "com.hihonor.cloudmusic"),
            new Entry("Apple Music", "io.github.proify.lyricon.amprovider",
                    "com.apple.android.music"),
            new Entry("LX Music", "io.github.proify.lyricon.lxprovider",
                    "cn.toside.music.mobile", "com.lxwalnut.music.mobile"),
            new Entry("Poweramp", "io.github.proify.lyricon.paprovider",
                    "com.maxmpz.audioplayer"),
            new Entry("Spotify", "io.github.proify.lyricon.spotifyprovider",
                    "com.spotify.music"),
            new Entry("汽水音乐", "io.github.proify.lyricon.qishuiprovider",
                    "com.luna.music"),
            new Entry("酷狗音乐", "io.github.proify.lyricon.kgprovider",
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
