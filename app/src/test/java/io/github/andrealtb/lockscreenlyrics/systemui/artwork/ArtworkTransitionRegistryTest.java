package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ArtworkTransitionRegistryTest {
    @Test public void startBeforeHostAttachCanCompleteOnlyAfterTheExactOwnedDraw() {
        var registry = new ArtworkTransitionRegistry();
        Object first = new Object(), unrelated = new Object();
        registry.start(first);
        assertFalse(registry.complete(first));
        registry.beginDraw(unrelated);
        registry.endDraw(unrelated);
        assertFalse(registry.complete(first));
        assertFalse(registry.complete(unrelated));
        registry.beginDraw(first);
        registry.invalidate(first);
        registry.endDraw(first);
        assertFalse(registry.complete(first));
        registry.beginDraw(first);
        registry.endDraw(first);
        assertTrue(registry.complete(first));
    }
    @Test public void reverseBeforeAttachAndReplacementCannotReusePreviousCompletion() {
        var registry = new ArtworkTransitionRegistry();
        Object first = new Object(), replacement = new Object();
        registry.start(first);
        registry.reverseOrReset(first);
        registry.beginDraw(first);
        registry.endDraw(first);
        assertFalse(registry.complete(first));
        registry.start(first);
        registry.beginDraw(first);
        registry.endDraw(first);
        assertTrue(registry.complete(first));
        assertFalse(registry.complete(replacement));
        registry.release(first);
        assertFalse(registry.complete(first));
    }
    @Test public void nestedOwnedDrawsDoNotLoseTheOuterFrame() {
        var registry = new ArtworkTransitionRegistry();
        Object outer = new Object(), inner = new Object();
        registry.start(outer);
        registry.start(inner);
        registry.beginDraw(outer);
        registry.beginDraw(inner);
        registry.invalidate(inner);
        registry.endDraw(inner);
        registry.invalidate(outer);
        registry.endDraw(outer);
        assertFalse(registry.complete(outer));
        registry.beginDraw(outer);
        registry.beginDraw(inner);
        registry.endDraw(inner);
        registry.endDraw(outer);
        assertTrue(registry.complete(inner));
        assertTrue(registry.complete(outer));
    }
    @Test public void earlyObservationBudgetDoesNotEvictTheClaimedCurrentCover() {
        var registry = new ArtworkTransitionRegistry();
        Object current = new Object();
        registry.start(current);
        registry.beginDraw(current);
        registry.endDraw(current);
        List<Object> retained = new ArrayList<>();
        for (int i = 0; i < 100; i++) { Object extra = new Object(); retained.add(extra); registry.start(extra); }
        assertEquals(32, registry.size());
        assertTrue(registry.complete(current));
        registry.clear();
        assertEquals(0, registry.size());
        assertFalse(registry.complete(current));
    }
}
