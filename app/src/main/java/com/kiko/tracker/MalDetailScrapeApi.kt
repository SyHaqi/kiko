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
