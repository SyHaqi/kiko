package com.kiko.tracker

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom

private const val API = "https://api.myanimelist.net/v2"
const val MAL_REDIRECT = "com.kiko.tracker://oauth/callback"

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
// "4_koma_manga" would otherwise come out of prettify() as "4 Koma Manga" (plain underscore-to-space
// split), which never matches Discover's "4-Koma Manga" filter chip — that facet would silently
// return zero results no matter what else was selected alongside it.
private fun prettifySource(raw: String) = when (raw.lowercase()) {
    "4_koma_manga" -> "4-Koma Manga"
    else -> prettify(raw)
}

/** Thrown internally when the access token is rejected so [authorized] can refresh and retry once. */
private class AuthExpired : IOException()

// One entry from a title's user-submitted "Recommendations" (the tab MAL's website shows below a
// title's own page, e.g. myanimelist.net/anime/{id}/{slug}/userrecs) — other titles that people who
// watched/read this one suggested, ranked by how many people recommended each. `votes` is how many
// users submitted that particular recommendation.
data class RecommendedEntry(val malId: Int, val title: String, val cover: String, val votes: Int, val malType: String = "anime")

// One page of a season's anime chart, plus whether MAL indicated another page follows it.
data class SeasonalPage(val items: List<MediaItem>, val hasMore: Boolean)

// One subboard nested under a forum board (e.g. "Anime DB" under "DB Modification Requests") —
// topics can be scoped to just a subboard the same way they can to a whole board.
data class ForumSubboard(val id: Int, val title: String)

// One forum board, optionally containing subboards.
data class ForumBoard(val id: Int, val title: String, val description: String = "", val subboards: List<ForumSubboard> = emptyList())

// A top-level grouping of boards — e.g. "MyAnimeList", "Anime & Manga", "General" — mirrors the
// section headers on myanimelist.net/forum.
data class ForumCategory(val title: String, val boards: List<ForumBoard>)

// Whoever created a topic or post. MAL's own field for the avatar is spelled "forum_avator" (not a
// typo on this end — that's the actual API field name).
data class ForumUser(val id: Int = 0, val name: String = "", val avatar: String = "")

// One row in a board's topic list, or in a cross-board search result list — same shape either way.
data class ForumTopic(
    val id: Int, val title: String, val createdAt: String, val author: ForumUser,
    val postCount: Int, val lastPostAt: String, val lastPostAuthor: ForumUser, val isLocked: Boolean = false,
)

// One page of a topic listing, plus whether MAL indicated another page follows it — same idea as SeasonalPage.
data class ForumTopicsPage(val items: List<ForumTopic>, val hasMore: Boolean)

// A single reply within a topic — the topic's own original post is just post #1.
data class ForumPost(val id: Int, val number: Int, val createdAt: String, val author: ForumUser, val body: String, val signature: String = "")

data class ForumPollOption(val text: String, val votes: Int)
data class ForumPoll(val question: String, val closed: Boolean, val options: List<ForumPollOption>)

// A topic's posts (paginated — MAL caps this well under a long thread's full length) plus its
// optional poll. The poll only ever comes back on this endpoint, never on the plain topics listing.
data class ForumTopicDetail(val title: String, val posts: List<ForumPost>, val poll: ForumPoll?, val hasMore: Boolean)

// The signed-in person's own MAL account details plus their anime list statistics — MAL's API only
// exposes aggregate stats for anime, not manga, so the manga side has no equivalent numbers to show.
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
    // MAL's `my_list_status` update endpoint requires PATCH. java.net.HttpURLConnection refuses
    // to send that method at all (its built-in whitelist has no PATCH, only GET/POST/PUT/DELETE/...),
    // which is why saves were silently failing to reach MAL — OkHttp sends whatever method you ask for.
    private val client = OkHttpClient()

    fun authUrl(): String {
        val verifier = randomToken(48)
        val state = randomToken(18)
        prefs.edit().putString("verifier", verifier).putString("state", state).apply()
        // MAL's OAuth only supports the "plain" PKCE method, so the challenge is the verifier itself.
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

    // The signed-in person's own MAL profile — avatar, account details, and anime list stats — for
    // the Profile tab. `anime_statistics` is the one optional field MAL will actually expand here.
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

    // Searches MAL's full anime/manga database (not just the signed-in user's list) by title.
    // type == null searches both anime and manga and merges the results (the Discover "All" filter).
    suspend fun search(query: String, type: MediaType?): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        if (type == null) return@withContext listOf(searchKind(query, "anime"), searchKind(query, "manga")).flatten()
        searchKind(query, if (type == MediaType.Anime) "anime" else "manga")
    }

    // Anime premiering in the current MAL season — the closest honest signal MAL's API exposes for
    // "recently added" (the API has no direct added-to-database-date field or endpoint).
    suspend fun seasonalAnime(limit: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        val (year, season) = currentSeason()
        val body = authorized { get("$API/anime/season/$year/$season?limit=$limit&nsfw=true&fields=${fields("anime")}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry("anime", arr.getJSONObject(it)) }
    }

    suspend fun upcomingAnime(limit: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/anime/ranking?ranking_type=upcoming&limit=$limit&nsfw=true&fields=${fields("anime")}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry("anime", arr.getJSONObject(it)) }
    }

    // Anime or manga ranking chart. rankingType is a MAL ranking_type value — "all" (score), "bypopularity",
    // "favorite", or (anime only) "upcoming" — powers the Home "Ranking" screen.
    suspend fun ranking(type: MediaType, rankingType: String, limit: Int = 25): List<MediaItem> = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val body = authorized { get("$API/$kind/ranking?ranking_type=$rankingType&limit=$limit&nsfw=true&fields=${fields(kind)}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry(kind, arr.getJSONObject(it)) }
    }

    // Anime airing/announced in an arbitrary season/year, optionally sorted by member count or start
    // date — powers the Home "Seasonal Chart" screen (the year/season the person picks, not just now).
    // MAL's season chart mixes anime that premiered that season with ones still airing from an earlier
    // season (the website itself splits these into "New" and "Continuing" sections) — this endpoint
    // doesn't distinguish them itself, so the caller filters by each item's own start_season when it
    // only wants premieres. `offset` pages through the chart (MAL caps `limit` well under its full
    // size), and `hasMore` reflects whether MAL's own paging info says there's a next page.
    suspend fun seasonalAnime(year: Int, season: String, limit: Int = 25, offset: Int = 0, sort: String = "anime_num_list_users"): SeasonalPage = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/anime/season/$year/$season?limit=$limit&offset=$offset&sort=$sort&nsfw=true&fields=${fields("anime")}") }
        val json = JSONObject(body)
        val arr = json.optJSONArray("data") ?: return@withContext SeasonalPage(emptyList(), false)
        val items = (0 until arr.length()).map { parseEntry("anime", arr.getJSONObject(it)) }
        SeasonalPage(items, json.optJSONObject("paging")?.has("next") == true)
    }

    // Personalized "because you're tracking X" anime suggestions for the signed-in user — powers the
    // Home "Recommendations" row. MAL has no equivalent manga endpoint.
    suspend fun animeSuggestions(limit: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        val body = authorized { get("$API/anime/suggestions?limit=$limit&nsfw=true&fields=${fields("anime")}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry("anime", arr.getJSONObject(it)) }
    }

    // Fetches full details for a single title by id — used to open a Related-row entry inside Kiko's
    // own Detail screen instead of sending the person out to the MAL website. The details endpoint
    // returns the title's fields flat (not wrapped in "node") and names the viewer's own list entry
    // "my_list_status" rather than "list_status", so both are adapted here to reuse parseEntry as-is.
    suspend fun detail(id: Int, type: MediaType): MediaItem = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        // "statistics" (anime only — see parseEntry) is the official status-distribution field
        // powering the Detail screen's bottom-of-page "Status distribution" section; same as
        // "pictures", the bulk list/ranking/season/search endpoints don't request it.
        val detailFields = fields(kind).replace("list_status", "my_list_status") + ",pictures,statistics"
        val body = authorized { get("$API/$kind/$id?fields=$detailFields") }
        val flat = JSONObject(body)
        val wrapped = JSONObject().put("node", flat)
        flat.optJSONObject("my_list_status")?.let { wrapped.put("list_status", it) }
        parseEntry(kind, wrapped)
    }

    // The website's user-submitted "Recommendations" for a title. MAL's official API does expose
    // this on the single-title endpoint via the `recommendations` field (see `fields()` and
    // `parseEntry` above) — it's just not returned by the bulk list/ranking/season/suggestions
    // endpoints, the same gap `related`/opening/ending themes have, so this reuses `detail()`
    // to backfill it the same way `backfillRelated`/`backfillThemes` do in the view model.
    suspend fun userRecommendations(id: Int, type: MediaType): List<RecommendedEntry> = withContext(Dispatchers.IO) {
        detail(id, type).recommended
    }

    // The forum's full board hierarchy (categories -> boards -> subboards) — powers the Forums tab's
    // landing page. MAL doesn't page this; it's a small, mostly-static tree, unlike topics/posts below.
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

    // A board or subboard's topic list, or a cross-board keyword search when query is non-blank and
    // boardId/subboardId are both null — MAL's /forum/topics endpoint handles both with the same params.
    suspend fun forumTopics(boardId: Int? = null, subboardId: Int? = null, query: String = "", limit: Int = 25, offset: Int = 0): ForumTopicsPage = withContext(Dispatchers.IO) {
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
        ForumTopicsPage(items, json.optJSONObject("paging")?.has("next") == true)
    }

    // A topic's posts (paginated) plus its poll, if it has one.
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

    suspend fun update(item: MediaItem): Unit = withContext(Dispatchers.IO) {
        val endpoint = "$API/${if (item.type == MediaType.Anime) "anime" else "manga"}/${item.id}/my_list_status"
        val status = when (item.status) {
            WatchStatus.Watching -> "watching"; WatchStatus.Reading -> "reading"; WatchStatus.Completed -> "completed"
            WatchStatus.OnHold -> "on_hold"; WatchStatus.Dropped -> "dropped"; WatchStatus.Plan -> "plan_to_watch"
        }
        // MAL uses a different key for this field on write than it does on read: the list-fetch
        // response calls it "num_episodes_watched" (see parseEntry above), but the update endpoint
        // expects "num_watched_episodes" — sending the read-side name here meant this field was
        // silently dropped on every save.
        val progressField = if (item.type == MediaType.Anime) "num_watched_episodes" else "num_chapters_read"
        // MAL splits rewatch tracking by media type too: "is_rewatching"/"num_times_rewatched" for
        // anime, "is_rereading"/"num_times_reread" for manga — same idea as progressField above.
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
        }
        authorized { form(endpoint, fields, method = "PATCH") }
    }

    // Removes the entry from the person's MAL list entirely (MAL's `my_list_status` DELETE endpoint) —
    // this is a real delete, not a status change, so it's called from the Edit sheet's Delete button
    // rather than folded into update() above.
    suspend fun deleteEntry(item: MediaItem): Unit = withContext(Dispatchers.IO) {
        val endpoint = "$API/${if (item.type == MediaType.Anime) "anime" else "manga"}/${item.id}/my_list_status"
        authorized { delete(endpoint) }
    }

    // The `fields` query param shared by both "my list" fetches and full-database search.
    private fun fields(kind: String): String {
        // related_anime/related_manga are list-of-object fields — like authors below, MAL only
        // returns usable data (title, picture) for these if the node sub-fields are spelled out
        // explicitly; requesting the bare field name silently comes back empty.
        // themes/demographics (e.g. "Isekai", "Shounen") are finer-grained than genres alone and
        // are what the Detail screen's "Recommended" row scores similarity on.
        val common = "list_status,genres,explicit_genres,themes,demographics,main_picture,synopsis,background,mean,rank,popularity,num_list_users," +
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

    private suspend fun searchKind(query: String, kind: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = authorized { get("$API/$kind?q=$encoded&limit=25&nsfw=true&fields=${fields(kind)}") }
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseEntry(kind, arr.getJSONObject(it)) }
    }

    // The current MAL "season" (winter/spring/summer/fall) by today's date, for the season-anime endpoint.
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

    // Parses one {node, list_status} entry — the shape returned by both the user-list and search endpoints.
    // list_status is present (with the user's own progress/score/dates) when signed in and already tracking
    // the title; absent for a title found via search that isn't on the user's list yet.
    private fun parseEntry(kind: String, e: JSONObject): MediaItem {
        val n = e.getJSONObject("node")
        val s = e.optJSONObject("list_status") ?: JSONObject()
        val status = when (s.optString("status")) {
            "watching" -> WatchStatus.Watching
            "reading" -> WatchStatus.Reading
            "completed" -> WatchStatus.Completed
            "on_hold" -> WatchStatus.OnHold
            "dropped" -> WatchStatus.Dropped
            else -> WatchStatus.Plan
        }
        // MAL's finer-grained categorization below genre level — e.g. "Isekai" or "Time Travel" as
        // a theme, "Shounen" or "Seinen" as a demographic — used only to score Recommended-row matches.
        fun tagList(field: String) = n.optJSONArray(field)?.let { arr2 -> (0 until arr2.length()).map { arr2.getJSONObject(it).optString("name") } } ?: emptyList()
        // "explicit_genres" (Ecchi/Erotica/Hentai) is a separate array on MAL's API from plain
        // "genres" — folded together here since the app treats them as one genre list everywhere
        // else (Detail screen's chips, isAdultContent(), the Discover genre filter).
        val genresList = tagList("genres") + tagList("explicit_genres")
        val contentThemes = tagList("themes")
        val demographics = tagList("demographics")
        val picture = n.optJSONObject("main_picture")
        val creator = if (kind == "anime") {
            n.optJSONArray("studios")?.optJSONObject(0)?.optString("name") ?: ""
        } else {
            val a = n.optJSONArray("authors")?.optJSONObject(0)?.optJSONObject("node")
            listOfNotNull(a?.optString("first_name")?.takeIf { it.isNotBlank() }, a?.optString("last_name")?.takeIf { it.isNotBlank() }).joinToString(" ")
        }
        val altTitleNode = n.optJSONObject("alternative_titles")
        val titleEnglish = altTitleNode?.optString("en") ?: ""
        val japaneseTitle = altTitleNode?.optString("ja")?.takeIf { it.isNotBlank() }
        val synonymsArr = altTitleNode?.optJSONArray("synonyms")
        val synonyms = listOfNotNull(japaneseTitle) +
                (synonymsArr?.let { arr2 -> (0 until arr2.length()).map { arr2.getString(it) } } ?: emptyList())
        val season = n.optJSONObject("start_season")?.optString("season")?.takeIf { it.isNotBlank() }?.let(::prettify) ?: ""
        val broadcastDay = n.optJSONObject("broadcast")?.optString("day_of_the_week")?.takeIf { it.isNotBlank() }?.let(::prettify) ?: ""
        // "HH:mm" in JST, as MAL reports it — converted to the device's local day/time by
        // MediaItem.localBroadcast() wherever it's shown (Home's "Today's release", the Schedule screen).
        val broadcastTime = n.optJSONObject("broadcast")?.optString("start_time")?.takeIf { it.isNotBlank() } ?: ""
        fun themeList(field: String) = n.optJSONArray(field)?.let { arr2 -> (0 until arr2.length()).map { arr2.getJSONObject(it).optString("text") } } ?: emptyList()
        fun relatedList(field: String, malType: String) = n.optJSONArray(field)?.let { arr2 ->
            (0 until arr2.length()).map { i ->
                val r = arr2.getJSONObject(i)
                val node = r.getJSONObject("node")
                val nodePicture = node.optJSONObject("main_picture")
                RelatedEntry(
                    relation = prettify(r.optString("relation_type")),
                    title = node.getString("title"),
                    malId = node.optInt("id", 0),
                    malType = malType,
                    cover = nodePicture?.optString("large")?.takeIf { it.isNotBlank() } ?: nodePicture?.optString("medium") ?: "",
                )
            }
        } ?: emptyList()
        val related = relatedList("related_anime", "anime") + relatedList("related_manga", "manga")
        // Community status breakdown across every member tracking this anime — only present when
        // "statistics" was requested (detail()'s single-title fetch, not the bulk list/ranking/
        // season/search endpoints) and only for anime (MAL doesn't expose this for manga).
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
        // Only present when "pictures" was requested (the single-title detail() fetch, not the bulk
        // list/ranking/season endpoints) — every other cover MAL has on file for this title, so the
        // Detail screen's fullscreen viewer can be swiped through instead of showing just the one.
        val extraCovers = n.optJSONArray("pictures")?.let { arr2 ->
            (0 until arr2.length()).mapNotNull { i ->
                val p = arr2.getJSONObject(i)
                p.optString("large").takeIf { it.isNotBlank() } ?: p.optString("medium").takeIf { it.isNotBlank() }
            }
        } ?: emptyList()
        val covers = (listOf(mainCover) + extraCovers).filter { it.isNotBlank() }.distinct()
        // Recommendations are always same-media-type on MAL (an anime's recs are other anime, a
        // manga's are other manga) — unlike related_anime/related_manga there's no cross-type array
        // to merge, so `kind` (this entry's own type) is used directly as malType below.
        val recommended = n.optJSONArray("recommendations")?.let { arr2 ->
            (0 until arr2.length()).mapNotNull { i ->
                val r = arr2.getJSONObject(i)
                val node = r.optJSONObject("node") ?: return@mapNotNull null
                val nodePicture = node.optJSONObject("main_picture")
                RecommendedEntry(
                    malId = node.optInt("id", 0),
                    title = node.optString("title"),
                    cover = nodePicture?.optString("large")?.takeIf { it.isNotBlank() } ?: nodePicture?.optString("medium") ?: "",
                    votes = r.optInt("num_recommendations", 0),
                    malType = kind,
                )
            }.sortedByDescending { it.votes }
        } ?: emptyList()
        return MediaItem(
            id = n.getInt("id").toString(),
            title = n.getString("title"),
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
            isRewatching = if (kind == "anime") s.optBoolean("is_rewatching") else s.optBoolean("is_rereading"),
            timesRewatched = if (kind == "anime") s.optInt("num_times_rewatched") else s.optInt("num_times_reread"),
            updatedAt = s.optString("updated_at"),
            broadcastDay = broadcastDay,
            broadcastTime = broadcastTime,
            nsfw = n.optString("nsfw", "white"),
            inUserList = e.has("list_status"),
            covers = covers,
        )
    }

    // Runs an authorized call; if the access token has expired, refreshes it once and retries.
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