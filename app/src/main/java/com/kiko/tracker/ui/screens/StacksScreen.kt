@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.kiko.tracker.data.api.StackBrowseKind
import com.kiko.tracker.data.api.StackSummary
import com.kiko.tracker.data.api.StackTitleEntry
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.ui.components.CoverStatusMark
import com.kiko.tracker.ui.components.Pill
import com.kiko.tracker.ui.components.SearchField
import com.kiko.tracker.ui.components.SkeletonBlock
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.components.statusColor
import com.kiko.tracker.ui.theme.KikoColors
import com.kiko.tracker.ui.theme.ListRowSkeletonGroup
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.accent
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCombinedClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.ui.theme.pressScale
import com.kiko.tracker.ui.theme.rememberStaggerMemory
import com.kiko.tracker.viewmodel.LibraryViewModel

@Composable fun StacksHomeScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenBrowse: (StackBrowseKind) -> Unit, onOpenStack: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    // Data is cached in
    // entries and back doesn't
    LaunchedEffect(Unit) { vm.loadStacksHome() }
    // Restore scroll position on
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.stacksHomeScrollIndex, initialFirstVisibleItemScrollOffset = vm.stacksHomeScrollOffset)
    val staggerSeen = rememberStaggerMemory()
    val saveScroll = { vm.saveStacksHomeScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    val openStack: (StackSummary) -> Unit = { s -> saveScroll(); onOpenStack(s.id, s.title) }
    val openBrowse: (StackBrowseKind) -> Unit = { k -> saveScroll(); onOpenBrowse(k) }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Spotlight row data —
    // Hoisted here (not inside
    val spotlightStacks = remember(vm.stacksHomeChallenges, vm.stacksHomeManga, vm.stacksHomeAnime) {
        vm.stacksHomeChallenges.map { "ch" to it } + vm.stacksHomeManga.map { "mg" to it } + vm.stacksHomeAnime.map { "an" to it }
    }
    // The "Recent" section here
    // this screen. Paging further
    // screen (via "See all"

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Text("Interest Stacks", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    // "Open in browser" and
                    // than two identical boxed
                    // treatment reads as one
                    Row(
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(kikoCorner(13.dp)))
                            .background(c.surfaceContainerHigh),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/stacks")) }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                        }
                        Box(Modifier.width(1.dp).height(18.dp).background(c.outlineVariant))
                        IconButton(onClick = { openBrowse(StackBrowseKind.All) }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Search, "Search stacks", tint = c.ink) }
                    }
                }
            }
            if (vm.stacksHomeLoading) {
                item { ListRowSkeletonGroup(4) }
            }
            if (spotlightStacks.isNotEmpty()) {
                // Spotlight row — the
                item { StackSectionHeader("Spotlight", onSeeAll = { openBrowse(StackBrowseKind.All) }) }
                item {
                    // Non-lazy + IntrinsicSize.Max so
                    // tallest one and stretches
                    // row (and everything below
                    // past cards with/without a
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        spotlightStacks.forEachIndexed { index, (_, s) ->
                            StaggeredItem(index, modifier = Modifier.fillMaxHeight()) { StackSpotlightCard(s, vm, modifier = Modifier.fillMaxHeight()) { openStack(s) } }
                        }
                    }
                }
            }
            if (vm.stacksHomeRecent.isNotEmpty()) {
                item { StackSectionHeader("Recent Interest Stacks", onSeeAll = { openBrowse(StackBrowseKind.All) }) }
                itemsIndexed(vm.stacksHomeRecent, key = { _, it -> "rc-${it.id}" }) { index, s ->
                    StaggeredItem(index, staggerSeen) {
                        Column {
                            StackListRow(s, vm) { openStack(s) }
                            // Same subtle row separator
                            // (see SearchResultRow's divider) —
                            if (index < vm.stacksHomeRecent.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
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
// Section title + "See

@Composable fun StackSectionHeader(title: String, onSeeAll: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
        // Same squircle arrow affordance
        // for why (Play Store-style
        // app's rounded-rectangle language rather
        // clickable Box rather than
        // is exactly what renders
        // default 40dp touch target.
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(kikoCorner(10.dp)))
                .background(c.surfaceContainerHigh)
                .kikoClickable(onClick = onSeeAll),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.ArrowForward, "See all", tint = c.ink, modifier = Modifier.size(17.dp)) }
    }
}

// Interest Stacks full browse/search

@Composable fun StacksScreen(vm: LibraryViewModel, initialKind: StackBrowseKind, onBack: () -> Unit, onOpenStack: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    // Only (re)loads when the
    // stack's detail page reuses
    LaunchedEffect(initialKind) { vm.setStacksBrowseKind(initialKind) }
    val activeKind = vm.stacksBrowseActiveKind ?: initialKind
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.stacksBrowseScrollIndex, initialFirstVisibleItemScrollOffset = vm.stacksBrowseScrollOffset)
    val staggerSeen = rememberStaggerMemory()
    val openStack: (StackSummary) -> Unit = { s -> vm.saveStacksBrowseScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onOpenStack(s.id, s.title) }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Auto-load the next page
    LaunchedEffect(listState, activeKind, vm.stacksBrowseResults.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 6 && vm.stacksBrowseResults.isNotEmpty() && !vm.stacksBrowseLoading) vm.loadMoreStacksBrowse() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Interest Stacks", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            SearchField(value = vm.stacksBrowseQuery, change = { vm.updateStacksBrowseQuery(it) }, hint = "Search stacks", onSearch = { vm.searchStacksBrowse() })
        }
        val kindListState = rememberLazyListState(initialFirstVisibleItemIndex = StackBrowseKind.entries.indexOf(activeKind).coerceAtLeast(0))
        LaunchedEffect(Unit) { centerChip(kindListState, StackBrowseKind.entries.indexOf(activeKind).coerceAtLeast(0)) }
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
                        StaggeredItem(index, staggerSeen) {
                            Column {
                                StackListRow(s, vm) { openStack(s) }
                                if (index < vm.stacksBrowseResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
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
// Up to 3 covers

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
// Small poster-sized cover —
// auto-generated stack thumbnail collage,

@Composable fun StackCoverCollage(covers: List<String>, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(modifier.clip(RoundedCornerShape(kikoCorner(14.dp)))) {
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
// Type/Challenge badges shared by

@Composable fun StackTagsRow(tags: List<String>) {
    val c = LocalKikoColors.current
    if (tags.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag -> if (tag == "Challenge") Pill(tag, c.warm, c.ink) else Pill(tag, c.primaryContainer, c.onPrimaryContainer) }
    }
}
// Small "N Entries ·

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
                Modifier.clip(kikoPillShape()).background(c.primaryContainer).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Layers, null, tint = c.accent, modifier = Modifier.size(11.dp))
                Text(restacks.toString(), color = c.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
// Featured stacks-homepage card —

@Composable fun StackFeaturedCard(stack: StackSummary, vm: LibraryViewModel, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    // See loadStackCovers on the
    // card independently re-fetching the
    val cachedCovers = vm.getCachedStackCovers(stack.id)
    val covers = stack.covers.ifEmpty { cachedCovers ?: emptyList() }
    LaunchedEffect(stack.id) { if (stack.covers.isEmpty()) vm.loadStackCovers(stack.id) }
    // Card(onClick=) overload, not a
    // for why: Card's own
    // attached there draws outside
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(kikoCorner(22.dp)),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).pressScale(interactionSource),
    ) {
        Column {
            StackCoverBanner(covers, modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(topStart = kikoCorner(22.dp), topEnd = kikoCorner(22.dp))))
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
// Spotlight card — fixed-width
// Challenge/Manga/Anime spotlight row on

@Composable fun StackSpotlightCard(stack: StackSummary, vm: LibraryViewModel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    // Browse/search summaries almost never
    // to a per-stack fetch
    // it only ever happens
    val cachedCovers = vm.getCachedStackCovers(stack.id)
    val covers = stack.covers.ifEmpty { cachedCovers ?: emptyList() }
    LaunchedEffect(stack.id) { if (stack.covers.isEmpty()) vm.loadStackCovers(stack.id) }
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(kikoCorner(22.dp)),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
        modifier = modifier.width(250.dp).pressScale(interactionSource),
    ) {
        Column(Modifier.fillMaxHeight()) {
            StackCoverBanner(covers, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(topStart = kikoCorner(22.dp), topEnd = kikoCorner(22.dp))))
            // weight(1f) lets this text
            // gave the card (see
            // stats row to the
            Column(Modifier.padding(14.dp).weight(1f)) {
                if (stack.tags.isNotEmpty()) Box(Modifier.padding(bottom = 8.dp)) { StackTagsRow(stack.tags) }
                Text(stack.title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (stack.author.isNotBlank()) Text("by ${stack.author}", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
                if (stack.description.isNotBlank()) {
                    Text(stack.description, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(top = 10.dp)) { StackStatsRow(stack.entryCount, stack.restacks, stack.updatedLabel) }
            }
        }
    }
}
// Compact stack card for
// cards; "See more" opens
// StackStatsRow from the cards
// on-demand cover fetch: StacksApi.forMedia's
// LibraryViewModel.loadMediaStacks) already ships each
// there's nothing to backfill
@Composable fun DetailStackCard(stack: StackSummary, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(kikoCorner(20.dp)),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
        modifier = Modifier.width(210.dp).pressScale(interactionSource),
    ) {
        Column {
            StackCoverBanner(stack.covers, modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(topStart = kikoCorner(20.dp), topEnd = kikoCorner(20.dp))))
            Column(Modifier.padding(13.dp)) {
                Text(stack.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (stack.author.isNotBlank()) Text("by ${stack.author}", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
                Box(Modifier.padding(top = 8.dp)) { StackStatsRow(stack.entryCount, stack.restacks, stack.updatedLabel) }
            }
        }
    }
}
// Full "Interest Stacks" page
// preview row ("See more")
// StacksScreen's general browse above,
// LibraryViewModel.loadMediaStacksPage (StacksApi.forMedia) instead of
// search, and titled with
@Composable fun MediaStacksScreen(vm: LibraryViewModel, item: MediaItem, onBack: () -> Unit, onOpenStack: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    val mediaId = item.id.toIntOrNull()
    LaunchedEffect(item.id, item.type) { if (mediaId != null) vm.loadMediaStacksPage(mediaId, item.type, reset = true) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.mediaStacksScrollIndex, initialFirstVisibleItemScrollOffset = vm.mediaStacksScrollOffset)
    val staggerSeen = rememberStaggerMemory()
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    DisposableEffect(item.id) { onDispose { vm.saveMediaStacksScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) } }
    // Auto-load the next page
    LaunchedEffect(listState, mediaId, vm.mediaStacksResults.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (mediaId != null && lastVisible != null && total > 0 && lastVisible >= total - 6 && vm.mediaStacksResults.isNotEmpty() && !vm.mediaStacksLoading && vm.mediaStacksHasMore) {
                    vm.loadMoreMediaStacks(mediaId, item.type)
                }
            }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Interest Stacks", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text(item.displayTitle(), color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
                if (vm.mediaStacksResults.isEmpty() && !vm.mediaStacksLoading) {
                    item { Text("No interest stacks found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                }
                if (vm.mediaStacksLoading && vm.mediaStacksResults.isEmpty()) {
                    item { ListRowSkeletonGroup(6) }
                } else {
                    itemsIndexed(vm.mediaStacksResults, key = { _, it -> it.id }) { index, s ->
                        StaggeredItem(index, staggerSeen) {
                            Column {
                                StackListRow(s, vm) { onOpenStack(s.id, s.title) }
                                if (index < vm.mediaStacksResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                            }
                        }
                    }
                }
                if (vm.mediaStacksLoading && vm.mediaStacksResults.isNotEmpty()) {
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
// Recent-stacks list row —
@Composable fun StackListRow(stack: StackSummary, vm: LibraryViewModel, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    // Same on-demand-and-cached cover fetch
    // — just takes 2
    val cachedCovers = vm.getCachedStackCovers(stack.id)
    val covers = stack.covers.ifEmpty { cachedCovers ?: emptyList() }.take(2)
    LaunchedEffect(stack.id) { if (stack.covers.isEmpty()) vm.loadStackCovers(stack.id) }
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
// One stack's entries —
// breakdown against the signed-in

@Composable fun StackDetailScreen(vm: LibraryViewModel, stackId: Int, initialTitle: String, loadingId: Int?, myListStatus: Map<Pair<Int, MediaType>, WatchStatus>, initialScroll: Pair<Int, Int> = 0 to 0, onLeaveScroll: (Int, Int) -> Unit = { _, _ -> }, onBack: () -> Unit, onOpenEntry: (StackTitleEntry) -> Unit, onEditEntry: (StackTitleEntry) -> Unit = {}, selectedItem: MediaItem? = null, onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {}) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    // Entries live in the
    // off a fetch the
    // opening an entry reads
    val detail = vm.getCachedStackDetail(stackId)
    val loadFailed = detail == null && vm.stackDetailLoadFailed(stackId)
    LaunchedEffect(stackId) { vm.loadStackDetail(stackId) }
    val gridState = rememberLazyGridState()
    // On a cache miss
    // an initial index/offset set
    // revisited. Instead, jump to
    // actually landed (only the
    // subsequent scrolling isn't fought).
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
    // Persist scroll position whenever
    // opening an entry AND
    // entry-open tap, so backing
    // the last entry-open position
    // down to it instead
    DisposableEffect(stackId) {
        onDispose { onLeaveScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }
    }
    val showGoToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 600 } }
    // Dispatches a tapped character/person/company
    // stack's description to this
    // ForumTopicScreen/ClubDetailScreen do for their
    val onOpenProfileLink: (MalProfileLink) -> Unit = { link ->
        when (link) {
            is MalProfileLink.Character -> onOpenCharacter(link.malId)
            is MalProfileLink.Person -> onOpenPerson(link.malId)
            is MalProfileLink.Company -> onOpenCompany(link.malId)
        }
    }
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
                    // Back button gets real
                    // matching the spacing every
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        Text(detail?.title?.ifBlank { initialTitle } ?: initialTitle, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        // Open this stack in
                        IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/stacks/$stackId")) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                            Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (detail == null && !loadFailed) {
                        StackDetailHeaderSkeleton()
                    } else if (loadFailed) {
                        Text("Couldn't load this stack.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center)
                    }
                    detail?.let { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (d.type.isNotBlank()) Pill(d.type, c.primaryContainer, c.onPrimaryContainer)
                            if (d.author.isNotBlank()) Text("by ${d.author}", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 9.dp))
                        }
                        // Description sits above the
                        // Rendered through the same
                        // so links stay tappable
                        // images load, instead of
                        if (d.description.isNotBlank()) {
                            ForumBody(d.description, Modifier.padding(top = 10.dp), onOpenProfileLink = onOpenProfileLink)
                        }
                        Box(Modifier.padding(top = 12.dp)) { StackStatsRow(d.entries.size, d.restacks, "") }
                        StackMyProgressBar(d.entries, myListStatus, c, Modifier.padding(top = 16.dp))
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            if (detail == null && !loadFailed) {
                // Skeletal grid placeholder while
                items(9) { i -> StaggeredItem(i) { StackEntryGridCardSkeleton() } }
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
// "My progress" breakdown for
// tab's stats card, scoped
// also appear in this
// Always rendered — even
// of the stack's entries
// the segmented bar (which

@Composable fun StackMyProgressBar(entries: List<StackTitleEntry>, myListStatus: Map<Pair<Int, MediaType>, WatchStatus>, c: KikoColors, modifier: Modifier = Modifier) {
    val tracked = entries.mapNotNull { myListStatus[it.malId to it.type] }
    // Mixed-type stacks are rare,
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
                            Box(Modifier.size(9.dp).clip(kikoCircleShape()).background(statusColor(st)))
                            Text("${st.label} $n", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
// Loading placeholder for the
// lines, "my progress" bar)
// entries are still being
// rather than blank-then-pop. Shape

@Composable fun StackDetailHeaderSkeleton() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(Modifier.width(64.dp).height(22.dp), shape = kikoPillShape())
            SkeletonBlock(Modifier.padding(start = 9.dp).width(96.dp).height(13.dp))
        }
        Column(Modifier.padding(top = 12.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(13.dp))
            SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(13.dp))
        }
        Box(Modifier.padding(top = 14.dp)) { SkeletonBlock(Modifier.width(140.dp).height(12.dp)) }
        Column(Modifier.padding(top = 18.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                SkeletonBlock(Modifier.width(90.dp).height(11.dp))
                SkeletonBlock(Modifier.width(70.dp).height(11.dp))
            }
            SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp), shape = kikoPillShape())
        }
        Spacer(Modifier.height(4.dp))
    }
}
// Loading placeholder for a
// + two text bars,

@Composable fun StackEntryGridCardSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
            SkeletonBlock(Modifier.fillMaxSize(), shape = RoundedCornerShape(kikoCorner(16.dp)))
        }
        SkeletonBlock(Modifier.padding(top = 7.dp).fillMaxWidth(0.85f).height(12.dp))
        SkeletonBlock(Modifier.padding(top = 5.dp).fillMaxWidth(0.4f).height(11.dp))
    }
}
// Grid card for a
// title, and format/score meta,

@Composable fun StackEntryGridCard(number: Int, entry: StackTitleEntry, loading: Boolean, myStatus: WatchStatus?, onClick: () -> Unit, onLongPress: (() -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "stackEntrySelectBg")
    val pad by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "stackEntrySelectPad")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(bg)
            .kikoCombinedClickable(
                enabled = !loading,
                onClick = onClick,
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit() } },
            )
            // animateDpAsState on `pad` above
            // value frame-by-frame, so the
            // animateContentSize() here was a
            // wrapping every card in
            .padding(pad)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(kikoCorner(16.dp)))
                .background(c.surfaceLow),
        ) {
            if (entry.cover.isNotBlank()) {
                AsyncImage(model = entry.cover, contentDescription = entry.title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Text(entry.title.take(1), fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(kikoPillShape()).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            myStatus?.let { CoverStatusMark(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
            // Long-press selection — same
            // Placed bottom-end since the
            // status mark owns top-end
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
                    Modifier.size(22.dp).clip(kikoCircleShape()).background(c.primary).border(1.5.dp, Color.White.copy(alpha = .9f), kikoCircleShape()),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, "Selected", tint = c.onPrimary, modifier = Modifier.size(13.dp)) }
            }
        }
        // start/bottom inset kept on
        // being the card's bottom-most
        // missing, e.g. some manga
        Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp, start = 3.dp, bottom = 2.dp))
        val meta = buildString {
            val fmt = listOfNotNull(entry.format.takeIf { it.isNotBlank() }, entry.year.takeIf { it.isNotBlank() }).joinToString(", ")
            if (fmt.isNotBlank()) append(fmt)
            if (entry.score > 0) { if (isNotEmpty()) append(" · "); append("★ %.2f".format(entry.score)) }
        }
        // Small always-on inset (independent
        // card corner doesn't clip
        if (meta.isNotBlank()) Text(meta, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, start = 3.dp, bottom = 2.dp))
    }
}

// Discover search results page