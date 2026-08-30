package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Bridge-side structured logger. Default-off; DEBUG/INFO respect the master switch and area
 * gates. WARN/ERROR always emit. Dual sinks are logcat and the libxposed framework logger.
 */
public final class StructuredBridgeLog {
    public static final String TAG = "LockscreenLyrics";
    public static final String COMPONENT = "bridge";
    private static final long THROTTLE_WINDOW_MS = 3_000L;

    private static final CopyOnWriteArrayList<BridgeLogSink> SINKS = new CopyOnWriteArrayList<>();
    private static final DiagnosticThrottler THROTTLER = new DiagnosticThrottler(THROTTLE_WINDOW_MS);
    private static volatile BridgeDebugConfig config = BridgeDebugConfig.disabled();
    private static volatile String processName = "unknown";
    private static volatile boolean logTagForcesDebug;

    private StructuredBridgeLog() {
    }

    public static void configure(
            BridgeDebugConfig nextConfig,
            String nextProcessName,
            BridgeLogSink logcatSink,
            BridgeLogSink frameworkSink) {
        config = nextConfig == null ? BridgeDebugConfig.disabled() : nextConfig;
        if (nextProcessName != null && !nextProcessName.isEmpty()) {
            processName = nextProcessName;
        }
        SINKS.clear();
        if (logcatSink != null) {
            SINKS.add(logcatSink);
        }
        if (frameworkSink != null) {
            SINKS.add(frameworkSink);
        }
        THROTTLER.clear();
    }

    public static void setProcessName(String nextProcessName) {
        if (nextProcessName != null && !nextProcessName.isEmpty()) {
            processName = nextProcessName;
        }
    }

    public static void setLogTagForcesDebug(boolean enabled) {
        logTagForcesDebug = enabled;
    }

    public static BridgeDebugConfig config() {
        return config;
    }

    public static String processName() {
        return processName;
    }

    public static boolean isMasterEnabled() {
        return logTagForcesDebug || config.masterEnabled;
    }

    public static boolean isAreaEnabled(BridgeDebugArea area) {
        return logTagForcesDebug || config.isAreaEnabled(area);
    }

    public static void emitLegacyInfo(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        LegacyLogEventMap.Mapping mapping = LegacyLogEventMap.classify(message);
        if (!mapping.alwaysOn && !isAreaEnabled(mapping.area)) {
            return;
        }
        emit(Log.INFO, "INFO", mapping.area, mapping.event, message, null, mapping.alwaysOn);
    }

    public static void info(
            BridgeDebugArea area,
            String event,
            String message) {
        if (!isAreaEnabled(area)) {
            return;
        }
        emit(Log.INFO, "INFO", area, event, message, null, false);
    }

    public static void infoAlways(
            BridgeDebugArea area,
            String event,
            String message) {
        emit(Log.INFO, "INFO", area, event, message, null, true);
    }

    public static void debug(
            BridgeDebugArea area,
            String event,
            Supplier<String> message) {
        if (!isAreaEnabled(area) || message == null) {
            return;
        }
        emit(Log.DEBUG, "DEBUG", area, event, message.get(), null, false);
    }

    public static void warn(BridgeDebugArea area, String event, String message) {
        emit(Log.WARN, "WARN", area, event, message, null, true);
    }

    public static void error(String message, Throwable throwable) {
        LegacyLogEventMap.Mapping mapping = LegacyLogEventMap.classify(message);
        emit(Log.ERROR, "ERROR", mapping.area, BridgeEvents.FAILURE, message, throwable, true);
    }

    static void resetForTesting() {
        config = BridgeDebugConfig.disabled();
        processName = "unknown";
        logTagForcesDebug = false;
        SINKS.clear();
        THROTTLER.clear();
    }

    static void addSinkForTesting(BridgeLogSink sink) {
        if (sink != null && !SINKS.contains(sink)) {
            SINKS.add(sink);
        }
    }

    private static void emit(
            int androidLevel,
            String levelName,
            BridgeDebugArea area,
            String event,
            String message,
            Throwable throwable,
            boolean alwaysOn) {
        BridgeDebugArea safeArea = area == null ? BridgeDebugArea.BOOTSTRAP : area;
        String safeEvent = event == null || event.isEmpty() ? BridgeEvents.DETAIL : event;
        if (!alwaysOn && androidLevel < Log.WARN && !isAreaEnabled(safeArea)) {
            return;
        }
        String throttleKey = safeArea.key + "|" + safeEvent + "|" + processName;
        long now = System.currentTimeMillis();
        if (androidLevel < Log.WARN && !THROTTLER.shouldLog(throttleKey, now)) {
            return;
        }
        int suppressed = androidLevel < Log.WARN ? THROTTLER.takeSuppressed(throttleKey) : 0;
        String formatted = SensitiveFieldRedactor.redact(BridgeLogFormatter.format(
                levelName,
                COMPONENT,
                safeArea.key,
                safeEvent,
                processName,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                suppressed > 0 ? suppressed : null,
                message));
        if (SINKS.isEmpty()) {
            if (throwable != null) {
                Log.println(androidLevel, TAG, formatted);
                Log.e(TAG, formatted, throwable);
            } else {
                Log.println(androidLevel, TAG, formatted);
            }
            return;
        }
        for (BridgeLogSink sink : SINKS) {
            try {
                sink.log(androidLevel, TAG, formatted, throwable);
            } catch (Throwable ignored) {
                // Logging must never crash SystemUI.
            }
        }
    }
}
