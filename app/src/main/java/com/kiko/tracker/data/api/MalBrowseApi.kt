package com.kiko.tracker.data.api

import android.content.Context
import com.kiko.tracker.data.model.ReviewEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

/** One review from MAL's site-wide review feed, with the title it was written for. */
data class SiteReview(val review: ReviewEntry, val title: String, val malId: Int, val isManga: Boolean, val cover: String)

/** A blog entry in the feed or on a user's blog. [relations] are the anime the entry is tagged with. */
data class BlogRef(val id: Int, val title: String, val author: String, val posted: String, val comments: Int, val relations: List<Pair<Int, String>>)
data class BlogComment(val user: String, val avatar: String, val time: String, val body: String)
data class BlogPost(
    val id: Int, val title: String, val author: String, val date: String, val body: String,
    val relations: List<Pair<Int, String>>, val comments: List<BlogComment>, val canComment: Boolean,
)

/** What the signed-in user can edit on one of their own entries. */
data class BlogDraft(val id: Int, val title: String, val text: String, val privacy: Int, val commenting: Boolean, val relations: List<Pair<Int, String>>)

data class MagazineRef(val id: Int, val slug: String, val name: String, val count: Int)
data class MagazineTitle(val id: Int, val title: String, val info: String, val cover: String, val score: Double, val members: Int, val genres: String)

/**
 * Website pages that have no API: the site-wide review feed, blogs and manga magazines. Reading works without a login (the session
 * cookie is sent when there is one); writing blog entries and comments needs the website session.
 *
 *  - reviews   : reviews.php?t=anime|manga&filter_check=&filter_hide=&preliminary=on&spoiler=off&p=N, items are div.review-element (same markup as a
 *                title's review tab) plus div.titleblock a.title and div.thumb-right img.
 *  - magazines : /manga/magazine (a.genre-name-link "Name (count)"), /manga/magazine/ID/Slug lists up to 100 manga tiles.
 *  - blogs     : blog.php (feed), blog.php?eid=ID (post + table.blog_detail_comment_table), POST blogcomment.php (comment_text, subcom, blogid),
 *                POST myblog.php with entry_title / entry_privacy / entry_text / entry_commenting / animerel[] (at least one anime) and subval=1 create,
 *                edit = POST myblog.php?go=edit&id=ID with the same fields (subval=1, confirm=1), delete = same URL with subval=2, confirm=1 (all verified on a real account).
 */
class MalBrowseApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)
    private val base = "https://myanimelist.net"

    private fun get(url: String): Document {
        val builder = Request.Builder().url(url).header("User-Agent", MAL_DESKTOP_USER_AGENT)
        session.get()?.let { builder.header("Cookie", it) }
        return client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code}")
            Jsoup.parse(body, url)
        }
    }

    private fun needSession(): String = session.get() ?: throw MalSessionExpired()

    private fun csrf(doc: Document): String =
        doc.selectFirst("meta[name=csrf_token]")?.attr("content")?.takeIf { it.isNotBlank() } ?: throw IOException("Couldn't read MAL's security token")

    private fun post(url: String, form: FormBody): String {
        val cookie = needSession()
        val req = Request.Builder().url(url).post(form).header("Cookie", cookie).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()
        return client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.request.url.toString().contains("login.php")) throw MalSessionExpired()
            val doc = Jsoup.parse(text)
            doc.selectFirst(".badresult")?.text()?.trim()?.let { throw IOException(it.ifBlank { "MAL rejected that" }) }
            if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code}")
            doc.selectFirst(".goodresult")?.text()?.substringBefore("Please wait")?.trim()?.takeIf { it.isNotBlank() } ?: "Done"
        }
    }

    private fun idFrom(href: String, kind: String): Int = Regex("/$kind/(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // ---- site-wide reviews ---------------------------------------------------------------------------------------------

    suspend fun reviews(manga: Boolean, page: Int, includeSpoilers: Boolean = false): List<SiteReview> = withContext(Dispatchers.IO) {
        val kind = if (manga) "manga" else "anime"
        val doc = get("$base/reviews.php?t=$kind&filter_check=&filter_hide=&preliminary=on&spoiler=${if (includeSpoilers) "on" else "off"}&p=$page")
        val parsed = MalDetailScrapeApi().parseReviews(doc)
        val elements = doc.select("div.review-element").filter { it.selectFirst("div.text") != null }
        // parseReviews drops elements with blank text; both lists skip the same ones, so they line up index by index.
        parsed.mapIndexedNotNull { i, review ->
            val el = elements.getOrNull(i) ?: return@mapIndexedNotNull null
            val link = el.selectFirst("div.titleblock a.title") ?: return@mapIndexedNotNull null
            val cover = el.selectFirst("div.thumb-right img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }.orEmpty()
            val href = link.attr("href")
            SiteReview(review, link.text().trim(), idFrom(href, kind), manga, cover)
        }
    }

    // ---- magazines -----------------------------------------------------------------------------------------------------

    suspend fun magazines(): List<MagazineRef> = withContext(Dispatchers.IO) {
        val doc = get("$base/manga/magazine")
        doc.select("a.genre-name-link").mapNotNull { a ->
            val m = Regex("/manga/magazine/(\\d+)/([^/?#]+)").find(a.attr("href")) ?: return@mapNotNull null
            val text = a.text().trim()
            val count = Regex("\\(([\\d,]+)\\)\\s*$").find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
            MagazineRef(m.groupValues[1].toInt(), m.groupValues[2], text.replace(Regex("\\s*\\([\\d,]+\\)\\s*$"), "").trim(), count)
        }.distinctBy { it.id }
    }

    suspend fun magazineTitles(mag: MagazineRef, page: Int): List<MagazineTitle> = withContext(Dispatchers.IO) {
        val doc = get("$base/manga/magazine/${mag.id}/${mag.slug}" + if (page > 1) "?page=$page" else "")
        doc.select("div.seasonal-anime").mapNotNull { el ->
            val link = el.selectFirst("h2.h2_manga_title a.link-title") ?: el.selectFirst("a.link-title") ?: return@mapNotNull null
            val id = idFrom(link.attr("href"), "manga")
            if (id == 0) return@mapNotNull null
            val img = el.selectFirst("img")
            MagazineTitle(
                id = id, title = link.text().trim(),
                info = el.selectFirst("div.info")?.text()?.trim().orEmpty().substringBefore(" - ").take(60),
                cover = img?.let { it.attr("data-src").ifBlank { it.attr("src") } }.orEmpty(),
                score = el.selectFirst(".js-score")?.text()?.trim()?.toDoubleOrNull() ?: 0.0,
                members = el.selectFirst(".js-members")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0,
                genres = el.select("div.genres span.genre a, span.genre a").joinToString(", ") { it.text().trim() },
            )
        }.distinctBy { it.id }
    }

    // ---- blogs ---------------------------------------------------------------------------------------------------------

    private fun relationsIn(el: Element): List<Pair<Int, String>> =
        el.select("a[href*=/anime/]").mapNotNull { a ->
            val id = idFrom(a.attr("href"), "anime"); val t = a.text().trim()
            if (id == 0 || t.isBlank()) null else id to t
        }.distinctBy { it.first }

    private fun parseBlogRefs(doc: Document): List<BlogRef> {
        val seen = HashSet<Int>()
        val out = ArrayList<BlogRef>()
        for (link in doc.select("a[href*=blog.php?eid=]")) {
            val id = Regex("eid=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val title = link.text().trim()
            if (title.isBlank() || !seen.add(id)) continue
            // The smallest ancestor that holds this entry's "Posted ... by ..." line and no other entry.
            var box: Element? = link.parent()
            var depth = 0
            while (box != null && depth < 6) {
                val ids = box.select("a[href*=blog.php?eid=]").mapNotNull { Regex("eid=(\\d+)").find(it.attr("href"))?.groupValues?.get(1) }.toSet()
                if (ids.size == 1 && box.selectFirst(".lightLink")?.text()?.contains("Posted") == true) break
                box = box.parent(); depth++
            }
            if (box == null || depth >= 6) continue
            val meta = box.selectFirst(".lightLink")!!.text()
            val author = box.selectFirst(".lightLink a[href*=/profile/]")?.text()?.trim().orEmpty()
            val comments = Regex("\\((\\d+) comments?\\)").find(meta)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val posted = meta.substringAfter("Posted").substringBefore(" by ").trim()
            out += BlogRef(id, title, author, posted, comments, relationsIn(box))
        }
        return out
    }

    suspend fun blogFeed(): List<BlogRef> = withContext(Dispatchers.IO) { parseBlogRefs(get("$base/blog.php")) }

    suspend fun userBlog(username: String): List<BlogRef> = withContext(Dispatchers.IO) { parseBlogRefs(get("$base/blog/$username")) }

    suspend fun blogPost(id: Int): BlogPost = withContext(Dispatchers.IO) {
        val doc = get("$base/blog.php?eid=$id")
        val content = doc.selectFirst(".blog_detail_content_wrapper") ?: throw IOException("Couldn't find the blog entry. It may have been removed or made private.")
        val title = doc.selectFirst("div.lightLink")?.text()?.trim().orEmpty().ifBlank { doc.title().substringBefore(" - ").trim() }
        val date = doc.selectFirst("div.normal_header")?.text()?.trim().orEmpty()
        val cap = doc.selectFirst(".blog_detail_cap_wrapper")?.text().orEmpty()
        val author = Regex("Posted by\\s+(\\S+)").find(cap)?.groupValues?.get(1)?.trim().orEmpty()
        val body = content.clone()
        body.select("br").forEach { it.after("\n") }
        body.select("p, div").forEach { it.after("\n") }
        val rel = doc.select("div").firstOrNull { it.ownText().contains("Anime Relations") }?.let { relationsIn(it) }.orEmpty()
        val comments = doc.select("table.blog_detail_comment_table").mapNotNull { t ->
            val user = t.selectFirst(".dark_text a, a[href*=/profile/]")?.text()?.trim().orEmpty()
            if (user.isBlank()) return@mapNotNull null
            val time = t.selectFirst(".dark_text small, small")?.text()?.removePrefix("|")?.trim().orEmpty()
            val avatarImg = t.selectFirst(".picSurround img")
            val avatar = avatarImg?.let { it.attr("data-src").ifBlank { it.attr("src") } }.orEmpty()
            val text = t.clone()
            text.select(".picSurround, .dark_text, small, a.fl-r, .spaceit").remove()
            text.select("br").forEach { it.after("\n") }
            BlogComment(user, avatar, time, text.wholeText().trim().ifBlank { t.text().substringAfter(time).trim() })
        }
        BlogPost(id, title, author, date, body.wholeText().replace(Regex("\n{3,}"), "\n\n").trim(), rel, comments, canComment = doc.selectFirst("form[action*=blogcomment]") != null || doc.selectFirst("textarea[name=comment_text]") != null)
    }

    /** Not live-tested: posts to MAL's own comment form the same way the page does. */
    suspend fun postBlogComment(blogId: Int, text: String): Unit = withContext(Dispatchers.IO) {
        val doc = get("$base/blog.php?eid=$blogId")
        post("$base/blogcomment.php", FormBody.Builder().add("comment_text", text).add("subcom", "Submit").add("blogid", blogId.toString()).add("csrf_token", csrf(doc)).build())
    }

    /** Searches anime for the "related anime" picker (MAL's own autocomplete endpoint). */
    suspend fun searchAnime(query: String): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("myanimelist.net").addPathSegments("includes/ajax.inc.php").addQueryParameter("t", "5").addQueryParameter("a", query).build()
        val doc = get(url.toString())
        doc.select("a[href*=/anime/]").mapNotNull { a ->
            val id = idFrom(a.attr("href"), "anime"); val t = a.text().trim()
            if (id == 0 || t.isBlank()) null else id to t
        }.distinctBy { it.first }.take(10)
    }

    /** Loads one of the user's own entries into the editor. */
    suspend fun loadDraft(id: Int): BlogDraft = withContext(Dispatchers.IO) {
        needSession()
        val doc = get("$base/myblog.php?go=edit&id=$id")
        val form = doc.selectFirst("form#blogForm") ?: throw IOException("Couldn't open that entry for editing")
        BlogDraft(
            id = id,
            title = form.selectFirst("input[name=entry_title]")?.attr("value").orEmpty(),
            text = form.selectFirst("textarea[name=entry_text]")?.wholeText().orEmpty().trim('\n'),
            privacy = form.selectFirst("select[name=entry_privacy] option[selected]")?.attr("value")?.toIntOrNull() ?: 1,
            commenting = form.selectFirst("input[name=entry_commenting]")?.hasAttr("checked") ?: true,
            relations = form.select("input[name=\"animerel[]\"]").map { input ->
                val aid = input.attr("value").toIntOrNull() ?: 0
                aid to (input.parent()?.ownText()?.trim().orEmpty().ifBlank { "Anime $aid" })
            }.filter { it.first != 0 },
        )
    }

    /** Creates a new entry ([id] == 0) or updates one. MAL requires at least one related anime. Returns MAL's own confirmation. */
    suspend fun saveBlog(id: Int, title: String, text: String, privacy: Int, commenting: Boolean, relations: List<Int>): String = withContext(Dispatchers.IO) {
        needSession()
        if (relations.isEmpty()) throw IOException("Pick at least one related anime. MAL requires it for every blog entry.")
        val doc = get(if (id == 0) "$base/myblog.php" else "$base/myblog.php?go=edit&id=$id")
        val form = FormBody.Builder()
            .add("entry_title", title).add("entry_privacy", privacy.toString()).add("entry_text", text)
            .add("subval", "1").add("confirm", "1").add("csrf_token", csrf(doc))
        if (commenting) form.add("entry_commenting", "1")
        relations.forEach { form.add("animerel[]", it.toString()) }
        post(if (id == 0) "$base/myblog.php" else "$base/myblog.php?go=edit&id=$id", form.build())
    }

    suspend fun deleteBlog(id: Int): Unit = withContext(Dispatchers.IO) {
        needSession()
        val doc = get("$base/myblog.php?go=edit&id=$id")
        post("$base/myblog.php?go=edit&id=$id", FormBody.Builder().add("subval", "2").add("confirm", "1").add("csrf_token", csrf(doc)).build())
    }
}