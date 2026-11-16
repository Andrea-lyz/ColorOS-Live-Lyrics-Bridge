package io.github.andrealtb.lockscreenlyrics;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;
import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.Feature;
import io.github.andrealtb.lockscreenlyrics.diagnostics.StructuredBridgeLog;

/**
 * Feature-level degradation (C-04, plan §4). Each enhancement keeps its own narrow guard (a
 * failed binding, a renderer latch, a null construction result); this ledger adds the level above
 * them: once a feature has failed {@link #threshold} times in the current SystemUI plugin
 * component generation, it stops being attempted and its official/manual fallback is used.
 *
 * <ul>
 *   <li>Isolation: one feature's failures never disable another feature.</li>
 *   <li>Recovery is bound to explicit events, never to time or frames: a new plugin ClassLoader
 *       generation (new component code) clears everything; a real change of the lyric settings
 *       clears that feature at most {@link #MAX_SETTINGS_RECOVERIES} times per generation.</li>
 *   <li>A single exception never disables a feature permanently: the counter needs several
 *       failures, and a new generation or a SystemUI restart starts from zero.</li>
 * </ul>
 *
 * <p>Reads ({@link #allowed}) run on draw paths, so they read one volatile mask and take no lock.
 */
public final class FeatureDegradation {
    static final int MAX_SETTINGS_RECOVERIES = 2;
    private static final FeatureDegradation GLOBAL = new FeatureDegradation(true);

    private final boolean log;
    private final int[] failures = new int[Feature.values().length];
    private final int[] settingsRecoveries = new int[Feature.values().length];
    private volatile int degradedMask;

    FeatureDegradation(boolean log) {
        this.log = log;
    }

    public static FeatureDegradation global() {
        return GLOBAL;
    }

    /**
     * Failures tolerated before a feature is paused. Per-binding guards already absorb one-off
     * errors, so the custom draw / lift / glow budgets count distinct failures across bindings.
     * Synthetic translation-action construction is deterministic for a component version, so it
     * gives up after a second identical failure instead of repeating reflection on every update.
     */
    static int threshold(Feature feature) {
        switch (feature) {
            case TRANSLATION_BUTTON:
                return 2;
            default:
                return 3;
        }
    }

    public boolean allowed(Feature feature) {
        return (degradedMask & bit(feature)) == 0;
    }

    /** Records one failure; returns true when this failure paused the feature. */
    public boolean failed(Feature feature, String cause) {
        boolean tripped;
        int count;
        synchronized (this) {
            int index = feature.ordinal();
            if ((degradedMask & bit(feature)) != 0) return false;
            count = ++failures[index];
            tripped = count >= threshold(feature);
            if (tripped) degradedMask |= bit(feature);
        }
        if (tripped && log) {
            StructuredBridgeLog.warn(BridgeDebugArea.RENDERER, BridgeEvents.FEATURE_DEGRADED,
                    "feature=" + feature.name() + ", failures=" + count
                            + ", cause=" + (cause == null ? "unknown" : cause)
                            + ", recovery=plugin-generation|settings");
        }
        return tripped;
    }

    /** A new SystemUI plugin ClassLoader became active: new component code, new budget. */
    synchronized void onComponentGeneration() {
        int mask = degradedMask;
        java.util.Arrays.fill(failures, 0);
        java.util.Arrays.fill(settingsRecoveries, 0);
        degradedMask = 0;
        if (mask != 0 && log) {
            StructuredBridgeLog.warn(BridgeDebugArea.RENDERER, BridgeEvents.FEATURE_RECOVERED,
                    "trigger=plugin-generation, mask=" + Integer.toHexString(mask));
        }
    }

    /**
     * The user changed the settings these features read. Paused features get another attempt, at most
     * {@link #MAX_SETTINGS_RECOVERIES} times per generation so a broken component plus a chatty
     * settings channel cannot become a retry loop. Failure counts of running features also reset.
     */
    void onSettingsReapplied(Feature... features) {
        StringBuilder recovered = null;
        synchronized (this) {
            for (Feature feature : features) {
                int index = feature.ordinal();
                if ((degradedMask & bit(feature)) == 0) {
                    failures[index] = 0;
                    continue;
                }
                if (settingsRecoveries[index] >= MAX_SETTINGS_RECOVERIES) continue;
                settingsRecoveries[index]++;
                failures[index] = 0;
                degradedMask &= ~bit(feature);
                if (log) {
                    if (recovered == null) recovered = new StringBuilder();
                    else recovered.append('|');
                    recovered.append(feature.name());
                }
            }
        }
        if (recovered != null) {
            StructuredBridgeLog.warn(BridgeDebugArea.RENDERER, BridgeEvents.FEATURE_RECOVERED,
                    "trigger=settings, features=" + recovered);
        }
    }

    synchronized int failureCount(Feature feature) {
        return failures[feature.ordinal()];
    }

    private static int bit(Feature feature) {
        return 1 << feature.ordinal();
    }
}
