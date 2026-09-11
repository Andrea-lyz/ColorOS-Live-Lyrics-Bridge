package io.github.andrealtb.lockscreenlyrics.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CharLiftGeometryTest {

    private static final float WAVE = WordLyricRenderConstants.CHAR_LIFT_WAVE_WIDTH_FACTOR;
    private static final long SINK_IN = WordLyricRenderConstants.CHAR_LIFT_SINK_IN_MS;

    // Each non-ASCII fixture is asserted for char length before it is used.
    // An editor that normalizes the decomposed sequence to its precomposed form
    // would otherwise turn the combining-mark case into a single-char case that
    // proves nothing, and the failure would point at the wrong thing.

    /** Three CJK glyphs, one grapheme each. */
    private static final String CJK = "逐字歌";

    /** Decomposed "e" + combining acute, followed by "s". */
    private static final String COMBINING = "e" + (char) 0x0301 + "s";

    /** "a" + U+1D11E musical G clef (a surrogate pair) + "b". */
    private static final String SURROGATE_PAIR =
            "a" + new String(Character.toChars(0x1D11E)) + "b";

    /** Woman-ZWJ-woman-ZWJ-girl family emoji followed by "!". */
    private static final String EMOJI_ZWJ_SEQUENCE =
            new String(Character.toChars(0x1F469))
                    + (char) 0x200D
                    + new String(Character.toChars(0x1F469))
                    + (char) 0x200D
                    + new String(Character.toChars(0x1F467))
                    + "!";

    @Test
    public void floatWeightMatchesSaltPlayersRaisedCosine() {
        for (float u = 0.01f; u < 1f; u += 0.01f) {
            float expected = (1f + (float) Math.cos(u * Math.PI)) * 0.5f;
            assertEquals(
                    "raised cosine diverged at u=" + u,
                    expected,
                    CharLiftGeometry.floatWeight(u),
                    1e-6f);
        }
    }

    @Test
    public void floatWeightIsOneBehindTheWaveAndZeroAheadOfIt() {
        assertEquals(1f, CharLiftGeometry.floatWeight(0f), 1e-6f);
        assertEquals(1f, CharLiftGeometry.floatWeight(-0.5f), 1e-6f);
        assertEquals(1f, CharLiftGeometry.floatWeight(-40f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.floatWeight(1f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.floatWeight(1.5f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.floatWeight(40f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.floatWeight(Float.NaN), 1e-6f);
        assertEquals(0.5f, CharLiftGeometry.floatWeight(0.5f), 1e-6f);
    }

    @Test
    public void floatWeightFallsMonotonicallyAcrossTheWindow() {
        float previous = 2f;
        for (float u = 0f; u <= 1f; u += 1f / 64f) {
            float value = CharLiftGeometry.floatWeight(u);
            assertTrue("float weight climbed at u=" + u, value <= previous + 1e-6f);
            previous = value;
        }
    }

    @Test
    public void waveCoordinatePutsTheFrontAtTheMiddleOfTheWindow() {
        assertEquals(0.5f, CharLiftGeometry.waveCoordinate(100f, 100f, 60f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.waveCoordinate(100f, 70f, 60f), 1e-6f);
        assertEquals(1f, CharLiftGeometry.waveCoordinate(100f, 130f, 60f), 1e-6f);
    }

    @Test
    public void sungCharactersRestAtTheNormalBaselineAndUnsungOnesSitLow() {
        float wave = 60f;
        float maxSink = 6f;

        assertEquals(
                "a sung character must be exactly on the baseline",
                0f,
                CharLiftGeometry.sinkFor(100f, 40f, wave, maxSink, 1f),
                1e-6f);
        assertEquals(
                "an unsung character must rest a full sink below it",
                maxSink,
                CharLiftGeometry.sinkFor(100f, 200f, wave, maxSink, 1f),
                1e-6f);
        assertEquals(
                "the character at the front is halfway up",
                maxSink * 0.5f,
                CharLiftGeometry.sinkFor(100f, 100f, wave, maxSink, 1f),
                1e-5f);
    }

    @Test
    public void sinkFallsMonotonicallyAsTheFrontApproaches() {
        float wave = 60f;
        float maxSink = 6f;
        float previous = Float.MAX_VALUE;
        for (float front = 60f; front <= 140f; front += 2f) {
            float sink = CharLiftGeometry.sinkFor(front, 100f, wave, maxSink, 1f);
            assertTrue("sink grew as the front advanced at front=" + front, sink <= previous + 1e-6f);
            previous = sink;
        }
        assertEquals("the grapheme ends up on the baseline", 0f, previous, 1e-6f);
    }

    @Test
    public void sinkScalesWithTheRampAndIgnoresDegenerateInput() {
        float wave = 60f;
        assertEquals(3f, CharLiftGeometry.sinkFor(100f, 200f, wave, 6f, 0.5f), 1e-5f);
        assertEquals(0f, CharLiftGeometry.sinkFor(100f, 200f, wave, 6f, 0f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.sinkFor(100f, 200f, wave, 0f, 1f), 1e-6f);
        assertEquals(6f, CharLiftGeometry.sinkFor(100f, 200f, wave, 6f, 4f), 1e-5f);
    }

    /**
     * The property the bell-shaped model could not provide: once the front has
     * moved past a wrapped segment, every grapheme in it stands at the normal
     * baseline and stays there, so the sung/unsung difference is visible no
     * matter how fast the front is moving.
     */
    @Test
    public void aFullySungSegmentStandsStillAtTheBaseline() {
        float wave = 60f;
        float maxSink = 6f;
        float firstSegmentWidth = 100f;
        float secondSegmentWidth = 80f;

        float front = firstSegmentWidth + 30f;
        for (float center = 5f; center < firstSegmentWidth; center += 5f) {
            assertEquals(
                    "a sung grapheme drifted off the baseline at " + center,
                    0f,
                    CharLiftGeometry.sinkFor(front, center, wave, maxSink, 1f),
                    1e-6f);
        }

        float frontAtLineEnd = firstSegmentWidth + secondSegmentWidth;
        assertEquals(
                0f,
                CharLiftGeometry.sinkFor(frontAtLineEnd, 95f, wave, maxSink, 1f),
                1e-6f);
        assertTrue(
                "the last characters are still mid-rise while the front parks on them",
                CharLiftGeometry.sinkFor(frontAtLineEnd, frontAtLineEnd - 1f, wave, maxSink, 1f)
                        > 0f);
        assertEquals(
                "and the finish overshoot puts the whole row on one baseline",
                0f,
                CharLiftGeometry.sinkFor(
                        frontAtLineEnd + wave * 0.5f,
                        frontAtLineEnd - 1f,
                        wave,
                        maxSink,
                        1f),
                1e-6f);
    }

    @Test
    public void theFinishOvershootClearsTheEndOfTheText() {
        float wave = 60f;
        long revealEnd = 5_000L;
        long finish = WordLyricRenderConstants.CHAR_LIFT_FINISH_MS;

        assertEquals(
                "nothing may move while the row is still being sung",
                0f,
                CharLiftGeometry.finishOvershoot(4_000L, revealEnd, finish, wave),
                1e-6f);
        assertEquals(0f, CharLiftGeometry.finishOvershoot(revealEnd, revealEnd, finish, wave), 1e-6f);
        assertEquals(
                "the wave ends up a half window past the text",
                wave * 0.5f,
                CharLiftGeometry.finishOvershoot(revealEnd + finish, revealEnd, finish, wave),
                1e-5f);
        assertEquals(
                wave * 0.5f,
                CharLiftGeometry.finishOvershoot(revealEnd + 60_000L, revealEnd, finish, wave),
                1e-5f);
        assertEquals(0f, CharLiftGeometry.finishOvershoot(revealEnd + 10L, revealEnd, finish, 0f), 1e-6f);

        float previous = -1f;
        for (long offset = 0L; offset <= finish; offset += 10L) {
            float value = CharLiftGeometry.finishOvershoot(
                    revealEnd + offset, revealEnd, finish, wave);
            assertTrue("the overshoot went backwards at +" + offset + "ms", value >= previous - 1e-6f);
            previous = value;
        }
    }

    @Test
    public void sinkRampRunsFromTheRowStartAndThenHoldsAtFull() {
        assertEquals(0f, CharLiftGeometry.sinkRamp(1_000L, 1_000L, SINK_IN), 1e-6f);
        assertEquals(0f, CharLiftGeometry.sinkRamp(900L, 1_000L, SINK_IN), 1e-6f);
        assertEquals(1f, CharLiftGeometry.sinkRamp(1_000L + SINK_IN, 1_000L, SINK_IN), 1e-6f);
        assertEquals(1f, CharLiftGeometry.sinkRamp(90_000L, 1_000L, SINK_IN), 1e-6f);
        assertEquals(0.5f, CharLiftGeometry.sinkRamp(1_000L + SINK_IN / 2L, 1_000L, SINK_IN), 1e-5f);
        assertEquals(1f, CharLiftGeometry.sinkRamp(1_001L, 1_000L, 0L), 1e-6f);

        float previous = -1f;
        for (long offset = 0L; offset <= SINK_IN; offset += 10L) {
            float value = CharLiftGeometry.sinkRamp(1_000L + offset, 1_000L, SINK_IN);
            assertTrue("ramp fell at +" + offset + "ms", value >= previous - 1e-6f);
            previous = value;
        }
    }

    @Test
    public void clampSinkKeepsClearanceBelowTheGlyph() {
        assertEquals(6f, CharLiftGeometry.clampSink(6f, 100f, 200f, 1f), 1e-6f);
        assertEquals(4f, CharLiftGeometry.clampSink(9f, 100f, 105f, 1f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.clampSink(9f, 100f, 101f, 1f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.clampSink(9f, 100f, 90f, 1f), 1e-6f);
        assertEquals(0f, CharLiftGeometry.clampSink(0f, 100f, 200f, 1f), 1e-6f);
    }

    @Test
    public void waveZoneCoversTheTransitionClampedToTheSegment() {
        float wave = 60f;
        float start = CharLiftGeometry.waveZoneStart(100f, wave, 0f, 200f);
        float end = CharLiftGeometry.waveZoneEnd(100f, wave, 0f, 200f);
        assertEquals(70f, start, 1e-4f);
        assertEquals(130f, end, 1e-4f);
    }

    @Test
    public void waveZoneSpansBothSegmentsAroundAWrap() {
        float wave = 60f;
        float front = 110f;

        assertEquals(80f, CharLiftGeometry.waveZoneStart(front, wave, 0f, 100f), 1e-4f);
        assertEquals(100f, CharLiftGeometry.waveZoneEnd(front, wave, 0f, 100f), 1e-4f);

        assertEquals(100f, CharLiftGeometry.waveZoneStart(front, wave, 100f, 180f), 1e-4f);
        assertEquals(140f, CharLiftGeometry.waveZoneEnd(front, wave, 100f, 180f), 1e-4f);
    }

    @Test
    public void waveZoneIsEmptyWhereTheSegmentIsEntirelySungOrEntirelyUnsung() {
        float wave = 60f;

        float sungStart = CharLiftGeometry.waveZoneStart(500f, wave, 0f, 100f);
        float sungEnd = CharLiftGeometry.waveZoneEnd(500f, wave, 0f, 100f);
        assertTrue("a fully sung segment needs no transition", sungEnd <= sungStart);

        float unsungStart = CharLiftGeometry.waveZoneStart(-500f, wave, 0f, 100f);
        float unsungEnd = CharLiftGeometry.waveZoneEnd(-500f, wave, 0f, 100f);
        assertTrue("a fully unsung segment needs no transition", unsungEnd <= unsungStart);
    }

    @Test
    public void waveZoneCollapsesForDegenerateInput() {
        assertEquals(4f, CharLiftGeometry.waveZoneStart(100f, 0f, 4f, 40f), 1e-6f);
        assertEquals(4f, CharLiftGeometry.waveZoneEnd(100f, 0f, 4f, 40f), 1e-6f);
        assertEquals(4f, CharLiftGeometry.waveZoneStart(100f, 60f, 4f, 4f), 1e-6f);
        assertEquals(4f, CharLiftGeometry.waveZoneEnd(100f, 60f, 4f, 4f), 1e-6f);
    }

    @Test
    public void theWaveWindowIsThreeTextSizesWide() {
        assertEquals(3.0f, WAVE, 1e-6f);
        float textSize = 32f;
        float wave = textSize * WAVE;
        assertEquals(
                "a grapheme one and a half text sizes ahead is still fully down",
                0f,
                CharLiftGeometry.floatWeight(
                        CharLiftGeometry.waveCoordinate(0f, textSize * 1.5f, wave)),
                1e-6f);
        assertEquals(
                "a grapheme one and a half text sizes behind is fully up",
                1f,
                CharLiftGeometry.floatWeight(
                        CharLiftGeometry.waveCoordinate(0f, -textSize * 1.5f, wave)),
                1e-6f);
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

    // Device trace, Anti-Hero, two wrapped lines of 936px and 522px: while the
    // first line was sung (reveal 40 -> 862px) the front must follow that
    // reveal. The max(front, flowOffset + reveal) formula pinned it at 936px,
    // the first segment's width, because the unsung second segment contributed
    // its flow offset with zero reveal.
    @Test
    public void flowFrontFollowsThePartialRevealOfTheFirstWrappedLine() {
        float front = 0f;
        front = CharLiftGeometry.accumulateFlowFront(front, 40f, 936f);
        front = CharLiftGeometry.accumulateFlowFront(front, 0f, 522f);
        assertEquals(40f, front, 1e-4f);

        front = 0f;
        front = CharLiftGeometry.accumulateFlowFront(front, 862f, 936f);
        front = CharLiftGeometry.accumulateFlowFront(front, 0f, 522f);
        assertEquals(862f, front, 1e-4f);
        assertTrue("front must not be pinned to the first segment width", front < 936f);
    }

    @Test
    public void flowFrontCrossesTheWrapAsTheSumOfSegmentReveals() {
        float front = 0f;
        front = CharLiftGeometry.accumulateFlowFront(front, 936f, 936f);
        front = CharLiftGeometry.accumulateFlowFront(front, 120f, 522f);
        assertEquals(1056f, front, 1e-4f);

        front = 0f;
        front = CharLiftGeometry.accumulateFlowFront(front, 936f, 936f);
        front = CharLiftGeometry.accumulateFlowFront(front, 522f, 522f);
        assertEquals(1458f, front, 1e-4f);
    }

    @Test
    public void flowFrontClampsOverlongRevealsAndIgnoresInvalidOnes() {
        assertEquals(936f, CharLiftGeometry.accumulateFlowFront(0f, 1000f, 936f), 1e-4f);
        assertEquals(10f, CharLiftGeometry.accumulateFlowFront(10f, -5f, 936f), 1e-4f);
        assertEquals(10f, CharLiftGeometry.accumulateFlowFront(10f, Float.NaN, 936f), 1e-4f);
    }
}
