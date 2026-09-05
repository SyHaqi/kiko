package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val MAL = "https://myanimelist.net"

// One club in a
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
// One post in a
data class ClubPost(val username: String, val avatar: String = "", val body: String = "", val postedLabel: String = "")
data class ClubsPage(val items: List<MalClub>, val hasMore: Boolean)
data class ClubMembersPage(val items: List<ClubMember>, val hasMore: Boolean)
data class ClubPostsPage(val items: List<ClubPost>, val hasMore: Boolean)

// Scrapes MAL's Clubs pages
// feed) and is being
// StacksApi: parse real MAL
// club home page, search
class ClubsApi {
    private val client = NetworkClient.shared

    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

    // First usable image URL
    // MAL lazy-loads almost everything
    private fun imageUrl(el: Element): String {
        el.select("img").forEach { img ->
            for (attr in listOf("data-src", "src")) {
                val raw = img.attr(attr)
                if (raw.isBlank() || raw.startsWith("data:")) continue
                return fullResMalImage(img.absUrl(attr).ifBlank { raw })
            }
        }
        return ""
    }

    // Elements between a "normal_header"
    // MAL groups sidebar content
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

    // Browse/search clubs — verified
    // results page. Pagination is
    // Browse (blank query) and
    // browse is the bare
    // listing), search adds cat/catid/action/q.
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

    // Each result is a
    // cell, then a name/description/president
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

    // Club home page —
    suspend fun fetchClub(id: Int): MalClub = withContext(Dispatchers.IO) {
        val url = "$MAL/clubs.php?cid=$id"
        val doc = fetchDoc(url)
        val name = doc.selectFirst("h1.h1")?.text()?.trim().orEmpty()
        // The club's own picture
        val image = doc.selectFirst("td[width=300] img")?.let { imageUrl(it.parent() ?: it) }.orEmpty()
        val statsText = normalizeWhitespace(doc)
        val members = Regex("Members:\\s*([\\d,]+)").find(statsText)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
        // Category used to be
        // page (statsText). Its stop
        // don't actually exist once
        // single spaces, so the
        // stop and ran to
        // up as the giant
        // (<div class="spaceit_pad"><span class="dark_text">Category:</span> …)
        // instead keeps this bounded
        val category = doc.select("div.spaceit_pad").firstOrNull { it.text().trim().startsWith("Category:") }
            ?.text()?.removePrefix("Category:")?.trim().orEmpty()
        val created = Regex("Created:\\s*([A-Za-z]+ \\d{1,2}, \\d{4})").find(statsText)?.groupValues?.get(1).orEmpty()
        val access = Regex("This is a (public|private|secret) club", RegexOption.IGNORE_CASE).find(statsText)?.groupValues?.get(1)?.replaceFirstChar(Char::uppercase).orEmpty()
        // The description lives in
        // to the "Information" header
        // <div class="normal_header club-information-header">Information</div>
        // <div class="clearfix" style="white-space: pre-wrap">…description…</div>
        // An adjacent-sibling CSS selector
        // so nothing past it
        // can ever get pulled
        // hand, which is only
        // next to it in
        val description = doc.selectFirst("div.club-information-header + div.clearfix")
            ?.let { normalizeWhitespace(it) }.orEmpty().trim()
        val staff = sectionAfterHeading(doc, "Club Staff").filter { it.hasClass("borderClass") }.mapNotNull { row ->
            val a = row.selectFirst("a[href^=/profile/]") ?: return@mapNotNull null
            val role = Regex("\\(([^)]+)\\)").find(row.text())?.groupValues?.get(1).orEmpty()
            ClubStaff(username = a.text().trim(), url = a.absUrl("href"), role = role)
        }
        MalClub(id = id, name = name, url = url, image = image, members = members, category = category, access = access, created = created, description = description, staff = staff)
    }

    // Club Comments — what
    // comments already embedded on
    // hit the dedicated t=comments
    suspend fun fetchCouch(id: Int, page: Int = 1): ClubPostsPage = withContext(Dispatchers.IO) {
        val doc = if (page <= 1) fetchDoc("$MAL/clubs.php?cid=$id") else fetchDoc("$MAL/clubs.php?id=$id&action=view&t=comments&show=${(page - 1) * 10}")
        val items = parseCouch(doc)
        ClubPostsPage(items, items.size >= 10)
    }

    // Each comment lives in
    // bgColor1/bgNone — a very
    private fun parseCouch(doc: Document): List<ClubPost> {
        return doc.select("div[id~=(?i)^comment\\d+$]").mapNotNull { block ->
            val body = block.selectFirst("td.w-break") ?: return@mapNotNull null
            val header = body.selectFirst("div") // the name + timestamp
            val username = header?.selectFirst("a[href^=/profile/]")?.text()?.trim().orEmpty()
            if (username.isBlank()) return@mapNotNull null
            val postedLabel = header?.selectFirst("small")?.text()?.trim()?.removePrefix("|")?.trim().orEmpty()
            val avatar = imageUrl(block)
            // Body text is everything
            val bodyClone = body.clone()
            bodyClone.selectFirst("div")?.remove()
            val text = normalizeWhitespace(bodyClone).trim()
            ClubPost(username = username, avatar = avatar, body = text.take(600), postedLabel = postedLabel)
        }
    }

    // Full club member list.
    // row markup as the
    suspend fun fetchMembers(id: Int, page: Int = 1): ClubMembersPage = withContext(Dispatchers.IO) {
        val show = (page - 1) * 36
        val doc = fetchDoc("$MAL/clubs.php?id=$id&action=view&t=members&show=$show")
        val items = parseMembers(doc)
        // "Total Members: N" on
        val total = Regex("Total Members:\\s*([\\d,]+)").find(normalizeWhitespace(doc))?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val hasMore = if (total != null) page * 36 < total else items.size >= 30
        ClubMembersPage(items, hasMore)
    }

    // Each member sits in
    // a picSurround with their
    // climbing only as far
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