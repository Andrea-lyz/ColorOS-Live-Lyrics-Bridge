package io.github.andrealtb.lockscreenlyrics.management.status

/**
 * One row of SystemUI's capability status (plan C-02, `CompatibilityStatus.putStatus`): what the
 * decision points for a feature recorded, as plain strings so it can be read on the JVM. These
 * are code-path observations inside SystemUI, never a visual acceptance (§14.17).
 */
internal data class CapabilityRow(
    val feature: String,
    val requested: String,
    /** Outcome of the most recently observed owner (one lyric row, one artwork host, ...). */
    val outcome: String,
    val reason: String,
    val scope: String,
    /** SystemClock.elapsedRealtime() in SystemUI when observed; -1 when never. */
    val observedAt: Long,
    val liveOwners: Int,
    /** Outcome counts over every owner still alive in the last 60 s, e.g. "PREPARED:2,WAITING:1". */
    val summary: String = "",
)

/** The features SystemUI reports, in the order the status page lists them. */
internal enum class CapabilityFeature { CUSTOM_DRAW, COVER_GLOW, CHAR_LIFT, WORD_PROGRESS, LINE_PROGRESS, TRANSLATION_PROGRESS, TRANSLATION_BUTTON, DYNAMIC_ARTWORK }

/** What a feature's records mean to a person reading the status page. */
internal enum class FeatureVerdict {
    /** Turned off in the Bridge's settings. */
    OFF,
    /** Nothing recorded in the last 60 seconds (no lyric screen shown, or the feature never ran). */
    NOT_OBSERVED,
    WAITING,
    /** Line progress on lines that are not line-timed (word-timed lyrics): the feature does not apply. */
    NOT_APPLICABLE,
    /** Fell back to the system's own look. */
    FALLBACK,
    /** Rejected at a decision point. */
    NOT_APPLIED,
    /** The code path ran to a recorded stage somewhere; not a visual check. */
    RAN,
    /** Unrecognized values (a newer or older SystemUI build). */
    UNKNOWN,
}

internal data class FeatureStatus(
    val feature: CapabilityFeature,
    val verdict: FeatureVerdict,
    /** For [FeatureVerdict.RAN]: the furthest stage any owner reached. */
    val stage: String?,
    /** Reason recorded with the latest observation; null when the verdict comes from another owner. */
    val reason: String?,
    val scope: String,
    /** Seconds since the latest observation, on the shared elapsedRealtime clock; null if never. */
    val ageSeconds: Long?,
    /** Live owners that ran / in total, when more than one owner reported. */
    val ranCount: Int = 0,
    val ownerCount: Int = 0,
)

/**
 * Several owners report the same feature (each lyric row, each artwork surface); the latest one
 * alone flips between "ran" and "waiting" (PJZ110, §14.17). The page therefore reads the counts
 * over all live owners and shows the best state reached, with how many owners reached it.
 */
internal fun featureStatuses(rows: List<CapabilityRow>, nowElapsed: Long): List<FeatureStatus> =
    CapabilityFeature.entries.mapNotNull { feature ->
        val row = rows.firstOrNull { it.feature == feature.name } ?: return@mapNotNull null
        val counts = parseSummary(row.summary)
        val best = if (row.outcome == "DISABLED" || counts.isEmpty()) row.outcome
            else PRIORITY.firstOrNull { (counts[it] ?: 0) > 0 } ?: row.outcome
        var verdict = verdictOf(best)
        if (feature == CapabilityFeature.LINE_PROGRESS && verdict == FeatureVerdict.WAITING && row.requested == "YES") {
            // A row that is not line-timed records "waiting / target not observed": not applicable.
            verdict = FeatureVerdict.NOT_APPLICABLE
        }
        val ran = RAN_STAGES.sumOf { counts[it] ?: 0 }
        FeatureStatus(
            feature = feature,
            verdict = verdict,
            stage = if (verdict == FeatureVerdict.RAN) RAN_STAGES.lastOrNull { (counts[it] ?: 0) > 0 } ?: best.takeIf { it in RAN_STAGES } else null,
            reason = row.reason.takeIf { best == row.outcome && verdict != FeatureVerdict.NOT_APPLICABLE },
            scope = row.scope,
            ageSeconds = if (row.observedAt >= 0L && nowElapsed >= row.observedAt) (nowElapsed - row.observedAt) / 1000L else null,
            ranCount = ran,
            ownerCount = counts.values.sum(),
        )
    }

private fun verdictOf(outcome: String): FeatureVerdict = when (outcome) {
    "DISABLED" -> FeatureVerdict.OFF
    "NOT_OBSERVED" -> FeatureVerdict.NOT_OBSERVED
    "WAITING" -> FeatureVerdict.WAITING
    "FALLBACK" -> FeatureVerdict.FALLBACK
    "REJECTED" -> FeatureVerdict.NOT_APPLIED
    in RAN_STAGES -> FeatureVerdict.RAN
    else -> FeatureVerdict.UNKNOWN
}

internal fun parseSummary(summary: String): Map<String, Int> =
    summary.split(',').mapNotNull { part ->
        val (name, count) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        count.trim().toIntOrNull()?.takeIf { it > 0 }?.let { name.trim() to it }
    }.toMap()

/** Stages in pipeline order; the furthest one reached is shown. */
private val RAN_STAGES = listOf("PREPARED", "DISPATCHED", "DRAW_COMMANDS", "PRESENTATION_ASSIGNED")

/** Best first: any owner that ran wins over fallbacks, rejections and waiting. */
private val PRIORITY = RAN_STAGES.reversed() + listOf("FALLBACK", "REJECTED", "WAITING", "NOT_OBSERVED")
