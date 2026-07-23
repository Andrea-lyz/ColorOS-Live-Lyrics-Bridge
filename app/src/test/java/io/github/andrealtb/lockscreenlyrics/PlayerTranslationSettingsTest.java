package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;

public final class PlayerTranslationSettingsTest {
    @Test
    public void builtInPlayersDoNotRequireProviderPackages() {
        assertTrue(PlayerTranslationSettings.entries().get(0).isBuiltIn());
        assertTrue(PlayerTranslationSettings.entries().get(1).isBuiltIn());
        assertEquals("com.salt.music",
                PlayerTranslationSettings.entries().get(0).playerPackages[0]);
    }

    @Test
    public void providerBackedPlayersExposeUniqueSupportedPackages() {
        String[] packages = PlayerTranslationSettings.flattenPackages();
        HashSet<String> unique = new HashSet<>();
        for (String packageName : packages) {
            assertTrue(unique.add(packageName));
            assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage(packageName));
        }
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage(
                "cn.toside.music.mobile"));
        assertTrue(PlayerTranslationSettings.isSupportedPlayerPackage(
                "com.lxwalnut.music.mobile"));
        assertFalse(PlayerTranslationSettings.isSupportedPlayerPackage("com.example.unknown"));
    }

    @Test
    public void providerBackedPlayersExposeEightUniqueProviderPackages() {
        String[] packages = PlayerTranslationSettings.providerPackages();
        HashSet<String> unique = new HashSet<>();
        assertEquals(8, packages.length);
        for (String packageName : packages) {
            assertTrue(unique.add(packageName));
        }
        assertTrue(unique.contains("io.github.proify.lyricon.cmprovider"));
        assertTrue(unique.contains("io.github.proify.lyricon.qishuiprovider"));
    }
}
