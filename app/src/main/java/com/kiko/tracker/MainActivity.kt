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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.math.roundToInt

// ---------- Palette ----------
// Same design language in both modes: soft indigo primary, rounded expressive containers.
// Colors are threaded through LocalKikoColors instead of hardcoded so every screen reacts to theme.
@Immutable
data class KikoColors(
    val ink: Color, val onPrimary: Color, val primary: Color, val primaryContainer: Color,
    val background: Color, val surface: Color, val surfaceLow: Color, val muted: Color,
    val lavender: Color, val warm: Color, val danger: Color
)
// MAL-brand palette: the header/primary blue (#2E51A2) is the same fixed hue in both light and
// dark mode, matching the real site (its header bar never changes with theme). Backgrounds and
// surfaces are lifted straight from MAL's own light (#FFFFFF/#F8F8F8) and dark (#121212/#181818)
// site colors.
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

// ---------- Themed palette ----------
// Builds a full KikoColors set from a single seed color, in the spirit of Material You: the seed's
// hue drives every color, and how saturated the result is depends on the chosen PaletteStyle. Style
// only changes chroma (saturation), never the underlying light/dark lightness ladder, so switching
// style never breaks contrast. Monochrome forces saturation to zero everywhere, which is also why
// it's the one style that looks the same no matter which seed (App default/Dynamic/Custom) produced
// it — with zero saturation, hue has nothing left to influence.
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
    // (accent, container, neutral) saturation per style — the same three bands Material's dynamic
    // color uses: a vivid accent, a softer container tint, and a barely-there neutral tint on surfaces.
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
// Resolves the seed color for the current ColorSource — App default is the app's own fixed brand hue,
// Dynamic pulls the system's Material You color (Android 12+ only; falls back to App default below
// that), and Custom parses the person's hex input (falls back to App default while invalid or blank).
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

// ---------- Title language ----------
// Lets people pick whether titles show in Romaji (MAL's default title) or English everywhere in the app.
enum class TitleLanguage(val label: String) { Romaji("Romaji"), English("English") }
private val LocalTitleLanguage = staticCompositionLocalOf { TitleLanguage.Romaji }
// The title to actually show, given the current preference — falls back to Romaji when no English title exists.
@Composable
fun MediaItem.displayTitle(): String {
    val pref = LocalTitleLanguage.current
    return if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) titleEnglish else title
}
// The "other" title, shown as a small subtitle when it differs from what's already on screen.
@Composable
fun MediaItem.secondaryTitle(): String {
    val pref = LocalTitleLanguage.current
    val other = if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) title else titleEnglish
    return other.takeIf { it.isNotBlank() && it != displayTitle() } ?: ""
}

// ---------- Data ----------
// @Immutable tells the Compose compiler this never mutates after construction — true here (every
// property is a val, nothing is ever mutated in place, only copied), but not something it can prove
// on its own because of the List<...> fields below (List is just an interface; Compose can't assume
// a given instance is never secretly a mutable list under the hood). Without this, MediaItem is
// inferred unstable, and every composable taking one — every card in every list/grid screen — has to
// recompose on any nearby state change instead of skipping when the item itself hasn't changed.
@Immutable
data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String, val type: MediaType, val status: WatchStatus,
    val progress: Int = 0, val total: Int = 0,
    // User's own tracking info — not MAL metadata: their personal rating and when *they* watched/read it.
    val myRating: Int = 0, val watchStartDate: String = "", val watchEndDate: String = "",
    // MAL's rewatch tracking — "is_rewatching"/"num_times_rewatched" for anime, the same concept
    // under "is_rereading"/"num_times_reread" for manga. Lets someone go through a Completed title
    // again without losing their original completion record.
    val isRewatching: Boolean = false, val timesRewatched: Int = 0,
    val genre: String = "", val genres: List<String> = emptyList(),
    // MAL's finer-grained tags below genre level ("Isekai", "Time Travel" as themes; "Shounen",
    // "Seinen" as demographics) — not shown anywhere in the UI, only used to score how similar a
    // title is to another for the Detail screen's "Recommended" row.
    val contentThemes: List<String> = emptyList(), val demographics: List<String> = emptyList(),
    val cover: String = "", val color: Long = 0xFFB7C3F5,
    // Every cover MAL has on file for this title (only populated by the single-title detail fetch —
    // see MalApi.detail) — lets the fullscreen cover viewer be swiped through instead of just showing
    // the one main cover. Always includes `cover` itself as the first entry when non-empty.
    val covers: List<String> = emptyList(),
    val synopsis: String = "", val background: String = "",
    val score: Double = 0.0, val rank: Int = 0, val popularity: Int = 0, val listUsers: Int = 0,
    val creator: String = "", val startDate: String = "", val season: String = "",
    val format: String = "", val airStatus: String = "", val source: String = "", val rating: String = "",
    val volumes: Int = 0, val titleEnglish: String = "",
    // Extra metadata surfaced in the detail screen's Dates / Alt titles / Theme songs / Related sections.
    val startDateFull: String = "", val endDateFull: String = "",
    val synonyms: List<String> = emptyList(),
    val openingThemes: List<String> = emptyList(), val endingThemes: List<String> = emptyList(),
    val related: List<RelatedEntry> = emptyList(),
    // Other titles MAL's own "recommendations" field surfaces for this one (from the site's
    // user-submitted userrecs page) — populated from the official per-title fetch, same as
    // related/themes above; MAL's bulk list/ranking/season endpoints don't reliably return it either.
    val recommended: List<RecommendedEntry> = emptyList(),
    // Community status breakdown across every MAL member tracking this anime (MAL's own `statistics`
    // field on the single-title endpoint — anime only, see MalApi.fields/parseEntry) — populated the
    // same backfill way as related/themes/recommended, powering the Detail screen's bottom-of-page
    // "Status distribution" section.
    val statusDistribution: StatusDistribution = StatusDistribution(),
    // ISO-8601 timestamp of when the user last touched this entry (from MAL's list_status.updated_at,
    // or stamped locally on save) — drives which title the Home "Continue" card surfaces.
    val updatedAt: String = "",
    // Day of the week a currently-airing anime broadcasts, e.g. "Friday" — powers the Home "Today's release" row.
    val broadcastDay: String = "",
    // Broadcast time as "HH:mm" in JST, paired with broadcastDay above — always converted to the
    // device's own timezone via MediaItem.localBroadcast() before being grouped or displayed.
    val broadcastTime: String = "",
    // MAL's own content rating for this title: "white" (safe), "gray" (suggestive), "black" (hentai).
    // Kept for reference/future use, but the Profile "Adult content" toggle is now driven by the
    // "Hentai" genre tag (see isAdultContent()) rather than this field — "gray" here also covers
    // merely suggestive/ecchi titles that aren't actually hentai, which this field alone can't distinguish.
    val nsfw: String = "white",
    // True once this title is actually tracked on the user's MAL list (has a list_status entry).
    // False for a Discover/search result the user hasn't added yet — defaults true since every other
    // source of MediaItem (the user's list, the seed library) already represents a tracked entry.
    val inUserList: Boolean = true,
)
// True when this title is flagged nsfw per MAL's own rating — "black" (hentai) or "gray" (partial
// nudity/borderline) — the ones filtered out unless the user opts in. Shared by every screen's list
// (List, Discover, Ranking, Recommendations, Seasonal) via nsfwFiltered below.
// MAL tags actual hentai with its own "Hentai" genre — unlike the nsfw field (white/gray/black),
// which lumps in merely suggestive/ecchi titles under "gray", this only catches the real thing.
private fun MediaItem.isAdultContent() = genres.any { it.equals("Hentai", ignoreCase = true) }
private fun List<MediaItem>.nsfwFiltered(allowAdult: Boolean) = if (allowAdult) this else filterNot { it.isAdultContent() }
data class RelatedEntry(val relation: String, val title: String, val malId: Int = 0, val malType: String = "anime", val cover: String = "")
// Community status breakdown across every MAL member tracking an anime — MAL's `statistics.status`
// object, counts as returned by the API (see MalApi.fields/parseEntry). `total` is used as the
// denominator for each status's proportional bar, same idea as the Profile screen's own list stats.
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

// ---------- ViewModel ----------
enum class DiscoverMode { Browse, Results }
// Discover's advanced filters — combined and applied together against whatever candidate pool the
// search turned up (a title query's results, or, when the query is blank, a broad ranking pool —
// see LibraryViewModel.runDiscoverSearch). MAL's public search endpoint only takes a title query, so
// there's no server-side way to filter by genre/studio/source/year/season/rating directly; every one
// of these fields is already present on MediaItem though (see MalApi.parseEntry), so filtering here
// client-side needs no extra API surface.
data class DiscoverFilters(
    val genres: Set<String> = emptySet(),
    val themes: Set<String> = emptySet(),
    val demographics: Set<String> = emptySet(),
    val studio: String = "",
    val source: String = "",
    val year: String = "",
    val season: SeasonName? = null,
    val rating: String = "",
    // Sub-type within Anime/Manga — TV/OVA/Movie/... for anime, Manga/Manhwa/One Shot/... for manga.
    // Named "format" (not "type") to avoid confusion with the Anime/Manga/All toggle, which is a
    // separate, coarser filter (discoverTypeFilter) that lives outside DiscoverFilters entirely.
    val format: String = "",
) {
    fun isActive() = genres.isNotEmpty() || themes.isNotEmpty() || demographics.isNotEmpty() || studio.isNotBlank() || source.isNotBlank() || year.isNotBlank() || season != null || rating.isNotBlank() || format.isNotBlank()
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
    return true
}
// MAL's full genre taxonomy (myanimelist.net/anime.php / manga.php sidebar), split into the same
// four facets MAL itself uses. Genre/Explicit Genre both match against MediaItem.genres (MAL's API
// returns explicit genres — Ecchi/Erotica/Hentai — as part of the same "genres" list, see
// MalApi.fields/parseEntry), kept as separate sections here only so the sheet doesn't bury three
// adult-content tags inside sixteen ordinary ones.
val CommonGenres = listOf("Action", "Adventure", "Avant Garde", "Award Winning", "Boys Love", "Comedy", "Drama", "Fantasy", "Girls Love", "Gourmet", "Horror", "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Suspense")
val CommonExplicitGenres = listOf("Ecchi", "Erotica", "Hentai")
// MAL's "themes" facet — a finer-grained tag sitting below genre (e.g. an Isekai can be Action,
// Adventure, or Comedy genre-wise, but Isekai itself is the theme) — already fetched into
// MediaItem.contentThemes (see MalApi.fields/parseEntry) for the Detail screen's Recommended-row
// scoring, just not previously surfaced as its own filter.
val CommonThemes = listOf("Adult Cast", "Anthropomorphic", "CGDCT", "Childcare", "Combat Sports", "Crossdressing", "Delinquents", "Detective", "Educational", "Gag Humor", "Gore", "Harem", "High Stakes Game", "Historical", "Idols (Female)", "Idols (Male)", "Isekai", "Iyashikei", "Love Polygon", "Magical Sex Shift", "Mahou Shoujo", "Martial Arts", "Mecha", "Medical", "Military", "Music", "Mythology", "Organized Crime", "Otaku Culture", "Parody", "Performing Arts", "Pets", "Psychological", "Racing", "Reincarnation", "Reverse Harem", "Romantic Subtext", "Samurai", "School", "Showbiz", "Space", "Strategy Game", "Super Power", "Survival", "Team Sports", "Time Travel", "Vampire", "Video Game", "Villainess", "Visual Arts", "Workplace")
// MAL's "demographics" facet — the intended readership/audience (Shounen, Seinen, ...), matched
// against MediaItem.demographics.
val CommonDemographics = listOf("Josei", "Kids", "Seinen", "Shoujo", "Shounen")
val CommonSources = listOf("Original", "Manga", "Light Novel", "Novel", "Visual Novel", "Game", "Web Manga", "Web Novel", "4-Koma Manga", "Other")
val CommonRatings = listOf("G - All Ages", "PG - Children", "PG-13", "R - 17+ (violence & profanity)", "R+ - Mild Nudity", "Rx - Hentai")
// Sub-type ("format") options, split by media kind since they don't overlap — matched against
// MediaItem.format (MalApi.prettifyFormat's output, e.g. "TV", "One Shot").
val CommonAnimeFormats = listOf("TV", "OVA", "Movie", "Special", "ONA", "Music")
val CommonMangaFormats = listOf("Manga", "Novel", "Light Novel", "One Shot", "Doujinshi", "Manhwa", "Manhua", "OEL")
// Discover search-result sort order. Newest compares full start dates (falling back to just the
// year when that's all a title has) so titles are ordered the same way regardless of which they have.
enum class DiscoverSort(val label: String) { Members("Members"), Score("Score"), Newest("Newest"), Title("Title") }
private fun List<MediaItem>.sortedForDiscover(sort: DiscoverSort, titleLanguage: TitleLanguage): List<MediaItem> = when (sort) {
    DiscoverSort.Members -> sortedByDescending { it.listUsers }
    DiscoverSort.Score -> sortedByDescending { it.score }
    DiscoverSort.Newest -> sortedByDescending { it.startDateFull.ifBlank { it.startDate } }
    DiscoverSort.Title -> sortedBy { it.resolvedTitle(titleLanguage).lowercase() }
}
enum class ForumMode { Boards, Topics }
// Anime/manga ranking chart filters — maps to MAL's ranking_type query param. Manga has no "upcoming" chart.
enum class RankingSort(val label: String) {
    Score("Score"), Popularity("Popularity"), Favorite("Favorites"), Upcoming("Upcoming");
    fun apiValue(): String = when (this) { Score -> "all"; Popularity -> "bypopularity"; Favorite -> "favorite"; Upcoming -> "upcoming" }
}
// The four MAL broadcast seasons, in calendar order, each with an icon for the season-picker row.
enum class SeasonName(val api: String, val label: String, val icon: ImageVector) {
    Winter("winter", "Winter", Icons.Default.AcUnit),
    Spring("spring", "Spring", Icons.Default.LocalFlorist),
    Summer("summer", "Summer", Icons.Default.BeachAccess),
    Fall("fall", "Fall", Icons.Default.Park),
}
private fun SeasonName.prev() = SeasonName.entries[(ordinal + 3) % 4]
private fun SeasonName.next() = SeasonName.entries[(ordinal + 1) % 4]
// Steps one season forward/back, rolling the year over at the Winter/Fall boundary.
private fun stepSeason(year: Int, season: SeasonName, forward: Boolean): Pair<Int, SeasonName> = when {
    forward && season == SeasonName.Fall -> year + 1 to SeasonName.Winter
    forward -> year to season.next()
    !forward && season == SeasonName.Winter -> year - 1 to SeasonName.Fall
    else -> year to season.prev()
}
private fun currentSeasonName(): SeasonName = when ((java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)) {
    in 1..3 -> SeasonName.Winter; in 4..6 -> SeasonName.Spring; in 7..9 -> SeasonName.Summer; else -> SeasonName.Fall
}
// Seasonal chart sort — maps to MAL's season-endpoint sort param.
enum class SeasonalSort(val api: String, val label: String) {
    Members("anime_num_list_users", "Members"),
    Score("anime_score", "Score"),
}
// "My list" screen sort order. LastUpdated/StartDate are the person's own tracking dates
// (watchStartDate / updatedAt on MediaItem), not MAL's own airing/publication dates — labelled
// "Last Updated" and "Start Date" for what the person actually did with the title.
enum class ListSort(val label: String) { Title("Title"), Score("Score"), LastUpdated("Last Updated"), StartDate("Start Date") }
private fun nowIso(): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
// Converts a currently-airing title's broadcast day + time — as MAL reports them, always JST — into
// the equivalent day-of-week and time in the device's own timezone. Anchored to the next occurrence
// of that weekday in JST (any occurrence works, since only the weekday/time-of-day are kept) so a
// late-night JST slot that lands after midnight locally, or before it, still lands on the correct
// local day instead of just carrying the raw JST weekday over unconverted.
private fun MediaItem.localBroadcast(): Pair<java.time.DayOfWeek, java.time.LocalTime>? {
    val dow = runCatching { java.time.DayOfWeek.valueOf(broadcastDay.uppercase(java.util.Locale.US)) }.getOrNull() ?: return null
    val time = runCatching { java.time.LocalTime.parse(broadcastTime) }.getOrDefault(java.time.LocalTime.MIDNIGHT)
    val jst = java.time.ZoneId.of("Asia/Tokyo")
    val anchor = java.time.LocalDate.now(jst).with(java.time.temporal.TemporalAdjusters.nextOrSame(dow)).atTime(time).atZone(jst)
    val local = anchor.withZoneSameInstant(java.time.ZoneId.systemDefault())
    return local.dayOfWeek to local.toLocalTime()
}
// "Airs today" / "Airs tomorrow" / "Next ep in 4d" — derived from broadcast day/time (already
// converted to the device's local timezone by localBroadcast()), anchored to the next occurrence of
// that weekday/time from right now. Only meaningful while the title is still currently airing; MAL's
// broadcast field lingers on finished titles too, so airStatus gates it off once a show has wrapped.
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
// Locale-correct short time string (e.g. "10:00 AM" or "22:00" depending on the device's locale).
private fun localizedTimeLabel(time: java.time.LocalTime): String =
    time.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(java.util.Locale.getDefault()))

class LibraryViewModel : ViewModel() {
    // Starts empty rather than pre-populated with seedItems()'s sample titles — a signed-out (or
    // genuinely list-less) person should see the real "No titles here yet." empty state, not fake
    // entries like Frieren/Dungeon Meshi that were never actually on anyone's MAL list.
    var items by mutableStateOf(emptyList<MediaItem>()); private set
    var destination by mutableStateOf(Destination.Home)
    var signedIn by mutableStateOf(false); var loading by mutableStateOf(false); var error by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(ThemeMode.System)
    var colorSource by mutableStateOf(ColorSource.AppDefault); private set
    var paletteStyle by mutableStateOf(PaletteStyle.TonalSpot); private set
    var customColorHex by mutableStateOf("2E51A2"); private set
    var titleLanguage by mutableStateOf(TitleLanguage.Romaji)
    var listFilter by mutableStateOf("All")
    // Also hoisted (rather than left as local `remember` state inside ListScreen) for the same
    // reason as seasonalScrollIndex below: AnimatedContent fully disposes ListScreen while a Detail
    // screen is on top, so anything kept in local state there is lost by the time "back" returns to it.
    var listTypeTab by mutableStateOf(MediaType.Anime); private set
    var listSort by mutableStateOf(ListSort.Title); private set
    var listScrollIndex by mutableStateOf(0); private set
    var listScrollOffset by mutableStateOf(0); private set
    fun saveListScroll(index: Int, offset: Int) { listScrollIndex = index; listScrollOffset = offset }
    // Anime and manga are different lists, and a new sort re-orders the one on screen — in both
    // cases the old scroll position points at the wrong row, so start back at the top.
    fun selectListTypeTab(t: MediaType) { listTypeTab = t; listScrollIndex = 0; listScrollOffset = 0 }
    fun setListSort(context: Context, sort: ListSort) { listSort = sort; listScrollIndex = 0; listScrollOffset = 0; settingsPrefs(context).edit().putString("list_sort", sort.name).apply() }
    fun loadListSort(context: Context) { listSort = runCatching { ListSort.valueOf(settingsPrefs(context).getString("list_sort", ListSort.Title.name)!!) }.getOrDefault(ListSort.Title) }
    // Off by default — hentai-rated titles are hidden everywhere in the app until the person opts in.
    var nsfwEnabled by mutableStateOf(false); private set
    // The signed-in person's own MAL account details + anime stats, shown on the Profile tab.
    var malProfile by mutableStateOf<MalProfile?>(null); private set
    var profileLoading by mutableStateOf(false); private set

    // ---- App updates (Profile > Check for updates, plus the auto background check) ----
    // Newest release known to be newer than this build, if any — populated from cache instantly on
    // launch, then refreshed by a real GitHub check. null means "nothing newer that we know of".
    var updateInfo by mutableStateOf<AppUpdateInfo?>(null); private set
    var updateChecking by mutableStateOf(false); private set
    // Set right after a manual check finishes with nothing newer, so the Profile row can say "You're
    // up to date" once — cleared as soon as the row's tapped again so it doesn't linger stale.
    var updateUpToDateMessage by mutableStateOf(false)
    var updateDialogOpen by mutableStateOf(false)
    var updateDownloadProgress by mutableStateOf<Float?>(null); private set
    var updateNeedsInstallPermission by mutableStateOf(false)
    var updateError by mutableStateOf<String?>(null)

    // Instant, no-network read of whatever the last successful check found — call on launch so the
    // Profile badge and (if due) auto-check both have something to work with right away.
    fun loadCachedUpdate(context: Context) {
        val checker = AppUpdateChecker(context)
        val cached = checker.cached() ?: return
        if (cached.version != checker.skippedVersion()) updateInfo = cached
    }
    // manual = true (the Profile row) always hits the network and reports "up to date" when there's
    // nothing new. manual = false (the launch-time auto-check) checks quietly and only surfaces a
    // result when there's actually something new to show, via onFound — used to fire a notification.
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
    // Downloads the release APK with live progress, then hands straight off to the system installer
    // — checks REQUEST_INSTALL_PACKAGES first since a wasted download just to hit that wall is a bad
    // experience on a slow connection.
    fun downloadAndInstallUpdate(context: Context) {
        val info = updateInfo ?: return
        val checker = AppUpdateChecker(context)
        if (!checker.canRequestInstall()) { updateNeedsInstallPermission = true; return }
        updateDownloadProgress = 0f; updateError = null
        viewModelScope.launch {
            checker.downloadApk(info) { progress -> updateDownloadProgress = progress }
                .onSuccess { file -> updateDownloadProgress = null; updateDialogOpen = false; checker.installApk(file) }
                .onFailure { updateDownloadProgress = null; updateError = it.message ?: "Download failed" }
        }
    }

    // Every list-of-titles surface, filtered by the current NSFW preference — composables should
    // read these instead of the raw fields below so toggling the setting updates every screen at once.
    val visibleItems get() = items.nsfwFiltered(nsfwEnabled)
    val visibleDiscoverResults get() = discoverResults.nsfwFiltered(nsfwEnabled).filter { it.matches(discoverFilters) }.sortedForDiscover(discoverSort, titleLanguage)
    val visibleDiscoverNewSeason get() = discoverNewSeason.nsfwFiltered(nsfwEnabled)
    val visibleDiscoverUpcoming get() = discoverUpcoming.nsfwFiltered(nsfwEnabled)
    val visibleRecommendations get() = recommendations.nsfwFiltered(nsfwEnabled)
    val visibleRankingResults get() = rankingResults.nsfwFiltered(nsfwEnabled)
    // MAL's season chart mixes anime that premiered this season with ones still airing from an
    // earlier season — seasonalResults holds the raw chart as fetched; this filters it down to just
    // the premieres unless "still airing from before" is toggled on.
    val visibleSeasonalResults: List<MediaItem> get() {
        val filtered = if (seasonalContinuingOnly) seasonalResults
        else seasonalResults.filter { it.startDate == seasonalYear.toString() && it.season.equals(seasonalSeason.label, ignoreCase = true) }
        return filtered.nsfwFiltered(nsfwEnabled)
    }

    // Discover: kept here (not local composable state) so it survives navigating into a detail
    // screen and back — that round trip disposes and recreates DiscoverScreen's composition.
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

    // Personalized "because you're tracking X" row for Home, based on the signed-in user's list.
    var recommendations by mutableStateOf<List<MediaItem>>(emptyList()); private set
    private var homeExtrasLoaded = false

    // Ranking chart — its own full-screen destination reached from Home, so state lives here (survives
    // opening a title's detail from the chart and pressing back).
    var rankingType by mutableStateOf(MediaType.Anime); private set
    var rankingSort by mutableStateOf(RankingSort.Score); private set
    var rankingResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var rankingLoading by mutableStateOf(false); private set
    var rankingError by mutableStateOf<String?>(null); private set

    // Seasonal chart — same reasoning: lives here so year/season/sort survive a detail round trip.
    var seasonalYear by mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)); private set
    var seasonalSeason by mutableStateOf(currentSeasonName()); private set
    var seasonalSort by mutableStateOf(SeasonalSort.Members); private set
    // "Still airing from before" — a display filter, not a fetch param: MAL's season endpoint itself
    // returns both anime that premiered this season and ones still airing from an earlier season
    // (see visibleSeasonalResults), so this just decides whether to keep the latter in view.
    var seasonalContinuingOnly by mutableStateOf(false); private set
    // Raw chart as fetched from MAL — unfiltered by seasonalContinuingOnly (see visibleSeasonalResults).
    var seasonalResults by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var seasonalHasMore by mutableStateOf(false); private set
    var seasonalLoading by mutableStateOf(false); private set
    var seasonalLoadingMore by mutableStateOf(false); private set
    var seasonalError by mutableStateOf<String?>(null); private set
    // Scroll position captured right before opening a title from the chart, restored when returning.
    // Compose's own state-saving (rememberSaveable) doesn't survive SeasonalScreen being fully
    // disposed while Detail is shown on top of it — this isn't backstack-aware navigation, just a
    // composable swapped out and back in — so the position is held here instead, alongside the rest
    // of the state that already needs to survive that round trip.
    var seasonalScrollIndex by mutableStateOf(0); private set
    var seasonalScrollOffset by mutableStateOf(0); private set
    fun saveSeasonalScroll(index: Int, offset: Int) { seasonalScrollIndex = index; seasonalScrollOffset = offset }

    // Called when leaving the Seasonal Chart page — it starts fresh (real current season, default
    // sort, filter off) on every visit rather than remembering the last thing browsed.
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
    // Only persists (and applies) a hex string once it's actually a valid 6-digit color, so a person
    // mid-typing never flashes an invalid/fallback color while they're still entering it.
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

    // Loads the signed-in person's own MAL account details + anime stats for the Profile tab.
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
    // Mirrors saveLive above: removes it from the local list immediately (so the UI feels instant),
    // then — if signed in — deletes it from the person's actual MAL list too. On failure the entry
    // stays deleted locally (matching saveLive's "local change always wins" behavior) but surfaces
    // the same error banner, since silently leaving it on MAL after the app "deleted" it would just
    // recreate this same bug on the next full sync from the server.
    fun deleteLive(context: Context, item: MediaItem) {
        delete(item.id)
        if (signedIn) viewModelScope.launch { runCatching { MalApi(context).deleteEntry(item) }.onFailure { error = "MAL sync failed: ${it.message ?: "unknown error"}" } }
    }
    fun signOut(context: Context) { MalApi(context).signOut(); signedIn = false; items = emptyList(); malProfile = null }

    // Loads the two browse rows once (per sign-in); cheap to call repeatedly from LaunchedEffect(Unit).
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
    // Loads the Home "Recommendations" row once (per sign-in); cheap to call repeatedly from LaunchedEffect(Unit).
    fun loadHomeExtras(context: Context) {
        if (homeExtrasLoaded || !MalApi(context).signedIn) return
        homeExtrasLoaded = true
        viewModelScope.launch { runCatching { MalApi(context).animeSuggestions(10) }.onSuccess { recommendations = it } }
    }
    // (Re)runs the anime/manga ranking chart for the given type + sort; Manga has no "upcoming" chart.
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
    // (Re)runs the seasonal chart for the given year/season/sort — always a fresh first page.
    // continuingOnly is stored for visibleSeasonalResults to read but no longer changes what's
    // fetched (see its comment): both new and continuing anime come back from one request.
    fun loadSeasonal(context: Context, year: Int = seasonalYear, season: SeasonName = seasonalSeason, sort: SeasonalSort = seasonalSort, continuingOnly: Boolean = seasonalContinuingOnly) {
        seasonalYear = year; seasonalSeason = season; seasonalSort = sort; seasonalContinuingOnly = continuingOnly
        if (!MalApi(context).signedIn) { seasonalError = "Sign in from Profile to browse seasons"; return }
        viewModelScope.launch {
            seasonalLoading = true
            runCatching { MalApi(context).seasonalAnime(year, season.api, sort = sort.api) }
                .onSuccess { seasonalResults = it.items; seasonalHasMore = it.hasMore; seasonalError = null }
                .onFailure { seasonalError = it.message ?: "Could not load season"; seasonalHasMore = false }
            seasonalLoading = false
        }
    }

    // Fetches the next page of the current season chart and appends it — called as the grid nears
    // the bottom. offset is the raw fetched count so far (not the filtered/visible count), since
    // that's what lines up with MAL's own paging over the unfiltered chart.
    fun loadMoreSeasonal(context: Context) {
        if (seasonalLoading || seasonalLoadingMore || !seasonalHasMore) return
        val api = MalApi(context)
        if (!api.signedIn) return
        viewModelScope.launch {
            seasonalLoadingMore = true
            runCatching { api.seasonalAnime(seasonalYear, seasonalSeason.api, offset = seasonalResults.size, sort = seasonalSort.api) }
                .onSuccess { seasonalResults = seasonalResults + it.items; seasonalHasMore = it.hasMore }
                .onFailure { seasonalHasMore = false }
            seasonalLoadingMore = false
        }
    }
    // Switches Discover into its separate Results page and (re)runs the search there — called both
    // from the Browse page's search box (first keystroke) and from the filter chips on Results itself.
    fun runDiscoverSearch(context: Context, query: String, type: String, filters: DiscoverFilters = discoverFilters) {
        discoverQuery = query; discoverTypeFilter = type; discoverFilters = filters; discoverMode = DiscoverMode.Results
        discoverSearchJob?.cancel()
        if (query.isBlank() && !filters.isActive()) { discoverResults = emptyList(); discoverSearching = false; discoverError = null; return }
        if (!MalApi(context).signedIn) { discoverError = "Sign in from Profile to search MyAnimeList"; return }
        discoverSearchJob = viewModelScope.launch {
            discoverSearching = true
            val t = when (type) { "Anime" -> MediaType.Anime; "Manga" -> MediaType.Manga; else -> null }
            val api = MalApi(context)
            runCatching {
                if (query.isNotBlank()) api.search(query, t)
                // Filter-only browse with a genre/theme/demographic chip selected (e.g. the theme-only
                // case this whole branch exists for — "Villainess" with no title typed): MAL's own API
                // has no genre/theme/demographic search param at all (see DiscoverFilters above), so
                // this goes through Tenrai (tenrai.org) — an unofficial, authless MAL mirror that does
                // expose real genre-id search — to build the candidate pool instead of the generic
                // ranking-chart pool below. visibleDiscoverResults still re-applies the exact same
                // matches() filtering afterwards, so this only ever needs to be a superset.
                else if (filters.genres.isNotEmpty() || filters.themes.isNotEmpty() || filters.demographics.isNotEmpty()) {
                    val tenrai = TenraiApi()
                    val kinds = t?.let { listOf(if (it == MediaType.Anime) "anime" else "manga") } ?: listOf("anime", "manga")
                    // Any single selected facet's own id set is already a valid (if imperfect) superset
                    // of the true match set once matches() runs locally afterwards — no need to query
                    // every facet. Themes preferred first since that's specifically the facet with no
                    // other server-side way to search it at all.
                    val names = filters.themes.ifEmpty { filters.genres.ifEmpty { filters.demographics } }
                    runCatching {
                        coroutineScope {
                            kinds.map { kind -> async { tenrai.searchByGenreIds(kind, tenrai.resolveGenreIds(kind, names), includeAdult = nsfwEnabled) } }
                                .awaitAll().flatten()
                        }
                    }.getOrElse {
                        // Tenrai unreachable — fall back to the old ranking-chart pool so filtering
                        // still works, just without the extra theme/demographic recall.
                        coroutineScope {
                            (t?.let { listOf(it) } ?: MediaType.entries.toList()).flatMap { mt ->
                                listOf("all", "bypopularity", "favorite").map { rankType -> async { api.ranking(mt, rankType, limit = 500) } }
                            }.awaitAll().flatten()
                        }
                    }
                        .distinctBy { it.id }
                        // Tenrai has no concept of the signed-in account, so every title it returns
                        // starts as untracked (see TenraiApi.parseJikanEntry) — swap in the person's own
                        // library entry wherever one already exists, the same way a title search result
                        // that's already on their MAL list would come back with list_status attached.
                        .map { candidate -> items.find { it.id == candidate.id && it.type == candidate.type } ?: candidate }
                }
                // Filter-only browse with no genre/theme/demographic chip (just studio/source/year/
                // season/rating/format): pull a broad candidate pool per type and let
                // visibleDiscoverResults narrow it with the filters instead. Pulling only "all" (score)
                // and "bypopularity" wasn't enough — both are essentially the same top-of-chart titles
                // reordered, so anything niche (a whole format like Manhwa/Light Novel) was simply never
                // in the pool to begin with. Instead this pulls from every one of MAL's own ranking
                // charts per type — which each partition the database along a different axis
                // (popularity, airing status, format) — and merges/dedupes the union, giving a far wider
                // and more varied candidate set for the same idea. Fetched in parallel so the extra
                // charts don't multiply wait time.
                else coroutineScope {
                    (t?.let { listOf(it) } ?: MediaType.entries.toList()).flatMap { mt ->
                        val rankTypes = if (mt == MediaType.Anime)
                            listOf("all", "bypopularity", "favorite", "airing", "upcoming", "tv", "ova", "movie", "special")
                        else
                            listOf("all", "bypopularity", "favorite", "manga", "novels", "oneshots", "doujin", "manhwa", "manhua")
                        rankTypes.map { rankType -> async { api.ranking(mt, rankType, limit = 500) } }
                    }.awaitAll().flatten()
                }.distinctBy { it.id }
            }
                .onSuccess { discoverResults = it; discoverError = null }
                .onFailure { discoverError = it.message ?: "Search failed" }
            discoverSearching = false
        }
    }
    // Leaves the Results page and returns to Discover's default browse view.
    fun exitDiscoverSearch() {
        discoverSort = DiscoverSort.Members
        discoverSearchJob?.cancel()
        discoverMode = DiscoverMode.Browse; discoverQuery = ""; discoverResults = emptyList(); discoverFilters = DiscoverFilters(); discoverError = null
    }

    // Forums: boards/subboards + whichever topic list is currently being browsed (a board's own
    // topics, or a cross-board search) all live here rather than as local composable state, for the
    // same reason Discover's search state does above — this survives opening a topic and pressing
    // back, since that round trip disposes and recreates ForumsScreen's composition.
    var forumMode by mutableStateOf(ForumMode.Boards); private set
    var forumCategories by mutableStateOf<List<ForumCategory>>(emptyList()); private set
    var forumBoardsLoading by mutableStateOf(false); private set
    var forumBoardsError by mutableStateOf<String?>(null); private set
    private var forumBoardsLoaded = false
    // Blank forumBoardTitle means the open topic list is a cross-board search (forumQuery) rather
    // than a board being browsed — ForumTopicsScreen's header falls back to "Search results" then.
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

    // Loads the board hierarchy once (per sign-in); cheap to call repeatedly from LaunchedEffect(Unit).
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
    // Switches Forums into a board's topic list — same "separate page, state lives in the ViewModel"
    // pattern as runDiscoverSearch above, so opening a topic and pressing back lands right back here.
    fun openForumBoard(context: Context, board: ForumBoard) {
        forumMode = ForumMode.Topics
        forumBoardTitle = board.title; forumBoardId = board.id; forumSubboards = board.subboards; forumSubboardId = null; forumQuery = ""
        runForumTopics(context)
    }
    // Narrows (or, with subboardId == null, clears) the currently-open board down to one subboard.
    fun openForumSubboard(context: Context, subboardId: Int?) {
        forumSubboardId = subboardId
        runForumTopics(context)
    }
    // Cross-board keyword search — same Topics page as browsing a board, just with forumBoardId null
    // and forumQuery set instead (see forumBoardTitle's comment above).
    fun runForumSearch(context: Context, query: String) {
        forumMode = ForumMode.Topics
        forumBoardTitle = ""; forumBoardId = null; forumSubboards = emptyList(); forumSubboardId = null; forumQuery = query
        runForumTopics(context)
    }
    private fun runForumTopics(context: Context) {
        forumTopicsJob?.cancel()
        if (!MalApi(context).signedIn) { forumTopicsError = "Sign in from Profile to browse the forums"; return }
        forumTopicsJob = viewModelScope.launch {
            forumTopicsLoading = true
            runCatching { MalApi(context).forumTopics(boardId = forumBoardId, subboardId = forumSubboardId, query = forumQuery) }
                .onSuccess { forumTopics = it.items; forumHasMore = it.hasMore; forumTopicsError = null }
                .onFailure { forumTopicsError = it.message ?: "Could not load topics" }
            forumTopicsLoading = false
        }
    }
    // Fetches the next page of the current board/search's topic list and appends it — called as the
    // list nears the bottom, same idea as loadMoreSeasonal above.
    fun loadMoreForumTopics(context: Context) {
        if (forumTopicsLoading || forumLoadingMore || !forumHasMore) return
        val api = MalApi(context); if (!api.signedIn) return
        viewModelScope.launch {
            forumLoadingMore = true
            runCatching { api.forumTopics(boardId = forumBoardId, subboardId = forumSubboardId, query = forumQuery, offset = forumTopics.size) }
                .onSuccess { forumTopics = forumTopics + it.items; forumHasMore = it.hasMore }
                .onFailure { forumHasMore = false }
            forumLoadingMore = false
        }
    }
    // Leaves the Topics page and returns to the Forums board list.
    fun exitForumTopics() {
        forumTopicsJob?.cancel()
        forumMode = ForumMode.Boards; forumBoardTitle = ""; forumBoardId = null; forumSubboards = emptyList(); forumSubboardId = null; forumQuery = ""
        forumTopics = emptyList(); forumTopicsError = null; forumHasMore = false
    }

    // Which Related-row card (by MAL id) is currently being fetched, so DetailScreen can show a
    // small loading state on just that card while the rest of the row stays tappable.
    var relatedLoadingId by mutableStateOf<Int?>(null); private set

    // Same idea as relatedLoadingId, for the Recommended row.
    var recommendedLoadingId by mutableStateOf<Int?>(null); private set

    // Which Discover search-result row (by MAL id, as string) is currently being fetched, so that
    // row can show a loading state while the rest of the results list stays tappable.
    var discoverDetailLoadingId by mutableStateOf<String?>(null); private set

    // A search/browse result only carries whatever fields MAL's list endpoint happened to return for
    // it — occasionally missing things like Related that the single-title endpoint does have. Rather
    // than open Detail with that partial copy and patch pieces in after the fact, this fetches the
    // full single-title record first and only then hands it to onLoaded to navigate with.
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

    // Fetches full details for a Related-row entry and hands the result to onLoaded (which the
    // caller uses to navigate into it) — this is what lets tapping a related title open Kiko's own
    // Detail screen instead of MAL's website.
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

    // MAL's user-list endpoint sometimes comes back with an empty Related row for a title even
    // though MAL does have relations for it (visible on the website, returned by the single-title
    // endpoint). DetailScreen calls this once per title when Related looks empty, so it can quietly
    // backfill from the more reliable endpoint without the person needing to do anything. Purely a
    // display fix for the current screen — doesn't touch vm.items, so it can't add an untracked
    // title (e.g. one found via Discover) to the person's list as a side effect. onDone always fires
    // exactly once (success, failure, or invalid id) so the caller can gate a "loaded" state on it.
    fun backfillRelated(context: Context, id: String, type: MediaType, onFound: (List<RelatedEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.related.isNotEmpty()) onFound(fresh.related) }
            onDone()
        }
    }

    // Same story as backfillRelated, but for opening/ending themes: MAL's bulk list/ranking/season/
    // suggestions endpoints don't reliably return opening_themes/ending_themes even though they're
    // requested in `fields`, while the single-title endpoint has them. DetailScreen calls this once
    // per title when both theme lists look empty, so it can quietly backfill from the more reliable
    // endpoint without the person needing to do anything. Purely a display fix for the current
    // screen — doesn't touch vm.items. onDone always fires exactly once, same contract as above.
    fun backfillThemes(context: Context, id: String, type: MediaType, onFound: (List<String>, List<String>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.openingThemes.isNotEmpty() || fresh.endingThemes.isNotEmpty()) onFound(fresh.openingThemes, fresh.endingThemes) }
            onDone()
        }
    }

    // Same story as backfillRelated/backfillThemes, for the cover gallery: the bulk list/ranking/
    // season/search endpoints never request MAL's "pictures" field (only the single-title detail()
    // fetch does — see MalApi.detail), so a title opened from the person's own list, Home, Ranking,
    // etc. starts out with just its one main cover. DetailScreen calls this once per title when
    // covers looks empty, so the fullscreen viewer can still be swiped through once it lands. Purely
    // a display fix for the current screen — doesn't touch vm.items. onDone always fires exactly
    // once, same contract as the backfills above.
    fun backfillCovers(context: Context, id: String, type: MediaType, onFound: (List<String>) -> Unit, onDone: () -> Unit = {}) {
        val intId = id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).detail(intId, type) }
                .onSuccess { fresh -> if (fresh.covers.size > 1) onFound(fresh.covers) }
            onDone()
        }
    }

    // The Detail screen's "Recommended" row: other titles MAL's own per-title endpoint surfaces via
    // its `recommendations` field (see MalApi.userRecommendations) — not returned by the bulk list/
    // ranking/season endpoints, so this backfills the same way related/themes do. onDone always fires
    // exactly once, same contract as the two backfills above.
    fun loadUserRecommendations(context: Context, item: MediaItem, onFound: (List<RecommendedEntry>) -> Unit, onDone: () -> Unit = {}) {
        val intId = item.id.toIntOrNull()
        if (intId == null) { onDone(); return }
        viewModelScope.launch {
            runCatching { MalApi(context).userRecommendations(intId, item.type) }
                .onSuccess { if (it.isNotEmpty()) onFound(it) }
            onDone()
        }
    }

    // Fetches full details for a Recommended-row entry and hands the result to onLoaded (which the
    // caller uses to navigate into it) — same idea as openRelated, for the Recommended row.
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

    // The Detail screen's bottom-of-page "Status distribution" section — how every MAL member
    // tracking this anime has it filed (watching/completed/on-hold/dropped/plan-to-watch), from
    // MAL's own official `statistics` field on the single-title endpoint (see MalApi.detail/
    // fields). Anime only, same as the field itself. Same non-blocking backfill contract as the
    // other Detail-screen loaders above.
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

// ---------- App update notification ----------
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
// Tapping the notification just reopens the app — Profile's "Check for updates" row picks the
// release straight back up from AppUpdateChecker's cache, no re-fetch needed.
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

// ---------- Activity ----------
class MainActivity : ComponentActivity() {
    private var callback by mutableStateOf<Uri?>(null)
    // Set when the app is opened via a myanimelist.net link (as opposed to the OAuth redirect,
    // which uses the app's own com.kiko.tracker:// scheme) — routed to KikoApp to open Detail directly.
    private var malLink by mutableStateOf<Uri?>(null)
    // Holds an update the launch-time auto-check found while the POST_NOTIFICATIONS prompt below is
    // in flight, so the permission callback can still post it once the person answers either way.
    private var pendingUpdateNotification: AppUpdateInfo? = null
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val info = pendingUpdateNotification; pendingUpdateNotification = null
        if (granted && info != null) { postUpdateNotification(this, info); AppUpdateChecker(this).markNotified(info.version) }
    }
    // Only called from the silent launch-time auto-check — the manual Profile button never needs a
    // system notification since the person is already looking straight at the result on screen.
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
        // Coil's default ImageLoader only decodes a GIF's first frame — every AsyncImage in the app
        // (forum post images, avatars, covers) otherwise renders an animated GIF as a static picture.
        // Registering the actual GIF decoders here (once, on Coil's app-wide singleton loader) is what
        // makes them play instead. ImageDecoderDecoder is the modern platform decoder (API 28+); GifDecoder
        // is the software fallback for the 26/27 range Kiko still supports (see minSdk in build.gradle.kts).
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(coil.decode.GifDecoder.Factory())
                }
                .build()
        )
        routeIntentUri(intent?.data)
        setContent {
            val vm: LibraryViewModel = viewModel()
            LaunchedEffect(Unit) { vm.loadTheme(this@MainActivity); vm.loadColorSource(this@MainActivity); vm.loadPaletteStyle(this@MainActivity); vm.loadCustomColor(this@MainActivity); vm.loadTitleLanguage(this@MainActivity); vm.loadListFilter(this@MainActivity); vm.loadListSort(this@MainActivity); vm.loadNsfwPref(this@MainActivity); vm.load(this@MainActivity); vm.loadDiscoverBrowse(this@MainActivity); vm.loadHomeExtras(this@MainActivity) }
            // Shows whatever the last check already knew about instantly, then — throttled to at most
            // once every 12h so relaunching the app repeatedly doesn't hammer GitHub's API — quietly
            // checks again in the background and, only if that turns up something new, notifies.
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
                        MalApi(this@MainActivity).finishAuth(uri).onSuccess { vm.load(this@MainActivity) }.onFailure { vm.error = it.message }
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

// Keeps the system status/navigation bars in sync with the current theme.
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

// ---------- Navigation transitions ----------
// Shared "push forward / pop back" motion used for every full-page navigation in the app (opening
// Detail/Ranking/Seasonal, returning from them, and Discover's own browse-to-results switch) — the
// incoming page slides in from the right while fading in, the outgoing page just fades out, and the
// reverse (going back) fades the returning page in while the leaving page slides out to the right.
// Switching between bottom-nav tabs (which aren't hierarchical — List isn't "above" Discover) just
// cross-fades instead of sliding, since neither tab is "forward" or "back" relative to the other.
private val PushEnter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 4 }
private val PushExit = fadeOut(tween(150))
private val PopEnter = fadeIn(tween(220))
private val PopExit = fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { it / 4 }
private val FadeEnter = fadeIn(tween(220))
private val FadeExit = fadeOut(tween(150))

// The app's top-level navigation state, gathered into one type so a single AnimatedContent can
// animate every full-page transition (Detail, Ranking, Seasonal, and the four bottom-nav tabs)
// consistently instead of each screen switch cutting instantly.
private sealed class TopScreen {
    data class Detail(val item: MediaItem) : TopScreen()
    object Ranking : TopScreen()
    object Seasonal : TopScreen()
    // initialDay is only consulted the moment this screen is created (via remember(initialDay) inside
    // ScheduleScreen) so it seeds which day tab opens on — it's not re-applied on later recompositions.
    data class Schedule(val initialDay: java.time.DayOfWeek) : TopScreen()
    // Reading a single forum topic — its own title travels along so the header can show it straight
    // away instead of waiting on the topic detail fetch to land first.
    data class Topic(val topicId: Int, val title: String) : TopScreen()
    data class Tab(val destination: Destination) : TopScreen()
}
// Distinguishes "same screen, updated data" (e.g. Detail's item getting a live-merge refresh) from
// "actually navigated elsewhere" — only the latter should replay the transition. Passed as
// AnimatedContent's contentKey so data updates within the same screen never retrigger the animation.
private fun TopScreen.navKey(): Any = when (this) {
    is TopScreen.Detail -> "detail:${item.id}"
    TopScreen.Ranking -> "ranking"
    TopScreen.Seasonal -> "seasonal"
    is TopScreen.Schedule -> "schedule"
    is TopScreen.Topic -> "topic:$topicId"
    is TopScreen.Tab -> "tab:$destination"
}
private fun TopScreen.isFullPage() = this is TopScreen.Detail || this is TopScreen.Ranking || this is TopScreen.Seasonal || this is TopScreen.Schedule || this is TopScreen.Topic

@Composable fun KikoApp(vm: LibraryViewModel = viewModel(), onSignIn: () -> Unit = {}, onSignOut: () -> Unit = {}, malLink: Uri? = null, onMalLinkHandled: () -> Unit = {}) {
    val context = LocalContext.current
    var editor by remember { mutableStateOf<MediaItem?>(null) }; var themeOpen by remember { mutableStateOf(false) }; var titleLangOpen by remember { mutableStateOf(false) }
    var colorSourceOpen by remember { mutableStateOf(false) }; var paletteStyleOpen by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    // Titles visited via a detail screen's own Related row, most-recent-last — lets "back" from a
    // related title return to the title that led there, instead of leaving Detail entirely. Opening
    // a title from anywhere else (List, Discover, a chart, a card) starts a clean stack.
    var detailStack by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    fun openDetail(item: MediaItem) { detailStack = emptyList(); selectedItem = item }
    fun openRelatedDetail(from: MediaItem, to: MediaItem) { detailStack = detailStack + from; selectedItem = to }
    fun backDetail() {
        val prev = detailStack.lastOrNull()
        if (prev != null) { selectedItem = prev; detailStack = detailStack.dropLast(1) } else selectedItem = null
    }
    // Handles arriving via a tapped myanimelist.net link (see MainActivity.routeIntentUri) — fetches
    // the title and opens it straight into Detail, same as tapping it anywhere inside the app.
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
    // Home's full-screen destinations — sit above the tab content, below Detail, same as the
    // Discover Results page pattern (their own back arrow / BackHandler, no bottom bar while open).
    var rankingOpen by remember { mutableStateOf(false) }
    var seasonalOpen by remember { mutableStateOf(false) }
    // Which day tab the Schedule screen should open on — set right before scheduleOpen flips to true
    // so "See all" on Home's "Today's release" lands straight on the user's current local day.
    var scheduleOpen by remember { mutableStateOf(false) }
    var scheduleInitialDay by remember { mutableStateOf(java.time.LocalDate.now().dayOfWeek) }
    fun openSchedule(day: java.time.DayOfWeek) { scheduleInitialDay = day; scheduleOpen = true }
    // Reading a single forum topic sits above the tab content the same way Ranking/Seasonal/Schedule
    // do — own back arrow, no bottom bar while open. Holds the topic's title alongside its id since
    // the topics list that opened it already has it in hand, so there's nothing to wait on for the header.
    var forumTopicOpen by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // Same live-merge as detailItem below: a search result's own copy never carries the user's
    // progress/status (MAL's search endpoint doesn't return list_status), so without this the
    // editor would open blank even for titles already being tracked.
    val editorItem = editor?.let { ed -> vm.visibleItems.find { it.id == ed.id } ?: vm.items.find { it.id == ed.id } ?: ed }
    // Prefer the live copy from vm.items (kept current as edits/syncs land); fall back to the
    // originally-selected item for titles found via Discover search that aren't on the list yet.
    val detailItem = selectedItem?.let { sel -> vm.items.find { it.id == sel.id } ?: sel }
    // At the tab level (nothing else — Detail, Ranking, Seasonal, Discover's search results — already
    // owns the back press), the first back press from any non-Home tab returns to Home instead of
    // leaving the app; only a second press, from Home itself, is left to fall through to the system
    // default and actually exit.
    BackHandler(enabled = detailItem == null && !rankingOpen && !seasonalOpen && !scheduleOpen && forumTopicOpen == null && vm.destination != Destination.Home) {
        vm.destination = Destination.Home
    }
    val darkTheme = when (vm.themeMode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    // The App default + Tonal Spot combo (what everyone starts on) keeps using the hand-tuned
    // LightKiko/DarkKiko constants exactly as before; anything else — a different source, or a
    // different palette style, even on the default source — runs through the generator.
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
                bottomBar = { if (detailItem == null && !rankingOpen && !seasonalOpen && !scheduleOpen && forumTopicOpen == null) BottomBar(vm.destination) { vm.destination = it } }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val topScreen = when {
                        detailItem != null -> TopScreen.Detail(detailItem)
                        rankingOpen -> TopScreen.Ranking
                        seasonalOpen -> TopScreen.Seasonal
                        scheduleOpen -> TopScreen.Schedule(scheduleInitialDay)
                        forumTopicOpen != null -> TopScreen.Topic(forumTopicOpen!!.first, forumTopicOpen!!.second)
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
                            is TopScreen.Detail -> DetailScreen(screen.item, onBack = ::backDetail, onEdit = { editor = it }, onOpenRelated = { rel -> vm.openRelated(context, rel) { fetched -> openRelatedDetail(screen.item, fetched) } }, relatedLoadingId = vm.relatedLoadingId, onBackfillRelated = { id, type, onFound, onDone -> vm.backfillRelated(context, id, type, onFound, onDone) }, onBackfillThemes = { id, type, onFound, onDone -> vm.backfillThemes(context, id, type, onFound, onDone) }, onBackfillCovers = { id, type, onFound, onDone -> vm.backfillCovers(context, id, type, onFound, onDone) }, onLoadRecommended = { forItem, onFound, onDone -> vm.loadUserRecommendations(context, forItem, onFound, onDone) }, onOpenRecommended = { rec -> vm.openRecommended(context, rec) { fetched -> openRelatedDetail(screen.item, fetched) } }, recommendedLoadingId = vm.recommendedLoadingId, onLoadStatusDistribution = { forItem, onFound, onDone -> vm.loadStatusDistribution(context, forItem, onFound, onDone) }, onGenreClick = { genre ->
                                selectedItem = null; detailStack = emptyList()
                                vm.destination = Destination.Discover
                                vm.runDiscoverSearch(context, "", if (screen.item.type == MediaType.Manga) "Manga" else "Anime", DiscoverFilters(genres = setOf(genre)))
                            })
                            TopScreen.Ranking -> RankingScreen(vm, onBack = { rankingOpen = false }, onOpenDetail = ::openDetail)
                            TopScreen.Seasonal -> SeasonalScreen(vm, onBack = { seasonalOpen = false }, onOpenDetail = ::openDetail)
                            is TopScreen.Schedule -> ScheduleScreen(vm, initialDay = screen.initialDay, onBack = { scheduleOpen = false }, onOpenDetail = ::openDetail)
                            is TopScreen.Topic -> ForumTopicScreen(topicId = screen.topicId, title = screen.title, onBack = { forumTopicOpen = null })
                            is TopScreen.Tab -> when (screen.destination) {
                                Destination.Home -> HomeScreen(vm, onOpenDetail = ::openDetail, onList = { vm.destination = Destination.List }, onDiscover = { vm.destination = Destination.Discover }, onRanking = { rankingOpen = true }, onSeasonal = { seasonalOpen = true }, onSchedule = ::openSchedule)
                                Destination.List -> ListScreen(vm, onOpenDetail = ::openDetail, onIncrement = { vm.saveLive(context, it) })
                                Destination.Discover -> DiscoverScreen(vm, onOpenDetail = ::openDetail)
                                Destination.Forums -> ForumsScreen(vm, onOpenTopic = { id, title -> forumTopicOpen = id to title })
                                Destination.Profile -> ProfileScreen(vm.signedIn, vm.malProfile, vm.items, vm.themeMode, vm.colorSource, vm.paletteStyle, vm.titleLanguage, vm.nsfwEnabled, onNsfwChange = { vm.setNsfw(context, it) }, onConnect = onSignIn, onSignOut = onSignOut, onThemeClick = { themeOpen = true }, onColorClick = { colorSourceOpen = true }, onPaletteClick = { paletteStyleOpen = true }, onTitleLanguageClick = { titleLangOpen = true },
                                    updateInfo = vm.updateInfo, updateChecking = vm.updateChecking, updateUpToDate = vm.updateUpToDateMessage,
                                    onCheckForUpdate = { if (vm.updateInfo != null) vm.updateDialogOpen = true else vm.checkForUpdate(context, manual = true) })
                            }
                        }
                    }
                    vm.error?.let { msg -> Text(msg, color = c.danger, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) }
                }
            }
            // These three sheets must stay inside this MaterialTheme/CompositionLocalProvider block —
            // moved outside, they'd fall back to LocalKikoColors' light-mode default and ignore dark mode
            // (this is what previously made the edit sheet render light even with dark theme selected).
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

// ---------- Shared pieces ----------
@Composable private fun BottomBar(selected: Destination, select: (Destination) -> Unit) { val c = LocalKikoColors.current; NavigationBar(containerColor = c.surface, tonalElevation = 4.dp) { Destination.entries.forEach { d -> NavigationBarItem(selected = d == selected, onClick = { select(d) }, icon = { Icon(d.icon, null) }, label = { Text(d.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = c.primary, selectedTextColor = c.primary, unselectedIconColor = c.muted, unselectedTextColor = c.muted, indicatorColor = c.primaryContainer)) } } }
@Composable private fun AppHeader(title: String, action: @Composable () -> Unit = {}) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-1).sp, color = LocalKikoColors.current.ink); action() } }

// ---------- Home ----------
@Composable private fun HomeScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit, onList: () -> Unit, onDiscover: () -> Unit, onRanking: () -> Unit, onSeasonal: () -> Unit, onSchedule: (java.time.DayOfWeek) -> Unit) {
    val c = LocalKikoColors.current
    val items = vm.visibleItems
    // Whatever the user touched most recently (per updatedAt) wins — not just the first Watching/Reading
    // entry in list order — so Continue always points at what they're actually mid-way through right now.
    val active = items.filter { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }.maxByOrNull { it.updatedAt }
        ?: items.firstOrNull { it.status == WatchStatus.Watching || it.status == WatchStatus.Reading }
        ?: items.firstOrNull()
    val today = java.time.LocalDate.now().dayOfWeek
    val todayReleases = vm.visibleDiscoverNewSeason.filter { it.localBroadcast()?.first == today }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            AppHeader("kiko") { Avatar(vm.malProfile?.picture.orEmpty()) }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("FRIDAY, AUGUST 1", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeActionButton(Modifier.weight(1f), "Ranking", Icons.Default.EmojiEvents, onRanking)
                    HomeActionButton(Modifier.weight(1f), "Chart", Icons.Default.DateRange, onSeasonal)
                }
                if (vm.signedIn && vm.loading) {
                    SectionTitle("Continue", "See list", onList); ContinueSkeletonCard()
                } else if (active != null) {
                    SectionTitle("Continue", "See list", onList); ContinueCard(active, onOpenDetail)
                }
                if (todayReleases.isNotEmpty()) {
                    SectionTitle("Today's release", "See all") { onSchedule(today) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(todayReleases, key = { it.id }) { item ->
                            val time = item.localBroadcast()?.second
                            BrowseCard(item, onOpenDetail, subtitle = time?.let(::localizedTimeLabel))
                        }
                    }
                }
                if (vm.visibleDiscoverNewSeason.isNotEmpty()) {
                    SectionTitle("This season", "See all", onSeasonal)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) { items(vm.visibleDiscoverNewSeason, key = { it.id }) { BrowseCard(it, onOpenDetail) } }
                }
                if (vm.visibleRecommendations.isNotEmpty()) {
                    SectionTitle("Recommendations", "See all", onDiscover)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) { items(vm.visibleRecommendations, key = { it.id }) { BrowseCard(it, onOpenDetail) } }
                }
                if (active == null && !vm.loading && !vm.signedIn && todayReleases.isEmpty() && vm.visibleDiscoverNewSeason.isEmpty() && vm.visibleRecommendations.isEmpty()) {
                    Text("Sign in from Profile to see releases and recommendations.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 40.dp))
                }
            }
        }
    }
}
// A compact, rounded pill button used for the two Home shortcuts (Ranking / Seasonal Chart).
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
// A softly pulsing grey box standing in for content that hasn't loaded yet.
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
// Placeholder standing in for the Continue card while the signed-in library fetch is still in flight,
// so the person sees an empty shell filling in rather than last session's stale "Continue" card
// (built from local sample data) briefly flashing before the real one replaces it. The rest of Home
// (Today's release/This season, Recommendations) isn't gated on this — each row just appears the
// moment its own fetch resolves, so the page fills in progressively instead of everyone waiting on
// the slowest request.
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
@Composable private fun MiniCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.width(118.dp).clickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().height(150.dp))
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(if (item.status == WatchStatus.Plan) "Saved for later" else progressLabel(item), color = c.primary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}
@Composable private fun Cover(item: MediaItem, modifier: Modifier = Modifier) {
    val displayTitle = item.displayTitle()
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(item.color)), contentAlignment = Alignment.Center) {
        if (item.cover.isNotBlank()) AsyncImage(model = item.cover, contentDescription = displayTitle, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(displayTitle.take(1), fontWeight = FontWeight.Bold, fontSize = 36.sp, color = Color.White.copy(.85f))
    }
}
private fun progressLabel(i: MediaItem) = if (i.progress == 0) i.status.label else "${i.progress}${if (i.total > 0) " of ${i.total}" else ""} ${if (i.type == MediaType.Anime) "episodes" else "chapters"}"
// The entry's actual published/aired format (e.g. "Manhwa", "Manhua", "TV", "Movie") straight from MAL's
// media_type — falls back to the bare Anime/Manga type only when MAL didn't return a format.
private fun formatLabel(i: MediaItem): String = i.format.ifBlank { if (i.type == MediaType.Anime) "Anime" else "Manga" }

// ---------- List ----------
// "Watching" and "Reading" are the same underlying status, just labeled differently for anime vs
// manga. The chosen filter is persisted globally (it should survive re-opening the app), but the
// Anime/Manga tab can be either one whenever this runs — a filter saved as "Reading" while on Manga
// would otherwise match zero anime entries and the list would look empty until something touched
// the filter again. Translating the label to whichever type is showing keeps the same *filter*
// selected across tabs instead of just silently matching nothing.
private fun normalizeFilterForType(filter: String, type: MediaType): String =
    if (filter == "Watching" || filter == "Reading") (if (type == MediaType.Anime) "Watching" else "Reading") else filter
// My List sort order. Score/LastUpdated sort highest-or-most-recent first, with untouched entries
// (myRating == 0, no updatedAt/watchStartDate yet) pushed to the bottom rather than sorting as if
// they were the lowest score or the oldest date.
// Title sort needs the same Romaji/English preference displayTitle() uses, but that's a @Composable
// (it reads LocalTitleLanguage) and this runs inside a plain sortedBy comparator — not a composable
// context — so the already-resolved preference is passed in as a plain value instead.
private fun MediaItem.resolvedTitle(pref: TitleLanguage): String =
    if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) titleEnglish else title
private fun List<MediaItem>.sortedWithListSort(sort: ListSort, titleLanguage: TitleLanguage): List<MediaItem> = when (sort) {
    ListSort.Title -> sortedBy { it.resolvedTitle(titleLanguage).lowercase() }
    ListSort.Score -> sortedWith(compareByDescending<MediaItem> { it.myRating > 0 }.thenByDescending { it.myRating })
    ListSort.LastUpdated -> sortedWith(compareByDescending<MediaItem> { it.updatedAt.isNotBlank() }.thenByDescending { it.updatedAt })
    ListSort.StartDate -> sortedWith(compareByDescending<MediaItem> { it.watchStartDate.isNotBlank() }.thenByDescending { it.watchStartDate })
}
// Small dropdown for the sort options above — a row of chips (like FilterRow) would either wrap
// past FilterRow's own or force this whole header taller, so this stays a single compact control.
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
// Same dropdown pattern as SortMenu above, for Discover's search-results sort (Members/Score/Newest/Title).
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
    // Search only applies once submitted (Enter/search action) — not on every keystroke.
    var submittedQuery by remember { mutableStateOf("") }
    val typeTab = vm.listTypeTab
    val effectiveFilter = normalizeFilterForType(vm.listFilter, typeTab)
    val filtered = vm.visibleItems
        .filter { it.type == typeTab && (effectiveFilter == "All" || it.status.label == effectiveFilter) && it.title.contains(submittedQuery, true) }
        .sortedWithListSort(vm.listSort, vm.titleLanguage)
    // Restores the exact row the person was looking at before opening a title, instead of snapping
    // back to the top — same reasoning (and the same AnimatedContent-disposal problem) as
    // seasonalScrollIndex, see LibraryViewModel. Position is saved at the moment of navigating away,
    // not on dispose, via the wrapped openItem below.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.listScrollIndex, initialFirstVisibleItemScrollOffset = vm.listScrollOffset)
    val openItem: (MediaItem) -> Unit = remember(onOpenDetail) {
        { item -> vm.saveListScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset); onOpenDetail(item) }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            AppHeader("My list") { Avatar(vm.malProfile?.picture.orEmpty()) }
            if (vm.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), color = c.primary, trackColor = c.surfaceLow)
            SearchField(query, { query = it }, "Search your list", onSearch = { submittedQuery = query }, onClear = { query = ""; submittedQuery = "" })
            // A fresh tab/filter/sort resets scroll (see selectListTypeTab/setListSort/setListFilter) since
            // it's a different — or differently ordered — list; only a Detail round trip should restore it.
            TypeToggle(typeTab) { vm.selectListTypeTab(it) }
            FilterRow(effectiveFilter, { vm.setListFilter(context, it) }, typeTab)
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${filtered.size} titles" + if (vm.loading) " · syncing…" else "", color = c.muted, fontSize = 13.sp)
                SortMenu(vm.listSort) { vm.setListSort(context, it) }
            }
        }
        itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
            ListRow(it, openItem, onIncrement, showType = false)
            if (index < filtered.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.muted.copy(alpha = .15f))
        }
        if (filtered.isEmpty()) item { Text("No titles here yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}
// Two-segment Anime/Manga switch, styled like the rest of the app instead of a stock Material TabRow.
// trackColor defaults to c.surface (right against the screen's c.background on List/Ranking, so the
// pill reads clearly) — but wherever this sits inside a c.surface Card (the Profile Stats card), that
// default would blend invisibly into its own container, so callers there pass c.surfaceLow instead to
// keep the same visible contrast everywhere the switch appears.
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
            // Only shown once there's something to clear — an empty field has nothing to undo, and
            // an always-visible "x" would just be one more thing sitting in the corner doing nothing.
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = {
                        (onClear ?: { change("") })()
                        // Clearing the text shouldn't leave the field focused — without this the
                        // keyboard would immediately pop back up right after the person closed it.
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
            // A light filled pill (not just bare colored text) so the tap target reads as a button
            // at a glance, without going back to the heavier card-style button this row used to sit in.
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

// ---------- Ranking & Seasonal chart ----------
// Full-screen anime/manga ranking chart, filterable by score/popularity/favorites/upcoming.
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
// A single numbered row in the ranking chart: position badge, cover, title, and score (or member count
// for the "Upcoming" chart, which has no score yet).
@Composable private fun RankingRow(position: Int, item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(19.dp)).background(c.surface).clickable { onOpenDetail(item) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { Text("#$position", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary) }
        Cover(item, Modifier.size(width = 54.dp, height = 76.dp))
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
// Full-screen seasonal chart. The season/year/sort picker used to sit permanently under the
// header, pushing the actual chart down; it now lives behind a single floating button so the
// grid — the reason people opened this screen — gets the space instead.
@Composable private fun SeasonalScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val handleBack = { vm.resetSeasonal(); onBack() }
    BackHandler(onBack = handleBack)
    // Only fetches on the *first* entry to this screen (or after resetSeasonal cleared things on a
    // prior exit) — year/season/sort/filter changes from the browse sheet already trigger their own
    // fetch directly (see Apply below). A key-based reload here would also re-fire every time this
    // screen remounts after a Detail round trip, replacing the already-paginated seasonalResults with
    // just its first page again and losing the scroll position along with it.
    LaunchedEffect(Unit) { if (vm.seasonalResults.isEmpty()) vm.loadSeasonal(context, vm.seasonalYear, vm.seasonalSeason, vm.seasonalSort, vm.seasonalContinuingOnly) }
    var browseOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = vm.seasonalScrollIndex, initialFirstVisibleItemScrollOffset = vm.seasonalScrollOffset)
    // Wraps the real onOpenDetail so the current position is saved at the moment of navigating away,
    // not on dispose — ordering against resetSeasonal's own zeroing-out on a real exit (handleBack)
    // would otherwise be ambiguous. Wrapped in remember (not a bare lambda) so its identity stays
    // stable across recompositions of this screen — otherwise every visible grid cell would see a
    // "new" click handler on each recomposition and skip-recomposition wouldn't apply to any of them.
    val openTitle: (MediaItem) -> Unit = remember(onOpenDetail) {
        { item -> vm.saveSeasonalScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset); onOpenDetail(item) }
    }

    // Fetches the next page once scrolled within a few rows of the end of what's currently loaded.
    // distinctUntilChanged skips re-checking on frames where the last visible item and total count
    // haven't actually changed (most scroll frames — the layout snapshot changes every frame, but
    // which item is last-visible usually doesn't), instead of recomputing this on every single frame.
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
// Full weekly release schedule, opened from Home's "Today's release" → "See all". Tabs run Monday
// through Sunday and always open on initialDay (the user's current local day when launched from
// Home) — each tab lists that day's currently-airing titles sorted by local airing time, derived
// from MediaItem.localBroadcast() so both the day grouping and the time shown are already converted
// out of MAL's raw JST values into whatever timezone/locale the device is set to.
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
// A single row in the Schedule screen: cover, title, and the local airing time in place of the
// rank/score RankingRow shows — mirrors that row's layout since both are simple browsable lists.
@Composable private fun ScheduleRow(item: MediaItem, time: java.time.LocalTime, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(19.dp)).background(c.surface).clickable { onOpenDetail(item) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(item, Modifier.size(width = 54.dp, height = 76.dp))
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(item.displayTitle(), fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(localizedTimeLabel(time), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
// Sheet behind the seasonal chart's floating button: pick any season/year/sort/filter, then tap
// Apply. Selections are held locally and only committed to the ViewModel (triggering a reload) on
// Apply, rather than each tap re-fetching immediately.
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

            // One compact control instead of two stacked rows: chevrons step a season at a time,
            // the icons jump straight to one. The current pick is spelled out above it.
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

            // Same FilterChip look as Year above and Discover's filters — one consistent chip
            // language across the app instead of a one-off segmented control.
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

            // A filter, not a sort — kept visually separate so it doesn't read as another option in
            // the row above. MAL's season chart mixes premieres with anime still airing from an
            // earlier season, so this is meaningful for any season being browsed, not just the real
            // current one: off shows premieres only, on also shows the leftover continuing titles.
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
// One tile in the seasonal chart's 3-column grid — cover plus score and member count, since most
// of these titles aren't on the user's list yet.
@Composable private fun SeasonalGridCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().clickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().aspectRatio(0.72f))
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

// ---------- Discover ----------
@Composable private fun DiscoverScreen(vm: LibraryViewModel, onOpenDetail: (MediaItem) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(vm.signedIn) { vm.loadDiscoverBrowse(context) }
    AnimatedContent(
        vm.discoverMode,
        transitionSpec = { if (targetState == DiscoverMode.Results) PushEnter togetherWith PushExit else PopEnter togetherWith PopExit },
        label = "discover-mode",
    ) { mode ->
        if (mode == DiscoverMode.Results) DiscoverResultsScreen(vm, context, onOpenDetail)
        else DiscoverBrowseScreen(vm, context, onOpenDetail)
    }
}
// The default Discover landing page: just a search box + two browse rows, no type filter.
@Composable private fun DiscoverBrowseScreen(vm: LibraryViewModel, context: Context, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf("") }
    var filterSheetOpen by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            AppHeader("Discover") { Avatar(vm.malProfile?.picture.orEmpty()) }
            Spacer(Modifier.height(17.dp))
            // Typing here hands off to the separate Results page — this page itself never shows results inline.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SearchField(query, { query = it }, "Search MAL library", onSearch = { if (query.isNotBlank() || vm.discoverFilters.isActive()) vm.runDiscoverSearch(context, query, vm.discoverTypeFilter) })
                }
                FilterIconButton(active = vm.discoverFilters.isActive(), onClick = { filterSheetOpen = true }, modifier = Modifier.padding(start = 10.dp))
            }
            if (filterSheetOpen) AdvancedFilterSheet(vm.discoverFilters, type = vm.discoverTypeFilter, onDismiss = { filterSheetOpen = false }, onApply = { filterSheetOpen = false; vm.runDiscoverSearch(context, query, vm.discoverTypeFilter, it) })
        }
        if (!vm.signedIn) {
            item { Text("Sign in from Profile to browse MyAnimeList", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        } else {
            item {
                if (vm.discoverBrowseLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.discoverBrowseError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (vm.visibleDiscoverNewSeason.isNotEmpty()) item {
                Text("New this season", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 13.dp, bottom = 10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) { items(vm.visibleDiscoverNewSeason, key = { it.id }) { BrowseCard(it, onOpenDetail) } }
            }
            if (vm.visibleDiscoverUpcoming.isNotEmpty()) item {
                Text("Top 10 upcoming", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 27.dp, bottom = 10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) { items(vm.visibleDiscoverUpcoming, key = { it.id }) { BrowseCard(it, onOpenDetail) } }
            }
        }
    }
}
// A separate page for search results — its own back arrow returns to Browse, and since all of its
// state lives in the ViewModel (not local remember), opening a result's detail and pressing back
// lands back here with the same query/filter/results intact.
@Composable private fun DiscoverResultsScreen(vm: LibraryViewModel, context: Context, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf(vm.discoverQuery) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = vm::exitDiscoverSearch)
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::exitDiscoverSearch, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back to Discover", tint = c.ink) }
                Text("Search results", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SearchField(query, { query = it }, "Search MAL library", onSearch = { vm.runDiscoverSearch(context, query, vm.discoverTypeFilter) })
                }
                FilterIconButton(active = vm.discoverFilters.isActive(), onClick = { filterSheetOpen = true }, modifier = Modifier.padding(start = 10.dp))
            }
            if (filterSheetOpen) AdvancedFilterSheet(vm.discoverFilters, type = vm.discoverTypeFilter, onDismiss = { filterSheetOpen = false }, onApply = { filterSheetOpen = false; vm.runDiscoverSearch(context, query, vm.discoverTypeFilter, it) })
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
            item { Text(emptyMessage, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        itemsIndexed(vm.visibleDiscoverResults, key = { _, it -> it.id }) { index, result ->
            SearchResultRow(result, loading = vm.discoverDetailLoadingId == result.id) { vm.openDiscoverDetail(context, result, onOpenDetail) }
            if (index < vm.visibleDiscoverResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 100.dp), thickness = 1.dp, color = c.muted.copy(alpha = .15f))
        }
    }
}
// Small round icon button that opens the Advanced Filters sheet — filled/tinted whenever a filter is
// actually set, so it doubles as an at-a-glance indicator without needing a separate badge.
@Composable private fun FilterIconButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (active) c.primary else c.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Tune, "Advanced filters", tint = if (active) c.onPrimary else c.ink) }
}
// One collapsible multi-select facet (Genre / Explicit Genre / Themes / Demographics) — collapsed
// by default (auto-expanded only if it already has a selection) so a sheet covering MAL's full
// ~80-tag taxonomy doesn't turn into one long wall of chips. The header doubles as an at-a-glance
// summary: a count badge shows how many are picked without needing to expand.
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
// Discover's advanced filters sheet — genre/explicit-genre/themes/demographics (each an
// ExpandableFilterSection, multi-select), a format ("Type") row, studio, source, year, season, and
// rating, all combined into one DiscoverFilters and applied together (see MediaItem.matches).
// Selections are held locally until Apply, same pattern as SeasonalBrowseSheet above. `type` is the
// Discover Anime/Manga/All toggle (not part of DiscoverFilters itself, see discoverTypeFilter) —
// narrows the format row to the sub-types that actually apply (Anime -> TV/OVA/..., Manga -> Manhwa/...).
@Composable private fun AdvancedFilterSheet(current: DiscoverFilters, type: String, onDismiss: () -> Unit, onApply: (DiscoverFilters) -> Unit) {
    val c = LocalKikoColors.current
    // current.genres holds both facets combined (see Apply below) — split back apart here so each
    // section's chips reflect only the selections that belong to it.
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
    val formatOptions = when (type) { "Anime" -> CommonAnimeFormats; "Manga" -> CommonMangaFormats; else -> CommonAnimeFormats + CommonMangaFormats }
    // skipPartiallyExpanded: without this, focusing a text field (Studio/Year) shrinks the
    // available height for the IME, and the sheet re-settles at its "partially expanded" anchor
    // instead of staying fully open — this removes that middle state entirely so the sheet only
    // ever sits fully expanded (or hidden), regardless of keyboard visibility.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            Text("Discover", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Advanced filters", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))

            ExpandableFilterSection("Genre", CommonGenres, genres, onToggle = { g -> genres = if (g in genres) genres - g else genres + g })
            // Kept as its own section instead of folded into Genre above — three adult-content tags
            // sitting apart from the other sixteen is easier to scan (and skip) than mixed in.
            ExpandableFilterSection("Explicit genre", CommonExplicitGenres, explicitGenres, onToggle = { g -> explicitGenres = if (g in explicitGenres) explicitGenres - g else explicitGenres + g })
            // MAL's finer-grained "themes" facet, below genre — an Isekai or Iyashikei title can be
            // any genre, so this is kept as its own section rather than folded into Genre above.
            ExpandableFilterSection("Themes", CommonThemes, themes, onToggle = { t -> themes = if (t in themes) themes - t else themes + t })
            ExpandableFilterSection("Demographics", CommonDemographics, demographics, onToggle = { d -> demographics = if (d in demographics) demographics - d else demographics + d })

            Text("Type", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formatOptions.forEach { f -> FilterChip(selected = format == f, onClick = { format = if (format == f) "" else f }, label = { Text(f) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) }
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
                    onClick = { genres = emptySet(); explicitGenres = emptySet(); themes = emptySet(); demographics = emptySet(); studio = ""; source = ""; year = ""; season = null; rating = ""; format = "" },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset", color = c.muted, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { onApply(DiscoverFilters(genres + explicitGenres, themes, demographics, studio.trim(), source, year, season, rating, format)) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    modifier = Modifier.weight(2f),
                ) { Text("Apply filters", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
// A cover-forward card for the browse rows (new-this-season / upcoming) — score instead of a status label,
// since these titles usually aren't on the user's list yet.
// subtitle overrides the default score/genre line below the title — used by the Home "Today's
// release" row to show the airing time (in the user's own locale/timezone) instead.
@Composable private fun BrowseCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit, subtitle: String? = null) {
    val c = LocalKikoColors.current
    Column(Modifier.width(118.dp).clickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().height(150.dp))
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(subtitle ?: (if (item.score > 0) "★ ${"%.1f".format(item.score)}" else item.genre), color = c.muted, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
// A search-result row for MAL lookups: cover carries the score as a badge, and metadata (format,
// episode/chapter count, release year, member count) replaces the personal-list status text that
// doesn't apply yet since most of these titles aren't on the user's list.
// `loading` covers the row's own tap (fetching full details before navigating) — while true, the
// cover dims under a spinner and further taps are ignored so the person can't queue up a second fetch.
@Composable private fun SearchResultRow(item: MediaItem, loading: Boolean, onTap: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !loading) { onTap() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(84.dp).height(118.dp)) {
            Cover(item, Modifier.fillMaxSize())
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
// "24 ep, Spring 2011" / "1 ep, 2013" — episode or chapter count plus season/year, skipping whichever part is unknown.
private fun episodeAndYear(item: MediaItem): String {
    val unit = if (item.type == MediaType.Anime) "ep" else "ch"
    val episodes = if (item.total > 0) "${item.total} $unit" else null
    val year = seasonYear(item.season, item.startDate).takeIf { it.isNotBlank() }
    return listOfNotNull(episodes, year).joinToString(", ")
}
// Comma-grouped member count, e.g. 648687 -> "648,687" — exact rather than abbreviated.
private fun formatExact(n: Int): String = "%,d".format(n)

// ---------- Forums ----------
// Top-level Forums tab: a categorized board list with its own search box (Boards), or the topic
// list for whichever board/subboard/search is currently open (Topics) — same Browse/Results split
// as Discover above, for the same reason (state survives opening a topic and coming back).
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
// The default Forums landing page: a search box plus every board MAL has, grouped under its category.
@Composable private fun ForumBoardsScreen(vm: LibraryViewModel, context: Context) {
    val c = LocalKikoColors.current
    var query by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            AppHeader("Forums")
            // Typing here hands off to the shared Topics page as a cross-board search — this page
            // itself never shows topic results inline, same as Discover's own search box above.
            SearchField(query, { query = it }, "Search topics", onSearch = { if (query.isNotBlank()) vm.runForumSearch(context, query) })
        }
        if (!vm.signedIn) {
            item { Text("Sign in from Profile to browse the MAL forums", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        } else {
            item {
                if (vm.forumBoardsLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.primary, trackColor = c.surfaceLow)
                vm.forumBoardsError?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
            }
            // Each category is one grouped card (board-index style, like a real forum's landing page)
            // instead of a stack of separate floating rows — dividers between boards read as one
            // organized section rather than a generic repeated list.
            vm.forumCategories.forEach { category ->
                item { Text(category.title.uppercase(), color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp)) }
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = c.surface), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            category.boards.forEachIndexed { index, board ->
                                ForumBoardRow(board) { vm.openForumBoard(context, board) }
                                if (index < category.boards.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 66.dp), thickness = 1.dp, color = c.muted.copy(alpha = .12f))
                            }
                        }
                    }
                }
            }
        }
    }
}
// One board in the landing page's grouped card — its subboards (if any) surface as a small count
// pill rather than a truncated joined-title line, so "this board has sub-sections" reads at a glance.
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
// Small floating "back to top" button — mirrors the "Back to top" link MAL's own forum pages show
// at the bottom of a topic list/thread. Only appears once scrolled a few rows down (no point showing
// it right at the top), and animates the list back to its first row on tap rather than jumping
// instantly, so the person doesn't lose their sense of place.
@Composable private fun GoToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FloatingActionButton(onClick = onClick, containerColor = c.primary, contentColor = c.onPrimary, modifier = Modifier.size(46.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Back to top")
        }
    }
}
// The shared topic-list page — reached either by tapping a board (forumBoardTitle set) or by a
// cross-board search (forumBoardTitle blank, see its comment in LibraryViewModel). Subboard chips
// only render when the open board actually has any.
@Composable private fun ForumTopicsScreen(vm: LibraryViewModel, context: Context, onOpenTopic: (Int, String) -> Unit) {
    val c = LocalKikoColors.current
    val headerTitle = vm.forumBoardTitle.ifBlank { "Search results" }
    BackHandler(onBack = vm::exitForumTopics)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    // Fetches the next page once scrolled within a few rows of the end of what's currently loaded —
    // same pattern SeasonalScreen uses for its grid, just against a plain list here.
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
                item { Text("No topics found.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            itemsIndexed(vm.forumTopics, key = { _, it -> it.id }) { index, topic ->
                ForumTopicRow(topic) { onOpenTopic(topic.id, topic.title) }
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
// One row in a topic list — the OP's little avatar circle up front (like a real thread list, not
// just a text row), title with a lock glyph if MAL has it closed to new replies, who started it, and
// who most recently replied and when (topic.lastPostAt/lastPostAuthor, previously fetched but never
// shown) — the "is this thread still active" signal any forum list leads with. Reply count sits in
// its own pill on the trailing edge instead of a bare icon+number.
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
// Reading a single topic: its posts (page one loads with the screen, further pages behind a "Load
// more" tap since a long thread can run well past what's worth fetching up front) plus its poll, if
// it has one. MAL's forum API is read-only — there's no reply/post endpoint — so this is browse-only.
@Composable private fun ForumTopicScreen(topicId: Int, title: String, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
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
    val listState = rememberLazyListState()
    val showGoToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                    Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
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
// MAL forum posts are written in BBCode, not HTML or Markdown — e.g. [b]bold[/b], [url=https://...]
// link text[/url], [img]https://...[/img]. This is a small renderer for the tags that actually show
// up in practice: bold/italic/underline/strikethrough, links, images, ordered/unordered lists, quotes,
// and centering. Anything else (size, color, spoiler, tables, ...) is left unstyled but its inner text
// is still kept, rather than either crashing on it or hiding it — MAL's own API gives no HTML fallback
// to render instead, so this is the only representation available for any of it.
private sealed class ForumBlock {
    data class Paragraph(val text: AnnotatedString, val center: Boolean = false) : ForumBlock()
    data class ImageBlock(val url: String) : ForumBlock()
    data class ListBlock(val items: List<AnnotatedString>, val ordered: Boolean) : ForumBlock()
    data class Quote(val text: AnnotatedString) : ForumBlock()
}
private sealed class BbToken {
    data class Text(val text: String) : BbToken()
    data class Open(val name: String, val attr: String?) : BbToken()
    data class Close(val name: String) : BbToken()
}
private val bbTagRegex = Regex("""\[(/?)([a-zA-Z*]+)(=[^\]]*)?\]""")
// MAL's editor emits [img] with either an "=value" attribute or a space-separated one like
// "[img width=500]" — the old pattern only matched the "=" form, so width-attributed images fell
// straight through as unrendered literal text instead of becoming an ImageBlock.
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
// Recursive so nested tags like [b][i]...[/i][/b] style correctly — returns the index just past the
// tokens it consumed, since the AnnotatedString.Builder itself has no notion of "where am I".
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
                        // [url=href]text[/url] carries the href as the tag's attribute; bare [url]href[/url]
                        // uses its own visible text as the href, so that one's peeked from the next token.
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
// MAL's forum bodies are mostly BBCode, but posts often carry literal HTML fragments too — `<br />`
// for line breaks and named entities like `&darr;` — left over from whatever editor produced them.
// The BBCode tokenizer above only understands `[tag]` syntax, so without this pass those fragments
// pass straight through as visible text (e.g. a literal "<br />" or "&darr;" in the rendered post).
private val brTagRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val htmlEntityRegex = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")
private val namedHtmlEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "darr" to "↓", "uarr" to "↑", "larr" to "←", "rarr" to "→", "harr" to "↔",
    "hellip" to "…", "mdash" to "—", "ndash" to "–", "copy" to "©", "reg" to "®", "trade" to "™",
    "middot" to "·", "bull" to "•", "deg" to "°", "sect" to "§", "para" to "¶",
    "dagger" to "†", "Dagger" to "‡", "spades" to "♠", "clubs" to "♣", "hearts" to "♥", "diams" to "♦",
)
private fun decodeHtmlEntities(text: String): String = htmlEntityRegex.replace(text) { m ->
    val body = m.groupValues[1]
    when {
        body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        else -> namedHtmlEntities[body] ?: m.value
    }
}
// A link whose own href is just an image file — MAL's forum editor drops an inserted picture (its
// "Stack" banners, its own badge icons, etc.) in as a bare [url]https://...png[/url] around the
// image's own CDN link, rather than wrapping it in [img]...[/img]. MAL's own site auto-embeds these
// as images instead of showing the raw link; without this pass the app rendered them as a plain
// clickable link showing the raw CDN URL (see ForumBody) instead of the picture the link points to.
// Only the *bare* form ([url]href[/url], no separate "=" attribute/label) is rewritten — a genuine
// [url=https://...png]click here[/url] still has real link text to show and is left as a link.
private val bareImageLinkRegex = Regex("""\[url\]\s*(https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?)\s*\[/url\]""", RegexOption.IGNORE_CASE)
// Some posts (MAL's own event/announcement threads included — pasted straight from a desktop editor)
// open an [img] tag and never close it, e.g. "[img width=500]https://cdn.myanimelist.net/...".
// bbBlockRegex below requires a matching [/img] to recognize an image at all, so without this pass
// that whole fragment — brackets, attribute, and URL — fell straight through as visible literal text
// instead of becoming a picture. This finds an [img ...] opening tag whose URL isn't already followed
// by a real [/img] (optionally after whitespace) and inserts one right after the URL, which ends at
// the first whitespace or "[" or the end of the string — same boundary a properly closed tag would use.
private val unclosedImgRegex = Regex("""\[img(?:[^\]]*)\]\s*(https?://[^\s\[\]]+)(?!\s*\[/img\])""", RegexOption.IGNORE_CASE)
private fun normalizeMalMarkup(raw: String): String =
    decodeHtmlEntities(brTagRegex.replace(raw, "\n"))
        .let { bareImageLinkRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { unclosedImgRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }

private fun parseBBCode(rawIn: String, linkColor: Color): List<ForumBlock> {
    if (rawIn.isBlank()) return emptyList()
    val raw = normalizeMalMarkup(rawIn)
    val blocks = mutableListOf<ForumBlock>()
    var pos = 0
    for (m in bbBlockRegex.findAll(raw)) {
        if (m.range.first > pos) blocks += paragraphsFrom(raw.substring(pos, m.range.first), linkColor)
        val tag = m.groupValues[1].lowercase()
        val inner = m.groupValues[2]
        when (tag) {
            "img" -> inner.trim().takeIf { it.isNotBlank() }?.let { blocks += ForumBlock.ImageBlock(it) }
            "list" -> {
                val items = inner.split(Regex("""\[\*\]""", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotBlank() }
                if (items.isNotEmpty()) blocks += ForumBlock.ListBlock(items.map { inlineAnnotated(it, linkColor) }, ordered = m.value.take(20).contains("=1"))
            }
            "quote" -> inner.trim().takeIf { it.isNotBlank() }?.let { blocks += ForumBlock.Quote(inlineAnnotated(it, linkColor)) }
            "center" -> blocks += paragraphsFrom(inner, linkColor, center = true)
        }
        pos = m.range.last + 1
    }
    if (pos < raw.length) blocks += paragraphsFrom(raw.substring(pos), linkColor)
    return blocks
}
// Fullscreen single-image viewer opened by tapping any image in a forum post (see ForumBody below) —
// pinch to zoom, drag to pan once zoomed, double-tap to toggle between fit and a fixed zoomed-in level,
// tap the scrim (at 1x only, so a pan gesture at higher zoom doesn't also dismiss) or the close button
// to leave. Deliberately its own dialog rather than reusing Detail's cover pager (showFullCover above):
// that one is a fixed-aspect-ratio gallery over a title's own covers, this is a single arbitrary-shaped
// image with real zoom, which is what "zoom in" on a forum picture actually needs.
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
// Renders a post body's BBCode as an actual Column of styled text/images/lists/quotes instead of raw
// markup — images sit as their own bordered box between paragraphs (matching how MAL's own site frames
// them), links are tappable and open in the system browser via LocalUriHandler; each image is itself
// tappable to open ZoomableImageDialog above.
@Composable private fun ForumBody(body: String, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    val uriHandler = LocalUriHandler.current
    val blocks = remember(body, c.primary) { parseBBCode(body, c.primary) }
    // Tapped image's URL, or null when no viewer is open — a post can have several images, so this
    // tracks which one rather than a plain boolean, same pattern as Detail's showFullCover.
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    fullscreenImage?.let { url -> ZoomableImageDialog(url, onDismiss = { fullscreenImage = null }) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ForumBlock.Paragraph -> ClickableText(
                    text = block.text,
                    style = TextStyle(color = c.ink, fontSize = 14.sp, lineHeight = 20.sp, textAlign = if (block.center) TextAlign.Center else TextAlign.Start),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { offset -> block.text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } } },
                )
                // MAL posts often specify a fixed pixel width (meant for a desktop-width page) — that's
                // deliberately ignored here rather than honored, since a "width=500" or "width=1200"
                // value would either overflow or look tiny depending on the phone. Instead the image is
                // capped to fit the screen: bounded width, a max height so a tall image can't dominate
                // the whole scroll, and Fit scaling so it's never stretched or cropped out of proportion.
                is ForumBlock.ImageBlock -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = block.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 340.dp).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, c.primary.copy(alpha = .5f), RoundedCornerShape(8.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { fullscreenImage = block.url },
                    )
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
                ) {
                    ClickableText(
                        text = block.text, style = TextStyle(color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, fontStyle = FontStyle.Italic),
                        onClick = { offset -> block.text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } } },
                    )
                }
            }
        }
    }
}
// One reply in a topic thread — avatar, name, post number (the original post is #1), timestamp,
// then the body rendered from its BBCode markup (see ForumBody below) rather than shown as raw text.
// isOriginalPost just swaps the "#N" label for an "OP" badge — no extra container or tinting,
// same row treatment as every other post.
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
// A topic's poll, if it has one — each option's bar fills relative to that option's share of votes.
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
// "2015-03-02T06:03:11+00:00" -> "Mar 2, 2015" — forum timestamps are full date-times (unlike the
// date-only/date-month fields formatFullDate above handles), so this parses the ISO-8601 offset
// format separately rather than trying to make one function cover both shapes.
private fun formatForumDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(raw)
        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(parsed!!)
    } catch (e: Exception) { raw.take(10) }
}

// ---------- Profile ----------
@Composable private fun ProfileScreen(
    connected: Boolean, profile: MalProfile?, items: List<MediaItem>, themeMode: ThemeMode, colorSource: ColorSource, paletteStyle: PaletteStyle, titleLanguage: TitleLanguage,
    nsfwEnabled: Boolean, onNsfwChange: (Boolean) -> Unit,
    onConnect: () -> Unit, onSignOut: () -> Unit, onThemeClick: () -> Unit, onColorClick: () -> Unit, onPaletteClick: () -> Unit, onTitleLanguageClick: () -> Unit,
    updateInfo: AppUpdateInfo? = null, updateChecking: Boolean = false, updateUpToDate: Boolean = false, onCheckForUpdate: () -> Unit = {},
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            AppHeader("Profile")

            // The person's actual MAL account: bigger avatar, account details, and their real anime stats.
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
                            // Opens the person's own MAL profile page on myanimelist.net — same
                            // CustomTabsIntent pattern as Detail's "Open in browser" menu item.
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

            // A single tabbed Stats card instead of two full-length cards stacked one after another
            // (the old "scroll past anime stats to reach manga stats" layout) — Anime/Manga picked
            // with the same TypeToggle used on List/Ranking, plus a genre breakdown and score
            // distribution chart per type, computed locally from the already-fetched list (MAL's API
            // itself only gives aggregate numbers for anime, via anime_statistics on /users/@me).
            val animeItems = items.filter { it.type == MediaType.Anime }
            val mangaItems = items.filter { it.type == MediaType.Manga }
            val mangaTotal = mangaItems.size
            val mangaChaptersRead = mangaItems.sumOf { it.progress }
            val ratedManga = mangaItems.filter { it.myRating > 0 }
            val mangaMeanScore = if (ratedManga.isNotEmpty()) ratedManga.map { it.myRating }.average() else 0.0
            val animeDaysWatched = profile?.animeDaysWatched ?: 0.0
            // Manga has no MAL equivalent to anime's official "days watched" stat, so reading time is
            // estimated here (~5 min/chapter) purely so Time Watched vs Read has something to compare
            // against — always labelled "(est.)", never presented as an official MAL number.
            val mangaDaysReadEst = mangaChaptersRead * 5.0 / 60.0 / 24.0
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

            // Only shown while signed out — once connected, the profile card above already makes that obvious.
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
            // Manual check, plus wherever the auto-check (or a cached result from last launch) already
            // found something newer — tapping it then just reopens UpdateDialog instead of re-fetching.
            ListItem(
                headlineContent = { Text("Check for updates", fontWeight = FontWeight.Bold, color = c.ink) },
                supportingContent = {
                    Text(
                        when {
                            updateChecking -> "Checking…"
                            updateInfo != null -> "Update available — ${updateInfo.version}"
                            updateUpToDate -> "You're up to date — v${BuildConfig.VERSION_NAME}"
                            else -> "v${BuildConfig.VERSION_NAME}"
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
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = !updateChecking, onClick = onCheckForUpdate),
            )
            // Sign out lives on its own at the very bottom of the page, away from the rest of Preferences.
            if (connected) {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onSignOut, colors = ButtonDefaults.textButtonColors(contentColor = c.danger), modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
        }
    }
}
// One of the three headline numbers up top of the anime stats section.
@Composable private fun HeroStat(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String, container: Color, content: Color) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(container).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = content)
        Text(label, color = content.copy(alpha = .75f), fontSize = 11.sp)
    }
}
// One row of the status breakdown — a proportional bar so, at a glance, "Completed" visibly dwarfs "Dropped".
// barColor tints both the count and the fill so each status reads at a glance (e.g. Dropped in red).
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
// MAL's own profile stats look: "Days: X   Mean Score: X" above a single stacked bar (each status's
// share of the total, side by side in one bar rather than five separate ones), then a status legend
// (dot + name + count) beside a plain summary column (Total Entries/Rewatched/Episodes) — see MAL's
// own "Anime Stats"/"Manga Stats" cards on a profile page.
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
// Top genres across the given list (anime or manga, whichever tab is selected), each as a
// proportional bar out of the list's total size — reuses StatBar's exact look so this reads as part
// of the same stats language rather than a separate chart widget.
@Composable private fun GenreBreakdownChart(items: List<MediaItem>, c: KikoColors) {
    val total = items.size
    val counts = items.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
    if (counts.isEmpty()) { Text("Not enough data yet.", color = c.muted, fontSize = 12.sp); return }
    Column(Modifier.fillMaxWidth()) { counts.forEach { (genre, count) -> StatBar(genre, count, total, c, c.primary) } }
}
// A histogram of the person's own 1-10 scores (myRating) across the given list — bar height scales
// to whichever score is most common, so the shape of someone's rating habits (harsh grader vs. easy
// scorer) is visible at a glance.
// Each bar sits in its own fixed-height slot (bar always bottom-anchored inside it, growing up
// from a shared baseline) instead of letting bar height push around the whole column's total
// height — that's what was shoving the "7" label out of line with the others: the tallest bar
// (score 7 usually has the most entries) left less room in the fixed-height Row for its own
// label, while every shorter bar had plenty of slack, so only that one column's text got squeezed.
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
// Fixed color per list status — same MAL-ish semantic mapping as before (green while watching, blue
// once completed, yellow on hold, red when dropped, grey for plan-to-watch), but now hardcoded rather
// than pulled from KikoColors: the person's chosen color theme/palette changes everything else in the
// app, but this meaning should read the same regardless, so it's deliberately NOT theme-aware.
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
// Same mapping by label text, for the Profile screen's stat rows — those use their own hardcoded
// labels ("Plan to watch", "On hold", ...) rather than an actual WatchStatus value.
private fun statusColor(label: String): Color = when {
    label.startsWith("Watch", true) || label.startsWith("Read", true) -> StatusWatchingColor
    label.startsWith("Complet", true) -> StatusCompletedColor
    label.contains("hold", true) -> StatusOnHoldColor
    label.startsWith("Drop", true) -> StatusDroppedColor
    else -> StatusPlanColor // Plan to watch
}
// Falls back to the plain "M" tile when signed out or the MAL picture hasn't loaded/isn't set.
@Composable private fun Avatar(picture: String = "") {
    val c = LocalKikoColors.current
    if (picture.isNotBlank()) {
        AsyncImage(model = picture, contentDescription = "Profile picture", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(43.dp).clip(RoundedCornerShape(16.dp)).background(c.warm))
    } else {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(16.dp)).background(c.warm), contentAlignment = Alignment.Center) { Text("M", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.ink) }
    }
}

// ---------- Detail ----------
@Composable private fun DetailScreen(item: MediaItem, onBack: () -> Unit, onEdit: (MediaItem) -> Unit, onOpenRelated: (RelatedEntry) -> Unit, relatedLoadingId: Int? = null, onBackfillRelated: (String, MediaType, (List<RelatedEntry>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() }, onBackfillThemes: (String, MediaType, (List<String>, List<String>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() }, onBackfillCovers: (String, MediaType, (List<String>) -> Unit, () -> Unit) -> Unit = { _, _, _, onDone -> onDone() }, onLoadRecommended: (MediaItem, (List<RecommendedEntry>) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() }, onOpenRecommended: (RecommendedEntry) -> Unit = {}, recommendedLoadingId: Int? = null, onLoadStatusDistribution: (MediaItem, (StatusDistribution) -> Unit, () -> Unit) -> Unit = { _, _, onDone -> onDone() }, onGenreClick: (String) -> Unit = {}) {
    val c = LocalKikoColors.current
    var synopsisExpanded by remember(item.id) { mutableStateOf(false) }
    // The list-fetch this item came from sometimes omits Related even when MAL has it — quietly
    // recheck the single-title endpoint once per title and use whatever it finds, if anything.
    // `relatedDone` (and the two below) track whether that recheck has finished — starting out
    // already-true when there's nothing to recheck — so the page can wait for everything at once
    // instead of popping sections in one by one as each fetch happens to land.
    var backfilledRelated by remember(item.id) { mutableStateOf<List<RelatedEntry>?>(null) }
    var relatedDone by remember(item.id) { mutableStateOf(item.related.isNotEmpty()) }
    LaunchedEffect(item.id) {
        if (item.related.isEmpty()) onBackfillRelated(item.id, item.type, { backfilledRelated = it }, { relatedDone = true }) else relatedDone = true
    }
    val related = backfilledRelated ?: item.related
    // Same fix as Related, for the same reason: the list-fetch this item came from sometimes omits
    // opening/ending themes even when MAL has them — quietly recheck the single-title endpoint once
    // per title and use whatever it finds, if anything.
    var backfilledThemes by remember(item.id) { mutableStateOf<Pair<List<String>, List<String>>?>(null) }
    var themesDone by remember(item.id) { mutableStateOf(item.openingThemes.isNotEmpty() || item.endingThemes.isNotEmpty()) }
    LaunchedEffect(item.id) {
        if (item.openingThemes.isEmpty() && item.endingThemes.isEmpty()) {
            onBackfillThemes(item.id, item.type, { op, ed -> backfilledThemes = op to ed }, { themesDone = true })
        } else themesDone = true
    }
    val (openingThemes, endingThemes) = backfilledThemes ?: (item.openingThemes to item.endingThemes)
    // Same fix as Related/Themes above, for the cover gallery: the bulk list/ranking/season/search
    // endpoints never request MAL's "pictures" field (only the single-title detail() fetch does —
    // see MalApi.detail), so a title opened from the person's own list, Home, Ranking, etc. starts
    // out with just its one main cover. Quietly recheck the single-title endpoint so the fullscreen
    // cover viewer can be swiped through once the rest land — non-blocking (like Recommended below)
    // since it only matters once the person actually taps the cover open, not for the rest of the page.
    var backfilledCovers by remember(item.id) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(item.id) {
        if (item.covers.size <= 1) onBackfillCovers(item.id, item.type, { backfilledCovers = it }, {})
    }
    val covers = backfilledCovers ?: item.covers
    // User-submitted "Recommended" row — see the section comment below for where this data comes
    // from. Loaded via onLoadRecommended and rendered whenever it arrives — same non-blocking
    // backfill pattern as Related/Themes above, since it also comes from a second single-title fetch.
    var recommended by remember(item.id) { mutableStateOf<List<RecommendedEntry>>(emptyList()) }
    LaunchedEffect(item.id) { onLoadRecommended(item, { recommended = it }, {}) }
    // Bottom-of-page status distribution — MAL's own official `statistics` field, same non-blocking
    // backfill pattern as the rest of this screen's second-fetch data.
    var statusDistribution by remember(item.id) { mutableStateOf<StatusDistribution?>(null) }
    LaunchedEffect(item.id) { onLoadStatusDistribution(item, { statusDistribution = it }, {}) }
    // A fresh LazyListState per title (instead of the one default state reused across every open
    // detail screen) is what makes tapping into a related title land at the top of its page instead
    // of wherever the previous title happened to be scrolled to.
    val listState = remember(item.id) { LazyListState() }
    // The hero cover is the one genuinely asynchronous piece of this screen (everything else in
    // `item` is already in memory) — sharing this one painter between the readiness check below and
    // the actual <Image> means the picture is decoded exactly once, not fetched twice. Requesting it
    // at Size.ORIGINAL (instead of leaving Coil to infer a size from layout) matters here: layout
    // only happens once the Image composable below is drawn, which only happens once this is ready
    // — leaving the size unset would make the request wait on a layout pass that never arrives.
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coverPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context).data(item.cover.ifBlank { null }).size(Size.ORIGINAL).build()
    )
    val coverReady = item.cover.isBlank() || coverPainter.state is AsyncImagePainter.State.Success || coverPainter.state is AsyncImagePainter.State.Error
    BackHandler(onBack = onBack)
    // Every section's data — cover, Related, Theme songs, Recommended — loads before any of the page
    // is shown, rather than the page appearing and each section popping in as its own fetch happens
    // to land. Same one-time cost as before, just paid up front instead of scattered across the
    // first few seconds on screen.
    if (!coverReady || !relatedDone || !themesDone) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary) }
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                // Tapping the cover opens it uncropped in a fullscreen popup (see showFullCover below)
                // rather than changing how it's cropped here — the hero banner itself stays as-is.
                var showFullCover by remember(item.id) { mutableStateOf(false) }
                val displayTitle = item.displayTitle()
                // The gallery's 2nd picture (see MalApi.detail's "pictures" field / backfillCovers)
                // doubles as a backdrop behind the poster — darkened (top shadow + overall dim) so it
                // reads as mood lighting, not a second thing competing for attention. Falls back to the
                // title's own placeholder color (same value used everywhere else in the app) when
                // there's nothing else MAL has on file to show back there.
                val backdropUrl = covers.getOrNull(1)
                // Outer wrapper is intentionally unclipped: the poster below is positioned to hang
                // past the banner's rounded bottom edge, and clipping here would slice it off.
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
                        // A soft shadow cast down from the system bar, instead of blurring the backdrop —
                        // keeps the artwork crisp while still settling the status bar icons/back button.
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .5f), .4f to Color.Transparent)))
                        // General darken so the poster/back-button chips stay legible over any backdrop.
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
                    // Poster sits lower than the back button (so it never looks like it's touching it)
                    // and is tall enough to hang past the banner's bottom edge into the page below,
                    // rather than being squeezed to fit entirely inside the blurred backdrop.
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
                    // Falls back to just the one cover for a title whose covers gallery hasn't loaded
                    // (e.g. opened before the single-title detail() fetch — see MalApi.detail/parseEntry).
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
                                    // Fit (not Crop) inside a box matching the cover's own 2:3 ratio — the
                                    // whole, uncropped image, letterboxed rather than sliced to fill the screen shape.
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
                    Text(itemDisplayTitle, style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.padding(top = 7.dp))
                    val secondary = item.secondaryTitle()
                    if (secondary.isNotBlank()) {
                        Text(secondary, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    // Airing status / episode-or-chapter count / rating, right below the title — the
                    // person's own list status isn't repeated here since the edit button below the page
                    // already shows it.
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

                    // Rank / popularity / members are community stats, kept apart from the title's own facts.
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
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                                item.synonyms.forEachIndexed { i, name ->
                                    Text(name, color = c.ink, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp))
                                    if (i != item.synonyms.lastIndex) HorizontalDivider(color = c.surfaceLow)
                                }
                            }
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

                    if (related.isNotEmpty()) {
                        Text("Related", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(related) { rel ->
                                RelatedCard(rel, loading = rel.malId > 0 && relatedLoadingId == rel.malId) {
                                    // A real MAL id lets us fetch full details and open Kiko's own Detail screen;
                                    // without one (title-only match) we fall back to a MAL web search, same as before.
                                    if (rel.malId > 0) onOpenRelated(rel) else uriHandler.openUri(malUrl(rel))
                                }
                            }
                        }
                    }

                    // MAL's official per-title endpoint exposes this via its `recommendations` field
                    // (see MalApi.userRecommendations) — the same user-submitted recommendations tab
                    // MAL's website shows below a title's own page, ranked by vote count.
                    if (recommended.isNotEmpty()) {
                        Text("Recommended", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            items(recommended, key = { it.malId }) { rec ->
                                RecommendedCard(rec, loading = recommendedLoadingId == rec.malId) { onOpenRecommended(rec) }
                            }
                        }
                    }

                    if (item.background.isNotBlank()) {
                        Text("Background", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                        Text(item.background, color = c.ink, fontSize = 14.sp, lineHeight = 21.sp)
                    }

                    // Bottom-of-page: how every MAL member tracking this anime has it filed, from
                    // MAL's own official `statistics` field (see MalApi.detail/parseEntry). Reuses
                    // the same StatBar + statusColor treatment as the Profile screen's own anime
                    // stats, so the status colors read consistently everywhere in the app.
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
// "Fall" + "2023" -> "Fall 2023"; falls back to whichever part is available.
private fun seasonYear(season: String, year: String): String = listOf(season, year).filter { it.isNotBlank() }.joinToString(" ")
// "2023-09-29" -> "Sep 29, 2023"; degrades gracefully for year-only or year-month dates.
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
// Same idea for the item currently open on Detail — used by its Share / Open in browser menu.
private fun malUrl(item: MediaItem): String {
    val intId = item.id.toIntOrNull()
    return if (intId != null && intId > 0) "https://myanimelist.net/${item.type.name.lowercase()}/$intId"
    else "https://myanimelist.net/search/all?q=" + java.net.URLEncoder.encode(item.title, "UTF-8")
}
// Recognizes a MyAnimeList title URL (myanimelist.net/anime/{id}/... or /manga/{id}/...) so it can be
// opened straight into Kiko's own Detail screen instead of falling through to a browser.
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
// Deliberately understated vs. Pill: an outline instead of a solid fill, so genres read as
// tags on the title rather than as status/score badges.
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
// Shared card shell for the Detail screen's horizontal rows (Related, Recommended, Characters):
// same width, same cover aspect ratio, and a fixed-height text block underneath — regardless of
// whether a given card has a label line, a subtitle line, both, or neither — so every card in
// every one of these rows is exactly the same size instead of Characters (which has up to three
// lines of text) reading taller than Related/Recommended (which only ever have two).
@Composable private fun DetailRowCard(
    imageUrl: String, fallbackLetter: String, title: String,
    label: String? = null, subtitle: String? = null,
    loading: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val c = LocalKikoColors.current
    Column(
        Modifier.width(130.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
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
        }
        // Fixed height (rather than sized to content) is what makes the block uniform: a card with
        // just a label still takes up the same space as one with a label AND a subtitle.
        Column(Modifier.fillMaxWidth().height(92.dp).padding(10.dp)) {
            if (label != null) Text(label.uppercase(), color = c.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (label != null) 4.dp else 0.dp))
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
@Composable private fun RelatedCard(entry: RelatedEntry, loading: Boolean = false, onClick: () -> Unit) {
    DetailRowCard(imageUrl = entry.cover, fallbackLetter = entry.title.take(1), title = entry.title, label = entry.relation, loading = loading, onClick = onClick)
}

// Same card treatment as RelatedCard (surface background, rounded container, top-rounded cover,
// same per-card loading overlay while its full details are being fetched) so the detail screen's
// Recommended row reads as its own set of cards rather than the bare image-over-text layout
// BrowseCard uses elsewhere.
@Composable private fun RecommendedCard(entry: RecommendedEntry, loading: Boolean = false, onClick: () -> Unit) {
    val subtitle = if (entry.votes > 0) "${entry.votes} recommend${if (entry.votes == 1) "s" else ""}" else "Recommended"
    DetailRowCard(imageUrl = entry.cover, fallbackLetter = entry.title.take(1), title = entry.title, subtitle = subtitle, loading = loading, onClick = onClick)
}

// ---------- Sheets ----------
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 9.dp)) { items(statusOptions) { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) }, colors = FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)) } }

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
// A themed, tappable "field" that opens a date picker dialog instead of accepting typed input.
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
// "2024-03-22" -> millis at UTC midnight, matching what DatePicker returns; null when blank/unparseable.
private fun String.toEpochMillisOrNull(): Long? {
    if (isBlank()) return null
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        sdf.parse(this)?.time
    } catch (e: Exception) { null }
}
// DatePicker's selectedDateMillis (UTC midnight) -> "2024-03-22" for storage.
private fun Long.toIsoDate(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(this))
}
// "2024-03-22" -> "Mar 22, 2024" for display, parsed/formatted in UTC to match the picker (avoids off-by-one-day drift).
private fun formatUserDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val out = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        out.format(parser.parse(raw)!!)
    } catch (e: Exception) { raw }
}
// Shown from Profile's "Check for updates" row whenever a newer release is known — either just
// fetched by the manual check, or already sitting in AppUpdateChecker's cache after the silent
// launch-time auto-check (or a tap on the update notification) found one. Skip/Later stay available
// mid-download; the destructive "close mid-download" case is deliberately allowed since the file is
// just re-downloaded from cache next time — not worth a second confirmation dialog on top of this one.
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
                    // Only the selected Custom row expands to reveal the hex field — picking a
                    // different source above collapses this away again.
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
                        Text(when (lang) { TitleLanguage.Romaji -> "e.g. Sousou no Frieren"; TitleLanguage.English -> "e.g. Frieren: Beyond Journey's End — falls back to Romaji when no English title exists" }, color = c.muted, fontSize = 12.sp)
                    }
                    if (lang == current) Icon(Icons.Default.Check, null, tint = c.primary)
                }
            }
        }
    }
}