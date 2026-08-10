@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    LaunchedEffect(vm.signedIn) { vm.loadDiscoverBrowse(context) }
    AnimatedContent(
        vm.discoverMode,
        transitionSpec = { if (targetState == DiscoverMode.Results) PushEnter togetherWith PushExit else PopEnter togetherWith PopExit },
        label = "discover-mode",
    ) { mode ->
        if (mode == DiscoverMode.Results) DiscoverResultsScreen(vm, context, onOpenDetail, onExitResults, onEdit, selectedItem)
        else DiscoverBrowseScreen(vm, context, onOpenDetail, onRanking, onSeasonal, onStacks, onRecommendations, onEdit)
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
    onEdit: (MediaItem) -> Unit = {}
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
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                AppHeader("Discover", 0.dp) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
                Spacer(Modifier.height(17.dp))

                // Search bar and filter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onGloballyPositioned { searchBarBounds = it.boundsInRoot() },
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

                // Ranking and Seasonal share the top row; Interest Stacks gets its own
                // full-width button below, edge-to-edge within the screen's padding
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DiscoverActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TrendingUp,
                        label = "Rankings",
                        onClick = onRanking
                    )
                    DiscoverActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.DateRange,
                        label = "Seasonal",
                        onClick = onSeasonal
                    )
                }
                Spacer(Modifier.height(12.dp))
                DiscoverActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Layers,
                    label = "Interest Stacks",
                    onClick = onStacks
                )

                Spacer(Modifier.height(12.dp))
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
                if (vm.visibleDiscoverNewSeason.isNotEmpty()) {
                    item {
                        SectionTitle("New this season", "See all", onSeasonal)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            // Cap row at 7
                            items(vm.visibleDiscoverNewSeason.take(7), key = { it.id }) { item ->
                                BrowseCard(item, trackedOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit)
                            }
                        }
                    }
                }

                // Top 10 upcoming row
                if (vm.visibleDiscoverUpcoming.isNotEmpty()) {
                    item {
                        SectionTitle("Top 10 upcoming", "", {})
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            items(vm.visibleDiscoverUpcoming, key = { it.id }) { item ->
                                BrowseCard(item, trackedOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit)
                            }
                        }
                    }
                }

                // Recommendations row
                if (vm.visibleRecommendations.isNotEmpty()) {
                    item {
                        SectionTitle("You might like", "See more", onRecommendations)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            // Cap row at 7; full list is in the "See more" grid
                            items(vm.visibleRecommendations.take(7), key = { it.id }) { item ->
                                BrowseCard(item, trackedOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit)
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = modifier.clickable(onClick = onClick)
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
    // Restore results scroll position
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.discoverScrollIndex, initialFirstVisibleItemScrollOffset = vm.discoverScrollOffset)
    val openResult: (MediaItem) -> Unit = { result ->
        vm.saveDiscoverScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        vm.openDiscoverDetail(context, result, onOpenDetail)
    }
    // Long-press to edit — same fetch-full-detail-first step as tapping through,
    // since a bare search result row doesn't carry everything EditSheet wants.
    val editResult: (MediaItem) -> Unit = { result -> vm.openDiscoverDetail(context, result, onEdit) }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Bounds (in root coordinates) of the outer Box and the search row, used to float
    // the suggestions list directly under the search bar regardless of scroll position
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { containerBounds = it.boundsInRoot() }) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExitResults, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back to Discover", tint = c.ink) }
                    Text("Search results", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onGloballyPositioned { searchBarBounds = it.boundsInRoot() },
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
                        items(listOf("Anime", "Manga")) { label -> FilterChip(selected = vm.discoverTypeFilter == label, onClick = { vm.runDiscoverSearch(context, query, label) }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
                    }
                    DiscoverSortMenu(current = vm.discoverSort, onSelect = vm::selectDiscoverSort, modifier = Modifier.padding(start = 8.dp))
                }
                if (vm.discoverSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.discoverError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (!vm.discoverSearching && vm.visibleDiscoverResults.isEmpty() && vm.discoverError == null) {
                val emptyMessage = if (vm.discoverQuery.isBlank()) "No results match your filters." else "No results for \"${vm.discoverQuery}\"."
                item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
            }
            itemsIndexed(vm.visibleDiscoverResults, key = { _, it -> it.id }) { index, result ->
                SearchResultRow(result, loading = vm.discoverDetailLoadingId == result.id, onTap = { openResult(result) }, onLongPress = { editResult(result) }, isSelected = selectedItem?.id == result.id && selectedItem?.type == result.type)
                if (index < vm.visibleDiscoverResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.muted.copy(alpha = .15f))
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
        modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (active) c.primary else c.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Tune, "Advanced filters", tint = if (active) c.onPrimary else c.ink) }
}
// Collapsible multi-select facet

@Composable fun ExpandableFilterSection(title: String, options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    val c = LocalKikoColors.current
    var expanded by remember(title) { mutableStateOf(selected.isNotEmpty()) }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (selected.isNotEmpty()) {
                    Box(Modifier.padding(start = 8.dp).clip(CircleShape).background(c.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(selected.size.toString(), color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse $title" else "Expand $title", tint = c.muted)
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 9.dp)) {
                options.forEach { o -> FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(o) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
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
    // Skip partially-expanded sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
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
                formatOptions.forEach { f -> FilterChip(selected = format == f, onClick = { format = if (format == f) "" else f }, label = { Text(f) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }

            Text("Status", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                airingOptions.forEach { s -> FilterChip(selected = airingStatus == s, onClick = { airingStatus = if (airingStatus == s) "" else s }, label = { Text(s) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }

            // Anime searches by studio, manga by author — both stored in DiscoverFilters.creator.
            // When type is ambiguous ("All"), label it as both since either can match.
            val creatorLabel = when (type) { "Anime" -> "Studio"; "Manga" -> "Author"; else -> "Studio / Author" }
            val creatorHint = when (type) { "Anime" -> "e.g. Madhouse"; "Manga" -> "e.g. Eiichiro Oda"; else -> "e.g. Madhouse or Eiichiro Oda" }
            Text(creatorLabel, color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            OutlinedTextField(
                value = creator, onValueChange = { creator = it }, placeholder = { Text(creatorHint, color = c.muted) }, singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Source", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CommonSources) { s -> FilterChip(selected = source == s, onClick = { source = if (source == s) "" else s }, label = { Text(s) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Year", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 9.dp))
                    OutlinedTextField(
                        value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) }, placeholder = { Text("e.g. 2023", color = c.muted) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
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
                CommonRatings.forEach { r -> FilterChip(selected = rating == r, onClick = { rating = if (rating == r) "" else r }, label = { Text(r, maxLines = 1) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
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

@Composable fun BrowseCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, subtitle: String? = null, myStatus: WatchStatus? = null, onLongPress: ((MediaItem) -> Unit)? = null) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.width(118.dp).combinedClickable(
            onClick = { onOpenDetail(item) },
            onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
        )
    ) {
        Cover(item, Modifier.fillMaxWidth().height(150.dp), showStatus = true, overrideStatus = myStatus)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(subtitle ?: (if (item.score > 0) "★ ${"%.1f".format(item.score)}" else item.genre), color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
// Discover search result row

@Composable fun SearchResultRow(item: MediaItem, loading: Boolean, onTap: () -> Unit, onLongPress: (() -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "searchResultSelectBg")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .combinedClickable(
                enabled = !loading,
                onClick = onTap,
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit() } },
            )
            .padding(horizontal = if (isSelected) 10.dp else 0.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(84.dp).height(118.dp)) {
            Cover(item, Modifier.fillMaxSize(), showStatus = true, selected = isSelected)
            if (item.score > 0) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                    Text("%.2f".format(item.score), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.format.isNotBlank()) Pill(item.format, c.primaryContainer, c.primary)
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

@Composable fun RecommendationsScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit, onEdit: (MediaItem) -> Unit = {}) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.loadHomeExtras(context) }
    // Same id+type keyed status map used elsewhere so recs the user already tracks show their mark
    val myListStatus = remember(vm.items) { vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 600 } }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Text("You might like", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
                }
            }
            if (vm.discoverBrowseLoading && vm.visibleRecommendations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), color = c.primary, trackColor = c.surfaceLow)
                }
            }
            items(vm.visibleRecommendations, key = { it.id }) { item ->
                RecommendationGridCard(item, onOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it to item.type] }, onLongPress = onEdit)
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

@Composable fun RecommendationGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, myStatus: WatchStatus? = null, onLongPress: ((MediaItem) -> Unit)? = null) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { onOpenDetail(item) },
            onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
        )
    ) {
        Cover(item, Modifier.fillMaxWidth().aspectRatio(0.72f), showStatus = true, overrideStatus = myStatus)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        if (item.score > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                Text("%.2f".format(item.score), color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
            }
        } else if (item.genre.isNotBlank()) {
            Text(item.genre, color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// Forums tab structure