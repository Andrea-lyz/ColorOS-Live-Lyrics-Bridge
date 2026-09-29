package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.widget.TextView;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public final class OfficialLyricScalePivotMethodPolicyTest {
    @Test
    public void coloros16InstanceHelperUsesFirstArgument() throws Exception {
        assertEquals(0, indexOf(FakeLyricsRecycler.class.getDeclaredMethod(
                "m", FakeLyricTextView.class)));
    }

    @Test
    public void coloros17StaticHelperUsesSecondArgument() throws Exception {
        assertEquals(1, indexOf(FakeLyricsRecycler.class.getDeclaredMethod(
                "d", FakeLyricsRecycler.class, FakeLyricTextView.class)));
    }

    @Test
    public void renderEffectHelperIsNotAPivotHelper() throws Exception {
        assertEquals(
                OfficialLyricScalePivotMethodPolicy.NOT_A_PIVOT_METHOD,
                indexOf(FakeLyricsRecycler.class.getDeclaredMethod(
                        "h", FakeLyricTextView.class, boolean.class)));
    }

    @Test
    public void staticHelperMustTakeItsOwnRecyclerFirst() throws Exception {
        assertEquals(
                OfficialLyricScalePivotMethodPolicy.NOT_A_PIVOT_METHOD,
                indexOf(FakeLyricsRecycler.class.getDeclaredMethod(
                        "foreign", Object.class, FakeLyricTextView.class)));
    }

    @Test
    public void nonVoidAndNonTextViewMethodsAreRejected() throws Exception {
        assertEquals(
                OfficialLyricScalePivotMethodPolicy.NOT_A_PIVOT_METHOD,
                indexOf(FakeLyricsRecycler.class.getDeclaredMethod(
                        "measure", FakeLyricTextView.class)));
        assertEquals(
                OfficialLyricScalePivotMethodPolicy.NOT_A_PIVOT_METHOD,
                indexOf(FakeLyricsRecycler.class.getDeclaredMethod(
                        "g", Object.class, int.class)));
        assertEquals(
                OfficialLyricScalePivotMethodPolicy.NOT_A_PIVOT_METHOD,
                indexOf(null));
    }

    private static int indexOf(Method method) {
        return OfficialLyricScalePivotMethodPolicy.textViewArgumentIndex(method);
    }

    @SuppressWarnings("unused")
    private static final class FakeLyricTextView extends TextView {
        FakeLyricTextView(Context context) {
            super(context);
        }
    }

    @SuppressWarnings("unused")
    private static final class FakeLyricsRecycler {
        void m(FakeLyricTextView textView) {
        }

        static void d(FakeLyricsRecycler recycler, FakeLyricTextView textView) {
        }

        void h(FakeLyricTextView textView, boolean active) {
        }

        static void foreign(Object recycler, FakeLyricTextView textView) {
        }

        int measure(FakeLyricTextView textView) {
            return 0;
        }

        void g(Object holder, int position) {
        }
    }
}
