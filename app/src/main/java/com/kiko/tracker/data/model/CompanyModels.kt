package com.kiko.tracker.data.model

// Company (studio/producer/licensor) search/detail live
// reason PersonModels.kt/CharacterModels.kt's types do
// tracking fields. These are
// search-result row, and the
// studio's full anime catalog).

// One row in MAL's
data class CompanySummary(
    val malId: Int,
    val name: String,
    // Parenthetical native/alternate name shown
    // e.g. "(京都アニメーション)" — kept
    // PersonSummary.altName/CharacterSummary.altName use.
    val japanese: String = "",
    val image: String = "",
)

// The single most-recent item
// same forum topic this
// card reuses that screen
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
    // Structured "Label: value" lines
    // actually lists for this
    // not-every-field-always-present shape as PersonDetail.bioFields.
    val bioFields: List<Pair<String, String>> = emptyList(),
    // Free-text company history/description paragraph.
    val about: String = "",
    // "Available At" links (official
    // text (domain or @handle)
    val links: List<Pair<String, String>> = emptyList(),
    val news: CompanyNews? = null,
    // Full anime catalog credited
    // MalCompanyApi's existing studio-page tile
    // baked in), so the
    // else in the app
    // page actually rendered —
    // manga DetailScreen's own Related/Recommended
    // (same reasoning — see
    val works: List<MediaItem> = emptyList(),
)