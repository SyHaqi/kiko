package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

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
//    partially-indexed third-party API's can be). It does not carry genre/theme/demographic/
//    source/rating data at all, so for manga we take the id straight out of each row's link
//    and fetch that one work's own facet data via Tenrai (see enrichWithFacets) — the same
//    "id in, that item's info out" step MalCompanyApi does for studio facets, just resolved
//    per credited work instead of once for the whole page, since studio pages bake genre ids
//    directly into each tile and person pages don't.
class MalPeopleApi {
    private val client = NetworkClient.shared
    // Only used to fill in genre/theme/demographic/source/rating data per credited manga
    // (see enrichWithFacets below) — the author's own MAL page doesn't expose any of that,
    // same reasoning MalCompanyApi documents for why it needs Tenrai's facet id maps.
    private val tenrai = TenraiApi()

    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

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
        runCatching { parseCreditedWorks(kind, fetchDoc("https://myanimelist.net/people/$personId"), queriedName) }.getOrElse { emptyList() }
    }

    // Shared by fetchCreditedWorks above (author-search flow, needs a fresh fetch of the
    // person's page) and detail() below (already has the page in hand from parsing the
    // rest of the profile, so this just re-parses the same Document instead of re-fetching
    // it). enrichFacets defaults on to keep fetchCreditedWorks' existing behavior for the
    // author-search filter-matching path; detail() passes false since a person page is
    // just displaying these rows, not filtering them, and the facet lookups are otherwise
    // a needless network round trip per credited manga for someone with a long bibliography.
    private suspend fun parseCreditedWorks(kind: String, doc: Document, queriedName: String, enrichFacets: Boolean = true): List<MediaItem> {
        val creatorLabel = doc.selectFirst("h1.title-name strong")?.text()?.let(::reorderMalPersonName)?.takeIf { it.isNotBlank() }
        val allCreators = listOfNotNull(creatorLabel, queriedName.takeIf { it.isNotBlank() }).distinct().joinToString(", ")
        val tableClass = if (kind == "anime") "js-table-people-staff" else "js-table-people-manga"
        val rowClass = if (kind == "anime") "js-people-staff" else "js-people-manga"
        val table = doc.selectFirst("table.$tableClass") ?: return emptyList()
        val rows = table.select("tr.$rowClass").mapNotNull { row -> parseWorkRow(kind, row, creatorLabel.orEmpty(), allCreators) }
        // Same "we already have the id, just look up that item" approach as studio
        // search's facet resolution, just per-row instead of per-page: the person page
        // never carries genre/theme/demographic/source/rating data (see unknownFacets
        // below), so each credited manga's id gets looked up individually to fill it in
        // and make it actually filterable, instead of every advanced-filter check on
        // these rows being silently skipped forever.
        return if (kind == "manga" && enrichFacets) enrichWithFacets(rows) else rows
    }

    // Fan out one facet lookup per credited work. Tenrai's own getRaw() throttles/retries
    // concurrent requests, so this doesn't need its own rate limiting on top.
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
                    // Lookup succeeded, so these facets are no longer unknown for this row —
                    // if it failed, leave them flagged unknown so matches() still skips those
                    // checks for this one item instead of wrongly treating it as a non-match.
                    unknownFacets = item.unknownFacets - setOf("genres", "themes", "demographics", "source", "rating", "airingStatus"),
                )
            }
        }.awaitAll()
    }

    // Convenience wrapper: name in, manga list out.
    suspend fun searchMangaByAuthor(name: String): List<MediaItem> {
        val personId = searchPerson(name) ?: return emptyList()
        return fetchCreditedWorks("manga", personId, name)
    }

    // Discover's People tab search — same shape as MalCharacterApi.search: MAL's own
    // people search results page, filtered to rows whose first cell wraps a thumbnail
    // link straight to /people/{id}/{Slug}, so this survives a markup reskin the same way
    // MalCharacterApi/MalCompanyApi's own row selectors do.
    suspend fun search(query: String): List<PersonSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = fetchDoc("https://myanimelist.net/people.php?cat=person&q=$encoded")
            doc.select("tr:has(td div.picSurround a[href~=/people/\\d+/])").mapNotNull(::parseSearchRow)
        }.getOrElse { emptyList() }
    }

    // Full person detail page: profile fields + favorites scraped straight off the page,
    // Voice Acting Roles scraped bespoke (see parseVoiceRoleRow — the character on each row
    // is data this app has nowhere else), and Anime Staff Positions/Published Manga reusing
    // parseCreditedWorks above since those two tables are the exact same shape the
    // author-search flow already knows how to read.
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
        // e.g. "(Ono Kana)" — kept as-is (parens included), same as it reads on MAL's own row.
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
        // The portrait column (the page's own left-hand 225px cell) is where every profile
        // field lives — picture, favorite/share buttons, then the loose "Label: value" text
        // parseProfileFields reads below.
        val infoCell = doc.selectFirst("td[width=225]")
        val image = infoCell?.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fullResMalImage) ?: ""
        val (allFields, about) = infoCell?.let(::parseProfileFields) ?: (emptyList<Pair<String, String>>() to "")
        val favorites = allFields.firstOrNull { it.first.contains("favorites", ignoreCase = true) }
            ?.second?.replace(",", "")?.toIntOrNull() ?: 0
        // Shown separately with its own heart icon (see PersonDetailScreen), same as
        // CharacterDetail.favorites — so it's pulled out of the generic field list here
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

    // The person's own bio fields (Given name/Family name/Alternate names/Birthday/
    // Hometown/Blood type/Height/Skills & Abilities/Profile/Twitter/...) sit as loose
    // "Label: value" text directly in the portrait column rather than their own labeled
    // container — read from combined element text (label boundaries) the same way as
    // before. The one part of that column that *isn't* safe to read this way is the
    // "More:" block (see parseMoreBlock below), since jsoup's Element.text() drops <br>
    // entirely and flattens it into one run-on string alongside everything else — so that
    // block is carved out and parsed separately first, and its own "More:" label (now
    // pointing at nothing) drops out on its own since an empty value is discarded below.
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

    // The "More:" block (Birthplace/Blood Type/Height/etc., plus a free-text bio paragraph
    // partway through, e.g. Earphones unit membership) isn't wrapped in its own per-field
    // element the way the rest of the profile column is — MAL prints it as one flat chunk
    // with only <br> tags marking line breaks, and the bio paragraph in the middle carries
    // no "Label:" prefix at all. Reading it as a single flattened string (the old,
    // whole-column approach) let a capitalized word ending one field's value bleed into the
    // next field's own label match — e.g. Birthplace's trailing "...Japan" got swallowed
    // into what should have been just "Blood Type", producing a garbled "Japan Blood Type"
    // row — and let the unlabeled bio paragraph get appended onto whichever field happened
    // to precede it. Walking the node tree directly and turning each <br> into a real
    // newline (mirroring MalCharacterApi.parseBio) keeps every field's value scoped to its
    // own line, and lets the bio paragraph fall out cleanly into `about` instead of
    // contaminating a neighboring field — regardless of whether it sits before or after the
    // labeled fields, unlike parseBio, which assumes the free text only ever comes last.
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

    // Voice Acting Roles: one row per (anime, character) pair, four cells — anime
    // thumbnail, anime title + format/year, character name + role + favorites, character
    // thumbnail — see the class doc's HTML shape above.
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
        // e.g. "Movie, 2026" or "TV, Summer 2026"
        val workInfo = cells[1].selectFirst("div[class*=info-text]")?.text()?.trim().orEmpty()
        val charCell = cells[2]
        val charLink = charCell.selectFirst("a[href*=/character/]") ?: return null
        val characterId = Regex("/character/(\\d+)").find(charLink.attr("abs:href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val characterName = reorderMalPersonName(charLink.text().trim())
        // Second spaceit_pad in this cell is the "Main"/"Supporting" label, right below the
        // character name link and above the favorites count.
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
            format = if (kind == "manga") normalizeMangaFormatLabel(format) else format,
            nsfw = "white",
            inUserList = false,
            unknownFacets = unknownFacets,
        )
    }
}