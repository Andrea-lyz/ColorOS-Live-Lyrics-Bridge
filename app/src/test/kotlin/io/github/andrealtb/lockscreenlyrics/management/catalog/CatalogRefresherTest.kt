package io.github.andrealtb.lockscreenlyrics.management.catalog

import io.github.andrealtb.lockscreenlyrics.management.net.HttpsBody
import io.github.andrealtb.lockscreenlyrics.management.net.HttpsTransport
import io.github.andrealtb.lockscreenlyrics.management.net.isTrustedHttpsUrl
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CatalogRefresherTest {
    private companion object {
        const val ISSUED = 1_794_614_400L
        const val NOW = ISSUED + 86_400L
        const val A = "https://cdn.jsdelivr.net/a"
        const val B = "https://raw.githubusercontent.com/b"
    }

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }
    private val verifier = CatalogVerifier(CatalogTrust(mapOf("k1" to keyPair.public), setOf(CatalogTrust.RELEASE_CERTIFICATE_SHA256)))

    private fun catalog(revision: Long, channel: String = "stable"): ByteArray {
        val payload = JSONObject(javaClass.classLoader!!.getResource("catalog/fixture-payload.json")!!.readText())
            .put("catalogRevision", revision).put("channel", channel)
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

    private class Disk : CatalogPersistence {
        var value: PersistedCatalog? = null
        override fun read() = value
        override fun write(value: PersistedCatalog) {
            this.value = value
        }
    }

    /** Serves fixed bodies; a missing URL fails like a network error. */
    private class Fake(private val bodies: Map<String, ByteArray>, private val declaredLength: Long? = null) : HttpsTransport {
        val requested = mutableListOf<Pair<String, Set<String>>>()
        override fun get(url: String, allowedHosts: Set<String>): HttpsBody {
            requested += url to allowedHosts
            val bytes = bodies[url] ?: throw IOException("offline")
            return HttpsBody(ByteArrayInputStream(bytes), declaredLength ?: bytes.size.toLong()) {}
        }
    }

    private fun refresh(disk: Disk, transport: Fake): RefreshResult =
        CatalogRefresher(CatalogStore(verifier, disk) { null }, transport, listOf(A, B), setOf("x")).refresh(NOW)

    @Test fun newerCatalogFromAnyMirrorIsStoredAndHostsArePinned() {
        val disk = Disk()
        val transport = Fake(mapOf(A to catalog(4), B to catalog(5)))
        assertEquals(RefreshOutcome.UPDATED, refresh(disk, transport).outcome)
        assertEquals(5L, disk.value!!.accepted.revision)
        assertEquals(listOf(A to setOf("x"), B to setOf("x")), transport.requested)
    }

    @Test fun staleMirrorAfterAFreshOneIsNotAnError() {
        val disk = Disk()
        assertEquals(RefreshOutcome.UPDATED, refresh(disk, Fake(mapOf(A to catalog(6), B to catalog(5)))).outcome)
        assertEquals(6L, disk.value!!.accepted.revision)
        // Both mirrors behind the stored floor (e.g. bundled snapshot is newer): up to date.
        assertEquals(RefreshOutcome.UP_TO_DATE, refresh(disk, Fake(mapOf(A to catalog(2), B to catalog(6)))).outcome)
    }

    @Test fun networkFailureAndRejectionKeepTheStoredCatalog() {
        val disk = Disk()
        refresh(disk, Fake(mapOf(A to catalog(3))))
        assertEquals(RefreshOutcome.NETWORK_FAILED, refresh(disk, Fake(emptyMap())).outcome)
        val rejected = refresh(disk, Fake(mapOf(A to catalog(9, channel = "test"))))
        assertEquals(RefreshOutcome.REJECTED, rejected.outcome)
        assertEquals(CatalogRejection.WRONG_CHANNEL, rejected.rejection)
        assertEquals(3L, disk.value!!.accepted.revision)
        // A valid mirror outweighs a bad one, and the rejection detail is dropped.
        val mixed = refresh(disk, Fake(mapOf(A to "junk".toByteArray(), B to catalog(4))))
        assertEquals(RefreshOutcome.UPDATED, mixed.outcome)
        assertNull(mixed.rejection)
    }

    @Test fun oversizedBodiesAreNotBuffered() {
        val big = ByteArray(CatalogVerifier.MAX_ENVELOPE_BYTES + 1)
        val streamed = refresh(Disk(), Fake(mapOf(A to big), declaredLength = -1))
        assertEquals(RefreshOutcome.REJECTED, streamed.outcome)
        assertEquals(CatalogRejection.TOO_LARGE, streamed.rejection)
        val declared = refresh(Disk(), Fake(mapOf(A to ByteArray(1)), declaredLength = big.size.toLong()))
        assertEquals(CatalogRejection.TOO_LARGE, declared.rejection)
    }

    @Test fun onlyPlainHttpsToListedHostsIsTrusted() {
        val hosts = setOf("github.com")
        assertTrue(isTrustedHttpsUrl("https://github.com/a/b?x=1", hosts))
        assertTrue(isTrustedHttpsUrl("https://GitHub.com:443/a", hosts))
        assertFalse(isTrustedHttpsUrl("http://github.com/a", hosts))
        assertFalse(isTrustedHttpsUrl("https://github.com:8443/a", hosts))
        assertFalse(isTrustedHttpsUrl("https://user@github.com/a", hosts))
        assertFalse(isTrustedHttpsUrl("https://github.com.evil.example/a", hosts))
        assertFalse(isTrustedHttpsUrl("https://evil.example/github.com", hosts))
        assertFalse(isTrustedHttpsUrl("not a url", hosts))
    }

    @Test fun mirrorsServeTheBranchAndPathPinnedInCatalogTrust() {
        for (url in CatalogSources.urls) {
            assertTrue(url, isTrustedHttpsUrl(url, CatalogSources.hosts))
            assertTrue(url, url.contains(CatalogSources.REPOSITORY) && url.contains(CatalogSources.BRANCH) &&
                url.endsWith("/" + CatalogSources.PATH))
        }
        val file = listOfNotNull(
            System.getenv("CLL_PROVIDER_MATRIX")?.let { File(it).resolveSibling("catalog-trust.json").path },
            "../../ColorOS-Live-Lyrics-Providers/release/catalog-trust.json",
            "../ColorOS-Live-Lyrics-Providers/release/catalog-trust.json",
        ).map(::File).firstOrNull { it.isFile }
        assumeTrue("Providers release/catalog-trust.json visible", file != null)
        val trust = JSONObject(file!!.readText())
        assertEquals(trust.getString("catalogBranch"), CatalogSources.BRANCH)
        assertEquals(trust.getString("catalogPath"), CatalogSources.PATH)
    }
}
