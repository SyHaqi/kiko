package com.kiko.tracker.util

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

// The Kiko support server's
// (and as the thing
// isn't installed to hand
const val CRASH_DISCORD_CHANNEL_URL = "https://discord.com/channels/871972731304951859/1536657318283055114"

fun copyCrashLogToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kiko crash log", text))
}

// Saves the crash text
// API 29+ goes through
// permission needed). Below that,
// rather than adding a
// devices, this falls back
// permission prompt but is
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

// Hands the crash log
// share sheet, targeted directly
// isn't installed (or the
// clipboard and opening the
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