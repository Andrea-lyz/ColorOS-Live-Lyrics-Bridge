package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;
import static io.github.andrealtb.lockscreenlyrics.LyricMetadataSupport.*;
import static io.github.andrealtb.lockscreenlyrics.LyricLogDescriptions.*;
import static io.github.andrealtb.lockscreenlyrics.LyricsRecyclerViews.*;

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
 * KuWo (KWMusic) compatibility on the SystemUI side (F-2a, plan §15.8.19): the artwork snapshot,
 * media-data artwork capture and Seedling artwork hooks, the car-lyric metadata hooks, the KuWo
 * artwork lookup, and the OPlus plugin media-model hook that repairs album art and restores the
 * lyric-support flag. It also owns the plugin DexKit resolution the media-model hook performs
 * (media-model targets and the legacy lyric position controller), which a new plugin generation
 * resets through {@link #resetForPluginGeneration}.
 *
 * <p>Moved verbatim from {@code LockscreenLyricsModule}; the only body edits are reads of the
 * SystemUI track metadata and one write of the metadata-refresh method, which go through
 * {@link Host}.
 */
final class KuWoSystemUiCompat {
    /** What the compatibility hooks read from, or hand to, the rest of the SystemUI runtime. */
    interface Host {
        XposedInterface.HookBuilder hook(java.lang.reflect.Executable origin);

        void info(BridgeDebugArea area, String event, String message);

        void infoAlways(BridgeDebugArea area, String event, String message);

        void warn(LyricLogFormatter.Area area, String event, String message);

        void artworkProbe(String stage, Supplier<String> details);

        void rememberOplusMediaDataManager(Object manager);

        String lastSystemUiSongName();

        String lastSystemUiArtistName();

        String lastSystemUiMediaId();

        void setSystemUiMetadataRefreshMethod(Method method);

        Object onOplusMediaDataMetadataChanged(XposedInterface.Chain chain) throws Throwable;

        Object onLegacyMediaDataLoad(XposedInterface.Chain chain) throws Throwable;
    }

    private final Host host;
    private final PluginHookGenerationManager pluginGeneration;
    final KuWoSystemUiRuntime kuWoRuntime = new KuWoSystemUiRuntime();
    private volatile boolean oplusPluginMediaModelHookInstalled;
    private volatile OplusPluginDexKitAdapter.Targets oplusPluginKuWoTargets;
    /** Immersive controller entry resolved in the same DexKit pass as the media-model targets. */
    volatile Method resolvedLyricPositionController;

    KuWoSystemUiCompat(Host host, PluginHookGenerationManager pluginGeneration) {
        this.host = host;
        this.pluginGeneration = pluginGeneration;
    }

    /** A new plugin ClassLoader generation: forget the previous generation's resolution. */
    void resetForPluginGeneration() {
        oplusPluginMediaModelHookInstalled = false;
        oplusPluginKuWoTargets = null;
        resolvedLyricPositionController = null;
    }

    private XposedInterface.HookBuilder hook(java.lang.reflect.Executable origin) {
        return host.hook(origin);
    }

    private void info(BridgeDebugArea area, String event, String message) {
        host.info(area, event, message);
    }

    private void infoAlways(BridgeDebugArea area, String event, String message) {
        host.infoAlways(area, event, message);
    }

    private void warn(LyricLogFormatter.Area area, String event, String message) {
        host.warn(area, event, message);
    }

    private void artworkProbe(String stage, Supplier<String> details) {
        host.artworkProbe(stage, details);
    }

    private void rememberOplusMediaDataManager(Object manager) {
        host.rememberOplusMediaDataManager(manager);
    }

    private Object onOplusMediaDataMetadataChanged(XposedInterface.Chain chain) throws Throwable {
        return host.onOplusMediaDataMetadataChanged(chain);
    }

    private Object onLegacyMediaDataLoad(XposedInterface.Chain chain) throws Throwable {
        return host.onLegacyMediaDataLoad(chain);
    }

    /**
     * KuWo publishes its cover through an http artwork URI that ColorOS refuses to load and
     * a Bitmap lane that does not survive the MediaSession binder crossing, so every
     * metadata-driven MediaData rebuild resolves the artwork to null and the immersive card
     * falls back to the default solid-color art. Capture the last non-null same-track icon
     * seen at the common lookup point and hand it back when the metadata-derived lookup is
     * empty again. Real track changes miss the snapshot and keep native behavior.
     */
    void installKuWoArtworkSnapshotHook(Class<?> ownerClass) {
        try {
            Method getArtwork = null;
            Class<?> current = ownerClass;
            while (current != null && getArtwork == null) {
                for (Method candidate : current.getDeclaredMethods()) {
                    if ("getArtWorkIconByMediaMetaData".equals(candidate.getName())
                            && candidate.getParameterCount() == 2
                            && candidate.getParameterTypes()[0] == MediaMetadata.class
                            && candidate.getParameterTypes()[1] == String.class) {
                        getArtwork = candidate;
                        break;
                    }
                }
                current = current.getSuperclass();
            }
            if (getArtwork == null) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "Skipped KuWo artwork snapshot hook:"
                                + " getArtWorkIconByMediaMetaData not found");
                return;
            }
            getArtwork.setAccessible(true);
            hook(getArtwork)
                    .setId(HOOK_ID_SYSTEMUI_ARTWORK_ICON)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onSystemUiKuWoArtworkLookup);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked SystemUI media-metadata artwork lookup for KuWo cover snapshot: "
                    + getArtwork.getDeclaringClass().getName());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook KuWo artwork snapshot: " + t);
        }
    }

    /**
     * C17 removed the 2-argument metadata lookup. Capture the artwork that the live media-data
     * pipeline already resolved (for example from the player's MediaSession) so the same-track
     * Seedling repair keeps a valid source without rebuilding or downloading a cover.
     */
    void installKuWoMediaDataArtworkCaptureHook(Class<?> ownerClass) {
        try {
            Method onMediaDataLoaded = null;
            for (Method candidate : ownerClass.getDeclaredMethods()) {
                if (!"onMediaDataLoaded".equals(candidate.getName())
                        || candidate.getParameterCount() != 3) {
                    continue;
                }
                Class<?>[] types = candidate.getParameterTypes();
                if (types[0] == String.class
                        && types[1] == String.class
                        && "com.android.systemui.media.controls.shared.model.MediaData"
                        .equals(types[2].getName())) {
                    onMediaDataLoaded = candidate;
                    break;
                }
            }
            if (onMediaDataLoaded == null) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "Skipped KuWo media-data artwork capture: onMediaDataLoaded not found");
                return;
            }
            onMediaDataLoaded.setAccessible(true);
            hook(onMediaDataLoaded)
                    .setId(HOOK_ID_SYSTEMUI_ARTWORK_ICON + "-media-data")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onKuWoMediaDataArtworkLoaded);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED,
                    "Hooked live media-data artwork capture for KuWo cover snapshot: "
                            + onMediaDataLoaded.getDeclaringClass().getName());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook KuWo media-data artwork capture: " + t);
        }
    }

    private Object onKuWoMediaDataArtworkLoaded(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            Object data = chain.getArg(2);
            if (data == null
                    || !KuWoMediaIdentityPolicy.isPlayerPackage(
                    invokeStringGetter(data, "getPackageName"))) {
                return result;
            }
            Object artwork = invokeNoArgByName(data, "getArtwork");
            if (!(artwork instanceof Icon) || !isPlausibleKuWoCoverIcon((Icon) artwork)) {
                return result;
            }
            Object mediaDataEx = invokeNoArgByName(data, "getMediaDataEx");
            Object metadata = mediaDataEx == null
                    ? null
                    : invokeNoArgByName(mediaDataEx, "getMetadata");
            if (metadata instanceof MediaMetadata) {
                rememberKuWoArtworkSnapshots((MediaMetadata) metadata, (Icon) artwork);
            } else {
                rememberKuWoArtworkSnapshot(
                        buildTrackKey(
                                invokeStringGetter(data, "getSong"),
                                invokeStringGetter(data, "getArtist")),
                        (Icon) artwork);
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo media-data artwork capture failed: " + t);
        }
        return result;
    }

    void installKuWoSeedlingArtworkHook(ClassLoader classLoader) {
        try {
            Class<?> dataClass = classLoader.loadClass(
                    "com.oplus.systemui.seedlingservice.mediaControl.SeedlingMediaData");
            Method setArtwork = dataClass.getDeclaredMethod(
                    "setArtworkIcon",
                    Icon.class);
            setArtwork.setAccessible(true);
            hook(setArtwork)
                    .setId(HOOK_ID_SYSTEMUI_SEEDLING_ARTWORK)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onKuWoSeedlingArtworkChanged);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked Seedling artwork for KuWo same-track cover repair");
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook Seedling artwork: " + t);
        }
    }

    private Object onKuWoSeedlingArtworkChanged(XposedInterface.Chain chain)
            throws Throwable {
        Object data = null;
        boolean traceLxArtwork = false;
        Object[] proceedArgs = null;
        try {
            data = chain.getThisObject();
            if (data != null && isLxSeedlingData(data)) {
                Object traceData = data;
                Icon incoming = chain.getArg(0) instanceof Icon ? (Icon) chain.getArg(0) : null;
                artworkProbe("SEEDLING_SET_IN", () -> ArtworkDiagnostics.describeSeedling(traceData)
                        + " | incoming=" + ArtworkDiagnostics.describeIcon(incoming));
                traceLxArtwork = true;
            } else if (data != null && isKuwoSeedlingData(data)) {
                String title = invokeStringGetter(data, "getSong");
                String artist = invokeStringGetter(data, "getArtist");
                String trackKey = buildTrackKey(title, artist);
                Icon incoming = chain.getArg(0) instanceof Icon ? (Icon) chain.getArg(0) : null;
                Icon snapshot = peekKuWoArtworkSnapshot(trackKey, host.lastSystemUiMediaId());
                if (KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                        isPlausibleKuWoCoverIcon(incoming),
                        trackKey,
                        buildTrackKey(host.lastSystemUiSongName(), host.lastSystemUiArtistName()),
                        snapshot != null)) {
                    long now = SystemClock.elapsedRealtime();
                    if (kuWoRuntime.takeSeedlingLog(now)) {
                        info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED,
                                "Repaired Seedling album artwork from the same-track snapshot;"
                                        + " incoming=" + describeSeedlingIcon(incoming)
                                        + ", repaired=" + describeSeedlingIcon(snapshot));
                    }
                    proceedArgs = chain.getArgs().toArray(new Object[0]);
                    proceedArgs[0] = snapshot;
                }
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo Seedling artwork repair failed: " + t);
        }
        Object result = HookProceed.once(chain, proceedArgs);
        if (traceLxArtwork && data != null) {
            Object tracedData = data;
            try {
                artworkProbe("SEEDLING_SET_OUT", () -> ArtworkDiagnostics.describeSeedling(tracedData));
            } catch (Throwable ignored) {
                // Diagnostics never own the host result.
            }
        }
        return result;
    }

    void installKuWoCarLyricMetadataHook(Class<?> ownerClass, Method metadataChanged) {
        try {
            Method changed = metadataChanged;
            if (changed == null || SystemUiDexKitAdapter.metadataParameterIndex(changed) < 0) {
                Class<?> current = ownerClass;
                while (current != null && changed == null) {
                    changed = SystemUiDexKitAdapter.findMetadataChangedEntry(current);
                    current = current.getSuperclass();
                }
            }
            if (changed == null) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "Skipped KuWo car-lyric metadata hook: onMetaDataChanged not found");
                return;
            }
            changed.setAccessible(true);
            if (SystemUiDexKitAdapter.isReplayableMetadataChanged(changed)) {
                host.setSystemUiMetadataRefreshMethod(changed);
            }
            hook(changed)
                    .setId(HOOK_ID_MEDIA_DATA_METADATA_CHANGED)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onOplusMediaDataMetadataChanged);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus media metadata changed for KuWo car-lyric identity: "
                    + changed.getDeclaringClass().getName()
                    + "#"
                    + changed.getName()
                    + " params="
                    + changed.getParameterCount());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook KuWo car-lyric metadata identity: " + t);
        }
    }

    void installKuWoCarLyricLoadMetadataHook(
            ClassLoader classLoader,
            String className) {
        try {
            Context context = currentApplicationContext();
            Class<?> managerClass = classLoader.loadClass(className);
            Method entry = null;
            for (Method method : managerClass.getDeclaredMethods()) {
                if ("loadMediaDataInBg".equals(method.getName())
                        && method.getParameterCount() == 4
                        && method.getParameterTypes()[0] == String.class
                        && method.getParameterTypes()[1] == StatusBarNotification.class
                        && method.getParameterTypes()[2] == String.class
                        && method.getParameterTypes()[3] == boolean.class) {
                    entry = method;
                    break;
                }
            }
            if (entry == null) {
                // C17 loads the same notification through onNotificationAddedEx; its second
                // parameter is the same StatusBarNotification the handler already consumes.
                entry = findDeclaredMethodOrNull(
                        managerClass,
                        "onNotificationAddedEx",
                        String.class,
                        StatusBarNotification.class);
            }
            if (entry == null) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "Skipped KuWo car-lyric load hook: no notification metadata entry");
                return;
            }
            entry.setAccessible(true);
            hook(entry)
                    .setId(HOOK_ID_MEDIA_DATA_LOAD_METADATA)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::onLegacyMediaDataLoad);
            infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked notification media loader for KuWo car-lyric identity: "
                    + managerClass.getName() + "#" + entry.getName());
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to hook legacy media data loader: " + t);
        }
    }

    private Object onSystemUiKuWoArtworkLookup(XposedInterface.Chain chain) throws Throwable {
        boolean kuwoLookup = false;
        MediaMetadata effectiveMetadata = null;
        Object[] proceedArgs = null;
        try {
            Object packageNameArg = chain.getArg(1);
            kuwoLookup = packageNameArg instanceof String
                    && KuWoMediaIdentityPolicy.isPlayerPackage((String) packageNameArg);
            if (kuwoLookup && chain.getArg(0) instanceof MediaMetadata) {
                effectiveMetadata = (MediaMetadata) chain.getArg(0);
            }
            if (kuwoLookup && effectiveMetadata != null
                    && KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                    host.lastSystemUiSongName(),
                    host.lastSystemUiArtistName(),
                    firstNonEmpty(
                            getText(effectiveMetadata, MediaMetadata.METADATA_KEY_TITLE),
                            getText(effectiveMetadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)),
                    firstNonEmpty(
                            getText(effectiveMetadata, MediaMetadata.METADATA_KEY_ARTIST),
                            getText(effectiveMetadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)))) {
                effectiveMetadata = buildMetadataWithStableSystemUiIdentity(
                        effectiveMetadata,
                        host.lastSystemUiSongName(),
                        host.lastSystemUiArtistName());
                proceedArgs = chain.getArgs().toArray(new Object[0]);
                proceedArgs[0] = effectiveMetadata;
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo artwork snapshot preprocessing failed: " + t);
        }

        Object result = HookProceed.once(chain, proceedArgs);
        if (!kuwoLookup) {
            return result;
        }
        try {
            String trackKey = kuWoArtworkSnapshotKey(effectiveMetadata);
            if (TextUtils.isEmpty(trackKey)) {
                return result;
            }
            if (result instanceof Icon && isPlausibleKuWoCoverIcon((Icon) result)) {
                rememberKuWoArtworkSnapshots(effectiveMetadata, (Icon) result);
                return result;
            }
            Icon snapshot = peekKuWoArtworkSnapshot(trackKey);
            if (snapshot != null) {
                if (kuWoRuntime.takeArtworkRestoreLogOnce()) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, "Restored KuWo album artwork from same-track snapshot after"
                            + (result == null ? " empty" : " implausible")
                            + " metadata lookup");
                }
                return snapshot;
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo artwork snapshot handling failed: " + t);
        }
        return result;
    }

    private static String kuWoArtworkSnapshotKey(MediaMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        return KuWoCoverPolicy.artworkSnapshotKey(
                metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                buildTrackKey(
                        firstNonEmpty(
                                getText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)),
                        firstNonEmpty(
                                getText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))));
    }

    static boolean isPlausibleKuWoCoverIcon(Icon icon) {
        try {
            return KuWoCoverPolicy.isPlausibleCoverIconBitmap(extractIconBitmap(icon));
        } catch (Throwable ignored) {
            return true;
        }
    }

    void rememberKuWoArtworkSnapshots(MediaMetadata metadata, Icon icon) {
        if (metadata == null || icon == null) {
            return;
        }
        kuWoRuntime.artwork().rememberKeys(
                kuWoArtworkSnapshotKey(metadata),
                buildTrackKey(
                        firstNonEmpty(
                                getText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)),
                        firstNonEmpty(
                                getText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                                getText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))),
                icon);
    }

    private void rememberKuWoArtworkSnapshot(String trackKey, Icon icon) {
        if (TextUtils.isEmpty(trackKey) || icon == null) {
            return;
        }
        kuWoRuntime.artwork().remember(trackKey, icon);
    }

    private Icon peekKuWoArtworkSnapshot(String trackKey) {
        return kuWoRuntime.artwork().peek(trackKey);
    }

    private Icon peekKuWoArtworkSnapshot(String trackKey, String expectedStrongKey) {
        return kuWoRuntime.artwork().peekIfStrongIdentity(trackKey, expectedStrongKey);
    }

    void tryInstallKuWoPluginMediaModelHook(ClassLoader pluginLoader) {
        if (oplusPluginMediaModelHookInstalled || pluginLoader == null) {
            return;
        }
        try {
            OplusPluginDexKitAdapter.Targets targets;
            try {
                OplusPluginDexKitAdapter.Resolution resolution =
                        OplusPluginDexKitAdapter.resolveAll(pluginLoader, LYRICS_RECYCLER_VIEW_CLASS);
                // Captured before the media-model check so a model miss cannot drop it.
                resolvedLyricPositionController = resolution.lyricPositionController;
                if (resolution.lyricPositionController == null) {
                    // The legacy timed controller is optional. ColorOS 17 aligns through the
                    // immersive entry instead, so this is not a whole-clock failure.
                    infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.DETAIL,
                            "Legacy timed lyric controller absent; using entry/row-style alignment: "
                                    + resolution.lyricPositionControllerFailure);
                }
                targets = resolution.requireTargets();
            } catch (Throwable dexKitFailure) {
                warn(
                        LyricLogFormatter.Area.SYSTEM_UI,
                        "lyric-policy",
                        "OPlus plugin DexKit resolution failed, using m6 fallback: "
                                + dexKitFailure);
                targets = OplusPluginDexKitAdapter.legacy(pluginLoader);
            }
            Class<?> modelClass = targets.mediaModelClass;
            oplusPluginKuWoTargets = targets;
            Constructor<?>[] constructors = modelClass.getDeclaredConstructors();
            int hooked = 0;
            for (Constructor<?> constructor : constructors) {
                constructor.setAccessible(true);
                XposedInterface.HookHandle handle = hook(constructor)
                        .setId(HOOK_ID_PLUGIN_CLASS_LOADER_CONSTRUCTOR
                                + "-kuwo-model-" + hooked + "-"
                                + constructor.getParameterTypes().length)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(this::onKuWoPluginMediaModelBuilt);
                pluginGeneration.rememberPluginHookHandle(pluginLoader, handle);
                hooked++;
            }
            if (hooked > 0) {
                oplusPluginMediaModelHookInstalled = true;
                infoAlways(BridgeDebugArea.BOOTSTRAP, BridgeEvents.HOOK_INSTALLED, "Hooked OPlus KuWo plugin media model, constructors=" + hooked
                        + ", resolver=" + (targets.resolvedByDexKit ? "dexkit" : "legacy")
                        + ", lyricSupportedField=" + targets.lyricSupportedField.getName()
                        + ", albumArtRepair=" + (targets.artwork != null
                                ? "on"
                                : "off: " + targets.artworkFailure));
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Skipped KuWo plugin media model hook: " + t);
        }
    }

    private Object onKuWoPluginMediaModelBuilt(XposedInterface.Chain chain) throws Throwable {
        Object model = chain.proceed();
        if (model == null) {
            model = chain.getThisObject();
        }
        if (model == null || !KuWoPluginMediaModelReader.containsPlayerPackage(model)) {
            return model;
        }
        try {
            OplusPluginDexKitAdapter.Targets targets = oplusPluginKuWoTargets;
            String description = String.valueOf(model);
            String title = readKuWoMediaModelText(
                    model,
                    description,
                    "h",
                    "f6173h",
                    "songName=",
                    ", artist=");
            String artist = readKuWoMediaModelText(
                    model,
                    description,
                    "i",
                    "f6174i",
                    "artist=",
                    ", iconBeforeArtist=");
            // A missing label counts as supported so it can never trigger a restore.
            boolean lyricSupported = !Boolean.FALSE.equals(
                    KuWoPluginMediaModelReader.readLyricSupported(description));
            String trackKey = buildTrackKey(title, artist);
            Field lyricField = targets == null
                    ? findFieldOfType(model.getClass(), "s")
                    : targets.lyricModelField;
            Object lyricModel = lyricField == null ? null : readField(model, lyricField);
            int lineCount = countLyricModelLines(lyricModel);
            boolean albumArtRepaired = false;
            KuWoSameTrackLyricRetention retention = kuWoRuntime.retention();
            synchronized (retention.lock()) {
                KuWoSameTrackLyricRetention.Result result = retention.decide(
                        trackKey,
                        title,
                        artist,
                        lyricModel,
                        lineCount,
                        lyricSupported);
                if (result.repairAlbumArt) {
                    albumArtRepaired = repairKuWoPluginAlbumArt(
                            model,
                            result.repairTitle,
                            result.repairArtist);
                }
                boolean lyricSupportRestored = false;
                if (result.lyricToRestore != null) {
                    if (lyricField != null) {
                        lyricField.setAccessible(true);
                        lyricField.set(model, result.lyricToRestore);
                    }
                    setKuWoPluginLyricSupported(model, targets);
                    lineCount = countLyricModelLines(result.lyricToRestore);
                } else if (result.restoreLyricSupport) {
                    lyricSupportRestored = setKuWoPluginLyricSupported(model, targets);
                }
                // Commit even when repairAlbumArt returned false. Repair swallows
                // failures; skipping commit here would drop same-track memory.
                retention.commit(result, trackKey, title, artist, lyricModel);
                long now = SystemClock.elapsedRealtime();
                if (result.action == KuWoSameTrackLyricRetention.Action.RETAIN_CAR_LYRIC_MUTATION
                        && kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.SESSION_REDUCED, "Ignored KuWo car-lyric metadata mutation while retaining"
                            + " same-track plugin model, lines=" + lineCount);
                } else if (result.action
                        == KuWoSameTrackLyricRetention.Action.CLEAR_FOR_TRACK_CHANGE
                        && kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Cleared KuWo plugin lyric retention for real track change: "
                            + trackKey);
                } else if (result.action
                        == KuWoSameTrackLyricRetention.Action.RESTORE_EMPTY_MODEL
                        && kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Retained same-track KuWo plugin lyric model, lines=" + lineCount);
                } else if (lyricSupportRestored && kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Restored same-track KuWo plugin lyric support, lines=" + lineCount);
                }
            }
            if (albumArtRepaired) {
                long now = SystemClock.elapsedRealtime();
                if (kuWoRuntime.takeMediaModelLog(now)) {
                    info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Repaired KuWo plugin album art from same-track snapshot, "
                            + "lines=" + lineCount);
                }
            }
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo plugin media model retention failed: " + t);
        }
        return model;
    }

    private boolean repairKuWoPluginAlbumArt(
            Object model,
            String title,
            String artist) {
        OplusPluginDexKitAdapter.Targets targets = oplusPluginKuWoTargets;
        if (targets == null) {
            logKuWoPluginAlbumArtSkip(title, artist, "missing-plugin-targets");
            return false;
        }
        // The install log already reports why repair is off for this plugin build.
        OplusPluginDexKitAdapter.Artwork artwork = targets.artwork;
        if (artwork == null) {
            return false;
        }
        Field albumArtField = artwork.albumArtField;
        Object albumArt = readField(model, albumArtField);
        if (albumArt == null) {
            logKuWoPluginAlbumArtSkip(title, artist, "no-album-art");
            return false;
        }
        try {
            Object staticIcon = artwork.staticIconGetter.invoke(albumArt);
            if (staticIcon == null) {
                return false;
            }
            Object lottie = artwork.lottieIconGetter.invoke(albumArt);
            Field drawableField = artwork.staticDrawableField;
            Field bitmapField = artwork.staticBitmapField;
            Field cardModelField = artwork.cardIconModelField;
            Object cardModel = readField(staticIcon, cardModelField);
            Bitmap cardBitmap = cardModel == null
                    ? null
                    : (Bitmap) artwork.iconModelBitmapGetter.invoke(cardModel);
            Object staticBitmapValue = readField(staticIcon, bitmapField);
            Bitmap staticBitmap = staticBitmapValue instanceof Bitmap
                    ? (Bitmap) staticBitmapValue
                    : null;
            Object staticDrawableValue = readField(staticIcon, drawableField);
            Drawable staticDrawable = staticDrawableValue instanceof Drawable
                    ? (Drawable) staticDrawableValue
                    : null;
            boolean cardInvalid = !KuWoCoverPolicy.isPlausibleCoverBitmap(cardBitmap);
            boolean staticBitmapInvalid = !KuWoCoverPolicy.isPlausibleCoverBitmap(staticBitmap);
            boolean drawableInvalid = staticDrawable != null
                    && !KuWoCoverPolicy.isPlausibleCoverSize(
                            staticDrawable.getIntrinsicWidth(),
                            staticDrawable.getIntrinsicHeight());
            if (!cardInvalid && !staticBitmapInvalid && !drawableInvalid) {
                return false;
            }
            Icon snapshot = peekKuWoArtworkSnapshot(
                    buildTrackKey(title, artist), host.lastSystemUiMediaId());
            if (snapshot == null) {
                logKuWoPluginAlbumArtSkip(title, artist, "no-snapshot");
                return false;
            }
            Bitmap bitmap = extractIconBitmap(snapshot);
            if (!KuWoCoverPolicy.isPlausibleCoverBitmap(bitmap)) {
                logKuWoPluginAlbumArtSkip(title, artist, "bad-snapshot");
                return false;
            }
            Drawable repairedDrawable = snapshot.loadDrawable(currentApplicationContext());
            Integer primaryColor = cardModel == null
                    ? null
                    : (Integer) artwork.iconModelColorGetter.invoke(cardModel);
            Object repairedCardModel = artwork.normalIconConstructor.newInstance(
                    bitmap,
                    primaryColor);
            Object repairedMiniModel = artwork.normalIconConstructor.newInstance(
                    bitmap,
                    primaryColor);
            Object repairedStaticIcon = artwork.staticIconConstructor.newInstance(
                    snapshot,
                    repairedDrawable,
                    bitmap,
                    repairedMiniModel,
                    repairedCardModel);
            Object repairedAlbumArt = artwork.multiIconConstructor.newInstance(
                    repairedStaticIcon,
                    lottie);
            albumArtField.setAccessible(true);
            albumArtField.set(model, repairedAlbumArt);
            return true;
        } catch (Throwable t) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "Failed to repair KuWo plugin album art: " + t);
            return false;
        }
    }

    /**
     * Sets the model's lyric-support flag and confirms it through the {@code isLyricSupported=}
     * label. A write that does not reach the label is undone, so a misidentified boolean (ColorOS
     * 17 keeps artworkFullBgEnable next to the flag) is never left changed. The model is still
     * inside its constructor hook, so no other thread can observe the probe.
     */
    private boolean setKuWoPluginLyricSupported(
            Object model,
            OplusPluginDexKitAdapter.Targets targets) throws IllegalAccessException {
        if (Boolean.TRUE.equals(KuWoPluginMediaModelReader.readLyricSupported(String.valueOf(model)))) {
            return true;
        }
        Field supportedField = targets == null
                ? findBooleanSuffixField(model.getClass(), "u")
                : targets.lyricSupportedField;
        if (supportedField == null) {
            return false;
        }
        supportedField.setAccessible(true);
        boolean previous = supportedField.getBoolean(model);
        supportedField.setBoolean(model, true);
        if (Boolean.TRUE.equals(KuWoPluginMediaModelReader.readLyricSupported(String.valueOf(model)))) {
            return true;
        }
        supportedField.setBoolean(model, previous);
        if (kuWoRuntime.takeLyricSupportMismatchLogOnce()) {
            warn(
                    LyricLogFormatter.Area.SYSTEM_UI,
                    "lyric-policy",
                    "KuWo plugin lyric-support field did not match the isLyricSupported label;"
                            + " write undone, field=" + supportedField.getName());
        }
        return false;
    }

    private static String readKuWoMediaModelText(
            Object model,
            String description,
            String primaryField,
            String fallbackField,
            String label,
            String nextLabel) {
        String labeled = KuWoPluginMediaModelReader.readLabeledText(
                description,
                label,
                nextLabel);
        if (labeled != null) {
            return labeled;
        }
        return readStringFieldByName(model, primaryField, fallbackField);
    }

    private void logKuWoPluginAlbumArtSkip(String title, String artist, String reason) {
        if (!kuWoRuntime.takeMediaModelLog(SystemClock.elapsedRealtime())) {
            return;
        }
        info(BridgeDebugArea.MEDIA, BridgeEvents.DETAIL, "Skipped KuWo plugin album art repair; reason=" + reason
                + ", track=" + buildTrackKey(title, artist));
    }
}
