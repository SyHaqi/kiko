@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.kiko.tracker.data.api.MalApi
import com.kiko.tracker.data.api.NetworkClient
import com.kiko.tracker.navigation.KikoApp
import com.kiko.tracker.ui.components.CrashDialog
import com.kiko.tracker.ui.components.SkeletonPhaseProvider
import com.kiko.tracker.util.AppUpdateChecker
import com.kiko.tracker.util.AppUpdateInfo
import com.kiko.tracker.util.copyCrashLogToClipboard
import com.kiko.tracker.util.postUpdateNotification
import com.kiko.tracker.util.saveCrashLogToDownloads
import com.kiko.tracker.util.shareCrashLogToDiscord
import com.kiko.tracker.viewmodel.LibraryViewModel

class MainActivity : ComponentActivity() {
    private var callback by mutableStateOf<Uri?>(null)
    // Opened via MAL link
    private var malLink by mutableStateOf<Uri?>(null)
    // Pending update during permission
    private var pendingUpdateNotification: AppUpdateInfo? = null
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val info = pendingUpdateNotification; pendingUpdateNotification = null
        if (granted && info != null) { postUpdateNotification(this, info); AppUpdateChecker(this).markNotified(info.version) }
    }
    // Only for auto-check
    private fun notifyUpdateAvailable(info: AppUpdateInfo) {
        if (info.version == AppUpdateChecker(this).notifiedVersion()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingUpdateNotification = info
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        postUpdateNotification(this, info)
        AppUpdateChecker(this).markNotified(info.version)
    }
    private fun routeIntentUri(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme == "com.kiko.tracker") callback = uri else malLink = uri
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Best-effort crash capture: if
        // anywhere in the process
        // file before the process
        // needing adb/Logcat hooked up.
        // afterward so normal OS
        // tool that registers its
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = java.io.StringWriter().also { throwable.printStackTrace(java.io.PrintWriter(it)) }.toString()
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "Crashed at ${java.util.Date()} on thread ${thread.name}\n\n$trace"
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        // Must run before the
        // API class gets constructed
        // up a disk cache
        NetworkClient.init(this)
        // Register animated GIF decoders
        // Built off the shared
        // same connection pool/dispatcher as
        // spinning up its own.
        val forumImageClient = NetworkClient.shared.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                // Some MAL image links
                // still plain http://. Cleartext
                // API 28, and this
                // so those requests would
                // server. Upgrade the scheme
                // in the app (this
                // not just the forum-post
                val url = if (original.url.scheme == "http") original.url.newBuilder().scheme("https").build() else original.url
                val req = original.newBuilder()
                    .url(url)
                    .header("Referer", "https://myanimelist.net/")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                    .build()
                chain.proceed(req)
            }
            .build()
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(forumImageClient)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(coil.decode.GifDecoder.Factory())
                }
                // Short fade-in on every
                // instant decode finishes —
                // no extra measure/layout pass)
                // through cover grids. Coil
                // network); an image already
                // instantly, so re-scrolling past
                .crossfade(200)
                // Hardware bitmaps (the Coil/platform
                // art, avatars, banners, and
                // and draw faster with
                // per card. The one
                // reaction stickers decoding back-to-back
                // hardware bitmap pool —
                // just the ForumImage composable
                // software-decode cost for every
                .build()
        )
        routeIntentUri(intent?.data)
        // If the app crashed
        // report, then clear it
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        var crashText by mutableStateOf<String?>(if (crashFile.exists()) runCatching { crashFile.readText() }.getOrNull() else null)
        setContent {
            val vm: LibraryViewModel = viewModel()
            // Note: vm.loadHomeExtras() (Discover's "You
            // trending manga rows) is
            // either of those lists,
            // competing for bandwidth with
            // Continue card and Airing
            // calls it lazily itself,
            LaunchedEffect(Unit) { vm.loadTheme(this@MainActivity); vm.loadColorSource(this@MainActivity); vm.loadPaletteStyle(this@MainActivity); vm.loadCustomColor(this@MainActivity); vm.loadTitleLanguage(this@MainActivity); vm.loadListFilter(this@MainActivity); vm.loadListTypeTab(this@MainActivity); vm.loadCommunityTab(this@MainActivity); vm.loadListSort(this@MainActivity); vm.loadListViewMode(this@MainActivity); vm.loadScoreFilterViewMode(this@MainActivity); vm.loadScoreFilterSort(this@MainActivity); vm.loadYearFilterViewMode(this@MainActivity); vm.loadYearFilterSort(this@MainActivity); vm.loadFormatFilterViewMode(this@MainActivity); vm.loadFormatFilterSort(this@MainActivity); vm.loadGenreFilterViewMode(this@MainActivity); vm.loadGenreFilterSort(this@MainActivity); vm.loadNsfwPref(this@MainActivity); vm.loadAmoledDark(this@MainActivity); vm.load(this@MainActivity); vm.loadDiscoverBrowse(this@MainActivity) }
            // Throttled background update check
            LaunchedEffect(Unit) {
                vm.loadCachedUpdate(this@MainActivity)
                val staleAfterMs = 12 * 60 * 60 * 1000L
                if (System.currentTimeMillis() - AppUpdateChecker(this@MainActivity).lastCheckedAt() > staleAfterMs) {
                    vm.checkForUpdate(this@MainActivity, manual = false, onFound = ::notifyUpdateAvailable)
                }
            }
            LaunchedEffect(callback) {
                callback?.let { uri ->
                    vm.loading = true
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        // Reload full homepage
                        MalApi(this@MainActivity).finishAuth(uri).onSuccess { vm.load(this@MainActivity); vm.loadDiscoverBrowse(this@MainActivity) }.onFailure { vm.error = it.message }
                        vm.loading = false
                    }
                    callback = null
                }
            }
            // Single shared shimmer clock
            // see SkeletonPhaseProvider in CommonComponents.kt.
            SkeletonPhaseProvider {
                KikoApp(
                    vm,
                    onSignIn = { if (BuildConfig.MAL_CLIENT_ID.isBlank()) vm.error = "Add your MAL Client ID to local.properties first" else CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, Uri.parse(MalApi(this@MainActivity).authUrl())) },
                    onSignOut = { vm.signOut(this@MainActivity) },
                    malLink = malLink,
                    onMalLinkHandled = { malLink = null },
                )
            }
            // Shows once, right after
            // trace is one tap
            crashText?.let { text ->
                CrashDialog(
                    crashText = text,
                    onDismiss = { crashText = null; crashFile.delete() },
                    onCopy = { copyCrashLogToClipboard(this@MainActivity, text) },
                    onDownload = {
                        saveCrashLogToDownloads(this@MainActivity, text)
                            .onSuccess { android.widget.Toast.makeText(this@MainActivity, "Saved to $it", android.widget.Toast.LENGTH_LONG).show() }
                            .onFailure { android.widget.Toast.makeText(this@MainActivity, "Couldn't save the file — try Copy instead", android.widget.Toast.LENGTH_LONG).show() }
                    },
                    onSendDiscord = { shareCrashLogToDiscord(this@MainActivity, text) },
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); routeIntentUri(intent.data) }
}

// Sync system bars theme