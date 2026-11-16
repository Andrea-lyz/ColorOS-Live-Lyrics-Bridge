package io.github.andrealtb.lockscreenlyrics.management

import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderEnablementTest {
    private val lx = "io.github.andrealtb.coloroslyrics.provider.lx"
    private val universal = "io.github.andrealtb.coloroslyrics.provider.universal"
    private val artwork = "io.github.andrealtb.artwork.am"
    private val lxHost = "cn.toside.music.mobile"

    private fun project(installed: Set<String>, reports: Map<String, EnablementReport>) =
        storeItems(ProviderDirectory.entries, CatalogState.Unavailable, 34, { reports[it] }) {
            if (it in installed) InstalledPackage("1", 1, emptySet()) else null
        }.items.associateBy { it.applicationId }

    @Test fun replyParsing() {
        assertEquals(EnablementReport(Enablement.ENABLED, setOf("a")), parseEnablement(1, true, listOf("a", "a")))
        assertEquals(Enablement.NOT_ENABLED, parseEnablement(1, false, null).state)
        assertEquals(Enablement.UNKNOWN, parseEnablement(0, true, null).state)
        assertEquals(Enablement.UNKNOWN, parseEnablement(1, null, null).state)
    }

    @Test fun enabledWithoutAnInstalledPlayerInScopeIsFlagged() {
        val installed = setOf(lx, lxHost, universal)
        val noScope = project(installed, mapOf(lx to EnablementReport(Enablement.ENABLED, setOf("other"))))
        assertEquals(Enablement.ENABLED_NO_SCOPE, noScope.getValue(lx).enablement)
        val ok = project(installed, mapOf(lx to EnablementReport(Enablement.ENABLED, setOf(lxHost))))
        assertEquals(Enablement.ENABLED, ok.getValue(lx).enablement)
        // No supported player installed: nothing to select yet, so no scope warning.
        val noHost = project(setOf(lx), mapOf(lx to EnablementReport(Enablement.ENABLED)))
        assertEquals(Enablement.ENABLED, noHost.getValue(lx).enablement)
        // The universal Provider chooses players in its own app.
        val uni = project(installed, mapOf(universal to EnablementReport(Enablement.ENABLED)))
        assertEquals(Enablement.ENABLED, uni.getValue(universal).enablement)
    }

    @Test fun onlyInstalledModulesShowAState() {
        val reports = mapOf(
            lx to EnablementReport(Enablement.NOT_ENABLED),
            artwork to EnablementReport(Enablement.ENABLED),
        )
        val items = project(setOf(artwork), reports)
        assertNull(items.getValue(lx).enablement)
        assertNull(items.getValue(artwork).enablement)
        assertEquals(Enablement.NOT_ENABLED, project(setOf(lx), reports).getValue(lx).enablement)
    }
}
