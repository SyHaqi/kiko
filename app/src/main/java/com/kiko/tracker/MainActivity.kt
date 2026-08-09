@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.math.roundToInt

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
        // Register animated GIF decoders + Referer/UA for hotlink-protected images
        val forumImageClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
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
                .build()
        )
        routeIntentUri(intent?.data)
        setContent {
            val vm: LibraryViewModel = viewModel()
            LaunchedEffect(Unit) { vm.loadTheme(this@MainActivity); vm.loadColorSource(this@MainActivity); vm.loadPaletteStyle(this@MainActivity); vm.loadCustomColor(this@MainActivity); vm.loadTitleLanguage(this@MainActivity); vm.loadListFilter(this@MainActivity); vm.loadListSort(this@MainActivity); vm.loadListViewMode(this@MainActivity); vm.loadNsfwPref(this@MainActivity); vm.load(this@MainActivity); vm.loadDiscoverBrowse(this@MainActivity); vm.loadHomeExtras(this@MainActivity) }
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
                        MalApi(this@MainActivity).finishAuth(uri).onSuccess { vm.load(this@MainActivity); vm.loadDiscoverBrowse(this@MainActivity); vm.loadHomeExtras(this@MainActivity) }.onFailure { vm.error = it.message }
                        vm.loading = false
                    }
                    callback = null
                }
            }
            KikoApp(
                vm,
                onSignIn = { if (BuildConfig.MAL_CLIENT_ID.isBlank()) vm.error = "Add your MAL Client ID to local.properties first" else CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, Uri.parse(MalApi(this@MainActivity).authUrl())) },
                onSignOut = { vm.signOut(this@MainActivity) },
                malLink = malLink,
                onMalLinkHandled = { malLink = null },
            )
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); routeIntentUri(intent.data) }
}

// Sync system bars theme