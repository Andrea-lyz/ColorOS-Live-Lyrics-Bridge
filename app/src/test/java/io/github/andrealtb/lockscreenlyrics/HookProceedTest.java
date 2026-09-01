package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public final class HookProceedTest {
    @Test
    public void hostIsInvokedExactlyOnceWithOriginalOrRewrittenArguments() throws Throwable {
        FakeChain original = new FakeChain();
        assertSame(original.result, HookProceed.once(original, null));
        assertEquals(1, original.calls);

        FakeChain rewritten = new FakeChain();
        Object[] args = {"replacement"};
        assertSame(rewritten.result, HookProceed.once(rewritten, args));
        assertEquals(1, rewritten.calls);
        assertEquals(Arrays.asList(args), rewritten.proceededArgs);
    }

    @Test(expected = IllegalStateException.class)
    public void hostExceptionIsNotRetried() throws Throwable {
        FakeChain chain = new FakeChain();
        chain.failure = new IllegalStateException("host");
        try {
            HookProceed.once(chain, null);
        } finally {
            assertEquals(1, chain.calls);
        }
    }

    private static final class FakeChain implements XposedInterface.Chain {
        final Object result = new Object();
        int calls;
        RuntimeException failure;
        List<Object> proceededArgs = Collections.emptyList();

        @Override public Executable getExecutable() { return null; }
        @Override public Object getThisObject() { return null; }
        @Override public List<Object> getArgs() { return Collections.emptyList(); }
        @Override public Object getArg(int index) { return null; }
        @Override public Object proceed() {
            calls++;
            if (failure != null) throw failure;
            return result;
        }
        @Override public Object proceed(Object[] args) {
            proceededArgs = Arrays.asList(args);
            return proceed();
        }
        @Override public Object proceedWith(Object thisObject) { return proceed(); }
        @Override public Object proceedWith(Object thisObject, Object[] args) {
            return proceed(args);
        }
    }
}
