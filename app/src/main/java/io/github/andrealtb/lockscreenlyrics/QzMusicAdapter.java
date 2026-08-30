package io.github.andrealtb.lockscreenlyrics;

final class QzMusicAdapter implements PlayerAdapter {
    private static final String PACKAGE_NAME = "love.qz.music";

    @Override
    public String packageName() {
        return PACKAGE_NAME;
    }

    @Override
    public String displayName() {
        return "QZ Music";
    }

    @Override
    public LyricProviderCapabilities lyricCapabilities() {
        return LyricProviderCapabilities.ACTIVE_INTEGRATION;
    }

    @Override
    public boolean supportsLyricRelayMetadata() {
        return true;
    }

    @Override
    public boolean allowsModuleToReplaceUntrustedLyricInfo() {
        return true;
    }

    @Override
    public void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader) {

    }
}
