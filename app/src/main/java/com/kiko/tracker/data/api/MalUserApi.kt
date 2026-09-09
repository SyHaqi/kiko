package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import com.kiko.tracker.data.model.UserSearchFilters
import com.kiko.tracker.data.model.UserSummary

// Discover's Users tab — MAL's own user search page
// (https://myanimelist.net/users.php?cat=user), the same non-ajax
// results-page family as MalCompanyApi/MalPeopleApi's own search().
//
// The markup shape is different from those two, though: instead of one
// <tr> per result (thumbnail cell + name cell), MAL lays this page out
// as a plain table where each <td class="borderClass"> IS one result —
// username link on top, avatar below it, "joined" timestamp at the
// bottom — six td's to a row. parseSearchCell below reads one of those
// cells rather than a whole row.
//
// Also unlike Company/People search, this page paginates via a
// "show=N" byte offset (24 results per page, 0-indexed) instead of
// returning everything in one shot — see page() and its hasMore check
// against the page's own "[1] 2 3 ... 20" links.
//
// Supports the same "Advanced Search" fields shown on the page itself
// (Location/Age range/Gender) via UserSearchFilters.
class MalUserApi {
    private val client = NetworkClient.shared
    private fun fetchDoc(url: String): Document = client.fetchMalDocument(url)

    data class Page(val users: List<UserSummary>, val hasMore: Boolean)

    companion object {
        // MAL's own page size for users.php (confirmed by the
        // show=24/48/... links on the results page).
        private const val PAGE_SIZE = 24
    }

    suspend fun search(query: String, filters: UserSearchFilters = UserSearchFilters(), page: Int = 0): Page = withContext(Dispatchers.IO) {
        runCatching {
            val params = mutableListOf("cat=user")
            if (query.isNotBlank()) params += "q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            if (filters.location.isNotBlank()) params += "loc=${java.net.URLEncoder.encode(filters.location, "UTF-8")}"
            filters.ageLow?.let { params += "agelow=$it" }
            filters.ageHigh?.let { params += "agehigh=$it" }
            // MAL's own g= values: 1 Male, 2 Female, 3 Non-Binary — blank
            // (param omitted entirely) is "Don't care".
            when (filters.gender) {
                "Male" -> params += "g=1"
                "Female" -> params += "g=2"
                "Non-Binary" -> params += "g=3"
            }
            if (page > 0) params += "show=${page * PAGE_SIZE}"
            val doc = fetchDoc("https://myanimelist.net/users.php?" + params.joinToString("&"))
            val users = doc.select("td.borderClass").mapNotNull(::parseSearchCell)
            // The numbered page-link row ([1] 2 3 ... 20) tells us
            // whether a further page exists — more reliable than
            // assuming a full PAGE_SIZE result means there's more,
            // since the very last page can also happen to be full.
            val hasMore = doc.select("a[href*=show=]").any { link ->
                Regex("show=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()?.let { it > page * PAGE_SIZE } == true
            }
            Page(users, hasMore)
        }.getOrElse { Page(emptyList(), false) }
    }

    private fun parseSearchCell(cell: Element): UserSummary? {
        val link = cell.selectFirst("div.picSurround a[href*=/profile/]") ?: return null
        val rawUsername = Regex("/profile/([^/?#]+)").find(link.attr("abs:href"))?.groupValues?.get(1) ?: return null
        val username = runCatching { java.net.URLDecoder.decode(rawUsername, "UTF-8") }.getOrDefault(rawUsername)
        // MAL serves a shared kaomoji placeholder for users with no
        // custom avatar (see broodkyle10/kyle1424/... in a real results
        // page) — treat that the same as Company/People's own
        // "questionmark" placeholder check and just leave it blank so
        // the row falls back to its own initial-letter avatar.
        val avatarUrl = link.selectFirst("img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.takeIf { it.isNotBlank() && !it.contains("kaomoji") }
            ?.let(::fullResMalImage) ?: ""
        val joined = cell.selectFirst("div.spaceit_pad.lightLink small")?.text()?.trim().orEmpty()
        return UserSummary(username = username, avatarUrl = avatarUrl, joined = joined)
    }
}