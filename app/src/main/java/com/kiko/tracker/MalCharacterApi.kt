package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val MAL = "https://myanimelist.net"

// Character search + character detail, scraped straight off MAL's own pages — same
// approach as MalPeopleApi/MalCompanyApi (no Tenrai/Jikan involved, since neither exposes
// a character search endpoint this app can rely on):
//
// 1. https://myanimelist.net/character.php?cat=character&q=<q> — MAL's own character
//    search page (the same page character.php's own "Search Characters..." box posts
//    to). Every result row's first cell links to https://myanimelist.net/character/{id}/
//    {Slug}, so rows are picked out by that link shape rather than a specific table
//    class, the same trick MalPeopleApi/MalCompanyApi use for their own search pages.
// 2. https://myanimelist.net/character/{id} — that character's own page. Its bio block
//    (Age/Birthdate/Blood Type/Height/Weight/Affiliations/Occupation/...) isn't in its
//    own container — it's loose text (with <br> line breaks) sitting directly between the
//    page's own name heading and its Voice Actors header — so it's read by walking that
//    heading's sibling nodes rather than a CSS selector. Everything up to the first blank
//    line is treated as "Label: value" bio fields; everything after that blank line is the
//    free-text biography/background/timeline.
class MalCharacterApi {
    private val client = NetworkClient.shared
    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

    suspend fun search(query: String): List<CharacterSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = fetchDoc("$MAL/character.php?cat=character&q=$encoded")
            // Every real result row's first cell wraps its thumbnail (or MAL's own
            // question-mark placeholder) in a picSurround link straight to /character/{id}/
            // — the header's own search-suggestion markup lives in <script type="text/x-
            // template"> blocks, which Jsoup never parses as real <tr> elements, so this
            // selector can't accidentally pick those up.
            doc.select("tr:has(td div.picSurround a[href~=/character/\\d+/])").mapNotNull(::parseSearchRow)
        }.getOrElse { emptyList() }
    }

    suspend fun detail(id: Int): CharacterDetail = withContext(Dispatchers.IO) {
        parseDetail(id, fetchDoc("$MAL/character/$id"))
    }

    private fun parseSearchRow(row: Element): CharacterSummary? {
        val cells = row.children()
        val picCell = cells.getOrNull(0) ?: return null
        val nameCell = cells.getOrNull(1) ?: return null
        val nameLink = nameCell.selectFirst("a[href*=/character/]") ?: return null
        val href = nameLink.attr("abs:href")
        val malId = Regex("/character/(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = nameLink.text().trim().takeIf { it.isNotBlank() } ?: return null
        // e.g. "(Hououin Kyouma, Okarin, Mad Scientist)" — kept as-is (parens included),
        // same as it reads on MAL's own results row.
        val altName = nameCell.selectFirst("small")?.text()?.trim().orEmpty()
        val image = picCell.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.contains("questionmark") }
            ?.let(::fullResMalImage) ?: ""
        val relatedWorks = cells.getOrNull(2)?.select("a")?.map { it.text().trim() }?.filter { it.isNotBlank() }.orEmpty()
        return CharacterSummary(malId = malId, name = reorderMalPersonName(name), image = image, altName = altName, relatedWorks = relatedWorks)
    }

    private fun parseDetail(id: Int, doc: Document): CharacterDetail {
        // The page's <h1> carries the full display name with the alter-ego/nickname
        // quoted inline, e.g. Rintarou "Hououin Kyouma, Okarin, Mad Scientist" Okabe —
        // used only as a fallback for name/nicknames below; the h2 (see nameHeading) is
        // the cleaner source whenever it's present.
        val h1 = doc.selectFirst("h1.title-name strong")?.text()?.trim().orEmpty()
        val quoteMatch = Regex("\"([^\"]*)\"").find(h1)
        val h1Nicknames = quoteMatch?.groupValues?.get(1)?.trim().orEmpty()
        val h1Name = h1.replace(Regex("\"[^\"]*\""), " ").replace(Regex("\\s+"), " ").trim()

        // h2's own text is already the clean "First Last" name with no quoted nicknames —
        // just the kanji name tucked in a trailing <small>(...)</small> — so it's a more
        // direct source for the title than pulling the quotes out of h1 above.
        val nameHeading = doc.selectFirst("h2.normal_header")
        val kanjiRaw = nameHeading?.selectFirst("small")?.text()?.trim().orEmpty()
        val nameKanji = kanjiRaw.removePrefix("(").removeSuffix(")").trim()
        val name = nameHeading?.ownText()?.trim()?.takeIf { it.isNotBlank() } ?: h1Name.ifBlank { "Unknown" }

        val image = doc.selectFirst("a[href*=/pics] img, img.portrait-225x350")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fullResMalImage) ?: ""

        val favorites = Regex("Member Favorites:\\s*([\\d,]+)").find(doc.text())
            ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0

        val (bioFields, about) = nameHeading?.let(::parseBio) ?: (emptyList<Pair<String, String>>() to "")

        return CharacterDetail(
            malId = id,
            name = name,
            nameKanji = nameKanji,
            nicknames = h1Nicknames,
            image = image,
            favorites = favorites,
            bioFields = bioFields,
            about = about,
            voiceActors = parseVoiceActors(doc),
            animeography = parseWorks(doc, "character-anime", "anime"),
            mangaography = parseWorks(doc, "character-manga", "manga"),
        )
    }

    // The bio block (Age/Birthdate/.../Occupation, then the free-text biography) is loose
    // text sitting directly between the name heading and the Voice Actors header — not
    // its own container — so it's read node-by-node off the heading's siblings rather
    // than by a CSS selector. <br> becomes a newline; everything else (including the
    // spoiler-tagged background/timeline sections) is flattened to its own text, since
    // this app has no interactive spoiler toggle to preserve — showing it plainly is what
    // "character biodata and everything" means here.
    private fun parseBio(nameHeading: Element): Pair<List<Pair<String, String>>, String> {
        val raw = StringBuilder()
        var node: Node? = nameHeading.nextSibling()
        while (node != null) {
            if (node is Element && node.hasClass("normal_header")) break
            when (node) {
                is TextNode -> raw.append(node.text())
                is Element -> if (node.tagName() == "br") raw.append("\n") else raw.append(node.text())
                else -> {}
            }
            node = node.nextSibling()
        }
        val lines = raw.toString().split("\n").map { it.trim() }
        val fieldLine = Regex("^([A-Z][A-Za-z ]{1,24}):\\s*(.+)$")
        val fields = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; break }
            val match = fieldLine.find(line) ?: break
            fields += match.groupValues[1].trim() to match.groupValues[2].trim()
            i++
        }
        val about = lines.drop(i).joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return fields to about
    }

    // Voice Actors: each dub is its own standalone <table> (picture + name + language),
    // one after another with no wrapper — walked as siblings of the "Voice Actors" header
    // rather than as rows of one shared table.
    private fun parseVoiceActors(doc: Document): List<CharacterVoiceActor> {
        val header = doc.select("div.normal_header").firstOrNull { it.ownText().trim() == "Voice Actors" } ?: return emptyList()
        val actors = mutableListOf<CharacterVoiceActor>()
        var sib = header.nextElementSibling()
        while (sib != null && sib.tagName() == "table") {
            val link = sib.selectFirst("a[href*=/people/]")
            if (link != null) {
                val vaId = Regex("/people/(\\d+)").find(link.attr("abs:href"))?.groupValues?.get(1)?.toIntOrNull()
                val vaName = link.text().trim()
                if (vaId != null && vaName.isNotBlank()) {
                    val image = sib.selectFirst("img")
                        ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::fullResMalImage) ?: ""
                    val language = sib.selectFirst("small")?.text()?.trim().orEmpty()
                    actors += CharacterVoiceActor(malId = vaId, name = reorderMalPersonName(vaName), image = image, language = language)
                }
            }
            sib = sib.nextElementSibling()
        }
        return actors
    }

    // Animeography/Mangaography each sit in their own table, immediately after their own
    // "div.normal_header.character-anime"/"character-manga" heading. Every row has both a
    // (empty-text) thumbnail link and the real title link pointing at the same id, plus a
    // third "edit"/"add" list-button link that also happens to contain "/anime/{id}" or
    // "/manga/{id}" in its own (ownlist) href — matching on the *canonical* work URL shape
    // is what tells the real title link apart from that button reliably.
    private fun parseWorks(doc: Document, headerClass: String, kind: String): List<CharacterWork> {
        val header = doc.selectFirst("div.normal_header.$headerClass") ?: return emptyList()
        val table = header.nextElementSibling()?.takeIf { it.tagName() == "table" } ?: return emptyList()
        val canonical = Regex("^${Regex.escape(MAL)}/$kind/(\\d+)/")
        return table.select("tr").mapNotNull { row ->
            val titleLink = row.select("a").firstOrNull { a -> a.text().isNotBlank() && canonical.containsMatchIn(a.attr("abs:href")) } ?: return@mapNotNull null
            val workId = canonical.find(titleLink.attr("abs:href"))?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val title = titleLink.text().trim()
            val image = row.selectFirst("img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
                ?.let(::fullResMalImage) ?: ""
            val role = row.selectFirst("small")?.text()?.trim()?.ifBlank { null } ?: "Main"
            CharacterWork(malId = workId, title = title, image = image, role = role)
        }
    }
}