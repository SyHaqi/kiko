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
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.PersonDetail
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.ui.components.SkeletonBlock
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.rememberStaggerMemory

// Person (voice actor/staff) detail
// its own blank-canvas layout
// a person has no
// Structure, top to bottom:
// Acting Roles / Anime
// (DetailRowCard, SectionTitle) the anime/manga
//
// Voice Acting Roles reuses
// they already read elsewhere
// = a muted line
// label is the "Main"/"Supporting"
// tapping opens the anime
// in this app. Anime
// (MalPeopleApi.fetchCreditedWorks), so those two
// again, keyed off item.displayTitle()/format/startDate
//
// Scroll position (this page's
// same way CharacterDetailScreen persists
// — seeded from initialScroll/initial*Scroll
// on the way out,
// rebuilds it on every
// Loading placeholder shaped like
// CharacterDetailScreenSkeleton in CharacterDetailScreen.kt, just
// (portrait + eyebrow +
// personDetailOpenId is set but
@Composable fun PersonDetailScreenSkeleton(onBack: () -> Unit) {
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
                SkeletonBlock(Modifier.padding(top = 26.dp).width(140.dp).height(18.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.width(92.dp).height(128.dp), shape = RoundedCornerShape(kikoCorner(14.dp))) }
                }
            }
        }
    }
}

@Composable fun PersonDetailScreen(
    malId: Int,
    person: PersonDetail?,
    onBack: () -> Unit,
    onOpenWork: (malId: Int, type: MediaType) -> Unit,
    workLoadingId: Int? = null,
    myListStatus: Map<Pair<Int, MediaType>, WatchStatus> = emptyMap(),
    initialScroll: Pair<Int, Int> = 0 to 0,
    initialRolesScroll: Pair<Int, Int> = 0 to 0,
    initialStaffScroll: Pair<Int, Int> = 0 to 0,
    initialMangaScroll: Pair<Int, Int> = 0 to 0,
    onLeaveScroll: (Int, Int) -> Unit = { _, _ -> },
    onLeaveRolesScroll: (Int, Int) -> Unit = { _, _ -> },
    onLeaveStaffScroll: (Int, Int) -> Unit = { _, _ -> },
    onLeaveMangaScroll: (Int, Int) -> Unit = { _, _ -> },
) {
    // Same instant-navigate-then-fill reasoning as
    // characterDetailOpenId's doc comment in
    if (person == null) {
        PersonDetailScreenSkeleton(onBack = onBack)
        return
    }
    val c = LocalKikoColors.current
    val uriHandler = LocalUriHandler.current
    val listState = remember(person.malId) { LazyListState(initialScroll.first, initialScroll.second) }
    val rolesListState = remember(person.malId) { LazyListState(initialRolesScroll.first, initialRolesScroll.second) }
    val staffListState = remember(person.malId) { LazyListState(initialStaffScroll.first, initialStaffScroll.second) }
    val mangaListState = remember(person.malId) { LazyListState(initialMangaScroll.first, initialMangaScroll.second) }
    DisposableEffect(person.malId) {
        onDispose {
            onLeaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            onLeaveRolesScroll(rolesListState.firstVisibleItemIndex, rolesListState.firstVisibleItemScrollOffset)
            onLeaveStaffScroll(staffListState.firstVisibleItemIndex, staffListState.firstVisibleItemScrollOffset)
            onLeaveMangaScroll(mangaListState.firstVisibleItemIndex, mangaListState.firstVisibleItemScrollOffset)
        }
    }
    BackHandler(onBack = onBack)
    val rolesSeen = rememberStaggerMemory()
    val staffSeen = rememberStaggerMemory()
    val mangaSeen = rememberStaggerMemory()
    var showFullImage by remember(person.malId) { mutableStateOf(false) }
    // Same collapsed-to-3-lines / tap-to-expand
    // About section — a
    var aboutExpanded by remember(person.malId) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { runCatching { uriHandler.openUri("https://myanimelist.net/people/${person.malId}") } },
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh),
                    ) { Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.ink) }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    // No backdrop banner —
                    // same fallback-letter treatment as
                    val posterInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.width(128.dp).aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh)
                            .let { m -> if (person.image.isNotBlank()) m.clickable(indication = null, interactionSource = posterInteraction) { showFullImage = true } else m },
                    ) {
                        if (person.image.isNotBlank()) {
                            AsyncImage(model = person.image, contentDescription = person.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(person.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    Text("PERSON", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 18.dp))
                    SelectionContainer {
                        Text(person.name, style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.padding(top = 7.dp))
                    }
                    if (person.favorites > 0) {
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = c.danger, modifier = Modifier.size(14.dp))
                            Text("${formatCount(person.favorites)} favorites", color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }

                    if (person.bioFields.isNotEmpty()) {
                        SectionTitle("Details", "", {})
                        Card(shape = RoundedCornerShape(kikoCorner(24.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                person.bioFields.forEachIndexed { i, (label, value) ->
                                    InfoRow(label, value)
                                    if (i != person.bioFields.lastIndex) HorizontalDivider(color = c.outlineVariant)
                                }
                            }
                        }
                    }

                    if (person.about.isNotBlank()) {
                        SectionTitle("About", "", {})
                        Text(
                            person.about, color = c.ink, fontSize = 14.sp, lineHeight = 21.sp,
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
                    key("voiceActingRoles") {
                        if (person.voiceActingRoles.isNotEmpty()) {
                            SectionTitle("Voice Acting Roles", "", {})
                            LazyRow(state = rolesListState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(person.voiceActingRoles, key = { _, r -> "role-${r.workId}-${r.characterId}" }) { i, role ->
                                    StaggeredItem(i, rolesSeen) {
                                        val workTitle = role.displayTitle()
                                        DetailRowCard(
                                            imageUrl = role.workImage, fallbackLetter = workTitle.take(1), title = workTitle,
                                            label = role.roleLabel, subtitle = role.characterName,
                                            loading = workLoadingId == role.workId,
                                            myStatus = myListStatus[role.workId to MediaType.Anime],
                                            onClick = { onOpenWork(role.workId, MediaType.Anime) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    key("staffCredits") {
                        if (person.staffCredits.isNotEmpty()) {
                            SectionTitle("Anime Staff Positions", "", {})
                            LazyRow(state = staffListState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(person.staffCredits, key = { _, w -> "staff-${w.id}" }) { i, work ->
                                    StaggeredItem(i, staffSeen) {
                                        val workTitle = work.displayTitle()
                                        DetailRowCard(
                                            imageUrl = work.cover, fallbackLetter = workTitle.take(1), title = workTitle, label = work.format,
                                            loading = workLoadingId == work.id.toIntOrNull(),
                                            myStatus = work.id.toIntOrNull()?.let { myListStatus[it to MediaType.Anime] },
                                            onClick = { work.id.toIntOrNull()?.let { onOpenWork(it, MediaType.Anime) } },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    key("publishedManga") {
                        if (person.publishedManga.isNotEmpty()) {
                            SectionTitle("Published Manga", "", {})
                            LazyRow(state = mangaListState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(person.publishedManga, key = { _, w -> "manga-${w.id}" }) { i, work ->
                                    StaggeredItem(i, mangaSeen) {
                                        val workTitle = work.displayTitle()
                                        DetailRowCard(
                                            imageUrl = work.cover, fallbackLetter = workTitle.take(1), title = workTitle, label = work.format,
                                            loading = workLoadingId == work.id.toIntOrNull(),
                                            myStatus = work.id.toIntOrNull()?.let { myListStatus[it to MediaType.Manga] },
                                            onClick = { work.id.toIntOrNull()?.let { onOpenWork(it, MediaType.Manga) } },
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

    if (showFullImage && person.image.isNotBlank()) {
        Dialog(onDismissRequest = { showFullImage = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showFullImage = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = person.image, contentDescription = person.name,
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