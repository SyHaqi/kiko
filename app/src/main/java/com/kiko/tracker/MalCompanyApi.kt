package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

// Resolves a studio/producer name typed into the Discover filter to a MAL company id,
// then scrapes that company's own MAL page for every anime it's credited on — the same
// two-request, MAL-pages-only approach MalPeopleApi uses for manga authors, so studio
// search no longer depends on Tenrai's ranking pool (which only covers each chart's top
// ~500 entries, so older or smaller titles in a studio's catalog never showed up) or on
// Tenrai being up at all.
//
// 1. https://myanimelist.net/search/all?cat=company&q=<name> — MAL's own unified search
//    page, filtered server-side to the "company" category (mirrors MalPeopleApi's
//    cat=person; "Companies" is one of the categories in MAL's own header search
//    dropdown, so this is a real, first-party search endpoint, not a guess).
//    Every result row links to the studio's own page as
//    https://myanimelist.net/anime/producer/{id}/{Name}, so — same trick as
//    MalPeopleApi — we just collect every such link on the page and match its visible
//    text against the query, rather than depending on a specific row/table CSS class.
// 2. https://myanimelist.net/anime/producer/{id} — that studio's own page, whose anime
//    grid lists every credited title (as studio, producer, or licensor — MAL doesn't
//    split those into separate pages, just a per-row role label) with title, cover,
//    format, score, member count, air date, and genre/theme/demographic ids all baked
//    directly into the HTML — one request, and it's the studio's *complete* catalog,
//    not a ranking-chart subset.
class MalCompanyApi {
    private val client = NetworkClient.shared
    // Only used to translate the studio page's numeric genre/theme/demographic ids back
    // into names (a small, static, once-cached reference lookup) — not to search or rank
    // anything, so this doesn't reintroduce the "missing catalog" problem above.
    private val tenrai = TenraiApi()

    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

    // Resolve a typed studio name (e.g. "Madhouse") to its MAL company id via MAL's own
    // company search results page. Returns the first (best-ranked) result whose link text
    // contains every query word, same loose-match rule MalPeopleApi uses for people.
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

    // Discover's Companies tab search — MAL's own dedicated company search page
    // (https://myanimelist.net/company?q=...), the same page this app's own "Companies"
    // A-Z browse links already point at. Row shape mirrors MalPeopleApi.search/
    // MalCharacterApi.search: a thumbnail cell + a name cell, matched by the
    // /anime/producer/{id}/ link every row has to have rather than a specific table class,
    // so this survives a markup reskin the same way those two do.
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
        // e.g. "(京都アニメーション)" — kept as-is (parens included), same as it reads on MAL's own row.
        val japanese = nameCell.selectFirst("small")?.text()?.trim().orEmpty()
        val image = picCell.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.contains("questionmark") }
            ?.let(::fullResMalImage) ?: ""
        return CompanySummary(malId = malId, name = name, japanese = japanese, image = image)
    }

    // Full company detail page: profile fields/favorites/about/links/one recent news item
    // scraped straight off the page's own left column (see parseDetail below), anime
    // catalog reusing the exact tile parsing fetchWorks below already does — this is that
    // same page, fetched once.
    suspend fun detail(id: Int): CompanyDetail = withContext(Dispatchers.IO) {
        val doc = fetchDoc("https://myanimelist.net/anime/producer/$id")
        val works = runCatching { parseWorks(doc, "") }.getOrElse { emptyList() }
        parseDetail(id, doc, works)
    }

    // Scrape a resolved studio's own MAL page for its full anime catalog.
    //
    // queriedName is what the person typed into the filter, stamped onto every result
    // alongside the page's own canonical name — same reasoning as MalPeopleApi's
    // fetchCreditedWorks: matches() requires allCreators to contain the searched string,
    // and formatting can otherwise differ between the two.
    suspend fun fetchWorks(companyId: Int, queriedName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        runCatching { parseWorks(fetchDoc("https://myanimelist.net/anime/producer/$companyId"), queriedName) }.getOrElse { emptyList() }
    }

    // Shared by fetchWorks above (studio-search flow, needs a fresh fetch of the company's
    // page) and detail() above (already has the page in hand from parsing the rest of the
    // profile, so this just re-parses the same Document instead of re-fetching it).
    private suspend fun parseWorks(doc: Document, queriedName: String): List<MediaItem> {
        val creatorLabel = doc.selectFirst("h1.title-name")?.text()?.takeIf { it.isNotBlank() }
        val allCreators = listOfNotNull(creatorLabel, queriedName.takeIf { it.isNotBlank() }).distinct().joinToString(", ")
        // Resolved once for the whole page rather than once per row. A failure here
        // doesn't fail the whole search — every row just falls back to no genre/theme/
        // demographic data (flagged via unknownFacets) instead.
        val facets = runCatching { tenrai.facetIdMaps("anime") }.getOrNull()
        return doc.select("div.js-seasonal-anime").mapNotNull { tile -> parseTile(tile, creatorLabel.orEmpty(), allCreators, facets) }
    }

    // Convenience wrapper: name in, anime list out.
    suspend fun searchAnimeByStudio(name: String): List<MediaItem> {
        val companyId = searchCompany(name) ?: return emptyList()
        return fetchWorks(companyId, name)
    }

    // ---- Company detail page parsing (profile fields/about/links/one recent news item) ----

    private fun parseDetail(id: Int, doc: Document, works: List<MediaItem>): CompanyDetail {
        val name = doc.selectFirst("h1.title-name")?.text()?.trim().orEmpty().ifBlank { "Unknown" }
        val image = doc.selectFirst("div.logo img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fullResMalImage) ?: ""
        // The one div.mb16 under content-left that actually holds the Synonyms/Japanese/
        // Established/Member Favorites/about rows — the sibling div.mb16 above it (social
        // share icons) has no div.spaceit_pad children, so this disambiguates cleanly.
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
                // The one row with no dark_text label at all is the free-text about
                // paragraph — a single bare <span> whose own <br> tags mark its paragraph
                // breaks, so a plain Element.text() call would flatten it into one run-on
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

    // Same "walk the node tree, turn <br> into a real newline" approach MalPeopleApi/
    // MalCharacterApi use for their own bio blocks.
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

    // The single most recent item off the page's own "Recent News" list — CompanyDetailScreen
    // only ever shows one, same as MAL's own page does before its "More News" link; that
    // link is what leads to the rest, which this doesn't need to replicate. topicId comes
    // from the row's own comment-count link so tapping the card reuses this app's existing
    // ForumTopicScreen rather than a bespoke news-article reader.
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
        // Already full-resolution on this page (unlike the small resized thumbnails on a
        // person's credited-works table), so no /r/WxH/ proxy segment to strip here.
        val cover = tile.selectFirst("img")?.let { img -> img.attr("data-src").ifBlank { img.attr("src") } }.orEmpty()
        val score = tile.selectFirst("span.js-score")?.text()?.toDoubleOrNull() ?: 0.0
        val members = tile.selectFirst("span.js-members")?.text()?.toIntOrNull() ?: 0
        val typeCode = Regex("js-anime-type-(\\d+)").find(tile.className())?.groupValues?.get(1)?.toIntOrNull()
        val format = typeCode?.let { typeFormats[it] } ?: "Special"
        // "19980401" -> year/month/day; some rows only know the year ("20020000")
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
        // The studio page never exposes source/rating/airing status at all, and only
        // exposes genre/theme/demographic data when the facet lookup above succeeded —
        // flagged so matches() skips those specific checks rather than a filter combo
        // wiping out every result (see MediaItem.unknownFacets doc in Models.kt).
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