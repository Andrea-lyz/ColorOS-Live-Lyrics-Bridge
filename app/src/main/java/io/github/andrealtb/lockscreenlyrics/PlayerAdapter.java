package io.github.andrealtb.lockscreenlyrics;

interface PlayerAdapter {
    String packageName();

    String displayName();

    void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader);
}
