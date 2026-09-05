package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.IOException
import com.kiko.tracker.data.model.CharacterEntry
import com.kiko.tracker.data.model.CompanyNews
import com.kiko.tracker.data.model.FeaturedArticleEntry
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.RelatedEntry
import com.kiko.tracker.data.model.ScoreStats
import com.kiko.tracker.data.model.VoiceActorEntry

private const val MAL = "https://myanimelist.net"

// Scrapes anime/manga detail-page widgets
// (or that we've deliberately
// (same approach as ClubsApi/MalPeopleApi/StacksApi
//
// - Related Entries: the
// only in practice —
// comes back empty for
// /manga/{id}, even though the
// direction regardless of which
// - Recommendations: the official
// but comes back thin
// Recommendations slider fills that
// user picks live on
// fetchUserRecommendations). The app shows
// whatever the slider adds
// over the other —
// - Characters & Voice
// proxy). Moved to a
// longer depends on Tenrai/Jikan
//
// Verified against a real
// Recommendations slider, and the
// wasn't available to verify
// (matched by /anime/ vs
// specific class names.
class MalDetailScrapeApi {
    private val client = NetworkClient.shared

    data class PageExtras(
        val related: List<RelatedEntry>,
        val recommended: List<RecommendedEntry>,
        // Recent News / Recent
        // widgets sitting below the
        // parsed off this same
        // CompanyNews (see CompanyModels.kt) since
        // own Recent News card
        // DetailForumDiscussionRow can share ForumsScreen's
        val news: List<CompanyNews> = emptyList(),
        val forumDiscussion: List<ForumTopic> = emptyList(),
        val featuredArticles: List<FeaturedArticleEntry> = emptyList(),
        // "Available At" links (official
        // below Statistics — same
        // visible text, value is
        // with CompanyDetailScreen's own link-chip
        val links: List<Pair<String, String>> = emptyList(),
    )

    suspend fun fetch(id: Int, type: MediaType): PageExtras = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val doc = client.fetchMalDocument("$MAL/$kind/$id")
        PageExtras(
            related = parseRelated(doc),
            recommended = parseRecommended(doc, id),
            news = parseDetailNews(doc),
            forumDiscussion = parseDetailForumDiscussion(doc, limit = 2),
            featuredArticles = parseFeaturedArticles(doc, limit = 2),
            links = parseAvailableLinks(doc),
        )
    }

    // "Available At" sidebar block
    // <div class="external_links"> of <a>
    // for its visible label
    // "Resources" block (AniDB/ANN/Wikipedia/...) built
    // div.external_links markup right below
    // heading specifically rather than
    // silently grab whichever of
    private fun parseAvailableLinks(doc: Document): List<Pair<String, String>> {
        val block = doc.select("h2").firstOrNull { it.text().trim() == "Available At" }
            ?.nextElementSibling()
            ?.takeIf { it.hasClass("external_links") }
            ?: return emptyList()
        return block.select("a[href]").mapNotNull { a ->
            val url = a.attr("abs:href")
            val label = a.selectFirst("div.caption")?.text()?.trim().orEmpty()
            if (url.isBlank() || label.isBlank()) null else label to url
        }
    }

    // Fetch characters row for
    // see LibraryViewModel.loadCharacters). Lets a
    // both attempts below coming
    // swallowed here, so it
    // listed.
    //
    // Same slug requirement as
    // routing treats the slug
    // requesting "/{kind}/{id}/characters" with no
    // itself — which silently
    // rather than the characters
    // is kept as a
    // specially.
    suspend fun fetchCharacters(id: Int, type: MediaType, title: String): List<CharacterEntry> = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val slugged = runCatching { parseCharacters(client.fetchMalDocument("$MAL/$kind/$id/${malSlug(title)}/characters")) }
        if ((slugged.getOrNull()?.size ?: 0) > 0) return@withContext slugged.getOrThrow()
        val fallback = runCatching { parseCharacters(client.fetchMalDocument("$MAL/$kind/$id/characters")) }
        fallback.getOrNull()?.let { return@withContext it }
        // Both requests came back
        // failure rather than a
        // reporting an empty list.
        throw slugged.exceptionOrNull() ?: fallback.exceptionOrNull()
        ?: IOException("MAL characters request failed: $kind/$id")
    }

    // Genuine user-submitted recommendation pairs,
    // "/userrecs" subpage — merged
    // (see parseRecommended below) rather
    // real picks alongside MAL's
    // happened to have more.
    // page, which listed ten-plus
    //
    // Same slug requirement as
    suspend fun fetchUserRecommendations(id: Int, type: MediaType, title: String): List<RecommendedEntry> = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val slugged = runCatching { parseUserRecommendations(client.fetchMalDocument("$MAL/$kind/$id/${malSlug(title)}/userrecs")) }
        if ((slugged.getOrNull()?.size ?: 0) > 0) return@withContext slugged.getOrThrow()
        val fallback = runCatching { parseUserRecommendations(client.fetchMalDocument("$MAL/$kind/$id/userrecs")) }
        fallback.getOrNull() ?: slugged.getOrDefault(emptyList())
    }

    // Each recommendation pairing on
    // cover-thumbnail cell, and an
    // recommender's writeup, and —
    // recommendations by N more
    // visible writeup is the
    // the hidden writeups themselves,
    private fun parseUserRecommendations(doc: Document): List<RecommendedEntry> =
        doc.select("table:has(div[id^=raArea])").mapNotNull { table ->
            val cells = table.selectFirst("tr")?.children() ?: return@mapNotNull null
            val picCell = cells.getOrNull(0) ?: return@mapNotNull null
            val infoCell = cells.getOrNull(1) ?: return@mapNotNull null

            // The paired title's own
            // recommendations by N more
            // count) but isn't an
            // pattern picks out the
            val titleLink = infoCell.selectFirst("a[href*=/anime/]:has(strong)")
                ?: infoCell.selectFirst("a[href*=/manga/]:has(strong)")
                ?: return@mapNotNull null
            val (malId, malType) = malRefFromUrl(titleLink.attr("abs:href")) ?: return@mapNotNull null
            val recTitle = titleLink.selectFirst("strong")?.text()?.trim().orEmpty()
            if (recTitle.isBlank()) return@mapNotNull null

            val cover = picCell.selectFirst("img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
                ?.let(::fullResMalImage) ?: ""

            val moreCount = infoCell.selectFirst("a.js-similar-recommendations-button strong")
                ?.text()?.trim()?.toIntOrNull() ?: 0

            RecommendedEntry(malId = malId, title = recTitle, cover = cover, votes = 1 + moreCount, malType = malType, isAuto = false)
        }
            // The "/userrecs" subpage can
            // (each recommender's write-up gets
            // separate recommendations pointing at
            // per write-up). Left undeduped,
            // as two entries sharing
            // the key comment above
            // below already dedupes its
            // do it here too,
            .distinctBy { it.malId to it.malType }

    // Community score breakdown (1-10)
    // the main detail page
    // the user actually opens
    //
    // MAL's own routing is
    // positional but not actually
    // Requesting "/{kind}/{id}/stats" (no slug)
    // parses "stats" itself as
    // detail page instead (which
    // like "no score data"
    // no-slug form is kept
    // with something MAL treats
    suspend fun fetchScoreStats(id: Int, type: MediaType, title: String): ScoreStats = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val slugged = runCatching { parseScoreStats(client.fetchMalDocument("$MAL/$kind/$id/${malSlug(title)}/stats")) }.getOrNull()
        if (slugged != null && slugged.total > 0) return@withContext slugged
        runCatching { parseScoreStats(client.fetchMalDocument("$MAL/$kind/$id/stats")) }.getOrDefault(slugged ?: ScoreStats())
    }

    // MAL's own slug convention:
    // collapsed to a single
    // slug "Maid-san_wa_Taberu_dake"). Doesn't need
    // actual slug for this
    private fun malSlug(title: String): String {
        val slug = title.trim().replace(Regex("[^A-Za-z0-9-]+"), "_").trim('_')
        return slug.ifBlank { "_" }
    }

    // "table.score-stats" rows go from
    // tag next to the
    // was verified against.
    private fun parseScoreStats(doc: Document): ScoreStats {
        val counts = doc.select("table.score-stats tr").mapNotNull { row ->
            val score = row.selectFirst("td.score-label")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val votes = Regex("\\d[\\d,]*").find(row.select("small").text())?.value?.replace(",", "")?.toIntOrNull() ?: 0
            score to votes
        }.toMap()
        return ScoreStats(counts)
    }

    // Each character on the
    // (table.js-anime-character-table): a picture cell,
    // favorites), and a voice-actor
    // dub language). Only the
    // fetchCharacters — since the
    private fun parseCharacters(doc: Document): List<CharacterEntry> =
        doc.select("table.js-anime-character-table").mapNotNull { table ->
            val cells = table.selectFirst("tr")?.children() ?: return@mapNotNull null
            val imageCell = cells.getOrNull(0) ?: return@mapNotNull null
            val infoCell = cells.getOrNull(1) ?: return@mapNotNull null
            val vaCell = cells.getOrNull(2)

            // The character link lives
            // the more reliable of
            // has managed to load
            val nameHeading = infoCell.selectFirst("h3.h3_character_name") ?: return@mapNotNull null
            val link = nameHeading.parent()?.takeIf { it.tagName() == "a" }
                ?: imageCell.selectFirst("a[href*=/character/]")
                ?: return@mapNotNull null
            val href = link.attr("abs:href")
            val malId = Regex("/character/(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null

            val name = reorderMalPersonName(nameHeading.text().trim())
            if (name.isBlank()) return@mapNotNull null

            val image = imageCell.selectFirst("img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
                ?.let(::fullResMalImage) ?: ""

            // Of the info cell's
            // no h3 (that's the
            // "color: #787878" — the
            val role = infoCell.select("div.spaceit_pad")
                .firstOrNull { it.selectFirst("h3") == null && !it.hasAttr("style") }
                ?.let { normalizeWhitespace(it).trim() }
                ?.ifBlank { null } ?: "Supporting"

            val japaneseVa = vaCell?.select("tr.js-anime-character-va-lang")?.firstNotNullOfOrNull { vaRow ->
                val lang = vaRow.selectFirst("div.js-anime-character-language")?.text()?.trim().orEmpty()
                if (!lang.equals("Japanese", ignoreCase = true)) return@firstNotNullOfOrNull null
                val vaLink = vaRow.selectFirst("a[href*=/people/]") ?: return@firstNotNullOfOrNull null
                val vaHref = vaLink.attr("abs:href")
                val vaId = Regex("/people/(\\d+)").find(vaHref)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@firstNotNullOfOrNull null
                val vaImage = vaRow.selectFirst("img")
                    ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::fullResMalImage) ?: ""
                VoiceActorEntry(
                    malId = vaId,
                    name = reorderMalPersonName(vaLink.text().trim()),
                    image = vaImage,
                    url = vaHref,
                )
            }

            CharacterEntry(malId = malId, name = name, image = image, role = role, url = href, japaneseVoiceActor = japaneseVa)
        }

    // malId + malType read
    // we're on, so a
    // both come through correctly.
    private fun malRefFromUrl(url: String): Pair<Int, String>? {
        val match = Regex("/(anime|manga)/(\\d+)").find(url) ?: return null
        val id = match.groupValues[2].toIntOrNull() ?: return null
        return id to match.groupValues[1]
    }

    // The Recommendations slider's own
    // a plain "/anime/{id}" page
    // they link to MAL's
    // a and b just
    // "the recommended one" in
    // silently took whichever id
    // whenever it was the
    // the wrong id entirely.
    // uses to patch a
    // comment on covers "coming
    // uncorrected, which is what
    // Picking whichever of the
    // Falls back to malRefFromUrl
    // slider, which link to
    private fun malRefFromRecommendationUrl(url: String, selfId: Int): Pair<Int, String>? {
        val pairMatch = Regex("/recommendations/(anime|manga)/(\\d+)-(\\d+)").find(url) ?: return malRefFromUrl(url)
        val type = pairMatch.groupValues[1]
        val a = pairMatch.groupValues[2].toIntOrNull() ?: return null
        val b = pairMatch.groupValues[3].toIntOrNull() ?: return null
        return (if (a == selfId) b else a) to type
    }

    private fun parseRelated(doc: Document): List<RelatedEntry> =
        doc.select("div.related-entries div.entry").mapNotNull { entry ->
            val link = entry.selectFirst(".content .title a") ?: entry.selectFirst(".image a") ?: return@mapNotNull null
            val (malId, malType) = malRefFromUrl(link.attr("abs:href")) ?: return@mapNotNull null
            // e.g. "Adaptation\n(Manga)" -> "Adaptation
            val relation = entry.selectFirst(".content .relation")?.let { normalizeWhitespace(it) }
                ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            val title = normalizeWhitespace(link).trim()
            if (title.isBlank()) return@mapNotNull null
            val cover = entry.selectFirst(".image img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
                ?.let(::fullResMalImage) ?: ""
            RelatedEntry(relation = relation.ifBlank { "Related" }, title = title, malId = malId, malType = malType, cover = cover)
        }

    // "?suggestion" only marks the
    // in this same slider
    // query param at all.
    // dropped every real entry
    // what let a stale/incorrect
    // scrape win the merge
    // LibraryViewModel.ensureDetailFetched) — matching both
    // that merge actually have
    //
    // This is the small
    // fetchUserRecommendations' dedicated "/userrecs" scrape
    // (see LibraryViewModel.ensureDetailFetched), so AutoRec
    // even for titles that
    private fun parseRecommended(doc: Document, selfId: Int): List<RecommendedEntry> =
        doc.select("a[href*='?suggestion'], a[href*='/recommendations/anime/'], a[href*='/recommendations/manga/']").mapNotNull { a ->
            val (malId, malType) = malRefFromRecommendationUrl(a.attr("abs:href"), selfId) ?: return@mapNotNull null
            val title = a.selectFirst(".title")?.text()?.takeIf { it.isNotBlank() }
                ?: a.closest("li")?.attr("title")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val cover = a.selectFirst("img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
                ?.let(::fullResMalImage) ?: ""
            val usersText = a.selectFirst(".users")?.text().orEmpty()
            val isAuto = usersText.contains("AutoRec", ignoreCase = true)
            val votes = if (isAuto) 0 else Regex("\\d+").find(usersText)?.value?.toIntOrNull() ?: 0
            RecommendedEntry(malId = malId, title = title, cover = cover, votes = votes, malType = malType, isAuto = isAuto)
        }.distinctBy { it.malId to it.malType }

    // "Recent News" widget (h2#recent_news)
    // div.clearfix (thumbnail + title
    // comments)" line), not the
    // scrapes off the company
    // Featured Articles" widget uses
    // element siblings after the
    // aren't wrapped in a
    // (the next widget's own
    // reuses ForumTopicScreen, same as
    // MalCompanyApi.parseNews's own comment) —
    // than opened with a
    private fun parseDetailNews(doc: Document, limit: Int = 5): List<CompanyNews> {
        val header = doc.selectFirst("h2#recent_news") ?: return emptyList()
        val results = mutableListOf<CompanyNews>()
        var sib: Element? = header.parent()?.nextElementSibling()
        while (sib != null && results.size < limit) {
            if (sib.selectFirst("h2") != null) break
            val titleLink = sib.selectFirst("p.spaceit a")
            if (sib.tagName() == "div" && titleLink != null) {
                val title = titleLink.text().trim()
                val topicId = sib.selectFirst("a[href*=topicid=]")?.attr("abs:href")
                    ?.let { Regex("topicid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                val image = sib.selectFirst("img")
                    ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::fullResMalImage) ?: ""
                // The snippet paragraph ends
                // before it in the
                // truncated sentence (e.g. "...Th...read
                // clone before reading text
                val snippet = sib.selectFirst("div.clearfix p")?.let { p ->
                    val clone = p.clone(); clone.select("a").remove(); clone.text().trim()
                }.orEmpty()
                val date = sib.selectFirst("p.lightLink")?.text()?.substringBefore(" by ")?.trim().orEmpty()
                if (title.isNotBlank() && topicId != null) {
                    results.add(CompanyNews(topicId = topicId, title = title, image = image, snippet = snippet, date = date))
                }
            }
            sib = sib.nextElementSibling()
        }
        return results
    }

    // "Recent Forum Discussion" widget
    // table#forumTopics, one <tr data-topic-id="...">
    // the row/cell markup rather
    // Title link is picked
    // link generally) so this
    // pagination number from a
    // final cell's own trailing
    // there's no wrapping element
    private fun parseDetailForumDiscussion(doc: Document, limit: Int): List<ForumTopic> =
        doc.select("table#forumTopics tr[data-topic-id]").take(limit).mapNotNull { row ->
            val id = row.attr("data-topic-id").toIntOrNull() ?: return@mapNotNull null
            val titleCell = row.selectFirst("td.forum_boardrow1") ?: return@mapNotNull null
            val titleLink = titleCell.selectFirst("a[data-ga-click-type=anime-recent-forum-discussion]")
                ?: return@mapNotNull null
            val title = titleLink.text().trim()
            if (title.isBlank()) return@mapNotNull null
            val authorName = titleCell.selectFirst("span.forum_postusername a")?.text()?.trim().orEmpty()
            val createdAt = titleCell.selectFirst("span.lightLink")?.text()?.trim().orEmpty()
            val cells = row.select("td")
            val postCount = cells.getOrNull(2)?.text()?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: 0
            val lastCell = cells.getOrNull(3)
            val lastPostAuthorName = lastCell?.selectFirst("a")?.text()?.trim().orEmpty()
            val lastPostAt = (lastCell?.childNodes()?.lastOrNull() as? TextNode)?.text()?.trim().orEmpty()
            ForumTopic(
                id = id, title = title, createdAt = createdAt, author = ForumUser(name = authorName),
                postCount = postCount, lastPostAt = lastPostAt, lastPostAuthor = ForumUser(name = lastPostAuthorName),
            )
        }

    // "Recent Featured Articles" widget
    // that actually matches the
    // scrapes (p.title/div.text/p.info), not this
    // News" widget above. No
    // "/featured/{id}/{slug}" article page, so
    // than routing through ForumTopicScreen.
    private fun parseFeaturedArticles(doc: Document, limit: Int): List<FeaturedArticleEntry> {
        val header = doc.selectFirst("h2#recent_featured_articles") ?: return emptyList()
        val container = header.parent()?.nextElementSibling() ?: return emptyList()
        return parseFeaturedArticleUnits(container.select("div.news-list div.news-unit"), limit)
    }

    // Shared by parseFeaturedArticles above
    // and parseHomeFeaturedArticles below (home
    // both are the exact
    // div.information b), just embedded
    // per-unit field extraction only
    private fun parseFeaturedArticleUnits(units: List<Element>, limit: Int): List<FeaturedArticleEntry> =
        units.take(limit).mapNotNull { unit ->
            val titleLink = unit.selectFirst("p.title a") ?: return@mapNotNull null
            val title = titleLink.text().trim()
            if (title.isBlank()) return@mapNotNull null
            val url = titleLink.attr("abs:href")
            val image = unit.selectFirst("img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
                ?.let(::fullResMalImage) ?: ""
            val snippet = unit.selectFirst("div.text")?.text()?.trim().orEmpty()
            val author = unit.selectFirst("p.info a")?.text()?.trim().orEmpty()
            val views = unit.selectFirst("div.information b")?.text()?.trim().orEmpty()
            FeaturedArticleEntry(url = url, title = title, image = image, snippet = snippet, author = author, views = views)
        }

    // Home page's own "Featured
    // "https://myanimelist.net/") — feeds Home's
    // the Snapshots row (see
    // cap. A plain, unauthenticated
    // isn't keyed to any
    // into PageExtras.
    suspend fun fetchHomeFeaturedArticles(limit: Int = 3): List<FeaturedArticleEntry> = withContext(Dispatchers.IO) {
        val doc = client.fetchMalDocument(MAL)
        val container = doc.selectFirst("div.widget.featured div.news-list") ?: return@withContext emptyList()
        parseFeaturedArticleUnits(container.select("div.news-unit"), limit)
    }
}