package io.github.andrealtb.lockscreenlyrics.render;

/**
 * Mutable holder for one wrapped line's start/end character indices and the
 * pixel width measured for that slice. Used by {@link OfficialLyricTextRenderer}
 * during layout to amortize the wrapped-line calculation across draws.
 */
public final class LyricDrawLine {
    public int start;
    public int end;
    public float width;
}
