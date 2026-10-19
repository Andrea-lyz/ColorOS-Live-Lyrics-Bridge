package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.view.View;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

public class ArtworkC17RolesTest {
    @Test public void pendingModelKeepsTheSharedInterfaceField() throws Exception {
        Field pending = field("pendingModel");
        assertEquals(pending, ArtworkC17Roles.pendingModel(
                List.of(field("renderer"), pending, field("switching")),
                List.of(pending, field("waitingLayout"), field("skip"))));
    }

    @Test public void pendingModelDoesNotRequireAConcreteClass() throws Exception {
        assertTrue(field("pendingModel").getType().isInterface());
    }

    @Test public void pendingModelRejectsAPrimitiveOnlyOverlap() {
        assertThrows(NoSuchFieldException.class, () -> ArtworkC17Roles.pendingModel(
                List.of(field("switching")),
                List.of(field("switching"), field("waitingLayout"))));
    }

    @Test public void rendererIsTheSwitchedInterfaceWithAViewAccessor() throws Exception {
        Field renderer = field("renderer");
        assertEquals(renderer, ArtworkC17Roles.renderer(
                List.of(field("pendingModel"), field("switching"), renderer)));
        assertEquals(Host.Renderer.class.getMethod("d"),
                ArtworkC17Roles.viewAccessor(Host.Renderer.class));
    }

    @Test public void otherSlotIsTheAlbumReaderThatIsNotCurrent() throws Exception {
        Field current = field("current");
        Field next = field("next");
        assertEquals(next, ArtworkC17Roles.otherSlot(
                List.of(current, next, field("pendingModel")), current, Host.Slot.class));
    }

    @Test public void mismatchesNameOnlyTheRolesThatMoved() throws Exception {
        ArtworkC17Roles left = roles(field("current"), field("next"));
        ArtworkC17Roles right = roles(field("current"), field("current"));
        assertEquals(List.of("next"), left.mismatches(right));
        assertTrue(left.mismatches(left).isEmpty());
    }

    private static ArtworkC17Roles roles(Field current, Field next) throws Exception {
        return new ArtworkC17Roles(
                current,
                next,
                field("pendingModel"),
                field("switching"),
                field("waitingLayout"),
                field("immersive"),
                field("lyricMode"),
                field("renderer"),
                field("backgroundRunning"));
    }

    private static Field field(String name) throws NoSuchFieldException {
        return Host.class.getDeclaredField(name);
    }

    private static final class Host {
        Slot current;
        Slot next;
        Model pendingModel;
        boolean switching;
        boolean waitingLayout;
        boolean immersive;
        boolean lyricMode;
        Renderer renderer;
        boolean backgroundRunning;
        int skip;

        interface Slot {}
        interface Model {}
        interface Renderer {
            View d();
        }
    }
}
