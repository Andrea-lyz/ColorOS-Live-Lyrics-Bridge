package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;
import static io.github.andrealtb.lockscreenlyrics.LyricLogDescriptions.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Translation action view tracking (E-4a, plan §15.3): which media-card action views are the
 * translation toggle (content description or icon alpha fingerprint), the VISIBLE->INVISIBLE
 * redirect bookkeeping, the forced visibility and the on/off presentation of the tracked views.
 * Moved verbatim from {@code LockscreenLyricsModule}; the only body edit is that the current word
 * lyric model is read through {@link Host}.
 *
 * <p>The View hooks that feed this tracker, the lyric-mode gate and the recycler-anchored scan
 * stay in the entry class until E-6/E-7 move the recycler registry and the global View hooks.
 */
final class TranslationActionViewTracker {
    /** What the tracker reads from, or asks of, the rest of the SystemUI runtime. */
    interface Host {
        boolean shouldManageTranslationActionViewVisibility();

        String describeTranslationButtonState();

        WordLyricModel currentWordLyricModel();

        String currentTranslationPreferencePackage();

        boolean userWantsTranslationButton(String packageName);

        boolean isLyricInfoTranslationEnabled(String packageName);

        boolean isNearRememberedLyricsRecyclerView(View candidate);

        void translationButtonDebug(String message);
    }

    private static final String TRANSLATION_ACTION_DESCRIPTION_PREFIX =
            "\u7ffb\u8bd1\uff1a";
    private static final int TRANSLATION_ICON_FINGERPRINT_SIZE = 48;
    /** Action icons stay far below this; artwork/wallpaper bitmaps would need a full main-thread copy to reject. */
    private static final int TRANSLATION_ICON_MAX_BITMAP_PX = 512;

    private final Host host;
    private final TranslationToggleMediaActionBinder translationToggleActionBinder;
    private final Handler mainHandler;
    final Object translationActionViewsLock = new Object();
    private final WeakHashMap<View, Boolean> translationActionViews = new WeakHashMap<>();
    /**
     * Tracked action views whose VISIBLE request was redirected to INVISIBLE. The ColorOS 17
     * plugin binds a button's visibility before its icon and label (section/media/s1.H), so the
     * redirect still sees the previous translation label on a slot that is being rebound to
     * another action. Guarded by {@link #translationActionViewsLock}.
     */
    final WeakHashMap<View, Boolean> translationActionRedirectedViews = new WeakHashMap<>();
    final WeakHashMap<ImageView, Boolean> translationActionPresentationStates =
            new WeakHashMap<>();
    final WeakHashMap<ImageView, Boolean> translationActionPresentationInFlight =
            new WeakHashMap<>();
    volatile boolean translationActionTrackingActive;
    private final WeakHashMap<Bitmap, Boolean> translationBitmapMatchCache = new WeakHashMap<>();
    private final WeakHashMap<Drawable, Boolean> translationDrawableMatchCache =
            new WeakHashMap<>();
    /** Rebinds create new Drawable instances of the same resource; their shared state renders identically. */
    private final WeakHashMap<Drawable.ConstantState, Boolean> translationDrawableStateMatchCache =
            new WeakHashMap<>();
    private final WeakHashMap<View, Long> translationRootLastScanAt = new WeakHashMap<>();
    volatile byte[] translationIconAlphaFingerprint;

    TranslationActionViewTracker(
            Host host,
            TranslationToggleMediaActionBinder translationToggleActionBinder,
            Handler mainHandler) {
        this.host = host;
        this.translationToggleActionBinder = translationToggleActionBinder;
        this.mainHandler = mainHandler;
    }

    private boolean shouldManageTranslationActionViewVisibility() {
        return host.shouldManageTranslationActionViewVisibility();
    }

    private String describeTranslationButtonState() {
        return host.describeTranslationButtonState();
    }

    private String currentTranslationPreferencePackage() {
        return host.currentTranslationPreferencePackage();
    }

    private boolean userWantsTranslationButton(String packageName) {
        return host.userWantsTranslationButton(packageName);
    }

    private boolean isLyricInfoTranslationEnabled(String packageName) {
        return host.isLyricInfoTranslationEnabled(packageName);
    }

    private boolean isNearRememberedLyricsRecyclerView(View candidate) {
        return host.isNearRememberedLyricsRecyclerView(candidate);
    }

    private void translationButtonDebug(String message) {
        host.translationButtonDebug(message);
    }

    void detectTranslationActionImageView(ImageView imageView, String reason) {
        if (imageView == null
                || translationIconAlphaFingerprint == null
                || (!isRememberedTranslationActionView(imageView)
                && !isNearRememberedLyricsRecyclerView(imageView))) {
            return;
        }
        if (!looksLikeTranslationIcon(imageView.getDrawable())) {
            return;
        }
        boolean newlyDetected = !isRememberedTranslationActionView(imageView);
        rememberTranslationActionView(imageView, true);
        if (newlyDetected) {
            translationButtonDebug("Detected translation action view from " + reason);
        }
    }

    void rememberTranslationActionView(View view) {
        rememberTranslationActionView(view, false);
    }

    void rememberTranslationActionView(View view, boolean iconMatched) {
        if (view == null) {
            return;
        }
        boolean added;
        synchronized (translationActionViewsLock) {
            Boolean previous = translationActionViews.get(view);
            added = previous == null;
            translationActionViews.put(view, iconMatched || Boolean.TRUE.equals(previous));
            translationActionTrackingActive = true;
        }
        applyTranslationActionViewVisibility(view);
        if (!added) {
            return;
        }
        translationButtonDebug("Tracked translation action view, iconMatched=" + iconMatched
                + ", attached=" + view.isAttachedToWindow()
                + ", visibility=" + view.getVisibility());
        view.post(() -> applyTranslationActionViewVisibility(view));
        mainHandler.postDelayed(() -> applyTranslationActionViewVisibility(view), 64L);
        mainHandler.postDelayed(() -> applyTranslationActionViewVisibility(view), 240L);
    }

    private void forgetTranslationActionView(View view) {
        if (view == null) {
            return;
        }
        synchronized (translationActionViewsLock) {
            translationActionViews.remove(view);
            translationActionTrackingActive = !translationActionViews.isEmpty();
        }
    }

    void clearTrackedTranslationActionViews() {
        synchronized (translationActionViewsLock) {
            translationActionViews.clear();
            translationActionRedirectedViews.clear();
            translationActionTrackingActive = false;
        }
    }

    void forgetTranslationActionViewIfRebound(View view) {
        if (view == null
                || !isRememberedTranslationActionView(view)
                || isTranslationActionDescription(view.getContentDescription())) {
            return;
        }
        if (view instanceof ImageView
                && looksLikeTranslationIcon(((ImageView) view).getDrawable())) {
            rememberTranslationActionView(view, true);
            return;
        }
        translationButtonDebug("forget translation action after rebound, view="
                + describeViewForLog(view)
                + ", description=" + view.getContentDescription());
        forgetTranslationActionView(view);
        restoreRedirectedTranslationActionVisibility(view);
    }

    /**
     * Gives a slot rebound to another action the VISIBLE state its bind asked for before the
     * redirect. The view is already forgotten, so this write is not redirected again.
     */
    private void restoreRedirectedTranslationActionVisibility(View view) {
        boolean redirected;
        synchronized (translationActionViewsLock) {
            redirected = translationActionRedirectedViews.remove(view) != null;
        }
        if (!redirected || view.getVisibility() != View.INVISIBLE) {
            return;
        }
        view.setVisibility(View.VISIBLE);
        translationButtonDebug("restored redirected action visibility after rebound, view="
                + describeViewForLog(view));
    }

    boolean isRedirectedTranslationActionView(View view) {
        synchronized (translationActionViewsLock) {
            return translationActionRedirectedViews.containsKey(view);
        }
    }

    boolean isRememberedTranslationActionView(View view) {
        synchronized (translationActionViewsLock) {
            return translationActionViews.containsKey(view);
        }
    }

    boolean isIconMatchedTranslationActionView(View view) {
        synchronized (translationActionViewsLock) {
            return Boolean.TRUE.equals(translationActionViews.get(view));
        }
    }

    void refreshTranslationActionViewVisibility() {
        ArrayList<View> views;
        synchronized (translationActionViewsLock) {
            views = new ArrayList<>(translationActionViews.keySet());
        }
        translationButtonDebug("refresh translation action views, count=" + views.size()
                + ", state=" + describeTranslationButtonState());
        for (View view : views) {
            applyTranslationActionViewVisibility(view);
        }
    }

    private void applyTranslationActionViewVisibility(View view) {
        if (view == null || !isRememberedTranslationActionView(view)) {
            return;
        }
        // The media card recycles button views across tracks. If this slot no longer shows the
        // translation toggle (for example the player's own favorite returned for a song without
        // translation), stop managing it so the player's button stays visible.
        if (!stillShowsTranslationToggle(view)) {
            forgetTranslationActionViewIfRebound(view);
            if (!isRememberedTranslationActionView(view)) {
                CompatibilityStatus.global().forgetOwner(view);
                return;
            }
        }
        applyTranslationActionViewPresentation(view);
        // Before the immersive lyric surface is attached, leave the action at ColorOS' requested
        // visibility. Hiding it during the pre-bind phase can strand a recycled view INVISIBLE.
        if (!shouldManageTranslationActionViewVisibility()) {
            CompatibilityStatus.global().forgetOwner(view);
            translationButtonDebug("skip translation view visibility management"
                    + ", state=" + describeTranslationButtonState()
                    + ", view=" + describeViewForLog(view));
            return;
        }
        int translationCount = host.currentWordLyricModel() == null
                ? -1
                : host.currentWordLyricModel().translationCount();
        boolean userWantsButton = userWantsTranslationButton(
                currentTranslationPreferencePackage());
        if (!PlayerTranslationTogglePolicy.shouldForceShowTrackedTranslationActionView(
                true, translationCount, userWantsButton)
                && !PlayerTranslationTogglePolicy.shouldForceHideTrackedTranslationActionView(
                true, translationCount, userWantsButton)) {
            CompatibilityStatus.global().translationPending(view, userWantsButton);
            translationButtonDebug("leave translation view visibility during lyric-model gap"
                    + ", state=" + describeTranslationButtonState()
                    + ", view=" + describeViewForLog(view));
            return;
        }
        int targetVisibility = PlayerTranslationTogglePolicy
                .shouldForceShowTrackedTranslationActionView(
                        true,
                        translationCount,
                        userWantsButton)
                ? View.VISIBLE
                : View.INVISIBLE;
        if (view.getVisibility() != targetVisibility) {
            view.setVisibility(targetVisibility);
            WordLyricModel model = host.currentWordLyricModel();
            translationButtonDebug("Updated translation action view visibility=" + targetVisibility
                    + ", translations=" + (model == null ? -1 : model.translationCount())
                    + ", attached=" + view.isAttachedToWindow());
            translationButtonDebug("applied translation view visibility=" + targetVisibility
                    + ", state=" + describeTranslationButtonState()
                    + ", view=" + describeViewForLog(view));
        }
        CompatibilityStatus.global().translationButton(
                view, userWantsButton, translationCount, targetVisibility == View.VISIBLE);
    }

    private void applyTranslationActionViewPresentation(View view) {
        if (view == null) return;
        boolean enabled = isLyricInfoTranslationEnabled(
                currentTranslationPreferencePackage());
        String description = TranslationActionPresentationPolicy.contentDescription(enabled);
        CharSequence currentDescription = view.getContentDescription();
        if (!TextUtils.equals(currentDescription, description)) {
            view.setContentDescription(description);
        }
        ImageView imageView = findTranslationActionImageView(
                view,
                0,
                isTranslationActionDescription(view.getContentDescription())
                        || isRememberedTranslationActionView(view));
        if (imageView == null) return;
        synchronized (translationActionViewsLock) {
            if (Boolean.TRUE.equals(translationActionPresentationInFlight.get(imageView))) {
                return;
            }
            Boolean applied = translationActionPresentationStates.get(imageView);
            if (applied != null && applied == enabled) return;
            translationActionPresentationStates.put(imageView, enabled);
            translationActionPresentationInFlight.put(imageView, true);
        }
        try {
            if (!TextUtils.equals(imageView.getContentDescription(), description)) {
                imageView.setContentDescription(description);
            }
            Drawable drawable = translationToggleActionBinder
                    .createTranslationActionPresentationDrawable(
                            currentTranslationPreferencePackage(),
                            enabled);
            if (drawable != null) {
                // Match the official heart path: swap the bound action icon immediately instead
                // of waiting for the next MediaData/PlaybackState rebuild.
                imageView.setImageAlpha(255);
                imageView.setImageDrawable(drawable);
            } else {
                imageView.setImageAlpha(
                        TranslationActionPresentationPolicy.imageAlpha(enabled));
            }
            imageView.invalidate();
            translationButtonDebug("Rebound translation action icon immediately"
                    + ", drawable=" + (drawable != null)
                    + ", alpha=" + TranslationActionPresentationPolicy.imageAlpha(enabled)
                    + ", enabled=" + enabled
                    + ", view=" + describeViewForLog(imageView));
        } finally {
            synchronized (translationActionViewsLock) {
                translationActionPresentationInFlight.remove(imageView);
            }
        }
    }

    private ImageView findTranslationActionImageView(
            View view,
            int depth,
            boolean trustedActionContainer) {
        if (view == null || depth > 3) return null;
        boolean trusted = trustedActionContainer
                || isTranslationActionDescription(view.getContentDescription());
        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            return trusted
                    || looksLikeTranslationIcon(imageView.getDrawable())
                    || isRememberedTranslationActionView(imageView)
                    ? imageView
                    : null;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            ImageView match = findTranslationActionImageView(
                    group.getChildAt(index),
                    depth + 1,
                    trusted);
            if (match != null) return match;
        }
        return null;
    }

    boolean stillShowsTranslationToggle(View view) {
        if (isTranslationActionDescription(view.getContentDescription())) {
            return true;
        }
        return view instanceof ImageView
                && looksLikeTranslationIcon(((ImageView) view).getDrawable());
    }

    int rememberedTranslationActionViewCount() {
        synchronized (translationActionViewsLock) {
            return translationActionViews.size();
        }
    }

    static boolean isTranslationActionDescription(CharSequence description) {
        return description != null
                && description.toString().startsWith(
                TRANSLATION_ACTION_DESCRIPTION_PREFIX);
    }

    void rememberTranslationIconFingerprint(Drawable drawable) {
        if (translationIconAlphaFingerprint != null) {
            return;
        }
        byte[] fingerprint = renderDrawableAlphaFingerprint(drawable);
        if (fingerprint == null) {
            return;
        }
        translationIconAlphaFingerprint = fingerprint;
    }

    void discoverTranslationActionViewsNear(View anchor) {
        if (anchor == null) {
            return;
        }
        View root = anchor;
        for (int i = 0; i < 32; i++) {
            Object parent = root.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            root = (View) parent;
        }
        long now = SystemClock.elapsedRealtime();
        synchronized (translationRootLastScanAt) {
            Long lastScanAt = translationRootLastScanAt.get(root);
            if (lastScanAt != null && now - lastScanAt < 50L) {
                return;
            }
            translationRootLastScanAt.put(root, now);
        }

        ArrayList<View> pending = new ArrayList<>();
        pending.add(root);
        int visited = 0;
        while (!pending.isEmpty() && visited < 6_000) {
            View view = pending.remove(pending.size() - 1);
            visited++;
            boolean descriptionMatched = isTranslationActionDescription(
                    view.getContentDescription());
            if (descriptionMatched) {
                rememberTranslationActionView(view, false);
            }
            if (view instanceof ImageView
                    && (descriptionMatched
                    || (translationIconAlphaFingerprint != null
                    && looksLikeTranslationIcon(((ImageView) view).getDrawable())))) {
                rememberTranslationActionView(view, true);
            }
            if (!(view instanceof ViewGroup)) {
                continue;
            }
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child != null) {
                    pending.add(child);
                }
            }
        }
    }

    private boolean looksLikeTranslationIcon(Drawable drawable) {
        byte[] expected = translationIconAlphaFingerprint;
        if (drawable == null || expected == null) {
            return false;
        }
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                return false;
            }
            if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > TRANSLATION_ICON_MAX_BITMAP_PX) {
                return false;
            }
            synchronized (translationBitmapMatchCache) {
                Boolean cached = translationBitmapMatchCache.get(bitmap);
                if (cached != null) {
                    return cached;
                }
            }
            boolean traced = BridgePerfTrace.begin("CLL:translationIconFingerprint");
            boolean matches;
            try {
                matches = alphaFingerprintsMatch(
                        expected,
                        renderBitmapAlphaFingerprint(bitmap));
            } finally {
                BridgePerfTrace.end(traced);
            }
            synchronized (translationBitmapMatchCache) {
                translationBitmapMatchCache.put(bitmap, matches);
            }
            return matches;
        }

        String drawableClassName = drawable.getClass().getName();
        if (!drawableClassName.contains("VectorDrawable")) {
            return false;
        }
        Drawable.ConstantState state = drawable.getConstantState();
        synchronized (translationDrawableMatchCache) {
            Boolean cached = translationDrawableMatchCache.get(drawable);
            if (cached == null && state != null) {
                cached = translationDrawableStateMatchCache.get(state);
            }
            if (cached != null) {
                return cached;
            }
        }
        boolean traced = BridgePerfTrace.begin("CLL:translationVectorFingerprint");
        boolean matches;
        try {
            matches = alphaFingerprintsMatch(
                    expected,
                    renderDrawableAlphaFingerprint(drawable));
        } finally {
            BridgePerfTrace.end(traced);
        }
        synchronized (translationDrawableMatchCache) {
            translationDrawableMatchCache.put(drawable, matches);
            if (state != null) {
                translationDrawableStateMatchCache.put(state, matches);
            }
        }
        return matches;
    }

    private static boolean alphaFingerprintsMatch(byte[] expected, byte[] actual) {
        if (expected == null || actual == null || expected.length != actual.length) {
            return false;
        }

        int expectedMax = 0;
        int actualMax = 0;
        for (int i = 0; i < expected.length; i++) {
            expectedMax = Math.max(expectedMax, expected[i] & 0xff);
            actualMax = Math.max(actualMax, actual[i] & 0xff);
        }
        if (expectedMax == 0 || actualMax == 0) {
            return false;
        }

        int expectedThreshold = Math.max(8, expectedMax / 8);
        int actualThreshold = Math.max(8, actualMax / 8);
        int intersection = 0;
        int union = 0;
        int expectedPixels = 0;
        int actualPixels = 0;
        for (int i = 0; i < expected.length; i++) {
            boolean expectedOn = (expected[i] & 0xff) >= expectedThreshold;
            boolean actualOn = (actual[i] & 0xff) >= actualThreshold;
            if (expectedOn) {
                expectedPixels++;
            }
            if (actualOn) {
                actualPixels++;
            }
            if (expectedOn && actualOn) {
                intersection++;
            }
            if (expectedOn || actualOn) {
                union++;
            }
        }
        if (union == 0 || expectedPixels == 0 || actualPixels == 0) {
            return false;
        }
        float areaRatio = actualPixels / (float) expectedPixels;
        float overlap = intersection / (float) union;
        return areaRatio >= 0.72f && areaRatio <= 1.38f && overlap >= 0.68f;
    }

    private static byte[] renderBitmapAlphaFingerprint(Bitmap source) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        Bitmap software = null;
        Bitmap scaled = null;
        try {
            software = source.getConfig() == Bitmap.Config.HARDWARE
                    ? source.copy(Bitmap.Config.ARGB_8888, false)
                    : source;
            if (software == null || software.isRecycled()) {
                return null;
            }
            scaled = Bitmap.createScaledBitmap(
                    software,
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    true);
            return alphaFingerprintOf(scaled);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (scaled != null && scaled != software && scaled != source) {
                scaled.recycle();
            }
            if (software != null && software != source) {
                software.recycle();
            }
        }
    }

    private static byte[] alphaFingerprintOf(Bitmap bitmap) {
        int size = TRANSLATION_ICON_FINGERPRINT_SIZE;
        int[] pixels = new int[size * size];
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
        byte[] alpha = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            alpha[i] = (byte) (pixels[i] >>> 24);
        }
        return alpha;
    }

    private static byte[] renderDrawableAlphaFingerprint(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            android.graphics.Rect oldBounds = new android.graphics.Rect(drawable.getBounds());
            int oldAlpha = drawable.getAlpha();
            drawable.setBounds(
                    0,
                    0,
                    TRANSLATION_ICON_FINGERPRINT_SIZE,
                    TRANSLATION_ICON_FINGERPRINT_SIZE);
            drawable.setAlpha(255);
            drawable.draw(canvas);
            drawable.setAlpha(oldAlpha);
            drawable.setBounds(oldBounds);

            return alphaFingerprintOf(bitmap);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }
}
