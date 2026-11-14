package io.github.andrealtb.lockscreenlyrics.management.root

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import io.github.andrealtb.lockscreenlyrics.management.Enablement
import io.github.andrealtb.lockscreenlyrics.management.EnablementReport
import java.io.File

/** Module states read from the LSPosed configuration, limited to the packages asked about. */
internal data class LsposedSnapshot(
    /** null = the module is not registered with LSPosed (yet). */
    val enabled: Map<String, Boolean?>,
    val scope: Map<String, Set<String>>,
) {
    fun reportFor(packageName: String): EnablementReport = when (enabled[packageName]) {
        true -> EnablementReport(Enablement.ENABLED, scope[packageName].orEmpty())
        false -> EnablementReport(Enablement.NOT_ENABLED)
        null -> EnablementReport(Enablement.UNKNOWN)
    }
}

internal sealed interface LsposedRead {
    data class Ok(val snapshot: LsposedSnapshot) : LsposedRead
    /** Root works but no LSPosed configuration file was found. */
    data object NoConfig : LsposedRead
    /** The file was read but its tables are not a layout this Bridge knows. */
    data object UnknownLayout : LsposedRead
    data object Failed : LsposedRead
}

/**
 * The two table layouts LSPosed has used for `modules_config.db` (plan §14.10):
 * - PER_USER_STATE: LSPosed 2.2.x (lsposed.zip) — `modules_state(module_pkg_name, user_id, enabled)`
 *   and `scope(module_pkg_name, app_pkg_name, user_id)`; seen on PJZ110 with 2.2.1 (7941).
 * - MODULE_ID: LSPosed 1.x and JingMatrix/Vector — `modules(mid, module_pkg_name, enabled)` and
 *   `scope(mid, app_pkg_name, user_id)`.
 */
internal enum class LsposedLayout { PER_USER_STATE, MODULE_ID }

internal fun detectLayout(columns: Map<String, Set<String>>): LsposedLayout? {
    val scope = columns["scope"] ?: return null
    if (!scope.containsAll(setOf("app_pkg_name", "user_id"))) return null
    val state = columns["modules_state"]
    if (state != null && state.containsAll(setOf("module_pkg_name", "user_id", "enabled")) &&
        "module_pkg_name" in scope
    ) return LsposedLayout.PER_USER_STATE
    val modules = columns["modules"] ?: return null
    if (modules.containsAll(setOf("mid", "module_pkg_name", "enabled")) && "mid" in scope) return LsposedLayout.MODULE_ID
    return null
}

/** SQL per layout; every query is parameterized by one module package and the Android user. */
internal object LsposedQueries {
    fun enabled(layout: LsposedLayout): String = when (layout) {
        LsposedLayout.PER_USER_STATE ->
            "SELECT enabled FROM modules_state WHERE module_pkg_name = ? AND user_id = ?"
        LsposedLayout.MODULE_ID -> "SELECT enabled FROM modules WHERE module_pkg_name = ? AND ? IS NOT NULL"
    }

    fun registered(): String = "SELECT 1 FROM modules WHERE module_pkg_name = ?"

    fun scope(layout: LsposedLayout): String = when (layout) {
        LsposedLayout.PER_USER_STATE ->
            "SELECT app_pkg_name FROM scope WHERE module_pkg_name = ? AND user_id = ?"
        LsposedLayout.MODULE_ID ->
            "SELECT s.app_pkg_name FROM scope s JOIN modules m ON s.mid = m.mid WHERE m.module_pkg_name = ? AND s.user_id = ?"
    }

    /** Registered with no state row means "never enabled for this user". */
    fun enabledValue(row: Int?, registered: Boolean): Boolean? = when {
        row != null -> row == 1
        registered -> false
        else -> null
    }
}

/**
 * Reads LSPosed's own configuration file with root (plan §14.10): `cat` copies
 * `/data/adb/lspd/config/modules_config.db` and its WAL into this App's private cache, the copy
 * is opened with Android's SQLite, only the asked modules' rows are read, and the copy is
 * deleted at once. The original files are never opened for writing.
 */
internal object LsposedConfig {
    private const val DIR = "/data/adb/lspd/config"
    private const val DB = "modules_config.db"
    private const val MAX_BYTES = 32L * 1024 * 1024
    private const val TIMEOUT_MS = 15_000L
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun read(context: Context, packages: List<String>, userId: Int): LsposedRead {
        val asked = packages.filter { PACKAGE.matches(it) }.distinct()
        val work = File(context.cacheDir, "lspd-read").apply { deleteRecursively(); mkdirs() }
        try {
            val db = File(work, DB)
            // WAL first: a checkpoint between the two copies then leaves the main file newer.
            RootShell.copyFile("$DIR/$DB-wal", File(work, "$DB-wal"), TIMEOUT_MS, MAX_BYTES)
            if (!RootShell.copyFile("$DIR/$DB", db, TIMEOUT_MS, MAX_BYTES)) return LsposedRead.NoConfig
            return try {
                // Read-write only because SQLite replays the copied WAL; this is our own copy.
                SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE).use { sql ->
                    val layout = detectLayout(columnsOf(sql)) ?: return LsposedRead.UnknownLayout
                    LsposedRead.Ok(snapshot(sql, layout, asked, userId))
                }
            } catch (_: SQLiteException) {
                LsposedRead.Failed
            }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun columnsOf(sql: SQLiteDatabase): Map<String, Set<String>> = buildMap {
        for (table in listOf("modules", "modules_state", "scope")) {
            sql.rawQuery("PRAGMA table_info($table)", null).use { c ->
                val names = buildSet { while (c.moveToNext()) add(c.getString(1)) }
                if (names.isNotEmpty()) put(table, names)
            }
        }
    }

    private fun snapshot(sql: SQLiteDatabase, layout: LsposedLayout, asked: List<String>, userId: Int): LsposedSnapshot {
        val user = userId.toString()
        val enabled = asked.associateWith { pkg ->
            val row = sql.rawQuery(LsposedQueries.enabled(layout), arrayOf(pkg, user)).use { c ->
                if (c.moveToFirst()) c.getInt(0) else null
            }
            val registered = sql.rawQuery(LsposedQueries.registered(), arrayOf(pkg)).use { it.moveToFirst() }
            LsposedQueries.enabledValue(row, registered)
        }
        val scope = asked.associateWith { pkg ->
            sql.rawQuery(LsposedQueries.scope(layout), arrayOf(pkg, user)).use { c ->
                buildSet { while (c.moveToNext()) c.getString(0)?.let(::add) }
            }
        }
        return LsposedSnapshot(enabled, scope)
    }

    /**
     * Opens the LSPosed manager on [packageName]'s page: the parasitic manager in
     * com.android.shell with a `module://<pkg>:<user>` link (only root may start it; checked
     * on PJZ110 with 2.2.1). A separately installed manager app is tried first, without root.
     */
    fun openModule(context: Context, packageName: String, userId: Int, rootGranted: Boolean): Boolean {
        if (!PACKAGE.matches(packageName)) return false
        val link = Uri.parse("module://$packageName:$userId")
        for (manager in STANDALONE_MANAGERS) {
            val component = context.packageManager.getLaunchIntentForPackage(manager)?.component ?: continue
            // A running manager keeps showing its last page, even across a fresh task (PJZ110,
            // 2.2.1: entering the Bridge from a module page brought that page back). With root,
            // stop only the manager's UI app first (the framework daemon is a separate root
            // process and keeps running), then open it in its own task so the Bridge screen,
            // which may sit in the manager's task, stays where back returns to.
            val stopped = rootGranted && RootShell.run("am force-stop $manager", TIMEOUT_MS)?.exitCode == 0
            val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                if (stopped) Intent.FLAG_ACTIVITY_MULTIPLE_TASK else Intent.FLAG_ACTIVITY_CLEAR_TASK
            val open = Intent(Intent.ACTION_MAIN).setComponent(component).setData(link).addFlags(flags)
            if (runCatching { context.startActivity(open) }.isSuccess) return true
        }
        if (!rootGranted) return false
        val script = "am start --user $userId -n com.android.shell/.BugreportWarningActivity " +
            "-a android.intent.action.MAIN -c org.lsposed.manager.LAUNCH_MANAGER " +
            "-d 'module://$packageName:$userId' -f 0x10000000"
        val result = RootShell.run(script, TIMEOUT_MS) ?: return false
        return result.exitCode == 0 && !result.stdout.contains("Error")
    }

    private val STANDALONE_MANAGERS = listOf("org.lsposed.manager", "org.matrix.vector.manager")
}
