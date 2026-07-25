package io.github.andrealtb.lockscreenlyrics.render;

/**
 * Cache of the last drawn {@link WordLine} plus the playback position it was
 * drawn at. Held by {@link OfficialLyricTextRenderer} to avoid re-running the
 * full normalization + index lookup when nothing has moved since the previous
 * frame. Promoted from {@code LockscreenLyricsModule} in step 3.1.
 */
public final class CachedDrawFrame {

    public final WordLyricModel model;
    public final WordLine line;
    public final long lineTimeMillis;
    public final String normalizedText;
    public long capturedAtElapsedMs;

    public CachedDrawFrame(
            WordLyricModel model,
            WordLine line,
            long lineTimeMillis,
            String normalizedText,
            long capturedAtElapsedMs) {
        this.model = model;
        this.line = line;
        this.lineTimeMillis = lineTimeMillis;
        this.normalizedText = WordLyricRenderSupport.nullToEmpty(normalizedText);
        this.capturedAtElapsedMs = capturedAtElapsedMs;
    }
}
