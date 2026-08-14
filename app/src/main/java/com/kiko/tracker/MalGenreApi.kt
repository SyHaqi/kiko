package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Element

// Genre/theme/demographic-filtered Discover search, scraped straight off
// myanimelist.net/anime.php and /manga.php instead of going through Tenrai's ranking-pool
// endpoints — same reasoning as MalCompanyApi/MalPeopleApi: MAL's own advanced search page
// *is* the filter, so there's no need for a third-party mirror of it, and this one isn't
// capped to a chart's top ~500 the way Tenrai's candidate-pool fallback was.
//
// Confirmed against MAL's own search page source (not guessed):
// - genre[]=<id> repeated per tag ANDs them together (a title must have every selected
//   genre) — the same "click once to include" checkboxes the Advanced Search panel shows.
// - genre_ex[]=<id> is a *separate* param name from genre[], not a negated id or a suffix —
//   confirmed from the "click twice to exclude" state's own checkbox markup
//   (name="genre_ex[]"). Used here to drop Hentai (id 12) when includeAdult is false, the
//   same thing Tenrai's &sfw flag did.
// - No o=/w= param is sent by default, which leaves MAL's own advanced-search page in its
//   normal unsorted order (the same order the site itself shows before you pick a column
//   to sort by) — that's what backs the app's default "Relevance" sort. o=7&w=1 was
//   confirmed by fetching that exact URL to sort by Members descending instead (Shingeki no
//   Kyojin's 4.4M members first, down to 1.6M by row 50); DiscoverSort.Members still gets
//   that ordering, just via the client-side re-sort in sortedForDiscover rather than a URL
//   param, so it doesn't have to fight the default request for which order "wins".
// - No page-size param exists; MAL's own pager increments show= by 50 (its fixed page
//   size), so this always returns up to 50 items — see pageSize below.
// - The results table never exposes genre/theme/demographic/source/rating ids per row
//   (unlike a studio/person page's data-genre attribute) — but since the search itself is
//   already filtered server-side by whatever was passed in, an item showing up here already
//   satisfies those facets; MediaItem.unknownFacets just tells matches() not to re-check
//   client-side against data this scrape never had.
class MalGenreApi {
    private val client = NetworkClient.shared

    private fun fetchDoc(url: String) = client.fetchMalDocument(url)

    val pageSize = 50

    // kind: "anime" | "manga". genreIds: one or more genre/theme/demographic ids, ANDed
    // together by MAL itself. type/status are MAL's own numeric codes (see
    // malAnimeTypeCode/malMangaTypeCode/malStatusCode in Models.kt) — null means "any".
    // page is 1-based; MAL's own show= offset is derived from it and the fixed 50-row size.
    suspend fun search(kind: String, genreIds: List<Int>, type: String?, status: String?, page: Int, includeAdult: Boolean): TenraiPage = withContext(Dispatchers.IO) {
        runCatching {
            if (genreIds.isEmpty()) return@runCatching TenraiPage(emptyList(), false)
            val show = (page - 1) * pageSize
            val base = if (kind == "anime")
                "https://myanimelist.net/anime.php?cat=anime&q=&p=0&r=0"
            else
                "https://myanimelist.net/manga.php?cat=manga&q=&mid=0"
            val typeParam = type?.let { "&type=$it" } ?: ""
            val statusParam = status?.let { "&status=$it" } ?: ""
            val genreParams = genreIds.joinToString("") { "&genre[]=$it" }
            // Hentai only — Ecchi/Erotica stay visible under the app's nsfw toggle the same
            // way Tenrai's &sfw flag only ever dropped genre 12, not the softer tags.
            val exParam = if (includeAdult) "" else "&genre_ex[]=12"
            val url = "$base&score=0&sm=0&sd=0&sy=0&em=0&ed=0&ey=0&c[0]=a&c[1]=b&c[2]=c&c[3]=f$typeParam$statusParam$genreParams$exParam&show=$show"
            val doc = fetchDoc(url)
            val table = doc.selectFirst("div.js-categories-seasonal table") ?: return@runCatching TenraiPage(emptyList(), false)
            val rows = table.select("tr").filter { it.selectFirst("div.picSurround") != null }
            val items = rows.mapNotNull { parseRow(it, kind) }
            // Same "a full page probably means there's more" reasoning as
            // TenraiApi.searchFiltered — MAL's own pager doesn't expose a has-next flag to
            // scrape, so a short/empty page is the only reliable "we've reached the end" signal.
            TenraiPage(items, items.size >= pageSize)
        }.getOrElse { TenraiPage(emptyList(), false) }
    }

    private fun parseRow(row: Element, kind: String): MediaItem? {
        // The picSurround-wrapping <a> also carries class hoverinfo_trigger but not fw-b, so
        // this selector lands on the title link only, for both the anime row's
        // "hoverinfo_trigger fw-b fl-l" and manga row's plain "hoverinfo_trigger fw-b".
        val link = row.select("a.hoverinfo_trigger.fw-b").firstOrNull { it.text().isNotBlank() } ?: return null
        val title = link.text()
        val idRegex = if (kind == "anime") Regex("/anime/(\\d+)/") else Regex("/manga/(\\d+)/")
        val id = idRegex.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val cover = row.selectFirst("img")?.let { img ->
            val raw = img.attr("data-src").ifBlank { img.attr("src") }
            fullResMalImage(img.absUrl(if (img.hasAttr("data-src")) "data-src" else "src").ifBlank { raw })
        }.orEmpty()
        val synopsis = row.selectFirst("div.pt4")?.let { pt4 ->
            pt4.selectFirst("a")?.remove() // drop the trailing "read more." link's text
            pt4.text().trim()
        }.orEmpty()
        // Score/Members cells share width=50/75 with the leading (image) and other cells on
        // other pages, so the "ac" (align-center) class — present on every data column but
        // not the image cell — is what actually disambiguates them here.
        val typeText = row.selectFirst("td.ac[width=45]")?.text()?.trim().orEmpty()
        val countText = row.selectFirst("td.ac[width=40]")?.text()?.trim().orEmpty()
        val scoreText = row.selectFirst("td.ac[width=50]")?.text()?.trim().orEmpty()
        val membersText = row.selectFirst("td.ac[width=75]")?.text()?.trim().orEmpty()
        val score = scoreText.toDoubleOrNull() ?: 0.0
        val members = membersText.replace(",", "").toIntOrNull() ?: 0
        val count = countText.toIntOrNull() ?: 0
        return MediaItem(
            id = id.toString(),
            title = title,
            type = if (kind == "anime") MediaType.Anime else MediaType.Manga,
            status = WatchStatus.Plan,
            cover = cover,
            synopsis = synopsis,
            score = score,
            listUsers = members,
            total = if (kind == "anime") count else 0,
            volumes = if (kind == "manga") count else 0,
            format = typeText,
            inUserList = false,
            // The search results table never carries genre/theme/demographic/source/rating
            // per row — see the class doc above for why that's fine here.
            unknownFacets = setOf("genres", "themes", "demographics", "source", "rating", "airingStatus"),
        )
    }
}
