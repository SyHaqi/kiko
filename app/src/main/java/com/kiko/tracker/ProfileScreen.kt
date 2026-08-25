@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import coil.compose.AsyncImage

// Full page for the profile drawer's "avatar + name" row — profile stats
@Composable fun ProfileStatsScreen(
    connected: Boolean, profile: MalProfile?, items: List<MediaItem>, onConnect: () -> Unit, onBack: () -> Unit,
    scrollOffset: Int = 0, onSaveScroll: (Int) -> Unit = {}, statsTab: MediaType = MediaType.Anime, onStatsTabChange: (MediaType) -> Unit = {},
    onScoreClick: (MediaType, Int) -> Unit = { _, _ -> }, onYearClick: (MediaType, Int) -> Unit = { _, _ -> }, onFormatClick: (MediaType, String) -> Unit = { _, _ -> },
    onGenreClick: (MediaType, String) -> Unit = { _, _ -> },
    onSignOut: () -> Unit = {}, refreshing: Boolean = false, onRefresh: () -> Unit = {},
) {
    val c = LocalKikoColors.current
    // Leaving the Profile page entirely: reset the Anime/Manga switcher and the
    // remembered scroll offset, so re-opening Profile always starts fresh at the top.
    // (Drilling into the score distribution filter list and coming back is handled
    // separately below — that round trip is meant to preserve both.)
    val exitProfile = { onBack(); onStatsTabChange(MediaType.Anime); onSaveScroll(0) }
    BackHandler(onBack = exitProfile)
    // Confirm before signing out — moved here from Settings so sign out lives with the
    // account it signs out of, reachable straight from the top-right of Profile.
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = c.surfaceContainerHigh,
            title = { Text("Sign out?", color = c.ink) },
            text = { Text("Are you sure you want to sign out of your MyAnimeList account?", color = c.muted) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Cancel") } },
        )
    }
    // Restore scroll position on return from the score distribution drill-down instead of resetting to top
    val scrollState = rememberScrollState(initial = scrollOffset)
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = exitProfile, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                Text(profile?.name?.ifBlank { "Profile" } ?: "Profile", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp).weight(1f))
                if (connected) {
                    IconButton(onClick = { confirmSignOut = true }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Sign out", tint = c.danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Box(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                ProfileStatsSection(connected, profile, items, onConnect, statsTab = statsTab, onStatsTabChange = onStatsTabChange, onScoreClick = { type, score -> onSaveScroll(scrollState.value); onScoreClick(type, score) }, onYearClick = { type, year -> onSaveScroll(scrollState.value); onYearClick(type, year) }, onFormatClick = onFormatClick, onGenreClick = { type, genre -> onSaveScroll(scrollState.value); onGenreClick(type, genre) })
            }
        }
    }
}

// Full page for the profile drawer's "Settings" row
@Composable fun SettingsScreen(
    connected: Boolean, themeMode: ThemeMode, colorSource: ColorSource, paletteStyle: PaletteStyle, titleLanguage: TitleLanguage,
    nsfwEnabled: Boolean, onNsfwChange: (Boolean) -> Unit,
    amoledDark: Boolean, onAmoledDarkChange: (Boolean) -> Unit,
    onThemeClick: () -> Unit, onColorClick: () -> Unit, onPaletteClick: () -> Unit, onTitleLanguageClick: () -> Unit,
    updateInfo: AppUpdateInfo?, onAboutClick: () -> Unit, onBack: () -> Unit,
) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.padding(top = 12.dp, bottom = 24.dp)) {
            SettingsSection(
                connected = connected, themeMode = themeMode, colorSource = colorSource, paletteStyle = paletteStyle, titleLanguage = titleLanguage,
                nsfwEnabled = nsfwEnabled, onNsfwChange = onNsfwChange,
                amoledDark = amoledDark, onAmoledDarkChange = onAmoledDarkChange,
                onThemeClick = onThemeClick, onColorClick = onColorClick, onPaletteClick = onPaletteClick, onTitleLanguageClick = onTitleLanguageClick,
                updateInfo = updateInfo, onAboutClick = onAboutClick,
            )
        }
    }
}

// Profile header card + full anime/manga stats (used inside the profile drawer's
// expandable "avatar + name" row). Ends with the score distribution chart.
@Composable fun ProfileStatsSection(connected: Boolean, profile: MalProfile?, items: List<MediaItem>, onConnect: () -> Unit, statsTab: MediaType = MediaType.Anime, onStatsTabChange: (MediaType) -> Unit = {}, onScoreClick: (MediaType, Int) -> Unit = { _, _ -> }, onYearClick: (MediaType, Int) -> Unit = { _, _ -> }, onFormatClick: (MediaType, String) -> Unit = { _, _ -> }, onGenreClick: (MediaType, String) -> Unit = { _, _ -> }) {    val c = LocalKikoColors.current
    val context = LocalContext.current
    Column {
        // Profile header with stats
        if (connected && profile != null) {
            Card(shape = RoundedCornerShape(kikoCorner(28.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (profile.picture.isNotBlank()) {
                            AsyncImage(model = profile.picture, contentDescription = profile.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(64.dp).clip(kikoCircleShape()).background(c.warm))
                        } else {
                            Box(Modifier.size(64.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                                Text(profile.name.take(1).uppercase().ifBlank { "M" }, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = c.ink)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(profile.name.ifBlank { "MyAnimeList" }, style = MaterialTheme.typography.titleLarge, color = c.ink)
                            val joined = profile.joinedAt.take(10).takeIf { it.length == 10 }?.let { formatFullDate(it) }
                            if (joined != null) Text("Joined $joined", color = c.muted, fontSize = 13.sp)
                        }
                        // Open MAL profile page
                        if (profile.name.isNotBlank()) {
                            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/profile/${profile.name}")) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                                Icon(Icons.Default.OpenInNew, "Open profile in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    val details = listOfNotNull(
                        profile.location.takeIf { it.isNotBlank() },
                        profile.gender.takeIf { it.isNotBlank() },
                    )
                    if (details.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                            details.forEach { Pill(it, c.surfaceLow, c.muted) }
                        }
                    }
                }
            }
        }

        // Tabbed anime/manga stats card
        // remember(items): same reasoning as the ScoreFilter/YearFilter/FormatFilter/GenreFilter
        // screens below (typeItems at line ~347+) — without it this re-filters and re-sums the
        // whole library on every recomposition of the stats section, not just when it changes.
        val animeItems = remember(items) { items.filter { it.type == MediaType.Anime } }
        val mangaItems = remember(items) { items.filter { it.type == MediaType.Manga } }
        val mangaTotal = mangaItems.size
        val mangaChaptersRead = remember(mangaItems) { mangaItems.sumOf { it.progress } }
        val ratedManga = remember(mangaItems) { mangaItems.filter { it.myRating > 0 } }
        val mangaMeanScore = if (ratedManga.isNotEmpty()) ratedManga.map { it.myRating }.average() else 0.0
        val animeDaysWatched = profile?.animeDaysWatched ?: 0.0
        // MAL: 8 min/chapter
        val mangaDaysReadEst = mangaChaptersRead * 8.0 / 60.0 / 24.0
        if (connected && ((profile?.animeTotalEntries ?: 0) > 0 || mangaItems.isNotEmpty())) {
            if (animeDaysWatched > 0 || mangaDaysReadEst > 0) {
                Card(shape = RoundedCornerShape(kikoCorner(28.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("TIME WATCHED VS READ", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeroStat(Modifier.weight(1f), Icons.Default.PlayCircle, "Days watched", animeDaysWatched.oneDecimal(), c.lavender, c.primary)
                            HeroStat(Modifier.weight(1f), Icons.Default.MenuBook, "Days read (est.)", mangaDaysReadEst.oneDecimal(), c.primaryContainer, c.onPrimaryContainer)
                        }
                    }
                }
            }
            Card(shape = RoundedCornerShape(kikoCorner(28.dp)), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Text("STATS", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                    TypeToggle(statsTab, trackColor = c.surfaceLow) { onStatsTabChange(it) }
                    Spacer(Modifier.height(18.dp))
                    // Basic cross-fade between the anime/manga stat breakdowns, matching the
                    // tab-switch transition used elsewhere in the app (e.g. Clubs tabs)
                    AnimatedContent(
                        statsTab,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "profile-stats-tab",
                    ) { tab ->
                        Column {
                            if (tab == MediaType.Anime) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    LabeledStat("Days:", animeDaysWatched.oneDecimal(), c)
                                    LabeledStat("Mean Score:", (profile?.animeMeanScore ?: 0.0).let { if (it > 0) it.twoDecimals() else "—" }, c)
                                }
                                Spacer(Modifier.height(12.dp))
                                SegmentedStatBar(listOf(
                                    (profile?.animeWatching ?: 0) to statusColor("Watching"),
                                    (profile?.animeCompleted ?: 0) to statusColor("Completed"),
                                    (profile?.animeOnHold ?: 0) to statusColor("On hold"),
                                    (profile?.animeDropped ?: 0) to statusColor("Dropped"),
                                    (profile?.animePlanToWatch ?: 0) to statusColor("Plan to watch"),
                                ), c)
                                Spacer(Modifier.height(20.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        StatusLegendRow("Watching", profile?.animeWatching ?: 0, statusColor("Watching"), c)
                                        StatusLegendRow("Completed", profile?.animeCompleted ?: 0, statusColor("Completed"), c)
                                        StatusLegendRow("On-Hold", profile?.animeOnHold ?: 0, statusColor("On hold"), c)
                                        StatusLegendRow("Dropped", profile?.animeDropped ?: 0, statusColor("Dropped"), c)
                                        StatusLegendRow("Plan to Watch", profile?.animePlanToWatch ?: 0, statusColor("Plan to watch"), c)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        SummaryRow("Total Entries", formatExact(profile?.animeTotalEntries ?: 0), c)
                                        SummaryRow("Rewatched", formatExact(animeItems.sumOf { it.timesRewatched }), c)
                                        SummaryRow("Episodes", formatExact(profile?.animeEpisodesWatched ?: 0), c)
                                    }
                                }
                                if (animeItems.isNotEmpty()) {
                                    Spacer(Modifier.height(24.dp))
                                    Text("GENRE BREAKDOWN", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    GenreBreakdownChart(animeItems, c, onGenreClick = { onGenreClick(MediaType.Anime, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("SCORE DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    ScoreDistributionChart(animeItems, c, onScoreClick = { onScoreClick(MediaType.Anime, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("FORMAT BREAKDOWN", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    FormatBreakdownChart(animeItems, c, onFormatClick = { onFormatClick(MediaType.Anime, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("YEAR DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    YearDistributionChart(animeItems, c, onYearClick = { onYearClick(MediaType.Anime, it) })
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    LabeledStat("Days:", mangaDaysReadEst.oneDecimal() + " (est.)", c)
                                    LabeledStat("Mean Score:", if (mangaMeanScore > 0) mangaMeanScore.twoDecimals() else "—", c)
                                }
                                Spacer(Modifier.height(12.dp))
                                SegmentedStatBar(listOf(
                                    mangaItems.count { it.status == WatchStatus.Reading } to statusColor("Reading"),
                                    mangaItems.count { it.status == WatchStatus.Completed } to statusColor("Completed"),
                                    mangaItems.count { it.status == WatchStatus.OnHold } to statusColor("On hold"),
                                    mangaItems.count { it.status == WatchStatus.Dropped } to statusColor("Dropped"),
                                    mangaItems.count { it.status == WatchStatus.Plan } to statusColor("Plan to read"),
                                ), c)
                                Spacer(Modifier.height(20.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        StatusLegendRow("Reading", mangaItems.count { it.status == WatchStatus.Reading }, statusColor("Reading"), c)
                                        StatusLegendRow("Completed", mangaItems.count { it.status == WatchStatus.Completed }, statusColor("Completed"), c)
                                        StatusLegendRow("On-Hold", mangaItems.count { it.status == WatchStatus.OnHold }, statusColor("On hold"), c)
                                        StatusLegendRow("Dropped", mangaItems.count { it.status == WatchStatus.Dropped }, statusColor("Dropped"), c)
                                        StatusLegendRow("Plan to Read", mangaItems.count { it.status == WatchStatus.Plan }, statusColor("Plan to read"), c)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        SummaryRow("Total Entries", formatExact(mangaTotal), c)
                                        SummaryRow("Reread", formatExact(mangaItems.sumOf { it.timesRewatched }), c)
                                        SummaryRow("Chapters", formatExact(mangaChaptersRead), c)
                                    }
                                }
                                if (mangaItems.isNotEmpty()) {
                                    Spacer(Modifier.height(24.dp))
                                    Text("GENRE BREAKDOWN", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    GenreBreakdownChart(mangaItems, c, onGenreClick = { onGenreClick(MediaType.Manga, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("SCORE DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    ScoreDistributionChart(mangaItems, c, onScoreClick = { onScoreClick(MediaType.Manga, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("FORMAT BREAKDOWN", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    FormatBreakdownChart(mangaItems, c, onFormatClick = { onFormatClick(MediaType.Manga, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("YEAR DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    YearDistributionChart(mangaItems, c, onYearClick = { onYearClick(MediaType.Manga, it) })
                                }
                            }
                        }
                    }
                }
            }
        }

        // Only shown when signed-out
        if (!connected) {
            Card(shape = RoundedCornerShape(kikoCorner(28.dp)), colors = CardDefaults.cardColors(containerColor = c.lavender), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Text("Connect MyAnimeList", style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    Text("Sign in with your MyAnimeList account to bring in your real list.", color = c.muted, modifier = Modifier.padding(top = 8.dp, bottom = 15.dp))
                    Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) { Text("Sign in with MyAnimeList") }
                }
            }
        }
    }
}

// Settings list — theme, color, palette, title language, adult content, about.
// Lives in the profile drawer's "Settings" row. Sign out moved to the Profile page
// (top-right of ProfileStatsScreen) — it lives with the account it signs out of.
@Composable fun SettingsSection(
    connected: Boolean, themeMode: ThemeMode, colorSource: ColorSource, paletteStyle: PaletteStyle, titleLanguage: TitleLanguage,
    nsfwEnabled: Boolean, onNsfwChange: (Boolean) -> Unit,
    amoledDark: Boolean = false, onAmoledDarkChange: (Boolean) -> Unit = {},
    onThemeClick: () -> Unit, onColorClick: () -> Unit, onPaletteClick: () -> Unit, onTitleLanguageClick: () -> Unit,
    updateInfo: AppUpdateInfo? = null, onAboutClick: () -> Unit = {},
) {
    val c = LocalKikoColors.current
    Column {
        ListItem(headlineContent = { Text("Theme", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(themeMode.label, color = c.muted) }, leadingContent = { Icon(Icons.Default.Palette, null, tint = c.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(onClick = onThemeClick))
        ListItem(headlineContent = { Text("Color", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(colorSource.label, color = c.muted) }, leadingContent = { Icon(Icons.Default.ColorLens, null, tint = c.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(onClick = onColorClick))
        ListItem(headlineContent = { Text("Color palette", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(paletteStyle.label, color = c.muted) }, leadingContent = { Icon(Icons.Default.Gradient, null, tint = c.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(onClick = onPaletteClick))
        ListItem(headlineContent = { Text("Title language", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(titleLanguage.label, color = c.muted) }, leadingContent = { Icon(Icons.Default.Translate, null, tint = c.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(onClick = onTitleLanguageClick))
        // Pure-black backgrounds for OLED/AMOLED screens — only takes effect while dark theme is active
        ListItem(headlineContent = { Text("AMOLED black", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text("True black backgrounds in dark mode, saves battery on AMOLED screens", color = c.muted) }, leadingContent = { Icon(Icons.Default.DarkMode, null, tint = c.primary) }, trailingContent = { Switch(checked = amoledDark, onCheckedChange = onAmoledDarkChange, colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(headlineContent = { Text("Adult content", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(if (nsfwEnabled) "Hentai-rated titles are shown" else "Hentai-rated titles are hidden", color = c.muted) }, leadingContent = { Icon(Icons.Default.VisibilityOff, null, tint = c.primary) }, trailingContent = { Switch(checked = nsfwEnabled, onCheckedChange = onNsfwChange, colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        // Tap opens about page
        ListItem(
            headlineContent = { Text("About", fontWeight = FontWeight.Bold, color = c.ink) },
            supportingContent = { Text(if (updateInfo != null) "Update available — ${updateInfo.version}" else "v${BuildConfig.VERSION_NAME}", color = if (updateInfo != null) c.primary else c.muted, fontWeight = if (updateInfo != null) FontWeight.Bold else FontWeight.Normal) },
            leadingContent = {
                Box {
                    Icon(Icons.Default.Info, null, tint = c.primary)
                    if (updateInfo != null) Box(Modifier.size(8.dp).align(Alignment.TopEnd).clip(kikoCircleShape()).background(c.danger))
                }
            },
            trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(onClick = onAboutClick),
        )
    }
}

// Opened by tapping a bar in the profile's score distribution chart.
// Starts on the tapped score; the chip row lets the user switch to any other score, or "All" rated titles.
@Composable fun ScoreFilterScreen(vm: LibraryViewModel, type: MediaType, initialScore: Int, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var score by remember { mutableStateOf(initialScore) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    val filtered = remember(typeItems, score, vm.scoreFilterSort, vm.titleLanguage) {
        typeItems.filter { it.myRating > 0 && (score == 0 || it.myRating == score) }.sortedWithListSort(vm.scoreFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.scoreFilterViewMode == ListViewMode.Grid
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Score Distribution", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        ScoreFilterRow(score) { score = it }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.scoreFilterViewMode) { vm.setScoreFilterViewMode(context, it) }
                SortMenu(vm.scoreFilterSort) { vm.setScoreFilterSort(context, it) }
            }
        }
    }
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles at this score yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item { header() }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                StaggeredItem(index, staggerSeen) {
                    Column {
                        ListRow(it, onOpenDetail, showType = false)
                        if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                    }
                }
            }
            if (filtered.isEmpty()) item { Text("No titles at this score yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    }
}
// Score chip row: "All" plus 10 down to 1, same chip styling as the status FilterRow

@Composable fun ScoreFilterRow(current: Int, set: (Int) -> Unit) {
    val c = LocalKikoColors.current
    val colors = kikoFilterChipColors()
    val scores = remember { (10 downTo 1).toList() }
    val initialIndex = remember { if (current == 0) 0 else scores.indexOf(current) + 1 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    // Land already-scrolled near the pre-selected chip (e.g. tapping a bar in the score
    // chart opens this screen with that score already chosen), then nudge it the rest of
    // the way to center — otherwise the chip confirming which score you're looking at
    // stays scrolled off past the edge until you manually swipe the row to find it.
    LaunchedEffect(Unit) { centerChip(listState, initialIndex) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
        item { FilterChip(selected = current == 0, onClick = { set(0); scope.centerChip(listState, 0) }, label = { Text("All") }, colors = colors) }
        itemsIndexed(scores) { index, s ->
            FilterChip(
                selected = current == s,
                onClick = { set(s); scope.centerChip(listState, index + 1) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = if (current == s) c.onPrimary else Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                        Text(s.toString(), modifier = Modifier.padding(start = 3.dp))
                    }
                },
                colors = colors,
            )
        }
    }
}
// Opened by tapping a bar in the profile's year distribution chart.
// Starts on the tapped year; the chip row lets the user switch to any other year that
// appears in the list, or "All" titles with a known release year. Mirrors
// ScoreFilterScreen above — same header/list/grid/sort shape, just filtered by release
// year instead of score (and with no myRating requirement, since year distribution
// isn't limited to rated titles).
@Composable fun YearFilterScreen(vm: LibraryViewModel, type: MediaType, initialYear: Int, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var year by remember { mutableStateOf(initialYear) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    // Same tolerant startDate parsing as YearDistributionChart, so the years offered
    // here line up with the years the chart actually drew bars for.
    fun releaseYear(item: MediaItem) = item.startDate.take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
    val years = remember(typeItems) { typeItems.mapNotNull(::releaseYear).distinct().sortedDescending() }
    val filtered = remember(typeItems, year, vm.yearFilterSort, vm.titleLanguage) {
        typeItems.filter { val y = releaseYear(it); y != null && (year == 0 || y == year) }.sortedWithListSort(vm.yearFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.yearFilterViewMode == ListViewMode.Grid
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Year Distribution", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        YearFilterRow(years, year) { year = it }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.yearFilterViewMode) { vm.setYearFilterViewMode(context, it) }
                SortMenu(vm.yearFilterSort) { vm.setYearFilterSort(context, it) }
            }
        }
    }
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles from this year yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item { header() }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                StaggeredItem(index, staggerSeen) {
                    Column {
                        ListRow(it, onOpenDetail, showType = false)
                        if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                    }
                }
            }
            if (filtered.isEmpty()) item { Text("No titles from this year yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    }
}
// Year chip row: "All" plus every release year present in the list, most recent first

@Composable fun YearFilterRow(years: List<Int>, current: Int, set: (Int) -> Unit) {
    val c = LocalKikoColors.current
    val colors = kikoFilterChipColors()
    val initialIndex = remember { if (current == 0) 0 else years.indexOf(current) + 1 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { centerChip(listState, initialIndex) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
        item { FilterChip(selected = current == 0, onClick = { set(0); scope.centerChip(listState, 0) }, label = { Text("All") }, colors = colors) }
        itemsIndexed(years) { index, y -> FilterChip(selected = current == y, onClick = { set(y); scope.centerChip(listState, index + 1) }, label = { Text(y.toString()) }, colors = colors) }
    }
}
// Opened by tapping a row in the profile's format breakdown chart (donut + legend).
// Starts on the tapped format; the chip row lets the user switch to any other format
// present in the list, or "All" titles with a known format. Mirrors ScoreFilterScreen/
// YearFilterScreen above — same header/list/grid/sort shape, just filtered by format
// string instead of score or release year.
@Composable fun FormatFilterScreen(vm: LibraryViewModel, type: MediaType, initialFormat: String, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var format by remember { mutableStateOf(initialFormat) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    val formats = remember(typeItems) { typeItems.map { it.format }.filter { it.isNotBlank() }.distinct().sorted() }
    val filtered = remember(typeItems, format, vm.formatFilterSort, vm.titleLanguage) {
        typeItems.filter { it.format.isNotBlank() && (format.isBlank() || it.format == format) }.sortedWithListSort(vm.formatFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.formatFilterViewMode == ListViewMode.Grid
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Format Breakdown", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        FormatFilterRow(formats, format) { format = it }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.formatFilterViewMode) { vm.setFormatFilterViewMode(context, it) }
                SortMenu(vm.formatFilterSort) { vm.setFormatFilterSort(context, it) }
            }
        }
    }
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles of this format yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item { header() }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                StaggeredItem(index, staggerSeen) {
                    Column {
                        ListRow(it, onOpenDetail, showType = false)
                        if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                    }
                }
            }
            if (filtered.isEmpty()) item { Text("No titles of this format yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    }
}
// Format chip row: "All" plus every format present in the list (TV/OVA/Movie, or
// Manga/Manhua/Light Novel), alphabetical — same shape as YearFilterRow above.

@Composable fun FormatFilterRow(formats: List<String>, current: String, set: (String) -> Unit) {
    val c = LocalKikoColors.current
    val colors = kikoFilterChipColors()
    val initialIndex = remember { if (current.isBlank()) 0 else formats.indexOf(current) + 1 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { centerChip(listState, initialIndex) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
        item { FilterChip(selected = current.isBlank(), onClick = { set(""); scope.centerChip(listState, 0) }, label = { Text("All") }, colors = colors) }
        itemsIndexed(formats) { index, f -> FilterChip(selected = current == f, onClick = { set(f); scope.centerChip(listState, index + 1) }, label = { Text(f) }, colors = colors) }
    }
}
// Opened by tapping a row in the profile's genre breakdown chart.
// Starts on the tapped genre; the chip row lets the user switch to any other genre
// present in the list, or "All" titles with a known genre. Mirrors FormatFilterScreen
// above — same header/list/grid/sort shape, just filtered by genre instead of format.
@Composable fun GenreFilterScreen(vm: LibraryViewModel, type: MediaType, initialGenre: String, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var genre by remember { mutableStateOf(initialGenre) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    val genres = remember(typeItems) { typeItems.flatMap { it.genres }.filter { it.isNotBlank() }.distinct().sorted() }
    val filtered = remember(typeItems, genre, vm.genreFilterSort, vm.titleLanguage) {
        typeItems.filter { genre.isBlank() || it.genres.any { g -> g == genre } }.sortedWithListSort(vm.genreFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.genreFilterViewMode == ListViewMode.Grid
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Genre Breakdown", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        GenreFilterRow(genres, genre) { genre = it }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.genreFilterViewMode) { vm.setGenreFilterViewMode(context, it) }
                SortMenu(vm.genreFilterSort) { vm.setGenreFilterSort(context, it) }
            }
        }
    }
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles with this genre yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item { header() }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                StaggeredItem(index, staggerSeen) {
                    Column {
                        ListRow(it, onOpenDetail, showType = false)
                        if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.outlineVariant)
                    }
                }
            }
            if (filtered.isEmpty()) item { Text("No titles with this genre yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    }
}
// Genre chip row: "All" plus every genre present in the list, alphabetical — same
// shape as FormatFilterRow above.

@Composable fun GenreFilterRow(genres: List<String>, current: String, set: (String) -> Unit) {
    val c = LocalKikoColors.current
    val colors = kikoFilterChipColors()
    val initialIndex = remember { if (current.isBlank()) 0 else genres.indexOf(current) + 1 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { centerChip(listState, initialIndex) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
        item { FilterChip(selected = current.isBlank(), onClick = { set(""); scope.centerChip(listState, 0) }, label = { Text("All") }, colors = colors) }
        itemsIndexed(genres) { index, g -> FilterChip(selected = current == g, onClick = { set(g); scope.centerChip(listState, index + 1) }, label = { Text(g) }, colors = colors) }
    }
}

// App info page