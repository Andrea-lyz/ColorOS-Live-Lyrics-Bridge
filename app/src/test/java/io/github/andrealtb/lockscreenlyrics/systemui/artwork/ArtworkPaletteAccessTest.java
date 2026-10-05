package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkPaletteAccessTest {
    @Test public void readsPrimary80FromTheSameSnapshotAndNeverKeepsOldAlbumColor() throws Exception {
        var access = new ArtworkPaletteAccess(Info.class);
        assertEquals(Integer.valueOf(0xFF123456), access.read(info(0xFF123456)));
        assertEquals(Integer.valueOf(0xFFABCDEF), access.read(info(0xFFABCDEF)));
        assertNull(access.read(new Info(null)));
        assertNull(access.read(null));
        assertNull(access.read(info(0)));
    }

    @Test public void changedMediaInfoShapeDisablesOnlyThisCapability() {
        assertThrows(NoSuchFieldException.class, () -> new ArtworkPaletteAccess(Object.class));
    }

    private static Info info(int color) { return new Info(new Art(new Icon(new Processed(new Colors(color, 2, 3, 4, 5, 6, 7))))); }
    public static final class Info { final Art d; Info(Art value) { d = value; } }
    public static final class Art { final Icon a; Art(Icon value) { a = value; } }
    public static final class Icon { final Processed e; Icon(Processed value) { e = value; } }
    public static final class Processed { final Colors b; Processed(Colors value) { b = value; } }
    public static final class Colors {
        final int a;
        final int b;
        public Colors(int primary80, int primary60, int c, int d, int e, int f, int g) { a = primary80; b = primary60; }
        @Override public String toString() { return "ArtColors(primary80=" + a + ", primary60=" + b + ", rest=...)"; }
    }
}
