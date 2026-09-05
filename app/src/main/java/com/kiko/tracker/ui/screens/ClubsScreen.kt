@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.kiko.tracker.data.api.ClubMember
import com.kiko.tracker.data.api.ClubPost
import com.kiko.tracker.data.api.ClubsApi
import com.kiko.tracker.data.api.ClubsPage
import com.kiko.tracker.data.api.MalClub
import com.kiko.tracker.data.model.CommunityTab
import com.kiko.tracker.data.model.next
import com.kiko.tracker.ui.components.Avatar
import com.kiko.tracker.ui.components.ExpandableSearchHeader
import com.kiko.tracker.ui.components.Pill
import com.kiko.tracker.ui.theme.ListRowSkeletonGroup
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.TopicRowSkeletonGroup
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.viewmodel.LibraryViewModel

// Clubs tab — browse/search
// (same approach as StacksApi:
// being shut down, so
@Composable fun ClubsScreen(vm: LibraryViewModel, onOpenClub: (MalClub) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val api = remember { ClubsApi() }
    // Query/results/pagination live on the
    // so they — and
    // coming back, instead of
    var query by remember { mutableStateOf(vm.clubsQuery) }
    // Search bar starts collapsed
    // (see ExpandableSearchHeader), instead of
    var searchExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(vm.clubsList.isEmpty()) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Blank query browses MAL's
    // query hits the search
    fun runSearch(q: String) {
        scope.launch {
            loading = true; error = null
            runCatching { api.search(q, 1) }
                .onSuccess { vm.setClubsResults(q, it) }
                .onFailure { error = it.message ?: "Could not load clubs"; vm.setClubsResults(q, ClubsPage(emptyList(), false)) }
            loading = false
        }
    }
    // Only fetch on a
    // (or switching tabs and
    // its scroll position gets
    LaunchedEffect(vm.signedIn) { if (vm.signedIn && vm.clubsList.isEmpty() && vm.clubsQuery.isBlank()) runSearch("") }

    // "See more" reveals 10
    // to MAL for another
    fun showMore() {
        scope.launch {
            if (vm.clubsVisibleCount >= vm.clubsList.size && vm.clubsHasMore && !loadingMore) {
                loadingMore = true
                val next = vm.clubsPage + 1
                runCatching { api.search(vm.clubsQuery, next) }
                    .onSuccess { vm.appendClubsResults(it, next) }
                loadingMore = false
            }
            vm.revealMoreClubs(vm.clubsVisibleCount + 10)
        }
    }

    // Restore the scroll position
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.clubsScrollIndex, initialFirstVisibleItemScrollOffset = vm.clubsScrollOffset)
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    val visibleClubs = remember(vm.clubsList, vm.clubsVisibleCount) { vm.clubsList.take(vm.clubsVisibleCount) }
    val canShowMore = vm.clubsVisibleCount < vm.clubsList.size || vm.clubsHasMore
    val openClub: (MalClub) -> Unit = { club ->
        vm.saveClubsScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onOpenClub(club)
    }

    PullToRefreshBox(isRefreshing = loading, onRefresh = { runSearch(vm.clubsQuery) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                ExpandableSearchHeader(
                    current = vm.communityTab,
                    options = CommunityTab.entries.toList(),
                    labelFor = { it.label },
                    onSelect = { vm.selectCommunityTab(context, it) },
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { if (vm.signedIn) runSearch(query) },
                    onClear = { query = ""; if (vm.signedIn) runSearch("") },
                    expanded = searchExpanded,
                    onExpandedChange = { expanded -> searchExpanded = expanded; if (!expanded) { query = vm.clubsQuery } },
                    hint = "Find clubs…",
                    horizontalPadding = 0.dp,
                    switchDescription = "Switch between Forums and Clubs",
                ) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
                if (vm.signedIn) {
                    Text(
                        if (vm.clubsQuery.isBlank()) "POPULAR CLUBS" else "RESULTS",
                        color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 22.dp, bottom = 9.dp),
                    )
                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), color = c.primary, trackColor = c.surfaceLow)
                    error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp)) }
                }
            }
            if (vm.authChecked && !vm.signedIn) {
                item { Text("Sign in from Profile to browse MAL clubs", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
            } else if (!loading && vm.clubsList.isEmpty() && error == null) {
                item { Text("No clubs found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
            }
            if (loading && visibleClubs.isEmpty()) {
                item { ListRowSkeletonGroup(6) }
            } else {
                itemsIndexed(visibleClubs, key = { _, club -> club.id }) { index, club ->
                    StaggeredItem(index) {
                        Column {
                            ClubRow(club) { openClub(club) }
                            if (index < visibleClubs.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                        }
                    }
                }
            }
            if (visibleClubs.isNotEmpty() && canShowMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) {
                            CircularProgressIndicator(color = c.primary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { showMore() }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("See more") }
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

// One club row in
// a bigger thumbnail and
// single truncated line.
@Composable fun ClubRow(club: MalClub, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().kikoClickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (club.image.isNotBlank()) {
            AsyncImage(model = club.image, contentDescription = club.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(width = 84.dp, height = 118.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.warm))
        } else {
            Box(Modifier.size(width = 84.dp, height = 118.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Groups, null, tint = c.primary, modifier = Modifier.size(30.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(club.name.ifBlank { "Unnamed club" }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (club.description.isNotBlank()) {
                Text(club.description, color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = c.muted, modifier = Modifier.size(13.dp))
                Text(formatExact(club.members), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = c.muted)
    }
}

private enum class ClubTab(val label: String) { Couch("Couch"), Cabinet("Cabinet"), Members("Members") }

// Full club page —
@Composable fun ClubDetailScreen(club: MalClub, onBack: () -> Unit, onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {}) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val api = remember { ClubsApi() }
    var full by remember(club.id) { mutableStateOf(club) }
    var loading by remember(club.id) { mutableStateOf(true) }
    LaunchedEffect(club.id) {
        loading = true
        // The club-page scrape can
        // list (image, category, staff,
        // short description that came
        // blank or still-garbled, so
        runCatching { api.fetchClub(club.id) }
            .onSuccess { fetched -> full = fetched.copy(description = fetched.description.ifBlank { club.description }) }
        loading = false
    }

    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(ClubTab.Couch) }
    // Dispatches a tapped character/person/company
    // club description or a
    // ForumTopicScreen does for forum
    val onOpenProfileLink: (MalProfileLink) -> Unit = { link ->
        when (link) {
            is MalProfileLink.Character -> onOpenCharacter(link.malId)
            is MalProfileLink.Person -> onOpenPerson(link.malId)
            is MalProfileLink.Company -> onOpenCompany(link.malId)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Box {
                    if (full.image.isNotBlank()) {
                        AsyncImage(model = full.image, contentDescription = full.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(160.dp).background(c.surfaceLow))
                    } else {
                        Box(Modifier.fillMaxWidth().height(160.dp).background(c.primaryContainer))
                    }
                    Box(Modifier.fillMaxWidth().height(160.dp).background(Color.Black.copy(alpha = .35f)))
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(Color.Black.copy(alpha = .35f))) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                        if (full.url.isNotBlank()) {
                            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(full.url)) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(Color.Black.copy(alpha = .35f))) {
                                Icon(Icons.Default.OpenInNew, "Open on MyAnimeList", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Text(
                        full.name.ifBlank { "Club" }, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 16.dp, end = 20.dp),
                    )
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = c.primary, trackColor = c.surfaceLow)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                        Pill("${formatExact(full.members)} members", c.surfaceLow, c.muted)
                        if (full.category.isNotBlank()) Pill(full.category, c.surfaceLow, c.muted)
                        if (full.access.isNotBlank()) Pill(full.access, c.surfaceLow, c.muted)
                    }
                    // Segmented tab switcher, matching
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh).padding(4.dp)) {
                        ClubTab.entries.forEach { t ->
                            val selected = tab == t
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(kikoCorner(12.dp))).background(if (selected) c.secondaryContainer else Color.Transparent).kikoClickable { tab = t }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(t.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) c.onSecondaryContainer else c.muted) }
                        }
                    }
                }
            }
            item {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "club-tab", modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) { t ->
                    when (t) {
                        ClubTab.Couch -> ClubCouchSection(club.id, full, onOpenBrowser = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(full.url)) }, onOpenProfileLink = onOpenProfileLink)
                        ClubTab.Cabinet -> ClubCabinetSection(full, loading, onOpenBrowser = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it)) })
                        ClubTab.Members -> ClubMembersSection(club.id, onOpenBrowser = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it)) })
                    }
                }
            }
        }
    }
}

// Couch = the club's
@Composable private fun ClubCouchSection(clubId: Int, club: MalClub, onOpenBrowser: () -> Unit, onOpenProfileLink: (MalProfileLink) -> Unit = {}) {
    val c = LocalKikoColors.current
    val api = remember { ClubsApi() }
    var posts by remember(clubId) { mutableStateOf<List<ClubPost>>(emptyList()) }
    var loading by remember(clubId) { mutableStateOf(true) }
    var error by remember(clubId) { mutableStateOf<String?>(null) }
    LaunchedEffect(clubId) {
        loading = true; error = null
        runCatching { api.fetchCouch(clubId) }
            .onSuccess { posts = it.items }
            .onFailure { error = it.message ?: "Could not load the Couch" }
        loading = false
    }
    Column {
        if (club.description.isNotBlank()) {
            Card(shape = RoundedCornerShape(kikoCorner(20.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                ForumBody(club.description, modifier = Modifier.padding(18.dp), onOpenProfileLink = onOpenProfileLink)
            }
            Spacer(Modifier.height(18.dp))
        }
        Text("COUCH", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 10.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
            error != null -> Text(error!!, color = c.danger, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center)
            posts.isEmpty() -> {
                Text("Couldn't find any Couch posts here — the layout may not have matched what we expected.", color = c.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
                Button(onClick = onOpenBrowser, enabled = club.url.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open club room")
                }
            }
            else -> Card(shape = RoundedCornerShape(kikoCorner(20.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                Column {
                    posts.forEachIndexed { index, post ->
                        Row(Modifier.fillMaxWidth().padding(14.dp)) {
                            if (post.avatar.isNotBlank()) {
                                AsyncImage(model = post.avatar, contentDescription = post.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(38.dp).clip(kikoCircleShape()).background(c.warm))
                            } else {
                                Box(Modifier.size(38.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                                    Text(post.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(post.username.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
                                    if (post.postedLabel.isNotBlank()) Text("  ·  ${post.postedLabel}", color = c.muted, fontSize = 11.sp)
                                }
                                ForumBody(post.body, modifier = Modifier.padding(top = 3.dp), onOpenProfileLink = onOpenProfileLink)
                            }
                        }
                        if (index < posts.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 66.dp), thickness = 1.dp, color = c.outlineVariant)
                    }
                }
            }
        }
    }
}

// Cabinet = club staff/leaders
@Composable private fun ClubCabinetSection(club: MalClub, loading: Boolean, onOpenBrowser: (String) -> Unit) {
    val c = LocalKikoColors.current
    when {
        loading && club.staff.isEmpty() -> TopicRowSkeletonGroup(5)
        club.staff.isEmpty() -> Text("No staff listed for this club.", color = c.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 30.dp), textAlign = TextAlign.Center)
        else -> Card(shape = RoundedCornerShape(kikoCorner(20.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
            Column {
                club.staff.forEachIndexed { index, staff ->
                    Row(Modifier.fillMaxWidth().kikoClickable(enabled = staff.url.isNotBlank()) { onOpenBrowser(staff.url) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                            Text(staff.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(staff.username.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
                            if (staff.role.isNotBlank()) Text(staff.role, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = c.muted)
                    }
                    if (index < club.staff.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 66.dp), thickness = 1.dp, color = c.outlineVariant)
                }
            }
        }
    }
}

// Paginated member list
@Composable private fun ClubMembersSection(clubId: Int, onOpenBrowser: (String) -> Unit) {
    val c = LocalKikoColors.current
    val api = remember { ClubsApi() }
    var members by remember(clubId) { mutableStateOf<List<ClubMember>>(emptyList()) }
    var page by remember(clubId) { mutableStateOf(1) }
    var hasMore by remember(clubId) { mutableStateOf(false) }
    var loading by remember(clubId) { mutableStateOf(true) }
    var loadingMore by remember(clubId) { mutableStateOf(false) }
    var error by remember(clubId) { mutableStateOf<String?>(null) }
    LaunchedEffect(clubId) {
        loading = true; error = null
        runCatching { api.fetchMembers(clubId, 1) }
            .onSuccess { members = it.items; hasMore = it.hasMore; page = 1 }
            .onFailure { error = it.message ?: "Could not load members" }
        loading = false
    }
    Column {
        when {
            loading -> TopicRowSkeletonGroup(5)
            error != null -> Text(error!!, color = c.danger, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 30.dp), textAlign = TextAlign.Center)
            members.isEmpty() -> Text("No members listed for this club.", color = c.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 30.dp), textAlign = TextAlign.Center)
            else -> {
                Card(shape = RoundedCornerShape(kikoCorner(20.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        members.forEachIndexed { index, member ->
                            Row(Modifier.fillMaxWidth().kikoClickable(enabled = member.url.isNotBlank()) { onOpenBrowser(member.url) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (member.image.isNotBlank()) {
                                    AsyncImage(model = member.image, contentDescription = member.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(38.dp).clip(kikoCircleShape()).background(c.warm))
                                } else {
                                    Box(Modifier.size(38.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                                        Text(member.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                                    }
                                }
                                Text(member.username.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                Icon(Icons.Default.ChevronRight, null, tint = c.muted)
                            }
                            if (index < members.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 66.dp), thickness = 1.dp, color = c.outlineVariant)
                        }
                    }
                }
                if (hasMore) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) {
                            CircularProgressIndicator(color = c.primary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = {
                                loadingMore = true
                            }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Load more") }
                        }
                    }
                }
            }
        }
    }
    // Fetch the next page
    LaunchedEffect(loadingMore) {
        if (loadingMore) {
            val next = page + 1
            runCatching { api.fetchMembers(clubId, next) }
                .onSuccess { members = members + it.items; hasMore = it.hasMore; page = next }
                .onFailure { hasMore = false }
            loadingMore = false
        }
    }
}