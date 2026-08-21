@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker

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

@Composable fun DiscoverScreen(
    vm: LibraryViewModel,
    onOpenDetail: (MediaItem) -> Unit,
    onRanking: () -> Unit,
    onSeasonal: () -> Unit,
    onStacks: () -> Unit,
    onRecommendations: () -> Unit,
    onExitResults: () -> Unit = vm::exitDiscoverSearch,
    onEdit: (MediaItem) -> Unit = {},
    selectedItem: MediaItem? = null
) {
    val context = LocalContext.current
    // loadHomeExtras() populates both trendingManga (this row) and recommendations
    // ("You might like") — it was previously only triggered from RecommendationsScreen's
    // own LaunchedEffect, so neither row ever had data on the Discover landing page until
    // you'd already opened "You might like" once that session. Load it alongside
    // loadDiscoverBrowse() so both rows are populated from the moment Discover opens.
    LaunchedEffect(vm.signedIn) { vm.loadDiscoverBrowse(context); vm.loadHomeExtras(context) }
    AnimatedContent(
        vm.discoverMode,
        transitionSpec = { if (targetState == DiscoverMode.Results) PushEnter togetherWith PushExit else PopEnter togetherWith PopExit },
        label = "discover-mode",
    ) { mode ->
        if (mode == DiscoverMode.Results) DiscoverResultsScreen(vm, context, onOpenDetail, onExitResults, onEdit, selectedItem)
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
    var query by remember { mutableStateOf("") }
    var filterSheetOpen by remember { mutableStateOf(false) }
    // Map (MAL id, type) -> the user's tracked status, so browse rows (which come straight from
    // Tenrai/MAL search results, not the user's own list) can still show the status badge.
    // Keyed by id+type since anime and manga IDs are independent and can collide.
    val myListStatus = remember(vm.items) { vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap() }
    // Restore scroll position on return from a card/entry instead of resetting to top
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.discoverBrowseScrollIndex, initialFirstVisibleItemScrollOffset = vm.discoverBrowseScrollOffset)
    val trackedOpenDetail: (MediaItem) -> Unit = remember(onOpenDetail) {
        { item -> vm.saveDiscoverBrowseScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onOpenDetail(item) }
    }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Bounds (in root coordinates) of the outer Box and the search row, used to float
    // the suggestions list directly under the search bar regardless of scroll position
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { containerBounds = it.boundsInRoot() }) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                AppHeader("Discover", 0.dp) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
                Spacer(Modifier.height(17.dp))

                // Search bar and filter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(IntrinsicSize.Min).onGloballyPositioned { searchBarBounds = it.boundsInRoot() },
                ) {
                    Box(Modifier.weight(1f)) {
                        SearchField(
                            value = query,
                            change = { query = it; vm.fetchDiscoverSuggestions(context, it, vm.discoverTypeFilter) },
                            hint = "Search in MAL",
                            onSearch = {
                                vm.clearDiscoverSuggestions()
                                if (query.isNotBlank() || vm.discoverFilters.isActive()) vm.runDiscoverSearch(context, query, vm.discoverTypeFilter)
                            }
                        )
                    }
                    FilterIconButton(active = vm.discoverFilters.isActive(), onClick = { filterSheetOpen = true }, modifier = Modifier.padding(start = 10.dp))
                }
                if (filterSheetOpen) AdvancedFilterSheet(vm.discoverFilters, type = "All", onDismiss = { filterSheetOpen = false }, onApply = { filterSheetOpen = false; vm.runDiscoverSearch(context, query, resolvedDiscoverType(it.format, vm.discoverTypeFilter), it) })

                Spacer(Modifier.height(14.dp))

                // Seasonal now lives in its own bottom tab, to the right of Discover;
                // Rankings and Interest Stacks keep their buttons here, side by side,
                // edge-to-edge within the screen's padding
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
                // Read each vm.visible* list once into a local val instead of once per usage
                // (the isNotEmpty() check and the itemsIndexed call below it) — vm.visible*
                // is a cached (derivedStateOf-backed) getter so a second read is cheap, but
                // .take(n) on it still allocates a fresh list each time it's called, so one
                // read+take per row beats two.
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
                            // Cap row at 7; full list is in the "See more" grid
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
        // Floating title suggestions as the user types — tap to fill the search bar and
        // run that search; tapping anywhere outside dismisses it and drops focus
        FloatingSearchSuggestions(
            anchorBounds = searchBarBounds,
            containerBounds = containerBounds,
            suggestions = if (query.isNotBlank()) vm.discoverSuggestions else emptyList(),
            onDismiss = vm::clearDiscoverSuggestions,
        ) { picked ->
            query = picked
            vm.runDiscoverSearch(context, picked, vm.discoverTypeFilter)
        }
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
    // Same fix as AiringNextCard: use the clickable Card(onClick=) overload instead of
    // our own .kikoClickable on the passed-in modifier, so the press ripple/scale stays
    // clipped to the card's rounded shape instead of showing as a square hint.
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

// Interest Stacks homepage — curated Challenge/Manga/Anime picks up top, Recent Interest Stacks below.
// Greets the user when they tap the Stacks button, mirroring myanimelist.net/stacks.

@Composable fun DiscoverResultsScreen(vm: LibraryViewModel, context: Context, onOpenDetail: (MediaItem) -> Unit, onExitResults: () -> Unit = vm::exitDiscoverSearch, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null) {
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
    // Long-press to edit — same fetch-full-detail-first step as tapping through,
    // since a bare search result row doesn't carry everything EditSheet wants.
    val editResult: (MediaItem) -> Unit = { result -> vm.openDiscoverDetail(context, result, onEdit) }
    // Search results come straight from the MAL search endpoint, not the user's own list, so
    // each result's baked-in status is just a snapshot from when the search ran — it doesn't
    // update on its own after tracking/editing a title from its detail page and coming back.
    // Same id+type keyed override used for the Discover browse rows/recommendations below,
    // recomputed whenever vm.items changes so the badge here stays live too.
    val myListStatus = remember(vm.items) { vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap() }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Bounds (in root coordinates) of the outer Box and the search row, used to float
    // the suggestions list directly under the search bar regardless of scroll position
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    // Load the next page only once the person has actually scrolled to the true end of what's
    // currently loaded (not a few items early) — that way there's no result-list mutation while
    // a fling is still carrying them past earlier items, and the spinner row is what they land on
    // before the next page's items ever appear, rather than more being fetched invisibly ahead of
    // where they are. Still only fires when results are really still paginated server-side (see
    // DiscoverPaginationSource/discoverHasMore) — for studio/author, multi-tag, and ranking-chart
    // results the list has already ended, so this stays a no-op there.
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
                    Text("Search results", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(IntrinsicSize.Min).onGloballyPositioned { searchBarBounds = it.boundsInRoot() },
                ) {
                    Box(Modifier.weight(1f)) {
                        SearchField(
                            query,
                            { query = it; vm.fetchDiscoverSuggestions(context, it, vm.discoverTypeFilter) },
                            "Search in MAL",
                            onSearch = { vm.clearDiscoverSuggestions(); vm.runDiscoverSearch(context, query, vm.discoverTypeFilter) }
                        )
                    }
                    FilterIconButton(active = vm.discoverFilters.isActive(), onClick = { filterSheetOpen = true }, modifier = Modifier.padding(start = 10.dp))
                }
                // Fixes type/format mismatch
                if (filterSheetOpen) AdvancedFilterSheet(vm.discoverFilters, type = vm.discoverTypeFilter, onDismiss = { filterSheetOpen = false }, onApply = { filterSheetOpen = false; vm.runDiscoverSearch(context, query, resolvedDiscoverType(it.format, vm.discoverTypeFilter), it) })

                Row(Modifier.fillMaxWidth().padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(listOf("Anime", "Manga")) { label -> FilterChip(selected = vm.discoverTypeFilter == label, onClick = { vm.runDiscoverSearch(context, query, label) }, label = { Text(label) }, colors = kikoFilterChipColors()) }
                    }
                    DiscoverSortMenu(current = vm.discoverSort, onSelect = { vm.selectDiscoverSort(context, it) }, modifier = Modifier.padding(start = 8.dp))
                }
                if (vm.discoverSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.discoverError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (!vm.discoverSearching && vm.visibleDiscoverResults.isEmpty() && vm.discoverError == null) {
                val emptyMessage = if (vm.discoverQuery.isBlank()) "No results match your filters." else "No results for \"${vm.discoverQuery}\"."
                item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
            }
            // Keyed by id+type together, not id alone — anime and manga are separate MAL id
            // spaces, so a search that mixes both kinds (e.g. "stein" matching an anime and
            // an unrelated manga with the same numeric id) can produce two rows with the same
            // id. LazyColumn requires unique keys; a collision here throws mid-scroll/fling
            // ("Key ... was already used"), which is what the crash while scrolling was.
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
            if (vm.discoverLoadingMore) {
                item { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) } }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
        // Floating title suggestions as the user types — tap to fill the search bar and
        // run that search; tapping anywhere outside dismisses it and drops focus
        FloatingSearchSuggestions(
            anchorBounds = searchBarBounds,
            containerBounds = containerBounds,
            suggestions = if (query.isNotBlank()) vm.discoverSuggestions else emptyList(),
            onDismiss = vm::clearDiscoverSuggestions,
        ) { picked ->
            query = picked
            vm.runDiscoverSearch(context, picked, vm.discoverTypeFilter)
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
    // Opens half-screen (partially expanded); drag up to go full screen, drag down
    // to return to half screen, drag down again to dismiss — skipPartiallyExpanded
    // left off so the sheet keeps its natural partial/full/dismiss states.
    val sheetState = rememberModalBottomSheetState()
    // contentWindowInsets = {} decouples the sheet container from the IME so it doesn't
    // resize/glitch when the keyboard opens/closes (e.g. typing in Creator or Year);
    // imePadding() below pushes the content up above the keyboard instead.
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

            // Anime searches by studio, manga by author — both stored in DiscoverFilters.creator.
            // When type is ambiguous ("All"), label it as both since either can match.
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
            // animateDpAsState on `pad` above already smoothly interpolates the padding
            // value frame-by-frame, so the container's size change is already gradual —
            // animateContentSize() here was doing a second, redundant size-diff/measure
            // pass on top of that for every card, every frame the selection state was
            // transitioning, purely to re-derive an animation the padding was already driving.
            .padding(pad)
    ) {
        Cover(item, Modifier.fillMaxWidth().height(150.dp), showStatus = true, overrideStatus = myStatus, selected = isSelected)
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
        Box(Modifier.width(84.dp).height(118.dp)) {
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

fun episodeAndYear(item: MediaItem): String {
    val unit = if (item.type == MediaType.Anime) "ep" else "ch"
    val episodes = if (item.total > 0) "${item.total} $unit" else null
    val year = seasonYear(item.season, item.startDate).takeIf { it.isNotBlank() }
    return listOfNotNull(episodes, year).joinToString(", ")
}
// Comma-format member count

fun formatExact(n: Int): String = "%,d".format(n)

// "You might like" — full grid of recommendations, with the user's status mark on each cover

@Composable fun RecommendationsScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.loadHomeExtras(context) }
    // Same id+type keyed status map used elsewhere so recs the user already tracks show their mark
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
// Recommendations grid tile — mirrors SeasonalGridCard but marks the user's tracked status

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
            // animateDpAsState on `pad` above already smoothly interpolates the padding
            // value frame-by-frame, so the container's size change is already gradual —
            // animateContentSize() here was a second, redundant size-diff/measure pass
            // wrapping every card in the grid. Same fix as BrowseCard above.
            .padding(pad)
    ) {
        // Height matches ListGridCardSkeleton's cover block so the loading state and the
        // real card don't jump in size once results arrive.
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