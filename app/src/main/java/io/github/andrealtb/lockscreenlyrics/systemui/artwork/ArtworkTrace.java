package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.os.SystemClock;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.StructuredBridgeLog;

import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Diagnostic-only owner IDs and local token references. No raw metadata, token or entry keys. */
public final class ArtworkTrace {
    private static final AtomicLong OWNERS = new AtomicLong();
    private static final WeakHashMap<Object, Long> TOKENS = new WeakHashMap<>();
    private static long tokenSequence;
    private static final ArtworkTrace OBSERVERS = new ArtworkTrace("observer");
    private final ArtworkDiagnosticGate gate = new ArtworkDiagnosticGate();
    public final long ownerId = OWNERS.incrementAndGet();
    private final String scope;

    public ArtworkTrace(String scope) { this.scope = scope; }

    public void state(String event, Supplier<String> detail) {
        if (!enabled()) return;
        try {
            String text = detail.get();
            if (!gate.accept(event, text, SystemClock.elapsedRealtime())) return;
            int limited = gate.takeLimited();
            StructuredBridgeLog.debugTransition(BridgeDebugArea.MEDIA, event,
                    () -> "ownerId=" + ownerId + " scope=" + scope + " " + text
                            + (limited == 0 ? "" : " diagnosticLimited=" + limited));
        } catch (Throwable ignored) {
            // Logging and diagnostic bookkeeping never alter a binding decision.
        }
    }

    public static boolean enabled() { return StructuredBridgeLog.isAreaEnabled(BridgeDebugArea.MEDIA); }

    public static synchronized long tokenRef(Object token) {
        if (!enabled() || token == null) return 0;
        Long known = TOKENS.get(token);
        if (known != null) return known;
        if (TOKENS.size() >= 64) TOKENS.clear();
        long reference = ++tokenSequence;
        TOKENS.put(token, reference);
        return reference;
    }

    public static String errorType(Throwable error) {
        for (int depth = 0; depth < 4 && error instanceof java.lang.reflect.InvocationTargetException
                && error.getCause() != null; depth++) error = error.getCause();
        return error == null ? "none" : error.getClass().getSimpleName();
    }

    public static void observerFailure(String hookId, Throwable error) {
        OBSERVERS.state("ARTWORK_OBSERVER_ERROR", () -> "hook=" + hookId + " errorType=" + errorType(error));
    }
}
