package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkModelAccessTest {
    @Test public void bindsLabelsWithoutDeclarationOrderOrObfuscatedFieldNames() throws Exception {
        var access = new ArtworkModelAccess(FlatCard.class, Flat.class, Flat.class);
        assertEquals(new ArtworkCardIdentity("player", "pkg", "Song (Live)", "Artist"),
                access.readCard(new FlatCard(new Flat("player", "unused", "Song (Live)", "Artist", "pkg"))));
        assertFalse(access.splitModel);
    }

    @Test public void splitIdentityAndProgressAlsoSupportsExactImmersiveWrapper() throws Exception {
        var access = new ArtworkModelAccess(SplitCard.class, Split.class, Info.class, Immersive.class);
        var card = new SplitCard(new Split(new Info("player", "unused", "Song", "Artist", "pkg")));
        var expected = new ArtworkCardIdentity("player", "pkg", "Song", "Artist");
        assertEquals(expected, access.readCard(card));
        assertEquals(expected, access.readCard(new Immersive(card)));
        assertTrue(access.splitModel);
        assertNull(access.readCard(new Object()));
        assertNull(access.readCard(new SplitCard(null)));
    }

    @Test public void rejectsDuplicatedOrMissingSemanticLabels() {
        assertThrows(IllegalArgumentException.class, () -> ArtworkModelAccess.bindIdentityFields(BadLabel.class, "MediaModel(uniqueId="));
        assertThrows(IllegalArgumentException.class, () -> ArtworkModelAccess.bindIdentityFields(DuplicateField.class, "MediaModel(uniqueId="));
    }

    @Test public void twoSameTypedModelFieldsNeverChooseFirst() {
        assertThrows(NoSuchFieldException.class, () -> new ArtworkModelAccess(AmbiguousCard.class, Flat.class, Flat.class));
    }

    // Fields intentionally declared in a different order, with arbitrary names.
    public static final class Flat {
        final String field4;
        final String field1;
        final String field3;
        final String field2;
        public Flat(String id, String voice, String title, String artist, String pkg) {
            field4 = artist; field1 = pkg; field3 = title; field2 = id;
        }
        @Override public String toString() {
            return "MediaModel(uniqueId=" + field2 + ", songName=" + field3 + ", artist=" + field4 + ", pkg=" + field1 + ")";
        }
    }
    public static final class Info {
        final String z;
        final String y;
        final String x;
        final String w;
        public Info(String id, String voice, String title, String artist, String pkg) {
            z = pkg; y = artist; x = id; w = title;
        }
        @Override public String toString() {
            return "MediaInfo(uniqueId=" + x + ", songName=" + w + ", artist=" + y + ", pkg=" + z + ")";
        }
    }
    public static final class FlatCard { final Flat q; FlatCard(Flat q) { this.q = q; } }
    public static final class Split { final Info i; final long progress = 100; Split(Info i) { this.i = i; } }
    public static final class SplitCard { final Split v; SplitCard(Split v) { this.v = v; } }
    public static final class Immersive { final SplitCard v; Immersive(SplitCard v) { this.v = v; } }
    public static final class AmbiguousCard { final Flat a = null; final Flat b = null; }
    public static final class BadLabel {
        public BadLabel(String a, String b, String c, String d) {}
        @Override public String toString() { return "MediaModel(uniqueId=unknown, artist=unknown)"; }
    }
    public static final class DuplicateField {
        final String a;
        final String b;
        public DuplicateField(String id, String voice, String title, String artist, String pkg) { a = b = id; }
        @Override public String toString() {
            return "MediaModel(uniqueId=" + a + ", songName=" + a + ", artist=" + a + ", pkg=" + a + ")";
        }
    }
}
