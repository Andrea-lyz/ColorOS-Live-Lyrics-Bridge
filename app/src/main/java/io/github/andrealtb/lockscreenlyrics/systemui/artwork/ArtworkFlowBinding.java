package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Strong ownership only for the attached observation lifetime. Closed leases reject late reads. */
public final class ArtworkFlowBinding implements AutoCloseable {
    private Object primary;
    private Method getter;
    private ArtworkModuleSource.Input module;
    private final List<Object> watched = new ArrayList<>();

    public ArtworkFlowBinding(Object primary, Method getter, ArtworkModuleSource.Input module, List<Object> delegates) {
        if (primary == null || getter == null || delegates.size() > 1) throw new IllegalArgumentException("flow_binding_shape");
        this.primary = primary;
        this.getter = getter;
        this.module = module;
        watched.add(primary);
        for (Object delegate : delegates) {
            if (delegate == primary || delegate == null) throw new IllegalArgumentException("flow_binding_duplicate");
            watched.add(delegate);
        }
    }

    public synchronized boolean owns(Object value) {
        for (Object flow : watched) if (flow == value) return true;
        return false;
    }

    public synchronized List<Object> watched() { return List.copyOf(watched); }

    public synchronized Object read() throws ReflectiveOperationException {
        if (primary == null) throw new IllegalStateException("flow_binding_closed");
        return payload(getter.invoke(primary));
    }

    public synchronized Object payload(Object value) throws ReflectiveOperationException {
        if (primary == null) throw new IllegalStateException("flow_binding_closed");
        return module == null ? value : module.payload(value);
    }

    @Override public synchronized void close() {
        primary = null;
        getter = null;
        module = null;
        watched.clear();
    }
}
