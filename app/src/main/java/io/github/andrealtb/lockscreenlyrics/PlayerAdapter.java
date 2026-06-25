package io.github.andrealtb.lockscreenlyrics;

interface PlayerAdapter {
    String packageName();

    String displayName();

    LyricProviderCapabilities lyricCapabilities();

    void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader);

    default boolean supportsLyricRelayMetadata() {
        return false;
    }

    default boolean mayRetainStaleLyricInfo() {
        return false;
    }

    default boolean allowsModuleToReplaceUntrustedLyricInfo() {
        return false;
    }

    default boolean rewritesPlayerLyricInfoMetadata() {
        return true;
    }

    default boolean publishesCapturedLyricToMediaSession() {
        return true;
    }
}
