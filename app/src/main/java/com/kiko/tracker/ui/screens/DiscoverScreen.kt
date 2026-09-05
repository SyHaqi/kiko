@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
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
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.PersonSummary
import com.kiko.tracker.data.model.SeasonName
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.oneDecimal
import com.kiko.tracker.data.model.resolvedDiscoverType
import com.kiko.tracker.data.model.twoDecimals
import com.kiko.tracker.navigation.PopEnter
import com.kiko.tracker.navigation.PopExit
import com.kiko.tracker.navigation.PushEnter
import com.kiko.tracker.navigation.PushExit
import com.kiko.tracker.ui.components.AppHeader
import com.kiko.tracker.ui.components.Avatar
import com.kiko.tracker.ui.components.Cover
import com.kiko.tracker.ui.components.FloatingSearchSuggestions
import com.kiko.tracker.ui.components.Pill
import com.kiko.tracker.ui.components.SearchField
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.ListGridCardSkeleton
import com.kiko.tracker.ui.theme.ListRowSkeletonGroup
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
    onSeasonal: () -> Unit,
    onStacks: () -> Unit,
    onRecommendations: () -> Unit,
    onExitResults: () -> Unit = vm::exitDiscoverSearch,
    onEdit: (MediaItem) -> Unit = {},
    selectedItem: MediaItem? = null,
    onOpenCharacter: (Int) -> Unit = {},
    onOpenPerson: (Int) -> Unit = {},
    onOpenCompany: (Int) -> Unit = {},
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
        if (mode == DiscoverMode.Results) DiscoverResultsScreen(vm, context, onOpenDetail, onExitResults, onEdit, selectedItem, onOpenCharacter, onOpenPerson, onOpenCompany)
        else DiscoverBrowseScreen(vm, context, onOpenDetail, onRanking, onSeasonal, onStacks, onRecommendations, onEdit, selectedItem)
    }
}
// Discover landing page

@Composable fun DiscoverBrowseScreen(
    vm: LibraryViewModel,
    context: Context,
    onOpenDetail: (MediaItem) -> Unit,
    onRanking: () -> Unit,
    onSeasonal: () -> Unit,
    onStacks: () -> Unit,
    onRecommendations: () -> Unit,
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                // Search now lives on
                // DiscoverResultsScreen) instead of an
                // icon that jumps there,
                // part of the header
                AppHeader("Discover", 0.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Same pill search icon
                        // (43dp squircle, surfaceContainerHigh, kikoClickable)
                        // plain Material IconButton, so
                        Box(
                            Modifier
                                .size(43.dp)
                                .clip(RoundedCornerShape(kikoCorner(16.dp)))
                                .background(c.surfaceContainerHigh)
                                .kikoClickable { vm.openDiscoverSearch(context) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Search, "Search", tint = c.ink) }
                        Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect }
                    }
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
                val newSeason = vm.visibleDiscoverNewSeason.take(7)
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

                // Top 10 upcoming row
                val upcoming = vm.visibleDiscoverUpcoming
                if (upcoming.isNotEmpty()) {
                    item {
                        SectionTitle("Top 10 upcoming", "", {})
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
                        SectionTitle("Trending manga", "", {})
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
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            color = c.primary,
                            trackColor = c.surfaceLow
                        )
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
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

@Composable fun DiscoverResultsScreen(vm: LibraryViewModel, context: Context, onOpenDetail: (MediaItem) -> Unit, onExitResults: () -> Unit = vm::exitDiscoverSearch, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null, onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {}) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf(vm.discoverQuery) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = onExitResults)
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
    // Search results come straight
    // each result's baked-in status
    // update on its own
    // Same id+type keyed override
    // recomputed whenever vm.items changes
    val myListStatus = remember(vm.items) { vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap() }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Bounds (in root coordinates)
    // the suggestions list directly
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
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
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 1) vm.loadMoreDiscoverSearch(context) }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { containerBounds = it.boundsInRoot() }) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExitResults, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back to Discover", tint = c.ink) }
                    Text("Search & Discover", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(IntrinsicSize.Min).onGloballyPositioned { searchBarBounds = it.boundsInRoot() },
                ) {
                    Box(Modifier.weight(1f)) {
                        SearchField(
                            query,
                            { query = it; if (vm.discoverTypeFilter == "Anime" || vm.discoverTypeFilter == "Manga") vm.fetchDiscoverSuggestions(context, it, vm.discoverTypeFilter) else vm.clearDiscoverSuggestions() },
                            "Search in MAL",
                            onSearch = { vm.clearDiscoverSuggestions(); vm.selectDiscoverType(context, vm.discoverTypeFilter, query) },
                            focusRequester = searchFocusRequester,
                        )
                    }
                    // Advanced filters (format/genre/year/score) only
                    if (vm.discoverTypeFilter == "Anime" || vm.discoverTypeFilter == "Manga") {
                        FilterIconButton(active = vm.discoverFilters.isActive(), onClick = { filterSheetOpen = true; vm.prewarmGenreLookup() }, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                // Fixes type/format mismatch
                if (filterSheetOpen) AdvancedFilterSheet(vm.discoverFilters, type = vm.discoverTypeFilter, onDismiss = { filterSheetOpen = false }, onApply = { filterSheetOpen = false; vm.runDiscoverSearch(context, query, resolvedDiscoverType(it.format, vm.discoverTypeFilter), it) })

                Row(Modifier.fillMaxWidth().padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    DiscoverTypeDropdown(current = vm.discoverTypeFilter, onSelect = { picked -> vm.selectDiscoverType(context, picked, query) })
                    if (vm.discoverTypeFilter == "Anime" || vm.discoverTypeFilter == "Manga") {
                        DiscoverSortMenu(current = vm.discoverSort, onSelect = { vm.selectDiscoverSort(context, it) }, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                when (vm.discoverTypeFilter) {
                    "Characters" -> {
                        if (vm.characterSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                        vm.characterError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                    }
                    "People" -> {
                        if (vm.personSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                        vm.personError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                    }
                    "Companies" -> {
                        if (vm.companySearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                        vm.companyError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                    }
                    "Anime", "Manga" -> {
                        if (vm.discoverSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
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
                        item { ListRowSkeletonGroup(6) }
                    } else {
                        itemsIndexed(charResults, key = { _, it -> "char_${it.malId}" }) { index, result ->
                            StaggeredItem(index, staggerSeen) {
                                Column {
                                    CharacterSearchResultRow(result, loading = vm.characterDetailLoadingId == result.malId, onTap = { openCharacterResult(result) })
                                    if (index < charResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
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
                        item { ListRowSkeletonGroup(6) }
                    } else {
                        itemsIndexed(peopleResults, key = { _, it -> "person_${it.malId}" }) { index, result ->
                            StaggeredItem(index, staggerSeen) {
                                Column {
                                    PersonSearchResultRow(result, loading = vm.personDetailLoadingId == result.malId, onTap = { openPersonResult(result) })
                                    if (index < peopleResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
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
                        item { ListRowSkeletonGroup(6) }
                    } else {
                        itemsIndexed(companyResultsList, key = { _, it -> "company_${it.malId}" }) { index, result ->
                            StaggeredItem(index, staggerSeen) {
                                Column {
                                    CompanySearchResultRow(result, loading = vm.companyDetailLoadingId == result.malId, onTap = { openCompanyResult(result) })
                                    if (index < companyResultsList.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                                }
                            }
                        }
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
                        item { ListRowSkeletonGroup(6) }
                    } else {
                        itemsIndexed(resultsForList, key = { _, it -> "${it.id}_${it.type}" }) { index, result ->
                            StaggeredItem(index, staggerSeen) {
                                Column {
                                    SearchResultRow(result, loading = vm.discoverDetailLoadingId == result.id, onTap = { openResult(result) }, onLongPress = { editResult(result) }, isSelected = selectedItem?.id == result.id && selectedItem?.type == result.type, myStatus = result.id.toIntOrNull()?.let { myListStatus[it to result.type] })
                                    if (index < resultsForList.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (vm.discoverLoadingMore) {
                item { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) } }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
        // Floating title suggestions as
        // run that search; tapping
        FloatingSearchSuggestions(
            anchorBounds = searchBarBounds,
            containerBounds = containerBounds,
            suggestions = if (query.isNotBlank()) vm.discoverSuggestions else emptyList(),
            onDismiss = vm::clearDiscoverSuggestions,
        ) { picked ->
            query = picked
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

@Composable fun ExpandableFilterSection(title: String, options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    val c = LocalKikoColors.current
    var expanded by remember(title) { mutableStateOf(selected.isNotEmpty()) }
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

@Composable fun AdvancedFilterSheet(current: DiscoverFilters, type: String, onDismiss: () -> Unit, onApply: (DiscoverFilters) -> Unit) {
    val c = LocalKikoColors.current
    // Split combined genre facets
    var genres by remember { mutableStateOf(current.genres.filter { it !in CommonExplicitGenres }.toSet()) }
    var explicitGenres by remember { mutableStateOf(current.genres.filter { it in CommonExplicitGenres }.toSet()) }
    var themes by remember { mutableStateOf(current.themes) }
    var demographics by remember { mutableStateOf(current.demographics) }
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

            ExpandableFilterSection("Genre", CommonGenres, genres, onToggle = { g -> genres = if (g in genres) genres - g else genres + g })
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
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "browseCardSelectBg")
    val pad by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "browseCardSelectPad")
    Column(
        Modifier
            .width(118.dp)
            .clip(RoundedCornerShape(kikoCorner(14.dp)))
            .background(bg)
            .kikoCombinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
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
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(subtitle ?: (if (item.score > 0) "★ ${item.score.oneDecimal()}" else item.genre), color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
// Discover search result row

@Composable fun SearchResultRow(item: MediaItem, loading: Boolean, onTap: () -> Unit, onLongPress: (() -> Unit)? = null, isSelected: Boolean = false, myStatus: WatchStatus? = null) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "searchResultSelectBg")
    val hPad by animateDpAsState(if (isSelected) 10.dp else 0.dp, label = "searchResultSelectPad")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(16.dp)))
            .background(bg)
            .kikoCombinedClickable(
                enabled = !loading,
                onClick = onTap,
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit() } },
            )
            .padding(horizontal = hPad, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Matches ListRow's cover size
        // smaller than every other
        Box(Modifier.width(92.dp).height(128.dp)) {
            Cover(item, Modifier.fillMaxSize(), showStatus = true, overrideStatus = myStatus, selected = isSelected)
            if (item.score > 0) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(kikoCorner(8.dp))).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                    Text(item.score.twoDecimals(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.format.isNotBlank()) Pill(item.format, c.primaryContainer, c.onPrimaryContainer)
                episodeAndYear(item).takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 9.dp))
                }
            }
            if (item.listUsers > 0) {
                Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Group, null, tint = c.muted, modifier = Modifier.size(13.dp))
                    Text(formatExact(item.listUsers), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
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
    val options = listOf("Anime", "Manga", "Characters", "Companies", "People")
    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(current) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp)) },
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(enabled = !loading, onClick = onTap).padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(92.dp).height(128.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (entry.image.isNotBlank()) {
                AsyncImage(model = entry.image, contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(entry.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(enabled = !loading, onClick = onTap).padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(92.dp).height(128.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (entry.image.isNotBlank()) {
                AsyncImage(model = entry.image, contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(entry.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(enabled = !loading, onClick = onTap).padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(92.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (entry.image.isNotBlank()) {
                AsyncImage(model = entry.image, contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text(entry.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Text("You might like", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
                }
            }
            if (vm.discoverBrowseLoading && vm.visibleRecommendations.isEmpty()) {
                items(9) { i -> StaggeredItem(i) { ListGridCardSkeleton() } }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// Recommendations grid tile —

@Composable fun RecommendationGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, myStatus: WatchStatus? = null, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "recommendationSelectBg")
    val pad by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "recommendationSelectPad")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(14.dp)))
            .background(bg)
            .kikoCombinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
            )
            // animateDpAsState on `pad` above
            // value frame-by-frame, so the
            // animateContentSize() here was a
            // wrapping every card in
            .padding(pad)
    ) {
        // Height matches ListGridCardSkeleton's cover
        // real card don't jump
        Cover(item, Modifier.fillMaxWidth().height(160.dp), showStatus = true, overrideStatus = myStatus, selected = isSelected)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        if (item.score > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                Text(item.score.twoDecimals(), color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
            }
        } else if (item.genre.isNotBlank()) {
            Text(item.genre, color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// Forums tab structure