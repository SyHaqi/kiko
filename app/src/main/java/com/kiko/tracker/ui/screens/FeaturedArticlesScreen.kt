@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import com.kiko.tracker.ui.components.LinkifiedText
import com.kiko.tracker.ui.components.SkeletonBlock
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.ui.theme.pressScale
import com.kiko.tracker.viewmodel.LibraryViewModel

// Full "Featured Articles" browse — 2-column grid, infinite scroll,
// backed by LibraryViewModel.loadFeaturedArticlesGrid/loadMoreFeaturedArticlesGrid
// (MalDetailScrapeApi.fetchFeaturedArticlesPage). Same shape as StacksScreen's
// browse grid; tapping a card opens FeaturedArticleScreen in-app instead of
// the CustomTabsIntent browser tab Home/Detail used to use.
@Composable fun FeaturedArticlesScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenArticle: (String, String) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.loadFeaturedArticlesGrid() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = vm.featuredArticlesScrollIndex,
        initialFirstVisibleItemScrollOffset = vm.featuredArticlesScrollOffset,
    )
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 600 } }
    val openArticle: (FeaturedArticleEntry) -> Unit = { article ->
        vm.saveFeaturedArticlesScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        onOpenArticle(article.url, article.title)
    }
    // Load next page as
    // last two grid rows
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to gridState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 5) vm.loadMoreFeaturedArticlesGrid() }
    }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = vm.featuredArticlesLoading && vm.featuredArticles.isNotEmpty(), onRefresh = { vm.loadFeaturedArticlesGrid(force = true) }, modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (showGoToTop) 90.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        Text("Featured Articles", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                    }
                    vm.featuredArticlesError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                }
                if (vm.featuredArticlesLoading && vm.featuredArticles.isEmpty()) {
                    items(8) { i -> StaggeredItem(i) { FeaturedArticleGridCardSkeleton() } }
                } else if (vm.featuredArticles.isEmpty() && vm.featuredArticlesError == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("No articles found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                } else {
                    itemsIndexed(vm.featuredArticles, key = { _, it -> it.url }) { index, article ->
                        StaggeredItem(index) { FeaturedArticleGridCard(article) { openArticle(article) } }
                    }
                }
                if (vm.featuredArticlesLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) } }
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { gridState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}

// Single card in the 2-column browse grid — banner cover, title,
// author, and an optional tag pill (Advertorial/Spoiler/Events/...)
// echoing the one shown on myanimelist.net's own news-unit rows.
@Composable fun FeaturedArticleGridCard(article: FeaturedArticleEntry, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(kikoCorner(18.dp)),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
        modifier = Modifier.fillMaxWidth().pressScale(interactionSource),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(100.dp)
                    .clip(RoundedCornerShape(topStart = kikoCorner(18.dp), topEnd = kikoCorner(18.dp)))
                    .background(c.surfaceContainerHigh),
            ) {
                if (article.image.isNotBlank()) {
                    AsyncImage(model = article.image, contentDescription = article.title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Text(article.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
                }
                if (article.tag.isNotBlank()) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(8.dp).clip(kikoPillShape()).background(c.primaryContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text(article.tag, color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                }
            }
            Column(Modifier.padding(11.dp)) {
                Text(article.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (article.author.isNotBlank()) Text("by ${article.author}", color = c.muted, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (article.views.isNotBlank()) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, null, tint = c.muted, modifier = Modifier.size(11.dp))
                        Text(article.views, color = c.muted, fontWeight = FontWeight.Medium, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable fun FeaturedArticleGridCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainer)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(topStart = kikoCorner(18.dp), topEnd = kikoCorner(18.dp)))
        Column(Modifier.padding(11.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(12.dp))
            SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.7f).height(12.dp))
            SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.4f).height(10.dp))
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