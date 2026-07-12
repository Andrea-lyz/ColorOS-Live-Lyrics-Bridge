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
    public void pausedAndBufferingPositionJumpsRealignLyrics() {
        assertTrue(LockscreenIntegrationPolicy.shouldRealignAfterPlaybackPositionJump(
                2, 26_000L, 17_000L, 1_500L));
        assertTrue(LockscreenIntegrationPolicy.shouldRealignAfterPlaybackPositionJump(
                6, 17_000L, 7_621L, 1_500L));
        assertTrue(LockscreenIntegrationPolicy.shouldRealignAfterPlaybackPositionJump(
                3, 1_000L, 69_000L, 1_500L));
        assertFalse(LockscreenIntegrationPolicy.shouldRealignAfterPlaybackPositionJump(
                0, 17_000L, 0L, 1_500L));
        assertFalse(LockscreenIntegrationPolicy.shouldRealignAfterPlaybackPositionJump(
                2, 17_000L, 16_000L, 1_500L));
    }

    @Test
    public void stalePowerampScaleIndexYieldsToEstablishedProgressLine() {
        assertTrue(LockscreenIntegrationPolicy.shouldPreferProgressScaleForStalePowerampIndex(
                true, 117_617L, 125_833L, 128_001L, 420L));
        assertFalse(LockscreenIntegrationPolicy.shouldPreferProgressScaleForStalePowerampIndex(
                true, 117_617L, 125_833L, 126_100L, 420L));
        assertFalse(LockscreenIntegrationPolicy.shouldPreferProgressScaleForStalePowerampIndex(
                false, 117_617L, 125_833L, 128_001L, 420L));
        assertFalse(LockscreenIntegrationPolicy.shouldPreferProgressScaleForStalePowerampIndex(
                true, 129_535L, 125_833L, 128_001L, 420L));
    }

    @Test
    public void samePowerampTrackReattachKeepsNativePlaybackPosition() {
        assertTrue(LockscreenIntegrationPolicy.shouldPreservePowerampPositionForSameTrackReattach(
                true, true, true, true));
        assertTrue(LockscreenIntegrationPolicy.shouldPreservePowerampPositionForSameTrackReattach(
                false, false, true, true));
        assertFalse(LockscreenIntegrationPolicy.shouldPreservePowerampPositionForSameTrackReattach(
                true, false, true, true));
        assertFalse(LockscreenIntegrationPolicy.shouldPreservePowerampPositionForSameTrackReattach(
                true, true, false, true));
        assertFalse(LockscreenIntegrationPolicy.shouldPreservePowerampPositionForSameTrackReattach(
                true, true, true, false));
    }

    @Test
    public void powerampTrackEventTemporarilyMakesNativePositionAuthoritative() {
        assertTrue(LockscreenIntegrationPolicy.shouldTrustPowerampNativePosition(
                14_900_397L, 14_902_357L));
        assertFalse(LockscreenIntegrationPolicy.shouldTrustPowerampNativePosition(
                14_902_358L, 14_902_357L));
        assertFalse(LockscreenIntegrationPolicy.shouldTrustPowerampNativePosition(
                10L, 0L));
    }

    @Test
    public void onlyLyricRecyclerLayoutNotificationsAreGuarded() {
        assertTrue(LockscreenIntegrationPolicy.isLyricsRecyclerComputingLayoutException(
                new IllegalStateException(
                        "Cannot call this method while RecyclerView is computing a layout "
                                + "or scrolling LyricsRecyclerView")));
        assertFalse(LockscreenIntegrationPolicy.isLyricsRecyclerComputingLayoutException(
                new IllegalStateException(
                        "Cannot call this method while RecyclerView is computing a layout")));
        assertFalse(LockscreenIntegrationPolicy.isLyricsRecyclerComputingLayoutException(
                new IllegalStateException("LyricsRecyclerView was detached")));
    }

    @Test
    public void providerBroadcastPayloadLimitsRejectSingleAndAggregateOversizeData() {
        assertTrue(LockscreenIntegrationPolicy.isExternalLyricPayloadSizeAcceptable(
                20_000, 10_000, 100_000, 20_000, 128,
                1_500_000, 3_000_000, 16_384));
        assertFalse(LockscreenIntegrationPolicy.isExternalLyricPayloadSizeAcceptable(
                1_500_001, 0, 0, 0, 128,
                1_500_000, 3_000_000, 16_384));
        assertFalse(LockscreenIntegrationPolicy.isExternalLyricPayloadSizeAcceptable(
                1_000_000, 1_000_000, 1_000_000, 1, 128,
                1_500_000, 3_000_000, 16_384));
        assertFalse(LockscreenIntegrationPolicy.isExternalLyricPayloadSizeAcceptable(
                10, 10, 10, 10, 16_385,
                1_500_000, 3_000_000, 16_384));
    }

    @Test
    public void generationScopedPromotionRequiresCurrentTrackAndActivePlayer() {
        assertTrue(LockscreenIntegrationPolicy.shouldAllowGenerationScopedExternalLyricPromotion(
                true, 5554232L, true, true));
        assertFalse(LockscreenIntegrationPolicy.shouldAllowGenerationScopedExternalLyricPromotion(
                false, 5554232L, true, true));
        assertFalse(LockscreenIntegrationPolicy.shouldAllowGenerationScopedExternalLyricPromotion(
                true, 0L, true, true));
        assertFalse(LockscreenIntegrationPolicy.shouldAllowGenerationScopedExternalLyricPromotion(
                true, 5554232L, false, true));
        assertFalse(LockscreenIntegrationPolicy.shouldAllowGenerationScopedExternalLyricPromotion(
                true, 5554232L, true, false));
    }

    @Test
    public void delayedExternalLyricCommitRequiresFreshMatchingCurrentContext() {
        assertTrue(LockscreenIntegrationPolicy.shouldReplaySystemUiLyricLoadAfterExternalPromotion(
                true, true, true, true, false, 320L, 15_000L));
        assertFalse(LockscreenIntegrationPolicy.shouldReplaySystemUiLyricLoadAfterExternalPromotion(
                false, true, true, true, false, 320L, 15_000L));
        assertFalse(LockscreenIntegrationPolicy.shouldReplaySystemUiLyricLoadAfterExternalPromotion(
                true, false, true, true, false, 320L, 15_000L));
        assertFalse(LockscreenIntegrationPolicy.shouldReplaySystemUiLyricLoadAfterExternalPromotion(
                true, true, false, true, false, 320L, 15_000L));
        assertFalse(LockscreenIntegrationPolicy.shouldReplaySystemUiLyricLoadAfterExternalPromotion(
                true, true, true, false, false, 320L, 15_000L));
        assertFalse(LockscreenIntegrationPolicy.shouldReplaySystemUiLyricLoadAfterExternalPromotion(
                true, true, true, true, true, 320L, 15_000L));
        assertFalse(LockscreenIntegrationPolicy.shouldReplaySystemUiLyricLoadAfterExternalPromotion(
                true, true, true, true, false, 15_001L, 15_000L));
    }

    @Test
    public void applePromotionCanUseFreshMatchingSystemUiMetadataBeforeSeedlingCatchesUp() {
        assertTrue(LockscreenIntegrationPolicy.shouldUseRecentSystemUiTrackContext(
                true, true, true, 295L, 15_000L));
        assertFalse(LockscreenIntegrationPolicy.shouldUseRecentSystemUiTrackContext(
                true, true, false, 295L, 15_000L));
        assertFalse(LockscreenIntegrationPolicy.shouldUseRecentSystemUiTrackContext(
                true, true, true, 15_001L, 15_000L));
    }

    @Test
    public void staleApplePlaybackStateYieldsToFreshSystemUiTrackMetadata() {
        assertTrue(LockscreenIntegrationPolicy
                .shouldIgnoreExternalPlaybackStateForRecentSystemUiTrack(
                        true, true, false, true, 17L, 3_000L));
        assertFalse(LockscreenIntegrationPolicy
                .shouldIgnoreExternalPlaybackStateForRecentSystemUiTrack(
                        true, true, true, true, 17L, 3_000L));
        assertFalse(LockscreenIntegrationPolicy
                .shouldIgnoreExternalPlaybackStateForRecentSystemUiTrack(
                        false, true, false, true, 17L, 3_000L));
        assertFalse(LockscreenIntegrationPolicy
                .shouldIgnoreExternalPlaybackStateForRecentSystemUiTrack(
                        true, true, false, false, 17L, 3_000L));
    }

    @Test
    public void thirdWrappedLineSlidesIntoTwoLineWindow() {
        assertEquals(0, LockscreenIntegrationPolicy.clampSlidingWindowStart(0, 3, 2));
        assertEquals(1, LockscreenIntegrationPolicy.clampSlidingWindowStart(1, 3, 2));
        assertEquals(1, LockscreenIntegrationPolicy.clampSlidingWindowStart(2, 3, 2));
    }

    @Test
    public void passiveLinePanHoldsThenSmoothlyMovesAcrossTheMiddleOfTheLine() {
        assertEquals(0f, LockscreenIntegrationPolicy.passiveLinePanProgress(0f), 0.0001f);
        assertEquals(0f, LockscreenIntegrationPolicy.passiveLinePanProgress(0.215f), 0.0001f);
        assertEquals(0.5f, LockscreenIntegrationPolicy.passiveLinePanProgress(0.5f), 0.0001f);
        assertEquals(1f, LockscreenIntegrationPolicy.passiveLinePanProgress(0.785f), 0.0001f);
        assertEquals(1f, LockscreenIntegrationPolicy.passiveLinePanProgress(1f), 0.0001f);
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
    public void finalPseudoWordLineUsesPreviousCadenceInsteadOfSixHundredMilliseconds() {
        assertEquals(4_200L,
                LockscreenIntegrationPolicy.estimateFinalLineTimedDurationMillis(
                        4_200L,
                        "Final line"));
    }

    @Test
    public void finalPseudoWordLineUsesReadableTextFallbackWithoutPreviousLine() {
        long duration = LockscreenIntegrationPolicy.estimateFinalLineTimedDurationMillis(
                -1L,
                "这是最后一句歌词");
        assertTrue(duration >= 3_000L);
        assertTrue(duration <= 8_000L);
    }

    @Test
    public void finalPseudoWordLineRejectsImplausiblyShortPreviousCadence() {
        assertTrue(LockscreenIntegrationPolicy.estimateFinalLineTimedDurationMillis(
                600L,
                "Last line") >= 2_800L);
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
    public void ordinaryLatinPrefixIsNotTreatedAsSameLyricVariant() {
        assertFalse(LockscreenIntegrationPolicy.sameLyricVariant(
                "He did",
                "He did it"));
        assertFalse(LockscreenIntegrationPolicy.sameLyricVariant(
                "I think he did it",
                "I think he did it but I just can't prove it"));
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
    public void japaneseLyricContainingDrumCharacterIsNotProductionDetail() {
        assertEquals(false, LockscreenIntegrationPolicy.isProductionDetailLine(
                "我が太陽系の鼓動に合わせて",
                7_230L));
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
    public void largeDominantInlineTimingGapIsSuspicious() {
        assertTrue(LockscreenIntegrationPolicy.hasSuspiciousInlineTimingGap(
                3,
                9_400L,
                25_060L,
                15_300L));
    }

    @Test
    public void evenlySpacedLongInlineTimingIsNotSuspicious() {
        assertFalse(LockscreenIntegrationPolicy.hasSuspiciousInlineTimingGap(
                8,
                0L,
                28_000L,
                4_000L));
    }

    @Test
    public void progressiveInlinePrefixIsKeptBeforeLatinWord() {
        assertTrue(LockscreenIntegrationPolicy.isLikelyInlineTimedMainLyricPrefix(
                3,
                0,
                2_066L,
                4_480L));
    }

    @Test
    public void singlePlainTranslationPrefixIsNotInlineTimedMainLyric() {
        assertFalse(LockscreenIntegrationPolicy.isLikelyInlineTimedMainLyricPrefix(
                1,
                0,
                2_066L,
                2_066L));
    }

    @Test
    public void compactInlinePrefixStillStaysOnSameLine() {
        assertTrue(LockscreenIntegrationPolicy.isLikelyInlineTimedMainLyricPrefix(
                3,
                3,
                6_100L,
                6_100L));
    }

    @Test
    public void translationNeverReplacesHiddenMainLyricLine() {
        assertFalse(LockscreenIntegrationPolicy.shouldUseTranslationReplacementTransition(
                true,
                3,
                2,
                0.5f));
    }

    @Test
    public void sparseInlineTimingFallsBackToLineTimedLrc() {
        assertTrue(LockscreenIntegrationPolicy.shouldFallbackToLineTimedLrcForSparseInlineTiming(
                63,
                2));
    }

    @Test
    public void denseInlineTimingKeepsWordTimedParser() {
        assertFalse(LockscreenIntegrationPolicy.shouldFallbackToLineTimedLrcForSparseInlineTiming(
                45,
                45));
    }

    @Test
    public void shortInlineSamplesDoNotForceFallback() {
        assertFalse(LockscreenIntegrationPolicy.shouldFallbackToLineTimedLrcForSparseInlineTiming(
                3,
                1));
    }

    @Test
    public void cjkMainLyricKeepsShortLatinVocalTail() {
        assertTrue(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "\u719f\u6089\u7684\u4fa7\u8138\u90fd\u91cd\u53e0",
                "Oh"));
        assertTrue(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "\u6211\u7684\u660e\u5929\u53eb\u505a\u6628\u5929",
                "Hoh"));
    }

    @Test
    public void cjkMainLyricKeepsUppercaseAcronymTail() {
        assertTrue(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "\u60aa\u970a\u9000\u6563",
                "ICBM"));
    }

    @Test
    public void cjkTranslationStillSplitsBeforeLongEnglishMainLyric() {
        assertFalse(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "\u4f60\u5bf9\u6211\u7ec5\u58eb\u793c\u8c8c",
                "Treat me like a lady"));
    }

    @Test
    public void ordinaryShortEnglishWordsDoNotTriggerTailProtection() {
        assertFalse(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "\u964c\u751f\u7684\u60c5\u8282",
                "okey"));
        assertFalse(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "\u964c\u751f\u7684\u60c5\u8282",
                "OK"));
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

    @Test
    public void downgradedWordTimedChineseLineRemainsPrimaryLyric() {
        assertFalse(LockscreenIntegrationPolicy.shouldTreatAsDelayedInlineTranslation(
                true,
                false,
                9,
                1,
                false));
        assertTrue(LockscreenIntegrationPolicy.shouldTreatAsDelayedInlineTranslation(
                true,
                false,
                1,
                1,
                false));
    }
}
