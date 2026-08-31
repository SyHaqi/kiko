@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.util.Log
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.browser.customtabs.CustomTabsIntent
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Combined Community tab — Forums and Clubs share one bottom-nav slot, switched via the
// same tappable-title dropdown pattern ListScreen uses for Anime/Manga (see SwitcherHeader).
// Drilling into a specific board's topics still takes over the full screen either way,
// since that's a detail view rather than another top-level section to switch between.
@Composable fun CommunityScreen(vm: LibraryViewModel, onOpenTopic: (Int, String) -> Unit, onOpenClub: (MalClub) -> Unit) {
    val context = LocalContext.current
    // Only forums need eagerly preloading here — Clubs fetches its own list lazily on
    // first composition (see ClubsScreen), and News-board jumps fetch on demand too.
    LaunchedEffect(vm.signedIn, vm.communityTab) { if (vm.communityTab == CommunityTab.Forums) vm.loadForumBoards(context) }
    AnimatedContent(
        vm.forumMode,
        transitionSpec = { if (targetState == ForumMode.Topics) PushEnter togetherWith PushExit else PopEnter togetherWith PopExit },
        label = "forum-mode",
    ) { mode ->
        if (mode == ForumMode.Topics) {
            ForumTopicsScreen(vm, context, onOpenTopic)
        } else {
            AnimatedContent(
                vm.communityTab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "community-tab",
            ) { tab ->
                when (tab) {
                    CommunityTab.Forums -> ForumBoardsScreen(vm, context)
                    CommunityTab.Clubs -> ClubsScreen(vm, onOpenClub)
                }
            }
        }
    }
}
// Forums landing page

@Composable fun ForumBoardsScreen(vm: LibraryViewModel, context: Context) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf("") }
    // Search bar starts collapsed into an icon beside the avatar, same as the List tab
    // (see ExpandableSearchHeader) — expanding it takes over the whole header row instead
    // of sitting underneath as its own always-visible field.
    var searchExpanded by remember { mutableStateOf(false) }
    // Restore board list scroll
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.forumBoardsScrollIndex, initialFirstVisibleItemScrollOffset = vm.forumBoardsScrollOffset)
    val saveScroll = { vm.saveForumBoardsScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    PullToRefreshBox(isRefreshing = vm.forumBoardsLoading, onRefresh = { vm.loadForumBoards(context, force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                ExpandableSearchHeader(
                    current = vm.communityTab,
                    options = CommunityTab.entries.toList(),
                    labelFor = { it.label },
                    onSelect = { vm.selectCommunityTab(context, it) },
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { if (query.isNotBlank()) { saveScroll(); vm.runForumSearch(context, query) } },
                    onClear = { query = "" },
                    expanded = searchExpanded,
                    onExpandedChange = { expanded -> searchExpanded = expanded; if (!expanded) query = "" },
                    hint = "Search topics",
                    horizontalPadding = 0.dp,
                    switchDescription = "Switch between Forums and Clubs",
                ) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
            }
            if (vm.authChecked && !vm.signedIn) {
                item { Text("Sign in from Profile to browse the MAL forums", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
            } else {
                item {
                    if (vm.forumBoardsLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                    vm.forumBoardsError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                }
                if (vm.forumBoardsLoading && vm.forumCategories.isEmpty()) {
                    item { TopicRowSkeletonGroup(5) }
                }
                // Grouped category board card
                vm.forumCategories.forEach { category ->
                    item { Text(category.title.uppercase(), color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp)) }
                    item {
                        Card(shape = RoundedCornerShape(kikoCorner(20.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Column {
                                category.boards.forEachIndexed { index, board ->
                                    ForumBoardRow(board) { saveScroll(); vm.openForumBoard(context, board) }
                                    if (index < category.boards.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 66.dp), thickness = 1.dp, color = c.outlineVariant)
                                }
                            }
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
// Subboard count pill

// MAL gives every forum board its own FontAwesome glyph in the sidebar/board-list markup
// (bullhorn for Announcements, gavel for Guidelines, life-ring for Support, and so on —
// see the board-list HTML). This app previously rendered every single board with the same
// generic Icons.Default.Forum glyph, which made the board list visually flat and harder to
// scan at a glance. Mapping board id -> a Material Icons Extended equivalent restores that
// per-board distinctiveness while keeping the app's own look: same tint/background/shape as
// before, only the glyph itself changes. Ids come straight from MAL's own board query params
// (e.g. "?board=5"), which is the only stable identifier — titles occasionally get re-worded.
// Falls back to the original generic Forum glyph for any board id not covered here (keeps
// future/unlisted boards, like a new one MAL adds later, from rendering as a blank icon).
private fun forumBoardIcon(board: ForumBoard) = when (board.id) {
    5 -> Icons.Default.Campaign            // Updates & Announcements
    14 -> Icons.Default.Gavel              // MAL Guidelines & FAQ
    17 -> Icons.Default.EditNote           // DB Modification Requests
    3 -> Icons.Default.SupportAgent        // Support
    4 -> Icons.Default.Lightbulb           // Suggestions
    13 -> Icons.Default.EmojiEvents        // MAL Contests
    15 -> Icons.Default.Article            // News Discussion
    16 -> Icons.Default.CardGiftcard       // Anime & Manga Recommendations
    19 -> Icons.Default.Folder             // Series Discussion
    1 -> Icons.Default.Tv                  // Anime Discussion
    2 -> Icons.Default.MenuBook            // Manga Discussion
    8 -> Icons.Default.ChatBubble          // Introductions
    7 -> Icons.Default.SportsEsports       // Games, Computers & Tech Support
    10 -> Icons.Default.MusicNote          // Music & Entertainment
    11 -> Icons.Default.LocalCafe          // Casual Discussion
    12 -> Icons.Default.PhotoLibrary       // Creative Corner
    9 -> Icons.Default.Extension           // Forum Games
    6 -> Icons.Default.LocalBar            // Current Events
    else -> Icons.Default.Forum
}

@Composable fun ForumBoardRow(board: ForumBoard, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().kikoClickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(forumBoardIcon(board), null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(board.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
            if (board.description.isNotBlank()) Text(board.description, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (board.subboards.isNotEmpty()) {
            Box(Modifier.padding(end = 8.dp).clip(kikoPillShape()).background(c.surfaceLow).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text("${board.subboards.size} boards", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = c.muted)
    }
}
// Back-to-top floating button

@Composable fun GoToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FloatingActionButton(onClick = onClick, containerColor = c.primary, contentColor = c.onPrimary, modifier = Modifier.size(46.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Back to top")
        }
    }
}
// Shared topic list page

@Composable fun ForumTopicsScreen(vm: LibraryViewModel, context: Context, onOpenTopic: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    val headerTitle = vm.forumBoardTitle.ifBlank { "Search results" }
    BackHandler(onBack = vm::exitForumTopics)
    // Restore topics scroll position
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.forumTopicsScrollIndex, initialFirstVisibleItemScrollOffset = vm.forumTopicsScrollOffset)
    val openTopic: (ForumTopic) -> Unit = { topic ->
        vm.saveForumTopicsScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onOpenTopic(topic.id, topic.title)
    }
    val scope = rememberCoroutineScope()
    // Item-index alone misses cases where a single tall item (e.g. a long post) is
    // scrolled through without the index ever advancing, so also trigger off pixel offset
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Load more forum topics
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 6) vm.loadMoreForumTopics(context) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::exitForumTopics, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back to Forums", tint = c.ink) }
                    Text(headerTitle, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
                if (vm.forumSubboards.isNotEmpty()) {
                    val subboardListState = rememberLazyListState()
                    LazyRow(state = subboardListState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 15.dp)) {
                        item { FilterChip(selected = vm.forumSubboardId == null, onClick = { vm.openForumSubboard(context, null); scope.centerChip(subboardListState, 0) }, label = { Text("All") }, colors = kikoFilterChipColors()) }
                        itemsIndexed(vm.forumSubboards, key = { _, it -> it.id }) { index, sub -> FilterChip(selected = vm.forumSubboardId == sub.id, onClick = { vm.openForumSubboard(context, sub.id); scope.centerChip(subboardListState, index + 1) }, label = { Text(sub.title) }, colors = kikoFilterChipColors()) }
                    }
                }
                if (vm.forumTopicsLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.forumTopicsError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (!vm.forumTopicsLoading && vm.forumTopics.isEmpty() && vm.forumTopicsError == null) {
                item { Text("No topics found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
            }
            if (vm.forumTopicsLoading && vm.forumTopics.isEmpty()) {
                item { TopicRowSkeletonGroup(6) }
            } else {
                itemsIndexed(vm.forumTopics, key = { _, it -> it.id }) { index, topic ->
                    StaggeredItem(index) {
                        Column {
                            if (vm.forumIsNewsBoard) NewsTopicRow(topic) { openTopic(topic) } else ForumTopicRow(topic) { openTopic(topic) }
                            if (index < vm.forumTopics.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.outlineVariant)
                        }
                    }
                }
            }
            if (vm.forumLoadingMore) {
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
// Forum topic list row

@Composable fun ForumTopicRow(topic: ForumTopic, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().kikoClickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        if (topic.author.avatar.isNotBlank()) {
            AsyncImage(model = topic.author.avatar, contentDescription = topic.author.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(36.dp).clip(kikoCircleShape()).background(c.warm))
        } else {
            Box(Modifier.size(36.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                Text(topic.author.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (topic.isLocked) Icon(Icons.Default.Lock, null, tint = c.muted, modifier = Modifier.size(13.dp).padding(end = 4.dp))
                Text(topic.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("by ${topic.author.name.ifBlank { "Unknown" }} · ${formatForumDate(topic.createdAt)}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            if (topic.lastPostAuthor.name.isNotBlank()) {
                Text("Last reply by ${topic.lastPostAuthor.name} · ${formatForumDate(topic.lastPostAt)}", color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.Default.ChatBubbleOutline, null, tint = c.muted, modifier = Modifier.size(13.dp))
            Text("${topic.postCount}", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
// News Discussion topic row

@Composable fun NewsTopicRow(topic: ForumTopic, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().kikoClickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(width = 84.dp, height = 118.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (topic.imageUrl != null) {
                AsyncImage(model = topic.imageUrl, contentDescription = topic.title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Icon(Icons.Default.Newspaper, null, tint = c.muted, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (topic.isLocked) Icon(Icons.Default.Lock, null, tint = c.muted, modifier = Modifier.size(12.dp).padding(end = 4.dp))
                Text(topic.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text("by ${topic.author.name.ifBlank { "Unknown" }} · ${formatForumDate(topic.createdAt)}", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (topic.lastPostAuthor.name.isNotBlank()) {
                    Text("Last reply by ${topic.lastPostAuthor.name} · ${formatForumDate(topic.lastPostAt)}", color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = c.muted, modifier = Modifier.size(13.dp))
                    Text("${topic.postCount}", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
// Single topic posts screen

@Composable fun ForumTopicScreen(vm: LibraryViewModel, topicId: Int, title: String, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember(topicId) { mutableStateOf<List<ForumPost>>(emptyList()) }
    var poll by remember(topicId) { mutableStateOf<ForumPoll?>(null) }
    var loading by remember(topicId) { mutableStateOf(true) }
    var loadingMore by remember(topicId) { mutableStateOf(false) }
    var hasMore by remember(topicId) { mutableStateOf(false) }
    var error by remember(topicId) { mutableStateOf<String?>(null) }
    // Brand-new topics (posted within the last few hours) can come back with an empty
    // "posts" array for a while even though the topic itself already exists — MAL's forum
    // read-API appears to lag its own website by a bit for the very newest content. That
    // previously showed as a totally blank screen with no explanation. Retrying once after
    // a short delay covers the common case where the lag has already cleared by the time
    // this recomposes; if it's still empty after that, the empty state below at least tells
    // the person what happened instead of leaving the page looking broken.
    LaunchedEffect(topicId) {
        loading = true
        error = null
        var result = runCatching { MalApi(context).forumTopic(topicId) }
        // Empty (but successful) response on the first try — give the API one more chance
        // after a short pause before treating it as genuinely empty.
        if (result.isSuccess && result.getOrNull()?.posts?.isEmpty() == true) {
            kotlinx.coroutines.delay(1500)
            result = runCatching { MalApi(context).forumTopic(topicId) }
        }
        result.onSuccess { posts = it.posts; poll = it.poll; hasMore = it.hasMore; error = null }
            .onFailure { error = it.message ?: "Could not load topic" }
        loading = false
    }
    // Restore per-topic scroll position
    val (initialIndex, initialOffset) = remember(topicId) { vm.forumTopicScrollFor(topicId) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex, initialFirstVisibleItemScrollOffset = initialOffset)
    val goBack = { vm.saveForumTopicScroll(topicId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onBack() }
    BackHandler(onBack = goBack)
    // Item-index alone misses cases where a single tall item (e.g. a long OP post) is
    // scrolled through without the index ever advancing, so also trigger off pixel offset
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Auto-load the next page of replies as the user nears the bottom, instead of
    // requiring a manual tap — matches ForumTopicsScreen's behavior so replies appear
    // as you scroll rather than needing to be requested explicitly
    LaunchedEffect(listState, topicId, hasMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (lastVisible != null && total > 0 && lastVisible >= total - 6 && hasMore && !loadingMore && !loading) {
                    loadingMore = true
                    runCatching { MalApi(context).forumTopic(topicId, offset = posts.size) }
                        .onSuccess { posts = posts + it.posts; hasMore = it.hasMore }
                        .onFailure { hasMore = false }
                    loadingMore = false
                }
            }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = goBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    // Open topic in browser
                    IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/forum/?topicid=$topicId")) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                        Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                }
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                // Distinguishes "still loading" from "MAL's API hasn't returned this brand-new
                // topic's posts yet" — previously both looked identical (an empty screen), which
                // is especially confusing right after tapping into a just-posted news topic.
                if (!loading && error == null && posts.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HourglassEmpty, null, tint = c.muted, modifier = Modifier.size(28.dp))
                        Text("This topic hasn't finished loading on MAL's end yet", color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                        Text("This can happen for very recently posted topics — try again in a bit.", color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = {
                            scope.launch {
                                loading = true
                                runCatching { MalApi(context).forumTopic(topicId) }
                                    .onSuccess { posts = it.posts; poll = it.poll; hasMore = it.hasMore; error = null }
                                    .onFailure { error = it.message ?: "Could not load topic" }
                                loading = false
                            }
                        }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                    }
                }
                poll?.let { ForumPollCard(it, Modifier.padding(top = 6.dp, bottom = 6.dp)) }
            }
            itemsIndexed(posts, key = { _, p -> p.id }) { index, post ->
                StaggeredItem(index) {
                    Column {
                        ForumPostCard(post, isOriginalPost = post.number == 1)
                        if (index < posts.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = c.outlineVariant)
                    }
                }
            }
            if (loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
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
// Full review readout page

@Composable fun ReviewScreen(entry: ReviewEntry, itemTitle: String, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { scrollState.value > 600 } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp).padding(bottom = if (showGoToTop) 90.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                Text(itemTitle, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                if (entry.url.isNotBlank()) {
                    // Open review in browser
                    IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(entry.url)) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                        Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.userImage.isNotBlank()) {
                    AsyncImage(model = entry.userImage, contentDescription = entry.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(40.dp).clip(kikoCircleShape()).background(c.warm))
                } else {
                    Box(Modifier.size(40.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                        Text(entry.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                    }
                }
                Text(entry.username, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                if (entry.score > 0) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    Text(entry.score.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (entry.isSpoiler) Text("Contains spoilers", color = c.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            if (entry.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 14.dp)) {
                    entry.tags.forEach { tag ->
                        val verdict = tag in ReviewVerdictTags
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (verdict) Icon(Icons.Default.Star, null, tint = verdictColor(tag, c), modifier = Modifier.size(13.dp))
                            Text(
                                tag, color = if (verdict) verdictColor(tag, c) else c.muted, fontWeight = if (verdict) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp, modifier = Modifier.padding(start = if (verdict) 4.dp else 0.dp),
                            )
                        }
                    }
                }
            }
            SelectionContainer {
                Text(
                    entry.review, color = c.ink, fontSize = 14.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 28.dp),
                )
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { scrollState.animateScrollTo(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// BBCode tag renderer
sealed class ForumBlock {
    data class Paragraph(val text: AnnotatedString, val center: Boolean = false) : ForumBlock()
    // Tenor flag needs resolving
    data class ImageBlock(val url: String, val resolveTenor: Boolean = false) : ForumBlock()
    data class ListBlock(val items: List<AnnotatedString>, val ordered: Boolean) : ForumBlock()
    // Quote holds nested blocks
    data class Quote(val blocks: List<ForumBlock>) : ForumBlock()
}
sealed class BbToken {
    data class Text(val text: String) : BbToken()
    data class Open(val name: String, val attr: String?) : BbToken()
    data class Close(val name: String) : BbToken()
}

@Composable fun ForumImage(url: String, c: KikoColors, onTap: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // Single source of truth for what a tap does — previously the outer
    // SubcomposeAsyncImage carried its own `.clickable { onTap(url) }` covering
    // the whole box, AND the error state had a second, nested `.clickable` to
    // open the browser. Two overlapping clickable regions on the same spot is
    // unreliable: the outer one was winning, so "tap to open" silently opened
    // the fullscreen ZoomableImageDialog instead — which has no error state of
    // its own, so it just showed a blank black screen. That reads as "nothing
    // happened". Track load state here instead and dispatch a single tap
    // handler based on it.
    var isError by remember(url) { mutableStateOf(false) }
    // Surfaced directly in the UI below so the real failure reason (HTTP status,
    // host/SSL error, decode error, etc.) is visible on-device without needing
    // Logcat/adb. This is what actually found the real bug (a malformed URL from
    // stray nested BBCode tags, fixed in stripBbTags/parseBlocks) — worth keeping
    // around for whatever surfaces next.
    var errorDetail by remember(url) { mutableStateOf<String?>(null) }
    // Back to showing the whole image at its own aspect ratio instead of cropping
    // it into a fixed box. The crop was only ever a (wrong) guess at fixing a load
    // failure that turned out to be a parsing bug, not a sizing one — now that the
    // URL itself is correct, there's no reason to keep cropping.
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        // BBCode [img] tags on MAL forums carry no alt text, so there's nothing more specific
        // to describe this with — but leaving it null means TalkBack skips the image inside a
        // post entirely, as if it weren't there. A generic label at least tells a screen
        // reader user an image exists here instead of leaving a silent gap in the post.
        SubcomposeAsyncImage(
            // See the ImageLoader setup in MainActivity for why this is the one spot in the
            // app that opts back OUT of hardware bitmaps: a post can carry a dozen+ small
            // reaction stickers decoding back-to-back, which can exhaust the GPU-driver-limited
            // hardware bitmap pool and crash natively with no catchable exception.
            model = ImageRequest.Builder(context).data(url).allowHardware(false).build(), contentDescription = "Image", contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    isError = true
                    val t = state.result.throwable
                    errorDetail = "${t::class.simpleName}: ${t.message ?: "no message"}"
                    // Also still logged under tag "ForumImage" for adb/Logcat if that's handy.
                    Log.e("ForumImage", "failed to load $url", t)
                } else if (state is AsyncImagePainter.State.Success) {
                    isError = false
                    errorDetail = null
                }
            },
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 340.dp).clip(RoundedCornerShape(kikoCorner(8.dp)))
                .border(1.dp, c.primary.copy(alpha = .5f), RoundedCornerShape(kikoCorner(8.dp)))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (isError) {
                        // Try the browser custom tab first — this is what every other
                        // "open in browser" spot in the app uses and it's proven more
                        // reliable than LocalUriHandler on some devices/launchers. Only
                        // fall back to the ambient UriHandler, and only surface a toast
                        // if both fail, so a tap never just silently does nothing.
                        val opened = runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }
                        if (opened.isFailure) {
                            Log.e("ForumImage", "couldn't open $url via custom tab", opened.exceptionOrNull())
                            val fallback = runCatching { uriHandler.openUri(url) }
                            if (fallback.isFailure) {
                                Log.e("ForumImage", "couldn't open $url in browser either", fallback.exceptionOrNull())
                                android.widget.Toast.makeText(context, "Couldn't open image link", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        onTap(url)
                    }
                },
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
                is AsyncImagePainter.State.Error -> Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Warning, null, tint = c.muted, modifier = Modifier.size(22.dp))
                    Text("Couldn't load image · tap to open", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    errorDetail?.let {
                        Text(it, color = c.muted.copy(alpha = .7f), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}
// Fullscreen zoomable image viewer

@Composable fun ZoomableImageDialog(url: String, onDismiss: () -> Unit) {
    var scale by remember(url) { mutableStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var isError by remember(url) { mutableStateOf(false) }
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .95f))
                .pointerInput(url) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = newScale
                        offset = if (newScale <= 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Same reasoning as ForumImage: size the decode target off the real
            // screen this dialog fills, rather than a flat number that can end up
            // bigger (or needlessly smaller) than the device's actual screen.
            val targetWidthPx = with(density) { (maxWidth * 0.95f).roundToPx().coerceAtLeast(1) }
            val targetHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
            val request = remember(url, targetWidthPx, targetHeightPx) {
                ImageRequest.Builder(context).data(url).size(targetWidthPx, targetHeightPx).build()
            }
            SubcomposeAsyncImage(
                model = request, contentDescription = "Image, full screen", contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                onState = { state ->
                    isError = state is AsyncImagePainter.State.Error
                    if (state is AsyncImagePainter.State.Error) Log.e("ForumImage", "fullscreen failed to load $url", state.result.throwable)
                },
                modifier = Modifier.fillMaxWidth(0.95f)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                    .pointerInput(url, isError) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!isError) { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }
                            },
                            onTap = {
                                if (isError) {
                                    val opened = runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }
                                    if (opened.isFailure) runCatching { uriHandler.openUri(url) }
                                } else if (scale <= 1f) onDismiss()
                            },
                        )
                    },
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    is AsyncImagePainter.State.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(28.dp))
                        Text("Couldn't load image · tap to open", color = Color.White.copy(alpha = .8f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.White.copy(alpha = .15f)),
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
}
// Render BBCode as column

@Composable fun ForumBody(body: String, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val uriHandler = LocalUriHandler.current
    val blocks = remember(body, c.primary) { parseBBCode(body, c.primary) }
    // Currently open viewer image
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    fullscreenImage?.let { url -> ZoomableImageDialog(url, onDismiss = { fullscreenImage = null }) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block -> ForumBlockView(block, c, uriHandler) { fullscreenImage = it } }
    }
}
// Recursive block rendering helper

@Composable fun ForumBlockView(block: ForumBlock, c: KikoColors, uriHandler: androidx.compose.ui.platform.UriHandler, muted: Boolean = false, onImageTap: (String) -> Unit) {
    when (block) {
        is ForumBlock.Paragraph -> ClickableText(
            text = block.text,
            style = TextStyle(
                color = if (muted) c.muted else c.ink, fontSize = if (muted) 13.sp else 14.sp,
                lineHeight = if (muted) 19.sp else 20.sp, fontStyle = if (muted) FontStyle.Italic else FontStyle.Normal,
                textAlign = if (block.center) TextAlign.Center else TextAlign.Start,
            ),
            modifier = Modifier.fillMaxWidth(),
            onClick = { offset -> block.text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } } },
        )
        // Ignore fixed pixel width
        is ForumBlock.ImageBlock -> {
            // Tenor resolve loading states
            if (block.resolveTenor) {
                var resolved by remember(block.url) { mutableStateOf<String?>(null) }
                var failed by remember(block.url) { mutableStateOf(false) }
                LaunchedEffect(block.url) {
                    val gif = TenorResolver.resolveGifUrl(block.url)
                    if (gif != null) resolved = gif else failed = true
                }
                when {
                    failed -> Text(
                        block.url, color = c.primary, fontSize = 13.sp, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.fillMaxWidth().clickable { runCatching { uriHandler.openUri(block.url) } },
                    )
                    resolved == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.primary, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                    else -> ForumImage(resolved!!, c, onImageTap)
                }
            } else {
                ForumImage(block.url, c, onImageTap)
            }
        }
        is ForumBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(if (block.ordered) "${index + 1}." else "•", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp).width(18.dp))
                    ClickableText(
                        text = item, style = TextStyle(color = c.ink, fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.weight(1f),
                        onClick = { offset -> item.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } } },
                    )
                }
            }
        }
        is ForumBlock.Quote -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(10.dp))).background(c.surfaceContainerHigh)
                .border(androidx.compose.foundation.BorderStroke(3.dp, c.muted.copy(alpha = .35f)), RoundedCornerShape(kikoCorner(10.dp))).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.blocks.forEach { nested -> ForumBlockView(nested, c, uriHandler, muted = true, onImageTap = onImageTap) }
        }
    }
}
// Single topic reply row

@Composable fun ForumPostCard(post: ForumPost, isOriginalPost: Boolean = false) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        if (post.author.avatar.isNotBlank()) {
            AsyncImage(model = post.author.avatar, contentDescription = post.author.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(38.dp).clip(kikoCircleShape()).background(c.warm))
        } else {
            Box(Modifier.size(38.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                Text(post.author.name.take(1).ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.author.name.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
                if (isOriginalPost) {
                    Box(Modifier.padding(start = 8.dp).clip(kikoPillShape()).background(c.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("OP", color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                } else {
                    Text("#${post.number}", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(formatForumDate(post.createdAt), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
            ForumBody(post.body, Modifier.padding(top = 8.dp))
        }
    }
}
// Poll option vote bars

@Composable fun ForumPollCard(poll: ForumPoll, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val totalVotes = poll.options.sumOf { it.votes }.coerceAtLeast(1)
    Card(shape = RoundedCornerShape(kikoCorner(20.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(poll.question, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
            Spacer(Modifier.height(10.dp))
            poll.options.forEach { opt ->
                val fraction = opt.votes.toFloat() / totalVotes
                Column(Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(opt.text, color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                        Text("${opt.votes}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(kikoPillShape()).background(c.surfaceLow)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(kikoPillShape()).background(c.primary))
                    }
                }
            }
            if (poll.closed) Text("Poll closed", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
// Parse forum ISO timestamp

fun formatForumDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(raw)
        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(parsed!!)
    } catch (e: Exception) { raw.take(10) }
}

// Profile section