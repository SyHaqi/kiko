package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/** One row of MAL's episode list. [topicId] is the episode's forum discussion (0 when MAL hasn't made one yet). */
data class EpisodeEntry(
    val number: Int, val title: String, val altTitle: String, val aired: String,
    val pollAverage: Double, val replies: Int, val topicId: Int,
)
data class EpisodesPage(val items: List<EpisodeEntry>, val hasMore: Boolean)

/** The detail view of a single episode. [watchUrl] is the provider link MAL shows when the video can't be embedded. */
data class EpisodeDetail(val title: String, val altTitle: String, val duration: String, val aired: String, val synopsis: String, val watchUrl: String)

/**
 * MAL's per-anime episode pages (public, no login):
 *  - list   : /anime/ID/slug/episode?offset=N, 100 rows per page, tr.episode-list-data with td.episode-number / .episode-title (a + span.di-ib) /
 *             .episode-aired / .episode-poll (data-raw average) / .episode-forum (a to forum/?topicid=ID, text = replies).
 *  - detail : /anime/ID/slug/episode/N, h2.fs18 (title), p.fn-grey2 (romaji), "Duration:" / "Aired:" text, the h2 "Synopsis" block and
 *             a.btn-embedded-denied (provider redirect).
 */
class MalEpisodesApi {
    private val client = NetworkClient.shared
    private val base = "https://myanimelist.net"

    private fun get(url: String): Document {
        val req = Request.Builder().url(url).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()
        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(if (resp.code == 404) "MAL has no episode list for this title" else "MAL returned ${resp.code}")
            Jsoup.parse(body, url)
        }
    }

    private fun slug(title: String): String = title.trim().replace(Regex("[^A-Za-z0-9-]+"), "_").trim('_').ifBlank { "_" }

    suspend fun episodes(malId: Int, title: String, offset: Int): EpisodesPage = withContext(Dispatchers.IO) {
        val q = if (offset > 0) "?offset=$offset" else ""
        val doc = runCatching { get("$base/anime/$malId/${slug(title)}/episode$q") }.getOrElse { get("$base/anime/$malId/_/episode$q") }
        val items = doc.select("tr.episode-list-data").mapNotNull { row ->
            val number = row.selectFirst("td.episode-number")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val link = row.selectFirst("td.episode-title a")
            val alt = row.selectFirst("td.episode-title span.di-ib")?.text()?.replace(' ', ' ')?.trim().orEmpty()
            val forum = row.selectFirst("td.episode-forum a")
            EpisodeEntry(
                number = number,
                title = link?.text()?.trim().orEmpty().ifBlank { "Episode $number" },
                altTitle = alt,
                aired = row.selectFirst("td.episode-aired")?.text()?.trim()?.takeIf { it != "N/A" }.orEmpty(),
                pollAverage = row.selectFirst("td.episode-poll")?.attr("data-raw")?.toDoubleOrNull() ?: 0.0,
                replies = forum?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0,
                topicId = Regex("topicid=(\\d+)").find(forum?.attr("href").orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            )
        }
        // MAL pages the list in blocks of 100; a full block means there may be another.
        EpisodesPage(items, hasMore = items.size >= 100)
    }

    suspend fun episode(malId: Int, title: String, number: Int): EpisodeDetail = withContext(Dispatchers.IO) {
        val doc = runCatching { get("$base/anime/$malId/${slug(title)}/episode/$number") }.getOrElse { get("$base/anime/$malId/_/episode/$number") }
        val head = doc.selectFirst("h2.fs18")
        val titleText = head?.clone()?.also { it.select("span.fw-n").remove() }?.text()?.trim().orEmpty()
        val meta = head?.parents()?.firstOrNull { it.hasClass("di-t") }?.text().orEmpty()
        val synopsis = doc.select("h2.fw-b").firstOrNull { it.text().trim() == "Synopsis" }?.parent()?.let { box ->
            val c = box.clone(); c.select("h2").remove(); c.select("br").forEach { it.after("\n") }; c.wholeText().trim()
        }.orEmpty()
        EpisodeDetail(
            title = titleText,
            altTitle = doc.selectFirst("p.fn-grey2")?.text()?.trim().orEmpty(),
            duration = Regex("Duration:\\s*(\\S+)").find(meta)?.groupValues?.get(1).orEmpty(),
            aired = Regex("Aired:\\s*(.+?)(?:\\(JST\\)|$)").find(meta)?.groupValues?.get(1)?.trim().orEmpty(),
            synopsis = synopsis.takeIf { !it.startsWith("No synopsis", ignoreCase = true) }.orEmpty(),
            watchUrl = doc.selectFirst("a.btn-embedded-denied")?.absUrl("href").orEmpty(),
        )
    }
}
