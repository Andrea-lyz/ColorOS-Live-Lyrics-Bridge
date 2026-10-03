package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ArtworkRenderGuardTest {
    private static final ArtworkRequestStamp REQUEST = new ArtworkRequestStamp(1, 2, 3, 4, 5, 6, 7, 8);

    @Test public void everyOwnershipDomainInvalidatesLatePreparationOrFirstFrame() {
        long[] epochs = {1, 2, 3, 4, 5, 6, 7, 8};
        for (int domain = 0; domain < epochs.length; domain++) {
            AtomicReference<ArtworkRequestStamp> current = new AtomicReference<>(REQUEST);
            ArtworkRenderGuard guard = new ArtworkRenderGuard(REQUEST, current::get, () -> true);
            assertTrue(guard.permits());
            long[] changed = epochs.clone();
            changed[domain]++;
            current.set(new ArtworkRequestStamp(changed[0], changed[1], changed[2], changed[3],
                    changed[4], changed[5], changed[6], changed[7]));
            assertFalse("changed ownership domain " + domain, guard.permits());
            current.set(REQUEST);
            assertFalse("old rendering lease cannot revive", guard.permits());
        }
    }

    @Test public void screenOffOrTransitionInvalidationCannotReviveOldFrames() {
        AtomicBoolean eligible = new AtomicBoolean(true);
        ArtworkRenderGuard old = new ArtworkRenderGuard(REQUEST, () -> REQUEST, eligible::get);
        assertTrue(old.permits());
        eligible.set(false);
        assertFalse(old.permits());
        eligible.set(true);
        assertFalse(old.permits());
        assertTrue(new ArtworkRenderGuard(REQUEST, () -> REQUEST, eligible::get).permits());
    }

    @Test public void detachedOwnerAndMissingCurrentStampRejectLateCallbacks() {
        ArtworkRenderGuard closed = new ArtworkRenderGuard(REQUEST, () -> REQUEST, () -> true);
        closed.close();
        closed.close();
        assertFalse(closed.permits());
        assertFalse(new ArtworkRenderGuard(REQUEST, () -> null, () -> true).permits());
    }

    @Test public void failedHostReadFailsClosedWithoutEscapingIntoRenderer() {
        ArtworkRenderGuard guard = new ArtworkRenderGuard(REQUEST, () -> REQUEST,
                () -> { throw new IllegalStateException("host unavailable"); });
        assertFalse(guard.permits());
        assertFalse(guard.permits());
    }
}
