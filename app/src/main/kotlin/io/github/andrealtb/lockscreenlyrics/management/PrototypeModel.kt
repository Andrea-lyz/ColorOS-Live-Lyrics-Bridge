package io.github.andrealtb.lockscreenlyrics.management

/**
 * M0 prototype UI state (plan §8.4 U-01). Live, read-only: the home status card (reply from the
 * Bridge inside SystemUI, see [homeStatus]) and the store's installed state (PackageManager, see
 * [storeItems]). Still SAMPLE: the other home cards. There is no catalog yet, so nothing can be
 * downloaded or updated, and no action installs, updates or requests a scope.
 */
internal enum class ManagementTab { HOME, STORE, APPEARANCE, STATUS }

internal enum class ProviderKind { DEDICATED, UNIVERSAL, ARTWORK }

/** UPDATE_AVAILABLE and SIGNATURE_MISMATCH need the catalog and are not produced yet. */
internal enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE, SIGNATURE_MISMATCH }

internal enum class StoreFilter { SUITABLE, ALL, INSTALLED, UPDATES }

/** How the store may open an installed plugin; it never changes the plugin's own settings. */
internal enum class ProviderEntry {
    /** Exported MAIN/INFO page that holds the module's own debug switch. */
    DEBUG_PAGE,
    /** Exported MAIN/INFO page without a debug switch (Readify). */
    INFO_PAGE,
    /** Independent app with its own launcher entry (Universal, Dynamic Artwork). */
    LAUNCHER,
}

/** The one primary action a card offers; failures stay visible instead of hiding behind it. */
internal enum class StoreAction { DOWNLOAD, UPDATE, OPEN_DEBUG, OPEN_INFO, OPEN_APP, NONE }

internal data class StoreItem(
    val id: String,
    val name: String,
    val applicationId: String,
    val kind: ProviderKind,
    val entry: ProviderEntry,
    val installedVersion: String?,
    val installState: InstallState,
    /** At least one supported player package is installed (always false for non-dedicated). */
    val hostDetected: Boolean,
)

internal enum class Check { OK, ATTENTION, UNKNOWN }

/**
 * Whether the Bridge is running inside SystemUI. The App process cannot read the framework's
 * enable/scope state, so a missing reply is reported as "no response", never as "disabled".
 */
internal enum class ModuleStatus { CHECKING, RUNNING, NO_RESPONSE }

/** Which build SystemUI has actually loaded, compared with the installed APK. */
internal enum class SystemUiLoad { CHECKING, CURRENT, OUTDATED_NEEDS_RESTART, UNKNOWN }

/** What SystemUI reports about settings pushed since it started; not a per-change receipt. */
internal enum class ConfigObservation { CHECKING, APPLIED_SINCE_START, NOT_PUSHED_SINCE_START, UNKNOWN }

internal fun ModuleStatus.check(): Check = when (this) {
    ModuleStatus.RUNNING -> Check.OK
    ModuleStatus.NO_RESPONSE -> Check.ATTENTION
    ModuleStatus.CHECKING -> Check.UNKNOWN
}

internal fun SystemUiLoad.check(): Check = when (this) {
    SystemUiLoad.CURRENT -> Check.OK
    SystemUiLoad.OUTDATED_NEEDS_RESTART -> Check.ATTENTION
    SystemUiLoad.UNKNOWN, SystemUiLoad.CHECKING -> Check.UNKNOWN
}

internal fun ConfigObservation.check(): Check = when (this) {
    ConfigObservation.APPLIED_SINCE_START -> Check.OK
    // Normal after a restart: SystemUI read settings from storage and nothing was pushed since.
    ConfigObservation.NOT_PUSHED_SINCE_START, ConfigObservation.UNKNOWN,
    ConfigObservation.CHECKING -> Check.UNKNOWN
}

/** Result of one status request to SystemUI. */
internal sealed interface StatusQuery {
    data object Pending : StatusQuery
    data object NoReply : StatusQuery
    data class Reply(val injectedBuildId: String?, val appliedUiRevision: Long) : StatusQuery
}

internal data class HomeStatus(
    val module: ModuleStatus,
    val systemUi: SystemUiLoad,
    val config: ConfigObservation,
)

internal fun homeStatus(installedBuildId: String?, query: StatusQuery): HomeStatus = when (query) {
    StatusQuery.Pending -> HomeStatus(ModuleStatus.CHECKING, SystemUiLoad.CHECKING, ConfigObservation.CHECKING)
    StatusQuery.NoReply -> HomeStatus(ModuleStatus.NO_RESPONSE, SystemUiLoad.UNKNOWN, ConfigObservation.UNKNOWN)
    is StatusQuery.Reply -> HomeStatus(
        module = ModuleStatus.RUNNING,
        systemUi = when {
            !isKnownBuildId(query.injectedBuildId) || !isKnownBuildId(installedBuildId) -> SystemUiLoad.UNKNOWN
            query.injectedBuildId == installedBuildId -> SystemUiLoad.CURRENT
            else -> SystemUiLoad.OUTDATED_NEEDS_RESTART
        },
        config = if (query.appliedUiRevision >= 0L) ConfigObservation.APPLIED_SINCE_START
        else ConfigObservation.NOT_PUSHED_SINCE_START,
    )
}

private fun isKnownBuildId(id: String?) = !id.isNullOrBlank() && id != "unknown"

/** Home cards below the live status card; still sample data. */
internal data class HomeState(
    val onboardingRemaining: Int,
    val featuresNeedingAttention: Int,
    val updatesAvailable: Int,
)

internal fun StoreItem.primaryAction(): StoreAction = when (installState) {
    InstallState.NOT_INSTALLED -> StoreAction.DOWNLOAD
    InstallState.UPDATE_AVAILABLE -> StoreAction.UPDATE
    // A different signer cannot be updated in place; never offer a one-tap data wipe.
    InstallState.SIGNATURE_MISMATCH -> StoreAction.NONE
    InstallState.INSTALLED -> openAction()
}

/** Opening an installed plugin's own page; also offered next to an update. */
internal fun StoreItem.openAction(): StoreAction = when (entry) {
    ProviderEntry.DEBUG_PAGE -> StoreAction.OPEN_DEBUG
    ProviderEntry.INFO_PAGE -> StoreAction.OPEN_INFO
    ProviderEntry.LAUNCHER -> StoreAction.OPEN_APP
}

internal fun List<StoreItem>.filteredBy(filter: StoreFilter): List<StoreItem> = when (filter) {
    StoreFilter.SUITABLE -> filter { it.hostDetected || it.kind != ProviderKind.DEDICATED }
    StoreFilter.ALL -> this
    StoreFilter.INSTALLED -> filter { it.installState != InstallState.NOT_INSTALLED }
    StoreFilter.UPDATES -> filter { it.installState == InstallState.UPDATE_AVAILABLE }
}

/**
 * Official Provider set. Mirrors ColorOS-Live-Lyrics-Providers/release/v5-provider-matrix.json;
 * ProviderDirectoryContractTest fails when the two differ (plan §6.1: no second business list).
 */
internal data class DirectoryEntry(
    val module: String,
    val name: String,
    val applicationId: String,
    val kind: ProviderKind,
    val entry: ProviderEntry,
    val hostPackages: List<String>,
)

internal object ProviderDirectory {
    private const val P = "io.github.andrealtb.coloroslyrics.provider."
    private fun dedicated(module: String, name: String, id: String, vararg hosts: String) =
        DirectoryEntry(module, name, P + id, ProviderKind.DEDICATED, ProviderEntry.DEBUG_PAGE, hosts.toList())

    val entries: List<DirectoryEntry> = listOf(
        dedicated("player-salt", "Salt", "salt", "com.salt.music"),
        dedicated("player-cone", "Cone", "cone", "ink.trantor.coneplayer", "ink.trantor.coneplayer.gp"),
        dedicated("kuwo-music", "KuWo", "kuwo", "cn.kuwo.player"),
        dedicated("player-lx", "LX", "lx", "cn.toside.music.mobile", "com.lxwalnut.music.mobile"),
        dedicated("player-poweramp", "Poweramp", "poweramp", "com.maxmpz.audioplayer"),
        dedicated("player-metrolist", "Metrolist", "metrolist", "com.metrolist.music"),
        dedicated("player-kugou", "KuGou", "kugou", "com.kugou.android", "com.kugou.android.lite"),
        dedicated("player-qq", "QQ", "qq", "com.tencent.qqmusic"),
        dedicated("player-netease", "NetEase", "netease", "com.netease.cloudmusic", "com.hihonor.cloudmusic"),
        dedicated("player-apple", "Apple", "apple", "com.apple.android.music"),
        dedicated("player-spotify", "Spotify", "spotify", "com.spotify.music"),
        dedicated("player-qishui", "QiShui", "qishui", "com.luna.music"),
        DirectoryEntry("player-readify", "Readify", P + "readify", ProviderKind.DEDICATED,
            ProviderEntry.INFO_PAGE, listOf("com.readin.app")),
        DirectoryEntry("universal-provider", "Universal", P + "universal", ProviderKind.UNIVERSAL,
            ProviderEntry.LAUNCHER, emptyList()),
        DirectoryEntry("artwork-provider-am", "Dynamic Artwork", "io.github.andrealtb.artwork.am",
            ProviderKind.ARTWORK, ProviderEntry.LAUNCHER, emptyList()),
    )
}

/** Projects the directory onto what this device reports; [versionOf] returns null when absent. */
internal fun storeItems(entries: List<DirectoryEntry>, versionOf: (String) -> String?): List<StoreItem> =
    entries.map { e ->
        val version = versionOf(e.applicationId)
        StoreItem(
            id = e.module,
            name = e.name,
            applicationId = e.applicationId,
            kind = e.kind,
            entry = e.entry,
            installedVersion = version,
            installState = if (version == null) InstallState.NOT_INSTALLED else InstallState.INSTALLED,
            hostDetected = e.kind == ProviderKind.DEDICATED && e.hostPackages.any { versionOf(it) != null },
        )
    }

internal object SampleData {
    val home = HomeState(
        onboardingRemaining = 2,
        featuresNeedingAttention = 1,
        updatesAvailable = 1,
    )
}
