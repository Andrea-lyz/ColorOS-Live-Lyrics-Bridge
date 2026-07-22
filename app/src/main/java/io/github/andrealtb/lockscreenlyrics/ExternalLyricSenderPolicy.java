package io.github.andrealtb.lockscreenlyrics;

import io.github.andrealtb.lockscreenlyrics.protocol.ExternalLyricProtocol;

/**
 * SystemUI-side static whitelist for direct v4 broadcasts.
 *
 * <p>ColorOS does not provide a usable broadcast sender UID here. The policy therefore validates
 * the declared provider/module kind and its fixed source-to-player binding; it is intentionally
 * not presented as a cryptographic sender identity check.</p>
 */
final class ExternalLyricSenderPolicy {
    private ExternalLyricSenderPolicy() {
    }

    static Decision authorizeStaticWhitelist(
            ExternalLyricProtocol.Transport transport,
            ExternalLyricIngress.CaptureSnapshot snapshot) {
        if (snapshot == null) {
            return Decision.reject("missing direct lyric snapshot");
        }
        return authorizeStaticWhitelist(
                transport,
                snapshot.senderKind,
                snapshot.source,
                snapshot.playerPackage,
                snapshot.senderPackage);
    }

    /** Android-free overload used by the focused static-whitelist test. */
    static Decision authorizeStaticWhitelist(
            ExternalLyricProtocol.Transport transport,
            String senderKind,
            String source,
            String playerPackage,
            String senderPackage) {
        if (transport != ExternalLyricProtocol.Transport.DIRECT) {
            return Decision.reject("SystemUI only accepts the direct v4 transport");
        }
        if (isEmpty(playerPackage) || !playerPackage.equals(senderPackage)) {
            return Decision.reject("declared sender package must match declared player package");
        }
        if (ExternalLyricProtocol.SENDER_KIND_PROVIDER.equals(senderKind)) {
            if (!ExternalLyricProviderRegistry.isTrustedSourceBoundToHostPackage(
                    source,
                    playerPackage)) {
                return Decision.reject("source is not registered for the declared player package");
            }
            return Decision.accept();
        }
        if (ExternalLyricProtocol.SENDER_KIND_MODULE.equals(senderKind)) {
            if (!PlayerAdapterRegistry.isBuiltInPlayerPackage(playerPackage)) {
                return Decision.reject("module sender is not a supported built-in player");
            }
            return Decision.accept();
        }
        return Decision.reject("unknown direct lyric sender kind");
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    static final class Decision {
        final boolean accepted;
        final String rejection;
        private Decision(boolean accepted, String rejection) {
            this.accepted = accepted;
            this.rejection = rejection;
        }

        static Decision accept() {
            return new Decision(true, "");
        }

        static Decision reject(String rejection) {
            return new Decision(false, rejection == null ? "sender rejected" : rejection);
        }
    }
}
