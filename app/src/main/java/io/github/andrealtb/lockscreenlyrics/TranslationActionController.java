package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translation action state (E-4b, plan §15.3): the OPlus pkg-actions rule hook with its raw and
 * last-applied snapshots, the guarded replay, the rule-0 package sets, and the per-player
 * translation preference store (lyricInfo translation on/off and the translation button switch).
 * Moved verbatim from {@code LockscreenLyricsModule}; the only body edit is that the global default
 * translation flag is read through {@link Host}.
 *
 * <p>The settings broadcast handler, the MediaData rebind and the createActionsFromState hook stay
 * in the entry class: they read playback-binding, model, renderer and recycler state that belongs
 * to E-5 to E-7.
 */
final class TranslationActionController {
    /** What the controller reads from, or asks of, the rest of the SystemUI runtime. */
    interface Host {
        boolean isDeviceKeyguardLocked(Context context);

        String currentTranslationPreferencePackage();

        boolean defaultTranslationEnabled();

        void translationButtonDebug(String message);

        void info(BridgeDebugArea area, String event, String message);

        void error(String message, Throwable throwable);
    }

    private final Host host;
    private final OfficialLyricTextRenderer officialLyricTextRenderer;
    /** Set while the Bridge replays updatePkgActionsRule; see {@link #replayOplusPkgActionsRule}. */
    private final ThreadLocal<Boolean> replayingOplusPkgActionsRule = new ThreadLocal<>();
    private volatile Object oplusMediaActionPrioritySelector;
    volatile Method oplusUpdatePkgActionsRuleMethod;
    private volatile Object[] lastOplusPkgActionsRuleArgs;
    private volatile Object[] rawOplusPkgActionsRuleArgs;
    private final Set<String> translationToggleRule0Packages =
            ConcurrentHashMap.newKeySet();
    private final Set<String> pendingTranslationToggleRule0Packages =
            ConcurrentHashMap.newKeySet();
    private final Set<String> refreshedTranslationToggleRule0Packages =
            ConcurrentHashMap.newKeySet();
    final Set<String> loadedTranslationPreferencePackages =
            ConcurrentHashMap.newKeySet();
    final Map<String, Boolean> lyricInfoTranslationEnabledByPackage =
            new ConcurrentHashMap<>();
    final Map<String, Boolean> translationButtonEnabledByPackage =
            new ConcurrentHashMap<>();

    TranslationActionController(Host host, OfficialLyricTextRenderer officialLyricTextRenderer) {
        this.host = host;
        this.officialLyricTextRenderer = officialLyricTextRenderer;
    }

    private boolean isDeviceKeyguardLocked(Context context) {
        return host.isDeviceKeyguardLocked(context);
    }

    private String currentTranslationPreferencePackage() {
        return host.currentTranslationPreferencePackage();
    }

    private void translationButtonDebug(String message) {
        host.translationButtonDebug(message);
    }

    private void info(BridgeDebugArea area, String event, String message) {
        host.info(area, event, message);
    }

    private void error(String message, Throwable throwable) {
        host.error(message, throwable);
    }

    Object onOplusMediaUpdatePkgActionsRule(XposedInterface.Chain chain) throws Throwable {
        if (Boolean.TRUE.equals(replayingOplusPkgActionsRule.get())) {
            // A Bridge replay already carries the exact rules to apply. Recording it as the raw
            // vendor snapshot would make the unlock restore re-apply the patched rules.
            return chain.proceed();
        }
        oplusMediaActionPrioritySelector = chain.getThisObject();
        List<Object> args = chain.getArgs();
        if (args.isEmpty() || !(args.get(0) instanceof Map)) {
            return chain.proceed();
        }

        Object[] rawArgs = args.toArray(new Object[0]);
        rawOplusPkgActionsRuleArgs = TranslationActionRulePolicy.snapshotRawArgs(rawArgs);

        Context lockContext = currentApplicationContext();
        boolean keyguardLocked = isDeviceKeyguardLocked(lockContext);
        if (!keyguardLocked) {
            lastOplusPkgActionsRuleArgs = rawArgs.clone();
            return chain.proceed();
        }

        ArrayList<String> knownTranslationPackages =
                new ArrayList<>(translationToggleRule0Packages);
        Object[] patchedArgs = TranslationActionRulePolicy.patch(
                rawArgs, knownTranslationPackages);
        lastOplusPkgActionsRuleArgs = patchedArgs.clone();
        Object result = chain.proceed(patchedArgs);
        markTranslationToggleRule0PackagesRefreshed(knownTranslationPackages);
        return result;
    }

    void ensureTranslationToggleRule0(String packageName) {
        if (TextUtils.isEmpty(packageName)
                ) {
            translationButtonDebug("skip Rule0 ensure, package=" + nullToEmpty(packageName)
                    + ", reason=empty");
            return;
        }
        boolean added = translationToggleRule0Packages.add(packageName);
        if (!refreshedTranslationToggleRule0Packages.contains(packageName)) {
            pendingTranslationToggleRule0Packages.add(packageName);
        }
        translationButtonDebug("ensure Rule0 package=" + packageName
                + ", added=" + added
                + ", pending=" + pendingTranslationToggleRule0Packages.contains(packageName)
                + ", refreshed=" + refreshedTranslationToggleRule0Packages.contains(packageName));
        refreshPendingTranslationToggleRule0Packages();
    }

    /**
     * Keeps a native-action-row player out of the forced Rule0 refresh so the cached, already
     * patched argument map cannot re-apply Rule0 to it on the next {@code updatePkgActionsRule}.
     */
    void restoreOriginalPkgActionsRules() {
        Object selector = oplusMediaActionPrioritySelector;
        Method updateMethod = oplusUpdatePkgActionsRuleMethod;
        Object[] rawSnapshot = rawOplusPkgActionsRuleArgs;
        if (selector == null || updateMethod == null || rawSnapshot == null) {
            return;
        }
        try {
            synchronized (selector) {
                Object[] cleanArgs = TranslationActionRulePolicy.snapshotRawArgs(rawSnapshot);
                replayOplusPkgActionsRule(updateMethod, selector, cleanArgs);
                lastOplusPkgActionsRuleArgs = cleanArgs.clone();
            }
            translationButtonDebug("Restored native OPlus action rules from clean snapshot");
        } catch (Throwable t) {
            error("Failed to restore native OPlus action rules from snapshot", t);
        }
    }

    /**
     * Applies rules the Bridge prepared itself. The call re-enters the updatePkgActionsRule hook
     * on this thread, which then passes it through unchanged.
     */
    private void replayOplusPkgActionsRule(Method updateMethod, Object selector, Object[] args)
            throws ReflectiveOperationException {
        replayingOplusPkgActionsRule.set(Boolean.TRUE);
        try {
            updateMethod.invoke(selector, args);
        } finally {
            replayingOplusPkgActionsRule.remove();
        }
    }

    void dropTranslationToggleRule0(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        translationToggleRule0Packages.remove(packageName);
        pendingTranslationToggleRule0Packages.remove(packageName);
        refreshedTranslationToggleRule0Packages.remove(packageName);
    }

    private void refreshPendingTranslationToggleRule0Packages() {
        ArrayList<String> pendingPackages = new ArrayList<>();
        for (String packageName : pendingTranslationToggleRule0Packages) {
            if (refreshedTranslationToggleRule0Packages.contains(packageName)) {
                pendingTranslationToggleRule0Packages.remove(packageName);
            } else {
                pendingPackages.add(packageName);
            }
        }
        if (pendingPackages.isEmpty()) {
            translationButtonDebug("refresh Rule0 skipped: no pending packages");
            return;
        }

        Object selector = oplusMediaActionPrioritySelector;
        Method updateMethod = oplusUpdatePkgActionsRuleMethod;
        Object[] baseArgs = rawOplusPkgActionsRuleArgs != null ? rawOplusPkgActionsRuleArgs : lastOplusPkgActionsRuleArgs;
        if (selector == null
                || updateMethod == null
                || baseArgs == null) {
            translationButtonDebug("refresh Rule0 waiting, pending=" + pendingPackages
                    + ", selector=" + (selector != null)
                    + ", updateMethod=" + (updateMethod != null)
                    + ", baseArgs=" + (baseArgs != null));
            return;
        }

        try {
            ArrayList<String> knownTranslationPackages = new ArrayList<>();
            synchronized (selector) {
                Object[] refreshArgs = TranslationActionRulePolicy.snapshotRawArgs(baseArgs);
                if (!(refreshArgs[0] instanceof Map)) {
                    return;
                }
                for (String packageName : translationToggleRule0Packages) {
                    if (!PlayerSystemUiPolicy.preservesNativeActionRow(packageName)) {
                        knownTranslationPackages.add(packageName);
                    }
                }
                refreshArgs = TranslationActionRulePolicy.patch(
                        refreshArgs, knownTranslationPackages);
                replayOplusPkgActionsRule(updateMethod, selector, refreshArgs);
                lastOplusPkgActionsRuleArgs = refreshArgs.clone();
            }
            markTranslationToggleRule0PackagesRefreshed(knownTranslationPackages);
            info(BridgeDebugArea.PLAYER_SPECIAL, BridgeEvents.DETAIL, "Enabled OPlus Rule0 through updatePkgActionsRule for translation providers "
                    + pendingPackages);
        } catch (Throwable t) {
            error("Failed to enable OPlus Rule0 through updatePkgActionsRule for "
                    + pendingPackages, t);
        }
    }

    private void markTranslationToggleRule0PackagesRefreshed(List<String> packageNames) {
        for (String packageName : packageNames) {
            if (TextUtils.isEmpty(packageName)) {
                continue;
            }
            refreshedTranslationToggleRule0Packages.add(packageName);
            pendingTranslationToggleRule0Packages.remove(packageName);
        }
    }

    boolean isLyricInfoTranslationEnabled() {
        return isLyricInfoTranslationEnabled(currentTranslationPreferencePackage());
    }

    boolean isLyricInfoTranslationEnabled(String packageName) {
        String preferencePackage = normalizeTranslationPreferencePackage(packageName);
        ensureTranslationPreferenceLoaded(preferencePackage);
        Boolean enabled = lyricInfoTranslationEnabledByPackage.get(preferencePackage);
        return enabled == null || enabled;
    }

    void ensureTranslationPreferenceLoaded() {
        ensureTranslationPreferenceLoaded(currentTranslationPreferencePackage());
    }

    void ensureTranslationPreferenceLoaded(String packageName) {
        String preferencePackage = normalizeTranslationPreferencePackage(packageName);
        if (loadedTranslationPreferencePackages.contains(preferencePackage)) {
            return;
        }
        synchronized (this) {
            if (loadedTranslationPreferencePackages.contains(preferencePackage)) {
                return;
            }
            Context context = currentApplicationContext();
            if (context == null) {
                officialLyricTextRenderer.setTranslationEnabledImmediately(
                        isLyricInfoTranslationEnabledFromCache(preferencePackage));
                return;
            }
            SharedPreferences preferences = context.getSharedPreferences(
                    TRANSLATION_PREFERENCES_NAME,
                    Context.MODE_PRIVATE);
            boolean defaultValue = defaultTranslationPreferenceValue(
                    preferences,
                    preferencePackage);
            boolean enabled = preferences.getBoolean(
                    translationPreferenceKeyForPackage(preferencePackage),
                    defaultValue);
            lyricInfoTranslationEnabledByPackage.put(preferencePackage, enabled);
            loadedTranslationPreferencePackages.add(preferencePackage);
            if (shouldApplyTranslationPreferenceToRenderer(preferencePackage)) {
                officialLyricTextRenderer.setTranslationEnabledImmediately(enabled);
            }
        }
    }

    boolean shouldApplyTranslationPreferenceToRenderer(String packageName) {
        String currentPackage = currentTranslationPreferencePackage();
        return TextUtils.isEmpty(currentPackage)
                || normalizeTranslationPreferencePackage(packageName).equals(currentPackage);
    }

    boolean isLyricInfoTranslationEnabledFromCache(String packageName) {
        Boolean enabled = lyricInfoTranslationEnabledByPackage.get(
                normalizeTranslationPreferencePackage(packageName));
        return enabled == null || enabled;
    }

    static String normalizeTranslationPreferencePackage(String packageName) {
        return TextUtils.isEmpty(packageName) ? "" : packageName;
    }

    static String translationPreferenceKeyForPackage(String packageName) {
        String preferencePackage = normalizeTranslationPreferencePackage(packageName);
        return TextUtils.isEmpty(preferencePackage)
                ? TRANSLATION_PREFERENCE_KEY
                : TRANSLATION_PREFERENCE_KEY + "." + preferencePackage;
    }

    boolean defaultTranslationPreferenceValue(
            SharedPreferences preferences,
            String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return host.defaultTranslationEnabled();
        }
        return preferences.getBoolean(
                LyricUiSettings.translationDefaultKeyForPackage(
                        normalizeTranslationPreferencePackage(packageName)),
                host.defaultTranslationEnabled());
    }

    boolean userWantsTranslationButton(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return defaultTranslationButtonEnabled();
        }
        Boolean cached = translationButtonEnabledByPackage.get(packageName);
        if (cached != null) {
            return cached;
        }
        Context context = currentApplicationContext();
        if (context == null) {
            return defaultTranslationButtonEnabled();
        }
        boolean enabled = context.getSharedPreferences(
                        TRANSLATION_PREFERENCES_NAME,
                        Context.MODE_PRIVATE)
                .getBoolean(
                        LyricUiSettings.translationButtonKeyForPackage(packageName),
                        defaultTranslationButtonEnabled());
        translationButtonEnabledByPackage.put(packageName, enabled);
        return enabled;
    }

    boolean defaultTranslationButtonEnabled() {
        Context context = currentApplicationContext();
        if (context == null) {
            return true;
        }
        return LyricUiSettings.defaultTranslationButtonEnabled(
                context.getSharedPreferences(
                        TRANSLATION_PREFERENCES_NAME,
                        Context.MODE_PRIVATE));
    }
}
