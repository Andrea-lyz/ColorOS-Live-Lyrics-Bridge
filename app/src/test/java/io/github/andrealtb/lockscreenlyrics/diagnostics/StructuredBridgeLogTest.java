package io.github.andrealtb.lockscreenlyrics.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class StructuredBridgeLogTest {
    @After
    public void tearDown() {
        StructuredBridgeLog.resetForTesting();
    }

    @Test
    public void formatsStableFieldOrderAndKeepsLegacyAodPhrase() {
        String formatted = BridgeLogFormatter.format(
                "INFO",
                "bridge",
                "aod",
                BridgeEvents.RECYCLER_ATTACHED,
                "com.android.systemui",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Observed LyricsRecyclerView attachment");
        assertEquals(
                "[CLL] level=INFO component=bridge area=aod event=RECYCLER_ATTACHED"
                        + " process=com.android.systemui"
                        + " message=\"Observed LyricsRecyclerView attachment\"",
                formatted);
    }

    @Test
    public void redactsTokensAndDoesNotLogWhenDebugIsOff() {
        RecordingSink sink = new RecordingSink();
        StructuredBridgeLog.configure(
                BridgeDebugConfig.disabled(),
                "com.android.systemui",
                sink,
                null);
        StructuredBridgeLog.info(
                BridgeDebugArea.RENDERER,
                BridgeEvents.RENDER_STATE_CHANGED,
                "token=abc123def456ghi789");
        assertTrue(sink.messages.isEmpty());

        StructuredBridgeLog.infoAlways(
                BridgeDebugArea.BOOTSTRAP,
                BridgeEvents.SYSTEMUI_BOOTSTRAP,
                "Loaded in com.android.systemui token=abc123def456ghi789");
        assertEquals(1, sink.messages.size());
        assertTrue(sink.messages.get(0).startsWith("[CLL] "));
        assertFalse(sink.messages.get(0).contains("abc123def456ghi789"));
        assertTrue(sink.messages.get(0).contains("<REDACTED_TOKEN>"));
    }

    @Test
    public void debugAreaGateAndWarnAlwaysEmit() {
        RecordingSink sink = new RecordingSink();
        BridgeDebugConfig config = new BridgeDebugConfig(
                BridgeDebugConfig.SCHEMA_VERSION,
                1L,
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false);
        StructuredBridgeLog.configure(config, "com.android.systemui", sink, null);
        StructuredBridgeLog.info(
                BridgeDebugArea.RENDERER,
                BridgeEvents.RENDER_STATE_CHANGED,
                "should be gated");
        StructuredBridgeLog.emitLegacyInfo("Observed LyricsRecyclerView attachment, alpha=0");
        StructuredBridgeLog.warn(
                BridgeDebugArea.RENDERER,
                "IGNORED",
                "renderer warn still emits");
        assertEquals(2, sink.messages.size());
        assertTrue(sink.messages.get(0).contains("event=RECYCLER_ATTACHED"));
        assertTrue(sink.messages.get(0).contains("Observed LyricsRecyclerView attachment"));
        assertTrue(sink.messages.get(1).contains("level=WARN"));
    }

    @Test
    public void explicitInfoUsesEventConstantWithoutClassify() {
        RecordingSink sink = new RecordingSink();
        BridgeDebugConfig config = new BridgeDebugConfig(
                BridgeDebugConfig.SCHEMA_VERSION,
                1L,
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false);
        StructuredBridgeLog.configure(config, "com.android.systemui", sink, null);
        StructuredBridgeLog.info(
                BridgeDebugArea.AOD,
                BridgeEvents.RECYCLER_ATTACHED,
                "Observed LyricsRecyclerView attachment");
        assertEquals(1, sink.messages.size());
        assertTrue(sink.messages.get(0).contains("event=RECYCLER_ATTACHED"));
        assertTrue(sink.messages.get(0).contains("Observed LyricsRecyclerView attachment"));
    }

    @Test
    public void debugSupplierIsNotEvaluatedWhenDisabled() {
        RecordingSink sink = new RecordingSink();
        StructuredBridgeLog.configure(
                BridgeDebugConfig.disabled(),
                "com.android.systemui",
                sink,
                null);
        boolean[] evaluated = {false};
        StructuredBridgeLog.debug(BridgeDebugArea.LYRIC, "DETAIL", () -> {
            evaluated[0] = true;
            return "expensive";
        });
        assertFalse(evaluated[0]);
        assertTrue(sink.messages.isEmpty());
    }

    @Test
    public void throttlerCountsSuppressedRepeats() {
        DiagnosticThrottler throttler = new DiagnosticThrottler(1_000L);
        assertTrue(throttler.shouldLog("k", 10_000L));
        assertFalse(throttler.shouldLog("k", 10_200L));
        assertFalse(throttler.shouldLog("k", 10_400L));
        assertTrue(throttler.shouldLog("k", 11_000L));
        assertEquals(2, throttler.takeSuppressed("k"));
    }

    @Test
    public void uriAndTrackDiagnosticsNeverExposeIdentityOrQuery() {
        String uri = "https://media.example/private/art.jpg?token=secret1234567890123456";
        String summary = SensitiveFieldRedactor.uriSummary(uri);
        String track = SensitiveFieldRedactor.trackHash("private-media-id|song|artist");

        assertTrue(summary.startsWith("https://media.example/sha256:"));
        assertFalse(summary.contains("private/art.jpg"));
        assertFalse(summary.contains("token="));
        assertFalse(track.contains("private-media-id"));
    }

    private static final class RecordingSink implements BridgeLogSink {
        final List<String> messages = new ArrayList<>();

        @Override
        public void log(int androidLevel, String tag, String message, Throwable throwable) {
            messages.add(message);
        }
    }
}
