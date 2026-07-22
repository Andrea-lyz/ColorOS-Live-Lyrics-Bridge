package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricContentCleanupConfigTest {
    @Test
    public void defaultsAndRoundTripPreserveSafeRules() {
        LyricContentCleanupConfig.LearnedRule prefix =
                new LyricContentCleanupConfig.LearnedRule(
                        LyricContentCleanupConfig.LearnedType.PREFIX,
                        "Produced by：");
        LyricContentCleanupConfig source = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .copyrightNoticesEnabled(false)
                .addLearnedRule(prefix)
                .firstFormalLine("track", LyricOpeningCleanup.fingerprint("First lyric"))
                .build();

        LyricContentCleanupConfig decoded = LyricContentCleanupConfig.decode(source.encode());

        assertFalse(decoded.copyrightNoticesEnabled);
        assertTrue(decoded.productionCreditsEnabled);
        assertEquals("produced by:", decoded.learnedRules.get(0).value);
        assertEquals(
                LyricOpeningCleanup.fingerprint("First lyric"),
                decoded.firstFormalLineByTrack.get("track"));
    }

    @Test
    public void unknownSchemaIsRejected() {
        assertNull(LyricContentCleanupConfig.decode("{\"schema\":2}"));
    }

    @Test
    public void duplicateLearnedRulesAreCollapsed() {
        LyricContentCleanupConfig.LearnedRule first =
                new LyricContentCleanupConfig.LearnedRule(
                        LyricContentCleanupConfig.LearnedType.PREFIX,
                        "Mixed by:");
        LyricContentCleanupConfig.LearnedRule same =
                new LyricContentCleanupConfig.LearnedRule(
                        LyricContentCleanupConfig.LearnedType.PREFIX,
                        "mixed   by：");

        LyricContentCleanupConfig config = LyricContentCleanupConfig.defaults()
                .buildUpon()
                .addLearnedRule(first)
                .addLearnedRule(same)
                .build();

        assertEquals(1, config.learnedRules.size());
    }

    @Test
    public void learnedRulesAndTrackOverridesAreNotEntryCapped() {
        LyricContentCleanupConfig.Builder builder = LyricContentCleanupConfig.defaults()
                .buildUpon();
        for (int index = 0; index < 128; index++) {
            builder.addLearnedRule(new LyricContentCleanupConfig.LearnedRule(
                    LyricContentCleanupConfig.LearnedType.PREFIX,
                    "unusual-header-" + index + ":"));
            builder.firstFormalLine(
                    "track-" + index,
                    LyricOpeningCleanup.fingerprint("first lyric " + index));
        }

        LyricContentCleanupConfig source = builder.build();
        LyricContentCleanupConfig decoded = LyricContentCleanupConfig.decode(source.encode());

        assertNotNull(decoded);
        assertEquals(128, source.learnedRules.size());
        assertEquals(128, source.firstFormalLineByTrack.size());
        assertEquals(source.learnedRules, decoded.learnedRules);
        assertEquals(source.firstFormalLineByTrack, decoded.firstFormalLineByTrack);
    }
}
