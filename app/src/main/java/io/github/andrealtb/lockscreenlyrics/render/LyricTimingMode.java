package io.github.andrealtb.lockscreenlyrics.render;

/**
 * Indicates whether a {@link WordLine} carries per-word timing data
 * ({@link #WORD_TIMED}) or only a line-level timestamp
 * ({@link #LINE_TIMED}). Originally nested inside
 * {@code LockscreenLyricsModule}; promoted to a top-level render enum in
 * step 3.1 so callers outside the module can branch on timing fidelity.
 */
public enum LyricTimingMode {
    WORD_TIMED,
    LINE_TIMED
}
