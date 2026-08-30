package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;

public final class PlayerTranslationSettingsTest {
    @Test
    public void entriesExposeOnlyPlayerPackages() {
        for (Field field : PlayerTranslationSettings.Entry.class.getDeclaredFields()) {
            assertFalse("Provider applicationId field must not return to Bridge",
                    field.getName().toLowerCase().contains("provider"));
        }

        String[] packages = PlayerTranslationSettings.flattenPackages();
        HashSet<String> unique = new HashSet<>();
        for (String packageName : packages) {
            assertTrue(unique.add(packageName));
            assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage(packageName));
            assertFalse(packageName.startsWith("io.github.andrealtb.coloroslyrics.provider."));
            assertFalse(packageName.startsWith("io.github.proify.lyricon."));
        }
    }

    @Test
    public void supportedMatrixMatchesFourDotZeroPlayers() {
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage("com.salt.music"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage("ink.trantor.coneplayer"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage(
                "ink.trantor.coneplayer.gp"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage("com.tencent.qqmusic"));
        assertFalse(PlayerTranslationSettings.isSupportedPlayerPackage("com.tencent.qqmusicpad"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage("com.netease.cloudmusic"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage("com.hihonor.cloudmusic"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage("cn.kuwo.player"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage("cn.toside.music.mobile"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage(
                "com.lxwalnut.music.mobile"));
        assertFalse(PlayerTranslationSettings.isSupportedPlayerPackage(
                "com.lxnetease.music.mobile"));
        assertFalse(PlayerTranslationSettings.isSupportedPlayerPackage(
                "com.ikunshare.music.mobile"));
        assertFalse(PlayerTranslationSettings.isSupportedPlayerPackage("com.example.unknown"));
    }

    @Test
    public void translationSourceDrivesSupportedSwitches() {
        for (PlayerTranslationSettings.Entry entry : PlayerTranslationSettings.entries()) {
            if ("com.spotify.music".equals(entry.playerPackages[0])
                    || "com.metrolist.music".equals(entry.playerPackages[0])) {
                assertFalse(entry.supportsTranslation);
            } else {
                assertTrue(entry.supportsTranslation);
            }
        }
    }
}
