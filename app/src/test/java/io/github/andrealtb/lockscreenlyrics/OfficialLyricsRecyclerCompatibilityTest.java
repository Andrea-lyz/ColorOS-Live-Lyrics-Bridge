package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;

public class OfficialLyricsRecyclerCompatibilityTest {
    @Test
    public void oldAndNewLayoutUsesIntUWithoutChangingDuration() {
        UvCLayout recycler = new UvCLayout();

        OfficialLyricsRecyclerCompatibility.Binding binding =
                OfficialLyricsRecyclerCompatibility.resolve(recycler.getClass());

        assertNotNull(binding);
        assertEquals("u/v/C", binding.layoutName);
        assertEquals(Integer.valueOf(12), binding.readLineSpacing(recycler));
        assertTrue(binding.writeLineSpacing(recycler, -20));
        assertEquals(-20, recycler.u);
        assertEquals(340L, recycler.v);
        assertTrue(binding.writeInactiveScale(recycler, 0.82f));
        assertEquals(0.82f, recycler.C, 0.0001f);
    }

    @Test
    public void userLayoutUsesIntTAndNeverWritesLongU() {
        TuALayout recycler = new TuALayout();

        OfficialLyricsRecyclerCompatibility.Binding binding =
                OfficialLyricsRecyclerCompatibility.resolve(recycler.getClass());

        assertNotNull(binding);
        assertEquals("t/u/A", binding.layoutName);
        assertEquals(Integer.valueOf(8), binding.readLineSpacing(recycler));
        assertTrue(binding.writeLineSpacing(recycler, -20));
        assertEquals(-20, recycler.t);
        assertEquals(340L, recycler.u);
        assertTrue(binding.writeInactiveScale(recycler, 0.88f));
        assertEquals(0.88f, recycler.A, 0.0001f);
    }

    @Test
    public void realmeUi7UsesSpacingUAndScaleBWithoutChangingFontWeightOrDuration() {
        Ui7Layout recycler = new Ui7Layout();

        OfficialLyricsRecyclerCompatibility.Binding binding =
                OfficialLyricsRecyclerCompatibility.resolve(recycler.getClass());

        assertNotNull(binding);
        assertEquals("u/v/B", binding.layoutName);
        assertEquals(Integer.valueOf(20), binding.readLineSpacing(recycler));
        assertEquals(Float.valueOf(0.73f), binding.readInactiveScale(recycler));
        assertTrue(binding.writeLineSpacing(recycler, -20));
        assertEquals(-20, recycler.u);
        assertEquals(500, recycler.t);
        assertEquals(300L, recycler.v);
        assertTrue(binding.writeInactiveScale(recycler, 0.84f));
        assertEquals(0.84f, recycler.B, 0.0001f);
        assertTrue(recycler.C);
    }

    @Test
    public void ui7BindingRejectsStaticSpacingOrWrongScaleType() {
        assertNull(OfficialLyricsRecyclerCompatibility.resolve(StaticUi7Layout.class));
        assertNull(OfficialLyricsRecyclerCompatibility.resolve(WrongUi7ScaleLayout.class));
    }

    @Test
    public void ambiguousFieldNameWithoutVerifiedFingerprintIsRejected() {
        assertNull(OfficialLyricsRecyclerCompatibility.resolve(
                DangerousPartialLayout.class));
        assertNull(OfficialLyricsRecyclerCompatibility.resolve(
                UnknownLayout.class));
    }

    @Test
    public void primitiveAccessorsFailClosedForWrongReceiver() {
        OfficialLyricsRecyclerCompatibility.Binding binding =
                OfficialLyricsRecyclerCompatibility.resolve(UvCLayout.class);

        assertNotNull(binding);
        assertNull(binding.readLineSpacing(new Object()));
        assertFalse(binding.writeLineSpacing(new Object(), -20));
        assertNull(binding.readInactiveScale(new Object()));
        assertFalse(binding.writeInactiveScale(new Object(), 0.9f));
    }

    @Test
    public void semanticFieldsCanUseArbitraryObfuscatedNames() throws Exception {
        Field spacing = FutureLayout.class.getDeclaredField("q");
        Field duration = FutureLayout.class.getDeclaredField("r");
        Field scale = FutureLayout.class.getDeclaredField("s");

        OfficialLyricsRecyclerCompatibility.Binding binding =
                OfficialLyricsRecyclerCompatibility.fromResolvedFields(
                        FutureLayout.class,
                        spacing,
                        duration,
                        scale,
                        "dexkit:q/r/s");

        FutureLayout recycler = new FutureLayout();
        assertNotNull(binding);
        assertEquals("dexkit:q/r/s", binding.layoutName);
        assertTrue(binding.writeLineSpacing(recycler, -20));
        assertEquals(-20, recycler.q);
        assertEquals(340L, recycler.r);
        assertTrue(binding.writeInactiveScale(recycler, 0.84f));
        assertEquals(0.84f, recycler.s, 0.0001f);
    }

    @Test
    public void semanticFieldsRejectWrongPrimitiveRoles() throws Exception {
        assertNull(OfficialLyricsRecyclerCompatibility.fromResolvedFields(
                FutureLayout.class,
                FutureLayout.class.getDeclaredField("r"),
                FutureLayout.class.getDeclaredField("q"),
                FutureLayout.class.getDeclaredField("s"),
                "dexkit:wrong"));
    }

    private static final class UvCLayout {
        int u = 12;
        long v = 340L;
        float C = 0.9f;
    }

    private static final class TuALayout {
        int t = 8;
        long u = 340L;
        float A = 0.9f;
    }

    private static final class DangerousPartialLayout {
        long u = 340L;
        float A = 0.9f;
    }

    private static final class Ui7Layout {
        int t = 500;
        int u = 20;
        long v = 300L;
        float B = 0.73f;
        boolean C = true;
    }

    private static final class StaticUi7Layout {
        static int u = 20;
        long v = 300L;
        float B = 0.73f;
    }

    private static final class WrongUi7ScaleLayout {
        int u = 20;
        long v = 300L;
        boolean B = true;
    }

    private static final class UnknownLayout {
        int q = 8;
        long r = 340L;
        float s = 0.9f;
    }

    private static final class FutureLayout {
        int q = 8;
        long r = 340L;
        float s = 0.9f;
    }
}
