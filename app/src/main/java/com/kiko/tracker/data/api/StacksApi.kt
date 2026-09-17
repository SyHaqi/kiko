package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import com.kiko.tracker.data.model.MediaType

private const val MAL = "https://myanimelist.net"

// One row in a
data class StackSummary(
    val id: Int,
    val title: String,
    val type: String = "",
    val author: String = "",
    val description: String = "",
    val entryCount: Int = 0,
    val restacks: Int = 0,
    val updatedLabel: String = "",
    val covers: List<String> = emptyList(),
    // Display tags e.g. ["Manga",
    val tags: List<String> = emptyList(),
)
// One title inside a
// the full MediaItem is
data class StackTitleEntry(
    val malId: Int,
    val type: MediaType,
    val title: String,
    val cover: String = "",
    val format: String = "",
    val year: String = "",
    val score: Double = 0.0,
)
data class StackDetail(
    val id: Int,
    val title: String,
    val type: String = "",
    val author: String = "",
    val description: String = "",
    val restacks: Int = 0,
    val entries: List<StackTitleEntry> = emptyList(),
)

// Browse tabs, mirrors MAL's
enum class StackBrowseKind(val param: String, val label: String) {
    All("", "All"),
    Challenges("challenges", "Challenges"),
    Anime("anime", "Anime"),
    Manga("manga", "Manga"),
    MyAnimeList("myanimelist", "MyAnimeList"),
}

// Scrapes MAL's Interest Stacks
// feature (Jikan/Tenrai don't cover
// (/stacks/{id}, /anime|manga/{id}) rather than
// URL shapes are far
class StacksApi {
    private val client = NetworkClient.shared

    // Every other MAL* scraper (ClubsApi,
    // just uses the shared desktop UA
    // "Kiko/1.0" — a string that
    // self-identifies as a scraper. /stacks
    // reCAPTCHA/CMP/ad-loader scripts guarding it), so
    // treats that UA fingerprint differently there
    // trace while /stacks/search (thinner page,
    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

    // Browse or search stacks
    // still has to download
    // only keep the first
    // no reason to run
    // throw all but the
    suspend fun search(kind: StackBrowseKind, query: String = "", page: Int = 1, limit: Int? = null): List<StackSummary> = withContext(Dispatchers.IO) {
        val typeParam = if (kind.param.isBlank()) "" else "type=${kind.param}&"
        val q = if (query.isBlank()) "" else "q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&"
        val url = "$MAL/stacks/search?$typeParam${q}p=$page"
        parseSummaries(fetchDoc(url), limit)
    }

    // A page of forMedia()
    // title (from the "Showing:
    data class MediaStacksPage(val items: List<StackSummary>, val total: Int?)

    // Interest stacks that include
    // "/anime/{id}/stacks" (or "/manga/{id}/stacks") subpage,
    // that title's own detail
    // Row shape (title/author/entry-count/covers) is
    // browse/search page above that
    // only the URL and
    // per page: offset=0, 20,
    // "#total-count" counter rather than
    // parsed out — inferring
    // getting every row right
    // real end whenever the
    suspend fun forMedia(mediaId: Int, type: MediaType, offset: Int = 0, limit: Int? = null): MediaStacksPage = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val url = "$MAL/$kind/$mediaId/stacks" + if (offset > 0) "?offset=$offset" else ""
        val doc = fetchDoc(url)
        val total = doc.selectFirst("#total-count")?.attr("data-total")?.toIntOrNull()
        MediaStacksPage(parseSummaries(doc, limit), total)
    }

    // First usable image URLs
    // and lazy-load attributes (data-src)
    // Resolved via absUrl so
    private fun coverUrls(el: Element, limit: Int = 3): List<String> {
        val out = LinkedHashSet<String>()
        el.select("img").forEach { img ->
            if (out.size >= limit) return@forEach
            for (attr in listOf("data-src", "data-srcset", "src")) {
                val raw = img.attr(attr)
                if (raw.isBlank() || raw.startsWith("data:")) continue
                val url = img.absUrl(attr).substringBefore(" ").ifBlank { raw.substringBefore(" ") }
                if (url.contains("/images/anime/") || url.contains("/images/manga/")) { out.add(url); break }
            }
        }
        return out.toList()
    }

    // Flattened element text with
    // MAL renders separators like

    // Marks the end of
    // "N Entries" (e.g. "50
    // phrase depends on the
    // and already tracking that
    // 7.00" there instead. But
    // all, so the anonymous
    // those — it prints
    // has tags, a "Tags:"
    // phrase meant rowContainer never
    // really scrape, so the
    // blank even though the
    // this can take.
    private val descriptionStop = Regex("\\d+\\s+Entries|My List:|Mean Score:|Start tracking this stack!|Tags:")

    // MAL's browse/search rows render the type/Challenge badges glued
    // directly together with no separator, e.g. "AnimeChallengeby
    // MyAnimeList" or "ChallengeMangaby MyAnimeList" (badge order isn't
    // fixed, and either badge can appear alone too: "Animeby ..."). A plain
    // \b(Anime|Manga)\b regex never matches any of this because there's no
    // word boundary between "Anime"/"Manga"/"Challenge" and the "by" that
    // immediately follows — both sides are letters. Instead, first find the
    // whole glued run of badge words (anchored so it can't start mid-word,
    // e.g. inside "Animegataris"), then split that run into its individual
    // tokens. Also matches the normal spaced-out form used elsewhere (e.g.
    // stack detail pages), since a single word is a run of length one.
    private val stackBadgeRun = Regex("(?:(?<=^)|(?<=[^A-Za-z]))(?:Anime|Manga|Challenge)+(?=by|[^A-Za-z]|$)")
    private val stackBadgeToken = Regex("Anime|Manga|Challenge")

    // Shared by parseSummaries() and parsePickups()
    // "N days left" or an
    private val relativeOrDatedLabel = Regex(
        "(\\d+\\s+(?:hours?|days?|minutes?)\\s+ago|\\d+\\s+days?\\s+left|Time ended|[A-Za-z]{3}\\s+\\d{1,2},\\s*\\d{1,2}:\\d{2}\\s*[AP]M)"
    )

    // Climbs from a title
    // already contains one of
    // wrapper around this one
    // article") call proved unreliable
    // land on a wrapper
    // entirely), so climb level
    // clearly holds the full
    private fun rowContainer(a: Element, maxLevels: Int = 8): Element {
        var el: Element = a
        repeat(maxLevels) {
            if (descriptionStop.containsMatchIn(normalizeWhitespace(el))) return el
            el = el.parent() ?: return el
        }
        return el
    }

    // Title anchors that point
    private fun parseSummaries(doc: Document, limit: Int? = null): List<StackSummary> {
        val seen = LinkedHashMap<Int, StackSummary>()
        for (a in doc.select("a[href~=(?i)^https?://myanimelist\\.net/stacks/\\d+$]")) {
            if (limit != null && seen.size >= limit) break
            val id = a.attr("href").substringAfterLast("/stacks/").substringBefore("?").toIntOrNull() ?: continue
            val title = a.text().trim().takeIf { it.isNotBlank() } ?: continue
            if (seen.containsKey(id)) continue
            val container = rowContainer(a)
            val text = normalizeWhitespace(container)
            val typeTokens = stackBadgeRun.findAll(text).flatMap { run -> stackBadgeToken.findAll(run.value).map { it.value } }.toList()
            val type = typeTokens.firstOrNull { it == "Anime" || it == "Manga" }.orEmpty()
            val author = Regex("by\\s+([\\w\\-.]+)").find(text)?.groupValues?.get(1).orEmpty()
            val entryCount = Regex("(\\d+)\\s+Entries").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val restacks = Regex("(\\d+)\\s+Restacks").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            // Covers relative "N ago"/"N
            val updatedLabel = relativeOrDatedLabel.find(text)?.value.orEmpty()
            val description = if (author.isNotBlank()) {
                Regex(Regex.escape("by $author") + "\\s*(.*?)\\s*(?:${descriptionStop.pattern})", RegexOption.DOT_MATCHES_ALL)
                    .find(text)?.groupValues?.get(1)?.trim().orEmpty()
            } else ""
            val tags = listOfNotNull(type.takeIf { it.isNotBlank() }, "Challenge".takeIf { typeTokens.contains("Challenge") })
            seen[id] = StackSummary(id, title, type, author, description, entryCount, restacks, updatedLabel, coverUrls(container), tags)
        }
        return seen.values.toList()
    }

    // The 4 banner-style picks pinned
    // above "Recent Interest Stacks" on
    // (.column-1) plus three smaller ones
    // its own inline field parsing (entry
    // out to be too easy to
    // page — so this only pulls
    // reused search() to hydrate the
    // parseSummaries() already gets right for
    private fun parsePickupTitles(doc: Document): List<Pair<Int, String>> {
        val seen = LinkedHashMap<Int, String>()
        for (block in doc.select(".column-1, .column-3 .column-item")) {
            val a = block.select("a[href~=(?i)/stacks/\\d+$]").firstOrNull { it.text().isNotBlank() } ?: continue
            val id = a.attr("href").substringAfterLast("/stacks/").substringBefore("?").toIntOrNull() ?: continue
            val title = a.text().trim()
            if (title.isNotBlank()) seen.putIfAbsent(id, title)
        }
        return seen.toList()
    }

    // Fetches those 4 pickup stacks'
    // and title only, then hydrates
    // — the same summary shape that
    // works fine already) rather than
    // banner markup, which doesn't always
    suspend fun spotlight(): List<StackSummary> = withContext(Dispatchers.IO) {
        val picks = parsePickupTitles(fetchDoc("$MAL/stacks"))
        coroutineScope {
            picks.map { (id, title) ->
                async {
                    runCatching { search(StackBrowseKind.All, query = title, limit = 5) }
                        .getOrElse { emptyList() }
                        .firstOrNull { it.id == id }
                }
            }.awaitAll().filterNotNull()
        }
    }

    // Renders an element's content
    // .text() — that used
    // and drop <img>/<br> entirely,
    // description rendered as dead
    // vanished (matching the same
    // couldn't load" complaints reported
    // [url=href]text[/url], images become [img]src[/img],
    // real newline, and the
    // (ForumBody) the forums screen
    // tappable links and loadable
    private fun bbCodeFromElement(el: Element): String {
        val sb = StringBuilder()
        fun visit(node: org.jsoup.nodes.Node) {
            when (node) {
                is org.jsoup.nodes.TextNode -> sb.append(node.text())
                is Element -> when (node.tagName().lowercase()) {
                    "br" -> sb.append('\n')
                    "img" -> {
                        val attr = if (node.hasAttr("data-src")) "data-src" else "src"
                        val src = node.absUrl(attr).ifBlank { node.attr(attr) }
                        if (src.isNotBlank() && !src.startsWith("data:")) sb.append("[img]").append(src).append("[/img]")
                    }
                    "a" -> {
                        val href = node.absUrl("href").ifBlank { node.attr("href") }
                        if (href.isNotBlank()) {
                            sb.append("[url=").append(href).append(']')
                            node.childNodes().forEach(::visit)
                            sb.append("[/url]")
                        } else {
                            node.childNodes().forEach(::visit)
                        }
                    }
                    "b", "strong" -> { sb.append("[b]"); node.childNodes().forEach(::visit); sb.append("[/b]") }
                    "i", "em" -> { sb.append("[i]"); node.childNodes().forEach(::visit); sb.append("[/i]") }
                    "u" -> { sb.append("[u]"); node.childNodes().forEach(::visit); sb.append("[/u]") }
                    "p", "div", "li" -> { node.childNodes().forEach(::visit); sb.append('\n') }
                    else -> node.childNodes().forEach(::visit)
                }
                else -> {}
            }
        }
        el.childNodes().forEach(::visit)
        // Collapse the occasional run
        // the source, e.g. before
        // down to a single
        return sb.toString().lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // Full entry list for
    // covers via JS and
    // while list view ships
    suspend fun detail(stackId: Int): StackDetail = withContext(Dispatchers.IO) {
        val doc = fetchDoc("$MAL/stacks/$stackId?view_style=list")
        val title = doc.select("meta[property=og:title]").attr("content")
            .ifBlank { doc.title().substringBefore(" - Interest Stacks").trim() }
        val ogDescription = doc.select("meta[property=og:description]").attr("content")
        // "MyAnimeList - Interest Stacks
        // carries the actual stack
        val restacks = Regex("(\\d+)\\s+Restacks").find(ogDescription)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        // The byline lives in
        // <span class="mr4">by</span>AUTHOR<br>DATE | TIME_LEFT
        // no actual space character
        // markup; the visible gap
        // text against a literal
        // leaving both author and
        // marker span itself and
        // author, whether it's a
        // account, which isn't a
        val infoEl = doc.selectFirst("div.information")
        val byMarker = infoEl?.select("span")?.firstOrNull { it.ownText().trim() == "by" }
        val author = when (val sibling = byMarker?.nextSibling()) {
            is org.jsoup.nodes.TextNode -> sibling.text().trim()
            is Element -> sibling.text().trim()
            else -> ""
        }
        val bodyText = normalizeWhitespace(doc.body())
        val type = Regex("\\b(Anime|Manga)\\b").find(bodyText)?.groupValues?.get(1).orEmpty()
        // The description itself sits
        // (class="introduction"), right above the
        // no need to carve
        val description = doc.selectFirst("div.introduction")?.let(::bbCodeFromElement).orEmpty()
        StackDetail(stackId, title, type, author, description, restacks, parseEntries(doc))
    }

    // First few entry covers
    // cover images themselves (unlike
    // browse screen calls this
    suspend fun topCovers(stackId: Int, limit: Int = 3): List<String> =
        detail(stackId).entries.mapNotNull { it.cover.takeIf(String::isNotBlank) }.take(limit)

    // Title anchors inside a
    // often live under separate
    // match on the resolved
    // stack templates emit relative
    // together instead of trusting
    private fun parseEntries(doc: Document): List<StackTitleEntry> {
        data class Hit(val type: MediaType, val id: Int, val a: Element)
        val idHref = Regex("https?://myanimelist\\.net/(anime|manga)/(\\d+)")
        val hits = doc.select("a[href]").mapNotNull { a ->
            val m = idHref.find(a.absUrl("href")) ?: return@mapNotNull null
            val id = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            Hit(if (m.groupValues[1] == "anime") MediaType.Anime else MediaType.Manga, id, a)
        }
        val out = LinkedHashMap<Int, StackTitleEntry>()
        hits.groupBy { it.id }.forEach { (id, group) ->
            val title = group.map { it.a.text().trim() }.firstOrNull { it.isNotBlank() } ?: return@forEach
            // Best-effort cosmetic fields only
            // backfilled accurately from MalApi
            // NOTE: `text` also contains
            // so the format capture
            // unanchored "any letters," pattern
            // title whenever it precedes
            val text = group.joinToString(" ") { (it.a.closest("div, li, article") ?: it.a.parent() ?: it.a).text() }
            val formatYear = Regex("\\b(TV|Movie|OVA|ONA|Special|Music|Light Novel|Manga|Novel|One-shot|Doujinshi|Manhwa|Manhua|OEL)\\b,?\\s*(\\d{4})").find(text)
            val score = Regex("\\b(\\d\\.\\d{2})\\b").find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val cover = group.firstNotNullOfOrNull { coverUrls(it.a, limit = 1).firstOrNull() }.orEmpty()
            out[id] = StackTitleEntry(id, group.first().type, title, cover, formatYear?.groupValues?.get(1)?.trim().orEmpty(), formatYear?.groupValues?.get(2).orEmpty(), score)
        }
        return out.values.toList()
    }
}