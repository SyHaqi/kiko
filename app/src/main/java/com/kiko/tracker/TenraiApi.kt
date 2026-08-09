package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

private const val TENRAI = "https://api.tenrai.org/v1"

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
private fun normalizeMangaFormat(jikanType: String) = when (jikanType.lowercase()) {
    "one-shot", "oneshot" -> "One Shot"
    else -> jikanType
}
// "Last, First" to "First Last"
private fun reorderName(raw: String): String {
    val parts = raw.split(", ")
    return if (parts.size == 2) "${parts[1]} ${parts[0]}" else raw
}

// Genre search via Tenrai
class TenraiApi {
    private val client = OkHttpClient()

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

    // Build broad candidate pool
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

    // Fetch characters row for detail page
    suspend fun fetchCharacters(kind: String, malId: Int): List<CharacterEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONObject(getRaw("$TENRAI/$kind/$malId/characters")).optJSONArray("data") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val ch = o.optJSONObject("character") ?: return@mapNotNull null
                CharacterEntry(
                    malId = ch.optInt("mal_id"),
                    name = reorderName(ch.optString("name")),
                    image = ch.optJSONObject("images")?.optJSONObject("jpg")?.optString("image_url").orEmpty(),
                    role = o.optString("role").ifBlank { "Supporting" },
                    url = ch.optString("url"),
                )
            }
        }.getOrElse { emptyList() }
    }

    // Fetch staff row for detail page
    suspend fun fetchStaff(kind: String, malId: Int): List<StaffEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONObject(getRaw("$TENRAI/$kind/$malId/staff")).optJSONArray("data") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val person = o.optJSONObject("person") ?: return@mapNotNull null
                val positions = o.optJSONArray("positions")
                StaffEntry(
                    malId = person.optInt("mal_id"),
                    name = reorderName(person.optString("name")),
                    image = person.optJSONObject("images")?.optJSONObject("jpg")?.optString("image_url").orEmpty(),
                    role = positions?.let { p -> (0 until p.length()).joinToString(", ") { p.getString(it) } }.orEmpty(),
                    url = person.optString("url"),
                )
            }
        }.getOrElse { emptyList() }
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
            n.optJSONArray("authors")?.let { arr -> (0 until arr.length()).mapNotNull { reorderName(arr.getJSONObject(it).optString("name")).takeIf { s -> s.isNotBlank() } } } ?: emptyList()
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