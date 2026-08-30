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
    public void staticWhitelistAcceptsNativeQzMusicProviderBinding() {
        assertTrue(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                ExternalLyricProtocol.SENDER_KIND_PROVIDER,
                "lyricprovider/qz-music",
                "love.qz.music",
                "love.qz.music").accepted);
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

    @Test
    public void staticWhitelistRejectsUnknownSenderKind() {
        assertFalse(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                "rogue-sender",
                "lyricprovider/spotify-music",
                "com.spotify.music",
                "com.spotify.music").accepted);
    }

    @Test
    public void staticWhitelistRejectsMismatchPlayerAndSenderPackage() {
        assertFalse(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                ExternalLyricProtocol.SENDER_KIND_PROVIDER,
                "lyricprovider/spotify-music",
                "com.spotify.music",
                "").accepted);
        assertFalse(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                ExternalLyricProtocol.SENDER_KIND_PROVIDER,
                "lyricprovider/spotify-music",
                "",
                "com.spotify.music").accepted);
    }

    @Test
    public void staticWhitelistRejectsNonDirectTransport() {
        // The Android-free overload only accepts Transport.DIRECT. Any other
        // enum value (or null) must be rejected up front before the
        // sender-kind check runs.
        assertFalse(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                null,
                ExternalLyricProtocol.SENDER_KIND_PROVIDER,
                "lyricprovider/spotify-music",
                "com.spotify.music",
                "com.spotify.music").accepted);
    }

    @Test
    public void staticWhitelistRejectsNullSnapshot() {
        assertFalse(ExternalLyricSenderPolicy.authorizeStaticWhitelist(
                ExternalLyricProtocol.Transport.DIRECT,
                null).accepted);
    }
}
