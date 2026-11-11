@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoDropdownMenu
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoSectionLoading
import com.kiko.tracker.ui.components.KikoLoadingRow
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerEdgeEnd
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
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.kiko.tracker.data.model.LocalCompactList
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.systemIs24Hour
import com.kiko.tracker.data.model.upcomingLabel
import com.kiko.tracker.data.model.twoDecimals
import com.kiko.tracker.ui.components.Cover
import com.kiko.tracker.ui.components.TypeToggle
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
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
    var sortSheetOpen by remember { mutableStateOf(false) }
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(KikoHeader.BackTop * 2 + 48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Rounded.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Ranking", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f).headerTitleStart())
            // Score / Popularity / Favorites / Upcoming live behind this filter button.
            IconButton(onClick = { sortSheetOpen = true }, modifier = Modifier.headerEdgeEnd()) {
                Icon(Icons.Rounded.FilterList, "Filter", tint = c.ink, modifier = Modifier.size(24.dp))
            }
        }
        if (sortSheetOpen) RankingSortSheet(vm.rankingSort, sorts, onDismiss = { sortSheetOpen = false }) { vm.loadRanking(context, vm.rankingType, it) }
        Box(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
                    item {
                        // M3 default small top app bar: 64dp tall, content centred, no extra top/bottom padding.
                        RankingTypeButtonGroup(vm.rankingType) { vm.loadRanking(context, it, vm.rankingSort) }
                        // Breathing room between the Anime/Manga switch and the first entry (the sort pill that
                        // used to sit here moved into the header's filter sheet).
                        Spacer(Modifier.height(12.dp))
                        if (vm.rankingLoading && vm.visibleRankingResults.isNotEmpty()) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = c.primary)
                        vm.rankingError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                    }
                    if (vm.rankingLoading && vm.visibleRankingResults.isEmpty()) {
                        item { KikoSectionLoading() }
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
    }
}
// Anime / Manga switch — a Material 3 Expressive connected button group (single-select), same
// construction as the Stats switch on Profile: ToggleButtons spaced by
// ButtonGroupDefaults.ConnectedSpaceBetween with the stock leading / trailing connected shapes.
// Unchecked = surfaceContainer, checked = primary.
@Composable private fun RankingTypeButtonGroup(current: MediaType, onSelect: (MediaType) -> Unit) {
    val c = LocalKikoColors.current
    val types = MediaType.entries
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        types.forEachIndexed { index, t ->
            ToggleButton(
                checked = current == t,
                onCheckedChange = { if (current != t) onSelect(t) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    types.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = c.surfaceContainer,
                    contentColor = c.onSurfaceVariant,
                    checkedContainerColor = c.primary,
                    checkedContentColor = c.onPrimary,
                ),
            ) {
                Text(if (t == MediaType.Anime) "Anime" else "Manga", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
// Which chart is shown (Score / Popularity / Favorites / Upcoming) isn't a sort order, so each option
// gets its own icon instead of the generic "sort" glyph.
private fun rankingIcon(sort: RankingSort): androidx.compose.ui.graphics.vector.ImageVector = when (sort) {
    RankingSort.Score -> Icons.Rounded.Star
    RankingSort.Popularity -> Icons.Rounded.Whatshot
    RankingSort.Favorite -> Icons.Rounded.Favorite
    RankingSort.Upcoming -> Icons.Rounded.Event
}
// Ranking chart sheet, opened from the header's filter button. No reset/apply: tapping an option
// switches the chart straight away and closes the sheet.
@Composable private fun RankingSortSheet(current: RankingSort, options: List<RankingSort>, onDismiss: () -> Unit, onSelect: (RankingSort) -> Unit) {
    val c = LocalKikoColors.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Rank by", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Chart", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
            options.forEach { sort ->
                val selected = sort == current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable { if (!selected) onSelect(sort); onDismiss() }.padding(horizontal = 4.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(rankingIcon(sort), null, tint = if (selected) c.accent else c.muted, modifier = Modifier.size(22.dp))
                    Text(sort.label, color = if (selected) c.accent else c.ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    if (selected) Icon(Icons.Rounded.Check, "Selected", tint = c.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
// Ranking chart row

@Composable fun RankingRow(position: Int, item: MediaItem, onOpenDetail: (MediaItem) -> Unit, myStatus: WatchStatus? = null) {
    val c = LocalKikoColors.current
    // Same pieces as SearchResultRow / My List: "format · episodes, year", plus the air timer while airing.
    val compact = LocalCompactList.current
    val coverW = if (compact) 72.dp else 88.dp
    val coverH = if (compact) 108.dp else 132.dp
    val rowVPad = if (compact) 10.dp else 12.dp
    val timer = item.airTimerLabel(null, systemIs24Hour()) ?: item.upcomingLabel()
    val timerTint = c.muted
    val details = listOf(formatLabel(item), episodeAndYear(item)).filter { it.isNotBlank() }.joinToString(" · ")
    // No divider lines — the row's own 14dp top/bottom padding is the only separation.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(16.dp)))
            .kikoClickable { onOpenDetail(item) }
            .padding(vertical = rowVPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // overrideStatus: ranking chart results are merged with the library, so a status
        // edit/delete made elsewhere shows up here immediately.
        Box(Modifier.size(width = coverW, height = coverH)) {
            Cover(item, Modifier.fillMaxSize(), showStatus = true, overrideStatus = myStatus)
        }
        // Text column is as tall as the cover: title starts at the cover's top edge, the
        // details/timer lines follow it, and the members count + rank number are pinned to the
        // bottom (members left, "#N" right).
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
                        Icon(Icons.Rounded.Schedule, null, tint = timerTint, modifier = Modifier.size(12.dp))
                        Text(timer, color = timerTint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    run {
                        // Star always shows; unrated titles read "N/A" instead of the rating vanishing.
                        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                        Text(if (item.score > 0) item.score.twoDecimals() else "N/A", color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp))
                        if (item.listUsers > 0) {
                            // Members count to the right of the MAL score, separated by a dot.
                            Text("·", color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 6.dp))
                            Icon(Icons.Rounded.Group, null, tint = c.muted, modifier = Modifier.size(13.dp))
                            Text(formatExact(item.listUsers), color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 5.dp))
                        }
                    }
                }
                Text("#$position", fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, color = c.primary)
            }
        }
    }
}
// Seasonal chart screen