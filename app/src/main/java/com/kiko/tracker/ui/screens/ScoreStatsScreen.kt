@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.ReviewEntry
import com.kiko.tracker.data.model.ReviewVerdictTags
import com.kiko.tracker.data.model.ScoreStats
import com.kiko.tracker.data.model.verdict
import com.kiko.tracker.data.model.verdictColor
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
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
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

// "See more" sheet from
// Detail's Reviews row —
// re-runs onLoad, which just
// hands back the same
// cached list the row
// already fetched (see LibraryViewModel.loadReviews),
// so this doesn't re-hit
// MAL. Tapping a review
// opens the full ReviewScreen readout
// on top of this
// sheet (see onOpenReview at
// the Navigation.kt call site) rather
// than dismissing it, so
// this composable gets torn
// down and rebuilt when
// that happens — initialReviews/initialScroll seed
// it back to exactly
// where it was instead
// of a fresh "loading"
// state, and onLeaveScroll captures
// the scroll position on
// that same teardown.
@Composable fun ReviewListSheet(
    item: MediaItem, onDismiss: () -> Unit,
    onLoad: ((List<ReviewEntry>) -> Unit, () -> Unit) -> Unit,
    onOpenReview: (ReviewEntry) -> Unit,
    initialReviews: List<ReviewEntry> = emptyList(),
    initialScroll: Pair<Int, Int> = 0 to 0,
    onLeaveScroll: (Int, Int) -> Unit = { _, _ -> },
) {
    val c = LocalKikoColors.current
    var reviews by remember(item.id, item.type) { mutableStateOf(initialReviews) }
    var loading by remember(item.id, item.type) { mutableStateOf(initialReviews.isEmpty()) }
    val listState = remember(item.id, item.type) { LazyListState(initialScroll.first, initialScroll.second) }
    LaunchedEffect(item.id, item.type) { onLoad({ reviews = it }, { loading = false }) }
    DisposableEffect(item.id, item.type) {
        onDispose { onLeaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).heightIn(max = 640.dp)) {
            Text("Reviews", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(item.title, style = MaterialTheme.typography.headlineSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            when {
                loading && reviews.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                reviews.isEmpty() -> Text("No reviews yet.", color = c.muted, fontSize = 12.sp)
                else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(reviews, key = { it.malId }) { rev -> ReviewListItem(rev, onClick = { onOpenReview(rev) }) }
                }
            }
        }
    }
}

// Full-width counterpart to
// DetailScreen's 260dp-wide ReviewCard, for
// the vertical list inside
// ReviewListSheet.
@Composable fun ReviewListItem(entry: ReviewEntry, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHigh).kikoClickable(onClick = onClick).padding(14.dp),
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
    }
}