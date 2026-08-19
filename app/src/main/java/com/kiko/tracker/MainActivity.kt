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
        // Best-effort crash capture: if anything throws an uncaught exception
        // anywhere in the process (not just the UI thread), write it to a plain
        // file before the process dies, so it survives past the crash without
        // needing adb/Logcat hooked up. Delegates to the previous handler
        // afterward so normal OS crash/ANR behavior (and any crash reporting
        // tool that registers its own handler) still happens.
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
        // Must run before the very first NetworkClient.shared access below (and before any
        // API class gets constructed later in setContent{}) so the shared OkHttpClient picks
        // up a disk cache dir instead of building itself uncached.
        NetworkClient.init(this)
        // Register animated GIF decoders + Referer/UA for hotlink-protected images.
        // Built off the shared client (newBuilder()) so Coil's image loading reuses the
        // same connection pool/dispatcher as the rest of the app's networking instead of
        // spinning up its own.
        val forumImageClient = NetworkClient.shared.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                // Some MAL image links (older avatars/uploads, pasted forum links) are
                // still plain http://. Cleartext traffic is blocked by default since
                // API 28, and this app declares no networkSecurityConfig to allow it,
                // so those requests would otherwise fail before ever reaching the
                // server. Upgrade the scheme here so it covers every AsyncImage call
                // in the app (this client backs the single global Coil ImageLoader),
                // not just the forum-post body renderer.
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
                // Short fade-in on every AsyncImage in the app instead of a hard pop the
                // instant decode finishes — cheap (Coil does it as a drawable transition,
                // no extra measure/layout pass) and reads as noticeably smoother scrolling
                // through cover grids. Coil only applies this on an actual load (disk/
                // network); an image already sitting in memory cache still renders
                // instantly, so re-scrolling past something already-seen isn't slowed down.
                .crossfade(200)
                // Hardware bitmaps (the Coil/platform default) stay on globally now — cover
                // art, avatars, banners, and every other single-image spot in the app decode
                // and draw faster with them, and there's only ever one such image in flight
                // per card. The one real exhaustion risk is a forum post with a dozen+ small
                // reaction stickers decoding back-to-back against the GPU-driver-limited
                // hardware bitmap pool — that's scoped to allowHardware(false) locally, on
                // just the ForumImage composable that renders those, instead of paying the
                // software-decode cost for every image everywhere.
                .build()
        )
        routeIntentUri(intent?.data)
        // If the app crashed last run, show it now so it's easy to grab and
        // report, then clear it so it doesn't keep reappearing.
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        var crashText by mutableStateOf<String?>(if (crashFile.exists()) runCatching { crashFile.readText() }.getOrNull() else null)
        setContent {
            val vm: LibraryViewModel = viewModel()
            // Note: vm.loadHomeExtras() (Discover's "You might like" recommendations +
            // trending manga rows) is deliberately NOT kicked off here — Home never reads
            // either of those lists, so firing it at cold start just adds two more requests
            // competing for bandwidth with vm.load()/loadDiscoverBrowse() (which Home's
            // Continue card and Airing Next row actually wait on). DiscoverScreen already
            // calls it lazily itself, the first time that screen is actually opened.
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
            // Single shared shimmer clock for every skeleton placeholder in the app —
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
            // Shows once, right after a crash-and-relaunch, so the actual stack
            // trace is one tap away to copy/paste instead of needing adb.
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