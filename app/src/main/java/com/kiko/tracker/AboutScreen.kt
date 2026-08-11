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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size as UiSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable fun AboutScreen(
    onBack: () -> Unit,
    updateInfo: AppUpdateInfo?, updateChecking: Boolean, updateUpToDate: Boolean, onCheckForUpdate: () -> Unit,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    // Adaptive icon as bitmap
    val appIcon = remember(context) {
        runCatching { context.packageManager.getApplicationIcon(context.packageName) }
            .getOrNull()?.toBitmap(168, 168)?.asImageBitmap()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(13.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("About", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (appIcon != null) {
                Image(bitmap = appIcon, contentDescription = "Kiko", modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)))
            } else {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(c.primaryContainer))
            }
            Text("Kiko", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 14.dp))
            Text("Version ${BuildConfig.VERSION_NAME}", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(28.dp))
        ListItem(
            headlineContent = { Text("Check for updates", fontWeight = FontWeight.Bold, color = c.ink) },
            supportingContent = {
                Text(
                    when {
                        updateChecking -> "Checking…"
                        updateInfo != null -> "Update available — ${updateInfo.version}"
                        updateUpToDate -> "You're up to date"
                        else -> "Tap to check"
                    },
                    color = if (updateInfo != null) c.primary else c.muted,
                    fontWeight = if (updateInfo != null) FontWeight.Bold else FontWeight.Normal,
                )
            },
            leadingContent = {
                Box {
                    Icon(Icons.Default.SystemUpdate, null, tint = c.primary)
                    if (updateInfo != null) Box(Modifier.size(8.dp).align(Alignment.TopEnd).clip(CircleShape).background(c.danger))
                }
            },
            trailingContent = { if (updateChecking) CircularProgressIndicator(Modifier.size(18.dp), color = c.primary, strokeWidth = 2.dp) else Icon(Icons.Default.ChevronRight, null, tint = c.muted) },
            colors = ListItemDefaults.colors(containerColor = c.surface),
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = !updateChecking, onClick = onCheckForUpdate),
        )
        Spacer(Modifier.height(28.dp))
        // Community links row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://github.com/SyHaqi/kiko")) }, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(16.dp))) {
                Icon(painterResource(R.drawable.ic_github), "GitHub", tint = c.ink)
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://discord.gg/KZYQHpDWKH")) }, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(16.dp))) {
                Icon(painterResource(R.drawable.ic_discord), "Discord", tint = c.ink)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
// One headline stat number

@Composable fun HeroStat(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String, container: Color, content: Color) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(container).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = content)
        Text(label, color = content.copy(alpha = .75f), fontSize = 11.sp)
    }
}
// Proportional status breakdown bar

@Composable fun StatBar(label: String, value: Int, total: Int, c: KikoColors, barColor: Color = c.primary) {
    val fraction = if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(value.toString(), color = barColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(c.surfaceLow)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(barColor))
        }
    }
}
// MAL-style stats card layout

@Composable fun LabeledStat(label: String, value: String, c: KikoColors) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$label ", color = c.muted, fontSize = 13.sp)
        Text(value, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable fun SegmentedStatBar(segments: List<Pair<Int, Color>>, c: KikoColors) {
    val total = segments.sumOf { it.first }
    Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)).background(c.surfaceLow)) {
        if (total > 0) {
            Row(Modifier.fillMaxSize()) {
                segments.forEach { (value, color) -> if (value > 0) Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color)) }
            }
        }
    }
}

@Composable fun StatusLegendRow(label: String, value: Int, color: Color, c: KikoColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(9.dp))
        Text(label, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable fun SummaryRow(label: String, value: String, c: KikoColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.muted, fontSize = 13.sp)
        Text(value, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
// Top genres proportional bars — each genre gets its own swatch from the fixed
// ChartPalette below instead of one flat color repeated down the list.

@Composable fun GenreBreakdownChart(items: List<MediaItem>, c: KikoColors) {
    val total = items.size
    // Skip junk genre tags
    val counts = items.flatMap { it.genres }.filter { it.isNotBlank() && it.trim().split(" ").size <= 3 && it.length <= 24 }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
    if (counts.isEmpty()) { Text("Not enough data yet.", color = c.muted, fontSize = 12.sp); return }
    Column(Modifier.fillMaxWidth()) { counts.forEachIndexed { index, (genre, count) -> StatBar(genre, count, total, c, chartColor(c, index)) } }
}
// Categorical colors for stat charts (genre bars, format ring/legend) — a fixed,
// hardcoded set of solid swatches, same spirit as the Status colors below (flat,
// saturated, theme-independent) rather than something spun off the user's chosen
// accent color. Kept deliberately distinct from the Status palette (green/navy/
// gold/maroon/gray) so genre and format breakdowns never visually collide with the
// status legend elsewhere on the same screen. Cycles by index for any list length.

val ChartPalette = listOf(
    Color(0xFFFF6F59), // coral
    Color(0xFF6C63FF), // indigo
    Color(0xFF2EC4B6), // teal
    Color(0xFFFF9F1C), // orange
    Color(0xFFE84393), // magenta
    Color(0xFF00B4D8), // sky blue
    Color(0xFF9B5DE5), // purple
    Color(0xFF4A4E69), // slate
)

fun chartColor(c: KikoColors, index: Int): Color = ChartPalette[index % ChartPalette.size]
// Format breakdown — a donut ring (TV/OVA/Movie for anime, Manga/Manhua/Light Novel for manga)
// paired with a ranked legend, each format's own hue from chartColor so every wedge
// reads as distinct at a glance instead of one accent bleeding into its own shades.

@Composable fun FormatBreakdownChart(items: List<MediaItem>, c: KikoColors) {
    val counts = items.map { it.format }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    if (counts.isEmpty()) { Text("Not enough data yet.", color = c.muted, fontSize = 12.sp); return }
    val total = counts.sumOf { it.value }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FormatRing(counts, total, c, modifier = Modifier.size(92.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            counts.forEachIndexed { index, entry -> FormatLegendRow(entry.key, entry.value, total, chartColor(c, index), c) }
        }
    }
}
// Donut ring with rounded, gapped segments and a centered total

@Composable fun FormatRing(counts: List<Map.Entry<String, Int>>, total: Int, c: KikoColors, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.17f
            val gapDegrees = if (counts.size > 1) 6f else 0f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = UiSize(diameter, diameter)
            var startAngle = -90f
            counts.forEachIndexed { index, entry ->
                val sweep = (entry.value.toFloat() / total) * (360f - gapDegrees * counts.size)
                drawArc(
                    color = chartColor(c, index),
                    startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                startAngle += sweep + gapDegrees
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(total.toString(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.ink)
            Text("titles", color = c.muted, fontSize = 10.sp)
        }
    }
}
// One ranked row in the format legend: dot, name, raw count, percentage pill — the
// pill's text picks up the segment's own color so it visually ties back to its wedge

@Composable fun FormatLegendRow(label: String, count: Int, total: Int, color: Color, c: KikoColors) {
    val pct = if (total > 0) (count * 100f / total).roundToInt() else 0
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(9.dp))
        Text(label, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = c.muted, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(RoundedCornerShape(50)).background(c.surfaceLow).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("$pct%", color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}
// Score distribution — bars sweep through a fixed, hardcoded gradient (coral at the
// low end to teal at the high end, the same two hues that anchor ChartPalette above)
// instead of tracking the theme's own accent color. Fixed hue/saturation/lightness
// stops keep the ordering readable (neighbors look similar, extremes look most
// different) while staying independent of whatever seed color the user picked.

private const val ScoreGradientStartHue = 8f   // coral — matches ChartPalette
private const val ScoreGradientEndHue = 174f   // teal — matches ChartPalette
private const val ScoreGradientSaturation = 0.62f
private const val ScoreGradientLightness = 0.52f

fun scoreBarColor(c: KikoColors, score: Int): Color {
    val t = (score - 1) / 9f
    val hue = ScoreGradientStartHue + t * (ScoreGradientEndHue - ScoreGradientStartHue)
    return hslColor(hue, ScoreGradientSaturation, ScoreGradientLightness)
}

@Composable fun ScoreDistributionChart(items: List<MediaItem>, c: KikoColors, onScoreClick: ((Int) -> Unit)? = null) {
    val counts = (1..10).associateWith { s -> items.count { it.myRating == s } }
    if (counts.values.all { it == 0 }) { Text("No scored titles yet.", color = c.muted, fontSize = 12.sp); return }
    val maxCount = counts.values.max()
    val barSlotHeight = 56.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        (1..10).forEach { score ->
            val count = counts.getValue(score)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp).let { m -> if (onScoreClick != null) m.clickable { onScoreClick(score) } else m },
            ) {
                Text(if (count > 0) count.toString() else "", color = c.muted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().height(barSlotHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height((count.toFloat() / maxCount * barSlotHeight.value).dp.coerceAtLeast(if (count > 0) 4.dp else 1.dp))
                            .clip(RoundedCornerShape(4.dp)).background(if (count > 0) scoreBarColor(c, score) else c.surfaceLow)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(score.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}
// Year distribution — how many of the user's titles were originally released in each
// year, laid out the same way as ScoreDistributionChart above (count label, bar, axis
// label) but in a LazyRow instead of a fixed Row: a title list can span decades, and
// unlike scores (a fixed 1-10 set) the year axis has no natural cap, so it scrolls
// horizontally instead of squeezing. Every year between the earliest and latest release
// gets a column — including zero-count years — so the shape of the timeline (and any
// gaps in it) reads correctly rather than only showing years that happen to have data.
// Bars use a single fixed, hardcoded color independent of the theme's accent, distinct
// from both the coral-to-teal score gradient and the categorical ChartPalette wedges.

val YearBarColor = Color(0xFF6C56D9) // fixed violet, unrelated to the user's chosen accent

@Composable fun YearDistributionChart(items: List<MediaItem>, c: KikoColors, onYearClick: ((Int) -> Unit)? = null) {
    // "Compatible with year": tolerate any startDate that begins with a plausible
    // 4-digit year (extra trailing text, non-numeric junk, blanks) rather than crashing
    // or silently dropping the item from the count.
    val counts = items.mapNotNull { it.startDate.take(4).toIntOrNull() }
        .filter { it in 1900..2100 }
        .groupingBy { it }.eachCount()
    if (counts.isEmpty()) { Text("Not enough data yet.", color = c.muted, fontSize = 12.sp); return }
    val years = (counts.keys.min()..counts.keys.max()).toList()
    val maxCount = counts.values.max()
    val barSlotHeight = 56.dp
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(years, key = { it }) { year ->
            val count = counts[year] ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(30.dp).let { m -> if (onYearClick != null && count > 0) m.clickable { onYearClick(year) } else m },
            ) {
                Text(if (count > 0) count.toString() else "", color = c.muted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().height(barSlotHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height((count.toFloat() / maxCount * barSlotHeight.value).dp.coerceAtLeast(if (count > 0) 4.dp else 1.dp))
                            .clip(RoundedCornerShape(4.dp)).background(if (count > 0) YearBarColor else c.surfaceLow)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(year.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
        }
    }
}
// Fixed non-theme status colors

val StatusWatchingColor = Color(0xFF2DB039)

val StatusCompletedColor = Color(0xFF26448F)

val StatusOnHoldColor = Color(0xFFE7B715)

val StatusDroppedColor = Color(0xFFA12F31)

val StatusPlanColor = Color(0xFF8F8F8F)