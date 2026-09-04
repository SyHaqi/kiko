@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

// Company (studio/producer/licensor) detail page — same blank-canvas-page reasoning as
// PersonDetailScreen/CharacterDetailScreen: a company has no synopsis/tracking status of
// its own either (see CompanyModels.kt). Structure, top to bottom: square logo (companies
// don't have a portrait aspect the way people/characters do — MAL's own logos are square),
// "COMPANY" eyebrow, name, favorites, bio fields, about, links, one Recent News card, then
// the studio's full anime catalog as a filterable grid — the one part of this page that's
// genuinely different from Person/Character's horizontal credited-works rows, since a
// studio's catalog can run into the hundreds (Kyoto Animation's is 140+) and reads far
// better as a scannable grid with a type filter than as one very long LazyRow.
//
// The grid is *not* a nested LazyVerticalGrid — Compose doesn't support a lazily-scrolling
// grid inside an already-lazily-scrolling column. Instead, the whole page (bio, one news
// card, and the grid) is one LazyColumn, and the filtered catalog is chunked into rows of 3
// up front, each chunk rendered as a plain Row `item`. Same "chunk it yourself" technique
// this app hasn't needed until now because every other grid in the app (Seasonal, Ranking,
// Discover results) is the *entire* page, not one section living inside a taller one.
//
// Scroll position (this page's own single vertical scroll — bio, news, and the grid all
// share it) is persisted the same way every other detail page does: seeded from
// initialScroll on entry, saved via onLeaveScroll on the way out, since Navigation.kt's
// AnimatedContent tears this composable down and rebuilds it on every work-row hop.

// Loading placeholder shaped like the real page below — shown while companyDetailOpenId is
// set but companyDetailOpen hasn't resolved yet (see Navigation.kt).
@Composable fun CompanyDetailScreenSkeleton(onBack: () -> Unit) {
    val c = LocalKikoColors.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                SkeletonBlock(Modifier.size(110.dp), shape = RoundedCornerShape(kikoCorner(24.dp)))
                SkeletonBlock(Modifier.padding(top = 18.dp).width(96.dp).height(12.dp))
                SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth(0.6f).height(26.dp))
                SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.3f).height(14.dp))
                SkeletonBlock(Modifier.padding(top = 26.dp).width(140.dp).height(18.dp))
                SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth().height(96.dp), shape = RoundedCornerShape(kikoCorner(20.dp)))
                SkeletonBlock(Modifier.padding(top = 26.dp).width(90.dp).height(18.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { SkeletonBlock(Modifier.width(64.dp).height(32.dp), shape = RoundedCornerShape(kikoCorner(16.dp))) }
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    repeat(3) { ListGridCardSkeleton(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val CompanyFormatOrder = listOf("TV", "Movie", "OVA", "ONA", "Special", "Music")

@Composable fun CompanyDetailScreen(
    malId: Int,
    company: CompanyDetail?,
    onBack: () -> Unit,
    onOpenWork: (Int) -> Unit,
    onOpenNews: (topicId: Int, title: String) -> Unit,
    workLoadingId: Int? = null,
    myListStatus: Map<Int, WatchStatus> = emptyMap(),
    initialScroll: Pair<Int, Int> = 0 to 0,
    onLeaveScroll: (Int, Int) -> Unit = { _, _ -> },
) {
    // Same instant-navigate-then-fill reasoning as CharacterDetailScreen/PersonDetailScreen
    // — see companyDetailOpenId's doc comment in Navigation.kt.
    if (company == null) {
        CompanyDetailScreenSkeleton(onBack = onBack)
        return
    }
    val c = LocalKikoColors.current
    val uriHandler = LocalUriHandler.current
    val listState = remember(company.malId) { LazyListState(initialScroll.first, initialScroll.second) }
    DisposableEffect(company.malId) {
        onDispose { onLeaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    BackHandler(onBack = onBack)
    var showFullImage by remember(company.malId) { mutableStateOf(false) }
    // Same collapsed-to-3-lines / tap-to-expand treatment as Person/CharacterDetailScreen's
    // own About section — a studio's history paragraph can run just as long.
    var aboutExpanded by remember(company.malId) { mutableStateOf(false) }
    // Only offer an option for a format the studio's own catalog actually has, in the same
    // fixed TV/Movie/OVA/ONA/Special/Music order the rest of the app's format filters use,
    // not whatever order they happened to appear on the page in.
    val availableFormats = remember(company.works) { CompanyFormatOrder.filter { f -> company.works.any { it.format == f } } }
    var selectedFormat by remember(company.malId) { mutableStateOf("All") }
    // Reuses Discover's own DiscoverSort/sortedForDiscover/DiscoverSortMenu wholesale rather
    // than a bespoke company-page sort — same Members/Score/Newest/Title options MAL's own
    // studio page offers, and Relevance (the shared default) just leaves the catalog in
    // MAL's original order with no query to rank against, same as it does everywhere else
    // in the app.
    var selectedSort by remember(company.malId) { mutableStateOf(DiscoverSort.Relevance) }
    val titleLanguage = LocalTitleLanguage.current
    val filteredWorks = remember(company.works, selectedFormat, selectedSort, titleLanguage) {
        val byFormat = if (selectedFormat == "All") company.works else company.works.filter { it.format == selectedFormat }
        byFormat.sortedForDiscover(selectedSort, titleLanguage)
    }
    val gridRows = remember(filteredWorks) { filteredWorks.chunked(3) }
    val gridSeen = rememberStaggerMemory()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { runCatching { uriHandler.openUri("https://myanimelist.net/anime/producer/${company.malId}") } },
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh),
                    ) { Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.ink) }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    // Square logo, not a 2:3 portrait — matches how MAL's own studio logos
                    // are actually shaped, unlike a person/character portrait.
                    val logoInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.size(110.dp)
                            .clip(RoundedCornerShape(kikoCorner(24.dp))).background(c.surfaceContainerHigh)
                            .let { m -> if (company.image.isNotBlank()) m.clickable(indication = null, interactionSource = logoInteraction) { showFullImage = true } else m },
                    ) {
                        if (company.image.isNotBlank()) {
                            AsyncImage(model = company.image, contentDescription = company.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        } else {
                            Text(company.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    Text("COMPANY", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 18.dp))
                    SelectionContainer {
                        Text(company.name, style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.padding(top = 7.dp))
                    }
                    if (company.favorites > 0) {
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = c.danger, modifier = Modifier.size(14.dp))
                            Text("${formatCount(company.favorites)} favorites", color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }

                    if (company.bioFields.isNotEmpty()) {
                        SectionTitle("Details", "", {})
                        Card(shape = RoundedCornerShape(kikoCorner(24.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                company.bioFields.forEachIndexed { i, (label, value) ->
                                    InfoRow(label, value)
                                    if (i != company.bioFields.lastIndex) HorizontalDivider(color = c.outlineVariant)
                                }
                            }
                        }
                    }

                    if (company.about.isNotBlank()) {
                        SectionTitle("About", "", {})
                        Text(
                            company.about, color = c.ink, fontSize = 14.sp, lineHeight = 21.sp,
                            maxLines = if (aboutExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .animateContentSize()
                                .clickable { aboutExpanded = !aboutExpanded },
                        )
                    }

                    if (company.links.isNotEmpty()) {
                        SectionTitle("Links", "", {})
                        // Wrapping FlowRow of icon-led pills, one per platform — same
                        // placement as the genre chips on the anime detail screen, just with
                        // room for the icon up front. Icon is resolved from the link's own
                        // host (companyLinkIcon below), so it still degrades gracefully to a
                        // generic globe icon for a platform this app doesn't specifically
                        // recognize.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            company.links.forEach { (label, url) ->
                                CompanyLinkChip(label, url, onClick = { runCatching { uriHandler.openUri(url) } })
                            }
                        }
                    }

                    company.news?.let { news ->
                        SectionTitle("Recent News", "", {})
                        CompanyNewsCard(news, onClick = { onOpenNews(news.topicId, news.title) })
                    }

                    if (company.works.isNotEmpty()) {
                        SectionTitle("Anime (${company.works.size})", "", {})
                        // Format filter + sort, same Row(SpaceBetween) pairing DiscoverScreen
                        // already uses for its own type dropdown + DiscoverSortMenu.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            CompanyFormatDropdown(current = selectedFormat, options = availableFormats, works = company.works, onSelect = { selectedFormat = it })
                            DiscoverSortMenu(current = selectedSort, onSelect = { selectedSort = it })
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            if (company.works.isNotEmpty()) {
                if (filteredWorks.isEmpty()) {
                    item {
                        Text(
                            "No ${selectedFormat.lowercase()} entries.", color = c.muted, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        )
                    }
                } else {
                    itemsIndexed(gridRows, key = { i, _ -> "row-$i" }) { rowIndex, rowItems ->
                        StaggeredItem(rowIndex, gridSeen) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                rowItems.forEach { work ->
                                    val workId = work.id.toIntOrNull()
                                    Box(Modifier.weight(1f)) {
                                        SeasonalGridCard(item = work, onOpenDetail = { workId?.let(onOpenWork) }, myStatus = workId?.let { myListStatus[it] })
                                        if (workId != null && workId == workLoadingId) {
                                            Box(
                                                Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(Color.Black.copy(alpha = .45f)),
                                                contentAlignment = Alignment.Center,
                                            ) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
                                        }
                                    }
                                }
                                // Pad the last row so a partial row of 1-2 doesn't stretch to fill the width.
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullImage && company.image.isNotBlank()) {
        Dialog(onDismissRequest = { showFullImage = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showFullImage = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = company.image, contentDescription = company.name,
                    modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clip(RoundedCornerShape(kikoCorner(16.dp))),
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

// Resolves a link's own host to the platform's icon — checked against the URL rather than
// the link's visible label text, since MAL's own label can be a handle ("@kyoani_") or a
// bare domain rather than the platform's name. Falls back to a generic globe icon (the
// studio's own official site, or any platform this app doesn't specifically recognize)
// rather than leaving the chip icon-less.
private fun companyLinkIconRes(url: String): Int? {
    val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
    return when {
        "youtube" in host -> R.drawable.ic_youtube
        "facebook" in host -> R.drawable.ic_facebook
        "instagram" in host -> R.drawable.ic_instagram
        "twitter" in host || host == "x.com" || host.endsWith(".x.com") -> R.drawable.ic_x
        else -> null
    }
}

// One "Available At" pill — leading platform icon (see companyLinkIconRes above) + the
// link's own label, in the app's standard pill shape (rounded surfaceContainer background,
// same as GenreChip used to be here, just with room for the icon up front).
@Composable private fun CompanyLinkChip(label: String, url: String, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val iconRes = companyLinkIconRes(url)
    Row(
        Modifier
            .clip(RoundedCornerShape(kikoCorner(14.dp)))
            .background(c.surfaceContainer)
            .kikoClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), null, tint = c.ink, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.Language, null, tint = c.ink, modifier = Modifier.size(16.dp))
        }
        Text(label, color = c.ink, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

// Anime catalog's format filter, as a dropdown rather than a FlowRow of chips — mirrors
// DiscoverTypeDropdown's own FilterChip-trigger/DropdownMenu shape, since a studio's
// catalog can offer up to six formats at once (see CompanyFormatOrder) and that no longer
// sits comfortably as a row of chips next to the sort menu. Each option shows its own
// count, same as MAL's own studio page filter ("All (141)", "TV (34)", ...).
@Composable private fun CompanyFormatDropdown(current: String, options: List<String>, works: List<MediaItem>, onSelect: (String) -> Unit) {
    val c = LocalKikoColors.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(current) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp)) },
            colors = kikoFilterChipColors(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(kikoCorner(16.dp)), containerColor = c.surfaceContainer) {
            DropdownMenuItem(
                text = { Text("All (${works.size})", color = if (current == "All") c.accent else c.ink, fontWeight = if (current == "All") FontWeight.Bold else FontWeight.Normal) },
                onClick = { expanded = false; onSelect("All") },
                trailingIcon = if (current == "All") { { Icon(Icons.Default.Check, null, tint = c.primary, modifier = Modifier.size(18.dp)) } } else null,
            )
            options.forEach { f ->
                val count = works.count { it.format == f }
                DropdownMenuItem(
                    text = { Text("$f ($count)", color = if (f == current) c.accent else c.ink, fontWeight = if (f == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { expanded = false; onSelect(f) },
                    trailingIcon = if (f == current) { { Icon(Icons.Default.Check, null, tint = c.primary, modifier = Modifier.size(18.dp)) } } else null,
                )
            }
        }
    }
}

// One horizontal card for the page's single "Recent News" item — thumbnail, title, snippet,
// date. Tapping opens the same forum topic this app's own News section already knows how to
// render (see ForumTopicScreen), rather than a bespoke news-article reader.
@Composable private fun CompanyNewsCard(news: CompanyNews, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(20.dp))).background(c.surfaceContainer)
            .kikoClickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(Modifier.width(76.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (news.image.isNotBlank()) {
                AsyncImage(model = news.image, contentDescription = news.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(news.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(news.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (news.snippet.isNotBlank()) {
                Text(news.snippet, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
            if (news.date.isNotBlank()) {
                Text(news.date, color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }
}