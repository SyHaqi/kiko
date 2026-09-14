package com.kiko.tracker.data.api

import android.content.Context
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

/**
 * One row of another MAL member's anime/manga list — enough to render a
 * read-only card (poster, title, progress, score) and open the title's own
 * DetailScreen via [malId]/[type], same as a favorite entry does
 * (see openFavoriteTitle in Navigation.kt).
 */
data class MalUserListEntry(
    val malId: Int,
    val title: String,
    val cover: String,
    val type: MediaType,
    val status: WatchStatus,
    val progress: Int,
    val total: Int,
    // 0..10, MAL's own scale — 0 means "no score given" (rendered as "-").
    val score: Int,
)

/**
 * Scrapes myanimelist.net/animelist|mangalist/{username} — not the official
 * list API (that only ever exposes the *signed-in* user's list), so this is
 * the only way to show another member's list in-app. Same request shape as
 * MalProfileScrapeApi/MalHistoryScrapeApi (logged-in cookie required — MAL
 * bounces an anonymous request to login.php the same way).
 *
 * One request per real per-item status (Watching/Reading, Completed, On
 * Hold, Dropped, Plan to Watch — MAL's codes 1/2/3/4/6), fired concurrently
 * and merged, rather than a single `status=7` ("All") request. That single
 * request looks like it should be enough — MAL's own "All Anime/Manga" view
 * visually groups every status into its own section — but the raw HTML
 * behind it is just ONE `table.list-table`/`data-items` blob for the "All"
 * view, capped at MAL's usual ~300-row page size same as any other single
 * status page. For anyone with a bigger list than that, whichever statuses
 * don't fit in that first page — commonly everything except Watching,
 * since MAL's default sort surfaces in-progress titles first — never make
 * it into the scraped result and silently come back with 0 entries. Hitting
 * each status's own URL sidesteps that: every one of those five pages is
 * its own separately-paginated ~300-row cap, so nothing gets crowded out by
 * another status's titles. Within a single status, [fetchStatus] then pages
 * through that status's own `&offset=` param (same one MAL's infinite
 * scroll uses) until it runs out, so a status that's itself bigger than the
 * ~300-row page — a long-running member's Completed list, say — isn't
 * truncated either.
 */
class MalUserListScrapeApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    /** MAL's own per-item status codes (confirmed against animelist/mangalist
     *  URLs) — 5 and 7 aren't real item statuses (7 is MAL's "All" tab, see
     *  the class doc above for why that one's not used here). Code 1 means
     *  "in progress" for either list, but the app's WatchStatus enum splits
     *  that into Watching (anime) vs Reading (manga) — same distinction
     *  FriendProfileScreen's status tabs already make. */
    private val statusCodes = listOf(1, 2, 3, 4, 6)

    private fun statusFromCode(code: Int, type: MediaType): WatchStatus? = when (code) {
        1 -> if (type == MediaType.Anime) WatchStatus.Watching else WatchStatus.Reading
        2 -> WatchStatus.Completed
        3 -> WatchStatus.OnHold
        4 -> WatchStatus.Dropped
        6 -> WatchStatus.Plan
        else -> null
    }

    suspend fun list(username: String, type: MediaType): List<MalUserListEntry> = withContext(Dispatchers.IO) {
        val cookie = session.get() ?: throw MalSessionExpired()
        val kind = if (type == MediaType.Anime) "animelist" else "mangalist"

        coroutineScope {
            statusCodes.map { code ->
                async { fetchStatus(kind, username, code, cookie, type) }
            }.awaitAll().flatten()
        }
    }

    private fun fetchStatus(kind: String, username: String, statusCode: Int, cookie: String, type: MediaType): List<MalUserListEntry> {
        // MAL's list-table page caps at ~300 rows and expects the same
        // infinite-scroll &offset= param the site's own JS uses to load
        // further pages — fetching status=$statusCode alone only ever
        // returned that first page, silently truncating any status with
        // more than ~300 titles in it (an active member's Completed list
        // clears that easily). Keep paging with an increasing offset until
        // a page comes back with nothing, which is MAL's own signal that
        // there's no more of this status left to fetch.
        val entries = mutableListOf<MalUserListEntry>()
        var offset = 0
        while (true) {
            val page = fetchPage(kind, username, statusCode, offset, cookie, type)
            if (page.isEmpty()) break
            entries += page
            offset += page.size
            // Safety valve so a server response we don't expect (e.g. the
            // same page repeating) can't spin this into an infinite loop —
            // 20,000 titles in one status is far beyond any real MAL list.
            if (offset > 20_000) break
        }
        return entries
    }

    private fun fetchPage(kind: String, username: String, statusCode: Int, offset: Int, cookie: String, type: MediaType): List<MalUserListEntry> {
        val url = "https://myanimelist.net/$kind/$username?status=$statusCode" + (if (offset > 0) "&offset=$offset" else "")
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .build()

        val body = client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val html = resp.body?.string().orEmpty()
            if (finalUrl.contains("login.php") || html.contains("id=\"loginForm\"")) throw MalSessionExpired()
            if (!resp.isSuccessful) throw IOException("MAL $kind scrape failed (${resp.code}): $username")
            html
        }

        // A single-status page renders exactly one table for that status
        // (empty statuses, or an offset past the end of one, render none at
        // all) — no per-group merging needed here the way the old single
        // `status=7` request required.
        val doc = Jsoup.parse(body, request.url.toString())
        val table = doc.selectFirst("table.list-table") ?: return emptyList()
        val raw = table.attr("data-items")
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { i -> parseEntry(array.getJSONObject(i), type) }
    }

    private fun parseEntry(o: JSONObject, type: MediaType): MalUserListEntry? {
        val status = statusFromCode(o.optInt("status"), type) ?: return null
        return if (type == MediaType.Anime) {
            val id = o.optInt("anime_id")
            if (id == 0) return null
            MalUserListEntry(
                malId = id,
                title = o.optString("anime_title_eng").ifBlank { o.optString("anime_title") },
                cover = o.optString("anime_image_path"),
                type = type,
                status = status,
                progress = o.optInt("num_watched_episodes"),
                total = o.optInt("anime_num_episodes"),
                score = o.optInt("score"),
            )
        } else {
            val id = o.optInt("manga_id")
            if (id == 0) return null
            MalUserListEntry(
                malId = id,
                title = o.optString("manga_english").ifBlank { o.optString("manga_title") },
                cover = o.optString("manga_image_path"),
                type = type,
                status = status,
                progress = o.optInt("num_read_chapters"),
                total = o.optInt("manga_num_chapters"),
                score = o.optInt("score"),
            )
        }
    }
}