@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import java.util.UUID

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

// "%.1f"/"%.2f".format(score) was called directly in every list-item composable (browse
// cards, ranking rows, search results, seasonal cards, profile stats) — each call builds a
// java.util.Formatter and resolves the default Locale from scratch. Fine occasionally, but
// these run once per card as items scroll into view across lists that can run into the
// hundreds (Ranking, Seasonal), so it adds up to a lot of repeated formatter construction
// for a value that's just "round to N decimals". Scores are always non-negative here, so a
// plain round-and-divide + manual decimal split avoids Formatter/Locale entirely.
private fun Double.decimalString(places: Int): String {
    val factor = if (places == 1) 10.0 else 100.0
    val rounded = Math.round(this * factor)
    val whole = rounded / factor.toLong()
    val frac = rounded % factor.toLong()
    return if (places == 1) "$whole.$frac" else "$whole.${frac.toString().padStart(2, '0')}"
}
fun Double.oneDecimal(): String = decimalString(1)
fun Double.twoDecimals(): String = decimalString(2)

fun List<MediaItem>.nsfwFiltered(allowAdult: Boolean) = if (allowAdult) this else filterNot { it.isAdultContent() }

data class RelatedEntry(val relation: String, val title: String, val malId: Int = 0, val malType: String = "anime", val cover: String = "")
// Characters/staff row entries

// Japanese VA only — the Voice Actors row only ever shows the Japanese cast, so no
// need to carry every dub's entries just to filter them back out at display time.
data class VoiceActorEntry(val malId: Int, val name: String, val image: String, val url: String = "")

data class CharacterEntry(val malId: Int, val name: String, val image: String, val role: String, val url: String = "", val japaneseVoiceActor: VoiceActorEntry? = null)

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
// Community score breakdown (how everyone who scored a title split across 1-10) —
// scraped from MAL's own /stats page since the official API doesn't expose this,
// only the aggregate mean. Loaded on demand from DetailScreen's "Status distribution"
// section ("See more"), not prefetched with every title like StatusDistribution above.

data class ScoreStats(val counts: Map<Int, Int> = emptyMap()) {
    val total: Int get() = counts.values.sum()
}

enum class MediaType { Anime, Manga }

enum class WatchStatus(val label: String) { Watching("Watching"), Reading("Reading"), Plan("Plan to Watch"), Completed("Completed"), OnHold("On Hold"), Dropped("Dropped") }

// WatchStatus.label is fixed per enum value — that works for Watching/Reading because MAL's
// own API has two separate status strings for those ("watching"/"reading"), so they're two
// separate enum values already. "Plan to watch" has no manga counterpart on MAL's side (it's
// a single "plan_to_watch" status for both types — see MalApi.update/parseEntry), so Plan
// stays one enum value/API string and the type-aware wording is only applied here, at
// display time.
fun WatchStatus.displayLabel(type: MediaType): String =
    if (this == WatchStatus.Plan && type == MediaType.Manga) "Plan to Read" else label

enum class Destination(val label: String, val icon: ImageVector) { Home("Home", Icons.Default.Home), List("My list", Icons.Default.List), Discover("Discover", Icons.Default.Search), Seasonal("Seasonal", Icons.Default.DateRange), Community("Hub", Icons.Default.Groups) }

// Sub-tab within the combined Community destination (Forums + Clubs behind one switcher header)
enum class CommunityTab(val label: String) { Forums("Forums"), Clubs("Clubs") }

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
// App format label -> Jikan/MAL `type` search param, so a single-tag Discover search can
// have MAL filter by format server-side instead of only after the fact client-side.
// Returns null for a format Jikan's `type` filter doesn't recognize (falls back to
// client-side matches() filtering only).
fun jikanTypeParam(format: String): String? = when (format) {
    "TV" -> "tv"; "OVA" -> "ova"; "Movie" -> "movie"; "Special" -> "special"; "ONA" -> "ona"; "Music" -> "music"
    "Manga" -> "manga"; "Novel" -> "novel"; "Light Novel" -> "lightnovel"; "One Shot" -> "oneshot"
    "Doujinshi" -> "doujinshi"; "Manhwa" -> "manhwa"; "Manhua" -> "manhua"; "OEL" -> "oel"
    else -> null
}
// App airing-status bucket -> Jikan `status` search param. Anime and manga use different
// vocab for the "ongoing" bucket (airing vs publishing), so kind ("anime"/"manga") matters.
fun jikanStatusParam(bucket: String, kind: String): String? = when (bucket) {
    "Ongoing" -> if (kind == "anime") "airing" else "publishing"
    "Finished" -> "complete"
    "Upcoming" -> "upcoming"
    else -> null
}
// App format label -> MAL's own numeric `type` code on anime.php/manga.php's advanced
// search filter (read straight off each page's <select name="type"> options — not Jikan's
// string codes, which don't apply once the search itself goes straight to MAL). Anime and
// manga each have their own numbering, so kind matters; null falls back to client-side
// matches() filtering only, same as jikanTypeParam.
fun malAnimeTypeCode(format: String): String? = when (format) {
    "TV" -> "1"; "OVA" -> "2"; "Movie" -> "3"; "Special" -> "4"; "ONA" -> "5"; "Music" -> "6"
    else -> null
}
fun malMangaTypeCode(format: String): String? = when (format) {
    "Manga" -> "1"; "Light Novel" -> "2"; "One Shot" -> "3"; "Doujinshi" -> "4"
    "Manhwa" -> "5"; "Manhua" -> "6"; "Novel" -> "8"
    else -> null
}
// Dispatches to whichever of the two above applies — a blank format (nothing picked in the
// filter sheet) is deliberately distinct from one that doesn't map to a MAL code for this
// kind (e.g. "TV" while kind == "manga"): both return null, but callers never hit the
// second case since DiscoverFilters.format is only ever set from CommonAnimeFormats or
// CommonMangaFormats to begin with.
fun malTypeCode(kind: String, format: String): String? = if (kind == "anime") malAnimeTypeCode(format) else malMangaTypeCode(format)
// App airing-status bucket -> MAL's own numeric `status` code (again read off the page's
// own <select name="status">, separate options for anime vs manga).
fun malStatusCode(bucket: String, kind: String): String? = when (bucket) {
    "Finished" -> "2"
    "Ongoing" -> "1"
    "Upcoming" -> "3"
    else -> null
}
// Format switches media type

fun resolvedDiscoverType(format: String, fallback: String): String = when {
    format in CommonAnimeFormats -> "Anime"
    format in CommonMangaFormats -> "Manga"
    else -> fallback
}
// Sort order options

enum class DiscoverSort(val label: String) { Relevance("Relevance"), Members("Members"), Score("Score"), Newest("Newest"), Title("Title") }
// Collapse punctuation/symbols (e.g. the ★ in "Stardust★Wink") to spaces so title matching
// isn't defeated by stylized titles; also lets "startsWith"/word-boundary checks line up.

// Compiled once at class-load instead of on every normalize call — this runs per title,
// per synonym, per item, per sort, so a fresh Regex() here was a lot of avoidable
// compilation work as the (paginated, ever-growing) results list got sorted repeatedly.
private val nonAlnumRegex = Regex("[^a-z0-9]+")
fun normalizeForTitleMatch(s: String) = s.lowercase().replace(nonAlnumRegex, " ").trim()
// Match quality against a single candidate string, or null if no match at all.
// wordBoundaryRegex is query-specific but identical across every candidate/item in a given
// sort, so callers build it once and pass it in rather than recompiling per call.

fun matchTier(candidate: String, q: String, wordBoundaryRegex: Regex): Int? {
    val c = normalizeForTitleMatch(candidate)
    if (c.isBlank()) return null
    return when {
        c == q -> 0
        c.startsWith(q) -> 1
        wordBoundaryRegex.containsMatchIn(c) -> 2
        c.contains(q) -> 3
        else -> null
    }
}
// Title match ranking score. Primary/English title matches always outrank synonym-only
// matches (e.g. MAL lists "Demon Slayer" as a synonym of the unrelated anime "Onigiri" —
// that shouldn't out-rank the real "Demon Slayer: Kimetsu no Yaiba" for that query).

fun MediaItem.titleMatchRank(q: String, wordBoundaryRegex: Regex): Int {
    if (q.isBlank()) return Int.MAX_VALUE
    val primaryRank = listOf(title, titleEnglish).mapNotNull { matchTier(it, q, wordBoundaryRegex) }.minOrNull()
    if (primaryRank != null) return primaryRank
    val synonymRank = synonyms.mapNotNull { matchTier(it, q, wordBoundaryRegex) }.minOrNull()
    return synonymRank?.plus(4) ?: 8
}
// Default blank query

fun List<MediaItem>.sortedForDiscover(sort: DiscoverSort, titleLanguage: TitleLanguage, query: String = ""): List<MediaItem> {
    // Relevance leaves items in whatever order the source already returned them in — that's
    // MAL's own result order (its default, un-sorted search-page order for genre/theme
    // browsing, or its search relevance ranking for a text query) rather than something this
    // app recomputes. sortedBy is a stable sort, so items that tie on match rank keep their
    // original relative order instead of being reshuffled by some other field.
    if (sort == DiscoverSort.Relevance) {
        if (query.isBlank()) return this
        val q = normalizeForTitleMatch(query)
        val wordBoundaryRegex = Regex("\\b" + Regex.escape(q) + "\\b")
        return map { it to it.titleMatchRank(q, wordBoundaryRegex) }
            .sortedBy { it.second }
            .map { it.first }
    }
    val bySort: Comparator<MediaItem> = when (sort) {
        DiscoverSort.Relevance -> error("handled above")
        DiscoverSort.Members -> compareByDescending { it.listUsers }
        DiscoverSort.Score -> compareByDescending { it.score }
        DiscoverSort.Newest -> compareByDescending { it.startDateFull.ifBlank { it.startDate } }
        DiscoverSort.Title -> compareBy { it.resolvedTitle(titleLanguage).lowercase() }
    }
    if (query.isBlank()) return sortedWith(bySort)
    // Precompute each item's match rank once (O(n)) instead of inside the comparator, which
    // a comparison sort invokes O(n log n) times — for n items that's a lot of redundant
    // string-normalizing/regex work on every re-sort as more pages get appended.
    val q = normalizeForTitleMatch(query)
    val wordBoundaryRegex = Regex("\\b" + Regex.escape(q) + "\\b")
    return map { it to it.titleMatchRank(q, wordBoundaryRegex) }
        .sortedWith(compareBy<Pair<MediaItem, Int>> { it.second }.thenComparator { a, b -> bySort.compare(a.first, b.first) })
        .map { it.first }
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
// Which episode number the next broadcast slot (today, tomorrow, or N days out) is —
// computed from the series' first air date plus the weekly broadcast cadence, entirely in
// JST since that's the zone the weekly slot is actually anchored to; a week is 7 days
// regardless of the viewer's own timezone, so this stays correct without needing to touch
// localBroadcast()'s local-time conversion (that's for display only). Best-effort: shows
// aren't guaranteed to release exactly weekly with zero breaks, so this can drift by a
// week around a skipped episode — same caveat as any other schedule-inferred field here.
private fun MediaItem.nextEpisodeNumber(): Int? {
    val dow = runCatching { java.time.DayOfWeek.valueOf(broadcastDay.uppercase(java.util.Locale.US)) }.getOrNull() ?: return null
    val time = runCatching { java.time.LocalTime.parse(broadcastTime) }.getOrDefault(java.time.LocalTime.MIDNIGHT)
    val startDate = runCatching { java.time.LocalDate.parse(startDateFull) }.getOrNull() ?: return null
    val jst = java.time.ZoneId.of("Asia/Tokyo")
    val firstAir = startDate.with(java.time.temporal.TemporalAdjusters.nextOrSame(dow)).atTime(time).atZone(jst)
    val nowJst = java.time.ZonedDateTime.now(jst)
    var nextAir = nowJst.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(dow)).atTime(time).atZone(jst)
    if (nextAir.isBefore(nowJst)) nextAir = nextAir.plusWeeks(1)
    if (nextAir.isBefore(firstAir)) return 1
    val weeksBetween = java.time.Duration.between(firstAir, nextAir).toDays() / 7
    val epNum = weeksBetween.toInt() + 1
    return if (total > 0) minOf(epNum, total) else epNum
}
// Next episode air label

// confirmed, when supplied, is AniList's staff-curated nextAiringEpisode (see AniListApi) —
// correct across delays/hiatuses, unlike nextEpisodeNumber()'s date-math guess below.
// Callers pass it in once LibraryViewModel.loadAiringEpisode resolves it; until then (or if
// AniList has nothing for this id), this falls back to the guess for both the episode number
// AND the air time. Previously only the episode number was overridden here while the "today/
// tomorrow/in Xd" wording still came from the date-math guess — so a delayed or hiatus-ing
// show could show AniList's correct episode number next to a guessed air time that no longer
// matched it (e.g. "Ep. 8 airs today" for a episode that's actually still a week away). Using
// confirmed.airingAt for the timing too keeps the number and the "when" it's paired with
// consistent with each other.
fun MediaItem.nextEpisodeLabel(confirmed: AiringInfo? = null): String? {
    if (!airStatus.equals("Currently Airing", ignoreCase = true)) return null
    val next = confirmedAirDateTime(confirmed) ?: guessedAirDateTime() ?: return null
    val now = java.time.LocalDateTime.now()
    val hoursAway = java.time.Duration.between(now, next).toHours().coerceAtLeast(0)
    val epNum = confirmed?.episode ?: nextEpisodeNumber()
    return when {
        hoursAway < 24 -> if (epNum != null) "Ep. $epNum airs today" else "Airs today"
        hoursAway < 48 -> if (epNum != null) "Ep. $epNum airs tomorrow" else "Airs tomorrow"
        else -> if (epNum != null) "Ep. $epNum in ${hoursAway / 24}d" else "Next ep in ${hoursAway / 24}d"
    }
}
// Locale time format

fun localizedTimeLabel(time: java.time.LocalTime, is24Hour: Boolean): String =
    time.format(java.time.format.DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", java.util.Locale.getDefault()))
// Read device time format

@Composable fun systemIs24Hour(): Boolean = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
// AniList's confirmed airingAt (UTC epoch seconds) converted to the viewer's local zone —
// the authoritative counterpart to guessedAirDateTime() below. Preferred whenever present.
private fun MediaItem.confirmedAirDateTime(confirmed: AiringInfo?): java.time.LocalDateTime? {
    val airingAt = confirmed?.airingAt ?: return null
    return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(airingAt), java.time.ZoneId.systemDefault())
}
// Date-math fallback used when AniList has no confirmed nextAiringEpisode for this id yet
// (or the lookup hasn't resolved) — see nextEpisodeNumber()'s caveat above; this can drift by
// a week around a delay/hiatus, which is exactly what confirmedAirDateTime() above corrects.
private fun MediaItem.guessedAirDateTime(): java.time.LocalDateTime? {
    val (day, time) = localBroadcast() ?: return null
    val now = java.time.LocalDateTime.now()
    var next = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(day)).atTime(time)
    if (next.isBefore(now)) next = next.plusDays(7)
    return next
}
// Next airing full timestamp — prefers AniList's confirmed airingAt when available, same
// reasoning as nextEpisodeLabel() above, so the "Airing next" row's sort order and any
// displayed time-of-day both line up with whatever episode number is actually shown.
fun MediaItem.nextAirDateTime(confirmed: AiringInfo? = null): java.time.LocalDateTime? {
    if (!airStatus.equals("Currently Airing", ignoreCase = true)) return null
    return confirmedAirDateTime(confirmed) ?: guessedAirDateTime()
}