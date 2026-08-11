package com.kiko.tracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

// The Kiko support server's crash-reports channel — used as a fallback destination
// (and as the thing "Send to Discord" is actually pointing at) if the Discord app
// isn't installed to hand the log to directly.
const val CRASH_DISCORD_CHANNEL_URL = "https://discord.com/channels/871972731304951859/1536657318283055114"

fun copyCrashLogToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kiko crash log", text))
}

// Saves the crash text as a .txt file the person can find outside the app.
// API 29+ goes through MediaStore straight into the public Downloads folder (no
// permission needed). Below that, WRITE_EXTERNAL_STORAGE is a dangerous permission —
// rather than adding a whole request flow for that narrow, increasingly rare band of
// devices, this falls back to the app's own external files dir, which never needs a
// permission prompt but is still browsable from a file manager under
// Android/data/com.kiko.tracker/files/Download.
fun saveCrashLogToDownloads(context: Context, text: String): Result<String> = runCatching {
    val filename = "kiko-crash-${System.currentTimeMillis()}.txt"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Couldn't create the download entry")
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: throw IOException("Couldn't open the download for writing")
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Downloads/$filename"
    } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        File(downloadsDir, filename).also { it.writeText(text) }
        "Downloads/$filename"
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IOException("No storage available")
        if (!dir.exists()) dir.mkdirs()
        File(dir, filename).also { it.writeText(text) }
        "Android/data/${context.packageName}/files/Download/$filename"
    }
}

// Hands the crash log straight to the Discord app as a file attachment via the system
// share sheet, targeted directly at Discord so no chooser dialog is needed. If Discord
// isn't installed (or the share otherwise fails), falls back to copying the log to the
// clipboard and opening the crash-reports channel in a browser, so pasting it in is one
// tap away.
fun shareCrashLogToDiscord(context: Context, text: String) {
    val sent = runCatching {
        val file = File(context.cacheDir, "kiko-crash-share.txt")
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Kiko crash log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.discord")
        }
        context.startActivity(intent)
    }.isSuccess
    if (!sent) {
        copyCrashLogToClipboard(context, text)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CRASH_DISCORD_CHANNEL_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}