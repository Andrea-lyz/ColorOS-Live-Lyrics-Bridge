package io.github.andrealtb.lockscreenlyrics.management.install

import android.content.pm.PackageInstaller
import io.github.andrealtb.lockscreenlyrics.management.InstalledPackage
import io.github.andrealtb.lockscreenlyrics.management.catalog.ArtifactMismatch
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogArtifact
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogEntry
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogKind
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogTrust
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogVerifier
import io.github.andrealtb.lockscreenlyrics.management.net.HttpsBody
import io.github.andrealtb.lockscreenlyrics.management.net.HttpsTransport
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallFlowTest {
    private val official = CatalogTrust.RELEASE_CERTIFICATE_SHA256
    private val apk = ByteArray(5000) { (it * 31).toByte() }
    private val dir: File = Files.createTempDirectory("store-download").toFile()

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun entry(
        bytes: ByteArray = apk,
        urls: List<String> = listOf("https://a.example/x.apk", "https://b.example/x.apk"),
        versionCode: Long = 8,
    ) = CatalogEntry(
        "player-salt", CatalogKind.DEDICATED_LYRICS, "io.github.andrealtb.coloroslyrics.provider.salt", "1.1.2",
        versionCode, 27, 1, "0".repeat(39) + "1", null,
        CatalogArtifact(urls, bytes.size.toLong(), CatalogVerifier.sha256Hex(bytes), setOf(official)),
    )

    private class Fake(private val bodies: Map<String, ByteArray>) : HttpsTransport {
        val requested = mutableListOf<String>()
        override fun get(url: String, allowedHosts: Set<String>): HttpsBody {
            requested += url
            val bytes = bodies[url] ?: throw IOException("offline")
            return HttpsBody(ByteArrayInputStream(bytes), -1) {}
        }
    }

    private fun download(transport: HttpsTransport, entry: CatalogEntry = entry(), cancel: () -> Boolean = { false }) =
        ApkDownloader(transport, dir).download(entry, setOf("a.example", "b.example"), cancel)

    @Test fun verifiedFileGetsItsFinalNameOnlyAfterTheDigestMatches() {
        val result = download(Fake(mapOf("https://a.example/x.apk" to apk)))
        result as DownloadResult.Verified
        assertEquals("player-salt-8.apk", result.file.name)
        assertTrue(result.file.readBytes().contentEquals(apk))
        assertEquals(listOf("player-salt-8.apk"), dir.list()!!.toList())
    }

    @Test fun aBadMirrorFallsBackToTheNextAndNothingPartialIsLeft() {
        val tampered = apk.copyOf().also { it[10] = (it[10] + 1).toByte() }
        val transport = Fake(mapOf("https://a.example/x.apk" to tampered, "https://b.example/x.apk" to apk))
        assertTrue(download(transport) is DownloadResult.Verified)
        assertEquals(2, transport.requested.size)

        dir.listFiles()!!.forEach { it.delete() }
        val onlyBad = download(Fake(mapOf("https://a.example/x.apk" to tampered)))
        assertEquals(DownloadResult.Mismatch(ArtifactMismatch.SHA256), onlyBad)
        val tooLong = download(Fake(mapOf("https://a.example/x.apk" to apk + byteArrayOf(1))))
        assertEquals(DownloadResult.Mismatch(ArtifactMismatch.TOO_LARGE), tooLong)
        assertEquals(DownloadResult.NetworkFailed, download(Fake(emptyMap())))
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test fun aVerifiedCacheIsReusedAndAStaleOneIsReplaced() {
        assertTrue(download(Fake(mapOf("https://a.example/x.apk" to apk))) is DownloadResult.Verified)
        val offline = Fake(emptyMap())
        assertTrue(download(offline) is DownloadResult.Verified)
        assertTrue(offline.requested.isEmpty())
        // Same name, different expected bytes: the cached file is not trusted.
        val other = ByteArray(5000) { 7 }
        val result = download(Fake(mapOf("https://a.example/x.apk" to other)), entry(bytes = other))
        assertTrue(result is DownloadResult.Verified)
        assertTrue((result as DownloadResult.Verified).file.readBytes().contentEquals(other))
    }

    @Test fun cancellingStopsAndDeletesThePart() {
        var reads = 0
        val result = download(Fake(mapOf("https://a.example/x.apk" to apk)), cancel = { reads++ > 1 })
        assertEquals(DownloadResult.Cancelled, result)
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test fun installerStatusesMapToDistinctFailures() {
        assertEquals(OperationFailure.CANCELLED, installerFailure(PackageInstaller.STATUS_FAILURE_ABORTED))
        assertEquals(OperationFailure.CONFLICT, installerFailure(PackageInstaller.STATUS_FAILURE_CONFLICT))
        assertEquals(OperationFailure.INCOMPATIBLE, installerFailure(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE))
        assertEquals(OperationFailure.STORAGE, installerFailure(PackageInstaller.STATUS_FAILURE_STORAGE))
        assertEquals(OperationFailure.BLOCKED, installerFailure(PackageInstaller.STATUS_FAILURE_BLOCKED))
        assertEquals(OperationFailure.INVALID, installerFailure(PackageInstaller.STATUS_FAILURE_INVALID))
        assertEquals(OperationFailure.OTHER, installerFailure(PackageInstaller.STATUS_FAILURE))
        assertEquals(OperationFailure.IDENTITY_MISMATCH, archiveFailure(ArtifactMismatch.SIGNER))
        assertNull(archiveFailure(null))
    }

    @Test fun successMeansPackageManagerShowsTheExactVersionAndAnAllowedSigner() {
        val e = entry()
        assertEquals(Operation.Succeeded("1.1.2"), verifyInstalled(e, InstalledPackage("1.1.2", 8, setOf(official))))
        val notVerified = Operation.Failed(OperationFailure.NOT_VERIFIED)
        assertEquals(notVerified, verifyInstalled(e, null))
        assertEquals(notVerified, verifyInstalled(e, InstalledPackage("1.1.1", 7, setOf(official))))
        assertEquals(notVerified, verifyInstalled(e, InstalledPackage("1.1.2", 8, setOf("b".repeat(64)))))
        assertEquals(notVerified, verifyInstalled(e, InstalledPackage("1.1.2", 8, emptySet())))
    }

    @Test fun reconcileNeverCommitsASecondSession() {
        val e = entry()
        val record = InstallRecord(e.providerId, e.applicationId, 8, 42)
        val done = InstalledPackage("1.1.2", 8, setOf(official))
        val old = InstalledPackage("1.1.1", 7, setOf(official))
        assertEquals(ReconcileVerdict.INSTALLED, reconcile(record, e, done, sessionExists = true))
        assertEquals(ReconcileVerdict.SESSION_OPEN, reconcile(record, e, old, sessionExists = true))
        assertEquals(ReconcileVerdict.LOST, reconcile(record, e, old, sessionExists = false))
        // The catalog moved on: the record's version is what counts.
        assertEquals(ReconcileVerdict.LOST, reconcile(record, entry(versionCode = 9), done, sessionExists = false))
        assertEquals(ReconcileVerdict.LOST, reconcile(record, null, done, sessionExists = false))
    }

    @Test fun recordFileRoundTripsAndToleratesGarbage() {
        val file = InstallRecordFile(File(dir, "store/install-session.json"))
        assertNull(file.read())
        val record = InstallRecord("player-salt", "io.github.andrealtb.coloroslyrics.provider.salt", 8, 42)
        file.write(record)
        assertEquals(record, file.read())
        file.write(record.copy(sessionId = 43))
        assertEquals(43, file.read()!!.sessionId)
        File(dir, "store/install-session.json").writeText("{")
        assertNull(file.read())
        file.clear()
        assertFalse(File(dir, "store/install-session.json").exists())
    }

    @Test fun onlyInFlightOperationsBlockAnotherStart() {
        assertTrue(Operation.Downloading(0, 1).active)
        assertTrue(Operation.WaitingForUser.active)
        assertFalse(Operation.NeedsInstallPermission.active)
        assertFalse(Operation.Failed(OperationFailure.CANCELLED).active)
        assertEquals(OperationFailure.CANCELLED, DownloadResult.Cancelled.failure())
        assertNull(DownloadResult.Verified(File("x")).failure())
    }
}
