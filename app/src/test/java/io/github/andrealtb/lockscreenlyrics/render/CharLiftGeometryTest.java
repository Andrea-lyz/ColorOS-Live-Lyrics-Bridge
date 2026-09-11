package io.github.andrealtb.lockscreenlyrics.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CharLiftGeometryTest {

    private static final float RISE = WordLyricRenderConstants.CHAR_LIFT_RISE_SPAN;
    private static final float SETTLE = WordLyricRenderConstants.CHAR_LIFT_SETTLE_SPAN;
    private static final long TAIL = WordLyricRenderConstants.CHAR_LIFT_SETTLE_TAIL_MS;

    // Each non-ASCII fixture is asserted for char length before it is used.
    // An editor that normalizes the decomposed sequence to its precomposed form
    // would otherwise turn the combining-mark case into a single-char case that
    // proves nothing, and the failure would point at the wrong thing.

    /** Three CJK glyphs, one grapheme each. */
    private static final String CJK = "逐字歌";

    /** Decomposed "e" + combining acute, followed by "s". */
    private static final String COMBINING = "és";

    /** "a" + U+1D11E musical G clef (a surrogate pair) + "b". */
    private static final String SURROGATE_PAIR = "a𝄞b";

    /** Woman-ZWJ-woman-ZWJ-girl family emoji followed by "!". */
    private static final String EMOJI_ZWJ_SEQUENCE =
            "👩‍👩‍👧!";

    @Test
    public void envelopePeaksAtTheFrontAndVanishesOutsideBothSpans() {
        assertEquals(1f, CharLiftGeometry.liftEnvelope(0f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(-RISE), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(-4f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(SETTLE), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(4f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(Float.NaN), 1e-6f);

        for (float d = -2f; d <= 2f; d += 0.01f) {
            assertTrue(
                    "envelope exceeded the peak at d=" + d,
                    CharLiftGeometry.liftEnvelope(d) <= 1f + 1e-6f);
        }
    }

    @Test
    public void envelopeRisesThenSettlesMonotonically() {
        float previous = -1f;
        for (float d = -RISE; d <= 0f; d += RISE / 24f) {
            float value = CharLiftGeometry.liftEnvelope(d);
            assertTrue("rise side dipped at d=" + d, value >= previous - 1e-6f);
            previous = value;
        }
        previous = 2f;
        for (float d = 0f; d <= SETTLE; d += SETTLE / 24f) {
            float value = CharLiftGeometry.liftEnvelope(d);
            assertTrue("settle side climbed at d=" + d, value <= previous + 1e-6f);
            previous = value;
        }
    }

    @Test
    public void envelopeJoinsSmoothlyAtTheFront() {
        float step = 0.001f;
        float before = CharLiftGeometry.liftEnvelope(-step);
        float after = CharLiftGeometry.liftEnvelope(step);
        assertEquals("envelope is not continuous at the peak", before, after, 1e-4f);

        float risingSlope = (CharLiftGeometry.liftEnvelope(0f) - before) / step;
        float settlingSlope = (after - CharLiftGeometry.liftEnvelope(0f)) / step;
        assertTrue("rise side has a corner at the peak", Math.abs(risingSlope) < 0.02f);
        assertTrue("settle side has a corner at the peak", Math.abs(settlingSlope) < 0.02f);
    }

    @Test
    public void envelopeIsAsymmetricSoCharactersRiseFasterThanTheySettle() {
        assertEquals(
                CharLiftGeometry.liftEnvelope(-RISE / 2f),
                CharLiftGeometry.liftEnvelope(SETTLE / 2f),
                1e-6f);
        assertTrue("the settle side must reach further than the rise side", SETTLE > RISE);
        assertTrue(CharLiftGeometry.liftEnvelope(RISE) > 0f);
    }

    @Test
    public void liftScalesWithMaxLiftAndDecay() {
        assertEquals(4f, CharLiftGeometry.liftFor(100f, 100f, 20f, 4f, 1f), 1e-5f);
        assertEquals(2f, CharLiftGeometry.liftFor(100f, 100f, 20f, 4f, 0.5f), 1e-5f);
        assertEquals(0f, CharLiftGeometry.liftFor(100f, 100f, 20f, 4f, 0f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftFor(100f, 100f, 0f, 4f, 1f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftFor(100f, 100f, 20f, 0f, 1f), 1e-6f);
    }

    @Test
    public void liftReachesFurtherBehindTheFrontThanAhead() {
        float bump = 20f;
        float ahead = CharLiftGeometry.liftFor(100f, 100f + 0.8f * bump, bump, 4f, 1f);
        float behind = CharLiftGeometry.liftFor(100f, 100f - 0.8f * bump, bump, 4f, 1f);
        assertEquals("a grapheme beyond the rise span stays put", 0f, ahead, 1e-6f);
        assertTrue("a grapheme inside the settle span is still falling", behind > 0f);
        assertTrue("the settle side must not outrank the peak", behind < 4f);
    }

    /**
     * Flow coordinates run across wrapped segments, so a segment that is already
     * fully revealed does not pin the front to its own right edge. Measuring in
     * canvas x per segment would park this grapheme at the envelope peak for the
     * whole row.
     */
    @Test
    public void trailingGraphemeOfAFullyRevealedSegmentKeepsSettling() {
        float bump = 40f;
        float maxLift = 4f;
        float firstSegmentWidth = 100f;
        float secondSegmentWidth = 80f;
        float trailingCenter = 95f;

        float frontInsideSecondSegment = firstSegmentWidth + 30f;
        float settling = CharLiftGeometry.liftFor(
                frontInsideSecondSegment, trailingCenter, bump, maxLift, 1f);
        assertEquals(
                maxLift * CharLiftGeometry.liftEnvelope(35f / bump),
                settling,
                1e-5f);
        assertTrue("the grapheme must already be falling", settling < maxLift * 0.5f);
        assertTrue("the grapheme must not have landed yet", settling > 0f);

        float frontAtLineEnd = firstSegmentWidth + secondSegmentWidth;
        assertEquals(
                "a front a whole segment away must leave no lift",
                0f,
                CharLiftGeometry.liftFor(frontAtLineEnd, trailingCenter, bump, maxLift, 1f),
                1e-6f);

        float pinnedToSegmentEdge = CharLiftGeometry.liftFor(
                firstSegmentWidth, trailingCenter, bump, maxLift, 1f);
        assertTrue(
                "a per-segment front would have parked this grapheme at the peak",
                pinnedToSegmentEdge > settling * 3f);
    }

    @Test
    public void revealDecayHoldsUntilTheLineEndsThenFadesOverTheTail() {
        assertEquals(1f, CharLiftGeometry.revealDecay(1_000L, 2_000L, TAIL), 1e-6f);
        assertEquals(1f, CharLiftGeometry.revealDecay(2_000L, 2_000L, TAIL), 1e-6f);
        assertEquals(0f, CharLiftGeometry.revealDecay(2_000L + TAIL, 2_000L, TAIL), 1e-6f);
        assertEquals(0f, CharLiftGeometry.revealDecay(9_000L, 2_000L, TAIL), 1e-6f);
        assertEquals(0f, CharLiftGeometry.revealDecay(2_001L, 2_000L, 0L), 1e-6f);
        assertEquals(0.5f, CharLiftGeometry.revealDecay(2_000L + TAIL / 2L, 2_000L, TAIL), 1e-5f);

        float previous = 2f;
        for (long offset = 0L; offset <= TAIL; offset += 10L) {
            float value = CharLiftGeometry.revealDecay(2_000L + offset, 2_000L, TAIL);
            assertTrue("decay climbed at +" + offset + "ms", value <= previous + 1e-6f);
            previous = value;
        }
    }

    @Test
    public void clampLiftUsesTheHeadroomAboveTheGlyph() {
        assertEquals(1.5f, CharLiftGeometry.clampLift(1.5f, 40f, 10f), 1e-6f);
        assertEquals(4f, CharLiftGeometry.clampLift(9f, 40f, 36f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.clampLift(9f, 40f, 40f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.clampLift(9f, 40f, 44f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.clampLift(0f, 40f, 0f), 1e-6f);
    }

    @Test
    public void liftZoneCoversTheFrontNeighbourhoodClampedToTheSegment() {
        float bump = 20f;
        float start = CharLiftGeometry.liftZoneStart(100f, bump, 0f, 200f);
        float end = CharLiftGeometry.liftZoneEnd(100f, bump, 0f, 200f);
        assertEquals(100f - SETTLE * bump, start, 1e-4f);
        assertEquals(100f + RISE * bump, end, 1e-4f);
        assertTrue(start < end);
    }

    /**
     * A bump straddling a wrap must open a zone in both segments: the tail of
     * the finished segment is still settling while the head of the next one is
     * already rising.
     */
    @Test
    public void liftZoneSpansBothSegmentsAroundAWrap() {
        float bump = 40f;
        float front = 130f;

        assertEquals(
                front - SETTLE * bump,
                CharLiftGeometry.liftZoneStart(front, bump, 0f, 100f),
                1e-4f);
        assertEquals(100f, CharLiftGeometry.liftZoneEnd(front, bump, 0f, 100f), 1e-4f);

        assertEquals(100f, CharLiftGeometry.liftZoneStart(front, bump, 100f, 180f), 1e-4f);
        assertEquals(
                front + RISE * bump,
                CharLiftGeometry.liftZoneEnd(front, bump, 100f, 180f),
                1e-4f);
    }

    @Test
    public void liftZoneIsEmptyWhenTheFrontIsAwayFromTheSegment() {
        float bump = 20f;
        float start = CharLiftGeometry.liftZoneStart(500f, bump, 0f, 100f);
        float end = CharLiftGeometry.liftZoneEnd(500f, bump, 0f, 100f);
        assertEquals(100f, start, 1e-6f);
        assertTrue("a far front must not open a zone", end <= start);

        float beforeStart = CharLiftGeometry.liftZoneStart(-500f, bump, 0f, 100f);
        float beforeEnd = CharLiftGeometry.liftZoneEnd(-500f, bump, 0f, 100f);
        assertTrue("a front before the segment must not open a zone", beforeEnd <= beforeStart);
    }

    @Test
    public void liftZoneCanCoverTheWholeSegment() {
        float bump = 400f;
        assertEquals(0f, CharLiftGeometry.liftZoneStart(5f, bump, 0f, 10f), 1e-6f);
        assertEquals(10f, CharLiftGeometry.liftZoneEnd(5f, bump, 0f, 10f), 1e-6f);
    }

    @Test
    public void liftZoneCollapsesForDegenerateInput() {
        assertEquals(4f, CharLiftGeometry.liftZoneStart(100f, 0f, 4f, 40f), 1e-6f);
        assertEquals(4f, CharLiftGeometry.liftZoneEnd(100f, 0f, 4f, 40f), 1e-6f);
        assertEquals(4f, CharLiftGeometry.liftZoneStart(100f, 20f, 4f, 4f), 1e-6f);
        assertEquals(4f, CharLiftGeometry.liftZoneEnd(100f, 20f, 4f, 4f), 1e-6f);
    }

    @Test
    public void graphemeBoundariesSplitCjkPerCodePoint() {
        assertEquals(3, CJK.length());
        assertArrayEquals(
                new int[]{0, 1, 2, 3},
                CharLiftGeometry.graphemeBoundaries(CJK, 0, CJK.length()));
    }

    @Test
    public void graphemeBoundariesSplitLatinPerCharacter() {
        assertArrayEquals(
                new int[]{0, 1, 2, 3, 4},
                CharLiftGeometry.graphemeBoundaries("word", 0, 4));
    }

    @Test
    public void graphemeBoundariesKeepCombiningMarksWithTheirBase() {
        assertEquals(3, COMBINING.length());
        assertArrayEquals(
                new int[]{0, 2, 3},
                CharLiftGeometry.graphemeBoundaries(COMBINING, 0, COMBINING.length()));
    }

    @Test
    public void graphemeBoundariesKeepSurrogatePairsWhole() {
        assertEquals(4, SURROGATE_PAIR.length());
        assertArrayEquals(
                new int[]{0, 1, 3, 4},
                CharLiftGeometry.graphemeBoundaries(SURROGATE_PAIR, 0, SURROGATE_PAIR.length()));
    }

    @Test
    public void graphemeBoundariesNeverSplitAnEmojiSurrogatePair() {
        String family = EMOJI_ZWJ_SEQUENCE;
        int[] boundaries = CharLiftGeometry.graphemeBoundaries(family, 0, family.length());
        assertTrue(boundaries.length >= 2);
        assertEquals(0, boundaries[0]);
        assertEquals(family.length(), boundaries[boundaries.length - 1]);
        for (int boundary : boundaries) {
            if (boundary > 0 && boundary < family.length()) {
                assertTrue(
                        "boundary " + boundary + " split a surrogate pair",
                        !Character.isLowSurrogate(family.charAt(boundary)));
            }
        }
    }

    @Test
    public void graphemeBoundariesStayInsideTheRequestedRange() {
        assertArrayEquals(
                new int[]{1, 2, 3},
                CharLiftGeometry.graphemeBoundaries("abcd", 1, 3));
        assertArrayEquals(
                new int[]{0, 1, 2, 3, 4},
                CharLiftGeometry.graphemeBoundaries("abcd", -5, 40));
    }

    @Test
    public void graphemeBoundariesAreEmptyForEmptyRanges() {
        assertEquals(0, CharLiftGeometry.graphemeBoundaries("abcd", 2, 2).length);
        assertEquals(0, CharLiftGeometry.graphemeBoundaries("abcd", 3, 1).length);
        assertEquals(0, CharLiftGeometry.graphemeBoundaries("", 0, 0).length);
        assertEquals(0, CharLiftGeometry.graphemeBoundaries(null, 0, 4).length);
    }
}
