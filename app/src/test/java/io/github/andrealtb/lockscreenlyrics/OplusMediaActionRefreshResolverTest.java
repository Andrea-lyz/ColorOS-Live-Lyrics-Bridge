package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.media.session.PlaybackState;

import org.junit.Test;

import java.lang.reflect.Method;

public final class OplusMediaActionRefreshResolverTest {
    @Test
    public void exactSyntheticNameWins() {
        Method method = OplusMediaActionRefreshResolver.resolve(ExactManager.class);
        assertNotNull(method);
        assertEquals("access$updateMediaDataFromPlayState", method.getName());
    }

    @Test
    public void uniqueObfuscatedShapeIsAccepted() {
        Method method = OplusMediaActionRefreshResolver.resolve(UniqueManager.class);
        assertNotNull(method);
        assertEquals("a", method.getName());
    }

    @Test
    public void ambiguousObfuscatedShapeFailsClosed() {
        assertNull(OplusMediaActionRefreshResolver.resolve(AmbiguousManager.class));
    }

    private static final class ExactManager {
        private static void access$updateMediaDataFromPlayState(
                ExactManager owner,
                String packageName,
                PlaybackState state) {
        }
    }

    private static final class UniqueManager {
        private static void a(UniqueManager owner, String packageName, PlaybackState state) {
        }
    }

    private static final class AmbiguousManager {
        private static void a(AmbiguousManager owner, String packageName, PlaybackState state) {
        }

        private static void b(AmbiguousManager owner, String packageName, PlaybackState state) {
        }
    }
}
