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

enum class TitleLanguage(val label: String) { Romaji("Romaji"), English("English") }

val LocalTitleLanguage = staticCompositionLocalOf { TitleLanguage.Romaji }
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
    // Comma-separated tags, synced to MAL's own per-entry "tags" field
    val notes: String = "",
    // Free-text personal note, synced to MAL's own per-entry "comments" field
    val comments: String = "",
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
    val creator: String = "",
    // All studios (anime) or all authors (manga), comma-separated — used for filter matching
    // so a work still matches when the searched-for author isn't the first one credited.
    val allCreators: String = "",
    val startDate: String = "", val season: String = "",
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
    // Facet names (matching DiscoverFilters field names below, e.g. "genres", "source")
    // this item simply has no data for — because it came from a scrape that doesn't expose
    // that facet (e.g. MalPeopleApi's author page has no genre tags, MalCompanyApi's studio
    // page has no source/rating/airing status) rather than the item genuinely having none.
    // matches() skips those specific checks instead of treating "no data" as "no match",
    // which previously made an active genre/theme/etc. filter wipe out every author-search
    // result outright even when every one of them was a perfectly good match.
    val unknownFacets: Set<String> = emptySet(),
)
// Is title NSFW?

fun MediaItem.isAdultContent() = genres.any { it.equals("Hentai", ignoreCase = true) }

fun List<MediaItem>.nsfwFiltered(allowAdult: Boolean) = if (allowAdult) this else filterNot { it.isAdultContent() }

data class RelatedEntry(val relation: String, val title: String, val malId: Int = 0, val malType: String = "anime", val cover: String = "")
// Characters/staff row entries

data class CharacterEntry(val malId: Int, val name: String, val image: String, val role: String, val url: String = "")

data class StaffEntry(val malId: Int, val name: String, val image: String, val role: String, val url: String = "")
// Reviews row entry

data class ReviewEntry(val malId: Int, val username: String, val userImage: String, val review: String, val score: Int, val tags: List<String> = emptyList(), val reactionScore: Int = 0, val isSpoiler: Boolean = false, val url: String = "")
// MAL's three recommendation verdicts

val ReviewVerdictTags = setOf("Recommended", "Mixed Feelings", "Not Recommended")

fun ReviewEntry.verdict(): String? = tags.firstOrNull { it in ReviewVerdictTags }

fun verdictColor(verdict: String, c: KikoColors): Color = when (verdict) {
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

enum class Destination(val label: String, val icon: ImageVector) { Home("Home", Icons.Default.Home), List("My list", Icons.Default.List), Discover("Discover", Icons.Default.Search), Forums("Forums", Icons.Default.Forum), Clubs("Clubs", Icons.Default.Groups) }

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
    // Matches MediaItem.creator — studio name for anime, author name for manga
    val creator: String = "",
    val source: String = "",
    val year: String = "",
    val season: SeasonName? = null,
    val rating: String = "",
    // Sub-type format field
    val format: String = "",
    // Finished, Ongoing, Upcoming
    val airingStatus: String = "",
) {
    fun isActive() = genres.isNotEmpty() || themes.isNotEmpty() || demographics.isNotEmpty() || creator.isNotBlank() || source.isNotBlank() || year.isNotBlank() || season != null || rating.isNotBlank() || format.isNotBlank() || airingStatus.isNotBlank()
}
// Groups raw airing/publishing text

fun airingBucket(raw: String): String = when {
    raw.contains("Finished", ignoreCase = true) -> "Finished"
    raw.contains("Not yet", ignoreCase = true) -> "Upcoming"
    raw.isNotBlank() -> "Ongoing"
    else -> ""
}

fun MediaItem.matches(f: DiscoverFilters): Boolean {
    if (f.genres.isNotEmpty() && "genres" !in unknownFacets && genres.none { g -> f.genres.any { it.equals(g, ignoreCase = true) } }) return false
    if (f.themes.isNotEmpty() && "themes" !in unknownFacets && contentThemes.none { t -> f.themes.any { it.equals(t, ignoreCase = true) } }) return false
    if (f.demographics.isNotEmpty() && "demographics" !in unknownFacets && demographics.none { d -> f.demographics.any { it.equals(d, ignoreCase = true) } }) return false
    // Studio for anime, author for manga — check every credited name, not just the
    // first-listed one (e.g. an illustrator credited alongside a separate writer).
    // Always available for studio/author search results, so no unknownFacets gate here.
    if (f.creator.isNotBlank() && allCreators.split(",").map { it.trim() }.none { it.contains(f.creator, ignoreCase = true) }) return false
    if (f.source.isNotBlank() && "source" !in unknownFacets && !source.equals(f.source, ignoreCase = true)) return false
    if (f.year.isNotBlank() && startDate != f.year) return false
    if (f.season != null && !season.equals(f.season.label, ignoreCase = true)) return false
    if (f.rating.isNotBlank() && "rating" !in unknownFacets && !rating.equals(f.rating, ignoreCase = true)) return false
    if (f.format.isNotBlank() && !format.equals(f.format, ignoreCase = true)) return false
    if (f.airingStatus.isNotBlank() && "airingStatus" !in unknownFacets && airingBucket(airStatus) != f.airingStatus) return false
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

fun resolvedDiscoverType(format: String, fallback: String): String = when {
    format in CommonAnimeFormats -> "Anime"
    format in CommonMangaFormats -> "Manga"
    else -> fallback
}
// Sort order options

enum class DiscoverSort(val label: String) { Members("Members"), Score("Score"), Newest("Newest"), Title("Title") }
// Collapse punctuation/symbols (e.g. the ★ in "Stardust★Wink") to spaces so title matching
// isn't defeated by stylized titles; also lets "startsWith"/word-boundary checks line up.

fun normalizeForTitleMatch(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
// Match quality against a single candidate string, or null if no match at all

fun matchTier(candidate: String, q: String): Int? {
    val c = normalizeForTitleMatch(candidate)
    if (c.isBlank()) return null
    return when {
        c == q -> 0
        c.startsWith(q) -> 1
        Regex("\\b" + Regex.escape(q) + "\\b").containsMatchIn(c) -> 2
        c.contains(q) -> 3
        else -> null
    }
}
// Title match ranking score. Primary/English title matches always outrank synonym-only
// matches (e.g. MAL lists "Demon Slayer" as a synonym of the unrelated anime "Onigiri" —
// that shouldn't out-rank the real "Demon Slayer: Kimetsu no Yaiba" for that query).

fun MediaItem.titleMatchRank(query: String): Int {
    val q = normalizeForTitleMatch(query)
    if (q.isBlank()) return Int.MAX_VALUE
    val primaryRank = listOf(title, titleEnglish).mapNotNull { matchTier(it, q) }.minOrNull()
    if (primaryRank != null) return primaryRank
    val synonymRank = synonyms.mapNotNull { matchTier(it, q) }.minOrNull()
    return synonymRank?.plus(4) ?: 8
}
// Default blank query

fun List<MediaItem>.sortedForDiscover(sort: DiscoverSort, titleLanguage: TitleLanguage, query: String = ""): List<MediaItem> {
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

fun SeasonName.prev() = SeasonName.entries[(ordinal + 3) % 4]

fun SeasonName.next() = SeasonName.entries[(ordinal + 1) % 4]
// Step season forward/back

fun stepSeason(year: Int, season: SeasonName, forward: Boolean): Pair<Int, SeasonName> = when {
    forward && season == SeasonName.Fall -> year + 1 to SeasonName.Winter
    forward -> year to season.next()
    !forward && season == SeasonName.Winter -> year - 1 to SeasonName.Fall
    else -> year to season.prev()
}

fun currentSeasonName(): SeasonName = when ((java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)) {
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

fun nowIso(): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
// Convert broadcast to local

fun MediaItem.localBroadcast(): Pair<java.time.DayOfWeek, java.time.LocalTime>? {
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

fun localizedTimeLabel(time: java.time.LocalTime, is24Hour: Boolean): String =
    time.format(java.time.format.DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", java.util.Locale.getDefault()))
// Read device time format

@Composable fun systemIs24Hour(): Boolean = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
// Next airing full timestamp

fun MediaItem.nextAirDateTime(): java.time.LocalDateTime? {
    if (!airStatus.equals("Currently Airing", ignoreCase = true)) return null
    val (day, time) = localBroadcast() ?: return null
    val now = java.time.LocalDateTime.now()
    var next = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(day)).atTime(time)
    if (next.isBefore(now)) next = next.plusDays(7)
    return next
}