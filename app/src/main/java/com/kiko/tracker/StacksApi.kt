package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val MAL = "https://myanimelist.net"

// One row in a stacks browse/search list
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
    // Display tags e.g. ["Manga", "Challenge"] — type plus any special badges (Challenge, MyAnimeList)
    val tags: List<String> = emptyList(),
)
// One title inside a stack — cosmetic fields are best-effort scrape;
// the full MediaItem is fetched via MalApi only when tapped (see openStackEntry)
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

// Browse tabs, mirrors MAL's own type= filter and tab order/labels
enum class StackBrowseKind(val param: String, val label: String) {
    All("", "All"),
    Challenges("challenges", "Challenges"),
    Anime("anime", "Anime"),
    Manga("manga", "Manga"),
    MyAnimeList("myanimelist", "MyAnimeList"),
}

// Scrapes MAL's Interest Stacks pages — there is no public API for this
// feature (Jikan/Tenrai don't cover it), so parsing leans on href patterns
// (/stacks/{id}, /anime|manga/{id}) rather than CSS classes, since those
// URL shapes are far less likely to change than markup/class names.
class StacksApi {
    private val client = NetworkClient.shared

    // Deliberately its own distinct UA rather than MAL_DESKTOP_USER_AGENT (see class doc).
    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url, userAgent = "Mozilla/5.0 (Android) Kiko/1.0")

    // Browse or search stacks by type. limit stops parsing (not fetching — the whole page
    // still has to download either way) once that many rows are found, for callers that
    // only keep the first one or two results anyway (e.g. Home's single-stack teaser) —
    // no reason to run every regex in parseSummaries against every row on the page just to
    // throw all but the first one away.
    suspend fun search(kind: StackBrowseKind, query: String = "", page: Int = 1, limit: Int? = null): List<StackSummary> = withContext(Dispatchers.IO) {
        val typeParam = if (kind.param.isBlank()) "" else "type=${kind.param}&"
        val q = if (query.isBlank()) "" else "q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&"
        val url = "$MAL/stacks/search?$typeParam${q}p=$page"
        parseSummaries(fetchDoc(url), limit)
    }

    // A page of forMedia() results, paired with MAL's own reported total count for that
    // title (from the "Showing: X/Y" counter at the top of the page) when it's present.
    data class MediaStacksPage(val items: List<StackSummary>, val total: Int?)

    // Interest stacks that include a given anime/manga title — MAL's own
    // "/anime/{id}/stacks" (or "/manga/{id}/stacks") subpage, the same one linked from
    // that title's own detail page ("Interest Stacks" section / its own "View All").
    // Row shape (title/author/entry-count/covers) is close enough to the general
    // browse/search page above that the shared parseSummaries below handles it as-is —
    // only the URL and paging differ. offset follows MAL's own pagination there (20 rows
    // per page: offset=0, 20, 40, ...). total comes straight from the page's own
    // "#total-count" counter rather than being inferred from how many rows this page
    // parsed out — inferring "last page" from a row count that depends on parseSummaries
    // getting every row right is exactly what let pagination stop one row short of the
    // real end whenever the scraper undercounted a page by even one row.
    suspend fun forMedia(mediaId: Int, type: MediaType, offset: Int = 0, limit: Int? = null): MediaStacksPage = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val url = "$MAL/$kind/$mediaId/stacks" + if (offset > 0) "?offset=$offset" else ""
        val doc = fetchDoc(url)
        val total = doc.selectFirst("#total-count")?.attr("data-total")?.toIntOrNull()
        MediaStacksPage(parseSummaries(doc, limit), total)
    }

    // First usable image URLs under an element, preferring MAL's own CDN paths
    // and lazy-load attributes (data-src) over a blank/placeholder src.
    // Resolved via absUrl so relative or protocol-relative srcs still load.
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

    // Flattened element text with non-breaking spaces folded to regular spaces —
    // MAL renders separators like "N Entries" with &nbsp;, which plain \s won't match

    // Marks the end of a stack's descriptive text. Browse/search rows print
    // "N Entries" (e.g. "50 Entries"). The single-stack detail page's stop
    // phrase depends on the *viewer's own* MAL session: someone logged in
    // and already tracking that stack sees "My List: 2/114" / "Mean Score:
    // 7.00" there instead. But our OkHttpClient sends no session cookie at
    // all, so the anonymous HTML we actually fetch never contains either of
    // those — it prints "Start tracking this stack!" (and, when the stack
    // has tags, a "Tags:" line before that) in their place. Missing that
    // phrase meant rowContainer never found a stop point on the page we
    // really scrape, so the description regex matched nothing and came back
    // blank even though the text was right there. Recognize all the shapes
    // this can take.
    private val descriptionStop = Regex("\\d+\\s+Entries|My List:|Mean Score:|Start tracking this stack!|Tags:")

    // Climbs from a title anchor to the nearest ancestor whose flattened text
    // already contains one of the descriptionStop markers — i.e. the tightest
    // wrapper around this one stack's card/row. A single closest("div, li,
    // article") call proved unreliable across MAL's actual markup (some rows
    // land on a wrapper that excludes the sibling cover/author/stats text
    // entirely), so climb level by level and stop at the first ancestor that
    // clearly holds the full row.
    private fun rowContainer(a: Element, maxLevels: Int = 8): Element {
        var el: Element = a
        repeat(maxLevels) {
            if (descriptionStop.containsMatchIn(normalizeWhitespace(el))) return el
            el = el.parent() ?: return el
        }
        return el
    }

    // Title anchors that point straight at a stack, deduped by id
    private fun parseSummaries(doc: Document, limit: Int? = null): List<StackSummary> {
        val seen = LinkedHashMap<Int, StackSummary>()
        for (a in doc.select("a[href~=(?i)^https?://myanimelist\\.net/stacks/\\d+$]")) {
            if (limit != null && seen.size >= limit) break
            val id = a.attr("href").substringAfterLast("/stacks/").substringBefore("?").toIntOrNull() ?: continue
            val title = a.text().trim().takeIf { it.isNotBlank() } ?: continue
            if (seen.containsKey(id)) continue
            val container = rowContainer(a)
            val text = normalizeWhitespace(container)
            val type = Regex("\\b(Anime|Manga)\\b").find(text)?.groupValues?.get(1).orEmpty()
            val author = Regex("by\\s+([\\w\\-.]+)").find(text)?.groupValues?.get(1).orEmpty()
            val entryCount = Regex("(\\d+)\\s+Entries").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val restacks = Regex("(\\d+)\\s+Restacks").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            // Covers relative "N ago"/"N left" as well as an absolute "Aug 2, 3:26 AM" timestamp fallback
            val updatedLabel = Regex(
                "(\\d+\\s+(?:hours?|days?|minutes?)\\s+ago|\\d+\\s+days?\\s+left|Time ended|[A-Za-z]{3}\\s+\\d{1,2},\\s*\\d{1,2}:\\d{2}\\s*[AP]M)"
            ).find(text)?.value.orEmpty()
            val description = if (author.isNotBlank()) {
                Regex(Regex.escape("by $author") + "\\s*(.*?)\\s*(?:${descriptionStop.pattern})", RegexOption.DOT_MATCHES_ALL)
                    .find(text)?.groupValues?.get(1)?.trim().orEmpty()
            } else ""
            // "Challenge" shows as its own badge alongside the type pill on MAL's curated stacks
            val tags = listOfNotNull(type.takeIf { it.isNotBlank() }, "Challenge".takeIf { Regex("\\bChallenge\\b").containsMatchIn(text) })
            seen[id] = StackSummary(id, title, type, author, description, entryCount, restacks, updatedLabel, coverUrls(container), tags)
        }
        return seen.values.toList()
    }

    // Renders an element's content as BBCode-flavored text instead of plain
    // .text() — that used to collapse every <a href> down to its bare label
    // and drop <img>/<br> entirely, which is why links in a stack's
    // description rendered as dead plain text and any embedded images just
    // vanished (matching the same "plain text, no hyperlink" and "image
    // couldn't load" complaints reported for forum posts). Anchors become
    // [url=href]text[/url], images become [img]src[/img], <br> becomes a
    // real newline, and the result is fed through the same BBCode renderer
    // (ForumBody) the forums screen already uses, so both surfaces get
    // tappable links and loadable images from one shared code path.
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
        // Collapse the occasional run of 3+ blank lines (consecutive <br>s in
        // the source, e.g. before "Jul/Aug 2026 Official Challenge Stack")
        // down to a single blank line separator, then trim the ends.
        return sb.toString().lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // Full entry list for one stack — force list view: tile/seasonal lazy-load
    // covers via JS and leave the <img> src empty in the raw HTML we scrape,
    // while list view ships real cdn.myanimelist.net src attributes upfront
    suspend fun detail(stackId: Int): StackDetail = withContext(Dispatchers.IO) {
        val doc = fetchDoc("$MAL/stacks/$stackId?view_style=list")
        val title = doc.select("meta[property=og:title]").attr("content")
            .ifBlank { doc.title().substringBefore(" - Interest Stacks").trim() }
        val ogDescription = doc.select("meta[property=og:description]").attr("content")
        // "MyAnimeList - Interest Stacks - 9 Entries, 14 Restacks" — this meta tag never
        // carries the actual stack description, only the entry/restack counts
        val restacks = Regex("(\\d+)\\s+Restacks").find(ogDescription)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        // The byline lives in its own dedicated element (class="information"):
        // <span class="mr4">by</span>AUTHOR<br>DATE | TIME_LEFT — note there is
        // no actual space character between "by" and the author name in the
        // markup; the visible gap is purely the mr4 margin CSS class. Matching
        // text against a literal "by " (with a space) therefore never hit,
        // leaving both author and description blank. Instead, find the "by"
        // marker span itself and read its very next sibling node — that's the
        // author, whether it's a plain text node (MAL's official "MyAnimeList"
        // account, which isn't a profile link) or an <a> (regular users).
        val infoEl = doc.selectFirst("div.information")
        val byMarker = infoEl?.select("span")?.firstOrNull { it.ownText().trim() == "by" }
        val author = when (val sibling = byMarker?.nextSibling()) {
            is org.jsoup.nodes.TextNode -> sibling.text().trim()
            is Element -> sibling.text().trim()
            else -> ""
        }
        val bodyText = normalizeWhitespace(doc.body())
        val type = Regex("\\b(Anime|Manga)\\b").find(bodyText)?.groupValues?.get(1).orEmpty()
        // The description itself sits in its own dedicated element too
        // (class="introduction"), right above the my-list/mean-score stats —
        // no need to carve it out of surrounding byline/date text at all.
        val description = doc.selectFirst("div.introduction")?.let(::bbCodeFromElement).orEmpty()
        StackDetail(stackId, title, type, author, description, restacks, parseEntries(doc))
    }

    // First few entry covers for a stack — the browse/search rows never ship
    // cover images themselves (unlike a single stack's own list view), so the
    // browse screen calls this per row to fill in a banner on demand
    suspend fun topCovers(stackId: Int, limit: Int = 3): List<String> =
        detail(stackId).entries.mapNotNull { it.cover.takeIf(String::isNotBlank) }.take(limit)

    // Title anchors inside a stack, deduped by malId. Title text and cover img
    // often live under separate sibling anchors sharing the same href, so we
    // match on the resolved absolute href (not the raw attribute — some
    // stack templates emit relative hrefs) and group every anchor for an id
    // together instead of trusting one "closest" container to hold both.
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
            // Best-effort cosmetic fields only — anything missing gets
            // backfilled accurately from MalApi the moment the entry opens.
            // NOTE: `text` also contains the title (same container as the format/year),
            // so the format capture must be anchored to a real MAL format token — an
            // unanchored "any letters," pattern will happily swallow part or all of the
            // title whenever it precedes ", <year>" in the scraped text.
            val text = group.joinToString(" ") { (it.a.closest("div, li, article") ?: it.a.parent() ?: it.a).text() }
            val formatYear = Regex("\\b(TV|Movie|OVA|ONA|Special|Music|Light Novel|Manga|Novel|One-shot|Doujinshi|Manhwa|Manhua|OEL)\\b,?\\s*(\\d{4})").find(text)
            val score = Regex("\\b(\\d\\.\\d{2})\\b").find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val cover = group.firstNotNullOfOrNull { coverUrls(it.a, limit = 1).firstOrNull() }.orEmpty()
            out[id] = StackTitleEntry(id, group.first().type, title, cover, formatYear?.groupValues?.get(1)?.trim().orEmpty(), formatYear?.groupValues?.get(2).orEmpty(), score)
        }
        return out.values.toList()
    }
}