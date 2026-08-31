package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

private const val TENRAI = "https://api.tenrai.org/v1"

data class TenraiPage(val items: List<MediaItem>, val hasMore: Boolean)

// Tenrai/Jikan rate-limits to a few requests per second. Requests that get fired
// faster than that come back 429 and were previously swallowed silently by the
// callers below (getOrNull/getOrElse), which is why an author search — which fans
// out into one detail request per credited work — used to return a different,
// incomplete subset of results on every "Apply". This shared limiter caps in-flight
// requests and retries 429s with backoff instead of dropping them, so all requests
// eventually succeed rather than randomly disappearing.
private object TenraiThrottle {
    val semaphore = Semaphore(3)
}
private class TenraiRateLimitException(url: String) : IOException("Tenrai rate-limited: $url")

// Normalize Jikan facet values
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

// Genre search via Tenrai
class TenraiApi {
    private val client = NetworkClient.shared

    // Build genre name-id map
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

    private suspend fun fetchGenreFacet(kind: String, facet: String): Map<String, Int> {
        val body = getRaw("$TENRAI/genres/$kind?filter=$facet")
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyMap()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            name.lowercase() to o.optInt("mal_id")
        }.toMap()
    }

    // Resolve labels to ids
    suspend fun resolveGenreIds(kind: String, names: Set<String>): List<Int> {
        if (names.isEmpty()) return emptyList()
        val map = genreNameMap(kind)
        return names.mapNotNull { map[it.lowercase()] }
    }

    // Id -> name, one map per facet (kept separate rather than merged like genreNameMap
    // above, since a caller translating ids back to names needs to know *which* bucket —
    // genre vs theme vs demographic — each id belongs in, not just that it resolves to
    // something). Used by MalCompanyApi to turn a studio page's data-genre="1,2,50" ids
    // into filterable names; this is a small, static reference lookup (~150 rows, cached
    // once), not a search — MalCompanyApi still scrapes MAL directly for the actual anime
    // list rather than going through Tenrai's own search/ranking endpoints.
    data class FacetIdMaps(val genres: Map<Int, String>, val explicitGenres: Map<Int, String>, val themes: Map<Int, String>, val demographics: Map<Int, String>)
    private object IdCache { val byKind = mutableMapOf<String, FacetIdMaps>() }

    suspend fun facetIdMaps(kind: String): FacetIdMaps {
        IdCache.byKind[kind]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            coroutineScope {
                val g = async { runCatching { fetchGenreFacetById(kind, "genres") }.getOrElse { emptyMap() } }
                val eg = async { runCatching { fetchGenreFacetById(kind, "explicit_genres") }.getOrElse { emptyMap() } }
                val th = async { runCatching { fetchGenreFacetById(kind, "themes") }.getOrElse { emptyMap() } }
                val dm = async { runCatching { fetchGenreFacetById(kind, "demographics") }.getOrElse { emptyMap() } }
                FacetIdMaps(g.await(), eg.await(), th.await(), dm.await())
            }
        }
        IdCache.byKind[kind] = result
        return result
    }

    private suspend fun fetchGenreFacetById(kind: String, facet: String): Map<Int, String> {
        val body = getRaw("$TENRAI/genres/$kind?filter=$facet")
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyMap()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            o.optInt("mal_id") to name
        }.toMap()
    }

    // Superseded by MalGenreApi.search, which scrapes MAL's own advanced search directly
    // instead of Tenrai's members-ranked candidate pool (capped at each chart's top ~500).
    // Left in place only in case something still needs a raw Tenrai genre search; no
    // current call site uses this.
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

    private suspend fun searchPage(kind: String, genreId: Int, page: Int, limit: Int, includeAdult: Boolean): List<MediaItem> {
        val sfwParam = if (includeAdult) "" else "&sfw"
        val url = "$TENRAI/$kind?genres=$genreId&order_by=members&sort=desc&page=$page&limit=$limit$sfwParam"
        val body = getRaw(url)
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).map { parseJikanEntry(kind, arr.getJSONObject(it)) }
    }

    // Single genre/theme/demographic id search, with format ("type") and airing/publish
    // status filtered server-side by MAL's own search — same as searchByGenreIds's single
    // page, but returns the real has_next_page flag so the results (and their count) line
    // up with what the person sees searching that same tag on the MAL website, rather than
    // a fixed members-sorted candidate pool that silently drops anything outside it.
    //
    // has_next_page itself isn't fully trustworthy though: MAL's own search backend is
    // known to under-report pagination once multiple filters are ANDed together (genre +
    // type + status, same shape as a "villainess" + "manhwa" + "finished" search) — see
    // jikan-me/jikan#273, where a genre-filtered search reported only one page of results
    // when the real total spanned dozens. A page that comes back full (== limit items) is
    // a far stronger "there's probably more" signal than a `has_next_page: false` flag is a
    // "there isn't" one, so a full page keeps hasMore true regardless of what the flag says;
    // only a genuinely short/empty page is trusted to mean we've reached the real end.
    // Superseded by MalGenreApi.search — see the doc note on searchByGenreIds above. No
    // current call site uses this.
    suspend fun searchFiltered(kind: String, genreId: Int, type: String?, status: String?, page: Int, limit: Int = 25, includeAdult: Boolean): TenraiPage = withContext(Dispatchers.IO) {
        val sfwParam = if (includeAdult) "" else "&sfw"
        val typeParam = type?.let { "&type=$it" } ?: ""
        val statusParam = status?.let { "&status=$it" } ?: ""
        val url = "$TENRAI/$kind?genres=$genreId&order_by=members&sort=desc&page=$page&limit=$limit$typeParam$statusParam$sfwParam"
        val body = getRaw(url)
        val json = JSONObject(body)
        val arr = json.optJSONArray("data") ?: return@withContext TenraiPage(emptyList(), false)
        val items = (0 until arr.length()).map { parseJikanEntry(kind, arr.getJSONObject(it)) }
        val apiHasNext = json.optJSONObject("pagination")?.optBoolean("has_next_page", false) == true
        TenraiPage(items, apiHasNext || items.size >= limit)
    }

    // Fetch one item's full facet data (genres/themes/demographics/source/rating/airing
    // status) by id. Used to enrich author/studio search rows that only carry a title +
    // id from the page they were scraped off of — MalPeopleApi's person page lists format
    // + year per credited work and nothing else, so this is the "we already have the id,
    // just look that item up" fetch that fills in the rest, one request per credited work.
    // Same throttled/retried getRaw() as every other Tenrai call, so fanning this out over
    // a long credited-works list doesn't trip the 429 issue described above.
    suspend fun fetchItemFacets(kind: String, malId: Int): MediaItem? = withContext(Dispatchers.IO) {
        runCatching {
            val obj = JSONObject(getRaw("$TENRAI/$kind/$malId")).optJSONObject("data") ?: return@runCatching null
            parseJikanEntry(kind, obj)
        }.getOrNull()
    }

    // Fetch reviews row for detail page
    suspend fun fetchReviews(kind: String, malId: Int): List<ReviewEntry> = withContext(Dispatchers.IO) {
        runCatching {
            // MAL's own most-helpful order (Tenrai's sort values are newest|oldest|most_helpful)
            val arr = JSONObject(getRaw("$TENRAI/$kind/$malId/reviews?sort=most_helpful")).optJSONArray("data") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val text = o.optString("review").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val user = o.optJSONObject("user")
                val tagsArr = o.optJSONArray("tags")
                val tags = tagsArr?.let { t -> (0 until t.length()).map { t.getString(it) } } ?: emptyList()
                val reactionScore = o.optJSONObject("reactions")?.optInt("overall", 0) ?: 0
                ReviewEntry(
                    malId = o.optInt("mal_id"),
                    username = user?.optString("username")?.takeIf { it.isNotBlank() } ?: "Anonymous",
                    userImage = user?.optJSONObject("images")?.optJSONObject("jpg")?.optString("image_url").orEmpty(),
                    review = text.trim(),
                    score = o.optInt("score", 0),
                    tags = tags,
                    reactionScore = reactionScore,
                    isSpoiler = o.optBoolean("is_spoiler", false),
                    url = o.optString("url"),
                )
                // Keep the API's most-helpful order as-is
            }
        }.getOrElse { emptyList() }
    }

    // Caps concurrent Tenrai requests and retries 429s with backoff, since firing
    // requests faster than the API's rate limit previously caused those requests
    // to fail and get silently dropped by callers instead of eventually succeeding.
    private suspend fun getRaw(url: String): String = TenraiThrottle.semaphore.withPermit {
        var lastError: IOException? = null
        repeat(4) { attempt ->
            if (attempt > 0) delay(300L * (1 shl (attempt - 1))) // 300ms, 600ms, 1200ms
            try {
                return@withPermit getRawOnce(url)
            } catch (e: TenraiRateLimitException) {
                lastError = e
            } catch (e: IOException) {
                throw e
            }
        }
        throw lastError ?: IOException("Tenrai request failed: $url")
    }

    private fun getRawOnce(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (resp.code == 429) throw TenraiRateLimitException(url)
            if (!resp.isSuccessful) throw IOException("Tenrai request failed (${resp.code}): ${text.take(300)}")
            return text
        }
    }

    // Parse Tenrai into MediaItem
    private fun parseJikanEntry(kind: String, n: JSONObject): MediaItem {
        fun tagList(field: String) = n.optJSONArray(field)?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).optString("name") } } ?: emptyList()
        val genresList = tagList("genres") + tagList("explicit_genres")
        val contentThemes = tagList("themes")
        val demographics = tagList("demographics")
        val images = n.optJSONObject("images")?.optJSONObject("jpg") ?: n.optJSONObject("images")?.optJSONObject("webp")
        val cover = images?.optString("large_image_url")?.takeIf { it.isNotBlank() }
            ?: images?.optString("image_url")?.takeIf { it.isNotBlank() } ?: ""
        val allCreators = if (kind == "anime") {
            n.optJSONArray("studios")?.let { arr -> (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name").takeIf { s -> s.isNotBlank() } } } ?: emptyList()
        } else {
            n.optJSONArray("authors")?.let { arr -> (0 until arr.length()).mapNotNull { reorderMalPersonName(arr.getJSONObject(it).optString("name")).takeIf { s -> s.isNotBlank() } } } ?: emptyList()
        }
        val creator = allCreators.firstOrNull() ?: ""
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
            title = n.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: titleEnglish,
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
            allCreators = allCreators.joinToString(", "),
            startDate = startDateFull.take(4),
            season = season,
            format = n.optString("type").takeIf { it.isNotBlank() && it != "null" }?.let { if (kind == "manga") normalizeMangaFormatLabel(it) else it } ?: "",
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