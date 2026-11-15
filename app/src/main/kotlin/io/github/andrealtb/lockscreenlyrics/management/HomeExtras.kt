package io.github.andrealtb.lockscreenlyrics.management

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon (plan D3, user decision 2026-11-15): an activity-alias of the management
 * Home that the Home card can disable. LSPosed's module-settings entry is on the Activity itself,
 * so hiding the icon never removes the way in from LSPosed.
 */
internal object LauncherIcon {
    private const val ALIAS = "io.github.andrealtb.lockscreenlyrics.management.LauncherEntry"

    private fun component(context: Context) = ComponentName(context.packageName, ALIAS)

    fun isShown(context: Context): Boolean =
        isShown(context.packageManager.getComponentEnabledSetting(component(context)))

    /** DEFAULT follows the manifest, where the alias is enabled. */
    fun isShown(setting: Int): Boolean = setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setShown(context: Context, shown: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (shown) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

/**
 * One-time "what's new in 5.0" card (user decision 2026-11-15). Only people upgrading from an
 * older Bridge see it; a fresh install is marked as seen on first launch so a later 5.x update
 * does not show it to someone who never used 4.x.
 */
internal object WhatsNew {
    private const val PREFS = "management_home"
    private const val KEY_SEEN = "whats_new_5_0_seen"

    /** Pure decision for tests: not yet seen, and this install was updated from an earlier APK. */
    fun shouldShow(seen: Boolean, firstInstallTime: Long, lastUpdateTime: Long): Boolean =
        !seen && lastUpdateTime > firstInstallTime

    fun shouldShow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getBoolean(KEY_SEEN, false)
        val info = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        val show = shouldShow(seen, info.firstInstallTime, info.lastUpdateTime)
        if (!show && !seen) markSeen(context)
        return show
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply()
    }
}
