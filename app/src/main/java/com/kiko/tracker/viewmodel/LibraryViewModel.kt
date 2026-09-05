@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.viewmodel

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
import com.kiko.tracker.data.api.AiringInfo
import com.kiko.tracker.data.api.AniListApi
import com.kiko.tracker.data.api.ClubsPage
import com.kiko.tracker.data.api.ForumBoard
import com.kiko.tracker.data.api.ForumCategory
import com.kiko.tracker.data.api.ForumSubboard
import com.kiko.tracker.data.api.ForumTopic
import com.kiko.tracker.data.api.MalApi
import com.kiko.tracker.data.api.MalCharacterApi
import com.kiko.tracker.data.api.MalClub
import com.kiko.tracker.data.api.MalCompanyApi
import com.kiko.tracker.data.api.MalDetailScrapeApi
import com.kiko.tracker.data.api.MalGenreApi
import com.kiko.tracker.data.api.MalGenreLookup
import com.kiko.tracker.data.api.MalPeopleApi
import com.kiko.tracker.data.api.MalProfile
import com.kiko.tracker.data.api.NewsSnapshot
import com.kiko.tracker.data.api.RecommendedEntry
import com.kiko.tracker.data.api.StackBrowseKind
import com.kiko.tracker.data.api.StackDetail
import com.kiko.tracker.data.api.StackSummary
import com.kiko.tracker.data.api.StackTitleEntry
import com.kiko.tracker.data.api.StacksApi
import com.kiko.tracker.data.api.TenraiApi
import com.kiko.tracker.data.api.firstImageUrl
import com.kiko.tracker.data.model.CharacterDetail
import com.kiko.tracker.data.model.CharacterEntry
import com.kiko.tracker.data.model.CharacterSummary
import com.kiko.tracker.data.model.ColorSource
import com.kiko.tracker.data.model.CommunityTab
import com.kiko.tracker.data.model.CompanyDetail
import com.kiko.tracker.data.model.CompanyNews
import com.kiko.tracker.data.model.CompanySummary
import com.kiko.tracker.data.model.Destination
import com.kiko.tracker.data.model.DiscoverFilters
import com.kiko.tracker.data.model.DiscoverMode
import com.kiko.tracker.data.model.DiscoverSort
import com.kiko.tracker.data.model.FeaturedArticleEntry
import com.kiko.tracker.data.model.ForumMode
import com.kiko.tracker.data.model.ListSort
import com.kiko.tracker.data.model.ListViewMode
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.PaletteStyle
import com.kiko.tracker.data.model.PersonDetail
import com.kiko.tracker.data.model.PersonSummary
import com.kiko.tracker.data.model.RankingSort
import com.kiko.tracker.data.model.RelatedEntry
import com.kiko.tracker.data.model.ReviewEntry
import com.kiko.tracker.data.model.ScoreStats
import com.kiko.tracker.data.model.SeasonName
import com.kiko.tracker.data.model.SeasonalSort
import com.kiko.tracker.data.model.StatusDistribution
import com.kiko.tracker.data.model.ThemeMode
import com.kiko.tracker.data.model.TitleLanguage
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.currentSeasonName
import com.kiko.tracker.data.model.malStatusCode
import com.kiko.tracker.data.model.malTypeCode
import com.kiko.tracker.data.model.matches
import com.kiko.tracker.data.model.nowIso
import com.kiko.tracker.data.model.nsfwFiltered
import com.kiko.tracker.data.model.sortedForDiscover
import com.kiko.tracker.ui.screens.normalizeFilterForType
import com.kiko.tracker.ui.screens.sortedWithListSort
import com.kiko.tracker.ui.theme.parseHexColor
import com.kiko.tracker.util.AppUpdateChecker
import com.kiko.tracker.util.AppUpdateInfo
import com.kiko.tracker.util.settingsPrefs

// @Stable, not @Immutable: this
// mutableStateOf), but it's always
// out once by viewModel()
// snapshot system, so reads
// regardless. What @Stable buys
// composables several layers deep
// annotation the Compose compiler
// behaves this way —
// `vm: LibraryViewModel` as a
// parameter, even when nothing
// scrolling grid of cards
// change (e.g. a snackbar
// just the composables that
@Stable
class LibraryViewModel : ViewModel() {
    // No longer used by
    // (MalGenreApi) scrapes MAL's own
    // 50-row page size of
    // Kept only in case
    private val TENRAI_SEARCH_PAGE_LIMIT = 10
    // Start with empty list
    var items by mutableStateOf(emptyList<MediaItem>()); private set
    // O(1) library lookup by
    // results against the user's
    // a full linear scan
    // 20-50 item page against
    // derivedStateOf caches this and
    private val itemsByKey: Map<Pair<String, MediaType>, MediaItem> by derivedStateOf {
        items.associateBy { it.id to it.type }
    }
    // Live "is this tracked,
    // own list (search/discover/seasonal/ranking results)
    // on every call, so
    // of only after the
    fun trackedStatus(item: MediaItem): WatchStatus? = itemsByKey[item.id to item.type]?.status
    var destination by mutableStateOf(Destination.Home)
    // Avatar popup menu (profile/settings),
    // that avatar's on-screen bounds
    // itself, so the popup
    // was opened from.
    var profileDrawerOpen by mutableStateOf(false)
    var profileMenuAnchor by mutableStateOf<Rect?>(null)
    var signedIn by mutableStateOf(false); var loading by mutableStateOf(false); var error by mutableStateOf<String?>(null)
    // Whether the initial signed-in
    // itself defaults to false
    // and genuinely signed out"
    // "Please sign in" prompt)
    // on every cold start,
    // LaunchedEffect that calls load()
    // authChecked too so nothing
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
    // Jump My List to
    // tapping it lands on
    // Switches to the item's
    // surfaces items in that
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
    // Home tab scroll —
    // all, so opening a
    // scratch at the top,
    // above was already added
    var homeScrollIndex by mutableStateOf(0); private set
    var homeScrollOffset by mutableStateOf(0); private set
    fun saveHomeScroll(index: Int, offset: Int) { homeScrollIndex = index; homeScrollOffset = offset }
    // Discover landing/browse tab scroll
    // which is the search-results
    var discoverBrowseScrollIndex by mutableStateOf(0); private set
    var discoverBrowseScrollOffset by mutableStateOf(0); private set
    fun saveDiscoverBrowseScroll(index: Int, offset: Int) { discoverBrowseScrollIndex = index; discoverBrowseScrollOffset = offset }
    // Bumped by BottomBar on
    // Discover landing page. DiscoverResultsScreen
    // keyboard on its search
    // two triggers in a
    // both register as distinct
    //
    // discoverSearchFocusConsumedTick tracks the last
    // acted on. This lives
    // itself gets torn down
    // back — a plain
    // tick and re-fire on
    // returning to an in-progress
    // ViewModel level means it
    // matter how many times
    var discoverSearchFocusTick by mutableStateOf(0); private set
    var discoverSearchFocusConsumedTick by mutableStateOf(0); private set
    fun requestDiscoverSearchFocus() { discoverSearchFocusTick++ }
    fun consumeDiscoverSearchFocus() { discoverSearchFocusConsumedTick = discoverSearchFocusTick }
    // Jumps to the search-results
    // the Discover landing page,
    // starts a new blank
    // Browse landing page, where
    // scratch). If a search
    // an in-progress search from
    // alone and just re-requests
    // typing", not "start over".
    fun openDiscoverSearch(context: Context) {
        if (discoverMode == DiscoverMode.Browse) runDiscoverSearch(context, "", discoverTypeFilter)
        requestDiscoverSearchFocus()
    }
    // Clubs tab state —
    // Discover results above: query,
    // live here instead of
    // torn down when the
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
    // Per-title detail scroll —
    // anime and manga ids
    private val detailScrollPositions = mutableMapOf<Pair<String, MediaType>, Pair<Int, Int>>()
    fun getDetailScroll(id: String, type: MediaType) = detailScrollPositions[id to type] ?: (0 to 0)
    fun saveDetailScroll(id: String, type: MediaType, index: Int, offset: Int) { detailScrollPositions[id to type] = index to offset }
    // Per-title cache for every
    // recommended, status distribution, characters/staff,
    // since anime and manga
    // means hopping from a
    // refire any network calls
    // recommended chain reads from
    // way out of the
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
        var stacks: List<StackSummary>? = null,
        // Recent News / Recent
        // Interest Stacks preview —
        // ensureDetailFetched scrape as related/recommended
        // network round trip.
        var news: List<CompanyNews>? = null,
        var forumDiscussion: List<ForumTopic>? = null,
        var featuredArticles: List<FeaturedArticleEntry>? = null,
        // "Available At" links —
        // forumDiscussion/featuredArticles above, just reading
        var links: List<Pair<String, String>>? = null,
        var relatedScroll: Pair<Int, Int> = 0 to 0,
        var recommendedScroll: Pair<Int, Int> = 0 to 0,
        var charactersScroll: Pair<Int, Int> = 0 to 0,
    )
    private val detailCaches = mutableMapOf<Pair<String, MediaType>, DetailCache>()
    private fun detailCache(id: String, type: MediaType) = detailCaches.getOrPut(id to type) { DetailCache() }
    // Read-only look at whatever's
    // entry if there's nothing
    // uses this to seed
    // instead of always starting
    // LaunchedEffect to catch up
    // Reviews page (or any
    // flash the loading skeleton
    data class DetailCacheSnapshot(
        val related: List<RelatedEntry>?,
        val openingThemes: List<String>?,
        val endingThemes: List<String>?,
        val covers: List<String>?,
        val recommended: List<RecommendedEntry>?,
        val statusDistribution: StatusDistribution?,
        val characters: List<CharacterEntry>?,
        val reviews: List<ReviewEntry>?,
        val stacks: List<StackSummary>?,
        val news: List<CompanyNews>?,
        val forumDiscussion: List<ForumTopic>?,
        val featuredArticles: List<FeaturedArticleEntry>?,
        val links: List<Pair<String, String>>?,
    )
    fun peekDetailCache(id: String, type: MediaType): DetailCacheSnapshot? = detailCaches[id to type]?.let {
        DetailCacheSnapshot(it.related, it.openingThemes, it.endingThemes, it.covers, it.recommended, it.statusDistribution, it.characters, it.reviews, it.stacks, it.news, it.forumDiscussion, it.featuredArticles, it.links)
    }
    // Drops every cached detail
    // call this once the
    // every single step back
    // opened from outside any
    fun clearDetailCache() { detailCaches.clear(); detailScrollPositions.clear() }
    // Drops the cache +
    // stepping back past that
    // in Navigation.kt), so a
    // leave stale cached data/position
    // backed out of.
    fun forgetDetailPage(id: String, type: MediaType) { detailCaches.remove(id to type); detailScrollPositions.remove(id to type) }
    // Scroll position for the
    // page — separate from
    // page's own vertical scroll.
    // either row and coming
    // default scroll state doesn't
    // rebuilt on every related/recommended
    fun getRelatedRowScroll(id: String, type: MediaType) = detailCache(id, type).relatedScroll
    fun saveRelatedRowScroll(id: String, type: MediaType, index: Int, offset: Int) { detailCache(id, type).relatedScroll = index to offset }
    fun getRecommendedRowScroll(id: String, type: MediaType) = detailCache(id, type).recommendedScroll
    fun saveRecommendedRowScroll(id: String, type: MediaType, index: Int, offset: Int) { detailCache(id, type).recommendedScroll = index to offset }
    // Same idea for the
    // row (see onOpenCharacter) and
    // that hop tears DetailScreen
    // recommended hops above.
    fun getCharactersRowScroll(id: String, type: MediaType) = detailCache(id, type).charactersScroll
    fun saveCharactersRowScroll(id: String, type: MediaType, index: Int, offset: Int) { detailCache(id, type).charactersScroll = index to offset }
    // Same idea for a
    // coming back from an
    private val stackDetailScrollPositions = mutableMapOf<Int, Pair<Int, Int>>()
    fun getStackDetailScroll(stackId: Int) = stackDetailScrollPositions[stackId] ?: (0 to 0)
    fun saveStackDetailScroll(stackId: Int, index: Int, offset: Int) { stackDetailScrollPositions[stackId] = index to offset }
    // A stack's fetched entries,
    // opening an entry from
    // re-show a spinner for)
    // Compose recomposes StackDetailScreen when
    // user leaves the Interest
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
    // Drops every cached stack's
    // flow is left (not
    // doesn't grow unbounded and
    fun clearStackDetailCache() { stackDetailCache.clear(); stackDetailFailedIds.clear(); stackCoverCache.clear(); stackCoverInFlight.clear() }
    // Cover thumbnails for a
    // Those summaries almost never
    // which has to fetch
    // it) — every row
    // into view, so a
    // scrolling back to a
    // the result outside that
    // once per stack for
    // stackDetailCache above), reuses stackDetailCache
    // detail was already loaded
    // referencing the same stack
    // firing overlapping requests for
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
    // AniList's confirmed nextAiringEpisode for
    // overrides MediaItem.nextEpisodeNumber()'s date-math guess
    // AniList has an answer,
    // skipped week still counts
    // is a resolved "AniList
    // the date-math label in
    // recomposition. Never persisted or
    // lookup, not something that
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
    // Which sub-section (Forums/Clubs) the
    var communityTab by mutableStateOf(CommunityTab.Forums); private set
    fun selectCommunityTab(context: Context, t: CommunityTab) { communityTab = t; settingsPrefs(context).edit().putString("community_tab", t.name).apply() }
    fun loadCommunityTab(context: Context) { communityTab = runCatching { CommunityTab.valueOf(settingsPrefs(context).getString("community_tab", CommunityTab.Forums.name)!!) }.getOrDefault(CommunityTab.Forums) }
    fun setListSort(context: Context, sort: ListSort) { listSort = sort; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_sort", sort.name).apply() }
    fun loadListSort(context: Context) { listSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("list_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
    fun setListViewMode(context: Context, mode: ListViewMode) { listViewMode = mode; settingsPrefs(context).edit().putString("list_view_mode", mode.name).apply() }
    fun loadListViewMode(context: Context) { listViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("list_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    // Score distribution drill-down view
    // switching one screen's list/grid
    var scoreFilterViewMode by mutableStateOf(ListViewMode.List); private set
    fun setScoreFilterViewMode(context: Context, mode: ListViewMode) { scoreFilterViewMode = mode; settingsPrefs(context).edit().putString("score_filter_view_mode", mode.name).apply() }
    fun loadScoreFilterViewMode(context: Context) { scoreFilterViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("score_filter_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    // Score distribution drill-down sort
    // changing one screen's sort
    var scoreFilterSort by mutableStateOf(ListSort.Score); private set
    fun setScoreFilterSort(context: Context, sort: ListSort) { scoreFilterSort = sort; settingsPrefs(context).edit().putString("score_filter_sort", sort.name).apply() }
    fun loadScoreFilterSort(context: Context) { scoreFilterSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("score_filter_sort", ListSort.Score.name)!!) }.getOrDefault(ListSort.Score) }
    // Year distribution drill-down view
    // screen's own prefs above,
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
    // Genre breakdown drill-down view
    // screen's own prefs above,
    var genreFilterViewMode by mutableStateOf(ListViewMode.List); private set
    fun setGenreFilterViewMode(context: Context, mode: ListViewMode) { genreFilterViewMode = mode; settingsPrefs(context).edit().putString("genre_filter_view_mode", mode.name).apply() }
    fun loadGenreFilterViewMode(context: Context) { genreFilterViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("genre_filter_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    var genreFilterSort by mutableStateOf(ListSort.Title); private set
    fun setGenreFilterSort(context: Context, sort: ListSort) { genreFilterSort = sort; settingsPrefs(context).edit().putString("genre_filter_sort", sort.name).apply() }
    fun loadGenreFilterSort(context: Context) { genreFilterSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("genre_filter_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
    // Profile stats page scroll
    // verticalScroll Column, not a
    var profileScrollOffset by mutableStateOf(0); private set
    fun saveProfileScroll(offset: Int) { profileScrollOffset = offset }
    // Profile stats Anime/Manga switcher
    // survives drilling into the
    // Only resets to Anime
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
    // All of these used
    // allocating filterNot pass) fresh
    // reads its `visible*` property
    // check plus the itemsIndexed/items
    // scans for nothing every
    // neither had actually changed
    // and only recomputes when
    // nsfwEnabled, etc.) actually changes
    // visibleDiscoverResultsState below, just extended
    private val visibleItemsState by derivedStateOf { items.nsfwFiltered(nsfwEnabled) }
    val visibleItems: List<MediaItem> get() = visibleItemsState
    // Cached via derivedStateOf: filtering
    // read multiple times per
    // avoids redoing that work
    // it's already in the
    // appended, see runDiscoverSearch/loadMoreTitleSearch/selectDiscoverSort). Re-sorting
    // whole accumulated list here
    // whenever a newly-loaded page's
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
    // MalGenreFiltered results are paginated
    // search — whatever's already
    // been scrolled to so
    // (the old behavior) can't
    // the highest-ranked items for
    // So for that source,
    // wired through to MalGenreApi
    // already-globally-sorted pages instead. Other
    // ranking-pool fallback) already have
    // client-side re-sort of what's
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
    // Which real, further-paginated endpoint
    // the studio/author, multi-tag, and
    // pool in one shot,
    private enum class DiscoverPaginationSource { None, TitleSearch, MalGenreFiltered }
    private var discoverPaginationSource = DiscoverPaginationSource.None
    // Stashed only for the
    // the same genre/theme/demographic ids
    private var discoverGenreKind: String? = null
    private var discoverGenreIds: List<Int> = emptyList()
    var discoverNewSeason by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverUpcoming by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverBrowseLoading by mutableStateOf(false); private set
    var discoverBrowseError by mutableStateOf<String?>(null); private set
    private var discoverBrowseLoaded = false
    // Guards backfillLibraryThemes below —
    // discoverBrowseLoaded above, so a
    // ran once (e.g. on
    private var libraryThemesBackfilled = false
    private var discoverSearchJob: kotlinx.coroutines.Job? = null
    // Tracks whichever loadMoreTitleSearch/loadMoreGenreFiltered coroutine
    // in flight, so a
    // can cancel it —
    // already been reset for
    // which is both wrong
    // key in DiscoverScreen).
    private var discoverLoadMoreJob: kotlinx.coroutines.Job? = null
    // Lightweight title suggestions shown
    // just plain title strings
    var discoverSuggestions by mutableStateOf<List<String>>(emptyList()); private set
    private var discoverSuggestJob: kotlinx.coroutines.Job? = null
    // Raw (pre-filter) results from
    // lowercased/trimmed creator name —
    // year, ...) while the
    // generic matches() filtering in
    // cached list instead. Cleared
    // process — it only
    // studio/author ever searched otherwise
    private val creatorSearchCache = mutableMapOf<Pair<MediaType, String>, List<MediaItem>>()

    // Discover's Characters tab —
    // characters aren't MediaItem at
    // discoverQuery, discoverMode, and discoverScroll*
    // path so switching the
    private val malCharacterApi by lazy { MalCharacterApi() }
    var characterResults by mutableStateOf<List<CharacterSummary>>(emptyList()); private set
    var characterSearching by mutableStateOf(false); private set
    var characterError by mutableStateOf<String?>(null); private set
    private var characterSearchJob: kotlinx.coroutines.Job? = null
    // Loading spinner target for
    // same shape as discoverDetailLoadingId
    var characterDetailLoadingId by mutableStateOf<Int?>(null); private set
    // Resolved character pages —
    // reasoning as detailCaches' resolvedItem
    // MAL id. Dropped on
    // forgetDetailPage for the anime/manga
    // fresh rather than caching
    private val characterDetailCache = mutableMapOf<Int, CharacterDetail>()
    // Character detail page's own
    // getRelatedRowScroll/getRecommendedRowScroll above, but keyed
    // MAL id (a separate
    // tapping into an Animeography/Mangaography
    // page (and its two
    // tears CharacterDetailScreen down and
    // own related/recommended hops.
    private data class CharacterScrollCache(
        var main: Pair<Int, Int> = 0 to 0,
        var anime: Pair<Int, Int> = 0 to 0,
        var manga: Pair<Int, Int> = 0 to 0,
    )
    private val characterScrollCaches = mutableMapOf<Int, CharacterScrollCache>()
    private fun characterScrollCache(malId: Int) = characterScrollCaches.getOrPut(malId) { CharacterScrollCache() }
    fun getCharacterScroll(malId: Int) = characterScrollCache(malId).main
    fun saveCharacterScroll(malId: Int, index: Int, offset: Int) { characterScrollCache(malId).main = index to offset }
    fun getCharacterAnimeScroll(malId: Int) = characterScrollCache(malId).anime
    fun saveCharacterAnimeScroll(malId: Int, index: Int, offset: Int) { characterScrollCache(malId).anime = index to offset }
    fun getCharacterMangaScroll(malId: Int) = characterScrollCache(malId).manga
    fun saveCharacterMangaScroll(malId: Int, index: Int, offset: Int) { characterScrollCache(malId).manga = index to offset }
    // Drop a character's remembered
    // backed out of —
    // cache-plus-position shape) as forgetDetailPage
    // recommended chain, so a
    // session-long cache.
    fun forgetCharacterPage(malId: Int) { characterScrollCaches.remove(malId); characterDetailCache.remove(malId) }

    // Run a character search
    // discoverTypeFilter/discoverMode too) but talks
    // MediaItem search pipeline, since
    // and isn't further-paginated the
    fun runCharacterSearch(query: String) {
        discoverQuery = query; discoverTypeFilter = "Characters"; discoverMode = DiscoverMode.Results
        discoverScrollIndex = 0; discoverScrollOffset = 0
        characterSearchJob?.cancel()
        if (query.isBlank()) { characterResults = emptyList(); characterSearching = false; characterError = null; return }
        if (query.trim().length < 3) {
            characterResults = emptyList(); characterSearching = false
            characterError = "Type at least 3 characters to search"
            return
        }
        characterSearchJob = viewModelScope.launch {
            characterSearching = true
            runCatching { malCharacterApi.search(query) }
                .onSuccess { characterResults = it; characterError = null }
                .onFailure {
                    // Same cancellation-isn't-a-failure reasoning as
                    // switching the type dropdown
                    // characterSearchJob?.cancel() above.
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    characterError = it.message ?: "Search failed"
                }
            characterSearching = false
        }
    }

    // Discover's People tab —
    // (a person isn't a
    // own search()/detail() instead of
    private val malPeopleApi by lazy { MalPeopleApi() }
    var personResults by mutableStateOf<List<PersonSummary>>(emptyList()); private set
    var personSearching by mutableStateOf(false); private set
    var personError by mutableStateOf<String?>(null); private set
    private var personSearchJob: kotlinx.coroutines.Job? = null
    // Loading spinner target for
    // same shape as characterDetailLoadingId
    var personDetailLoadingId by mutableStateOf<Int?>(null); private set
    // Resolved person pages —
    // backing out via forgetPersonPage,
    private val personDetailCache = mutableMapOf<Int, PersonDetail>()
    // Person detail page's own
    // keyed by the person's
    // rather than id+type, for
    // reset this page to
    private data class PersonScrollCache(
        var main: Pair<Int, Int> = 0 to 0,
        var roles: Pair<Int, Int> = 0 to 0,
        var staff: Pair<Int, Int> = 0 to 0,
        var manga: Pair<Int, Int> = 0 to 0,
    )
    private val personScrollCaches = mutableMapOf<Int, PersonScrollCache>()
    private fun personScrollCache(malId: Int) = personScrollCaches.getOrPut(malId) { PersonScrollCache() }
    fun getPersonScroll(malId: Int) = personScrollCache(malId).main
    fun savePersonScroll(malId: Int, index: Int, offset: Int) { personScrollCache(malId).main = index to offset }
    fun getPersonRolesScroll(malId: Int) = personScrollCache(malId).roles
    fun savePersonRolesScroll(malId: Int, index: Int, offset: Int) { personScrollCache(malId).roles = index to offset }
    fun getPersonStaffScroll(malId: Int) = personScrollCache(malId).staff
    fun savePersonStaffScroll(malId: Int, index: Int, offset: Int) { personScrollCache(malId).staff = index to offset }
    fun getPersonMangaScroll(malId: Int) = personScrollCache(malId).manga
    fun savePersonMangaScroll(malId: Int, index: Int, offset: Int) { personScrollCache(malId).manga = index to offset }
    // Drop a person's remembered
    // backed out of —
    // forgetCharacterPage.
    fun forgetPersonPage(malId: Int) { personScrollCaches.remove(malId); personDetailCache.remove(malId) }

    // Discover's Companies tab —
    // above (a company isn't
    // MalCompanyApi's own search()/detail() (the
    // MalCompanyApi already did for
    // this just adds the
    private val malCompanyApi by lazy { MalCompanyApi() }
    var companyResults by mutableStateOf<List<CompanySummary>>(emptyList()); private set
    var companySearching by mutableStateOf(false); private set
    var companyError by mutableStateOf<String?>(null); private set
    private var companySearchJob: kotlinx.coroutines.Job? = null
    // Loading spinner target for
    // same shape as characterDetailLoadingId/personDetailLoadingId
    // MAL company id.
    var companyDetailLoadingId by mutableStateOf<Int?>(null); private set
    // Resolved company pages —
    // above (dropped on backing
    // process).
    private val companyDetailCache = mutableMapOf<Int, CompanyDetail>()
    // Company detail page's own
    // characterScrollCaches/personScrollCaches above) since the
    // card, and the anime
    // several independent LazyRows; same
    private val companyScrollPositions = mutableMapOf<Int, Pair<Int, Int>>()
    fun getCompanyScroll(malId: Int) = companyScrollPositions[malId] ?: (0 to 0)
    fun saveCompanyScroll(malId: Int, index: Int, offset: Int) { companyScrollPositions[malId] = index to offset }
    // Drop a company's remembered
    // backed out of —
    // forgetCharacterPage/forgetPersonPage.
    fun forgetCompanyPage(malId: Int) { companyScrollPositions.remove(malId); companyDetailCache.remove(malId) }

    // Run a company search
    // MAL-side minimum-length rule.
    fun runCompanySearch(query: String) {
        discoverQuery = query; discoverTypeFilter = "Companies"; discoverMode = DiscoverMode.Results
        discoverScrollIndex = 0; discoverScrollOffset = 0
        companySearchJob?.cancel()
        if (query.isBlank()) { companyResults = emptyList(); companySearching = false; companyError = null; return }
        if (query.trim().length < 3) {
            companyResults = emptyList(); companySearching = false
            companyError = "Type at least 3 characters to search"
            return
        }
        companySearchJob = viewModelScope.launch {
            companySearching = true
            runCatching { malCompanyApi.search(query) }
                .onSuccess { companyResults = it; companyError = null }
                .onFailure {
                    // Same cancellation-isn't-a-failure reasoning as
                    // runPersonSearch — switching the
                    // job via companySearchJob?.cancel() above.
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    companyError = it.message ?: "Search failed"
                }
            companySearching = false
        }
    }

    // Fetch a tapped row's
    // shape openCharacterDetail/openPersonDetail document above
    // CompanyDetailScreenSkeleton right away, this
    // can). The anime grid
    // no English-title backfill here,
    // anime/manga DetailScreen never got
    // in one title per
    // would make Company inconsistent
    // for a title-language mismatch
    fun openCompanyDetail(context: Context, malId: Int, onLoaded: (CompanyDetail) -> Unit, onError: () -> Unit = {}) {
        companyDetailCache[malId]?.let { onLoaded(it); return }
        companyDetailLoadingId = malId
        viewModelScope.launch {
            runCatching { malCompanyApi.detail(malId) }
                .onSuccess { companyDetailCache[malId] = it; onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load company"; onError() }
            companyDetailLoadingId = null
        }
    }

    // Run a people search
    // rule.
    fun runPersonSearch(query: String) {
        discoverQuery = query; discoverTypeFilter = "People"; discoverMode = DiscoverMode.Results
        discoverScrollIndex = 0; discoverScrollOffset = 0
        personSearchJob?.cancel()
        if (query.isBlank()) { personResults = emptyList(); personSearching = false; personError = null; return }
        if (query.trim().length < 3) {
            personResults = emptyList(); personSearching = false
            personError = "Type at least 3 characters to search"
            return
        }
        personSearchJob = viewModelScope.launch {
            personSearching = true
            runCatching { malPeopleApi.search(query) }
                .onSuccess { personResults = it; personError = null }
                .onFailure {
                    // Same cancellation-isn't-a-failure reasoning as
                    // switching the type dropdown
                    // personSearchJob?.cancel() above.
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    personError = it.message ?: "Search failed"
                }
            personSearching = false
        }
    }

    // Fetch a tapped row's
    // page the instant it's
    // shows PersonDetailScreenSkeleton until onLoaded
    // gate anything before returning;
    //
    // Voice Acting Roles/Anime Staff
    // MAL's own person page
    // to run one via
    // DetailScreen never got that
    // ever bakes in one
    // inconsistent with the row
    fun openPersonDetail(context: Context, malId: Int, onLoaded: (PersonDetail) -> Unit, onError: () -> Unit = {}) {
        personDetailCache[malId]?.let { onLoaded(it); return }
        personDetailLoadingId = malId
        viewModelScope.launch {
            runCatching { malPeopleApi.detail(malId) }
                .onSuccess { personDetailCache[malId] = it; onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load person"; onError() }
            personDetailLoadingId = null
        }
    }

    // Switches the Discover type
    // each have a real
    // runPersonSearch/runCompanySearch); any other value
    fun selectDiscoverType(context: Context, type: String, query: String) {
        when (type) {
            "Anime", "Manga" -> runDiscoverSearch(context, query, type)
            "Characters" -> runCharacterSearch(query)
            "People" -> runPersonSearch(query)
            "Companies" -> runCompanySearch(query)
            else -> {
                discoverQuery = query; discoverTypeFilter = type; discoverMode = DiscoverMode.Results
                discoverSearchJob?.cancel(); discoverLoadMoreJob?.cancel(); characterSearchJob?.cancel(); personSearchJob?.cancel(); companySearchJob?.cancel()
                discoverResults = emptyList(); characterResults = emptyList(); personResults = emptyList(); companyResults = emptyList()
                discoverSearching = false; characterSearching = false; personSearching = false; companySearching = false
                discoverError = null; characterError = null; personError = null; companyError = null; discoverHasMore = false
                discoverPaginationSource = DiscoverPaginationSource.None
            }
        }
    }

    // Fetch a tapped row's
    // shape openPersonDetail documents above
    // CharacterDetailScreenSkeleton right away, this
    // can). Animeography/Mangaography rows show
    // actually rendered — no
    // resolveCharacterWorkTitles). Related/Recommended on the
    // DetailScreen never got that
    // ever bakes in one
    // Character inconsistent with the
    fun openCharacterDetail(context: Context, malId: Int, onLoaded: (CharacterDetail) -> Unit, onError: () -> Unit = {}) {
        characterDetailCache[malId]?.let { onLoaded(it); return }
        characterDetailLoadingId = malId
        viewModelScope.launch {
            runCatching { malCharacterApi.detail(malId) }
                .onSuccess { characterDetailCache[malId] = it; onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load character"; onError() }
            characterDetailLoadingId = null
        }
    }

    // Home recommendations row
    var recommendations by mutableStateOf<List<MediaItem>>(emptyList()); private set
    // Trending manga row (manga
    // uses the popularity ranking
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

    // Match on id AND
    // and can collide (e.g.
    // a safe key here:
    // unrelated title of the
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
    // Persist only valid hex.
    // user is dragging the
    // pixel of pointer movement
    // call meant every drag
    // Editor/HashMap allocation each frame.
    // live preview swatch/theme) still
    // disk write is pushed
    // collapses a whole drag
    // lifts (or after a
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
    // MalApi.fetchList (the official MAL
    // per item, but those
    // ignores field names it
    // that comes back from
    // left theme/demographic filters unable
    // Tenrai (the Jikan-backed API
    // theme data per title,
    // this "we have the
    // enrich author/studio search rows)
    // library item once per
    // In-memory only, not persisted
    // launch re-runs it, which
    // TenraiApi.getRaw) already caps this
    // so fanning out one
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
        // Reuse one MalApi instance
        // instead of constructing a
        // caches SharedPreferences instances by
        val api = MalApi(context)
        if (discoverBrowseLoaded || !api.signedIn) return
        discoverBrowseLoaded = true
        discoverBrowseLoading = true
        viewModelScope.launch {
            // These two endpoints don't
            // them one after another
            // fully returned. Home's "Airing
            // on discoverNewSeason, so that
            // Home had anything to
            // loadStacksHome() already does below
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
    // Load recommendations + trending
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
                // Not reconciled against the
                // vm.trackedStatus() lookup at render
                // ScheduleRow), so it stays
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
                // Not reconciled against the
                .onSuccess { seasonalResults = seasonalResults + it.items; seasonalHasMore = it.hasMore }
                .onFailure { seasonalHasMore = false }
            seasonalLoadingMore = false
        }
    }
    // Title suggestions for the
    // These are for autofilling
    // actual search via runDiscoverSearch,
    fun fetchDiscoverSuggestions(context: Context, query: String, type: String) {
        discoverSuggestJob?.cancel()
        if (query.isBlank()) { discoverSuggestions = emptyList(); return }
        val api = MalApi(context)
        if (!api.signedIn) { discoverSuggestions = emptyList(); return }
        discoverSuggestJob = viewModelScope.launch {
            delay(100) // debounce so we're not
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
        // MAL's own search endpoint
        // (see MalApi.searchKind), which otherwise
        // failed (400): ..." message.
        // endpoint — creator/genre-filter searches
        // only a problem when
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
                // Studio (anime) / author
                // company or person id
                // scrape that studio's/person's own
                // credited works — two
                // no Tenrai/third-party API involved
                // Raw (pre-filter) results are
                // so re-applying filters (Advanced
                // etc.) with the same
                // re-filters the cached list,
                //
                // This has to be
                // after: whenever someone types
                // search bar (the natural
                // original ordering that branch
                // actually resolves the creator
                // at all. The scrape
                    // choosing to call it
                    if (filters.creator.isNotBlank()) {
                        val creatorKey = filters.creator.trim().lowercase()
                        val animeResults = if (t == MediaType.Manga) emptyList() else creatorSearchCache.getOrPut(MediaType.Anime to creatorKey) {
                            val malCompany = MalCompanyApi()
                            val studioResults = runCatching {
                                val companyId = malCompany.searchCompany(filters.creator)
                                if (companyId == null) emptyList() else malCompany.fetchWorks(companyId, filters.creator)
                            }.getOrElse { emptyList() }
                            // Fall back to the
                            // by matches()) only if
                            // lookup failure still shows
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
                            // Same reasoning: fall back
                            // only when the author
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
                        // The only branch backed
                        // record that so loadMoreDiscoverSearch()
                        val page = api.search(query, t)
                        discoverPaginationSource = DiscoverPaginationSource.TitleSearch; discoverHasMore = page.hasMore
                        page.items
                    }
                    // Search via genre/theme/demographic filters,
                    // anime.php/manga.php advanced search (MalGenreApi)
                    // gets ANDed together and
                    // format/status, rather than the
                    // only handled a single
                    // pool (capped at each
                    // -> id lookup itself
                    // rather than Tenrai —
                    // slow part of this
                    else if (filters.genres.isNotEmpty() || filters.themes.isNotEmpty() || filters.demographics.isNotEmpty()) {
                        val malGenre = MalGenreApi()
                        val genreLookup = MalGenreLookup() // static name->id lookup table,
                        val kinds = t?.let { listOf(if (it == MediaType.Anime) "anime" else "manga") } ?: listOf("anime", "manga")
                        // "All" media type can't
                        // don't share a genre
                        // paginated searches — this
                        // Each kind's ids are
                        // resolved a second time
                        // resolveGenreIds already ran once
                        val perKind = coroutineScope {
                            kinds.map { kind ->
                                async {
                                    val ids = runCatching { genreLookup.resolveGenreIds(kind, filters.genres, filters.themes, filters.demographics) }.getOrElse { emptyList() }
                                    if (ids.isEmpty()) null else runCatching {
                                        malGenre.search(kind, ids, malTypeCode(kind, filters.format), malStatusCode(filters.airingStatus, kind), page = 1, includeAdult = nsfwEnabled, sort = discoverSort)
                                    }.getOrNull()?.let { Triple(kind, ids, it) }
                                }
                            }.awaitAll().filterNotNull()
                        }
                        val singleKind = kinds.singleOrNull()
                        if (singleKind != null && perKind.isNotEmpty()) {
                            val (_, ids, page) = perKind.first()
                            discoverPaginationSource = DiscoverPaginationSource.MalGenreFiltered
                            discoverHasMore = page.hasMore
                            discoverGenreKind = singleKind
                            discoverGenreIds = ids
                            page.items
                        } else if (perKind.isNotEmpty()) {
                            // "All" media type: no
                            // shape doesn't support loadMoreDiscoverSearch
                            // the old multi-kind fallback
                            perKind.flatMap { (_, _, page) -> page.items }.distinctBy { it.id }
                        } else {
                            // Genre/theme/demographic name didn't resolve
                            // request failed outright —
                            // (filtered client-side by matches())
                            // something instead of a
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
                // Resolve MalGenreApi's blank English
                // the results are ever
                // already comes back with
                val enriched = resolveEnglishTitles(context, results)
                // Sort once, here, at
                // visibleDiscoverResults). Deliberately NOT reconciled
                // anymore: that used to
                // discoverResults, which then stayed
                // title elsewhere (or, worse,
                // baked copy still had
                // from a live vm.trackedStatus()
                enriched.sortedForDiscover(discoverSort, titleLanguage, query)
            }
                .onSuccess { discoverResults = it; discoverError = null }
                .onFailure {
                    // Cancellation isn't a real
                    // supersedes this one (e.g.
                    // loading cancels this job
                    // runCatching catches CancellationException like
                    // without this check the
                    // "StandaloneCoroutine was cancelled" right
                    // results had already rendered.
                    // silently instead, the way
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    discoverError = it.message ?: "Search failed"; discoverHasMore = false; discoverPaginationSource = DiscoverPaginationSource.None
                }
            discoverSearching = false
        }
    }
    // Load next page of
    // real, further-paginated search (see
    fun loadMoreDiscoverSearch(context: Context) {
        if (discoverSearching || discoverLoadingMore || !discoverHasMore) return
        when (discoverPaginationSource) {
            DiscoverPaginationSource.TitleSearch -> loadMoreTitleSearch(context)
            DiscoverPaginationSource.MalGenreFiltered -> loadMoreGenreFiltered(context)
            DiscoverPaginationSource.None -> {}
        }
    }
    // Resolves English titles for
    // leaves titleEnglish blank —
    // patched list. Callers now
    // person with Title Language
    // then flip to English
    // behind it) was the
    // search, but it always
    // itself does cost real
    // trip — see MalApi.englishTitles),
    // raised: this path is
    // No-ops entirely when Title
    // titleEnglish otherwise, so there's
    // for the already-common case
    // ranking / creator search
    private suspend fun resolveEnglishTitles(context: Context, items: List<MediaItem>): List<MediaItem> {
        if (titleLanguage != TitleLanguage.English) return items
        val targets = items.filter { it.titleEnglish.isBlank() }
        if (targets.isEmpty()) return items
        val byKind = targets.groupBy { if (it.type == MediaType.Anime) "anime" else "manga" }
        val resolvedByKind = coroutineScope {
            byKind.map { (kind, list) ->
                async { kind to runCatching { MalApi(context).englishTitles(kind, list.mapNotNull { it.id.toIntOrNull() }) }.getOrElse { emptyMap() } }
            }.awaitAll()
        }.toMap()
        if (resolvedByKind.values.all { it.isEmpty() }) return items
        return items.map { item ->
            val intId = item.id.toIntOrNull()
            if (item.titleEnglish.isBlank() && intId != null) {
                val kind = if (item.type == MediaType.Anime) "anime" else "manga"
                resolvedByKind[kind]?.get(intId)?.takeIf { it.isNotBlank() }?.let { item.copy(titleEnglish = it) } ?: item
            } else item
        }
    }
    // Kicks off (and caches)
    // soon as the filter
    // MalGenreLookup.GenreFacetCache. Scraped straight off
    // filter panel (one request
    // comment), so prewarming here
    // it right when Apply
    fun prewarmGenreLookup() {
        val genreLookup = MalGenreLookup()
        viewModelScope.launch { genreLookup.prewarmGenreNames("anime") }
        viewModelScope.launch { genreLookup.prewarmGenreNames("manga") }
    }
    private fun loadMoreTitleSearch(context: Context) {
        val api = MalApi(context)
        if (!api.signedIn) return
        val t = when (discoverTypeFilter) { "Anime" -> MediaType.Anime; "Manga" -> MediaType.Manga; else -> null }
        discoverLoadMoreJob = viewModelScope.launch {
            discoverLoadingMore = true
            runCatching { api.search(discoverQuery, t, offset = discoverResults.size) }
                // Reconcile against user's library,
                // and sort only the
                // Never re-sorts the already-displayed
                // already on screen jump
                .onSuccess { page ->
                    val existingKeys = discoverResults.mapTo(HashSet()) { it.id to it.type }
                    // Not reconciled against the
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
    private fun loadMoreGenreFiltered(context: Context) {
        val kind = discoverGenreKind ?: return
        val ids = discoverGenreIds.ifEmpty { return }
        // MAL's own advanced-search pager
        // MalGenreApi.pageSize) — there's no
        // the next page number
        val nextPage = (discoverResults.size / MalGenreApi().pageSize) + 1
        discoverLoadMoreJob = viewModelScope.launch {
            discoverLoadingMore = true
            runCatching {
                MalGenreApi().search(kind, ids, malTypeCode(kind, discoverFilters.format), malStatusCode(discoverFilters.airingStatus, kind), page = nextPage, includeAdult = nsfwEnabled, sort = discoverSort)
            }
                // Same reasoning as loadMoreTitleSearch:
                // the already-displayed items instead
                .onSuccess { page ->
                    val existingKeys = discoverResults.mapTo(HashSet()) { it.id to it.type }
                    // Not reconciled against the
                    val newItemsRaw = page.items
                        .filter { (it.id to it.type) !in existingKeys }
                        .distinctBy { it.id to it.type }
                    // Resolved before appending —
                    // by scrolling doesn't flash
                    val newItems = resolveEnglishTitles(context, newItemsRaw).sortedForDiscover(discoverSort, titleLanguage, discoverQuery)
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
        characterSearchJob?.cancel(); characterResults = emptyList(); characterError = null; characterSearching = false
        personSearchJob?.cancel(); personResults = emptyList(); personError = null; personSearching = false
        companySearchJob?.cancel(); companyResults = emptyList(); companyError = null; companySearching = false
        // Drop the raw studio/author
        // whole process — it
        // page didn't re-scrape MAL.
        // gone, and holding onto
        // the session just grows
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
    // Jump to the Announcements
    // forumBoardIcon (ForumsScreen.kt) already keys
    // ids are the stable
    // re-worded. Used by Home's
    fun openAnnouncementsBoard(context: Context) {
        viewModelScope.launch {
            val cached = forumCategories.flatMap { it.boards }.firstOrNull { it.id == 5 }
            val board = cached ?: run {
                val fetched = runCatching { MalApi(context).forumBoards() }.getOrNull() ?: return@run null
                forumCategories = fetched; forumBoardsLoaded = true
                fetched.flatMap { it.boards }.firstOrNull { it.id == 5 }
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

    // Home "Featured Articles" row,
    // homepage widget (see MalDetailScrapeApi.fetchHomeFeaturedArticles),
    // same DetailFeaturedArticleCard DetailScreen's "Recent
    // already uses. A plain
    // the official (OAuth) API,
    // this isn't gated behind
    var homeFeaturedArticles by mutableStateOf<List<FeaturedArticleEntry>>(emptyList()); private set
    var homeFeaturedArticlesLoading by mutableStateOf(false); private set
    private var homeFeaturedArticlesLoaded = false
    fun loadHomeFeaturedArticles(force: Boolean = false) {
        if (homeFeaturedArticlesLoaded && !force) return
        homeFeaturedArticlesLoaded = true
        homeFeaturedArticlesLoading = true
        viewModelScope.launch {
            runCatching { MalDetailScrapeApi().fetchHomeFeaturedArticles(limit = 3) }
                .onSuccess { homeFeaturedArticles = it }
                // Fail silently, no banner
                .onFailure { homeFeaturedArticlesLoaded = false }
            homeFeaturedArticlesLoading = false
        }
    }

    // Latest MAL announcement (board
    // announcement card — a
    // same forumTopics endpoint the
    var homeAnnouncement by mutableStateOf<ForumTopic?>(null); private set
    var homeAnnouncementLoading by mutableStateOf(false); private set
    private var homeAnnouncementLoaded = false
    fun loadHomeAnnouncement(context: Context, force: Boolean = false) {
        val api = MalApi(context)
        if ((homeAnnouncementLoaded && !force) || !api.signedIn) return
        homeAnnouncementLoaded = true
        homeAnnouncementLoading = true
        viewModelScope.launch {
            runCatching {
                // forumTopics sorts by sort=recent
                // items.first() can be an
                // reply rather than the
                // without thumbnails and pick
                // trusting list order.
                val latest = api.forumTopics(boardId = 5, limit = 25).items
                    .maxByOrNull { parseForumCreatedAtMillis(it.createdAt) } ?: return@runCatching null
                // Thumbnail lookup only for
                // batch — forumTopics(withThumbnails =
                val image = runCatching { api.forumTopic(latest.id, limit = 1) }.getOrNull()
                    ?.posts?.firstOrNull()?.body?.let { firstImageUrl(it) }
                if (image != null) latest.copy(imageUrl = image) else latest
            }
                .onSuccess { homeAnnouncement = it }
                // Fail silently, no banner
                .onFailure { homeAnnouncementLoaded = false }
            homeAnnouncementLoading = false
        }
    }
    // ForumTopic.createdAt format ("yyyy-MM-dd'T'HH:mm:ssXXX") —
    // (ForumsScreen.kt) already parses —
    // can be picked by
    private fun parseForumCreatedAtMillis(raw: String): Long =
        runCatching { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(raw)?.time }.getOrNull() ?: 0L

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

    // Animeography/Mangaography row loading id
    // openRelated above, since a
    // anime/manga ids and deserve
    var characterWorkLoadingId by mutableStateOf<Int?>(null); private set
    fun openCharacterWork(context: Context, malId: Int, type: MediaType, onLoaded: (MediaItem) -> Unit) {
        val cache = detailCache(malId.toString(), type)
        cache.resolvedItem?.let { onLoaded(it); return }
        characterWorkLoadingId = malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(malId, type) }
                .onSuccess { cache.resolvedItem = it; onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            characterWorkLoadingId = null
        }
    }

    // Stack entry row loading
    var stackEntryLoadingId by mutableStateOf<Int?>(null); private set

    // Open a title tapped
    fun openStackEntry(context: Context, entry: StackTitleEntry, onLoaded: (MediaItem) -> Unit) {
        stackEntryLoadingId = entry.malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(entry.malId, entry.type) }
                .onSuccess { onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            stackEntryLoadingId = null
        }
    }

    // Interest Stacks browsing state
    // fetched lists AND scroll
    // entries and back out
    // and drops the user
    var stacksHomeChallenges by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksHomeManga by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksHomeAnime by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksHomeMal by mutableStateOf<List<StackSummary>>(emptyList()); private set
    // Only ever page 1
    // results are reached via
    // browse screen (that screen
    var stacksHomeRecent by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var stacksHomeLoading by mutableStateOf(false); private set
    private var stacksHomeLoaded = false
    var stacksHomeScrollIndex by mutableStateOf(0); private set
    var stacksHomeScrollOffset by mutableStateOf(0); private set
    fun saveStacksHomeScroll(index: Int, offset: Int) { stacksHomeScrollIndex = index; stacksHomeScrollOffset = offset }
    // Loads once — cached
    // a stack's detail page
    fun loadStacksHome() {
        if (stacksHomeLoaded) return
        stacksHomeLoaded = true
        stacksHomeLoading = true
        viewModelScope.launch {
            val api = StacksApi()
            coroutineScope {
                // limit matches each row's
                // stack on the page
                val ch = async { runCatching { api.search(StackBrowseKind.Challenges, limit = 2) }.getOrElse { emptyList() } }
                val mg = async { runCatching { api.search(StackBrowseKind.Manga, limit = 1) }.getOrElse { emptyList() } }
                val an = async { runCatching { api.search(StackBrowseKind.Anime, limit = 1) }.getOrElse { emptyList() } }
                val mal = async { runCatching { api.search(StackBrowseKind.MyAnimeList, limit = 1) }.getOrElse { emptyList() } }
                // Home's "Recent" row only
                // away via "See all"
                // screen), so there's no
                val rc = async { runCatching { api.search(StackBrowseKind.All, limit = 5) }.getOrElse { emptyList() } }
                stacksHomeChallenges = ch.await(); stacksHomeManga = mg.await(); stacksHomeAnime = an.await(); stacksHomeMal = mal.await(); stacksHomeRecent = rc.await()
            }
            stacksHomeLoading = false
        }
    }
    // Single freshest stack for
    // lighter-weight cousin of loadStacksHome()
    // needs the one most-recent
    var homeLatestStack by mutableStateOf<StackSummary?>(null); private set
    private var homeLatestStackLoaded = false
    fun loadHomeLatestStack(context: Context, force: Boolean = false) {
        if ((homeLatestStackLoaded && !force) || !MalApi(context).signedIn) return
        homeLatestStackLoaded = true
        viewModelScope.launch {
            homeLatestStack = runCatching { StacksApi().search(StackBrowseKind.All, limit = 1).firstOrNull() }.getOrNull()
        }
    }

    // Interest Stacks browse/search screen
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
    // Switches tab and reloads
    // the same tab after
    fun setStacksBrowseKind(kind: StackBrowseKind) {
        if (stacksBrowseActiveKind == kind) return
        stacksBrowseActiveKind = kind
        stacksBrowseScrollIndex = 0; stacksBrowseScrollOffset = 0
        loadStacksBrowse(reset = true)
    }
    fun searchStacksBrowse() { stacksBrowseScrollIndex = 0; stacksBrowseScrollOffset = 0; loadStacksBrowse(reset = true) }
    fun loadMoreStacksBrowse() = loadStacksBrowse(reset = false)

    // Coalesces the detail-page backfills
    // distribution, recommended) into a
    // independently call MalApi.detail() and/or
    // DetailScreen mounted — up
    // plus 2 identical scrapes
    // only read the one
    // one MalApi.detail() call and
    // keyed by (id, type)
    // LaunchedEffects in the same
    // triggering their own, then
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
                // Genuine user-submitted recs, from
                // needs fresh.title for MAL's
                // this starts once apiDeferred
                // concurrently with scrapeDeferred below
                val userRecsDeferred = async {
                    runCatching { MalDetailScrapeApi().fetchUserRecommendations(intId, type, fresh?.title.orEmpty()) }.getOrNull()
                }
                val scraped = scrapeDeferred.await()
                val userRecs = userRecsDeferred.await()
                // Only cached on a
                // backfills, so a failed
                // a permanent blank.
                if (fresh != null) {
                    cache.openingThemes = fresh.openingThemes
                    cache.endingThemes = fresh.endingThemes
                    cache.covers = fresh.covers
                    cache.statusDistribution = fresh.statusDistribution
                }
                // Related: the official API's
                // same-type only in practice
                // related_anime but usually comes
                // novel adaptations), and vice
                // Related Entries box on
                // box is scraped directly
                // API result stays as
                // Entries with a resolved
                // old/obscure related titles have
                // page), so those are
                // collapse onto the same
                // genuinely no related entries
                // otherwise never satisfy isNotEmpty(),
                // forcing a fresh network
                val apiRelated = fresh?.related ?: emptyList()
                val scrapedRelated = scraped?.related ?: emptyList()
                cache.related = (scrapedRelated + apiRelated).distinctBy { if (it.malId > 0) "id:${it.malType}:${it.malId}" else "title:${it.malType}:${it.title}" }
                // Recommended: real user recs
                // votes, never AutoRec —
                // detail page's Recommendations slider
                // picks in the row
                // that shows up in
                // rather than getting relabeled
                // when the slider also
                // per entry, while the
                // table alongside every write-up
                // pairing's cover has been
                // own poster instead of
                // source wins whenever both
                // own recommendations field only
                // when empty, same reasoning
                val realRecs = userRecs ?: emptyList()
                val sliderRecs = scraped?.recommended ?: emptyList()
                val sliderByKey = sliderRecs.associateBy { it.malId to it.malType }
                val merged = realRecs.map { real ->
                    val sliderCover = sliderByKey[real.malId to real.malType]?.cover
                    if (!sliderCover.isNullOrBlank()) real.copy(cover = sliderCover) else real
                }
                val mergedKeys = merged.map { it.malId to it.malType }.toSet()
                cache.recommended = (merged + sliderRecs.filterNot { (it.malId to it.malType) in mergedKeys })
                    .ifEmpty { fresh?.recommended ?: emptyList() }
                // Recent News / Recent
                // cached when the scrape
                // there's no separate API
                // failure still retries next
                if (scraped != null) {
                    cache.news = scraped.news
                    cache.forumDiscussion = scraped.forumDiscussion
                    cache.featuredArticles = scraped.featuredArticles
                    cache.links = scraped.links
                }
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

    // Backfill empty theme fields
    // all, so a fetch
    // old code kept the
    // meaning it silently refired
    // returned to, instead of
    // for anime titles that
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

    // Load characters row (feeds
    // Actors row on the
    // longer fans out a
    // onError fires when the
    // MalDetailScrapeApi.fetchCharacters, which scrapes MAL's
    // subpage directly rather than
    // that just has no
    // failure state instead of
    // cast data".
    fun loadCharacters(item: MediaItem, onFound: (List<CharacterEntry>) -> Unit, onDone: () -> Unit = {}, onError: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.characters?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalDetailScrapeApi().fetchCharacters(intId, item.type, item.title) }
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

    // Community score breakdown for
    // Status distribution. Its own
    // above) since it's a
    // will never actually tap
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

    // Recent News row on
    // related/recommended above (see ensureDetailFetched),
    // off the already-cached result
    // opens the same forum
    // Recent News card already
    fun loadDetailNews(context: Context, item: MediaItem, onFound: (List<CompanyNews>) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.news?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, item.id, item.type).await()
            val result = cache.news ?: emptyList()
            if (result.isNotEmpty()) onFound(result)
            onDone()
        }
    }

    // Recent Forum Discussion row
    fun loadDetailForumDiscussion(context: Context, item: MediaItem, onFound: (List<ForumTopic>) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.forumDiscussion?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, item.id, item.type).await()
            val result = cache.forumDiscussion ?: emptyList()
            if (result.isNotEmpty()) onFound(result)
            onDone()
        }
    }

    // Recent Featured Articles row
    fun loadDetailFeaturedArticles(context: Context, item: MediaItem, onFound: (List<FeaturedArticleEntry>) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.featuredArticles?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, item.id, item.type).await()
            val result = cache.featuredArticles ?: emptyList()
            if (result.isNotEmpty()) onFound(result)
            onDone()
        }
    }

    // "Available At" Links section,
    // MalDetailScrapeApi.fetch() scrape as news/forumDiscussion/featuredArticles
    // just reading its links
    fun loadDetailLinks(context: Context, item: MediaItem, onFound: (List<Pair<String, String>>) -> Unit, onDone: () -> Unit = {}) {
        val cache = detailCache(item.id, item.type)
        cache.links?.let { onFound(it); onDone(); return }
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            ensureDetailFetched(context, item.id, item.type).await()
            val result = cache.links ?: emptyList()
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

    // Interest Stacks preview row
    // DetailScreenActions.onLoadStacks. Its own scrape
    // separate from ensureDetailFetched's related/recommended/etc
    // slow or failed fetch
    // own gating condition deliberately
    fun loadMediaStacks(item: MediaItem, onFound: (List<StackSummary>) -> Unit) {
        val cache = detailCache(item.id, item.type)
        cache.stacks?.let { onFound(it); return }
        val intId = item.id.toIntOrNull() ?: return
        viewModelScope.launch {
            val result = runCatching { StacksApi().forMedia(intId, item.type, limit = 5).items }.getOrElse { emptyList() }
            cache.stacks = result
            if (result.isNotEmpty()) onFound(result)
        }
    }

    // Full "Interest Stacks" page
    // the preview row) —
    // above, just scoped to
    // search. reset with the
    // no-op if results are
    // setStacksBrowseKind — so leaving
    var mediaStacksResults by mutableStateOf<List<StackSummary>>(emptyList()); private set
    var mediaStacksLoading by mutableStateOf(false); private set
    // Whether another page might
    // title (see StacksApi.MediaStacksPage.total) rather
    // page's own scrape happened
    // meant pagination stopped one
    // ever undercounted a genuinely
    // "fewer than 20 rows
    // counter itself doesn't parse.
    var mediaStacksHasMore by mutableStateOf(true); private set
    private var mediaStacksOffset = 0
    private var mediaStacksTarget: Pair<Int, MediaType>? = null
    var mediaStacksScrollIndex by mutableStateOf(0); private set
    var mediaStacksScrollOffset by mutableStateOf(0); private set
    fun saveMediaStacksScroll(index: Int, offset: Int) { mediaStacksScrollIndex = index; mediaStacksScrollOffset = offset }

    fun loadMediaStacksPage(mediaId: Int, type: MediaType, reset: Boolean = false) {
        val target = mediaId to type
        if (reset) {
            if (mediaStacksTarget == target && mediaStacksResults.isNotEmpty()) return
            mediaStacksTarget = target
            mediaStacksResults = emptyList()
            mediaStacksOffset = 0
            mediaStacksHasMore = true
            mediaStacksScrollIndex = 0; mediaStacksScrollOffset = 0
        } else if (mediaStacksLoading || mediaStacksTarget != target || !mediaStacksHasMore) return
        val targetOffset = if (reset) 0 else mediaStacksOffset + 20
        mediaStacksLoading = true
        viewModelScope.launch {
            val page = runCatching { StacksApi().forMedia(mediaId, type, offset = targetOffset) }
                .getOrElse { StacksApi.MediaStacksPage(emptyList(), null) }
            if (mediaStacksTarget == target) {
                // MAL's own pagination here
                // not a stable cursor
                // after it, which can
                // first one. distinctBy guards
                // (StackListRow, key = it.id
                // otherwise crashes with "Key
                val merged = (if (reset) page.items else mediaStacksResults + page.items).distinctBy { it.id }
                mediaStacksResults = merged
                mediaStacksOffset = targetOffset
                mediaStacksHasMore = if (page.total != null) merged.size < page.total else page.items.size >= 20
            }
            mediaStacksLoading = false
        }
    }
    fun loadMoreMediaStacks(mediaId: Int, type: MediaType) = loadMediaStacksPage(mediaId, type, reset = false)
}