package io.github.andrealtb.lockscreenlyrics.systemui.lyrics;

/**
 * LyricsRecyclerView ownership and prime eligibility. First attach is a valid official
 * transition: do not require alpha, translation, or non-zero size before priming.
 */
public final class LyricsRecyclerPolicy {
    private LyricsRecyclerPolicy() {
    }

    public static boolean shouldModulePosition(
            boolean aodLowFrameRateMode,
            boolean screenInteractive,
            boolean systemUiOwnsNativeLyrics) {
        return !systemUiOwnsNativeLyrics
                && (aodLowFrameRateMode || !screenInteractive);
    }

    /**
     * Inputs that must be true before {@code primeLyricsRecyclerView} continues.
     * Alpha, translationY, and measured size are intentionally absent.
     */
    public static boolean hasPrimeInputs(
            boolean recyclerPresent,
            boolean modelHasLines,
            boolean attachedToWindow,
            boolean visible) {
        return recyclerPresent
                && modelHasLines
                && attachedToWindow
                && visible;
    }

    public static boolean shouldSkipSetCurrentLyricHook(boolean threadLocalSuppressed) {
        return threadLocalSuppressed;
    }

    public static boolean shouldScheduleFollowUpPrime(boolean hasBoundChildren) {
        return !hasBoundChildren;
    }

    /**
     * Activates player-agnostic recovery only after the vendor's known official index has stayed
     * at least two rows behind the active line. A missing index or ordinary layout/centering
     * difference is not failure evidence: taking ownership there cancels the native SmoothScroller.
     */
    public static boolean shouldActivateAdaptiveLineTimedFollow(
            boolean nativePayload,
            boolean lineTimed,
            boolean recyclerTransitionSettled,
            int targetIndex,
            int officialIndex,
            long lagObservedMillis,
            long minimumLagMillis) {
        if (!nativePayload
                || !lineTimed
                || !recyclerTransitionSettled
                || targetIndex < 0
                || officialIndex < 0
                || lagObservedMillis < 0L
                || minimumLagMillis < 0L) {
            return false;
        }
        return Math.abs(targetIndex - officialIndex) >= 2
                && lagObservedMillis >= minimumLagMillis;
    }

    public static boolean shouldFollowAdaptiveLineTimedRow(
            boolean recoveryActive,
            boolean recyclerTransitionSettled,
            int targetIndex,
            int lastAlignedIndex) {
        return recoveryActive
                && recyclerTransitionSettled
                && targetIndex >= 0
                && targetIndex != lastAlignedIndex;
    }

}
