package io.github.andrealtb.lockscreenlyrics.management

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.andrealtb.lockscreenlyrics.R
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogSource
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogState
import io.github.andrealtb.lockscreenlyrics.management.catalog.RefreshOutcome
import io.github.andrealtb.lockscreenlyrics.management.install.Operation
import io.github.andrealtb.lockscreenlyrics.management.install.OperationFailure
import io.github.andrealtb.lockscreenlyrics.management.root.RootState

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
    updatesAvailable: Int,
    root: RootView,
    onRequestRoot: () -> Unit,
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
                CheckRow(
                    label = stringResource(R.string.mgmt_check_root),
                    check = when {
                        root.state == RootState.GRANTED && root.cliAvailable != false -> Check.OK
                        root.state == RootState.DENIED || root.state == RootState.UNAVAILABLE ||
                            root.cliAvailable == false -> Check.ATTENTION
                        else -> Check.UNKNOWN
                    },
                    value = stringResource(when {
                        root.state == RootState.GRANTED && root.cliAvailable == false -> R.string.mgmt_root_no_cli
                        root.state == RootState.GRANTED -> R.string.mgmt_root_granted
                        root.state == RootState.CHECKING -> R.string.mgmt_status_checking
                        root.state == RootState.DENIED -> R.string.mgmt_root_denied
                        root.state == RootState.UNAVAILABLE -> R.string.mgmt_root_unavailable
                        else -> R.string.mgmt_root_not_requested
                    }),
                )
                if (root.state == RootState.NOT_REQUESTED || root.state == RootState.DENIED) {
                    Button(onClick = onRequestRoot) { Text(stringResource(R.string.mgmt_action_request_root)) }
                }
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
        if (updatesAvailable > 0) {
            item {
                ActionCard(
                    text = pluralStringResource(R.plurals.mgmt_updates_available, updatesAvailable, updatesAvailable),
                    action = stringResource(R.string.mgmt_action_open_store),
                    primary = false,
                    onAction = onOpenStore,
                )
            }
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
    unrecognizedEntries: Int,
    catalog: CatalogView,
    onRefreshCatalog: () -> Unit,
    operations: Map<String, Operation>,
    reminders: RemindersView,
    onRemindersChange: (Boolean) -> Unit,
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
        item { CatalogCard(catalog, unrecognizedEntries, onRefreshCatalog) }
        item { ReminderCard(reminders, onRemindersChange) }
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
                Text(stringResource(if (filter == StoreFilter.UPDATES) R.string.mgmt_store_no_updates
                    else R.string.mgmt_store_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val busy = operations.values.any { it.active }
        items(visible, key = { it.id }) { item ->
            StoreCard(item = item, operation = operations[item.id], busy = busy, onAction = { onAction(item, it) })
        }
    }
}

private val ProviderIconSize = 40.dp

/**
 * The Provider's launcher icon. Adaptive icons are drawn through the platform drawable (with the
 * device's icon mask); Compose's painterResource only handles plain vectors and bitmaps.
 */
@Composable
private fun ProviderIcon(@DrawableRes iconRes: Int) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { ProviderIconSize.roundToPx() }
    val bitmap: ImageBitmap? = remember(iconRes, px) {
        if (iconRes == 0) null else runCatching {
            AppCompatResources.getDrawable(context, iconRes)?.let { drawable ->
                Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { out ->
                    drawable.setBounds(0, 0, px, px)
                    drawable.draw(Canvas(out))
                }.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(ProviderIconSize))
    } else {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ProviderIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoreCard(item: StoreItem, operation: Operation?, busy: Boolean, onAction: (StoreAction) -> Unit) {
    StatusCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            ProviderIcon(item.iconRes)
            Text(if (item.nameRes != 0) stringResource(item.nameRes) else item.name,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            KindLabel(item.kind)
        }
        val installed = item.installedVersion?.let { stringResource(R.string.mgmt_version_installed, it) }
            ?: stringResource(R.string.mgmt_version_not_installed)
        Text(
            text = item.catalogVersion?.let { stringResource(R.string.mgmt_version_with_latest, installed, it) } ?: installed,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StateNote(item)
        item.enablement?.let { state ->
            EnablementRow(state)
            if (item.offersScopeRequest() && operation?.active != true) {
                Button(onClick = { onAction(StoreAction.REQUEST_SCOPE) }) {
                    Text(stringResource(R.string.mgmt_action_request_scope))
                }
            }
            if (state.offersLsposedShortcut() && operation?.active != true) {
                OutlinedButton(onClick = { onAction(StoreAction.OPEN_LSPOSED) }) {
                    Text(stringResource(if (state == Enablement.ENABLED_NO_SCOPE) R.string.mgmt_action_lsposed_scope
                        else R.string.mgmt_action_lsposed_enable))
                }
            }
        }
        if (operation != null) OperationStatus(item, operation, onAction)
        // While any operation runs, no second download/update starts (one installer at a time).
        if (operation?.active == true) return@StatusCard
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            when (val action = item.primaryAction()) {
                StoreAction.NONE, StoreAction.CANCEL, StoreAction.CONFIRM_INSTALL, StoreAction.ALLOW_INSTALLS,
                StoreAction.OPEN_LSPOSED, StoreAction.REQUEST_SCOPE -> Unit
                StoreAction.DOWNLOAD ->
                    Button(onClick = { onAction(action) }, enabled = !busy) { Text(stringResource(action.labelRes())) }
                StoreAction.UPDATE -> {
                    Button(onClick = { onAction(action) }, enabled = !busy) { Text(stringResource(action.labelRes())) }
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
    val note = item.note() ?: return
    val (icon, text) = when (note) {
        StoreNote.SIGNATURE_MISMATCH -> Icons.Filled.Warning to R.string.mgmt_state_signature
        StoreNote.LOCAL_NEWER -> Icons.Filled.Info to R.string.mgmt_state_local_newer
        StoreNote.NOT_IN_CATALOG -> Icons.Filled.Info to R.string.mgmt_state_not_in_catalog
        StoreNote.NEEDS_NEWER_BRIDGE -> Icons.Filled.Info to R.string.mgmt_state_needs_bridge
        StoreNote.UNSUPPORTED_SDK -> Icons.Filled.Info to R.string.mgmt_state_unsupported_sdk
        StoreNote.HOST_MISSING -> Icons.Filled.Info to R.string.mgmt_host_missing
        StoreNote.NO_DEBUG_SWITCH -> Icons.Filled.Info to R.string.mgmt_state_no_debug_switch
        StoreNote.INDEPENDENT -> Icons.Filled.CheckCircle to R.string.mgmt_state_independent
    }
    val color = if (note.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color)
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** LSPosed enable state of an installed module: a reminder, not proof that it runs. */
@Composable
private fun EnablementRow(state: Enablement) {
    val (icon, text, color) = when (state) {
        Enablement.CHECKING -> Triple(Icons.Filled.Info, R.string.mgmt_enable_checking, MaterialTheme.colorScheme.onSurfaceVariant)
        Enablement.ENABLED -> Triple(Icons.Filled.CheckCircle, R.string.mgmt_enable_on, MaterialTheme.colorScheme.secondary)
        Enablement.ENABLED_NO_SCOPE -> Triple(Icons.Filled.Warning, R.string.mgmt_enable_no_scope, MaterialTheme.colorScheme.error)
        Enablement.NOT_ENABLED -> Triple(Icons.Filled.Warning, R.string.mgmt_enable_off, MaterialTheme.colorScheme.error)
        Enablement.NO_ENDPOINT -> Triple(Icons.Filled.Info, R.string.mgmt_enable_no_endpoint, MaterialTheme.colorScheme.onSurfaceVariant)
        Enablement.UNREACHABLE -> Triple(Icons.Filled.Info, R.string.mgmt_enable_unreachable, MaterialTheme.colorScheme.onSurfaceVariant)
        Enablement.DIFFERENT_SIGNER -> Triple(Icons.Filled.Info, R.string.mgmt_enable_signer, MaterialTheme.colorScheme.onSurfaceVariant)
        Enablement.UNKNOWN -> Triple(Icons.Filled.Info, R.string.mgmt_enable_unknown, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color)
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Opt-in background reminder (plan §6.5); it only notifies and never installs. */
@Composable
private fun ReminderCard(reminders: RemindersView, onChange: (Boolean) -> Unit) {
    StatusCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.mgmt_reminder_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.mgmt_reminder_body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = reminders.enabled, onCheckedChange = onChange)
        }
        if (reminders.enabled && !reminders.notificationsAllowed) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.mgmt_reminder_notifications_off), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Where the store's data comes from and whether it may install; never hidden (plan §6.2). */
@Composable
private fun CatalogCard(catalog: CatalogView, unrecognizedEntries: Int, onRefresh: () -> Unit) {
    val current = catalog.state as? CatalogState.Current
    val headline = catalog.headline()
    StatusCard {
        if (StoreRuntime.testCatalogTrust) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.mgmt_catalog_test_trust), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        CheckRow(
            label = stringResource(R.string.mgmt_catalog_label),
            check = when (headline) {
                CatalogHeadline.FRESH -> Check.OK
                CatalogHeadline.EXPIRED, CatalogHeadline.NONE -> Check.ATTENTION
                CatalogHeadline.LOADING -> Check.UNKNOWN
            },
            value = if (current == null) {
                stringResource(if (headline == CatalogHeadline.LOADING) R.string.mgmt_catalog_loading else R.string.mgmt_catalog_none)
            } else {
                val source = stringResource(
                    when (current.source) {
                        CatalogSource.BUNDLED -> R.string.mgmt_catalog_source_bundled
                        CatalogSource.PERSISTED -> R.string.mgmt_catalog_source_saved
                        CatalogSource.REMOTE -> R.string.mgmt_catalog_source_remote
                    },
                )
                val base = stringResource(R.string.mgmt_catalog_revision, current.catalog.revision, source)
                if (headline == CatalogHeadline.EXPIRED) base + "\n" + stringResource(R.string.mgmt_catalog_expired) else base
            },
        )
        val refresh = if (catalog.refreshing) {
            R.string.mgmt_catalog_refreshing
        } else {
            when (catalog.lastRefresh?.outcome) {
                null -> null
                RefreshOutcome.UPDATED -> R.string.mgmt_catalog_refresh_updated
                RefreshOutcome.UP_TO_DATE -> R.string.mgmt_catalog_refresh_current
                RefreshOutcome.REJECTED -> R.string.mgmt_catalog_refresh_rejected
                RefreshOutcome.PERSIST_FAILED -> R.string.mgmt_catalog_refresh_not_saved
                RefreshOutcome.NETWORK_FAILED -> R.string.mgmt_catalog_refresh_network
            }
        }
        if (refresh != null) {
            val text = if (refresh == R.string.mgmt_catalog_refresh_rejected) {
                stringResource(refresh, catalog.lastRefresh?.rejection?.name?.lowercase() ?: "?")
            } else {
                stringResource(refresh)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (unrecognizedEntries > 0) {
            Text(
                pluralStringResource(R.plurals.mgmt_catalog_unrecognized, unrecognizedEntries, unrecognizedEntries),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onRefresh, enabled = !catalog.refreshing && headline != CatalogHeadline.LOADING) {
            Text(stringResource(R.string.mgmt_catalog_refresh))
        }
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
    StoreAction.CANCEL -> R.string.mgmt_action_cancel
    StoreAction.CONFIRM_INSTALL -> R.string.mgmt_action_confirm_install
    StoreAction.ALLOW_INSTALLS -> R.string.mgmt_action_allow_installs
    StoreAction.OPEN_LSPOSED -> R.string.mgmt_action_lsposed_enable
    StoreAction.REQUEST_SCOPE -> R.string.mgmt_action_request_scope
}

internal fun OperationFailure.messageRes(): Int = when (this) {
    OperationFailure.NETWORK -> R.string.mgmt_fail_network
    OperationFailure.CONTENT_MISMATCH -> R.string.mgmt_fail_content
    OperationFailure.IDENTITY_MISMATCH -> R.string.mgmt_fail_identity
    OperationFailure.STORAGE -> R.string.mgmt_fail_storage
    OperationFailure.CANCELLED -> R.string.mgmt_fail_cancelled
    OperationFailure.CONFLICT -> R.string.mgmt_fail_conflict
    OperationFailure.INCOMPATIBLE -> R.string.mgmt_fail_incompatible
    OperationFailure.BLOCKED -> R.string.mgmt_fail_blocked
    OperationFailure.INVALID -> R.string.mgmt_fail_invalid
    OperationFailure.INTERRUPTED -> R.string.mgmt_fail_interrupted
    OperationFailure.NOT_VERIFIED -> R.string.mgmt_fail_not_verified
    OperationFailure.OTHER -> R.string.mgmt_fail_other
}

/** Progress and outcome of this card's store operation, with its own controls. */
@Composable
private fun OperationStatus(item: StoreItem, operation: Operation, onAction: (StoreAction) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (operation) {
        is Operation.Downloading -> {
            val fraction = if (operation.total > 0) (operation.downloaded.toFloat() / operation.total).coerceIn(0f, 1f) else 0f
            Text(stringResource(R.string.mgmt_op_downloading, (fraction * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium, color = muted)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { onAction(StoreAction.CANCEL) }) { Text(stringResource(R.string.mgmt_action_cancel)) }
        }
        Operation.Verifying, Operation.Installing -> {
            Text(stringResource(if (operation == Operation.Verifying) R.string.mgmt_op_verifying else R.string.mgmt_op_installing),
                style = MaterialTheme.typography.bodyMedium, color = muted)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Operation.WaitingForUser -> {
            Text(stringResource(R.string.mgmt_op_waiting), style = MaterialTheme.typography.bodyMedium, color = muted)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(onClick = { onAction(StoreAction.CONFIRM_INSTALL) }) { Text(stringResource(R.string.mgmt_action_confirm_install)) }
                OutlinedButton(onClick = { onAction(StoreAction.CANCEL) }) { Text(stringResource(R.string.mgmt_action_cancel)) }
            }
        }
        Operation.NeedsInstallPermission -> {
            Text(stringResource(R.string.mgmt_op_permission), style = MaterialTheme.typography.bodyMedium, color = muted)
            Button(onClick = { onAction(StoreAction.ALLOW_INSTALLS) }) { Text(stringResource(R.string.mgmt_action_allow_installs)) }
        }
        is Operation.Succeeded -> {
            Text(stringResource(R.string.mgmt_op_succeeded, operation.versionName),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            if (item.kind == ProviderKind.DEDICATED) {
                Text(stringResource(R.string.mgmt_op_next_steps_dedicated), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
        is Operation.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(stringResource(operation.failure.messageRes()), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error)
        }
    }
}
