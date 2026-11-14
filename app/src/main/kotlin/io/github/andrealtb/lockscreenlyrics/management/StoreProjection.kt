package io.github.andrealtb.lockscreenlyrics.management

import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogEntry
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogState
import io.github.andrealtb.lockscreenlyrics.management.catalog.RefreshResult

/** What PackageManager reports for one installed package (current signers only, no lineage). */
internal data class InstalledPackage(
    val versionName: String,
    val versionCode: Long,
    /** SHA-256 of each current signing certificate; empty when they could not be read. */
    val signerSha256: Set<String>,
)

/** The store list plus catalog entries this Bridge build cannot show (plan §6.2). */
internal data class StoreProjection(
    val items: List<StoreItem>,
    /** Catalog entries for plugins outside this build's directory/package visibility. */
    val unrecognizedEntries: Int,
) {
    val updatesAvailable: Int get() = items.count { it.installState == InstallState.UPDATE_AVAILABLE }
}

/**
 * Projects the built-in directory onto this device and the verified catalog (plan §6.1/§6.5).
 * Ordering uses each app's own versionCode, never the Bridge version. A catalog entry only
 * counts when both its providerId and applicationId match the directory, so a catalog cannot
 * point a known plugin at another package. Pure: [installedOf] returns null when absent.
 */
internal fun storeItems(
    entries: List<DirectoryEntry>,
    catalog: CatalogState,
    deviceSdk: Int,
    enablementOf: (String) -> EnablementReport? = { null },
    installedOf: (String) -> InstalledPackage?,
): StoreProjection {
    val current = catalog as? CatalogState.Current
    val catalogEntries = current?.catalog?.entries.orEmpty()
    val installedHosts = entries.flatMap { it.hostPackages }.filter { installedOf(it) != null }.toSet()
    val items = entries.map { e ->
        val installed = installedOf(e.applicationId)
        val report = if (installed != null && e.reportsEnablement()) enablementOf(e.applicationId) else null
        val entry = catalogEntries.firstOrNull { it.providerId == e.module && it.applicationId == e.applicationId }
        StoreItem(
            id = e.module,
            name = e.name,
            nameRes = e.nameRes,
            iconRes = e.iconRes,
            enablement = report?.let { it.state.forCard(it, e, installedHosts) },
            applicationId = e.applicationId,
            kind = e.kind,
            entry = e.entry,
            installedVersion = installed?.versionName,
            installState = installState(installed, entry),
            hostDetected = e.kind == ProviderKind.DEDICATED && e.hostPackages.any { installedOf(it) != null },
            catalogVersion = entry?.versionName,
            availability = when {
                current == null -> Availability.NO_CATALOG
                entry == null -> Availability.NOT_IN_CATALOG
                !current.installable -> Availability.CATALOG_EXPIRED
                !entry.supportedBy() -> Availability.NEEDS_NEWER_BRIDGE
                entry.minSdk > deviceSdk -> Availability.UNSUPPORTED_SDK
                else -> Availability.AVAILABLE
            },
        )
    }
    val known = entries.map { it.module to it.applicationId }.toSet()
    return StoreProjection(items, catalogEntries.count { (it.providerId to it.applicationId) !in known })
}

private fun installState(installed: InstalledPackage?, entry: CatalogEntry?): InstallState = when {
    installed == null -> InstallState.NOT_INSTALLED
    entry == null -> InstallState.INSTALLED
    // Fail closed: unreadable signers are treated like foreign ones, so no update is offered.
    installed.signerSha256.isEmpty() || !entry.artifact.signerSha256.containsAll(installed.signerSha256) ->
        InstallState.SIGNATURE_MISMATCH
    installed.versionCode < entry.versionCode -> InstallState.UPDATE_AVAILABLE
    installed.versionCode > entry.versionCode -> InstallState.LOCAL_NEWER
    else -> InstallState.INSTALLED
}

/** Background reminder switch and whether its notifications can appear at all. */
internal data class RemindersView(val enabled: Boolean = false, val notificationsAllowed: Boolean = true)

/** What the store shows about its catalog; [state] is null until the first load finishes. */
internal data class CatalogView(
    val state: CatalogState? = null,
    val refreshing: Boolean = false,
    val lastRefresh: RefreshResult? = null,
)

internal enum class CatalogHeadline { LOADING, NONE, FRESH, EXPIRED }

internal fun CatalogView.headline(): CatalogHeadline = when (val s = state) {
    null -> CatalogHeadline.LOADING
    CatalogState.Unavailable -> CatalogHeadline.NONE
    is CatalogState.Current -> if (s.installable) CatalogHeadline.FRESH else CatalogHeadline.EXPIRED
}

/** The one note a card shows under its version line; warnings first (plan §8.3). */
internal enum class StoreNote {
    SIGNATURE_MISMATCH, LOCAL_NEWER, NOT_IN_CATALOG, NEEDS_NEWER_BRIDGE, UNSUPPORTED_SDK,
    HOST_MISSING, NO_DEBUG_SWITCH, INDEPENDENT,
}

internal fun StoreItem.note(): StoreNote? {
    val wantsCatalog = installState == InstallState.NOT_INSTALLED || installState == InstallState.UPDATE_AVAILABLE
    return when {
        installState == InstallState.SIGNATURE_MISMATCH -> StoreNote.SIGNATURE_MISMATCH
        installState == InstallState.LOCAL_NEWER -> StoreNote.LOCAL_NEWER
        // NO_CATALOG and CATALOG_EXPIRED are explained once, by the catalog card.
        wantsCatalog && availability == Availability.NOT_IN_CATALOG -> StoreNote.NOT_IN_CATALOG
        wantsCatalog && availability == Availability.NEEDS_NEWER_BRIDGE -> StoreNote.NEEDS_NEWER_BRIDGE
        wantsCatalog && availability == Availability.UNSUPPORTED_SDK -> StoreNote.UNSUPPORTED_SDK
        kind == ProviderKind.DEDICATED && !hostDetected -> StoreNote.HOST_MISSING
        installState == InstallState.NOT_INSTALLED -> null
        entry == ProviderEntry.INFO_PAGE -> StoreNote.NO_DEBUG_SWITCH
        kind != ProviderKind.DEDICATED -> StoreNote.INDEPENDENT
        else -> null
    }
}

internal val StoreNote.isWarning: Boolean get() = this == StoreNote.SIGNATURE_MISMATCH
