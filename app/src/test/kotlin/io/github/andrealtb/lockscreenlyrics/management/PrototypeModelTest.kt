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
        availability: Availability = Availability.AVAILABLE,
    ) = StoreItem(id, id, "pkg.$id", kind, entry,
        if (state == InstallState.NOT_INSTALLED) null else "1", state, hostDetected, availability = availability)

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

    @Test fun localNewerOnlyOpensThePluginsOwnPage() {
        for (kind in ProviderKind.entries) {
            val it = item(kind, InstallState.LOCAL_NEWER)
            assertEquals(it.openAction(), it.primaryAction())
        }
    }

    @Test fun signatureMismatchStillOffersTheStoreInstallNextToTheOwnPage() {
        // The installer decides: devices that relax signature checks can install over a foreign signer.
        for (kind in ProviderKind.entries) {
            val it = item(kind, InstallState.SIGNATURE_MISMATCH)
            assertEquals(StoreAction.UPDATE, it.primaryAction())
            for (availability in Availability.entries.filter { a -> a != Availability.AVAILABLE }) {
                val unavailable = item(kind, InstallState.SIGNATURE_MISMATCH, availability = availability)
                assertEquals(unavailable.openAction(), unavailable.primaryAction())
            }
        }
    }

    @Test fun downloadAndUpdateNeedAnInstallableCatalogEntry() {
        for (availability in Availability.entries.filter { it != Availability.AVAILABLE }) {
            assertEquals(StoreAction.NONE,
                item(ProviderKind.DEDICATED, InstallState.NOT_INSTALLED, availability = availability).primaryAction())
            assertEquals(StoreAction.OPEN_DEBUG,
                item(ProviderKind.DEDICATED, InstallState.UPDATE_AVAILABLE, availability = availability).primaryAction())
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

    @Test fun readifyOpensItsInfoPageBecauseItHasNoDebugSwitch() {
        val readify = ProviderDirectory.entries.single { it.module == "player-readify" }
        assertEquals(ProviderEntry.INFO_PAGE, readify.entry)
        assertEquals(11, ProviderDirectory.entries.count { it.entry == ProviderEntry.DEBUG_PAGE })
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
