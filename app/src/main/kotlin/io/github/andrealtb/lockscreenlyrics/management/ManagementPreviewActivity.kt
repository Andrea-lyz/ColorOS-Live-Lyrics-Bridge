package io.github.andrealtb.lockscreenlyrics.management

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.andrealtb.lockscreenlyrics.LyricUiSettingsActivity
import io.github.andrealtb.lockscreenlyrics.ModuleStatusQuery
import io.github.andrealtb.lockscreenlyrics.R
import io.github.andrealtb.lockscreenlyrics.SettingsLocaleContext
import kotlinx.coroutines.launch

/**
 * M0 management UI prototype (plan §8.1/§8.4 U-01). Live: the home status card (read-only reply
 * from the Bridge in SystemUI) and the store's installed state; other home cards are SAMPLE data
 * and there is no catalog, so nothing can be downloaded or updated. Ordinary App
 * process only; nothing in the Hook/bootstrap/render path may reference this package. No action
 * installs, updates or requests a scope.
 */
class ManagementPreviewActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var statusQuery by mutableStateOf<StatusQuery>(StatusQuery.Pending)
    private var installedItems by mutableStateOf(storeItems(ProviderDirectory.entries) { null })
    /** Bumped for every request and on pause, so late replies and timeouts are ignored. */
    private var queryGeneration = 0

    override fun attachBaseContext(newBase: Context) {
        // Same persisted language as the Views settings pages.
        super.attachBaseContext(SettingsLocaleContext.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ManagementTheme {
                ManagementPrototype(
                    status = homeStatus(ModuleStatusQuery.installedBuildId(), statusQuery),
                    onRefreshStatus = ::queryStatus,
                    storeItems = installedItems,
                    onStoreAction = ::runStoreAction,
                    onOpenAppearance = {
                        startActivity(Intent(this, LyricUiSettingsActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check after returning from LSPosed, a plugin page, an installer or a restart.
        queryStatus()
        refreshInstalled()
    }

    /** Read-only PackageManager lookup, limited by the manifest's directed <queries>. */
    private fun refreshInstalled() {
        installedItems = storeItems(ProviderDirectory.entries) { packageName ->
            try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            } catch (missing: PackageManager.NameNotFoundException) {
                null
            } catch (unavailable: RuntimeException) {
                null
            }
        }
    }

    /**
     * Opens a plugin's own page. The store never changes a plugin's settings: debug mode is
     * toggled inside each plugin's page. Returns a message for the snackbar, or null.
     */
    private fun runStoreAction(item: StoreItem, action: StoreAction): String? {
        val intent = when (action) {
            StoreAction.OPEN_DEBUG, StoreAction.OPEN_INFO ->
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_INFO).setPackage(item.applicationId)
            StoreAction.OPEN_APP -> packageManager.getLaunchIntentForPackage(item.applicationId)
            StoreAction.DOWNLOAD, StoreAction.UPDATE -> return getString(R.string.mgmt_snackbar_no_catalog)
            StoreAction.NONE -> return null
        } ?: return getString(R.string.mgmt_snackbar_cannot_open, item.name)
        return try {
            startActivity(intent)
            null
        } catch (notFound: ActivityNotFoundException) {
            getString(R.string.mgmt_snackbar_cannot_open, item.name)
        } catch (denied: SecurityException) {
            getString(R.string.mgmt_snackbar_cannot_open, item.name)
        }
    }

    override fun onPause() {
        queryGeneration++
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    /** Read-only request to the Bridge in SystemUI; never changes any setting. */
    private fun queryStatus() {
        val generation = ++queryGeneration
        handler.removeCallbacksAndMessages(null)
        statusQuery = StatusQuery.Pending
        val sent = ModuleStatusQuery.send(this, handler) { buildId, revision ->
            if (generation == queryGeneration && statusQuery == StatusQuery.Pending) {
                statusQuery = StatusQuery.Reply(buildId, revision)
            }
        }
        if (!sent) {
            statusQuery = StatusQuery.NoReply
            return
        }
        handler.postDelayed({
            if (generation == queryGeneration && statusQuery == StatusQuery.Pending) {
                statusQuery = StatusQuery.NoReply
            }
        }, ModuleStatusQuery.TIMEOUT_MS)
    }
}

private data class TabSpec(val tab: ManagementTab, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(ManagementTab.HOME, R.string.mgmt_tab_home, Icons.Filled.Home),
    TabSpec(ManagementTab.STORE, R.string.mgmt_tab_store, Icons.Filled.ShoppingCart),
    TabSpec(ManagementTab.APPEARANCE, R.string.mgmt_tab_appearance, Icons.Filled.Edit),
    TabSpec(ManagementTab.STATUS, R.string.mgmt_tab_status, Icons.Filled.Info),
)

/** Width at which the bottom bar becomes a navigation rail (Material medium window class). */
private const val RAIL_MIN_WIDTH_DP = 600

@Composable
internal fun ManagementPrototype(
    status: HomeStatus,
    onRefreshStatus: () -> Unit,
    storeItems: List<StoreItem>,
    onStoreAction: (StoreItem, StoreAction) -> String?,
    onOpenAppearance: () -> Unit,
) {
    // Only page-level UI state lives here; it is safe to restore after recreation.
    var tab by rememberSaveable { mutableStateOf(ManagementTab.HOME) }
    var filter by rememberSaveable { mutableStateOf(StoreFilter.SUITABLE) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nothingDone = stringResource(R.string.mgmt_snackbar_prototype)
    val showNothingDone: () -> Unit = { scope.launch { snackbar.showSnackbar(nothingDone) } }
    val useRail = LocalConfiguration.current.screenWidthDp >= RAIL_MIN_WIDTH_DP

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!useRail) {
                NavigationBar {
                    TABS.forEach { spec ->
                        NavigationBarItem(
                            selected = tab == spec.tab,
                            onClick = { tab = spec.tab },
                            icon = { Icon(spec.icon, contentDescription = null) },
                            label = { Text(stringResource(spec.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding)) {
            if (useRail) {
                NavigationRail {
                    TABS.forEach { spec ->
                        NavigationRailItem(
                            selected = tab == spec.tab,
                            onClick = { tab = spec.tab },
                            icon = { Icon(spec.icon, contentDescription = null) },
                            label = { Text(stringResource(spec.label)) },
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                when (tab) {
                    ManagementTab.HOME -> HomeContent(
                        status = status,
                        state = SampleData.home,
                        onRefreshStatus = onRefreshStatus,
                        onContinueSetup = showNothingDone,
                        onViewReasons = { tab = ManagementTab.STATUS },
                        onOpenStore = { tab = ManagementTab.STORE },
                    )
                    ManagementTab.STORE -> StoreContent(
                        items = storeItems,
                        filter = filter,
                        onFilterChange = { filter = it },
                        onAction = { item, action ->
                            onStoreAction(item, action)?.let { message ->
                                scope.launch { snackbar.showSnackbar(message) }
                            }
                        },
                    )
                    ManagementTab.APPEARANCE -> PlaceholderContent(
                        body = stringResource(R.string.mgmt_appearance_body),
                        action = stringResource(R.string.mgmt_appearance_open),
                        onAction = onOpenAppearance,
                    )
                    ManagementTab.STATUS -> PlaceholderContent(
                        body = stringResource(R.string.mgmt_status_body),
                        action = null,
                        onAction = {},
                    )
                }
            }
        }
    }
}
