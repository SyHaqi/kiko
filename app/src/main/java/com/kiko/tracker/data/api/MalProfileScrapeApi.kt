package com.kiko.tracker.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/** Thrown when there's no stored cookie, or MAL bounced the request back to its login form. */
class MalSessionExpired : IOException()

data class MalFriend(
    val username: String,
    val profileUrl: String,
    val avatarUrl: String? = null
)

data class MalFavoriteEntry(
    val title: String,
    val url: String,
    // "TV·2011" for anime/manga, the work title for characters, blank for people/companies
    val subtitle: String? = null,
    val imageUrl: String? = null
)

data class MalFavorites(
    val anime: List<MalFavoriteEntry>,
    val manga: List<MalFavoriteEntry>,
    val characters: List<MalFavoriteEntry>,
    val people: List<MalFavoriteEntry>,
    val companies: List<MalFavoriteEntry>
)

/**
 * Scrapes myanimelist.net/profile/{username} for the handful of things the
 * official API doesn't expose at all: manga stats, friends, and favorites.
 * Same request/parse shape as ClubsApi/MalDetailScrapeApi, except this one
 * needs a logged-in session cookie (see MalSessionCookie / MalLoginWebView)
 * rather than just a desktop user-agent — MAL serves a stripped-down page
 * (or bounces to login.php) without one.
 */
class MalProfileScrapeApi(context: Context) {

    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    private fun fetchProfileDocument(username: String): Document {
        val cookie = session.get() ?: throw MalSessionExpired()

        val request = Request.Builder()
            .url("https://myanimelist.net/profile/$username")
            .header("Cookie", cookie)
            .header("User-Agent", MAL_DESKTOP_USER_AGENT)
            .build()

        client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            // An expired/invalid cookie gets redirected to the login form.
            if (finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")) {
                throw MalSessionExpired()
            }
            if (!resp.isSuccessful) throw IOException("MAL profile scrape failed (${resp.code}): $username")
            return Jsoup.parse(body, finalUrl)
        }
    }

    /** Fetches manga stats for [username] and returns [profile] with those fields filled in. */
    suspend fun applyMangaStats(profile: MalProfile, username: String): MalProfile =
        withContext(Dispatchers.IO) {
            val doc = fetchProfileDocument(username)
            val mangaBlock = doc.selectFirst("div.stats.manga")
                ?: return@withContext profile // markup changed or block missing; leave profile as-is

            val days = mangaBlock.selectFirst("div.stat-score .di-tc.al")
                ?.text()?.substringAfter("Days:")?.trim()?.toDoubleOrNull() ?: 0.0
            val meanScore = mangaBlock.selectFirst("div.stat-score .score-label")
                ?.text()?.toDoubleOrNull() ?: 0.0

            val statusCounts = mangaBlock.select("ul.stats-status li").associate { li ->
                val label = li.selectFirst("a")?.text().orEmpty()
                val count = li.selectFirst("span.di-ib.fl-r")
                    ?.text()?.replace(",", "")?.toIntOrNull() ?: 0
                label to count
            }
            val dataCounts = mangaBlock.select("ul.stats-data li").associate { li ->
                val spans = li.select("span")
                val label = spans.getOrNull(0)?.text().orEmpty()
                val value = spans.getOrNull(1)?.text()?.replace(",", "")?.toIntOrNull() ?: 0
                label to value
            }

            profile.copy(
                mangaDaysRead = days,
                mangaMeanScore = meanScore,
                mangaReading = statusCounts["Reading"] ?: 0,
                mangaCompleted = statusCounts["Completed"] ?: 0,
                mangaOnHold = statusCounts["On-Hold"] ?: 0,
                mangaDropped = statusCounts["Dropped"] ?: 0,
                mangaPlanToRead = statusCounts["Plan to Read"] ?: 0,
                mangaTotalEntries = dataCounts["Total Entries"] ?: 0,
                mangaReread = dataCounts["Reread"] ?: 0,
                mangaChaptersRead = dataCounts["Chapters"] ?: 0,
                mangaVolumesRead = dataCounts["Volumes"] ?: 0
            )
        }

    suspend fun friends(username: String): List<MalFriend> = withContext(Dispatchers.IO) {
        val doc = fetchProfileDocument(username)
        doc.select("div.user-friends a.icon-friend").map { a ->
            MalFriend(
                username = a.text().ifBlank { a.attr("title") },
                profileUrl = a.attr("abs:href"),
                avatarUrl = a.attr("data-bg").ifBlank { null }
            )
        }
    }

    suspend fun favorites(username: String): MalFavorites = withContext(Dispatchers.IO) {
        val doc = fetchProfileDocument(username)

        fun section(containerId: String): List<MalFavoriteEntry> {
            val container = doc.selectFirst("div#$containerId") ?: return emptyList()
            return container.select("ul.fav-slide li.btn-fav").map { li ->
                val a = li.selectFirst("a")
                MalFavoriteEntry(
                    title = li.attr("title").ifBlank { a?.selectFirst("span.title")?.text().orEmpty() },
                    url = a?.attr("abs:href").orEmpty(),
                    subtitle = a?.selectFirst("span.users")?.text()?.ifBlank { null },
                    imageUrl = a?.selectFirst("img")?.attr("data-src")?.ifBlank { null }
                )
            }
        }

        MalFavorites(
            anime = section("anime_favorites"),
            manga = section("manga_favorites"),
            characters = section("character_favorites"),
            people = section("person_favorites"),
            companies = section("company_favorites")
        )
    }
}