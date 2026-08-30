package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KuWoMediaIdentityPolicyTest {
    @Test
    public void exactIdentityRemainsCarLyricMutation() {
        assertTrue(KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                "Light Ripple",
                "HOYO-MiX",
                " light ripple ",
                "hoyo-mix"));
    }

    @Test
    public void mergedTitleAndArtistRemainsCarLyricMutation() {
        assertTrue(KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                "Light Ripple",
                "HOYO-MiX",
                "Light Ripple | HOYO-MiX",
                null));
    }

    @Test
    public void lyricLineTitleWithMergedArtistRemainsCarLyricMutation() {
        assertTrue(KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                "Your Type",
                "Carly Rae Jepsen",
                "I'm sorry I'm sorry I love you",
                "Your Type-Carly Rae Jepsen"));
    }

    @Test
    public void longerMultiArtistSpellingIsRealTrackChange() {
        assertFalse(KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                "轻涟 La vaguelette",
                "HOYO-MiX",
                "轻涟 La vaguelette",
                "胡夏&HOYO-MiX"));
    }

    @Test
    public void playerPackageIsKuWoOnly() {
        assertTrue(KuWoMediaIdentityPolicy.isPlayerPackage("cn.kuwo.player"));
        assertFalse(KuWoMediaIdentityPolicy.isPlayerPackage("com.salt.music"));
        assertFalse(KuWoMediaIdentityPolicy.isPlayerPackage(null));
    }

    @Test
    public void differentTrackIsRealTrackChange() {
        assertFalse(KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                "Renegades",
                "ONE OK ROCK",
                "360",
                "Charli xcx"));
    }
}
