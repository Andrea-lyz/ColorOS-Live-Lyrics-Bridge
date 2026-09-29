package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KuWoSameTrackLyricRetentionTest {
    @Test
    public void firstSightingClearsIdentityEvenWhenIncomingHasLyrics() {
        KuWoSameTrackLyricRetention retention = new KuWoSameTrackLyricRetention();
        Object lyric = new Object();
        KuWoSameTrackLyricRetention.Result first = retention.evaluateAndCommit(
                "song|artist",
                "Song",
                "Artist",
                lyric,
                4,
                true);
        assertEquals(
                KuWoSameTrackLyricRetention.Action.CLEAR_FOR_TRACK_CHANGE,
                first.action);
        assertFalse(first.repairAlbumArt);
        assertNull(first.lyricToRestore);
        assertNull(retention.rememberedLyric());
        assertEquals("song|artist", retention.rememberedTrackKey());
        assertEquals("Song", retention.rememberedTitle());
    }

    @Test
    public void remembersCurrentModelThenRestoresEmptyRebuild() {
        KuWoSameTrackLyricRetention retention = rememberCurrent(
                "your type|carly rae jepsen",
                "Your Type",
                "Carly Rae Jepsen");
        Object lyric = retention.rememberedLyric();

        KuWoSameTrackLyricRetention.Result restore = retention.evaluateAndCommit(
                "your type|carly rae jepsen",
                "Your Type",
                "Carly Rae Jepsen",
                null,
                0,
                true);
        assertEquals(KuWoSameTrackLyricRetention.Action.RESTORE_EMPTY_MODEL, restore.action);
        assertSame(lyric, restore.lyricToRestore);
        assertEquals("Your Type", restore.repairTitle);
        assertSame(lyric, retention.rememberedLyric());
    }

    @Test
    public void carLyricMutationRetainsRememberedLyricWithoutUpdatingIdentity() {
        KuWoSameTrackLyricRetention retention = rememberCurrent(
                "your type|carly rae jepsen",
                "Your Type",
                "Carly Rae Jepsen");
        Object lyric = retention.rememberedLyric();

        KuWoSameTrackLyricRetention.Result retained = retention.evaluateAndCommit(
                "i'm sorry i'm sorry i love you|your type-carly rae jepsen",
                "I'm sorry I'm sorry I love you",
                "Your Type-Carly Rae Jepsen",
                null,
                0,
                true);
        assertEquals(
                KuWoSameTrackLyricRetention.Action.RETAIN_CAR_LYRIC_MUTATION,
                retained.action);
        assertSame(lyric, retained.lyricToRestore);
        assertEquals("Your Type", retained.repairTitle);
        assertEquals("Carly Rae Jepsen", retained.repairArtist);
        assertEquals("your type|carly rae jepsen", retention.rememberedTrackKey());
        assertEquals("Your Type", retention.rememberedTitle());
        assertSame(lyric, retention.rememberedLyric());
    }

    @Test
    public void realTrackChangeClearsRetention() {
        KuWoSameTrackLyricRetention retention = rememberCurrent(
                "renegades|one ok rock",
                "Renegades",
                "ONE OK ROCK");
        assertTrue(retention.rememberedLyric() != null);

        KuWoSameTrackLyricRetention.Result cleared = retention.evaluateAndCommit(
                "360|charli xcx",
                "360",
                "Charli xcx",
                new Object(),
                9,
                true);
        assertEquals(KuWoSameTrackLyricRetention.Action.CLEAR_FOR_TRACK_CHANGE, cleared.action);
        assertFalse(cleared.repairAlbumArt);
        assertNull(cleared.lyricToRestore);
        assertNull(retention.rememberedLyric());
        assertEquals("360|charli xcx", retention.rememberedTrackKey());
        assertEquals("360", retention.rememberedTitle());
    }

    @Test
    public void emptyIncomingKeyDoesNotCountAsTrackChange() {
        KuWoSameTrackLyricRetention retention = rememberCurrent("song|artist", "Song", "Artist");
        Object second = new Object();
        KuWoSameTrackLyricRetention.Result remembered = retention.evaluateAndCommit(
                "",
                "Song",
                "Artist",
                second,
                3,
                true);
        assertEquals(KuWoSameTrackLyricRetention.Action.REMEMBER_CURRENT, remembered.action);
        assertEquals("song|artist", retention.rememberedTrackKey());
        assertSame(second, retention.rememberedLyric());
    }

    @Test
    public void decideDoesNotCommitUntilCallerSucceeds() {
        KuWoSameTrackLyricRetention retention = rememberCurrent("a|b", "A", "B");
        Object lyric = retention.rememberedLyric();
        KuWoSameTrackLyricRetention.Result cleared = retention.decide(
                "c|d",
                "C",
                "D",
                new Object(),
                4,
                true);
        assertEquals(
                KuWoSameTrackLyricRetention.Action.CLEAR_FOR_TRACK_CHANGE,
                cleared.action);
        assertSame(lyric, retention.rememberedLyric());
        retention.commit(cleared, "c|d", "C", "D", new Object());
        assertNull(retention.rememberedLyric());
        assertEquals("c|d", retention.rememberedTrackKey());
    }

    @Test
    public void carLyricMutationBeforeRememberedLyricStillRequestsRepair() {
        KuWoSameTrackLyricRetention retention = new KuWoSameTrackLyricRetention();
        retention.evaluateAndCommit(
                "your type|carly rae jepsen",
                "Your Type",
                "Carly Rae Jepsen",
                null,
                0,
                true);
        KuWoSameTrackLyricRetention.Result retained = retention.evaluateAndCommit(
                "i'm sorry i'm sorry i love you|your type-carly rae jepsen",
                "I'm sorry I'm sorry I love you",
                "Your Type-Carly Rae Jepsen",
                null,
                0,
                true);
        assertEquals(
                KuWoSameTrackLyricRetention.Action.RETAIN_CAR_LYRIC_MUTATION,
                retained.action);
        assertNull(retained.lyricToRestore);
        assertTrue(retained.repairAlbumArt);
        assertEquals("Your Type", retained.repairTitle);
    }

    @Test
    public void sameTrackRebuildThatDropsShownLyricSupportAsksForRestore() {
        KuWoSameTrackLyricRetention retention = rememberCurrent("song|artist", "Song", "Artist");
        Object lyric = retention.rememberedLyric();

        KuWoSameTrackLyricRetention.Result rebuilt = retention.evaluateAndCommit(
                "song|artist",
                "Song",
                "Artist",
                lyric,
                8,
                false);
        assertEquals(KuWoSameTrackLyricRetention.Action.REMEMBER_CURRENT, rebuilt.action);
        assertTrue(rebuilt.restoreLyricSupport);
        assertNull(rebuilt.lyricToRestore);
        assertSame(lyric, retention.rememberedLyric());
    }

    @Test
    public void trackThatNeverShowedLyricSupportIsLeftAlone() {
        KuWoSameTrackLyricRetention retention = new KuWoSameTrackLyricRetention();
        Object lyric = new Object();
        retention.evaluateAndCommit("song|artist", "Song", "Artist", lyric, 8, false);
        for (int rebuild = 0; rebuild < 2; rebuild++) {
            KuWoSameTrackLyricRetention.Result result = retention.evaluateAndCommit(
                    "song|artist",
                    "Song",
                    "Artist",
                    lyric,
                    8,
                    false);
            assertEquals(KuWoSameTrackLyricRetention.Action.REMEMBER_CURRENT, result.action);
            assertFalse(result.restoreLyricSupport);
        }
    }

    @Test
    public void trackChangeForgetsShownLyricSupport() {
        KuWoSameTrackLyricRetention retention = rememberCurrent("a|b", "A", "B");
        retention.evaluateAndCommit("c|d", "C", "D", new Object(), 5, true);

        KuWoSameTrackLyricRetention.Result next = retention.evaluateAndCommit(
                "c|d",
                "C",
                "D",
                new Object(),
                5,
                false);
        assertEquals(KuWoSameTrackLyricRetention.Action.REMEMBER_CURRENT, next.action);
        assertFalse(next.restoreLyricSupport);
    }

    private static KuWoSameTrackLyricRetention rememberCurrent(
            String trackKey,
            String title,
            String artist) {
        KuWoSameTrackLyricRetention retention = new KuWoSameTrackLyricRetention();
        Object lyric = new Object();
        retention.evaluateAndCommit(trackKey, title, artist, lyric, 1, true);
        KuWoSameTrackLyricRetention.Result remembered = retention.evaluateAndCommit(
                trackKey,
                title,
                artist,
                lyric,
                8,
                true);
        assertEquals(KuWoSameTrackLyricRetention.Action.REMEMBER_CURRENT, remembered.action);
        assertSame(lyric, retention.rememberedLyric());
        return retention;
    }
}
