package com.kiko.tracker.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Restacks (or un-restacks) an Interest Stack on myanimelist.net's own website — there's no
 * such endpoint on the official API, same situation as MalForumReplyApi's forum replies. Needs
 * the logged-in session cookie (MalSessionCookie/MalLoginWebView), not the OAuth token.
 *
 * Reverse-engineered from a real restack captured in the browser devtools Network tab (the
 * stack detail page's restack "balloon" button is a plain onclick handler, not a <form>):
 *
 *   POST https://myanimelist.net/stacks/restack.json
 *   form: mode ("add" or "remove"), id, csrf_token
 *   ->    small JSON body, exact shape unconfirmed from a single capture — like
 *         MalForumReplyApi's caution around its "html" field, this only trusts the HTTP status
 *         here rather than parsing an under-verified response shape.
 *
 * csrf_token is scraped fresh off the stack's own detail page immediately before posting, same
 * reasoning as MalForumReplyApi: no evidence it's a fixed per-session token rather than one
 * that's page-specific.
 */
class StacksRestackApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    suspend fun setRestacked(stackId: Int, restacked: Boolean): Unit = withContext(Dispatchers.IO) {
        val cookie = session.get() ?: throw MalSessionExpired()

        val pageRequest = Request.Builder()
            .url("https://myanimelist.net/stacks/$stackId")
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .build()
        val csrfToken = client.newCall(pageRequest).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            // Same expired-cookie signal MalProfileScrapeApi/MalForumReplyApi check for.
            if (finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")) throw MalSessionExpired()
            Jsoup.parse(body).selectFirst("meta[name=csrf_token]")?.attr("content")
                ?.takeIf { it.isNotBlank() } ?: throw IOException("Could not read csrf token off the stack page")
        }

        val formBody = FormBody.Builder()
            .add("mode", if (restacked) "add" else "remove")
            .add("id", stackId.toString())
            .add("csrf_token", csrfToken)
            .build()
        val postRequest = Request.Builder()
            .url("https://myanimelist.net/stacks/restack.json")
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()
        client.newCall(postRequest).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403) throw MalSessionExpired()
            if (!resp.isSuccessful) throw IOException("Restack failed (${resp.code}): ${text.take(300)}")
        }
    }

    /**
     * The signed-in user's own "My Interest Stacks" list (created + restacked), scraped from
     * https://myanimelist.net/profile/{username}/stacks?tab=all — the same profile-stacks page
     * MalProfileScrapeApi's other calls already hit successfully for this user. Reuses
     * StacksApi's own row parser — the profile stacks list is the same "title anchor pointing
     * at /stacks/{id}" row shape, just with the type/Challenge badge as its own clean
     * <span class="tag-anime">Anime</span> rather than glued to "by" like the browse pages, so
     * it parses the same way either form takes in StacksApi.parseSummaries().
     *
     * Deliberately doesn't hit /stacks/my and rely on OkHttp following a redirect to the profile
     * page: /stacks/my doesn't 3xx there server-side (it's a client-side bounce), so a plain GET
     * just serves the generic Interest Stacks home page — which is why this used to silently
     * return only Spotlight/Recent-style content and never the signed-in user's own stacks.
     * [username] is the same malProfile?.name value MalProfileScrapeApi's calls already use.
     */
    suspend fun myStacks(username: String): List<StackSummary> = withContext(Dispatchers.IO) {
        val cookie = session.get() ?: throw MalSessionExpired()
        val request = Request.Builder()
            .url("https://myanimelist.net/profile/$username/stacks?tab=all")
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .build()
        val doc = client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            if (finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")) throw MalSessionExpired()
            Jsoup.parse(body, finalUrl)
        }
        StacksApi().parseSummaries(doc)
    }
}