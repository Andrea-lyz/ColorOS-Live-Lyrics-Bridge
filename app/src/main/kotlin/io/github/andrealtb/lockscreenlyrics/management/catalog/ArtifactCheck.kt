package io.github.andrealtb.lockscreenlyrics.management.catalog

import java.io.InputStream
import java.security.MessageDigest

internal enum class ArtifactMismatch { TOO_LARGE, SIZE, SHA256, APPLICATION_ID, VERSION_CODE, NO_SIGNER, SIGNER }

/** Size and digest of a downloaded file, measured by [ArtifactCheck.measure]. */
internal data class MeasuredContent(val bytes: Long, val sha256: String)

/** Package facts read from the downloaded archive (Android adapter in M3). */
internal data class ObservedPackage(
    val applicationId: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

/**
 * Plan §6.2: a downloaded APK is installable only when its bytes and its package identity both
 * match a verified catalog entry. HTTPS and the host are never part of that decision.
 */
internal object ArtifactCheck {
    /** Streams [input] once; stops and returns null as soon as it exceeds [limit] bytes. */
    fun measure(input: InputStream, limit: Long): MeasuredContent? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            digest.update(buffer, 0, read)
        }
        return MeasuredContent(total, digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun checkContent(artifact: CatalogArtifact, measured: MeasuredContent?): ArtifactMismatch? = when {
        measured == null -> ArtifactMismatch.TOO_LARGE
        measured.bytes != artifact.bytes -> ArtifactMismatch.SIZE
        measured.sha256 != artifact.sha256 -> ArtifactMismatch.SHA256
        else -> null
    }

    /** Every current signer must be one the entry (and so the pinned trust) allows. */
    fun checkPackage(entry: CatalogEntry, observed: ObservedPackage): ArtifactMismatch? = when {
        observed.applicationId != entry.applicationId -> ArtifactMismatch.APPLICATION_ID
        observed.versionCode != entry.versionCode -> ArtifactMismatch.VERSION_CODE
        observed.signerSha256.isEmpty() -> ArtifactMismatch.NO_SIGNER
        !entry.artifact.signerSha256.containsAll(observed.signerSha256) -> ArtifactMismatch.SIGNER
        else -> null
    }
}
