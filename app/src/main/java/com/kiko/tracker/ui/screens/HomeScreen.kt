@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.kiko.tracker.data.api.ForumTopic
import com.kiko.tracker.data.api.NewsSnapshot
import com.kiko.tracker.data.model.DiscoverSort
import com.kiko.tracker.data.model.ListSort
import com.kiko.tracker.data.model.ListViewMode
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.TitleLanguage
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayLabel
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.localizedTimeLabel
import com.kiko.tracker.data.model.next
import com.kiko.tracker.data.model.nextAirDateTime
import com.kiko.tracker.data.model.nextEpisodeLabel
import com.kiko.tracker.data.model.systemIs24Hour
import com.kiko.tracker.ui.components.AppHeader
import com.kiko.tracker.ui.components.Avatar
import com.kiko.tracker.ui.components.Cover
import com.kiko.tracker.ui.components.ExpandableSearchHeader
import com.kiko.tracker.ui.components.statusColor
import com.kiko.tracker.ui.theme.AiringNextCardSkeleton
import com.kiko.tracker.ui.theme.AiringNextRowSkeleton
import com.kiko.tracker.ui.theme.ContinueCardSkeleton
import com.kiko.tracker.ui.theme.DetailFeaturedArticleCardSkeleton
import com.kiko.tracker.ui.theme.ListGridCardSkeleton
import com.kiko.tracker.ui.theme.ListRowSkeletonGroup
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.SnapshotsGridSkeleton
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.accent
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCombinedClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.rememberStaggerMemory
import com.kiko.tracker.viewmodel.LibraryViewModel

@Composable fun HomeScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, onList: () -> Unit, onLocateInList: (MediaItem) -> Unit, onDiscover: () -> Unit, onRanking: () -> Unit, onSeasonal: () -> Unit, onSchedule: (java.time.DayOfWeek) -> Unit, onOpenTopic: (Int, String) -> Unit, onSeeNews: () -> Unit, onOpenStack: (Int, String) -> Unit, onOpenStacks: () -> Unit, onOpenAnnouncements: () -> Unit, onSignIn: () -> Unit, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    LaunchedEffect(vm.signedIn) { vm.loadNewsSnapshots(context); vm.loadHomeAnnouncement(context) }
    LaunchedEffect(Unit) { vm.loadHomeFeaturedArticles() }
    // Testing swap: hide (not
    // MAL announcement card in
    val showContinueCard = false
    // Was recomputing (filter +
    // including ones triggered by
    // background sync — instead
    // change. Same remember(...) pattern
    val items = remember(vm.items, vm.nsfwEnabled) { vm.visibleItems }
    val active = remember(items) {
        // Most recently updated wins
        items.filter { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }.maxByOrNull { it.updatedAt }
            ?: items.firstOrNull { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }
            ?: items.firstOrNull()
    }
    val today = java.time.LocalDate.now().dayOfWeek
    // Airing-next row pool —
    // re-filtering, re-parsing dates on,
    // list on every recomposition,
    // toggling elsewhere on the
    val newSeason = vm.visibleDiscoverNewSeason
    val airingNext = remember(newSeason) {
        newSeason.mapNotNull { item -> item.nextAirDateTime()?.let { item to it } }.sortedBy { it.second }.take(5).map { it.first }
    }
    // Restore scroll position on
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.homeScrollIndex, initialFirstVisibleItemScrollOffset = vm.homeScrollOffset)
    // Persist scroll position whenever
    // detail/topic/stack, tapping "See news"/"See
    // Saving only at a
    // "See news" never saved
    // back to the top
    // instead of top, since
    // every exit path shares,
    DisposableEffect(Unit) {
        onDispose { vm.saveHomeScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    val trackedOpenDetail: (MediaItem) -> Unit = onOpenDetail
    val trackedOpenTopic: (Int, String) -> Unit = onOpenTopic
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    PullToRefreshBox(
        isRefreshing = vm.loading,
        onRefresh = { vm.load(context); vm.loadNewsSnapshots(context, force = true); vm.loadHomeAnnouncement(context, force = true); vm.loadHomeFeaturedArticles(force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = if (showGoToTop) 90.dp else 24.dp)) {
            item {
                AppHeader("kiko") { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    // Use device current date
                    Text(
                        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.getDefault())).uppercase(java.util.Locale.getDefault()),
                        color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp,
                    )
                    // Each of these home
                    // featured articles) loads independently
                    // the rest of the
                    // scrolling. Without a key(...)
                    // content lives in a
                    // anywhere in that item{}
                    // every one of these
                    // every row, every image)
                    // with the scroll gesture
                    // slow" right after opening
                    // (unlike Box/Column) — it
                    // boundary, so a read
                    key("airingNext") {
                        if (airingNext.isNotEmpty()) {
                            SectionTitle("Airing next", "See all", click = { onSchedule(today) })
                            AiringNextRow(airingNext, vm, trackedOpenDetail)
                        } else if (vm.discoverBrowseLoading) {
                            SectionTitle("Airing next", "See all", click = { onSchedule(today) })
                            AiringNextRowSkeleton()
                        }
                    }
                    // Most recently updated in-progress
                    // (see top of function)
                    // place. Nothing here was
                    if (showContinueCard) {
                        if (active != null) {
                            SectionTitle("Continue", "See list", onList)
                            ContinueCard(active, vm, onClick = { onLocateInList(active) }, onLongPress = onEdit, isSelected = selectedItem?.id == active.id && selectedItem?.type == active.type)
                        } else if (vm.loading) {
                            SectionTitle("Continue", "See list", onList)
                            ContinueCardSkeleton()
                        }
                    }
                    // Latest MAL announcement, standing
                    // sized (via AnnouncementCard's identical
                    // AiringNextCard above it, so
                    key("announcement") {
                        val announcement = vm.homeAnnouncement
                        if (announcement != null) {
                            SectionTitle("MAL Announcement", "See more", click = onOpenAnnouncements)
                            AnnouncementCard(announcement, onClick = { trackedOpenTopic(announcement.id, announcement.title) })
                        } else if (vm.homeAnnouncementLoading) {
                            SectionTitle("MAL Announcement", "See more", click = onOpenAnnouncements)
                            AiringNextCardSkeleton(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    // Home recent news row
                    key("snapshots") {
                        if (vm.newsSnapshots.isNotEmpty()) {
                            SectionTitle("Snapshots", "See news", onSeeNews)
                            // Extra breathing room here
                            // reads as more "content-dense"
                            // so it wants a
                            // default bottom padding gives
                            Spacer(Modifier.height(8.dp))
                            SnapshotsGrid(vm.newsSnapshots, trackedOpenTopic)
                        } else if (vm.newsSnapshotsLoading) {
                            SectionTitle("Snapshots", "See news", onSeeNews)
                            Spacer(Modifier.height(8.dp))
                            SnapshotsGridSkeleton()
                        }
                    }
                    // Top 3 MAL homepage
                    // (DetailFeaturedArticleCard) and "by <author>
                    // DetailScreen's own "Recent Featured
                    // same way: no in-app
                    // externally via a custom
                    key("featuredArticles") {
                        if (vm.homeFeaturedArticles.isNotEmpty()) {
                            SectionTitle("Featured Articles", "View more", { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/featured")) })
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                vm.homeFeaturedArticles.forEach { article ->
                                    DetailFeaturedArticleCard(article) { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(article.url)) }
                                }
                            }
                        } else if (vm.homeFeaturedArticlesLoading) {
                            SectionTitle("Featured Articles", "View more", { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/featured")) })
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(3) { DetailFeaturedArticleCardSkeleton() }
                            }
                        }
                    }
                    if (vm.authChecked && !vm.signedIn && !vm.loading) {
                        Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Please sign in with your MyAnimeList account", color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) { Text("Sign in with MyAnimeList") }
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
// Airing next row order

@Composable fun AiringNextRow(items: List<MediaItem>, vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit) {
    // Each card takes almost
    // with just a thin
    // that the row scrolls.
    // sliver proportional to the
    // screens or invisible on
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { items(items, key = { it.id }) { AiringNextCard(it, vm, onOpenDetail, modifier = Modifier.fillParentMaxWidth(0.94f)) } }
}
// Airing next card layout
// radius, and padding) so

@Composable fun AiringNextCard(item: MediaItem, vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val is24Hour = systemIs24Hour()
    // Best-effort AniList lookup for
    // LibraryViewModel.loadAiringEpisode) — fires once
    // date-math guess on screen
    LaunchedEffect(item.id) { vm.loadAiringEpisode(item) }
    val confirmed = vm.getCachedAiring(item.id)
    val time = item.nextAirDateTime(confirmed)?.toLocalTime()
    Box(
        modifier
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surfaceContainer)
            .kikoClickable { onOpenDetail(item) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // overrideStatus: airingNext is discoverNewSeason
            // never merged with the
            // lookup here is what
            // and disappear immediately after
            // whenever this row happens
            Cover(item, Modifier.size(width = 84.dp, height = 118.dp), showStatus = true, overrideStatus = vm.trackedStatus(item))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(item.displayTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.genre.isNotBlank()) {
                    Text(item.genre, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.height(8.dp))
                // Full card width now,
                // one line instead of
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = c.accent, modifier = Modifier.size(13.dp))
                    Text(
                        listOfNotNull(item.nextEpisodeLabel(confirmed), time?.let { localizedTimeLabel(it, is24Hour) }).joinToString(" · "),
                        color = c.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}
// Reusable rounded pill button

@Composable fun HomeActionButton(modifier: Modifier = Modifier, label: String, icon: ImageVector, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        modifier.clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.primaryContainer).kikoClickable(onClick = onClick).padding(vertical = 15.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.onPrimaryContainer, modifier = Modifier.size(19.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.onPrimaryContainer, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun SectionTitle(title: String, action: String, click: () -> Unit, icon: ImageVector = Icons.Default.ArrowForward) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.ink)
        // Rounded-square affordance instead of
        // uses on its section
        // squircle rather than a
        // round (chips, cards, and
        // language). Sections with nothing
        if (action.isNotBlank()) {
            // Plain clickable box instead
            // default 40dp touch-target sizing
            // small a button doesn't
            // is exactly what renders,
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(kikoCorner(10.dp)))
                    .background(c.surfaceContainerHigh)
                    .kikoClickable(onClick = click),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, action, tint = c.ink, modifier = Modifier.size(17.dp)) }
        }
    }
}

// Home's "Continue" entry, now
// the app (see StackFeaturedCard)
// instead of sitting as
// only the surrounding container
// List rather than opening
// into the list, not

@Composable fun ContinueCard(item: MediaItem, vm: LibraryViewModel, onClick: (MediaItem) -> Unit, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surfaceContainer),
    ) {
        ListRow(item, onClick, showType = false, onLongPress = onLongPress, isSelected = isSelected, showChevron = true, modifier = Modifier.padding(horizontal = 14.dp), vm = vm)
    }
}
// Latest-announcement card standing in
// above) — deliberately built
// padding as AiringNextCard, rather
// this shelf lines up
// above it. Falls back
// (true for most Announcements-board
@Composable fun AnnouncementCard(topic: ForumTopic, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surfaceContainer)
            .kikoClickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 84.dp, height = 118.dp)
                    .clip(RoundedCornerShape(kikoCorner(14.dp)))
                    .background(c.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (!topic.imageUrl.isNullOrBlank()) {
                    AsyncImage(model = topic.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Campaign, null, tint = c.onPrimaryContainer, modifier = Modifier.size(30.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(topic.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "by ${topic.author.name.ifBlank { "MyAnimeList" }} · ${formatForumDate(topic.createdAt)}",
                    color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Forum, null, tint = c.accent, modifier = Modifier.size(13.dp))
                    Text(
                        "${topic.postCount} replies",
                        color = c.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}
// Pinterest-style snapshots layout

@Composable fun SnapshotsGrid(snapshots: List<NewsSnapshot>, onOpenTopic: (Int, String) -> Unit) {
    val left = snapshots.filterIndexed { i, _ -> i % 2 == 0 }
    val right = snapshots.filterIndexed { i, _ -> i % 2 == 1 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            left.forEachIndexed { i, s -> SnapshotCard(s, tall = i % 2 == 0, onOpenTopic) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            right.forEachIndexed { i, s -> SnapshotCard(s, tall = i % 2 == 1, onOpenTopic) }
        }
    }
}
// Snapshot card title overlay

@Composable fun SnapshotCard(snapshot: NewsSnapshot, tall: Boolean, onOpenTopic: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (tall) 210.dp else 160.dp)
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .kikoClickable { onOpenTopic(snapshot.topicId, snapshot.title) },
    ) {
        AsyncImage(model = snapshot.imageUrl, contentDescription = snapshot.title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = .35f),
                            1f to Color.Black.copy(alpha = .92f),
                        ),
                    ),
                ),
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                snapshot.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(shadow = Shadow(color = Color.Black.copy(alpha = .8f), offset = Offset(0f, 1f), blurRadius = 4f)),
            )
        }
    }
}

fun progressLabel(i: MediaItem) = if (i.progress == 0) i.status.displayLabel(i.type) else "${i.progress} of ${if (i.total > 0) i.total.toString() else "?"} ${if (i.type == MediaType.Anime) "episodes" else "chapters"}"
// Same as progressLabel, but
// the grid tile, where
fun compactProgressLabel(i: MediaItem) = if (i.progress == 0) i.status.displayLabel(i.type) else "${i.progress} of ${if (i.total > 0) i.total.toString() else "?"} ${if (i.type == MediaType.Anime) "ep." else "ch."}"
// Format field fallback

fun formatLabel(i: MediaItem): String = i.format.ifBlank { if (i.type == MediaType.Anime) "Anime" else "Manga" }

// Translate status label

fun normalizeFilterForType(filter: String, type: MediaType): String = when (filter) {
    "Watching", "Reading" -> if (type == MediaType.Anime) "Watching" else "Reading"
    "Plan to Watch", "Plan to Read" -> if (type == MediaType.Anime) "Plan to Watch" else "Plan to Read"
    else -> filter
}
// My List sort logic

fun MediaItem.resolvedTitle(pref: TitleLanguage): String =
    if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) titleEnglish else title

fun List<MediaItem>.sortedWithListSort(sort: ListSort, titleLanguage: TitleLanguage): List<MediaItem> = when (sort) {
    ListSort.Title -> sortedBy { it.resolvedTitle(titleLanguage).lowercase() }
    ListSort.Score -> sortedWith(compareByDescending<MediaItem> { it.myRating > 0 }.thenByDescending { it.myRating })
    ListSort.LastUpdated -> sortedWith(compareByDescending<MediaItem> { it.updatedAt.isNotBlank() }.thenByDescending { it.updatedAt })
    ListSort.StartDate -> sortedWith(compareByDescending<MediaItem> { it.watchStartDate.isNotBlank() }.thenByDescending { it.watchStartDate })
}
// Compact sort dropdown

@Composable fun SortMenu(current: ListSort, onSelect: (ListSort) -> Unit) {
    val c = LocalKikoColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHigh).kikoClickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sort, "Sort", tint = c.accent, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceContainerHigh, shape = RoundedCornerShape(kikoCorner(18.dp))) {
            ListSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.accent else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(s); open = false },
                )
            }
        }
    }
}
// Discover results sort dropdown

@Composable fun DiscoverSortMenu(current: DiscoverSort, onSelect: (DiscoverSort) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHigh).kikoClickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sort, "Sort", tint = c.accent, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceContainerHigh, shape = RoundedCornerShape(kikoCorner(18.dp))) {
            DiscoverSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.accent else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(s); open = false },
                )
            }
        }
    }
}


@Composable fun ListScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, onIncrement: (MediaItem) -> Unit, onEdit: (MediaItem) -> Unit = {}, selectedItem: MediaItem? = null) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Search only on submit
    var submittedQuery by remember { mutableStateOf("") }
    // Search bar starts collapsed
    // the whole header row
    var searchExpanded by remember { mutableStateOf(false) }
    val typeTab = vm.listTypeTab
    val effectiveFilter = normalizeFilterForType(vm.listFilter, typeTab)
    // Was recomputing filter+sort over
    // ones triggered by unrelated
    // — instead of only
    // remember(...) pattern ScoreFilterScreen/YearFilterScreen already
    val filtered = remember(vm.items, vm.nsfwEnabled, typeTab, effectiveFilter, submittedQuery, vm.listSort, vm.titleLanguage) {
        vm.visibleItems
            .filter { it.type == typeTab && (effectiveFilter == "All" || it.status.displayLabel(typeTab) == effectiveFilter) && it.title.contains(submittedQuery, true) }
            .sortedWithListSort(vm.listSort, vm.titleLanguage)
    }
    // Status filter now lives
    // old FilterRow chip row,
    var filterMenuOpen by remember { mutableStateOf(false) }
    val isGrid = vm.listViewMode == ListViewMode.Grid
    // Shared between grid and
    // index that's already played
    // switching to the other,
    val staggerSeen = rememberStaggerMemory()
    // Restore list scroll position
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.listScrollIndex, initialFirstVisibleItemScrollOffset = vm.listScrollOffset)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = vm.listScrollIndex, initialFirstVisibleItemScrollOffset = vm.listScrollOffset)
    val openItem: (MediaItem) -> Unit = remember(onOpenDetail, isGrid) {
        {
                item ->
            if (isGrid) vm.saveListScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            else vm.saveListScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            onOpenDetail(item)
        }
    }
    val header: @Composable () -> Unit = {
        // Type switcher lives in
        // instead of a separate
        // search icon sits just
        // row when tapped, hiding
        ExpandableSearchHeader(
            current = typeTab,
            options = MediaType.entries.toList(),
            labelFor = { if (it == MediaType.Anime) "Anime" else "Manga" },
            onSelect = { vm.selectListTypeTab(context, it) },
            query = query,
            onQueryChange = { query = it },
            onSearch = { submittedQuery = query },
            onClear = { query = ""; submittedQuery = "" },
            expanded = searchExpanded,
            onExpandedChange = { expanded -> searchExpanded = expanded; if (!expanded) { query = ""; submittedQuery = "" } },
            hint = "Search your list",
            horizontalPadding = 0.dp,
            switchDescription = "Switch between Anime and Manga",
        ) { Avatar(vm.malProfile?.picture.orEmpty(), vm.malProfile?.name.orEmpty()) { rect -> vm.profileDrawerOpen = true; vm.profileMenuAnchor = rect } }
        if (vm.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), color = c.accent, trackColor = c.surfaceLow)
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} titles" + if (vm.loading) " · syncing…" else "", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.listViewMode) { vm.setListViewMode(context, it) }
                SortMenu(vm.listSort) { vm.setListSort(context, it) }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { if (isGrid) gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 600 else listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Go-to-top (left) and the
    // stacking, so a flat
    val bottomInset = 90.dp
    PullToRefreshBox(isRefreshing = vm.loading, onRefresh = { vm.load(context) }, modifier = Modifier.fillMaxSize()) {
        // Basic cross-fade when switching
        // transition used elsewhere in
        AnimatedContent(
            isGrid,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "list-view-mode",
        ) { grid ->
            if (grid) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomInset),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
                    if (vm.loading && filtered.isEmpty()) {
                        items(9) { i -> StaggeredItem(i) { ListGridCardSkeleton() } }
                    } else {
                        itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, openItem, onIncrement, onLongPress = onEdit, isSelected = selectedItem?.id == item.id && selectedItem?.type == item.type) } }
                    }
                    if (!vm.loading && filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomInset)) {
                    item { header() }
                    if (vm.loading && filtered.isEmpty()) {
                        item { ListRowSkeletonGroup(6) }
                    } else {
                        itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                            StaggeredItem(index, staggerSeen) {
                                Column {
                                    ListRow(it, openItem, onIncrement, showType = false, onLongPress = onEdit, isSelected = selectedItem?.id == it.id && selectedItem?.type == it.type, vm = vm)
                                    if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                                }
                            }
                        }
                    }
                    if (!vm.loading && filtered.isEmpty()) item { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
        // Dim scrim behind the
        // same interaction as Google
        AnimatedVisibility(
            visible = filterMenuOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { filterMenuOpen = false }
            )
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { if (isGrid) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
        )
        StatusFilterFab(
            effectiveFilter, { vm.setListFilter(context, it) }, typeTab,
            expanded = filterMenuOpen, onExpandedChange = { filterMenuOpen = it },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// List/grid switcher

@Composable fun ListViewModeToggle(current: ListViewMode, onSelect: (ListViewMode) -> Unit) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(kikoCorner(12.dp)))
            .background(c.surfaceContainerHigh)
            .kikoClickable { onSelect(if (current == ListViewMode.List) ListViewMode.Grid else ListViewMode.List) }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (current == ListViewMode.List) Icons.Default.GridView else Icons.Default.ViewList,
            contentDescription = if (current == ListViewMode.List) "Switch to grid view" else "Switch to list view",
            tint = c.accent, modifier = Modifier.size(16.dp),
        )
    }
}
// Compact grid tile —

@Composable fun ListGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, onIncrement: ((MediaItem) -> Unit)? = null, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "gridSelectBg")
    val pad by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "gridSelectPad")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(bg)
            .kikoCombinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
            )
            // animateDpAsState on `pad` above
            // value frame-by-frame, so the
            // animateContentSize() here was a
            // wrapping every card in
            // transition. Same fix as
            .padding(pad)
    ) {
        // Height matches ListGridCardSkeleton's cover
        // real card don't jump
        Cover(item, Modifier.fillMaxWidth().height(160.dp), showStatus = true, selected = isSelected)
        // Fixed to 2 lines
        Text(
            item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, color = c.ink,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        // Always reserve the progress
        // there's nothing to show,
        // across every card in
        // Total unknown (e.g. an
        // yet) intentionally shows no
        // distracting, so the "2
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp)) {
            if (onIncrement != null && item.total > 0) {
                LinearProgressIndicator(progress = { item.progress.toFloat() / item.total }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(kikoCorner(4.dp))), color = statusColor(item.status), trackColor = c.surfaceLow)
            }
        }
        // Small always-on inset (independent
        // card corner doesn't clip
        Text(compactProgressLabel(item), color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, start = 3.dp, bottom = 2.dp))
    }
}
// Anime/Manga segmented switch

// Status filter — bottom-right
// old horizontal FilterChip row.
// tap the FAB (or

@Composable fun StatusFilterFab(current: String, set: (String) -> Unit, type: MediaType, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val progressLabel = if (type == MediaType.Anime) "Watching" else "Reading"
    val planLabel = if (type == MediaType.Anime) "Plan to Watch" else "Plan to Read"
    val labels = listOf("All", progressLabel, planLabel, "Completed", "On Hold", "Dropped")

    Column(modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(200), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                // Reversed so the list
                // since each new option
                labels.reversed().forEach { label ->
                    StatusFilterOption(label, filterLabelIcon(label), selected = current == label) { set(label); onExpandedChange(false) }
                }
            }
        }
        // Extended (icon + text)
        // on the button itself
        ExtendedFloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = c.primary,
            contentColor = c.onPrimary,
            icon = { Icon(if (expanded) Icons.Default.Close else filterLabelIcon(current), contentDescription = null) },
            text = { Text(if (expanded) "Close" else current) },
        )
    }
}
// One row of the
// Google Keep's note-type FAB
// Shaped as a squircle
// and every other chip/card/button

@Composable fun StatusFilterOption(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(if (selected) c.primary else c.surfaceContainerHigh)
            .kikoClickable(scale = 0.94f) { onClick() }
            .padding(start = 18.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) c.onPrimary else c.ink, modifier = Modifier.size(20.dp))
        Text(label, color = if (selected) c.onPrimary else c.ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
// Icon per status filter

fun filterLabelIcon(label: String): ImageVector = when (label) {
    "All" -> Icons.Default.Apps
    "Watching", "Reading" -> Icons.Default.PlayArrow
    "Plan to Watch", "Plan to Read" -> Icons.Default.Bookmark
    "Completed" -> Icons.Default.Check
    "On Hold" -> Icons.Default.Pause
    "Dropped" -> Icons.Default.Close
    else -> Icons.Default.FilterList
}

// vm is optional (and,
// screens that don't pass
@Composable fun ListRow(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, onIncrement: ((MediaItem) -> Unit)? = null, showType: Boolean = true, modifier: Modifier = Modifier, onLongPress: ((MediaItem) -> Unit)? = null, isSelected: Boolean = false, showChevron: Boolean = false, vm: LibraryViewModel? = null) {
    val c = LocalKikoColors.current
    val haptic = LocalHapticFeedback.current
    if (vm != null) LaunchedEffect(item.id) { vm.loadAiringEpisode(item) }
    val confirmed = vm?.getCachedAiring(item.id)
    val bg by animateColorAsState(if (isSelected) c.primaryContainer else Color.Transparent, label = "rowSelectBg")
    val hPad by animateDpAsState(if (isSelected) 10.dp else 0.dp, label = "rowSelectPad")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(16.dp)))
            .background(bg)
            .kikoCombinedClickable(
                onClick = { onOpenDetail(item) },
                onLongClick = onLongPress?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit(item) } },
            )
            .padding(horizontal = hPad, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(item, Modifier.size(width = 92.dp, height = 128.dp), selected = isSelected)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (showType) "${item.type} · ${item.genre}" else item.genre, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.myRating > 0) {
                    Text("  ·  ", color = c.muted, fontSize = 13.sp)
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                    Text(item.myRating.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
            if (item.total > 0) {
                LinearProgressIndicator(progress = { item.progress.toFloat() / item.total }, modifier = Modifier.fillMaxWidth(0.75f).padding(top = 9.dp).height(4.dp).clip(RoundedCornerShape(kikoCorner(4.dp))), color = statusColor(item.status), trackColor = c.surfaceLow)
            }
            Text(progressLabel(item), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            item.nextEpisodeLabel(confirmed)?.let { label ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = c.accent, modifier = Modifier.size(12.dp))
                    Text(label, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (onIncrement != null) {
            val atMax = item.total > 0 && item.progress >= item.total
            // Compact squircle instead of
            // its own wide rectangle,
            // horizontal room for a
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(kikoCorner(12.dp)))
                    .background(if (atMax) c.surfaceContainerHigh else c.primaryContainer)
                    .kikoClickable(enabled = !atMax) {
                        val next = (item.progress + 1).let { p -> if (item.total > 0) minOf(p, item.total) else p }
                        onIncrement(item.copy(progress = next))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+1", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (atMax) c.muted else c.onPrimaryContainer)
            }
        } else if (showChevron) {
            // No increment action here
            // trailing slot instead of
            Icon(Icons.Default.ChevronRight, null, tint = c.muted, modifier = Modifier.size(22.dp))
        }
    }
}

// Ranking chart screen