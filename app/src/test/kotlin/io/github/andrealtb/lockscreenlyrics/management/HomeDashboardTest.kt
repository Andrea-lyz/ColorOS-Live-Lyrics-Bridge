package io.github.andrealtb.lockscreenlyrics.management

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDashboardTest {
    private fun item(id: String, installed: Boolean, enablement: Enablement?) = StoreItem(
        id = id, name = id, applicationId = "io.example.$id", kind = ProviderKind.DEDICATED,
        entry = ProviderEntry.DEBUG_PAGE, installedVersion = if (installed) "1.0" else null,
        installState = if (installed) InstallState.INSTALLED else InstallState.NOT_INSTALLED,
        hostDetected = true, enablement = enablement,
    )

    @Test fun tilesCountInstalledPluginsAndSeparateMissingScopeFromNotEnabled() {
        val items = listOf(
            item("a", true, Enablement.ENABLED),
            item("b", true, Enablement.ENABLED_NO_SCOPE),
            item("c", true, Enablement.NOT_ENABLED),
            item("d", true, Enablement.NO_ENDPOINT), // unknown is not counted as needing action
            item("e", false, null),
        )
        val summary = homeSummary(items, updatesAvailable = 2, setupDone = 4, setupTotal = 6, setupRemaining = 1)
        // b (no scope) needs action; c (not enabled) is only a note (§14.18).
        assertEquals(HomeSummary(4, 1, 1, 1, 2, 4, 6, 1), summary)
    }

    @Test fun heroFollowsTheBridgeAndSystemUiState() {
        fun hero(m: ModuleStatus, s: SystemUiLoad) = HomeStatus(m, s, ConfigObservation.UNKNOWN).hero()
        assertEquals(HeroState.CHECKING, hero(ModuleStatus.CHECKING, SystemUiLoad.CHECKING))
        assertEquals(HeroState.NOT_RUNNING, hero(ModuleStatus.NO_RESPONSE, SystemUiLoad.UNKNOWN))
        assertEquals(HeroState.RESTART_SYSTEMUI, hero(ModuleStatus.RUNNING, SystemUiLoad.OUTDATED_NEEDS_RESTART))
        assertEquals(HeroState.READY, hero(ModuleStatus.RUNNING, SystemUiLoad.CURRENT))
    }
}
