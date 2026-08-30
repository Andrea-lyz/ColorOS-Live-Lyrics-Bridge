package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PlayerSystemUiPolicyTest {
    @Test
    public void nativePlayerPackagesReceiveOnlySystemUiCompatibility() {
        Set<String> packages = new HashSet<>(
                Arrays.asList(PlayerSystemUiPolicy.oplusHistoryPackages()));

        assertEquals(20, packages.size());
        for (String packageName : new String[]{
                PlayerSystemUiPolicy.HALCYON,
                PlayerSystemUiPolicy.FLAMINGO,
                PlayerSystemUiPolicy.QZ_MUSIC,
                PlayerSystemUiPolicy.PRISM_MUSIC
        }) {
            assertTrue(packages.contains(packageName));
            assertFalse(PlayerSystemUiPolicy
                    .supportsFavoriteTranslationOverride(packageName));
        }
    }
}
