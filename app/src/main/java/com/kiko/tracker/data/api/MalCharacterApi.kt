package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import com.kiko.tracker.data.model.CharacterDetail
import com.kiko.tracker.data.model.CharacterSummary
import com.kiko.tracker.data.model.CharacterVoiceActor
import com.kiko.tracker.data.model.CharacterWork

private const val MAL = "https://myanimelist.net"

// Character search + character
// approach as MalPeopleApi/MalCompanyApi (no
// a character search endpoint
//
// 1. https://myanimelist.net/character.php?cat=character&q=<q> — MAL's
// search page (the same
// to). Every result row's
// {Slug}, so rows are
// class, the same trick
// 2. https://myanimelist.net/character/{id} — that
// (Age/Birthdate/Blood Type/Height/Weight/Affiliations/Occupation/...) isn't in
// own container — it's
// page's own name heading
// heading's sibling nodes rather
// line is treated as
// free-text biography/background/timeline.
class MalCharacterApi {
    private val client = NetworkClient.shared
    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

    suspend fun search(query: String): List<CharacterSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = fetchDoc("$MAL/character.php?cat=character&q=$encoded")
            // Every real result row's
            // question-mark placeholder) in a
            // — the header's own
            // template"> blocks, which Jsoup
            // selector can't accidentally pick
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
        // e.g. "(Hououin Kyouma, Okarin,
        // same as it reads
        val altName = nameCell.selectFirst("small")?.text()?.trim().orEmpty()
        val image = picCell.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.contains("questionmark") }
            ?.let(::fullResMalImage) ?: ""
        val relatedWorks = cells.getOrNull(2)?.select("a")?.map { it.text().trim() }?.filter { it.isNotBlank() }.orEmpty()
        return CharacterSummary(malId = malId, name = reorderMalPersonName(name), image = image, altName = altName, relatedWorks = relatedWorks)
    }

    private fun parseDetail(id: Int, doc: Document): CharacterDetail {
        // The page's <h1> carries
        // quoted inline, e.g. Rintarou
        // used only as a
        // the cleaner source whenever
        val h1 = doc.selectFirst("h1.title-name strong")?.text()?.trim().orEmpty()
        val quoteMatch = Regex("\"([^\"]*)\"").find(h1)
        val h1Nicknames = quoteMatch?.groupValues?.get(1)?.trim().orEmpty()
        val h1Name = h1.replace(Regex("\"[^\"]*\""), " ").replace(Regex("\\s+"), " ").trim()

        // h2's own text is
        // just the kanji name
        // direct source for the
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

    // The bio block (Age/Birthdate/.../Occupation,
    // text sitting directly between
    // its own container —
    // than by a CSS
    // spoiler-tagged background/timeline sections) is
    // this app has no
    // "character biodata and everything"
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

    // Voice Actors: each dub
    // one after another with
    // rather than as rows
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

    // Animeography/Mangaography each sit in
    // "div.normal_header.character-anime"/"character-manga" heading. Every row
    // (empty-text) thumbnail link and
    // third "edit"/"add" list-button link
    // "/manga/{id}" in its own
    // is what tells the
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