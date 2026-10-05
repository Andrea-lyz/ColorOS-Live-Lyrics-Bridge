package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
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

    private static class Reviewed { private boolean running; }
    private static class Changed { private int running; }
    private static class Global { private static boolean running; }
}
