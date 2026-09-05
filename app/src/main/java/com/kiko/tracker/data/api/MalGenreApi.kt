package com.kiko.tracker.data.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Element
import com.kiko.tracker.data.model.DiscoverSort
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.WatchStatus

// Genre/theme/demographic-filtered Discover search, scraped
// myanimelist.net/anime.php and /manga.php instead
// endpoints — same reasoning
// *is* the filter, so
// capped to a chart's
//
// Confirmed against MAL's own
// - genre[]=<id> repeated per
// genre) — the same
// - genre_ex[]=<id> is a
// confirmed from the "click
// (name="genre_ex[]"). Used here to
// same thing Tenrai's &sfw
// - No o=/w= param
// in its normal unsorted
// column to sort by)
// sortedForDiscover's client-side sort works
// server-side alphabetical sort here
// o=/w= IS sent —
// enough for those.
// - No page-size param
// size), so this always
// - c[]=d (Start Date)
// DiscoverSort.Newest has something real
// startDate/startDateFull came back blank
// it didn't match the
// Confirmed by fetching the
// the Start Date cell
// unknown month and/or day
// at all — see
// - The results table
// (unlike a studio/person page's
// already filtered server-side by
// satisfies those facets; MediaItem.unknownFacets
// client-side against data this
class MalGenreApi {
    private val client = NetworkClient.shared

    private fun fetchDoc(url: String) = client.fetchMalDocument(url)

    val pageSize = 50

    // Server-side sort codes for
    // the header row of
    // no explicit sort): the
    // links, giving o=6 Type,
    // value already confirmed elsewhere
    // than a fresh guess.
    // <a>), so MAL doesn't
    // client-side sort (see the
    //
    // Passing these turns each
    // across every matching title
    // page that was coming
    // was fetching page N
    // client-side before appending it
    // LibraryViewModel) — correct for
    // remotely the same as
    // website's own Sort-by-Members shows
    // It went unnoticed for
    // because those return under
    // there, so a per-page
    private fun sortParam(sort: DiscoverSort): String = when (sort) {
        DiscoverSort.Members -> "&o=7&w=1"
        DiscoverSort.Score -> "&o=3&w=1"
        DiscoverSort.Newest -> "&o=9&w=1"
        DiscoverSort.Relevance, DiscoverSort.Title -> ""
    }

    // kind: "anime" | "manga".
    // together by MAL itself.
    // malAnimeTypeCode/malMangaTypeCode/malStatusCode in Models.kt) —
    // page is 1-based; MAL's
    // sort: which DiscoverSort the
    // Title are requested unsorted
    // respectively) and stay client-sorted;
    // sortParam so pagination continues
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
            // Hentai only — Ecchi/Erotica
            // way Tenrai's &sfw flag
            val exParam = if (includeAdult) "" else "&genre_ex[]=12"
            val url = "$base&score=0&sm=0&sd=0&sy=0&em=0&ed=0&ey=0&c[0]=a&c[1]=b&c[2]=c&c[3]=d&c[4]=f$typeParam$statusParam$genreParams$exParam${sortParam(sort)}&show=$show"
            val doc = fetchDoc(url)
            val table = doc.selectFirst("div.js-categories-seasonal table") ?: return@runCatching TenraiPage(emptyList(), false)
            val rows = table.select("tr").filter { it.selectFirst("div.picSurround") != null }
            val items = rows.mapNotNull { parseRow(it, kind) }
            // Same "a full page
            // TenraiApi.searchFiltered — MAL's own
            // scrape, so a short/empty
            TenraiPage(items, items.size >= pageSize)
        }.getOrElse { TenraiPage(emptyList(), false) }
    }

    private fun parseRow(row: Element, kind: String): MediaItem? {
        // The picSurround-wrapping <a> also
        // this selector lands on
        // "hoverinfo_trigger fw-b fl-l" and
        val link = row.select("a.hoverinfo_trigger.fw-b").firstOrNull { it.text().isNotBlank() } ?: return null
        val title = link.text()
        val idRegex = if (kind == "anime") Regex("/anime/(\\d+)/") else Regex("/manga/(\\d+)/")
        val id = idRegex.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        // The title link's own
        // default (the site's default/romaji
        // MediaItem.titleEnglish was left blank
        // A previous attempt tried
        // the actual page source
        // that box doesn't even
        // and more importantly it
        // <div class="hoverinfo" id="sinfo41380" rel="a41380"></div>
        // MAL populates it client-side,
        // title — there is
        // scrape to read, for
        // English titles for these
        // MAL id this scrape
        // per-id request against MAL's
        // rather than another scrape.
        // runs that lookup in
        // it can't add latency
        // setting is actually English
        val titleEnglish = ""
        val cover = row.selectFirst("img")?.let { img ->
            val raw = img.attr("data-src").ifBlank { img.attr("src") }
            fullResMalImage(img.absUrl(if (img.hasAttr("data-src")) "data-src" else "src").ifBlank { raw })
        }.orEmpty()
        val synopsis = row.selectFirst("div.pt4")?.let { pt4 ->
            pt4.selectFirst("a")?.remove() // drop the trailing "read
            pt4.text().trim()
        }.orEmpty()
        // Score/Members/Start Date cells share
        // other cells on other
        // data column but not
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
            titleEnglish = titleEnglish,
            inUserList = false,
            // The search results table
            // per row — see
            unknownFacets = setOf("genres", "themes", "demographics", "source", "rating", "airingStatus"),
        )
    }

    // MAL renders this column
    // of an unknown month
    // is entirely unknown —
    // mixes recent titles (25/26/27)
    // "65", "70", "83", "92"
    // more than ~10 past
    // entries don't get sorted
    // Returns (4-digit year for
    // MediaItem.startDateFull — unknown month/day
    // sorts correctly by year,
    // compare) — or ("",
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

// Name -> id lookup
// "Content Filter" checkbox panel
// same page MalGenreApi.search() already
// the real ids MAL's
// needed 2-4 separate requests
// backoff on 429s) before
// made "search by genres"
// request per kind, with
// rest of the session
// MAL directly instead of
//
// Anime and manga do
// anime's is 81 —
// Tenrai-backed version did.
data class GenreFacets(
    val genres: Map<String, Int>,
    val explicitGenres: Map<String, Int>,
    val themes: Map<String, Int>,
    val demographics: Map<String, Int>,
)

private object GenreFacetCache {
    val byKind = mutableMapOf<String, GenreFacets>()
    val inFlight = mutableMapOf<String, Deferred<GenreFacets>>()
    val mutex = Mutex()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

// Every checkbox label on
// "Action (5,017)" — stripped
// CommonDemographics (Models.kt) and the
private val genreLabelCountSuffix = Regex("\\s*\\([\\d,]+\\)\\s*$")

class MalGenreLookup {
    private val client = NetworkClient.shared

    // Fired when the filter
    // first genre-filtered search of
    // Apply is tapped. Safe
    suspend fun prewarmGenreNames(kind: String) { runCatching { facets(kind) } }

    // Same "fail the whole
    // old TenraiApi.resolveGenreIds had: LibraryViewModel
    // when this comes back
    suspend fun resolveGenreIds(kind: String, genres: Set<String>, themes: Set<String> = emptySet(), demographics: Set<String> = emptySet()): List<Int> {
        if (genres.isEmpty() && themes.isEmpty() && demographics.isEmpty()) return emptyList()
        val f = runCatching { facets(kind) }.getOrNull() ?: return emptyList()
        // The "Genres" chip picker
        // and Explicit Genres groups
        // chips, not a separate
        val genreMap = f.genres + f.explicitGenres
        val resolved = genres.map { genreMap[it.lowercase()] } + themes.map { f.themes[it.lowercase()] } + demographics.map { f.demographics[it.lowercase()] }
        return if (resolved.any { it == null }) emptyList() else resolved.filterNotNull()
    }

    private suspend fun facets(kind: String): GenreFacets {
        GenreFacetCache.byKind[kind]?.let { return it }
        val deferred = GenreFacetCache.mutex.withLock {
            GenreFacetCache.byKind[kind]?.let { return it }
            GenreFacetCache.inFlight.getOrPut(kind) {
                GenreFacetCache.scope.async { fetchFacets(kind).also { GenreFacetCache.byKind[kind] = it } }
            }
        }
        return try {
            deferred.await()
        } finally {
            GenreFacetCache.mutex.withLock { if (GenreFacetCache.inFlight[kind] === deferred) GenreFacetCache.inFlight.remove(kind) }
        }
    }

    // One request gets every
    // "Genres"/"Explicit Genres"/"Themes"/"Demographics" heading (div.category-type)
    // a shared div.category-wrapper, each
    // name="genre[]" input, with the
    // following <p>.
    private fun fetchFacets(kind: String): GenreFacets {
        val url = if (kind == "anime") "https://myanimelist.net/anime.php" else "https://myanimelist.net/manga.php"
        val doc = client.fetchMalDocument(url)
        val byCategory = mutableMapOf<String, MutableMap<String, Int>>()
        doc.select("div.category-wrapper").forEach { wrapper ->
            val category = wrapper.selectFirst("div.category-type")?.text()?.trim() ?: return@forEach
            val map = byCategory.getOrPut(category) { mutableMapOf() }
            wrapper.select("input[name=genre[]]").forEach { input ->
                val id = input.attr("value").toIntOrNull() ?: return@forEach
                val label = input.nextElementSibling()?.takeIf { it.tagName() == "p" }?.text()?.trim() ?: return@forEach
                val name = label.replace(genreLabelCountSuffix, "").trim()
                if (name.isNotBlank()) map[name.lowercase()] = id
            }
        }
        return GenreFacets(
            genres = byCategory["Genres"].orEmpty(),
            explicitGenres = byCategory["Explicit Genres"].orEmpty(),
            themes = byCategory["Themes"].orEmpty(),
            demographics = byCategory["Demographics"].orEmpty(),
        )
    }
}