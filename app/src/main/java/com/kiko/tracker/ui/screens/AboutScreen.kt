@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size as UiSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.kiko.tracker.BuildConfig
import com.kiko.tracker.R
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.ScoreStats
import com.kiko.tracker.ui.theme.KikoColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.hslColor
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.util.AppUpdateInfo

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
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("About", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (appIcon != null) {
                Image(bitmap = appIcon, contentDescription = "Kiko", modifier = Modifier.size(88.dp).clip(RoundedCornerShape(kikoCorner(24.dp))))
            } else {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(kikoCorner(24.dp))).background(c.primaryContainer))
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
                    if (updateInfo != null) Box(Modifier.size(8.dp).align(Alignment.TopEnd).clip(kikoCircleShape()).background(c.danger))
                }
            },
            trailingContent = { if (updateChecking) CircularProgressIndicator(Modifier.size(18.dp), color = c.primary, strokeWidth = 2.dp) else Icon(Icons.Default.ChevronRight, null, tint = c.muted) },
            colors = ListItemDefaults.colors(containerColor = c.surfaceContainer),
            modifier = Modifier.clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(enabled = !updateChecking, onClick = onCheckForUpdate),
        )
        Spacer(Modifier.height(28.dp))
        // Community links row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://github.com/SyHaqi/kiko")) }, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh)) {
                Icon(painterResource(R.drawable.ic_github), "GitHub", tint = c.ink)
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://discord.gg/KZYQHpDWKH")) }, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceContainerHigh)) {
                Icon(painterResource(R.drawable.ic_discord), "Discord", tint = c.ink)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
// One headline stat number

@Composable fun HeroStat(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String, container: Color, content: Color) {
    Column(modifier.clip(RoundedCornerShape(kikoCorner(18.dp))).background(container).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = content)
        Text(label, color = content.copy(alpha = .75f), fontSize = 11.sp)
    }
}
// Proportional status breakdown bar

@Composable fun StatBar(label: String, value: Int, total: Int, c: KikoColors, barColor: Color = c.primary, onClick: (() -> Unit)? = null) {
    val fraction = if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }.padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(value.toString(), color = barColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(kikoPillShape()).background(c.surfaceLow)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(kikoPillShape()).background(barColor))
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
    Box(Modifier.fillMaxWidth().height(9.dp).clip(kikoPillShape()).background(c.surfaceLow)) {
        if (total > 0) {
            Row(Modifier.fillMaxSize()) {
                segments.forEach { (value, color) -> if (value > 0) Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color)) }
            }
        }
    }
}

@Composable fun StatusLegendRow(label: String, value: Int, color: Color, c: KikoColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(kikoCircleShape()).background(color))
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
// Top genres proportional bars
// ChartPalette below instead of

@Composable fun GenreBreakdownChart(items: List<MediaItem>, c: KikoColors, onGenreClick: ((String) -> Unit)? = null) {
    val total = items.size
    // Skip junk genre tags
    val counts = items.flatMap { it.genres }.filter { it.isNotBlank() && it.trim().split(" ").size <= 3 && it.length <= 24 }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
    if (counts.isEmpty()) { Text("Not enough data yet.", color = c.muted, fontSize = 12.sp); return }
    Column(Modifier.fillMaxWidth()) { counts.forEachIndexed { index, (genre, count) -> StatBar(genre, count, total, c, chartColor(c, index), onClick = onGenreClick?.let { { it(genre) } }) } }
}
// Categorical colors for stat
// hardcoded set of solid
// saturated, theme-independent) rather than
// accent color. Kept deliberately
// gold/maroon/gray) so genre and
// status legend elsewhere on

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
// Format breakdown — a
// paired with a ranked
// reads as distinct at

@Composable fun FormatBreakdownChart(items: List<MediaItem>, c: KikoColors, onFormatClick: ((String) -> Unit)? = null) {
    val counts = items.map { it.format }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    if (counts.isEmpty()) { Text("Not enough data yet.", color = c.muted, fontSize = 12.sp); return }
    val total = counts.sumOf { it.value }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FormatRing(counts, total, c, modifier = Modifier.size(92.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            counts.forEachIndexed { index, entry -> FormatLegendRow(entry.key, entry.value, total, chartColor(c, index), c, onClick = onFormatClick?.let { { it(entry.key) } }) }
        }
    }
}
// Donut ring with rounded,

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
// One ranked row in
// pill's text picks up

@Composable fun FormatLegendRow(label: String, count: Int, total: Int, color: Color, c: KikoColors, onClick: (() -> Unit)? = null) {
    val pct = if (total > 0) (count * 100f / total).roundToInt() else 0
    Row(
        Modifier.fillMaxWidth().let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(kikoCircleShape()).background(color))
        Spacer(Modifier.width(9.dp))
        Text(label, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = c.muted, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(kikoPillShape()).background(c.surfaceLow).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("$pct%", color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

// Score distribution — bars
// low end to teal
// instead of tracking the
// stops keep the ordering
// different) while staying independent

private const val ScoreGradientStartHue = 8f   // coral — matches ChartPalette
private const val ScoreGradientEndHue = 174f   // teal — matches ChartPalette
private const val ScoreGradientSaturation = 0.62f
private const val ScoreGradientLightness = 0.52f

fun scoreBarColor(c: KikoColors, score: Int): Color {
    val t = (score - 1) / 9f
    val hue = ScoreGradientStartHue + t * (ScoreGradientEndHue - ScoreGradientStartHue)
    return hslColor(hue, ScoreGradientSaturation, ScoreGradientLightness)
}

// Shared bar renderer behind
// on the profile page)
// votes, on the Score
//
// Bar height was a
// on a real list
// 2s, 3s, and often
// reads as "one tall
// taller (96dp vs the
// the fraction is sqrt-compressed
// max count still reads
// of both bottoming out
private val ScoreBarSlotHeight = 96.dp

@Composable private fun ScoreBarsCore(counts: Map<Int, Int>, c: KikoColors, barColorFor: (Int) -> Color, onScoreClick: ((Int) -> Unit)? = null) {
    val maxCount = counts.values.max()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        (1..10).forEach { score ->
            val count = counts.getValue(score)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp).let { m -> if (onScoreClick != null) m.clickable { onScoreClick(score) } else m },
            ) {
                Text(if (count > 0) count.toString() else "", color = c.muted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().height(ScoreBarSlotHeight), contentAlignment = Alignment.BottomCenter) {
                    val fraction = if (maxCount > 0 && count > 0) sqrt(count.toFloat() / maxCount) else 0f
                    Box(
                        Modifier.fillMaxWidth().height((fraction * ScoreBarSlotHeight.value).dp.coerceAtLeast(if (count > 0) 4.dp else 1.dp))
                            .clip(RoundedCornerShape(kikoCorner(4.dp))).background(if (count > 0) barColorFor(score) else c.surfaceLow)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(score.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}

@Composable fun ScoreDistributionChart(items: List<MediaItem>, c: KikoColors, onScoreClick: ((Int) -> Unit)? = null) {
    val counts = (1..10).associateWith { s -> items.count { it.myRating == s } }
    if (counts.values.all { it == 0 }) { Text("No scored titles yet.", color = c.muted, fontSize = 12.sp); return }
    ScoreBarsCore(counts, c, barColorFor = { scoreBarColor(c, it) }, onScoreClick = onScoreClick)
}

// A title's community score
// fed vote counts scraped
// own ratings. Not click-to-filter
@Composable fun CommunityScoreDistributionChart(stats: ScoreStats, c: KikoColors) {
    val counts = (1..10).associateWith { s -> stats.counts[s] ?: 0 }
    if (counts.values.all { it == 0 }) { Text("No score data yet.", color = c.muted, fontSize = 12.sp); return }
    ScoreBarsCore(counts, c, barColorFor = { scoreBarColor(c, it) })
}
// Year distribution — how
// year, laid out the
// label) but in a
// unlike scores (a fixed
// horizontally instead of squeezing.
// gets a column —
// gaps in it) reads
// Bars sweep through a
// magenta at the most
// timeline reads as a
// different hue family from
// Same slot height as
// the differences in bar

private const val YearGradientStartHue = 235f  // indigo — earliest year
private const val YearGradientEndHue = 320f    // magenta — most recent
private const val YearGradientSaturation = 0.58f
private const val YearGradientLightness = 0.54f

fun yearBarColor(t: Float): Color {
    val hue = YearGradientStartHue + t * (YearGradientEndHue - YearGradientStartHue)
    return hslColor(hue, YearGradientSaturation, YearGradientLightness)
}

@Composable fun YearDistributionChart(items: List<MediaItem>, c: KikoColors, onYearClick: ((Int) -> Unit)? = null) {
    // "Compatible with year": tolerate
    // 4-digit year (extra trailing
    // or silently dropping the
    val counts = items.mapNotNull { it.startDate.take(4).toIntOrNull() }
        .filter { it in 1900..2100 }
        .groupingBy { it }.eachCount()
    if (counts.isEmpty()) { Text("Not enough data yet.", color = c.muted, fontSize = 12.sp); return }
    // Most recent year first
    // order (sortedDescending()) so the
    val years = (counts.keys.max() downTo counts.keys.min()).toList()
    val minYear = years.min()
    val maxYear = years.max()
    val maxCount = counts.values.max()
    val barSlotHeight = ScoreBarSlotHeight
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(years, key = { it }) { year ->
            val count = counts[year] ?: 0
            // Gradient position is tied
            // magenta), independent of the
            // above doesn't flip which
            val t = if (maxYear > minYear) (year - minYear).toFloat() / (maxYear - minYear) else 0f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(30.dp).let { m -> if (onYearClick != null && count > 0) m.clickable { onYearClick(year) } else m },
            ) {
                Text(if (count > 0) count.toString() else "", color = c.muted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().height(barSlotHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height((count.toFloat() / maxCount * barSlotHeight.value).dp.coerceAtLeast(if (count > 0) 4.dp else 1.dp))
                            .clip(RoundedCornerShape(kikoCorner(4.dp))).background(if (count > 0) yearBarColor(t) else c.surfaceLow)
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