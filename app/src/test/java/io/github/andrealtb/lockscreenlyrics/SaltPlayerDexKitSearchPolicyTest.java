package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SaltPlayerDexKitSearchPolicyTest {
    @Test
    public void searchesLegacyAndMedia3ObfuscationPackages() {
        assertArrayEquals(
                new String[]{"androidx.obf", "androidx.media3"},
                SaltPlayerDexKitSearchPolicy.lyricModelPackages());
    }

    @Test
    public void returnsDefensivePackageArray() {
        String[] packages = SaltPlayerDexKitSearchPolicy.lyricModelPackages();
        packages[0] = "changed";

        assertEquals(
                "androidx.obf",
                SaltPlayerDexKitSearchPolicy.lyricModelPackages()[0]);
    }
}
