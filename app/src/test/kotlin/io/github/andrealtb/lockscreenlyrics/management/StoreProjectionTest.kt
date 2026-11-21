package io.github.andrealtb.lockscreenlyrics.management

import io.github.andrealtb.lockscreenlyrics.management.catalog.Catalog
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogArtifact
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogEntry
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogFreshness
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogKind
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogSource
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogState
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreProjectionTest {
    private val official = CatalogTrust.RELEASE_CERTIFICATE_SHA256
    private val foreign = "a".repeat(64)
    private val salt = "io.github.andrealtb.coloroslyrics.provider.salt"
    private val universal = "io.github.andrealtb.coloroslyrics.provider.universal"

    private fun entry(
        providerId: String = "player-salt",
        applicationId: String = salt,
        versionCode: Long = 8,
        minSdk: Int = 27,
        minStoreProtocol: Int = 1,
    ) = CatalogEntry(
        providerId, CatalogKind.DEDICATED_LYRICS, applicationId, "1.1.$versionCode", versionCode, minSdk,
        minStoreProtocol, "0".repeat(39) + "1", null,
        CatalogArtifact(listOf("https://github.com/x/y/releases/download/t/a.apk"), 1000, "1".repeat(64), setOf(official)),
    )

    private fun catalog(vararg entries: CatalogEntry, freshness: CatalogFreshness = CatalogFreshness.FRESH) =
        CatalogState.Current(
            Catalog(3, 1, 2, "stable", setOf("github.com"), entries.toList()),
            freshness,
            CatalogSource.BUNDLED,
        )

    private fun project(
        catalog: CatalogState,
        installed: Map<String, InstalledPackage> = emptyMap(),
        sdk: Int = 34,
    ) = storeItems(ProviderDirectory.entries, catalog, sdk) { installed[it] }

    private fun StoreProjection.item(id: String) = items.single { it.id == id }

    private fun installed(versionCode: Long, vararg signers: String = arrayOf(official)) =
        InstalledPackage("v$versionCode", versionCode, signers.toSet())

    @Test fun withoutACatalogInstalledPluginsAreOnlyInstalledAndNothingIsOffered() {
        val p = project(CatalogState.Unavailable, mapOf(salt to installed(1, foreign), "com.salt.music" to installed(1)))
        val s = p.item("player-salt")
        assertEquals(InstallState.INSTALLED, s.installState)
        assertEquals(Availability.NO_CATALOG, s.availability)
        assertTrue(s.hostDetected)
        assertNull(s.catalogVersion)
        assertEquals(StoreAction.NONE, p.item("player-qq").primaryAction())
        assertEquals(0, p.updatesAvailable)
    }

    @Test fun versionCodeDecidesUpdateCurrentAndLocalNewer() {
        val c = catalog(entry(versionCode = 8))
        assertEquals(InstallState.UPDATE_AVAILABLE, project(c, mapOf(salt to installed(7))).item("player-salt").installState)
        assertEquals(InstallState.INSTALLED, project(c, mapOf(salt to installed(8))).item("player-salt").installState)
        assertEquals(InstallState.LOCAL_NEWER, project(c, mapOf(salt to installed(9))).item("player-salt").installState)
        val update = project(c, mapOf(salt to installed(7)))
        assertEquals(1, update.updatesAvailable)
        assertEquals(StoreAction.UPDATE, update.item("player-salt").primaryAction())
        assertEquals("1.1.8", update.item("player-salt").catalogVersion)
    }

    @Test fun foreignOrUnreadableSignersAreNotCountedAsUpdatesButMayStillInstall() {
        val c = catalog(entry(versionCode = 8))
        for (signers in listOf(setOf(foreign), setOf(official, foreign), emptySet())) {
            val projection = project(c, mapOf(salt to InstalledPackage("1", 1, signers)))
            val item = projection.item("player-salt")
            assertEquals(InstallState.SIGNATURE_MISMATCH, item.installState)
            assertEquals(0, projection.updatesAvailable)
            // PackageInstaller decides; devices that relax signature checks can install over it.
            assertEquals(StoreAction.UPDATE, item.primaryAction())
            assertEquals(StoreAction.OPEN_DEBUG, item.openAction())
            assertEquals(StoreNote.SIGNATURE_MISMATCH, item.note())
        }
    }

    @Test fun missingPluginIsDownloadableOnlyFromAFreshSupportedEntry() {
        assertEquals(StoreAction.DOWNLOAD, project(catalog(entry())).item("player-salt").primaryAction())
        val expired = project(catalog(entry(), freshness = CatalogFreshness.EXPIRED)).item("player-salt")
        assertEquals(Availability.CATALOG_EXPIRED, expired.availability)
        assertEquals(StoreAction.NONE, expired.primaryAction())
        val protocol = project(catalog(entry(minStoreProtocol = 2))).item("player-salt")
        assertEquals(Availability.NEEDS_NEWER_BRIDGE, protocol.availability)
        assertEquals(StoreNote.NEEDS_NEWER_BRIDGE, protocol.note())
        val sdk = project(catalog(entry(minSdk = 35)), sdk = 34).item("player-salt")
        assertEquals(Availability.UNSUPPORTED_SDK, sdk.availability)
        assertEquals(StoreAction.NONE, sdk.primaryAction())
        val absent = project(catalog(entry())).item("player-qq")
        assertEquals(Availability.NOT_IN_CATALOG, absent.availability)
        assertEquals(StoreNote.NOT_IN_CATALOG, absent.note())
    }

    @Test fun anEntryPointingAKnownPluginAtAnotherPackageIsIgnoredAndCounted() {
        val p = project(catalog(entry(applicationId = universal), entry(providerId = "player-future", applicationId = "com.example.future")))
        assertEquals(Availability.NOT_IN_CATALOG, p.item("player-salt").availability)
        assertEquals(2, p.unrecognizedEntries)
    }

    @Test fun notesPutWarningsFirstAndExplainIndependentApps() {
        val c = catalog(entry())
        val p = project(c, mapOf(universal to installed(1), salt to installed(9)))
        assertEquals(StoreNote.LOCAL_NEWER, p.item("player-salt").note())
        assertEquals(StoreNote.INDEPENDENT, p.item("universal-provider").note())
        // Not installed, no catalog entry, no host: the catalog gap is the actionable fact.
        assertEquals(StoreNote.NOT_IN_CATALOG, p.item("player-kugou").note())
        assertEquals(StoreNote.HOST_MISSING, project(CatalogState.Unavailable).item("player-kugou").note())
        assertFalse(StoreNote.LOCAL_NEWER.isWarning)
    }

    @Test fun catalogHeadline() {
        assertEquals(CatalogHeadline.LOADING, CatalogView().headline())
        assertEquals(CatalogHeadline.NONE, CatalogView(CatalogState.Unavailable).headline())
        assertEquals(CatalogHeadline.FRESH, CatalogView(catalog()).headline())
        assertEquals(CatalogHeadline.EXPIRED, CatalogView(catalog(freshness = CatalogFreshness.EXPIRED)).headline())
    }
}
