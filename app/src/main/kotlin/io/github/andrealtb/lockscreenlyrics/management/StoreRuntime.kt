package io.github.andrealtb.lockscreenlyrics.management

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import io.github.andrealtb.lockscreenlyrics.management.catalog.BundledCatalogSnapshot
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogRefresher
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogState
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogStore
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogTrust
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogVerifier
import io.github.andrealtb.lockscreenlyrics.management.catalog.FileCatalogPersistence
import io.github.andrealtb.lockscreenlyrics.management.catalog.RefreshResult
import io.github.andrealtb.lockscreenlyrics.management.net.UrlConnectionHttpsTransport

/**
 * Process-wide owner of the catalog store (ordinary App process only). All store access is
 * serialized so two screens can never race on the revision floor. Blocking: call off the main
 * thread.
 */
internal object StoreRuntime {
    /** Opening the store re-checks the network at most this often (plan §6.5). */
    private const val AUTO_REFRESH_INTERVAL_MS = 10 * 60 * 1000L

    private val lock = Any()
    private var store: CatalogStore? = null
    private var lastRemoteCheckElapsed = Long.MIN_VALUE

    private fun store(context: Context): CatalogStore = store ?: run {
        val app = context.applicationContext
        CatalogStore(
            CatalogVerifier(CatalogTrust.production()),
            FileCatalogPersistence.forApp(app),
        ) { BundledCatalogSnapshot.read(app) }.also { store = it }
    }

    fun load(context: Context): CatalogState = synchronized(lock) { store(context).load(nowEpochSeconds()) }

    fun autoRefreshDue(): Boolean = synchronized(lock) {
        lastRemoteCheckElapsed == Long.MIN_VALUE ||
            SystemClock.elapsedRealtime() - lastRemoteCheckElapsed >= AUTO_REFRESH_INTERVAL_MS
    }

    /** Fetches the mirrors, then returns the state the store now holds. */
    fun refresh(context: Context): Pair<RefreshResult, CatalogState> = synchronized(lock) {
        val store = store(context)
        val result = CatalogRefresher(store, UrlConnectionHttpsTransport).refresh(nowEpochSeconds())
        lastRemoteCheckElapsed = SystemClock.elapsedRealtime()
        result to store.load(nowEpochSeconds())
    }

    private fun nowEpochSeconds() = System.currentTimeMillis() / 1000
}

/** PackageManager facts for the directory, limited by the manifest's directed <queries>. */
internal object InstalledPackages {
    /** Plugins with their current signers; player hosts by presence only. */
    fun snapshot(pm: PackageManager, entries: List<DirectoryEntry>): Map<String, InstalledPackage> = buildMap {
        for (entry in entries) {
            read(pm, entry.applicationId, withSigners = true)?.let { put(entry.applicationId, it) }
            for (host in entry.hostPackages) {
                if (host !in this) read(pm, host, withSigners = false)?.let { put(host, it) }
            }
        }
    }

    /** One plugin with its current signers, or null when it is not installed or not visible. */
    fun readPlugin(pm: PackageManager, packageName: String): InstalledPackage? = read(pm, packageName, withSigners = true)

    @Suppress("DEPRECATION")
    private fun read(pm: PackageManager, packageName: String, withSigners: Boolean): InstalledPackage? {
        val flags = when {
            !withSigners -> 0
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> PackageManager.GET_SIGNING_CERTIFICATES
            else -> PackageManager.GET_SIGNATURES
        }
        val info = try {
            pm.getPackageInfo(packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        } catch (_: RuntimeException) {
            return null
        }
        return InstalledPackage(
            versionName = info.versionName ?: "",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong(),
            signerSha256 = if (withSigners) currentSigners(info) else emptySet(),
        )
    }

    /** Current signers only (no rotation history), compared against the catalog's allowed set. */
    @Suppress("DEPRECATION")
    fun currentSigners(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { CatalogVerifier.sha256Hex(it.toByteArray()) }.toSet()
    }
}
