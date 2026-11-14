package io.github.andrealtb.lockscreenlyrics.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrototypeModelTest {
    private fun item(
        kind: ProviderKind,
        state: InstallState,
        hostDetected: Boolean = true,
        entry: ProviderEntry = if (kind == ProviderKind.DEDICATED) ProviderEntry.DEBUG_PAGE else ProviderEntry.LAUNCHER,
        id: String = "${kind.name}-${state.name}-$hostDetected-${entry.name}",
    ) = StoreItem(id, id, "pkg.$id", kind, entry,
        if (state == InstallState.NOT_INSTALLED) null else "1", state, hostDetected)

    @Test fun installedPluginsOpenTheirOwnPage() {
        assertEquals(StoreAction.OPEN_DEBUG, item(ProviderKind.DEDICATED, InstallState.INSTALLED).primaryAction())
        assertEquals(StoreAction.OPEN_INFO,
            item(ProviderKind.DEDICATED, InstallState.INSTALLED, entry = ProviderEntry.INFO_PAGE).primaryAction())
        assertEquals(StoreAction.OPEN_APP, item(ProviderKind.UNIVERSAL, InstallState.INSTALLED).primaryAction())
        assertEquals(StoreAction.OPEN_APP, item(ProviderKind.ARTWORK, InstallState.INSTALLED).primaryAction())
    }

    @Test fun missingPluginsOfferDownloadAndUpdatesKeepTheOpenAction() {
        assertEquals(StoreAction.DOWNLOAD, item(ProviderKind.DEDICATED, InstallState.NOT_INSTALLED).primaryAction())
        val update = item(ProviderKind.DEDICATED, InstallState.UPDATE_AVAILABLE)
        assertEquals(StoreAction.UPDATE, update.primaryAction())
        assertEquals(StoreAction.OPEN_DEBUG, update.openAction())
    }

    @Test fun signatureMismatchOffersNoInstallOrWipeAction() {
        for (kind in ProviderKind.entries) {
            assertEquals(StoreAction.NONE, item(kind, InstallState.SIGNATURE_MISMATCH).primaryAction())
        }
    }

    @Test fun filtersMatchTheirLabels() {
        val missingHost = item(ProviderKind.DEDICATED, InstallState.NOT_INSTALLED, hostDetected = false)
        val update = item(ProviderKind.DEDICATED, InstallState.UPDATE_AVAILABLE)
        val notInstalled = item(ProviderKind.DEDICATED, InstallState.NOT_INSTALLED)
        val universal = item(ProviderKind.UNIVERSAL, InstallState.INSTALLED, hostDetected = false)
        val all = listOf(missingHost, update, notInstalled, universal)
        assertEquals(all, all.filteredBy(StoreFilter.ALL))
        assertEquals(listOf(update, notInstalled, universal), all.filteredBy(StoreFilter.SUITABLE))
        assertEquals(listOf(update, universal), all.filteredBy(StoreFilter.INSTALLED))
        assertEquals(listOf(update), all.filteredBy(StoreFilter.UPDATES))
    }

    @Test fun storeItemsReflectOnlyWhatThePackageManagerReports() {
        val installed = mapOf(
            "io.github.andrealtb.coloroslyrics.provider.salt" to "1.1.2",
            "com.salt.music" to "12.3",
            "io.github.andrealtb.coloroslyrics.provider.universal" to "1.1.3",
        )
        val items = storeItems(ProviderDirectory.entries) { installed[it] }.associateBy { it.id }
        val salt = items.getValue("player-salt")
        assertEquals(InstallState.INSTALLED, salt.installState)
        assertEquals("1.1.2", salt.installedVersion)
        assertTrue(salt.hostDetected)
        val qq = items.getValue("player-qq")
        assertEquals(InstallState.NOT_INSTALLED, qq.installState)
        assertFalse(qq.hostDetected)
        // Independent apps never count as "has a host"; they are suitable by kind instead.
        assertFalse(items.getValue("universal-provider").hostDetected)
        // No catalog: nothing is ever reported as having an update or a foreign signature.
        assertTrue(items.values.none {
            it.installState == InstallState.UPDATE_AVAILABLE || it.installState == InstallState.SIGNATURE_MISMATCH
        })
    }

    @Test fun readifyOpensItsInfoPageBecauseItHasNoDebugSwitch() {
        val readify = ProviderDirectory.entries.single { it.module == "player-readify" }
        assertEquals(ProviderEntry.INFO_PAGE, readify.entry)
        assertEquals(12, ProviderDirectory.entries.count { it.entry == ProviderEntry.DEBUG_PAGE })
    }

    private val installed = "Bridge:app:debug:abc:clean:111:resolver-1"

    @Test fun pendingQueryShowsCheckingNotHealthy() {
        val status = homeStatus(installed, StatusQuery.Pending)
        assertEquals(HomeStatus(ModuleStatus.CHECKING, SystemUiLoad.CHECKING, ConfigObservation.CHECKING), status)
        assertEquals(Check.UNKNOWN, status.module.check())
    }

    @Test fun noReplyIsNoResponseNeverDisabledOrCurrent() {
        val status = homeStatus(installed, StatusQuery.NoReply)
        assertEquals(ModuleStatus.NO_RESPONSE, status.module)
        assertEquals(Check.ATTENTION, status.module.check())
        assertEquals(SystemUiLoad.UNKNOWN, status.systemUi)
        assertEquals(ConfigObservation.UNKNOWN, status.config)
    }

    @Test fun matchingBuildIsCurrentAndDifferentBuildNeedsRestart() {
        val current = homeStatus(installed, StatusQuery.Reply(installed, 42L))
        assertEquals(ModuleStatus.RUNNING, current.module)
        assertEquals(SystemUiLoad.CURRENT, current.systemUi)
        assertEquals(ConfigObservation.APPLIED_SINCE_START, current.config)
        val old = homeStatus(installed, StatusQuery.Reply("Bridge:app:debug:old:dirty:222:resolver-1", 42L))
        assertEquals(SystemUiLoad.OUTDATED_NEEDS_RESTART, old.systemUi)
        assertEquals(Check.ATTENTION, old.systemUi.check())
    }

    @Test fun missingBuildIdsAreUnknownNotAMatch() {
        assertEquals(SystemUiLoad.UNKNOWN, homeStatus(installed, StatusQuery.Reply("unknown", 1L)).systemUi)
        assertEquals(SystemUiLoad.UNKNOWN, homeStatus(installed, StatusQuery.Reply(null, 1L)).systemUi)
        assertEquals(SystemUiLoad.UNKNOWN, homeStatus("unknown", StatusQuery.Reply("unknown", 1L)).systemUi)
    }

    @Test fun noPushSinceStartIsNeutralNotAFailure() {
        val status = homeStatus(installed, StatusQuery.Reply(installed, -1L))
        assertEquals(ConfigObservation.NOT_PUSHED_SINCE_START, status.config)
        assertEquals(Check.UNKNOWN, status.config.check())
    }
}
