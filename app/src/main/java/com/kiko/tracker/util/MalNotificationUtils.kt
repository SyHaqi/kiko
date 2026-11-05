package com.kiko.tracker.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kiko.tracker.MainActivity
import com.kiko.tracker.data.api.MalNotification

const val MAL_NOTIFICATION_CHANNEL = "mal_notifications"
const val MAL_NOTIFICATION_ID = 4202
const val EXTRA_OPEN_NOTIFICATIONS = "com.kiko.tracker.OPEN_NOTIFICATIONS"

/**
 * Remembers which MAL notifications have already been pushed / seen, so the same item is never
 * announced twice and the in-app badge clears once the inbox has been opened.
 *
 * MAL's own "mark as read" call isn't part of what's been captured, so read-state here is local:
 * [unreadCount] = items that haven't been tapped open in Kiko's inbox yet (see [syncRead]).
 */
class MalNotificationTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kiko_mal_notifications", Context.MODE_PRIVATE)

    private fun ids(key: String): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()
    private fun putIds(key: String, v: Set<String>, cap: Int = 300) {
        // Cap growth: ids are numeric and monotonically increasing, keep the newest [cap].
        val trimmed = v.sortedByDescending { it.toLongOrNull() ?: 0L }.take(cap).toSet()
        prefs.edit().putStringSet(key, trimmed).apply()
    }

    /** True until the first successful fetch has seeded the baseline (so old items don't all push at once). */
    fun isFirstRun(): Boolean = !prefs.contains("pushed")

    /** First fetch: treat everything currently there as already announced (the badge still counts unread ones). */
    fun seedPushedOnly(all: List<MalNotification>) { putIds("pushed", all.map { it.id }.toSet()) }

    /** Unread items never pushed before. Marks them pushed. */
    fun takeNewForPush(all: List<MalNotification>): List<MalNotification> {
        val pushed = ids("pushed")
        val fresh = all.filter { !it.isRead && it.id !in pushed }
        if (fresh.isNotEmpty()) putIds("pushed", pushed + fresh.map { it.id })
        return fresh
    }

    // Per-item read state, local (MAL's own mark-as-read call was never captured). MAL's read flag
    // only seeds an item the first time Kiko sees it; after that only a tap in Kiko changes it, so
    // e.g. accepting a friend request (which MAL marks read) doesn't clear it from "New".
    fun syncRead(all: List<MalNotification>) {
        val known = ids("known")
        val fresh = all.filter { it.id !in known }
        if (fresh.isEmpty()) return
        val read = ids("read") + fresh.filter { it.isRead || it.isHistory }.map { it.id }
        putIds("known", known + fresh.map { it.id }, 1000)
        putIds("read", read, 1000)
    }
    fun readIds(): Set<String> = ids("read")
    fun markRead(notificationIds: Collection<String>) { putIds("read", ids("read") + notificationIds, 1000) }
    fun unreadCount(all: List<MalNotification>): Int { val read = ids("read"); return all.count { it.id !in read } }

    var lastCheckedAt: Long
        get() = prefs.getLong("last_checked", 0L)
        set(v) { prefs.edit().putLong("last_checked", v).apply() }

    var permissionAsked: Boolean
        get() = prefs.getBoolean("permission_asked", false)
        set(v) { prefs.edit().putBoolean("permission_asked", v).apply() }
}

fun ensureMalNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(MAL_NOTIFICATION_CHANNEL) != null) return
    manager.createNotificationChannel(
        NotificationChannel(MAL_NOTIFICATION_CHANNEL, "MyAnimeList notifications", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Friend requests, accepted requests, comments and other MAL activity" }
    )
}

fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** One notification per new item when there's a single one, otherwise a grouped inbox summary. */
fun postMalNotifications(context: Context, fresh: List<MalNotification>) {
    if (fresh.isEmpty() || !canPostNotifications(context)) return
    ensureMalNotificationChannel(context)
    val openIntent = Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
    val pending = PendingIntent.getActivity(context, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder = NotificationCompat.Builder(context, MAL_NOTIFICATION_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setAutoCancel(true)
        .setContentIntent(pending)
    if (fresh.size == 1) {
        val n = fresh.first()
        builder.setContentTitle(n.title).also { b -> n.body?.let { b.setContentText(it) } }
    } else {
        val style = NotificationCompat.InboxStyle()
        fresh.take(6).forEach { style.addLine(it.title) }
        if (fresh.size > 6) style.setSummaryText("+${fresh.size - 6} more")
        builder.setContentTitle("${fresh.size} new MyAnimeList notifications")
            .setContentText(fresh.first().title)
            .setStyle(style)
    }
    NotificationManagerCompat.from(context).notify(MAL_NOTIFICATION_ID, builder.build())
}