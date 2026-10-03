package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;

public class AmDownloadPolicyTest {
    @Test public void startedClientCancelFinishesAndCaches() {
        assertTrue(AmDownloadPolicy.keepDownloading(true, false, false, 0, 2));
    }
    @Test public void queuedRequestCancelledBeforeStartingStillAborts() {
        assertFalse(AmDownloadPolicy.keepDownloading(false, false, false, 0, 2));
    }
    @Test public void finishedCancelledOrOverBudgetDownloadsDoNotDetach() {
        assertFalse(AmDownloadPolicy.keepDownloading(true, true, false, 0, 2));
        assertFalse(AmDownloadPolicy.keepDownloading(true, false, true, 0, 2));
        assertFalse(AmDownloadPolicy.keepDownloading(true, false, false, 2, 2));
    }
}
