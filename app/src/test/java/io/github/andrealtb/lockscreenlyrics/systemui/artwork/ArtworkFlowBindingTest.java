package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ArtworkFlowBindingTest {
    @Test public void oneOwnedInputIsRetainedAcrossRepeatedReadsAndReleasedCompletely() throws Exception {
        var flow = new ArtworkModuleSourceTest.StateFlow("first");
        var delegate = new Object();
        var binding = new ArtworkFlowBinding(flow, flow.getClass().getMethod("getValue"), null, List.of(delegate));
        for (int i = 0; i < 1000; i++) { assertEquals("first", binding.read()); assertEquals(2, binding.watched().size()); }
        flow.value = "latest";
        assertEquals("latest", binding.read());
        binding.close();
        binding.close();
        assertFalse(binding.owns(flow));
        assertFalse(binding.owns(delegate));
        assertTrue(binding.watched().isEmpty());
        assertThrows(IllegalStateException.class, binding::read);
        assertThrows(IllegalStateException.class, () -> binding.payload("late"));
    }

    @Test public void sectionLeaseDecodesLatestModuleRatherThanFreezingItsInitialValue() throws Exception {
        var first = new ArtworkModuleSourceTest.Module("first");
        var flow = new ArtworkModuleSourceTest.StateFlow(new ArtworkModuleSourceTest.Section(first));
        var input = ArtworkModuleSource.bind(new ArtworkModuleSourceTest.Owner(flow), ArtworkModuleSourceTest.Module.class);
        var binding = new ArtworkFlowBinding(flow, input.getter(), input, List.of());
        assertSame(first, binding.read());
        var next = new ArtworkModuleSourceTest.Module("next");
        flow.value = new ArtworkModuleSourceTest.Section(next);
        assertSame(next, binding.read());
        binding.close();
        assertTrue(binding.watched().isEmpty());
    }

    @Test public void replacingLeaseCannotAcceptOldFlowOrAccumulateWatchers() throws Exception {
        var first = new ArtworkModuleSourceTest.StateFlow("old");
        var second = new ArtworkModuleSourceTest.StateFlow("new");
        var old = new ArtworkFlowBinding(first, first.getClass().getMethod("getValue"), null, List.of());
        old.close();
        var current = new ArtworkFlowBinding(second, second.getClass().getMethod("getValue"), null, List.of());
        assertFalse(current.owns(first));
        assertFalse(old.owns(first));
        assertEquals(1, current.watched().size());
        assertThrows(IllegalArgumentException.class,
                () -> new ArtworkFlowBinding(second, second.getClass().getMethod("getValue"), null, List.of(second)));
    }
}
