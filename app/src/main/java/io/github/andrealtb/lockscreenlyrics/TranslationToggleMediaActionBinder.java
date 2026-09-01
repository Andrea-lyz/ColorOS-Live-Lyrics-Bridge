package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.InsetDrawable;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.StructuredBridgeLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns every OPlus media-card model operation behind the lyric translation toggle: locating the
 * player's favorite action (PlaybackState.CustomAction mapping, the OPlus heart marker, or the
 * single-action Rule0 fallback), replacing it in place, promoting it within the Rule0 custom
 * actions and presenting the enabled state.
 *
 * <p>Decision logic lives in {@link PlayerTranslationTogglePolicy}; this class only executes on
 * the media model objects owned by SystemUI, so the logic stays out of
 * {@link LockscreenLyricsModule}.</p>
 */
final class TranslationToggleMediaActionBinder {

    interface Host {
        Context currentApplicationContext();

        String logProcessName();

        boolean isLyricInfoTranslationEnabled(String packageName);

        void rememberTranslationIconFingerprint(Drawable drawable);

        void onTranslationToggleClicked(String packageName);
    }

    private static final String TRANSLATION_ICON_RESOURCE_NAME = "ic_translation";
    private static final String SALT_DESKTOP_LYRIC_ACTION = "com.salt.music.desktop_lyrics";

    private final Host host;
    private String lastTranslationToggleConfigLogKey = "";
    private long lastTranslationToggleConfigLogAt;

    TranslationToggleMediaActionBinder(Host host) {
        this.host = host;
    }

    void applyTranslationToggle(
            String packageName,
            Object mediaButton,
            boolean allowOverride,
            boolean userWantsButton,
            String currentProviderPackage,
            int modelTranslationCount,
            int payloadTranslationChars) {
        if (isEmpty(packageName) || mediaButton == null) {
            debug("replace action skipped, package=" + nullToEmpty(packageName)
                    + ", mediaButton=" + (mediaButton != null));
            return;
        }

        Object mediaButtonEx = invokeNoArgByName(mediaButton, "getMediaButtonEx");
        Object actions = invokeNoArgByName(mediaButtonEx, "getRule0CustomActions");
        if (!(actions instanceof List)) {
            debug("replace action skipped: Rule0 actions missing"
                    + ", package=" + nullToEmpty(packageName)
                    + ", mediaButtonEx=" + (mediaButtonEx == null
                    ? "null"
                    : mediaButtonEx.getClass().getName())
                    + ", actions=" + (actions == null
                    ? "null"
                    : actions.getClass().getName()));
            return;
        }
        List<?> actionList = (List<?>) actions;
        Object heartAction = invokeNoArgByName(mediaButtonEx, "getHeartAction");
        debug("inspect Rule0 actions, package=" + nullToEmpty(packageName)
                + ", count=" + actionList.size()
                + ", heart=" + (heartAction == null
                ? "null"
                : heartAction.getClass().getName())
                + ", actions=" + describeRule0ActionIds(actionList));

        Object overrideCandidate = null;
        String overrideActionId = "";
        for (Object mediaAction : actionList) {
            Object runnable = invokeNoArgByName(mediaAction, "getAction");
            PlaybackState.CustomAction customAction = findPlaybackStateCustomAction(runnable);
            if (customAction == null) {
                continue;
            }
            String actionId = customAction.getAction();
            boolean integrationAction =
                    LyricInfoContract.ACTION_TOGGLE_TRANSLATION.equals(actionId);
            boolean legacySaltAction = SALT_DESKTOP_LYRIC_ACTION.equals(actionId);
            if (!integrationAction && !legacySaltAction) {
                if (overrideCandidate == null && allowOverride) {
                    overrideCandidate = mediaAction;
                    overrideActionId = actionId;
                }
                continue;
            }
            if (!userWantsButton) {
                if (integrationAction) {
                    removeRule0Action(mediaButtonEx, actionList, mediaAction);
                    debug("Removed lyricInfo translation toggle action, package="
                            + nullToEmpty(packageName)
                            + " (button disabled by user)");
                } else {
                    debug("Left player desktop-lyric action untouched, package="
                            + nullToEmpty(packageName)
                            + " (button disabled by user)");
                }
                return;
            }
            configure(
                    mediaButtonEx,
                    actionList,
                    mediaAction,
                    packageName,
                    integrationAction ? "public" : "salt-legacy",
                    actionId);
            if (integrationAction
                    && PlayerTranslationTogglePolicy
                            .shouldBindOplusHeartAlongsidePublicTranslationAction(packageName)) {
                bindOplusHeartAlongsidePublicAction(
                        mediaButtonEx,
                        mediaAction,
                        packageName);
            }
            return;
        }
        if (overrideCandidate == null) {
            overrideCandidate = findOverrideFallback(mediaButtonEx, actionList);
            overrideActionId = "";
        }
        if (overrideCandidate != null && allowOverride) {
            configure(
                    mediaButtonEx,
                    actionList,
                    overrideCandidate,
                    packageName,
                    "player-override",
                    overrideActionId);
            return;
        }
        debug("no translation action candidate | package=" + nullToEmpty(packageName)
                + ", currentProvider=" + nullToEmpty(currentProviderPackage)
                + ", modelTranslations=" + modelTranslationCount
                + ", payloadTranslationChars=" + payloadTranslationChars);
    }

    private void removeRule0Action(Object mediaButtonEx, List<?> actions, Object target) {
        if (actions == null || actions.isEmpty() || target == null) {
            return;
        }
        ArrayList<Object> remaining = new ArrayList<>(actions.size());
        boolean removed = false;
        for (Object action : actions) {
            if (action == target) {
                removed = true;
            } else {
                remaining.add(action);
            }
        }
        if (!removed) {
            return;
        }
        tryInvokeOneArgByName(mediaButtonEx, "setRule0CustomActions", remaining);
        writeFieldValue(mediaButtonEx, "rule0CustomActions", remaining);
    }

    /**
     * QQ Music and KuGou (original) expose their favorite without a PlaybackState.CustomAction
     * backing the Rule0 action. Locate it through the OPlus heart marker first, then fall back to
     * the single-action Rule0 list those players keep.
     */
    private Object findOverrideFallback(Object mediaButtonEx, List<?> actions) {
        Object heartAction = invokeNoArgByName(mediaButtonEx, "getHeartAction");
        if (heartAction != null) {
            debug("override fallback located favorite via OPlus heart action"
                    + ", action=" + heartAction.getClass().getName());
            return heartAction;
        }
        if (actions != null && actions.size() == 1) {
            debug("override fallback located single Rule0 action");
            return actions.get(0);
        }
        return null;
    }

    /**
     * Copy translation presentation onto the OPlus heart without promoting it into Rule0.
     * Poweramp's visible favorite slot after a pause rebuild is that heart, not Rule0[0].
     */
    void bindOplusHeartAlongsidePublicAction(
            Object mediaButtonEx,
            Object publicMediaAction,
            String packageName) {
        Object heartAction = invokeNoArgByName(mediaButtonEx, "getHeartAction");
        if (heartAction == null) {
            debug("public translation action present, OPlus heart absent, package="
                    + nullToEmpty(packageName));
            return;
        }
        if (heartAction == publicMediaAction) {
            debug("OPlus heart is the public translation action, package="
                    + nullToEmpty(packageName));
            return;
        }
        debug("bind OPlus heart alongside public translation action, package="
                + nullToEmpty(packageName)
                + ", heart=" + heartAction.getClass().getName());
        if (!replaceMediaActionIcon(heartAction, packageName)) {
            rememberCurrentMediaActionIcon(heartAction);
        }
        present(heartAction, packageName);
        tryInvokeOneArgByName(heartAction, "setAction", (Runnable) () -> {
            boolean before = host.isLyricInfoTranslationEnabled(packageName);
            debug("translation action clicked, package=" + nullToEmpty(packageName)
                    + ", slot=oplus-heart"
                    + ", enabledBefore=" + before);
            host.onTranslationToggleClicked(packageName);
            present(heartAction, packageName);
            debug("translation action click applied, package=" + nullToEmpty(packageName)
                    + ", slot=oplus-heart"
                    + ", enabledAfter=" + host.isLyricInfoTranslationEnabled(packageName));
        });
        maybeLogConfiguredTranslationMediaAction(packageName, "public-heart", "");
    }

    private void configure(
            Object mediaButtonEx,
            List<?> actions,
            Object mediaAction,
            String packageName,
            String protocol,
            String originalActionId) {
        boolean publicProtocol = "public".equals(protocol);
        boolean overrideProtocol = "player-override".equals(protocol);
        boolean enabledBefore = host.isLyricInfoTranslationEnabled(packageName);
        debug("configure translation action, package=" + nullToEmpty(packageName)
                + ", protocol=" + protocol
                + ", originalAction=" + nullToEmpty(originalActionId)
                + ", enabledBefore=" + enabledBefore
                + ", mediaAction=" + (mediaAction == null
                ? "null"
                : mediaAction.getClass().getName()));
        if (publicProtocol || overrideProtocol) {
            promoteTranslationToggleAction(mediaButtonEx, actions, mediaAction);
            if (!replaceMediaActionIcon(mediaAction, packageName) && publicProtocol) {
                rememberCurrentMediaActionIcon(mediaAction);
            }
        } else {
            replaceMediaActionIcon(mediaAction, packageName);
        }

        present(mediaAction, packageName);
        tryInvokeOneArgByName(mediaAction, "setAction", (Runnable) () -> {
            boolean before = host.isLyricInfoTranslationEnabled(packageName);
            debug("translation action clicked, package=" + nullToEmpty(packageName)
                    + ", enabledBefore=" + before);
            host.onTranslationToggleClicked(packageName);
            present(mediaAction, packageName);
            debug("translation action click applied, package=" + nullToEmpty(packageName)
                    + ", enabledAfter=" + host.isLyricInfoTranslationEnabled(packageName));
        });
        maybeLogConfiguredTranslationMediaAction(packageName, protocol, originalActionId);
    }

    private void promoteTranslationToggleAction(
            Object mediaButtonEx, List<?> actions, Object translationAction) {
        if (actions.isEmpty() || actions.get(0) == translationAction) {
            return;
        }
        ArrayList<Object> ordered = new ArrayList<>(actions.size());
        ordered.add(translationAction);
        for (Object action : actions) {
            if (action != translationAction) {
                ordered.add(action);
            }
        }
        tryInvokeOneArgByName(mediaButtonEx, "setRule0CustomActions", ordered);
        Object applied = invokeNoArgByName(mediaButtonEx, "getRule0CustomActions");
        if (!(applied instanceof List)
                || ((List<?>) applied).isEmpty()
                || ((List<?>) applied).get(0) != translationAction) {
            writeFieldValue(mediaButtonEx, "rule0CustomActions", ordered);
        }
        debug("Promoted lyricInfo translation toggle within OPlus Rule0 custom actions");
    }

    private void rememberCurrentMediaActionIcon(Object mediaAction) {
        Object icon = invokeNoArgByName(mediaAction, "getIcon");
        if (icon instanceof Drawable) {
            host.rememberTranslationIconFingerprint((Drawable) icon);
        }
    }

    private boolean replaceMediaActionIcon(Object mediaAction, String packageName) {
        Context context = host.currentApplicationContext();
        if (context == null || mediaAction == null || isEmpty(packageName)) {
            return false;
        }
        try {
            TranslationIcon translationIcon = findTranslationIcon(context, packageName);
            debug("replace media action icon, package=" + nullToEmpty(packageName)
                    + ", foundIcon=" + (translationIcon != null)
                    + ", action=" + mediaAction.getClass().getName());
            if (translationIcon == null
                    || !isSystemUiSafeSemanticIcon(translationIcon.icon)) {
                debug("replace media action icon skipped: resource-backed semantic icon missing"
                        + ", package=" + nullToEmpty(packageName));
                return false;
            }
            Drawable drawable = translationIcon.drawable;
            debug("replace media action icon drawable=" + (drawable != null)
                    + ", action=" + mediaAction.getClass().getName());
            if (drawable != null) {
                drawable = drawable.mutate();
                host.rememberTranslationIconFingerprint(drawable);
                tryInvokeOneArgByName(mediaAction, "setIcon", drawable);
                Object appliedIcon = invokeNoArgByName(mediaAction, "getIcon");
                debug("replace media action icon applied=" + (appliedIcon == drawable)
                        + ", appliedClass=" + (appliedIcon == null
                        ? "null"
                        : appliedIcon.getClass().getName()));
            }
            Object mediaActionEx = invokeNoArgByName(mediaAction, "getMediaActionEx");
            writeFieldValue(mediaActionEx, "icon", translationIcon.icon);
            return true;
        } catch (Throwable t) {
            StructuredBridgeLog.error("Failed to load lyric translation icon: " + t, t);
            return false;
        }
    }

    private static final class TranslationIcon {
        final Icon icon;
        final Drawable drawable;

        TranslationIcon(Icon icon, Drawable drawable) {
            this.icon = icon;
            this.drawable = drawable;
        }
    }

    private static TranslationIcon findTranslationIcon(
            Context context, String providerPackage) {
        // Host-player packages can expose unrelated resources with the same generic name and
        // different theme tints. Keep one canonical white action icon across every v5 player.
        return findTranslationIconInPackage(
                context,
                TranslationActionPresentationPolicy.CANONICAL_ICON_PACKAGE);
    }

    private static TranslationIcon findTranslationIconInPackage(
            Context context, String packageName) {
        if (context == null || isEmpty(packageName)) {
            return null;
        }
        try {
            Context packageContext = context.createPackageContext(
                    packageName,
                    Context.CONTEXT_IGNORE_SECURITY);
            Resources resources = packageContext.getResources();
            int resourceId = resources.getIdentifier(
                    TRANSLATION_ICON_RESOURCE_NAME,
                    "drawable",
                    packageName);
            if (resourceId == 0) {
                return null;
            }
            Drawable drawable = packageContext.getDrawable(resourceId);
            if (drawable == null) return null;
            drawable = drawable.mutate();
            drawable.setTint(Color.WHITE);
            Icon semanticIcon = Icon.createWithResource(packageContext, resourceId);
            if (!isSystemUiSafeSemanticIcon(semanticIcon)) {
                return null;
            }
            return new TranslationIcon(
                    semanticIcon,
                    drawable);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void present(Object mediaAction, String packageName) {
        boolean enabled = host.isLyricInfoTranslationEnabled(packageName);
        debug("update action presentation, package=" + nullToEmpty(packageName)
                + ", enabled=" + enabled
                + ", action=" + (mediaAction == null
                ? "null"
                : mediaAction.getClass().getName()));
        tryInvokeOneArgByName(
                mediaAction,
                "setContentDescription",
                TranslationActionPresentationPolicy.contentDescription(enabled));
        Object icon = invokeNoArgByName(mediaAction, "getIcon");
        if (icon instanceof Drawable) {
            ((Drawable) icon).setAlpha(
                    TranslationActionPresentationPolicy.imageAlpha(enabled));
            ((Drawable) icon).invalidateSelf();
        }
    }

    private static boolean isSystemUiSafeSemanticIcon(Icon icon) {
        return icon != null && isSystemUiSafeSemanticIconType(icon.getType());
    }

    static boolean isSystemUiSafeSemanticIconType(int iconType) {
        // OPlusMediaViewPagerAdapter#setSemanticButton unconditionally calls getResPackage()
        // for every non-null OplusMediaActionEx icon. Framework Icon throws for BITMAP, DATA,
        // URI and adaptive-bitmap types, so this high-impact SystemUI path must be resource-only.
        return iconType == Icon.TYPE_RESOURCE;
    }

    Drawable createTranslationActionPresentationDrawable(
            String providerPackage,
            boolean enabled) {
        TranslationIcon translationIcon = findTranslationIcon(
                host.currentApplicationContext(),
                providerPackage);
        if (translationIcon == null || translationIcon.drawable == null) return null;
        Drawable drawable = translationIcon.drawable.mutate();
        drawable.setAlpha(TranslationActionPresentationPolicy.imageAlpha(enabled));
        return new InsetDrawable(
                drawable,
                TranslationActionPresentationPolicy.ACTION_ICON_INSET_FRACTION);
    }

    private void maybeLogConfiguredTranslationMediaAction(
            String packageName, String protocol, String originalActionId) {
        String actionId = isEmpty(originalActionId) ? "" : originalActionId;
        String logKey = packageName + "|" + protocol + "|" + actionId;
        long now = SystemClock.elapsedRealtime();
        if (logKey.equals(lastTranslationToggleConfigLogKey)
                && now - lastTranslationToggleConfigLogAt
                < LyricTimingTuningConstants.Translation.TOGGLE_CONFIG_LOG_THROTTLE_MS) {
            return;
        }
        lastTranslationToggleConfigLogKey = logKey;
        lastTranslationToggleConfigLogAt = now;
        debug("Configured lyricInfo translation toggle for " + packageName
                + ", protocol=" + protocol
                + (isEmpty(actionId)
                ? ""
                : ", originalAction=" + actionId));
    }

    private static PlaybackState.CustomAction findPlaybackStateCustomAction(Object runnable) {
        if (runnable == null) {
            return null;
        }
        Class<?> current = runnable.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(runnable);
                    if (value instanceof PlaybackState.CustomAction) {
                        return (PlaybackState.CustomAction) value;
                    }
                } catch (Throwable ignored) {
                    // Continue through synthetic fields and superclass fields.
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String describeRule0ActionIds(List<?> actions) {
        if (actions == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(actions.size(), 8);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Object mediaAction = actions.get(i);
            Object runnable = invokeNoArgByName(mediaAction, "getAction");
            PlaybackState.CustomAction customAction = findPlaybackStateCustomAction(runnable);
            if (customAction != null) {
                builder.append(customAction.getAction());
            } else {
                builder.append(mediaAction == null
                        ? "null"
                        : mediaAction.getClass().getSimpleName());
            }
        }
        if (actions.size() > limit) {
            builder.append(", +").append(actions.size() - limit);
        }
        return builder.append(']').toString();
    }

    private static Object invokeNoArgByName(Object target, String methodName) {
        if (target == null || isEmpty(methodName)) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void tryInvokeOneArgByName(
            Object target, String methodName, Object arg) {
        if (target == null || isEmpty(methodName)) {
            return;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            Method[] methods = current.getDeclaredMethods();
            for (Method method : methods) {
                if (!methodName.equals(method.getName())
                        || method.getParameterTypes().length != 1) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(target, arg);
                    return;
                } catch (Throwable ignored) {
                    // Try another overload or superclass implementation.
                }
            }
            current = current.getSuperclass();
        }
    }

    private static void writeFieldValue(Object target, String fieldName, Object value) {
        if (target == null || isEmpty(fieldName)) {
            return;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private void debug(String message) {
        StructuredBridgeLog.debug(
                BridgeDebugArea.PLAYER_SPECIAL,
                "BUTTON",
                () -> message);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
