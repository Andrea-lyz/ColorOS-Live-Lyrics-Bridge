package io.github.andrealtb.lockscreenlyrics.management.reminder

import io.github.andrealtb.lockscreenlyrics.management.Availability
import io.github.andrealtb.lockscreenlyrics.management.InstallState
import io.github.andrealtb.lockscreenlyrics.management.ProviderEntry
import io.github.andrealtb.lockscreenlyrics.management.ProviderKind
import io.github.andrealtb.lockscreenlyrics.management.StoreItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNoticeTest {
    private fun item(
        id: String,
        version: String = "1.2",
        state: InstallState = InstallState.UPDATE_AVAILABLE,
        availability: Availability = Availability.AVAILABLE,
    ) = StoreItem(id, id.uppercase(), "pkg.$id", ProviderKind.DEDICATED, ProviderEntry.DEBUG_PAGE, "1.1", state, true,
        catalogVersion = version, availability = availability)

    @Test fun onlyInstallableSameSignerUpdatesAreAnnounced() {
        val items = listOf(
            item("a"),
            item("b", state = InstallState.SIGNATURE_MISMATCH),
            item("c", state = InstallState.LOCAL_NEWER),
            item("d", availability = Availability.CATALOG_EXPIRED),
            item("e", state = InstallState.INSTALLED),
        )
        assertEquals(listOf("a"), announceableUpdates(items).map { it.id })
    }

    @Test fun eachVersionAlertsOnceAndANewerVersionAlertsAgain() {
        val first = planNotice(listOf(item("a")), emptySet())
        assertTrue(first.alert)
        assertEquals(listOf("A"), first.names)
        val again = planNotice(listOf(item("a")), first.announced)
        assertFalse(again.alert)
        assertEquals(listOf("A"), again.names)
        assertTrue(planNotice(listOf(item("a", version = "1.3")), first.announced).alert)
        assertTrue(planNotice(listOf(item("a"), item("b")), first.announced).alert)
    }

    @Test fun installedUpdatesDropOutAndNothingLeftCancels() {
        val announced = planNotice(listOf(item("a"), item("b")), emptySet()).announced
        val afterInstall = planNotice(listOf(item("b")), announced)
        assertFalse(afterInstall.alert)
        assertEquals(setOf("b@1.2"), afterInstall.announced)
        val none = planNotice(emptyList(), announced)
        assertTrue(none.names.isEmpty())
        assertFalse(none.alert)
        assertTrue(none.announced.isEmpty())
    }
}
