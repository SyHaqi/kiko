package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

// "Last, First" to "First Last"
private fun reorderMalName(raw: String): String {
    val parts = raw.split(", ")
    return if (parts.size == 2) "${parts[1]} ${parts[0]}" else raw
}
private fun normalizeMangaFormat(malType: String) = when (malType.lowercase()) {
    "one-shot", "oneshot" -> "One Shot"
    else -> malType
}

// Resolves an author/artist name typed into the Discover filter to a MAL person id, then
// scrapes that person's own MAL page for their credited manga — no third-party API
// involved, just MAL's own pages, parsed straight with Jsoup like ClubsApi/StacksApi do:
//
// 1. https://myanimelist.net/search/all?cat=person&q=<name> — MAL's own unified search
//    page (this is the URL pattern MAL itself declares as its site-search endpoint),
//    filtered server-side to the "person" category. We tried two other "search" URLs
//    before landing here, both dead ends:
//      - https://myanimelist.net/people.php?cat=person&q=<name> silently ignores the q
//        param and always returns the default "Most Favorited" ranking regardless of
//        what's searched, so it was resolving to an unrelated popular person instead of
//        Yasuda — that's why the manga list came from the wrong person entirely.
//      - https://myanimelist.net/search/prefix.json?type=person&keyword=<name>, the ajax
//        endpoint behind the header's own search-as-you-type box. Plausible in principle,
//        but it's meant to be called only as a same-origin XHR — a bare request from here
//        can fail in ways that get silently swallowed by runCatching, so searchPerson()
//        was quietly returning null on-device even though nothing looked wrong in the
//        code. The caller then fell back to a client-filtered top-500 ranking pool (see
//        LibraryViewModel) — which only contains an author's biggest hits, so smaller or
//        older credited works never show up there even though that fallback "works" in
//        the sense of not crashing outright. /search/all is a real server-rendered
//        results page, not ajax-only, so there's no origin/header requirement to trip
//        over.
//    Every result row on that page links to the person's profile as
//    https://myanimelist.net/people/{id}/{Name}, so rather than depending on a specific
//    table/row CSS class, we collect every such link on the page directly and match its
//    visible text against the query — this degrades gracefully even if MAL reskins the
//    surrounding markup, since the href shape is the one thing a person result has to have.
// 2. https://myanimelist.net/people/{id} — that person's own page, whose "Published Manga"
//    table lists every credited work with its title, cover, format, year, score, and member
//    count baked directly into the HTML (one request, and titles are never blank the way a
//    partially-indexed third-party API's can be).
class MalPeopleApi {
    private val client = OkHttpClient()
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private fun fetchDoc(url: String): Document {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("MAL request failed (${resp.code}): $url")
            return Jsoup.parse(body, url)
        }
    }

    // Resolve a typed author name (e.g. "Suzuhito Yasuda") to their MAL person id via
    // MAL's own person search results page. Rows display "Last, First" (sometimes with
    // the Japanese name alongside), so we match loosely on word containment rather than
    // requiring an exact match against the user's "First Last" input. Returns the first
    // (best-ranked) result whose link text contains every query word.
    suspend fun searchPerson(name: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val queryWords = name.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (queryWords.isEmpty()) return@withContext null
            val encoded = java.net.URLEncoder.encode(name, "UTF-8")
            val doc = fetchDoc("https://myanimelist.net/search/all?cat=person&q=$encoded")
            doc.select("a[href~=/people/\\d+/]").firstNotNullOfOrNull { link ->
                val displayName = link.text().takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
                if (queryWords.all { displayName.lowercase().contains(it) }) {
                    Regex("/people/(\\d+)/").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                } else null
            }
        }.getOrNull()
    }

    // Scrape a resolved person's own MAL page for their credited manga (or anime, if
    // ever needed) and pull each work's manga id straight out of its title link
    // (https://myanimelist.net/manga/{id}/{Title} -> {id}).
    //
    // queriedName is what the person typed into the filter. DiscoverFilters.matches()
    // requires MediaItem.allCreators to contain that string (case-insensitive substring)
    // or the result gets filtered back out downstream — the person page doesn't repeat
    // the author's own name on every row (it's their own page), so we stamp the queried
    // name plus the page's own canonical name onto every result to guarantee that check
    // passes regardless of "Last, First" vs "First Last" formatting differences.
    suspend fun fetchCreditedWorks(kind: String, personId: Int, queriedName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDoc("https://myanimelist.net/people/$personId")
            val creatorLabel = doc.selectFirst("h1.title-name strong")?.text()?.let(::reorderMalName)?.takeIf { it.isNotBlank() }
            val allCreators = listOfNotNull(creatorLabel, queriedName.takeIf { it.isNotBlank() }).distinct().joinToString(", ")
            val tableClass = if (kind == "anime") "js-table-people-staff" else "js-table-people-manga"
            val rowClass = if (kind == "anime") "js-people-staff" else "js-people-manga"
            val table = doc.selectFirst("table.$tableClass") ?: return@runCatching emptyList()
            table.select("tr.$rowClass").mapNotNull { row -> parseWorkRow(kind, row, creatorLabel.orEmpty(), allCreators) }
        }.getOrElse { emptyList() }
    }

    // Convenience wrapper: name in, manga list out.
    suspend fun searchMangaByAuthor(name: String): List<MediaItem> {
        val personId = searchPerson(name) ?: return emptyList()
        return fetchCreditedWorks("manga", personId, name)
    }

    // MAL serves the credited-works table's thumbnails through a resizing proxy path like
    // "/r/50x70/images/manga/3/122224.jpg" — the same pattern ClubsApi strips for club/member
    // avatars. Removing the "/r/WxH/" segment returns the original, full-resolution cover.
    private fun fullResUrl(url: String): String = url.replaceFirst(Regex("/r/\\d+x\\d+(?:-\\d+)?/"), "/")

    private fun parseWorkRow(kind: String, row: Element, creator: String, allCreators: String): MediaItem? {
        val link = row.selectFirst("a.js-people-title") ?: return null
        val title = link.text().takeIf { it.isNotBlank() } ?: return null
        val id = Regex("/$kind/(\\d+)").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val cover = row.selectFirst("img")?.let { img ->
            val raw = img.attr("data-src").ifBlank { img.attr("src") }
            fullResUrl(img.absUrl(if (img.hasAttr("data-src")) "data-src" else "src").ifBlank { raw })
        }.orEmpty()
        // e.g. "TV, Fall 2014" or "Light Novel, 2017"
        val infoParts = row.selectFirst("div[class*=info-text]")?.text()?.split(", ", limit = 2).orEmpty()
        val format = infoParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: ""
        val year = infoParts.getOrNull(1)?.takeLast(4)?.filter { it.isDigit() } ?: ""
        val score = row.selectFirst("span.score-val")?.text()?.toDoubleOrNull() ?: 0.0
        val members = row.selectFirst("div[class*=total-members]")?.text()
            ?.let { Regex("[\\d,]+").find(it)?.value?.replace(",", "")?.toIntOrNull() } ?: 0
        // This row only ever carries format + year; genre/theme/demographic/source/rating/
        // airing-status data isn't on the person's own page at all (see class doc above).
        // Flagging that here — rather than leaving genres/etc. as a plain empty list — is
        // what lets matches() tell "no data for this facet" apart from "known non-match",
        // so an author search combined with e.g. a Genre filter still shows every one of
        // this person's works instead of silently showing zero.
        val unknownFacets = setOf("genres", "themes", "demographics", "source", "rating", "airingStatus")
        return MediaItem(
            id = id.toString(),
            title = title,
            type = if (kind == "anime") MediaType.Anime else MediaType.Manga,
            status = WatchStatus.Plan,
            cover = cover,
            score = score,
            listUsers = members,
            creator = creator,
            allCreators = allCreators,
            startDate = year,
            format = if (kind == "manga") normalizeMangaFormat(format) else format,
            nsfw = "white",
            inUserList = false,
            unknownFacets = unknownFacets,
        )
    }
}