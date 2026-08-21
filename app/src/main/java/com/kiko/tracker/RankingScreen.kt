@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Text("Ranking", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
                }
                TypeToggle(vm.rankingType) { vm.loadRanking(context, it, vm.rankingSort) }
                val sortListState = rememberLazyListState()
                LazyRow(state = sortListState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
                    itemsIndexed(sorts) { index, sort -> FilterChip(selected = vm.rankingSort == sort, onClick = { vm.loadRanking(context, vm.rankingType, sort); scope.centerChip(sortListState, index) }, label = { Text(sort.label) }, colors = kikoFilterChipColors()) }
                }
                if (vm.rankingLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.rankingError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (vm.rankingLoading && vm.visibleRankingResults.isEmpty()) {
                item { ListRowSkeletonGroup(6) }
            } else {
                itemsIndexed(vm.visibleRankingResults, key = { _, it -> it.id }) { index, it ->
                    StaggeredItem(index, staggerSeen) {
                        Column {
                            RankingRow(index + 1, it, onOpenDetail, myStatus = vm.trackedStatus(it))
                            if (index < vm.visibleRankingResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                        }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// Ranking chart row

@Composable fun RankingRow(position: Int, item: MediaItem, onOpenDetail: (MediaItem) -> Unit, myStatus: WatchStatus? = null) {
    val c = LocalKikoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(16.dp)))
            .kikoClickable { onOpenDetail(item) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Text("#$position", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary) }
        // overrideStatus: ranking chart results are the raw MAL ranking API response, never
        // merged with the library, so a live O(1) lookup is what makes the badge reflect a
        // status edit/delete made elsewhere without needing to reopen this screen.
        Cover(item, Modifier.size(width = 84.dp, height = 118.dp), showStatus = true, overrideStatus = myStatus)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${item.type} · ${item.genre}", color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            if (item.score > 0) {
                Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                    Text(item.score.twoDecimals(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
            } else if (item.listUsers > 0) {
                Text(formatCount(item.listUsers), color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }
}
// Seasonal chart screen