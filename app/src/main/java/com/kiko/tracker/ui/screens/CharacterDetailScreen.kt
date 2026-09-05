@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kiko.tracker.data.model.CharacterDetail
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.ui.components.SkeletonBlock
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.rememberStaggerMemory

// Character detail page. Deliberately
// character has no synopsis,
// is built as its
// it (see CharacterModels.kt). Structure,
// name, kanji name, bio
// Mangaography rows — same
// the anime/manga detail page
//
// Animeography/Mangaography specifically are treated
// page's own Related/Recommended rows:
// (onOpenWork) instead of a
// in flight (workLoadingId), and
// — a character's animeography/mangaography
// MAL account, so there's
// Titles in those two
// DetailScreen's own title does
//
// Scroll position (this page's
// rows) is persisted the
// Recommended row scroll: seeded
// on entry, saved via
// Without this, tapping an
// down via Navigation.kt's AnimatedContent,
// DetailScreen) and backing out
// it. Voice Actors deliberately
// tab rather than navigating
// there's nothing to lose.
// Loading placeholder shaped like
// Details card + one
// tapped, before the fetch
// in Navigation.kt), so the
// leaving the user parked
// same reasoning as DetailScreenSkeleton
// without a backdrop banner,
@Composable fun CharacterDetailScreenSkeleton(onBack: () -> Unit) {
    val c = LocalKikoColors.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                SkeletonBlock(Modifier.width(128.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(kikoCorner(16.dp)))
                SkeletonBlock(Modifier.padding(top = 18.dp).width(84.dp).height(12.dp))
                SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth(0.6f).height(26.dp))
                SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.3f).height(14.dp))
                SkeletonBlock(Modifier.padding(top = 26.dp).width(90.dp).height(18.dp))
                SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth().height(120.dp), shape = RoundedCornerShape(kikoCorner(24.dp)))
                SkeletonBlock(Modifier.padding(top = 26.dp).width(120.dp).height(18.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.width(92.dp).height(128.dp), shape = RoundedCornerShape(kikoCorner(14.dp))) }
                }
            }
        }
    }
}

@Composable fun CharacterDetailScreen(
    malId: Int,
    character: CharacterDetail?,
    onBack: () -> Unit,
    // Opens a Voice Actors
    // PersonDetailScreen) — same fetch-then-open
    // the external MAL page
    onOpenPerson: (malId: Int) -> Unit = {},
    onOpenWork: (malId: Int, type: MediaType) -> Unit,
    workLoadingId: Int? = null,
    myListStatus: Map<Pair<Int, MediaType>, WatchStatus> = emptyMap(),
    initialScroll: Pair<Int, Int> = 0 to 0,
    initialAnimeScroll: Pair<Int, Int> = 0 to 0,
    initialMangaScroll: Pair<Int, Int> = 0 to 0,
    onLeaveScroll: (Int, Int) -> Unit = { _, _ -> },
    onLeaveAnimeScroll: (Int, Int) -> Unit = { _, _ -> },
    onLeaveMangaScroll: (Int, Int) -> Unit = { _, _ -> },
) {
    // Navigation.kt now shows this
    // the fetch behind it
    // character is null for
    // out before touching anything
    if (character == null) {
        CharacterDetailScreenSkeleton(onBack = onBack)
        return
    }
    val c = LocalKikoColors.current
    val uriHandler = LocalUriHandler.current
    val listState = remember(character.malId) { LazyListState(initialScroll.first, initialScroll.second) }
    val animeListState = remember(character.malId) { LazyListState(initialAnimeScroll.first, initialAnimeScroll.second) }
    val mangaListState = remember(character.malId) { LazyListState(initialMangaScroll.first, initialMangaScroll.second) }
    DisposableEffect(character.malId) {
        onDispose {
            onLeaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            onLeaveAnimeScroll(animeListState.firstVisibleItemIndex, animeListState.firstVisibleItemScrollOffset)
            onLeaveMangaScroll(mangaListState.firstVisibleItemIndex, mangaListState.firstVisibleItemScrollOffset)
        }
    }
    BackHandler(onBack = onBack)
    val voiceActorsSeen = rememberStaggerMemory()
    val animeSeen = rememberStaggerMemory()
    val mangaSeen = rememberStaggerMemory()
    var showFullImage by remember(character.malId) { mutableStateOf(false) }
    // Same collapsed-to-3-lines / tap-to-expand
    // (see DetailScreen.kt) — a
    var aboutExpanded by remember(character.malId) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { runCatching { uriHandler.openUri("https://myanimelist.net/character/${character.malId}") } },
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh),
                    ) { Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.ink) }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    // No backdrop banner —
                    // with, so this is
                    // treatment as every other
                    val posterInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.width(128.dp).aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh)
                            .let { m -> if (character.image.isNotBlank()) m.clickable(indication = null, interactionSource = posterInteraction) { showFullImage = true } else m },
                    ) {
                        if (character.image.isNotBlank()) {
                            AsyncImage(model = character.image, contentDescription = character.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(character.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    // No trailing " ·
                    // character has no sub-type,
                    Text("CHARACTER", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 18.dp))
                    SelectionContainer {
                        Column {
                            Text(character.name, style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.padding(top = 7.dp))
                            if (character.nameKanji.isNotBlank()) {
                                Text(character.nameKanji, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    if (character.nicknames.isNotBlank()) {
                        Text(character.nicknames, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    if (character.favorites > 0) {
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = c.danger, modifier = Modifier.size(14.dp))
                            Text("${formatCount(character.favorites)} favorites", color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }

                    if (character.bioFields.isNotEmpty()) {
                        SectionTitle("Details", "", {})
                        Card(shape = RoundedCornerShape(kikoCorner(24.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                character.bioFields.forEachIndexed { i, (label, value) ->
                                    InfoRow(label, value)
                                    if (i != character.bioFields.lastIndex) HorizontalDivider(color = c.outlineVariant)
                                }
                            }
                        }
                    }

                    if (character.about.isNotBlank()) {
                        SectionTitle("About", "", {})
                        Text(
                            character.about, color = c.ink, fontSize = 14.sp, lineHeight = 21.sp,
                            maxLines = if (aboutExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .animateContentSize()
                                .clickable { aboutExpanded = !aboutExpanded },
                        )
                    }

                    // Each row below reads
                    // tapped entry is being
                    // LazyColumn item{} (see the
                    // of workLoadingId here ties
                    // every other row —
                    // whole page twice (once
                    // while the user has
                    // recomposition to itself instead,
                    key("voiceActors") {
                        if (character.voiceActors.isNotEmpty()) {
                            SectionTitle("Voice Actors", "", {})
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(character.voiceActors, key = { _, va -> "${va.malId}-${va.language}" }) { i, va ->
                                    StaggeredItem(i, voiceActorsSeen) {
                                        PersonCard(va.image, va.name.take(1), va.name, va.language) { onOpenPerson(va.malId) }
                                    }
                                }
                            }
                        }
                    }

                    key("animeography") {
                        if (character.animeography.isNotEmpty()) {
                            SectionTitle("Animeography", "", {})
                            LazyRow(state = animeListState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(character.animeography, key = { _, w -> "anime-${w.malId}" }) { i, work ->
                                    StaggeredItem(i, animeSeen) {
                                        val workTitle = work.displayTitle()
                                        DetailRowCard(
                                            imageUrl = work.image, fallbackLetter = workTitle.take(1), title = workTitle, label = work.role,
                                            loading = workLoadingId == work.malId,
                                            myStatus = myListStatus[work.malId to MediaType.Anime],
                                            onClick = { onOpenWork(work.malId, MediaType.Anime) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    key("mangaography") {
                        if (character.mangaography.isNotEmpty()) {
                            SectionTitle("Mangaography", "", {})
                            LazyRow(state = mangaListState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(character.mangaography, key = { _, w -> "manga-${w.malId}" }) { i, work ->
                                    StaggeredItem(i, mangaSeen) {
                                        val workTitle = work.displayTitle()
                                        DetailRowCard(
                                            imageUrl = work.image, fallbackLetter = workTitle.take(1), title = workTitle, label = work.role,
                                            loading = workLoadingId == work.malId,
                                            myStatus = myListStatus[work.malId to MediaType.Manga],
                                            onClick = { onOpenWork(work.malId, MediaType.Manga) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullImage && character.image.isNotBlank()) {
        Dialog(onDismissRequest = { showFullImage = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showFullImage = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = character.image, contentDescription = character.name,
                    modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(2f / 3f).clip(RoundedCornerShape(kikoCorner(16.dp))),
                    contentScale = ContentScale.Fit,
                )
                IconButton(
                    onClick = { showFullImage = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.White.copy(alpha = .15f)),
                ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
        }
    }
}