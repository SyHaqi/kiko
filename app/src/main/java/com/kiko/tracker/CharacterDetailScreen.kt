@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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

// Character detail page. Deliberately not a reskin of the anime/manga DetailScreen — a
// character has no synopsis, no background banner, no episodes/tracking status, so this
// is built as its own blank-canvas layout rather than stretching MediaItem's shape to fit
// it (see CharacterModels.kt). Structure, top to bottom: thumbnail, "Character" eyebrow,
// name, kanji name, bio fields, free-text about, then Voice Actors / Animeography /
// Mangaography rows — same row-card language (PersonCard, DetailRowCard, SectionTitle)
// the anime/manga detail page already uses, so it still reads as part of the same app.
//
// Animeography/Mangaography specifically are treated exactly like the anime/manga detail
// page's own Related/Recommended rows: same DetailRowCard, same in-app fetch-then-open
// (onOpenWork) instead of a browser hop, same per-card loading spinner while that fetch is
// in flight (workLoadingId), and the same "already in your list" status mark (myListStatus)
// — a character's animeography/mangaography entries are real anime/manga ids on this same
// MAL account, so there's no reason they should behave differently from Related/Recommended.
// Titles in those two rows also follow this app's own Title Language setting the same way
// DetailScreen's own title does — see CharacterWork.displayTitle() in CharacterModels.kt.
//
// Scroll position (this page's own vertical scroll, plus the Animeography/Mangaography
// rows) is persisted the same way DetailScreen persists its own scroll + Related/
// Recommended row scroll: seeded from initialScroll/initialAnimeScroll/initialMangaScroll
// on entry, saved via onLeaveScroll/onLeaveAnimeScroll/onLeaveMangaScroll on the way out.
// Without this, tapping an Animeography/Mangaography entry (which tears this composable
// down via Navigation.kt's AnimatedContent, same as a related/recommended hop does for
// DetailScreen) and backing out reset this page to the top instead of where the user left
// it. Voice Actors deliberately isn't included here — tapping one opens an external browser
// tab rather than navigating in-app, so this composable is never torn down for it and
// there's nothing to lose.
@Composable fun CharacterDetailScreen(
    character: CharacterDetail,
    onBack: () -> Unit,
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
    // Same collapsed-to-3-lines / tap-to-expand treatment as DetailScreen's own Synopsis
    // (see DetailScreen.kt) — a character's About/background text can run just as long.
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
                    // No backdrop banner — a character has no second image to fill one
                    // with, so this is just the portrait on its own, same fallback-letter
                    // treatment as every other cover in the app when there's no image.
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

                    // No trailing " · Format" here (unlike the anime/manga header) — a
                    // character has no sub-type, so the eyebrow label stands alone.
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

                    if (character.voiceActors.isNotEmpty()) {
                        SectionTitle("Voice Actors", "", {})
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(character.voiceActors, key = { _, va -> "${va.malId}-${va.language}" }) { i, va ->
                                StaggeredItem(i, voiceActorsSeen) {
                                    PersonCard(va.image, va.name.take(1), va.name, va.language) { runCatching { uriHandler.openUri("https://myanimelist.net/people/${va.malId}") } }
                                }
                            }
                        }
                    }

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