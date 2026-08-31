package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import java.io.IOException

private const val MAL = "https://myanimelist.net"

// Scrapes anime/manga detail-page widgets the official MAL API can't reliably supply
// (or that we've deliberately moved off Tenrai/Jikan for), straight off MAL's own HTML
// (same approach as ClubsApi/MalPeopleApi/StacksApi via MalScraping.kt):
//
//  - Related Entries: the official API's related_anime/related_manga fields are same-type
//    only in practice — an /anime/{id} request reliably returns related_anime but usually
//    comes back empty for related_manga (manga/light novel adaptations), and vice versa on
//    /manga/{id}, even though the website's own Related Entries box always lists every
//    direction regardless of which page you're on.
//  - Recommendations: the official API's `recommendations` field is user-submitted only,
//    but comes back thin for newer/lower-traffic titles. The main detail page's own
//    Recommendations slider fills that gap with MAL's algorithmic "AutoRec" picks — real
//    user picks live on the title's separate "/userrecs" subpage instead (see
//    fetchUserRecommendations). The app shows both: real recs from "/userrecs" plus
//    whatever the slider adds on top (AutoRec included), rather than picking one source
//    over the other — see LibraryViewModel.ensureDetailFetched for how they're merged.
//  - Characters & Voice Actors: previously fetched via TenraiApi.fetchCharacters (a Jikan
//    proxy). Moved to a direct scrape of MAL's own "/characters" subpage so this row no
//    longer depends on Tenrai/Jikan being up or in sync with MAL at all.
//
// Verified against a real anime detail page response (Related Entries + the AutoRec-tagged
// Recommendations slider, and the Mieruko-chan characters subpage). The manga page markup
// wasn't available to verify against, so the selectors below are written to be type-agnostic
// (matched by /anime/ vs /manga/ in the link's own href) rather than assuming manga-page-
// specific class names.
class MalDetailScrapeApi {
    private val client = NetworkClient.shared

    data class PageExtras(val related: List<RelatedEntry>, val recommended: List<RecommendedEntry>)

    suspend fun fetch(id: Int, type: MediaType): PageExtras = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val doc = client.fetchMalDocument("$MAL/$kind/$id")
        PageExtras(parseRelated(doc), parseRecommended(doc))
    }

    // Fetch characters row for the detail page (also feeds the Japanese Voice Actors row —
    // see LibraryViewModel.loadCharacters). Lets a genuine fetch failure (DNS, timeout,
    // both attempts below coming back non-200) propagate to the caller instead of being
    // swallowed here, so it can be told apart from a title that genuinely has no characters
    // listed.
    //
    // Same slug requirement as fetchScoreStats below: MAL's "/{kind}/{id}/{slug}/{subpage}"
    // routing treats the slug segment as positional but unvalidated against the id, so
    // requesting "/{kind}/{id}/characters" with no slug gets "characters" parsed as the slug
    // itself — which silently 200s with the *main* detail page (no characters table at all)
    // rather than the characters subpage. Slugging the title in fixes it; the no-slug form
    // is kept as a fallback in case a title's slug ever collides with something MAL treats
    // specially.
    suspend fun fetchCharacters(id: Int, type: MediaType, title: String): List<CharacterEntry> = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val slugged = runCatching { parseCharacters(client.fetchMalDocument("$MAL/$kind/$id/${malSlug(title)}/characters")) }
        if ((slugged.getOrNull()?.size ?: 0) > 0) return@withContext slugged.getOrThrow()
        val fallback = runCatching { parseCharacters(client.fetchMalDocument("$MAL/$kind/$id/characters")) }
        fallback.getOrNull()?.let { return@withContext it }
        // Both requests came back with no usable list — if either was a genuine fetch
        // failure rather than a real "zero characters" page, surface that instead of
        // reporting an empty list.
        throw slugged.exceptionOrNull() ?: fallback.exceptionOrNull()
        ?: IOException("MAL characters request failed: $kind/$id")
    }

    // Genuine user-submitted recommendation pairs, scraped from the title's own
    // "/userrecs" subpage — merged with the main detail page's AutoRec-padded slider
    // (see parseRecommended below) rather than replacing it, so the Recommended row shows
    // real picks alongside MAL's own algorithmic ones instead of just whichever source
    // happened to have more. Verified against the real Monster Musume no Oishasan userrecs
    // page, which listed ten-plus real user picks the slider didn't surface at all.
    //
    // Same slug requirement as fetchCharacters/fetchScoreStats above.
    suspend fun fetchUserRecommendations(id: Int, type: MediaType, title: String): List<RecommendedEntry> = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val slugged = runCatching { parseUserRecommendations(client.fetchMalDocument("$MAL/$kind/$id/${malSlug(title)}/userrecs")) }
        if ((slugged.getOrNull()?.size ?: 0) > 0) return@withContext slugged.getOrThrow()
        val fallback = runCatching { parseUserRecommendations(client.fetchMalDocument("$MAL/$kind/$id/userrecs")) }
        fallback.getOrNull() ?: slugged.getOrDefault(emptyList())
    }

    // Each recommendation pairing on the "/userrecs" subpage is its own table: a small
    // cover-thumbnail cell, and an info cell holding the paired title, the first
    // recommender's writeup, and — if others agree on the same pairing — a "Read
    // recommendations by N more users" toggle. That toggle's count plus the one always-
    // visible writeup is the real vote total for the pairing (there's no need to parse
    // the hidden writeups themselves, just the count in the toggle).
    private fun parseUserRecommendations(doc: Document): List<RecommendedEntry> =
        doc.select("table:has(div[id^=raArea])").mapNotNull { table ->
            val cells = table.selectFirst("tr")?.children() ?: return@mapNotNull null
            val picCell = cells.getOrNull(0) ?: return@mapNotNull null
            val infoCell = cells.getOrNull(1) ?: return@mapNotNull null

            // The paired title's own link is the one wrapping a <strong>; the "Read
            // recommendations by N more users" toggle also wraps a <strong> (just the
            // count) but isn't an /anime/ or /manga/ link, so restricting to that href
            // pattern picks out the title link specifically.
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
            // The "/userrecs" subpage can list the same anime/manga pairing more than once
            // (each recommender's write-up gets its own <table>, so a title with several
            // separate recommendations pointing at the same paired entry produces one row
            // per write-up). Left undeduped, that reaches DetailScreen's Recommended LazyRow
            // as two entries sharing the same "${malId}-${malType}" key and crashes it (see
            // the key comment above the itemsIndexed call in DetailScreen.kt). parseRecommended
            // below already dedupes its own (slider) source the same way for the same reason —
            // do it here too, keeping the first (highest vote-count) occurrence.
            .distinctBy { it.malId to it.malType }

    // Community score breakdown (1-10) — lives on the title's separate /stats page, not
    // the main detail page above, so this is its own request, made on demand only when
    // the user actually opens the score stats screen (see LibraryViewModel.loadScoreStats).
    //
    // MAL's own routing is "/{kind}/{id}/{slug}/{subpage}", with the slug segment
    // positional but not actually validated against the id — any placeholder text works.
    // Requesting "/{kind}/{id}/stats" (no slug) doesn't hit the stats subpage at all: MAL
    // parses "stats" itself as filling the slug slot, so it silently 200s with the regular
    // detail page instead (which has no score-stats table), and this came back looking
    // like "no score data" for every title. Slugging the title in ourselves fixes it; the
    // no-slug form is kept as a fallback in case a title's slugified title ever collides
    // with something MAL treats specially.
    suspend fun fetchScoreStats(id: Int, type: MediaType, title: String): ScoreStats = withContext(Dispatchers.IO) {
        val kind = if (type == MediaType.Anime) "anime" else "manga"
        val slugged = runCatching { parseScoreStats(client.fetchMalDocument("$MAL/$kind/$id/${malSlug(title)}/stats")) }.getOrNull()
        if (slugged != null && slugged.total > 0) return@withContext slugged
        runCatching { parseScoreStats(client.fetchMalDocument("$MAL/$kind/$id/stats")) }.getOrDefault(slugged ?: ScoreStats())
    }

    // MAL's own slug convention: title with every run of non-alphanumeric characters
    // collapsed to a single underscore (e.g. "Maid-san wa Taberu dake" -> the real MAL
    // slug "Maid-san_wa_Taberu_dake"). Doesn't need to be byte-for-byte identical to MAL's
    // actual slug for this title — MAL doesn't check it against the id — just non-empty.
    private fun malSlug(title: String): String {
        val slug = title.trim().replace(Regex("[^A-Za-z0-9-]+"), "_").trim('_')
        return slug.ifBlank { "_" }
    }

    // "table.score-stats" rows go from score 10 down to 1, each with a "(N votes)" small
    // tag next to the percentage — same table shape as the Sayonara Lara stats page this
    // was verified against.
    private fun parseScoreStats(doc: Document): ScoreStats {
        val counts = doc.select("table.score-stats tr").mapNotNull { row ->
            val score = row.selectFirst("td.score-label")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val votes = Regex("\\d[\\d,]*").find(row.select("small").text())?.value?.replace(",", "")?.toIntOrNull() ?: 0
            score to votes
        }.toMap()
        return ScoreStats(counts)
    }

    // Each character on the "/characters" subpage is its own 3-column table
    // (table.js-anime-character-table): a picture cell, an info cell (name + role +
    // favorites), and a voice-actor cell (one table.js-anime-character-va row per
    // dub language). Only the Japanese VA is kept — same as the old Jikan-backed
    // fetchCharacters — since the Voice Actors row on the detail page is Japanese-only.
    private fun parseCharacters(doc: Document): List<CharacterEntry> =
        doc.select("table.js-anime-character-table").mapNotNull { table ->
            val cells = table.selectFirst("tr")?.children() ?: return@mapNotNull null
            val imageCell = cells.getOrNull(0) ?: return@mapNotNull null
            val infoCell = cells.getOrNull(1) ?: return@mapNotNull null
            val vaCell = cells.getOrNull(2)

            // The character link lives twice (image + name); the one wrapping the h3 is
            // the more reliable of the two since every row has a name but not every row
            // has managed to load a picture.
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

            // Of the info cell's several "spaceit_pad" divs, the role is the one with
            // no h3 (that's the name) and no inline style (the favorites-count div carries
            // "color: #787878" — the role div doesn't).
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

    // malId + malType read straight off the link's own href rather than off which page
    // we're on, so a manga's related anime (and an anime's related manga/light novel)
    // both come through correctly.
    private fun malRefFromUrl(url: String): Pair<Int, String>? {
        val match = Regex("/(anime|manga)/(\\d+)").find(url) ?: return null
        val id = match.groupValues[2].toIntOrNull() ?: return null
        return id to match.groupValues[1]
    }

    private fun parseRelated(doc: Document): List<RelatedEntry> =
        doc.select("div.related-entries div.entry").mapNotNull { entry ->
            val link = entry.selectFirst(".content .title a") ?: entry.selectFirst(".image a") ?: return@mapNotNull null
            val (malId, malType) = malRefFromUrl(link.attr("abs:href")) ?: return@mapNotNull null
            // e.g. "Adaptation\n(Manga)" -> "Adaptation (Manga)"
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

    // "?suggestion" only marks the AutoRec fallback links — a real, user-submitted entry
    // in this same slider links straight to "/recommendations/{type}/{a}-{b}" with no
    // query param at all. Matching on "?suggestion" alone (as this used to) silently
    // dropped every real entry from the slider and kept only the AutoRec ones, which is
    // what let a stale/incorrect cover from fetchUserRecommendations' separate "/userrecs"
    // scrape win the merge uncontested (see the cover-preference comment in
    // LibraryViewModel.ensureDetailFetched) — matching both link shapes here is what lets
    // that merge actually have the slider's own (verified-correct) cover to prefer.
    //
    // This is the small slider on the *main* detail page — merged alongside
    // fetchUserRecommendations' dedicated "/userrecs" scrape rather than replaced by it
    // (see LibraryViewModel.ensureDetailFetched), so AutoRec picks still pad out the row
    // even for titles that already have real user recs.
    private fun parseRecommended(doc: Document): List<RecommendedEntry> =
        doc.select("a[href*='?suggestion'], a[href*='/recommendations/anime/'], a[href*='/recommendations/manga/']").mapNotNull { a ->
            val (malId, malType) = malRefFromUrl(a.attr("abs:href")) ?: return@mapNotNull null
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
}