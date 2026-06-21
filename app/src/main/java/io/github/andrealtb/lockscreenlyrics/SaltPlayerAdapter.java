package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Constructor;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.enums.MatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;

final class SaltPlayerAdapter implements PlayerAdapter {
    private static final String PACKAGE_NAME = "com.salt.music";
    private static final String HOOK_ID_PREFIX = "salt-player-lyric-result-";
    private static final String OBFUSCATED_PACKAGE = "androidx.obf";
    private static final String SOURCE_ENUM_MARKER_EMBEDDED = "EMBEDDED";
    private static final String SOURCE_ENUM_MARKER_LYRICS3 = "TAG_LYRICS3_V2";
    private static final String SCROLL_ENUM_MARKER_CAN_SCROLL = "CAN_SCROLL";
    private static final String SCROLL_ENUM_MARKER_NOT_SCROLL = "NOT_SCROLL";
    private static final Object DEXKIT_LOAD_LOCK = new Object();

    private static volatile boolean dexKitLoaded;

    @Override
    public String packageName() {
        return PACKAGE_NAME;
    }

    @Override
    public String displayName() {
        return "Salt Player";
    }

    @Override
    public LyricProviderCapabilities lyricCapabilities() {
        return LyricProviderCapabilities.PASSIVE_PARSER;
    }

    @Override
    public void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader) {
        // Some Salt Player builds do not publish the legacy desktop-lyric custom action.
        // Inject the public translation action so SystemUI can always build the toggle.
        module.installInjectedTranslationToggleActionHook(PACKAGE_NAME);

        try {
            ensureDexKitLoaded();
        } catch (Throwable t) {
            module.error("Failed to load DexKit for " + displayName(), t);
            return;
        }

        // This adapter resolves once when the player process becomes ready, then closes the
        // bridge after all related queries.
        try (DexKitBridge bridge = DexKitBridge.create(classLoader, true)) {
            ClassData sourceEnum = findSingleClassUsingStrings(
                    bridge,
                    "lyric source enum",
                    SOURCE_ENUM_MARKER_EMBEDDED,
                    SOURCE_ENUM_MARKER_LYRICS3);
            ClassData scrollEnum = findSingleClassUsingStrings(
                    bridge,
                    "lyric scroll enum",
                    SCROLL_ENUM_MARKER_CAN_SCROLL,
                    SCROLL_ENUM_MARKER_NOT_SCROLL);
            ClassData lyricResult = findLyricResultClass(bridge, sourceEnum, scrollEnum);

            Class<?> sourceEnumClass = sourceEnum.getInstance(classLoader);
            Class<?> lyricResultClass = lyricResult.getInstance(classLoader);
            int hookCount = hookLyricResultConstructors(
                    module,
                    lyricResultClass,
                    sourceEnumClass,
                    lyricCapabilities());
            if (hookCount == 0) {
                throw new IllegalStateException(
                        "No matching lyric result constructors in " + lyricResult.getName());
            }

            module.info("Hooked " + displayName()
                    + " lyric result constructors via DexKit: result=" + lyricResult.getName()
                    + ", source=" + sourceEnum.getName()
                    + ", scroll=" + scrollEnum.getName()
                    + ", count=" + hookCount);
        } catch (Throwable t) {
            module.error("Failed to hook " + displayName()
                    + " lyric result constructors via DexKit", t);
        }
    }

    private static void ensureDexKitLoaded() {
        if (dexKitLoaded) {
            return;
        }
        synchronized (DEXKIT_LOAD_LOCK) {
            if (dexKitLoaded) {
                return;
            }
            System.loadLibrary("dexkit");
            dexKitLoaded = true;
        }
    }

    private static ClassData findSingleClassUsingStrings(
            DexKitBridge bridge,
            String description,
            String... strings) {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .searchPackages(OBFUSCATED_PACKAGE)
                .matcher(ClassMatcher.create().usingEqStrings(strings)));
        return requireSingleClass(description, classes);
    }

    private static ClassData findLyricResultClass(
            DexKitBridge bridge,
            ClassData sourceEnum,
            ClassData scrollEnum) {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .searchPackages(OBFUSCATED_PACKAGE)
                .matcher(ClassMatcher.create()
                        .fields(FieldsMatcher.create()
                                .addForType(sourceEnum.getName())
                                .addForType(scrollEnum.getName())
                                .matchType(MatchType.Contains))));
        return requireSingleClass("lyric result class", classes);
    }

    private static ClassData requireSingleClass(String description, ClassDataList classes) {
        if (classes.size() == 1) {
            return classes.get(0);
        }
        throw new IllegalStateException(
                "Expected one Salt Player " + description + ", found " + classes.size()
                        + ": " + classes);
    }

    private static int hookLyricResultConstructors(
            LockscreenLyricsModule module,
            Class<?> lyricResultClass,
            Class<?> sourceEnumClass,
            LyricProviderCapabilities capabilities) {
        int count = 0;
        for (Constructor<?> constructor : lyricResultClass.getDeclaredConstructors()) {
            String kind = lyricConstructorKind(constructor, sourceEnumClass);
            if (kind == null) {
                continue;
            }
            constructor.setAccessible(true);
            module.hook(constructor)
                    .setId(HOOK_ID_PREFIX
                            + simpleClassName(lyricResultClass.getName()) + "-" + kind)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> module.onPlayerLyricResultConstructed(
                            chain,
                            capabilities));
            count++;
        }
        return count;
    }

    private static String lyricConstructorKind(
            Constructor<?> constructor,
            Class<?> sourceEnumClass) {
        Class<?>[] types = constructor.getParameterTypes();
        if (types.length != 3
                || types[1] != String.class
                || types[0] != sourceEnumClass) {
            return null;
        }
        if (types[2] == String.class) {
            return "primary";
        }
        if (types[2] == int.class) {
            return "synthetic";
        }
        return null;
    }

    private static String simpleClassName(String className) {
        int index = className.lastIndexOf('.');
        return (index >= 0 ? className.substring(index + 1) : className).toLowerCase(Locale.ROOT);
    }
}
