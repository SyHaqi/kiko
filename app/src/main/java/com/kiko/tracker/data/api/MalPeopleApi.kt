package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.PersonDetail
import com.kiko.tracker.data.model.PersonSummary
import com.kiko.tracker.data.model.PersonVoiceRole
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.matches

// Resolves an author/artist name
// scrapes that person's own
// involved, just MAL's own
//
// 1. https://myanimelist.net/search/all?cat=person&q=<name> — MAL's
// page (this is the
// filtered server-side to the
// before landing here, both
// - https://myanimelist.net/people.php?cat=person&q=<name> silently ignores
// param and always returns
// what's searched, so it
// Yasuda — that's why
// - https://myanimelist.net/search/prefix.json?type=person&keyword=<name>, the ajax
// endpoint behind the header's
// but it's meant to
// can fail in ways
// was quietly returning null
// code. The caller then
// LibraryViewModel) — which only
// older credited works never
// the sense of not
// results page, not ajax-only,
// over.
// Every result row on
// https://myanimelist.net/people/{id}/{Name}, so rather than
// table/row CSS class, we
// visible text against the
// surrounding markup, since the
// 2. https://myanimelist.net/people/{id} — that
// table lists every credited
// count baked directly into
// partially-indexed third-party API's can
// source/rating data at all,
// and fetch that one
// "id in, that item's
// per credited work instead
// directly into each tile
class MalPeopleApi {
    private val client = NetworkClient.shared
    // Only used to fill
    // (see enrichWithFacets below) —
    // same reasoning MalCompanyApi documents
    private val tenrai = TenraiApi()

    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

    // Resolve a typed author
    // MAL's own person search
    // the Japanese name alongside),
    // requiring an exact match
    // (best-ranked) result whose link
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

    // Scrape a resolved person's
    // ever needed) and pull
    // (https://myanimelist.net/manga/{id}/{Title} -> {id}).
    //
    // queriedName is what the
    // requires MediaItem.allCreators to contain
    // or the result gets
    // the author's own name
    // name plus the page's
    // passes regardless of "Last,
    suspend fun fetchCreditedWorks(kind: String, personId: Int, queriedName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        runCatching { parseCreditedWorks(kind, fetchDoc("https://myanimelist.net/people/$personId"), queriedName) }.getOrElse { emptyList() }
    }

    // Shared by fetchCreditedWorks above
    // person's page) and detail()
    // rest of the profile,
    // it). enrichFacets defaults on
    // author-search filter-matching path; detail()
    // just displaying these rows,
    // a needless network round
    private suspend fun parseCreditedWorks(kind: String, doc: Document, queriedName: String, enrichFacets: Boolean = true): List<MediaItem> {
        val creatorLabel = doc.selectFirst("h1.title-name strong")?.text()?.let(::reorderMalPersonName)?.takeIf { it.isNotBlank() }
        val allCreators = listOfNotNull(creatorLabel, queriedName.takeIf { it.isNotBlank() }).distinct().joinToString(", ")
        val tableClass = if (kind == "anime") "js-table-people-staff" else "js-table-people-manga"
        val rowClass = if (kind == "anime") "js-people-staff" else "js-people-manga"
        val table = doc.selectFirst("table.$tableClass") ?: return emptyList()
        val rows = table.select("tr.$rowClass").mapNotNull { row -> parseWorkRow(kind, row, creatorLabel.orEmpty(), allCreators) }
        // Same "we already have
        // search's facet resolution, just
        // never carries genre/theme/demographic/source/rating data
        // below), so each credited
        // and make it actually
        // these rows being silently
        return if (kind == "manga" && enrichFacets) enrichWithFacets(rows) else rows
    }

    // Fan out one facet
    // concurrent requests, so this
    private suspend fun enrichWithFacets(items: List<MediaItem>): List<MediaItem> = coroutineScope {
        items.map { item ->
            async {
                val malId = item.id.toIntOrNull()
                val facets = malId?.let { runCatching { tenrai.fetchItemFacets("manga", it) }.getOrNull() }
                if (facets == null) item else item.copy(
                    genre = facets.genres.firstOrNull() ?: item.genre,
                    genres = facets.genres,
                    contentThemes = facets.contentThemes,
                    demographics = facets.demographics,
                    source = facets.source,
                    rating = facets.rating,
                    airStatus = facets.airStatus,
                    // Lookup succeeded, so these
                    // if it failed, leave
                    // checks for this one
                    unknownFacets = item.unknownFacets - setOf("genres", "themes", "demographics", "source", "rating", "airingStatus"),
                )
            }
        }.awaitAll()
    }

    // Convenience wrapper: name in,
    suspend fun searchMangaByAuthor(name: String): List<MediaItem> {
        val personId = searchPerson(name) ?: return emptyList()
        return fetchCreditedWorks("manga", personId, name)
    }

    // Discover's People tab search
    // people search results page,
    // link straight to /people/{id}/{Slug},
    // MalCharacterApi/MalCompanyApi's own row selectors
    suspend fun search(query: String): List<PersonSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = fetchDoc("https://myanimelist.net/people.php?cat=person&q=$encoded")
            doc.select("tr:has(td div.picSurround a[href~=/people/\\d+/])").mapNotNull(::parseSearchRow)
        }.getOrElse { emptyList() }
    }

    // Full person detail page:
    // Voice Acting Roles scraped
    // is data this app
    // parseCreditedWorks above since those
    // author-search flow already knows
    suspend fun detail(id: Int): PersonDetail = withContext(Dispatchers.IO) {
        val doc = fetchDoc("https://myanimelist.net/people/$id")
        coroutineScope {
            val staff = async { parseCreditedWorks("anime", doc, "", enrichFacets = false) }
            val manga = async { parseCreditedWorks("manga", doc, "", enrichFacets = false) }
            parseDetail(id, doc, staff.await(), manga.await())
        }
    }

    private fun parseSearchRow(row: Element): PersonSummary? {
        val cells = row.children()
        val picCell = cells.getOrNull(0) ?: return null
        val nameCell = cells.getOrNull(1) ?: return null
        val nameLink = nameCell.selectFirst("a[href*=/people/]") ?: return null
        val malId = Regex("/people/(\\d+)").find(nameLink.attr("abs:href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val rawName = nameLink.text().trim().takeIf { it.isNotBlank() } ?: return null
        // e.g. "(Ono Kana)" —
        val altName = nameCell.selectFirst("small")?.text()?.trim().orEmpty()
        val image = picCell.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.contains("questionmark") }
            ?.let(::fullResMalImage) ?: ""
        return PersonSummary(malId = malId, name = reorderMalPersonName(rawName), image = image, altName = altName)
    }

    private fun parseDetail(id: Int, doc: Document, staffCredits: List<MediaItem>, publishedManga: List<MediaItem>): PersonDetail {
        val nameRaw = doc.selectFirst("div.h1-title h1.title-name strong")?.text()?.trim().orEmpty()
        val name = reorderMalPersonName(nameRaw).ifBlank { "Unknown" }
        // The portrait column (the
        // field lives — picture,
        // parseProfileFields reads below.
        val infoCell = doc.selectFirst("td[width=225]")
        val image = infoCell?.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fullResMalImage) ?: ""
        val (allFields, about) = infoCell?.let(::parseProfileFields) ?: (emptyList<Pair<String, String>>() to "")
        val favorites = allFields.firstOrNull { it.first.contains("favorites", ignoreCase = true) }
            ?.second?.replace(",", "")?.toIntOrNull() ?: 0
        // Shown separately with its
        // CharacterDetail.favorites — so it's
        // rather than rendered twice.
        val bioFields = allFields.filterNot { it.first.contains("favorites", ignoreCase = true) }
        val voiceActingRoles = doc.selectFirst("table.js-table-people-character")
            ?.select("tr.js-people-character")?.mapNotNull(::parseVoiceRoleRow).orEmpty()
        return PersonDetail(
            malId = id, name = name, image = image, favorites = favorites,
            bioFields = bioFields, about = about, voiceActingRoles = voiceActingRoles,
            staffCredits = staffCredits, publishedManga = publishedManga,
        )
    }

    // The person's own bio
    // Hometown/Blood type/Height/Skills & Abilities/Profile/Twitter/...)
    // "Label: value" text directly
    // container — read from
    // before. The one part
    // "More:" block (see parseMoreBlock
    // entirely and flattens it
    // block is carved out
    // pointing at nothing) drops
    private fun parseProfileFields(container: Element): Pair<List<Pair<String, String>>, String> {
        val working = container.clone()
        val moreDiv = working.selectFirst(".people-informantion-more")
        val (moreFields, about) = moreDiv?.let(::parseMoreBlock) ?: (emptyList<Pair<String, String>>() to "")
        moreDiv?.remove()

        val text = normalizeWhitespace(working)
        val labelRegex = Regex("([A-Z][A-Za-z][A-Za-z &()]{0,28}):\\s")
        val matches = labelRegex.findAll(text).toList()
        val fields = mutableListOf<Pair<String, String>>()
        for (i in matches.indices) {
            val label = matches[i].groupValues[1].trim()
            val valueStart = matches[i].range.last + 1
            val valueEnd = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val value = text.substring(valueStart, valueEnd).trim()
            if (value.isNotBlank()) fields += label to value
        }
        return (fields + moreFields) to about
    }

    // The "More:" block (Birthplace/Blood
    // partway through, e.g. Earphones
    // element the way the
    // with only <br> tags
    // no "Label:" prefix at
    // whole-column approach) let a
    // next field's own label
    // into what should have
    // row — and let
    // to precede it. Walking
    // newline (mirroring MalCharacterApi.parseBio) keeps
    // own line, and lets
    // contaminating a neighboring field
    // labeled fields, unlike parseBio,
    private fun parseMoreBlock(container: Element): Pair<List<Pair<String, String>>, String> {
        val raw = StringBuilder()
        fun walk(node: Node) {
            when {
                node is TextNode -> raw.append(node.text())
                node is Element && node.tagName() == "br" -> raw.append("\n")
                node is Element -> node.childNodes().forEach(::walk)
            }
        }
        container.childNodes().forEach(::walk)

        val lineRegex = Regex("^([A-Z][A-Za-z &()]{0,28}):\\s*(.+)$")
        val fields = mutableListOf<Pair<String, String>>()
        val aboutLines = mutableListOf<String>()
        for (rawLine in raw.toString().replace('\u00A0', ' ').split("\n")) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val match = lineRegex.find(line)
            if (match != null) fields += match.groupValues[1].trim() to match.groupValues[2].trim()
            else aboutLines += line
        }
        return fields to aboutLines.joinToString("\n").trim()
    }

    // Voice Acting Roles: one
    // thumbnail, anime title +
    // thumbnail — see the
    private fun parseVoiceRoleRow(row: Element): PersonVoiceRole? {
        val cells = row.children()
        if (cells.size < 4) return null
        val workLink = cells[1].selectFirst("a.js-people-title") ?: return null
        val workId = Regex("/anime/(\\d+)").find(workLink.attr("abs:href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val workTitle = workLink.text().trim().takeIf { it.isNotBlank() } ?: return null
        val workImage = cells[0].selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.contains("questionmark") }
            ?.let(::fullResMalImage) ?: ""
        // e.g. "Movie, 2026" or
        val workInfo = cells[1].selectFirst("div[class*=info-text]")?.text()?.trim().orEmpty()
        val charCell = cells[2]
        val charLink = charCell.selectFirst("a[href*=/character/]") ?: return null
        val characterId = Regex("/character/(\\d+)").find(charLink.attr("abs:href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val characterName = reorderMalPersonName(charLink.text().trim())
        // Second spaceit_pad in this
        // character name link and
        val roleLabel = charCell.select("div.spaceit_pad").getOrNull(1)?.text()?.trim().orEmpty()
        val characterImage = cells[3].selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.contains("questionmark") }
            ?.let(::fullResMalImage) ?: ""
        return PersonVoiceRole(
            workId = workId, workTitle = workTitle, workImage = workImage, workInfo = workInfo,
            characterId = characterId, characterName = characterName, characterImage = characterImage,
            roleLabel = roleLabel,
        )
    }

    private fun parseWorkRow(kind: String, row: Element, creator: String, allCreators: String): MediaItem? {
        val link = row.selectFirst("a.js-people-title") ?: return null
        val title = link.text().takeIf { it.isNotBlank() } ?: return null
        val id = Regex("/$kind/(\\d+)").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val cover = row.selectFirst("img")?.let { img ->
            val raw = img.attr("data-src").ifBlank { img.attr("src") }
            fullResMalImage(img.absUrl(if (img.hasAttr("data-src")) "data-src" else "src").ifBlank { raw })
        }.orEmpty()
        // e.g. "TV, Fall 2014"
        val infoParts = row.selectFirst("div[class*=info-text]")?.text()?.split(", ", limit = 2).orEmpty()
        val format = infoParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: ""
        val year = infoParts.getOrNull(1)?.takeLast(4)?.filter { it.isDigit() } ?: ""
        val score = row.selectFirst("span.score-val")?.text()?.toDoubleOrNull() ?: 0.0
        val members = row.selectFirst("div[class*=total-members]")?.text()
            ?.let { Regex("[\\d,]+").find(it)?.value?.replace(",", "")?.toIntOrNull() } ?: 0
        // This row only ever
        // airing-status data isn't on
        // Flagging that here —
        // what lets matches() tell
        // so an author search
        // this person's works instead
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
            format = if (kind == "manga") normalizeMangaFormatLabel(format) else format,
            nsfw = "white",
            inUserList = false,
            unknownFacets = unknownFacets,
        )
    }
}