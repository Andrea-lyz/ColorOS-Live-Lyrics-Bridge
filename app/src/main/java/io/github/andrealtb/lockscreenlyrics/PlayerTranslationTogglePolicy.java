package io.github.andrealtb.lockscreenlyrics;

import java.util.Set;

/**
 * Decides whether a player's favorite media-card slot may be replaced with the lyric translation
 * toggle. Pure decision logic; every SystemUI media-model operation lives in
 * {@link TranslationToggleMediaActionBinder}.
 */
final class PlayerTranslationTogglePolicy {

    private PlayerTranslationTogglePolicy() {
    }

    static boolean canOverrideFavoriteActionWithTranslation(
            String packageName,
            Set<String> providerDeclaredTranslationTogglePackages) {
        return PlayerSystemUiPolicy.supportsFavoriteTranslationOverride(packageName)
                || (providerDeclaredTranslationTogglePackages != null
                && providerDeclaredTranslationTogglePackages.contains(packageName));
    }

    static boolean shouldReplaceFavoriteActionWithTranslation(
            boolean canOverrideFavoriteAction,
            boolean isCurrentLyricProviderPackage,
            int modelTranslationCount,
            String payloadTranslationLyric) {
        if (!canOverrideFavoriteAction || !isCurrentLyricProviderPackage) {
            return false;
        }
        if (modelTranslationCount > 0) {
            return true;
        }
        return payloadTranslationLyric != null
                && LyricInfoContract.containsTimedLrc(payloadTranslationLyric);
    }

    /**
     * Poweramp has no native favorite CustomAction. After a real {@code PAUSED → PLAYING}
     * rebuild ColorOS puts OPlus heart in the visible favorite slot while public
     * {@code TOGGLE_TRANSLATION} stays as an extra Rule0 action. Bind that heart in place.
     * Salt/Cone must keep public-action-only so desktop-lyrics / favorite stay intact.
     */
    static boolean shouldBindOplusHeartAlongsidePublicTranslationAction(String packageName) {
        return PlayerSystemUiPolicy.isPoweramp(packageName);
    }

    /**
     * Force a tracked translation action view to {@code INVISIBLE} only when the current
     * model is known to have no translations. {@code translationCount < 0} is the
     * track-change gap after the previous model was cleared; hiding then can strand a
     * recycled favorite slot.
     */
    static boolean shouldForceHideTrackedTranslationActionView(
            boolean lyricSurfaceReady,
            int translationCount) {
        return shouldForceHideTrackedTranslationActionView(
                lyricSurfaceReady,
                translationCount,
                true);
    }

    static boolean shouldForceHideTrackedTranslationActionView(
            boolean lyricSurfaceReady,
            int translationCount,
            boolean userWantsButton) {
        return lyricSurfaceReady && (!userWantsButton || translationCount == 0);
    }

    /**
     * Force {@code VISIBLE} only when a model with translations is already cached.
     */
    static boolean shouldForceShowTrackedTranslationActionView(
            boolean lyricSurfaceReady,
            int translationCount) {
        return shouldForceShowTrackedTranslationActionView(
                lyricSurfaceReady,
                translationCount,
                true);
    }

    static boolean shouldForceShowTrackedTranslationActionView(
            boolean lyricSurfaceReady,
            int translationCount,
            boolean userWantsButton) {
        return lyricSurfaceReady && userWantsButton && translationCount > 0;
    }
}
