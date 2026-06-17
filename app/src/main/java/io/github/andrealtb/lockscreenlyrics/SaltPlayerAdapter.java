package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Constructor;

import io.github.libxposed.api.XposedInterface;

final class SaltPlayerAdapter implements PlayerAdapter {
    private static final String PACKAGE_NAME = "com.salt.music";
    private static final String HOOK_ID_PRIMARY = "salt-player-lyric-result-primary";
    private static final String HOOK_ID_SYNTHETIC = "salt-player-lyric-result-synthetic";

    @Override
    public String packageName() {
        return PACKAGE_NAME;
    }

    @Override
    public String displayName() {
        return "Salt Player";
    }

    @Override
    public void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader) {
        try {
            Class<?> sourceClass = classLoader.loadClass("androidx.obf.rw0");
            Class<?> lyricResultClass = classLoader.loadClass("androidx.obf.qw0");

            Constructor<?> primary = lyricResultClass.getDeclaredConstructor(sourceClass, String.class, String.class);
            primary.setAccessible(true);
            module.hook(primary)
                    .setId(HOOK_ID_PRIMARY)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(module::onPlayerLyricResultConstructed);

            Constructor<?> synthetic = lyricResultClass.getDeclaredConstructor(sourceClass, String.class, int.class);
            synthetic.setAccessible(true);
            module.hook(synthetic)
                    .setId(HOOK_ID_SYNTHETIC)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(module::onPlayerLyricResultConstructed);

            module.info("Hooked " + displayName() + " lyric result constructors");
        } catch (Throwable t) {
            module.error("Failed to hook " + displayName() + " lyric result constructors", t);
        }
    }
}
