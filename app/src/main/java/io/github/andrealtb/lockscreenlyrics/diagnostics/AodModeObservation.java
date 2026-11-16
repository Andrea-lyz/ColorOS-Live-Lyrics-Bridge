package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.os.Bundle;

/**
 * Renderer-side AOD low-frame-rate mode transitions, recorded only where the renderer actually
 * switches mode. Observation only: it never reads system AOD state and never changes the mode.
 * Writes happen on a real transition, not per frame, and allocate nothing.
 */
public final class AodModeObservation {
    public static final String TRANSITIONS = "cll_aod_mode_transitions";
    public static final String CHANGED_AT = "cll_aod_mode_changed_at";
    public static final String CURRENT = "cll_aod_mode_current";
    private static final AodModeObservation GLOBAL = new AodModeObservation();

    private int transitions;
    private long changedAt = -1L;
    private int current = -1; // -1 unknown, 0 off, 1 on

    public static AodModeObservation global() { return GLOBAL; }

    /** Called after the renderer has accepted a mode change. */
    public synchronized void changed(boolean enabled, long elapsedRealtimeMs) {
        int next = enabled ? 1 : 0;
        if (next == current) return;
        current = next;
        changedAt = elapsedRealtimeMs;
        if (transitions < Integer.MAX_VALUE) transitions++;
    }

    public synchronized void putStatus(Bundle result) {
        result.putInt(TRANSITIONS, transitions);
        result.putLong(CHANGED_AT, changedAt);
        result.putString(CURRENT, current < 0 ? "unknown" : current == 1 ? "true" : "false");
    }

    synchronized void resetForTest() { transitions = 0; changedAt = -1L; current = -1; }
}
