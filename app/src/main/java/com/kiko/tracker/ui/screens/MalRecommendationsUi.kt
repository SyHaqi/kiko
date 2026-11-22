@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.widget.Toast
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.api.MalSocialApi
import com.kiko.tracker.data.api.MyRecEntry
import com.kiko.tracker.data.api.MyRecs
import com.kiko.tracker.data.api.RecEditor
import com.kiko.tracker.data.api.RecPair
import com.kiko.tracker.data.api.RecommendedEntry
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.ui.components.KikoErrorState
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import kotlinx.coroutines.launch

private fun friendlyError(t: Throwable): String =
    if (t is MalSessionExpired) "Your MAL login expired. Reconnect from Profile and try again." else t.message?.takeIf { it.isNotBlank() } ?: "That didn't work"

// Entry row on a title's page, same look as the "Write a review" row.
@Composable fun RecommendRow(onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceLow).kikoClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ThumbUp, null, tint = c.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Recommend a similar title", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Public on MyAnimeList, from titles on your list", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// Full list behind "See more" on a title's Recommended row. "Recommend a similar title" lives at the top (null hides it).
@Composable fun RecommendedListSheet(
    title: String, entries: List<RecommendedEntry>, myListStatus: Map<Pair<Int, MediaType>, WatchStatus>,
    onRecommend: (() -> Unit)?, onOpen: (RecommendedEntry) -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalKikoColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                Text("Recommended", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("For people who liked $title", color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (onRecommend != null) RecommendRow(onRecommend)
                Spacer(Modifier.height(14.dp))
            }
            if (entries.isEmpty()) item { Text("Nobody has recommended anything for this title yet.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            itemsIndexed(entries, key = { _, e -> "${e.malId}-${e.malType}" }) { i, e ->
                val type = if (e.malType == "manga") MediaType.Manga else MediaType.Anime
                val subtitle = when {
                    e.isAuto -> "AutoRec"
                    e.votes > 0 -> "${e.votes} recommend${if (e.votes == 1) "s" else ""}"
                    else -> "Recommended"
                }
                Row(
                    Modifier.fillMaxWidth().clip(forumGroupShape(i, entries.size, 22.dp)).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f) { onOpen(e) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(e.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(kikoCorner(10.dp))).background(c.surfaceLow))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(e.displayTitle(), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        myListStatus[e.malId to type]?.let { Text(it.label, color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** "If you liked [source], try [target]": pick a title from the user's own list and say why. Posts to MAL. */
@Composable fun RecommendSheet(source: MediaItem, library: List<MediaItem>, onDismiss: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sourceId = source.id.toIntOrNull()
    val onList = sourceId != null && library.any { it.id == source.id && it.type == source.type }
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<MediaItem?>(null) }
    var text by rememberSaveable(source.id, source.type) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val noun = if (source.type == MediaType.Anime) "anime" else "manga"
    val candidates = remember(library, query, source) {
        library.filter { it.type == source.type && it.id != source.id && it.id.toIntOrNull() != null && (query.isBlank() || it.title.contains(query, true) || it.titleEnglish.contains(query, true)) }
            .sortedBy { it.title.lowercase() }.take(80)
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState, containerColor = c.surfaceContainerLow, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 20.dp)) {
            Text("Recommend a similar $noun", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(source.title, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            if (!onList) {
                Text(
                    "MAL only lets you recommend from titles on your own list. Add this $noun to your list first, then come back.",
                    color = c.ink, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(kikoCorner(20.dp))).background(c.surfaceContainerHigh).padding(18.dp),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) { Text("Close") }
                return@Column
            }

            Text("Which title is similar?", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search your list") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.muted) }, shape = RoundedCornerShape(kikoCorner(16.dp)), colors = settingsFieldColors(c), modifier = Modifier.fillMaxWidth(),
            )
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (candidates.isEmpty()) Text("Nothing on your list matches.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                candidates.forEachIndexed { i, it ->
                    val selected = target?.id == it.id
                    Row(
                        Modifier.fillMaxWidth().clip(forumGroupShape(i, candidates.size, 18.dp)).background(if (selected) c.secondaryContainer else c.surfaceContainerHigh)
                            .kikoClickable(scale = 0.98f) { target = it }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(it.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 34.dp, height = 48.dp).clip(RoundedCornerShape(kikoCorner(8.dp))).background(c.surfaceLow))
                        Text(it.title, color = if (selected) c.onSecondaryContainer else c.ink, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = c.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Text("Why are they alike?", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            TextField(
                value = text, onValueChange = { text = it; error = null }, minLines = 4, maxLines = 8, shape = RoundedCornerShape(kikoCorner(16.dp)), colors = settingsFieldColors(c), modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(target?.let { "How is ${it.title} similar to ${source.title}?" } ?: "Pick a title above, then explain the similarity") },
            )
            Text(
                "${text.trim().length} characters. English only, give your own reasons and keep it spoiler-free. Recommendations are public.",
                color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 6.dp, top = 8.dp),
            )
            error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 10.dp)) }

            Button(
                onClick = {
                    val t = target ?: return@Button
                    val tid = t.id.toIntOrNull() ?: return@Button
                    busy = true; error = null
                    scope.launch {
                        runCatching { MalSocialApi(context).recommend(source.type == MediaType.Manga, sourceId!!, tid, text.trim()) }
                            .onSuccess { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); busy = false; onDismiss() }
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = friendlyError(it); busy = false }
                    }
                },
                enabled = !busy && target != null && text.isNotBlank(), shape = kikoPillShape(), modifier = Modifier.align(Alignment.End).padding(top = 18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
            ) { if (busy) KikoLoadingIndicator(color = c.onPrimary, size = 18.dp) else Text("Post recommendation", fontWeight = FontWeight.Medium) }
        }
    }
}

// Manage MAL account > Your recommendations: everything the user has recommended, with edit and delete.
@Composable fun MalMyRecommendationsScreen(onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val api = remember { MalSocialApi(context) }
    var recs by remember { mutableStateOf<MyRecs?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MyRecEntry?>(null) }
    LaunchedEffect(reloadKey) {
        error = null
        runCatching { api.myRecommendations() }.onSuccess { recs = it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = friendlyError(it) }
    }
    BackHandler { if (editing != null) editing = null else onBack() }
    editing?.let { entry ->
        MalRecEditorScreen(entry, onBack = { editing = null; reloadKey++ })
        return
    }
    Column(Modifier.fillMaxSize()) {
        MalAccountHeader("Your recommendations", onBack)
        val r = recs
        when {
            error != null && r == null -> KikoErrorState(error!!, onRetry = { reloadKey++ })
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 28.dp)) {
                Text(
                    "Titles you've recommended on MyAnimeList. Open one to edit your reasons or remove it. To add a new one, use \"Recommend a similar title\" on any anime or manga page.",
                    color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                )
                RecGroup("Anime", r.anime) { editing = it }
                RecGroup("Manga", r.manga) { editing = it }
            }
        }
    }
}

@Composable private fun RecGroup(label: String, items: List<MyRecEntry>, onOpen: (MyRecEntry) -> Unit) {
    val c = LocalKikoColors.current
    GroupLabel(label)
    if (items.isEmpty()) {
        Text("Nothing here yet.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, e -> NavRow(Icons.Rounded.ThumbUp, e.title, e.meta, forumGroupShape(i, items.size)) { onOpen(e) } }
    }
}

@Composable private fun MalRecEditorScreen(entry: MyRecEntry, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { MalSocialApi(context) }
    var editor by remember { mutableStateOf<RecEditor?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val texts = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<RecPair?>(null) }
    LaunchedEffect(entry.editUrl, reloadKey) {
        error = null
        runCatching { api.loadEditor(entry.editUrl) }
            .onSuccess { editor = it; texts.clear(); texts.addAll(it.pairs.map { p -> p.text }) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = friendlyError(it) }
    }
    Column(Modifier.fillMaxSize()) {
        MalAccountHeader(entry.title, onBack)
        val ed = editor
        when {
            error != null && ed == null -> KikoErrorState(error!!, onRetry = { reloadKey++ })
            ed == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            ed.pairs.isEmpty() -> Text("No recommendations are left on this title.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ed.pairs.forEachIndexed { i, p ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(22.dp))).background(c.surfaceContainerHigh).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.targetTitle, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (p.sourceTitle.isNotBlank()) Text("similar to ${p.sourceTitle}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { removing = p }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, "Remove", tint = c.danger) }
                        }
                        TextField(
                            value = texts.getOrElse(i) { p.text }, onValueChange = { if (i < texts.size) texts[i] = it }, minLines = 3, maxLines = 8,
                            shape = RoundedCornerShape(kikoCorner(16.dp)), colors = settingsFieldColors(c), modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { api.saveEditor(ed, texts.toList()) }
                                .onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); busy = false; onBack() }
                                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Toast.makeText(context, friendlyError(it), Toast.LENGTH_LONG).show(); busy = false }
                        }
                    },
                    enabled = !busy, shape = kikoPillShape(), modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary), contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                ) { if (busy) KikoLoadingIndicator(color = c.onPrimary, size = 18.dp) else Text("Save changes", fontWeight = FontWeight.Medium) }
            }
        }
    }
    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null }, containerColor = c.surfaceContainerHigh,
            title = { Text("Remove this recommendation?", color = c.ink) },
            text = { Text("${p.targetTitle} will no longer be recommended for ${p.sourceTitle.ifBlank { "this title" }} on MyAnimeList.", color = c.muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null; busy = true
                    scope.launch {
                        runCatching { api.deletePair(p) }
                            .onSuccess { busy = false; reloadKey++ }
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Toast.makeText(context, friendlyError(it), Toast.LENGTH_LONG).show(); busy = false }
                    }
                }) { Text("Remove", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel", color = c.muted) } },
        )
    }
}
