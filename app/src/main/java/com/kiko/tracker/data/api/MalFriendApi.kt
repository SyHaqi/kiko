package com.kiko.tracker.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/** What the profile page says about the friendship between the signed-in user and someone else. */
enum class FriendRelation {
    /** MAL printed its "Add friend" link — a request can be sent. */
    CanRequest,
    /** MAL printed its "Remove friend" link — you're already friends and can unfriend. */
    CanRemove,
    /** Neither link on the page: already requested, or it's your own page. */
    Unavailable,
}

/** The profile page's friend-request link, as scraped: the target's numeric id plus relation. */
data class FriendRequestInfo(val relation: FriendRelation, val userId: Int?)

/**
 * The filters in MAL's notification sidebar (verified against each captured category page). [slug] is
 * the path under /notification/. The "All" page only embeds the NEW items — read items ("history")
 * are embedded only on the individual category pages, which is why history is loaded per category.
 */
enum class NotificationCategory(val slug: String?, val label: String) {
    All(null, "All"),
    FriendRequests("friend_request", "Friend requests"),
    ProfileComments("profile_comment", "Profile comments"),
    BlogComments("blog_comment", "Blog comments"),
    ForumQuotes("forum_quote", "Forum quotes"),
    UserMentions("user_mention", "User mentions"),
    WatchedTopics("watched_topic_message", "Watched topics"),
    ClubMessages("club_mass_message", "Club messages"),
    RelatedAnime("related_anime", "New related anime"),
    NowAiring("on_air", "Now airing"),
    Payment("payment", "Payment");

    /** MAL's "View more" page of older items for this category (not parsed — opened in the browser). */
    val moreHistoryUrl: String? get() = slug?.let { "https://myanimelist.net/notification/$it/history" }

    companion object {
        fun of(type: String): NotificationCategory = when {
            type.startsWith("friend_request") -> FriendRequests
            type == "profile_comment" -> ProfileComments
            type == "blog_comment" -> BlogComments
            type == "forum_quote" -> ForumQuotes
            type.startsWith("user_mention") -> UserMentions
            type == "watched_topic_message" -> WatchedTopics
            type.startsWith("club_mass_message") -> ClubMessages
            type == "related_anime_add" -> RelatedAnime
            type == "on_air" -> NowAiring
            type.startsWith("payment") -> Payment
            else -> All
        }
    }
}

/** One entry from myanimelist.net/notification, flattened into something a row can render. */
data class MalNotification(
    val id: String,
    /** MAL's own typeIdentifier, e.g. "friend_request_accept", "related_anime_add". */
    val type: String,
    val category: String,
    val createdAtEpoch: Long,
    val displayTime: String,
    val isRead: Boolean,
    val url: String?,
    val title: String,
    val body: String? = null,
    val avatarUrl: String? = null,
    /** Came from MAL's `historyItems` (already-read items) rather than the new `items`. */
    val isHistory: Boolean = false,
    /** Friend requests only: MAL says this request has already been accepted. */
    val isApproved: Boolean = false,
) {
    /** Which sidebar category this belongs to (never [NotificationCategory.All]) — drives the tabs. */
    val kind: NotificationCategory get() = NotificationCategory.of(type)
    val isFriendRelated: Boolean get() = type.startsWith("friend_request")
    val isFriendAccepted: Boolean get() = type.startsWith("friend_request") && type.contains("accept")
    val isIncomingFriendRequest: Boolean get() = type == "friend_request"
}

/**
 * Friend requests and the notification inbox, both through MAL's website (the official API has
 * neither), so both need the logged-in cookie session — same requirement as MalProfileScrapeApi.
 *
 * What was verified against real captured pages:
 *  - Profile page: the "Add friend" icon is `a#request` → `myfriends.php?go=add&id={userId}`,
 *    and it is simply absent for your own page.
 *  - `myfriends.php?go=add&id={userId}` (GET) is a confirmation form: POST to the same URL with
 *    `attachedMsg` (max 255 chars), `go=add`, `confirm=1`. No hidden token field in that form.
 *  - /notification embeds all items as JSON in `window.MAL.notification = {...};`.
 *
 *  - Accepting an incoming request from /notification/friend_request (captured in devtools):
 *    POST /notification/api/accept-friend-request.json, JSON body
 *    `{\"notification_ids\": [\"<notification id>\"], \"csrf_token\": \"<token>\"}`, plus the usual
 *    cookie and `X-Requested-With: XMLHttpRequest`. Replies 200 with a tiny (36 byte) JSON body.
 *
 *  - Marking read from /notification (captured): POST /notification/api/check-items-as-read.json,
 *    same JSON body shape as accept (`notification_ids` + `csrf_token`).
 *
 * What was NOT captured (so is handled defensively rather than assumed): the page MAL returns
 * after a successful POST, and the JSON shape of notification types other than
 * related_anime_add / club_mass_message_in_forum (those are read off MAL's own Vue templates),
 * the accept call's response body, and the DENY call entirely — [DENY_ENDPOINT] is the mirror-image
 * guess of the accept URL and must be confirmed with a devtools capture of pressing Deny.
 */
class MalFriendApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    private fun cookieOrThrow(): String = session.get() ?: throw MalSessionExpired()

    private fun checkSession(finalUrl: String, body: String) {
        if (finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")) throw MalSessionExpired()
    }

    // Everything here is per-user state (friend relation, inbox, confirm forms), so never serve it
    // from NetworkClient.shared's disk cache — a cached profile page kept showing the old relation
    // right after a friend was removed.
    private fun get(url: String): Pair<String, Document> {
        val request = Request.Builder().url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cookie", cookieOrThrow()).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()
        client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            checkSession(finalUrl, body)
            if (!resp.isSuccessful) throw IOException("MAL request failed (${resp.code}): $url")
            return body to Jsoup.parse(body, finalUrl)
        }
    }

    /** Reads [username]'s profile for the "Add friend" link (and the numeric id it carries). */
    suspend fun requestInfo(username: String): FriendRequestInfo = withContext(Dispatchers.IO) {
        val (_, doc) = get("https://myanimelist.net/profile/${java.net.URLEncoder.encode(username, "UTF-8").replace("+", "%20")}")
        // MAL reuses id="request" on BOTH links (captured: the Remove link is `a#request.icon-remove`
        // → myfriends.php?go=remove&id=…), so the relation is decided by the href, never the id.
        fun idOf(el: org.jsoup.nodes.Element?) =
            el?.attr("href")?.let { Regex("[?&]id=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val removeLink = doc.selectFirst("a[href*=myfriends.php?go=remove]")
        val addLink = doc.selectFirst("a[href*=myfriends.php?go=add]")
        when {
            removeLink != null && idOf(removeLink) != null -> FriendRequestInfo(FriendRelation.CanRemove, idOf(removeLink))
            addLink != null && idOf(addLink) != null -> FriendRequestInfo(FriendRelation.CanRequest, idOf(addLink))
            else -> FriendRequestInfo(FriendRelation.Unavailable, null)
        }
    }

    /**
     * Forum message links (`/forum/message/{id}`) carry no topic id — MAL redirects them to the topic
     * page. Loads one and reads the topic id off where it ended up.
     */
    suspend fun resolveForumTopicId(url: String): Int? = withContext(Dispatchers.IO) {
        val (_, doc) = get(url)
        listOfNotNull(
            doc.location(),
            doc.selectFirst("link[rel=canonical]")?.attr("href"),
            doc.selectFirst("meta[property=og:url]")?.attr("content"),
        ).firstNotNullOfOrNull { Regex("[?&]topicid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
    }

    /**
     * Sends a friend request to the user with numeric id [userId]. Loads the confirmation page
     * first (same as a browser would) so a changed/blocked flow fails loudly instead of silently.
     */
    suspend fun sendRequest(userId: Int, message: String): Unit = withContext(Dispatchers.IO) {
        val url = "https://myanimelist.net/myfriends.php?go=add&id=$userId"
        val (_, confirmDoc) = get(url)
        if (confirmDoc.selectFirst("form input[name=confirm]") == null) {
            // No confirmation form: MAL is showing an error (already friends/pending/blocked/limit).
            val reason = confirmDoc.selectFirst("div.badresult, div.error, div.goodresult")?.text()
            throw IOException(reason?.takeIf { it.isNotBlank() } ?: "MAL didn't offer a friend request for this user.")
        }
        val csrf = confirmDoc.selectFirst("meta[name=csrf_token]")?.attr("content").orEmpty()
        val form = FormBody.Builder()
            .add("attachedMsg", message.take(255))
            .add("go", "add")
            .add("confirm", "1")
            .apply { if (csrf.isNotBlank()) add("csrf_token", csrf) }
            .build()
        val post = Request.Builder().url(url)
            .header("Cookie", cookieOrThrow()).header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .header("Referer", url).post(form).build()
        client.newCall(post).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            checkSession(finalUrl, body)
            if (!resp.isSuccessful) throw IOException("Friend request failed (${resp.code})")
            val doc = Jsoup.parse(body, finalUrl)
            // Still on the "Are you sure…" form => the POST wasn't accepted.
            if (doc.selectFirst("form input[name=confirm][type=submit]") != null) throw IOException("MAL didn't accept the request — try again.")
            doc.selectFirst("div.badresult")?.text()?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
        }
    }

    /**
     * Removes the friend with numeric id [userId]. Two steps like [sendRequest]: load
     * `myfriends.php?go=remove&id=…` (confirmation page), then submit its form. Verified against the
     * real confirmation page: it is `<form method="POST">` (no action, so it posts back to the same
     * URL) holding a hidden `id` and a submit button `removeFriend` = "Yes, remove friend". There is
     * no `confirm` field and no csrf field inside the form, but the page's own
     * `<meta name="csrf_token">` is sent like the add flow does — a bare POST without it got a 400.
     */
    suspend fun removeFriend(userId: Int): Unit = withContext(Dispatchers.IO) {
        val url = "https://myanimelist.net/myfriends.php?go=remove&id=$userId"
        val (_, confirmDoc) = get(url)
        val form = confirmDoc.selectFirst("form:has(input[name=removeFriend])")
        if (form == null) {
            val reason = confirmDoc.selectFirst("div.badresult, div.error, div.goodresult")?.text()
            throw IOException(reason?.takeIf { it.isNotBlank() } ?: "MAL didn't offer to remove this friend.")
        }
        val body = FormBody.Builder()
            .apply {
                form.select("input[type=hidden][name]").forEach { add(it.attr("name"), it.attr("value")) }
                // A browser sends the clicked submit button's name/value too.
                val submit = form.selectFirst("input[name=removeFriend]")
                add("removeFriend", submit?.attr("value")?.ifBlank { null } ?: "Yes, remove friend")
                confirmDoc.selectFirst("meta[name=csrf_token]")?.attr("content")?.takeIf { it.isNotBlank() }?.let { add("csrf_token", it) }
            }
            .build()
        val action = form.absUrl("action").ifBlank { url }
        val post = Request.Builder().url(action)
            .header("Cookie", cookieOrThrow()).header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .header("Origin", "https://myanimelist.net")
            .header("Referer", url).post(body).build()
        client.newCall(post).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val text = resp.body?.string().orEmpty()
            checkSession(finalUrl, text)
            if (!resp.isSuccessful) {
                val snippet = Jsoup.parse(text).text().replace(Regex("\\s+"), " ").take(140)
                throw IOException("Removing the friend failed (${resp.code})" + if (snippet.isNotBlank()) ": $snippet" else "")
            }
            val doc = Jsoup.parse(text, finalUrl)
            // Still showing the "Are you sure…" form => MAL didn't take it.
            if (doc.selectFirst("input[name=removeFriend]") != null) throw IOException("MAL didn't accept that — try again.")
            doc.selectFirst("div.badresult")?.text()?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
        }
    }

    /**
     * Accepts ([accept] = true) or denies the incoming friend request carried by notification
     * [notificationId] (the same id the notification list uses).
     */
    suspend fun respondToRequest(notificationId: String, accept: Boolean): Unit = withContext(Dispatchers.IO) {
        postNotificationApi(
            endpoint = if (accept) ACCEPT_ENDPOINT else DENY_ENDPOINT,
            ids = listOf(notificationId),
            notFoundHint = if (accept) "" else " (the deny endpoint is unconfirmed)",
        )
    }

    /**
     * Marks notifications read on MAL's side (what the website does when you click one): captured
     * `POST /notification/api/check-items-as-read.json` with the same body as accept. Kiko's own
     * read state is local and updates instantly; this just keeps the website in step.
     */
    suspend fun markRead(notificationIds: List<String>): Unit = withContext(Dispatchers.IO) {
        if (notificationIds.isEmpty()) return@withContext
        postNotificationApi(MARK_READ_ENDPOINT, notificationIds)
    }

    // Shared by accept / deny / mark-read: all three take `{notification_ids: [...], csrf_token}` as
    // JSON. The csrf token is scraped fresh off /notification right before the call, like
    // MalForumReplyApi does for forum posts.
    private fun postNotificationApi(endpoint: String, ids: List<String>, notFoundHint: String = "") {
        val (html, doc) = get("https://myanimelist.net/notification")
        val csrf = doc.selectFirst("meta[name=csrf_token]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: Regex("csrf_token[\"']?\\s*[:=]\\s*[\"']([0-9a-f]{16,})[\"']").find(html)?.groupValues?.get(1)
            ?: throw IOException("Couldn't read the security token off the notification page")
        val payload = JSONObject()
            .put("notification_ids", JSONArray().apply { ids.forEach { put(it) } })
            .put("csrf_token", csrf)
            .toString()
        val request = Request.Builder()
            .url(endpoint)
            .header("Cookie", cookieOrThrow()).header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .header("Accept", "*/*")
            .header("Origin", "https://myanimelist.net")
            .header("Referer", "https://myanimelist.net/notification")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401) throw MalSessionExpired()
            if (!resp.isSuccessful) {
                val hint = if (resp.code == 404) notFoundHint else ""
                throw IOException("MAL rejected the request (${resp.code})$hint")
            }
            // The response body is a tiny JSON (36 bytes) whose shape was never captured, so only
            // treat an explicit failure marker as one.
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json != null && (json.optBoolean("success", true).not() || json.has("error"))) {
                throw IOException(json.optString("error").ifBlank { "MAL didn't accept that." })
            }
        }
    }

    /** All items currently on /notification (new + history), newest first. */
    suspend fun notifications(category: NotificationCategory = NotificationCategory.All): List<MalNotification> = withContext(Dispatchers.IO) {
        val (body, _) = get(category.slug?.let { "https://myanimelist.net/notification/$it" } ?: "https://myanimelist.net/notification")
        val json = extractNotificationJson(body) ?: throw IOException("Couldn't find the notification data on the page")
        val root = JSONObject(json)
        val out = ArrayList<MalNotification>()
        for (key in listOf("items", "historyItems")) {
            val arr: JSONArray = root.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { parse(it, history = key == "historyItems") }?.let(out::add)
        }
        out.distinctBy { it.id }.sortedByDescending { it.createdAtEpoch }
    }

    // Pulls the `{...}` literal after `window.MAL.notification =` by matching braces (string-aware),
    // since a regex can't reliably find the end of a JSON blob that contains nested objects/arrays.
    private fun extractNotificationJson(html: String): String? {
        val marker = "window.MAL.notification ="
        val start = html.indexOf(marker).takeIf { it >= 0 }?.let { html.indexOf('{', it + marker.length) } ?: return null
        if (start < 0) return null
        var depth = 0; var inString = false; var escaped = false
        for (i in start until html.length) {
            val ch = html[i]
            if (inString) {
                when { escaped -> escaped = false; ch == '\\' -> escaped = true; ch == '"' -> inString = false }
            } else when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return html.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun parse(o: JSONObject, history: Boolean): MalNotification? {
        if (o.optBoolean("isDeleted", false)) return null
        val id = o.optString("id").ifBlank { return null }
        val type = o.optString("typeIdentifier")
        fun s(key: String) = o.optString(key).takeIf { it.isNotBlank() }
        val friend = s("friendName")
        val (title, body) = when {
            type.startsWith("friend_request") && type.contains("accept") -> "${friend ?: "Someone"} accepted your friend request" to null
            type.startsWith("friend_request") && type.contains("deny") -> "${friend ?: "Someone"} denied your friend request" to null
            type.startsWith("friend_request") -> "${friend ?: "Someone"} sent you a friend request" to s("message")
            type == "profile_comment" -> "${s("commentUserName") ?: "Someone"} commented on your profile" to s("text")
            type == "forum_quote" -> "${s("quoteUserName") ?: "Someone"} quoted your post" to s("topicTitle")
            type == "blog_comment" -> "${s("commentUserName") ?: "Someone"} commented on your blog" to null
            type == "watched_topic_message" -> "${s("postedUserName") ?: "Someone"} replied in a topic you watch" to s("topicTitle")
            type.startsWith("club_mass_message") -> "${s("sharedUserName") ?: "A club member"} shared a discussion in ${s("clubName") ?: "your club"}" to s("topicTitle")
            type.startsWith("user_mention") -> "${s("senderName") ?: "Someone"} mentioned you" to s("pageTitle")
            type == "on_air" -> "Anime you plan to watch started airing" to
                    o.optJSONArray("animes")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("title") }.joinToString(", ") }?.ifBlank { null }
            type == "related_anime_add" -> {
                val anime = o.optJSONObject("anime")
                "New related anime added" to anime?.let { "${it.optString("title")} (${it.optString("mediaType")})" }
            }
            type.startsWith("payment") -> "Your MAL subscription was suspended" to s("planName")
            else -> (s("categoryName") ?: "Notification") to null
        }
        val avatar = s("friendImageUrl")
        return MalNotification(
            id = id, type = type, category = o.optString("categoryName"),
            createdAtEpoch = o.optLong("createdAt"), displayTime = o.optString("createdAtForDisplay"),
            isRead = o.optBoolean("isRead", false),
            url = s("url") ?: s("friendProfileUrl"),
            title = title, body = body, avatarUrl = avatar,
            isHistory = history, isApproved = o.optBoolean("isApproved", false),
        )
    }

    private companion object {
        const val ACCEPT_ENDPOINT = "https://myanimelist.net/notification/api/accept-friend-request.json"
        const val MARK_READ_ENDPOINT = "https://myanimelist.net/notification/api/check-items-as-read.json"
        // UNVERIFIED: guessed by mirroring ACCEPT_ENDPOINT. Replace with the real URL from devtools.
        const val DENY_ENDPOINT = "https://myanimelist.net/notification/api/deny-friend-request.json"
    }
}