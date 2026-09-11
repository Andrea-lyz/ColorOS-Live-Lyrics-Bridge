package io.github.andrealtb.lockscreenlyrics.render;

import java.text.BreakIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;

/**
 * Pure geometry for the per-character float-up animation described in
 * {@code docs/4.0/WORD-SYNC-CHAR-LIFT-PLAN.md}, modelled on Salt Player's
 * {@code LyricsLinePosition.floatUps} (see
 * {@code PlayerSource/SaltPlayer/SALT-LYRICS-FLOAT-ANIMATION-REPORT.md}).
 *
 * <h2>Step, not bump</h2>
 *
 * <p>Unsung characters rest {@code maxSink} below the normal baseline and rise
 * to it as the reveal front reaches them, staying there for the rest of the
 * row. The difference between sung and unsung text is therefore a standing
 * state rather than a passing motion, which is what keeps it readable when the
 * front moves faster than a few frames per character. An earlier bell-shaped
 * bump — rise on approach, fall after — put every character back on one
 * baseline, so a fast front left nothing to see.
 *
 * <h2>Flow coordinates</h2>
 *
 * <p>Distances are measured in <em>flow</em> coordinates: advance width
 * accumulated from the start of the line's text, across wrapped segments, using
 * the same paint the reveal width is measured with. Canvas x is unusable here.
 * A wrapped segment that sits entirely before the active word reports a reveal
 * width equal to its full width, so a per-segment front would pin itself to
 * that segment's right edge. Segment origins also differ under centre/end
 * alignment, so canvas x is not comparable between segments of one line.
 *
 * <p>Flow coordinates are direction-free: RTL is handled where a flow interval
 * is mapped back onto a segment's canvas x, not here.
 *
 * <p>Everything is static, allocation-light, and free of Android types so
 * {@code OfficialLyricTextRenderer} can call it per frame and the JVM unit
 * suite can cover it without Robolectric.
 */
public final class CharLiftGeometry {

    private CharLiftGeometry() {
    }

    private static final int[] NO_BOUNDARIES = new int[0];

    /**
     * Grapheme cluster boundaries inside {@code text[start, end)}, absolute in
     * {@code text} and always including both ends. An empty or inverted range
     * yields an empty array, never a single-element one, so callers can treat
     * {@code length < 2} as "nothing to walk".
     *
     * <p>Cluster splitting keeps surrogate pairs and combining marks attached to
     * their base character; CJK degrades to one code point per cluster. Results
     * must be cached by the caller — this runs a {@link BreakIterator} over the
     * whole segment.
     */
    public static int[] graphemeBoundaries(String text, int start, int end) {
        if (text == null) {
            return NO_BOUNDARIES;
        }
        int length = text.length();
        int from = Math.max(0, Math.min(start, length));
        int to = Math.max(from, Math.min(end, length));
        if (from >= to) {
            return NO_BOUNDARIES;
        }
        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(new StringCharacterIterator(text, from, to, from));
        int[] boundaries = new int[to - from + 1];
        int count = 0;
        boundaries[count++] = iterator.first();
        for (int next = iterator.next(); next != BreakIterator.DONE; next = iterator.next()) {
            boundaries[count++] = next;
        }
        return count == boundaries.length ? boundaries : Arrays.copyOf(boundaries, count);
    }

    /**
     * Raised-cosine float weight over the wave window, where {@code u} is the
     * grapheme's position in the window: {@code 0} at the trailing edge (fully
     * sung) and {@code 1} at the leading edge (not yet sung).
     *
     * <p>{@code 1} means risen to the normal baseline, {@code 0} means resting
     * at the sunk position. This is Salt Player's {@code wb1.m8152} curve.
     */
    public static float floatWeight(float u) {
        if (Float.isNaN(u) || u >= 1f) {
            return 0f;
        }
        if (u <= 0f) {
            return 1f;
        }
        return (1f + (float) Math.cos(u * Math.PI)) * 0.5f;
    }

    /**
     * Window coordinate of a grapheme centred at flow coordinate
     * {@code flowCenter} while the reveal front sits at {@code flowFront}.
     */
    public static float waveCoordinate(float flowFront, float flowCenter, float waveWidth) {
        if (waveWidth <= 0f) {
            return flowCenter >= flowFront ? 1f : 0f;
        }
        return (flowCenter - flowFront + waveWidth * 0.5f) / waveWidth;
    }

    /**
     * How far below the normal baseline a grapheme currently sits: {@code 0}
     * once sung, {@code maxSink} while still ahead of the wave, raised cosine in
     * between. {@code ramp} is the row's sink-in factor from {@link #sinkRamp}.
     */
    public static float sinkFor(
            float flowFront,
            float flowCenter,
            float waveWidth,
            float maxSink,
            float ramp) {
        if (maxSink <= 0f || ramp <= 0f) {
            return 0f;
        }
        float weight = floatWeight(waveCoordinate(flowFront, flowCenter, waveWidth));
        if (weight >= 1f) {
            return 0f;
        }
        return maxSink * (1f - weight) * Math.min(1f, ramp);
    }

    /**
     * Sink-in factor for a row that has just become active, so the unsung text
     * settles into its sunk position instead of appearing there. A pure
     * function of playback position: no per-row animation state to drift or to
     * reset on a seek.
     */
    public static float sinkRamp(long position, long lineBeginMillis, long sinkInMillis) {
        if (sinkInMillis <= 0L) {
            return 1f;
        }
        if (position <= lineBeginMillis) {
            return 0f;
        }
        long elapsed = position - lineBeginMillis;
        if (elapsed >= sinkInMillis) {
            return 1f;
        }
        return smoothStep((float) elapsed / (float) sinkInMillis);
    }

    /**
     * Extra distance the wave travels past the reveal front once the row's last
     * word is done, so the wave clears the end of the text instead of parking
     * on it.
     *
     * <p>The front stops at the end of the line, but the transition window
     * straddles the front: the final characters would be left halfway up
     * forever. Running the wave a half window further finishes their rise and
     * leaves the row on one baseline — which also makes a settled row render
     * exactly as it would with the effect off. Salt Player does not need this
     * because its rows scroll away; ours stay on screen.
     */
    public static float finishOvershoot(
            long position,
            long lineRevealEndMillis,
            long finishMillis,
            float waveWidth) {
        if (waveWidth <= 0f || position <= lineRevealEndMillis) {
            return 0f;
        }
        float half = waveWidth * 0.5f;
        if (finishMillis <= 0L) {
            return half;
        }
        long elapsed = position - lineRevealEndMillis;
        if (elapsed >= finishMillis) {
            return half;
        }
        return half * smoothStep((float) elapsed / (float) finishMillis);
    }

    /**
     * Sink clamped so the sunk glyph keeps {@code minClearance} between its
     * bottom and the first thing below it — the translation row's glyphs, or
     * the bottom of the row's canvas.
     */
    public static float clampSink(
            float sink,
            float glyphBottomY,
            float floorY,
            float minClearance) {
        if (sink <= 0f) {
            return 0f;
        }
        float room = floorY - minClearance - glyphBottomY;
        if (room <= 0f) {
            return 0f;
        }
        return Math.min(sink, room);
    }

    /**
     * Start of the flow interval where the wave is in transition, intersected
     * with one wrapped segment's flow range. Everything before it is sung and
     * needs no displacement; everything after {@link #waveZoneEnd} is uniformly
     * sunk. The intersection is empty when the end is not greater than the
     * start.
     */
    public static float waveZoneStart(
            float flowFront,
            float waveWidth,
            float flowSegmentStart,
            float flowSegmentEnd) {
        if (waveWidth <= 0f || flowSegmentEnd <= flowSegmentStart) {
            return flowSegmentStart;
        }
        return clampToSegment(
                flowFront - waveWidth * 0.5f, flowSegmentStart, flowSegmentEnd);
    }

    /**
     * End of the flow interval described by {@link #waveZoneStart}.
     */
    public static float waveZoneEnd(
            float flowFront,
            float waveWidth,
            float flowSegmentStart,
            float flowSegmentEnd) {
        if (waveWidth <= 0f || flowSegmentEnd <= flowSegmentStart) {
            return flowSegmentStart;
        }
        return clampToSegment(
                flowFront + waveWidth * 0.5f, flowSegmentStart, flowSegmentEnd);
    }

    private static float clampToSegment(float value, float segmentStart, float segmentEnd) {
        return Math.max(segmentStart, Math.min(segmentEnd, value));
    }

    private static float smoothStep(float progress) {
        float value = Math.max(0f, Math.min(1f, progress));
        return value * value * (3f - 2f * value);
    }
}
