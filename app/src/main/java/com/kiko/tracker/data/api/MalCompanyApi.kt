package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import com.kiko.tracker.data.model.CompanyDetail
import com.kiko.tracker.data.model.CompanyNews
import com.kiko.tracker.data.model.CompanySummary
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.WatchStatus

// Resolves a studio/producer name
// then scrapes that company's
// two-request, MAL-pages-only approach MalPeopleApi
// search no longer depends
// ~500 entries, so older
// Tenrai being up at
//
// 1. https://myanimelist.net/search/all?cat=company&q=<name> — MAL's
// page, filtered server-side to
// cat=person; "Companies" is one
// dropdown, so this is
// Every result row links
// https://myanimelist.net/anime/producer/{id}/{Name}, so — same
// MalPeopleApi — we just
// text against the query,
// 2. https://myanimelist.net/anime/producer/{id} — that
// grid lists every credited
// split those into separate
// format, score, member count,
// directly into the HTML
// not a ranking-chart subset.
class MalCompanyApi {
    private val client = NetworkClient.shared
    // Only used to translate
    // into names (a small,
    // anything, so this doesn't
    private val tenrai = TenraiApi()

    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

    // Resolve a typed studio
    // company search results page.
    // contains every query word,
    suspend fun searchCompany(name: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val queryWords = name.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (queryWords.isEmpty()) return@withContext null
            val encoded = java.net.URLEncoder.encode(name, "UTF-8")
            val doc = fetchDoc("https://myanimelist.net/search/all?cat=company&q=$encoded")
            doc.select("a[href~=/anime/producer/\\d+/]").firstNotNullOfOrNull { link ->
                val displayName = link.text().takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
                if (queryWords.all { displayName.lowercase().contains(it) }) {
                    Regex("/anime/producer/(\\d+)/").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                } else null
            }
        }.getOrNull()
    }

    // Discover's Companies tab search
    // (https://myanimelist.net/company?q=...), the same page
    // A-Z browse links already
    // MalCharacterApi.search: a thumbnail cell
    // /anime/producer/{id}/ link every row
    // so this survives a
    suspend fun search(query: String): List<CompanySummary> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = fetchDoc("https://myanimelist.net/company?q=$encoded")
            doc.select("tr:has(td div.picSurround a[href~=/anime/producer/\\d+/])").mapNotNull(::parseSearchRow)
        }.getOrElse { emptyList() }
    }

    private fun parseSearchRow(row: Element): CompanySummary? {
        val cells = row.children()
        val picCell = cells.getOrNull(0) ?: return null
        val nameCell = cells.getOrNull(1) ?: return null
        val nameLink = nameCell.selectFirst("a[href*=/anime/producer/]") ?: return null
        val malId = Regex("/anime/producer/(\\d+)").find(nameLink.attr("abs:href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = nameLink.text().trim().takeIf { it.isNotBlank() } ?: return null
        // e.g. "(京都アニメーション)" — kept
        val japanese = nameCell.selectFirst("small")?.text()?.trim().orEmpty()
        val image = picCell.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.contains("questionmark") }
            ?.let(::fullResMalImage) ?: ""
        return CompanySummary(malId = malId, name = name, japanese = japanese, image = image)
    }

    // Full company detail page:
    // scraped straight off the
    // catalog reusing the exact
    // same page, fetched once.
    //
    // The page fetch and
    // though neither depends on
    // demographic reference lookup, not
    // facet cache that meant
    // trips back to back
    // both fire together and
    suspend fun detail(id: Int): CompanyDetail = withContext(Dispatchers.IO) {
        coroutineScope {
            val docDeferred = async { fetchDoc("https://myanimelist.net/anime/producer/$id") }
            val facetsDeferred = async { runCatching { tenrai.facetIdMaps("anime") }.getOrNull() }
            val doc = docDeferred.await()
            val works = runCatching { parseWorks(doc, "", facetsDeferred.await()) }.getOrElse { emptyList() }
            parseDetail(id, doc, works)
        }
    }

    // Scrape a resolved studio's
    //
    // queriedName is what the
    // alongside the page's own
    // fetchCreditedWorks: matches() requires allCreators
    // and formatting can otherwise
    //
    // Same page-fetch/facet-lookup parallelization as
    suspend fun fetchWorks(companyId: Int, queriedName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val docDeferred = async { fetchDoc("https://myanimelist.net/anime/producer/$companyId") }
            val facetsDeferred = async { runCatching { tenrai.facetIdMaps("anime") }.getOrNull() }
            runCatching { parseWorks(docDeferred.await(), queriedName, facetsDeferred.await()) }.getOrElse { emptyList() }
        }
    }

    // Shared by fetchWorks above
    // the page doc and
    // resolved facets in here,
    private fun parseWorks(doc: Document, queriedName: String, facets: TenraiApi.FacetIdMaps?): List<MediaItem> {
        val creatorLabel = doc.selectFirst("h1.title-name")?.text()?.takeIf { it.isNotBlank() }
        val allCreators = listOfNotNull(creatorLabel, queriedName.takeIf { it.isNotBlank() }).distinct().joinToString(", ")
        return doc.select("div.js-seasonal-anime").mapNotNull { tile -> parseTile(tile, creatorLabel.orEmpty(), allCreators, facets) }
    }

    // Convenience wrapper: name in,
    suspend fun searchAnimeByStudio(name: String): List<MediaItem> {
        val companyId = searchCompany(name) ?: return emptyList()
        return fetchWorks(companyId, name)
    }

    // ---- Company detail page

    private fun parseDetail(id: Int, doc: Document, works: List<MediaItem>): CompanyDetail {
        val name = doc.selectFirst("h1.title-name")?.text()?.trim().orEmpty().ifBlank { "Unknown" }
        val image = doc.selectFirst("div.logo img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fullResMalImage) ?: ""
        // The one div.mb16 under
        // Established/Member Favorites/about rows —
        // share icons) has no
        val infoContainer = doc.selectFirst("div.content-left div.mb16:has(div.spaceit_pad)")
        var favorites = 0
        val bioFields = mutableListOf<Pair<String, String>>()
        var about = ""
        infoContainer?.select("div.spaceit_pad")?.forEach { row ->
            val labelSpan = row.selectFirst("span.dark_text")
            if (labelSpan != null) {
                val label = labelSpan.text().trim().removeSuffix(":")
                val clone = row.clone()
                clone.selectFirst("span.dark_text")?.remove()
                val value = clone.text().trim()
                if (label.equals("Member Favorites", ignoreCase = true)) {
                    favorites = value.replace(",", "").toIntOrNull() ?: 0
                } else if (value.isNotBlank()) {
                    bioFields += label to value
                }
            } else {
                // The one row with
                // paragraph — a single
                // breaks, so a plain
                // line (see brToNewlines below).
                val text = brToNewlines(row)
                if (text.isNotBlank()) about = text
            }
        }
        val links = doc.selectFirst("div.user-profile-sns")?.select("a")?.mapNotNull { a ->
            val href = a.attr("abs:href").trim()
            val label = a.text().trim()
            if (href.isBlank() || label.isBlank()) null else label to href
        }.orEmpty()
        val news = runCatching { parseNews(doc) }.getOrNull()
        return CompanyDetail(
            malId = id, name = name, image = image, favorites = favorites,
            bioFields = bioFields, about = about, links = links, news = news, works = works,
        )
    }

    // Same "walk the node
    // MalCharacterApi use for their
    private fun brToNewlines(container: Element): String {
        val sb = StringBuilder()
        fun walk(node: Node) {
            when {
                node is TextNode -> sb.append(node.text())
                node is Element && node.tagName() == "br" -> sb.append("\n")
                node is Element -> node.childNodes().forEach(::walk)
            }
        }
        container.childNodes().forEach(::walk)
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // The single most recent
    // only ever shows one,
    // link is what leads
    // from the row's own
    // ForumTopicScreen rather than a
    private fun parseNews(doc: Document): CompanyNews? {
        val unit = doc.selectFirst("div.news-list div.news-unit") ?: return null
        val titleLink = unit.selectFirst("p.title a") ?: return null
        val title = titleLink.text().trim().takeIf { it.isNotBlank() } ?: return null
        val image = unit.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fullResMalImage) ?: ""
        val snippet = unit.selectFirst("div.text")?.text()?.trim().orEmpty()
        val date = unit.selectFirst("p.info")?.text()?.trim()?.substringBefore(" by ")?.trim().orEmpty()
        val topicId = unit.selectFirst("a.comment")?.attr("abs:href")
            ?.let { Regex("topicid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: return null
        return CompanyNews(topicId = topicId, title = title, image = image, snippet = snippet, date = date)
    }

    private val typeFormats = mapOf(1 to "TV", 2 to "OVA", 3 to "Movie", 4 to "Special", 5 to "ONA", 6 to "Music")

    private fun parseTile(tile: Element, creator: String, allCreators: String, facets: TenraiApi.FacetIdMaps?): MediaItem? {
        val link = tile.selectFirst("div.title a") ?: return null
        val title = link.text().takeIf { it.isNotBlank() } ?: return null
        val id = Regex("/anime/(\\d+)").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        // Already full-resolution on this
        // person's credited-works table), so
        val cover = tile.selectFirst("img")?.let { img -> img.attr("data-src").ifBlank { img.attr("src") } }.orEmpty()
        val score = tile.selectFirst("span.js-score")?.text()?.toDoubleOrNull() ?: 0.0
        val members = tile.selectFirst("span.js-members")?.text()?.toIntOrNull() ?: 0
        val typeCode = Regex("js-anime-type-(\\d+)").find(tile.className())?.groupValues?.get(1)?.toIntOrNull()
        val format = typeCode?.let { typeFormats[it] } ?: "Special"
        // "19980401" -> year/month/day; some
        val raw = tile.selectFirst("span.js-start_date")?.text()?.takeIf { it.length == 8 && it != "00000000" }
        val year = raw?.take(4)?.takeIf { it != "0000" } ?: ""
        val month = raw?.substring(4, 6)?.toIntOrNull()?.takeIf { it in 1..12 }
        val day = raw?.substring(6, 8)?.toIntOrNull()?.takeIf { it in 1..31 }
        val season = month?.let { when (it) { in 1..3 -> "Winter"; in 4..6 -> "Spring"; in 7..9 -> "Summer"; else -> "Fall" } }.orEmpty()
        val startDateFull = if (year.isNotBlank() && month != null && day != null) "%s-%02d-%02d".format(year, month, day) else ""
        val genreIds = tile.attr("data-genre").split(",").mapNotNull { it.trim().toIntOrNull() }
        val genres = facets?.let { m -> genreIds.mapNotNull { m.genres[it] ?: m.explicitGenres[it] } }.orEmpty()
        val themes = facets?.let { m -> genreIds.mapNotNull { m.themes[it] } }.orEmpty()
        val demographics = facets?.let { m -> genreIds.mapNotNull { m.demographics[it] } }.orEmpty()
        // The studio page never
        // exposes genre/theme/demographic data when
        // flagged so matches() skips
        // wiping out every result
        val unknown = buildSet {
            add("source"); add("rating"); add("airingStatus")
            if (facets == null) { add("genres"); add("themes"); add("demographics") }
        }
        return MediaItem(
            id = id.toString(),
            title = title,
            type = MediaType.Anime,
            status = WatchStatus.Plan,
            genre = genres.firstOrNull() ?: "",
            genres = genres,
            contentThemes = themes,
            demographics = demographics,
            cover = cover,
            score = score,
            listUsers = members,
            creator = creator,
            allCreators = allCreators,
            startDate = year,
            season = season,
            format = format,
            startDateFull = startDateFull,
            nsfw = if (genres.any { it.equals("Hentai", ignoreCase = true) }) "black" else "white",
            inUserList = false,
            unknownFacets = unknown,
        )
    }
}