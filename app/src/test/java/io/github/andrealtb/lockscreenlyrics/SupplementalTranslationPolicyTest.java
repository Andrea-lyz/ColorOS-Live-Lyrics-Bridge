package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;

public final class SupplementalTranslationPolicyTest {
    @Test
    public void rejectsBlankSpaceNextPrimaryLineAsAyyTranslation() {
        WordLine ayy = line(25_830L, "Ayy");
        WordLine newMoney = line(25_890L, "New money, suit and tie");

        assertTrue(SupplementalTranslationPolicy.matchesNearbyPrimaryLine(
                Arrays.asList(ayy, newMoney),
                ayy,
                "New money, suit and tie",
                120L));
        assertFalse(SupplementalTranslationPolicy.matchesNearbyPrimaryLine(
                Arrays.asList(ayy, newMoney),
                ayy,
                "新贵公子 西装革履",
                120L));
        assertFalse(SupplementalTranslationPolicy.isNearestPrimaryLineForTimestamp(
                Arrays.asList(ayy, newMoney),
                ayy,
                25_890L,
                120L));
        assertTrue(SupplementalTranslationPolicy.isNearestPrimaryLineForTimestamp(
                Arrays.asList(ayy, newMoney),
                newMoney,
                25_890L,
                120L));
    }

    @Test
    public void doesNotRejectSameTextOutsideSupplementalWindow() {
        WordLine current = line(1_000L, "Primary");
        WordLine distant = line(2_000L, "Repeated elsewhere");

        assertFalse(SupplementalTranslationPolicy.matchesNearbyPrimaryLine(
                Arrays.asList(current, distant),
                current,
                "Repeated elsewhere",
                120L));
        assertTrue(SupplementalTranslationPolicy.isNearestPrimaryLineForTimestamp(
                Arrays.asList(current, distant),
                current,
                1_050L,
                120L));
    }

    private static WordLine line(long timeMillis, String text) {
        return new WordLine(timeMillis, text, null);
    }
}
