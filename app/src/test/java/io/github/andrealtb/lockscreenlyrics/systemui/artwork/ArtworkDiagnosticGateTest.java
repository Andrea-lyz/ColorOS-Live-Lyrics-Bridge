package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkDiagnosticGateTest {
    @Test public void changedStatesWithinThreeSecondsArePreserved() {
        var gate = new ArtworkDiagnosticGate();
        assertTrue(gate.accept("bound", "epoch=1", 1000));
        assertTrue(gate.accept("bound", "epoch=2", 1010));
        assertTrue(gate.accept("bound", "epoch=3", 1020));
    }

    @Test public void hostsNeverSuppressEachOthersState() {
        var card = new ArtworkDiagnosticGate();
        var immersive = new ArtworkDiagnosticGate();
        assertTrue(card.accept("bound", "epoch=1", 0));
        assertTrue(immersive.accept("bound", "epoch=1", 0));
        assertFalse(card.accept("bound", "epoch=1", 1));
        assertFalse(immersive.accept("bound", "epoch=1", 1));
    }

    @Test public void duplicateStateDoesNotSpendBurstBudget() {
        var gate = new ArtworkDiagnosticGate();
        assertTrue(gate.accept("state", "initial", 0));
        for (int i = 0; i < 1000; i++) assertFalse(gate.accept("state", "initial", 1));
        assertTrue(gate.accept("state", "changed", 2));
        assertEquals(0, gate.takeLimited());
    }

    @Test public void stormIsBoundedAndLatestSuppressedStateCanBeEmittedLater() {
        var gate = new ArtworkDiagnosticGate();
        int count = 0;
        for (int i = 0; i < 100; i++) if (gate.accept("state", "revision=" + i, 0)) count++;
        assertEquals(24, count);
        assertEquals(76, gate.takeLimited());
        assertTrue(gate.accept("state", "revision=99", 1000));
        assertFalse(gate.accept("state", "revision=99", 1001));
    }

    @Test public void clockResetDoesNotPermanentlyBlockDiagnostics() {
        var gate = new ArtworkDiagnosticGate();
        for (int i = 0; i < 24; i++) assertTrue(gate.accept("state", "revision=" + i, 10_000));
        assertTrue(gate.accept("state", "after_reset", 1));
    }
}
