package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

private const val MAL = "https://myanimelist.net"

// Scrapes the two anime/manga detail-page widgets the official MAL API can't reliably
// supply, straight off MAL's own HTML (same approach as ClubsApi/MalPeopleApi/StacksApi
// via MalScraping.kt):
//
//  - Related Entries: the official API's related_anime/related_manga fields are same-type
//    only in practice — an /anime/{id} request reliably returns related_anime but usually
//    comes back empty for related_manga (manga/light novel adaptations), and vice versa on
//    /manga/{id}, even though the website's own Related Entries box always lists every
//    direction regardless of which page you're on.
//  - Recommendations: the official API's `recommendations` field is user-submitted only.
//    Newer/lower-traffic titles that don't have enough of those yet get padded out on the
//    website by MAL's own algorithmic "AutoRec" picks, which the official API never exposes
//    at all — so a brand-new airing title (the common case) shows nothing via the API even
//    though the website's widget is full.
//
// Verified against a real anime detail page response (Related Entries + the AutoRec-tagged
// Recommendations slider). The manga page markup wasn't available to verify against, so the
// selectors below are written to be type-agnostic (matched by /anime/ vs /manga/ in the
// link's own href) rather than assuming manga-page-specific class names.
class MalDetailScrapeApi {
    private val client = NetworkClient.shared

    data class PageExtras(val related: List<RelatedEntry>, val recommended: List<RecommendedEntry>)

    suspend fun fetch(id: Int, type: MediaType): PageExtras = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val doc = client.fetchMalDocument("$MAL/$kind/$id")
        PageExtras(parseRelated(doc), parseRecommended(doc))
    }

    // Community score breakdown (1-10) — lives on the title's separate /stats page, not
    // the main detail page above, so this is its own request, made on demand only when
    // the user actually opens the score stats screen (see LibraryViewModel.loadScoreStats).
    //
    // MAL's own routing is "/{kind}/{id}/{slug}/{subpage}", with the slug segment
    // positional but not actually validated against the id — any placeholder text works.
    // Requesting "/{kind}/{id}/stats" (no slug) doesn't hit the stats subpage at all: MAL
    // parses "stats" itself as filling the slug slot, so it silently 200s with the regular
    // detail page instead (which has no score-stats table), and this came back looking
    // like "no score data" for every title. Slugging the title in ourselves fixes it; the
    // no-slug form is kept as a fallback in case a title's slugified title ever collides
    // with something MAL treats specially.
    suspend fun fetchScoreStats(id: Int, type: MediaType, title: String): ScoreStats = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val slugged = runCatching { parseScoreStats(client.fetchMalDocument("$MAL/$kind/$id/${malSlug(title)}/stats")) }.getOrNull()
        if (slugged != null && slugged.total > 0) return@withContext slugged
        runCatching { parseScoreStats(client.fetchMalDocument("$MAL/$kind/$id/stats")) }.getOrDefault(slugged ?: ScoreStats())
    }

    // MAL's own slug convention: title with every run of non-alphanumeric characters
    // collapsed to a single underscore (e.g. "Maid-san wa Taberu dake" -> the real MAL
    // slug "Maid-san_wa_Taberu_dake"). Doesn't need to be byte-for-byte identical to MAL's
    // actual slug for this title — MAL doesn't check it against the id — just non-empty.
    private fun malSlug(title: String): String {
        val slug = title.trim().replace(Regex("[^A-Za-z0-9-]+"), "_").trim('_')
        return slug.ifBlank { "_" }
    }

    // "table.score-stats" rows go from score 10 down to 1, each with a "(N votes)" small
    // tag next to the percentage — same table shape as the Sayonara Lara stats page this
    // was verified against.
    private fun parseScoreStats(doc: Document): ScoreStats {
        val counts = doc.select("table.score-stats tr").mapNotNull { row ->
            val score = row.selectFirst("td.score-label")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val votes = Regex("\\d[\\d,]*").find(row.select("small").text())?.value?.replace(",", "")?.toIntOrNull() ?: 0
            score to votes
        }.toMap()
        return ScoreStats(counts)
    }

    // malId + malType read straight off the link's own href rather than off which page
    // we're on, so a manga's related anime (and an anime's related manga/light novel)
    // both come through correctly.
    private fun malRefFromUrl(url: String): Pair<Int, String>? {
        val match = Regex("/(anime|manga)/(\\d+)").find(url) ?: return null
        val id = match.groupValues[2].toIntOrNull() ?: return null
        return id to match.groupValues[1]
    }

    private fun parseRelated(doc: Document): List<RelatedEntry> =
        doc.select("div.related-entries div.entry").mapNotNull { entry ->
            val link = entry.selectFirst(".content .title a") ?: entry.selectFirst(".image a") ?: return@mapNotNull null
            val (malId, malType) = malRefFromUrl(link.attr("abs:href")) ?: return@mapNotNull null
            // e.g. "Adaptation\n(Manga)" -> "Adaptation (Manga)"
            val relation = entry.selectFirst(".content .relation")?.let { normalizeWhitespace(it) }
                ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            val title = normalizeWhitespace(link).trim()
            if (title.isBlank()) return@mapNotNull null
            val cover = entry.selectFirst(".image img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
                ?.let(::fullResMalImage) ?: ""
            RelatedEntry(relation = relation.ifBlank { "Related" }, title = title, malId = malId, malType = malType, cover = cover)
        }

    // The recommendations widget's own links carry a stable "?suggestion" query param
    // regardless of whether the entry is user-submitted or an AutoRec fallback, so that's
    // used as the anchor selector instead of a class name that might differ between the
    // anime and manga versions of the widget.
    private fun parseRecommended(doc: Document): List<RecommendedEntry> =
        doc.select("a[href*='?suggestion']").mapNotNull { a ->
            val (malId, malType) = malRefFromUrl(a.attr("abs:href")) ?: return@mapNotNull null
            val title = a.selectFirst(".title")?.text()?.takeIf { it.isNotBlank() }
                ?: a.closest("li")?.attr("title")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val cover = a.selectFirst("img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
                ?.let(::fullResMalImage) ?: ""
            val usersText = a.selectFirst(".users")?.text().orEmpty()
            val isAuto = usersText.contains("AutoRec", ignoreCase = true)
            val votes = if (isAuto) 0 else Regex("\\d+").find(usersText)?.value?.toIntOrNull() ?: 0
            RecommendedEntry(malId = malId, title = title, cover = cover, votes = votes, malType = malType, isAuto = isAuto)
        }.distinctBy { it.malId to it.malType }
}