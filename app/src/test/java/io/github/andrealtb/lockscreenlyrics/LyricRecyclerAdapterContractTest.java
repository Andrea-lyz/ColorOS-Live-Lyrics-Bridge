package io.github.andrealtb.lockscreenlyrics;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

public class LyricRecyclerAdapterContractTest {
    @Test public void resolvesRealAndroidxBaseFromRuntimeHierarchy() {
        Object adapter = new RealAdapter();
        Class<?> base = LyricRecyclerAdapterContract.findBaseClass(adapter);
        assertSame(RecyclerView.Adapter.class, base);
        List<Method> bindings = LyricRecyclerAdapterContract.bindingMethods(adapter, base);
        assertEquals(1, bindings.size());
        assertEquals("onBindViewHolder", bindings.get(0).getName());
        assertSame(RecyclerView.ViewHolder.class, bindings.get(0).getParameterTypes()[0]);
    }

    @Test public void resolvesHostTypesWithoutAnyAndroidxClassOrMethodNames() {
        Object adapter = new ForeignAdapter();
        assertSame(ForeignBase.class, LyricRecyclerAdapterContract.findBaseClass(adapter));
        List<Method> bindings = LyricRecyclerAdapterContract.bindingMethods(adapter, ForeignBase.class);
        assertEquals(2, bindings.size());
        for (Method method : bindings) {
            assertSame(ForeignAdapter.class, method.getDeclaringClass());
            assertSame(ForeignHolder.class, method.getParameterTypes()[0]);
        }
    }

    @Test public void ignoresSameNameOverloadsWithAnUnrelatedHolder() {
        List<Method> bindings = LyricRecyclerAdapterContract.bindingMethods(
                new ForeignAdapter(), ForeignBase.class);
        assertEquals(2, bindings.size());
        for (Method method : bindings) assertNotEquals(String.class, method.getParameterTypes()[0]);
    }

    @Test public void doesNotHookInheritedBasePayloadWrapper() {
        Object adapter = new PartialAdapter();
        List<Method> bindings = LyricRecyclerAdapterContract.bindingMethods(adapter, ForeignBase.class);
        assertEquals(1, bindings.size());
        assertEquals(2, bindings.get(0).getParameterTypes().length);
    }

    @Test public void followsInheritedConcreteBindOverrides() {
        Object adapter = new LeafAdapter();
        assertSame(ForeignBase.class, LyricRecyclerAdapterContract.findBaseClass(adapter));
        List<Method> bindings = LyricRecyclerAdapterContract.bindingMethods(adapter, ForeignBase.class);
        assertEquals(2, bindings.size());
        for (Method method : bindings) assertSame(ForeignAdapter.class, method.getDeclaringClass());
    }

    @Test public void rejectsPartialStructuralMatches() {
        assertNull(LyricRecyclerAdapterContract.findBaseClass(new WrongFactory()));
        assertNull(LyricRecyclerAdapterContract.findBaseClass(new Object()));
        assertNull(LyricRecyclerAdapterContract.findBaseClass(null));
    }

    public static final class ForeignHolder {
        public final View foreignItem = null;
    }

    public abstract static class ForeignBase {
        public abstract int a();
        public abstract ForeignHolder b(ViewGroup parent, int viewType);
        public abstract void c(ForeignHolder holder, int position);
        public void d(ForeignHolder holder, int position, List<?> payloads) { c(holder, position); }
    }

    public static class ForeignAdapter extends ForeignBase {
        @Override public int a() { return 1; }
        @Override public ForeignHolder b(ViewGroup parent, int type) { return null; }
        @Override public void c(ForeignHolder holder, int position) { }
        @Override public void d(ForeignHolder holder, int position, List<?> payloads) { }
        public void c(String other, int position) { }
    }

    public static final class LeafAdapter extends ForeignAdapter { }

    public static final class PartialAdapter extends ForeignBase {
        @Override public int a() { return 1; }
        @Override public ForeignHolder b(ViewGroup parent, int type) { return null; }
        @Override public void c(ForeignHolder holder, int position) { }
    }

    public abstract static class MissingFactoryBase {
        public abstract int a();
        public abstract String b(ViewGroup parent, int viewType);
        public abstract void c(ForeignHolder holder, int position);
    }

    public static final class WrongFactory extends MissingFactoryBase {
        @Override public int a() { return 1; }
        @Override public String b(ViewGroup parent, int type) { return ""; }
        @Override public void c(ForeignHolder holder, int position) { }
    }

    private static final class RealHolder extends RecyclerView.ViewHolder {
        RealHolder(View view) { super(view); }
    }

    private static final class RealAdapter extends RecyclerView.Adapter<RealHolder> {
        @Override public RealHolder onCreateViewHolder(ViewGroup parent, int type) { return null; }
        @Override public void onBindViewHolder(RealHolder holder, int position) { }
        @Override public int getItemCount() { return 1; }
    }
}
