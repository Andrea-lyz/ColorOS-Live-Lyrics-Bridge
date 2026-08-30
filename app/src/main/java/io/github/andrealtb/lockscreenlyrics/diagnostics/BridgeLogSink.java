package io.github.andrealtb.lockscreenlyrics.diagnostics;

/** One destination for a formatted `[CLL]` line. */
public interface BridgeLogSink {
    void log(int androidLevel, String tag, String message, Throwable throwable);
}
