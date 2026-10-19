package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.graphics.drawable.Drawable;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class ArtworkC17ProfileTest {
    @Test public void reviewedRoleMustHaveExactInstanceFieldType() throws Exception {
        var field = ArtworkC17Access.field(Reviewed.class, "running", boolean.class);
        Reviewed instance = new Reviewed();
        assertFalse(field.getBoolean(instance));
        instance.running = true;
        assertTrue(field.getBoolean(instance));
    }

    @Test public void renamedOrRetypedRoleRejectsTheProfile() {
        assertThrows(NoSuchFieldException.class, () -> ArtworkC17Access.field(Changed.class, "running", boolean.class));
        assertThrows(NoSuchFieldException.class, () -> ArtworkC17Access.field(Reviewed.class, "missing", boolean.class));
    }

    @Test public void staticStateCannotBeMistakenForAHostsAnimation() {
        assertThrows(NoSuchFieldException.class, () -> ArtworkC17Access.field(Global.class, "running", boolean.class));
    }

    @Test public void albumUpdateShapeAcceptsEitherObfuscatedName() throws Exception {
        assertTrue(ArtworkC17Access.isAlbumUpdate(method("e")));
        assertTrue(ArtworkC17Access.isAlbumUpdate(method("f")));
        assertFalse(ArtworkC17Access.isAlbumUpdate(AlbumUpdates.class.getDeclaredMethod(
                "e", Object.class, Object.class, Object.class)));
        assertFalse(ArtworkC17Access.isAlbumUpdate(AlbumUpdates.class.getDeclaredMethod(
                "staticUpdate", Drawable.class, boolean.class)));
    }

    private static Method method(String name) throws NoSuchMethodException {
        return AlbumUpdates.class.getDeclaredMethod(name, Drawable.class, boolean.class);
    }

    private static class AlbumUpdates {
        void e(Drawable drawable, boolean animate) {}
        void f(Drawable drawable, boolean animate) {}
        void e(Object model, Object current, Object next) {}
        static void staticUpdate(Drawable drawable, boolean animate) {}
    }

    private static class Reviewed { private boolean running; }
    private static class Changed { private int running; }
    private static class Global { private static boolean running; }
}
