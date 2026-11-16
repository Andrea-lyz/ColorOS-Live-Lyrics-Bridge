package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.Feature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/** C-04 feature-level degradation: isolation, bounded budget, explicit and limited recovery. */
public class FeatureDegradationTest {
    @Test
    public void singleFailureNeverPausesAFeature() {
        FeatureDegradation ledger = new FeatureDegradation(false);
        assertFalse(ledger.failed(Feature.CUSTOM_DRAW, "x"));
        assertTrue(ledger.allowed(Feature.CUSTOM_DRAW));
        assertFalse(ledger.failed(Feature.TRANSLATION_BUTTON, "x"));
        assertTrue(ledger.allowed(Feature.TRANSLATION_BUTTON));
    }

    @Test
    public void thresholdPausesOnlyThatFeature() {
        FeatureDegradation ledger = new FeatureDegradation(false);
        int threshold = FeatureDegradation.threshold(Feature.CHAR_LIFT);
        for (int i = 1; i < threshold; i++) assertFalse(ledger.failed(Feature.CHAR_LIFT, "x"));
        assertTrue(ledger.failed(Feature.CHAR_LIFT, "x"));
        assertFalse(ledger.allowed(Feature.CHAR_LIFT));
        for (Feature other : Feature.values()) {
            if (other != Feature.CHAR_LIFT) assertTrue(other.name(), ledger.allowed(other));
        }
        // Further failures while paused are not counted and do not re-trip.
        assertFalse(ledger.failed(Feature.CHAR_LIFT, "x"));
        assertEquals(threshold, ledger.failureCount(Feature.CHAR_LIFT));
    }

    @Test
    public void newComponentGenerationClearsEverything() {
        FeatureDegradation ledger = new FeatureDegradation(false);
        trip(ledger, Feature.CUSTOM_DRAW);
        trip(ledger, Feature.COVER_GLOW);
        ledger.onComponentGeneration();
        assertTrue(ledger.allowed(Feature.CUSTOM_DRAW));
        assertTrue(ledger.allowed(Feature.COVER_GLOW));
        assertEquals(0, ledger.failureCount(Feature.CUSTOM_DRAW));
    }

    @Test
    public void settingsRecoveryIsLimitedPerGeneration() {
        FeatureDegradation ledger = new FeatureDegradation(false);
        for (int i = 0; i < FeatureDegradation.MAX_SETTINGS_RECOVERIES; i++) {
            trip(ledger, Feature.CUSTOM_DRAW);
            ledger.onSettingsReapplied(Feature.CUSTOM_DRAW);
            assertTrue(ledger.allowed(Feature.CUSTOM_DRAW));
        }
        trip(ledger, Feature.CUSTOM_DRAW);
        ledger.onSettingsReapplied(Feature.CUSTOM_DRAW);
        assertFalse("settings recovery budget is spent", ledger.allowed(Feature.CUSTOM_DRAW));
        // Only new component code restores it after that.
        ledger.onComponentGeneration();
        assertTrue(ledger.allowed(Feature.CUSTOM_DRAW));
    }

    @Test
    public void settingsRecoveryOnlyTouchesNamedFeatures() {
        FeatureDegradation ledger = new FeatureDegradation(false);
        trip(ledger, Feature.TRANSLATION_BUTTON);
        trip(ledger, Feature.CHAR_LIFT);
        ledger.onSettingsReapplied(Feature.TRANSLATION_BUTTON);
        assertTrue(ledger.allowed(Feature.TRANSLATION_BUTTON));
        assertFalse(ledger.allowed(Feature.CHAR_LIFT));
    }

    @Test
    public void settingsResetClearsPartialFailureCounts() {
        FeatureDegradation ledger = new FeatureDegradation(false);
        ledger.failed(Feature.COVER_GLOW, "x");
        ledger.failed(Feature.COVER_GLOW, "x");
        ledger.onSettingsReapplied(Feature.COVER_GLOW);
        assertEquals(0, ledger.failureCount(Feature.COVER_GLOW));
    }

    /** The guard is wired at each feature's existing failure point and fallback, not elsewhere. */
    @Test
    public void featureSitesUseTheLedger() throws IOException {
        String coordinator = source("OfficialLyricDrawCoordinator.java");
        assertTrue(coordinator.contains("allowed(CompatibilityStatus.Feature.CUSTOM_DRAW)"));
        assertTrue(coordinator.contains("failed(CompatibilityStatus.Feature.CUSTOM_DRAW"));
        String renderer = source("OfficialLyricTextRenderer.java");
        assertTrue(renderer.contains("allowed(CompatibilityStatus.Feature.CHAR_LIFT)"));
        assertEquals(3, count(renderer, "failed(CompatibilityStatus.Feature.CHAR_LIFT"));
        String binder = source("TranslationToggleMediaActionBinder.java");
        assertTrue(binder.contains("allowed(CompatibilityStatus.Feature.TRANSLATION_BUTTON)"));
        assertTrue(binder.contains("failed(CompatibilityStatus.Feature.TRANSLATION_BUTTON"));
        String artwork = source("systemui/artwork/DynamicArtworkRuntime.java");
        assertTrue(artwork.contains("degradation.allowed(glow)"));
        assertTrue(artwork.contains("degradation.failed(glow"));
        assertTrue(source("PluginHookGenerationManager.java").contains("onComponentGeneration()"));
        assertTrue(renderer.contains("FeatureDegradation.global().onSettingsReapplied("));
        assertFalse("new C-04 code stays out of the entry class",
                source("LockscreenLyricsModule.java").contains("FeatureDegradation"));
    }

    private static void trip(FeatureDegradation ledger, Feature feature) {
        for (int i = 0; i < FeatureDegradation.threshold(feature); i++) ledger.failed(feature, "x");
        assertFalse(ledger.allowed(feature));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    private static String source(String relative) throws IOException {
        Path root = Paths.get("src/main/java/io/github/andrealtb/lockscreenlyrics");
        if (!Files.isDirectory(root)) root = Paths.get("app").resolve(root);
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }
}
