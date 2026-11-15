@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoSectionLoading
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.KikoLoadingRow
import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import android.util.Log
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material3.*
import com.kiko.tracker.ui.components.KikoPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextIndent
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
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.ui.theme.parseHexColor
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
    KikoPullToRefreshBox(isRefreshing = vm.forumBoardsLoading, onRefresh = { vm.loadForumBoards(context, force = true) }, modifier = Modifier.fillMaxSize(), onlyWhenPulled = true) {
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
                    edgeBleed = 14.dp,
                    switchDescription = "Switch between Forums and Clubs",
                ) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty(), showUpdateBadge = vm.updateInfo != null, size = 33.dp, circle = true) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
            }
            if (vm.authChecked && !vm.signedIn) {
                item { Text("Sign in from Profile to browse the MAL forums", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
            } else {
                item {
                    vm.forumBoardsError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                }
                if (vm.forumBoardsLoading && vm.forumCategories.isEmpty()) {
                    item { KikoSectionLoading() }
                }
                // Grouped category board card
                vm.forumCategories.forEachIndexed { categoryIndex, category ->
                    // First label: 9dp so content starts the same distance under the header as on the Seasonal screen.
                    // Later labels keep 22dp as the spacing between category groups.
                    // Same look as the Settings groups (Play Store style): a primary section title, then
                    // separate rows with big outer / tight inner corners and a 2dp gap — no divider lines.
                    item { Text(category.title, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, top = if (categoryIndex == 0) 9.dp else 24.dp, bottom = 10.dp)) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            category.boards.forEachIndexed { index, board ->
                                ForumBoardRow(board, forumGroupShape(index, category.boards.size)) { saveScroll(); vm.openForumBoard(context, board) }
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
// generic Icons.Rounded.Forum glyph, which
// scan at a glance.
// per-board distinctiveness while keeping
// before, only the glyph
// (e.g. "?board=5"), which is
// Falls back to the
// future/unlisted boards, like a
private fun forumBoardIcon(board: ForumBoard) = when (board.id) {
    5 -> Icons.Rounded.Campaign            // Updates & Announcements
    14 -> Icons.Rounded.Gavel              // MAL Guidelines & FAQ
    17 -> Icons.Rounded.EditNote           // DB Modification Requests
    3 -> Icons.Rounded.SupportAgent        // Support
    4 -> Icons.Rounded.Lightbulb           // Suggestions
    13 -> Icons.Rounded.EmojiEvents        // MAL Contests
    15 -> Icons.Rounded.Article            // News Discussion
    16 -> Icons.Rounded.CardGiftcard       // Anime & Manga Recommendations
    19 -> Icons.Rounded.Folder             // Series Discussion
    1 -> Icons.Rounded.Tv                  // Anime Discussion
    2 -> Icons.Rounded.MenuBook            // Manga Discussion
    8 -> Icons.Rounded.ChatBubble          // Introductions
    7 -> Icons.Rounded.SportsEsports       // Games, Computers & Tech
    10 -> Icons.Rounded.MusicNote          // Music & Entertainment
    11 -> Icons.Rounded.LocalCafe          // Casual Discussion
    12 -> Icons.Rounded.PhotoLibrary       // Creative Corner
    9 -> Icons.Rounded.Extension           // Forum Games
    6 -> Icons.Rounded.LocalBar            // Current Events
    else -> Icons.Rounded.Forum
}

// Same corner pattern as Settings' groups (settingsGroupShape in ProfileScreen): 28dp outer, 4dp inner.
fun forumGroupShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 28.dp
    val inner = 4.dp
    if (count == 1) return RoundedCornerShape(outer)
    return when (index) {
        0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable fun ForumBoardRow(board: ForumBoard, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(kikoCorner(20.dp)), onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Icon(Icons.Rounded.ChevronRight, null, tint = c.muted)
    }
}
// Back-to-top floating button

@Composable fun GoToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FloatingActionButton(onClick = onClick, containerColor = c.primary, contentColor = c.onPrimary, modifier = Modifier.size(46.dp)) {
            Icon(Icons.Rounded.KeyboardArrowUp, "Back to top")
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
    Column(Modifier.fillMaxSize()) {
        KikoTopBar(title = headerTitle, onBack = vm::exitForumTopics, progress = { 1f }, backDescription = "Back to Forums")
        Box(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
                    item {
                        if (vm.forumSubboards.isNotEmpty()) {
                            val subboardListState = rememberLazyListState()
                            LazyRow(state = subboardListState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 15.dp)) {
                                item { FilterChip(selected = vm.forumSubboardId == null, onClick = { vm.openForumSubboard(context, null); scope.centerChip(subboardListState, 0) }, label = { Text("All") }, colors = kikoFilterChipColors()) }
                                itemsIndexed(vm.forumSubboards, key = { _, it -> it.id }) { index, sub -> FilterChip(selected = vm.forumSubboardId == sub.id, onClick = { vm.openForumSubboard(context, sub.id); scope.centerChip(subboardListState, index + 1) }, label = { Text(sub.title) }, colors = kikoFilterChipColors()) }
                            }
                        }
                        if (vm.forumTopicsLoading && vm.forumTopics.isNotEmpty()) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary)
                        vm.forumTopicsError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                    }
                    if (!vm.forumTopicsLoading && vm.forumTopics.isEmpty() && vm.forumTopicsError == null) {
                        item { Text("No topics found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                    }
                    if (vm.forumTopicsLoading && vm.forumTopics.isEmpty()) {
                        item { KikoSectionLoading() }
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
                        item { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 28.dp) } }
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
                if (topic.isLocked) Icon(Icons.Rounded.Lock, null, tint = c.muted, modifier = Modifier.size(13.dp).padding(end = 4.dp))
                Text(topic.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("by ${topic.author.name.ifBlank { "Unknown" }} · ${formatForumDate(topic.createdAt)}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            if (topic.lastPostAuthor.name.isNotBlank()) {
                Text("Last reply by ${topic.lastPostAuthor.name} · ${formatForumDate(topic.lastPostAt)}", color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.Rounded.ChatBubbleOutline, null, tint = c.muted, modifier = Modifier.size(13.dp))
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
                Icon(Icons.Rounded.Newspaper, null, tint = c.muted, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (topic.isLocked) Icon(Icons.Rounded.Lock, null, tint = c.muted, modifier = Modifier.size(12.dp).padding(end = 4.dp))
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
                    Icon(Icons.Rounded.ChatBubbleOutline, null, tint = c.muted, modifier = Modifier.size(13.dp))
                    Text("${topic.postCount}", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
// Single topic posts screen

@Composable fun ForumTopicScreen(vm: LibraryViewModel, topicId: Int, title: String, onBack: () -> Unit, onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {}, jumpToPostId: Int? = null) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Set when opened from a notification that points at one post (a quote, a mention, ...): the list
    // pages in until that post is loaded, scrolls to it and flashes it briefly.
    var jumping by remember(topicId, jumpToPostId) { mutableStateOf(jumpToPostId != null) }
    var jumpFailed by remember(topicId, jumpToPostId) { mutableStateOf(false) }
    var highlightedPostId by remember(topicId, jumpToPostId) { mutableStateOf<Int?>(null) }
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
    // TextFieldValue (not String) so the formatting toolbar can read/replace the selection.
    var draft by remember(topicId) { mutableStateOf(TextFieldValue("")) }
    var replyingTo by remember(topicId) { mutableStateOf<ForumPost?>(null) }
    var posting by remember(topicId) { mutableStateOf(false) }
    var postError by remember(topicId) { mutableStateOf<String?>(null) }
    // listState is declared further down (it needs initialIndex/initialOffset computed after
    // this point); sendReply() below wants to scroll to the newly-inserted post once it lands,
    // so it just raises this flag rather than referencing listState directly — a LaunchedEffect
    // near listState's own declaration consumes it.
    var pendingScrollToNewest by remember(topicId) { mutableStateOf(false) }
    // Raised when Reply is tapped on a post: the reply bar is pinned at the bottom, so just
    // focus its text field (which brings up the keyboard).
    var pendingScrollToComposer by remember(topicId) { mutableStateOf(false) }
    val replyFocusRequester = remember { FocusRequester() }
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
        val text = draft.text.trim()
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
                    draft = TextFieldValue("")
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
    // Messenger-style: the keyboard shrinks the list from the bottom, so scroll by the same amount
    // to keep what you were reading in view (instead of the list being clipped like a sheet).
    val density = LocalDensity.current
    val imeRise = WindowInsets.ime.exclude(WindowInsets.navigationBars)
    LaunchedEffect(listState) {
        var last = imeRise.getBottom(density)
        snapshotFlow { imeRise.getBottom(density) }.collect { now ->
            val delta = now - last
            last = now
            if (delta != 0) listState.dispatchRawDelta(delta.toFloat())
        }
    }
    // Scrolls to the reply the user just sent, once it's actually in `posts` (see
    // pendingScrollToNewest above) — separate effect since sendReply() is declared before
    // listState exists.
    LaunchedEffect(pendingScrollToNewest) {
        if (pendingScrollToNewest) {
            // Item 0 is the header, posts follow — so the newest post is at index posts.size.
            listState.animateScrollToItem(posts.size.coerceAtLeast(0))
            pendingScrollToNewest = false
        }
    }
    LaunchedEffect(pendingScrollToComposer) {
        if (pendingScrollToComposer) {
            runCatching { replyFocusRequester.requestFocus() }
            pendingScrollToComposer = false
        }
    }
    // Jump to a notification's post. Pages through the topic (same REST paging as the auto-load below,
    // sharing its loadingMore flag so the two never fetch at once) until the post shows up, then scrolls
    // to it and highlights it for a moment. Gives up quietly if it's gone (deleted) or past 3000 posts.
    LaunchedEffect(topicId, jumpToPostId, loading) {
        val target = jumpToPostId ?: return@LaunchedEffect
        if (loading || !jumping) return@LaunchedEffect
        if (posts.isEmpty()) { jumping = false; jumpFailed = true; return@LaunchedEffect }
        while (true) {
            val index = posts.indexOfFirst { it.id == target }
            if (index >= 0) {
                listState.scrollToItem(index + 1) // item 0 is the header
                highlightedPostId = target
                jumping = false
                kotlinx.coroutines.delay(2500)
                highlightedPostId = null
                return@LaunchedEffect
            }
            if (!hasMore || posts.size >= 3000) { jumping = false; jumpFailed = true; return@LaunchedEffect }
            while (loadingMore) kotlinx.coroutines.delay(50)
            if (posts.any { it.id == target }) continue // the auto-load just brought it in
            loadingMore = true
            runCatching { MalApi(context).forumTopic(topicId, limit = 100, offset = posts.size) }
                .onSuccess { page -> val have = posts.mapTo(HashSet()) { it.id }; posts = posts + page.posts.filter { it.id !in have }; hasMore = page.hasMore && page.posts.isNotEmpty() }
                .onFailure { hasMore = false }
            loadingMore = false
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
                        KikoLoadingIndicator(color = c.primary)
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
    // Jetchat-style: the screen itself is NOT inset by the keyboard (that's the filter-sheet
    // behaviour). Only the reply bar takes imePadding(), and the list rides up with it (see the
    // ime effect above), like a messaging app.
    Column(Modifier.fillMaxSize()) {
        val titleProgress = rememberTitleProgress(listState)
        KikoTopBar(title = title, onBack = goBack, progress = { titleProgress.value }) {
            ShareMoreMenu(title = title, shareUrl = "https://myanimelist.net/forum/?topicid=$topicId")
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    val starter = posts.firstOrNull()
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
                        if (starter != null && starter.author.name.isNotBlank()) {
                            Text(
                                "Started by ${starter.author.name} · ${formatForumDate(starter.createdAt)}",
                                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (loading || jumping) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = c.primary)
                        if (jumpFailed) Text("Couldn't find that post — it may have been deleted.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                        // Distinguishes "still loading" from "this topic really has no posts yet" — brand-new
                        // topics can briefly come back empty from MAL's read side.
                        if (!loading && error == null && posts.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.HourglassEmpty, null, tint = c.muted, modifier = Modifier.size(28.dp))
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
                    }
                    poll?.let { ForumPollCard(it, Modifier.padding(top = 6.dp)) }
                }
                itemsIndexed(posts, key = { _, p -> p.id }) { index, post ->
                    StaggeredItem(index) {
                        Column {
                            // Small section label between the opening post and the replies under it.
                            if (index == 1 && posts.firstOrNull()?.number == 1) {
                                Text("Replies", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 10.dp))
                            }
                            ForumPostCard(
                                post, isOriginalPost = post.number == 1, highlighted = highlightedPostId == post.id,
                                onOpenProfileLink = onOpenProfileLink,
                                canReply = connected,
                                onReply = { target ->
                                    replyingTo = target
                                    // Prefill the compose box with "@name " so the reply is
                                    // addressed to whoever's being replied to, same as tapping
                                    // Reply implies on the website. Only auto-insert into an
                                    // empty box — never clobber text the user already typed.
                                    val name = target.author.name.trim()
                                    if (draft.text.isBlank() && name.isNotBlank()) {
                                        val prefill = "@$name "
                                        draft = TextFieldValue(prefill, TextRange(prefill.length))
                                    }
                                    pendingScrollToComposer = true
                                },
                                onQuote = { target ->
                                    replyingTo = target
                                    // MAL's own quote syntax; the quoted text is a plain-text excerpt so
                                    // formatting or images inside the original can't leak in half-open.
                                    val quote = "[quote=${target.author.name.trim()} message=${target.id}]${quotePreview(target.body)}[/quote]\n"
                                    val base = draft.text
                                    val merged = if (base.isBlank()) quote else base.trimEnd() + "\n" + quote
                                    draft = TextFieldValue(merged, TextRange(merged.length))
                                    pendingScrollToComposer = true
                                },
                            )
                        }
                    }
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            KikoLoadingIndicator(color = c.primary, size = 28.dp)
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
        // Reply bar pinned under the list like a messaging app's compose bar. It alone takes the
        // keyboard inset. The nav-bar inset is consumed first because the root Scaffold already
        // pads for it — otherwise imePadding() would count it twice and leave a gap.
        if (!loading && posts.isNotEmpty()) {
            ForumReplyBar(
                modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars).imePadding(),
                connected = connected,
                draft = draft,
                onDraftChange = { draft = it },
                replyingTo = replyingTo,
                onCancelReply = {
                    // Strip the "@name " prefix back out if it's still exactly what was
                    // auto-inserted when Reply was tapped — leaves anything the user typed
                    // themselves (before, after, or instead of it) completely alone.
                    replyingTo?.author?.name?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                        if (draft.text == "@$name ") draft = TextFieldValue("")
                    }
                    replyingTo = null
                },
                posting = posting,
                error = postError,
                onConnect = { showLogin = true },
                onSend = ::sendReply,
                focusRequester = replyFocusRequester,
            )
        }
    }
}
// ---------------------------------------------------------------------------
// Reply composer
//
// Pinned under the thread like a messaging app's compose bar. A "format" button opens a
// toolbar that wraps the selection in MAL BBCode (bold, italic, underline, strike, link, image,
// YouTube, quote, spoiler, lists, alignment, colour, size) and a preview toggle shows the draft
// rendered exactly as it will look in the thread.
// ---------------------------------------------------------------------------

// Replaces the current selection with open + selection + close. With nothing selected it inserts
// open + placeholder + close and selects the placeholder so typing replaces it.
private fun TextFieldValue.surround(open: String, close: String, placeholder: String): TextFieldValue {
    val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
    val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    val inner = if (start == end) placeholder else text.substring(start, end)
    val newText = text.substring(0, start) + open + inner + close + text.substring(end)
    val innerStart = start + open.length
    return copy(text = newText, selection = TextRange(innerStart, innerStart + inner.length), composition = null)
}

// [url=https://]text[/url] with the "https://" part selected, ready to be replaced by a pasted link.
private fun TextFieldValue.insertLink(): TextFieldValue {
    val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
    val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    val label = if (start == end) "link text" else text.substring(start, end)
    val open = "[url="
    val href = "https://"
    val newText = text.substring(0, start) + open + href + "]" + label + "[/url]" + text.substring(end)
    return copy(text = newText, selection = TextRange(start + open.length, start + open.length + href.length), composition = null)
}

// [list] / [list=1] with one [*] per selected line (or two empty items when nothing is selected).
private fun TextFieldValue.insertList(ordered: Boolean): TextFieldValue {
    val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
    val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    val open = if (ordered) "[list=1]\n" else "[list]\n"
    val body = if (start == end) "[*]item\n[*]item" else text.substring(start, end).lines().filter { it.isNotBlank() }.joinToString("\n") { "[*]${it.trim()}" }
    val close = "\n[/list]"
    val newText = text.substring(0, start) + open + body + close + text.substring(end)
    val cursor = start + open.length + body.length + close.length
    return copy(text = newText, selection = TextRange(cursor), composition = null)
}

private val FormatColors = listOf(
    "Red" to "#E53935", "Orange" to "#FB8C00", "Yellow" to "#FDD835", "Green" to "#43A047",
    "Blue" to "#1E88E5", "Purple" to "#8E24AA", "Pink" to "#EC407A", "Gray" to "#757575",
)
private val FormatSizes = listOf(25, 50, 75, 100, 125, 150, 175, 200).map { "$it%" to it }

@Composable private fun FormatButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, active: Boolean = false, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Box(
        Modifier.size(40.dp).clip(kikoCircleShape()).background(if (active) c.secondaryContainer else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) c.onSecondaryContainer else c.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable private fun FormatToolbar(draft: TextFieldValue, onDraftChange: (TextFieldValue) -> Unit, previewing: Boolean, onTogglePreview: () -> Unit) {
    val c = LocalKikoColors.current
    var colorMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        FormatButton(Icons.Rounded.Visibility, "Preview", active = previewing, onClick = onTogglePreview)
        Spacer(Modifier.width(4.dp))
        FormatButton(Icons.Rounded.FormatBold, "Bold") { onDraftChange(draft.surround("[b]", "[/b]", "bold")) }
        FormatButton(Icons.Rounded.FormatItalic, "Italic") { onDraftChange(draft.surround("[i]", "[/i]", "italic")) }
        FormatButton(Icons.Rounded.FormatUnderlined, "Underline") { onDraftChange(draft.surround("[u]", "[/u]", "underline")) }
        FormatButton(Icons.Rounded.StrikethroughS, "Strikethrough") { onDraftChange(draft.surround("[s]", "[/s]", "strikethrough")) }
        Box {
            FormatButton(Icons.Rounded.FormatColorText, "Text colour") { colorMenu = true }
            DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                FormatColors.forEach { (name, hex) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        leadingIcon = { Box(Modifier.size(16.dp).clip(kikoCircleShape()).background(parseHexColor(hex) ?: c.primary)) },
                        onClick = { colorMenu = false; onDraftChange(draft.surround("[color=$hex]", "[/color]", "colored text")) },
                    )
                }
            }
        }
        Box {
            FormatButton(Icons.Rounded.FormatSize, "Text size") { sizeMenu = true }
            DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                FormatSizes.forEach { (name, percent) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { sizeMenu = false; onDraftChange(draft.surround("[size=$percent]", "[/size]", "sized text")) },
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        FormatButton(Icons.Rounded.Link, "Link") { onDraftChange(draft.insertLink()) }
        FormatButton(Icons.Rounded.Image, "Image") { onDraftChange(draft.surround("[img]", "[/img]", "https://")) }
        FormatButton(Icons.Rounded.PlayCircle, "YouTube video") { onDraftChange(draft.surround("[yt]", "[/yt]", "video id")) }
        Spacer(Modifier.width(4.dp))
        FormatButton(Icons.Rounded.FormatQuote, "Quote") { onDraftChange(draft.surround("[quote]", "[/quote]", "quote")) }
        FormatButton(Icons.Rounded.VisibilityOff, "Spoiler") { onDraftChange(draft.surround("[spoiler]", "[/spoiler]", "spoiler text")) }
        FormatButton(Icons.Rounded.FormatListBulleted, "Bulleted list") { onDraftChange(draft.insertList(ordered = false)) }
        FormatButton(Icons.Rounded.FormatListNumbered, "Numbered list") { onDraftChange(draft.insertList(ordered = true)) }
        FormatButton(Icons.Rounded.FormatAlignCenter, "Center") { onDraftChange(draft.surround("[center]", "[/center]", "centered text")) }
        FormatButton(Icons.Rounded.FormatAlignRight, "Align right") { onDraftChange(draft.surround("[right]", "[/right]", "right-aligned text")) }
    }
}

@Composable fun ForumReplyBar(
    modifier: Modifier = Modifier,
    connected: Boolean,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    replyingTo: ForumPost?,
    onCancelReply: () -> Unit,
    posting: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val c = LocalKikoColors.current
    // Drop the text field's focus (cursor) as soon as the keyboard goes away,
    // e.g. after the user dismisses it with the back gesture.
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) focusManager.clearFocus()
        imeWasVisible = imeVisible
    }
    var showTools by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    // A tonal sheet with rounded top corners instead of a hairline: reads as its own layer above the thread.
    Surface(
        color = c.surfaceContainer,
        shape = RoundedCornerShape(topStart = kikoCorner(28.dp), topEnd = kikoCorner(28.dp)),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (!connected) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Forum, null, tint = c.muted, modifier = Modifier.size(20.dp))
                Text("Connect your MAL account to reply", color = c.muted, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                FilledTonalButton(onClick = onConnect) { Text("Connect") }
            }
            return@Surface
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp, bottom = 8.dp)) {
            replyingTo?.let { target ->
                Row(
                    Modifier.padding(start = 4.dp, bottom = 8.dp).clip(kikoPillShape()).background(c.primaryContainer).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = c.onPrimaryContainer, modifier = Modifier.size(14.dp))
                    Text("Replying to ${target.author.name.ifBlank { "Unknown" }}", color = c.onPrimaryContainer, fontWeight = FontWeight.Medium, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                    IconButton(onClick = onCancelReply, modifier = Modifier.padding(start = 2.dp).size(24.dp)) {
                        Icon(Icons.Rounded.Close, "Cancel reply", tint = c.onPrimaryContainer, modifier = Modifier.size(14.dp))
                    }
                }
            }
            error?.let { Text(it, color = c.danger, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) }
            AnimatedVisibility(visible = showTools && previewing && draft.text.isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(max = 240.dp).clip(RoundedCornerShape(kikoCorner(20.dp)))
                        .background(c.surfaceContainerLow).verticalScroll(rememberScrollState()).padding(14.dp),
                ) {
                    ForumBody(draft.text, background = c.surfaceContainerLow)
                }
            }
            AnimatedVisibility(visible = showTools) {
                FormatToolbar(draft, onDraftChange, previewing) { previewing = !previewing }
            }
            val canSend = !posting && draft.text.isNotBlank()
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    FormatButton(Icons.Rounded.TextFormat, if (showTools) "Hide formatting" else "Show formatting", active = showTools) {
                        showTools = !showTools
                        if (!showTools) previewing = false
                    }
                }
                // Compose pill: one rounded container holding a field that grows from one line up to six
                // (then scrolls) with the send button at its right, bottom-aligned so it stays put as the field gets taller.
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(kikoCorner(26.dp))).background(c.surfaceContainerHighest).padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = !posting,
                        minLines = 1, maxLines = 6,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 20.sp, color = c.ink),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        decorationBox = { inner ->
                            // Min height = the 40dp send button, text centred in it, so one line sits level with
                            // the button and the pill has the same 4dp gap on top/bottom/end. Taller (multi-line)
                            // text grows the box instead; the button stays bottom-aligned.
                            Box(Modifier.heightIn(min = 40.dp).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                if (draft.text.isEmpty()) Text(if (replyingTo != null) "Write a reply…" else "Write a new post…", color = c.muted, fontSize = 15.sp, lineHeight = 20.sp)
                                inner()
                            }
                        },
                    )
                    // Plain Box instead of IconButton so the 40dp circle isn't padded up to 48dp.
                    Box(
                        Modifier.padding(start = 6.dp).size(40.dp)
                            .clip(kikoCircleShape())
                            .background(if (canSend) c.primary else c.surfaceLow)
                            .clickable(enabled = canSend, onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (posting) {
                            KikoLoadingIndicator(color = c.onPrimary, size = 24.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (canSend) c.onPrimary else c.muted, modifier = Modifier.size(18.dp))
                        }
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = KikoHeader.BackTop, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Rounded.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text(itemTitle, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).headerTitleStart())
            if (entry.url.isNotBlank()) {
                // Share / open the review in the browser (3-dot menu)
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides c.ink) {
                    Box(Modifier.headerEdgeEnd()) { ShareMoreMenu(title = itemTitle, shareUrl = entry.url) }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 14.dp).padding(bottom = if (showGoToTop) 90.dp else 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.userImage.isNotBlank()) {
                            AsyncImage(model = entry.userImage, contentDescription = entry.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(40.dp).clip(kikoCircleShape()).background(c.warm))
                        } else {
                            Box(Modifier.size(40.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                                Text(entry.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                            }
                        }
                        Text(entry.username, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        if (entry.score > 0) ReviewScoreChip(entry.score)
                    }
                    if (entry.isSpoiler || entry.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                            if (entry.isSpoiler) SpoilerChip(showLabel = true)
                            entry.tags.forEach { tag -> if (tag in ReviewVerdictTags) VerdictChip(tag, showLabel = true) else ReviewTagChip(tag) }
                        }
                    }
                    SelectionContainer {
                        Text(
                            entry.review, color = c.ink, fontSize = 14.sp, lineHeight = 22.sp,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                    }
                    // MAL reactions (top emojis + total), same readout as the review cards.
                    ReviewReactions(entry, Modifier.padding(top = 18.dp), emojiSize = 15.sp, countSize = 13.sp)
                    Spacer(Modifier.height(28.dp))
                }
                GoToTopButton(
                    visible = showGoToTop,
                    onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
                )
            }
        }
    }
}
// BBCode tag renderer
sealed class ForumBlock {
    // A run of styled text. `scale` is the largest [size] multiplier inside it (so the line height can follow).
    data class Paragraph(val text: AnnotatedString, val align: TextAlign = TextAlign.Start, val scale: Float = 1f) : ForumBlock()
    // Tenor flag needs resolving; widthDp is an optional "[img=120]" / "[img width=120]" size hint.
    data class ImageBlock(val url: String, val resolveTenor: Boolean = false, val widthDp: Int? = null) : ForumBlock()
    // Each item is itself a list of blocks, so items can hold images and nested lists.
    // kind: "1" numbers, "a"/"A" letters, "i"/"I" roman numerals (only used when ordered).
    data class ListBlock(val items: List<List<ForumBlock>>, val ordered: Boolean, val kind: String = "1") : ForumBlock()
    // Quote holds nested blocks; author comes from [quote=Name] (message ids are stripped).
    data class Quote(val blocks: List<ForumBlock>, val author: String = "") : ForumBlock()
    // [spoiler] / [spoiler=Label]: collapsed until tapped.
    data class Spoiler(val label: String, val blocks: List<ForumBlock>) : ForumBlock()
    // [code]: shown literally in a monospace, horizontally scrollable box.
    data class Code(val text: String) : ForumBlock()
    // [hr]
    object Rule : ForumBlock()
    // YouTube embed ([yt]ID[/yt], or MAL's <iframe class="movie youtube">) — drawn as a tappable
    // thumbnail that opens the video in the YouTube app / browser (see ForumVideo).
    data class VideoBlock(val videoId: String) : ForumBlock()
}
sealed class BbToken {
    data class Text(val text: String) : BbToken()
    data class Open(val name: String, val attr: String?) : BbToken()
    data class Close(val name: String) : BbToken()
}

@Composable fun ForumImage(url: String, c: KikoColors, onTap: (String) -> Unit, widthHint: androidx.compose.ui.unit.Dp? = null) {
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
    // The loaded image's width/height ratio. Once known, the frame (border + rounded clip) is sized to
    // exactly that ratio so it hugs the image instead of sitting at a fixed 90%-wide box with empty
    // bars inside it (which happened for tall/narrow images capped at 340dp).
    var aspect by remember(url) { mutableStateOf<Float?>(null) }
    // Back to showing the
    // it into a fixed
    // failure that turned out
    // URL itself is correct,
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val maxFrameHeight = 340.dp
            val ratio = aspect
            // "[img=120]"-style size hints (emoticons, stickers) keep their small size instead of
            // being stretched to the full post width.
            val availableWidth = if (widthHint != null && widthHint < maxWidth) widthHint else maxWidth
            val frameModifier = if (ratio != null) {
                // Fill the available width, but never taller than maxFrameHeight — if it would be,
                // shrink the WIDTH too so the frame stays the image's exact shape.
                var w = availableWidth
                var h = w / ratio
                if (h > maxFrameHeight) { h = maxFrameHeight; w = maxFrameHeight * ratio }
                Modifier.size(w, h)
            } else Modifier.width(availableWidth).heightIn(max = maxFrameHeight) // still loading / unknown size
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
                        val sz = state.painter.intrinsicSize
                        if (sz.width.isFinite() && sz.height.isFinite() && sz.width > 0f && sz.height > 0f) aspect = sz.width / sz.height
                    }
                },
                modifier = frameModifier.clip(RoundedCornerShape(kikoCorner(16.dp)))
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
                        KikoLoadingIndicator(color = c.primary, size = 32.dp)
                    }
                    is AsyncImagePainter.State.Error -> Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = c.muted, modifier = Modifier.size(22.dp))
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
}
// YouTube embed placeholder: thumbnail + play button, tap opens the video externally. A real
// in-app player isn't embedded on purpose — a WebView per post would be heavy in a scrolling list.
@Composable fun ForumVideo(videoId: String, c: KikoColors) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // maxresdefault only exists for HD uploads (404s otherwise); hqdefault always exists but is
    // 4:3 with black bars baked in, which the 16:9 Crop below trims off exactly.
    val thumbs = remember(videoId) {
        listOf("https://img.youtube.com/vi/$videoId/maxresdefault.jpg", "https://img.youtube.com/vi/$videoId/hqdefault.jpg")
    }
    var thumbIndex by remember(videoId) { mutableStateOf(0) }
    val shape = RoundedCornerShape(kikoCorner(16.dp))
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(Color.Black)
                .clickable {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    if (runCatching { uriHandler.openUri(url) }.isFailure) {
                        android.widget.Toast.makeText(context, "Couldn't open video link", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
        ) {
            AsyncImage(
                model = thumbs[thumbIndex], contentDescription = "Video thumbnail",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onError = { if (thumbIndex < thumbs.lastIndex) thumbIndex++ },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.align(Alignment.Center).size(56.dp).clip(kikoCircleShape()).background(Color.Black.copy(alpha = .6f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play video on YouTube", tint = Color.White, modifier = Modifier.size(36.dp)) }
            Text(
                "Watch on YouTube", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .clip(kikoPillShape()).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 9.dp, vertical = 3.dp),
            )
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
                    is AsyncImagePainter.State.Loading -> KikoLoadingIndicator(color = Color.White, size = 40.dp)
                    is AsyncImagePainter.State.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Warning, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(28.dp))
                        Text("Couldn't load image · tap to open", color = Color.White.copy(alpha = .8f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp).clip(kikoCircleShape()).background(Color.White.copy(alpha = .15f)),
            ) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
        }
    }
}
// Render BBCode as a column of blocks

@Composable fun ForumBody(
    body: String,
    modifier: Modifier = Modifier,
    onOpenProfileLink: (MalProfileLink) -> Unit = {},
    // The surface the text sits on: only used to drop [color] tags that would be unreadable there.
    background: Color = LocalKikoColors.current.surfaceContainerLow,
    // Forum posts use compact 15sp text; the article reader passes larger, airier values.
    textSize: Float = 15f,
    lineHeight: Float = 1.45f,
    blockSpacing: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val c = LocalKikoColors.current
    val uriHandler = LocalUriHandler.current
    val blocks = remember(body, c.primary, background) { parseBBCode(body, c.primary, background) }
    // Currently open viewer image
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    fullscreenImage?.let { url -> ZoomableImageDialog(url, onDismiss = { fullscreenImage = null }) }
    androidx.compose.runtime.CompositionLocalProvider(LocalForumTypography provides ForumTypography(textSize, lineHeight)) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(blockSpacing)) {
            blocks.forEach { block ->
                // Headings (a [size] bigger than body text) get extra room above so they bind to what follows.
                val isHeading = block is ForumBlock.Paragraph && block.scale > 1.1f
                Box(if (isHeading) Modifier.padding(top = 6.dp) else Modifier) {
                    ForumBlockView(block, c, uriHandler, onOpenProfileLink = onOpenProfileLink) { fullscreenImage = it }
                }
            }
        }
    }
}

internal data class ForumTypography(val size: Float = 15f, val lineHeight: Float = 1.45f)
internal val LocalForumTypography = androidx.compose.runtime.compositionLocalOf { ForumTypography() }

// A run of short hard-broken lines (a cast list, credits, "Label: value" rows) reads as a list, but a
// wrapped line looks identical to a new item. Indenting wrapped continuation lines makes each item
// distinct without adding bullets the author didn't write.
private fun withHangingIndent(text: AnnotatedString): AnnotatedString {
    val lines = text.text.split('\n')
    if (lines.size < 3 || lines.any { it.length > 160 }) return text
    return buildAnnotatedString {
        append(text)
        var start = 0
        lines.forEachIndexed { i, line ->
            val end = if (i < lines.lastIndex) start + line.length + 1 else start + line.length
            if (end > start) addStyle(ParagraphStyle(textIndent = TextIndent(restLine = 14.sp)), start, end)
            start = end
        }
    }
}

// A tapped BBCode link: character/person/company pages open in-app (see parseMalProfileLink),
// anything else goes to the browser.
private fun openForumLink(url: String, uriHandler: androidx.compose.ui.platform.UriHandler, onOpenProfileLink: (MalProfileLink) -> Unit) {
    val profileLink = parseMalProfileLink(url)
    if (profileLink != null) onOpenProfileLink(profileLink) else runCatching { uriHandler.openUri(url) }
}

private fun alphaMarker(n: Int, upper: Boolean): String {
    val letter = 'a' + ((n - 1) % 26)
    val s = letter.toString().repeat((n - 1) / 26 + 1)
    return if (upper) s.uppercase() else s
}

private fun romanMarker(n: Int): String {
    val numerals = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    var rest = n.coerceIn(1, 3999)
    val sb = StringBuilder()
    for ((value, symbol) in numerals) while (rest >= value) { sb.append(symbol); rest -= value }
    return sb.toString()
}

private fun listMarker(block: ForumBlock.ListBlock, index: Int): String {
    if (!block.ordered) return "•"
    val n = index + 1
    return when (block.kind) {
        "a" -> alphaMarker(n, false) + "."
        "A" -> alphaMarker(n, true) + "."
        "i" -> romanMarker(n).lowercase() + "."
        "I" -> romanMarker(n) + "."
        else -> "$n."
    }
}

// Shared "quoted text" container: tinted rounded surface with an accent bar on the left. Tinting
// with the primary colour (instead of a fixed surface tone) keeps it readable on any card colour.
// The bar is drawn rather than measured, because a quote can hold images (SubcomposeLayout), which
// can't answer the intrinsic-size queries a Row + fillMaxHeight bar would need.
@Composable fun ForumQuoteFrame(c: KikoColors, depth: Int = 0, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val bar = c.primary.copy(alpha = 0.55f)
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(16.dp)))
            .background(c.primary.copy(alpha = if (depth == 0) 0.08f else 0.12f))
            .drawBehind { drawRect(bar, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable private fun ForumSpoilerView(
    block: ForumBlock.Spoiler, c: KikoColors, uriHandler: androidx.compose.ui.platform.UriHandler, quoteDepth: Int,
    onOpenProfileLink: (MalProfileLink) -> Unit, onImageTap: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.ink.copy(alpha = 0.06f))) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (open) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null, tint = c.muted, modifier = Modifier.size(18.dp))
            Text(
                block.label.ifBlank { "Spoiler" }, color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Text(if (open) "Hide" else "Show", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        // Content stays out of composition until opened, so images inside a spoiler aren't fetched early.
        AnimatedVisibility(visible = open) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.blocks.forEach { nested -> ForumBlockView(nested, c, uriHandler, quoteDepth, onOpenProfileLink, onImageTap) }
            }
        }
    }
}

// Recursive block rendering helper

@Composable fun ForumBlockView(
    block: ForumBlock, c: KikoColors, uriHandler: androidx.compose.ui.platform.UriHandler,
    quoteDepth: Int = 0, onOpenProfileLink: (MalProfileLink) -> Unit = {}, onImageTap: (String) -> Unit,
) {
    when (block) {
        is ForumBlock.Paragraph -> {
            val inQuote = quoteDepth > 0
            val typo = LocalForumTypography.current
            val base = if (inQuote) typo.size - 1f else typo.size
            val shown = remember(block.text, block.align) { if (block.align == TextAlign.Start) withHangingIndent(block.text) else block.text }
            ClickableText(
                text = shown,
                style = TextStyle(
                    color = if (inQuote) c.muted else c.ink, fontSize = base.sp,
                    // Line height follows the biggest [size] in the paragraph so large text doesn't overlap.
                    lineHeight = (base * typo.lineHeight * block.scale).sp,
                    textAlign = block.align,
                ),
                modifier = Modifier.fillMaxWidth(),
                onClick = { offset -> block.text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { openForumLink(it.item, uriHandler, onOpenProfileLink) } },
            )
        }
        is ForumBlock.ImageBlock -> {
            val hint = block.widthDp?.dp
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
                        KikoLoadingIndicator(color = c.primary, size = 32.dp)
                    }
                    else -> ForumImage(resolved!!, c, onImageTap, hint)
                }
            } else {
                ForumImage(block.url, c, onImageTap, hint)
            }
        }
        is ForumBlock.VideoBlock -> ForumVideo(block.videoId, c)
        is ForumBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    val typo = LocalForumTypography.current
                    Text(
                        listMarker(block, index), color = c.muted, fontWeight = FontWeight.Bold, fontSize = typo.size.sp, lineHeight = (typo.size * typo.lineHeight).sp,
                        modifier = Modifier.widthIn(min = 16.dp).padding(end = 8.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.forEach { nested -> ForumBlockView(nested, c, uriHandler, quoteDepth, onOpenProfileLink, onImageTap) }
                    }
                }
            }
        }
        is ForumBlock.Quote -> ForumQuoteFrame(c, quoteDepth) {
            if (block.author.isNotBlank()) {
                Text("${block.author} said:", color = c.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            block.blocks.forEach { nested -> ForumBlockView(nested, c, uriHandler, quoteDepth + 1, onOpenProfileLink, onImageTap) }
        }
        is ForumBlock.Spoiler -> ForumSpoilerView(block, c, uriHandler, quoteDepth, onOpenProfileLink, onImageTap)
        is ForumBlock.Code -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.ink.copy(alpha = 0.06f))
                .horizontalScroll(rememberScrollState()).padding(14.dp),
        ) {
            Text(block.text, color = c.ink, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, softWrap = false)
        }
        ForumBlock.Rule -> HorizontalDivider(thickness = 1.dp, color = c.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
    }
}

// Small pill button under a post ("Quote", "Reply").
@Composable private fun PostAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tonal: Boolean = false, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val tint = if (tonal) c.onSecondaryContainer else c.muted
    Row(
        Modifier.clip(kikoPillShape()).background(if (tonal) c.secondaryContainer else c.ink.copy(alpha = 0.06f)).kikoClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

// Single topic post: one rounded card per post — author row, optional "reply to" strip, the
// formatted body, then Quote / Reply actions. The original post sits on a slightly stronger tone
// so the thread has a clear starting point.
@Composable fun ForumPostCard(
    post: ForumPost,
    isOriginalPost: Boolean = false,
    highlighted: Boolean = false,
    onOpenProfileLink: (MalProfileLink) -> Unit = {},
    canReply: Boolean = false,
    onReply: (ForumPost) -> Unit = {},
    onQuote: (ForumPost) -> Unit = {},
) {
    val c = LocalKikoColors.current
    val baseContainer = if (isOriginalPost) c.surfaceContainerHigh else c.surfaceContainerLow
    val container by animateColorAsState(if (highlighted) c.primaryContainer else baseContainer, tween(400), label = "postContainer")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(24.dp))).background(container).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.author.avatar.isNotBlank()) {
                AsyncImage(model = post.author.avatar, contentDescription = post.author.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(42.dp).clip(kikoCircleShape()).background(c.warm))
            } else {
                Box(Modifier.size(42.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                    Text(post.author.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.author.name.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isOriginalPost) {
                        Box(Modifier.padding(start = 8.dp).clip(kikoPillShape()).background(c.primary).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("OP", color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
                Text(formatForumDate(post.createdAt), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
            }
            if (!isOriginalPost && post.number > 0) {
                Text("#${post.number}", color = c.muted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
        }
        // "Reply to X" hint — MAL's own parent-post reference, drawn with the same quote frame as an inline [quote].
        if (post.replyToAuthor.isNotBlank()) {
            ForumQuoteFrame(c, 0, Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = c.primary, modifier = Modifier.size(14.dp))
                    Text("Reply to ${post.replyToAuthor}", color = c.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
                if (post.replyToBody.isNotBlank()) {
                    Text(post.replyToBody, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        ForumBody(post.body, Modifier.padding(top = 12.dp), onOpenProfileLink = onOpenProfileLink, background = baseContainer)
        if (canReply) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                PostAction(Icons.Rounded.FormatQuote, "Quote") { onQuote(post) }
                PostAction(Icons.AutoMirrored.Rounded.Reply, "Reply", tonal = true) { onReply(post) }
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
private val BbTagRegex = Regex("\\[/?[^\\]]*\\]")
private val WhitespaceRegex = Regex("\\s+")
private fun plainBodyPreview(bbBody: String, maxLen: Int = 140): String {
    val plain = bbBody.replace(BbTagRegex, " ").replace(WhitespaceRegex, " ").trim()
    return if (plain.length > maxLen) plain.take(maxLen).trimEnd() + "…" else plain
}
// Excerpt used by the Quote button: drops nested [quote] blocks first so a reply chain doesn't get
// pasted back in, then flattens to plain text.
private val NestedQuoteRegex = Regex("\\[quote[^\\]]*\\].*?\\[/quote\\]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private fun quotePreview(bbBody: String): String = plainBodyPreview(NestedQuoteRegex.replace(bbBody, " "), 300)
// Parse forum ISO timestamp

fun formatForumDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(raw)
        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(parsed!!)
    } catch (e: Exception) { raw.take(10) }
}

// Profile section