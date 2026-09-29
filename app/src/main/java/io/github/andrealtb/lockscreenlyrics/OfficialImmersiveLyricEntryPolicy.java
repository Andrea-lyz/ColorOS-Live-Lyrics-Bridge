package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * ColorOS 17 current-lyric entry of {@code LyricsRecyclerView}.
 *
 * <p>SystemUIPlugin 17.000.002 no longer calls a {@code setCurrentLyric(int)} or timed
 * {@code (boolean, long)} method. Its immersive view model resolves the row itself from the
 * playback position and calls {@code o(ImmersiveLyricsModel, boolean animate)}, which owns the
 * index, SmoothScroller, adapter notifications and row transforms. Only the model's position may
 * be replaced; the animate flag and every call stay with SystemUI.</p>
 */
final class OfficialImmersiveLyricEntryPolicy {
    private OfficialImmersiveLyricEntryPolicy() {
    }

    static boolean matches(Method method, Class<?> lyricsRecyclerClass) {
        if (method == null
                || lyricsRecyclerClass == null
                || method.getDeclaringClass() != lyricsRecyclerClass
                || method.getReturnType() != void.class
                || Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == 2
                && (parameterTypes[1] == boolean.class || parameterTypes[1] == Boolean.class)
                && ImmersiveLyricsModelAccess.isModelClass(parameterTypes[0]);
    }

    /**
     * The vendor's row lookup: the last row whose start time is at or before {@code position},
     * row 0 before the first start, the last row after the end, -1 for an empty list.
     */
    static int indexAt(long[] startTimes, long positionMillis) {
        if (startTimes == null || startTimes.length == 0) {
            return -1;
        }
        int firstAfter = -1;
        for (int i = 0; i < startTimes.length; i++) {
            if (startTimes[i] > positionMillis) {
                firstAfter = i;
                break;
            }
        }
        if (firstAfter < 0) {
            return startTimes.length - 1;
        }
        return firstAfter == 0 ? 0 : firstAfter - 1;
    }

    /**
     * Row the vendor list should show, keeping it on the module's smoothed clock without ever
     * holding the vendor back: the vendor schedules its next emission from its own clock, so a
     * later vendor row is always accepted.
     *
     * <ul>
     *   <li>The module clock crossed into the next row less than {@code bandMillis} ago: switch now,
     *       together with word fill.</li>
     *   <li>The vendor steps back one row while the module clock is still past that boundary
     *       (coarse position jitter): keep the previous row.</li>
     *   <li>Anything else (seek, restart, another session) keeps the vendor row.</li>
     * </ul>
     *
     * @param previousIndex row last applied to the same lines, or -1
     * @param moduleIntoRowMillis module position minus the start of {@code moduleIndex}
     */
    static int alignedIndex(
            int vendorIndex,
            int previousIndex,
            int moduleIndex,
            long moduleIntoRowMillis,
            long bandMillis) {
        if (vendorIndex < 0 || moduleIndex < 0 || bandMillis < 0L) {
            return vendorIndex;
        }
        if (previousIndex >= 0
                && vendorIndex == previousIndex - 1
                && moduleIndex >= previousIndex) {
            return previousIndex;
        }
        if (moduleIndex == vendorIndex + 1
                && moduleIntoRowMillis >= 0L
                && moduleIntoRowMillis <= bandMillis) {
            return moduleIndex;
        }
        return vendorIndex;
    }
}
