package io.github.andrealtb.lockscreenlyrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.os.Bundle;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@SuppressLint("PrivateApi") // This resolver only targets LSPosed-hosted vendor SystemUI classes.
final class SystemUiDexKitAdapter {
    private static final Object DEXKIT_LOAD_LOCK = new Object();

    private static volatile boolean dexKitLoaded;

    private SystemUiDexKitAdapter() {
    }

    @SuppressLint({"DuplicateCreateDexKit", "PrivateApi"})
    static Targets resolve(ClassLoader classLoader) throws ReflectiveOperationException {
        ensureDexKitLoaded();
        // Resolution is cached by LockscreenLyricsModule; the bridge only lives for this
        // one initialization transaction.
        //noinspection DuplicateCreateDexKit -- all targets resolve in one initialization transaction.
        try (DexKitBridge bridge = DexKitBridge.create(classLoader, true)) {
            Class<?> rusManagerClass = findSingleClass(
                    bridge,
                    classLoader,
                    "OPlus media RUS manager",
                    "com.oplus.systemui.media",
                    "parseSaveXmlValue whiteList: ",
                    "getRusWhiteList: cache is empty",
                    "app_systemui_oplus_media_controller_config.xml");
            Class<?> selectorClass = findSingleClass(
                    bridge,
                    classLoader,
                    "OPlus media action selector",
                    "com.oplus.systemui.media",
                    "not rule, use Actions",
                    "oplusActionConfig=",
                    "Test MediaAction, but not rule, use notification Actions");
            Class<?> strategyClass = findSingleClass(
                    bridge,
                    classLoader,
                    "OPlus media action strategy",
                    "com.oplus.systemui.media",
                    "createActionsFromState. oplusActionConfigList = ");
            Class<?> lyricLoaderClass = findSingleClass(
                    bridge,
                    classLoader,
                    "OPlus lyric loader",
                    "com.oplus.systemui.media",
                    "loadLyricInBg reason: lyric is avilable, lyric= ",
                    "loadLyricInBg reason: song changed, lyric= ",
                    "Failed to parse lyric data: ");
            Class<?> seedlingBundleClass = findSingleClass(
                    bridge,
                    classLoader,
                    "Seedling media bundle mapper",
                    "com.oplus.systemui.seedlingservice",
                    "mediaId",
                    "currentLyric",
                    "lastPositionUpdateTime",
                    "shouldShowLyric");

            Method dealEndTag = requireUniqueMethod(
                    rusManagerClass,
                    "RUS end-tag handler",
                    method -> Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == void.class
                            && hasParameterTypes(
                            method,
                            String.class,
                            Set.class,
                            Set.class,
                            List.class,
                            List.class,
                            Map.class,
                            Map.class,
                            Map.class,
                            Map.class,
                            Map.class));
            Method saveListToSp = requireUniqueMethod(
                    rusManagerClass,
                    "RUS persistence",
                    method -> !Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == void.class
                            && hasParameterTypes(
                            method,
                            Context.class,
                            Set.class,
                            Set.class,
                            Map.class,
                            Map.class,
                            Map.class,
                            Map.class,
                            Map.class));
            Method getRusWhiteList = requireUniqueMethod(
                    rusManagerClass,
                    "RUS whitelist getter",
                    method -> !Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 0
                            && List.class.isAssignableFrom(method.getReturnType()));
            Method getLyricEntrance = requireUniqueMethod(
                    selectorClass,
                    "lyric entrance lookup",
                    method -> !Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == int.class
                            && hasParameterTypes(method, String.class));
            Method updatePkgActionsRule = requireUniqueMethod(
                    selectorClass,
                    "media action rule update",
                    method -> !Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == void.class
                            && hasParameterTypes(
                            method,
                            Map.class,
                            Map.class,
                            Map.class,
                            Map.class,
                            Map.class));
            Method createActionsFromState = requireUniqueMethod(
                    strategyClass,
                    "media action builder",
                    method -> !Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() != void.class
                            && method.getParameterCount() == 3
                            && method.getParameterTypes()[0] == String.class
                            && method.getParameterTypes()[2] == MediaController.class);
            Method loadLyricInBg = requireUniqueMethod(
                    lyricLoaderClass,
                    "lyricInfo loader",
                    method -> !Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 6
                            && method.getParameterTypes()[0] == String.class
                            && method.getParameterTypes()[1] == MediaMetadata.class
                            && method.getParameterTypes()[2] == String.class
                            && method.getParameterTypes()[3] == String.class
                            && method.getParameterTypes()[4] == String.class
                            && method.getReturnType() == method.getParameterTypes()[5]);
            Method mediaDataToBundle = requireUniqueMethod(
                    seedlingBundleClass,
                    "Seedling media bundle mapper",
                    method -> Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == Bundle.class
                            && method.getParameterCount() == 3
                            && List.class.isAssignableFrom(method.getParameterTypes()[0])
                            && method.getParameterTypes()[1] == boolean.class
                            && method.getParameterTypes()[2] == boolean.class);

            return new Targets(
                    dealEndTag,
                    saveListToSp,
                    getRusWhiteList,
                    getLyricEntrance,
                    updatePkgActionsRule,
                    createActionsFromState,
                    loadLyricInBg,
                    mediaDataToBundle,
                    true);
        }
    }

    static Targets resolveLegacy(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> rusManagerClass = classLoader.loadClass(
                "com.oplus.systemui.media.seedling.rus.OplusMediaRusUpdateManager");
        Class<?> selectorClass = classLoader.loadClass(
                "com.oplus.systemui.media.controls.pipeline.MediaActionPrioritySelectorImpl");
        Class<?> strategyClass = classLoader.loadClass(
                "com.oplus.systemui.media.controls.pipeline.OplusMediaDataManagerStrategy");
        Class<?> mediaDataClass = classLoader.loadClass(
                "com.android.systemui.media.controls.shared.model.MediaData");
        Class<?> managerClass = classLoader.loadClass(
                "com.oplus.systemui.media.controls.pipeline.OplusMediaDataManagerExImpl");
        Class<?> lyricDataClass = classLoader.loadClass(
                "com.android.systemui.media.controls.models.player.OplusMediaLyricData");
        Class<?> seedlingBundleClass = classLoader.loadClass(
                "com.oplus.systemui.seedlingservice.utils.SeedlingMediaDataHandleUtils");

        return new Targets(
                rusManagerClass.getDeclaredMethod(
                        "dealEndTag",
                        String.class,
                        Set.class,
                        Set.class,
                        List.class,
                        List.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class),
                rusManagerClass.getDeclaredMethod(
                        "saveListToSP",
                        Context.class,
                        Set.class,
                        Set.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class),
                rusManagerClass.getDeclaredMethod("getRusWhiteList"),
                selectorClass.getDeclaredMethod("getLyricEntrance", String.class),
                selectorClass.getDeclaredMethod(
                        "updatePkgActionsRule",
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class),
                strategyClass.getDeclaredMethod(
                        "createActionsFromState",
                        String.class,
                        mediaDataClass,
                        MediaController.class),
                managerClass.getDeclaredMethod(
                        "loadLyricInBg",
                        String.class,
                        MediaMetadata.class,
                        String.class,
                        String.class,
                        String.class,
                        lyricDataClass),
                seedlingBundleClass.getDeclaredMethod(
                        "mediaDataToBundle",
                        List.class,
                        boolean.class,
                        boolean.class),
                false);
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

    private static Class<?> findSingleClass(
            DexKitBridge bridge,
            ClassLoader classLoader,
            String description,
            String packageName,
            String... strings) throws ReflectiveOperationException {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .searchPackages(packageName)
                .matcher(ClassMatcher.create().usingEqStrings(strings)));
        if (classes.size() != 1) {
            throw new IllegalStateException(
                    "Expected one " + description + ", found " + classes.size() + ": " + classes);
        }
        ClassData classData = classes.get(0);
        return classData.getInstance(classLoader);
    }

    private static Method requireUniqueMethod(
            Class<?> owner,
            String description,
            Predicate<Method> predicate) {
        Method match = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (!predicate.test(method)) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException(
                        "Expected one " + description + " in " + owner.getName()
                                + ", found at least " + match + " and " + method);
            }
            match = method;
        }
        if (match == null) {
            throw new IllegalStateException(
                    "No " + description + " found in " + owner.getName());
        }
        match.setAccessible(true);
        return match;
    }

    private static boolean hasParameterTypes(Method method, Class<?>... parameterTypes) {
        return Arrays.equals(method.getParameterTypes(), parameterTypes);
    }

    static final class Targets {
        final Method dealEndTag;
        final Method saveListToSp;
        final Method getRusWhiteList;
        final Method getLyricEntrance;
        final Method updatePkgActionsRule;
        final Method createActionsFromState;
        final Method loadLyricInBg;
        final Method mediaDataToBundle;
        final boolean resolvedByDexKit;

        Targets(
                Method dealEndTag,
                Method saveListToSp,
                Method getRusWhiteList,
                Method getLyricEntrance,
                Method updatePkgActionsRule,
                Method createActionsFromState,
                Method loadLyricInBg,
                Method mediaDataToBundle,
                boolean resolvedByDexKit) {
            this.dealEndTag = dealEndTag;
            this.saveListToSp = saveListToSp;
            this.getRusWhiteList = getRusWhiteList;
            this.getLyricEntrance = getLyricEntrance;
            this.updatePkgActionsRule = updatePkgActionsRule;
            this.createActionsFromState = createActionsFromState;
            this.loadLyricInBg = loadLyricInBg;
            this.mediaDataToBundle = mediaDataToBundle;
            this.resolvedByDexKit = resolvedByDexKit;
        }
    }
}
