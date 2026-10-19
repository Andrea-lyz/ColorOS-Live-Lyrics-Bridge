package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Reviewed 17.000.002 roles, reached through DexKit anchors and checked against actual view ownership. */
public final class ArtworkC17Access {
    private static final String VIEW = "com.oplus.systemui.plugins.shared.template.component.media.view.";
    private final Field controller;
    private final Field current, next, animation, pending, pendingModel, switching, waitingLayout, immersive, renderer, backgroundHost;
    private final Field backgroundRunning, backgroundPending, blurEffect;
    private final Field lyricMode;
    private final Class<?> backgroundType;
    private final Method rendererView;
    final List<Method> changes;
    /** {@code dexkit} or {@code legacy}; which binding supplied the controller roles. */
    final String roleSource;
    /** Roles where DexKit and the reviewed letters disagree; empty when they match or one is missing. */
    final List<String> legacyMismatches;

    /**
     * @param dexRoles roles bound from log-line anchors, or {@code null} when that scan failed.
     *     The reviewed 17.000.002 letters are only a fallback: same-versionName builds move them.
     */
    @androidx.annotation.RequiresApi(31)
    ArtworkC17Access(Class<?> section, Class<?> control, Class<?> background, Method album,
            ArtworkC17Roles dexRoles) throws ReflectiveOperationException {
        ClassLoader loader = section.getClassLoader();
        Class<?> smart = loader.loadClass(VIEW + "SmartShapeableImageView");
        if (!smart.getSuperclass().getName().equals(
                "com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView")) {
            throw new NoSuchFieldException("c17_shape_parent");
        }
        backgroundType = background;
        if (!android.widget.FrameLayout.class.isAssignableFrom(background)) throw new NoSuchFieldException("c17_background_type");
        ArtworkC17Roles legacy = legacyRoles(control, background, smart);
        ArtworkC17Roles roles = dexRoles != null ? dexRoles : legacy;
        if (roles == null) throw new NoSuchFieldException("c17_roles_unresolved");
        roleSource = dexRoles != null ? "dexkit" : "legacy";
        legacyMismatches = dexRoles != null ? dexRoles.mismatches(legacy) : List.of();
        controller = ArtworkModelAccess.uniqueField(section, control);
        current = requireRole(roles.current(), control, smart);
        next = requireRole(roles.next(), control, smart);
        if (current.equals(next)) throw new NoSuchFieldException("c17_slot_same");
        pendingModel = requireRole(roles.pendingModel(), control, null);
        if (pendingModel.getType().isPrimitive()) throw new NoSuchFieldException("c17_pending_model");
        switching = requireRole(roles.switching(), control, boolean.class);
        waitingLayout = requireRole(roles.waitingLayout(), control, boolean.class);
        immersive = requireRole(roles.immersive(), control, boolean.class);
        lyricMode = requireRole(roles.lyricMode(), control, boolean.class);
        renderer = requireRole(roles.renderer(), control, null);
        if (!renderer.getType().isInterface()) throw new NoSuchFieldException("c17_renderer_interface");
        rendererView = ArtworkC17Roles.viewAccessor(renderer.getType());
        rendererView.setAccessible(true);
        backgroundRunning = requireRole(roles.backgroundRunning(), background, boolean.class);
        // Fields that are the only one of their exact type need no name; the reviewed letter
        // remains only for a build that adds a second field of the same type.
        animation = typedOrNamed(control, ValueAnimator.class, "t");
        pending = typedOrNamed(control, Drawable.class, "u");
        backgroundHost = typedOrNamed(control, ViewGroup.class, "h");
        backgroundPending = typedOrNamed(background, Bitmap.class, "g");
        Class<?> blur = loader.loadClass("com.oplus.systemui.plugins.shared.template.section.media.BackdropBlurView");
        blurEffect = typedOrNamed(blur, RenderEffect.class, "g");
        if (!isAlbumUpdate(album) || album.getDeclaringClass() != control) {
            throw new NoSuchMethodException("c17_album_update_shape");
        }
        album.setAccessible(true);
        Method bg = null;
        for (Method method : background.getDeclaredMethods()) {
            Class<?>[] args = method.getParameterTypes();
            if (!Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                    && args.length == 3 && args[0] == Bitmap.class && args[1] == boolean.class) {
                if (bg != null) throw new NoSuchMethodException("c17_background_update_ambiguous");
                bg = method;
            }
        }
        if (bg == null) throw new NoSuchMethodException("c17_background_update_missing");
        changes = List.of(album, bg, smart.getDeclaredMethod("setImageDrawable", Drawable.class),
                smart.getDeclaredMethod("setSmoothCorner", int.class));
    }

    Bound bind(Object section, ViewGroup root) throws ReflectiveOperationException {
        Object owner = controller.get(section);
        ImageView first = image(root, "img_large_album_art");
        ImageView second = image(root, "img_large_album_art_next");
        if (owner == null || first == null || second == null || first == second || first.getParent() != second.getParent()
                || !((current.get(owner) == first && next.get(owner) == second)
                    || (current.get(owner) == second && next.get(owner) == first))) throw new NoSuchFieldException("c17_slot_owner");
        return new Bound(owner, first, second);
    }

    /** Name-independent shape of the immersive album writer. Both {@code e} and {@code f} qualify. */
    static boolean isAlbumUpdate(Method method) {
        return method != null
                && !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == void.class
                && method.getParameterCount() == 2
                && method.getParameterTypes()[0] == Drawable.class
                && method.getParameterTypes()[1] == boolean.class;
    }

    /** The reviewed 17.000.002 letters, or {@code null} when this build does not carry them. */
    private static ArtworkC17Roles legacyRoles(Class<?> control, Class<?> background, Class<?> smart) {
        try {
            Field renderer = control.getDeclaredField("i");
            return new ArtworkC17Roles(
                    field(control, "r", smart),
                    field(control, "s", smart),
                    field(control, "j", control.getDeclaredField("k").getType()),
                    field(control, "n", boolean.class),
                    field(control, "o", boolean.class),
                    field(control, "c", boolean.class),
                    field(control, "p", boolean.class),
                    field(control, "i", renderer.getType()),
                    field(background, "f", boolean.class));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static Field requireRole(Field field, Class<?> owner, Class<?> type) throws NoSuchFieldException {
        if (field == null || field.getDeclaringClass() != owner || Modifier.isStatic(field.getModifiers())
                || (type != null && field.getType() != type)) {
            throw new NoSuchFieldException("c17_role_shape");
        }
        field.setAccessible(true);
        return field;
    }

    private static Field typedOrNamed(Class<?> owner, Class<?> type, String name) throws ReflectiveOperationException {
        try {
            return ArtworkModelAccess.uniqueField(owner, type);
        } catch (NoSuchFieldException ambiguous) {
            return field(owner, name, type);
        }
    }

    static Field field(Class<?> type, String name, Class<?> expected) throws NoSuchFieldException {
        Field value = type.getDeclaredField(name);
        if (value.getType() != expected || Modifier.isStatic(value.getModifiers())) throw new NoSuchFieldException("c17_field_shape");
        value.setAccessible(true);
        return value;
    }

    static View resource(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.oplus.systemui.plugins");
        return id == 0 ? null : root.findViewById(id);
    }

    private static ImageView image(View root, String name) {
        View view = resource(root, name);
        return view instanceof ImageView result ? result : null;
    }

    public final class Bound {
        private final WeakReference<Object> owner;
        private final WeakReference<ImageView> first, second;
        private WeakReference<View> lastBackground = new WeakReference<>(null);
        private WeakReference<Object> lastRenderer = new WeakReference<>(null);
        private WeakReference<ImageView> content = new WeakReference<>(null);
        private WeakReference<View> topMask = new WeakReference<>(null), bottomMask = new WeakReference<>(null);

        Bound(Object owner, ImageView first, ImageView second) {
            this.owner = new WeakReference<>(owner);
            this.first = new WeakReference<>(first);
            this.second = new WeakReference<>(second);
        }

        boolean owns(Object value) {
            return value != null && (value == owner.get() || value == first.get() || value == second.get()
                    || value == lastBackground.get());
        }

        /** p is set by the lyric UI mode flow (V0), and also controls the fullscreen blur. */
        boolean coverMode() {
            try {
                Object control = owner.get();
                return control != null && immersive.getBoolean(control) && !lyricMode.getBoolean(control);
            } catch (ReflectiveOperationException | RuntimeException error) { return false; }
        }

        /** Returns only the active renderer's content, never a whole-window ID search. */
        ImageView background() throws ReflectiveOperationException {
            Object control = owner.get();
            if (control == null) return null;
            Object active = renderer.get(control);
            if (active == null) return null;
            View cached = lastBackground.get();
            if (active == lastRenderer.get() && cached != null && content.get() != null
                    && cached.getParent() == backgroundHost.get(control)) return content.get();
            Object view = rendererView.invoke(active);
            if (!backgroundType.isInstance(view) || !(view instanceof ViewGroup root)
                    || root.getParent() != backgroundHost.get(control)) return null;
            lastBackground = new WeakReference<>(root);
            lastRenderer = new WeakReference<>(active);
            content = new WeakReference<>(image(root, "content"));
            topMask = new WeakReference<>(resource(root, "topMask"));
            bottomMask = new WeakReference<>(resource(root, "bottomMask"));
            return content.get();
        }

        ImageView foreground() throws ReflectiveOperationException {
            Object control = owner.get();
            if (control == null) return null;
            Object image = current.get(control);
            Object other = next.get(control);
            return ((image == first.get() && other == second.get()) || (image == second.get() && other == first.get()))
                    ? (ImageView) image : null;
        }

        boolean ready(ImageView image, boolean background) {
            try {
                Object control = owner.get();
                if (control == null || image.getDrawable() == null || !immersive.getBoolean(control) || switching.getBoolean(control)
                        || waitingLayout.getBoolean(control) || pendingModel.get(control) != null) return false;
                if (!background) {
                    ValueAnimator animator = (ValueAnimator) animation.get(control);
                    return ArtworkC17State.foregroundReady(image == foreground(),
                            animator != null && (animator.isStarted() || animator.isRunning()), pending.get(control) != null);
                }
                if (image != background()) return false;
                View root = lastBackground.get();
                if (root == null || !ArtworkC17State.backgroundReady(backgroundRunning.getBoolean(root),
                        backgroundPending.get(root) != null)) return false;
                return blurReady(topMask.get()) && blurReady(bottomMask.get());
            } catch (ReflectiveOperationException | RuntimeException error) { return false; }
        }

        private boolean blurReady(View mask) throws IllegalAccessException {
            return blurEffect.getDeclaringClass().isInstance(mask) && mask.getVisibility() == View.VISIBLE
                    && blurEffect.get(mask) != null;
        }
    }
}
