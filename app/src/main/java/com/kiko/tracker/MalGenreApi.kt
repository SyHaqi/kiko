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
// - No o=/w= param is sent for Relevance/Title, which leaves MAL's own advanced-search page
//   in its normal unsorted order (the same order the site itself shows before you pick a
//   column to sort by) — that's what backs the app's default "Relevance" sort, and what
//   sortedForDiscover's client-side sort works against for Title (MAL doesn't expose a
//   server-side alphabetical sort here — see sortParam below). For Members/Score/Newest,
//   o=/w= IS sent — see sortParam for why a client-side re-sort of one fetched page isn't
//   enough for those.
// - No page-size param exists; MAL's own pager increments show= by 50 (its fixed page
//   size), so this always returns up to 50 items — see pageSize below.
// - c[]=d (Start Date) is now requested alongside Type/Eps/Score/Members so
//   DiscoverSort.Newest has something real to sort on — without it every row's
//   startDate/startDateFull came back blank and "Newest" silently no-opped, which is why
//   it didn't match the genre page's own "Sorted by Newest" order on myanimelist.net.
//   Confirmed by fetching the site's own "Just Added" link (?c[0]=a&c[1]=d&cv=2&o=9&w=1):
//   the Start Date cell renders as MM-DD-YY ("04-06-25"), with "??" standing in for an
//   unknown month and/or day ("??-??-26", "01-??-27") and a bare "-" when nothing is known
//   at all — see parseStartDate below for how that gets turned into a sortable date.
// - The results table never exposes genre/theme/demographic/source/rating ids per row
//   (unlike a studio/person page's data-genre attribute) — but since the search itself is
//   already filtered server-side by whatever was passed in, an item showing up here already
//   satisfies those facets; MediaItem.unknownFacets just tells matches() not to re-check
//   client-side against data this scrape never had.
class MalGenreApi {
    private val client = NetworkClient.shared

    private fun fetchDoc(url: String) = client.fetchMalDocument(url)

    val pageSize = 50

    // Server-side sort codes for MAL's own o=/w= column-sort links — confirmed straight off
    // the header row of a live genre-filtered anime.php results page (view-source, genre[0]=36,
    // no explicit sort): the Type/Eps/Score/Members column headers carry their own "?...&o=N&w=1"
    // links, giving o=6 Type, o=4 Eps, o=3 Score, o=7 Members. o=9/w=1 for Newest reuses the
    // value already confirmed elsewhere in this file (the site's own "Just Added" link) rather
    // than a fresh guess. There's no such link on the Title column (it's plain text, not an
    // <a>), so MAL doesn't expose a server-side alphabetical sort here — Title has to stay a
    // client-side sort (see the DiscoverSort.Title case below and sortedForDiscover).
    //
    // Passing these turns each fetched 50-row page into a true globally-sorted slice (MAL sorts
    // across every matching title before paginating) instead of the arbitrary default-order
    // page that was coming back before. That distinction is the actual bug this fixes: the app
    // was fetching page N in MAL's *unsorted* order and then re-sorting only that one page
    // client-side before appending it (see loadMoreGenreFiltered/runDiscoverSearch in
    // LibraryViewModel) — correct for whatever 50 items happened to be on that page, but not
    // remotely the same as "top 50 by members across the whole genre", which is what the
    // website's own Sort-by-Members shows and what DiscoverSort.Members is supposed to mean.
    // It went unnoticed for narrow filter combinations (e.g. a type + a niche theme) simply
    // because those return under 50 results total — a single page IS the whole result set
    // there, so a per-page sort and a global sort are the same thing by coincidence.
    private fun sortParam(sort: DiscoverSort): String = when (sort) {
        DiscoverSort.Members -> "&o=7&w=1"
        DiscoverSort.Score -> "&o=3&w=1"
        DiscoverSort.Newest -> "&o=9&w=1"
        DiscoverSort.Relevance, DiscoverSort.Title -> ""
    }

    // kind: "anime" | "manga". genreIds: one or more genre/theme/demographic ids, ANDed
    // together by MAL itself. type/status are MAL's own numeric codes (see
    // malAnimeTypeCode/malMangaTypeCode/malStatusCode in Models.kt) — null means "any".
    // page is 1-based; MAL's own show= offset is derived from it and the fixed 50-row size.
    // sort: which DiscoverSort the results should already come back ordered by — Relevance and
    // Title are requested unsorted (MAL's own default order / no server-side support,
    // respectively) and stay client-sorted; Members/Score/Newest are requested pre-sorted via
    // sortParam so pagination continues in true global order instead of per-page order.
    suspend fun search(kind: String, genreIds: List<Int>, type: String?, status: String?, page: Int, includeAdult: Boolean, sort: DiscoverSort = DiscoverSort.Relevance): TenraiPage = withContext(Dispatchers.IO) {
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
            val url = "$base&score=0&sm=0&sd=0&sy=0&em=0&ed=0&ey=0&c[0]=a&c[1]=b&c[2]=c&c[3]=d&c[4]=f$typeParam$statusParam$genreParams$exParam${sortParam(sort)}&show=$show"
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
        // Score/Members/Start Date cells share width=50/75/70 with the leading (image) and
        // other cells on other pages, so the "ac" (align-center) class — present on every
        // data column but not the image cell — is what actually disambiguates them here.
        val typeText = row.selectFirst("td.ac[width=45]")?.text()?.trim().orEmpty()
        val countText = row.selectFirst("td.ac[width=40]")?.text()?.trim().orEmpty()
        val scoreText = row.selectFirst("td.ac[width=50]")?.text()?.trim().orEmpty()
        val startDateText = row.selectFirst("td.ac[width=70]")?.text()?.trim().orEmpty()
        val membersText = row.selectFirst("td.ac[width=75]")?.text()?.trim().orEmpty()
        val score = scoreText.toDoubleOrNull() ?: 0.0
        val members = membersText.replace(",", "").toIntOrNull() ?: 0
        val count = countText.toIntOrNull() ?: 0
        val (startYear, startDateFull) = parseStartDate(startDateText)
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
            startDate = startYear,
            startDateFull = startDateFull,
            inUserList = false,
            // The search results table never carries genre/theme/demographic/source/rating
            // per row — see the class doc above for why that's fine here.
            unknownFacets = setOf("genres", "themes", "demographics", "source", "rating", "airingStatus"),
        )
    }

    // MAL renders this column as MM-DD-YY with a two-digit year ("04-06-25"), "??" in place
    // of an unknown month and/or day ("??-??-26", "01-??-27"), or a bare "-" when the date
    // is entirely unknown — confirmed against the site's own "Just Added" listing, which
    // mixes recent titles (25/26/27) with much older ones rendered the same way (e.g. "60",
    // "65", "70", "83", "92" for 1960s-1990s titles, "01" for a 2001 one). Two-digit years
    // more than ~10 past the current year are treated as 19xx rather than 20xx so those old
    // entries don't get sorted as if they were from the 2060s-2090s.
    // Returns (4-digit year for MediaItem.startDate, sortable "YYYY-MM-DD" for
    // MediaItem.startDateFull — unknown month/day fall back to "01" so the string still
    // sorts correctly by year, then month, within DiscoverSort.Newest's plain string
    // compare) — or ("", "") when nothing could be parsed.
    private fun parseStartDate(raw: String): Pair<String, String> {
        val parts = raw.trim().split("-")
        if (parts.size != 3) return "" to ""
        val (mm, dd, yy) = parts
        val yyNum = yy.toIntOrNull() ?: return "" to ""
        val currentYY = java.time.Year.now().value % 100
        val fullYear = (if (yyNum > currentYY + 10) 1900 else 2000) + yyNum
        val month = (mm.toIntOrNull()?.takeIf { it in 1..12 } ?: 1).toString().padStart(2, '0')
        val day = (dd.toIntOrNull()?.takeIf { it in 1..31 } ?: 1).toString().padStart(2, '0')
        return fullYear.toString() to "$fullYear-$month-$day"
    }
}