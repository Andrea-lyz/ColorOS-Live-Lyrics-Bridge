package io.github.andrealtb.lockscreenlyrics.render;

/**
 * Shared render constants consumed by {@link WordLine}, {@link WordLyricModel},
 * and {@link OfficialLyricTextRenderer}. They used to live as private static
 * finals on {@code LockscreenLyricsModule} but were promoted here in step 3.1 of
 * the LockscreenLyricsModule split. Keep values identical to the original
 * literals so the AOD baseline (180 ms suppression, 0.9 inactive row scale)
 * is preserved.
 */
public final class WordLyricRenderConstants {

    private WordLyricRenderConstants() {
    }

    public static final float OFFICIAL_LYRIC_INACTIVE_ROW_SCALE = 0.9f;

    public static final boolean OFFICIAL_SLOT_ALIAS_REUSE_ENABLED = true;

    public static final float OFFICIAL_LYRIC_ROW_EASE_X1 = 0.28f;

    public static final float OFFICIAL_LYRIC_ROW_EASE_Y1 = 0f;

    public static final float OFFICIAL_LYRIC_ROW_EASE_X2 = 0.46f;

    public static final float OFFICIAL_LYRIC_ROW_EASE_Y2 = 1f;

    public static final float LYRIC_SLOT_HEIGHT_DP = 80f;

    public static final float LYRIC_SLOT_MIN_HEIGHT_DP = 56f;

    public static final float LYRIC_SLOT_VERTICAL_PADDING_DP = 12f;

    public static final float LYRIC_SLOT_BOTTOM_SAFETY_DP = 1f;

    public static final float OFFICIAL_LYRIC_ACTIVE_ROW_SCALE = 1.0f;

    public static final long OFFICIAL_LYRIC_ROW_SCALE_ANIMATION_MS = 340L;

    /**
     * How far unsung characters rest below the baseline, as a fraction of the
     * text size. Salt Player's {@code floatUpPercentage} default.
     */
    public static final float CHAR_LIFT_MAX_FACTOR = 0.10f;

    /**
     * Width of the raised-cosine transition window as a fraction of the text
     * size, so the wave touches the character being sung plus one or two on
     * each side. Salt Player uses exactly 3.0.
     */
    public static final float CHAR_LIFT_WAVE_WIDTH_FACTOR = 3.0f;

    /** How long the unsung text takes to settle into its sunk position when a row activates. */
    public static final long CHAR_LIFT_SINK_IN_MS = 240L;

    /**
     * How long the wave takes to run off the end of the text once the last word
     * is revealed. Without it the front parks at the end of the line with the
     * final characters still inside the transition window, leaving them
     * permanently short of the baseline.
     */
    public static final long CHAR_LIFT_FINISH_MS = 240L;

    /** Clearance kept between sunk glyphs and whatever sits below them. */
    public static final float CHAR_LIFT_MIN_CLEARANCE_DP = 1f;
}
