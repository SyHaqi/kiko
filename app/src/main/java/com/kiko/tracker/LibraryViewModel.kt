@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Deferred
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.Stable

// @Stable, not @Immutable: this class is mutable (every field below is a `var` backed by
// mutableStateOf), but it's always the SAME instance for the lifetime of a screen (handed
// out once by viewModel() in MainActivity) and every mutation goes through Compose's
// snapshot system, so reads inside any composable are correctly and precisely invalidated
// regardless. What @Stable buys is different: `vm` is passed as a parameter into dozens of
// composables several layers deep (ListRow, ListGridCard, SortMenu, ...), and without this
// annotation the Compose compiler has no way to know a class built entirely out of `var`s
// behaves this way — it infers the class Unstable, which means every composable that takes
// `vm: LibraryViewModel` as a parameter loses the ability to skip recomposition on that
// parameter, even when nothing it actually reads from `vm` changed. On a screen with a
// scrolling grid of cards each holding a `vm` reference, that turns one unrelated state
// change (e.g. a snackbar dismissing) into a full re-walk of every visible card instead of
// just the composables that actually read the changed field.
@Stable
class LibraryViewModel : ViewModel() {
    // No longer used by the genre/theme/demographic-filtered Discover search — that path
    // (MalGenreApi) scrapes MAL's own advanced-search results directly, which has a fixed
    // 50-row page size of its own (MalGenreApi.pageSize) rather than an adjustable limit.
    // Kept only in case something else still references it.
    private val TENRAI_SEARCH_PAGE_LIMIT = 10
    // Start with empty list
    var items by mutableStateOf(emptyList<MediaItem>()); private set
    // O(1) library lookup by (id, type), used when reconciling a page of seasonal/discover
    // results against the user's library. Previously each reconciliation ran items.find{...} —
    // a full linear scan of the whole library — once per candidate in the incoming page, so a
    // 20-50 item page against a several-hundred-entry library was an O(page * library) scan.
    // derivedStateOf caches this and only recomputes when `items` itself changes.
    private val itemsByKey: Map<Pair<String, MediaType>, MediaItem> by derivedStateOf {
        items.associateBy { it.id to it.type }
    }
    // Live "is this tracked, and as what?" lookup for items that didn't come from the user's
    // own list (search/discover/seasonal/ranking results) — O(1) via itemsByKey, and re-evaluated
    // on every call, so a status edit or a delete shows up on these screens immediately instead
    // of only after the screen happens to re-fetch. Used as Cover's overrideStatus.
    fun trackedStatus(item: MediaItem): WatchStatus? = itemsByKey[item.id to item.type]?.status
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
        selectListTypeTab(context, item.type)
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
    // Per-title detail scroll — keyed by id+type, same as detailCaches below, since
    // anime and manga ids can collide.
    private val detailScrollPositions = mutableMapOf<Pair<String, MediaType>, Pair<Int, Int>>()
    fun getDetailScroll(id: String, type: MediaType) = detailScrollPositions[id to type] ?: (0 to 0)
    fun saveDetailScroll(id: String, type: MediaType, index: Int, offset: Int) { detailScrollPositions[id to type] = index to offset }
    // Per-title cache for every DetailScreen sub-section (related, themes, covers,
    // recommended, status distribution, characters/staff, reviews). Keyed by id+type
    // since anime and manga ids can collide. Backing DetailScreen's loaders with this
    // means hopping from a title into a related/recommended entry and back doesn't
    // refire any network calls or re-show loading skeletons — the whole related ⇄
    // recommended chain reads from memory. Only cleared once the user backs all the
    // way out of the chain (see clearDetailCache(), called from Navigation.kt).
    private data class DetailCache(
        var resolvedItem: MediaItem? = null,
        var related: List<RelatedEntry>? = null,
        var openingThemes: List<String>? = null,
        var endingThemes: List<String>? = null,
        var covers: List<String>? = null,
        var recommended: List<RecommendedEntry>? = null,
        var statusDistribution: StatusDistribution? = null,
        var characters: List<CharacterEntry>? = null,
        var reviews: List<ReviewEntry>? = null,
        var scoreStats: ScoreStats? = null,
        var relatedScroll: Pair<Int, Int> = 0 to 0,
        var recommendedScroll: Pair<Int, Int> = 0 to 0,
    )
    private val detailCaches = mutableMapOf<Pair<String, MediaType>, DetailCache>()
    private fun detailCache(id: String, type: MediaType) = detailCaches.getOrPut(id to type) { DetailCache() }
    // Read-only look at whatever's already cached for a title, without creating an
    // entry if there's nothing there yet (unlike detailCache() above). DetailScreen
    // uses this to seed its local state directly from the cache on first composition,
    // instead of always starting "empty/not-done" and waiting a frame for a
    // LaunchedEffect to catch up — that gap is what made returning from a title's
    // Reviews page (or any other overlay that tears DetailScreen down and rebuilds it)
    // flash the loading skeleton even though the data was already sitting in memory.
    data class DetailCacheSnapshot(
        val related: List<RelatedEntry>?,
        val openingThemes: List<String>?,
        val endingThemes: List<String>?,
        val covers: List<String>?,
        val recommended: List<RecommendedEntry>?,
        val statusDistribution: StatusDistribution?,
        val characters: List<CharacterEntry>?,
        val reviews: List<ReviewEntry>?,
    )
    fun peekDetailCache(id: String, type: MediaType): DetailCacheSnapshot? = detailCaches[id to type]?.let {
        DetailCacheSnapshot(it.related, it.openingThemes, it.endingThemes, it.covers, it.recommended, it.statusDistribution, it.characters, it.reviews)
    }
    // Drops every cached detail sub-section, and every remembered scroll position —
    // call this once the user has fully left the related/recommended chain (not on
    // every single step back within it), and when a brand-new, unrelated title is
    // opened from outside any chain.
    fun clearDetailCache() { detailCaches.clear(); detailScrollPositions.clear() }
    // Drops the cache + scroll position for exactly one title — call this when
    // stepping back past that single title within an active chain (see backDetail()
    // in Navigation.kt), so a title that's no longer reachable going forward doesn't
    // leave stale cached data/position lingering until the whole chain is eventually
    // backed out of.
    fun forgetDetailPage(id: String, type: MediaType) { detailCaches.remove(id to type); detailScrollPositions.remove(id to type) }
    // Scroll position for the Related/Recommended horizontal rows on the detail
    // page — separate from getDetailScroll/saveDetailScroll above, which track the
    // page's own vertical scroll. Without this, tapping an entry partway through
    // either row and coming back snapped it to the first item, since a LazyRow's
    // default scroll state doesn't survive this composable being torn down and
    // rebuilt on every related/recommended hop.
    fun getRelatedRowScroll(id: String, type: MediaType) = detailCache(id, type).relatedScroll
    fun saveRelatedRowScroll(id: String, type: MediaType, index: Int, offset: Int) { detailCache(id, type).relatedScroll = index to offset }
    fun getRecommendedRowScroll(id: String, type: MediaType) = detailCache(id, type).recommendedScroll
    fun saveRecommendedRowScroll(id: String, type: MediaType, index: Int, offset: Int) { detailCache(id, type).recommendedScroll = index to offset }
    // Same idea for a stack's own entry grid — restores scroll position when
    // coming back from an entry's detail page instead of resetting to top
    private val stackDetailScrollPositions = mutableMapOf<Int, Pair<Int, Int>>()
    fun getStackDetailScroll(stackId: Int) = stackDetailScrollPositions[stackId] ?: (0 to 0)
    fun saveStackDetailScroll(stackId: Int, index: Int, offset: Int) { stackDetailScrollPositions[stackId] = index to offset }
    // A stack's fetched entries, keyed by stack id — populated once per stack so
    // opening an entry from the grid and pressing back doesn't re-fetch (and
    // re-show a spinner for) the whole stack every time. mutableStateMapOf so
    // Compose recomposes StackDetailScreen when an entry lands. Cleared once the
    // user leaves the Interest Stacks section entirely (see clearStackDetailCache).
    private val stackDetailCache = mutableStateMapOf<Int, StackDetail>()
    private val stackDetailFailedIds = mutableStateMapOf<Int, Boolean>()
    fun getCachedStackDetail(stackId: Int): StackDetail? = stackDetailCache[stackId]
    fun stackDetailLoadFailed(stackId: Int): Boolean = stackDetailFailedIds[stackId] == true
    fun loadStackDetail(stackId: Int) {
        if (stackDetailCache.containsKey(stackId) || stackDetailFailedIds[stackId] == true) return
        viewModelScope.launch {
            val fetched = runCatching { StacksApi().detail(stackId) }.getOrNull()
            if (fetched != null) stackDetailCache[stackId] = fetched else stackDetailFailedIds[stackId] = true
        }
    }
    // Drops every cached stack's entries — call once the whole Interest Stacks
    // flow is left (not on every single back-tap within it), so the cache
    // doesn't grow unbounded and the next visit picks up fresh data from MAL.
    fun clearStackDetailCache() { stackDetailCache.clear(); stackDetailFailedIds.clear(); stackCoverCache.clear(); stackCoverInFlight.clear() }
    // Cover thumbnails for a stack's browse/search/spotlight row, keyed by stack id.
    // Those summaries almost never ship cover images themselves (see StacksApi.topCovers,
    // which has to fetch the whole detail page just to read a couple of entry covers off
    // it) — every row used to call that directly and independently the moment it scrolled
    // into view, so a 50-row Recent list meant up to 50 separate full-page fetches, and
    // scrolling back to a row already seen re-fetched it again since nothing remembered
    // the result outside that one composable instance. This cache makes it happen at most
    // once per stack for the life of the Interest Stacks session (cleared alongside
    // stackDetailCache above), reuses stackDetailCache for free when that stack's full
    // detail was already loaded some other way, and stackCoverInFlight stops two rows
    // referencing the same stack (e.g. it showing in both Spotlight and Recent) from
    // firing overlapping requests for it.
    private val stackCoverCache = mutableStateMapOf<Int, List<String>>()
    private val stackCoverInFlight = mutableSetOf<Int>()
    fun getCachedStackCovers(stackId: Int): List<String>? = stackCoverCache[stackId]
    fun loadStackCovers(stackId: Int) {
        if (stackCoverCache.containsKey(stackId) || stackId in stackCoverInFlight) return
        stackDetailCache[stackId]?.let { detail ->
            stackCoverCache[stackId] = detail.entries.mapNotNull { it.cover.takeIf(String::isNotBlank) }.take(3)
            return
        }
        stackCoverInFlight += stackId
        viewModelScope.launch {
            stackCoverCache[stackId] = runCatching { StacksApi().topCovers(stackId) }.getOrElse { emptyList() }
            stackCoverInFlight -= stackId
        }
    }
    // AniList's confirmed nextAiringEpisode for a currently-airing show, keyed by MAL id —
    // overrides MediaItem.nextEpisodeNumber()'s date-math guess (see Models.kt) wherever
    // AniList has an answer, since that guess drifts after a real-world delay/hiatus (a
    // skipped week still counts as "one week elapsed" in the date math). A null cache entry
    // is a resolved "AniList had nothing" (or the fetch failed) — callers just keep showing
    // the date-math label in that case, so it's cached too, to avoid refetching every
    // recomposition. Never persisted or cleared on refresh: it's a live, small, per-session
    // lookup, not something that goes stale across a single app session.
    private val airingCache = mutableStateMapOf<String, AiringInfo?>()
    private val airingInFlight = mutableSetOf<String>()
    fun getCachedAiring(malId: String): AiringInfo? = airingCache[malId]
    fun loadAiringEpisode(item: MediaItem) {
        val malId = item.id.toIntOrNull() ?: return
        if (item.id in airingCache || item.id in airingInFlight) return
        airingInFlight += item.id
        viewModelScope.launch {
            airingCache[item.id] = runCatching { AniListApi().nextAiringEpisode(malId) }.getOrNull()
            airingInFlight -= item.id
        }
    }
    // Reset scroll on sort
    fun selectListTypeTab(context: Context, t: MediaType) { listTypeTab = t; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_type_tab", t.name).apply() }
    fun loadListTypeTab(context: Context) { listTypeTab = runCatching { MediaType.valueOf(settingsPrefs(context).getString("list_type_tab", MediaType.Anime.name)!!) }.getOrDefault(MediaType.Anime) }
    // Which sub-section (Forums/Clubs) the combined Community tab is showing
    var communityTab by mutableStateOf(CommunityTab.Forums); private set
    fun selectCommunityTab(context: Context, t: CommunityTab) { communityTab = t; settingsPrefs(context).edit().putString("community_tab", t.name).apply() }
    fun loadCommunityTab(context: Context) { communityTab = runCatching { CommunityTab.valueOf(settingsPrefs(context).getString("community_tab", CommunityTab.Forums.name)!!) }.getOrDefault(CommunityTab.Forums) }
    fun setListSort(context: Context, sort: ListSort) { listSort = sort; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_sort", sort.name).apply() }
    fun loadListSort(context: Context) { listSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("list_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
    fun setListViewMode(context: Context, mode: ListViewMode) { listViewMode = mode; settingsPrefs(context).edit().putString("list_view_mode", mode.name).apply() }
    fun loadListViewMode(context: Context) { listViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("list_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    // Score distribution drill-down view mode — separate pref from list_view_mode above so
    // switching one screen's list/grid choice doesn't affect the other's
    var scoreFilterViewMode by mutableStateOf(ListViewMode.List); private set
    fun setScoreFilterViewMode(context: Context, mode: ListViewMode) { scoreFilterViewMode = mode; settingsPrefs(context).edit().putString("score_filter_view_mode", mode.name).apply() }
    fun loadScoreFilterViewMode(context: Context) { scoreFilterViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("score_filter_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    // Score distribution drill-down sort order — separate pref from list_sort above so
    // changing one screen's sort doesn't affect the other's
    var scoreFilterSort by mutableStateOf(ListSort.Score); private set
    fun setScoreFilterSort(context: Context, sort: ListSort) { scoreFilterSort = sort; settingsPrefs(context).edit().putString("score_filter_sort", sort.name).apply() }
    fun loadScoreFilterSort(context: Context) { scoreFilterSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("score_filter_sort", ListSort.Score.name)!!) }.getOrDefault(ListSort.Score) }
    // Year distribution drill-down view mode + sort — same pattern as the score filter
    // screen's own prefs above, kept separate so the two drill-down screens don't share state
    var yearFilterViewMode by mutableStateOf(ListViewMode.List); private set
    fun setYearFilterViewMode(context: Context, mode: ListViewMode) { yearFilterViewMode = mode; settingsPrefs(context).edit().putString("year_filter_view_mode", mode.name).apply() }
    fun loadYearFilterViewMode(context: Context) { yearFilterViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("year_filter_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    var yearFilterSort by mutableStateOf(ListSort.Title); private set
    fun setYearFilterSort(context: Context, sort: ListSort) { yearFilterSort = sort; settingsPrefs(context).edit().putString("year_filter_sort", sort.name).apply() }
    fun loadYearFilterSort(context: Context) { yearFilterSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("year_filter_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
    var formatFilterViewMode by mutableStateOf(ListViewMode.List); private set
    fun setFormatFilterViewMode(context: Context, mode: ListViewMode) { formatFilterViewMode = mode; settingsPrefs(context).edit().putString("format_filter_view_mode", mode.name).apply() }
    fun loadFormatFilterViewMode(context: Context) { formatFilterViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("format_filter_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    var formatFilterSort by mutableStateOf(ListSort.Title); private set
    fun setFormatFilterSort(context: Context, sort: ListSort) { formatFilterSort = sort; settingsPrefs(context).edit().putString("format_filter_sort", sort.name).apply() }
    fun loadFormatFilterSort(context: Context) { formatFilterSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("format_filter_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
    // Genre breakdown drill-down view mode + sort — same pattern as the format filter
    // screen's own prefs above, kept separate so the two drill-down screens don't share state
    var genreFilterViewMode by mutableStateOf(ListViewMode.List); private set
    fun setGenreFilterViewMode(context: Context, mode: ListViewMode) { genreFilterViewMode = mode; settingsPrefs(context).edit().putString("genre_filter_view_mode", mode.name).apply() }
    fun loadGenreFilterViewMode(context: Context) { genreFilterViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("genre_filter_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    var genreFilterSort by mutableStateOf(ListSort.Title); private set
    fun setGenreFilterSort(context: Context, sort: ListSort) { genreFilterSort = sort; settingsPrefs(context).edit().putString("genre_filter_sort", sort.name).apply() }
    fun loadGenreFilterSort(context: Context) { genreFilterSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("genre_filter_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
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
    var amoledDark by mutableStateOf(false); private set
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
    // All of these used to be plain `get()` properties that ran nsfwFiltered() (an
    // allocating filterNot pass) fresh on every single read. Each row in Discover/Home
    // reads its `visible*` property at least twice per recomposition (an `.isNotEmpty()`
    // check plus the itemsIndexed/items call that follows it), so that was two full list
    // scans for nothing every time nsfwEnabled or the underlying list was read, even when
    // neither had actually changed since the last read. derivedStateOf caches the result
    // and only recomputes when one of the state values it reads (the source list,
    // nsfwEnabled, etc.) actually changes — same fix already applied to
    // visibleDiscoverResultsState below, just extended to the rest of these.
    private val visibleItemsState by derivedStateOf { items.nsfwFiltered(nsfwEnabled) }
    val visibleItems: List<MediaItem> get() = visibleItemsState
    // Cached via derivedStateOf: filtering the whole (growing, paginated) results list is
    // read multiple times per recomposition (empty check, itemsIndexed, lastIndex), so this
    // avoids redoing that work on every read. Deliberately does NOT re-sort discoverResults —
    // it's already in the right order by construction (sorted once when a page is fetched/
    // appended, see runDiscoverSearch/loadMoreTitleSearch/selectDiscoverSort). Re-sorting the
    // whole accumulated list here on every append used to reshuffle rows already on screen
    // whenever a newly-loaded page's items outranked one you'd already scrolled past.
    private val visibleDiscoverResultsState by derivedStateOf {
        discoverResults.nsfwFiltered(nsfwEnabled)
            .filter { it.matches(discoverFilters) }
    }
    val visibleDiscoverResults: List<MediaItem> get() = visibleDiscoverResultsState
    private val visibleDiscoverNewSeasonState by derivedStateOf { discoverNewSeason.nsfwFiltered(nsfwEnabled) }
    val visibleDiscoverNewSeason: List<MediaItem> get() = visibleDiscoverNewSeasonState
    private val visibleDiscoverUpcomingState by derivedStateOf { discoverUpcoming.nsfwFiltered(nsfwEnabled) }
    val visibleDiscoverUpcoming: List<MediaItem> get() = visibleDiscoverUpcomingState
    private val visibleRecommendationsState by derivedStateOf { recommendations.nsfwFiltered(nsfwEnabled) }
    val visibleRecommendations: List<MediaItem> get() = visibleRecommendationsState
    private val visibleTrendingMangaState by derivedStateOf { trendingManga.nsfwFiltered(nsfwEnabled) }
    val visibleTrendingManga: List<MediaItem> get() = visibleTrendingMangaState
    private val visibleRankingResultsState by derivedStateOf { rankingResults.nsfwFiltered(nsfwEnabled) }
    val visibleRankingResults: List<MediaItem> get() = visibleRankingResultsState
    // Filter to premieres only
    private val visibleSeasonalResultsState by derivedStateOf {
        val filtered = if (seasonalContinuingOnly) seasonalResults
        else seasonalResults.filter { it.startDate == seasonalYear.toString() && it.season.equals(seasonalSeason.label, ignoreCase = true) }
        filtered.nsfwFiltered(nsfwEnabled)
    }
    val visibleSeasonalResults: List<MediaItem> get() = visibleSeasonalResultsState

    // Discover state survives navigation
    var discoverMode by mutableStateOf(DiscoverMode.Browse); private set
    var discoverQuery by mutableStateOf(""); private set
    var discoverTypeFilter by mutableStateOf("Anime"); private set
    var discoverResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverFilters by mutableStateOf(DiscoverFilters()); private set
    var discoverSort by mutableStateOf(DiscoverSort.Relevance); private set
    // MalGenreFiltered results are paginated 50 at a time straight off MAL's own genre-filtered
    // search — whatever's already in discoverResults is only however many pages happen to have
    // been scrolled to so far, not the whole genre. Re-sorting just that loaded slice client-side
    // (the old behavior) can't reproduce MAL's true "sorted by Members/Score/Newest" order, since
    // the highest-ranked items for that sort might sit on a page that hasn't been fetched yet.
    // So for that source, changing the sort re-runs the search from page 1 with the new sort
    // wired through to MalGenreApi (see sortParam there), which asks MAL to hand back
    // already-globally-sorted pages instead. Other sources (title search relevance, the
    // ranking-pool fallback) already have their whole pool loaded in one shot, so a plain
    // client-side re-sort of what's already there is correct for them and doesn't need a re-fetch.
    fun selectDiscoverSort(context: Context, sort: DiscoverSort) {
        discoverSort = sort
        if (discoverPaginationSource == DiscoverPaginationSource.MalGenreFiltered) {
            runDiscoverSearch(context, discoverQuery, discoverTypeFilter)
        } else {
            discoverResults = discoverResults.sortedForDiscover(sort, titleLanguage, discoverQuery)
        }
    }
    var discoverSearching by mutableStateOf(false); private set
    var discoverError by mutableStateOf<String?>(null); private set
    var discoverHasMore by mutableStateOf(false); private set
    var discoverLoadingMore by mutableStateOf(false); private set
    // Which real, further-paginated endpoint (if any) backs the current discoverResults —
    // the studio/author, multi-tag, and ranking-chart branches already pull their whole
    // pool in one shot, so for those the list really has ended and there's nothing to load.
    private enum class DiscoverPaginationSource { None, TitleSearch, MalGenreFiltered }
    private var discoverPaginationSource = DiscoverPaginationSource.None
    // Stashed only for the MalGenreFiltered source, so loadMoreDiscoverSearch() can ask for
    // the same genre/theme/demographic ids + media kind + format/status's next page
    private var discoverGenreKind: String? = null
    private var discoverGenreIds: List<Int> = emptyList()
    var discoverNewSeason by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverUpcoming by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverBrowseLoading by mutableStateOf(false); private set
    var discoverBrowseError by mutableStateOf<String?>(null); private set
    private var discoverBrowseLoaded = false
    // Guards backfillLibraryThemes below — same one-shot-per-session shape as
    // discoverBrowseLoaded above, so a fresh load() doesn't refire it after it already
    // ran once (e.g. on pull-to-refresh).
    private var libraryThemesBackfilled = false
    private var discoverSearchJob: kotlinx.coroutines.Job? = null
    // Tracks whichever loadMoreTitleSearch/loadMoreGenreFiltered coroutine is currently
    // in flight, so a fresh search (e.g. switching the Anime/Manga type chip mid-scroll)
    // can cancel it — otherwise a stale load-more can resolve after discoverResults has
    // already been reset for the new search and append the previous type's items into it,
    // which is both wrong data and a duplicate-key crash risk (see visibleDiscoverResults
    // key in DiscoverScreen).
    private var discoverLoadMoreJob: kotlinx.coroutines.Job? = null
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
    // Trending manga row (manga has no personalized suggestions endpoint on MAL, so this
    // uses the popularity ranking as a stand-in for "trending")
    var trendingManga by mutableStateOf<List<MediaItem>>(emptyList()); private set
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
    // Persist only valid hex. Debounced: HsvColorPicker calls this continuously while the
    // user is dragging the saturation/hue picker -- dozens of times per second, one call per
    // pixel of pointer movement -- so writing to SharedPreferences synchronously on every
    // call meant every drag gesture was also hammering the settings file with a fresh
    // Editor/HashMap allocation each frame. customColorHex (the in-memory state driving the
    // live preview swatch/theme) still updates immediately on every call -- only the actual
    // disk write is pushed 250ms out and cancelled/restarted by the next call, so it
    // collapses a whole drag gesture's worth of writes into one, right after the finger
    // lifts (or after a brief pause) instead of one per pixel.
    private var customColorPersistJob: kotlinx.coroutines.Job? = null
    fun setCustomColor(context: Context, hex: String) {
        customColorHex = hex
        if (parseHexColor(hex) == null) return
        customColorPersistJob?.cancel()
        customColorPersistJob = viewModelScope.launch {
            delay(250)
            settingsPrefs(context).edit().putString("custom_color_hex", hex).apply()
        }
    }
    fun loadTitleLanguage(context: Context) { titleLanguage = runCatching { TitleLanguage.valueOf(settingsPrefs(context).getString("title_language", TitleLanguage.Romaji.name)!!) }.getOrDefault(TitleLanguage.Romaji) }
    fun setTitleLanguage(context: Context, lang: TitleLanguage) { titleLanguage = lang; settingsPrefs(context).edit().putString("title_language", lang.name).apply() }
    fun loadListFilter(context: Context) { listFilter = settingsPrefs(context).getString("list_filter", "All") ?: "All" }
    fun setListFilter(context: Context, filter: String) { listFilter = filter; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_filter", filter).apply() }
    fun loadNsfwPref(context: Context) { nsfwEnabled = settingsPrefs(context).getBoolean("nsfw_enabled", false) }
    fun setNsfw(context: Context, enabled: Boolean) { nsfwEnabled = enabled; settingsPrefs(context).edit().putBoolean("nsfw_enabled", enabled).apply() }
    fun loadAmoledDark(context: Context) { amoledDark = settingsPrefs(context).getBoolean("amoled_dark", false) }
    fun setAmoledDark(context: Context, enabled: Boolean) { amoledDark = enabled; settingsPrefs(context).edit().putBoolean("amoled_dark", enabled).apply() }

    // Load profile and stats
    fun loadProfile(context: Context) {
        val api = MalApi(context); if (!api.signedIn) { malProfile = null; return }
        profileLoading = true
        viewModelScope.launch { runCatching { api.profile() }.onSuccess { malProfile = it }; profileLoading = false }
    }

    fun load(context: Context) {
        val api = MalApi(context); signedIn = api.signedIn; authChecked = true; if (!signedIn) return
        loading = true
        viewModelScope.launch { runCatching { api.library() }.onSuccess { items = it; backfillLibraryThemes() }.onFailure { error = it.message ?: "Could not load your MAL list" }; loading = false }
        loadProfile(context)
    }
    // MalApi.fetchList (the official MAL API) requests a "themes"/"demographics" field
    // per item, but those aren't actually part of MAL's official API — MAL silently
    // ignores field names it doesn't recognize rather than erroring, so every item
    // that comes back from load() has contentThemes/demographics = emptyList(), which
    // left theme/demographic filters unable to match anything against the library.
    // Tenrai (the Jikan-backed API already used for search/discover) does have real
    // theme data per title, and TenraiApi.fetchItemFacets already exists for exactly
    // this "we have the id, look this one item up" case (previously only used to
    // enrich author/studio search rows) — this just runs that same lookup across every
    // library item once per session and merges the results back in.
    // In-memory only, not persisted to disk (same as detailCaches below) — a fresh app
    // launch re-runs it, which is fine: Tenrai's shared request throttle (see
    // TenraiApi.getRaw) already caps this at a few concurrent requests with 429 backoff,
    // so fanning out one request per title can't hammer the API even for a large library.
    private fun backfillLibraryThemes() {
        if (libraryThemesBackfilled || items.isEmpty()) return
        libraryThemesBackfilled = true
        val targets = items.filter { it.contentThemes.isEmpty() && it.demographics.isEmpty() }
            .mapNotNull { item -> item.id.toIntOrNull()?.let { intId -> item to intId } }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val tenrai = TenraiApi()
            val results = coroutineScope {
                targets.map { (item, intId) ->
                    async {
                        val kind = if (item.type == MediaType.Anime) "anime" else "manga"
                        Triple(item.id, item.type, runCatching { tenrai.fetchItemFacets(kind, intId) }.getOrNull())
                    }
                }.awaitAll()
            }
            val byKey = results.mapNotNull { (id, type, facets) -> facets?.let { (id to type) to it } }.toMap()
            if (byKey.isNotEmpty()) {
                items = items.map { item -> byKey[item.id to item.type]?.let { f -> item.copy(contentThemes = f.contentThemes, demographics = f.demographics) } ?: item }
            }
        }
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
    fun signOut(context: Context) { MalApi(context).signOut(); signedIn = false; items = emptyList(); malProfile = null; libraryThemesBackfilled = false }

    // Load home browse rows
    fun loadDiscoverBrowse(context: Context) {
        // Reuse one MalApi instance for the signedIn check and the actual calls below
        // instead of constructing a fresh one for each — construction is cheap (Android
        // caches SharedPreferences instances by name internally) but still unnecessary churn.
        val api = MalApi(context)
        if (discoverBrowseLoaded || !api.signedIn) return
        discoverBrowseLoaded = true
        discoverBrowseLoading = true
        viewModelScope.launch {
            // These two endpoints don't depend on each other, but `a() to b()` was awaiting
            // them one after another — the second request didn't even start until the first
            // fully returned. Home's "Airing next" row (the first content Home shows) waits
            // on discoverNewSeason, so that serial round-trip was a direct hit to how fast
            // Home had anything to display. Firing both under one coroutineScope like
            // loadStacksHome() already does below lets them run concurrently instead.
            runCatching {
                coroutineScope {
                    val season = async { api.seasonalAnime(100) }
                    val up = async { api.upcomingAnime(10) }
                    season.await() to up.await()
                }
            }
                .onSuccess { (season, up) -> discoverNewSeason = season; discoverUpcoming = up; discoverBrowseError = null }
                .onFailure { discoverBrowseError = it.message ?: "Could not load Discover" }
            discoverBrowseLoading = false
        }
    }
    // Load recommendations + trending manga rows
    fun loadHomeExtras(context: Context) {
        val api = MalApi(context)
        if (homeExtrasLoaded || !api.signedIn) return
        homeExtrasLoaded = true
        viewModelScope.launch { runCatching { api.animeSuggestions(100) }.onSuccess { recommendations = it } }
        viewModelScope.launch { runCatching { api.ranking(MediaType.Manga, "bypopularity", limit = 10) }.onSuccess { trendingManga = it } }
    }
    // (Re)run ranking chart
    fun loadRanking(context: Context, type: MediaType, sort: RankingSort) {
        rankingType = type; rankingSort = if (type == MediaType.Manga && sort == RankingSort.Upcoming) RankingSort.Score else sort
        val api = MalApi(context)
        if (!api.signedIn) { rankingError = "Sign in from Profile to view rankings"; return }
        viewModelScope.launch {
            rankingLoading = true
            runCatching { api.ranking(rankingType, rankingSort.apiValue()) }
                .onSuccess { rankingResults = it; rankingError = null }
                .onFailure { rankingError = it.message ?: "Could not load ranking" }
            rankingLoading = false
        }
    }
    // (Re)run seasonal chart
    fun loadSeasonal(context: Context, year: Int = seasonalYear, season: SeasonName = seasonalSeason, sort: SeasonalSort = seasonalSort, continuingOnly: Boolean = seasonalContinuingOnly) {
        seasonalYear = year; seasonalSeason = season; seasonalSort = sort; seasonalContinuingOnly = continuingOnly
        val api = MalApi(context)
        if (!api.signedIn) { seasonalError = "Sign in from Profile to browse seasons"; return }
        viewModelScope.launch {
            seasonalLoading = true
            runCatching { api.seasonalAnime(year, season.api, sort = sort.api) }
                // Not reconciled against the library here — the status badge is a live
                // vm.trackedStatus() lookup at render time instead (see SeasonalGridCard/
                // ScheduleRow), so it stays accurate after later edits/deletes without a re-fetch.
                .onSuccess { seasonalResults = it.items; seasonalHasMore = it.hasMore; seasonalError = null }
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
                // Not reconciled against the library — see loadSeasonal's comment above.
                .onSuccess { seasonalResults = seasonalResults + it.items; seasonalHasMore = it.hasMore }
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
        val api = MalApi(context)
        if (!api.signedIn) { discoverSuggestions = emptyList(); return }
        discoverSuggestJob = viewModelScope.launch {
            delay(100) // debounce so we're not firing a request per keystroke
            val t = when (type) { "Anime" -> MediaType.Anime; "Manga" -> MediaType.Manga; else -> null }
            runCatching { api.suggestTitles(query, t) }
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
        discoverLoadMoreJob?.cancel()
        discoverHasMore = false; discoverPaginationSource = DiscoverPaginationSource.None
        if (query.isBlank() && !filters.isActive()) { discoverResults = emptyList(); discoverSearching = false; discoverError = null; return }
        // MAL's own search endpoint rejects queries under 3 characters with a 400
        // (see MalApi.searchKind), which otherwise surfaced as a raw "MAL request
        // failed (400): ..." message. Only the free-text query goes through that
        // endpoint — creator/genre-filter searches don't — so a too-short query is
        // only a problem when there's no filter-driven search to fall back on.
        if (query.isNotBlank() && query.trim().length < 3 && !filters.isActive()) {
            discoverResults = emptyList(); discoverSearching = false
            discoverError = "Type at least 3 characters to search"
            return
        }
        val api = MalApi(context)
        if (!api.signedIn) { discoverError = "Sign in from Profile to search MyAnimeList"; return }
        discoverSearchJob = viewModelScope.launch {
            discoverSearching = true
            discoverHasMore = false; discoverPaginationSource = DiscoverPaginationSource.None
            val t = when (type) { "Anime" -> MediaType.Anime; "Manga" -> MediaType.Manga; else -> null }
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
                    else if (query.isNotBlank()) {
                        // The only branch backed by a real, further-paginated MAL endpoint —
                        // record that so loadMoreDiscoverSearch() knows it's safe to page it.
                        val page = api.search(query, t)
                        discoverPaginationSource = DiscoverPaginationSource.TitleSearch; discoverHasMore = page.hasMore
                        page.items
                    }
                    // Search via genre/theme/demographic filters, straight off MAL's own
                    // anime.php/manga.php advanced search (MalGenreApi) — every selected tag
                    // gets ANDed together and filtered server-side by MAL itself, along with
                    // format/status, rather than the old Tenrai candidate-pool approach that
                    // only handled a single tag that way and fell back to a members-ranked
                    // pool (capped at each chart's top ~500) for anything broader.
                    else if (filters.genres.isNotEmpty() || filters.themes.isNotEmpty() || filters.demographics.isNotEmpty()) {
                        val malGenre = MalGenreApi()
                        val tenrai = TenraiApi() // only for the static name->id lookup table, not a search
                        val kinds = t?.let { listOf(if (it == MediaType.Anime) "anime" else "manga") } ?: listOf("anime", "manga")
                        val names = filters.genres + filters.themes + filters.demographics
                        // "All" media type can't be mapped onto one search (anime and manga
                        // don't share a genre id space), so it still merges two separate
                        // paginated searches — this just isn't the old members-ranked pool.
                        val pages = coroutineScope {
                            kinds.map { kind ->
                                async {
                                    val ids = runCatching { tenrai.resolveGenreIds(kind, names) }.getOrElse { emptyList() }
                                    if (ids.isEmpty()) null else runCatching {
                                        malGenre.search(kind, ids, malTypeCode(kind, filters.format), malStatusCode(filters.airingStatus, kind), page = 1, includeAdult = nsfwEnabled, sort = discoverSort)
                                    }.getOrNull()?.let { kind to it }
                                }
                            }.awaitAll().filterNotNull()
                        }
                        val singleKind = kinds.singleOrNull()
                        if (singleKind != null && pages.isNotEmpty()) {
                            val (_, page) = pages.first()
                            discoverPaginationSource = DiscoverPaginationSource.MalGenreFiltered
                            discoverHasMore = page.hasMore
                            discoverGenreKind = singleKind
                            discoverGenreIds = runCatching { tenrai.resolveGenreIds(singleKind, names) }.getOrElse { emptyList() }
                            page.items
                        } else if (pages.isNotEmpty()) {
                            // "All" media type: no single next-page cursor to stash, so this
                            // shape doesn't support loadMoreDiscoverSearch — same limitation
                            // the old multi-kind fallback had.
                            pages.flatMap { (_, page) -> page.items }.distinctBy { it.id }
                        } else {
                            // Genre/theme/demographic name didn't resolve to an id, or every
                            // request failed outright — fall back to a broad ranking pool
                            // (filtered client-side by matches()) so the person still sees
                            // something instead of a blank screen.
                            coroutineScope {
                                (t?.let { listOf(it) } ?: MediaType.entries.toList()).flatMap { mt ->
                                    listOf("all", "bypopularity", "favorite").map { rankType -> async { api.ranking(mt, rankType, limit = 500) } }
                                }.awaitAll().flatten()
                            }.distinctBy { it.id }
                        }
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
                // Sort once, here, at fetch time — not reactively on every read (see
                // visibleDiscoverResults). Deliberately NOT reconciled against the library here
                // anymore: that used to bake each matching item's status permanently into
                // discoverResults, which then stayed stale after later editing or deleting that
                // title elsewhere (or, worse, kept showing a status after a delete because the
                // baked copy still had inUserList = true). The status badge now always comes
                // from a live vm.trackedStatus() lookup at render time instead (see DiscoverScreen).
                results.sortedForDiscover(discoverSort, titleLanguage, query)
            }
                .onSuccess { discoverResults = it; discoverError = null }
                .onFailure {
                    // Cancellation isn't a real failure — it fires whenever a newer search
                    // supersedes this one (e.g. tapping the Manga chip while Anime is still
                    // loading cancels this job via discoverSearchJob?.cancel() above). Plain
                    // runCatching catches CancellationException like any other Throwable, so
                    // without this check the superseded job would stomp discoverError with
                    // "StandaloneCoroutine was cancelled" right after the new search's own
                    // results had already rendered. Rethrowing lets it finish cancelling
                    // silently instead, the way coroutine cancellation is meant to work.
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    discoverError = it.message ?: "Search failed"; discoverHasMore = false; discoverPaginationSource = DiscoverPaginationSource.None
                }
            discoverSearching = false
        }
    }
    // Load next page of search results — a no-op unless the current results came from a
    // real, further-paginated search (see DiscoverPaginationSource / runDiscoverSearch)
    fun loadMoreDiscoverSearch(context: Context) {
        if (discoverSearching || discoverLoadingMore || !discoverHasMore) return
        when (discoverPaginationSource) {
            DiscoverPaginationSource.TitleSearch -> loadMoreTitleSearch(context)
            DiscoverPaginationSource.MalGenreFiltered -> loadMoreGenreFiltered()
            DiscoverPaginationSource.None -> {}
        }
    }
    private fun loadMoreTitleSearch(context: Context) {
        val api = MalApi(context)
        if (!api.signedIn) return
        val t = when (discoverTypeFilter) { "Anime" -> MediaType.Anime; "Manga" -> MediaType.Manga; else -> null }
        discoverLoadMoreJob = viewModelScope.launch {
            discoverLoadingMore = true
            runCatching { api.search(discoverQuery, t, offset = discoverResults.size) }
                // Reconcile against user's library, then dedupe against what's already shown
                // and sort only the newly-fetched items among themselves before appending.
                // Never re-sorts the already-displayed prefix — that's what used to make rows
                // already on screen jump position every time a new page landed.
                .onSuccess { page ->
                    val existingKeys = discoverResults.mapTo(HashSet()) { it.id to it.type }
                    // Not reconciled against the library — see runDiscoverSearch's comment above.
                    val newItems = page.items
                        .filter { (it.id to it.type) !in existingKeys }
                        .distinctBy { it.id to it.type }
                        .sortedForDiscover(discoverSort, titleLanguage, discoverQuery)
                    discoverResults = discoverResults + newItems
                    discoverHasMore = page.hasMore
                }
                .onFailure { discoverHasMore = false }
            discoverLoadingMore = false
        }
    }
    private fun loadMoreGenreFiltered() {
        val kind = discoverGenreKind ?: return
        val ids = discoverGenreIds.ifEmpty { return }
        // MAL's own advanced-search pager has a fixed 50-row page size (see
        // MalGenreApi.pageSize) — there's no smaller adjustable limit to slice against, so
        // the next page number is just how many MAL-sized pages we've already shown.
        val nextPage = (discoverResults.size / MalGenreApi().pageSize) + 1
        discoverLoadMoreJob = viewModelScope.launch {
            discoverLoadingMore = true
            runCatching {
                MalGenreApi().search(kind, ids, malTypeCode(kind, discoverFilters.format), malStatusCode(discoverFilters.airingStatus, kind), page = nextPage, includeAdult = nsfwEnabled, sort = discoverSort)
            }
                // Same reasoning as loadMoreTitleSearch: sort only the new page, append after
                // the already-displayed items instead of re-sorting the whole merged list.
                .onSuccess { page ->
                    val existingKeys = discoverResults.mapTo(HashSet()) { it.id to it.type }
                    // Not reconciled against the library — see runDiscoverSearch's comment above.
                    val newItems = page.items
                        .filter { (it.id to it.type) !in existingKeys }
                        .distinctBy { it.id to it.type }
                        .sortedForDiscover(discoverSort, titleLanguage, discoverQuery)
                    discoverResults = discoverResults + newItems
                    discoverHasMore = page.hasMore
                }
                .onFailure { discoverHasMore = false }
            discoverLoadingMore = false
        }
    }
    // Return to browse view
    fun exitDiscoverSearch() {
        discoverSort = DiscoverSort.Relevance
        discoverSearchJob?.cancel()
        discoverLoadMoreJob?.cancel()
        discoverSuggestJob?.cancel(); discoverSuggestions = emptyList()
        discoverMode = DiscoverMode.Browse; discoverQuery = ""; discoverResults = emptyList(); discoverFilters = DiscoverFilters(); discoverError = null
        discoverHasMore = false; discoverLoadingMore = false; discoverPaginationSource = DiscoverPaginationSource.None
        discoverGenreKind = null; discoverGenreIds = emptyList()
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
        val api = MalApi(context)
        if ((forumBoardsLoaded && !force) || !api.signedIn) return
        forumBoardsLoaded = true
        forumBoardsLoading = true
        viewModelScope.launch {
            runCatching { api.forumBoards() }
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
        val api = MalApi(context)
        if (!api.signedIn) { forumTopicsError = "Sign in from Profile to browse the forums"; return }
        val newsBoard = forumIsNewsBoard
        forumTopicsJob = viewModelScope.launch {
            forumTopicsLoading = true
            runCatching { api.forumTopics(boardId = forumBoardId, subboardId = forumSubboardId, query = forumQuery, withThumbnails = newsBoard) }
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
        val api = MalApi(context)
        if ((newsSnapshotsLoaded && !force) || !api.signedIn) return
        newsSnapshotsLoaded = true
        newsSnapshotsLoading = true
        viewModelScope.launch {
            runCatching { api.newsSnapshots() }
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
        val cache = detailCache(entry.malId.toString(), type)
        cache.resolvedItem?.let { onLoaded(it); return }
        relatedLoadingId = entry.malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(entry.malId, type) }
                .onSuccess { cache.resolvedItem = it; onLoaded(it) }
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
                // limit matches each row's own .take() below — no reason to parse every
                // stack on the page just to discard all but the first one or two.
                val ch = async { runCatching { api.search(StackBrowseKind.Challenges, limit = 2) }.getOrElse { emptyList() } }
                val mg = async { runCatching { api.search(StackBrowseKind.Manga, limit = 1) }.getOrElse { emptyList() } }
                val an = async { runCatching { api.search(StackBrowseKind.Anime, limit = 1) }.getOrElse { emptyList() } }
                val mal = async { runCatching { api.search(StackBrowseKind.MyAnimeList, limit = 1) }.getOrElse { emptyList() } }
                // Home's "Recent" row only ever shows a handful — the rest is one tap
                // away via "See all" / search (which opens the full paginated browse
                // screen), so there's no reason to fetch or render more than that here.
                val rc = async { runCatching { api.search(StackBrowseKind.All, limit = 5) }.getOrElse { emptyList() } }
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
            homeLatestStack = runCatching { StacksApi().search(StackBrowseKind.All, limit = 1).firstOrNull() }.getOrNull()
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

    // Coalesces the detail-page backfills below (related, themes, covers, status
    // distribution, recommended) into a single round trip per title. These used to each
    // independently call MalApi.detail() and/or MalDetailScrapeApi().fetch() the moment
    // DetailScreen mounted — up to 4 identical calls to the same MAL API detail endpoint
    // plus 2 identical scrapes of the same HTML page, just because each backfill function
    // only read the one field it needed off its own copy of the response. This does exactly
    // one MalApi.detail() call and one MalDetailScrapeApi().fetch() call, run in parallel,
    // keyed by (id, type) so overlapping callers (all five backfills fire from DetailScreen's
    // LaunchedEffects in the same frame) share the single in-flight request instead of each
    // triggering their own, then fans the combined result out to every cache field at once.
    private val detailFetchInFlight = mutableMapOf<Pair<String, MediaType>, Deferred<Unit>>()
    private fun ensureDetailFetched(context: Context, id: String, type: MediaType): Deferred<Unit> {
        val key = id to type
        detailFetchInFlight[key]?.let { return it }
        val cache = detailCache(id, type)
        val intId = id.toIntOrNull()
        val deferred = viewModelScope.async {
            if (intId == null) return@async
            coroutineScope {
                val apiDeferred = async { runCatching { MalApi(context).detail(intId, type) }.getOrNull() }
                val scrapeDeferred = async { runCatching { MalDetailScrapeApi().fetch(intId, type) }.getOrNull() }
                val fresh = apiDeferred.await()
                val scraped = scrapeDeferred.await()
                // Only cached on a successful detail() call — same as the old individual
                // backfills, so a failed fetch still retries next time instead of caching
                // a permanent blank.
                if (fresh != null) {
                    cache.openingThemes = fresh.openingThemes
                    cache.endingThemes = fresh.endingThemes
                    cache.covers = fresh.covers
                    cache.statusDistribution = fresh.statusDistribution
                }
                // Related: the official API's related_anime/related_manga fields are
                // same-type only in practice — an anime detail request reliably returns
                // related_anime but usually comes back empty for related_manga (manga/light
                // novel adaptations), and vice versa on manga pages — even though MAL's own
                // Related Entries box on the website always lists every direction. So that
                // box is scraped directly and merged with whatever the API did return; the
                // API result stays as a fallback if the scrape itself came back empty.
                // Entries with a resolved malId are deduped by (id, type); a handful of very
                // old/obscure related titles have no malId at all (MAL never linked them to a
                // page), so those are kept as-is, deduped by title instead so they don't all
                // collapse onto the same "id 0" key. Cached even when empty — a title with
                // genuinely no related entries (common for standalone manga/webtoons) would
                // otherwise never satisfy isNotEmpty(), leaving cache.related null forever and
                // forcing a fresh network fetch every single time this screen remounts.
                val apiRelated = fresh?.related ?: emptyList()
                val scrapedRelated = scraped?.related ?: emptyList()
                cache.related = (scrapedRelated + apiRelated).distinctBy { if (it.malId > 0) "id:${it.malType}:${it.malId}" else "title:${it.malType}:${it.title}" }
                // Recommended: the website's Recommendations widget is a superset of what the
                // official API can give us (includes MAL's own algorithmic "AutoRec" picks the
                // API never exposes), so it's preferred; the official API call's own
                // recommendations field is kept only as a fallback in case the scrape came
                // back empty. Cached even when empty, same reasoning as related above.
                cache.recommended = scraped?.recommended?.ifEmpty { fresh?.recommended ?: emptyList() } ?: (fresh?.recommended ?: emptyList())
            }
        }
        detailFetchInFlight[key] = deferred
        deferred.invokeOnCompletion { detailFetchInFlight.remove(key) }
        return deferred
    }

    // Backfill empty related row
    fun backfillRelated(context: Context, id: String, type: MediaType, onFound: (List<RelatedEntry>) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(id, type)
        cache.related?.let { onFound(it); onDone(); return }
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, id, type).await()
            val merged = cache.related ?: emptyList()
            if (merged.isNotEmpty()) onFound(merged)
            onDone()
        }
    }

    // Backfill empty theme fields — anime only. Manga has no OP/ED field on MAL at
    // all, so a fetch here could never come back non-empty; without this guard the
    // old code kept the network call but only ever cached a *non-empty* result,
    // meaning it silently refired every time a manga detail page was mounted or
    // returned to, instead of remembering "checked, nothing there" like it does
    // for anime titles that happen to have no themes.
    fun backfillThemes(context: Context, id: String, type: MediaType, onFound: (List<String>, List<String>) -> Unit, onDone: () -> Unit = {}) {
        if (type != MediaType.Anime) { onDone(); return }
        val cache = detailCache(id, type)
        val cachedOp = cache.openingThemes; val cachedEd = cache.endingThemes
        if (cachedOp != null || cachedEd != null) { onFound(cachedOp ?: emptyList(), cachedEd ?: emptyList()); onDone(); return }
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, id, type).await()
            val op = cache.openingThemes ?: emptyList(); val ed = cache.endingThemes ?: emptyList()
            if (op.isNotEmpty() || ed.isNotEmpty()) onFound(op, ed)
            onDone()
        }
    }

    // Backfill missing cover gallery
    fun backfillCovers(context: Context, id: String, type: MediaType, onFound: (List<String>) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(id, type)
        cache.covers?.let { onFound(it); onDone(); return }
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, id, type).await()
            val covers = cache.covers ?: emptyList()
            if (covers.size > 1) onFound(covers)
            onDone()
        }
    }

    // Load characters row (feeds both the Characters row and the Japanese Voice
    // Actors row on the detail page — staff is no longer shown there, so this no
    // longer fans out a second fetchStaff() network call alongside it).
    // onError fires when the fetch itself fails (network/DNS/etc — see
    // TenraiApi.fetchCharacters) as opposed to a title that just has no characters
    // listed, so the detail page can show a retryable failure state instead of
    // silently treating a blocked request the same as "no cast data".
    fun loadCharacters(item: MediaItem, onFound: (List<CharacterEntry>) -> Unit, onDone: () -> Unit = {}, onError: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.characters?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        val kind = if (item.type == MediaType.Anime) "anime" else "manga"
        viewModelScope.launch {
            runCatching { TenraiApi().fetchCharacters(kind, intId) }
                .onSuccess { chars ->
                    cache.characters = chars
                    if (chars.isNotEmpty()) onFound(chars)
                }
                .onFailure { onError() }
            onDone()
        }
    }

    // Load reviews row
    fun loadReviews(item: MediaItem, onFound: (List<ReviewEntry>) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.reviews?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        val kind = if (item.type == MediaType.Anime) "anime" else "manga"
        viewModelScope.launch {
            runCatching { TenraiApi().fetchReviews(kind, intId) }
                .onSuccess { cache.reviews = it; if (it.isNotEmpty()) onFound(it) }
            onDone()
        }
    }

    // Community score breakdown for the Score Stats screen, opened via "See more" on
    // Status distribution. Its own on-demand load (not folded into ensureDetailFetched
    // above) since it's a separate MAL page (/stats) that most people opening a title
    // will never actually tap into.
    fun loadScoreStats(item: MediaItem, onFound: (ScoreStats) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.scoreStats?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalDetailScrapeApi().fetchScoreStats(intId, item.type, item.title) }
                .onSuccess { cache.scoreStats = it; if (it.total > 0) onFound(it) }
            onDone()
        }
    }

    // Backfill recommended row
    fun loadUserRecommendations(context: Context, item: MediaItem, onFound: (List<RecommendedEntry>) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.recommended?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, item.id, item.type).await()
            val result = cache.recommended ?: emptyList()
            if (result.isNotEmpty()) onFound(result)
            onDone()
        }
    }

    // Open recommended-row title
    fun openRecommended(context: Context, entry: RecommendedEntry, onLoaded: (MediaItem) -> Unit) {
        val type = if (entry.malType == "anime") MediaType.Anime else MediaType.Manga
        val cache = detailCache(entry.malId.toString(), type)
        cache.resolvedItem?.let { onLoaded(it); return }
        recommendedLoadingId = entry.malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(entry.malId, type) }
                .onSuccess { cache.resolvedItem = it; onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            recommendedLoadingId = null
        }
    }

    // Load status distribution data
    fun loadStatusDistribution(context: Context, item: MediaItem, onFound: (StatusDistribution) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.statusDistribution?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null || item.type != MediaType.Anime) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, item.id, item.type).await()
            cache.statusDistribution?.let { if (it.total > 0) onFound(it) }
            onDone()
        }
    }
}