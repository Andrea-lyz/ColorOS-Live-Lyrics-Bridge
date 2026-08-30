package io.github.andrealtb.lockscreenlyrics.systemui.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoMediaIdentityPolicy;

public final class SystemUiLoadLyricPolicyTest {
    @Test
    public void kuwoCarLyricIdentityRequiresPlayerPackageAndMutation() {
        assertTrue(SystemUiLoadLyricPolicy.shouldNormalizeKuWoCarLyricIdentity(
                KuWoMediaIdentityPolicy.PLAYER_PACKAGE,
                "Your Type",
                "Carly Rae Jepsen",
                "I'm sorry I'm sorry I love you",
                "Your Type-Carly Rae Jepsen"));
        assertFalse(SystemUiLoadLyricPolicy.shouldNormalizeKuWoCarLyricIdentity(
                "com.other.player",
                "Your Type",
                "Carly Rae Jepsen",
                "I'm sorry I'm sorry I love you",
                "Your Type-Carly Rae Jepsen"));
        assertFalse(SystemUiLoadLyricPolicy.shouldNormalizeKuWoCarLyricIdentity(
                KuWoMediaIdentityPolicy.PLAYER_PACKAGE,
                "Renegades",
                "ONE OK ROCK",
                "360",
                "Charli xcx"));
    }

    @Test
    public void nativePayloadIsAcceptedAndMissingPayloadClears() {
        assertEquals(
                SystemUiLoadLyricPolicy.Action.ACCEPT_PAYLOAD,
                SystemUiLoadLyricPolicy.decide(true));
        assertEquals(
                SystemUiLoadLyricPolicy.Action.CLEAR_PROVIDER,
                SystemUiLoadLyricPolicy.decide(false));
    }

    @Test
    public void metadataRewriteUsesNullSafeInequality() {
        assertTrue(SystemUiLoadLyricPolicy.shouldRewriteMetadataLyricInfo(null, "{}"));
        assertFalse(SystemUiLoadLyricPolicy.shouldRewriteMetadataLyricInfo("{}", "{}"));
    }
}
