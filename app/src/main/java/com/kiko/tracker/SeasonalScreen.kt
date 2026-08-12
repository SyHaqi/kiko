@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

@Composable fun SeasonalScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val handleBack = { vm.resetSeasonal(); onBack() }
    BackHandler(onBack = handleBack)
    // Fetch only on entry
    LaunchedEffect(Unit) { if (vm.seasonalResults.isEmpty()) vm.loadSeasonal(context, vm.seasonalYear, vm.seasonalSeason, vm.seasonalSort, vm.seasonalContinuingOnly) }
    var browseOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = vm.seasonalScrollIndex, initialFirstVisibleItemScrollOffset = vm.seasonalScrollOffset)
    // Save position before navigating
    val openTitle: (MediaItem) -> Unit = remember(onOpenDetail) {
        { item -> vm.saveSeasonalScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset); onOpenDetail(item) }
    }

    // Load more near end
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to gridState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 6) vm.loadMoreSeasonal(context) }
    }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 600 } }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = handleBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Seasonal Chart", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${vm.seasonalSeason.label} ${vm.seasonalYear}", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        }
                    }
                    if (vm.seasonalLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = c.primary, trackColor = c.surfaceLow)
                    vm.seasonalError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                    Spacer(Modifier.height(14.dp))
                }
            }
            if (vm.seasonalLoading && vm.visibleSeasonalResults.isEmpty()) {
                items(9) { i -> StaggeredItem(i) { ListGridCardSkeleton() } }
            } else {
                itemsIndexed(vm.visibleSeasonalResults, key = { _, it -> it.id }) { index, it -> StaggeredItem(index) { SeasonalGridCard(it, openTitle) } }
            }
            if (!vm.seasonalLoading && vm.visibleSeasonalResults.isEmpty() && vm.seasonalError == null) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles for this season.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            if (vm.seasonalLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { gridState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
        )
        ExtendedFloatingActionButton(
            onClick = { browseOpen = true },
            containerColor = c.primary,
            contentColor = c.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(vm.seasonalSeason.icon, null) },
            text = { Text("${vm.seasonalSeason.label} ${vm.seasonalYear}", fontWeight = FontWeight.Bold) },
        )
    }
    if (browseOpen) SeasonalBrowseSheet(vm, context, onDismiss = { browseOpen = false })
}
// Weekly release schedule screen

@Composable fun ScheduleScreen(vm: LibraryViewModel, initialDay: java.time.DayOfWeek, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    var selectedDay by remember(initialDay) { mutableStateOf(initialDay) }
    val byDay = remember(vm.visibleDiscoverNewSeason) {
        vm.visibleDiscoverNewSeason.mapNotNull { item -> item.localBroadcast()?.let { (day, time) -> Triple(item, day, time) } }
    }
    val dayItems = byDay.filter { it.second == selectedDay }.sortedBy { it.third }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Release Schedule", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        val dayListState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        LazyRow(state = dayListState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp)) {
            itemsIndexed(java.time.DayOfWeek.values().toList()) { index, day ->
                val label = day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                FilterChip(
                    selected = selectedDay == day,
                    onClick = { selectedDay = day; scope.centerChip(dayListState, index) },
                    label = { Text(label) },
                    colors = kikoFilterChipColors(),
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            if (dayItems.isEmpty()) {
                item { Text("No releases on this day.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            itemsIndexed(dayItems, key = { _, it -> it.first.id }) { index, (item, _, time) -> StaggeredItem(index) { ScheduleRow(item, time, onOpenDetail) } }
        }
    }
}
// Schedule screen row

@Composable fun ScheduleRow(item: MediaItem, time: java.time.LocalTime, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val is24Hour = systemIs24Hour()
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface), border = BorderStroke(1.dp, c.cardBorder),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).kikoClickable { onOpenDetail(item) },
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, Modifier.size(width = 58.dp, height = 82.dp), showStatus = true)
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatLabel(item)} · ${item.genre}", color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                Row(
                    Modifier.padding(top = 9.dp).clip(RoundedCornerShape(10.dp)).background(c.primaryContainer).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Schedule, null, tint = c.primary, modifier = Modifier.size(12.dp))
                    Text(localizedTimeLabel(time, is24Hour), color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
    }
}
// Seasonal browse filter sheet

@Composable fun SeasonalBrowseSheet(vm: LibraryViewModel, context: Context, onDismiss: () -> Unit) {
    val c = LocalKikoColors.current
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val years = (thisYear + 1 downTo 2000).toList()
    var pendingYear by remember { mutableStateOf(vm.seasonalYear) }
    var pendingSeason by remember { mutableStateOf(vm.seasonalSeason) }
    var pendingSort by remember { mutableStateOf(vm.seasonalSort) }
    var pendingContinuing by remember { mutableStateOf(vm.seasonalContinuingOnly) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Seasonal Chart", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Browse a season", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))

            // Compact season stepper control
            Text("${pendingSeason.label} $pendingYear", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(20.dp)).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val (py, ps) = stepSeason(pendingYear, pendingSeason, forward = false)
                val (ny, ns) = stepSeason(pendingYear, pendingSeason, forward = true)
                IconButton(onClick = { pendingYear = py; pendingSeason = ps }) { Icon(Icons.Default.ChevronLeft, "Previous season", tint = c.ink) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SeasonName.entries.forEach { s -> SeasonIconButton(selected = s == pendingSeason, season = s) { pendingSeason = s } }
                }
                IconButton(onClick = { pendingYear = ny; pendingSeason = ns }) { Icon(Icons.Default.ChevronRight, "Next season", tint = c.ink) }
            }

            Text("Year", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            val yearListState = rememberLazyListState()
            val yearScope = rememberCoroutineScope()
            LazyRow(state = yearListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(years) { index, y -> FilterChip(selected = y == pendingYear, onClick = { pendingYear = y; yearScope.centerChip(yearListState, index) }, label = { Text(y.toString()) }, colors = kikoFilterChipColors()) }
            }

            // Consistent filter chip style
            Text("Sort by", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeasonalSort.entries.forEach { s ->
                    FilterChip(
                        selected = pendingSort == s,
                        onClick = { pendingSort = s },
                        label = { Text(s.label, maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(seasonalSortIcon(s), null, modifier = Modifier.size(15.dp)) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, iconColor = c.muted, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary, selectedLeadingIconColor = c.onPrimary),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Continuing titles filter toggle
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(16.dp))
                    .kikoClickable { pendingContinuing = !pendingContinuing }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Autorenew, null, tint = c.primary, modifier = Modifier.size(19.dp))
                Text("Still airing from before", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.weight(1f).padding(start = 11.dp))
                Switch(
                    checked = pendingContinuing,
                    onCheckedChange = { pendingContinuing = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary),
                )
            }

            Button(
                onClick = { vm.loadSeasonal(context, pendingYear, pendingSeason, pendingSort, pendingContinuing); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            ) { Text("Apply", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}

fun seasonalSortIcon(s: SeasonalSort) = when (s) { SeasonalSort.Members -> Icons.Default.Group; SeasonalSort.Score -> Icons.Default.Star }
// Seasonal chart grid tile

@Composable fun SeasonalGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().kikoClickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().aspectRatio(0.72f), showStatus = true)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        if (item.score > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                Text("%.2f".format(item.score), color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
            }
        }
        if (item.listUsers > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Default.Group, null, tint = c.muted, modifier = Modifier.size(11.dp))
                Text(formatCount(item.listUsers), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
            }
        }
    }
}

@Composable fun SeasonIconButton(selected: Boolean, season: SeasonName, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Box(Modifier.size(46.dp).clip(CircleShape).background(if (selected) c.primary else Color.Transparent).kikoClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(season.icon, season.label, tint = if (selected) c.onPrimary else c.muted, modifier = Modifier.size(21.dp))
    }
}

// Discover section
