package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.media.session.PlaybackState;

import org.junit.Test;

import java.lang.reflect.Method;

public final class MediaDataStateUpdateResolverTest {
    @Test
    public void coloros16UpdateStateIsUsed() {
        Method method = MediaDataStateUpdateResolver.resolve(ColorOs16Manager.class);
        assertNotNull(method);
        assertEquals("updateState", method.getName());
    }

    @Test
    public void coloros17OnStateUpdateIsUsedInsteadOfTheSuspendHelper() {
        Method method = MediaDataStateUpdateResolver.resolve(ColorOs17Manager.class);
        assertNotNull(method);
        assertEquals("onStateUpdate", method.getName());
    }

    @Test
    public void inheritedEntryIsFound() {
        Method method = MediaDataStateUpdateResolver.resolve(ColorOs17Subclass.class);
        assertNotNull(method);
        assertEquals(ColorOs17Manager.class, method.getDeclaringClass());
    }

    @Test
    public void managerWithoutAnEntryFailsClosed() {
        assertNull(MediaDataStateUpdateResolver.resolve(SuspendOnlyManager.class));
        assertNull(MediaDataStateUpdateResolver.resolve(null));
    }

    @SuppressWarnings("unused")
    private static class ColorOs16Manager {
        public void updateState(String key, PlaybackState state) {
        }
    }

    @SuppressWarnings("unused")
    private static class ColorOs17Manager {
        public void onStateUpdate(String key, PlaybackState state) {
        }

        static Object access$updateState(
                ColorOs17Manager owner,
                String key,
                PlaybackState state,
                Object continuation) {
            return null;
        }
    }

    private static final class ColorOs17Subclass extends ColorOs17Manager {
    }

    @SuppressWarnings("unused")
    private static final class SuspendOnlyManager {
        static Object access$updateState(
                SuspendOnlyManager owner,
                String key,
                PlaybackState state,
                Object continuation) {
            return null;
        }

        static void updateState(String key, PlaybackState state) {
        }
    }
}
