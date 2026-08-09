@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.math.roundToInt

// Full page for the profile drawer's "avatar + name" row — profile stats
@Composable fun ProfileStatsScreen(connected: Boolean, profile: MalProfile?, items: List<MediaItem>, onConnect: () -> Unit, onBack: () -> Unit, scrollOffset: Int = 0, onSaveScroll: (Int) -> Unit = {}, onScoreClick: (MediaType, Int) -> Unit = { _, _ -> }) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    // Restore scroll position on return from the score distribution drill-down instead of resetting to top
    val scrollState = rememberScrollState(initial = scrollOffset)
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text(profile?.name?.ifBlank { "Profile" } ?: "Profile", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
            ProfileStatsSection(connected, profile, items, onConnect, onScoreClick = { type, score -> onSaveScroll(scrollState.value); onScoreClick(type, score) })
        }
    }
}

// Full page for the profile drawer's "Settings" row
@Composable fun SettingsScreen(
    connected: Boolean, themeMode: ThemeMode, colorSource: ColorSource, paletteStyle: PaletteStyle, titleLanguage: TitleLanguage,
    nsfwEnabled: Boolean, onNsfwChange: (Boolean) -> Unit,
    onThemeClick: () -> Unit, onColorClick: () -> Unit, onPaletteClick: () -> Unit, onTitleLanguageClick: () -> Unit,
    updateInfo: AppUpdateInfo?, onAboutClick: () -> Unit, onSignOut: () -> Unit, onBack: () -> Unit,
) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.padding(top = 12.dp, bottom = 24.dp)) {
            SettingsSection(
                connected = connected, themeMode = themeMode, colorSource = colorSource, paletteStyle = paletteStyle, titleLanguage = titleLanguage,
                nsfwEnabled = nsfwEnabled, onNsfwChange = onNsfwChange,
                onThemeClick = onThemeClick, onColorClick = onColorClick, onPaletteClick = onPaletteClick, onTitleLanguageClick = onTitleLanguageClick,
                updateInfo = updateInfo, onAboutClick = onAboutClick, onSignOut = onSignOut,
            )
        }
    }
}

// Profile header card + full anime/manga stats (used inside the profile drawer's
// expandable "avatar + name" row). Ends with the score distribution chart.
@Composable fun ProfileStatsSection(connected: Boolean, profile: MalProfile?, items: List<MediaItem>, onConnect: () -> Unit, onScoreClick: (MediaType, Int) -> Unit = { _, _ -> }) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    Column {
        // Profile header with stats
        if (connected && profile != null) {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (profile.picture.isNotBlank()) {
                            AsyncImage(model = profile.picture, contentDescription = profile.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(64.dp).clip(CircleShape).background(c.warm))
                        } else {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(c.warm), contentAlignment = Alignment.Center) {
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
                            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/profile/${profile.name}")) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surfaceLow)) {
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
        val animeItems = items.filter { it.type == MediaType.Anime }
        val mangaItems = items.filter { it.type == MediaType.Manga }
        val mangaTotal = mangaItems.size
        val mangaChaptersRead = mangaItems.sumOf { it.progress }
        val ratedManga = mangaItems.filter { it.myRating > 0 }
        val mangaMeanScore = if (ratedManga.isNotEmpty()) ratedManga.map { it.myRating }.average() else 0.0
        val animeDaysWatched = profile?.animeDaysWatched ?: 0.0
        // MAL: 8 min/chapter
        val mangaDaysReadEst = mangaChaptersRead * 8.0 / 60.0 / 24.0
        if (connected && ((profile?.animeTotalEntries ?: 0) > 0 || mangaItems.isNotEmpty())) {
            if (animeDaysWatched > 0 || mangaDaysReadEst > 0) {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("TIME WATCHED VS READ", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeroStat(Modifier.weight(1f), Icons.Default.PlayCircle, "Days watched", "%.1f".format(animeDaysWatched), c.lavender, c.primary)
                            HeroStat(Modifier.weight(1f), Icons.Default.MenuBook, "Days read (est.)", "%.1f".format(mangaDaysReadEst), c.primaryContainer, c.primary)
                        }
                    }
                }
            }
            var statsTab by remember { mutableStateOf(MediaType.Anime) }
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Text("STATS", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                    TypeToggle(statsTab, trackColor = c.surfaceLow) { statsTab = it }
                    Spacer(Modifier.height(18.dp))
                    if (statsTab == MediaType.Anime) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabeledStat("Days:", "%.1f".format(animeDaysWatched), c)
                            LabeledStat("Mean Score:", (profile?.animeMeanScore ?: 0.0).let { if (it > 0) "%.2f".format(it) else "—" }, c)
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
                            GenreBreakdownChart(animeItems, c)
                            Spacer(Modifier.height(24.dp))
                            Text("SCORE DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                            ScoreDistributionChart(animeItems, c, onScoreClick = { onScoreClick(MediaType.Anime, it) })
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabeledStat("Days:", "%.1f".format(mangaDaysReadEst) + " (est.)", c)
                            LabeledStat("Mean Score:", if (mangaMeanScore > 0) "%.2f".format(mangaMeanScore) else "—", c)
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
                            GenreBreakdownChart(mangaItems, c)
                            Spacer(Modifier.height(24.dp))
                            Text("SCORE DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                            ScoreDistributionChart(mangaItems, c, onScoreClick = { onScoreClick(MediaType.Manga, it) })
                        }
                    }
                }
            }
        }

        // Only shown when signed-out
        if (!connected) {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = c.lavender), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Text("Connect MyAnimeList", style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    Text("Sign in with your MyAnimeList account to bring in your real list.", color = c.muted, modifier = Modifier.padding(top = 8.dp, bottom = 15.dp))
                    Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) { Text("Sign in with MyAnimeList") }
                }
            }
        }
    }
}

// Settings list — theme, color, palette, title language, adult content, about,
// and (when signed in) sign out. Lives in the profile drawer's "Settings" row.
@Composable fun SettingsSection(
    connected: Boolean, themeMode: ThemeMode, colorSource: ColorSource, paletteStyle: PaletteStyle, titleLanguage: TitleLanguage,
    nsfwEnabled: Boolean, onNsfwChange: (Boolean) -> Unit,
    onThemeClick: () -> Unit, onColorClick: () -> Unit, onPaletteClick: () -> Unit, onTitleLanguageClick: () -> Unit,
    updateInfo: AppUpdateInfo? = null, onAboutClick: () -> Unit = {}, onSignOut: () -> Unit = {},
) {
    val c = LocalKikoColors.current
    // Confirm before signing out
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = c.surface,
            title = { Text("Sign out?", color = c.ink) },
            text = { Text("Are you sure you want to sign out of your MyAnimeList account?", color = c.muted) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Cancel") } },
        )
    }
    Column {
        ListItem(headlineContent = { Text("Theme", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(themeMode.label, color = c.muted) }, leadingContent = { Icon(Icons.Default.Palette, null, tint = c.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onThemeClick))
        ListItem(headlineContent = { Text("Color", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(colorSource.label, color = c.muted) }, leadingContent = { Icon(Icons.Default.ColorLens, null, tint = c.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onColorClick))
        ListItem(headlineContent = { Text("Color palette", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(paletteStyle.label, color = c.muted) }, leadingContent = { Icon(Icons.Default.Gradient, null, tint = c.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onPaletteClick))
        ListItem(headlineContent = { Text("Title language", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(titleLanguage.label, color = c.muted) }, leadingContent = { Icon(Icons.Default.Translate, null, tint = c.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onTitleLanguageClick))
        ListItem(headlineContent = { Text("Adult content", fontWeight = FontWeight.Bold, color = c.ink) }, supportingContent = { Text(if (nsfwEnabled) "Hentai-rated titles are shown" else "Hentai-rated titles are hidden", color = c.muted) }, leadingContent = { Icon(Icons.Default.VisibilityOff, null, tint = c.primary) }, trailingContent = { Switch(checked = nsfwEnabled, onCheckedChange = onNsfwChange, colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        // Tap opens about page
        ListItem(
            headlineContent = { Text("About", fontWeight = FontWeight.Bold, color = c.ink) },
            supportingContent = { Text(if (updateInfo != null) "Update available — ${updateInfo.version}" else "v${BuildConfig.VERSION_NAME}", color = if (updateInfo != null) c.primary else c.muted, fontWeight = if (updateInfo != null) FontWeight.Bold else FontWeight.Normal) },
            leadingContent = {
                Box {
                    Icon(Icons.Default.Info, null, tint = c.primary)
                    if (updateInfo != null) Box(Modifier.size(8.dp).align(Alignment.TopEnd).clip(CircleShape).background(c.danger))
                }
            },
            trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = c.muted) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onAboutClick),
        )
        if (connected) {
            ListItem(
                headlineContent = { Text("Sign out", fontWeight = FontWeight.Bold, color = c.danger) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = c.danger) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { confirmSignOut = true },
            )
        }
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
    val filtered = remember(typeItems, score) {
        typeItems.filter { it.myRating > 0 && (score == 0 || it.myRating == score) }.sortedWith(compareByDescending<MediaItem> { it.myRating }.thenBy { it.title })
    }
    val isGrid = vm.scoreFilterViewMode == ListViewMode.Grid
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Score Distribution", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        ScoreFilterRow(score) { score = it }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            ListViewModeToggle(vm.scoreFilterViewMode) { vm.setScoreFilterViewMode(context, it) }
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
            items(filtered, key = { it.id }) { item -> ListGridCard(item, onOpenDetail) }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles at this score yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item { header() }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                ListRow(it, onOpenDetail, showType = false)
                if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.muted.copy(alpha = .15f))
            }
            if (filtered.isEmpty()) item { Text("No titles at this score yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    }
}
// Score chip row: "All" plus 10 down to 1, same chip styling as the status FilterRow

@Composable fun ScoreFilterRow(current: Int, set: (Int) -> Unit) {
    val c = LocalKikoColors.current
    val colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
        item { FilterChip(selected = current == 0, onClick = { set(0) }, label = { Text("All") }, colors = colors) }
        items((10 downTo 1).toList()) { s ->
            FilterChip(
                selected = current == s,
                onClick = { set(s) },
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
// App info page