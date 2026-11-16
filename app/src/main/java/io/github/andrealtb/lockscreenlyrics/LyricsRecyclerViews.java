package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;
import static io.github.andrealtb.lockscreenlyrics.LyricMetadataSupport.*;
import static io.github.andrealtb.lockscreenlyrics.LyricLogDescriptions.*;

import android.app.KeyguardManager;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONObject;
import io.github.andrealtb.lockscreenlyrics.render.LineTimedRevealPolicy;
import io.github.andrealtb.lockscreenlyrics.render.CachedDrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.CharLiftGeometry;
import io.github.andrealtb.lockscreenlyrics.render.DrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.LyricDrawLine;
import io.github.andrealtb.lockscreenlyrics.render.LyricDrawLayoutEngine;
import io.github.andrealtb.lockscreenlyrics.render.LyricTimingMode;
import io.github.andrealtb.lockscreenlyrics.render.LyricsRecyclerGeometry;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricFrameResolver;
import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricDataset;
import io.github.andrealtb.lockscreenlyrics.render.LyricRenderTransactions;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderConstants;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import io.github.andrealtb.lockscreenlyrics.bootstrap.SystemUiRuntimeBootstrap;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.DynamicArtworkRuntime;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkTrace;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugApplyPolicy;
import io.github.andrealtb.lockscreenlyrics.SystemUiPlaybackController.PlaybackControllerBinding;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugConfig;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgePerformanceSampler;
import io.github.andrealtb.lockscreenlyrics.diagnostics.ArtworkDiagnostics;
import io.github.andrealtb.lockscreenlyrics.diagnostics.SensitiveFieldRedactor;
import io.github.andrealtb.lockscreenlyrics.diagnostics.StructuredBridgeLog;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoCoverPolicy;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoMediaIdentityPolicy;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoPluginMediaModelReader;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoSameTrackLyricRetention;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoSystemUiRuntime;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.LyricsRecyclerFieldAccessor;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.LyricsRecyclerPolicy;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.SystemUiLoadLyricPolicy;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Stateless helpers for the SystemUI LyricsRecyclerView and its lyric rows (F-1a, plan §15.8.19):
 * recycler identification, adapter / child-position reflection, current-index reads, geometry
 * capture, row scale pivots, slot heights, visibility tests and View visual effects. Moved
 * verbatim from {@code LockscreenLyricsModule}; every member is static and owns no runtime state
 * beyond its reflection caches.
 */
final class LyricsRecyclerViews {
    private LyricsRecyclerViews() {
    }

    private static final WeakHashMap<Class<?>, WeakReference<Method[]>>
            VIEW_BLUR_METHOD_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, WeakReference<Method>>
            RECYCLER_POSITION_METHOD_CACHE = new WeakHashMap<>();
    /** getAdapter lives on RecyclerView; a per-call declared-method walk throws once per subclass level. */
    private static final WeakHashMap<Class<?>, WeakReference<Method>>
            RECYCLER_ADAPTER_METHOD_CACHE = new WeakHashMap<>();
    static final float OFFICIAL_LYRIC_BLUR_ZERO_THRESHOLD_PX =
            LyricTimingTuningConstants.OfficialLyric.BLUR_ZERO_THRESHOLD_PX;
    static final Object RECYCLER_POSITION_METHOD_CACHE_LOCK = new Object();
    static final WeakHashMap<Class<?>, Boolean> RECYCLER_POSITION_METHOD_MISSING =
            new WeakHashMap<>();
    static final WeakHashMap<Class<?>, Boolean> LYRICS_RECYCLER_VIEW_CLASS_MATCH_CACHE =
            new WeakHashMap<>();
    static final LyricsRecyclerFieldAccessor LYRICS_RECYCLER_FIELD_ACCESSOR =
            new LyricsRecyclerFieldAccessor();
    static final ThreadLocal<Rect> VIEW_VISIBLE_RECT =
            ThreadLocal.withInitial(Rect::new);

    static void stopLyricsRecyclerScroll(View recycler) {
        tryInvokeNoArgByName(recycler, "stopScroll");
    }

    static int findBoundLyricsRecyclerItemHeight(View recyclerView, int targetIndex) {
        if (!(recyclerView instanceof ViewGroup) || targetIndex < 0) {
            return 0;
        }
        ViewGroup group = (ViewGroup) recyclerView;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (readRecyclerChildPosition(recyclerView, child) == targetIndex) {
                return child.getHeight();
            }
        }
        return 0;
    }

    static boolean hasBoundLyricsRecyclerChildren(View recycler) {
        return recycler instanceof ViewGroup && ((ViewGroup) recycler).getChildCount() > 0;
    }

    static boolean isLyricsRecyclerView(View view) {
        if (view == null) {
            return false;
        }
        Class<?> viewClass = view.getClass();
        synchronized (LYRICS_RECYCLER_VIEW_CLASS_MATCH_CACHE) {
            Boolean cached = LYRICS_RECYCLER_VIEW_CLASS_MATCH_CACHE.get(viewClass);
            if (cached != null) {
                return cached;
            }
            boolean matches = hasLyricsRecyclerViewClassName(viewClass);
            LYRICS_RECYCLER_VIEW_CLASS_MATCH_CACHE.put(viewClass, matches);
            return matches;
        }
    }

    static boolean hasLyricsRecyclerViewClassName(Class<?> viewClass) {
        Class<?> current = viewClass;
        while (current != null) {
            if (LYRICS_RECYCLER_VIEW_CLASS.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    static int readLyricsRecyclerAdapterItemCount(View recycler) {
        Object adapter = invokeNoArgByName(recycler, "getAdapter");
        Object itemCount = invokeNoArgByName(adapter, "getItemCount");
        return itemCount instanceof Number ? ((Number) itemCount).intValue() : -1;
    }

    static boolean isLyricsRecyclerCurrentLyricMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if ("setCurrentLyric".equals(method.getName())
                && parameterTypes.length > 0
                && parameterTypes[0] == int.class) {
            return true;
        }
        return isOfficialTimedCurrentLyricMethod(method);
    }

    static boolean isLyricsRecyclerNotifyCrashGuardMethod(Method method) {
        return method != null
                && "e".equals(method.getName())
                && method.getReturnType() == void.class;
    }

    static boolean isLyricsRecyclerScalePivotMethod(Method method) {
        // Method names drift across same-version SystemUIPlugin binaries (verified m/k plus the
        // current u/v/C device variant, and ColorOS 17's static d). The shapes are narrow and
        // safe: they can only operate on the lyric row, and restoring the configured pivot after
        // they return is idempotent even for style/binding helpers.
        return OfficialLyricScalePivotMethodPolicy.textViewArgumentIndex(method)
                != OfficialLyricScalePivotMethodPolicy.NOT_A_PIVOT_METHOD;
    }

    static Class<?> findRecyclerAdapterBaseClass(Object adapter) {
        return LyricRecyclerAdapterContract.findBaseClass(adapter);
    }

    static boolean isOfficialVisualFocusedLine(
            int lineIndex,
            int focusAnchorIndex,
            int scaleActiveIndex) {
        if (lineIndex < 0) {
            return false;
        }
        if (lineIndex == scaleActiveIndex) {
            return true;
        }
        return scaleActiveIndex < 0 && lineIndex == focusAnchorIndex;
    }

    static WordLine resolveFocusAnchorLine(
            WordLyricModel model, WordLine activeLine, long position) {
        WordLine firstDisplayLine = firstLineBeforeDisplayStart(model, position);
        if (firstDisplayLine != null) {
            return firstDisplayLine;
        }
        if (activeLine != null || model == null) {
            return activeLine;
        }
        return model.lineAt(model.displayIndexAt(position));
    }

    static int findLyricsRecyclerAdapterPosition(TextView textView) {
        LyricsRecyclerMatch match = findLyricsRecyclerMatch(textView);
        return match == null
                ? -1
                : readRecyclerChildPosition(match.recycler(), match.itemView());
    }

    static int readRecyclerChildPosition(View recyclerView, View child) {
        if (recyclerView == null || child == null) {
            return -1;
        }
        Method method = resolveRecyclerChildPositionMethod(recyclerView.getClass());
        if (method == null) {
            return -1;
        }
        try {
            Object result = method.invoke(recyclerView, child);
            return result instanceof Number ? ((Number) result).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    static Method resolveRecyclerChildPositionMethod(Class<?> recyclerClass) {
        synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
            WeakReference<Method> reference = RECYCLER_POSITION_METHOD_CACHE.get(recyclerClass);
            Method cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            if (RECYCLER_POSITION_METHOD_MISSING.containsKey(recyclerClass)) {
                return null;
            }
        }
        Method resolved = findRecyclerChildPositionMethod(recyclerClass);
        synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
            if (resolved == null) {
                RECYCLER_POSITION_METHOD_MISSING.put(recyclerClass, Boolean.TRUE);
            } else {
                RECYCLER_POSITION_METHOD_CACHE.put(
                        recyclerClass,
                        new WeakReference<>(resolved));
            }
        }
        return resolved;
    }

    static Method findRecyclerChildPositionMethod(Class<?> recyclerClass) {
        Method resolved = null;
        String[] methodNames = {
                "getChildBindingAdapterPosition",
                "getChildAdapterPosition",
                "getChildLayoutPosition"
        };
        for (String methodName : methodNames) {
            try {
                resolved = findMethod(recyclerClass, methodName, View.class);
                resolved.setAccessible(true);
                break;
            } catch (Throwable ignored) {
                // Some plugin RecyclerView builds expose only one of these helpers.
            }
        }
        return resolved;
    }

    static int findValidLyricsRecyclerAdapterPosition(TextView textView, WordLyricModel model) {
        int position = findLyricsRecyclerAdapterPosition(textView);
        if (model == null
                || position < 0
                || (position >= model.lines.size()
                && position >= model.officialLines.size())) {
            return -1;
        }
        return position;
    }

    static View findContainingLyricsRecyclerView(TextView textView) {
        LyricsRecyclerMatch match = findLyricsRecyclerMatch(textView);
        return match == null ? null : match.recycler();
    }

    static View findLyricsRecyclerItemView(TextView textView) {
        LyricsRecyclerMatch match = findLyricsRecyclerMatch(textView);
        if (match != null) {
            return match.itemView();
        }
        if (textView == null) {
            return null;
        }
        return textView;
    }

    static LyricsRecyclerMatch findLyricsRecyclerMatch(TextView textView) {
        if (textView == null) {
            return null;
        }
        LyricsRecyclerMatch cached = LYRICS_RECYCLER_MATCH_CACHE.get(textView);
        if (cached == NO_LYRICS_RECYCLER_MATCH) {
            return null;
        }
        if (cached != null && cached.isUsable(textView)) {
            return cached;
        }
        if (cached != null) {
            LYRICS_RECYCLER_MATCH_CACHE.remove(textView);
        }
        View child = textView;
        Object parent = child.getParent();
        for (int depth = 0; depth < 12 && parent instanceof View; depth++) {
            View parentView = (View) parent;
            if (isLyricsRecyclerView(parentView)) {
                LyricsRecyclerMatch match = new LyricsRecyclerMatch(parentView, child);
                LYRICS_RECYCLER_MATCH_CACHE.put(textView, match);
                return match;
            }
            child = parentView;
            parent = child.getParent();
        }
        if (textView.isAttachedToWindow()) {
            LYRICS_RECYCLER_MATCH_CACHE.put(textView, NO_LYRICS_RECYCLER_MATCH);
        }
        return null;
    }

    /** ColorOS 17 displayed official row, or null before it is resolved for this recycler. */
    static Integer readImmersiveCurrentIndexField(Object recycler) {
        Field field = immersiveCurrentIndexField;
        if (field == null
                || recycler == null
                || !field.getDeclaringClass().isInstance(recycler)) {
            return null;
        }
        try {
            return field.getInt(recycler);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static int synchronizeBoundLyricItemScales(
            View recyclerView,
            float inactiveScale) {
        if (!(recyclerView instanceof ViewGroup)) {
            return 0;
        }
        ViewGroup group = (ViewGroup) recyclerView;
        int officialIndex = readLyricsRecyclerCurrentIndex(recyclerView);
        int changed = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            TextView lyricTextView = findFirstTextView(child, new int[]{0});
            View scaleView = lyricTextView == null ? child : lyricTextView;
            int adapterPosition = readRecyclerChildPosition(recyclerView, child);
            float targetScale = adapterPosition >= 0 && adapterPosition == officialIndex
                    ? 1f
                    : inactiveScale;
            if (Math.abs(scaleView.getScaleX() - targetScale) > 0.001f) {
                scaleView.setScaleX(targetScale);
                changed++;
            }
            if (Math.abs(scaleView.getScaleY() - targetScale) > 0.001f) {
                scaleView.setScaleY(targetScale);
            }
        }
        return changed;
    }

    static void synchronizeBoundLyricItemPivots(View recyclerView) {
        if (!(recyclerView instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) recyclerView;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView textView = findFirstTextView(group.getChildAt(i), new int[]{0});
            if (textView != null) {
                applyOfficialLyricScalePivot(textView);
            }
        }
    }

    static TextView findFirstTextView(View view, int[] visited) {
        if (view == null || visited[0]++ > 24) {
            return null;
        }
        if (view instanceof TextView) {
            return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView match = findFirstTextView(group.getChildAt(i), visited);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    static void applyOfficialLyricScalePivot(TextView textView) {
        if (textView == null) {
            return;
        }
        int resolvedWidth = LyricUiLayoutPolicy.resolvedViewDimension(
                textView.getWidth(),
                textView.getMeasuredWidth());
        int resolvedHeight = LyricUiLayoutPolicy.resolvedViewDimension(
                textView.getHeight(),
                textView.getMeasuredHeight());
        if (resolvedWidth <= 0) {
            // The vendor method already selected its safest available pivot. Do not replace it
            // with paddingLeft merely because this hook ran before the first layout.
            return;
        }
        float contentWidth = Math.max(
                0f,
                resolvedWidth - textView.getPaddingLeft() - textView.getPaddingRight());
        float pivotX = LyricUiLayoutPolicy.horizontalScalePivot(
                runtimeLyricUiConfig.alignment,
                textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL,
                textView.getPaddingLeft(),
                contentWidth);
        textView.setPivotX(pivotX);
        if (runtimeLyricUiConfig.alignment != LyricUiConfig.ALIGN_CENTER) {
            textView.setPivotY(0f);
            return;
        }
        if (resolvedHeight <= 0) {
            return;
        }
        float contentHeight = Math.max(
                0f,
                resolvedHeight - textView.getPaddingTop() - textView.getPaddingBottom());
        textView.setPivotY(textView.getPaddingTop() + contentHeight / 2f);
    }

    static int synchronizeBoundLyricItemSpacing(
            View recyclerView, int previousSpacing, int requestedSpacing) {
        if (!(recyclerView instanceof ViewGroup) || previousSpacing == requestedSpacing) {
            return 0;
        }
        ViewGroup recyclerGroup = (ViewGroup) recyclerView;
        int synchronizedChildren = 0;
        for (int i = 0; i < recyclerGroup.getChildCount(); i++) {
            View child = recyclerGroup.getChildAt(i);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (!(params instanceof ViewGroup.MarginLayoutParams)) {
                continue;
            }
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
            if (marginParams.bottomMargin != previousSpacing) {
                continue;
            }
            marginParams.bottomMargin = requestedSpacing;
            child.setLayoutParams(marginParams);
            synchronizedChildren++;
        }
        return synchronizedChildren;
    }

    static void prepareOfficialLyricTextView(TextView textView) {
        if (textView == null) {
            return;
        }
        // This method is also reached from the per-frame custom draw path. TextView's
        // height setters request a new layout even when the requested value is unchanged,
        // so keep this preparation idempotent to avoid remeasuring the whole shade on
        // every lyric frame.
        if (textView.getIncludeFontPadding()) {
            textView.setIncludeFontPadding(false);
        }
        if (textView.getMinHeight() != 0) {
            textView.setMinHeight(0);
        }
        if (textView.getMinimumHeight() != 0) {
            textView.setMinimumHeight(0);
        }
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null
                && itemView != textView
                && itemView.getMinimumHeight() != 0) {
            itemView.setMinimumHeight(0);
        }
    }

    static void clearFocusedOfficialLyricViewEffects(TextView textView) {
        applyFocusedOfficialLyricViewEffects(textView, false, false);
    }

    static void applyFocusedOfficialLyricViewEffects(
            TextView textView,
            boolean inactiveBlur) {
        applyFocusedOfficialLyricViewEffects(textView, inactiveBlur, inactiveBlur);
    }

    static void applyFocusedOfficialLyricViewEffects(
            TextView textView,
            boolean inactiveBlur,
            boolean keepZeroBlurEffect) {
        applyFocusedOfficialLyricViewEffects(
                textView,
                inactiveBlur ? runtimeLyricUiConfig.blurRadiusTenthsPx / 10f : 0f,
                keepZeroBlurEffect);
    }

    static void applyFocusedOfficialLyricViewEffects(
            TextView textView,
            float inactiveBlurRadius,
            boolean keepZeroBlurEffect) {
        if (textView == null) {
            return;
        }
        applyViewVisualEffects(textView, inactiveBlurRadius, keepZeroBlurEffect);
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null && itemView != textView) {
            applyViewVisualEffects(itemView, false, false);
        }
    }

    static void clearViewVisualEffects(View view) {
        applyViewVisualEffects(view, false, false);
    }

    static void applyViewVisualEffects(View view, boolean inactiveBlur) {
        applyViewVisualEffects(view, inactiveBlur, inactiveBlur);
    }

    static void applyViewVisualEffects(
            View view,
            boolean inactiveBlur,
            boolean keepZeroBlurEffect) {
        applyViewVisualEffects(
                view,
                inactiveBlur ? runtimeLyricUiConfig.blurRadiusTenthsPx / 10f : 0f,
                keepZeroBlurEffect);
    }

    static void applyViewVisualEffects(
            View view,
            float inactiveBlurRadius,
            boolean keepZeroBlurEffect) {
        if (view == null) {
            return;
        }
        float blurRadius = Math.max(0f, inactiveBlurRadius);
        if (blurRadius < OFFICIAL_LYRIC_BLUR_ZERO_THRESHOLD_PX) {
            blurRadius = 0f;
        }
        // Do not reset alpha/scale/layer type from onDraw. Those physical View properties belong
        // to SystemUI's RecyclerView/page transaction; overwriting them each frame caused the
        // official row animator and the custom renderer to pull the same item in opposite ways.
        boolean updateBlur;
        int effectMode = blurRadius > 0f
                ? 1000 + Math.max(1, Math.round(blurRadius * 10f))
                : keepZeroBlurEffect ? 1 : 0;
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            Integer previousEffectMode = VIEW_LYRIC_RENDER_EFFECT_MODE.get(view);
            updateBlur = previousEffectMode == null
                    || previousEffectMode != effectMode
                    || !VIEW_BLUR_DISABLED.containsKey(view);
            if (effectMode != 0) {
                VIEW_LYRIC_RENDER_EFFECT_MODE.put(view, effectMode);
            } else {
                VIEW_LYRIC_RENDER_EFFECT_MODE.remove(view);
            }
            if (updateBlur) {
                VIEW_BLUR_DISABLED.put(view, Boolean.TRUE);
            }
        }
        if (!updateBlur) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect effect = null;
            if (effectMode > 1) {
                float radius = (effectMode - 1000) / 10f;
                effect = RenderEffect.createBlurEffect(
                        radius,
                        radius,
                        Shader.TileMode.CLAMP);
            } else if (effectMode == 1) {
                effect = RenderEffect.createBlurEffect(0f, 0f, Shader.TileMode.CLAMP);
            }
            view.setRenderEffect(effect);
        }
        if (effectMode == 0) {
            for (Method method : resolveViewBlurMethods(view.getClass())) {
                try {
                    method.invoke(view, false);
                } catch (Throwable ignored) {
                    // The standard visual properties above are sufficient when a vendor
                    // implementation rejects its optional blur setter.
                }
            }
        }
    }

    static Method[] resolveViewBlurMethods(Class<?> viewClass) {
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            WeakReference<Method[]> reference = VIEW_BLUR_METHOD_CACHE.get(viewClass);
            Method[] cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
        }
        Method[] resolved = findViewBlurMethods(viewClass);
        synchronized (VIEW_VISUAL_EFFECT_CACHE_LOCK) {
            VIEW_BLUR_METHOD_CACHE.put(viewClass, new WeakReference<>(resolved));
        }
        return resolved;
    }

    static Method[] findViewBlurMethods(Class<?> viewClass) {
        ArrayList<Method> resolved = new ArrayList<>(2);
        boolean foundViewBlur = false;
        boolean foundBlur = false;
        Class<?> current = viewClass;
        while (current != null && (!foundViewBlur || !foundBlur)) {
            for (Method method : current.getDeclaredMethods()) {
                String name = method.getName();
                if ((foundViewBlur || !"setViewBlur".equals(name))
                        && (foundBlur || !"setBlur".equals(name))) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1
                        || (parameterTypes[0] != boolean.class
                        && parameterTypes[0] != Boolean.class)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    resolved.add(method);
                    if ("setViewBlur".equals(name)) {
                        foundViewBlur = true;
                    } else {
                        foundBlur = true;
                    }
                } catch (Throwable ignored) {
                    // Keep looking in the superclass.
                }
            }
            current = current.getSuperclass();
        }
        return resolved.toArray(new Method[0]);
    }

    static void setOfficialLyricSlotHeight(TextView textView, int height) {
        int safeHeight = Math.max(1, height);
        setViewHeight(textView, safeHeight);
        View itemView = findLyricsRecyclerItemView(textView);
        if (itemView != null && itemView != textView) {
            if (itemView.getMinimumHeight() != 0) {
                itemView.setMinimumHeight(0);
            }
            setViewHeight(itemView, safeHeight);
        }
    }

    static void setViewHeight(View view, int height) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height == height) {
            return;
        }
        int oldHeight = params.height;
        boolean diagnosticsEnabled = isLyricLayoutDiagnosticsEnabled();
        String before = diagnosticsEnabled ? describeViewForLog(view) : "";
        params.height = height;
        view.setLayoutParams(params);
        if (diagnosticsEnabled) {
            StructuredBridgeLog.info(
                    BridgeDebugArea.AOD,
                    BridgeEvents.OFFICIAL_LAYOUT_HEIGHT_CHANGED,
                    "Official lyric layout height changed"
                    + ", oldLayoutHeight=" + oldHeight
                    + ", newLayoutHeight=" + height
                    + ", before=" + before
                    + ", after=" + describeViewForLog(view));
        }
    }

    static int countEffectivelyVisibleLyricCandidates(
            ArrayList<TextView> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int visible = 0;
        for (TextView candidate : candidates) {
            if (isEffectivelyVisible(candidate)) {
                visible++;
            }
        }
        return visible;
    }

    static boolean isEffectivelyVisible(View view) {
        if (view == null
                || !view.isAttachedToWindow()
                || view.getVisibility() != View.VISIBLE
                || view.getAlpha() <= 0.01f
                || view.getWidth() <= 0
                || view.getHeight() <= 0) {
            return false;
        }

        Object parent = view.getParent();
        for (int depth = 0; depth < 16 && parent instanceof View; depth++) {
            View parentView = (View) parent;
            if (parentView.getVisibility() != View.VISIBLE
                    || parentView.getAlpha() <= 0.01f
                    || parentView.getWidth() <= 0
                    || parentView.getHeight() <= 0) {
                return false;
            }
            parent = parentView.getParent();
        }
        Rect visibleRect = VIEW_VISIBLE_RECT.get();
        visibleRect.setEmpty();
        return view.getGlobalVisibleRect(visibleRect) && !visibleRect.isEmpty();
    }

    static boolean isInLyricsRecyclerView(TextView textView) {
        return findLyricsRecyclerMatch(textView) != null;
    }

    static Object readRecyclerAdapter(View recycler) {
        if (recycler == null) {
            return null;
        }
        Class<?> recyclerClass = recycler.getClass();
        Method method;
        synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
            WeakReference<Method> reference = RECYCLER_ADAPTER_METHOD_CACHE.get(recyclerClass);
            method = reference == null ? null : reference.get();
        }
        if (method == null) {
            for (Class<?> current = recyclerClass; current != null && method == null;
                    current = current.getSuperclass()) {
                try {
                    method = current.getDeclaredMethod("getAdapter");
                    method.setAccessible(true);
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
            if (method == null) {
                return null;
            }
            synchronized (RECYCLER_POSITION_METHOD_CACHE_LOCK) {
                RECYCLER_ADAPTER_METHOD_CACHE.put(recyclerClass, new WeakReference<>(method));
            }
        }
        try {
            return method.invoke(recycler);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static int readLyricsRecyclerCurrentIndex(Object recycler) {
        int fieldIndex = LYRICS_RECYCLER_FIELD_ACCESSOR.readCurrentIndex(recycler, Integer.MIN_VALUE);
        if (fieldIndex != Integer.MIN_VALUE) {
            return fieldIndex;
        }
        if (recycler == null) {
            return -1;
        }
        Integer immersiveIndex = readImmersiveCurrentIndexField(recycler);
        if (immersiveIndex != null) {
            return immersiveIndex;
        }
        synchronized (IMMERSIVE_ENTRY_INDEXES) {
            ImmersiveEntryIndex entry = IMMERSIVE_ENTRY_INDEXES.get(recycler);
            return entry == null ? -1 : entry.index;
        }
    }

    static void invalidateLyricsRecyclerDescendants(View root) {
        invalidateLyricsRecyclerDescendants(root, new int[]{0});
    }

    static void invalidateLyricsRecyclerDescendants(View view, int[] visited) {
        if (view == null || visited[0]++ > 320) {
            return;
        }
        if (view instanceof TextView) {
            view.postInvalidateOnAnimation();
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            invalidateLyricsRecyclerDescendants(group.getChildAt(i), visited);
        }
    }

    static LyricsRecyclerGeometry captureLyricsRecyclerGeometry(
            View recycler, int targetIndex) {
        if (!(recycler instanceof ViewGroup)) {
            return LyricsRecyclerGeometry.EMPTY;
        }
        ViewGroup group = (ViewGroup) recycler;
        int childCount = group.getChildCount();
        int firstVisiblePosition = Integer.MAX_VALUE;
        int lastVisiblePosition = -1;
        int firstVisibleTop = 0;
        int targetCenter = Integer.MIN_VALUE;
        for (int i = 0; i < childCount; i++) {
            View child = group.getChildAt(i);
            int position = readRecyclerChildPosition(recycler, child);
            if (position >= 0 && position < firstVisiblePosition) {
                firstVisiblePosition = position;
                firstVisibleTop = child.getTop();
            }
            if (position > lastVisiblePosition) {
                lastVisiblePosition = position;
            }
            if (position == targetIndex) {
                targetCenter = child.getTop() + child.getHeight() / 2;
            }
        }
        if (firstVisiblePosition == Integer.MAX_VALUE) {
            firstVisiblePosition = -1;
        }
        return new LyricsRecyclerGeometry(
                firstVisiblePosition,
                lastVisiblePosition,
                firstVisibleTop,
                targetCenter,
                childCount);
    }
}
