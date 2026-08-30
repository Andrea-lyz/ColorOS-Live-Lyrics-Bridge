package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

public class PlayerTranslationTogglePolicyTest {

    @Test
    public void registeredProvidersMayOverrideFavoriteActionWithTranslation() {
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.QQ_MUSIC,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.KUGOU,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.NETEASE_MUSIC,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.KUWO,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.SALT,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.CONE,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.CONE_GP,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.LX_MUSIC,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.LX_WALNUT,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                PlayerSystemUiPolicy.POWERAMP,
                Collections.emptySet()));
        assertFalse(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                "com.lxnetease.music.mobile",
                Collections.emptySet()));
    }

    @Test
    public void unknownPackagesCannotOverrideWithoutDeclaredCapability() {
        assertFalse(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                "com.example.unknown",
                Collections.emptySet()));
        assertFalse(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                "",
                Collections.emptySet()));
        assertFalse(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                null,
                Collections.emptySet()));
    }

    @Test
    public void declaredCapabilityEnablesOverrideEvenWithoutRegistryEntry() {
        HashSet<String> declared = new HashSet<>();
        declared.add("com.example.player");
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                "com.example.player",
                declared));
    }

    @Test
    public void overrideRequiresCurrentProviderAndTranslationContent() {
        String timedTranslation = "[00:01.00]翻译";
        assertFalse(PlayerTranslationTogglePolicy.shouldReplaceFavoriteActionWithTranslation(
                true, false, 3, timedTranslation));
        assertFalse(PlayerTranslationTogglePolicy.shouldReplaceFavoriteActionWithTranslation(
                false, true, 3, timedTranslation));
        assertFalse(PlayerTranslationTogglePolicy.shouldReplaceFavoriteActionWithTranslation(
                true, true, 0, "无时间戳的文本"));
        assertFalse(PlayerTranslationTogglePolicy.shouldReplaceFavoriteActionWithTranslation(
                true, true, 0, null));
    }

    @Test
    public void translationsFromModelOrPayloadEnableTheOverride() {
        assertTrue(PlayerTranslationTogglePolicy.shouldReplaceFavoriteActionWithTranslation(
                true, true, 47, null));
        assertTrue(PlayerTranslationTogglePolicy.shouldReplaceFavoriteActionWithTranslation(
                true, true, 0, "[00:01.00]翻译行"));
    }

    @Test
    public void onlyPowerampBindsOplusHeartAlongsidePublicTranslationAction() {
        assertTrue(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                PlayerSystemUiPolicy.POWERAMP));
        assertFalse(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                PlayerSystemUiPolicy.SALT));
        assertFalse(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                PlayerSystemUiPolicy.CONE));
        assertFalse(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                PlayerSystemUiPolicy.CONE_GP));
        assertFalse(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                PlayerSystemUiPolicy.LX_MUSIC));
        assertFalse(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                PlayerSystemUiPolicy.LX_WALNUT));
        assertFalse(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                PlayerSystemUiPolicy.KUWO));
        assertFalse(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                null));
        assertFalse(PlayerTranslationTogglePolicy.shouldBindOplusHeartAlongsidePublicTranslationAction(
                ""));
    }

    @Test
    public void trackedTranslationViewIsNotHiddenDuringLyricModelGap() {
        assertFalse(PlayerTranslationTogglePolicy.shouldForceHideTrackedTranslationActionView(
                true, -1));
        assertFalse(PlayerTranslationTogglePolicy.shouldForceShowTrackedTranslationActionView(
                true, -1));
        assertFalse(PlayerTranslationTogglePolicy.shouldForceHideTrackedTranslationActionView(
                false, 0));
        assertTrue(PlayerTranslationTogglePolicy.shouldForceHideTrackedTranslationActionView(
                true, 0));
        assertTrue(PlayerTranslationTogglePolicy.shouldForceShowTrackedTranslationActionView(
                true, 61));
        assertFalse(PlayerTranslationTogglePolicy.shouldForceHideTrackedTranslationActionView(
                true, 61));
        assertFalse(PlayerTranslationTogglePolicy.shouldForceShowTrackedTranslationActionView(
                false, 61));
    }

    @Test
    public void userButtonPreferenceOverridesTrackedViewVisibilityImmediately() {
        assertTrue(PlayerTranslationTogglePolicy.shouldForceHideTrackedTranslationActionView(
                true, -1, false));
        assertTrue(PlayerTranslationTogglePolicy.shouldForceHideTrackedTranslationActionView(
                true, 12, false));
        assertFalse(PlayerTranslationTogglePolicy.shouldForceShowTrackedTranslationActionView(
                true, 12, false));
        assertTrue(PlayerTranslationTogglePolicy.shouldForceShowTrackedTranslationActionView(
                true, 12, true));
    }
}
