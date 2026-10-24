package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
import io.github.andrealtb.lockscreenlyrics.render.LyricRenderTransactions;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricDataset;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class OfficialLyricDrawTransitionTest {
    @Test public void threeSharedCreditsStayBlankUntilTheWholeNativeDatasetChanges() throws Throwable {
        Fixture f = new Fixture();
        TextView composed = new TextView(RuntimeEnvironment.getApplication());
        TextView produced = new TextView(RuntimeEnvironment.getApplication());
        composed.setText("Composed by: Jack");
        produced.setText("Produced by: Jack");
        f.host.bind(composed);
        f.host.bind(produced);
        f.host.publish(f.host.modelB);
        f.draw(f.text);
        f.draw(composed);
        f.draw(produced);
        assertEquals(0, f.host.draws);
        assertEquals(0, f.originalDraws);
        assertEquals(0, f.host.frameLookups);
        assertEquals(0, f.host.readyFrames);
        assertEquals(0, f.host.retries);
    }

    @Test public void oldValidHolderCannotJoinNewDatasetUntilRealAdapterBind() throws Throwable {
        Fixture f = new Fixture();
        f.host.publish(f.host.modelB);
        f.host.dataset(f.host.modelB.officialDataset);
        f.draw(f.text);
        assertEquals(0, f.host.draws);
        f.host.bind(f.text);
        f.draw(f.text);
        assertEquals(1, f.host.draws);
        assertEquals(1, f.host.readyFrames);
        assertSame(f.host.modelB, f.host.lastRenderedModel);
        assertEquals(0, f.originalDraws);
    }

    @Test public void nativeDataCanBeReadyBeforeModelPublication() throws Throwable {
        Fixture f = new Fixture();
        f.host.dataset(f.host.modelB.officialDataset);
        f.host.bind(f.text);
        f.draw(f.text);
        assertEquals(0, f.host.draws);
        f.host.publish(f.host.modelB);
        f.draw(f.text);
        assertEquals(1, f.host.draws);
    }

    @Test public void waitingDatasetIsNotReleasedBySettlingOrAod() throws Throwable {
        Fixture f = new Fixture();
        f.host.publish(f.host.modelB);
        f.host.settling = true;
        f.draw(f.text);
        f.host.aod = true;
        f.draw(f.text);
        assertEquals(0, f.host.draws);
        assertEquals(0, f.originalDraws);
        assertEquals(0, f.host.retries);
    }

    @Test public void transparentCurrentDatasetStillPreparesCustomPixels() throws Throwable {
        Fixture f = new Fixture();
        f.host.recycler.setAlpha(0f);
        f.draw(f.text);
        assertEquals(1, f.host.draws);
        assertEquals(1, f.host.readyFrames);
        assertEquals(0, f.originalDraws);
    }

    @Test public void oldDatasetCannotReuseRendererFailureNativeFallback() throws Throwable {
        Fixture f = new Fixture();
        f.host.failDraw = true;
        f.draw(f.text);
        assertEquals(1, f.originalDraws);
        f.host.dataset(f.host.modelA.officialDataset);
        f.draw(f.text);
        assertEquals(1, f.originalDraws);
        assertEquals(1, f.host.draws);
        assertEquals(1, f.host.errors);
    }

    @Test public void staleCachedModelIsRejectedBeforeRendering() throws Throwable {
        Fixture f = new Fixture();
        f.host.publish(f.host.modelB);
        f.host.dataset(f.host.modelB.officialDataset);
        f.host.bind(f.text);
        f.host.cachedModel = f.host.modelA;
        f.draw(f.text);
        assertSame(f.host.modelB, f.host.lastRenderedModel);
        assertSame(f.host.modelB, f.host.lastRequestedModel);
        assertEquals(1, f.host.draws);
    }

    @Test public void ordinaryTextViewsKeepOriginalDrawWithoutDatasetLookup() throws Throwable {
        Fixture f = new Fixture();
        f.host.recycler = null;
        f.draw(f.text);
        assertEquals(1, f.originalDraws);
        assertEquals(0, f.host.bindingQueries);
    }

    @Test public void absentCustomModelKeepsOfficialFallback() throws Throwable {
        Fixture f = new Fixture();
        f.host.transactions.publish(null, null);
        f.draw(f.text);
        assertEquals(1, f.originalDraws);
        assertEquals(0, f.host.bindingQueries);
    }

    @Test public void unsupportedNativeDatasetFallsBackWithoutPersistentBlanking() throws Throwable {
        Fixture f = new Fixture();
        f.host.dataset(null);
        f.draw(f.text);
        assertEquals(1, f.originalDraws);
        assertEquals(0, f.host.draws);
    }

    private static final class Fixture {
        final TextView text = new TextView(RuntimeEnvironment.getApplication());
        final RecordingHost host = new RecordingHost();
        final OfficialLyricDrawCoordinator coordinator = new OfficialLyricDrawCoordinator(host);
        int originalDraws;

        Fixture() {
            text.setText("Lyrics by: Jack");
            host.publish(host.modelA);
            host.dataset(host.modelA.officialDataset);
            host.bind(text);
        }

        void draw(TextView view) throws Throwable {
            assertNull(coordinator.draw(view, new Canvas(), () -> {
                originalDraws++;
                return null;
            }));
        }
    }

    private static final class RecordingHost extends OfficialLyricDrawCoordinatorTest.FakeHost {
        final LyricRenderTransactions<WordLyricModel> transactions = new LyricRenderTransactions<>();
        final Object adapter = new Object();
        View recycler = new View(RuntimeEnvironment.getApplication());
        final WordLyricModel modelA = model("Maroon", "When the morning came");
        final WordLyricModel modelB = model("Anti-Hero", "I have this thing");
        WordLyricModel cachedModel;
        WordLyricModel lastRenderedModel;
        WordLyricModel lastRequestedModel;
        boolean settling;
        boolean aod;
        boolean failDraw;
        int draws;
        int readyFrames;
        int retries;
        int frameLookups;
        int bindingQueries;
        int errors;

        RecordingHost() { inspect = true; }

        void publish(WordLyricModel model) { transactions.publish(model, model.officialDataset); }
        void dataset(OfficialLyricDataset dataset) {
            LyricRenderTransactions.DatasetChange change = transactions.beginDataset(recycler, adapter, dataset);
            transactions.commitDataset(change, adapter);
        }
        void bind(TextView row) {
            String text = row.getText().toString();
            int position = text.startsWith("Lyrics") ? 1 : text.startsWith("Composed") ? 2 : 3;
            transactions.completeRowBind(transactions.beginRowBind(adapter, position), row);
        }

        @Override public WordLyricModel currentWordLyricModel() { return transactions.currentModel(); }
        @Override public View findContainingLyricsRecyclerView(TextView view) { return recycler; }
        @Override public boolean isLyricRecyclerSettleWindowActive() { return settling; }
        @Override public boolean isAodLowFrameRateLyricMode() { return aod; }
        @Override public LyricRenderTransactions.Status lyricDrawBindingStatus(
                TextView view, View surface, WordLyricModel model) {
            bindingQueries++;
            return transactions.rowStatus(surface, view, model);
        }
        @Override public DrawFrame findOfficialLyricDrawFrame(TextView view, WordLyricModel model) {
            frameLookups++;
            lastRequestedModel = model;
            return frame(model);
        }
        @Override public DrawFrame findRecentOfficialDrawFrame(TextView view, WordLyricModel model) {
            frameLookups++;
            lastRequestedModel = model;
            return frame(cachedModel == null ? model : cachedModel);
        }
        @Override public void scheduleBoundLyricFrameRetry(TextView view, WordLyricModel model) { retries++; }
        @Override public void drawWithOfficialRenderer(Canvas canvas, TextView view, DrawFrame frame) {
            draws++;
            lastRenderedModel = frame.model;
            if (failDraw) throw new IllegalStateException("test renderer failure");
        }
        @Override public void markBoundLyricFrameReady(TextView view, DrawFrame frame) { readyFrames++; }
        @Override public void onCustomDrawError(TextView view, WordLyricModel model, Throwable error) { errors++; }

        private static WordLyricModel model(String title, String verse) {
            WordLyricModel model = new WordLyricModel();
            model.officialDataset = OfficialLyricDataset.fromDisplaySource(
                    "[00:00.00]" + title + "\n[00:01.00]Lyrics by: Jack\n"
                            + "[00:02.00]Composed by: Jack\n[00:03.00]Produced by: Jack\n[00:04.00]" + verse);
            String credit = "Lyrics by: Jack";
            ArrayList<WordRange> words = new ArrayList<>();
            words.add(new WordRange(1_000L, 0, credit.length()));
            WordLine line = new WordLine(1_000L, credit, words, 2_000L);
            model.lines.add(line);
            model.officialLines.add(line);
            return model;
        }

        private static DrawFrame frame(WordLyricModel model) {
            return new DrawFrame(model, model.lines.get(0), 0, 0, 0, 1_000L, 1_000L, false, true, false);
        }
    }
}
