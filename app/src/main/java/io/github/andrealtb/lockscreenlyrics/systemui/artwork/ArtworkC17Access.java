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

    @androidx.annotation.RequiresApi(31)
    ArtworkC17Access(Class<?> section, Class<?> control, Class<?> background) throws ReflectiveOperationException {
        ClassLoader loader = section.getClassLoader();
        Class<?> smart = loader.loadClass(VIEW + "SmartShapeableImageView");
        if (!smart.getSuperclass().getName().equals(
                "com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView")) {
            throw new NoSuchFieldException("c17_shape_parent");
        }
        controller = ArtworkModelAccess.uniqueField(section, control);
        current = field(control, "r", smart);
        next = field(control, "s", smart);
        animation = field(control, "t", ValueAnimator.class);
        pending = field(control, "u", Drawable.class);
        pendingModel = field(control, "j", control.getDeclaredField("k").getType());
        switching = field(control, "n", boolean.class);
        waitingLayout = field(control, "o", boolean.class);
        immersive = field(control, "c", boolean.class);
        lyricMode = field(control, "p", boolean.class);
        backgroundHost = field(control, "h", ViewGroup.class);
        renderer = control.getDeclaredField("i");
        if (!renderer.getType().isInterface() || Modifier.isStatic(renderer.getModifiers())) {
            throw new NoSuchFieldException("c17_renderer_interface");
        }
        renderer.setAccessible(true);
        rendererView = renderer.getType().getMethod("d");
        if (rendererView.getReturnType() != View.class) throw new NoSuchMethodException("c17_renderer_view");
        backgroundType = background;
        if (!android.widget.FrameLayout.class.isAssignableFrom(background)) throw new NoSuchFieldException("c17_background_type");
        backgroundRunning = field(background, "f", boolean.class);
        backgroundPending = field(background, "g", Bitmap.class);
        Class<?> blur = loader.loadClass("com.oplus.systemui.plugins.shared.template.section.media.BackdropBlurView");
        blurEffect = field(blur, "g", RenderEffect.class);
        Method album = control.getDeclaredMethod("e", Drawable.class, boolean.class);
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
