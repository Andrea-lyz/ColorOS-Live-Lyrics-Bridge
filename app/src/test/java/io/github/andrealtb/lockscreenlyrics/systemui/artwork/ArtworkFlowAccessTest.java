package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkFlowAccessTest {
    @Test public void findsScopedModuleEvenWhenVmStoresOnlyDerivedProperties() throws Exception {
        var flow = new TestFlow(new Payload());
        var vm = new Vm(flow);
        assertSame(flow, ArtworkFlowAccess.moduleFlow(vm, Payload.class));
        assertEquals("mpModule", vm.requestedName);
        assertEquals(Payload.class, vm.requestedType);
    }

    @Test public void ignoresSameSignatureDtoAccessorAndDoesNotDependOnMethodName() throws Exception {
        var flow = new TestFlow(new Payload());
        assertSame(flow, ArtworkFlowAccess.moduleFlow(new Vm(flow), Payload.class));
    }

    @Test public void emptyModuleCanBindForLaterEmissionButWrongPayloadIsRejected() throws Exception {
        var empty = new TestFlow(null);
        assertSame(empty, ArtworkFlowAccess.moduleFlow(new Vm(empty), Payload.class));
        assertThrows(IllegalArgumentException.class,
                () -> ArtworkFlowAccess.moduleFlow(new Vm(new TestFlow(new Object())), Payload.class));
    }

    @Test public void duplicateAccessorsAndMissingFlowNeverPickFirst() {
        assertThrows(NoSuchMethodException.class, () -> ArtworkFlowAccess.moduleFlow(new AmbiguousVm(), Payload.class));
        assertThrows(NoSuchMethodException.class, () -> ArtworkFlowAccess.moduleFlow(new Vm(null), Payload.class));
        assertThrows(NoSuchMethodException.class, () -> ArtworkFlowAccess.moduleFlow(new Object(), Payload.class));
    }

    public interface Flow { Object getValue(); }
    public static final class TestFlow implements Flow {
        private final Object value;
        TestFlow(Object value) { this.value = value; }
        public Object getValue() { return value; }
    }
    public static final class Payload {}
    public static final class Vm {
        private final Flow module;
        public final String derivedTitle = "derived";
        Class<?> requestedType;
        String requestedName;
        Vm(Flow module) { this.module = module; }
        public Object plainDto(Class<?> type, String key) { throw new AssertionError("Not a Flow accessor"); }
        public Flow renamedAccessor(Class<?> type, String key) {
            requestedType = type; requestedName = key; return module;
        }
    }
    public static final class AmbiguousVm {
        public Flow one(Class<?> type, String key) { return null; }
        public Flow two(Class<?> type, String key) { return null; }
    }
}
