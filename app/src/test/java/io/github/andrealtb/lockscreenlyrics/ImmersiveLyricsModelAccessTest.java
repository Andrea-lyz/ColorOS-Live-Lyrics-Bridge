package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ImmersiveLyricsModelAccessTest {
    private static final List<FakeLine> LINES = Arrays.asList(
            new FakeLine(0L, "intro"),
            new FakeLine(1_500L, "first"),
            new FakeLine(4_000L, "second"));

    @Test
    public void readsLinesAndPositionByShape() {
        ImmersiveLyricsModelAccess.Snapshot snapshot =
                ImmersiveLyricsModelAccess.read(new FakeImmersiveModel(LINES, 1));

        assertSame(LINES, snapshot.lines);
        assertEquals(1, snapshot.index);
        assertEquals(3, snapshot.lineCount());
    }

    @Test
    public void withIndexBuildsANewModelOverTheSameLines() {
        FakeImmersiveModel original = new FakeImmersiveModel(LINES, 1);

        Object moved = ImmersiveLyricsModelAccess.withIndex(original, LINES, 2);

        assertNotSame(original, moved);
        assertTrue(moved instanceof FakeImmersiveModel);
        assertSame(LINES, ((FakeImmersiveModel) moved).lines);
        assertEquals(2, ((FakeImmersiveModel) moved).position);
        assertEquals(1, original.position);
    }

    @Test
    public void startTimesFollowListOrder() {
        assertArrayEquals(
                new long[]{0L, 1_500L, 4_000L},
                ImmersiveLyricsModelAccess.startTimes(LINES));
        assertNull(ImmersiveLyricsModelAccess.startTimes(Arrays.asList(new Object())));
        assertNull(ImmersiveLyricsModelAccess.startTimes(null));
    }

    @Test
    public void rejectsClassesThatAreNotTheModelShape() {
        assertTrue(ImmersiveLyricsModelAccess.isModelClass(FakeImmersiveModel.class));
        assertFalse(ImmersiveLyricsModelAccess.isModelClass(TwoIntModel.class));
        assertFalse(ImmersiveLyricsModelAccess.isModelClass(NoConstructorModel.class));
        assertFalse(ImmersiveLyricsModelAccess.isModelClass(boolean.class));
        assertNull(ImmersiveLyricsModelAccess.read("not a model"));
        assertNull(ImmersiveLyricsModelAccess.read(null));
    }

    @SuppressWarnings("unused")
    private static final class FakeLine {
        final long timeMillis;
        final String content;

        FakeLine(long timeMillis, String content) {
            this.timeMillis = timeMillis;
            this.content = content;
        }
    }

    @SuppressWarnings("unused")
    private static final class FakeImmersiveModel {
        final List<?> lines;
        final int position;

        FakeImmersiveModel(List<?> lines, int position) {
            this.lines = lines;
            this.position = position;
        }
    }

    @SuppressWarnings("unused")
    private static final class TwoIntModel {
        final List<?> lines;
        final int position;
        final int other;

        TwoIntModel(List<?> lines, int position) {
            this.lines = lines;
            this.position = position;
            this.other = 0;
        }
    }

    @SuppressWarnings("unused")
    private static final class NoConstructorModel {
        final List<?> lines = null;
        final int position = 0;
    }
}
