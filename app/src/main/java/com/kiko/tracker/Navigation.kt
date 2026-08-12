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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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

@Composable

fun SyncSystemBars(darkTheme: Boolean, background: Color) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        SideEffect {
            val window = activity?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            window.statusBarColor = background.toArgb()
            window.navigationBarColor = background.toArgb()
        }
    }
}

// Shared navigation transition motion

// Shared navigation transition motion — push/pop get a faint scale on top of the
// fade+slide so the screen underneath reads as physically receding/advancing
// (matching the depth cue Android's own activity transitions use), not just sliding.

val PushEnter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 4 }

val PushExit = fadeOut(tween(150)) + scaleOut(tween(220), targetScale = .96f)

val PopEnter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = .96f)

val PopExit = fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { it / 4 }

val FadeEnter = fadeIn(tween(220))

val FadeExit = fadeOut(tween(150))

// Top-level navigation state
sealed class TopScreen {
    data class Detail(val item: MediaItem) : TopScreen()
    object Ranking : TopScreen()
    object Seasonal : TopScreen()
    // Full grid of "You might like" recommendations
    object Recommendations : TopScreen()
    // Seed initial schedule day
    data class Schedule(val initialDay: java.time.DayOfWeek) : TopScreen()
    // Reading single forum topic
    data class Topic(val topicId: Int, val title: String) : TopScreen()
    // App info page
    object About : TopScreen()
    // Full review readout
    data class Review(val review: ReviewEntry, val itemTitle: String) : TopScreen()
    // Reviews page in webview
    // Interest stacks homepage — curated challenge/manga/anime picks + recent list
    object StacksHome : TopScreen()
    // Interest stacks full browse/search, seeded with a starting tab
    data class StacksBrowse(val initialKind: StackBrowseKind) : TopScreen()
    // One stack's entries
    data class StackDetail(val stackId: Int, val title: String) : TopScreen()
    // Single club page
    data class ClubDetail(val club: MalClub) : TopScreen()
    // Full pages opened from the profile drawer
    object ProfileStats : TopScreen()
    object SettingsPage : TopScreen()
    // Titles at one score, opened by tapping a bar in the profile's score distribution chart
    data class ScoreFilter(val type: MediaType, val score: Int) : TopScreen()
    // Titles released in one year, opened by tapping a bar in the profile's year distribution chart
    data class YearFilter(val type: MediaType, val year: Int) : TopScreen()
    data class Tab(val destination: Destination) : TopScreen()
}
// Same screen vs navigation

fun TopScreen.navKey(): Any = when (this) {
    is TopScreen.Detail -> "detail:${item.id}"
    TopScreen.Ranking -> "ranking"
    TopScreen.Seasonal -> "seasonal"
    TopScreen.Recommendations -> "recommendations"
    is TopScreen.Schedule -> "schedule"
    is TopScreen.Topic -> "topic:$topicId"
    TopScreen.About -> "about"
    is TopScreen.Review -> "review:${review.malId}"
    TopScreen.StacksHome -> "stacksHome"
    is TopScreen.StacksBrowse -> "stacksBrowse"
    is TopScreen.StackDetail -> "stackDetail:$stackId"
    is TopScreen.ClubDetail -> "clubDetail:${club.id}"
    TopScreen.ProfileStats -> "profileStats"
    TopScreen.SettingsPage -> "settingsPage"
    is TopScreen.ScoreFilter -> "scoreFilter:$type:$score"
    is TopScreen.YearFilter -> "yearFilter:$type:$year"
    is TopScreen.Tab -> "tab:$destination"
}

fun TopScreen.isFullPage() = this is TopScreen.Detail || this is TopScreen.Ranking || this is TopScreen.Seasonal || this is TopScreen.Recommendations || this is TopScreen.Schedule || this is TopScreen.Topic || this is TopScreen.About || this is TopScreen.Review || this is TopScreen.StacksHome || this is TopScreen.StacksBrowse || this is TopScreen.StackDetail || this is TopScreen.ClubDetail || this is TopScreen.ProfileStats || this is TopScreen.SettingsPage || this is TopScreen.ScoreFilter || this is TopScreen.YearFilter


@Composable fun KikoApp(vm: LibraryViewModel = viewModel(), onSignIn: () -> Unit = {}, onSignOut: () -> Unit = {}, malLink: Uri? = null, onMalLinkHandled: () -> Unit = {}) {
    val context = LocalContext.current
    var editor by remember { mutableStateOf<MediaItem?>(null) }; var themeOpen by remember { mutableStateOf(false) }; var titleLangOpen by remember { mutableStateOf(false) }
    var colorSourceOpen by remember { mutableStateOf(false) }; var paletteStyleOpen by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    // Related title navigation stack
    var detailStack by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    // Direction hint for the AnimatedContent transitionSpec below: detail-to-detail
    // hops (tapping a related/recommended card, or backing out of one) are both
    // "isFullPage() on both sides" and would otherwise fall through to a flat
    // cross-fade with no sense of depth. Set right before selectedItem changes so
    // it's already up to date by the time topScreen recomposes.
    var detailGoingBack by remember { mutableStateOf(false) }
    // Interest stacks nav state: home -> browse (seeded tab) -> detail. Declared up here
    // (rather than alongside the other overlay flags below) because backDetail()'s
    // Discover-detour restoration needs to write to stackDetailOpen.
    var stacksHomeOpen by remember { mutableStateOf(false) }
    var stacksBrowseKind by remember { mutableStateOf<StackBrowseKind?>(null) }
    var stackDetailOpen by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // Item to return to when backing out of a genre-chip/creator-tap jump to Discover
    var discoverReturnItem by remember { mutableStateOf<MediaItem?>(null) }
    // Where the detour started — the tab (My List, Home, ...) and, if applicable, the
    // specific stack detail screen — so backing all the way out of the detour lands
    // back where the user actually was, not on the Discover tab they detoured through.
    // Set once per detour (first jump only — see jumpToDiscover) and only cleared once
    // the whole detour is fully backed out of (see backDetail) or abandoned via the
    // bottom nav bar.
    var discoverReturnDestination by remember { mutableStateOf<Destination?>(null) }
    var discoverReturnStack by remember { mutableStateOf<Pair<Int, String>?>(null) }
    fun openDetail(item: MediaItem) {
        // Always the start of a brand-new detail chain (a list row, search result,
        // stack entry, etc. — never a step within an existing related/recommended
        // chain, that's openRelatedDetail below). Any previously cached chain is now
        // unreachable, so drop it rather than let it linger in memory.
        vm.clearDetailCache()
        detailGoingBack = false
        detailStack = emptyList()
        // A title opened while a Discover detour is still in progress (e.g. tapping
        // another result on the author's search page) is just drilling further into
        // that same detour — keep the breadcrumbs so backing out eventually still
        // returns to the real origin instead of the Discover tab. Only a genuinely
        // fresh open (no detour active) should discard them.
        if (discoverReturnDestination == null) discoverReturnItem = null
        selectedItem = item
    }
    fun openRelatedDetail(from: MediaItem, to: MediaItem) { detailGoingBack = false; detailStack = detailStack + from; selectedItem = to }
    fun backDetail() {
        val prev = detailStack.lastOrNull()
        if (prev != null) { detailGoingBack = true; selectedItem = prev; detailStack = detailStack.dropLast(1); return }
        // Backing out from the root of the related/recommended chain — the whole chain
        // is now unreachable, so drop everything cached for it (see clearDetailCache()).
        vm.clearDetailCache()
        selectedItem = null
        // Only restore once the detour's own "return to origin item" stop has already
        // been consumed (discoverReturnItem null) — otherwise this was just closing an
        // intermediate title opened mid-detour, which should reveal the search results
        // underneath, not jump straight back to the origin tab/screen.
        val destination = discoverReturnDestination
        if (discoverReturnItem == null && destination != null) {
            vm.destination = destination
            stackDetailOpen = discoverReturnStack
            discoverReturnDestination = null; discoverReturnStack = null
        }
    }
    // Handle tapped MAL link
    LaunchedEffect(malLink) {
        val uri = malLink ?: return@LaunchedEffect
        parseMalDeepLink(uri)?.let { (id, type) ->
            vm.loading = true
            runCatching { MalApi(context).detail(id, type) }
                .onSuccess { openDetail(it) }
                .onFailure { vm.error = it.message ?: "Could not load that MAL link" }
            vm.loading = false
        }
        onMalLinkHandled()
    }
    // Home full-screen destinations
    var rankingOpen by remember { mutableStateOf(false) }
    var seasonalOpen by remember { mutableStateOf(false) }
    var recommendationsOpen by remember { mutableStateOf(false) }
    // Schedule day to open
    var scheduleOpen by remember { mutableStateOf(false) }
    var scheduleInitialDay by remember { mutableStateOf(java.time.LocalDate.now().dayOfWeek) }
    fun openSchedule(day: java.time.DayOfWeek) { scheduleInitialDay = day; scheduleOpen = true }
    // Forum topic screen state
    var forumTopicOpen by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // About page open state
    var aboutOpen by remember { mutableStateOf(false) }
    // Full review readout state
    var reviewOpen by remember { mutableStateOf<Pair<ReviewEntry, String>?>(null) }
    // Reviews webview state
    fun openStacks() { stackDetailOpen = null; stacksBrowseKind = null; stacksHomeOpen = true }
    fun openStacksBrowse(kind: StackBrowseKind) { stackDetailOpen = null; stacksBrowseKind = kind }
    // Club detail state
    var clubDetailOpen by remember { mutableStateOf<MalClub?>(null) }
    // Full pages opened from the profile drawer
    var profileStatsOpen by remember { mutableStateOf(false) }
    var settingsPageOpen by remember { mutableStateOf(false) }
    // Score distribution drill-down, opened over ProfileStats
    var scoreFilterOpen by remember { mutableStateOf<Pair<MediaType, Int>?>(null) }
    // Year distribution drill-down, opened over ProfileStats — same shape as scoreFilterOpen above
    var yearFilterOpen by remember { mutableStateOf<Pair<MediaType, Int>?>(null) }
    // Jump from a detail page (genre chip / creator tap) to Discover search results.
    // Clears every other overlay screen's "open" state, not just detailStack — a detail
    // page can be reached from inside a stack, club, forum topic, review, etc., and each
    // of those leaves its own flag (stackDetailOpen, clubDetailOpen, ...) set underneath.
    // topScreen's priority chain checks those flags ahead of the plain destination, so
    // once selectedItem is cleared below, a leftover flag would resurface that screen
    // instead of the Discover results — e.g. tapping an author from an entry opened out
    // of a stack would fall back to the stack's entry list instead of showing the search.
    fun jumpToDiscover(from: MediaItem, type: String, filters: DiscoverFilters) {
        // Only remember the true origin (tab + stack screen) on the first hop of a
        // detour — a jump launched from inside an already-active detour (e.g. tapping
        // another creator link while already browsing search results) must not
        // overwrite it with the Discover tab we're currently sitting in.
        if (discoverReturnDestination == null) {
            discoverReturnDestination = vm.destination
            discoverReturnStack = stackDetailOpen
        }
        discoverReturnItem = from
        vm.clearDetailCache()
        selectedItem = null; detailStack = emptyList()
        stackDetailOpen = null; stacksBrowseKind = null; stacksHomeOpen = false
        clubDetailOpen = null
        rankingOpen = false; seasonalOpen = false; recommendationsOpen = false; scheduleOpen = false
        forumTopicOpen = null; aboutOpen = false; reviewOpen = null
        profileStatsOpen = false; settingsPageOpen = false; scoreFilterOpen = null; yearFilterOpen = null
        vm.destination = Destination.Discover
        vm.runDiscoverSearch(context, "", type, filters)
    }
    // Live-merge search result — must match on id AND type, since MAL anime and
    // manga ids are separate numbering spaces and can collide (e.g. anime id 11577 is
    // Steins;Gate Movie: Fuka Ryouiki no Déjà vu, manga id 11577 is Stardust★Wink)
    val editorItem = editor?.let { ed -> vm.visibleItems.find { it.id == ed.id && it.type == ed.type } ?: vm.items.find { it.id == ed.id && it.type == ed.type } ?: ed }
    // Prefer live item copy — same id+type requirement as above
    val detailItem = selectedItem?.let { sel -> vm.items.find { it.id == sel.id && it.type == sel.type } ?: sel }
    // Back press returns home
    BackHandler(enabled = detailItem == null && !rankingOpen && !seasonalOpen && !recommendationsOpen && !scheduleOpen && forumTopicOpen == null && !aboutOpen && reviewOpen == null && !stacksHomeOpen && stacksBrowseKind == null && stackDetailOpen == null && clubDetailOpen == null && !profileStatsOpen && !settingsPageOpen && scoreFilterOpen == null && yearFilterOpen == null && (vm.destination != Destination.Home || discoverReturnItem != null)) {
        val returnItem = discoverReturnItem
        if (returnItem != null && vm.destination == Destination.Discover) {
            discoverReturnItem = null
            selectedItem = returnItem
        } else {
            vm.destination = Destination.Home
        }
    }
    val darkTheme = when (vm.themeMode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    // Default palette uses constants
    val c = remember(darkTheme, vm.colorSource, vm.paletteStyle, vm.customColorHex, vm.amoledDark) {
        val base = if (vm.colorSource == ColorSource.AppDefault && vm.paletteStyle == PaletteStyle.TonalSpot) {
            if (darkTheme) DarkKiko else LightKiko
        } else {
            themedPalette(resolveSeedColor(context, vm.colorSource, vm.customColorHex, darkTheme), vm.paletteStyle, darkTheme)
        }
        if (darkTheme && vm.amoledDark) amoledify(base) else base
    }
    SyncSystemBars(darkTheme, c.background)
    CompositionLocalProvider(LocalKikoColors provides c, LocalTitleLanguage provides vm.titleLanguage) {
        MaterialTheme(
            colorScheme = if (darkTheme)
                darkColorScheme(primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryContainer, background = c.background, surface = c.surface, onBackground = c.ink, onSurface = c.ink)
            else
                lightColorScheme(primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryContainer, background = c.background, surface = c.surface, onBackground = c.ink, onSurface = c.ink),
            typography = Typography(
                displaySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp),
                headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 24.sp),
                titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 21.sp),
                titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                bodyMedium = TextStyle(fontFamily = AppFont, fontSize = 14.sp)
            )
        ) {
            Scaffold(
                containerColor = c.background,
                bottomBar = { if (detailItem == null && !rankingOpen && !seasonalOpen && !recommendationsOpen && !scheduleOpen && forumTopicOpen == null && !aboutOpen && reviewOpen == null && !stacksHomeOpen && stacksBrowseKind == null && stackDetailOpen == null && clubDetailOpen == null && !profileStatsOpen && !settingsPageOpen && scoreFilterOpen == null && yearFilterOpen == null) BottomBar(vm.destination) { discoverReturnItem = null; discoverReturnDestination = null; discoverReturnStack = null; vm.destination = it } }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val topScreen = when {
                        reviewOpen != null -> TopScreen.Review(reviewOpen!!.first, reviewOpen!!.second)
                        detailItem != null -> TopScreen.Detail(detailItem)
                        rankingOpen -> TopScreen.Ranking
                        seasonalOpen -> TopScreen.Seasonal
                        recommendationsOpen -> TopScreen.Recommendations
                        scheduleOpen -> TopScreen.Schedule(scheduleInitialDay)
                        stackDetailOpen != null -> TopScreen.StackDetail(stackDetailOpen!!.first, stackDetailOpen!!.second)
                        stacksBrowseKind != null -> TopScreen.StacksBrowse(stacksBrowseKind!!)
                        stacksHomeOpen -> TopScreen.StacksHome
                        clubDetailOpen != null -> TopScreen.ClubDetail(clubDetailOpen!!)
                        aboutOpen -> TopScreen.About
                        scoreFilterOpen != null -> TopScreen.ScoreFilter(scoreFilterOpen!!.first, scoreFilterOpen!!.second)
                        yearFilterOpen != null -> TopScreen.YearFilter(yearFilterOpen!!.first, yearFilterOpen!!.second)
                        profileStatsOpen -> TopScreen.ProfileStats
                        settingsPageOpen -> TopScreen.SettingsPage
                        forumTopicOpen != null -> TopScreen.Topic(forumTopicOpen!!.first, forumTopicOpen!!.second)
                        else -> TopScreen.Tab(vm.destination)
                    }
                    AnimatedContent(
                        targetState = topScreen,
                        contentKey = { it.navKey() },
                        transitionSpec = {
                            when {
                                targetState.isFullPage() && !initialState.isFullPage() -> PushEnter togetherWith PushExit
                                !targetState.isFullPage() && initialState.isFullPage() -> PopEnter togetherWith PopExit
                                // Related/recommended hops: both sides are TopScreen.Detail, so
                                // the two branches above don't fire and this used to fall through
                                // to a flat cross-fade with no sense of direction — tapping into a
                                // related title and then backing out both looked like the same
                                // abrupt "pop". Give it the same push (going deeper) / pop (coming
                                // back) motion the rest of the app uses, driven by detailGoingBack.
                                targetState is TopScreen.Detail && initialState is TopScreen.Detail ->
                                    if (detailGoingBack) PopEnter togetherWith PopExit else PushEnter togetherWith PushExit
                                else -> FadeEnter togetherWith FadeExit
                            }
                        },
                        label = "topScreen",
                    ) { screen ->
                        when (screen) {
                            is TopScreen.Detail -> DetailScreen(
                                screen.item,
                                actions = DetailScreenActions(
                                    onBack = ::backDetail,
                                    onEdit = { editor = it },
                                    onOpenRelated = { rel -> vm.openRelated(context, rel) { fetched -> openRelatedDetail(screen.item, fetched) } },
                                    onBackfillRelated = { id, type, onFound, onDone -> vm.backfillRelated(context, id, type, onFound, onDone) },
                                    onBackfillThemes = { id, type, onFound, onDone -> vm.backfillThemes(context, id, type, onFound, onDone) },
                                    onBackfillCovers = { id, type, onFound, onDone -> vm.backfillCovers(context, id, type, onFound, onDone) },
                                    onLoadRecommended = { forItem, onFound, onDone -> vm.loadUserRecommendations(context, forItem, onFound, onDone) },
                                    onOpenRecommended = { rec -> vm.openRecommended(context, rec) { fetched -> openRelatedDetail(screen.item, fetched) } },
                                    onLoadStatusDistribution = { forItem, onFound, onDone -> vm.loadStatusDistribution(context, forItem, onFound, onDone) },
                                    onLoadCharactersStaff = { forItem, onFound, onDone -> vm.loadCharactersStaff(forItem, onFound, onDone) },
                                    onLoadReviews = { forItem, onFound, onDone -> vm.loadReviews(forItem, onFound, onDone) },
                                    onOpenReview = { rev -> reviewOpen = rev to screen.item.title },
                                    onOpenReviewList = { url, _ -> CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) },
                                    onLeaveScroll = { index, offset -> vm.saveDetailScroll(screen.item.id, index, offset) },
                                    onGenreClick = { genre ->
                                        jumpToDiscover(screen.item, if (screen.item.type == MediaType.Manga) "Manga" else "Anime", DiscoverFilters(genres = setOf(genre)))
                                    },
                                    onCreatorClick = { creator ->
                                        jumpToDiscover(screen.item, if (screen.item.type == MediaType.Manga) "Manga" else "Anime", DiscoverFilters(creator = creator))
                                    },
                                ),
                                relatedLoadingId = vm.relatedLoadingId,
                                recommendedLoadingId = vm.recommendedLoadingId,
                                initialScroll = vm.getDetailScroll(screen.item.id),
                                myListStatus = vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap(),
                            )
                            TopScreen.Ranking -> RankingScreen(vm, onBack = { rankingOpen = false }, onOpenDetail = ::openDetail)
                            TopScreen.Seasonal -> SeasonalScreen(vm, onBack = { seasonalOpen = false }, onOpenDetail = ::openDetail)
                            TopScreen.Recommendations -> RecommendationsScreen(vm, onBack = { recommendationsOpen = false }, onOpenDetail = ::openDetail, onEdit = { editor = it }, selectedItem = editor)
                            is TopScreen.Schedule -> ScheduleScreen(vm, initialDay = screen.initialDay, onBack = { scheduleOpen = false }, onOpenDetail = ::openDetail)
                            is TopScreen.Topic -> ForumTopicScreen(vm, topicId = screen.topicId, title = screen.title, onBack = { forumTopicOpen = null })
                            TopScreen.About -> AboutScreen(
                                onBack = { aboutOpen = false },
                                updateInfo = vm.updateInfo, updateChecking = vm.updateChecking, updateUpToDate = vm.updateUpToDateMessage,
                                onCheckForUpdate = { if (vm.updateInfo != null) vm.updateDialogOpen = true else vm.checkForUpdate(context, manual = true) },
                            )
                            is TopScreen.Review -> ReviewScreen(screen.review, screen.itemTitle, onBack = { reviewOpen = null })
                            TopScreen.StacksHome -> StacksHomeScreen(vm, onBack = { stacksHomeOpen = false }, onOpenBrowse = { kind -> openStacksBrowse(kind) }, onOpenStack = { id, title -> stackDetailOpen = id to title })
                            is TopScreen.StacksBrowse -> StacksScreen(vm, initialKind = screen.initialKind, onBack = { stacksBrowseKind = null }, onOpenStack = { id, title -> stackDetailOpen = id to title })
                            is TopScreen.StackDetail -> StackDetailScreen(screen.stackId, screen.title, loadingId = vm.stackEntryLoadingId, myListStatus = vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap(), initialScroll = vm.getStackDetailScroll(screen.stackId), onLeaveScroll = { index, offset -> vm.saveStackDetailScroll(screen.stackId, index, offset) }, onBack = { stackDetailOpen = null }, onOpenEntry = { entry -> vm.openStackEntry(context, entry) { fetched -> openDetail(fetched) } }, onEditEntry = { entry -> vm.openStackEntry(context, entry) { fetched -> editor = fetched } }, selectedItem = editor)
                            is TopScreen.ClubDetail -> ClubDetailScreen(screen.club, onBack = { clubDetailOpen = null })
                            TopScreen.ProfileStats -> ProfileStatsScreen(vm.signedIn, vm.malProfile, vm.items, onConnect = onSignIn, onBack = { profileStatsOpen = false }, scrollOffset = vm.profileScrollOffset, onSaveScroll = vm::saveProfileScroll, statsTab = vm.profileStatsTab, onStatsTabChange = vm::selectProfileStatsTab, onScoreClick = { type, score -> scoreFilterOpen = type to score }, onYearClick = { type, year -> yearFilterOpen = type to year }, onSignOut = { profileStatsOpen = false; onSignOut() }, refreshing = vm.loading || vm.profileLoading, onRefresh = { vm.load(context) })
                            is TopScreen.ScoreFilter -> ScoreFilterScreen(vm = vm, type = screen.type, initialScore = screen.score, onBack = { scoreFilterOpen = null }, onOpenDetail = ::openDetail)
                            is TopScreen.YearFilter -> YearFilterScreen(vm = vm, type = screen.type, initialYear = screen.year, onBack = { yearFilterOpen = null }, onOpenDetail = ::openDetail)
                            TopScreen.SettingsPage -> SettingsScreen(
                                connected = vm.signedIn, themeMode = vm.themeMode, colorSource = vm.colorSource, paletteStyle = vm.paletteStyle, titleLanguage = vm.titleLanguage,
                                nsfwEnabled = vm.nsfwEnabled, onNsfwChange = { vm.setNsfw(context, it) },
                                amoledDark = vm.amoledDark, onAmoledDarkChange = { vm.setAmoledDark(context, it) },
                                onThemeClick = { themeOpen = true }, onColorClick = { colorSourceOpen = true }, onPaletteClick = { paletteStyleOpen = true }, onTitleLanguageClick = { titleLangOpen = true },
                                updateInfo = vm.updateInfo, onAboutClick = { aboutOpen = true },
                                onBack = { settingsPageOpen = false },
                            )
                            is TopScreen.Tab -> when (screen.destination) {
                                Destination.Home -> HomeScreen(vm, onOpenDetail = ::openDetail, onList = { vm.destination = Destination.List }, onLocateInList = { item -> vm.locateInList(context, item); vm.destination = Destination.List }, onDiscover = { vm.destination = Destination.Discover }, onRanking = { rankingOpen = true }, onSeasonal = { seasonalOpen = true }, onSchedule = ::openSchedule, onOpenTopic = { id, title -> forumTopicOpen = id to title }, onSeeNews = { vm.destination = Destination.Forums; vm.openNewsBoard(context) }, onOpenStack = { id, title -> stackDetailOpen = id to title }, onOpenStacks = ::openStacks, onSignIn = onSignIn, onEdit = { editor = it }, selectedItem = editor)
                                Destination.List -> ListScreen(vm, onOpenDetail = ::openDetail, onIncrement = { vm.saveLive(context, it) }, onEdit = { editor = it }, selectedItem = editor)
                                Destination.Discover -> DiscoverScreen(
                                    vm,
                                    onOpenDetail = ::openDetail,
                                    onRanking = { rankingOpen = true },
                                    onSeasonal = { seasonalOpen = true },
                                    onStacks = ::openStacks,
                                    onRecommendations = { recommendationsOpen = true },
                                    onExitResults = {
                                        val returnItem = discoverReturnItem
                                        if (returnItem != null) { discoverReturnItem = null; selectedItem = returnItem }
                                        else vm.exitDiscoverSearch()
                                    },
                                    onEdit = { editor = it },
                                    selectedItem = editor
                                )
                                Destination.Forums -> ForumsScreen(vm, onOpenTopic = { id, title -> forumTopicOpen = id to title })
                                Destination.Clubs -> ClubsScreen(vm, onOpenClub = { clubDetailOpen = it })
                            }
                        }
                    }
                    vm.error?.let { msg -> ErrorDialog(msg, onDismiss = { vm.error = null }) }
                }
            }
            // Keep sheets inside theme
            editorItem?.let { EditSheet(it, onDismiss = { editor = null }, onSave = { vm.saveLive(context, it); editor = null }, onDelete = { vm.deleteLive(context, it); editor = null; if (selectedItem?.id == it.id && selectedItem?.type == it.type) { vm.clearDetailCache(); selectedItem = null; detailStack = emptyList() } }) }
            if (themeOpen) ThemeSheet(vm.themeMode, onDismiss = { themeOpen = false }, onSelect = { vm.setTheme(context, it); themeOpen = false })
            if (colorSourceOpen) ColorSourceSheet(vm.colorSource, vm.customColorHex, onDismiss = { colorSourceOpen = false }, onSelect = { vm.setColorSource(context, it) }, onCustomHexChange = { vm.setCustomColor(context, it) })
            if (paletteStyleOpen) PaletteStyleSheet(vm.paletteStyle, onDismiss = { paletteStyleOpen = false }, onSelect = { vm.setPaletteStyle(context, it); paletteStyleOpen = false })
            if (titleLangOpen) TitleLanguageSheet(vm.titleLanguage, onDismiss = { titleLangOpen = false }, onSelect = { vm.setTitleLanguage(context, it); titleLangOpen = false })
            if (vm.updateDialogOpen) vm.updateInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    downloadProgress = vm.updateDownloadProgress,
                    needsInstallPermission = vm.updateNeedsInstallPermission,
                    error = vm.updateError,
                    onDownload = { vm.downloadAndInstallUpdate(context) },
                    onOpenInstallSettings = { vm.updateNeedsInstallPermission = false; context.startActivity(AppUpdateChecker(context).installPermissionSettingsIntent()) },
                    onSkip = { vm.skipUpdate(context) },
                    onDismiss = { vm.updateDialogOpen = false; vm.updateNeedsInstallPermission = false; vm.updateError = null },
                )
            }
            if (vm.profileDrawerOpen) {
                AvatarMenu(
                    connected = vm.signedIn, profile = vm.malProfile, anchor = vm.profileMenuAnchor,
                    onOpenProfile = { profileStatsOpen = true },
                    onOpenSettings = { settingsPageOpen = true },
                    onDismiss = { vm.profileDrawerOpen = false; vm.profileMenuAnchor = null },
                )
            }
        }
    }
}

// Shared pieces section