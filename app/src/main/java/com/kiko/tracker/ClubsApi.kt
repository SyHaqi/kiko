package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

private const val MAL = "https://myanimelist.net"

// One club in a browse/search list
data class MalClub(
    val id: Int = 0,
    val name: String = "",
    val url: String = "",
    val image: String = "",
    val members: Int = 0,
    val category: String = "",
    val access: String = "",
    val created: String = "",
    val description: String = "",
    val staff: List<ClubStaff> = emptyList(),
)
data class ClubStaff(val username: String, val url: String = "", val role: String = "")
data class ClubMember(val username: String, val url: String = "", val image: String = "")
// One post in a club's Couch (MAL calls this "Club Comments" on desktop)
data class ClubPost(val username: String, val avatar: String = "", val body: String = "", val postedLabel: String = "")
data class ClubsPage(val items: List<MalClub>, val hasMore: Boolean)
data class ClubMembersPage(val items: List<ClubMember>, val hasMore: Boolean)
data class ClubPostsPage(val items: List<ClubPost>, val hasMore: Boolean)

// Scrapes MAL's Clubs pages directly — Jikan never exposed the Couch (comments
// feed) and is being shut down anyway, so this follows the same approach as
// StacksApi: parse real MAL HTML. Verified against real responses for the
// club home page, search results, and the full members page.
class ClubsApi {
    private val client = OkHttpClient()

    private fun fetchDoc(url: String): Document {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("MAL request failed (${resp.code})")
            return Jsoup.parse(body, url)
        }
    }

    private fun normText(el: Element): String = el.text().replace('\u00A0', ' ')

    // First usable image URL under an element (avatar, club icon, etc.)
    // MAL lazy-loads almost everything via data-src, with src only as a placeholder
    private fun imageUrl(el: Element): String {
        el.select("img").forEach { img ->
            for (attr in listOf("data-src", "src")) {
                val raw = img.attr(attr)
                if (raw.isBlank() || raw.startsWith("data:")) continue
                return img.absUrl(attr).ifBlank { raw }
            }
        }
        return ""
    }

    // Elements between a "normal_header" section heading and the next one —
    // MAL groups sidebar content (Club Staff, Anime Relations, etc.) this way
    private fun sectionAfterHeading(doc: Document, headingText: String): List<Element> {
        val heading = doc.select("div.normal_header").firstOrNull { it.text().trim() == headingText } ?: return emptyList()
        val out = mutableListOf<Element>()
        var el = heading.nextElementSibling()
        while (el != null && !(el.tagName() == "div" && el.hasClass("normal_header"))) {
            out.add(el)
            el = el.nextElementSibling()
        }
        return out
    }

    // Browse/search clubs — verified against a real clubs.php?...&action=find
    // results page. Pagination is a page number (&p=), not a row offset.
    // Browse (blank query) and search share the same table.club-list markup —
    // browse is the bare clubs.php page (MAL's "recently active" default
    // listing), search adds cat/catid/action/q. Both paginate with &p=.
    suspend fun search(query: String = "", page: Int = 1): ClubsPage = withContext(Dispatchers.IO) {
        val url = if (query.isBlank()) {
            "$MAL/clubs.php?p=$page"
        } else {
            "$MAL/clubs.php?cat=club&catid=0&action=find&p=$page&q=" + java.net.URLEncoder.encode(query, "UTF-8")
        }
        val doc = fetchDoc(url)
        val items = parseClubList(doc)
        val hasMore = doc.select(".pagination a").any { it.text().contains("More", ignoreCase = true) }
        ClubsPage(items, hasMore)
    }

    // Each result is a <tr class="table-data"> in table.club-list: a picture
    // cell, then a name/description/president cell, then members/comment/post cells
    private fun parseClubList(doc: Document): List<MalClub> {
        return doc.select("table.club-list tr.table-data").mapNotNull { row ->
            val nameLink = row.selectFirst(".informantion a.fw-b") ?: return@mapNotNull null
            val id = Regex("cid=(\\d+)").find(nameLink.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val name = nameLink.text().trim()
            val description = row.selectFirst(".informantion div.word-break")?.text()?.trim().orEmpty()
            val members = row.select("td.ac").getOrNull(0)?.text()?.trim()?.replace(",", "")?.toIntOrNull() ?: 0
            MalClub(id = id, name = name, url = nameLink.absUrl("href"), image = imageUrl(row), members = members, description = description)
        }
    }

    // Club home page — name, image, stats, description, and staff (Cabinet)
    suspend fun fetchClub(id: Int): MalClub = withContext(Dispatchers.IO) {
        val url = "$MAL/clubs.php?cid=$id"
        val doc = fetchDoc(url)
        val name = doc.selectFirst("h1.h1")?.text()?.trim().orEmpty()
        // The club's own picture is the first image in the 300px-wide right sidebar
        val image = doc.selectFirst("td[width=300] img")?.let { imageUrl(it.parent() ?: it) }.orEmpty()
        val statsText = normText(doc)
        val members = Regex("Members:\\s*([\\d,]+)").find(statsText)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
        // Category used to be pulled with a regex over the whole flattened
        // page (statsText). Its stop condition assumed "\n" boundaries that
        // don't actually exist once normText() collapses everything to
        // single spaces, so the non-greedy match never found anywhere to
        // stop and ran to the end of the entire page — that's what showed
        // up as the giant pill. Reading the specific stats row directly
        // (<div class="spaceit_pad"><span class="dark_text">Category:</span> …)
        // instead keeps this bounded to just that one line.
        val category = doc.select("div.spaceit_pad").firstOrNull { it.text().trim().startsWith("Category:") }
            ?.text()?.removePrefix("Category:")?.trim().orEmpty()
        val created = Regex("Created:\\s*([A-Za-z]+ \\d{1,2}, \\d{4})").find(statsText)?.groupValues?.get(1).orEmpty()
        val access = Regex("This is a (public|private|secret) club", RegexOption.IGNORE_CASE).find(statsText)?.groupValues?.get(1)?.replaceFirstChar(Char::uppercase).orEmpty()
        // The description lives in the div.clearfix that sits directly next
        // to the "Information" header — confirmed against MAL's real markup:
        //   <div class="normal_header club-information-header">Information</div>
        //   <div class="clearfix" style="white-space: pre-wrap">…description…</div>
        // An adjacent-sibling CSS selector pins us to exactly that element,
        // so nothing past it (Club Type text, Report Club, sidebar widgets)
        // can ever get pulled in — unlike walking .nextElementSibling() by
        // hand, which is only as safe as our assumptions about what sits
        // next to it in the parsed DOM.
        val description = doc.selectFirst("div.club-information-header + div.clearfix")
            ?.let { normText(it) }.orEmpty().trim()
        val staff = sectionAfterHeading(doc, "Club Staff").filter { it.hasClass("borderClass") }.mapNotNull { row ->
            val a = row.selectFirst("a[href^=/profile/]") ?: return@mapNotNull null
            val role = Regex("\\(([^)]+)\\)").find(row.text())?.groupValues?.get(1).orEmpty()
            ClubStaff(username = a.text().trim(), url = a.absUrl("href"), role = role)
        }
        MalClub(id = id, name = name, url = url, image = image, members = members, category = category, access = access, created = created, description = description, staff = staff)
    }

    // Club Comments — what the mobile app labels "Couch". Page 1 reads the
    // comments already embedded on the club's own home page; further pages
    // hit the dedicated t=comments view (URL/pagination unverified so far).
    suspend fun fetchCouch(id: Int, page: Int = 1): ClubPostsPage = withContext(Dispatchers.IO) {
        val doc = if (page <= 1) fetchDoc("$MAL/clubs.php?cid=$id") else fetchDoc("$MAL/clubs.php?id=$id&action=view&t=comments&show=${(page - 1) * 10}")
        val items = parseCouch(doc)
        ClubPostsPage(items, items.size >= 10)
    }

    // Each comment lives in its own <div id="commentNNNN">, alternating
    // bgColor1/bgNone — a very stable anchor to select on directly
    private fun parseCouch(doc: Document): List<ClubPost> {
        return doc.select("div[id~=(?i)^comment\\d+$]").mapNotNull { block ->
            val body = block.selectFirst("td.w-break") ?: return@mapNotNull null
            val header = body.selectFirst("div") // the name + timestamp row
            val username = header?.selectFirst("a[href^=/profile/]")?.text()?.trim().orEmpty()
            if (username.isBlank()) return@mapNotNull null
            val postedLabel = header?.selectFirst("small")?.text()?.trim()?.removePrefix("|")?.trim().orEmpty()
            val avatar = imageUrl(block)
            // Body text is everything in the cell minus the name/timestamp header row
            val bodyClone = body.clone()
            bodyClone.selectFirst("div")?.remove()
            val text = normText(bodyClone).trim()
            ClubPost(username = username, avatar = avatar, body = text.take(600), postedLabel = postedLabel)
        }
    }

    // Full club member list. URL/pagination unverified — assumes the same
    // row markup as the "Club Members" preview on the club's own home page.
    suspend fun fetchMembers(id: Int, page: Int = 1): ClubMembersPage = withContext(Dispatchers.IO) {
        val show = (page - 1) * 36
        val doc = fetchDoc("$MAL/clubs.php?id=$id&action=view&t=members&show=$show")
        val items = parseMembers(doc)
        // "Total Members: N" on the page gives an exact count when present
        val total = Regex("Total Members:\\s*([\\d,]+)").find(normText(doc))?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val hasMore = if (total != null) page * 36 < total else items.size >= 30
        ClubMembersPage(items, hasMore)
    }

    // Each member sits in its own <td class="borderClass"> — a name link, then
    // a picSurround with their avatar. Several of these tds share one <tr>, so
    // climbing only as far as the td (not the row) keeps each avatar with its
    // own username.
    private fun parseMembers(doc: Document): List<ClubMember> {
        val seen = LinkedHashMap<String, ClubMember>()
        doc.select("a[href~=(?i)^/profile/[^/?]+$], a[href~=(?i)^https?://myanimelist\\.net/profile/[^/?]+$]").forEach { a ->
            val username = a.text().trim()
            if (username.isBlank() || seen.containsKey(username)) return@forEach
            var container: Element = a
            repeat(2) { container = container.parent() ?: container }
            seen[username] = ClubMember(username = username, url = a.absUrl("href"), image = imageUrl(container))
        }
        return seen.values.toList()
    }
}