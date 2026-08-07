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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
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

// Palette section
@Immutable
data class KikoColors(
    val ink: Color, val onPrimary: Color, val primary: Color, val primaryContainer: Color,
    val background: Color, val surface: Color, val surfaceLow: Color, val muted: Color,
    val lavender: Color, val warm: Color, val danger: Color
)
// MAL brand palette colors
private val LightKiko = KikoColors(
    ink = Color(0xFF1B1B1F), onPrimary = Color.White, primary = Color(0xFF2E51A2), primaryContainer = Color(0xFFE1E7F5),
    background = Color(0xFFFFFFFF), surface = Color(0xFFF8F8F8), surfaceLow = Color(0xFFEDEDED), muted = Color(0xFF6D6D6D),
    lavender = Color(0xFFEAF0FF), warm = Color(0xFFFFE9C7), danger = Color(0xFFB3261E)
)
private val DarkKiko = KikoColors(
    ink = Color(0xFFEDEDED), onPrimary = Color(0xFF14203D), primary = Color(0xFFABC4ED), primaryContainer = Color(0xFF24365E),
    background = Color(0xFF121212), surface = Color(0xFF181818), surfaceLow = Color(0xFF222222), muted = Color(0xFFA3A3A3),
    lavender = Color(0xFF1F2A44), warm = Color(0xFF463A28), danger = Color(0xFFFFB4AB)
)
private val LocalKikoColors = staticCompositionLocalOf { LightKiko }
private val AppFont = FontFamily.SansSerif

// Generate theme from seed
private val AppDefaultSeed = Color(0xFF2E51A2)
private fun normHue(h: Float) = ((h % 360f) + 360f) % 360f
private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(normHue(hue), saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))
private fun seedHue(seed: Color): Float {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(
        (seed.red * 255f).roundToInt().coerceIn(0, 255),
        (seed.green * 255f).roundToInt().coerceIn(0, 255),
        (seed.blue * 255f).roundToInt().coerceIn(0, 255),
        hsl,
    )
    return hsl[0]
}
private fun themedPalette(seed: Color, style: PaletteStyle, dark: Boolean): KikoColors {
    val hue = seedHue(seed)
    // Saturation bands per style
    val (accentSat, containerSat, neutralSat) = when (style) {
        PaletteStyle.TonalSpot -> Triple(0.52f, 0.35f, 0.06f)
        PaletteStyle.Neutral -> Triple(0.18f, 0.10f, 0.02f)
        PaletteStyle.Monochrome -> Triple(0f, 0f, 0f)
    }
    return if (!dark) KikoColors(
        ink = hslColor(hue, neutralSat, 0.12f),
        onPrimary = Color.White,
        primary = hslColor(hue, accentSat, 0.46f),
        primaryContainer = hslColor(hue, containerSat, 0.88f),
        background = hslColor(hue, neutralSat, 0.975f),
        surface = hslColor(hue, neutralSat * 0.6f, 0.995f),
        surfaceLow = hslColor(hue, neutralSat, 0.95f),
        muted = hslColor(hue, neutralSat, 0.45f),
        lavender = hslColor(hue + 40f, containerSat, 0.93f),
        warm = hslColor(hue - 150f, containerSat, 0.87f),
        danger = Color(0xFFB3261E),
    ) else KikoColors(
        ink = hslColor(hue, neutralSat, 0.94f),
        onPrimary = hslColor(hue, neutralSat, 0.10f),
        primary = hslColor(hue, accentSat, 0.74f),
        primaryContainer = hslColor(hue, containerSat, 0.30f),
        background = hslColor(hue, neutralSat, 0.08f),
        surface = hslColor(hue, neutralSat, 0.13f),
        surfaceLow = hslColor(hue, neutralSat, 0.17f),
        muted = hslColor(hue, neutralSat, 0.68f),
        lavender = hslColor(hue + 40f, containerSat, 0.18f),
        warm = hslColor(hue - 150f, containerSat, 0.21f),
        danger = Color(0xFFFFB4AB),
    )
}
// Resolve palette seed color
private fun resolveSeedColor(context: Context, source: ColorSource, customHex: String, dark: Boolean): Color = when (source) {
    ColorSource.AppDefault -> AppDefaultSeed
    ColorSource.Dynamic -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else AppDefaultSeed
    ColorSource.Custom -> parseHexColor(customHex) ?: AppDefaultSeed
}
private fun parseHexColor(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 || cleaned.any { it !in "0123456789abcdefABCDEF" }) return null
    return try { Color(0xFF000000 or cleaned.toLong(16)) } catch (e: Exception) { null }
}

// Romaji or English titles
enum class TitleLanguage(val label: String) { Romaji("Romaji"), English("English") }
private val LocalTitleLanguage = staticCompositionLocalOf { TitleLanguage.Romaji }
// Preferred title to show
@Composable
fun MediaItem.displayTitle(): String {
    val pref = LocalTitleLanguage.current
    return if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) titleEnglish else title
}
// Alternate title subtitle
@Composable
fun MediaItem.secondaryTitle(): String {
    val pref = LocalTitleLanguage.current
    val other = if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) title else titleEnglish
    return other.takeIf { it.isNotBlank() && it != displayTitle() } ?: ""
}

// Immutable MediaItem annotation
@Immutable
data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String, val type: MediaType, val status: WatchStatus,
    val progress: Int = 0, val total: Int = 0,
    // User's own tracking info
    val myRating: Int = 0, val watchStartDate: String = "", val watchEndDate: String = "",
    // Rewatch tracking fields
    val isRewatching: Boolean = false, val timesRewatched: Int = 0,
    val genre: String = "", val genres: List<String> = emptyList(),
    // Theme and demographic tags
    val contentThemes: List<String> = emptyList(), val demographics: List<String> = emptyList(),
    val cover: String = "", val color: Long = 0xFFB7C3F5,
    // All cover images
    val covers: List<String> = emptyList(),
    val synopsis: String = "", val background: String = "",
    val score: Double = 0.0, val rank: Int = 0, val popularity: Int = 0, val listUsers: Int = 0,
    val creator: String = "", val startDate: String = "", val season: String = "",
    val format: String = "", val airStatus: String = "", val source: String = "", val rating: String = "",
    val volumes: Int = 0, val titleEnglish: String = "",
    // Extra detail metadata
    val startDateFull: String = "", val endDateFull: String = "",
    val synonyms: List<String> = emptyList(),
    val openingThemes: List<String> = emptyList(), val endingThemes: List<String> = emptyList(),
    val related: List<RelatedEntry> = emptyList(),
    // Recommendations from MAL
    val recommended: List<RecommendedEntry> = emptyList(),
    // Status distribution stats
    val statusDistribution: StatusDistribution = StatusDistribution(),
    // Last touched timestamp
    val updatedAt: String = "",
    // Broadcast day of week
    val broadcastDay: String = "",
    // Broadcast time JST
    val broadcastTime: String = "",
    // MAL content rating
    val nsfw: String = "white",
    // Is title tracked?
    val inUserList: Boolean = true,
)
// Is title NSFW?
private fun MediaItem.isAdultContent() = genres.any { it.equals("Hentai", ignoreCase = true) }
private fun List<MediaItem>.nsfwFiltered(allowAdult: Boolean) = if (allowAdult) this else filterNot { it.isAdultContent() }
data class RelatedEntry(val relation: String, val title: String, val malId: Int = 0, val malType: String = "anime", val cover: String = "")
// Characters/staff row entries
data class CharacterEntry(val malId: Int, val name: String, val image: String, val role: String, val url: String = "")
data class StaffEntry(val malId: Int, val name: String, val image: String, val role: String, val url: String = "")
// Reviews row entry
data class ReviewEntry(val malId: Int, val username: String, val userImage: String, val review: String, val score: Int, val tags: List<String> = emptyList(), val reactionScore: Int = 0, val isSpoiler: Boolean = false, val url: String = "")
// MAL's three recommendation verdicts
private val ReviewVerdictTags = setOf("Recommended", "Mixed Feelings", "Not Recommended")
private fun ReviewEntry.verdict(): String? = tags.firstOrNull { it in ReviewVerdictTags }
private fun verdictColor(verdict: String, c: KikoColors): Color = when (verdict) {
    "Recommended" -> c.primary
    "Not Recommended" -> c.danger
    else -> c.muted
}
// Status breakdown counts
data class StatusDistribution(
    val watching: Int = 0, val completed: Int = 0, val onHold: Int = 0, val dropped: Int = 0, val planToWatch: Int = 0,
) {
    val total: Int get() = watching + completed + onHold + dropped + planToWatch
}
enum class MediaType { Anime, Manga }
enum class WatchStatus(val label: String) { Watching("Watching"), Reading("Reading"), Plan("Plan to Watch"), Completed("Completed"), OnHold("On Hold"), Dropped("Dropped") }
enum class Destination(val label: String, val icon: ImageVector) { Home("Home", Icons.Default.Home), List("My list", Icons.Default.List), Discover("Discover", Icons.Default.Search), Forums("Forums", Icons.Default.Forum), Profile("Profile", Icons.Default.Person) }
enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }
enum class ColorSource(val label: String) { AppDefault("App default"), Dynamic("Dynamic"), Custom("Custom") }
enum class PaletteStyle(val label: String) { TonalSpot("Tonal Spot"), Neutral("Neutral"), Monochrome("Monochrome") }

// ViewModel section
enum class DiscoverMode { Browse, Results }
// Discover advanced filters
data class DiscoverFilters(
    val genres: Set<String> = emptySet(),
    val themes: Set<String> = emptySet(),
    val demographics: Set<String> = emptySet(),
    val studio: String = "",
    val source: String = "",
    val year: String = "",
    val season: SeasonName? = null,
    val rating: String = "",
    // Sub-type format field
    val format: String = "",
    // Finished, Ongoing, Upcoming
    val airingStatus: String = "",
) {
    fun isActive() = genres.isNotEmpty() || themes.isNotEmpty() || demographics.isNotEmpty() || studio.isNotBlank() || source.isNotBlank() || year.isNotBlank() || season != null || rating.isNotBlank() || format.isNotBlank() || airingStatus.isNotBlank()
}
// Groups raw airing/publishing text
private fun airingBucket(raw: String): String = when {
    raw.contains("Finished", ignoreCase = true) -> "Finished"
    raw.contains("Not yet", ignoreCase = true) -> "Upcoming"
    raw.isNotBlank() -> "Ongoing"
    else -> ""
}
private fun MediaItem.matches(f: DiscoverFilters): Boolean {
    if (f.genres.isNotEmpty() && genres.none { g -> f.genres.any { it.equals(g, ignoreCase = true) } }) return false
    if (f.themes.isNotEmpty() && contentThemes.none { t -> f.themes.any { it.equals(t, ignoreCase = true) } }) return false
    if (f.demographics.isNotEmpty() && demographics.none { d -> f.demographics.any { it.equals(d, ignoreCase = true) } }) return false
    if (f.studio.isNotBlank() && !creator.contains(f.studio, ignoreCase = true)) return false
    if (f.source.isNotBlank() && !source.equals(f.source, ignoreCase = true)) return false
    if (f.year.isNotBlank() && startDate != f.year) return false
    if (f.season != null && !season.equals(f.season.label, ignoreCase = true)) return false
    if (f.rating.isNotBlank() && !rating.equals(f.rating, ignoreCase = true)) return false
    if (f.format.isNotBlank() && !format.equals(f.format, ignoreCase = true)) return false
    if (f.airingStatus.isNotBlank() && airingBucket(airStatus) != f.airingStatus) return false
    return true
}
// Full genre taxonomy
val CommonGenres = listOf("Action", "Adventure", "Avant Garde", "Award Winning", "Boys Love", "Comedy", "Drama", "Fantasy", "Girls Love", "Gourmet", "Horror", "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Suspense")
val CommonExplicitGenres = listOf("Ecchi", "Erotica", "Hentai")
// Themes filter facet
val CommonThemes = listOf("Adult Cast", "Anthropomorphic", "CGDCT", "Childcare", "Combat Sports", "Crossdressing", "Delinquents", "Detective", "Educational", "Gag Humor", "Gore", "Harem", "High Stakes Game", "Historical", "Idols (Female)", "Idols (Male)", "Isekai", "Iyashikei", "Love Polygon", "Magical Sex Shift", "Mahou Shoujo", "Martial Arts", "Mecha", "Medical", "Military", "Music", "Mythology", "Organized Crime", "Otaku Culture", "Parody", "Performing Arts", "Pets", "Psychological", "Racing", "Reincarnation", "Reverse Harem", "Romantic Subtext", "Samurai", "School", "Showbiz", "Space", "Strategy Game", "Super Power", "Survival", "Team Sports", "Time Travel", "Vampire", "Video Game", "Villainess", "Visual Arts", "Workplace")
// Demographics filter facet
val CommonDemographics = listOf("Josei", "Kids", "Seinen", "Shoujo", "Shounen")
val CommonSources = listOf("Original", "Manga", "Light Novel", "Novel", "Visual Novel", "Game", "Web Manga", "Web Novel", "4-Koma Manga", "Other")
val CommonRatings = listOf("G - All Ages", "PG - Children", "PG-13", "R - 17+ (violence & profanity)", "R+ - Mild Nudity", "Rx - Hentai")
// Format filter options
val CommonAnimeFormats = listOf("TV", "OVA", "Movie", "Special", "ONA", "Music")
val CommonMangaFormats = listOf("Manga", "Novel", "Light Novel", "One Shot", "Doujinshi", "Manhwa", "Manhua", "OEL")
// Format switches media type
private fun resolvedDiscoverType(format: String, fallback: String): String = when {
    format in CommonAnimeFormats -> "Anime"
    format in CommonMangaFormats -> "Manga"
    else -> fallback
}
// Sort order options
enum class DiscoverSort(val label: String) { Members("Members"), Score("Score"), Newest("Newest"), Title("Title") }
// Title match ranking score
private fun MediaItem.titleMatchRank(query: String): Int {
    if (query.isBlank()) return Int.MAX_VALUE
    val q = query.trim().lowercase()
    val candidates = (listOf(title, titleEnglish) + synonyms).filter { it.isNotBlank() }.map { it.lowercase() }
    if (candidates.isEmpty()) return Int.MAX_VALUE
    return candidates.minOf { c ->
        when {
            c == q -> 0
            c.startsWith(q) -> 1
            Regex("\\b" + Regex.escape(q) + "\\b").containsMatchIn(c) -> 2
            c.contains(q) -> 3
            else -> 4
        }
    }
}
// Default blank query
private fun List<MediaItem>.sortedForDiscover(sort: DiscoverSort, titleLanguage: TitleLanguage, query: String = ""): List<MediaItem> {
    val bySort: Comparator<MediaItem> = when (sort) {
        DiscoverSort.Members -> compareByDescending { it.listUsers }
        DiscoverSort.Score -> compareByDescending { it.score }
        DiscoverSort.Newest -> compareByDescending { it.startDateFull.ifBlank { it.startDate } }
        DiscoverSort.Title -> compareBy { it.resolvedTitle(titleLanguage).lowercase() }
    }
    return if (query.isBlank()) sortedWith(bySort) else sortedWith(compareBy<MediaItem> { it.titleMatchRank(query) }.then(bySort))
}
enum class ForumMode { Boards, Topics }
// Ranking chart filters
enum class RankingSort(val label: String) {
    Score("Score"), Popularity("Popularity"), Favorite("Favorites"), Upcoming("Upcoming");
    fun apiValue(): String = when (this) { Score -> "all"; Popularity -> "bypopularity"; Favorite -> "favorite"; Upcoming -> "upcoming" }
}
// Four broadcast seasons
enum class SeasonName(val api: String, val label: String, val icon: ImageVector) {
    Winter("winter", "Winter", Icons.Default.AcUnit),
    Spring("spring", "Spring", Icons.Default.LocalFlorist),
    Summer("summer", "Summer", Icons.Default.BeachAccess),
    Fall("fall", "Fall", Icons.Default.Park),
}
private fun SeasonName.prev() = SeasonName.entries[(ordinal + 3) % 4]
private fun SeasonName.next() = SeasonName.entries[(ordinal + 1) % 4]
// Step season forward/back
private fun stepSeason(year: Int, season: SeasonName, forward: Boolean): Pair<Int, SeasonName> = when {
    forward && season == SeasonName.Fall -> year + 1 to SeasonName.Winter
    forward -> year to season.next()
    !forward && season == SeasonName.Winter -> year - 1 to SeasonName.Fall
    else -> year to season.prev()
}
private fun currentSeasonName(): SeasonName = when ((java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)) {
    in 1..3 -> SeasonName.Winter; in 4..6 -> SeasonName.Spring; in 7..9 -> SeasonName.Summer; else -> SeasonName.Fall
}
// Seasonal chart sort
enum class SeasonalSort(val api: String, val label: String) {
    Members("anime_num_list_users", "Members"),
    Score("anime_score", "Score"),
}
// My list sort order
enum class ListSort(val label: String) { Title("Title"), Score("Score"), LastUpdated("Last Updated"), StartDate("Start Date") }
// My list display mode
enum class ListViewMode { List, Grid }
private fun nowIso(): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
// Convert broadcast to local
private fun MediaItem.localBroadcast(): Pair<java.time.DayOfWeek, java.time.LocalTime>? {
    val dow = runCatching { java.time.DayOfWeek.valueOf(broadcastDay.uppercase(java.util.Locale.US)) }.getOrNull() ?: return null
    val time = runCatching { java.time.LocalTime.parse(broadcastTime) }.getOrDefault(java.time.LocalTime.MIDNIGHT)
    val jst = java.time.ZoneId.of("Asia/Tokyo")
    val anchor = java.time.LocalDate.now(jst).with(java.time.temporal.TemporalAdjusters.nextOrSame(dow)).atTime(time).atZone(jst)
    val local = anchor.withZoneSameInstant(java.time.ZoneId.systemDefault())
    return local.dayOfWeek to local.toLocalTime()
}
// Next episode air label
fun MediaItem.nextEpisodeLabel(): String? {
    if (!airStatus.equals("Currently Airing", ignoreCase = true)) return null
    val (day, time) = localBroadcast() ?: return null
    val now = java.time.LocalDateTime.now()
    var next = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(day)).atTime(time)
    if (next.isBefore(now)) next = next.plusDays(7)
    val hoursAway = java.time.Duration.between(now, next).toHours()
    return when {
        hoursAway < 24 -> "Airs today"
        hoursAway < 48 -> "Airs tomorrow"
        else -> "Next ep in ${hoursAway / 24}d"
    }
}
// Locale time format
private fun localizedTimeLabel(time: java.time.LocalTime, is24Hour: Boolean): String =
    time.format(java.time.format.DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", java.util.Locale.getDefault()))
// Read device time format
@Composable private fun systemIs24Hour(): Boolean = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
// Next airing full timestamp
private fun MediaItem.nextAirDateTime(): java.time.LocalDateTime? {
    if (!airStatus.equals("Currently Airing", ignoreCase = true)) return null
    val (day, time) = localBroadcast() ?: return null
    val now = java.time.LocalDateTime.now()
    var next = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(day)).atTime(time)
    if (next.isBefore(now)) next = next.plusDays(7)
    return next
}

class LibraryViewModel : ViewModel() {
    // Start with empty list
    var items by mutableStateOf(emptyList<MediaItem>()); private set
    var destination by mutableStateOf(Destination.Home)
    var signedIn by mutableStateOf(false); var loading by mutableStateOf(false); var error by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(ThemeMode.System)
    var colorSource by mutableStateOf(ColorSource.AppDefault); private set
    var paletteStyle by mutableStateOf(PaletteStyle.TonalSpot); private set
    var customColorHex by mutableStateOf("2E51A2"); private set
    var titleLanguage by mutableStateOf(TitleLanguage.Romaji)
    var listFilter by mutableStateOf("All")
    // Hoisted scroll state
    var listTypeTab by mutableStateOf(MediaType.Anime); private set
    var listSort by mutableStateOf(ListSort.Title); private set
    var listViewMode by mutableStateOf(ListViewMode.List); private set
    var listScrollIndex by mutableStateOf(0); private set
    var listScrollOffset by mutableStateOf(0); private set
    fun saveListScroll(index: Int, offset: Int) { listScrollIndex = index; listScrollOffset = offset }
    // Discover results scroll
    var discoverScrollIndex by mutableStateOf(0); private set
    var discoverScrollOffset by mutableStateOf(0); private set
    fun saveDiscoverScroll(index: Int, offset: Int) { discoverScrollIndex = index; discoverScrollOffset = offset }
    // Per-title detail scroll
    private val detailScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    fun getDetailScroll(id: String) = detailScrollPositions[id] ?: (0 to 0)
    fun saveDetailScroll(id: String, index: Int, offset: Int) { detailScrollPositions[id] = index to offset }
    // Reset scroll on sort
    fun selectListTypeTab(t: MediaType) { listTypeTab = t; listScrollIndex = 0; listScrollOffset = 0 }
    fun setListSort(context: Context, sort: ListSort) { listSort = sort; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_sort", sort.name).apply() }
    fun loadListSort(context: Context) { listSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("list_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
    fun setListViewMode(context: Context, mode: ListViewMode) { listViewMode = mode; settingsPrefs(context).edit().putString("list_view_mode", mode.name).apply() }
    fun loadListViewMode(context: Context) { listViewMode = runCatching { ListViewMode.valueOf(settingsPrefs(context).getString("list_view_mode", ListViewMode.List.name)!!) }.getOrDefault(ListViewMode.List) }
    // NSFW off by default
    var nsfwEnabled by mutableStateOf(false); private set
    // User profile stats
    var malProfile by mutableStateOf<MalProfile?>(null); private set
    var profileLoading by mutableStateOf(false); private set

    // App update state
    var updateInfo by mutableStateOf<AppUpdateInfo?>(null); private set
    var updateChecking by mutableStateOf(false); private set
    // Up to date flag
    var updateUpToDateMessage by mutableStateOf(false)
    var updateDialogOpen by mutableStateOf(false)
    var updateDownloadProgress by mutableStateOf<Float?>(null); private set
    var updateNeedsInstallPermission by mutableStateOf(false)
    var updateError by mutableStateOf<String?>(null)

    // Instant cached check read
    fun loadCachedUpdate(context: Context) {
        val checker = AppUpdateChecker(context)
        val cached = checker.cached() ?: return
        // Drop stale cached version
        if (!checker.isStillNewer(cached.version)) { checker.clearCache(); return }
        if (cached.version != checker.skippedVersion()) updateInfo = cached
    }
    // Manual vs auto check
    fun checkForUpdate(context: Context, manual: Boolean = false, onFound: (AppUpdateInfo) -> Unit = {}) {
        if (updateChecking) return
        updateChecking = true; updateUpToDateMessage = false; updateError = null
        viewModelScope.launch {
            val checker = AppUpdateChecker(context)
            checker.checkLatest()
                .onSuccess { found ->
                    val skipped = checker.skippedVersion()
                    val shown = found?.takeIf { it.version != skipped }
                    updateInfo = shown
                    if (shown != null) { onFound(shown); if (manual) updateDialogOpen = true } else if (manual) updateUpToDateMessage = true
                }
                .onFailure { if (manual) updateError = it.message ?: "Couldn't check for updates" }
            updateChecking = false
        }
    }
    fun skipUpdate(context: Context) {
        val version = updateInfo?.version ?: return
        AppUpdateChecker(context).skipVersion(version)
        updateInfo = null; updateDialogOpen = false
    }
    // Download then install APK
    fun downloadAndInstallUpdate(context: Context) {
        val info = updateInfo ?: return
        val checker = AppUpdateChecker(context)
        if (!checker.canRequestInstall()) { updateNeedsInstallPermission = true; return }
        updateDownloadProgress = 0f; updateError = null
        viewModelScope.launch {
            checker.downloadApk(info) { progress -> updateDownloadProgress = progress }
                // Clear badge on install
                .onSuccess { file -> updateDownloadProgress = null; updateDialogOpen = false; updateInfo = null; checker.clearCache(); checker.installApk(file) }
                .onFailure { updateDownloadProgress = null; updateError = it.message ?: "Download failed" }
        }
    }

    // NSFW-filtered list surfaces
    val visibleItems get() = items.nsfwFiltered(nsfwEnabled)
    val visibleDiscoverResults get() = discoverResults.nsfwFiltered(nsfwEnabled).filter { it.matches(discoverFilters) }.sortedForDiscover(discoverSort, titleLanguage, discoverQuery)
    val visibleDiscoverNewSeason get() = discoverNewSeason.nsfwFiltered(nsfwEnabled)
    val visibleDiscoverUpcoming get() = discoverUpcoming.nsfwFiltered(nsfwEnabled)
    val visibleRecommendations get() = recommendations.nsfwFiltered(nsfwEnabled)
    val visibleRankingResults get() = rankingResults.nsfwFiltered(nsfwEnabled)
    // Filter to premieres only
    val visibleSeasonalResults: List<MediaItem> get() {
        val filtered = if (seasonalContinuingOnly) seasonalResults
        else seasonalResults.filter { it.startDate == seasonalYear.toString() && it.season.equals(seasonalSeason.label, ignoreCase = true) }
        return filtered.nsfwFiltered(nsfwEnabled)
    }

    // Discover state survives navigation
    var discoverMode by mutableStateOf(DiscoverMode.Browse); private set
    var discoverQuery by mutableStateOf(""); private set
    var discoverTypeFilter by mutableStateOf("Anime"); private set
    var discoverResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverFilters by mutableStateOf(DiscoverFilters()); private set
    var discoverSort by mutableStateOf(DiscoverSort.Members); private set
    fun selectDiscoverSort(sort: DiscoverSort) { discoverSort = sort }
    var discoverSearching by mutableStateOf(false); private set
    var discoverError by mutableStateOf<String?>(null); private set
    var discoverNewSeason by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverUpcoming by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var discoverBrowseLoading by mutableStateOf(false); private set
    var discoverBrowseError by mutableStateOf<String?>(null); private set
    private var discoverBrowseLoaded = false
    private var discoverSearchJob: kotlinx.coroutines.Job? = null

    // Home recommendations row
    var recommendations by mutableStateOf<List<MediaItem>>(emptyList()); private set
    private var homeExtrasLoaded = false

    // Ranking chart state
    var rankingType by mutableStateOf(MediaType.Anime); private set
    var rankingSort by mutableStateOf(RankingSort.Score); private set
    var rankingResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var rankingLoading by mutableStateOf(false); private set
    var rankingError by mutableStateOf<String?>(null); private set

    // Seasonal chart state
    var seasonalYear by mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)); private set
    var seasonalSeason by mutableStateOf(currentSeasonName()); private set
    var seasonalSort by mutableStateOf(SeasonalSort.Members); private set
    // Continuing anime display filter
    var seasonalContinuingOnly by mutableStateOf(false); private set
    // Raw unfiltered season chart
    var seasonalResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var seasonalHasMore by mutableStateOf(false); private set
    var seasonalLoading by mutableStateOf(false); private set
    var seasonalLoadingMore by mutableStateOf(false); private set
    var seasonalError by mutableStateOf<String?>(null); private set
    // Restore scroll on return
    var seasonalScrollIndex by mutableStateOf(0); private set
    var seasonalScrollOffset by mutableStateOf(0); private set
    fun saveSeasonalScroll(index: Int, offset: Int) { seasonalScrollIndex = index; seasonalScrollOffset = offset }

    // Reset chart when leaving
    fun resetSeasonal() {
        seasonalYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        seasonalSeason = currentSeasonName()
        seasonalSort = SeasonalSort.Members
        seasonalContinuingOnly = false
        seasonalResults = emptyList()
        seasonalHasMore = false
        seasonalScrollIndex = 0
        seasonalScrollOffset = 0
        seasonalError = null
    }

    fun save(item: MediaItem) { items = if (items.any { it.id == item.id }) items.map { if (it.id == item.id) item else it } else listOf(item) + items }
    fun delete(id: String) { items = items.filterNot { it.id == id } }
    fun reset() { items = emptyList() }

    fun loadTheme(context: Context) { themeMode = runCatching { ThemeMode.valueOf(settingsPrefs(context).getString("theme_mode", ThemeMode.System.name)!!) }.getOrDefault(ThemeMode.System) }
    fun setTheme(context: Context, mode: ThemeMode) { themeMode = mode; settingsPrefs(context).edit().putString("theme_mode", mode.name).apply() }
    fun loadColorSource(context: Context) { colorSource = runCatching { ColorSource.valueOf(settingsPrefs(context).getString("color_source", ColorSource.AppDefault.name)!!) }.getOrDefault(ColorSource.AppDefault) }
    fun setColorSource(context: Context, source: ColorSource) { colorSource = source; settingsPrefs(context).edit().putString("color_source", source.name).apply() }
    fun loadPaletteStyle(context: Context) { paletteStyle = runCatching { PaletteStyle.valueOf(settingsPrefs(context).getString("palette_style", PaletteStyle.TonalSpot.name)!!) }.getOrDefault(PaletteStyle.TonalSpot) }
    fun setPaletteStyle(context: Context, style: PaletteStyle) { paletteStyle = style; settingsPrefs(context).edit().putString("palette_style", style.name).apply() }
    fun loadCustomColor(context: Context) { customColorHex = settingsPrefs(context).getString("custom_color_hex", "2E51A2") ?: "2E51A2" }
    // Persist only valid hex
    fun setCustomColor(context: Context, hex: String) {
        customColorHex = hex
        if (parseHexColor(hex) != null) settingsPrefs(context).edit().putString("custom_color_hex", hex).apply()
    }
    fun loadTitleLanguage(context: Context) { titleLanguage = runCatching { TitleLanguage.valueOf(settingsPrefs(context).getString("title_language", TitleLanguage.Romaji.name)!!) }.getOrDefault(TitleLanguage.Romaji) }
    fun setTitleLanguage(context: Context, lang: TitleLanguage) { titleLanguage = lang; settingsPrefs(context).edit().putString("title_language", lang.name).apply() }
    fun loadListFilter(context: Context) { listFilter = settingsPrefs(context).getString("list_filter", "All") ?: "All" }
    fun setListFilter(context: Context, filter: String) { listFilter = filter; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_filter", filter).apply() }
    fun loadNsfwPref(context: Context) { nsfwEnabled = settingsPrefs(context).getBoolean("nsfw_enabled", false) }
    fun setNsfw(context: Context, enabled: Boolean) { nsfwEnabled = enabled; settingsPrefs(context).edit().putBoolean("nsfw_enabled", enabled).apply() }

    // Load profile and stats
    fun loadProfile(context: Context) {
        val api = MalApi(context); if (!api.signedIn) { malProfile = null; return }
        profileLoading = true
        viewModelScope.launch { runCatching { api.profile() }.onSuccess { malProfile = it }; profileLoading = false }
    }

    fun load(context: Context) {
        val api = MalApi(context); signedIn = api.signedIn; if (!signedIn) return
        loading = true
        viewModelScope.launch { runCatching { api.library() }.onSuccess { items = it }.onFailure { error = it.message ?: "Could not load your MAL list" }; loading = false }
        loadProfile(context)
    }
    fun saveLive(context: Context, item: MediaItem) {
        val stamped = item.copy(updatedAt = nowIso(), inUserList = true)
        save(stamped)
        if (signedIn) viewModelScope.launch { runCatching { MalApi(context).update(stamped) }.onFailure { error = "MAL sync failed: ${it.message ?: "unknown error"}" } }
    }
    // Delete mirrors local-first save
    fun deleteLive(context: Context, item: MediaItem) {
        delete(item.id)
        if (signedIn) viewModelScope.launch { runCatching { MalApi(context).deleteEntry(item) }.onFailure { error = "MAL sync failed: ${it.message ?: "unknown error"}" } }
    }
    fun signOut(context: Context) { MalApi(context).signOut(); signedIn = false; items = emptyList(); malProfile = null }

    // Load home browse rows
    fun loadDiscoverBrowse(context: Context) {
        if (discoverBrowseLoaded || !MalApi(context).signedIn) return
        discoverBrowseLoaded = true
        discoverBrowseLoading = true
        viewModelScope.launch {
            val api = MalApi(context)
            runCatching { api.seasonalAnime(100) to api.upcomingAnime(10) }
                .onSuccess { (season, up) -> discoverNewSeason = season; discoverUpcoming = up; discoverBrowseError = null }
                .onFailure { discoverBrowseError = it.message ?: "Could not load Discover" }
            discoverBrowseLoading = false
        }
    }
    // Load recommendations row
    fun loadHomeExtras(context: Context) {
        if (homeExtrasLoaded || !MalApi(context).signedIn) return
        homeExtrasLoaded = true
        viewModelScope.launch { runCatching { MalApi(context).animeSuggestions(10) }.onSuccess { recommendations = it } }
    }
    // (Re)run ranking chart
    fun loadRanking(context: Context, type: MediaType, sort: RankingSort) {
        rankingType = type; rankingSort = if (type == MediaType.Manga && sort == RankingSort.Upcoming) RankingSort.Score else sort
        if (!MalApi(context).signedIn) { rankingError = "Sign in from Profile to view rankings"; return }
        viewModelScope.launch {
            rankingLoading = true
            runCatching { MalApi(context).ranking(rankingType, rankingSort.apiValue()) }
                .onSuccess { rankingResults = it; rankingError = null }
                .onFailure { rankingError = it.message ?: "Could not load ranking" }
            rankingLoading = false
        }
    }
    // (Re)run seasonal chart
    fun loadSeasonal(context: Context, year: Int = seasonalYear, season: SeasonName = seasonalSeason, sort: SeasonalSort = seasonalSort, continuingOnly: Boolean = seasonalContinuingOnly) {
        seasonalYear = year; seasonalSeason = season; seasonalSort = sort; seasonalContinuingOnly = continuingOnly
        if (!MalApi(context).signedIn) { seasonalError = "Sign in from Profile to browse seasons"; return }
        viewModelScope.launch {
            seasonalLoading = true
            runCatching { MalApi(context).seasonalAnime(year, season.api, sort = sort.api) }
                // Reconcile against user's library
                .onSuccess { seasonalResults = it.items.map { candidate -> items.find { i -> i.id == candidate.id && i.type == candidate.type } ?: candidate }; seasonalHasMore = it.hasMore; seasonalError = null }
                .onFailure { seasonalError = it.message ?: "Could not load season"; seasonalHasMore = false }
            seasonalLoading = false
        }
    }

    // Load more season page
    fun loadMoreSeasonal(context: Context) {
        if (seasonalLoading || seasonalLoadingMore || !seasonalHasMore) return
        val api = MalApi(context)
        if (!api.signedIn) return
        viewModelScope.launch {
            seasonalLoadingMore = true
            runCatching { api.seasonalAnime(seasonalYear, seasonalSeason.api, offset = seasonalResults.size, sort = seasonalSort.api) }
                // Reconcile against user's library
                .onSuccess { seasonalResults = seasonalResults + it.items.map { candidate -> items.find { i -> i.id == candidate.id && i.type == candidate.type } ?: candidate }; seasonalHasMore = it.hasMore }
                .onFailure { seasonalHasMore = false }
            seasonalLoadingMore = false
        }
    }
    // Switch to results page
    fun runDiscoverSearch(context: Context, query: String, type: String, filters: DiscoverFilters = discoverFilters) {
        discoverQuery = query; discoverTypeFilter = type; discoverFilters = filters; discoverMode = DiscoverMode.Results
        // Reset scroll for search
        discoverScrollIndex = 0; discoverScrollOffset = 0
        discoverSearchJob?.cancel()
        if (query.isBlank() && !filters.isActive()) { discoverResults = emptyList(); discoverSearching = false; discoverError = null; return }
        if (!MalApi(context).signedIn) { discoverError = "Sign in from Profile to search MyAnimeList"; return }
        discoverSearchJob = viewModelScope.launch {
            discoverSearching = true
            val t = when (type) { "Anime" -> MediaType.Anime; "Manga" -> MediaType.Manga; else -> null }
            val api = MalApi(context)
            runCatching {
                val results = if (query.isNotBlank()) api.search(query, t)
                // Search via Tenrai filters
                else if (filters.genres.isNotEmpty() || filters.themes.isNotEmpty() || filters.demographics.isNotEmpty()) {
                    val tenrai = TenraiApi()
                    val kinds = t?.let { listOf(if (it == MediaType.Anime) "anime" else "manga") } ?: listOf("anime", "manga")
                    // Pick one facet id
                    val names = filters.themes.ifEmpty { filters.genres.ifEmpty { filters.demographics } }
                    runCatching {
                        coroutineScope {
                            kinds.map { kind -> async { tenrai.searchByGenreIds(kind, tenrai.resolveGenreIds(kind, names), includeAdult = nsfwEnabled) } }
                                .awaitAll().flatten()
                        }
                    }.getOrElse {
                        // Fallback if Tenrai fails
                        coroutineScope {
                            (t?.let { listOf(it) } ?: MediaType.entries.toList()).flatMap { mt ->
                                listOf("all", "bypopularity", "favorite").map { rankType -> async { api.ranking(mt, rankType, limit = 500) } }
                            }.awaitAll().flatten()
                        }
                    }
                        .distinctBy { it.id }
                }
                // Merge multiple ranking charts
                else coroutineScope {
                    (t?.let { listOf(it) } ?: MediaType.entries.toList()).flatMap { mt ->
                        val rankTypes = if (mt == MediaType.Anime)
                            listOf("all", "bypopularity", "favorite", "airing", "upcoming", "tv", "ova", "movie", "special")
                        else
                            listOf("all", "bypopularity", "favorite", "manga", "novels", "oneshots", "doujin", "manhwa", "manhua")
                        rankTypes.map { rankType -> async { api.ranking(mt, rankType, limit = 500) } }
                    }.awaitAll().flatten()
                }.distinctBy { it.id }
                // Reconcile against user's library
                results.map { candidate -> items.find { it.id == candidate.id && it.type == candidate.type } ?: candidate }
            }
                .onSuccess { discoverResults = it; discoverError = null }
                .onFailure { discoverError = it.message ?: "Search failed" }
            discoverSearching = false
        }
    }
    // Return to browse view
    fun exitDiscoverSearch() {
        discoverSort = DiscoverSort.Members
        discoverSearchJob?.cancel()
        discoverMode = DiscoverMode.Browse; discoverQuery = ""; discoverResults = emptyList(); discoverFilters = DiscoverFilters(); discoverError = null
    }

    // Forum browsing state hoisted
    var forumMode by mutableStateOf(ForumMode.Boards); private set
    var forumCategories by mutableStateOf<List<ForumCategory>>(emptyList()); private set
    var forumBoardsLoading by mutableStateOf(false); private set
    var forumBoardsError by mutableStateOf<String?>(null); private set
    private var forumBoardsLoaded = false
    // Blank title means search
    var forumBoardTitle by mutableStateOf(""); private set
    var forumBoardId by mutableStateOf<Int?>(null); private set
    var forumSubboards by mutableStateOf<List<ForumSubboard>>(emptyList()); private set
    var forumSubboardId by mutableStateOf<Int?>(null); private set
    var forumQuery by mutableStateOf(""); private set
    var forumTopics by mutableStateOf<List<ForumTopic>>(emptyList()); private set
    var forumTopicsLoading by mutableStateOf(false); private set
    var forumTopicsError by mutableStateOf<String?>(null); private set
    var forumHasMore by mutableStateOf(false); private set
    var forumLoadingMore by mutableStateOf(false); private set
    private var forumTopicsJob: kotlinx.coroutines.Job? = null
    // Forum scroll position slots
    var forumBoardsScrollIndex by mutableStateOf(0); private set
    var forumBoardsScrollOffset by mutableStateOf(0); private set
    fun saveForumBoardsScroll(index: Int, offset: Int) { forumBoardsScrollIndex = index; forumBoardsScrollOffset = offset }
    var forumTopicsScrollIndex by mutableStateOf(0); private set
    var forumTopicsScrollOffset by mutableStateOf(0); private set
    fun saveForumTopicsScroll(index: Int, offset: Int) { forumTopicsScrollIndex = index; forumTopicsScrollOffset = offset }
    private val forumTopicScrollPositions = mutableMapOf<Int, Pair<Int, Int>>()
    fun forumTopicScrollFor(topicId: Int): Pair<Int, Int> = forumTopicScrollPositions[topicId] ?: (0 to 0)
    fun saveForumTopicScroll(topicId: Int, index: Int, offset: Int) { forumTopicScrollPositions[topicId] = index to offset }

    // Load forum board hierarchy
    fun loadForumBoards(context: Context) {
        if (forumBoardsLoaded || !MalApi(context).signedIn) return
        forumBoardsLoaded = true
        forumBoardsLoading = true
        viewModelScope.launch {
            runCatching { MalApi(context).forumBoards() }
                .onSuccess { forumCategories = it; forumBoardsError = null }
                .onFailure { forumBoardsError = it.message ?: "Could not load forums" }
            forumBoardsLoading = false
        }
    }
    // Open board's topic list
    fun openForumBoard(context: Context, board: ForumBoard) {
        forumMode = ForumMode.Topics
        forumBoardTitle = board.title; forumBoardId = board.id; forumSubboards = board.subboards; forumSubboardId = null; forumQuery = ""
        forumTopicsScrollIndex = 0; forumTopicsScrollOffset = 0
        runForumTopics(context)
    }
    // Narrow to one subboard
    fun openForumSubboard(context: Context, subboardId: Int?) {
        forumSubboardId = subboardId
        forumTopicsScrollIndex = 0; forumTopicsScrollOffset = 0
        runForumTopics(context)
    }
    // Cross-board keyword search
    fun runForumSearch(context: Context, query: String) {
        forumMode = ForumMode.Topics
        forumBoardTitle = ""; forumBoardId = null; forumSubboards = emptyList(); forumSubboardId = null; forumQuery = query
        forumTopicsScrollIndex = 0; forumTopicsScrollOffset = 0
        runForumTopics(context)
    }
    // Is News Discussion board?
    val forumIsNewsBoard: Boolean get() = forumBoardTitle.equals("News Discussion", ignoreCase = true)
    private fun runForumTopics(context: Context) {
        forumTopicsJob?.cancel()
        if (!MalApi(context).signedIn) { forumTopicsError = "Sign in from Profile to browse the forums"; return }
        val newsBoard = forumIsNewsBoard
        forumTopicsJob = viewModelScope.launch {
            forumTopicsLoading = true
            runCatching { MalApi(context).forumTopics(boardId = forumBoardId, subboardId = forumSubboardId, query = forumQuery, withThumbnails = newsBoard) }
                .onSuccess { forumTopics = it.items; forumHasMore = it.hasMore; forumTopicsError = null }
                .onFailure { forumTopicsError = it.message ?: "Could not load topics" }
            forumTopicsLoading = false
        }
    }
    // Load more forum topics
    fun loadMoreForumTopics(context: Context) {
        if (forumTopicsLoading || forumLoadingMore || !forumHasMore) return
        val api = MalApi(context); if (!api.signedIn) return
        val newsBoard = forumIsNewsBoard
        viewModelScope.launch {
            forumLoadingMore = true
            runCatching { api.forumTopics(boardId = forumBoardId, subboardId = forumSubboardId, query = forumQuery, offset = forumTopics.size, withThumbnails = newsBoard) }
                .onSuccess { forumTopics = forumTopics + it.items; forumHasMore = it.hasMore }
                .onFailure { forumHasMore = false }
            forumLoadingMore = false
        }
    }
    // Return to board list
    fun exitForumTopics() {
        forumTopicsJob?.cancel()
        forumMode = ForumMode.Boards; forumBoardTitle = ""; forumBoardId = null; forumSubboards = emptyList(); forumSubboardId = null; forumQuery = ""
        forumTopics = emptyList(); forumTopicsError = null; forumHasMore = false
    }
    // Jump to News board
    fun openNewsBoard(context: Context) {
        viewModelScope.launch {
            val cached = forumCategories.flatMap { it.boards }.firstOrNull { it.title.equals("News Discussion", ignoreCase = true) }
            val board = cached ?: run {
                val fetched = runCatching { MalApi(context).forumBoards() }.getOrNull() ?: return@run null
                forumCategories = fetched; forumBoardsLoaded = true
                fetched.flatMap { it.boards }.firstOrNull { it.title.equals("News Discussion", ignoreCase = true) }
            }
            board?.let { openForumBoard(context, it) }
        }
    }

    // Home snapshots row state
    var newsSnapshots by mutableStateOf<List<NewsSnapshot>>(emptyList()); private set
    var newsSnapshotsLoading by mutableStateOf(false); private set
    private var newsSnapshotsLoaded = false
    fun loadNewsSnapshots(context: Context) {
        if (newsSnapshotsLoaded || !MalApi(context).signedIn) return
        newsSnapshotsLoaded = true
        newsSnapshotsLoading = true
        viewModelScope.launch {
            runCatching { MalApi(context).newsSnapshots() }
                .onSuccess { newsSnapshots = it }
                // Fail silently, no banner
                .onFailure { newsSnapshotsLoaded = false }
            newsSnapshotsLoading = false
        }
    }

    // Related row loading id
    var relatedLoadingId by mutableStateOf<Int?>(null); private set

    // Recommended row loading id
    var recommendedLoadingId by mutableStateOf<Int?>(null); private set

    // Discover row loading id
    var discoverDetailLoadingId by mutableStateOf<String?>(null); private set

    // Fetch full record first
    fun openDiscoverDetail(context: Context, item: MediaItem, onLoaded: (MediaItem) -> Unit) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onLoaded(item); return }
        discoverDetailLoadingId = item.id
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, item.type) }
                .onSuccess { onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            discoverDetailLoadingId = null
        }
    }

    // Open related-row title
    fun openRelated(context: Context, entry: RelatedEntry, onLoaded: (MediaItem) -> Unit) {
        val type = if (entry.malType == "anime") MediaType.Anime else MediaType.Manga
        relatedLoadingId = entry.malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(entry.malId, type) }
                .onSuccess { onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            relatedLoadingId = null
        }
    }

    // Backfill empty related row
    fun backfillRelated(context: Context, id: String, type: MediaType, onFound: (List<RelatedEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.related.isNotEmpty()) onFound(fresh.related) }
            onDone()
        }
    }

    // Backfill empty theme fields
    fun backfillThemes(context: Context, id: String, type: MediaType, onFound: (List<String>, List<String>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.openingThemes.isNotEmpty() || fresh.endingThemes.isNotEmpty()) onFound(fresh.openingThemes, fresh.endingThemes) }
            onDone()
        }
    }

    // Backfill missing cover gallery
    fun backfillCovers(context: Context, id: String, type: MediaType, onFound: (List<String>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.covers.size > 1) onFound(fresh.covers) }
            onDone()
        }
    }

    // Load characters + staff rows
    fun loadCharactersStaff(item: MediaItem, onFound: (List<CharacterEntry>, List<StaffEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        val kind = if (item.type == MediaType.Anime) "anime" else "manga"
        viewModelScope.launch {
            runCatching {
                val tenrai = TenraiApi()
                coroutineScope {
                    val chars = async { tenrai.fetchCharacters(kind, intId) }
                    val staffList = async { tenrai.fetchStaff(kind, intId) }
                    chars.await() to staffList.await()
                }
            }.onSuccess { (chars, staffList) -> if (chars.isNotEmpty() || staffList.isNotEmpty()) onFound(chars, staffList) }
            onDone()
        }
    }

    // Load reviews row
    fun loadReviews(item: MediaItem, onFound: (List<ReviewEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        val kind = if (item.type == MediaType.Anime) "anime" else "manga"
        viewModelScope.launch {
            runCatching { TenraiApi().fetchReviews(kind, intId) }
                .onSuccess { if (it.isNotEmpty()) onFound(it) }
            onDone()
        }
    }

    // Backfill recommended row
    fun loadUserRecommendations(context: Context, item: MediaItem, onFound: (List<RecommendedEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).userRecommendations(intId, item.type) }
                .onSuccess { if (it.isNotEmpty()) onFound(it) }
            onDone()
        }
    }

    // Open recommended-row title
    fun openRecommended(context: Context, entry: RecommendedEntry, onLoaded: (MediaItem) -> Unit) {
        val type = if (entry.malType == "anime") MediaType.Anime else MediaType.Manga
        recommendedLoadingId = entry.malId
        viewModelScope.launch {
            runCatching { MalApi(context).detail(entry.malId, type) }
                .onSuccess { onLoaded(it) }
                .onFailure { error = it.message ?: "Could not load title" }
            recommendedLoadingId = null
        }
    }

    // Load status distribution data
    fun loadStatusDistribution(context: Context, item: MediaItem, onFound: (StatusDistribution) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null || item.type != MediaType.Anime) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, item.type) }
                .onSuccess { fresh -> if (fresh.statusDistribution.total > 0) onFound(fresh.statusDistribution) }
            onDone()
        }
    }
}
private fun settingsPrefs(context: Context) = context.getSharedPreferences("kiko_settings", Context.MODE_PRIVATE)

// App update notification
private const val UPDATE_NOTIFICATION_CHANNEL = "app_updates"
private const val UPDATE_NOTIFICATION_ID = 4201
private fun ensureUpdateChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(UPDATE_NOTIFICATION_CHANNEL) != null) return
    manager.createNotificationChannel(
        NotificationChannel(UPDATE_NOTIFICATION_CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Lets you know when a new version of Kiko is ready to install" }
    )
}
// Tapping reopens the app
private fun postUpdateNotification(context: Context, info: AppUpdateInfo) {
    ensureUpdateChannel(context)
    val openIntent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pendingIntent = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Kiko ${info.version} is available")
        .setContentText("Tap to update, from Profile > Check for updates.")
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
    }
}

// Activity section
class MainActivity : ComponentActivity() {
    private var callback by mutableStateOf<Uri?>(null)
    // Opened via MAL link
    private var malLink by mutableStateOf<Uri?>(null)
    // Pending update during permission
    private var pendingUpdateNotification: AppUpdateInfo? = null
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val info = pendingUpdateNotification; pendingUpdateNotification = null
        if (granted && info != null) { postUpdateNotification(this, info); AppUpdateChecker(this).markNotified(info.version) }
    }
    // Only for auto-check
    private fun notifyUpdateAvailable(info: AppUpdateInfo) {
        if (info.version == AppUpdateChecker(this).notifiedVersion()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingUpdateNotification = info
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        postUpdateNotification(this, info)
        AppUpdateChecker(this).markNotified(info.version)
    }
    private fun routeIntentUri(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme == "com.kiko.tracker") callback = uri else malLink = uri
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register animated GIF decoders + Referer/UA for hotlink-protected images
        val forumImageClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Referer", "https://myanimelist.net/")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                    .build()
                chain.proceed(req)
            }
            .build()
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(forumImageClient)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(coil.decode.GifDecoder.Factory())
                }
                .build()
        )
        routeIntentUri(intent?.data)
        setContent {
            val vm: LibraryViewModel = viewModel()
            LaunchedEffect(Unit) { vm.loadTheme(this@MainActivity); vm.loadColorSource(this@MainActivity); vm.loadPaletteStyle(this@MainActivity); vm.loadCustomColor(this@MainActivity); vm.loadTitleLanguage(this@MainActivity); vm.loadListFilter(this@MainActivity); vm.loadListSort(this@MainActivity); vm.loadListViewMode(this@MainActivity); vm.loadNsfwPref(this@MainActivity); vm.load(this@MainActivity); vm.loadDiscoverBrowse(this@MainActivity); vm.loadHomeExtras(this@MainActivity) }
            // Throttled background update check
            LaunchedEffect(Unit) {
                vm.loadCachedUpdate(this@MainActivity)
                val staleAfterMs = 12 * 60 * 60 * 1000L
                if (System.currentTimeMillis() - AppUpdateChecker(this@MainActivity).lastCheckedAt() > staleAfterMs) {
                    vm.checkForUpdate(this@MainActivity, manual = false, onFound = ::notifyUpdateAvailable)
                }
            }
            LaunchedEffect(callback) {
                callback?.let { uri ->
                    vm.loading = true
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        // Reload full homepage
                        MalApi(this@MainActivity).finishAuth(uri).onSuccess { vm.load(this@MainActivity); vm.loadDiscoverBrowse(this@MainActivity); vm.loadHomeExtras(this@MainActivity) }.onFailure { vm.error = it.message }
                        vm.loading = false
                    }
                    callback = null
                }
            }
            KikoApp(
                vm,
                onSignIn = { if (BuildConfig.MAL_CLIENT_ID.isBlank()) vm.error = "Add your MAL Client ID to local.properties first" else CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, Uri.parse(MalApi(this@MainActivity).authUrl())) },
                onSignOut = { vm.signOut(this@MainActivity) },
                malLink = malLink,
                onMalLinkHandled = { malLink = null },
            )
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); routeIntentUri(intent.data) }
}

// Sync system bars theme
@Composable
private fun SyncSystemBars(darkTheme: Boolean, background: Color) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        SideEffect {
            val window = activity?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            window.statusBarColor = background.toArgb()
            window.navigationBarColor = background.toArgb()
        }
    }
}

// Shared navigation transition motion
private val PushEnter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 4 }
private val PushExit = fadeOut(tween(150))
private val PopEnter = fadeIn(tween(220))
private val PopExit = fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { it / 4 }
private val FadeEnter = fadeIn(tween(220))
private val FadeExit = fadeOut(tween(150))

// Top-level navigation state
private sealed class TopScreen {
    data class Detail(val item: MediaItem) : TopScreen()
    object Ranking : TopScreen()
    object Seasonal : TopScreen()
    // Seed initial schedule day
    data class Schedule(val initialDay: java.time.DayOfWeek) : TopScreen()
    // Reading single forum topic
    data class Topic(val topicId: Int, val title: String) : TopScreen()
    // App info page
    object About : TopScreen()
    // Full review readout
    data class Review(val review: ReviewEntry, val itemTitle: String) : TopScreen()
    // Reviews page in webview
    data class ReviewList(val url: String, val itemTitle: String) : TopScreen()
    data class Tab(val destination: Destination) : TopScreen()
}
// Same screen vs navigation
private fun TopScreen.navKey(): Any = when (this) {
    is TopScreen.Detail -> "detail:${item.id}"
    TopScreen.Ranking -> "ranking"
    TopScreen.Seasonal -> "seasonal"
    is TopScreen.Schedule -> "schedule"
    is TopScreen.Topic -> "topic:$topicId"
    TopScreen.About -> "about"
    is TopScreen.Review -> "review:${review.malId}"
    is TopScreen.ReviewList -> "reviewList:$url"
    is TopScreen.Tab -> "tab:$destination"
}
private fun TopScreen.isFullPage() = this is TopScreen.Detail || this is TopScreen.Ranking || this is TopScreen.Seasonal || this is TopScreen.Schedule || this is TopScreen.Topic || this is TopScreen.About || this is TopScreen.Review || this is TopScreen.ReviewList

@Composable fun KikoApp(vm: LibraryViewModel = viewModel(), onSignIn: () -> Unit = {}, onSignOut: () -> Unit = {}, malLink: Uri? = null, onMalLinkHandled: () -> Unit = {}) {
    val context = LocalContext.current
    var editor by remember { mutableStateOf<MediaItem?>(null) }; var themeOpen by remember { mutableStateOf(false) }; var titleLangOpen by remember { mutableStateOf(false) }
    var colorSourceOpen by remember { mutableStateOf(false) }; var paletteStyleOpen by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    // Related title navigation stack
    var detailStack by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    fun openDetail(item: MediaItem) { detailStack = emptyList(); selectedItem = item }
    fun openRelatedDetail(from: MediaItem, to: MediaItem) { detailStack = detailStack + from; selectedItem = to }
    fun backDetail() {
        val prev = detailStack.lastOrNull()
        if (prev != null) { selectedItem = prev; detailStack = detailStack.dropLast(1) } else selectedItem = null
    }
    // Handle tapped MAL link
    LaunchedEffect(malLink) {
        val uri = malLink ?: return@LaunchedEffect
        parseMalDeepLink(uri)?.let { (id, type) ->
            vm.loading = true
            runCatching { MalApi(context).detail(id, type) }
                .onSuccess { openDetail(it) }
                .onFailure { vm.error = it.message ?: "Could not load that MAL link" }
            vm.loading = false
        }
        onMalLinkHandled()
    }
    // Home full-screen destinations
    var rankingOpen by remember { mutableStateOf(false) }
    var seasonalOpen by remember { mutableStateOf(false) }
    // Schedule day to open
    var scheduleOpen by remember { mutableStateOf(false) }
    var scheduleInitialDay by remember { mutableStateOf(java.time.LocalDate.now().dayOfWeek) }
    fun openSchedule(day: java.time.DayOfWeek) { scheduleInitialDay = day; scheduleOpen = true }
    // Forum topic screen state
    var forumTopicOpen by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // About page open state
    var aboutOpen by remember { mutableStateOf(false) }
    // Full review readout state
    var reviewOpen by remember { mutableStateOf<Pair<ReviewEntry, String>?>(null) }
    // Reviews webview state
    var reviewListOpen by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Live-merge search result
    val editorItem = editor?.let { ed -> vm.visibleItems.find { it.id == ed.id } ?: vm.items.find { it.id == ed.id } ?: ed }
    // Prefer live item copy
    val detailItem = selectedItem?.let { sel -> vm.items.find { it.id == sel.id } ?: sel }
    // Back press returns home
    BackHandler(enabled = detailItem == null && !rankingOpen && !seasonalOpen && !scheduleOpen && forumTopicOpen == null && !aboutOpen && reviewOpen == null && reviewListOpen == null && vm.destination != Destination.Home) {
        vm.destination = Destination.Home
    }
    val darkTheme = when (vm.themeMode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    // Default palette uses constants
    val c = remember(darkTheme, vm.colorSource, vm.paletteStyle, vm.customColorHex) {
        if (vm.colorSource == ColorSource.AppDefault && vm.paletteStyle == PaletteStyle.TonalSpot) {
            if (darkTheme) DarkKiko else LightKiko
        } else {
            themedPalette(resolveSeedColor(context, vm.colorSource, vm.customColorHex, darkTheme), vm.paletteStyle, darkTheme)
        }
    }
    SyncSystemBars(darkTheme, c.background)
    CompositionLocalProvider(LocalKikoColors provides c, LocalTitleLanguage provides vm.titleLanguage) {
        MaterialTheme(
            colorScheme = if (darkTheme)
                darkColorScheme(primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryContainer, background = c.background, surface = c.surface, onBackground = c.ink, onSurface = c.ink)
            else
                lightColorScheme(primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryContainer, background = c.background, surface = c.surface, onBackground = c.ink, onSurface = c.ink),
            typography = Typography(
                displaySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp),
                headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 24.sp),
                titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 21.sp),
                titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                bodyMedium = TextStyle(fontFamily = AppFont, fontSize = 14.sp)
            )
        ) {
            Scaffold(
                containerColor = c.background,
                bottomBar = { if (detailItem == null && !rankingOpen && !seasonalOpen && !scheduleOpen && forumTopicOpen == null && !aboutOpen && reviewOpen == null && reviewListOpen == null) BottomBar(vm.destination) { vm.destination = it } }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val topScreen = when {
                        reviewOpen != null -> TopScreen.Review(reviewOpen!!.first, reviewOpen!!.second)
                        reviewListOpen != null -> TopScreen.ReviewList(reviewListOpen!!.first, reviewListOpen!!.second)
                        detailItem != null -> TopScreen.Detail(detailItem)
                        rankingOpen -> TopScreen.Ranking
                        seasonalOpen -> TopScreen.Seasonal
                        scheduleOpen -> TopScreen.Schedule(scheduleInitialDay)
                        forumTopicOpen != null -> TopScreen.Topic(forumTopicOpen!!.first, forumTopicOpen!!.second)
                        aboutOpen -> TopScreen.About
                        else -> TopScreen.Tab(vm.destination)
                    }
                    AnimatedContent(
                        targetState = topScreen,
                        contentKey = { it.navKey() },
                        transitionSpec = {
                            when {
                                targetState.isFullPage() && !initialState.isFullPage() -> PushEnter togetherWith PushExit
                                !targetState.isFullPage() && initialState.isFullPage() -> PopEnter togetherWith PopExit
                                else -> FadeEnter togetherWith FadeExit
                            }
                        },
                        label = "topScreen",
                    ) { screen ->
                        when (screen) {
                            is TopScreen.Detail -> DetailScreen(screen.item, onBack = ::backDetail, onEdit = { editor = it }, onOpenRelated = { rel -> vm.openRelated(context, rel) { fetched -> openRelatedDetail(screen.item, fetched) } }, relatedLoadingId = vm.relatedLoadingId, onBackfillRelated = { id, type, onFound, onDone -> vm.backfillRelated(context, id, type, onFound, onDone) }, onBackfillThemes = { id, type, onFound, onDone -> vm.backfillThemes(context, id, type, onFound, onDone) }, onBackfillCovers = { id, type, onFound, onDone -> vm.backfillCovers(context, id, type, onFound, onDone) }, onLoadRecommended = { forItem, onFound, onDone -> vm.loadUserRecommendations(context, forItem, onFound, onDone) }, onOpenRecommended = { rec -> vm.openRecommended(context, rec) { fetched -> openRelatedDetail(screen.item, fetched) } }, recommendedLoadingId = vm.recommendedLoadingId, onLoadStatusDistribution = { forItem, onFound, onDone -> vm.loadStatusDistribution(context, forItem, onFound, onDone) }, onLoadCharactersStaff = { forItem, onFound, onDone -> vm.loadCharactersStaff(forItem, onFound, onDone) }, onLoadReviews = { forItem, onFound, onDone -> vm.loadReviews(forItem, onFound, onDone) }, onOpenReview = { rev -> reviewOpen = rev to screen.item.title }, onOpenReviewList = { url, title -> reviewListOpen = url to title }, initialScroll = vm.getDetailScroll(screen.item.id), onLeaveScroll = { index, offset -> vm.saveDetailScroll(screen.item.id, index, offset) }, myListStatus = vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { it to li.status } }.toMap(), onGenreClick = { genre ->
                                selectedItem = null; detailStack = emptyList()
                                vm.destination = Destination.Discover
                                vm.runDiscoverSearch(context, "", if (screen.item.type == MediaType.Manga) "Manga" else "Anime", DiscoverFilters(genres = setOf(genre)))
                            })
                            TopScreen.Ranking -> RankingScreen(vm, onBack = { rankingOpen = false }, onOpenDetail = ::openDetail)
                            TopScreen.Seasonal -> SeasonalScreen(vm, onBack = { seasonalOpen = false }, onOpenDetail = ::openDetail)
                            is TopScreen.Schedule -> ScheduleScreen(vm, initialDay = screen.initialDay, onBack = { scheduleOpen = false }, onOpenDetail = ::openDetail)
                            is TopScreen.Topic -> ForumTopicScreen(vm, topicId = screen.topicId, title = screen.title, onBack = { forumTopicOpen = null })
                            TopScreen.About -> AboutScreen(
                                onBack = { aboutOpen = false },
                                updateInfo = vm.updateInfo, updateChecking = vm.updateChecking, updateUpToDate = vm.updateUpToDateMessage,
                                onCheckForUpdate = { if (vm.updateInfo != null) vm.updateDialogOpen = true else vm.checkForUpdate(context, manual = true) },
                            )
                            is TopScreen.Review -> ReviewScreen(screen.review, screen.itemTitle, onBack = { reviewOpen = null })
                            is TopScreen.ReviewList -> WebPageScreen(screen.url, screen.itemTitle, darkTheme = darkTheme, onBack = { reviewListOpen = null })
                            is TopScreen.Tab -> when (screen.destination) {
                                Destination.Home -> HomeScreen(vm, onOpenDetail = ::openDetail, onList = { vm.destination = Destination.List }, onDiscover = { vm.destination = Destination.Discover }, onRanking = { rankingOpen = true }, onSeasonal = { seasonalOpen = true }, onSchedule = ::openSchedule, onOpenTopic = { id, title -> forumTopicOpen = id to title }, onSeeNews = { vm.destination = Destination.Forums; vm.openNewsBoard(context) })
                                Destination.List -> ListScreen(vm, onOpenDetail = ::openDetail, onIncrement = { vm.saveLive(context, it) })
                                Destination.Discover -> DiscoverScreen(
                                    vm,
                                    onOpenDetail = ::openDetail,
                                    onRanking = { rankingOpen = true },
                                    onSeasonal = { seasonalOpen = true }
                                )
                                Destination.Forums -> ForumsScreen(vm, onOpenTopic = { id, title -> forumTopicOpen = id to title })
                                Destination.Profile -> ProfileScreen(vm.signedIn, vm.malProfile, vm.items, vm.themeMode, vm.colorSource, vm.paletteStyle, vm.titleLanguage, vm.nsfwEnabled, onNsfwChange = { vm.setNsfw(context, it) }, onConnect = onSignIn, onSignOut = onSignOut, onThemeClick = { themeOpen = true }, onColorClick = { colorSourceOpen = true }, onPaletteClick = { paletteStyleOpen = true }, onTitleLanguageClick = { titleLangOpen = true },
                                    updateInfo = vm.updateInfo, onAboutClick = { aboutOpen = true })
                            }
                        }
                    }
                    vm.error?.let { msg -> Text(msg, color = c.danger, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) }
                }
            }
            // Keep sheets inside theme
            editorItem?.let { EditSheet(it, onDismiss = { editor = null }, onSave = { vm.saveLive(context, it); editor = null }, onDelete = { vm.deleteLive(context, it); editor = null; if (selectedItem?.id == it.id) { selectedItem = null; detailStack = emptyList() } }) }
            if (themeOpen) ThemeSheet(vm.themeMode, onDismiss = { themeOpen = false }, onSelect = { vm.setTheme(context, it); themeOpen = false })
            if (colorSourceOpen) ColorSourceSheet(vm.colorSource, vm.customColorHex, onDismiss = { colorSourceOpen = false }, onSelect = { vm.setColorSource(context, it) }, onCustomHexChange = { vm.setCustomColor(context, it) })
            if (paletteStyleOpen) PaletteStyleSheet(vm.paletteStyle, onDismiss = { paletteStyleOpen = false }, onSelect = { vm.setPaletteStyle(context, it); paletteStyleOpen = false })
            if (titleLangOpen) TitleLanguageSheet(vm.titleLanguage, onDismiss = { titleLangOpen = false }, onSelect = { vm.setTitleLanguage(context, it); titleLangOpen = false })
            if (vm.updateDialogOpen) vm.updateInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    downloadProgress = vm.updateDownloadProgress,
                    needsInstallPermission = vm.updateNeedsInstallPermission,
                    error = vm.updateError,
                    onDownload = { vm.downloadAndInstallUpdate(context) },
                    onOpenInstallSettings = { vm.updateNeedsInstallPermission = false; context.startActivity(AppUpdateChecker(context).installPermissionSettingsIntent()) },
                    onSkip = { vm.skipUpdate(context) },
                    onDismiss = { vm.updateDialogOpen = false; vm.updateNeedsInstallPermission = false; vm.updateError = null },
                )
            }
        }
    }
}

// Shared pieces section
@Composable private fun BottomBar(selected: Destination, select: (Destination) -> Unit) { val c = LocalKikoColors.current; NavigationBar(containerColor = c.surface, tonalElevation = 4.dp) { Destination.entries.forEach { d -> NavigationBarItem(selected = d == selected, onClick = { select(d) }, icon = { Icon(d.icon, null) }, label = { Text(d.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = c.primary, selectedTextColor = c.primary, unselectedIconColor = c.muted, unselectedTextColor = c.muted, indicatorColor = c.primaryContainer)) } } }
@Composable private fun AppHeader(title: String, action: @Composable () -> Unit = {}) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-1).sp, color = LocalKikoColors.current.ink); action() } }

// Unused params kept intentionally
@Composable private fun HomeScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, onList: () -> Unit, onDiscover: () -> Unit, onRanking: () -> Unit, onSeasonal: () -> Unit, onSchedule: (java.time.DayOfWeek) -> Unit, onOpenTopic: (Int, String) -> Unit, onSeeNews: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    LaunchedEffect(vm.signedIn) { vm.loadNewsSnapshots(context) }
    val items = vm.visibleItems
    // Most recently updated wins
    val active = items.filter { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }.maxByOrNull { it.updatedAt }
        ?: items.firstOrNull { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }
        ?: items.firstOrNull()
    val today = java.time.LocalDate.now().dayOfWeek
    // Airing-next row pool
    val airingNext = vm.visibleDiscoverNewSeason.mapNotNull { item -> item.nextAirDateTime()?.let { item to it } }.sortedBy { it.second }.take(5).map { it.first }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            AppHeader("kiko") { Avatar(vm.malProfile?.picture.orEmpty()) }
            Column(Modifier.padding(horizontal = 20.dp)) {
                // Use device current date
                Text(
                    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.getDefault())).uppercase(java.util.Locale.getDefault()),
                    color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp,
                )
                if (airingNext.isNotEmpty()) {
                    SectionTitle("Airing next", "See all") { onSchedule(today) }
                    AiringNextRow(airingNext, onOpenDetail)
                }
                if (vm.signedIn && vm.loading) {
                    SectionTitle("Continue", "See list", onList); ContinueSkeletonCard()
                } else if (active != null) {
                    SectionTitle("Continue", "See list", onList); ContinueCard(active, onOpenDetail)
                }
                // Home recent news row
                if (vm.newsSnapshots.isNotEmpty()) {
                    SectionTitle("Snapshots", "See news", onSeeNews)
                    SnapshotsGrid(vm.newsSnapshots, onOpenTopic)
                }
                if (active == null && !vm.loading && !vm.signedIn && airingNext.isEmpty()) {
                    Text("Sign in from Profile to see releases and recommendations.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 40.dp))
                }
            }
        }
    }
}
// Airing next row order
@Composable private fun AiringNextRow(items: List<MediaItem>, onOpenDetail: (MediaItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) { items(items, key = { it.id }) { AiringNextCard(it, onOpenDetail) } }
}
// Airing next card layout
@Composable private fun AiringNextCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val is24Hour = systemIs24Hour()
    val time = item.localBroadcast()?.second
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = c.surface), elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.width(250.dp).clickable { onOpenDetail(item) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, Modifier.size(width = 62.dp, height = 88.dp), showStatus = true)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.displayTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = c.primary, modifier = Modifier.size(13.dp))
                    Text(
                        listOfNotNull(item.nextEpisodeLabel(), time?.let { localizedTimeLabel(it, is24Hour) }).joinToString(" · "),
                        color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}
// Reusable rounded pill button
@Composable private fun HomeActionButton(modifier: Modifier = Modifier, label: String, icon: ImageVector, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.primaryContainer).clickable(onClick = onClick).padding(vertical = 15.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(19.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable private fun SectionTitle(title: String, action: String, click: () -> Unit) { val c = LocalKikoColors.current; Row(Modifier.fillMaxWidth().padding(top = 29.dp, bottom = 13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink); TextButton(onClick = click) { Text(action, fontWeight = FontWeight.Bold, color = c.primary) } } }
@Composable private fun ContinueCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = c.surface), elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(item) }) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, Modifier.size(width = 82.dp, height = 112.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(item.displayTitle(), style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatLabel(item)} · ${item.genre}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(13.dp))
                LinearProgressIndicator(progress = { if (item.total > 0) item.progress.toFloat() / item.total else .45f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(9.dp)), color = statusColor(item.status), trackColor = c.surfaceLow)
                Text(progressLabel(item), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }
}
// Pulsing loading placeholder box
@Composable private fun SkeletonBlock(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp)) {
    val c = LocalKikoColors.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.clip(shape).background(c.surfaceLow.copy(alpha = alpha)))
}
// Continue card placeholder
@Composable private fun ContinueSkeletonCard() {
    val c = LocalKikoColors.current
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = c.surface), elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(Modifier.size(width = 82.dp, height = 112.dp), shape = RoundedCornerShape(16.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(18.dp))
                SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.5f).height(12.dp))
                SkeletonBlock(Modifier.padding(top = 21.dp).fillMaxWidth().height(7.dp), shape = RoundedCornerShape(9.dp))
            }
        }
    }
}
// Pinterest-style snapshots layout
@Composable private fun SnapshotsGrid(snapshots: List<NewsSnapshot>, onOpenTopic: (Int, String) -> Unit) {
    val left = snapshots.filterIndexed { i, _ -> i % 2 == 0 }
    val right = snapshots.filterIndexed { i, _ -> i % 2 == 1 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            left.forEachIndexed { i, s -> SnapshotCard(s, tall = i % 2 == 0, onOpenTopic) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            right.forEachIndexed { i, s -> SnapshotCard(s, tall = i % 2 == 1, onOpenTopic) }
        }
    }
}
// Snapshot card title overlay
@Composable private fun SnapshotCard(snapshot: NewsSnapshot, tall: Boolean, onOpenTopic: (Int, String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (tall) 210.dp else 160.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenTopic(snapshot.topicId, snapshot.title) },
    ) {
        AsyncImage(model = snapshot.imageUrl, contentDescription = snapshot.title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = .35f),
                            1f to Color.Black.copy(alpha = .92f),
                        ),
                    ),
                ),
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                snapshot.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(shadow = Shadow(color = Color.Black.copy(alpha = .8f), offset = Offset(0f, 1f), blurRadius = 4f)),
            )
        }
    }
}
@Composable private fun MiniCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.width(118.dp).clickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().height(150.dp), showStatus = true)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(if (item.status == WatchStatus.Plan) "Saved for later" else progressLabel(item), color = c.primary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}
@Composable private fun Cover(item: MediaItem, modifier: Modifier = Modifier, showStatus: Boolean = false, statusAlignment: Alignment = Alignment.TopStart, overrideStatus: WatchStatus? = null) {
    val displayTitle = item.displayTitle()
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(item.color)), contentAlignment = Alignment.Center) {
        if (item.cover.isNotBlank()) AsyncImage(model = item.cover, contentDescription = displayTitle, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(displayTitle.take(1), fontWeight = FontWeight.Bold, fontSize = 36.sp, color = Color.White.copy(.85f))
        // Optional tracking mark — overrideStatus lets callers supply the real list status for
        // items that weren't sourced from the user's own list (item.inUserList would be false)
        if (showStatus) (overrideStatus ?: trackedBadgeStatus(item))?.let { CoverStatusMark(it, Modifier.align(statusAlignment).padding(6.dp)) }
    }
}
// All 5 states shown
private fun trackedBadgeStatus(item: MediaItem): WatchStatus? =
    if (item.inUserList) item.status else null
// Icon per tracking status
private fun WatchStatus.badgeIcon(): ImageVector = when (this) {
    WatchStatus.Watching, WatchStatus.Reading -> Icons.Default.PlayArrow
    WatchStatus.Completed -> Icons.Default.Check
    WatchStatus.OnHold -> Icons.Default.Pause
    WatchStatus.Dropped -> Icons.Default.Close
    WatchStatus.Plan -> Icons.Default.Bookmark
}
// Small color coded dot
@Composable private fun CoverStatusMark(status: WatchStatus, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape).background(statusColor(status)).border(1.5.dp, Color.White.copy(alpha = .9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(status.badgeIcon(), status.label, tint = Color.White, modifier = Modifier.size(13.dp)) }
}
private fun progressLabel(i: MediaItem) = if (i.progress == 0) i.status.label else "${i.progress}${if (i.total > 0) " of ${i.total}" else ""} ${if (i.type == MediaType.Anime) "episodes" else "chapters"}"
// Format field fallback
private fun formatLabel(i: MediaItem): String = i.format.ifBlank { if (i.type == MediaType.Anime) "Anime" else "Manga" }

// Translate status label
private fun normalizeFilterForType(filter: String, type: MediaType): String =
    if (filter == "Watching" || filter == "Reading") (if (type == MediaType.Anime) "Watching" else "Reading") else filter
// My List sort logic
private fun MediaItem.resolvedTitle(pref: TitleLanguage): String =
    if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) titleEnglish else title
private fun List<MediaItem>.sortedWithListSort(sort: ListSort, titleLanguage: TitleLanguage): List<MediaItem> = when (sort) {
    ListSort.Title -> sortedBy { it.resolvedTitle(titleLanguage).lowercase() }
    ListSort.Score -> sortedWith(compareByDescending<MediaItem> { it.myRating > 0 }.thenByDescending { it.myRating })
    ListSort.LastUpdated -> sortedWith(compareByDescending<MediaItem> { it.updatedAt.isNotBlank() }.thenByDescending { it.updatedAt })
    ListSort.StartDate -> sortedWith(compareByDescending<MediaItem> { it.watchStartDate.isNotBlank() }.thenByDescending { it.watchStartDate })
}
// Compact sort dropdown
@Composable private fun SortMenu(current: ListSort, onSelect: (ListSort) -> Unit) {
    val c = LocalKikoColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).clickable { open = true }.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sort, "Sort", tint = c.primary, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            ListSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.primary else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(s); open = false },
                )
            }
        }
    }
}
// Discover results sort dropdown
@Composable private fun DiscoverSortMenu(current: DiscoverSort, onSelect: (DiscoverSort) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).clickable { open = true }.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sort, "Sort", tint = c.primary, modifier = Modifier.size(16.dp))
            Text(current.label, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            DiscoverSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) c.primary else c.ink, fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(s); open = false },
                )
            }
        }
    }
}

@Composable private fun ListScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, onIncrement: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Search only on submit
    var submittedQuery by remember { mutableStateOf("") }
    val typeTab = vm.listTypeTab
    val effectiveFilter = normalizeFilterForType(vm.listFilter, typeTab)
    val filtered = vm.visibleItems
        .filter { it.type == typeTab && (effectiveFilter == "All" || it.status.label == effectiveFilter) && it.title.contains(submittedQuery, true) }
        .sortedWithListSort(vm.listSort, vm.titleLanguage)
    val isGrid = vm.listViewMode == ListViewMode.Grid
    // Restore list scroll position (shared between list/grid since both are single-column-index scroll states)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.listScrollIndex, initialFirstVisibleItemScrollOffset = vm.listScrollOffset)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = vm.listScrollIndex, initialFirstVisibleItemScrollOffset = vm.listScrollOffset)
    val openItem: (MediaItem) -> Unit = remember(onOpenDetail, isGrid) {
        {
                item ->
            if (isGrid) vm.saveListScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            else vm.saveListScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            onOpenDetail(item)
        }
    }
    val header: @Composable () -> Unit = {
        AppHeader("My list")
        if (vm.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), color = c.primary, trackColor = c.surfaceLow)
        SearchField(query, { query = it }, "Search your list", onSearch = { submittedQuery = query }, onClear = { query = ""; submittedQuery = "" })
        // Reset scroll on change
        TypeToggle(typeTab) { vm.selectListTypeTab(it) }
        FilterRow(effectiveFilter, { vm.setListFilter(context, it) }, typeTab)
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} titles" + if (vm.loading) " · syncing…" else "", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.listViewMode) { vm.setListViewMode(context, it) }
                SortMenu(vm.listSort) { vm.setListSort(context, it) }
            }
        }
    }
    if (isGrid) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
            items(filtered, key = { it.id }) { item -> ListGridCard(item, openItem, onIncrement) }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item { header() }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                ListRow(it, openItem, onIncrement, showType = false)
                if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.muted.copy(alpha = .15f))
            }
            if (filtered.isEmpty()) item { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}
// List/grid switcher
@Composable private fun ListViewModeToggle(current: ListViewMode, onSelect: (ListViewMode) -> Unit) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .clickable { onSelect(if (current == ListViewMode.List) ListViewMode.Grid else ListViewMode.List) }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (current == ListViewMode.List) Icons.Default.GridView else Icons.Default.ViewList,
            contentDescription = if (current == ListViewMode.List) "Switch to grid view" else "Switch to list view",
            tint = c.primary, modifier = Modifier.size(16.dp),
        )
    }
}
// Compact grid tile — cover, title, and the same progress bar as the list row
@Composable private fun ListGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, onIncrement: ((MediaItem) -> Unit)? = null) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().clickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        // Fixed to 2 lines so every tile's progress bar lines up regardless of title length
        Text(
            item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, color = c.ink,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        if (onIncrement != null && item.total > 0) {
            LinearProgressIndicator(progress = { item.progress.toFloat() / item.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(4.dp)), color = statusColor(item.status), trackColor = c.surfaceLow)
        }
        Text(progressLabel(item), color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}
// Anime/Manga segmented switch
@Composable private fun TypeToggle(current: MediaType, trackColor: Color = LocalKikoColors.current.surface, set: (MediaType) -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(trackColor).padding(4.dp)) {
        MediaType.entries.forEach { t ->
            val selected = current == t
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (selected) c.primary else Color.Transparent).clickable { set(t) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (t == MediaType.Anime) "Anime" else "Manga", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) c.onPrimary else c.muted)
            }
        }
    }
}
@Composable private fun SearchField(value: String, change: (String) -> Unit, hint: String, onSearch: (() -> Unit)? = null, onClear: (() -> Unit)? = null) {
    val c = LocalKikoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = change, placeholder = { Text(hint, color = c.muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = c.muted) },
        trailingIcon = {
            // Show clear when non-empty
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = {
                        (onClear ?: { change("") })()
                        // Clear field, drop focus
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    modifier = Modifier.size(32.dp),
                ) { Icon(Icons.Default.Close, "Clear search", tint = c.muted, modifier = Modifier.size(16.dp)) }
            }
        },
        singleLine = true, shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(); keyboard?.hide() }),
        modifier = Modifier.fillMaxWidth(),
    )
}
@Composable private fun FilterRow(current: String, set: (String) -> Unit, type: MediaType) {
    val c = LocalKikoColors.current
    val progressLabel = if (type == MediaType.Anime) "Watching" else "Reading"
    val labels = listOf("All", progressLabel, "Plan to Watch", "Completed", "On Hold", "Dropped")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
        items(labels) { label -> FilterChip(selected = current == label, onClick = { set(label) }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
    }
}
@Composable private fun ListRow(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, onIncrement: ((MediaItem) -> Unit)? = null, showType: Boolean = true) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().clickable { onOpenDetail(item) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(item, Modifier.size(width = 84.dp, height = 118.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (showType) "${item.type} · ${item.genre}" else item.genre, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            if (onIncrement != null && item.total > 0) {
                LinearProgressIndicator(progress = { item.progress.toFloat() / item.total }, modifier = Modifier.fillMaxWidth(0.75f).padding(top = 9.dp).height(4.dp).clip(RoundedCornerShape(4.dp)), color = statusColor(item.status), trackColor = c.surfaceLow)
            }
            Text(progressLabel(item), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            item.nextEpisodeLabel()?.let { label ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = c.primary, modifier = Modifier.size(12.dp))
                    Text(label, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (onIncrement != null) {
            val atMax = item.total > 0 && item.progress >= item.total
            // Filled pill tap target
            FilledTonalButton(
                onClick = { val next = (item.progress + 1).let { p -> if (item.total > 0) minOf(p, item.total) else p }; onIncrement(item.copy(progress = next)) },
                enabled = !atMax,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.primaryContainer, contentColor = c.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            ) { Text("+1", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

// Ranking chart screen
@Composable private fun RankingScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(vm.rankingType, vm.rankingSort) { vm.loadRanking(context, vm.rankingType, vm.rankingSort) }
    val sorts = if (vm.rankingType == MediaType.Anime) RankingSort.entries.toList() else RankingSort.entries.filterNot { it == RankingSort.Upcoming }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                Text("Ranking", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
            }
            TypeToggle(vm.rankingType) { vm.loadRanking(context, it, vm.rankingSort) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
                items(sorts) { sort -> FilterChip(selected = vm.rankingSort == sort, onClick = { vm.loadRanking(context, vm.rankingType, sort) }, label = { Text(sort.label) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }
            if (vm.rankingLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
            vm.rankingError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
        }
        itemsIndexed(vm.visibleRankingResults, key = { _, it -> it.id }) { index, it -> RankingRow(index + 1, it, onOpenDetail) }
        if (!vm.rankingLoading && vm.visibleRankingResults.isEmpty() && vm.rankingError == null) {
            item { Text("No results.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}
// Ranking chart row
@Composable private fun RankingRow(position: Int, item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(19.dp)).background(c.surface).clickable { onOpenDetail(item) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { Text("#$position", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary) }
        Cover(item, Modifier.size(width = 54.dp, height = 76.dp), showStatus = true)
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.type} · ${item.genre}", color = c.muted, fontSize = 12.sp)
        }
        if (item.score > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                Text("%.2f".format(item.score), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp))
            }
        } else if (item.listUsers > 0) {
            Text(formatCount(item.listUsers), color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
// Seasonal chart screen
@Composable private fun SeasonalScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val handleBack = { vm.resetSeasonal(); onBack() }
    BackHandler(onBack = handleBack)
    // Fetch only on entry
    LaunchedEffect(Unit) { if (vm.seasonalResults.isEmpty()) vm.loadSeasonal(context, vm.seasonalYear, vm.seasonalSeason, vm.seasonalSort, vm.seasonalContinuingOnly) }
    var browseOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = vm.seasonalScrollIndex, initialFirstVisibleItemScrollOffset = vm.seasonalScrollOffset)
    // Save position before navigating
    val openTitle: (MediaItem) -> Unit = remember(onOpenDetail) {
        { item -> vm.saveSeasonalScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset); onOpenDetail(item) }
    }

    // Load more near end
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to gridState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 6) vm.loadMoreSeasonal(context) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = handleBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Seasonal Chart", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${vm.seasonalSeason.label} ${vm.seasonalYear}", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        }
                    }
                    if (vm.seasonalLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = c.primary, trackColor = c.surfaceLow)
                    vm.seasonalError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                    Spacer(Modifier.height(14.dp))
                }
            }
            items(vm.visibleSeasonalResults, key = { it.id }) { SeasonalGridCard(it, openTitle) }
            if (!vm.seasonalLoading && vm.visibleSeasonalResults.isEmpty() && vm.seasonalError == null) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles for this season.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            if (vm.seasonalLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { browseOpen = true },
            containerColor = c.primary,
            contentColor = c.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(vm.seasonalSeason.icon, null) },
            text = { Text("${vm.seasonalSeason.label} ${vm.seasonalYear}", fontWeight = FontWeight.Bold) },
        )
    }
    if (browseOpen) SeasonalBrowseSheet(vm, context, onDismiss = { browseOpen = false })
}
// Weekly release schedule screen
@Composable private fun ScheduleScreen(vm: LibraryViewModel, initialDay: java.time.DayOfWeek, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    var selectedDay by remember(initialDay) { mutableStateOf(initialDay) }
    val byDay = remember(vm.visibleDiscoverNewSeason) {
        vm.visibleDiscoverNewSeason.mapNotNull { item -> item.localBroadcast()?.let { (day, time) -> Triple(item, day, time) } }
    }
    val dayItems = byDay.filter { it.second == selectedDay }.sortedBy { it.third }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Release Schedule", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp)) {
            items(java.time.DayOfWeek.values().toList()) { day ->
                val label = day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                FilterChip(
                    selected = selectedDay == day,
                    onClick = { selectedDay = day },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary),
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            if (dayItems.isEmpty()) {
                item { Text("No releases on this day.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            items(dayItems, key = { it.first.id }) { (item, _, time) -> ScheduleRow(item, time, onOpenDetail) }
        }
    }
}
// Schedule screen row
@Composable private fun ScheduleRow(item: MediaItem, time: java.time.LocalTime, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val is24Hour = systemIs24Hour()
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpenDetail(item) },
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, Modifier.size(width = 58.dp, height = 82.dp), showStatus = true)
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatLabel(item)} · ${item.genre}", color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                Row(
                    Modifier.padding(top = 9.dp).clip(RoundedCornerShape(10.dp)).background(c.primaryContainer).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Schedule, null, tint = c.primary, modifier = Modifier.size(12.dp))
                    Text(localizedTimeLabel(time, is24Hour), color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
    }
}
// Seasonal browse filter sheet
@Composable private fun SeasonalBrowseSheet(vm: LibraryViewModel, context: Context, onDismiss: () -> Unit) {
    val c = LocalKikoColors.current
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val years = (thisYear + 1 downTo 2000).toList()
    var pendingYear by remember { mutableStateOf(vm.seasonalYear) }
    var pendingSeason by remember { mutableStateOf(vm.seasonalSeason) }
    var pendingSort by remember { mutableStateOf(vm.seasonalSort) }
    var pendingContinuing by remember { mutableStateOf(vm.seasonalContinuingOnly) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Seasonal Chart", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Browse a season", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))

            // Compact season stepper control
            Text("${pendingSeason.label} $pendingYear", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val (py, ps) = stepSeason(pendingYear, pendingSeason, forward = false)
                val (ny, ns) = stepSeason(pendingYear, pendingSeason, forward = true)
                IconButton(onClick = { pendingYear = py; pendingSeason = ps }) { Icon(Icons.Default.ChevronLeft, "Previous season", tint = c.ink) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SeasonName.entries.forEach { s -> SeasonIconButton(selected = s == pendingSeason, season = s) { pendingSeason = s } }
                }
                IconButton(onClick = { pendingYear = ny; pendingSeason = ns }) { Icon(Icons.Default.ChevronRight, "Next season", tint = c.ink) }
            }

            Text("Year", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(years) { y -> FilterChip(selected = y == pendingYear, onClick = { pendingYear = y }, label = { Text(y.toString()) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }

            // Consistent filter chip style
            Text("Sort by", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeasonalSort.entries.forEach { s ->
                    FilterChip(
                        selected = pendingSort == s,
                        onClick = { pendingSort = s },
                        label = { Text(s.label, maxLines = 1, softWrap = false) },
                        leadingIcon = { Icon(seasonalSortIcon(s), null, modifier = Modifier.size(15.dp)) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, iconColor = c.muted, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary, selectedLeadingIconColor = c.onPrimary),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Continuing titles filter toggle
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)
                    .clickable { pendingContinuing = !pendingContinuing }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Autorenew, null, tint = c.primary, modifier = Modifier.size(19.dp))
                Text("Still airing from before", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.weight(1f).padding(start = 11.dp))
                Switch(
                    checked = pendingContinuing,
                    onCheckedChange = { pendingContinuing = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary),
                )
            }

            Button(
                onClick = { vm.loadSeasonal(context, pendingYear, pendingSeason, pendingSort, pendingContinuing); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            ) { Text("Apply", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}
private fun seasonalSortIcon(s: SeasonalSort) = when (s) { SeasonalSort.Members -> Icons.Default.Group; SeasonalSort.Score -> Icons.Default.Star }
// Seasonal chart grid tile
@Composable private fun SeasonalGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().clickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().aspectRatio(0.72f), showStatus = true)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        if (item.score > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                Text("%.2f".format(item.score), color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
            }
        }
        if (item.listUsers > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Default.Group, null, tint = c.muted, modifier = Modifier.size(11.dp))
                Text(formatCount(item.listUsers), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
            }
        }
    }
}
@Composable private fun SeasonIconButton(selected: Boolean, season: SeasonName, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Box(Modifier.size(46.dp).clip(CircleShape).background(if (selected) c.primary else Color.Transparent).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(season.icon, season.label, tint = if (selected) c.onPrimary else c.muted, modifier = Modifier.size(21.dp))
    }
}

// Discover section
@Composable private fun DiscoverScreen(
    vm: LibraryViewModel,
    onOpenDetail: (MediaItem) -> Unit,
    onRanking: () -> Unit,
    onSeasonal: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(vm.signedIn) { vm.loadDiscoverBrowse(context) }
    AnimatedContent(
        vm.discoverMode,
        transitionSpec = { if (targetState == DiscoverMode.Results) PushEnter togetherWith PushExit else PopEnter togetherWith PopExit },
        label = "discover-mode",
    ) { mode ->
        if (mode == DiscoverMode.Results) DiscoverResultsScreen(vm, context, onOpenDetail)
        else DiscoverBrowseScreen(vm, context, onOpenDetail, onRanking, onSeasonal)
    }
}
// Discover landing page
@Composable private fun DiscoverBrowseScreen(
    vm: LibraryViewModel,
    context: Context,
    onOpenDetail: (MediaItem) -> Unit,
    onRanking: () -> Unit,
    onSeasonal: () -> Unit
) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf("") }
    var filterSheetOpen by remember { mutableStateOf(false) }
    // Map MAL id -> the user's tracked status, so browse rows (which come straight from
    // Tenrai/MAL search results, not the user's own list) can still show the status badge
    val myListStatus = remember(vm.items) { vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { it to li.status } }.toMap() }

    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            AppHeader("Discover")
            Spacer(Modifier.height(17.dp))

            // Search bar and filter
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SearchField(
                        value = query,
                        change = { query = it },
                        hint = "Search in MAL",
                        onSearch = { if (query.isNotBlank() || vm.discoverFilters.isActive()) vm.runDiscoverSearch(context, query, vm.discoverTypeFilter) }
                    )
                }
                FilterIconButton(active = vm.discoverFilters.isActive(), onClick = { filterSheetOpen = true }, modifier = Modifier.padding(start = 10.dp))
            }
            if (filterSheetOpen) AdvancedFilterSheet(vm.discoverFilters, type = "All", onDismiss = { filterSheetOpen = false }, onApply = { filterSheetOpen = false; vm.runDiscoverSearch(context, query, resolvedDiscoverType(it.format, vm.discoverTypeFilter), it) })

            Spacer(Modifier.height(14.dp))

            // Ranking and Seasonal buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DiscoverActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.TrendingUp,
                    label = "Rankings",
                    onClick = onRanking
                )
                DiscoverActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.DateRange,
                    label = "Seasonal",
                    onClick = onSeasonal
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        if (!vm.signedIn) {
            item {
                Text(
                    "Sign in from Profile to browse MyAnimeList",
                    color = c.muted,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // New this season row
            if (vm.visibleDiscoverNewSeason.isNotEmpty()) {
                item {
                    SectionTitle("New this season", "See all", onSeasonal)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        // Cap row at 7
                        items(vm.visibleDiscoverNewSeason.take(7), key = { it.id }) { item ->
                            BrowseCard(item, onOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it] })
                        }
                    }
                }
            }

            // Top 10 upcoming row
            if (vm.visibleDiscoverUpcoming.isNotEmpty()) {
                item {
                    SectionTitle("Top 10 upcoming", "", {})
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(vm.visibleDiscoverUpcoming, key = { it.id }) { item ->
                            BrowseCard(item, onOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it] })
                        }
                    }
                }
            }

            // Recommendations row
            if (vm.visibleRecommendations.isNotEmpty()) {
                item {
                    SectionTitle("You might like", "", {})
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(vm.visibleRecommendations, key = { it.id }) { item ->
                            BrowseCard(item, onOpenDetail, myStatus = item.id.toIntOrNull()?.let { myListStatus[it] })
                        }
                    }
                }
            }

            // Loading and error states
            if (vm.discoverBrowseLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = c.primary,
                        trackColor = c.surfaceLow
                    )
                }
            }
            vm.discoverBrowseError?.let { error ->
                item {
                    Text(error, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

// Ranking/Seasonal action card
@Composable private fun DiscoverActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val c = LocalKikoColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink)
        }
    }
}

// Discover search results page
@Composable private fun DiscoverResultsScreen(vm: LibraryViewModel, context: Context, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf(vm.discoverQuery) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = vm::exitDiscoverSearch)
    // Restore results scroll position
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.discoverScrollIndex, initialFirstVisibleItemScrollOffset = vm.discoverScrollOffset)
    val openResult: (MediaItem) -> Unit = { result ->
        vm.saveDiscoverScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        vm.openDiscoverDetail(context, result, onOpenDetail)
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::exitDiscoverSearch, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back to Discover", tint = c.ink) }
                Text("Search results", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SearchField(query, { query = it }, "Search in MAL", onSearch = { vm.runDiscoverSearch(context, query, vm.discoverTypeFilter) })
                }
                FilterIconButton(active = vm.discoverFilters.isActive(), onClick = { filterSheetOpen = true }, modifier = Modifier.padding(start = 10.dp))
            }
            // Fixes type/format mismatch
            if (filterSheetOpen) AdvancedFilterSheet(vm.discoverFilters, type = vm.discoverTypeFilter, onDismiss = { filterSheetOpen = false }, onApply = { filterSheetOpen = false; vm.runDiscoverSearch(context, query, resolvedDiscoverType(it.format, vm.discoverTypeFilter), it) })
            Row(Modifier.fillMaxWidth().padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(listOf("Anime", "Manga")) { label -> FilterChip(selected = vm.discoverTypeFilter == label, onClick = { vm.runDiscoverSearch(context, query, label) }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
                }
                DiscoverSortMenu(current = vm.discoverSort, onSelect = vm::selectDiscoverSort, modifier = Modifier.padding(start = 8.dp))
            }
            if (vm.discoverSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
            vm.discoverError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
        }
        if (!vm.discoverSearching && vm.visibleDiscoverResults.isEmpty() && vm.discoverError == null) {
            val emptyMessage = if (vm.discoverQuery.isBlank()) "No results match your filters." else "No results for \"${vm.discoverQuery}\"."
            item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
        }
        itemsIndexed(vm.visibleDiscoverResults, key = { _, it -> it.id }) { index, result ->
            SearchResultRow(result, loading = vm.discoverDetailLoadingId == result.id) { openResult(result) }
            if (index < vm.visibleDiscoverResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.muted.copy(alpha = .15f))
        }
    }
}
// Filters button with indicator
@Composable private fun FilterIconButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (active) c.primary else c.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Tune, "Advanced filters", tint = if (active) c.onPrimary else c.ink) }
}
// Collapsible multi-select facet
@Composable private fun ExpandableFilterSection(title: String, options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    val c = LocalKikoColors.current
    var expanded by remember(title) { mutableStateOf(selected.isNotEmpty()) }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (selected.isNotEmpty()) {
                    Box(Modifier.padding(start = 8.dp).clip(CircleShape).background(c.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(selected.size.toString(), color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse $title" else "Expand $title", tint = c.muted)
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 9.dp)) {
                options.forEach { o -> FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(o) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }
        }
    }
}
// Discover advanced filters sheet
@Composable private fun AdvancedFilterSheet(current: DiscoverFilters, type: String, onDismiss: () -> Unit, onApply: (DiscoverFilters) -> Unit) {
    val c = LocalKikoColors.current
    // Split combined genre facets
    var genres by remember { mutableStateOf(current.genres.filter { it !in CommonExplicitGenres }.toSet()) }
    var explicitGenres by remember { mutableStateOf(current.genres.filter { it in CommonExplicitGenres }.toSet()) }
    var themes by remember { mutableStateOf(current.themes) }
    var demographics by remember { mutableStateOf(current.demographics) }
    var studio by remember { mutableStateOf(current.studio) }
    var source by remember { mutableStateOf(current.source) }
    var year by remember { mutableStateOf(current.year) }
    var season by remember { mutableStateOf(current.season) }
    var rating by remember { mutableStateOf(current.rating) }
    var format by remember { mutableStateOf(current.format) }
    var airingStatus by remember { mutableStateOf(current.airingStatus) }
    val airingOptions = listOf("Ongoing", "Finished", "Upcoming")
    val formatOptions = when (type) { "Anime" -> CommonAnimeFormats; "Manga" -> CommonMangaFormats; else -> CommonAnimeFormats + CommonMangaFormats }
    // Skip partially-expanded sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            Text("Discover", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Advanced filters", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))

            ExpandableFilterSection("Genre", CommonGenres, genres, onToggle = { g -> genres = if (g in genres) genres - g else genres + g })
            // Separate explicit genre section
            ExpandableFilterSection("Explicit genre", CommonExplicitGenres, explicitGenres, onToggle = { g -> explicitGenres = if (g in explicitGenres) explicitGenres - g else explicitGenres + g })
            // Separate themes section
            ExpandableFilterSection("Themes", CommonThemes, themes, onToggle = { t -> themes = if (t in themes) themes - t else themes + t })
            ExpandableFilterSection("Demographics", CommonDemographics, demographics, onToggle = { d -> demographics = if (d in demographics) demographics - d else demographics + d })

            Text("Type", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formatOptions.forEach { f -> FilterChip(selected = format == f, onClick = { format = if (format == f) "" else f }, label = { Text(f) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }

            Text("Status", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                airingOptions.forEach { s -> FilterChip(selected = airingStatus == s, onClick = { airingStatus = if (airingStatus == s) "" else s }, label = { Text(s) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }

            Text("Studio", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            OutlinedTextField(
                value = studio, onValueChange = { studio = it }, placeholder = { Text("e.g. Madhouse", color = c.muted) }, singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Source", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CommonSources) { s -> FilterChip(selected = source == s, onClick = { source = if (source == s) "" else s }, label = { Text(s) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Year", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 9.dp))
                    OutlinedTextField(
                        value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) }, placeholder = { Text("e.g. 2023", color = c.muted) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Season", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SeasonName.entries.forEach { s -> SeasonIconButton(selected = s == season, season = s) { season = if (season == s) null else s } }
                    }
                }
            }

            Text("Rating", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommonRatings.forEach { r -> FilterChip(selected = rating == r, onClick = { rating = if (rating == r) "" else r }, label = { Text(r, maxLines = 1) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { genres = emptySet(); explicitGenres = emptySet(); themes = emptySet(); demographics = emptySet(); studio = ""; source = ""; year = ""; season = null; rating = ""; format = ""; airingStatus = "" },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset", color = c.muted, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { onApply(DiscoverFilters(genres + explicitGenres, themes, demographics, studio.trim(), source, year, season, rating, format, airingStatus)) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    modifier = Modifier.weight(2f),
                ) { Text("Apply filters", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
// Browse row cover card
@Composable private fun BrowseCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, subtitle: String? = null, myStatus: WatchStatus? = null) {
    val c = LocalKikoColors.current
    Column(Modifier.width(118.dp).clickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().height(150.dp), showStatus = true, overrideStatus = myStatus)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(subtitle ?: (if (item.score > 0) "★ ${"%.1f".format(item.score)}" else item.genre), color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
// Discover search result row
@Composable private fun SearchResultRow(item: MediaItem, loading: Boolean, onTap: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !loading) { onTap() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(84.dp).height(118.dp)) {
            Cover(item, Modifier.fillMaxSize(), showStatus = true)
            if (item.score > 0) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                    Text("%.2f".format(item.score), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.format.isNotBlank()) Pill(item.format, c.primaryContainer, c.primary)
                episodeAndYear(item).takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 9.dp))
                }
            }
            if (item.listUsers > 0) {
                Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Group, null, tint = c.muted, modifier = Modifier.size(13.dp))
                    Text(formatExact(item.listUsers), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
    }
}
// Format episode/season label
private fun episodeAndYear(item: MediaItem): String {
    val unit = if (item.type == MediaType.Anime) "ep" else "ch"
    val episodes = if (item.total > 0) "${item.total} $unit" else null
    val year = seasonYear(item.season, item.startDate).takeIf { it.isNotBlank() }
    return listOfNotNull(episodes, year).joinToString(", ")
}
// Comma-format member count
private fun formatExact(n: Int): String = "%,d".format(n)

// Forums tab structure
@Composable private fun ForumsScreen(vm: LibraryViewModel, onOpenTopic: (Int, String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(vm.signedIn) { vm.loadForumBoards(context) }
    AnimatedContent(
        vm.forumMode,
        transitionSpec = { if (targetState == ForumMode.Topics) PushEnter togetherWith PushExit else PopEnter togetherWith PopExit },
        label = "forum-mode",
    ) { mode ->
        if (mode == ForumMode.Topics) ForumTopicsScreen(vm, context, onOpenTopic)
        else ForumBoardsScreen(vm, context)
    }
}
// Forums landing page
@Composable private fun ForumBoardsScreen(vm: LibraryViewModel, context: Context) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf("") }
    // Restore board list scroll
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.forumBoardsScrollIndex, initialFirstVisibleItemScrollOffset = vm.forumBoardsScrollOffset)
    val saveScroll = { vm.saveForumBoardsScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            AppHeader("Forums")
            // Search hands off topics
            SearchField(query, { query = it }, "Search topics", onSearch = { if (query.isNotBlank()) { saveScroll(); vm.runForumSearch(context, query) } })
        }
        if (!vm.signedIn) {
            item { Text("Sign in from Profile to browse the MAL forums", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
        } else {
            item {
                if (vm.forumBoardsLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.forumBoardsError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            // Grouped category board card
            vm.forumCategories.forEach { category ->
                item { Text(category.title.uppercase(), color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp)) }
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            category.boards.forEachIndexed { index, board ->
                                ForumBoardRow(board) { saveScroll(); vm.openForumBoard(context, board) }
                                if (index < category.boards.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 66.dp), thickness = 1.dp, color = c.muted.copy(alpha = .12f))
                            }
                        }
                    }
                }
            }
        }
    }
}
// Subboard count pill
@Composable private fun ForumBoardRow(board: ForumBoard, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Forum, null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(board.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
            if (board.description.isNotBlank()) Text(board.description, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (board.subboards.isNotEmpty()) {
            Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(c.surfaceLow).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text("${board.subboards.size} boards", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = c.muted)
    }
}
// Back-to-top floating button
@Composable private fun GoToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FloatingActionButton(onClick = onClick, containerColor = c.primary, contentColor = c.onPrimary, modifier = Modifier.size(46.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Back to top")
        }
    }
}
// Shared topic list page
@Composable private fun ForumTopicsScreen(vm: LibraryViewModel, context: Context, onOpenTopic: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    val headerTitle = vm.forumBoardTitle.ifBlank { "Search results" }
    BackHandler(onBack = vm::exitForumTopics)
    // Restore topics scroll position
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.forumTopicsScrollIndex, initialFirstVisibleItemScrollOffset = vm.forumTopicsScrollOffset)
    val openTopic: (ForumTopic) -> Unit = { topic ->
        vm.saveForumTopicsScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onOpenTopic(topic.id, topic.title)
    }
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    // Load more forum topics
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, total) -> if (lastVisible != null && total > 0 && lastVisible >= total - 6) vm.loadMoreForumTopics(context) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::exitForumTopics, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back to Forums", tint = c.ink) }
                    Text(headerTitle, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
                if (vm.forumSubboards.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 15.dp)) {
                        item { FilterChip(selected = vm.forumSubboardId == null, onClick = { vm.openForumSubboard(context, null) }, label = { Text("All") }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
                        items(vm.forumSubboards, key = { it.id }) { sub -> FilterChip(selected = vm.forumSubboardId == sub.id, onClick = { vm.openForumSubboard(context, sub.id) }, label = { Text(sub.title) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
                    }
                }
                if (vm.forumTopicsLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.forumTopicsError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (!vm.forumTopicsLoading && vm.forumTopics.isEmpty() && vm.forumTopicsError == null) {
                item { Text("No topics found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center) }
            }
            itemsIndexed(vm.forumTopics, key = { _, it -> it.id }) { index, topic ->
                if (vm.forumIsNewsBoard) NewsTopicRow(topic) { openTopic(topic) } else ForumTopicRow(topic) { openTopic(topic) }
                if (index < vm.forumTopics.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.muted.copy(alpha = .12f))
            }
            if (vm.forumLoadingMore) {
                item { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) } }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// Forum topic list row
@Composable private fun ForumTopicRow(topic: ForumTopic, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        if (topic.author.avatar.isNotBlank()) {
            AsyncImage(model = topic.author.avatar, contentDescription = topic.author.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape).background(c.warm))
        } else {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.warm), contentAlignment = Alignment.Center) {
                Text(topic.author.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (topic.isLocked) Icon(Icons.Default.Lock, null, tint = c.muted, modifier = Modifier.size(13.dp).padding(end = 4.dp))
                Text(topic.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("by ${topic.author.name.ifBlank { "Unknown" }} · ${formatForumDate(topic.createdAt)}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            if (topic.lastPostAuthor.name.isNotBlank()) {
                Text("Last reply by ${topic.lastPostAuthor.name} · ${formatForumDate(topic.lastPostAt)}", color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.Default.ChatBubbleOutline, null, tint = c.muted, modifier = Modifier.size(13.dp))
            Text("${topic.postCount}", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
// News Discussion topic row
@Composable private fun NewsTopicRow(topic: ForumTopic, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(width = 84.dp, height = 118.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceLow), contentAlignment = Alignment.Center) {
            if (topic.imageUrl != null) {
                AsyncImage(model = topic.imageUrl, contentDescription = topic.title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Icon(Icons.Default.Newspaper, null, tint = c.muted, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (topic.isLocked) Icon(Icons.Default.Lock, null, tint = c.muted, modifier = Modifier.size(12.dp).padding(end = 4.dp))
                Text(topic.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text("by ${topic.author.name.ifBlank { "Unknown" }} · ${formatForumDate(topic.createdAt)}", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (topic.lastPostAuthor.name.isNotBlank()) {
                    Text("Last reply by ${topic.lastPostAuthor.name} · ${formatForumDate(topic.lastPostAt)}", color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = c.muted, modifier = Modifier.size(13.dp))
                    Text("${topic.postCount}", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
// Single topic posts screen
@Composable private fun ForumTopicScreen(vm: LibraryViewModel, topicId: Int, title: String, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember(topicId) { mutableStateOf<List<ForumPost>>(emptyList()) }
    var poll by remember(topicId) { mutableStateOf<ForumPoll?>(null) }
    var loading by remember(topicId) { mutableStateOf(true) }
    var loadingMore by remember(topicId) { mutableStateOf(false) }
    var hasMore by remember(topicId) { mutableStateOf(false) }
    var error by remember(topicId) { mutableStateOf<String?>(null) }
    LaunchedEffect(topicId) {
        loading = true
        runCatching { MalApi(context).forumTopic(topicId) }
            .onSuccess { posts = it.posts; poll = it.poll; hasMore = it.hasMore; error = null }
            .onFailure { error = it.message ?: "Could not load topic" }
        loading = false
    }
    // Restore per-topic scroll position
    val (initialIndex, initialOffset) = remember(topicId) { vm.forumTopicScrollFor(topicId) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex, initialFirstVisibleItemScrollOffset = initialOffset)
    val goBack = { vm.saveForumTopicScroll(topicId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onBack() }
    BackHandler(onBack = goBack)
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = goBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    // Open topic in browser
                    IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/forum/?topicid=$topicId")) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) {
                        Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                }
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                poll?.let { ForumPollCard(it, Modifier.padding(top = 6.dp, bottom = 6.dp)) }
            }
            itemsIndexed(posts, key = { _, p -> p.id }) { index, post ->
                ForumPostCard(post, isOriginalPost = post.number == 1)
                if (index < posts.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = c.muted.copy(alpha = .12f))
            }
            if (hasMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) {
                            CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        } else {
                            TextButton(onClick = {
                                scope.launch {
                                    loadingMore = true
                                    runCatching { MalApi(context).forumTopic(topicId, offset = posts.size) }
                                        .onSuccess { posts = posts + it.posts; hasMore = it.hasMore }
                                        .onFailure { hasMore = false }
                                    loadingMore = false
                                }
                            }) { Text("Load more replies", color = c.primary, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        GoToTopButton(
            visible = showGoToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }
}
// Full review readout page
@Composable private fun ReviewScreen(entry: ReviewEntry, itemTitle: String, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text(itemTitle, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
            if (entry.url.isNotBlank()) {
                // Open review in browser
                IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(entry.url)) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) {
                    Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.userImage.isNotBlank()) {
                AsyncImage(model = entry.userImage, contentDescription = entry.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape).background(c.warm))
            } else {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.warm), contentAlignment = Alignment.Center) {
                    Text(entry.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                }
            }
            Text(entry.username, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
            if (entry.score > 0) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                Text(entry.score.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (entry.isSpoiler) Text("Contains spoilers", color = c.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        if (entry.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 14.dp)) {
                entry.tags.forEach { tag ->
                    val verdict = tag in ReviewVerdictTags
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (verdict) Icon(Icons.Default.Star, null, tint = verdictColor(tag, c), modifier = Modifier.size(13.dp))
                        Text(
                            tag, color = if (verdict) verdictColor(tag, c) else c.muted, fontWeight = if (verdict) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp, modifier = Modifier.padding(start = if (verdict) 4.dp else 0.dp),
                        )
                    }
                }
            }
        }
        SelectionContainer {
            Text(
                entry.review, color = c.ink, fontSize = 14.sp, lineHeight = 22.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 28.dp),
            )
        }
    }
}
// BBCode tag renderer
private sealed class ForumBlock {
    data class Paragraph(val text: AnnotatedString, val center: Boolean = false) : ForumBlock()
    // Tenor flag needs resolving
    data class ImageBlock(val url: String, val resolveTenor: Boolean = false) : ForumBlock()
    data class ListBlock(val items: List<AnnotatedString>, val ordered: Boolean) : ForumBlock()
    // Quote holds nested blocks
    data class Quote(val blocks: List<ForumBlock>) : ForumBlock()
}
private sealed class BbToken {
    data class Text(val text: String) : BbToken()
    data class Open(val name: String, val attr: String?) : BbToken()
    data class Close(val name: String) : BbToken()
}
private val bbTagRegex = Regex("""\[(/?)([a-zA-Z*]+)(=[^\]]*)?\]""")
// Match img attribute forms
private val bbBlockRegex = Regex("""\[(img|list|quote|center)(?:[^\]]*)?\](.*?)\[/\1\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private fun tokenizeBb(raw: String): List<BbToken> {
    val tokens = mutableListOf<BbToken>()
    var last = 0
    for (m in bbTagRegex.findAll(raw)) {
        if (m.range.first > last) tokens += BbToken.Text(raw.substring(last, m.range.first))
        val closing = m.groupValues[1] == "/"
        val name = m.groupValues[2].lowercase()
        val attr = m.groupValues[3].removePrefix("=").ifBlank { null }
        tokens += if (closing) BbToken.Close(name) else BbToken.Open(name, attr)
        last = m.range.last + 1
    }
    if (last < raw.length) tokens += BbToken.Text(raw.substring(last))
    return tokens
}
// Recursive nested tag parsing
private fun AnnotatedString.Builder.appendBb(tokens: List<BbToken>, from: Int, stopAt: String?, linkColor: Color): Int {
    var i = from
    while (i < tokens.size) {
        when (val t = tokens[i]) {
            is BbToken.Text -> { append(t.text); i++ }
            is BbToken.Close -> { i++; if (t.name == stopAt) return i }
            is BbToken.Open -> {
                i = when (t.name) {
                    "b" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendBb(tokens, i + 1, "b", linkColor) }
                    "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendBb(tokens, i + 1, "i", linkColor) }
                    "u" -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { appendBb(tokens, i + 1, "u", linkColor) }
                    "s", "strike" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendBb(tokens, i + 1, t.name, linkColor) }
                    "url" -> {
                        // URL from tag attribute
                        val href = t.attr ?: (tokens.getOrNull(i + 1) as? BbToken.Text)?.text?.trim()
                        if (href != null) pushStringAnnotation("URL", href)
                        val next = withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { appendBb(tokens, i + 1, "url", linkColor) }
                        if (href != null) pop()
                        next
                    }
                    else -> appendBb(tokens, i + 1, t.name, linkColor) // unrecognized tag: drop the markup, keep its text
                }
            }
        }
    }
    return i
}
private fun inlineAnnotated(raw: String, linkColor: Color): AnnotatedString = buildAnnotatedString { appendBb(tokenizeBb(raw), 0, null, linkColor) }

private fun paragraphsFrom(text: String, linkColor: Color, center: Boolean = false): List<ForumBlock> {
    val trimmed = text.trim('\n', '\r')
    if (trimmed.isBlank()) return emptyList()
    return trimmed.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }.map { ForumBlock.Paragraph(inlineAnnotated(it, linkColor), center) }
}
// Convert stray HTML fragments
private val brTagRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val htmlEntityRegex = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")
private val namedHtmlEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "darr" to "↓", "uarr" to "↑", "larr" to "←", "rarr" to "→", "harr" to "↔",
    "hellip" to "…", "mdash" to "—", "ndash" to "–", "copy" to "©", "reg" to "®", "trade" to "™",
    "middot" to "·", "bull" to "•", "deg" to "°", "sect" to "§", "para" to "¶",
    "dagger" to "†", "Dagger" to "‡", "spades" to "♠", "clubs" to "♣", "hearts" to "♥", "diams" to "♦",
    // Fix curly-quote entities
    "rsquo" to "\u2019", "lsquo" to "\u2018", "rdquo" to "\u201D", "ldquo" to "\u201C",
)
private fun decodeHtmlEntities(text: String): String = htmlEntityRegex.replace(text) { m ->
    val body = m.groupValues[1]
    when {
        body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        else -> namedHtmlEntities[body] ?: m.value
    }
}
// Rewrite bare image links
private val bareImageLinkRegex = Regex("""\[url\]\s*(https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?)\s*\[/url\]""", RegexOption.IGNORE_CASE)
// Fix unclosed img tags
private val unclosedImgRegex = Regex("""\[img(?:[^\]]*)\]\s*(https?://[^\s\[\]]++)(?!\s*\[/img\])""", RegexOption.IGNORE_CASE)
// Wrap bare image URLs
private val bareUrlRegex = Regex(
    """(?<!\[img\])(?<!\[img\][ \t]{0,10})(?:https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?)(?=\s|$)(?!\[/img\])""",
    setOf(RegexOption.IGNORE_CASE),
)
// Wrap Tenor GIF shares
private val bareTenorLinkRegex = Regex(
    """\[url\]\s*(https?://tenor\.com/view/\S*?)\s*\[/url\]|(?<!\[img\]tenor:)(https?://tenor\.com/view/\S+?)(?=\s|\[|$)""",
    RegexOption.IGNORE_CASE,
)
private fun normalizeMalMarkup(raw: String): String =
    decodeHtmlEntities(brTagRegex.replace(raw, "\n"))
        .let { bareImageLinkRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { unclosedImgRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { bareTenorLinkRegex.replace(it) { m -> "[img]tenor:${m.groupValues[1].ifBlank { m.groupValues[2] }}[/img]" } }
        .let { bareUrlRegex.replace(it) { m -> "[img]${m.value}[/img]" } }

private fun parseBBCode(rawIn: String, linkColor: Color): List<ForumBlock> {
    if (rawIn.isBlank()) return emptyList()
    return parseBlocks(normalizeMalMarkup(rawIn), linkColor)
}
// Recurse into center/quote blocks
private fun parseBlocks(raw: String, linkColor: Color): List<ForumBlock> {
    val blocks = mutableListOf<ForumBlock>()
    var pos = 0
    for (m in bbBlockRegex.findAll(raw)) {
        if (m.range.first > pos) blocks += paragraphsFrom(raw.substring(pos, m.range.first), linkColor)
        val tag = m.groupValues[1].lowercase()
        val inner = m.groupValues[2]
        when (tag) {
            "img" -> inner.trim().takeIf { it.isNotBlank() }?.let {
                if (it.startsWith("tenor:", ignoreCase = true)) blocks += ForumBlock.ImageBlock(it.substring(6), resolveTenor = true)
                else blocks += ForumBlock.ImageBlock(it)
            }
            "list" -> {
                val items = inner.split(Regex("""\[\*\]""", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotBlank() }
                if (items.isNotEmpty()) blocks += ForumBlock.ListBlock(items.map { inlineAnnotated(it, linkColor) }, ordered = m.value.take(20).contains("=1"))
            }
            "quote" -> inner.trim().takeIf { it.isNotBlank() }?.let { blocks += ForumBlock.Quote(parseBlocks(it, linkColor)) }
            // No extra box wrapper
            "center" -> parseBlocks(inner, linkColor).forEach { nested ->
                blocks += if (nested is ForumBlock.Paragraph) nested.copy(center = true) else nested
            }
        }
        pos = m.range.last + 1
    }
    if (pos < raw.length) blocks += paragraphsFrom(raw.substring(pos), linkColor)
    return blocks
}
// Forum image with fallback
@Composable private fun ForumImage(url: String, c: KikoColors, onTap: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 340.dp).clip(RoundedCornerShape(8.dp))
                .border(1.dp, c.primary.copy(alpha = .5f), RoundedCornerShape(8.dp))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTap(url) },
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
                is AsyncImagePainter.State.Error -> Column(
                    Modifier.fillMaxWidth().padding(16.dp).clickable { runCatching { uriHandler.openUri(url) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Warning, null, tint = c.muted, modifier = Modifier.size(22.dp))
                    Text("Couldn't load image · tap to open", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}
// Fullscreen zoomable image viewer
@Composable private fun ZoomableImageDialog(url: String, onDismiss: () -> Unit) {
    var scale by remember(url) { mutableStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .95f))
                .pointerInput(url) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = newScale
                        offset = if (newScale <= 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.95f)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                    .pointerInput(url) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                            },
                            onTap = { if (scale <= 1f) onDismiss() },
                        )
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .15f)),
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
}
// Render BBCode as column
@Composable private fun ForumBody(body: String, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val uriHandler = LocalUriHandler.current
    val blocks = remember(body, c.primary) { parseBBCode(body, c.primary) }
    // Currently open viewer image
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    fullscreenImage?.let { url -> ZoomableImageDialog(url, onDismiss = { fullscreenImage = null }) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block -> ForumBlockView(block, c, uriHandler) { fullscreenImage = it } }
    }
}
// Recursive block rendering helper
@Composable private fun ForumBlockView(block: ForumBlock, c: KikoColors, uriHandler: androidx.compose.ui.platform.UriHandler, muted: Boolean = false, onImageTap: (String) -> Unit) {
    when (block) {
        is ForumBlock.Paragraph -> ClickableText(
            text = block.text,
            style = TextStyle(
                color = if (muted) c.muted else c.ink, fontSize = if (muted) 13.sp else 14.sp,
                lineHeight = if (muted) 19.sp else 20.sp, fontStyle = if (muted) FontStyle.Italic else FontStyle.Normal,
                textAlign = if (block.center) TextAlign.Center else TextAlign.Start,
            ),
            modifier = Modifier.fillMaxWidth(),
            onClick = { offset -> block.text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } } },
        )
        // Ignore fixed pixel width
        is ForumBlock.ImageBlock -> {
            // Tenor resolve loading states
            if (block.resolveTenor) {
                var resolved by remember(block.url) { mutableStateOf<String?>(null) }
                var failed by remember(block.url) { mutableStateOf(false) }
                LaunchedEffect(block.url) {
                    val gif = TenorResolver.resolveGifUrl(block.url)
                    if (gif != null) resolved = gif else failed = true
                }
                when {
                    failed -> Text(
                        block.url, color = c.primary, fontSize = 13.sp, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.fillMaxWidth().clickable { runCatching { uriHandler.openUri(block.url) } },
                    )
                    resolved == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.primary, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                    else -> ForumImage(resolved!!, c, onImageTap)
                }
            } else {
                ForumImage(block.url, c, onImageTap)
            }
        }
        is ForumBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(if (block.ordered) "${index + 1}." else "•", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp).width(18.dp))
                    ClickableText(
                        text = item, style = TextStyle(color = c.ink, fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.weight(1f),
                        onClick = { offset -> item.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } } },
                    )
                }
            }
        }
        is ForumBlock.Quote -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceLow)
                .border(androidx.compose.foundation.BorderStroke(3.dp, c.muted.copy(alpha = .35f)), RoundedCornerShape(10.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.blocks.forEach { nested -> ForumBlockView(nested, c, uriHandler, muted = true, onImageTap = onImageTap) }
        }
    }
}
// Single topic reply row
@Composable private fun ForumPostCard(post: ForumPost, isOriginalPost: Boolean = false) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        if (post.author.avatar.isNotBlank()) {
            AsyncImage(model = post.author.avatar, contentDescription = post.author.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(38.dp).clip(CircleShape).background(c.warm))
        } else {
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.warm), contentAlignment = Alignment.Center) {
                Text(post.author.name.take(1).ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.author.name.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
                if (isOriginalPost) {
                    Box(Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(c.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("OP", color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                } else {
                    Text("#${post.number}", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(formatForumDate(post.createdAt), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
            ForumBody(post.body, Modifier.padding(top = 8.dp))
        }
    }
}
// Poll option vote bars
@Composable private fun ForumPollCard(poll: ForumPoll, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val totalVotes = poll.options.sumOf { it.votes }.coerceAtLeast(1)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(poll.question, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
            Spacer(Modifier.height(10.dp))
            poll.options.forEach { opt ->
                val fraction = opt.votes.toFloat() / totalVotes
                Column(Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(opt.text, color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                        Text("${opt.votes}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(50)).background(c.surfaceLow)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(c.primary))
                    }
                }
            }
            if (poll.closed) Text("Poll closed", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
// Parse forum ISO timestamp
private fun formatForumDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(raw)
        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(parsed!!)
    } catch (e: Exception) { raw.take(10) }
}

// Profile section
@Composable private fun ProfileScreen(
    connected: Boolean, profile: MalProfile?, items: List<MediaItem>, themeMode: ThemeMode, colorSource: ColorSource, paletteStyle: PaletteStyle, titleLanguage: TitleLanguage,
    nsfwEnabled: Boolean, onNsfwChange: (Boolean) -> Unit,
    onConnect: () -> Unit, onSignOut: () -> Unit, onThemeClick: () -> Unit, onColorClick: () -> Unit, onPaletteClick: () -> Unit, onTitleLanguageClick: () -> Unit,
    updateInfo: AppUpdateInfo? = null, onAboutClick: () -> Unit = {},
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
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
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            AppHeader("Profile") {
                // Sign out icon action
                if (connected) {
                    IconButton(onClick = { confirmSignOut = true }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surfaceLow)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Sign out", tint = c.danger)
                    }
                }
            }

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
                                ScoreDistributionChart(animeItems, c)
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
                                ScoreDistributionChart(mangaItems, c)
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
            Text("Preferences", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 30.dp, bottom = 10.dp))
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
        }
    }
}
// App info page
@Composable private fun AboutScreen(
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
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
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
            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://github.com/SyHaqi/kiko")) }, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)) {
                Icon(painterResource(R.drawable.ic_github), "GitHub", tint = c.ink)
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://discord.gg/KZYQHpDWKH")) }, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)) {
                Icon(painterResource(R.drawable.ic_discord), "Discord", tint = c.ink)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
// One headline stat number
@Composable private fun HeroStat(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String, container: Color, content: Color) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(container).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = content)
        Text(label, color = content.copy(alpha = .75f), fontSize = 11.sp)
    }
}
// Proportional status breakdown bar
@Composable private fun StatBar(label: String, value: Int, total: Int, c: KikoColors, barColor: Color = c.primary) {
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
@Composable private fun LabeledStat(label: String, value: String, c: KikoColors) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$label ", color = c.muted, fontSize = 13.sp)
        Text(value, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
@Composable private fun SegmentedStatBar(segments: List<Pair<Int, Color>>, c: KikoColors) {
    val total = segments.sumOf { it.first }
    Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)).background(c.surfaceLow)) {
        if (total > 0) {
            Row(Modifier.fillMaxSize()) {
                segments.forEach { (value, color) -> if (value > 0) Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color)) }
            }
        }
    }
}
@Composable private fun StatusLegendRow(label: String, value: Int, color: Color, c: KikoColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(9.dp))
        Text(label, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
@Composable private fun SummaryRow(label: String, value: String, c: KikoColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.muted, fontSize = 13.sp)
        Text(value, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
// Top genres proportional bars
@Composable private fun GenreBreakdownChart(items: List<MediaItem>, c: KikoColors) {
    val total = items.size
    // Skip junk genre tags
    val counts = items.flatMap { it.genres }.filter { it.isNotBlank() && it.trim().split(" ").size <= 3 && it.length <= 24 }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
    if (counts.isEmpty()) { Text("Not enough data yet.", color = c.muted, fontSize = 12.sp); return }
    Column(Modifier.fillMaxWidth()) { counts.forEach { (genre, count) -> StatBar(genre, count, total, c, c.primary) } }
}
// Score distribution histogram
@Composable private fun ScoreDistributionChart(items: List<MediaItem>, c: KikoColors) {
    val counts = (1..10).associateWith { s -> items.count { it.myRating == s } }
    if (counts.values.all { it == 0 }) { Text("No scored titles yet.", color = c.muted, fontSize = 12.sp); return }
    val maxCount = counts.values.max()
    val barSlotHeight = 56.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        (1..10).forEach { score ->
            val count = counts.getValue(score)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                Text(if (count > 0) count.toString() else "", color = c.muted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().height(barSlotHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height((count.toFloat() / maxCount * barSlotHeight.value).dp.coerceAtLeast(if (count > 0) 4.dp else 1.dp))
                            .clip(RoundedCornerShape(4.dp)).background(if (count > 0) c.primary else c.surfaceLow)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(score.toString(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}
// Fixed non-theme status colors
private val StatusWatchingColor = Color(0xFF2DB039)
private val StatusCompletedColor = Color(0xFF26448F)
private val StatusOnHoldColor = Color(0xFFE7B715)
private val StatusDroppedColor = Color(0xFFA12F31)
private val StatusPlanColor = Color(0xFF8F8F8F)
private fun statusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.Watching, WatchStatus.Reading -> StatusWatchingColor
    WatchStatus.Completed -> StatusCompletedColor
    WatchStatus.OnHold -> StatusOnHoldColor
    WatchStatus.Dropped -> StatusDroppedColor
    WatchStatus.Plan -> StatusPlanColor
}
// Status color by label
private fun statusColor(label: String): Color = when {
    label.startsWith("Watch", true) || label.startsWith("Read", true) -> StatusWatchingColor
    label.startsWith("Complet", true) -> StatusCompletedColor
    label.contains("hold", true) -> StatusOnHoldColor
    label.startsWith("Drop", true) -> StatusDroppedColor
    else -> StatusPlanColor // Plan to watch
}
// Fallback avatar tile
@Composable private fun Avatar(picture: String = "") {
    val c = LocalKikoColors.current
    if (picture.isNotBlank()) {
        AsyncImage(model = picture, contentDescription = "Profile picture", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(43.dp).clip(RoundedCornerShape(16.dp)).background(c.warm))
    } else {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(16.dp)).background(c.warm), contentAlignment = Alignment.Center) { Text("M", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.ink) }
    }
}

// Detail section
// In-app browser page
@Composable private fun WebPageScreen(url: String, title: String, darkTheme: Boolean, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var loading by remember(url) { mutableStateOf(true) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
            IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) {
                Icon(Icons.Default.OpenInNew, "Open in browser", tint = c.primary, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.fillMaxSize().padding(top = 15.dp)) {
            val backgroundArgb = c.background.toArgb()
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView(it).apply {
                        setBackgroundColor(backgroundArgb)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Follow app theme, not system
                        applyForcedDarkMode(settings, darkTheme)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, finishedUrl: String) { loading = false }
                        }
                        loadUrl(url)
                    }
                },
                update = { webView -> webView.setBackgroundColor(backgroundArgb); applyForcedDarkMode(webView.settings, darkTheme) },
            )
            // Loading indicator overlay
            if (loading) CircularProgressIndicator(color = c.primary, modifier = Modifier.align(Alignment.Center))
        }
    }
}
// Forces WebView rendering to match app theme
private fun applyForcedDarkMode(settings: android.webkit.WebSettings, darkTheme: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darkTheme)
    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        @Suppress("DEPRECATION")
        WebSettingsCompat.setForceDark(settings, if (darkTheme) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
    }
}
@Composable private fun DetailScreen(item: MediaItem, onBack: () -> Unit, onEdit: (MediaItem) -> Unit, onOpenRelated: (RelatedEntry) -> Unit, relatedLoadingId: Int? = null, onBackfillRelated: (String, MediaType, (List<RelatedEntry>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() }, onBackfillThemes: (String, MediaType, (List<String>, List<String>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() }, onBackfillCovers: (String, MediaType, (List<String>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() }, onLoadRecommended: (MediaItem, (List<RecommendedEntry>) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() }, onOpenRecommended: (RecommendedEntry) -> Unit = {}, recommendedLoadingId: Int? = null, onLoadStatusDistribution: (MediaItem, (StatusDistribution) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() }, onLoadCharactersStaff: (MediaItem, (List<CharacterEntry>, List<StaffEntry>) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() }, onLoadReviews: (MediaItem, (List<ReviewEntry>) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() }, onOpenReview: (ReviewEntry) -> Unit = {}, onOpenReviewList: (String, String) -> Unit = { _, _ -> }, onGenreClick: (String) -> Unit = {}, initialScroll: Pair<Int, Int> = 0 to 0, onLeaveScroll: (Int, Int) -> Unit = { _, _ -> }, myListStatus: Map<Int, WatchStatus> = emptyMap()) {
    val c = LocalKikoColors.current
    var synopsisExpanded by remember(item.id) { mutableStateOf(false) }
    // Track related backfill completion
    var backfilledRelated by remember(item.id) { mutableStateOf<List<RelatedEntry>?>(null) }
    var relatedDone by remember(item.id) { mutableStateOf(item.related.isNotEmpty()) }
    LaunchedEffect(item.id) {
        if (item.related.isEmpty()) onBackfillRelated(item.id, item.type, { backfilledRelated = it }, { relatedDone = true }) else relatedDone = true
    }
    val related = backfilledRelated ?: item.related
    // Recheck themes if missing
    var backfilledThemes by remember(item.id) { mutableStateOf<Pair<List<String>, List<String>>?>(null) }
    var themesDone by remember(item.id) { mutableStateOf(item.openingThemes.isNotEmpty() || item.endingThemes.isNotEmpty()) }
    LaunchedEffect(item.id) {
        if (item.openingThemes.isEmpty() && item.endingThemes.isEmpty()) {
            onBackfillThemes(item.id, item.type, { op, ed -> backfilledThemes = op to ed }, { themesDone = true })
        } else themesDone = true
    }
    val (openingThemes, endingThemes) = backfilledThemes ?: (item.openingThemes to item.endingThemes)
    // Characters + staff rows
    var characters by remember(item.id) { mutableStateOf<List<CharacterEntry>>(emptyList()) }
    var staffList by remember(item.id) { mutableStateOf<List<StaffEntry>>(emptyList()) }
    LaunchedEffect(item.id) { onLoadCharactersStaff(item, { chars, stf -> characters = chars; staffList = stf }, {}) }
    var reviews by remember(item.id) { mutableStateOf<List<ReviewEntry>>(emptyList()) }
    LaunchedEffect(item.id) { onLoadReviews(item, { reviews = it }, {}) }
    // Recheck cover gallery non-blocking
    var backfilledCovers by remember(item.id) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(item.id) {
        if (item.covers.size <= 1) onBackfillCovers(item.id, item.type, { backfilledCovers = it }, {})
    }
    val covers = backfilledCovers ?: item.covers
    // Recommended row loads async
    var recommended by remember(item.id) { mutableStateOf<List<RecommendedEntry>>(emptyList()) }
    LaunchedEffect(item.id) { onLoadRecommended(item, { recommended = it }, {}) }
    // Status distribution loads async
    var statusDistribution by remember(item.id) { mutableStateOf<StatusDistribution?>(null) }
    LaunchedEffect(item.id) { onLoadStatusDistribution(item, { statusDistribution = it }, {}) }
    // Fresh scroll state per-title
    val listState = remember(item.id) { LazyListState(initialScroll.first, initialScroll.second) }
    // Save spot on leave
    DisposableEffect(item.id) {
        onDispose { onLeaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    // Share single decoded painter
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coverPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context).data(item.cover.ifBlank { null }).size(Size.ORIGINAL).build()
    )
    val coverReady = item.cover.isBlank() || coverPainter.state is AsyncImagePainter.State.Success || coverPainter.state is AsyncImagePainter.State.Error
    BackHandler(onBack = onBack)
    // Load all sections upfront
    if (!coverReady || !relatedDone || !themesDone) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary) }
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
                    Box(Modifier.fillMaxWidth().height(248.dp).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
                        if (backdropUrl != null) {
                            AsyncImage(
                                model = backdropUrl, contentDescription = null,
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
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .32f)),
                        ) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                        var moreOpen by remember(item.id) { mutableStateOf(false) }
                        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                            IconButton(
                                onClick = { moreOpen = true },
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .32f)),
                            ) { Icon(Icons.Default.MoreVert, "More options", tint = Color.White) }
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
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
                    Box(
                        Modifier.padding(start = 20.dp, top = 96.dp).width(128.dp).aspectRatio(2f / 3f)
                            .shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color(item.color))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showFullCover = true },
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
                                        model = gallery[page], contentDescription = item.displayTitle(),
                                        modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)),
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
                                            Modifier.size(if (i == pagerState.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                                .background(Color.White.copy(alpha = if (i == pagerState.currentPage) .95f else .4f)),
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showFullCover = false },
                                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .15f)),
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
                                Text("%.2f".format(item.score), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    // Next episode airing time
                    item.nextEpisodeLabel()?.let { label ->
                        val is24Hour = systemIs24Hour()
                        val airTime = item.localBroadcast()?.second
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
                            item.genres.forEach { g -> GenreChip(g, onClick = { onGenreClick(g) }) }
                        }
                    }
                    val meta = listOfNotNull(item.creator.takeIf { it.isNotBlank() }, aired.takeIf { it.isNotBlank() })
                    if (meta.isNotEmpty()) Text(meta.joinToString("   ·   "), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))

                    Text("Synopsis", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
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
                        Text("Statistics", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth()) {
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
                        Text("Details", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                details.forEachIndexed { i, (label, value) ->
                                    InfoRow(label, value)
                                    if (i != details.lastIndex) HorizontalDivider(color = c.surfaceLow)
                                }
                            }
                        }
                    }

                    if (item.synonyms.isNotEmpty()) {
                        Text("Alternative titles", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth()) {
                            SelectionContainer {
                                Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                    item.synonyms.forEachIndexed { i, name ->
                                        Text(name, color = c.ink, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp))
                                        if (i != item.synonyms.lastIndex) HorizontalDivider(color = c.surfaceLow)
                                    }
                                }
                            }
                        }
                    }

                    if (characters.isNotEmpty()) {
                        Text("Characters", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(characters, key = { it.malId }) { ch -> CharacterCard(ch, uriHandler) }
                        }
                    }
                    if (staffList.isNotEmpty()) {
                        Text("Staff", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(staffList, key = { it.malId }) { st -> StaffCard(st, uriHandler) }
                        }
                    }

                    val themes = openingThemes.map { "OP" to it } + endingThemes.map { "ED" to it }
                    if (themes.isNotEmpty()) {
                        Text("Theme songs", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                themes.forEachIndexed { i, (kind, text) ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { uriHandler.openUri(youtubeSearchUrl("$text $itemDisplayTitle")) }.padding(vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(kind, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                                        Text(text, color = c.ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                        Icon(Icons.Default.PlayArrow, "Search on YouTube", tint = c.muted, modifier = Modifier.size(18.dp))
                                    }
                                    if (i != themes.lastIndex) HorizontalDivider(color = c.surfaceLow)
                                }
                            }
                        }
                    }

                    if (reviews.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Reviews", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.weight(1f))
                            Text("See more", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { onOpenReviewList(malReviewsUrl(item), itemDisplayTitle) })
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(reviews, key = { it.malId }) { rev -> ReviewCard(rev, onClick = { onOpenReview(rev) }) }
                        }
                    }

                    if (related.isNotEmpty()) {
                        Text("Related", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(related) { rel ->
                                RelatedCard(rel, loading = rel.malId > 0 && relatedLoadingId == rel.malId, myStatus = myListStatus[rel.malId]) {
                                    // Fallback to web search
                                    if (rel.malId > 0) onOpenRelated(rel) else uriHandler.openUri(malUrl(rel))
                                }
                            }
                        }
                    }

                    // Recommendations from MAL endpoint
                    if (recommended.isNotEmpty()) {
                        Text("Recommended", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            items(recommended, key = { it.malId }) { rec ->
                                RecommendedCard(rec, loading = recommendedLoadingId == rec.malId, myStatus = myListStatus[rec.malId]) { onOpenRecommended(rec) }
                            }
                        }
                    }

                    if (item.background.isNotBlank()) {
                        Text("Background", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        Text(item.background, color = c.ink, fontSize = 14.sp, lineHeight = 21.sp)
                    }

                    // Reuse status bar styling
                    statusDistribution?.takeIf { it.total > 0 }?.let { dist ->
                        Text("Status distribution", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth()) {
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
            onClick = { onEdit(item) },
            icon = { Icon(if (item.inUserList) Icons.Default.Edit else Icons.Default.Add, if (item.inUserList) "Edit" else "Add", tint = c.onPrimary) },
            text = { Text(if (item.inUserList) item.status.label else "Add", fontWeight = FontWeight.Bold, color = c.onPrimary) },
            containerColor = c.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}
@Composable private fun InfoRow(label: String, value: String) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.muted, fontSize = 13.sp)
        Text(value, color = c.ink, fontWeight = FontWeight.Medium, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}
private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.0fK".format(n / 1_000.0)
    else -> n.toString()
}
// Format season and year
private fun seasonYear(season: String, year: String): String = listOf(season, year).filter { it.isNotBlank() }.joinToString(" ")
// Format ISO date display
private fun formatFullDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        when (raw.count { it == '-' }) {
            2 -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(raw)!!)
            1 -> java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US).format(java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse(raw)!!)
            else -> raw
        }
    } catch (e: Exception) { raw }
}
private fun youtubeSearchUrl(query: String) = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")
private fun malUrl(entry: RelatedEntry) = if (entry.malId > 0) "https://myanimelist.net/${entry.malType}/${entry.malId}" else "https://myanimelist.net/search/all?q=" + java.net.URLEncoder.encode(entry.title, "UTF-8")
// Item for share/open menu
private fun malUrl(item: MediaItem): String {
    val intId = item.id.toIntOrNull()
    return if (intId != null && intId > 0) "https://myanimelist.net/${item.type.name.lowercase()}/$intId"
    else "https://myanimelist.net/search/all?q=" + java.net.URLEncoder.encode(item.title, "UTF-8")
}
// Reviews page for an item
private fun malReviewsUrl(item: MediaItem): String {
    val intId = item.id.toIntOrNull()
    return if (intId != null && intId > 0) "https://myanimelist.net/${item.type.name.lowercase()}/$intId/_/reviews"
    else malUrl(item)
}
// Recognize MAL title URL
private fun parseMalDeepLink(uri: Uri): Pair<Int, MediaType>? {
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
@Composable private fun Pill(text: String, container: Color, content: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
// Outline style genre chip
@Composable private fun GenreChip(text: String, onClick: (() -> Unit)? = null) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.muted.copy(alpha = .35f), RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = c.ink, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}
@Composable private fun StatBlock(modifier: Modifier, value: String, label: String) {
    val c = LocalKikoColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
// Uniform shared card shell
@Composable private fun DetailRowCard(
    imageUrl: String, fallbackLetter: String, title: String,
    label: String? = null, subtitle: String? = null,
    loading: Boolean = false, onClick: (() -> Unit)? = null,
    myStatus: WatchStatus? = null,
) {
    val c = LocalKikoColors.current
    Column(
        Modifier.width(140.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
            .let { m -> onClick?.let { m.clickable(enabled = !loading, onClick = it) } ?: m },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(c.surfaceLow)) {
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
            if (label != null) Text(label.uppercase(), color = c.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (label != null) 4.dp else 0.dp))
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
@Composable private fun RelatedCard(entry: RelatedEntry, loading: Boolean = false, myStatus: WatchStatus? = null, onClick: () -> Unit) {
    DetailRowCard(imageUrl = entry.cover, fallbackLetter = entry.title.take(1), title = entry.title, label = entry.relation, loading = loading, myStatus = myStatus, onClick = onClick)
}

// Recommended card same style
@Composable private fun RecommendedCard(entry: RecommendedEntry, loading: Boolean = false, myStatus: WatchStatus? = null, onClick: () -> Unit) {
    val subtitle = if (entry.votes > 0) "${entry.votes} recommend${if (entry.votes == 1) "s" else ""}" else "Recommended"
    DetailRowCard(imageUrl = entry.cover, fallbackLetter = entry.title.take(1), title = entry.title, subtitle = subtitle, loading = loading, myStatus = myStatus, onClick = onClick)
}
// Compact card for characters/staff rows
@Composable private fun PersonCard(imageUrl: String, fallbackLetter: String, name: String, role: String, onClick: (() -> Unit)?) {
    val c = LocalKikoColors.current
    Column(
        Modifier.width(88.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
            .let { m -> onClick?.let { m.clickable(onClick = it) } ?: m },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(c.surfaceLow)) {
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
@Composable private fun CharacterCard(entry: CharacterEntry, uriHandler: androidx.compose.ui.platform.UriHandler) {
    PersonCard(entry.image, entry.name.take(1), entry.name, entry.role) { entry.url.takeIf { it.isNotBlank() }?.let { runCatching { uriHandler.openUri(it) } } }
}
// Staff card opens MAL page
@Composable private fun StaffCard(entry: StaffEntry, uriHandler: androidx.compose.ui.platform.UriHandler) {
    PersonCard(entry.image, entry.name.take(1), entry.name, entry.role.ifBlank { "Staff" }) { entry.url.takeIf { it.isNotBlank() }?.let { runCatching { uriHandler.openUri(it) } } }
}
// Review card opens full text
@Composable private fun ReviewCard(entry: ReviewEntry, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Column(
        Modifier.width(260.dp).clip(RoundedCornerShape(18.dp)).background(c.surface).clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.userImage.isNotBlank()) {
                AsyncImage(model = entry.userImage, contentDescription = entry.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(30.dp).clip(CircleShape).background(c.warm))
            } else {
                Box(Modifier.size(30.dp).clip(CircleShape).background(c.warm), contentAlignment = Alignment.Center) {
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
@Composable private fun EditSheet(item: MediaItem, onDismiss: () -> Unit, onSave: (MediaItem) -> Unit, onDelete: () -> Unit) {
    val c = LocalKikoColors.current
    var status by remember { mutableStateOf(item.status) }
    var progress by remember { mutableStateOf(item.progress) }
    var rating by remember { mutableStateOf(item.myRating) }
    var startDate by remember { mutableStateOf(item.watchStartDate) }
    var endDate by remember { mutableStateOf(item.watchEndDate) }
    var rewatching by remember { mutableStateOf(item.isRewatching) }
    var timesRewatched by remember { mutableStateOf(item.timesRewatched) }
    val rewatchWord = if (item.type == MediaType.Anime) "Rewatch" else "Reread"
    val rewatchedWord = if (item.type == MediaType.Anime) "rewatched" else "reread"
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = { Text("Remove from your list?", color = c.ink) },
            text = { Text("Are you sure you want to remove \"${item.title}\" from your list? This also removes it from your MyAnimeList account.", color = c.muted) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Cancel") } },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) { Text("Delete") }
                Button(
                    onClick = { onSave(item.copy(status = status, progress = progress, myRating = rating, watchStartDate = startDate, watchEndDate = endDate, isRewatching = rewatching, timesRewatched = timesRewatched)) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                ) { Text("Save change") }
            }

            Text("Status", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
            val statusOptions = remember(item.type) { WatchStatus.entries.filterNot { it == if (item.type == MediaType.Anime) WatchStatus.Reading else WatchStatus.Watching } }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 9.dp)) {
                items(statusOptions) { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = {
                            status = s
                            // Auto-fill progress to the max when marking as completed
                            if (s == WatchStatus.Completed && item.total > 0) progress = item.total
                        },
                        label = { Text(s.label) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary),
                    )
                }
            }

            Text(if (item.type == MediaType.Anime) "Episodes watched" else "Chapters read", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal = 6.dp),
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
                Box(Modifier.padding(start = 14.dp).size(34.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(if (rating == 0) "–" else rating.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary)
                }
            }

            Text("Dates", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(Modifier.weight(1f), label = "Start date", value = startDate, onPick = { startDate = it })
                DateField(Modifier.weight(1f), label = "End date", value = endDate, onPick = { endDate = it })
            }

            Text(rewatchWord, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, modifier = Modifier.padding(top = 20.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Currently ${rewatchWord.lowercase()}ing", color = c.ink, fontSize = 14.sp)
                Switch(checked = rewatching, onCheckedChange = { rewatching = it }, colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { if (timesRewatched > 0) timesRewatched-- }) { Icon(Icons.Default.Remove, "Decrease", tint = c.primary) }
                Text("Times $rewatchedWord: $timesRewatched", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                IconButton(onClick = { timesRewatched++ }) { Icon(Icons.Default.Add, "Increase", tint = c.primary) }
            }
        }
    }
}
// Tappable date picker field
@Composable private fun DateField(modifier: Modifier = Modifier, label: String, value: String, onPick: (String) -> Unit) {
    val c = LocalKikoColors.current
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).clickable { showPicker = true }.padding(horizontal = 14.dp, vertical = 13.dp),
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
private fun String.toEpochMillisOrNull(): Long? {
    if (isBlank()) return null
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        sdf.parse(this)?.time
    } catch (e: Exception) { null }
}
// Format millis to date
private fun Long.toIsoDate(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(this))
}
// Format date for display
private fun formatUserDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val out = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        out.format(parser.parse(raw)!!)
    } catch (e: Exception) { raw }
}
// Update available dialog sheet
@Composable private fun UpdateDialog(
    info: AppUpdateInfo, downloadProgress: Float?, needsInstallPermission: Boolean, error: String?,
    onDownload: () -> Unit, onOpenInstallSettings: () -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalKikoColors.current
    val downloading = downloadProgress != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Kiko ${info.version} is available", color = c.ink) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (needsInstallPermission) {
                    Text("Kiko needs permission to install updates. Allow it from Settings, then try again.", color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                } else if (error != null) {
                    Text(error, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (downloading) {
                    val progress = downloadProgress ?: 0f
                    Text("Downloading update…", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = c.primary, trackColor = c.surfaceLow)
                    Text("${(progress * 100).toInt()}%", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text(info.notes.ifBlank { "No release notes provided." }, color = c.muted, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            when {
                needsInstallPermission -> TextButton(onClick = onOpenInstallSettings, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Open settings") }
                else -> TextButton(onClick = onDownload, enabled = !downloading, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text(if (downloading) "Downloading…" else "Update now") }
            }
        },
        dismissButton = {
            if (!downloading) {
                Row {
                    TextButton(onClick = onSkip, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Skip") }
                    TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Later") }
                }
            }
        },
    )
}
@Composable private fun ThemeSheet(current: ThemeMode, onDismiss: () -> Unit, onSelect: (ThemeMode) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a theme", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            ThemeMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (mode == current) c.primaryContainer else c.surface).clickable { onSelect(mode) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(mode.label, fontWeight = FontWeight.Bold, color = c.ink)
                        Text(when (mode) { ThemeMode.System -> "Matches your device setting"; ThemeMode.Light -> "Always light"; ThemeMode.Dark -> "Always dark" }, color = c.muted, fontSize = 12.sp)
                    }
                    if (mode == current) Icon(Icons.Default.Check, null, tint = c.primary)
                }
            }
        }
    }
}
@Composable private fun ColorSourceSheet(current: ColorSource, customHex: String, onDismiss: () -> Unit, onSelect: (ColorSource) -> Unit, onCustomHexChange: (String) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a color", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            ColorSource.entries.forEach { source ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (source == current) c.primaryContainer else c.surface)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(source); if (source != ColorSource.Custom) onDismiss() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(source.label, fontWeight = FontWeight.Bold, color = c.ink)
                            Text(
                                when (source) { ColorSource.AppDefault -> "Kiko's default indigo"; ColorSource.Dynamic -> "Matches your device wallpaper"; ColorSource.Custom -> "Pick your own hex color" },
                                color = c.muted, fontSize = 12.sp,
                            )
                        }
                        if (source == current) Icon(Icons.Default.Check, null, tint = c.primary)
                    }
                    // Expand only Custom row
                    if (source == ColorSource.Custom && current == ColorSource.Custom) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val valid = parseHexColor(customHex) != null
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (valid) parseHexColor(customHex)!! else c.surfaceLow).border(1.dp, c.muted.copy(alpha = .4f), RoundedCornerShape(6.dp)))
                            OutlinedTextField(
                                value = customHex, onValueChange = { onCustomHexChange(it.take(7)) },
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                                singleLine = true, prefix = { Text("#", color = c.muted) },
                                isError = !valid,
                                supportingText = { if (!valid) Text("6-digit hex, e.g. 2E51A2", color = c.danger, fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun PaletteStyleSheet(current: PaletteStyle, onDismiss: () -> Unit, onSelect: (PaletteStyle) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a color palette", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            PaletteStyle.entries.forEach { style ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (style == current) c.primaryContainer else c.surface).clickable { onSelect(style) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(style.label, fontWeight = FontWeight.Bold, color = c.ink)
                        Text(
                            when (style) {
                                PaletteStyle.TonalSpot -> "Balanced, vivid accent color"
                                PaletteStyle.Neutral -> "Softer, more muted colors"
                                PaletteStyle.Monochrome -> "Greyscale — the same in every color"
                            },
                            color = c.muted, fontSize = 12.sp,
                        )
                    }
                    if (style == current) Icon(Icons.Default.Check, null, tint = c.primary)
                }
            }
        }
    }
}
@Composable private fun TitleLanguageSheet(current: TitleLanguage, onDismiss: () -> Unit, onSelect: (TitleLanguage) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Preferences", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Title language", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            TitleLanguage.entries.forEach { lang ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (lang == current) c.primaryContainer else c.surface).clickable { onSelect(lang) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(lang.label, fontWeight = FontWeight.Bold, color = c.ink)
                        Text(when (lang) { TitleLanguage.Romaji -> "e.g. Sousou no Frieren"; TitleLanguage.English -> "e.g. Frieren: Beyond Journey's End" }, color = c.muted, fontSize = 12.sp)
                    }
                    if (lang == current) Icon(Icons.Default.Check, null, tint = c.primary)
                }
            }
        }
    }
}