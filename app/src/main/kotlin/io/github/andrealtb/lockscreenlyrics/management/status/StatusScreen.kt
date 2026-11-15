package io.github.andrealtb.lockscreenlyrics.management.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.andrealtb.lockscreenlyrics.R
import io.github.andrealtb.lockscreenlyrics.management.Enablement
import io.github.andrealtb.lockscreenlyrics.management.HomeStatus
import io.github.andrealtb.lockscreenlyrics.management.ModuleStatus
import io.github.andrealtb.lockscreenlyrics.management.BrandButton
import io.github.andrealtb.lockscreenlyrics.management.Spacing
import io.github.andrealtb.lockscreenlyrics.management.StoreItem
import io.github.andrealtb.lockscreenlyrics.management.SystemUiLoad

/** What the status page shows besides the Home summary. */
internal data class StatusDetails(
    val installedBuildId: String?,
    /** Null until SystemUI replied. */
    val loadedBuildId: String?,
    /** Null when SystemUI did not reply or its build predates the capability rows. */
    val features: List<FeatureStatus>?,
)

/**
 * Status & help (plan §8.2, §14.17): the running builds, each feature's last recorded state and
 * reason inside SystemUI, the installed plugins, and the way into short diagnostics. Read-only:
 * nothing here changes a setting.
 */
@Composable
internal fun StatusContent(
    status: HomeStatus,
    details: StatusDetails,
    plugins: List<StoreItem>,
    pluginName: (StoreItem) -> String,
    onRefresh: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenStore: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.widthIn(max = Spacing.maxContentWidth),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        item {
            Column(Modifier.padding(horizontal = Spacing.xs)) {
                Text(stringResource(R.string.mgmt_tab_status), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.status_subtitle), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Section(stringResource(R.string.status_section_build)) {
                Field(stringResource(R.string.status_installed_build), details.installedBuildId ?: stringResource(R.string.mgmt_check_unknown))
                Field(stringResource(R.string.status_loaded_build), when (status.module) {
                    ModuleStatus.CHECKING -> stringResource(R.string.home_value_checking)
                    ModuleStatus.NO_RESPONSE -> stringResource(R.string.home_chip_no_response)
                    ModuleStatus.RUNNING -> details.loadedBuildId ?: stringResource(R.string.mgmt_check_unknown)
                })
                if (status.systemUi == SystemUiLoad.OUTDATED_NEEDS_RESTART) {
                    Note(Icons.Filled.Warning, stringResource(R.string.mgmt_systemui_outdated), warn = true)
                }
                OutlinedButton(onClick = onRefresh, enabled = status.module != ModuleStatus.CHECKING) {
                    Text(stringResource(R.string.mgmt_action_refresh))
                }
            }
        }
        item {
            Section(stringResource(R.string.status_section_features)) {
                Text(stringResource(R.string.status_features_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val features = details.features
                when {
                    status.module == ModuleStatus.CHECKING -> Text(stringResource(R.string.home_value_checking))
                    features == null -> Note(Icons.Filled.Info, stringResource(
                        if (status.module == ModuleStatus.NO_RESPONSE) R.string.status_features_no_reply else R.string.status_features_unsupported), warn = false)
                    else -> features.forEachIndexed { index, feature ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        FeatureRow(feature)
                    }
                }
            }
        }
        item {
            Section(stringResource(R.string.status_section_plugins)) {
                val installed = plugins.filter { it.installedVersion != null }
                if (installed.isEmpty()) {
                    Text(stringResource(R.string.status_plugins_none), style = MaterialTheme.typography.bodyMedium)
                }
                installed.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenStore).padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val (icon, warn) = item.enablement.iconOf()
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                        Text(pluginName(item), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(item.enablement.labelRes()), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Section(stringResource(R.string.status_section_diagnostics)) {
                Text(stringResource(R.string.status_diagnostics_body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                BrandButton(onClick = onOpenDiagnostics) { Text(stringResource(R.string.status_open_diagnostics)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, warn: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
            tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Feature name, verdict in words with an icon, then the reason and how fresh the record is. */
@Composable
private fun FeatureRow(status: FeatureStatus) {
    val (icon, warn) = when (status.verdict) {
        FeatureVerdict.RAN -> Icons.Filled.CheckCircle to false
        FeatureVerdict.FALLBACK, FeatureVerdict.NOT_APPLIED -> Icons.Filled.Warning to true
        else -> Icons.Filled.Info to false
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = when {
                warn -> MaterialTheme.colorScheme.error
                status.verdict == FeatureVerdict.RAN -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(status.feature.labelRes()), style = MaterialTheme.typography.titleSmall)
            Text(
                if (status.verdict == FeatureVerdict.RAN) stringResource(R.string.status_verdict_ran, stringResource(stageRes(status.stage)))
                else stringResource(status.verdict.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (status.ownerCount > 1) {
                Text(stringResource(R.string.status_owner_counts, status.ranCount, status.ownerCount - status.ranCount),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val reason = status.reason?.let(::reasonRes)
            if (reason != null && status.verdict != FeatureVerdict.OFF && status.verdict != FeatureVerdict.NOT_OBSERVED) {
                Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status.ageSeconds?.let {
                Text(stringResource(R.string.status_observed_ago, it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun CapabilityFeature.labelRes(): Int = when (this) {
    CapabilityFeature.CUSTOM_DRAW -> R.string.status_feature_custom_draw
    CapabilityFeature.COVER_GLOW -> R.string.status_feature_cover_glow
    CapabilityFeature.CHAR_LIFT -> R.string.status_feature_char_lift
    CapabilityFeature.WORD_PROGRESS -> R.string.status_feature_word_progress
    CapabilityFeature.LINE_PROGRESS -> R.string.status_feature_line_progress
    CapabilityFeature.TRANSLATION_PROGRESS -> R.string.status_feature_translation_progress
    CapabilityFeature.TRANSLATION_BUTTON -> R.string.status_feature_translation_button
    CapabilityFeature.DYNAMIC_ARTWORK -> R.string.status_feature_dynamic_artwork
}

private fun FeatureVerdict.labelRes(): Int = when (this) {
    FeatureVerdict.OFF -> R.string.status_verdict_off
    FeatureVerdict.NOT_OBSERVED -> R.string.status_verdict_not_observed
    FeatureVerdict.WAITING -> R.string.status_verdict_waiting
    FeatureVerdict.NOT_APPLICABLE -> R.string.status_verdict_line_progress_na
    FeatureVerdict.FALLBACK -> R.string.status_verdict_fallback
    FeatureVerdict.NOT_APPLIED -> R.string.status_verdict_not_applied
    FeatureVerdict.RAN -> R.string.status_verdict_ran
    FeatureVerdict.UNKNOWN -> R.string.mgmt_check_unknown
}

private fun stageRes(stage: String?): Int = when (stage) {
    "PREPARED" -> R.string.status_stage_prepared
    "DISPATCHED" -> R.string.status_stage_dispatched
    "DRAW_COMMANDS" -> R.string.status_stage_draw
    "PRESENTATION_ASSIGNED" -> R.string.status_stage_presented
    else -> R.string.mgmt_check_unknown
}

internal fun reasonRes(reason: String): Int? = when (reason) {
    "CONFIG_DISABLED" -> R.string.status_reason_config_disabled
    "RAW_LYRIC_MISSING" -> R.string.status_reason_raw_lyric_missing
    "LINE_PROGRESS_DISABLED" -> R.string.status_reason_line_progress_disabled
    "AOD_LOW_FRAME_RATE" -> R.string.status_reason_aod_low_frame_rate
    "HOST_NOT_FOUND" -> R.string.status_reason_host_not_found
    "SNAPSHOT_PENDING" -> R.string.status_reason_snapshot_pending
    "PALETTE_UNAVAILABLE" -> R.string.status_reason_palette_unavailable
    "DATASET_UNSUPPORTED" -> R.string.status_reason_dataset_unsupported
    "FRAME_REJECTED" -> R.string.status_reason_frame_rejected
    "NATIVE_FALLBACK" -> R.string.status_reason_native_fallback
    "TARGET_NOT_OBSERVED" -> R.string.status_reason_target_not_observed
    "RUNTIME_EXCEPTION" -> R.string.status_reason_runtime_exception
    else -> null
}

private fun Enablement?.iconOf(): Pair<ImageVector, Boolean> = when (this) {
    Enablement.ENABLED -> Icons.Filled.CheckCircle to false
    Enablement.NOT_ENABLED, Enablement.ENABLED_NO_SCOPE -> Icons.Filled.Warning to true
    else -> Icons.Filled.Info to false
}

private fun Enablement?.labelRes(): Int = when (this) {
    Enablement.ENABLED -> R.string.status_plugin_enabled
    Enablement.ENABLED_NO_SCOPE -> R.string.status_plugin_no_scope
    Enablement.NOT_ENABLED -> R.string.status_plugin_not_enabled
    Enablement.CHECKING, null -> R.string.home_value_checking
    else -> R.string.status_plugin_unknown
}
