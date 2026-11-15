package io.github.andrealtb.lockscreenlyrics.management

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.andrealtb.lockscreenlyrics.R
import io.github.andrealtb.lockscreenlyrics.management.root.RootState

/** Counts for the Home tiles, derived from the same store items the store tab shows. */
internal data class HomeSummary(
    val installedProviders: Int,
    val enabledProviders: Int,
    /** Installed Providers that need an LSPosed action (not enabled, or no player in scope). */
    val providersNeedingAction: Int,
    val updatesAvailable: Int,
    val setupDone: Int,
    val setupTotal: Int,
    val setupRemaining: Int,
)

internal fun homeSummary(items: List<StoreItem>, updatesAvailable: Int, setupDone: Int, setupTotal: Int, setupRemaining: Int): HomeSummary {
    val installed = items.filter { it.installedVersion != null }
    return HomeSummary(
        installedProviders = installed.size,
        enabledProviders = installed.count { it.enablement == Enablement.ENABLED },
        providersNeedingAction = installed.count { it.enablement == Enablement.NOT_ENABLED || it.enablement == Enablement.ENABLED_NO_SCOPE },
        updatesAvailable = updatesAvailable,
        setupDone = setupDone,
        setupTotal = setupTotal,
        setupRemaining = setupRemaining,
    )
}

/** What the hero card says about the lyric chain on this phone. */
internal enum class HeroState { CHECKING, READY, RESTART_SYSTEMUI, NOT_RUNNING }

internal fun HomeStatus.hero(): HeroState = when {
    module == ModuleStatus.CHECKING -> HeroState.CHECKING
    module == ModuleStatus.NO_RESPONSE -> HeroState.NOT_RUNNING
    systemUi == SystemUiLoad.OUTDATED_NEEDS_RESTART -> HeroState.RESTART_SYSTEMUI
    else -> HeroState.READY
}

/**
 * Card-based Home (plan §8.2, user request 2026-11-15): a status hero with one large re-check
 * action, a 2×2 grid of tiles that each open where their number comes from, and the setup call
 * to action when steps remain. Every value is a live read; nothing here is sample data.
 */
@Composable
internal fun HomeDashboard(
    status: HomeStatus,
    summary: HomeSummary,
    root: RootView,
    onRefreshStatus: () -> Unit,
    onRequestRoot: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenStore: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.widthIn(max = Spacing.maxContentWidth),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        item {
            Column(Modifier.padding(horizontal = Spacing.xs)) {
                Text(stringResource(R.string.mgmt_tab_home), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { HeroCard(status, onRefreshStatus) }
        if (summary.setupRemaining > 0) {
            item { SetupCallout(summary, onOpenSetup) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Tile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.List,
                    label = stringResource(R.string.home_tile_providers),
                    value = summary.installedProviders.toString(),
                    caption = if (summary.providersNeedingAction > 0)
                        pluralStringResource(R.plurals.home_providers_attention, summary.providersNeedingAction, summary.providersNeedingAction)
                    else stringResource(R.string.home_providers_enabled, summary.enabledProviders),
                    attention = summary.providersNeedingAction > 0,
                    onClick = onOpenStore,
                )
                Tile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.ShoppingCart,
                    label = stringResource(R.string.home_tile_updates),
                    value = summary.updatesAvailable.toString(),
                    caption = stringResource(if (summary.updatesAvailable > 0) R.string.home_updates_some else R.string.home_updates_none),
                    attention = false,
                    onClick = onOpenStore,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                val rootMissing = root.state == RootState.NOT_REQUESTED || root.state == RootState.DENIED
                Tile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Lock,
                    label = stringResource(R.string.home_tile_root),
                    value = stringResource(root.shortLabel()),
                    caption = stringResource(if (rootMissing) R.string.home_root_tap else R.string.home_root_readonly),
                    attention = root.state == RootState.GRANTED && root.cliAvailable == false,
                    onClick = if (rootMissing) onRequestRoot else null,
                )
                Tile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.home_tile_setup),
                    value = "${summary.setupDone}/${summary.setupTotal}",
                    caption = if (summary.setupRemaining > 0)
                        pluralStringResource(R.plurals.mgmt_onboarding_remaining, summary.setupRemaining, summary.setupRemaining)
                    else stringResource(R.string.home_setup_all_done),
                    attention = summary.setupRemaining > 0,
                    onClick = onOpenSetup,
                )
            }
        }
        if (root.state == RootState.GRANTED && root.cliAvailable == false) {
            item { NoticeCard(stringResource(R.string.mgmt_root_no_cli)) }
        }
    }
}

@Composable
private fun HeroCard(status: HomeStatus, onRefresh: () -> Unit) {
    val hero = status.hero()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_hero_label), style = MaterialTheme.typography.titleSmall)
                    StatusChip(hero)
                }
                Text(stringResource(hero.headline()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                DetailLine(stringResource(R.string.mgmt_check_systemui), stringResource(status.systemUi.shortLabel()))
                DetailLine(stringResource(R.string.home_detail_config), stringResource(status.config.shortLabel()))
                if (hero == HeroState.NOT_RUNNING) {
                    Text(stringResource(R.string.mgmt_module_no_response), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledIconButton(
                onClick = onRefresh,
                enabled = hero != HeroState.CHECKING,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.mgmt_action_refresh), modifier = Modifier.size(32.dp))
            }
        }
    }
}

/** State in words plus an icon, never color alone (plan §8.3). */
@Composable
private fun StatusChip(hero: HeroState) {
    val (icon, container, content) = when (hero) {
        HeroState.READY -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary)
        HeroState.CHECKING -> Triple(Icons.Filled.Info, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error.copy(alpha = 0.10f), MaterialTheme.colorScheme.error)
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(hero.chip()), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SetupCallout(summary: HomeSummary, onOpenSetup: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(pluralStringResource(R.plurals.mgmt_onboarding_remaining, summary.setupRemaining, summary.setupRemaining),
                style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { summary.setupDone.toFloat() / summary.setupTotal.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
            Button(
                onClick = onOpenSetup,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) { Text(stringResource(R.string.mgmt_action_continue_setup)) }
        }
    }
}

/** A tile: label, one large value and a caption. Tapping opens where the value comes from. */
@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    value: String,
    caption: String,
    attention: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 140.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                if (attention) Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error)
                Text(caption, style = MaterialTheme.typography.bodySmall,
                    color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Height the floating dock covers above the navigation bar (dock + its bottom margin). */
internal val DockReserve = 80.dp

/**
 * Floating navigation dock (user request 2026-11-15): the same four top-level destinations as
 * the navigation bar, on a rounded surface above the content. Wide windows keep the rail.
 */
@Composable
internal fun FloatingDock(
    items: List<Triple<ManagementTab, Int, ImageVector>>,
    selected: ManagementTab,
    onSelect: (ManagementTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = Spacing.xl).widthIn(max = 480.dp).fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(6.dp).height(56.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items.forEach { (tab, label, icon) ->
                val on = tab == selected
                // Each item fills the dock's height so its icon and label sit centered (r3 left
                // them at the top with empty space below).
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    selected = on,
                    onClick = { onSelect(tab) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, contentDescription = null)
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

private fun HeroState.headline(): Int = when (this) {
    HeroState.CHECKING -> R.string.home_hero_checking
    HeroState.READY -> R.string.home_hero_ready
    HeroState.RESTART_SYSTEMUI -> R.string.home_hero_restart
    HeroState.NOT_RUNNING -> R.string.home_hero_not_running
}

private fun HeroState.chip(): Int = when (this) {
    HeroState.CHECKING -> R.string.home_chip_checking
    HeroState.READY -> R.string.home_chip_running
    HeroState.RESTART_SYSTEMUI -> R.string.home_chip_outdated
    HeroState.NOT_RUNNING -> R.string.home_chip_no_response
}

private fun SystemUiLoad.shortLabel(): Int = when (this) {
    SystemUiLoad.CHECKING -> R.string.home_value_checking
    SystemUiLoad.CURRENT -> R.string.home_value_current
    SystemUiLoad.OUTDATED_NEEDS_RESTART -> R.string.home_value_outdated
    SystemUiLoad.UNKNOWN -> R.string.mgmt_check_unknown
}

private fun ConfigObservation.shortLabel(): Int = when (this) {
    ConfigObservation.CHECKING -> R.string.home_value_checking
    ConfigObservation.APPLIED_SINCE_START -> R.string.home_value_applied
    ConfigObservation.NOT_PUSHED_SINCE_START -> R.string.home_value_startup
    ConfigObservation.UNKNOWN -> R.string.mgmt_check_unknown
}

private fun RootView.shortLabel(): Int = when (state) {
    RootState.GRANTED -> R.string.home_root_granted
    RootState.CHECKING -> R.string.home_value_checking
    RootState.DENIED -> R.string.home_root_denied
    RootState.UNAVAILABLE -> R.string.home_root_none
    RootState.NOT_REQUESTED -> R.string.home_root_not_requested
}
