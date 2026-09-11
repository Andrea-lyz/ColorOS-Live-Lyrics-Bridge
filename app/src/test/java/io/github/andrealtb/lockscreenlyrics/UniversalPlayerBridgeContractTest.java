package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class UniversalPlayerBridgeContractTest {
    @After
    public void resetRuntimeBindings() {
        UniversalPlayerBridgeContract.applyRuntimeBindings(false, Collections.emptyList());
    }

    @Test
    public void identityAndActionAvoidProviderApplicationIds() {
        assertEquals(
                "io.github.andrealtb.universallyrics.action.PLAYER_BINDINGS_CHANGED",
                UniversalPlayerBridgeContract.ACTION_PLAYER_BINDINGS_CHANGED);
        assertTrue(UniversalPlayerBridgeContract.isAcceptedIdentity(
                UniversalPlayerBridgeContract.PROVIDER_IDENTITY));
        assertFalse(UniversalPlayerBridgeContract.isAcceptedIdentity("other"));
        assertFalse(UniversalPlayerBridgeContract.ACTION_PLAYER_BINDINGS_CHANGED.contains(
                "coloroslyrics.provider"));
        assertFalse(UniversalPlayerBridgeContract.PROVIDER_IDENTITY.contains(
                "coloroslyrics.provider"));
    }

    @Test
    public void sanitizeDropsOfficialAndSpecialHosts() {
        List<String> sanitized = UniversalPlayerBridgeContract.sanitizeBoundPackages(Arrays.asList(
                " com.salt.music ",
                "com.tencent.qqmusic",
                "com.netease.cloudmusic",
                "com.hihonor.cloudmusic",
                "com.kugou.android",
                "com.kugou.android.lite",
                "cn.kuwo.player",
                "com.heytap.music",
                UniversalPlayerBridgeContract.TRANSLATION_GROUP_PACKAGE,
                "",
                null,
                "remix.myplayer"));
        assertEquals(Arrays.asList("com.salt.music", "remix.myplayer"), sanitized);
    }

    @Test
    public void runtimeExtrasJoinHistoryWithoutMutatingStaticWhitelist() {
        assertEquals(22, PlayerSystemUiPolicy.oplusHistoryPackages().length);
        assertFalse(PlayerSystemUiPolicy.isHistoryPackage("remix.myplayer"));
        UniversalPlayerBridgeContract.applyRuntimeBindings(
                true,
                Collections.singletonList("remix.myplayer"));
        assertTrue(UniversalPlayerBridgeContract.isPresent());
        assertTrue(PlayerSystemUiPolicy.isHistoryPackage("remix.myplayer"));
        assertTrue(PlayerSystemUiPolicy.isHistoryPackage(PlayerSystemUiPolicy.SALT));
        assertFalse(PlayerSystemUiPolicy.isHistoryPackage("com.tencent.qqmusicpad"));
        assertEquals(22, PlayerSystemUiPolicy.oplusHistoryPackages().length);
    }
}
