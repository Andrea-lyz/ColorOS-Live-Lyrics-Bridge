package io.github.andrealtb.lockscreenlyrics.management.install

import android.content.pm.PackageInstaller
import io.github.andrealtb.lockscreenlyrics.management.InstalledPackage
import io.github.andrealtb.lockscreenlyrics.management.catalog.ArtifactMismatch
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogEntry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONException
import org.json.JSONObject

/** Why a store operation stopped; each has its own user-facing explanation (plan §6.4). */
internal enum class OperationFailure {
    NETWORK,
    /** Downloaded bytes differ from the signed catalog entry. */
    CONTENT_MISMATCH,
    /** The archive's package name, versionCode or signer differs from the entry. */
    IDENTITY_MISMATCH,
    STORAGE,
    /** The user cancelled the system installer. */
    CANCELLED,
    /** An installed package with a different signer, or a downgrade. */
    CONFLICT,
    INCOMPATIBLE,
    /** Blocked by the system or a policy (ColorOS security checks, device admin). */
    BLOCKED,
    INVALID,
    /** The app was closed or restarted while the installer session was open. */
    INTERRUPTED,
    /** The installer reported success but PackageManager does not show the expected package. */
    NOT_VERIFIED,
    OTHER,
}

/** One plugin's store operation; at most one runs at a time (see StoreOperations). */
internal sealed interface Operation {
    val active: Boolean get() = false

    data class Downloading(val downloaded: Long, val total: Long) : Operation {
        override val active get() = true
    }

    data object Verifying : Operation {
        override val active get() = true
    }

    /** Verified and waiting for "install unknown apps"; never re-requested in a loop. */
    data object NeedsInstallPermission : Operation

    data object Installing : Operation {
        override val active get() = true
    }

    /** The system installer asked the user to confirm; this is neither success nor failure. */
    data object WaitingForUser : Operation {
        override val active get() = true
    }

    data class Succeeded(val versionName: String) : Operation

    data class Failed(val failure: OperationFailure) : Operation
}

internal fun DownloadResult.failure(): OperationFailure? = when (this) {
    is DownloadResult.Verified -> null
    DownloadResult.NetworkFailed -> OperationFailure.NETWORK
    is DownloadResult.Mismatch -> OperationFailure.CONTENT_MISMATCH
    DownloadResult.StorageFailed -> OperationFailure.STORAGE
    DownloadResult.Cancelled -> OperationFailure.CANCELLED
}

/** A terminal installer status; [PackageInstaller.STATUS_PENDING_USER_ACTION] is not terminal. */
internal fun installerFailure(status: Int): OperationFailure = when (status) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> OperationFailure.CANCELLED
    PackageInstaller.STATUS_FAILURE_CONFLICT -> OperationFailure.CONFLICT
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> OperationFailure.INCOMPATIBLE
    PackageInstaller.STATUS_FAILURE_STORAGE -> OperationFailure.STORAGE
    PackageInstaller.STATUS_FAILURE_BLOCKED -> OperationFailure.BLOCKED
    PackageInstaller.STATUS_FAILURE_INVALID -> OperationFailure.INVALID
    else -> OperationFailure.OTHER
}

/**
 * Plan §6.4: success is only what PackageManager shows afterwards, the exact versionCode with a
 * signer the entry allows. The installer's STATUS_SUCCESS alone is not enough.
 */
internal fun verifyInstalled(entry: CatalogEntry, installed: InstalledPackage?): Operation = when {
    installed == null || installed.versionCode != entry.versionCode || installed.signerSha256.isEmpty() ||
        !entry.artifact.signerSha256.containsAll(installed.signerSha256) -> Operation.Failed(OperationFailure.NOT_VERIFIED)
    else -> Operation.Succeeded(installed.versionName)
}

/** Identity check of the downloaded archive before it is handed to the installer. */
internal fun archiveFailure(mismatch: ArtifactMismatch?): OperationFailure? =
    mismatch?.let { OperationFailure.IDENTITY_MISMATCH }

/** The installer session that is open right now, persisted so a restart can reconcile it. */
internal data class InstallRecord(
    val providerId: String,
    val applicationId: String,
    val versionCode: Long,
    val sessionId: Int,
)

internal enum class ReconcileVerdict {
    /** The expected package is installed: finish as success (the callback was missed). */
    INSTALLED,
    /** The session still exists; without its confirmation intent it must be abandoned. */
    SESSION_OPEN,
    /** No session and not installed: the attempt ended without a status we saw. */
    LOST,
}

/** Decides what an orphaned record means after the process restarted (plan §6.4 reentry). */
internal fun reconcile(record: InstallRecord, entry: CatalogEntry?, installed: InstalledPackage?, sessionExists: Boolean): ReconcileVerdict {
    val matches = entry != null && entry.versionCode == record.versionCode &&
        verifyInstalled(entry, installed) is Operation.Succeeded
    return when {
        matches -> ReconcileVerdict.INSTALLED
        sessionExists -> ReconcileVerdict.SESSION_OPEN
        else -> ReconcileVerdict.LOST
    }
}

/** Single-record file next to the catalog state, in no-backup storage. */
internal class InstallRecordFile(private val file: File) {
    fun read(): InstallRecord? = try {
        val json = JSONObject(file.readText())
        InstallRecord(
            json.getString("providerId"),
            json.getString("applicationId"),
            json.getLong("versionCode"),
            json.getInt("sessionId"),
        )
    } catch (_: IOException) {
        null
    } catch (_: JSONException) {
        null
    }

    @Throws(IOException::class)
    fun write(record: InstallRecord) {
        file.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("cannot create $it") }
        val temp = File(file.path + ".tmp")
        temp.writeText(
            JSONObject()
                .put("providerId", record.providerId)
                .put("applicationId", record.applicationId)
                .put("versionCode", record.versionCode)
                .put("sessionId", record.sessionId)
                .toString(),
        )
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun clear() {
        file.delete()
    }
}
