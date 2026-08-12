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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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

@Composable fun StacksHomeScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenBrowse: (StackBrowseKind) -> Unit, onOpenStack: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    // Data is cached in the ViewModel — loads once, so navigating into a stack's
    // entries and back doesn't re-fetch or reset this list
    LaunchedEffect(Unit) { vm.loadStacksHome() }
    // Restore scroll position on return
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.stacksHomeScrollIndex, initialFirstVisibleItemScrollOffset = vm.stacksHomeScrollOffset)
    val saveScroll = { vm.saveStacksHomeScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    val openStack: (StackSummary) -> Unit = { s -> saveScroll(); onOpenStack(s.id, s.title) }
    val openBrowse: (StackBrowseKind) -> Unit = { k -> saveScroll(); onOpenBrowse(k) }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Spotlight row data — the curated Challenge/Manga/Anime picks combined into one ordered list.
    // Hoisted here (not inside the LazyColumn content block) since remember() needs composable context.
    val spotlightStacks = remember(vm.stacksHomeChallenges, vm.stacksHomeManga, vm.stacksHomeAnime) {
        vm.stacksHomeChallenges.map { "ch" to it } + vm.stacksHomeManga.map { "mg" to it } + vm.stacksHomeAnime.map { "an" to it }
    }
    // The "Recent" section here only ever shows page 1 — no auto-load-more on
    // this screen. Paging further only happens in the dedicated browse/search
    // screen (via "See all" or the search icon above), not by scrolling Home.

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Text("Interest Stacks", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    // Open stacks home in browser
                    IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/stacks")) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) {
                        Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { openBrowse(StackBrowseKind.All) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) { Icon(Icons.Default.Search, "Search stacks", tint = c.ink) }
                }
            }
            if (vm.stacksHomeLoading) {
                item { ListRowSkeletonGroup(4) }
            }
            if (spotlightStacks.isNotEmpty()) {
                // Spotlight row — the curated Challenge/Manga/Anime picks side by side instead of stacked full-width
                item { StackSectionHeader("Spotlight", onSeeAll = { openBrowse(StackBrowseKind.All) }) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(spotlightStacks, key = { _, (prefix, s) -> "$prefix-${s.id}" }) { index, (_, s) -> StaggeredItem(index) { StackSpotlightCard(s) { openStack(s) } } }
                    }
                }
            }
            if (vm.stacksHomeRecent.isNotEmpty()) {
                item { StackSectionHeader("Recent Interest Stacks", onSeeAll = { openBrowse(StackBrowseKind.All) }) }
                itemsIndexed(vm.stacksHomeRecent, key = { _, it -> "rc-${it.id}" }) { index, s -> StaggeredItem(index) { StackListRow(s) { openStack(s) } } }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// Section title + "See all" link shared across the stacks homepage

@Composable fun StackSectionHeader(title: String, onSeeAll: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text("See all", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary)
        }
    }
}

// Interest Stacks full browse/search screen — tabs, search field, Recent-style rows

@Composable fun StacksScreen(vm: LibraryViewModel, initialKind: StackBrowseKind, onBack: () -> Unit, onOpenStack: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    // Only (re)loads when the tab actually changes — returning here from a
    // stack's detail page reuses the cached results and scroll position
    LaunchedEffect(initialKind) { vm.setStacksBrowseKind(initialKind) }
    val activeKind = vm.stacksBrowseActiveKind ?: initialKind
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.stacksBrowseScrollIndex, initialFirstVisibleItemScrollOffset = vm.stacksBrowseScrollOffset)
    val openStack: (StackSummary) -> Unit = { s -> vm.saveStacksBrowseScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onOpenStack(s.id, s.title) }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Auto-load the next page as the user nears the bottom instead of requiring a manual tap
    LaunchedEffect(listState, activeKind, vm.stacksBrowseResults.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 6 && vm.stacksBrowseResults.isNotEmpty() && !vm.stacksBrowseLoading) vm.loadMoreStacksBrowse() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Interest Stacks", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            SearchField(value = vm.stacksBrowseQuery, change = { vm.updateStacksBrowseQuery(it) }, hint = "Search stacks", onSearch = { vm.searchStacksBrowse() })
        }
        val kindListState = rememberLazyListState()
        LazyRow(state = kindListState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            itemsIndexed(StackBrowseKind.entries.toList()) { index, k ->
                FilterChip(
                    selected = activeKind == k,
                    onClick = { vm.setStacksBrowseKind(k); scope.centerChip(kindListState, index) },
                    label = { Text(k.label) },
                    colors = kikoFilterChipColors(),
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
                if (vm.stacksBrowseResults.isEmpty() && !vm.stacksBrowseLoading) {
                    item { Text("No stacks found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                }
                if (vm.stacksBrowseLoading && vm.stacksBrowseResults.isEmpty()) {
                    item { ListRowSkeletonGroup(6) }
                } else {
                    itemsIndexed(vm.stacksBrowseResults, key = { _, it -> it.id }) { index, s ->
                        StaggeredItem(index) {
                            Column {
                                StackListRow(s) { openStack(s) }
                                if (index < vm.stacksBrowseResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.muted.copy(alpha = .15f))
                            }
                        }
                    }
                }
                if (vm.stacksBrowseLoading && vm.stacksBrowseResults.isNotEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) } }
                }
            }
            GoToTopButton(
                visible = showGoToTop,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            )
        }
    }
}
// Up to 3 covers side by side as a banner, or a plain icon tile when none scraped

@Composable fun StackCoverBanner(covers: List<String>, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    if (covers.isEmpty()) {
        Box(modifier.background(c.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Layers, null, tint = c.primary, modifier = Modifier.size(26.dp))
        }
    } else {
        Row(modifier) {
            covers.forEach { url ->
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
    }
}
// Small poster-sized cover — the top 1-2 entry covers side by side, mimicking MAL's
// auto-generated stack thumbnail collage, for use in compact list rows

@Composable fun StackCoverCollage(covers: List<String>, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        if (covers.isEmpty()) {
            Box(Modifier.fillMaxSize().background(c.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Layers, null, tint = c.primary, modifier = Modifier.size(22.dp))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                covers.take(2).forEach { url ->
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
            }
        }
    }
}
// Type/Challenge badges shared by featured cards and list rows

@Composable fun StackTagsRow(tags: List<String>) {
    val c = LocalKikoColors.current
    if (tags.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag -> if (tag == "Challenge") Pill(tag, c.warm, c.ink) else Pill(tag, c.primaryContainer, c.primary) }
    }
}
// Small "N Entries · Restacks" meta pill row shared by browse row and detail header

@Composable fun StackStatsRow(entryCount: Int, restacks: Int, updatedLabel: String) {
    val c = LocalKikoColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (entryCount > 0) Text("$entryCount Entries", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (updatedLabel.isNotBlank()) {
            if (entryCount > 0) Text(" · ", color = c.muted, fontSize = 12.sp)
            Text(updatedLabel, color = c.muted, fontSize = 12.sp)
        }
        if (restacks > 0) {
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(c.primaryContainer).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Layers, null, tint = c.primary, modifier = Modifier.size(11.dp))
                Text(restacks.toString(), color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
// Featured stacks-homepage card — big cover banner, tags, description, stats

@Composable fun StackFeaturedCard(stack: StackSummary, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    var covers by remember(stack.id) { mutableStateOf(stack.covers) }
    LaunchedEffect(stack.id) {
        if (covers.isEmpty()) covers = runCatching { StacksApi().topCovers(stack.id) }.getOrElse { emptyList() }
    }
    // Card(onClick=) overload, not a plain Card + .kikoClickable — see AiringNextCard
    // for why: Card's own rounded clip wraps the passed-in modifier, so a ripple
    // attached there draws outside the clip and shows as a square hint.
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface), border = BorderStroke(1.dp, c.cardBorder),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).pressScale(interactionSource),
    ) {
        Column {
            StackCoverBanner(covers, modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)))
            Column(Modifier.padding(16.dp)) {
                Text(stack.title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (stack.tags.isNotEmpty()) Box(Modifier.padding(top = 9.dp)) { StackTagsRow(stack.tags) }
                if (stack.author.isNotBlank()) Text("by ${stack.author}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                if (stack.description.isNotBlank()) {
                    Text(stack.description, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
                Box(Modifier.padding(top = 12.dp)) { StackStatsRow(stack.entryCount, stack.restacks, stack.updatedLabel) }
            }
        }
    }
}
// Spotlight card — fixed-width version of StackFeaturedCard for the horizontal
// Challenge/Manga/Anime spotlight row on the stacks homepage

@Composable fun StackSpotlightCard(stack: StackSummary, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    var covers by remember(stack.id) { mutableStateOf(stack.covers) }
    LaunchedEffect(stack.id) {
        if (covers.isEmpty()) covers = runCatching { StacksApi().topCovers(stack.id) }.getOrElse { emptyList() }
    }
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface), border = BorderStroke(1.dp, c.cardBorder),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.width(250.dp).pressScale(interactionSource),
    ) {
        Column {
            StackCoverBanner(covers, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)))
            Column(Modifier.padding(14.dp)) {
                if (stack.tags.isNotEmpty()) Box(Modifier.padding(bottom = 8.dp)) { StackTagsRow(stack.tags) }
                Text(stack.title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (stack.author.isNotBlank()) Text("by ${stack.author}", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
                if (stack.description.isNotBlank()) {
                    Text(stack.description, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                }
                Box(Modifier.padding(top = 10.dp)) { StackStatsRow(stack.entryCount, stack.restacks, stack.updatedLabel) }
            }
        }
    }
}
// Recent-stacks list row — mirrors SearchResultRow's discover layout (cover left, tags/description/stats right)

@Composable fun StackListRow(stack: StackSummary, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    // Browse/search rows don't ship cover images themselves, so fetch the
    // top entry covers from the stack's own page once this row is visible
    var covers by remember(stack.id) { mutableStateOf(stack.covers) }
    LaunchedEffect(stack.id) {
        if (covers.isEmpty()) covers = runCatching { StacksApi().topCovers(stack.id, limit = 2) }.getOrElse { emptyList() }
    }
    Row(
        Modifier.fillMaxWidth().kikoClickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StackCoverCollage(covers, modifier = Modifier.width(84.dp).height(118.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(stack.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (stack.tags.isNotEmpty()) Box(Modifier.padding(top = 7.dp)) { StackTagsRow(stack.tags) }
            if (stack.author.isNotBlank()) Text("by ${stack.author}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
            if (stack.description.isNotBlank()) {
                Text(stack.description, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
            Box(Modifier.padding(top = 8.dp)) { StackStatsRow(stack.entryCount, stack.restacks, stack.updatedLabel) }
        }
    }
}
// One stack's entries — header (back button + title), description, "my progress"
// breakdown against the signed-in user's list, then a seasonal-chart-style grid

@Composable fun StackDetailScreen(stackId: Int, initialTitle: String, loadingId: Int?, myListStatus: Map<Pair<Int, MediaType>, WatchStatus>, initialScroll: Pair<Int, Int> = 0 to 0, onLeaveScroll: (Int, Int) -> Unit = { _, _ -> }, onBack: () -> Unit, onOpenEntry: (StackTitleEntry) -> Unit, onEditEntry: (StackTitleEntry) -> Unit = {}, selectedItem: MediaItem? = null) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var detail by remember(stackId) { mutableStateOf<StackDetail?>(null) }
    var loadFailed by remember(stackId) { mutableStateOf(false) }
    LaunchedEffect(stackId) {
        loadFailed = false
        detail = runCatching { StacksApi().detail(stackId) }.getOrNull()
        if (detail == null) loadFailed = true
    }
    val gridState = rememberLazyGridState()
    // Entries are fetched fresh over the network each time this screen is (re)composed,
    // so the grid is empty at the moment gridState is created — an initial index/offset
    // set then would just get clamped to 0 and never revisited. Instead, jump to the saved
    // position once the real entries have actually landed (only the first time per visit,
    // so the user's own subsequent scrolling isn't fought).
    var scrollRestored by remember(stackId) { mutableStateOf(false) }
    LaunchedEffect(detail) {
        val d = detail
        if (d != null && !scrollRestored) {
            scrollRestored = true
            if (initialScroll.first != 0 || initialScroll.second != 0) {
                gridState.scrollToItem(initialScroll.first, initialScroll.second)
            }
        }
    }
    val scope = rememberCoroutineScope()
    // Persist scroll position whenever this screen leaves composition — covers both
    // opening an entry AND pressing back. Previously this was only saved from the
    // entry-open tap, so backing out (e.g. after scrolling back to the top first) left
    // the last entry-open position stuck in place, and the next visit would jump back
    // down to it instead of respecting where the user actually left the list.
    DisposableEffect(stackId) {
        onDispose { onLeaveScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }
    }
    val showGoToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 600 } }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    // Back button gets real breathing room below it before the type pill,
                    // matching the spacing every other detail header in the app uses
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        Text(detail?.title?.ifBlank { initialTitle } ?: initialTitle, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        // Open this stack in browser
                        IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/stacks/$stackId")) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) {
                            Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (detail == null && !loadFailed) {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
                    } else if (loadFailed) {
                        Text("Couldn't load this stack.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center)
                    }
                    detail?.let { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (d.type.isNotBlank()) Pill(d.type, c.primaryContainer, c.primary)
                            if (d.author.isNotBlank()) Text("by ${d.author}", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 9.dp))
                        }
                        // Description sits above the entries, right under the byline.
                        // Rendered through the same BBCode parser the forums screen uses,
                        // so links stay tappable (blue + underlined) and any embedded
                        // images load, instead of falling back to unstyled plain text.
                        if (d.description.isNotBlank()) {
                            ForumBody(d.description, Modifier.padding(top = 10.dp))
                        }
                        Box(Modifier.padding(top = 12.dp)) { StackStatsRow(d.entries.size, d.restacks, "") }
                        StackMyProgressBar(d.entries, myListStatus, c, Modifier.padding(top = 16.dp))
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            detail?.let { d ->
                if (d.entries.isEmpty() && !loadFailed) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("No entries in this stack.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), textAlign = TextAlign.Center) }
                }
                itemsIndexed(d.entries, key = { _, e -> e.malId }) { i, entry ->
                    StaggeredItem(i) {
                        StackEntryGridCard(i + 1, entry, loading = loadingId == entry.malId, myStatus = myListStatus[entry.malId to entry.type], onClick = { onOpenEntry(entry) }, onLongPress = { onEditEntry(entry) }, isSelected = selectedItem?.id == entry.malId.toString() && selectedItem?.type == entry.type)
                    }
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
// "My progress" breakdown for a stack — same segmented-bar + legend shown on the profile
// tab's stats card, scoped to just the titles from the signed-in user's list that
// also appear in this stack (Watching/Reading, Completed, On-Hold, Dropped, Plan).
// Always rendered — even at 0 tracked — so the stack detail screen always shows how many
// of the stack's entries the user has already watched/read out of the total, alongside
// the segmented bar (which just renders as an empty track when there's nothing tracked yet).

@Composable fun StackMyProgressBar(entries: List<StackTitleEntry>, myListStatus: Map<Pair<Int, MediaType>, WatchStatus>, c: KikoColors, modifier: Modifier = Modifier) {
    val tracked = entries.mapNotNull { myListStatus[it.malId to it.type] }
    // Mixed-type stacks are rare, but pick the verb matching whichever type dominates
    val verb = if (entries.count { it.type == MediaType.Manga } > entries.count { it.type == MediaType.Anime }) "Read" else "Watched"
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MY PROGRESS", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            Text("${tracked.size} of ${entries.size} $verb", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Box(Modifier.padding(top = 10.dp)) {
            SegmentedStatBar(WatchStatus.entries.map { st -> tracked.count { it == st } to statusColor(st) }, c)
        }
        if (tracked.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                WatchStatus.entries.forEach { st ->
                    val n = tracked.count { it == st }
                    if (n > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor(st)))
                            Text("${st.label} $n", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
// Grid card for a title inside a stack — cover with rank badge + tracking status mark,
// title, and format/score meta, styled to match SeasonalGridCard

@Composable fun StackEntryGridCard(number: Int, entry: StackTitleEntry, loading: Boolean, myStatus: WatchStatus?, onClick: () -> Unit, onLongPress: (() -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "stackEntrySelectBg")
    val pad by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "stackEntrySelectPad")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .kikoCombinedClickable(
                enabled = !loading,
                onClick = onClick,
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit() } },
            )
            .animateContentSize()
            .padding(pad)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(16.dp)).background(c.surfaceLow).border(1.dp, c.cardBorder, RoundedCornerShape(16.dp))) {
            if (entry.cover.isNotBlank()) {
                AsyncImage(model = entry.cover, contentDescription = entry.title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Text(entry.title.take(1), fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            myStatus?.let { CoverStatusMark(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
            // Long-press selection — same tint + checkmark treatment as Cover()'s selected state.
            // Placed bottom-end since the number badge already owns top-start and the tracking
            // status mark owns top-end on this card.
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                Box(Modifier.fillMaxSize().background(c.primary.copy(alpha = .32f)))
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .6f),
                exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .6f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(c.primary).border(1.5.dp, Color.White.copy(alpha = .9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, "Selected", tint = c.onPrimary, modifier = Modifier.size(13.dp)) }
            }
        }
        Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        val meta = buildString {
            val fmt = listOfNotNull(entry.format.takeIf { it.isNotBlank() }, entry.year.takeIf { it.isNotBlank() }).joinToString(", ")
            if (fmt.isNotBlank()) append(fmt)
            if (entry.score > 0) { if (isNotEmpty()) append(" · "); append("★ %.2f".format(entry.score)) }
        }
        if (meta.isNotBlank()) Text(meta, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

// Discover search results page