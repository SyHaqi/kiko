@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoSectionLoading
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.KikoLoadingRow
import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.kiko.tracker.ui.components.KikoPullToRefreshBox
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
import coil.request.ImageRequest
import coil.size.Size
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
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.viewmodel.LibraryViewModel

// Full "Featured Articles" browse — infinite scroll, backed by
// LibraryViewModel.loadFeaturedArticlesGrid/loadMoreFeaturedArticlesGrid
// (MalDetailScrapeApi.fetchFeaturedArticlesPage). Tapping an article opens
// FeaturedArticleScreen in-app.
//
// Layout (Material 3 Expressive, kept deliberately quiet): search field, one row of tag chips
// (single tap filters, no sheet), the newest article as a large rounded "hero" card, then the rest
// as a grouped list — big outer corners, tight inner corners, a 2dp gap and no divider lines, the
// same shape language as the Forums board list and Settings. Every item carries an explicit `key`
// so LazyColumn can diff/animate reliably and scroll stays smooth as pages load in.
@Composable fun FeaturedArticlesScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenArticle: (String, String) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.loadFeaturedArticlesGrid(); vm.loadFeaturedTags() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = vm.featuredArticlesScrollIndex,
        initialFirstVisibleItemScrollOffset = vm.featuredArticlesScrollOffset,
    )
    val scope = rememberCoroutineScope()
    // Text stays local until submitted, so typing doesn't refetch on every keystroke.
    var searchQuery by remember { mutableStateOf(vm.featuredArticlesQuery) }
    val searchFocusRequester = remember { FocusRequester() }
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    // Persist scroll position on ANY exit from this screen — tapping into an
    // article, pressing the header back button, or the system back gesture —
    // not just the "tap a card" path below. AnimatedContent (Navigation.kt)
    // fully disposes this composable when the top screen changes away from
    // it, so onDispose is the one place guaranteed to run before that
    // happens, matching HomeScreen's own saveHomeScroll-on-dispose shape.
    DisposableEffect(Unit) {
        onDispose { vm.saveFeaturedArticlesScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
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
    val articles = vm.featuredArticles
    val hero = articles.firstOrNull()
    val rest = if (articles.size > 1) articles.subList(1, articles.size) else emptyList()
    Column(Modifier.fillMaxSize()) {
        KikoTopBar(title = "Featured Articles", onBack = onBack, progress = { 1f })
        Box(Modifier.weight(1f)) {
            KikoPullToRefreshBox(isRefreshing = vm.featuredArticlesLoading && articles.isNotEmpty(), onRefresh = { vm.loadFeaturedArticlesGrid(force = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp),
                ) {
                    item(key = "header") {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            SearchField(
                                value = searchQuery,
                                change = { searchQuery = it },
                                hint = "Search articles",
                                onSearch = { vm.searchFeaturedArticles(searchQuery) },
                                onClear = { searchQuery = ""; vm.searchFeaturedArticles("") },
                                focusRequester = searchFocusRequester,
                            )
                            if (vm.featuredTags.isNotEmpty()) {
                                val chipState = androidx.compose.foundation.lazy.rememberLazyListState()
                                LazyRow(state = chipState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                                    item(key = "tag_all") {
                                        FilterChip(
                                            selected = vm.featuredArticlesTagSlug == null,
                                            onClick = { searchQuery = ""; vm.selectFeaturedArticlesTag(null); scope.centerChip(chipState, 0) },
                                            label = { Text("All") }, colors = kikoFilterChipColors(),
                                        )
                                    }
                                    itemsIndexed(vm.featuredTags, key = { _, tag -> tag.slug }) { index, tag ->
                                        FilterChip(
                                            selected = vm.featuredArticlesTagSlug == tag.slug,
                                            onClick = { searchQuery = ""; vm.selectFeaturedArticlesTag(tag.slug); scope.centerChip(chipState, index + 1) },
                                            label = { Text(tag.name) }, colors = kikoFilterChipColors(),
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(14.dp))
                            }
                            vm.featuredArticlesError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                        }
                    }
                    if (vm.featuredArticlesLoading && articles.isEmpty()) {
                        item(key = "loading") { KikoSectionLoading() }
                    } else if (articles.isEmpty() && vm.featuredArticlesError == null) {
                        item(key = "empty") {
                            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Article, null, tint = c.muted, modifier = Modifier.size(32.dp))
                                Text("No articles found", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                            }
                        }
                    } else {
                        if (hero != null) {
                            item(key = "hero") { FeaturedArticleHero(hero) { openArticle(hero) } }
                        }
                        if (rest.isNotEmpty()) {
                            item(key = "latest_label") {
                                Text("Latest", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp))
                            }
                            itemsIndexed(rest, key = { _, it -> it.url }) { index, article ->
                                StaggeredItem(index) {
                                    Box(Modifier.padding(bottom = 2.dp)) {
                                        FeaturedArticleListRow(article, forumGroupShape(index, rest.size)) { openArticle(article) }
                                    }
                                }
                            }
                        }
                    }
                    if (vm.featuredArticlesLoadingMore) {
                        item(key = "loading_more") { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 28.dp) } }
                    }
                }
            }
            GoToTopButton(
                visible = showGoToTop,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
            )
        }
    }
}

// "by Author · 1.2K views" — shared by the hero card and the list rows.
@Composable private fun ArticleMetaLine(author: String, views: String, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    if (author.isBlank() && views.isBlank()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (author.isNotBlank()) {
            Text("by $author", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        if (views.isNotBlank()) {
            if (author.isNotBlank()) Text("  ·  ", color = c.muted, fontSize = 12.sp)
            Icon(Icons.Rounded.Visibility, null, tint = c.muted, modifier = Modifier.size(13.dp))
            Text(" $views", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

// Category label (Interview, Analysis, Cosplay...) — plain primary-coloured caps instead of a pill, to keep the lists quiet.
@Composable private fun ArticleTagLabel(tag: String, modifier: Modifier = Modifier) {
    if (tag.isBlank()) return
    val c = LocalKikoColors.current
    Text(tag.uppercase(), color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

// The newest article as one big card: wide image on top, then label, title, a two-line snippet and meta.
// Most of these source images are small unproxied uploads, so Size.ORIGINAL keeps Coil from
// downsampling them any further than they already are.
@Composable fun FeaturedArticleHero(article: FeaturedArticleEntry, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(28.dp)))
            .background(c.surfaceContainerHigh)
            .kikoClickable(scale = 0.98f, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c.surfaceContainerHighest)) {
            if (article.image.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(article.image).size(Size.ORIGINAL).allowHardware(true).crossfade(true).build(),
                    contentDescription = article.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
            } else {
                Icon(Icons.Rounded.Article, null, tint = c.muted, modifier = Modifier.align(Alignment.Center).size(40.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            ArticleTagLabel(article.tag, Modifier.padding(bottom = 6.dp))
            Text(article.title, style = MaterialTheme.typography.titleLarge, fontSize = 22.sp, lineHeight = 29.sp, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (article.snippet.isNotBlank()) {
                Text(article.snippet, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
            ArticleMetaLine(article.author, article.views, Modifier.padding(top = 12.dp))
        }
    }
}

// One article in the grouped list: text on the left (label, title, meta), a small rounded thumbnail on the right.
@Composable fun FeaturedArticleListRow(
    article: FeaturedArticleEntry,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(kikoCorner(20.dp)),
    onClick: () -> Unit,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            ArticleTagLabel(article.tag, Modifier.padding(bottom = 4.dp))
            Text(article.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            ArticleMetaLine(article.author, article.views, Modifier.padding(top = 10.dp))
        }
        if (article.image.isNotBlank()) {
            Box(Modifier.padding(start = 14.dp).size(88.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHighest)) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(article.image).crossfade(true).build(),
                    contentDescription = article.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
    }
}


// Single article reader — fetches MalDetailScrapeApi.fetchFeaturedArticle
// directly in a LaunchedEffect, same "no ViewModel round-trip for content,
// just local screen state" shape ForumTopicScreen uses for forum posts.
@Composable fun FeaturedArticleScreen(vm: LibraryViewModel, url: String, title: String, onBack: () -> Unit, onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {}) {
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
    // A tapped body link that resolves to a MAL character/person/company
    // page (parseMalProfileLink) opens that page in-app instead of the
    // browser — same dispatch shape ForumTopicScreen's onOpenProfileLink
    // uses for forum-post links.
    val onOpenProfileLink: (MalProfileLink) -> Unit = { link ->
        when (link) {
            is MalProfileLink.Character -> onOpenCharacter(link.malId)
            is MalProfileLink.Person -> onOpenPerson(link.malId)
            is MalProfileLink.Company -> onOpenCompany(link.malId)
        }
    }
    BackHandler(onBack = onBack)
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    fullscreenImage?.let { img -> ZoomableImageDialog(img, onDismiss = { fullscreenImage = null }) }
    // Restore the reading position for THIS article's url, same
    // save/restore shape as Home/FeaturedArticlesScreen's own scroll
    // persistence — a plain remember(url) ties it to the current article so
    // opening a different article via the ArticleBlockView.Image/link cast
    // above never leaks its position into this one.
    val scrollState = remember(url) { androidx.compose.foundation.ScrollState(vm.featuredArticleScrollFor(url)) }
    // Persist on ANY exit — a link inside the article opening the browser
    // or an anime/manga Detail page (AnimatedContent disposes this
    // composable the moment topScreen switches away from it), the header
    // back button, or the system back gesture all end up here.
    DisposableEffect(url) {
        onDispose { vm.saveFeaturedArticleScroll(url, scrollState.value) }
    }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { scrollState.value > 800 } }
    Column(Modifier.fillMaxSize()) {
        val titleProgress = rememberTitleProgress(scrollState)
        KikoTopBar(title = content?.title?.ifBlank { title } ?: title, onBack = onBack, progress = { titleProgress.value }) {
            ShareMoreMenu(title = content?.title?.ifBlank { title } ?: title, shareUrl = url)
        }
        Box(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize()) {
                // Slightly wider side margins than list screens: this is long-form reading.
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 18.dp, end = 18.dp, bottom = if (showGoToTop) 90.dp else 32.dp)) {
                    Text(content?.title?.ifBlank { title } ?: title, style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp))
                    if (loading) KikoLoadingRow(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), color = c.primary)
                    error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                    content?.let { data ->
                        // "by Author · Nov 3, 2026 · 1.2K views" on one quiet line.
                        val meta = listOfNotNull(
                            data.author.takeIf { it.isNotBlank() }?.let { "by $it" },
                            data.date.takeIf { it.isNotBlank() },
                            data.views.takeIf { it.isNotBlank() }?.let { "$it views" },
                        ).joinToString("  ·  ")
                        if (meta.isNotEmpty()) Text(meta, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp)
                        if (data.tags.isNotEmpty()) {
                            FlowRow(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                data.tags.forEach { tag ->
                                    Box(Modifier.clip(kikoPillShape()).background(c.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(tag, color = c.onSecondaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                        HorizontalDivider(thickness = 1.dp, color = c.outlineVariant, modifier = Modifier.padding(top = 18.dp))
                        if (data.body.isNotBlank()) {
                            // Rich body: same BBCode renderer as forum posts, with reading-sized text and looser spacing.
                            ForumBody(
                                data.body, Modifier.padding(top = 20.dp), onOpenProfileLink = onOpenProfileLink,
                                background = c.background, textSize = 16f, lineHeight = 1.6f, blockSpacing = 18.dp,
                            )
                        } else {
                            Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                data.blocks.forEach { block -> ArticleBlockView(block, c, onOpenProfileLink = onOpenProfileLink) { fullscreenImage = it } }
                            }
                        }
                        if (data.blocks.isEmpty() && data.body.isBlank() && !loading && error == null) {
                            Text("Couldn't read this article's content — tap the browser icon above to view it on myanimelist.net.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp))
                        }
                    }
                }
                GoToTopButton(
                    visible = showGoToTop,
                    onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
                )
            }
        }
    }
}

// Body blocks tuned for reading: 16sp text on a 26sp line, a real heading step, and the same rounded
// image treatment the forum uses.
@Composable fun ArticleBlockView(block: ArticleBlock, c: com.kiko.tracker.ui.theme.KikoColors, onOpenProfileLink: (MalProfileLink) -> Unit = {}, onImageTap: (String) -> Unit) {
    when (block) {
        is ArticleBlock.Heading -> LinkifiedText(block.text, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 27.sp, color = c.ink, modifier = Modifier.padding(top = 8.dp), onOpenProfileLink = onOpenProfileLink)
        is ArticleBlock.Paragraph -> LinkifiedText(block.text, fontSize = 16.sp, lineHeight = 26.sp, color = c.ink, onOpenProfileLink = onOpenProfileLink)
        is ArticleBlock.Image -> ForumImage(block.url, c, onImageTap)
        is ArticleBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(if (block.ordered) "${index + 1}." else "\u2022", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(end = 10.dp).widthIn(min = 16.dp))
                    LinkifiedText(item, color = c.ink, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f), onOpenProfileLink = onOpenProfileLink)
                }
            }
        }
        ArticleBlock.Divider -> HorizontalDivider(thickness = 1.dp, color = c.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
    }
}