package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Pure gate for future verified SystemUI hosts; AOD never runs an artwork decoder. */
public final class ArtworkPlaybackPolicy {
    public enum Surface { LOCKSCREEN_CARD, IMMERSIVE }

    private ArtworkPlaybackPolicy() {}

    public static boolean mayPlay(boolean enabled, boolean hostSupported, boolean exactSession,
            boolean interactive, boolean keyguardShowing, boolean dozing,
            boolean attached, boolean visible, boolean playing, boolean transitionComplete) {
        return enabled && hostSupported && exactSession && interactive && keyguardShowing
                && !dozing && attached && visible && playing && transitionComplete;
    }

    public static Surface select(boolean cardEligible, boolean immersiveEligible) {
        return immersiveEligible ? Surface.IMMERSIVE : cardEligible ? Surface.LOCKSCREEN_CARD : null;
    }

    /** Ambiguity in the preferred surface cannot be resolved by picking a lower-priority host. */
    public static Surface selectUnique(int cardCount, int immersiveCount) {
        if (cardCount < 0 || immersiveCount < 0 || immersiveCount > 1) return null;
        if (immersiveCount == 1) return Surface.IMMERSIVE;
        return cardCount == 1 ? Surface.LOCKSCREEN_CARD : null;
    }
}
