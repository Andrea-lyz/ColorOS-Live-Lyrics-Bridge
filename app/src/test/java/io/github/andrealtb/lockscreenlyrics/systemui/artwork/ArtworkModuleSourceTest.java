package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import kotlin.coroutines.Continuation;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class ArtworkModuleSourceTest {
    @Test public void delegatedSourceUpdatesWithoutCreatingOrCollectingADerivedFlow() throws Exception {
        var first = new Module("first");
        var source = new StateFlow(new Section(first));
        var owner = new Owner(source);
        var vm = new DelegatingVm(owner);
        var input = ArtworkModuleSource.bind(vm, Module.class);
        assertSame(source, input.flow());
        assertSame(first, input.payload(input.getter().invoke(input.flow())));
        var second = new Module("second");
        source.value = new Section(second);
        assertSame(second, input.payload(input.getter().invoke(input.flow())));
        assertEquals(0, owner.factories);
        assertEquals(0, source.collections);
    }

    @Test public void configFlowCannotBeMistakenForSectionInput() throws Exception {
        var source = new StateFlow(new Section(new Module("song")));
        var owner = new Owner(source);
        assertSame(source, ArtworkModuleSource.bind(owner, Module.class).flow());
    }

    @Test public void missingPayloadInvalidatesInsteadOfReturningPreviousModule() throws Exception {
        var source = new StateFlow(new Section(new Module("song")));
        var input = ArtworkModuleSource.bind(new Owner(source), Module.class);
        source.value = new Section(null);
        assertNull(input.payload(input.getter().invoke(input.flow())));
        assertThrows(IllegalArgumentException.class, () -> input.payload(new Object()));
    }

    @Test public void duplicateInputsAreRejectedEvenWhenBothContainSameModule() {
        var module = new Module("song");
        assertThrows(NoSuchMethodException.class, () -> ArtworkModuleSource.bind(new DuplicateOwner(module), Module.class));
    }

    @Test public void delegateCycleAndMultipleMapAccessorsFailClosed() {
        assertThrows(NoSuchMethodException.class, () -> ArtworkModuleSource.bind(new Cycle(), Module.class));
        var module = new Module("song");
        assertThrows(NoSuchMethodException.class,
                () -> ArtworkModuleSource.bind(new Owner(new StateFlow(new AmbiguousSection(module))), Module.class));
    }

    public interface Renderable {}
    public interface Vm { Renderable snapshot(Class<?> type, String key); }
    public interface Flow { Object getValue(); }
    public static final class Module implements Renderable {
        final String name;
        Module(String name) { this.name = name; }
    }
    public static class Section {
        final Map<String, Renderable> data = new HashMap<>();
        Section(Renderable module) { data.put("mpModule", module); }
        public Map<String, Renderable> modules() { return data; }
    }
    public static final class AmbiguousSection extends Section {
        AmbiguousSection(Renderable module) { super(module); }
        public Map<String, Renderable> otherMap() { return data; }
    }
    public static final class StateFlow implements Flow {
        Object value;
        int collections;
        StateFlow(Object value) { this.value = value; }
        public Object getValue() { return value; }
        public Object collect(Object collector, Continuation<?> continuation) { collections++; return null; }
    }
    public static final class Owner implements Vm {
        final StateFlow input;
        final StateFlow config = new StateFlow("configuration");
        int factories;
        Owner(StateFlow input) { this.input = input; }
        public Renderable snapshot(Class<?> type, String key) { return ((Section) input.getValue()).modules().get(key); }
        public Flow derivedFactory(Class<?> type, String key) { factories++; throw new AssertionError("Do not create WhileSubscribed flow"); }
    }
    public static final class DelegatingVm implements Vm {
        final Vm delegate;
        final StateFlow lyricMode = new StateFlow(false);
        DelegatingVm(Vm delegate) { this.delegate = delegate; }
        public Renderable snapshot(Class<?> type, String key) { return delegate.snapshot(type, key); }
    }
    public static final class DuplicateOwner implements Vm {
        final StateFlow first;
        final StateFlow second;
        DuplicateOwner(Renderable module) { first = new StateFlow(new Section(module)); second = new StateFlow(new Section(module)); }
        public Renderable snapshot(Class<?> type, String key) { return ((Section) first.value).modules().get(key); }
    }
    public static final class Cycle implements Vm {
        final Vm delegate = this;
        public Renderable snapshot(Class<?> type, String key) { return null; }
    }
}
