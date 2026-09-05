package com.kiko.tracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import com.kiko.tracker.BuildConfig
import com.kiko.tracker.data.api.NetworkClient

// Check GitHub for updates
private const val UPDATE_REPO = "SyHaqi/kiko"
private const val UPDATE_API = "https://api.github.com/repos/$UPDATE_REPO/releases/latest"

// Update info for UI
data class AppUpdateInfo(
    val version: String,
    val notes: String,
    val htmlUrl: String,
    val downloadUrl: String,
    val apkSizeBytes: Long,
)

// Compare version numbers
private fun parseVersion(raw: String): List<Int> =
    raw.removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
private fun isNewerVersion(remote: String, local: String): Boolean {
    val r = parseVersion(remote); val l = parseVersion(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val rv = r.getOrElse(i) { 0 }; val lv = l.getOrElse(i) { 0 }
        if (rv != lv) return rv > lv
    }
    return false
}

class AppUpdateChecker(private val context: Context) {
    private val client = NetworkClient.shared
    private val prefs = context.getSharedPreferences("kiko_update", Context.MODE_PRIVATE)

    // Cache last check
    fun cached(): AppUpdateInfo? {
        val version = prefs.getString("cached_version", null) ?: return null
        val downloadUrl = prefs.getString("cached_download_url", null) ?: return null
        return AppUpdateInfo(
            version = version,
            notes = prefs.getString("cached_notes", "") ?: "",
            htmlUrl = prefs.getString("cached_html_url", "") ?: "",
            downloadUrl = downloadUrl,
            apkSizeBytes = prefs.getLong("cached_size", 0L),
        )
    }
    private fun cache(info: AppUpdateInfo?) {
        prefs.edit().apply {
            if (info == null) { remove("cached_version"); remove("cached_download_url"); remove("cached_notes"); remove("cached_html_url"); remove("cached_size") }
            else { putString("cached_version", info.version); putString("cached_download_url", info.downloadUrl); putString("cached_notes", info.notes); putString("cached_html_url", info.htmlUrl); putLong("cached_size", info.apkSizeBytes) }
            putLong("last_checked_at", System.currentTimeMillis())
        }.apply()
    }
    fun lastCheckedAt(): Long = prefs.getLong("last_checked_at", 0L)
    // Clear stale cached update
    fun clearCache() = cache(null)
    // Still newer than build
    fun isStillNewer(version: String): Boolean = isNewerVersion(version, BuildConfig.VERSION_NAME)
    fun skippedVersion(): String? = prefs.getString("skipped_version", null)
    fun skipVersion(version: String) { prefs.edit().putString("skipped_version", version).apply() }
    // Already notified flag
    fun notifiedVersion(): String? = prefs.getString("notified_version", null)
    fun markNotified(version: String) { prefs.edit().putString("notified_version", version).apply() }

    // Check for update
    suspend fun checkLatest(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) { runCatching {
        val request = Request.Builder().url(UPDATE_API).header("Accept", "application/vnd.github+json").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub returned HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            if (tag.isBlank()) throw IOException("Release had no tag")
            val assets = json.optJSONArray("assets")
            var apkUrl = ""; var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url"); apkSize = asset.optLong("size", 0L); break
                    }
                }
            }
            val info = AppUpdateInfo(
                version = tag,
                notes = json.optString("body").trim(),
                htmlUrl = json.optString("html_url"),
                downloadUrl = apkUrl,
                apkSizeBytes = apkSize,
            )
            val newer = isNewerVersion(info.version, BuildConfig.VERSION_NAME) && info.downloadUrl.isNotBlank()
            cache(if (newer) info else null)
            if (newer) info else null
        }
    } }

    // Download APK with progress
    suspend fun downloadApk(info: AppUpdateInfo, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) { runCatching {
        val target = File(context.cacheDir, "kiko-update.apk")
        if (target.exists()) target.delete()
        val request = Request.Builder().url(info.downloadUrl).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty download response")
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var readSoFar = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readSoFar += read
                        if (total > 0) onProgress((readSoFar.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        target
    } }

    // Can request install?
    fun canRequestInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    // Open install permission settings
    fun installPermissionSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    // Install via FileProvider
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }
}