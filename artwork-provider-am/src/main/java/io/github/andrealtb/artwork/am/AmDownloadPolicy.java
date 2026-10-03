package io.github.andrealtb.artwork.am;

/** A request that has started resolving completes and caches, whatever the client does next. */
final class AmDownloadPolicy {
    private AmDownloadPolicy() {}

    static boolean keepDownloading(boolean started, boolean finished, boolean cancelled, int detached, int maxDetached) {
        return started && !finished && !cancelled && detached < maxDetached;
    }
}
