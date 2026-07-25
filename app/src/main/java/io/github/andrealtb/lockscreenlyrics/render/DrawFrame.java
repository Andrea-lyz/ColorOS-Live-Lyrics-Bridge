package io.github.andrealtb.lockscreenlyrics.render;

/**
 * Per-frame data handed to {@link OfficialLyricTextRenderer}. Carries the
 * active {@link WordLine}, the resolved active / scale indices, playback
 * position, glow position, and the row-scale animation flag consumed by the
 * row scale / focused reveal animation. Promoted from
 * {@code LockscreenLyricsModule} in step 3.1.
 */
public final class DrawFrame {

    public final WordLyricModel model;
    public final WordLine line;
    public final int lineIndex;
    public final int activeIndex;
    public final int scaleActiveIndex;
    public final long position;
    public final long glowPosition;
    public final boolean active;
    public final boolean focused;
    public final boolean rowScaleAnimationAllowed;

    public DrawFrame(
            WordLyricModel model,
            WordLine line,
            int lineIndex,
            int activeIndex,
            int scaleActiveIndex,
            long position,
            long glowPosition,
            boolean active,
            boolean focused,
            boolean rowScaleAnimationAllowed) {
        this.model = model;
        this.line = line;
        this.lineIndex = lineIndex;
        this.activeIndex = activeIndex;
        this.scaleActiveIndex = scaleActiveIndex;
        this.position = position;
        this.glowPosition = glowPosition;
        this.active = active;
        this.focused = focused;
        this.rowScaleAnimationAllowed = rowScaleAnimationAllowed;
    }
}
