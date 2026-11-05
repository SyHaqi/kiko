@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import coil.request.ImageRequest
import com.kiko.tracker.data.api.ClubsApi
import com.kiko.tracker.data.api.MalClub
import com.kiko.tracker.data.api.MalFriendApi
import com.kiko.tracker.data.api.MalNotification
import com.kiko.tracker.data.api.NotificationCategory
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.KikoPullToRefreshBox
import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.util.openInBrowser
import com.kiko.tracker.viewmodel.LibraryViewModel

/**
 * MAL notification inbox (myanimelist.net/notification): accepted/denied/incoming friend
 * requests, profile comments, forum quotes, club shares, new related anime, and the rest.
 * Laid out like a Material 3 list page: small top app bar with a "mark all as read" action, scrollable
 * category tabs (MAL's sidebar filters, same strip as My List), then per page a "New" section and a
 * "History" section of connected tonal rows. A row moves from New to History
 * only when it is tapped (which opens its page) or via "mark all" — read state is local, see
 * MalNotificationTracker.
 */
// The strip is fixed: every tab is always there (even with nothing in it) so it never grows or
// shifts while the inbox loads; only the page under it changes.
private val JumpToPostKinds = setOf(
    NotificationCategory.ForumQuotes, NotificationCategory.UserMentions,
    NotificationCategory.WatchedTopics, NotificationCategory.ClubMessages,
)

private val NotificationTabs = listOf(
    NotificationCategory.All,
    NotificationCategory.FriendRequests,
    NotificationCategory.ForumQuotes,
    NotificationCategory.UserMentions,
    NotificationCategory.WatchedTopics,
    NotificationCategory.ClubMessages,
    NotificationCategory.RelatedAnime,
    NotificationCategory.NowAiring,
)

@Composable fun NotificationsScreen(
    vm: LibraryViewModel,
    selectedTab: NotificationCategory,
    onSelectTab: (NotificationCategory) -> Unit,
    onBack: () -> Unit,
    onOpenUser: (username: String, avatarUrl: String?) -> Unit,
    onOpenTitle: (Int, MediaType) -> Unit,
    onOpenTopic: (topicId: Int, title: String, postId: Int?) -> Unit = { _, _, _ -> },
    onOpenClub: (MalClub) -> Unit = {},
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()

    // False until the first fetch has been kicked off, so a page shows its spinner (not "nothing here") on the first frame.
    var requested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.loadFriendResponses(context) // answered requests show their outcome, not Accept / Deny
        vm.refreshNotifications(context)
        requested = true
        // Read items live on the per-category pages, not on /notification. Started a beat later so
        // the page's first draw and first scroll aren't competing with 10 requests + state updates.
        kotlinx.coroutines.delay(400)
        vm.loadNotificationHistory(context)
    }

    // New items come from /notification, read ones from the category pages — merged here.
    val inbox = remember(vm.notifications, vm.notificationHistory) {
        (vm.notifications + vm.notificationHistory).distinctBy { it.id }.sortedByDescending { it.createdAtEpoch }
    }
    val readIds = vm.notificationsRead
    val tabs = NotificationTabs
    // The selected tab lives in the caller (Navigation) so it survives opening a notification's page and
    // coming back; the caller resets it to All whenever the inbox is closed.
    val current = if (selectedTab in tabs) selectedTab else NotificationCategory.All
    // One pass over the inbox for every tab's badge, instead of a full scan per tab per recomposition.
    val unreadCounts = remember(inbox, readIds) {
        val m = HashMap<NotificationCategory, Int>()
        var total = 0
        for (n in inbox) if (n.id !in readIds) { total++; m[n.kind] = (m[n.kind] ?: 0) + 1 }
        m[NotificationCategory.All] = total
        m
    }
    fun unreadIn(cat: NotificationCategory) = unreadCounts[cat] ?: 0

    // Opens what a notification points at inside Kiko whenever Kiko has a page for it (profiles,
    // titles, forum topics, clubs); anything else falls back to the browser.
    fun open(n: MalNotification) {
        val url = n.url?.let { if (it.startsWith("/")) "https://myanimelist.net$it" else it } ?: return
        val profile = Regex("myanimelist\\.net/profile/([^/?#]+)").find(url)?.groupValues?.get(1)
        val title = Regex("myanimelist\\.net/(anime|manga)/(\\d+)").find(url)
        // Forum topics (quotes, watched topics, mentions, club-shared discussions all link to one).
        val topicId = Regex("[?&]topicid=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
        // A single post's link: MAL redirects it to its topic, so the topic id has to be looked up.
        val isForumMessage = Regex("myanimelist\\.net/forum/message/\\d+").containsMatchIn(url)
        val clubId = Regex("myanimelist\\.net/clubs\\.php\\?(?:[^#]*&)?(?:cid|id)=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
        val topicTitle = n.body?.takeIf { it.isNotBlank() } ?: n.title
        // Quotes, mentions, watched topics and club shares jump to the exact post when the link names one
        // (forum/message/<id>, #msg<id> or msgid=<id>); otherwise the topic just opens at the top.
        val postId = if (n.kind in JumpToPostKinds) Regex("myanimelist\\.net/forum/message/(\\d+)|#msg(\\d+)|[?&]msgid=(\\d+)").find(url)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull() else null
        when {
            profile != null -> onOpenUser(runCatching { java.net.URLDecoder.decode(profile, "UTF-8") }.getOrDefault(profile), n.avatarUrl)
            title != null -> onOpenTitle(title.groupValues[2].toInt(), if (title.groupValues[1] == "anime") MediaType.Anime else MediaType.Manga)
            topicId != null -> onOpenTopic(topicId, topicTitle, postId)
            isForumMessage -> scope.launch {
                val id = runCatching { MalFriendApi(context).resolveForumTopicId(url) }.getOrNull()
                if (id != null) onOpenTopic(id, topicTitle, postId) else context.openInBrowser(url)
            }
            clubId != null -> scope.launch {
                runCatching { ClubsApi().fetchClub(clubId) }
                    .onSuccess(onOpenClub)
                    .onFailure { context.openInBrowser(url) }
            }
            else -> context.openInBrowser(url)
        }
    }
    // Tapping opens the page the notification points at, then marks it read.
    fun tap(n: MalNotification) { open(n); vm.markNotificationRead(context, n.id) }
    val tapState = rememberUpdatedState(::tap)
    val onTap = remember { { n: MalNotification -> tapState.value(n) } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = KikoHeader.BackTop, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Rounded.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Notifications", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).headerTitleStart())
            val canMarkAll = unreadIn(current) > 0
            IconButton(onClick = { vm.markAllNotificationsRead(context, current) }, enabled = canMarkAll, modifier = Modifier.headerEdgeEnd()) {
                Icon(Icons.Rounded.DoneAll, "Mark all as read", tint = c.ink.copy(alpha = if (canMarkAll) 1f else 0.38f), modifier = Modifier.size(24.dp))
            }
        }
        KikoTabPager(
            items = tabs,
            selected = current,
            onSelect = onSelectTab,
            tab = { cat, isSelected ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val n = unreadIn(cat)
                    // The badge hangs off the label's corner instead of sitting in the row, so a count
                    // arriving later never widens the tab or moves the indicator.
                    BadgedBox(badge = {
                        if (n > 0) Badge(containerColor = c.primary, contentColor = c.onPrimary) { Text(if (n > 99) "99+" else n.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }) { KikoTabText(cat.label, isSelected) }
                }
            },
            page = { cat -> NotificationPage(vm, cat, inbox, awaitingFirstLoad = !requested, onTap = onTap) },
        )
    }
}

@Composable private fun NotificationPage(vm: LibraryViewModel, category: NotificationCategory, inbox: List<MalNotification>, awaitingFirstLoad: Boolean, onTap: (MalNotification) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val items = remember(inbox, category) { if (category == NotificationCategory.All) inbox else inbox.filter { it.kind == category } }
    val readIds = vm.notificationsRead
    // Split once per change, not on every recomposition (which happens a lot while scrolling/animating).
    val unread = remember(items, readIds) { items.filter { it.id !in readIds } }
    // The "All" tab only lists what's new; read items live under their own category's History.
    val showHistory = category != NotificationCategory.All
    val history = remember(items, readIds, showHistory) { if (showHistory) items.filter { it.id in readIds } else emptyList() }
    // History starts collapsed (and stays that way per tab, even across rotation / page recycling).
    var historyExpanded by rememberSaveable(category) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    KikoPullToRefreshBox(
        isRefreshing = (vm.notificationsLoading && vm.notifications.isNotEmpty()) || vm.notificationHistoryLoading,
        onRefresh = { vm.refreshNotifications(context); vm.loadNotificationHistory(context, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                items.isEmpty() && (awaitingFirstLoad || vm.notificationsLoading || vm.notificationHistoryLoading) -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary) }
                }
                items.isEmpty() -> item(key = "empty") {
                    Text(
                        vm.notificationsError ?: if (category == NotificationCategory.All) "No notifications yet — friend requests, replies and other MAL activity will show up here." else "No ${category.label.lowercase()} yet.",
                        color = c.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
                else -> {
                    vm.notificationsError?.let { err -> item(key = "error") { Text(err, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) } }
                    item(key = "new_header") { SectionLabel("New", if (unread.isNotEmpty()) unread.size else null) }
                    if (unread.isEmpty()) item(key = "caught_up") {
                        Text("You're all caught up", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                    }
                    itemsIndexed(unread, key = { _, n -> n.id }, contentType = { _, _ -> "notification" }) { index, n ->
                        NotificationEntry(vm, n, isNew = true, shape = segmentedShape(index, unread.size), modifier = Modifier.animateItem(), onTap = { onTap(n) })
                    }
                    if (showHistory && history.isNotEmpty()) {
                        item(key = "history_header", contentType = "history_header") {
                            HistoryHeader(history.size, historyExpanded) { historyExpanded = !historyExpanded }
                        }
                        // Collapsed by default: none of these rows are composed until the header is opened.
                        if (historyExpanded) {
                            itemsIndexed(history, key = { _, n -> n.id }, contentType = { _, _ -> "notification" }) { index, n ->
                                NotificationEntry(vm, n, isNew = false, shape = segmentedShape(index, history.size), modifier = Modifier, onTap = { onTap(n) })
                            }
                            // MAL embeds only the latest 10 per category; older ones are behind its "View more".
                            val more = category.moreHistoryUrl
                            if (more != null && history.size >= 10) item(key = "more_history", contentType = "more") {
                                TextButton(onClick = { context.openInBrowser(more) }) { Text("View older on MAL") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Material 3 list section label: titleSmall in the primary color, aligned with the row content.
@Composable private fun SectionLabel(text: String, count: Int?) {
    val c = LocalKikoColors.current
    Text(
        if (count != null) "$text · $count" else text,
        style = MaterialTheme.typography.titleSmall, color = c.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

// Tappable "History · N" header with a chevron; toggles the collapsed history list.
@Composable private fun HistoryHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).kikoClickable(scale = 1f, onClick = onToggle).padding(start = 4.dp, top = 16.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("History · $count", style = MaterialTheme.typography.titleSmall, color = c.primary, modifier = Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Collapse history" else "Expand history",
            tint = c.primary, modifier = Modifier.size(24.dp),
        )
    }
}

@Composable private fun NotificationEntry(
    vm: LibraryViewModel, n: MalNotification, isNew: Boolean, shape: Shape, modifier: Modifier, onTap: () -> Unit,
) {
    val context = LocalContext.current
    // MAL's own "Accepted" state (isApproved) wins over the in-memory one so an already-accepted
    // request doesn't get its buttons back after the app restarts.
    val response = if (n.isIncomingFriendRequest) {
        vm.friendResponseState(n.id).let { if (it.accepted == null && n.isApproved) it.copy(accepted = true) else it }
    } else null
    NotificationRow(
        n = n, isNew = isNew, shape = shape, modifier = modifier, onClick = onTap,
        response = response,
        onAccept = { vm.respondToFriendRequest(context, n.id, accept = true) },
        onDeny = { vm.respondToFriendRequest(context, n.id, accept = false) },
    )
}

private fun iconFor(n: MalNotification): ImageVector = when {
    n.isFriendAccepted -> Icons.Rounded.HowToReg
    n.isFriendRelated -> Icons.Rounded.PersonAdd
    n.type.contains("comment") || n.type.contains("quote") || n.type.contains("mention") || n.type.contains("watched") -> Icons.Rounded.ChatBubble
    n.type.startsWith("club") -> Icons.Rounded.Groups
    n.type == "on_air" || n.type == "related_anime_add" -> Icons.Rounded.Movie
    else -> Icons.Rounded.Notifications
}

// Connected tonal list: big outer corners on the first/last row, small ones where rows meet.
private val ShapeSingle = RoundedCornerShape(20.dp)
private val ShapeFirst = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val ShapeLast = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
private val ShapeMiddle = RoundedCornerShape(4.dp)
private fun segmentedShape(index: Int, count: Int): Shape = when {
    count <= 1 -> ShapeSingle
    index == 0 -> ShapeFirst
    index == count - 1 -> ShapeLast
    else -> ShapeMiddle
}

@Composable private fun NotificationRow(
    n: MalNotification, isNew: Boolean, shape: Shape, modifier: Modifier = Modifier, onClick: () -> Unit,
    response: LibraryViewModel.FriendResponseState?, onAccept: () -> Unit, onDeny: () -> Unit,
) {
    val c = LocalKikoColors.current
    val container = if (isNew) c.secondaryContainer else c.surfaceContainer
    val content = if (isNew) c.onSecondaryContainer else c.ink
    ListItem(
        modifier = modifier.clip(shape).kikoClickable(scale = 0.99f, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = container, headlineColor = content,
            supportingColor = content.copy(alpha = 0.75f), trailingIconColor = c.primary,
        ),
        leadingContent = {
            if (!n.avatarUrl.isNullOrBlank()) {
                val ctx = LocalContext.current
                val request = remember(n.avatarUrl) { ImageRequest.Builder(ctx).data(n.avatarUrl).size(132).crossfade(false).build() }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(kikoCircleShape()).background(c.warm))
            } else {
                Box(Modifier.size(44.dp).clip(kikoCircleShape()).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(iconFor(n), null, tint = c.primary, modifier = Modifier.size(22.dp))
                }
            }
        },
        headlineContent = {
            Text(n.title, fontSize = 14.sp, fontWeight = if (isNew) FontWeight.Bold else FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                n.body?.let { Text(it, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(listOf(n.category, n.displayTime).filter { it.isNotBlank() }.joinToString(" · "), color = content.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = if (n.body != null) 4.dp else 2.dp))
                if (response != null) {
                    when {
                        response.accepted != null -> Text(
                            if (response.accepted) "Friend request accepted" else "Friend request denied",
                            color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp),
                        )
                        response.busy -> KikoLoadingIndicator(color = c.primary, size = 32.dp, modifier = Modifier.padding(top = 6.dp))
                        else -> {
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onAccept, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.height(36.dp)) { Text("Accept", fontSize = 13.sp) }
                                FilledTonalButton(onClick = onDeny, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.height(36.dp)) { Text("Deny", fontSize = 13.sp) }
                            }
                            response.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        }
                    }
                }
            }
        },
        trailingContent = if (isNew) ({ Box(Modifier.size(8.dp).clip(kikoCircleShape()).background(c.primary)) }) else null,
    )
}