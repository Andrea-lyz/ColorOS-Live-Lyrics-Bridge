package io.github.andrealtb.lockscreenlyrics.render;

/**
 * Which side of a duet a {@link WordLine} belongs to. Lines stay {@link #UNSPECIFIED} unless the
 * track carries both sides, so single-singer tracks never change layout.
 */
public enum LyricSide {
    UNSPECIFIED,
    PRIMARY,
    SECONDARY
}
