package io.github.andrealtb.lockscreenlyrics.management.catalog

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Client-pinned trust for the store (D2). Catalog keys and APK signing certificates are two
 * separate trust sets: a valid catalog can only point at APKs signed by a pinned certificate.
 */
internal class CatalogTrust(
    val catalogKeys: Map<String, PublicKey>,
    val releaseSignerSha256: Set<String>,
) {
    companion object {
        /**
         * keyId -> base64 X.509 SubjectPublicKeyInfo (ECDSA P-256), equal to Providers
         * release/catalog-trust.json (bound by test). 2026a is the active key held by the
         * protected `provider-catalog` Environment; 2026b is the offline backup. Rotating either
         * needs a Bridge release with a new pair.
         */
        val PRODUCTION_CATALOG_KEYS: Map<String, String> = mapOf(
            "cll-catalog-2026a" to
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbW5YgxEtOIU7NMZDsWd+oVjdQuCb55eiaSlXGlkN6Pyg8fdGwi9sAzcL+QVRjyb794VHO9fyklXFTto3rjDOEg==",
            "cll-catalog-2026b" to
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAENbTQvHtHeAfGoAEHAAWbUZxhKFKEIuYDlvTvYegpF5U2KOj+98HFv++QZtlfxxDzmmeQGlfVBIqGcW++oXr1nQ==",
        )

        /** Same value as release/bridge-release-contract.json releaseCertificateSha256. */
        const val RELEASE_CERTIFICATE_SHA256 =
            "ff38544ba21922c35989097fe6f2ad0bda434b4d0bb611e016d51d7862d36195"

        fun production(): CatalogTrust = CatalogTrust(
            PRODUCTION_CATALOG_KEYS.mapValues { (_, spki) -> decodeP256PublicKey(spki) },
            setOf(RELEASE_CERTIFICATE_SHA256),
        )

        /** Group order of NIST P-256; distinguishes it from other 256-bit curves. */
        private val P256_ORDER = BigInteger(
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)

        /** Decodes an SPKI key and refuses anything other than an ECDSA P-256 key. */
        fun decodeP256PublicKey(spkiBase64: String): PublicKey {
            val key = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(spkiBase64)))
            require(key is ECPublicKey && key.params.order == P256_ORDER) {
                "catalog keys must be ECDSA P-256"
            }
            return key
        }
    }
}
