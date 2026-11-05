package com.kiko.tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kiko.tracker.data.api.MAL_DESKTOP_USER_AGENT
import com.kiko.tracker.data.api.MalFriendApi
import com.kiko.tracker.data.api.MalNotification
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.api.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Checks MyAnimeList for new notifications while Kiko is closed and posts them as system
 * notifications (see [postMalNotifications]) — the same check the open app does every 5 minutes,
 * run by WorkManager so it also happens in the background and survives reboots.
 *
 * MAL has no push service, so this is polling: Android won't run periodic work more often than
 * every 15 minutes, and Doze / battery optimisation can stretch that further. It needs the MAL web
 * session (the same login the inbox uses); with no session it does nothing and unschedules itself.
 */
class MalNotificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val session = MalSessionCookie(context)
        if (!session.has()) { cancel(context); return Result.success() }
        // Must run before anything touches NetworkClient.shared (the process may have been started
        // just for this worker, i.e. without MainActivity.onCreate having run).
        NetworkClient.init(context)
        val tracker = MalNotificationTracker(context)
        return try {
            val all = MalFriendApi(context).notifications()
            tracker.lastCheckedAt = System.currentTimeMillis()
            if (tracker.isFirstRun()) {
                // Baseline only: never announce the whole backlog.
                tracker.seedPushedOnly(all)
            } else {
                val fresh = tracker.takeNewForPush(all)
                if (fresh.isNotEmpty()) {
                    val avatars = loadAvatars(fresh.take(MAX_INDIVIDUAL_NOTIFICATIONS))
                    postMalNotifications(context, fresh, avatars)
                }
            }
            Result.success()
        } catch (e: MalSessionExpired) {
            session.clear()
            cancel(context)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline / MAL hiccup: just try again on the next period.
            Result.success()
        }
    }

    private suspend fun loadAvatars(items: List<MalNotification>): Map<String, Bitmap> = withContext(Dispatchers.IO) {
        val out = HashMap<String, Bitmap>()
        for (n in items) {
            val url = n.avatarUrl?.takeIf { it.isNotBlank() } ?: continue
            loadAvatar(url)?.let { out[n.id] = it }
        }
        out
    }

    private fun loadAvatar(url: String): Bitmap? = runCatching {
        val request = Request.Builder().url(url.replaceFirst("http://", "https://"))
            .header("Referer", "https://myanimelist.net/")
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .build()
        val client = NetworkClient.shared.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
        client.newCall(request).execute().use { resp ->
            val bytes = if (resp.isSuccessful) resp.body?.bytes() else null
            val raw = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            raw?.let { circleCrop(it, AVATAR_SIZE_PX) }
        }
    }.getOrNull()

    private fun circleCrop(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val from = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        canvas.drawBitmap(src, from, Rect(0, 0, size, size), paint)
        return out
    }

    companion object {
        private const val UNIQUE_NAME = "mal_notification_check"
        private const val AVATAR_SIZE_PX = 128

        /** Idempotent: keeps the existing schedule if there already is one. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MalNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}