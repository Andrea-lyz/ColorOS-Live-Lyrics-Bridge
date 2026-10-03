package io.github.andrealtb.artwork.am;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AmNetworkDeadlineTest {
    @Rule public final TemporaryFolder files = new TemporaryFolder();
    private static final URI URI_VALUE = URI.create("https://mvod.itunes.apple.com/test/video.mp4");
    private static final AmHls.FilePlan PLAN = new AmHls.FilePlan(URI_VALUE, 4, 1);
    private static final URI ALBUM = URI.create("https://music.apple.com/us/album/test/1");

    @Test public void headersAreAbortedByStageDeadlineWithoutRepeatingTheSameRendition() throws Exception {
        AtomicInteger opened = new AtomicInteger();
        FakeConnection stalled = new FakeConnection("headers");
        AmNetwork network = new AmNetwork(() -> true, reason -> {}, uri -> {
            opened.incrementAndGet(); return stalled;
        }, stage -> 100);
        long start = System.nanoTime();
        try { network.file(PLAN, files.newFile(), 10, new AmNetwork.Task()); fail(); }
        catch (AmFailure failure) { assertEquals("network_stage_timeout", failure.reason); }
        assertEquals(1, opened.get());
        assertTrue("the abandoned connection is still closed", stalled.disconnected.await(1, TimeUnit.SECONDS));
        assertTrue("header block must end at deadline", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000);
    }

    @Test public void partialBodyTimeoutCanMoveToAnotherFileWithoutAppendingTheOldBytes() throws Exception {
        AtomicInteger opened = new AtomicInteger();
        AmNetwork network = new AmNetwork(() -> true, reason -> {}, uri ->
                new FakeConnection(opened.getAndIncrement() == 0 ? "body" : "none"), stage -> 100);
        File file = files.newFile();
        try { network.file(PLAN, file, 10, new AmNetwork.Task()); fail(); }
        catch (AmFailure failure) { assertEquals("network_stage_timeout", failure.reason); }
        assertEquals(2, file.length());
        network.file(PLAN, file, 10, new AmNetwork.Task());
        assertEquals(2, opened.get());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, java.nio.file.Files.readAllBytes(file.toPath()));
    }

    @Test public void lookupThatIgnoresDisconnectIsAbandonedAndRetriedAtTheStageDeadline() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger lookups = new AtomicInteger();
        List<String> traces = new CopyOnWriteArrayList<>();
        AmNetwork network = new AmNetwork(() -> true, traces::add, new AmNetwork.Connections() {
            @Override public HttpURLConnection open(URI uri) throws IOException { return new FakeConnection("none"); }
            @Override public void resolve(String host) { lookups.incrementAndGet(); awaitIgnoringInterrupts(release); }
        }, stage -> 100);
        long start = System.nanoTime();
        try { network.text(ALBUM, 1024, new AmNetwork.Task()); fail(); }
        catch (AmFailure failure) { assertEquals("network_stage_timeout", failure.reason); }
        finally { release.countDown(); }
        assertEquals("each attempt gets its own lookup instead of waiting on the stuck one", 3, lookups.get());
        String stall = traces.stream().filter(trace -> trace.startsWith("web_album_stalled_in_dns ")).findFirst().orElse("");
        assertTrue(stall, stall.contains(" budgetMs=100 "));
        assertTrue("names where the worker is stuck: " + stall, stall.contains("awaitIgnoringInterrupts"));
        assertTrue(traces.contains("retry_after_network_stage_timeout_attempt1"));
        assertTrue("an uninterruptible lookup must not hold the resolver",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
    }

    @Test public void retryAfterAStalledLookupCanStillSucceed() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger lookups = new AtomicInteger();
        AmNetwork network = new AmNetwork(() -> true, reason -> {}, new AmNetwork.Connections() {
            @Override public HttpURLConnection open(URI uri) throws IOException { return new FakeConnection("none"); }
            @Override public void resolve(String host) { if (lookups.getAndIncrement() == 0) awaitIgnoringInterrupts(release); }
        }, stage -> 100);
        try { assertEquals(4, network.text(ALBUM, 1024, new AmNetwork.Task()).length()); }
        finally { release.countDown(); }
        assertEquals(2, lookups.get());
    }

    @Test public void cancellingTheTaskReleasesTheResolverDuringAnUninterruptibleStall() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        AmNetwork network = new AmNetwork(() -> true, reason -> {}, new AmNetwork.Connections() {
            @Override public HttpURLConnection open(URI uri) throws IOException { return new FakeConnection("none"); }
            @Override public void resolve(String host) { entered.countDown(); awaitIgnoringInterrupts(release); }
        }, stage -> 10_000);
        AmNetwork.Task task = new AmNetwork.Task();
        Thread canceller = new Thread(() -> { awaitIgnoringInterrupts(entered); task.cancel(); });
        canceller.start();
        long start = System.nanoTime();
        try { network.text(ALBUM, 1024, task); fail(); }
        catch (AmFailure failure) { assertEquals("cancelled", failure.reason); }
        finally { release.countDown(); canceller.join(); }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000);
    }

    @Test public void aDisconnectThatBlocksNeverHoldsTheResolver() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger opened = new AtomicInteger();
        AmNetwork network = new AmNetwork(() -> true, reason -> {}, uri -> {
            opened.incrementAndGet();
            return new FakeConnection("none") {
                @Override public void connect() { awaitIgnoringInterrupts(release); }
                @Override public void disconnect() { awaitIgnoringInterrupts(release); }
            };
        }, stage -> 100);
        long start = System.nanoTime();
        try { network.text(ALBUM, 1024, new AmNetwork.Task()); fail(); }
        catch (AmFailure failure) { assertEquals("network_stage_timeout", failure.reason); }
        finally { release.countDown(); }
        assertEquals(3, opened.get());
        assertTrue("device logs: the waiting side never returned from disconnect()",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
    }

    /** Models a blocking name lookup: neither disconnect() nor interrupt ends it. */
    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        while (true) {
            try { if (latch.await(3, TimeUnit.SECONDS)) return; throw new AssertionError("test stall was not released"); }
            catch (InterruptedException ignored) { /* deliberately uninterruptible */ }
        }
    }

    private static class FakeConnection extends HttpURLConnection {
        private final String stall;
        private final CountDownLatch disconnected = new CountDownLatch(1);
        volatile boolean aborted;
        FakeConnection(String stall) throws IOException { super(URI_VALUE.toURL()); this.stall = stall; }
        @Override public void connect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { aborted = true; disconnected.countDown(); }
        @Override public int getResponseCode() throws IOException {
            if (stall.equals("headers")) block();
            return 200;
        }
        @Override public long getContentLengthLong() { return 4; }
        @Override public String getHeaderField(String name) { return null; }
        @Override public InputStream getInputStream() {
            if (!stall.equals("body")) return new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
            return new InputStream() {
                boolean partial;
                @Override public int read() throws IOException { block(); return -1; }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    if (partial) { block(); return -1; }
                    partial = true; bytes[offset] = 9; bytes[offset + 1] = 9; return 2;
                }
            };
        }
        private void block() throws IOException {
            try { if (!disconnected.await(2, TimeUnit.SECONDS)) throw new IOException("deadline did not disconnect"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            throw new IOException("connection closed");
        }
    }
}
