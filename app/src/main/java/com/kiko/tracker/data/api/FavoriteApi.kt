package com.kiko.tracker.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Thrown when MAL's favorite.json endpoint answers with a non-2xx and a specific reason could
 * be pulled out of its JSON body (an "errors"/"error"/"message" key — the endpoint's exact
 * error shape isn't documented anywhere, this just tries the field names MAL's other AJAX
 * endpoints use). [reason] is that human-readable text straight from MAL, or null if the body
 * didn't parse as JSON or didn't have any of those keys — callers should fall back to a generic
 * message in that case rather than surface the raw body.
 */
class FavoriteToggleFailed(val reason: String?, code: Int) : IOException("Favorite toggle failed ($code): ${reason ?: "no reason given"}")

/**
 * The five categories MAL's own profile groups "Favorites" into (see
 * MalProfileScrapeApi.MalFavorites) — urlSegment is the {kind} path piece the favorite.json
 * endpoint below expects, pageUrl is that entity's own detail page (scraped fresh for a
 * csrf_token immediately before posting, same as StacksRestackApi).
 */
enum class FavoriteKind(val urlSegment: String, val pageUrl: (Int) -> String) {
    Anime("anime", { id -> "https://myanimelist.net/anime/$id" }),
    Manga("manga", { id -> "https://myanimelist.net/manga/$id" }),
    Character("character", { id -> "https://myanimelist.net/character/$id" }),
    Person("person", { id -> "https://myanimelist.net/people/$id" }),
    Company("company", { id -> "https://myanimelist.net/anime/producer/$id" }),
}

/**
 * Adds/removes an anime, manga, character, person, or company to the signed-in user's MAL
 * Favorites on myanimelist.net's own website — there's no such endpoint on the official API,
 * same situation as StacksRestackApi's restacking. Needs the logged-in session cookie
 * (MalSessionCookie/MalLoginWebView), not the OAuth token.
 *
 * Reverse-engineered from a real "Add to Favorites" click captured in the browser devtools
 * Network tab (the sidebar's favorites link is a plain onclick handler, not a <form>):
 *
 *   POST https://myanimelist.net/favorite/{kind}/{id}.json
 *   ->   {"created_at": 1789620248} on success
 *   ->   {"is_supporter": false, "url": "/membership", "max_favs": 10} on a 400 when the
 *        signed-in account has hit its favorites cap (confirmed from a live capture; see
 *        FavoriteToggleFailed below for how this is turned into a message)
 *
 * Only the "add" direction was captured (the capture already had this entry favorited from a
 * previous session, so only "Remove from Favorites" was on offer in the UI — the opposite of
 * what got captured). Same caution as StacksRestackApi's own comment about its "mode" field:
 * rather than guess at a second, unconfirmed URL shape for the opposite direction, this reuses
 * the one confirmed shape and switches HTTP verb — POST to add, DELETE to remove — since that
 * matches the endpoint's own REST-y "/{kind}/{id}.json" naming (a single resource, not a
 * StacksRestackApi-style "restack.json" action route with its own mode param). If MAL turns
 * out to want something else for the remove direction, this is the first place to look.
 *
 * csrf_token is scraped fresh off the entity's own detail page immediately before posting, same
 * reasoning as StacksRestackApi/MalForumReplyApi: no evidence it's a fixed per-session token
 * rather than one that's page-specific.
 */
class FavoriteApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    suspend fun setFavorited(kind: FavoriteKind, malId: Int, favorited: Boolean): Unit = withContext(Dispatchers.IO) {
        val cookie = session.get() ?: throw MalSessionExpired()

        val pageRequest = Request.Builder()
            .url(kind.pageUrl(malId))
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .build()
        val csrfToken = client.newCall(pageRequest).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            // Same expired-cookie signal MalProfileScrapeApi/MalForumReplyApi/StacksRestackApi
            // check for.
            if (finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")) throw MalSessionExpired()
            Jsoup.parse(body).selectFirst("meta[name=csrf_token]")?.attr("content")
                ?.takeIf { it.isNotBlank() } ?: throw IOException("Could not read csrf token off the ${kind.urlSegment} page")
        }

        val formBody = FormBody.Builder()
            .add("id", malId.toString())
            .add("csrf_token", csrfToken)
            .build()
        val requestBuilder = Request.Builder()
            .url("https://myanimelist.net/favorite/${kind.urlSegment}/$malId.json")
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
        val request = if (favorited) requestBuilder.post(formBody).build() else requestBuilder.delete(formBody).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403) throw MalSessionExpired()
            if (!resp.isSuccessful) {
                // Confirmed shape (captured from a live 400 on favorite/anime/{id}.json):
                //   {"is_supporter":false,"url":"/membership","max_favs":10}
                // MAL caps free accounts at 10 favorites per category and reports the cap this
                // way rather than a generic "errors" message — this isn't a transient failure,
                // it's MAL saying no, so it gets its own specific, actionable copy. Falls back
                // to the "errors"/"error"/"message" guess below for anything else MAL might send
                // (e.g. a different rejection reason on a future response), and finally to
                // reason == null so the caller shows its own generic message.
                val json = runCatching { JSONObject(text) }.getOrNull()
                val reason = if (json != null && json.has("max_favs")) {
                    val maxFavs = json.optInt("max_favs")
                    val isSupporter = json.optBoolean("is_supporter", false)
                    val link = json.optString("url").takeIf { it.isNotBlank() }?.let { "https://myanimelist.net$it" }
                    buildString {
                        append("You've reached the $maxFavs-favorite limit")
                        append(if (isSupporter) "." else " for free accounts.")
                        if (!isSupporter && link != null) append(" MAL Supporters get a higher limit — see $link.")
                    }
                } else {
                    json?.let {
                        it.optString("errors").takeIf { s -> s.isNotBlank() }
                            ?: it.optJSONArray("errors")?.let { arr -> (0 until arr.length()).map { i -> arr.optString(i) } }
                                ?.filter { s -> s.isNotBlank() }?.joinToString(" ")?.takeIf { s -> s.isNotBlank() }
                            ?: it.optString("error").takeIf { s -> s.isNotBlank() }
                            ?: it.optString("message").takeIf { s -> s.isNotBlank() }
                    }
                }
                throw FavoriteToggleFailed(reason, resp.code)
            }
        }
    }
}