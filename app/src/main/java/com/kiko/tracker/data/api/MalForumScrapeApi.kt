package com.kiko.tracker.data.api

import com.kiko.tracker.ui.components.youTubeIdFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reads a forum topic straight off myanimelist.net's own page — not the official REST API
 * (MalApi.forumTopic). Exists because that REST endpoint has shown real-world staleness on cold
 * loads: opening a topic that already has replies visible on the website can come back from
 * `/v2/forum/topic/{id}` missing some of them, independent of anything this app does (confirmed
 * against a live devtools capture — see the ForumTopicScreen/MalForumReplyApi conversation this
 * was built from). The website itself is the source of truth MAL's own users see, so reading
 * from it directly sidesteps that caching layer entirely for the moment freshness matters most:
 * a topic's very first load.
 *
 * Deliberately scoped to ONLY the first page of a topic (offset 0) — this is built off one real
 * captured page, which only had a single page of posts, so there's no verified selector for
 * MAL's pagination controls on a long topic. [hasMore] is computed instead from a value that
 * *is* verified from that capture (the `#totalReplies` hidden input) compared against how many
 * posts actually came back on this page — if there's more than a page's worth, hasMore is true,
 * and callers should fall back to MalApi.forumTopic's own offset-based paging (already correct
 * and already in production) to fetch the rest. Callers should also treat a thrown exception or
 * empty result here as "fall back to MalApi.forumTopic", not as "the topic is truly empty" —
 * this is a bonus freshness path, not a replacement for the REST API's reliability.
 *
 * Known gaps, deliberately not guessed at: polls aren't parsed (this topic didn't have one, so
 * there's no verified poll markup to match against) and author.id is always 0 (no per-post
 * numeric user id appears in this markup, only the username in profile link hrefs).
 *
 * IMPORTANT — body/signature format: MalApi.forumTopic (the official REST endpoint) hands back
 * the post's raw BBCode source in its "body" field (e.g. literal "[img]...[/img]",
 * "[quote]...[/quote]" text) — that's what ForumBody/parseBBCode (BBCodeParser.kt) is built to
 * read, and it's also exactly what gets typed into MalForumReplyApi.postReply's messageText.
 * This page, by contrast, is myanimelist.net's own already-rendered HTML for the post (real
 * <img>/<a>/<blockquote> tags, not bracket tags) — feeding that straight into parseBBCode is why
 * images/quotes/gifs/formatting silently failed to render before. htmlToBb() below walks that
 * rendered HTML and re-emits it as the same bracket-tag string the REST path produces, so every
 * post — scraped or REST — flows through one shared rendering pipeline from here on.
 *
 * htmlToBb is reverse-engineered off captured pages, so treat its tag coverage as best-effort: see its own
 * doc comment for exactly what is mapped (formatting, colour/size, alignment, links, images, YouTube,
 * lists, code, quotes with authors and spoilers).
 */
class MalForumScrapeApi {
    private val client = NetworkClient.shared

    suspend fun topic(topicId: Int): ForumTopicDetail = withContext(Dispatchers.IO) {
        val doc = client.fetchMalDocument("https://myanimelist.net/forum/?topicid=$topicId")
        val title = doc.selectFirst("h1.forum_locheader")?.text().orEmpty()
        val posts = doc.select("div.forum-topic-message.message[data-id]").mapNotNull(::parsePost)
        val totalReplies = doc.selectFirst("input#totalReplies")?.attr("value")?.toIntOrNull() ?: 0
        // +1 for the original post, which isn't counted in totalReplies.
        val hasMore = posts.size < totalReplies + 1
        ForumTopicDetail(title = title, posts = posts, poll = null, hasMore = hasMore, locked = isLockedPage(doc))
    }

    // MAL swaps the reply box for a "locked" notice on locked topics. Looked for outside the posts themselves so a post that
    // merely says "this topic is locked" can't trip it.
    private fun isLockedPage(doc: org.jsoup.nodes.Document): Boolean {
        val copy = doc.clone()
        copy.select("div.forum-topic-message").remove()
        if (copy.select("[class*=locked], i.fa-lock, .icon-lock").isNotEmpty()) return true
        val text = copy.body().text().lowercase()
        return "this topic is locked" in text || "topic has been locked" in text || "this topic is closed" in text
    }

    private fun parsePost(el: Element): ForumPost? {
        val id = el.attr("data-id").toIntOrNull() ?: return null
        val number = el.selectFirst("div.postnum")?.attr("data-postnum")?.toIntOrNull() ?: 0
        val epochSeconds = el.selectFirst("div.message-header div.date")?.attr("data-time")?.toLongOrNull()
        val createdAt = epochSeconds?.let(::isoUtc).orEmpty()
        val authorName = el.selectFirst("div.profile div.username a")?.text()?.trim().orEmpty()
        val avatar = el.selectFirst("div.profile a.forum-icon img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            .orEmpty()
        val body = el.selectFirst("div.content table.body td")?.let(::htmlToBb).orEmpty()
        val signature = el.selectFirst("div.sig-container table.sig td")?.let(::htmlToBb).orEmpty()
        if (body.isBlank()) return null
        // "Reply to X" block MAL renders above a post that has a parent — sits alongside (not
        // inside) the message table above, so it doesn't interfere with the body select above.
        // js-replyto-target's text is literally "Reply to <name>"; strip that prefix down to
        // just the name so callers don't have to know MAL's exact phrasing.
        val repliedEl = el.selectFirst("div.replied")
        val replyToAuthor = repliedEl?.selectFirst(".js-replyto-target")?.text()?.removePrefix("Reply to")?.trim().orEmpty()
        val replyToBody = repliedEl?.selectFirst(".replied-body")?.text()?.trim().orEmpty()
        return ForumPost(
            id = id, number = number, createdAt = createdAt,
            author = ForumUser(name = authorName, avatar = avatar), body = body, signature = signature,
            replyToAuthor = replyToAuthor, replyToBody = replyToBody,
        )
    }

    // Reformats a unix timestamp into the same "yyyy-MM-dd'T'HH:mm:ssXXX" shape MalApi.forumTopic's
    // own created_at values already come in, so formatForumDate() in ForumsScreen.kt keeps working
    // unmodified regardless of which of the two sources a given ForumPost came from.
    private fun isoUtc(epochSeconds: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochSeconds * 1000))
    }

    // Block-level tags get a paragraph break after their content so the renderer's blank-line
    // splitting (BBCodeParser.kt) still separates them the way it would separate two real
    // BBCode paragraphs. Extra blank lines this produces are harmless — the renderer already
    // trims/filters blank segments.
    private val blockLevelTags = setOf("div", "p", "tr", "table", "tbody", "td", "section", "figure", "figcaption", "article", "aside", "h1", "h2", "h3", "h4", "h5", "h6")
    private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    private val styleColorRegex = Regex("""(?:^|;)color:([^;]+)""")
    private val styleSizeRegex = Regex("""font-size:(\d+)%""")
    private val styleWidthRegex = Regex("""(?:^|;)width:(\d+)px""")
    private val rgbRegex = Regex("""rgba?\((\d+),(\d+),(\d+)""")

    // "rgb(255,0,0)" -> "#FF0000"; named colours and #hex values pass through untouched.
    private fun cssColorToBb(raw: String): String? {
        val v = raw.trim().lowercase()
        if (v.isBlank() || v == "inherit" || v == "initial" || v == "transparent") return null
        rgbRegex.find(v)?.let { m ->
            val (r, g, b) = m.destructured
            return "#%02X%02X%02X".format(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
        }
        return v
    }

    // Relative links ("/profile/x", "/anime/1/y") only make sense on myanimelist.net.
    private fun absoluteHref(raw: String): String {
        val href = raw.trim()
        return if (href.startsWith("/") && !href.startsWith("//")) "https://myanimelist.net$href" else href
    }

    // Walks MAL's already-rendered post/signature HTML and re-emits it as the bracket-tag BBCode
    // string parseBBCode (BBCodeParser.kt) reads. Covered: bold / italic / underline / strike
    // (tags and inline styles), colour and percentage font-size spans, centre / right alignment,
    // links, images (small ones keep a width hint), YouTube embeds, lists, headings, rules, code
    // blocks, quotes (with the "Name said:" author) and spoilers.
    //
    // Still not handled because no verified sample exists: emoticon-specific markup (a small
    // image just becomes a small [img]) and polls.
    // articleMode: used for Featured Article bodies. Headings become larger bold text (instead of just bold)
    // and image URLs go through mapImage (full-resolution rewrite).
    internal fun htmlToBb(root: Element, articleMode: Boolean = false, mapImage: (String) -> String = { it }): String {
        val sb = StringBuilder()

        fun visit(node: Node) {
            when (node) {
                is TextNode -> {
                    // Jsoup's text() collapses the newline after a <br /> into a space, which would leave
                    // every line after a line break starting with a stray indent. Drop it like a browser does.
                    val t = node.text()
                    sb.append(if (sb.isEmpty() || sb.last() == '\n') t.trimStart() else t)
                }
                is Element -> {
                    val tag = node.tagName().lowercase()
                    val style = node.attr("style").replace(" ", "").lowercase()
                    // --- elements that are fully handled on their own -------------------------
                    when {
                        tag == "br" -> {
                            while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
                            sb.append("\n"); return
                        }
                        tag == "hr" -> { sb.append("\n[hr]\n"); return }
                        // Spoiler buttons, scripts, style blocks: nothing to show.
                        tag == "input" || tag == "script" || tag == "style" || tag == "button" -> return
                        tag == "iframe" -> {
                            val id = youTubeIdFrom(node.attr("data-src").ifBlank { node.attr("src") })
                            if (id != null) sb.append("\n[yt]").append(id).append("[/yt]\n")
                            return
                        }
                        tag == "img" -> {
                            val raw = node.attr("data-src").ifBlank { node.attr("src") }.trim()
                            val src = mapImage(node.absUrl("data-src").ifBlank { node.absUrl("src") }.ifBlank { raw })
                            if (src.isNotBlank()) {
                                val width = node.attr("width").toIntOrNull() ?: styleWidthRegex.find(style)?.groupValues?.get(1)?.toIntOrNull()
                                if (width != null && width > 0) sb.append("[img=").append(width).append("]") else sb.append("[img]")
                                sb.append(src).append("[/img]")
                            }
                            return
                        }
                        tag == "pre" || node.hasClass("codetext") -> {
                            val code = node.wholeText().trim('\n', '\r')
                            if (code.isNotBlank()) sb.append("\n[code]").append(code).append("[/code]\n")
                            return
                        }
                        node.hasClass("spoiler") -> {
                            val content = node.selectFirst(".spoiler_content")
                            val button = node.selectFirst("input.show_button")
                            val label = button?.let { it.attr("data-showname").ifBlank { it.attr("value") } }
                                ?.removePrefix("Show ")?.trim().orEmpty()
                            val body = content ?: node
                            val labelled = label.isNotBlank() && !label.equals("spoiler", ignoreCase = true)
                            // Plain text inside an unlabelled spoiler is an inline spoiler: keep it in the sentence it was
                            // written in (no forced line breaks). Anything else stays its own block.
                            val blockSel = "div, blockquote, ul, ol, img, iframe, table, pre, .spoiler"
                            val inline = !labelled && body.children().none { it.tagName() in setOf("div", "blockquote", "ul", "ol", "img", "iframe", "table", "pre") || it.select(blockSel).isNotEmpty() }
                            sb.append(if (inline) "[spoiler" else "\n[spoiler")
                            if (labelled) sb.append("=").append(label)
                            sb.append("]")
                            body.childNodes().forEach(::visit)
                            sb.append(if (inline) "[/spoiler]" else "[/spoiler]\n")
                            return
                        }
                        tag == "blockquote" || node.hasClass("quotetext") -> {
                            // MAL puts "<strong>Name said:</strong><br>" at the top of a quote.
                            val kids = node.childNodes().toMutableList()
                            var author = ""
                            val first = kids.firstOrNull { !(it is TextNode && it.isBlank) }
                            if (first is Element && first.tagName().equals("strong", ignoreCase = true) && first.text().trim().endsWith("said:")) {
                                author = first.text().trim().removeSuffix("said:").trim()
                                val idx = kids.indexOf(first)
                                kids.removeAt(idx)
                                // the <br> right after the author line
                                val next = kids.getOrNull(idx)
                                if (next is Element && next.tagName().equals("br", ignoreCase = true)) kids.removeAt(idx)
                            }
                            sb.append("\n[quote")
                            if (author.isNotBlank()) sb.append("=").append(author)
                            sb.append("]")
                            kids.forEach(::visit)
                            sb.append("[/quote]\n")
                            return
                        }
                        tag == "a" -> {
                            val href = absoluteHref(node.attr("href"))
                            if (href.contains("tenor.com/view/", ignoreCase = true) && node.text().trim() == href) {
                                // Emitted as an unadorned [url] so normalizeMalMarkup's tenor rule still inlines the GIF.
                                sb.append("[url]").append(href).append("[/url]")
                                return
                            }
                        }
                    }
                    // --- wrapper elements: open tags, children, close tags --------------------
                    val opens = ArrayList<String>(4)
                    val closes = ArrayList<String>(4)
                    fun wrap(open: String, close: String) { opens += open; closes += close }

                    if (tag == "a") {
                        val href = absoluteHref(node.attr("href"))
                        if (href.isNotBlank()) wrap("[url=$href]", "[/url]")
                    }
                    if (tag in headingTags && articleMode) wrap("\n[size=${if (tag == "h1" || tag == "h2") 130 else 115}][b]", "[/b][/size]")
                    if (tag == "figcaption" && articleMode) wrap("[size=88]", "[/size]")
                    if (tag == "b" || tag == "strong" || (tag in headingTags && !articleMode) || style.contains("font-weight:bold") || style.contains("font-weight:700")) wrap("[b]", "[/b]")
                    if (tag == "i" || tag == "em" || style.contains("font-style:italic")) wrap("[i]", "[/i]")
                    if (tag == "u" || style.contains("text-decoration:underline")) wrap("[u]", "[/u]")
                    if (tag == "s" || tag == "strike" || tag == "del" || style.contains("line-through")) wrap("[s]", "[/s]")
                    styleColorRegex.find(style)?.groupValues?.get(1)?.let(::cssColorToBb)?.let { wrap("[color=$it]", "[/color]") }
                    styleSizeRegex.find(style)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it != 100 }?.let { wrap("[size=$it]", "[/size]") }
                    when {
                        style.contains("text-align:center") || tag == "center" -> wrap("\n[center]", "[/center]\n")
                        style.contains("text-align:right") -> wrap("\n[right]", "[/right]\n")
                    }
                    when (tag) {
                        "li" -> wrap("[*]", "\n")
                        "ol" -> wrap("\n[list=1]\n", "[/list]\n")
                        "ul" -> wrap("\n[list]\n", "[/list]\n")
                    }

                    opens.forEach { sb.append(it) }
                    node.childNodes().forEach(::visit)
                    for (i in closes.indices.reversed()) sb.append(closes[i])
                    if (tag in blockLevelTags) sb.append("\n\n")
                }
                else -> {}
            }
        }

        root.childNodes().forEach(::visit)
        return sb.toString()
    }
}