package io.github.andrealtb.lockscreenlyrics.management.catalog

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtifactCheckTest {
    private val signer = CatalogTrust.RELEASE_CERTIFICATE_SHA256
    private val content = "fake apk bytes".toByteArray()
    private val measured = ArtifactCheck.measure(ByteArrayInputStream(content), 1024)!!

    private val entry = CatalogEntry(
        providerId = "player-salt",
        kind = CatalogKind.DEDICATED_LYRICS,
        applicationId = "io.example.salt",
        versionName = "1.1.2",
        versionCode = 8,
        minSdk = 27,
        minStoreProtocol = 1,
        sourceCommit = "215b0c9e8add8e2886f4913171e989cc6f9e7f73",
        releaseNotes = null,
        artifact = CatalogArtifact(listOf("https://github.com/x.apk"), content.size.toLong(), measured.sha256, setOf(signer)),
    )

    @Test fun measureReportsSizeAndDigest() {
        assertEquals(content.size.toLong(), measured.bytes)
        assertEquals(CatalogVerifier.sha256Hex(content), measured.sha256)
    }

    @Test fun measureStopsPastTheLimit() {
        assertNull(ArtifactCheck.measure(ByteArrayInputStream(ByteArray(2048)), 1024))
        assertEquals(ArtifactMismatch.TOO_LARGE, ArtifactCheck.checkContent(entry.artifact, null))
    }

    @Test fun contentMustMatchSizeAndDigest() {
        assertNull(ArtifactCheck.checkContent(entry.artifact, measured))
        assertEquals(ArtifactMismatch.SIZE, ArtifactCheck.checkContent(entry.artifact, measured.copy(bytes = 1)))
        assertEquals(ArtifactMismatch.SHA256, ArtifactCheck.checkContent(entry.artifact, measured.copy(sha256 = "1".repeat(64))))
    }

    @Test fun packageIdentityMustMatchTheEntry() {
        val good = ObservedPackage("io.example.salt", 8, setOf(signer))
        assertNull(ArtifactCheck.checkPackage(entry, good))
        assertEquals(ArtifactMismatch.APPLICATION_ID, ArtifactCheck.checkPackage(entry, good.copy(applicationId = "io.example.other")))
        assertEquals(ArtifactMismatch.VERSION_CODE, ArtifactCheck.checkPackage(entry, good.copy(versionCode = 7)))
        assertEquals(ArtifactMismatch.NO_SIGNER, ArtifactCheck.checkPackage(entry, good.copy(signerSha256 = emptySet())))
        assertEquals(ArtifactMismatch.SIGNER, ArtifactCheck.checkPackage(entry, good.copy(signerSha256 = setOf("ab".repeat(32)))))
        assertEquals(ArtifactMismatch.SIGNER, ArtifactCheck.checkPackage(entry, good.copy(signerSha256 = setOf(signer, "ab".repeat(32)))))
    }
}
