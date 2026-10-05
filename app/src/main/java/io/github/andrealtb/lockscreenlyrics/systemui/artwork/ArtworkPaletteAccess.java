package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** C17 MediaInfo.albumArt -> IconModel -> processed artwork -> ArtColors.primary80.
 * Same value consumed by K0.N / s1.M; no bitmap sampling or fallback to a previous album. */
final class ArtworkPaletteAccess {
    private final Field artwork, icon, processed, colors, primary80;
    private final Field[] chain;

    ArtworkPaletteAccess(Class<?> info) throws ReflectiveOperationException {
        artwork = objectField(info, "d");
        icon = objectField(artwork.getType(), "a");
        processed = objectField(icon.getType(), "e");
        colors = objectField(processed.getType(), "b");
        chain = new Field[]{artwork, icon, processed, colors};
        Class<?> palette = colors.getType();
        primary80 = palette.getDeclaredField("a");
        if (primary80.getType() != int.class || Modifier.isStatic(primary80.getModifiers())
                || !Modifier.isFinal(primary80.getModifiers())) throw new NoSuchFieldException("primary80_shape");
        primary80.setAccessible(true);
        Object probe = palette.getConstructor(int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                .newInstance(1, 2, 3, 4, 5, 6, 7);
        if (!probe.toString().startsWith("ArtColors(primary80=1, primary60=2,") || primary80.getInt(probe) != 1) {
            throw new IllegalArgumentException("art_colors_contract");
        }
    }

    Integer read(Object info) throws IllegalAccessException {
        Object value = info;
        for (Field field : chain) {
            if (value == null) return null;
            value = field.get(value);
        }
        if (value == null) return null;
        int color = primary80.getInt(value);
        return (color >>> 24) == 0xFF ? color : null;
    }

    private static Field objectField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (field.getType().isPrimitive() || Modifier.isStatic(field.getModifiers())
                || !Modifier.isFinal(field.getModifiers())) throw new NoSuchFieldException("art_palette_field");
        field.setAccessible(true);
        return field;
    }
}
