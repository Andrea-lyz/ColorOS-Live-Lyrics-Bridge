package io.github.andrealtb.lockscreenlyrics;

import android.content.Context;
import android.widget.TextView;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OfficialLyricRowStyleMethodPolicyTest {
    @Test
    public void coloros17ActiveStyleHelperMatches() throws Exception {
        assertTrue(matches(FakeLyricsRecycler.class.getDeclaredMethod(
                "h", FakeLyricTextView.class, boolean.class)));
    }

    @Test
    public void pivotAndScaleHelpersDoNotMatch() throws Exception {
        assertFalse(matches(FakeLyricsRecycler.class.getDeclaredMethod(
                "d", FakeLyricsRecycler.class, FakeLyricTextView.class)));
        assertFalse(matches(FakeLyricsRecycler.class.getDeclaredMethod(
                "m", FakeLyricTextView.class)));
        assertFalse(matches(FakeLyricsRecycler.class.getDeclaredMethod(
                "g", Object.class, int.class)));
    }

    @Test
    public void staticOrNonVoidOrWrongFlagShapesDoNotMatch() throws Exception {
        assertFalse(matches(FakeLyricsRecycler.class.getDeclaredMethod(
                "staticStyle", FakeLyricTextView.class, boolean.class)));
        assertFalse(matches(FakeLyricsRecycler.class.getDeclaredMethod(
                "measure", FakeLyricTextView.class, boolean.class)));
        assertFalse(matches(FakeLyricsRecycler.class.getDeclaredMethod(
                "timed", FakeLyricTextView.class, long.class)));
    }

    @Test
    public void inheritedHelperIsIgnored() throws Exception {
        Method inherited = FakeRecyclerBase.class.getDeclaredMethod(
                "baseStyle", FakeLyricTextView.class, boolean.class);
        assertFalse(OfficialLyricRowStyleMethodPolicy.matches(inherited, FakeLyricsRecycler.class));
        assertFalse(OfficialLyricRowStyleMethodPolicy.matches(null, FakeLyricsRecycler.class));
    }

    private static boolean matches(Method method) {
        return OfficialLyricRowStyleMethodPolicy.matches(method, FakeLyricsRecycler.class);
    }

    @SuppressWarnings("unused")
    private static final class FakeLyricTextView extends TextView {
        FakeLyricTextView(Context context) {
            super(context);
        }
    }

    @SuppressWarnings("unused")
    private static class FakeRecyclerBase {
        void baseStyle(FakeLyricTextView textView, boolean active) {
        }
    }

    @SuppressWarnings("unused")
    private static final class FakeLyricsRecycler extends FakeRecyclerBase {
        void h(FakeLyricTextView textView, boolean active) {
        }

        static void d(FakeLyricsRecycler recycler, FakeLyricTextView textView) {
        }

        void m(FakeLyricTextView textView) {
        }

        void g(Object holder, int position) {
        }

        static void staticStyle(FakeLyricTextView textView, boolean active) {
        }

        int measure(FakeLyricTextView textView, boolean active) {
            return 0;
        }

        void timed(FakeLyricTextView textView, long position) {
        }
    }
}
