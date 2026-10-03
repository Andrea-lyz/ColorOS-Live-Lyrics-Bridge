package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.ViewGroup;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Independent artwork capabilities. Shape/transition readiness is not implied by model resolution. */
public final class ArtworkCompatibilityResolver {
    public record SurfaceTargets(Class<?> sectionClass, Field sectionViewModel,
            List<Constructor<?>> constructors, Method dispose, String imageResource,
            ArtworkPlaybackPolicy.Surface surface) {}
    public record Targets(ArtworkModelAccess model, SurfaceTargets card, SurfaceTargets immersive) {}

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
            ArtworkModelAccess access = new ArtworkModelAccess(card, model, identity, immersiveCard);
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
            return new Targets(access, ordinary, immersive);
        }
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
