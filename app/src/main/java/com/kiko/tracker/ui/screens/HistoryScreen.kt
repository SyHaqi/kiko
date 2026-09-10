@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.api.MalHistoryEntry
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.viewmodel.LibraryViewModel

/**
 * Full history list — the "See more" destination off Home's "Last Updated
 * List" section. Same compact HistoryRow design as Home (see
 * HomeScreen.kt — HistoryRow/HistoryCover/historyCoverColor/
 * openHistoryDetail all live there and are reused here unchanged), just
 * the complete feed instead of the top 5, grouped under MAL's own day
 * headers (entry.dayLabel) the way myanimelist.net/history itself groups
 * them. This is the one difference from Home, which deliberately skips
 * day grouping in favor of a flat list with date/time per row — see
 * HomeScreen's `lastUpdated` comment.
 */
@Composable fun HistoryScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(vm.malProfile?.name) { vm.malProfile?.name?.let { vm.loadHistory(context, it) } }

    val items = remember(vm.items, vm.nsfwEnabled) { vm.visibleItems }
    // MAL's history page already comes back with same-day rows contiguous
    // (newest first) — this just walks that run and cuts a new bucket
    // whenever dayLabel changes, rather than a groupingBy that would
    // reorder or merge non-adjacent runs of the same label.
    val grouped = remember(vm.history) {
        val out = mutableListOf<Pair<String, List<MalHistoryEntry>>>()
        var label: String? = null
        var bucket = mutableListOf<MalHistoryEntry>()
        for (entry in vm.history) {
            if (entry.dayLabel != label) {
                if (label != null) out += label to bucket
                label = entry.dayLabel
                bucket = mutableListOf()
            }
            bucket += entry
        }
        if (label != null) out += label to bucket
        out
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = vm.historyLoading && vm.history.isNotEmpty(),
            onRefresh = { vm.malProfile?.name?.let { vm.loadHistory(context, it, force = true) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        Text("History", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                if (vm.historyLoading && vm.history.isEmpty()) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = c.primary)
                        }
                    }
                } else if (vm.history.isEmpty()) {
                    item(key = "empty") {
                        Text("No history yet — episode and chapter updates you make will show up here.", color = c.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
                    }
                }
                grouped.forEach { (label, entries) ->
                    item(key = "day:$label") {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, color = c.muted, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                    }
                    itemsIndexed(entries, key = { index, it -> "${it.type}:${it.mediaId}:${it.timeLabel}:$index" }) { _, entry ->
                        val cover = remember(entry, items) { historyCoverColor(entry, items) }
                        HistoryRow(entry, cover.first, cover.second, showDay = false) { openHistoryDetail(entry, items, context, vm, onOpenDetail) }
                    }
                }
            }
        }
    }
}