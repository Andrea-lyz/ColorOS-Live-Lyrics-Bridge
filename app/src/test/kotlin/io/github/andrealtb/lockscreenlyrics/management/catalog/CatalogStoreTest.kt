package io.github.andrealtb.lockscreenlyrics.management.catalog

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogStoreTest {
    private companion object {
        const val ISSUED = 1_794_614_400L
        const val NOW = ISSUED + 86_400L
    }

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }
    private val verifier = CatalogVerifier(CatalogTrust(mapOf("k1" to keyPair.public), setOf(CatalogTrust.RELEASE_CERTIFICATE_SHA256)))

    private fun catalog(revision: Long, issued: Long = ISSUED, versionName: String = "1.1.2"): ByteArray {
        val payload = JSONObject(javaClass.classLoader!!.getResource("catalog/fixture-payload.json")!!.readText())
            .put("catalogRevision", revision)
            .put("issuedAtEpochSeconds", issued)
            .put("expiresAtEpochSeconds", issued + 180 * 86_400L)
        payload.getJSONArray("entries").getJSONObject(0).put("versionName", versionName)
        val bytes = payload.toString().toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(CatalogVerifier.DOMAIN)
            update(bytes)
            sign()
        }
        val b64 = Base64.getEncoder()
        return JSONObject().put("envelopeVersion", 1).put("keyId", "k1").put("algorithm", "ECDSA_P256_SHA256")
            .put("payload", b64.encodeToString(bytes)).put("signature", b64.encodeToString(signature))
            .toString().toByteArray()
    }

    private class MemoryPersistence(var value: PersistedCatalog? = null, var failWrites: Boolean = false) : CatalogPersistence {
        var writes = 0
        override fun read() = value
        override fun write(value: PersistedCatalog) {
            if (failWrites) throw IOException("disk full")
            writes++
            this.value = value
        }
    }

    private fun current(state: CatalogState): CatalogState.Current =
        state as? CatalogState.Current ?: throw AssertionError("expected a catalog, got $state")

    @Test fun nothingPersistedAndNoSnapshotMeansUnavailable() {
        assertEquals(CatalogState.Unavailable, CatalogStore(verifier, MemoryPersistence()) { null }.load(NOW))
    }

    @Test fun bundledSnapshotSeedsTheFloorOnFirstRun() {
        val disk = MemoryPersistence()
        val state = current(CatalogStore(verifier, disk) { catalog(5) }.load(NOW))
        assertEquals(CatalogSource.BUNDLED, state.source)
        assertEquals(5L, disk.value!!.accepted.revision)
        assertTrue(state.installable)
    }

    @Test fun olderBundledSnapshotNeverRollsBackAPersistedCatalog() {
        val disk = MemoryPersistence()
        val store = CatalogStore(verifier, disk) { catalog(3) }
        assertTrue(store.offer(catalog(7), NOW) is CatalogOffer.Updated)
        val state = current(store.load(NOW))
        assertEquals(CatalogSource.PERSISTED, state.source)
        assertEquals(7L, state.catalog.revision)
        assertEquals(7L, disk.value!!.accepted.revision)
    }

    @Test fun newerBundledSnapshotFromABridgeUpdateRaisesTheFloor() {
        val disk = MemoryPersistence()
        CatalogStore(verifier, disk) { null }.offer(catalog(4), NOW)
        val state = current(CatalogStore(verifier, disk) { catalog(9) }.load(NOW))
        assertEquals(CatalogSource.BUNDLED, state.source)
        assertEquals(9L, disk.value!!.accepted.revision)
    }

    @Test fun remoteOffersAreMonotonic() {
        val disk = MemoryPersistence()
        val store = CatalogStore(verifier, disk) { null }
        val r5 = catalog(5)
        assertTrue(store.offer(r5, NOW) is CatalogOffer.Updated)
        assertEquals(CatalogOffer.Unchanged, store.offer(r5, NOW))
        val rollback = store.offer(catalog(4), NOW)
        assertTrue(rollback is CatalogOffer.Rejected && rollback.reason == CatalogRejection.ROLLBACK)
        val fork = store.offer(catalog(5, versionName = "9.9.9"), NOW)
        assertTrue(fork is CatalogOffer.Rejected && fork.reason == CatalogRejection.REVISION_CONFLICT)
        assertEquals(1, disk.writes)
    }

    @Test fun expiredCatalogIsDisplayOnly() {
        val disk = MemoryPersistence()
        val state = current(CatalogStore(verifier, disk) { catalog(2) }.load(ISSUED + 200 * 86_400L))
        assertEquals(CatalogFreshness.EXPIRED, state.freshness)
        assertTrue(!state.installable)
    }

    @Test fun tamperedPersistedStateFallsBackToTheSnapshotWithoutLoweringTheFloor() {
        val disk = MemoryPersistence()
        val store = CatalogStore(verifier, disk) { catalog(3) }
        store.offer(catalog(6), NOW)
        // Bytes edited on disk: signature fails, the floor (revision 6) still blocks the r3 snapshot.
        disk.value = PersistedCatalog(disk.value!!.accepted, catalog(6).also { it[it.size / 2] = 'x'.code.toByte() })
        assertEquals(CatalogState.Unavailable, store.load(NOW))
        assertEquals(6L, disk.value!!.accepted.revision)
    }

    @Test fun failingWritesNeverExposeAnUnpersistedCatalog() {
        val disk = MemoryPersistence(failWrites = true)
        val store = CatalogStore(verifier, disk) { catalog(3) }
        assertEquals(CatalogState.Unavailable, store.load(NOW))
        assertEquals(CatalogOffer.PersistFailed, store.offer(catalog(4), NOW))
    }

    @Test fun unreadableSnapshotIsIgnored() {
        val store = CatalogStore(verifier, MemoryPersistence()) { throw IOException("asset broken") }
        assertEquals(CatalogState.Unavailable, store.load(NOW))
        assertEquals(CatalogState.Unavailable, CatalogStore(verifier, MemoryPersistence()) { "junk".toByteArray() }.load(NOW))
    }

    @Test fun filePersistenceRoundTripsAndReplacesAtomically() {
        val dir = Files.createTempDirectory("catalog-store").toFile()
        try {
            val disk = FileCatalogPersistence(File(dir, "store"))
            assertNull(disk.read())
            val first = PersistedCatalog(AcceptedRevision(1, "a".repeat(64)), byteArrayOf(1, 2, 3))
            val second = PersistedCatalog(AcceptedRevision(2, "b".repeat(64)), byteArrayOf(4, 5))
            disk.write(first)
            assertEquals(first, disk.read())
            disk.write(second)
            assertEquals(second, disk.read())
            assertTrue(File(dir, "store").listFiles()!!.map { it.name } == listOf(FileCatalogPersistence.FILE_NAME))

            File(dir, "store/${FileCatalogPersistence.FILE_NAME}").writeText("{not json")
            assertNull(disk.read())

            val store = CatalogStore(verifier, disk) { null }
            assertTrue(store.offer(catalog(8), NOW) is CatalogOffer.Updated)
            assertEquals(8L, current(CatalogStore(verifier, FileCatalogPersistence(File(dir, "store"))) { null }.load(NOW)).catalog.revision)
        } finally {
            dir.deleteRecursively()
        }
    }
}
