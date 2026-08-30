package io.github.andrealtb.lockscreenlyrics.systemui.lyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricsRecyclerPolicyTest {
    @Test
    public void systemUiOwnsBrightScreenRecyclerPositioning() {
        assertFalse(LyricsRecyclerPolicy.shouldModulePosition(false, true, false));
        assertTrue(LyricsRecyclerPolicy.shouldModulePosition(true, true, false));
        assertTrue(LyricsRecyclerPolicy.shouldModulePosition(false, false, false));
    }

    @Test
    public void nativeV5LyricsAlwaysKeepSystemUiRecyclerOwnership() {
        assertFalse(LyricsRecyclerPolicy.shouldModulePosition(true, true, true));
        assertFalse(LyricsRecyclerPolicy.shouldModulePosition(false, false, true));
    }

    @Test
    public void firstAttachPrimeDoesNotRequireAlphaOrSize() {
        assertTrue(LyricsRecyclerPolicy.hasPrimeInputs(true, true, true, true));
        assertFalse(LyricsRecyclerPolicy.hasPrimeInputs(false, true, true, true));
        assertFalse(LyricsRecyclerPolicy.hasPrimeInputs(true, false, true, true));
        assertFalse(LyricsRecyclerPolicy.hasPrimeInputs(true, true, false, true));
        assertFalse(LyricsRecyclerPolicy.hasPrimeInputs(true, true, true, false));
    }

    @Test
    public void setCurrentLyricHookHonorsThreadLocalSuppressOnly() {
        assertTrue(LyricsRecyclerPolicy.shouldSkipSetCurrentLyricHook(true));
        assertFalse(LyricsRecyclerPolicy.shouldSkipSetCurrentLyricHook(false));
    }

    @Test
    public void followUpPrimeRunsOnlyWhenChildrenAreMissing() {
        assertTrue(LyricsRecyclerPolicy.shouldScheduleFollowUpPrime(false));
        assertFalse(LyricsRecyclerPolicy.shouldScheduleFollowUpPrime(true));
    }

    @Test
    public void persistentKnownOfficialLagActivatesAdaptiveFollowWithoutPlayerIdentity() {
        assertTrue(LyricsRecyclerPolicy.shouldActivateAdaptiveLineTimedFollow(
                true, true, true, 3, 1, 700L, 600L));
        assertFalse(LyricsRecyclerPolicy.shouldActivateAdaptiveLineTimedFollow(
                true, true, true, 3, -1, 700L, 600L));
        assertFalse(LyricsRecyclerPolicy.shouldActivateAdaptiveLineTimedFollow(
                true, true, true, 2, 1, 700L, 600L));
        assertFalse(LyricsRecyclerPolicy.shouldActivateAdaptiveLineTimedFollow(
                true, true, true, 3, 1, 599L, 600L));
        assertFalse(LyricsRecyclerPolicy.shouldActivateAdaptiveLineTimedFollow(
                true, false, true, 3, 1, 700L, 600L));
        assertFalse(LyricsRecyclerPolicy.shouldActivateAdaptiveLineTimedFollow(
                false, true, true, 3, 1, 700L, 600L));
        assertFalse(LyricsRecyclerPolicy.shouldActivateAdaptiveLineTimedFollow(
                true, true, false, 3, 1, 700L, 600L));
    }

    @Test
    public void adaptiveFollowRunsOncePerNewActiveRow() {
        assertTrue(LyricsRecyclerPolicy.shouldFollowAdaptiveLineTimedRow(
                true, true, 2, 1));
        assertFalse(LyricsRecyclerPolicy.shouldFollowAdaptiveLineTimedRow(
                true, true, 2, 2));
        assertFalse(LyricsRecyclerPolicy.shouldFollowAdaptiveLineTimedRow(
                false, true, 2, 1));
        assertFalse(LyricsRecyclerPolicy.shouldFollowAdaptiveLineTimedRow(
                true, false, 2, 1));
    }

}
