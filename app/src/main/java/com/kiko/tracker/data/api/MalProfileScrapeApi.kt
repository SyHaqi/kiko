package com.kiko.tracker.data.api

import android.content.Context
import com.kiko.tracker.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

// The handful of "About" fields MAL prints as plain text next to a friend's
// avatar (ul.user-status on the profile page) rather than as part of any
// stats block. Kept as raw scraped strings — e.g. joined = "Aug 7, 2024" —
// rather than trying to force them into MalProfile.joinedAt's ISO shape
// (that field is only ever ISO when it comes from the official API for the
// signed-in user; formatFullDate's `.take(10)` assumption would mangle a
// human-readable string like this one).
data class MalProfileHeader(
    val username: String,
    val avatarUrl: String? = null,
    val lastOnline: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val joined: String? = null,
)

// Everything FriendProfileScreen needs for a given username in one scrape:
// header info + both anime and manga stats blocks. friends()/favorites()
// stay separate calls (see below) since FriendsFavoritesScreen already
// loads those lazily/independently, same as it does for the signed-in user.
data class MalFriendProfile(val header: MalProfileHeader, val stats: MalProfile)

// One cover in an About Me row ("Last completed anime", "Publishing manga",
// etc.) — MAL's about-me editor auto-generates these from the user's list,
// so there's no scraped title text to lean on (unlike MalFavoriteEntry,
// where the desktop markup does print one); [title] is instead recovered
// from the title's URL slug. [type] drives which tap handler fires — see
// AboutMeCard/malIdFromFavoriteUrl in ProfileScreen.kt, same id-from-url
// trick the Favorites rows already use.
data class MalAboutMeItem(val title: String, val url: String, val imageUrl: String?, val type: MediaType?)

// One row in the About Me widget, in on-page order.
data class MalAboutMeSection(val heading: String, val items: List<MalAboutMeItem>)

// MAL's free-form "About Me" profile widget: an optional banner image, a
// display name/title, a short intro blurb, and up to a handful of
// auto-generated rows (currently watching/reading, last completed, etc.).
// Entirely opt-in on MAL's side — a user who hasn't touched the about-me
// editor has none of this, hence [isEmpty]. The three *Color fields are the
// one part of the user's own about-me theme worth carrying into Kiko (see
// AboutMeCard) — everything else about that theme (fonts, background
// patterns) is left alone rather than reconstructed.
data class MalAboutMe(
    val mainVisualUrl: String? = null,
    val displayName: String? = null,
    val introText: String? = null,
    val headerTextColor: String? = null,
    val bodyTextColor: String? = null,
    val backgroundColor: String? = null,
    val sections: List<MalAboutMeSection> = emptyList(),
) {
    val isEmpty: Boolean get() = mainVisualUrl == null && displayName.isNullOrBlank() && introText.isNullOrBlank() && sections.isEmpty()
}

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

    /**
     * One-shot scrape of everything FriendProfileScreen needs for [username]:
     * avatar/last-online/gender/birthday/joined plus both anime and manga
     * stats blocks — none of which are reachable through the official API
     * for anyone but the signed-in user (MalApi.profile() only ever hits
     * /users/@me). Same status-block shape as applyMangaStats above, just
     * also pulling the anime side and the header line.
     */
    suspend fun fullProfile(username: String): MalFriendProfile = withContext(Dispatchers.IO) {
        val doc = fetchProfileDocument(username)

        val avatarUrl = doc.selectFirst("div.user-image img")?.attr("data-src")?.ifBlank { null }
        val aboutFields = doc.select("ul.user-status.border-top.pb8.mb4 li.clearfix").associate { li ->
            li.selectFirst("span.user-status-title")?.text().orEmpty() to li.selectFirst("span.user-status-data")?.text().orEmpty()
        }
        val header = MalProfileHeader(
            username = username,
            avatarUrl = avatarUrl,
            lastOnline = aboutFields["Last Online"]?.ifBlank { null },
            gender = aboutFields["Gender"]?.ifBlank { null },
            birthday = aboutFields["Birthday"]?.ifBlank { null },
            joined = aboutFields["Joined"]?.ifBlank { null },
        )

        // Same shape as applyMangaStats' mangaBlock parsing above, just
        // generalized over the selector so it can pull either stats.anime
        // or stats.manga off the same document without a second fetch.
        fun statusAndDataCounts(block: Element): Pair<Map<String, Int>, Map<String, Int>> {
            val statusCounts = block.select("ul.stats-status li").associate { li ->
                val label = li.selectFirst("a")?.text().orEmpty()
                val count = li.selectFirst("span.di-ib.fl-r")?.text()?.replace(",", "")?.toIntOrNull() ?: 0
                label to count
            }
            val dataCounts = block.select("ul.stats-data li").associate { li ->
                val spans = li.select("span")
                val label = spans.getOrNull(0)?.text().orEmpty()
                val value = spans.getOrNull(1)?.text()?.replace(",", "")?.toIntOrNull() ?: 0
                label to value
            }
            return statusCounts to dataCounts
        }
        fun days(block: Element) = block.selectFirst("div.stat-score .di-tc.al")?.text()?.substringAfter("Days:")?.trim()?.toDoubleOrNull() ?: 0.0
        fun meanScore(block: Element) = block.selectFirst("div.stat-score .score-label")?.text()?.toDoubleOrNull() ?: 0.0

        val animeBlock = doc.selectFirst("div.stats.anime")
        val (animeStatus, animeData) = animeBlock?.let(::statusAndDataCounts) ?: (emptyMap<String, Int>() to emptyMap())
        val mangaBlock = doc.selectFirst("div.stats.manga")
        val (mangaStatus, mangaData) = mangaBlock?.let(::statusAndDataCounts) ?: (emptyMap<String, Int>() to emptyMap())

        val stats = MalProfile(
            name = username,
            picture = avatarUrl.orEmpty(),
            gender = header.gender.orEmpty(),
            animeDaysWatched = animeBlock?.let(::days) ?: 0.0,
            animeMeanScore = animeBlock?.let(::meanScore) ?: 0.0,
            animeEpisodesWatched = animeData["Episodes"] ?: 0,
            animeTotalEntries = animeData["Total Entries"] ?: 0,
            animeWatching = animeStatus["Watching"] ?: 0,
            animeCompleted = animeStatus["Completed"] ?: 0,
            animeOnHold = animeStatus["On-Hold"] ?: 0,
            animeDropped = animeStatus["Dropped"] ?: 0,
            animePlanToWatch = animeStatus["Plan to Watch"] ?: 0,
            mangaDaysRead = mangaBlock?.let(::days) ?: 0.0,
            mangaMeanScore = mangaBlock?.let(::meanScore) ?: 0.0,
            mangaChaptersRead = mangaData["Chapters"] ?: 0,
            mangaVolumesRead = mangaData["Volumes"] ?: 0,
            mangaReread = mangaData["Reread"] ?: 0,
            mangaTotalEntries = mangaData["Total Entries"] ?: 0,
            mangaReading = mangaStatus["Reading"] ?: 0,
            mangaCompleted = mangaStatus["Completed"] ?: 0,
            mangaOnHold = mangaStatus["On-Hold"] ?: 0,
            mangaDropped = mangaStatus["Dropped"] ?: 0,
            mangaPlanToRead = mangaStatus["Plan to Read"] ?: 0,
            // joinedAt intentionally left blank — see MalProfileHeader's doc
            // comment above; the human-readable "Joined" string lives on
            // header instead, so ProfileStatsSection's ISO-only `.take(10)`
            // formatting (built for the official API's date shape) never
            // sees a value it would mangle.
        )

        MalFriendProfile(header, stats)
    }

    /**
     * Scrapes the free-form "About Me" widget off [username]'s profile page
     * (div#modern-about-me-inner) — banner, display name, intro blurb, and
     * whichever auto-generated rows (currently watching/reading, last
     * completed, publishing) that user's about-me editor has turned on.
     * Returns [MalAboutMe.isEmpty] when the user hasn't set one up at all.
     */
    suspend fun aboutMe(username: String): MalAboutMe = withContext(Dispatchers.IO) {
        val doc = fetchProfileDocument(username)
        val root = doc.selectFirst("div#modern-about-me-inner") ?: return@withContext MalAboutMe()

        // The three theme colors a user picks in MAL's about-me editor are
        // written as CSS custom properties in a <style> block just above
        // #modern-about-me — pulled out with a regex since Jsoup doesn't
        // resolve CSS vars for us.
        val styleText = doc.select("style").joinToString("\n") { it.data() }
        fun cssVar(name: String) = Regex("--$name:\\s*(#[0-9a-fA-F]{3,8})").find(styleText)?.groupValues?.getOrNull(1)

        val mainVisualUrl = root.selectFirst(".l-mainvisual img")?.attr("abs:src")?.ifBlank { null }
        val displayName = root.selectFirst(".l-intro-ttl h2 span")?.text()?.ifBlank { null }
        val introText = root.selectFirst(".c-intro-description .c-aboutme-text")?.text()?.ifBlank { null }

        // Both "N_M items per row" layouts (l-listitem-5_5_items,
        // l-listitem-3_2_items) share the same inner ul.l-listitem-list
        // shape — a 10-item block just splits it across two row1/row2
        // <ul>s, which this selector picks up together in document order.
        val sections = root.select(".l-listitem-5_5_items, .l-listitem-3_2_items").mapNotNull { block ->
            val heading = block.selectFirst("h3.c-aboutme-ttl-lv2 span")?.text()?.ifBlank { null } ?: return@mapNotNull null
            val items = block.select("ul.l-listitem-list li.l-listitem-list-item a").mapNotNull { a ->
                val src = a.selectFirst("img")?.attr("abs:src")?.ifBlank { null } ?: return@mapNotNull null
                val href = a.attr("href")
                val type = when {
                    href.startsWith("/anime/") -> MediaType.Anime
                    href.startsWith("/manga/") -> MediaType.Manga
                    else -> null
                }
                // No title text in this widget's markup (unlike Favorites,
                // which prints one) — recovered from the url slug instead.
                val title = href.trimEnd('/').substringAfterLast('/').replace('_', ' ').ifBlank { "Untitled" }
                MalAboutMeItem(title = title, url = a.attr("abs:href"), imageUrl = src, type = type)
            }
            if (items.isEmpty()) null else MalAboutMeSection(heading, items)
        }

        MalAboutMe(
            mainVisualUrl = mainVisualUrl,
            displayName = displayName,
            introText = introText,
            headerTextColor = cssVar("about-me-color-header-text"),
            bodyTextColor = cssVar("about-me-color-body-text"),
            backgroundColor = cssVar("about-me-color-background"),
            sections = sections,
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
                    // Same resize-proxy/company-logo-size fixup used by every
                    // other MAL scrape (see fullResMalImage) — the raw
                    // data-src here is the small carousel thumbnail, not the
                    // full-size cover/logo.
                    imageUrl = a?.selectFirst("img")?.attr("data-src")?.ifBlank { null }?.let(::fullResMalImage)
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