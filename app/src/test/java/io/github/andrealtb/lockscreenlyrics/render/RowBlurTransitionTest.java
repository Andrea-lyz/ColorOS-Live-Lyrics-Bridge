package io.github.andrealtb.lockscreenlyrics.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RowBlurTransitionTest {
    private static final float RADIUS = 6f;
    private static final float SHARPEN = WordLyricRenderConstants.OFFICIAL_LYRIC_ROW_SHARPEN_FRACTION;

    @Test
    public void rowBecomingActiveIsFullySharpWellBeforeTheLineChangeEnds() {
        assertEquals(RADIUS, blur(RADIUS, 0f, 0f), 0.0001f);
        // Most of the blur is gone in the first fifth of the change.
        assertTrue(blur(RADIUS, 0f, 0.2f) < RADIUS * 0.25f);
        assertEquals(0f, blur(RADIUS, 0f, SHARPEN), 0.0001f);
        assertEquals(0f, blur(RADIUS, 0f, 1f), 0.0001f);
    }

    @Test
    public void rowLeavingFocusStaysMostlySharpWhileItIsStillLarge() {
        assertEquals(0f, blur(0f, RADIUS, 0f), 0.0001f);
        assertTrue(blur(0f, RADIUS, 0.3f) < RADIUS * 0.15f);
        assertEquals(RADIUS, blur(0f, RADIUS, 1f), 0.0001f);
    }

    @Test
    public void blurMovesMonotonicallyAndClampsOutsideTheChange() {
        float previousSharpening = RADIUS;
        float previousBlurring = 0f;
        for (int step = 0; step <= 20; step++) {
            float progress = step / 20f;
            float sharpening = blur(RADIUS, 0f, progress);
            float blurring = blur(0f, RADIUS, progress);
            assertTrue(sharpening <= previousSharpening + 0.0001f);
            assertTrue(blurring >= previousBlurring - 0.0001f);
            previousSharpening = sharpening;
            previousBlurring = blurring;
        }
        assertEquals(RADIUS, blur(RADIUS, 0f, -1f), 0.0001f);
        assertEquals(RADIUS, blur(0f, RADIUS, 2f), 0.0001f);
        // An interrupted change resumes from wherever the radius currently is.
        assertEquals(2f, blur(2f, RADIUS, 0f), 0.0001f);
    }

    private static float blur(float start, float target, float progress) {
        return WordLyricRenderSupport.rowBlurRadiusAt(start, target, progress, SHARPEN);
    }
}
