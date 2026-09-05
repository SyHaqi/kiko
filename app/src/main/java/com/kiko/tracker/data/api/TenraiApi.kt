package com.kiko.tracker.data.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.ReviewEntry
import com.kiko.tracker.data.model.WatchStatus

private const val TENRAI = "https://api.tenrai.org/v1"

data class TenraiPage(val items: List<MediaItem>, val hasMore: Boolean)

// Tenrai/Jikan rate-limits to a
// faster than that come
// callers below (getOrNull/getOrElse), which
// out into one detail
// incomplete subset of results
// requests and retries 429s
// eventually succeed rather than
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

    // Per-(kind, facet) name->id maps
    // map per kind covering
    // only fetches the specific
    private object Cache {
        val byFacet = mutableMapOf<Pair<String, String>, Map<String, Int>>()
        val inFlight = mutableMapOf<Pair<String, String>, Deferred<Map<String, Int>>>()
        val mutex = Mutex()
        // Its own SupervisorJob-backed scope
        // callers racing here (prewarmGenreNames,
        // resolveGenreIds, fired on Apply)
        // prewarm coroutine can get
        // still waiting on that
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    // One facet's name->id map
    // like genreNameMap's old whole-kind
    // resolveGenreIds for why that
    // gets retried on the
    // the old merged version
    private suspend fun facetNameMap(kind: String, facet: String): Map<String, Int> {
        val key = kind to facet
        Cache.byFacet[key]?.let { return it }
        val deferred = Cache.mutex.withLock {
            Cache.byFacet[key]?.let { return it }
            Cache.inFlight.getOrPut(key) { Cache.scope.async { runCatching { fetchGenreFacet(kind, facet) }.onSuccess { Cache.byFacet[key] = it }.getOrElse { emptyMap() } } }
        }
        return try {
            deferred.await()
        } finally {
            Cache.mutex.withLock { if (Cache.inFlight[key] === deferred) Cache.inFlight.remove(key) }
        }
    }

    // The "genres" chip picker
    // "explicit_genres" facets merged together
    // not a separate category)
    // demographics each map to
    private suspend fun genreFacetMap(kind: String): Map<String, Int> = coroutineScope {
        val genres = async { facetNameMap(kind, "genres") }
        val explicit = async { facetNameMap(kind, "explicit_genres") }
        genres.await() + explicit.await()
    }

    // Populates genreNameMap's cache ahead
    // first genre-filtered search of
    // they tap Apply —
    // the filter sheet opens.
    // know which category the
    // ever fetches what a
    // dependency — Apply is
    // wasn't triggered at all)
    // fetch already no-ops once
    suspend fun prewarmGenreNames(kind: String) {
        coroutineScope {
            listOf("genres", "explicit_genres", "themes", "demographics").map { facet -> async { facetNameMap(kind, facet) } }.awaitAll()
        }
    }

    // Resolve labels to ids.
    // (genres+explicit_genres / themes /
    // fetching all 4 the
    // only ever picks genre
    // 4 on an un-prewarmed
    suspend fun resolveGenreIds(kind: String, genres: Set<String>, themes: Set<String> = emptySet(), demographics: Set<String> = emptySet()): List<Int> {
        if (genres.isEmpty() && themes.isEmpty() && demographics.isEmpty()) return emptyList()
        val (genreMap, themeMap, demoMap) = coroutineScope {
            val g = if (genres.isNotEmpty()) async { genreFacetMap(kind) } else null
            val th = if (themes.isNotEmpty()) async { facetNameMap(kind, "themes") } else null
            val d = if (demographics.isNotEmpty()) async { facetNameMap(kind, "demographics") } else null
            Triple(g?.await() ?: emptyMap(), th?.await() ?: emptyMap(), d?.await() ?: emptyMap())
        }
        // Same "fail the whole
        // before: a selection like
        // Action+Adventure the moment Fantasy
        // LibraryViewModel has no way
        // keeps using MalGenreApi with
        // the way it does
        val resolved = genres.map { genreMap[it.lowercase()] } + themes.map { themeMap[it.lowercase()] } + demographics.map { demoMap[it.lowercase()] }
        return if (resolved.any { it == null }) emptyList() else resolved.filterNotNull()
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

    // Id -> name, one
    // above, since a caller
    // genre vs theme vs
    // something). Used by MalCompanyApi
    // into filterable names; this
    // once), not a search
    // list rather than going
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

    // Superseded by MalGenreApi.search, which
    // instead of Tenrai's members-ranked
    // Left in place only
    // current call site uses
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

    // Single genre/theme/demographic id search,
    // status filtered server-side by
    // page, but returns the
    // up with what the
    // a fixed members-sorted candidate
    //
    // has_next_page itself isn't fully
    // known to under-report pagination
    // type + status, same
    // jikan-me/jikan#273, where a genre-filtered
    // when the real total
    // a far stronger "there's
    // "there isn't" one, so
    // only a genuinely short/empty
    // Superseded by MalGenreApi.search —
    // current call site uses
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

    // Fetch one item's full
    // status) by id. Used
    // id from the page
    // + year per credited
    // just look that item
    // Same throttled/retried getRaw() as
    // a long credited-works list
    suspend fun fetchItemFacets(kind: String, malId: Int): MediaItem? = withContext(Dispatchers.IO) {
        runCatching {
            val obj = JSONObject(getRaw("$TENRAI/$kind/$malId")).optJSONObject("data") ?: return@runCatching null
            parseJikanEntry(kind, obj)
        }.getOrNull()
    }

    // Fetch reviews row for
    suspend fun fetchReviews(kind: String, malId: Int): List<ReviewEntry> = withContext(Dispatchers.IO) {
        runCatching {
            // MAL's own most-helpful order
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
                // Keep the API's most-helpful
            }
        }.getOrElse { emptyList() }
    }

    // Caps concurrent Tenrai requests
    // requests faster than the
    // to fail and get
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