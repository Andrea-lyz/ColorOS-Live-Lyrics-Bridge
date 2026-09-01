package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SystemUiLyricPublicationGateTest {
    @Test
    public void olderParseCannotCommitAfterNewerLoadBegins() throws Exception {
        SystemUiLyricPublicationGate gate = new SystemUiLyricPublicationGate();
        long older = gate.beginLoad();
        CountDownLatch newerStarted = new CountDownLatch(1);
        CountDownLatch releaseOlder = new CountDownLatch(1);
        AtomicBoolean olderCommitted = new AtomicBoolean(true);

        Thread olderParse = new Thread(() -> {
            try {
                releaseOlder.await(2, TimeUnit.SECONDS);
                olderCommitted.set(gate.canCommit(older));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        olderParse.start();
        long newer = gate.beginLoad();
        newerStarted.countDown();
        assertTrue(newerStarted.await(1, TimeUnit.SECONDS));
        releaseOlder.countDown();
        olderParse.join(2_000L);

        assertFalse(olderCommitted.get());
        assertTrue(gate.canCommit(newer));
    }
}
