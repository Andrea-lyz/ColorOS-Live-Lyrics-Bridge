package io.github.andrealtb.lockscreenlyrics.render;

/** Presentation-only continuous sweep for line timestamps; never manufactures word timings. */
public final class LineTimedRevealPolicy {
    private LineTimedRevealPolicy() {}

    public static long revealEnd(long start, long displayEnd) {
        long duration = Math.max(1L, displayEnd - start);
        // Finish before the row handoff so the trailing feather gets several fully lit frames.
        return start + duration - Math.min(120L, duration / 10L);
    }

    public static float progress(long start, long displayEnd, long position) {
        if (position <= start) return 0f;
        long end = revealEnd(start, displayEnd);
        if (position >= end) return 1f;
        return (position - start) / (float) Math.max(1L, end - start);
    }

    public static float segmentReveal(float totalWidth, float precedingWidth, float width, float progress) {
        return Math.max(0f, Math.min(width, totalWidth * progress - precedingWidth));
    }

    public static boolean active(WordLyricModel model, WordLine line, long position) {
        return model != null && line != null && position >= line.timeMillis
                && model.findActiveLine(position) == line;
    }
}
