@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.api.MalUserListEntry
import com.kiko.tracker.data.api.MalUserListScrapeApi
import com.kiko.tracker.data.model.ListViewMode
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayLabel
import com.kiko.tracker.ui.components.CoverRatingMark
import com.kiko.tracker.ui.components.CoverStatusMark
import com.kiko.tracker.ui.components.MalLoginWebView
import com.kiko.tracker.ui.components.statusColor
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.accent
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.rememberStaggerMemory
import com.kiko.tracker.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

// Tab order/labels for the status strip — "All" first, then MAL's own five
// per-item statuses (same set FriendProfile's StatusLegendRow taps into).
private fun friendListStatusTabs(type: MediaType): List<WatchStatus?> =
    listOf(null, if (type == MediaType.Anime) WatchStatus.Watching else WatchStatus.Reading, WatchStatus.Completed, WatchStatus.OnHold, WatchStatus.Dropped, WatchStatus.Plan)

private fun WatchStatus?.tabLabel(type: MediaType): String = this?.displayLabel(type) ?: "All"

// Sort options for a friend's list — same four as My List's ListSort.
// LastUpdated/StartDate read MalUserListEntry.updatedAt/startDate (scraped
// from the same list page); entries missing one sink to the bottom, and both
// sort newest-first, matching sortedWithListSort in HomeScreen.
private enum class FriendListSort(val label: String) { Title("Title"), Score("Score"), LastUpdated("Last Updated"), StartDate("Start Date") }

private fun List<MalUserListEntry>.sortedWithFriendSort(sort: FriendListSort): List<MalUserListEntry> = when (sort) {
    FriendListSort.Title -> sortedBy { it.title.lowercase() }
    FriendListSort.Score -> sortedWith(compareByDescending<MalUserListEntry> { it.score > 0 }.thenByDescending { it.score })
    FriendListSort.LastUpdated -> sortedWith(compareByDescending<MalUserListEntry> { it.updatedAt > 0L }.thenByDescending { it.updatedAt })
    FriendListSort.StartDate -> sortedWith(compareByDescending<MalUserListEntry> { it.startDate.isNotBlank() }.thenByDescending { it.startDate })
}

private fun friendProgressLabel(entry: MalUserListEntry, type: MediaType): String {
    if (entry.progress == 0) return entry.status.displayLabel(type)
    val unit = if (type == MediaType.Anime) "ep." else "ch."
    return if (entry.total > 0) "${entry.progress} of ${entry.total} $unit" else "${entry.progress} $unit"
}

/**
 * Another MAL member's anime or manga list, read-only, opened by tapping a
 * status row (Watching/Completed/…) on FriendProfileScreen — replaces what
 * used to just be a CustomTabsIntent out to myanimelist.net/animelist/…
 * with the same list rendered in Kiko's own UI. MalUserListScrapeApi does
 * one fetch for the whole list; switching the status strip below just
 * re-filters that same in-memory list, same idea as ListScreen's own tabs.
 *
 * Mirrors My List's own design (StatusListPage in HomeScreen.kt): a
 * segmented, swipeable status strip; a count row with a grid/list toggle
 * and a sort menu; and either a grid of cover tiles or a row list.
 */
@Composable fun FriendListScreen(
    vm: LibraryViewModel,
    username: String, type: MediaType, initialStatus: WatchStatus?,
    onBack: () -> Unit, onOpenTitle: (Int, MediaType) -> Unit,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val session = remember { MalSessionCookie(context) }
    var connected by remember { mutableStateOf(session.has()) }
    var showLogin by remember { mutableStateOf(false) }
    var verifyingLogin by remember { mutableStateOf(false) }

    var entries by remember { mutableStateOf<List<MalUserListEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Grid/list + sort are kept above the per-friend `key()` below, so
    // browsing from one friend's list to another keeps your last choice —
    // only the active status tab resets per friend.
    var viewMode by remember { mutableStateOf(ListViewMode.List) }
    var sort by remember { mutableStateOf(FriendListSort.Title) }

    fun load() {
        if (username.isBlank()) return
        loading = true; error = null
        scope.launch {
            runCatching { MalUserListScrapeApi(context).list(username, type) }
                .onSuccess { entries = it }
                .onFailure { e ->
                    if (e is MalSessionExpired) {
                        connected = false; session.clear()
                    } else {
                        error = "Couldn't load this list — try again."
                    }
                }
            loading = false
        }
    }

    LaunchedEffect(connected, username, type) { if (connected) load() }

    if (!connected) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                Text(if (type == MediaType.Anime) "Anime List" else "Manga List", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
            }
            if (showLogin) {
                Box(Modifier.fillMaxSize()) {
                    MalLoginWebView(
                        session = session,
                        onLoginSuccess = { showLogin = false; connected = true },
                        onVerifyingChange = { verifyingLogin = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (verifyingLogin) {
                        Box(Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = c.primary)
                                Text("Confirming your MAL login…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.People, null, tint = c.muted, modifier = Modifier.size(48.dp))
                    Text("Unlock more features", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink, modifier = Modifier.padding(top = 16.dp))
                    Text(
                        "MAL doesn't expose another member's list through the app the usual way. Kiko needs to open a one-time login page to read it from their profile.",
                        color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                    )
                    Button(onClick = { showLogin = true }, modifier = Modifier.padding(top = 20.dp), colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) {
                        Text("Connect")
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(if (type == MediaType.Anime) "Anime List" else "Manga List", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text(username, color = c.muted, fontSize = 12.sp)
            }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), color = c.accent, trackColor = c.surfaceLow)

        // Keyed on username/type: switching to a different friend (or
        // switching anime/manga) should land back on "All" rather than
        // keeping whichever tab you'd swiped to on the last friend.
        key(username, type) {
            FriendListBody(
                vm = vm, username = username,
                entries = entries, loading = loading, error = error, type = type, initialStatus = initialStatus,
                viewMode = viewMode, sort = sort,
                onSetViewMode = { viewMode = it }, onSetSort = { sort = it },
                onRetry = ::load, onOpenTitle = onOpenTitle,
            )
        }
    }
}

@Composable private fun ColumnScope.FriendListBody(
    vm: LibraryViewModel, username: String,
    entries: List<MalUserListEntry>?, loading: Boolean, error: String?, type: MediaType, initialStatus: WatchStatus?,
    viewMode: ListViewMode, sort: FriendListSort,
    onSetViewMode: (ListViewMode) -> Unit, onSetSort: (FriendListSort) -> Unit,
    onRetry: () -> Unit, onOpenTitle: (Int, MediaType) -> Unit,
) {
    val c = LocalKikoColors.current
    val tabs = remember(type) { friendListStatusTabs(type) }
    val labels = remember(tabs) { tabs.map { it.tabLabel(type) } }
    val initialIndex = remember(tabs, initialStatus) { tabs.indexOf(initialStatus).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { labels.size }
    val scope = rememberCoroutineScope()

    // Same rounded-container + underline-tab look as My List's own
    // StatusFilterTabs, just driven off a plain index instead of a
    // vm-backed filter string.
    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh),
        containerColor = Color.Transparent,
        contentColor = c.primary,
        edgePadding = 6.dp,
        divider = {},
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == pagerState.currentPage
            Tab(
                selected = selected,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                selectedContentColor = c.primary,
                unselectedContentColor = c.muted,
                text = { Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1) },
            )
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
        FriendListPage(
            vm = vm, username = username,
            entries = entries, loading = loading, error = error, tabStatus = tabs.getOrNull(page), type = type,
            viewMode = viewMode, sort = sort, onSetViewMode = onSetViewMode, onSetSort = onSetSort,
            onRetry = onRetry, onOpenTitle = onOpenTitle,
        )
    }
}

// One swipeable page — the title count/grid-toggle/sort row plus the grid
// or list of a friend's titles for a single status filter. Mirrors My
// List's StatusListPage in HomeScreen.kt.
@Composable private fun FriendListPage(
    vm: LibraryViewModel, username: String,
    entries: List<MalUserListEntry>?, loading: Boolean, error: String?, tabStatus: WatchStatus?, type: MediaType,
    viewMode: ListViewMode, sort: FriendListSort,
    onSetViewMode: (ListViewMode) -> Unit, onSetSort: (FriendListSort) -> Unit,
    onRetry: () -> Unit, onOpenTitle: (Int, MediaType) -> Unit,
) {
    val c = LocalKikoColors.current
    val filtered = remember(entries, tabStatus, sort) {
        entries?.filter { tabStatus == null || it.status == tabStatus }?.sortedWithFriendSort(sort)
    }
    val isGrid = viewMode == ListViewMode.Grid
    val staggerSeen = rememberStaggerMemory()
    // Restored from the ViewModel rather than a plain rememberLazyListState/
    // rememberLazyGridState: this whole screen is torn down (not just
    // backgrounded) whenever a title is opened from it, so state that only
    // lives in `remember` resets to the top the moment you come back.
    val savedScroll = remember(username, type, tabStatus) { vm.getFriendListScroll(username, type, tabStatus) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedScroll.first, initialFirstVisibleItemScrollOffset = savedScroll.second)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = savedScroll.first, initialFirstVisibleItemScrollOffset = savedScroll.second)
    // rememberUpdatedState so the dispose lambda below always sees the
    // view mode as of the moment this page is actually torn down, rather
    // than whatever it was when the effect was first installed.
    val isGridAtDispose by rememberUpdatedState(isGrid)
    DisposableEffect(username, type, tabStatus) {
        onDispose {
            if (isGridAtDispose) vm.saveFriendListScroll(username, type, tabStatus, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            else vm.saveFriendListScroll(username, type, tabStatus, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    val showGoToTop by remember { derivedStateOf { if (isGrid) gridState.firstVisibleItemIndex > 0 else listState.firstVisibleItemIndex > 0 } }
    val scope = rememberCoroutineScope()

    if (error != null && entries == null) {
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error, color = c.muted, fontSize = 13.sp)
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
        }
        return
    }
    if (loading && entries == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, modifier = Modifier.padding(top = 40.dp)) }
        return
    }

    val countRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.orEmpty().size} titles" + if (loading) " · syncing…" else "", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(viewMode, onSetViewMode)
                FriendSortMenu(sort, onSetSort)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            isGrid,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "friend-list-view-mode",
        ) { grid ->
            if (grid) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { countRow() }
                    if (filtered.isNullOrEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles here.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
                    } else {
                        itemsIndexed(filtered, key = { _, it -> it.malId }) { index, entry ->
                            StaggeredItem(index, staggerSeen) { FriendGridCard(entry, type) { onOpenTitle(entry.malId, type) } }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { countRow() }
                    if (filtered.isNullOrEmpty()) {
                        item { Text("No titles here.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
                    } else {
                        itemsIndexed(filtered, key = { _, it -> it.malId }) { index, entry ->
                            StaggeredItem(index, staggerSeen) {
                                Column(Modifier.padding(horizontal = 14.dp)) {
                                    FriendListRow(entry, type, c, onClick = { onOpenTitle(entry.malId, type) })
                                    // Same indent as My List's own ListRow divider —
                                    // lines up with the 92dp cover + 16dp text gap below.
                                    if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { if (isGrid) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
        )
    }
}

// Same visual shape as My List's SortMenu, over FriendListSort.
@Composable private fun FriendSortMenu(current: FriendListSort, onSelect: (FriendListSort) -> Unit) {
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
            FriendListSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.accent else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(s); open = false },
                )
            }
        }
    }
}

// Compact grid tile for a friend's title — 1:1 with My List's own
// ListGridCard (160dp cover, CoverStatusMark/CoverRatingMark badges, 2-line
// title, progress bar + label — My List's grid tile has no genre line, so
// neither does this one), built straight off MalUserListEntry's own
// cover/title fields (same pattern as HistoryRow's HistoryCover) rather than
// a MediaItem, since a friend's scraped entry isn't necessarily in your own
// signed-in list.
@Composable private fun FriendGridCard(entry: MalUserListEntry, type: MediaType, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).kikoClickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHigh)) {
            if (entry.cover.isNotBlank()) {
                AsyncImage(model = entry.cover, contentDescription = entry.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(entry.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            // Same icon+border badge as Cover()'s showStatus, top-start.
            CoverStatusMark(entry.status, Modifier.align(Alignment.TopStart).padding(6.dp))
            // Same pill badge as Cover()'s showRating, bottom-end.
            if (entry.score > 0) CoverRatingMark(entry.score, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Text(
            entry.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, color = c.ink,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp)) {
            if (entry.total > 0) {
                LinearProgressIndicator(progress = { entry.progress.toFloat() / entry.total }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(kikoCorner(4.dp))), color = statusColor(entry.status), trackColor = c.surfaceLow)
            }
        }
        Text(friendProgressLabel(entry, type), color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, start = 3.dp, bottom = 2.dp))
    }
}

// 1:1 with My List's own ListRow: 92x128 cover, same vertical rhythm.
// Score-only (no genre line) — MAL's scraped list-table data has no genre
// field to show one (see MalUserListEntry's doc comment).
@Composable
private fun FriendListRow(entry: MalUserListEntry, type: MediaType, c: com.kiko.tracker.ui.theme.KikoColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 92.dp, height = 128.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh)) {
            if (entry.cover.isNotBlank()) {
                AsyncImage(model = entry.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(entry.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 6.dp).weight(1f)) {
            Text(entry.title, color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.score > 0) {
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                    Text(entry.score.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
            if (entry.total > 0) {
                LinearProgressIndicator(progress = { entry.progress.toFloat() / entry.total }, modifier = Modifier.fillMaxWidth(0.75f).padding(top = 9.dp).height(4.dp).clip(RoundedCornerShape(kikoCorner(4.dp))), color = statusColor(entry.status), trackColor = c.surfaceLow)
            }
            Text(friendProgressLabel(entry, type), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}