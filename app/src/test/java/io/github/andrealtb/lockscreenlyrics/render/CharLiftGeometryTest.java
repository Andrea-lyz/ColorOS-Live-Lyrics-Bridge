package io.github.andrealtb.lockscreenlyrics.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CharLiftGeometryTest {

    private static final float RISE = WordLyricRenderConstants.CHAR_LIFT_RISE_SPAN;
    private static final float SETTLE = WordLyricRenderConstants.CHAR_LIFT_SETTLE_SPAN;
    private static final long TAIL = WordLyricRenderConstants.CHAR_LIFT_SETTLE_TAIL_MS;

    /** "逐字歌" - three CJK glyphs, one grapheme each. */
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
        assertEquals(1f, CharLiftGeometry.liftEnvelope(0f, RISE, SETTLE), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(-RISE, RISE, SETTLE), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(-4f, RISE, SETTLE), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(SETTLE, RISE, SETTLE), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(4f, RISE, SETTLE), 1e-6f);

        for (float d = -2f; d <= 2f; d += 0.01f) {
            assertTrue(
                    "envelope exceeded the peak at d=" + d,
                    CharLiftGeometry.liftEnvelope(d, RISE, SETTLE) <= 1f + 1e-6f);
        }
    }

    @Test
    public void envelopeRisesThenSettlesMonotonically() {
        float previous = -1f;
        for (float d = -RISE; d <= 0f; d += RISE / 24f) {
            float value = CharLiftGeometry.liftEnvelope(d, RISE, SETTLE);
            assertTrue("rise side dipped at d=" + d, value >= previous - 1e-6f);
            previous = value;
        }
        previous = 2f;
        for (float d = 0f; d <= SETTLE; d += SETTLE / 24f) {
            float value = CharLiftGeometry.liftEnvelope(d, RISE, SETTLE);
            assertTrue("settle side climbed at d=" + d, value <= previous + 1e-6f);
            previous = value;
        }
    }

    @Test
    public void envelopeJoinsSmoothlyAtTheFront() {
        float step = 0.001f;
        float before = CharLiftGeometry.liftEnvelope(-step, RISE, SETTLE);
        float after = CharLiftGeometry.liftEnvelope(step, RISE, SETTLE);
        assertEquals("envelope is not continuous at the peak", before, after, 1e-4f);

        float risingSlope = (CharLiftGeometry.liftEnvelope(0f, RISE, SETTLE) - before) / step;
        float settlingSlope = (after - CharLiftGeometry.liftEnvelope(0f, RISE, SETTLE)) / step;
        assertTrue("rise side has a corner at the peak", Math.abs(risingSlope) < 0.02f);
        assertTrue("settle side has a corner at the peak", Math.abs(settlingSlope) < 0.02f);
    }

    @Test
    public void envelopeIsAsymmetricSoCharactersRiseFasterThanTheySettle() {
        float halfRise = CharLiftGeometry.liftEnvelope(-RISE / 2f, RISE, SETTLE);
        float halfSettle = CharLiftGeometry.liftEnvelope(SETTLE / 2f, RISE, SETTLE);
        assertEquals(halfRise, halfSettle, 1e-6f);
        assertTrue("the settle side must reach further than the rise side", SETTLE > RISE);
        assertTrue(CharLiftGeometry.liftEnvelope(RISE, RISE, SETTLE) > 0f);
    }

    @Test
    public void envelopeIgnoresDegenerateSpans() {
        assertEquals(0f, CharLiftGeometry.liftEnvelope(-0.2f, 0f, SETTLE), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(0.2f, RISE, 0f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftEnvelope(Float.NaN, RISE, SETTLE), 1e-6f);
    }

    @Test
    public void liftScalesWithMaxLiftAndDecay() {
        assertEquals(4f, CharLiftGeometry.liftFor(100f, 100f, 20f, 4f, 1f, false), 1e-5f);
        assertEquals(2f, CharLiftGeometry.liftFor(100f, 100f, 20f, 4f, 0.5f, false), 1e-5f);
        assertEquals(0f, CharLiftGeometry.liftFor(100f, 100f, 20f, 4f, 0f, false), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftFor(100f, 100f, 0f, 4f, 1f, false), 1e-6f);
        assertEquals(0f, CharLiftGeometry.liftFor(100f, 100f, 20f, 0f, 1f, false), 1e-6f);
    }

    @Test
    public void liftMirrorsTheEnvelopeForRightToLeftLayout() {
        float leftToRight = CharLiftGeometry.liftFor(100f, 90f, 20f, 4f, 1f, false);
        float rightToLeft = CharLiftGeometry.liftFor(100f, 110f, 20f, 4f, 1f, true);
        assertEquals(leftToRight, rightToLeft, 1e-6f);
        assertTrue("a revealed grapheme must carry some lift", leftToRight > 0f);

        float ltrAhead = CharLiftGeometry.liftFor(100f, 110f, 20f, 4f, 1f, false);
        float rtlAhead = CharLiftGeometry.liftFor(100f, 90f, 20f, 4f, 1f, true);
        assertEquals(ltrAhead, rtlAhead, 1e-6f);
        assertTrue("the unreached side must lift less than the revealed side", ltrAhead < leftToRight);
    }

    @Test
    public void liftReachesFurtherBehindTheFrontThanAhead() {
        float bump = 20f;
        float ahead = CharLiftGeometry.liftFor(100f, 100f + 0.8f * bump, bump, 4f, 1f, false);
        float behind = CharLiftGeometry.liftFor(100f, 100f - 0.8f * bump, bump, 4f, 1f, false);
        assertEquals("a grapheme beyond the rise span stays put", 0f, ahead, 1e-6f);
        assertTrue("a grapheme inside the settle span is still falling", behind > 0f);
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
        float start = CharLiftGeometry.liftZoneStart(100f, bump, 0f, 200f, false);
        float end = CharLiftGeometry.liftZoneEnd(100f, bump, 0f, 200f, false);
        assertEquals(100f - SETTLE * bump, start, 1e-4f);
        assertEquals(100f + RISE * bump, end, 1e-4f);
        assertTrue(start < end);
    }

    @Test
    public void liftZoneMirrorsForRightToLeftLayout() {
        float bump = 20f;
        assertEquals(
                100f - RISE * bump,
                CharLiftGeometry.liftZoneStart(100f, bump, 0f, 200f, true),
                1e-4f);
        assertEquals(
                100f + SETTLE * bump,
                CharLiftGeometry.liftZoneEnd(100f, bump, 0f, 200f, true),
                1e-4f);
    }

    @Test
    public void liftZoneIsEmptyWhenTheFrontIsAwayFromTheSegment() {
        float bump = 20f;
        float start = CharLiftGeometry.liftZoneStart(500f, bump, 0f, 100f, false);
        float end = CharLiftGeometry.liftZoneEnd(500f, bump, 0f, 100f, false);
        assertEquals(100f, start, 1e-6f);
        assertTrue("a far front must not open a zone", end <= start);

        float beforeStart = CharLiftGeometry.liftZoneStart(-500f, bump, 0f, 100f, false);
        float beforeEnd = CharLiftGeometry.liftZoneEnd(-500f, bump, 0f, 100f, false);
        assertTrue("a front left of the segment must not open a zone", beforeEnd <= beforeStart);
    }

    @Test
    public void liftZoneCanCoverTheWholeSegment() {
        float bump = 400f;
        assertEquals(0f, CharLiftGeometry.liftZoneStart(5f, bump, 0f, 10f, false), 1e-6f);
        assertEquals(10f, CharLiftGeometry.liftZoneEnd(5f, bump, 0f, 10f, false), 1e-6f);
    }

    @Test
    public void liftZoneCollapsesForDegenerateInput() {
        assertEquals(4f, CharLiftGeometry.liftZoneStart(100f, 0f, 4f, 40f, false), 1e-6f);
        assertEquals(4f, CharLiftGeometry.liftZoneEnd(100f, 0f, 4f, 40f, false), 1e-6f);
        assertEquals(4f, CharLiftGeometry.liftZoneStart(100f, 20f, 4f, 4f, false), 1e-6f);
        assertEquals(4f, CharLiftGeometry.liftZoneEnd(100f, 20f, 4f, 4f, false), 1e-6f);
    }

    @Test
    public void graphemeBoundariesSplitCjkPerCodePoint() {
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
