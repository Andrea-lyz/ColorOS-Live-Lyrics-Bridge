package io.github.andrealtb.lockscreenlyrics.diagnostics;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34, manifest = Config.NONE)
public class ShortDiagnosticSessionTest {
    private final AtomicLong clock = new AtomicLong(100L);
    private final ShortDiagnosticSession session = new ShortDiagnosticSession(clock::get);
    private void record(Reason reason, Outcome outcome) {
        session.record(Feature.CHAR_LIFT, Truth.YES, Truth.UNKNOWN, Truth.NO, Path.CUSTOM, outcome, reason, Scope.CHAR_LIFT_GATE, clock.get());
    }
    @Test public void detailedRecordingIsOffByDefault() {
        record(Reason.FRAME_REJECTED, Outcome.REJECTED);
        assertEquals(0, session.retainedEvents()); assertFalse(session.active());
    }
    @Test public void duplicateStartCannotEraseAnActiveWindow() {
        String id = session.start(); record(Reason.FRAME_REJECTED, Outcome.REJECTED);
        assertEquals(id, session.start()); assertEquals(1, session.retainedEvents());
    }
    @Test public void expiryReallyStopsFurtherRecording() {
        session.start(); clock.set(60_101); assertFalse(session.active());
        record(Reason.NONE, Outcome.PREPARED); assertEquals(0, session.retainedEvents());
    }
    @Test public void wrongSessionCannotMarkOrStopAnotherWindow() {
        session.start(); assertTrue(session.mark("old").startsWith("STALE"));
        assertTrue(session.stop("old").startsWith("STALE")); assertTrue(session.active());
    }
    @Test public void stopIsIdempotentAndNewWindowGetsNewIdentity() {
        String first = session.start(); assertEquals("STOPPED", session.stop(first));
        assertEquals("STOPPED", session.stop(first)); assertFalse(session.active());
        assertNotEquals(first, session.start());
    }
    @Test public void eventAndMarkerBudgetsAreBounded() {
        String id = session.start();
        for (int i = 0; i < 180; i++) {
            clock.set(100L + (i / 7) * 501L);
            session.record(Feature.values()[i % 7], Truth.YES, Truth.YES, Truth.YES, Path.CUSTOM,
                    (i / 7) % 2 == 0 ? Outcome.PREPARED : Outcome.DRAW_COMMANDS,
                    Reason.NONE, Scope.RENDER_DISPATCH, clock.get());
        }
        assertEquals(128, session.retainedEvents());
        assertTrue(session.droppedEvents() > 0);
        id = session.start();
        for (int i = 0; i < 100; i++) session.mark(id);
        assertTrue(session.retainedEvents() <= 128);
    }
    @Test public void fastTransitionsAreSampledNotUnbounded() throws Exception {
        session.start();
        for (int i = 0; i < 100_000; i++) record(i % 2 == 0 ? Reason.FRAME_REJECTED : Reason.NONE, Outcome.WAITING);
        assertTrue(session.retainedEvents() <= 2);
        String output = session.summaryJson(new Bundle());
        assertTrue(output.contains("SAMPLED_PARTIAL")); assertTrue(output.contains("suppressedTransitions"));
    }
    @Test public void exportedEventsContainOnlyTypedMetadataAndNoFreeText() throws Exception {
        String id = session.start(); session.mark(id); record(Reason.FRAME_REJECTED, Outcome.REJECTED);
        Bundle runtime = new Bundle(); runtime.putString("rawLyric", "PRIVATE_LYRIC_91023");
        runtime.putString("token", "PRIVATE_TOKEN_91023"); runtime.putString("uri", "/storage/private/91023");
        String output = session.summaryJson(runtime);
        assertFalse(output.contains("PRIVATE_")); assertFalse(output.contains("/storage/"));
        assertTrue(output.contains("USER_MARK")); assertTrue(output.contains("FRAME_REJECTED"));
    }
}
