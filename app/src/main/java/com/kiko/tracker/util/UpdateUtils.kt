@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.util

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kiko.tracker.MainActivity
import com.kiko.tracker.R

fun settingsPrefs(context: Context) = context.getSharedPreferences("kiko_settings", Context.MODE_PRIVATE)

// App update notification

const val UPDATE_NOTIFICATION_CHANNEL = "app_updates"

const val UPDATE_NOTIFICATION_ID = 4201

fun ensureUpdateChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(UPDATE_NOTIFICATION_CHANNEL) != null) return
    manager.createNotificationChannel(
        NotificationChannel(UPDATE_NOTIFICATION_CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Lets you know when a new version of Kiko is ready to install" }
    )
}
// Tapping reopens the app

fun postUpdateNotification(context: Context, info: AppUpdateInfo) {
    ensureUpdateChannel(context)
    val openIntent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pendingIntent = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Kiko ${info.version} is available")
        .setContentText("Tap to update, from Profile > Check for updates.")
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
    }
}

// Activity section
