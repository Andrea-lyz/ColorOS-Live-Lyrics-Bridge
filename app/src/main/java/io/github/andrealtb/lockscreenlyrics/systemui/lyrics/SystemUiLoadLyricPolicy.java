package io.github.andrealtb.lockscreenlyrics.systemui.lyrics;

import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoMediaIdentityPolicy;

/**
 * Official {@code loadLyricInBg} decisions. The hook still reads MediaMetadata, applies
 * cleanup, and writes the chosen args back. Do not add recycler alpha/size readiness
 * gates here.
 */
public final class SystemUiLoadLyricPolicy {
    public enum Action {
        ACCEPT_PAYLOAD,
        CLEAR_PROVIDER
    }

    private SystemUiLoadLyricPolicy() {
    }

    public static boolean shouldNormalizeKuWoCarLyricIdentity(
            String packageName,
            String lastTitle,
            String lastArtist,
            String incomingTitle,
            String incomingArtist) {
        return KuWoMediaIdentityPolicy.isPlayerPackage(packageName)
                && KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                lastTitle,
                lastArtist,
                incomingTitle,
                incomingArtist);
    }

    public static Action decide(boolean hasPayload) {
        return hasPayload ? Action.ACCEPT_PAYLOAD : Action.CLEAR_PROVIDER;
    }

    public static boolean shouldRewriteMetadataLyricInfo(
            String originalLyricInfo,
            String normalizedLyricInfo) {
        return !textEquals(originalLyricInfo, normalizedLyricInfo);
    }
    private static boolean textEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
