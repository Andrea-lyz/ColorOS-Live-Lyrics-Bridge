package io.github.andrealtb.lockscreenlyrics.render;

import static io.github.andrealtb.lockscreenlyrics.render.LyricRenderTransactions.Status.*;
import static org.junit.Assert.*;

import org.junit.Test;

public final class LyricRenderTransactionsTest {
    private final LyricRenderTransactions<Object> transactions = new LyricRenderTransactions<>();
    private final Object surface = new Object(), adapter = new Object(), row = new Object();
    private final Object modelA = new Object(), modelB = new Object();
    private final OfficialLyricDataset datasetA = OfficialLyricDatasetTest.dataset("Maroon", "When the morning came");
    private final OfficialLyricDataset datasetB = OfficialLyricDatasetTest.dataset("Anti-Hero", "I have this thing");

    @Test public void sharedCreditsCannotTransferOldRowsIntoNewModel() {
        readyA();
        transactions.publish(modelB, datasetB);
        assertEquals(WAITING, transactions.surfaceStatus(surface, modelB));
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelB));
    }

    @Test public void nativeDatasetCommitCannotClaimAnOldHolderWithValidPosition() {
        readyA();
        transactions.publish(modelB, datasetB);
        dataset(datasetB);
        assertEquals(READY, transactions.surfaceStatus(surface, modelB));
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelB));
        bind(row);
        assertEquals(READY, transactions.rowStatus(surface, row, modelB));
    }

    @Test public void nativeDataCanArriveBeforeItsMatchingParsedModel() {
        readyA();
        dataset(datasetB);
        bind(row);
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
        transactions.publish(modelB, datasetB);
        assertEquals(READY, transactions.rowStatus(surface, row, modelB));
    }

    @Test public void inFlightDatasetDoesNotExposePartiallyReboundRows() {
        readyA();
        transactions.publish(modelB, datasetB);
        LyricRenderTransactions.DatasetChange change = transactions.beginDataset(surface, adapter, datasetB);
        bind(row);
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelB));
        transactions.commitDataset(change, adapter);
        assertEquals(READY, transactions.rowStatus(surface, row, modelB));
    }

    @Test public void lateOldBindCannotBeRelabelledAsTheCurrentDataset() {
        readyA();
        LyricRenderTransactions.RowBinding old = transactions.beginRowBind(adapter);
        transactions.publish(modelB, datasetB);
        dataset(datasetB);
        transactions.completeRowBind(old, row);
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelB));
        bind(row);
        assertEquals(READY, transactions.rowStatus(surface, row, modelB));
    }

    @Test public void sameTrackRawTimingUpgradeDoesNotForceNativeRebind() {
        readyA();
        transactions.publish(modelB, datasetA);
        assertEquals(READY, transactions.rowStatus(surface, row, modelB));
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void repeatedIdenticalSetLyricsStillRequiresItsRealBindEpoch() {
        readyA();
        dataset(datasetA);
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
        bind(row);
        assertEquals(READY, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void staleDatasetCompletionCannotOverrideANewerOne() {
        readyA();
        LyricRenderTransactions.DatasetChange older = transactions.beginDataset(surface, adapter, datasetB);
        LyricRenderTransactions.DatasetChange newer = transactions.beginDataset(surface, adapter, datasetA);
        transactions.commitDataset(older, adapter);
        assertEquals(WAITING, transactions.surfaceStatus(surface, modelA));
        transactions.commitDataset(newer, adapter);
        bind(row);
        assertEquals(READY, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void failedNativeUpdateRestoresItsPreviousCommit() {
        readyA();
        LyricRenderTransactions.DatasetChange failed = transactions.beginDataset(surface, adapter, datasetB);
        transactions.abortDataset(failed);
        assertEquals(READY, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void emptyNativeDataCannotRenderPreviousRows() {
        readyA();
        dataset(OfficialLyricDatasetTest.source(""));
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void unsupportedReflectionHasAnExplicitFallbackState() {
        readyA();
        dataset(null);
        assertEquals(UNSUPPORTED, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void aodAndBrightSurfacesCommitIndependently() {
        readyA();
        Object otherSurface = new Object(), otherAdapter = new Object(), otherRow = new Object();
        transactions.publish(modelB, datasetB);
        LyricRenderTransactions.DatasetChange change = transactions.beginDataset(otherSurface, otherAdapter, datasetB);
        transactions.commitDataset(change, otherAdapter);
        transactions.completeRowBind(transactions.beginRowBind(otherAdapter), otherRow);
        assertEquals(READY, transactions.rowStatus(otherSurface, otherRow, modelB));
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelB));
        assertEquals(WAITING, transactions.rowStatus(surface, otherRow, modelB));
    }

    @Test public void attachOrMatchingTextDoesNotEstablishOwnership() {
        transactions.publish(modelA, datasetA);
        dataset(datasetA);
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void clearingPublicationOrPluginGenerationInvalidatesOldBindings() {
        readyA();
        transactions.publish(null, null);
        assertNull(transactions.currentModel());
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
        transactions.publish(modelA, datasetA);
        transactions.clearBindings();
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void unrelatedAdapterEventsAreIgnored() {
        readyA();
        assertNull(transactions.beginRowBind(new Object()));
        transactions.completeRowBind(null, new Object());
        assertEquals(READY, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void asynchronousAdapterObservationUsesCommittedDataNotItsPendingRequest() {
        transactions.registerAdapter(surface, adapter);
        transactions.publish(modelB, datasetB);
        assertTrue(transactions.observeAdapterDataset(adapter, datasetA));
        bind(row);
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelB));
        assertTrue(transactions.observeAdapterDataset(adapter, datasetB));
        bind(row);
        assertEquals(READY, transactions.rowStatus(surface, row, modelB));
    }

    @Test public void unchangedDiffRowsMigrateOnlyAfterFullDatasetCommit() {
        readyA();
        Object credit = new Object();
        transactions.completeRowBind(transactions.beginRowBind(adapter, 1), credit);
        transactions.publish(modelB, datasetB);
        assertEquals(LyricRenderTransactions.RowStatus.WAITING,
                transactions.observeRowPosition(surface, credit, 1, modelB));
        transactions.observeAdapterDataset(adapter, datasetB);
        assertEquals(LyricRenderTransactions.RowStatus.CURRENT,
                transactions.observeRowPosition(surface, credit, 1, modelB));
        assertEquals(READY, transactions.rowStatus(surface, credit, modelB));
        assertEquals(LyricRenderTransactions.RowStatus.WAITING,
                transactions.observeRowPosition(surface, row, 0, modelB));
        assertFalse(transactions.commitSurfacePixels(surface, modelB, false));
        bind(row);
        assertEquals(LyricRenderTransactions.RowStatus.CURRENT,
                transactions.observeRowPosition(surface, row, 0, modelB));
        assertTrue(transactions.commitSurfacePixels(surface, modelB, true));
    }

    @Test public void newRealBindSupersedesOldHolderEvenWithSamePositionAndDataset() {
        readyA();
        Object replacement = new Object();
        transactions.completeRowBind(transactions.beginRowBind(adapter, 0), replacement);
        assertEquals(LyricRenderTransactions.RowStatus.RETIRED,
                transactions.observeRowPosition(surface, row, 0, modelA));
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
        assertEquals(READY, transactions.rowStatus(surface, replacement, modelA));
    }

    @Test public void nestedPayloadBindCannotRestoreItsOlderOuterBindingToken() {
        readyA();
        LyricRenderTransactions.RowBinding outer = transactions.beginRowBind(adapter, 0);
        LyricRenderTransactions.RowBinding inner = transactions.beginRowBind(adapter, 0);
        transactions.completeRowBind(inner, row);
        transactions.completeRowBind(outer, row);
        assertEquals(READY, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void temporaryNoPositionRetainsProvenanceForRecovery() {
        readyA();
        assertEquals(LyricRenderTransactions.RowStatus.RETIRED,
                transactions.observeRowPosition(surface, row, -1, modelA));
        assertEquals(WAITING, transactions.rowStatus(surface, row, modelA));
        assertEquals(LyricRenderTransactions.RowStatus.CURRENT,
                transactions.observeRowPosition(surface, row, 0, modelA));
        assertEquals(READY, transactions.rowStatus(surface, row, modelA));
    }

    @Test public void surfacePixelsCommitOncePerCoherentModelDatasetAndBindingCohort() {
        readyA();
        assertFalse(transactions.commitSurfacePixels(surface, modelA, false));
        assertTrue(transactions.commitSurfacePixels(surface, modelA, true));
        assertFalse(transactions.commitSurfacePixels(surface, modelA, true));
        bind(row);
        assertTrue(transactions.commitSurfacePixels(surface, modelA, true));
        transactions.publish(modelB, datasetA);
        assertTrue(transactions.commitSurfacePixels(surface, modelB, true));
    }

    @Test public void rowsInRecycledHiddenHoldersRemainDiscoverableForDisplayListInvalidation() {
        readyA();
        Object old = row;
        Object replacement = new Object();
        bind(replacement);
        assertTrue(transactions.rowsForSurface(surface).contains(old));
        assertTrue(transactions.rowsForSurface(surface).contains(replacement));
    }

    private void readyA() {
        transactions.publish(modelA, datasetA);
        dataset(datasetA);
        bind(row);
        assertEquals(READY, transactions.rowStatus(surface, row, modelA));
    }

    private void dataset(OfficialLyricDataset dataset) {
        LyricRenderTransactions.DatasetChange change = transactions.beginDataset(surface, adapter, dataset);
        transactions.commitDataset(change, adapter);
    }

    private void bind(Object view) {
        transactions.completeRowBind(transactions.beginRowBind(adapter), view);
    }
}
