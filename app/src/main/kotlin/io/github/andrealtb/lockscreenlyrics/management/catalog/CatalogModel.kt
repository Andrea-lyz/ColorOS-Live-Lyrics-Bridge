package io.github.andrealtb.lockscreenlyrics.management.catalog

/**
 * Provider store catalog, schema 1 (plan §6.3, decisions D1/D2 in §14.5). Only produced by
 * [CatalogVerifier] after the envelope signature and every rule below passed; nothing here is
 * read from an unverified source.
 */
internal enum class CatalogKind(val wire: String) {
    DEDICATED_LYRICS("dedicated-lyrics"),
    UNIVERSAL_LYRICS("universal-lyrics"),
    ARTWORK("artwork");

    companion object {
        fun fromWire(value: String): CatalogKind? = entries.firstOrNull { it.wire == value }
    }
}

internal data class CatalogArtifact(
    /** Ordered mirrors of the same bytes; every host is in [Catalog.trustedHosts]. */
    val urls: List<String>,
    val bytes: Long,
    val sha256: String,
    /** Subset of the client-pinned release certificates; a catalog cannot introduce a signer. */
    val signerSha256: Set<String>,
)

internal data class CatalogEntry(
    val providerId: String,
    val kind: CatalogKind,
    val applicationId: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val minStoreProtocol: Int,
    val sourceCommit: String,
    val releaseNotes: String?,
    val artifact: CatalogArtifact,
) {
    /** Entries for a newer store protocol stay listed but are not installable by this client. */
    fun supportedBy(storeProtocol: Int = CatalogVerifier.STORE_PROTOCOL): Boolean = minStoreProtocol <= storeProtocol
}

internal data class Catalog(
    val revision: Long,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val channel: String,
    val trustedHosts: Set<String>,
    val entries: List<CatalogEntry>,
)
