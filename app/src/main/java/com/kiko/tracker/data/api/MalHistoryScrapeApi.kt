package com.kiko.tracker.data.api

import android.content.Context
import com.kiko.tracker.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/**
 * One row on myanimelist.net/history/{username} — a single episode/chapter
 * progress update, newest first. Unlike an `items` entry (one row per
 * title, current progress only) this is one row per *update*, so the same
 * title can appear many times with different [progressLabel]s.
 *
 * The history page itself carries no cover art or numeric id column beyond
 * the title link's `?id=`, so [mediaId] is kept as a plain String (matching
 * MediaItem.id's own shape) for callers to join against `items` for a
 * cover/tint — see HomeScreen's `coverColor()`. [dayLabel] is MAL's own
 * group heading for the row ("Tuesday", "Last Week", ...) — kept per-row
 * rather than hoisted into a separate group key so callers can either
 * respect it (HistoryScreen, grouped the same way MAL groups it) or ignore
 * it entirely (Home's flat "Last Updated List", which only wants
 * [timeLabel] as a subtitle).
 */
data class MalHistoryEntry(
    val mediaId: String,
    val type: MediaType,
    val title: String,
    // "ep. 6" or "chap. 3", already MAL-formatted
    val progressLabel: String,
    // "Sep 8, 11:37 AM"
    val timeLabel: String,
    // "Tuesday", "Last Week", ... — MAL's own section heading for this row
    val dayLabel: String,
)

/**
 * Scrapes myanimelist.net/history/{username} — the user's combined
 * anime+manga activity feed (episode/chapter-level, unlike anything the
 * official API or Jikan expose). Same request/parse shape as
 * MalProfileScrapeApi, and needs the same logged-in session cookie rather
 * than just a desktop user-agent — MAL bounces to login.php without one.
 */
class MalHistoryScrapeApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    private fun fetchDocument(username: String): Document {
        val cookie = session.get() ?: throw MalSessionExpired()
        val request = Request.Builder()
            .url("https://myanimelist.net/history/$username")
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .build()

        client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            // An expired/invalid cookie gets redirected to the login form.
            if (finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")) {
                throw MalSessionExpired()
            }
            if (!resp.isSuccessful) throw IOException("MAL history scrape failed (${resp.code}): $username")
            return Jsoup.parse(body, finalUrl)
        }
    }

    /**
     * Fetches [username]'s full history table, newest first (MAL's own
     * order — no re-sorting needed). The table has no <tbody>/per-day
     * <table> nesting to key off, just a flat run of <tr>s: a "day header"
     * row (div.normal_header) every so often, a blank spacer row between
     * days, and otherwise one row per update. Selecting by descendant
     * (`table tr`, not `table > tr`) sidesteps any implicit-tbody
     * differences between MAL's raw markup and how Jsoup's parser
     * normalizes it.
     */
    suspend fun history(username: String): List<MalHistoryEntry> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(username)
        val rows = doc.select("div.history_content_wrapper table tr")

        var currentDay = ""
        val entries = mutableListOf<MalHistoryEntry>()
        for (row in rows) {
            val header = row.selectFirst("div.normal_header")
            if (header != null) {
                // ownText() only — the "(N)" count sits in a nested
                // <span><small>, which ownText() correctly excludes.
                currentDay = header.ownText().trim()
                continue
            }
            val cells = row.select("td.borderClass")
            val firstCell = cells.getOrNull(0) ?: continue // spacer row
            val link = firstCell.selectFirst("a[href~=\\.php\\?id=]") ?: continue
            val href = link.attr("href")
            val isAnime = href.contains("/anime.php")
            val id = Regex("id=(\\d+)").find(href)?.groupValues?.get(1) ?: continue
            val title = link.text().trim()
            // firstCell.text() is "<title> ep./chap. <N>" all run together
            // (Jsoup joins element text with spaces) — stripping the
            // already-known title prefix is simpler and more robust than
            // separately pulling the unit word and the <strong> number
            // back apart.
            val progressLabel = firstCell.text().removePrefix(title).trim()
            // Second cell is "<a class='lightbox'>Edit</a>&nbsp;Sep 8, 11:37 AM"
            val timeLabel = cells.getOrNull(1)?.text()?.substringAfter("Edit")?.trim().orEmpty()
            if (progressLabel.isBlank() || timeLabel.isBlank()) continue

            entries += MalHistoryEntry(
                mediaId = id,
                type = if (isAnime) MediaType.Anime else MediaType.Manga,
                title = title,
                progressLabel = progressLabel,
                timeLabel = timeLabel,
                dayLabel = currentDay,
            )
        }
        entries
    }
}