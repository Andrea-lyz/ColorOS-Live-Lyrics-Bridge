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
                    new String[]{
                            "parseSaveXmlValue whiteList: ",
                            "getRusWhiteList: cache is empty",
                            "app_systemui_oplus_media_controller_config.xml"},
                    new String[]{
                            "parseSaveXmlValue whiteList: ",
                            "parseSaveXmlValue pkgRuleMap: ",
                            "applyConfig version="});
            Class<?> selectorClass = findSingleClass(
                    bridge,
                    classLoader,
                    "OPlus media action selector",
                    "com.oplus.systemui.media",
                    new String[]{
                            "not rule, use Actions",
                            "oplusActionConfig=",
                            "Test MediaAction, but not rule, use notification Actions"});
            Class<?> strategyClass = findSingleClass(
                    bridge,
                    classLoader,
                    "OPlus media action strategy",
                    "com.oplus.systemui.media",
                    new String[]{"createActionsFromState. oplusActionConfigList = "});
            Class<?> lyricLoaderClass = findSingleClass(
                    bridge,
                    classLoader,
                    "OPlus lyric loader",
                    "com.oplus.systemui.media",
                    new String[]{
                            "loadLyricInBg reason: lyric is avilable, lyric= ",
                            "loadLyricInBg reason: song changed, lyric= ",
                            "Failed to parse lyric data: "});
            Class<?> seedlingBundleClass = findSingleClass(
                    bridge,
                    classLoader,
                    "Seedling media bundle mapper",
                    "com.oplus.systemui.seedlingservice",
                    new String[]{
                            "mediaId",
                            "currentLyric",
                            "lastPositionUpdateTime",
                            "shouldShowLyric"});

            Method dealEndTag = requireUniqueMethod(
                    rusManagerClass,
                    "RUS end-tag handler",
                    SystemUiDexKitAdapter::isDealEndTagShape);
            Method saveListToSp = requireUniqueMethod(
                    rusManagerClass,
                    "RUS persistence",
                    SystemUiDexKitAdapter::isSaveListToSpShape);
            Method getRusWhiteList = findOptionalMethod(
                    rusManagerClass,
                    "RUS whitelist getter",
                    SystemUiDexKitAdapter::isWhiteListGetterShape);
            Method mediaRusConfigWhiteListGetter = findMediaRusConfigWhiteListGetter(classLoader);
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
                    mediaRusConfigWhiteListGetter,
                    getLyricEntrance,
                    updatePkgActionsRule,
                    createActionsFromState,
                    loadLyricInBg,
                    mediaDataToBundle,
                    true,
                    getRusWhiteList != null ? RUS_VARIANT_OLD : RUS_VARIANT_NEW);
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

        Method dealEndTag = rusManagerClass.getDeclaredMethod(
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
                Map.class);
        Method saveListToSp = findDeclaredMethod(
                rusManagerClass,
                "saveListToSP",
                new Class<?>[]{
                        Context.class,
                        Set.class,
                        Set.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class},
                new Class<?>[]{
                        Context.class,
                        Set.class,
                        Set.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        int.class});
        Method getRusWhiteList = findOptionalDeclaredMethod(rusManagerClass, "getRusWhiteList");
        Method mediaRusConfigWhiteListGetter = findMediaRusConfigWhiteListGetter(classLoader);
        Method updatePkgActionsRule = findDeclaredMethod(
                selectorClass,
                "updatePkgActionsRule",
                new Class<?>[]{
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class},
                new Class<?>[]{
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class,
                        Map.class});

        return new Targets(
                dealEndTag,
                saveListToSp,
                getRusWhiteList,
                mediaRusConfigWhiteListGetter,
                selectorClass.getDeclaredMethod("getLyricEntrance", String.class),
                updatePkgActionsRule,
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
                false,
                getRusWhiteList != null ? RUS_VARIANT_OLD : RUS_VARIANT_NEW);
    }

    private static Method findDeclaredMethod(
            Class<?> owner,
            String name,
            Class<?>[]... parameterSets) throws NoSuchMethodException {
        NoSuchMethodException lastFailure = null;
        for (Class<?>[] parameterTypes : parameterSets) {
            try {
                return owner.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    private static Method findOptionalDeclaredMethod(Class<?> owner, String name) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
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
            String[]... anchorSets) throws ReflectiveOperationException {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .searchPackages(packageName)
                .matcher(buildAnchorMatcher(anchorSets)));
        if (classes.size() != 1) {
            throw new IllegalStateException(
                    "Expected one " + description + ", found " + classes.size() + ": " + classes
                            + " (anchor sets=" + anchorSets.length + ")");
        }
        ClassData classData = classes.get(0);
        return classData.getInstance(classLoader);
    }

    /**
     * Each anchor set is an AND group (every string must be used by the same class); multiple
     * sets are combined with OR so one query tolerates structurally different builds.
     */
    static ClassMatcher buildAnchorMatcher(String[]... anchorSets) {
        if (anchorSets.length == 0) {
            throw new IllegalArgumentException("At least one anchor set required");
        }
        ClassMatcher matcher = ClassMatcher.create();
        if (anchorSets.length == 1) {
            matcher.usingEqStrings(anchorSets[0]);
        } else {
            ClassMatcher[] anchorMatchers = new ClassMatcher[anchorSets.length];
            for (int i = 0; i < anchorSets.length; i++) {
                anchorMatchers[i] = ClassMatcher.create().usingEqStrings(anchorSets[i]);
            }
            matcher.anyOf(anchorMatchers);
        }
        return matcher;
    }

    static Method findOptionalMethod(
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
        if (match != null) {
            match.setAccessible(true);
        }
        return match;
    }

    /**
     * ColorOS 16.0.10.x moved the persisted media RUS whitelist into a dedicated config value
     * object; the old {@code OplusMediaRusUpdateManager.getRusWhiteList()} was removed. The
     * getter keeps the same shape, so the same predicate applies.
     */
    private static Method findMediaRusConfigWhiteListGetter(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> mediaRusConfigClass;
        try {
            mediaRusConfigClass = classLoader.loadClass(
                    "com.oplus.systemui.media.seedling.rus.MediaRusConfig");
        } catch (ClassNotFoundException e) {
            return null;
        }
        return findOptionalMethod(
                mediaRusConfigClass,
                "media RUS config whitelist getter",
                SystemUiDexKitAdapter::isWhiteListGetterShape);
    }

    static boolean isDealEndTagShape(Method method) {
        return Modifier.isStatic(method.getModifiers())
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
                Map.class);
    }

    /**
     * ColorOS 16.0.9.x: instance method with {@code (Context, Set, Set, Map x5)}.
     * ColorOS 16.0.10.x: static method with {@code (Context, Set, Set, Map x5, Map, int)}.
     */
    static boolean isSaveListToSpShape(Method method) {
        if (method.getReturnType() != void.class) {
            return false;
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            return hasParameterTypes(
                    method,
                    Context.class,
                    Set.class,
                    Set.class,
                    Map.class,
                    Map.class,
                    Map.class,
                    Map.class,
                    Map.class);
        }
        return hasParameterTypes(
                method,
                Context.class,
                Set.class,
                Set.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                int.class);
    }

    /**
     * ColorOS 16.0.9.x passes five maps; 16.0.10.x adds a sixth (lyric enable) map.
     */
    static boolean isUpdatePkgActionsRuleShape(Method method) {
        if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) {
            return false;
        }
        return hasParameterTypes(
                method,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class)
                || hasParameterTypes(
                method,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class);
    }

    static boolean isWhiteListGetterShape(Method method) {
        return !Modifier.isStatic(method.getModifiers())
                && method.getParameterCount() == 0
                && List.class.isAssignableFrom(method.getReturnType());
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
        /** Present on ColorOS 16.0.9.x style builds; {@code null} on 16.0.10.x style builds. */
        final Method getRusWhiteList;
        /** Present on ColorOS 16.0.10.x style builds; {@code null} on older builds. */
        final Method mediaRusConfigWhiteListGetter;
        final Method getLyricEntrance;
        final Method updatePkgActionsRule;
        final Method createActionsFromState;
        final Method loadLyricInBg;
        final Method mediaDataToBundle;
        final boolean resolvedByDexKit;
        final String rusVariant;

        Targets(
                Method dealEndTag,
                Method saveListToSp,
                Method getRusWhiteList,
                Method mediaRusConfigWhiteListGetter,
                Method getLyricEntrance,
                Method updatePkgActionsRule,
                Method createActionsFromState,
                Method loadLyricInBg,
                Method mediaDataToBundle,
                boolean resolvedByDexKit,
                String rusVariant) {
            this.dealEndTag = dealEndTag;
            this.saveListToSp = saveListToSp;
            this.getRusWhiteList = getRusWhiteList;
            this.mediaRusConfigWhiteListGetter = mediaRusConfigWhiteListGetter;
            this.getLyricEntrance = getLyricEntrance;
            this.updatePkgActionsRule = updatePkgActionsRule;
            this.createActionsFromState = createActionsFromState;
            this.loadLyricInBg = loadLyricInBg;
            this.mediaDataToBundle = mediaDataToBundle;
            this.resolvedByDexKit = resolvedByDexKit;
            this.rusVariant = rusVariant;
        }
    }

    static final String RUS_VARIANT_OLD = "OLD";
    static final String RUS_VARIANT_NEW = "NEW";
}
