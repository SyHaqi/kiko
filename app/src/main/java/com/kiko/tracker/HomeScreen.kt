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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

@Composable fun HomeScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, onList: () -> Unit, onLocateInList: (MediaItem) -> Unit, onDiscover: () -> Unit, onRanking: () -> Unit, onSeasonal: () -> Unit, onSchedule: (java.time.DayOfWeek) -> Unit, onOpenTopic: (Int, String) -> Unit, onSeeNews: () -> Unit, onOpenStack: (Int, String) -> Unit, onOpenStacks: () -> Unit, onSignIn: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    LaunchedEffect(vm.signedIn) { vm.loadNewsSnapshots(context); vm.loadHomeLatestStack(context) }
    val items = vm.visibleItems
    // Most recently updated wins
    val active = items.filter { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }.maxByOrNull { it.updatedAt }
        ?: items.firstOrNull { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }
        ?: items.firstOrNull()
    val today = java.time.LocalDate.now().dayOfWeek
    // Airing-next row pool
    val airingNext = vm.visibleDiscoverNewSeason.mapNotNull { item -> item.nextAirDateTime()?.let { item to it } }.sortedBy { it.second }.take(5).map { it.first }
    // Restore scroll position on return from a card/entry instead of resetting to top
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.homeScrollIndex, initialFirstVisibleItemScrollOffset = vm.homeScrollOffset)
    val trackedOpenDetail: (MediaItem) -> Unit = remember(onOpenDetail) {
        { item -> vm.saveHomeScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onOpenDetail(item) }
    }
    val trackedOpenTopic: (Int, String) -> Unit = remember(onOpenTopic) {
        { id, title -> vm.saveHomeScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onOpenTopic(id, title) }
    }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    PullToRefreshBox(
        isRefreshing = vm.loading,
        onRefresh = { vm.load(context); vm.loadNewsSnapshots(context, force = true); vm.loadHomeLatestStack(context, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                AppHeader("kiko") { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { vm.profileDrawerOpen = true } }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    // Use device current date
                    Text(
                        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.getDefault())).uppercase(java.util.Locale.getDefault()),
                        color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp,
                    )
                    if (airingNext.isNotEmpty()) {
                        SectionTitle("Airing next", "See all") { onSchedule(today) }
                        AiringNextRow(airingNext, trackedOpenDetail)
                    }
                    // Most recently updated in-progress title
                    active?.let { item ->
                        SectionTitle("Continue", "See list", onList)
                        ContinueCard(item, onClick = { onLocateInList(item) })
                    }
                    // Home recent news row
                    if (vm.newsSnapshots.isNotEmpty()) {
                        SectionTitle("Snapshots", "See news", onSeeNews)
                        SnapshotsGrid(vm.newsSnapshots, trackedOpenTopic)
                    }
                    // Freshest Interest Stack teaser
                    vm.homeLatestStack?.let { stack ->
                        SectionTitle("Interest Stacks", "See all", onOpenStacks)
                        StackFeaturedCard(stack) {
                            vm.saveHomeScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                            onOpenStack(stack.id, stack.title)
                        }
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

@Composable fun AiringNextRow(items: List<MediaItem>, onOpenDetail: (MediaItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) { items(items, key = { it.id }) { AiringNextCard(it, onOpenDetail) } }
}
// Airing next card layout

@Composable fun AiringNextCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val is24Hour = systemIs24Hour()
    val time = item.localBroadcast()?.second
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = c.surface), elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.width(264.dp).clickable { onOpenDetail(item) }) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, Modifier.size(width = 78.dp, height = 110.dp), showStatus = true)
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(item.displayTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = c.primary, modifier = Modifier.size(13.dp))
                    Text(
                        listOfNotNull(item.nextEpisodeLabel(), time?.let { localizedTimeLabel(it, is24Hour) }).joinToString(" · "),
                        color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp),
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
        modifier.clip(RoundedCornerShape(18.dp)).background(c.primaryContainer).clickable(onClick = onClick).padding(vertical = 15.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(19.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun SectionTitle(title: String, action: String, click: () -> Unit) { val c = LocalKikoColors.current; Row(Modifier.fillMaxWidth().padding(top = 29.dp, bottom = 13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink); TextButton(onClick = click) { Text(action, fontWeight = FontWeight.Bold, color = c.primary) } } }

// Inline "Continue" card, docked under Airing Next — the same row style and size used for
// entries in My List (ListRow below), just wrapped in a card so it stands out as its own
// section. Lives in the normal scroll flow, so there's no dismiss/pin gesture to manage.
// Tapping it jumps to the entry's spot in My List rather than opening its detail page —
// "Continue" is meant as a shortcut back into the list, not a detail-page shortcut.

@Composable fun ContinueCard(item: MediaItem, onClick: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        ListRow(item, onClick, showType = false, modifier = Modifier.padding(horizontal = 14.dp))
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
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (tall) 210.dp else 160.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenTopic(snapshot.topicId, snapshot.title) },
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

fun progressLabel(i: MediaItem) = if (i.progress == 0) i.status.label else "${i.progress}${if (i.total > 0) " of ${i.total}" else ""} ${if (i.type == MediaType.Anime) "episodes" else "chapters"}"
// Format field fallback

fun formatLabel(i: MediaItem): String = i.format.ifBlank { if (i.type == MediaType.Anime) "Anime" else "Manga" }

// Translate status label

fun normalizeFilterForType(filter: String, type: MediaType): String =
    if (filter == "Watching" || filter == "Reading") (if (type == MediaType.Anime) "Watching" else "Reading") else filter
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
            Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).clickable { open = true }.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sort, "Sort", tint = c.primary, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            ListSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.primary else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
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
            Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).clickable { open = true }.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sort, "Sort", tint = c.primary, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            DiscoverSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.primary else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
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
    val typeTab = vm.listTypeTab
    val effectiveFilter = normalizeFilterForType(vm.listFilter, typeTab)
    val filtered = vm.visibleItems
        .filter { it.type == typeTab && (effectiveFilter == "All" || it.status.label == effectiveFilter) && it.title.contains(submittedQuery, true) }
        .sortedWithListSort(vm.listSort, vm.titleLanguage)
    val isGrid = vm.listViewMode == ListViewMode.Grid
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
        AppHeader("My list", 0.dp) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { vm.profileDrawerOpen = true } }
        if (vm.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), color = c.primary, trackColor = c.surfaceLow)
        SearchField(query, { query = it }, "Search your list", onSearch = { submittedQuery = query }, onClear = { query = ""; submittedQuery = "" })
        // Reset scroll on change
        TypeToggle(typeTab) { vm.selectListTypeTab(it) }
        FilterRow(effectiveFilter, { vm.setListFilter(context, it) }, typeTab)
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
    PullToRefreshBox(isRefreshing = vm.loading, onRefresh = { vm.load(context) }, modifier = Modifier.fillMaxSize()) {
        if (isGrid) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
                items(filtered, key = { it.id }) { item -> ListGridCard(item, openItem, onIncrement, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type) }
                if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                item { header() }
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                    ListRow(it, openItem, onIncrement, showType = false, onLongPress = onEdit, isSelected = selectedItem?.id == it.id && selectedItem?.type == it.type)
                    if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.muted.copy(alpha = .15f))
                }
                if (filtered.isEmpty()) item { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { if (isGrid) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// List/grid switcher

@Composable fun ListViewModeToggle(current: ListViewMode, onSelect: (ListViewMode) -> Unit) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .clickable { onSelect(if (current == ListViewMode.List) ListViewMode.Grid else ListViewMode.List) }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (current == ListViewMode.List) Icons.Default.GridView else Icons.Default.ViewList,
            contentDescription = if (current == ListViewMode.List) "Switch to grid view" else "Switch to list view",
            tint = c.primary, modifier = Modifier.size(16.dp),
        )
    }
}
// Compact grid tile — cover, title, and the same progress bar as the list row

@Composable fun ListGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, onIncrement: ((MediaItem) -> Unit)? = null, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "gridSelectBg")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .combinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
            )
            .animateContentSize()
            .padding(if (isSelected) 8.dp else 0.dp)
    ) {
        Cover(item, Modifier.fillMaxWidth().aspectRatio(2f / 3f), showRating = true, selected = isSelected)
        // Fixed to 2 lines so every tile's progress bar lines up regardless of title length
        Text(
            item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, color = c.ink,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        if (onIncrement != null && item.total > 0) {
            LinearProgressIndicator(progress = { item.progress.toFloat() / item.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(4.dp)), color = statusColor(item.status), trackColor = c.surfaceLow)
        }
        Text(progressLabel(item), color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}
// Anime/Manga segmented switch

@Composable fun FilterRow(current: String, set: (String) -> Unit, type: MediaType) {
    val c = LocalKikoColors.current
    val progressLabel = if (type == MediaType.Anime) "Watching" else "Reading"
    val labels = listOf("All", progressLabel, "Plan to Watch", "Completed", "On Hold", "Dropped")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
        items(labels) { label -> FilterChip(selected = current == label, onClick = { set(label) }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
    }
}

@Composable fun ListRow(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, onIncrement: ((MediaItem) -> Unit)? = null, showType: Boolean = true, modifier: Modifier = Modifier, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "rowSelectBg")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .combinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
            )
            .padding(horizontal = if (isSelected) 10.dp else 0.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(item, Modifier.size(width = 84.dp, height = 118.dp), selected = isSelected)
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
                LinearProgressIndicator(progress = { item.progress.toFloat() / item.total }, modifier = Modifier.fillMaxWidth(0.75f).padding(top = 9.dp).height(4.dp).clip(RoundedCornerShape(4.dp)), color = statusColor(item.status), trackColor = c.surfaceLow)
            }
            Text(progressLabel(item), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            item.nextEpisodeLabel()?.let { label ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = c.primary, modifier = Modifier.size(12.dp))
                    Text(label, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (onIncrement != null) {
            val atMax = item.total > 0 && item.progress >= item.total
            // Filled pill tap target
            FilledTonalButton(
                onClick = { val next = (item.progress + 1).let { p -> if (item.total > 0) minOf(p, item.total) else p }; onIncrement(item.copy(progress = next)) },
                enabled = !atMax,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.primaryContainer, contentColor = c.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            ) { Text("+1", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

// Ranking chart screen