package io.github.andrealtb.lockscreenlyrics;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderConstants;
import io.github.libxposed.api.XposedInterface;

/**
 * Softens the easing of the vendor lyric line change.
 *
 * <p>The vendor moves a line change with two curves that both brake hard: the row scale and
 * colour animators use (0.3, 0, 0.1, 1), which covers most of the distance in the first third
 * and then crawls, and the scroll uses the platform {@link DecelerateInterpolator}. Only the
 * curves are replaced; durations, targets, cancellation and ordering stay with the vendor.</p>
 *
 * <p>Verified on SystemUIPlugin 16.001.002 (scale / colour animators built by recycler helpers
 * returning {@link ValueAnimator}) and 17.000.002 (built inside the adapter's payload bind and
 * kept on the ViewHolder as an {@link AnimatorSet}); both scroll with an androidx
 * {@code LinearSmoothScroller} subclass nested in the recycler. Every step is optional: a shape
 * that does not match leaves the vendor easing in place.</p>
 */
final class LineChangeEasing {
    static final TimeInterpolator SOFT = new PathInterpolator(
            WordLyricRenderConstants.LINE_CHANGE_EASE_X1,
            WordLyricRenderConstants.LINE_CHANGE_EASE_Y1,
            WordLyricRenderConstants.LINE_CHANGE_EASE_X2,
            WordLyricRenderConstants.LINE_CHANGE_EASE_Y2);

    private static final String LINEAR_SMOOTH_SCROLLER =
            "androidx.recyclerview.widget.LinearSmoothScroller";
    private static final String SCROLL_INTERPOLATOR_FIELD = "mDecelerateInterpolator";
    private static final Field[] NO_FIELDS = new Field[0];

    private static final Map<Class<?>, Field[]> HOLDER_ANIMATOR_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> LYRIC_SCROLLERS = new ConcurrentHashMap<>();
    private static volatile Field scrollInterpolatorField;
    private static volatile Class<?> lyricsRecyclerClass;

    private LineChangeEasing() {
    }

    /** The scroll interpolator slot is typed as DecelerateInterpolator, so subclass it. */
    static final class SoftDecelerate extends DecelerateInterpolator {
        @Override
        public float getInterpolation(float input) {
            return SOFT.getInterpolation(input);
        }
    }

    private static final SoftDecelerate SOFT_SCROLL = new SoftDecelerate();

    /**
     * Hooks {@code LinearSmoothScroller#onTargetFound} from the plugin ClassLoader, which hands
     * its interpolator to the scroll. Returns the hook handle, or null when the shape is absent.
     */
    static XposedInterface.HookHandle installScrollHook(
            XposedInterface xposed, Class<?> recyclerClass) {
        if (xposed == null || recyclerClass == null) {
            return null;
        }
        try {
            Class<?> scroller = Class.forName(
                    LINEAR_SMOOTH_SCROLLER, false, recyclerClass.getClassLoader());
            Field field = scroller.getDeclaredField(SCROLL_INTERPOLATOR_FIELD);
            if (field.getType() != DecelerateInterpolator.class
                    || Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            Method onTargetFound = null;
            for (Method method : scroller.getDeclaredMethods()) {
                if ("onTargetFound".equals(method.getName())
                        && method.getParameterTypes().length == 3) {
                    onTargetFound = method;
                }
            }
            if (onTargetFound == null) {
                return null;
            }
            field.setAccessible(true);
            scrollInterpolatorField = field;
            lyricsRecyclerClass = recyclerClass;
            return xposed.hook(onTargetFound)
                    .setId("oplus-word-lyrics-recycler-scroll-easing")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        softenScroll(chain.getThisObject());
                        return chain.proceed();
                    });
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void softenScroll(Object scroller) {
        Field field = scrollInterpolatorField;
        if (scroller == null || field == null || !isLyricScroller(scroller.getClass())) {
            return;
        }
        try {
            if (!(field.get(scroller) instanceof SoftDecelerate)) {
                field.set(scroller, SOFT_SCROLL);
            }
        } catch (Throwable ignored) {
            // The vendor scroll easing remains.
        }
    }

    /** Only the lyric recycler's own scroller: it carries a reference to its recycler. */
    private static boolean isLyricScroller(Class<?> type) {
        Class<?> recycler = lyricsRecyclerClass;
        if (recycler == null) {
            return false;
        }
        Boolean cached = LYRIC_SCROLLERS.get(type);
        if (cached != null) {
            return cached;
        }
        boolean matches = false;
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == recycler) {
                matches = true;
                break;
            }
        }
        LYRIC_SCROLLERS.put(type, matches);
        return matches;
    }

    /** ColorOS 16: a recycler helper just built one line-change animator. */
    static Object softenAnimator(Object hookResult) {
        if (hookResult instanceof ValueAnimator) {
            setInterpolator((ValueAnimator) hookResult);
        }
        return hookResult;
    }

    /**
     * ColorOS 17: the adapter's payload bind just built and started the row's line-change
     * animators and kept them on the holder. Starting does not advance them before the next
     * frame, so replacing the curve now shapes the whole animation.
     */
    static Object softenHolderAnimators(Object hookResult, Object holder) {
        if (holder == null) {
            return hookResult;
        }
        for (Field field : holderAnimatorFields(holder.getClass())) {
            try {
                Object value = field.get(holder);
                if (value instanceof AnimatorSet) {
                    for (Animator child : ((AnimatorSet) value).getChildAnimations()) {
                        if (child instanceof ValueAnimator) {
                            setInterpolator((ValueAnimator) child);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // The vendor easing remains.
            }
        }
        return hookResult;
    }

    private static Field[] holderAnimatorFields(Class<?> holderClass) {
        Field[] cached = HOLDER_ANIMATOR_FIELDS.get(holderClass);
        if (cached != null) {
            return cached;
        }
        ArrayList<Field> fields = new ArrayList<>();
        for (Field field : holderClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && AnimatorSet.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    fields.add(field);
                } catch (Throwable ignored) {
                    // Skip a field the runtime refuses to open.
                }
            }
        }
        Field[] resolved = fields.isEmpty() ? NO_FIELDS : fields.toArray(new Field[0]);
        HOLDER_ANIMATOR_FIELDS.put(holderClass, resolved);
        return resolved;
    }

    private static void setInterpolator(ValueAnimator animator) {
        try {
            if (animator.getInterpolator() != SOFT) {
                animator.setInterpolator(SOFT);
            }
        } catch (Throwable ignored) {
            // The vendor easing remains.
        }
    }
}
