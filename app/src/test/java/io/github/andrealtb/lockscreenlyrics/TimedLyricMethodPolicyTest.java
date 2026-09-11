package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.*;
import org.junit.Test;

public final class TimedLyricMethodPolicyTest {
    static class Renamed { void x(boolean animate, long position) {} }
    static class Ambiguous {
        void x(boolean animate, long position) {}
        void y(boolean animate, long position) {}
    }
    static class Legacy {
        void l(boolean animate, long position) {}
        void y(boolean animate, long position) {}
    }
    @Test public void uniquelyRenamedMethodMatchesButAmbiguityDoesNot() throws Exception {
        assertTrue(TimedLyricMethodPolicy.matches(Renamed.class.getDeclaredMethod("x", boolean.class, long.class)));
        assertFalse(TimedLyricMethodPolicy.matches(Ambiguous.class.getDeclaredMethod("x", boolean.class, long.class)));
        assertTrue(TimedLyricMethodPolicy.matches(Legacy.class.getDeclaredMethod("l", boolean.class, long.class)));
    }
    @Test public void holdsUntilNextEventRegardlessOfNativeElapsedTime() {
        for (long elapsed : new long[] {0, 1000, 3000, 10000, 60000}) {
            assertEquals(2000L, TimedLyricMethodPolicy.position(2000L, elapsed));
            assertEquals(elapsed, TimedLyricMethodPolicy.position(-1L, elapsed));
        }
        assertEquals(1000L, TimedLyricMethodPolicy.position(1000L, 60000));
    }
}
