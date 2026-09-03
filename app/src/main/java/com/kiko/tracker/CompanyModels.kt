package com.kiko.tracker

// Company (studio/producer/licensor) search/detail live outside MediaItem for the same
// reason PersonModels.kt/CharacterModels.kt's types do — a company has none of MediaItem's
// tracking fields. These are the two shapes Discover's Companies tab needs: a lightweight
// search-result row, and the fuller company detail page (bio + one recent news item + the
// studio's full anime catalog).

// One row in MAL's own company search results (https://myanimelist.net/company?q=...)
data class CompanySummary(
    val malId: Int,
    val name: String,
    // Parenthetical native/alternate name shown under the name on the search results row,
    // e.g. "(京都アニメーション)" — kept as-is (parens included), same convention
    // PersonSummary.altName/CharacterSummary.altName use.
    val japanese: String = "",
    val image: String = "",
)

// The single most-recent item off a company's own "Recent News" list. topicId links to the
// same forum topic this app's ForumTopicScreen already knows how to open, so tapping this
// card reuses that screen rather than needing a bespoke news-article reader.
data class CompanyNews(
    val topicId: Int,
    val title: String,
    val image: String = "",
    val snippet: String = "",
    val date: String = "",
)

// Full company detail page
data class CompanyDetail(
    val malId: Int,
    val name: String,
    val image: String = "",
    val favorites: Int = 0,
    // Structured "Label: value" lines (Synonyms, Japanese, Established, ...) — whatever MAL
    // actually lists for this company, in the order they appear on the page. Same
    // not-every-field-always-present shape as PersonDetail.bioFields.
    val bioFields: List<Pair<String, String>> = emptyList(),
    // Free-text company history/description paragraph.
    val about: String = "",
    // "Available At" links (official site + socials) — label is the link's own visible
    // text (domain or @handle) as MAL prints it, value is the absolute URL to open.
    val links: List<Pair<String, String>> = emptyList(),
    val news: CompanyNews? = null,
    // Full anime catalog credited to this company — already real MediaItem entries via
    // MalCompanyApi's existing studio-page tile parsing (format/score/members/genre all
    // baked in), so the grid on CompanyDetailScreen is just that same shape everywhere
    // else in the app already knows how to render. Titles show whatever MAL's own studio
    // page actually rendered — no English-title backfill, deliberately: the main anime/
    // manga DetailScreen's own Related/Recommended rows never got that treatment either
    // (same reasoning — see LibraryViewModel.openCompanyDetail).
    val works: List<MediaItem> = emptyList(),
)