package io.github.andrealtb.lockscreenlyrics.management.onboarding

import io.github.andrealtb.lockscreenlyrics.management.DirectoryEntry
import io.github.andrealtb.lockscreenlyrics.management.Enablement
import io.github.andrealtb.lockscreenlyrics.management.ModuleStatus
import io.github.andrealtb.lockscreenlyrics.management.ProviderKind
import io.github.andrealtb.lockscreenlyrics.management.SystemUiLoad
import io.github.andrealtb.lockscreenlyrics.management.root.RootState

/**
 * Setup flow G1–G6 (plan §5.1, §14.16). Every step's state is DERIVED from what the phone reports
 * now; only the user's own choices (which players they use, which optional steps they skipped,
 * that they saw lyrics while playing) are stored. Returning from LSPosed, the installer or a
 * Provider therefore never ticks a step by itself: the next re-read does.
 */
internal enum class SetupStep {
    /** G1+G2: the Bridge is enabled in LSPosed and SystemUI runs the installed build. */
    BRIDGE,
    /** Optional: read-only root, so the store can read LSPosed's own enable/scope state. */
    ROOT,
    /** G3: the players the user actually uses. */
    PLAYERS,
    /** G4: the dedicated Provider for each chosen player is installed. */
    INSTALL,
    /** G5: each of those Providers is enabled in LSPosed with its player in scope. */
    ENABLE,
    /** G6: the user played something and saw lyrics. A user confirmation, not a measurement. */
    PLAYBACK,
}

internal enum class StepState {
    DONE,
    /** Waiting on the user. */
    TODO,
    /** Still being read (status query, root check, enablement). */
    CHECKING,
    /** An earlier step decides this one (no players chosen yet). */
    BLOCKED,
    SKIPPED,
    /** Nothing to do on this phone (e.g. no supported player installed). */
    NOT_APPLICABLE,
}

/** Why a step is not done, for the step's text. */
internal enum class StepReason {
    NONE,
    BRIDGE_NOT_RUNNING, BRIDGE_RESTART_SYSTEMUI,
    NO_SUPPORTED_PLAYER, CHOOSE_PLAYERS,
    PROVIDERS_MISSING,
    PROVIDERS_NOT_ENABLED, PROVIDERS_NO_SCOPE, PROVIDERS_UNKNOWN,
}

internal data class SetupFacts(
    val module: ModuleStatus,
    val systemUi: SystemUiLoad,
    val root: RootState,
    /** Every installed package the store knows (Providers and supported players). */
    val installed: Set<String>,
    val enablement: Map<String, Enablement>,
)

/** What the user chose. Kept apart from LyricUiConfig (plan §5.2). */
internal data class SetupChoices(
    /** Null until the user confirms a choice. */
    val players: Set<String>? = null,
    val skipped: Set<SetupStep> = emptySet(),
    val playbackConfirmedAtMillis: Long? = null,
)

internal data class StepView(
    val step: SetupStep,
    val state: StepState,
    val reason: StepReason = StepReason.NONE,
    /** Directory entries this step is about (missing or not yet enabled Providers). */
    val providers: List<DirectoryEntry> = emptyList(),
) {
    val skippable: Boolean get() = step == SetupStep.ROOT || step == SetupStep.PLAYBACK
}

internal data class SetupView(
    val steps: List<StepView>,
    val players: List<String>,
    val chosenPlayers: Set<String>,
    /** Whether [chosenPlayers] is the user's own confirmed choice, not the 4.x default. */
    val playersConfirmed: Boolean = false,
) {
    /**
     * Steps that need the user. A step still being read is not counted: a re-check would
     * otherwise flash "1 step left" on Home while it runs (PJZ110, plan §14.16).
     */
    val remaining: Int get() = steps.count { it.state == StepState.TODO }
    val done: Int get() = steps.count { it.state == StepState.DONE || it.state == StepState.SKIPPED || it.state == StepState.NOT_APPLICABLE }
}

internal fun setupView(entries: List<DirectoryEntry>, facts: SetupFacts, choices: SetupChoices): SetupView {
    val dedicated = entries.filter { it.kind == ProviderKind.DEDICATED }
    val players = dedicated.flatMap { it.hostPackages }.distinct().filter { it in facts.installed }
    // A 4.x user who already has Providers is not sent back to step one (plan §5.2): until they
    // choose, the players covered by their installed Providers count as chosen.
    val chosen = (choices.players ?: dedicated.filter { it.applicationId in facts.installed }
        .flatMap { it.hostPackages }.toSet()).intersect(players.toSet())
    val needed = dedicated.filter { entry -> entry.hostPackages.any { it in chosen } }

    val bridge = when {
        facts.module == ModuleStatus.CHECKING || facts.systemUi == SystemUiLoad.CHECKING -> StepView(SetupStep.BRIDGE, StepState.CHECKING)
        facts.module == ModuleStatus.NO_RESPONSE -> StepView(SetupStep.BRIDGE, StepState.TODO, StepReason.BRIDGE_NOT_RUNNING)
        facts.systemUi == SystemUiLoad.OUTDATED_NEEDS_RESTART -> StepView(SetupStep.BRIDGE, StepState.TODO, StepReason.BRIDGE_RESTART_SYSTEMUI)
        else -> StepView(SetupStep.BRIDGE, StepState.DONE)
    }
    val root = when {
        facts.root == RootState.GRANTED -> StepView(SetupStep.ROOT, StepState.DONE)
        facts.root == RootState.CHECKING -> StepView(SetupStep.ROOT, StepState.CHECKING)
        SetupStep.ROOT in choices.skipped -> StepView(SetupStep.ROOT, StepState.SKIPPED)
        else -> StepView(SetupStep.ROOT, StepState.TODO)
    }
    val playerStep = when {
        players.isEmpty() -> StepView(SetupStep.PLAYERS, StepState.NOT_APPLICABLE, StepReason.NO_SUPPORTED_PLAYER)
        chosen.isEmpty() -> StepView(SetupStep.PLAYERS, StepState.TODO, StepReason.CHOOSE_PLAYERS)
        else -> StepView(SetupStep.PLAYERS, StepState.DONE)
    }
    val missing = needed.filter { it.applicationId !in facts.installed }
    val install = when {
        chosen.isEmpty() -> StepView(SetupStep.INSTALL, if (players.isEmpty()) StepState.NOT_APPLICABLE else StepState.BLOCKED)
        missing.isNotEmpty() -> StepView(SetupStep.INSTALL, StepState.TODO, StepReason.PROVIDERS_MISSING, missing)
        else -> StepView(SetupStep.INSTALL, StepState.DONE)
    }
    val enable = enableStep(needed.filter { it.applicationId in facts.installed }, facts, chosen.isEmpty(), players.isEmpty())
    val playback = when {
        choices.playbackConfirmedAtMillis != null -> StepView(SetupStep.PLAYBACK, StepState.DONE)
        SetupStep.PLAYBACK in choices.skipped -> StepView(SetupStep.PLAYBACK, StepState.SKIPPED)
        else -> StepView(SetupStep.PLAYBACK, StepState.TODO)
    }
    return SetupView(listOf(bridge, root, playerStep, install, enable, playback), players, chosen, choices.players != null)
}

private fun enableStep(installed: List<DirectoryEntry>, facts: SetupFacts, noChoice: Boolean, noPlayers: Boolean): StepView {
    if (noChoice) return StepView(SetupStep.ENABLE, if (noPlayers) StepState.NOT_APPLICABLE else StepState.BLOCKED)
    if (installed.isEmpty()) return StepView(SetupStep.ENABLE, StepState.BLOCKED)
    val states = installed.associateWith { facts.enablement[it.applicationId] }
    fun those(vararg s: Enablement) = states.filterValues { it in s }.keys.toList()
    val off = those(Enablement.NOT_ENABLED)
    val noScope = those(Enablement.ENABLED_NO_SCOPE)
    val checking = states.filterValues { it == null || it == Enablement.CHECKING }.keys.toList()
    val unknown = installed - states.filterValues { it == Enablement.ENABLED }.keys - off.toSet() - noScope.toSet() - checking.toSet()
    return when {
        off.isNotEmpty() -> StepView(SetupStep.ENABLE, StepState.TODO, StepReason.PROVIDERS_NOT_ENABLED, off)
        noScope.isNotEmpty() -> StepView(SetupStep.ENABLE, StepState.TODO, StepReason.PROVIDERS_NO_SCOPE, noScope)
        checking.isNotEmpty() -> StepView(SetupStep.ENABLE, StepState.CHECKING, providers = checking)
        // Without root and with a Provider too old to report, the state cannot be read: ask the user.
        unknown.isNotEmpty() -> StepView(SetupStep.ENABLE, StepState.TODO, StepReason.PROVIDERS_UNKNOWN, unknown)
        else -> StepView(SetupStep.ENABLE, StepState.DONE)
    }
}
