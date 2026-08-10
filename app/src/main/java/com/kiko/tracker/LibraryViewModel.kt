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
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Rect
import java.util.UUID
import kotlin.math.roundToInt

class LibraryViewModel : ViewModel() {
    // Start with empty list
    var items by mutableStateOf(emptyList<MediaItem>()); private set
    var destination by mutableStateOf(Destination.Home)
    // Avatar popup menu (profile/settings), opened from any tab's avatar. anchor is
    // that avatar's on-screen bounds at the moment it was tapped, captured by Avatar
    // itself, so the popup can appear directly under it regardless of which tab it
    // was opened from.
    var profileDrawerOpen by mutableStateOf(false)
    var profileMenuAnchor by mutableStateOf<Rect?>(null)
    var signedIn by mutableStateOf(false); var loading by mutableStateOf(false); var error by mutableStateOf<String?>(null)
    // Whether the initial signed-in check (in load()) has actually run yet. signedIn
    // itself defaults to false before that, which is indistinguishable from "checked
    // and genuinely signed out" — so screens gating on signedIn alone (e.g. Home's
    // "Please sign in" prompt) briefly flashed that prompt for already-signed-in users
    // on every cold start, for the one+ frames between first composition and the
    // LaunchedEffect that calls load() actually running. Gate that kind of UI on
    // authChecked too so nothing shows until we know the real answer.
    var authChecked by mutableStateOf(false); private set
    var themeMode by mutableStateOf(ThemeMode.System)
    var colorSource by mutableStateOf(ColorSource.AppDefault); private set
    var paletteStyle by mutableStateOf(PaletteStyle.TonalSpot); private set
    var customColorHex by mutableStateOf("2E51A2"); private set
    var titleLanguage by mutableStateOf(TitleLanguage.Romaji)
    var listFilter by mutableStateOf("All")
    // Hoisted scroll state
    var listTypeTab by mutableStateOf(MediaType.Anime); private set
    var listSort by mutableStateOf(ListSort.Title); private set
    var listViewMode by mutableStateOf(ListViewMode.List); private set
    var listScrollIndex by mutableStateOf(0); private set
    var listScrollOffset by mutableStateOf(0); private set
    fun saveListScroll(index: Int, offset: Int) { listScrollIndex = index; listScrollOffset = offset }
    // Jump My List to wherever a given item sits — used by Home's "Continue" card so
    // tapping it lands on the item's row in the list instead of opening its detail page.
    // Switches to the item's type tab and the Watching/Reading filter (Continue only ever
    // surfaces items in that status), then finds its index in that same filtered/sorted
    // order to scroll to.
    fun locateInList(context: Context, item: MediaItem) {
        selectListTypeTab(item.type)
        setListFilter(context, normalizeFilterForType("Watching", item.type))
        val ordered = visibleItems.filter { it.type == item.type && it.status.label == normalizeFilterForType("Watching", item.type) }.sortedWithListSort(listSort, titleLanguage)
        val idx = ordered.indexOfFirst { it.id == item.id && it.type == item.type }
        listScrollIndex = if (idx >= 0) idx else 0
        listScrollOffset = 0
    }
    // Discover results scroll
    var discoverScrollIndex by mutableStateOf(0); private set
    var discoverScrollOffset by mutableStateOf(0); private set
    fun saveDiscoverScroll(index: Int, offset: Int) { discoverScrollIndex = index; discoverScrollOffset = offset }
    // Home tab scroll — HomeScreen's LazyColumn previously had no hoisted state at
    // all, so opening a card and pressing back left it torn down and rebuilt from
    // scratch at the top, same class of bug the List/Discover-results scroll state
    // above was already added to fix.
    var homeScrollIndex by mutableStateOf(0); private set
    var homeScrollOffset by mutableStateOf(0); private set
    fun saveHomeScroll(index: Int, offset: Int) { homeScrollIndex = index; homeScrollOffset = offset }
    // Discover landing/browse tab scroll (separate from discoverScroll* above,
    // which is the search-results list further down the same tab)
    var discoverBrowseScrollIndex by mutableStateOf(0); private set
    var discoverBrowseScrollOffset by mutableStateOf(0); private set
    fun saveDiscoverBrowseScroll(index: Int, offset: Int) { discoverBrowseScrollIndex = index; discoverBrowseScrollOffset = offset }
    // Clubs tab state — survives navigating into a club and back, same as
    // Discover results above: query, loaded pages, and scroll position all
    // live here instead of in ClubsScreen's own remember{} blocks, which get
    // torn down when the screen leaves composition for the club detail page.
    var clubsQuery by mutableStateOf(""); private set
    var clubsList by mutableStateOf<List<MalClub>>(emptyList()); private set
    var clubsPage by mutableStateOf(1); private set
    var clubsHasMore by mutableStateOf(false); private set
    var clubsVisibleCount by mutableStateOf(10); private set
    var clubsScrollIndex by mutableStateOf(0); private set
    var clubsScrollOffset by mutableStateOf(0); private set
    fun saveClubsScroll(index: Int, offset: Int) { clubsScrollIndex = index; clubsScrollOffset = offset }
    fun setClubsResults(query: String, page: ClubsPage) {
        clubsQuery = query; clubsList = page.items; clubsPage = 1; clubsHasMore = page.hasMore
        clubsVisibleCount = 10; clubsScrollIndex = 0; clubsScrollOffset = 0
    }
    fun appendClubsResults(page: ClubsPage, pageNumber: Int) { clubsList = clubsList + page.items; clubsHasMore = page.hasMore; clubsPage = pageNumber }
    fun revealMoreClubs(count: Int) { clubsVisibleCount = count }
    // Per-title detail scroll
    private val detailScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    fun getDetailScroll(id: String) = detailScrollPositions[id] ?: (0 to 0)
    fun saveDetailScroll(id: String, index: Int, offset: Int) { detailScrollPositions[id] = index to offset }
    // Same idea for a stack's own entry grid — restores scroll position when
    // coming back from an entry's detail page instead of resetting to top
    private val stackDetailScrollPositions = mutableMapOf<Int, Pair<Int, Int>>()
    fun getStackDetailScroll(stackId: Int) = stackDetailScrollPositions[stackId] ?: (0 to 0)
    fun saveStackDetailScroll(stackId: Int, index: Int, offset: Int) { stackDetailScrollPositions[stackId] = index to offset }
    // Reset scroll on sort
    fun selectListTypeTab(t: MediaType) { listTypeTab = t; listScrollIndex = 0; listScrollOffset = 0 }
    fun setListSort(context: Context, sort: ListSort) { listSort = sort; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_sort", sort.name).apply() }
    fun loadListSort(context: Context) { listSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("list_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
    fun setListViewMode(context: Context, mode: ListViewMode) { listViewMode = mode; settingsPrefs(context).edit().putString("list_view_mode", mode.name).apply() }
    fun loadListViewMode(context: Context) { listViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("list_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    // Score distribution drill-down view mode — separate pref from list_view_mode above so
    // switching one screen's list/grid choice doesn't affect the other's
    var scoreFilterViewMode by mutableStateOf(ListViewMode.List); private set
    fun setScoreFilterViewMode(context: Context, mode: ListViewMode) { scoreFilterViewMode = mode; settingsPrefs(context).edit().putString("score_filter_view_mode", mode.name).apply() }
    fun loadScoreFilterViewMode(context: Context) { scoreFilterViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("score_filter_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    // Profile stats page scroll — a single pixel offset since it's a plain
    // verticalScroll Column, not a LazyColumn with item indices
    var profileScrollOffset by mutableStateOf(0); private set
    fun saveProfileScroll(offset: Int) { profileScrollOffset = offset }
    // Profile stats Anime/Manga switcher — hoisted here (not local `remember`) so it
    // survives drilling into the score distribution filter list and coming back.
    // Only resets to Anime when the user leaves the Profile page entirely.
    var profileStatsTab by mutableStateOf(MediaType.Anime); private set
    fun selectProfileStatsTab(type: MediaType) { profileStatsTab = type }
    // NSFW off by default
    var nsfwEnabled by mutableStateOf(false); private set
    // User profile stats
    var malProfile by mutableStateOf<MalProfile?>(null); private set
    var profileLoading by mutableStateOf(false); private set

    // App update state
    var updateInfo by mutableStateOf<AppUpdateInfo?>(null); private set
    var updateChecking by mutableStateOf(false); private set
    // Up to date flag
    var updateUpToDateMessage by mutableStateOf(false)
    var updateDialogOpen by mutableStateOf(false)
    var updateDownloadProgress by mutableStateOf<Float?>(null); private set
    var updateNeedsInstallPermission by mutableStateOf(false)
    var updateError by mutableStateOf<String?>(null)

    // Instant cached check read
    fun loadCachedUpdate(context: Context) {
        val checker = AppUpdateChecker(context)
        val cached = checker.cached() ?: return
        // Drop stale cached version
        if (!checker.isStillNewer(cached.version)) { checker.clearCache(); return }
        if (cached.version != checker.skippedVersion()) updateInfo = cached
    }
    // Manual vs auto check
    fun checkForUpdate(context: Context, manual: Boolean = false, onFound: (AppUpdateInfo) -> Unit = {}) {
        if (updateChecking) return
        updateChecking = true; updateUpToDateMessage = false; updateError = null
        viewModelScope.launch {
            val checker = AppUpdateChecker(context)
            checker.checkLatest()
                .onSuccess { found ->
                    val skipped = checker.skippedVersion()
                    val shown = found?.takeIf { it.version != skipped }
                    updateInfo = shown
                    if (shown != null) { onFound(shown); if (manual) updateDialogOpen = true } else if (manual) updateUpToDateMessage = true
                }
                .onFailure { if (manual) updateError = it.message ?: "Couldn't check for updates" }
            updateChecking = false
        }
    }
    fun skipUpdate(context: Context) {
        val version = updateInfo?.version ?: return
        AppUpdateChecker(context).skipVersion(version)
        updateInfo = null; updateDialogOpen = false
    }
    // Download then install APK
    fun downloadAndInstallUpdate(context: Context) {
        val info = updateInfo ?: return
        val checker = AppUpdateChecker(context)
        if (!checker.canRequestInstall()) { updateNeedsInstallPermission = true; return }
        updateDownloadProgress = 0f; updateError = null
        viewModelScope.launch {
            checker.downloadApk(info) { progress -> updateDownloadProgress = progress }
                // Clear badge on install
                .onSuccess { file -> updateDownloadProgress = null; updateDialogOpen = false; updateInfo = null; checker.clearCache(); checker.installApk(file) }
                .onFailure { updateDownloadProgress = null; updateError = it.message ?: "Download failed" }
        }
    }

    // NSFW-filtered list surfaces
    val visibleItems get() = items.nsfwFiltered(nsfwEnabled)
    val visibleDiscoverResults get() = discoverResults.nsfwFiltered(nsfwEnabled).filter { it.matches(discoverFilters) }.sortedForDiscover(discoverSort, titleLanguage, discoverQuery)
    val visibleDiscoverNewSeason get() = discoverNewSeason.nsfwFiltered(nsfwEnabled)
    val visibleDiscoverUpcoming get() = discoverUpcoming.nsfwFiltered(nsfwEnabled)
    val visibleRecommendations get() = recommendations.nsfwFiltered(nsfwEnabled)
    val visibleRankingResults get() = rankingResults.nsfwFiltered(nsfwEnabled)
    // Filter to premieres only
    val visibleSeasonalResults: List<MediaItem> get() {
        val filtered = if (seasonalContinuingOnly) seasonalResults
        else seasonalResults.filter { it.startDate == seasonalYear.toString() && it.season.equals(seasonalSeason.label, ignoreCase = true) }
        return filtered.nsfwFiltered(nsfwEnabled)
    }

    // Discover state survives navigation
    var discoverMode by mutableStateOf(DiscoverMode.Browse); private set
    var discoverQuery by mutableStateOf(""); private set
    var discoverTypeFilter by mutableStateOf("Anime"); private set
    var discoverResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverFilters by mutableStateOf(DiscoverFilters()); private set
    var discoverSort by mutableStateOf(DiscoverSort.Members); private set
    fun selectDiscoverSort(sort: DiscoverSort) { discoverSort = sort }
    var discoverSearching by mutableStateOf(false); private set
    var discoverError by mutableStateOf<String?>(null); private set
    var discoverNewSeason by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverUpcoming by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverBrowseLoading by mutableStateOf(false); private set
    var discoverBrowseError by mutableStateOf<String?>(null); private set
    private var discoverBrowseLoaded = false
    private var discoverSearchJob: kotlinx.coroutines.Job? = null
    // Lightweight title suggestions shown below the search bar as the user types —
    // just plain title strings the user can tap to fill/submit the search, no thumbnails
    var discoverSuggestions by mutableStateOf<List<String>>(emptyList()); private set
    private var discoverSuggestJob: kotlinx.coroutines.Job? = null
    // Raw (pre-filter) results from the last studio/author lookup, keyed by media type +
    // lowercased/trimmed creator name — so re-applying Advanced Filters (genre, format,
    // year, ...) while the Studio/Author field stays the same doesn't re-scrape MAL; the
    // generic matches() filtering in visibleDiscoverResults just re-runs against this
    // cached list instead. Cleared in exitDiscoverSearch() rather than living for the whole
    // process — it only needs to survive re-filtering on the results page itself, and every
    // studio/author ever searched otherwise sticks around in memory for the rest of the session.
    private val creatorSearchCache = mutableMapOf<Pair<MediaType, String>, List<MediaItem>>()

    // Home recommendations row
    var recommendations by mutableStateOf<List<MediaItem>>(emptyList()); private set
    private var homeExtrasLoaded = false

    // Ranking chart state
    var rankingType by mutableStateOf(MediaType.Anime); private set
    var rankingSort by mutableStateOf(RankingSort.Score); private set
    var rankingResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var rankingLoading by mutableStateOf(false); private set
    var rankingError by mutableStateOf<String?>(null); private set

    // Seasonal chart state
    var seasonalYear by mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)); private set
    var seasonalSeason by mutableStateOf(currentSeasonName()); private set
    var seasonalSort by mutableStateOf(SeasonalSort.Members); private set
    // Continuing anime display filter
    var seasonalContinuingOnly by mutableStateOf(false); private set
    // Raw unfiltered season chart
    var seasonalResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var seasonalHasMore by mutableStateOf(false); private set
    var seasonalLoading by mutableStateOf(false); private set
    var seasonalLoadingMore by mutableStateOf(false); private set
    var seasonalError by mutableStateOf<String?>(null); private set
    // Restore scroll on return
    var seasonalScrollIndex by mutableStateOf(0); private set
    var seasonalScrollOffset by mutableStateOf(0); private set
    fun saveSeasonalScroll(index: Int, offset: Int) { seasonalScrollIndex = index; seasonalScrollOffset = offset }

    // Reset chart when leaving
    fun resetSeasonal() {
        seasonalYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        seasonalSeason = currentSeasonName()
        seasonalSort = SeasonalSort.Members
        seasonalContinuingOnly = false
        seasonalResults = emptyList()
        seasonalHasMore = false
        seasonalScrollIndex = 0
        seasonalScrollOffset = 0
        seasonalError = null
    }

    // Match on id AND type — MAL anime and manga ids are separate numbering spaces
    // and can collide (e.g. anime id 11577 vs manga id 11577), so id alone isn't
    // a safe key here: matching by id only could silently overwrite or delete an
    // unrelated title of the other type.
    fun save(item: MediaItem) { items = if (items.any { it.id == item.id && it.type == item.type }) items.map { if (it.id == item.id && it.type == item.type) item else it } else listOf(item) + items }
    fun delete(id: String, type: MediaType) { items = items.filterNot { it.id == id && it.type == type } }
    fun reset() { items = emptyList() }

    fun loadTheme(context: Context) { themeMode = runCatching { ThemeMode.valueOf(settingsPrefs(context).getString("theme_mode", ThemeMode.System.name)!!) }.getOrDefault(ThemeMode.System) }
    fun setTheme(context: Context, mode: ThemeMode) { themeMode = mode; settingsPrefs(context).edit().putString("theme_mode", mode.name).apply() }
    fun loadColorSource(context: Context) { colorSource = runCatching { ColorSource.valueOf(settingsPrefs(context).getString("color_source", ColorSource.AppDefault.name)!!) }.getOrDefault(ColorSource.AppDefault) }
    fun setColorSource(context: Context, source: ColorSource) { colorSource = source; settingsPrefs(context).edit().putString("color_source", source.name).apply() }
    fun loadPaletteStyle(context: Context) { paletteStyle = runCatching { PaletteStyle.valueOf(settingsPrefs(context).getString("palette_style", PaletteStyle.TonalSpot.name)!!) }.getOrDefault(PaletteStyle.TonalSpot) }
    fun setPaletteStyle(context: Context, style: PaletteStyle) { paletteStyle = style; settingsPrefs(context).edit().putString("palette_style", style.name).apply() }
    fun loadCustomColor(context: Context) { customColorHex = settingsPrefs(context).getString("custom_color_hex", "2E51A2") ?: "2E51A2" }
    // Persist only valid hex
    fun setCustomColor(context: Context, hex: String) {
        customColorHex = hex
        if (parseHexColor(hex) != null) settingsPrefs(context).edit().putString("custom_color_hex", hex).apply()
    }
    fun loadTitleLanguage(context: Context) { titleLanguage = runCatching { TitleLanguage.valueOf(settingsPrefs(context).getString("title_language", TitleLanguage.Romaji.name)!!) }.getOrDefault(TitleLanguage.Romaji) }
    fun setTitleLanguage(context: Context, lang: TitleLanguage) { titleLanguage = lang; settingsPrefs(context).edit().putString("title_language", lang.name).apply() }
    fun loadListFilter(context: Context) { listFilter = settingsPrefs(context).getString("list_filter", "All") ?: "All" }
    fun setListFilter(context: Context, filter: String) { listFilter = filter; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_filter", filter).apply() }
    fun loadNsfwPref(context: Context) { nsfwEnabled = settingsPrefs(context).getBoolean("nsfw_enabled", false) }
    fun setNsfw(context: Context, enabled: Boolean) { nsfwEnabled = enabled; settingsPrefs(context).edit().putBoolean("nsfw_enabled", enabled).apply() }

    // Load profile and stats
    fun loadProfile(context: Context) {
        val api = MalApi(context); if (!api.signedIn) { malProfile = null; return }
        profileLoading = true
        viewModelScope.launch { runCatching { api.profile() }.onSuccess { malProfile = it }; profileLoading = false }
    }

    fun load(context: Context) {
        val api = MalApi(context); signedIn = api.signedIn; authChecked = true; if (!signedIn) return
        loading = true
        viewModelScope.launch { runCatching { api.library() }.onSuccess { items = it }.onFailure { error = it.message ?: "Could not load your MAL list" }; loading = false }
        loadProfile(context)
    }
    fun saveLive(context: Context, item: MediaItem) {
        val stamped = item.copy(updatedAt = nowIso(), inUserList = true)
        save(stamped)
        if (signedIn) viewModelScope.launch { runCatching { MalApi(context).update(stamped) }.onFailure { error = "MAL sync failed: ${it.message ?: "unknown error"}" } }
    }
    // Delete mirrors local-first save
    fun deleteLive(context: Context, item: MediaItem) {
        delete(item.id, item.type)
        if (signedIn) viewModelScope.launch { runCatching { MalApi(context).deleteEntry(item) }.onFailure { error = "MAL sync failed: ${it.message ?: "unknown error"}" } }
    }
    fun signOut(context: Context) { MalApi(context).signOut(); signedIn = false; items = emptyList(); malProfile = null }

    // Load home browse rows
    fun loadDiscoverBrowse(context: Context) {
        if (discoverBrowseLoaded || !MalApi(context).signedIn) return
        discoverBrowseLoaded = true
        discoverBrowseLoading = true
        viewModelScope.launch {
            val api = MalApi(context)
            runCatching { api.seasonalAnime(100) to api.upcomingAnime(10) }
                .onSuccess { (season, up) -> discoverNewSeason = season; discoverUpcoming = up; discoverBrowseError = null }
                .onFailure { discoverBrowseError = it.message ?: "Could not load Discover" }
            discoverBrowseLoading = false
        }
    }
    // Load recommendations row
    fun loadHomeExtras(context: Context) {
        if (homeExtrasLoaded || !MalApi(context).signedIn) return
        homeExtrasLoaded = true
        viewModelScope.launch { runCatching { MalApi(context).animeSuggestions(100) }.onSuccess { recommendations = it } }
    }
    // (Re)run ranking chart
    fun loadRanking(context: Context, type: MediaType, sort: RankingSort) {
        rankingType = type; rankingSort = if (type == MediaType.Manga && sort == RankingSort.Upcoming) RankingSort.Score else sort
        if (!MalApi(context).signedIn) { rankingError = "Sign in from Profile to view rankings"; return }
        viewModelScope.launch {
            rankingLoading = true
            runCatching { MalApi(context).ranking(rankingType, rankingSort.apiValue()) }
                .onSuccess { rankingResults = it; rankingError = null }
                .onFailure { rankingError = it.message ?: "Could not load ranking" }
            rankingLoading = false
        }
    }
    // (Re)run seasonal chart
    fun loadSeasonal(context: Context, year: Int = seasonalYear, season: SeasonName = seasonalSeason, sort: SeasonalSort = seasonalSort, continuingOnly: Boolean = seasonalContinuingOnly) {
        seasonalYear = year; seasonalSeason = season; seasonalSort = sort; seasonalContinuingOnly = continuingOnly
        if (!MalApi(context).signedIn) { seasonalError = "Sign in from Profile to browse seasons"; return }
        viewModelScope.launch {
            seasonalLoading = true
            runCatching { MalApi(context).seasonalAnime(year, season.api, sort = sort.api) }
                // Reconcile against user's library
                .onSuccess { seasonalResults = it.items.map { candidate -> items.find { i -> i.id == candidate.id && i.type == candidate.type } ?: candidate }; seasonalHasMore = it.hasMore; seasonalError = null }
                .onFailure { seasonalError = it.message ?: "Could not load season"; seasonalHasMore = false }
            seasonalLoading = false
        }
    }

    // Load more season page
    fun loadMoreSeasonal(context: Context) {
        if (seasonalLoading || seasonalLoadingMore || !seasonalHasMore) return
        val api = MalApi(context)
        if (!api.signedIn) return
        viewModelScope.launch {
            seasonalLoadingMore = true
            runCatching { api.seasonalAnime(seasonalYear, seasonalSeason.api, offset = seasonalResults.size, sort = seasonalSort.api) }
                // Reconcile against user's library
                .onSuccess { seasonalResults = seasonalResults + it.items.map { candidate -> items.find { i -> i.id == candidate.id && i.type == candidate.type } ?: candidate }; seasonalHasMore = it.hasMore }
                .onFailure { seasonalHasMore = false }
            seasonalLoadingMore = false
        }
    }
    // Title suggestions for the search bar, fetched (debounced) as the user types.
    // These are for autofilling the search field only — tapping one runs the
    // actual search via runDiscoverSearch, it doesn't open a detail page directly.
    fun fetchDiscoverSuggestions(context: Context, query: String, type: String) {
        discoverSuggestJob?.cancel()
        if (query.isBlank()) { discoverSuggestions = emptyList(); return }
        if (!MalApi(context).signedIn) { discoverSuggestions = emptyList(); return }
        discoverSuggestJob = viewModelScope.launch {
            delay(100) // debounce so we're not firing a request per keystroke
            val t = when (type) { "Anime" -> MediaType.Anime; "Manga" -> MediaType.Manga; else -> null }
            runCatching { MalApi(context).suggestTitles(query, t) }
                .onSuccess { discoverSuggestions = it }
                .onFailure { discoverSuggestions = emptyList() }
        }
    }
    fun clearDiscoverSuggestions() { discoverSuggestJob?.cancel(); discoverSuggestions = emptyList() }

    // Switch to results page
    fun runDiscoverSearch(context: Context, query: String, type: String, filters: DiscoverFilters = discoverFilters) {
        discoverQuery = query; discoverTypeFilter = type; discoverFilters = filters; discoverMode = DiscoverMode.Results
        // Reset scroll for search
        discoverScrollIndex = 0; discoverScrollOffset = 0
        discoverSuggestJob?.cancel(); discoverSuggestions = emptyList()
        discoverSearchJob?.cancel()
        if (query.isBlank() && !filters.isActive()) { discoverResults = emptyList(); discoverSearching = false; discoverError = null; return }
        if (!MalApi(context).signedIn) { discoverError = "Sign in from Profile to search MyAnimeList"; return }
        discoverSearchJob = viewModelScope.launch {
            discoverSearching = true
            val t = when (type) { "Anime" -> MediaType.Anime; "Manga" -> MediaType.Manga; else -> null }
            val api = MalApi(context)
            runCatching {
                val results =
                // Studio (anime) / author (manga) search: resolve the typed name to a MAL
                // company or person id by searching MAL's own search page directly, then
                // scrape that studio's/person's own MAL page for their full catalog of
                // credited works — two requests total, both straight to myanimelist.net,
                // no Tenrai/third-party API involved (see MalCompanyApi/MalPeopleApi).
                // Raw (pre-filter) results are cached per name+type in creatorSearchCache,
                // so re-applying filters (Advanced Filters "Apply", changing genre/format/
                // etc.) with the same creator doesn't re-scrape MAL — matches() below just
                // re-filters the cached list, same as every other search path already does.
                //
                // This has to be checked *before* the plain-text query branch below, not
                // after: whenever someone types the studio/author's name into the main
                // search bar (the natural thing to do) `query` is non-blank, so with the
                // original ordering that branch always won and this one — the one that
                // actually resolves the creator and pulls their full works list — never ran
                // at all. The scrape underneath it isn't the search itself; the search is
                    // choosing to call it in the first place.
                    if (filters.creator.isNotBlank()) {
                        val creatorKey = filters.creator.trim().lowercase()
                        val animeResults = if (t == MediaType.Manga) emptyList() else creatorSearchCache.getOrPut(MediaType.Anime to creatorKey) {
                            val malCompany = MalCompanyApi()
                            val studioResults = runCatching {
                                val companyId = malCompany.searchCompany(filters.creator)
                                if (companyId == null) emptyList() else malCompany.fetchWorks(companyId, filters.creator)
                            }.getOrElse { emptyList() }
                            // Fall back to the old ranking-pool approach (filtered client-side
                            // by matches()) only if we couldn't resolve the studio at all, so a
                            // lookup failure still shows something instead of a blank screen.
                            studioResults.ifEmpty {
                                coroutineScope {
                                    listOf("all", "bypopularity", "favorite").map { rankType -> async { api.ranking(MediaType.Anime, rankType, limit = 500) } }
                                }.awaitAll().flatten()
                            }
                        }
                        val mangaResults = if (t == MediaType.Anime) emptyList() else creatorSearchCache.getOrPut(MediaType.Manga to creatorKey) {
                            val malPeople = MalPeopleApi()
                            val authorResults = runCatching {
                                val personId = malPeople.searchPerson(filters.creator)
                                if (personId == null) emptyList() else malPeople.fetchCreditedWorks("manga", personId, filters.creator)
                            }.getOrElse { emptyList() }
                            // Same reasoning: fall back to the old manga ranking-pool approach
                            // only when the author couldn't be resolved / had nothing credited.
                            authorResults.ifEmpty {
                                coroutineScope {
                                    listOf("all", "bypopularity", "favorite", "manga", "novels", "oneshots", "doujin", "manhwa", "manhua").map { rankType ->
                                        async { api.ranking(MediaType.Manga, rankType, limit = 500) }
                                    }
                                }.awaitAll().flatten()
                            }
                        }
                        (animeResults + mangaResults).distinctBy { it.id to it.type }
                    }
                    else if (query.isNotBlank()) api.search(query, t)
                    // Search via Tenrai filters
                    else if (filters.genres.isNotEmpty() || filters.themes.isNotEmpty() || filters.demographics.isNotEmpty()) {
                        val tenrai = TenraiApi()
                        val kinds = t?.let { listOf(if (it == MediaType.Anime) "anime" else "manga") } ?: listOf("anime", "manga")
                        // Pick one facet id
                        val names = filters.themes.ifEmpty { filters.genres.ifEmpty { filters.demographics } }
                        val tenraiResults = runCatching {
                            coroutineScope {
                                kinds.map { kind -> async { tenrai.searchByGenreIds(kind, tenrai.resolveGenreIds(kind, names), includeAdult = nsfwEnabled) } }
                                    .awaitAll().flatten()
                            }
                        }.getOrElse { emptyList() }
                        // Fall back to a broad ranking pool (filtered client-side against the
                        // chosen genre/theme/demographic by matches()) whenever Tenrai comes back
                        // empty — not just when it throws. A clean-but-empty response otherwise had
                        // no safety net, so the first tap could show nothing until a manual retry.
                        (if (tenraiResults.isNotEmpty()) tenraiResults else coroutineScope {
                            (t?.let { listOf(it) } ?: MediaType.entries.toList()).flatMap { mt ->
                                listOf("all", "bypopularity", "favorite").map { rankType -> async { api.ranking(mt, rankType, limit = 500) } }
                            }.awaitAll().flatten()
                        })
                            .distinctBy { it.id }
                    }
                    // Merge multiple ranking charts
                    else coroutineScope {
                        (t?.let { listOf(it) } ?: MediaType.entries.toList()).flatMap { mt ->
                            val rankTypes = if (mt == MediaType.Anime)
                                listOf("all", "bypopularity", "favorite", "airing", "upcoming", "tv", "ova", "movie", "special")
                            else
                                listOf("all", "bypopularity", "favorite", "manga", "novels", "oneshots", "doujin", "manhwa", "manhua")
                            rankTypes.map { rankType -> async { api.ranking(mt, rankType, limit = 500) } }
                        }.awaitAll().flatten()
                    }.distinctBy { it.id }
                // Reconcile against user's library
                results.map { candidate -> items.find { it.id == candidate.id && it.type == candidate.type } ?: candidate }
            }
                .onSuccess { discoverResults = it; discoverError = null }
                .onFailure { discoverError = it.message ?: "Search failed" }
            discoverSearching = false
        }
    }
    // Return to browse view
    fun exitDiscoverSearch() {
        discoverSort = DiscoverSort.Members
        discoverSearchJob?.cancel()
        discoverSuggestJob?.cancel(); discoverSuggestions = emptyList()
        discoverMode = DiscoverMode.Browse; discoverQuery = ""; discoverResults = emptyList(); discoverFilters = DiscoverFilters(); discoverError = null
        // Drop the raw studio/author lookup cache here rather than letting it live for the
        // whole process — it existed purely so re-applying filters *within* the same results
        // page didn't re-scrape MAL. Once the person leaves the results page that reason is
        // gone, and holding onto every studio/author they've ever searched for the rest of
        // the session just grows unbounded for no benefit; the next search simply re-scrapes.
        creatorSearchCache.clear()
    }

    // Forum browsing state hoisted
    var forumMode by mutableStateOf(ForumMode.Boards); private set
    var forumCategories by mutableStateOf<List<ForumCategory>>(emptyList()); private set
    var forumBoardsLoading by mutableStateOf(false); private set
    var forumBoardsError by mutableStateOf<String?>(null); private set
    private var forumBoardsLoaded = false
    // Blank title means search
    var forumBoardTitle by mutableStateOf(""); private set
    var forumBoardId by mutableStateOf<Int?>(null); private set
    var forumSubboards by mutableStateOf<List<ForumSubboard>>(emptyList()); private set
    var forumSubboardId by mutableStateOf<Int?>(null); private set
    var forumQuery by mutableStateOf(""); private set
    var forumTopics by mutableStateOf<List<ForumTopic>>(emptyList()); private set
    var forumTopicsLoading by mutableStateOf(false); private set
    var forumTopicsError by mutableStateOf<String?>(null); private set
    var forumHasMore by mutableStateOf(false); private set
    var forumLoadingMore by mutableStateOf(false); private set
    private var forumTopicsJob: kotlinx.coroutines.Job? = null
    // Forum scroll position slots
    var forumBoardsScrollIndex by mutableStateOf(0); private set
    var forumBoardsScrollOffset by mutableStateOf(0); private set
    fun saveForumBoardsScroll(index: Int, offset: Int) { forumBoardsScrollIndex = index; forumBoardsScrollOffset = offset }
    var forumTopicsScrollIndex by mutableStateOf(0); private set
    var forumTopicsScrollOffset by mutableStateOf(0); private set
    fun saveForumTopicsScroll(index: Int, offset: Int) { forumTopicsScrollIndex = index; forumTopicsScrollOffset = offset }
    private val forumTopicScrollPositions = mutableMapOf<Int, Pair<Int, Int>>()
    fun forumTopicScrollFor(topicId: Int): Pair<Int, Int> = forumTopicScrollPositions[topicId] ?: (0 to 0)
    fun saveForumTopicScroll(topicId: Int, index: Int, offset: Int) { forumTopicScrollPositions[topicId] = index to offset }

    // Load forum board hierarchy
    fun loadForumBoards(context: Context, force: Boolean = false) {
        if ((forumBoardsLoaded && !force) || !MalApi(context).signedIn) return
        forumBoardsLoaded = true
        forumBoardsLoading = true
        viewModelScope.launch {
            runCatching { MalApi(context).forumBoards() }
                .onSuccess { forumCategories = it; forumBoardsError = null }
                .onFailure { forumBoardsError = it.message ?: "Could not load forums" }
            forumBoardsLoading = false
        }
    }
    // Open board's topic list
    fun openForumBoard(context: Context, board: ForumBoard) {
        forumMode = ForumMode.Topics
        forumBoardTitle = board.title; forumBoardId = board.id; forumSubboards = board.subboards; forumSubboardId = null; forumQuery = ""
        forumTopicsScrollIndex = 0; forumTopicsScrollOffset = 0
        runForumTopics(context)
    }
    // Narrow to one subboard
    fun openForumSubboard(context: Context, subboardId: Int?) {
        forumSubboardId = subboardId
        forumTopicsScrollIndex = 0; forumTopicsScrollOffset = 0
        runForumTopics(context)
    }
    // Cross-board keyword search
    fun runForumSearch(context: Context, query: String) {
        forumMode = ForumMode.Topics
        forumBoardTitle = ""; forumBoardId = null; forumSubboards = emptyList(); forumSubboardId = null; forumQuery = query
        forumTopicsScrollIndex = 0; forumTopicsScrollOffset = 0
        runForumTopics(context)
    }
    // Is News Discussion board?
    val forumIsNewsBoard: Boolean get() = forumBoardTitle.equals("News Discussion", ignoreCase = true)
    private fun runForumTopics(context: Context) {
        forumTopicsJob?.cancel()
        if (!MalApi(context).signedIn) { forumTopicsError = "Sign in from Profile to browse the forums"; return }
        val newsBoard = forumIsNewsBoard
        forumTopicsJob = viewModelScope.launch {
            forumTopicsLoading = true
            runCatching { MalApi(context).forumTopics(boardId = forumBoardId, subboardId = forumSubboardId, query = forumQuery, withThumbnails = newsBoard) }
                .onSuccess { forumTopics = it.items; forumHasMore = it.hasMore; forumTopicsError = null }
                .onFailure { forumTopicsError = it.message ?: "Could not load topics" }
            forumTopicsLoading = false
        }
    }
    // Load more forum topics
    fun loadMoreForumTopics(context: Context) {
        if (forumTopicsLoading || forumLoadingMore || !forumHasMore) return
        val api = MalApi(context); if (!api.signedIn) return
        val newsBoard = forumIsNewsBoard
        viewModelScope.launch {
            forumLoadingMore = true
            runCatching { api.forumTopics(boardId = forumBoardId, subboardId = forumSubboardId, query = forumQuery, offset = forumTopics.size, withThumbnails = newsBoard) }
                .onSuccess { forumTopics = forumTopics + it.items; forumHasMore = it.hasMore }
                .onFailure { forumHasMore = false }
            forumLoadingMore = false
        }
    }
    // Return to board list
    fun exitForumTopics() {
        forumTopicsJob?.cancel()
        forumMode = ForumMode.Boards; forumBoardTitle = ""; forumBoardId = null; forumSubboards = emptyList(); forumSubboardId = null; forumQuery = ""
        forumTopics = emptyList(); forumTopicsError = null; forumHasMore = false
    }
    // Jump to News board
    fun openNewsBoard(context: Context) {
        viewModelScope.launch {
            val cached = forumCategories.flatMap { it.boards }.firstOrNull { it.title.equals("News Discussion", ignoreCase = true) }
            val board = cached ?: run {
                val fetched = runCatching { MalApi(context).forumBoards() }.getOrNull() ?: return@run null
                forumCategories = fetched; forumBoardsLoaded = true
                fetched.flatMap { it.boards }.firstOrNull { it.title.equals("News Discussion", ignoreCase = true) }
            }
            board?.let { openForumBoard(context, it) }
        }
    }

    // Home snapshots row state
    var newsSnapshots by mutableStateOf<List<NewsSnapshot>>(emptyList()); private set
    var newsSnapshotsLoading by mutableStateOf(false); private set
    private var newsSnapshotsLoaded = false
    fun loadNewsSnapshots(context: Context, force: Boolean = false) {
        if ((newsSnapshotsLoaded && !force) || !MalApi(context).signedIn) return
        newsSnapshotsLoaded = true
        newsSnapshotsLoading = true
        viewModelScope.launch {
            runCatching { MalApi(context).newsSnapshots() }
                .onSuccess { newsSnapshots = it }
                // Fail silently, no banner
                .onFailure { newsSnapshotsLoaded = false }
            newsSnapshotsLoading = false
        }
    }

    // Related row loading id
    var relatedLoadingId by mutableStateOf<Int?>(null); private set

    // Recommended row loading id
    var recommendedLoadingId by mutableStateOf<Int?>(null); private set

    // Discover row loading id
    var discoverDetailLoadingId by mutableStateOf<String?>(null); private set

    // Fetch full record first
    fun openDiscoverDetail(context: Context, item: MediaItem, onLoaded: (MediaItem) -> Unit) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onLoaded(item); return }
        discoverDetailLoadingId = item.id
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, item.type) }
                .onSuccess { onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            discoverDetailLoadingId = null
        }
    }

    // Open related-row title
    fun openRelated(context: Context, entry: RelatedEntry, onLoaded: (MediaItem) -> Unit) {
        val type = if (entry.malType == "anime") MediaType.Anime else MediaType.Manga
        relatedLoadingId = entry.malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(entry.malId, type) }
                .onSuccess { onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            relatedLoadingId = null
        }
    }

    // Stack entry row loading id
    var stackEntryLoadingId by mutableStateOf<Int?>(null); private set

    // Open a title tapped from inside a stack
    fun openStackEntry(context: Context, entry: StackTitleEntry, onLoaded: (MediaItem) -> Unit) {
        stackEntryLoadingId = entry.malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(entry.malId, entry.type) }
                .onSuccess { onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            stackEntryLoadingId = null
        }
    }

    // Interest Stacks browsing state hoisted here (not just local `remember`) so the
    // fetched lists AND scroll position both survive navigating into a stack's
    // entries and back out — otherwise every return trip re-fetches from MAL
    // and drops the user back at the top of the list.
    var stacksHomeChallenges by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksHomeManga by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksHomeAnime by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksHomeMal by mutableStateOf<List<StackSummary>>(emptyList()); private set
    // Only ever page 1 — Home's "Recent" section doesn't paginate; further
    // results are reached via "See all" / search, which opens the dedicated
    // browse screen (that screen does its own paging independently).
    var stacksHomeRecent by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksHomeLoading by mutableStateOf(false); private set
    private var stacksHomeLoaded = false
    var stacksHomeScrollIndex by mutableStateOf(0); private set
    var stacksHomeScrollOffset by mutableStateOf(0); private set
    fun saveStacksHomeScroll(index: Int, offset: Int) { stacksHomeScrollIndex = index; stacksHomeScrollOffset = offset }
    // Loads once — cached in this VM for the rest of the process, so returning from
    // a stack's detail page shows the same rows at the same scroll offset
    fun loadStacksHome() {
        if (stacksHomeLoaded) return
        stacksHomeLoaded = true
        stacksHomeLoading = true
        viewModelScope.launch {
            val api = StacksApi()
            coroutineScope {
                val ch = async { runCatching { api.search(StackBrowseKind.Challenges).take(2) }.getOrElse { emptyList() } }
                val mg = async { runCatching { api.search(StackBrowseKind.Manga).take(1) }.getOrElse { emptyList() } }
                val an = async { runCatching { api.search(StackBrowseKind.Anime).take(1) }.getOrElse { emptyList() } }
                val mal = async { runCatching { api.search(StackBrowseKind.MyAnimeList).take(1) }.getOrElse { emptyList() } }
                val rc = async { runCatching { api.search(StackBrowseKind.All) }.getOrElse { emptyList() } }
                stacksHomeChallenges = ch.await(); stacksHomeManga = mg.await(); stacksHomeAnime = an.await(); stacksHomeMal = mal.await(); stacksHomeRecent = rc.await()
            }
            stacksHomeLoading = false
        }
    }
    // Single freshest stack for the Home screen's "Interest Stacks" teaser — a
    // lighter-weight cousin of loadStacksHome() above, since Home only ever
    // needs the one most-recent card, not the full curated homepage.
    var homeLatestStack by mutableStateOf<StackSummary?>(null); private set
    private var homeLatestStackLoaded = false
    fun loadHomeLatestStack(context: Context, force: Boolean = false) {
        if ((homeLatestStackLoaded && !force) || !MalApi(context).signedIn) return
        homeLatestStackLoaded = true
        viewModelScope.launch {
            homeLatestStack = runCatching { StacksApi().search(StackBrowseKind.All).firstOrNull() }.getOrNull()
        }
    }

    // Interest Stacks browse/search screen state — same hoisting reasoning as above
    var stacksBrowseResults by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksBrowseLoading by mutableStateOf(false); private set
    var stacksBrowseQuery by mutableStateOf(""); private set
    var stacksBrowseActiveKind by mutableStateOf<StackBrowseKind?>(null); private set
    private var stacksBrowsePage = 1
    var stacksBrowseScrollIndex by mutableStateOf(0); private set
    var stacksBrowseScrollOffset by mutableStateOf(0); private set
    fun saveStacksBrowseScroll(index: Int, offset: Int) { stacksBrowseScrollIndex = index; stacksBrowseScrollOffset = offset }
    fun updateStacksBrowseQuery(q: String) { stacksBrowseQuery = q }
    private fun loadStacksBrowse(reset: Boolean) {
        val kind = stacksBrowseActiveKind ?: return
        if (!reset && stacksBrowseLoading) return
        val targetPage = if (reset) 1 else stacksBrowsePage + 1
        stacksBrowseLoading = true
        viewModelScope.launch {
            val result = runCatching { StacksApi().search(kind, stacksBrowseQuery.trim(), targetPage) }.getOrElse { emptyList() }
            stacksBrowseResults = if (reset) result else stacksBrowseResults + result
            stacksBrowsePage = targetPage
            stacksBrowseLoading = false
        }
    }
    // Switches tab and reloads only when the kind actually changes — returning to
    // the same tab after visiting a stack's detail page keeps the existing results
    fun setStacksBrowseKind(kind: StackBrowseKind) {
        if (stacksBrowseActiveKind == kind) return
        stacksBrowseActiveKind = kind
        stacksBrowseScrollIndex = 0; stacksBrowseScrollOffset = 0
        loadStacksBrowse(reset = true)
    }
    fun searchStacksBrowse() { stacksBrowseScrollIndex = 0; stacksBrowseScrollOffset = 0; loadStacksBrowse(reset = true) }
    fun loadMoreStacksBrowse() = loadStacksBrowse(reset = false)

    // Backfill empty related row
    fun backfillRelated(context: Context, id: String, type: MediaType, onFound: (List<RelatedEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.related.isNotEmpty()) onFound(fresh.related) }
            onDone()
        }
    }

    // Backfill empty theme fields
    fun backfillThemes(context: Context, id: String, type: MediaType, onFound: (List<String>, List<String>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.openingThemes.isNotEmpty() || fresh.endingThemes.isNotEmpty()) onFound(fresh.openingThemes, fresh.endingThemes) }
            onDone()
        }
    }

    // Backfill missing cover gallery
    fun backfillCovers(context: Context, id: String, type: MediaType, onFound: (List<String>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.covers.size > 1) onFound(fresh.covers) }
            onDone()
        }
    }

    // Load characters + staff rows
    fun loadCharactersStaff(item: MediaItem, onFound: (List<CharacterEntry>, List<StaffEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        val kind = if (item.type == MediaType.Anime) "anime" else "manga"
        viewModelScope.launch {
            runCatching {
                val tenrai = TenraiApi()
                coroutineScope {
                    val chars = async { tenrai.fetchCharacters(kind, intId) }
                    val staffList = async { tenrai.fetchStaff(kind, intId) }
                    chars.await() to staffList.await()
                }
            }.onSuccess { (chars, staffList) -> if (chars.isNotEmpty() || staffList.isNotEmpty()) onFound(chars, staffList) }
            onDone()
        }
    }

    // Load reviews row
    fun loadReviews(item: MediaItem, onFound: (List<ReviewEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        val kind = if (item.type == MediaType.Anime) "anime" else "manga"
        viewModelScope.launch {
            runCatching { TenraiApi().fetchReviews(kind, intId) }
                .onSuccess { if (it.isNotEmpty()) onFound(it) }
            onDone()
        }
    }

    // Backfill recommended row
    fun loadUserRecommendations(context: Context, item: MediaItem, onFound: (List<RecommendedEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).userRecommendations(intId, item.type) }
                .onSuccess { if (it.isNotEmpty()) onFound(it) }
            onDone()
        }
    }

    // Open recommended-row title
    fun openRecommended(context: Context, entry: RecommendedEntry, onLoaded: (MediaItem) -> Unit) {
        val type = if (entry.malType == "anime") MediaType.Anime else MediaType.Manga
        recommendedLoadingId = entry.malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(entry.malId, type) }
                .onSuccess { onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            recommendedLoadingId = null
        }
    }

    // Load status distribution data
    fun loadStatusDistribution(context: Context, item: MediaItem, onFound: (StatusDistribution) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null || item.type != MediaType.Anime) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, item.type) }
                .onSuccess { fresh -> if (fresh.statusDistribution.total > 0) onFound(fresh.statusDistribution) }
            onDone()
        }
    }
}