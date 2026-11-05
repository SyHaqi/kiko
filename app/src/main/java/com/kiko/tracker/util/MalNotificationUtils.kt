package com.kiko.tracker.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kiko.tracker.MainActivity
import com.kiko.tracker.R
import com.kiko.tracker.data.api.MalNotification

const val MAL_NOTIFICATION_CHANNEL = "mal_notifications"
const val MAL_NOTIFICATION_ID = 4202
const val EXTRA_OPEN_NOTIFICATIONS = "com.kiko.tracker.OPEN_NOTIFICATIONS"
const val MAL_NOTIFICATION_GROUP = "com.kiko.tracker.MAL_NOTIFICATIONS"
/** More new items than this at once only appear in the grouped summary, not as their own notification. */
const val MAX_INDIVIDUAL_NOTIFICATIONS = 5

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

    // Friend requests already answered from Kiko (inbox buttons or the notification's own buttons),
    // kept so an answered request never shows Accept / Deny again after a restart. id -> accepted?
    fun friendResponses(): Map<String, Boolean> =
        ids("responded").mapNotNull { e -> e.substringBefore('|').takeIf { it.isNotBlank() }?.let { it to (e.substringAfter('|', "1") == "1") } }.toMap()
    fun saveFriendResponse(notificationId: String, accepted: Boolean) {
        val kept = ids("responded").filterNot { it.substringBefore('|') == notificationId } + "$notificationId|${if (accepted) 1 else 0}"
        prefs.edit().putStringSet("responded", kept.sortedByDescending { it.substringBefore('|').toLongOrNull() ?: 0L }.take(500).toSet()).apply()
    }
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

/** Stable positive system-notification id for one MAL notification. */
fun malSystemNotificationId(notificationId: String): Int = (notificationId.hashCode() and 0x3FFFFFFF) + 10_000

fun cancelMalNotification(context: Context, notificationId: String) {
    NotificationManagerCompat.from(context).cancel(malSystemNotificationId(notificationId))
}

/** Status-bar icon for a notification, by kind (mirrors the row icons in the in-app inbox). */
@DrawableRes fun malNotificationIcon(n: MalNotification): Int = when {
    n.isFriendAccepted -> R.drawable.ic_stat_friend_accepted
    n.isFriendRelated -> R.drawable.ic_stat_friend_add
    n.type.contains("comment") || n.type.contains("quote") || n.type.contains("mention") || n.type.contains("watched") -> R.drawable.ic_stat_comment
    n.type.startsWith("club") -> R.drawable.ic_stat_club
    n.type == "on_air" || n.type == "related_anime_add" -> R.drawable.ic_stat_media
    else -> R.drawable.ic_stat_default
}

private fun openInboxIntent(context: Context, requestCode: Int): PendingIntent {
    val openIntent = Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
    return PendingIntent.getActivity(context, requestCode, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/**
 * One system notification per new item (up to [MAX_INDIVIDUAL_NOTIFICATIONS]), each with the icon
 * for its kind and, when [avatars] has one for it, the sender's avatar as the large icon. They're
 * bundled under a summary so a burst collapses into a single inbox-style entry; only the summary
 * alerts, so a burst makes one sound. Tapping any of them opens Kiko's notification inbox.
 */
fun postMalNotifications(context: Context, fresh: List<MalNotification>, avatars: Map<String, Bitmap> = emptyMap()) {
    if (fresh.isEmpty() || !canPostNotifications(context)) return
    ensureMalNotificationChannel(context)
    val manager = NotificationManagerCompat.from(context)
    fresh.take(MAX_INDIVIDUAL_NOTIFICATIONS).forEach { n ->
        // Positive, stable per-item id: re-announcing the same item replaces it instead of stacking.
        val id = malSystemNotificationId(n.id)
        val builder = NotificationCompat.Builder(context, MAL_NOTIFICATION_CHANNEL)
            .setSmallIcon(malNotificationIcon(n))
            .setContentTitle(n.title)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setGroup(MAL_NOTIFICATION_GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setContentIntent(openInboxIntent(context, id))
        n.body?.takeIf { it.isNotBlank() }?.let { builder.setContentText(it).setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
        avatars[n.id]?.let { builder.setLargeIcon(it) }
        manager.notify(id, builder.build())
    }
    val style = NotificationCompat.InboxStyle()
    fresh.take(6).forEach { style.addLine(it.title) }
    if (fresh.size > 6) style.setSummaryText("+${fresh.size - 6} more")
    val summary = NotificationCompat.Builder(context, MAL_NOTIFICATION_CHANNEL)
        .setSmallIcon(malNotificationIcon(fresh.first()))
        .setContentTitle(if (fresh.size == 1) fresh.first().title else "${fresh.size} new MyAnimeList notifications")
        .setContentText(fresh.first().title)
        .setStyle(style)
        .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        .setGroup(MAL_NOTIFICATION_GROUP)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        .setAutoCancel(true)
        .setContentIntent(openInboxIntent(context, 1))
    manager.notify(MAL_NOTIFICATION_ID, summary.build())
}