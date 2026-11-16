package io.github.andrealtb.lockscreenlyrics.management.status

import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapabilityModelTest {
    private fun row(feature: String, outcome: String, reason: String = "NONE", observedAt: Long = 1_000L) =
        CapabilityRow(feature, "YES", outcome, reason, "LYRIC_ROW", observedAt, 1)

    @Test fun outcomesMapToWhatAPersonReads() {
        val rows = listOf(
            row("CUSTOM_DRAW", "DRAW_COMMANDS"),
            row("COVER_GLOW", "FALLBACK", "PALETTE_UNAVAILABLE"),
            row("CHAR_LIFT", "DISABLED", "CONFIG_DISABLED"),
            row("WORD_PROGRESS", "PREPARED"),
            row("LINE_PROGRESS", "NOT_OBSERVED", "TARGET_NOT_OBSERVED", observedAt = -1L),
            row("TRANSLATION_PROGRESS", "WAITING", "SNAPSHOT_PENDING"),
            row("TRANSLATION_BUTTON", "REJECTED", "HOST_NOT_FOUND"),
            row("DYNAMIC_ARTWORK", "SOMETHING_NEWER"),
        )
        val statuses = featureStatuses(rows, nowElapsed = 31_000L)
        assertEquals(CapabilityFeature.entries.toList(), statuses.map { it.feature })
        assertEquals(
            listOf(FeatureVerdict.RAN, FeatureVerdict.FALLBACK, FeatureVerdict.OFF, FeatureVerdict.RAN, FeatureVerdict.NOT_OBSERVED,
                FeatureVerdict.WAITING, FeatureVerdict.NOT_APPLIED, FeatureVerdict.UNKNOWN),
            statuses.map { it.verdict },
        )
        assertEquals("DRAW_COMMANDS", statuses[0].stage)
        assertNull(statuses[1].stage)
        assertEquals(30L, statuses[0].ageSeconds)
        assertNull(statuses[4].ageSeconds)
    }

    @Test fun missingOrUnknownFeaturesAreLeftOutAndClockSkewHasNoAge() {
        val statuses = featureStatuses(listOf(row("CUSTOM_DRAW", "PREPARED", observedAt = 5_000L), row("FUTURE_FEATURE", "PREPARED")), 1_000L)
        assertEquals(listOf(CapabilityFeature.CUSTOM_DRAW), statuses.map { it.feature })
        assertNull(statuses.single().ageSeconds)
    }

    /** The page lists exactly the features, outcomes and reasons SystemUI can send. */
    @Test fun mirrorsTheSystemUiEnums() {
        assertEquals(CompatibilityStatus.Feature.values().map { it.name }.toSet(), CapabilityFeature.entries.map { it.name }.toSet())
        val known = setOf("DISABLED", "NOT_OBSERVED", "WAITING", "FALLBACK", "REJECTED", "PREPARED", "DISPATCHED",
            "DRAW_COMMANDS", "PRESENTATION_ASSIGNED")
        assertEquals(known, CompatibilityStatus.Outcome.values().map { it.name }.toSet())
        for (reason in CompatibilityStatus.Reason.values()) {
            if (reason != CompatibilityStatus.Reason.NONE) assert(reasonRes(reason.name) != null) { "no text for $reason" }
        }
    }

    @Test fun anyOwnerThatRanWinsOverTheLatestWaitingOne() {
        // Dynamic artwork: the latest record is the player's "waiting", but a card host is bound.
        val artwork = CapabilityRow("DYNAMIC_ARTWORK", "YES", "WAITING", "TARGET_NOT_OBSERVED", "ARTWORK_LIFECYCLE",
            1_000L, 3, "WAITING:2,PREPARED:1")
        val status = featureStatuses(listOf(artwork), 2_000L).single()
        assertEquals(FeatureVerdict.RAN, status.verdict)
        assertEquals("PREPARED", status.stage)
        assertNull(status.reason) // the reason belongs to the waiting owner, not the one that ran
        assertEquals(1, status.ranCount)
        assertEquals(3, status.ownerCount)
    }

    @Test fun lineProgressOnWordTimedLyricsIsNotApplicable() {
        val wordTimed = CapabilityRow("LINE_PROGRESS", "YES", "WAITING", "TARGET_NOT_OBSERVED", "LYRIC_ROW", 1_000L, 4, "WAITING:4")
        assertEquals(FeatureVerdict.NOT_APPLICABLE, featureStatuses(listOf(wordTimed), 2_000L).single().verdict)
        // One line-timed line among them: it ran there.
        val mixed = wordTimed.copy(summary = "PREPARED:1,WAITING:3")
        val status = featureStatuses(listOf(mixed), 2_000L).single()
        assertEquals(FeatureVerdict.RAN, status.verdict)
        assertEquals(1, status.ranCount)
        // Turned off stays off whatever the counts say.
        val off = wordTimed.copy(requested = "NO", outcome = "DISABLED", summary = "")
        assertEquals(FeatureVerdict.OFF, featureStatuses(listOf(off), 2_000L).single().verdict)
    }

    @Test fun summariesParseDefensively() {
        assertEquals(mapOf("PREPARED" to 2, "WAITING" to 1), parseSummary("PREPARED:2,WAITING:1"))
        assertEquals(emptyMap<String, Int>(), parseSummary("none"))
        assertEquals(mapOf("WAITING" to 1), parseSummary("WAITING:1,BROKEN,X:y,Y:0"))
    }
}
