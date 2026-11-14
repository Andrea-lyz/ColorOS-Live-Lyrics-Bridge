package io.github.andrealtb.lockscreenlyrics.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.andrealtb.lockscreenlyrics.R

/** Marks every prototype page; sample values must never be read as device evidence. */
@Composable
internal fun SampleBanner(modifier: Modifier = Modifier, text: Int = R.string.mgmt_sample_banner) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun HomeContent(
    status: HomeStatus,
    state: HomeState,
    onRefreshStatus: () -> Unit,
    onContinueSetup: () -> Unit,
    onViewReasons: () -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.widthIn(max = Spacing.maxContentWidth),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item { SampleBanner(text = R.string.mgmt_home_banner) }
        item {
            StatusCard {
                CheckRow(
                    label = stringResource(R.string.mgmt_check_module),
                    check = status.module.check(),
                    value = stringResource(when (status.module) {
                        ModuleStatus.CHECKING -> R.string.mgmt_status_checking
                        ModuleStatus.RUNNING -> R.string.mgmt_module_running
                        ModuleStatus.NO_RESPONSE -> R.string.mgmt_module_no_response
                    }),
                )
                CheckRow(
                    label = stringResource(R.string.mgmt_check_systemui),
                    check = status.systemUi.check(),
                    value = stringResource(when (status.systemUi) {
                        SystemUiLoad.CHECKING -> R.string.mgmt_status_checking
                        SystemUiLoad.CURRENT -> R.string.mgmt_systemui_current
                        SystemUiLoad.OUTDATED_NEEDS_RESTART -> R.string.mgmt_systemui_outdated
                        SystemUiLoad.UNKNOWN -> R.string.mgmt_check_unknown
                    }),
                )
                CheckRow(
                    label = stringResource(R.string.mgmt_check_config),
                    check = status.config.check(),
                    value = stringResource(when (status.config) {
                        ConfigObservation.CHECKING -> R.string.mgmt_status_checking
                        ConfigObservation.APPLIED_SINCE_START -> R.string.mgmt_config_applied
                        ConfigObservation.NOT_PUSHED_SINCE_START -> R.string.mgmt_config_not_pushed
                        ConfigObservation.UNKNOWN -> R.string.mgmt_check_unknown
                    }),
                )
                OutlinedButton(
                    onClick = onRefreshStatus,
                    enabled = status.module != ModuleStatus.CHECKING,
                ) { Text(stringResource(R.string.mgmt_action_refresh)) }
            }
        }
        if (state.onboardingRemaining > 0) {
            item {
                ActionCard(
                    text = pluralStringResource(
                        R.plurals.mgmt_onboarding_remaining,
                        state.onboardingRemaining,
                        state.onboardingRemaining,
                    ),
                    action = stringResource(R.string.mgmt_action_continue_setup),
                    primary = true,
                    onAction = onContinueSetup,
                )
            }
        }
        if (state.featuresNeedingAttention > 0) {
            item {
                ActionCard(
                    text = pluralStringResource(
                        R.plurals.mgmt_features_attention,
                        state.featuresNeedingAttention,
                        state.featuresNeedingAttention,
                    ),
                    action = stringResource(R.string.mgmt_action_view_reasons),
                    primary = false,
                    onAction = onViewReasons,
                )
            }
        }
        item {
            ActionCard(
                text = pluralStringResource(
                    R.plurals.mgmt_updates_available,
                    state.updatesAvailable,
                    state.updatesAvailable,
                ),
                action = stringResource(R.string.mgmt_action_open_store),
                primary = false,
                onAction = onOpenStore,
            )
        }
    }
}

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) { content() }
    }
}

/** Status uses both an icon and words, never color alone (plan §8.3). */
@Composable
private fun CheckRow(label: String, check: Check, value: String) {
    val (icon, tint) = when (check) {
        Check.OK -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.secondary
        Check.ATTENTION -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
        Check.UNKNOWN -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionCard(text: String, action: String, primary: Boolean, onAction: () -> Unit) {
    StatusCard {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (primary) {
            Button(onClick = onAction) { Text(action) }
        } else {
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun StoreContent(
    items: List<StoreItem>,
    filter: StoreFilter,
    onFilterChange: (StoreFilter) -> Unit,
    onAction: (StoreItem, StoreAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = items.filteredBy(filter)
    LazyColumn(
        modifier = modifier.widthIn(max = Spacing.maxContentWidth),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item { SampleBanner(text = R.string.mgmt_store_banner) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(StoreFilter.entries.toList()) { option ->
                    FilterChip(
                        selected = option == filter,
                        onClick = { onFilterChange(option) },
                        label = { Text(stringResource(option.labelRes())) },
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(stringResource(if (filter == StoreFilter.UPDATES) R.string.mgmt_store_no_catalog
                    else R.string.mgmt_store_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(visible, key = { it.id }) { item ->
            StoreCard(item = item, onAction = { onAction(item, it) })
        }
    }
}

@Composable
private fun StoreCard(item: StoreItem, onAction: (StoreAction) -> Unit) {
    StatusCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            KindLabel(item.kind)
        }
        Text(
            text = item.installedVersion?.let { stringResource(R.string.mgmt_version_installed, it) }
                ?: stringResource(R.string.mgmt_version_not_installed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StateNote(item)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            when (val action = item.primaryAction()) {
                StoreAction.NONE -> Unit
                // No catalog yet: offered so the state is visible, answered with a clear message.
                StoreAction.DOWNLOAD ->
                    OutlinedButton(onClick = { onAction(action) }) { Text(stringResource(action.labelRes())) }
                StoreAction.UPDATE -> {
                    Button(onClick = { onAction(action) }) { Text(stringResource(action.labelRes())) }
                    OutlinedButton(onClick = { onAction(item.openAction()) }) {
                        Text(stringResource(item.openAction().labelRes()))
                    }
                }
                StoreAction.OPEN_DEBUG, StoreAction.OPEN_APP ->
                    Button(onClick = { onAction(action) }) { Text(stringResource(action.labelRes())) }
                StoreAction.OPEN_INFO ->
                    OutlinedButton(onClick = { onAction(action) }) { Text(stringResource(action.labelRes())) }
            }
        }
    }
}

@Composable
private fun StateNote(item: StoreItem) {
    val note: Pair<ImageVector, String>? = when {
        item.installState == InstallState.SIGNATURE_MISMATCH ->
            Icons.Filled.Warning to stringResource(R.string.mgmt_state_signature)
        item.kind == ProviderKind.DEDICATED && !item.hostDetected ->
            Icons.Filled.Info to stringResource(R.string.mgmt_host_missing)
        item.installState == InstallState.INSTALLED && item.entry == ProviderEntry.INFO_PAGE ->
            Icons.Filled.Info to stringResource(R.string.mgmt_state_no_debug_switch)
        item.installState == InstallState.INSTALLED && item.kind != ProviderKind.DEDICATED ->
            Icons.Filled.CheckCircle to stringResource(R.string.mgmt_state_independent)
        else -> null
    }
    note ?: return
    val warning = item.installState == InstallState.SIGNATURE_MISMATCH
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            note.first,
            contentDescription = null,
            tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(note.second, style = MaterialTheme.typography.bodyMedium,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KindLabel(kind: ProviderKind) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            stringResource(kind.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
    }
}

@Composable
internal fun PlaceholderContent(
    body: String,
    action: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = Spacing.maxContentWidth).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SampleBanner()
        StatusCard {
            Text(body, style = MaterialTheme.typography.bodyLarge)
            if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

internal fun StoreFilter.labelRes(): Int = when (this) {
    StoreFilter.SUITABLE -> R.string.mgmt_filter_suitable
    StoreFilter.ALL -> R.string.mgmt_filter_all
    StoreFilter.INSTALLED -> R.string.mgmt_filter_installed
    StoreFilter.UPDATES -> R.string.mgmt_filter_updates
}

internal fun ProviderKind.labelRes(): Int = when (this) {
    ProviderKind.DEDICATED -> R.string.mgmt_kind_dedicated
    ProviderKind.UNIVERSAL -> R.string.mgmt_kind_universal
    ProviderKind.ARTWORK -> R.string.mgmt_kind_artwork
}

internal fun StoreAction.labelRes(): Int = when (this) {
    StoreAction.DOWNLOAD -> R.string.mgmt_action_download
    StoreAction.UPDATE -> R.string.mgmt_action_update
    StoreAction.OPEN_DEBUG -> R.string.mgmt_action_debug
    StoreAction.OPEN_INFO -> R.string.mgmt_action_module_info
    StoreAction.OPEN_APP, StoreAction.NONE -> R.string.mgmt_action_open
}
