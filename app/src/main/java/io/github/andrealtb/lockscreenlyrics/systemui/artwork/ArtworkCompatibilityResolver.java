package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Independent artwork capabilities. Shape/transition readiness is not implied by model resolution. */
public final class ArtworkCompatibilityResolver {
    private static final String ALBUM_LOG = "[updateAlbumArtView] animation is running, pending new drawable";
    private static final String R8_PACKAGE = "com.oplus.systemui.plugins.r8.";

    public record SurfaceTargets(Class<?> sectionClass, Field sectionViewModel,
            List<Constructor<?>> constructors, Method dispose, String imageResource,
            ArtworkPlaybackPolicy.Surface surface) {}

    /**
     * @param c17Status why the C17 profile or its DexKit roles are missing; empty when resolved by DexKit
     * @param cardSpringRunning C17 card spring running flag bound from DexKit, or {@code null}
     */
    public record Targets(ArtworkModelAccess model, SurfaceTargets card, SurfaceTargets immersive,
            ArtworkC17Access c17, String c17Status, Field cardSpringRunning) {}

    private ArtworkCompatibilityResolver() {}

    @SuppressLint("DuplicateCreateDexKit")
    public static Targets resolve(ClassLoader loader) throws ReflectiveOperationException {
        System.loadLibrary("dexkit");
        // One scan per ready plugin generation, owned by the independent artwork runtime.
        try (DexKitBridge dex = DexKitBridge.create(loader, true)) {
            ClassDataList flat = dex.findClass(FindClass.create().matcher(
                    ClassMatcher.create().usingEqStrings("MediaModel(uniqueId=", ", songName=", ", pkg=")));
            if (flat.size() > 1) throw new IllegalArgumentException("flat_model_ambiguous");
            Class<?> identity;
            Class<?> model;
            if (flat.size() == 1) {
                model = identity = flat.get(0).getInstance(loader);
            } else {
                identity = singleClass(dex, loader, "MediaInfo(uniqueId=", ", songName=", ", pkg=");
                model = singleClass(dex, loader, "MediaModel(info=", ", progress=");
            }
            ClassDataList cards = dex.findClass(FindClass.create().matcher(ClassMatcher.create().anyOf(
                    ClassMatcher.create().usingEqStrings("LACardModel(key=", ", mediaModel="),
                    ClassMatcher.create().usingEqStrings("LACardModel(key=", ", progressBarModel=null, mediaModel="))));
            if (cards.size() != 1) throw new IllegalArgumentException("card_model_ambiguous");
            Class<?> card = cards.get(0).getInstance(loader);
            Class<?> immersiveCard = null;
            ClassDataList wrappers = dex.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .usingEqStrings("LAImmersiveCardModel(contentModel=", ", laCardModel=")));
            if (wrappers.size() == 1) immersiveCard = wrappers.get(0).getInstance(loader);
            Field albumArt = null;
            if (flat.size() != 1) {
                try { albumArt = albumArtField(dex, loader, identity); }
                catch (ReflectiveOperationException | RuntimeException ignored) { /* reviewed letter fallback */ }
            }
            ArtworkModelAccess access = new ArtworkModelAccess(card, model, identity, immersiveCard, albumArt);
            MethodDataList albumUpdates = dex.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingEqStrings("updateAlbumArtIcon iconBitmap is null")
                            .returnType(void.class).paramCount(1)));
            if (albumUpdates.size() != 1) throw new IllegalArgumentException("card_section_ambiguous");
            Class<?> section = albumUpdates.get(0).getMethodInstance(loader).getDeclaringClass();
            SurfaceTargets ordinary = bindSurface(section, "img_album_art", ArtworkPlaybackPolicy.Surface.LOCKSCREEN_CARD);
            SurfaceTargets immersive = null;
            if (immersiveCard != null) {
                try {
                    Class<?> owner = singleClass(dex, loader, "MediaPlayerImmersiveSection",
                            "albumSectionAnimationRunner called, curMode=");
                    immersive = bindSurface(owner, "img_large_album_art", ArtworkPlaybackPolicy.Surface.IMMERSIVE);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Independent capability: an immersive miss never enables a guessed host or drops the card.
                }
            }
            ArtworkC17Access c17 = null;
            String c17Status = immersive == null ? "immersive_unresolved" : "sdk_unsupported";
            Field cardSpringRunning = null;
            if (access.splitModel && android.os.Build.VERSION.SDK_INT >= 33) {
                try { cardSpringRunning = cardSpringRunning(dex, loader); }
                catch (ReflectiveOperationException | RuntimeException ignored) { /* reviewed letter fallback */ }
            }
            if (access.splitModel && immersive != null && android.os.Build.VERSION.SDK_INT >= 33) {
                try {
                    Class<?> control = singleClass(dex, loader, "ImmersiveBackgroundController", ALBUM_LOG);
                    Class<?> background = singleClass(dex, loader, "MediaFullScreenView");
                    ArtworkC17Roles roles = null;
                    String roleFailure = "";
                    try { roles = controllerRoles(dex, loader, control, background); }
                    catch (ReflectiveOperationException | RuntimeException error) { roleFailure = describe(error); }
                    c17 = new ArtworkC17Access(immersive.sectionClass(), control, background,
                            albumUpdate(dex, loader, control), roles);
                    c17Status = roleFailure.isEmpty() ? "" : "dexkit_roles=" + roleFailure;
                } catch (ReflectiveOperationException | RuntimeException error) {
                    // Retain read-only model binding; the reason is reported by the runtime.
                    c17Status = describe(error);
                }
            }
            return new Targets(access, ordinary, immersive, c17, c17Status, cardSpringRunning);
        }
    }

    /**
     * Controller and fullscreen-view roles from the log lines that read or write them. Verified
     * offline against SystemUIPlugin 17.000.002 reference and PYB110 binaries: every role is
     * unique and equals the reviewed letter on both.
     */
    private static ArtworkC17Roles controllerRoles(DexKitBridge dex, ClassLoader loader, Class<?> control,
            Class<?> background) throws ReflectiveOperationException {
        Class<?> slot = loader.loadClass(
                "com.oplus.systemui.plugins.shared.template.component.media.view.SmartShapeableImageView");
        List<Field> switchWrites = accessed(dex, loader, control, "[switchRender] anim:", true);
        List<Field> pendingWrites = accessed(dex, loader, control, "[updateFromState] pending ", true);
        Field current = ArtworkC17Roles.ofType(
                accessed(dex, loader, control, "[updateFromState] ", false), slot, "c17_current");
        return new ArtworkC17Roles(
                current,
                ArtworkC17Roles.otherSlot(accessed(dex, loader, control, ALBUM_LOG, false), current, slot),
                ArtworkC17Roles.pendingModel(switchWrites, pendingWrites),
                ArtworkC17Roles.ofType(switchWrites, boolean.class, "c17_switching"),
                ArtworkC17Roles.ofType(pendingWrites, boolean.class, "c17_waiting_layout"),
                ArtworkC17Roles.ofType(accessed(dex, loader, control, "[onEnterImmersive] activeRenderer: ", true),
                        boolean.class, "c17_immersive"),
                ArtworkC17Roles.ofType(accessed(dex, loader, control, "[onLyricModeChanged] lyricVis: ", true),
                        boolean.class, "c17_lyric_mode"),
                ArtworkC17Roles.renderer(switchWrites),
                ArtworkC17Roles.ofType(accessed(dex, loader, background, "MediaFullScreenView#SwitchBg", true),
                        boolean.class, "c17_background_running"));
    }

    /**
     * The card spring is the RotatableImageView field typed in the r8 package; its running flag
     * is the base-class boolean that cancel() reads and start() writes (AndroidX DynamicAnimation
     * main-thread guard messages).
     */
    private static Field cardSpringRunning(DexKitBridge dex, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> image = loader.loadClass(
                "com.oplus.systemui.plugins.shared.template.component.media.view.RotatableImageView");
        Field spring = ArtworkC17Roles.single(List.of(image.getDeclaredFields()),
                field -> field.getType().getName().startsWith(R8_PACKAGE), "card_spring");
        Class<?> base = spring.getType().getSuperclass();
        if (base == null) throw new NoSuchFieldException("card_spring_base");
        return ArtworkC17Roles.common(
                accessed(dex, loader, base, "Animations may only be canceled on the main thread", false),
                accessed(dex, loader, base, "Animations may only be started on the main thread", true),
                boolean.class, "card_spring_running");
    }

    /**
     * Instance fields of {@code owner} read or written by any method using {@code anchor}.
     * Types are filtered in Java on the loaded Field, not by DexKit type matching.
     */
    private static List<Field> accessed(DexKitBridge dex, ClassLoader loader, Class<?> owner, String anchor,
            boolean write) throws ReflectiveOperationException {
        MethodMatcher method = MethodMatcher.create().usingEqStrings(anchor);
        FieldMatcher matcher = FieldMatcher.create().declaredClass(owner.getName());
        if (write) matcher.addWriteMethod(method);
        else matcher.addReadMethod(method);
        List<Field> fields = new ArrayList<>();
        for (FieldData data : dex.findField(FindField.create().matcher(matcher))) {
            Field field = data.getFieldInstance(loader);
            if (field.getDeclaringClass() == owner && !Modifier.isStatic(field.getModifiers())) fields.add(field);
        }
        return fields;
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isEmpty() ? "" : ":" + message);
    }

    private static SurfaceTargets bindSurface(Class<?> section, String imageResource,
            ArtworkPlaybackPolicy.Surface surface) throws ReflectiveOperationException {
            List<Constructor<?>> constructors = new ArrayList<>();
            Class<?> viewModel = null;
            for (Constructor<?> constructor : section.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                int vmIndex = viewModelIndex(types);
                if (vmIndex < 0) continue;
                if (viewModel != null && viewModel != types[vmIndex]) throw new IllegalArgumentException("section_vm_ambiguous");
                viewModel = types[vmIndex];
                constructor.setAccessible(true);
                constructors.add(constructor);
            }
            if (constructors.size() != 1) throw new IllegalArgumentException("section_constructor_ambiguous");
            Field field = ArtworkModelAccess.uniqueField(section, viewModel);
            Method dispose = section.getMethod("dispose");
            if (dispose.getReturnType() != void.class || Modifier.isStatic(dispose.getModifiers())) {
                throw new IllegalArgumentException("section_dispose_shape");
            }
            return new SurfaceTargets(section, field, List.copyOf(constructors), dispose, imageResource, surface);
    }

    /**
     * {@code MediaInfo} keeps several fields of the album-art object type. The toString label
     * is unique on both the reviewed and PYB110 17.000.002 binaries.
     */
    private static Field albumArtField(DexKitBridge dex, ClassLoader loader, Class<?> info)
            throws ReflectiveOperationException {
        List<Field> fields = new ArrayList<>();
        for (FieldData data : dex.findField(FindField.create().matcher(
                FieldMatcher.create()
                        .declaredClass(info.getName())
                        .addReadMethod(MethodMatcher.create().usingEqStrings(", albumArt="))))) {
            Field field = data.getFieldInstance(loader);
            if (field.getDeclaringClass() == info && !Modifier.isStatic(field.getModifiers())
                    && !field.getType().isPrimitive()) {
                fields.add(field);
            }
        }
        Field selected = ArtworkC17Roles.single(fields, field -> true, "album_art");
        selected.setAccessible(true);
        return selected;
    }

    /**
     * The album-art writer is found by its log line, not its obfuscated name. PYB110 17.000.002
     * moved the reviewed {@code e(Drawable, boolean)} body to {@code f} and reused {@code e}.
     */
    private static Method albumUpdate(DexKitBridge dex, ClassLoader loader, Class<?> control)
            throws ReflectiveOperationException {
        MethodDataList methods = dex.findMethod(FindMethod.create().matcher(
                MethodMatcher.create()
                        .declaredClass(control.getName())
                        .usingEqStrings(ALBUM_LOG)
                        .returnType(void.class)
                        .paramTypes(Drawable.class, boolean.class)));
        if (methods.size() != 1) {
            throw new NoSuchMethodException(methods.size() == 0
                    ? "c17_album_update_missing" : "c17_album_update_ambiguous");
        }
        Method method = methods.get(0).getMethodInstance(loader);
        if (method.getDeclaringClass() != control || !ArtworkC17Access.isAlbumUpdate(method)) {
            throw new NoSuchMethodException("c17_album_update_shape");
        }
        method.setAccessible(true);
        return method;
    }

    public static int viewModelIndex(Class<?>[] types) {
        if ((types.length == 3 || types.length == 4) && types[0] == Context.class
                && types[1] != Context.class && types[2] == ViewGroup.class) return 1;
        if (types.length == 4 && types[0] == Context.class && types[1] == Context.class
                && types[3] == ViewGroup.class) return 2;
        return -1;
    }

    private static Class<?> singleClass(DexKitBridge dex, ClassLoader loader, String... anchors)
            throws ReflectiveOperationException {
        ClassDataList classes = dex.findClass(FindClass.create().matcher(ClassMatcher.create().usingEqStrings(anchors)));
        if (classes.size() != 1) throw new IllegalArgumentException("model_anchor_ambiguous");
        return classes.get(0).getInstance(loader);
    }
}
