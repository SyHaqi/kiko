package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.IOException
import com.kiko.tracker.data.model.ArticleBlock
import com.kiko.tracker.data.model.CharacterEntry
import com.kiko.tracker.data.model.CompanyNews
import com.kiko.tracker.data.model.FeaturedArticleContent
import com.kiko.tracker.data.model.FeaturedArticleEntry
import com.kiko.tracker.data.model.FeaturedTag
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.RelatedEntry
import com.kiko.tracker.data.model.ReviewEntry
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

    // Reviews, scraped in place
    // of the old Tenrai/Jikan
    // proxy (which had started
    // coming back empty). "spoiler=on"
    // matches unchecking MAL's own
    // "Spoiler" filter toggle, which
    // otherwise hides spoiler reviews
    // by default — same
    // slug requirement as the
    // other subpages above.
    suspend fun fetchReviews(id: Int, type: MediaType, title: String): List<ReviewEntry> = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val slugged = runCatching { parseReviews(client.fetchMalDocument("$MAL/$kind/$id/${malSlug(title)}/reviews?spoiler=on")) }
        if ((slugged.getOrNull()?.size ?: 0) > 0) return@withContext slugged.getOrThrow()
        val fallback = runCatching { parseReviews(client.fetchMalDocument("$MAL/$kind/$id/reviews?spoiler=on")) }
        fallback.getOrNull() ?: slugged.getOrDefault(emptyList())
    }

    // Verdict tags ("Recommended", "Mixed
    // Feelings", "Not Recommended") and
    // category tags ("Funny", "Well-written",
    // etc) print as plain
    // visible text on each
    // ".tag" div, so text()
    // already matches what the
    // old Jikan "tags" array
    // gave us. "Preliminary" carries
    // an episode-count span inside
    // the same div. "Spoiler"
    // is pulled out into
    // ReviewEntry.isSpoiler instead of staying
    // a tag, matching the
    // dedicated field the UI
    // already reads.
    private fun parseReviewTags(tagsDiv: Element?): Pair<List<String>, Boolean> {
        val divs = tagsDiv?.select("div.tag").orEmpty()
        val spoiler = divs.any { it.hasClass("spoiler") }
        val tags = divs.filterNot { it.hasClass("spoiler") }.mapNotNull { it.text().trim().takeIf { t -> t.isNotBlank() } }
        return tags to spoiler
    }

    // Rebuilds the review body
    // from "div.text": MAL renders
    // paragraph breaks as <br>
    // rather than separate <p>
    // tags, and tucks the
    // rest of a long
    // review inside a display:none
    // "js-hidden" span (still real
    // text to Jsoup, just
    // hidden behind a "Read
    // more" toggle in the
    // browser) — so a
    // plain .text() call would
    // both flatten every paragraph
    // onto one line and
    // pull in the "..."
    // ellipsis marker that sits
    // between the visible and
    // hidden portions. Walk the
    // node tree instead: turn
    // <br> into "\n", drop
    // the ellipsis span, and
    // collapse only the incidental
    // whitespace from the source
    // HTML's own indentation.
    private fun textWithLineBreaks(el: Element): String {
        val sb = StringBuilder()
        fun walk(node: org.jsoup.nodes.Node) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> when {
                    node.tagName() == "br" -> sb.append("\n")
                    node.hasClass("js-visible") -> {}
                    else -> node.childNodes().forEach(::walk)
                }
                else -> {}
            }
        }
        el.childNodes().forEach(::walk)
        return sb.toString()
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    // mapIndexedNotNull rather than mapNotNull:
    // the index feeds a
    // fallback id when a
    // review's permalink is missing
    // or unparseable, so malId
    // never silently collapses to
    // the same value (0)
    // across multiple reviews. LazyColumn/LazyRow
    // use malId as key= for
    // smooth scrolling — a
    // duplicate key there breaks
    // item identity across recomposition, which
    // is the opposite of
    // what key= is for.
    // Real MAL ids are
    // always positive, so a
    // negative, index-derived fallback can
    // never collide with a
    // genuine one.
    private fun parseReviews(doc: Document): List<ReviewEntry> =
        doc.select("div.review-element").mapIndexedNotNull { index, el ->
            val textDiv = el.selectFirst("div.text") ?: return@mapIndexedNotNull null
            val text = textWithLineBreaks(textDiv)
            if (text.isBlank()) return@mapIndexedNotNull null
            val profileLink = el.selectFirst("div.thumb a")?.attr("href").orEmpty()
            val username = el.selectFirst("div.username a")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: profileLink.trim('/').substringAfterLast("/").takeIf { it.isNotBlank() }
                ?: "Anonymous"
            val avatarImg = el.selectFirst("div.thumb img")
            val userImage = avatarImg?.attr("data-src")?.takeIf { it.isNotBlank() } ?: avatarImg?.attr("src").orEmpty()
            val score = el.selectFirst("div.rating span.num")?.text()?.trim()?.toIntOrNull() ?: 0
            val (tags, spoiler) = parseReviewTags(el.selectFirst("div.tags"))
            val url = el.selectFirst("div.open a")?.attr("href").orEmpty()
            val reactionScore = Regex("\"num\":(\\d+)").find(el.attr("data-reactions"))?.groupValues?.get(1)?.toIntOrNull() ?: 0
            ReviewEntry(
                malId = url.substringAfterLast("id=").toIntOrNull() ?: -(index + 1),
                username = username,
                userImage = userImage,
                review = text,
                score = score,
                tags = tags,
                reactionScore = reactionScore,
                isSpoiler = spoiler,
                url = url,
            )
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
            val tag = unit.selectFirst("div.tags .tag")?.text()?.trim().orEmpty()
            FeaturedArticleEntry(url = url, title = title, image = image, snippet = snippet, author = author, views = views, tag = tag)
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

    data class FeaturedArticlesPage(val articles: List<FeaturedArticleEntry>, val hasMore: Boolean)

    // Shared by the three "browse a page of Featured Article news-units"
    // entry points below (plain browse, tag filter, search) — same
    // news-unit/pagination shape on all three (matches the site's own
    // "1 - 20"/"21 - 40" pager labels), only the source URL differs.
    private suspend fun fetchFeaturedArticlesFrom(url: String, page: Int): FeaturedArticlesPage = withContext(Dispatchers.IO) {
        val doc = client.fetchMalDocument(url)
        val units = doc.select("div.news-list div.news-unit")
        val articles = parseFeaturedArticleUnits(units, units.size)
        val maxPage = doc.select("div.pagination a.link").mapNotNull { a ->
            Regex("[?&]p=(\\d+)").find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: page
        FeaturedArticlesPage(articles, hasMore = page < maxPage)
    }

    // Full "myanimelist.net/featured?p=N" browse list — 20 news-unit
    // entries per page. Deliberately skips the page-1-only "featured-pickup"
    // editorial pinning strip so every page parses the exact same
    // shape, rather than special-casing page 1 for four extra cards.
    suspend fun fetchFeaturedArticlesPage(page: Int): FeaturedArticlesPage =
        fetchFeaturedArticlesFrom("$MAL/featured?p=$page", page)

    // "myanimelist.net/featured/tag/{slug}?p=N" — Featured Articles scoped
    // to one tag chip off fetchFeaturedTags() below (e.g. "interview",
    // "cosplay"). Backs the tag-filter chip row on the Featured Articles screen.
    suspend fun fetchFeaturedArticlesByTag(tagSlug: String, page: Int): FeaturedArticlesPage =
        fetchFeaturedArticlesFrom("$MAL/featured/tag/$tagSlug?p=$page", page)

    // "myanimelist.net/featured/search?cat=featured&q=...&p=N" — full-text
    // search over Featured Articles. Backs the search icon on the
    // Featured Articles screen.
    suspend fun fetchFeaturedArticlesSearch(query: String, page: Int): FeaturedArticlesPage =
        fetchFeaturedArticlesFrom("$MAL/featured/search?cat=featured&q=${java.net.URLEncoder.encode(query, "UTF-8")}&p=$page", page)

    // "myanimelist.net/featured/tag" — the full category table (Interview,
    // Analysis, Cosplay, Studios, ...), each linking to its own
    // /featured/tag/{slug} browse page. Fetched once and cached by the
    // ViewModel (same shape as forum subboards) to fill the tag-filter chip row.
    suspend fun fetchFeaturedTags(): List<FeaturedTag> = withContext(Dispatchers.IO) {
        val doc = client.fetchMalDocument("$MAL/featured/tag")
        doc.select("div.news-tags-table a.tag-name-link").mapNotNull { a ->
            val name = a.selectFirst("span.tag-name")?.text()?.trim().orEmpty()
            val slug = a.attr("href").substringAfterLast("/featured/tag/").substringBefore("?")
            if (name.isBlank() || slug.isBlank()) null else FeaturedTag(name = name, slug = slug)
        }
    }

    // Single "/featured/{id}/{slug}" article page — the actual reader,
    // as opposed to parseFeaturedArticleUnits above which only ever
    // scrapes list-row summaries. No official API for this (same
    // situation as forum topics before MalApi.forumTopic existed),
    // so the whole article body is flattened into plain ArticleBlocks
    // here rather than carried as raw HTML into the ui.screens layer.
    suspend fun fetchFeaturedArticle(url: String): FeaturedArticleContent = withContext(Dispatchers.IO) {
        val doc = client.fetchMalDocument(url)
        val container = doc.selectFirst("div.news-container") ?: throw IOException("Article not found")
        val title = container.selectFirst("h1.title")?.text()?.trim().orEmpty()
        val infoBlock = container.selectFirst("div.news-info-block div.information")
        val author = infoBlock?.selectFirst("a")?.text()?.trim().orEmpty()
        val views = infoBlock?.selectFirst("b")?.text()?.trim().orEmpty()
        // Date sits as a bare text node between the byline's <br>
        // and the "| N views" segment — no class to select on it.
        val date = infoBlock?.html()?.substringAfter("<br>", "")?.substringBefore("|")
            ?.let { org.jsoup.Jsoup.parse(it).text().trim() }.orEmpty()
        val tags = container.select("div.tags .tag").map { it.text().trim() }.filter { it.isNotBlank() }
        val bodyEl = container.selectFirst("div.featured-article-body")
        val blocks = bodyEl?.let(::parseFeaturedArticleBody).orEmpty()
        val links = bodyEl?.let(::parseFeaturedArticleLinks).orEmpty()
        FeaturedArticleContent(title = title, author = author, date = date, views = views, tags = tags, blocks = blocks, links = links)
    }

    // Host suffix for MAL's own domain — links to MAL's own anime/people/forum
    // pages already surface elsewhere (inline text, Related Database Entries),
    // so they're excluded here rather than duplicated as "official link" chips.
    private val malHostRegex = Regex("""(^|\.)myanimelist\.net$""", RegexOption.IGNORE_CASE)

    // Pulls an advertorial article's own official/social links — the
    // "Official Site: <a>...</a>", "Official Discord: <a>...</a>", "Official
    // X: <a>...</a>", "Add to Wishlist: <a>...</a>" lines its Game
    // Information list (or equivalent inline links) carries — into the same
    // (label, url) shape CompanyDetail.links uses, deduped by URL, first
    // occurrence wins. Anchors that only wrap an <img> are skipped: those are
    // already surfaced as ArticleBlock.Image banners, not info links.
    //
    // Narrative articles (event reports, interviews) litter their body with
    // dozens of inline citation links — "Blink of Ray" -> youtube.com, a
    // setlist's worth of "unravel"/"GYUTTO!!"/... -> youtube.com, etc. None
    // of those are preceded by an explicit "Label: " prefix, so without a
    // filter every single one fell through to friendlyLinkLabel's generic
    // per-host name ("YouTube") and the Links section turned into dozens of
    // identically-labeled chips. Only two shapes are treated as a real
    // info-link now: an explicit "Label: <a>" line (checked for a literal
    // trailing colon, not just "whatever text happened to precede the
    // anchor" — that looser check also let a long, colon-less run-on
    // sentence get used verbatim as a chip's label), or a bare link to one
    // of the small set of recognized official/social hosts.
    private val officialFallbackHosts = listOf("facebook", "twitter", "x.com", "t.co", "instagram", "discord", "steampowered", "tiktok")
    private fun parseFeaturedArticleLinks(body: Element): List<Pair<String, String>> {
        val seen = LinkedHashMap<String, String>()
        for (a in body.select("a[href]")) {
            if (a.selectFirst("img") != null) continue
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            if (href.isBlank()) continue
            val host = runCatching { java.net.URI(href).host?.lowercase() }.getOrNull().orEmpty()
            if (host.isBlank() || malHostRegex.containsMatchIn(host)) continue
            if (seen.containsKey(href)) continue
            val ownText = a.text().trim()
            val container = a.closest("li") ?: a.closest("p")
            // Only the text *before* the anchor can be a "Label: " prefix —
            // text after it (e.g. a <br/>-separated sentence sharing the
            // same <p> as a bare "https://..." link, common in article
            // bodies) is unrelated prose, not part of the link's label, and
            // must never leak into the chip. Using container.text() minus
            // a removeSuffix(ownText) used to grab that trailing prose
            // whenever the anchor sat at the *start* of the paragraph
            // instead of the end, since the string then doesn't end with
            // ownText and removeSuffix is a no-op.
            val rawPrefix = container?.let { textBeforeNode(it, a) }?.trimEnd().orEmpty()
            // A real "Label:" line ends with a colon right before the
            // anchor — anything else preceding the link (a run-on sentence,
            // mid-paragraph prose) is not a label and must be discarded
            // rather than used as one.
            val explicitLabel = rawPrefix.takeIf { it.endsWith(":") }?.dropLast(1)?.trim()?.takeIf { it.isNotBlank() && it.length <= 40 }
            val label = explicitLabel ?: officialFallbackHosts.firstOrNull { it in host }?.let { friendlyLinkLabel(host, ownText) } ?: continue
            seen[href] = label
        }
        // Defensive cap — even a legitimate advertorial rarely lists more
        // than a handful of official links, so this guards against any
        // article shape this heuristic doesn't anticipate.
        return seen.map { (url, label) -> label to url }.take(8)
    }

    // Concatenates the text of `container`'s content that appears strictly
    // before `target` in document order, stopping as soon as `target` is
    // reached during the depth-first walk. Used instead of a naive
    // container.text() (which includes everything, before AND after) so a
    // link's own trailing sentence never gets mistaken for its label.
    private fun textBeforeNode(container: Element, target: Element): String {
        val sb = StringBuilder()
        fun walk(node: Node): Boolean {
            if (node === target) return true
            if (node is TextNode) {
                sb.append(node.text())
            } else {
                for (child in node.childNodes()) {
                    if (walk(child)) return true
                }
            }
            return false
        }
        for (child in container.childNodes()) {
            if (walk(child)) break
        }
        return sb.toString()
    }

    // Fallback label for a link with no readable "Label: <a>" prefix (a bare
    // "here"/button-style link, or the anchor's own text is just the URL) —
    // a friendly service name when the host is recognizable, else the host.
    // Generic CTA words ("here", "click here", "link") are never used as a
    // label even as a last resort — the host name reads better than "Here".
    private val genericLinkTextRegex = Regex("""^(click\s+)?here!?$|^this\s+link$|^link$""", RegexOption.IGNORE_CASE)

    private fun friendlyLinkLabel(host: String, ownText: String): String = when {
        "facebook" in host -> "Facebook"
        "twitter" in host || host == "x.com" || host.endsWith(".x.com") || host == "t.co" -> "X"
        "instagram" in host -> "Instagram"
        "youtube" in host || host == "youtu.be" -> "YouTube"
        "discord" in host -> "Discord"
        "steampowered" in host -> "Steam"
        "tiktok" in host -> "TikTok"
        ownText.isNotBlank() && !ownText.startsWith("http", ignoreCase = true) && ownText.length <= 40 && !genericLinkTextRegex.matches(ownText) -> ownText
        else -> host.removePrefix("www.")
    }

    // Flattens an element's inline content to plain text like Element.text()
    // does, but first rewrites any `<a href>` anchor (other than an
    // image-only one — those are pulled out as ArticleBlock.Image instead)
    // into a "[label](href)" marker, since a plain Element.text() call keeps
    // only the anchor's visible words and silently drops the href — fine
    // when that text already happens to be the URL, but it loses the link
    // entirely for anchors like "Here" or "Official Discord". linkify()
    // (CommonComponents.kt) turns the marker back into a tappable span.
    // Operates on a clone so the original body tree stays untouched for
    // parseFeaturedArticleLinks (called right after this, on the same
    // Element) to read anchors' real text/href from.
    private fun textWithLinks(el: Element): String {
        val clone = el.clone()
        for (a in clone.select("a[href]")) {
            if (a.selectFirst("img") != null) continue // image-only anchor, handled as ArticleBlock.Image
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            val label = a.text().trim()
            if (href.isBlank() || label.isBlank()) continue
            // A literal '[' / ']' in the label, or ')' in the href, would
            // corrupt the "[label](href)" shape markdownLinkRegex expects —
            // fall back to the bare href as its own label rather than risk
            // a garbled marker (neither character is expected in practice).
            val safeLabel = if ('[' in label || ']' in label) href else label
            val safeHref = href.substringBefore(")")
            a.text("[$safeLabel]($safeHref)")
        }
        return clone.text()
    }

    // Flattens an article body's top-level elements into ArticleBlocks.
    // MAL articles wrap standalone images in their own <p> (or plain
    // <a href="..."><img></a> banner links), so those are pulled out
    // as ArticleBlock.Image rather than rendered as empty paragraphs.
    private fun parseFeaturedArticleBody(body: Element): List<ArticleBlock> {
        fun imageSrc(img: Element) = img.attr("abs:src").ifBlank { img.attr("abs:data-src") }
        val blocks = mutableListOf<ArticleBlock>()
        for (node in body.children()) {
            when (node.tagName().lowercase()) {
                "p" -> {
                    // MAL often puts a banner <img> *and* trailing prose in
                    // the same <p> (e.g. "<img/><br/>Some text..."), so image
                    // and text are no longer mutually exclusive here — emit
                    // an Image block for every <img> found, in document
                    // order, then a Paragraph for any remaining text.
                    // Previously this only emitted an Image when the <p>'s
                    // text was *entirely* blank, so any <img> sharing a <p>
                    // with real text was silently dropped.
                    for (img in node.select("img")) {
                        imageSrc(img).takeIf { it.isNotBlank() }?.let { blocks += ArticleBlock.Image(fullResMalImage(it)) }
                    }
                    textWithLinks(node).trim().takeIf { it.isNotBlank() }?.let { blocks += ArticleBlock.Paragraph(it) }
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> textWithLinks(node).trim().takeIf { it.isNotBlank() }?.let { blocks += ArticleBlock.Heading(it) }
                "hr" -> if (blocks.lastOrNull() != ArticleBlock.Divider) blocks += ArticleBlock.Divider
                "ul", "ol" -> {
                    val items = node.children().filter { it.tagName().equals("li", ignoreCase = true) }.map { textWithLinks(it).trim() }.filter { it.isNotBlank() }
                    if (items.isNotEmpty()) blocks += ArticleBlock.ListBlock(items, ordered = node.tagName().equals("ol", ignoreCase = true))
                }
                "img" -> imageSrc(node).takeIf { it.isNotBlank() }?.let { blocks += ArticleBlock.Image(fullResMalImage(it)) }
                "a" -> {
                    val img = node.selectFirst("img")
                    if (img != null) {
                        imageSrc(img).takeIf { it.isNotBlank() }?.let { blocks += ArticleBlock.Image(fullResMalImage(it)) }
                    } else {
                        textWithLinks(node).trim().takeIf { it.isNotBlank() }?.let { blocks += ArticleBlock.Paragraph(it) }
                    }
                }
                "br", "iframe", "script", "style" -> {}
                else -> textWithLinks(node).trim().takeIf { it.isNotBlank() }?.let { blocks += ArticleBlock.Paragraph(it) }
            }
        }
        while (blocks.firstOrNull() == ArticleBlock.Divider) blocks.removeAt(0)
        while (blocks.lastOrNull() == ArticleBlock.Divider) blocks.removeAt(blocks.lastIndex)
        return blocks
    }
}