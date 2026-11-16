package io.github.andrealtb.lockscreenlyrics.management

import android.content.Context
import android.os.Bundle
import android.os.RemoteException

/**
 * Whether an installed Provider is enabled in the LSPosed manager, as the Provider's own App
 * process reports it (plan §14.9). Guidance only: it does not prove the hook runs in a player.
 */
internal enum class Enablement {
    CHECKING,
    ENABLED,
    /** Enabled, but no installed supported player is in its scope. */
    ENABLED_NO_SCOPE,
    NOT_ENABLED,
    /** The installed version declares no status endpoint (older Provider). */
    NO_ENDPOINT,
    /** The endpoint exists but could not be reached (e.g. ColorOS blocked starting the plugin). */
    UNREACHABLE,
    /** The Provider is signed differently from this Bridge and does not answer. */
    DIFFERENT_SIGNER,
    UNKNOWN,
}

/** Raw answer of one Provider; [scope] is only known when it is connected. */
internal data class EnablementReport(val state: Enablement, val scope: Set<String> = emptySet())

internal fun parseEnablement(protocol: Int, connected: Boolean?, scope: List<String>?): EnablementReport = when {
    protocol < 1 || connected == null -> EnablementReport(Enablement.UNKNOWN)
    connected -> EnablementReport(Enablement.ENABLED, scope.orEmpty().toSet())
    else -> EnablementReport(Enablement.NOT_ENABLED)
}

/**
 * The state a card shows. Player-specific Providers are flagged when none of this phone's
 * supported players is in scope; the universal Provider picks players in its own app.
 */
internal fun Enablement.forCard(report: EnablementReport, entry: DirectoryEntry, installedHosts: Set<String>): Enablement =
    if (this == Enablement.ENABLED && entry.kind == ProviderKind.DEDICATED &&
        entry.hostPackages.any { it in installedHosts } && entry.hostPackages.none { it in report.scope && it in installedHosts }
    ) Enablement.ENABLED_NO_SCOPE else this

/** Root state plus whether the LSPosed configuration could be read (null: not tried yet). */
internal data class RootView(
    val state: io.github.andrealtb.lockscreenlyrics.management.root.RootState =
        io.github.andrealtb.lockscreenlyrics.management.root.RootState.NOT_REQUESTED,
    val cliAvailable: Boolean? = null,
)

/** States where a shortcut into the LSPosed manager helps the user act. */
internal fun Enablement.offersLsposedShortcut(): Boolean =
    this == Enablement.NOT_ENABLED || this == Enablement.ENABLED_NO_SCOPE || this == Enablement.UNREACHABLE ||
        this == Enablement.UNKNOWN || this == Enablement.NO_ENDPOINT || this == Enablement.DIFFERENT_SIGNER

/** Which directory entries can report enablement (LSPosed modules; not the artwork app). */
internal fun DirectoryEntry.reportsEnablement(): Boolean = kind != ProviderKind.ARTWORK

/** Blocking query of one Provider's `<applicationId>.cll.module-status` endpoint. */
internal object ProviderEnablementQuery {
    private const val METHOD = "status"

    fun query(context: Context, applicationId: String): EnablementReport {
        val authority = "$applicationId.cll.module-status"
        // Declared in the installed APK? (visible through the manifest's directed <queries>)
        val declared = try {
            context.packageManager.resolveContentProvider(authority, 0) != null
        } catch (_: RuntimeException) {
            false
        }
        if (!declared) return EnablementReport(Enablement.NO_ENDPOINT)
        val client = try {
            context.contentResolver.acquireUnstableContentProviderClient(authority)
        } catch (_: SecurityException) {
            null
        } ?: return EnablementReport(Enablement.UNREACHABLE)
        return try {
            val reply: Bundle = client.call(METHOD, null, null) ?: return EnablementReport(Enablement.UNKNOWN)
            parseEnablement(
                reply.getInt("protocol", 0),
                if (reply.containsKey("connected")) reply.getBoolean("connected") else null,
                reply.getStringArrayList("scope"),
            )
        } catch (_: SecurityException) {
            EnablementReport(Enablement.DIFFERENT_SIGNER)
        } catch (_: RemoteException) {
            // The plugin process died during the call, typically killed or blocked by the system.
            EnablementReport(Enablement.UNREACHABLE)
        } catch (_: RuntimeException) {
            EnablementReport(Enablement.UNKNOWN)
        } finally {
            client.close()
        }
    }
}
