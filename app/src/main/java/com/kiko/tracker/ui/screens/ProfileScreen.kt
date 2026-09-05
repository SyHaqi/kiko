@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import coil.compose.AsyncImage
import com.kiko.tracker.BuildConfig
import com.kiko.tracker.data.api.MalProfile
import com.kiko.tracker.data.model.ColorSource
import com.kiko.tracker.data.model.ListViewMode
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.PaletteStyle
import com.kiko.tracker.data.model.ThemeMode
import com.kiko.tracker.data.model.TitleLanguage
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.oneDecimal
import com.kiko.tracker.data.model.twoDecimals
import com.kiko.tracker.ui.components.Pill
import com.kiko.tracker.ui.components.TypeToggle
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.components.statusColor
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.ui.theme.rememberStaggerMemory
import com.kiko.tracker.util.AppUpdateInfo
import com.kiko.tracker.viewmodel.LibraryViewModel

// Full page for the
@Composable fun ProfileStatsScreen(
    connected: Boolean, profile: MalProfile?, items: List<MediaItem>, onConnect: () -> Unit, onBack: () -> Unit,
    scrollOffset: Int = 0, onSaveScroll: (Int) -> Unit = {}, statsTab: MediaType = MediaType.Anime, onStatsTabChange: (MediaType) -> Unit = {},
    onScoreClick: (MediaType, Int) -> Unit = { _, _ -> }, onYearClick: (MediaType, Int) -> Unit = { _, _ -> }, onFormatClick: (MediaType, String) -> Unit = { _, _ -> },
    onGenreClick: (MediaType, String) -> Unit = { _, _ -> },
    onSignOut: () -> Unit = {}, refreshing: Boolean = false, onRefresh: () -> Unit = {},
) {
    val c = LocalKikoColors.current
    // Leaving the Profile page
    // remembered scroll offset, so
    // (Drilling into the score
    // separately below — that
    val exitProfile = { onBack(); onStatsTabChange(MediaType.Anime); onSaveScroll(0) }
    BackHandler(onBack = exitProfile)
    // Confirm before signing out
    // account it signs out
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
    // Restore scroll position on
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

// Full page for the
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

// Profile header card +
// expandable "avatar + name"
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
        // remember(items): same reasoning as
        // screens below (typeItems at
        // whole library on every
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
                    // Basic cross-fade between the
                    // tab-switch transition used elsewhere
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

// Settings list — theme,
// Lives in the profile
// (top-right of ProfileStatsScreen) —
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
        // Pure-black backgrounds for OLED/AMOLED
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

// Opened by tapping a
// Starts on the tapped
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
// Score chip row: "All"

@Composable fun ScoreFilterRow(current: Int, set: (Int) -> Unit) {
    val c = LocalKikoColors.current
    val colors = kikoFilterChipColors()
    val scores = remember { (10 downTo 1).toList() }
    val initialIndex = remember { if (current == 0) 0 else scores.indexOf(current) + 1 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    // Land already-scrolled near the
    // chart opens this screen
    // the way to center
    // stays scrolled off past
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
// Opened by tapping a
// Starts on the tapped
// appears in the list,
// ScoreFilterScreen above — same
// year instead of score
// isn't limited to rated
@Composable fun YearFilterScreen(vm: LibraryViewModel, type: MediaType, initialYear: Int, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var year by remember { mutableStateOf(initialYear) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    // Same tolerant startDate parsing
    // here line up with
    fun releaseYear(item: MediaItem) = item.startDate.take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
    val years = remember(typeItems) { typeItems.mapNotNull(::releaseYear).distinct().sortedDescending() }
    val filtered = remember(typeItems, year, vm.yearFilterSort, vm.titleLanguage) {
        typeItems.filter { val y = releaseYear(it); y != null && (year == 0 || y == year) }.sortedWithListSort(vm.yearFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.yearFilterViewMode == ListViewMode.Grid
    // Year picker moved off
    // them scrolled out of
    // GenreFilterScreen below. See YearFilterFab/YearFilterSheet.
    var yearSheetOpen by remember { mutableStateOf(false) }
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Year Distribution", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.yearFilterViewMode) { vm.setYearFilterViewMode(context, it) }
                SortMenu(vm.yearFilterSort) { vm.setYearFilterSort(context, it) }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
                if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles from this year yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
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
        YearFilterFab(year, onClick = { yearSheetOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp))
    }
    if (yearSheetOpen) YearFilterSheet(years, year, onDismiss = { yearSheetOpen = false }) { year = it; yearSheetOpen = false }
}
// Year picker FAB —
// "spell it out, don't
// Tapping it opens YearFilterSheet
// count here is unbounded

@Composable fun YearFilterFab(current: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = c.primary,
        contentColor = c.onPrimary,
        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
        text = { Text(if (current == 0) "All Years" else current.toString()) },
        modifier = modifier,
    )
}
// Year picker sheet —
// first, wrapped into a
// every year is reachable
// AdvancedFilterSheet genre/theme sections (see

@Composable fun YearFilterSheet(years: List<Int>, current: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val c = LocalKikoColors.current
    val colors = kikoFilterChipColors()
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Filter by", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Year", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = current == 0, onClick = { onSelect(0) }, label = { Text("All") }, colors = colors)
                years.forEach { y -> FilterChip(selected = current == y, onClick = { onSelect(y) }, label = { Text(y.toString()) }, colors = colors) }
            }
        }
    }
}
// Opened by tapping a
// Starts on the tapped
// present in the list,
// YearFilterScreen above — same
// string instead of score
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
// Format chip row: "All"
// Manga/Manhua/Light Novel), alphabetical —

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
// Opened by tapping a
// Starts on the tapped
// present in the list,
// above — same header/list/grid/sort
@Composable fun GenreFilterScreen(vm: LibraryViewModel, type: MediaType, initialGenre: String, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var genre by remember { mutableStateOf(initialGenre) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    // Ranked most → least,
    // without the chart's top-6
    val genreCounts = remember(typeItems) { typeItems.flatMap { it.genres }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value } }
    val filtered = remember(typeItems, genre, vm.genreFilterSort, vm.titleLanguage) {
        typeItems.filter { genre.isBlank() || it.genres.any { g -> g == genre } }.sortedWithListSort(vm.genreFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.genreFilterViewMode == ListViewMode.Grid
    // Genre picker moved off
    // past 30-40 distinct tags,
    // see GenreFilterFab/GenreFilterSheet below.
    var genreSheetOpen by remember { mutableStateOf(false) }
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Genre Breakdown", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.genreFilterViewMode) { vm.setGenreFilterViewMode(context, it) }
                SortMenu(vm.genreFilterSort) { vm.setGenreFilterSort(context, it) }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
                if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles with this genre yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
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
        GenreFilterFab(genre, onClick = { genreSheetOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp))
    }
    if (genreSheetOpen) GenreFilterSheet(genreCounts, typeItems.size, genre, onDismiss = { genreSheetOpen = false }) { genre = it; genreSheetOpen = false }
}
// Genre picker FAB —
// StatusFilterFab. Truncates to one
// doesn't blow up the

@Composable fun GenreFilterFab(current: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = c.primary,
        contentColor = c.onPrimary,
        icon = { Icon(Icons.Default.Sell, contentDescription = null) },
        text = { Text(current.ifBlank { "All Genres" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
    )
}
// Genre picker sheet —
// with a proportional bar
// distribution the chart summarizes

@Composable fun GenreFilterSheet(genres: List<Pair<String, Int>>, total: Int, current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val c = LocalKikoColors.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Filter by", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Genre", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GenreFilterBarRow("All", total, total, c.primary, selected = current.isBlank()) { onSelect("") }
                genres.forEachIndexed { index, (g, count) -> GenreFilterBarRow(g, count, total, chartColor(c, index), selected = current == g) { onSelect(g) } }
            }
        }
    }
}
// One ranked row in
// library total, colored the
// color reads the same

@Composable fun GenreFilterBarRow(label: String, count: Int, total: Int, barColor: Color, selected: Boolean, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val fraction = if (total > 0) (count.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(14.dp)))
            .let { m -> if (selected) m.background(c.surfaceContainerHigh) else m }
            .kikoClickable(scale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp).padding(end = 8.dp))
                Text(label, color = c.ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
            }
            Text(count.toString(), color = barColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(kikoPillShape()).background(c.surfaceLow)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(kikoPillShape()).background(barColor))
        }
    }
}

// App info page