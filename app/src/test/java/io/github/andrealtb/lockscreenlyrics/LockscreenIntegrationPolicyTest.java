package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LockscreenIntegrationPolicyTest {
    @Test
    public void repeatedLyricTextStillMatchesTheCurrentlyActiveLine() {
        String dorothea = "Hey Dorothea do you ever stop and think about me";

        assertTrue(LockscreenIntegrationPolicy.activeTextMatches(dorothea, dorothea));
    }

    @Test
    public void parsesOfficialCurrentLyricIndexFromSeedlingLog() {
        String message = "LyricsRecyclerView-->setCurrentLyric p:5, c:4, a:true, aod: true";

        assertEquals(5, LockscreenIntegrationPolicy.parseTaggedNonNegativeInt(message, "p:"));
        assertEquals(4, LockscreenIntegrationPolicy.parseTaggedNonNegativeInt(message, "c:"));
        assertEquals(-1, LockscreenIntegrationPolicy.parseTaggedNonNegativeInt(message, "missing:"));
    }

    @Test
    public void playingPositionUsesMediaSessionMonotonicClock() {
        assertEquals(5_161L, LockscreenIntegrationPolicy.extrapolatePlaybackPosition(
                true,
                161L,
                79_635_159L,
                1f,
                79_640_159L));
        assertEquals(6_411L, LockscreenIntegrationPolicy.extrapolatePlaybackPosition(
                true,
                161L,
                79_635_159L,
                1.25f,
                79_640_159L));
    }

    @Test
    public void pausedPositionDoesNotAdvanceWithElapsedRealtime() {
        assertEquals(27_696L, LockscreenIntegrationPolicy.extrapolatePlaybackPosition(
                false,
                27_696L,
                79_633_396L,
                1f,
                79_640_159L));
    }

    @Test
    public void playbackResetNearZeroStartsATrackHandoff() {
        assertTrue(LockscreenIntegrationPolicy.isLikelyPlaybackTrackRestart(85_916L, 53L));
        assertEquals(false, LockscreenIntegrationPolicy.isLikelyPlaybackTrackRestart(6_892L, 6_897L));
        assertEquals(false, LockscreenIntegrationPolicy.isLikelyPlaybackTrackRestart(7_000L, 20L));
    }

    @Test
    public void thirdWrappedLineSlidesIntoTwoLineWindow() {
        assertEquals(0, LockscreenIntegrationPolicy.clampSlidingWindowStart(0, 3, 2));
        assertEquals(1, LockscreenIntegrationPolicy.clampSlidingWindowStart(1, 3, 2));
        assertEquals(1, LockscreenIntegrationPolicy.clampSlidingWindowStart(2, 3, 2));
    }

    @Test
    public void lineTimedLyricKeepsTheVisibleWindowUntilProgressReachesHiddenLine() {
        assertEquals(0, LockscreenIntegrationPolicy.lineTimedSlidingWindowStart(
                6_790L,
                6_790L,
                12_410L,
                3,
                2));
        assertEquals(0, LockscreenIntegrationPolicy.lineTimedSlidingWindowStart(
                9_000L,
                6_790L,
                12_410L,
                3,
                2));
        assertEquals(1, LockscreenIntegrationPolicy.lineTimedSlidingWindowStart(
                11_000L,
                6_790L,
                12_410L,
                3,
                2));
        assertEquals(1, LockscreenIntegrationPolicy.lineTimedSlidingWindowStart(
                12_300L,
                6_790L,
                12_410L,
                3,
                2));
    }

    @Test
    public void lineTimedLyricUsesRenderedLineWidthsForWindowTiming() {
        float[] widths = {60f, 20f, 20f};

        assertEquals(0, LockscreenIntegrationPolicy.lineTimedSlidingWindowStart(
                5_000L,
                0L,
                10_000L,
                widths,
                3,
                2));
        assertEquals(1, LockscreenIntegrationPolicy.lineTimedSlidingWindowStart(
                8_100L,
                0L,
                10_000L,
                widths,
                3,
                2));
    }

    @Test
    public void capturedLyricTakesPriorityOverPlayerLyricInfo() {
        assertEquals(
                LockscreenIntegrationPolicy.LyricInfoSource.MODULE_CAPTURE,
                LockscreenIntegrationPolicy.chooseLyricInfoSource(true, false, true));
    }

    @Test
    public void explicitPlayerIntegrationTakesPriorityOverModuleCapture() {
        assertEquals(
                LockscreenIntegrationPolicy.LyricInfoSource.PLAYER_INTEGRATION,
                LockscreenIntegrationPolicy.chooseLyricInfoSource(true, true, true));
    }

    @Test
    public void playerLyricInfoIsOnlyUsedAsFallback() {
        assertEquals(
                LockscreenIntegrationPolicy.LyricInfoSource.PLAYER_FALLBACK,
                LockscreenIntegrationPolicy.chooseLyricInfoSource(true, false, false));
        assertEquals(
                LockscreenIntegrationPolicy.LyricInfoSource.NONE,
                LockscreenIntegrationPolicy.chooseLyricInfoSource(false, false, false));
    }

    @Test
    public void oplusHistoryIntegrationKeepsOfficialAndExplicitPlayers() {
        assertTrue(LockscreenIntegrationPolicy.shouldEnableOplusHistoryIntegration(
                true, false, false));
        assertTrue(LockscreenIntegrationPolicy.shouldEnableOplusHistoryIntegration(
                false, true, false));
        assertTrue(LockscreenIntegrationPolicy.shouldEnableOplusHistoryIntegration(
                false, false, true));
        assertFalse(LockscreenIntegrationPolicy.shouldEnableOplusHistoryIntegration(
                false, false, false));
    }

    @Test
    public void debounceAcceptsOnlyEventsOutsideWindow() {
        assertTrue(LockscreenIntegrationPolicy.shouldAcceptDebouncedEvent(1_000L, 0L, 1_200L));
        assertFalse(LockscreenIntegrationPolicy.shouldAcceptDebouncedEvent(1_500L, 1_000L, 1_200L));
        assertTrue(LockscreenIntegrationPolicy.shouldAcceptDebouncedEvent(2_200L, 1_000L, 1_200L));
        assertTrue(LockscreenIntegrationPolicy.shouldAcceptDebouncedEvent(900L, 1_000L, 1_200L));
    }

    @Test
    public void wordTimedAndPlainSourceVariantsAreNotTranslations() {
        assertTrue(LockscreenIntegrationPolicy.sameLyricVariant(
                "Put your lips close to mine",
                "Put your lips close to mine (close to mine)"));
        assertTrue(LockscreenIntegrationPolicy.sameLyricVariant(
                "It's been a long time",
                "Its been a long time"));
    }

    @Test
    public void distinctLanguageLineRemainsATranslation() {
        assertEquals(false, LockscreenIntegrationPolicy.sameLyricVariant(
                "Put your lips close to mine",
                "请靠近我 轻吻我的双唇"));
    }

    @Test
    public void labelledProductionDetailAfterLongIntroIsNotALyric() {
        assertTrue(LockscreenIntegrationPolicy.isProductionDetailLine(
                "Produced by：Christopher Rowe/Taylor Swift",
                26_211L));
        assertTrue(LockscreenIntegrationPolicy.isProductionDetailLine(
                "人声录音棚：薛峰工作室",
                16_000L));
    }

    @Test
    public void ordinaryColonLyricIsNotAProductionDetail() {
        assertEquals(false, LockscreenIntegrationPolicy.isProductionDetailLine(
                "I said: come home",
                26_211L));
    }

    @Test
    public void saltLyricRelayKeepsStableLyricInfo() {
        assertTrue(LockscreenIntegrationPolicy.shouldPreserveStableLyricInfoForRelay(
                true,
                false,
                true,
                true,
                true));
    }

    @Test
    public void realTrackChangeIsNotTreatedAsSaltLyricRelay() {
        assertEquals(false, LockscreenIntegrationPolicy.shouldPreserveStableLyricInfoForRelay(
                true,
                true,
                true,
                true,
                true));
    }

    @Test
    public void duplicateEndTagDoesNotTurnTranslationIntoWordTiming() {
        assertEquals(false, LockscreenIntegrationPolicy.hasProgressiveInlineTiming(
                1,
                24_850L,
                24_850L,
                24_850L,
                -1L));
        assertTrue(LockscreenIntegrationPolicy.hasProgressiveInlineTiming(
                3,
                21_200L,
                23_700L,
                21_200L,
                24_200L));
    }

    @Test
    public void repeatedSameTimestampSegmentsAreLineTimed() {
        assertEquals(false, LockscreenIntegrationPolicy.hasProgressiveInlineTiming(
                12,
                6_100L,
                6_100L,
                6_100L,
                -1L));
    }

    @Test
    public void spacedOpeningTitleArtistCreditIsFilteredAfterFiveSeconds() {
        assertTrue(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                "Sweeter Than Fiction (Taylor's Version) - Taylor Swift",
                6_100L));
    }

    @Test
    public void hyphenatedOpeningVocalIsNotATitleArtistCredit() {
        assertEquals(false, LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                "I-I-I-I I-I-I-I",
                2_412L));
    }

    @Test
    public void delayedTranslationImmediatelyBeforeNextLineAttachesBackward() {
        assertTrue(LockscreenIntegrationPolicy.shouldAttachDelayedTranslation(
                true,
                true,
                21_200L,
                24_200L,
                24_850L,
                24_860L));
        assertTrue(LockscreenIntegrationPolicy.shouldAttachDelayedTranslation(
                true,
                true,
                164_680L,
                174_890L,
                194_130L,
                194_140L));
    }

    @Test
    public void ordinaryFollowingMainLineIsNotAttachedAsTranslation() {
        assertEquals(false, LockscreenIntegrationPolicy.shouldAttachDelayedTranslation(
                true,
                false,
                21_200L,
                24_200L,
                24_860L,
                29_860L));
    }
}
