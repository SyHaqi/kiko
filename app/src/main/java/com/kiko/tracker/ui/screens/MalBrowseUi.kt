@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.BlogComment
import com.kiko.tracker.data.api.BlogPost
import com.kiko.tracker.data.api.BlogRef
import com.kiko.tracker.data.api.MagazineRef
import com.kiko.tracker.data.api.MagazineTitle
import com.kiko.tracker.data.api.MalBrowseApi
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.api.SiteReview
import com.kiko.tracker.data.model.CommunityTab
import com.kiko.tracker.data.model.ReviewEntry
import com.kiko.tracker.data.model.verdict
import com.kiko.tracker.ui.components.Avatar
import com.kiko.tracker.ui.components.KikoErrorState
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.KikoPullToRefreshBox
import com.kiko.tracker.ui.components.SwitcherHeader
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

private fun browseError(t: Throwable): String =
    if (t is MalSessionExpired) "Your MAL login expired. Reconnect from Profile and try again." else t.message?.takeIf { it.isNotBlank() } ?: "Couldn't load this from MyAnimeList"

/** The Community section switcher (Forums / Clubs / Reviews / Blogs) with the avatar on the right, shared by the new tabs. */
@Composable private fun CommunityHeader(vm: LibraryViewModel) {
    val context = LocalContext.current
    SwitcherHeader(
        current = vm.communityTab, options = CommunityTab.entries.toList(), labelFor = { it.label },
        onSelect = { vm.selectCommunityTab(context, it) }, horizontalPadding = KikoHeader.SidePadding,
        switchDescription = "Switch between Forums, Clubs, Reviews and Blogs",
    ) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty(), showUpdateBadge = vm.updateInfo != null, size = 33.dp, circle = true) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
}

/** A connected two-or-more button group, the same control as the Anime / Manga switch on Profile. */
@Composable private fun <T> ConnectedSwitch(current: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = current == value, onCheckedChange = { if (current != value) onSelect(value) },
                modifier = Modifier.weight(1f).height(40.dp).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(containerColor = c.surfaceContainer, contentColor = c.onSurfaceVariant, checkedContainerColor = c.primary, checkedContentColor = c.onPrimary),
            ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable private fun EmptyNote(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(44.dp))
        Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        Text(sub, color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun LoadMoreButton(loading: Boolean, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        if (loading) KikoLoadingIndicator(color = c.primary, size = 28.dp)
        else TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("See more") }
    }
}

// ---- Reviews tab -------------------------------------------------------------------------------------------------------

/** Community > Reviews: MAL's site-wide review feed, anime or manga. Tapping a review opens the same reader as a title's review tab. */
@Composable fun ReviewsTab(vm: LibraryViewModel, onOpenReview: (ReviewEntry, String) -> Unit) {
    val c = LocalKikoColors.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val api = remember { MalBrowseApi(ctx) }
    var manga by rememberSaveable { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<SiteReview>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching { api.reviews(manga, 1) }
                .onSuccess { items = it; page = 1; hasMore = it.isNotEmpty() }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = browseError(it); items = emptyList(); hasMore = false }
            loading = false
        }
    }
    LaunchedEffect(manga) { items = emptyList(); load() }

    Column(Modifier.fillMaxSize()) {
        CommunityHeader(vm)
        ConnectedSwitch(manga, listOf(false to "Anime", true to "Manga"), { manga = it })
        KikoPullToRefreshBox(isRefreshing = loading && items.isNotEmpty(), onRefresh = { load() }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
                error != null && items.isEmpty() -> KikoErrorState(error!!, onRetry = { load() })
                items.isEmpty() -> EmptyNote(Icons.Rounded.RateReview, "No reviews found", "Pull down to try again.")
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(items) { index, sr ->
                        StaggeredItem(index) { SiteReviewRow(sr, forumGroupShape(index, items.size, 22.dp)) { onOpenReview(sr.review, sr.title) } }
                    }
                    if (hasMore) item {
                        LoadMoreButton(loadingMore) {
                            if (loadingMore) return@LoadMoreButton
                            loadingMore = true
                            scope.launch {
                                runCatching { api.reviews(manga, page + 1) }
                                    .onSuccess { more -> page += 1; hasMore = more.isNotEmpty(); items = (items + more).distinctBy { it.review.url.ifBlank { it.review.username + it.title } } }
                                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = null; hasMore = false }
                                loadingMore = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SiteReviewRow(sr: SiteReview, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val r = sr.review
    Row(Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f, onClick = onClick).padding(14.dp)) {
        if (sr.cover.isNotBlank()) {
            AsyncImage(model = sr.cover, contentDescription = sr.title, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(width = 62.dp, height = 90.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.warm))
        } else {
            Box(Modifier.size(width = 62.dp, height = 90.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.RateReview, null, tint = c.onPrimaryContainer) }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(sr.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.userImage.isNotBlank()) AsyncImage(model = r.userImage, contentDescription = r.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(20.dp).clip(kikoCircleShape()).background(c.warm))
                Text(r.username, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(start = if (r.userImage.isNotBlank()) 6.dp else 0.dp))
                if (r.score > 0) { Spacer(Modifier.width(8.dp)); ReviewScoreChip(r.score, compact = true) }
                r.verdict()?.let { Spacer(Modifier.width(6.dp)); VerdictChip(it, compact = true) }
            }
            Text(r.review, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            val total = if (r.reactionCounts.isNotEmpty()) r.reactionCounts.sum() else r.reactionScore
            if (total > 0 || r.date.isNotBlank()) Text(listOfNotNull(r.date.takeIf { it.isNotBlank() }, "$total reactions".takeIf { total > 0 }).joinToString("  ·  "), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---- Blogs tab ---------------------------------------------------------------------------------------------------------

/** Community > Blogs: MAL's blog feed, or your own entries. Tapping opens the reader; Write opens the composer. */
@Composable fun BlogsTab(vm: LibraryViewModel, reloadKey: Int, onOpenBlog: (Int) -> Unit, onWrite: () -> Unit) {
    val c = LocalKikoColors.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val api = remember { MalBrowseApi(ctx) }
    var mine by rememberSaveable { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<BlogRef>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val myName = vm.malProfile?.name.orEmpty()

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching { if (mine && myName.isNotBlank()) api.userBlog(myName) else api.blogFeed() }
                .onSuccess { items = it }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = browseError(it); items = emptyList() }
            loading = false
        }
    }
    LaunchedEffect(mine, reloadKey) { load() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CommunityHeader(vm)
            if (vm.signedIn) ConnectedSwitch(mine, listOf(false to "Latest", true to "My entries"), { mine = it })
            KikoPullToRefreshBox(isRefreshing = loading && items.isNotEmpty(), onRefresh = { load() }, modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
                    error != null -> KikoErrorState(error!!, onRetry = { load() })
                    items.isEmpty() -> EmptyNote(Icons.Rounded.EditNote, if (mine) "You haven't written any entries" else "No blog entries found", if (mine) "Tap Write to publish your first one." else "Pull down to try again.")
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(items, key = { _, b -> b.id }) { index, b ->
                            StaggeredItem(index) { BlogRow(b, showAuthor = !mine, shape = forumGroupShape(index, items.size, 22.dp)) { onOpenBlog(b.id) } }
                        }
                    }
                }
            }
        }
        if (vm.signedIn) ExtendedFloatingActionButton(
            onClick = onWrite, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = c.primary, contentColor = c.onPrimary,
            icon = { Icon(Icons.Rounded.Edit, null) }, text = { Text("Write", fontWeight = FontWeight.Bold) },
        )
    }
}

@Composable private fun BlogRow(b: BlogRef, showAuthor: Boolean, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(b.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(b.author.takeIf { showAuthor && it.isNotBlank() }?.let { "by $it" }, b.posted.takeIf { it.isNotBlank() }, "${b.comments} ${if (b.comments == 1) "comment" else "comments"}").joinToString("  ·  "),
            color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (b.relations.isNotEmpty()) Text(b.relations.joinToString(", ") { it.second }, color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---- Blog reader -------------------------------------------------------------------------------------------------------

@Composable fun BlogReaderScreen(blogId: Int, myName: String, onBack: () -> Unit, onEdit: (Int) -> Unit, onDeleted: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { MalBrowseApi(context) }
    val hasSession = remember { MalSessionCookie(context).has() }
    var post by remember { mutableStateOf<BlogPost?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(blogId, reload) {
        error = null
        runCatching { api.blogPost(blogId) }.onSuccess { post = it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = browseError(it) }
    }
    Column(Modifier.fillMaxSize()) {
        MalAccountHeader("Blog entry", onBack, subtitle = post?.author?.takeIf { it.isNotBlank() }?.let { "by $it" }.orEmpty())
        val p = post
        when {
            error != null && p == null -> KikoErrorState(error!!, onRetry = { reload++ })
            p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 28.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(24.dp))).background(c.surfaceContainerHigh).padding(20.dp)) {
                    Text(p.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
                    Text(listOfNotNull(p.author.takeIf { it.isNotBlank() }?.let { "by $it" }, p.date.takeIf { it.isNotBlank() }).joinToString("  ·  "), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    if (p.relations.isNotEmpty()) Text("Anime: " + p.relations.joinToString(", ") { it.second }, color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                    ForumBody(p.body, modifier = Modifier.padding(top = 16.dp), background = c.surfaceContainerHigh)
                }
                if (myName.isNotBlank() && p.author.equals(myName, ignoreCase = true)) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Delete", color = c.danger) }
                        FilledTonalButton(onClick = { onEdit(blogId) }, shape = kikoPillShape()) { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edit") }
                    }
                }
                Text("${p.comments.size} ${if (p.comments.size == 1) "comment" else "comments"}", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    p.comments.forEachIndexed { i, cm -> BlogCommentRow(cm, forumGroupShape(i, p.comments.size, 22.dp)) }
                }
                if (hasSession && p.canComment) {
                    TextField(
                        value = comment, onValueChange = { comment = it }, placeholder = { Text("Add a comment") }, minLines = 3,
                        shape = RoundedCornerShape(kikoCorner(18.dp)), colors = settingsFieldColors(c), modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    )
                    Button(
                        enabled = comment.isNotBlank() && !sending, shape = kikoPillShape(),
                        onClick = {
                            sending = true
                            scope.launch {
                                runCatching { api.postBlogComment(blogId, comment.trim()) }
                                    .onSuccess { comment = ""; Toast.makeText(context, "Comment posted", Toast.LENGTH_SHORT).show(); reload++ }
                                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Toast.makeText(context, browseError(it), Toast.LENGTH_LONG).show() }
                                sending = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary), modifier = Modifier.align(Alignment.End).padding(top = 10.dp),
                    ) { if (sending) KikoLoadingIndicator(color = c.onPrimary, size = 18.dp) else Text("Post comment", fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, containerColor = c.surfaceContainerHigh,
            title = { Text("Delete this entry?", color = c.ink) },
            text = { Text("It will be removed from your MyAnimeList blog, along with its comments.", color = c.muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { api.deleteBlog(blogId) }
                            .onSuccess { Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show(); onDeleted() }
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Toast.makeText(context, browseError(it), Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Delete", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.muted) } },
        )
    }
}

@Composable private fun BlogCommentRow(cm: BlogComment, shape: androidx.compose.ui.graphics.Shape) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 14.dp)) {
        if (cm.avatar.isNotBlank()) AsyncImage(model = cm.avatar, contentDescription = cm.user, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(36.dp).clip(kikoCircleShape()).background(c.warm))
        else Box(Modifier.size(36.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) { Text(cm.user.take(1).uppercase(), color = c.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cm.user, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (cm.time.isNotBlank()) Text("  ·  ${cm.time}", color = c.muted, fontSize = 11.sp, maxLines = 1)
            }
            ForumBody(cm.body, modifier = Modifier.padding(top = 4.dp), background = c.surfaceContainerHigh)
        }
    }
}

// ---- Blog composer -----------------------------------------------------------------------------------------------------

/** Write a new entry ([editId] == 0) or edit one. MAL requires at least one related anime on every entry. */
@Composable fun BlogComposeScreen(editId: Int, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { MalBrowseApi(context) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var privacy by rememberSaveable { mutableIntStateOf(1) }
    var commenting by rememberSaveable { mutableStateOf(true) }
    var relations by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(editId != 0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var animeQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(editId) {
        if (editId == 0) return@LaunchedEffect
        runCatching { api.loadDraft(editId) }
            .onSuccess { title = it.title; text = it.text; privacy = it.privacy; commenting = it.commenting; relations = it.relations; loading = false }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; loadError = browseError(it); loading = false }
    }
    fun search() {
        if (animeQuery.isBlank() || searching) return
        searching = true
        scope.launch {
            runCatching { api.searchAnime(animeQuery.trim()) }
                .onSuccess { results = it }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Toast.makeText(context, browseError(it), Toast.LENGTH_SHORT).show() }
            searching = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        MalAccountHeader(if (editId == 0) "New blog entry" else "Edit entry", onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            loadError != null -> KikoErrorState(loadError!!, onRetry = onBack)
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 4.dp, bottom = 28.dp)) {
                GroupLabel("Entry")
                TextField(value = title, onValueChange = { title = it }, singleLine = true, placeholder = { Text("Title") }, shape = RoundedCornerShape(kikoCorner(18.dp)), colors = settingsFieldColors(c), modifier = Modifier.fillMaxWidth())
                TextField(value = text, onValueChange = { text = it }, placeholder = { Text("Write something. BBCode works, same as on the website.") }, minLines = 8, shape = RoundedCornerShape(kikoCorner(18.dp)), colors = settingsFieldColors(c), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                GroupLabel("Related anime")
                Text("MAL requires every entry to be about at least one anime.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
                if (relations.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    relations.forEach { (id, name) ->
                        InputChip(selected = true, onClick = { relations = relations.filterNot { it.first == id } }, label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingIcon = { Icon(Icons.Rounded.Close, "Remove", modifier = Modifier.size(16.dp)) })
                    }
                }
                TextField(
                    value = animeQuery, onValueChange = { animeQuery = it }, singleLine = true, placeholder = { Text("Search anime to add") },
                    shape = RoundedCornerShape(kikoCorner(18.dp)), colors = settingsFieldColors(c), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }),
                    trailingIcon = { IconButton(onClick = { search() }) { if (searching) KikoLoadingIndicator(color = c.primary, size = 18.dp) else Icon(Icons.Rounded.Search, "Search") } },
                )
                if (results.isNotEmpty()) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    results.forEachIndexed { i, (id, name) ->
                        val added = relations.any { it.first == id }
                        Row(
                            Modifier.fillMaxWidth().clip(forumGroupShape(i, results.size, 18.dp)).background(c.surfaceContainerHigh).kikoClickable(enabled = !added, scale = 0.98f) { relations = relations + (id to name); results = emptyList(); animeQuery = "" }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, color = if (added) c.muted else c.ink, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(if (added) Icons.Rounded.Check else Icons.Rounded.Add, null, tint = c.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                GroupLabel("Who can read it")
                ConnectedSwitch(privacy, listOf(1 to "Public", 2 to "Friends", 3 to "Only me"), { privacy = it }, Modifier.padding(horizontal = 0.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow comments", color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = commenting, onCheckedChange = { commenting = it })
                }
                Button(
                    enabled = !saving && title.isNotBlank() && text.isNotBlank() && relations.isNotEmpty(), shape = kikoPillShape(),
                    onClick = {
                        saving = true
                        scope.launch {
                            runCatching { api.saveBlog(editId, title.trim(), text, privacy, commenting, relations.map { it.first }) }
                                .onSuccess { Toast.makeText(context, if (editId == 0) "Entry published" else "Entry updated", Toast.LENGTH_SHORT).show(); onSaved() }
                                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Toast.makeText(context, browseError(it), Toast.LENGTH_LONG).show() }
                            saving = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier.align(Alignment.End).padding(top = 22.dp),
                ) { if (saving) KikoLoadingIndicator(color = c.onPrimary, size = 18.dp) else Text(if (editId == 0) "Publish" else "Save changes", fontWeight = FontWeight.Medium) }
            }
        }
    }
}

// ---- Magazines ---------------------------------------------------------------------------------------------------------

/** Discover > Magazines (manga): every magazine A to Z with a filter box, then the manga it serialised. */
@Composable fun MagazinesScreen(selected: MagazineRef?, onSelect: (MagazineRef?) -> Unit, onBack: () -> Unit, onOpenTitle: (MagazineTitle) -> Unit) {
    BackHandler { if (selected != null) onSelect(null) else onBack() }
    if (selected == null) MagazineIndex(onBack, onSelect) else MagazineDetail(selected, { onSelect(null) }, onOpenTitle)
}

@Composable private fun MagazineIndex(onBack: () -> Unit, onSelect: (MagazineRef) -> Unit) {
    val c = LocalKikoColors.current
    val ctx = LocalContext.current
    val api = remember { MalBrowseApi(ctx) }
    var all by remember { mutableStateOf(MagazineCache.list) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(reload) {
        if (all.isNotEmpty() && reload == 0) return@LaunchedEffect
        error = null
        runCatching { api.magazines() }.onSuccess { all = it; MagazineCache.list = it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = browseError(it) }
    }
    val shown = remember(all, query) { all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase() } }
    val groups = remember(shown) { shown.groupBy { it.name.firstOrNull()?.uppercaseChar()?.takeIf { ch -> ch.isLetter() } ?: '#' }.toSortedMap() }
    Column(Modifier.fillMaxSize()) {
        MalSearchHeader("Magazines", onBack, query, { query = it }, hint = "Search magazines", subtitle = if (all.isNotEmpty()) "${formatExact(all.size)} manga magazines" else "")
        when {
            error != null && all.isEmpty() -> KikoErrorState(error!!, onRetry = { reload++ })
            all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            shown.isEmpty() -> EmptyNote(Icons.Rounded.SearchOff, "No magazines match", "Try a shorter name.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 28.dp)) {
                groups.forEach { (letter, mags) ->
                    item(key = "h$letter") { Text(letter.toString(), color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp)) }
                    itemsIndexed(mags, key = { _, m -> m.id }) { i, m ->
                        Row(
                            Modifier.padding(top = if (i == 0) 0.dp else 2.dp).fillMaxWidth().clip(forumGroupShape(i, mags.size, 20.dp)).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f) { onSelect(m) }.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (m.count > 0) Text(formatExact(m.count), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                            Icon(Icons.Rounded.ChevronRight, null, tint = c.muted)
                        }
                    }
                }
            }
        }
    }
}

/** Remembers the (large) magazine index for the life of the process so coming back from a title doesn't refetch it. */
private object MagazineCache { var list: List<MagazineRef> = emptyList() }

@Composable private fun MagazineDetail(mag: MagazineRef, onBack: () -> Unit, onOpenTitle: (MagazineTitle) -> Unit) {
    val c = LocalKikoColors.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val api = remember { MalBrowseApi(ctx) }
    var items by remember(mag.id) { mutableStateOf<List<MagazineTitle>>(emptyList()) }
    var page by remember(mag.id) { mutableIntStateOf(1) }
    var hasMore by remember(mag.id) { mutableStateOf(false) }
    var loading by remember(mag.id) { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember(mag.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(mag.id, reload) {
        loading = true; error = null
        runCatching { api.magazineTitles(mag, 1) }
            .onSuccess { items = it; page = 1; hasMore = it.size >= 100 }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = browseError(it) }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        MalAccountHeader(mag.name, onBack, subtitle = "Magazine" + if (mag.count > 0) " · ${formatExact(mag.count)} manga" else "")
        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            error != null -> KikoErrorState(error!!, onRetry = { reload++ })
            items.isEmpty() -> EmptyNote(Icons.Rounded.MenuBook, "Nothing listed", "MAL has no manga for this magazine yet.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(items, key = { _, t -> t.id }) { i, t -> StaggeredItem(i) { MagazineTitleRow(t, forumGroupShape(i, items.size, 22.dp)) { onOpenTitle(t) } } }
                if (hasMore) item {
                    LoadMoreButton(loadingMore) {
                        if (loadingMore) return@LoadMoreButton
                        loadingMore = true
                        scope.launch {
                            runCatching { api.magazineTitles(mag, page + 1) }
                                .onSuccess { more -> val fresh = more.filter { m -> items.none { it.id == m.id } }; page += 1; hasMore = more.size >= 100 && fresh.isNotEmpty(); items = items + fresh }
                                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; hasMore = false }
                            loadingMore = false
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MagazineTitleRow(t: MagazineTitle, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f, onClick = onClick).padding(14.dp)) {
        if (t.cover.isNotBlank()) AsyncImage(model = t.cover, contentDescription = t.title, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(width = 62.dp, height = 90.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.warm))
        else Box(Modifier.size(width = 62.dp, height = 90.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.primaryContainer))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(t.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (t.info.isNotBlank()) Text(t.info, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.genres.isNotBlank()) Text(t.genres, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (t.score > 0) { ScoreLine(t.score, modifier = Modifier.padding(end = 12.dp)) }
                if (t.members > 0) Text("${formatExact(t.members)} members", color = c.muted, fontSize = 12.sp)
            }
        }
    }
}
