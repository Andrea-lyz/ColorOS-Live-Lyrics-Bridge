package io.github.andrealtb.lockscreenlyrics.management.catalog

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal enum class CatalogFreshness {
    FRESH,

    /** Signed and otherwise valid but past expiresAt: show installed state, block install/update. */
    EXPIRED,
}

internal enum class CatalogRejection {
    TOO_LARGE,
    MALFORMED_ENVELOPE,
    UNSUPPORTED_ENVELOPE,
    UNSUPPORTED_ALGORITHM,
    UNKNOWN_KEY,
    BAD_SIGNATURE,
    MALFORMED_PAYLOAD,
    UNSUPPORTED_SCHEMA,
    WRONG_CHANNEL,
    INVALID_VALIDITY,
    NOT_YET_VALID,
    ROLLBACK,
    REVISION_CONFLICT,
    INVALID_ENTRY,
    UNTRUSTED_URL,
    UNTRUSTED_SIGNER,
    DUPLICATE_ENTRY,
}

/** The newest catalog this client accepted; persisted by the caller and never decreased. */
internal data class AcceptedRevision(val revision: Long, val payloadSha256: String)

internal sealed interface CatalogVerification {
    data class Accepted(
        val catalog: Catalog,
        val freshness: CatalogFreshness,
        val accepted: AcceptedRevision,
    ) : CatalogVerification

    data class Rejected(val reason: CatalogRejection, val detail: String) : CatalogVerification
}

/**
 * Verifies a signed catalog envelope (D2): ECDSA P-256 / SHA-256 over [DOMAIN] followed by the
 * exact payload bytes, then parses only those bytes. Any rule failure rejects the whole catalog;
 * there is no partially trusted catalog. Mirrors Providers scripts/catalog/catalog-envelope.mjs.
 *
 * Pure JVM code: no Android, network, storage or Hook dependency. The caller supplies time and
 * the last accepted revision, and persists [CatalogVerification.Accepted.accepted].
 */
internal class CatalogVerifier(
    private val trust: CatalogTrust,
    private val expectedChannel: String = "stable",
    private val clockSkewSeconds: Long = CLOCK_SKEW_SECONDS,
) {
    fun verify(
        envelopeBytes: ByteArray,
        nowEpochSeconds: Long,
        lastAccepted: AcceptedRevision?,
    ): CatalogVerification = try {
        verifyOrThrow(envelopeBytes, nowEpochSeconds, lastAccepted)
    } catch (rejection: Reject) {
        CatalogVerification.Rejected(rejection.reason, rejection.detail)
    }

    private fun verifyOrThrow(
        envelopeBytes: ByteArray,
        now: Long,
        lastAccepted: AcceptedRevision?,
    ): CatalogVerification.Accepted {
        if (envelopeBytes.size > MAX_ENVELOPE_BYTES) reject(CatalogRejection.TOO_LARGE, "envelope")
        val envelope = parseObject(envelopeBytes, CatalogRejection.MALFORMED_ENVELOPE)
        if (envelope.opt("envelopeVersion") != ENVELOPE_VERSION) {
            reject(CatalogRejection.UNSUPPORTED_ENVELOPE, "envelopeVersion")
        }
        if (envelope.opt("algorithm") != ALGORITHM) reject(CatalogRejection.UNSUPPORTED_ALGORITHM, "algorithm")
        val keyId = string(envelope, "keyId", CatalogRejection.MALFORMED_ENVELOPE)
        val key = trust.catalogKeys[keyId] ?: reject(CatalogRejection.UNKNOWN_KEY, keyId)
        val payload = base64(envelope, "payload")
        val signature = base64(envelope, "signature")

        val signatureValid = try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(DOMAIN)
                update(payload)
                verify(signature)
            }
        } catch (_: java.security.SignatureException) {
            false
        }
        if (!signatureValid) reject(CatalogRejection.BAD_SIGNATURE, keyId)

        // Everything below reads signed bytes only.
        val digest = sha256Hex(payload)
        val root = parseObject(payload, CatalogRejection.MALFORMED_PAYLOAD)
        if (root.opt("schemaVersion") != SCHEMA_VERSION) reject(CatalogRejection.UNSUPPORTED_SCHEMA, "schemaVersion")
        val revision = long(root, "catalogRevision", CatalogRejection.MALFORMED_PAYLOAD)
        val issued = long(root, "issuedAtEpochSeconds", CatalogRejection.MALFORMED_PAYLOAD)
        val expires = long(root, "expiresAtEpochSeconds", CatalogRejection.MALFORMED_PAYLOAD)
        if (revision < 1) reject(CatalogRejection.MALFORMED_PAYLOAD, "catalogRevision")
        if (issued <= 0 || expires <= issued || expires - issued > MAX_VALIDITY_SECONDS) {
            reject(CatalogRejection.INVALID_VALIDITY, "issued/expires")
        }
        val channel = string(root, "channel", CatalogRejection.MALFORMED_PAYLOAD)
        if (channel != expectedChannel) reject(CatalogRejection.WRONG_CHANNEL, channel)
        if (now + clockSkewSeconds < issued) reject(CatalogRejection.NOT_YET_VALID, "issuedAt")
        if (lastAccepted != null) {
            if (revision < lastAccepted.revision) reject(CatalogRejection.ROLLBACK, "$revision<${lastAccepted.revision}")
            if (revision == lastAccepted.revision && digest != lastAccepted.payloadSha256) {
                reject(CatalogRejection.REVISION_CONFLICT, "$revision")
            }
        }

        val hostsJson = array(root, "trustedHosts", CatalogRejection.MALFORMED_PAYLOAD)
        if (hostsJson.length() !in 1..MAX_HOSTS) reject(CatalogRejection.MALFORMED_PAYLOAD, "trustedHosts")
        val hosts = (0 until hostsJson.length()).map { i ->
            val host = hostsJson.opt(i) as? String
            if (host == null || !HOST.matches(host)) reject(CatalogRejection.MALFORMED_PAYLOAD, "trustedHosts[$i]")
            host
        }.toSet()

        val entriesJson = array(root, "entries", CatalogRejection.MALFORMED_PAYLOAD)
        if (entriesJson.length() > MAX_ENTRIES) reject(CatalogRejection.MALFORMED_PAYLOAD, "entries")
        val entries = (0 until entriesJson.length()).map { i ->
            val item = entriesJson.opt(i) as? JSONObject ?: reject(CatalogRejection.INVALID_ENTRY, "entries[$i]")
            entry(item, "entries[$i]", hosts)
        }
        if (entries.map { it.providerId }.toSet().size != entries.size ||
            entries.map { it.applicationId }.toSet().size != entries.size
        ) {
            reject(CatalogRejection.DUPLICATE_ENTRY, "providerId/applicationId")
        }

        val freshness = if (now > expires + clockSkewSeconds) CatalogFreshness.EXPIRED else CatalogFreshness.FRESH
        return CatalogVerification.Accepted(
            Catalog(revision, issued, expires, channel, hosts, entries),
            freshness,
            AcceptedRevision(revision, digest),
        )
    }

    private fun entry(json: JSONObject, at: String, hosts: Set<String>): CatalogEntry {
        val bad = CatalogRejection.INVALID_ENTRY
        val providerId = string(json, "providerId", bad).also { if (!PROVIDER_ID.matches(it)) reject(bad, "$at.providerId") }
        val kind = CatalogKind.fromWire(string(json, "kind", bad)) ?: reject(bad, "$at.kind")
        val applicationId = string(json, "applicationId", bad)
            .also { if (!APPLICATION_ID.matches(it)) reject(bad, "$at.applicationId") }
        val versionName = string(json, "versionName", bad)
            .also { if (it.isBlank() || it.length > 64 || CONTROL.containsMatchIn(it)) reject(bad, "$at.versionName") }
        val versionCode = long(json, "versionCode", bad).also { if (it < 1) reject(bad, "$at.versionCode") }
        val minSdk = long(json, "minSdk", bad).also { if (it !in 1L..1000L) reject(bad, "$at.minSdk") }
        val minStoreProtocol = long(json, "minStoreProtocol", bad)
            .also { if (it !in 1L..Int.MAX_VALUE.toLong()) reject(bad, "$at.minStoreProtocol") }
        val sourceCommit = string(json, "sourceCommit", bad)
            .also { if (!HEX40.matches(it) || ALL_ZERO.matches(it)) reject(bad, "$at.sourceCommit") }
        val notes = when (val raw = json.opt("releaseNotes")) {
            null -> null
            is String -> raw.also { if (it.length > 4000 || CONTROL.containsMatchIn(it)) reject(bad, "$at.releaseNotes") }
            else -> reject(bad, "$at.releaseNotes")
        }

        val artifactJson = json.opt("artifact") as? JSONObject ?: reject(bad, "$at.artifact")
        val urlsJson = array(artifactJson, "urls", bad)
        if (urlsJson.length() !in 1..MAX_URLS) reject(bad, "$at.artifact.urls")
        val urls = (0 until urlsJson.length()).map { i ->
            val raw = urlsJson.opt(i) as? String ?: reject(bad, "$at.artifact.urls[$i]")
            raw.also { checkUrl(it, hosts, "$at.artifact.urls[$i]") }
        }
        val bytes = long(artifactJson, "bytes", bad).also { if (it !in 1L..MAX_ARTIFACT_BYTES) reject(bad, "$at.artifact.bytes") }
        val sha256 = string(artifactJson, "sha256", bad)
            .also { if (!HEX64.matches(it) || ALL_ZERO.matches(it)) reject(bad, "$at.artifact.sha256") }
        val signersJson = array(artifactJson, "signerSha256", bad)
        if (signersJson.length() !in 1..MAX_SIGNERS) reject(bad, "$at.artifact.signerSha256")
        val signers = (0 until signersJson.length()).map { i ->
            val signer = signersJson.opt(i) as? String
            if (signer == null || !HEX64.matches(signer) || ALL_ZERO.matches(signer)) reject(bad, "$at.artifact.signerSha256[$i]")
            if (signer !in trust.releaseSignerSha256) reject(CatalogRejection.UNTRUSTED_SIGNER, "$at.artifact.signerSha256[$i]")
            signer
        }.toSet()

        return CatalogEntry(
            providerId, kind, applicationId, versionName, versionCode, minSdk.toInt(),
            minStoreProtocol.toInt(), sourceCommit, notes,
            CatalogArtifact(urls, bytes, sha256, signers),
        )
    }

    private fun checkUrl(raw: String, hosts: Set<String>, at: String) {
        val uri = try {
            URI(raw)
        } catch (_: java.net.URISyntaxException) {
            reject(CatalogRejection.UNTRUSTED_URL, at)
        }
        val plainHttps = uri.scheme == "https" && uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port == 443)
        if (!plainHttps || uri.host == null || uri.host !in hosts) reject(CatalogRejection.UNTRUSTED_URL, at)
    }

    private class Reject(val reason: CatalogRejection, val detail: String) : Exception(null, null, false, false)

    companion object {
        const val ENVELOPE_VERSION = 1
        const val SCHEMA_VERSION = 1
        const val ALGORITHM = "ECDSA_P256_SHA256"

        /** Store protocol implemented by this client; entries may require a newer one. */
        const val STORE_PROTOCOL = 1
        const val CLOCK_SKEW_SECONDS = 24L * 3600
        const val MAX_VALIDITY_SECONDS = 190L * 24 * 3600
        const val MAX_ENVELOPE_BYTES = 256 * 1024
        const val MAX_ARTIFACT_BYTES = 64L * 1024 * 1024
        private const val MAX_HOSTS = 16
        private const val MAX_ENTRIES = 64
        private const val MAX_URLS = 8
        private const val MAX_SIGNERS = 4

        val DOMAIN: ByteArray = "CLL-CATALOG-V1\n".toByteArray(StandardCharsets.US_ASCII)

        private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
        private val PROVIDER_ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val APPLICATION_ID = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        private val HEX64 = Regex("^[0-9a-f]{64}$")
        private val HEX40 = Regex("^[0-9a-f]{40}$")
        private val ALL_ZERO = Regex("^0+$")
        private val CONTROL = Regex("[\\u0000-\\u0009\\u000b-\\u001f\\u007f]")

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun reject(reason: CatalogRejection, detail: String): Nothing = throw Reject(reason, detail)

        private fun parseObject(bytes: ByteArray, reason: CatalogRejection): JSONObject {
            val text = try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                reject(reason, "utf-8")
            }
            return try {
                JSONObject(text)
            } catch (_: org.json.JSONException) {
                reject(reason, "json")
            }
        }

        private fun string(json: JSONObject, name: String, reason: CatalogRejection): String =
            json.opt(name) as? String ?: reject(reason, name)

        /** Integral JSON numbers only; org.json would otherwise coerce strings and doubles. */
        private fun long(json: JSONObject, name: String, reason: CatalogRejection): Long =
            when (val value = json.opt(name)) {
                is Int -> value.toLong()
                is Long -> value
                else -> reject(reason, name)
            }

        private fun array(json: JSONObject, name: String, reason: CatalogRejection): JSONArray =
            json.opt(name) as? JSONArray ?: reject(reason, name)

        private fun base64(json: JSONObject, name: String): ByteArray {
            val text = string(json, name, CatalogRejection.MALFORMED_ENVELOPE)
            return try {
                Base64.getDecoder().decode(text)
            } catch (_: IllegalArgumentException) {
                reject(CatalogRejection.MALFORMED_ENVELOPE, name)
            }
        }
    }
}
