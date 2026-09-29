package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.widget.TextView;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OfficialImmersiveLyricEntryPolicyTest {
    private static final long BAND = 600L;
    private static final long[] STARTS = {0L, 1_500L, 4_000L, 9_000L};

    @Test
    public void coloros17EntryMatchesByShape() throws Exception {
        assertTrue(matches(FakeRecycler.class.getDeclaredMethod(
                "o", FakeImmersiveModel.class, boolean.class)));
    }

    @Test
    public void otherRecyclerMethodsDoNotMatch() throws Exception {
        assertFalse(matches(FakeRecycler.class.getDeclaredMethod(
                "h", FakeLyricTextView.class, boolean.class)));
        assertFalse(matches(FakeRecycler.class.getDeclaredMethod(
                "setAodModeStyle", boolean.class)));
        assertFalse(matches(FakeRecycler.class.getDeclaredMethod(
                "staticEntry", FakeImmersiveModel.class, boolean.class)));
        assertFalse(matches(FakeRecycler.class.getDeclaredMethod(
                "timed", boolean.class, long.class)));
        assertFalse(OfficialImmersiveLyricEntryPolicy.matches(null, FakeRecycler.class));
    }

    @Test
    public void indexAtFollowsTheVendorLookup() {
        assertEquals(0, OfficialImmersiveLyricEntryPolicy.indexAt(new long[]{500L, 900L}, 100L));
        assertEquals(0, OfficialImmersiveLyricEntryPolicy.indexAt(STARTS, 0L));
        assertEquals(1, OfficialImmersiveLyricEntryPolicy.indexAt(STARTS, 1_500L));
        assertEquals(1, OfficialImmersiveLyricEntryPolicy.indexAt(STARTS, 3_999L));
        assertEquals(3, OfficialImmersiveLyricEntryPolicy.indexAt(STARTS, 60_000L));
        assertEquals(-1, OfficialImmersiveLyricEntryPolicy.indexAt(new long[0], 1_000L));
        assertEquals(-1, OfficialImmersiveLyricEntryPolicy.indexAt(null, 1_000L));
    }

    @Test
    public void moduleClockJustPastTheBoundaryAdvancesTheRow() {
        assertEquals(2, OfficialImmersiveLyricEntryPolicy.alignedIndex(1, 1, 2, 120L, BAND));
        assertEquals(2, OfficialImmersiveLyricEntryPolicy.alignedIndex(1, -1, 2, BAND, BAND));
    }

    @Test
    public void moduleClockFarPastTheBoundaryKeepsTheVendorRow() {
        assertEquals(1, OfficialImmersiveLyricEntryPolicy.alignedIndex(1, 1, 2, BAND + 1L, BAND));
        assertEquals(1, OfficialImmersiveLyricEntryPolicy.alignedIndex(1, 1, 3, 50L, BAND));
    }

    @Test
    public void vendorAheadOfTheModuleIsNeverHeldBack() {
        assertEquals(2, OfficialImmersiveLyricEntryPolicy.alignedIndex(2, 1, 1, 3_000L, BAND));
    }

    @Test
    public void jitterBackAcrossTheBoundaryKeepsThePreviousRow() {
        assertEquals(2, OfficialImmersiveLyricEntryPolicy.alignedIndex(1, 2, 2, 200L, BAND));
    }

    @Test
    public void realSeekBackFollowsTheVendor() {
        assertEquals(1, OfficialImmersiveLyricEntryPolicy.alignedIndex(1, 2, 1, 2_000L, BAND));
        assertEquals(0, OfficialImmersiveLyricEntryPolicy.alignedIndex(0, 3, 0, 10L, BAND));
    }

    @Test
    public void unknownRowsKeepTheVendorValue() {
        assertEquals(-1, OfficialImmersiveLyricEntryPolicy.alignedIndex(-1, 2, 2, 10L, BAND));
        assertEquals(1, OfficialImmersiveLyricEntryPolicy.alignedIndex(1, 1, -1, 10L, BAND));
    }

    private static boolean matches(Method method) {
        return OfficialImmersiveLyricEntryPolicy.matches(method, FakeRecycler.class);
    }

    @SuppressWarnings("unused")
    private static final class FakeLyricTextView extends TextView {
        FakeLyricTextView(Context context) {
            super(context);
        }
    }

    @SuppressWarnings("unused")
    private static final class FakeImmersiveModel {
        final List<?> lines;
        final int position;

        FakeImmersiveModel(List<?> lines, int position) {
            this.lines = lines;
            this.position = position;
        }
    }

    @SuppressWarnings("unused")
    private static final class FakeRecycler {
        void o(FakeImmersiveModel model, boolean animate) {
        }

        void h(FakeLyricTextView textView, boolean active) {
        }

        void setAodModeStyle(boolean aod) {
        }

        static void staticEntry(FakeImmersiveModel model, boolean animate) {
        }

        void timed(boolean animate, long position) {
        }
    }
}
