package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The "New Anime", "Adapts Manga" ... labels MAL shows on each news item. They only exist on the website's own
 * news listing (myanimelist.net/news?p=N), not on the forum REST API, so this reads that listing and maps each
 * item's forum topic id to its tags.
 *
 * Deliberately selector-light: it only relies on link shapes seen on that page - /news/<articleId>, /news/tag/<slug>
 * (slug like "new_anime", "more_info", "winter_2027") and /forum/?topicid=<topicId>. A news item is found by walking up from
 * its discussion link to the largest ancestor that still holds a single article, and its tags are read from inside that
 * element only (so sidebar and "trending tags" links can't leak onto an item). Anything that doesn't produce a topic id is
 * dropped, and any failure just means "no tags" (the row renders without the tag line), never an error for the topic list.
 * MAL lists 20 items per page up to p=20, so only the newest ~400 news items can ever be tagged.
 */
object MalNewsTagApi {
    private val client = NetworkClient.shared
    private val articleLink = Regex("""/news/(\d+)(?:[/?#]|$)""")
    private val tagLink = Regex("""/news/tag/([A-Za-z0-9_\-]+)""")
    private val topicLink = Regex("""topicid=(\d+)""")

    private val lock = Mutex()
    private val tagsByTopic = HashMap<Int, List<String>>()
    private val pagesLoaded = HashSet<Int>()

    /** Tags for [topicIds] from the first [pages] news pages (cached; only unseen pages are fetched). */
    suspend fun tagsFor(topicIds: Collection<Int>, pages: Int): Map<Int, List<String>> {
        val wanted = (1..pages.coerceIn(1, MAX_PAGES)).filter { lock.withLock { it !in pagesLoaded } }
        if (wanted.isNotEmpty()) {
            val fetched = coroutineScope {
                wanted.map { page -> async { page to runCatching { fetchPage(page) }.getOrNull() } }.awaitAll()
            }
            lock.withLock {
                fetched.forEach { (page, map) -> if (map != null) { tagsByTopic.putAll(map); pagesLoaded.add(page) } }
            }
        }
        return lock.withLock { topicIds.mapNotNull { id -> tagsByTopic[id]?.takeIf { it.isNotEmpty() }?.let { id to it } }.toMap() }
    }

    private suspend fun fetchPage(page: Int): Map<Int, List<String>> = withContext(Dispatchers.IO) {
        val doc = client.fetchMalDocument(if (page <= 1) "https://myanimelist.net/news" else "https://myanimelist.net/news?p=$page")
        fun articleIds(el: org.jsoup.nodes.Element) = el.select("a[href]").mapNotNull { articleLink.find(it.attr("href"))?.groupValues?.get(1) }.toSet()
        val out = LinkedHashMap<Int, List<String>>()
        for (link in doc.select("a[href*=topicid=]")) {
            val topic = topicLink.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (topic in out) continue
            // The news item = the biggest ancestor of its discussion link that still contains only ONE article. Tags are
            // read from inside that element only, so sidebar / "trending tags" links elsewhere on the page can't leak in.
            var unit: org.jsoup.nodes.Element = link
            while (true) {
                val parent = unit.parent() ?: break
                if (parent.tagName() == "body" || articleIds(parent).size > 1) break
                unit = parent
            }
            if (articleIds(unit).isEmpty()) continue
            val tags = unit.select("a[href*=/news/tag/]").mapNotNull { tagLink.find(it.attr("href"))?.groupValues?.get(1)?.lowercase() }.distinct().take(MAX_TAGS)
            if (tags.isNotEmpty()) out[topic] = tags
        }
        out
    }

    private const val MAX_PAGES = 20   // MAL lists 20 per page and goes to p=20 (400 items)
    private const val MAX_TAGS = 5

    /** "new_anime" -> "New Anime". */
    fun label(slug: String): String = if (slug == "op_ed") "OP/ED" else slug.split('_', '-').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}