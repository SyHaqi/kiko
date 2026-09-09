package com.kiko.tracker.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

/** Result of a successful reply post — the new message's own id. Deliberately does NOT try to
 * parse the "html" field the ajax endpoint also returns into a ForumPost here: that markup has
 * only ever been seen as one raw devtools capture, not verified against a range of real replies
 * (formatting, quoted replies, avatars-off users, etc.), and a scraper built off a single sample
 * is exactly the kind of thing that silently mis-parses later. Callers that want to show the
 * post immediately (rather than wait on forumTopic()'s read-after-write lag) should build a
 * ForumPost from data they already trust client-side — the signed-in user's own profile info,
 * the messageText that was submitted, and this messageId — not from scraping this response. */
data class MalForumReplyResult(val messageId: Int)

/**
 * Posts forum replies straight to myanimelist.net's own website. There's no such endpoint on
 * the official API — MalApi's forumTopics()/forumTopic() are read-only — so this needs the
 * logged-in session cookie (MalSessionCookie/MalLoginWebView), same requirement as
 * MalProfileScrapeApi's friends/favorites scraping.
 *
 * Reverse-engineered from a real reply captured in the browser devtools Network tab (the
 * desktop reply box's own <form> in the page markup points at a different, decoy endpoint —
 * "/forum/?action=message", used only for BBCode preview — the actual submit hits this one):
 *
 *   POST https://myanimelist.net/includes/ajax.inc.php?t=82
 *   form: topicId, parentId (0 for a fresh top-level reply, or the id of the specific post
 *         being replied to/quoted), messageText, totalReplies, csrf_token
 *   ->    {"html": "<rendered post HTML>", "msg_id": "<new post id>"}
 *
 * csrf_token and totalReplies are both scraped fresh off the topic page immediately before
 * posting rather than cached: the captured token matched that exact page's own
 * <meta name="csrf_token"> value, not a fixed per-session one, so there's no evidence it's
 * safe to reuse across requests.
 */
class MalForumReplyApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    suspend fun postReply(topicId: Int, messageText: String, parentId: Int = 0): MalForumReplyResult = withContext(Dispatchers.IO) {
        val cookie = session.get() ?: throw MalSessionExpired()

        val pageRequest = Request.Builder()
            .url("https://myanimelist.net/forum/?topicid=$topicId")
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .build()
        val (csrfToken, totalReplies) = client.newCall(pageRequest).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            // Same expired-cookie signal MalProfileScrapeApi checks for.
            if (finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")) throw MalSessionExpired()
            val doc = Jsoup.parse(body)
            val token = doc.selectFirst("meta[name=csrf_token]")?.attr("content")
                ?.takeIf { it.isNotBlank() } ?: throw IOException("Could not read csrf token off the topic page")
            val total = doc.selectFirst("#totalReplies")?.attr("value")?.toIntOrNull() ?: 0
            token to total
        }

        val formBody = FormBody.Builder()
            .add("topicId", topicId.toString())
            .add("parentId", parentId.toString())
            .add("messageText", messageText)
            .add("totalReplies", totalReplies.toString())
            .add("csrf_token", csrfToken)
            .build()
        val postRequest = Request.Builder()
            .url("https://myanimelist.net/includes/ajax.inc.php?t=82")
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()
        client.newCall(postRequest).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403) throw MalSessionExpired()
            if (!resp.isSuccessful) throw IOException("Reply failed (${resp.code}): ${text.take(300)}")
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw IOException("Unexpected reply response: ${text.take(300)}")
            val msgId = json.optString("msg_id").toIntOrNull()
                ?: throw IOException("MAL didn't confirm the post — no msg_id came back")
            MalForumReplyResult(messageId = msgId)
        }
    }
}