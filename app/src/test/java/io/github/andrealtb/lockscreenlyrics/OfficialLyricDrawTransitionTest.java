package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import io.github.andrealtb.lockscreenlyrics.render.DrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;

/** Exercises the real coordinator with Android views and cross-song shared credit text. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class OfficialLyricDrawTransitionTest {
    @Test
    public void retiringSharedCreditCannotDrawFromRecentFrameOrOriginalText() throws Throwable {
        Fixture f = new Fixture();
        f.host.unbound = true;
        f.draw();
        assertEquals(0, f.host.draws);
        assertEquals(0, f.originalDraws);
        assertEquals(0, f.host.frameLookups);
        assertEquals(0, f.host.readyFrames);
        assertEquals(0, f.host.visibleNotes);
        assertEquals(1, f.host.forgottenFrames);
        assertEquals(1, f.host.retries);
    }

    @Test
    public void sameTextViewDrawsAgainAsSoonAsBindingRecovers() throws Throwable {
        Fixture f = new Fixture();
        f.host.unbound = true;
        f.draw();
        f.host.unbound = false;
        f.draw();
        assertEquals(1, f.host.draws);
        assertEquals(1, f.host.readyFrames);
        assertEquals(0, f.originalDraws);
    }

    @Test
    public void retiringRowsStayBlankDuringSettlingAndAod() throws Throwable {
        Fixture f = new Fixture();
        f.host.unbound = true;
        f.host.settling = true;
        f.draw();
        f.host.aod = true;
        f.draw();
        assertEquals(0, f.host.draws);
        assertEquals(0, f.originalDraws);
    }

    @Test
    public void validRowsPrepareCustomPixelsWhileRecyclerIsTransparent() throws Throwable {
        Fixture f = new Fixture();
        f.host.recycler.setAlpha(0f);
        f.draw();
        assertEquals(1, f.host.draws);
        assertEquals(1, f.host.readyFrames);
        assertEquals(0, f.originalDraws);
    }

    @Test
    public void retiringRowCannotReuseAnEarlierRendererFailureFallback() throws Throwable {
        Fixture f = new Fixture();
        f.host.failDraw = true;
        f.draw();
        assertEquals(1, f.originalDraws);
        f.host.unbound = true;
        f.draw();
        assertEquals(1, f.originalDraws);
        assertEquals(1, f.host.draws);
        assertEquals(1, f.host.errors);
    }

    @Test
    public void ordinaryTextViewsKeepOriginalDraw() throws Throwable {
        Fixture f = new Fixture();
        f.host.recycler = null;
        f.host.unbound = true;
        f.draw();
        assertEquals(1, f.originalDraws);
        assertEquals(0, f.host.bindingQueries);
    }

    @Test
    public void absentCustomModelKeepsOfficialFallback() throws Throwable {
        Fixture f = new Fixture();
        f.host.model = null;
        f.host.unbound = true;
        f.draw();
        assertEquals(1, f.originalDraws);
        assertEquals(0, f.host.bindingQueries);
    }

    private static final class Fixture {
        final TextView text = new TextView(RuntimeEnvironment.getApplication());
        final RecordingHost host = new RecordingHost();
        final OfficialLyricDrawCoordinator coordinator = new OfficialLyricDrawCoordinator(host);
        int originalDraws;

        Fixture() {
            text.setText("Recorded by: Laura Sisk at the Kitty");
        }

        void draw() throws Throwable {
            assertNull(coordinator.draw(text, new Canvas(), () -> {
                originalDraws++;
                return null;
            }));
        }
    }

    private static final class RecordingHost extends OfficialLyricDrawCoordinatorTest.FakeHost {
        View recycler = new View(RuntimeEnvironment.getApplication());
        WordLyricModel model = new WordLyricModel();
        final DrawFrame frame;
        boolean unbound;
        boolean settling;
        boolean aod;
        boolean failDraw;
        int draws;
        int readyFrames;
        int visibleNotes;
        int retries;
        int frameLookups;
        int forgottenFrames;
        int bindingQueries;
        int errors;

        RecordingHost() {
            inspect = true;
            String credit = "Recorded by: Laura Sisk at the Kitty";
            ArrayList<WordRange> words = new ArrayList<>();
            words.add(new WordRange(1_000L, 0, credit.length()));
            WordLine line = new WordLine(1_000L, credit, words, 2_000L);
            model.lines.add(line);
            model.officialLines.add(line);
            frame = new DrawFrame(model, line, 0, 0, 0, 1_000L, 1_000L, false, true, false);
        }

        @Override public WordLyricModel currentWordLyricModel() { return model; }
        @Override public View findContainingLyricsRecyclerView(TextView view) { return recycler; }
        @Override public boolean isLyricRecyclerSettleWindowActive() { return settling; }
        @Override public boolean isAodLowFrameRateLyricMode() { return aod; }
        @Override public boolean isUnboundLyricsRecyclerRow(TextView view) {
            bindingQueries++;
            return unbound;
        }
        @Override public DrawFrame findOfficialLyricDrawFrame(TextView view) {
            frameLookups++;
            return frame;
        }
        @Override public DrawFrame findRecentOfficialDrawFrame(TextView view) {
            frameLookups++;
            return frame;
        }
        @Override public void forgetRecentOfficialDrawFrame(TextView view) { forgottenFrames++; }
        @Override public void scheduleBoundLyricFrameRetry(TextView view, WordLyricModel model) {
            retries++;
        }
        @Override public void noteVisibleLockscreenLyricTextView(TextView view, View rv, long time) {
            visibleNotes++;
        }
        @Override public void drawWithOfficialRenderer(Canvas canvas, TextView view, DrawFrame frame) {
            draws++;
            if (failDraw) throw new IllegalStateException("test renderer failure");
        }
        @Override public void markBoundLyricFrameReady(TextView view, DrawFrame frame) { readyFrames++; }
        @Override public void onCustomDrawError(TextView view, WordLyricModel model, Throwable error) {
            errors++;
        }
    }
}
