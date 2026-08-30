package io.github.andrealtb.lockscreenlyrics.systemui.lyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LyricsRecyclerFieldAccessorTest {
    private final LyricsRecyclerFieldAccessor accessor = new LyricsRecyclerFieldAccessor();

    @Test
    public void readsInheritedPrimitiveCurrentIndex() {
        PrimitiveChild recycler = new PrimitiveChild();
        assertEquals(7, accessor.readCurrentIndex(recycler, -1));

        recycler.setCurrentIndex(11);
        assertEquals(11, accessor.readCurrentIndex(recycler, -1));
    }

    @Test
    public void readsBoxedCurrentIndex() {
        assertEquals(5, accessor.readCurrentIndex(new BoxedRecycler(), -1));
    }

    @Test
    public void rejectsMissingOrWrongTypedField() {
        assertEquals(-1, accessor.readCurrentIndex(new MissingRecycler(), -1));
        assertEquals(-1, accessor.readCurrentIndex(new WrongTypeRecycler(), -1));
        assertEquals(-1, accessor.readCurrentIndex(null, -1));
    }

    private static class PrimitiveBase {
        private int n = 7;

        void setCurrentIndex(int value) {
            n = value;
        }
    }

    private static final class PrimitiveChild extends PrimitiveBase {
    }

    private static final class BoxedRecycler {
        private Integer n = 5;
    }

    private static final class MissingRecycler {
    }

    private static final class WrongTypeRecycler {
        @SuppressWarnings("unused")
        private String n = "5";
    }
}
