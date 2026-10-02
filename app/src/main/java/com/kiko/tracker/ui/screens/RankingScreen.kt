@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.RankingSort
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.airTimerLabel
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.systemIs24Hour
import com.kiko.tracker.data.model.upcomingLabel
import com.kiko.tracker.data.model.twoDecimals
import com.kiko.tracker.ui.components.Cover
import com.kiko.tracker.ui.components.TypeToggle
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.ListRowSkeletonGroup
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.accent
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.rememberStaggerMemory
import com.kiko.tracker.viewmodel.LibraryViewModel

@Composable fun RankingScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(vm.rankingType, vm.rankingSort) { vm.loadRanking(context, vm.rankingType, vm.rankingSort) }
    val sorts = if (vm.rankingType == MediaType.Anime) RankingSort.entries.toList() else RankingSort.entries.filterNot { it == RankingSort.Upcoming }
    val listState = rememberLazyListState()
    val staggerSeen = rememberStaggerMemory()
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                // M3 default small top app bar: 64dp tall, content centred, no extra top/bottom padding.
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
                    Text("Ranking", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.headerTitleStart())
                }
                RankingTypeTabs(vm.rankingType) { vm.loadRanking(context, it, vm.rankingSort) }
                // Score / Popularity / Favorites / Upcoming as one dropdown button (same pill look as My List's sort).
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    RankingSortMenu(vm.rankingSort, sorts) { vm.loadRanking(context, vm.rankingType, it) }
                }
                if (vm.rankingLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.rankingError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (vm.rankingLoading && vm.visibleRankingResults.isEmpty()) {
                item { ListRowSkeletonGroup(6) }
            } else {
                itemsIndexed(vm.visibleRankingResults, key = { _, it -> it.id }) { index, it ->
                    StaggeredItem(index, staggerSeen) {
                        RankingRow(index + 1, it, onOpenDetail, myStatus = vm.trackedStatus(it))
                    }
                }
            }
            if (!vm.rankingLoading && vm.visibleRankingResults.isEmpty() && vm.rankingError == null) {
                item { Text("No results.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
        )
    }
}
// Anime / Manga primary tabs — same strip look as My List's status tabs (indicator hugs the label),
// but a plain two-tab row since the ranking list itself isn't swipeable.
@Composable private fun RankingTypeTabs(current: MediaType, onSelect: (MediaType) -> Unit) {
    val c = LocalKikoColors.current
    val density = LocalDensity.current
    val types = MediaType.entries
    val selectedIndex = types.indexOf(current).coerceAtLeast(0)
    val textWidths = remember { mutableStateMapOf<Int, Dp>() }
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        contentColor = c.primary,
        divider = { HorizontalDivider(color = c.outlineVariant) },
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                val pos = tabPositions[selectedIndex]
                val labelWidth = textWidths[selectedIndex] ?: pos.width
                val offset by animateDpAsState(pos.left + (pos.width - labelWidth) / 2, label = "rankingTabOffset")
                val width by animateDpAsState(labelWidth, label = "rankingTabWidth")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.BottomStart)
                        .offset(x = offset)
                        .width(width)
                        .height(3.dp)
                        .background(c.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
            }
        },
    ) {
        types.forEachIndexed { index, t ->
            val selected = index == selectedIndex
            Tab(
                selected = selected,
                onClick = { onSelect(t) },
                selectedContentColor = c.primary,
                unselectedContentColor = c.muted,
                text = {
                    Box(Modifier.onGloballyPositioned { coords ->
                        val w = with(density) { coords.size.width.toDp() }
                        if (textWidths[index] != w) textWidths[index] = w
                    }) { KikoTabText(if (t == MediaType.Anime) "Anime" else "Manga", selected) }
                },
            )
        }
    }
}
// Which chart is shown (Score / Popularity / Favorites / Upcoming) isn't a sort order, so each option
// gets its own icon instead of the generic "sort" glyph.
private fun rankingIcon(sort: RankingSort): androidx.compose.ui.graphics.vector.ImageVector = when (sort) {
    RankingSort.Score -> Icons.Default.Star
    RankingSort.Popularity -> Icons.Default.Whatshot
    RankingSort.Favorite -> Icons.Default.Favorite
    RankingSort.Upcoming -> Icons.Default.Event
}
// Ranking chart dropdown — same pill shape as My List's SortMenu, over RankingSort.
@Composable private fun RankingSortMenu(current: RankingSort, options: List<RankingSort>, onSelect: (RankingSort) -> Unit) {
    val c = LocalKikoColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHigh).kikoClickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(rankingIcon(current), "Ranking type", tint = c.accent, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceContainerHigh, shape = RoundedCornerShape(kikoCorner(18.dp))) {
            options.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label, color = if (sort == current) c.accent else c.ink, fontWeight = if (sort == current) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(rankingIcon(sort), null, tint = if (sort == current) c.accent else c.muted, modifier = Modifier.size(18.dp)) },
                    onClick = { onSelect(sort); open = false },
                )
            }
        }
    }
}
// Ranking chart row

@Composable fun RankingRow(position: Int, item: MediaItem, onOpenDetail: (MediaItem) -> Unit, myStatus: WatchStatus? = null) {
    val c = LocalKikoColors.current
    // Same pieces as SearchResultRow / My List: "format · episodes, year", plus the air timer while airing.
    val timer = item.airTimerLabel(null, systemIs24Hour()) ?: item.upcomingLabel()
    val timerTint = if (item.upcomingLabel() != null) c.muted else c.accent
    val details = listOf(formatLabel(item), episodeAndYear(item)).filter { it.isNotBlank() }.joinToString(" · ")
    // No divider lines — the row's own 14dp top/bottom padding is the only separation.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(16.dp)))
            .kikoClickable { onOpenDetail(item) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // overrideStatus: ranking chart results are merged with the library, so a status
        // edit/delete made elsewhere shows up here immediately.
        Box(Modifier.size(width = 100.dp, height = 150.dp)) {
            Cover(item, Modifier.fillMaxSize(), showStatus = true, overrideStatus = myStatus)
            if (item.score > 0) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(kikoCorner(8.dp))).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                    Text(item.score.twoDecimals(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
        }
        // Text column is as tall as the cover: title starts at the cover's top edge, the
        // details/timer lines follow it, and the members count + rank number are pinned to the
        // bottom (members left, "#N" right).
        Column(
            Modifier.weight(1f).height(150.dp).padding(start = 16.dp, end = 6.dp),
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (item.listUsers > 0) {
                        Icon(Icons.Default.Group, null, tint = c.muted, modifier = Modifier.size(13.dp))
                        Text(formatExact(item.listUsers), color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 5.dp))
                    }
                }
                Text("#$position", fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, color = c.primary)
            }
        }
    }
}
// Seasonal chart screen