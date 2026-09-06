@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.kiko.tracker.data.api.MalDetailScrapeApi
import com.kiko.tracker.data.model.ArticleBlock
import com.kiko.tracker.data.model.FeaturedArticleContent
import com.kiko.tracker.data.model.FeaturedArticleEntry
import com.kiko.tracker.data.model.FeaturedTag
import com.kiko.tracker.ui.components.LinkifiedText
import com.kiko.tracker.ui.components.Pill
import com.kiko.tracker.ui.components.SearchField
import com.kiko.tracker.ui.components.SkeletonBlock
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.ui.theme.pressScale
import com.kiko.tracker.viewmodel.LibraryViewModel

// Full "Featured Articles" browse — single-column list, infinite scroll,
// backed by LibraryViewModel.loadFeaturedArticlesGrid/loadMoreFeaturedArticlesGrid
// (MalDetailScrapeApi.fetchFeaturedArticlesPage). Tapping a card opens
// FeaturedArticleScreen in-app instead of the CustomTabsIntent browser tab
// Home/Detail used to use. Every item below (including the header, empty
// state, and loading footer) carries an explicit `key` so LazyColumn can
// diff/animate reliably and scroll stays smooth as pages load in.
@Composable fun FeaturedArticlesScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenArticle: (String, String) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.loadFeaturedArticlesGrid(); vm.loadFeaturedTags() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = vm.featuredArticlesScrollIndex,
        initialFirstVisibleItemScrollOffset = vm.featuredArticlesScrollOffset,
    )
    val scope = rememberCoroutineScope()
    // Search icon in the header expands into this field (My List's own
    // search-on-submit shape) — text is local until submitted, so typing
    // doesn't refetch on every keystroke.
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(vm.featuredArticlesQuery) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchExpanded) { if (searchExpanded) searchFocusRequester.requestFocus() }
    val closeSearch: () -> Unit = { searchExpanded = false; searchQuery = ""; vm.searchFeaturedArticles("") }
    // Only resets the field's own local state — used when a tag gets
    // applied from the sheet below, so the two filters never fight over
    // what's showing in the header (selectFeaturedArticlesTag already
    // clears the vm's query, this just keeps the UI in sync with it).
    val collapseSearchUi: () -> Unit = { searchExpanded = false; searchQuery = "" }
    // Tag-filter sheet — "All" plus every category off
    // myanimelist.net/featured/tag (Interview, Analysis, Cosplay, ...).
    var tagSheetOpen by remember { mutableStateOf(false) }
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    val openArticle: (FeaturedArticleEntry) -> Unit = { article ->
        vm.saveFeaturedArticlesScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onOpenArticle(article.url, article.title)
    }
    // Load next page as the last few rows come into view
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 5) vm.loadMoreFeaturedArticlesGrid() }
    }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = vm.featuredArticlesLoading && vm.featuredArticles.isNotEmpty(), onRefresh = { vm.loadFeaturedArticlesGrid(force = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        if (searchExpanded) {
                            Box(Modifier.weight(1f).padding(start = 12.dp)) {
                                SearchField(
                                    value = searchQuery,
                                    change = { searchQuery = it },
                                    hint = "Search articles",
                                    onSearch = { vm.searchFeaturedArticles(searchQuery) },
                                    onClear = { searchQuery = ""; vm.searchFeaturedArticles("") },
                                    focusRequester = searchFocusRequester,
                                )
                            }
                            IconButton(onClick = closeSearch, modifier = Modifier.padding(start = 8.dp).size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.Close, "Close search", tint = c.ink) }
                        } else {
                            Text("Featured Articles", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            if (vm.featuredTags.isNotEmpty()) {
                                FeaturedTagFilterButton(
                                    selectedName = vm.featuredTags.firstOrNull { it.slug == vm.featuredArticlesTagSlug }?.name,
                                    onClick = { tagSheetOpen = true },
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            IconButton(onClick = { searchExpanded = true }, modifier = Modifier.padding(start = 8.dp).size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.Search, "Search articles", tint = c.ink) }
                        }
                    }
                    vm.featuredArticlesError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                }
                if (vm.featuredArticlesLoading && vm.featuredArticles.isEmpty()) {
                    items(8, key = { "skeleton_$it" }) { i -> StaggeredItem(i) { FeaturedArticleGridCardSkeleton() } }
                } else if (vm.featuredArticles.isEmpty() && vm.featuredArticlesError == null) {
                    item(key = "empty") { Text("No articles found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                } else {
                    itemsIndexed(vm.featuredArticles, key = { _, it -> it.url }) { index, article ->
                        StaggeredItem(index) { FeaturedArticleGridCard(article) { openArticle(article) } }
                    }
                }
                if (vm.featuredArticlesLoadingMore) {
                    item(key = "loading_more") { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) } }
                }
            }
        }
        if (tagSheetOpen) {
            FeaturedTagFilterSheet(
                tags = vm.featuredTags,
                current = vm.featuredArticlesTagSlug,
                onDismiss = { tagSheetOpen = false },
                onApply = { picked -> tagSheetOpen = false; collapseSearchUi(); vm.selectFeaturedArticlesTag(picked) },
            )
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}

// Small header button that opens FeaturedTagFilterSheet — shows the
// selected tag's name once one is applied (truncated), otherwise just
// "Tags", same rounded-square footprint as the back/search buttons
// beside it but sized to its label instead of a fixed 38dp square.
@Composable fun FeaturedTagFilterButton(selectedName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val active = selectedName != null
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(kikoCorner(13.dp)))
            .background(if (active) c.primary else c.surfaceContainerHigh)
            .kikoClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.FilterList, "Filter by tag", tint = if (active) c.onPrimary else c.ink, modifier = Modifier.size(16.dp))
        Text(
            selectedName ?: "Tags",
            color = if (active) c.onPrimary else c.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp).widthIn(max = 100.dp),
        )
    }
}

// Tag-filter sheet — every category off myanimelist.net/featured/tag
// (Interview, Analysis, Cosplay, Studios, ...) as single-select chips,
// staged locally until "Apply" so browsing the sheet doesn't refetch on
// every tap. Same Reset/Apply footer shape as DiscoverScreen's own
// AdvancedFilterSheet.
@Composable fun FeaturedTagFilterSheet(tags: List<FeaturedTag>, current: String?, onDismiss: () -> Unit, onApply: (String?) -> Unit) {
    val c = LocalKikoColors.current
    var pending by remember { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Featured Articles", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Filter by tag", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = pending == null, onClick = { pending = null }, label = { Text("All") }, colors = kikoFilterChipColors())
                tags.forEach { tag -> FilterChip(selected = pending == tag.slug, onClick = { pending = tag.slug }, label = { Text(tag.name) }, colors = kikoFilterChipColors()) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { pending = null }, modifier = Modifier.weight(1f)) { Text("Reset", color = c.muted, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { onApply(pending) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    modifier = Modifier.weight(2f),
                ) { Text("Apply", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// Single card in the single-column browse list — bigger banner cover, title,
// author, and an optional tag pill (Advertorial/Spoiler/Events/...)
// echoing the one shown on myanimelist.net's own news-unit rows. Sized up
// now that it spans the full width instead of sharing a row with a sibling.
@Composable fun FeaturedArticleGridCard(article: FeaturedArticleEntry, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(kikoCorner(20.dp)),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
        modifier = Modifier.fillMaxWidth().pressScale(interactionSource),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(topStart = kikoCorner(20.dp), topEnd = kikoCorner(20.dp)))
                    .background(c.surfaceContainerHigh),
            ) {
                if (article.image.isNotBlank()) {
                    AsyncImage(
                        model = article.image,
                        contentDescription = article.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        // High filter quality so the bitmap is resampled with
                        // bilinear/mip-mapped filtering rather than Coil's
                        // low-quality default — this card's cover is now a
                        // full-width 180dp banner (much bigger than the old
                        // half-width 2-column tile), so the source thumbnail
                        // gets stretched noticeably more; without this the
                        // extra stretch reads as soft/blurry next to Home's
                        // smaller, sharper-looking 210x120dp article cards.
                        filterQuality = FilterQuality.High,
                    )
                } else {
                    Text(article.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
                }
            }
            Column(Modifier.padding(15.dp)) {
                // Tag pill sits above the title now — same placement as
                // Interest Stacks' own StackSpotlightCard (StackTagsRow
                // above stack.title), instead of overlaid on the cover image.
                if (article.tag.isNotBlank()) Box(Modifier.padding(bottom = 8.dp)) { Pill(article.tag, c.primaryContainer, c.onPrimaryContainer) }
                Text(article.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (article.author.isNotBlank()) Text("by ${article.author}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (article.views.isNotBlank()) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, null, tint = c.muted, modifier = Modifier.size(13.dp))
                        Text(article.views, color = c.muted, fontWeight = FontWeight.Medium, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable fun FeaturedArticleGridCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(20.dp))).background(c.surfaceContainer)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(topStart = kikoCorner(20.dp), topEnd = kikoCorner(20.dp)))
        Column(Modifier.padding(15.dp)) {
            SkeletonBlock(Modifier.width(64.dp).height(20.dp), shape = kikoPillShape())
            SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth().height(14.dp))
            SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.5f).height(14.dp))
            SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth(0.3f).height(11.dp))
        }
    }
}

// Single article reader — fetches MalDetailScrapeApi.fetchFeaturedArticle
// directly in a LaunchedEffect, same "no ViewModel round-trip for content,
// just local screen state" shape ForumTopicScreen uses for forum posts.
@Composable fun FeaturedArticleScreen(url: String, title: String, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var content by remember(url) { mutableStateOf<FeaturedArticleContent?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        loading = true
        error = null
        runCatching { MalDetailScrapeApi().fetchFeaturedArticle(url) }
            .onSuccess { content = it }
            .onFailure { error = it.message ?: "Could not load article" }
        loading = false
    }
    BackHandler(onBack = onBack)
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    fullscreenImage?.let { img -> ZoomableImageDialog(img, onDismiss = { fullscreenImage = null }) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { scrollState.value > 800 } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                Text(content?.title?.ifBlank { title } ?: title, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                    Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), color = c.primary, trackColor = c.surfaceLow)
            error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            if (loading && content == null) {
                FeaturedArticleReaderSkeleton()
            }
            content?.let { data ->
                if (data.author.isNotBlank() || data.date.isNotBlank() || data.views.isNotBlank()) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (data.author.isNotBlank()) Text("by ${data.author}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        if (data.date.isNotBlank()) Text(" · ${data.date}", color = c.muted, fontSize = 12.sp)
                        if (data.views.isNotBlank()) {
                            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Visibility, null, tint = c.muted, modifier = Modifier.size(12.dp))
                                Text(" ${data.views}", color = c.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (data.tags.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        data.tags.forEach { tag ->
                            Box(Modifier.padding(end = 6.dp).clip(kikoPillShape()).background(c.primaryContainer).padding(horizontal = 9.dp, vertical = 4.dp)) {
                                Text(tag, color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
                // Official/social links (Facebook, X, Discord, Steam, official
                // site, etc.) scraped out of the article body — same
                // CompanyLinkChip pill row as Company/Detail's own "Links"
                // section, so it looks identical here.
                if (data.links.isNotEmpty()) {
                    SectionTitle("Links", "", {})
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.links.forEach { (label, linkUrl) ->
                            CompanyLinkChip(label, linkUrl, onClick = { runCatching { uriHandler.openUri(linkUrl) } })
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.blocks.forEach { block -> ArticleBlockView(block, c) { fullscreenImage = it } }
                }
                if (data.blocks.isEmpty() && !loading && error == null) {
                    Text("Couldn't read this article's content — tap the browser icon above to view it on myanimelist.net.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp))
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { scrollState.animateScrollTo(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}

@Composable fun ArticleBlockView(block: ArticleBlock, c: com.kiko.tracker.ui.theme.KikoColors, onImageTap: (String) -> Unit) {
    when (block) {
        is ArticleBlock.Heading -> LinkifiedText(block.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = c.ink, modifier = Modifier.padding(top = 6.dp))
        is ArticleBlock.Paragraph -> LinkifiedText(block.text, fontSize = 14.sp, lineHeight = 21.sp, color = c.ink)
        is ArticleBlock.Image -> ForumImage(block.url, c, onImageTap)
        is ArticleBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(if (block.ordered) "${index + 1}." else "•", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp).width(18.dp))
                    LinkifiedText(item, color = c.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        ArticleBlock.Divider -> HorizontalDivider(thickness = 1.dp, color = c.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable fun FeaturedArticleReaderSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(200.dp), shape = RoundedCornerShape(kikoCorner(16.dp)))
        SkeletonBlock(Modifier.padding(top = 16.dp).fillMaxWidth().height(14.dp))
        SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth().height(14.dp))
        SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(14.dp))
        SkeletonBlock(Modifier.padding(top = 20.dp).fillMaxWidth(0.5f).height(16.dp))
        SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth().height(14.dp))
        SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.85f).height(14.dp))
    }
}