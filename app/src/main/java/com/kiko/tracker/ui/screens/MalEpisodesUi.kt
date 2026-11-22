@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.api.EpisodeDetail
import com.kiko.tracker.data.api.EpisodeEntry
import com.kiko.tracker.data.api.MalEpisodesApi
import com.kiko.tracker.ui.components.KikoErrorState
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.util.openInBrowser
import kotlinx.coroutines.launch

/**
 * Every episode of an anime as MAL lists it: air date, the community's episode rating, and the episode's forum thread
 * (which opens in the in-app forum). Tapping a row opens a sheet with the synopsis and a link to where MAL says it can be watched.
 */
@Composable fun MalEpisodesScreen(malId: Int, title: String, onBack: () -> Unit, onOpenTopic: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    val scope = rememberCoroutineScope()
    val api = remember { MalEpisodesApi() }
    val cache = BrowseCache.episodesFor(malId)
    var items by remember(malId) { mutableStateOf(cache.items) }
    var hasMore by remember(malId) { mutableStateOf(cache.hasMore) }
    var loading by remember(malId) { mutableStateOf(!cache.loaded) }
    val listState = remember(malId) { androidx.compose.foundation.lazy.LazyListState(cache.index, cache.offset) }
    DisposableEffect(malId) { onDispose { cache.index = listState.firstVisibleItemIndex; cache.offset = listState.firstVisibleItemScrollOffset } }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember(malId) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<EpisodeEntry?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(malId, reload) {
        if (cache.loaded && reload == 0) return@LaunchedEffect
        loading = true; error = null
        runCatching { api.episodes(malId, title, 0) }
            .onSuccess { items = it.items; hasMore = it.hasMore; cache.items = it.items; cache.hasMore = it.hasMore; cache.loaded = true }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "Couldn't load the episode list" }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        MalAccountHeader("Episodes", onBack, subtitle = title)
        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            error != null -> KikoErrorState(error!!, onRetry = { reload++ })
            items.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Movie, null, tint = c.muted, modifier = Modifier.size(44.dp))
                Text("No episodes listed yet", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
                Text(title, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(items, key = { _, e -> e.number }) { i, e ->
                    StaggeredItem(i) { EpisodeRow(e, forumGroupShape(i, items.size, 22.dp), onOpenTopic = { onOpenTopic(e.topicId, "$title - Episode ${e.number} Discussion") }) { open = e } }
                }
                if (hasMore) item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) KikoLoadingIndicator(color = c.primary, size = 28.dp)
                        else TextButton(onClick = {
                            loadingMore = true
                            scope.launch {
                                runCatching { api.episodes(malId, title, items.size) }
                                    .onSuccess { more -> val fresh = more.items.filter { m -> items.none { it.number == m.number } }; items = items + fresh; hasMore = more.hasMore && fresh.isNotEmpty(); cache.items = items; cache.hasMore = hasMore }
                                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; hasMore = false }
                                loadingMore = false
                            }
                        }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("See more") }
                    }
                }
            }
        }
    }
    open?.let { e -> EpisodeSheet(malId, title, e, onOpenTopic = { open = null; onOpenTopic(e.topicId, "$title - Episode ${e.number} Discussion") }, onDismiss = { open = null }) }
}

@Composable private fun EpisodeRow(e: EpisodeEntry, shape: androidx.compose.ui.graphics.Shape, onOpenTopic: () -> Unit, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.primaryContainer), contentAlignment = Alignment.Center) {
            Text(e.number.toString(), color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = if (e.number >= 1000) 11.sp else 14.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(e.aired.takeIf { it.isNotBlank() }, e.pollAverage.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
        }
        if (e.topicId != 0) {
            Row(
                Modifier.clip(kikoPillShape()).background(c.surfaceContainerHighest).kikoClickable(onClick = onOpenTopic).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Forum, "Discussion", tint = c.muted, modifier = Modifier.size(14.dp))
                Text(e.replies.toString(), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

@Composable private fun EpisodeSheet(malId: Int, title: String, e: EpisodeEntry, onOpenTopic: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val api = remember { MalEpisodesApi() }
    var detail by remember(e.number) { mutableStateOf<EpisodeDetail?>(null) }
    var failed by remember(e.number) { mutableStateOf(false) }
    LaunchedEffect(e.number) {
        runCatching { api.episode(malId, title, e.number) }.onSuccess { detail = it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; failed = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp).navigationBarsPadding()) {
            Text("Episode ${e.number}", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(e.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 4.dp))
            if (e.altTitle.isNotBlank()) Text(e.altTitle, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            val d = detail
            val meta = listOfNotNull(e.aired.takeIf { it.isNotBlank() }, d?.duration?.takeIf { it.isNotBlank() && it != "00:00:00" }, e.pollAverage.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            when {
                d == null && !failed -> Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 28.dp) }
                d != null && d.synopsis.isNotBlank() -> Text(d.synopsis, color = c.ink, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 16.dp))
                else -> Text("No synopsis has been added for this episode.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (e.topicId != 0) FilledTonalButton(onClick = onOpenTopic, shape = kikoPillShape()) { Icon(Icons.Rounded.Forum, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Discussion (${e.replies})") }
                val watch = d?.watchUrl.orEmpty()
                if (watch.isNotBlank()) Button(
                    onClick = { context.openInBrowser(watch) }, shape = kikoPillShape(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                ) { Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Watch", fontWeight = FontWeight.Medium) }
            }
        }
    }
}
