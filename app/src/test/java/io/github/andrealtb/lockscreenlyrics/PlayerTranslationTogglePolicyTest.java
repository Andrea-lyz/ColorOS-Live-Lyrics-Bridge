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
                ExternalLyricProviderRegistry.QQ_MUSIC_PLAYER_PACKAGE,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                ExternalLyricProviderRegistry.KUGOU_MUSIC_PLAYER_PACKAGE,
                Collections.emptySet()));
        assertTrue(PlayerTranslationTogglePolicy.canOverrideFavoriteActionWithTranslation(
                ExternalLyricProviderRegistry.NETEASE_MUSIC_PLAYER_PACKAGE,
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
}
