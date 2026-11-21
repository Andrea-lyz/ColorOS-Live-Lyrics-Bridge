package io.github.andrealtb.lockscreenlyrics.management.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import io.github.andrealtb.lockscreenlyrics.management.InstalledPackages
import io.github.andrealtb.lockscreenlyrics.management.StoreRuntime
import io.github.andrealtb.lockscreenlyrics.management.catalog.ArtifactCheck
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogEntry
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogState
import io.github.andrealtb.lockscreenlyrics.management.catalog.ObservedPackage
import io.github.andrealtb.lockscreenlyrics.management.net.UrlConnectionHttpsTransport
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Download → verify → PackageInstaller session for one plugin at a time (plan §6.4). Lives for
 * the App process, so an Activity recreation never restarts or duplicates an operation. The
 * open session is persisted ([InstallRecordFile]); after a process restart [reconcile] decides
 * from PackageManager and the session list instead of committing a second session.
 * Ordinary App process only; never silent: the system installer always asks the user.
 */
internal object StoreOperations {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _operations = MutableStateFlow<Map<String, Operation>>(emptyMap())
    val operations: StateFlow<Map<String, Operation>> = _operations.asStateFlow()

    /** A system confirmation the visible Activity should launch once, then clear. */
    private val _confirmation = MutableStateFlow<Intent?>(null)
    val confirmation: StateFlow<Intent?> = _confirmation.asStateFlow()

    /**
     * Identity-verified downloads still in the private cache, by provider. The store offers to
     * hand them to another app (system installer, file manager) when the PackageInstaller
     * confirmation never appears on some ROMs. Only verified files are ever listed here.
     */
    private val _shareable = MutableStateFlow<Map<String, File>>(emptyMap())
    val shareable: StateFlow<Map<String, File>> = _shareable.asStateFlow()

    /** Kept so the user can reopen the confirmation after backing out of it. */
    private var lastConfirmation: Intent? = null
    @Volatile private var cancelDownload = false
    /** Verified download waiting for "install unknown apps". */
    private var awaitingPermission: Pair<CatalogEntry, File>? = null
    /** Entry of the open session, for verifying the result without reloading the catalog. */
    private var installing: CatalogEntry? = null

    val busy: Boolean get() = _operations.value.values.any { it.active }

    /** Returns false (and does nothing) while another operation is running. */
    fun start(context: Context, entry: CatalogEntry, trustedHosts: Set<String>): Boolean {
        val app = context.applicationContext
        synchronized(lock) {
            if (busy) return false
            cancelDownload = false
            awaitingPermission = null
            // A new download may clean the cache, so no earlier file stays offered.
            _shareable.value = emptyMap()
            set(entry.providerId, Operation.Downloading(0, entry.artifact.bytes))
        }
        scope.launch { guarded(entry.providerId) { downloadAndInstall(app, entry, trustedHosts) } }
        return true
    }

    fun cancel(context: Context, providerId: String) {
        when (_operations.value[providerId]) {
            is Operation.Downloading -> cancelDownload = true
            Operation.WaitingForUser -> {
                val record = records(context).read() ?: return
                try {
                    context.packageManager.packageInstaller.abandonSession(record.sessionId)
                } catch (_: SecurityException) {
                    // Already gone; the status receiver or reconcile reports it.
                }
                finish(context, record, Operation.Failed(OperationFailure.CANCELLED))
            }
            else -> Unit
        }
    }

    fun confirmationLaunched() {
        _confirmation.value = null
    }

    /** Reopens the system confirmation for the session that is still waiting, if any. */
    fun reopenConfirmation() {
        lastConfirmation?.let { _confirmation.value = it }
    }

    /** Called on resume: continues after the user allowed installs; never asks again by itself. */
    fun continueIfAllowed(context: Context) {
        val app = context.applicationContext
        val pending = synchronized(lock) {
            val waiting = awaitingPermission ?: return
            if (!canInstall(app)) return
            awaitingPermission = null
            set(waiting.first.providerId, Operation.Installing)
            waiting
        }
        scope.launch { guarded(pending.first.providerId) { install(app, pending.first, pending.second) } }
    }

    /**
     * Called on resume. An installer session left by a previous process cannot be confirmed any
     * more (its confirmation intent is gone): it is finished from PackageManager's view, or
     * abandoned so the user can retry. Sessions this process is waiting on are left alone.
     */
    fun reconcile(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val record = records(app).read() ?: return@launch
            val current = _operations.value[record.providerId]
            if (current?.active == true) {
                // The user may have installed the shared package with another app meanwhile.
                if (current == Operation.WaitingForUser || current == Operation.Installing) {
                    guarded(record.providerId) { finishIfInstalledElsewhere(app, record) }
                }
                return@launch
            }
            guarded(record.providerId) { reconcileOrphan(app, record) }
        }
    }

    private fun reconcileOrphan(app: Context, record: InstallRecord) {
        val installer = app.packageManager.packageInstaller
        val sessionExists = installer.getSessionInfo(record.sessionId) != null
        val entry = installing?.takeIf { it.providerId == record.providerId } ?: entryFor(app, record.providerId)
        val installed = InstalledPackages.readPlugin(app.packageManager, record.applicationId)
        when (reconcile(record, entry, installed, sessionExists)) {
            ReconcileVerdict.INSTALLED -> finish(app, record, verifyInstalled(entry!!, installed))
            ReconcileVerdict.SESSION_OPEN -> {
                try {
                    installer.abandonSession(record.sessionId)
                } catch (_: SecurityException) {
                }
                finish(app, record, Operation.Failed(OperationFailure.INTERRUPTED))
            }
            ReconcileVerdict.LOST -> finish(app, record, Operation.Failed(OperationFailure.INTERRUPTED))
        }
    }

    /** Ends a still-open session once PackageManager already shows the expected package. */
    private fun finishIfInstalledElsewhere(app: Context, record: InstallRecord) {
        val entry = installing?.takeIf { it.providerId == record.providerId } ?: entryFor(app, record.providerId)
        val installed = InstalledPackages.readPlugin(app.packageManager, record.applicationId)
        if (reconcile(record, entry, installed, sessionExists = true) != ReconcileVerdict.INSTALLED) return
        try {
            app.packageManager.packageInstaller.abandonSession(record.sessionId)
        } catch (_: SecurityException) {
        }
        finish(app, record, verifyInstalled(entry!!, installed))
    }

    /** From [InstallStatusReceiver]; statuses for sessions we no longer track are ignored. */
    fun onStatus(context: Context, sessionId: Int, status: Int, confirm: Intent?) {
        val app = context.applicationContext
        val record = records(app).read()?.takeIf { it.sessionId == sessionId } ?: return
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirm == null) {
                    finish(app, record, Operation.Failed(OperationFailure.OTHER))
                    return
                }
                lastConfirmation = confirm
                set(record.providerId, Operation.WaitingForUser)
                _confirmation.value = confirm
            }
            PackageInstaller.STATUS_SUCCESS -> scope.launch { guarded(record.providerId) {
                val entry = installing?.takeIf { it.providerId == record.providerId } ?: entryFor(app, record.providerId)
                val installed = InstalledPackages.readPlugin(app.packageManager, record.applicationId)
                val result = entry?.takeIf { it.versionCode == record.versionCode }?.let { verifyInstalled(it, installed) }
                    ?: Operation.Failed(OperationFailure.NOT_VERIFIED)
                finish(app, record, result)
            } }
            else -> finish(app, record, Operation.Failed(installerFailure(status)))
        }
    }

    private fun downloadAndInstall(app: Context, entry: CatalogEntry, hosts: Set<String>) {
        val downloader = ApkDownloader(UrlConnectionHttpsTransport, cacheDir(app))
        var lastReport = 0L
        val result = downloader.download(entry, hosts, { cancelDownload }) { done, total ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                lastReport = now
                set(entry.providerId, Operation.Downloading(done, total))
            }
        }
        result.failure()?.let {
            set(entry.providerId, Operation.Failed(it))
            return
        }
        val file = (result as DownloadResult.Verified).file
        set(entry.providerId, Operation.Verifying)
        val observed = ArchiveInspector.read(app.packageManager, file)
        val identity = if (observed == null) OperationFailure.IDENTITY_MISMATCH
        else archiveFailure(ArtifactCheck.checkPackage(entry, observed))
        if (identity != null) {
            file.delete()
            set(entry.providerId, Operation.Failed(identity))
            return
        }
        _shareable.value = mapOf(entry.providerId to file)
        synchronized(lock) {
            if (!canInstall(app)) {
                awaitingPermission = entry to file
                set(entry.providerId, Operation.NeedsInstallPermission)
                return
            }
            set(entry.providerId, Operation.Installing)
        }
        install(app, entry, file)
    }

    private fun install(app: Context, entry: CatalogEntry, file: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(entry.applicationId)
            setSize(file.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Plan §6.4: always keep the user's confirmation, even where updates could skip it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        var sessionId = -1
        try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                installing = entry
                records(app).write(InstallRecord(entry.providerId, entry.applicationId, entry.versionCode, sessionId))
                session.commit(statusSender(app, sessionId))
            }
        } catch (e: Exception) {
            if (e !is IOException && e !is SecurityException && e !is RuntimeException) throw e
            if (sessionId >= 0) {
                try {
                    installer.abandonSession(sessionId)
                } catch (_: SecurityException) {
                }
            }
            records(app).clear()
            installing = null
            set(entry.providerId, Operation.Failed(if (e is IOException) OperationFailure.STORAGE else OperationFailure.BLOCKED))
        }
    }

    private fun finish(app: Context, record: InstallRecord, result: Operation) {
        synchronized(lock) {
            records(app).clear()
            installing = null
            lastConfirmation = null
            _confirmation.value = null
            if (result is Operation.Succeeded) _shareable.value = emptyMap()
            set(record.providerId, result)
        }
        // Keep a verified file after a cancel so a retry does not download again.
        if (result is Operation.Succeeded) ApkDownloader(UrlConnectionHttpsTransport, cacheDir(app)).clean(keep = null)
    }

    /** An unexpected platform exception ends the operation visibly instead of crashing the App. */
    private inline fun guarded(providerId: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            set(providerId, Operation.Failed(OperationFailure.OTHER))
        }
    }

    private fun set(providerId: String, operation: Operation) {
        _operations.update { it + (providerId to operation) }
    }

    private fun canInstall(app: Context): Boolean = app.packageManager.canRequestPackageInstalls()

    private fun entryFor(app: Context, providerId: String): CatalogEntry? =
        (StoreRuntime.load(app) as? CatalogState.Current)?.catalog?.entries?.firstOrNull { it.providerId == providerId }

    private fun cacheDir(app: Context) = File(app.cacheDir, "provider-store")

    private fun records(context: Context) =
        InstallRecordFile(File(File(context.noBackupFilesDir, "store"), "install-session.json"))

    private fun statusSender(app: Context, sessionId: Int) = PendingIntent.getBroadcast(
        app,
        sessionId,
        Intent(app, InstallStatusReceiver::class.java).setPackage(app.packageName),
        // Mutable: the installer adds the status extras. The intent is explicit.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
    ).intentSender

    private const val PROGRESS_INTERVAL_MS = 200L
}

/** Not exported: only the platform installer, via our own PendingIntent, reaches it. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val confirm = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        } else {
            null
        }
        StoreOperations.onStatus(context, sessionId, status, confirm)
    }
}

/** Package facts of a downloaded archive; null when it cannot be parsed. */
internal object ArchiveInspector {
    @Suppress("DEPRECATION")
    fun read(pm: PackageManager, file: File): ObservedPackage? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val info: PackageInfo = try {
            pm.getPackageArchiveInfo(file.path, flags)
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return ObservedPackage(info.packageName, versionCode, InstalledPackages.currentSigners(info))
    }
}
