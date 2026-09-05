package com.kiko.tracker.data.api

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom
import com.kiko.tracker.BuildConfig
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.RelatedEntry
import com.kiko.tracker.data.model.StatusDistribution
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.ui.components.normalizeMalMarkup
import com.kiko.tracker.ui.components.parseBlocks
import com.kiko.tracker.ui.screens.ForumBlock

private const val API = "https://api.myanimelist.net/v2"
const val MAL_REDIRECT = "com.kiko.tracker://oauth/callback"

// Android's bundled org.json (unlike
// `null` value to the
// sentinel overrides toString() to
// via String.valueOf() rather than
// explicit `"title": null` for
// rows silently rendered the
// title field safely, filtering
private fun JSONObject.safeTitle(key: String = "title"): String =
    optString(key).takeIf { it.isNotBlank() && it != "null" } ?: ""

private fun prettify(raw: String) = raw.split('_').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
private fun prettifyFormat(raw: String) = when (raw.lowercase()) {
    "tv" -> "TV"; "ova" -> "OVA"; "ona" -> "ONA"; "oel" -> "OEL"
    else -> prettify(raw)
}
private fun prettifyRating(raw: String) = when (raw.lowercase()) {
    "g" -> "G - All Ages"
    "pg" -> "PG - Children"
    "pg_13" -> "PG-13"
    "r" -> "R - 17+ (violence & profanity)"
    "r+" -> "R+ - Mild Nudity"
    "rx" -> "Rx - Hentai"
    else -> prettify(raw)
}
// Fix underscore genre names
private fun prettifySource(raw: String) = when (raw.lowercase()) {
    "4_koma_manga" -> "4-Koma Manga"
    else -> prettify(raw)
}

/** Thrown for token refresh */
private class AuthExpired : IOException()

// Per-(kind,id) English-title cache, process-wide
// once MalApi.englishTitles resolves one
// to Discover, a re-applied
// Deliberately a top-level object
// per call site (`MalApi(context)`)
// actually accumulate anything across
private object EnglishTitleCache {
    val map = java.util.concurrent.ConcurrentHashMap<Pair<String, Int>, String>()
}

// Extract first forum image
// Previously this duplicated its
// same BBCode parser ForumBody
// fixes here kept drifting
// MAL-hosted image URL that
// own regexes required an
// and the topic silently
// snapshot row entirely). Running
// guarantees the thumbnail and
private fun firstImageBlockUrl(blocks: List<ForumBlock>): String? {
    for (b in blocks) when (b) {
        is ForumBlock.ImageBlock -> if (!b.resolveTenor) return b.url
        is ForumBlock.Quote -> firstImageBlockUrl(b.blocks)?.let { return it }
        else -> {}
    }
    return null
}
// Was file-private; now internal
// (e.g. LibraryViewModel.loadHomeAnnouncement, which fetches
// instead of running forumTopics'
// same extraction logic instead
internal fun firstImageUrl(body: String): String? =
    firstImageBlockUrl(parseBlocks(normalizeMalMarkup(body), androidx.compose.ui.graphics.Color.Unspecified))

// User-submitted title recommendation. isAuto
// algorithmically-generated pick the website
// user-submitted recommendations for titles
// entries have no real
data class RecommendedEntry(val malId: Int, val title: String, val cover: String, val votes: Int, val malType: String = "anime", val isAuto: Boolean = false)

// One season chart page
data class SeasonalPage(val items: List<MediaItem>, val hasMore: Boolean)
data class SearchPage(val items: List<MediaItem>, val hasMore: Boolean)

// Forum subboard entry
data class ForumSubboard(val id: Int, val title: String)

// One forum board
data class ForumBoard(val id: Int, val title: String, val description: String = "", val subboards: List<ForumSubboard> = emptyList())

// Top-level board category
data class ForumCategory(val title: String, val boards: List<ForumBoard>)

// Topic or post author
data class ForumUser(val id: Int = 0, val name: String = "", val avatar: String = "")

// One topic list row
data class ForumTopic(
    val id: Int, val title: String, val createdAt: String, val author: ForumUser,
    val postCount: Int, val lastPostAt: String, val lastPostAuthor: ForumUser, val isLocked: Boolean = false,
    val imageUrl: String? = null,
)

// One topic listing page
data class ForumTopicsPage(val items: List<ForumTopic>, val hasMore: Boolean)

// Single topic reply
data class ForumPost(val id: Int, val number: Int, val createdAt: String, val author: ForumUser, val body: String, val signature: String = "")

data class ForumPollOption(val text: String, val votes: Int)
data class ForumPoll(val question: String, val closed: Boolean, val options: List<ForumPollOption>)

// Topic posts and poll
data class ForumTopicDetail(val title: String, val posts: List<ForumPost>, val poll: ForumPoll?, val hasMore: Boolean)

// Home snapshot card
data class NewsSnapshot(val topicId: Int, val title: String, val imageUrl: String)

// User profile and stats
data class MalProfile(
    val name: String = "",
    val picture: String = "",
    val gender: String = "",
    val location: String = "",
    val birthday: String = "",
    val joinedAt: String = "",
    val animeDaysWatched: Double = 0.0,
    val animeMeanScore: Double = 0.0,
    val animeEpisodesWatched: Int = 0,
    val animeTotalEntries: Int = 0,
    val animeWatching: Int = 0,
    val animeCompleted: Int = 0,
    val animeOnHold: Int = 0,
    val animeDropped: Int = 0,
    val animePlanToWatch: Int = 0,
)

class MalApi(private val context: Context) {
    private val prefs = context.getSharedPreferences("mal_session", Context.MODE_PRIVATE)
    val signedIn get() = !prefs.getString("access_token", null).isNullOrBlank()
    // Use OkHttp for PATCH
    private val client = NetworkClient.shared

    companion object {
        // The "News Discussion" board
        // re-fetch the entire forum
        // pull-to-refresh) just to look
        // full round trip off
        @Volatile private var newsBoardIdCache: Int? = null
    }

    // MAL's official anime/manga search
    // characters with a 400
    // server-side — and plenty
    // Japanese titles with subtitles)
    // title still searches on
    private fun clampMalQuery(query: String) = query.take(64)

    fun authUrl(): String {
        val verifier = randomToken(48)
        val state = randomToken(18)
        prefs.edit().putString("verifier", verifier).putString("state", state).apply()
        // PKCE plain challenge
        return Uri.parse("https://myanimelist.net/v1/oauth2/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.MAL_CLIENT_ID)
            .appendQueryParameter("redirect_uri", MAL_REDIRECT)
            .appendQueryParameter("code_challenge", verifier)
            .appendQueryParameter("state", state)
            .build().toString()
    }

    suspend fun finishAuth(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        require(uri.getQueryParameter("state") == prefs.getString("state", null)) { "Sign in state did not match" }
        val code = requireNotNull(uri.getQueryParameter("code")) { uri.getQueryParameter("error") ?: "No authorization code returned" }
        val verifier = requireNotNull(prefs.getString("verifier", null)) { "Sign in session expired, please try again" }
        val response = form(
            "https://myanimelist.net/v1/oauth2/token",
            mapOf(
                "client_id" to BuildConfig.MAL_CLIENT_ID,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to MAL_REDIRECT,
                "code_verifier" to verifier
            ),
            auth = false
        )
        storeTokens(JSONObject(response))
    } }

    fun signOut() = prefs.edit().clear().apply()

    suspend fun library(): List<MediaItem> = withContext(Dispatchers.IO) {
        val anime = async { fetchList("anime") }
        val manga = async { fetchList("manga") }
        anime.await() + manga.await()
    }

    // Signed-in user profile
    suspend fun profile(): MalProfile = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/users/@me?fields=name,picture,gender,birthday,location,joined_at,anime_statistics") }
        val j = JSONObject(body)
        val stats = j.optJSONObject("anime_statistics") ?: JSONObject()
        MalProfile(
            name = j.optString("name"),
            picture = j.optString("picture"),
            gender = j.optString("gender").takeIf { it.isNotBlank() }?.let(::prettify) ?: "",
            location = j.optString("location"),
            birthday = j.optString("birthday"),
            joinedAt = j.optString("joined_at"),
            animeDaysWatched = stats.optDouble("num_days_watched", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            animeMeanScore = stats.optDouble("mean_score", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            animeEpisodesWatched = stats.optInt("num_episodes", 0),
            animeTotalEntries = stats.optInt("num_items", 0),
            animeWatching = stats.optInt("num_items_watching", 0),
            animeCompleted = stats.optInt("num_items_completed", 0),
            animeOnHold = stats.optInt("num_items_on_hold", 0),
            animeDropped = stats.optInt("num_items_dropped", 0),
            animePlanToWatch = stats.optInt("num_items_plan_to_watch", 0),
        )
    }

    // Search anime and manga
    suspend fun search(query: String, type: MediaType?, offset: Int = 0): SearchPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchPage(emptyList(), false)
        if (type == null) {
            // Merges both kinds' first
            // kinds isn't well-defined here,
            val pages = listOf(searchKind(query, "anime", offset), searchKind(query, "manga", offset))
            return@withContext SearchPage(pages.flatMap { it.items }, pages.any { it.hasMore })
        }
        searchKind(query, if (type == MediaType.Anime) "anime" else "manga", offset)
    }

    // Lightweight title-only lookup for
    // Deliberately requests minimal fields
    // and just returns plain
    // autofill the search field
    suspend fun suggestTitles(query: String, type: MediaType?, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val kinds = if (type == null) listOf("anime", "manga") else listOf(if (type == MediaType.Anime) "anime" else "manga")
        val results = coroutineScope { kinds.map { kind -> async { suggestKind(query, kind, limit) } }.awaitAll().flatten() }
        val q = query.trim().lowercase()
        // Titles starting with the
        results.distinctBy { it.lowercase() }
            .sortedWith(compareBy({ !it.lowercase().startsWith(q) }, { it.length }))
            .take(limit)
    }

    private suspend fun suggestKind(query: String, kind: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(clampMalQuery(query), "UTF-8")
        val body = runCatching { authorized { get("$API/$kind?q=$encoded&limit=$limit&nsfw=true&fields=id,title") } }.getOrElse { return@withContext emptyList() }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optJSONObject("node")?.safeTitle()?.takeIf { t -> t.isNotBlank() } }
    }

    // Current season anime list
    suspend fun seasonalAnime(limit: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        val (year, season) = currentSeason()
        val body = authorized { get("$API/anime/season/$year/$season?limit=$limit&nsfw=true&fields=${browseFields("anime")}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry("anime", arr.getJSONObject(it)) }
    }

    suspend fun upcomingAnime(limit: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/anime/ranking?ranking_type=upcoming&limit=$limit&nsfw=true&fields=${browseFields("anime")}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry("anime", arr.getJSONObject(it)) }
    }

    // Anime or manga ranking
    suspend fun ranking(type: MediaType, rankingType: String, limit: Int = 25): List<MediaItem> = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val body = authorized { get("$API/$kind/ranking?ranking_type=$rankingType&limit=$limit&nsfw=true&fields=${browseFields(kind)}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry(kind, arr.getJSONObject(it)) }
    }

    // Seasonal anime chart data
    suspend fun seasonalAnime(year: Int, season: String, limit: Int = 25, offset: Int = 0, sort: String = "anime_num_list_users"): SeasonalPage = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/anime/season/$year/$season?limit=$limit&offset=$offset&sort=$sort&nsfw=true&fields=${browseFields("anime")}") }
        val json = JSONObject(body)
        val arr = json.optJSONArray("data") ?: return@withContext SeasonalPage(emptyList(), false)
        val items = (0 until arr.length()).map { parseEntry("anime", arr.getJSONObject(it)) }
        SeasonalPage(items, json.optJSONObject("paging")?.has("next") == true)
    }

    // Personalized anime recommendations
    suspend fun animeSuggestions(limit: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/anime/suggestions?limit=$limit&nsfw=true&fields=${browseFields("anime")}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry("anime", arr.getJSONObject(it)) }
    }

    // Backfills English titles for
    // currently just MalGenreApi's genre/theme/demographic-filtered
    // own doc comment: that
    // read directly). Requests only
    // the API allows —
    // and runs against MAL's
    // mirror, so backfilling titles
    // Semaphore(3) genre-lookup throttle. Concurrency
    // ids already cached (from
    // without a network request;
    // per id (cached as
    // just keep showing the
    suspend fun englishTitles(kind: String, ids: List<Int>): Map<Int, String> = withContext(Dispatchers.IO) {
        val toFetch = ids.distinct().filterNot { EnglishTitleCache.map.containsKey(kind to it) }
        if (toFetch.isNotEmpty()) {
            // LibraryViewModel.resolveEnglishTitles now awaits this
            // genre-search results (previously it
            // on screen), so its
            // old Semaphore(5) took up
            // still comfortably under what
            // burst (this hits a
            // lookup above), and halves
            val gate = Semaphore(10)
            coroutineScope {
                toFetch.map { id ->
                    async {
                        gate.withPermit {
                            val title = runCatching {
                                val body = authorized { get("$API/$kind/$id?fields=alternative_titles{en}") }
                                JSONObject(body).optJSONObject("alternative_titles")?.safeTitle("en") ?: ""
                            }.getOrDefault("")
                            EnglishTitleCache.map[kind to id] = title
                        }
                    }
                }.awaitAll()
            }
        }
        ids.associateWith { EnglishTitleCache.map[kind to it] ?: "" }
    }

    // Fetch single title detail
    suspend fun detail(id: Int, type: MediaType): MediaItem = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        // Status distribution statistics
        val detailFields = fields(kind).replace("list_status", "my_list_status") + ",pictures,statistics"
        val body = authorized { get("$API/$kind/$id?fields=$detailFields") }
        val flat = JSONObject(body)
        val wrapped = JSONObject().put("node", flat)
        flat.optJSONObject("my_list_status")?.let { wrapped.put("list_status", it) }
        parseEntry(kind, wrapped)
    }

    // Website user recommendations
    suspend fun userRecommendations(id: Int, type: MediaType): List<RecommendedEntry> = withContext(Dispatchers.IO) {
        detail(id, type).recommended
    }

    // Forum board hierarchy
    suspend fun forumBoards(): List<ForumCategory> = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/forum/boards") }
        val categories = JSONObject(body).optJSONArray("categories") ?: return@withContext emptyList()
        (0 until categories.length()).map { i ->
            val cat = categories.getJSONObject(i)
            val boardsArr = cat.optJSONArray("boards")
            val boards = boardsArr?.let { arr -> (0 until arr.length()).map { parseForumBoard(arr.getJSONObject(it)) } } ?: emptyList()
            ForumCategory(title = cat.optString("title"), boards = boards)
        }
    }

    private fun parseForumBoard(b: JSONObject): ForumBoard {
        val subArr = b.optJSONArray("subboards")
        val subs = subArr?.let { arr -> (0 until arr.length()).map { i -> arr.getJSONObject(i).let { ForumSubboard(it.optInt("id"), it.optString("title")) } } } ?: emptyList()
        return ForumBoard(id = b.optInt("id"), title = b.optString("title"), description = b.optString("description"), subboards = subs)
    }

    private fun parseForumUser(o: JSONObject?) = ForumUser(o?.optInt("id") ?: 0, o?.optString("name") ?: "", o?.optString("forum_avator") ?: "")

    // Board topics or search
    suspend fun forumTopics(boardId: Int? = null, subboardId: Int? = null, query: String = "", limit: Int = 25, offset: Int = 0, withThumbnails: Boolean = false): ForumTopicsPage = withContext(Dispatchers.IO) {
        val params = buildString {
            append("limit=$limit&offset=$offset&sort=recent")
            boardId?.let { append("&board_id=$it") }
            subboardId?.let { append("&subboard_id=$it") }
            if (query.isNotBlank()) append("&q=${URLEncoder.encode(query, "UTF-8")}")
        }
        val body = authorized { get("$API/forum/topics?$params") }
        val json = JSONObject(body)
        val arr = json.optJSONArray("data") ?: return@withContext ForumTopicsPage(emptyList(), false)
        val items = (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            ForumTopic(
                id = t.optInt("id"), title = t.optString("title"), createdAt = t.optString("created_at"),
                author = parseForumUser(t.optJSONObject("created_by")), postCount = t.optInt("number_of_posts"),
                lastPostAt = t.optString("last_post_created_at"), lastPostAuthor = parseForumUser(t.optJSONObject("last_post_created_by")),
                isLocked = t.optBoolean("is_locked"),
            )
        }
        // Thumbnails aren't in the
        // /forum/topic/{id} fetch. Firing all
        // 10-item news row) piles
        // makes MAL's API noticeably
        // at once keeps this
        val thumbnailGate = Semaphore(4)
        val withCovers = if (!withThumbnails) items else items.map { topic ->
            async {
                if (topic.isLocked) topic
                else thumbnailGate.withPermit {
                    runCatching { forumTopic(topic.id, limit = 1) }.getOrNull()
                        ?.posts?.firstOrNull()?.body
                        ?.let { firstImageUrl(it) }
                        ?.let { topic.copy(imageUrl = it) } ?: topic
                }
            }
        }.awaitAll()
        ForumTopicsPage(withCovers, json.optJSONObject("paging")?.has("next") == true)
    }

    // Topic posts and poll
    suspend fun forumTopic(topicId: Int, limit: Int = 30, offset: Int = 0): ForumTopicDetail = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/forum/topic/$topicId?limit=$limit&offset=$offset") }
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: JSONObject()
        val postsArr = data.optJSONArray("posts")
        val posts = postsArr?.let { arr -> (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            ForumPost(id = p.optInt("id"), number = p.optInt("number"), createdAt = p.optString("created_at"), author = parseForumUser(p.optJSONObject("created_by")), body = p.optString("body"), signature = p.optString("signature"))
        } } ?: emptyList()
        val pollObj = data.optJSONObject("poll")
        val poll = pollObj?.let {
            val optsArr = it.optJSONArray("options")
            val opts = optsArr?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> ForumPollOption(o.optString("text"), o.optInt("votes")) } } } ?: emptyList()
            ForumPoll(question = it.optString("question"), closed = it.optBoolean("closed"), options = opts)
        }
        ForumTopicDetail(title = data.optString("title"), posts = posts, poll = poll, hasMore = json.optJSONObject("paging")?.has("next") == true)
    }

    // Latest news snapshots
    suspend fun newsSnapshots(limit: Int = 10): List<NewsSnapshot> = withContext(Dispatchers.IO) {
        // Cached after the first
        // full /forum/boards round trip
        val newsBoardId = newsBoardIdCache ?: forumBoards()
            .flatMap { it.boards }
            .firstOrNull { it.title.equals("News Discussion", ignoreCase = true) }
            ?.id?.also { newsBoardIdCache = it }
        ?: return@withContext emptyList()
        // Reuses forumTopics' own (now
        // running a second, separate
        forumTopics(boardId = newsBoardId, limit = limit + 6, withThumbnails = true).items
            .filterNot { it.isLocked }
            .mapNotNull { topic -> topic.imageUrl?.let { NewsSnapshot(topicId = topic.id, title = topic.title, imageUrl = it) } }
            .take(limit)
    }

    suspend fun update(item: MediaItem): Unit = withContext(Dispatchers.IO) {
        val endpoint = "$API/${if (item.type == MediaType.Anime) "anime" else "manga"}/${item.id}/my_list_status"
        val status = when (item.status) {
            WatchStatus.Watching -> "watching"; WatchStatus.Reading -> "reading"; WatchStatus.Completed -> "completed"
            WatchStatus.OnHold -> "on_hold"; WatchStatus.Dropped -> "dropped"; WatchStatus.Plan -> "plan_to_watch"
        }
        // Fix episode write key
        val progressField = if (item.type == MediaType.Anime) "num_watched_episodes" else "num_chapters_read"
        // Rewatch tracking by type
        val rewatchingField = if (item.type == MediaType.Anime) "is_rewatching" else "is_rereading"
        val timesRewatchedField = if (item.type == MediaType.Anime) "num_times_rewatched" else "num_times_reread"
        val fields = buildMap {
            put("status", status)
            put(progressField, item.progress.toString())
            put("score", item.myRating.toString())
            if (item.watchStartDate.isNotBlank()) put("start_date", item.watchStartDate)
            if (item.watchEndDate.isNotBlank()) put("finish_date", item.watchEndDate)
            put(rewatchingField, item.isRewatching.toString())
            put(timesRewatchedField, item.timesRewatched.toString())
            // MAL accepts tags as
            // website's own tag field
            put("tags", item.notes)
            put("comments", item.comments)
        }
        authorized { form(endpoint, fields, method = "PATCH") }
    }

    // Delete list entry
    suspend fun deleteEntry(item: MediaItem): Unit = withContext(Dispatchers.IO) {
        val endpoint = "$API/${if (item.type == MediaType.Anime) "anime" else "manga"}/${item.id}/my_list_status"
        authorized { delete(endpoint) }
    }

    // Shared fields query param
    // Browse endpoints need my_list_status
    private fun browseFields(kind: String) = fields(kind).replace("list_status", "my_list_status")
    private fun fields(kind: String): String {
        // list_status only returns its
        // unless the extra sub-fields
        // start/finish dates were being
        // MAL's per-entry tags field;
        val listStatus = if (kind == "anime") {
            "list_status{status,score,num_episodes_watched,is_rewatching,num_times_rewatched,updated_at,start_date,finish_date,tags,comments}"
        } else {
            "list_status{status,score,num_chapters_read,num_volumes_read,is_rereading,num_times_reread,updated_at,start_date,finish_date,tags,comments}"
        }
        // Related and theme fields
        val common = "$listStatus,genres,explicit_genres,themes,demographics,main_picture,synopsis,background,mean,rank,popularity,num_list_users," +
                "start_date,end_date,media_type,status,alternative_titles,nsfw," +
                "related_anime{node{id,title,main_picture},relation_type},related_manga{node{id,title,main_picture},relation_type}," +
                "recommendations{node{id,title,main_picture},num_recommendations}"
        val kindSpecific = if (kind == "anime") {
            "num_episodes,studios,source,rating,start_season,opening_themes,ending_themes,broadcast"
        } else {
            "num_chapters,num_volumes,authors{first_name,last_name},source"
        }
        return "$common,$kindSpecific"
    }

    private suspend fun searchKind(query: String, kind: String, offset: Int = 0): SearchPage = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(clampMalQuery(query), "UTF-8")
        // Small page size so
        // same as the Tenrai-filtered
        val body = authorized { get("$API/$kind?q=$encoded&limit=10&offset=$offset&nsfw=true&fields=${browseFields(kind)}") }
        val json = JSONObject(body)
        val arr = json.optJSONArray("data") ?: return@withContext SearchPage(emptyList(), false)
        val items = (0 until arr.length()).map { parseEntry(kind, arr.getJSONObject(it)) }
        SearchPage(items, json.optJSONObject("paging")?.has("next") == true)
    }

    // Compute current MAL season
    private fun currentSeason(): Pair<Int, String> {
        val cal = java.util.Calendar.getInstance()
        val season = when (cal.get(java.util.Calendar.MONTH) + 1) {
            in 1..3 -> "winter"; in 4..6 -> "spring"; in 7..9 -> "summer"; else -> "fall"
        }
        return cal.get(java.util.Calendar.YEAR) to season
    }

    private suspend fun fetchList(kind: String): List<MediaItem> {
        val body = authorized { get("$API/users/@me/${kind}list?limit=1000&nsfw=true&fields=${fields(kind)}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).map { parseEntry(kind, arr.getJSONObject(it)) }
    }

    // Parse node and status
    private fun parseEntry(kind: String, e: JSONObject): MediaItem {
        val n = e.getJSONObject("node")
        // Browse uses my_list_status
        val s = e.optJSONObject("list_status") ?: e.optJSONObject("my_list_status") ?: JSONObject()
        val status = when (s.optString("status")) {
            "watching" -> WatchStatus.Watching
            "reading" -> WatchStatus.Reading
            "completed" -> WatchStatus.Completed
            "on_hold" -> WatchStatus.OnHold
            "dropped" -> WatchStatus.Dropped
            else -> WatchStatus.Plan
        }
        // Themes and demographics data
        fun tagList(field: String) = n.optJSONArray(field)?.let { arr2 -> (0 until arr2.length()).map { arr2.getJSONObject(it).optString("name") } } ?: emptyList()
        // Merge explicit genres in
        val genresList = tagList("genres") + tagList("explicit_genres")
        val contentThemes = tagList("themes")
        val demographics = tagList("demographics")
        val picture = n.optJSONObject("main_picture")
        val allCreators = if (kind == "anime") {
            n.optJSONArray("studios")?.let { arr2 -> (0 until arr2.length()).mapNotNull { arr2.getJSONObject(it).optString("name").takeIf { s -> s.isNotBlank() } } } ?: emptyList()
        } else {
            n.optJSONArray("authors")?.let { arr2 ->
                (0 until arr2.length()).mapNotNull { i ->
                    val a = arr2.getJSONObject(i).optJSONObject("node")
                    listOfNotNull(a?.optString("first_name")?.takeIf { it.isNotBlank() }, a?.optString("last_name")?.takeIf { it.isNotBlank() })
                        .joinToString(" ").takeIf { it.isNotBlank() }
                }
            } ?: emptyList()
        }
        val creator = allCreators.firstOrNull() ?: ""
        val altTitleNode = n.optJSONObject("alternative_titles")
        val titleEnglish = altTitleNode?.safeTitle("en") ?: ""
        val japaneseTitle = altTitleNode?.safeTitle("ja")?.takeIf { it.isNotBlank() }
        val synonymsArr = altTitleNode?.optJSONArray("synonyms")
        val synonyms = listOfNotNull(japaneseTitle) +
                (synonymsArr?.let { arr2 -> (0 until arr2.length()).map { arr2.getString(it) } } ?: emptyList())
        val season = n.optJSONObject("start_season")?.optString("season")?.takeIf { it.isNotBlank() }?.let(::prettify) ?: ""
        val broadcastDay = n.optJSONObject("broadcast")?.optString("day_of_the_week")?.takeIf { it.isNotBlank() }?.let(::prettify) ?: ""
        // Broadcast time in JST
        val broadcastTime = n.optJSONObject("broadcast")?.optString("start_time")?.takeIf { it.isNotBlank() } ?: ""
        fun themeList(field: String) = n.optJSONArray(field)?.let { arr2 -> (0 until arr2.length()).map { arr2.getJSONObject(it).optString("text") } } ?: emptyList()
        fun relatedList(field: String, malType: String) = n.optJSONArray(field)?.let { arr2 ->
            (0 until arr2.length()).map { i ->
                val r = arr2.getJSONObject(i)
                val node = r.getJSONObject("node")
                val nodePicture = node.optJSONObject("main_picture")
                RelatedEntry(
                    relation = prettify(r.optString("relation_type")),
                    title = node.safeTitle(),
                    malId = node.optInt("id", 0),
                    malType = malType,
                    cover = nodePicture?.optString("large")?.takeIf { it.isNotBlank() } ?: nodePicture?.optString("medium") ?: "",
                )
            }
        } ?: emptyList()
        val related = relatedList("related_anime", "anime") + relatedList("related_manga", "manga")
        // Anime status breakdown
        val statusDistribution = if (kind == "anime") {
            n.optJSONObject("statistics")?.optJSONObject("status")?.let { s2 ->
                StatusDistribution(
                    watching = s2.optString("watching").toIntOrNull() ?: 0,
                    completed = s2.optString("completed").toIntOrNull() ?: 0,
                    onHold = s2.optString("on_hold").toIntOrNull() ?: 0,
                    dropped = s2.optString("dropped").toIntOrNull() ?: 0,
                    planToWatch = s2.optString("plan_to_watch").toIntOrNull() ?: 0,
                )
            } ?: StatusDistribution()
        } else StatusDistribution()
        val mainCover = picture?.optString("large")?.takeIf { it.isNotBlank() } ?: picture?.optString("medium") ?: ""
        // All title cover pictures
        val extraCovers = n.optJSONArray("pictures")?.let { arr2 ->
            (0 until arr2.length()).mapNotNull { i ->
                val p = arr2.getJSONObject(i)
                p.optString("large").takeIf { it.isNotBlank() } ?: p.optString("medium").takeIf { it.isNotBlank() }
            }
        } ?: emptyList()
        val covers = (listOf(mainCover) + extraCovers).filter { it.isNotBlank() }.distinct()
        // Same-type recommendations only
        val recommended = n.optJSONArray("recommendations")?.let { arr2 ->
            (0 until arr2.length()).mapNotNull { i ->
                val r = arr2.getJSONObject(i)
                val node = r.optJSONObject("node") ?: return@mapNotNull null
                val nodePicture = node.optJSONObject("main_picture")
                RecommendedEntry(
                    malId = node.optInt("id", 0),
                    title = node.safeTitle(),
                    cover = nodePicture?.optString("large")?.takeIf { it.isNotBlank() } ?: nodePicture?.optString("medium") ?: "",
                    votes = r.optInt("num_recommendations", 0),
                    malType = kind,
                )
            }.sortedByDescending { it.votes }
        } ?: emptyList()
        return MediaItem(
            id = n.getInt("id").toString(),
            title = n.safeTitle().takeIf { it.isNotBlank() }
                ?: titleEnglish.takeIf { it.isNotBlank() }
                ?: synonyms.firstOrNull()
                ?: "Untitled",
            type = if (kind == "anime") MediaType.Anime else MediaType.Manga,
            status = status,
            progress = if (kind == "anime") s.optInt("num_episodes_watched") else s.optInt("num_chapters_read"),
            total = if (kind == "anime") n.optInt("num_episodes") else n.optInt("num_chapters"),
            genre = genresList.firstOrNull() ?: "",
            genres = genresList,
            contentThemes = contentThemes,
            demographics = demographics,
            cover = mainCover,
            color = 0xFFB7C3F5,
            synopsis = n.optString("synopsis"),
            background = n.optString("background"),
            score = n.optDouble("mean", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            rank = n.optInt("rank", 0),
            popularity = n.optInt("popularity", 0),
            listUsers = n.optInt("num_list_users", 0),
            creator = creator,
            allCreators = allCreators.joinToString(", "),
            startDate = n.optString("start_date").take(4),
            season = season,
            format = prettifyFormat(n.optString("media_type")),
            airStatus = prettify(n.optString("status")),
            source = prettifySource(n.optString("source")),
            rating = prettifyRating(n.optString("rating")),
            volumes = n.optInt("num_volumes", 0),
            titleEnglish = titleEnglish,
            startDateFull = n.optString("start_date"),
            endDateFull = n.optString("end_date"),
            synonyms = synonyms,
            openingThemes = if (kind == "anime") themeList("opening_themes") else emptyList(),
            endingThemes = if (kind == "anime") themeList("ending_themes") else emptyList(),
            related = related,
            recommended = recommended,
            statusDistribution = statusDistribution,
            myRating = s.optInt("score", 0),
            watchStartDate = s.optString("start_date"),
            watchEndDate = s.optString("finish_date"),
            // "tags" comes back as
            // to the same comma-separated
            notes = s.optJSONArray("tags")?.let { arr2 -> (0 until arr2.length()).map { arr2.getString(it) } }?.joinToString(", ") ?: "",
            comments = s.optString("comments"),
            isRewatching = if (kind == "anime") s.optBoolean("is_rewatching") else s.optBoolean("is_rereading"),
            timesRewatched = if (kind == "anime") s.optInt("num_times_rewatched") else s.optInt("num_times_reread"),
            updatedAt = s.optString("updated_at"),
            broadcastDay = broadcastDay,
            broadcastTime = broadcastTime,
            nsfw = n.optString("nsfw", "white"),
            inUserList = e.has("list_status") || e.has("my_list_status"),
            covers = covers,
        )
    }

    // Retry once if expired
    private suspend fun <T> authorized(block: () -> T): T = try {
        block()
    } catch (_: AuthExpired) {
        refreshToken()
        block()
    }

    private suspend fun refreshToken() = withContext(Dispatchers.IO) {
        val refresh = requireNotNull(prefs.getString("refresh_token", null)) { "Not signed in" }
        val response = form(
            "https://myanimelist.net/v1/oauth2/token",
            mapOf("client_id" to BuildConfig.MAL_CLIENT_ID, "grant_type" to "refresh_token", "refresh_token" to refresh),
            auth = false
        )
        storeTokens(JSONObject(response))
    }

    private fun storeTokens(json: JSONObject) {
        prefs.edit()
            .putString("access_token", json.getString("access_token"))
            .putString("refresh_token", json.getString("refresh_token"))
            .apply()
    }

    private fun randomToken(bytes: Int) = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${prefs.getString("access_token", "")}")
            .addHeader("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 401) throw AuthExpired()
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("MAL request failed (${resp.code}): ${text.take(300)}")
            return text
        }
    }

    private fun delete(url: String): String {
        val request = Request.Builder().url(url).delete()
            .addHeader("Authorization", "Bearer ${prefs.getString("access_token", "")}")
            .addHeader("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 401) throw AuthExpired()
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("MAL request failed (${resp.code}): ${text.take(300)}")
            return text
        }
    }

    private fun form(url: String, values: Map<String, String>, method: String = "POST", auth: Boolean = true): String {
        val body = FormBody.Builder().apply { values.forEach { (k, v) -> add(k, v) } }.build()
        val builder = Request.Builder().url(url).method(method, body)
        if (auth) builder.addHeader("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
        if (auth && signedIn) builder.addHeader("Authorization", "Bearer ${prefs.getString("access_token", "")}")
        client.newCall(builder.build()).execute().use { resp ->
            if (auth && resp.code == 401) throw AuthExpired()
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("MAL request failed (${resp.code}): ${text.take(300)}")
            return text
        }
    }
}