package io.github.andrealtb.lockscreenlyrics;

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

// Entry-class statics the moved bodies use unqualified (isModuleManagedPlayerPackage, readFieldValue...).
import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

/**
 * OPlus media policy hooks that only widen vendor allow-lists for module-managed players:
 * history whitelist and media blacklist in system_server, the AOD media-panel whitelist, the
 * RUS whitelist getter, the lyric entrance and the lyric-support gate in SystemUI. The
 * translation-button rules stay with the translation code (E-4). Each install is idempotent and
 * guarded by its own flag; {@code synchronized (this)} now locks this object, not the module.
 */
final class OplusMediaPolicyHooks {
    // Constants, state and hooks moved verbatim from LockscreenLyricsModule (E-2d1, plan §15.8.5).
    private static final String OPLUS_MEDIA_CONTROL_SERVICE_CLASS =
            "com.android.server.media.OplusMediaControlService";
    private static final String OPLUS_HISTORY_WHITELIST_METHOD =
            "isInHistoryPlayInfoWhiteList";
    private static final String OPLUS_MEDIA_BLACKLIST_METHOD = "isInMediaBlackList";
    private static final String HOOK_ID_OPLUS_HISTORY_WHITELIST =
            "oplus-media-history-whitelist-salt";
    private static final String HOOK_ID_OPLUS_MEDIA_BLACKLIST =
            "oplus-media-blacklist-bypass";
    private static final String HOOK_ID_AOD_MEDIA_SUPPORT =
            "oplus-aod-media-support";
    private static final int OPLUS_LYRIC_ENTRANCE_ALL = 52;

    private volatile boolean oplusHistoryWhitelistHookInstalled;
    private volatile boolean oplusMediaBlacklistHookInstalled;
    private volatile boolean aodMediaSupportHookInstalled;
    private final Set<String> loggedOplusHistoryIntegrationPackages =
            ConcurrentHashMap.newKeySet();
    private final Set<String> loggedOplusHistoryManifestFailures =
            ConcurrentHashMap.newKeySet();
    private final Set<String> loggedOplusBlacklistBypassPackages =
            ConcurrentHashMap.newKeySet();
    private final Set<String> loggedAodMediaSupportPackages =
            ConcurrentHashMap.newKeySet();
    private volatile boolean oplusLyricEntranceOverrideLogged;
    private volatile boolean oplusLyricSupportGateLiftLogged;

    /** What these hooks need from the module: installing hooks and the structured log. */
    interface Host {
        XposedInterface.HookBuilder hook(java.lang.reflect.Executable origin);

        void info(BridgeDebugArea area, String event, String message);

        void infoAlways(BridgeDebugArea area, String event, String message);

        void error(String message, Throwable throwable);
    }

    private final Host host;

    OplusMediaPolicyHooks(Host host) {
        this.host = host;
    }

    // Same names as the module's own methods, so the moved bodies read exactly as before.
    private XposedInterface.HookBuilder hook(java.lang.reflect.Executable origin) {
        return host.hook(origin);
    }

    private void info(BridgeDebugArea area, String event, String message) {
        host.info(area, event, message);
    }

    private void infoAlways(BridgeDebugArea area, String event, String message) {
        host.infoAlways(area, event, message);
    }

    private void error(String message, Throwable throwable) {
        host.error(message, throwable);
    }

    @SuppressLint("PrivateApi") // LSPosed hook resolves the vendor SystemUI service by name.
    void installOplusHistoryWhitelistHook(ClassLoader classLoader) {
        if (oplusHistoryWhitelistHookInstalled) {
            return;
        }
        synchronized (this) {
            if (oplusHistoryWhitelistHookInstalled) {
                return;
            }
            try {
                Class<?> serviceClass = classLoader.loadClass(
                        OPLUS_MEDIA_CONTROL_SERVICE_CLASS);
                Method whitelistMethod = serviceClass.getDeclaredMethod(
                        OPLUS_HISTORY_WHITELIST_METHOD,
                        String.class);
                whitelistMethod.setAccessible(true);
                hook(whitelistMethod)
                        .setId(HOOK_ID_OPLUS_HISTORY_WHITELIST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusHistoryWhitelistLookup);
                oplusHistoryWhitelistHookInstalled = true;
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus media history integration");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                info(BridgeDebugArea.BOOTSTRAP, BridgeEvents.DETAIL, "OPlus media history whitelist hook is unavailable on this system");
            } catch (Throwable t) {
                error("Failed to hook OPlus media history whitelist", t);
            }
        }
    }

    private Object onOplusHistoryWhitelistLookup(
            XposedInterface.Chain chain) throws Throwable {
        Object originalResult = chain.proceed();
        boolean alreadyWhitelisted = Boolean.TRUE.equals(originalResult);
        if (alreadyWhitelisted) {
            return originalResult;
        }
        Object packageNameArg = chain.getArg(0);
        if (!(packageNameArg instanceof String)
                || TextUtils.isEmpty((String) packageNameArg)) {
            return originalResult;
        }

        String packageName = (String) packageNameArg;
        boolean moduleManagedPackage = isModuleManagedPlayerPackage(packageName);
        boolean manifestOptIn = !moduleManagedPackage
                && isOplusHistoryIntegrationDeclared(
                        chain.getThisObject(),
                        packageName);
        if (!LockscreenIntegrationPolicy.shouldEnableOplusHistoryIntegration(
                false,
                moduleManagedPackage,
                manifestOptIn)) {
            return originalResult;
        }
        if (loggedOplusHistoryIntegrationPackages.add(packageName)) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Accepted " + packageName + " into OPlus media history"
                    + (moduleManagedPackage
                    ? " via " + moduleManagedPlayerKind(packageName) + " integration"
                    : " via manifest opt-in"));
        }
        return true;
    }

    @SuppressLint("PrivateApi") // LSPosed hook resolves the OPlus media control service by name.
    void installOplusMediaBlacklistBypassHook(ClassLoader classLoader) {
        if (oplusMediaBlacklistHookInstalled) {
            return;
        }
        synchronized (this) {
            if (oplusMediaBlacklistHookInstalled) {
                return;
            }
            try {
                Class<?> serviceClass = classLoader.loadClass(
                        OPLUS_MEDIA_CONTROL_SERVICE_CLASS);
                Method blacklistMethod = serviceClass.getDeclaredMethod(
                        OPLUS_MEDIA_BLACKLIST_METHOD,
                        String.class);
                blacklistMethod.setAccessible(true);
                hook(blacklistMethod)
                        .setId(HOOK_ID_OPLUS_MEDIA_BLACKLIST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onOplusMediaBlackListLookup);
                oplusMediaBlacklistHookInstalled = true;
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus media blacklist bypass");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                info(BridgeDebugArea.BOOTSTRAP, BridgeEvents.DETAIL, "OPlus media blacklist bypass hook is unavailable on this system");
            } catch (Throwable t) {
                error("Failed to hook OPlus media blacklist bypass", t);
            }
        }
    }

    private Object onOplusMediaBlackListLookup(
            XposedInterface.Chain chain) throws Throwable {
        Object originalResult = chain.proceed();
        boolean blacklisted = Boolean.TRUE.equals(originalResult);
        if (!blacklisted) {
            return originalResult;
        }
        Object packageNameArg = chain.getArg(0);
        if (!(packageNameArg instanceof String)
                || TextUtils.isEmpty((String) packageNameArg)) {
            return originalResult;
        }

        String packageName = (String) packageNameArg;
        boolean moduleManagedPackage = isModuleManagedPlayerPackage(packageName);
        boolean manifestOptIn = !moduleManagedPackage
                && isOplusHistoryIntegrationDeclared(
                        chain.getThisObject(),
                        packageName);
        if (!LockscreenIntegrationPolicy.shouldEnableOplusHistoryIntegration(
                false,
                moduleManagedPackage,
                manifestOptIn)) {
            return originalResult;
        }
        if (loggedOplusBlacklistBypassPackages.add(packageName)) {
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Bypassed OPlus media blacklist for " + packageName
                    + (moduleManagedPackage
                    ? " via " + moduleManagedPlayerKind(packageName) + " integration"
                    : " via manifest opt-in"));
        }
        return false;
    }

    private boolean isOplusHistoryIntegrationDeclared(
            Object service,
            String packageName) {
        Object contextValue = readFieldValue(service, "mContext");
        if (!(contextValue instanceof Context)) {
            return false;
        }

        Context context = (Context) contextValue;
        long identity = Binder.clearCallingIdentity();
        try {
            ApplicationInfo applicationInfo = context.getPackageManager()
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            Bundle metadata = applicationInfo.metaData;
            return metadata != null
                    && metadata.getBoolean(
                            LyricInfoContract.MANIFEST_METADATA_OPLUS_MEDIA_HISTORY,
                            false);
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            if (loggedOplusHistoryManifestFailures.add(packageName)) {
                error("Failed to read OPlus media history opt-in for " + packageName, t);
            }
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    void installAodMediaSupportHooks(ClassLoader classLoader) {
        if (aodMediaSupportHookInstalled) {
            return;
        }
        synchronized (this) {
            if (aodMediaSupportHookInstalled) {
                return;
            }
            try {
                Class<?> companionClass = classLoader.loadClass(
                        "com.oplusos.systemui.aod.mediapanel.AodMediaDataListener$Companion");
                int hooked = 0;
                hooked += hookAodMediaSupportMethod(companionClass, "isAodMediaSupport");
                hooked += hookAodMediaSupportMethod(
                        companionClass,
                        "isAodMediaSupportWithoutFeature");
                if (hooked > 0) {
                    aodMediaSupportHookInstalled = true;
                    infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus AOD media support whitelist, methods=" + hooked);
                }
            } catch (ClassNotFoundException e) {
                info(BridgeDebugArea.AOD, BridgeEvents.AOD_TRANSITION, "OPlus AOD media support whitelist hook is unavailable on this system");
            } catch (Throwable t) {
                error("Failed to hook OPlus AOD media support whitelist", t);
            }
        }
    }

    private int hookAodMediaSupportMethod(Class<?> companionClass, String methodName)
            throws NoSuchMethodException {
        Method method = companionClass.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        hook(method)
                .setId(HOOK_ID_AOD_MEDIA_SUPPORT + "-" + methodName)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(this::onAodMediaSupportLookup);
        return 1;
    }

    private Object onAodMediaSupportLookup(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (Boolean.TRUE.equals(result)) {
            return result;
        }
        Object packageNameArg = chain.getArg(0);
        if (!(packageNameArg instanceof String)
                || TextUtils.isEmpty((String) packageNameArg)) {
            return result;
        }

        String packageName = (String) packageNameArg;
        if (!isModuleManagedPlayerPackage(packageName)) {
            return result;
        }
        if (loggedAodMediaSupportPackages.add(packageName)) {
            info(BridgeDebugArea.AOD, BridgeEvents.AOD_TRANSITION, "Accepted " + packageName + " into OPlus AOD media panel whitelist"
                    + " via " + moduleManagedPlayerKind(packageName) + " integration");
        }
        return true;
    }

    Object onOplusMediaRusGetWhiteList(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (result instanceof OplusMediaWhitelistBypassList) {
            return result;
        }
        if (result instanceof List) {
            return new OplusMediaWhitelistBypassList((List<?>) result);
        }
        return result;
    }

    Object onOplusMediaGetLyricEntrance(XposedInterface.Chain chain) throws Throwable {
        Object packageNameArg = chain.getArg(0);
        String packageName = packageNameArg instanceof String ? (String) packageNameArg : "";
        Object result = chain.proceed();
        int original = result instanceof Number ? ((Number) result).intValue() : 0;
        int overrideResult = 0;
        if (original != 0) {
            return result;
        }
        if (isModuleManagedPlayerPackage(packageName)) {
            overrideResult = OPLUS_LYRIC_ENTRANCE_ALL;
            if (!oplusLyricEntranceOverrideLogged) {
                oplusLyricEntranceOverrideLogged = true;
                info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "OPlus lyric entrance normalized: package=" + packageName
                        + ", " + original + "->" + overrideResult);
            }
        }
        return overrideResult;
    }

    /**
     * Lifts the RUS lyricEnable gate for module-managed players only. lyricEnable 1 demands the
     * player's own {@code oplus.media.config=1} metadata (built in for com.kugou.android.lite and
     * com.gaana); these players get their lyrics from a Provider instead of that handshake. A
     * vendor {@code true} is never changed.
     */
    Object onOplusMediaResolveLyricSupport(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (!Boolean.FALSE.equals(result)) {
            return result;
        }
        Object packageNameArg = chain.getArg(0);
        String packageName = packageNameArg instanceof String ? (String) packageNameArg : "";
        if (!isModuleManagedPlayerPackage(packageName)) {
            return result;
        }
        if (!oplusLyricSupportGateLiftLogged) {
            oplusLyricSupportGateLiftLogged = true;
            info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "OPlus lyric support gate lifted: package="
                    + packageName);
        }
        return Boolean.TRUE;
    }
}
