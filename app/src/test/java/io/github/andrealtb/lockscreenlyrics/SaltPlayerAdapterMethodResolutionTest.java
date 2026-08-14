package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

/**
 * Covers Salt Player final-publisher coroutine method resolution. Salt 12.2.1 obfuscates
 * coroutine method names (invoke/create/invokeSuspend) with a non-printable CJK dictionary, so
 * resolution must fall back to the unique single-Object-parameter structural signature.
 */
public final class SaltPlayerAdapterMethodResolutionTest {

    @Test
    public void prefersLiteralInvokeSuspendName() throws Exception {
        Method method = SaltPlayerAdapter.findInvokeSuspendMethod(NamedPublisher.class);

        assertEquals("invokeSuspend", method.getName());
        assertEquals(1, method.getParameterTypes().length);
        assertEquals(Object.class, method.getParameterTypes()[0]);
    }

    @Test
    public void fallsBackToUniqueSingleObjectParameterMethod() throws Exception {
        Method method = SaltPlayerAdapter.findInvokeSuspendMethod(ObfuscatedPublisher.class);

        assertEquals("mo135", method.getName());
        assertEquals(1, method.getParameterTypes().length);
        assertEquals(Object.class, method.getParameterTypes()[0]);
    }

    @Test
    public void rejectsPublisherWithoutSuspendShape() {
        assertThrows(
                NoSuchMethodException.class,
                () -> SaltPlayerAdapter.findInvokeSuspendMethod(NoCandidatePublisher.class));
    }

    @Test
    public void rejectsAmbiguousStructuralCandidates() {
        NoSuchMethodException error = assertThrows(
                NoSuchMethodException.class,
                () -> SaltPlayerAdapter.findInvokeSuspendMethod(AmbiguousPublisher.class));

        assertTrue(error.getMessage().contains("Ambiguous"));
    }

    private static final class NamedPublisher {
        public Object invoke(Object a, Object b) {
            return null;
        }

        public Object invokeSuspend(Object o) {
            return null;
        }
    }

    private static final class ObfuscatedPublisher {
        public Object mo321(Object a, Object b) {
            return null;
        }

        public Object mo135(Object o) {
            return null;
        }
    }

    private static final class NoCandidatePublisher {
        public Object invoke(Object a, Object b) {
            return null;
        }
    }

    private static final class AmbiguousPublisher {
        public Object first(Object o) {
            return null;
        }

        public Object second(Object o) {
            return null;
        }
    }
}
