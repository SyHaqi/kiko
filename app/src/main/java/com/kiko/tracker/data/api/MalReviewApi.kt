package com.kiko.tracker.data.api

import android.content.Context
import com.kiko.tracker.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.FormElement
import java.io.IOException

/** MAL's own client-side minimums for a review (the website's form JS refuses to submit below
 * these — see checkValid() in the write-a-review page). Mirrored here so the editor can show
 * the same progress the website does instead of finding out on publish. */
const val MAL_REVIEW_MIN_WORDS = 100
const val MAL_REVIEW_MIN_CHARS = 500

/** Same word test as MAL's form JS: it counts whitespace-then-word-character matches, plus one
 * for the first word. Deliberately NOT a smarter tokenizer — matching MAL's own count exactly
 * is the point, so "100 words" here means "100 words" to MAL's page too. */
fun malReviewWordCount(text: String): Int =
    if (text.isBlank()) 0 else Regex("\\s\\w").findAll(text.trim()).count() + 1

/** MAL's three recommendation verdicts, as the form's frm_review_feelings radio values. */
object MalReviewFeeling {
    const val Recommended = 1
    const val MixedFeelings = 2
    const val NotRecommended = 3
}

/**
 * Everything the editor needs from MAL's write-a-review page, plus the raw form fields so
 * [MalReviewApi.submit] can post the page back exactly as MAL rendered it.
 *
 * [fields] is the form's complete successful-control set (hidden inputs, the user's current
 * list status/score/progress selects, etc.) as a browser would send it. It is kept whole on
 * purpose: the form also carries the signed-in user's list entry (myinfo_status, myinfo_score,
 * myinfo_watchedeps, frmreview_seeneps), and re-posting those untouched means publishing a
 * review can't accidentally change the user's list.
 *
 * [existingText]/[existingScore]/[existingFeeling]/[existingSpoiler] are whatever the form came
 * pre-filled with: the overall score is pre-filled from the user's list score, and if the user
 * has already reviewed the title the rest should be pre-filled too (not confirmed — see the
 * class doc on [MalReviewApi]).
 */
data class MalReviewForm(
    val postUrl: String,
    val referer: String,
    val fields: List<Pair<String, String>>,
    val csrfToken: String?,
    /** Non-null when MAL's own rules say this user can't review this title yet. */
    val blockedReason: String?,
    val existingText: String,
    val existingScore: Int,
    val existingFeeling: Int,
    val existingSpoiler: Boolean?,
)

data class MalReviewResult(val url: String)

/** MAL answered the post but didn't accept it — [message] is MAL's own reason when one could
 * be read off the page, otherwise a generic "didn't confirm" line. */
class MalReviewRejected(message: String) : IOException(message)

/**
 * Writes anime/manga reviews on myanimelist.net's own website. The official API has no review
 * endpoint at all, so — same situation as MalForumReplyApi/FavoriteApi — this needs the
 * logged-in session cookie (MalSessionCookie/MalLoginWebView), not the OAuth token.
 *
 * Built from a saved copy of the real write-a-review page (anime, series 61316), NOT from a
 * captured submit, so the read half is well grounded and the write half is inferred:
 *
 *   GET  https://myanimelist.net/myreviews.php?seriesid={id}&go=write
 *   <form method="post" name="reviews">   — no action attribute, so it posts back to the URL
 *                                           above; no csrf field inside the form (the page's
 *                                           <meta name="csrf_token"> is sent too, harmlessly)
 *     frm_review_text       textarea, >= 500 chars and >= 100 words (client-side check)
 *     frmreview_overall_score   hidden, 1-10, pre-filled from the user's list score
 *     frm_review_feelings   radio: 1 Recommended / 2 Mixed Feelings / 3 Not Recommended
 *     frm_review_is_spoiler radio: 1 yes / 0 no  (both required, neither pre-selected)
 *     frm_submit            hidden, the page JS sets it to 1 right before form.submit()
 *     review_type / frm_series_id / frm_series_title   hidden, identify the title
 *     myinfo_* / frmreview_seeneps   the user's list entry, re-posted as-is
 *
 * Not yet confirmed against a live submit (worth one devtools capture if posting misbehaves):
 *  - what a successful post answers with. Success is judged as "MAL no longer answered with the
 *    review form"; if the form comes back, any visible error text on it is surfaced, and with
 *    no error text the message tells the user to check MAL before retrying (so an unrecognized
 *    success page can't turn into a duplicate post).
 *  - the manga form URL. Only the anime URL is confirmed. For manga the link is read off the
 *    title's own page (an anchor to myreviews.php?go=write), with a best-guess fallback; either
 *    way the form is only accepted if its review_type/frm_series_id match what was asked for,
 *    so a wrong guess fails with an error rather than reviewing the wrong thing.
 */
class MalReviewApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    private fun get(url: String, cookie: String): Request = Request.Builder()
        .url(url)
        .header("Cookie", cookie)
        .header("User-Agent", MAL_DESKTOP_USER_AGENT)
        // The shared client has an on-disk HTTP cache; the form carries per-moment state (list
        // progress, csrf meta), so never serve it from there.
        .cacheControl(CacheControl.FORCE_NETWORK)
        .build()

    private fun isLoginPage(finalUrl: String, body: String) =
        finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")

    suspend fun loadForm(malId: Int, type: MediaType): MalReviewForm = withContext(Dispatchers.IO) {
        val cookie = session.get() ?: throw MalSessionExpired()
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        for (url in candidateFormUrls(malId, type, cookie)) {
            val parsed = client.newCall(get(url, cookie)).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                val body = resp.body?.string().orEmpty()
                if (resp.code == 401 || resp.code == 403 || isLoginPage(finalUrl, body)) throw MalSessionExpired()
                if (!resp.isSuccessful) return@use null
                parseForm(Jsoup.parse(body, finalUrl), finalUrl, malId, kind)
            }
            if (parsed != null) return@withContext parsed
        }
        throw IOException("Couldn't open MAL's review form for this title")
    }

    private fun candidateFormUrls(malId: Int, type: MediaType, cookie: String): List<String> {
        if (type == MediaType.Anime) return listOf("https://myanimelist.net/myreviews.php?seriesid=$malId&go=write")
        // Manga: prefer the "write a review" link MAL itself renders on the title's page.
        val discovered = runCatching {
            client.newCall(get("https://myanimelist.net/manga/$malId", cookie)).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || isLoginPage(finalUrl, body)) null
                else Jsoup.parse(body, finalUrl)
                    .select("a[href*=myreviews.php][href*=go=write]")
                    .firstOrNull()?.absUrl("href")?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
        return listOfNotNull(discovered, "https://myanimelist.net/myreviews.php?seriesid=$malId&go=write&t=manga").distinct()
    }

    private fun parseForm(doc: Document, pageUrl: String, malId: Int, kind: String): MalReviewForm? {
        val form = doc.selectFirst("form[name=reviews]") as? FormElement ?: return null
        // Refuse a form that isn't for exactly this title/medium (see the manga note above).
        val reviewType = form.selectFirst("input[name=review_type]")?.`val`()
        val seriesId = form.selectFirst("input[name=frm_series_id]")?.`val`()?.toIntOrNull()
        if (reviewType != kind || seriesId != malId) return null

        val fields = form.formData().map { it.key() to it.value() }
        fun field(name: String) = fields.firstOrNull { it.first == name }?.second.orEmpty()

        val action = form.attr("action").takeIf { it.isNotBlank() }?.let { form.absUrl("action") } ?: pageUrl

        // MAL's own gate (see the page's "cannot write a review for TV/ONA series until you have
        // seen 3 episodes or completed it" notice): completed, or >= required episodes seen.
        val curStatus = doc.selectFirst("#myinfo_curstatus")?.attr("value")?.toIntOrNull()
        val required = doc.selectFirst("#myinfo_required_episodes")?.attr("value")?.toIntOrNull() ?: 0
        val seen = field("frmreview_seeneps").toIntOrNull() ?: 0
        val blocked = when {
            curStatus == null -> null // markup we don't recognize (e.g. manga): let MAL decide on submit
            curStatus == 0 -> "Add this title to your MAL list first — MAL only lets you review titles on your list."
            curStatus == 2 -> null
            required > 0 && seen < required ->
                "MAL only allows a review once you've watched $required episodes or completed the series (you're at $seen)."
            else -> null
        }

        return MalReviewForm(
            postUrl = action,
            referer = pageUrl,
            fields = fields,
            csrfToken = doc.selectFirst("meta[name=csrf_token]")?.attr("content")?.takeIf { it.isNotBlank() },
            blockedReason = blocked,
            existingText = field("frm_review_text"),
            existingScore = field("frmreview_overall_score").toIntOrNull()?.takeIf { it in 1..10 } ?: 0,
            existingFeeling = field("frm_review_feelings").toIntOrNull()?.takeIf { it in 1..3 } ?: 0,
            existingSpoiler = field("frm_review_is_spoiler").takeIf { it.isNotBlank() }?.let { it == "1" },
        )
    }

    /** Posts the review. Only the review's own fields are overridden; everything else is sent
     * back exactly as MAL's form rendered it (see [MalReviewForm.fields]). */
    suspend fun submit(
        form: MalReviewForm, text: String, score: Int, feeling: Int, spoiler: Boolean,
    ): MalReviewResult = withContext(Dispatchers.IO) {
        require(score in 1..10) { "score must be 1-10" }
        require(feeling in 1..3) { "feeling must be 1-3" }
        val cookie = session.get() ?: throw MalSessionExpired()

        val overridden = setOf(
            "frm_review_text", "frmreview_overall_score", "frm_review_feelings", "frm_review_is_spoiler",
            "frm_submit", "frm_preview", "frm_delete",
        )
        val body = FormBody.Builder().apply {
            form.fields.filterNot { it.first in overridden }.forEach { (k, v) -> add(k, v) }
            add("frm_review_text", text)
            add("frmreview_overall_score", score.toString())
            add("frm_review_feelings", feeling.toString())
            add("frm_review_is_spoiler", if (spoiler) "1" else "0")
            add("frm_preview", "")
            add("frm_delete", "")
            add("frm_submit", "1") // what checkValid('post') sets right before form.submit()
            if (form.csrfToken != null && form.fields.none { it.first == "csrf_token" }) add("csrf_token", form.csrfToken)
        }.build()

        val request = Request.Builder()
            .url(form.postUrl)
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .header("Referer", form.referer)
            .header("Origin", "https://myanimelist.net")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val html = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403 || isLoginPage(finalUrl, html)) throw MalSessionExpired()
            if (!resp.isSuccessful) throw IOException("Publishing failed (${resp.code})")
            val doc = Jsoup.parse(html, finalUrl)
            if (doc.selectFirst("form[name=reviews]") != null) {
                // MAL re-rendered the form: not accepted. Surface whatever it says, but only text
                // that's actually visible (the page ships several display:none helper warnings).
                val reasons = doc.select(".badresult-text, .badresult, .error, .warning-box")
                    .filterNot { el -> (listOf(el) + el.parents()).any { isHiddenInline(it) } }
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                throw MalReviewRejected(
                    reasons.joinToString(" ").take(300).ifBlank {
                        "MAL didn't confirm the review. Check your reviews on MAL before trying again, so it isn't posted twice."
                    }
                )
            }
            MalReviewResult(url = finalUrl)
        }
    }

    private fun isHiddenInline(el: Element): Boolean =
        el.attr("style").replace(" ", "").contains("display:none", ignoreCase = true)
}