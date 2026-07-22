package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class LyricOpeningCleanupTest {
    @Test
    public void builtInsCleanCreditsButCanBeDisabled() {
        String lrc = "[00:00.000]Example - Artist\n"
                + "[00:00.500]© 2026 Example Publishing\n"
                + "[00:01.000]Produced by: Someone\n"
                + "[00:08.000]First lyric";

        LyricOpeningCleanup.Result defaults = LyricOpeningCleanup.clean(
                lrc,
                "track",
                LyricContentCleanupConfig.defaults());
        assertFalse(defaults.timedText.contains("Example - Artist"));
        assertFalse(defaults.timedText.contains("© 2026"));
        assertFalse(defaults.timedText.contains("Produced by"));
        assertTrue(defaults.timedText.contains("First lyric"));

        LyricContentCleanupConfig disabled = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .copyrightNoticesEnabled(false)
                .productionCreditsEnabled(false)
                .titleArtistLeadEnabled(false)
                .build();
        assertEquals(lrc, LyricOpeningCleanup.clean(lrc, "track", disabled).timedText);
    }

    @Test
    public void parsingProtectedTranslationNoticeCannotBeUnhidden() {
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

        assertFalse(result.timedText.contains("翻译由"));
        assertTrue(result.timedText.contains("First lyric"));
        assertEquals(LyricOpeningCleanup.Reason.FIXED_PARSING,
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
    public void extremeTaylorCreditBlockIsCoveredByDefaultBuiltIns() {
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
                LyricContentCleanupConfig.defaults());

        assertEquals("[00:15.222]I'm doing good, I'm on some new shit", result.timedText);
        assertEquals(16, result.decisions.size());
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
}
