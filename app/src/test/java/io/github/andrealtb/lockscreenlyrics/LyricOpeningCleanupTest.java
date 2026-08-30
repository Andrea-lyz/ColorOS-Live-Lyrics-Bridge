package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class LyricOpeningCleanupTest {
    @Test
    public void builtInsAreDefaultOffAndCanBeEnabled() {
        String lrc = "[00:00.000]Example - Artist\n"
                + "[00:00.500]© 2026 Example Publishing\n"
                + "[00:01.000]Produced by: Someone\n"
                + "[00:08.000]First lyric";

        LyricOpeningCleanup.Result defaults = LyricOpeningCleanup.clean(
                lrc,
                "track",
                LyricContentCleanupConfig.defaults());
        assertEquals(lrc, defaults.timedText);

        LyricOpeningCleanup.Result enabled = LyricOpeningCleanup.clean(
                lrc,
                "track",
                allBuiltInsEnabled());
        assertFalse(enabled.timedText.contains("Example - Artist"));
        assertFalse(enabled.timedText.contains("© 2026"));
        assertFalse(enabled.timedText.contains("Produced by"));
        assertTrue(enabled.timedText.contains("First lyric"));
    }

    @Test
    public void translationNoticeRemainsVisibleWhenUserCleanupIsDisabled() {
        String lrc = "[00:01.000]以下歌词翻译由 Salt Player 提供\n"
                + "[00:05.000]First lyric";
        LyricContentCleanupConfig config = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .copyrightNoticesEnabled(false)
                .productionCreditsEnabled(false)
                .titleArtistLeadEnabled(false)
                .firstFormalLine(
                        "track",
                        LyricOpeningCleanup.fingerprint("以下歌词翻译由 Salt Player 提供"))
                .build();

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(lrc, "track", config);

        assertEquals(lrc, result.timedText);
        assertTrue(result.timedText.contains("翻译由"));
        assertTrue(result.timedText.contains("First lyric"));
        assertEquals(LyricOpeningCleanup.Reason.VISIBLE,
                result.decisions.get(0).reason);
    }

    @Test
    public void perTrackFirstFormalLineHidesWholeComplexPrefixWithoutRowCount() {
        String lrc = "[00:00.480]Written[00:00.523] by：[00:00.652]Taylor Swift/Aaron Dessner\n"
                + "[00:00.960]© 2020 TASRM Publishing, administered by: Songs Of Universal\n"
                + "[00:01.725]Produced by：Aaron Dessner\n"
                + "[00:03.995]Piano, Acoustic Guitar, Electric Guitar, Drum Programming by：Aaron Dessner\n"
                + "[00:15.222]I'm doing good, I'm on some new shit";
        List<LyricOpeningCleanup.Line> lines = LyricOpeningCleanup.parseLines(lrc);
        LyricContentCleanupConfig config = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .firstFormalLine("the-1", lines.get(4).fingerprint)
                .build();

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(lrc, "the-1", config);

        assertEquals("[00:15.222]I'm doing good, I'm on some new shit", result.timedText);
        for (int index = 0; index < 4; index++) assertTrue(result.decisions.get(index).hidden);
        assertFalse(result.decisions.get(4).hidden);
    }

    @Test
    public void manualFirstFormalLineDropsEveryPrecedingPhysicalRow() {
        String lrc = "Provider preface without a timestamp\n"
                + "[00:00.000]Unusual opening metadata\n"
                + "[00:01.000]Another opening header\n"
                + "[00:02.296]First lyric\n"
                + "[00:04.507]Second lyric";
        List<LyricOpeningCleanup.Line> lines = LyricOpeningCleanup.parseLines(lrc);
        LyricContentCleanupConfig config = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .copyrightNoticesEnabled(false)
                .productionCreditsEnabled(false)
                .titleArtistLeadEnabled(false)
                .firstFormalLine("track", lines.get(2).fingerprint)
                .build();

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(lrc, "track", config);

        assertEquals(
                "[00:02.296]First lyric\n[00:04.507]Second lyric",
                result.timedText);
    }

    @Test
    public void manualFirstFormalLineKeepsSlightlyEarlyCrossLaneVariant() {
        String raw = "[00:00.000]Raw header\n"
                + "[00:02.296]Raw first lyric\n"
                + "[00:04.507]Raw second lyric";
        String display = "[00:00.000]Rendered header\n"
                + "[00:02.295]Localized first lyric\n"
                + "[00:04.507]Localized second lyric";
        List<LyricOpeningCleanup.Line> rawLines = LyricOpeningCleanup.parseLines(raw);
        LyricContentCleanupConfig config = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .copyrightNoticesEnabled(false)
                .productionCreditsEnabled(false)
                .titleArtistLeadEnabled(false)
                .firstFormalLine("track", rawLines.get(1).fingerprint)
                .build();

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(
                display,
                raw,
                "track",
                config);

        assertEquals(
                "[00:02.295]Localized first lyric\n[00:04.507]Localized second lyric",
                result.timedText);
    }

    @Test
    public void extremeTaylorCreditBlockIsCoveredWhenBuiltInsAreEnabled() {
        String lrc = "[00:00.000]the 1 (Explicit) - Taylor Swift\n"
                + "[00:00.100]TME享有本翻译作品的著作权\n"
                + "[00:00.480]Written by：Taylor Swift/Aaron Dessner\n"
                + "[00:00.960]© 2020 TASRM Publishing, administered by：Songs Of Universal\n"
                + "[00:01.453]All Rights Reserved. Used by Permission.\n"
                + "[00:01.725]Produced by：Aaron Dessner\n"
                + "[00:01.907]Recorded by：Jonathan Low and Aaron Dessner\n"
                + "[00:02.497]Vocals recorded by：Laura Sisk\n"
                + "[00:03.087]Mixed by：Jonathan Low\n"
                + "[00:03.541]Mastered by：Randy Merrill\n"
                + "[00:03.995]Piano, Acoustic Guitar, Electric Guitar, Drum Programming, Mellotron, OP1 and Synth Bass by：Aaron Dessner\n"
                + "[00:04.858]Orchestration by：Bryce Dessner\n"
                + "[00:05.130]Synthesizer and OP1 by：Thomas Bartlett\n"
                + "[00:05.857]Percussion by：Jason Treuting\n"
                + "[00:06.311]Viola and Violin by：Yuki Numata Resnick\n"
                + "[00:15.222]I'm doing good, I'm on some new shit";

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(
                lrc,
                "the-1",
                allBuiltInsEnabled());

        assertEquals("[00:15.222]I'm doing good, I'm on some new shit", result.timedText);
        assertEquals(16, result.decisions.size());
    }

    @Test
    public void nearbyProductionCreditsAreRemovedAsPhysicalRows() {
        String lrc = "[00:00.253]Background Vocal: Alyx/Shannon Bae\n"
                + "[00:00.338]Drum: Abraham Olaleye\n"
                + "[00:00.439]Synth: Abraham Olaleye\n"
                + "[00:00.557]Digital Edited by: Cube Studio\n"
                + "[00:00.844]Mixed in Dolby Atmos by: Adam Grover\n"
                + "[00:00.980]Original Publishers: Example Publishing\n"
                + "[00:01.723]Sub-Publishers: Example Publishing Korea\n"
                + "[00:02.483]Background Vocal: Alyx/Shannon Bae\n"
                + "[00:02.533]Drum: Abraham Olaleye\n"
                + "[00:02.668]Synth: Abraham Olaleye\n"
                + "[00:02.804]Digital Edited by: Cube Studio\n"
                + "[00:03.108]Mixed in Dolby Atmos by: Adam Grover\n"
                + "[00:03.243]Original Publishers: Example Publishing\n"
                + "[00:03.986]Sub-Publishers: Example Publishing Korea\n"
                + "[00:04.561]Keep me on my toes";

        LyricOpeningCleanup.Result cleaned = LyricOpeningCleanup.clean(
                lrc,
                "gimme-dat-love",
                allBuiltInsEnabled());

        assertEquals("[00:04.561]Keep me on my toes", cleaned.timedText);
        assertEquals(15, cleaned.decisions.size());
        for (int index = 0; index < 14; index++) {
            assertTrue(cleaned.decisions.get(index).hidden);
        }
        assertFalse(cleaned.decisions.get(14).hidden);

        LyricContentCleanupConfig disabled = allBuiltInsEnabled()
                .buildUpon()
                .productionCreditsEnabled(false)
                .build();
        assertEquals(lrc, LyricOpeningCleanup.clean(
                lrc,
                "gimme-dat-love",
                disabled).timedText);
    }

    @Test
    public void missingFingerprintFailsOpenInsteadOfUsingStoredRowNumber() {
        String lrc = "[00:01.000]Credit line\n[00:05.000]First lyric";
        LyricContentCleanupConfig config = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .copyrightNoticesEnabled(false)
                .productionCreditsEnabled(false)
                .titleArtistLeadEnabled(false)
                .firstFormalLine("track", LyricOpeningCleanup.fingerprint("Old lyric"))
                .build();

        assertEquals(lrc, LyricOpeningCleanup.clean(lrc, "track", config).timedText);
    }

    @Test
    public void learnedPrefixIsNormalizedAndOnlyAffectsOpeningWindow() {
        LyricContentCleanupConfig.LearnedRule rule =
                LyricOpeningCleanup.proposeLearnedRule("Vocals recorded by：Laura Sisk");
        assertNotNull(rule);
        LyricContentCleanupConfig config = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .copyrightNoticesEnabled(false)
                .productionCreditsEnabled(false)
                .titleArtistLeadEnabled(false)
                .addLearnedRule(rule)
                .build();
        String lrc = "[00:02.000]VOCALS   RECORDED BY: Someone\n"
                + "[00:31.000]Vocals recorded by: this is a real later lyric";

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(lrc, "", config);

        assertFalse(result.timedText.contains("Someone"));
        assertTrue(result.timedText.contains("real later lyric"));
    }

    @Test
    public void unusualManualCandidateFallsBackToExactLearnedRule() {
        LyricContentCleanupConfig.LearnedRule rule =
                LyricOpeningCleanup.proposeLearnedRule("(SCORE (13)/Megatone production note)");

        assertNotNull(rule);
        assertEquals(LyricContentCleanupConfig.LearnedType.EXACT, rule.type);
        assertEquals("(score (13)/megatone production note)", rule.value);
    }

    @Test
    public void titleArtistContinuationDoesNotHideOpeningEnglishLyricSentence() {
        String lrc = "[00:00.000]I Really Want to Stay at Your House - Samuel Kim&Lorien\n"
                + "[00:01.799]I couldn't wait for you to come clear the cupboards\n"
                + "[00:09.405]But now you're going to leave with nothing but a sign";

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(
                lrc,
                "i-really-want-to-stay-at-your-house|samuel kim&lorien",
                allBuiltInsEnabled());

        assertFalse(result.timedText.contains("Samuel Kim"));
        assertTrue(result.timedText.contains(
                "I couldn't wait for you to come clear the cupboards"));
        assertTrue(result.timedText.contains(
                "But now you're going to leave with nothing but a sign"));
        assertEquals(LyricOpeningCleanup.Reason.BUILTIN_TITLE_ARTIST,
                result.decisions.get(0).reason);
        assertEquals(LyricOpeningCleanup.Reason.VISIBLE, result.decisions.get(1).reason);
        assertFalse(result.decisions.get(1).hidden);
    }

    @Test
    public void wordTimedOpeningEnglishLyricIsNotTreatedAsTitleArtistCredit() {
        String lrc = "[ti:I Really Want to Stay at Your House]\n"
                + "[ar:Samuel Kim&Lorien]\n"
                + "[00:01.799]<00:01.799>I <00:01.900>couldn't "
                + "<00:02.200>wait <00:02.500>for <00:02.800>you "
                + "<00:03.100>to <00:03.400>come <00:03.700>clear "
                + "<00:04.000>the <00:04.300>cupboards<00:09.405>\n"
                + "[00:09.405]<00:09.405>But <00:09.623>now "
                + "<00:09.855>you're <00:10.116>going <00:10.312>to "
                + "<00:10.538>leave <00:10.816>with <00:11.089>nothing "
                + "<00:11.545>but <00:11.745>a <00:12.072>sign<00:12.673>";

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(
                lrc,
                "i-really-want-to-stay-at-your-house|samuel kim&lorien",
                allBuiltInsEnabled());

        assertEquals("I couldn't wait for you to come clear the cupboards",
                result.decisions.get(0).line.text);
        assertEquals(LyricOpeningCleanup.Reason.VISIBLE, result.decisions.get(0).reason);
        assertFalse(result.decisions.get(0).hidden);
        assertTrue(result.timedText.contains("couldn't"));
    }

    @Test
    public void shortFeaturedArtistRowAfterTitleArtistCreditRemainsHidden() {
        String lrc = "[00:00.000]Sweeter Than Fiction (Taylor's Version) - Taylor Swift\n"
                + "[00:00.800]Aaron Dessner\n"
                + "[00:15.222]I'm doing good, I'm on some new shit";

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(
                lrc,
                "sweeter-than-fiction",
                allBuiltInsEnabled());

        assertFalse(result.timedText.contains("Taylor Swift"));
        assertFalse(result.timedText.contains("Aaron Dessner"));
        assertTrue(result.timedText.contains("I'm doing good, I'm on some new shit"));
        assertEquals(LyricOpeningCleanup.Reason.BUILTIN_TITLE_ARTIST,
                result.decisions.get(1).reason);
        assertTrue(result.decisions.get(1).hidden);
    }

    @Test
    public void titleArtistContinuationDoesNotHideOpeningCjkLyricSentence() {
        String lrc = "[00:00.000]I Really Want to Stay at Your House - Samuel Kim&Lorien\n"
                + "[00:01.799]我已经等不及你来清理残局\n"
                + "[00:09.405]但现在你准备离开 只留下一张字条";

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(
                lrc,
                "i-really-want-to-stay-at-your-house|samuel kim&lorien",
                allBuiltInsEnabled());

        assertTrue(result.timedText.contains("我已经等不及你来清理残局"));
        assertEquals(LyricOpeningCleanup.Reason.VISIBLE, result.decisions.get(1).reason);
        assertFalse(result.decisions.get(1).hidden);
    }

    @Test
    public void yrcLineTagsAndWordTagsArePreservedWhenVisible() {
        String yrc = "[1000,2000](0,400,0)Hel(400,500,0)lo";

        LyricOpeningCleanup.Result result = LyricOpeningCleanup.clean(
                yrc,
                "track",
                LyricContentCleanupConfig.defaults());

        assertEquals(yrc, result.timedText);
        assertEquals("Hello", result.decisions.get(0).line.text);
    }

    @Test
    public void settingsSnapshotIsBoundedToOpeningRows() {
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < 120; index++) {
            source.append(String.format("[%02d:%02d.000]Line %d\n",
                    index / 60,
                    index % 60,
                    index));
        }

        String preview = LyricOpeningCleanup.previewTimedText(source.toString());

        assertEquals(80, LyricOpeningCleanup.parseLines(preview).size());
        assertTrue(preview.length() <= LyricOpeningCleanup.MAX_PREVIEW_CHARS);
        assertFalse(preview.contains("Line 80"));
    }

    private static LyricContentCleanupConfig allBuiltInsEnabled() {
        return LyricContentCleanupConfig.defaults()
                .buildUpon()
                .copyrightNoticesEnabled(true)
                .productionCreditsEnabled(true)
                .titleArtistLeadEnabled(true)
                .build();
    }
}
