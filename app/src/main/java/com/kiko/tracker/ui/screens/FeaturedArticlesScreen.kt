@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.kiko.tracker.ui.components.SkeletonBlock
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
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
    // Persistent search field under the header now (Search & Discover's own
    // shape) instead of an icon that expands/collapses — text stays local
    // until submitted, so typing doesn't refetch on every keystroke.
    var searchQuery by remember { mutableStateOf(vm.featuredArticlesQuery) }
    val searchFocusRequester = remember { FocusRequester() }
    // Resets just the field's own local state — used when a tag gets
    // applied from the sheet below, so the two filters never fight over
    // what's showing in the header (selectFeaturedArticlesTag already
    // clears the vm's query, this just keeps the UI in sync with it).
    val collapseSearchUi: () -> Unit = { searchQuery = "" }
    // Tag-filter sheet — "All" plus every category off
    // myanimelist.net/featured/tag (Interview, Analysis, Cosplay, ...).
    var tagSheetOpen by remember { mutableStateOf(false) }
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
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = vm.featuredArticlesLoading && vm.featuredArticles.isNotEmpty(), onRefresh = { vm.loadFeaturedArticlesGrid(force = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp),
            ) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        Text("Featured Articles", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                    }
                    // Search bar + tag filter button, same row shape as
                    // Search & Discover's own SearchField/FilterIconButton
                    // pair — IntrinsicSize.Min so the tag button matches the
                    // field's height exactly regardless of platform default.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 15.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            SearchField(
                                value = searchQuery,
                                change = { searchQuery = it },
                                hint = "Search articles",
                                onSearch = { vm.searchFeaturedArticles(searchQuery) },
                                onClear = { searchQuery = ""; vm.searchFeaturedArticles("") },
                                focusRequester = searchFocusRequester,
                            )
                        }
                        if (vm.featuredTags.isNotEmpty()) {
                            FeaturedTagFilterButton(
                                selectedName = vm.featuredTags.firstOrNull { it.slug == vm.featuredArticlesTagSlug }?.name,
                                onClick = { tagSheetOpen = true },
                                modifier = Modifier.fillMaxHeight().padding(start = 10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    vm.featuredArticlesError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                }
                if (vm.featuredArticlesLoading && vm.featuredArticles.isEmpty()) {
                    items(6, key = { "skeleton_$it" }) { i ->
                        StaggeredItem(i) {
                            Column {
                                FeaturedArticleListRowSkeleton()
                                if (i < 5) HorizontalDivider(thickness = 1.dp, color = c.outlineVariant)
                            }
                        }
                    }
                } else if (vm.featuredArticles.isEmpty() && vm.featuredArticlesError == null) {
                    item(key = "empty") { Text("No articles found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
                } else {
                    itemsIndexed(vm.featuredArticles, key = { _, it -> it.url }) { index, article ->
                        StaggeredItem(index) {
                            Column {
                                FeaturedArticleListRow(article) { openArticle(article) }
                                if (index < vm.featuredArticles.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.outlineVariant)
                            }
                        }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp),
        )
    }
}

// Small header button that opens FeaturedTagFilterSheet — shows the
// selected tag's name once one is applied (truncated), otherwise just
// "Tags". Sized to match whatever height its caller gives it (the search
// field's own height, via the parent Row's IntrinsicSize.Min) rather than a
// fixed height of its own, same "match the field beside it" shape as
// Search & Discover's FilterIconButton.
@Composable fun FeaturedTagFilterButton(selectedName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val active = selectedName != null
    Row(
        modifier
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(if (active) c.primary else c.surfaceContainerHigh)
            .kikoClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
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

// Single card in the single-column browse list — thumbnail, title, author,
// and an optional tag pill (Advertorial/Spoiler/Events/...) echoing the one
// shown on myanimelist.net's own news-unit rows.
//
// Row layout with a square-ish thumbnail (not a full-width banner) — MAL's
// own news-unit list serves these particular images as small, unproxied
// (no "/r/WxH/" resize path) uploads, so they're genuinely low-resolution
// source files rather than crops of a bigger master asset. Stretched across
// the full card width at 180dp tall (the old layout) that low native
// resolution became obvious — soft, and with Crop's zoom-to-fill, sometimes
// showing little more than an enlarged corner of the image. Sizing the
// thumbnail close to how myanimelist.net itself displays these (a modest,
// roughly-square tile) keeps the same source file from having to stretch
// nearly as far, so it reads the same way it does on the site.
// "My List" row shape (no card background, plain row separated by a hairline
// divider) instead of a Card — but the thumbnail sits as a full-width banner
// on top rather than a leading square/portrait cover. Fixed wide aspect
// ratio (16:9, matches the fallback/skeleton box below) instead of sizing
// off the image's own intrinsic dimensions — leaving height unconstrained
// let it render as a big square while Coil had no reported size yet, and
// most of these article thumbnails are wide (banner-shaped) source images
// to begin with, so a wide box crops very little off them.
@Composable fun FeaturedArticleListRow(article: FeaturedArticleEntry, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .kikoClickable(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh),
        ) {
            if (article.image.isNotBlank()) {
                AsyncImage(
                    // Size.ORIGINAL so Coil decodes at the source's own
                    // resolution instead of downsampling to a guessed view
                    // size — there's no quality to give up further on top of
                    // an already-small source file.
                    model = ImageRequest.Builder(context).data(article.image).size(Size.ORIGINAL).allowHardware(true).build(),
                    contentDescription = article.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
            } else {
                Text(article.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 30.sp, color = c.muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        Text(article.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
        if (article.snippet.isNotBlank()) {
            Text(article.snippet, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (article.author.isNotBlank()) Text("by ${article.author}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (article.views.isNotBlank()) {
                if (article.author.isNotBlank()) Text("  ·  ", color = c.muted, fontSize = 12.sp)
                Icon(Icons.Default.Visibility, null, tint = c.muted, modifier = Modifier.size(12.dp))
                Text(" ${article.views} views", color = c.muted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
        }
        if (article.tag.isNotBlank()) Box(Modifier.padding(top = 8.dp)) { Pill(article.tag, c.primaryContainer, c.onPrimaryContainer) }
    }
}

@Composable fun FeaturedArticleListRowSkeleton() {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = RoundedCornerShape(kikoCorner(14.dp)))
        SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth().height(16.dp))
        SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(16.dp))
        SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth().height(13.dp))
        SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).height(13.dp))
        SkeletonBlock(Modifier.padding(top = 10.dp).width(90.dp).height(14.dp))
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
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 14.dp, end = 14.dp, bottom = if (showGoToTop) 90.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                    Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
            Text(content?.title?.ifBlank { title } ?: title, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp))
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
                    data.blocks.forEach { block -> ArticleBlockView(block, c, onOpenProfileLink = onOpenProfileLink) { fullscreenImage = it } }
                }
                if (data.blocks.isEmpty() && !loading && error == null) {
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

@Composable fun ArticleBlockView(block: ArticleBlock, c: com.kiko.tracker.ui.theme.KikoColors, onOpenProfileLink: (MalProfileLink) -> Unit = {}, onImageTap: (String) -> Unit) {
    when (block) {
        is ArticleBlock.Heading -> LinkifiedText(block.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = c.ink, modifier = Modifier.padding(top = 6.dp), onOpenProfileLink = onOpenProfileLink)
        is ArticleBlock.Paragraph -> LinkifiedText(block.text, fontSize = 14.sp, lineHeight = 21.sp, color = c.ink, onOpenProfileLink = onOpenProfileLink)
        is ArticleBlock.Image -> ForumImage(block.url, c, onImageTap)
        is ArticleBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(if (block.ordered) "${index + 1}." else "•", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp).width(18.dp))
                    LinkifiedText(item, color = c.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f), onOpenProfileLink = onOpenProfileLink)
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