@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable fun HomeScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, onList: () -> Unit, onLocateInList: (MediaItem) -> Unit, onDiscover: () -> Unit, onRanking: () -> Unit, onSeasonal: () -> Unit, onSchedule: (java.time.DayOfWeek) -> Unit, onOpenTopic: (Int, String) -> Unit, onSeeNews: () -> Unit, onOpenStack: (Int, String) -> Unit, onOpenStacks: () -> Unit, onSignIn: () -> Unit, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    LaunchedEffect(vm.signedIn) { vm.loadNewsSnapshots(context); vm.loadHomeLatestStack(context) }
    // Was recomputing (filter + maxByOrNull) over the whole library on every recomposition —
    // including ones triggered by unrelated state like vm.loading toggling during a
    // background sync — instead of only when the inputs that actually affect the result
    // change. Same remember(...) pattern ListScreen already uses for its filtered/sorted list.
    val items = remember(vm.items, vm.nsfwEnabled) { vm.visibleItems }
    val active = remember(items) {
        // Most recently updated wins
        items.filter { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }.maxByOrNull { it.updatedAt }
            ?: items.firstOrNull { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }
            ?: items.firstOrNull()
    }
    val today = java.time.LocalDate.now().dayOfWeek
    // Airing-next row pool — same remember(...) reasoning as items/active above: this was
    // re-filtering, re-parsing dates on, and re-sorting the full (up to 100-item) new-season
    // list on every recomposition, including ones with nothing to do with it (e.g. vm.loading
    // toggling elsewhere on the page).
    val newSeason = vm.visibleDiscoverNewSeason
    val airingNext = remember(newSeason) {
        newSeason.mapNotNull { item -> item.nextAirDateTime()?.let { item to it } }.sortedBy { it.second }.take(5).map { it.first }
    }
    // Restore scroll position on return from a card/entry instead of resetting to top
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.homeScrollIndex, initialFirstVisibleItemScrollOffset = vm.homeScrollOffset)
    // Persist scroll position whenever Home leaves composition, for any reason — opening a
    // detail/topic/stack, tapping "See news"/"See all", or just switching bottom-nav tabs.
    // Saving only at a few specific click sites (as before) missed some exits entirely (e.g.
    // "See news" never saved at all) and, more importantly, missed switching tabs — so scrolling
    // back to the top and then leaving via the bottom nav would restore the last *saved* position
    // instead of top, since nothing updated the saved value in between. Disposal is the one point
    // every exit path shares, so saving there covers all of them regardless of how Home was left.
    DisposableEffect(Unit) {
        onDispose { vm.saveHomeScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    val trackedOpenDetail: (MediaItem) -> Unit = onOpenDetail
    val trackedOpenTopic: (Int, String) -> Unit = onOpenTopic
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    PullToRefreshBox(
        isRefreshing = vm.loading,
        onRefresh = { vm.load(context); vm.loadNewsSnapshots(context, force = true); vm.loadHomeLatestStack(context, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                AppHeader("kiko") { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    // Use device current date
                    Text(
                        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.getDefault())).uppercase(java.util.Locale.getDefault()),
                        color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp,
                    )
                    if (airingNext.isNotEmpty()) {
                        SectionTitle("Airing next", "See all", click = { onSchedule(today) })
                        AiringNextRow(airingNext, vm, trackedOpenDetail)
                    } else if (vm.discoverBrowseLoading) {
                        SectionTitle("Airing next", "See all", click = { onSchedule(today) })
                        AiringNextRowSkeleton()
                    }
                    // Most recently updated in-progress title
                    if (active != null) {
                        SectionTitle("Continue", "See list", onList)
                        ContinueCard(active, vm, onClick = { onLocateInList(active) }, onLongPress = onEdit, isSelected = selectedItem?.id == active.id && selectedItem?.type == active.type)
                    } else if (vm.loading) {
                        SectionTitle("Continue", "See list", onList)
                        ContinueCardSkeleton()
                    }
                    // Home recent news row
                    if (vm.newsSnapshots.isNotEmpty()) {
                        SectionTitle("Snapshots", "See news", onSeeNews)
                        // Extra breathing room here specifically — the Pinterest-style grid
                        // reads as more "content-dense" than the single-row shelves above it,
                        // so it wants a bit more separation from the title than SectionTitle's
                        // default bottom padding gives the other sections.
                        Spacer(Modifier.height(8.dp))
                        SnapshotsGrid(vm.newsSnapshots, trackedOpenTopic)
                    } else if (vm.newsSnapshotsLoading) {
                        SectionTitle("Snapshots", "See news", onSeeNews)
                        Spacer(Modifier.height(8.dp))
                        SnapshotsGridSkeleton()
                    }
                    // Freshest Interest Stack teaser
                    vm.homeLatestStack?.let { stack ->
                        SectionTitle("Interest Stacks", "See all", onOpenStacks)
                        StackFeaturedCard(stack, vm) { onOpenStack(stack.id, stack.title) }
                    }
                    if (vm.authChecked && !vm.signedIn && !vm.loading) {
                        Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Please sign in with your MyAnimeList account", color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) { Text("Sign in with MyAnimeList") }
                        }
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
// Airing next row order

@Composable fun AiringNextRow(items: List<MediaItem>, vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit) {
    // Each card takes almost the full row width — same footprint as ContinueCard below it —
    // with just a thin sliver of the next card peeking in from the edge as the only hint
    // that the row scrolls. fillParentMaxWidth (not a fixed dp width) is what keeps that
    // sliver proportional to the screen instead of a fixed peek that's too fat on wide
    // screens or invisible on narrow ones.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { items(items, key = { it.id }) { AiringNextCard(it, vm, onOpenDetail, modifier = Modifier.fillParentMaxWidth(0.94f)) } }
}
// Airing next card layout — sized to match ContinueCard (same cover size, corner
// radius, and padding) so the two shelves read as the same kind of card.

@Composable fun AiringNextCard(item: MediaItem, vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val is24Hour = systemIs24Hour()
    // Best-effort AniList lookup for the real next-episode number + air time (see
    // LibraryViewModel.loadAiringEpisode) — fires once per id per session and just leaves the
    // date-math guess on screen until/unless it resolves.
    LaunchedEffect(item.id) { vm.loadAiringEpisode(item) }
    val confirmed = vm.getCachedAiring(item.id)
    val time = item.nextAirDateTime(confirmed)?.toLocalTime()
    Box(
        modifier
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surfaceContainer)
            .kikoClickable { onOpenDetail(item) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // overrideStatus: airingNext is discoverNewSeason data (raw seasonal API results,
            // never merged with the library — see LibraryViewModel.itemsByKey), so a live O(1)
            // lookup here is what makes the status badge appear immediately after tracking it
            // and disappear immediately after untracking/deleting it, instead of only updating
            // whenever this row happens to refetch.
            Cover(item, Modifier.size(width = 84.dp, height = 118.dp), showStatus = true, overrideStatus = vm.trackedStatus(item))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(item.displayTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.genre.isNotBlank()) {
                    Text(item.genre, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.height(8.dp))
                // Full card width now, so the episode + air-time label has room to sit on
                // one line instead of wrapping the way it did in the old narrow card.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = c.accent, modifier = Modifier.size(13.dp))
                    Text(
                        listOfNotNull(item.nextEpisodeLabel(confirmed), time?.let { localizedTimeLabel(it, is24Hour) }).joinToString(" · "),
                        color = c.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}
// Reusable rounded pill button

@Composable fun HomeActionButton(modifier: Modifier = Modifier, label: String, icon: ImageVector, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        modifier.clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.primaryContainer).kikoClickable(onClick = onClick).padding(vertical = 15.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.onPrimaryContainer, modifier = Modifier.size(19.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.onPrimaryContainer, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun SectionTitle(title: String, action: String, click: () -> Unit, icon: ImageVector = Icons.Default.ArrowForward) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.ink)
        // Rounded-square affordance instead of a text link — same pattern the Play Store
        // uses on its section headers ("Dating apps" -> arrow button), but shaped like a
        // squircle rather than a full circle, since nothing else in the app is fully
        // round (chips, cards, and buttons all use kikoCorner's rounded-rectangle
        // language). Sections with nothing to link to (action left blank) render no button.
        if (action.isNotBlank()) {
            // Plain clickable box instead of IconButton — IconButton carries its own
            // default 40dp touch-target sizing internally, which is extra machinery this
            // small a button doesn't need. Sizing this by hand means what's written here
            // is exactly what renders, full stop.
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(kikoCorner(10.dp)))
                    .background(c.surfaceContainerHigh)
                    .kikoClickable(onClick = click),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, action, tint = c.ink, modifier = Modifier.size(17.dp)) }
        }
    }
}

// Home's "Continue" entry, now boxed the same way as the other card-style shelves in
// the app (see StackFeaturedCard) — tonal surfaceContainer fill + rounded corners —
// instead of sitting as a bare, unboxed row. Still just wraps [ListRow]'s content;
// only the surrounding container changed. Tapping it jumps to the entry's spot in My
// List rather than opening its detail page — "Continue" is meant as a shortcut back
// into the list, not a detail-page shortcut.

@Composable fun ContinueCard(item: MediaItem, vm: LibraryViewModel, onClick: (MediaItem) -> Unit, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surfaceContainer),
    ) {
        ListRow(item, onClick, showType = false, onLongPress = onLongPress, isSelected = isSelected, showChevron = true, modifier = Modifier.padding(horizontal = 14.dp), vm = vm)
    }
}
// Pinterest-style snapshots layout

@Composable fun SnapshotsGrid(snapshots: List<NewsSnapshot>, onOpenTopic: (Int, String) -> Unit) {
    val left = snapshots.filterIndexed { i, _ -> i % 2 == 0 }
    val right = snapshots.filterIndexed { i, _ -> i % 2 == 1 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            left.forEachIndexed { i, s -> SnapshotCard(s, tall = i % 2 == 0, onOpenTopic) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            right.forEachIndexed { i, s -> SnapshotCard(s, tall = i % 2 == 1, onOpenTopic) }
        }
    }
}
// Snapshot card title overlay

@Composable fun SnapshotCard(snapshot: NewsSnapshot, tall: Boolean, onOpenTopic: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (tall) 210.dp else 160.dp)
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .kikoClickable { onOpenTopic(snapshot.topicId, snapshot.title) },
    ) {
        AsyncImage(model = snapshot.imageUrl, contentDescription = snapshot.title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = .35f),
                            1f to Color.Black.copy(alpha = .92f),
                        ),
                    ),
                ),
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                snapshot.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(shadow = Shadow(color = Color.Black.copy(alpha = .8f), offset = Offset(0f, 1f), blurRadius = 4f)),
            )
        }
    }
}

fun progressLabel(i: MediaItem) = if (i.progress == 0) i.status.displayLabel(i.type) else "${i.progress} of ${if (i.total > 0) i.total.toString() else "?"} ${if (i.type == MediaType.Anime) "episodes" else "chapters"}"
// Same as progressLabel, but with "episodes"/"chapters" shortened to "ep."/"ch." — used only in
// the grid tile, where the card is too narrow to reliably fit the full word at 10sp.
fun compactProgressLabel(i: MediaItem) = if (i.progress == 0) i.status.displayLabel(i.type) else "${i.progress} of ${if (i.total > 0) i.total.toString() else "?"} ${if (i.type == MediaType.Anime) "ep." else "ch."}"
// Format field fallback

fun formatLabel(i: MediaItem): String = i.format.ifBlank { if (i.type == MediaType.Anime) "Anime" else "Manga" }

// Translate status label

fun normalizeFilterForType(filter: String, type: MediaType): String = when (filter) {
    "Watching", "Reading" -> if (type == MediaType.Anime) "Watching" else "Reading"
    "Plan to Watch", "Plan to Read" -> if (type == MediaType.Anime) "Plan to Watch" else "Plan to Read"
    else -> filter
}
// My List sort logic

fun MediaItem.resolvedTitle(pref: TitleLanguage): String =
    if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) titleEnglish else title

fun List<MediaItem>.sortedWithListSort(sort: ListSort, titleLanguage: TitleLanguage): List<MediaItem> = when (sort) {
    ListSort.Title -> sortedBy { it.resolvedTitle(titleLanguage).lowercase() }
    ListSort.Score -> sortedWith(compareByDescending<MediaItem> { it.myRating > 0 }.thenByDescending { it.myRating })
    ListSort.LastUpdated -> sortedWith(compareByDescending<MediaItem> { it.updatedAt.isNotBlank() }.thenByDescending { it.updatedAt })
    ListSort.StartDate -> sortedWith(compareByDescending<MediaItem> { it.watchStartDate.isNotBlank() }.thenByDescending { it.watchStartDate })
}
// Compact sort dropdown

@Composable fun SortMenu(current: ListSort, onSelect: (ListSort) -> Unit) {
    val c = LocalKikoColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHigh).kikoClickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sort, "Sort", tint = c.accent, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceContainerHigh, shape = RoundedCornerShape(kikoCorner(18.dp))) {
            ListSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.accent else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(s); open = false },
                )
            }
        }
    }
}
// Discover results sort dropdown

@Composable fun DiscoverSortMenu(current: DiscoverSort, onSelect: (DiscoverSort) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHigh).kikoClickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sort, "Sort", tint = c.accent, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceContainerHigh, shape = RoundedCornerShape(kikoCorner(18.dp))) {
            DiscoverSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.accent else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(s); open = false },
                )
            }
        }
    }
}


@Composable fun ListScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, onIncrement: (MediaItem) -> Unit, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Search only on submit
    var submittedQuery by remember { mutableStateOf("") }
    // Search bar starts collapsed into an icon beside the avatar; expanding it takes over
    // the whole header row (see ExpandableSearchHeader).
    var searchExpanded by remember { mutableStateOf(false) }
    val typeTab = vm.listTypeTab
    val effectiveFilter = normalizeFilterForType(vm.listFilter, typeTab)
    // Was recomputing filter+sort over the whole list on every recomposition — including
    // ones triggered by unrelated state like vm.loading toggling during a background sync
    // — instead of only when the inputs that actually affect the result change. Same
    // remember(...) pattern ScoreFilterScreen/YearFilterScreen already use below.
    val filtered = remember(vm.items, vm.nsfwEnabled, typeTab, effectiveFilter, submittedQuery, vm.listSort, vm.titleLanguage) {
        vm.visibleItems
            .filter { it.type == typeTab && (effectiveFilter == "All" || it.status.displayLabel(typeTab) == effectiveFilter) && it.title.contains(submittedQuery, true) }
            .sortedWithListSort(vm.listSort, vm.titleLanguage)
    }
    // Status filter now lives in the bottom-right FAB (see StatusFilterFab) instead of the
    // old FilterRow chip row, so its open/closed state is hoisted up here.
    var filterMenuOpen by remember { mutableStateOf(false) }
    val isGrid = vm.listViewMode == ListViewMode.Grid
    // Shared between grid and list mode (both walk the same `filtered` index order) so an
    // index that's already played its entrance in one view mode doesn't replay it after
    // switching to the other, and so scrolling back up doesn't replay it at all.
    val staggerSeen = rememberStaggerMemory()
    // Restore list scroll position (shared between list/grid since both are single-column-index scroll states)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.listScrollIndex, initialFirstVisibleItemScrollOffset = vm.listScrollOffset)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = vm.listScrollIndex, initialFirstVisibleItemScrollOffset = vm.listScrollOffset)
    val openItem: (MediaItem) -> Unit = remember(onOpenDetail, isGrid) {
        {
                item ->
            if (isGrid) vm.saveListScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            else vm.saveListScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            onOpenDetail(item)
        }
    }
    val header: @Composable () -> Unit = {
        // Type switcher lives in the header itself now (tap the title to open Anime/Manga menu)
        // instead of a separate full-width toggle row underneath — saves vertical space. The
        // search icon sits just left of the avatar and expands edge-to-edge over this whole
        // row when tapped, hiding the title/avatar rather than squeezing in beside them.
        ExpandableSearchHeader(
            current = typeTab,
            options = MediaType.entries.toList(),
            labelFor = { if (it == MediaType.Anime) "Anime" else "Manga" },
            onSelect = { vm.selectListTypeTab(context, it) },
            query = query,
            onQueryChange = { query = it },
            onSearch = { submittedQuery = query },
            onClear = { query = ""; submittedQuery = "" },
            expanded = searchExpanded,
            onExpandedChange = { expanded -> searchExpanded = expanded; if (!expanded) { query = ""; submittedQuery = "" } },
            hint = "Search your list",
            horizontalPadding = 0.dp,
            switchDescription = "Switch between Anime and Manga",
        ) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
        if (vm.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), color = c.accent, trackColor = c.surfaceLow)
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} titles" + if (vm.loading) " · syncing…" else "", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.listViewMode) { vm.setListViewMode(context, it) }
                SortMenu(vm.listSort) { vm.setListSort(context, it) }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { if (isGrid) gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 600 else listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Go-to-top (left) and the filter FAB (right) now sit in opposite corners instead of
    // stacking, so a flat inset covers either one.
    val bottomInset = 90.dp
    PullToRefreshBox(isRefreshing = vm.loading, onRefresh = { vm.load(context) }, modifier = Modifier.fillMaxSize()) {
        // Basic cross-fade when switching between grid and list layouts, matching the tab-switch
        // transition used elsewhere in the app (e.g. Clubs tabs, Profile stats)
        AnimatedContent(
            isGrid,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "list-view-mode",
        ) { grid ->
            if (grid) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomInset),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
                    if (vm.loading && filtered.isEmpty()) {
                        items(9) { i -> StaggeredItem(i) { ListGridCardSkeleton() } }
                    } else {
                        itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, openItem, onIncrement, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type) } }
                    }
                    if (!vm.loading && filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomInset)) {
                    item { header() }
                    if (vm.loading && filtered.isEmpty()) {
                        item { ListRowSkeletonGroup(6) }
                    } else {
                        itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                            StaggeredItem(index, staggerSeen) {
                                Column {
                                    ListRow(it, openItem, onIncrement, showType = false, onLongPress = onEdit, isSelected = selectedItem?.id == it.id && selectedItem?.type == it.type, vm = vm)
                                    if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                                }
                            }
                        }
                    }
                    if (!vm.loading && filtered.isEmpty()) item { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
        // Dim scrim behind the expanded status menu — tap anywhere outside it to close,
        // same interaction as Google Keep's expandable note-type FAB.
        AnimatedVisibility(
            visible = filterMenuOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { filterMenuOpen = false }
            )
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { if (isGrid) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
        )
        StatusFilterFab(
            effectiveFilter, { vm.setListFilter(context, it) }, typeTab,
            expanded = filterMenuOpen, onExpandedChange = { filterMenuOpen = it },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// List/grid switcher

@Composable fun ListViewModeToggle(current: ListViewMode, onSelect: (ListViewMode) -> Unit) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(kikoCorner(12.dp)))
            .background(c.surfaceContainerHigh)
            .kikoClickable { onSelect(if (current == ListViewMode.List) ListViewMode.Grid else ListViewMode.List) }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (current == ListViewMode.List) Icons.Default.GridView else Icons.Default.ViewList,
            contentDescription = if (current == ListViewMode.List) "Switch to grid view" else "Switch to list view",
            tint = c.accent, modifier = Modifier.size(16.dp),
        )
    }
}
// Compact grid tile — cover, title, and the same progress bar as the list row

@Composable fun ListGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, onIncrement: ((MediaItem) -> Unit)? = null, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "gridSelectBg")
    val pad by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "gridSelectPad")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(bg)
            .kikoCombinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
            )
            // animateDpAsState on `pad` above already smoothly interpolates the padding
            // value frame-by-frame, so the container's size change is already gradual —
            // animateContentSize() here was a second, redundant size-diff/measure pass
            // wrapping every card in the grid, all the time, not just during a selection
            // transition. Same fix as BrowseCard in DiscoverScreen.
            .padding(pad)
    ) {
        // Height matches ListGridCardSkeleton's cover block so the loading state and the
        // real card don't jump in size once results arrive.
        Cover(item, Modifier.fillMaxWidth().height(160.dp), showStatus = true, selected = isSelected)
        // Fixed to 2 lines so every tile's progress bar lines up regardless of title length
        Text(
            item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, color = c.ink,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        // Always reserve the progress bar's footprint (top padding + height), even when
        // there's nothing to show, so the chapter/episode counter below stays aligned
        // across every card in the grid row regardless of what each one renders inside.
        // Total unknown (e.g. an ongoing manga MAL hasn't published a chapter count for
        // yet) intentionally shows no bar — an animated indeterminate one read as busy/
        // distracting, so the "2 of ? chapters" text below carries that case on its own.
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp)) {
            if (onIncrement != null && item.total > 0) {
                LinearProgressIndicator(progress = { item.progress.toFloat() / item.total }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(kikoCorner(4.dp))), color = statusColor(item.status), trackColor = c.surfaceLow)
            }
        }
        // Small always-on inset (independent of the selection `pad`) so the rounded 18dp
        // card corner doesn't clip the leading character of this bottom-most line.
        Text(compactProgressLabel(item), color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, start = 3.dp, bottom = 2.dp))
    }
}
// Anime/Manga segmented switch

// Status filter — bottom-right FAB that expands into one option per status, replacing the
// old horizontal FilterChip row. Same interaction as Google Keep's expandable note-type FAB:
// tap the FAB (or the scrim) to toggle, tap an option to pick it and collapse.

@Composable fun StatusFilterFab(current: String, set: (String) -> Unit, type: MediaType, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val progressLabel = if (type == MediaType.Anime) "Watching" else "Reading"
    val planLabel = if (type == MediaType.Anime) "Plan to Watch" else "Plan to Read"
    val labels = listOf("All", progressLabel, planLabel, "Completed", "On Hold", "Dropped")

    Column(modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(200), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                // Reversed so the list reads top-to-bottom in the same order it's defined,
                // since each new option stacks above the previous one off the FAB.
                labels.reversed().forEach { label ->
                    StatusFilterOption(label, filterLabelIcon(label), selected = current == label) { set(label); onExpandedChange(false) }
                }
            }
        }
        // Extended (icon + text) rather than icon-only, so the active status is spelled out
        // on the button itself — no room for it to be mistaken for something it isn't.
        ExtendedFloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = c.primary,
            contentColor = c.onPrimary,
            icon = { Icon(if (expanded) Icons.Default.Close else filterLabelIcon(current), contentDescription = null) },
            text = { Text(if (expanded) "Close" else current) },
        )
    }
}
// One row of the expanded status menu — a single pill (icon + label together), matching
// Google Keep's note-type FAB menu instead of a separate label chip next to a round button.
// Shaped as a squircle (rounded rectangle) rather than a full pill, same as HomeActionButton
// and every other chip/card/button in the app — nothing here is fully round.

@Composable fun StatusFilterOption(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(if (selected) c.primary else c.surfaceContainerHigh)
            .kikoClickable(scale = 0.94f) { onClick() }
            .padding(start = 18.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) c.onPrimary else c.ink, modifier = Modifier.size(20.dp))
        Text(label, color = if (selected) c.onPrimary else c.ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
// Icon per status filter label, for the FAB menu

fun filterLabelIcon(label: String): ImageVector = when (label) {
    "All" -> Icons.Default.Apps
    "Watching", "Reading" -> Icons.Default.PlayArrow
    "Plan to Watch", "Plan to Read" -> Icons.Default.Bookmark
    "Completed" -> Icons.Default.Check
    "On Hold" -> Icons.Default.Pause
    "Dropped" -> Icons.Default.Close
    else -> Icons.Default.FilterList
}

// vm is optional (and, when passed, only used for the AniList episode-number lookup below) —
// screens that don't pass one just keep showing the plain date-math guess, same as before.
@Composable fun ListRow(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, onIncrement: ((MediaItem) -> Unit)? = null, showType: Boolean = true, modifier: Modifier = Modifier, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false, showChevron: Boolean = false, vm: LibraryViewModel? = null) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    if (vm != null) LaunchedEffect(item.id) { vm.loadAiringEpisode(item) }
    val confirmed = vm?.getCachedAiring(item.id)
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "rowSelectBg")
    val hPad by animateDpAsState(if (isSelected) 10.dp else 0.dp, label = "rowSelectPad")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(16.dp)))
            .background(bg)
            .kikoCombinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
            )
            .padding(horizontal = hPad, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(item, Modifier.size(width = 92.dp, height = 128.dp), selected = isSelected)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (showType) "${item.type} · ${item.genre}" else item.genre, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.myRating > 0) {
                    Text("  ·  ", color = c.muted, fontSize = 13.sp)
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                    Text(item.myRating.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
            if (item.total > 0) {
                LinearProgressIndicator(progress = { item.progress.toFloat() / item.total }, modifier = Modifier.fillMaxWidth(0.75f).padding(top = 9.dp).height(4.dp).clip(RoundedCornerShape(kikoCorner(4.dp))), color = statusColor(item.status), trackColor = c.surfaceLow)
            }
            Text(progressLabel(item), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            item.nextEpisodeLabel(confirmed)?.let { label ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = c.accent, modifier = Modifier.size(12.dp))
                    Text(label, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (onIncrement != null) {
            val atMax = item.total > 0 && item.progress >= item.total
            // Compact squircle instead of a padded pill button — a "+1" tap doesn't need
            // its own wide rectangle, and shrinking it to a fixed-size square frees up
            // horizontal room for a bigger cover.
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(kikoCorner(12.dp)))
                    .background(if (atMax) c.surfaceContainerHigh else c.primaryContainer)
                    .kikoClickable(enabled = !atMax) {
                        val next = (item.progress + 1).let { p -> if (item.total > 0) minOf(p, item.total) else p }
                        onIncrement(item.copy(progress = next))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+1", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (atMax) c.muted else c.onPrimaryContainer)
            }
        } else if (showChevron) {
            // No increment action here (e.g. the Home "Continue" card) — a chevron fills the
            // trailing slot instead of leaving it blank, signaling the row is tappable.
            Icon(Icons.Default.ChevronRight, null, tint = c.muted, modifier = Modifier.size(22.dp))
        }
    }
}

// Ranking chart screen