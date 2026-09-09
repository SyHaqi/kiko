@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Reply
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
import com.kiko.tracker.data.api.ForumBoard
import com.kiko.tracker.data.api.ForumPoll
import com.kiko.tracker.data.api.ForumPost
import com.kiko.tracker.data.api.ForumTopic
import com.kiko.tracker.data.api.ForumTopicDetail
import com.kiko.tracker.data.api.ForumUser
import com.kiko.tracker.data.api.MalApi
import com.kiko.tracker.data.api.MalClub
import com.kiko.tracker.data.api.MalForumReplyApi
import com.kiko.tracker.data.api.MalForumScrapeApi
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.model.CommunityTab
import com.kiko.tracker.data.model.ForumMode
import com.kiko.tracker.data.model.ReviewEntry
import com.kiko.tracker.data.model.ReviewVerdictTags
import com.kiko.tracker.data.model.verdict
import com.kiko.tracker.data.model.verdictColor
import com.kiko.tracker.navigation.PopEnter
import com.kiko.tracker.navigation.PopExit
import com.kiko.tracker.navigation.PushEnter
import com.kiko.tracker.navigation.PushExit
import com.kiko.tracker.ui.components.Avatar
import com.kiko.tracker.ui.components.ExpandableSearchHeader
import com.kiko.tracker.ui.components.MalLoginWebView
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.components.parseBBCode
import com.kiko.tracker.ui.theme.KikoColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.TopicRowSkeletonGroup
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.util.TenorResolver
import com.kiko.tracker.viewmodel.LibraryViewModel

// Combined Community tab —
// same tappable-title dropdown pattern
// Drilling into a specific
// since that's a detail
@Composable fun CommunityScreen(vm: LibraryViewModel, onOpenTopic: (Int, String) -> Unit, onOpenClub: (MalClub) -> Unit) {
    val context = LocalContext.current
    // Only forums need eagerly
    // first composition (see ClubsScreen),
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
    // Search bar starts collapsed
    // (see ExpandableSearchHeader) — expanding
    // of sitting underneath as
    var searchExpanded by remember { mutableStateOf(false) }
    // Restore board list scroll
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.forumBoardsScrollIndex, initialFirstVisibleItemScrollOffset = vm.forumBoardsScrollOffset)
    val saveScroll = { vm.saveForumBoardsScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    PullToRefreshBox(isRefreshing = vm.forumBoardsLoading, onRefresh = { vm.loadForumBoards(context, force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
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
                ) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty(), showUpdateBadge = vm.updateInfo != null) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
        )
    }
}
// Subboard count pill

// MAL gives every forum
// (bullhorn for Announcements, gavel
// see the board-list HTML).
// generic Icons.Default.Forum glyph, which
// scan at a glance.
// per-board distinctiveness while keeping
// before, only the glyph
// (e.g. "?board=5"), which is
// Falls back to the
// future/unlisted boards, like a
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
    7 -> Icons.Default.SportsEsports       // Games, Computers & Tech
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
    // Item-index alone misses cases
    // scrolled through without the
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Load more forum topics
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 6) vm.loadMoreForumTopics(context) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
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

@Composable fun ForumTopicScreen(vm: LibraryViewModel, topicId: Int, title: String, onBack: () -> Unit, onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {}) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember(topicId) { mutableStateOf<List<ForumPost>>(emptyList()) }
    var poll by remember(topicId) { mutableStateOf<ForumPoll?>(null) }
    var loading by remember(topicId) { mutableStateOf(true) }
    var loadingMore by remember(topicId) { mutableStateOf(false) }
    var hasMore by remember(topicId) { mutableStateOf(false) }
    var error by remember(topicId) { mutableStateOf<String?>(null) }
    // Replying needs the website session cookie (MalSessionCookie) — the
    // official API this screen otherwise reads from is GET-only for forums,
    // see MalForumReplyApi's doc comment. Same connect-then-retry shape as
    // FriendsFavoritesScreen.
    val session = remember { MalSessionCookie(context) }
    var connected by remember { mutableStateOf(session.has()) }
    var showLogin by remember { mutableStateOf(false) }
    var verifyingLogin by remember { mutableStateOf(false) }
    var draftText by remember(topicId) { mutableStateOf("") }
    var replyingTo by remember(topicId) { mutableStateOf<ForumPost?>(null) }
    var posting by remember(topicId) { mutableStateOf(false) }
    var postError by remember(topicId) { mutableStateOf<String?>(null) }
    // listState is declared further down (it needs initialIndex/initialOffset computed after
    // this point); sendReply() below wants to scroll to the newly-inserted post once it lands,
    // so it just raises this flag rather than referencing listState directly — a LaunchedEffect
    // near listState's own declaration consumes it.
    var pendingScrollToNewest by remember(topicId) { mutableStateOf(false) }
    // Reads the topic's first page from the website directly rather than MalApi.forumTopic (the
    // official REST API) — see MalForumScrapeApi's doc comment for why: that REST endpoint has
    // shown real staleness on cold loads independent of anything this app does. Falls back to the
    // REST API if the scrape comes back empty (parse/markup mismatch) or throws, so a change to
    // MAL's page markup degrades to the old, reliable behavior rather than breaking the screen.
    suspend fun freshFirstPage(): Result<ForumTopicDetail> {
        val scraped = runCatching { MalForumScrapeApi().topic(topicId) }
        if (scraped.isSuccess && scraped.getOrNull()?.posts?.isNotEmpty() == true) return scraped
        return runCatching { MalApi(context).forumTopic(topicId) }
    }
    // Background reconciliation only, run after the optimistic insert in sendReply() below —
    // NOT what makes the user's own reply appear (that's instant, from client-known data). This
    // exists purely to pick up: (a) MAL's own canonical formatting/id for the post we just
    // optimistically inserted, in case it differs from our guess, and (b) anyone else's replies
    // that landed around the same time. Targets the offset our new post actually lands on
    // (the old last page) rather than offset 0 — a reply is appended to the END of the thread,
    // so refetching page 1 would never contain it once a topic has more than one page, no matter
    // how long we wait. Merges additively and never shrinks/replaces the list: if the read API
    // is still lagged, or errors, the optimistic post (already showing) is left exactly alone.
    fun reconcileAfterReply(myMessageId: Int, postsBeforeReply: Int) {
        scope.launch {
            val targetOffset = (postsBeforeReply - (postsBeforeReply % 30)).coerceAtLeast(0)
            var attempt = 0
            var delayMs = 1000L
            while (attempt < 3) {
                // The scraper only reads page 0 (see MalForumScrapeApi's doc comment) — beyond
                // that, stick with the REST API's own offset paging, already correct.
                val result = if (targetOffset == 0) freshFirstPage()
                else runCatching { MalApi(context).forumTopic(topicId, offset = targetOffset) }
                val fetched = result.getOrNull()
                if (fetched != null) {
                    // Exclude our own optimistic post from "known" on purpose: that's what makes
                    // its real/canonical counterpart always count as "new" the moment MAL actually
                    // has it, rather than being silently skipped because an id already existed
                    // locally. Anything else already on screen (including other users' posts we
                    // picked up on a previous reconcile pass) stays excluded as normal.
                    val known = posts.filterNot { it.id == myMessageId }.map { it.id }.toSet()
                    val newFromServer = fetched.posts.filter { it.id !in known }
                    if (newFromServer.isNotEmpty()) {
                        // Only drop existing entries whose id is about to be replaced by an
                        // incoming one — that's our stand-in once (and only once) its canonical
                        // twin has actually arrived. If it hasn't arrived yet, myMessageId simply
                        // isn't in incomingIds, so the stand-in is left alone, not deleted.
                        val incomingIds = newFromServer.map { it.id }.toSet()
                        // Our own optimistic post already carries the "Reply to X" hint (built
                        // client-side in sendReply()); the REST API's own posts never do (see
                        // ForumPost's doc comment). Carry it over onto our canonical replacement
                        // rather than let it silently disappear the moment reconciliation lands.
                        val optimisticMine = posts.firstOrNull { it.id == myMessageId }
                        val patchedFromServer = newFromServer.map { p ->
                            if (p.id == myMessageId && p.replyToAuthor.isBlank() && optimisticMine?.replyToAuthor?.isNotBlank() == true)
                                p.copy(replyToAuthor = optimisticMine.replyToAuthor, replyToBody = optimisticMine.replyToBody)
                            else p
                        }
                        posts = posts.filterNot { it.id in incomingIds } + patchedFromServer
                        poll = fetched.poll
                        return@launch
                    }
                }
                attempt++
                if (attempt < 3) { kotlinx.coroutines.delay(delayMs); delayMs *= 2 }
            }
            // Gave up reconciling — the optimistic post the user already sees stays put either way.
        }
    }
    fun sendReply() {
        val text = draftText.trim()
        if (text.isBlank() || posting) return
        posting = true
        postError = null
        // Captured before replyingTo is cleared on success below — sendReply() needs it after
        // the reply lands to stamp the optimistic post with the same "Reply to X" hint MAL itself
        // will render for anyone reading the topic on the website.
        val target = replyingTo
        val parentId = target?.id ?: 0
        val postsBeforeReply = posts.size
        scope.launch {
            runCatching { MalForumReplyApi(context).postReply(topicId, text, parentId) }
                .onSuccess { result ->
                    draftText = ""
                    replyingTo = null
                    // Show the user's own reply immediately rather than waiting on a refetch —
                    // built entirely from data already trusted client-side (own profile, the
                    // text just submitted, the id MAL's write response confirmed), not from
                    // guessing at forumTopic()'s read-after-write timing or which page it lands on.
                    val me = vm.malProfile
                    val optimistic = ForumPost(
                        id = result.messageId,
                        number = (posts.lastOrNull()?.number ?: 0) + 1,
                        createdAt = "Just now",
                        author = ForumUser(name = me?.name.orEmpty(), avatar = me?.picture.orEmpty()),
                        body = text,
                        replyToAuthor = target?.author?.name.orEmpty(),
                        replyToBody = target?.let { plainBodyPreview(it.body) }.orEmpty(),
                    )
                    posts = posts + optimistic
                    pendingScrollToNewest = true
                    reconcileAfterReply(result.messageId, postsBeforeReply)
                }
                .onFailure { e ->
                    if (e is MalSessionExpired) { connected = false; session.clear() }
                    else postError = e.message ?: "Could not post reply"
                }
            posting = false
        }
    }
    // Brand-new topics (posted within
    // "posts" array for a
    // read-API appears to lag
    // previously showed as a
    // a short delay covers
    // this recomposes; if it's
    // the person what happened
    LaunchedEffect(topicId) {
        loading = true
        error = null
        var result = freshFirstPage()
        // Empty (but successful) response
        // after a short pause
        if (result.isSuccess && result.getOrNull()?.posts?.isEmpty() == true) {
            kotlinx.coroutines.delay(1500)
            result = freshFirstPage()
        }
        result.onSuccess { posts = it.posts; poll = it.poll; hasMore = it.hasMore; error = null }
            .onFailure { error = it.message ?: "Could not load topic" }
        loading = false
    }
    // Restore per-topic scroll position
    val (initialIndex, initialOffset) = remember(topicId) { vm.forumTopicScrollFor(topicId) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex, initialFirstVisibleItemScrollOffset = initialOffset)
    // Scrolls to the reply the user just sent, once it's actually in `posts` (see
    // pendingScrollToNewest above) — separate effect since sendReply() is declared before
    // listState exists.
    LaunchedEffect(pendingScrollToNewest) {
        if (pendingScrollToNewest) {
            listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            pendingScrollToNewest = false
        }
    }
    val goBack = { vm.saveForumTopicScroll(topicId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onBack() }
    BackHandler(onBack = goBack)
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
        return
    }
    // Dispatches a tapped character/person/company
    // post's body to this
    // equivalent row elsewhere in
    val onOpenProfileLink: (MalProfileLink) -> Unit = { link ->
        when (link) {
            is MalProfileLink.Character -> onOpenCharacter(link.malId)
            is MalProfileLink.Person -> onOpenPerson(link.malId)
            is MalProfileLink.Company -> onOpenCompany(link.malId)
        }
    }
    // Item-index alone misses cases
    // scrolled through without the
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Auto-load the next page
    // requiring a manual tap
    // as you scroll rather
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
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
                item {
                    // Fixed back/open-in-browser row — same non-floating pattern as the other
                    // detail screens (e.g. PersonDetailScreen): plain row at the top of the
                    // scrolling content, no background fade, no overlay.
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = goBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/forum/?topicid=$topicId")) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                            Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp))
                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                    error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                    // Distinguishes "still loading" from
                    // topic's posts yet" —
                    // is especially confusing right
                    if (!loading && error == null && posts.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = c.muted, modifier = Modifier.size(28.dp))
                            Text("This topic hasn't finished loading on MAL's end yet", color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                            Text("This can happen for very recently posted topics — try again in a bit.", color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = {
                                scope.launch {
                                    loading = true
                                    freshFirstPage()
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
                            ForumPostCard(
                                post, isOriginalPost = post.number == 1, onOpenProfileLink = onOpenProfileLink,
                                canReply = connected,
                                onReply = { target ->
                                    replyingTo = target
                                    // Prefill the compose box with "@name " so the reply is
                                    // addressed to whoever's being replied to, same as tapping
                                    // Reply implies on the website. Only auto-insert into an
                                    // empty box — never clobber text the user already typed.
                                    val name = target.author.name.trim()
                                    if (draftText.isBlank() && name.isNotBlank()) draftText = "@$name "
                                },
                            )
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
            )
        }
        ForumReplyBar(
            connected = connected,
            draftText = draftText,
            onDraftChange = { draftText = it },
            replyingTo = replyingTo,
            onCancelReply = {
                // Strip the "@name " prefix back out if it's still exactly what was
                // auto-inserted when Reply was tapped — leaves anything the user typed
                // themselves (before, after, or instead of it) completely alone.
                replyingTo?.author?.name?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                    if (draftText == "@$name ") draftText = ""
                }
                replyingTo = null
            },
            posting = posting,
            error = postError,
            onConnect = { showLogin = true },
            onSend = ::sendReply,
        )
    }
}
// Bottom-pinned compose bar for posting to a topic — mirrors
// FriendsFavoritesScreen's connect-then-retry shape when there's no session
// cookie yet, otherwise a plain text field + send button, with an optional
// "replying to X" chip above it when a specific post was targeted.
@Composable fun ForumReplyBar(
    connected: Boolean,
    draftText: String,
    onDraftChange: (String) -> Unit,
    replyingTo: ForumPost?,
    onCancelReply: () -> Unit,
    posting: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onSend: () -> Unit,
) {
    val c = LocalKikoColors.current
    Surface(color = c.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        if (!connected) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Forum, null, tint = c.muted, modifier = Modifier.size(18.dp))
                Text("Connect your MAL account to reply", color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                TextButton(onClick = onConnect) { Text("Connect") }
            }
            return@Surface
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            replyingTo?.let { target ->
                Row(
                    Modifier.padding(bottom = 8.dp).clip(kikoPillShape()).background(c.surfaceLow).padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Replying to ${target.author.name.ifBlank { "Unknown" }}", color = c.muted, fontSize = 12.sp)
                    IconButton(onClick = onCancelReply, modifier = Modifier.padding(start = 4.dp).size(20.dp)) {
                        Icon(Icons.Default.Close, "Cancel reply", tint = c.muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            error?.let { Text(it, color = c.danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draftText, onValueChange = onDraftChange,
                    placeholder = { Text(if (replyingTo != null) "Write a reply…" else "Write a new post…", color = c.muted, fontSize = 13.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    modifier = Modifier.weight(1f), minLines = 1, maxLines = 3,
                    shape = RoundedCornerShape(kikoCorner(16.dp)),
                    enabled = !posting,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.outlineVariant),
                )
                IconButton(
                    onClick = onSend, enabled = !posting && draftText.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp).size(44.dp).clip(kikoCircleShape())
                        .background(if (draftText.isNotBlank() && !posting) c.primary else c.surfaceLow),
                ) {
                    if (posting) {
                        CircularProgressIndicator(color = c.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (draftText.isNotBlank()) c.onPrimary else c.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
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
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 14.dp).padding(bottom = if (showGoToTop) 90.dp else 24.dp)) {
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
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
    // Single source of truth
    // SubcomposeAsyncImage carried its own
    // the whole box, AND
    // open the browser. Two
    // unreliable: the outer one
    // the fullscreen ZoomableImageDialog instead
    // its own, so it
    // happened". Track load state
    // handler based on it.
    var isError by remember(url) { mutableStateOf(false) }
    // Surfaced directly in the
    // host/SSL error, decode error,
    // Logcat/adb. This is what
    // stray nested BBCode tags,
    // around for whatever surfaces
    var errorDetail by remember(url) { mutableStateOf<String?>(null) }
    // Back to showing the
    // it into a fixed
    // failure that turned out
    // URL itself is correct,
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        // BBCode [img] tags on
        // to describe this with
        // post entirely, as if
        // reader user an image
        SubcomposeAsyncImage(
            // See the ImageLoader setup
            // app that opts back
            // reaction stickers decoding back-to-back,
            // hardware bitmap pool and
            model = ImageRequest.Builder(context).data(url).allowHardware(false).build(), contentDescription = "Image", contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    isError = true
                    val t = state.result.throwable
                    errorDetail = "${t::class.simpleName}: ${t.message ?: "no message"}"
                    // Also still logged under
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
                        // Try the browser custom
                        // "open in browser" spot
                        // reliable than LocalUriHandler on
                        // fall back to the
                        // if both fail, so
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
            // Same reasoning as ForumImage:
            // screen this dialog fills,
            // bigger (or needlessly smaller)
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

@Composable fun ForumBody(body: String, modifier: Modifier = Modifier, onOpenProfileLink: (MalProfileLink) -> Unit = {}) {
    val c = LocalKikoColors.current
    val uriHandler = LocalUriHandler.current
    val blocks = remember(body, c.primary) { parseBBCode(body, c.primary) }
    // Currently open viewer image
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    fullscreenImage?.let { url -> ZoomableImageDialog(url, onDismiss = { fullscreenImage = null }) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block -> ForumBlockView(block, c, uriHandler, onOpenProfileLink = onOpenProfileLink) { fullscreenImage = it } }
    }
}
// A tapped BBCode link:
// (see parseMalProfileLink), same as
// would; anything else falls

private fun openForumLink(url: String, uriHandler: androidx.compose.ui.platform.UriHandler, onOpenProfileLink: (MalProfileLink) -> Unit) {
    val profileLink = parseMalProfileLink(url)
    if (profileLink != null) onOpenProfileLink(profileLink) else runCatching { uriHandler.openUri(url) }
}
// Recursive block rendering helper

@Composable fun ForumBlockView(block: ForumBlock, c: KikoColors, uriHandler: androidx.compose.ui.platform.UriHandler, muted: Boolean = false, onOpenProfileLink: (MalProfileLink) -> Unit = {}, onImageTap: (String) -> Unit) {
    when (block) {
        is ForumBlock.Paragraph -> ClickableText(
            text = block.text,
            style = TextStyle(
                color = if (muted) c.muted else c.ink, fontSize = if (muted) 13.sp else 14.sp,
                lineHeight = if (muted) 19.sp else 20.sp, fontStyle = if (muted) FontStyle.Italic else FontStyle.Normal,
                textAlign = if (block.center) TextAlign.Center else TextAlign.Start,
            ),
            modifier = Modifier.fillMaxWidth(),
            onClick = { offset -> block.text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { openForumLink(it.item, uriHandler, onOpenProfileLink) } },
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
                        onClick = { offset -> item.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { openForumLink(it.item, uriHandler, onOpenProfileLink) } },
                    )
                }
            }
        }
        is ForumBlock.Quote -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(10.dp))).background(c.surfaceContainerHigh)
                .border(androidx.compose.foundation.BorderStroke(3.dp, c.muted.copy(alpha = .35f)), RoundedCornerShape(kikoCorner(10.dp))).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.blocks.forEach { nested -> ForumBlockView(nested, c, uriHandler, muted = true, onOpenProfileLink = onOpenProfileLink, onImageTap = onImageTap) }
        }
    }
}
// Single topic reply row

@Composable fun ForumPostCard(post: ForumPost, isOriginalPost: Boolean = false, onOpenProfileLink: (MalProfileLink) -> Unit = {}, canReply: Boolean = false, onReply: (ForumPost) -> Unit = {}) {
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
            // "Reply to X" hint — same bordered/backed treatment as a quoted BBCode block
            // (ForumBlockView's ForumBlock.Quote case below) so it reads as one visual language,
            // just for MAL's own parent-post reference rather than an inline [quote] tag.
            if (post.replyToAuthor.isNotBlank()) {
                Column(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(kikoCorner(10.dp))).background(c.surfaceContainerHigh)
                        .border(BorderStroke(3.dp, c.muted.copy(alpha = .35f)), RoundedCornerShape(kikoCorner(10.dp))).padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, tint = c.muted, modifier = Modifier.size(12.dp))
                        Text("Reply to ${post.replyToAuthor}", color = c.muted, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (post.replyToBody.isNotBlank()) {
                        Text(
                            post.replyToBody, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, fontStyle = FontStyle.Italic,
                            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            ForumBody(post.body, Modifier.padding(top = 8.dp), onOpenProfileLink = onOpenProfileLink)
            if (canReply) {
                Row(
                    Modifier.padding(top = 8.dp).clip(kikoPillShape()).kikoClickable { onReply(post) }.padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null, tint = c.muted, modifier = Modifier.size(14.dp))
                    Text("Reply", color = c.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
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
// Strips a post's BBCode source down to a short plain-text preview, for the "Reply to X" hint
// stamped onto our own just-sent optimistic post in sendReply() — the target post's real body is
// still bracket-tag BBCode (see MalForumScrapeApi's doc comment) at that point, not the rendered
// text ForumBody would show, so this is a plain best-effort strip rather than real BBCode parsing:
// good enough for a one-line muted quote preview, not meant to survive round-tripping.
private fun plainBodyPreview(bbBody: String, maxLen: Int = 140): String {
    val plain = bbBody.replace(Regex("\\[/?[^\\]]*\\]"), " ").replace(Regex("\\s+"), " ").trim()
    return if (plain.length > maxLen) plain.take(maxLen).trimEnd() + "…" else plain
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