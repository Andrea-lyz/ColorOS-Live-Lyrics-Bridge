package io.github.andrealtb.lockscreenlyrics;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassDataList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Resolves obfuscated OPlus plugin media-model classes without relying on {@code m6.*}. */
final class OplusPluginDexKitAdapter {
    private static final Object DEXKIT_LOAD_LOCK = new Object();

    private static volatile boolean dexKitLoaded;

    private OplusPluginDexKitAdapter() {
    }

    @SuppressLint("DuplicateCreateDexKit")
    static Targets resolve(ClassLoader classLoader) throws ReflectiveOperationException {
        ensureDexKitLoaded();
        //noinspection DuplicateCreateDexKit -- this plugin ClassLoader is scanned once.
        try (DexKitBridge bridge = DexKitBridge.create(classLoader, true)) {
            Class<?> mediaModelClass = findSingleClass(
                    bridge,
                    classLoader,
                    "MediaModel",
                    "MediaModel(uniqueId=",
                    ", lyricModel=",
                    ", isLyricSupported=");
            Class<?> multiIconClass = findSingleClass(
                    bridge,
                    classLoader,
                    "MultiIconModel",
                    "MultiIconModel(staticIcon=",
                    ", lottieIcon=");
            Class<?> staticIconClass = findSingleClass(
                    bridge,
                    classLoader,
                    "StaticIcon",
                    "StaticIcon(icon=",
                    ", iconModelForCard=");
            Class<?> normalIconClass = findSingleClass(
                    bridge,
                    classLoader,
                    "NormalIcon",
                    "NormalIcon(icon=",
                    ", primaryColor=");
            Class<?> lottieIconClass = findSingleClass(
                    bridge,
                    classLoader,
                    "LottieIcon",
                    "LottieIcon[assetName: ",
                    " repeatCount=");
            Class<?> lyricModelClass = findSingleClass(
                    bridge,
                    classLoader,
                    "LyricModel",
                    "LyricModel(lines=");
            return bindResolvedClasses(
                    mediaModelClass,
                    multiIconClass,
                    staticIconClass,
                    normalIconClass,
                    lottieIconClass,
                    lyricModelClass,
                    true);
        }
    }

    static Targets legacy(ClassLoader classLoader) throws ReflectiveOperationException {
        return bindResolvedClasses(
                classLoader.loadClass("m6.t"),
                classLoader.loadClass("m6.v"),
                classLoader.loadClass("m6.z"),
                classLoader.loadClass("m6.i"),
                classLoader.loadClass("m6.q"),
                classLoader.loadClass("m6.s"),
                false);
    }

    static Targets bindResolvedClasses(
            Class<?> mediaModelClass,
            Class<?> multiIconClass,
            Class<?> staticIconClass,
            Class<?> normalIconClass,
            Class<?> lottieIconClass,
            Class<?> lyricModelClass,
            boolean resolvedByDexKit) throws ReflectiveOperationException {
        Class<?> iconModelClass = normalIconClass.getSuperclass();
        if (iconModelClass == null || iconModelClass == Object.class) {
            throw new IllegalStateException("NormalIcon has no icon-model superclass");
        }
        Field albumArtField = requireField(mediaModelClass, multiIconClass, 0);
        Field lyricModelField = requireField(mediaModelClass, lyricModelClass, 0);
        Field lyricSupportedField = requireLastField(mediaModelClass, boolean.class);
        requireField(staticIconClass, Icon.class, 0);
        Field staticDrawableField = requireField(staticIconClass, Drawable.class, 0);
        Field staticBitmapField = requireField(staticIconClass, Bitmap.class, 0);
        requireField(staticIconClass, iconModelClass, 0);
        Field cardIconModelField = requireField(staticIconClass, iconModelClass, 1);
        Method staticIconGetter = requireZeroArgMethod(multiIconClass, staticIconClass);
        Method lottieIconGetter = requireZeroArgMethod(multiIconClass, lottieIconClass);
        Method bitmapGetter = requireZeroArgMethod(iconModelClass, Bitmap.class);
        Method colorGetter = requireZeroArgMethod(iconModelClass, Integer.class);
        Constructor<?> staticIconConstructor = requireConstructor(
                staticIconClass,
                Icon.class,
                Drawable.class,
                Bitmap.class,
                iconModelClass,
                iconModelClass);
        Constructor<?> normalIconConstructor = requireConstructor(
                normalIconClass,
                Bitmap.class,
                Integer.class);
        Constructor<?> multiIconConstructor = requireConstructor(
                multiIconClass,
                staticIconClass,
                lottieIconClass);
        return new Targets(
                mediaModelClass,
                albumArtField,
                lyricModelField,
                lyricSupportedField,
                staticIconGetter,
                lottieIconGetter,
                staticDrawableField,
                staticBitmapField,
                cardIconModelField,
                bitmapGetter,
                colorGetter,
                staticIconConstructor,
                normalIconConstructor,
                multiIconConstructor,
                resolvedByDexKit);
    }

    private static Class<?> findSingleClass(
            DexKitBridge bridge,
            ClassLoader classLoader,
            String description,
            String... anchors) throws ReflectiveOperationException {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .matcher(ClassMatcher.create().usingEqStrings(anchors)));
        if (classes.size() != 1) {
            throw new IllegalStateException(
                    "Expected one " + description + ", found " + classes.size() + ": " + classes);
        }
        return classes.get(0).getInstance(classLoader);
    }

    private static Field requireField(Class<?> owner, Class<?> fieldType, int ordinal) {
        List<Field> matches = fields(owner, fieldType);
        if (ordinal >= matches.size()) {
            throw new IllegalStateException(
                    "Missing field " + fieldType.getName() + "[" + ordinal + "] in " + owner.getName());
        }
        Field field = matches.get(ordinal);
        field.setAccessible(true);
        return field;
    }

    private static Field requireLastField(Class<?> owner, Class<?> fieldType) {
        List<Field> matches = fields(owner, fieldType);
        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    "Missing field " + fieldType.getName() + " in " + owner.getName());
        }
        Field field = matches.get(matches.size() - 1);
        field.setAccessible(true);
        return field;
    }

    private static List<Field> fields(Class<?> owner, Class<?> fieldType) {
        ArrayList<Field> matches = new ArrayList<>();
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == fieldType) {
                    matches.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return matches;
    }

    private static Method requireZeroArgMethod(Class<?> owner, Class<?> returnType) {
        Method match = null;
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 0
                        || method.getReturnType() != returnType) {
                    continue;
                }
                if (match != null) {
                    throw new IllegalStateException(
                            "Ambiguous zero-arg " + returnType.getName() + " method in " + owner.getName());
                }
                match = method;
            }
            current = current.getSuperclass();
        }
        if (match == null) {
            throw new IllegalStateException(
                    "Missing zero-arg " + returnType.getName() + " method in " + owner.getName());
        }
        match.setAccessible(true);
        return match;
    }

    private static Constructor<?> requireConstructor(Class<?> owner, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Constructor<?> constructor = owner.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor;
    }

    private static void ensureDexKitLoaded() {
        if (dexKitLoaded) {
            return;
        }
        synchronized (DEXKIT_LOAD_LOCK) {
            if (!dexKitLoaded) {
                System.loadLibrary("dexkit");
                dexKitLoaded = true;
            }
        }
    }

    static final class Targets {
        final Class<?> mediaModelClass;
        final Field albumArtField;
        final Field lyricModelField;
        final Field lyricSupportedField;
        final Method staticIconGetter;
        final Method lottieIconGetter;
        final Field staticDrawableField;
        final Field staticBitmapField;
        final Field cardIconModelField;
        final Method iconModelBitmapGetter;
        final Method iconModelColorGetter;
        final Constructor<?> staticIconConstructor;
        final Constructor<?> normalIconConstructor;
        final Constructor<?> multiIconConstructor;
        final boolean resolvedByDexKit;

        Targets(
                Class<?> mediaModelClass,
                Field albumArtField,
                Field lyricModelField,
                Field lyricSupportedField,
                Method staticIconGetter,
                Method lottieIconGetter,
                Field staticDrawableField,
                Field staticBitmapField,
                Field cardIconModelField,
                Method iconModelBitmapGetter,
                Method iconModelColorGetter,
                Constructor<?> staticIconConstructor,
                Constructor<?> normalIconConstructor,
                Constructor<?> multiIconConstructor,
                boolean resolvedByDexKit) {
            this.mediaModelClass = mediaModelClass;
            this.albumArtField = albumArtField;
            this.lyricModelField = lyricModelField;
            this.lyricSupportedField = lyricSupportedField;
            this.staticIconGetter = staticIconGetter;
            this.lottieIconGetter = lottieIconGetter;
            this.staticDrawableField = staticDrawableField;
            this.staticBitmapField = staticBitmapField;
            this.cardIconModelField = cardIconModelField;
            this.iconModelBitmapGetter = iconModelBitmapGetter;
            this.iconModelColorGetter = iconModelColorGetter;
            this.staticIconConstructor = staticIconConstructor;
            this.normalIconConstructor = normalIconConstructor;
            this.multiIconConstructor = multiIconConstructor;
            this.resolvedByDexKit = resolvedByDexKit;
        }
    }
}
