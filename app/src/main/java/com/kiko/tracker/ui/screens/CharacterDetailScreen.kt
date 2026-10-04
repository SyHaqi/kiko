@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoLoadingScreen
import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.util.openInBrowser
import android.content.Intent
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.kiko.tracker.ui.components.FavoriteHeartButton
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.rememberStaggerMemory


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
    // "Add to Favorites" heart beside the poster — see FavoriteApi/LibraryViewModel.
    favorited: Boolean = false,
    onLoadFavoriteStatus: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
) {
    // Navigation.kt now shows this
    // the fetch behind it
    // character is null for
    // out before touching anything
    if (character == null) {
        KikoLoadingScreen(onBack = onBack)
        return
    }
    val c = LocalKikoColors.current
    val context = LocalContext.current
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
    LaunchedEffect(character.malId) { onLoadFavoriteStatus() }
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
                DetailHeader(
                    eyebrow = "CHARACTER", title = character.name,
                    shareUrl = "https://myanimelist.net/character/${character.malId}",
                    favorited = favorited, onBack = onBack, onToggleFavorite = onToggleFavorite,
                )
                Column(Modifier.padding(horizontal = 14.dp)) {
                    val facts = buildList {
                        if (character.favorites > 0) add(DetailFact(Icons.Default.Favorite, c.danger, "${formatCount(character.favorites)} favorites"))
                        if (character.animeography.isNotEmpty()) add(DetailFact(Icons.Default.Tv, c.primary, "${character.animeography.size} anime"))
                        if (character.mangaography.isNotEmpty()) add(DetailFact(Icons.Default.MenuBook, c.primary, "${character.mangaography.size} manga"))
                        if (character.voiceActors.isNotEmpty()) add(DetailFact(Icons.Default.Mic, c.primary, countLabel(character.voiceActors.size, "voice actor")))
                    }
                    val posterInteraction = remember { MutableInteractionSource() }
                    DetailCoverRow(
                        coverWidth = 128.dp, coverHeight = 192.dp, coverCorner = 16.dp, facts = facts,
                        modifier = Modifier.padding(top = 16.dp),
                        coverModifier = Modifier.background(c.surfaceContainerHigh)
                            .let { m -> if (character.image.isNotBlank()) m.clickable(indication = null, interactionSource = posterInteraction) { showFullImage = true } else m },
                    ) {
                        if (character.image.isNotBlank()) {
                            AsyncImage(model = character.image, contentDescription = character.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(character.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    if (character.bioFields.isNotEmpty()) {
                        SectionTitle("Details", "", {})
                        DetailFieldRows(character.bioFields)
                    }

                    // The Japanese name / nicknames used to sit under the title; like the anime page's
                    // "Alternative titles" they're now a list here. Long-press a row to copy it.
                    val altNames = listOf(character.nameKanji, character.nicknames).filter { it.isNotBlank() }.distinct()
                    if (altNames.isNotEmpty()) {
                        SectionTitle("Alternative names", "", {})
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            altNames.forEachIndexed { i, name ->
                                DetailGroupRow(i, altNames.size, onLongClick = { copyTitleToClipboard(context, name) }) {
                                    Text(name, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
                    modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp).clip(kikoCircleShape()).background(Color.White.copy(alpha = .15f)),
                ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
        }
    }
}