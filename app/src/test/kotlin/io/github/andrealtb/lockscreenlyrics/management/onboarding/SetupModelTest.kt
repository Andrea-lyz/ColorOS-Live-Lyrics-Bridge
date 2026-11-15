package io.github.andrealtb.lockscreenlyrics.management.onboarding

import io.github.andrealtb.lockscreenlyrics.management.Enablement
import io.github.andrealtb.lockscreenlyrics.management.ModuleStatus
import io.github.andrealtb.lockscreenlyrics.management.ProviderDirectory
import io.github.andrealtb.lockscreenlyrics.management.SystemUiLoad
import io.github.andrealtb.lockscreenlyrics.management.root.RootState
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupModelTest {
    private val salt = "io.github.andrealtb.coloroslyrics.provider.salt"
    private val lx = "io.github.andrealtb.coloroslyrics.provider.lx"
    private val saltPlayer = "com.salt.music"
    private val lxPlayer = "cn.toside.music.mobile"

    private fun facts(
        installed: Set<String> = emptySet(),
        enablement: Map<String, Enablement> = emptyMap(),
        module: ModuleStatus = ModuleStatus.RUNNING,
        systemUi: SystemUiLoad = SystemUiLoad.CURRENT,
        root: RootState = RootState.NOT_REQUESTED,
    ) = SetupFacts(module, systemUi, root, installed, enablement)

    private fun SetupView.state(step: SetupStep) = steps.single { it.step == step }

    @Test fun freshPhoneWithAPlayerAsksForEachStepInOrder() {
        val view = setupView(ProviderDirectory.entries, facts(installed = setOf(saltPlayer)), SetupChoices())
        assertEquals(StepState.DONE, view.state(SetupStep.BRIDGE).state)
        assertEquals(StepState.TODO, view.state(SetupStep.ROOT).state)
        assertEquals(StepReason.CHOOSE_PLAYERS, view.state(SetupStep.PLAYERS).reason)
        assertEquals(StepState.BLOCKED, view.state(SetupStep.INSTALL).state)
        assertEquals(StepState.BLOCKED, view.state(SetupStep.ENABLE).state)
        assertEquals(StepState.TODO, view.state(SetupStep.PLAYBACK).state)
        assertEquals(listOf(saltPlayer), view.players)
        assertEquals(3, view.remaining)
    }

    @Test fun choosingAPlayerAsksForItsProviderThenItsEnableState() {
        val chose = SetupChoices(players = setOf(saltPlayer))
        val missing = setupView(ProviderDirectory.entries, facts(installed = setOf(saltPlayer)), chose)
        assertEquals(StepReason.PROVIDERS_MISSING, missing.state(SetupStep.INSTALL).reason)
        assertEquals(listOf(salt), missing.state(SetupStep.INSTALL).providers.map { it.applicationId })

        val installed = setOf(saltPlayer, salt)
        fun enable(e: Enablement?) = setupView(ProviderDirectory.entries,
            facts(installed = installed, enablement = e?.let { mapOf(salt to it) }.orEmpty()), chose).state(SetupStep.ENABLE)
        assertEquals(StepReason.PROVIDERS_NOT_ENABLED, enable(Enablement.NOT_ENABLED).reason)
        assertEquals(StepReason.PROVIDERS_NO_SCOPE, enable(Enablement.ENABLED_NO_SCOPE).reason)
        assertEquals(StepState.CHECKING, enable(null).state)
        assertEquals(StepReason.PROVIDERS_UNKNOWN, enable(Enablement.NO_ENDPOINT).reason)
        assertEquals(StepState.DONE, enable(Enablement.ENABLED).state)
    }

    @Test fun anUpgradedUserWithProvidersIsNotSentBackToStepOne() {
        // 4.x user: LX Provider and its player installed and enabled; no setup choices stored.
        val view = setupView(ProviderDirectory.entries,
            facts(installed = setOf(lxPlayer, lx, saltPlayer), enablement = mapOf(lx to Enablement.ENABLED)), SetupChoices())
        assertEquals(setOf(lxPlayer), view.chosenPlayers)
        assertEquals(StepState.DONE, view.state(SetupStep.PLAYERS).state)
        assertEquals(StepState.DONE, view.state(SetupStep.INSTALL).state)
        assertEquals(StepState.DONE, view.state(SetupStep.ENABLE).state)
    }

    @Test fun bridgeRootAndOptionalSteps() {
        val choices = SetupChoices(skipped = setOf(SetupStep.ROOT, SetupStep.PLAYBACK))
        val skipped = setupView(ProviderDirectory.entries, facts(module = ModuleStatus.NO_RESPONSE), choices)
        assertEquals(StepReason.BRIDGE_NOT_RUNNING, skipped.state(SetupStep.BRIDGE).reason)
        assertEquals(StepState.SKIPPED, skipped.state(SetupStep.ROOT).state)
        assertEquals(StepState.SKIPPED, skipped.state(SetupStep.PLAYBACK).state)
        // No supported player: the player-dependent steps do not apply.
        assertEquals(StepState.NOT_APPLICABLE, skipped.state(SetupStep.PLAYERS).state)
        assertEquals(StepState.NOT_APPLICABLE, skipped.state(SetupStep.INSTALL).state)
        assertEquals(1, skipped.remaining)

        val restart = setupView(ProviderDirectory.entries, facts(systemUi = SystemUiLoad.OUTDATED_NEEDS_RESTART), SetupChoices())
        assertEquals(StepReason.BRIDGE_RESTART_SYSTEMUI, restart.state(SetupStep.BRIDGE).reason)
        // Root granted wins over an older skip; a playback confirmation completes G6.
        val granted = setupView(ProviderDirectory.entries, facts(root = RootState.GRANTED),
            choices.copy(playbackConfirmedAtMillis = 1L))
        assertEquals(StepState.DONE, granted.state(SetupStep.ROOT).state)
        assertEquals(StepState.DONE, granted.state(SetupStep.PLAYBACK).state)
    }

    @Test fun choicesOutsideThisPhoneAreIgnored() {
        // A stored choice for a player that is no longer installed does not ask for its Provider.
        val view = setupView(ProviderDirectory.entries, facts(installed = setOf(saltPlayer)),
            SetupChoices(players = setOf(lxPlayer)))
        assertEquals(emptySet<String>(), view.chosenPlayers)
        assertEquals(StepReason.CHOOSE_PLAYERS, view.state(SetupStep.PLAYERS).reason)
    }

    @Test fun aStepBeingReadIsNotCountedAsRemaining() {
        // A re-check puts the Bridge step into CHECKING; Home must not flash "1 step left".
        val view = setupView(ProviderDirectory.entries,
            facts(module = ModuleStatus.CHECKING, root = RootState.GRANTED),
            SetupChoices(skipped = setOf(SetupStep.PLAYBACK)))
        assertEquals(StepState.CHECKING, view.state(SetupStep.BRIDGE).state)
        assertEquals(0, view.remaining)
        assertEquals(false, view.playersConfirmed)
        assertEquals(true, setupView(ProviderDirectory.entries, facts(), SetupChoices(players = emptySet())).playersConfirmed)
    }
}
