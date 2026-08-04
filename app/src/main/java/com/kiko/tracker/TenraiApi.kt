package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

private const val TENRAI = "https://api.tenrai.org/v1"

// Jikan (and so Tenrai) spells a few facet values slightly differently than MAL's official v2 API
// does — normalized here so titles pulled from Tenrai still match Discover's existing filter chips
// (CommonRatings/CommonSources/CommonMangaFormats in MainActivity.kt), which were written against
// the official API's raw values via MalApi.kt's prettify helpers.
private fun normalizeRating(jikanRating: String) = when {
    jikanRating.startsWith("PG-13") -> "PG-13"
    jikanRating.isBlank() || jikanRating == "null" -> ""
    else -> jikanRating
}
private fun normalizeSource(jikanSource: String) = when (jikanSource.lowercase()) {
    "4-koma manga" -> "4-Koma Manga"
    "" , "null" -> ""
    else -> jikanSource.split(' ', '-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
private fun normalizeMangaFormat(jikanType: String) = when (jikanType.lowercase()) {
    "one-shot", "oneshot" -> "One Shot"
    else -> jikanType
}

// Tenrai (tenrai.org) is an unofficial, authless MAL mirror that follows the Jikan v4 schema — used
// here purely to plug the one hole MAL's own official API leaves open: there is no server-side way
// to search by genre/theme/demographic (see DiscoverFilters in MainActivity.kt). Everything else in
// Discover (title search, ranking, seasonal, the user's own list) still goes through the real MalApi;
// this is only ever used to build a *candidate pool* that LibraryViewModel.visibleDiscoverResults then
// narrows with the exact same client-side `matches()` filtering it already applies to every other pool.
class TenraiApi {
    private val client = OkHttpClient()

    // name (lowercased) -> MAL genre id, combining all four of MAL's facets (genres, explicit_genres,
    // themes, demographics) into one map per media kind — the id space is shared across facets, and
    // Tenrai/Jikan's /anime and /manga search endpoints take any of them through the same `genres` param.
    // Fetched once and cached for the process lifetime since this taxonomy is effectively static.
    private object Cache {
        val byKind = mutableMapOf<String, Map<String, Int>>()
    }

    private suspend fun genreNameMap(kind: String): Map<String, Int> {
        Cache.byKind[kind]?.let { return it }
        val facets = listOf("genres", "explicit_genres", "themes", "demographics")
        val merged = withContext(Dispatchers.IO) {
            coroutineScope {
                facets.map { facet -> async { runCatching { fetchGenreFacet(kind, facet) }.getOrElse { emptyMap() } } }.awaitAll()
            }
        }.fold(emptyMap<String, Int>()) { acc, m -> acc + m }
        Cache.byKind[kind] = merged
        return merged
    }

    private fun fetchGenreFacet(kind: String, facet: String): Map<String, Int> {
        val body = getRaw("$TENRAI/genres/$kind?filter=$facet")
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyMap()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            name.lowercase() to o.optInt("mal_id")
        }.toMap()
    }

    // Resolves the Discover chip labels (e.g. "Villainess", "Ecchi", "Shounen") the user picked into
    // Tenrai/MAL genre ids for the given media kind. Unknown names are silently dropped rather than
    // failing the whole search — a label that doesn't resolve just contributes nothing to the pool.
    suspend fun resolveGenreIds(kind: String, names: Set<String>): List<Int> {
        if (names.isEmpty()) return emptyList()
        val map = genreNameMap(kind)
        return names.mapNotNull { map[it.lowercase()] }
    }

    // Builds a candidate pool of titles tagged with ANY of the given genre/theme/demographic ids —
    // deliberately a broad OR across ids (not Jikan's own AND-within-one-call `genres=1,10` semantics),
    // because the caller (runDiscoverSearch) still runs the exact AND-across-facets/OR-within-facet
    // logic locally via MediaItem.matches() afterwards. This only needs to be a superset.
    // `pages` is per id, fetched in parallel — 2 pages (up to 100 titles) per id sorted by member count
    // is enough to surface a niche tag's actual titles instead of just whatever's in the top overall charts.
    suspend fun searchByGenreIds(kind: String, ids: List<Int>, pages: Int = 2, limit: Int = 50, includeAdult: Boolean): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                ids.flatMap { id ->
                    (1..pages).map { page -> async { runCatching { searchPage(kind, id, page, limit, includeAdult) }.getOrElse { emptyList() } } }
                }.awaitAll().flatten()
            }
        }.distinctBy { it.id }
    }

    private fun searchPage(kind: String, genreId: Int, page: Int, limit: Int, includeAdult: Boolean): List<MediaItem> {
        val sfwParam = if (includeAdult) "" else "&sfw"
        val url = "$TENRAI/$kind?genres=$genreId&order_by=members&sort=desc&page=$page&limit=$limit$sfwParam"
        val body = getRaw(url)
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).map { parseJikanEntry(kind, arr.getJSONObject(it)) }
    }

    private fun getRaw(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("Tenrai request failed (${resp.code}): ${text.take(300)}")
            return text
        }
    }

    // Parses one Jikan-v4-shaped anime/manga object (Tenrai's schema) into the app's own MediaItem.
    // Deliberately conservative: `inUserList` always starts false and myRating/progress/status always
    // start blank/Plan, since Tenrai has no concept of a signed-in MAL account — the caller reconciles
    // against the user's already-loaded library (LibraryViewModel.items) afterwards to restore those
    // for any title the person already tracks, the same way a fresh MAL search result would look before
    // that reconciliation.
    private fun parseJikanEntry(kind: String, n: JSONObject): MediaItem {
        fun tagList(field: String) = n.optJSONArray(field)?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).optString("name") } } ?: emptyList()
        val genresList = tagList("genres") + tagList("explicit_genres")
        val contentThemes = tagList("themes")
        val demographics = tagList("demographics")
        val images = n.optJSONObject("images")?.optJSONObject("jpg") ?: n.optJSONObject("images")?.optJSONObject("webp")
        val cover = images?.optString("large_image_url")?.takeIf { it.isNotBlank() }
            ?: images?.optString("image_url")?.takeIf { it.isNotBlank() } ?: ""
        val creator = if (kind == "anime") {
            n.optJSONArray("studios")?.optJSONObject(0)?.optString("name") ?: ""
        } else {
            n.optJSONArray("authors")?.optJSONObject(0)?.optString("name") ?: ""
        }
        val titlesArr = n.optJSONArray("titles")
        fun titleOfType(t: String) = titlesArr?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("type").equals(t, ignoreCase = true) }?.optString("title") }
        val titleEnglish = n.optString("title_english").takeIf { it.isNotBlank() } ?: titleOfType("English") ?: ""
        val japanese = titleOfType("Japanese")
        val synonyms = listOfNotNull(japanese?.takeIf { it.isNotBlank() })
        val startDateFull = n.optJSONObject("aired")?.optString("from") ?: n.optJSONObject("published")?.optString("from") ?: ""
        val endDateFull = n.optJSONObject("aired")?.optString("to") ?: n.optJSONObject("published")?.optString("to") ?: ""
        val season = n.optString("season").takeIf { it.isNotBlank() }?.replaceFirstChar(Char::uppercase) ?: ""
        val broadcastDay = n.optJSONObject("broadcast")?.optString("day")?.removeSuffix("s")?.takeIf { it.isNotBlank() } ?: ""
        val broadcastTime = n.optJSONObject("broadcast")?.optString("time")?.takeIf { it.isNotBlank() } ?: ""
        return MediaItem(
            id = n.optInt("mal_id").toString(),
            title = n.optString("title"),
            type = if (kind == "anime") MediaType.Anime else MediaType.Manga,
            status = WatchStatus.Plan,
            progress = 0,
            total = if (kind == "anime") n.optInt("episodes", 0) else n.optInt("chapters", 0),
            genre = genresList.firstOrNull() ?: "",
            genres = genresList,
            contentThemes = contentThemes,
            demographics = demographics,
            cover = cover,
            synopsis = n.optString("synopsis").takeIf { it != "null" } ?: "",
            background = n.optString("background").takeIf { it != "null" } ?: "",
            score = n.optDouble("score", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            rank = n.optInt("rank", 0),
            popularity = n.optInt("popularity", 0),
            listUsers = n.optInt("members", 0),
            creator = creator,
            startDate = startDateFull.take(4),
            season = season,
            format = n.optString("type").takeIf { it.isNotBlank() && it != "null" }?.let { if (kind == "manga") normalizeMangaFormat(it) else it } ?: "",
            airStatus = n.optString("status"),
            source = normalizeSource(n.optString("source")),
            rating = normalizeRating(n.optString("rating")),
            volumes = n.optInt("volumes", 0),
            titleEnglish = titleEnglish,
            startDateFull = startDateFull,
            endDateFull = endDateFull,
            synonyms = synonyms,
            broadcastDay = broadcastDay,
            broadcastTime = broadcastTime,
            nsfw = if (genresList.any { it.equals("Hentai", ignoreCase = true) }) "black" else "white",
            inUserList = false,
        )
    }
}