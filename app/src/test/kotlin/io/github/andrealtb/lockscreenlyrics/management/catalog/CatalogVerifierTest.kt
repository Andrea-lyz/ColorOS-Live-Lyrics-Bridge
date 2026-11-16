package io.github.andrealtb.lockscreenlyrics.management.catalog

import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

class CatalogVerifierTest {
    private companion object {
        const val ISSUED = 1_794_614_400L
        const val EXPIRES = 1_810_166_400L
        const val DAY = 86_400L
        const val NOW = ISSUED + DAY
        const val FIXTURE_KEY_ID = "test-only-2026"
        const val FIXTURE_PAYLOAD_SHA256 = "289f23118d90bee9a9547b078a51ac9486a97f0257f1fd152f701cfc13aec223"
        val SIGNER = CatalogTrust.RELEASE_CERTIFICATE_SHA256
    }

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResource("catalog/$name")!!.readText()

    private fun pemToSpki(pem: String): String =
        pem.lines().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private val trust = CatalogTrust(mapOf("k1" to keyPair.public), setOf(SIGNER))
    private val verifier = CatalogVerifier(trust)

    private fun basePayload(): JSONObject = JSONObject(resource("fixture-payload.json"))

    private fun envelope(
        payload: ByteArray,
        signWith: KeyPair = keyPair,
        keyId: String = "k1",
        domain: ByteArray = CatalogVerifier.DOMAIN,
        mutate: (JSONObject) -> Unit = {},
    ): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(signWith.private)
            update(domain)
            update(payload)
            sign()
        }
        val b64 = Base64.getEncoder()
        return JSONObject()
            .put("envelopeVersion", 1)
            .put("keyId", keyId)
            .put("algorithm", "ECDSA_P256_SHA256")
            .put("payload", b64.encodeToString(payload))
            .put("signature", b64.encodeToString(signature))
            .also(mutate)
            .toString().toByteArray()
    }

    private fun signed(edit: (JSONObject) -> Unit = {}): ByteArray =
        envelope(basePayload().also(edit).toString().toByteArray())

    private fun entry0(p: JSONObject): JSONObject = p.getJSONArray("entries").getJSONObject(0)
    private fun artifact0(p: JSONObject): JSONObject = entry0(p).getJSONObject("artifact")

    private fun accepted(result: CatalogVerification): CatalogVerification.Accepted =
        result as? CatalogVerification.Accepted ?: run { fail("expected accepted, got $result"); throw AssertionError() }

    private fun assertRejected(reason: CatalogRejection, result: CatalogVerification) {
        assertTrue("expected $reason, got $result",
            result is CatalogVerification.Rejected && result.reason == reason)
    }

    @Test fun sharedFixtureFromTheProvidersSignerVerifies() {
        val fixtureTrust = CatalogTrust(
            mapOf(FIXTURE_KEY_ID to CatalogTrust.decodeP256PublicKey(pemToSpki(resource("TEST-ONLY-catalog-signing-key.pub.pem")))),
            setOf(SIGNER),
        )
        val result = accepted(CatalogVerifier(fixtureTrust).verify(resource("fixture-envelope.json").toByteArray(), NOW, null))
        assertEquals(CatalogFreshness.FRESH, result.freshness)
        assertEquals(AcceptedRevision(3, FIXTURE_PAYLOAD_SHA256), result.accepted)
        val salt = result.catalog.entries.first { it.providerId == "player-salt" }
        assertEquals(CatalogKind.DEDICATED_LYRICS, salt.kind)
        assertEquals(8L, salt.versionCode)
        assertEquals(setOf(SIGNER), salt.artifact.signerSha256)
        assertEquals("TEST FIXTURE ONLY.\n不是正式发布。", salt.releaseNotes)
        assertEquals(CatalogKind.UNIVERSAL_LYRICS, result.catalog.entries[1].kind)
    }

    @Test fun productionTrustPinsTwoP256KeysAndRejectsTheTestFixture() {
        assertEquals(setOf("cll-catalog-2026a", "cll-catalog-2026b"), CatalogTrust.PRODUCTION_CATALOG_KEYS.keys)
        assertFalse(CatalogTrust.PRODUCTION_CATALOG_KEYS.keys.any { it.startsWith("test-") })
        assertEquals(2, CatalogTrust.production().catalogKeys.size)
        assertRejected(CatalogRejection.UNKNOWN_KEY,
            CatalogVerifier(CatalogTrust.production()).verify(resource("fixture-envelope.json").toByteArray(), NOW, null))
    }

    @Test fun pinnedReleaseCertificateMatchesTheBridgeReleaseContract() {
        val contract = listOf("../release/bridge-release-contract.json", "release/bridge-release-contract.json")
            .map(::File).firstOrNull { it.isFile }
        assumeTrue("bridge-release-contract.json visible", contract != null)
        assertEquals(JSONObject(contract!!.readText()).getString("releaseCertificateSha256"),
            CatalogTrust.RELEASE_CERTIFICATE_SHA256)
    }

    @Test fun pinnedCatalogTrustMatchesTheProvidersSigningConfiguration() {
        // Same discovery as ProviderDirectoryContractTest: the matrix env var points into the
        // Providers release/ directory, which also holds catalog-trust.json.
        val file = listOfNotNull(
            System.getenv("CLL_PROVIDER_MATRIX")?.let { File(it).resolveSibling("catalog-trust.json").path },
            "../../ColorOS-Live-Lyrics-Providers/release/catalog-trust.json",
            "../ColorOS-Live-Lyrics-Providers/release/catalog-trust.json",
        ).map(::File).firstOrNull { it.isFile }
        assumeTrue("Providers release/catalog-trust.json visible", file != null)
        val trustJson = JSONObject(file!!.readText())
        assertEquals(trustJson.getString("releaseCertificateSha256"), CatalogTrust.RELEASE_CERTIFICATE_SHA256)
        val keys = trustJson.getJSONObject("keys")
        val keyIds = keys.keys().asSequence().toSet()
        assertEquals(keyIds, CatalogTrust.PRODUCTION_CATALOG_KEYS.keys)
        for (keyId in keyIds) {
            assertEquals(keyId, keys.getString(keyId), CatalogTrust.PRODUCTION_CATALOG_KEYS[keyId])
        }
    }

    @Test fun validCatalogIsAccepted() {
        val result = accepted(verifier.verify(signed(), NOW, null))
        assertEquals(3L, result.catalog.revision)
        assertEquals(2, result.catalog.entries.size)
        assertEquals(setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"),
            result.catalog.trustedHosts)
    }

    @Test fun tamperedSignatureKeyOrDomainIsRejected() {
        val good = basePayload().toString().toByteArray()
        val flipped = good.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        val tampered = JSONObject(String(envelope(good))).put("payload", Base64.getEncoder().encodeToString(flipped))
        assertRejected(CatalogRejection.BAD_SIGNATURE, verifier.verify(tampered.toString().toByteArray(), NOW, null))

        val other = KeyPairGenerator.getInstance("EC").run { initialize(ECGenParameterSpec("secp256r1")); generateKeyPair() }
        assertRejected(CatalogRejection.BAD_SIGNATURE, verifier.verify(envelope(good, signWith = other), NOW, null))
        assertRejected(CatalogRejection.BAD_SIGNATURE, verifier.verify(envelope(good, domain = ByteArray(0)), NOW, null))
        assertRejected(CatalogRejection.UNKNOWN_KEY, verifier.verify(envelope(good, keyId = "k2"), NOW, null))
        assertRejected(CatalogRejection.BAD_SIGNATURE,
            verifier.verify(envelope(good) { it.put("signature", Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))) }, NOW, null))
    }

    @Test fun envelopeShapeIsStrict() {
        val good = basePayload().toString().toByteArray()
        assertRejected(CatalogRejection.UNSUPPORTED_ENVELOPE, verifier.verify(envelope(good) { it.put("envelopeVersion", 2) }, NOW, null))
        assertRejected(CatalogRejection.UNSUPPORTED_ENVELOPE, verifier.verify(envelope(good) { it.put("envelopeVersion", "1") }, NOW, null))
        assertRejected(CatalogRejection.UNSUPPORTED_ALGORITHM, verifier.verify(envelope(good) { it.put("algorithm", "ED25519") }, NOW, null))
        assertRejected(CatalogRejection.MALFORMED_ENVELOPE, verifier.verify(envelope(good) { it.put("payload", "@@not base64@@") }, NOW, null))
        assertRejected(CatalogRejection.MALFORMED_ENVELOPE, verifier.verify("not json".toByteArray(), NOW, null))
        assertRejected(CatalogRejection.TOO_LARGE,
            verifier.verify(ByteArray(CatalogVerifier.MAX_ENVELOPE_BYTES + 1) { ' '.code.toByte() }, NOW, null))
    }

    @Test fun signedButMalformedPayloadIsRejected() {
        assertRejected(CatalogRejection.MALFORMED_PAYLOAD, verifier.verify(envelope(byteArrayOf(0xC3.toByte(), 0x28)), NOW, null))
        assertRejected(CatalogRejection.UNSUPPORTED_SCHEMA, verifier.verify(signed { it.put("schemaVersion", 2) }, NOW, null))
        assertRejected(CatalogRejection.MALFORMED_PAYLOAD, verifier.verify(signed { it.put("catalogRevision", "3") }, NOW, null))
        assertRejected(CatalogRejection.MALFORMED_PAYLOAD, verifier.verify(signed { it.put("catalogRevision", 0) }, NOW, null))
        assertRejected(CatalogRejection.MALFORMED_PAYLOAD, verifier.verify(signed { it.put("trustedHosts", JSONArray()) }, NOW, null))
        assertRejected(CatalogRejection.MALFORMED_PAYLOAD,
            verifier.verify(signed { it.put("trustedHosts", JSONArray(listOf("GitHub.com"))) }, NOW, null))
        assertRejected(CatalogRejection.WRONG_CHANNEL, verifier.verify(signed { it.put("channel", "test") }, NOW, null))
    }

    @Test fun validityWindowAndClockSkew() {
        val skew = CatalogVerifier.CLOCK_SKEW_SECONDS
        assertEquals(CatalogFreshness.FRESH, accepted(verifier.verify(signed(), ISSUED - skew, null)).freshness)
        assertRejected(CatalogRejection.NOT_YET_VALID, verifier.verify(signed(), ISSUED - skew - 1, null))
        assertEquals(CatalogFreshness.FRESH, accepted(verifier.verify(signed(), EXPIRES + skew, null)).freshness)
        assertEquals(CatalogFreshness.EXPIRED, accepted(verifier.verify(signed(), EXPIRES + skew + 1, null)).freshness)
        assertRejected(CatalogRejection.INVALID_VALIDITY,
            verifier.verify(signed { it.put("expiresAtEpochSeconds", ISSUED + 191 * DAY) }, NOW, null))
        assertRejected(CatalogRejection.INVALID_VALIDITY,
            verifier.verify(signed { it.put("expiresAtEpochSeconds", ISSUED) }, NOW, null))
    }

    @Test fun revisionNeverGoesBackwardsOrForks() {
        val first = accepted(verifier.verify(signed(), NOW, null)).accepted
        // Re-serving the exact same signed payload is fine (e.g. another mirror).
        assertEquals(first, accepted(verifier.verify(envelope(basePayload().toString().toByteArray()), NOW, first)).accepted)
        assertRejected(CatalogRejection.REVISION_CONFLICT,
            verifier.verify(signed { entry0(it).put("versionName", "1.1.2-other") }, NOW, first))
        assertRejected(CatalogRejection.ROLLBACK, verifier.verify(signed { it.put("catalogRevision", 2) }, NOW, first))
        assertEquals(4L, accepted(verifier.verify(signed { it.put("catalogRevision", 4) }, NOW, first)).accepted.revision)
    }

    @Test fun catalogCannotIntroduceSignersOrHosts() {
        assertRejected(CatalogRejection.UNTRUSTED_SIGNER,
            verifier.verify(signed { artifact0(it).put("signerSha256", JSONArray(listOf("ab".repeat(32)))) }, NOW, null))
        val urls = listOf(
            "https://evil.example/a.apk",
            "http://github.com/a.apk",
            "https://user@github.com/a.apk",
            "https://github.com:8443/a.apk",
            "https://github.com/a.apk#frag",
            "ftp://github.com/a.apk",
        )
        for (url in urls) {
            assertRejected(CatalogRejection.UNTRUSTED_URL,
                verifier.verify(signed { artifact0(it).put("urls", JSONArray(listOf(url))) }, NOW, null))
        }
        // A host listed in the signed trustedHosts is accepted (adding a CDN needs no Bridge update).
        val withCdn = accepted(verifier.verify(signed {
            it.getJSONArray("trustedHosts").put("cdn.example.org")
            artifact0(it).getJSONArray("urls").put("https://cdn.example.org/salt.apk")
        }, NOW, null))
        assertEquals(2, withCdn.catalog.entries[0].artifact.urls.size)
    }

    @Test fun zeroPlaceholderAndOutOfRangeEntriesAreRejected() {
        val edits: List<(JSONObject) -> Unit> = listOf(
            { artifact0(it).put("sha256", "0".repeat(64)) },
            { artifact0(it).put("sha256", "AB".repeat(32)) },
            { artifact0(it).put("bytes", 0) },
            { artifact0(it).put("bytes", CatalogVerifier.MAX_ARTIFACT_BYTES + 1) },
            { artifact0(it).put("urls", JSONArray()) },
            { artifact0(it).put("signerSha256", JSONArray()) },
            { entry0(it).put("versionCode", 0) },
            { entry0(it).put("versionCode", "8") },
            { entry0(it).put("versionCode", 8.5) },
            { entry0(it).put("sourceCommit", "0".repeat(40)) },
            { entry0(it).put("kind", "plugin") },
            { entry0(it).put("providerId", "Salt") },
            { entry0(it).put("applicationId", "salt") },
            { entry0(it).put("versionName", " ") },
            { entry0(it).put("releaseNotes", "bell\u0007") },
            { entry0(it).put("releaseNotes", 1) },
            { entry0(it).remove("artifact") },
        )
        for ((i, edit) in edits.withIndex()) {
            val result = verifier.verify(signed(edit), NOW, null)
            assertTrue("edit #$i should be rejected as INVALID_ENTRY, got $result",
                result is CatalogVerification.Rejected && result.reason == CatalogRejection.INVALID_ENTRY)
        }
    }

    @Test fun duplicateEntriesAreRejected() {
        assertRejected(CatalogRejection.DUPLICATE_ENTRY, verifier.verify(signed {
            val entries = it.getJSONArray("entries")
            entries.getJSONObject(1).put("applicationId", entries.getJSONObject(0).getString("applicationId"))
        }, NOW, null))
        assertRejected(CatalogRejection.DUPLICATE_ENTRY, verifier.verify(signed {
            val entries = it.getJSONArray("entries")
            entries.getJSONObject(1).put("providerId", entries.getJSONObject(0).getString("providerId"))
        }, NOW, null))
    }

    @Test fun newerStoreProtocolEntryIsListedButNotSupported() {
        val result = accepted(verifier.verify(signed { entry0(it).put("minStoreProtocol", 2) }, NOW, null))
        assertFalse(result.catalog.entries[0].supportedBy())
        assertTrue(result.catalog.entries[1].supportedBy())
    }

    @Test fun emptyCatalogIsValidAndUnknownFieldsAreIgnored() {
        val result = accepted(verifier.verify(signed {
            it.put("entries", JSONArray())
            it.put("futureField", "ignored")
        }, NOW, null))
        assertTrue(result.catalog.entries.isEmpty())
    }

    @Test fun onlyP256KeysAreAccepted() {
        val p384 = KeyPairGenerator.getInstance("EC").run { initialize(ECGenParameterSpec("secp384r1")); generateKeyPair() }
        try {
            CatalogTrust.decodeP256PublicKey(Base64.getEncoder().encodeToString(p384.public.encoded))
            fail("P-384 key must be refused")
        } catch (_: IllegalArgumentException) {
        }
        CatalogTrust.decodeP256PublicKey(Base64.getEncoder().encodeToString(keyPair.public.encoded))
    }
}
