package io.github.andrealtb.lockscreenlyrics.management

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.andrealtb.lockscreenlyrics.R

/**
 * Management UI state (plan §8.4 U-01). Live, read-only: the home status card (reply from the
 * Bridge inside SystemUI, see [homeStatus]) and the store (PackageManager facts projected onto the
 * verified catalog, see [storeItems]). Still SAMPLE: the onboarding/feature home cards. No action
 * here requests a scope.
 */
internal enum class ManagementTab { HOME, STORE, APPEARANCE, STATUS }

internal enum class ProviderKind { DEDICATED, UNIVERSAL, ARTWORK }

/**
 * Installed state against the verified catalog (plan §6.5). Without a catalog entry an installed
 * plugin is only ever INSTALLED; the other states need the entry's versionCode and signers.
 */
internal enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE, LOCAL_NEWER, SIGNATURE_MISMATCH }

/** Whether the catalog lets this client install the entry right now (see [storeItems]). */
internal enum class Availability {
    AVAILABLE,
    /** No verified catalog (no snapshot in a local build, nothing fetched yet). */
    NO_CATALOG,
    /** The catalog has expired: display only, no installs or updates (§6.2). */
    CATALOG_EXPIRED,
    /** The catalog has no entry for this plugin (or it names a different package). */
    NOT_IN_CATALOG,
    /** The entry needs a newer store protocol than this Bridge speaks. */
    NEEDS_NEWER_BRIDGE,
    /** The entry's minSdk is above this phone's Android version. */
    UNSUPPORTED_SDK,
}

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
internal enum class StoreAction {
    DOWNLOAD, UPDATE, OPEN_DEBUG, OPEN_INFO, OPEN_APP, NONE,
    /** Operation controls (see install.Operation): stop a download / abandon a waiting session. */
    CANCEL,
    /** Reopen the system installer confirmation the user backed out of. */
    CONFIRM_INSTALL,
    /** Open the system "install unknown apps" page for this App. */
    ALLOW_INSTALLS,
    /** Open the LSPosed manager on this module's page (root, or a standalone manager). */
    OPEN_LSPOSED,
}

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
    /** versionName of the catalog entry, when there is one. */
    val catalogVersion: String? = null,
    val availability: Availability = Availability.NO_CATALOG,
    /** Formal localized store name; 0 falls back to [name]. */
    @StringRes val nameRes: Int = 0,
    /** The Provider's own launcher icon, copied into the Bridge; 0 = generic icon. */
    @DrawableRes val iconRes: Int = 0,
    /** LSPosed enable state for installed modules; null when not applicable or not asked. */
    val enablement: Enablement? = null,
)

/** The name shown to people: the formal localized store name when there is one. */
internal fun StoreItem.displayName(resources: Resources): String =
    if (nameRes != 0) resources.getString(nameRes) else name

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

/** Home cards below the live status card; onboarding and features are still sample data. */
internal data class HomeState(
    val onboardingRemaining: Int,
    val featuresNeedingAttention: Int,
)

internal fun StoreItem.primaryAction(): StoreAction = when (installState) {
    InstallState.NOT_INSTALLED -> if (availability == Availability.AVAILABLE) StoreAction.DOWNLOAD else StoreAction.NONE
    InstallState.UPDATE_AVAILABLE -> if (availability == Availability.AVAILABLE) StoreAction.UPDATE else openAction()
    // A different signer cannot be updated in place; never offer a one-tap data wipe. The
    // plugin's own page still opens (e.g. a locally built debug Provider).
    InstallState.SIGNATURE_MISMATCH, InstallState.LOCAL_NEWER, InstallState.INSTALLED -> openAction()
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
    /** Short ASCII name, matching the matrix displayName (asset names, logs). */
    val name: String,
    val applicationId: String,
    val kind: ProviderKind,
    val entry: ProviderEntry,
    val hostPackages: List<String>,
    /** Formal store name, localized: "<official player name> Provider". */
    @StringRes val nameRes: Int,
    /**
     * Copy of the Provider's launcher icon (the target player's official artwork, see
     * `res/drawable/store_icon_*`); 0 when the Provider has none of its own (Readify).
     */
    @DrawableRes val iconRes: Int = 0,
)

internal object ProviderDirectory {
    private const val P = "io.github.andrealtb.coloroslyrics.provider."
    private fun dedicated(module: String, name: String, nameRes: Int, iconRes: Int, id: String, vararg hosts: String) =
        DirectoryEntry(module, name, P + id, ProviderKind.DEDICATED, ProviderEntry.DEBUG_PAGE, hosts.toList(), nameRes, iconRes)

    val entries: List<DirectoryEntry> = listOf(
        dedicated("player-salt", "Salt", R.string.store_name_salt, R.drawable.store_icon_salt, "salt", "com.salt.music"),
        dedicated("player-cone", "Cone", R.string.store_name_cone, R.drawable.store_icon_cone, "cone", "ink.trantor.coneplayer", "ink.trantor.coneplayer.gp"),
        dedicated("kuwo-music", "KuWo", R.string.store_name_kuwo, R.drawable.store_icon_kuwo, "kuwo", "cn.kuwo.player"),
        dedicated("player-lx", "LX", R.string.store_name_lx, R.drawable.store_icon_lx, "lx", "cn.toside.music.mobile", "com.lxwalnut.music.mobile"),
        dedicated("player-poweramp", "Poweramp", R.string.store_name_poweramp, R.drawable.store_icon_poweramp, "poweramp", "com.maxmpz.audioplayer"),
        dedicated("player-metrolist", "Metrolist", R.string.store_name_metrolist, R.drawable.store_icon_metrolist, "metrolist", "com.metrolist.music"),
        dedicated("player-kugou", "KuGou", R.string.store_name_kugou, R.drawable.store_icon_kugou, "kugou", "com.kugou.android", "com.kugou.android.lite"),
        dedicated("player-qq", "QQ", R.string.store_name_qq, R.drawable.store_icon_qq, "qq", "com.tencent.qqmusic"),
        dedicated("player-netease", "NetEase", R.string.store_name_netease, R.drawable.store_icon_netease, "netease", "com.netease.cloudmusic", "com.hihonor.cloudmusic"),
        dedicated("player-apple", "Apple", R.string.store_name_apple, R.drawable.store_icon_apple, "apple", "com.apple.android.music"),
        dedicated("player-spotify", "Spotify", R.string.store_name_spotify, R.drawable.store_icon_spotify, "spotify", "com.spotify.music"),
        dedicated("player-qishui", "QiShui", R.string.store_name_qishui, R.drawable.store_icon_qishui, "qishui", "com.luna.music"),
        DirectoryEntry("player-readify", "Readify", P + "readify", ProviderKind.DEDICATED,
            ProviderEntry.INFO_PAGE, listOf("com.readin.app"), R.string.store_name_readify),
        DirectoryEntry("universal-provider", "Universal", P + "universal", ProviderKind.UNIVERSAL,
            ProviderEntry.LAUNCHER, emptyList(), R.string.store_name_universal, R.drawable.store_icon_universal),
        DirectoryEntry("artwork-provider-am", "Dynamic Artwork", "io.github.andrealtb.artwork.am",
            ProviderKind.ARTWORK, ProviderEntry.LAUNCHER, emptyList(), R.string.store_name_artwork, R.drawable.store_icon_artwork),
    )
}

internal object SampleData {
    val home = HomeState(
        onboardingRemaining = 2,
        featuresNeedingAttention = 1,
    )
}
