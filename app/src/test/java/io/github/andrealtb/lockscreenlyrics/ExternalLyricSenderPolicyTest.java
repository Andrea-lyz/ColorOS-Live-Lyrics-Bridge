package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.andrealtb.lockscreenlyrics.protocol.ExternalLyricProtocol;

import org.junit.Test;

public final class ExternalLyricSenderPolicyTest {
    @Test
    public void staticWhitelistAcceptsRegisteredProviderBinding() {
        assertTrue(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                ExternalLyricProtocol.SENDER_KIND_PROVIDER,
                "lyricprovider/spotify-music",
                "com.spotify.music",
                "com.spotify.music").accepted);
    }

    @Test
    public void staticWhitelistRejectsMismatchedProviderClaims() {
        assertFalse(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                ExternalLyricProtocol.SENDER_KIND_PROVIDER,
                "lyricprovider/spotify-music",
                "com.tencent.qqmusic",
                "com.tencent.qqmusic").accepted);
        assertFalse(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                ExternalLyricProtocol.SENDER_KIND_PROVIDER,
                "lyricprovider/spotify-music",
                "com.spotify.music",
                "com.example.attacker").accepted);
    }

    @Test
    public void staticWhitelistKeepsBuiltInModulePlayersSeparateFromProviderSources() {
        assertTrue(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                ExternalLyricProtocol.SENDER_KIND_MODULE,
                "bridge/salt-player",
                "com.salt.music",
                "com.salt.music").accepted);
        assertFalse(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                ExternalLyricProtocol.SENDER_KIND_MODULE,
                "bridge/unknown",
                "com.example.music",
                "com.example.music").accepted);
    }
}
