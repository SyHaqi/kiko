@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoSectionLoading
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.KikoLoadingRow
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.kiko.tracker.data.model.CharacterSummary
import com.kiko.tracker.data.model.CommonAnimeFormats
import com.kiko.tracker.data.model.CommonDemographics
import com.kiko.tracker.data.model.CommonExplicitGenres
import com.kiko.tracker.data.model.CommonGenres
import com.kiko.tracker.data.model.CommonMangaFormats
import com.kiko.tracker.data.model.CommonRatings
import com.kiko.tracker.data.model.CommonSources
import com.kiko.tracker.data.model.CommonThemes
import com.kiko.tracker.data.model.CompanySummary
import com.kiko.tracker.data.model.DiscoverFilters
import com.kiko.tracker.data.model.DiscoverMode
import com.kiko.tracker.data.model.ListViewMode
import com.kiko.tracker.data.model.LocalCompactList
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.PersonSummary
import com.kiko.tracker.data.model.RankingSort
import com.kiko.tracker.data.model.SeasonName
import com.kiko.tracker.data.model.currentSeasonName
import com.kiko.tracker.data.model.UserSearchFilters
import com.kiko.tracker.data.model.UserSummary
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.AirTimerPrefs
import com.kiko.tracker.data.model.airTimerLabel
import com.kiko.tracker.data.model.upcomingLabel
import com.kiko.tracker.data.model.systemIs24Hour
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.localBroadcast
import com.kiko.tracker.data.model.oneDecimal
import com.kiko.tracker.data.model.resolvedDiscoverType
import com.kiko.tracker.data.model.twoDecimals
import com.kiko.tracker.navigation.PopEnter
import com.kiko.tracker.navigation.PopExit
import com.kiko.tracker.navigation.PushEnter
import com.kiko.tracker.navigation.PushExit
import com.kiko.tracker.ui.components.AppHeader
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.Avatar
import com.kiko.tracker.ui.components.Cover
import com.kiko.tracker.ui.components.FloatingSearchSuggestions
import com.kiko.tracker.ui.components.Pill
import com.kiko.tracker.ui.components.SearchField
import com.kiko.tracker.ui.components.SearchTopBar
import com.kiko.tracker.ui.components.SearchBarFilterButton
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.accent
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCombinedClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.pressScale
import com.kiko.tracker.ui.theme.rememberStaggerMemory
import com.kiko.tracker.viewmodel.LibraryViewModel

@Composable fun DiscoverScreen(
    vm: LibraryViewModel,
    onOpenDetail: (MediaItem) -> Unit,
    onRanking: () -> Unit,
    onSeeRanking: (MediaType, RankingSort) -> Unit = { _, _ -> },
    onSeasonal: () -> Unit,
    onStacks: () -> Unit,
    onRecommendations: () -> Unit,
    onSchedule: (java.time.DayOfWeek) -> Unit = {},
    onExitResults: () -> Unit = vm::exitDiscoverSearch,
    onEdit: (MediaItem) -> Unit = {},
    selectedItem: MediaItem? = null,
    onOpenCharacter: (Int) -> Unit = {},
    onOpenPerson: (Int) -> Unit = {},
    onOpenCompany: (Int) -> Unit = {},
    onOpenUser: (String, String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    // loadHomeExtras() populates both trendingManga
    // ("You might like") —
    // own LaunchedEffect, so neither
    // you'd already opened "You
    // loadDiscoverBrowse() so both rows
    LaunchedEffect(vm.signedIn) { vm.loadDiscoverBrowse(context); vm.loadHomeExtras(context) }
    AnimatedContent(
        vm.discoverMode,
        transitionSpec = { if (targetState == DiscoverMode.Results) PushEnter togetherWith PushExit else PopEnter togetherWith PopExit },
        label = "discover-mode",
    ) { mode ->
        if (mode == DiscoverMode.Results) DiscoverResultsScreen(vm, context, onOpenDetail, onExitResults, onEdit, selectedItem, onOpenCharacter, onOpenPerson, onOpenCompany, onOpenUser)
        else DiscoverBrowseScreen(vm, context, onOpenDetail, onRanking, onSeeRanking, onSeasonal, onStacks, onRecommendations, onSchedule, onEdit, selectedItem)
    }
}
// Discover landing page

@Composable fun DiscoverBrowseScreen(
    vm: LibraryViewModel,
    context: Context,
    onOpenDetail: (MediaItem) -> Unit,
    onRanking: () -> Unit,
    onSeeRanking: (MediaType, RankingSort) -> Unit = { _, _ -> },
    onSeasonal: () -> Unit,
    onStacks: () -> Unit,
    onRecommendations: () -> Unit,
    onSchedule: (java.time.DayOfWeek) -> Unit = {},
    onEdit: (MediaItem) -> Unit = {},
    selectedItem: MediaItem? = null
) {
    val c = LocalKikoColors.current
    // Map (MAL id, type)
    // Tenrai/MAL search results, not
    // Keyed by id+type since
    val myListStatus = remember(vm.items) { vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap() }
    // Restore scroll position on
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.discoverBrowseScrollIndex, initialFirstVisibleItemScrollOffset = vm.discoverBrowseScrollOffset)
    val trackedOpenDetail: (MediaItem) -> Unit = remember(onOpenDetail) {
        { item -> vm.saveDiscoverBrowseScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onOpenDetail(item) }
    }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Only titles that actually premiered this season — vm.visibleDiscoverNewSeason
    // also carries older, still-airing shows (needed by the Release Schedule /
    // "airing next" rows elsewhere), so filter those out here instead of at the source.
    val curYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString() }
    val curSeason = remember { currentSeasonName() }
    val newSeasonPremieres = remember(vm.visibleDiscoverNewSeason, curYear, curSeason) {
        vm.visibleDiscoverNewSeason.filter { it.startDate == curYear && it.season.equals(curSeason.label, ignoreCase = true) }
    }
    // Everything airing today, regardless of premiere date — same
    // localBroadcast() day-bucketing ScheduleScreen uses, just filtered
    // down to today's day-of-week and sorted by local time (not shown).
    val today = remember { java.time.LocalDate.now().dayOfWeek }
    val todayReleases = remember(vm.visibleDiscoverNewSeason, today) {
        vm.visibleDiscoverNewSeason.mapNotNull { item -> item.localBroadcast()?.let { (day, time) -> if (day == today) item to time else null } }
            .sortedBy { it.second }.map { it.first }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                // Search now lives on
                // DiscoverResultsScreen) instead of an
                // icon that jumps there,
                // part of the header
                // Play Store-style header: full-width search pill + round avatar. Same fixed
                // slot, side margin and avatar position as every other main page (KikoHeader);
                // the pill simply takes all the width the other pages give to their title.
                // Tapping the pill opens the search screen.
                Row(
                    Modifier.fillMaxWidth().height(KikoHeader.Height),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(c.surfaceContainerHigh)
                            .kikoClickable { vm.openDiscoverSearch(context) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, "Search", tint = c.ink, modifier = Modifier.size(22.dp))
                        Text("Search in MAL", color = c.muted, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(start = 14.dp))
                    }
                    Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty(), showUpdateBadge = vm.updateInfo != null, size = KikoHeader.AvatarSize, circle = true) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect }
                }
                Spacer(Modifier.height(17.dp))

                // Seasonal now lives in
                // Rankings and Interest Stacks
                // edge-to-edge within the screen's
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscoverActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TrendingUp,
                        label = "Rankings",
                        onClick = onRanking
                    )
                    DiscoverActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Layers,
                        label = "Stacks",
                        onClick = onStacks
                    )
                }
            }

            if (vm.authChecked && !vm.signedIn) {
                item {
                    Text(
                        "Sign in from Profile to browse MyAnimeList",
                        color = c.muted,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // New this season row
                // Read each vm.visible* list
                // (the isNotEmpty() check and
                // is a cached (derivedStateOf-backed)
                // .take(n) on it still
                // read+take per row beats
                val newSeason = newSeasonPremieres.take(7)
                if (newSeason.isNotEmpty()) {
                    item {
                        SectionTitle("New this season", "See all", onSeasonal)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            // Cap row at 7
                            itemsIndexed(newSeason, key = { _, it -> it.id }) { index, item ->
                                StaggeredItem(index) { BrowseCard(item, trackedOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type) }
                            }
                        }
                    }
                }

                // Today row — everything releasing today, no time shown,
                // just a scrollable list. "See more" jumps into the full
                // Release Schedule pre-selected on today.
                if (todayReleases.isNotEmpty()) {
                    item {
                        SectionTitle("Today", "See more", { onSchedule(today) })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            itemsIndexed(todayReleases, key = { _, it -> it.id }) { index, item ->
                                StaggeredItem(index) { BrowseCard(item, trackedOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type) }
                            }
                        }
                    }
                }

                // Top 10 upcoming row
                val upcoming = vm.visibleDiscoverUpcoming
                if (upcoming.isNotEmpty()) {
                    item {
                        SectionTitle("Top 10 upcoming", "See more", { onSeeRanking(MediaType.Anime, RankingSort.Upcoming) })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            itemsIndexed(upcoming, key = { _, it -> it.id }) { index, item ->
                                StaggeredItem(index) { BrowseCard(item, trackedOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type) }
                            }
                        }
                    }
                }

                // Trending manga row
                val trendingManga = vm.visibleTrendingManga.take(6)
                if (trendingManga.isNotEmpty()) {
                    item {
                        SectionTitle("Trending manga", "See more", { onSeeRanking(MediaType.Manga, RankingSort.Popularity) })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            // Cap row at 6
                            itemsIndexed(trendingManga, key = { _, it -> it.id }) { index, item ->
                                StaggeredItem(index) { BrowseCard(item, trackedOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type) }
                            }
                        }
                    }
                }

                // Recommendations row
                val recommendations = vm.visibleRecommendations.take(7)
                if (recommendations.isNotEmpty()) {
                    item {
                        SectionTitle("You might like", "See more", onRecommendations)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            // Cap row at 7;
                            itemsIndexed(recommendations, key = { _, it -> it.id }) { index, item ->
                                StaggeredItem(index) { BrowseCard(item, trackedOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type) }
                            }
                        }
                    }
                }

                // Loading and error states
                if (vm.discoverBrowseLoading) {
                    item {
                        KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary)
                    }
                }
                vm.discoverBrowseError?.let { error ->
                    item {
                        Text(error, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
        )
    }
}

// Ranking/Seasonal action card

@Composable fun DiscoverActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val c = LocalKikoColors.current
    // Same fix as AiringNextCard:
    // our own .kikoClickable on
    // clipped to the card's
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(kikoCorner(16.dp)),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
        modifier = modifier.pressScale(interactionSource)
    ) {
        Row(
            Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink)
        }
    }
}

// Interest Stacks homepage —
// Greets the user when

@Composable fun DiscoverResultsScreen(vm: LibraryViewModel, context: Context, onOpenDetail: (MediaItem) -> Unit, onExitResults: () -> Unit = vm::exitDiscoverSearch, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null, onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {}, onOpenUser: (String, String?) -> Unit = { _, _ -> }) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf(vm.discoverQuery) }
    var searchFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadSearchHistory(context) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var userFilterSheetOpen by remember { mutableStateOf(false) }
    // Genre section only pre-expands the first time the sheet is opened
    // via requestDiscoverFilterSheet() below — reset once consumed so a
    // later manual tap on the filter icon behaves normally (expanded only
    // when a genre's already selected, same as before).
    var forceExpandGenre by remember { mutableStateOf(false) }
    LaunchedEffect(vm.discoverFilterSheetTick) {
        if (vm.discoverFilterSheetTick > vm.discoverFilterSheetConsumedTick) {
            filterSheetOpen = true
            forceExpandGenre = true
            vm.prewarmGenreLookup()
            vm.consumeDiscoverFilterSheet()
        }
    }
    BackHandler(onBack = onExitResults)
    // Every search type (Anime/Manga/Characters/People/Companies/Users) honours the saved
    // List layout preference; each type has its own grid tile.
    val isGrid = vm.listViewMode == ListViewMode.Grid
    val staggerSeen = rememberStaggerMemory()
    // Restore results scroll position
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.discoverScrollIndex, initialFirstVisibleItemScrollOffset = vm.discoverScrollOffset)
    val openResult: (MediaItem) -> Unit = { result ->
        vm.saveDiscoverScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        vm.openDiscoverDetail(context, result, onOpenDetail)
    }
    // Long-press to edit —
    // since a bare search
    val editResult: (MediaItem) -> Unit = { result -> vm.openDiscoverDetail(context, result, onEdit) }
    // Character/People rows navigate immediately
    // openCharacter/openPerson and characterDetailOpenId's doc
    // fetching the full page
    // onOpenPerson just take the
    val openCharacterResult: (CharacterSummary) -> Unit = { result ->
        vm.saveDiscoverScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onOpenCharacter(result.malId)
    }
    val openPersonResult: (PersonSummary) -> Unit = { result ->
        vm.saveDiscoverScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onOpenPerson(result.malId)
    }
    val openCompanyResult: (CompanySummary) -> Unit = { result ->
        vm.saveDiscoverScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onOpenCompany(result.malId)
    }
    val openUserResult: (UserSummary) -> Unit = { result ->
        vm.saveDiscoverScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onOpenUser(result.username, result.avatarUrl.takeIf { it.isNotBlank() })
    }
    // Search results come straight
    // each result's baked-in status
    // update on its own
    // Same id+type keyed override
    // recomputed whenever vm.items changes
    val myListStatus = remember(vm.items) { vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap() }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // A new search (typed query, type switch, sort/filter change) must jump back to the first
    // result and reveal the loading indicator at the top of the list; otherwise, if you had
    // scrolled down, the search ran off-screen with nothing to show for it.
    val searchingNow = when (vm.discoverTypeFilter) {
        "Characters" -> vm.characterSearching
        "People" -> vm.personSearching
        "Companies" -> vm.companySearching
        "Users" -> vm.userSearching
        else -> vm.discoverSearching
    }
    LaunchedEffect(searchingNow) { if (searchingNow) listState.scrollToItem(0) }
    // Bounds (in root coordinates)
    // the suggestions list directly
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    var typeRowBounds by remember { mutableStateOf<Rect?>(null) }
    // Bumped by the search
    // Discover bottom-nav tab) via
    // and pop the keyboard
    // there untouched — a
    // against the ViewModel's own
    // discoverSearchFocusConsumedTick) instead of just
    // screen gets torn down
    // would have no memory
    // re-fire (and pop the
    // that never asked for
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(vm.discoverSearchFocusTick) {
        if (vm.discoverSearchFocusTick > vm.discoverSearchFocusConsumedTick) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
            vm.consumeDiscoverSearchFocus()
        }
    }
    // Load the next page
    // currently loaded (not a
    // a fling is still
    // before the next page's
    // where they are. Still
    // DiscoverPaginationSource/discoverHasMore) — for studio/author,
    // results the list has
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore, vm.visibleDiscoverResults.size, vm.discoverTypeFilter) {
        if (shouldLoadMore) {
            if (vm.discoverTypeFilter == "Users") vm.loadMoreUserSearch() else vm.loadMoreDiscoverSearch(context)
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { containerBounds = it.boundsInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            SearchTopBar(
                value = query,
                onValueChange = { query = it; if (vm.discoverTypeFilter == "Anime" || vm.discoverTypeFilter == "Manga") vm.fetchDiscoverSuggestions(context, it, vm.discoverTypeFilter) else vm.clearDiscoverSuggestions() },
                hint = "Search ${vm.discoverTypeFilter}",
                onSearch = { vm.clearDiscoverSuggestions(); vm.addSearchHistory(context, vm.discoverTypeFilter, query); vm.selectDiscoverType(context, vm.discoverTypeFilter, query) },
                onFocusChange = { searchFocused = it },
                onBack = onExitResults,
                focusRequester = searchFocusRequester,
                modifier = Modifier.onGloballyPositioned { searchBarBounds = it.boundsInRoot() },
            ) {
                // Advanced filters (format/genre/year/score) only apply to Anime/Manga; Users have
                // their own sheet (location/age/gender). Characters/People/Companies have none.
                if (vm.discoverTypeFilter == "Anime" || vm.discoverTypeFilter == "Manga") {
                    SearchBarFilterButton(active = vm.discoverFilters.isActive(), onClick = { filterSheetOpen = true; vm.prewarmGenreLookup() })
                }
                if (vm.discoverTypeFilter == "Users") {
                    SearchBarFilterButton(active = vm.userFilters.isActive(), onClick = { userFilterSheetOpen = true })
                }
            }
            // Fixes type/format mismatch
            if (filterSheetOpen) AdvancedFilterSheet(vm.discoverFilters, type = vm.discoverTypeFilter, onDismiss = { filterSheetOpen = false; forceExpandGenre = false }, onApply = { filterSheetOpen = false; forceExpandGenre = false; vm.runDiscoverSearch(context, query, resolvedDiscoverType(it.format, vm.discoverTypeFilter), it) }, forceExpandGenre = forceExpandGenre)
            if (userFilterSheetOpen) UserAdvancedFilterSheet(vm.userFilters, onDismiss = { userFilterSheetOpen = false }, onApply = { userFilterSheetOpen = false; vm.runUserSearch(query, it) })
            val gridRows = remember(vm.visibleDiscoverResults, isGrid) { if (isGrid) vm.visibleDiscoverResults.chunked(3) else emptyList() }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
                // Type dropdown + sort scroll away with the results. Its bounds are tracked so the
                // floating suggestions can start below it while it's on screen.
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp).heightIn(min = 48.dp).onGloballyPositioned { typeRowBounds = it.boundsInRoot() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        DiscoverTypeDropdown(current = vm.discoverTypeFilter, onSelect = { picked -> vm.selectDiscoverType(context, picked, query) })
                        if (vm.discoverTypeFilter == "Anime" || vm.discoverTypeFilter == "Manga") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DiscoverSortMenu(current = vm.discoverSort, onSelect = { vm.selectDiscoverSort(context, it) })
                            }
                        }
                    }
                }
                item {
                    when (vm.discoverTypeFilter) {
                        "Characters" -> {
                            if (vm.characterSearching && vm.characterResults.isNotEmpty()) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary)
                            vm.characterError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                        }
                        "People" -> {
                            if (vm.personSearching && vm.personResults.isNotEmpty()) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary)
                            vm.personError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                        }
                        "Companies" -> {
                            if (vm.companySearching && vm.companyResults.isNotEmpty()) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary)
                            vm.companyError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                        }
                        "Users" -> {
                            if (vm.userSearching && vm.userResults.isNotEmpty()) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary)
                            vm.userError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                        }
                        "Anime", "Manga" -> {
                            if (vm.discoverSearching && vm.visibleDiscoverResults.isNotEmpty()) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary)
                            vm.discoverError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                        }
                        else -> {}
                    }
                }
                when (vm.discoverTypeFilter) {
                    "Characters" -> {
                        if (!vm.characterSearching && vm.characterResults.isEmpty() && vm.characterError == null) {
                            val emptyMessage = if (vm.discoverQuery.isBlank()) "Search for a character." else "No characters for \"${vm.discoverQuery}\"."
                            item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                        }
                        val charResults = vm.characterResults
                        if (vm.characterSearching && charResults.isEmpty()) {
                            item { KikoSectionLoading() }
                        } else if (isGrid) {
                            entityGridRows(charResults, { "char_${it.malId}" }, staggerSeen) { result ->
                                EntityGridCard(
                                    title = result.name,
                                    subtitle = result.altName.ifBlank { result.relatedWorks.firstOrNull().orEmpty() },
                                    imageUrl = result.image,
                                    loading = vm.characterDetailLoadingId == result.malId,
                                    onTap = { openCharacterResult(result) },
                                )
                            }
                        } else {
                            itemsIndexed(charResults, key = { _, it -> "char_${it.malId}" }) { index, result ->
                                StaggeredItem(index, staggerSeen) {
                                    Column {
                                        CharacterSearchResultRow(result, loading = vm.characterDetailLoadingId == result.malId, onTap = { openCharacterResult(result) })
                                    }
                                }
                            }
                        }
                    }
                    "People" -> {
                        if (!vm.personSearching && vm.personResults.isEmpty() && vm.personError == null) {
                            val emptyMessage = if (vm.discoverQuery.isBlank()) "Search for a person." else "No people for \"${vm.discoverQuery}\"."
                            item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                        }
                        val peopleResults = vm.personResults
                        if (vm.personSearching && peopleResults.isEmpty()) {
                            item { KikoSectionLoading() }
                        } else if (isGrid) {
                            entityGridRows(peopleResults, { "person_${it.malId}" }, staggerSeen) { result ->
                                EntityGridCard(
                                    title = result.name,
                                    subtitle = result.altName,
                                    imageUrl = result.image,
                                    loading = vm.personDetailLoadingId == result.malId,
                                    onTap = { openPersonResult(result) },
                                )
                            }
                        } else {
                            itemsIndexed(peopleResults, key = { _, it -> "person_${it.malId}" }) { index, result ->
                                StaggeredItem(index, staggerSeen) {
                                    Column {
                                        PersonSearchResultRow(result, loading = vm.personDetailLoadingId == result.malId, onTap = { openPersonResult(result) })
                                    }
                                }
                            }
                        }
                    }
                    "Companies" -> {
                        if (!vm.companySearching && vm.companyResults.isEmpty() && vm.companyError == null) {
                            val emptyMessage = if (vm.discoverQuery.isBlank()) "Search for a company." else "No companies for \"${vm.discoverQuery}\"."
                            item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                        }
                        val companyResultsList = vm.companyResults
                        if (vm.companySearching && companyResultsList.isEmpty()) {
                            item { KikoSectionLoading() }
                        } else if (isGrid) {
                            entityGridRows(companyResultsList, { "company_${it.malId}" }, staggerSeen) { result ->
                                EntityGridCard(
                                    title = result.name,
                                    subtitle = result.japanese,
                                    imageUrl = result.image,
                                    loading = vm.companyDetailLoadingId == result.malId,
                                    fit = true, square = true,
                                    onTap = { openCompanyResult(result) },
                                )
                            }
                        } else {
                            itemsIndexed(companyResultsList, key = { _, it -> "company_${it.malId}" }) { index, result ->
                                StaggeredItem(index, staggerSeen) {
                                    Column {
                                        CompanySearchResultRow(result, loading = vm.companyDetailLoadingId == result.malId, onTap = { openCompanyResult(result) })
                                    }
                                }
                            }
                        }
                    }
                    "Users" -> {
                        if (!vm.userSearching && vm.userResults.isEmpty() && vm.userError == null) {
                            val emptyMessage = if (vm.discoverQuery.isBlank() && !vm.userFilters.isActive()) "Search for a user." else "No users found."
                            item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                        }
                        val userResultsList = vm.userResults
                        if (vm.userSearching && userResultsList.isEmpty()) {
                            item { KikoSectionLoading() }
                        } else if (isGrid) {
                            entityGridRows(userResultsList, { "user_${it.username}" }, staggerSeen) { result ->
                                // MAL prints "Mar 9, 2010 3:27 AM"; drop the time so it fits a 3-wide tile.
                                val joinedDate = result.joined.replace(Regex("\\s+\\d{1,2}:\\d{2}\\s*[AP]M$", RegexOption.IGNORE_CASE), "")
                                EntityGridCard(
                                    title = result.username,
                                    subtitle = if (joinedDate.isNotBlank()) "Joined $joinedDate" else "",
                                    imageUrl = result.avatarUrl,
                                    onTap = { openUserResult(result) },
                                )
                            }
                        } else {
                            itemsIndexed(userResultsList, key = { _, it -> "user_${it.username}" }) { index, result ->
                                StaggeredItem(index, staggerSeen) {
                                    Column {
                                        UserSearchResultRow(result, onTap = { openUserResult(result) })
                                    }
                                }
                            }
                        }
                        if (vm.userLoadingMore) {
                            item { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 28.dp) } }
                        }
                    }
                    else -> {
                        if (!vm.discoverSearching && vm.visibleDiscoverResults.isEmpty() && vm.discoverError == null) {
                            val emptyMessage = if (vm.discoverQuery.isBlank()) "No results match your filters." else "No results for \"${vm.discoverQuery}\"."
                            item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                        }
                        // Keyed by id+type together,
                        // spaces, so a search
                        // an unrelated manga with
                        // id. LazyColumn requires unique
                        // ("Key ... was already
                        val resultsForList = vm.visibleDiscoverResults
                        if (vm.discoverSearching && resultsForList.isEmpty()) {
                            item { KikoSectionLoading() }
                        } else if (isGrid) {
                            // LazyColumn (not LazyVerticalGrid) throughout this screen so the
                            // header/filters/type-branches above stay single-column — grid mode
                            // just chunks results into 3-wide rows instead of switching containers.
                            val rows = gridRows
                            itemsIndexed(rows, key = { rowIndex, row -> "discover_row_${row.firstOrNull()?.let { "${it.id}_${it.type}" } ?: rowIndex}" }) { rowIndex, rowItems ->
                                StaggeredItem(rowIndex, staggerSeen) {
                                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                        rowItems.forEach { result ->
                                            Box(Modifier.weight(1f)) {
                                                RecommendationGridCard(
                                                    result, onOpenDetail = openResult,
                                                    myStatus = result.id.toIntOrNull()?.let { myListStatus[it to result.type] },
                                                    onLongPress = editResult,
                                                    isSelected = selectedItem?.id == result.id && selectedItem?.type == result.type,
                                                )
                                            }
                                        }
                                        // Keep the last, possibly-partial row's cards from stretching wide
                                        repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(resultsForList, key = { _, it -> "${it.id}_${it.type}" }) { index, result ->
                                StaggeredItem(index, staggerSeen) {
                                    Column {
                                        SearchResultRow(result, loading = vm.discoverDetailLoadingId == result.id, onTap = { openResult(result) }, onLongPress = { editResult(result) }, isSelected = selectedItem?.id == result.id && selectedItem?.type == result.type, myStatus = result.id.toIntOrNull()?.let { myListStatus[it to result.type] })
                                    }
                                }
                            }
                        }
                    }
                }
                if (vm.discoverLoadingMore) {
                    item { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 28.dp) } }
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
        )
        // Floating title suggestions as
        // run that search; tapping
        FloatingSearchSuggestions(
            // Suggestions start below the bar's divider line, and below the type/sort row while that row is
            // visible (item 0 of the list), so neither is covered.
            anchorBounds = searchBarBounds?.let { b ->
                val dividerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 1.dp.toPx() }
                val rowBottom = if (listState.firstVisibleItemIndex == 0) typeRowBounds?.bottom ?: 0f else 0f
                Rect(b.left, b.top, b.right, maxOf(b.bottom + dividerPx, rowBottom))
            },
            containerBounds = containerBounds,
            suggestions = if (query.isNotBlank()) vm.discoverSuggestions else if (searchFocused) vm.searchHistoryFor(vm.discoverTypeFilter) else emptyList(),
            history = query.isBlank(),
            onRemove = { vm.removeSearchHistory(context, vm.discoverTypeFilter, it) },
            onClearAll = { vm.clearSearchHistory(context, vm.discoverTypeFilter) },
            onDismiss = vm::clearDiscoverSuggestions,
            // Arrow on a suggestion: copy it into the field and refine from there (suggestions refresh).
            onFill = { picked -> query = picked; vm.fetchDiscoverSuggestions(context, picked, vm.discoverTypeFilter) },
        ) { picked ->
            query = picked
            vm.addSearchHistory(context, vm.discoverTypeFilter, picked)
            vm.selectDiscoverType(context, vm.discoverTypeFilter, picked)
        }
    }
}
// Filters button with indicator

@Composable fun FilterIconButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(if (active) c.primary else c.surfaceContainerHigh)
            .kikoClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Tune, "Advanced filters", tint = if (active) c.onPrimary else c.ink) }
}
// Collapsible multi-select facet

@Composable fun ExpandableFilterSection(title: String, options: List<String>, selected: Set<String>, onToggle: (String) -> Unit, initiallyExpanded: Boolean = selected.isNotEmpty()) {
    val c = LocalKikoColors.current
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(12.dp))).clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (selected.isNotEmpty()) {
                    Box(Modifier.padding(start = 8.dp).clip(kikoCircleShape()).background(c.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(selected.size.toString(), color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse $title" else "Expand $title", tint = c.muted)
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 9.dp)) {
                options.forEach { o -> FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(o) }, colors = kikoFilterChipColors()) }
            }
        }
    }
}
// Discover advanced filters sheet

@Composable fun AdvancedFilterSheet(current: DiscoverFilters, type: String, onDismiss: () -> Unit, onApply: (DiscoverFilters) -> Unit, forceExpandGenre: Boolean = false) {
    val c = LocalKikoColors.current
    // Split combined genre facets. MAL's detail-page chips mix genres, themes and demographics, and
    // tapping one arrives here as `current.genres` regardless of which kind it is (see onGenreClick in
    // Navigation). The sheet only highlights a chip inside its own section, so a tapped theme like
    // "Isekai" or demographic like "Shounen" showed nothing selected. File each tag under the section
    // whose options actually contain it; anything unrecognised stays in Genre as before.
    val themeTags = current.genres.filter { it in CommonThemes }
    val demographicTags = current.genres.filter { it in CommonDemographics }
    var genres by remember { mutableStateOf(current.genres.filter { it !in CommonExplicitGenres && it !in CommonThemes && it !in CommonDemographics }.toSet()) }
    var explicitGenres by remember { mutableStateOf(current.genres.filter { it in CommonExplicitGenres }.toSet()) }
    var themes by remember { mutableStateOf(current.themes + themeTags) }
    var demographics by remember { mutableStateOf(current.demographics + demographicTags) }
    var creator by remember { mutableStateOf(current.creator) }
    var source by remember { mutableStateOf(current.source) }
    var year by remember { mutableStateOf(current.year) }
    var season by remember { mutableStateOf(current.season) }
    var rating by remember { mutableStateOf(current.rating) }
    var format by remember { mutableStateOf(current.format) }
    var airingStatus by remember { mutableStateOf(current.airingStatus) }
    val airingOptions = listOf("Ongoing", "Finished", "Upcoming")
    val formatOptions = when (type) { "Anime" -> CommonAnimeFormats; "Manga" -> CommonMangaFormats; else -> CommonAnimeFormats + CommonMangaFormats }
    // Opens half-screen (partially expanded);
    // to return to half
    // left off so the
    val sheetState = rememberModalBottomSheetState()
    // contentWindowInsets = {} decouples
    // resize/glitch when the keyboard
    // imePadding() below pushes the
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding().verticalScroll(rememberScrollState())) {
            Text("Discover", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Advanced filters", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))

            ExpandableFilterSection("Genre", CommonGenres, genres, onToggle = { g -> genres = if (g in genres) genres - g else genres + g }, initiallyExpanded = genres.isNotEmpty() || forceExpandGenre)
            // Separate explicit genre section
            ExpandableFilterSection("Explicit genre", CommonExplicitGenres, explicitGenres, onToggle = { g -> explicitGenres = if (g in explicitGenres) explicitGenres - g else explicitGenres + g })
            // Separate themes section
            ExpandableFilterSection("Themes", CommonThemes, themes, onToggle = { t -> themes = if (t in themes) themes - t else themes + t })
            ExpandableFilterSection("Demographics", CommonDemographics, demographics, onToggle = { d -> demographics = if (d in demographics) demographics - d else demographics + d })

            Text("Type", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formatOptions.forEach { f -> FilterChip(selected = format == f, onClick = { format = if (format == f) "" else f }, label = { Text(f) }, colors = kikoFilterChipColors()) }
            }

            Text("Status", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                airingOptions.forEach { s -> FilterChip(selected = airingStatus == s, onClick = { airingStatus = if (airingStatus == s) "" else s }, label = { Text(s) }, colors = kikoFilterChipColors()) }
            }

            // Anime searches by studio,
            // When type is ambiguous
            val creatorLabel = when (type) { "Anime" -> "Studio"; "Manga" -> "Author"; else -> "Studio / Author" }
            val creatorHint = when (type) { "Anime" -> "e.g. Madhouse"; "Manga" -> "e.g. Eiichiro Oda"; else -> "e.g. Madhouse or Eiichiro Oda" }
            Text(creatorLabel, color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            OutlinedTextField(
                value = creator, onValueChange = { creator = it }, placeholder = { Text(creatorHint, color = c.muted) }, singleLine = true,
                shape = RoundedCornerShape(kikoCorner(14.dp)),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.outline, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Source", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            val sourceListState = rememberLazyListState()
            val sourceScope = rememberCoroutineScope()
            LazyRow(state = sourceListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(CommonSources) { index, s -> FilterChip(selected = source == s, onClick = { source = if (source == s) "" else s; sourceScope.centerChip(sourceListState, index) }, label = { Text(s) }, colors = kikoFilterChipColors()) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Year", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 9.dp))
                    OutlinedTextField(
                        value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) }, placeholder = { Text("e.g. 2023", color = c.muted) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(kikoCorner(14.dp)),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.outline, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Season", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SeasonName.entries.forEach { s -> SeasonIconButton(selected = s == season, season = s) { season = if (season == s) null else s } }
                    }
                }
            }

            Text("Rating", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommonRatings.forEach { r -> FilterChip(selected = rating == r, onClick = { rating = if (rating == r) "" else r }, label = { Text(r, maxLines = 1) }, colors = kikoFilterChipColors()) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { genres = emptySet(); explicitGenres = emptySet(); themes = emptySet(); demographics = emptySet(); creator = ""; source = ""; year = ""; season = null; rating = ""; format = ""; airingStatus = "" },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset", color = c.muted, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { onApply(DiscoverFilters(genres + explicitGenres, themes, demographics, creator.trim(), source, year, season, rating, format, airingStatus)) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    modifier = Modifier.weight(2f),
                ) { Text("Apply filters", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
// Browse row cover card

@Composable fun BrowseCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, subtitle: String? = null, myStatus: WatchStatus? = null, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val bg = Color.Transparent
    val pad = 0.dp
    Column(
        Modifier
            .width(118.dp)
            .clip(RoundedCornerShape(kikoCorner(14.dp)))
            .background(bg)
            .kikoCombinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { edit(item) } },
            )
            // animateDpAsState on `pad` above
            // value frame-by-frame, so the
            // animateContentSize() here was doing
            // pass on top of
            // transitioning, purely to re-derive
            .padding(pad)
    ) {
        // Same 84:118 cover ratio
        // this row's old fixed
        // Crop was cutting off
        Cover(item, Modifier.fillMaxWidth().aspectRatio(84f / 118f), showStatus = true, overrideStatus = myStatus, selected = isSelected)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp))
        if (subtitle != null) {
            Text(subtitle, color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            ScoreLine(item.score, decimals = 1)
        }
    }
}

// Score line under the Discover-tab row cards (BrowseCard). Always renders a star + value, or
// "N/A" when there's no score, at a fixed 16dp height so the line is identical on every card.
// (Grid tiles use SeasonalGridCard's own score row instead.)
// A real Icon + explicit lineHeight is used instead of a text "★" glyph, which fell back to a
// different font than plain text and shifted the line height.
@Composable fun ScoreLine(score: Double, decimals: Int = 1, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(top = 2.dp).height(16.dp)) {
        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
        val label = if (score > 0) (if (decimals == 2) score.twoDecimals() else score.oneDecimal()) else "N/A"
        // Center + Trim.None pins the glyphs in the middle of the 16sp line box. The default
        // (Proportional + Trim.Both) splits/trims the extra line height unevenly for a single
        // line, which left the number sitting off-center next to the star.
        Text(
            label, color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1,
            style = TextStyle(lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)),
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}
// Discover search result row

@Composable fun SearchResultRow(item: MediaItem, loading: Boolean, onTap: () -> Unit, onLongPress: (() -> Unit)? = null, isSelected: Boolean = false, myStatus: WatchStatus? = null, modifier: Modifier = Modifier, vm: LibraryViewModel? = null) {
    val c = LocalKikoColors.current
    val compact = LocalCompactList.current
    val coverW = if (compact) 72.dp else 100.dp
    val coverH = if (compact) 108.dp else 150.dp
    val rowVPad = if (compact) 10.dp else 14.dp
    // Optional vm (Home's Airing next row): use the confirmed episode air time, same as ListRow.
    if (vm != null) LaunchedEffect(item.id) { vm.loadAiringEpisode(item) }
    val confirmed = vm?.getCachedAiring(item.id)
    // Airing -> "Ep. N airs on ..."; not started yet -> "Not Yet Aired". Never both.
    val is24 = systemIs24Hour()
    val upcoming = remember(item) { item.upcomingLabel() }
    // Live countdown: only airing rows in countdown style run a clock, ticking once per wall-clock minute.
    // The tick is a remember key, so the label recomputes each minute and otherwise stays cached.
    val ticking = AirTimerPrefs.countdown && item.airStatus.equals("Currently Airing", ignoreCase = true)
    val minuteTick by produceState(0L, ticking) {
        if (!ticking) return@produceState
        while (true) {
            val now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000L - now % 60_000L)
            value = System.currentTimeMillis() / 60_000L
        }
    }
    val timer = remember(item, confirmed, is24, AirTimerPrefs.countdown, minuteTick) { item.airTimerLabel(confirmed, is24) ?: upcoming }
    // Accent for the live air timer, muted for the quieter "Not Yet Aired" label.
    val timerTint = if (upcoming != null) c.muted else c.accent
    // "TV · 23 ep, Spring 2018" — no progress bar here, so the format joins the episodes/year line.
    val details = remember(item) { listOf(formatLabel(item), episodeAndYear(item)).filter { it.isNotBlank() }.joinToString(" · ") }
    val bg = Color.Transparent
    val hPad = 0.dp
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(16.dp)))
            .background(bg)
            .kikoCombinedClickable(
                enabled = !loading,
                onClick = onTap,
                onLongClick = onLongPress?.let { edit -> { edit() } },
            )
            .padding(horizontal = hPad, vertical = rowVPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same 100x150 cover as ListRow so search results line up with My List.
        Box(Modifier.size(width = coverW, height = coverH)) {
            Cover(item, Modifier.fillMaxSize(), showStatus = true, overrideStatus = myStatus, selected = isSelected)
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = Color.White, size = 28.dp)
                }
            }
        }
        // Same structure as ListRow: title, then the "format · episodes, year" line right
        // under it (and the air timer below that while airing), pinned to the top; the
        // members count pinned to the bottom.
        Column(
            Modifier.weight(1f).height(coverH).padding(start = 16.dp, end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (details.isNotBlank()) {
                    Text(details, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                if (timer != null) {
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = timerTint, modifier = Modifier.size(14.dp))
                        Text(timer, color = timerTint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Column {
                run {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Star always shows; unrated titles read "N/A" instead of the rating vanishing.
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                        Text(if (item.score > 0) item.score.twoDecimals() else "N/A", color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp))
                        if (item.listUsers > 0) {
                            // Members count to the right of the MAL score, separated by a dot.
                            Text("·", color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 6.dp))
                            Icon(Icons.Default.Group, null, tint = c.muted, modifier = Modifier.size(13.dp))
                            Text(formatExact(item.listUsers), color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 5.dp))
                        }
                    }
                }
            }
        }
    }
}
// Format episode/season label

// Discover's type-filter chip —
// rather than a fixed
// their own search entirely
// FilterChips side by side
@Composable fun DiscoverTypeDropdown(current: String, onSelect: (String) -> Unit) {
    val c = LocalKikoColors.current
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Anime", "Manga", "Characters", "Companies", "People", "Users")
    Box {
        // Sized to match the sort button beside it (30dp tall, 12sp label, 16dp icon).
        FilterChip(
            modifier = Modifier.height(30.dp),
            selected = true,
            onClick = { expanded = true },
            label = { Text(current, fontSize = 12.sp) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp)) },
            colors = kikoFilterChipColors(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(kikoCorner(16.dp)), containerColor = c.surfaceContainer) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = c.ink) },
                    onClick = { expanded = false; onSelect(option) },
                    trailingIcon = if (option == current) { { Icon(Icons.Default.Check, null, tint = c.primary, modifier = Modifier.size(18.dp)) } } else null,
                )
            }
        }
    }
}

// One row in the
// above (92x128 thumbnail, name
// instead of MediaItem since
@Composable fun CharacterSearchResultRow(entry: CharacterSummary, loading: Boolean, onTap: () -> Unit) {
    val c = LocalKikoColors.current
    val compact = LocalCompactList.current
    val coverW = if (compact) 72.dp else 100.dp
    val coverH = if (compact) 108.dp else 150.dp
    val rowVPad = if (compact) 10.dp else 14.dp
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(enabled = !loading, onClick = onTap).padding(vertical = rowVPad),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(width = coverW, height = coverH).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (entry.image.isNotBlank()) {
                AsyncImage(model = entry.image, contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(entry.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = Color.White, size = 28.dp)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.altName.isNotBlank()) {
                Text(entry.altName, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
            if (entry.relatedWorks.isNotEmpty()) {
                Text(entry.relatedWorks.take(2).joinToString(" · "), color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }
}

// One row in the
// just off PersonSummary (no
// carry that the way
@Composable fun PersonSearchResultRow(entry: PersonSummary, loading: Boolean, onTap: () -> Unit) {
    val c = LocalKikoColors.current
    val compact = LocalCompactList.current
    val coverW = if (compact) 72.dp else 100.dp
    val coverH = if (compact) 108.dp else 150.dp
    val rowVPad = if (compact) 10.dp else 14.dp
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(enabled = !loading, onClick = onTap).padding(vertical = rowVPad),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(width = coverW, height = coverH).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (entry.image.isNotBlank()) {
                AsyncImage(model = entry.image, contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(entry.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = Color.White, size = 28.dp)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.altName.isNotBlank()) {
                Text(entry.altName, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

// One row in the
// PersonSearchResultRow above, but with
// one, matching how MAL's
// own square logo treatment).
@Composable fun CompanySearchResultRow(entry: CompanySummary, loading: Boolean, onTap: () -> Unit) {
    val c = LocalKikoColors.current
    val compact = LocalCompactList.current
    val logoSize = if (compact) 72.dp else 100.dp
    val rowVPad = if (compact) 10.dp else 14.dp
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(enabled = !loading, onClick = onTap).padding(vertical = rowVPad),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(logoSize).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (entry.image.isNotBlank()) {
                AsyncImage(model = entry.image, contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text(entry.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = Color.White, size = 28.dp)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.japanese.isNotBlank()) {
                Text(entry.japanese, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

// One row in the Users tab's search results — same rounded-square
// treatment as CompanySearchResultRow above (no cropping to a face —
// MAL user avatars, like company logos, come in whatever aspect ratio
// the user uploaded), with the joined date standing in for the
// japanese/altName subtitle line those two show.
@Composable fun UserSearchResultRow(entry: UserSummary, onTap: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(onClick = onTap).padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(100.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (entry.avatarUrl.isNotBlank()) {
                AsyncImage(model = entry.avatarUrl, contentDescription = entry.username, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(entry.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(entry.username, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.joined.isNotBlank()) {
                Text("Joined ${entry.joined}", color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

// Users tab's own advanced filters sheet — MAL's users.php Location/Age
// range/Gender panel, a much smaller sibling of AdvancedFilterSheet
// above (none of that one's genre/format/year facets apply to a user
// search).
@Composable fun UserAdvancedFilterSheet(current: UserSearchFilters, onDismiss: () -> Unit, onApply: (UserSearchFilters) -> Unit) {
    val c = LocalKikoColors.current
    var location by remember { mutableStateOf(current.location) }
    var ageLow by remember { mutableStateOf(current.ageLow?.toString() ?: "") }
    var ageHigh by remember { mutableStateOf(current.ageHigh?.toString() ?: "") }
    var gender by remember { mutableStateOf(current.gender) }
    val genderOptions = listOf("", "Male", "Female", "Non-Binary")
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding().verticalScroll(rememberScrollState())) {
            Text("Users", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Advanced filters", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))

            Text("Location", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            OutlinedTextField(
                value = location, onValueChange = { location = it },
                placeholder = { Text("Ex: California") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Age", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = ageLow, onValueChange = { ageLow = it.filter(Char::isDigit) },
                    placeholder = { Text("Min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Text("to", color = c.muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = ageHigh, onValueChange = { ageHigh = it.filter(Char::isDigit) },
                    placeholder = { Text("Max") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Gender", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                genderOptions.forEach { option ->
                    FilterChip(
                        selected = gender == option,
                        onClick = { gender = option },
                        label = { Text(option.ifBlank { "Don't care" }) },
                        colors = kikoFilterChipColors(),
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { location = ""; ageLow = ""; ageHigh = ""; gender = "" },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset") }
                Button(
                    onClick = { onApply(UserSearchFilters(location = location.trim(), ageLow = ageLow.toIntOrNull(), ageHigh = ageHigh.toIntOrNull(), gender = gender)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Apply") }
            }
        }
    }
}

fun episodeAndYear(item: MediaItem): String {
    val unit = if (item.type == MediaType.Anime) "ep" else "ch"
    val episodes = if (item.total > 0) "${item.total} $unit" else null
    val year = seasonYear(item.season, item.startDate).takeIf { it.isNotBlank() }
    return listOfNotNull(episodes, year).joinToString(", ")
}
// Comma-format member count

fun formatExact(n: Int): String = "%,d".format(n)

// "You might like" —

@Composable fun RecommendationsScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.loadHomeExtras(context) }
    // Same id+type keyed status
    val myListStatus = remember(vm.items) { vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap() }
    val gridState = rememberLazyGridState()
    val staggerSeen = rememberStaggerMemory()
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 600 } }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 19.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
                    Text("You might like", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.headerTitleStart())
                }
            }
            if (vm.discoverBrowseLoading && vm.visibleRecommendations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { KikoSectionLoading() }
            } else {
                itemsIndexed(vm.visibleRecommendations, key = { _, it -> it.id }) { index, item ->
                    StaggeredItem(index, staggerSeen) {
                        RecommendationGridCard(item, onOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type)
                    }
                }
            }
            if (!vm.discoverBrowseLoading && vm.visibleRecommendations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No recommendations yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center)
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { gridState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
        )
    }
}
// Recommendations / search-result grid tile — now just the Seasonal screen's tile
// (SeasonalGridCard) so every anime/manga grid in the app looks identical: 160dp cover, bold
// 2-line title, star + score row and members row (both "N/A" when missing). Kept as a wrapper so
// existing call sites don't change.
@Composable fun RecommendationGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, myStatus: WatchStatus? = null, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false) {
    SeasonalGridCard(item, onOpenDetail, onLongPress = onLongPress, isSelected = isSelected, myStatus = myStatus)
}

// Grid tile for Characters / People / Companies / Users search results, laid out like the
// Seasonal tile: 160dp cover, bold 2-line title (always reserved), then one muted info line that's
// also always reserved (even when blank) so every tile in a row has the same height.
@Composable fun EntityGridCard(title: String, subtitle: String, imageUrl: String, loading: Boolean = false, fit: Boolean = false, square: Boolean = false, onTap: () -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).kikoClickable(enabled = !loading, onClick = onTap)) {
        // square = true gives a 1:1 cover (used for Company logos); everything else keeps the 160dp-tall cover.
        Box((if (square) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.fillMaxWidth().height(160.dp)).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh)) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(model = imageUrl, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
            } else {
                Text(title.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = Color.White, size = 24.dp)
                }
            }
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(subtitle.ifBlank { " " }, color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

// Chunks results into 3-wide rows inside the screen's single LazyColumn (same approach the
// Anime/Manga grid uses), padding the last partial row so its tiles don't stretch.
private fun <T> LazyListScope.entityGridRows(items: List<T>, keyOf: (T) -> String, seen: MutableSet<Int>, card: @Composable (T) -> Unit) {
    val rows = items.chunked(3)
    itemsIndexed(rows, key = { i, row -> "grid_row_${row.firstOrNull()?.let(keyOf) ?: i}" }) { rowIndex, rowItems ->
        StaggeredItem(rowIndex, seen) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                rowItems.forEach { Box(Modifier.weight(1f)) { card(it) } }
                repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

// Forums tab structure