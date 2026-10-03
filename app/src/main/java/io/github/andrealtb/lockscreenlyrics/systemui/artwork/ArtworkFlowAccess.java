package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** The scoped mpModule accessor survives even when the VM stores only derived property flows. */
public final class ArtworkFlowAccess {
    private ArtworkFlowAccess() {}

    public static Object moduleFlow(Object viewModel, Class<?> payloadType) throws ReflectiveOperationException {
        if (viewModel == null || payloadType == null) throw new NoSuchMethodException("module_payload_missing");
        Method selected = null;
        for (Method method : viewModel.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                    || parameters.length != 2 || parameters[0] != Class.class || parameters[1] != String.class) continue;
            try {
                Method value = method.getReturnType().getMethod("getValue");
                if (value.getReturnType() != Object.class || Modifier.isStatic(value.getModifiers())) continue;
            } catch (NoSuchMethodException ignored) { continue; }
            if (selected != null) throw new NoSuchMethodException("module_flow_accessor_ambiguous");
            selected = method;
        }
        if (selected == null) throw new NoSuchMethodException("module_flow_accessor_missing");
        selected.setAccessible(true);
        Object flow = selected.invoke(viewModel, payloadType, "mpModule");
        if (flow == null) throw new NoSuchMethodException("module_flow_missing");
        Method getter = flow.getClass().getMethod("getValue");
        getter.setAccessible(true);
        Object value = getter.invoke(flow);
        if (value != null && value.getClass() != payloadType) throw new IllegalArgumentException("module_payload_mismatch");
        return flow;
    }
}
