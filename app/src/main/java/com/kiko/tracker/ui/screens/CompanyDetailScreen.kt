@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoLoadingScreen
import com.kiko.tracker.ui.components.coverClip
import com.kiko.tracker.ui.components.KikoLoadingIndicator
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import com.kiko.tracker.R
import com.kiko.tracker.data.model.CompanyDetail
import com.kiko.tracker.data.model.CompanyNews
import com.kiko.tracker.data.model.DiscoverSort
import com.kiko.tracker.data.model.LocalTitleLanguage
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.TitleLanguage
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.sortedForDiscover
import com.kiko.tracker.ui.components.LinkifiedText
import com.kiko.tracker.ui.components.FavoriteHeartButton
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.accent
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.rememberStaggerMemory

// Company (studio/producer/licensor) detail page
// PersonDetailScreen/CharacterDetailScreen: a company has
// its own either (see
// don't have a portrait
// "COMPANY" eyebrow, name, favorites,
// the studio's full anime
// genuinely different from Person/Character's
// studio's catalog can run
// better as a scannable
//
// The grid is *not*
// grid inside an already-lazily-scrolling
// card, and the grid)
// up front, each chunk
// this app hasn't needed
// Discover results) is the
//
// Scroll position (this page's
// share it) is persisted
// initialScroll on entry, saved
// AnimatedContent tears this composable


private val CompanyFormatOrder = listOf("TV", "Movie", "OVA", "ONA", "Special", "Music")
// How many works the Anime grid reveals at a time (3 rows of 3).
private const val CompanyPageSize = 9

@Composable fun CompanyDetailScreen(
    malId: Int,
    company: CompanyDetail?,
    onBack: () -> Unit,
    onOpenWork: (Int) -> Unit,
    onOpenNews: (topicId: Int, title: String) -> Unit,
    workLoadingId: Int? = null,
    // Looks up English titles for the given anime ids (empty unless Title Language is English).
    onResolveEnglishTitles: suspend (List<Int>) -> Map<Int, String> = { emptyMap() },
    myListStatus: Map<Int, WatchStatus> = emptyMap(),
    initialScroll: Pair<Int, Int> = 0 to 0,
    onLeaveScroll: (Int, Int) -> Unit = { _, _ -> },
    // "Add to Favorites" heart beside the logo — see FavoriteApi/LibraryViewModel.
    favorited: Boolean = false,
    onLoadFavoriteStatus: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
) {
    // Same instant-navigate-then-fill reasoning as
    // — see companyDetailOpenId's doc
    if (company == null) {
        KikoLoadingScreen(onBack = onBack)
        return
    }
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val listState = remember(company.malId) { LazyListState(initialScroll.first, initialScroll.second) }
    DisposableEffect(company.malId) {
        onDispose { onLeaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(company.malId) { onLoadFavoriteStatus() }
    var showFullImage by remember(company.malId) { mutableStateOf(false) }
    // Same collapsed-to-3-lines / tap-to-expand
    // own About section —
    var aboutExpanded by remember(company.malId) { mutableStateOf(false) }
    // Only offer an option
    // fixed TV/Movie/OVA/ONA/Special/Music order the
    // not whatever order they
    val availableFormats = remember(company.works) { CompanyFormatOrder.filter { f -> company.works.any { it.format == f } } }
    var selectedFormat by remember(company.malId) { mutableStateOf("All") }
    // Reuses Discover's own DiscoverSort/sortedForDiscover/DiscoverSortMenu
    // than a bespoke company-page
    // studio page offers, and
    // MAL's original order with
    // in the app.
    var selectedSort by remember(company.malId) { mutableStateOf(DiscoverSort.Relevance) }
    val titleLanguage = LocalTitleLanguage.current
    val filteredWorks = remember(company.works, selectedFormat, selectedSort, titleLanguage) {
        val byFormat = if (selectedFormat == "All") company.works else company.works.filter { it.format == selectedFormat }
        byFormat.sortedForDiscover(selectedSort, titleLanguage)
    }
    // Paged like Discover's search results: show 9 titles (3 rows) first, then reveal 9 more each
    // time the list is scrolled to the end. Filtering/sorting still runs on the full list, so the
    // counts and sort order stay correct. Returning to the page restores enough rows for the saved
    // scroll position to land where the user left.
    var visibleCount by remember(company.malId) { mutableIntStateOf(maxOf(CompanyPageSize, initialScroll.first * 3 + CompanyPageSize)) }
    var lastFilter by remember(company.malId) { mutableStateOf(selectedFormat to selectedSort) }
    LaunchedEffect(selectedFormat, selectedSort) {
        val key = selectedFormat to selectedSort
        if (key != lastFilter) { lastFilter = key; visibleCount = CompanyPageSize }
    }
    val hasMoreWorks = visibleCount < filteredWorks.size
    // MAL's studio page only carries the romaji title, and an English one costs one request per
    // title. So when Title Language is English, look them up only for the rows currently revealed
    // (9 at a time, in the background): the page opens immediately with romaji and each title
    // swaps to English as it arrives. Lookups are cached app-wide, so reopening is cheap.
    var englishTitles by remember(company.malId) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(company.malId, titleLanguage, filteredWorks, visibleCount) {
        if (titleLanguage != TitleLanguage.English) return@LaunchedEffect
        val ids = filteredWorks.take(visibleCount).mapNotNull { w ->
            w.id.toIntOrNull()?.takeIf { w.titleEnglish.isBlank() && it !in englishTitles }
        }
        if (ids.isEmpty()) return@LaunchedEffect
        val got = onResolveEnglishTitles(ids)
        if (got.isNotEmpty()) englishTitles = englishTitles + got
    }
    val gridRows = remember(filteredWorks, visibleCount, englishTitles) {
        filteredWorks.take(visibleCount).map { w ->
            englishTitles[w.id.toIntOrNull()]?.takeIf { it.isNotBlank() }?.let { w.copy(titleEnglish = it) } ?: w
        }.chunked(3)
    }
    LaunchedEffect(listState, filteredWorks.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (lastVisible != null && total > 0 && lastVisible >= total - 1 && visibleCount < filteredWorks.size) {
                    visibleCount = minOf(visibleCount + CompanyPageSize, filteredWorks.size)
                }
            }
    }
    val gridSeen = rememberStaggerMemory()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                DetailHeader(
                    eyebrow = "COMPANY", title = company.name,
                    shareUrl = "https://myanimelist.net/anime/producer/${company.malId}",
                    favorited = favorited, onBack = onBack, onToggleFavorite = onToggleFavorite,
                )
                Column(Modifier.padding(horizontal = 14.dp)) {
                    val established = company.bioFields.firstOrNull { it.first.contains("establish", ignoreCase = true) }?.second?.takeIf { it.isNotBlank() }
                    val facts = buildList {
                        if (company.favorites > 0) add(DetailFact(Icons.Default.Favorite, c.danger, "${formatCount(company.favorites)} favorites"))
                        if (company.works.isNotEmpty()) add(DetailFact(Icons.Default.Tv, c.primary, "${company.works.size} anime"))
                        established?.let { add(DetailFact(Icons.Default.CalendarMonth, c.primary, it)) }
                    }
                    val logoInteraction = remember { MutableInteractionSource() }
                    // Square logo (110dp) rather than a 2:3 poster — company logos are shaped that way.
                    DetailCoverRow(
                        coverWidth = 110.dp, coverHeight = 110.dp, coverCorner = 24.dp, facts = facts,
                        modifier = Modifier.padding(top = 16.dp),
                        coverModifier = Modifier.background(c.surfaceContainerHigh)
                            .let { m -> if (company.image.isNotBlank()) m.clickable(indication = null, interactionSource = logoInteraction) { showFullImage = true } else m },
                    ) {
                        if (company.image.isNotBlank()) {
                            AsyncImage(model = company.image, contentDescription = company.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        } else {
                            Text(company.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    if (company.bioFields.isNotEmpty()) {
                        SectionTitle("Details", "", {})
                        DetailFieldRows(company.bioFields)
                    }

                    if (company.about.isNotBlank()) {
                        SectionTitle("About", "", {})
                        LinkifiedText(
                            company.about, color = c.ink, fontSize = 14.sp, lineHeight = 21.sp,
                            maxLines = if (aboutExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.animateContentSize(),
                            onClick = { aboutExpanded = !aboutExpanded },
                        )
                    }

                    if (company.links.isNotEmpty()) {
                        SectionTitle("Links", "", {})
                        // Wrapping FlowRow of icon-led
                        // placement as the genre
                        // room for the icon
                        // host (companyLinkIcon below), so
                        // generic globe icon for
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
                        // Format filter + sort,
                        // already uses for its
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
                                            ) { KikoLoadingIndicator(color = Color.White, size = 26.dp) }
                                        }
                                    }
                                }
                                // Pad the last row
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    if (hasMoreWorks) {
                        item(key = "loading-more") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                KikoLoadingIndicator(size = 24.dp)
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
                    modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp).clip(kikoCircleShape()).background(Color.White.copy(alpha = .15f)),
                ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
        }
    }
}

// Resolves a link's own
// the link's visible label
// bare domain rather than
// studio's own official site,
// rather than leaving the
//
// Not private — DetailScreen's
// reuses this and CompanyLinkChip
// icon-resolution + pill styling.
fun companyLinkIconRes(url: String): Int? {
    val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
    return when {
        "youtube" in host -> R.drawable.ic_youtube
        "facebook" in host -> R.drawable.ic_facebook
        "instagram" in host -> R.drawable.ic_instagram
        "twitter" in host || host == "x.com" || host.endsWith(".x.com") || host == "t.co" -> R.drawable.ic_x
        "discord" in host || host == "official discord" -> R.drawable.ic_discord
        "steampowered" in host -> R.drawable.ic_steam
        "reddit" in host || host == "redd.it" -> R.drawable.ic_reddit
        else -> null
    }
}

// One "Available At" pill
// link's own label, in
// same as GenreChip used
//
// Not private — shared
@Composable fun CompanyLinkChip(label: String, url: String, onClick: () -> Unit) {
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

// Anime catalog's format filter,
// DiscoverTypeDropdown's own FilterChip-trigger/DropdownMenu shape,
// catalog can offer up
// sits comfortably as a
// count, same as MAL's
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

// One horizontal card for
// opens the same forum
// ForumTopicScreen), rather than a
// own Recent News row
// page's news widget.
@Composable fun CompanyNewsCard(news: CompanyNews, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth()
            .height(138.dp)
            .clip(RoundedCornerShape(kikoCorner(20.dp))).background(c.surfaceContainer)
            .kikoClickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).coverClip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)) {
            if (news.image.isNotBlank()) {
                AsyncImage(model = news.image, contentDescription = news.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(news.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp).weight(1f)) {
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