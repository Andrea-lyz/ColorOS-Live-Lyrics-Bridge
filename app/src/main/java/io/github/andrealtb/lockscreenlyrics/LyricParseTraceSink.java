package io.github.andrealtb.lockscreenlyrics;

/**
 * Trace and failure outlet for {@link NativeLyricModelAssembler}. The
 * assembler stays free of Android log classes so the whole native lyric
 * payload pipeline can run under plain JUnit; the module implements this
 * with its existing {@code LockscreenLyricsParse} trace tag and structured
 * error logging. Phase 6 slice 5.
 */
interface LyricParseTraceSink {
    /** Whether verbose parse tracing is currently enabled. */
    boolean traceEnabled();

    /** Emits one already-enabled verbose parse trace message. */
    void trace(String message);

    /** Reports a Lyrics Core parser failure without crashing the injected process. */
    void onCoreParseFailure(boolean primarySource, Throwable failure);
}
