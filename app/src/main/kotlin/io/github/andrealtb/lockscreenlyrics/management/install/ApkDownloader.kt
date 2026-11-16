package io.github.andrealtb.lockscreenlyrics.management.install

import io.github.andrealtb.lockscreenlyrics.management.catalog.ArtifactCheck
import io.github.andrealtb.lockscreenlyrics.management.catalog.ArtifactMismatch
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogEntry
import io.github.andrealtb.lockscreenlyrics.management.catalog.MeasuredContent
import io.github.andrealtb.lockscreenlyrics.management.net.HttpsTransport
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

internal sealed interface DownloadResult {
    /** Bytes match the catalog entry's size and SHA-256; identity is checked separately. */
    data class Verified(val file: File) : DownloadResult

    /** No mirror delivered the bytes (network, HTTP status, untrusted redirect). */
    data object NetworkFailed : DownloadResult

    /** A mirror delivered different bytes; nothing is kept. */
    data class Mismatch(val reason: ArtifactMismatch) : DownloadResult

    /** The private cache could not be written (usually no space). */
    data object StorageFailed : DownloadResult

    data object Cancelled : DownloadResult
}

/**
 * Downloads a catalog artifact into the App's private [directory] (plan §6.4). Bytes go to a
 * `.part` file and become `<providerId>-<versionCode>.apk` only after size and SHA-256 match the
 * signed entry, so a partial or foreign file can never reach the installer. Mirrors are tried in
 * catalog order; every hop must stay within the catalog's trusted hosts. Blocking.
 */
internal class ApkDownloader(
    private val transport: HttpsTransport,
    private val directory: File,
) {
    fun interface Progress {
        fun onProgress(downloaded: Long, total: Long)
    }

    fun download(
        entry: CatalogEntry,
        trustedHosts: Set<String>,
        isCancelled: () -> Boolean = { false },
        progress: Progress = Progress { _, _ -> },
    ): DownloadResult {
        val artifact = entry.artifact
        val target = File(directory, fileName(entry))
        // A verified file from an earlier attempt (e.g. the user cancelled the installer).
        val cached = if (target.isFile) measure(target) else null
        if (cached != null && ArtifactCheck.checkContent(artifact, cached) == null) {
            return DownloadResult.Verified(target)
        }
        if (!directory.isDirectory && !directory.mkdirs()) return DownloadResult.StorageFailed
        clean(keep = null)
        val part = File(directory, fileName(entry) + ".part")
        var mismatch: ArtifactMismatch? = null
        for (url in artifact.urls) {
            if (isCancelled()) return cancelled(part)
            val measured = try {
                fetch(url, trustedHosts, part, artifact.bytes, isCancelled, progress)
            } catch (_: CancelledException) {
                return cancelled(part)
            } catch (_: StorageException) {
                part.delete()
                return DownloadResult.StorageFailed
            } catch (_: IOException) {
                part.delete()
                continue
            }
            val problem = ArtifactCheck.checkContent(artifact, measured)
            if (problem != null) {
                // A mirror served other bytes; try the next one, report if none is right.
                mismatch = mismatch ?: problem
                part.delete()
                continue
            }
            if (!part.renameTo(target)) {
                part.delete()
                return DownloadResult.StorageFailed
            }
            return DownloadResult.Verified(target)
        }
        return mismatch?.let { DownloadResult.Mismatch(it) } ?: DownloadResult.NetworkFailed
    }

    /** Deletes every cached download except [keep] (after an install, or before a new one). */
    fun clean(keep: File?) {
        directory.listFiles()?.forEach { if (it != keep) it.delete() }
    }

    /** Returns null when the body exceeds [expected] bytes (stopped as soon as it does). */
    private fun fetch(
        url: String,
        hosts: Set<String>,
        part: File,
        expected: Long,
        isCancelled: () -> Boolean,
        progress: Progress,
    ): MeasuredContent? = transport.get(url, hosts).use { body ->
        if (body.contentLength > expected) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        val out = try {
            FileOutputStream(part)
        } catch (e: IOException) {
            throw StorageException(e)
        }
        out.use {
            while (true) {
                if (isCancelled()) throw CancelledException()
                val read = body.stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > expected) return null
                digest.update(buffer, 0, read)
                try {
                    out.write(buffer, 0, read)
                } catch (e: IOException) {
                    throw StorageException(e)
                }
                progress.onProgress(total, expected)
            }
            try {
                out.fd.sync()
            } catch (e: IOException) {
                throw StorageException(e)
            }
        }
        MeasuredContent(total, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun measure(file: File): MeasuredContent? = try {
        file.inputStream().use { ArtifactCheck.measure(it, file.length()) }
    } catch (_: IOException) {
        null
    }

    private fun cancelled(part: File): DownloadResult {
        part.delete()
        return DownloadResult.Cancelled
    }

    private class CancelledException : IOException()
    private class StorageException(cause: IOException) : IOException(cause)

    companion object {
        fun fileName(entry: CatalogEntry) = "${entry.providerId}-${entry.versionCode}.apk"
    }
}
