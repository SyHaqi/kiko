@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.ScoreStats
import com.kiko.tracker.ui.theme.LocalKikoColors

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