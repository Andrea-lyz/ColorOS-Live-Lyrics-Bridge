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
    public void freshPositionUpdateAfterTrackResetIsAuthoritative() {
        assertTrue(LockscreenIntegrationPolicy.isFreshPlaybackPositionAfterTrackReset(
                80_000_700L,
                80_000_000L));
        assertTrue(LockscreenIntegrationPolicy.isFreshPlaybackPositionAfterTrackReset(
                80_000_000L,
                80_000_000L));
        assertFalse(LockscreenIntegrationPolicy.isFreshPlaybackPositionAfterTrackReset(
                79_999_999L,
                80_000_000L));
        assertFalse(LockscreenIntegrationPolicy.isFreshPlaybackPositionAfterTrackReset(
                0L,
                80_000_000L));
    }

    @Test
    public void transientRecyclerMissDoesNotDeactivateAnActiveLyricTransition() {
        assertTrue(LockscreenIntegrationPolicy.shouldRetainLyricModeForTransientSurfaceMiss(
                true, true, true, 2_000L, 1_200L));
        assertTrue(LockscreenIntegrationPolicy.shouldRetainLyricModeForTransientSurfaceMiss(
                true, true, false, 600L, 1_200L));
        assertFalse(LockscreenIntegrationPolicy.shouldRetainLyricModeForTransientSurfaceMiss(
                true, true, false, 1_201L, 1_200L));
        assertFalse(LockscreenIntegrationPolicy.shouldRetainLyricModeForTransientSurfaceMiss(
                true, false, true, 0L, 1_200L));
        assertFalse(LockscreenIntegrationPolicy.shouldRetainLyricModeForTransientSurfaceMiss(
                false, true, true, 0L, 1_200L));
    }

    @Test
    public void systemUiOwnsBrightScreenRecyclerPositioning() {
        assertFalse(LockscreenIntegrationPolicy.shouldModulePositionLyricsRecycler(
                false, true, false));
        assertTrue(LockscreenIntegrationPolicy.shouldModulePositionLyricsRecycler(
                true, true, false));
        assertTrue(LockscreenIntegrationPolicy.shouldModulePositionLyricsRecycler(
                false, false, false));
        assertFalse(LockscreenIntegrationPolicy.shouldModulePositionLyricsRecycler(
                true, false, true));
    }

    @Test
    public void pausedBufferingAndPlayingPositionJumpsAreObserved() {
        assertTrue(LockscreenIntegrationPolicy.isPlaybackPositionJump(
                2, 26_000L, 17_000L, 1_500L));
        assertTrue(LockscreenIntegrationPolicy.isPlaybackPositionJump(
                6, 17_000L, 7_621L, 1_500L));
        assertTrue(LockscreenIntegrationPolicy.isPlaybackPositionJump(
                3, 1_000L, 69_000L, 1_500L));
        assertFalse(LockscreenIntegrationPolicy.isPlaybackPositionJump(
                0, 17_000L, 0L, 1_500L));
        assertFalse(LockscreenIntegrationPolicy.isPlaybackPositionJump(
                2, 17_000L, 16_000L, 1_500L));
    }

    @Test
    public void attachedRecyclerIsSearchedWhenRememberedRowsBelongToHiddenSurface() {
        assertTrue(LockscreenIntegrationPolicy.shouldSearchAttachedRecyclerForLyricCandidates(
                0, 0));
        assertTrue(LockscreenIntegrationPolicy.shouldSearchAttachedRecyclerForLyricCandidates(
                8, 0));
        assertFalse(LockscreenIntegrationPolicy.shouldSearchAttachedRecyclerForLyricCandidates(
                8, 1));
    }

    @Test
    public void officialRecyclerIndexWinsOverPlaybackProgressDuringTransition() {
        assertEquals(32, LockscreenIntegrationPolicy.chooseOfficialLyricVisualIndex(
                32, 34, 34));
        assertEquals(34, LockscreenIntegrationPolicy.chooseOfficialLyricVisualIndex(
                -1, 34, 35));
        assertEquals(35, LockscreenIntegrationPolicy.chooseOfficialLyricVisualIndex(
                -1, -1, 35));
    }

    @Test
    public void wordProgressStaysOffUntilFirstWordEvenIfLineIsFocused() {
        assertFalse(LockscreenIntegrationPolicy.shouldTreatLineAsWordProgressActive(
                true, true));
        assertTrue(LockscreenIntegrationPolicy.shouldTreatLineAsWordProgressActive(
                true, false));
        assertFalse(LockscreenIntegrationPolicy.shouldTreatLineAsWordProgressActive(
                false, false));
    }

    @Test
    public void leftoverOfficialRecyclerIndexIsNotTrustedDuringIntroOrTrackReset() {
        assertFalse(LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                false, 3, 0, true, false));
        assertTrue(LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                false, 0, 0, true, false));
        assertFalse(LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                false, 12, 0, false, true));
        assertTrue(LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                false, 1, 0, false, true));
        assertTrue(LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                false, 12, 0, false, false));
        assertFalse(LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                false, -1, 0, false, false));
    }

    @Test
    public void nativeV5UsesOfficialRecyclerLineEvenWhenBridgeClockIsStale() {
        assertTrue(LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                true, 12, 0, true, true));
        assertFalse(LockscreenIntegrationPolicy.shouldTrustOfficialRecyclerActiveLine(
                true, -1, 0, true, true));
    }

    @Test
    public void nativeClockRestartsAfterNoLyricTrackInvalidatesBinding() {
        assertTrue(LockscreenIntegrationPolicy.shouldResetNativePlaybackClock(
                true, "", "spotify:track:returning"));
        assertTrue(LockscreenIntegrationPolicy.shouldResetNativePlaybackClock(
                true, "spotify:track:previous", "spotify:track:returning"));
        assertFalse(LockscreenIntegrationPolicy.shouldResetNativePlaybackClock(
                true, "spotify:track:returning", "spotify:track:returning"));
        assertFalse(LockscreenIntegrationPolicy.shouldResetNativePlaybackClock(
                false, "", "spotify:track:returning"));
        assertFalse(LockscreenIntegrationPolicy.shouldResetNativePlaybackClock(
                true, "spotify:track:previous", ""));
    }

    @Test
    public void officialInactiveScaleIsClampedAndDisabledScaleIsNeutral() {
        assertEquals(0.75f, LockscreenIntegrationPolicy.officialInactiveRowScale(
                true, 60), 0.0001f);
        assertEquals(0.90f, LockscreenIntegrationPolicy.officialInactiveRowScale(
                true, 90), 0.0001f);
        assertEquals(1f, LockscreenIntegrationPolicy.officialInactiveRowScale(
                true, 120), 0.0001f);
        assertEquals(1f, LockscreenIntegrationPolicy.officialInactiveRowScale(
                false, 75), 0.0001f);
    }

    @Test
    public void brightLyricsWaitForPublishedModelGeometryButAodKeepsItsExistingPath() {
        assertTrue(LockscreenIntegrationPolicy.shouldDeferBrightLyricPixelsForGeometryCommit(
                true, false));
        assertFalse(LockscreenIntegrationPolicy.shouldDeferBrightLyricPixelsForGeometryCommit(
                true, true));
        assertFalse(LockscreenIntegrationPolicy.shouldDeferBrightLyricPixelsForGeometryCommit(
                false, false));
    }

    @Test
    public void committedBrightModelRevealDoesNotTakeOverAodOrDisabledMotion() {
        assertTrue(LockscreenIntegrationPolicy.shouldRevealCommittedBrightLyricModel(
                true, false, LyricUiConfig.MOTION_STANDARD));
        assertTrue(LockscreenIntegrationPolicy.shouldRevealCommittedBrightLyricModel(
                true, false, LyricUiConfig.MOTION_REDUCED));
        assertFalse(LockscreenIntegrationPolicy.shouldRevealCommittedBrightLyricModel(
                true, false, LyricUiConfig.MOTION_OFF));
        assertFalse(LockscreenIntegrationPolicy.shouldRevealCommittedBrightLyricModel(
                true, true, LyricUiConfig.MOTION_STANDARD));
        assertFalse(LockscreenIntegrationPolicy.shouldRevealCommittedBrightLyricModel(
                false, false, LyricUiConfig.MOTION_STANDARD));
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
    public void thirdWrappedLineSlidesIntoTwoLineWindow() {
        assertEquals(0, LockscreenIntegrationPolicy.clampSlidingWindowStart(0, 3, 2));
        assertEquals(1, LockscreenIntegrationPolicy.clampSlidingWindowStart(1, 3, 2));
        assertEquals(1, LockscreenIntegrationPolicy.clampSlidingWindowStart(2, 3, 2));
    }

    @Test
    public void longWrappedLyricsCanReachEveryTwoLineWindow() {
        assertEquals(0, LockscreenIntegrationPolicy.clampSlidingWindowStart(0, 5, 2));
        assertEquals(2, LockscreenIntegrationPolicy.clampSlidingWindowStart(2, 5, 2));
        assertEquals(3, LockscreenIntegrationPolicy.clampSlidingWindowStart(4, 5, 2));
        assertEquals(14, LockscreenIntegrationPolicy.clampSlidingWindowStart(15, 16, 2));
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
                "熟悉的侧脸都重叠",
                "Oh"));
        assertTrue(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "我的明天叫做昨天",
                "Hoh"));
    }

    @Test
    public void cjkMainLyricKeepsUppercaseAcronymTail() {
        assertTrue(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "悪霊退散",
                "ICBM"));
    }

    @Test
    public void cjkTranslationStillSplitsBeforeLongEnglishMainLyric() {
        assertFalse(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "你对我绅士礼貌",
                "Treat me like a lady"));
    }

    @Test
    public void ordinaryShortEnglishWordsDoNotTriggerTailProtection() {
        assertFalse(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "陌生的情节",
                "okey"));
        assertFalse(LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                "陌生的情节",
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
    public void openingEnglishLyricSentenceIsNotATitleArtistCredit() {
        assertEquals(false, LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                "I couldn't wait for you to come clear the cupboards",
                1_799L));
        assertEquals(false, LyricMetadataFilter.isDisplayProductionDetailLine(
                "I couldn't wait for you to come clear the cupboards",
                1_799L));
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

    @Test
    public void isLikelyTitleArtistCreditRejectsAfterFifteenSeconds() {
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                "Hello World - Adele",
                16_000L));
    }

    @Test
    public void isLikelyTitleArtistCreditRejectsOverLongOpening() {
        String longText = "Sweeter Than Fiction (Taylor's Version) From The Vault - "
                + "Taylor Swift Additional Vocals By Ed Sheeran Acoustic Guitarist "
                + "Engineered By Serban Ghenea Mixed By Manny Marroquin";
        assertTrue(longText.length() > 96);
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                longText, 6_000L));
    }

    @Test
    public void isLikelyTitleArtistCreditRejectsSentenceEndingPunctuation() {
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                "Hello World - Adele!",
                3_000L));
    }

    @Test
    public void isLikelyTitleArtistCreditRejectsTextWithoutSeparator() {
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                "Single string with no separator at all",
                3_000L));
    }

    @Test
    public void isLikelyTitleArtistCreditRejectsLettersMissingOnEitherSide() {
        // Both sides must contain at least one letter — pure symbols/punctuation
        // are not enough to be a title-artist credit.
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                "++ - ***",
                3_000L));
    }

    @Test
    public void isLikelyTitleArtistCreditRejectsNullAndEmpty() {
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(null, 0L));
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit("", 0L));
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit("   ", 0L));
        assertFalse(LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                "Hello World - Adele", -1L));
    }

    @Test
    public void isProductionDetailLineRejectsBareLabelWithoutContent() {
        assertFalse(LockscreenIntegrationPolicy.isProductionDetailLine(
                "Producer:", 2_000L));
    }

    @Test
    public void isProductionDetailLineRejectsUnLabeledLineAfterFifteenSeconds() {
        assertFalse(LockscreenIntegrationPolicy.isProductionDetailLine(
                "Producer Joe Bloggs",
                16_000L));
    }

    @Test
    public void isProductionDetailLineRejectsOverLongLabel() {
        String longLabel = "voice directed and edited by something really long";
        assertTrue(longLabel.length() > 40);
        assertFalse(LockscreenIntegrationPolicy.isProductionDetailLine(
                longLabel + ": someone",
                2_000L));
    }

    @Test
    public void isProductionDetailLineRejectsUnrecognisedLabel() {
        assertFalse(LockscreenIntegrationPolicy.isProductionDetailLine(
                "Shouted by: someone",
                2_000L));
    }

    @Test
    public void isProductionDetailLineRejectsNullAndEmpty() {
        assertFalse(LockscreenIntegrationPolicy.isProductionDetailLine(null, 0L));
        assertFalse(LockscreenIntegrationPolicy.isProductionDetailLine("", 0L));
        assertFalse(LockscreenIntegrationPolicy.isProductionDetailLine("   ", 0L));
    }

}
