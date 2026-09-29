package io.github.andrealtb.lockscreenlyrics;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves obfuscated OPlus plugin targets (media-model classes and the immersive lyric position
 * controller) without relying on {@code m6.*} or other obfuscated names.
 *
 * <p>The model that carries the lyric fields is {@code MediaModel} on ColorOS 16 and
 * {@code MediaInfo} on ColorOS 17 (SystemUIPlugin 17.000.002 wraps it in a new
 * {@code MediaModel(info=, progress=)}). Only the lyric fields are required. Album-art repair
 * binds separately and stays off when the icon models lack the ColorOS 16 shape: the ColorOS 17
 * StaticIcon has no Icon field and derives its colors from an artwork result the plugin does not
 * expose.</p>
 */
final class OplusPluginDexKitAdapter {
    private static final Object DEXKIT_LOAD_LOCK = new Object();
    private static final String[] MEDIA_MODEL_ANCHORS_COLOROS16 = {
            "MediaModel(uniqueId=", ", lyricModel=", ", isLyricSupported="};
    private static final String[] MEDIA_INFO_ANCHORS_COLOROS17 = {
            "MediaInfo(uniqueId=", ", lyricModel=", ", isLyricSupported="};

    private static volatile boolean dexKitLoaded;

    private OplusPluginDexKitAdapter() {
    }

    /**
     * Resolves every plugin target in one DexKit scan. The media-model targets and the lyric
     * position controller fail independently, so a missing one never costs the other.
     */
    @SuppressLint("DuplicateCreateDexKit")
    static Resolution resolveAll(ClassLoader classLoader, String lyricsRecyclerViewClass) {
        ensureDexKitLoaded();
        //noinspection DuplicateCreateDexKit -- this plugin ClassLoader is scanned once.
        try (DexKitBridge bridge = DexKitBridge.create(classLoader, true)) {
            Method controller = null;
            Throwable controllerFailure = null;
            try {
                controller = findLyricPositionController(bridge, classLoader, lyricsRecyclerViewClass);
            } catch (Throwable t) {
                controllerFailure = t;
            }
            Targets targets = null;
            Throwable targetsFailure = null;
            try {
                targets = resolveTargets(bridge, classLoader);
            } catch (Throwable t) {
                targetsFailure = t;
            }
            return new Resolution(targets, targetsFailure, controller, controllerFailure);
        }
    }

    private static Targets resolveTargets(DexKitBridge bridge, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> mediaModelClass = findSingleClass(
                bridge,
                classLoader,
                "MediaModel",
                MEDIA_MODEL_ANCHORS_COLOROS16,
                MEDIA_INFO_ANCHORS_COLOROS17);
        Class<?> lyricModelClass = findSingleClass(
                bridge,
                classLoader,
                "LyricModel",
                new String[] {"LyricModel(lines="});
        return bindResolvedClasses(
                mediaModelClass,
                lyricModelClass,
                modelClass -> bindArtwork(
                        modelClass,
                        findSingleClass(
                                bridge,
                                classLoader,
                                "MultiIconModel",
                                new String[] {"MultiIconModel(staticIcon=", ", lottieIcon="}),
                        findSingleClass(
                                bridge,
                                classLoader,
                                "StaticIcon",
                                new String[] {"StaticIcon(icon=", ", iconModelForCard="}),
                        findSingleClass(
                                bridge,
                                classLoader,
                                "NormalIcon",
                                new String[] {"NormalIcon(icon=", ", primaryColor="}),
                        findSingleClass(
                                bridge,
                                classLoader,
                                "LottieIcon",
                                new String[] {"LottieIcon[assetName: ", " repeatCount="})),
                true);
    }

    /**
     * The immersive controller entry {@code void (Long position, boolean animate)} that forwards
     * to the recycler's timed {@code (boolean, long)} method and schedules the next row change
     * from the same position. Matched by shape and call, never by its obfuscated name.
     */
    private static Method findLyricPositionController(
            DexKitBridge bridge,
            ClassLoader classLoader,
            String lyricsRecyclerViewClass) throws ReflectiveOperationException {
        MethodDataList methods = bridge.findMethod(FindMethod.create()
                .matcher(MethodMatcher.create()
                        .paramTypes(Long.class, boolean.class)
                        .returnType(void.class)
                        .addInvoke(MethodMatcher.create()
                                .declaredClass(lyricsRecyclerViewClass)
                                .paramTypes(boolean.class, long.class)
                                .returnType(void.class))));
        if (methods.size() != 1) {
            throw new IllegalStateException(
                    "Expected one lyric position controller, found " + methods.size());
        }
        Method method = methods.get(0).getMethodInstance(classLoader);
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException("Lyric position controller is static: " + method);
        }
        method.setAccessible(true);
        return method;
    }

    static Targets legacy(ClassLoader classLoader) throws ReflectiveOperationException {
        return bindResolvedClasses(
                classLoader.loadClass("m6.t"),
                classLoader.loadClass("m6.s"),
                modelClass -> bindArtwork(
                        modelClass,
                        classLoader.loadClass("m6.v"),
                        classLoader.loadClass("m6.z"),
                        classLoader.loadClass("m6.i"),
                        classLoader.loadClass("m6.q")),
                false);
    }

    /**
     * Binds the required lyric fields. An artwork binding failure only turns album-art repair
     * off and is kept for the install log.
     */
    static Targets bindResolvedClasses(
            Class<?> mediaModelClass,
            Class<?> lyricModelClass,
            ArtworkBinder artworkBinder,
            boolean resolvedByDexKit) {
        Field lyricModelField = requireField(mediaModelClass, lyricModelClass, 0);
        Field lyricSupportedField = requireBooleanFieldAfter(lyricModelField);
        Artwork artwork = null;
        Throwable artworkFailure = null;
        try {
            artwork = artworkBinder.bind(mediaModelClass);
        } catch (Throwable t) {
            artworkFailure = t;
        }
        return new Targets(
                mediaModelClass,
                lyricModelField,
                lyricSupportedField,
                artwork,
                artworkFailure,
                resolvedByDexKit);
    }

    static Artwork bindArtwork(
            Class<?> mediaModelClass,
            Class<?> multiIconClass,
            Class<?> staticIconClass,
            Class<?> normalIconClass,
            Class<?> lottieIconClass) throws ReflectiveOperationException {
        Class<?> iconModelClass = normalIconClass.getSuperclass();
        if (iconModelClass == null || iconModelClass == Object.class) {
            throw new IllegalStateException("NormalIcon has no icon-model superclass");
        }
        Field albumArtField = requireField(mediaModelClass, multiIconClass, 0);
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
        return new Artwork(
                albumArtField,
                staticIconGetter,
                lottieIconGetter,
                staticDrawableField,
                staticBitmapField,
                cardIconModelField,
                bitmapGetter,
                colorGetter,
                staticIconConstructor,
                normalIconConstructor,
                multiIconConstructor);
    }

    private static Class<?> findSingleClass(
            DexKitBridge bridge,
            ClassLoader classLoader,
            String description,
            String[]... anchorSets) throws ReflectiveOperationException {
        ClassDataList classes = bridge.findClass(FindClass.create()
                .matcher(SystemUiDexKitAdapter.buildAnchorMatcher(anchorSets)));
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

    /**
     * The lyric-support flag is the first boolean after the lyric model, matching the property
     * and toString order on ColorOS 16 ({@code t -> u}) and 17 ({@code p -> q}). The last boolean
     * is not it: ColorOS 17 appends {@code artworkFullBgEnable}. ART lists declared fields in dex
     * order, which follows these obfuscated names; the module still checks every write against
     * the {@code isLyricSupported=} label.
     */
    static Field requireBooleanFieldAfter(Field lyricModelField) {
        Class<?> owner = lyricModelField.getDeclaringClass();
        boolean afterLyricModel = false;
        for (Field field : owner.getDeclaredFields()) {
            if (field.equals(lyricModelField)) {
                afterLyricModel = true;
                continue;
            }
            if (afterLyricModel
                    && !Modifier.isStatic(field.getModifiers())
                    && field.getType() == boolean.class) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException(
                "Missing boolean after " + lyricModelField.getName() + " in " + owner.getName());
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

    static final class Resolution {
        private final Targets targets;
        private final Throwable targetsFailure;
        /** Null when the plugin build has no unique controller entry; the module then only guards. */
        final Method lyricPositionController;
        final Throwable lyricPositionControllerFailure;

        Resolution(
                Targets targets,
                Throwable targetsFailure,
                Method lyricPositionController,
                Throwable lyricPositionControllerFailure) {
            this.targets = targets;
            this.targetsFailure = targetsFailure;
            this.lyricPositionController = lyricPositionController;
            this.lyricPositionControllerFailure = lyricPositionControllerFailure;
        }

        Targets requireTargets() {
            if (targets == null) {
                throw new IllegalStateException("OPlus plugin media-model targets unresolved", targetsFailure);
            }
            return targets;
        }
    }

    interface ArtworkBinder {
        Artwork bind(Class<?> mediaModelClass) throws ReflectiveOperationException;
    }

    static final class Targets {
        final Class<?> mediaModelClass;
        final Field lyricModelField;
        final Field lyricSupportedField;
        /** Null when album-art repair is unavailable; {@link #artworkFailure} says why. */
        final Artwork artwork;
        final Throwable artworkFailure;
        final boolean resolvedByDexKit;

        Targets(
                Class<?> mediaModelClass,
                Field lyricModelField,
                Field lyricSupportedField,
                Artwork artwork,
                Throwable artworkFailure,
                boolean resolvedByDexKit) {
            this.mediaModelClass = mediaModelClass;
            this.lyricModelField = lyricModelField;
            this.lyricSupportedField = lyricSupportedField;
            this.artwork = artwork;
            this.artworkFailure = artworkFailure;
            this.resolvedByDexKit = resolvedByDexKit;
        }
    }

    static final class Artwork {
        final Field albumArtField;
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

        Artwork(
                Field albumArtField,
                Method staticIconGetter,
                Method lottieIconGetter,
                Field staticDrawableField,
                Field staticBitmapField,
                Field cardIconModelField,
                Method iconModelBitmapGetter,
                Method iconModelColorGetter,
                Constructor<?> staticIconConstructor,
                Constructor<?> normalIconConstructor,
                Constructor<?> multiIconConstructor) {
            this.albumArtField = albumArtField;
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
        }
    }
}
