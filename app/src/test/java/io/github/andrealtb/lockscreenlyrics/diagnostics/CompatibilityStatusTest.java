package io.github.andrealtb.lockscreenlyrics.diagnostics;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
import static io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 34, manifest = Config.NONE)
public class CompatibilityStatusTest {
    private final AtomicLong now = new AtomicLong(100L);
    private final CompatibilityStatus status = new CompatibilityStatus(now::get, 60_000L);
    private final Object owner = new Object();
    private Snapshot row(Feature feature) { return status.snapshot().get(feature.ordinal()); }

    @Test public void neverObservedDoesNotMeanUnsupportedOrDisabled() {
        Snapshot s = row(Feature.CHAR_LIFT);
        assertEquals(Truth.UNKNOWN, s.requested);
        assertEquals(Truth.UNKNOWN, s.supported);
        assertEquals(Reason.TARGET_NOT_OBSERVED, s.reason);
        assertEquals(-1L, s.observedAt);
    }
    @Test public void configuredOffIsSeparateFromSupport() {
        status.requested(Feature.CHAR_LIFT, false);
        assertEquals(Outcome.DISABLED, row(Feature.CHAR_LIFT).outcome);
        assertEquals(Truth.UNKNOWN, row(Feature.CHAR_LIFT).supported);
        assertEquals(Reason.CONFIG_DISABLED, row(Feature.CHAR_LIFT).reason);
    }
    @Test public void paletteAvailabilityIsPreparationNotPixelSuccess() {
        status.palette(owner, true, true);
        assertEquals(Outcome.PREPARED, row(Feature.COVER_GLOW).outcome);
        assertEquals(Scope.PALETTE_SAMPLE, row(Feature.COVER_GLOW).scope);
    }
    @Test public void missingPaletteKeepsManualFallbackAndUnknownCapability() {
        status.palette(owner, true, false);
        assertEquals(Reason.PALETTE_UNAVAILABLE, row(Feature.COVER_GLOW).reason);
        assertEquals(Path.MANUAL_COLOR, row(Feature.COVER_GLOW).path);
        assertEquals(Truth.UNKNOWN, row(Feature.COVER_GLOW).supported);
    }
    @Test public void liftPreparationAndDrawCommandsAreDifferentEvidence() {
        status.liftPrepared(owner);
        assertEquals(Outcome.PREPARED, row(Feature.CHAR_LIFT).outcome);
        status.liftDrawn(owner, false);
        assertEquals(Outcome.DRAW_COMMANDS, row(Feature.CHAR_LIFT).outcome);
        assertEquals(Scope.GLYPH_COMMANDS, row(Feature.CHAR_LIFT).scope);
    }
    @Test public void aodDoesNotBecomeUnsupported() {
        status.liftBlocked(owner, true, false, true, false, false);
        assertEquals(Reason.AOD_LOW_FRAME_RATE, row(Feature.CHAR_LIFT).reason);
        assertEquals(Truth.UNKNOWN, row(Feature.CHAR_LIFT).supported);
    }
    @Test public void translationExplainsLineProgressDependency() {
        status.progress(owner, Feature.TRANSLATION_PROGRESS, true, false, false, true);
        assertEquals(Reason.LINE_PROGRESS_DISABLED, row(Feature.TRANSLATION_PROGRESS).reason);
    }
    @Test public void runtimeFailureDoesNotReportCompletedLift() {
        status.liftDrawn(owner, true);
        assertEquals(Outcome.FALLBACK, row(Feature.CHAR_LIFT).outcome);
        assertEquals(Reason.RUNTIME_EXCEPTION, row(Feature.CHAR_LIFT).reason);
        assertEquals(Truth.NO, row(Feature.CHAR_LIFT).eligible);
    }
    @Test public void drawDispatchDoesNotClaimVisualAcceptance() {
        status.draw(owner, Path.CUSTOM, Outcome.DISPATCHED, Reason.NONE);
        assertEquals(Outcome.DISPATCHED, row(Feature.CUSTOM_DRAW).outcome);
        assertEquals(Scope.RENDER_DISPATCH, row(Feature.CUSTOM_DRAW).scope);
    }
    @Test public void datasetFailureHasLocalUnsupportedScope() {
        status.draw(owner, Path.NATIVE, Outcome.FALLBACK, Reason.DATASET_UNSUPPORTED);
        assertEquals(Truth.NO, row(Feature.CUSTOM_DRAW).supported);
        assertEquals(Scope.RENDER_DISPATCH, row(Feature.CUSTOM_DRAW).scope);
    }
    @Test public void readingSnapshotDoesNotRefreshOrMutateObservation() {
        status.liftPrepared(owner);
        now.set(200);
        assertEquals(100L, row(Feature.CHAR_LIFT).observedAt);
        assertEquals(100L, row(Feature.CHAR_LIFT).observedAt);
    }
    @Test public void staleOwnerBecomesUnknownWithoutInventingFailure() {
        status.liftPrepared(owner);
        now.set(60_101);
        assertEquals(Outcome.NOT_OBSERVED, row(Feature.CHAR_LIFT).outcome);
        assertEquals(Truth.UNKNOWN, row(Feature.CHAR_LIFT).supported);
    }
    @Test public void rebindAndNewDatasetClearPriorObservation() {
        status.palette(owner, true, true);
        status.forgetOwner(owner);
        assertEquals(Reason.TARGET_NOT_OBSERVED, row(Feature.COVER_GLOW).reason);
        status.liftPrepared(owner);
        status.invalidateAll();
        assertEquals(Reason.TARGET_NOT_OBSERVED, row(Feature.CHAR_LIFT).reason);
    }
    @Test public void translationVisibilityAssignmentDoesNotClaimVisiblePixels() {
        status.translationButton(owner, true, 1, true);
        assertEquals(Outcome.PRESENTATION_ASSIGNED, row(Feature.TRANSLATION_BUTTON).outcome);
        assertEquals(Scope.VIEW_VISIBILITY, row(Feature.TRANSLATION_BUTTON).scope);
    }
    @Test public void missingTranslationDataIsNotUnsupportedHost() {
        status.translationButton(owner, true, 0, false);
        assertEquals(Reason.RAW_LYRIC_MISSING, row(Feature.TRANSLATION_BUTTON).reason);
        assertEquals(Truth.YES, row(Feature.TRANSLATION_BUTTON).supported);
        assertEquals(Truth.NO, row(Feature.TRANSLATION_BUTTON).eligible);
    }
    @Test public void artworkBindingDoesNotProveDecoderPresentation() {
        status.artworkBinding(owner, true, true, true, true);
        assertEquals(Outcome.PREPARED, row(Feature.DYNAMIC_ARTWORK).outcome);
        assertEquals(Scope.ARTWORK_IMMERSIVE_BINDING, row(Feature.DYNAMIC_ARTWORK).scope);
    }
    @Test public void unavailableArtworkUsesNativeFallbackWithoutFakeCapabilityFailure() {
        status.artworkBinding(owner, true, false, true, false);
        assertEquals(Path.NATIVE, row(Feature.DYNAMIC_ARTWORK).path);
        assertEquals(Reason.SNAPSHOT_PENDING, row(Feature.DYNAMIC_ARTWORK).reason);
    }
    @Test public void lyricModelResetDoesNotEraseIndependentArtworkState() {
        status.artworkBinding(owner, true, true, true, true);
        status.liftPrepared(owner);
        status.invalidateLyricObservations();
        assertEquals(Outcome.PREPARED, row(Feature.DYNAMIC_ARTWORK).outcome);
        assertEquals(Outcome.NOT_OBSERVED, row(Feature.CHAR_LIFT).outcome);
    }
    @Test public void alternatingRowsReuseWeakOwnersWithBoundedRetention() {
        Object[] live = new Object[200];
        for (int i = 0; i < live.length; i++) {
            live[i] = new Object();
            status.liftPrepared(live[i]);
        }
        assertTrue(status.retainedOwners() <= 128);
        for (int i = 0; i < 1000; i++) status.liftPrepared(live[199]);
        assertTrue(status.retainedOwners() <= 128);
    }
    @Test public void oneFeatureResetDoesNotClearAnotherFeaturesWeakOwner() {
        status.palette(owner, true, true);
        status.liftPrepared(owner);
        status.requested(Feature.CHAR_LIFT, false);
        assertEquals(Outcome.PREPARED, row(Feature.COVER_GLOW).outcome);
        assertEquals(Outcome.DISABLED, row(Feature.CHAR_LIFT).outcome);
    }
    @Test public void anotherRowDoesNotEraseTheFocusedRowsObservation() {
        Object focused = new Object(), offscreen = new Object();
        status.draw(focused, Path.CUSTOM, Outcome.DISPATCHED, Reason.NONE);
        now.set(150);
        status.draw(offscreen, Path.NATIVE, Outcome.WAITING, Reason.RAW_LYRIC_MISSING);
        Snapshot s = row(Feature.CUSTOM_DRAW);
        assertEquals(Outcome.WAITING, s.outcome); // latest observation is still reported as-is
        assertEquals(2, s.liveOwners);
        assertEquals(1, s.count(Outcome.DISPATCHED));
        assertEquals(1, s.count(Outcome.WAITING));
        assertEquals("WAITING:1,DISPATCHED:1", s.outcomeSummary());
        status.forgetOwner(offscreen);
        s = row(Feature.CUSTOM_DRAW);
        assertEquals(Outcome.DISPATCHED, s.outcome);
        assertEquals(1, s.liveOwners);
    }
    @Test public void sameOwnerUpdatesInPlaceAndOwnersStayBounded() {
        for (int i = 0; i < 1000; i++) status.draw(owner, Path.CUSTOM, Outcome.DISPATCHED, Reason.NONE);
        assertEquals(1, row(Feature.CUSTOM_DRAW).liveOwners);
        Object[] rows = new Object[OWNERS_PER_FEATURE + 5];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new Object(); now.set(200 + i);
            status.draw(rows[i], Path.CUSTOM, Outcome.PREPARED, Reason.NONE);
        }
        Snapshot s = row(Feature.CUSTOM_DRAW);
        assertEquals(OWNERS_PER_FEATURE, s.liveOwners);
        assertEquals(200L + rows.length - 1, s.observedAt);
    }
    @Test public void staleOwnersDropOutOfTheSummary() {
        Object a = new Object(), b = new Object();
        status.liftPrepared(a);
        now.set(50_000);
        status.liftDrawn(b, false);
        now.set(60_150); // a is older than 60 s, b is not
        Snapshot s = row(Feature.CHAR_LIFT);
        assertEquals(1, s.liveOwners);
        assertEquals(Outcome.DRAW_COMMANDS, s.outcome);
        assertEquals(0, s.count(Outcome.PREPARED));
    }
    @Test public void bundleCarriesOwnerCountAndSummaryWithoutObjectIdentity() {
        status.draw(owner, Path.CUSTOM, Outcome.DISPATCHED, Reason.NONE);
        String formatted = formatStatusForTest();
        assertTrue(formatted.contains("owners=1"));
        assertTrue(formatted.contains("summary=DISPATCHED:1"));
        assertFalse(formatted.contains("java.lang.Object@"));
    }
    private String formatStatusForTest() {
        android.os.Bundle b = new android.os.Bundle();
        status.putStatus(b);
        return formatStatus(b);
    }
    @Test public void targetlessAndUninstrumentedFeaturesStayUnknownAndBounded() {
        for (int i = 0; i < 1000; i++) status.liftPrepared(owner);
        assertEquals(Feature.values().length, status.snapshot().size());
        assertEquals(Outcome.NOT_OBSERVED, row(Feature.TRANSLATION_BUTTON).outcome);
        assertEquals(Outcome.NOT_OBSERVED, row(Feature.DYNAMIC_ARTWORK).outcome);
    }

    @Test public void wordProgressRecordsTheFillDecisionAndTheAodWait() {
        Object line = new Object();
        status.wordProgress(line, true, false);
        assertEquals(Outcome.PREPARED, row(Feature.WORD_PROGRESS).outcome);
        assertEquals(Reason.NONE, row(Feature.WORD_PROGRESS).reason);
        status.wordProgress(line, false, true);
        assertEquals(Outcome.WAITING, row(Feature.WORD_PROGRESS).outcome);
        assertEquals(Reason.AOD_LOW_FRAME_RATE, row(Feature.WORD_PROGRESS).reason);
    }
}
