package com.kiko.tracker.data.api

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
 * htmlToBb is necessarily reverse-engineered off the same single captured page as the rest of
 * this file, so treat its tag coverage as best-effort, not exhaustive:
 *  - [b]/[i]/[u]/[s], [url=...], [img], [list]/[list=1], [quote], and centered blocks are mapped.
 *  - A bare tenor.com/view link (href text == its own URL) is emitted as an unadorned
 *    "[url]...[/url]" rather than "[url=...]...[/url]" specifically so normalizeMalMarkup's
 *    bareTenorLinkRegex still catches it and inlines the actual GIF, same as it already does for
 *    REST-sourced posts.
 *  - Not handled, because no verified sample exists: emoticon/smilie icons (any <img> — including
 *    a tiny smiley — becomes a full [img] block, same visual weight as a real embedded picture),
 *    quote author-attribution headers ("username said:" — only the quoted text itself survives),
 *    and [spoiler] (parseBBCode has no spoiler support at all yet, even on the REST path, so this
 *    just matches existing behavior rather than introducing a new gap).
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
        ForumTopicDetail(title = title, posts = posts, poll = null, hasMore = hasMore)
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

    // Block-level tags get a paragraph break after their content so paragraphsFrom's blank-line
    // splitting (BBCodeParser.kt) still separates them the way it would separate two real
    // BBCode paragraphs. Extra blank lines this produces are harmless — that same function
    // already trims/filters blank segments.
    private val blockLevelTags = setOf("div", "p", "tr", "table", "tbody", "td", "section", "h1", "h2", "h3", "h4", "h5", "h6")

    // Walks MAL's already-rendered post/signature HTML and re-emits it as the bracket-tag BBCode
    // string parseBBCode (BBCodeParser.kt) already knows how to read — see this file's class doc
    // comment for exactly which tags are covered and which known gaps are left unguessed-at.
    private fun htmlToBb(root: Element): String {
        val sb = StringBuilder()

        fun visit(node: Node) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    val tag = node.tagName().lowercase()
                    val style = node.attr("style").replace(" ", "").lowercase()
                    when {
                        tag == "br" -> sb.append("\n")
                        tag == "img" -> {
                            val src = node.attr("data-src").ifBlank { node.attr("src") }.trim()
                            if (src.isNotBlank()) sb.append("[img]").append(src).append("[/img]")
                        }
                        tag == "a" -> {
                            val href = node.attr("href").trim()
                            when {
                                href.isBlank() -> node.childNodes().forEach(::visit)
                                href.contains("tenor.com/view/", ignoreCase = true) && node.text().trim() == href ->
                                    sb.append("[url]").append(href).append("[/url]")
                                else -> {
                                    sb.append("[url=").append(href).append("]")
                                    node.childNodes().forEach(::visit)
                                    sb.append("[/url]")
                                }
                            }
                        }
                        tag == "b" || tag == "strong" || style.contains("font-weight:bold") -> {
                            sb.append("[b]"); node.childNodes().forEach(::visit); sb.append("[/b]")
                        }
                        tag == "i" || tag == "em" -> {
                            sb.append("[i]"); node.childNodes().forEach(::visit); sb.append("[/i]")
                        }
                        tag == "u" -> {
                            sb.append("[u]"); node.childNodes().forEach(::visit); sb.append("[/u]")
                        }
                        tag == "s" || tag == "strike" || tag == "del" || style.contains("text-decoration:line-through") -> {
                            sb.append("[s]"); node.childNodes().forEach(::visit); sb.append("[/s]")
                        }
                        tag == "blockquote" || node.hasClass("quotetext") -> {
                            sb.append("\n[quote]"); node.childNodes().forEach(::visit); sb.append("[/quote]\n")
                        }
                        tag == "li" -> {
                            sb.append("[*]"); node.childNodes().forEach(::visit); sb.append("\n")
                        }
                        tag == "ol" -> {
                            sb.append("\n[list=1]\n"); node.childNodes().forEach(::visit); sb.append("[/list]\n")
                        }
                        tag == "ul" -> {
                            sb.append("\n[list]\n"); node.childNodes().forEach(::visit); sb.append("[/list]\n")
                        }
                        style.contains("text-align:center") -> {
                            sb.append("\n[center]"); node.childNodes().forEach(::visit); sb.append("[/center]\n")
                        }
                        else -> {
                            node.childNodes().forEach(::visit)
                            if (tag in blockLevelTags) sb.append("\n\n")
                        }
                    }
                }
                else -> {}
            }
        }

        root.childNodes().forEach(::visit)
        return sb.toString()
    }
}