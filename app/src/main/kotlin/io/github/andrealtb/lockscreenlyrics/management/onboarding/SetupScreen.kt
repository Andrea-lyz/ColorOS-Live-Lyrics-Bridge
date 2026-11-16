package io.github.andrealtb.lockscreenlyrics.management.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andrealtb.lockscreenlyrics.R
import io.github.andrealtb.lockscreenlyrics.management.BrandButton
import io.github.andrealtb.lockscreenlyrics.management.Spacing

/** What the setup page asks the Activity to do; the page itself never starts external work. */
internal sealed interface SetupAction {
    data object OpenLsposedForBridge : SetupAction
    data object RefreshStatus : SetupAction
    data object RequestRoot : SetupAction
    data object OpenStore : SetupAction
    data class ChoosePlayers(val players: Set<String>) : SetupAction
    data class Skip(val step: SetupStep) : SetupAction
    data class Unskip(val step: SetupStep) : SetupAction
    data object ConfirmPlayback : SetupAction
}

/**
 * G1–G6 as one checklist (plan §5.1, §8.3): step number, words and an icon for each state, a
 * back arrow, and "skip" only for the optional steps. States come from [SetupView]; the page
 * keeps only the unconfirmed chip selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupScreen(
    view: SetupView,
    playerLabel: (String) -> String,
    providerName: (io.github.andrealtb.lockscreenlyrics.management.DirectoryEntry) -> String,
    onAction: (SetupAction) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.setup_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Spacing.maxContentWidth),
                contentPadding = PaddingValues(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    val total = view.steps.size
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.setup_progress, view.done, total), style = MaterialTheme.typography.labelLarge)
                        LinearProgressIndicator(progress = { view.done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    }
                }
                items(view.steps, key = { it.step.name }) { step ->
                    StepCard(step, number = view.steps.indexOf(step) + 1, view, playerLabel, providerName, onAction)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCard(
    step: StepView,
    number: Int,
    view: SetupView,
    playerLabel: (String) -> String,
    providerName: (io.github.andrealtb.lockscreenlyrics.management.DirectoryEntry) -> String,
    onAction: (SetupAction) -> Unit,
) {
    val stateText = stringResource(step.state.labelRes())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                StepBadge(number, step.state)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(step.step.titleRes()), style = MaterialTheme.typography.titleMedium)
                    Text(stateText, style = MaterialTheme.typography.labelMedium, color = step.state.color())
                }
            }
            Text(stringResource(step.bodyRes()), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step.providers.isNotEmpty()) {
                Text(step.providers.joinToString("、") { providerName(it) }, style = MaterialTheme.typography.bodyMedium)
            }
            if (step.step == SetupStep.PLAYERS && view.players.isNotEmpty()) {
                var picked by rememberSaveable(view.chosenPlayers) { mutableStateOf(view.chosenPlayers.toList()) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    view.players.forEach { pkg ->
                        val on = pkg in picked
                        FilterChip(
                            selected = on,
                            onClick = { picked = if (on) picked - pkg else picked + pkg },
                            // App names alone can be ambiguous ("音乐" is Honor's NetEase build,
                            // two ConePlayer builds share a name), so the package name is shown too.
                            label = {
                                Column(Modifier.padding(vertical = Spacing.xs)) {
                                    Text(playerLabel(pkg))
                                    Text(pkg, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = if (on) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null,
                        )
                    }
                }
                val changed = !view.playersConfirmed || picked.toSet() != view.chosenPlayers
                BrandButton(onClick = { onAction(SetupAction.ChoosePlayers(picked.toSet())) },
                    enabled = picked.isNotEmpty() && changed) {
                    Text(stringResource(R.string.setup_action_choose_players))
                }
            }
            StepActions(step, onAction)
        }
    }
}

@Composable
private fun StepActions(step: StepView, onAction: (SetupAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        when {
            step.state == StepState.SKIPPED ->
                TextButton(onClick = { onAction(SetupAction.Unskip(step.step)) }) { Text(stringResource(R.string.setup_action_unskip)) }
            step.state != StepState.TODO -> Unit
            step.step == SetupStep.BRIDGE -> {
                BrandButton(onClick = { onAction(SetupAction.OpenLsposedForBridge) }) { Text(stringResource(R.string.setup_action_open_lsposed)) }
                OutlinedButton(onClick = { onAction(SetupAction.RefreshStatus) }) { Text(stringResource(R.string.mgmt_action_refresh)) }
            }
            step.step == SetupStep.ROOT ->
                BrandButton(onClick = { onAction(SetupAction.RequestRoot) }) { Text(stringResource(R.string.mgmt_action_request_root)) }
            step.step == SetupStep.INSTALL || step.step == SetupStep.ENABLE ->
                BrandButton(onClick = { onAction(SetupAction.OpenStore) }) { Text(stringResource(R.string.setup_action_open_store)) }
            step.step == SetupStep.PLAYBACK ->
                BrandButton(onClick = { onAction(SetupAction.ConfirmPlayback) }) { Text(stringResource(R.string.setup_action_confirm_playback)) }
        }
        if (step.skippable && step.state == StepState.TODO) {
            TextButton(onClick = { onAction(SetupAction.Skip(step.step)) }) { Text(stringResource(R.string.setup_action_skip)) }
        }
    }
}

/** Number until done, then a check; the state is also spelled out next to it. */
@Composable
private fun StepBadge(number: Int, state: StepState) {
    val done = state == StepState.DONE
    Surface(
        shape = CircleShape,
        color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (done) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            else Text(number.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StepState.color() = when (this) {
    StepState.DONE -> MaterialTheme.colorScheme.secondary
    StepState.TODO -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun StepState.labelRes(): Int = when (this) {
    StepState.DONE -> R.string.setup_state_done
    StepState.TODO -> R.string.setup_state_todo
    StepState.CHECKING -> R.string.setup_state_checking
    StepState.BLOCKED -> R.string.setup_state_blocked
    StepState.SKIPPED -> R.string.setup_state_skipped
    StepState.NOT_APPLICABLE -> R.string.setup_state_not_applicable
}

private fun SetupStep.titleRes(): Int = when (this) {
    SetupStep.BRIDGE -> R.string.setup_step_bridge
    SetupStep.ROOT -> R.string.setup_step_root
    SetupStep.PLAYERS -> R.string.setup_step_players
    SetupStep.INSTALL -> R.string.setup_step_install
    SetupStep.ENABLE -> R.string.setup_step_enable
    SetupStep.PLAYBACK -> R.string.setup_step_playback
}

private fun StepView.bodyRes(): Int = when (reason) {
    StepReason.BRIDGE_NOT_RUNNING -> R.string.setup_reason_bridge_not_running
    StepReason.BRIDGE_RESTART_SYSTEMUI -> R.string.setup_reason_bridge_restart
    StepReason.NO_SUPPORTED_PLAYER -> R.string.setup_reason_no_player
    StepReason.CHOOSE_PLAYERS -> R.string.setup_reason_choose_players
    StepReason.PROVIDERS_MISSING -> R.string.setup_reason_providers_missing
    StepReason.PROVIDERS_NOT_ENABLED -> R.string.setup_reason_providers_not_enabled
    StepReason.PROVIDERS_NO_SCOPE -> R.string.setup_reason_providers_no_scope
    StepReason.PROVIDERS_UNKNOWN -> R.string.setup_reason_providers_unknown
    StepReason.NONE -> when (step) {
        SetupStep.BRIDGE -> R.string.setup_body_bridge
        SetupStep.ROOT -> R.string.setup_body_root
        SetupStep.PLAYERS -> R.string.setup_body_players
        SetupStep.INSTALL -> if (state == StepState.BLOCKED) R.string.setup_body_after_players else R.string.setup_body_install
        SetupStep.ENABLE -> if (state == StepState.BLOCKED) R.string.setup_body_after_install else R.string.setup_body_enable
        SetupStep.PLAYBACK -> R.string.setup_body_playback
    }
}
