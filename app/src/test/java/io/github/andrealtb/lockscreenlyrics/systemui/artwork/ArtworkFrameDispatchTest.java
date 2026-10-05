package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkFrameDispatchTest {
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final ArtworkFrameDispatch frames = new ArtworkFrameDispatch(queue::add);

    @Test public void staleLeaseRevokesNowButLayerSurvivesUntilDrawReturns() {
        var stamp = new ArtworkRequestStamp(1, 1, 1, 1, 1, 1, 1, 1);
        AtomicBoolean eligible = new AtomicBoolean(true);
        var guard = new ArtworkRenderGuard(stamp, () -> stamp, eligible::get);
        AtomicBoolean layer = new AtomicBoolean(true);
        assertTrue(guard.permits());
        // Simulate TextureView.draw -> applyUpdate -> onSurfaceTextureUpdated.
        eligible.set(false);
        assertFalse(guard.permits());
        frames.offer(() -> { if (!guard.permits()) layer.set(false); });
        assertTrue("draw still needs its TextureLayer after the callback", layer.get());
        eligible.set(true);
        assertFalse("a fast screen-on cannot resurrect the old lease", guard.permits());
        queue.remove().run();
        assertFalse(layer.get());
    }

    @Test public void multipleFrameNotificationsProduceOneLatestDelivery() {
        List<Integer> seen = new ArrayList<>();
        frames.offer(() -> seen.add(1));
        frames.offer(() -> seen.add(2));
        assertTrue(seen.isEmpty());
        assertEquals(1, queue.size());
        queue.remove().run();
        assertEquals(List.of(2), seen);
    }

    @Test public void stoppingOrHandingOverCancelsOldQueuedWorkWithoutConsumingNewFrame() {
        List<String> seen = new ArrayList<>();
        frames.offer(() -> seen.add("old surface"));
        frames.clear();
        frames.offer(() -> seen.add("new surface"));
        queue.remove().run();
        assertTrue(seen.isEmpty());
        queue.remove().run();
        assertEquals(List.of("new surface"), seen);
    }

    @Test public void closeDropsPendingDelivery() {
        frames.offer(() -> fail("closed renderer delivered a frame"));
        frames.clear();
        queue.remove().run();
        assertTrue(queue.isEmpty());
    }

    @Test public void reentrantOfferWaitsForAnotherQueueTurn() {
        List<Integer> seen = new ArrayList<>();
        frames.offer(() -> { seen.add(1); frames.offer(() -> seen.add(2)); });
        queue.remove().run();
        assertEquals(List.of(1), seen);
        queue.remove().run();
        assertEquals(List.of(1, 2), seen);
    }
}
