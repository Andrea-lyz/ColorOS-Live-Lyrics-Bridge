package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * C17 immersive controller roles. Same-versionName SystemUIPlugin binaries move these
 * obfuscated letters, so each role is picked from the fields that one logged method reads
 * or writes. Selection is pure so it can be checked without DexKit.
 */
public record ArtworkC17Roles(Field current, Field next, Field pendingModel, Field switching,
        Field waitingLayout, Field immersive, Field lyricMode, Field renderer, Field backgroundRunning) {

    /** Names of roles whose fields differ between two bindings. */
    List<String> mismatches(ArtworkC17Roles other) {
        List<String> roles = new ArrayList<>();
        if (other == null) return roles;
        compare(roles, "current", current, other.current);
        compare(roles, "next", next, other.next);
        compare(roles, "pendingModel", pendingModel, other.pendingModel);
        compare(roles, "switching", switching, other.switching);
        compare(roles, "waitingLayout", waitingLayout, other.waitingLayout);
        compare(roles, "immersive", immersive, other.immersive);
        compare(roles, "lyricMode", lyricMode, other.lyricMode);
        compare(roles, "renderer", renderer, other.renderer);
        compare(roles, "backgroundRunning", backgroundRunning, other.backgroundRunning);
        return roles;
    }

    private static void compare(List<String> roles, String name, Field left, Field right) {
        if (!Objects.equals(left, right)) roles.add(name);
    }

    static Field single(Collection<Field> candidates, Predicate<Field> keep, String role)
            throws NoSuchFieldException {
        Field selected = null;
        for (Field field : candidates) {
            if (field == null || Modifier.isStatic(field.getModifiers()) || !keep.test(field)) continue;
            if (selected != null && !selected.equals(field)) throw new NoSuchFieldException(role + "_ambiguous");
            selected = field;
        }
        if (selected == null) throw new NoSuchFieldException(role + "_missing");
        selected.setAccessible(true);
        return selected;
    }

    static Field ofType(Collection<Field> candidates, Class<?> type, String role) throws NoSuchFieldException {
        return single(candidates, field -> field.getType() == type, role);
    }

    /** The second slot is the other slot-typed field the album writer touches. */
    static Field otherSlot(Collection<Field> albumReads, Field current, Class<?> slotType)
            throws NoSuchFieldException {
        return single(albumReads, field -> field.getType() == slotType && !field.equals(current), "c17_next");
    }

    /**
     * The pending model is the reference field written by both the renderer switch and the
     * layout-pending branch. Its type is an interface on reviewed builds; never exclude those.
     */
    static Field pendingModel(Collection<Field> switchWrites, Collection<Field> pendingWrites)
            throws NoSuchFieldException {
        return single(switchWrites, field -> !field.getType().isPrimitive() && pendingWrites.contains(field),
                "c17_pending_model");
    }

    /** The renderer is the switched field whose interface exposes the background view. */
    static Field renderer(Collection<Field> switchWrites) throws NoSuchFieldException {
        return single(switchWrites, field -> field.getType().isInterface() && hasViewAccessor(field.getType()),
                "c17_renderer");
    }

    /** A flag both read by one method and written by another, e.g. a spring's running bit. */
    static Field common(Collection<Field> reads, Collection<Field> writes, Class<?> type, String role)
            throws NoSuchFieldException {
        return single(reads, field -> field.getType() == type && writes.contains(field), role);
    }

    /** The single public no-argument {@code View} accessor; its obfuscated letter is not stable. */
    static Method viewAccessor(Class<?> type) throws NoSuchMethodException {
        Method selected = null;
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || method.getReturnType() != View.class) continue;
            if (selected != null) throw new NoSuchMethodException("c17_renderer_view_ambiguous");
            selected = method;
        }
        if (selected == null) throw new NoSuchMethodException("c17_renderer_view_missing");
        return selected;
    }

    private static boolean hasViewAccessor(Class<?> type) {
        try {
            viewAccessor(type);
            return true;
        } catch (NoSuchMethodException error) {
            return false;
        }
    }
}
