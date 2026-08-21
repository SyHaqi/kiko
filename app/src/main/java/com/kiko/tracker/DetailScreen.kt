@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlin.math.roundToInt

// Groups every DetailScreen callback into one object instead of ~16 separate lambda
// parameters on the composable itself — the call site was becoming unreadable (one very
// long argument list with no grouping), and this keeps the plain data params (item,
// relatedLoadingId, recommendedLoadingId, initialScroll, myListStatus) separate from the
// actions so it's clear at a glance which is which.
data class DetailScreenActions(
    val onBack: () -> Unit,
    val onEdit: (MediaItem) -> Unit,
    val onOpenRelated: (RelatedEntry) -> Unit,
    val onBackfillRelated: (String, MediaType, (List<RelatedEntry>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() },
    val onBackfillThemes: (String, MediaType, (List<String>, List<String>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() },
    val onBackfillCovers: (String, MediaType, (List<String>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() },
    val onLoadRecommended: (MediaItem, (List<RecommendedEntry>) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() },
    val onOpenRecommended: (RecommendedEntry) -> Unit = {},
    val onLoadStatusDistribution: (MediaItem, (StatusDistribution) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() },
    val onOpenScoreStats: (MediaItem) -> Unit = {},
    val onLoadCharacters: (MediaItem, (List<CharacterEntry>) -> Unit, () -> Unit, () -> Unit) -> Unit = { _, _, onDone, _ -> onDone() },
    val onLoadReviews: (MediaItem, (List<ReviewEntry>) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() },
    val onOpenReview: (ReviewEntry) -> Unit = {},
    val onOpenReviewList: (String, String) -> Unit = { _, _ -> },
    val onGenreClick: (String) -> Unit = {},
    val onCreatorClick: (String) -> Unit = {},
    val onLeaveScroll: (Int, Int) -> Unit = { _, _ -> },
    val onLeaveRelatedScroll: (Int, Int) -> Unit = { _, _ -> },
    val onLeaveRecommendedScroll: (Int, Int) -> Unit = { _, _ -> },
    // Kicks off the best-effort AniList lookup for the confirmed next-episode number + air
    // time (see LibraryViewModel.loadAiringEpisode) — the result itself arrives via the
    // airingInfo param below, same split as relatedLoadingId/onBackfillRelated above.
    val onLoadAiringEpisode: (MediaItem) -> Unit = {},
)

// Loading placeholder shaped like the real header below (backdrop + overlapping
// poster + title block + genre chips + synopsis lines) — shown instead of a bare
// spinner while cover/related/themes are still resolving, so the page reads as
// "this detail page is arriving" rather than blank-then-pop. Back stays tappable
// the whole time so loading never traps the user.
@Composable fun DetailScreenSkeleton(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(248.dp).clip(RoundedCornerShape(bottomStart = kikoCorner(32.dp), bottomEnd = kikoCorner(32.dp)))) {
                    SkeletonBlock(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .32f)),
                    ) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                }
                Box(Modifier.padding(start = 20.dp, top = 96.dp).width(128.dp).aspectRatio(2f / 3f).shadow(10.dp, RoundedCornerShape(kikoCorner(16.dp)))) {
                    SkeletonBlock(Modifier.fillMaxSize(), shape = RoundedCornerShape(kikoCorner(16.dp)))
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                SkeletonBlock(Modifier.padding(top = 18.dp).width(96.dp).height(12.dp))
                SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth(0.75f).height(26.dp))
                SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.4f).height(14.dp))
                SkeletonBlock(Modifier.padding(top = 18.dp).fillMaxWidth(0.55f).height(14.dp))
                Row(Modifier.padding(top = 20.dp)) {
                    repeat(3) { i -> SkeletonBlock(Modifier.padding(end = 8.dp).width(if (i == 1) 78.dp else 64.dp).height(28.dp), shape = RoundedCornerShape(kikoCorner(10.dp))) }
                }
                SkeletonBlock(Modifier.padding(top = 26.dp).width(90.dp).height(18.dp))
                Column(Modifier.padding(top = 12.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth().height(13.dp))
                    SkeletonBlock(Modifier.padding(top = 9.dp).fillMaxWidth().height(13.dp))
                    SkeletonBlock(Modifier.padding(top = 9.dp).fillMaxWidth(0.65f).height(13.dp))
                }
            }
        }
    }
}

@Composable fun DetailScreen(item: MediaItem, actions: DetailScreenActions, relatedLoadingId: Int? = null, recommendedLoadingId: Int? = null, initialScroll: Pair<Int, Int> = 0 to 0, initialRelatedScroll: Pair<Int, Int> = 0 to 0, initialRecommendedScroll: Pair<Int, Int> = 0 to 0, myListStatus: Map<Pair<Int, MediaType>, WatchStatus> = emptyMap(), cachedSnapshot: LibraryViewModel.DetailCacheSnapshot? = null, airingInfo: AiringInfo? = null) {
    LaunchedEffect(item.id) { actions.onLoadAiringEpisode(item) }
    val c = LocalKikoColors.current
    var synopsisExpanded by remember(item.id) { mutableStateOf(false) }
    // Track related backfill completion. Seeded from cachedSnapshot (not just
    // item.related) so a title whose related row only ever got filled in via
    // backfill — not present on the original MediaItem — doesn't flash the loading
    // skeleton every time this composable is torn down and rebuilt (e.g. going into
    // a review and back), even though the data was already sitting in the cache.
    var backfilledRelated by remember(item.id) { mutableStateOf(cachedSnapshot?.related) }
    var relatedDone by remember(item.id) { mutableStateOf(item.related.isNotEmpty() || cachedSnapshot?.related != null) }
    LaunchedEffect(item.id) {
        if (item.related.isEmpty() && cachedSnapshot?.related == null) actions.onBackfillRelated(item.id, item.type, { backfilledRelated = it }, { relatedDone = true }) else relatedDone = true
    }
    val related = backfilledRelated ?: item.related
    // Recheck themes if missing — same cache-seeded reasoning as related above.
    // Anime-only: manga has no OP/ED field on MAL, so manga skips straight to done
    // rather than firing a network call that could never come back with anything.
    var backfilledThemes by remember(item.id) {
        mutableStateOf(
            if (cachedSnapshot?.openingThemes != null || cachedSnapshot?.endingThemes != null)
                cachedSnapshot?.openingThemes.orEmpty() to cachedSnapshot?.endingThemes.orEmpty()
            else null
        )
    }
    var themesDone by remember(item.id) { mutableStateOf(item.type != MediaType.Anime || item.openingThemes.isNotEmpty() || item.endingThemes.isNotEmpty() || cachedSnapshot?.openingThemes != null || cachedSnapshot?.endingThemes != null) }
    LaunchedEffect(item.id) {
        if (item.type == MediaType.Anime && item.openingThemes.isEmpty() && item.endingThemes.isEmpty() && cachedSnapshot?.openingThemes == null && cachedSnapshot?.endingThemes == null) {
            actions.onBackfillThemes(item.id, item.type, { op, ed -> backfilledThemes = op to ed }, { themesDone = true })
        } else themesDone = true
    }
    val (openingThemes, endingThemes) = backfilledThemes ?: (item.openingThemes to item.endingThemes)
    // Characters row (also feeds the Japanese Voice Actors row below) — seeded
    // from cache so it doesn't blank out and reload on every remount either.
    var characters by remember(item.id) { mutableStateOf(cachedSnapshot?.characters ?: emptyList()) }
    // Tracks a failed fetch (network/DNS/etc.) separately from "no characters listed",
    // so a blocked/broken request shows a retryable message instead of just vanishing.
    // charactersRetryKey bumping re-keys the LaunchedEffect below to fire the fetch again.
    var charactersFailed by remember(item.id) { mutableStateOf(false) }
    var charactersRetryKey by remember(item.id) { mutableStateOf(0) }
    LaunchedEffect(item.id, charactersRetryKey) {
        charactersFailed = false
        actions.onLoadCharacters(item, { chars -> characters = chars }, {}, { charactersFailed = true })
    }
    var reviews by remember(item.id) { mutableStateOf(cachedSnapshot?.reviews ?: emptyList()) }
    LaunchedEffect(item.id) { actions.onLoadReviews(item, { reviews = it }, {}) }
    // Recheck cover gallery non-blocking — seeded from cache too.
    var backfilledCovers by remember(item.id) { mutableStateOf(cachedSnapshot?.covers) }
    LaunchedEffect(item.id) {
        if (item.covers.size <= 1 && cachedSnapshot?.covers == null) actions.onBackfillCovers(item.id, item.type, { backfilledCovers = it }, {})
    }
    val covers = backfilledCovers ?: item.covers
    // Recommended row loads async — seeded from cache too. Tracked with its own
    // "done" flag (same pattern as relatedDone above) so the skeleton doesn't clear
    // before this resolves — without it, manga pages (whose themesDone is instant,
    // see below) reveal the page before this fetch finishes, and Recommended visibly
    // pops in a beat later instead of appearing with everything else.
    var recommended by remember(item.id) { mutableStateOf(cachedSnapshot?.recommended ?: emptyList()) }
    var recommendedDone by remember(item.id) { mutableStateOf(cachedSnapshot?.recommended != null) }
    LaunchedEffect(item.id) { actions.onLoadRecommended(item, { recommended = it }, { recommendedDone = true }) }
    // Status distribution loads async — seeded from cache too.
    var statusDistribution by remember(item.id) { mutableStateOf(cachedSnapshot?.statusDistribution) }
    LaunchedEffect(item.id) { actions.onLoadStatusDistribution(item, { statusDistribution = it }, {}) }
    // Fresh scroll state per-title
    val listState = remember(item.id) { LazyListState(initialScroll.first, initialScroll.second) }
    // One stagger-memory set per horizontal row so each row's entrance animation plays
    // only the first time an item appears — without this, StaggeredItem replays its
    // fadeIn/slideIn every time a row's cards are disposed+recomposed as they scroll
    // in/out of the LazyRow's retained window, which is what caused the scroll jank.
    val charactersSeen = rememberStaggerMemory()
    val voiceActorsSeen = rememberStaggerMemory()
    val reviewsSeen = rememberStaggerMemory()
    val relatedSeen = rememberStaggerMemory()
    val recommendedSeen = rememberStaggerMemory()
    // Related/recommended rows get their own remembered scroll state too — without
    // this, tapping an entry mid-scroll and coming back snaps the row back to its
    // first item, since a plain rememberLazyListState() resets whenever this
    // composable is torn down and rebuilt (see AnimatedContent's key-based teardown
    // in Navigation.kt).
    val relatedListState = remember(item.id) { LazyListState(initialRelatedScroll.first, initialRelatedScroll.second) }
    val recommendedListState = remember(item.id) { LazyListState(initialRecommendedScroll.first, initialRecommendedScroll.second) }
    // Save spot on leave
    DisposableEffect(item.id) {
        onDispose {
            actions.onLeaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            actions.onLeaveRelatedScroll(relatedListState.firstVisibleItemIndex, relatedListState.firstVisibleItemScrollOffset)
            actions.onLeaveRecommendedScroll(recommendedListState.firstVisibleItemIndex, recommendedListState.firstVisibleItemScrollOffset)
        }
    }
    // Share single decoded painter. Opts into hardware bitmaps (allowHardware(true)) here
    // rather than inheriting the app's global allowHardware(false) default — that default
    // exists for forum threads decoding many small animated stickers back-to-back (see the
    // Coil setup in MainActivity), not a single large poster image like this one.
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coverPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context).data(item.cover.ifBlank { null }).size(Size.ORIGINAL).allowHardware(true).build()
    )
    val coverReady = item.cover.isBlank() || coverPainter.state is AsyncImagePainter.State.Success || coverPainter.state is AsyncImagePainter.State.Error
    BackHandler(onBack = actions.onBack)
    // Load all sections upfront
    if (!coverReady || !relatedDone || !themesDone || !recommendedDone) {
        DetailScreenSkeleton(onBack = actions.onBack)
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                // Tap cover opens fullscreen
                var showFullCover by remember(item.id) { mutableStateOf(false) }
                val displayTitle = item.displayTitle()
                // Backdrop from second picture
                val backdropUrl = covers.getOrNull(1)
                // Unclipped wrapper for poster
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(248.dp).clip(RoundedCornerShape(bottomStart = kikoCorner(32.dp), bottomEnd = kikoCorner(32.dp)))) {
                        if (backdropUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(backdropUrl).allowHardware(true).build(), contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(Color(item.color)))
                        }
                        // Shadow instead of blur
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .5f), .4f to Color.Transparent)))
                        // General darkening overlay
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .32f), Color.Black.copy(alpha = .7f)))))
                        IconButton(
                            onClick = actions.onBack,
                            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .32f)),
                        ) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                        var moreOpen by remember(item.id) { mutableStateOf(false) }
                        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                            IconButton(
                                onClick = { moreOpen = true },
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.Black.copy(alpha = .32f)),
                            ) { Icon(Icons.Default.MoreVert, "More options", tint = Color.White) }
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }, shape = RoundedCornerShape(kikoCorner(18.dp))) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = {
                                        moreOpen = false
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, malUrl(item))
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, displayTitle))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                    onClick = { moreOpen = false; uriHandler.openUri(malUrl(item)) },
                                )
                            }
                        }
                    }
                    // Poster position below button
                    val posterInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.padding(start = 20.dp, top = 96.dp).width(128.dp).aspectRatio(2f / 3f)
                            .shadow(10.dp, RoundedCornerShape(kikoCorner(16.dp))).clip(RoundedCornerShape(kikoCorner(16.dp))).background(Color(item.color))
                            .pressScale(posterInteraction, scale = 0.94f)
                            .clickable(indication = null, interactionSource = posterInteraction) { showFullCover = true },
                    ) {
                        if (item.cover.isNotBlank()) {
                            Image(painter = coverPainter, contentDescription = displayTitle, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Text(displayTitle.take(1), fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Color.White.copy(.85f), modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
                if (showFullCover && item.cover.isNotBlank()) {
                    // Fallback to single cover
                    val gallery = covers.ifEmpty { listOf(item.cover) }
                    val pagerState = rememberPagerState(pageCount = { gallery.size })
                    Dialog(onDismissRequest = { showFullCover = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f))) {
                            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                Box(
                                    Modifier.fillMaxSize()
                                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showFullCover = false },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Fit, not cropped, cover
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(gallery[page]).allowHardware(true).build(), contentDescription = item.displayTitle(),
                                        modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(2f / 3f).clip(RoundedCornerShape(kikoCorner(16.dp))),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    )
                                }
                            }
                            if (gallery.size > 1) {
                                Row(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    gallery.indices.forEach { i ->
                                        Box(
                                            Modifier.size(if (i == pagerState.currentPage) 8.dp else 6.dp).clip(kikoCircleShape())
                                                .background(Color.White.copy(alpha = if (i == pagerState.currentPage) .95f else .4f)),
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showFullCover = false },
                                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(Color.White.copy(alpha = .15f)),
                            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    val itemDisplayTitle = item.displayTitle()
                    val aired = seasonYear(item.season, item.startDate)
                    Text(
                        "${item.type.name.uppercase()}${if (item.format.isNotBlank()) " · ${item.format}" else ""}",
                        color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    SelectionContainer {
                        Column {
                            Text(itemDisplayTitle, style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.padding(top = 7.dp))
                            val secondary = item.secondaryTitle()
                            if (secondary.isNotBlank()) {
                                Text(secondary, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    // Status line below title
                    val statusMeta = listOfNotNull(
                        item.airStatus.takeIf { it.isNotBlank() },
                        if (item.total > 0) "${item.total} ${if (item.type == MediaType.Anime) "episodes" else "chapters"}" else null,
                    )
                    if (statusMeta.isNotEmpty() || item.score > 0) {
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (statusMeta.isNotEmpty()) {
                                Text(statusMeta.joinToString("   ·   "), color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            if (item.score > 0) {
                                if (statusMeta.isNotEmpty()) Text("   ·   ", color = c.muted, fontSize = 13.sp)
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                                Text(item.score.twoDecimals(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    // Next episode airing time
                    item.nextEpisodeLabel(airingInfo)?.let { label ->
                        val is24Hour = systemIs24Hour()
                        val airTime = item.nextAirDateTime(airingInfo)?.toLocalTime()
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = c.primary, modifier = Modifier.size(14.dp))
                            Text(
                                listOfNotNull(label, airTime?.let { localizedTimeLabel(it, is24Hour) }).joinToString(" · "),
                                color = c.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }

                    if (item.genres.isNotEmpty()) {
                        Text("GENRES", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 18.dp, bottom = 9.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.genres.forEach { g -> GenreChip(g, onClick = { actions.onGenreClick(g) }) }
                        }
                    }
                    val meta = listOfNotNull(item.creator.takeIf { it.isNotBlank() }, aired.takeIf { it.isNotBlank() })
                    if (meta.isNotEmpty()) Text(meta.joinToString("   ·   "), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))

                    SectionTitle("Synopsis", "", {})
                    Text(
                        item.synopsis.ifBlank { "No synopsis available yet." },
                        color = if (item.synopsis.isBlank()) c.muted else c.ink,
                        fontSize = 14.sp, lineHeight = 21.sp,
                        maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .animateContentSize()
                            .let { if (item.synopsis.isNotBlank()) it.clickable { synopsisExpanded = !synopsisExpanded } else it },
                    )

                    // Community rank/popularity stats
                    if (item.rank > 0 || item.popularity > 0 || item.listUsers > 0) {
                        SectionTitle("Statistics", "", {})
                        Card(shape = RoundedCornerShape(kikoCorner(24.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                                if (item.rank > 0) StatBlock(Modifier.weight(1f), "#${item.rank}", "Rank")
                                if (item.popularity > 0) StatBlock(Modifier.weight(1f), "#${item.popularity}", "Popularity")
                                if (item.listUsers > 0) StatBlock(Modifier.weight(1f), formatCount(item.listUsers), "Members")
                            }
                        }
                    }

                    val details = buildList {
                        if (item.format.isNotBlank()) add("Format" to item.format)
                        if (item.source.isNotBlank()) add("Source" to item.source)
                        if (aired.isNotBlank()) add(if (item.type == MediaType.Anime) "Aired" to aired else "Published" to aired)
                        if (item.startDateFull.isNotBlank()) add("Start date" to formatFullDate(item.startDateFull))
                        if (item.endDateFull.isNotBlank()) add("End date" to formatFullDate(item.endDateFull))
                        else if (item.startDateFull.isNotBlank()) add("End date" to "Ongoing")
                        if (item.type == MediaType.Anime && item.total > 0) add("Episodes" to item.total.toString())
                        if (item.type == MediaType.Manga && item.total > 0) add("Chapters" to item.total.toString())
                        if (item.type == MediaType.Manga && item.volumes > 0) add("Volumes" to item.volumes.toString())
                        if (item.type == MediaType.Anime && item.rating.isNotBlank()) add("Rating" to item.rating)
                        if (item.creator.isNotBlank()) add(if (item.type == MediaType.Anime) "Studio" to item.creator else "Author" to item.creator)
                    }
                    if (details.isNotEmpty()) {
                        SectionTitle("Details", "", {})
                        Card(shape = RoundedCornerShape(kikoCorner(24.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                details.forEachIndexed { i, (label, value) ->
                                    val isCreatorRow = (label == "Studio" || label == "Author") && item.creator.isNotBlank()
                                    InfoRow(label, value, onClick = if (isCreatorRow) { { actions.onCreatorClick(item.creator) } } else null)
                                    if (i != details.lastIndex) HorizontalDivider(color = c.outlineVariant)
                                }
                            }
                        }
                    }

                    if (item.synonyms.isNotEmpty()) {
                        SectionTitle("Alternative titles", "", {})
                        Card(shape = RoundedCornerShape(kikoCorner(24.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            SelectionContainer {
                                Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                    item.synonyms.forEachIndexed { i, name ->
                                        Text(name, color = c.ink, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp))
                                        if (i != item.synonyms.lastIndex) HorizontalDivider(color = c.outlineVariant)
                                    }
                                }
                            }
                        }
                    }

                    if (characters.isNotEmpty()) {
                        SectionTitle("Characters", "See cast", { actions.onOpenReviewList(malCharactersUrl(item), itemDisplayTitle) })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(characters, key = { _, it -> it.malId }) { i, ch -> StaggeredItem(i, charactersSeen) { CharacterCard(ch, uriHandler) } }
                        }
                    } else if (charactersFailed) {
                        // Fetch itself failed (as opposed to the title just having no cast
                        // listed) — surface it instead of silently hiding the section, since
                        // that's the difference between "no data" and "something's blocking
                        // this" (e.g. a network-level filter on the user's WiFi).
                        SectionTitle("Characters", "", {})
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(kikoCorner(16.dp)))
                                .background(c.surfaceContainer)
                                .kikoClickable { charactersRetryKey++ }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = c.muted, modifier = Modifier.size(16.dp))
                            Text("Couldn't load cast — tap to retry", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    // Japanese cast only, one row — characters without a listed Japanese VA
                    // (e.g. a role recast mid-series with no dub credited yet) are skipped.
                    val japaneseVoiceActors = characters.mapNotNull { ch -> ch.japaneseVoiceActor?.let { it to ch.name } }
                    if (japaneseVoiceActors.isNotEmpty()) {
                        SectionTitle("Voice Actors", "", {})
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(japaneseVoiceActors, key = { _, (va, _) -> va.malId }) { i, (va, charName) -> StaggeredItem(i, voiceActorsSeen) { VoiceActorCard(va, charName, uriHandler) } }
                        }
                    }

                    val themes = openingThemes.map { "OP" to it } + endingThemes.map { "ED" to it }
                    if (themes.isNotEmpty()) {
                        SectionTitle("Theme songs", "", {})
                        Card(shape = RoundedCornerShape(kikoCorner(24.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                themes.forEachIndexed { i, (kind, text) ->
                                    Row(
                                        Modifier.fillMaxWidth().kikoClickable { uriHandler.openUri(youtubeSearchUrl("$text $itemDisplayTitle")) }.padding(vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(kind, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                                        Text(text, color = c.ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                        Icon(Icons.Default.PlayArrow, "Search on YouTube", tint = c.muted, modifier = Modifier.size(18.dp))
                                    }
                                    if (i != themes.lastIndex) HorizontalDivider(color = c.outlineVariant)
                                }
                            }
                        }
                    }

                    if (reviews.isNotEmpty()) {
                        SectionTitle("Reviews", "See more", { actions.onOpenReviewList(malReviewsUrl(item), itemDisplayTitle) })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(reviews, key = { _, it -> it.malId }) { i, rev -> StaggeredItem(i, reviewsSeen) { ReviewCard(rev, onClick = { actions.onOpenReview(rev) }) } }
                        }
                    }

                    if (related.isNotEmpty()) {
                        SectionTitle("Related", "", {})
                        LazyRow(state = relatedListState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(related, key = { _, it -> "${it.relation}-${it.malId}-${it.title}" }) { i, rel ->
                                StaggeredItem(i, relatedSeen) {
                                    RelatedCard(rel, loading = rel.malId > 0 && relatedLoadingId == rel.malId, myStatus = myListStatus[rel.malId to (if (rel.malType == "manga") MediaType.Manga else MediaType.Anime)]) {
                                        // Fallback to web search
                                        if (rel.malId > 0) actions.onOpenRelated(rel) else uriHandler.openUri(malUrl(rel))
                                    }
                                }
                            }
                        }
                    }

                    // Recommendations from MAL endpoint
                    if (recommended.isNotEmpty()) {
                        SectionTitle("Recommended", "", {})
                        LazyRow(state = recommendedListState, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            itemsIndexed(recommended, key = { _, it -> it.malId }) { i, rec ->
                                StaggeredItem(i, recommendedSeen) { RecommendedCard(rec, loading = recommendedLoadingId == rec.malId, myStatus = myListStatus[rec.malId to (if (rec.malType == "manga") MediaType.Manga else MediaType.Anime)]) { actions.onOpenRecommended(rec) } }
                            }
                        }
                    }

                    if (item.background.isNotBlank()) {
                        SectionTitle("Background", "", {})
                        Text(item.background, color = c.ink, fontSize = 14.sp, lineHeight = 21.sp)
                    }

                    // Reuse status bar styling
                    statusDistribution?.takeIf { it.total > 0 }?.let { dist ->
                        SectionTitle("Status distribution", "See more", { actions.onOpenScoreStats(item) }, icon = Icons.Default.BarChart)
                        Card(shape = RoundedCornerShape(kikoCorner(24.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                                StatBar("Watching", dist.watching, dist.total, c, statusColor("Watching"))
                                StatBar("Completed", dist.completed, dist.total, c, statusColor("Completed"))
                                StatBar("On hold", dist.onHold, dist.total, c, statusColor("On hold"))
                                StatBar("Dropped", dist.dropped, dist.total, c, statusColor("Dropped"))
                                StatBar("Plan to watch", dist.planToWatch, dist.total, c, statusColor("Plan to watch"))
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { actions.onEdit(item) },
            icon = { Icon(if (item.inUserList) Icons.Default.Edit else Icons.Default.Add, if (item.inUserList) "Edit" else "Add", tint = c.onPrimary) },
            text = { Text(if (item.inUserList) item.status.displayLabel(item.type) else "Add", fontWeight = FontWeight.Bold, color = c.onPrimary) },
            containerColor = c.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.kikoClickable(onClick = onClick) else it }.padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.muted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                value, color = if (onClick != null) c.primary else c.ink, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = c.primary, modifier = Modifier.size(16.dp).padding(start = 2.dp))
        }
    }
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.0fK".format(n / 1_000.0)
    else -> n.toString()
}
// Format season and year

fun seasonYear(season: String, year: String): String = listOf(season, year).filter { it.isNotBlank() }.joinToString(" ")
// Format ISO date display

fun formatFullDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        when (raw.count { it == '-' }) {
            2 -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(raw)!!)
            1 -> java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US).format(java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse(raw)!!)
            else -> raw
        }
    } catch (e: Exception) { raw }
}

fun youtubeSearchUrl(query: String) = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")

fun malUrl(entry: RelatedEntry) = if (entry.malId > 0) "https://myanimelist.net/${entry.malType}/${entry.malId}" else "https://myanimelist.net/search/all?q=" + java.net.URLEncoder.encode(entry.title, "UTF-8")
// Item for share/open menu

fun malUrl(item: MediaItem): String {
    val intId = item.id.toIntOrNull()
    return if (intId != null && intId > 0) "https://myanimelist.net/${item.type.name.lowercase()}/$intId"
    else "https://myanimelist.net/search/all?q=" + java.net.URLEncoder.encode(item.title, "UTF-8")
}
// Reviews page for an item

fun malReviewsUrl(item: MediaItem): String {
    val intId = item.id.toIntOrNull()
    return if (intId != null && intId > 0) "https://myanimelist.net/${item.type.name.lowercase()}/$intId/_/reviews"
    else malUrl(item)
}
// Full characters page for an item

fun malCharactersUrl(item: MediaItem): String {
    val intId = item.id.toIntOrNull()
    return if (intId != null && intId > 0) "https://myanimelist.net/${item.type.name.lowercase()}/$intId/_/characters"
    else malUrl(item)
}
// Recognize MAL title URL

fun parseMalDeepLink(uri: Uri): Pair<Int, MediaType>? {
    val host = uri.host?.lowercase() ?: return null
    if (host != "myanimelist.net" && !host.endsWith(".myanimelist.net")) return null
    val segments = uri.pathSegments
    if (segments.size < 2) return null
    val type = when (segments[0].lowercase()) {
        "anime" -> MediaType.Anime
        "manga" -> MediaType.Manga
        else -> return null
    }
    val id = segments[1].toIntOrNull() ?: return null
    return id to type
}

@Composable fun DetailRowCard(
    imageUrl: String, fallbackLetter: String, title: String,
    label: String? = null, subtitle: String? = null,
    loading: Boolean = false, onClick: (() -> Unit)? = null,
    myStatus: WatchStatus? = null,
) {
    val c = LocalKikoColors.current
    Column(
        Modifier.width(140.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHigh)
            .let { m -> onClick?.let { m.kikoClickable(enabled = !loading, onClick = it) } ?: m },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(topStart = kikoCorner(18.dp), topEnd = kikoCorner(18.dp))).background(c.surfaceLow)) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(model = imageUrl, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Text(fallbackLetter, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
            // Own tracking mark
            myStatus?.let { CoverStatusMark(it, Modifier.align(Alignment.TopStart).padding(6.dp)) }
        }
        // Fixed height text block
        Column(Modifier.fillMaxWidth().height(112.dp).padding(10.dp)) {
            if (label != null) Text(label.uppercase(), color = c.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (label != null) 4.dp else 0.dp))
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable fun RelatedCard(entry: RelatedEntry, loading: Boolean = false, myStatus: WatchStatus? = null, onClick: () -> Unit) {
    DetailRowCard(imageUrl = entry.cover, fallbackLetter = entry.title.take(1), title = entry.title, label = entry.relation, loading = loading, myStatus = myStatus, onClick = onClick)
}

// Recommended card same style

@Composable fun RecommendedCard(entry: RecommendedEntry, loading: Boolean = false, myStatus: WatchStatus? = null, onClick: () -> Unit) {
    // AutoRec entries have no real vote count (see RecommendedEntry.isAuto) — labeling
    // them "Recommended" like a real user pick would misrepresent where the pick came
    // from, so they get MAL's own "AutoRec" label instead.
    val subtitle = when {
        entry.isAuto -> "AutoRec"
        entry.votes > 0 -> "${entry.votes} recommend${if (entry.votes == 1) "s" else ""}"
        else -> "Recommended"
    }
    DetailRowCard(imageUrl = entry.cover, fallbackLetter = entry.title.take(1), title = entry.title, subtitle = subtitle, loading = loading, myStatus = myStatus, onClick = onClick)
}
// Compact card for characters/voice-actor rows

@Composable fun PersonCard(imageUrl: String, fallbackLetter: String, name: String, role: String, onClick: (() -> Unit)?) {
    val c = LocalKikoColors.current
    Column(
        Modifier.width(88.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh)
            .let { m -> onClick?.let { m.kikoClickable(onClick = it) } ?: m },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(topStart = kikoCorner(14.dp), topEnd = kikoCorner(14.dp))).background(c.surfaceLow)) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(model = imageUrl, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Text(fallbackLetter, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(name, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (role.isNotBlank()) Text(role, color = c.muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
// Character card opens MAL page

@Composable fun CharacterCard(entry: CharacterEntry, uriHandler: androidx.compose.ui.platform.UriHandler) {
    PersonCard(entry.image, entry.name.take(1), entry.name, entry.role) { entry.url.takeIf { it.isNotBlank() }?.let { runCatching { uriHandler.openUri(it) } } }
}
// Voice actor card opens the VA's own MAL page; the character they voice is shown as the subtitle

@Composable fun VoiceActorCard(entry: VoiceActorEntry, characterName: String, uriHandler: androidx.compose.ui.platform.UriHandler) {
    PersonCard(entry.image, entry.name.take(1), entry.name, characterName) { entry.url.takeIf { it.isNotBlank() }?.let { runCatching { uriHandler.openUri(it) } } }
}
// Review card opens full text

@Composable fun ReviewCard(entry: ReviewEntry, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Column(
        Modifier.width(260.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHigh).kikoClickable(onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.userImage.isNotBlank()) {
                AsyncImage(model = entry.userImage, contentDescription = entry.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(30.dp).clip(kikoCircleShape()).background(c.warm))
            } else {
                Box(Modifier.size(30.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                    Text(entry.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink)
                }
            }
            Text(entry.username, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 9.dp))
            if (entry.score > 0) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                Text(entry.score.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp))
            }
        }
        val verdict = entry.verdict()
        val otherTags = entry.tags.filterNot { it in ReviewVerdictTags }
        if (verdict != null || otherTags.isNotEmpty()) {
            Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                verdict?.let {
                    Icon(Icons.Default.Star, null, tint = verdictColor(it, c), modifier = Modifier.size(11.dp))
                    Text(it, color = verdictColor(it, c), fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                }
                otherTags.firstOrNull()?.let {
                    Text(it, color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = if (verdict != null) 8.dp else 0.dp))
                }
            }
        }
        if (entry.isSpoiler) Text("Contains spoilers", color = c.danger, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            entry.review, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp,
            maxLines = 5, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// Sheets section

@Composable fun EditSheet(item: MediaItem, onDismiss: () -> Unit, onSave: (MediaItem) -> Unit, onDelete: () -> Unit) {
    val c = LocalKikoColors.current
    var status by remember { mutableStateOf(item.status) }
    var progress by remember { mutableStateOf(item.progress) }
    var rating by remember { mutableStateOf(item.myRating) }
    var startDate by remember { mutableStateOf(item.watchStartDate) }
    var endDate by remember { mutableStateOf(item.watchEndDate) }
    var rewatching by remember { mutableStateOf(item.isRewatching) }
    var timesRewatched by remember { mutableStateOf(item.timesRewatched) }
    var notes by remember { mutableStateOf(item.notes) }
    var comments by remember { mutableStateOf(item.comments) }
    val rewatchWord = if (item.type == MediaType.Anime) "Rewatch" else "Reread"
    val rewatchedWord = if (item.type == MediaType.Anime) "rewatched" else "reread"
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surfaceContainerHigh,
            title = { Text("Remove from your list?", color = c.ink) },
            text = { Text("Are you sure you want to remove \"${item.title}\" from your list? This also removes it from your MyAnimeList account.", color = c.muted) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Cancel") } },
        )
    }
    // Opens half-screen (partially expanded); drag up to go full screen, drag down
    // to return to half screen, drag down again to dismiss — skipPartiallyExpanded
    // left off so the sheet keeps its natural partial/full/dismiss states.
    val sheetState = rememberModalBottomSheetState()
    // By default ModalBottomSheet resizes/re-anchors its own container against the IME,
    // which is what caused the sheet to shrink while the keyboard was open. An empty
    // contentWindowInsets stops that; imePadding() on the inner content pushes the
    // focused field up above the keyboard instead.
    // Even so, Compose still briefly re-settles the sheet's own anchor right as the
    // keyboard finishes closing, which is what caused the drag-handle-overlapping-content
    // "cut" glitch. Remember the sheet's value from just before the keyboard opened, wait
    // for the close animation/remeasure to actually finish, then restore that value —
    // instead of fighting that remeasure mid-flight the way an immediate call did.
    var lastStableValue by remember { mutableStateOf(sheetState.currentValue) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            lastStableValue = sheetState.currentValue
        } else {
            delay(120)
            when (lastStableValue) {
                SheetValue.Expanded -> sheetState.expand()
                SheetValue.PartiallyExpanded -> sheetState.partialExpand()
                else -> {}
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) { Text("Delete") }
                Button(
                    onClick = { onSave(item.copy(status = status, progress = progress, myRating = rating, watchStartDate = startDate, watchEndDate = endDate, isRewatching = rewatching, timesRewatched = timesRewatched, notes = notes, comments = comments)) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                ) { Text("Save change") }
            }

            Text("Status", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
            val statusOptions = remember(item.type) { WatchStatus.entries.filterNot { it == if (item.type == MediaType.Anime) WatchStatus.Reading else WatchStatus.Watching } }
            val statusListState = rememberLazyListState()
            val statusScope = rememberCoroutineScope()
            LazyRow(state = statusListState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 9.dp)) {
                itemsIndexed(statusOptions) { index, s ->
                    FilterChip(
                        selected = status == s,
                        onClick = {
                            status = s
                            // Auto-fill progress to the max when marking as completed
                            if (s == WatchStatus.Completed && item.total > 0) progress = item.total
                            statusScope.centerChip(statusListState, index)
                        },
                        label = { Text(s.displayLabel(item.type)) },
                        colors = kikoFilterChipColors(),
                    )
                }
            }

            Text(if (item.type == MediaType.Anime) "Episodes watched" else "Chapters read", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { if (progress > 0) progress-- }) { Icon(Icons.Default.Remove, "Decrease", tint = c.primary) }
                Text(if (item.total > 0) "$progress/${item.total}" else progress.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                IconButton(onClick = { if (item.total <= 0 || progress < item.total) progress++ }) { Icon(Icons.Default.Add, "Increase", tint = c.primary) }
            }

            Text("Your rating", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.roundToInt() },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.surfaceLow),
                    modifier = Modifier.weight(1f),
                )
                // Star matches the app's rating iconography elsewhere (e.g. score display),
                // just recolored to this component's own primary/primaryContainer accent
                // instead of the MAL-score amber, since this is the user's own rating.
                Box(Modifier.padding(start = 14.dp).size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = c.primaryContainer, modifier = Modifier.fillMaxSize())
                    Text(if (rating == 0) "–" else rating.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.primary)
                }
            }

            Text("Dates", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(Modifier.weight(1f), label = "Start date", value = startDate, onPick = { startDate = it })
                DateField(Modifier.weight(1f), label = "End date", value = endDate, onPick = { endDate = it })
            }

            Text(rewatchWord, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Currently ${rewatchWord.lowercase()}ing", color = c.ink, fontSize = 14.sp)
                Switch(checked = rewatching, onCheckedChange = { rewatching = it }, colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { if (timesRewatched > 0) timesRewatched-- }) { Icon(Icons.Default.Remove, "Decrease", tint = c.primary) }
                Text("Times $rewatchedWord: $timesRewatched", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                IconButton(onClick = { timesRewatched++ }) { Icon(Icons.Default.Add, "Increase", tint = c.primary) }
            }

            Text("Tags", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                placeholder = { Text("Comma separated, e.g. comfort watch, rewatch", color = c.muted) },
                minLines = 3, maxLines = 6,
                shape = RoundedCornerShape(kikoCorner(16.dp)),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.outline, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            )

            Text("Notes", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            OutlinedTextField(
                value = comments, onValueChange = { comments = it },
                placeholder = { Text("Write a note about this entry", color = c.muted) },
                minLines = 3, maxLines = 6,
                shape = RoundedCornerShape(kikoCorner(16.dp)),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.outline, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            )
        }
    }
}
// Tappable date picker field

@Composable fun DateField(modifier: Modifier = Modifier, label: String, value: String, onPick: (String) -> Unit) {
    val c = LocalKikoColors.current
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh).kikoClickable { showPicker = true }.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(if (value.isBlank()) "Not set" else formatUserDate(value), color = if (value.isBlank()) c.muted else c.ink, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 6.dp))
            Icon(Icons.Default.DateRange, null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.toEpochMillisOrNull())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.toIsoDate()) }; showPicker = false }) { Text("OK", color = c.primary, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel", color = c.muted) } },
            colors = DatePickerDefaults.colors(containerColor = c.background),
        ) { DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = c.background, selectedDayContainerColor = c.primary, todayDateBorderColor = c.primary)) }
    }
}
// Parse date to millis

fun String.toEpochMillisOrNull(): Long? {
    if (isBlank()) return null
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        sdf.parse(this)?.time
    } catch (e: Exception) { null }
}
// Format millis to date

fun Long.toIsoDate(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(this))
}
// Format date for display

fun formatUserDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val out = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        out.format(parser.parse(raw)!!)
    } catch (e: Exception) { raw }
}
// Update available dialog sheet