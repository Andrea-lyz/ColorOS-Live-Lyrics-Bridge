package io.github.andrealtb.lockscreenlyrics.management.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.andrealtb.lockscreenlyrics.R
import io.github.andrealtb.lockscreenlyrics.management.Availability
import io.github.andrealtb.lockscreenlyrics.management.InstallState
import io.github.andrealtb.lockscreenlyrics.management.InstalledPackages
import io.github.andrealtb.lockscreenlyrics.management.ManagementPreviewActivity
import io.github.andrealtb.lockscreenlyrics.management.ProviderDirectory
import io.github.andrealtb.lockscreenlyrics.management.StoreItem
import io.github.andrealtb.lockscreenlyrics.management.StoreRuntime
import io.github.andrealtb.lockscreenlyrics.management.catalog.RefreshOutcome
import io.github.andrealtb.lockscreenlyrics.management.storeItems
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** What one background check should do with the notification (pure, see [planNotice]). */
internal data class UpdateNotice(
    /** Plugins to name in the notification; empty means cancel any shown notification. */
    val names: List<String>,
    /** True only when an update appeared that was not announced before. */
    val alert: Boolean,
    /** Keys to remember, so the same versions are not announced again. */
    val announced: Set<String>,
)

/** Updates a reminder may announce: installable now, same signer, older local version. */
internal fun announceableUpdates(items: List<StoreItem>): List<StoreItem> =
    items.filter { it.installState == InstallState.UPDATE_AVAILABLE && it.availability == Availability.AVAILABLE }

/**
 * Plan §6.5: announce each plugin version once. A version already announced stays silent (the
 * notification is kept, without sound), an installed update disappears from the next notice,
 * and a newer version of the same plugin is announced again.
 */
internal fun planNotice(updates: List<StoreItem>, previouslyAnnounced: Set<String>): UpdateNotice {
    val keys = updates.associateBy { "${it.id}@${it.catalogVersion}" }
    return UpdateNotice(
        names = updates.map { it.name },
        alert = keys.keys.any { it !in previouslyAnnounced },
        announced = keys.keys,
    )
}

/**
 * Optional background update check (plan §6.5). Off by default; the user turns it on in the
 * store. A JobScheduler job with a network constraint runs about once a day (ColorOS decides
 * when, so nothing is promised as real time); failures retry with the platform's exponential
 * backoff. It only reminds: it never downloads or installs. The lyric runtime never depends on it.
 */
internal object UpdateReminders {
    private const val PREFS = "provider_store"
    private const val KEY_ENABLED = "update_reminders_enabled"
    private const val KEY_ANNOUNCED = "update_reminders_announced"
    private const val JOB_ID = 0x434c4c01
    private const val CHANNEL_ID = "provider_updates"
    private const val NOTIFICATION_ID = 0x434c4c02
    private const val INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val FLEX_MS = 6L * 60 * 60 * 1000

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Notification permission is the caller's job (asked only when the user turns this on). */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context) else {
            scheduler(context).cancel(JOB_ID)
            notifications(context).cancel(NOTIFICATION_ID)
            prefs(context).edit().remove(KEY_ANNOUNCED).apply()
        }
    }

    /** Re-creates a lost job (e.g. after an App update) without changing an existing one. */
    fun ensureScheduled(context: Context) {
        if (isEnabled(context) && scheduler(context).getPendingJob(JOB_ID) == null) schedule(context)
    }

    fun notificationsAllowed(context: Context): Boolean = notifications(context).areNotificationsEnabled()

    private fun schedule(context: Context) {
        val app = context.applicationContext
        val job = JobInfo.Builder(JOB_ID, ComponentName(app, UpdateCheckJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(INTERVAL_MS, FLEX_MS)
            .setPersisted(true)
            .build()
        scheduler(app).schedule(job)
    }

    /** Blocking. Returns true when the check should be retried (no mirror answered). */
    fun runCheck(context: Context): Boolean {
        val app = context.applicationContext
        if (!isEnabled(app)) return false
        val (result, state) = StoreRuntime.refresh(app)
        if (result.outcome == RefreshOutcome.NETWORK_FAILED) return true
        val installed = InstalledPackages.snapshot(app.packageManager, ProviderDirectory.entries)
        val items = storeItems(ProviderDirectory.entries, state, Build.VERSION.SDK_INT) { installed[it] }.items
        val previous = prefs(app).getStringSet(KEY_ANNOUNCED, emptySet()).orEmpty()
        val notice = planNotice(announceableUpdates(items), previous)
        // Without notification permission nothing is announced, so nothing is remembered either.
        if (notice.names.isNotEmpty() && !notificationsAllowed(app)) return false
        show(app, notice)
        prefs(app).edit().putStringSet(KEY_ANNOUNCED, notice.announced).apply()
        return false
    }

    private fun show(app: Context, notice: UpdateNotice) {
        val manager = notifications(app)
        if (notice.names.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, app.getString(R.string.mgmt_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            app,
            0,
            Intent(app, ManagementPreviewActivity::class.java)
                .putExtra(ManagementPreviewActivity.EXTRA_OPEN_STORE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = app.resources.getQuantityString(R.plurals.mgmt_updates_available, notice.names.size, notice.names.size)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(app, CHANNEL_ID)
        } else {
            android.app.Notification.Builder(app)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(notice.names.joinToString(", "))
            .setContentIntent(open)
            .setAutoCancel(true)
            // A version already announced is refreshed silently, never re-alerted.
            .setOnlyAlertOnce(true)
            .build()
        if (!notice.alert && !isShowing(manager)) return
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun isShowing(manager: NotificationManager): Boolean =
        manager.activeNotifications.any { it.id == NOTIFICATION_ID }

    private fun scheduler(context: Context) = context.getSystemService(JobScheduler::class.java)
    private fun notifications(context: Context) = context.getSystemService(NotificationManager::class.java)
}

/** Runs [UpdateReminders.runCheck] off the main thread; the system may stop it at any time. */
class UpdateCheckJobService : JobService() {
    private val stopped = AtomicBoolean(false)

    override fun onStartJob(params: JobParameters): Boolean {
        stopped.set(false)
        thread(name = "cll-update-check", isDaemon = true) {
            val retry = try {
                UpdateReminders.runCheck(this)
            } catch (_: RuntimeException) {
                true
            }
            if (!stopped.get()) jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped.set(true)
        return true
    }
}
