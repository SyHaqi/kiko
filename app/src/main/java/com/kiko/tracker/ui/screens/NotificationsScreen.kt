@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.MalNotification
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
 * Laid out like a Material 3 list page: small top app bar with a "mark all as read" action, then a
 * "New" section and a "History" section of connected tonal rows. A row moves from New to History
 * only when it is tapped (which opens its page) or via "mark all" — read state is local, see
 * MalNotificationTracker.
 */
@Composable fun NotificationsScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenUser: (username: String, avatarUrl: String?) -> Unit,
    onOpenTitle: (Int, MediaType) -> Unit,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) { vm.refreshNotifications(context) }

    val unread = vm.notifications.filter { it.id !in vm.notificationsRead }
    val history = vm.notifications.filter { it.id in vm.notificationsRead }

    fun open(n: MalNotification) {
        val url = n.url ?: return
        val profile = Regex("myanimelist\\.net/profile/([^/?#]+)").find(url)?.groupValues?.get(1)
        val title = Regex("myanimelist\\.net/(anime|manga)/(\\d+)").find(url)
        when {
            profile != null -> onOpenUser(runCatching { java.net.URLDecoder.decode(profile, "UTF-8") }.getOrDefault(profile), n.avatarUrl)
            title != null -> onOpenTitle(title.groupValues[2].toInt(), if (title.groupValues[1] == "anime") MediaType.Anime else MediaType.Manga)
            else -> context.openInBrowser(url)
        }
    }
    // Tapping opens the page the notification points at, then marks it read.
    fun tap(n: MalNotification) { open(n); vm.markNotificationRead(context, n.id) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = KikoHeader.BackTop, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Rounded.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Notifications", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).headerTitleStart())
            IconButton(onClick = { vm.markAllNotificationsRead(context) }, enabled = unread.isNotEmpty(), modifier = Modifier.headerEdgeEnd()) {
                Icon(Icons.Rounded.DoneAll, "Mark all as read", tint = c.ink.copy(alpha = if (unread.isNotEmpty()) 1f else 0.38f), modifier = Modifier.size(24.dp))
            }
        }
        KikoPullToRefreshBox(
            isRefreshing = vm.notificationsLoading && vm.notifications.isNotEmpty(),
            onRefresh = { vm.refreshNotifications(context) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    vm.notificationsLoading && vm.notifications.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary) }
                    }
                    vm.notifications.isEmpty() -> item(key = "empty") {
                        Text(
                            vm.notificationsError ?: "No notifications yet — friend requests, replies and other MAL activity will show up here.",
                            color = c.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }
                    else -> {
                        vm.notificationsError?.let { err -> item(key = "error") { Text(err, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) } }
                        item(key = "new_header") { SectionLabel("New", if (unread.isNotEmpty()) unread.size else null) }
                        if (unread.isEmpty()) item(key = "caught_up") {
                            Text("You're all caught up", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                        }
                        itemsIndexed(unread, key = { _, n -> n.id }) { index, n ->
                            NotificationEntry(vm, n, isNew = true, shape = segmentedShape(index, unread.size), modifier = Modifier.animateItem(), onTap = { tap(n) })
                        }
                        if (history.isNotEmpty()) {
                            item(key = "history_header") { SectionLabel("History", null) }
                            itemsIndexed(history, key = { _, n -> n.id }) { index, n ->
                                NotificationEntry(vm, n, isNew = false, shape = segmentedShape(index, history.size), modifier = Modifier.animateItem(), onTap = { tap(n) })
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
private fun segmentedShape(index: Int, count: Int): Shape {
    val outer = 20.dp; val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner, topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner, bottomEnd = if (index == count - 1) outer else inner,
    )
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
                AsyncImage(model = n.avatarUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(44.dp).clip(kikoCircleShape()).background(c.warm))
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