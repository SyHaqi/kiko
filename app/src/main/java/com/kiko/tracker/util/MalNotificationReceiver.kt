package com.kiko.tracker.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import com.kiko.tracker.data.api.MalSessionCookie

/**
 * Keeps MAL notification checks running while Kiko is closed.
 *
 * WorkManager's periodic job (15 min minimum) gets deferred for hours by Doze / battery savers.
 * This adds a second, independent trigger: an AlarmManager alarm that is allowed to fire in Doze
 * (about every 10 minutes at best) and kicks off an expedited check. Every alarm schedules the next
 * one, and the receiver also revives the chain after a reboot or an app update.
 */
object MalNotificationScheduler {
    const val ACTION_CHECK = "com.kiko.tracker.CHECK_MAL_NOTIFICATIONS"
    private const val ALARM_REQUEST_CODE = 7001
    private const val INTERVAL_MS = 10 * 60 * 1000L

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST_CODE,
        Intent(context, MalNotificationReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Idempotent: periodic WorkManager backup + the next alarm. */
    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        MalNotificationWorker.schedule(app)
        scheduleAlarm(app)
    }

    fun scheduleAlarm(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        // Inexact on purpose: needs no special permission, and still fires during Doze.
        runCatching {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + INTERVAL_MS, alarmIntent(app))
        }
    }

    fun cancelAll(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(app))
        MalNotificationWorker.cancel(app)
    }
}

class MalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!MalSessionCookie(app).has()) { MalNotificationScheduler.cancelAll(app); return }
        // Re-arm first, so one failed check never breaks the chain. Covers the alarm itself,
        // BOOT_COMPLETED and MY_PACKAGE_REPLACED (alarms don't survive either).
        MalNotificationScheduler.scheduleAll(app)
        MalNotificationWorker.runNow(app)
    }
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** Opens the system "Allow Kiko to run in the background?" dialog (falls back to the battery settings list). */
@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}