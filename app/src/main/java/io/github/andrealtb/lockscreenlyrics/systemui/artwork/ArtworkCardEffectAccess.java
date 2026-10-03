package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.animation.AnimatorSet;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Narrow C16-user shader profile. Missing or changed native structure rejects only card display. */
@androidx.annotation.RequiresApi(33)
public final class ArtworkCardEffectAccess {
    public final Class<?> imageType;
    public final Method bitmapSetter;
    public final Method angleSetter;
    private final Field bitmap;
    private final Field animator;
    private final Field spring;
    private final Field running;
    private final Field paint;
    private final Field shader;
    private final Method superDraw;

    public ArtworkCardEffectAccess(ClassLoader loader) throws ReflectiveOperationException {
        imageType = loader.loadClass("com.oplus.systemui.plugins.shared.template.component.media.view.RotatableImageView");
        Class<?> base = imageType.getSuperclass();
        bitmap = unique(imageType, Bitmap.class);
        animator = unique(imageType, AnimatorSet.class);
        // These exact source/Dex fields are the current local C16 sample, not a cross-version spring API.
        Class<?> springType = loader.loadClass("j1.h");
        spring = unique(imageType, springType);
        running = springType.getDeclaredField("g");
        if (running.getType() != boolean.class || Modifier.isStatic(running.getModifiers())) {
            throw new NoSuchFieldException("card_spring_contract");
        }
        running.setAccessible(true);
        paint = unique(base, Paint.class);
        shader = unique(base, RuntimeShader.class);
        superDraw = base.getMethod("getUseSuperDraw");
        if (superDraw.getReturnType() != boolean.class) throw new NoSuchMethodException("card_draw_contract");
        bitmapSetter = imageType.getMethod("setEffectBitmap", Bitmap.class);
        Method angle = null;
        for (Method method : base.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != void.class || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != float.class) continue;
            if (angle != null) throw new NoSuchMethodException("card_angle_ambiguous");
            angle = method;
        }
        if (angle == null) throw new NoSuchMethodException("card_angle_missing");
        angleSetter = angle;
    }

    public boolean settled(ImageView image) {
        try {
            if (image.getClass() != imageType || (boolean) superDraw.invoke(image) || bitmap.get(image) == null) return false;
            AnimatorSet animation = (AnimatorSet) animator.get(image);
            Object nativeSpring = spring.get(image);
            return (animation == null || (!animation.isStarted() && !animation.isRunning()))
                    && (nativeSpring == null || !running.getBoolean(nativeSpring));
        } catch (ReflectiveOperationException | RuntimeException error) { return false; }
    }

    public RuntimeShader mask(ImageView image) throws ReflectiveOperationException {
        if (!settled(image)) throw new ArtworkImmersiveMount.Unsupported("card_effect_active_or_unknown");
        Paint nativePaint = (Paint) paint.get(image);
        RuntimeShader nativeShader = (RuntimeShader) shader.get(image);
        if (nativePaint == null || nativeShader == null || nativePaint.getShader() != nativeShader) {
            throw new ArtworkImmersiveMount.Unsupported("card_shader_contract");
        }
        return nativeShader;
    }

    private static Field unique(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        Field selected = null;
        Field[] fields = owner.getDeclaredFields();
        if (fields.length > 64) throw new NoSuchFieldException("card_field_budget");
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != type) continue;
            if (selected != null) throw new NoSuchFieldException("card_field_ambiguous");
            selected = field;
        }
        if (selected == null) throw new NoSuchFieldException("card_field_missing");
        selected.setAccessible(true);
        return selected;
    }
}
