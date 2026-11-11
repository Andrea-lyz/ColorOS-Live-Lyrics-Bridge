package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import static org.junit.Assert.assertEquals;

import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ArtworkLyricColorTargetsTest {
    private static final class Row extends View {
        int invalidations;
        Row() { super(RuntimeEnvironment.getApplication()); }
        @Override public void postInvalidateOnAnimation() { invalidations++; }
    }

    @Test public void paletteUpdateInvalidatesTheActualRowsNotJustTheirParent() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        FrameLayout container = new FrameLayout(RuntimeEnvironment.getApplication());
        root.addView(container);
        Row lyric = new Row();
        Row unrelated = new Row();
        container.addView(lyric);
        container.addView(unrelated);
        ArtworkLyricColorTargets targets = new ArtworkLyricColorTargets();
        targets.watch(lyric);
        targets.watch(lyric);
        targets.invalidate(root);
        assertEquals(1, lyric.invalidations);
        assertEquals(0, unrelated.invalidations);
    }

    @Test public void recycledRowsCannotReceiveThePreviousCardsUpdates() {
        FrameLayout oldRoot = new FrameLayout(RuntimeEnvironment.getApplication());
        FrameLayout newRoot = new FrameLayout(RuntimeEnvironment.getApplication());
        Row lyric = new Row();
        oldRoot.addView(lyric);
        ArtworkLyricColorTargets targets = new ArtworkLyricColorTargets();
        targets.watch(lyric);
        oldRoot.removeView(lyric);
        newRoot.addView(lyric);
        targets.invalidate(oldRoot);
        newRoot.removeView(lyric);
        oldRoot.addView(lyric);
        targets.invalidate(oldRoot);
        assertEquals(0, lyric.invalidations);
    }

    @Test public void releaseDiscardsSubscribersUntilTheyDrawAgain() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        Row lyric = new Row();
        root.addView(lyric);
        ArtworkLyricColorTargets targets = new ArtworkLyricColorTargets();
        targets.watch(lyric);
        targets.clear();
        targets.invalidate(root);
        assertEquals(0, lyric.invalidations);
        targets.watch(lyric);
        targets.invalidate(root);
        assertEquals(1, lyric.invalidations);
    }

    @Test public void missingRootDiscardsSubscribers() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        Row lyric = new Row();
        root.addView(lyric);
        ArtworkLyricColorTargets targets = new ArtworkLyricColorTargets();
        targets.watch(lyric);
        targets.invalidate(null);
        targets.invalidate(root);
        assertEquals(0, lyric.invalidations);
    }
}
