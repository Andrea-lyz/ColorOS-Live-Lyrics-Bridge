package io.github.andrealtb.lockscreenlyrics.render;

import java.text.BreakIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;

/**
 * Pure geometry for the per-character vertical lift animation described in
 * {@code docs/4.0/WORD-SYNC-CHAR-LIFT-PLAN.md}. The lift is driven by the
 * distance between a grapheme and the word-timed reveal front, so it inherits
 * every timing correction already applied to the clip reveal, the feather edge,
 * and the progress glow instead of carrying a second clock.
 *
 * <h2>Flow coordinates</h2>
 *
 * <p>Distances here are measured in <em>flow</em> coordinates: advance width
 * accumulated from the start of the line's text, across wrapped segments, using
 * the same paint the reveal width is measured with. Canvas x is unusable for
 * this. A wrapped segment that sits entirely before the active word reports a
 * reveal width equal to its full width, so a per-segment front would pin itself
 * to that segment's right edge and leave its trailing grapheme parked at the
 * envelope peak for as long as the row stayed active. Segment origins also
 * differ under centre/end alignment, so canvas x is not even comparable between
 * segments of one line.
 *
 * <p>Flow coordinates are direction-free: RTL is handled where a flow interval
 * is mapped back onto a segment's canvas x, not by mirroring the envelope.
 *
 * <p>Everything here is static, allocation-light, and free of Android types so
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
     * Asymmetric bell envelope over the signed, bump-width normalized flow
     * distance to the reveal front. Positive {@code signedDistance} means the
     * front has already passed the grapheme.
     *
     * <p>The rise side is short so a character starts moving just before it
     * lights up; the settle side is long so it drifts back down. Both sides have
     * zero slope at the peak, which keeps the envelope C¹ continuous at
     * {@code signedDistance == 0}.
     */
    public static float liftEnvelope(float signedDistance) {
        if (Float.isNaN(signedDistance)) {
            return 0f;
        }
        if (signedDistance < 0f) {
            return smootherStep(
                    1f + signedDistance / WordLyricRenderConstants.CHAR_LIFT_RISE_SPAN);
        }
        return 1f - smootherStep(
                signedDistance / WordLyricRenderConstants.CHAR_LIFT_SETTLE_SPAN);
    }

    /**
     * Lift in pixels for a grapheme centred at flow coordinate
     * {@code flowCenter} while the reveal front sits at {@code flowFront}.
     * {@code decay} is the line-level settle factor from {@link #revealDecay}.
     */
    public static float liftFor(
            float flowFront,
            float flowCenter,
            float bumpWidth,
            float maxLift,
            float decay) {
        if (bumpWidth <= 0f || maxLift <= 0f || decay <= 0f) {
            return 0f;
        }
        float envelope = liftEnvelope((flowFront - flowCenter) / bumpWidth);
        if (envelope <= 0f) {
            return 0f;
        }
        return maxLift * envelope * Math.min(1f, decay);
    }

    /**
     * Line-level settle factor. The front stops moving once the last word is
     * revealed, so without this the trailing graphemes would hang in the air;
     * it decays to 0 over {@code tailMillis} and bounds the extra invalidate
     * loop.
     */
    public static float revealDecay(long position, long lineRevealEndMillis, long tailMillis) {
        if (position <= lineRevealEndMillis) {
            return 1f;
        }
        if (tailMillis <= 0L) {
            return 0f;
        }
        long elapsed = position - lineRevealEndMillis;
        if (elapsed >= tailMillis) {
            return 0f;
        }
        return 1f - smoothStep((float) elapsed / (float) tailMillis);
    }

    /**
     * Lift clamped to the headroom between the glyph top and the top edge the
     * row may not cross. The row TextView clips to its own bounds, so a lift
     * larger than the headroom would shear the glyph instead of moving it.
     */
    public static float clampLift(float lift, float glyphTopY, float topBoundY) {
        if (lift <= 0f) {
            return 0f;
        }
        float headroom = glyphTopY - topBoundY;
        if (headroom <= 0f) {
            return 0f;
        }
        return Math.min(lift, headroom);
    }

    /**
     * Start of the flow interval where the envelope is non-zero, intersected
     * with one wrapped segment's flow range. The intersection is empty when
     * {@link #liftZoneEnd} is not greater than this value; only graphemes inside
     * it need the per-grapheme draw path, and the caller maps the interval back
     * onto that segment's canvas x.
     */
    public static float liftZoneStart(
            float flowFront,
            float bumpWidth,
            float flowSegmentStart,
            float flowSegmentEnd) {
        if (bumpWidth <= 0f || flowSegmentEnd <= flowSegmentStart) {
            return flowSegmentStart;
        }
        float start = flowFront
                - WordLyricRenderConstants.CHAR_LIFT_SETTLE_SPAN * bumpWidth;
        return clampToSegment(start, flowSegmentStart, flowSegmentEnd);
    }

    /**
     * End of the flow interval described by {@link #liftZoneStart}.
     */
    public static float liftZoneEnd(
            float flowFront,
            float bumpWidth,
            float flowSegmentStart,
            float flowSegmentEnd) {
        if (bumpWidth <= 0f || flowSegmentEnd <= flowSegmentStart) {
            return flowSegmentStart;
        }
        float end = flowFront + WordLyricRenderConstants.CHAR_LIFT_RISE_SPAN * bumpWidth;
        return clampToSegment(end, flowSegmentStart, flowSegmentEnd);
    }

    private static float clampToSegment(float value, float segmentStart, float segmentEnd) {
        return Math.max(segmentStart, Math.min(segmentEnd, value));
    }

    private static float smoothStep(float progress) {
        float value = Math.max(0f, Math.min(1f, progress));
        return value * value * (3f - 2f * value);
    }

    private static float smootherStep(float progress) {
        float value = Math.max(0f, Math.min(1f, progress));
        return value * value * value * (value * (value * 6f - 15f) + 10f);
    }
}
