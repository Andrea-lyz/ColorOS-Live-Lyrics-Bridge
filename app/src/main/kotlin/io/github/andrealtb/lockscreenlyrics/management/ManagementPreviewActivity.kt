package io.github.andrealtb.lockscreenlyrics.management

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
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
import io.github.andrealtb.lockscreenlyrics.management.catalog.CatalogState
import io.github.andrealtb.lockscreenlyrics.management.install.Operation
import io.github.andrealtb.lockscreenlyrics.management.install.StoreOperations
import io.github.andrealtb.lockscreenlyrics.management.reminder.UpdateReminders
import io.github.andrealtb.lockscreenlyrics.management.root.LsposedConfig
import io.github.andrealtb.lockscreenlyrics.management.root.LsposedRead
import io.github.andrealtb.lockscreenlyrics.management.root.RootAccess
import io.github.andrealtb.lockscreenlyrics.management.root.RootState
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Management UI (plan §8.1/§8.4 U-01). Live: the home status card (read-only reply from the
 * Bridge in SystemUI) and the store (installed packages projected onto the verified catalog, see
 * [StoreRuntime]); the onboarding/feature home cards are SAMPLE data. Ordinary App process only;
 * nothing in the Hook/bootstrap/render path may reference this package. No action requests a
 * scope.
 */
class ManagementPreviewActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var statusQuery by mutableStateOf<StatusQuery>(StatusQuery.Pending)
    private var installed by mutableStateOf<Map<String, InstalledPackage>>(emptyMap())
    private var enablement by mutableStateOf<Map<String, EnablementReport>>(emptyMap())
    private var enablementJob: kotlinx.coroutines.Job? = null
    private var root by mutableStateOf(RootView())
    private val userId get() = android.os.Process.myUid() / 100_000
    private var catalog by mutableStateOf(CatalogView())
    /** Bumped for every request and on pause, so late replies and timeouts are ignored. */
    private var queryGeneration = 0
    private var reminders by mutableStateOf(RemindersView())
    /** Incremented when a notification asks to show the store. */
    private var openStoreRequests by mutableIntStateOf(0)
    private var message by mutableStateOf<String?>(null)

    /** Asked only when the user turns reminders on (plan §6.4/§6.5), never on first open. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            setReminders(true)
        } else {
            message = getString(R.string.mgmt_reminder_permission_denied)
        }
    }

    companion object {
        /** Set by the update reminder notification: open on the store tab. */
        const val EXTRA_OPEN_STORE = "io.github.andrealtb.lockscreenlyrics.extra.OPEN_STORE"
    }

    override fun attachBaseContext(newBase: Context) {
        // Same persisted language as the Views settings pages.
        super.attachBaseContext(SettingsLocaleContext.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_STORE, false)) openStoreRequests++
        UpdateReminders.ensureScheduled(this)
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { StoreRuntime.load(applicationContext) }
            catalog = catalog.copy(state = state)
        }
        lifecycleScope.launch {
            // Launch each system installer confirmation once, only while this screen is visible.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                StoreOperations.confirmation.collect { intent ->
                    if (intent != null) {
                        StoreOperations.confirmationLaunched()
                        try {
                            startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                        } catch (_: SecurityException) {
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            // An operation that finished changes what is installed.
            var finished = emptySet<String>()
            StoreOperations.operations.collect { ops ->
                val now = ops.filterValues { it is Operation.Succeeded || it is Operation.Failed }.keys
                if (now != finished) refreshInstalled()
                finished = now
            }
        }
        setContent {
            ManagementTheme {
                val packages = installed
                val enabled = enablement
                val view = catalog
                val operations by StoreOperations.operations.collectAsState()
                ManagementPrototype(
                    status = homeStatus(ModuleStatusQuery.installedBuildId(), statusQuery),
                    onRefreshStatus = ::queryStatus,
                    store = storeItems(ProviderDirectory.entries, view.state ?: CatalogState.Unavailable,
                        Build.VERSION.SDK_INT, { enabled[it] }) { packages[it] },
                    catalog = view,
                    onRefreshCatalog = { refreshCatalog(automatic = false) },
                    onStoreOpened = { refreshCatalog(automatic = true) },
                    operations = operations,
                    reminders = reminders,
                    onRemindersChange = ::onRemindersChange,
                    root = root,
                    onRequestRoot = ::requestRoot,
                    openStoreRequests = openStoreRequests,
                    message = message,
                    onMessageShown = { message = null },
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
        StoreOperations.reconcile(this)
        reminders = RemindersView(UpdateReminders.isEnabled(this), UpdateReminders.notificationsAllowed(this))
        StoreOperations.continueIfAllowed(this)
    }

    /** Read-only PackageManager lookup, limited by the manifest's directed <queries>. */
    private var refreshJob: kotlinx.coroutines.Job? = null

    private fun refreshInstalled() {
        // One refresh at a time: resume and a finished operation must not race two su checks.
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            installed = withContext(Dispatchers.IO) { InstalledPackages.snapshot(packageManager, ProviderDirectory.entries) }
            // Never prompts here: only a root grant the user asked for earlier is used.
            if (RootAccess.requested(this@ManagementPreviewActivity)) {
                val had = root.state == RootState.GRANTED
                if (!had) root = root.copy(state = RootState.CHECKING)
                root = root.copy(state = withContext(Dispatchers.IO) {
                    RootAccess.check(applicationContext, prompt = false, previouslyGranted = had)
                })
            }
            refreshEnablement()
        }
    }

    /** The user's tap: the root manager (Magisk/KernelSU/APatch) may ask them. */
    private fun requestRoot() {
        root = root.copy(state = RootState.CHECKING)
        lifecycleScope.launch {
            root = root.copy(state = withContext(Dispatchers.IO) { RootAccess.check(applicationContext, prompt = true) })
            refreshEnablement()
        }
    }

    /**
     * Asks each installed module whether LSPosed enabled it (on every resume, so returning from
     * the LSPosed manager updates the cards). Sequential and off the main thread.
     */
    private fun refreshEnablement() {
        enablementJob?.cancel()
        val targets = ProviderDirectory.entries.filter { it.reportsEnablement() && installed.containsKey(it.applicationId) }
        enablement = targets.associate { it.applicationId to (enablement[it.applicationId] ?: EnablementReport(Enablement.CHECKING)) }
        enablementJob = lifecycleScope.launch {
            // With root: what LSPosed itself has stored, read from a private copy of its config file.
            if (root.state == RootState.GRANTED) {
                val read = withContext(Dispatchers.IO) {
                    LsposedConfig.read(applicationContext, targets.map { it.applicationId }, userId)
                }
                root = root.copy(cliAvailable = read is LsposedRead.Ok)
                if (read is LsposedRead.Ok) {
                    enablement = targets.associate { it.applicationId to read.snapshot.reportFor(it.applicationId) }
                    return@launch
                }
            }
            // Without root (or no CLI): each plugin's own report.
            for (entry in targets) {
                val report = withContext(Dispatchers.IO) { ProviderEnablementQuery.query(applicationContext, entry.applicationId) }
                enablement = enablement + (entry.applicationId to report)
            }
        }
    }

    /**
     * Fetches the catalog mirrors. Opening the store checks at most every few minutes; the
     * button always checks. The result is shown on the catalog card, never silently dropped.
     */
    private fun refreshCatalog(automatic: Boolean) {
        if (catalog.refreshing || catalog.state == null) return
        lifecycleScope.launch {
            if (automatic && !withContext(Dispatchers.IO) { StoreRuntime.autoRefreshDue() }) return@launch
            catalog = catalog.copy(refreshing = true)
            val (result, state) = withContext(Dispatchers.IO) { StoreRuntime.refresh(applicationContext) }
            catalog = CatalogView(state = state, refreshing = false, lastRefresh = result)
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
            StoreAction.DOWNLOAD, StoreAction.UPDATE -> return startInstall(item)
            StoreAction.CANCEL -> {
                StoreOperations.cancel(this, item.id)
                return null
            }
            StoreAction.CONFIRM_INSTALL -> {
                StoreOperations.reopenConfirmation()
                return null
            }
            StoreAction.ALLOW_INSTALLS -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            StoreAction.OPEN_LSPOSED -> {
                openLsposed(item)
                return null
            }
            StoreAction.NONE -> return null
        } ?: return getString(R.string.mgmt_snackbar_cannot_open, item.displayName(resources))
        return try {
            startActivity(intent)
            null
        } catch (notFound: ActivityNotFoundException) {
            getString(R.string.mgmt_snackbar_cannot_open, item.displayName(resources))
        } catch (denied: SecurityException) {
            getString(R.string.mgmt_snackbar_cannot_open, item.displayName(resources))
        }
    }

    /** Opens the LSPosed manager on the module; the user flips the switch there themselves. */
    private fun openLsposed(item: StoreItem) {
        val name = item.displayName(resources)
        lifecycleScope.launch {
            val opened = withContext(Dispatchers.IO) {
                LsposedConfig.openModule(applicationContext, item.applicationId, userId, root.state == RootState.GRANTED)
            }
            // LSPosed 2.2.1 opens on its home page, not the module (PJZ110), so always say where to go.
            if (opened) {
                message = getString(R.string.mgmt_lsposed_opened, name)
            } else {
                message = getString(
                    if (root.state == RootState.GRANTED) R.string.mgmt_lsposed_open_failed else R.string.mgmt_lsposed_open_needs_root,
                    name,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_STORE, false)) openStoreRequests++
    }

    private fun onRemindersChange(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !UpdateReminders.notificationsAllowed(this)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        setReminders(enabled)
    }

    private fun setReminders(enabled: Boolean) {
        UpdateReminders.setEnabled(this, enabled)
        reminders = RemindersView(enabled, UpdateReminders.notificationsAllowed(this))
    }

    /** Starts download → verify → install for the item's entry in the current, fresh catalog. */
    private fun startInstall(item: StoreItem): String? {
        val current = catalog.state as? CatalogState.Current
        val entry = current?.catalog?.entries?.firstOrNull { it.providerId == item.id && it.applicationId == item.applicationId }
        if (current == null || !current.installable || entry == null || item.availability != Availability.AVAILABLE) {
            return getString(R.string.mgmt_snackbar_not_installable)
        }
        return if (StoreOperations.start(this, entry, current.catalog.trustedHosts)) null
        else getString(R.string.mgmt_snackbar_busy)
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
    store: StoreProjection,
    catalog: CatalogView,
    onRefreshCatalog: () -> Unit,
    onStoreOpened: () -> Unit,
    operations: Map<String, Operation>,
    reminders: RemindersView,
    onRemindersChange: (Boolean) -> Unit,
    root: RootView,
    onRequestRoot: () -> Unit,
    openStoreRequests: Int,
    message: String?,
    onMessageShown: () -> Unit,
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
    LaunchedEffect(openStoreRequests) { if (openStoreRequests > 0) tab = ManagementTab.STORE }
    LaunchedEffect(message) {
        if (message != null) {
            onMessageShown()
            snackbar.showSnackbar(message)
        }
    }
    // Also re-runs once the first catalog load finishes; the callee throttles network checks.
    val catalogLoaded = catalog.state != null
    LaunchedEffect(tab, catalogLoaded) { if (tab == ManagementTab.STORE && catalogLoaded) onStoreOpened() }

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
                        updatesAvailable = store.updatesAvailable,
                        root = root,
                        onRequestRoot = onRequestRoot,
                        onRefreshStatus = onRefreshStatus,
                        onContinueSetup = showNothingDone,
                        onViewReasons = { tab = ManagementTab.STATUS },
                        onOpenStore = { tab = ManagementTab.STORE },
                    )
                    ManagementTab.STORE -> StoreContent(
                        items = store.items,
                        unrecognizedEntries = store.unrecognizedEntries,
                        catalog = catalog,
                        onRefreshCatalog = onRefreshCatalog,
                        operations = operations,
                        reminders = reminders,
                        onRemindersChange = onRemindersChange,
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
