package com.kiko.tracker.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/** What the signed-in user can do on one club's page. */
data class ClubMembership(val joined: Boolean, val canJoin: Boolean, val canComment: Boolean)

/** One title on the user's "My Recommendations" page, with the link MAL uses to open its editor. */
data class MyRecEntry(val title: String, val editUrl: String, val infoUrl: String, val meta: String)
data class MyRecs(val anime: List<MyRecEntry>, val manga: List<MyRecEntry>)

/** One "X is similar to Y" pair inside a recommendation editor. [targetId] is the recommended title, [sourceId] the page it was made from. */
data class RecPair(val targetId: Int, val targetTitle: String, val sourceId: Int, val sourceTitle: String, val isManga: Boolean, val text: String)
data class RecEditor(val url: String, val pairs: List<RecPair>)

/** One row of the inbox or sent box. [user] is the sender (inbox) or the recipient (sent). */
data class MalMessage(val id: String, val user: String, val subject: String, val preview: String, val date: String, val unread: Boolean, val readUrl: String)
data class MalMessagesPage(val items: List<MalMessage>, val total: Int, val hasMore: Boolean)
data class MalMessageDetail(val user: String, val date: String, val subject: String, val body: String, val replyUrl: String, val canDelete: Boolean)

/**
 * Social actions on myanimelist.net that need the signed-in website session: club join / leave / comments and
 * recommendations. Every request is a plain form post (no CAPTCHA), verified on a real account:
 *
 *  - join / leave : GET clubs.php?action=join|leave&id=ID shows a confirm form (hidden action_type = submitjoin / submitleave);
 *                   POST the same URL with action_type + csrf_token.
 *  - club comment : POST clubs.php?cid=ID with action_type=commentSubmit, commentText, csrf_token.
 *  - recommend    : POST myrecommendations.php?go=make&aid=ID (manga: &mid=ID) with rec[] (target id), rec_text[], subrec=Submit, csrf_token.
 *                   The title must be on the user's own list, otherwise MAL answers "This anime is not on your list".
 *  - edit         : POST myrecommendations.php?aeid=ID with rec[], rec_text[], subrec=Update, csrf_token.
 *  - messages     : read-only here. Inbox = mymessages.php, sent = mymessages.php?go=sent (rows are .message with .mym_user / .mym_subject a.subject-link /
 *                   .mym_date), a message = mymessages.php?go=read&id=ID (body inside td.dialog-text). Sending needs reCAPTCHA, so compose and reply
 *                   open MAL's own form (mymessages.php?go=send&toname=NAME) in a Custom Tab. Delete = POST mymessages.php?go=delete with the message id.
 *  - delete       : POST includes/ajax.inc.php?t=26 (manga t=52) with arid/arsid (manga mrid/mrsid) + csrf_token -> "Success".
 */
class MalSocialApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)
    private val base = "https://myanimelist.net"

    private fun get(url: String): Document {
        val cookie = session.get() ?: throw MalSessionExpired()
        val req = Request.Builder().url(url).header("Cookie", cookie).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()
        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.request.url.toString().contains("login.php") || body.contains("id=\"loginForm\"")) throw MalSessionExpired()
            if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code}")
            Jsoup.parse(body, url)
        }
    }

    private fun csrf(doc: Document): String =
        doc.selectFirst("meta[name=csrf_token]")?.attr("content")?.takeIf { it.isNotBlank() } ?: throw IOException("Couldn't read MAL's security token")

    /** Posts [form] and returns MAL's own success line; throws MAL's error text when it answers with a .badresult. */
    private fun post(url: String, form: FormBody, ajax: Boolean = false): String {
        val cookie = session.get() ?: throw MalSessionExpired()
        val builder = Request.Builder().url(url).post(form).header("Cookie", cookie).header("User-Agent", MAL_DESKTOP_USER_AGENT)
        if (ajax) builder.header("X-Requested-With", "XMLHttpRequest")
        return client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.request.url.toString().contains("login.php")) throw MalSessionExpired()
            if (ajax) {
                if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code}")
                return@use text.trim().ifBlank { "Done" }
            }
            val doc = Jsoup.parse(text)
            doc.selectFirst(".badresult")?.text()?.trim()?.let { throw IOException(it.ifBlank { "MAL rejected that" }) }
            if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code}")
            doc.selectFirst(".goodresult")?.text()?.substringBefore("Please wait")?.trim()?.takeIf { it.isNotBlank() } ?: "Done"
        }
    }

    // ---- clubs ---------------------------------------------------------------------------------------------------------

    suspend fun clubMembership(id: Int): ClubMembership = withContext(Dispatchers.IO) {
        val doc = get("$base/clubs.php?cid=$id")
        ClubMembership(
            joined = doc.selectFirst("a[href*=action=leave]") != null,
            canJoin = doc.selectFirst("a[href*=action=join]") != null,
            canComment = doc.selectFirst("textarea[name=commentText]") != null,
        )
    }

    suspend fun joinClub(id: Int): String = clubAction("join", id)
    suspend fun leaveClub(id: Int): String = clubAction("leave", id)

    private suspend fun clubAction(action: String, id: Int): String = withContext(Dispatchers.IO) {
        val url = "$base/clubs.php?action=$action&id=$id"
        val doc = get(url)
        val type = doc.selectFirst("input[name=action_type]")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: throw IOException("MAL didn't offer that action for this club")
        post(url, FormBody.Builder().add("action_type", type).add("csrf_token", csrf(doc)).build())
    }

    suspend fun postClubComment(id: Int, text: String): String = withContext(Dispatchers.IO) {
        val url = "$base/clubs.php?cid=$id"
        val doc = get(url)
        post(url, FormBody.Builder().add("action_type", "commentSubmit").add("commentText", text).add("csrf_token", csrf(doc)).build())
    }

    // ---- recommendations -----------------------------------------------------------------------------------------------

    /** Recommends [targetId] to people who like [sourceId]. Both must be on the user's list. */
    suspend fun recommend(isManga: Boolean, sourceId: Int, targetId: Int, text: String): String = withContext(Dispatchers.IO) {
        val url = "$base/myrecommendations.php?go=make&${if (isManga) "mid" else "aid"}=$sourceId"
        val doc = get(url)
        doc.selectFirst(".badresult")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
        post(url, FormBody.Builder().add("rec[]", targetId.toString()).add("rec_text[]", text).add("subrec", "Submit").add("csrf_token", csrf(doc)).build())
    }

    suspend fun myRecommendations(): MyRecs = withContext(Dispatchers.IO) {
        val doc = get("$base/myrecommendations.php")
        fun column(header: String): List<MyRecEntry> {
            val head = doc.select("div.normal_header").firstOrNull { it.ownText().trim().startsWith(header) } ?: return emptyList()
            val cell = head.parent() ?: return emptyList()
            return cell.select("div.borderClass").mapNotNull { row ->
                val a = row.selectFirst("a[href*=eid=]") ?: return@mapNotNull null
                val info = row.select("a").firstOrNull { it !== a }?.absUrl("href").orEmpty()
                MyRecEntry(title = a.text().trim(), editUrl = a.absUrl("href"), infoUrl = info, meta = row.selectFirst("span.lightLink")?.text()?.substringBefore(" - ")?.trim().orEmpty())
            }
        }
        MyRecs(anime = column("Anime Recommendations"), manga = column("Manga Recommendations"))
    }

    suspend fun loadEditor(editUrl: String): RecEditor = withContext(Dispatchers.IO) {
        val doc = get(editUrl)
        val pairs = doc.select("#recarea > div[id~=^rec\\d+$]").mapNotNull { box ->
            val targetId = box.selectFirst("input[name=rec[]]")?.attr("value")?.toIntOrNull() ?: return@mapNotNull null
            val args = Regex("removeRec\\((\\d+),(\\d+),(\\d+),(\\d+)\\)").find(box.selectFirst("a[onclick*=removeRec]")?.attr("onclick").orEmpty())
            val isManga = args?.groupValues?.get(2) == "2"
            val sourceId = args?.groupValues?.get(4)?.toIntOrNull() ?: 0
            val names = box.select("span.dark_text").map { it.text().trim() }
            RecPair(
                targetId = targetId,
                targetTitle = box.selectFirst("div.dark_text")?.text()?.trim().orEmpty().ifBlank { names.firstOrNull().orEmpty() },
                sourceId = sourceId,
                sourceTitle = names.getOrNull(1).orEmpty(),
                isManga = isManga,
                text = box.selectFirst("textarea[name=rec_text[]]")?.text().orEmpty(),
            )
        }
        RecEditor(url = editUrl, pairs = pairs)
    }

    suspend fun saveEditor(editor: RecEditor, texts: List<String>): String = withContext(Dispatchers.IO) {
        val doc = get(editor.url)
        val form = FormBody.Builder()
        editor.pairs.forEachIndexed { i, p -> form.add("rec[]", p.targetId.toString()); form.add("rec_text[]", texts.getOrElse(i) { p.text }) }
        form.add("subrec", "Update").add("csrf_token", csrf(doc))
        post(editor.url, form.build())
    }

    suspend fun deletePair(pair: RecPair): Unit = withContext(Dispatchers.IO) {
        val doc = get("$base/myrecommendations.php")
        val form = FormBody.Builder().add("csrf_token", csrf(doc))
        if (pair.isManga) form.add("mrid", pair.targetId.toString()).add("mrsid", pair.sourceId.toString())
        else form.add("arid", pair.targetId.toString()).add("arsid", pair.sourceId.toString())
        val answer = post("$base/includes/ajax.inc.php?t=${if (pair.isManga) 52 else 26}", form.build(), ajax = true)
        if (!answer.contains("Success", ignoreCase = true)) throw IOException(answer.take(120))
    }

    // ---- private messages ----------------------------------------------------------------------------------------------

    suspend fun messages(sent: Boolean, offset: Int = 0): MalMessagesPage = withContext(Dispatchers.IO) {
        val params = buildList { if (sent) add("go=sent"); if (offset > 0) add("show=$offset") }
        val doc = get("$base/mymessages.php" + if (params.isEmpty()) "" else "?" + params.joinToString("&"))
        val items = doc.select("div.message").mapNotNull { row ->
            val link = row.selectFirst("a.subject-link") ?: return@mapNotNull null
            MalMessage(
                id = row.id().removePrefix("message_"),
                user = row.selectFirst(".mym_user a")?.text()?.trim().orEmpty(),
                subject = link.ownText().trim().removeSuffix("-").trim(),
                preview = link.selectFirst("span.text")?.text()?.trim().orEmpty(),
                date = row.selectFirst(".mym_date")?.text()?.trim().orEmpty(),
                unread = row.hasClass("unread"),
                readUrl = link.absUrl("href"),
            )
        }
        val total = Regex("You have (\\d+) messages? in your").find(doc.text())?.groupValues?.get(1)?.toIntOrNull() ?: items.size
        MalMessagesPage(items, total, hasMore = offset + items.size < total && items.isNotEmpty())
    }

    /** New (unread) messages on the first inbox page, for the badge in the avatar menu. */
    suspend fun unreadMessages(): Int = withContext(Dispatchers.IO) { get("$base/mymessages.php").select("div.message.unread").size }

    suspend fun readMessage(readUrl: String): MalMessageDetail = withContext(Dispatchers.IO) {
        val doc = get(readUrl)
        val cell = doc.selectFirst("td.dialog-text") ?: throw IOException("Couldn't find the message on MAL's page. The layout may have changed.")
        val user = cell.selectFirst("h2 a")?.text()?.trim().orEmpty()
        val date = cell.selectFirst("div.lightLink")?.text()?.trim().orEmpty()
        val subject = cell.selectFirst("div.fw-b")?.text()?.trim().orEmpty()
        val replyUrl = Regex("'([^']*go=send[^']*)'").find(cell.select("input[onclick*=go=send]").firstOrNull()?.attr("onclick").orEmpty())?.groupValues?.get(1)?.replace("&amp;", "&")
            ?.let { if (it.startsWith("http")) it else base + (if (it.startsWith("/")) it else "/$it") }.orEmpty()
        val body = cell.clone()
        body.select("h2, div.lightLink, div.fw-b, div.message-response-buttons").remove()
        body.select("br").forEach { it.after("\n") }
        MalMessageDetail(user, date, subject, body.wholeText().trim(), replyUrl, canDelete = doc.selectFirst("form[action*=go=delete] input[name=id]") != null)
    }

    /** Deletes the message shown at [readUrl]. Unverified against a live account (the only test message was kept). */
    suspend fun deleteMessage(readUrl: String): Unit = withContext(Dispatchers.IO) {
        val doc = get(readUrl)
        val form = doc.selectFirst("form[action*=go=delete]") ?: throw IOException("MAL didn't offer delete for this message")
        val id = form.selectFirst("input[name=id]")?.attr("value").orEmpty().ifBlank { throw IOException("Couldn't find the message id") }
        post(form.absUrl("action").ifBlank { "$base/mymessages.php?go=delete" }, FormBody.Builder().add("id", id).add("csrf_token", csrf(doc)).build())
    }
}
