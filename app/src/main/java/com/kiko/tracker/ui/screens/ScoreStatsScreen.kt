@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.AnimeForumFilter
import com.kiko.tracker.data.api.ForumTopic
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.ReviewEntry
import com.kiko.tracker.data.model.ReviewReactionEmojis
import com.kiko.tracker.data.model.ReviewVerdictTags
import com.kiko.tracker.data.model.ScoreStats
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.verdict
import com.kiko.tracker.data.model.verdictColor
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner

// Bottom sheet opened from
// shows that title's community
// 1-10), scraped from MAL's
// aggregate mean, never the
// A sheet rather than
// Dialogs.kt/DetailScreen.kt: a quick look
// around, so it doesn't
@Composable fun ScoreStatsSheet(
    item: MediaItem, onDismiss: () -> Unit,
    onLoad: ((ScoreStats) -> Unit, () -> Unit) -> Unit,
) {
    val c = LocalKikoColors.current
    var stats by remember(item.id, item.type) { mutableStateOf<ScoreStats?>(null) }
    var loading by remember(item.id, item.type) { mutableStateOf(true) }
    LaunchedEffect(item.id, item.type) { loading = true; onLoad({ stats = it }, { loading = false }) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Score Stats", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(item.title, style = MaterialTheme.typography.headlineSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, bottom = 20.dp))
            when {
                loading && stats == null -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = c.primary, size = 28.dp)
                }
                stats == null || stats!!.total == 0 -> Text("No score data yet.", color = c.muted, fontSize = 12.sp)
                else -> {
                    Text("${stats!!.total} vote${if (stats!!.total == 1) "" else "s"}", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 16.dp))
                    CommunityScoreDistributionChart(stats!!, c)
                }
            }
        }
    }
}

// Full "Reviews" page opened from Detail's Reviews row "See more" (see
// TopScreen.ReviewList). onLoad re-runs the same cached loader the row already
// used (LibraryViewModel.loadReviews), so this doesn't re-hit MAL. Tapping a review
// opens the full ReviewScreen on top of this page (reviewOpen outranks it in
// Navigation.kt) rather than dismissing it, so this composable is torn down and
// rebuilt when that happens — initialReviews/initialScroll seed it back to exactly
// where it was instead of a fresh "loading" state, and onLeaveScroll captures the
// scroll position on that same teardown.
//
// Rows are grouped tonal rows (big outer corners, tight inner ones, a 2dp gap
// instead of dividers) — same look as Clubs / Settings / Detail info rows.
@Composable fun ReviewListScreen(
    item: MediaItem, onBack: () -> Unit,
    onLoad: ((List<ReviewEntry>) -> Unit, () -> Unit) -> Unit,
    onOpenReview: (ReviewEntry) -> Unit,
    initialReviews: List<ReviewEntry> = emptyList(),
    initialScroll: Int = 0,
    onLeaveScroll: (Int) -> Unit = {},
) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    var reviews by remember(item.id, item.type) { mutableStateOf(initialReviews) }
    var loading by remember(item.id, item.type) { mutableStateOf(initialReviews.isEmpty()) }
    val scrollState = rememberScrollState(initial = initialScroll)
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { scrollState.value > 600 } }
    LaunchedEffect(item.id, item.type) { onLoad({ reviews = it }, { loading = false }) }
    DisposableEffect(item.id, item.type) {
        onDispose { onLeaveScroll(scrollState.value) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 14.dp).padding(bottom = if (showGoToTop) 90.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = KikoHeader.BackTop, bottom = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
                Column(Modifier.headerTitleStart()) {
                    Text("Reviews", style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Text(item.displayTitle(), color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            when {
                loading && reviews.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = c.primary, size = 28.dp)
                }
                reviews.isEmpty() -> Text("No reviews yet.", color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
                else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    reviews.forEachIndexed { i, rev ->
                        key(rev.malId) { ReviewListItem(rev, forumGroupShape(i, reviews.size), onClick = { onOpenReview(rev) }) }
                    }
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { scrollState.animateScrollTo(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
        )
    }
}

// "See more" sheet from Detail's Recent Forum Discussion row —
// same sheet-over-current-screen shape the old Reviews tray had, except the row above
// only ever shows a 2-item preview (parseDetailForumDiscussion's own
// limit), so this always re-fetches the full per-title /forum
// listing rather than reading back an already-complete cached list.
// The three FilterChips mirror MAL's own "All Topics | Episodes
// | Other" tab strip on that page (see AnimeForumFilter) —
// switching one re-runs onLoad with the new filter, which
// LibraryViewModel.loadForumDiscussionList caches per-filter so flipping back
// to a tab already viewed this sheet-visit doesn't re-hit the
// network. Tapping a topic opens the full ForumTopicScreen on
// top of this sheet (see onOpenTopic at the Navigation.kt call
// site) the same way the Reviews page's onOpenReview does, rather
// than dismissing it — reusing DetailForumDiscussionRow itself so the
// row here looks identical to the preview it's expanding.
@Composable fun ForumDiscussionListSheet(
    item: MediaItem, onDismiss: () -> Unit,
    onLoad: (AnimeForumFilter, (List<ForumTopic>) -> Unit, () -> Unit) -> Unit,
    onOpenTopic: (Int, String) -> Unit,
) {
    val c = LocalKikoColors.current
    var filter by remember(item.id, item.type) { mutableStateOf(AnimeForumFilter.All) }
    var topics by remember(item.id, item.type) { mutableStateOf<List<ForumTopic>>(emptyList()) }
    var loading by remember(item.id, item.type) { mutableStateOf(true) }
    LaunchedEffect(item.id, item.type, filter) {
        loading = true
        topics = emptyList()
        onLoad(filter, { topics = it }, { loading = false })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Forum", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(item.title, style = MaterialTheme.typography.headlineSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                AnimeForumFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }, colors = kikoFilterChipColors())
                }
            }
            when {
                loading && topics.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = c.primary, size = 28.dp)
                }
                topics.isEmpty() -> Text("No topics yet.", color = c.muted, fontSize = 12.sp)
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    topics.forEachIndexed { i, topic ->
                        DetailForumDiscussionRow(topic) { onOpenTopic(topic.id, topic.title) }
                        if (i < topics.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.outlineVariant)
                    }
                }
            }
        }
    }
}

// Full-width counterpart to
// DetailScreen's 260dp-wide ReviewCard, for
// the vertical list on ReviewListScreen. One tonal row of a grouped list —
// pass forumGroupShape(index, count) for the big-outer / tight-inner corners.
@Composable fun ReviewListItem(entry: ReviewEntry, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(kikoCorner(18.dp)), onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainer).kikoClickable(scale = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.userImage.isNotBlank()) {
                AsyncImage(model = entry.userImage, contentDescription = entry.username, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(kikoCircleShape()).background(c.warm))
            } else {
                Box(Modifier.size(32.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                    Text(entry.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
                }
            }
            Text(entry.username, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
            if (entry.score > 0) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                Text(entry.score.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
        val verdict = entry.verdict()
        val otherTags = entry.tags.filterNot { it in ReviewVerdictTags }
        if (verdict != null || otherTags.isNotEmpty()) {
            Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                verdict?.let {
                    Icon(Icons.Default.Star, null, tint = verdictColor(it, c), modifier = Modifier.size(12.dp))
                    Text(it, color = verdictColor(it, c), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                }
                otherTags.firstOrNull()?.let {
                    Text(it, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = if (verdict != null) 8.dp else 0.dp))
                }
            }
        }
        if (entry.isSpoiler) Text("Contains spoilers", color = c.danger, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Text(entry.review, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        ReviewReactions(entry, Modifier.padding(top = 10.dp))
    }
}

// MAL reactions for a review: the three most-used emojis, then the total number of
// reactions — same readout as the preview ReviewCard on the detail page, shared by the
// "See more" page (ReviewListItem) and the full ReviewScreen. Draws nothing when MAL
// reported no reactions for the review.
@Composable fun ReviewReactions(entry: ReviewEntry, modifier: Modifier = Modifier, emojiSize: androidx.compose.ui.unit.TextUnit = 13.sp, countSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    val c = LocalKikoColors.current
    val topEmojis: List<String> = entry.reactionCounts
        .mapIndexed { i, n -> Pair(i, n) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(3)
        .mapNotNull { ReviewReactionEmojis.getOrNull(it.first) }
    val totalReactions = if (entry.reactionCounts.isNotEmpty()) entry.reactionCounts.sum() else entry.reactionScore
    if (topEmojis.isEmpty() || totalReactions <= 0) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(topEmojis.joinToString(""), fontSize = emojiSize)
        Text(totalReactions.toString(), color = c.muted, fontSize = countSize, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}